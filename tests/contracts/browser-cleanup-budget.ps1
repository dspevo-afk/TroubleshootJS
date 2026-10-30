[CmdletBinding()]
param(
    [string]$RepositoryRoot = '',
    [string]$BrowserPath = '',
    [switch]$ForceTcpListener,
    [ValidateRange(0, 64)]
    [int]$EvidenceArtifactCount = 0,
    [ValidateRange(1, 2048)]
    [int]$ProfileFileCount = 512
)

# Focused real-host browser resource-cleanup canary. The module factory attaches
# to about:blank; this contract does not exercise application or player behavior.
# Run from Windows PowerShell 5.1 after a production preview artifact exists.
Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$script:Checks = 0
$script:Stage = 'preflight'
$script:Context = $null
$script:BrowserSession = $null
$script:BrowserAttachmentProof = $null
$script:HelperProcess = $null
$script:HelperIdentity = $null
$script:HelperStartTicks = 0L
$script:HelperProfile = ''
$script:HelperAbsenceProven = $false
$script:HelperScript = ''
$script:HelperAuxProfile = ''
$script:JunctionPath = ''
$script:JunctionTarget = ''
$script:JunctionProfile = ''
$script:SentinelRoot = ''
$script:SentinelFile = ''
$script:SentinelValue = ''
$script:NamespaceRoot = ''
$script:ReportPath = ''
$script:CleanupAttempted = $false
$script:CleanupSucceeded = $false
$script:FailureType = ''
$script:FailureStage = ''
$script:ProcessLaunches = New-Object Collections.ArrayList
$script:ProfileFilesExercised = 0
$script:EvidenceArtifactSummary = [ordered]@{
    status = 'NOT_RUN'
    requestedCount = [int]$EvidenceArtifactCount
    count = 0
    bytes = 0L
    registeredCount = 0
}
$script:EvidenceContainmentSummary = $null
$script:ArtifactJunctionPath = ''
$script:ArtifactSentinelRoot = ''
$script:ArtifactSentinelFile = ''
$script:ArtifactSentinelValue = ''
$cleanupResult = $null
$cleanupWatch = $null

function Assert-Contract([bool]$Condition, [string]$Code) {
    if (-not $Condition) { throw [InvalidOperationException]::new($Code) }
    $script:Checks++
}

function Invoke-PrivateProfileRemoval($Context, $Profile) {
    $module = Get-Module VerifierIsolation
    if ($null -eq $module) { throw [InvalidOperationException]::new('Verifier module is unavailable.') }
    $record = [pscustomobject]@{ Profile = [string]$Profile }
    & $module { Remove-VerifierBrowserProfile $args[0] $args[1] } $Context $record
}

function Assert-VerifierPreviewIdentitySchema($Identity) {
    $module = Get-Module VerifierIsolation
    if ($null -eq $module) { throw [InvalidOperationException]::new('Verifier module is unavailable.') }
    & $module { Assert-VerifierPreviewIdentitySchema @args } $Identity
}

function Assert-BrowserSessionAttachment($Session, $Context, $BrowserIdentity,
        [string]$BrowserPath) {
    Assert-Contract ($null -ne $Session -and $null -ne $Session.Record -and
        $null -ne $Session.Browser -and $null -ne $Session.Socket) 'browser-factory-result'
    $record = $Session.Record
    [void](Assert-VerifierDurableBrowserSession $record $Context `
        'browser cleanup contract attached session')
    Assert-Contract ($Session.Browser -is [Diagnostics.Process] -and
        -not [bool]$Session.Browser.HasExited -and
        [int]$Session.Browser.Id -eq [int]$record.ProcessId -and
        [int]$record.ProcessId -eq [int]$BrowserIdentity.Record.ProcessId -and
        [long]$record.ProcessStartTicks -eq [long]$BrowserIdentity.Record.ProcessStartTicks -and
        (Test-VerifierCanonicalWindowsPathValue `
            ([string]$BrowserIdentity.Record.ExecutablePath) $BrowserPath)) `
        'browser-factory-process-identity'
    Assert-Contract (Test-VerifierProcessIdentity $record $null $BrowserPath) `
        'browser-live-profile-port-command-identity'
    Assert-Contract ([IO.Path]::GetFileName([string]$record.BrowserPath) -ieq
        [IO.Path]::GetFileName($BrowserPath) -and
        (Test-VerifierCanonicalWindowsPathValue ([string]$Session.Profile) `
            ([string]$record.Profile)) -and
        (Test-VerifierCanonicalWindowsPathValue ([string]$record.Profile) `
            ([string]$record.Lease.ProfilePath)) -and
        [int]$Session.CdpPort -gt 0 -and
        [int]$Session.CdpPort -eq [int]$record.CdpPort -and
        [int]$record.CdpPort -eq [int]$record.Lease.Port -and
        [string]$Session.RouteId -ceq [string]$record.RouteId) `
        'browser-factory-profile-port-route-binding'
    Assert-Contract ($record.Status -ceq 'attached' -and
        $record.CleanupResult -ceq 'pending' -and
        $record.ExpectedUrl -ceq 'about:blank' -and
        -not [String]::IsNullOrWhiteSpace([string]$record.TargetId) -and
        $record.RecoveryReceipt.Protocol -ceq 'troubleshootjs-verifier-browser-recovery-v1' -and
        $record.RecoveryReceipt.State -ceq 'bound') 'browser-factory-target-receipt-attachment'
    Assert-Contract ($Session.Socket -is [Net.WebSockets.ClientWebSocket] -and
        $Session.Socket.State -eq [Net.WebSockets.WebSocketState]::Open -and
        [object]::ReferenceEquals($Session.Socket, $record.Runtime.Socket)) `
        'browser-factory-open-attached-socket'
    return [ordered]@{
        processId = [int]$record.ProcessId
        processStartTicks = [long]$record.ProcessStartTicks
        executable = [IO.Path]::GetFileName([string]$BrowserIdentity.Record.ExecutablePath)
        processIdentityVerified = $true
        profileIdentity = Get-VerifierOpaquePathIdentity 'browser-profile' $record.Profile
        profileCommandLineVerified = $true
        cdpPort = [int]$record.CdpPort
        portBoundToLease = $true
        targetAttached = $true
        requestedUrl = [string]$record.ExpectedUrl
        recoveryReceiptProtocol = [string]$record.RecoveryReceipt.Protocol
        recoveryReceiptState = [string]$record.RecoveryReceipt.State
        recoveryReceiptBoundToSession = $true
        socketOpen = $true
        socketBoundToRecord = $true
    }
}

