
[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [ValidateSet('TerminalPreview', 'Ownerless')]
    [string]$Case,
    [string]$RepositoryRoot = '',
    [Parameter(Mandatory = $true)]
    [string]$EvidenceDirectory,
    [string]$BrowserPath = '',
    [string]$ExpectedModuleSha256 = ''
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$script:ParentScriptPath = $PSCommandPath
$script:RepositoryRoot = ''
$script:EvidenceRoot = ''
$script:TaskNonce = ''
$script:ModulePath = ''
$script:PreviewScriptPath = ''
$script:ChildScriptPath = ''
$script:ReportPath = ''
$script:Stage = 'preflight'
$script:Failure = $null
$script:ExitCode = 2
$script:ChildResult = $null
$script:ChildEnvelope = $null
$script:ChildWitness = $null
$script:ManifestBefore = $null
$script:ManifestAfter = $null
$script:RecoveryResult = $null
$script:RecoveryElapsedMs = $null
$script:RecoveryAttempted = $false
$script:RecoveryInvokedInFinally = $false
$script:RecoveryEligibility = $null
$script:RecoveryManifestPath = ''
$script:RecoverySafeToAttempt = $false
$script:RecoverySkipReason = ''
$script:RecoveryFailure = $null
$script:SourceStable = $false
$script:PreRecoveryEligibilityProven = $false
$script:WitnessBindingProven = $false
$script:FinalProofs = [ordered]@{}
$script:AggregateWatch = [Diagnostics.Stopwatch]::StartNew()
$script:Report = [ordered]@{
    protocol = 'troubleshootjs-terminal-preview-recovery-result-v1'
    case = $Case
    status = 'FAIL'
    selectedHost = [ordered]@{
        required = 'WindowsPowerShell 5.1 Desktop'
        actual = $PSVersionTable.PSVersion.ToString()
        edition = [string]$PSVersionTable.PSEdition
    }
    expectedRecoveryMode = if ($Case -ceq 'TerminalPreview') { 'bound-terminal-preview' } else { 'bound' }
    sourcePinsBefore = $null
    sourcePinsAfterChild = $null
    sourcePinsAfterModuleImport = $null
    sourcePinsAfterRecovery = $null
    sourceStable = $false
    preRecoveryEligibilityProven = $false
    child = [ordered]@{
        resultReturned = $false
        processId = 0
        processStartTicks = 0L
        terminationProven = $false
        exitCode = $null
        status = ''
        runId = ''
        outputCapture = $null
    }
    witness = $null
    manifest = $null
    recovery = [ordered]@{
        attempted = $false
        success = $false
        entryPointReturnedSuccess = $false
        finalManifestProofSucceeded = $false
        operationElapsedMs = $null
        cleanupUpperBoundWithin120s = $null
        invokedInFinally = $false
        finallyEligibilityRevalidated = $false
        skipReason = ''
        fallbackFailure = $null
        resourceDisposition = 'unproven_not_assessed'
        exactWitnessPath = ''
        taskEvidenceDirectory = ''
        preRecoveryEligibilityProven = $false
        witnessBindingProven = $false
        candidateWitnessPath = ''
        candidateWitnessPathConstrainedToTaskEvidence = $false
        localWatchdog = $false
        requiredCallerOuterBoundMs = 600000
        callerMustBoundThisProcess = $true
    }
    cleanupComponents = [ordered]@{
        previewOwnerCleanupElapsedMs = $null
        retainedBrowserRecoveryOperationElapsedMs = $null
        sumOfMeasuredOperationIntervalsMs = $null
        sumIsContinuousWallClock = $false
    }
    proofs = $script:FinalProofs
    timing = [ordered]@{
        aggregateBudgetMs = 600000
        aggregateElapsedMs = $null
        browserNaturalShutdownBudgetMs = 15000
        completeRunCleanupBudgetMs = 120000
        recoveryOperationIsCleanupUpperBound = $true
        exactRecoveryCleanupSubphaseMs = $null
        childSetupElapsedMs = $null
        childSetupIncludesPreviewTerminalization = ($Case -ceq 'TerminalPreview')
        localCancellationWatchdog = $false
        timingNote = 'The maintained retained-recovery entry point is synchronous and does not return nested Complete-VerifierRun timings. Its full operation interval is recorded as an upper bound; the caller must bound this process to 600000 ms. Child setup is reported separately. The sum of preview-owner cleanup and retained recovery operation intervals is not a continuous wall-clock interval.'
    }
    failure = $null
}

function Get-FileSha256([string]$Path) {
    $stream = $null
    $hasher = $null
    try {
        $stream = [IO.File]::OpenRead($Path)
        $hasher = [Security.Cryptography.SHA256]::Create()
        $bytes = $hasher.ComputeHash($stream)
        return ([BitConverter]::ToString($bytes).Replace('-', '').ToLowerInvariant())
    } finally {
        if ($null -ne $hasher) { $hasher.Dispose() }
        if ($null -ne $stream) { $stream.Dispose() }
    }
}

function Get-StringSha256([string]$Value) {
    $bytes = [Text.Encoding]::UTF8.GetBytes($Value)
    $hasher = $null
    try {
        $hasher = [Security.Cryptography.SHA256]::Create()
        return ([BitConverter]::ToString($hasher.ComputeHash($bytes)).Replace('-', '').ToLowerInvariant())
    } finally {
        if ($null -ne $hasher) { $hasher.Dispose() }
    }
}

function Write-PrivateChildStreamFile([string]$EvidenceRoot, [string]$FileName, [string]$Text) {
    Assert-Contract ($FileName -ceq 'child.stdout.txt' -or $FileName -ceq 'child.stderr.txt' -or $FileName -ceq 'child-stream-capture.json') 'CHILD_STREAM_ARTIFACT_NAME'
    $path = Join-Path $EvidenceRoot $FileName
    Assert-Contract (Test-VerifierChildPath $EvidenceRoot $path) 'CHILD_STREAM_ARTIFACT_PATH'
    [void](Assert-VerifierNoReparseAncestors $EvidenceRoot)
    $bytes = [Text.UTF8Encoding]::new($false).GetBytes([string]$Text)
    $stream = $null
    try {
        $stream = [IO.File]::Open($path, [IO.FileMode]::CreateNew, [IO.FileAccess]::Write, [IO.FileShare]::None)
        $stream.Write($bytes, 0, $bytes.Length)
        $stream.Flush($true)
    } finally {
        if ($null -ne $stream) { $stream.Dispose() }
    }
    [void](Assert-VerifierPhysicalOwnedPath $EvidenceRoot $path)
    $length = [long](Get-Item -LiteralPath $path).Length
    Assert-Contract ($length -eq [long]$bytes.Length) 'CHILD_STREAM_ARTIFACT_LENGTH'
    return [ordered]@{
        file = $FileName
        byteCount = $length
        sha256 = Get-FileSha256 $path
    }
}

function Get-SourcePinSet {
    return [ordered]@{
        moduleSha256 = Get-FileSha256 $script:ModulePath
        previewSha256 = Get-FileSha256 $script:PreviewScriptPath
        parentTestSha256 = Get-FileSha256 $script:ParentScriptPath
        childTestSha256 = Get-FileSha256 $script:ChildScriptPath
    }
}

function Test-SourcePinsEqual($Left, $Right) {
    foreach ($name in @('moduleSha256', 'previewSha256', 'parentTestSha256', 'childTestSha256')) {
        if (-not [string]$Left[$name] -or -not [string]$Right[$name] -or
                -not ([string]$Left[$name]).Equals([string]$Right[$name], [StringComparison]::OrdinalIgnoreCase)) {
            return $false
        }
    }
    return $true
}

function Get-FailureIdentity($ErrorRecord) {
    if ($null -eq $ErrorRecord) { return [ordered]@{ type = 'Unknown'; id = 'UNKNOWN' } }
    $errorId = [string]$ErrorRecord.FullyQualifiedErrorId
    if ([String]::IsNullOrWhiteSpace($errorId)) { $errorId = 'UNSPECIFIED' }
    $errorId = ($errorId -split '[,;\r\n]')[0]
    $errorId = [regex]::Replace($errorId, '[^A-Za-z0-9_.-]', '_')
    if ($errorId.Length -gt 120) { $errorId = $errorId.Substring(0, 120) }
    $typeName = if ($null -ne $ErrorRecord.Exception) { [string]$ErrorRecord.Exception.GetType().Name } else { 'Unknown' }
    return [ordered]@{ type = $typeName; id = $errorId }
}

function Assert-Contract([bool]$Condition, [string]$Code) {
    if (-not $Condition) { throw [InvalidOperationException]::new($Code) }
}

function Assert-TrueBoolean($Object, [string]$Name) {
    $property = $Object.PSObject.Properties[$Name]
    Assert-Contract ($null -ne $property -and $property.Value -is [bool] -and [bool]$property.Value) ('PROOF_TRUE_' + $Name)
}

function Assert-FalseBoolean($Object, [string]$Name) {
    $property = $Object.PSObject.Properties[$Name]
    Assert-Contract ($null -ne $property -and $property.Value -is [bool] -and -not [bool]$property.Value) ('PROOF_FALSE_' + $Name)
}

function Assert-WitnessBinding($Witness, $Envelope, [string]$WitnessPath) {
    Assert-Contract ([string]$Witness.taskNonce -ceq $script:TaskNonce -and
        [string]$Envelope.taskNonce -ceq $script:TaskNonce) 'TASK_NONCE_BINDING'
    Assert-Contract ([string]$Witness.case -ceq $Case -and
        [string]$Envelope.case -ceq $Case) 'SELECTED_CASE_BINDING'
    Assert-Contract ([string]$Envelope.runId -ceq [string]$Witness.context.runId) 'CHILD_RUN_ID_BINDING'
    $runGuid = [Guid]::Empty
    Assert-Contract ([Guid]::TryParseExact([string]$Witness.context.runId, 'N', [ref]$runGuid)) 'CHILD_RUN_ID_FORMAT'
    $root = Get-VerifierFullPath $script:RepositoryRoot
    $repositoryIdentity = Get-VerifierRepositoryIdentity $root
    Assert-Contract ([string]$Witness.context.repositoryIdentity -ceq $repositoryIdentity -and
        (Test-VerifierCanonicalWindowsPathValue ([string]$Witness.context.worktreeRoot) $root)) 'CHILD_REPOSITORY_BINDING'
    $tempRoot = Get-VerifierFullPath ([IO.Path]::GetTempPath())
    $verifyRoot = Join-Path (Join-Path $tempRoot 'TroubleshootJS') 'verify'
    $expectedNamespace = Join-Path $verifyRoot $repositoryIdentity
    $expectedRunRoot = Join-Path $expectedNamespace ([string]$Witness.context.runId)
    $expectedManifest = Join-Path $expectedRunRoot 'manifest.json'
    $expectedLeaseRoot = Join-Path $expectedRunRoot 'port-leases'
    $expectedEvidence = Join-Path $script:EvidenceRoot ('run-' + [string]$Witness.context.runId)
    Assert-Contract ((Test-VerifierCanonicalWindowsPathValue ([string]$Witness.context.evidenceNamespaceRoot) $script:EvidenceRoot) -and
        (Test-VerifierCanonicalWindowsPathValue ([string]$Witness.context.runNamespaceRoot) $expectedNamespace) -and
        (Test-VerifierCanonicalWindowsPathValue ([string]$Witness.context.runRoot) $expectedRunRoot) -and
        (Test-VerifierCanonicalWindowsPathValue ([string]$Witness.context.manifestPath) $expectedManifest) -and
        (Test-VerifierCanonicalWindowsPathValue ([string]$Witness.context.portLeaseRoot) $expectedLeaseRoot) -and
        (Test-VerifierCanonicalWindowsPathValue ([string]$Witness.context.evidenceDirectory) $expectedEvidence)) 'CHILD_RUN_NAMESPACE_BINDING'
    Assert-Contract (Test-VerifierChildPath $script:EvidenceRoot $WitnessPath) 'WITNESS_OUTSIDE_SELECTED_EVIDENCE_ROOT'
    $expectedWitnessName = switch ([string]$Witness.phase) {
        'context-created' { 'witness-start.json' }
        'preview-ready' { 'witness-preview-ready.json' }
        'browser-attached' { 'witness-browser-attached.json' }
        'ready' { 'witness-ready.json' }
        'failure' { 'witness-failure.json' }
        default { '' }
    }
    Assert-Contract (-not [String]::IsNullOrWhiteSpace($expectedWitnessName) -and
        (Test-VerifierCanonicalWindowsPathValue $WitnessPath (Join-Path $expectedEvidence $expectedWitnessName))) 'WITNESS_PHASE_PATH_BINDING'
    Assert-Contract ([string]$Envelope.status -ceq 'READY' -or [string]$Envelope.status -ceq 'FAIL') 'CHILD_RESULT_STATUS_INVALID'
    if ([string]$Envelope.status -ceq 'READY') {
        Assert-Contract ([string]$Witness.phase -ceq 'ready' -and
            [string]$Witness.outcome -ceq 'READY_FOR_PARENT_RECOVERY') 'READY_WITNESS_OUTCOME_BINDING'
    } else {
        Assert-Contract ([string]$Envelope.status -ceq 'FAIL') 'FAILURE_WITNESS_STATUS_BINDING'
    }
}

function Read-DurableManifest([string]$ManifestPath) {
    Assert-Contract (Test-Path -LiteralPath $ManifestPath -PathType Leaf) 'MANIFEST_FILE_MISSING'
    $text = [IO.File]::ReadAllText($ManifestPath)
    return (ConvertFrom-VerifierDurableJson $text)
}


function Assert-EligibleManifestShape($Manifest, $Witness) {
    $context = $Witness.context
    $childId = [int]$Witness.childProcess.processId
    $childStart = [long]$Witness.childProcess.processStartTicks
    Assert-Contract ($childId -gt 0 -and $childStart -gt 0) 'WITNESS_CHILD_IDENTITY'
    Assert-Contract ([string]$Manifest.protocol -ceq 'troubleshootjs-verifier-run-v1') 'MANIFEST_PROTOCOL'
    Assert-Contract ([string]$Manifest.runId -ceq [string]$context.runId -and
        [string]$Manifest.repositoryIdentity -ceq [string]$context.repositoryIdentity) 'MANIFEST_RUN_ID'
    Assert-Contract ((Test-VerifierCanonicalWindowsPathValue ([string]$Manifest.manifestPath) ([string]$context.manifestPath)) -and
        (Test-VerifierCanonicalWindowsPathValue ([string]$Manifest.runRoot) ([string]$context.runRoot)) -and
        (Test-VerifierCanonicalWindowsPathValue ([string]$Manifest.runNamespaceRoot) ([string]$context.runNamespaceRoot)) -and
        (Test-VerifierCanonicalWindowsPathValue ([string]$Manifest.worktreeRoot) $script:RepositoryRoot) -and
        (Test-VerifierCanonicalWindowsPathValue ([string]$Manifest.evidenceDirectory) ([string]$context.evidenceDirectory)) -and
        (Test-VerifierCanonicalWindowsPathValue ([string]$Manifest.evidenceNamespaceRoot) ([string]$context.evidenceNamespaceRoot))) 'MANIFEST_ROOT_BINDING'
    Assert-Contract (-not [String]::IsNullOrWhiteSpace([string]$Manifest.previewNonce) -and
        (Get-StringSha256 ([string]$Manifest.previewNonce)).Equals([string]$context.previewNonceSha256, [StringComparison]::OrdinalIgnoreCase)) 'MANIFEST_PREVIEW_NONCE_BINDING'
    Assert-Contract ([string]$Manifest.cleanup.state -ceq 'pending') 'MANIFEST_CLEANUP_NOT_PENDING'

    $sessions = @($Manifest.browserSessions)
    $leases = @($Manifest.leases)
    Assert-Contract ($sessions.Count -eq 1) 'MANIFEST_SESSION_COUNT'
    $session = $sessions[0]
    Assert-Contract ([string]$session.status -ceq 'attached' -and
        [string]$session.cleanupResult -ceq 'pending') 'MANIFEST_SESSION_NOT_ATTACHED_PENDING'
    Assert-Contract ([string]$session.expectedUrl -ceq 'about:blank') 'MANIFEST_SESSION_URL'
    Assert-Contract ([string]$session.runId -ceq [string]$Manifest.runId -and
        [string]$session.repositoryIdentity -ceq [string]$Manifest.repositoryIdentity -and
        (Test-VerifierCanonicalWindowsPathValue ([string]$session.worktreeRoot) $script:RepositoryRoot)) 'MANIFEST_SESSION_ROOT_BINDING'
    Assert-Contract ([string]$session.recoveryReceipt.state -ceq 'bound' -and
        $session.recoveryReceipt.closeAttempted -is [bool] -and
        -not [bool]$session.recoveryReceipt.closeAttempted) 'MANIFEST_RECEIPT_NOT_FRESH_BOUND'
    Assert-Contract ([string]$session.profile -ceq [string]$Witness.browser.profilePath -and
        [int]$session.cdpPort -eq [int]$Witness.browser.cdpPort -and
        [string]$session.cdpLeasePath -ceq [string]$Witness.browser.cdpLeasePath) 'MANIFEST_SESSION_WITNESS_ASSOCIATION'
    Assert-Contract ([int]$session.processId -eq [int]$Witness.browser.processId -and
        [long]$session.processStartTicks -eq [long]$Witness.browser.processStartTicks -and
        [int]$session.processParentProcessId -eq $childId -and
        [long]$session.processParentProcessStartTicks -eq $childStart) 'MANIFEST_BROWSER_PARENT_BINDING'
    Assert-Contract ([int]$session.recoveryReceipt.rootProcessId -eq [int]$Witness.browser.processId -and
        [long]$session.recoveryReceipt.rootProcessStartTicks -eq [long]$Witness.browser.processStartTicks -and
        [int]$session.recoveryReceipt.rootParentProcessId -eq $childId -and
        [long]$session.recoveryReceipt.rootParentProcessStartTicks -eq $childStart) 'MANIFEST_RECEIPT_PARENT_BINDING'

    $previewLease = $null
    $cdpLeases = @($leases | Where-Object { [string]$_.kind -ceq 'cdp' })
    Assert-Contract ($cdpLeases.Count -eq 1) 'MANIFEST_CDP_LEASE_COUNT'
    $cdpLease = $cdpLeases[0]
    Assert-Contract ([int]$cdpLease.port -eq [int]$session.cdpPort -and
        [string]$cdpLease.path -ceq [string]$session.cdpLeasePath -and
        [string]$cdpLease.leaseId -ceq [string]$Witness.browser.lease.leaseId -and
        [string]$session.recoveryReceipt.leaseId -ceq [string]$cdpLease.leaseId -and
        [string]$cdpLease.path -ceq [string]$Witness.browser.lease.path -and
        [int]$cdpLease.port -eq [int]$Witness.browser.lease.port -and
        [int]$cdpLease.claimOwnerPid -eq [int]$Witness.browser.lease.claimOwnerPid -and
        [long]$cdpLease.claimOwnerStartTicks -eq [long]$Witness.browser.lease.claimOwnerStartTicks -and
        [string]$cdpLease.runId -ceq [string]$Manifest.runId -and
        [string]$cdpLease.repositoryIdentity -ceq [string]$Manifest.repositoryIdentity -and
        (Test-VerifierCanonicalWindowsPathValue ([string]$cdpLease.worktreeRoot) $script:RepositoryRoot)) 'MANIFEST_CDP_ASSOCIATION'
    Assert-Contract ([int]$cdpLease.claimOwnerPid -eq $childId -and
        [long]$cdpLease.claimOwnerStartTicks -eq $childStart -and
        [string]$cdpLease.claim.protocol -ceq 'troubleshootjs-verifier-port-claim-v1' -and
        [string]$cdpLease.claim.runId -ceq [string]$Manifest.runId -and
        [string]$cdpLease.claim.repositoryIdentity -ceq [string]$Manifest.repositoryIdentity -and
        (Test-VerifierCanonicalWindowsPathValue ([string]$cdpLease.claim.worktreeRoot) $script:RepositoryRoot) -and
        [string]$cdpLease.claim.leaseId -ceq [string]$cdpLease.leaseId -and
        [string]$cdpLease.claim.path -ceq [string]$cdpLease.path -and
        [string]$cdpLease.claim.kind -ceq 'cdp' -and
        [int]$cdpLease.claim.port -eq [int]$cdpLease.port -and
        [string]$cdpLease.claim.mutexName -ceq [string]$cdpLease.claimName -and
        [int]$cdpLease.claim.ownerPid -eq $childId -and
        [long]$cdpLease.claim.ownerStartTicks -eq $childStart) 'MANIFEST_CDP_CLAIM_OWNER_BINDING'
    Assert-Contract (Test-Path -LiteralPath ([string]$cdpLease.path) -PathType Leaf) 'MANIFEST_CDP_CLAIM_MISSING'
    Assert-Contract ([string]$cdpLease.status -ceq 'bound' -and
        [string]$cdpLease.claimState -ceq 'bound' -and
        [string]$cdpLease.releaseState -ceq 'active' -and
        [string]$cdpLease.releaseJournalState -ceq 'active' -and
        [bool]$cdpLease.processProofRequired -and
        [int]$cdpLease.boundProcessId -eq [int]$session.processId -and
        [long]$cdpLease.boundProcessStartTicks -eq [long]$session.processStartTicks) 'MANIFEST_CDP_NOT_BOUND'
    Assert-Contract ([string]$session.recoveryReceipt.protocol -ceq 'troubleshootjs-verifier-browser-recovery-v1' -and
        [string]$session.recoveryReceipt.runId -ceq [string]$Manifest.runId -and
        [string]$session.recoveryReceipt.repositoryIdentity -ceq [string]$Manifest.repositoryIdentity -and
        (Test-VerifierCanonicalWindowsPathValue ([string]$session.recoveryReceipt.worktreeRoot) $script:RepositoryRoot) -and
        [string]$session.recoveryReceipt.routeId -ceq [string]$session.routeId -and
        [string]$session.recoveryReceipt.routeName -ceq [string]$session.routeName -and
        [string]$session.recoveryReceipt.browserPath -ceq [string]$session.browserPath -and
        [string]$session.recoveryReceipt.profile -ceq [string]$session.profile -and
        [string]$session.recoveryReceipt.leaseId -ceq [string]$cdpLease.leaseId -and
        [int]$session.recoveryReceipt.cdpPort -eq [int]$cdpLease.port) 'MANIFEST_RECEIPT_IDENTITY_BINDING'

    if ($Case -ceq 'Ownerless') {
        Assert-Contract ([string]$Manifest.baseUrl -ceq '' -and
            [string]$Manifest.server.owner -ceq 'none' -and
            [string]$Manifest.server.baseUrl -ceq '') 'OWNERLESS_BASE_URL_OR_OWNER'
        Assert-Contract ($leases.Count -eq 1) 'OWNERLESS_LEASE_SHAPE'
        Assert-Contract ([string]$Manifest.server.repositoryIdentity -ceq [string]$Manifest.repositoryIdentity -and
            (Test-VerifierCanonicalWindowsPathValue ([string]$Manifest.server.worktreeRoot) $script:RepositoryRoot) -and
            [string]$Manifest.server.owner -ceq 'none') 'OWNERLESS_SERVER_ROOT_BINDING'
    } else {
        Assert-Contract ($leases.Count -eq 2) 'TERMINAL_PREVIEW_LEASE_COUNT'
        $previewLeases = @($leases | Where-Object { [string]$_.kind -ceq 'preview' })
        Assert-Contract ($previewLeases.Count -eq 1) 'TERMINAL_PREVIEW_LEASE_KINDS'
        $previewLease = $previewLeases[0]
        Assert-Contract ([string]$Manifest.baseUrl -ceq [string]$Manifest.server.baseUrl -and
            -not [String]::IsNullOrWhiteSpace([string]$Manifest.baseUrl) -and
            [string]$Manifest.server.owner -ceq 'run' -and
            [string]$Manifest.server.runId -ceq [string]$Manifest.runId -and
            [string]$Manifest.server.repositoryIdentity -ceq [string]$Manifest.repositoryIdentity -and
            [string]$Manifest.server.script -ceq [string]$Witness.preview.scriptPath -and
            [string]$Manifest.server.baseUrl -ceq [string]$Witness.preview.baseUrl -and
            (Get-StringSha256 ([string]$Manifest.server.nonce)).Equals([string]$Witness.preview.nonceSha256, [StringComparison]::OrdinalIgnoreCase) -and
            [string]$Witness.preview.nonceSha256 -ceq [string]$context.previewNonceSha256 -and
            (Test-VerifierCanonicalWindowsPathValue ([string]$Manifest.server.worktreeRoot) $script:RepositoryRoot)) 'TERMINAL_PREVIEW_SERVER_BINDING'
        Assert-Contract ([int]$Manifest.server.processParentProcessId -eq $childId -and
            [long]$Manifest.server.processParentProcessStartTicks -eq $childStart -and
            [int]$Manifest.server.processId -eq [int]$Witness.preview.processId -and
            [long]$Manifest.server.processStartTicks -eq [long]$Witness.preview.processStartTicks -and
            [int]$Manifest.server.leaseOwnerPid -eq $childId -and
            [long]$Manifest.server.leaseOwnerStartTicks -eq $childStart) 'TERMINAL_PREVIEW_PROCESS_PARENT_BINDING'
        Assert-TrueBoolean $Manifest.server 'processIdentityKnown'
        Assert-TrueBoolean $Manifest.server 'processTerminationProven'
        Assert-TrueBoolean $Manifest.server 'processAbsent'
        Assert-TrueBoolean $Manifest.server 'listenerInspectionProven'
        Assert-TrueBoolean $Manifest.server 'listenerAbsent'
        Assert-Contract ([int]$Manifest.server.port -eq [int]$previewLease.port -and
            [string]$Manifest.server.leasePath -ceq [string]$previewLease.path -and
            [string]$Manifest.server.leaseId -ceq [string]$previewLease.leaseId -and
            [string]$Manifest.server.leaseId -ceq [string]$Witness.preview.lease.leaseId -and
            [string]$Manifest.server.leasePath -ceq [string]$Witness.preview.lease.path -and
            [int]$Manifest.server.leaseOwnerPid -eq [int]$Witness.preview.lease.claimOwnerPid -and
            [long]$Manifest.server.leaseOwnerStartTicks -eq [long]$Witness.preview.lease.claimOwnerStartTicks -and
            [int]$previewLease.port -eq [int]$Witness.preview.port) 'TERMINAL_PREVIEW_SERVER_LEASE_ASSOCIATION'
        Assert-Contract ([string]$previewLease.runId -ceq [string]$Manifest.runId -and
            [string]$previewLease.repositoryIdentity -ceq [string]$Manifest.repositoryIdentity -and
            (Test-VerifierCanonicalWindowsPathValue ([string]$previewLease.worktreeRoot) $script:RepositoryRoot)) 'TERMINAL_PREVIEW_LEASE_ROOT_BINDING'
        Assert-Contract ([int]$previewLease.claimOwnerPid -eq $childId -and
            [long]$previewLease.claimOwnerStartTicks -eq $childStart -and
            [string]$Manifest.server.leaseClaimName -ceq [string]$previewLease.claimName -and
            [string]$previewLease.claim.protocol -ceq 'troubleshootjs-verifier-port-claim-v1' -and
            [string]$previewLease.claim.runId -ceq [string]$Manifest.runId -and
            [string]$previewLease.claim.repositoryIdentity -ceq [string]$Manifest.repositoryIdentity -and
            (Test-VerifierCanonicalWindowsPathValue ([string]$previewLease.claim.worktreeRoot) $script:RepositoryRoot) -and
            [string]$previewLease.claim.leaseId -ceq [string]$previewLease.leaseId -and
            [string]$previewLease.claim.path -ceq [string]$previewLease.path -and
            [string]$previewLease.claim.kind -ceq 'preview' -and
            [int]$previewLease.claim.port -eq [int]$previewLease.port -and
            [string]$previewLease.claim.mutexName -ceq [string]$previewLease.claimName -and
            [int]$previewLease.claim.ownerPid -eq $childId -and
            [long]$previewLease.claim.ownerStartTicks -eq $childStart) 'TERMINAL_PREVIEW_CLAIM_OWNER_BINDING'
        Assert-Contract ([string]$previewLease.status -ceq 'released' -and
            [string]$previewLease.claimState -ceq 'released' -and
            [string]$previewLease.releaseState -ceq 'complete' -and
            [string]$previewLease.releaseJournalState -ceq 'complete') 'TERMINAL_PREVIEW_LEASE_NOT_TERMINAL'
        Assert-TrueBoolean $previewLease 'mutexReleased'
        Assert-TrueBoolean $previewLease 'processTerminationProven'
        Assert-TrueBoolean $previewLease 'processAbsent'
        Assert-TrueBoolean $previewLease 'listenerInspectionSuccess'
        Assert-TrueBoolean $previewLease 'listenerInspectionKnown'
        Assert-FalseBoolean $previewLease 'listenerHasListeners'
        Assert-TrueBoolean $previewLease 'listenerAbsent'
        Assert-Contract ([bool]$previewLease.releaseBlocked -eq $false -and
            -not (Test-Path -LiteralPath ([string]$previewLease.path))) 'TERMINAL_PREVIEW_CLAIM_NOT_RELEASED'
        $script:FinalProofs['terminalPreviewCleanupAndLeaseProof'] = $true
    }
    $script:FinalProofs['manifestEligibleShape'] = $true
    return [pscustomobject]@{
        Manifest = $Manifest
        Session = $session
        CdpLease = $cdpLease
        PreviewLease = $previewLease
        Server = $Manifest.server
        Leases = $leases
    }
}

function Assert-SelectedFieldsUnchanged($Before, $After, [string[]]$Names, [string]$Code) {
    $beforeView = [ordered]@{}
    $afterView = [ordered]@{}
    foreach ($name in $Names) {
        $beforeProperty = $Before.PSObject.Properties[$name]
        $afterProperty = $After.PSObject.Properties[$name]
        Assert-Contract ($null -ne $beforeProperty -and $null -ne $afterProperty) ($Code + '_FIELD_MISSING')
        $beforeView[$name] = $beforeProperty.Value
        $afterView[$name] = $afterProperty.Value
    }
    $beforeJson = ConvertTo-Json -InputObject $beforeView -Depth 16 -Compress
    $afterJson = ConvertTo-Json -InputObject $afterView -Depth 16 -Compress
    Assert-Contract ([string]::Equals([string]$beforeJson, [string]$afterJson, [StringComparison]::Ordinal)) $Code
}

function Assert-ImmutableLeaseBinding($BeforeLease, $AfterLease, [string]$Code) {
    Assert-SelectedFieldsUnchanged -Before $BeforeLease -After $AfterLease -Names @(
        'leaseId', 'kind', 'port', 'path', 'runId', 'repositoryIdentity', 'worktreeRoot',
        'claimName', 'claimOwnerPid', 'claimOwnerStartTicks', 'profile', 'browserPath',
        'registered', 'boundProcessId', 'boundProcessStartTicks', 'processProofRequired'
    ) -Code ($Code + '_LEASE_IDENTITY')
    Assert-Contract ($null -ne $BeforeLease.claim -and $null -ne $AfterLease.claim) ($Code + '_CLAIM_MISSING')
    Assert-SelectedFieldsUnchanged -Before $BeforeLease.claim -After $AfterLease.claim -Names @(
        'protocol', 'runId', 'repositoryIdentity', 'worktreeRoot', 'leaseId', 'path',
        'kind', 'port', 'mutexName', 'ownerPid', 'ownerStartTicks'
    ) -Code ($Code + '_CLAIM_IDENTITY')
}

function Assert-FinalManifestProof($Manifest, $Witness, $Eligibility) {
    Assert-Contract ($null -ne $Eligibility -and $null -ne $Eligibility.Manifest -and
        $null -ne $Eligibility.Session -and $null -ne $Eligibility.CdpLease) 'FINAL_ELIGIBILITY_SNAPSHOT_MISSING'
    $before = $Eligibility.Manifest
    $context = $Witness.context
    Assert-SelectedFieldsUnchanged -Before $before -After $Manifest -Names @(
        'protocol', 'runId', 'repositoryIdentity', 'worktreeRoot', 'runRoot',
        'runNamespaceRoot', 'evidenceDirectory', 'evidenceNamespaceRoot', 'manifestPath',
        'createdUtc', 'baseUrl', 'previewNonce'
    ) -Code 'FINAL_MANIFEST_ROOT_IDENTITY_CHANGED'
    Assert-Contract ([string]$Manifest.protocol -ceq 'troubleshootjs-verifier-run-v1' -and
        [string]$Manifest.runId -ceq [string]$context.runId -and
        [string]$Manifest.repositoryIdentity -ceq [string]$context.repositoryIdentity -and
        (Test-VerifierCanonicalWindowsPathValue ([string]$Manifest.worktreeRoot) $script:RepositoryRoot) -and
        (Test-VerifierCanonicalWindowsPathValue ([string]$Manifest.runNamespaceRoot) ([string]$context.runNamespaceRoot)) -and
        (Test-VerifierCanonicalWindowsPathValue ([string]$Manifest.runRoot) ([string]$context.runRoot)) -and
        (Test-VerifierCanonicalWindowsPathValue ([string]$Manifest.manifestPath) ([string]$context.manifestPath)) -and
        (Test-VerifierCanonicalWindowsPathValue ([string]$Manifest.evidenceNamespaceRoot) ([string]$script:EvidenceRoot)) -and
        (Test-VerifierCanonicalWindowsPathValue ([string]$Manifest.evidenceDirectory) ([string]$context.evidenceDirectory)) -and
        (Get-StringSha256 ([string]$Manifest.previewNonce)).Equals([string]$context.previewNonceSha256, [StringComparison]::OrdinalIgnoreCase)) 'FINAL_MANIFEST_ROOT_BINDING'
    Assert-Contract ([string]$Manifest.cleanup.state -ceq 'complete') 'FINAL_RUN_CLEANUP_STATE'
    Assert-Contract (@($Manifest.cleanup.errors).Count -eq 0) 'FINAL_RUN_CLEANUP_ERRORS'

    $sessions = @($Manifest.browserSessions)
    Assert-Contract ($sessions.Count -eq 1 -and @($before.browserSessions).Count -eq 1) 'FINAL_SESSION_COUNT'
    $session = $sessions[0]
    $beforeSession = $Eligibility.Session
    Assert-SelectedFieldsUnchanged -Before $beforeSession -After $session -Names @(
        'owner', 'runId', 'repositoryIdentity', 'worktreeRoot', 'routeId', 'routeName',
        'cdpPort', 'browserPath', 'cdpLeasePath', 'profile', 'processId',
        'processStartTicks', 'processParentProcessId', 'processParentProcessStartTicks',
        'processCommandLine', 'targetId', 'expectedUrl', 'expectedRunMarker', 'expectedRouteMarker'
    ) -Code 'FINAL_SESSION_IDENTITY_CHANGED'
    $beforeLaunch = $beforeSession.containmentLaunch
    $launch = $session.containmentLaunch
    Assert-Contract (($null -eq $beforeLaunch -and $null -eq $launch) -or
        ($null -ne $beforeLaunch -and $null -ne $launch)) 'FINAL_CONTAINMENT_LAUNCH_SHAPE_CHANGED'
    if ($null -ne $beforeLaunch) {
        Assert-SelectedFieldsUnchanged -Before $beforeLaunch -After $launch -Names @(
            'protocol', 'jobName', 'launchProcessId', 'launchedUtc'
        ) -Code 'FINAL_CONTAINMENT_LAUNCH_IDENTITY_CHANGED'
    }
    Assert-Contract ([string]$session.owner -ceq 'run' -and
        [string]$session.runId -ceq [string]$Manifest.runId -and
        [string]$session.repositoryIdentity -ceq [string]$Manifest.repositoryIdentity -and
        (Test-VerifierCanonicalWindowsPathValue ([string]$session.worktreeRoot) $script:RepositoryRoot) -and
        [string]$session.cleanupResult -ceq 'complete' -and
        [string]$session.status -ceq 'cleaned') 'FINAL_BROWSER_CLEANUP_RESULT'
    Assert-TrueBoolean $session 'profileProcessScanCompleted'
    Assert-FalseBoolean $session 'profileInspectionFailed'
    Assert-Contract ([string]$session.expectedUrl -ceq 'about:blank' -and
        [string]$session.profile -ceq [string]$Witness.browser.profilePath -and
        [int]$session.cdpPort -eq [int]$Witness.browser.cdpPort -and
        [string]$session.cdpLeasePath -ceq [string]$Witness.browser.cdpLeasePath -and
        [int]$session.processId -eq [int]$Witness.browser.processId -and
        [long]$session.processStartTicks -eq [long]$Witness.browser.processStartTicks -and
        [int]$session.processParentProcessId -eq [int]$Witness.childProcess.processId -and
        [long]$session.processParentProcessStartTicks -eq [long]$Witness.childProcess.processStartTicks) 'FINAL_SESSION_WITNESS_BINDING'
    Assert-Contract (-not (Test-Path -LiteralPath ([string]$session.profile))) 'FINAL_BROWSER_PROFILE_STILL_EXISTS'

    $beforeReceipt = $beforeSession.recoveryReceipt
    $receipt = $session.recoveryReceipt
    Assert-Contract ($null -ne $beforeReceipt -and $null -ne $receipt) 'FINAL_RECEIPT_MISSING'
    Assert-SelectedFieldsUnchanged -Before $beforeReceipt -After $receipt -Names @(
        'protocol', 'authorityToken', 'issuedUtc', 'runId', 'repositoryIdentity',
        'worktreeRoot', 'routeId', 'routeName', 'browserPath', 'profile', 'leaseId',
        'cdpPort', 'rootProcessId', 'rootProcessStartTicks', 'rootParentProcessId',
        'rootParentProcessStartTicks', 'rootProcessCommandLine', 'listenerProcessId',
        'listenerProcessStartTicks'
    ) -Code 'FINAL_RECEIPT_IDENTITY_CHANGED'
    Assert-Contract ([string]$beforeReceipt.state -ceq 'bound' -and
        $beforeReceipt.closeAttempted -is [bool] -and -not [bool]$beforeReceipt.closeAttempted -and
        [string]$receipt.state -ceq 'closed' -and $receipt.closeAttempted -is [bool] -and
        [bool]$receipt.closeAttempted) 'FINAL_BROWSER_RECEIPT_NOT_CLOSED'

    $leases = @($Manifest.leases)
    $beforeLeases = @($Eligibility.Leases)
    $expectedLeaseCount = if ($Case -ceq 'TerminalPreview') { 2 } else { 1 }
    Assert-Contract ($leases.Count -eq $expectedLeaseCount -and $beforeLeases.Count -eq $expectedLeaseCount) 'FINAL_LEASE_COUNT'
    $cdpLease = @($leases | Where-Object { [string]$_.kind -ceq 'cdp' })
    Assert-Contract ($cdpLease.Count -eq 1 -and [string]$cdpLease[0].leaseId -ceq [string]$Eligibility.CdpLease.leaseId) 'FINAL_CDP_LEASE_COUNT'
    Assert-ImmutableLeaseBinding $Eligibility.CdpLease $cdpLease[0] 'FINAL_CDP'
    Assert-Contract ([string]$cdpLease[0].profile -ceq [string]$session.profile -and
        [int]$cdpLease[0].port -eq [int]$session.cdpPort -and
        [string]$cdpLease[0].path -ceq [string]$session.cdpLeasePath) 'FINAL_CDP_PROFILE_PORT_ASSOCIATION'

    foreach ($lease in $leases) {
        Assert-Contract ([string]$lease.status -ceq 'released' -and
            [string]$lease.claimState -ceq 'released' -and
            [string]$lease.releaseState -ceq 'complete' -and
            [string]$lease.releaseJournalState -ceq 'complete') 'FINAL_LEASE_NOT_TERMINAL'
        Assert-FalseBoolean $lease 'releaseBlocked'
        Assert-TrueBoolean $lease 'mutexReleased'
        Assert-TrueBoolean $lease 'processTerminationProven'
        Assert-TrueBoolean $lease 'processAbsent'
        Assert-TrueBoolean $lease 'listenerInspectionSuccess'
        Assert-TrueBoolean $lease 'listenerInspectionKnown'
        Assert-FalseBoolean $lease 'listenerHasListeners'
        Assert-TrueBoolean $lease 'listenerAbsent'
        Assert-Contract (-not (Test-Path -LiteralPath ([string]$lease.path))) 'FINAL_PORT_CLAIM_STILL_EXISTS'
    }

    $server = $Manifest.server
    $beforeServer = $Eligibility.Server
    Assert-Contract ($null -ne $server -and $null -ne $beforeServer) 'FINAL_SERVER_MISSING'
    Assert-SelectedFieldsUnchanged -Before $beforeServer -After $server -Names @(
        'owner', 'baseUrl', 'repositoryIdentity', 'worktreeRoot', 'port', 'processId',
        'processStartTicks', 'repositoryRoot', 'webRoot', 'identityProtocol',
        'identityVerified', 'callerOwned', 'processParentProcessId',
        'processParentProcessStartTicks', 'processCommandLine', 'script', 'runId',
        'nonce', 'leaseId', 'leaseKind', 'leaseClaimName', 'leaseOwnerPid',
        'leaseOwnerStartTicks', 'leasePath', 'leaseProcessProofRequired',
        'leaseListenerOwnerKind', 'leaseListenerOwnerProof', 'leaseListenerOwnerEvidence',
        'stdoutLog', 'stderrLog'
    ) -Code 'FINAL_SERVER_IDENTITY_CHANGED'
    if ($Case -ceq 'TerminalPreview') {
        $previewLease = @($leases | Where-Object { [string]$_.kind -ceq 'preview' })
        Assert-Contract ($previewLease.Count -eq 1 -and $null -ne $Eligibility.PreviewLease) 'FINAL_PREVIEW_LEASE_COUNT'
        Assert-ImmutableLeaseBinding $Eligibility.PreviewLease $previewLease[0] 'FINAL_PREVIEW'
        Assert-Contract ([string]$server.owner -ceq 'run' -and
            [string]$server.baseUrl -ceq [string]$beforeServer.baseUrl -and
            -not [String]::IsNullOrWhiteSpace([string]$server.baseUrl) -and
            [string]$Manifest.baseUrl -ceq [string]$server.baseUrl -and
            [string]$server.runId -ceq [string]$Manifest.runId -and
            [string]$server.script -ceq [string]$Witness.preview.scriptPath -and
            [int]$server.processId -eq [int]$Witness.preview.processId -and
            [long]$server.processStartTicks -eq [long]$Witness.preview.processStartTicks -and
            [int]$server.processParentProcessId -eq [int]$Witness.childProcess.processId -and
            [long]$server.processParentProcessStartTicks -eq [long]$Witness.childProcess.processStartTicks -and
            [string]$server.leaseId -ceq [string]$previewLease[0].leaseId -and
            [string]$server.leasePath -ceq [string]$previewLease[0].path -and
            [int]$server.port -eq [int]$previewLease[0].port -and
            (Get-StringSha256 ([string]$server.nonce)).Equals([string]$Witness.preview.nonceSha256, [StringComparison]::OrdinalIgnoreCase) -and
            [string]$Witness.preview.nonceSha256 -ceq [string]$context.previewNonceSha256) 'FINAL_TERMINAL_PREVIEW_IDENTITY'
        Assert-Contract ([string]$server.cleanupResult -ceq 'complete') 'FINAL_TERMINAL_PREVIEW_SHAPE'
        Assert-TrueBoolean $server 'processTerminationProven'
        Assert-TrueBoolean $server 'processAbsent'
        Assert-TrueBoolean $server 'listenerInspectionProven'
        Assert-TrueBoolean $server 'listenerAbsent'
        Assert-Contract ([string]$previewLease[0].status -ceq 'released' -and
            [string]$previewLease[0].claimState -ceq 'released' -and
            [string]$previewLease[0].releaseState -ceq 'complete' -and
            [string]$previewLease[0].releaseJournalState -ceq 'complete' -and
            -not (Test-Path -LiteralPath ([string]$server.leasePath))) 'FINAL_PREVIEW_CLAIM_STILL_EXISTS'
    } else {
        Assert-Contract ([string]$server.owner -ceq 'none' -and
            [string]$server.baseUrl -ceq '' -and [string]$Manifest.baseUrl -ceq '') 'FINAL_OWNERLESS_SERVER_SHAPE'
    }

    $script:FinalProofs['finalManifestIdentityPreserved'] = $true
    $script:FinalProofs['browserCloseAndProfileProof'] = $true
    $script:FinalProofs['cdpClaimListenerProcessAbsenceProof'] = $true
    $script:FinalProofs['allPortClaimsAbsent'] = $true
    if ($Case -ceq 'TerminalPreview') {
        $script:FinalProofs['previewProcessListenerClaimAbsenceProof'] = $true
    }
}
function Import-ExactVerifierModule {
    $loaded = @(Get-Module -Name 'VerifierIsolation')
    if ($loaded.Count -gt 0) {
        Assert-Contract ($loaded.Count -eq 1 -and
            [IO.Path]::GetFullPath([string]$loaded[0].Path).Equals([IO.Path]::GetFullPath($script:ModulePath), [StringComparison]::OrdinalIgnoreCase)) 'VERIFIER_MODULE_IMPORT_AMBIGUOUS'
        Remove-Module -ModuleInfo $loaded[0] -Force -ErrorAction Stop
    }
    Import-Module -Name $script:ModulePath -Force -ErrorAction Stop
    $fresh = @(Get-Module -Name 'VerifierIsolation')
    Assert-Contract ($fresh.Count -eq 1 -and
        [IO.Path]::GetFullPath([string]$fresh[0].Path).Equals([IO.Path]::GetFullPath($script:ModulePath), [StringComparison]::OrdinalIgnoreCase)) 'FRESH_VERIFIER_MODULE_IMPORT'
}

function Assert-RecoveryStillEligible {
    Assert-Contract ($script:RecoverySafeToAttempt -and $script:PreRecoveryEligibilityProven -and
        $script:WitnessBindingProven -and $script:SourceStable -and
        $null -ne $script:RecoveryEligibility -and
        $null -ne $script:ChildResult -and $null -ne $script:ChildEnvelope -and
        $null -ne $script:ChildWitness -and $null -ne $script:ChildWitness.browser -and
        ([string]$script:ChildEnvelope.status -ceq 'READY' -or [string]$script:ChildEnvelope.status -ceq 'FAIL') -and
        (([string]$script:ChildEnvelope.status -ceq 'READY' -and [int]$script:ChildResult.ExitCode -eq 0) -or
            ([string]$script:ChildEnvelope.status -ceq 'FAIL' -and [int]$script:ChildResult.ExitCode -ne 0)) -and
        [bool]$script:ChildResult.TerminationProven -and
        [int]$script:ChildWitness.childProcess.processId -eq [int]$script:ChildResult.ProcessId -and
        [long]$script:ChildWitness.childProcess.processStartTicks -eq [long]$script:ChildResult.ProcessStartTicks) 'RECOVERY_SAFETY_PREREQUISITES_NOT_PROVEN'
    Assert-Contract ([string]::Equals([IO.Path]::GetFullPath($script:RecoveryManifestPath),
        [IO.Path]::GetFullPath([string]$script:ChildWitness.context.manifestPath), [StringComparison]::OrdinalIgnoreCase)) 'RECOVERY_MANIFEST_PATH_CHANGED'
    $boundWitnessPath = [string]$script:Report.recovery.exactWitnessPath
    Assert-Contract (-not [String]::IsNullOrWhiteSpace($boundWitnessPath) -and
        [string]::Equals([IO.Path]::GetFullPath($boundWitnessPath), [IO.Path]::GetFullPath([string]$script:Report.witness.file), [StringComparison]::OrdinalIgnoreCase) -and
        (Test-Path -LiteralPath $boundWitnessPath -PathType Leaf)) 'RECOVERY_BOUND_WITNESS_PATH_MISSING'
    [void](Assert-VerifierNoReparseAncestors $script:EvidenceRoot)
    [void](Assert-VerifierPhysicalOwnedPath $script:EvidenceRoot $boundWitnessPath)
    Assert-Contract ((Get-FileSha256 $boundWitnessPath).Equals([string]$script:Report.witness.sha256, [StringComparison]::OrdinalIgnoreCase)) 'RECOVERY_BOUND_WITNESS_CHANGED'
    Assert-Contract ($null -ne $script:Report.manifest -and
        -not [String]::IsNullOrWhiteSpace([string]$script:Report.manifest.beforeRecoverySha256)) 'RECOVERY_PRESTATE_HASH_MISSING'
    [void](Assert-VerifierNoReparseAncestors ([string]$script:ChildWitness.context.runRoot))
    [void](Assert-VerifierPhysicalOwnedPath ([string]$script:ChildWitness.context.runRoot) $script:RecoveryManifestPath)
    $currentPins = Get-SourcePinSet
    $script:Report.sourcePinsAfterChild = $currentPins
    $script:SourceStable = Test-SourcePinsEqual $script:Report.sourcePinsBefore $currentPins
    Assert-Contract $script:SourceStable 'SOURCE_PINS_CHANGED_BEFORE_RECOVERY'
    $currentManifestSha = Get-FileSha256 $script:RecoveryManifestPath
    Assert-Contract ($currentManifestSha.Equals([string]$script:Report.manifest.beforeRecoverySha256, [StringComparison]::OrdinalIgnoreCase)) 'RECOVERY_MANIFEST_CHANGED_AFTER_ELIGIBILITY'
    $currentManifest = Read-DurableManifest $script:RecoveryManifestPath
    $currentEligibility = Assert-EligibleManifestShape $currentManifest $script:ChildWitness
    Assert-Contract (Test-Path -LiteralPath ([string]$currentEligibility.CdpLease.path) -PathType Leaf) 'CDP_CLAIM_MISSING_BEFORE_RECOVERY'
    if ($Case -ceq 'TerminalPreview') {
        Assert-Contract (-not (Test-Path -LiteralPath ([string]$currentEligibility.PreviewLease.path))) 'PREVIEW_CLAIM_REAPPEARED_BEFORE_RECOVERY'
    }
    $script:RecoveryEligibility = $currentEligibility
}

function Invoke-MaintainedRecoveryAndProve([string]$ManifestPath, $Eligibility, [bool]$FromFinally) {
    Assert-Contract (-not $script:RecoveryAttempted) 'MAINTAINED_RECOVERY_ALREADY_ATTEMPTED'
    $script:Stage = if ($FromFinally) { 'finally-module-import' } else { 'fresh-module-import' }
    Import-ExactVerifierModule
    $pinsAfterModuleImport = Get-SourcePinSet
    $script:Report.sourcePinsAfterModuleImport = $pinsAfterModuleImport
    $stableAfterModuleImport = Test-SourcePinsEqual $script:Report.sourcePinsBefore $pinsAfterModuleImport
    $script:SourceStable = ($script:SourceStable -and $stableAfterModuleImport)
    Assert-Contract $script:SourceStable 'SOURCE_PINS_CHANGED_AFTER_MODULE_IMPORT'
    $remainingForRecovery = [long](600000 - $script:AggregateWatch.ElapsedMilliseconds)
    Assert-Contract ($remainingForRecovery -ge 120000) 'AGGREGATE_BUDGET_CANNOT_RESERVE_CLEANUP'
    $script:Stage = if ($FromFinally) { 'finally-maintained-recovery' } else { 'maintained-recovery' }
    $script:RecoveryAttempted = $true
    $script:RecoveryInvokedInFinally = $FromFinally
    $script:Report.recovery.attempted = $true
    $script:Report.recovery.invokedInFinally = $FromFinally
    $recoveryWatch = [Diagnostics.Stopwatch]::StartNew()
    try {
        $script:RecoveryResult = Invoke-VerifierRetainedBrowserRecovery $ManifestPath
    } finally {
        $recoveryWatch.Stop()
        $script:RecoveryElapsedMs = [long]$recoveryWatch.ElapsedMilliseconds
        $script:Report.recovery.operationElapsedMs = $script:RecoveryElapsedMs
        $script:Report.recovery.cleanupUpperBoundWithin120s = [bool]($script:RecoveryElapsedMs -le 120000)
        $script:Report.cleanupComponents.retainedBrowserRecoveryOperationElapsedMs = $script:RecoveryElapsedMs
    }
    Assert-Contract ($null -ne $script:RecoveryResult -and [bool]$script:RecoveryResult.Success -and
        [string]$script:RecoveryResult.RunId -ceq [string]$Eligibility.Manifest.runId -and
        [int]$script:RecoveryResult.Port -eq [int]$script:ChildWitness.browser.cdpPort) 'MAINTAINED_RECOVERY_RETURN_MISMATCH'
    $script:Report.recovery.entryPointReturnedSuccess = $true
    $script:Report.proofs['maintainedRecoveryEntryPointSucceeded'] = $true
    $script:Report.proofs['recoveryOperationWithin120sUpperBound'] = ($script:RecoveryElapsedMs -le 120000)
    $script:ManifestAfter = Read-DurableManifest $ManifestPath
    Assert-FinalManifestProof $script:ManifestAfter $script:ChildWitness $Eligibility
    $script:Report.manifest.finalSha256 = Get-FileSha256 $ManifestPath
    $script:Report.manifest.cleanupStateAfter = [string]$script:ManifestAfter.cleanup.state
    $script:Report.manifest.leaseCountAfter = @($script:ManifestAfter.leases).Count
    $script:Report.recovery.finalManifestProofSucceeded = $true
    $script:Report.recovery.success = $true
    $script:Report.recovery.resourceDisposition = 'owner_proven_cleanup_complete'
    $previewCleanupMs = $null
    if ($null -ne $script:ChildWitness.timing.previewTerminalizationElapsedMs) {
        $previewCleanupMs = [long]$script:ChildWitness.timing.previewTerminalizationElapsedMs
    }
    $script:Report.cleanupComponents.previewOwnerCleanupElapsedMs = $previewCleanupMs
    if ($null -ne $previewCleanupMs) {
        $script:Report.cleanupComponents.sumOfMeasuredOperationIntervalsMs = [long]($previewCleanupMs + $script:RecoveryElapsedMs)
    } else {
        $script:Report.cleanupComponents.sumOfMeasuredOperationIntervalsMs = [long]$script:RecoveryElapsedMs
    }
    $script:Report.proofs['finalManifestIdentityPreserved'] = $true
    Assert-Contract ($script:RecoveryElapsedMs -le 120000) 'RECOVERY_OPERATION_EXCEEDED_CLEANUP_UPPER_BOUND'
    $afterPins = Get-SourcePinSet
    $stableAfterRecovery = Test-SourcePinsEqual $script:Report.sourcePinsBefore $afterPins
    $script:Report.sourcePinsAfterRecovery = $afterPins
    $script:SourceStable = ($script:SourceStable -and $stableAfterRecovery)
    Assert-Contract $stableAfterRecovery 'SOURCE_PINS_CHANGED_AFTER_RECOVERY'
    $script:Report.proofs['sourcePinsStableThroughRecovery'] = $true
    $aggregateMs = [long]$script:AggregateWatch.ElapsedMilliseconds
    Assert-Contract ($aggregateMs -le 600000) 'AGGREGATE_BUDGET_EXCEEDED'
}

function Set-RecoverySkipState([string]$Reason, [string]$Disposition) {
    if ([String]::IsNullOrWhiteSpace([string]$script:Report.recovery.skipReason)) {
        $script:Report.recovery.skipReason = $Reason
    }
    if (-not [bool]$script:Report.recovery.finalManifestProofSucceeded) {
        $script:Report.recovery.resourceDisposition = $Disposition
    }
}

function Invoke-FinallyRecoveryIfSafe {
    if ($script:RecoveryAttempted) {
        if (-not [bool]$script:Report.recovery.finalManifestProofSucceeded) {
            Set-RecoverySkipState 'maintained_recovery_api_already_attempted_no_retry' 'api_attempted_cleanup_unproven_no_retry'
        }
        return
    }
    if (-not $script:RecoverySafeToAttempt -or -not $script:WitnessBindingProven -or
            -not $script:PreRecoveryEligibilityProven -or -not $script:SourceStable -or
            $null -eq $script:ChildResult -or $null -eq $script:ChildEnvelope -or
            $null -eq $script:ChildWitness -or $null -eq $script:ChildWitness.browser -or
            ([string]$script:ChildEnvelope.status -cne 'READY' -and [string]$script:ChildEnvelope.status -cne 'FAIL') -or
            (([string]$script:ChildEnvelope.status -ceq 'READY' -and [int]$script:ChildResult.ExitCode -ne 0) -or
                ([string]$script:ChildEnvelope.status -ceq 'FAIL' -and [int]$script:ChildResult.ExitCode -eq 0)) -or
            $null -eq $script:RecoveryEligibility) {
        Set-RecoverySkipState 'exact_child_witness_manifest_eligibility_or_source_stability_not_proven' 'resources_may_remain_unproven_no_safe_recovery'
        return
    }
    $remainingMs = [long](600000 - $script:AggregateWatch.ElapsedMilliseconds)
    if ($remainingMs -lt 120000) {
        Set-RecoverySkipState 'aggregate_budget_cannot_reserve_existing_120000ms_cleanup_window' 'resources_may_remain_unproven_cleanup_reserve_unavailable'
        return
    }
    try {
        Assert-RecoveryStillEligible
        $script:Report.recovery.finallyEligibilityRevalidated = $true
        Invoke-MaintainedRecoveryAndProve $script:RecoveryManifestPath $script:RecoveryEligibility $true
    } catch {
        $identity = Get-FailureIdentity $_
        $script:Report.recovery.fallbackFailure = [ordered]@{
            type = [string]$identity.type
            id = [string]$identity.id
        }
        if ($script:RecoveryAttempted) {
            Set-RecoverySkipState 'maintained_recovery_attempted_no_retry' 'api_attempted_cleanup_unproven_no_retry'
        } else {
            Set-RecoverySkipState 'finally_recovery_preflight_or_module_import_failed' 'resources_may_remain_unproven_no_recovery_attempted'
        }
        $script:Report.status = 'FAIL'
        $script:ExitCode = 2
    }
}
function Save-Report {
    if ([String]::IsNullOrWhiteSpace($script:ReportPath)) { return }
    $script:Report.timing.aggregateElapsedMs = [long]$script:AggregateWatch.ElapsedMilliseconds
    $json = ConvertTo-Json -InputObject $script:Report -Depth 16 -Compress
    [IO.File]::WriteAllText($script:ReportPath, $json, [Text.UTF8Encoding]::new($false))
}

$exitCode = 2
try {
    if ($PSVersionTable.PSVersion.Major -ne 5 -or $PSVersionTable.PSVersion.Minor -lt 1 -or $PSVersionTable.PSEdition -cne 'Desktop') {
        throw [InvalidOperationException]::new('SELECTED_HOST_NOT_WINDOWS_POWERSHELL_5')
    }
    if ([String]::IsNullOrWhiteSpace($RepositoryRoot)) {
        $RepositoryRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..\..'))
    } else {
        $RepositoryRoot = [IO.Path]::GetFullPath($RepositoryRoot)
    }
    $script:RepositoryRoot = [IO.Path]::GetFullPath($RepositoryRoot)
    $RepositoryRoot = $script:RepositoryRoot
    $requestedEvidence = [IO.Path]::GetFullPath($EvidenceDirectory)
    $script:EvidenceRoot = $requestedEvidence
    $tempRootRaw = [IO.Path]::GetFullPath([IO.Path]::GetTempPath()).TrimEnd([char]92) + [char]92
    $repoPrefix = $RepositoryRoot.TrimEnd([char]92) + [char]92
    if (-not $requestedEvidence.StartsWith($tempRootRaw, [StringComparison]::OrdinalIgnoreCase) -or
            $requestedEvidence.StartsWith($repoPrefix, [StringComparison]::OrdinalIgnoreCase) -or
            (Test-Path -LiteralPath $requestedEvidence)) {
        throw [InvalidOperationException]::new('EVIDENCE_DIRECTORY_NOT_UNIQUE_OS_TEMP_CHILD')
    }
    $script:ModulePath = Join-Path $RepositoryRoot 'scripts\VerifierIsolation.psm1'
    $script:PreviewScriptPath = Join-Path $RepositoryRoot 'scripts\preview.ps1'
    $script:ChildScriptPath = Join-Path $PSScriptRoot 'browser-terminal-preview-recovery-child.ps1'
    if (-not (Test-Path -LiteralPath $script:ModulePath -PathType Leaf) -or
            -not (Test-Path -LiteralPath $script:PreviewScriptPath -PathType Leaf) -or
            -not (Test-Path -LiteralPath $script:ChildScriptPath -PathType Leaf)) {
        throw [InvalidOperationException]::new('SOURCE_INPUT_MISSING')
    }
    $modulePin = Get-FileSha256 $script:ModulePath
    if (-not [String]::IsNullOrWhiteSpace($ExpectedModuleSha256) -and
            $ExpectedModuleSha256 -notmatch '^[0-9a-fA-F]{64}$') {
        throw [InvalidOperationException]::new('EXPECTED_MODULE_PIN_FORMAT_INVALID')
    }
    $modulePinExpected = if ([String]::IsNullOrWhiteSpace($ExpectedModuleSha256)) { $modulePin } else { $ExpectedModuleSha256 }
    if (-not $modulePin.Equals($modulePinExpected, [StringComparison]::OrdinalIgnoreCase)) {
        throw [InvalidOperationException]::new('VERIFIER_MODULE_PIN_MISMATCH')
    }
    New-Item -ItemType Directory -Path $requestedEvidence -ErrorAction Stop | Out-Null
    $script:ReportPath = Join-Path $requestedEvidence 'case-result.json'
    $script:Report.recovery.taskEvidenceDirectory = $requestedEvidence
    $script:TaskNonce = [Guid]::NewGuid().ToString('N')
    Import-Module -Name $script:ModulePath -Force -ErrorAction Stop
    $moduleInfo = @(Get-Module -Name 'VerifierIsolation' | Where-Object {
        [IO.Path]::GetFullPath([string]$_.Path).Equals([IO.Path]::GetFullPath($script:ModulePath), [StringComparison]::OrdinalIgnoreCase)
    })
    Assert-Contract ($moduleInfo.Count -eq 1) 'VERIFIER_MODULE_IMPORT_AMBIGUOUS'
    $tempRoot = Get-VerifierFullPath ([IO.Path]::GetTempPath())
    [void](Assert-VerifierNoReparseAncestors $requestedEvidence)
    [void](Assert-VerifierPhysicalOwnedPath $tempRoot $requestedEvidence -ValidateTree)
    $script:Report.sourcePinsBefore = Get-SourcePinSet
    Assert-Contract ([string]$script:Report.sourcePinsBefore.moduleSha256 -ceq [string]$modulePinExpected) 'VERIFIER_MODULE_PIN_CHANGED_BEFORE_CHILD'
    $actualParentPin = [string]$script:Report.sourcePinsBefore.parentTestSha256
    $actualChildPin = [string]$script:Report.sourcePinsBefore.childTestSha256
    $actualPreviewPin = [string]$script:Report.sourcePinsBefore.previewSha256
    $browser = Resolve-VerifierBrowserPath $BrowserPath
    Assert-Contract (-not [String]::IsNullOrWhiteSpace($browser) -and
        (Test-Path -LiteralPath $browser -PathType Leaf)) 'BROWSER_EXECUTABLE_UNAVAILABLE'
    $powershell = Get-Command -Name 'powershell.exe' -CommandType Application -ErrorAction Stop
    $childArgs = @(
        '-NoLogo', '-NoProfile', '-NonInteractive', '-ExecutionPolicy', 'Bypass',
        '-File', $script:ChildScriptPath,
        '-Case', $Case,
        '-RepositoryRoot', $RepositoryRoot,
        '-EvidenceDirectory', $requestedEvidence,
        '-BrowserPath', $browser,
        '-TaskNonce', $script:TaskNonce,
        '-ExpectedModuleSha256', $modulePinExpected,
        '-ExpectedPreviewSha256', $actualPreviewPin,
        '-ExpectedParentScriptSha256', $actualParentPin,
        '-ExpectedChildScriptSha256', $actualChildPin
    )
    $script:Stage = 'child-process'
    $remainingMs = [int][Math]::Floor(600000 - $script:AggregateWatch.ElapsedMilliseconds)
    Assert-Contract ($remainingMs -gt 0) 'AGGREGATE_BUDGET_EXHAUSTED_BEFORE_CHILD'
    $childWatch = [Diagnostics.Stopwatch]::StartNew()
    $script:ChildResult = Invoke-VerifierBoundedProcess $powershell.Source $childArgs $remainingMs
    $childWatch.Stop()
    $childStdoutText = [string]$script:ChildResult.Stdout
    $childStderrText = [string]$script:ChildResult.Stderr
    $stdoutCapture = Write-PrivateChildStreamFile $requestedEvidence 'child.stdout.txt' $childStdoutText
    $stderrCapture = Write-PrivateChildStreamFile $requestedEvidence 'child.stderr.txt' $childStderrText
    $streamReceipt = [ordered]@{
        protocol = 'troubleshootjs-child-stream-capture-v1'
        childProcessId = [int]$script:ChildResult.ProcessId
        childProcessStartTicks = [long]$script:ChildResult.ProcessStartTicks
        exitCode = [int]$script:ChildResult.ExitCode
        terminationProven = [bool]$script:ChildResult.TerminationProven
        stdout = $stdoutCapture
        stderr = $stderrCapture
    }
    $streamReceiptText = ConvertTo-Json -InputObject $streamReceipt -Depth 5 -Compress
    $streamReceiptCapture = Write-PrivateChildStreamFile $requestedEvidence 'child-stream-capture.json' $streamReceiptText
    $script:Report.child = [ordered]@{
        resultReturned = $true
        executablePath = [string]$powershell.Source
        processId = [int]$script:ChildResult.ProcessId
        processStartTicks = [long]$script:ChildResult.ProcessStartTicks
        exitCode = [int]$script:ChildResult.ExitCode
        terminationProven = [bool]$script:ChildResult.TerminationProven
        elapsedMs = [long]$childWatch.ElapsedMilliseconds
        status = ''
        runId = ''
        outputCapture = $null
        stage = ''
        failureType = ''
        failureId = ''
        cleanupAttempted = $false
        cleanupSucceeded = $false
        cleanupElapsedMs = $null
    }
    $script:Report.child.outputCapture = [ordered]@{
        receiptFile = [string]$streamReceiptCapture.file
        receiptByteCount = [long]$streamReceiptCapture.byteCount
        receiptSha256 = [string]$streamReceiptCapture.sha256
    }
    Assert-Contract ([bool]$script:ChildResult.TerminationProven -and
        [int]$script:ChildResult.ProcessId -gt 0 -and [long]$script:ChildResult.ProcessStartTicks -gt 0) 'CHILD_EXIT_IDENTITY_NOT_PROVEN'
    $childText = [string]$script:ChildResult.Stdout
    Assert-Contract (-not [String]::IsNullOrWhiteSpace($childText)) 'CHILD_RESULT_MISSING'
    $script:ChildEnvelope = ConvertFrom-Json -InputObject $childText -ErrorAction Stop
    Assert-Contract ($null -ne $script:ChildEnvelope -and $script:ChildEnvelope -isnot [array] -and
        [string]$script:ChildEnvelope.protocol -ceq 'troubleshootjs-terminal-preview-recovery-child-result-v1' -and
        [string]$script:ChildEnvelope.case -ceq $Case -and
        [string]$script:ChildEnvelope.taskNonce -ceq $script:TaskNonce) 'CHILD_RESULT_SCHEMA'
    $script:Report.child.status = [string]$script:ChildEnvelope.status
    $script:Report.child.runId = [string]$script:ChildEnvelope.runId
    $script:Report.child.stage = if ($script:ChildEnvelope.PSObject.Properties['stage']) { [string]$script:ChildEnvelope.stage } else { '' }
    $script:Report.child.failureType = if ($script:ChildEnvelope.PSObject.Properties['failureType']) { [string]$script:ChildEnvelope.failureType } else { '' }
    $script:Report.child.failureId = if ($script:ChildEnvelope.PSObject.Properties['failureId']) { [string]$script:ChildEnvelope.failureId } else { '' }
    $script:Report.child.cleanupAttempted = if ($script:ChildEnvelope.PSObject.Properties['cleanupAttempted']) { [bool]$script:ChildEnvelope.cleanupAttempted } else { $false }
    $script:Report.child.cleanupSucceeded = if ($script:ChildEnvelope.PSObject.Properties['cleanupSucceeded']) { [bool]$script:ChildEnvelope.cleanupSucceeded } else { $false }
    $script:Report.child.cleanupElapsedMs = if ($script:ChildEnvelope.PSObject.Properties['cleanupElapsedMs']) { $script:ChildEnvelope.cleanupElapsedMs } else { $null }

    $witnessPath = [string]$script:ChildEnvelope.witnessFile
    if (-not [String]::IsNullOrWhiteSpace($witnessPath)) {
        $witnessPath = Get-VerifierFullPath $witnessPath
        Assert-Contract (Test-VerifierChildPath $requestedEvidence $witnessPath) 'CHILD_WITNESS_ESCAPES_EVIDENCE_ROOT'
        [void](Assert-VerifierPhysicalOwnedPath $requestedEvidence $witnessPath)
        $script:Report.recovery.candidateWitnessPath = $witnessPath
        $script:Report.recovery.candidateWitnessPathConstrainedToTaskEvidence = $true
        $script:Report.recovery.resourceDisposition = 'child_witness_candidate_constrained_but_not_yet_bound'
        Assert-Contract (Test-Path -LiteralPath $witnessPath -PathType Leaf) 'CHILD_WITNESS_MISSING'
        $witnessJson = [IO.File]::ReadAllText($witnessPath)
        $script:ChildWitness = ConvertFrom-Json -InputObject $witnessJson -ErrorAction Stop
        Assert-Contract ([string]$script:ChildWitness.protocol -ceq 'troubleshootjs-terminal-preview-recovery-child-witness-v1' -and
            [string]$script:ChildWitness.case -ceq $Case) 'CHILD_WITNESS_SCHEMA'
        Assert-WitnessBinding $script:ChildWitness $script:ChildEnvelope $witnessPath
        Assert-Contract ([int]$script:ChildWitness.childProcess.processId -eq [int]$script:ChildResult.ProcessId -and
            [long]$script:ChildWitness.childProcess.processStartTicks -eq [long]$script:ChildResult.ProcessStartTicks -and
            [string]$script:ChildWitness.childProcess.executablePath -ieq [string]$powershell.Source) 'CHILD_WITNESS_PROCESS_IDENTITY_MISMATCH'
        $script:WitnessBindingProven = $true
        $script:Report.recovery.witnessBindingProven = $true
        $script:Report.recovery.exactWitnessPath = $witnessPath
        $script:Report.recovery.resourceDisposition = 'child_witness_bound_recovery_eligibility_pending'
        $script:Report.witness = [ordered]@{
            file = $witnessPath
            sha256 = Get-FileSha256 $witnessPath
            phase = [string]$script:ChildWitness.phase
            outcome = [string]$script:ChildWitness.outcome
            runId = [string]$script:ChildWitness.context.runId
            manifestPath = [string]$script:ChildWitness.context.manifestPath
            childProcessId = [int]$script:ChildWitness.childProcess.processId
            childProcessStartTicks = [long]$script:ChildWitness.childProcess.processStartTicks
            browserProcessId = if ($null -ne $script:ChildWitness.browser) { [int]$script:ChildWitness.browser.processId } else { 0 }
            browserProcessStartTicks = if ($null -ne $script:ChildWitness.browser) { [long]$script:ChildWitness.browser.processStartTicks } else { 0L }
            browserProfilePath = if ($null -ne $script:ChildWitness.browser) { [string]$script:ChildWitness.browser.profilePath } else { '' }
            browserCdpPort = if ($null -ne $script:ChildWitness.browser) { [int]$script:ChildWitness.browser.cdpPort } else { 0 }
            previewProcessId = if ($null -ne $script:ChildWitness.preview) { [int]$script:ChildWitness.preview.processId } else { 0 }
            previewProcessStartTicks = if ($null -ne $script:ChildWitness.preview) { [long]$script:ChildWitness.preview.processStartTicks } else { 0L }
            previewPort = if ($null -ne $script:ChildWitness.preview) { [int]$script:ChildWitness.preview.port } else { 0 }
            timing = $script:ChildWitness.timing
        }
        if ([string]$script:ChildEnvelope.status -ceq 'READY') {
            Assert-Contract ([int]$script:ChildResult.ExitCode -eq 0 -and
                [string]$script:ChildWitness.phase -ceq 'ready' -and
                [string]$script:ChildWitness.outcome -ceq 'READY_FOR_PARENT_RECOVERY') 'CHILD_READY_RESULT_MISMATCH'
                Assert-Contract ([int]$script:ChildWitness.browser.processParentProcessId -eq [int]$script:ChildResult.ProcessId -and
                [long]$script:ChildWitness.browser.processParentProcessStartTicks -eq [long]$script:ChildResult.ProcessStartTicks) 'CHILD_BROWSER_PARENT_IDENTITY_MISMATCH'
            Assert-Contract (-not [bool]$script:ChildWitness.timing.cleanupAttempted -and
                -not [bool]$script:ChildWitness.timing.cleanupSucceeded) 'CHILD_READY_AFTER_CLEANUP_ATTEMPT'
            Assert-Contract ([bool]$script:ChildWitness.browser.socketOpen -and
                [string]$script:ChildWitness.browser.expectedUrl -ceq 'about:blank' -and
                [string]$script:ChildWitness.browser.receiptState -ceq 'bound' -and
                -not [bool]$script:ChildWitness.browser.receiptCloseAttempted) 'CHILD_BROWSER_ATTACHMENT_WITNESS'
            if ($Case -ceq 'TerminalPreview') {
                Assert-Contract ($null -ne $script:ChildWitness.preview -and
                    [string]$script:ChildWitness.preview.cleanupResult -ceq 'complete' -and
                    [string]$script:ChildWitness.preview.lease.status -ceq 'released' -and
                    [string]$script:ChildWitness.preview.lease.releaseState -ceq 'complete') 'CHILD_TERMINAL_PREVIEW_WITNESS'
            } else {
                Assert-Contract ($null -eq $script:ChildWitness.preview -and
                    [string]$script:ChildWitness.context.serverOwner -ceq 'none' -and
                    [bool]$script:ChildWitness.context.baseUrlEmpty) 'CHILD_OWNERLESS_WITNESS'
            }
        } else {
            Assert-Contract ([string]$script:ChildEnvelope.status -ceq 'FAIL' -and
                [int]$script:ChildResult.ExitCode -ne 0) 'CHILD_FAIL_EXIT_MISMATCH'
            $script:Report.recovery.resourceDisposition = 'child_reported_failure_cleanup_state_requires_root_review'
        }
        $script:Report.proofs['childExitedWithExactHandleIdentity'] = $true
        $script:Report.proofs['childWitnessRetainedUnderTaskEvidence'] = $true
    } else {
        $script:Report.recovery.resourceDisposition = 'child_result_without_bound_witness_resources_unproven'
        Assert-Contract ([string]$script:ChildEnvelope.status -ceq 'FAIL' -and
            [int]$script:ChildResult.ExitCode -ne 0) 'CHILD_NO_WITNESS_NOT_EXPLICIT_FAILURE'
    }

    $script:Stage = 'source-stability-after-child'
    $script:Report.sourcePinsAfterChild = Get-SourcePinSet
    $script:SourceStable = Test-SourcePinsEqual $script:Report.sourcePinsBefore $script:Report.sourcePinsAfterChild
    Assert-Contract $script:SourceStable 'SOURCE_PINS_CHANGED_AFTER_CHILD'

    if ($script:WitnessBindingProven -and $null -ne $script:ChildWitness -and $null -ne $script:ChildWitness.browser) {
        $manifestPath = [string]$script:ChildWitness.context.manifestPath
        $script:ManifestBefore = Read-DurableManifest $manifestPath
        $eligibility = Assert-EligibleManifestShape $script:ManifestBefore $script:ChildWitness
        $script:RecoveryEligibility = $eligibility
        $script:RecoveryManifestPath = $manifestPath
        $script:PreRecoveryEligibilityProven = $true
        $script:Report.recovery.preRecoveryEligibilityProven = $true
        $script:Report.recovery.resourceDisposition = 'eligible_manifest_pending_retained_browser_recovery'
        $script:Report.manifest = [ordered]@{
            path = $manifestPath
            runId = [string]$script:ManifestBefore.runId
            beforeRecoverySha256 = Get-FileSha256 $manifestPath
            leaseCount = @($script:ManifestBefore.leases).Count
            sessionCount = @($script:ManifestBefore.browserSessions).Count
            cleanupStateBefore = [string]$script:ManifestBefore.cleanup.state
            exactMode = [string]$script:Report.expectedRecoveryMode
            finalSha256 = $null
            cleanupStateAfter = $null
            leaseCountAfter = $null
        }
        $script:Report.proofs['beforeRecoveryManifestExactShape'] = $true
        Assert-Contract ($script:WitnessBindingProven -and $script:SourceStable -and
            [bool]$script:ChildResult.TerminationProven -and
            (([string]$script:ChildEnvelope.status -ceq 'READY' -and [int]$script:ChildResult.ExitCode -eq 0) -or
            ([string]$script:ChildEnvelope.status -ceq 'FAIL' -and [int]$script:ChildResult.ExitCode -ne 0))) 'RECOVERY_SAFETY_PREREQUISITES_NOT_PROVEN'
        $script:RecoverySafeToAttempt = $true
        if ([string]$script:ChildEnvelope.status -ceq 'FAIL') {
            $script:Stage = 'child-setup-failed-recovery-candidate'
            throw [InvalidOperationException]::new('CHILD_SETUP_NOT_READY')
        }
        $remainingForRecovery = 600000 - $script:AggregateWatch.ElapsedMilliseconds
        Assert-Contract ($remainingForRecovery -ge 120000) 'AGGREGATE_BUDGET_CANNOT_RESERVE_CLEANUP'
        Assert-RecoveryStillEligible
        Invoke-MaintainedRecoveryAndProve $manifestPath $script:RecoveryEligibility $false
    } else {
        $script:Stage = 'child-setup-failed'
        throw [InvalidOperationException]::new('CHILD_SETUP_NOT_READY')
    }

    $script:Stage = 'source-stability-after-recovery'
    $script:Report.sourcePinsAfterRecovery = Get-SourcePinSet
    $stableFinal = Test-SourcePinsEqual $script:Report.sourcePinsBefore $script:Report.sourcePinsAfterRecovery
    $script:SourceStable = ($script:SourceStable -and $stableFinal)
    Assert-Contract $stableFinal 'SOURCE_PINS_CHANGED_AFTER_RECOVERY'
    $aggregateMs = [long]$script:AggregateWatch.ElapsedMilliseconds
    Assert-Contract ($aggregateMs -le 600000) 'AGGREGATE_BUDGET_EXCEEDED'
    $script:Report.proofs['sourcePinsStableThroughout'] = $true
    $script:Report.status = 'PASS'
    $script:ExitCode = 0
} catch {
    $script:Failure = $_
    $identity = Get-FailureIdentity $_
    $script:Report.failure = [ordered]@{
        stage = [string]$script:Stage
        type = [string]$identity.type
        id = [string]$identity.id
    }
    $script:Report.status = 'FAIL'
    $script:ExitCode = 2
} finally {
    if ($script:ExitCode -ne 0) {
        try {
            Invoke-FinallyRecoveryIfSafe
        } catch {
            $identity = Get-FailureIdentity $_
            $script:Report.recovery.fallbackFailure = [ordered]@{
                type = [string]$identity.type
                id = [string]$identity.id
            }
            Set-RecoverySkipState 'finally_recovery_handler_failed' 'resources_may_remain_unproven_finally_handler_failed'
            $script:Report.status = 'FAIL'
            $script:ExitCode = 2
        }
    }
    if ($null -ne $script:ChildWitness -and $null -ne $script:ChildWitness.timing) {
        $script:Report.timing.childSetupElapsedMs = [long]$script:ChildWitness.timing.setupElapsedMs
        if ($null -ne $script:ChildWitness.timing.previewTerminalizationElapsedMs) {
            $script:Report.cleanupComponents.previewOwnerCleanupElapsedMs = [long]$script:ChildWitness.timing.previewTerminalizationElapsedMs
        }
    }
    if ($null -ne $script:RecoveryElapsedMs) {
        $previewMs = $script:Report.cleanupComponents.previewOwnerCleanupElapsedMs
        if ($null -ne $previewMs) {
            $script:Report.cleanupComponents.sumOfMeasuredOperationIntervalsMs = [long]($previewMs + $script:RecoveryElapsedMs)
        } else {
            $script:Report.cleanupComponents.sumOfMeasuredOperationIntervalsMs = [long]$script:RecoveryElapsedMs
        }
    }
    $script:AggregateWatch.Stop()
    $script:Report.timing.aggregateElapsedMs = [long]$script:AggregateWatch.ElapsedMilliseconds
    $script:Report.sourceStable = [bool]$script:SourceStable
    $script:Report.preRecoveryEligibilityProven = [bool]$script:PreRecoveryEligibilityProven
    $script:Report.recovery.attempted = [bool]$script:RecoveryAttempted
    $script:Report.recovery.operationElapsedMs = $script:RecoveryElapsedMs
    $script:Report.recovery.cleanupUpperBoundWithin120s = if ($null -ne $script:RecoveryElapsedMs) { [bool]($script:RecoveryElapsedMs -le 120000) } else { $null }
    $script:Report.proofs = $script:FinalProofs
    try {
        Save-Report
    } catch {
        $identity = Get-FailureIdentity $_
        $script:Report.status = 'FAIL'
        $script:ExitCode = 2
        $script:Report.failure = [ordered]@{
            stage = 'result-write'
            type = [string]$identity.type
            id = [string]$identity.id
        }
        try { Save-Report } catch { }
    }
}

if (-not [String]::IsNullOrWhiteSpace($script:ReportPath)) {
    [Console]::Out.WriteLine(('evidence-report=' + $script:ReportPath + ' status=' + $script:Report.status))
}
[Console]::Out.WriteLine((ConvertTo-Json -InputObject ([ordered]@{
    protocol = $script:Report.protocol
    case = $script:Report.case
    status = $script:Report.status
    reportFile = $script:ReportPath
    aggregateElapsedMs = $script:Report.timing.aggregateElapsedMs
}) -Depth 5 -Compress))
exit $script:ExitCode
