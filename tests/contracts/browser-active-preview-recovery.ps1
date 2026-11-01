
[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [ValidateSet('ActivePreview')]
    [string]$Case,
    [Parameter(Mandatory = $true)]
    [string]$RepositoryRoot,
    [Parameter(Mandatory = $true)]
    [string]$EvidenceDirectory,
    [string]$BrowserPath = '',
    [Parameter(Mandatory = $true)]
    [ValidatePattern('^[0-9a-fA-F]{64}$')]
    [string]$ExpectedModuleSha256
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$script:ParentScriptPath = $PSCommandPath
$script:EvidenceRoot = ''
$script:EvidenceRootVerified = $false
$script:TaskNonce = ''
$script:ModulePath = ''
$script:PreviewScriptPath = ''
$script:ChildScriptPath = ''
$script:ReportPath = ''
$script:ReportPersistenceProven = $false
$script:ReportPersistenceErrorType = ''
$script:Stage = 'preflight'
$script:Failure = $null
$script:ExitCode = 2
$script:ChildProcess = $null
$script:ChildProcessIdentity = $null
$script:ChildExitProof = $null
$script:ChildExitObserved = $false
$script:ChildSidechannel = $null
$script:ChildOutputCaptureAttempted = $false
$script:ChildOutputCaptureComplete = $false
$script:ChildOutputEnvelopeVerified = $false
$script:ChildProcessHandleDisposed = $false
$script:ParentProcessStartTicks = 0L
$script:ChildAbsenceProof = $null
$script:MainProofsComplete = $false
$script:FinalHostAbsenceProof = $null
$script:FinalHostProofElapsedMs = $null
$script:OutputCaptureElapsedMs = $null
$script:ChildEnvelope = $null
$script:ChildWitness = $null
$script:ManifestBefore = $null
$script:ManifestAfter = $null
$script:RecoveryResult = $null
$script:RecoveryElapsedMs = $null
$script:RecoveryAttempted = $false
$script:RecoveryInvocationStarted = $false
$script:RecoveryInvocationCompleted = $false
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
    protocol = 'troubleshootjs-verifier-active-preview-recovery-result-v1'
    case = $Case
    status = 'FAIL'
    selectedHost = [ordered]@{
        required = 'WindowsPowerShell 5.1 Desktop'
        actual = $PSVersionTable.PSVersion.ToString()
        edition = [string]$PSVersionTable.PSEdition
    }
    expectedRecoveryMode = 'active-preview'
    sourcePinsBefore = $null
    sourcePinsAfterChild = $null
    sourcePinsAfterModuleImport = $null
    sourcePinsAfterRecovery = $null
    sourcePinsAfterFinalProof = $null
    sourceStable = $false
    privateFailureDetails = @()
    preRecoveryEligibilityProven = $false
    child = [ordered]@{
        processLaunched = $false
        processId = 0
        processStartTicks = 0L
        parentProcessId = 0
        parentProcessStartTicks = 0L
        executablePath = ''
        scriptPath = ''
        launchIdentityProven = $false
        terminationProven = $false
        processExitObserved = $false
        processAbsenceProvenBeforeRecovery = $false
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

        apiReportedElapsedMs = $null

        apiReportedWithin120s = $null
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
        childSetupIncludesPreviewStart = $true
        localCancellationWatchdog = $false
        childWaitAndExitObservationMs = $null
        outputCaptureAndParseMs = $null
        finalExactHostAbsenceProofMs = $null
        timingNote = 'The maintained active-preview recovery entry point is synchronous and internally enforces the unchanged 120000 ms budget. The caller-measured operationElapsedMs and the API-reported ElapsedMs are recorded separately; the caller must bound this process to 600000 ms. Child setup reports preview start and browser attachment separately. The API does not expose separate preview-stop and browser-shutdown durations.'
    }
    failure = $null
    outputCaptureFailure = $null
    reportPersistenceFailure = $null
    finalHostProofs = $null
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
    Assert-Contract ($FileName -ceq 'child-stream-capture.json') 'CHILD_STREAM_ARTIFACT_NAME'
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

function Limit-PrivateFailureField([string]$Value, [int]$MaximumCharacters) {
    if ($null -eq $Value) { return '' }
    if ($Value.Length -le $MaximumCharacters) { return $Value }
    return $Value.Substring(0, $MaximumCharacters)
}

function Write-PrivateFailureDetails($ErrorRecord, [string]$FileName) {
    Assert-Contract ($script:EvidenceRootVerified) 'PRIVATE_FAILURE_EVIDENCE_ROOT_UNVERIFIED'
    Assert-Contract ($FileName -ceq 'parent-failure-main.json' -or
        $FileName -ceq 'parent-failure-finally-recovery.json' -or
        $FileName -ceq 'parent-failure-finally-handler.json' -or
        $FileName -ceq 'parent-failure-output-capture.json' -or
        $FileName -ceq 'parent-failure-final-host-proof.json') 'PRIVATE_FAILURE_ARTIFACT_NAME'
    [void](Assert-VerifierNoReparseAncestors $script:EvidenceRoot)
    $path = Join-Path $script:EvidenceRoot $FileName
    Assert-Contract (Test-VerifierChildPath $script:EvidenceRoot $path) 'PRIVATE_FAILURE_ARTIFACT_PATH'
    Assert-Contract (-not (Test-Path -LiteralPath $path)) 'PRIVATE_FAILURE_ARTIFACT_ALREADY_EXISTS'

    $exception = $null
    if ($null -ne $ErrorRecord) { $exception = $ErrorRecord.Exception }
    $exceptionMessage = ''
    $exceptionText = ''
    $exceptionType = 'Unknown'
    $innerExceptionMessage = ''
    $innerExceptionType = ''
    if ($null -ne $exception) {
        $exceptionType = [string]$exception.GetType().FullName
        $exceptionMessage = [string]$exception.Message
        $exceptionText = [string]$exception.ToString()
        if ($null -ne $exception.InnerException) {
            $innerExceptionType = [string]$exception.InnerException.GetType().FullName
            $innerExceptionMessage = [string]$exception.InnerException.Message
        }
    }
    $errorId = ''
    $category = ''
    $scriptStackTrace = ''
    $positionMessage = ''
    if ($null -ne $ErrorRecord) {
        $errorId = [string]$ErrorRecord.FullyQualifiedErrorId
        if ($null -ne $ErrorRecord.CategoryInfo) { $category = [string]$ErrorRecord.CategoryInfo.ToString() }
        $scriptStackTrace = [string]$ErrorRecord.ScriptStackTrace
        if ($null -ne $ErrorRecord.InvocationInfo) { $positionMessage = [string]$ErrorRecord.InvocationInfo.PositionMessage }
    }
    $helperEvidencePath = ''
    $helperPathMatch = [regex]::Match($exceptionMessage,
        "(?i)(?:logs retained at|bounded-process evidence|evidence retained at|evidence)\s*:?\s*'([^']{1,4096})'")
    if ($helperPathMatch.Success) { $helperEvidencePath = [string]$helperPathMatch.Groups[1].Value }

    $record = [ordered]@{
        protocol = 'troubleshootjs-private-parent-failure-v1'
        stage = [string]$script:Stage
        errorId = Limit-PrivateFailureField $errorId 2048
        category = Limit-PrivateFailureField $category 2048
        exceptionType = Limit-PrivateFailureField $exceptionType 512
        exceptionMessage = Limit-PrivateFailureField $exceptionMessage 4096
        exceptionText = Limit-PrivateFailureField $exceptionText 6000
        innerExceptionType = Limit-PrivateFailureField $innerExceptionType 512
        innerExceptionMessage = Limit-PrivateFailureField $innerExceptionMessage 2048
        scriptStackTrace = Limit-PrivateFailureField $scriptStackTrace 4096
        invocationPosition = Limit-PrivateFailureField $positionMessage 4096
        helperEvidencePath = Limit-PrivateFailureField $helperEvidencePath 4096
    }
    $json = ConvertTo-Json -InputObject $record -Depth 6 -Compress
    $bytes = [Text.UTF8Encoding]::new($false).GetBytes($json)
    Assert-Contract ($bytes.Length -le 65536) 'PRIVATE_FAILURE_ARTIFACT_TOO_LARGE'
    $stream = $null
    try {
        $stream = [IO.File]::Open($path, [IO.FileMode]::CreateNew, [IO.FileAccess]::Write, [IO.FileShare]::None)
        $stream.Write($bytes, 0, $bytes.Length)
        $stream.Flush($true)
    } finally {
        if ($null -ne $stream) { $stream.Dispose() }
    }
    [void](Assert-VerifierPhysicalOwnedPath $script:EvidenceRoot $path)
    $length = [long](Get-Item -LiteralPath $path).Length
    Assert-Contract ($length -eq [long]$bytes.Length -and $length -le 65536) 'PRIVATE_FAILURE_ARTIFACT_LENGTH'
    return [ordered]@{ file = $FileName; byteCount = $length; sha256 = Get-FileSha256 $path }
}

function Record-PrivateFailureDetails($ErrorRecord, [string]$FileName) {
    if (-not $script:EvidenceRootVerified) { return }
    try {
        $artifact = Write-PrivateFailureDetails $ErrorRecord $FileName
        $script:Report.privateFailureDetails = @($script:Report.privateFailureDetails) + @($artifact)
    } catch {
        $identity = Get-FailureIdentity $_
        $failedCapture = [ordered]@{
            file = $FileName
            captureSucceeded = $false
            captureFailureType = [string]$identity.type
            captureFailureId = [string]$identity.id
        }
        $script:Report.privateFailureDetails = @($script:Report.privateFailureDetails) + @($failedCapture)
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


function Assert-ClaimFileMatchesLease($Lease, [string]$Kind, [int]$OwnerPid, [long]$OwnerStartTicks) {
    $path = [string]$Lease.path
    Assert-Contract (-not [String]::IsNullOrWhiteSpace($path) -and
        (Test-VerifierCanonicalWindowsPathValue $path ([string]$Lease.path)) -and
        (Test-VerifierChildPath ([string]$script:ChildWitness.context.runRoot) $path)) 'CLAIM_PATH_BINDING'
    [void](Assert-VerifierNoReparseAncestors $path)
    [void](Assert-VerifierPhysicalOwnedPath ([string]$script:ChildWitness.context.runRoot) $path)
    Assert-Contract (Test-Path -LiteralPath $path -PathType Leaf) 'CLAIM_FILE_MISSING'
    $claim = ConvertFrom-VerifierDurableJson ([IO.File]::ReadAllText($path))
    $leaseGuid = [Guid]::Empty
    Assert-Contract ([Guid]::TryParseExact([string]$Lease.leaseId, 'N', [ref]$leaseGuid) -and
        [string]$Lease.leaseId -ceq $leaseGuid.ToString('N')) 'LEASE_ID_NOT_CANONICAL_GUID_N'
    Assert-Contract ([string]$claim.protocol -ceq 'troubleshootjs-verifier-port-claim-v1' -and
        [string]$claim.runId -ceq [string]$Lease.runId -and
        [string]$claim.repositoryIdentity -ceq [string]$Lease.repositoryIdentity -and
        (Test-VerifierCanonicalWindowsPathValue ([string]$claim.worktreeRoot) $script:RepositoryRoot) -and
        [string]$claim.leaseId -ceq [string]$Lease.leaseId -and
        [string]$claim.path -ceq $path -and
        [string]$claim.kind -ceq $Kind -and
        [int]$claim.port -eq [int]$Lease.port -and
        [string]$claim.mutexName -ceq [string]$Lease.claimName -and
        [int]$claim.ownerPid -eq $OwnerPid -and
        [long]$claim.ownerStartTicks -eq $OwnerStartTicks) 'CLAIM_FILE_TUPLE_MISMATCH'
    Assert-Contract ([string]$Lease.kind -ceq $Kind -and
        [int]$Lease.claimOwnerPid -eq $OwnerPid -and
        [long]$Lease.claimOwnerStartTicks -eq $OwnerStartTicks -and
        [string]$Lease.claim.protocol -ceq [string]$claim.protocol -and
        [string]$Lease.claim.runId -ceq [string]$claim.runId -and
        [string]$Lease.claim.repositoryIdentity -ceq [string]$claim.repositoryIdentity -and
        [string]$Lease.claim.leaseId -ceq [string]$claim.leaseId -and
        [string]$Lease.claim.path -ceq [string]$claim.path -and
        [string]$Lease.claim.kind -ceq [string]$claim.kind -and
        [int]$Lease.claim.port -eq [int]$claim.port -and
        [string]$Lease.claim.mutexName -ceq [string]$claim.mutexName -and
        [int]$Lease.claim.ownerPid -eq [int]$claim.ownerPid -and
        [long]$Lease.claim.ownerStartTicks -eq [long]$claim.ownerStartTicks) 'CLAIM_FILE_MANIFEST_CLAIM_MISMATCH'
    return [pscustomobject]@{ Record = $claim; Sha256 = Get-FileSha256 $path }
}

function Read-PrivateChildOutput([string]$FileName, [long]$MaximumBytes) {
    Assert-Contract ($FileName -ceq 'child.stdout.txt' -or $FileName -ceq 'child.stderr.txt' -or
        $FileName -ceq 'child-exit-envelope.json') 'CHILD_OUTPUT_ARTIFACT_NAME'
    $path = Join-Path $script:EvidenceRoot $FileName
    Assert-Contract (Test-VerifierChildPath $script:EvidenceRoot $path) 'CHILD_OUTPUT_ARTIFACT_PATH'
    [void](Assert-VerifierNoReparseAncestors $script:EvidenceRoot)
    [void](Assert-VerifierPhysicalOwnedPath $script:EvidenceRoot $path)
    Assert-Contract (Test-Path -LiteralPath $path -PathType Leaf) 'CHILD_OUTPUT_ARTIFACT_MISSING'
    $length = [long](Get-Item -LiteralPath $path).Length
    $minimumBytes = if ($FileName -ceq 'child.stderr.txt') { 0L } else { 1L }
    Assert-Contract ($length -ge $minimumBytes -and $length -le $MaximumBytes) 'CHILD_OUTPUT_ARTIFACT_SIZE'
    $bytes = [IO.File]::ReadAllBytes($path)
    Assert-Contract ([long]$bytes.Length -eq $length) 'CHILD_OUTPUT_ARTIFACT_LENGTH_CHANGED'
    $encoding = [Text.UTF8Encoding]::new($false, $true)
    $text = $encoding.GetString($bytes)
    return [pscustomobject]@{
        Text = $text
        Bytes = $bytes
        Receipt = [ordered]@{ file = $FileName; byteCount = $length; sha256 = Get-FileSha256 $path }
    }
}

function Get-ChildSidechannel([string]$Path, [int]$ActualExitCode) {
    Assert-Contract ([string]::Equals([IO.Path]::GetFullPath($Path),
        [IO.Path]::GetFullPath((Join-Path $script:EvidenceRoot 'child-exit-envelope.json')),
        [StringComparison]::OrdinalIgnoreCase)) 'CHILD_SIDECHANNEL_PATH_NOT_EXACT'
    $capture = Read-PrivateChildOutput 'child-exit-envelope.json' 131072
    $sidechannel = ConvertFrom-Json -InputObject $capture.Text -ErrorAction Stop
    Assert-Contract ($null -ne $sidechannel -and $sidechannel -isnot [array] -and
        $sidechannel.GetType().FullName -ceq 'System.Management.Automation.PSCustomObject' -and
        [string]$sidechannel.protocol -ceq 'troubleshootjs-verifier-active-preview-recovery-exit-sidechannel-v1' -and
        [string]$sidechannel.case -ceq $Case -and
        [string]$sidechannel.taskNonce -ceq $script:TaskNonce -and
        [int]$sidechannel.exitCode -eq $ActualExitCode) 'CHILD_SIDECHANNEL_SCHEMA'
    Assert-Contract ($null -ne $sidechannel.childProcess -and $sidechannel.childProcess -isnot [array] -and
        [int]$sidechannel.childProcess.processId -eq [int]$script:ChildProcessIdentity.ProcessId -and
        [long]$sidechannel.childProcess.processStartTicks -eq [long]$script:ChildProcessIdentity.ProcessStartTicks -and
        [int]$sidechannel.childProcess.parentProcessId -eq [int]$script:ChildProcessIdentity.ParentProcessId -and
        [long]$sidechannel.childProcess.parentProcessStartTicks -eq [long]$script:ChildProcessIdentity.ParentProcessStartTicks -and
        [string]::Equals([IO.Path]::GetFullPath([string]$sidechannel.childProcess.executablePath),
            [IO.Path]::GetFullPath([string]$script:ChildProcessIdentity.ExecutablePath), [StringComparison]::OrdinalIgnoreCase) -and
        [string]::Equals([IO.Path]::GetFullPath([string]$sidechannel.childProcess.scriptPath),
            [IO.Path]::GetFullPath([string]$script:ChildScriptPath), [StringComparison]::OrdinalIgnoreCase) -and
        [string]$sidechannel.childProcess.taskNonce -ceq $script:TaskNonce -and
        [string]$sidechannel.childProcess.commandLineSha256 -ceq [string]$script:ChildProcessIdentity.CommandLineSha256) 'CHILD_SIDECHANNEL_LAUNCH_TUPLE'
    foreach ($name in @('moduleSha256','previewSha256','parentTestSha256','childTestSha256')) {
        $sourceProperty = $sidechannel.sourcePins.PSObject.Properties[$name]
        Assert-Contract ($null -ne $sourceProperty -and
            [string]$sourceProperty.Value -ceq [string]$script:Report.sourcePinsBefore[$name]) 'CHILD_SIDECHANNEL_SOURCE_PINS'
    }
    $envelopeJson = [string]$sidechannel.envelopeJson
    Assert-Contract (-not [String]::IsNullOrWhiteSpace($envelopeJson) -and $envelopeJson.Length -le 32768 -and
        $envelopeJson.IndexOf("`r") -lt 0 -and $envelopeJson.IndexOf("`n") -lt 0 -and
        (Get-StringSha256 $envelopeJson).Equals([string]$sidechannel.envelopeSha256, [StringComparison]::OrdinalIgnoreCase)) 'CHILD_SIDECHANNEL_ENVELOPE_HASH'
    $envelope = ConvertFrom-Json -InputObject $envelopeJson -ErrorAction Stop
    Assert-Contract ($null -ne $envelope -and $envelope -isnot [array] -and
        $envelope.GetType().FullName -ceq 'System.Management.Automation.PSCustomObject' -and
        [string]$envelope.protocol -ceq 'troubleshootjs-verifier-active-preview-recovery-child-result-v1' -and
        [string]$envelope.case -ceq $Case -and
        [string]$envelope.taskNonce -ceq $script:TaskNonce -and
        [string]$envelope.status -ceq [string]$sidechannel.status -and
        [string]$envelope.runId -ceq [string]$sidechannel.runId -and
        [string]$envelope.witnessFile -ceq [string]$sidechannel.witnessFile) 'CHILD_SIDECHANNEL_RESULT_ENVELOPE'
    Assert-Contract ((([string]$envelope.status -ceq 'READY' -and $ActualExitCode -eq 0) -or
        ([string]$envelope.status -ceq 'FAIL' -and $ActualExitCode -ne 0))) 'CHILD_SIDECHANNEL_EXIT_STATUS'
    if (-not [String]::IsNullOrWhiteSpace([string]$sidechannel.witnessFile)) {
        $witnessPath = Get-VerifierFullPath ([string]$sidechannel.witnessFile)
        Assert-Contract (Test-VerifierChildPath $script:EvidenceRoot $witnessPath) 'CHILD_SIDECHANNEL_WITNESS_PATH'
        [void](Assert-VerifierPhysicalOwnedPath $script:EvidenceRoot $witnessPath)
        Assert-Contract (Test-Path -LiteralPath $witnessPath -PathType Leaf) 'CHILD_SIDECHANNEL_WITNESS_MISSING'
        Assert-Contract ((Get-FileSha256 $witnessPath).Equals([string]$sidechannel.witnessSha256,
            [StringComparison]::OrdinalIgnoreCase)) 'CHILD_SIDECHANNEL_WITNESS_HASH'
    } else {
        Assert-Contract ([String]::IsNullOrWhiteSpace([string]$sidechannel.witnessSha256)) 'CHILD_SIDECHANNEL_WITNESS_HASH_WITHOUT_FILE'
    }
    $script:ChildSidechannel = $sidechannel
    $script:ChildEnvelope = $envelope
    $script:Report.child.outputCapture.sidechannel = $capture.Receipt
    return $sidechannel
}
function Assert-ChildLaunchIdentity($Process, [string]$ExpectedExecutablePath) {
    Assert-Contract ($null -ne $Process -and $Process.GetType() -eq [Diagnostics.Process]) 'CHILD_LAUNCH_HANDLE_TYPE'
    $processId = [int]$Process.Id
    $processStartTicks = [long](Get-VerifierLaunchedProcessStartTicks $Process)
    Assert-Contract ($processId -gt 0 -and $processStartTicks -gt 0) 'CHILD_LAUNCH_HANDLE_IDENTITY'
    $script:ChildProcessIdentity = [ordered]@{
        ProcessId = $processId
        ProcessStartTicks = $processStartTicks
        ParentProcessId = 0
        ParentProcessStartTicks = 0L
        ExecutablePath = $ExpectedExecutablePath
        ScriptPath = $script:ChildScriptPath
        TaskNonce = $script:TaskNonce
        CommandLine = ''
        CommandLineSha256 = ''
    }
    $script:Report.child.processLaunched = $true
    $script:Report.child.processId = $processId
    $script:Report.child.processStartTicks = $processStartTicks
    $script:Report.child.executablePath = $ExpectedExecutablePath
    $script:Report.child.scriptPath = $script:ChildScriptPath
    $script:ParentProcessStartTicks = [long](Get-VerifierCurrentProcessStartTicks)
    $parentRecord = Get-VerifierCurrentProcessRecordById $PID
    Assert-Contract ($null -ne $parentRecord -and [int]$parentRecord.ProcessId -eq [int]$PID -and
        [long]$parentRecord.ProcessStartTicks -eq [long](Get-VerifierCurrentProcessStartTicks)) 'PARENT_LAUNCH_IDENTITY'
    $current = Get-VerifierCurrentProcessRecordById $processId
    Assert-Contract ($null -ne $current -and [int]$current.ProcessId -eq $processId -and
        [long]$current.ProcessStartTicks -eq $processStartTicks -and
        [int]$current.ParentProcessId -eq [int]$PID -and
        [string]::Equals([IO.Path]::GetFullPath([string]$current.ExecutablePath),
            [IO.Path]::GetFullPath($ExpectedExecutablePath), [StringComparison]::OrdinalIgnoreCase) -and
        [string]::Equals([IO.Path]::GetFullPath([string]$Process.StartInfo.FileName),
            [IO.Path]::GetFullPath($ExpectedExecutablePath), [StringComparison]::OrdinalIgnoreCase)) 'CHILD_LAUNCH_CIM_IDENTITY'
    $commandLine = [string]$current.CommandLine
    Assert-Contract ((Test-VerifierCommandLinePath $commandLine $script:ChildScriptPath) -and
        (Test-VerifierCommandLineSwitch $commandLine '-TaskNonce' $script:TaskNonce) -and
        (Test-VerifierCommandLineSwitch $commandLine '-Case' $Case) -and
        (Test-VerifierCommandLineSwitch $commandLine '-RepositoryRoot' $script:RepositoryRoot) -and
        (Test-VerifierCommandLineSwitch $commandLine '-ModulePath' $script:ModulePath) -and
        (Test-VerifierCommandLineSwitch $commandLine '-EvidenceDirectory' $script:EvidenceRoot) -and
        (Test-VerifierCommandLineSwitch $commandLine '-ExpectedParentProcessId' ([string]$PID)) -and
        (Test-VerifierCommandLineSwitch $commandLine '-ExpectedParentProcessStartTicks' ([string]$parentRecord.ProcessStartTicks)) -and
        (Test-VerifierCommandLineSwitch $commandLine '-ExpectedModuleSha256' ([string]$script:Report.sourcePinsBefore.moduleSha256)) -and
        (Test-VerifierCommandLineSwitch $commandLine '-ExpectedPreviewSha256' ([string]$script:Report.sourcePinsBefore.previewSha256)) -and
        (Test-VerifierCommandLineSwitch $commandLine '-ExpectedParentScriptSha256' ([string]$script:Report.sourcePinsBefore.parentTestSha256)) -and
        (Test-VerifierCommandLineSwitch $commandLine '-ExpectedChildScriptSha256' ([string]$script:Report.sourcePinsBefore.childTestSha256))) 'CHILD_LAUNCH_COMMAND_LINE_BINDING'
    return [ordered]@{
        ProcessId = $processId
        ProcessStartTicks = $processStartTicks
        ParentProcessId = [int]$PID
        ParentProcessStartTicks = [long]$parentRecord.ProcessStartTicks
        ExecutablePath = [string]$current.ExecutablePath
        ScriptPath = [string]$script:ChildScriptPath
        TaskNonce = [string]$script:TaskNonce
        CommandLine = $commandLine
        CommandLineSha256 = Get-StringSha256 $commandLine
    }
}

function Wait-ExactChildProcessExit([int]$WaitMilliseconds) {
    Assert-Contract ($WaitMilliseconds -gt 0 -and $WaitMilliseconds -le 600000) 'CHILD_WAIT_BOUND_INVALID'
    Assert-Contract ($null -ne $script:ChildProcess -and $script:ChildProcess.GetType() -eq [Diagnostics.Process]) 'CHILD_EXIT_HANDLE_MISSING'
    if (-not $script:ChildProcess.WaitForExit($WaitMilliseconds)) { return $false }
    $script:ChildProcess.Refresh()
    Assert-Contract ([bool]$script:ChildProcess.HasExited) 'CHILD_EXIT_HANDLE_NONTERMINAL'
    $exitCode = [int]$script:ChildProcess.ExitCode
    $script:ChildExitObserved = $true
    $script:ChildExitProof = [pscustomobject]@{
        processId = [int]$script:ChildProcessIdentity.ProcessId
        processStartTicks = [long]$script:ChildProcessIdentity.ProcessStartTicks
        exitCode = $exitCode
        processExitObserved = $true
        outputCaptureStatus = 'PENDING'
        fullTerminationProven = $false
    }
    $script:Report.child.processExitObserved = $true
    $script:Report.child.exitCode = $exitCode
    $script:Report.child.outputCapture.status = 'PENDING'
    return $true
}
function Assert-ExactChildProcessAbsent {
    Assert-Contract ($null -ne $script:ChildProcessIdentity -and $script:ChildExitObserved -and
        $null -ne $script:ChildExitProof -and [bool]$script:ChildExitProof.processExitObserved) 'CHILD_EXIT_IDENTITY_NOT_PROVEN'
    $runId = if ($null -ne $script:ChildWitness -and $null -ne $script:ChildWitness.context) {
        [string]$script:ChildWitness.context.runId
    } elseif ($null -ne $script:ChildSidechannel) { [string]$script:ChildSidechannel.runId } else { '' }
    $record = [pscustomobject]@{
        ProcessId = [int]$script:ChildProcessIdentity.ProcessId
        ProcessStartTicks = [long]$script:ChildProcessIdentity.ProcessStartTicks
        CommandLine = [string]$script:ChildProcessIdentity.CommandLine
        ParentProcessId = [int]$script:ChildProcessIdentity.ParentProcessId
        ParentProcessStartTicks = [long]$script:ChildProcessIdentity.ParentProcessStartTicks
    }
    $proof = Confirm-VerifierRecordedProcessAbsent -Recorded $record `
        -Role 'active-preview recovery setup child' `
        -ExpectedCommandLine ([string]$script:ChildProcessIdentity.CommandLine) `
        -Script $script:ChildScriptPath -RunId $runId -Nonce $script:TaskNonce
    Assert-Contract ($proof.QueryProven -and $proof.Absent -and -not $proof.Replaced) 'EXACT_CHILD_PROCESS_ABSENCE_NOT_PROVEN'
    $script:Report.proofs['exactChildPidStartAbsentBeforeRecovery'] = $true
    return $proof
}
function Assert-SidechannelResourceBinding($Witness) {
    $tuple = $script:ChildSidechannel.resourceTuple
    Assert-Contract ($null -ne $tuple -and $tuple -isnot [array] -and
        $tuple.GetType().FullName -ceq 'System.Management.Automation.PSCustomObject') 'CHILD_SIDECHANNEL_RESOURCE_TUPLE_TYPE'
    Assert-Contract ($null -ne $Witness.preview -and $null -ne $Witness.browser -and
        [string]$tuple.runId -ceq [string]$Witness.context.runId -and
        [string]::Equals([IO.Path]::GetFullPath([string]$tuple.manifestPath),
            [IO.Path]::GetFullPath([string]$Witness.context.manifestPath), [StringComparison]::OrdinalIgnoreCase) -and
        [int]$tuple.previewProcessId -eq [int]$Witness.preview.processId -and
        [long]$tuple.previewProcessStartTicks -eq [long]$Witness.preview.processStartTicks -and
        [int]$tuple.previewPort -eq [int]$Witness.preview.port -and
        [string]$tuple.previewLeaseId -ceq [string]$Witness.preview.lease.leaseId -and
        [string]::Equals([IO.Path]::GetFullPath([string]$tuple.previewClaimPath),
            [IO.Path]::GetFullPath([string]$Witness.preview.lease.path), [StringComparison]::OrdinalIgnoreCase) -and
        [int]$tuple.browserProcessId -eq [int]$Witness.browser.processId -and
        [long]$tuple.browserProcessStartTicks -eq [long]$Witness.browser.processStartTicks -and
        [string]::Equals([IO.Path]::GetFullPath([string]$tuple.browserProfilePath),
            [IO.Path]::GetFullPath([string]$Witness.browser.profilePath), [StringComparison]::OrdinalIgnoreCase) -and
        [int]$tuple.cdpPort -eq [int]$Witness.browser.cdpPort -and
        [string]$tuple.cdpLeaseId -ceq [string]$Witness.browser.lease.leaseId -and
        [string]::Equals([IO.Path]::GetFullPath([string]$tuple.cdpClaimPath),
            [IO.Path]::GetFullPath([string]$Witness.browser.lease.path), [StringComparison]::OrdinalIgnoreCase)) 'CHILD_SIDECHANNEL_RESOURCE_TUPLE_BINDING'
}

function Confirm-FinalRecordedProcessAbsent($Recorded, [string]$Role, [string]$ExpectedCommandLine,
        [int]$Port, [string]$Script, [string]$RunId, [string]$Nonce, [string]$Profile) {
    $proof = Confirm-VerifierRecordedProcessAbsent -Recorded $Recorded -Role $Role `
        -ExpectedCommandLine $ExpectedCommandLine -Port $Port -Script $Script `
        -RunId $RunId -Nonce $Nonce -Profile $Profile
    Assert-Contract ($proof.QueryProven -and $proof.Absent -and -not $proof.Replaced) `
        ('FINAL_' + ($Role -replace '[^A-Za-z0-9]','_').ToUpperInvariant() + '_ABSENCE_NOT_PROVEN')
    return $proof
}

function Confirm-FinalEmptyListener([int]$Port, [string]$Role, [int]$ExpectedProcessId,
        [long]$ExpectedStartTicks, [string]$ExpectedCommandLine, [int]$ExpectedParentProcessId,
        [long]$ExpectedParentStartTicks, [string]$Script, [string]$RunId,
        [string]$Nonce, [string]$Profile) {
    $proof = Confirm-VerifierReleasedListener -Port $Port -ExpectedProcessId $ExpectedProcessId `
        -ExpectedStartTicks $ExpectedStartTicks -ExpectedCommandLine $ExpectedCommandLine `
        -ExpectedParentProcessId $ExpectedParentProcessId -ExpectedParentProcessStartTicks $ExpectedParentStartTicks `
        -Script $Script -RunId $RunId -Nonce $Nonce -Profile $Profile
    Assert-Contract ($proof.QueryProven -and $proof.OldOwnerAbsent -and
        -not $proof.PortReused -and -not $proof.PidReused -and
        $proof.Inspection.Success -and $proof.Inspection.Known -and
        -not $proof.Inspection.HasListeners -and @($proof.Inspection.Listeners).Count -eq 0) `
        ('FINAL_' + ($Role -replace '[^A-Za-z0-9]','_').ToUpperInvariant() + '_LISTENER_ABSENCE_NOT_PROVEN')
    return $proof
}

function Assert-FinalExactHostAbsence {
    Assert-Contract ($script:RecoveryInvocationCompleted -and
        $null -ne $script:RecoveryEligibility -and
        $null -ne $script:ChildWitness.preview -and $null -ne $script:ChildWitness.browser) 'FINAL_HOST_PROOF_PRECONDITION'
    $witness = $script:ChildWitness
    $session = $script:RecoveryEligibility.Session
    $server = $script:RecoveryEligibility.Server
    $runId = [string]$script:ChildWitness.context.runId
    $childRecord = [pscustomobject]@{
        ProcessId = [int]$script:ChildProcessIdentity.ProcessId
        ProcessStartTicks = [long]$script:ChildProcessIdentity.ProcessStartTicks
        ParentProcessId = [int]$script:ChildProcessIdentity.ParentProcessId
        ParentProcessStartTicks = [long]$script:ChildProcessIdentity.ParentProcessStartTicks
        CommandLine = [string]$script:ChildProcessIdentity.CommandLine
    }
    $previewRecord = [pscustomobject]@{
        ProcessId = [int]$witness.preview.processId
        ProcessStartTicks = [long]$witness.preview.processStartTicks
        ParentProcessId = [int]$witness.preview.processParentProcessId
        ParentProcessStartTicks = [long]$witness.preview.processParentProcessStartTicks
        CommandLine = [string]$server.processCommandLine
    }
    $browserRecord = [pscustomobject]@{
        ProcessId = [int]$witness.browser.processId
        ProcessStartTicks = [long]$witness.browser.processStartTicks
        ParentProcessId = [int]$witness.browser.processParentProcessId
        ParentProcessStartTicks = [long]$witness.browser.processParentProcessStartTicks
        CommandLine = [string]$session.processCommandLine
    }
    $script:FinalHostAbsenceProof = [ordered]@{
        childProcessAbsent = $false
        previewProcessAbsent = $false
        browserProcessAbsent = $false
        previewPortKnownEmpty = $false
        cdpPortKnownEmpty = $false
        checks = [ordered]@{}
    }
    $childProcessProof = Confirm-FinalRecordedProcessAbsent $childRecord 'recovery setup child' `
        ([string]$script:ChildProcessIdentity.CommandLine) 0 $script:ChildScriptPath `
        $runId $script:TaskNonce ''
    $script:FinalHostAbsenceProof.childProcessAbsent = $true
    $script:FinalHostAbsenceProof.checks.childProcess = [ordered]@{ queryProven = $childProcessProof.QueryProven; absent = $childProcessProof.Absent; replaced = $childProcessProof.Replaced }
    $previewProcessProof = Confirm-FinalRecordedProcessAbsent $previewRecord 'owned preview process' `
        ([string]$server.processCommandLine) ([int]$witness.preview.port) `
        ([string]$server.script) $runId ([string]$server.nonce) ''
    $script:FinalHostAbsenceProof.previewProcessAbsent = $true
    $script:FinalHostAbsenceProof.checks.previewProcess = [ordered]@{ queryProven = $previewProcessProof.QueryProven; absent = $previewProcessProof.Absent; replaced = $previewProcessProof.Replaced }
    $browserProcessProof = Confirm-FinalRecordedProcessAbsent $browserRecord 'owned browser process' `
        ([string]$session.processCommandLine) ([int]$witness.browser.cdpPort) '' `
        $runId '' ([string]$witness.browser.profilePath)
    $script:FinalHostAbsenceProof.browserProcessAbsent = $true
    $script:FinalHostAbsenceProof.checks.browserProcess = [ordered]@{ queryProven = $browserProcessProof.QueryProven; absent = $browserProcessProof.Absent; replaced = $browserProcessProof.Replaced }
    $previewListenerProof = Confirm-FinalEmptyListener ([int]$witness.preview.port) 'owned preview port' `
        ([int]$witness.preview.processId) ([long]$witness.preview.processStartTicks) `
        ([string]$server.processCommandLine) ([int]$witness.preview.processParentProcessId) `
        ([long]$witness.preview.processParentProcessStartTicks) ([string]$server.script) `
        $runId ([string]$server.nonce) ''
    $script:FinalHostAbsenceProof.previewPortKnownEmpty = $true
    $script:FinalHostAbsenceProof.checks.previewPort = [ordered]@{ queryProven = $previewListenerProof.QueryProven; oldOwnerAbsent = $previewListenerProof.OldOwnerAbsent; portReused = $previewListenerProof.PortReused; pidReused = $previewListenerProof.PidReused; listenerCount = @($previewListenerProof.Inspection.Listeners).Count; inspectionSuccess = $previewListenerProof.Inspection.Success; inspectionKnown = $previewListenerProof.Inspection.Known }
    $cdpListenerProof = Confirm-FinalEmptyListener ([int]$witness.browser.cdpPort) 'owned browser CDP port' `
        ([int]$witness.browser.processId) ([long]$witness.browser.processStartTicks) `
        ([string]$session.processCommandLine) ([int]$witness.browser.processParentProcessId) `
        ([long]$witness.browser.processParentProcessStartTicks) '' $runId '' `
        ([string]$witness.browser.profilePath)
    $script:FinalHostAbsenceProof.cdpPortKnownEmpty = $true
    $script:FinalHostAbsenceProof.checks.cdpPort = [ordered]@{ queryProven = $cdpListenerProof.QueryProven; oldOwnerAbsent = $cdpListenerProof.OldOwnerAbsent; portReused = $cdpListenerProof.PortReused; pidReused = $cdpListenerProof.PidReused; listenerCount = @($cdpListenerProof.Inspection.Listeners).Count; inspectionSuccess = $cdpListenerProof.Inspection.Success; inspectionKnown = $cdpListenerProof.Inspection.Known }
    foreach ($name in @('childProcessAbsent','previewProcessAbsent','browserProcessAbsent',
            'previewPortKnownEmpty','cdpPortKnownEmpty')) {
        Assert-Contract ([bool]$script:FinalHostAbsenceProof[$name]) ('FINAL_HOST_PROOF_' + $name.ToUpperInvariant())
    }
    $script:FinalProofs['finalExactProcessAbsenceAndKnownPortsEmpty'] = $true
    $script:Report.finalHostProofs = $script:FinalHostAbsenceProof
    $script:Report.child.outputCapture.finalExactProcessAndListenerAbsenceProven = $true
}

function Complete-ChildOutputCaptureAndProve {
    Assert-Contract (-not $script:ChildOutputCaptureAttempted -and
        $null -ne $script:ChildProcess -and $null -ne $script:ChildProcessIdentity -and
        $script:ChildExitObserved -and $null -ne $script:ChildExitProof -and
        $script:RecoveryInvocationCompleted) 'CHILD_CAPTURE_PRECONDITION'
    $script:ChildOutputCaptureAttempted = $true
    $script:Stage = 'child-output-capture'
    $script:OutputCaptureWatch = [Diagnostics.Stopwatch]::StartNew()
    Complete-VerifierProcessOutputCapture -Process $script:ChildProcess -WaitMilliseconds 5000
    $script:ChildProcess.Refresh()
    Assert-Contract ([bool]$script:ChildProcess.HasExited -and
        [int]$script:ChildProcess.ExitCode -eq [int]$script:ChildExitProof.exitCode) 'CHILD_HANDLE_EXIT_CHANGED_DURING_CAPTURE'
    Assert-Contract ($script:ChildProcess.PSObject.Properties['VerifierRedirectedOutputCaptureComplete'] -and
        [bool]$script:ChildProcess.VerifierRedirectedOutputCaptureComplete) 'CHILD_CAPTURE_COMPLETION_MARKER_MISSING'
    $stdoutTask = $script:ChildProcess.VerifierRedirectedOutputTask
    $stderrTask = $script:ChildProcess.VerifierRedirectedErrorTask
    Assert-Contract ($null -ne $stdoutTask -and $stdoutTask -is [Threading.Tasks.Task] -and
        $null -ne $stderrTask -and $stderrTask -is [Threading.Tasks.Task] -and
        $stdoutTask.IsCompleted -and -not $stdoutTask.IsFaulted -and -not $stdoutTask.IsCanceled -and
        $stderrTask.IsCompleted -and -not $stderrTask.IsFaulted -and -not $stderrTask.IsCanceled) 'CHILD_CAPTURE_TASKS_NOT_COMPLETE'
    $script:Report.child.outputCapture.bothReadTasksCompleted = $true
    $stdout = Read-PrivateChildOutput 'child.stdout.txt' 131072
    $stderr = Read-PrivateChildOutput 'child.stderr.txt' 131072
    $expectedStdout = [string]$script:ChildSidechannel.envelopeJson + [Environment]::NewLine
    $expectedStdoutBytes = [Text.UTF8Encoding]::new($false).GetBytes($expectedStdout)
    $stdoutBytesMatch = ([long]$stdout.Bytes.Length -eq [long]$expectedStdoutBytes.Length)
    if ($stdoutBytesMatch) {
        for ($byteIndex = 0; $byteIndex -lt $expectedStdoutBytes.Length; $byteIndex++) {
            if ($stdout.Bytes[$byteIndex] -ne $expectedStdoutBytes[$byteIndex]) { $stdoutBytesMatch = $false; break }
        }
    }
    Assert-Contract ($stdoutBytesMatch -and [string]::Equals([string]$stdout.Text, $expectedStdout, [StringComparison]::Ordinal)) 'CHILD_STDOUT_SIDECHANNEL_ENVELOPE_MISMATCH'
    $parsed = ConvertFrom-Json -InputObject $stdout.Text -ErrorAction Stop
    Assert-Contract ($null -ne $parsed -and $parsed -isnot [array] -and
        $parsed.GetType().FullName -ceq 'System.Management.Automation.PSCustomObject' -and
        [string]$parsed.protocol -ceq [string]$script:ChildEnvelope.protocol -and
        [string]$parsed.case -ceq [string]$script:ChildEnvelope.case -and
        [string]$parsed.taskNonce -ceq [string]$script:ChildEnvelope.taskNonce -and
        [string]$parsed.status -ceq [string]$script:ChildEnvelope.status -and
        [string]$parsed.runId -ceq [string]$script:ChildEnvelope.runId -and
        [string]$parsed.witnessFile -ceq [string]$script:ChildEnvelope.witnessFile) 'CHILD_STDOUT_ENVELOPE_PARSE_BINDING'
    $script:ChildOutputEnvelopeVerified = $true
    $script:Report.child.outputCapture.envelopeMatchesSidechannel = $true
    $script:Report.child.outputCapture.stdout = $stdout.Receipt
    $script:Report.child.outputCapture.stderr = $stderr.Receipt
    $streamReceipt = [ordered]@{
        protocol = 'troubleshootjs-child-stream-capture-v2'
        processId = [int]$script:ChildProcessIdentity.ProcessId
        processStartTicks = [long]$script:ChildProcessIdentity.ProcessStartTicks
        exitCode = [int]$script:ChildExitProof.exitCode
        processExitObservedByRetainedHandle = $true
        outputCaptureComplete = $true
        stdout = $stdout.Receipt
        stderr = $stderr.Receipt
        envelopeMatchesDurableSidechannel = $true
    }
    $receiptCapture = Write-PrivateChildStreamFile $script:EvidenceRoot 'child-stream-capture.json' `
        (ConvertTo-Json -InputObject $streamReceipt -Depth 6 -Compress)
    $script:Report.child.outputCapture.receipt = $receiptCapture
    $script:ChildOutputCaptureComplete = $true
    $script:ChildExitProof.outputCaptureStatus = 'COMPLETE'
    $script:ChildExitProof.fullTerminationProven = $false
    $script:OutputCaptureWatch.Stop()
    $script:OutputCaptureElapsedMs = [long]$script:OutputCaptureWatch.ElapsedMilliseconds
    $script:Report.timing.outputCaptureAndParseMs = $script:OutputCaptureElapsedMs
    $script:Report.child.outputCapture.status = 'COMPLETE'
    $script:Stage = 'child-process-handle-dispose'
    $script:ChildProcess.Dispose()
    $script:ChildProcessHandleDisposed = $true
    $script:Report.child.outputCapture.handleDisposed = $true
    $script:Report.child.terminationProven = $false
    $script:Stage = 'final-exact-host-absence'
    $finalProofWatch = [Diagnostics.Stopwatch]::StartNew()
    try { Assert-FinalExactHostAbsence } finally {
        $finalProofWatch.Stop()
        $script:FinalHostProofElapsedMs = [long]$finalProofWatch.ElapsedMilliseconds
        $script:Report.timing.finalExactHostAbsenceProofMs = $script:FinalHostProofElapsedMs
    }
    $finalPins = Get-SourcePinSet
    $script:Report.sourcePinsAfterFinalProof = $finalPins
    $stableFinalProof = Test-SourcePinsEqual $script:Report.sourcePinsBefore $finalPins
    $script:SourceStable = ($script:SourceStable -and $stableFinalProof)
    Assert-Contract $stableFinalProof 'SOURCE_PINS_CHANGED_AFTER_FINAL_HOST_PROOF'
    $script:ChildExitProof.fullTerminationProven = $true
    $script:Report.child.terminationProven = $true
    $script:FinalProofs['childOutputCapturedAndParsedAfterRecovery'] = $true
    $script:FinalProofs['childProcessHandleDisposedAfterCapture'] = $true
    $script:Report.proofs = $script:FinalProofs
    Assert-Contract ([long]$script:AggregateWatch.ElapsedMilliseconds -le 600000) 'AGGREGATE_BUDGET_EXCEEDED_AFTER_CAPTURE'
}
function Assert-EligibleManifestShape($Manifest, $Witness) {
    $context = $Witness.context
    $childId = [int]$Witness.childProcess.processId
    $childStart = [long]$Witness.childProcess.processStartTicks
    Assert-Contract ($childId -gt 0 -and $childStart -gt 0) 'WITNESS_CHILD_IDENTITY'
    $runGuid = [Guid]::Empty
    Assert-Contract ([Guid]::TryParseExact([string]$context.runId, 'N', [ref]$runGuid) -and
        [string]$context.runId -ceq $runGuid.ToString('N')) 'RUN_ID_NOT_CANONICAL_GUID_N'
    Assert-Contract ([string]$Manifest.protocol -ceq 'troubleshootjs-verifier-run-v1' -and
        [string]$Manifest.runId -ceq [string]$context.runId -and
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

    Assert-Contract (@($Manifest.cleanup.errors).Count -eq 0) 'MANIFEST_CLEANUP_ERRORS_NOT_EMPTY'

    $sessions = @($Manifest.browserSessions)
    $leases = @($Manifest.leases)
    Assert-Contract ($sessions.Count -eq 1 -and $leases.Count -eq 2 -and
        [int]$context.leaseRecordCount -eq 2 -and [int]$context.browserSessionCount -eq 1 -and
        [string]$context.cleanupState -ceq 'pending') 'ACTIVE_PREVIEW_RESOURCE_CARDINALITY'
    $session = $sessions[0]
    Assert-Contract ([string]$session.status -ceq 'attached' -and
        [string]$session.cleanupResult -ceq 'pending' -and
        [string]$session.expectedUrl -ceq 'about:blank') 'ACTIVE_PREVIEW_BROWSER_NOT_ATTACHED_PENDING'
    Assert-Contract ([string]$session.runId -ceq [string]$Manifest.runId -and
        [string]$session.repositoryIdentity -ceq [string]$Manifest.repositoryIdentity -and
        (Test-VerifierCanonicalWindowsPathValue ([string]$session.worktreeRoot) $script:RepositoryRoot)) 'MANIFEST_SESSION_ROOT_BINDING'
    Assert-Contract ([string]$session.recoveryReceipt.state -ceq 'bound' -and
        $session.recoveryReceipt.closeAttempted -is [bool] -and
        -not [bool]$session.recoveryReceipt.closeAttempted) 'MANIFEST_RECEIPT_NOT_FRESH_BOUND'
    Assert-Contract ([string]$session.profile -ceq [string]$Witness.browser.profilePath -and
        [int]$session.cdpPort -eq [int]$Witness.browser.cdpPort -and
        [string]$session.cdpLeasePath -ceq [string]$Witness.browser.cdpLeasePath -and
        [int]$session.processId -eq [int]$Witness.browser.processId -and
        [long]$session.processStartTicks -eq [long]$Witness.browser.processStartTicks -and
        [int]$session.processParentProcessId -eq $childId -and
        [long]$session.processParentProcessStartTicks -eq $childStart) 'MANIFEST_SESSION_WITNESS_ASSOCIATION'
    Assert-Contract ([int]$session.recoveryReceipt.rootProcessId -eq [int]$Witness.browser.processId -and
        [long]$session.recoveryReceipt.rootProcessStartTicks -eq [long]$Witness.browser.processStartTicks -and
        [int]$session.recoveryReceipt.rootParentProcessId -eq $childId -and
        [long]$session.recoveryReceipt.rootParentProcessStartTicks -eq $childStart -and
        [string]$session.recoveryReceipt.protocol -ceq 'troubleshootjs-verifier-browser-recovery-v1' -and
        [string]$session.recoveryReceipt.runId -ceq [string]$Manifest.runId -and
        [string]$session.recoveryReceipt.repositoryIdentity -ceq [string]$Manifest.repositoryIdentity -and
        (Test-VerifierCanonicalWindowsPathValue ([string]$session.recoveryReceipt.worktreeRoot) $script:RepositoryRoot) -and
        [string]$session.recoveryReceipt.routeId -ceq [string]$session.routeId -and
        [string]$session.recoveryReceipt.routeName -ceq [string]$session.routeName -and
        [string]$session.recoveryReceipt.browserPath -ceq [string]$session.browserPath -and
        [string]$session.recoveryReceipt.profile -ceq [string]$session.profile) 'MANIFEST_RECEIPT_IDENTITY_BINDING'
    Assert-Contract ([bool]$Witness.browser.socketOpen -and
        [string]$Witness.browser.receiptState -ceq 'bound' -and
        -not [bool]$Witness.browser.receiptCloseAttempted -and
        [string]$Witness.browser.status -ceq 'attached' -and
        [string]$Witness.browser.cleanupResult -ceq 'pending' -and
        [int]$Witness.browser.processParentProcessId -eq $childId -and
        [long]$Witness.browser.processParentProcessStartTicks -eq $childStart) 'CHILD_BROWSER_ATTACHMENT_WITNESS'

    $cdpLeases = @($leases | Where-Object { [string]$_.kind -ceq 'cdp' })
    $previewLeases = @($leases | Where-Object { [string]$_.kind -ceq 'preview' })
    Assert-Contract ($cdpLeases.Count -eq 1 -and $previewLeases.Count -eq 1) 'ACTIVE_PREVIEW_LEASE_KINDS'
    $cdpLease = $cdpLeases[0]
    $previewLease = $previewLeases[0]
    Assert-Contract ([int]$cdpLease.port -eq [int]$session.cdpPort -and
        [string]$cdpLease.path -ceq [string]$session.cdpLeasePath -and
        [string]$cdpLease.leaseId -ceq [string]$Witness.browser.lease.leaseId -and
        [string]$session.recoveryReceipt.leaseId -ceq [string]$cdpLease.leaseId -and
        [int]$cdpLease.claimOwnerPid -eq $childId -and
        [long]$cdpLease.claimOwnerStartTicks -eq $childStart -and
        [string]$cdpLease.runId -ceq [string]$Manifest.runId -and
        [string]$cdpLease.repositoryIdentity -ceq [string]$Manifest.repositoryIdentity -and
        (Test-VerifierCanonicalWindowsPathValue ([string]$cdpLease.worktreeRoot) $script:RepositoryRoot) -and
        [string]$cdpLease.status -ceq 'bound' -and [string]$cdpLease.claimState -ceq 'bound' -and
        [string]$cdpLease.releaseState -ceq 'active' -and [string]$cdpLease.releaseJournalState -ceq 'active' -and
        [bool]$cdpLease.processProofRequired -and
        [int]$cdpLease.boundProcessId -eq [int]$session.processId -and
        [long]$cdpLease.boundProcessStartTicks -eq [long]$session.processStartTicks) 'MANIFEST_CDP_ASSOCIATION'
    Assert-Contract ([string]$cdpLease.claim.protocol -ceq 'troubleshootjs-verifier-port-claim-v1' -and
        [string]$cdpLease.claim.mutexName -ceq [string]$cdpLease.claimName -and
        [int]$cdpLease.claim.ownerPid -eq $childId -and
        [long]$cdpLease.claim.ownerStartTicks -eq $childStart) 'MANIFEST_CDP_CLAIM_OWNER_BINDING'
    Assert-Contract ([int]$session.recoveryReceipt.cdpPort -eq [int]$cdpLease.port -and
        [int]$session.recoveryReceipt.rootProcessId -eq [int]$session.processId -and
        [long]$session.recoveryReceipt.rootProcessStartTicks -eq [long]$session.processStartTicks) 'MANIFEST_CDP_RECEIPT_BINDING'

    $previewGuid = [Guid]::Empty
    Assert-Contract ([Guid]::TryParseExact([string]$previewLease.leaseId, 'N', [ref]$previewGuid) -and
        [string]$previewLease.leaseId -ceq $previewGuid.ToString('N')) 'PREVIEW_LEASE_ID_NOT_CANONICAL_GUID_N'
    $preview = $Manifest.server
    Assert-Contract ([string]$Manifest.baseUrl -ceq [string]$preview.baseUrl -and
        -not [String]::IsNullOrWhiteSpace([string]$Manifest.baseUrl) -and
        [string]$preview.owner -ceq 'run' -and [string]$preview.state -ceq 'run-owned-verified' -and
        [string]$preview.cleanupResult -ceq 'pending' -and
        [string]$preview.runId -ceq [string]$Manifest.runId -and
        [string]$preview.repositoryIdentity -ceq [string]$Manifest.repositoryIdentity -and
        [string]$preview.script -ceq [string]$Witness.preview.scriptPath -and
        [string]$preview.baseUrl -ceq [string]$Witness.preview.baseUrl -and
        (Get-StringSha256 ([string]$preview.nonce)).Equals([string]$Witness.preview.nonceSha256, [StringComparison]::OrdinalIgnoreCase) -and
        [string]$Witness.preview.nonceSha256 -ceq [string]$context.previewNonceSha256 -and
        (Test-VerifierCanonicalWindowsPathValue ([string]$preview.worktreeRoot) $script:RepositoryRoot)) 'ACTIVE_PREVIEW_SERVER_BINDING'
    Assert-Contract ([bool]$preview.listenerInspectionProven -eq [bool]$Witness.preview.listenerInspectionProven -and
        [string]$preview.leaseListenerOwnerKind -ceq [string]$previewLease.listenerOwnerKind -and
        [string]$preview.leaseListenerOwnerProof -ceq [string]$previewLease.listenerOwnerProof -and
        [string]$preview.leaseListenerOwnerEvidence -ceq [string]$previewLease.listenerOwnerEvidence -and
        [string]$previewLease.listenerOwnerKind -ceq 'kernel-transport' -and
        [string]$previewLease.listenerOwnerProof -ceq 'run-owned-preview-http-sys-v1' -and
        [string]$previewLease.listenerOwnerEvidence -ceq 'pid-4-system-http-sys') 'ACTIVE_PREVIEW_HTTP_SYS_WITNESS_BINDING'

    Assert-TrueBoolean $preview 'identityVerified'
    Assert-TrueBoolean $preview 'processIdentityKnown'
    Assert-FalseBoolean $preview 'callerOwned'
    Assert-FalseBoolean $preview 'ownershipUncertain'
    Assert-FalseBoolean $preview 'processTerminationProven'
    Assert-FalseBoolean $preview 'processAbsent'
    Assert-Contract ([bool]$Witness.preview.processIdentityKnown -and
        -not [bool]$Witness.preview.ownershipUncertain -and
        -not [bool]$Witness.preview.processTerminationProven -and
        -not [bool]$Witness.preview.processAbsent -and
        [string]$Witness.preview.state -ceq 'run-owned-verified' -and
        [string]$Witness.preview.cleanupResult -ceq 'pending') 'ACTIVE_PREVIEW_WITNESS_STATE'
    Assert-Contract ([int]$preview.processId -eq [int]$Witness.preview.processId -and
        [long]$preview.processStartTicks -eq [long]$Witness.preview.processStartTicks -and
        [int]$preview.processParentProcessId -eq $childId -and
        [long]$preview.processParentProcessStartTicks -eq $childStart -and
        [int]$preview.leaseOwnerPid -eq $childId -and
        [long]$preview.leaseOwnerStartTicks -eq $childStart -and
        [int]$preview.port -eq [int]$previewLease.port -and
        [string]$preview.leaseId -ceq [string]$previewLease.leaseId -and
        [string]$preview.leasePath -ceq [string]$previewLease.path -and
        [string]$preview.leaseClaimName -ceq [string]$previewLease.claimName -and
        [string]$preview.leaseKind -ceq 'preview' -and
        [bool]$preview.leaseProcessProofRequired -eq [bool]$previewLease.processProofRequired) 'ACTIVE_PREVIEW_PROCESS_LEASE_ASSOCIATION'
    Assert-Contract ([string]$previewLease.runId -ceq [string]$Manifest.runId -and
        [string]$previewLease.repositoryIdentity -ceq [string]$Manifest.repositoryIdentity -and
        (Test-VerifierCanonicalWindowsPathValue ([string]$previewLease.worktreeRoot) $script:RepositoryRoot) -and
        [int]$previewLease.claimOwnerPid -eq $childId -and
        [long]$previewLease.claimOwnerStartTicks -eq $childStart -and
        [string]$previewLease.status -ceq 'bound' -and [string]$previewLease.claimState -ceq 'bound' -and
        [string]$previewLease.releaseState -ceq 'active' -and
        [string]$previewLease.releaseJournalState -ceq 'active' -and
        -not [bool]$previewLease.releaseBlocked -and -not [bool]$previewLease.mutexReleased -and
        [bool]$previewLease.processProofRequired -and
        -not [bool]$previewLease.processTerminationProven -and
        -not [bool]$previewLease.processAbsent -and
        [bool]$previewLease.listenerInspectionSuccess -and
        [bool]$previewLease.listenerInspectionKnown -and
        [bool]$previewLease.listenerHasListeners -and
        -not [bool]$previewLease.listenerAbsent -and
        [int]$previewLease.boundProcessId -eq [int]$preview.processId -and
        [long]$previewLease.boundProcessStartTicks -eq [long]$preview.processStartTicks) 'ACTIVE_PREVIEW_LEASE_STATE'
    Assert-Contract ([string]$previewLease.claim.protocol -ceq 'troubleshootjs-verifier-port-claim-v1' -and
        [string]$previewLease.claim.runId -ceq [string]$Manifest.runId -and
        [string]$previewLease.claim.repositoryIdentity -ceq [string]$Manifest.repositoryIdentity -and
        [string]$previewLease.claim.leaseId -ceq [string]$previewLease.leaseId -and
        [string]$previewLease.claim.path -ceq [string]$previewLease.path -and
        [string]$previewLease.claim.kind -ceq 'preview' -and
        [int]$previewLease.claim.port -eq [int]$previewLease.port -and
        [string]$previewLease.claim.mutexName -ceq [string]$previewLease.claimName -and
        [int]$previewLease.claim.ownerPid -eq $childId -and
        [long]$previewLease.claim.ownerStartTicks -eq $childStart) 'ACTIVE_PREVIEW_CLAIM_OWNER_BINDING'
    Assert-Contract ([string]$Witness.preview.lease.leaseId -ceq [string]$previewLease.leaseId -and
        [string]$Witness.preview.lease.path -ceq [string]$previewLease.path -and
        [int]$Witness.preview.lease.port -eq [int]$previewLease.port -and
        [int]$Witness.preview.lease.claimOwnerPid -eq $childId -and
        [long]$Witness.preview.lease.claimOwnerStartTicks -eq $childStart -and
        [bool]$Witness.preview.lease.listenerInspectionSuccess -eq [bool]$previewLease.listenerInspectionSuccess -and
        [bool]$Witness.preview.lease.listenerInspectionKnown -eq [bool]$previewLease.listenerInspectionKnown -and
        [bool]$Witness.preview.lease.listenerHasListeners -eq [bool]$previewLease.listenerHasListeners -and
        [bool]$Witness.preview.lease.listenerAbsent -eq [bool]$previewLease.listenerAbsent -and
        [string]$Witness.preview.lease.listenerOwnerKind -ceq [string]$previewLease.listenerOwnerKind -and
        [string]$Witness.preview.lease.listenerOwnerProof -ceq [string]$previewLease.listenerOwnerProof -and
        [string]$Witness.preview.lease.listenerOwnerEvidence -ceq [string]$previewLease.listenerOwnerEvidence) 'ACTIVE_PREVIEW_LEASE_WITNESS_BINDING'

    $cdpClaimProof = Assert-ClaimFileMatchesLease $cdpLease 'cdp' $childId $childStart
    $previewClaimProof = Assert-ClaimFileMatchesLease $previewLease 'preview' $childId $childStart
    Assert-Contract ((Test-Path -LiteralPath ([string]$cdpLease.path) -PathType Leaf) -and
        (Test-Path -LiteralPath ([string]$previewLease.path) -PathType Leaf)) 'ACTIVE_PREVIEW_CLAIM_FILES_MISSING'
    $script:FinalProofs['activePreviewManifestClaimsAndBrowserBindings'] = $true
    return [pscustomobject]@{
        Manifest = $Manifest
        Session = $session
        CdpLease = $cdpLease
        PreviewLease = $previewLease
        Server = $preview
        Leases = $leases
        CdpClaimSha256 = [string]$cdpClaimProof.Sha256
        PreviewClaimSha256 = [string]$previewClaimProof.Sha256
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
        'claimName', 'claimOwnerPid', 'claimOwnerStartTicks', 'bindValidatedUtc', 'profile', 'browserPath',
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
        'runNamespaceRoot', 'evidenceDirectory', 'evidenceNamespaceRoot', 'manifestPath', 'artifacts',
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
        'protocol', 'authorityToken', 'issuedUtc', 'boundUtc', 'runId', 'repositoryIdentity',
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
    $expectedLeaseCount = if ($Case -ceq 'ActivePreview') { 2 } else { 1 }
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
    if ($Case -ceq 'ActivePreview') {
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
            [string]$Witness.preview.nonceSha256 -ceq [string]$context.previewNonceSha256) 'FINAL_ACTIVE_PREVIEW_IDENTITY'
        Assert-Contract ([string]$server.cleanupResult -ceq 'complete' -and [string]$server.state -ceq 'cleaned') 'FINAL_ACTIVE_PREVIEW_SHAPE'
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
    if ($Case -ceq 'ActivePreview') {
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
    Assert-Contract (@(Get-Command -Name 'Invoke-VerifierRetainedActivePreviewRecovery' -Module VerifierIsolation -ErrorAction SilentlyContinue).Count -eq 1) 'ACTIVE_PREVIEW_RECOVERY_API_NOT_EXPORTED'
}

function Assert-RecoveryStillEligible {
    Assert-Contract ($script:RecoverySafeToAttempt -and $script:PreRecoveryEligibilityProven -and
        $script:WitnessBindingProven -and $script:SourceStable -and
        $null -ne $script:RecoveryEligibility -and
        $null -ne $script:ChildProcessIdentity -and $script:ChildExitObserved -and
        $null -ne $script:ChildExitProof -and [bool]$script:ChildExitProof.processExitObserved -and
        $null -ne $script:ChildAbsenceProof -and $script:ChildAbsenceProof.QueryProven -and $script:ChildAbsenceProof.Absent -and
        $null -ne $script:ChildEnvelope -and $null -ne $script:ChildWitness -and $null -ne $script:ChildWitness.browser -and
        ([string]$script:ChildEnvelope.status -ceq 'READY' -or [string]$script:ChildEnvelope.status -ceq 'FAIL') -and
        (([string]$script:ChildEnvelope.status -ceq 'READY' -and [int]$script:ChildExitProof.exitCode -eq 0) -or
            ([string]$script:ChildEnvelope.status -ceq 'FAIL' -and [int]$script:ChildExitProof.exitCode -ne 0)) -and
        [int]$script:ChildWitness.childProcess.processId -eq [int]$script:ChildProcessIdentity.ProcessId -and
        [long]$script:ChildWitness.childProcess.processStartTicks -eq [long]$script:ChildProcessIdentity.ProcessStartTicks) 'RECOVERY_SAFETY_PREREQUISITES_NOT_PROVEN'
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
    [void](Assert-ClaimFileMatchesLease $currentEligibility.CdpLease 'cdp' ([int]$script:ChildWitness.childProcess.processId) ([long]$script:ChildWitness.childProcess.processStartTicks))
    if ($Case -ceq 'ActivePreview') {
        Assert-Contract (Test-Path -LiteralPath ([string]$currentEligibility.PreviewLease.path) -PathType Leaf) 'PREVIEW_CLAIM_MISSING_BEFORE_RECOVERY'
        [void](Assert-ClaimFileMatchesLease $currentEligibility.PreviewLease 'preview' ([int]$script:ChildWitness.childProcess.processId) ([long]$script:ChildWitness.childProcess.processStartTicks))
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
    $script:RecoveryInvocationStarted = $true
    try {
        $script:RecoveryResult = Invoke-VerifierRetainedActivePreviewRecovery -ManifestPath $ManifestPath
        $recoveryResultType = if ($null -ne $script:RecoveryResult) { [string]$script:RecoveryResult.GetType().FullName } else { '' }
        Assert-Contract ($null -ne $script:RecoveryResult -and $script:RecoveryResult -isnot [array] -and $recoveryResultType -ceq 'System.Management.Automation.PSCustomObject') 'MAINTAINED_RECOVERY_RESULT_TYPE'
    } finally {
        $script:RecoveryInvocationCompleted = $script:RecoveryInvocationStarted
        $recoveryWatch.Stop()
        $script:RecoveryElapsedMs = [long]$recoveryWatch.ElapsedMilliseconds
        $script:Report.recovery.operationElapsedMs = $script:RecoveryElapsedMs
        $script:Report.recovery.cleanupUpperBoundWithin120s = [bool]($script:RecoveryElapsedMs -le 120000)
        $script:Report.cleanupComponents.retainedBrowserRecoveryOperationElapsedMs = $script:RecoveryElapsedMs
    }
    Assert-Contract ($null -ne $script:RecoveryResult -and [bool]$script:RecoveryResult.Success -and
        [string]::Equals([IO.Path]::GetFullPath([string]$script:RecoveryResult.ManifestPath),
            [IO.Path]::GetFullPath($ManifestPath), [StringComparison]::OrdinalIgnoreCase) -and
        [string]$script:RecoveryResult.RunId -ceq [string]$Eligibility.Manifest.runId -and
        [int]$script:RecoveryResult.PreviewPort -eq [int]$script:ChildWitness.preview.port -and
        [int]$script:RecoveryResult.CdpPort -eq [int]$script:ChildWitness.browser.cdpPort -and
        [string]$script:RecoveryResult.PreviewCleanupResult -ceq 'complete' -and
        [string]$script:RecoveryResult.BrowserCleanupResult -ceq 'complete' -and
        [string]$script:RecoveryResult.BrowserCloseLane -ceq 'boundReceiptParentExit' -and
        [long]$script:RecoveryResult.ElapsedMs -ge 0 -and
        [long]$script:RecoveryResult.ElapsedMs -le 120000 -and
        [long]$script:RecoveryResult.ElapsedMs -le [long]$script:RecoveryElapsedMs) 'MAINTAINED_RECOVERY_RETURN_MISMATCH'
    $script:Report.recovery.apiReportedElapsedMs = [long]$script:RecoveryResult.ElapsedMs
    $script:Report.recovery.apiReportedWithin120s = $true
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
    $script:Report.cleanupComponents.previewOwnerCleanupElapsedMs = $null
    $script:Report.cleanupComponents.sumOfMeasuredOperationIntervalsMs = [long]$script:RecoveryElapsedMs
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
            $null -eq $script:ChildProcessIdentity -or -not $script:ChildExitObserved -or
            $null -eq $script:ChildExitProof -or -not [bool]$script:ChildExitProof.processExitObserved -or
            $null -eq $script:ChildAbsenceProof -or -not $script:ChildAbsenceProof.QueryProven -or -not $script:ChildAbsenceProof.Absent -or
            $null -eq $script:ChildEnvelope -or $null -eq $script:ChildWitness -or $null -eq $script:ChildWitness.browser -or
            ([string]$script:ChildEnvelope.status -cne 'READY' -and [string]$script:ChildEnvelope.status -cne 'FAIL') -or
            (([string]$script:ChildEnvelope.status -ceq 'READY' -and [int]$script:ChildExitProof.exitCode -ne 0) -or
                ([string]$script:ChildEnvelope.status -ceq 'FAIL' -and [int]$script:ChildExitProof.exitCode -eq 0)) -or
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
        $failureRecord = $_
        Record-PrivateFailureDetails $failureRecord 'parent-failure-finally-recovery.json'
        $identity = Get-FailureIdentity $failureRecord
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
    $script:ReportPersistenceProven = $false
    if ([String]::IsNullOrWhiteSpace($script:ReportPath)) {
        throw [InvalidOperationException]::new('REPORT_PATH_NOT_SET')
    }
    $script:Report.timing.aggregateElapsedMs = [long]$script:AggregateWatch.ElapsedMilliseconds
    $json = ConvertTo-Json -InputObject $script:Report -Depth 16 -Compress
    [IO.File]::WriteAllText($script:ReportPath, $json, [Text.UTF8Encoding]::new($false))
    if (-not (Test-Path -LiteralPath $script:ReportPath -PathType Leaf)) {
        throw [IO.IOException]::new('REPORT_WRITE_READBACK_FILE_MISSING')
    }
    $persistedJson = [IO.File]::ReadAllText($script:ReportPath)
    if (-not [string]::Equals($persistedJson, $json, [StringComparison]::Ordinal)) {
        throw [IO.IOException]::new('REPORT_WRITE_READBACK_MISMATCH')
    }
    $script:ReportPersistenceProven = $true
    return $true
}

$exitCode = 2
try {
    if ($PSVersionTable.PSVersion.Major -ne 5 -or $PSVersionTable.PSVersion.Minor -lt 1 -or $PSVersionTable.PSEdition -cne 'Desktop') {
        throw [InvalidOperationException]::new('SELECTED_HOST_NOT_WINDOWS_POWERSHELL_5')
    }
    if ([String]::IsNullOrWhiteSpace($RepositoryRoot)) {
        throw [InvalidOperationException]::new('REPOSITORY_ROOT_REQUIRED')
    }
    $RepositoryRoot = [IO.Path]::GetFullPath($RepositoryRoot)
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
    $expectedModulePath = [IO.Path]::GetFullPath((Join-Path $RepositoryRoot 'scripts\VerifierIsolation.psm1'))
    $script:ModulePath = $expectedModulePath
    Assert-Contract ([String]::Equals([IO.Path]::GetFullPath($script:ModulePath), $expectedModulePath, [StringComparison]::OrdinalIgnoreCase)) 'VERIFIER_MODULE_PATH_NOT_CANONICAL'
    $script:PreviewScriptPath = Join-Path $RepositoryRoot 'scripts\preview.ps1'
    $script:ChildScriptPath = Join-Path $PSScriptRoot 'browser-active-preview-recovery-child.ps1'
    if (-not (Test-Path -LiteralPath $script:ModulePath -PathType Leaf) -or
            -not (Test-Path -LiteralPath $script:PreviewScriptPath -PathType Leaf) -or
            -not (Test-Path -LiteralPath $script:ChildScriptPath -PathType Leaf)) {
        throw [InvalidOperationException]::new('SOURCE_INPUT_MISSING')
    }
    $modulePin = Get-FileSha256 $script:ModulePath
    if ($ExpectedModuleSha256 -notmatch '^[0-9a-fA-F]{64}$') {
        throw [InvalidOperationException]::new('EXPECTED_MODULE_PIN_FORMAT_INVALID')
    }
    $modulePinExpected = $ExpectedModuleSha256
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
    Assert-Contract (@(Get-Command -Name 'Invoke-VerifierRetainedActivePreviewRecovery' -Module VerifierIsolation -ErrorAction SilentlyContinue).Count -eq 1) 'ACTIVE_PREVIEW_RECOVERY_API_NOT_EXPORTED'
    foreach ($requiredOwner in @('Start-VerifierProcess','Complete-VerifierProcessOutputCapture',
            'Confirm-VerifierRecordedProcessAbsent','Confirm-VerifierReleasedListener')) {
        Assert-Contract (@(Get-Command -Name $requiredOwner -Module VerifierIsolation -ErrorAction SilentlyContinue).Count -eq 1) ('REQUIRED_FINAL_PROOF_OWNER_NOT_EXPORTED_' + $requiredOwner)
    }
    $tempRoot = Get-VerifierFullPath ([IO.Path]::GetTempPath())
    [void](Assert-VerifierNoReparseAncestors $requestedEvidence)
    [void](Assert-VerifierPhysicalOwnedPath $tempRoot $requestedEvidence -ValidateTree)
    $script:EvidenceRootVerified = $true
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
        '-ModulePath', $script:ModulePath,
        '-EvidenceDirectory', $requestedEvidence,
        '-BrowserPath', $browser,
        '-TaskNonce', $script:TaskNonce,
        '-ExpectedModuleSha256', $modulePinExpected,
        '-ExpectedPreviewSha256', $actualPreviewPin,
        '-ExpectedParentScriptSha256', $actualParentPin,
        '-ExpectedChildScriptSha256', $actualChildPin
    )
    $script:Stage = 'child-process'
    $remainingMs = [int][Math]::Floor(600000 - $script:AggregateWatch.ElapsedMilliseconds - 120000)
    Assert-Contract ($remainingMs -gt 0) 'AGGREGATE_BUDGET_CANNOT_RESERVE_RECOVERY_WINDOW'
    $stdoutPath = Join-Path $requestedEvidence 'child.stdout.txt'
    $stderrPath = Join-Path $requestedEvidence 'child.stderr.txt'
    $sidechannelPath = Join-Path $requestedEvidence 'child-exit-envelope.json'
    foreach ($ownedPath in @($stdoutPath, $stderrPath, $sidechannelPath)) {
        Assert-Contract (Test-VerifierChildPath $requestedEvidence $ownedPath) 'CHILD_CAPTURE_PATH_OUTSIDE_EVIDENCE'
        [void](Assert-VerifierPhysicalOwnedPath $requestedEvidence $ownedPath)
        Assert-Contract (-not (Test-Path -LiteralPath $ownedPath)) 'CHILD_CAPTURE_PATH_NOT_UNIQUE'
    }
    $parentRecord = Get-VerifierCurrentProcessRecordById $PID
    Assert-Contract ($null -ne $parentRecord -and [int]$parentRecord.ProcessId -eq [int]$PID) 'PARENT_LAUNCH_IDENTITY'
    $script:ParentProcessStartTicks = [long]$parentRecord.ProcessStartTicks
    Assert-Contract ($script:ParentProcessStartTicks -gt 0) 'PARENT_LAUNCH_START_IDENTITY'
    $childArgs += @(
        '-ExpectedParentProcessId', [string]$PID,
        '-ExpectedParentProcessStartTicks', [string]$script:ParentProcessStartTicks
    )
    $script:Report.child.outputCapture = [ordered]@{
        status = 'PENDING'
        stdoutPath = 'child.stdout.txt'
        stderrPath = 'child.stderr.txt'
        sidechannelPath = 'child-exit-envelope.json'
        sidechannel = $null
        stdout = $null
        stderr = $null
        receipt = $null
        envelopeMatchesSidechannel = $false
        bothReadTasksCompleted = $false
        handleDisposed = $false
        finalExactProcessAndListenerAbsenceProven = $false
    }
    $childWatch = [Diagnostics.Stopwatch]::StartNew()
    $script:ChildProcess = Start-VerifierProcess $powershell.Source $childArgs $stdoutPath $stderrPath
    $script:ChildProcessIdentity = Assert-ChildLaunchIdentity $script:ChildProcess $powershell.Source
    $script:Report.child.processLaunched = $true
    $script:Report.child.processId = [int]$script:ChildProcessIdentity.ProcessId
    $script:Report.child.processStartTicks = [long]$script:ChildProcessIdentity.ProcessStartTicks
    $script:Report.child.parentProcessId = [int]$script:ChildProcessIdentity.ParentProcessId
    $script:Report.child.parentProcessStartTicks = [long]$script:ChildProcessIdentity.ParentProcessStartTicks
    $script:Report.child.executablePath = [string]$script:ChildProcessIdentity.ExecutablePath
    $script:Report.child.scriptPath = [string]$script:ChildProcessIdentity.ScriptPath
    $script:Report.child.launchIdentityProven = $true

    $script:Stage = 'child-process-exit'
    $waitCompleted = $false
    try { $waitCompleted = Wait-ExactChildProcessExit $remainingMs } finally {
        $childWatch.Stop()
        $script:Report.child.elapsedMs = [long]$childWatch.ElapsedMilliseconds
        $script:Report.child.processExitElapsedMs = [long]$childWatch.ElapsedMilliseconds
        $script:Report.timing.childWaitAndExitObservationMs = [long]$childWatch.ElapsedMilliseconds
    }
    Assert-Contract $waitCompleted 'CHILD_PROCESS_EXIT_TIMEOUT'


    $script:ChildSidechannel = Get-ChildSidechannel $sidechannelPath ([int]$script:ChildExitProof.exitCode)
    $script:Report.child.exitCode = [int]$script:ChildExitProof.exitCode
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
        Assert-Contract ([string]$script:ChildWitness.protocol -ceq 'troubleshootjs-verifier-active-preview-recovery-child-witness-v1' -and
            [string]$script:ChildWitness.case -ceq $Case) 'CHILD_WITNESS_SCHEMA'
        Assert-WitnessBinding $script:ChildWitness $script:ChildEnvelope $witnessPath
        if ([string]$script:ChildEnvelope.status -ceq 'READY') { Assert-SidechannelResourceBinding $script:ChildWitness }
        Assert-Contract ([int]$script:ChildWitness.childProcess.processId -eq [int]$script:ChildProcessIdentity.ProcessId -and
            [long]$script:ChildWitness.childProcess.processStartTicks -eq [long]$script:ChildProcessIdentity.ProcessStartTicks -and
            [int]$script:ChildWitness.childProcess.parentProcessId -eq [int]$script:ChildProcessIdentity.ParentProcessId -and
            [long]$script:ChildWitness.childProcess.parentProcessStartTicks -eq [long]$script:ChildProcessIdentity.ParentProcessStartTicks -and
            [string]$script:ChildWitness.childProcess.executablePath -ieq [string]$script:ChildProcessIdentity.ExecutablePath -and
            [string]$script:ChildWitness.childProcess.scriptPath -ieq [string]$script:ChildScriptPath -and
            [string]$script:ChildWitness.childProcess.taskNonce -ceq $script:TaskNonce -and
            [string]$script:ChildWitness.childProcess.commandLineSha256 -ceq [string]$script:ChildProcessIdentity.CommandLineSha256) 'CHILD_WITNESS_PROCESS_IDENTITY_MISMATCH'
        $script:WitnessBindingProven = $true
        $script:Report.recovery.witnessBindingProven = $true
        $script:ChildAbsenceProof = Assert-ExactChildProcessAbsent
        $script:Report.child.processAbsenceProvenBeforeRecovery = $true
        $script:Report.recovery.childProcessAbsenceProven = $true
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
            Assert-Contract ([int]$script:ChildExitProof.exitCode -eq 0 -and
                [string]$script:ChildWitness.phase -ceq 'ready' -and
                [string]$script:ChildWitness.outcome -ceq 'READY_FOR_PARENT_RECOVERY') 'CHILD_READY_RESULT_MISMATCH'
                Assert-Contract ([int]$script:ChildWitness.browser.processParentProcessId -eq [int]$script:ChildProcessIdentity.ProcessId -and
                [long]$script:ChildWitness.browser.processParentProcessStartTicks -eq [long]$script:ChildProcessIdentity.ProcessStartTicks) 'CHILD_BROWSER_PARENT_IDENTITY_MISMATCH'
            Assert-Contract (-not [bool]$script:ChildWitness.timing.cleanupAttempted -and
                -not [bool]$script:ChildWitness.timing.cleanupSucceeded) 'CHILD_READY_AFTER_CLEANUP_ATTEMPT'
            Assert-Contract ([bool]$script:ChildWitness.browser.socketOpen -and
                [string]$script:ChildWitness.browser.expectedUrl -ceq 'about:blank' -and
                [string]$script:ChildWitness.browser.receiptState -ceq 'bound' -and
                -not [bool]$script:ChildWitness.browser.receiptCloseAttempted) 'CHILD_BROWSER_ATTACHMENT_WITNESS'
            Assert-Contract ($null -ne $script:ChildWitness.preview -and

                [string]$script:ChildWitness.preview.owner -ceq 'run' -and

                [string]$script:ChildWitness.preview.state -ceq 'run-owned-verified' -and

                [string]$script:ChildWitness.preview.cleanupResult -ceq 'pending' -and

                [bool]$script:ChildWitness.preview.processIdentityKnown -and

                -not [bool]$script:ChildWitness.preview.ownershipUncertain -and

                -not [bool]$script:ChildWitness.preview.processTerminationProven -and

                -not [bool]$script:ChildWitness.preview.processAbsent -and

                [int]$script:ChildWitness.preview.processParentProcessId -eq [int]$script:ChildProcessIdentity.ProcessId -and

                [long]$script:ChildWitness.preview.processParentProcessStartTicks -eq [long]$script:ChildProcessIdentity.ProcessStartTicks -and

                [string]$script:ChildWitness.preview.lease.status -ceq 'bound' -and

                [string]$script:ChildWitness.preview.lease.claimState -ceq 'bound' -and

                [string]$script:ChildWitness.preview.lease.releaseState -ceq 'active' -and

                [string]$script:ChildWitness.preview.lease.releaseJournalState -ceq 'active' -and

                [int]$script:ChildWitness.preview.lease.claimOwnerPid -eq [int]$script:ChildProcessIdentity.ProcessId -and

                [long]$script:ChildWitness.preview.lease.claimOwnerStartTicks -eq [long]$script:ChildProcessIdentity.ProcessStartTicks) 'CHILD_ACTIVE_PREVIEW_WITNESS'
        } else {
            Assert-Contract ([string]$script:ChildEnvelope.status -ceq 'FAIL' -and
                [int]$script:ChildExitProof.exitCode -ne 0) 'CHILD_FAIL_EXIT_MISMATCH'
            $script:Report.recovery.resourceDisposition = 'child_reported_failure_cleanup_state_requires_root_review'
        }
        $script:Report.proofs['childExitedWithExactHandleIdentity'] = $true
        $script:Report.proofs['childWitnessRetainedUnderTaskEvidence'] = $true
    } else {
        $script:Report.recovery.resourceDisposition = 'child_result_without_bound_witness_resources_unproven'
        Assert-Contract ([string]$script:ChildEnvelope.status -ceq 'FAIL' -and
            [int]$script:ChildExitProof.exitCode -ne 0) 'CHILD_NO_WITNESS_NOT_EXPLICIT_FAILURE'
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
            cdpClaimSha256Before = [string]$eligibility.CdpClaimSha256
            previewClaimSha256Before = [string]$eligibility.PreviewClaimSha256
            exactMode = [string]$script:Report.expectedRecoveryMode
            finalSha256 = $null
            cleanupStateAfter = $null
            leaseCountAfter = $null
        }
        $script:Report.proofs['beforeRecoveryManifestExactShape'] = $true
        Assert-Contract ($script:WitnessBindingProven -and $script:SourceStable -and
            $script:ChildExitObserved -and $null -ne $script:ChildAbsenceProof -and
            $script:ChildAbsenceProof.QueryProven -and $script:ChildAbsenceProof.Absent -and -not $script:ChildAbsenceProof.Replaced -and
            (([string]$script:ChildEnvelope.status -ceq 'READY' -and [int]$script:ChildExitProof.exitCode -eq 0) -or
            ([string]$script:ChildEnvelope.status -ceq 'FAIL' -and [int]$script:ChildExitProof.exitCode -ne 0))) 'RECOVERY_SAFETY_PREREQUISITES_NOT_PROVEN'
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
    $script:MainProofsComplete = $true
} catch {
    $script:Failure = $_
    Record-PrivateFailureDetails $script:Failure 'parent-failure-main.json'
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
            $failureRecord = $_
            Record-PrivateFailureDetails $failureRecord 'parent-failure-finally-handler.json'
            $identity = Get-FailureIdentity $failureRecord
            $script:Report.recovery.fallbackFailure = [ordered]@{
                type = [string]$identity.type
                id = [string]$identity.id
            }
            Set-RecoverySkipState 'finally_recovery_handler_failed' 'resources_may_remain_unproven_finally_handler_failed'
            $script:Report.status = 'FAIL'
            $script:ExitCode = 2
        }
    }
    if ($script:ChildExitObserved -and $script:RecoveryInvocationCompleted -and
            -not $script:ChildOutputCaptureAttempted) {
        try {
            Complete-ChildOutputCaptureAndProve
        } catch {
            $failureRecord = $_
            $failureFile = if ([string]$script:Stage -ceq 'final-exact-host-absence') {
                'parent-failure-final-host-proof.json'
            } else { 'parent-failure-output-capture.json' }
            Record-PrivateFailureDetails $failureRecord $failureFile
            $identity = Get-FailureIdentity $failureRecord
            if ($null -ne $script:OutputCaptureWatch) {
                $script:OutputCaptureWatch.Stop()
                $script:Report.timing.outputCaptureAndParseMs = [long]$script:OutputCaptureWatch.ElapsedMilliseconds
            }
            if (-not $script:ChildOutputCaptureComplete -and $null -ne $script:Report.child.outputCapture) {
                $script:Report.child.outputCapture.status = 'FAILED'
            }
            if ($null -ne $script:FinalHostAbsenceProof) { $script:Report.finalHostProofs = $script:FinalHostAbsenceProof }
            $script:Report.outputCaptureFailure = [ordered]@{
                type = [string]$identity.type
                id = [string]$identity.id
                stage = [string]$script:Stage
            }
            $script:Report.status = 'FAIL'
            $script:ExitCode = 2
        }
    } elseif ($script:ChildExitObserved -and -not $script:ChildOutputCaptureAttempted -and
            $null -ne $script:Report.child.outputCapture) {
        $script:Report.child.outputCapture.status = 'NOT_ATTEMPTED_RECOVERY_NOT_PROVEN'
    }
    if ($script:MainProofsComplete -and $script:RecoveryAttempted -and
            $script:Report.recovery.success -and $script:ChildOutputCaptureComplete -and
            $script:ChildOutputEnvelopeVerified -and $script:ChildProcessHandleDisposed -and
            $null -ne $script:ChildExitProof -and [bool]$script:ChildExitProof.fullTerminationProven -and
            $null -ne $script:FinalHostAbsenceProof -and $script:SourceStable -and
            $script:Report.child.outputCapture.finalExactProcessAndListenerAbsenceProven) {
        $script:Report.status = 'PASS'
        $script:ExitCode = 0
    } else {
        if ($script:ExitCode -eq 0) { $script:ExitCode = 2 }
        if ($script:Report.status -cne 'PASS') { $script:Report.status = 'FAIL' }
    }
    if ($null -ne $script:ChildWitness -and $null -ne $script:ChildWitness.timing) {
        $script:Report.timing.childSetupElapsedMs = [long]$script:ChildWitness.timing.setupElapsedMs
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
        $savedReport = Save-Report
        if (-not [bool]$savedReport) { throw [InvalidOperationException]::new('REPORT_WRITE_NOT_PROVEN') }
    } catch {
        $identity = Get-FailureIdentity $_
        $script:ReportPersistenceProven = $false
        $script:ReportPersistenceErrorType = [string]$identity.type
        $script:Report.status = 'FAIL'
        $script:ExitCode = 2
        $script:Report.reportPersistenceFailure = [ordered]@{
            stage = 'result-write'
            type = [string]$identity.type
            id = [string]$identity.id
        }
        if ($null -eq $script:Report.failure) {
            $script:Report.failure = [ordered]@{
                stage = 'result-write'
                type = [string]$identity.type
                id = [string]$identity.id
            }
        }
        try {
            $retrySavedReport = Save-Report
            if (-not [bool]$retrySavedReport) { throw [InvalidOperationException]::new('REPORT_RETRY_WRITE_NOT_PROVEN') }
        } catch {
            $retryIdentity = Get-FailureIdentity $_
            $script:ReportPersistenceProven = $false
            $script:ReportPersistenceErrorType = [string]$retryIdentity.type
            [Console]::Error.WriteLine(('report-persistence=UNPROVEN type=' + [string]$retryIdentity.type))
        }
    }
}

if ($script:ExitCode -eq 0 -and -not $script:ReportPersistenceProven) {
    $script:Report.status = 'FAIL'
    $script:ExitCode = 2
    $script:ReportPersistenceErrorType = 'ReportPersistenceNotProven'
    if ($null -eq $script:Report.reportPersistenceFailure) {
        $script:Report.reportPersistenceFailure = [ordered]@{
            stage = 'result-write-final-guard'
            type = 'InvalidOperationException'
            id = 'REPORT_WRITE_NOT_PROVEN'
        }
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
    reportPersisted = [bool]$script:ReportPersistenceProven
    reportPersistenceFailureType = [string]$script:ReportPersistenceErrorType
}) -Depth 5 -Compress))
exit $script:ExitCode
