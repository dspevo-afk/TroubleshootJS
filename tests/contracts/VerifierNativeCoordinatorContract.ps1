[CmdletBinding()]
param(
    [Parameter(Mandatory=$true)][string]$ModulePath,
    [Parameter(Mandatory=$true)][string]$ExpectedModuleSha256,
    [Parameter(Mandatory=$true)][long]$ExpectedModuleBytes,
    [Parameter(Mandatory=$true)][string]$FixtureSourcePath,
    [Parameter(Mandatory=$true)][string]$ExpectedFixtureSourceSha256,
    [Parameter(Mandatory=$true)][string]$EvidenceRoot,
    [ValidateSet('Smoke','Suite')][string]$Mode = 'Smoke',
    [string]$JavaExecutable = '',
    [string]$ExpectedJavaSha256 = 'e71f3022c972743d2a37e2597b45282b9c380211cd491c54a0c3d63c11bcfbce'
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
$owner = $null
$jobType = $null
$ChildExecutable = $null
$ChildImageSha256 = $null
$ChildImageBytes = $null
$rootReady = $false
$cases = New-Object 'System.Collections.Generic.List[object]'
$script:activeCase = $null

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
    $body = [Text.UTF8Encoding]::new($false).GetBytes(($Value | ConvertTo-Json -Depth 24) + "`n")
    $stream = [IO.File]::Open((Join-Path $EvidenceRoot $Name), [IO.FileMode]::CreateNew, [IO.FileAccess]::Write, [IO.FileShare]::Read)
    try { $stream.Write($body, 0, $body.Length) }
    finally { $stream.Dispose() }
}

function Save-Case([string]$Directory, $Value) {
    $body = [Text.UTF8Encoding]::new($false).GetBytes(($Value | ConvertTo-Json -Depth 24) + "`n")
    $stream = [IO.File]::Open((Join-Path $Directory 'case.json'), [IO.FileMode]::CreateNew, [IO.FileAccess]::Write, [IO.FileShare]::Read)
    try { $stream.Write($body, 0, $body.Length) }
    finally { $stream.Dispose() }
}

function Equal-Strings($Actual, $Expected, [string]$Label) {
    Require (@($Actual).Count -eq @($Expected).Count) ($Label + ' count')
    for ($index = 0; $index -lt @($Expected).Count; $index++) {
        Require ($Actual[$index] -is [string] -and $Actual[$index] -ceq $Expected[$index]) ($Label + ' value')
    }
}

function Assert-StringRun([string]$Actual, [char]$Value, [int]$Count, [string]$Label) {
    Require ($Actual.Length -eq $Count) ($Label + ' length')
    for ($index = 0; $index -lt $Actual.Length; $index++) {
        if ($Actual[$index] -ne $Value) { throw ($Label + ' mismatch at offset ' + $index) }
    }
}

function Get-BaseException($Record) {
    Require ($null -ne $Record -and $null -ne $Record.Exception) 'exception record was missing'
    return $Record.Exception.GetBaseException()
}

function Read-ActorWitness([string]$StatePath, [string]$Nonce, [string]$JobName,
        [string[]]$ExpectedArgv, [bool]$ExpectedInJob,
        [string]$ExpectedMode = 'emit') {
    Require ([IO.File]::Exists($StatePath)) 'fixture actor state witness missing'
    $body = [IO.File]::ReadAllText($StatePath)
    $witness = $body | ConvertFrom-Json -ErrorAction Stop
    Require ($witness.mode -ceq $ExpectedMode) 'fixture actor mode mismatch'
    Require ($witness.nonce -ceq $Nonce) 'fixture actor nonce mismatch'
    Require ([bool]$witness.inExpectedJob -eq $ExpectedInJob) 'fixture actor job membership witness mismatch'
    Require ([long]$witness.pid -gt 0 -and [long]$witness.creationFileTime -gt 0) 'fixture actor identity invalid'
    Require ([String]::Equals([IO.Path]::GetFullPath($witness.imagePath), $ChildExecutable, [StringComparison]::OrdinalIgnoreCase)) 'fixture actor image path mismatch'
    Require ($witness.imageSha256 -ceq $ChildImageSha256) 'fixture actor image SHA mismatch'
    Equal-Strings @($witness.argv) $ExpectedArgv 'fixture actor argv'
    $parsed = @(& $owner { param($Raw) ConvertFrom-VerifierWindowsCommandLine $Raw } $witness.commandLine)
    Equal-Strings $parsed $ExpectedArgv 'fixture actor raw command line'
    return [pscustomobject]@{ body = $body; witness = $witness; expectedJobName = $JobName }
}

function Assert-NativeFields($Native, [string]$JobName) {
    Require ($null -ne $Native) 'native containment facts were missing'
    $required = @(
        'JobName', 'LeaseReturned', 'ProcessCreationFileTime', 'RootExitObserved',
        'JobEmpty', 'ReadHandlesClosed', 'LeaseClosed', 'TerminationRequested',
        'CleanupProven', 'DescendantHandleWaits', 'Scope', 'ImagePath', 'ProcessRoot',
        'OperationPhase', 'CleanupPhase', 'OperationMilliseconds', 'CleanupMilliseconds',
        'NativeExitCode', 'ProcessExitCode'
    )
    foreach ($name in $required) {
        Require ($null -ne $Native.PSObject.Properties[$name]) ('native containment fact was missing: ' + $name)
    }
    Require ($Native.JobName -ceq $JobName) 'native containment job name mismatch'
    Require ($Native.DescendantHandleWaits -ceq 'NOT_ATTESTED') 'native descendant handle scope changed'
    Require ($Native.Scope -ceq 'SERIAL_DEDICATED_HOST') 'native containment scope changed'
    Require (-not [String]::IsNullOrWhiteSpace([string]$Native.OperationPhase)) 'native operation phase was missing'
    Require (-not [String]::IsNullOrWhiteSpace([string]$Native.CleanupPhase)) 'native cleanup phase was missing'
    Require ($Native.OperationMilliseconds -is [double] -and $Native.CleanupMilliseconds -is [double] -and $Native.OperationMilliseconds -ge 0 -and $Native.CleanupMilliseconds -ge 0) 'native phase durations were invalid'
}

function Assert-ProcessRoot([string]$ProcessRoot, [bool]$MustExist) {
    Require (-not [String]::IsNullOrWhiteSpace($ProcessRoot) -and [IO.Path]::IsPathRooted($ProcessRoot)) 'native process root was not an absolute path'
    $fullRoot = [IO.Path]::GetFullPath($ProcessRoot)
    $tempRoot = [IO.Path]::GetFullPath([IO.Path]::GetTempPath())
    $expectedParent = [IO.Path]::GetFullPath((Join-Path $tempRoot 'TroubleshootJS'))
    Require ([String]::Equals([IO.Path]::GetDirectoryName($fullRoot), $expectedParent, [StringComparison]::OrdinalIgnoreCase)) 'native process root was outside its task-owned temp namespace'
    Require ([IO.Path]::GetFileName($fullRoot) -cmatch '^verify-process-[0-9a-f]{32}$') 'native process root name changed'
    if ($MustExist) {
        Require ([IO.Directory]::Exists($fullRoot)) 'native failure evidence directory was not retained'
        & $owner { param($Path) Assert-VerifierNoReparseAncestors $Path } $fullRoot
        Require ([bool](& $owner { param($Base, $Path) Test-VerifierPhysicalChildPath $Base $Path } $expectedParent $fullRoot)) 'native failure evidence escaped its temp parent'
    } else {
        Require (-not [IO.Directory]::Exists($fullRoot) -and -not [IO.File]::Exists($fullRoot)) 'successful native process root was not removed before return'
    }
    return $fullRoot
}

function Close-TestCreatorJob($Job) {
    $watch = [Diagnostics.Stopwatch]::StartNew()
    $initialIds = @($Job.GetMemberProcessIds() | ForEach-Object { [long]$_ })
    $terminationRequested = $false
    if ($initialIds.Count -gt 0) {
        $Job.TerminateOwned([uint32]2)
        $terminationRequested = $true
    }
    $memberIds = @($initialIds)
    while ($memberIds.Count -gt 0 -and $watch.ElapsedMilliseconds -lt 5000) {
        Start-Sleep -Milliseconds 20
        $memberIds = @($Job.GetMemberProcessIds() | ForEach-Object { [long]$_ })
    }
    Require ($memberIds.Count -eq 0) 'test-owned creator job did not become empty before disposal'
    $Job.Dispose()
    $watch.Stop()
    return [pscustomobject]@{
        InitialMemberIds = @($initialIds)
        FinalMemberIds = @($memberIds)
        CleanupTerminationRequested = $terminationRequested
        CreatorJobDisposed = $true
        CleanupMilliseconds = $watch.Elapsed.TotalMilliseconds
    }
}

function New-TestCreatorJob([string]$JobName) {
    $job = $jobType::CreateNew($JobName)
    $memberIds = @($job.GetMemberProcessIds())
    Require ($memberIds.Count -eq 0) 'native coordinator contract requires a fresh empty creator job'
    return $job
}

