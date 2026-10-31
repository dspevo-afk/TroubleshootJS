[CmdletBinding()]
param([string]$RepositoryRoot = '')

# Focused selected-host contract for the bound-receipt natural-exit wait. The
# positive path uses the real Windows Job API and real nested PowerShell
# processes. The single transient absence in the separate negative path is an
# explicitly injected WMI-lag boundary condition, not host evidence.
Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$script:Checks = 0
$script:Module = $null
$script:WorkRoot = ''
$script:Jobs = New-Object Collections.ArrayList
$script:Scopes = New-Object Collections.ArrayList
$script:RetainedProcesses = New-Object Collections.ArrayList
$script:FailureStage = 'preflight'
$script:ContractPassed = $false

function Assert-Contract([bool]$Condition, [string]$Code) {
    if (-not $Condition) { throw [InvalidOperationException]::new($Code) }
    $script:Checks++
}

function Invoke-ModuleCode {
    param([scriptblock]$Code, $Argument)
    & $script:Module $Code $Argument
}

function New-TaskJob([string]$Name) {
    $job = Invoke-ModuleCode {
        $jobType = Initialize-VerifierBrowserContainmentJobApi
        $jobType::CreateNew([string]$args[0])
    } $Name
    [void]$script:Jobs.Add($job)
    return $job
}

