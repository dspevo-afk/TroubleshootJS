[CmdletBinding()]
param(
    [Parameter(Mandatory=$true)][string]$ModulePath,
    [Parameter(Mandatory=$true)][string]$ChildExecutable,
    [Parameter(Mandatory=$true)][string]$ChildFixture,
    [Parameter(Mandatory=$true)][string]$ExpectedFixtureSha256,
    [string]$ExpectedChildImageSha256 = 'ad169f4cb4bfb78c7a5c030a4529c19d6643276778e33994c93e145b6191c3ec',
    [Parameter(Mandatory=$true)][string]$EvidenceRoot,
    [ValidateSet('Smoke','Suite')][string]$Mode = 'Suite'
)
Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
$cases = New-Object 'System.Collections.Generic.List[object]'
$rootReady = $false

function Require([bool]$Condition, [string]$Reason) {
    if (-not $Condition) { throw $Reason }
}
function Sha([string]$Path) {
    $stream = [IO.File]::OpenRead($Path)
    $algorithm = [Security.Cryptography.SHA256]::Create()
    try { return ([BitConverter]::ToString($algorithm.ComputeHash($stream))).Replace('-','').ToLowerInvariant() }
    finally { $algorithm.Dispose(); $stream.Dispose() }
}
function Save([string]$Name, $Value) {
    $body = [Text.UTF8Encoding]::new($false).GetBytes(($Value | ConvertTo-Json -Depth 20) + "`n")
    $stream = [IO.File]::Open((Join-Path $EvidenceRoot $Name), [IO.FileMode]::CreateNew, [IO.FileAccess]::Write, [IO.FileShare]::Read)
    try { $stream.Write($body,0,$body.Length) } finally { $stream.Dispose() }
}
function Equal-Array($Actual, $Expected, [string]$Label) {
    Require (@($Actual).Count -eq @($Expected).Count) ($Label + ' count')
    for ($i=0; $i -lt @($Expected).Count; $i++) {
        Require ($Actual[$i] -is [string] -and $Actual[$i] -ceq $Expected[$i]) ($Label + ' value')
    }
}
function Observe-Witness($Witness, $ExpectedArgv, [string]$Nonce) {
    Require ($Witness.nonce -ceq $Nonce) 'nonce'
    Require ([long]$Witness.pid -gt 0 -and [long]$Witness.creationFileTime -gt 0) 'witness identity'
    Require ($Witness.imageSha256 -ceq $ExpectedChildImageSha256) 'actual image SHA'
    Require ([String]::Equals([IO.Path]::GetFullPath($Witness.imagePath), $ChildExecutable, [StringComparison]::OrdinalIgnoreCase)) 'actual image path'
    Equal-Array @($Witness.argv) @($ExpectedArgv) 'observed argv'
    $parsed = @(& $owner { param($raw) ConvertFrom-VerifierWindowsCommandLine $raw } $Witness.commandLine)
    Require ($parsed.Count -eq (@($ExpectedArgv).Count + 4)) 'observed command line count'
    Require ([String]::Equals([IO.Path]::GetFullPath($parsed[0]), $ChildExecutable, [StringComparison]::OrdinalIgnoreCase)) 'command line executable'
    Equal-Array @($parsed | Select-Object -Skip 1) (@('-I','-B',$ChildFixture) + @($ExpectedArgv)) 'observed command line'
}
function Finish-Case([string]$Name, $Watch, $Facts) {
    $Watch.Stop()
    $row = [ordered]@{ name=$Name; status='PASS'; operationMillis=$Watch.Elapsed.TotalMilliseconds; cleanupMillis=$null; cleanupStatus='UNMEASURED_SEPARATELY'; facts=$Facts }
    $cases.Add($row)
    Save ($Name+'.case.json') $row
}
function Positive([string]$Name, [string]$ChildMode, [string[]]$Payload) {
    $watch = [Diagnostics.Stopwatch]::StartNew()
    $state = Join-Path $EvidenceRoot ($Name+'.state.json')
    $nonce = [guid]::NewGuid().ToString('N')
    $fixtureArgs = @($ChildMode,$state,$nonce) + @($Payload)
    $result = Invoke-VerifierBoundedProcess -FilePath $ChildExecutable -Arguments (@('-I','-B',$ChildFixture) + $fixtureArgs) -TimeoutMilliseconds 10000
    $expectedExit = 0
    if ($ChildMode -ceq 'nonzero') { $expectedExit=7 }
    Require ($result.ExitCode -is [int] -and $result.ExitCode -eq $expectedExit) 'child exit code'
    Require ($result.TerminationProven -eq $true) 'terminal child'
    $stateBody = [IO.File]::ReadAllText($state)
    $witness = $stateBody | ConvertFrom-Json
    Observe-Witness $witness $fixtureArgs $nonce
    Require ($result.ProcessId -eq [long]$witness.pid) 'retained PID'
    Require ($result.ProcessStartTicks -eq ([long]$witness.creationFileTime + 504911232000000000L)) 'retained creation identity'
    Require ($result.Stdout -ceq $stateBody) 'stdout exact witness'
    Require ($result.Stderr -ceq "CODE_ONLY_STDERR`n") 'stderr exact bytes'
    foreach ($path in @($result.ProcessRoot,$result.StdoutLog,$result.StderrLog)) {
        Require ($path -is [string] -and -not [String]::IsNullOrWhiteSpace($path) -and [IO.Path]::IsPathRooted($path)) 'owner log path present and absolute'
        Require (-not [IO.File]::Exists($path) -and -not [IO.Directory]::Exists($path)) 'successful owner log cleanup'
    }
    Finish-Case $Name $watch @{ pid=$result.ProcessId; startTicks=$result.ProcessStartTicks; imageSha256=$witness.imageSha256; exitCode=$result.ExitCode; terminationProven=$true; ownerLogsAbsent=$true }
}