function Get-CleanupPhaseTimings($CleanupResult) {
    if ($null -eq $CleanupResult -or
            -not $CleanupResult.PSObject.Properties['ExtraPhaseTimings'] -or
            $null -eq $CleanupResult.ExtraPhaseTimings) {
        throw [InvalidOperationException]::new('Cleanup phase timings were not returned.')
    }
    $values = [ordered]@{}
    foreach ($name in @('PreflightMs', 'PhysicalNamespaceMs', 'BrowserSessionMs',
            'PreviewMs', 'LeaseReleaseMs', 'FinalProofMs')) {
        $property = $CleanupResult.ExtraPhaseTimings.PSObject.Properties[$name]
        if ($null -eq $property -or
                -not (Test-VerifierStrictIntegralValue $property.Value 0L ([long]::MaxValue))) {
            throw [InvalidOperationException]::new('Cleanup phase timing was missing or malformed.')
        }
        $values[$name] = [long]$property.Value
    }
    $shutdown = @($CleanupResult.ExtraPhaseTimings.BrowserShutdown)
    Assert-Contract ($shutdown.Count -eq 1 -and
        $shutdown[0].Lane -ceq 'boundReceipt' -and
        $shutdown[0].FailurePhase -ceq '' -and
        (Test-VerifierStrictIntegralValue $shutdown[0].TotalMs 0L 15000L) -and
        $null -ne $shutdown[0].PhasesMs) 'natural-shutdown-timing-proof'
    $values['BrowserShutdown'] = $shutdown
    return $values
}

function Assert-TerminalLease($Lease) {
    Assert-Contract ($Lease.Status -ceq 'released' -and
        $Lease.ClaimState -ceq 'released' -and
        $Lease.ReleaseState -ceq 'complete' -and
        $Lease.ReleaseJournalState -ceq 'complete' -and
        $Lease.MutexReleased -is [bool] -and $Lease.MutexReleased -and
        $null -eq $Lease.ClaimMutex -and
        $Lease.ListenerInspectionSuccess -is [bool] -and $Lease.ListenerInspectionSuccess -and
        $Lease.ListenerInspectionKnown -is [bool] -and $Lease.ListenerInspectionKnown -and
        $Lease.ListenerHasListeners -is [bool] -and -not $Lease.ListenerHasListeners -and
        $Lease.ListenerAbsent -is [bool] -and $Lease.ListenerAbsent) 'terminal-lease-proof'
    Assert-Contract (-not (Test-Path -LiteralPath $Lease.Path -ErrorAction Stop)) 'claim-absence'
    Assert-Contract ($Lease.ProcessProofRequired -is [bool] -and $Lease.ProcessProofRequired -and
        $Lease.ProcessTerminationProven -is [bool] -and
            $Lease.ProcessTerminationProven -and $Lease.ProcessAbsent -is [bool] -and
            $Lease.ProcessAbsent) 'lease-process-absence-proof'
    Assert-Contract (-not $Lease.ReleaseBlocked -and $Lease.ReleaseBlockReason -ceq '') 'lease-release-unblocked'
}

function Add-LaunchRecord([string]$Role, $Identity, [string]$ProfileRole,
        [int]$Port, [bool]$Hidden) {
    $record = $Identity.Record
    Assert-Contract ($null -ne $Identity.Process -and
        $record.ProcessId -gt 0 -and $record.ProcessStartTicks -gt 0 -and
        $record.ParentProcessId -gt 0 -and $record.ParentProcessStartTicks -gt 0 -and
        -not [String]::IsNullOrWhiteSpace([string]$record.ExecutablePath)) 'launch-identity'
    [void]$script:ProcessLaunches.Add([ordered]@{
        role = $Role
        pid = [int]$record.ProcessId
        processStartTicks = [long]$record.ProcessStartTicks
        executable = [IO.Path]::GetFileName([string]$record.ExecutablePath)
        profileRole = $ProfileRole
        port = $Port
        hidden = $Hidden
    })
}

function New-ProfileWalkFixture([string]$Profile, [int]$Count) {
    $fixtureRoot = Join-Path $Profile 'task-cleanup-walk'
    New-Item -ItemType Directory -Path $fixtureRoot -ErrorAction Stop | Out-Null
    $encoding = [Text.UTF8Encoding]::new($false)
    for ($index = 0; $index -lt $Count; $index++) {
        $bucket = Join-Path $fixtureRoot ('bucket-{0:D2}' -f ($index % 16))
        if (-not (Test-Path -LiteralPath $bucket -PathType Container)) {
            New-Item -ItemType Directory -Path $bucket -ErrorAction Stop | Out-Null
        }
        $file = Join-Path $bucket ('entry-{0:D4}.txt' -f $index)
        [IO.File]::WriteAllText($file, ('task-owned profile file {0}' -f $index), $encoding)
        $script:ProfileFilesExercised = $index + 1
    }
    $observed = @(Get-ChildItem -LiteralPath $fixtureRoot -File -Recurse -Force -ErrorAction Stop)
    Assert-Contract ($observed.Count -eq $Count) 'profile-walk-fixture-count'
    return $Count
}

