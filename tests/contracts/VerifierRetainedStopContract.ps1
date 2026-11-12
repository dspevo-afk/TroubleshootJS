[CmdletBinding()]
param(
    [Parameter(Mandatory=$true)][string]$ModulePath,
    [Parameter(Mandatory=$true)][string]$ExpectedModuleSha256,
    [Parameter(Mandatory=$true)][long]$ExpectedModuleBytes,
    [Parameter(Mandatory=$true)][string]$ChildExecutable,
    [string]$ExpectedChildImageSha256 = 'ad169f4cb4bfb78c7a5c030a4529c19d6643276778e33994c93e145b6191c3ec',
    [Parameter(Mandatory=$true)][string]$ChildFixture,
    [Parameter(Mandatory=$true)][string]$ExpectedFixtureSha256,
    [Parameter(Mandatory=$true)][string]$EvidenceRoot
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
$cases = New-Object 'System.Collections.Generic.List[object]'
$owner = $null
$rootReady = $false
$script:activeRun = $null
$script:activeEvidence = $null

function Require([bool]$Condition, [string]$Reason) {
    if (-not $Condition) { throw $Reason }
}

function Sha([string]$Path) {
    $stream = [IO.File]::OpenRead($Path)
    $algorithm = [Security.Cryptography.SHA256]::Create()
    try {
        return ([BitConverter]::ToString($algorithm.ComputeHash($stream))).Replace('-','').ToLowerInvariant()
    } finally {
        $algorithm.Dispose()
        $stream.Dispose()
    }
}

function Save([string]$Name, $Value) {
    $body = [Text.UTF8Encoding]::new($false).GetBytes(($Value | ConvertTo-Json -Depth 20) + "`n")
    $stream = [IO.File]::Open((Join-Path $EvidenceRoot $Name), [IO.FileMode]::CreateNew, [IO.FileAccess]::Write, [IO.FileShare]::Read)
    try { $stream.Write($body, 0, $body.Length) }
    finally { $stream.Dispose() }
}

function Equal-Strings($Actual, $Expected, [string]$Label) {
    Require (@($Actual).Count -eq @($Expected).Count) ($Label + ' count')
    for ($i = 0; $i -lt @($Expected).Count; $i++) {
        Require ($Actual[$i] -is [string] -and $Actual[$i] -ceq $Expected[$i]) ($Label + ' value')
    }
}

function Invoke-ExactStop($Run, $ExpectedStartTicks, $WaitMilliseconds) {
    & $owner {
        param($Process, $StartTicks, $Wait)
        Stop-VerifierBoundedProcessExactly $Process $StartTicks $Wait
    } $Run.Process $ExpectedStartTicks $WaitMilliseconds
}

function Get-LaunchStartTicks($Process) {
    return [long](& $owner {
        param($Value)
        Get-VerifierLaunchedProcessStartTicks $Value
    } $Process)
}

function Start-FixtureRun([string]$Name, [string]$Mode) {
    $caseDirectory = Join-Path $EvidenceRoot $Name
    $null = [IO.Directory]::CreateDirectory($caseDirectory)
    $statePath = Join-Path $caseDirectory 'witness.state.json'
    $stdoutPath = Join-Path $caseDirectory 'stdout.log'
    $stderrPath = Join-Path $caseDirectory 'stderr.log'
    $nonce = [guid]::NewGuid().ToString('N')
    $fixtureArguments = @('-I', '-B', $ChildFixture, $Mode, $statePath, $nonce)
    $launch = [pscustomobject]@{
        FilePath = $ChildExecutable
        Arguments = $fixtureArguments
        StdoutPath = $stdoutPath
        StderrPath = $stderrPath
    }
    $process = & $owner {
        param($Spec)
        Start-VerifierProcess $Spec.FilePath $Spec.Arguments $Spec.StdoutPath $Spec.StderrPath
    } $launch
    Require ($null -ne $process -and $process.GetType() -eq [Diagnostics.Process]) 'launcher did not return the original Process object'

    $run = [pscustomobject]@{
        Name = $Name
        Mode = $Mode
        Directory = $caseDirectory
        StatePath = $statePath
        StdoutPath = $stdoutPath
        StderrPath = $stderrPath
        Nonce = $nonce
        Process = $process
        ProcessId = 0
        StartTicks = 0L
        OriginalHandle = [IntPtr]::Zero
        Witness = $null
        StateBody = ''
        ExitCode = $null
        StopOutcome = ''
        CleanupStopProven = $false
    }
    $script:activeRun = $run
    $run.ProcessId = [int]$process.Id
    $run.OriginalHandle = $process.Handle
    $script:activeEvidence = [ordered]@{
        case = $Name
        mode = $Mode
        processId = $run.ProcessId
        startTicks = $null
        originalHandleRetained = ($run.OriginalHandle -ne [IntPtr]::Zero -and $run.OriginalHandle -ne [IntPtr]::new(-1))
        imageSha256 = $ExpectedChildImageSha256
        statePath = $statePath
        stdoutPath = $stdoutPath
        stderrPath = $stderrPath
        witnessRead = $false
    }
    Require ($run.ProcessId -gt 0) 'launched PID was not positive'
    Require ($run.OriginalHandle -ne [IntPtr]::Zero -and $run.OriginalHandle -ne [IntPtr]::new(-1)) 'original process handle was invalid'
    $run.StartTicks = Get-LaunchStartTicks $process
    Require ($run.StartTicks -gt 0) 'retained launch start identity was not positive'
    $script:activeEvidence.startTicks = $run.StartTicks
    return $run
}

function Read-FixtureWitness($Run, [int]$TimeoutMilliseconds = 5000) {
    $watch = [Diagnostics.Stopwatch]::StartNew()
    $lastReadFailure = ''
    while ($watch.ElapsedMilliseconds -lt $TimeoutMilliseconds) {
        if ([IO.File]::Exists($Run.StatePath)) {
            try {
                $body = [IO.File]::ReadAllText($Run.StatePath)
                $witness = $body | ConvertFrom-Json -ErrorAction Stop
                $Run.StateBody = $body
                $Run.Witness = $witness
                $script:activeEvidence.witnessRead = $true
                return $witness
            } catch {
                $lastReadFailure = $_.Exception.Message
            }
        }
        $Run.Process.Refresh()
        if ($Run.Process.HasExited) {
            throw ('fixture exited before its state witness was readable: ' + $lastReadFailure)
        }
        Start-Sleep -Milliseconds 20
    }
    throw ('fixture state witness was not readable within the bound: ' + $lastReadFailure)
}

function Assert-FixtureWitness($Run, $Witness) {
    Require ($Witness.nonce -ceq $Run.Nonce) 'fixture nonce mismatch'
    Require ([long]$Witness.pid -eq $Run.ProcessId) 'fixture PID did not match the retained launch object'
    Require ([long]$Witness.creationFileTime -gt 0) 'fixture creation time was not positive'
    $witnessStartTicks = [long]$Witness.creationFileTime + 504911232000000000L
    Require ($witnessStartTicks -eq $Run.StartTicks) 'fixture creation time did not match the original launch handle'
    Require ($Witness.imageSha256 -ceq $ExpectedChildImageSha256) 'fixture image SHA did not match the selected executable pin'
    Require ([String]::Equals([IO.Path]::GetFullPath($Witness.imagePath), $ChildExecutable, [StringComparison]::OrdinalIgnoreCase)) 'fixture image path did not match the selected executable'

    $expectedArgv = @($Run.Mode, $Run.StatePath, $Run.Nonce)
    Equal-Strings @($Witness.argv) $expectedArgv 'independent fixture argv'
    $parsed = @(& $owner {
        param($RawCommandLine)
        ConvertFrom-VerifierWindowsCommandLine $RawCommandLine
    } $Witness.commandLine)
    Require ($parsed.Count -eq ($expectedArgv.Count + 4)) 'independent raw command line token count'
    Require ([String]::Equals([IO.Path]::GetFullPath($parsed[0]), $ChildExecutable, [StringComparison]::OrdinalIgnoreCase)) 'raw command line executable path'
    $expectedTail = @('-I', '-B', $ChildFixture) + $expectedArgv
    Equal-Strings @($parsed | Select-Object -Skip 1) $expectedTail 'independent raw command line arguments'
}

function Assert-OriginalStillLive($Run, [string]$Label) {
    $Run.Process.Refresh()
    Require (-not $Run.Process.HasExited) ($Label + ': original process handle signaled unexpectedly')
    Require ($Run.Process.Handle -eq $Run.OriginalHandle) ($Label + ': original process handle changed')
    Require ((Get-LaunchStartTicks $Run.Process) -eq $Run.StartTicks) ($Label + ': original start identity changed')
}

function Require-StopRejected($Run, $ExpectedStartTicks, $WaitMilliseconds, [string]$ExpectedMessage) {
    $failure = $null
    try { Invoke-ExactStop $Run $ExpectedStartTicks $WaitMilliseconds }
    catch { $failure = $_ }
    Require ($null -ne $failure) 'invalid bounded stop input unexpectedly succeeded'
    $infrastructure = & $owner {
        param($ErrorRecord)
        Test-VerifierInfrastructureError $ErrorRecord
    } $failure
    Require ([bool]$infrastructure) 'invalid bounded stop did not report an infrastructure failure'
    Require ($failure.Exception.Message -ceq $ExpectedMessage) 'bounded stop rejection message changed'
}

function Complete-And-CheckLogs($Run) {
    & $owner {
        param($Process)
        Complete-VerifierProcessOutputCapture $Process 5000
    } $Run.Process
    Require ([IO.File]::Exists($Run.StdoutPath) -and [IO.File]::Exists($Run.StderrPath)) 'captured logs were not retained'
    if ($Run.Witness) {
        $stdout = [IO.File]::ReadAllText($Run.StdoutPath)
        $stderr = [IO.File]::ReadAllText($Run.StderrPath)
        Require ($stdout -ceq $Run.StateBody) 'stdout did not retain the flushed independent witness'
        if ($Run.Mode -ceq 'success') {
            Require ($stderr -ceq ([string]::Concat('CODE_ONLY_STDERR', [char]10))) 'natural-exit stderr mismatch'
        } else {
            Require ($stderr -ceq '') 'stopped sleep fixture unexpectedly wrote stderr'
        }
    }
}

function Run-Case([string]$Name, [string]$Kind) {
    $script:activeRun = $null
    $script:activeEvidence = [ordered]@{ case = $Name; mode = $null; processId = $null; startTicks = $null; originalHandleRetained = $false; imageSha256 = $ExpectedChildImageSha256; statePath = $null; stdoutPath = $null; stderrPath = $null; witnessRead = $false }
    $operationWatch = [Diagnostics.Stopwatch]::StartNew()
    $primaryFailure = $null
    $run = $null

    try {
        $mode = if ($Kind -in @('natural-correct','natural-wrong')) { 'success' } else { 'sleep' }
        $run = Start-FixtureRun $Name $mode
        $witness = Read-FixtureWitness $run 5000
        Assert-FixtureWitness $run $witness

        if ($mode -ceq 'success') {
            Require ($run.Process.WaitForExit(5000)) 'natural-success fixture did not exit within the bound'
            $run.Process.Refresh()
            Require ($run.Process.HasExited) 'natural-success original handle was not signaled'
            Require ($run.Process.Handle -eq $run.OriginalHandle) 'natural-success original handle changed'
            $run.ExitCode = [int]$run.Process.ExitCode
            Require ($run.ExitCode -eq 0) 'natural-success fixture exit code was not zero'

            if ($Kind -ceq 'natural-correct') {
                Invoke-ExactStop $run $run.StartTicks 5000
                $run.StopOutcome = 'ACCEPTED_ALREADY_EXITED_ORIGINAL_HANDLE'
            } else {
                $wrongTicks = [long]($run.StartTicks + 1L)
                Require-StopRejected $run $wrongTicks 5000 'VERIFIER_INFRASTRUCTURE: Bounded process retained start identity did not match its launch.'
                $run.StopOutcome = 'WRONG_START_REJECTED_AFTER_NATURAL_EXIT'
            }
        } else {
            Assert-OriginalStillLive $run 'sleep fixture before stop checks'
            if ($Kind -ceq 'live-wrong') {
                $wrongTicks = [long]($run.StartTicks + 1L)
                Require-StopRejected $run $wrongTicks 5000 'VERIFIER_INFRASTRUCTURE: Bounded process retained start identity did not match its launch.'
                Assert-OriginalStillLive $run 'wrong-start rejection'
                Invoke-ExactStop $run $run.StartTicks 5000
                $run.StopOutcome = 'WRONG_START_REJECTED_AND_ORIGINAL_STOPPED'
            } else {
                $badWaits = @(
                    [pscustomobject]@{ name = 'string'; value = '5000' },
                    [pscustomobject]@{ name = 'bool'; value = $true },
                    [pscustomobject]@{ name = 'zero'; value = 0 }
                )
                foreach ($badWait in $badWaits) {
                    Require-StopRejected $run $run.StartTicks $badWait.value 'VERIFIER_INFRASTRUCTURE: A bounded verifier process requires exact positive start identity and wait-bound scalars.'
                    Assert-OriginalStillLive $run ('malformed wait ' + $badWait.name)
                }
                Invoke-ExactStop $run $run.StartTicks 5000
                $run.StopOutcome = 'MALFORMED_WAITS_REJECTED_BEFORE_KILL_AND_ORIGINAL_STOPPED'
            }
        }
        $operationWatch.Stop()
    } catch {
        $primaryFailure = $_
        $operationWatch.Stop()
        if ($null -eq $run) { $run = $script:activeRun }
    }

    $cleanupWatch = [Diagnostics.Stopwatch]::StartNew()
    $cleanupFailures = New-Object 'System.Collections.Generic.List[string]'
    if ($null -ne $run -and $null -ne $run.Process) {
        if ($run.StartTicks -gt 0) {
            try {
                Invoke-ExactStop $run $run.StartTicks 5000
                $run.CleanupStopProven = $true
            } catch {
                [void]$cleanupFailures.Add('original-handle stop proof failed: ' + $_.Exception.Message)
            }
        } else {
            [void]$cleanupFailures.Add('original launch start identity was unavailable; no PID-based stop was attempted')
        }
        if ($run.CleanupStopProven) {
            try { Complete-And-CheckLogs $run }
            catch { [void]$cleanupFailures.Add('original-handle output capture failed: ' + $_.Exception.Message) }
        }
        try { $run.Process.Dispose() }
        catch { [void]$cleanupFailures.Add('original Process object disposal failed: ' + $_.Exception.Message) }
    }
    $cleanupWatch.Stop()

    $script:activeEvidence = [ordered]@{
        case = $Name
        mode = if ($null -ne $run) { $run.Mode } else { $null }
        processId = if ($null -ne $run) { $run.ProcessId } else { $null }
        startTicks = if ($null -ne $run -and $run.StartTicks -gt 0) { $run.StartTicks } else { $null }
        originalHandleRetained = ($null -ne $run -and $null -ne $run.Process -and $run.OriginalHandle -ne [IntPtr]::Zero)
        imageSha256 = if ($null -ne $run -and $null -ne $run.Witness) { $run.Witness.imageSha256 } else { $ExpectedChildImageSha256 }
        statePath = if ($null -ne $run) { $run.StatePath } else { $null }
        stdoutPath = if ($null -ne $run) { $run.StdoutPath } else { $null }
        stderrPath = if ($null -ne $run) { $run.StderrPath } else { $null }
        witnessRead = ($null -ne $run -and $null -ne $run.Witness)
        stopOutcome = if ($null -ne $run) { $run.StopOutcome } else { '' }
        cleanupStopProven = ($null -ne $run -and $run.CleanupStopProven)
        primaryFailure = if ($null -ne $primaryFailure) { $primaryFailure.Exception.Message } else { $null }
        cleanupFailures = @($cleanupFailures.ToArray())
        operationMilliseconds = $operationWatch.Elapsed.TotalMilliseconds
        cleanupMilliseconds = $cleanupWatch.Elapsed.TotalMilliseconds
    }

    if ($null -ne $primaryFailure -or $cleanupFailures.Count -gt 0) {
        $summary = 'retained-stop case ' + $Name + ' failed'
        if ($null -ne $primaryFailure) { $summary += ': ' + $primaryFailure.Exception.Message }
        if ($cleanupFailures.Count -gt 0) { $summary += '; cleanup: ' + [String]::Join('; ', $cleanupFailures.ToArray()) }
        throw [InvalidOperationException]::new($summary)
    }

    $row = [ordered]@{
        name = $Name
        status = 'PASS'
        operationMilliseconds = $operationWatch.Elapsed.TotalMilliseconds
        cleanupMilliseconds = $cleanupWatch.Elapsed.TotalMilliseconds
        facts = [ordered]@{
            processId = $run.ProcessId
            startTicks = $run.StartTicks
            imageSha256 = $run.Witness.imageSha256
            originalHandleRetained = $true
            witnessCreationMatched = $true
            stopOutcome = $run.StopOutcome
            cleanupStopProven = $run.CleanupStopProven
            stdoutLog = $run.StdoutPath
            stderrLog = $run.StderrPath
        }
    }
    $cases.Add($row)
    Save ($Name + '.case.json') $row
    $script:activeRun = $null
}

try {
    Require ($PSVersionTable.PSVersion.Major -eq 5 -and $PSVersionTable.PSVersion.Minor -eq 1) 'Windows PowerShell 5.1 required'
    Require ($ExpectedModuleBytes -gt 0 -and $ExpectedModuleSha256 -cmatch '^[0-9a-f]{64}$') 'module byte and SHA pins must be exact positive values'
    Require ([IO.FileInfo]::new($ModulePath).Length -eq $ExpectedModuleBytes -and (Sha $ModulePath) -ceq $ExpectedModuleSha256) 'module pin'
    Require ($ExpectedFixtureSha256 -cmatch '^[0-9a-f]{64}$' -and (Sha $ChildFixture) -ceq $ExpectedFixtureSha256) 'fixture pin'
    Require ($ExpectedChildImageSha256 -cmatch '^[0-9a-f]{64}$' -and (Sha $ChildExecutable) -ceq $ExpectedChildImageSha256) 'selected image pin'

    $owner = Import-Module -Name $ModulePath -Force -DisableNameChecking -PassThru
    $ChildExecutable = [IO.Path]::GetFullPath($ChildExecutable)
    $ChildFixture = [IO.Path]::GetFullPath($ChildFixture)
    $EvidenceRoot = [IO.Path]::GetFullPath($EvidenceRoot)
    $tempRoot = [IO.Path]::GetFullPath([IO.Path]::GetTempPath())
    & $owner { param($Path) Assert-VerifierNoReparseAncestors $Path } $EvidenceRoot
    $insideTemp = & $owner { param($Base, $Path) Test-VerifierPhysicalChildPath $Base $Path } $tempRoot $EvidenceRoot
    Require ([bool]$insideTemp) 'evidence root must be a physical child of the operating-system temp root'
    Require (-not [IO.Directory]::Exists($EvidenceRoot) -and -not [IO.File]::Exists($EvidenceRoot)) 'fresh evidence root required'
    $null = [IO.Directory]::CreateDirectory($EvidenceRoot)
    $rootReady = $true
    & $owner { param($Path) Assert-VerifierNoReparseAncestors $Path } $EvidenceRoot

    $sourcePins = [ordered]@{
        moduleBytes = $ExpectedModuleBytes
        moduleSha256 = $ExpectedModuleSha256
        fixtureBytes = ([IO.FileInfo]::new($ChildFixture).Length)
        fixtureSha256 = $ExpectedFixtureSha256
        childImageBytes = ([IO.FileInfo]::new($ChildExecutable).Length)
        childImageSha256 = $ExpectedChildImageSha256
    }
    Save 'inputs.json' @{ pins = $sourcePins; powershellVersion = $PSVersionTable.PSVersion.ToString(); scope = 'RETAINED_DIRECT_PROCESS_HANDLE_STOP_ONLY'; descendantContainment = 'NOT_TESTED'; }

    Run-Case 'natural-correct-start' 'natural-correct'
    Run-Case 'natural-wrong-start' 'natural-wrong'
    Run-Case 'live-wrong-start' 'live-wrong'
    Run-Case 'malformed-waits' 'malformed-waits'

    Require ($cases.Count -eq 4) 'complete retained-stop case count'
    Require ([IO.FileInfo]::new($ModulePath).Length -eq $ExpectedModuleBytes -and (Sha $ModulePath) -ceq $ExpectedModuleSha256 -and (Sha $ChildFixture) -ceq $ExpectedFixtureSha256 -and (Sha $ChildExecutable) -ceq $ExpectedChildImageSha256) 'final module, fixture, and image pins'
    Save 'cases.json' @{ status = 'PASS'; caseCount = $cases.Count; cases = @($cases.ToArray()); scope = 'RETAINED_DIRECT_PROCESS_HANDLE_STOP_ONLY'; descendantContainment = 'NOT_TESTED'; creatorDeathProtection = 'NOT_TESTED'; }
    Write-Output ('Verifier retained-process stop contract cases=' + $cases.Count + ' status=PASS')
    exit 0
} catch {
    if ($rootReady) {
        try {
            Save 'failure.json' @{
                status = 'FAIL_NOT_PASS'
                type = $_.Exception.GetType().FullName
                message = $_.Exception.Message
                active = $script:activeEvidence
                completedCases = @($cases.ToArray())
            }
        } catch { }
    }
    [Console]::Error.WriteLine('Verifier retained-process stop contract FAIL: ' + $_.Exception.GetType().FullName)
    exit 2
}
