[CmdletBinding()]
param(
    [Parameter(Mandatory=$true)][string]$ModulePath,
    [Parameter(Mandatory=$true)][string]$ExpectedModuleSha256,
    [Parameter(Mandatory=$true)][long]$ExpectedModuleBytes,
    [Parameter(Mandatory=$true)][string]$ChildExecutable,
    [Parameter(Mandatory=$true)][string]$ChildFixture,
    [Parameter(Mandatory=$true)][string]$ExpectedFixtureSha256,
    [string]$ExpectedChildImageSha256 = 'ad169f4cb4bfb78c7a5c030a4529c19d6643276778e33994c93e145b6191c3ec',
    [long]$ExpectedChildExecutableBytes = 171744,
    [Parameter(Mandatory=$true)][string]$EvidenceRoot,
    [ValidateSet('Smoke','Suite')][string]$Mode = 'Suite'
)
Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
$cases = New-Object 'System.Collections.Generic.List[object]'
$rootReady = $false
$stdoutCap = 65536
$stderrCap = 65536
$token = [Threading.CancellationToken]::None

function Require([bool]$Condition, [string]$Reason) {
    if (-not $Condition) { throw $Reason }
}
function Sha([string]$Path) {
    $stream = [IO.File]::OpenRead($Path)
    $algorithm = [Security.Cryptography.SHA256]::Create()
    try { return ([BitConverter]::ToString($algorithm.ComputeHash($stream))).Replace('-','').ToLowerInvariant() }
    finally { $algorithm.Dispose(); $stream.Dispose() }
}
function Sha-Bytes([byte[]]$Bytes) {
    $algorithm = [Security.Cryptography.SHA256]::Create()
    try { return ([BitConverter]::ToString($algorithm.ComputeHash($Bytes))).Replace('-','').ToLowerInvariant() }
    finally { $algorithm.Dispose() }
}
function Save([string]$Name, $Value) {
    $body = [Text.UTF8Encoding]::new($false).GetBytes(($Value | ConvertTo-Json -Depth 20) + "`n")
    $stream = [IO.File]::Open((Join-Path $EvidenceRoot $Name), [IO.FileMode]::CreateNew, [IO.FileAccess]::Write, [IO.FileShare]::Read)
    try { $stream.Write($body, 0, $body.Length); $stream.Flush() } finally { $stream.Dispose() }
}
function Equal-Array($Actual, $Expected, [string]$Label) {
    Require (@($Actual).Count -eq @($Expected).Count) ($Label + ' count')
    for ($i = 0; $i -lt @($Expected).Count; $i++) {
        Require ($Actual[$i] -is [string] -and $Actual[$i] -ceq $Expected[$i]) ($Label + ' value')
    }
}
function Equal-Bytes([byte[]]$Actual, [byte[]]$Expected, [string]$Label) {
    Require ($Actual.Length -eq $Expected.Length) ($Label + ' length')
    for ($i = 0; $i -lt $Expected.Length; $i++) {
        Require ($Actual[$i] -eq $Expected[$i]) ($Label + ' byte')
    }
}
function New-FixtureCall([string]$Name, [string]$ChildMode) {
    $state = Join-Path $EvidenceRoot ($Name + '.state.json')
    $nonce = [guid]::NewGuid().ToString('N')
    return [pscustomobject]@{
        Name = $Name
        State = $state
        Nonce = $nonce
        FixtureArguments = @($ChildMode, $state, $nonce)
    }
}
function Observe-Witness($Call, [string[]]$ExpectedArgv) {
    Require ([IO.File]::Exists($Call.State)) 'child witness state exists'
    $raw = [IO.File]::ReadAllBytes($Call.State)
    $body = [Text.Encoding]::ASCII.GetString($raw)
    $witness = $body | ConvertFrom-Json
    Require ($witness.nonce -ceq $Call.Nonce) 'witness nonce'
    Require ([long]$witness.pid -gt 0 -and [long]$witness.creationFileTime -gt 0) 'witness identity'
    Require ($witness.imageSha256 -ceq $ExpectedChildImageSha256) 'witness image SHA'
    Require ([String]::Equals([IO.Path]::GetFullPath($witness.imagePath), $ChildExecutable, [StringComparison]::OrdinalIgnoreCase)) 'witness image path'
    Equal-Array @($witness.argv) @($ExpectedArgv) 'witness argv'
    $parsed = @(& $owner { param($rawCommandLine) ConvertFrom-VerifierWindowsCommandLine $rawCommandLine } $witness.commandLine)
    Require ($parsed.Count -eq (@($ExpectedArgv).Count + 4)) 'witness command-line count'
    Require ([String]::Equals([IO.Path]::GetFullPath($parsed[0]), $ChildExecutable, [StringComparison]::OrdinalIgnoreCase)) 'witness command-line executable'
    Equal-Array @($parsed | Select-Object -Skip 1) (@('-I','-B',$ChildFixture) + @($ExpectedArgv)) 'witness command line'
    return [pscustomobject]@{ Witness=$witness; Raw=$raw; Body=$body }
}
function Assert-ImageIdentity($Facts, $Witness) {
    $property = $Facts.PSObject.Properties['ImageIdentity']
    Require ($null -ne $property -and $null -ne $property.Value) 'original-handle image identity present'
    $identity = $property.Value
    Equal-Array @($identity.PSObject.Properties.Name | Sort-Object) @('ImagePath','ProcessCreationFileTime','ProcessId','ProcessStartTicks') 'image identity schema'
    Require ([int]$identity.ProcessId -eq [int]$Witness.pid) 'image identity PID'
    Require ([long]$identity.ProcessStartTicks -eq [long]$Facts.StartTicks) 'image identity retained process ticks'
    Require ([long]$Facts.StartTicks -eq ([long]$Witness.creationFileTime + 504911232000000000L)) 'witness process creation ticks'
    Require ([long]$identity.ProcessCreationFileTime -eq [long]$Witness.creationFileTime) 'image identity FILETIME'
    Require ([String]::Equals([IO.Path]::GetFullPath($identity.ImagePath), [IO.Path]::GetFullPath($Witness.imagePath), [StringComparison]::OrdinalIgnoreCase)) 'image identity image path'
}
function Assert-CaptureFacts($Facts, [bool]$ImageExpected, [bool]$FailureLogsExpected) {
    Require ($null -ne $Facts) 'bounded capture facts present'
    $expected = @(
        'ProcessId','StartTicks','CaptureFailureKind','ReaderTasksTerminal','ReadersClosed',
        'FailureLogsRetained','OperationMilliseconds','CleanupMilliseconds','CleanupProven',
        'StdoutByteCount','StdoutObservedBytes','StdoutDiscardedDuringCleanup','StdoutSha256',
        'StderrByteCount','StderrObservedBytes','StderrDiscardedDuringCleanup','StderrSha256'
    )
    if ($ImageExpected) { $expected += 'ImageIdentity' }
    Equal-Array @($Facts.PSObject.Properties.Name) $expected 'capture facts schema'
    Require ($Facts.ReaderTasksTerminal -eq $true -and $Facts.ReadersClosed -eq $true) 'capture readers terminal and closed'
    Require ($Facts.CleanupProven -eq $true) 'bounded capture cleanup proven'
    Require ($Facts.FailureLogsRetained -eq $FailureLogsExpected) 'capture log retention state'
    Require ([double]$Facts.OperationMilliseconds -ge 0 -and [double]$Facts.OperationMilliseconds -lt 180000) 'capture operation bound'
    Require ([double]$Facts.CleanupMilliseconds -ge 0 -and [double]$Facts.CleanupMilliseconds -lt 120000) 'capture cleanup bound'
}
function Assert-SuccessResult($Result, $Call, [int]$ExpectedExit, [bool]$Bounded, [bool]$ImageExpected) {
    Require ($Result.ExitCode -is [int] -and $Result.ExitCode -eq $ExpectedExit) 'direct child exit code'
    Require ($Result.TerminationProven -eq $true) 'direct child terminal proof'
    $observed = Observe-Witness $Call $Call.FixtureArguments
    Require ([int]$Result.ProcessId -eq [int]$observed.Witness.pid) 'public result PID'
    Require ([long]$Result.ProcessStartTicks -eq ([long]$observed.Witness.creationFileTime + 504911232000000000L)) 'public result start ticks'
    Require ($Result.Stdout -ceq $observed.Body) 'public result stdout witness'
    if ($Call.FixtureArguments[0] -ceq 'sleep') {
        Require ($Result.Stderr -ceq '') 'sleep stderr'
    } else {
        Require ($Result.Stderr -ceq "CODE_ONLY_STDERR`n") 'public result stderr'
    }
    $expectedResultProperties = @('FilePath','Arguments','ProcessId','ProcessStartTicks','ExitCode','Stdout','Stderr','StdoutLog','StderrLog','ProcessRoot','TerminationProven')
    if ($Bounded) { $expectedResultProperties += 'CaptureFacts' }
    Equal-Array @($Result.PSObject.Properties.Name) $expectedResultProperties 'public result schema'
    if ($Bounded) {
        $facts = $Result.CaptureFacts
        Assert-CaptureFacts $facts $ImageExpected $false
        $rawStderr = [Text.Encoding]::UTF8.GetBytes([string]$Result.Stderr)
        Require ([int]$facts.ProcessId -eq [int]$Result.ProcessId) 'capture facts PID'
        Require ([long]$facts.StartTicks -eq [long]$Result.ProcessStartTicks) 'capture facts start ticks'
        Require ([int]$facts.StdoutByteCount -eq $observed.Raw.Length) 'captured stdout bytes'
        Require ([long]$facts.StdoutObservedBytes -eq $observed.Raw.Length) 'observed stdout bytes'
        Require ($facts.StdoutSha256 -ceq (Sha-Bytes $observed.Raw)) 'captured stdout digest'
        Require ([int]$facts.StderrByteCount -eq $rawStderr.Length) 'captured stderr bytes'
        Require ([long]$facts.StderrObservedBytes -eq $rawStderr.Length) 'observed stderr bytes'
        Require ($facts.StderrSha256 -ceq (Sha-Bytes $rawStderr)) 'captured stderr digest'
        if ($ImageExpected) { Assert-ImageIdentity $facts $observed.Witness }
        else { Require ($null -eq $facts.PSObject.Properties['ImageIdentity']) 'default capture has no image identity' }
    } else {
        Require ($null -eq $Result.PSObject.Properties['CaptureFacts']) 'legacy result has no capture facts'
    }
    foreach ($path in @($Result.ProcessRoot, $Result.StdoutLog, $Result.StderrLog)) {
        Require ($path -is [string] -and [IO.Path]::IsPathRooted($path)) 'successful owner log path present'
        Require (-not [IO.File]::Exists($path) -and -not [IO.Directory]::Exists($path)) 'successful owner logs removed'
    }
    return [pscustomobject]@{ Witness=$observed.Witness; Facts=$(if ($Bounded) { $Result.CaptureFacts } else { $null }) }
}
function Invoke-FixtureBounded($Call, [bool]$Capture, [bool]$CaptureIdentity, [int]$TimeoutMilliseconds) {
    $parameters = @{
        FilePath = $ChildExecutable
        Arguments = (@('-I','-B',$ChildFixture) + @($Call.FixtureArguments))
        TimeoutMilliseconds = $TimeoutMilliseconds
    }
    if ($Capture) {
        $parameters['MaxStdoutBytes'] = $stdoutCap
        $parameters['MaxStderrBytes'] = $stderrCap
        $parameters['CancellationToken'] = $token
    }
    if ($CaptureIdentity) { $parameters['CaptureImageIdentity'] = $true }
    return Invoke-VerifierBoundedProcess @parameters
}
function Finish-Case([string]$Name, $Watch, $Facts) {
    $Watch.Stop()
    $row = [ordered]@{ name=$Name; status='PASS'; elapsedMilliseconds=$Watch.Elapsed.TotalMilliseconds; facts=$Facts }
    $cases.Add($row)
    Save ($Name + '.case.json') $row
}
function Expect-PrelaunchRefusal([string]$Name, [string]$Flavor, [string]$ExpectedMessage) {
    $watch = [Diagnostics.Stopwatch]::StartNew()
    $call = New-FixtureCall $Name 'success'
    $parameters = @{
        FilePath = $ChildExecutable
        Arguments = (@('-I','-B',$ChildFixture) + @($call.FixtureArguments))
        TimeoutMilliseconds = 10000
    }
    switch ($Flavor) {
        'string' { $parameters['CaptureImageIdentity'] = 'true' }
        'integer' { $parameters['CaptureImageIdentity'] = 1 }
        'missing-capture' { $parameters['CaptureImageIdentity'] = $true }
        'native' {
            $parameters['CaptureImageIdentity'] = $true
            $parameters['MaxStdoutBytes'] = $stdoutCap
            $parameters['MaxStderrBytes'] = $stderrCap
            $parameters['CancellationToken'] = $token
            $parameters['CreatorJob'] = $null
            $parameters['WorkingDirectory'] = $EvidenceRoot
        }
        default { throw 'unknown prelaunch refusal flavor' }
    }
    $failure = $null
    try { $null = Invoke-VerifierBoundedProcess @parameters } catch { $failure = $_ }
    if ($null -ne $failure) {
        Save ($Name + '.refusal.json') @{ type=$failure.Exception.GetType().FullName; message=$failure.Exception.Message }
    }
    Require ($null -ne $failure -and (Test-VerifierInfrastructureError $failure)) 'typed prelaunch refusal'
    Require ($failure.Exception.Message -ceq $ExpectedMessage) 'exact prelaunch refusal message'
    Require (-not [IO.File]::Exists($call.State)) 'prelaunch refusal emitted no child state'
    Finish-Case $Name $watch @{ guard='PRE_LAUNCH'; stateAbsent=$true; exactType=$Flavor; osProcessAudit='NOT_PERFORMED' }
}

