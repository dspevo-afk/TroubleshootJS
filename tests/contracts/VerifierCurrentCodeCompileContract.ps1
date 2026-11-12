[CmdletBinding()]
param(
    [Parameter(Mandatory=$true)][string]$ModulePath,
    [Parameter(Mandatory=$true)][string]$InvocationPath,
    [Parameter(Mandatory=$true)][string]$ExpectedInvocationSha256,
    [Parameter(Mandatory=$true)][long]$ExpectedInvocationBytes,
    [Parameter(Mandatory=$true)][string]$EvidenceRoot,
    [ValidateSet('Compile','HeldMain')][string]$Role='Compile'
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
$owner = $jobType = $job = $result = $nativeFailure = $primaryFailure = $cleanupFailure = $jobCleanup = $null
$rootCreated = $false; $operationWatch = [Diagnostics.Stopwatch]::new()
$jobCleanupAttempted = $false
$script:retainedUnprovenJob = $null
$script:active = $null
$PinnedModuleSha256 = '418da63a7bc1e4eb3ad0234bd789a24db2b911b635e6a54468a7bb7087c180a6'
$PinnedModuleBytes = 1092245L
$PinnedPowerShellSha256 = 'd8f182560210d43baaf767d482ad1dc1207372522e91413b9d5e515a875e552d'
$PinnedPowerShellBytes = 495616L
$PinnedJavacSha256 = '61fa6463ee5b59004719b9833a16b148740cfa9f2d1f5a95ca598cc90be04c64'
$PinnedJavacBytes = 24192L
$PinnedJavaSha256 = 'e71f3022c972743d2a37e2597b45282b9c380211cd491c54a0c3d63c11bcfbce'
$PinnedJavaBytes = 304768L
$MaximumInvocationBytes = 65536L
function Require([bool]$Condition, [string]$Message) { if (-not $Condition) { throw [InvalidOperationException]::new($Message) } }
function Assert-CanonicalAbsolute([string]$Path, [string]$Label) {
    Require ([IO.Path]::IsPathRooted($Path) -and [IO.Path]::GetFullPath($Path) -ceq $Path) ($Label + ' must be canonical and absolute')
}
function Sha([string]$Path) {
    $stream = [IO.File]::OpenRead($Path)
    $algorithm = [Security.Cryptography.SHA256]::Create()
    try { return ([BitConverter]::ToString($algorithm.ComputeHash($stream))).Replace('-','').ToLowerInvariant() }
    finally { $algorithm.Dispose(); $stream.Dispose() }
}
function Save-Json([string]$Path, $Value) {
    $bytes = [Text.UTF8Encoding]::new($false).GetBytes((($Value | ConvertTo-Json -Depth 20) + "`n"))
    $stream = [IO.File]::Open($Path, [IO.FileMode]::CreateNew, [IO.FileAccess]::Write, [IO.FileShare]::None)
    try { $stream.Write($bytes, 0, $bytes.Length); $stream.Flush() }
    finally { $stream.Dispose() }
}
function Assert-Tuple([string]$Path, [string]$ExpectedSha256, [long]$ExpectedBytes, [string]$Label) {
    Require ([IO.File]::Exists($Path)) ($Label + ' file missing')
    Require ([IO.FileInfo]::new($Path).Length -eq $ExpectedBytes) ($Label + ' byte count mismatch')
    Require ((Sha $Path) -ceq $ExpectedSha256) ($Label + ' SHA-256 mismatch')
}
function Close-CreatorJob($Job) {
    $watch = [Diagnostics.Stopwatch]::StartNew()
    $initialIds = @()
    $memberIds = @()
    $initialObserved = $finalObserved = $disposalAttempted = $disposed = $terminationRequested = $false
    $failure = $null
    try {
        $initialIds = @($Job.GetMemberProcessIds() | ForEach-Object { [long]$_ }); $initialObserved = $true; $memberIds = @($initialIds)
        if ($initialIds.Count -gt 0) {
            $terminationRequested = $true; $Job.TerminateOwned([uint32]2)
        }
        while ($memberIds.Count -gt 0 -and $watch.ElapsedMilliseconds -lt 5000) {
            Start-Sleep -Milliseconds 20; $memberIds = @($Job.GetMemberProcessIds() | ForEach-Object { [long]$_ })
        }
        $finalObserved = ($memberIds.Count -eq 0)
        if (-not $finalObserved) { throw 'caller creator job did not become empty within 5000ms' }
        $disposalAttempted = $true; $Job.Dispose(); $disposed = $true
    } catch { $failure = $_ }
    $watch.Stop()
    if (-not $disposed) { $script:retainedUnprovenJob = $Job }
    return [pscustomobject]@{ initialMemberIds=@($initialIds); finalMemberIds=@($memberIds); initialMembershipObserved=$initialObserved
        emptyMembershipObserved=$finalObserved; terminationRequested=$terminationRequested; disposalAttempted=$disposalAttempted
        disposed=$disposed; cleanupMilliseconds=$watch.Elapsed.TotalMilliseconds
        failure=if ($null -ne $failure) { $failure.Exception.Message } else { $null } }
}
try {
    Require ($PSVersionTable.PSVersion.Major -eq 5 -and $PSVersionTable.PSVersion.Minor -eq 1) 'Windows PowerShell 5.1 required'
    Require ($ExpectedInvocationSha256 -cmatch '^[0-9a-f]{64}$') 'invocation frame expected SHA malformed'
    Require ($ExpectedInvocationBytes -gt 0 -and $ExpectedInvocationBytes -le $MaximumInvocationBytes) 'invocation frame byte limit invalid'
    $InvocationPath = [IO.Path]::GetFullPath($InvocationPath)
    Require ([IO.File]::Exists($InvocationPath)) 'invocation frame missing'
    Require ([IO.FileInfo]::new($InvocationPath).Length -eq $ExpectedInvocationBytes) 'invocation frame size mismatch'
    Require ((Sha $InvocationPath) -ceq $ExpectedInvocationSha256) 'invocation frame SHA mismatch'
    $requestBytes = [IO.File]::ReadAllBytes($InvocationPath)
    Require ($requestBytes.Length -eq $ExpectedInvocationBytes) 'invocation frame bytes changed during read'
    $requestAlgorithm = [Security.Cryptography.SHA256]::Create()
    try { $requestSha256 = ([BitConverter]::ToString($requestAlgorithm.ComputeHash($requestBytes))).Replace('-','').ToLowerInvariant() }
    finally { $requestAlgorithm.Dispose() }
    Require ($requestSha256 -ceq $ExpectedInvocationSha256) 'invocation frame SHA changed during read'
    $requestText = [Text.UTF8Encoding]::new($false, $true).GetString($requestBytes)
    $request = $requestText | ConvertFrom-Json -ErrorAction Stop
    $propertyNames = @($request.PSObject.Properties | ForEach-Object { [string]$_.Name })
    $expectedNames = @('Arguments', 'FilePath', 'MaxStderrBytes', 'MaxStdoutBytes', 'TimeoutMilliseconds', 'WorkingDirectory')
    Require ($propertyNames.Count -eq $expectedNames.Count) 'invocation frame key count mismatch'
    foreach ($name in $expectedNames) { Require ($propertyNames -ccontains $name) ('invocation frame key missing: ' + $name) }
    Require ($request.FilePath -is [string] -and -not [String]::IsNullOrWhiteSpace($request.FilePath)) 'invocation FilePath must be a nonempty string'
    Require ($request.WorkingDirectory -is [string] -and -not [String]::IsNullOrWhiteSpace($request.WorkingDirectory)) 'invocation WorkingDirectory must be a nonempty string'
    Require ([IO.Path]::IsPathRooted($request.FilePath) -and [IO.Path]::GetFullPath($request.FilePath) -ceq $request.FilePath) 'invocation FilePath must be canonical and absolute'
    Require ([IO.Path]::IsPathRooted($request.WorkingDirectory) -and [IO.Path]::GetFullPath($request.WorkingDirectory) -ceq $request.WorkingDirectory) 'invocation WorkingDirectory must be canonical and absolute'
    Require ([IO.Directory]::Exists($request.WorkingDirectory)) 'invocation WorkingDirectory does not exist'
    $expectedTimeout = if ($Role -ceq 'Compile') { 60000 } else { 10000 }
    Require (($request.TimeoutMilliseconds -is [int] -or $request.TimeoutMilliseconds -is [long]) -and $request.TimeoutMilliseconds -eq $expectedTimeout) 'invocation timeout differs from the selected role budget'
    Require (($request.MaxStdoutBytes -is [int] -or $request.MaxStdoutBytes -is [long]) -and $request.MaxStdoutBytes -eq 4096) 'invocation stdout cap must be the frozen 4096-byte test value'
    Require (($request.MaxStderrBytes -is [int] -or $request.MaxStderrBytes -is [long]) -and $request.MaxStderrBytes -eq 4096) 'invocation stderr cap must be the frozen 4096-byte test value'
    Require ($null -ne $request.Arguments -and $request.Arguments.GetType().IsArray) 'invocation Arguments must be a JSON string array'
    $argumentList = New-Object 'System.Collections.Generic.List[string]'
    foreach ($argument in $request.Arguments) {
        Require ($argument -is [string]) 'every invocation argument must be a string'
        $argumentList.Add($argument)
    }
    [string[]]$arguments = $argumentList.ToArray()
    $expectedExitCode = if ($Role -ceq 'Compile') { 0 } else { 1 }
    $expectedStderr = ''
    if ($Role -ceq 'HeldMain') {
        Require ($arguments.Count -eq 10) 'HeldMain invocation must contain exactly ten arguments'
        Require ($arguments[0] -ceq '-Xmx1024m' -and $arguments[1] -ceq '-cp') 'HeldMain heap/classpath options changed'
        $classPathParts = $arguments[2].Split([char]';')
        Require ($classPathParts.Count -eq 4) 'HeldMain classpath must contain four current CODE roots'
        for ($index = 0; $index -lt $classPathParts.Count; $index++) {
            Assert-CanonicalAbsolute $classPathParts[$index] ('HeldMain classpath root ' + $index)
        }
        Require ($arguments[3] -ceq 'com.lushprojects.circuitjs1.client.CohortTerminalTelemetryStudy') 'HeldMain class changed'
        Require ($arguments[4] -ceq 'COHORT_TERMINAL_TELEMETRY_V2') 'HeldMain telemetry marker changed'
        Require ($arguments[5] -cmatch '^[0-9a-f]{8}(-[0-9a-f]{4}){3}-[0-9a-f]{12}$') 'HeldMain UUID slot is malformed'
        foreach ($index in @(6, 7, 8)) { Require ($arguments[$index] -cmatch '^[0-9a-f]{64}$') ('HeldMain hash slot is malformed at ' + $index) }
        Assert-CanonicalAbsolute $arguments[9] 'HeldMain output directory'
        $expectedStderr = 'Exception in thread "main" java.lang.IllegalStateException: ADOPTED_SNAPSHOT_COMPONENT_ONLY' + "`r`n`t" +
            'at com.lushprojects.circuitjs1.client.CohortTerminalTelemetryStudy.main(CohortTerminalTelemetryStudy.java:29)' + "`r`n"
    }

    $ModulePath = [IO.Path]::GetFullPath($ModulePath)
    Assert-Tuple $ModulePath $PinnedModuleSha256 $PinnedModuleBytes 'N61 module'
    if ($Role -ceq 'Compile') {
        Require ([IO.Path]::GetFileName($request.FilePath) -ieq 'javac.exe') 'Compile role requires the selected javac.exe'
        $toolPath = $request.FilePath; $toolSha256 = $PinnedJavacSha256; $toolBytes = $PinnedJavacBytes
        Assert-Tuple $toolPath $toolSha256 $toolBytes 'selected javac.exe'
        $javacPath = $toolPath
    } else {
        Require ([IO.Path]::GetFileName($request.FilePath) -ieq 'java.exe') 'HeldMain role requires the selected java.exe'
        $toolPath = $request.FilePath; $toolSha256 = $PinnedJavaSha256; $toolBytes = $PinnedJavaBytes
        $javacPath = Join-Path ([IO.Path]::GetDirectoryName($toolPath)) 'javac.exe'
        Assert-Tuple $toolPath $toolSha256 $toolBytes 'selected java.exe'
        Assert-Tuple $javacPath $PinnedJavacSha256 $PinnedJavacBytes 'selected sibling javac.exe'
    }
    $hostProcess = [Diagnostics.Process]::GetCurrentProcess()
    try {
        $PowerShellPath = [IO.Path]::GetFullPath($hostProcess.MainModule.FileName)
        Assert-Tuple $PowerShellPath $PinnedPowerShellSha256 $PinnedPowerShellBytes 'selected Windows PowerShell host'
    } finally { $hostProcess.Dispose() }
    $processEnvironment = [Environment]::GetEnvironmentVariables('Process')
    foreach ($name in @('JAVA_TOOL_OPTIONS', '_JAVA_OPTIONS', 'JDK_JAVA_OPTIONS', 'CLASSPATH')) {
        Require (-not $processEnvironment.Contains($name)) ('ambient Java environment variable is forbidden: ' + $name)
    }

    $owner = Import-Module -Name $ModulePath -Force -DisableNameChecking -PassThru
    foreach ($path in @($ModulePath, $InvocationPath, $PowerShellPath, $toolPath, $javacPath, $request.WorkingDirectory)) {
        & $owner { param($Item) Assert-VerifierNoReparseAncestors $Item } $path
    }
    $jobType = & $owner { Initialize-VerifierBrowserContainmentJobApi }
    Require ($jobType -is [type] -and $jobType.Name -ceq 'VerifierBrowserContainmentJobApi') 'existing native creator-job API unavailable'
    $EvidenceRoot = [IO.Path]::GetFullPath($EvidenceRoot)
    & $owner { param($Item) Assert-VerifierNoReparseAncestors $Item } $EvidenceRoot
    $tempRoot = [IO.Path]::GetFullPath([IO.Path]::GetTempPath())
    Require ([bool](& $owner { param($Base, $Item) Test-VerifierPhysicalChildPath $Base $Item } $tempRoot $EvidenceRoot)) 'evidence root must be a physical OS-temp child'
    Require (-not [IO.Directory]::Exists($EvidenceRoot) -and -not [IO.File]::Exists($EvidenceRoot)) 'fresh evidence root required'
    $null = [IO.Directory]::CreateDirectory($EvidenceRoot)
    $rootCreated = $true
    & $owner { param($Item) Assert-VerifierNoReparseAncestors $Item } $EvidenceRoot
    Save-Json (Join-Path $EvidenceRoot 'inputs.json') ([ordered]@{
        requestPath = $InvocationPath; requestSha256 = $requestSha256; requestBytes = $requestBytes.Length
        modulePath = $ModulePath; moduleSha256 = $PinnedModuleSha256; moduleBytes = $PinnedModuleBytes
        role = $Role; toolPath = $toolPath; toolSha256 = $toolSha256; toolBytes = $toolBytes
        javacPath = $javacPath; javacSha256 = $PinnedJavacSha256; javacBytes = $PinnedJavacBytes
        powershellPath = $PowerShellPath; powershellSha256 = $PinnedPowerShellSha256; powershellBytes = $PinnedPowerShellBytes
        filePath = $request.FilePath; arguments = @($arguments); workingDirectory = $request.WorkingDirectory
        timeoutMilliseconds = $request.TimeoutMilliseconds; maxStdoutBytes = $request.MaxStdoutBytes; maxStderrBytes = $request.MaxStderrBytes
        expectedExitCode = $expectedExitCode; expectedStdout = ''; expectedStderr = $expectedStderr
        classification = if ($Role -ceq 'HeldMain') { 'EXPECTED_REFUSAL_NOT_ADMISSION' } else { 'CURRENT_CODE_COMPILE_ONLY' }
    })

    $jobLabel = if ($Role -ceq 'Compile') { 'CurrentCodeCompile' } else { 'CurrentCodeHeldMain' }
    $jobName = 'Local\TroubleshootJS.Verifier.' + $jobLabel + '.' + [guid]::NewGuid().ToString('N')
    $job = $jobType::CreateNew($jobName)
    Require (@($job.GetMemberProcessIds()).Count -eq 0) 'new native compile creator job was not empty'
    Assert-Tuple $InvocationPath $ExpectedInvocationSha256 $ExpectedInvocationBytes 'invocation frame'
    Assert-Tuple $ModulePath $PinnedModuleSha256 $PinnedModuleBytes 'N61 module'
    Assert-Tuple $toolPath $toolSha256 $toolBytes 'selected role executable'
    Assert-Tuple $javacPath $PinnedJavacSha256 $PinnedJavacBytes 'selected sibling javac.exe'
    Assert-Tuple $PowerShellPath $PinnedPowerShellSha256 $PinnedPowerShellBytes 'selected Windows PowerShell host'
    $operationWatch.Start()
    try {
        $result = Invoke-VerifierBoundedProcess -FilePath $request.FilePath -Arguments $arguments `
            -TimeoutMilliseconds $request.TimeoutMilliseconds -MaxStdoutBytes $request.MaxStdoutBytes `
            -MaxStderrBytes $request.MaxStderrBytes -CancellationToken ([Threading.CancellationToken]::None) `
            -CreatorJob $job -WorkingDirectory $request.WorkingDirectory
    } catch {
        $nativeFailure = $_
    }
    $operationWatch.Stop()
    if ($null -ne $result) {
        Save-Json (Join-Path $EvidenceRoot 'raw-result.json') $result
    }
    if ($null -ne $nativeFailure) { throw $nativeFailure }
    Require ($null -ne $result) 'native invocation returned no bounded result'
    Require ($result.ExitCode -is [int] -and $result.ExitCode -eq $expectedExitCode) 'native invocation exit differed from the selected role expectation'
    Require ($result.Stdout -ceq '' -and $result.Stderr -ceq $expectedStderr) 'native invocation output differed from the selected role expectation'
    Require ($result.TerminationProven -eq $true) 'native result did not prove original root termination'
    Require ($result.FilePath -ceq $request.FilePath) 'native executable path changed'
    Require (@($result.Arguments).Count -eq $arguments.Count) 'native compile argv count changed'
    for ($index = 0; $index -lt $arguments.Count; $index++) {
        Require ($result.Arguments[$index] -is [string] -and $result.Arguments[$index] -ceq $arguments[$index]) ('native compile argv changed at position ' + $index)
    }
    $native = $result.CaptureFacts.NativeContainment
    Require ($native.JobName -ceq $jobName) 'native job identity changed'
    Require ($native.LeaseReturned -eq $true -and $native.RootExitObserved -eq $true) 'native invocation did not retain and observe its original process lease'
    Require ($native.JobEmpty -eq $true -and @($job.GetMemberProcessIds()).Count -eq 0) 'native invocation did not leave its current job empty'
    Require ($native.ReadHandlesClosed -eq $true -and $native.LeaseClosed -eq $true -and $native.CleanupProven -eq $true) 'native reader/lease cleanup was not proven'
    Require ($native.TerminationRequested -eq $false) 'natural native result unexpectedly requested job termination'
    Require ($native.OperationPhase -ceq 'COMPLETE' -and $native.CleanupPhase -ceq 'COMPLETE') 'native operation or cleanup phase did not complete'
    Require ($native.NativeExitCode -is [uint32] -and [long]$native.NativeExitCode -eq $expectedExitCode -and $native.ProcessExitCode -is [int] -and $native.ProcessExitCode -eq $expectedExitCode) 'native raw/signed exits differed from the selected role expectation'
    Require ($result.ProcessId -is [int] -and $result.ProcessId -gt 0 -and $result.ProcessStartTicks -is [long] -and $result.ProcessStartTicks -gt 0) 'native root identity was invalid'
    Require ([long]$native.ProcessCreationFileTime -eq ([long]$result.ProcessStartTicks - 504911232000000000L)) 'native FILETIME differed from public start ticks'
    Require ([String]::Equals([IO.Path]::GetFullPath($native.ImagePath), [IO.Path]::GetFullPath($toolPath), [StringComparison]::OrdinalIgnoreCase)) 'native image differed from the pinned role executable'
    Require ($result.CaptureFacts.ReaderTasksTerminal -eq $true -and $result.CaptureFacts.ReadersClosed -eq $true -and $result.CaptureFacts.CleanupProven -eq $true) 'native byte readers did not settle'
    $expectedStderrBytes = [Text.Encoding]::ASCII.GetByteCount($expectedStderr)
    Require ($result.CaptureFacts.CaptureFailureKind -ceq '' -and $result.CaptureFacts.StdoutByteCount -eq 0 -and $result.CaptureFacts.StderrByteCount -eq $expectedStderrBytes) 'native capture bytes differed from the selected role expectation'
    Require ($result.CaptureFacts.StdoutObservedBytes -eq 0 -and $result.CaptureFacts.StderrObservedBytes -eq $expectedStderrBytes -and $result.CaptureFacts.StdoutDiscardedDuringCleanup -eq 0 -and $result.CaptureFacts.StderrDiscardedDuringCleanup -eq 0) 'native capture discarded or miscounted role output'
    $processRoot = [IO.Path]::GetFullPath($result.ProcessRoot)
    Require (-not [IO.Directory]::Exists($processRoot) -and -not [IO.File]::Exists($processRoot)) 'successful native compile process root remained'
    foreach ($path in @($result.StdoutLog, $result.StderrLog)) { Require (-not [IO.File]::Exists($path)) 'successful native compile log remained' }

    $cleanupWatch = [Diagnostics.Stopwatch]::StartNew()
    $jobCleanupAttempted = $true
    $jobCleanup = Close-CreatorJob $job
    $job = if ($jobCleanup.disposed) { $null } else { $job }
    $cleanupWatch.Stop()
    Require ($jobCleanup.emptyMembershipObserved -and $jobCleanup.disposed -and $null -eq $jobCleanup.failure) 'caller creator job cleanup was not proven'
    $contractClassification = if ($Role -ceq 'Compile') { 'PASS' } else { 'PASS_EXPECTED_REFUSAL' }
    $resultObject = [ordered]@{
        status = 'PASS'; classification = $contractClassification; role = $Role
        expectedExitCode = $expectedExitCode; expectedStderr = $expectedStderr
        requestSha256 = $requestSha256; requestBytes = $requestBytes.Length
        rawResult = $result; callerJobCleanup = $jobCleanup
        operationMilliseconds = $operationWatch.Elapsed.TotalMilliseconds
        cleanupMilliseconds = $cleanupWatch.Elapsed.TotalMilliseconds
    }
    Save-Json (Join-Path $EvidenceRoot 'result.json') $resultObject
    if ($Role -ceq 'Compile') { [Console]::Out.WriteLine('Verifier current CODE compile contract status=PASS') }
    else { [Console]::Out.WriteLine('Verifier current CODE held main contract status=PASS') }
    exit 0
} catch {
    $primaryFailure = $_
    if ($operationWatch.IsRunning) { $operationWatch.Stop() }
    if ($rootCreated) {
        if ($null -ne $job -and -not $jobCleanupAttempted) {
            $cleanupWatch = [Diagnostics.Stopwatch]::StartNew()
            $jobCleanupAttempted = $true
            try { $jobCleanup = Close-CreatorJob $job; if ($jobCleanup.disposed) { $job = $null } }
            catch { $cleanupFailure = $_ }
            $cleanupWatch.Stop()
        } elseif (-not $jobCleanupAttempted) { $cleanupWatch = $null }
        try {
            $nativeData = $null
            $captureData = $null
            if ($null -ne $nativeFailure) {
                if ($nativeFailure.Exception.Data.Contains('VerifierNativeContainment')) { $nativeData = $nativeFailure.Exception.Data['VerifierNativeContainment'] }
                if ($nativeFailure.Exception.Data.Contains('VerifierCapture')) { $captureData = $nativeFailure.Exception.Data['VerifierCapture'] }
            }
            Save-Json (Join-Path $EvidenceRoot 'failure.json') ([ordered]@{
                status = 'FAIL_NOT_PASS'; exceptionType = $primaryFailure.Exception.GetType().FullName
                message = $primaryFailure.Exception.Message; scriptStackTrace = $primaryFailure.ScriptStackTrace
                rawResult = $result; nativeException = if ($null -ne $nativeFailure) { $nativeFailure.Exception.Message } else { $null }
                nativeContainment = $nativeData; captureFacts = $captureData
                callerJobCleanup = $jobCleanup
                retainedUnprovenJob = ($null -ne $script:retainedUnprovenJob)
                operationMilliseconds = $operationWatch.Elapsed.TotalMilliseconds
                cleanupMilliseconds = if ($null -ne $cleanupWatch) { $cleanupWatch.Elapsed.TotalMilliseconds } else { 0.0 }
            })
        } catch { }
    }
    [Console]::Error.WriteLine('Verifier current CODE compile contract FAIL: ' + $primaryFailure.Exception.GetType().FullName)
    exit 2
}
