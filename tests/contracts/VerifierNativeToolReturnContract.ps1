[CmdletBinding()]
param(
    [Parameter(Mandatory=$true)][string]$ModulePath,
    [Parameter(Mandatory=$true)][string]$ExpectedModuleSha256,
    [Parameter(Mandatory=$true)][long]$ExpectedModuleBytes,
    [Parameter(Mandatory=$true)][string]$FixtureSourcePath,
    [Parameter(Mandatory=$true)][string]$ExpectedFixtureSourceSha256,
    [Parameter(Mandatory=$true)][string]$JavaExecutable,
    [Parameter(Mandatory=$true)][string]$ExpectedJavaSha256,
    [Parameter(Mandatory=$true)][string]$JavacExecutable,
    [Parameter(Mandatory=$true)][string]$ExpectedJavacSha256,
    [Parameter(Mandatory=$true)][string]$EvidenceRoot,
    [ValidateSet('Smoke','Suite')][string]$Mode = 'Smoke',
    [switch]$ForcePostReturnAssertionFailure
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
$cases = New-Object 'System.Collections.Generic.List[object]'
$owner = $null
$jobType = $null
$rootCreated = $false
$script:activeCase = $null
$script:verifiedInputs = $null
$script:retainedCallerJobs = New-Object 'System.Collections.Generic.List[object]'
$script:retainedCallerJobFacts = New-Object 'System.Collections.Generic.List[object]'
$script:artifactPins = $null

$PinnedModuleSha256 = '418da63a7bc1e4eb3ad0234bd789a24db2b911b635e6a54468a7bb7087c180a6'
$PinnedModuleBytes = 1092245L
$PinnedPowerShellSha256 = 'd8f182560210d43baaf767d482ad1dc1207372522e91413b9d5e515a875e552d'
$PinnedPowerShellBytes = 495616L
$PinnedJavaSha256 = 'e71f3022c972743d2a37e2597b45282b9c380211cd491c54a0c3d63c11bcfbce'
$PinnedJavaBytes = 304768L
$PinnedJavacSha256 = '61fa6463ee5b59004719b9833a16b148740cfa9f2d1f5a95ca598cc90be04c64'
$PinnedJavacBytes = 24192L
$NativeTimeoutCompileMilliseconds = 60000
$NativeTimeoutRunMilliseconds = 10000
$NativeStdoutLimit = 4096
$NativeStderrLimit = 4096
$CanaryClassName = 'VerifierNativeToolReturnCanary'

function Require([bool]$Condition, [string]$Message) {
    if (-not $Condition) { throw [InvalidOperationException]::new($Message) }
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

function Write-ExclusiveBytes([string]$Path, [byte[]]$Bytes) {
    $stream = [IO.File]::Open($Path, [IO.FileMode]::CreateNew, [IO.FileAccess]::Write, [IO.FileShare]::None)
    try { $stream.Write($Bytes, 0, $Bytes.Length); $stream.Flush() }
    finally { $stream.Dispose() }
}

function Save-Json([string]$Path, $Value) {
    $json = ($Value | ConvertTo-Json -Depth 20) + "`n"
    Write-ExclusiveBytes $Path ([Text.UTF8Encoding]::new($false).GetBytes($json))
}

function Save-Case([string]$Directory, $Value) {
    Save-Json (Join-Path $Directory 'case.json') $Value
}

function Assert-FileTuple([string]$Path, [string]$ExpectedSha256,
        [long]$ExpectedBytes, [string]$Label) {
    Require ([IO.File]::Exists($Path)) ($Label + ' file missing')
    Require ($ExpectedSha256 -cmatch '^[0-9a-f]{64}$') ($Label + ' expected SHA malformed')
    Require ([IO.FileInfo]::new($Path).Length -eq $ExpectedBytes) ($Label + ' byte count mismatch')
    Require ((Sha $Path) -ceq $ExpectedSha256) ($Label + ' SHA-256 mismatch')
}

function Assert-FrozenInputs {
    Assert-FileTuple $script:verifiedInputs.ModulePath $script:verifiedInputs.ModuleSha256 $script:verifiedInputs.ModuleBytes 'module'
    Assert-FileTuple $script:verifiedInputs.FixtureSourcePath $script:verifiedInputs.FixtureSourceSha256 $script:verifiedInputs.FixtureSourceBytes 'Java canary source'
    Assert-FileTuple $script:verifiedInputs.JavaPath $script:verifiedInputs.JavaSha256 $script:verifiedInputs.JavaBytes 'selected java.exe'
    Assert-FileTuple $script:verifiedInputs.JavacPath $script:verifiedInputs.JavacSha256 $script:verifiedInputs.JavacBytes 'selected javac.exe'
    Assert-FileTuple $script:verifiedInputs.PowerShellPath $script:verifiedInputs.PowerShellSha256 $script:verifiedInputs.PowerShellBytes 'selected Windows PowerShell host'
    Assert-FileTuple $script:verifiedInputs.ContractPath $script:verifiedInputs.ContractSha256 $script:verifiedInputs.ContractBytes 'native tool return contract source'
    if ($null -ne $script:artifactPins) {
        Assert-FileTuple $script:artifactPins.SourcePath $script:artifactPins.SourceSha256 $script:artifactPins.SourceBytes 'staged Java source'
        Assert-FileTuple $script:artifactPins.SourceListPath $script:artifactPins.SourceListSha256 $script:artifactPins.SourceListBytes 'javac source list'
        if (-not [String]::IsNullOrEmpty([string]$script:artifactPins.ClassSha256)) {
            Assert-FileTuple $script:artifactPins.ClassPath $script:artifactPins.ClassSha256 $script:artifactPins.ClassBytes 'generated Java class'
        }
    }
}

function Write-SourceList([string]$Path, [string]$SourcePath) {
    $portablePath = $SourcePath.Replace([char]92, [char]47)
    $body = '"' + $portablePath + '"' + "`r`n"
    Write-ExclusiveBytes $Path ([Text.UTF8Encoding]::new($false).GetBytes($body))
}

function New-PhysicalDirectory([string]$Path) {
    Require (-not [IO.Directory]::Exists($Path) -and -not [IO.File]::Exists($Path)) ('expected a new directory: ' + $Path)
    $null = [IO.Directory]::CreateDirectory($Path)
    & $owner { param($Item) Assert-VerifierNoReparseAncestors $Item } $Path
}

function New-ToolCreatorJob([string]$Name) {
    $job = $jobType::CreateNew($Name)
    Require (@($job.GetMemberProcessIds()).Count -eq 0) 'native tool contract requires a fresh empty creator job'
    return $job
}

function Close-ToolCreatorJob($Job) {
    $watch = [Diagnostics.Stopwatch]::StartNew()
    $initialIds = @()
    $memberIds = @()
    $initialObserved = $false
    $finalObserved = $false
    $disposalAttempted = $false
    $terminationRequested = $false
    try {
        $initialIds = @($Job.GetMemberProcessIds() | ForEach-Object { [long]$_ })
        $initialObserved = $true
        $memberIds = @($initialIds)
        $finalObserved = $true
        if ($initialIds.Count -gt 0) {
            $terminationRequested = $true
            $Job.TerminateOwned([uint32]2)
        }
        while ($memberIds.Count -gt 0 -and $watch.ElapsedMilliseconds -lt 5000) {
            Start-Sleep -Milliseconds 20
            $memberIds = @($Job.GetMemberProcessIds() | ForEach-Object { [long]$_ })
        }
        Require ($memberIds.Count -eq 0) 'native tool contract caller job did not settle empty'
        $disposalAttempted = $true
        $Job.Dispose()
    } catch {
        $primary = $_
        $finalObserved = $false
        $queryFailure = $null
        try {
            $memberIds = @($Job.GetMemberProcessIds() | ForEach-Object { [long]$_ })
            $finalObserved = $true
        } catch { $queryFailure = $_.Exception.Message }
        $watch.Stop()
        [void]$script:retainedCallerJobs.Add($Job)
        $facts = [ordered]@{
            initialSnapshotObserved = $initialObserved; initialMemberIds = @($initialIds)
            finalSnapshotObserved = $finalObserved; finalMemberIds = @($memberIds)
            terminationRequested = $terminationRequested; disposalAttempted = $disposalAttempted
            callerJobDisposed = $false; ownerRetainedInThisHost = $true
            retentionLifetime = 'UNTIL_CONTRACT_HOST_EXIT_ONLY'
            cleanupMilliseconds = $watch.Elapsed.TotalMilliseconds; queryFailure = $queryFailure
            cleanupFailure = $primary.Exception.Message
        }
        [void]$script:retainedCallerJobFacts.Add($facts)
        $failure = [InvalidOperationException]::new('native tool caller-job cleanup unproven', $primary.Exception)
        $failure.Data['VerifierCallerJobCleanup'] = $facts
        throw $failure
    }
    $watch.Stop()
    return [pscustomobject]@{
        initialMemberIds = @($initialIds)
        finalMemberIds = @($memberIds)
        terminationRequested = $terminationRequested
        callerJobDisposed = $true
        cleanupMilliseconds = $watch.Elapsed.TotalMilliseconds
    }
}

function Assert-ClassArtifact([string]$ClassesDirectory, [string]$ExpectedSha256) {
    & $owner { param($Item) Assert-VerifierNoReparseAncestors $Item } $ClassesDirectory
    $entries = @([IO.Directory]::GetFileSystemEntries($ClassesDirectory))
    $classPath = Join-Path $ClassesDirectory ($CanaryClassName + '.class')
    Require ($entries.Count -eq 1 -and [String]::Equals([IO.Path]::GetFullPath($entries[0]), [IO.Path]::GetFullPath($classPath), [StringComparison]::OrdinalIgnoreCase)) 'generated output class census was not exactly the literal top-level class'
    $attributes = [IO.File]::GetAttributes($classPath)
    Require (($attributes -band [IO.FileAttributes]::Directory) -eq 0 -and ($attributes -band [IO.FileAttributes]::ReparsePoint) -eq 0) 'generated class entry was not a plain file'
    $bytes = [IO.File]::ReadAllBytes($classPath)
    Require ($bytes.Length -ge 8) 'generated class file was truncated'
    Require ($bytes[0] -eq 0xca -and $bytes[1] -eq 0xfe -and $bytes[2] -eq 0xba -and $bytes[3] -eq 0xbe) 'generated class file magic mismatch'
    $major = ([int]$bytes[6] * 256) + [int]$bytes[7]
    Require ($major -eq 51) 'generated class file major version was not 51'
    $hash = Sha $classPath
    Require ($hash -ceq $ExpectedSha256 -or [String]::IsNullOrEmpty($ExpectedSha256)) 'generated class file SHA changed'
    return [pscustomobject]@{
        relativePath = [IO.Path]::GetFileName($classPath)
        bytes = $bytes.Length
        sha256 = $hash
        magic = 'CAFEBABE'
        majorVersion = $major
        classCount = $entries.Count
    }
}

function Assert-EmptyOutputDirectory([string]$Directory) {
    $entries = @([IO.Directory]::GetFileSystemEntries($Directory))
    Require ($entries.Count -eq 0) 'rejected javac invocation produced output artifacts'
    return [pscustomobject]@{ entryCount = 0 }
}

function Invoke-NativeToolCase {
    param(
        [Parameter(Mandatory=$true)][string]$Name,
        [Parameter(Mandatory=$true)][string]$Executable,
        [Parameter(Mandatory=$true)][string]$ToolSha256,
        [Parameter(Mandatory=$true)][long]$ToolBytes,
        [Parameter(Mandatory=$true)][AllowEmptyString()][string[]]$Arguments,
        [Parameter(Mandatory=$true)][string]$WorkingDirectory,
        [Parameter(Mandatory=$true)][int]$TimeoutMilliseconds,
        [Parameter(Mandatory=$true)][int]$ExpectedExitCode,
        [Parameter(Mandatory=$true)][AllowEmptyString()][string]$ExpectedStdout,
        [Parameter(Mandatory=$true)][AllowEmptyString()][string]$ExpectedStderr,
        [Parameter(Mandatory=$true)][string]$OperationStatus
    )
    $caseDirectory = Join-Path $EvidenceRoot $Name
    New-PhysicalDirectory $caseDirectory
    $jobName = 'Local\TroubleshootJS.Verifier.NativeToolReturn.' + [guid]::NewGuid().ToString('N')
    $job = $null
    $result = $null
    $nativeFailure = $null
    $primaryFailure = $null
    $cleanupFailure = $null
    $jobCleanup = $null
    $operationWatch = [Diagnostics.Stopwatch]::new()
    $resultEnvelope = $null
    $script:activeCase = [ordered]@{
        name = $Name; phase = 'prelaunch'; executable = $Executable
        arguments = @($Arguments); jobName = $jobName; expectedExitCode = $ExpectedExitCode
    }
    try {
        Assert-FrozenInputs
        Assert-FileTuple $Executable $ToolSha256 $ToolBytes ($Name + ' selected executable')
        & $owner { param($Item) Assert-VerifierNoReparseAncestors $Item } $WorkingDirectory
        $job = New-ToolCreatorJob $jobName
        $token = [Threading.CancellationToken]::None
        $operationWatch.Start()
        try {
            $result = Invoke-VerifierBoundedProcess -FilePath $Executable -Arguments $Arguments `
                -TimeoutMilliseconds $TimeoutMilliseconds -MaxStdoutBytes $NativeStdoutLimit `
                -MaxStderrBytes $NativeStderrLimit -CancellationToken $token `
                -CreatorJob $job -WorkingDirectory $WorkingDirectory
            # The API already bounded both streams. Keep its actual result before
            # any assertion can fail; replace this with the checked projection only
            # after all checks succeed.
            $resultEnvelope = [ordered]@{ RawReturnedResult = $result }
        } catch {
            $nativeFailure = $_
            if ($_.Exception.Data.Contains('VerifierNativeContainment')) { $script:activeCase.nativeContainment = $_.Exception.Data['VerifierNativeContainment'] }
            if ($_.Exception.Data.Contains('VerifierCapture')) { $script:activeCase.captureFacts = $_.Exception.Data['VerifierCapture'] }
            throw
        }
        $operationWatch.Stop()

        Require ($result.FilePath -ceq $Executable) 'native tool result executable path changed'
        Require ($result.ExitCode -is [int] -and $result.ExitCode -eq $ExpectedExitCode) 'native tool signed exit code differed'
        Require ($result.TerminationProven -eq $true) 'native tool result did not prove original root termination'
        if (@($result.Arguments).Count -ne $Arguments.Count) { throw 'native tool result argv count differed' }
        for ($index = 0; $index -lt $Arguments.Count; $index++) {
            Require ($result.Arguments[$index] -is [string] -and $result.Arguments[$index] -ceq $Arguments[$index]) ('native tool argv differed at position ' + $index)
        }
        Require ($result.Stdout -ceq $ExpectedStdout -and $result.Stderr -ceq $ExpectedStderr) 'native tool output differed from the independent literal oracle'
        $native = $result.CaptureFacts.NativeContainment
        Require ($native.JobName -ceq $jobName) 'native tool job name differed'
        Require ($native.LeaseReturned -eq $true -and $native.RootExitObserved -eq $true) 'native tool did not return and wait its original process lease'
        Require ($native.JobEmpty -eq $true -and @($job.GetMemberProcessIds()).Count -eq 0) 'native tool job was not empty after original exit'
        Require ($native.ReadHandlesClosed -eq $true -and $native.LeaseClosed -eq $true -and $native.CleanupProven -eq $true) 'native tool reader/lease cleanup was not proven'
        Require ($native.TerminationRequested -eq $false) 'natural tool return unexpectedly requested job termination'
        Require ($native.CleanupPhase -ceq 'COMPLETE' -and $native.OperationPhase -ceq 'COMPLETE') 'native tool operation or cleanup phase did not complete'
        Require ($native.NativeExitCode -is [uint32] -and $native.ProcessExitCode -is [int]) 'native tool raw/signed exit snapshots had invalid types'
        $expectedRawExit = [BitConverter]::ToUInt32([BitConverter]::GetBytes([int]$ExpectedExitCode), 0)
        Require ([long]$native.NativeExitCode -eq [long]$expectedRawExit -and [int]$native.ProcessExitCode -eq $ExpectedExitCode) 'native tool DWORD and signed exit values differed'
        Require ($result.ProcessId -is [int] -and $result.ProcessId -gt 0 -and $result.ProcessStartTicks -is [long] -and $result.ProcessStartTicks -gt 0) 'native tool result process identity was invalid'
        Require ([long]$native.ProcessCreationFileTime -eq ([long]$result.ProcessStartTicks - 504911232000000000L)) 'native tool FILETIME differed from public start identity'
        Require ([String]::Equals([IO.Path]::GetFullPath($native.ImagePath), [IO.Path]::GetFullPath($Executable), [StringComparison]::OrdinalIgnoreCase)) 'native tool image path differed from the selected executable'

        $capture = $result.CaptureFacts
        $stdoutBytes = [Text.Encoding]::UTF8.GetBytes($result.Stdout)
        $stderrBytes = [Text.Encoding]::UTF8.GetBytes($result.Stderr)
        Require ($capture.ReaderTasksTerminal -eq $true -and $capture.ReadersClosed -eq $true -and $capture.CleanupProven -eq $true) 'native tool byte readers did not settle cleanly'
        Require ($capture.CaptureFailureKind -ceq '') 'native tool byte capture reported a failure'
        Require ($capture.StdoutByteCount -eq $stdoutBytes.Length -and $capture.StderrByteCount -eq $stderrBytes.Length) 'native tool retained byte counts differed from output'
        Require ($capture.StdoutObservedBytes -eq $stdoutBytes.Length -and $capture.StderrObservedBytes -eq $stderrBytes.Length) 'native tool observed byte counts differed from output'
        Require ($capture.StdoutDiscardedDuringCleanup -eq 0 -and $capture.StderrDiscardedDuringCleanup -eq 0) 'native tool capture discarded output during cleanup'
        Require ($capture.StdoutByteCount -le $NativeStdoutLimit -and $capture.StderrByteCount -le $NativeStderrLimit) 'native tool output exceeded test-only caps'
        $processRoot = [IO.Path]::GetFullPath($result.ProcessRoot)
        Require (-not [IO.Directory]::Exists($processRoot) -and -not [IO.File]::Exists($processRoot)) 'successful native tool process root remained after return'
        Require ([String]::Equals([IO.Path]::GetFullPath($native.ProcessRoot), $processRoot, [StringComparison]::OrdinalIgnoreCase)) 'native tool public and containment roots differed'
        foreach ($logPath in @($result.StdoutLog, $result.StderrLog)) {
            Require ([IO.Path]::IsPathRooted([string]$logPath) -and [String]::Equals([IO.Path]::GetDirectoryName([IO.Path]::GetFullPath($logPath)), $processRoot, [StringComparison]::OrdinalIgnoreCase)) 'native tool success log path was outside its process root'
            Require (-not [IO.File]::Exists($logPath)) 'native tool success log remained after return'
        }
        Assert-FrozenInputs
        Assert-FileTuple $Executable $ToolSha256 $ToolBytes ($Name + ' selected executable after return')
        $resultEnvelope = [ordered]@{
            filePath = $result.FilePath; arguments = @($result.Arguments)
            processId = $result.ProcessId; processStartTicks = $result.ProcessStartTicks
            processCreationFileTime = $native.ProcessCreationFileTime
            exitCode = $result.ExitCode; nativeExitCode = $native.NativeExitCode
            processExitCode = $native.ProcessExitCode
            terminationProven = $result.TerminationProven
            stdout = $result.Stdout; stderr = $result.Stderr
            stdoutBytes = $stdoutBytes.Length; stderrBytes = $stderrBytes.Length
            stdoutSha256 = Sha-Bytes $stdoutBytes; stderrSha256 = Sha-Bytes $stderrBytes
            captureFacts = $capture; nativeContainment = $native
            processRootRemoved = $true; successLogPathsRemoved = $true
        }
    } catch { $primaryFailure = $_ }
    if ($operationWatch.IsRunning) { $operationWatch.Stop() }
    $cleanupWatch = [Diagnostics.Stopwatch]::StartNew()
    if ($null -ne $job) {
        try { $jobCleanup = Close-ToolCreatorJob $job }
        catch {
            $cleanupFailure = $_
            if ($_.Exception.Data.Contains('VerifierCallerJobCleanup')) {
                $jobCleanup = $_.Exception.Data['VerifierCallerJobCleanup']
            }
        }
    }
    $cleanupWatch.Stop()
    $active = [ordered]@{
        name = $Name; status = if ($null -eq $primaryFailure -and $null -eq $cleanupFailure) { 'PASS' } else { 'FAIL_NOT_PASS' }
        operationStatus = $OperationStatus; expectedExitCode = $ExpectedExitCode
        executable = $Executable; executableSha256 = $ToolSha256; executableBytes = $ToolBytes
        arguments = @($Arguments); workingDirectory = $WorkingDirectory; jobName = $jobName
        result = $resultEnvelope
        nativeFailure = if ($null -ne $nativeFailure) { $nativeFailure.Exception.Message } else { $null }
        nativeFailureContainment = if ($null -ne $nativeFailure -and $nativeFailure.Exception.Data.Contains('VerifierNativeContainment')) { $nativeFailure.Exception.Data['VerifierNativeContainment'] } else { $null }
        nativeFailureCapture = if ($null -ne $nativeFailure -and $nativeFailure.Exception.Data.Contains('VerifierCapture')) { $nativeFailure.Exception.Data['VerifierCapture'] } else { $null }
        callerJobCleanup = $jobCleanup
        operationMilliseconds = $operationWatch.Elapsed.TotalMilliseconds
        cleanupMilliseconds = $cleanupWatch.Elapsed.TotalMilliseconds
        primaryFailure = if ($null -ne $primaryFailure) { $primaryFailure.Exception.Message } else { $null }
        cleanupFailure = if ($null -ne $cleanupFailure) { $cleanupFailure.Exception.Message } else { $null }
    }
    $script:activeCase = $active
    if ($null -ne $primaryFailure -or $null -ne $cleanupFailure) {
        $message = 'native tool case failed: ' + $Name
        if ($null -ne $primaryFailure) { $message += ': ' + $primaryFailure.Exception.Message }
        if ($null -ne $cleanupFailure) { $message += '; caller-job cleanup: ' + $cleanupFailure.Exception.Message }
        throw [InvalidOperationException]::new($message)
    }
    $row = [ordered]@{
        name = $Name; status = 'PASS'; operationStatus = $OperationStatus
        expectedExitCode = $ExpectedExitCode; result = $resultEnvelope
        arguments = @($Arguments); executable = $Executable
        executableSha256 = $ToolSha256; executableBytes = $ToolBytes
        callerJobCleanup = $jobCleanup
        operationMilliseconds = $operationWatch.Elapsed.TotalMilliseconds
        cleanupMilliseconds = $cleanupWatch.Elapsed.TotalMilliseconds
    }
    $cases.Add($row)
    Save-Case $caseDirectory $row
    return $row
}

try {
    Require ($PSVersionTable.PSVersion.Major -eq 5 -and $PSVersionTable.PSVersion.Minor -eq 1) 'Windows PowerShell 5.1 required'
    Require ($ExpectedModuleSha256 -ceq $PinnedModuleSha256 -and $ExpectedModuleBytes -eq $PinnedModuleBytes) 'module pin did not match the frozen N61 source tuple'
    Require ($ExpectedJavaSha256 -ceq $PinnedJavaSha256 -and $ExpectedJavacSha256 -ceq $PinnedJavacSha256) 'selected JDK8 executable pins differed from frozen scope'
    Require ($ExpectedFixtureSourceSha256 -cmatch '^[0-9a-f]{64}$') 'Java canary source SHA malformed'

    $ModulePath = [IO.Path]::GetFullPath($ModulePath)
    $FixtureSourcePath = [IO.Path]::GetFullPath($FixtureSourcePath)
    $JavaExecutable = [IO.Path]::GetFullPath($JavaExecutable)
    $JavacExecutable = [IO.Path]::GetFullPath($JavacExecutable)
    $ContractPath = [IO.Path]::GetFullPath($MyInvocation.MyCommand.Path)
    Assert-FileTuple $ModulePath $PinnedModuleSha256 $PinnedModuleBytes 'module'
    Require ([IO.File]::Exists($FixtureSourcePath)) 'Java canary source missing'
    Require ((Sha $FixtureSourcePath) -ceq $ExpectedFixtureSourceSha256) 'Java canary source SHA mismatch'
    Assert-FileTuple $JavaExecutable $PinnedJavaSha256 $PinnedJavaBytes 'selected java.exe'
    Assert-FileTuple $JavacExecutable $PinnedJavacSha256 $PinnedJavacBytes 'selected javac.exe'

    foreach ($variableName in @('JAVA_TOOL_OPTIONS', '_JAVA_OPTIONS', 'JDK_JAVA_OPTIONS', 'CLASSPATH')) {
        Require ($null -eq [Environment]::GetEnvironmentVariable($variableName, [EnvironmentVariableTarget]::Process)) ('ambient ' + $variableName + ' must be absent')
    }
    $hostProcess = [Diagnostics.Process]::GetCurrentProcess()
    try {
        $PowerShellPath = [IO.Path]::GetFullPath($hostProcess.MainModule.FileName)
        $PowerShellSha256 = Sha $PowerShellPath
        $PowerShellBytes = [IO.FileInfo]::new($PowerShellPath).Length
    } finally { $hostProcess.Dispose() }
    Assert-FileTuple $PowerShellPath $PinnedPowerShellSha256 $PinnedPowerShellBytes 'selected Windows PowerShell host'
    Require ([IO.File]::Exists($ContractPath)) 'native tool return contract source path unavailable'
    $ContractSha256 = Sha $ContractPath
    $ContractBytes = [IO.FileInfo]::new($ContractPath).Length

    $owner = Import-Module -Name $ModulePath -Force -DisableNameChecking -PassThru
    foreach ($inputPath in @($ModulePath, $FixtureSourcePath, $JavaExecutable, $JavacExecutable, $PowerShellPath, $ContractPath)) {
        & $owner { param($Path) Assert-VerifierNoReparseAncestors $Path } $inputPath
    }
    $jobType = & $owner { Initialize-VerifierBrowserContainmentJobApi }
    Require ($jobType -is [type] -and $jobType.Name -ceq 'VerifierBrowserContainmentJobApi') 'existing native creator-job API unavailable'

    $EvidenceRoot = [IO.Path]::GetFullPath($EvidenceRoot)
    & $owner { param($Path) Assert-VerifierNoReparseAncestors $Path } $EvidenceRoot
    $tempRoot = [IO.Path]::GetFullPath([IO.Path]::GetTempPath())
    Require ([bool](& $owner { param($Base, $Path) Test-VerifierPhysicalChildPath $Base $Path } $tempRoot $EvidenceRoot)) 'evidence root must be a physical temp child'
    Require (-not [IO.Directory]::Exists($EvidenceRoot) -and -not [IO.File]::Exists($EvidenceRoot)) 'fresh evidence root required'
    $null = [IO.Directory]::CreateDirectory($EvidenceRoot)
    $rootCreated = $true
    & $owner { param($Path) Assert-VerifierNoReparseAncestors $Path } $EvidenceRoot

    $script:verifiedInputs = [ordered]@{
        ModulePath = $ModulePath; ModuleSha256 = $PinnedModuleSha256; ModuleBytes = $PinnedModuleBytes
        FixtureSourcePath = $FixtureSourcePath; FixtureSourceSha256 = $ExpectedFixtureSourceSha256
        FixtureSourceBytes = [IO.FileInfo]::new($FixtureSourcePath).Length
        JavaPath = $JavaExecutable; JavaSha256 = $PinnedJavaSha256; JavaBytes = $PinnedJavaBytes
        JavacPath = $JavacExecutable; JavacSha256 = $PinnedJavacSha256; JavacBytes = $PinnedJavacBytes
        PowerShellPath = $PowerShellPath; PowerShellSha256 = $PinnedPowerShellSha256; PowerShellBytes = $PinnedPowerShellBytes
        ContractPath = $ContractPath; ContractSha256 = $ContractSha256; ContractBytes = $ContractBytes
    }
    Save-Json (Join-Path $EvidenceRoot 'inputs.json') ([ordered]@{
        source = [ordered]@{ path = $FixtureSourcePath; sha256 = $ExpectedFixtureSourceSha256; bytes = $script:verifiedInputs.FixtureSourceBytes }
        module = [ordered]@{ path = $ModulePath; sha256 = $PinnedModuleSha256; bytes = $PinnedModuleBytes }
        java = [ordered]@{ path = $JavaExecutable; sha256 = $PinnedJavaSha256; bytes = $PinnedJavaBytes }
        javac = [ordered]@{ path = $JavacExecutable; sha256 = $PinnedJavacSha256; bytes = $PinnedJavacBytes }
        powershell = [ordered]@{ path = $PowerShellPath; sha256 = $PinnedPowerShellSha256; bytes = $PinnedPowerShellBytes; version = $PSVersionTable.PSVersion.ToString() }
        contract = [ordered]@{ path = $ContractPath; sha256 = $ContractSha256; bytes = $ContractBytes }
        mode = $Mode; scope = 'CODE_ONLY_NATIVE_JDK8_TOOL_RETURN_CONTRACT'
        nativeLimits = @{ stdoutBytes = $NativeStdoutLimit; stderrBytes = $NativeStderrLimit; compileMs = $NativeTimeoutCompileMilliseconds; runMs = $NativeTimeoutRunMilliseconds; kind = 'TEST_ONLY' }
        environmentControls = @{ JAVA_TOOL_OPTIONS = 'ABSENT'; _JAVA_OPTIONS = 'ABSENT'; JDK_JAVA_OPTIONS = 'ABSENT'; CLASSPATH = 'ABSENT' }
        claims = @{ nativeRolePolicy = 'NOT_ESTABLISHED'; completeJdkClosure = 'NOT_ATTESTED'; historicalDescendantClosure = 'NOT_ATTESTED' }
    })

    $workspace = Join-Path $EvidenceRoot 'space # native tool return'
    $sourceSpace = Join-Path $workspace 'source space #'
    $sourceListSpace = Join-Path $workspace 'source list #'
    $emptyClasspath = Join-Path $workspace 'empty classpath #'
    $emptySourcepath = Join-Path $workspace 'empty sourcepath #'
    $classesDirectory = Join-Path $workspace 'classes #'
    $rejectedClassesDirectory = Join-Path $workspace 'rejected classes #'
    $emptyExtensionDirectory = Join-Path $workspace 'empty extensions #'
    $emptyEndorsedDirectory = Join-Path $workspace 'empty endorsed #'
    $runDirectory = Join-Path $workspace 'run cwd #'
    foreach ($directory in @($workspace, $sourceSpace, $sourceListSpace, $emptyClasspath, $emptySourcepath, $classesDirectory, $rejectedClassesDirectory, $emptyExtensionDirectory, $emptyEndorsedDirectory, $runDirectory)) {
        New-PhysicalDirectory $directory
    }
    Require (@([IO.Directory]::GetFileSystemEntries($emptyClasspath)).Count -eq 0) 'explicit classpath directory was not empty before compile'
    Require (@([IO.Directory]::GetFileSystemEntries($emptySourcepath)).Count -eq 0) 'explicit sourcepath directory was not empty before compile'
    $stagedSource = Join-Path $sourceSpace 'VerifierNativeToolReturnCanary.java'
    $sourceBytes = [IO.File]::ReadAllBytes($FixtureSourcePath)
    Write-ExclusiveBytes $stagedSource $sourceBytes
    Require ([IO.FileInfo]::new($stagedSource).Length -eq $script:verifiedInputs.FixtureSourceBytes -and (Sha $stagedSource) -ceq $ExpectedFixtureSourceSha256) 'staged Java source was not byte-identical to the pinned source'

    $sourceListPath = Join-Path $sourceListSpace 'sources.rsp'
    Write-SourceList $sourceListPath $stagedSource
    [string[]]$compileArguments = @(
        '-source','7','-target','7','-encoding','UTF-8','-Xlint:-options','-Xlint:unchecked','-proc:none',
        '-classpath',$emptyClasspath,'-sourcepath',$emptySourcepath,'-d',$classesDirectory,
        ('@' + $sourceListPath)
    )
    $sourceListSha256 = Sha $sourceListPath
    $sourceListBytes = [IO.FileInfo]::new($sourceListPath).Length
    $script:artifactPins = [ordered]@{
        SourcePath = $stagedSource; SourceSha256 = $ExpectedFixtureSourceSha256; SourceBytes = $sourceBytes.Length
        SourceListPath = $sourceListPath; SourceListSha256 = $sourceListSha256; SourceListBytes = $sourceListBytes
        ClassPath = (Join-Path $classesDirectory ($CanaryClassName + '.class')); ClassSha256 = ''; ClassBytes = 0L
    }
    $compileFacts = [ordered]@{
        sourcePath = $stagedSource; sourceSha256 = Sha $stagedSource; sourceBytes = $sourceBytes.Length
        sourceListPath = $sourceListPath; sourceListSha256 = $sourceListSha256; sourceListBytes = $sourceListBytes
        argfilePath = $sourceListPath; argfileSha256 = $sourceListSha256; argfileBytes = $sourceListBytes
        invocationArguments = @($compileArguments); sourcePathContainsSpaceAndHash = $stagedSource.Contains(' ') -and $stagedSource.Contains('#')
        explicitClasspath = $emptyClasspath; explicitSourcepath = $emptySourcepath; annotationProcessing = 'DISABLED'
        sourceLevel = 7; targetLevel = 7; encoding = 'UTF-8'
    }
    Save-Json (Join-Path $EvidenceRoot 'compile-inputs.json') $compileFacts

    $null = Invoke-NativeToolCase -Name 'javac-compile-accepted' -Executable $JavacExecutable `
        -ToolSha256 $PinnedJavacSha256 -ToolBytes $PinnedJavacBytes -Arguments $compileArguments `
        -WorkingDirectory $workspace -TimeoutMilliseconds $NativeTimeoutCompileMilliseconds `
        -ExpectedExitCode 0 -ExpectedStdout '' -ExpectedStderr '' -OperationStatus 'NATURAL_EXIT_SUCCESS'
    $classAfterCompile = Assert-ClassArtifact $classesDirectory ''
    $script:artifactPins.ClassSha256 = $classAfterCompile.sha256
    $script:artifactPins.ClassBytes = $classAfterCompile.bytes
    Save-Json (Join-Path $EvidenceRoot 'class-census-before-runs.json') ([ordered]@{
        files = @($classAfterCompile); exactlyOneClass = $true; className = $CanaryClassName
        classMajor = $classAfterCompile.majorVersion; expectedMajor = 51
    })
    $classHash = $classAfterCompile.sha256

    $canonicalWorkingDirectory = [IO.Path]::GetFullPath($runDirectory)
    $javaBaseArguments = @(
        ('-Djava.ext.dirs=' + $emptyExtensionDirectory),
        ('-Djava.endorsed.dirs=' + $emptyEndorsedDirectory),
        '-classpath', $classesDirectory, $CanaryClassName
    )
    $acceptArguments = [string[]](@($javaBaseArguments) + @(
        'accept', '', 'space value', 'quote"value', 'trailing\', '#hash;semi',
        '-9223372036854775808', $canonicalWorkingDirectory
    ))
    $rejectArguments = [string[]](@($javaBaseArguments) + @(
        'reject', '', 'space value', 'quote"value', 'trailing\', '#hash;semi',
        '-9223372036854775808', $canonicalWorkingDirectory
    ))
    $acceptedOutput = "TSJ_NATIVE_TOOL_RETURN_R1`n"
    if ($ForcePostReturnAssertionFailure) { $acceptedOutput = "TSJ_FORCED_WRONG_EXPECTATION_R1`n" }
    $rejectedOutput = "TSJ_NATIVE_TOOL_REJECT_R1`n"
    $hostArgumentFacts = [ordered]@{
        canonicalWorkingDirectory = $canonicalWorkingDirectory
        expectedJavaApplicationArguments = @('accept', '', 'space value', 'quote"value', 'trailing\', '#hash;semi', '-9223372036854775808', $canonicalWorkingDirectory)
        rejectionJavaApplicationArguments = @('reject', '', 'space value', 'quote"value', 'trailing\', '#hash;semi', '-9223372036854775808', $canonicalWorkingDirectory)
        numericSeedParsed = $false
        extensionsDirectory = $emptyExtensionDirectory
        endorsedDirectory = $emptyEndorsedDirectory
    }
    Save-Json (Join-Path $EvidenceRoot 'java-argv-oracle.json') $hostArgumentFacts

    $classBeforeAccept = Assert-ClassArtifact $classesDirectory $classHash
    $null = Invoke-NativeToolCase -Name 'java-accept-tricky-argv' -Executable $JavaExecutable `
        -ToolSha256 $PinnedJavaSha256 -ToolBytes $PinnedJavaBytes -Arguments $acceptArguments `
        -WorkingDirectory $runDirectory -TimeoutMilliseconds $NativeTimeoutRunMilliseconds `
        -ExpectedExitCode 0 -ExpectedStdout $acceptedOutput -ExpectedStderr '' -OperationStatus 'NATURAL_EXIT_SUCCESS'
    $classAfterAccept = Assert-ClassArtifact $classesDirectory $classHash

    if ($Mode -ceq 'Suite') {
        $null = Invoke-NativeToolCase -Name 'java-intended-rejection7' -Executable $JavaExecutable `
            -ToolSha256 $PinnedJavaSha256 -ToolBytes $PinnedJavaBytes -Arguments $rejectArguments `
            -WorkingDirectory $runDirectory -TimeoutMilliseconds $NativeTimeoutRunMilliseconds `
            -ExpectedExitCode 7 -ExpectedStdout '' -ExpectedStderr $rejectedOutput `
            -OperationStatus 'EXPECTED_JAVA_REJECTION_NOT_PASS'
        $classAfterReject = Assert-ClassArtifact $classesDirectory $classHash

        [string[]]$invalidArguments = @(
            '-source','7','-target','7','-encoding','UTF-8','-Xlint:-options','-Xlint:unchecked','-proc:none',
            '-tsj-code-only-invalid-option','-classpath',$emptyClasspath,'-sourcepath',$emptySourcepath,
            '-d',$rejectedClassesDirectory,('@' + $sourceListPath)
        )
        $expectedJavacDiagnostic = 'javac: invalid flag: -tsj-code-only-invalid-option' + "`r`n" +
            'Usage: javac <options> <source files>' + "`r`n" +
            'use -help for a list of possible options' + "`r`n"
        $invalidArgFacts = [ordered]@{
            sourceListPath = $sourceListPath; sourceListSha256 = $sourceListSha256
            arguments = @($invalidArguments); expectedStdout = ''; expectedStderr = $expectedJavacDiagnostic
            expectedExitCode = 2; outputDirectory = $rejectedClassesDirectory
        }
        Save-Json (Join-Path $EvidenceRoot 'javac-rejection-oracle.json') $invalidArgFacts
        $null = Invoke-NativeToolCase -Name 'javac-invalid-flag2' -Executable $JavacExecutable `
            -ToolSha256 $PinnedJavacSha256 -ToolBytes $PinnedJavacBytes -Arguments $invalidArguments `
            -WorkingDirectory $workspace -TimeoutMilliseconds $NativeTimeoutCompileMilliseconds `
            -ExpectedExitCode 2 -ExpectedStdout '' -ExpectedStderr $expectedJavacDiagnostic `
            -OperationStatus 'EXPECTED_COMPILER_REJECTION_NOT_PASS'
        $badOutputFacts = Assert-EmptyOutputDirectory $rejectedClassesDirectory
        $sourceListAfterRejection = Sha $sourceListPath
        Require ($sourceListAfterRejection -ceq $invalidArgFacts.sourceListSha256) 'source list changed during javac invalid-flag rejection'
        $classAfterBadCompiler = Assert-ClassArtifact $classesDirectory $classHash
        Save-Json (Join-Path $EvidenceRoot 'javac-rejection-output.json') ([ordered]@{
            operationStatus = 'EXPECTED_COMPILER_REJECTION_NOT_PASS'
            exitCode = 2; stdout = ''; stderr = $expectedJavacDiagnostic
            sourceListSha256After = $sourceListAfterRejection
            rejectedOutput = $badOutputFacts; acceptedClassAfterRejection = $classAfterBadCompiler
        })
    }

    $classFinal = Assert-ClassArtifact $classesDirectory $classHash
    Require ((Sha $stagedSource) -ceq $ExpectedFixtureSourceSha256) 'staged Java source changed during native invocations'
    Require ((Sha $sourceListPath) -ceq $compileFacts.sourceListSha256) 'javac source list changed during native invocations'
    Require (@([IO.Directory]::GetFileSystemEntries($emptyClasspath)).Count -eq 0) 'explicit classpath directory changed during native invocations'
    Require (@([IO.Directory]::GetFileSystemEntries($emptySourcepath)).Count -eq 0) 'explicit sourcepath directory changed during native invocations'
    Require ((Sha $ContractPath) -ceq $ContractSha256) 'native tool return contract source changed during invocations'
    Assert-FrozenInputs
    Save-Json (Join-Path $EvidenceRoot 'class-census-after-runs.json') ([ordered]@{
        files = @($classFinal); exactlyOneClass = $true; className = $CanaryClassName
        classMajor = $classFinal.majorVersion; expectedMajor = 51
        classSha256BeforeRuns = $classHash; classSha256AfterRuns = $classFinal.sha256
        classBytesBeforeRuns = $classAfterCompile.bytes; classBytesAfterRuns = $classFinal.bytes
        sourceSha256AfterRuns = Sha $stagedSource
        sourceListSha256AfterRuns = Sha $sourceListPath
        acceptedOutputDirectoryCensus = @($classFinal)
        rejectedCompilerOutput = if ($Mode -ceq 'Suite') { $badOutputFacts } else { 'NOT_RUN' }
    })
    $expectedCaseCount = if ($Mode -ceq 'Suite') { 4 } else { 2 }
    Require ($cases.Count -eq $expectedCaseCount) 'native tool return contract case count differed'
    Save-Json (Join-Path $EvidenceRoot 'cases.json') ([ordered]@{
        status = 'PASS'; mode = $Mode; caseCount = $cases.Count
        cases = @($cases.ToArray()); scope = 'CODE_ONLY_NATIVE_JDK8_TOOL_RETURN_CONTRACT'
        nativeRolePolicy = 'NOT_ESTABLISHED'; completeJdkClosure = 'NOT_ATTESTED'
        historicalDescendantClosure = 'NOT_ATTESTED'
    })
    [Console]::Out.WriteLine('Verifier native-tool-return contract mode=' + $Mode + ' cases=' + $cases.Count + ' status=PASS')
    exit 0
} catch {
    if ($rootCreated) {
        try {
            Save-Json (Join-Path $EvidenceRoot 'failure.json') ([ordered]@{
                status = 'FAIL_NOT_PASS'; exceptionType = $_.Exception.GetType().FullName
                message = $_.Exception.Message; scriptStackTrace = $_.ScriptStackTrace
                position = $_.InvocationInfo.PositionMessage; activeCase = $script:activeCase
                completedCases = @($cases.ToArray())
                retainedCallerJobFacts = @($script:retainedCallerJobFacts.ToArray())
            })
        } catch { }
    }
    [Console]::Error.WriteLine('Verifier native-tool-return contract FAIL: ' + $_.Exception.GetType().FullName)
    exit 2
}