function Start-ContainedPowerShell($Job, [string[]]$Arguments) {
    $launch = [pscustomobject]@{
        Job = $Job
        FilePath = $script:PowerShellExe
        Arguments = $Arguments
        WorkingDirectory = $script:WorkingDirectory
    }
    $processId = Invoke-ModuleCode {
        $launch = $args[0]
        Start-VerifierBrowserProcessInContainmentJob $launch.Job `
            $launch.FilePath $launch.Arguments $launch.WorkingDirectory
    } $launch
    return [int]$processId
}

function New-AttestedProcessRecord {
    param(
        [int]$ProcessId,
        $DrainScope,
        [string]$Label
    )
    $raw = $null
    $process = $null
    $parent = $null
    try {
        for ($attempt = 0; $attempt -lt 20; $attempt++) {
            $rows = @(Invoke-ModuleCode {
                Get-VerifierProcessRecordsByIdWithFallback ([int]$args[0]) `
                    'terminal natural-exit contract identity'
            } $ProcessId)
            if ($rows.Count -eq 1) { $raw = $rows[0]; break }
            Start-Sleep -Milliseconds 25
        }
        if ($null -eq $raw) { throw [InvalidOperationException]::new('contract-current-row-missing') }
        [void](Invoke-ModuleCode {
            $value = $args[0]
            Assert-VerifierBoundReceiptObservationRawRecord $value.Record $value.Label
        } ([pscustomobject]@{ Record = $raw; Label = $Label }))
        $process = Invoke-ModuleCode {
            Get-VerifierProcessById ([int]$args[0])
        } $ProcessId
        if ($null -eq $process -or $process.GetType() -ne [Diagnostics.Process]) {
            throw [InvalidOperationException]::new('contract-retained-process-missing')
        }
        $process.Refresh()
        if ([bool]$process.HasExited -or [int]$process.Id -ne $ProcessId) {
            throw [InvalidOperationException]::new('contract-process-exited-before-attestation')
        }
        $startTicks = [long](Invoke-ModuleCode {
            Get-VerifierProcessStartTicks $args[0]
        } $process)
        [void](Invoke-ModuleCode {
            Assert-VerifierBoundReceiptObservationMatchesRetainedProcess `
                $args[0].Record ([long]$args[0].StartTicks) $args[0].Label
        } ([pscustomobject]@{ Record = $raw; StartTicks = $startTicks; Label = $Label }))

        $parent = Invoke-ModuleCode {
            Get-VerifierProcessById ([int]$args[0])
        } ([int]$raw.ParentProcessId)
        if ($null -eq $parent -or $parent.GetType() -ne [Diagnostics.Process]) {
            throw [InvalidOperationException]::new('contract-parent-process-missing')
        }
        $parent.Refresh()
        if ([bool]$parent.HasExited -or [int]$parent.Id -ne [int]$raw.ParentProcessId) {
            throw [InvalidOperationException]::new('contract-parent-exited-before-attestation')
        }
        $parentStartTicks = [long](Invoke-ModuleCode {
            Get-VerifierProcessStartTicks $args[0]
        } $parent)
        $record = [pscustomobject]@{
            Process = $process
            ProcessId = [int]$raw.ProcessId
            ProcessStartTicks = $startTicks
            ParentProcessId = [int]$raw.ParentProcessId
            ParentProcessStartTicks = $parentStartTicks
            Name = if ($null -eq $raw.Name) { '' } else { [string]$raw.Name }
            ExecutablePath = if ($null -eq $raw.ExecutablePath) { '' } else { [string]$raw.ExecutablePath }
            CommandLine = if ($null -eq $raw.CommandLine) { '' } else { [string]$raw.CommandLine }
            VerifierDepth = 0
            TestRole = $Label
            TestJobName = $script:CurrentJobName
            TestWorkspaceIdentity = [string]$script:TaskToken
        }
        $attestation = Invoke-ModuleCode {
            $value = $args[0]
            New-VerifierBrowserDrainAttestation $value.Scope $value.Record `
                $value.Process -ObservationOnly
        } ([pscustomobject]@{ Scope = $DrainScope; Record = $record; Process = $process })
        $record | Add-Member -NotePropertyName VerifierDrainAttestation `
            -NotePropertyValue $attestation -ErrorAction Stop
        [void]$script:RetainedProcesses.Add([pscustomobject]@{
            Process = $process
            ProcessId = [int]$ProcessId
            ProcessStartTicks = [long]$startTicks
        })
        return $record
    } catch {
        if ($null -ne $parent) { try { $parent.Dispose() } catch { } }
        if ($null -ne $process -and -not [object]::ReferenceEquals($process, $null)) {
            # The drain scope owns it after attestation. Before that point it is
            # only a task-created temporary Process wrapper.
            try {
                $registered = $false
                if ($null -ne $DrainScope) {
                    $registered = [bool](Invoke-ModuleCode {
                        $value = $args[0]
                        $registration = Get-VerifierBrowserDrainScopeRegistration $value.Scope
                        foreach ($item in @($registration.Item2)) {
                            if ([object]::ReferenceEquals($item, $value.Process)) { return $true }
                        }
                        return $false
                    } ([pscustomobject]@{ Scope = $DrainScope; Process = $process }))
                }
                if (-not $registered) { $process.Dispose() }
            } catch { try { $process.Dispose() } catch { } }
        }
        throw
    } finally {
        if ($null -ne $parent) { try { $parent.Dispose() } catch { } }
    }
}

function Get-TimingCount($Timing, [string]$Name) {
    if (-not $Timing.Counts.Contains($Name)) { return 0L }
    return [long]$Timing.Counts[$Name]
}

function Test-RetainedTaskProcessesExited {
    foreach ($entry in @($script:RetainedProcesses)) {
        try {
            $process = $entry.Process
            if ($null -eq $process -or $process.GetType() -ne [Diagnostics.Process]) {
                return $false
            }
            $process.Refresh()
            if ([int]$process.Id -ne [int]$entry.ProcessId -or
                    -not [bool]$process.HasExited) {
                return $false
            }
            $startTime = $process.StartTime
            if ($null -eq $startTime -or $startTime.GetType() -ne [DateTime] -or
                    [long]$startTime.ToUniversalTime().Ticks -ne
                        [long]$entry.ProcessStartTicks) {
                return $false
            }
        } catch { return $false }
    }
    return $true
}

function New-JobBudget([int]$Milliseconds) {
    return [pscustomobject]@{
        Stopwatch = [Diagnostics.Stopwatch]::StartNew()
        Ticks = [long][Math]::Ceiling(
            ([double][Diagnostics.Stopwatch]::Frequency * $Milliseconds) / 1000.0)
    }
}

function Invoke-NaturalWait($Scope, $RootRecord, $Descendants, $Budget, $Timing) {
    $argsObject = [pscustomobject]@{
        Scope = $Scope
        Root = $RootRecord
        Descendants = @($Descendants)
        Budget = $Budget.Stopwatch
        Ticks = [long]$Budget.Ticks
        Timing = $Timing
    }
    Invoke-ModuleCode {
        $value = $args[0]
        Wait-VerifierBoundReceiptNaturalShutdownDescendants $value.Scope `
            $value.Root $value.Descendants $value.Budget $value.Ticks $value.Timing
    } $argsObject
}