function Run-DuplexSuccessSmoke {
    $caseName = 'native-duplex-success'
    $caseDirectory = Join-Path $EvidenceRoot $caseName
    $null = [IO.Directory]::CreateDirectory($caseDirectory)
    & $owner { param($Path) Assert-VerifierNoReparseAncestors $Path } $caseDirectory
    $stateDirectory = Join-Path $caseDirectory 'states'
    $null = [IO.Directory]::CreateDirectory($stateDirectory)
    & $owner { param($Path) Assert-VerifierNoReparseAncestors $Path } $stateDirectory
    $jobName = 'Local\TroubleshootJS.Verifier.NativeCoordinatorContract.' + [guid]::NewGuid().ToString('N')
    $nonce = [guid]::NewGuid().ToString('N')
    $stdoutCount = 65536
    $stderrCount = 65536
    $timeoutMilliseconds = 10000
    $stdoutLimit = 65536
    $stderrLimit = 65536
    [string[]]$arguments = @('emit', $stateDirectory, $nonce, $jobName, [string]$stdoutCount, [string]$stderrCount, '0')
    [string[]]$expectedArgv = @($ChildExecutable) + $arguments
    $job = $null
    $result = $null
    $primaryFailure = $null
    $cleanupFailure = $null
    $native = $null
    $witnessFacts = $null
    $resultFacts = $null
    $operationWatch = [Diagnostics.Stopwatch]::StartNew()

    try {
        $job = New-TestCreatorJob $jobName
        $token = [Threading.CancellationToken]::None
        $result = Invoke-VerifierBoundedProcess -FilePath $ChildExecutable -Arguments $arguments `
            -TimeoutMilliseconds $timeoutMilliseconds -MaxStdoutBytes $stdoutLimit `
            -MaxStderrBytes $stderrLimit -CancellationToken $token `
            -CreatorJob $job -WorkingDirectory $caseDirectory
        $operationWatch.Stop()

        Require ($result.FilePath -ceq $ChildExecutable) 'successful result FilePath changed'
        Equal-Strings @($result.Arguments) $arguments 'successful result Arguments'
        Require ($result.ProcessId -is [int] -and $result.ProcessId -gt 0) 'successful result PID was invalid'
        Require ($result.ProcessStartTicks -is [long] -and $result.ProcessStartTicks -gt 0) 'successful result start ticks were invalid'
        Require ($result.ExitCode -is [int] -and $result.ExitCode -eq 0) 'duplex fixture exit code was not zero'
        Require ($result.TerminationProven -eq $true) 'successful result did not prove the original process terminal'
        Assert-StringRun $result.Stdout ([char]'O') $stdoutCount 'native stdout'
        Assert-StringRun $result.Stderr ([char]'E') $stderrCount 'native stderr'

        $native = $result.CaptureFacts.NativeContainment
        Assert-NativeFields $native $jobName
        Require ($native.LeaseReturned -eq $true) 'native success did not report lease return'
        Require ($native.RootExitObserved -eq $true) 'native success did not observe original root exit'
        Require ($native.JobEmpty -eq $true) 'native success returned while creator job was nonempty'
        Require ($native.ReadHandlesClosed -eq $true) 'native success did not close both captured read handles'
        Require ($native.LeaseClosed -eq $true) 'native success did not close the returned lease'
        Require ($native.TerminationRequested -eq $false) 'natural duplex success unexpectedly requested job termination'
        Require ($native.CleanupProven -eq $true) 'native success did not prove its process/job/reader cleanup'
        Require ([long]$native.ProcessCreationFileTime -gt 0) 'native success omitted its process creation FILETIME'
        Require ([long]$native.ProcessCreationFileTime -eq ([long]$result.ProcessStartTicks - 504911232000000000L)) 'native creation FILETIME differed from public start ticks'
        Require ([long]$native.NativeExitCode -eq 0 -and [int]$native.ProcessExitCode -eq 0) 'native success exit snapshots differed from the public exit code'
        Require ([String]::Equals([IO.Path]::GetFullPath($native.ImagePath), $ChildExecutable, [StringComparison]::OrdinalIgnoreCase)) 'native success image path mismatch'

        $captureFacts = $result.CaptureFacts
        Require ($captureFacts.ReaderTasksTerminal -eq $true -and $captureFacts.ReadersClosed -eq $true) 'successful byte capture did not settle both readers'
        Require ($captureFacts.CleanupProven -eq $true) 'successful byte capture did not prove cleanup'
        Require ($captureFacts.CaptureFailureKind -ceq '') 'successful byte capture retained a failure kind'
        Require ($captureFacts.StdoutByteCount -eq $stdoutCount -and $captureFacts.StderrByteCount -eq $stderrCount) 'successful retained byte counts changed'
        Require ($captureFacts.StdoutObservedBytes -eq $stdoutCount -and $captureFacts.StderrObservedBytes -eq $stderrCount) 'successful observed byte counts changed'
        Require ($captureFacts.StdoutDiscardedDuringCleanup -eq 0 -and $captureFacts.StderrDiscardedDuringCleanup -eq 0) 'successful duplex capture discarded bytes during cleanup'
        Require ($captureFacts.ProcessId -eq $result.ProcessId -and $captureFacts.StartTicks -eq $result.ProcessStartTicks) 'capture facts identity differed from the public process result'

        $witnessFacts = Read-ActorWitness (Join-Path $stateDirectory 'emit.state.json') $nonce $jobName $expectedArgv $true
        $witness = $witnessFacts.witness
        Require ([long]$witness.pid -eq [long]$result.ProcessId) 'independent actor PID differed from public result'
        Require ([long]$witness.creationFileTime -eq [long]$native.ProcessCreationFileTime) 'independent actor FILETIME differed from native handle facts'
        Require ($result.ProcessStartTicks -eq ([long]$witness.creationFileTime + 504911232000000000L)) 'independent actor FILETIME differed from public start ticks'
        Require ([String]::Equals([IO.Path]::GetFullPath($witness.imagePath), $ChildExecutable, [StringComparison]::OrdinalIgnoreCase)) 'independent actor image path mismatch'
        Require ($witness.imageSha256 -ceq $ChildImageSha256) 'independent actor image SHA mismatch'

        $processRoot = Assert-ProcessRoot $result.ProcessRoot $false
        Require ([String]::Equals([IO.Path]::GetFullPath($native.ProcessRoot), $processRoot, [StringComparison]::OrdinalIgnoreCase)) 'native and public process roots differed'
        foreach ($path in @($result.StdoutLog, $result.StderrLog)) {
            Require ($path -is [string] -and [IO.Path]::IsPathRooted($path)) 'successful result log path was not absolute'
            Require ([String]::Equals([IO.Path]::GetDirectoryName([IO.Path]::GetFullPath($path)), $processRoot, [StringComparison]::OrdinalIgnoreCase)) 'successful log path was outside its process root'
            Require (-not [IO.File]::Exists($path)) 'successful native log file remained after return'
        }
        Require (@($job.GetMemberProcessIds()).Count -eq 0) 'native coordinator did not leave the caller-owned job open and empty'

        $resultFacts = [ordered]@{
            filePath = $result.FilePath
            arguments = @($result.Arguments)
            processId = $result.ProcessId
            processStartTicks = $result.ProcessStartTicks
            processCreationFileTime = $native.ProcessCreationFileTime
            imageSha256 = $witness.imageSha256
            exitCode = $result.ExitCode
            stdoutCharacters = $result.Stdout.Length
            stderrCharacters = $result.Stderr.Length
            logPathsAbsent = $true
            processRootRemoved = $true
            callerJobEmptyAfterReturn = $true
            captureFacts = $captureFacts
            nativeContainment = $native
            actorWitness = $witnessFacts
        }
    } catch {
        $primaryFailure = $_
        if ($operationWatch.IsRunning) { $operationWatch.Stop() }
    }

    $cleanupWatch = [Diagnostics.Stopwatch]::StartNew()
    $jobCleanup = $null
    if ($null -ne $job) {
        try { $jobCleanup = Close-TestCreatorJob $job }
        catch { $cleanupFailure = $_ }
    }
    $cleanupWatch.Stop()
    $active = [ordered]@{
        name = $caseName
        jobName = $jobName
        nonce = $nonce
        operationMilliseconds = $operationWatch.Elapsed.TotalMilliseconds
        cleanupMilliseconds = $cleanupWatch.Elapsed.TotalMilliseconds
        resultFacts = $resultFacts
        nativeContainment = $native
        actorWitness = $witnessFacts
        testOwnedJobCleanup = $jobCleanup
        primaryFailure = if ($null -ne $primaryFailure) { $primaryFailure.Exception.Message } else { $null }
        cleanupFailure = if ($null -ne $cleanupFailure) { $cleanupFailure.Exception.Message } else { $null }
    }
    $script:activeCase = $active
    if ($null -ne $primaryFailure -or $null -ne $cleanupFailure) {
        $message = 'native coordinator duplex smoke failed'
        if ($null -ne $primaryFailure) { $message += ': ' + $primaryFailure.Exception.Message }
        if ($null -ne $cleanupFailure) { $message += '; caller-job cleanup failed: ' + $cleanupFailure.Exception.Message }
        throw [InvalidOperationException]::new($message)
    }

    $row = [ordered]@{
        name = $caseName
        status = 'PASS'
        operationStatus = 'NATURAL_EXIT_SUCCESS'
        operationMilliseconds = $operationWatch.Elapsed.TotalMilliseconds
        cleanupMilliseconds = $cleanupWatch.Elapsed.TotalMilliseconds
        facts = $active
    }
    $cases.Add($row)
    Save-Case $caseDirectory $row
}

function Run-MissingExecutableSmoke {
    $caseName = 'native-missing-executable'
    $caseDirectory = Join-Path $EvidenceRoot $caseName
    $null = [IO.Directory]::CreateDirectory($caseDirectory)
    & $owner { param($Path) Assert-VerifierNoReparseAncestors $Path } $caseDirectory
    $jobName = 'Local\TroubleshootJS.Verifier.NativeCoordinatorContract.' + [guid]::NewGuid().ToString('N')
    $missingPath = [IO.Path]::GetFullPath((Join-Path $caseDirectory 'missing-native-image.exe'))
    Require (-not [IO.File]::Exists($missingPath) -and -not [IO.Directory]::Exists($missingPath)) 'negative executable existed before native invocation'
    [string[]]$arguments = @('not-launched')
    $job = $null
    $failure = $null
    $native = $null
    $captureFacts = $null
    $cleanupFailure = $null
    $primaryFailure = $null
    $nativeCause = $null
    $logFacts = $null
    $operationWatch = [Diagnostics.Stopwatch]::StartNew()

    try {
        $job = New-TestCreatorJob $jobName
        $token = [Threading.CancellationToken]::None
        try {
            $null = Invoke-VerifierBoundedProcess -FilePath $missingPath -Arguments $arguments `
                -TimeoutMilliseconds 10000 -MaxStdoutBytes 65536 -MaxStderrBytes 65536 `
                -CancellationToken $token -CreatorJob $job -WorkingDirectory $caseDirectory
        } catch { $failure = $_ }
        Require ($null -ne $failure) 'missing executable native invocation unexpectedly returned success'
        Require ($failure.Exception.Data.Contains('VerifierNativeContainment')) 'missing executable failure omitted native containment facts'
        Require ($failure.Exception.Data.Contains('VerifierCapture')) 'missing executable failure omitted the capture-facts key'
        $native = $failure.Exception.Data['VerifierNativeContainment']
        $captureFacts = $failure.Exception.Data['VerifierCapture']
        Assert-NativeFields $native $jobName
        Require ($null -eq $captureFacts) 'missing executable failure unexpectedly reported a capture state'
        Require ($native.LeaseReturned -eq $false) 'missing executable failure claimed a returned process lease'
        Require ([String]::IsNullOrEmpty([string]$native.ImagePath)) 'missing executable without a created process reported a process image'
        Require ([long]$native.ProcessCreationFileTime -eq 0) 'missing executable without a created process reported a creation FILETIME'
        Require ($native.RootExitObserved -eq $false) 'missing executable failure claimed a root process exit observation'
        Require ($native.JobEmpty -eq $true) 'missing executable failure returned a nonempty caller job'
        Require ($native.ReadHandlesClosed -eq $true) 'missing executable failure left captured reader handles open'
        Require ($native.LeaseClosed -eq $true) 'missing executable failure left an unreturned lease open'
        Require ($null -eq $native.NativeExitCode -and $null -eq $native.ProcessExitCode) 'missing executable without a lease reported exit-code snapshots'
        Require ($native.TerminationRequested -eq $false) 'missing executable failure requested job termination without a process'
        Require ($native.CleanupProven -eq $true) 'missing executable failure did not prove its partial-launch cleanup'
        Require (@($job.GetMemberProcessIds()).Count -eq 0) 'missing executable left a live process in the caller job'
        $nativeCause = Get-BaseException $failure
        Require ($nativeCause.GetType() -eq [System.ComponentModel.Win32Exception]) 'missing executable did not preserve the native launch exception'
        Require ([int]$nativeCause.NativeErrorCode -eq 2) 'missing executable native error code was not ERROR_FILE_NOT_FOUND'

        $processRoot = Assert-ProcessRoot $native.ProcessRoot $true
        $stdoutLog = Join-Path $processRoot 'stdout.log'
        $stderrLog = Join-Path $processRoot 'stderr.log'
        Require ([IO.File]::Exists($stdoutLog) -and [IO.File]::Exists($stderrLog)) 'missing executable did not retain both bounded logs'
        Require ([IO.FileInfo]::new($stdoutLog).Length -eq 0 -and [IO.FileInfo]::new($stderrLog).Length -eq 0) 'missing executable retained nonempty logs despite no launched root'
        $logNames = @([IO.Directory]::GetFiles($processRoot) | ForEach-Object { [IO.Path]::GetFileName($_) } | Sort-Object)
        Equal-Strings $logNames @('stderr.log', 'stdout.log') 'missing executable retained log names'
        Require (-not [IO.File]::Exists((Join-Path $caseDirectory 'emit.state.json'))) 'missing executable fixture unexpectedly ran'
        $logFacts = [ordered]@{
            processRoot = $processRoot
            stdoutLog = $stdoutLog
            stderrLog = $stderrLog
            stdoutBytes = [IO.FileInfo]::new($stdoutLog).Length
            stderrBytes = [IO.FileInfo]::new($stderrLog).Length
            retainedLogNames = $logNames
        }
    } catch {
        $primaryFailure = $_
        if ($null -eq $failure) { $failure = $_ }
    }
    if ($operationWatch.IsRunning) { $operationWatch.Stop() }

    $cleanupWatch = [Diagnostics.Stopwatch]::StartNew()
    $jobCleanup = $null
    if ($null -ne $job) {
        try { $jobCleanup = Close-TestCreatorJob $job }
        catch { $cleanupFailure = $_ }
    }
    $cleanupWatch.Stop()
    $active = [ordered]@{
        name = $caseName
        jobName = $jobName
        missingExecutable = $missingPath
        operationMilliseconds = $operationWatch.Elapsed.TotalMilliseconds
        cleanupMilliseconds = $cleanupWatch.Elapsed.TotalMilliseconds
        operationStatus = 'EXPECTED_NATIVE_LAUNCH_FAILURE_NOT_PASS'
        exceptionType = if ($null -ne $failure) { $failure.Exception.GetType().FullName } else { $null }
        nativeCauseType = if ($null -ne $failure) { $failure.Exception.GetBaseException().GetType().FullName } else { $null }
        nativeErrorCode = if ($null -ne $nativeCause) { [int]$nativeCause.NativeErrorCode } else { $null }
        exceptionMessage = if ($null -ne $failure) { $failure.Exception.Message } else { $null }
        nativeImagePath = if ($null -ne $native) { [string]$native.ImagePath } else { $null }
        processCreationFileTime = if ($null -ne $native) { [long]$native.ProcessCreationFileTime } else { $null }
        captureFacts = $captureFacts
        nativeContainment = $native
        logs = $logFacts
        primaryFailure = if ($null -ne $primaryFailure) { $primaryFailure.Exception.Message } else { $null }
        callerJobMemberIdsAfterFailure = if ($null -ne $jobCleanup) { @($jobCleanup.InitialMemberIds) } else { $null }
        testOwnedJobCleanup = $jobCleanup
        cleanupFailure = if ($null -ne $cleanupFailure) { $cleanupFailure.Exception.Message } else { $null }
    }
    $script:activeCase = $active
    if ($null -ne $cleanupFailure) {
        throw [InvalidOperationException]::new('native missing-executable smoke cleanup failed: ' + $cleanupFailure.Exception.Message)
    }
    if ($null -ne $primaryFailure) {
        throw [InvalidOperationException]::new('native missing-executable smoke assertion failed: ' + $primaryFailure.Exception.Message)
    }
    if ($null -eq $native -or $null -eq $logFacts -or $null -eq $failure -or $null -eq $nativeCause -or $nativeCause.GetType() -ne [System.ComponentModel.Win32Exception]) {
        throw [InvalidOperationException]::new('native missing-executable smoke assertions failed; see failure.json')
    }
    $row = [ordered]@{
        name = $caseName
        status = 'PASS'
        operationStatus = 'EXPECTED_NATIVE_LAUNCH_FAILURE_NOT_PASS'
        operationMilliseconds = $operationWatch.Elapsed.TotalMilliseconds
        cleanupMilliseconds = $cleanupWatch.Elapsed.TotalMilliseconds
        facts = $active
    }
    $cases.Add($row)
    Save-Case $caseDirectory $row
}

function New-NativeCaseContext([string]$Name) {
    $caseDirectory = Join-Path $EvidenceRoot $Name
    $null = [IO.Directory]::CreateDirectory($caseDirectory)
    & $owner { param($Path) Assert-VerifierNoReparseAncestors $Path } $caseDirectory
    $stateDirectory = Join-Path $caseDirectory 'states'
    $null = [IO.Directory]::CreateDirectory($stateDirectory)
    & $owner { param($Path) Assert-VerifierNoReparseAncestors $Path } $stateDirectory
    return [pscustomobject]@{
        Name = $Name
        Directory = $caseDirectory
        StateDirectory = $stateDirectory
        JobName = 'Local\TroubleshootJS.Verifier.NativeCoordinatorContract.' + [guid]::NewGuid().ToString('N')
        Nonce = [guid]::NewGuid().ToString('N')
        Job = $null
        JobCleanup = $null
    }
}

function Assert-NativeClosure($Native, [string]$JobName,
        [bool]$ExpectedTermination, [long]$ExpectedRawExit,
        [int]$ExpectedSignedExit) {
    Assert-NativeFields $Native $JobName
    Require ($Native.LeaseReturned -eq $true) 'native operation did not report its launched process lease'
    Require ($Native.RootExitObserved -eq $true) 'native operation did not observe the original root exit'
    Require ($Native.JobEmpty -eq $true) 'native operation returned before its creator job was empty'
    Require ($Native.ReadHandlesClosed -eq $true) 'native operation did not close both captured read handles'
    Require ($Native.LeaseClosed -eq $true) 'native operation did not close its returned process lease'
    Require ([bool]$Native.TerminationRequested -eq $ExpectedTermination) 'native operation termination request differed from the expected lane'
    Require ($Native.CleanupProven -eq $true) 'native operation did not prove cleanup'
    Require ($Native.CleanupPhase -ceq 'COMPLETE') 'native operation cleanup phase did not complete'
    Require ([long]$Native.ProcessCreationFileTime -gt 0) 'native operation omitted its process creation FILETIME'
    Require ($Native.NativeExitCode -is [uint32] -and [long]$Native.NativeExitCode -eq $ExpectedRawExit) 'native DWORD exit snapshot changed'
    Require ($Native.ProcessExitCode -is [int] -and [int]$Native.ProcessExitCode -eq $ExpectedSignedExit) 'native signed Process.ExitCode snapshot changed'
}

function Assert-NativeSuccessEnvelope($Result, [string]$JobName,
        [int]$ExpectedExitCode, [bool]$ExpectedTermination = $false) {
    Require ($null -ne $Result) 'native operation returned no result'
    Require ($Result.ProcessId -is [int] -and $Result.ProcessId -gt 0) 'native result PID was invalid'
    Require ($Result.ProcessStartTicks -is [long] -and $Result.ProcessStartTicks -gt 0) 'native result start ticks were invalid'
    Require ($Result.ExitCode -is [int] -and [int]$Result.ExitCode -eq $ExpectedExitCode) 'native result signed exit code differed'
    Require ($Result.TerminationProven -eq $true) 'native result did not prove original-process termination'
    $native = $Result.CaptureFacts.NativeContainment
    $expectedRawExit = [BitConverter]::ToUInt32([BitConverter]::GetBytes([int]$ExpectedExitCode), 0)
    Assert-NativeClosure $native $JobName $ExpectedTermination ([long]$expectedRawExit) $ExpectedExitCode
    Require ([long]$native.ProcessCreationFileTime -eq ([long]$Result.ProcessStartTicks - 504911232000000000L)) 'native FILETIME differed from public process start ticks'
    Require ([String]::Equals([IO.Path]::GetFullPath($native.ImagePath), [IO.Path]::GetFullPath($Result.FilePath), [StringComparison]::OrdinalIgnoreCase)) 'native success image path differed from the launched executable'
    $capture = $Result.CaptureFacts
    Require ($capture.ReaderTasksTerminal -eq $true -and $capture.ReadersClosed -eq $true) 'native success did not settle both readers'
    Require ($capture.CleanupProven -eq $true -and $capture.CaptureFailureKind -ceq '') 'native success capture facts reported failure or incomplete cleanup'
    Require ($capture.StdoutObservedBytes -eq $capture.StdoutByteCount -and $capture.StderrObservedBytes -eq $capture.StderrByteCount) 'native success did not account for every retained byte'
    Require ($capture.StdoutDiscardedDuringCleanup -eq 0 -and $capture.StderrDiscardedDuringCleanup -eq 0) 'native success discarded captured bytes during cleanup'
    Require ($Result.Stdout.Length -eq $capture.StdoutByteCount -and $Result.Stderr.Length -eq $capture.StderrByteCount) 'native decoded output length differed from retained byte counts'
    $root = Assert-ProcessRoot $Result.ProcessRoot $false
    foreach ($path in @($Result.StdoutLog, $Result.StderrLog)) {
        Require ([IO.Path]::IsPathRooted([string]$path)) 'native success log path was not absolute'
        Require ([String]::Equals([IO.Path]::GetDirectoryName([IO.Path]::GetFullPath($path)), $root, [StringComparison]::OrdinalIgnoreCase)) 'native success log path escaped its process root'
        Require (-not [IO.File]::Exists($path)) 'native success log remained after return'
    }
    Require ([String]::Equals([IO.Path]::GetFullPath($native.ProcessRoot), $root, [StringComparison]::OrdinalIgnoreCase)) 'native result and containment process roots differed'
    return [pscustomobject]@{ Native = $native; Capture = $capture; ProcessRoot = $root; Logs = @() }
}

function Assert-PatternBytes([byte[]]$Bytes, [char]$Pattern, [string]$Label) {
    for ($index = 0; $index -lt $Bytes.Length; $index++) {
        if ($Bytes[$index] -ne [byte][char]$Pattern) { throw ($Label + ' contained a non-pattern byte at offset ' + $index) }
    }
}

function Assert-NativeFailureEnvelope($Failure, [string]$JobName,
        [string]$ExpectedFailureKind, [bool]$ExpectedTermination,
        [long]$ExpectedRawExit, [int]$ExpectedSignedExit,
        [int]$StdoutLimit, [int]$StderrLimit,
        [int]$DeclaredStdout, [int]$DeclaredStderr,
        [string]$OverflowLane = '') {
    Require ($null -ne $Failure -and $null -ne $Failure.Exception) 'expected native operation failure was absent'
    Require ($Failure.Exception.Data.Contains('VerifierNativeContainment')) 'native operation failure omitted containment facts'
    Require ($Failure.Exception.Data.Contains('VerifierCapture')) 'native operation failure omitted capture facts'
    $native = $Failure.Exception.Data['VerifierNativeContainment']
    $capture = $Failure.Exception.Data['VerifierCapture']
    Require ($null -ne $capture) 'launched process failure omitted byte-capture facts'
    Assert-NativeClosure $native $JobName $ExpectedTermination $ExpectedRawExit $ExpectedSignedExit
    Require ($capture.CaptureFailureKind -ceq $ExpectedFailureKind) 'native capture failure kind differed from the expected operation'
    Require ($capture.ReaderTasksTerminal -eq $true -and $capture.ReadersClosed -eq $true) 'native failure readers did not settle'
    Require ($capture.CleanupProven -eq $true -and $capture.FailureLogsRetained -eq $true) 'native failure did not prove cleanup and retained prefixes'
    Require ([int]$capture.ProcessId -gt 0 -and [long]$capture.StartTicks -gt 0) 'native capture failure omitted process identity'
    Require ([long]$native.ProcessCreationFileTime -eq ([long]$capture.StartTicks - 504911232000000000L)) 'native failure FILETIME differed from capture identity'
    $root = Assert-ProcessRoot $native.ProcessRoot $true
    $logFacts = New-Object 'System.Collections.Generic.List[object]'
    for ($index = 0; $index -lt 2; $index++) {
        $laneName = @('Stdout', 'Stderr')[$index]
        $pattern = @([char]'O', [char]'E')[$index]
        $limit = @($StdoutLimit, $StderrLimit)[$index]
        $declared = @($DeclaredStdout, $DeclaredStderr)[$index]
        $logPath = Join-Path $root (($laneName.ToLowerInvariant()) + '.log')
        Require ([IO.File]::Exists($logPath)) ('native failure omitted ' + $laneName + ' prefix log')
        $bytes = [IO.File]::ReadAllBytes($logPath)
        $count = [int]$capture.($laneName + 'ByteCount')
        $observed = [long]$capture.($laneName + 'ObservedBytes')
        $discarded = [long]$capture.($laneName + 'DiscardedDuringCleanup')
        Require ($bytes.Length -eq $count -and $count -le $limit) ('native retained ' + $laneName + ' prefix exceeded its explicit cap')
        Require ($observed -ge $count -and ($observed + $discarded) -le $declared) ('native ' + $laneName + ' byte accounting exceeded the declared fixture output')
        Assert-PatternBytes $bytes $pattern ($laneName + ' failure prefix')
        if ($OverflowLane -ceq $laneName) {
            Require ($count -eq $limit -and $observed -eq ([long]$limit + 1L)) ('native first excess did not retain exactly the cap and observe one excess byte on ' + $laneName)
        }
        $logFacts.Add([pscustomobject]@{
            lane = $laneName; path = $logPath; retainedBytes = $count
            observedBytes = $observed; discardedDuringCleanup = $discarded
            limit = $limit; declaredBytes = $declared
        })
    }
    return [pscustomobject]@{ Native = $native; Capture = $capture; ProcessRoot = $root; Logs = @($logFacts.ToArray()) }
}

function Add-NativeCaseRow([string]$Name, [string]$OperationStatus,
        [double]$OperationMilliseconds, [double]$CleanupMilliseconds,
        $Facts) {
    $caseDirectory = Join-Path $EvidenceRoot $Name
    $row = [ordered]@{
        name = $Name; status = 'PASS'; operationStatus = $OperationStatus
        operationMilliseconds = $OperationMilliseconds
        cleanupMilliseconds = $CleanupMilliseconds; facts = $Facts
    }
    $cases.Add($row)
    Save-Case $caseDirectory $row
}

function Run-NativeFixtureCase {
    param(
        [Parameter(Mandatory=$true)][string]$Name,
        [Parameter(Mandatory=$true)][ValidateSet('emit','hold')][string]$FixtureMode,
        [Parameter(Mandatory=$true)][int]$StdoutCount,
        [Parameter(Mandatory=$true)][int]$StderrCount,
        [Parameter(Mandatory=$true)][int]$FixtureExitCode,
        [Parameter(Mandatory=$true)][int]$TimeoutMilliseconds,
        [Parameter(Mandatory=$true)][int]$StdoutLimit,
        [Parameter(Mandatory=$true)][int]$StderrLimit,
        [Parameter(Mandatory=$true)][ValidateSet('SUCCESS','OUTPUT_LIMIT','TIMEOUT','CANCELLED')][string]$ExpectedOperation,
        [int]$CancelAfterMilliseconds = 0
    )
    $context = New-NativeCaseContext $Name
    $job = $null
    $cancelSource = $null
    $result = $null
    $failure = $null
    $primaryFailure = $null
    $cleanupFailure = $null
    $envelope = $null
    $witnessFacts = $null
    $resultFacts = $null
    $operationWatch = [Diagnostics.Stopwatch]::StartNew()
    $operationStatus = ''
    [string[]]$arguments = @($FixtureMode, $context.StateDirectory, $context.Nonce,
        $context.JobName, [string]$StdoutCount, [string]$StderrCount, [string]$FixtureExitCode)
    [string[]]$expectedArgv = @($ChildExecutable) + @($arguments)
    $script:activeCase = [ordered]@{ name = $Name; phase = 'native-invoke'; jobName = $context.JobName; nonce = $context.Nonce; arguments = $arguments }

    try {
        $job = New-TestCreatorJob $context.JobName
        $token = [Threading.CancellationToken]::None
        if ($CancelAfterMilliseconds -gt 0) {
            $cancelSource = [Threading.CancellationTokenSource]::new()
            $cancelSource.CancelAfter($CancelAfterMilliseconds)
            $token = $cancelSource.Token
        }
        $operationWatch.Stop()
        $operationWatch = [Diagnostics.Stopwatch]::StartNew()
        try {
            $result = Invoke-VerifierBoundedProcess -FilePath $ChildExecutable -Arguments $arguments `
                -TimeoutMilliseconds $TimeoutMilliseconds -MaxStdoutBytes $StdoutLimit `
                -MaxStderrBytes $StderrLimit -CancellationToken $token `
                -CreatorJob $job -WorkingDirectory $context.Directory
        } catch { $failure = $_ }
        if ($ExpectedOperation -ceq 'SUCCESS') {
            Require ($null -eq $failure) ('expected natural result failed: ' + $(if ($null -ne $failure) { $failure.Exception.Message } else { '' }))
            $envelope = Assert-NativeSuccessEnvelope $result $context.JobName $FixtureExitCode $false
            Require ($result.Stdout -ceq ('O' * $StdoutCount)) 'native fixture stdout pattern or length differed'
            Require ($result.Stderr -ceq ('E' * $StderrCount)) 'native fixture stderr pattern or length differed'
            $operationStatus = if ($FixtureExitCode -eq 0) { 'NATURAL_EXIT_SUCCESS' } else { 'EXPECTED_NONZERO_EXIT_NOT_SUCCESS' }
        } else {
            Require ($null -ne $failure) ('expected native failure did not occur: ' + $ExpectedOperation)
            $expectedKind = $ExpectedOperation
            $envelope = Assert-NativeFailureEnvelope $failure $context.JobName $expectedKind $true 2 2 `
                $StdoutLimit $StderrLimit $StdoutCount $StderrCount `
                $(if ($ExpectedOperation -ceq 'OUTPUT_LIMIT') { if ($StdoutCount -gt $StdoutLimit) { 'Stdout' } else { 'Stderr' } } else { '' })
            $operationStatus = 'EXPECTED_' + $ExpectedOperation + '_NOT_PASS'
        }
        $statePath = Join-Path $context.StateDirectory ($FixtureMode + '.state.json')
        $witnessFacts = Read-ActorWitness $statePath $context.Nonce $context.JobName $expectedArgv $true $FixtureMode
        $witness = $witnessFacts.witness
        $expectedPid = if ($null -ne $result) { [long]$result.ProcessId } else { [long]$envelope.Capture.ProcessId }
        Require ([long]$witness.pid -eq $expectedPid) 'fixture witness PID differed from the native process identity'
        Require ([long]$witness.creationFileTime -eq [long]$envelope.Native.ProcessCreationFileTime) 'fixture witness FILETIME differed from native process identity'
        Require ([long]$witness.creationFileTime -eq ([long]$envelope.Capture.StartTicks - 504911232000000000L)) 'fixture witness FILETIME differed from capture start ticks'
        Require ($witness.imageSha256 -ceq $ChildImageSha256) 'fixture witness image SHA differed from the pinned CODE fixture'
        Require (@($job.GetMemberProcessIds()).Count -eq 0) 'native fixture operation returned with a nonempty caller job'
        $resultFacts = [ordered]@{
            mode = $FixtureMode; arguments = $arguments; expectedArgv = $expectedArgv
            fixtureExitCode = $FixtureExitCode; stdoutCount = $StdoutCount; stderrCount = $StderrCount
            stdoutLimit = $StdoutLimit; stderrLimit = $StderrLimit
            resultExitCode = if ($null -ne $result) { $result.ExitCode } else { $null }
            operationStatus = $operationStatus; capture = $envelope.Capture
            nativeContainment = $envelope.Native; logs = $envelope.Logs
            actorWitness = $witnessFacts; callerJobEmptyAfterReturn = $true
        }
    } catch {
        $primaryFailure = $_
    }
    if ($operationWatch.IsRunning) { $operationWatch.Stop() }
    $cleanupWatch = [Diagnostics.Stopwatch]::StartNew()
    if ($null -ne $job) {
        try { $context.JobCleanup = Close-TestCreatorJob $job }
        catch { $cleanupFailure = $_ }
    }
    if ($null -ne $cancelSource) {
        try { $cancelSource.Dispose() }
        catch { if ($null -eq $cleanupFailure) { $cleanupFailure = $_ } }
    }
    $cleanupWatch.Stop()
    $active = [ordered]@{
        name = $Name; jobName = $context.JobName; nonce = $context.Nonce
        operationMilliseconds = $operationWatch.Elapsed.TotalMilliseconds
        cleanupMilliseconds = $cleanupWatch.Elapsed.TotalMilliseconds
        operationStatus = $operationStatus; resultFacts = $resultFacts
        nativeContainment = if ($null -ne $envelope) { $envelope.Native } else { $null }
        actorWitness = $witnessFacts
        primaryFailure = if ($null -ne $primaryFailure) { $primaryFailure.Exception.Message } else { $null }
        cleanupFailure = if ($null -ne $cleanupFailure) { $cleanupFailure.Exception.Message } else { $null }
        testOwnedJobCleanup = $context.JobCleanup
    }
    $script:activeCase = $active
    if ($null -ne $primaryFailure -or $null -ne $cleanupFailure) {
        $message = 'native fixture case failed: ' + $Name
        if ($null -ne $primaryFailure) { $message += ': ' + $primaryFailure.Exception.Message }
        if ($null -ne $cleanupFailure) { $message += '; cleanup: ' + $cleanupFailure.Exception.Message }
        throw [InvalidOperationException]::new($message)
    }
    Add-NativeCaseRow $Name $operationStatus $operationWatch.Elapsed.TotalMilliseconds $cleanupWatch.Elapsed.TotalMilliseconds $active
}