function New-ThrowingImageIdentityType([string]$WitnessPath) {
    $suffix = [guid]::NewGuid().ToString('N')
    $className = 'N71ImageIdentityFailure' + $suffix
    $typeName = 'TroubleshootJsVerifier.' + $className
    $source = @"
using System;
using System.Diagnostics;
using System.IO;
using System.Threading;
namespace TroubleshootJsVerifier {
    public static class $className {
        public static string WitnessPath;
        public static object Read(IntPtr processHandle, int processId, long startTicks) {
            Stopwatch watch = Stopwatch.StartNew();
            while (watch.ElapsedMilliseconds < 2000) {
                if (!String.IsNullOrWhiteSpace(WitnessPath) && File.Exists(WitnessPath)) {
                    byte[] raw = null;
                    try { raw = File.ReadAllBytes(WitnessPath); } catch (IOException) { }
                    if (raw != null && raw.Length > 2 && raw[0] == (byte)'{' && raw[raw.Length - 1] == (byte)'\n') {
                        throw new InvalidOperationException("N71_INJECTED_IMAGE_QUERY_FAILURE");
                    }
                }
                Thread.Sleep(20);
            }
            throw new InvalidOperationException("N71_WITNESS_NOT_READY");
        }
    }
}
"@
    Add-Type -TypeDefinition $source -Language CSharp -ErrorAction Stop | Out-Null
    $fakeType = $typeName -as [type]
    Require ($null -ne $fakeType) 'throwing image API type compiled'
    $field = $fakeType.GetField('WitnessPath')
    Require ($null -ne $field) 'throwing image API witness field present'
    $field.SetValue($null, $WitnessPath)
    $readMethod = $fakeType.GetMethod('Read')
    Require ($null -ne $readMethod -and $readMethod.IsStatic) 'throwing image API static reader present'
    $parameterTypes = @($readMethod.GetParameters() | ForEach-Object { $_.ParameterType.FullName })
    Equal-Array $parameterTypes @('System.IntPtr','System.Int32','System.Int64') 'throwing image API signature'
    return $fakeType
}
function Is-BytePrefix([byte[]]$Prefix, [byte[]]$Whole) {
    if ($Prefix.Length -gt $Whole.Length) { return $false }
    for ($i = 0; $i -lt $Prefix.Length; $i++) {
        if ($Prefix[$i] -ne $Whole[$i]) { return $false }
    }
    return $true
}
function Invoke-GetterFailureCanary($Call, $Watch) {
    $savedInitializer = & $owner { (Get-Item Function:Initialize-VerifierDirectProcessImageIdentity).ScriptBlock }
    Require ($savedInitializer -is [scriptblock]) 'original image initializer saved'
    $fakeType = New-ThrowingImageIdentityType $Call.State
    $failure = $null
    $captureFacts = $null
    try {
        & $owner {
            param($ImageApiType)
            if (Test-Path Variable:script:N71InjectedImageApi) { throw 'fresh injected image scope required' }
            $script:N71InjectedImageApi = $ImageApiType
            Set-Item Function:script:Initialize-VerifierDirectProcessImageIdentity -Value {
                return $script:N71InjectedImageApi
            }
        } $fakeType
        try { $null = Invoke-FixtureBounded $Call $true $true 10000 } catch { $failure = $_ }
        if ($null -ne $failure) {
            try { $captureFacts = $failure.Exception.Data['VerifierCapture'] } catch { }
            Save 'image-getter-failure-primary.json' @{ type=$failure.Exception.GetType().FullName; message=$failure.Exception.Message; captureFacts=$captureFacts }
        }
    } finally {
        & $owner {
            param($OriginalInitializer)
            Set-Item Function:script:Initialize-VerifierDirectProcessImageIdentity -Value $OriginalInitializer
            if (Test-Path Variable:script:N71InjectedImageApi) {
                Remove-Variable -Scope Script -Name N71InjectedImageApi
            }
        } $savedInitializer
    }
    $restoredInitializer = & $owner { (Get-Item Function:Initialize-VerifierDirectProcessImageIdentity).ScriptBlock }
    Require ($restoredInitializer.ToString() -ceq $savedInitializer.ToString()) 'image initializer restored exactly'
    Require ($null -ne $failure -and (Test-VerifierInfrastructureError $failure)) 'image getter failure is typed infrastructure'
    Require ($failure.Exception.Message.Contains('N71_INJECTED_IMAGE_QUERY_FAILURE')) 'injected image getter failure retained'
    Require ($null -ne $captureFacts) 'image getter failure capture facts retained'
    Assert-CaptureFacts $captureFacts $false $true
    Require ($captureFacts.CaptureFailureKind -ceq 'CAPTURE_FAILURE') 'image getter failure kind'
    Require ($null -eq $captureFacts.PSObject.Properties['ImageIdentity']) 'failed getter does not claim image identity'

    $observed = Observe-Witness $Call $Call.FixtureArguments
    Require ([int]$captureFacts.ProcessId -eq [int]$observed.Witness.pid) 'failure facts PID matches live witness'
    Require ([long]$captureFacts.StartTicks -eq ([long]$observed.Witness.creationFileTime + 504911232000000000L)) 'failure facts start time matches witness FILETIME'
    Require ([int]$captureFacts.StdoutByteCount -le $stdoutCap -and [int]$captureFacts.StderrByteCount -le $stderrCap) 'failure prefixes within byte caps'
    Require ([long]$captureFacts.StdoutObservedBytes -ge [long]$captureFacts.StdoutByteCount -and [long]$captureFacts.StdoutObservedBytes -le $stdoutCap) 'failure stdout observed prefix bound'
    Require ([long]$captureFacts.StderrObservedBytes -ge [long]$captureFacts.StderrByteCount -and [long]$captureFacts.StderrObservedBytes -le $stderrCap) 'failure stderr observed prefix bound'

    $match = [regex]::Match($failure.Exception.Message, "Bounded-process evidence: '([^']+)'\.$")
    Require $match.Success 'image getter failure evidence path retained'
    $ownerRoot = [IO.Path]::GetFullPath($match.Groups[1].Value)
    $expectedParent = [IO.Path]::GetFullPath((Join-Path $tempRoot 'TroubleshootJS'))
    Require ([String]::Equals([IO.Path]::GetDirectoryName($ownerRoot), $expectedParent, [StringComparison]::OrdinalIgnoreCase)) 'image getter failure log namespace'
    Require ([IO.Path]::GetFileName($ownerRoot) -cmatch '^verify-process-[0-9a-f]{32}$') 'image getter failure owner log identity'
    Assert-VerifierNoReparseAncestors $ownerRoot
    $stdoutRaw = [IO.File]::ReadAllBytes((Join-Path $ownerRoot 'stdout.log'))
    $stderrRaw = [IO.File]::ReadAllBytes((Join-Path $ownerRoot 'stderr.log'))
    Require ($stdoutRaw.Length -eq [int]$captureFacts.StdoutByteCount -and (Sha-Bytes $stdoutRaw) -ceq $captureFacts.StdoutSha256) 'failure stdout log matches retained facts'
    Require ($stderrRaw.Length -eq [int]$captureFacts.StderrByteCount -and (Sha-Bytes $stderrRaw) -ceq $captureFacts.StderrSha256) 'failure stderr log matches retained facts'
    Require (Is-BytePrefix $stdoutRaw $observed.Raw) 'failure stdout is a witness prefix'
    Require (Is-BytePrefix $stderrRaw ([byte[]]@())) 'failure stderr is an empty witness prefix'
    Require ($captureFacts.CleanupProven -eq $true -and $captureFacts.ReaderTasksTerminal -eq $true -and $captureFacts.ReadersClosed -eq $true) 'launched child and capture readers cleaned through original owner'
    Require ($captureFacts.FailureLogsRetained -eq $true) 'getter failure prefix logs retained'
    return [pscustomobject]@{ Witness=$observed.Witness; Facts=$captureFacts; OwnerRoot=$ownerRoot; Stdout=$stdoutRaw; Stderr=$stderrRaw; Watch=$Watch }
}
try {
    Require ($PSVersionTable.PSVersion.Major -eq 5 -and $PSVersionTable.PSVersion.Minor -eq 1) 'Windows PowerShell 5.1 required'
    Require ($ExpectedModuleSha256 -cmatch '^[0-9a-f]{64}$' -and $ExpectedModuleBytes -gt 0) 'module pin arguments'
    Require ([IO.FileInfo]::new($ModulePath).Length -eq $ExpectedModuleBytes -and (Sha $ModulePath) -ceq $ExpectedModuleSha256) 'module pin'
    Require ($ExpectedFixtureSha256 -cmatch '^[0-9a-f]{64}$' -and (Sha $ChildFixture) -ceq $ExpectedFixtureSha256) 'fixture source pin'
    Require ($ExpectedChildImageSha256 -cmatch '^[0-9a-f]{64}$' -and [IO.FileInfo]::new($ChildExecutable).Length -eq $ExpectedChildExecutableBytes -and (Sha $ChildExecutable) -ceq $ExpectedChildImageSha256) 'selected child image pin'

    $hostExecutable = [IO.Path]::GetFullPath((Join-Path $PSHOME 'powershell.exe'))
    $hostInfo = [Diagnostics.Process]::GetCurrentProcess()
    try { $runningHost = [IO.Path]::GetFullPath($hostInfo.MainModule.FileName) } finally { $hostInfo.Dispose() }
    Require ([String]::Equals($runningHost, $hostExecutable, [StringComparison]::OrdinalIgnoreCase)) 'selected PowerShell host image path'
    Require ([IO.FileInfo]::new($hostExecutable).Length -eq 495616 -and (Sha $hostExecutable) -ceq 'd8f182560210d43baaf767d482ad1dc1207372522e91413b9d5e515a875e552d') 'selected PowerShell host pin'

    $owner = Import-Module -Name $ModulePath -Force -DisableNameChecking -PassThru
    $ModulePath = [IO.Path]::GetFullPath($ModulePath)
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
    $initialSources = [ordered]@{
        module=@{bytes=[IO.FileInfo]::new($ModulePath).Length;sha256=(Sha $ModulePath)}
        fixture=@{bytes=[IO.FileInfo]::new($ChildFixture).Length;sha256=(Sha $ChildFixture)}
        child=@{bytes=[IO.FileInfo]::new($ChildExecutable).Length;sha256=(Sha $ChildExecutable)}
        powershell=@{bytes=[IO.FileInfo]::new($hostExecutable).Length;sha256=(Sha $hostExecutable)}
    }
    Save 'inputs.json' @{ sources=$initialSources; powershellVersion=$PSVersionTable.PSVersion.ToString(); backend='DIRECT_PROCESS'; imageIdentity='OPT_IN_ORIGINAL_PROCESS_HANDLE'; nativeJobCapture='NOT_TESTED'; descendantContainment='NOT_ATTESTED' }

    $watch = [Diagnostics.Stopwatch]::StartNew()
    $call = New-FixtureCall 'image-success' 'success'
    $result = Invoke-FixtureBounded $call $true $true 10000
    $observed = Assert-SuccessResult $result $call 0 $true $true
    Finish-Case 'image-success' $watch @{ pid=$observed.Witness.pid; creationFileTime=$observed.Witness.creationFileTime; imagePath=$observed.Witness.imagePath; imageSha256=$observed.Witness.imageSha256; captureIdentity='ORIGINAL_PROCESS_HANDLE' }

    $watch = [Diagnostics.Stopwatch]::StartNew()
    $call = New-FixtureCall 'default-bounded' 'success'
    $result = Invoke-FixtureBounded $call $true $false 10000
    $observed = Assert-SuccessResult $result $call 0 $true $false
    Finish-Case 'default-bounded-no-image' $watch @{ pid=$observed.Witness.pid; startTicks=$result.ProcessStartTicks; captureFactsHasImageIdentity=$false; captureIdentity='OPT_IN_OMITTED' }

    Expect-PrelaunchRefusal 'invalid-image-flag-string' 'string' 'VERIFIER_INFRASTRUCTURE: CaptureImageIdentity must be one exact Boolean.'
    Expect-PrelaunchRefusal 'invalid-image-flag-integer' 'integer' 'VERIFIER_INFRASTRUCTURE: CaptureImageIdentity must be one exact Boolean.'
    Expect-PrelaunchRefusal 'image-requires-byte-capture' 'missing-capture' 'VERIFIER_INFRASTRUCTURE: Image identity requires explicit bounded stdout/stderr capture and cancellation token.'

    if ($Mode -ceq 'Suite') {
        $watch = [Diagnostics.Stopwatch]::StartNew()
        $call = New-FixtureCall 'legacy-default' 'success'
        $arguments = @('-I','-B',$ChildFixture) + @($call.FixtureArguments)
        $result = Invoke-VerifierBoundedProcess -FilePath $ChildExecutable -Arguments $arguments -TimeoutMilliseconds 10000
        $observed = Assert-SuccessResult $result $call 0 $false $false
        Finish-Case 'legacy-result-shape' $watch @{ pid=$observed.Witness.pid; startTicks=$result.ProcessStartTicks; captureFactsPresent=$false }

        $watch = [Diagnostics.Stopwatch]::StartNew()
        $call = New-FixtureCall 'image-nonzero-seven' 'nonzero'
        $result = Invoke-FixtureBounded $call $true $true 10000
        $observed = Assert-SuccessResult $result $call 7 $true $true
        Finish-Case 'image-nonzero-seven' $watch @{ pid=$observed.Witness.pid; creationFileTime=$observed.Witness.creationFileTime; exitCode=$result.ExitCode; operationOutcome='DIRECT_API_EXIT_7_OBSERVED_BRIDGE_ADJUDICATION_NOT_TESTED' }

        $watch = [Diagnostics.Stopwatch]::StartNew()
        $call = New-FixtureCall 'image-timeout' 'sleep'
        $failure = $null
        try { $null = Invoke-FixtureBounded $call $true $true 1500 } catch { $failure = $_ }
        if ($null -ne $failure) {
            $originalFacts = $null
            try { $originalFacts = $failure.Exception.Data['VerifierCapture'] } catch { }
            Save 'image-timeout-primary.json' @{ type=$failure.Exception.GetType().FullName; message=$failure.Exception.Message; captureFacts=$originalFacts }
        }
        Require ($null -ne $failure -and (Test-VerifierInfrastructureError $failure)) 'timeout expected as infrastructure failure'
        Require ($failure.Exception.Message.Contains('exceeded 1500ms')) 'timeout reason retained'
        $match = [regex]::Match($failure.Exception.Message, "Bounded-process evidence: '([^']+)'\.$")
        Require $match.Success 'timeout owner evidence path retained'
        $ownerRoot = [IO.Path]::GetFullPath($match.Groups[1].Value)
        $expectedParent = [IO.Path]::GetFullPath((Join-Path $tempRoot 'TroubleshootJS'))
        Require ([String]::Equals([IO.Path]::GetDirectoryName($ownerRoot), $expectedParent, [StringComparison]::OrdinalIgnoreCase)) 'timeout owner log namespace'
        Require ([IO.Path]::GetFileName($ownerRoot) -cmatch '^verify-process-[0-9a-f]{32}$') 'timeout owner log identity'
        Assert-VerifierNoReparseAncestors $ownerRoot
        $failureFacts = $failure.Exception.Data['VerifierCapture']
        Require ($null -ne $failureFacts) 'timeout capture facts retained'
        Assert-CaptureFacts $failureFacts $true $true
        Require ($failureFacts.CaptureFailureKind -ceq 'TIMEOUT') 'timeout failure kind'
        $observed = Observe-Witness $call $call.FixtureArguments
        Require ([int]$failureFacts.StdoutByteCount -eq $observed.Raw.Length) 'timeout retained stdout byte count'
        Require ([long]$failureFacts.StdoutObservedBytes -eq $observed.Raw.Length) 'timeout observed stdout bytes'
        Require ($failureFacts.StdoutSha256 -ceq (Sha-Bytes $observed.Raw)) 'timeout retained stdout digest'
        Require ([int]$failureFacts.StderrByteCount -eq 0 -and [long]$failureFacts.StderrObservedBytes -eq 0) 'timeout retained stderr is empty'
        Assert-ImageIdentity $failureFacts $observed.Witness
        $stdoutRaw = [IO.File]::ReadAllBytes((Join-Path $ownerRoot 'stdout.log'))
        $stderrRaw = [IO.File]::ReadAllBytes((Join-Path $ownerRoot 'stderr.log'))
        Equal-Bytes $stdoutRaw $observed.Raw 'timeout stdout prefix evidence'
        Equal-Bytes $stderrRaw ([byte[]]@()) 'timeout stderr evidence'
        Require ($failureFacts.CleanupProven -eq $true -and $failureFacts.FailureLogsRetained -eq $true) 'timeout cleanup and evidence retained'
        Finish-Case 'image-timeout-failure-facts' $watch @{ operationOutcome='EXPECTED_TIMEOUT_FAILURE_NOT_PASS'; failureKind=$failureFacts.CaptureFailureKind; pid=$failureFacts.ProcessId; startTicks=$failureFacts.StartTicks; creationFileTime=$observed.Witness.creationFileTime; imagePath=$observed.Witness.imagePath; ownerEvidenceRoot=$ownerRoot; stdoutBytes=$stdoutRaw.Length; stdoutSha256=(Sha-Bytes $stdoutRaw); stderrBytes=$stderrRaw.Length; cleanupProven=$failureFacts.CleanupProven }

        $watch = [Diagnostics.Stopwatch]::StartNew()
        $call = New-FixtureCall 'image-getter-failure' 'sleep'
        $getterFailure = Invoke-GetterFailureCanary $call $watch
        Finish-Case 'image-getter-failure-lifecycle' $watch @{ operationOutcome='EXPECTED_GETTER_FAILURE_NOT_PASS'; failureKind=$getterFailure.Facts.CaptureFailureKind; pid=$getterFailure.Facts.ProcessId; startTicks=$getterFailure.Facts.StartTicks; witnessCreationFileTime=$getterFailure.Witness.creationFileTime; imageIdentityClaimed=$false; ownerEvidenceRoot=$getterFailure.OwnerRoot; stdoutPrefixBytes=$getterFailure.Stdout.Length; stdoutPrefixSha256=(Sha-Bytes $getterFailure.Stdout); stderrPrefixBytes=$getterFailure.Stderr.Length; cleanupProven=$getterFailure.Facts.CleanupProven; initializerRestored=$true }
        Expect-PrelaunchRefusal 'image-native-mode-guard' 'native' 'VERIFIER_INFRASTRUCTURE: Original-handle image identity is available only for direct bounded capture.'
    }

    $expectedCaseCount = 5
    if ($Mode -ceq 'Suite') { $expectedCaseCount = 10 }
    Require ($cases.Count -eq $expectedCaseCount) 'complete contract case count'
    Require ((Sha $ModulePath) -ceq $initialSources.module.sha256 -and [IO.FileInfo]::new($ModulePath).Length -eq $initialSources.module.bytes) 'final module pin'
    Require ((Sha $ChildFixture) -ceq $initialSources.fixture.sha256 -and [IO.FileInfo]::new($ChildFixture).Length -eq $initialSources.fixture.bytes) 'final fixture pin'
    Require ((Sha $ChildExecutable) -ceq $initialSources.child.sha256 -and [IO.FileInfo]::new($ChildExecutable).Length -eq $initialSources.child.bytes) 'final selected child image pin'
    Require ((Sha $hostExecutable) -ceq $initialSources.powershell.sha256 -and [IO.FileInfo]::new($hostExecutable).Length -eq $initialSources.powershell.bytes) 'final selected PowerShell pin'
    Save 'cases.json' @{ status='PASS'; mode=$Mode; caseCount=$cases.Count; cases=@($cases.ToArray()); backend='DIRECT_PROCESS'; imageIdentity='OPT_IN_ORIGINAL_PROCESS_HANDLE'; nativeJobCapture='NOT_TESTED'; descendantContainment='NOT_ATTESTED'; sourcePinsStable=$true }
    Write-Output ('Verifier retained-image identity contract cases=' + $cases.Count + ' status=PASS')
    exit 0
} catch {
    if ($rootReady) { Save 'failure.json' @{ status='FAIL_NOT_PASS'; type=$_.Exception.GetType().FullName; message=$_.Exception.Message; completedCases=@($cases.ToArray()) } }
    [Console]::Error.WriteLine('Verifier retained-image identity contract FAIL: ' + $_.Exception.GetType().FullName)
    exit 2
}