try {
    Require ($PSVersionTable.PSVersion.Major -eq 5 -and $PSVersionTable.PSVersion.Minor -eq 1) 'Windows PowerShell 5.1 required'
    Require ([IO.FileInfo]::new($ModulePath).Length -eq 1099690 -and (Sha $ModulePath) -ceq 'd4384a271cf239438b32eddf15e4e1eaf1285d751dcc25c9dfce89b66c063085') 'module pin'
    Require ($ExpectedFixtureSha256 -cmatch '^[0-9a-f]{64}$' -and (Sha $ChildFixture) -ceq $ExpectedFixtureSha256) 'fixture pin'
    Require ($ExpectedChildImageSha256 -cmatch '^[0-9a-f]{64}$' -and (Sha $ChildExecutable) -ceq $ExpectedChildImageSha256) 'selected image pin'
    $owner = Import-Module -Name $ModulePath -Force -DisableNameChecking -PassThru
    $ChildExecutable = [IO.Path]::GetFullPath($ChildExecutable)
    $ChildFixture = [IO.Path]::GetFullPath($ChildFixture)
    $EvidenceRoot = [IO.Path]::GetFullPath($EvidenceRoot)
    $tempRoot = [IO.Path]::GetFullPath([IO.Path]::GetTempPath())
    Assert-VerifierNoReparseAncestors $EvidenceRoot
    Require (Test-VerifierPhysicalChildPath $tempRoot $EvidenceRoot) 'evidence root containment'
    Require (-not [IO.Directory]::Exists($EvidenceRoot) -and -not [IO.File]::Exists($EvidenceRoot)) 'fresh evidence root required'
    $null = [IO.Directory]::CreateDirectory($EvidenceRoot)
    $rootReady = $true
    Assert-VerifierNoReparseAncestors $EvidenceRoot
    $sourceHashes = @{ module=(Sha $ModulePath); fixture=(Sha $ChildFixture); executable=(Sha $ChildExecutable) }
    Save 'inputs.json' @{ hashes=$sourceHashes; powershellVersion=$PSVersionTable.PSVersion.ToString(); modulePath=$env:PSModulePath; mode=$Mode }

    $unicode = 'unicode-' + [char]0x03A9 + [char]0x96EA
    Positive 'success-argv' 'success' @('', 'two words', '#hash', 'semi;colon', 'literal"quote', 'tail\', $unicode)
    Positive 'nonzero-seven' 'nonzero' @('exit7')
    if ($Mode -ceq 'Suite') {
        foreach ($ordinal in @(1,2,3)) { Positive ('rapid-'+$ordinal) 'success' @() }
        $watch = [Diagnostics.Stopwatch]::StartNew()
        $state = Join-Path $EvidenceRoot 'timeout.state.json'
        $nonce = [guid]::NewGuid().ToString('N')
        $fixtureArgs = @('sleep',$state,$nonce)
        $failure = $null
        try { $null = Invoke-VerifierBoundedProcess -FilePath $ChildExecutable -Arguments (@('-I','-B',$ChildFixture)+$fixtureArgs) -TimeoutMilliseconds 1500 }
        catch { $failure = $_ }
        Require ($null -ne $failure -and (Test-VerifierInfrastructureError $failure)) 'timeout infrastructure failure required'
        $message = $failure.Exception.Message
        Require ($message.Contains('exceeded 1500ms') -and -not $message.Contains('cleanup was not proven')) 'real timeout with proven owner cleanup'
        $match = [regex]::Match($message, "Bounded-process evidence: '([^']+)'\.$")
        Require $match.Success 'timeout evidence path'
        $logRoot = [IO.Path]::GetFullPath($match.Groups[1].Value)
        $expectedParent = [IO.Path]::GetFullPath((Join-Path $tempRoot 'TroubleshootJS'))
        Require ([String]::Equals([IO.Path]::GetDirectoryName($logRoot), $expectedParent, [StringComparison]::OrdinalIgnoreCase)) 'timeout log namespace'
        Require ([IO.Path]::GetFileName($logRoot) -cmatch '^verify-process-[0-9a-f]{32}$') 'timeout log basename'
        Assert-VerifierNoReparseAncestors $logRoot
        $stateBody = [IO.File]::ReadAllText($state)
        $witness = $stateBody | ConvertFrom-Json
        Observe-Witness $witness $fixtureArgs $nonce
        Require ([IO.File]::ReadAllText((Join-Path $logRoot 'stdout.log')) -ceq $stateBody) 'timeout flushed output'
        Require ([IO.File]::ReadAllText((Join-Path $logRoot 'stderr.log')) -ceq '') 'timeout stderr'
        $live = $null
        try { $live = [Diagnostics.Process]::GetProcessById([int]$witness.pid) } catch [ArgumentException] { }
        try { Require ($null -eq $live) 'timed-out PID absent; reuse or uncertainty is failure' }
        finally { if ($null -ne $live) { $live.Dispose() } }
        Finish-Case 'timeout' $watch @{ pid=$witness.pid; startTicks=([long]$witness.creationFileTime+504911232000000000L); imageSha256=$witness.imageSha256; operationStatus='EXPECTED_TIMEOUT_NOT_PASS'; pidAbsent=$true; ownerEvidenceRetained=$logRoot }

        $badTimeouts = @(
            @{name='zero';value=0}, @{name='negative';value=-1},
            @{name='string';value='5'}, @{name='bool';value=$true},
            @{name='null';value=$null}, @{name='fraction';value=1.5},
            @{name='array';value=@(5)}, @{name='map';value=@{}}
        )
        foreach ($bad in $badTimeouts) {
            $watch = [Diagnostics.Stopwatch]::StartNew()
            $state = Join-Path $EvidenceRoot ('invalid-timeout-'+$bad.name+'.state.json')
            $failure = $null
            try { $null = Invoke-VerifierBoundedProcess -FilePath $ChildExecutable -Arguments @('-I','-B',$ChildFixture,'success',$state,'invalid') -TimeoutMilliseconds $bad.value }
            catch { $failure=$_ }
            Require ($null -ne $failure -and (Test-VerifierInfrastructureError $failure)) 'invalid timeout rejected'
            Require ($failure.Exception.Message -ceq 'VERIFIER_INFRASTRUCTURE: A bounded process timeout must be one exact positive integral scalar.') 'pre-launch timeout guard'
            Require (-not [IO.File]::Exists($state)) 'invalid timeout has no child state'
            Finish-Case ('invalid-timeout-'+$bad.name) $watch @{ guard='EXACT_PRE_START_GUARD'; stateAbsent=$true; osProcessAudit='NOT_PERFORMED' }
        }
        foreach ($name in @('scalar','member')) {
            $watch = [Diagnostics.Stopwatch]::StartNew()
            $state = Join-Path $EvidenceRoot ('invalid-arguments-'+$name+'.state.json')
            $badArgs = 'not-an-array'
            if ($name -ceq 'member') { $badArgs=@('-I','-B',$ChildFixture,'success',$state,'invalid',7) }
            $failure=$null
            try { $null=Invoke-VerifierBoundedProcess -FilePath $ChildExecutable -Arguments $badArgs -TimeoutMilliseconds 10000 }
            catch { $failure=$_ }
            Require ($null -ne $failure -and (Test-VerifierInfrastructureError $failure)) 'invalid argv rejected'
            $expectedMessage = 'VERIFIER_INFRASTRUCTURE: verifier process arguments must be one exact argument array before process start.'
            if ($name -ceq 'member') { $expectedMessage='VERIFIER_INFRASTRUCTURE: verifier process arguments contained a non-string argument before process start.' }
            Require ($failure.Exception.Message -ceq $expectedMessage) 'exact argv pre-launch guard'
            Require (-not [IO.File]::Exists($state)) 'invalid argv has no child state'
            Finish-Case ('invalid-arguments-'+$name) $watch @{ guard='PRE_START_ARGUMENT_GUARD'; stateAbsent=$true; osProcessAudit='NOT_PERFORMED' }
        }
    }
    $expectedCaseCount=2
    if ($Mode -ceq 'Suite') { $expectedCaseCount=16 }
    Require ($cases.Count -eq $expectedCaseCount) 'complete case count'
    Require ((Sha $ModulePath) -ceq $sourceHashes.module -and (Sha $ChildFixture) -ceq $sourceHashes.fixture -and (Sha $ChildExecutable) -ceq $sourceHashes.executable) 'final source/image pins'
    Save 'cases.json' @{ status='PASS'; mode=$Mode; caseCount=$cases.Count; cases=@($cases.ToArray()); cleanupStatus='UNMEASURED_SEPARATELY'; outputByteCap='NOT_QUALIFIED'; cancellation='NOT_QUALIFIED'; descendantContainment='NOT_QUALIFIED' }
    Write-Output ('Verifier bounded-process direct-child contract cases='+$cases.Count+' status=PASS')
    exit 0
} catch {
    if ($rootReady) { Save 'failure.json' @{ status='FAIL_NOT_PASS'; type=$_.Exception.GetType().FullName; message=$_.Exception.Message; completedCases=@($cases.ToArray()) } }
    [Console]::Error.WriteLine('Verifier direct-child contract FAIL: '+$_.Exception.GetType().FullName)
    exit 2
}
