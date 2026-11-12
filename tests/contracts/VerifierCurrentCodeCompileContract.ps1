[CmdletBinding()]
param(
    [Parameter(Mandatory=$true)][string]$ModulePath,
    [Parameter(Mandatory=$true)][string]$InvocationPath,
    [Parameter(Mandatory=$true)][string]$ExpectedInvocationSha256,
    [Parameter(Mandatory=$true)][long]$ExpectedInvocationBytes,
    [Parameter(Mandatory=$true)][string]$EvidenceRoot
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
$MaximumInvocationBytes = 65536L
function Require([bool]$Condition, [string]$Message) { if (-not $Condition) { throw [InvalidOperationException]::new($Message) } }
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
    Require (($request.TimeoutMilliseconds -is [int] -or $request.TimeoutMilliseconds -is [long]) -and $request.TimeoutMilliseconds -eq 60000) 'invocation timeout must be the frozen 60000ms value'
    Require (($request.MaxStdoutBytes -is [int] -or $request.MaxStdoutBytes -is [long]) -and $request.MaxStdoutBytes -eq 4096) 'invocation stdout cap must be the frozen 4096-byte test value'
    Require (($request.MaxStderrBytes -is [int] -or $request.MaxStderrBytes -is [long]) -and $request.MaxStderrBytes -eq 4096) 'invocation stderr cap must be the frozen 4096-byte test value'
    Require ($null -ne $request.Arguments -and $request.Arguments.GetType().IsArray) 'invocation Arguments must be a JSON string array'
    $argumentList = New-Object 'System.Collections.Generic.List[string]'
    foreach ($argument in $request.Arguments) {
        Require ($argument -is [string]) 'every invocation argument must be a string'
        $argumentList.Add($argument)
    }
    [string[]]$arguments = $argumentList.ToArray()

    $ModulePath = [IO.Path]::GetFullPath($ModulePath)
    Assert-Tuple $ModulePath $PinnedModuleSha256 $PinnedModuleBytes 'N61 module'
    Require ([IO.Path]::GetFileName($request.FilePath) -ieq 'javac.exe') 'invocation executable must be the selected javac.exe'
    Assert-Tuple $request.FilePath $PinnedJavacSha256 $PinnedJavacBytes 'selected javac.exe'
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
    foreach ($path in @($ModulePath, $InvocationPath, $PowerShellPath, $request.FilePath, $request.WorkingDirectory)) {
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
        javacPath = $request.FilePath; javacSha256 = $PinnedJavacSha256; javacBytes = $PinnedJavacBytes
        powershellPath = $PowerShellPath; powershellSha256 = $PinnedPowerShellSha256; powershellBytes = $PinnedPowerShellBytes
        filePath = $request.FilePath; arguments = @($arguments); workingDirectory = $request.WorkingDirectory
        timeoutMilliseconds = $request.TimeoutMilliseconds; maxStdoutBytes = $request.MaxStdoutBytes; maxStderrBytes = $request.MaxStderrBytes
        scope = 'CURRENT_CODE_COMPILE_NATIVE_BRIDGE_ONLY'
    })

    $jobName = 'Local\TroubleshootJS.Verifier.CurrentCodeCompile.' + [guid]::NewGuid().ToString('N')
    $job = $jobType::CreateNew($jobName)
    Require (@($job.GetMemberProcessIds()).Count -eq 0) 'new native compile creator job was not empty'
    Assert-Tuple $InvocationPath $ExpectedInvocationSha256 $ExpectedInvocationBytes 'invocation frame'
    Assert-Tuple $ModulePath $PinnedModuleSha256 $PinnedModuleBytes 'N61 module'
    Assert-Tuple $request.FilePath $PinnedJavacSha256 $PinnedJavacBytes 'selected javac.exe'
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
    Require ($null -ne $result) 'native compile invocation returned no bounded result'
    Require ($result.ExitCode -is [int] -and $result.ExitCode -eq 0) 'current CODE compile did not return zero'
    Require ($result.Stdout -ceq '' -and $result.Stderr -ceq '') 'current CODE compile emitted diagnostics'
    Require ($result.TerminationProven -eq $true) 'native compile result did not prove original root termination'
    Require ($result.FilePath -ceq $request.FilePath) 'native compile executable path changed'
    Require (@($result.Arguments).Count -eq $arguments.Count) 'native compile argv count changed'
    for ($index = 0; $index -lt $arguments.Count; $index++) {
        Require ($result.Arguments[$index] -is [string] -and $result.Arguments[$index] -ceq $arguments[$index]) ('native compile argv changed at position ' + $index)
    }
    $native = $result.CaptureFacts.NativeContainment
    Require ($native.JobName -ceq $jobName) 'native compile job identity changed'
    Require ($native.LeaseReturned -eq $true -and $native.RootExitObserved -eq $true) 'native compile did not retain and observe its original process lease'
    Require ($native.JobEmpty -eq $true -and @($job.GetMemberProcessIds()).Count -eq 0) 'native compile did not leave its current job empty'
    Require ($native.ReadHandlesClosed -eq $true -and $native.LeaseClosed -eq $true -and $native.CleanupProven -eq $true) 'native compile reader/lease cleanup was not proven'
    Require ($native.TerminationRequested -eq $false) 'natural compile result unexpectedly requested job termination'
    Require ($native.OperationPhase -ceq 'COMPLETE' -and $native.CleanupPhase -ceq 'COMPLETE') 'native compile operation or cleanup phase did not complete'
    Require ($native.NativeExitCode -is [uint32] -and [long]$native.NativeExitCode -eq 0 -and $native.ProcessExitCode -is [int] -and $native.ProcessExitCode -eq 0) 'native compile raw/signed exits differed from zero'
    Require ($result.ProcessId -is [int] -and $result.ProcessId -gt 0 -and $result.ProcessStartTicks -is [long] -and $result.ProcessStartTicks -gt 0) 'native compile root identity was invalid'
    Require ([long]$native.ProcessCreationFileTime -eq ([long]$result.ProcessStartTicks - 504911232000000000L)) 'native compile FILETIME differed from public start ticks'
    Require ([String]::Equals([IO.Path]::GetFullPath($native.ImagePath), [IO.Path]::GetFullPath($request.FilePath), [StringComparison]::OrdinalIgnoreCase)) 'native compile image differed from pinned javac'
    Require ($result.CaptureFacts.ReaderTasksTerminal -eq $true -and $result.CaptureFacts.ReadersClosed -eq $true -and $result.CaptureFacts.CleanupProven -eq $true) 'native compile byte readers did not settle'
    Require ($result.CaptureFacts.CaptureFailureKind -ceq '' -and $result.CaptureFacts.StdoutByteCount -eq 0 -and $result.CaptureFacts.StderrByteCount -eq 0) 'native compile capture was not empty and clean'
    Require ($result.CaptureFacts.StdoutObservedBytes -eq 0 -and $result.CaptureFacts.StderrObservedBytes -eq 0 -and $result.CaptureFacts.StdoutDiscardedDuringCleanup -eq 0 -and $result.CaptureFacts.StderrDiscardedDuringCleanup -eq 0) 'native compile observed or discarded diagnostics'
    $processRoot = [IO.Path]::GetFullPath($result.ProcessRoot)
    Require (-not [IO.Directory]::Exists($processRoot) -and -not [IO.File]::Exists($processRoot)) 'successful native compile process root remained'
    foreach ($path in @($result.StdoutLog, $result.StderrLog)) { Require (-not [IO.File]::Exists($path)) 'successful native compile log remained' }

    $cleanupWatch = [Diagnostics.Stopwatch]::StartNew()
    $jobCleanupAttempted = $true
    $jobCleanup = Close-CreatorJob $job
    $job = if ($jobCleanup.disposed) { $null } else { $job }
    $cleanupWatch.Stop()
    Require ($jobCleanup.emptyMembershipObserved -and $jobCleanup.disposed -and $null -eq $jobCleanup.failure) 'caller creator job cleanup was not proven'
    $resultObject = [ordered]@{
        status = 'PASS'; requestSha256 = $requestSha256; requestBytes = $requestBytes.Length
        rawResult = $result; callerJobCleanup = $jobCleanup
        operationMilliseconds = $operationWatch.Elapsed.TotalMilliseconds
        cleanupMilliseconds = $cleanupWatch.Elapsed.TotalMilliseconds
    }
    Save-Json (Join-Path $EvidenceRoot 'result.json') $resultObject
    [Console]::Out.WriteLine('Verifier current CODE compile contract status=PASS')
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