function Get-NativeActorObserverSource {
    return @'
using System;
using System.Collections.Generic;
using System.Diagnostics;
using System.Globalization;
using System.IO;
using System.Runtime.InteropServices;
using System.Security.Cryptography;
using System.Text;
using System.Threading;
using System.Web.Script.Serialization;

public sealed class VerifierNativeDescendantActorObserver : IDisposable
{
    private const uint JobObjectQuery = 0x0004;
    private readonly object _gate = new object();
    private readonly ManualResetEvent _ready = new ManualResetEvent(false);
    private readonly string _statePath;
    private readonly string _nonce;
    private readonly string _jobName;
    private readonly string _imagePath;
    private readonly string _imageSha256;
    private readonly string[] _expectedArgv;
    private readonly Thread _worker;
    private Process _process;
    private Exception _failure;
    private int _processId;
    private long _creationFileTime;
    private bool _inExpectedJob;

    [DllImport("kernel32.dll", CharSet = CharSet.Unicode, SetLastError = true)]
    private static extern IntPtr OpenJobObjectW(uint desiredAccess,
        [MarshalAs(UnmanagedType.Bool)] bool inheritHandle, string name);

    [DllImport("kernel32.dll", SetLastError = true)]
    [return: MarshalAs(UnmanagedType.Bool)]
    private static extern bool IsProcessInJob(IntPtr processHandle,
        IntPtr jobHandle, [MarshalAs(UnmanagedType.Bool)] out bool result);

    [DllImport("kernel32.dll", SetLastError = true)]
    [return: MarshalAs(UnmanagedType.Bool)]
    private static extern bool CloseHandle(IntPtr handle);

    public VerifierNativeDescendantActorObserver(string statePath, string nonce,
        string jobName, string imagePath, string imageSha256, string[] expectedArgv)
    {
        _statePath = statePath;
        _nonce = nonce;
        _jobName = jobName;
        _imagePath = Path.GetFullPath(imagePath);
        _imageSha256 = imageSha256;
        _expectedArgv = (string[])expectedArgv.Clone();
        _worker = new Thread(Observe);
        _worker.IsBackground = true;
        _worker.Name = "Verifier native descendant identity observer";
        _worker.Start();
    }

    private static string Text(Dictionary<string, object> record, string name)
    {
        object value;
        if (!record.TryGetValue(name, out value) || !(value is string))
            throw new InvalidDataException("Actor witness string missing: " + name);
        return (string)value;
    }

    private static long Integer(Dictionary<string, object> record, string name)
    {
        object value;
        if (!record.TryGetValue(name, out value))
            throw new InvalidDataException("Actor witness integer missing: " + name);
        return Convert.ToInt64(value, CultureInfo.InvariantCulture);
    }

    private static string HashFile(string path)
    {
        using (FileStream stream = File.OpenRead(path))
        using (SHA256 sha = SHA256.Create())
        {
            byte[] digest = sha.ComputeHash(stream);
            StringBuilder text = new StringBuilder(digest.Length * 2);
            foreach (byte value in digest)
                text.Append(value.ToString("x2", CultureInfo.InvariantCulture));
            return text.ToString();
        }
    }

    private static bool IsInJob(Process process, string jobName)
    {
        IntPtr job = OpenJobObjectW(JobObjectQuery, false, jobName);
        if (job == IntPtr.Zero || job == new IntPtr(-1))
            throw new System.ComponentModel.Win32Exception(Marshal.GetLastWin32Error());
        try
        {
            bool result;
            if (!IsProcessInJob(process.Handle, job, out result))
                throw new System.ComponentModel.Win32Exception(Marshal.GetLastWin32Error());
            return result;
        }
        finally
        {
            if (!CloseHandle(job))
                throw new System.ComponentModel.Win32Exception(Marshal.GetLastWin32Error());
        }
    }

    private void Observe()
    {
        Stopwatch watch = Stopwatch.StartNew();
        Exception lastRetry = null;
        try
        {
            while (watch.ElapsedMilliseconds < 8000)
            {
                if (!File.Exists(_statePath))
                {
                    Thread.Sleep(10);
                    continue;
                }
                Process candidate = null;
                bool retained = false;
                try
                {
                    string body = File.ReadAllText(_statePath);
                    JavaScriptSerializer serializer = new JavaScriptSerializer();
                    Dictionary<string, object> record;
                    try { record = (Dictionary<string, object>)serializer.DeserializeObject(body); }
                    catch (Exception parseError)
                    {
                        lastRetry = parseError;
                        Thread.Sleep(10);
                        continue;
                    }
                    if (record == null || Text(record, "mode") != "hold" ||
                        Text(record, "nonce") != _nonce ||
                        !String.Equals(Text(record, "imagePath"), _imagePath, StringComparison.OrdinalIgnoreCase) ||
                        Text(record, "imageSha256") != _imageSha256)
                        throw new InvalidDataException("Descendant actor witness did not match the expected fixture identity.");

                    object rawArgv;
                    object inExpectedJobValue;
                    if (!record.TryGetValue("argv", out rawArgv) || !(rawArgv is object[]) ||
                        !record.TryGetValue("inExpectedJob", out inExpectedJobValue) || !(inExpectedJobValue is bool))
                        throw new InvalidDataException("Descendant actor witness omitted argv or job membership.");
                    object[] actualArgv = (object[])rawArgv;
                    if (actualArgv.Length != _expectedArgv.Length)
                        throw new InvalidDataException("Descendant actor argv length differed from its expected command.");
                    for (int index = 0; index < actualArgv.Length; index++)
                    {
                        if (!(actualArgv[index] is string) ||
                            !String.Equals((string)actualArgv[index], _expectedArgv[index], StringComparison.Ordinal))
                            throw new InvalidDataException("Descendant actor argv differed at position " + index.ToString(CultureInfo.InvariantCulture));
                    }
                    if (!(bool)inExpectedJobValue)
                        throw new InvalidDataException("Descendant actor witness denied expected job membership.");

                    int pid = checked((int)Integer(record, "pid"));
                    long fileTime = Integer(record, "creationFileTime");
                    if (pid <= 0 || fileTime <= 0)
                        throw new InvalidDataException("Descendant actor identity was not positive.");
                    candidate = Process.GetProcessById(pid);
                    if (candidate.HasExited)
                        throw new InvalidOperationException("Descendant exited before the independent original-handle observer acquired it.");
                    string observedPath = Path.GetFullPath(candidate.MainModule.FileName);
                    if (!String.Equals(observedPath, _imagePath, StringComparison.OrdinalIgnoreCase) ||
                        !String.Equals(HashFile(observedPath), _imageSha256, StringComparison.Ordinal))
                        throw new InvalidDataException("Descendant process image identity differed from the pinned fixture.");
                    long observedFileTime = candidate.StartTime.ToUniversalTime().ToFileTimeUtc();
                    if (observedFileTime != fileTime)
                        throw new InvalidDataException("Descendant original-handle FILETIME differed from its fixture witness.");
                    bool actualJobMembership = IsInJob(candidate, _jobName);
                    if (!actualJobMembership)
                        throw new InvalidDataException("Descendant original process handle was outside its expected containment job.");
                    lock (_gate)
                    {
                        _process = candidate;
                        candidate = null;
                        _processId = pid;
                        _creationFileTime = fileTime;
                        _inExpectedJob = actualJobMembership;
                        retained = true;
                    }
                    return;
                }
                catch (IOException error)
                {
                    lastRetry = error;
                    Thread.Sleep(10);
                }
                finally
                {
                    if (!retained && candidate != null)
                        candidate.Dispose();
                }
            }
            _failure = new TimeoutException("Independent descendant observer did not retain the actor before its bounded deadline.", lastRetry);
        }
        catch (Exception error) { _failure = error; }
        finally { _ready.Set(); }
    }

    public void WaitForAcquired(int milliseconds)
    {
        if (milliseconds <= 0 || !_ready.WaitOne(milliseconds))
            throw new TimeoutException("Independent descendant original-process handle was not acquired in time.");
        lock (_gate)
        {
            if (_failure != null)
                throw new InvalidOperationException("Independent descendant observation failed.", _failure);
            if (_process == null)
                throw new InvalidOperationException("Independent descendant observer retained no original process handle.");
        }
    }

    public int ProcessId { get { lock (_gate) { return _processId; } } }
    public long CreationFileTime { get { lock (_gate) { return _creationFileTime; } } }
    public bool InExpectedJob { get { lock (_gate) { return _inExpectedJob; } } }
    public bool WaitForExit(int milliseconds)
    {
        lock (_gate)
        {
            if (_process == null) throw new InvalidOperationException("No retained descendant process handle.");
            return _process.WaitForExit(milliseconds);
        }
    }
    public bool HasExited
    {
        get
        {
            lock (_gate)
            {
                if (_process == null) throw new InvalidOperationException("No retained descendant process handle.");
                _process.Refresh();
                return _process.HasExited;
            }
        }
    }
    public int ExitCode
    {
        get
        {
            lock (_gate)
            {
                if (_process == null) throw new InvalidOperationException("No retained descendant process handle.");
                if (!_process.WaitForExit(0)) throw new InvalidOperationException("Descendant exit code requested before original-handle signal.");
                return _process.ExitCode;
            }
        }
    }
    public void Dispose()
    {
        if (_worker.IsAlive && !_worker.Join(9000))
            throw new TimeoutException("Independent descendant observer did not finish its bounded lifetime.");
        lock (_gate)
        {
            if (_process != null)
            {
                if (!_process.WaitForExit(0))
                    throw new InvalidOperationException("Cannot dispose a retained descendant observer before its original handle signals.");
                _process.Dispose();
                _process = null;
            }
        }
        _ready.Dispose();
    }
}
'@
}

