[CmdletBinding()]
param(
    [string]$ModulePath = '',
    [string]$ExpectedModuleSha256 = '',
    [switch]$LegacyOnly
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
$script:Assertions = 0
$script:ModuleInfo = $null
$script:FixtureWorktree = Join-Path $env:TEMP 'TroubleshootJS-retained-preview-fixture-worktree'
$script:RepositoryIdentity = [Guid]::NewGuid().ToString('N')
$script:RunId = [Guid]::NewGuid().ToString('N')
$script:FixtureNamespaceRoot = Join-Path (Join-Path $env:TEMP 'TroubleshootJS\verify') $script:RepositoryIdentity
$script:PreviewNonce = ('b' * 64)
$script:CreatedUtc = '2026-11-01T12:00:00.0000000Z'
$script:BrowserPath = 'C:\synthetic\browser.exe'
$script:BrowserPid = 7101
$script:BrowserStart = 710100L
$script:PreviewPid = 7201
$script:PreviewStart = 720100L
$script:PreviewLeaseId = '0123456789abcdef0123456789abcdef'
$script:CdpPort = 45111
$script:PreviewPort = 45222

function Assert-Contract([bool]$Condition, [string]$Code) {
    if (-not $Condition) { throw [InvalidOperationException]::new($Code) }
    $script:Assertions++
}

function Assert-FixturePathNoReparseAncestors([string]$Path, [switch]$RequireDirectory) {
    if ([String]::IsNullOrWhiteSpace($Path)) { throw 'FIXTURE_PATH_EMPTY' }
    $fullPath = [IO.Path]::GetFullPath($Path)
    if ($RequireDirectory -and -not [IO.Directory]::Exists($fullPath)) {
        throw 'FIXTURE_DIRECTORY_NOT_PRESENT_FOR_REPARSE_CHECK'
    }
    $currentPath = $fullPath
    while (-not [String]::IsNullOrWhiteSpace($currentPath)) {
        try {
            $attributes = [IO.File]::GetAttributes($currentPath)
            if ($attributes -band [IO.FileAttributes]::ReparsePoint) {
                throw 'FIXTURE_PATH_REPARSE_ANCESTOR_NOT_ALLOWED'
            }
        } catch [IO.FileNotFoundException] {
        } catch [IO.DirectoryNotFoundException] {
        }
        $parent = [IO.Directory]::GetParent($currentPath)
        if ($null -eq $parent) { break }
        $currentPath = $parent.FullName
    }
}

function Get-FixtureNamespacePathProof([switch]$RequireNamespaceDirectory) {
    $identity = [string]$script:RepositoryIdentity
    $parsedIdentity = [Guid]::Empty
    if (-not [Guid]::TryParseExact($identity, 'N', [ref]$parsedIdentity) -or
            $parsedIdentity -eq [Guid]::Empty -or
            $identity -cne $parsedIdentity.ToString('N')) {
        throw 'FIXTURE_REPOSITORY_IDENTITY_NOT_CANONICAL_GUID_N'
    }
    $verifyRoot = [IO.Path]::GetFullPath((Join-Path $env:TEMP 'TroubleshootJS\verify'))
    if ([IO.File]::Exists($verifyRoot)) { throw 'FIXTURE_VERIFY_ROOT_IS_NOT_A_DIRECTORY' }
    $expectedNamespace = [IO.Path]::GetFullPath((Join-Path $verifyRoot $identity))
    $actualNamespace = [IO.Path]::GetFullPath($script:FixtureNamespaceRoot)
    if (-not [String]::Equals($actualNamespace, $expectedNamespace,
            [StringComparison]::OrdinalIgnoreCase)) {
        throw 'FIXTURE_NAMESPACE_NOT_EXACT_GUID_CHILD_OF_VERIFY_ROOT'
    }
    Assert-FixturePathNoReparseAncestors $verifyRoot
    Assert-FixturePathNoReparseAncestors $actualNamespace `
        -RequireDirectory:$RequireNamespaceDirectory
    return $actualNamespace
}

function Get-ModuleMutexName([int]$Port) {
    return [string](& $script:ModuleInfo {
        param($requestedPort)
        Get-VerifierPortMutexName $null ([int]$requestedPort)
    } $Port)
}

function New-Claim([string]$Kind, [int]$Port, [string]$LeaseId,
        [string]$Path, [int]$OwnerPid, [long]$OwnerStart) {
    return [ordered]@{
        protocol = 'troubleshootjs-verifier-port-claim-v1'
        runId = $script:RunId
        repositoryIdentity = $script:RepositoryIdentity
        worktreeRoot = $script:FixtureWorktree
        leaseId = $LeaseId
        path = $Path
        kind = $Kind
        port = $Port
        mutexName = Get-ModuleMutexName $Port
        ownerPid = $OwnerPid
        ownerStartTicks = $OwnerStart
    }
}

function New-CdpLease([string]$RunRoot, [string]$LeaseId = 'cdp-fixture-lease') {
    $path = Join-Path (Join-Path $RunRoot 'port-leases') ($script:CdpPort.ToString() + '-' + $LeaseId + '.lease')
    $claim = New-Claim 'cdp' $script:CdpPort $LeaseId $path 8001 800100L
    return [ordered]@{
        leaseId = $LeaseId; kind = 'cdp'; port = $script:CdpPort; path = $path
        runId = $script:RunId; repositoryIdentity = $script:RepositoryIdentity
        worktreeRoot = $script:FixtureWorktree; claimName = Get-ModuleMutexName $script:CdpPort
        claimOwnerPid = 8001; claimOwnerStartTicks = 800100L
        status = 'bound'; claimState = 'bound'; releaseState = 'active'
        releaseJournalState = 'active'; registered = $true; releaseBlocked = $false
        releaseBlockReason = ''; mutexReleased = $false
        bindValidatedUtc = $script:CreatedUtc; releasedUtc = ''
        profile = (Join-Path $RunRoot 'browser\profile-cdp'); browserPath = $script:BrowserPath
        listenerInspectionSuccess = $true; listenerInspectionKnown = $true
        listenerInspectionUtc = $script:CreatedUtc; listenerHasListeners = $true
        listenerAbsent = $false; processTerminationProven = $false; processAbsent = $false
        processProofRequired = $true; boundProcessId = $script:BrowserPid
        boundProcessStartTicks = $script:BrowserStart; listenerProcessId = $script:BrowserPid
        listenerProcessStartTicks = $script:BrowserStart
        listenerOwnerKind = 'user-process'; listenerOwnerProof = 'diagnostics-process-start-v1'
        listenerOwnerEvidence = 'system-diagnostics-process-starttime'; claim = $claim
    }
}

function New-PreviewLease([string]$RunRoot, [string]$LeaseId) {
    $path = Join-Path (Join-Path $RunRoot 'port-leases') ($script:PreviewPort.ToString() + '-' + $LeaseId + '.lease')
    $claim = New-Claim 'preview' $script:PreviewPort $LeaseId $path 8002 800200L
    return [ordered]@{
        leaseId = $LeaseId; kind = 'preview'; port = $script:PreviewPort; path = $path
        runId = $script:RunId; repositoryIdentity = $script:RepositoryIdentity
        worktreeRoot = $script:FixtureWorktree; claimName = Get-ModuleMutexName $script:PreviewPort
        claimOwnerPid = 8002; claimOwnerStartTicks = 800200L
        status = 'released'; claimState = 'released'; releaseState = 'complete'
        releaseJournalState = 'complete'; registered = $true; releaseBlocked = $false
        releaseBlockReason = ''; mutexReleased = $true
        bindValidatedUtc = $script:CreatedUtc; releasedUtc = $script:CreatedUtc
        profile = ''; browserPath = ''
        listenerInspectionSuccess = $true; listenerInspectionKnown = $true
        listenerInspectionUtc = $script:CreatedUtc; listenerHasListeners = $false
        listenerAbsent = $true; processTerminationProven = $true; processAbsent = $true
        processProofRequired = $true; boundProcessId = $script:PreviewPid
        boundProcessStartTicks = $script:PreviewStart; listenerProcessId = $script:PreviewPid
        listenerProcessStartTicks = $script:PreviewStart
        listenerOwnerKind = 'user-process'; listenerOwnerProof = 'diagnostics-process-start-v1'
        listenerOwnerEvidence = 'system-diagnostics-process-starttime'; claim = $claim
    }
}

function New-Receipt([string]$Profile, [string]$CdpLeaseId, [string]$CommandLine) {
    return [ordered]@{
        protocol = 'troubleshootjs-verifier-browser-recovery-v1'
        authorityToken = ('c' * 64); issuedUtc = $script:CreatedUtc; state = 'bound'
        boundUtc = $script:CreatedUtc; closedUtc = ''; closeAttempted = $false
        closeAttemptedUtc = ''; runId = $script:RunId
        repositoryIdentity = $script:RepositoryIdentity; worktreeRoot = $script:FixtureWorktree
        routeId = 'fixture-route'; routeName = 'fixture-route'
        browserPath = $script:BrowserPath; profile = $Profile
        leaseId = $CdpLeaseId; cdpPort = $script:CdpPort
        rootProcessId = $script:BrowserPid; rootProcessStartTicks = $script:BrowserStart
        rootParentProcessId = 7001; rootParentProcessStartTicks = 700100L
        rootProcessCommandLine = $CommandLine
        listenerProcessId = $script:BrowserPid; listenerProcessStartTicks = $script:BrowserStart
    }
}

function New-Session([string]$RunRoot, [string]$CdpLeasePath, [string]$CdpLeaseId) {
    $profile = Join-Path $RunRoot 'browser\profile-cdp'
    $commandLine = $script:BrowserPath + ' --user-data-dir=' + $profile +
        ' --remote-debugging-port=' + $script:CdpPort
    return [ordered]@{
        owner = 'run'; runId = $script:RunId; repositoryIdentity = $script:RepositoryIdentity
        worktreeRoot = $script:FixtureWorktree; routeId = 'fixture-route'
        routeName = 'fixture-route'; profile = $profile; cdpLeasePath = $CdpLeasePath
        browserPath = $script:BrowserPath; processCommandLine = $commandLine
        targetId = 'fixture-target'; expectedUrl = 'http://127.0.0.1:45222/'
        expectedRunMarker = 'tsjVerifierRun=' + $script:RunId
        expectedRouteMarker = 'tsjVerifierRoute=fixture-route'
        cdpPort = $script:CdpPort; processId = $script:BrowserPid
        processStartTicks = $script:BrowserStart; processParentProcessId = 7001
        processParentProcessStartTicks = 700100L
        status = 'cleanup-failed'; cleanupResult = 'infrastructure-failure'
        error = 'synthetic retained failure'; profileProcessScanCompleted = $false
        profileInspectionFailed = $false
        recoveryReceipt = New-Receipt $profile $CdpLeaseId $commandLine
        containmentLaunch = [ordered]@{
            protocol = 'troubleshootjs-verifier-browser-containment-launch-v1'
            jobName = 'Local\synthetic-retained-browser-job'; state = 'launched'
            launchProcessId = $script:BrowserPid; launchedUtc = $script:CreatedUtc
        }
    }
}

function New-Server([string]$RunRoot, [bool]$Terminal) {
    $repositoryRoot = $script:FixtureWorktree
    $scriptPath = Join-Path $repositoryRoot 'scripts\preview.ps1'
    $stdout = Join-Path (Join-Path $RunRoot 'server') 'stdout.log'
    $stderr = Join-Path (Join-Path $RunRoot 'server') 'stderr.log'
    if (-not $Terminal) {
        return [ordered]@{
            owner = 'none'; baseUrl = ''; repositoryIdentity = $script:RepositoryIdentity
            worktreeRoot = $repositoryRoot; repositoryRoot = $repositoryRoot
            webRoot = Join-Path $repositoryRoot 'war'; identityProtocol = ''
            processCommandLine = ''; script = ''; runId = ''; nonce = ''
            leaseId = ''; leaseKind = ''; leaseClaimName = ''; leaseClaimState = ''
            leaseReleaseState = ''; leaseReleaseJournalState = ''; leasePath = ''
            port = 0; processId = 0; processStartTicks = 0L
            processParentProcessId = 0; processParentProcessStartTicks = 0L
            leaseOwnerPid = 0; leaseOwnerStartTicks = 0L
            state = 'not-started'; stdoutLog = ''; stderrLog = ''
            cleanupResult = 'not-applicable'; error = ''; identityVerified = $false
            callerOwned = $false; processIdentityKnown = $false; ownershipUncertain = $false
            processTerminationProven = $false; processAbsent = $false
            listenerInspectionProven = $false; listenerAbsent = $null
            leaseListenerAbsent = $null; leaseProcessProofRequired = $null
            leaseListenerOwnerKind = 'none'; leaseListenerOwnerProof = ''
            leaseListenerOwnerEvidence = ''
        }
    }
    $commandLine = 'powershell.exe -File ' + $scriptPath + ' -Port ' +
        $script:PreviewPort + ' -VerifierRunId ' + $script:RunId +
        ' -VerifierNonce ' + $script:PreviewNonce
    $previewLeaseId = $script:PreviewLeaseId
    $previewLease = New-PreviewLease $RunRoot $previewLeaseId
    return [ordered]@{
        owner = 'run'; baseUrl = 'http://127.0.0.1:' + $script:PreviewPort
        repositoryIdentity = $script:RepositoryIdentity; worktreeRoot = $repositoryRoot
        repositoryRoot = $repositoryRoot; webRoot = Join-Path $repositoryRoot 'war'
        identityProtocol = 'troubleshootjs-preview-identity-v2'
        processCommandLine = $commandLine; script = $scriptPath
        runId = $script:RunId; nonce = $script:PreviewNonce
        leaseId = $previewLeaseId; leaseKind = 'preview'
        leaseClaimName = $previewLease.claimName; leaseClaimState = 'released'
        leaseReleaseState = 'complete'; leaseReleaseJournalState = 'complete'
        leasePath = $previewLease.path; port = $script:PreviewPort
        processId = $script:PreviewPid; processStartTicks = $script:PreviewStart
        processParentProcessId = 7002; processParentProcessStartTicks = 700200L
        leaseOwnerPid = $previewLease.claimOwnerPid
        leaseOwnerStartTicks = $previewLease.claimOwnerStartTicks
        state = 'cleaned'; stdoutLog = $stdout; stderrLog = $stderr
        cleanupResult = 'complete'; error = ''; identityVerified = $true
        callerOwned = $false; processIdentityKnown = $true; ownershipUncertain = $false
        processTerminationProven = $true; processAbsent = $true
        listenerInspectionProven = $true; listenerAbsent = $true
        leaseListenerAbsent = $true; leaseProcessProofRequired = $true
        leaseListenerOwnerKind = 'user-process'
        leaseListenerOwnerProof = 'diagnostics-process-start-v1'
        leaseListenerOwnerEvidence = 'system-diagnostics-process-starttime'
    }
}

function New-Fixture([ValidateSet('old', 'terminal')][string]$Kind) {
    $repositoryRoot = $script:FixtureWorktree
    $namespaceRoot = $script:FixtureNamespaceRoot
    $runRoot = Join-Path $namespaceRoot $script:RunId
    $leaseRoot = Join-Path $runRoot 'port-leases'
    $cdpLease = New-CdpLease $runRoot
    $session = New-Session $runRoot $cdpLease.path $cdpLease.leaseId
    $server = New-Server $runRoot ($Kind -eq 'terminal')
    $leases = if ($Kind -eq 'terminal') {
        $previewLease = New-PreviewLease $runRoot $script:PreviewLeaseId
        @($cdpLease, $previewLease)
    } else { @($cdpLease) }
    $manifest = [ordered]@{
        protocol = 'troubleshootjs-verifier-run-v1'; runId = $script:RunId
        repositoryIdentity = $script:RepositoryIdentity; worktreeRoot = $repositoryRoot
        runRoot = $runRoot; runNamespaceRoot = $namespaceRoot
        evidenceDirectory = Join-Path (Join-Path $namespaceRoot 'evidence') $script:RunId
        evidenceNamespaceRoot = Join-Path $namespaceRoot 'evidence'
        manifestPath = Join-Path $runRoot 'manifest.json'; createdUtc = $script:CreatedUtc
        baseUrl = if ($Kind -eq 'terminal') { 'http://127.0.0.1:' + $script:PreviewPort } else { '' }
        previewNonce = $script:PreviewNonce; server = $server
        leases = @($leases); browserSessions = @($session); artifacts = @()
        cleanup = [ordered]@{ state = 'infrastructure-failure'; completedUtc = ''; errors = @('synthetic') }
    }
    return $manifest
}

function Set-IssuedContainedFixture($Manifest) {
    if ($null -eq $Manifest -or @($Manifest.leases).Count -lt 1 -or
            @($Manifest.browserSessions).Count -ne 1) {
        throw 'ISSUED_CONTAINED_FIXTURE_SHAPE_INVALID'
    }
    $lease = $Manifest.leases[0]
    $lease.status = 'leased'
    $lease.claimState = 'held'
    $lease.bindValidatedUtc = ''
    $lease.listenerInspectionSuccess = $false
    $lease.listenerInspectionKnown = $false
    $lease.listenerHasListeners = $null
    $lease.listenerAbsent = $null
    $lease.listenerInspectionUtc = ''
    $lease.processTerminationProven = $false
    $lease.processAbsent = $false
    $lease.processProofRequired = $false
    $lease.boundProcessId = 0
    $lease.boundProcessStartTicks = 0L
    $lease.listenerProcessId = 0
    $lease.listenerProcessStartTicks = 0L
    $lease.listenerOwnerKind = 'none'
    $lease.listenerOwnerProof = ''
    $lease.listenerOwnerEvidence = ''

    $session = $Manifest.browserSessions[0]
    $session.processId = 0
    $session.processStartTicks = 0L
    $session.processParentProcessId = 0
    $session.processParentProcessStartTicks = 0L
    $session.processCommandLine = ''
    $session.targetId = ''
    $session.expectedUrl = ''
    $session.status = 'cleanup-failed'
    $session.cleanupResult = 'infrastructure-failure'
    $session.error = 'synthetic issued-contained launch failure'
    $session.recoveryReceipt.state = 'issued'
    $session.recoveryReceipt.boundUtc = ''
    $session.recoveryReceipt.closedUtc = ''
    $session.recoveryReceipt.closeAttempted = $false
    $session.recoveryReceipt.closeAttemptedUtc = ''
    $session.recoveryReceipt.rootProcessId = 0
    $session.recoveryReceipt.rootProcessStartTicks = 0L
    $session.recoveryReceipt.rootParentProcessId = 0
    $session.recoveryReceipt.rootParentProcessStartTicks = 0L
    $session.recoveryReceipt.rootProcessCommandLine = ''
    $session.recoveryReceipt.listenerProcessId = 0
    $session.recoveryReceipt.listenerProcessStartTicks = 0L
    $session.containmentLaunch.state = 'launched'
    $session.containmentLaunch.launchProcessId = $script:BrowserPid
    $session.containmentLaunch.launchedUtc = $script:CreatedUtc
    return $Manifest
}

function Set-PreviewLeaseIdentityAlias($Manifest, [string]$LeaseId) {
    $portLeaseRoot = [IO.Path]::GetFullPath((Join-Path $Manifest.runRoot 'port-leases'))
    $candidatePath = Join-Path $portLeaseRoot `
        ($Manifest.leases[1].port.ToString() + '-' + $LeaseId + '.lease')
    $fullCandidatePath = [IO.Path]::GetFullPath($candidatePath)
    $rootWithSeparator = $portLeaseRoot.TrimEnd([char]92) + [char]92
    if (-not $fullCandidatePath.StartsWith($rootWithSeparator,
            [StringComparison]::OrdinalIgnoreCase)) {
        throw 'MALFORMED_PREVIEW_ID_FIXTURE_ESCAPES_PORT_LEASE_ROOT'
    }
    $Manifest.leases[1].leaseId = $LeaseId
    $Manifest.leases[1].path = $fullCandidatePath
    $Manifest.leases[1].claim.leaseId = $LeaseId
    $Manifest.leases[1].claim.path = $fullCandidatePath
    $Manifest.server.leaseId = $LeaseId
    $Manifest.server.leasePath = $fullCandidatePath
}

function Set-ModuleFixture($Manifest, [string]$ProcessMode = 'absent',
        [string]$ListenerMode = 'empty') {
    $json = ConvertTo-Json -InputObject $Manifest -Depth 24 -Compress
    $fixtureRoot = [IO.Path]::GetFullPath($script:FixtureNamespaceRoot).TrimEnd([char]92) + [char]92
    $manifestPath = [IO.Path]::GetFullPath([string]$Manifest.manifestPath)
    if (-not $manifestPath.StartsWith($fixtureRoot, [StringComparison]::OrdinalIgnoreCase)) {
        throw 'FIXTURE_MANIFEST_ESCAPES_TASK_NAMESPACE'
    }
    [IO.File]::WriteAllText($manifestPath, $json, (New-Object Text.UTF8Encoding($false)))
    $state = [pscustomobject]@{
        ManifestPath = [string]$Manifest.manifestPath; ManifestJson = $json
        RepositoryIdentity = $script:RepositoryIdentity; ProcessMode = $ProcessMode
        ListenerMode = $ListenerMode; Trace = New-Object Collections.ArrayList
        DurableCalls = 0; IntentWrites = 0; CompleteCalls = 0; CloseCalls = 0
        EnterCalls = 0; ExitCalls = 0; ChangeProcessAfterEnter = $false
        ScopePredicateOnly = $false; AuthorityActive = $false
        OriginalDurableServerLease = $null
        OriginalDurableManifestContext = $null
        OriginalRecoveryAuthority = $null
    }
    & $script:ModuleInfo {
        param($newState)
        $script:RetainedPreviewFixtureState = $newState
        $script:RetainedPreviewFixtureState.OriginalDurableServerLease = $script:RetainedPreviewFixtureOriginalDurableServerLease
        $script:RetainedPreviewFixtureState.OriginalDurableManifestContext = $script:RetainedPreviewFixtureOriginalDurableManifestContext
        $script:RetainedPreviewFixtureState.OriginalRecoveryAuthority = $script:RetainedPreviewFixtureOriginalRecoveryAuthority
    } $state
}

function Install-ModuleFixtureMocks {
    & $script:ModuleInfo {
        $script:RetainedPreviewFixtureOriginalDurableServerLease =
            (Get-Command Assert-VerifierDurableServerLease -CommandType Function).ScriptBlock
        $script:RetainedPreviewFixtureOriginalDurableManifestContext =
            (Get-Command Assert-VerifierDurableManifestContext -CommandType Function).ScriptBlock
        $script:RetainedPreviewFixtureOriginalRecoveryAuthority =
            (Get-Command Get-VerifierRetainedBrowserRecoveryLeaseAuthority -CommandType Function).ScriptBlock
        Set-Item Function:\Get-VerifierRepositoryIdentity -Value {
            param($Root)
            return [string]$script:RetainedPreviewFixtureState.RepositoryIdentity
        } -Force
        Set-Item Function:\Confirm-VerifierRecordedProcessAbsent -Value {
            param($Recorded, [string]$Role, [string]$ExpectedCommandLine,
                [int]$Port, [string]$Script, [string]$RunId,
                [string]$Nonce, [string]$Profile)
            [void]$script:RetainedPreviewFixtureState.Trace.Add('process')
            switch ([string]$script:RetainedPreviewFixtureState.ProcessMode) {
                'absent' { return [pscustomobject]@{ QueryProven = $true; Absent = $true; Replaced = $false; Current = $null } }
                'live' { return [pscustomobject]@{ QueryProven = $true; Absent = $false; Replaced = $false; Current = [pscustomobject]@{ ProcessId = $Recorded.ProcessId } } }
                'replaced' { return [pscustomobject]@{ QueryProven = $true; Absent = $true; Replaced = $true; Current = [pscustomobject]@{ ProcessId = $Recorded.ProcessId } } }
                default { return [pscustomobject]@{ QueryProven = $false; Absent = $false; Replaced = $false; Current = $null } }
            }
        } -Force
        Set-Item Function:\Get-VerifierLoopbackListenerRecords -Value {
            param([int]$Port)
            [void]$script:RetainedPreviewFixtureState.Trace.Add('listener')
            $mode = [string]$script:RetainedPreviewFixtureState.ListenerMode
            if ($mode -eq 'empty') {
                return [pscustomobject]@{
                    Success = $true; Known = $true; HasListeners = $false; Listeners = @()
                    Source = 'Get-NetTCPConnection'; ListenerOwnerKind = 'none'
                    ListenerOwnerProof = ''; ListenerOwnerEvidence = ''; Error = ''
                }
            }
            if ($mode -eq 'live') {
                $row = [pscustomobject]@{
                    LocalAddress = '127.0.0.1'; Port = $Port; ProcessId = 7202
                    ProcessStartTicks = 720200L; Source = 'Get-NetTCPConnection'
                    ListenerOwnerKind = 'user-process'
                    ListenerOwnerProof = 'diagnostics-process-start-v1'
                    ListenerOwnerEvidence = 'system-diagnostics-process-starttime'
                }
                return [pscustomobject]@{
                    Success = $true; Known = $true; HasListeners = $true; Listeners = @($row)
                    Source = 'Get-NetTCPConnection'; ListenerOwnerKind = 'user-process'
                    ListenerOwnerProof = 'diagnostics-process-start-v1'
                    ListenerOwnerEvidence = 'system-diagnostics-process-starttime'; Error = ''
                }
            }
            return [pscustomobject]@{
                Success = $false; Known = $false; HasListeners = $false; Listeners = @()
                Source = 'Get-NetTCPConnection'; ListenerOwnerKind = 'none'
                ListenerOwnerProof = ''; ListenerOwnerEvidence = ''; Error = 'synthetic unknown'
            }
        } -Force
        Set-Item Function:\Assert-VerifierDurableServerLease -Value {
            param($Context, [string]$Label = 'verifier server state', [switch]$AllowMissing)
            $state = $script:RetainedPreviewFixtureState
            $state.DurableCalls++
            [void]$state.Trace.Add('durable-server-lease')
            & $state.OriginalDurableServerLease $Context $Label -AllowMissing:$AllowMissing
        } -Force
        Set-Item Function:\Assert-VerifierDurableManifestContext -Value {
            param($Context)
            $state = $script:RetainedPreviewFixtureState
            if ($state.ScopePredicateOnly) { return }
            & $state.OriginalDurableManifestContext $Context
        } -Force
        Set-Item Function:\Get-VerifierRetainedBrowserRecoveryLeaseAuthority -Value {
            param($Context, $Lease, [string]$Label = 'retained browser recovery lease authority')
            $state = $script:RetainedPreviewFixtureState
            if ($state.ScopePredicateOnly -and $state.AuthorityActive) {
                return [pscustomobject]@{ SyntheticAuthorityForPredicateContract = $true }
            }
            & $state.OriginalRecoveryAuthority $Context $Lease $Label
        } -Force
        Set-Item Function:\Enter-VerifierRetainedBrowserRecoveryLease -Value {
            param($Context, $Lease)
            $state = $script:RetainedPreviewFixtureState
            $state.EnterCalls++
            [void]$state.Trace.Add('enter')
            $state.ScopePredicateOnly = $true
            $state.AuthorityActive = $true
            if ($state.ChangeProcessAfterEnter) { $state.ProcessMode = 'live' }
        } -Force
        Set-Item Function:\Exit-VerifierRetainedBrowserRecoveryLease -Value {
            param($Lease)
            $script:RetainedPreviewFixtureState.ExitCalls++
        } -Force
        Set-Item Function:\Write-VerifierManifest -Value {
            param($Context)
            $script:RetainedPreviewFixtureState.IntentWrites++
        } -Force
        Set-Item Function:\Complete-VerifierRun -Value {
            param($Context)
            $script:RetainedPreviewFixtureState.CompleteCalls++
            $script:RetainedPreviewFixtureState.CloseCalls++
            return [pscustomobject]@{ Success = $true }
        } -Force
    }
}

function Get-ModuleFixtureState {
    return (& $script:ModuleInfo { $script:RetainedPreviewFixtureState })
}

function Invoke-FixtureFactory($Manifest, [string]$ProcessMode = 'absent',
        [string]$ListenerMode = 'empty') {
    Set-ModuleFixture $Manifest $ProcessMode $ListenerMode
    return (& $script:ModuleInfo {
        param($manifestPath)
        New-VerifierRetainedBrowserRecoveryContext $manifestPath
    } ([string]$Manifest.manifestPath))
}

function Assert-RejectedFixture([string]$Name, $Manifest,
        [string]$ProcessMode = 'absent', [string]$ListenerMode = 'empty',
        [string]$ExpectedTrace = '', [string]$ExpectedMessage = '') {
    $rejected = $false
    $rejectionMessage = ''
    try { [void](Invoke-FixtureFactory $Manifest $ProcessMode $ListenerMode) }
    catch {
        $rejected = (& $script:ModuleInfo { param($failure) Test-VerifierInfrastructureError $failure } $_)
        if ($rejected) {
            $rejectionMessage = [string](& $script:ModuleInfo {
                param($failure)
                Get-VerifierErrorMessage $failure
            } $_)
        }
    }
    Assert-Contract $rejected ('NEGATIVE_NOT_REJECTED_' + $Name)
    $state = Get-ModuleFixtureState
    if ([String]::IsNullOrEmpty($ExpectedTrace)) {
        Assert-Contract ($state.Trace.Count -eq 0 -and $state.DurableCalls -eq 0) `
            ('NEGATIVE_REACHED_DURABLE_OR_LIVE_CENSUS_' + $Name)
    } else {
        Assert-Contract (($state.Trace -join ',') -ceq $ExpectedTrace) `
            ('NEGATIVE_FRESH_CENSUS_ORDER_CHANGED_' + $Name)
        Assert-Contract ($state.DurableCalls -eq 1) `
            ('NEGATIVE_DURABLE_SERVER_CHECK_COUNT_CHANGED_' + $Name)
    }
    Assert-Contract ($state.IntentWrites -eq 0 -and $state.CloseCalls -eq 0) ('NEGATIVE_MUTATION_' + $Name)
    if (-not [String]::IsNullOrEmpty($ExpectedMessage)) {
        Assert-Contract ($rejectionMessage -ceq $ExpectedMessage) `
            ('NEGATIVE_REJECTION_REASON_CHANGED_' + $Name)
    }
}

function Invoke-FreshProof($Context, [string]$ProcessMode, [string]$ListenerMode) {
    $state = Get-ModuleFixtureState
    $state.ProcessMode = $ProcessMode
    $state.ListenerMode = $ListenerMode
    return (& $script:ModuleInfo {
        param($candidateContext)
        Assert-VerifierFreshTerminalPreviewProcessAndListenerAbsence `
            $candidateContext 'synthetic recovery boundary'
    } $Context)
}

function Get-FunctionBodyText($Ast, [string]$Name) {
    $matches = @($Ast.FindAll({
        param($node)
        $node -is [Management.Automation.Language.FunctionDefinitionAst] -and
            $node.Name -ceq $Name
    }, $true))
    if ($matches.Count -ne 1) { throw ('AST_FUNCTION_COUNT_' + $Name) }
    return [string]$matches[0].Body.Extent.Text
}

function Get-Sha256Hex([string]$Path) {
    $stream = [IO.File]::OpenRead($Path)
    try {
        $sha = [Security.Cryptography.SHA256]::Create()
        try {
            return ([BitConverter]::ToString($sha.ComputeHash($stream))).Replace('-', '').ToLowerInvariant()
        }
        finally { $sha.Dispose() }
    }
    finally { $stream.Dispose() }
}

function Find-Ordinal([string]$Text, [string]$Needle, [int]$StartIndex = 0) {
    return $Text.IndexOf($Needle, $StartIndex, [StringComparison]::OrdinalIgnoreCase)
}

function Find-LastOrdinal([string]$Text, [string]$Needle) {
    return $Text.LastIndexOf($Needle, [StringComparison]::OrdinalIgnoreCase)
}

function Find-LastOrdinalBefore([string]$Text, [string]$Needle, [int]$Before) {
    if ($Before -le 0) { return -1 }
    return $Text.LastIndexOf($Needle, $Before - 1, [StringComparison]::OrdinalIgnoreCase)
}

if ([String]::IsNullOrWhiteSpace($ModulePath)) {
    $repositoryRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..\..'))
    $ModulePath = Join-Path $repositoryRoot 'scripts\VerifierIsolation.psm1'
}
$moduleFullPath = [IO.Path]::GetFullPath($ModulePath)
if (-not (Test-Path -LiteralPath $moduleFullPath -PathType Leaf)) {
    throw 'MODULE_FILE_MISSING'
}
$moduleSha = Get-Sha256Hex $moduleFullPath
if (-not [String]::IsNullOrWhiteSpace($ExpectedModuleSha256)) {
    if ($ExpectedModuleSha256 -notmatch '^[0-9a-fA-F]{64}$') {
        throw 'MODULE_SOURCE_EXPECTED_PIN_MALFORMED'
    }
    if ($moduleSha -cne $ExpectedModuleSha256.ToLowerInvariant()) {
        throw 'MODULE_SOURCE_PIN_MISMATCH'
    }
}
$script:ModuleInfo = Import-Module -Name $moduleFullPath -Force -PassThru
$preflightNamespace = Get-FixtureNamespacePathProof
if (Test-Path -LiteralPath $preflightNamespace) {
    throw 'FIXTURE_NAMESPACE_COLLISION'
}
$script:RunRoot = Join-Path $script:FixtureNamespaceRoot $script:RunId
$script:PortLeaseRoot = Join-Path $script:RunRoot 'port-leases'
$script:ProfileRoot = Join-Path $script:RunRoot 'browser\profile-cdp'
$script:EvidenceNamespaceRoot = Join-Path $script:FixtureNamespaceRoot 'evidence'
$script:EvidenceDirectory = Join-Path $script:EvidenceNamespaceRoot $script:RunId
$script:CreatedFixtureDirectories = $false
try {
    New-Item -ItemType Directory -Path $preflightNamespace -ErrorAction Stop | Out-Null
    $script:CreatedFixtureDirectories = $true
    [void][IO.Directory]::CreateDirectory($script:PortLeaseRoot)
    [void][IO.Directory]::CreateDirectory($script:ProfileRoot)
    [void][IO.Directory]::CreateDirectory((Join-Path $script:RunRoot 'server'))
    [void][IO.Directory]::CreateDirectory($script:EvidenceDirectory)
    Install-ModuleFixtureMocks

$oldManifest = New-Fixture 'old'
$oldResult = Invoke-FixtureFactory $oldManifest
$oldState = Get-ModuleFixtureState
Assert-Contract ($oldResult.Mode -ceq 'bound') 'OLD_BOUND_MODE_CHANGED'
Assert-Contract ($oldResult.Context.BaseUrl -ceq '') 'OLD_BASE_URL_CHANGED'
Assert-Contract ($oldResult.Context.LeaseRecords.Count -eq 1) 'OLD_LEASE_COUNT_CHANGED'
Assert-Contract ($null -eq $oldResult.Context.Server.Lease) 'OLD_SERVER_LEASE_CHANGED'
Assert-Contract ($oldState.Trace.Count -eq 0 -and $oldState.DurableCalls -eq 0) `
    'OLD_PATH_PERFORMED_UNEXPECTED_DURABLE_OR_PREVIEW_CENSUS'
Assert-Contract (@($oldState.Trace | Where-Object {
        $_ -ceq 'process' -or $_ -ceq 'listener'
    }).Count -eq 0) 'OLD_PATH_PERFORMED_PREVIEW_PROCESS_OR_LISTENER_CENSUS'

$issuedManifest = Set-IssuedContainedFixture (New-Fixture 'old')
$issuedResult = Invoke-FixtureFactory $issuedManifest
$issuedState = Get-ModuleFixtureState
Assert-Contract ($issuedResult.Mode -ceq 'issued-contained') 'ISSUED_CONTAINED_MODE_NOT_SELECTED'
Assert-Contract ($issuedResult.Context.BaseUrl -ceq '' -and
    $issuedResult.Context.Server.Owner -ceq 'none') 'ISSUED_CONTAINED_OWNERLESS_SERVER_CHANGED'
Assert-Contract ($issuedResult.Context.LeaseRecords.Count -eq 1 -and
    $issuedResult.Context.LeaseRecords[0].Kind -ceq 'cdp' -and
    $issuedResult.Context.LeaseRecords[0].Status -ceq 'leased' -and
    $issuedResult.Context.LeaseRecords[0].ClaimState -ceq 'held' -and
    -not $issuedResult.Context.LeaseRecords[0].ProcessProofRequired) `
    'ISSUED_CONTAINED_LEASE_SHAPE_CHANGED'
Assert-Contract ($issuedResult.Session.RecoveryReceipt.State -ceq 'issued' -and
    -not $issuedResult.Session.RecoveryReceipt.CloseAttempted) `
    'ISSUED_CONTAINED_RECEIPT_SHAPE_CHANGED'
Assert-Contract ($issuedResult.Session.ProcessId -eq 0 -and
    $issuedResult.Session.ProcessStartTicks -eq 0 -and
    $issuedResult.Session.ProcessParentProcessId -eq 0 -and
    $issuedResult.Session.ProcessParentProcessStartTicks -eq 0 -and
    [String]::IsNullOrEmpty($issuedResult.Session.ProcessCommandLine)) `
    'ISSUED_CONTAINED_SESSION_WAS_NOT_ROOTLESS'
Assert-Contract ($issuedResult.Session.ContainmentLaunch.State -ceq 'launched' -and
    $issuedResult.Session.ContainmentLaunch.LaunchProcessId -eq $script:BrowserPid -and
    $issuedResult.Session.ContainmentLaunch.LaunchedUtc -ceq $script:CreatedUtc) `
    'ISSUED_CONTAINED_LAUNCH_LEDGER_SHAPE_CHANGED'
Assert-Contract ($issuedState.Trace.Count -eq 0 -and $issuedState.DurableCalls -eq 0) `
    'ISSUED_CONTAINED_ADMISSION_PERFORMED_UNEXPECTED_CENSUS'
Assert-Contract ($issuedState.IntentWrites -eq 0 -and $issuedState.CloseCalls -eq 0) `
    'ISSUED_CONTAINED_ADMISSION_MUTATED_RETAINED_STATE'

if (-not $LegacyOnly) {

$terminalManifest = New-Fixture 'terminal'
$terminalPreviewId = [string]$terminalManifest.leases[1].leaseId
$parsedTerminalPreviewId = [Guid]::Empty
$terminalPreviewIdIsCanonical = [Guid]::TryParseExact($terminalPreviewId, 'N',
    [ref]$parsedTerminalPreviewId) -and
    $parsedTerminalPreviewId -ne [Guid]::Empty -and
    $terminalPreviewId -ceq $parsedTerminalPreviewId.ToString('N')
Assert-Contract $terminalPreviewIdIsCanonical 'POSITIVE_PREVIEW_LEASE_ID_NOT_NONEMPTY_LOWERCASE_GUID_N'
$terminalResult = Invoke-FixtureFactory $terminalManifest
$terminalState = Get-ModuleFixtureState
Assert-Contract ($terminalResult.Mode -ceq 'bound-terminal-preview') 'TERMINAL_MODE_NOT_SELECTED'
Assert-Contract ($terminalResult.Context.BaseUrl -ceq $terminalManifest.baseUrl) 'HISTORICAL_BASE_URL_NOT_PRESERVED'
Assert-Contract ($terminalResult.Context.LeaseRecords.Count -eq 2) 'TERMINAL_LEASE_RECORD_COUNT'
Assert-Contract ($terminalResult.Context.LeaseRecords[0].Kind -ceq 'cdp' -and
    $terminalResult.Context.LeaseRecords[1].Kind -ceq 'preview') 'TERMINAL_LEASE_ORDER_CHANGED'
Assert-Contract ([object]::ReferenceEquals($terminalResult.Context.Server.Lease,
    $terminalResult.Context.LeaseRecords[1])) 'SERVER_PREVIEW_LEASE_REFERENCE_NOT_PRESERVED'
Assert-Contract ([object]::ReferenceEquals($terminalResult.Session.Lease,
    $terminalResult.Context.LeaseRecords[0])) 'SESSION_CDP_LEASE_REFERENCE_NOT_PRESERVED'
Assert-Contract ($terminalState.Trace -join ',' -ceq 'durable-server-lease,process,listener') `
    'TERMINAL_PREFLIGHT_ORDER_OR_CENSUS_CHANGED'
Assert-Contract ($terminalState.DurableCalls -eq 1) 'TERMINAL_DURABLE_ASSOCIATION_NOT_VALIDATED'

$terminalIssuedManifest = Set-IssuedContainedFixture (New-Fixture 'terminal')
Assert-RejectedFixture 'terminal-issued-contained' $terminalIssuedManifest `
    'absent' 'empty' '' `
    'VERIFIER_INFRASTRUCTURE: Terminal preview recovery requires exactly one already-bound, close-unattempted CDP session.'

$selectorState = & $script:ModuleInfo {
    param($Context, $Session)
    Test-VerifierRetainedTerminalPreviewRecoveryScope $Context $Session
} $terminalResult.Context $terminalResult.Session
Assert-Contract (-not [bool]$selectorState) 'TERMINAL_SCOPE_SELECTOR_GRANTED_WITHOUT_RECOVERY_AUTHORITY'
$selectorFixtureState = Get-ModuleFixtureState
$selectorFixtureState.ScopePredicateOnly = $true
$selectorFixtureState.AuthorityActive = $true
$selectorState = & $script:ModuleInfo {
    param($Context, $Session)
    Test-VerifierRetainedTerminalPreviewRecoveryScope $Context $Session
} $terminalResult.Context $terminalResult.Session
Assert-Contract ([bool]$selectorState) 'TERMINAL_SCOPE_SELECTOR_REJECTED_VALID_CONTEXT'
$oldSelectorState = & $script:ModuleInfo {
    param($Context, $Session)
    Test-VerifierRetainedTerminalPreviewRecoveryScope $Context $Session
} $oldResult.Context $oldResult.Session
Assert-Contract (-not [bool]$oldSelectorState) 'OWNERLESS_SCOPE_SELECTOR_CHANGED'

$selectorNegativeCases = @(
    [pscustomobject]@{ Name='selector-extra-lease'; Apply={ param($c) $c.LeaseRecords=@($c.LeaseRecords[0],$c.LeaseRecords[1],$c.LeaseRecords[0]) } }
    [pscustomobject]@{ Name='selector-missing-preview-reference'; Apply={ param($c) $c.Server.Lease=$null } }
    [pscustomobject]@{ Name='selector-cdp-reference-mismatch'; Apply={ param($c) $c.BrowserSessions[0].Lease=$c.LeaseRecords[1] } }
    [pscustomobject]@{ Name='selector-base-url-mismatch'; Apply={ param($c) $c.BaseUrl+='x' } }
)
foreach ($case in $selectorNegativeCases) {
    $freshResult = Invoke-FixtureFactory (New-Fixture 'terminal')
    $state = Get-ModuleFixtureState
    $state.ScopePredicateOnly = $true
    $state.AuthorityActive = $true
    & $case.Apply $freshResult.Context
    $rejected = $false
    try {
        [void](& $script:ModuleInfo {
            param($Context, $Session)
            Test-VerifierRetainedTerminalPreviewRecoveryScope $Context $Session
        } $freshResult.Context $freshResult.Session)
    } catch { $rejected = (& $script:ModuleInfo { param($failure) Test-VerifierInfrastructureError $failure } $_) }
    Assert-Contract $rejected ('SCOPE_SELECTOR_ACCEPTED_' + $case.Name)
}

foreach ($badModes in @(
        @('live', 'empty'), @('replaced', 'empty'), @('unknown', 'empty'),
        @('absent', 'live'), @('absent', 'unknown'))) {
    $rejected = $false
    try { [void](Invoke-FreshProof $terminalResult.Context $badModes[0] $badModes[1]) }
    catch { $rejected = (& $script:ModuleInfo { param($failure) Test-VerifierInfrastructureError $failure } $_) }
    Assert-Contract $rejected ('FRESH_BOUNDARY_ACCEPTED_' + $badModes[0] + '_' + $badModes[1])
}
$freshState = Get-ModuleFixtureState
Assert-Contract ($freshState.Trace.Count -ge 8) 'FRESH_BOUNDARY_NEGATIVE_OBSERVATIONS_MISSING'

$negativeMutators = @(
    [pscustomobject]@{ Name='base-url-mismatch'; Apply={ param($x) $x.baseUrl += 'x' } }
    [pscustomobject]@{ Name='server-owner-caller'; Apply={ param($x) $x.server.owner='caller' } }
    [pscustomobject]@{ Name='server-state-active'; Apply={ param($x) $x.server.state='running' } }
    [pscustomobject]@{ Name='server-cleanup-incomplete'; Apply={ param($x) $x.server.cleanupResult='infrastructure-failure' } }
    [pscustomobject]@{ Name='server-identity-unverified'; Apply={ param($x) $x.server.identityVerified=$false } }
    [pscustomobject]@{ Name='server-caller-owned'; Apply={ param($x) $x.server.callerOwned=$true } }
    [pscustomobject]@{ Name='server-ownership-uncertain'; Apply={ param($x) $x.server.ownershipUncertain=$true } }
    [pscustomobject]@{ Name='server-termination-unproven'; Apply={ param($x) $x.server.processTerminationProven=$false } }
    [pscustomobject]@{ Name='server-process-present'; Apply={ param($x) $x.server.processAbsent=$false } }
    [pscustomobject]@{ Name='server-listener-unproven'; Apply={ param($x) $x.server.listenerInspectionProven=$false } }
    [pscustomobject]@{ Name='server-listener-present'; Apply={ param($x) $x.server.listenerAbsent=$false } }
    [pscustomobject]@{ Name='server-port-mismatch'; Apply={ param($x) $x.server.port++ } }
    [pscustomobject]@{ Name='server-run-mismatch'; Apply={ param($x) $x.server.runId='22222222222222222222222222222222' } }
    [pscustomobject]@{ Name='server-repository-mismatch'; Apply={ param($x) $x.server.repositoryIdentity=('d'*64) } }
    [pscustomobject]@{ Name='server-webroot-mismatch'; Apply={ param($x) $x.server.webRoot += '\foreign' } }
    [pscustomobject]@{ Name='preview-active'; Apply={ param($x) $x.leases[1].status='bound'; $x.leases[1].claimState='bound'; $x.leases[1].releaseState='active'; $x.leases[1].releaseJournalState='active'; $x.leases[1].mutexReleased=$false } }
    [pscustomobject]@{ Name='preview-release-blocked'; Apply={ param($x) $x.leases[1].releaseBlocked=$true } }
    [pscustomobject]@{ Name='preview-release-journal'; Apply={ param($x) $x.leases[1].releaseJournalState='pre-delete' } }
    [pscustomobject]@{ Name='preview-process-proof-missing'; Apply={ param($x) $x.leases[1].processTerminationProven=$false } }
    [pscustomobject]@{ Name='preview-process-not-absent'; Apply={ param($x) $x.leases[1].processAbsent=$false } }
    [pscustomobject]@{ Name='preview-listener-not-absent'; Apply={ param($x) $x.leases[1].listenerAbsent=$false } }
    [pscustomobject]@{ Name='preview-run-mismatch'; Apply={ param($x) $x.leases[1].runId='22222222222222222222222222222222' } }
    [pscustomobject]@{ Name='preview-repository-mismatch'; Apply={ param($x) $x.leases[1].repositoryIdentity=('d'*64) } }
    [pscustomobject]@{ Name='preview-port-mismatch'; Apply={ param($x) $x.leases[1].port=45223; $x.leases[1].claim.port=45223 } }
    [pscustomobject]@{ Name='preview-path-mismatch'; Apply={ param($x) $x.leases[1].path += '.foreign' } }
    [pscustomobject]@{ Name='preview-self-consistent-noncanonical-path'; Apply={ param($x)
        $foreign = $x.leases[1].path + '.foreign'
        $x.leases[1].path = $foreign; $x.leases[1].claim.path = $foreign
        $x.server.leasePath = $foreign
    } }
    [pscustomobject]@{ Name='preview-guid-d-separator-alias'; Apply={ param($x)
        Set-PreviewLeaseIdentityAlias $x '01234567-89ab-cdef-0123-456789abcdef'
    } }
    [pscustomobject]@{ Name='preview-dot-segment-alias'; Apply={ param($x)
        Set-PreviewLeaseIdentityAlias $x 'preview\..\alias'
    } }
    [pscustomobject]@{ Name='preview-guid-empty-alias'; Apply={ param($x)
        Set-PreviewLeaseIdentityAlias $x '00000000000000000000000000000000'
    } }
    [pscustomobject]@{ Name='preview-guid-n-uppercase-alias'; Apply={ param($x)
        Set-PreviewLeaseIdentityAlias $x '0123456789ABCDEF0123456789ABCDEF'
    } }
    [pscustomobject]@{ Name='preview-lease-id-mismatch'; Apply={ param($x) $x.leases[1].leaseId='foreign-preview'; $x.leases[1].claim.leaseId='foreign-preview' } }
    [pscustomobject]@{ Name='preview-owner-tuple-mismatch'; Apply={ param($x) $x.server.leaseOwnerStartTicks++ } }
    [pscustomobject]@{ Name='duplicate-preview-lease'; Apply={ param($x) $x.leases=@($x.leases[0],$x.leases[1],$x.leases[1]) } }
    [pscustomobject]@{ Name='missing-preview-lease'; Apply={ param($x) $x.leases=@($x.leases[0]) } }
    [pscustomobject]@{ Name='duplicate-cdp-lease'; Apply={ param($x) $x.leases=@($x.leases[0],$x.leases[1],$x.leases[0]) } }
    [pscustomobject]@{ Name='extra-session'; Apply={ param($x) $x.browserSessions=@($x.browserSessions[0],$x.browserSessions[0]) } }
    [pscustomobject]@{ Name='close-attempted'; Apply={ param($x) $x.browserSessions[0].recoveryReceipt.closeAttempted=$true; $x.browserSessions[0].recoveryReceipt.closeAttemptedUtc=$script:CreatedUtc } }
    [pscustomobject]@{ Name='receipt-run-mismatch'; Apply={ param($x) $x.browserSessions[0].recoveryReceipt.runId='22222222222222222222222222222222' } }
    [pscustomobject]@{ Name='session-cdp-port-mismatch'; Apply={ param($x) $x.browserSessions[0].cdpPort++ } }
    [pscustomobject]@{ Name='session-profile-mismatch'; Apply={ param($x) $x.browserSessions[0].profile += '\foreign' } }
    [pscustomobject]@{ Name='cleanup-unknown'; Apply={ param($x) $x.cleanup.state='complete' } }
)

foreach ($case in $negativeMutators) {
    $manifest = New-Fixture 'terminal'
    & $case.Apply $manifest
    Assert-RejectedFixture $case.Name $manifest
}

$processFailureCases = @(
    [pscustomobject]@{ Name='current-preview-process'; Process='live'; Listener='empty'; Trace='durable-server-lease,process' }
    [pscustomobject]@{ Name='preview-pid-reused'; Process='replaced'; Listener='empty'; Trace='durable-server-lease,process' }
    [pscustomobject]@{ Name='preview-process-query-unknown'; Process='unknown'; Listener='empty'; Trace='durable-server-lease,process' }
    [pscustomobject]@{ Name='preview-listener-present'; Process='absent'; Listener='live'; Trace='durable-server-lease,process,listener' }
    [pscustomobject]@{ Name='preview-listener-query-unknown'; Process='absent'; Listener='unknown'; Trace='durable-server-lease,process,listener' }
)
foreach ($case in $processFailureCases) {
    Assert-RejectedFixture $case.Name (New-Fixture 'terminal') $case.Process $case.Listener $case.Trace
}

$claimAndIntentManifest = New-Fixture 'terminal'
Set-ModuleFixture $claimAndIntentManifest 'absent' 'empty'
$stateBeforeRecovery = Get-ModuleFixtureState
$stateBeforeRecovery.ChangeProcessAfterEnter = $true
$invokeRejected = $false
try {
    [void](& $script:ModuleInfo {
        param($manifestPath)
        Invoke-VerifierRetainedBrowserRecovery $manifestPath
    } ([string]$claimAndIntentManifest.manifestPath))
} catch {
    $invokeRejected = (& $script:ModuleInfo { param($failure) Test-VerifierInfrastructureError $failure } $_)
}
$recoveryState = Get-ModuleFixtureState
Assert-Contract $invokeRejected 'AFTER_CLAIM_PROCESS_REAPPEARANCE_ACCEPTED'
Assert-Contract ($recoveryState.EnterCalls -eq 1 -and $recoveryState.ExitCalls -eq 1) `
    'AFTER_CLAIM_GUARD_DID_NOT_BRACKET_OWNED_CLAIM'
Assert-Contract ($recoveryState.IntentWrites -eq 0 -and $recoveryState.CompleteCalls -eq 0 -and
    $recoveryState.CloseCalls -eq 0) 'AFTER_CLAIM_FAILURE_REACHED_INTENT_OR_CLOSE'
Assert-Contract (($recoveryState.Trace -join ',') -ceq
    'durable-server-lease,process,listener,enter,durable-server-lease,process') `
    'AFTER_CLAIM_FRESH_CENSUS_NOT_OBSERVED'

$tokens = $null
$parseErrors = $null
$ast = [Management.Automation.Language.Parser]::ParseFile($moduleFullPath, [ref]$tokens, [ref]$parseErrors)
Assert-Contract ($parseErrors.Count -eq 0) 'MODULE_PARSE_FAILED'
$factoryBody = Get-FunctionBodyText $ast 'New-VerifierRetainedBrowserRecoveryContext'
$previewGuidParse = Find-Ordinal $factoryBody "[Guid]::TryParseExact([string]`$previewLease.LeaseId, 'N'"
$previewGuidEmpty = Find-Ordinal $factoryBody '$canonicalPreviewLeaseId -eq [Guid]::Empty'
$previewGuidCanonical = Find-Ordinal $factoryBody "`$previewLease.LeaseId -cne `$canonicalPreviewLeaseId.ToString('N')"
$previewClaimJoin = Find-Ordinal $factoryBody '$expectedPreviewClaimPath = Get-VerifierFullPath (Join-Path'
Assert-Contract ($previewGuidParse -ge 0 -and $previewGuidEmpty -gt $previewGuidParse -and
    $previewGuidCanonical -gt $previewGuidEmpty -and $previewClaimJoin -gt $previewGuidCanonical) `
    'PREVIEW_GUID_N_VALIDATION_NOT_BEFORE_CLAIM_PATH_JOIN'
$factoryFresh = Find-Ordinal $factoryBody 'Assert-VerifierFreshTerminalPreviewProcessAndListenerAbsence'
$factoryReturn = Find-Ordinal $factoryBody 'return [pscustomobject]@{'
Assert-Contract ($factoryFresh -ge 0 -and $factoryReturn -gt $factoryFresh) 'FACTORY_CENSUS_NOT_BEFORE_RETURN'
$invokeBody = Get-FunctionBodyText $ast 'Invoke-VerifierRetainedBrowserRecovery'
$invokeEnter = Find-Ordinal $invokeBody 'Enter-VerifierRetainedBrowserRecoveryLease'
$invokeScope = Find-Ordinal $invokeBody 'Test-VerifierRetainedTerminalPreviewRecoveryScope'
$invokeFresh = Find-Ordinal $invokeBody 'Assert-VerifierFreshTerminalPreviewProcessAndListenerAbsence'
$invokeIntent = Find-Ordinal $invokeBody '$context.CleanupState = ''pending'''
$invokeWrite = Find-Ordinal $invokeBody 'Write-VerifierManifest'
Assert-Contract ($invokeEnter -ge 0 -and $invokeScope -gt $invokeEnter -and
    $invokeFresh -gt $invokeScope -and $invokeIntent -gt $invokeFresh -and
    $invokeWrite -gt $invokeIntent) 'POST_CLAIM_FRESH_GUARD_ORDER_CHANGED'
$closeBody = Get-FunctionBodyText $ast 'Close-VerifierBrowserSessionWithBoundRecoveryReceipt'
$closeJournal = Find-Ordinal $closeBody 'Record-VerifierBrowserRecoveryCloseAttempt'
$closeRootAtSend = Find-LastOrdinal $closeBody 'Get-VerifierBrowserReceiptNaturalShutdownRoot'
$closeJobAtSend = Find-LastOrdinal $closeBody 'Assert-VerifierBrowserContainmentJobContainsRoot'
$closeScope = Find-Ordinal $closeBody 'Test-VerifierRetainedTerminalPreviewRecoveryScope'
$closeFresh = Find-Ordinal $closeBody 'Assert-VerifierFreshTerminalPreviewProcessAndListenerAbsence'
$closeSend = Find-Ordinal $closeBody 'Send-VerifierBrowserCloseAndReadAcknowledgement'
$closeDeadlineBeforeFresh = Find-LastOrdinalBefore $closeBody 'Assert-VerifierBrowserShutdownDeadline' $closeFresh
$closeDeadlineAfterFresh = if ($closeSend -gt 0) {
    Find-Ordinal $closeBody 'Assert-VerifierBrowserShutdownDeadline' ($closeFresh + 1)
} else { -1 }
Assert-Contract ($closeScope -ge 0 -and $closeFresh -gt $closeScope -and
    $closeDeadlineBeforeFresh -gt $closeScope -and $closeDeadlineBeforeFresh -lt $closeFresh -and
    $closeDeadlineAfterFresh -gt $closeFresh -and $closeJournal -gt $closeDeadlineAfterFresh -and
    $closeRootAtSend -gt $closeJournal -and $closeJobAtSend -gt $closeRootAtSend -and
    $closeSend -gt $closeJobAtSend) `
    'PRE_CLOSE_FRESH_GUARD_OR_DEADLINE_ORDER_CHANGED'

}
}
finally {
    if ($script:CreatedFixtureDirectories) {
        $fullNamespace = Get-FixtureNamespacePathProof -RequireNamespaceDirectory
        $reparse = @(Get-ChildItem -LiteralPath $fullNamespace -Force -Recurse -ErrorAction Stop |
            Where-Object { $_.Attributes -band [IO.FileAttributes]::ReparsePoint })
        if ($reparse.Count -ne 0) { throw 'FIXTURE_CLEANUP_REPARSE_NOT_PROVEN' }
        Remove-Item -LiteralPath $fullNamespace -Recurse -Force -ErrorAction Stop
    }
}
$moduleShaAfter = Get-Sha256Hex $moduleFullPath
if ($moduleShaAfter -cne $moduleSha) {
    throw 'MODULE_SOURCE_CHANGED_DURING_FIXTURE'
}
Write-Output ('VERIFIER_ISOLATION_SOURCE_SHA256 before=' + $moduleSha + ' after=' + $moduleShaAfter)
if ($LegacyOnly) {
    Write-Output ('PASS: retained preview recovery legacy ownerless fixtures; assertions=' +
        $script:Assertions + '; modes=bound,issued-contained; preview-census=NOT_RUN; host-recovery=NOT_RUN')
} else {
    Write-Output ('PASS: retained preview recovery fixture contract; assertions=' + $script:Assertions +
        '; scope=synthetic-fixtures-and-source-order-only; host-recovery=NOT_RUN')
}