function Register-TaskEvidenceFixtures($Context, [int]$Count) {
    $summary = [ordered]@{
        status = 'PENDING'
        requestedCount = $Count
        count = 0
        bytes = 0L
        registeredCount = 0
    }
    $script:EvidenceArtifactSummary = $summary
    [void](Assert-VerifierPhysicalOwnedPath $Context.EvidenceNamespaceRoot `
        $Context.EvidenceDirectory -ValidateTree)
    $startingArtifactCount = @($Context.Artifacts).Count
    $encoding = [Text.UTF8Encoding]::new($false)
    for ($index = 0; $index -lt $Count; $index++) {
        $artifactPath = Join-Path $Context.EvidenceDirectory `
            ('browser-cleanup-artifact-{0:D3}.json' -f $index)
        [void](Assert-VerifierPhysicalOwnedPath $Context.EvidenceDirectory $artifactPath)
        Assert-Contract (-not (Test-Path -LiteralPath $artifactPath -ErrorAction Stop)) `
            'evidence-artifact-path-unique'
        $artifactRecord = [ordered]@{
            protocol = 'troubleshootjs-browser-cleanup-evidence-v1'
            index = $index
            payload = ('task-owned cleanup evidence {0:D3}' -f $index)
        }
        $json = $artifactRecord | ConvertTo-Json -Compress -Depth 4
        $expectedBytes = [long]$encoding.GetByteCount($json)
        [IO.File]::WriteAllText($artifactPath, $json, $encoding)
        $item = Get-Item -LiteralPath $artifactPath -Force -ErrorAction Stop
        $summary['count']++
        $summary['bytes'] += [long]$item.Length
        $readback = [IO.File]::ReadAllText($artifactPath)
        [void]($readback | ConvertFrom-Json -ErrorAction Stop)
        Assert-Contract ($item.Length -eq $expectedBytes -and $readback -ceq $json) `
            'evidence-artifact-write-proof'

        Register-VerifierEvidenceArtifact $Context $artifactPath
        Assert-Contract (@($Context.Artifacts).Count -eq `
            ($startingArtifactCount + $index + 1) -and
            ($Context.Artifacts -contains [IO.Path]::GetFullPath($artifactPath))) `
            'evidence-artifact-registered'
        $summary['registeredCount']++
    }
    Assert-Contract ($summary['count'] -eq $Count -and
        $summary['registeredCount'] -eq $Count -and
        (@($Context.Artifacts).Count - $startingArtifactCount) -eq $Count) `
        'evidence-artifact-total-count'
    $summary.status = 'PASS'
    return $summary
}

function Invoke-EvidenceArtifactReparseCanary($Context) {
    $watch = [Diagnostics.Stopwatch]::StartNew()
    $outsideRoot = Join-Path $Context.RunRoot 'evidence-outside-sentinel'
    $junctionPath = Join-Path $Context.EvidenceDirectory 'task-owned-outside-link'
    $sentinelFile = Join-Path $outsideRoot 'sentinel.json'
    $sentinelValue = 'task-owned-evidence-sentinel-v1'
    $script:ArtifactSentinelRoot = $outsideRoot
    $script:ArtifactSentinelFile = $sentinelFile
    $script:ArtifactSentinelValue = $sentinelValue
    $script:ArtifactJunctionPath = $junctionPath
    $beforeArtifactCount = @($Context.Artifacts).Count

    Assert-Contract (-not (Test-Path -LiteralPath $outsideRoot -ErrorAction Stop) -and
        -not (Test-Path -LiteralPath $junctionPath -ErrorAction Stop)) `
        'evidence-canary-paths-unique'
    New-Item -ItemType Directory -Path $outsideRoot -ErrorAction Stop | Out-Null
    [void](Assert-VerifierPhysicalOwnedPath $Context.RunRoot $outsideRoot -ValidateTree)
    Assert-Contract (-not (Test-VerifierPhysicalChildPath `
        $Context.EvidenceDirectory $outsideRoot)) 'evidence-sentinel-outside-owned-directory'
    [IO.File]::WriteAllText($sentinelFile, $sentinelValue,
        ([Text.UTF8Encoding]::new($false)))
    [void](Assert-VerifierPhysicalOwnedPath $Context.RunRoot $outsideRoot -ValidateTree)
    New-Item -ItemType Junction -Path $junctionPath -Target $outsideRoot `
        -ErrorAction Stop | Out-Null

    $link = Get-Item -LiteralPath $junctionPath -Force -ErrorAction Stop
    Assert-Contract ([IO.Path]::GetFullPath([string]$link.FullName).Equals(
        [IO.Path]::GetFullPath($junctionPath), [StringComparison]::OrdinalIgnoreCase) -and
        (($link.Attributes -band [IO.FileAttributes]::ReparsePoint) -ne 0)) `
        'evidence-canary-exact-junction'
    if ($link.PSObject.Properties['Target'] -and $null -ne $link.Target) {
        $targets = @($link.Target | ForEach-Object { [IO.Path]::GetFullPath([string]$_) })
        Assert-Contract ($targets.Count -eq 1 -and $targets[0].Equals(
            [IO.Path]::GetFullPath($outsideRoot), [StringComparison]::OrdinalIgnoreCase)) `
            'evidence-canary-junction-target'
    }

    $rejectedPath = Join-Path $junctionPath 'sentinel.json'
    $reparseRejected = $false
    try {
        Register-VerifierEvidenceArtifact $Context $rejectedPath
    } catch {
        $reparseRejected = $_.Exception.Message -match '(?i)reparse point'
    }
    Assert-Contract $reparseRejected 'evidence-reparse-path-rejected'
    Assert-Contract (@($Context.Artifacts).Count -eq $beforeArtifactCount -and
        -not ($Context.Artifacts -contains [IO.Path]::GetFullPath($rejectedPath))) `
        'evidence-reparse-path-not-registered'
    Assert-Contract ([IO.File]::ReadAllText($sentinelFile) -ceq $sentinelValue) `
        'evidence-reparse-sentinel-preserved'
    Remove-VerifiedExactJunction $junctionPath $outsideRoot $sentinelFile `
        $sentinelValue $Context.EvidenceDirectory
    $script:ArtifactJunctionPath = ''
    Assert-Contract ([IO.File]::ReadAllText($sentinelFile) -ceq $sentinelValue) `
        'evidence-reparse-sentinel-after-link-removal'
    Remove-VerifierOwnedTree $Context.RunRoot $outsideRoot
    $script:ArtifactSentinelRoot = ''
    $script:ArtifactSentinelFile = ''
    $script:ArtifactSentinelValue = ''
    $watch.Stop()
    $summary = [ordered]@{
        status = 'PASS'
        rejectedLexicallyOwnedReparsePath = $true
        artifactLedgerUnchanged = $true
        sentinelPreserved = $true
        junctionRemovedNonrecursively = $true
        elapsedMs = [long]$watch.ElapsedMilliseconds
    }
    $script:EvidenceContainmentSummary = $summary
    return $summary
}

function Start-TaskProfileHelper([string]$CscriptPath, [string]$HelperPath,
        [string]$Profile, [string]$WorkingDirectory) {
    $arguments = @('//B', '//NoLogo', $HelperPath, ('--user-data-dir=' + $Profile))
    $argumentLine = ConvertTo-VerifierArgumentString $arguments
    $process = Start-Process -FilePath $CscriptPath -ArgumentList $argumentLine `
        -WorkingDirectory $WorkingDirectory -PassThru -WindowStyle Hidden -ErrorAction Stop
    $script:HelperProcess = $process
    $script:HelperProfile = $Profile
    $script:HelperScript = $HelperPath
    $startTicks = Get-VerifierLaunchedProcessStartTicks $process
    $script:HelperStartTicks = [long]$startTicks
    $identity = Get-VerifierCurrentProcessIdentity ([int]$process.Id) $startTicks 0 '' '' 0 '' '' 0
    $record = $identity.Record
    Assert-Contract ([IO.Path]::GetFileName([string]$record.ExecutablePath) -ieq 'cscript.exe' -and
        (Test-VerifierCommandLineCanonicalPathToken $record.CommandLine $HelperPath) -and
        (Test-VerifierCommandLineSwitch $record.CommandLine '--user-data-dir' $Profile)) 'helper-launch-identity'
    $helper = [pscustomobject]@{ Process = $identity.Process; Record = $record; Hidden = $true }
    $script:HelperIdentity = $helper
    return $helper
}

function Stop-TaskProfileHelper($Helper, [string]$Profile,
        [string]$HelperPath) {
    if ($null -eq $Helper -or $null -eq $Helper.Record) { return $null }
    $record = $Helper.Record
    $fresh = Get-VerifierCurrentProcessIdentity ([int]$record.ProcessId) `
        ([long]$record.ProcessStartTicks) ([int]$record.ParentProcessId) `
        ([string]$record.CommandLine) '' 0 '' '' ([long]$record.ParentProcessStartTicks)
    Assert-Contract ([IO.Path]::GetFileName([string]$fresh.Record.ExecutablePath) -ieq 'cscript.exe' -and
        (Test-VerifierCommandLineCanonicalPathToken $fresh.Record.CommandLine $HelperPath) -and
        (Test-VerifierCommandLineSwitch $fresh.Record.CommandLine '--user-data-dir' $Profile)) 'helper-pretermination-identity'
    $stopped = Stop-VerifierVerifiedProcessExactly $fresh.Process `
        ([long]$record.ProcessStartTicks) 5000 $fresh.Record
    Assert-Contract ($stopped.TerminationProven -is [bool] -and $stopped.TerminationProven) 'helper-termination-proof'
    $absence = Confirm-VerifierRecordedProcessAbsent $fresh.Record `
        'task-owned profile helper' ([string]$fresh.Record.CommandLine) 0 '' '' '' $Profile
    Assert-Contract ($absence.QueryProven -is [bool] -and $absence.QueryProven -and
        $absence.Absent -is [bool] -and $absence.Absent -and -not $absence.Replaced) 'helper-absence-proof'
    return [pscustomobject]@{
        pid = [int]$record.ProcessId
        processStartTicks = [long]$record.ProcessStartTicks
        executable = [IO.Path]::GetFileName([string]$record.ExecutablePath)
        terminationProven = [bool]$stopped.TerminationProven
        absenceProven = [bool]$absence.Absent
    }
}

function Remove-VerifiedExactJunction([string]$Junction, [string]$Target,
        [string]$SentinelPath, [string]$ExpectedSentinel,
        [string]$ExpectedParent = '') {
    $expectedJunction = [IO.Path]::GetFullPath($Junction)
    $expectedTarget = [IO.Path]::GetFullPath($Target)
    $expectedParentPath = if ([String]::IsNullOrWhiteSpace($ExpectedParent)) {
        [IO.Path]::GetFullPath((Join-Path $script:Context.RunRoot 'aux-junction-profile'))
    } else { [IO.Path]::GetFullPath($ExpectedParent) }
    Assert-Contract ([IO.Path]::GetFullPath((Split-Path -Parent $expectedJunction)).Equals(
        $expectedParentPath, [StringComparison]::OrdinalIgnoreCase)) 'exact-junction-parent'
    Assert-VerifierNoReparseAncestors $expectedParentPath
    $item = Get-Item -LiteralPath $expectedJunction -Force -ErrorAction Stop
    Assert-Contract ([IO.Path]::GetFullPath([string]$item.FullName).Equals(
        $expectedJunction, [StringComparison]::OrdinalIgnoreCase) -and
        (($item.Attributes -band [IO.FileAttributes]::ReparsePoint) -ne 0)) 'exact-junction-identity'
    if ($item.PSObject.Properties['LinkType'] -and
            -not [String]::IsNullOrWhiteSpace([string]$item.LinkType)) {
        Assert-Contract ([string]$item.LinkType -ieq 'Junction') 'junction-type'
    }
    if ($item.PSObject.Properties['Target'] -and $null -ne $item.Target) {
        $targets = @($item.Target | ForEach-Object { [IO.Path]::GetFullPath([string]$_) })
        Assert-Contract ($targets.Count -eq 1 -and $targets[0].Equals(
            $expectedTarget, [StringComparison]::OrdinalIgnoreCase)) 'junction-target-identity'
    }
    $observed = [IO.File]::ReadAllText($SentinelPath)
    Assert-Contract ($observed -ceq $ExpectedSentinel) 'junction-sentinel-identity'
    [IO.Directory]::Delete($expectedJunction, $false)
    Assert-Contract (-not (Test-Path -LiteralPath $expectedJunction -ErrorAction Stop)) 'junction-nonrecursive-removal'
}

function Get-OutputRecord($Context, $CleanupResult, [long]$CleanupElapsedMs,
        $HelperSummary, $JunctionSummary, [int]$FileCount,
        [long]$SetupElapsedMs, [bool]$RequestedForceTcpListener) {
    $phaseTimings = Get-CleanupPhaseTimings $CleanupResult
    $leases = @($Context.LeaseRecords)
    Assert-Contract ($Context.CleanupState -ceq 'complete' -and
        @($Context.CleanupErrors).Count -eq 0 -and $leases.Count -eq 2) 'run-cleanup-state'
    Assert-Contract ($Context.Server.CleanupResult -ceq 'complete' -and
        $Context.Server.ProcessTerminationProven -is [bool] -and
        $Context.Server.ProcessTerminationProven -and $Context.Server.ProcessAbsent -is [bool] -and
        $Context.Server.ProcessAbsent -and $Context.Server.ListenerInspectionProven -is [bool] -and
        $Context.Server.ListenerInspectionProven -and $Context.Server.ListenerAbsent -is [bool] -and
        $Context.Server.ListenerAbsent) 'preview-owner-cleanup-proof'
    foreach ($lease in $leases) { Assert-TerminalLease $lease }

    Assert-Contract ($null -ne $script:BrowserAttachmentProof) 'browser-attachment-proof-present'
    $sessionRecord = $script:BrowserSession.Record
    Assert-Contract (Test-VerifierCanonicalWindowsPathValue `
        ([string]$sessionRecord.Profile) ([string]$sessionRecord.Lease.ProfilePath)) 'exact-browser-profile-owner'
    Assert-Contract ($sessionRecord.CleanupResult -ceq 'complete' -and
        $sessionRecord.Status -ceq 'cleaned' -and
        $sessionRecord.ProfileProcessScanCompleted -is [bool] -and
        $sessionRecord.ProfileProcessScanCompleted -and
        $sessionRecord.ProfileInspectionFailed -is [bool] -and
        -not $sessionRecord.ProfileInspectionFailed -and
        -not (Test-Path -LiteralPath $sessionRecord.Profile -ErrorAction Stop)) 'browser-profile-cleanup-proof'
    Assert-Contract ($CleanupResult.Success -is [bool] -and $CleanupResult.Success -and
        $CleanupElapsedMs -ge 0 -and $CleanupElapsedMs -le 120000) 'complete-run-budget'
    Assert-Contract (@($Context.Artifacts).Count -eq
        [int]$script:EvidenceArtifactSummary['registeredCount']) `
        'evidence-artifact-final-ledger-count'

    $previewAbsence = Confirm-VerifierRecordedProcessAbsent $Context.Server `
        'owned preview' ([string]$Context.Server.ProcessCommandLine) `
        ([int]$Context.Server.Port) ([string]$Context.Server.Script) `
        ([string]$Context.RunId) ([string]$Context.PreviewNonce)
    $browserAbsence = Confirm-VerifierRecordedProcessAbsent $sessionRecord `
        'owned browser' ([string]$sessionRecord.ProcessCommandLine) `
        ([int]$sessionRecord.CdpPort) '' ([string]$Context.RunId) '' `
        ([string]$sessionRecord.Profile)
    Assert-Contract ($previewAbsence.QueryProven -and $previewAbsence.Absent -and
        -not $previewAbsence.Replaced -and $browserAbsence.QueryProven -and
        $browserAbsence.Absent -and -not $browserAbsence.Replaced) 'owner-process-absence'
    foreach ($lease in $leases) {
        $listener = Get-VerifierLoopbackListenerRecords ([int]$lease.Port)
        Assert-Contract ($listener.Success -and $listener.Known -and
            -not $listener.HasListeners) 'post-cleanup-listener-absence'
    }
    [void](Assert-VerifierBrowserProfileIsQuiescent ([string]$sessionRecord.Profile) `
        ([string]$sessionRecord.BrowserPath) ([string]$Context.RunId) `
        ([string]$Context.RepositoryIdentity) ([int]$sessionRecord.CdpPort))

    return [ordered]@{
        protocol = 'troubleshootjs-browser-cleanup-budget-v1'
        status = 'PASS'
        scope = 'about-blank-browser-resource-cleanup'
        host = [ordered]@{
            powershell = [string]$PSVersionTable.PSVersion
            edition = [string]$PSVersionTable.PSEdition
        }
        checks = $script:Checks
        setupElapsedMs = $SetupElapsedMs
        cleanupElapsedMs = $CleanupElapsedMs
        cleanupBudgetMs = 120000
        extraPhaseTimings = $phaseTimings
        profileFilesExercised = $FileCount
        evidenceArtifactCount = [int]$script:EvidenceArtifactSummary['count']
        evidenceArtifactBytes = [long]$script:EvidenceArtifactSummary['bytes']
        evidenceArtifacts = $script:EvidenceArtifactSummary
        evidenceContainmentCanary = $script:EvidenceContainmentSummary
        previewTransport = [ordered]@{
            requestedForceTcpListener = $RequestedForceTcpListener
            observedListenerOwnerKind = [string]$Context.Server.Lease.ListenerOwnerKind
        }
        browserAttachmentProof = $script:BrowserAttachmentProof
        processLaunches = @($script:ProcessLaunches.ToArray())
        helperQuiescenceCanary = $HelperSummary
        junctionDeletionCanary = $JunctionSummary
        limitations = @(
            'Scope is resource cleanup for a factory-attached about:blank browser session; no application or player behavior is exercised.',
            'The helper canary proves the real profile-quiescence boundary; it does not claim a full-run lease-release failure/retry.',
            'The listener-identity contract is exercised by its separate maintained contract.'
        )
        ownerProof = [ordered]@{
            previewProcessAbsent = [bool]$previewAbsence.Absent
            browserProcessAbsent = [bool]$browserAbsence.Absent
            previewClaimAbsent = -not (Test-Path -LiteralPath $Context.Server.Lease.Path)
            browserClaimAbsent = -not (Test-Path -LiteralPath $sessionRecord.Lease.Path)
            previewListenerAbsent = [bool]$Context.Server.Lease.ListenerAbsent
            browserListenerAbsent = [bool]$sessionRecord.Lease.ListenerAbsent
            browserProfileAbsent = -not (Test-Path -LiteralPath $sessionRecord.Profile)
            browserProfileIdentity = Get-VerifierOpaquePathIdentity 'browser-profile' $sessionRecord.Profile
            previewClaimIdentity = Get-VerifierOpaquePathIdentity 'port-claim' $Context.Server.Lease.Path
            browserClaimIdentity = Get-VerifierOpaquePathIdentity 'port-claim' $sessionRecord.Lease.Path
        }
    }
}

function Write-TaskReport($Report) {
    if ([String]::IsNullOrWhiteSpace($script:ReportPath) -or
            [String]::IsNullOrWhiteSpace($script:NamespaceRoot)) { return $false }
    try {
        [void](Assert-VerifierPhysicalOwnedPath $script:NamespaceRoot $script:ReportPath)
        $json = $Report | ConvertTo-Json -Depth 10
        [IO.File]::WriteAllText($script:ReportPath, $json,
            (New-Object Text.UTF8Encoding($false)))
        if (-not (Test-Path -LiteralPath $script:ReportPath -PathType Leaf -ErrorAction Stop)) {
            return $false
        }
        $item = Get-Item -LiteralPath $script:ReportPath -Force -ErrorAction Stop
        $readBack = [IO.File]::ReadAllText($script:ReportPath)
        if ($item.Length -le 0 -or $readBack -cne $json) { return $false }
        [void]($readBack | ConvertFrom-Json -ErrorAction Stop)
        return $true
    } catch {
        return $false
    }
}

function Get-SafeFailureMessage($ErrorRecord) {
    $message = if ($null -ne $ErrorRecord -and $ErrorRecord.Exception) {
        [string]$ErrorRecord.Exception.Message
    } else { '' }
    if ([String]::IsNullOrWhiteSpace($message)) { return 'operation-failed' }
    # Contract assertion messages are fixed machine-independent codes. Keep those
    # and a small set of fixed protocol errors; never serialize arbitrary runtime
    # exception text that could contain a user or task absolute path.
    if ($message -cmatch '^[a-z0-9][a-z0-9._-]{0,120}$') { return $message }
    $safeMessages = @(
        'CDP deadline expired.',
        'CDP socket closed before its response.',
        'CDP command returned an error.',
        'CDP response deadline expired.',
        'CDP evaluation did not return a value.',
        'Verifier module is unavailable.',
        'Cleanup phase timings were not returned.',
        'Cleanup phase timing was missing or malformed.'
    )
    if ($safeMessages -ccontains $message) { return $message }
    return 'operation-failed'
}

try {
    if ([Environment]::OSVersion.Platform -ne [PlatformID]::Win32NT -or
            $PSVersionTable.PSVersion.Major -ne 5 -or
            $PSVersionTable.PSEdition -ne 'Desktop') {
        throw [InvalidOperationException]::new('selected-host-must-be-windows-powershell-5')
    }
    $script:Stage = 'repository'
    $root = if ([String]::IsNullOrWhiteSpace($RepositoryRoot)) {
        [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
    } else { [IO.Path]::GetFullPath($RepositoryRoot) }
    $modulePath = Join-Path $root 'scripts/VerifierIsolation.psm1'
    $previewPath = Join-Path $root 'scripts/preview.ps1'
    $requiredPreviewArtifact = Join-Path $root 'war/circuitjs1/circuitjs1.nocache.js'
    if (-not (Test-Path -LiteralPath $modulePath -PathType Leaf) -or
            -not (Test-Path -LiteralPath $previewPath -PathType Leaf)) {
        throw [InvalidOperationException]::new('verifier-preview-source-missing')
    }
    if (-not (Test-Path -LiteralPath $requiredPreviewArtifact -PathType Leaf)) {
        throw [InvalidOperationException]::new('existing-production-preview-artifact-required-no-build-performed')
    }
    Import-Module $modulePath -Force -ErrorAction Stop
    $browser = Resolve-VerifierBrowserPath $BrowserPath
    if ([String]::IsNullOrWhiteSpace($browser) -or
            -not (Test-Path -LiteralPath $browser -PathType Leaf)) {
        throw [InvalidOperationException]::new('selected-real-browser-unavailable')
    }
    $cscript = Join-Path $env:WINDIR 'System32/cscript.exe'
    if (-not (Test-Path -LiteralPath $cscript -PathType Leaf)) {
        throw [InvalidOperationException]::new('windows-script-host-unavailable')
    }

    $tempRoot = [IO.Path]::GetFullPath([IO.Path]::GetTempPath())
    $verifyRoot = Join-Path $tempRoot 'TroubleshootJS/verify'
    $taskId = [Guid]::NewGuid().ToString('N')
    $script:NamespaceRoot = Join-Path $verifyRoot ('browser-cleanup-budget-' + $taskId)
    $script:ReportPath = Join-Path $script:NamespaceRoot 'browser-cleanup-budget-report.json'
    Assert-Contract (-not (Test-VerifierChildPath $root $script:NamespaceRoot)) 'temp-root-outside-repository'
    $setup = [Diagnostics.Stopwatch]::StartNew()
    $script:Context = New-VerifierRunContext $root '' $script:NamespaceRoot

    $script:Stage = 'owned-preview'
    $baseUrl = [string](Start-VerifierOwnedPreview $script:Context $previewPath 60 `
        ([bool]$ForceTcpListener))
    $identityResponse = Invoke-RestMethod -UseBasicParsing `
        -Uri ($baseUrl + '/__tsj/verify-identity') -TimeoutSec 10
    Assert-VerifierPreviewIdentitySchema $identityResponse
    Assert-Contract ($identityResponse.verifierRunId -ceq $script:Context.RunId -and
        $identityResponse.verifierNonce -ceq $script:Context.PreviewNonce -and
        [int]$identityResponse.previewPort -eq [int]$script:Context.Server.Port) 'owned-preview-identity'
    $previewIdentity = Get-VerifierCurrentProcessIdentity `
        ([int]$script:Context.Server.ProcessId) ([long]$script:Context.Server.ProcessStartTicks) `
        ([int]$script:Context.Server.ProcessParentProcessId) `
        ([string]$script:Context.Server.ProcessCommandLine) `
        ([string]$script:Context.Server.Script) ([int]$script:Context.Server.Port) `
        ([string]$script:Context.RunId) ([string]$script:Context.PreviewNonce) `
        ([long]$script:Context.Server.ProcessParentProcessStartTicks)
    Assert-Contract ([bool]$script:Context.Server.Process.StartInfo.CreateNoWindow) 'preview-hidden-launch'
    Add-LaunchRecord 'owned-preview' $previewIdentity 'none' `
        ([int]$script:Context.Server.Port) $true

    $script:Stage = 'helper-quiescence-canary'
    $helperWatch = [Diagnostics.Stopwatch]::StartNew()
    $auxProfile = Join-Path $script:Context.RunRoot 'aux-helper-profile'
    $script:HelperAuxProfile = $auxProfile
    New-Item -ItemType Directory -Path $auxProfile -ErrorAction Stop | Out-Null
    [void](Assert-VerifierPhysicalOwnedPath $script:Context.RunRoot $auxProfile -ValidateTree)
    $script:HelperScript = Join-Path $script:NamespaceRoot ('profile-helper-' + $taskId + '.vbs')
    $vbscript = "If WScript.Arguments.Count <> 1 Then WScript.Quit 41`r`nDo`r`n  WScript.Sleep 1000`r`nLoop`r`n"
    [IO.File]::WriteAllText($script:HelperScript, $vbscript,
        (New-Object Text.ASCIIEncoding))
    $script:HelperProcess = Start-TaskProfileHelper $cscript $script:HelperScript `
        $auxProfile $script:NamespaceRoot
    $script:HelperIdentity = $script:HelperProcess
    Add-LaunchRecord 'task-profile-helper' `
        ([pscustomobject]@{ Process = $script:HelperIdentity.Process; Record = $script:HelperIdentity.Record }) `
        'auxiliary-profile' 0 $true
    $references = @(Get-VerifierBrowserProcessSnapshot '' $auxProfile '' '' 0)
    $profileReferences = @(Get-VerifierProfileReferenceRecords $references $auxProfile)
    Assert-Contract ($profileReferences.Count -eq 1 -and
        [int]$profileReferences[0].ProcessId -eq [int]$script:HelperIdentity.Record.ProcessId -and
        (Test-VerifierCommandLineSwitch $profileReferences[0].CommandLine '--user-data-dir' $auxProfile)) `
        'helper-real-process-reference'
    $helperBlocked = $false
    try {
        Assert-VerifierBrowserProfileIsQuiescent $auxProfile '' '' '' 0
    } catch {
        $helperBlocked = $_.Exception.Message -match (
            'still references profile .+PID\(s\):\s*' +
            [regex]::Escape([string]$script:HelperIdentity.Record.ProcessId) + '(?:\D|$)')
    }
    Assert-Contract $helperBlocked 'helper-quiescence-rejected'
    $helperSummary = Stop-TaskProfileHelper $script:HelperIdentity $auxProfile $script:HelperScript
    $script:HelperAbsenceProven = $true
    $script:HelperProcess = $null
    $script:HelperIdentity = $null
    $script:HelperStartTicks = 0L
    $script:HelperProfile = ''
    [void](Assert-VerifierBrowserProfileIsQuiescent $auxProfile '' '' '' 0)
    Assert-Contract (Test-Path -LiteralPath $auxProfile -PathType Container -ErrorAction Stop) 'helper-profile-preserved'
    Invoke-PrivateProfileRemoval $script:Context $auxProfile
    Assert-Contract (-not (Test-Path -LiteralPath $auxProfile -ErrorAction Stop)) 'helper-profile-cleanup-retry'
    $script:HelperAuxProfile = ''
    $helperSummary | Add-Member -NotePropertyName elapsedMs -NotePropertyValue ([long]$helperWatch.ElapsedMilliseconds)
    $helperSummary | Add-Member -NotePropertyName scope -NotePropertyValue 'profile-quiescence-boundary-only'
    $helperSummary | Add-Member -NotePropertyName profileIdentity `
        -NotePropertyValue (Get-VerifierOpaquePathIdentity 'helper-profile' $auxProfile)

    $script:Stage = 'junction-deletion-canary'
    $junctionWatch = [Diagnostics.Stopwatch]::StartNew()
    $junctionProfile = Join-Path $script:Context.RunRoot 'aux-junction-profile'
    $sentinelRoot = Join-Path $script:Context.RunRoot 'outside-profile-sentinel'
    $script:JunctionProfile = $junctionProfile
    $script:SentinelRoot = $sentinelRoot
    New-Item -ItemType Directory -Path $junctionProfile -ErrorAction Stop | Out-Null
    New-Item -ItemType Directory -Path $sentinelRoot -ErrorAction Stop | Out-Null
    [void](Assert-VerifierPhysicalOwnedPath $script:Context.RunRoot $junctionProfile -ValidateTree)
    [void](Assert-VerifierPhysicalOwnedPath $script:Context.RunRoot $sentinelRoot -ValidateTree)
    Assert-Contract (-not (Test-VerifierPhysicalChildPath $junctionProfile $sentinelRoot) -and
        (Test-VerifierPhysicalChildPath $script:Context.RunRoot $sentinelRoot)) 'sentinel-outside-profile'
    $script:SentinelFile = Join-Path $sentinelRoot 'sentinel.txt'
    $sentinelValue = 'cleanup-junction-sentinel-' + $taskId
    $script:SentinelValue = $sentinelValue
    [IO.File]::WriteAllText($script:SentinelFile, $sentinelValue,
        ([Text.UTF8Encoding]::new($false)))
    $script:JunctionPath = Join-Path $junctionProfile 'task-owned-junction'
    $script:JunctionTarget = $sentinelRoot
    New-Item -ItemType Junction -Path $script:JunctionPath -Target $sentinelRoot `
        -ErrorAction Stop | Out-Null
    $walk = @(Get-ChildItem -LiteralPath $junctionProfile -Force -Recurse -ErrorAction Stop)
    $linkInWalk = @($walk | Where-Object {
        [IO.Path]::GetFullPath([string]$_.FullName).Equals(
            [IO.Path]::GetFullPath($script:JunctionPath), [StringComparison]::OrdinalIgnoreCase)
    })
    $walkedOutside = @($walk | Where-Object {
        [IO.Path]::GetFullPath([string]$_.FullName).StartsWith(
            ([IO.Path]::GetFullPath($sentinelRoot).TrimEnd([char]92) + [char]92),
            [StringComparison]::OrdinalIgnoreCase)
    })
    Assert-Contract ($linkInWalk.Count -eq 1 -and
        (($linkInWalk[0].Attributes -band [IO.FileAttributes]::ReparsePoint) -ne 0) -and
        $walkedOutside.Count -eq 0) 'host-junction-walk-safety'
    Assert-Contract (([IO.File]::ReadAllText((Join-Path $script:JunctionPath 'sentinel.txt'))) -ceq $sentinelValue) `
        'junction-target-canary'
    $script:Stage = 'junction-delete-refusal'
    $deleteBlocked = $false
    try {
        Invoke-PrivateProfileRemoval $script:Context $junctionProfile
    } catch {
        $deleteBlocked = $true
    }
    Assert-Contract ($deleteBlocked -and
        (Test-Path -LiteralPath $junctionProfile -PathType Container) -and
        (Test-Path -LiteralPath $script:SentinelFile -PathType Leaf) -and
        [IO.File]::ReadAllText($script:SentinelFile) -ceq $sentinelValue) 'junction-delete-blocked-sentinel-preserved'
    Remove-VerifiedExactJunction $script:JunctionPath $sentinelRoot `
        $script:SentinelFile $sentinelValue
    $script:JunctionPath = ''
    $script:JunctionTarget = ''
    Invoke-PrivateProfileRemoval $script:Context $junctionProfile
    Assert-Contract (-not (Test-Path -LiteralPath $junctionProfile -ErrorAction Stop) -and
        [IO.File]::ReadAllText($script:SentinelFile) -ceq $sentinelValue) 'junction-safe-retry'
    $script:JunctionProfile = ''
    $junctionSummary = [ordered]@{
        status = 'PASS'
        scope = 'owned-profile-deletion-boundary'
        deleteRefused = $true
        exactJunctionRemovedNonrecursively = $true
        retryDeletionSucceeded = $true
        sentinelPreserved = $true
        elapsedMs = [long]$junctionWatch.ElapsedMilliseconds
    }
    Remove-VerifierOwnedTree $script:Context.RunRoot $sentinelRoot
    $script:SentinelFile = ''
    $script:SentinelValue = ''
    $script:SentinelRoot = ''

    $script:Stage = 'real-browser-session'
    $script:BrowserSession = New-VerifierBrowserSession $script:Context `
        'browser-cleanup-budget' 'about:blank' $browser 45 $false
    $browserIdentity = Get-VerifierCurrentProcessIdentity `
        ([int]$script:BrowserSession.Record.ProcessId) `
        ([long]$script:BrowserSession.Record.ProcessStartTicks) `
        ([int]$script:BrowserSession.Record.ProcessParentProcessId) `
        ([string]$script:BrowserSession.Record.ProcessCommandLine) '' `
        ([int]$script:BrowserSession.CdpPort) ([string]$script:Context.RunId) '' `
        ([long]$script:BrowserSession.Record.ProcessParentProcessStartTicks) $browser
    $script:Stage = 'browser-session-attachment'
    $script:BrowserAttachmentProof = Assert-BrowserSessionAttachment `
        $script:BrowserSession $script:Context $browserIdentity $browser
    Add-LaunchRecord 'owned-browser' $browserIdentity 'browser-session-profile' `
        ([int]$script:BrowserSession.CdpPort) $true
    # The compiled matrix registers reports after browser attachment. Keep
    # startup independent of the later evidence population here as well.
    $script:Stage = 'evidence-artifact-fixtures'
    $null = Register-TaskEvidenceFixtures $script:Context $EvidenceArtifactCount
    $script:Stage = 'evidence-containment-canary'
    $null = Invoke-EvidenceArtifactReparseCanary $script:Context
    $script:Stage = 'profile-walk-fixture'
    $fileCount = New-ProfileWalkFixture ([string]$script:BrowserSession.Profile) $ProfileFileCount
    $setup.Stop()

    $script:Stage = 'complete-verifier-run'
    $script:CleanupAttempted = $true
    $cleanupWatch = [Diagnostics.Stopwatch]::StartNew()
    $cleanupResult = Complete-VerifierRun $script:Context
    $cleanupWatch.Stop()
    Assert-Contract ($cleanupResult.Success -is [bool] -and $cleanupResult.Success) 'complete-verifier-run-success'
    $script:CleanupSucceeded = $true
    $script:Stage = 'owner-proof'
    $setupElapsedMs = [long]$setup.ElapsedMilliseconds
    $report = Get-OutputRecord $script:Context $cleanupResult `
        ([long]$cleanupWatch.ElapsedMilliseconds) $helperSummary $junctionSummary `
        $fileCount $setupElapsedMs ([bool]$ForceTcpListener)
    $script:Checks = [int]$report.checks

    $report.checks = $script:Checks + 1
    $report.reportWritten = $true
    $script:Stage = 'report'
    $reportWritten = [bool](Write-TaskReport $report)
    Write-Output ('REPORT_PATH=' + $script:ReportPath + ' reportWritten=' + [string]$reportWritten)
    if (-not $reportWritten) {
        throw [InvalidOperationException]::new('positive-report-write-failed')
    }
    $script:Checks++
    $evidenceArtifactCount = [int]$script:EvidenceArtifactSummary['count']
    $evidenceArtifactBytes = [long]$script:EvidenceArtifactSummary['bytes']
    Write-Output ('PASS browser-cleanup-budget-v1 checks=' + [string]$script:Checks +
        ' profileFiles=' + [string]$fileCount +
        ' evidenceArtifacts=' + [string]$evidenceArtifactCount +
        ' evidenceBytes=' + [string]$evidenceArtifactBytes +
        ' cleanupMs=' + [string]$cleanupWatch.ElapsedMilliseconds +
        ' cleanupBudgetMs=120000')
    exit 0
} catch {
    $script:FailureType = $_.Exception.GetType().Name
    $script:FailureStage = $script:Stage
    $safeFailureMessage = Get-SafeFailureMessage $_
    $failureReport = [ordered]@{
        protocol = 'troubleshootjs-browser-cleanup-budget-v1'
        status = 'FAIL'
        scope = 'about-blank-browser-resource-cleanup'
        stage = $script:FailureStage
        errorType = $script:FailureType
        errorMessage = $safeFailureMessage
        checks = $script:Checks
        cleanupAttempted = [bool]$script:CleanupAttempted
        cleanupSucceeded = [bool]$script:CleanupSucceeded
        cleanupMs = if ($null -ne $cleanupWatch) { [long]$cleanupWatch.ElapsedMilliseconds } else { $null }
        cleanupBudgetMs = 120000
        resourceSuccess = if ($null -ne $cleanupResult) { [bool]$cleanupResult.Success } else { $null }
        extraPhaseTimings = if ($null -ne $cleanupResult -and
                $cleanupResult.PSObject.Properties['ExtraPhaseTimings']) {
            $cleanupResult.ExtraPhaseTimings
        } else { $null }
        profileFilesExercised = [int]$script:ProfileFilesExercised
        evidenceArtifacts = $script:EvidenceArtifactSummary
        evidenceContainmentCanary = $script:EvidenceContainmentSummary
        browserAttachmentProof = $script:BrowserAttachmentProof
        processLaunches = @($script:ProcessLaunches.ToArray())
        limits = @('No build or electrical/browser matrix was run by this script.',
            'Full-run release behavior for an unrelated helper profile is not claimed.',
            'Scope is resource cleanup for a factory-attached about:blank browser session; no application or player behavior is exercised.')
    }
    $failureReport.reportWriteAttempted = $true
    $failureReportWritten = [bool](Write-TaskReport $failureReport)
    Write-Output ('REPORT_PATH=' + $script:ReportPath + ' reportWritten=' + [string]$failureReportWritten)
    Write-Error ('FAIL browser-cleanup-budget-v1 stage=' + $script:FailureStage +
        ' errorType=' + $script:FailureType + ' errorMessage=' + $safeFailureMessage +
        ' checks=' + [string]$script:Checks) -ErrorAction Continue
    exit 2
} finally {
    if ($null -ne $script:HelperIdentity) {
        try {
            [void](Stop-TaskProfileHelper $script:HelperIdentity `
                $script:HelperProfile $script:HelperScript)
            $script:HelperIdentity = $null
            $script:HelperProcess = $null
            $script:HelperStartTicks = 0L
            $script:HelperProfile = ''
            $script:HelperAbsenceProven = $true
        } catch { }
    } elseif ($null -ne $script:HelperProcess -and $script:HelperStartTicks -gt 0 -and
            -not [String]::IsNullOrWhiteSpace($script:HelperProfile) -and
            -not [String]::IsNullOrWhiteSpace($script:HelperScript)) {
        try {
            $recovered = Get-VerifierCurrentProcessIdentity ([int]$script:HelperProcess.Id) `
                ([long]$script:HelperStartTicks) 0 '' '' 0 '' '' 0
            if ([IO.Path]::GetFileName([string]$recovered.Record.ExecutablePath) -ieq 'cscript.exe' -and
                    (Test-VerifierCommandLineCanonicalPathToken $recovered.Record.CommandLine $script:HelperScript) -and
                    (Test-VerifierCommandLineSwitch $recovered.Record.CommandLine '--user-data-dir' $script:HelperProfile)) {
                [void](Stop-TaskProfileHelper ([pscustomobject]@{
                    Process = $recovered.Process; Record = $recovered.Record
                }) $script:HelperProfile $script:HelperScript)
                $script:HelperIdentity = $null
                $script:HelperProcess = $null
                $script:HelperStartTicks = 0L
                $script:HelperProfile = ''
                $script:HelperAbsenceProven = $true
            }
        } catch { }
    }
    if (-not [String]::IsNullOrWhiteSpace($script:JunctionPath) -and
            -not [String]::IsNullOrWhiteSpace($script:JunctionTarget) -and
            -not [String]::IsNullOrWhiteSpace($script:SentinelFile)) {
        try {
            Remove-VerifiedExactJunction $script:JunctionPath $script:JunctionTarget `
                $script:SentinelFile $script:SentinelValue
            $script:JunctionPath = ''
            $script:JunctionTarget = ''
        } catch { }
    }
    if ($null -ne $script:Context -and
            -not [String]::IsNullOrWhiteSpace($script:HelperAuxProfile) -and
            ($null -eq $script:HelperProcess -or $script:HelperAbsenceProven) -and
            (Test-Path -LiteralPath $script:HelperAuxProfile -PathType Container)) {
        try { Invoke-PrivateProfileRemoval $script:Context $script:HelperAuxProfile } catch { }
    }
    if ($null -ne $script:Context -and
            -not [String]::IsNullOrWhiteSpace($script:JunctionProfile) -and
            [String]::IsNullOrWhiteSpace($script:JunctionPath) -and
            (Test-Path -LiteralPath $script:JunctionProfile -PathType Container)) {
        try { Invoke-PrivateProfileRemoval $script:Context $script:JunctionProfile } catch { }
    }
    if ($null -ne $script:Context -and
            -not [String]::IsNullOrWhiteSpace($script:SentinelRoot) -and
            [String]::IsNullOrWhiteSpace($script:JunctionPath) -and
            (Test-Path -LiteralPath $script:SentinelRoot -PathType Container)) {
        try { Remove-VerifierOwnedTree $script:Context.RunRoot $script:SentinelRoot } catch { }
    }
    if ($null -ne $script:Context -and
            -not [String]::IsNullOrWhiteSpace($script:ArtifactJunctionPath) -and
            -not [String]::IsNullOrWhiteSpace($script:ArtifactSentinelRoot) -and
            -not [String]::IsNullOrWhiteSpace($script:ArtifactSentinelFile) -and
            -not [String]::IsNullOrWhiteSpace($script:ArtifactSentinelValue)) {
        try {
            if (Test-Path -LiteralPath $script:ArtifactJunctionPath -ErrorAction Stop) {
                Remove-VerifiedExactJunction $script:ArtifactJunctionPath `
                    $script:ArtifactSentinelRoot $script:ArtifactSentinelFile `
                    $script:ArtifactSentinelValue $script:Context.EvidenceDirectory
                $script:ArtifactJunctionPath = ''
            } else { $script:ArtifactJunctionPath = '' }
        } catch { }
    }
    if ($null -ne $script:Context -and
            -not [String]::IsNullOrWhiteSpace($script:ArtifactSentinelRoot) -and
            [String]::IsNullOrWhiteSpace($script:ArtifactJunctionPath)) {
        try {
            if (Test-Path -LiteralPath $script:ArtifactSentinelRoot `
                    -PathType Container -ErrorAction Stop) {
                Remove-VerifierOwnedTree $script:Context.RunRoot $script:ArtifactSentinelRoot
            }
            $script:ArtifactSentinelRoot = ''
            $script:ArtifactSentinelFile = ''
            $script:ArtifactSentinelValue = ''
        } catch { }
    }
    if ($null -ne $script:Context -and -not $script:CleanupAttempted) {
        try {
            $script:CleanupAttempted = $true
            $lastChanceCleanup = Complete-VerifierRun $script:Context
            $script:CleanupSucceeded = [bool]$lastChanceCleanup.Success
        } catch { }
    }
    if (-not [String]::IsNullOrWhiteSpace($script:HelperScript) -and
            (Test-Path -LiteralPath $script:HelperScript -PathType Leaf) -and
            $null -eq $script:HelperIdentity) {
        try {
            [void](Assert-VerifierPhysicalOwnedPath $script:NamespaceRoot $script:HelperScript)
            [IO.File]::Delete($script:HelperScript)
        } catch { }
    }
}