function Invoke-JobEmptyWait($Job, $Budget, $Timing) {
    $argsObject = [pscustomobject]@{
        Job = $Job
        Budget = $Budget.Stopwatch
        Ticks = [long]$Budget.Ticks
        Timing = $Timing
    }
    Invoke-ModuleCode {
        $value = $args[0]
        Wait-VerifierBrowserContainmentJobEmpty $value.Job `
            $value.Budget $value.Ticks $value.Timing
    } $argsObject
}

function New-DrainScope() {
    $scope = Invoke-ModuleCode { New-VerifierBrowserDrainScope } $null
    [void]$script:Scopes.Add($scope)
    return $scope
}

function Wait-ForFile([string]$Path, [int]$Milliseconds, [string]$FailureCode) {
    $watch = [Diagnostics.Stopwatch]::StartNew()
    while ($watch.ElapsedMilliseconds -lt $Milliseconds) {
        if (Test-Path -LiteralPath $Path -PathType Leaf) { return }
        Start-Sleep -Milliseconds 25
    }
    throw [InvalidOperationException]::new($FailureCode)
}

function New-JobName([string]$Suffix) {
    return 'Local\TroubleshootJS.Verifier.BrowserContainment.AO.' +
        $script:TaskToken + '.' + $Suffix
}

try {
    if ([Environment]::OSVersion.Platform -ne [PlatformID]::Win32NT -or
            $PSVersionTable.PSVersion.Major -ne 5 -or
            $PSVersionTable.PSEdition -ne 'Desktop') {
        throw [InvalidOperationException]::new('selected-host-must-be-windows-powershell-5')
    }
    $script:FailureStage = 'module-preflight'
    $root = if ([String]::IsNullOrWhiteSpace($RepositoryRoot)) {
        [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
    } else { [IO.Path]::GetFullPath($RepositoryRoot) }
    $modulePath = Join-Path $root 'scripts/VerifierIsolation.psm1'
    if (-not (Test-Path -LiteralPath $modulePath -PathType Leaf)) {
        throw [InvalidOperationException]::new('verifier-isolation-source-missing')
    }
    $script:Module = Import-Module $modulePath -Force -PassThru -ErrorAction Stop
    $script:PowerShellExe = Join-Path $PSHOME 'powershell.exe'
    $script:WorkingDirectory = [Environment]::GetFolderPath('System')
    if (-not (Test-Path -LiteralPath $script:PowerShellExe -PathType Leaf)) {
        throw [InvalidOperationException]::new('selected-powershell-executable-missing')
    }
    $tempRoot = [IO.Path]::GetFullPath([IO.Path]::GetTempPath())
    $script:TaskToken = [Guid]::NewGuid().ToString('N')
    $script:WorkRoot = Join-Path $tempRoot ('TroubleshootJS/ao-natural-exit-' + $script:TaskToken)
    if (Test-Path -LiteralPath $script:WorkRoot) {
        throw [InvalidOperationException]::new('task-temp-root-already-exists')
    }
    Invoke-ModuleCode { Assert-VerifierNoReparseAncestors ([string]$args[0]) } $script:WorkRoot
    New-Item -ItemType Directory -Path $script:WorkRoot -ErrorAction Stop | Out-Null
    Invoke-ModuleCode {
        Assert-VerifierPhysicalOwnedPath $args[0].Root $args[0].Target `
            -ValidateTree
    } ([pscustomobject]@{ Root = $tempRoot; Target = $script:WorkRoot })

    $script:FailureStage = 'positive-real-contained-tree'
    $rootScript = Join-Path $script:WorkRoot 'staggered-root.ps1'
    $childPidPath = Join-Path $script:WorkRoot 'child.pid'
    $childIdentityPath = Join-Path $script:WorkRoot 'child.identity.json'
    $childExitPath = Join-Path $script:WorkRoot 'child.exit'
    $rootBody = @'
param([Parameter(Mandatory=$true)][string]$ChildPidPath,
    [Parameter(Mandatory=$true)][string]$ChildIdentityPath,
    [Parameter(Mandatory=$true)][string]$ChildExitPath,
    [Parameter(Mandatory=$true)][int]$ChildDelayMs,
    [Parameter(Mandatory=$true)][int]$RootTailMs,
    [Parameter(Mandatory=$true)][string]$JobName,
    [Parameter(Mandatory=$true)][string]$TaskWorkspaceIdentity)
$ErrorActionPreference = 'Stop'
$childCode = 'Start-Sleep -Milliseconds ' + $ChildDelayMs
$encoded = [Convert]::ToBase64String([Text.Encoding]::Unicode.GetBytes($childCode))
$childStart = New-Object Diagnostics.ProcessStartInfo
$childStart.FileName = Join-Path $PSHOME 'powershell.exe'
$childStart.Arguments = '-NoLogo -NoProfile -NonInteractive -EncodedCommand ' + $encoded
$childStart.UseShellExecute = $false
$childStart.CreateNoWindow = $true
$child = [Diagnostics.Process]::Start($childStart)
if ($null -eq $child) { exit 72 }
$parentProcess = [Diagnostics.Process]::GetCurrentProcess()
$childIdentity = [ordered]@{
    processId = [int]$child.Id
    processStartTicks = [long]$child.StartTime.ToUniversalTime().Ticks
    parentProcessId = [int]$PID
    parentProcessStartTicks = [long]$parentProcess.StartTime.ToUniversalTime().Ticks
    executablePath = [string]$childStart.FileName
    commandLine = [string]($childStart.FileName + ' ' + $childStart.Arguments)
    jobName = $JobName
    taskWorkspaceIdentity = $TaskWorkspaceIdentity
}
[IO.File]::WriteAllText($ChildIdentityPath, ($childIdentity | ConvertTo-Json -Compress),
    [Text.UTF8Encoding]::new($false))
$parentProcess.Dispose()
[IO.File]::WriteAllText($ChildPidPath, [string]$child.Id)
$child.WaitForExit()
[IO.File]::WriteAllText($ChildExitPath, [string]$child.ExitCode)
Start-Sleep -Milliseconds $RootTailMs
'@
    [IO.File]::WriteAllText($rootScript, $rootBody, [Text.UTF8Encoding]::new($false))
    $positiveJob = New-TaskJob (New-JobName 'positive')
    $script:CurrentJobName = [string]$positiveJob.Name
    $positiveJobTiming = [ordered]@{ Counts = [ordered]@{} }
    $positiveRootArgs = @('-NoLogo', '-NoProfile', '-NonInteractive',
        '-ExecutionPolicy', 'Bypass', '-File', $rootScript,
        '-ChildPidPath', $childPidPath, '-ChildIdentityPath', $childIdentityPath,
        '-ChildExitPath', $childExitPath, '-ChildDelayMs', '2500', '-RootTailMs', '5000',
        '-JobName', [string]$positiveJob.Name, '-TaskWorkspaceIdentity', $script:TaskToken)
    $positiveRootPid = Start-ContainedPowerShell $positiveJob $positiveRootArgs
    $positiveScope = New-DrainScope
    $positiveRootRecord = New-AttestedProcessRecord $positiveRootPid $positiveScope 'positive root'
    Wait-ForFile $childPidPath 5000 'positive-contained-child-pid-missing'
    $positiveChildPid = 0
    if (-not [int]::TryParse([IO.File]::ReadAllText($childPidPath), [ref]$positiveChildPid) -or
            $positiveChildPid -le 0) {
        throw [InvalidOperationException]::new('positive-contained-child-pid-malformed')
    }
    $positiveMembers = @($positiveJob.GetMemberProcessIds())
    Assert-Contract ($positiveMembers -contains $positiveRootPid -and
        $positiveMembers -contains $positiveChildPid) 'positive-both-processes-in-real-job'
    $positiveChildRecord = New-AttestedProcessRecord $positiveChildPid $positiveScope 'positive child'
    $childLaunchIdentity = [IO.File]::ReadAllText($childIdentityPath) |
        ConvertFrom-Json -ErrorAction Stop
    Assert-Contract ([int]$positiveChildRecord.ParentProcessId -eq [int]$positiveRootRecord.ProcessId -and
        [long]$positiveChildRecord.ParentProcessStartTicks -eq
            [long]$positiveRootRecord.ProcessStartTicks -and
        [int]$childLaunchIdentity.processId -eq [int]$positiveChildRecord.ProcessId -and
        [Math]::Abs([long]$childLaunchIdentity.processStartTicks -
            [long]$positiveChildRecord.ProcessStartTicks) -le 9L -and
        [int]$childLaunchIdentity.parentProcessId -eq [int]$positiveRootRecord.ProcessId -and
        [long]$childLaunchIdentity.parentProcessStartTicks -eq
            [long]$positiveRootRecord.ProcessStartTicks -and
        [string]$childLaunchIdentity.jobName -ceq [string]$positiveJob.Name -and
        [string]$childLaunchIdentity.taskWorkspaceIdentity -ceq $script:TaskToken -and
        [bool](Invoke-ModuleCode {
            $value = $args[0]
            (Test-VerifierCanonicalWindowsPathValue $value.ExpectedPath $value.ActualPath) -and
                (Test-VerifierCommandLineEquivalent $value.ExpectedCommandLine $value.ActualCommandLine)
        } ([pscustomobject]@{
            ExpectedPath = [string]$childLaunchIdentity.executablePath
            ActualPath = [string]$positiveChildRecord.ExecutablePath
            ExpectedCommandLine = [string]$childLaunchIdentity.commandLine
            ActualCommandLine = [string]$positiveChildRecord.CommandLine
        }))) 'positive-child-launch-identity-persisted-at-creation'

    $script:FailureStage = 'negative-attestation-identity'
    $copiedRecord = [pscustomobject]@{}
    foreach ($property in $positiveChildRecord.PSObject.Properties) {
        $copiedRecord | Add-Member -NotePropertyName $property.Name `
            -NotePropertyValue $property.Value -ErrorAction Stop
    }
    $copiedFailed = $false
    try {
        Invoke-ModuleCode {
            $value = $args[0]
            Get-VerifierBrowserDrainNaturalExitResult $value.Scope $value.Record
        } ([pscustomobject]@{ Scope = $positiveScope; Record = $copiedRecord })
    } catch {
        $copiedFailed = $_.Exception.Message -ceq `
            'VERIFIER_INFRASTRUCTURE: Browser drain natural-exit proof carried a copied, foreign, or malformed attestation.'
    }
    Assert-Contract $copiedFailed 'copied-attestation-rejected'
    $originalStartTicks = [long]$positiveChildRecord.ProcessStartTicks
    $wrongIdentityFailed = $false
    try {
        $positiveChildRecord.ProcessStartTicks = $originalStartTicks + 1L
        Invoke-ModuleCode {
            $value = $args[0]
            Get-VerifierBrowserDrainNaturalExitResult $value.Scope $value.Record
        } ([pscustomobject]@{ Scope = $positiveScope; Record = $positiveChildRecord })
    } catch {
        $wrongIdentityFailed = $_.Exception.Message -ceq `
            'VERIFIER_INFRASTRUCTURE: Attested browser descendant record changed its immutable identity after discovery.'
    } finally {
        $positiveChildRecord.ProcessStartTicks = $originalStartTicks
    }
    Assert-Contract $wrongIdentityFailed 'changed-attested-start-identity-rejected'

    $script:FailureStage = 'positive-terminal-proof-reuse'
    $positiveBudget = New-JobBudget 15000
    Invoke-NaturalWait $positiveScope $positiveRootRecord @($positiveChildRecord) `
        $positiveBudget $positiveJobTiming
    $positiveCounts = $positiveJobTiming.Counts
    $naturalCalls = Get-TimingCount $positiveJobTiming 'naturalExitProofCallCount'
    $naturalReuses = Get-TimingCount $positiveJobTiming 'naturalExitProofReuseCount'
    $waitPasses = Get-TimingCount $positiveJobTiming 'waitLoopPassCount'
    $absenceProofs = Get-TimingCount $positiveJobTiming 'currentAbsenceProofCount'
    $absenceObservations = Get-TimingCount $positiveJobTiming 'currentAbsenceObservationCount'
    Assert-Contract ($naturalCalls -gt 2 -and $naturalReuses -gt 0 -and
        $waitPasses -gt 1 -and $naturalCalls + $naturalReuses -eq ($waitPasses * 2)) `
        'terminal-proof-reused-only-after-prior-success'
    Assert-Contract ($absenceProofs -ge 2 -and
        $absenceObservations - $absenceProofs -eq 2) `
        'two-independent-absence-observations-per-terminal-identity'
    Wait-ForFile $childExitPath 3000 'positive-contained-child-exit-status-missing'
    Assert-Contract ([IO.File]::ReadAllText($childExitPath) -ceq '0' -and
        [int]$positiveRootRecord.Process.ExitCode -eq 0) 'positive-natural-helper-exit-codes'
    Invoke-ModuleCode {
        Assert-VerifierBoundReceiptNaturalShutdownNoLateDescendants `
            $args[0].Root @($args[0].Child) $args[0].Timing
    } ([pscustomobject]@{
        Root = $positiveRootRecord
        Child = $positiveChildRecord
        Timing = $positiveJobTiming
    })
    Invoke-JobEmptyWait $positiveJob $positiveBudget $positiveJobTiming
    $positiveProofElapsedMs = [long]$positiveBudget.Stopwatch.ElapsedMilliseconds
    Assert-Contract ((@($positiveJob.GetMemberProcessIds()).Count) -eq 0 -and
        (Get-TimingCount $positiveJobTiming 'containmentJobPollCount') -ge 1) `
        'final-real-kernel-job-empty-proof'

    $script:FailureStage = 'negative-real-live-job-member'
    $blockedJob = New-TaskJob (New-JobName 'blocked')
    $script:CurrentJobName = [string]$blockedJob.Name
    $blockedArgs = @('-NoLogo', '-NoProfile', '-NonInteractive',
        '-ExecutionPolicy', 'Bypass', '-Command', 'Start-Sleep -Milliseconds 5000')
    $blockedPid = Start-ContainedPowerShell $blockedJob $blockedArgs
    $blockedScope = New-DrainScope
    $blockedRecord = New-AttestedProcessRecord $blockedPid $blockedScope 'blocked live member'
    $blockedProcess = $blockedRecord.Process
    $blockedStartTicks = [long]$blockedRecord.ProcessStartTicks
    $blockedBudget = New-JobBudget 250
    $blockedTiming = [ordered]@{ Counts = [ordered]@{} }
    $blockedAtDeadline = $false
    try {
        Invoke-JobEmptyWait $blockedJob $blockedBudget $blockedTiming
    } catch {
        $blockedAtDeadline = $_.Exception.Message -ceq `
            'VERIFIER_INFRASTRUCTURE: Natural browser shutdown exceeded its complete monotonic cleanup deadline.'
    }
    Assert-Contract ($blockedAtDeadline -and
        @($blockedJob.GetMemberProcessIds()) -contains $blockedPid -and
        -not [bool]$blockedProcess.HasExited -and
        [int]$blockedProcess.Id -eq $blockedPid -and $blockedStartTicks -gt 0 -and
        (Get-TimingCount $blockedTiming 'containmentJobPollCount') -ge 1) `
        'live-real-job-member-blocks-empty-proof'
    if (-not $blockedProcess.WaitForExit(8000)) {
        throw [InvalidOperationException]::new('blocked-contract-helper-did-not-exit-naturally')
    }
    Invoke-JobEmptyWait $blockedJob (New-JobBudget 15000) $blockedTiming
    Assert-Contract ((@($blockedJob.GetMemberProcessIds()).Count) -eq 0) `
        'blocked-job-empty-only-after-natural-exit'

    $script:FailureStage = 'negative-transient-absence'
    $transientJob = New-TaskJob (New-JobName 'transient')
    $script:CurrentJobName = [string]$transientJob.Name
    $transientArgs = @('-NoLogo', '-NoProfile', '-NonInteractive',
        '-ExecutionPolicy', 'Bypass', '-Command', 'Start-Sleep -Milliseconds 900')
    $transientPid = Start-ContainedPowerShell $transientJob $transientArgs
    $transientScope = New-DrainScope
    $transientRecord = New-AttestedProcessRecord $transientPid $transientScope 'transient helper'
    if (-not $transientRecord.Process.WaitForExit(5000)) {
        throw [InvalidOperationException]::new('transient-contract-helper-did-not-exit-naturally')
    }
    $transientTiming = [ordered]@{ Counts = [ordered]@{} }
    $transientBudget = New-JobBudget 15000
    $transientPayload = [pscustomobject]@{
        Scope = $transientScope
        Root = $transientRecord
        Budget = $transientBudget.Stopwatch
        Ticks = [long]$transientBudget.Ticks
        Timing = $transientTiming
    }
    Invoke-ModuleCode {
        $value = $args[0]
        $functionPath = 'Function:\Get-VerifierNaturalExitCurrentAbsenceObservation'
        $original = (Get-Item -LiteralPath $functionPath -ErrorAction Stop).ScriptBlock
        if ($null -eq $original) {
            Throw-VerifierInfrastructure 'Natural-exit contract could not capture its scoped transient seam.'
        }
        $script:VerifierNaturalExitReuseContractOriginalAbsence = $original
        $script:VerifierNaturalExitReuseContractTransientInjected = $false
        $replacement = {
            param([int]$ProcessId)
            if (-not $script:VerifierNaturalExitReuseContractTransientInjected) {
                $script:VerifierNaturalExitReuseContractTransientInjected = $true
                return [pscustomobject]@{ Absent = $false; Transient = $true; Current = $null }
            }
            & $script:VerifierNaturalExitReuseContractOriginalAbsence $ProcessId
        }
        Set-Item -LiteralPath $functionPath -Value $replacement -ErrorAction Stop
        try {
            Wait-VerifierBoundReceiptNaturalShutdownDescendants $value.Scope `
                $value.Root @() $value.Budget $value.Ticks $value.Timing
            if (-not $script:VerifierNaturalExitReuseContractTransientInjected) {
                Throw-VerifierInfrastructure 'Natural-exit contract did not exercise its transient absence boundary.'
            }
        } finally {
            Set-Item -LiteralPath $functionPath -Value $original -ErrorAction Stop
            Remove-Variable -Scope Script -Name VerifierNaturalExitReuseContractOriginalAbsence `
                -ErrorAction SilentlyContinue
            Remove-Variable -Scope Script -Name VerifierNaturalExitReuseContractTransientInjected `
                -ErrorAction SilentlyContinue
        }
    } $transientPayload
    $transientProofs = Get-TimingCount $transientTiming 'currentAbsenceProofCount'
    $transientObservations = Get-TimingCount $transientTiming 'currentAbsenceObservationCount'
    Assert-Contract ((Get-TimingCount $transientTiming 'naturalExitProofCallCount') -ge 2 -and
        $transientObservations - $transientProofs -eq 1) `
        'transient-view-is-retried-before-terminal-proof-caching'
    Invoke-JobEmptyWait $transientJob $transientBudget $transientTiming
    Assert-Contract ((@($transientJob.GetMemberProcessIds()).Count) -eq 0) `
        'transient-job-member-eventually-proven-empty'

    $script:FailureStage = 'task-owned-cleanup'
    $taskCleanupWatch = [Diagnostics.Stopwatch]::StartNew()
    Assert-Contract (Test-RetainedTaskProcessesExited) `
        'all-task-process-identities-naturally-exited'
    foreach ($job in @($script:Jobs)) {
        Assert-Contract ((@($job.GetMemberProcessIds()).Count) -eq 0) `
            'all-task-kernel-jobs-empty-before-disposal'
    }
    foreach ($scope in @($script:Scopes)) {
        $disposeErrors = @(Invoke-ModuleCode { Dispose-VerifierBrowserDrainScope $args[0] } $scope)
        Assert-Contract ($disposeErrors.Count -eq 0) 'drain-scope-handle-disposal'
    }
    $script:Scopes.Clear()
    foreach ($job in @($script:Jobs)) {
        if (@($job.GetMemberProcessIds()).Count -ne 0) {
            throw [InvalidOperationException]::new('task-owned-contained-process-remained-before-job-disposal')
        }
        $job.Dispose()
    }
    $script:Jobs.Clear()
    foreach ($entry in @($script:RetainedProcesses)) {
        try { $entry.Process.Dispose() } catch { }
    }
    $script:RetainedProcesses.Clear()
    if (Test-Path -LiteralPath $script:WorkRoot -PathType Container) {
        Invoke-ModuleCode {
            Remove-VerifierOwnedTree $args[0].Root $args[0].Target
        } ([pscustomobject]@{ Root = $tempRoot; Target = $script:WorkRoot })
    }
    $taskCleanupWatch.Stop()
    $script:ContractPassed = $true
    Write-Host ('PASS: Browser terminal natural-exit reuse contracts assertions=' +
        $script:Checks + ' realJobTree=true staggeredNaturalExit=true reuseCount=' +
        $naturalReuses + ' terminalIdentities=2 absenceProofAttempts=' + $absenceProofs +
        ' currentAbsenceObservations=' + $absenceObservations +
        ' positiveProofMs=' + $positiveProofElapsedMs +
        ' taskCleanupMs=' + $taskCleanupWatch.ElapsedMilliseconds +
        ' liveJobMemberBlocked=true transientRetry=true wrongIdentityRejected=true limitsUnchanged=true')
    exit 0
} catch {
    $safe = if ($_.Exception -and $_.Exception.Message -cmatch '^[a-z0-9][a-z0-9._-]{0,120}$') {
        [string]$_.Exception.Message
    } else { 'operation-failed' }
    Write-Host ('FAIL: Browser terminal natural-exit reuse contracts stage=' +
        $script:FailureStage + ' code=' + $safe + ' assertions=' + $script:Checks) `
        -ErrorAction Continue
    Write-Error $_ -ErrorAction Continue
    if (-not [String]::IsNullOrWhiteSpace($script:WorkRoot)) {
        Write-Host ('RETAINED_WORKSPACE: ' + (Split-Path -Leaf $script:WorkRoot))
    }
    exit 2
} finally {
    # Normal successful execution empties every job naturally. On a failure,
    # retain the exact task-owned process handles long enough for the bounded
    # fixtures above to finish; never PID-stop a process from this cleanup.
    foreach ($entry in @($script:RetainedProcesses)) {
        try {
            if (-not [bool]$entry.Process.HasExited) { [void]$entry.Process.WaitForExit(30000) }
        } catch { }
    }
    $allTaskProcessesExited = Test-RetainedTaskProcessesExited
    $allTaskJobsEmpty = $true
    foreach ($job in @($script:Jobs)) {
        try {
            $watch = [Diagnostics.Stopwatch]::StartNew()
            while ($watch.ElapsedMilliseconds -lt 30000 -and
                    @($job.GetMemberProcessIds()).Count -gt 0) {
                Start-Sleep -Milliseconds 25
            }
            if (@($job.GetMemberProcessIds()).Count -ne 0) { $allTaskJobsEmpty = $false }
        } catch { $allTaskJobsEmpty = $false }
    }
    if ($allTaskProcessesExited -and $allTaskJobsEmpty) {
        foreach ($scope in @($script:Scopes)) {
            try { [void](Invoke-ModuleCode { Dispose-VerifierBrowserDrainScope $args[0] } $scope) } catch { }
        }
        foreach ($job in @($script:Jobs)) {
            try { $job.Dispose() } catch { }
        }
        foreach ($entry in @($script:RetainedProcesses)) {
            try { $entry.Process.Dispose() } catch { }
        }
    } else {
        Write-Host ('CLEANUP_NOT_PROVEN: Browser terminal natural-exit contract retained task resources processHandles=' +
            $script:RetainedProcesses.Count + ' jobHandles=' + $script:Jobs.Count +
            ' workspaceRetained=true') -ErrorAction Continue
    }
    if ($script:ContractPassed -and $allTaskProcessesExited -and $allTaskJobsEmpty -and
            -not [String]::IsNullOrWhiteSpace($script:WorkRoot) -and
            (Test-Path -LiteralPath $script:WorkRoot -PathType Container)) {
        try {
            Invoke-ModuleCode {
                Remove-VerifierOwnedTree $args[0].Root $args[0].Target
            } ([pscustomobject]@{ Root = $tempRoot; Target = $script:WorkRoot })
        } catch {
            Write-Host 'CLEANUP_NOT_PROVEN: Browser terminal natural-exit contract workspace retained=true' `
                -ErrorAction Continue
        }
    }
}
