[CmdletBinding()]
param(
    [Parameter(Mandatory=$true)][string]$ModulePath,
    [Parameter(Mandatory=$true)][string]$ExpectedModuleSha256,
    [Parameter(Mandatory=$true)][long]$ExpectedModuleBytes,
    [Parameter(Mandatory=$true)][string]$ChildExecutable,
    [string]$ExpectedChildImageSha256 = 'ad169f4cb4bfb78c7a5c030a4529c19d6643276778e33994c93e145b6191c3ec',
    [Parameter(Mandatory=$true)][string]$ChildFixture,
    [Parameter(Mandatory=$true)][string]$ExpectedFixtureSha256,
    [Parameter(Mandatory=$true)][string]$WitnessPath,
    [Parameter(Mandatory=$true)][string]$ExpectedWitnessSha256,
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
function Sha-Bytes([byte[]]$Bytes) {
    $algorithm = [Security.Cryptography.SHA256]::Create()
    try { return ([BitConverter]::ToString($algorithm.ComputeHash($Bytes))).Replace('-','').ToLowerInvariant() }
    finally { $algorithm.Dispose() }
}
function Save([string]$Name, $Value) {
    $json = ($Value | ConvertTo-Json -Depth 20) + "`n"
    $body = [Text.UTF8Encoding]::new($false).GetBytes($json)
    $stream = [IO.File]::Open((Join-Path $EvidenceRoot $Name), [IO.FileMode]::CreateNew, [IO.FileAccess]::Write, [IO.FileShare]::Read)
    try { $stream.Write($body,0,$body.Length) } finally { $stream.Dispose() }
}
function Field($Value, [string]$Name) {
    if ($null -eq $Value) { throw ('missing capture field ' + $Name) }
    if ($Value -is [Collections.IDictionary]) {
        if (-not $Value.Contains($Name)) { throw ('missing capture field ' + $Name) }
        return $Value[$Name]
    }
    $property = $Value.PSObject.Properties[$Name]
    if ($null -eq $property) { throw ('missing capture field ' + $Name) }
    return $property.Value
}
function Repeat-Byte([byte]$Value, [int]$Count) {
    $bytes = [byte[]]::new($Count)
    for ($i=0; $i -lt $Count; $i++) { $bytes[$i]=$Value }
    return ,$bytes
}
function Repeat-Utf8Pair([int]$Count) {
    Require (($Count % 2) -eq 0) 'UTF-8 expected byte count must be even'
    $bytes = [byte[]]::new($Count)
    for ($i=0; $i -lt $Count; $i+=2) { $bytes[$i]=0xC3; $bytes[$i+1]=0xA9 }
    return ,$bytes
}
function Payload([string]$ChildMode, [string]$Stream, [int]$Count) {
    if ($ChildMode -ceq 'flood') { return ,(Repeat-Byte 70 $Count) }
    if ($ChildMode -ceq 'utf8' -and $Stream -ceq 'stdout') { return ,(Repeat-Utf8Pair $Count) }
    if ($Stream -ceq 'stdout') { return ,(Repeat-Byte 79 $Count) }
    return ,(Repeat-Byte 69 $Count)
}
function Equal-Array($Actual, $Expected, [string]$Label) {
    Require (@($Actual).Count -eq @($Expected).Count) ($Label + ' count')
    for ($i=0; $i -lt @($Expected).Count; $i++) {
        Require ($Actual[$i] -is [string] -and $Actual[$i] -ceq $Expected[$i]) ($Label + ' value')
    }
}
function Equal-Bytes([byte[]]$Actual, [byte[]]$Expected, [string]$Label) {
    Require ($Actual.Length -eq $Expected.Length) ($Label + ' length')
    for ($i=0; $i -lt $Expected.Length; $i++) {
        Require ($Actual[$i] -eq $Expected[$i]) ($Label + ' byte')
    }
}
function Observe-Witness($Witness, [string[]]$ExpectedArgv, [string]$Nonce) {
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
function Check-FactsCommon($Facts) {
    foreach ($name in @('ReaderTasksTerminal','ReadersClosed','CleanupProven')) {
        $value = Field $Facts $name
        Require ($value -is [bool] -and $value) ($name + ' must be true')
    }
    foreach ($name in @('OperationMilliseconds','CleanupMilliseconds')) {
        $value = Field $Facts $name
        Require ($value -is [ValueType] -and $value -isnot [bool]) ($name + ' must be numeric')
        $number = [double]$value
        Require ($number -ge 0 -and -not [double]::IsNaN($number) -and -not [double]::IsInfinity($number)) ($name + ' range')
    }
    foreach ($name in @('StdoutDiscardedDuringCleanup','StderrDiscardedDuringCleanup')) {
        Require ([long](Field $Facts $name) -ge 0) ($name + ' range')
    }
}
function Check-SuccessFacts($Facts, [byte[]]$Stdout, [byte[]]$Stderr) {
    Check-FactsCommon $Facts
    Require ([long](Field $Facts 'StdoutByteCount') -eq $Stdout.Length) 'stdout retained byte count'
    Require ([long](Field $Facts 'StderrByteCount') -eq $Stderr.Length) 'stderr retained byte count'
    Require ([long](Field $Facts 'StdoutObservedBytes') -eq $Stdout.Length) 'stdout observed byte count'
    Require ([long](Field $Facts 'StderrObservedBytes') -eq $Stderr.Length) 'stderr observed byte count'
    Require ((Field $Facts 'StdoutSha256') -ceq (Sha-Bytes $Stdout)) 'stdout retained SHA'
    Require ((Field $Facts 'StderrSha256') -ceq (Sha-Bytes $Stderr)) 'stderr retained SHA'
}
function Check-FailureFacts($Facts, [byte[]]$StdoutPrefix, [byte[]]$StderrPrefix, [long]$StdoutObserved, [long]$StderrObserved) {
    Check-FactsCommon $Facts
    Require ((Field $Facts 'FailureLogsRetained') -eq $true) 'failure prefix evidence retained'
    Require ([long](Field $Facts 'StdoutByteCount') -eq $StdoutPrefix.Length) 'failure stdout retained count'
    Require ([long](Field $Facts 'StderrByteCount') -eq $StderrPrefix.Length) 'failure stderr retained count'
    Require ([long](Field $Facts 'StdoutObservedBytes') -eq $StdoutObserved) 'failure stdout observed count'
    Require ([long](Field $Facts 'StderrObservedBytes') -eq $StderrObserved) 'failure stderr observed count'
    Require ((Field $Facts 'StdoutSha256') -ceq (Sha-Bytes $StdoutPrefix)) 'failure stdout retained SHA'
    Require ((Field $Facts 'StderrSha256') -ceq (Sha-Bytes $StderrPrefix)) 'failure stderr retained SHA'
}
function Finish-Case([string]$Name, $Watch, [string]$ExpectedOutcome, $Facts) {
    $Watch.Stop()
    $row = [ordered]@{ name=$Name; status='PASS'; expectedOutcome=$ExpectedOutcome; harnessMillis=$Watch.Elapsed.TotalMilliseconds; facts=$Facts }
    $cases.Add($row)
    Save ($Name + '.case.json') $row
}
function New-FixtureArgs([string]$Name, [string]$ChildMode, [long]$StdoutBytes, [long]$StderrBytes, [int]$ExitCode) {
    $state = Join-Path $EvidenceRoot ($Name + '.state.json')
    $nonce = [guid]::NewGuid().ToString('N')
    $args = @($WitnessPath,$ChildMode,$state,$nonce,
        $StdoutBytes.ToString([Globalization.CultureInfo]::InvariantCulture),
        $StderrBytes.ToString([Globalization.CultureInfo]::InvariantCulture),
        $ExitCode.ToString([Globalization.CultureInfo]::InvariantCulture))
    return @{ state=$state; nonce=$nonce; fixtureArgs=$args }
}
function Invoke-CaptureChild($FixtureArgs, [int]$TimeoutMilliseconds, [long]$MaxStdoutBytes, [long]$MaxStderrBytes, $CancellationToken) {
    $parameters = @{
        FilePath = $ChildExecutable
        Arguments = (@('-I','-B',$ChildFixture) + @($FixtureArgs))
        TimeoutMilliseconds = $TimeoutMilliseconds
        MaxStdoutBytes = $MaxStdoutBytes
        MaxStderrBytes = $MaxStderrBytes
    }
    if ($null -ne $CancellationToken) { $parameters.CancellationToken = $CancellationToken }
    return Invoke-VerifierBoundedProcess @parameters
}
function Run-Success([string]$Name, [string]$ChildMode, [long]$StdoutBytes, [long]$StderrBytes,
                     [long]$StdoutCap, [long]$StderrCap, [int]$ExitCode,
                     [byte[]]$ExpectedStdout, [byte[]]$ExpectedStderr) {
    $watch = [Diagnostics.Stopwatch]::StartNew()
    $child = New-FixtureArgs $Name $ChildMode $StdoutBytes $StderrBytes $ExitCode
    $result = Invoke-CaptureChild $child.fixtureArgs 15000 $StdoutCap $StderrCap $null
    Require ($result.ExitCode -is [int] -and $result.ExitCode -eq $ExitCode) 'child exit code'
    Require ($result.TerminationProven -eq $true) 'terminal child'
    Check-SuccessFacts $result.CaptureFacts $ExpectedStdout $ExpectedStderr
    Require ([long]$result.ProcessId -gt 0) 'retained PID'
    Require ([long]$result.ProcessStartTicks -gt 0) 'retained start identity'
    Require ($result.Stdout -is [string] -and $result.Stderr -is [string]) 'legacy text result fields'
    if ($ChildMode -ceq 'emit') {
        Require ($result.Stdout -ceq [Text.Encoding]::ASCII.GetString($ExpectedStdout)) 'ASCII stdout text'
        Require ($result.Stderr -ceq [Text.Encoding]::ASCII.GetString($ExpectedStderr)) 'ASCII stderr text'
    }

    Require ([IO.File]::Exists($child.state)) 'child state present'
    $witness = [IO.File]::ReadAllText($child.state) | ConvertFrom-Json
    Observe-Witness $witness $child.fixtureArgs $child.nonce
    Require ($result.ProcessId -eq [long]$witness.pid) 'retained PID matches witness'
    Require ($result.ProcessStartTicks -eq ([long]$witness.creationFileTime + 504911232000000000L)) 'retained start identity matches witness'

    foreach ($path in @($result.ProcessRoot,$result.StdoutLog,$result.StderrLog)) {
        Require ($path -is [string] -and [IO.Path]::IsPathRooted($path)) 'successful owner path present'
        Require (-not [IO.File]::Exists($path) -and -not [IO.Directory]::Exists($path)) 'successful owner logs cleaned'
    }
    Finish-Case $Name $watch 'SUCCESS' @{ pid=$result.ProcessId; startTicks=$result.ProcessStartTicks; exitCode=$result.ExitCode; stdoutBytes=$ExpectedStdout.Length; stderrBytes=$ExpectedStderr.Length; stdoutSha256=(Sha-Bytes $ExpectedStdout); stderrSha256=(Sha-Bytes $ExpectedStderr); cleanupProven=$true; operationMillis=$result.CaptureFacts.OperationMilliseconds; cleanupMillis=$result.CaptureFacts.CleanupMilliseconds; readerTasksTerminal=$result.CaptureFacts.ReaderTasksTerminal; readersClosed=$result.CaptureFacts.ReadersClosed }
}
function Assert-OwnerEvidencePath([string]$Message) {
    $match = [regex]::Match($Message, "Bounded-process evidence: '([^']+)'\.$")
    Require $match.Success 'bounded failure evidence path suffix'
    $path = [IO.Path]::GetFullPath($match.Groups[1].Value)
    $expectedParent = [IO.Path]::GetFullPath((Join-Path $tempRoot 'TroubleshootJS'))
    Require ([String]::Equals([IO.Path]::GetDirectoryName($path), $expectedParent, [StringComparison]::OrdinalIgnoreCase)) 'failure evidence namespace'
    Require ([IO.Path]::GetFileName($path) -cmatch '^verify-process-[0-9a-f]{32}$') 'failure evidence basename'
    Assert-VerifierNoReparseAncestors $path
    return $path
}
function Require-PidAbsent([long]$ProcessId, [long]$StartTicks) {
    $process = $null
    try { $process = [Diagnostics.Process]::GetProcessById([int]$ProcessId) }
    catch [ArgumentException] { return 'ABSENT' }
    try {
        $liveTicks = [long]$process.StartTime.ToUniversalTime().Ticks
        Require ($liveTicks -ne $StartTicks) 'owned child identity remains live'
        return 'PID_REUSED_WITH_DIFFERENT_START'
    } finally { $process.Dispose() }
}
function Run-LimitFailure([string]$Name, [string]$StreamName, [long]$StdoutBytes, [long]$StderrBytes,
                          [long]$StdoutCap, [long]$StderrCap, [string]$ChildMode) {
    $watch = [Diagnostics.Stopwatch]::StartNew()
    $child = New-FixtureArgs $Name $ChildMode $StdoutBytes $StderrBytes 0
    $failure = $null
    try { $null = Invoke-CaptureChild $child.fixtureArgs 15000 $StdoutCap $StderrCap $null }
    catch { $failure = $_ }
    Require ($null -ne $failure -and (Test-VerifierInfrastructureError $failure)) 'capture limit must fail as infrastructure'
    Require ($failure.Exception.Message.StartsWith('VERIFIER_INFRASTRUCTURE:', [StringComparison]::Ordinal)) 'capture error prefix'
    Require ($failure.Exception.Message.Contains($StreamName + ' byte limit')) 'stream-specific byte limit failure'
    $failureData = $failure.Exception.Data['VerifierCapture']
    Save ($Name + '.failure-observed.json') @{ message=$failure.Exception.Message; captureFacts=$failureData }
    Require ($null -ne $failureData) 'failure capture facts retained'
    $kind = [string](Field $failureData 'CaptureFailureKind')
    Require ($kind -match '(?i)limit') 'limit failure kind'
    $logRoot = Assert-OwnerEvidencePath $failure.Exception.Message
    $stdoutLog = Join-Path $logRoot 'stdout.log'
    $stderrLog = Join-Path $logRoot 'stderr.log'
    Require ([IO.File]::Exists($stdoutLog) -and [IO.File]::Exists($stderrLog)) 'bounded raw logs retained'

    $stdoutExpected = Payload $ChildMode 'stdout' ([int]$StdoutBytes)
    $stderrExpected = Payload $ChildMode 'stderr' ([int]$StderrBytes)
    $stdoutPrefixLength = [int][Math]::Min($StdoutCap,$StdoutBytes)
    $stderrPrefixLength = [int][Math]::Min($StderrCap,$StderrBytes)
    $stdoutPrefix = [byte[]]::new($stdoutPrefixLength)
    $stderrPrefix = [byte[]]::new($stderrPrefixLength)
    if ($stdoutPrefixLength -gt 0) { [Array]::Copy($stdoutExpected,$stdoutPrefix,$stdoutPrefixLength) }
    if ($stderrPrefixLength -gt 0) { [Array]::Copy($stderrExpected,$stderrPrefix,$stderrPrefixLength) }
    $stdoutRaw = [IO.File]::ReadAllBytes($stdoutLog)
    $stderrRaw = [IO.File]::ReadAllBytes($stderrLog)
    Require ($stdoutRaw.Length -le $StdoutCap -and $stderrRaw.Length -le $StderrCap) 'raw prefix stays within each cap'
    Equal-Bytes $stdoutRaw $stdoutPrefix 'raw stdout prefix'
    Equal-Bytes $stderrRaw $stderrPrefix 'raw stderr prefix'

    $stdoutObserved = $StdoutBytes
    $stderrObserved = $StderrBytes
    if ($StreamName -ceq 'stdout') { $stdoutObserved = $StdoutCap + 1 }
    else { $stderrObserved = $StderrCap + 1 }
    Check-FailureFacts $failureData $stdoutPrefix $stderrPrefix $stdoutObserved $stderrObserved
    $processId = [long](Field $failureData 'ProcessId')
    $startTicks = [long](Field $failureData 'StartTicks')
    Require ($processId -gt 0 -and $startTicks -gt 0) 'failed child identity facts'
    Require ([IO.File]::Exists($child.state)) 'failed child state present'
    $witness = [IO.File]::ReadAllText($child.state) | ConvertFrom-Json
    Observe-Witness $witness $child.fixtureArgs $child.nonce
    Require ($processId -eq [long]$witness.pid) 'failed PID matches witness'
    Require ($startTicks -eq ([long]$witness.creationFileTime + 504911232000000000L)) 'failed start identity matches witness'
    $pidStatus = Require-PidAbsent $processId $startTicks
    Finish-Case $Name $watch 'EXPECTED_LIMIT_FAILURE' @{ processId=$processId; startTicks=$startTicks; failureKind=$kind; stdoutRetainedBytes=$stdoutRaw.Length; stderrRetainedBytes=$stderrRaw.Length; stdoutObservedBytes=$stdoutObserved; stderrObservedBytes=$stderrObserved; cleanupProven=$true; pidStatus=$pidStatus; evidenceFilesRetained=$true }
}
function Run-TerminalFailure([string]$Name, [string]$ExpectedMessage, [string]$ExpectedKind,
                             [int]$TimeoutMilliseconds, $CancellationToken) {
    $watch = [Diagnostics.Stopwatch]::StartNew()
    $child = New-FixtureArgs $Name 'hold' 0 0 0
    $failure = $null
    try { $null = Invoke-CaptureChild $child.fixtureArgs $TimeoutMilliseconds 1024 1024 $CancellationToken }
    catch { $failure = $_ }
    Require ($null -ne $failure -and (Test-VerifierInfrastructureError $failure)) 'terminal event must fail as infrastructure'
    Require ($failure.Exception.Message.StartsWith('VERIFIER_INFRASTRUCTURE:', [StringComparison]::Ordinal)) 'terminal error prefix'
    Require ($failure.Exception.Message.Contains($ExpectedMessage)) 'timeout or cancellation message'
    $failureData = $failure.Exception.Data['VerifierCapture']
    Save ($Name + '.failure-observed.json') @{ message=$failure.Exception.Message; captureFacts=$failureData }
    Require ($null -ne $failureData) 'terminal failure capture facts'
    $kind = [string](Field $failureData 'CaptureFailureKind')
    Require ($kind -match $ExpectedKind) 'terminal failure kind'
    $logRoot = Assert-OwnerEvidencePath $failure.Exception.Message
    $stdoutLog = Join-Path $logRoot 'stdout.log'
    $stderrLog = Join-Path $logRoot 'stderr.log'
    Require ([IO.File]::Exists($stdoutLog) -and [IO.File]::Exists($stderrLog)) 'terminal raw logs retained'
    $stdoutRaw = [IO.File]::ReadAllBytes($stdoutLog)
    $stderrRaw = [IO.File]::ReadAllBytes($stderrLog)
    $empty = [byte[]]::new(0)
    Equal-Bytes $stdoutRaw $empty 'terminal stdout log'
    Equal-Bytes $stderrRaw $empty 'terminal stderr log'
    Check-FailureFacts $failureData $empty $empty 0 0

    Require ([IO.File]::Exists($child.state)) 'terminal child state present'
    $witness = [IO.File]::ReadAllText($child.state) | ConvertFrom-Json
    Observe-Witness $witness $child.fixtureArgs $child.nonce
    $processId = [long](Field $failureData 'ProcessId')
    $startTicks = [long](Field $failureData 'StartTicks')
    Require ($processId -eq [long]$witness.pid) 'terminal PID matches witness'
    Require ($startTicks -eq ([long]$witness.creationFileTime + 504911232000000000L)) 'terminal start identity matches witness'
    $pidStatus = Require-PidAbsent $processId $startTicks
    Finish-Case $Name $watch 'EXPECTED_TERMINAL_FAILURE' @{ processId=$processId; startTicks=$startTicks; failureKind=$kind; cleanupProven=$true; pidStatus=$pidStatus; evidenceFilesRetained=$true }
}
function Run-PrelaunchReject([string]$Name, [bool]$IncludeStdoutCap, [object]$StdoutCap,
                             [bool]$IncludeStderrCap, [object]$StderrCap,
                             [bool]$IncludeToken, [object]$CancellationToken) {
    $watch = [Diagnostics.Stopwatch]::StartNew()
    $child = New-FixtureArgs $Name 'emit' 0 0 0
    $parameters = @{
        FilePath = $ChildExecutable
        Arguments = (@('-I','-B',$ChildFixture) + @($child.fixtureArgs))
        TimeoutMilliseconds = 15000
    }
    if ($IncludeStdoutCap) { $parameters.MaxStdoutBytes = $StdoutCap }
    if ($IncludeStderrCap) { $parameters.MaxStderrBytes = $StderrCap }
    if ($IncludeToken) { $parameters.CancellationToken = $CancellationToken }
    $failure = $null
    try { $null = Invoke-VerifierBoundedProcess @parameters }
    catch { $failure = $_ }
    Require ($null -ne $failure -and (Test-VerifierInfrastructureError $failure)) 'invalid bounded options must be infrastructure'
    Require ($failure.Exception.Message.StartsWith('VERIFIER_INFRASTRUCTURE:', [StringComparison]::Ordinal)) 'prelaunch guard prefix'
    $expectedMessage = 'VERIFIER_INFRASTRUCTURE: Bounded capture byte limits must be exact positive integral scalars.'
    if ($Name -ceq 'pre-cancelled') { $expectedMessage = 'VERIFIER_INFRASTRUCTURE: Bounded process capture cancelled before process start.' }
    elseif ($Name -ceq 'missing-stdout-cap' -or $Name -ceq 'missing-stderr-cap') { $expectedMessage = 'VERIFIER_INFRASTRUCTURE: Bounded capture requires explicit stdout and stderr byte limits.' }
    elseif ($Name -ceq 'invalid-token') { $expectedMessage = 'VERIFIER_INFRASTRUCTURE: Bounded capture cancellation must be one exact CancellationToken.' }
    Require ($failure.Exception.Message -ceq $expectedMessage) 'exact prelaunch guard reason'
    Require (-not [IO.File]::Exists($child.state)) 'prelaunch guard starts no child'
    Finish-Case $Name $watch 'EXPECTED_PRELAUNCH_REJECTION' @{ guard='PRE_START'; stateAbsent=$true; processAudit='NOT_PERFORMED' }
}
function Run-SettlementDeadline([string]$Name) {
    $watch = [Diagnostics.Stopwatch]::StartNew()
    $child = New-FixtureArgs $Name 'emit' 4 2 0
    $saved = & $owner { (Get-Item Function:Get-VerifierBoundedCaptureFacts).ScriptBlock }
    $failure = $null
    $hits = 0
    try {
        & $owner {
            param($original)
            if (Test-Path Variable:script:N57OriginalFacts) { throw 'fresh settlement scope required' }
            $script:N57OriginalFacts = $original
            $script:N57FactsHits = 0
            Set-Item Function:script:Get-VerifierBoundedCaptureFacts -Value {
                param($State,$ProcessId,$StartTicks)
                $result = & $script:N57OriginalFacts $State $ProcessId $StartTicks
                $script:N57FactsHits++
                if ($script:N57FactsHits -eq 1) { [Threading.Thread]::Sleep(1100) }
                return $result
            }
        } $saved
        try { $null = Invoke-CaptureChild $child.fixtureArgs 1000 4 2 $null }
        catch { $failure = $_ }
        $hits = & $owner { $script:N57FactsHits }
    } finally {
        & $owner {
            param($original)
            Set-Item Function:script:Get-VerifierBoundedCaptureFacts -Value $original
            foreach ($name in @('N57OriginalFacts','N57FactsHits')) {
                if (Test-Path ('Variable:script:'+$name)) { Remove-Variable -Scope Script -Name $name }
            }
        } $saved
    }
    Require ((& $owner { (Get-Item Function:Get-VerifierBoundedCaptureFacts).ScriptBlock.ToString() }) -ceq $saved.ToString()) 'settlement hook restored'
    Require ($hits -eq 2) 'real capture reached settlement and failure facts'
    Require ($null -ne $failure -and (Test-VerifierInfrastructureError $failure)) 'late settlement cannot return success'
    Require ($failure.Exception.Message.Contains('during capture settlement')) 'settlement deadline observed'
    $facts = $failure.Exception.Data['VerifierCapture']
    Save ($Name+'.failure-observed.json') @{message=$failure.Exception.Message; captureFacts=$facts}
    Check-FailureFacts $facts (Repeat-Byte 79 4) (Repeat-Byte 69 2) 4 2
    Require ((Field $facts 'CaptureFailureKind') -ceq 'TIMEOUT') 'settlement timeout kind'
    Require ([double](Field $facts 'OperationMilliseconds') -ge 1000) 'settlement exceeded fixed test budget'
    $logRoot = Assert-OwnerEvidencePath $failure.Exception.Message
    Equal-Bytes ([IO.File]::ReadAllBytes((Join-Path $logRoot 'stdout.log'))) (Repeat-Byte 79 4) 'settlement stdout prefix'
    Equal-Bytes ([IO.File]::ReadAllBytes((Join-Path $logRoot 'stderr.log'))) (Repeat-Byte 69 2) 'settlement stderr prefix'
    $witness = [IO.File]::ReadAllText($child.state) | ConvertFrom-Json
    Observe-Witness $witness $child.fixtureArgs $child.nonce
    Require ((Field $facts 'ProcessId') -eq [long]$witness.pid -and (Field $facts 'StartTicks') -eq ([long]$witness.creationFileTime+504911232000000000L)) 'settlement child identity'
    $pidStatus = Require-PidAbsent ([long]$witness.pid) ([long]$witness.creationFileTime+504911232000000000L)
    Finish-Case $Name $watch 'EXPECTED_CONTROLLED_SETTLEMENT_TIMEOUT_NOT_PASS' @{controlledDelayMillis=1100; operationMillis=(Field $facts 'OperationMilliseconds'); cleanupMillis=(Field $facts 'CleanupMilliseconds'); cleanupProven=$true; hookRestored=$true; pidStatus=$pidStatus}
}
function Run-CleanupEvidenceFailure([string]$Name) {
    $watch = [Diagnostics.Stopwatch]::StartNew()
    $child = New-FixtureArgs $Name 'emit' 4 2 0
    $saved = & $owner { (Get-Item Function:Close-VerifierBoundedCapture).ScriptBlock }
    $failure = $null
    try {
        & $owner {
            param($original)
            if (Test-Path Variable:script:N57OriginalClose) { throw 'fresh cleanup injection scope required' }
            Set-Variable -Scope Script -Name N57OriginalClose -Value $original
            Set-Item Function:script:Close-VerifierBoundedCapture -Value {
                param($State)
                & $script:N57OriginalClose $State
                throw [IO.IOException]::new('N57_INJECTED_AFTER_REAL_DRAIN')
            }
        } $saved
        try { $null = Invoke-CaptureChild $child.fixtureArgs 15000 4 2 $null }
        catch { $failure = $_ }
    } finally {
        & $owner {
            param($original)
            Set-Item Function:script:Close-VerifierBoundedCapture -Value $original
            if (Test-Path Variable:script:N57OriginalClose) {
                Remove-Variable -Scope Script -Name N57OriginalClose
            }
        } $saved
    }
    $restored = & $owner { (Get-Item Function:Close-VerifierBoundedCapture).ScriptBlock.ToString() }
    Require ($restored -ceq $saved.ToString()) 'cleanup hook restored exactly'
    Require ($null -ne $failure -and (Test-VerifierInfrastructureError $failure)) 'injected cleanup must fail'
    Require ($failure.Exception.Message.Contains('N57_INJECTED_AFTER_REAL_DRAIN')) 'injected failure observed'
    $facts = $failure.Exception.Data['VerifierCapture']
    Save ($Name + '.failure-observed.json') @{message=$failure.Exception.Message; captureFacts=$facts}
    Require ((Field $facts 'ReaderTasksTerminal') -eq $true -and (Field $facts 'ReadersClosed') -eq $true) 'real readers settled before injection'
    Require ((Field $facts 'CleanupProven') -eq $false) 'injected cleanup never reported proven'
    Require ((Field $facts 'FailureLogsRetained') -eq $true) 'failed cleanup retains prefix evidence'
    $logRoot = Assert-OwnerEvidencePath $failure.Exception.Message
    Equal-Bytes ([IO.File]::ReadAllBytes((Join-Path $logRoot 'stdout.log'))) (Repeat-Byte 79 4) 'failed cleanup stdout'
    Equal-Bytes ([IO.File]::ReadAllBytes((Join-Path $logRoot 'stderr.log'))) (Repeat-Byte 69 2) 'failed cleanup stderr'
    $witness = [IO.File]::ReadAllText($child.state) | ConvertFrom-Json
    Observe-Witness $witness $child.fixtureArgs $child.nonce
    Require ((Field $facts 'ProcessId') -eq [long]$witness.pid -and (Field $facts 'StartTicks') -eq ([long]$witness.creationFileTime + 504911232000000000L)) 'injected child identity'
    $pidStatus = Require-PidAbsent ([long]$witness.pid) ([long]$witness.creationFileTime + 504911232000000000L)
    Finish-Case $Name $watch 'EXPECTED_INJECTED_CLEANUP_FAILURE_NOT_PASS' @{cleanupProven=$false; prefixEvidenceRetained=$true; readersTerminal=$true; pidStatus=$pidStatus; hookRestored=$true}
}
function Run-LegacyNoCap([string]$Name) {
    $watch = [Diagnostics.Stopwatch]::StartNew()
    $stdoutBytes = 65536
    $stderrBytes = 32
    $child = New-FixtureArgs $Name 'emit' $stdoutBytes $stderrBytes 0
    # This intentionally uses only the established three-parameter call.
    $result = Invoke-VerifierBoundedProcess -FilePath $ChildExecutable `
        -Arguments (@('-I','-B',$ChildFixture) + @($child.fixtureArgs)) `
        -TimeoutMilliseconds 15000
    Require ($result.ExitCode -is [int] -and $result.ExitCode -eq 0) 'legacy exit code'
    Require ($result.TerminationProven -eq $true) 'legacy terminal child'
    Require ($null -eq $result.PSObject.Properties['CaptureFacts']) 'legacy call must not create capture facts'
    $stdout = [Text.Encoding]::ASCII.GetBytes([string]$result.Stdout)
    $stderr = [Text.Encoding]::ASCII.GetBytes([string]$result.Stderr)
    Equal-Bytes $stdout (Repeat-Byte 79 $stdoutBytes) 'legacy stdout full stream'
    Equal-Bytes $stderr (Repeat-Byte 69 $stderrBytes) 'legacy stderr full stream'
    Require ([IO.File]::Exists($child.state)) 'legacy child state present'
    $witness = [IO.File]::ReadAllText($child.state) | ConvertFrom-Json
    Observe-Witness $witness $child.fixtureArgs $child.nonce
    Require ($result.ProcessId -eq [long]$witness.pid) 'legacy PID matches witness'
    Require ($result.ProcessStartTicks -eq ([long]$witness.creationFileTime + 504911232000000000L)) 'legacy start identity matches witness'
    foreach ($path in @($result.ProcessRoot,$result.StdoutLog,$result.StderrLog)) {
        Require ($path -is [string] -and [IO.Path]::IsPathRooted($path)) 'legacy owner path present'
        Require (-not [IO.File]::Exists($path) -and -not [IO.Directory]::Exists($path)) 'legacy owner logs cleaned'
    }
    Finish-Case $Name $watch 'LEGACY_THREE_ARGUMENTS' @{ pid=$result.ProcessId; startTicks=$result.ProcessStartTicks; exitCode=$result.ExitCode; stdoutBytes=$stdout.Length; stderrBytes=$stderr.Length; captureFacts='ABSENT' }
}

try {
    Require ($PSVersionTable.PSVersion.Major -eq 5 -and $PSVersionTable.PSVersion.Minor -eq 1) 'Windows PowerShell 5.1 required'
    Require (Test-Path -LiteralPath $ModulePath -PathType Leaf) 'module file missing'
    Require ([IO.FileInfo]::new($ModulePath).Length -eq $ExpectedModuleBytes -and $ExpectedModuleSha256 -cmatch '^[0-9a-f]{64}$' -and (Sha $ModulePath) -ceq $ExpectedModuleSha256) 'module pin'
    Require ($ExpectedFixtureSha256 -cmatch '^[0-9a-f]{64}$' -and (Sha $ChildFixture) -ceq $ExpectedFixtureSha256) 'child fixture pin'
    Require ($ExpectedWitnessSha256 -cmatch '^[0-9a-f]{64}$' -and (Sha $WitnessPath) -ceq $ExpectedWitnessSha256) 'witness pin'
    Require ($ExpectedChildImageSha256 -cmatch '^[0-9a-f]{64}$' -and (Sha $ChildExecutable) -ceq $ExpectedChildImageSha256) 'selected child image pin'

    $owner = Import-Module -Name $ModulePath -Force -DisableNameChecking -PassThru
    $ModulePath = [IO.Path]::GetFullPath($ModulePath)
    $ChildExecutable = [IO.Path]::GetFullPath($ChildExecutable)
    $ChildFixture = [IO.Path]::GetFullPath($ChildFixture)
    $WitnessPath = [IO.Path]::GetFullPath($WitnessPath)
    $EvidenceRoot = [IO.Path]::GetFullPath($EvidenceRoot)
    $tempRoot = [IO.Path]::GetFullPath([IO.Path]::GetTempPath())
    Assert-VerifierNoReparseAncestors $EvidenceRoot
    Require (Test-VerifierPhysicalChildPath $tempRoot $EvidenceRoot) 'evidence root must be a physical temp child'
    Require (-not [IO.Directory]::Exists($EvidenceRoot) -and -not [IO.File]::Exists($EvidenceRoot)) 'fresh evidence root required'
    $null = [IO.Directory]::CreateDirectory($EvidenceRoot)
    $rootReady = $true
    Assert-VerifierNoReparseAncestors $EvidenceRoot
    $sourceHashes = @{ module=(Sha $ModulePath); fixture=(Sha $ChildFixture); witness=(Sha $WitnessPath); executable=(Sha $ChildExecutable) }
    Save 'inputs.json' @{ hashes=$sourceHashes; powershellVersion=$PSVersionTable.PSVersion.ToString(); mode=$Mode }

    if ($Mode -ceq 'Smoke') {
        $stdout = Repeat-Byte 79 4
        $stderr = Repeat-Byte 69 2
        Run-Success 'smoke-exact-cap' 'emit' 4 2 4 2 0 $stdout $stderr
        Run-LimitFailure 'smoke-flood-cap' 'stdout' 1048576 0 16 8 'flood'
        $expectedCaseNames = @('smoke-exact-cap','smoke-flood-cap')
    }
    else {
        $empty = [byte[]]::new(0)
        Run-Success 'smoke-exact-cap' 'emit' 4 2 4 2 0 (Repeat-Byte 79 4) (Repeat-Byte 69 2)
        Run-LimitFailure 'smoke-flood-cap' 'stdout' 1048576 0 16 8 'flood'

        Run-Success 'stdout-below-cap' 'emit' 3 0 4 1 0 (Repeat-Byte 79 3) $empty
        Run-Success 'stdout-exact-cap' 'emit' 4 0 4 1 0 (Repeat-Byte 79 4) $empty
        Run-LimitFailure 'stdout-one-over-cap' 'stdout' 5 0 4 1 'emit'
        Run-Success 'stderr-below-cap' 'emit' 0 3 1 4 0 $empty (Repeat-Byte 69 3)
        Run-Success 'stderr-exact-cap' 'emit' 0 4 1 4 0 $empty (Repeat-Byte 69 4)
        Run-LimitFailure 'stderr-one-over-cap' 'stderr' 0 5 1 4 'emit'

        Run-Success 'duplex-65536' 'emit' 65536 65536 65536 65536 0 (Repeat-Byte 79 65536) (Repeat-Byte 69 65536)
        Run-Success 'utf8-exact-four-bytes' 'utf8' 4 1 4 1 0 (Repeat-Utf8Pair 4) (Repeat-Byte 69 1)
        Run-LimitFailure 'utf8-one-over-cap' 'stdout' 4 0 3 1 'utf8'
        Run-Success 'zero-output-exit-seven' 'emit' 0 0 1 1 7 $empty $empty

        Run-TerminalFailure 'hold-timeout' 'exceeded 1500ms' '(?i)timeout' 1500 $null
        $cancelSource = [Threading.CancellationTokenSource]::new()
        try {
            $cancelSource.CancelAfter(500)
            Run-TerminalFailure 'hold-cancelled' 'cancelled' '(?i)cancel' 10000 $cancelSource.Token
        } finally { $cancelSource.Dispose() }

        $preCancelled = [Threading.CancellationTokenSource]::new()
        try {
            $preCancelled.Cancel()
            Run-PrelaunchReject 'pre-cancelled' $true 8 $true 8 $true $preCancelled.Token
        } finally { $preCancelled.Dispose() }
        Run-PrelaunchReject 'missing-stdout-cap' $false $null $true 8 $false $null
        Run-PrelaunchReject 'missing-stderr-cap' $true 8 $false $null $false $null

        $badArray = [object[]]@(5)
        $badMap = @{}
        $tooLargeCap = [long]2147483648
        $invalidCaps = @(
            @{ name='zero'; value=0 }, @{ name='negative'; value=-1 },
            @{ name='string'; value='5' }, @{ name='bool'; value=$true },
            @{ name='null'; value=$null }, @{ name='fraction'; value=1.5 },
            @{ name='array'; value=$badArray }, @{ name='map'; value=$badMap },
            @{ name='over-intmax'; value=$tooLargeCap }
        )
        foreach ($bad in $invalidCaps) {
            Run-PrelaunchReject ('invalid-cap-' + $bad.name) $true $bad.value $true 8 $false $null
        }
        Run-PrelaunchReject 'invalid-token' $true 8 $true 8 $true 'not-a-cancellation-token'
        Run-SettlementDeadline 'settlement-deadline'
        Run-CleanupEvidenceFailure 'cleanup-failure-retains-prefix'
        Run-LegacyNoCap 'legacy-no-cap'

        $expectedCaseNames = @(
            'smoke-exact-cap','smoke-flood-cap',
            'stdout-below-cap','stdout-exact-cap','stdout-one-over-cap',
            'stderr-below-cap','stderr-exact-cap','stderr-one-over-cap',
            'duplex-65536','utf8-exact-four-bytes','utf8-one-over-cap','zero-output-exit-seven',
            'hold-timeout','hold-cancelled','pre-cancelled','missing-stdout-cap','missing-stderr-cap',
            'invalid-cap-zero','invalid-cap-negative','invalid-cap-string','invalid-cap-bool',
            'invalid-cap-null','invalid-cap-fraction','invalid-cap-array','invalid-cap-map',
            'invalid-cap-over-intmax','invalid-token','settlement-deadline','cleanup-failure-retains-prefix','legacy-no-cap'
        )
    }

    Require ($cases.Count -eq $expectedCaseNames.Count) 'complete case count'
    for ($i=0; $i -lt $expectedCaseNames.Count; $i++) { Require ($cases[$i].name -ceq $expectedCaseNames[$i]) 'case order' }
    Require ((Sha $ModulePath) -ceq $sourceHashes.module -and (Sha $ChildFixture) -ceq $sourceHashes.fixture -and (Sha $WitnessPath) -ceq $sourceHashes.witness -and (Sha $ChildExecutable) -ceq $sourceHashes.executable) 'final source and image pins'
    Save 'cases.json' @{ status='PASS'; mode=$Mode; caseCount=$cases.Count; cases=@($cases.ToArray()); selectedScope='DIRECT_CHILD_ONLY'; descendants='NOT_QUALIFIED' }
    Write-Output ('Verifier bounded capture contract cases=' + $cases.Count + ' status=PASS')
    exit 0
} catch {
    if ($rootReady) {
        try { Save 'failure.json' @{ status='FAIL_NOT_PASS'; type=$_.Exception.GetType().FullName; message=$_.Exception.Message; completedCases=@($cases.ToArray()) } }
        catch { }
    }
    [Console]::Error.WriteLine('Verifier bounded capture contract FAIL: ' + $_.Exception.GetType().FullName)
    exit 2
}