function Run-NativeExternalSuccessCase {
    param(
        [Parameter(Mandatory=$true)][string]$Name,
        [Parameter(Mandatory=$true)][string]$Executable,
        [Parameter(Mandatory=$true)][AllowEmptyCollection()][string[]]$Arguments,
        [Parameter(Mandatory=$true)][int]$TimeoutMilliseconds,
        [Parameter(Mandatory=$true)][int]$StdoutLimit,
        [Parameter(Mandatory=$true)][int]$StderrLimit,
        [Parameter(Mandatory=$true)][int]$ExpectedExitCode,
        [Parameter(Mandatory=$true)][AllowEmptyString()][string]$ExpectedStdout,
        [Parameter(Mandatory=$true)][AllowEmptyString()][string]$ExpectedStderr,
        [Parameter(Mandatory=$true)][string]$SourceKind,
        [Parameter(Mandatory=$true)][string]$ImageSha256,
        [string]$SourceSha256 = '',
        [string]$ExpectedStdoutSha256 = '',
        [string]$ExpectedStderrSha256 = '',
        [long]$ImageBytes = 0
    )
    $context = New-NativeCaseContext $Name
    $job = $null
    $result = $null
    $primaryFailure = $null
    $cleanupFailure = $null
    $envelope = $null
    $operationWatch = [Diagnostics.Stopwatch]::StartNew()
    $expectedRawExitCode = [BitConverter]::ToUInt32([BitConverter]::GetBytes([int]$ExpectedExitCode), 0)
    $script:activeCase = [ordered]@{ name = $Name; phase = 'native-invoke'; jobName = $context.JobName; filePath = $Executable; arguments = @($Arguments) }
    try {
        $job = New-TestCreatorJob $context.JobName
        $operationWatch.Stop()
        $operationWatch = [Diagnostics.Stopwatch]::StartNew()
        $result = Invoke-VerifierBoundedProcess -FilePath $Executable -Arguments $Arguments `
            -TimeoutMilliseconds $TimeoutMilliseconds -MaxStdoutBytes $StdoutLimit `
            -MaxStderrBytes $StderrLimit -CancellationToken ([Threading.CancellationToken]::None) `
            -CreatorJob $job -WorkingDirectory $context.Directory
        $envelope = Assert-NativeSuccessEnvelope $result $context.JobName $ExpectedExitCode $false
        Require ([String]::Equals([IO.Path]::GetFullPath($result.FilePath), [IO.Path]::GetFullPath($Executable), [StringComparison]::OrdinalIgnoreCase)) 'external native result executable path differed'
        Equal-Strings @($result.Arguments) $Arguments 'external native result arguments'
        Require ($result.Stdout -ceq $ExpectedStdout -and $result.Stderr -ceq $ExpectedStderr) 'external native captured output differed from the pinned expectation'
        $stdoutSha256 = Sha-Bytes ([Text.Encoding]::UTF8.GetBytes($result.Stdout))
        $stderrSha256 = Sha-Bytes ([Text.Encoding]::UTF8.GetBytes($result.Stderr))
        if (-not [String]::IsNullOrEmpty($ExpectedStdoutSha256)) { Require ($stdoutSha256 -ceq $ExpectedStdoutSha256) 'external native stdout bytes differed from their pinned hash' }
        if (-not [String]::IsNullOrEmpty($ExpectedStderrSha256)) { Require ($stderrSha256 -ceq $ExpectedStderrSha256) 'external native stderr bytes differed from their pinned hash' }
        Require ((Sha $Executable) -ceq $ImageSha256) 'external native executable SHA changed after capture'
        Require ([IO.FileInfo]::new($Executable).Length -eq $ImageBytes) 'external native executable size changed after capture'
        if (-not [String]::IsNullOrEmpty($SourceSha256)) { Require ($SourceSha256 -cmatch '^[0-9a-f]{64}$') 'external native source SHA was malformed' }
        Require (@($job.GetMemberProcessIds()).Count -eq 0) 'external native success returned a nonempty caller job'
    } catch { $primaryFailure = $_ }
    if ($operationWatch.IsRunning) { $operationWatch.Stop() }
    $cleanupWatch = [Diagnostics.Stopwatch]::StartNew()
    if ($null -ne $job) {
        try { $context.JobCleanup = Close-TestCreatorJob $job }
        catch { $cleanupFailure = $_ }
    }
    $cleanupWatch.Stop()
    $active = [ordered]@{
        name = $Name; jobName = $context.JobName; filePath = $Executable
        imageSha256 = $ImageSha256; imageBytes = $ImageBytes; sourceKind = $SourceKind
        sourceSha256 = $SourceSha256
        arguments = @($Arguments); expectedExitCode = $ExpectedExitCode
        expectedNativeExitCode = if ($null -ne $result) { [long]$expectedRawExitCode } else { $null }
        expectedProcessExitCode = $ExpectedExitCode
        actualNativeExitCode = if ($null -ne $envelope) { $envelope.Native.NativeExitCode } else { $null }
        actualProcessExitCode = if ($null -ne $envelope) { $envelope.Native.ProcessExitCode } else { $null }
        stdoutBytes = if ($null -ne $result) { $result.Stdout.Length } else { $null }
        stderrBytes = if ($null -ne $result) { $result.Stderr.Length } else { $null }
        stdoutSha256 = if ($null -ne $result) { Sha-Bytes ([Text.Encoding]::UTF8.GetBytes($result.Stdout)) } else { $null }
        stderrSha256 = if ($null -ne $result) { Sha-Bytes ([Text.Encoding]::UTF8.GetBytes($result.Stderr)) } else { $null }
        capture = if ($null -ne $envelope) { $envelope.Capture } else { $null }
        nativeContainment = if ($null -ne $envelope) { $envelope.Native } else { $null }
        callerJobCleanup = $context.JobCleanup
        primaryFailure = if ($null -ne $primaryFailure) { $primaryFailure.Exception.Message } else { $null }
        cleanupFailure = if ($null -ne $cleanupFailure) { $cleanupFailure.Exception.Message } else { $null }
        operationMilliseconds = $operationWatch.Elapsed.TotalMilliseconds
        cleanupMilliseconds = $cleanupWatch.Elapsed.TotalMilliseconds
    }
    $script:activeCase = $active
    if ($null -ne $primaryFailure -or $null -ne $cleanupFailure) {
        $message = 'native external success case failed: ' + $Name
        if ($null -ne $primaryFailure) { $message += ': ' + $primaryFailure.Exception.Message }
        if ($null -ne $cleanupFailure) { $message += '; cleanup: ' + $cleanupFailure.Exception.Message }
        throw [InvalidOperationException]::new($message)
    }
    $operationStatus = if ($ExpectedExitCode -eq 0) { 'NATURAL_EXIT_SUCCESS' } else { 'EXPECTED_NONZERO_EXIT_NOT_SUCCESS' }
    Add-NativeCaseRow $Name $operationStatus $operationWatch.Elapsed.TotalMilliseconds $cleanupWatch.Elapsed.TotalMilliseconds $active
}

function Run-NativeDescendantWriterCase {
    $name = 'native-descendant-writer-outside-sentinel'
    $context = New-NativeCaseContext $name
    $job = $null
    $sentinelProcess = $null
    $sentinelStartTicks = 0L
    $sentinelExitSignaled = $false
    $sentinelDisposed = $false
    $monitor = $null
    $operationFailure = $null
    $primaryFailure = $null
    $cleanupFailure = $null
    $envelope = $null
    $parentWitnessFacts = $null
    $childWitnessFacts = $null
    $sentinelWitnessFacts = $null
    $monitorFacts = $null
    $resultFacts = $null
    $operationWatch = [Diagnostics.Stopwatch]::StartNew()
    $stdoutCount = 16384
    $stderrCount = 16384
    $stdoutLimit = 65536
    $stderrLimit = 65536
    $timeoutMilliseconds = 1800
    [string[]]$childArguments = @('hold', $context.StateDirectory, $context.Nonce,
        $context.JobName, [string]$stdoutCount, [string]$stderrCount, '0')
    $childRawArguments = & $owner { param($Values) ConvertTo-VerifierArgumentString $Values } $childArguments
    [string[]]$parentArguments = @('parent-writer', $context.StateDirectory,
        $context.Nonce, $context.JobName, [string]$childRawArguments)
    [string[]]$expectedParentArgv = @($ChildExecutable) + @($parentArguments)
    [string[]]$expectedChildArgv = @($ChildExecutable) + @($childArguments)
    [string[]]$sentinelArguments = @('sentinel', $context.StateDirectory,
        ([guid]::NewGuid().ToString('N')), $context.JobName)
    [string[]]$expectedSentinelArgv = @($ChildExecutable) + @($sentinelArguments)
    $sentinelNonce = $sentinelArguments[2]
    $script:activeCase = [ordered]@{
        name = $name; phase = 'outside-sentinel-start'; jobName = $context.JobName
        nonce = $context.Nonce; childArguments = $childArguments
        parentArguments = $parentArguments; timeoutMilliseconds = $timeoutMilliseconds
    }

    try {
        $job = New-TestCreatorJob $context.JobName
        $sentinelProcess = & $owner {
            param($Path, $Arguments)
            Start-VerifierProcess $Path $Arguments
        } $ChildExecutable $sentinelArguments
        Require ($null -ne $sentinelProcess -and $sentinelProcess.GetType() -eq [Diagnostics.Process]) 'outside sentinel launch did not retain its original Process handle'
        $sentinelStartTicks = & $owner { param($Process) Get-VerifierLaunchedProcessStartTicks $Process } $sentinelProcess
        $sentinelStatePath = Join-Path $context.StateDirectory 'sentinel.state.json'
        $sentinelWait = [Diagnostics.Stopwatch]::StartNew()
        while (-not [IO.File]::Exists($sentinelStatePath) -and $sentinelWait.ElapsedMilliseconds -lt 5000) {
            Start-Sleep -Milliseconds 10
        }
        $sentinelWait.Stop()
        $sentinelWitnessFacts = Read-ActorWitness $sentinelStatePath $sentinelNonce $context.JobName $expectedSentinelArgv $false 'sentinel'
        Require ([long]$sentinelWitnessFacts.witness.pid -eq [long]$sentinelProcess.Id) 'outside sentinel witness PID differed from its original handle'
        Require ([long]$sentinelWitnessFacts.witness.creationFileTime -eq ([long]$sentinelStartTicks - 504911232000000000L)) 'outside sentinel witness FILETIME differed from its original start identity'
        Require (-not $sentinelProcess.HasExited) 'outside sentinel exited before the native job-stop boundary'
        $memberIdsBeforeCapture = @($job.GetMemberProcessIds() | ForEach-Object { [long]$_ })
        Require ($memberIdsBeforeCapture -notcontains [long]$sentinelProcess.Id) 'outside sentinel was a member of the target creator job'

        $childStatePath = Join-Path $context.StateDirectory 'hold.state.json'
        $monitor = [VerifierNativeDescendantActorObserver]::new(
            $childStatePath, $context.Nonce, $context.JobName, $ChildExecutable,
            $ChildImageSha256, $expectedChildArgv)
        try {
            $operationWatch.Stop()
            $operationWatch = [Diagnostics.Stopwatch]::StartNew()
            $operationFailure = $null
            $null = Invoke-VerifierBoundedProcess -FilePath $ChildExecutable -Arguments $parentArguments `
                -TimeoutMilliseconds $timeoutMilliseconds -MaxStdoutBytes $stdoutLimit `
                -MaxStderrBytes $stderrLimit -CancellationToken ([Threading.CancellationToken]::None) `
                -CreatorJob $job -WorkingDirectory $context.Directory
        } catch { $operationFailure = $_ }
        $operationWatch.Stop()
        Require ($null -ne $operationFailure) 'descendant writer unexpectedly allowed native capture success before the inherited pipe closed'
        $envelope = Assert-NativeFailureEnvelope $operationFailure $context.JobName 'TIMEOUT' $true 0 0 `
            $stdoutLimit $stderrLimit $stdoutCount $stderrCount
        Require ($envelope.Capture.StdoutByteCount -gt 0 -and $envelope.Capture.StderrByteCount -gt 0) 'descendant writer did not contribute to both inherited pipes'
        $parentWitnessFacts = Read-ActorWitness (Join-Path $context.StateDirectory 'parent-writer.state.json') `
            $context.Nonce $context.JobName $expectedParentArgv $true 'parent-writer'
        Require ([long]$parentWitnessFacts.witness.pid -eq [long]$envelope.Capture.ProcessId) 'parent-writer root PID differed from native capture identity'
        Require ([long]$parentWitnessFacts.witness.creationFileTime -eq [long]$envelope.Native.ProcessCreationFileTime) 'parent-writer root FILETIME differed from native lease identity'
        $monitor.WaitForAcquired(1000)
        $boundedWait = $monitor.WaitForExit(5000)
        $originalWaitZero = $monitor.WaitForExit(0)
        $monitorFacts = [ordered]@{
            processId = $monitor.ProcessId
            creationFileTime = $monitor.CreationFileTime
            inExpectedJob = $monitor.InExpectedJob
            originalHandleBoundedWaitForExit = $boundedWait
            originalHandleWaitZeroAfterSettlement = $originalWaitZero
        }
        Require ($monitorFacts.originalHandleBoundedWaitForExit -eq $true -and $monitorFacts.originalHandleWaitZeroAfterSettlement -eq $true) 'known descendant original process handle did not signal after job termination'
        Require ($monitor.HasExited -eq $true) 'known descendant remained live after its original-handle wait signaled'
        Require ($monitor.ExitCode -eq 2) 'known descendant original-handle exit code did not show owned job termination'
        $childWitnessFacts = Read-ActorWitness $childStatePath $context.Nonce $context.JobName $expectedChildArgv $true 'hold'
        Require ([long]$childWitnessFacts.witness.pid -eq [long]$monitor.ProcessId) 'independent descendant monitor PID differed from its actor witness'
        Require ([long]$childWitnessFacts.witness.creationFileTime -eq [long]$monitor.CreationFileTime) 'independent descendant monitor FILETIME differed from actor witness'
        Require (@($job.GetMemberProcessIds()).Count -eq 0) 'native descendant operation left a member in the caller job'
        $sentinelProcess.Refresh()
        Require (-not $sentinelProcess.HasExited) 'native coordinator job termination affected the outside sentinel'
        Require ((& $owner { param($Process) Get-VerifierLaunchedProcessStartTicks $Process } $sentinelProcess) -eq $sentinelStartTicks) 'outside sentinel original-handle start identity changed after native cleanup'
        $memberIdsAfterCapture = @($job.GetMemberProcessIds() | ForEach-Object { [long]$_ })
        Require ($memberIdsAfterCapture -notcontains [long]$sentinelProcess.Id) 'outside sentinel entered the creator job during native cleanup'

        $resultFacts = [ordered]@{
            operationStatus = 'EXPECTED_TIMEOUT_NOT_PASS'
            rootExitCode = $envelope.Native.ProcessExitCode
            rootNativeExitCode = $envelope.Native.NativeExitCode
            nativeContainment = $envelope.Native
            capture = $envelope.Capture
            prefixLogs = $envelope.Logs
            parentArguments = $parentArguments
            parentWitness = $parentWitnessFacts
            childArguments = $childArguments
            childWitness = $childWitnessFacts
            childOriginalHandleObserver = $monitorFacts
            outsideSentinelArguments = $sentinelArguments
            outsideSentinelWitness = $sentinelWitnessFacts
            outsideSentinelAliveAfterJobStop = $true
            callerJobEmptyAfterReturn = $true
        }
    } catch {
        $primaryFailure = $_
        if ($operationWatch.IsRunning) { $operationWatch.Stop() }
    }

    $cleanupWatch = [Diagnostics.Stopwatch]::StartNew()
    if ($null -ne $job) {
        try { $context.JobCleanup = Close-TestCreatorJob $job }
        catch { $cleanupFailure = $_ }
    }
    if ($null -ne $monitor) {
        try { $monitor.Dispose() }
        catch { if ($null -eq $cleanupFailure) { $cleanupFailure = $_ } }
    }
    if ($null -ne $sentinelProcess) {
        try {
            $sentinelProcess.Refresh()
            if (-not $sentinelProcess.WaitForExit(0)) {
                & $owner { param($Process, $StartTicks) Stop-VerifierBoundedProcessExactly $Process $StartTicks 5000 } `
                    $sentinelProcess $sentinelStartTicks | Out-Null
            }
            $sentinelExitSignaled = [bool]$sentinelProcess.WaitForExit(0)
            Require $sentinelExitSignaled 'outside sentinel cleanup did not observe its original handle signal'
        } catch { if ($null -eq $cleanupFailure) { $cleanupFailure = $_ } }
        if ($sentinelExitSignaled) {
            try { $sentinelProcess.Dispose(); $sentinelDisposed = $true }
            catch { if ($null -eq $cleanupFailure) { $cleanupFailure = $_ } }
        }
    }
    $cleanupWatch.Stop()
    $active = [ordered]@{
        name = $name; jobName = $context.JobName; nonce = $context.Nonce
        operationMilliseconds = $operationWatch.Elapsed.TotalMilliseconds
        cleanupMilliseconds = $cleanupWatch.Elapsed.TotalMilliseconds
        resultFacts = $resultFacts
        parentWitness = $parentWitnessFacts; childWitness = $childWitnessFacts
        descendantOriginalHandleObserver = $monitorFacts; outsideSentinelWitness = $sentinelWitnessFacts
        outsideSentinelExitSignaled = $sentinelExitSignaled; outsideSentinelDisposed = $sentinelDisposed
        outsideSentinelOriginalHandleRetainedOnFailure = ($null -ne $sentinelProcess -and -not $sentinelDisposed)
        testOwnedJobCleanup = $context.JobCleanup
        primaryFailure = if ($null -ne $primaryFailure) { $primaryFailure.Exception.Message } else { $null }
        primaryFailureBase = if ($null -ne $primaryFailure) { $primaryFailure.Exception.GetBaseException().Message } else { $null }
        cleanupFailure = if ($null -ne $cleanupFailure) { $cleanupFailure.Exception.Message } else { $null }
    }
    $script:activeCase = $active
    if ($null -ne $primaryFailure -or $null -ne $cleanupFailure) {
        $message = 'native descendant writer and outside sentinel case failed'
        if ($null -ne $primaryFailure) { $message += ': ' + $primaryFailure.Exception.Message }
        if ($null -ne $cleanupFailure) { $message += '; cleanup: ' + $cleanupFailure.Exception.Message }
        throw [InvalidOperationException]::new($message)
    }
    Add-NativeCaseRow $name 'EXPECTED_TIMEOUT_NOT_PASS' $operationWatch.Elapsed.TotalMilliseconds $cleanupWatch.Elapsed.TotalMilliseconds $active
}

function Assert-AdditionalNativeRequestGuards($Context, $Job) {
    $baseline = @{ FilePath = $ChildExecutable; Arguments = [string[]]@('must-not-launch'); TimeoutMilliseconds = 5000;
        MaxStdoutBytes = 32; MaxStderrBytes = 32; CancellationToken = [Threading.CancellationToken]::None;
        CreatorJob = $Job; WorkingDirectory = $Context.Directory }
    $rows = New-Object 'Collections.Generic.List[object]'
    foreach ($name in @('missing-job','missing-directory','missing-stdout','missing-stderr','missing-token',
            'wrong-job-type','wrong-token-type','relative-image','relative-directory','missing-directory-path','fractional-timeout')) {
        $spec = $baseline.Clone()
        switch ($name) {
            'missing-job' { $spec.Remove('CreatorJob') }
            'missing-directory' { $spec.Remove('WorkingDirectory') }
            'missing-stdout' { $spec.Remove('MaxStdoutBytes') }
            'missing-stderr' { $spec.Remove('MaxStderrBytes') }
            'missing-token' { $spec.Remove('CancellationToken') }
            'wrong-job-type' { $spec.CreatorJob = [pscustomobject]@{Name='fake'} }
            'wrong-token-type' { $spec.CancellationToken = $false }
            'relative-image' { $spec.FilePath = 'relative-image.exe' }
            'relative-directory' { $spec.WorkingDirectory = '.' }
            'missing-directory-path' { $spec.WorkingDirectory = Join-Path $Context.Directory 'missing-working-directory' }
            'fractional-timeout' { $spec.TimeoutMilliseconds = [double]1.5 }
        }
        $failure = $null
        try { $null = Invoke-VerifierBoundedProcess @spec } catch { $failure = $_ }
        Require ($null -ne $failure -and $failure.Exception.Data.Contains('VerifierFailureKind')) ('native request guard did not reject: '+$name)
        Require (-not $failure.Exception.Data.Contains('VerifierNativeContainment')) ('native request guard allocated live/evidence state: '+$name)
        Require (@($Job.GetMemberProcessIds()).Count -eq 0) ('native request guard changed caller membership: '+$name)
        $rows.Add([ordered]@{name=$name;operationStatus='EXPECTED_PREFLIGHT_REJECTION_NOT_PASS';jobEmpty=$true})
    }
    $closed = New-TestCreatorJob ($Context.JobName+'.closed')
    $query = $jobType::OpenExisting($Context.JobName+'.closed')
    try {
        $closed.Dispose()
        $spec=$baseline.Clone();$spec.CreatorJob=$closed;$failure=$null
        try {$null=Invoke-VerifierBoundedProcess @spec}catch{$failure=$_}
        Require ($null-ne$failure-and-not$failure.Exception.Data.Contains('VerifierNativeContainment')) 'closed native job did not reject before allocation'
        Require (@($query.GetMemberProcessIds()).Count-eq0) 'closed creator request launched a process'
        $rows.Add([ordered]@{name='closed-creator';operationStatus='EXPECTED_PREFLIGHT_REJECTION_NOT_PASS';jobEmpty=$true})
    } finally {$query.Dispose();$closed.Dispose()}
    $busy = New-TestCreatorJob ($Context.JobName+'.busy')
    $busyLease = $null
    try {
        $stateDirectory=Join-Path $Context.Directory 'busy-states';$null=[IO.Directory]::CreateDirectory($stateDirectory)
        [string[]]$busyArguments=@('hold',$stateDirectory,$Context.Nonce,$busy.Name,'0','0','0')
        $command=&$owner{param($v)ConvertTo-VerifierArgumentString $v} ([string[]](@($ChildExecutable)+@($busyArguments)))
        $busyLease=$busy.LaunchRetained($ChildExecutable,$command,$Context.Directory)
        Require (-not $busyLease.WaitForExit(0)) 'nonempty-job guard setup lacked a live original root'
        $spec=$baseline.Clone();$spec.CreatorJob=$busy;$failure=$null
        try {$null=Invoke-VerifierBoundedProcess @spec}catch{$failure=$_}
        Require ($null-ne$failure-and-not$failure.Exception.Data.Contains('VerifierNativeContainment')) 'busy native job did not reject before allocation'
        Require (-not$busyLease.WaitForExit(0)-and@($busy.GetMemberProcessIds())-contains$busyLease.ProcessId) 'busy-job rejection affected its existing original process'
        $rows.Add([ordered]@{name='nonempty-dedicated-job';operationStatus='EXPECTED_PREFLIGHT_REJECTION_NOT_PASS';existingOriginalRootUnaffected=$true;processId=$busyLease.ProcessId;creationFileTime=$busyLease.CreationFileTime})
    } finally {
        if($null-ne$busyLease){
            $clock=[Diagnostics.Stopwatch]::StartNew();$busy.TerminateOwned([uint32]2)
            while((-not$busyLease.WaitForExit(0)-or@($busy.GetMemberProcessIds()).Count-ne0)-and$clock.ElapsedMilliseconds-lt5000){Start-Sleep -Milliseconds 5}
            Require ($busyLease.WaitForExit(0)-and@($busy.GetMemberProcessIds()).Count-eq0) 'busy guard original-root/job cleanup not proven'
            $busyLease.Dispose()
        }
        $busy.Dispose()
    }
    return ,@($rows.ToArray())
}

function Run-NativeGuardCase {
    $name = 'native-raw-and-capability-guards'
    $context = New-NativeCaseContext $name
    $rawJob = $null
    $creatorJob = $null
    $queryJob = $null
    $rawFailure = $null
    $queryFailure = $null
    $rawCleanupFailure = $null
    $queryCleanupFailure = $null
    $rawFacts = $null
    $additionalGuardFacts = @()
    $queryFacts = $null
    $primaryFailure = $null
    $cleanupMilliseconds = 0.0
    $operationWatch = [Diagnostics.Stopwatch]::StartNew()
    $script:activeCase = [ordered]@{ name = $name; phase = 'raw-preflight'; jobName = $context.JobName }
    try {
        $rawJob = New-TestCreatorJob $context.JobName
        $additionalGuardFacts = Assert-AdditionalNativeRequestGuards $context $rawJob
        $entriesBeforeRawGuard = @([IO.Directory]::GetFileSystemEntries($context.Directory) | Sort-Object)
        try {
            $null = Invoke-VerifierBoundedProcess -FilePath $ChildExecutable -Arguments ([string[]]@('must-not-launch')) `
                -TimeoutMilliseconds 10000 -MaxStdoutBytes ([double]1.5) -MaxStderrBytes 32 `
                -CancellationToken ([Threading.CancellationToken]::None) -CreatorJob $rawJob -WorkingDirectory $context.Directory
        } catch { $rawFailure = $_ }
        Require ($null -ne $rawFailure -and $null -ne $rawFailure.Exception) 'malformed raw byte cap was accepted'
        Require (-not $rawFailure.Exception.Data.Contains('VerifierNativeContainment')) 'prelaunch raw guard unexpectedly reported a created process'
        Require ($rawFailure.Exception.Data.Contains('VerifierFailureKind') -and [string]$rawFailure.Exception.Data['VerifierFailureKind'] -ceq 'infrastructure') 'malformed raw byte cap lacked a typed infrastructure failure'
        Require ($rawFailure.Exception.Message.Contains('Bounded capture byte limits must be exact positive integral scalars.') -or
            $rawFailure.Exception.GetBaseException().Message.Contains('Bounded capture byte limits must be exact positive integral scalars.')) 'malformed raw byte cap failed at an unexpected boundary'
        Require (@($rawJob.GetMemberProcessIds()).Count -eq 0) 'malformed raw byte cap created a process'
        Require (-not [IO.File]::Exists((Join-Path $context.StateDirectory 'emit.state.json'))) 'malformed raw byte cap reached the fixture'
        Equal-Strings @([IO.Directory]::GetFileSystemEntries($context.Directory) | Sort-Object) $entriesBeforeRawGuard 'malformed raw byte cap changed its preflight namespace'
        $rawFacts = [ordered]@{
            operationStatus = 'EXPECTED_RAW_PREFLIGHT_REJECTION_NOT_PASS'
            exceptionType = $rawFailure.Exception.GetType().FullName
            message = $rawFailure.Exception.Message
            processCreated = $false
            nativeProcessRootCreated = $false
            callerJobEmpty = $true
        }
    } catch { $primaryFailure = $_ }
    $rawCleanupWatch = [Diagnostics.Stopwatch]::StartNew()
    if ($null -ne $rawJob) {
        try { $null = Close-TestCreatorJob $rawJob }
        catch { $rawCleanupFailure = $_ }
    }
    $rawCleanupWatch.Stop()
    $cleanupMilliseconds += $rawCleanupWatch.Elapsed.TotalMilliseconds

    if ($null -eq $primaryFailure -and $null -eq $rawCleanupFailure) {
        try {
            $creatorJob = New-TestCreatorJob $context.JobName
            $queryJob = $jobType::OpenExisting($context.JobName)
            Require ($null -ne $queryJob -and $queryJob.GetType() -eq $jobType) 'query-only wrapper did not open the named creator job'
            Require (@($queryJob.GetMemberProcessIds()).Count -eq 0) 'query-only wrapper opened a nonempty job'
            $queryStateDirectory = Join-Path $context.Directory 'query-state'
            $null = [IO.Directory]::CreateDirectory($queryStateDirectory)
            & $owner { param($Path) Assert-VerifierNoReparseAncestors $Path } $queryStateDirectory
            try {
                $null = Invoke-VerifierBoundedProcess -FilePath $ChildExecutable -Arguments ([string[]]@('emit', $queryStateDirectory, $context.Nonce, $context.JobName, '1', '1', '0')) `
                    -TimeoutMilliseconds 10000 -MaxStdoutBytes 32 -MaxStderrBytes 32 `
                    -CancellationToken ([Threading.CancellationToken]::None) -CreatorJob $queryJob -WorkingDirectory $context.Directory
            } catch { $queryFailure = $_ }
            Require ($null -ne $queryFailure -and $queryFailure.Exception.Data.Contains('VerifierNativeContainment')) 'query-only native capability did not fail through the owned coordinator'
            Require ($queryFailure.Exception.Data.Contains('VerifierCapture') -and $null -eq $queryFailure.Exception.Data['VerifierCapture']) 'query-only denial unexpectedly created a byte-capture state'
            $queryNative = $queryFailure.Exception.Data['VerifierNativeContainment']
            Assert-NativeFields $queryNative $context.JobName
            Require ($queryNative.LeaseReturned -eq $false -and [String]::IsNullOrEmpty([string]$queryNative.ImagePath)) 'query-only denial claimed a launched image lease'
            Require ([long]$queryNative.ProcessCreationFileTime -eq 0 -and $queryNative.RootExitObserved -eq $false) 'query-only denial claimed a launched process identity or exit'
            Require ($null -eq $queryNative.NativeExitCode -and $null -eq $queryNative.ProcessExitCode) 'query-only denial reported an exit code without a lease'
            Require ($queryNative.JobEmpty -eq $true -and $queryNative.ReadHandlesClosed -eq $true -and $queryNative.LeaseClosed -eq $true) 'query-only denial did not leave the empty job and unlaunched reader state closed'
            Require ($queryNative.TerminationRequested -eq $false -and $queryNative.CleanupProven -eq $true) 'query-only denial had incorrect cleanup facts'
            $queryCause = Get-BaseException $queryFailure
            Require ($queryCause.Message -ceq 'Only a creator-owned containment job may capture process pipes.' -or
                $queryFailure.Exception.Message.Contains('Only a creator-owned containment job may capture process pipes.')) 'query-only denial came from an unexpected capability guard'
            $queryRoot = Assert-ProcessRoot $queryNative.ProcessRoot $true
            $stdoutLog = Join-Path $queryRoot 'stdout.log'
            $stderrLog = Join-Path $queryRoot 'stderr.log'
            Require ([IO.File]::Exists($stdoutLog) -and [IO.File]::Exists($stderrLog)) 'query-only denial did not preserve bounded partial-launch logs'
            Require ([IO.FileInfo]::new($stdoutLog).Length -eq 0 -and [IO.FileInfo]::new($stderrLog).Length -eq 0) 'query-only denial retained output although no process lease was launched'
            Require (-not [IO.File]::Exists((Join-Path $queryStateDirectory 'emit.state.json'))) 'query-only job capability denial launched the fixture'
            Require (@($queryJob.GetMemberProcessIds()).Count -eq 0 -and @($creatorJob.GetMemberProcessIds()).Count -eq 0) 'query-only denial left a job member'
            $queryFacts = [ordered]@{
                operationStatus = 'EXPECTED_QUERY_ONLY_CAPABILITY_REJECTION_NOT_PASS'
                exceptionType = $queryFailure.Exception.GetType().FullName
                baseExceptionType = $queryCause.GetType().FullName
                baseExceptionMessage = $queryCause.Message
                nativeContainment = $queryNative
                zeroByteLogs = @($stdoutLog, $stderrLog)
                fixtureLaunched = $false
                queryJobEmpty = $true
                creatorJobEmpty = $true
            }
        } catch { $primaryFailure = $_ }
    }
    if ($operationWatch.IsRunning) { $operationWatch.Stop() }
    $queryCleanupWatch = [Diagnostics.Stopwatch]::StartNew()
    if ($null -ne $queryJob) {
        try { $queryJob.Dispose() }
        catch { $queryCleanupFailure = $_ }
    }
    if ($null -ne $creatorJob) {
        try { $null = Close-TestCreatorJob $creatorJob }
        catch { if ($null -eq $queryCleanupFailure) { $queryCleanupFailure = $_ } }
    }
    $queryCleanupWatch.Stop()
    $cleanupMilliseconds += $queryCleanupWatch.Elapsed.TotalMilliseconds
    $operationMilliseconds = [Math]::Max(0.0, $operationWatch.Elapsed.TotalMilliseconds - $cleanupMilliseconds)
    $active = [ordered]@{
        name = $name; jobName = $context.JobName
        operationMilliseconds = $operationMilliseconds
        cleanupMilliseconds = $cleanupMilliseconds
        rawPreflight = $rawFacts; queryCapability = $queryFacts
        additionalRequestGuards = $additionalGuardFacts
        primaryFailure = if ($null -ne $primaryFailure) { $primaryFailure.Exception.Message } else { $null }
        rawCleanupFailure = if ($null -ne $rawCleanupFailure) { $rawCleanupFailure.Exception.Message } else { $null }
        queryCleanupFailure = if ($null -ne $queryCleanupFailure) { $queryCleanupFailure.Exception.Message } else { $null }
    }
    $script:activeCase = $active
    if ($null -ne $primaryFailure -or $null -ne $rawCleanupFailure -or $null -ne $queryCleanupFailure) {
        $message = 'native coordinator guard case failed'
        if ($null -ne $primaryFailure) { $message += ': ' + $primaryFailure.Exception.Message }
        if ($null -ne $rawCleanupFailure) { $message += '; raw cleanup: ' + $rawCleanupFailure.Exception.Message }
        if ($null -ne $queryCleanupFailure) { $message += '; query cleanup: ' + $queryCleanupFailure.Exception.Message }
        throw [InvalidOperationException]::new($message)
    }
    Add-NativeCaseRow $name 'EXPECTED_GUARDS_REJECTED_NOT_PASS' $operationMilliseconds $cleanupMilliseconds $active
}

function Initialize-NativeSuiteArtifacts([string]$CompilerHostPath,
        [string]$CompilerHostSha256, [long]$CompilerHostBytes) {
    $compiledDirectory = Join-Path $EvidenceRoot 'compiled-suite-canaries'
    $null = [IO.Directory]::CreateDirectory($compiledDirectory)
    & $owner { param($Path) Assert-VerifierNoReparseAncestors $Path } $compiledDirectory

    $observerSource = Get-NativeActorObserverSource
    $observerSourcePath = Join-Path $compiledDirectory 'VerifierNativeDescendantActorObserver.cs'
    [IO.File]::WriteAllText($observerSourcePath, $observerSource, [Text.UTF8Encoding]::new($false))
    $script:nativeObserverSourceSha256 = Sha $observerSourcePath
    $script:nativeObserverSourceBytes = [IO.FileInfo]::new($observerSourcePath).Length
    $observerAssembly = Join-Path $compiledDirectory 'VerifierNativeDescendantActorObserver.dll'
    $null = Add-Type -Path $observerSourcePath -OutputAssembly $observerAssembly -OutputType Library `
        -ReferencedAssemblies @('System.dll', 'System.Core.dll', 'System.Web.Extensions.dll') -ErrorAction Stop
    Require ([IO.File]::Exists($observerAssembly)) 'independent descendant observer assembly was not emitted'
    $null = [Reflection.Assembly]::LoadFrom($observerAssembly)
    Require ($null -ne ([Management.Automation.PSTypeName]'VerifierNativeDescendantActorObserver').Type) 'independent observer artifact was not loaded'
    $script:nativeObserverAssemblySha256 = Sha $observerAssembly
    $script:nativeObserverAssemblyBytes = [IO.FileInfo]::new($observerAssembly).Length

    $signedSource = @'
using System;
internal static class VerifierSignedExitCanary
{
    private static int Main(string[] args)
    {
        return -1;
    }
}
'@
    $script:signedExitSourcePath = Join-Path $compiledDirectory 'VerifierSignedExitCanary.cs'
    [IO.File]::WriteAllText($script:signedExitSourcePath, $signedSource, [Text.UTF8Encoding]::new($false))
    $script:signedExitSourceSha256 = Sha $script:signedExitSourcePath
    $script:signedExitSourceBytes = [IO.FileInfo]::new($script:signedExitSourcePath).Length
    $script:signedExitExecutable = Join-Path $compiledDirectory 'VerifierSignedExitCanary.exe'
    $null = Add-Type -Path $script:signedExitSourcePath -OutputAssembly $script:signedExitExecutable -OutputType ConsoleApplication `
        -ReferencedAssemblies @('System.dll', 'System.Core.dll') -ErrorAction Stop
    Require ([IO.File]::Exists($script:signedExitExecutable)) 'signed-exit canary executable was not emitted'
    & $owner { param($Path) Assert-VerifierNoReparseAncestors $Path } $script:signedExitExecutable
    $script:signedExitExecutableSha256 = Sha $script:signedExitExecutable
    $script:signedExitExecutableBytes = [IO.FileInfo]::new($script:signedExitExecutable).Length
    Save 'suite-canary-artifacts.json' @{
        observerSourceSha256 = $script:nativeObserverSourceSha256
        observerSourceBytes = $script:nativeObserverSourceBytes
        observerAssemblySha256 = $script:nativeObserverAssemblySha256
        observerAssemblyBytes = $script:nativeObserverAssemblyBytes
        signedExitSourceSha256 = $script:signedExitSourceSha256
        signedExitSourceBytes = $script:signedExitSourceBytes
        signedExitExecutableSha256 = $script:signedExitExecutableSha256
        signedExitExecutableBytes = $script:signedExitExecutableBytes
        compilerHostPath = $CompilerHostPath
        compilerHostSha256 = $CompilerHostSha256
        compilerHostBytes = $CompilerHostBytes
        scope = 'TEST_ONLY_CODE_CANARIES'
    }
}

function Run-PinnedJavaVersionCase {
    $javaPath = [IO.Path]::GetFullPath($JavaExecutable)
    Require ([IO.File]::Exists($javaPath)) 'pinned Java executable is missing'
    Require ([IO.Path]::GetFileName($javaPath) -ieq 'java.exe') 'pinned Java executable leaf name changed'
    Require ($ExpectedJavaSha256 -cmatch '^[0-9a-f]{64}$' -and (Sha $javaPath) -ceq $ExpectedJavaSha256) 'pinned Java executable SHA changed'
    & $owner { param($Path) Assert-VerifierNoReparseAncestors $Path } $javaPath
    $javaBytes = [IO.FileInfo]::new($javaPath).Length
    $expectedVersionText = 'openjdk version "1.8.0_502"' + "`r`n" +
        'OpenJDK Runtime Environment (Temurin)(build 1.8.0_502-b07)' + "`r`n" +
        'OpenJDK 64-Bit Server VM (Temurin)(build 25.502-b07, mixed mode)' + "`r`n"
    Require ((Sha-Bytes ([Text.Encoding]::UTF8.GetBytes($expectedVersionText))) -ceq '1cc134eee0b09c82ff01739623f30b95dcd265223e8377d051fa0b4ac3c6cfa3') 'pinned Java reference bytes did not match the recorded Temurin output hash'
    Require ([Text.Encoding]::UTF8.GetBytes($expectedVersionText).Length -eq 155) 'pinned Temurin reference output length changed'
    Run-NativeExternalSuccessCase -Name 'native-pinned-jdk-version' -Executable $javaPath `
        -Arguments ([string[]]@('-version')) -TimeoutMilliseconds 10000 -StdoutLimit 1024 `
        -StderrLimit 4096 -ExpectedExitCode 0 -ExpectedStdout '' -ExpectedStderr $expectedVersionText `
        -SourceKind 'PINNED_TEMURIN_8U502_B07_JAVA_VERSION' -ImageSha256 $ExpectedJavaSha256 `
        -ExpectedStdoutSha256 'e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855' `
        -ExpectedStderrSha256 '1cc134eee0b09c82ff01739623f30b95dcd265223e8377d051fa0b4ac3c6cfa3' `
        -ImageBytes $javaBytes
}

function Run-SignedExitCase {
    [string[]]$arguments = @('signed-exit-bit-canary')
    Run-NativeExternalSuccessCase -Name 'native-signed-exit-minus-one' `
        -Executable $script:signedExitExecutable -Arguments $arguments `
        -TimeoutMilliseconds 10000 -StdoutLimit 1024 -StderrLimit 1024 `
        -ExpectedExitCode -1 -ExpectedStdout '' -ExpectedStderr '' `
        -SourceKind 'TEST_ONLY_LITERAL_CSHARP_RETURN_MINUS_ONE' `
        -ImageSha256 $script:signedExitExecutableSha256 -SourceSha256 $script:signedExitSourceSha256 `
        -ExpectedStdoutSha256 'e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855' `
        -ExpectedStderrSha256 'e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855' `
        -ImageBytes $script:signedExitExecutableBytes
}

try {
    Require ($PSVersionTable.PSVersion.Major -eq 5 -and $PSVersionTable.PSVersion.Minor -eq 1) 'Windows PowerShell 5.1 required'
    Require ($Mode -ceq 'Smoke' -or $Mode -ceq 'Suite') 'native coordinator contract mode was invalid'
    if ($Mode -ceq 'Suite') {
        Require (-not [String]::IsNullOrWhiteSpace($JavaExecutable)) 'Suite requires the pinned Java executable'
        Require ($ExpectedJavaSha256 -cmatch '^[0-9a-f]{64}$') 'Suite requires the pinned Java executable SHA'
    }
    Require (Test-Path -LiteralPath $ModulePath -PathType Leaf) 'module file missing'
    Require ($ExpectedModuleBytes -gt 0 -and $ExpectedModuleSha256 -cmatch '^[0-9a-f]{64}$' -and ([IO.FileInfo]::new($ModulePath).Length -eq $ExpectedModuleBytes) -and (Sha $ModulePath) -ceq $ExpectedModuleSha256) 'module pin'
    Require (Test-Path -LiteralPath $FixtureSourcePath -PathType Leaf) 'fixture source missing'
    Require ($ExpectedFixtureSourceSha256 -cmatch '^[0-9a-f]{64}$' -and (Sha $FixtureSourcePath) -ceq $ExpectedFixtureSourceSha256) 'fixture source pin'

    $owner = Import-Module -Name $ModulePath -Force -DisableNameChecking -PassThru
    $ModulePath = [IO.Path]::GetFullPath($ModulePath)
    $FixtureSourcePath = [IO.Path]::GetFullPath($FixtureSourcePath)
    $EvidenceRoot = [IO.Path]::GetFullPath($EvidenceRoot)
    $tempRoot = [IO.Path]::GetFullPath([IO.Path]::GetTempPath())
    & $owner { param($Path) Assert-VerifierNoReparseAncestors $Path } $FixtureSourcePath
    & $owner { param($Path) Assert-VerifierNoReparseAncestors $Path } $EvidenceRoot
    Require ([bool](& $owner { param($Base, $Path) Test-VerifierPhysicalChildPath $Base $Path } $tempRoot $EvidenceRoot)) 'evidence root must be a physical temp child'
    Require (-not [IO.Directory]::Exists($EvidenceRoot) -and -not [IO.File]::Exists($EvidenceRoot)) 'fresh evidence root required'
    $null = [IO.Directory]::CreateDirectory($EvidenceRoot)
    $rootReady = $true
    & $owner { param($Path) Assert-VerifierNoReparseAncestors $Path } $EvidenceRoot

    $compilerHostProcess = [Diagnostics.Process]::GetCurrentProcess()
    try {
        $compilerHostPath = [IO.Path]::GetFullPath($compilerHostProcess.MainModule.FileName)
        $compilerHostSha256 = Sha $compilerHostPath
        $compilerHostBytes = [IO.FileInfo]::new($compilerHostPath).Length
    } finally { $compilerHostProcess.Dispose() }

    $compileDirectory = Join-Path $EvidenceRoot 'compiled-fixture'
    $null = [IO.Directory]::CreateDirectory($compileDirectory)
    & $owner { param($Path) Assert-VerifierNoReparseAncestors $Path } $compileDirectory
    $ChildExecutable = [IO.Path]::GetFullPath((Join-Path $compileDirectory 'VerifierContainmentFixture.exe'))
    Require (-not [IO.File]::Exists($ChildExecutable) -and -not [IO.Directory]::Exists($ChildExecutable)) 'fresh fixture output required'
    $null = Add-Type -Path $FixtureSourcePath -OutputAssembly $ChildExecutable -OutputType ConsoleApplication `
        -ReferencedAssemblies @('System.dll', 'System.Core.dll', 'System.Web.Extensions.dll') -ErrorAction Stop
    Require ([IO.File]::Exists($ChildExecutable)) 'compiled fixture artifact missing'
    & $owner { param($Path) Assert-VerifierNoReparseAncestors $Path } $ChildExecutable
    $ChildImageSha256 = Sha $ChildExecutable
    $ChildImageBytes = [IO.FileInfo]::new($ChildExecutable).Length
    $jobType = & $owner { Initialize-VerifierBrowserContainmentJobApi }
    Require ($jobType -is [type] -and $jobType.Name -ceq 'VerifierBrowserContainmentJobApi') 'native containment job API unavailable'

    $javaPath = ''
    $javaSha256 = $null
    $javaBytes = $null
    if ($Mode -ceq 'Suite') {
        $javaPath = [IO.Path]::GetFullPath($JavaExecutable)
        Require ([IO.File]::Exists($javaPath)) 'pinned Java executable is missing'
        Require ((Sha $javaPath) -ceq $ExpectedJavaSha256) 'pinned Java executable SHA changed before Suite'
        & $owner { param($Path) Assert-VerifierNoReparseAncestors $Path } $javaPath
        $javaSha256 = $ExpectedJavaSha256
        $javaBytes = [IO.FileInfo]::new($javaPath).Length
        Initialize-NativeSuiteArtifacts $compilerHostPath $compilerHostSha256 $compilerHostBytes
    }

    Save 'inputs.json' @{
        hashes = [ordered]@{ module = (Sha $ModulePath); fixtureSource = (Sha $FixtureSourcePath); childArtifact = $ChildImageSha256; compilerHost = $compilerHostSha256; java = $javaSha256 }
        bytes = [ordered]@{ module = $ExpectedModuleBytes; fixtureSource = ([IO.FileInfo]::new($FixtureSourcePath).Length); childArtifact = $ChildImageBytes; compilerHost = $compilerHostBytes; java = $javaBytes }
        compilerHostPath = $compilerHostPath
        powershellVersion = $PSVersionTable.PSVersion.ToString()
        compiler = 'Windows PowerShell Add-Type'
        mode = $Mode
        scope = 'NATIVE_CREATOR_JOB_COORDINATOR_CODE_CONTRACT'
        explicitByteLimits = [ordered]@{
            smoke = @{ stdout = 65536; stderr = 65536 }
            belowCapExit7 = @{ stdout = 4096; stderr = 4096 }
            overflowAndLiveControl = @{ stdout = 4096; stderr = 4096 }
            descendantWriter = @{ stdout = 65536; stderr = 65536 }
            pinnedJava = @{ stdout = 1024; stderr = 4096 }
            signedExit = @{ stdout = 1024; stderr = 1024 }
        }
        callerJobOwnership = 'TEST_CALLER_RETAINS_AND_DISPOSES'
        descendantHandleWaits = 'NOT_ATTESTED'
        productionToolClosure = 'NOT_ATTESTED'
    }

    Run-DuplexSuccessSmoke
    Run-MissingExecutableSmoke
    if ($Mode -ceq 'Suite') {
        Run-NativeFixtureCase -Name 'native-below-cap-exit7' -FixtureMode emit `
            -StdoutCount 4095 -StderrCount 4095 -FixtureExitCode 7 -TimeoutMilliseconds 10000 `
            -StdoutLimit 4096 -StderrLimit 4096 -ExpectedOperation SUCCESS
        Run-NativeFixtureCase -Name 'native-stdout-over-cap' -FixtureMode hold `
            -StdoutCount 8192 -StderrCount 64 -FixtureExitCode 0 -TimeoutMilliseconds 10000 `
            -StdoutLimit 4096 -StderrLimit 4096 -ExpectedOperation OUTPUT_LIMIT
        Run-NativeFixtureCase -Name 'native-stderr-over-cap' -FixtureMode hold `
            -StdoutCount 64 -StderrCount 8192 -FixtureExitCode 0 -TimeoutMilliseconds 10000 `
            -StdoutLimit 4096 -StderrLimit 4096 -ExpectedOperation OUTPUT_LIMIT
        Run-NativeFixtureCase -Name 'native-timeout' -FixtureMode hold `
            -StdoutCount 32 -StderrCount 32 -FixtureExitCode 0 -TimeoutMilliseconds 1200 `
            -StdoutLimit 4096 -StderrLimit 4096 -ExpectedOperation TIMEOUT
        Run-NativeFixtureCase -Name 'native-cancel' -FixtureMode hold `
            -StdoutCount 32 -StderrCount 32 -FixtureExitCode 0 -TimeoutMilliseconds 6000 `
            -StdoutLimit 4096 -StderrLimit 4096 -ExpectedOperation CANCELLED -CancelAfterMilliseconds 1200
        Run-NativeDescendantWriterCase
        Run-NativeGuardCase
        Run-PinnedJavaVersionCase
        Run-SignedExitCase
        Require ($cases.Count -eq 11) 'complete native coordinator Suite case count'
    } else {
        Require ($cases.Count -eq 2) 'complete native coordinator Smoke case count'
    }
    Require ((Sha $ModulePath) -ceq $ExpectedModuleSha256 -and (Sha $FixtureSourcePath) -ceq $ExpectedFixtureSourceSha256 -and (Sha $ChildExecutable) -ceq $ChildImageSha256 -and (Sha $compilerHostPath) -ceq $compilerHostSha256) 'final module, source, child image, and compiler host pins'
    if ($Mode -ceq 'Suite') {
        Require ((Sha $javaPath) -ceq $ExpectedJavaSha256 -and [IO.FileInfo]::new($javaPath).Length -eq $javaBytes) 'final pinned Java executable input changed'
        Require ((Sha $script:signedExitExecutable) -ceq $script:signedExitExecutableSha256 -and (Sha $script:signedExitSourcePath) -ceq $script:signedExitSourceSha256) 'final signed-exit canary inputs changed'
        Require ((Sha (Join-Path $EvidenceRoot 'compiled-suite-canaries\VerifierNativeDescendantActorObserver.cs')) -ceq $script:nativeObserverSourceSha256) 'final descendant observer source changed'
        Require ((Sha (Join-Path $EvidenceRoot 'compiled-suite-canaries\VerifierNativeDescendantActorObserver.dll')) -ceq $script:nativeObserverAssemblySha256) 'final descendant observer assembly changed'
    }
    $suiteStatus = if ($Mode -ceq 'Suite') { 'PASS_CODE_CONTRACT' } else { 'NOT_RUN' }
    Save 'cases.json' @{ status = 'PASS'; mode = $Mode; caseCount = $cases.Count; cases = @($cases.ToArray()); scope = 'NATIVE_CREATOR_JOB_COORDINATOR_CODE_CONTRACT'; descendantHandleWaits = 'NOT_ATTESTED'; productionToolClosure = 'NOT_ATTESTED'; suite = $suiteStatus }
    Write-Output ('Verifier native-coordinator contract mode=' + $Mode + ' cases=' + $cases.Count + ' status=PASS')
    exit 0
} catch {
    if ($rootReady) {
        try {
            Save 'failure.json' @{
                status = 'FAIL_NOT_PASS'
                type = $_.Exception.GetType().FullName
                message = $_.Exception.Message
                scriptStackTrace = $_.ScriptStackTrace
                position = $_.InvocationInfo.PositionMessage
                active = $script:activeCase
                completedCases = @($cases.ToArray())
            }
        } catch { }
    }
    [Console]::Error.WriteLine('Verifier native coordinator contract FAIL: ' + $_.Exception.GetType().FullName)
    exit 2
}
