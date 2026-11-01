
[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [ValidateSet('ActivePreview')]
    [string]$Case,
    [Parameter(Mandatory = $true)]
    [string]$RepositoryRoot,
    [Parameter(Mandatory = $true)]
    [string]$ModulePath,
    [Parameter(Mandatory = $true)]
    [string]$EvidenceDirectory,
    [string]$BrowserPath = '',
    [Parameter(Mandatory = $true)]
    [string]$ExpectedModuleSha256,
    [Parameter(Mandatory = $true)]
    [string]$ExpectedPreviewSha256,
    [Parameter(Mandatory = $true)]
    [string]$ExpectedParentScriptSha256,
    [Parameter(Mandatory = $true)]
    [string]$ExpectedChildScriptSha256,
    [Parameter(Mandatory = $true)]
    [ValidateRange(1, 2147483647)]
    [int]$ExpectedParentProcessId,
    [Parameter(Mandatory = $true)]
    [ValidateRange(1, 9223372036854775807)]
    [long]$ExpectedParentProcessStartTicks,
    [Parameter(Mandatory = $true)]
    [ValidatePattern('^[0-9a-f]{32}$')]
    [string]$TaskNonce
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$script:Stage = 'preflight'
$script:Context = $null
$script:BrowserSession = $null
$script:ChildStartTicks = 0L
$script:ChildExecutablePath = ''
$script:ChildParentProcessId = 0
$script:ChildParentProcessStartTicks = 0L
$script:ChildCommandLine = ''
$script:SetupWatch = [Diagnostics.Stopwatch]::StartNew()
$script:PreviewElapsedMs = $null
$script:BrowserElapsedMs = $null
$script:CleanupAttempted = $false
$script:CleanupSucceeded = $false
$script:CleanupElapsedMs = $null
$script:CleanupFailureType = ''
$script:CleanupFailureId = ''
$script:LatestWitness = ''
$script:FinalizationFailureType = ''
$script:FinalizationFailureId = ''
$script:FinalizationRetryFailureType = ''
$script:FinalizationRetryFailureId = ''
$script:FinalizationWitnessFailureType = ''
$script:FinalizationWitnessFailureId = ''

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

function Assert-ExactPin([string]$Name, [string]$Path, [string]$Expected) {
    if ([String]::IsNullOrWhiteSpace($Expected) -or $Expected -notmatch '^[0-9a-fA-F]{64}$' -or -not (Test-Path -LiteralPath $Path -PathType Leaf)) {
        throw [InvalidOperationException]::new('SOURCE_PIN_INPUT_INVALID')
    }
    $actual = Get-FileSha256 $Path
    if (-not $actual.Equals($Expected, [StringComparison]::OrdinalIgnoreCase)) {
        throw [InvalidOperationException]::new(('SOURCE_PIN_CHANGED_' + $Name))
    }
}

function Get-LeaseWitness($Lease) {
    if ($null -eq $Lease) { return $null }
    return [ordered]@{
        kind = [string]$Lease.Kind
        leaseId = [string]$Lease.LeaseId
        port = [int]$Lease.Port
        path = [string]$Lease.Path
        profilePath = [string]$Lease.ProfilePath
        browserPath = [string]$Lease.BrowserPath
        status = [string]$Lease.Status
        claimState = [string]$Lease.ClaimState
        releaseState = [string]$Lease.ReleaseState
        releaseJournalState = [string]$Lease.ReleaseJournalState
        claimOwnerPid = [int]$Lease.ClaimOwnerPid
        claimOwnerStartTicks = [long]$Lease.ClaimOwnerStartTicks
        releaseBlocked = [bool]$Lease.ReleaseBlocked
        mutexReleased = [bool]$Lease.MutexReleased
        processProofRequired = [bool]$Lease.ProcessProofRequired
        processTerminationProven = [bool]$Lease.ProcessTerminationProven
        processAbsent = [bool]$Lease.ProcessAbsent
        listenerInspectionSuccess = [bool]$Lease.ListenerInspectionSuccess
        listenerInspectionKnown = [bool]$Lease.ListenerInspectionKnown
        listenerHasListeners = $Lease.ListenerHasListeners
        listenerAbsent = $Lease.ListenerAbsent
        boundProcessId = [int]$Lease.BoundProcessId
        boundProcessStartTicks = [long]$Lease.BoundProcessStartTicks
        listenerProcessId = [int]$Lease.ListenerProcessId
        listenerProcessStartTicks = $Lease.ListenerProcessStartTicks
        listenerOwnerKind = [string]$Lease.ListenerOwnerKind
        listenerOwnerProof = [string]$Lease.ListenerOwnerProof
        listenerOwnerEvidence = [string]$Lease.ListenerOwnerEvidence
        claimFilePresent = [bool](Test-Path -LiteralPath ([string]$Lease.Path) -PathType Leaf)
    }
}

function Write-ChildExitSidechannel([string]$EnvelopeJson, [int]$ExpectedExitCode, $Envelope,
        [string]$ModulePath, [string]$PreviewScriptPath, [string]$ParentScriptPath, [string]$ChildScriptPath, [string]$EvidenceDirectory) {
    if ($EnvelopeJson.Length -gt 32768 -or $EnvelopeJson.IndexOf("`r") -ge 0 -or $EnvelopeJson.IndexOf("`n") -ge 0) {
        throw [InvalidOperationException]::new('CHILD_EXIT_ENVELOPE_SIZE_OR_LINE_INVALID')
    }
    $sourcePins = [ordered]@{
        moduleSha256 = Get-FileSha256 $ModulePath
        previewSha256 = Get-FileSha256 $PreviewScriptPath
        parentTestSha256 = Get-FileSha256 $ParentScriptPath
        childTestSha256 = Get-FileSha256 $ChildScriptPath
    }
    $witnessPath = [string]$script:LatestWitness
    $witnessSha256 = ''
    if (-not [String]::IsNullOrWhiteSpace($witnessPath)) { $witnessSha256 = Get-FileSha256 $witnessPath }
    $resourceTuple = $null
    if ($null -ne $script:Context) {
        $preview = $script:Context.Server
        $browser = if (@($script:Context.BrowserSessions).Count -eq 1) { $script:Context.BrowserSessions[0] } else { $null }
        $resourceTuple = [ordered]@{
            runId = [string]$script:Context.RunId
            manifestPath = [string]$script:Context.ManifestPath
            previewProcessId = if ($null -ne $preview) { [int]$preview.ProcessId } else { 0 }
            previewProcessStartTicks = if ($null -ne $preview) { [long]$preview.ProcessStartTicks } else { 0L }
            previewPort = if ($null -ne $preview) { [int]$preview.Port } else { 0 }
            previewLeaseId = if ($null -ne $preview -and $null -ne $preview.Lease) { [string]$preview.Lease.LeaseId } else { '' }
            previewClaimPath = if ($null -ne $preview -and $null -ne $preview.Lease) { [string]$preview.Lease.Path } else { '' }
            browserProcessId = if ($null -ne $browser) { [int]$browser.ProcessId } else { 0 }
            browserProcessStartTicks = if ($null -ne $browser) { [long]$browser.ProcessStartTicks } else { 0L }
            browserProfilePath = if ($null -ne $browser) { [string]$browser.Profile } else { '' }
            cdpPort = if ($null -ne $browser) { [int]$browser.CdpPort } else { 0 }
            cdpLeaseId = if ($null -ne $browser -and $null -ne $browser.Lease) { [string]$browser.Lease.LeaseId } else { '' }
            cdpClaimPath = if ($null -ne $browser -and $null -ne $browser.Lease) { [string]$browser.Lease.Path } else { '' }
        }
    }
    $sidechannel = [ordered]@{
        protocol = 'troubleshootjs-verifier-active-preview-recovery-exit-sidechannel-v1'
        case = $Case
        taskNonce = $TaskNonce
        status = [string]$Envelope.status
        exitCode = $ExpectedExitCode
        runId = [string]$Envelope.runId
        witnessFile = $witnessPath
        witnessSha256 = $witnessSha256
        sourcePins = $sourcePins
        childProcess = [ordered]@{
            processId = [int]$PID
            processStartTicks = [long]$script:ChildStartTicks
            executablePath = [string]$script:ChildExecutablePath
            parentProcessId = [int]$script:ChildParentProcessId
            parentProcessStartTicks = [long]$script:ChildParentProcessStartTicks
            scriptPath = [string]$ChildScriptPath
            taskNonce = [string]$TaskNonce
            commandLineSha256 = Get-StringSha256 ([string]$script:ChildCommandLine)
        }
        resourceTuple = $resourceTuple
        envelopeJson = $EnvelopeJson
        envelopeSha256 = Get-StringSha256 $EnvelopeJson
    }
    $json = ConvertTo-Json -InputObject $sidechannel -Depth 12 -Compress
    $bytes = [Text.UTF8Encoding]::new($false).GetBytes($json)
    if ($bytes.Length -gt 131072) { throw [InvalidOperationException]::new('CHILD_EXIT_SIDECAR_TOO_LARGE') }
    $tempRoot = Get-VerifierFullPath ([IO.Path]::GetTempPath())
    $path = Join-Path $EvidenceDirectory 'child-exit-envelope.json'
    if (-not (Test-VerifierChildPath $EvidenceDirectory $path) -or (Test-Path -LiteralPath $path)) {
        throw [InvalidOperationException]::new('CHILD_EXIT_SIDECAR_PATH_INVALID')
    }
    [void](Assert-VerifierNoReparseAncestors $EvidenceDirectory)
    $stream = $null
    try {
        $stream = [IO.File]::Open($path, [IO.FileMode]::CreateNew, [IO.FileAccess]::Write, [IO.FileShare]::None)
        $stream.Write($bytes, 0, $bytes.Length)
        $stream.Flush($true)
    } finally {
        if ($null -ne $stream) { $stream.Dispose() }
    }
    [void](Assert-VerifierPhysicalOwnedPath $tempRoot $path)
    if ([long](Get-Item -LiteralPath $path).Length -ne [long]$bytes.Length -or
            -not (Get-FileSha256 $path).Equals((Get-StringSha256 $json), [StringComparison]::OrdinalIgnoreCase)) {
        throw [InvalidOperationException]::new('CHILD_EXIT_SIDECAR_DURABILITY_PROOF_FAILED')
    }
    return $path
}
function Get-WitnessSnapshot([string]$Phase, [string]$Outcome) {
    $preview = $null
    $server = $null
    if ($null -ne $script:Context -and $null -ne $script:Context.Server) {
        $serverRecord = $script:Context.Server
        $previewLease = $null
        if ($null -ne $serverRecord.Lease) { $previewLease = Get-LeaseWitness $serverRecord.Lease }
        $serverExe = ''
        if ($null -ne $serverRecord.Process) {
            try { $serverExe = [string]$serverRecord.Process.StartInfo.FileName } catch { }
        }
        $server = [ordered]@{
            owner = [string]$serverRecord.Owner
            baseUrl = [string]$serverRecord.BaseUrl
            nonceSha256 = Get-StringSha256 ([string]$serverRecord.Nonce)
            port = [int]$serverRecord.Port
            processId = [int]$serverRecord.ProcessId
            processStartTicks = [long]$serverRecord.ProcessStartTicks
            processParentProcessId = [int]$serverRecord.ProcessParentProcessId
            processParentProcessStartTicks = [long]$serverRecord.ProcessParentProcessStartTicks
            executablePath = $serverExe
            scriptPath = [string]$serverRecord.Script
            state = [string]$serverRecord.State
            cleanupResult = [string]$serverRecord.CleanupResult
            processIdentityKnown = [bool]$serverRecord.ProcessIdentityKnown
            ownershipUncertain = [bool]$serverRecord.OwnershipUncertain
            processTerminationProven = [bool]$serverRecord.ProcessTerminationProven
            processAbsent = [bool]$serverRecord.ProcessAbsent
            listenerInspectionProven = [bool]$serverRecord.ListenerInspectionProven
            listenerAbsent = $serverRecord.ListenerAbsent
            lease = $previewLease
        }
        if ([string]$serverRecord.Owner -ceq 'run') { $preview = $server }
    }

    $browser = $null
    if ($null -ne $script:Context -and $script:Context.BrowserSessions.Count -eq 1) {
        $record = $script:Context.BrowserSessions[0]
        $browserLease = Get-LeaseWitness $record.Lease
        $socketOpen = $false
        if ($null -ne $script:BrowserSession -and $null -ne $script:BrowserSession.Socket) {
            $socketOpen = ($script:BrowserSession.Socket.State -eq [Net.WebSockets.WebSocketState]::Open)
        }
        $browser = [ordered]@{
            processId = [int]$record.ProcessId
            processStartTicks = [long]$record.ProcessStartTicks
            processParentProcessId = [int]$record.ProcessParentProcessId
            processParentProcessStartTicks = [long]$record.ProcessParentProcessStartTicks
            executablePath = [string]$record.BrowserPath
            routeId = [string]$record.RouteId
            routeName = [string]$record.RouteName
            profilePath = [string]$record.Profile
            cdpPort = [int]$record.CdpPort
            cdpLeasePath = [string]$record.Lease.Path
            status = [string]$record.Status
            cleanupResult = [string]$record.CleanupResult
            expectedUrl = [string]$record.ExpectedUrl
            receiptState = [string]$record.RecoveryReceipt.State
            receiptCloseAttempted = [bool]$record.RecoveryReceipt.CloseAttempted
            socketOpen = $socketOpen
            lease = $browserLease
        }
    }

    $contextView = $null
    if ($null -ne $script:Context) {
        $contextView = [ordered]@{
            runId = [string]$script:Context.RunId
            repositoryIdentity = [string]$script:Context.RepositoryIdentity
            previewNonceSha256 = Get-StringSha256 ([string]$script:Context.PreviewNonce)
            worktreeRoot = [string]$script:Context.WorktreeRoot
            runRoot = [string]$script:Context.RunRoot
            runNamespaceRoot = [string]$script:Context.RunNamespaceRoot
            manifestPath = [string]$script:Context.ManifestPath
            evidenceDirectory = [string]$script:Context.EvidenceDirectory
            evidenceNamespaceRoot = [string]$script:Context.EvidenceNamespaceRoot
            portLeaseRoot = [string]$script:Context.PortLeaseRoot
            baseUrlEmpty = [String]::IsNullOrEmpty([string]$script:Context.BaseUrl)
            serverOwner = [string]$script:Context.Server.Owner
            cleanupState = [string]$script:Context.CleanupState
            leaseRecordCount = @($script:Context.LeaseRecords).Count
            browserSessionCount = @($script:Context.BrowserSessions).Count
        }
    }

    return [ordered]@{
        protocol = 'troubleshootjs-verifier-active-preview-recovery-child-witness-v1'
        taskNonce = $TaskNonce
        case = $Case
        phase = $Phase
        outcome = $Outcome
        childProcess = [ordered]@{
            processId = $PID
            processStartTicks = [long]$script:ChildStartTicks
            executablePath = $script:ChildExecutablePath
            parentProcessId = [int]$script:ChildParentProcessId
            parentProcessStartTicks = [long]$script:ChildParentProcessStartTicks
            scriptPath = [string]$PSCommandPath
            taskNonce = [string]$TaskNonce
            commandLineSha256 = Get-StringSha256 ([string]$script:ChildCommandLine)
        }
        context = $contextView
        preview = $preview
        browser = $browser
        timing = [ordered]@{
            setupElapsedMs = [long]$script:SetupWatch.ElapsedMilliseconds
            previewStartElapsedMs = $script:PreviewElapsedMs
            browserStartElapsedMs = $script:BrowserElapsedMs
            cleanupAttempted = [bool]$script:CleanupAttempted
            cleanupSucceeded = [bool]$script:CleanupSucceeded
            cleanupElapsedMs = $script:CleanupElapsedMs
            cleanupFailureType = $script:CleanupFailureType
            cleanupFailureId = $script:CleanupFailureId
            sidechannelFailureType = $script:FinalizationFailureType
            sidechannelFailureId = $script:FinalizationFailureId
            sidechannelRetryFailureType = $script:FinalizationRetryFailureType
            sidechannelRetryFailureId = $script:FinalizationRetryFailureId
            witnessFailureType = $script:FinalizationWitnessFailureType
            witnessFailureId = $script:FinalizationWitnessFailureId
        }
    }
}

function Write-Witness([string]$Phase, [string]$Outcome) {
    if ($null -eq $script:Context) { return '' }
    $name = switch ($Phase) {
        'context-created' { 'witness-start.json' }
        'preview-ready' { 'witness-preview-ready.json' }
        'browser-attached' { 'witness-browser-attached.json' }
        'ready' { 'witness-ready.json' }
        'failure' { 'witness-failure.json' }
        'sidechannel-failure' { 'witness-sidechannel-failure.json' }
        default { throw [InvalidOperationException]::new('WITNESS_PHASE_INVALID') }
    }
    $path = Join-Path $script:Context.EvidenceDirectory $name
    if (Test-Path -LiteralPath $path) {
        throw [InvalidOperationException]::new('WITNESS_PATH_ALREADY_EXISTS')
    }
    $json = ConvertTo-Json -InputObject (Get-WitnessSnapshot $Phase $Outcome) -Depth 16 -Compress
    [IO.File]::WriteAllText($path, $json, [Text.UTF8Encoding]::new($false))
    $script:LatestWitness = $path
    return $path
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

$exitCode = 0
$failure = $null
$childEnvelope = $null

try {
    if ($PSVersionTable.PSVersion.Major -ne 5 -or $PSVersionTable.PSVersion.Minor -lt 1 -or $PSVersionTable.PSEdition -cne 'Desktop') {
        throw [InvalidOperationException]::new('SELECTED_HOST_NOT_WINDOWS_POWERSHELL_5')
    }
    if ([String]::IsNullOrWhiteSpace($RepositoryRoot)) {
        throw [InvalidOperationException]::new('REPOSITORY_ROOT_REQUIRED')
    }
    $RepositoryRoot = [IO.Path]::GetFullPath($RepositoryRoot)
    if ([String]::IsNullOrWhiteSpace($ModulePath)) { throw [InvalidOperationException]::new('MODULE_PATH_REQUIRED') }
    $expectedModulePath = [IO.Path]::GetFullPath((Join-Path $RepositoryRoot 'scripts\VerifierIsolation.psm1'))
    $modulePath = [IO.Path]::GetFullPath($ModulePath)
    if (-not [String]::Equals($modulePath, $expectedModulePath, [StringComparison]::OrdinalIgnoreCase)) {
        throw [InvalidOperationException]::new('MODULE_PATH_NOT_CANONICAL')
    }
    $previewScript = Join-Path $RepositoryRoot 'scripts\preview.ps1'
    $parentScript = Join-Path $PSScriptRoot 'browser-active-preview-recovery.ps1'
    $childScript = [IO.Path]::GetFullPath($PSCommandPath)
    $script:Stage = 'source-pins'
    Assert-ExactPin 'module' $modulePath $ExpectedModuleSha256
    Assert-ExactPin 'preview' $previewScript $ExpectedPreviewSha256
    Assert-ExactPin 'parent-test' $parentScript $ExpectedParentScriptSha256
    Assert-ExactPin 'child-test' $childScript $ExpectedChildScriptSha256
    Import-Module -Name $modulePath -Force -DisableNameChecking -ErrorAction Stop
    $module = @(Get-Module -Name 'VerifierIsolation' | Where-Object {
        [IO.Path]::GetFullPath([string]$_.Path).Equals([IO.Path]::GetFullPath($modulePath), [StringComparison]::OrdinalIgnoreCase)
    })
    if ($module.Count -ne 1) { throw [InvalidOperationException]::new('VERIFIER_MODULE_IMPORT_AMBIGUOUS') }
    if ([String]::IsNullOrWhiteSpace($EvidenceDirectory)) { throw [InvalidOperationException]::new('EVIDENCE_DIRECTORY_REQUIRED') }
    $EvidenceDirectory = [IO.Path]::GetFullPath($EvidenceDirectory)
    $tempRoot = Get-VerifierFullPath ([IO.Path]::GetTempPath())
    if (-not (Test-VerifierChildPath $tempRoot $EvidenceDirectory) -or (Test-VerifierChildPath $RepositoryRoot $EvidenceDirectory)) {
        throw [InvalidOperationException]::new('EVIDENCE_DIRECTORY_OUTSIDE_TASK_TEMP_SCOPE')
    }
    [void](Assert-VerifierNoReparseAncestors $EvidenceDirectory)
    [void](Assert-VerifierPhysicalOwnedPath $tempRoot $EvidenceDirectory)
    if (-not (Test-Path -LiteralPath $EvidenceDirectory -PathType Container)) {
        throw [InvalidOperationException]::new('EVIDENCE_DIRECTORY_NOT_CREATED_BY_PARENT')
    }
    $browser = Resolve-VerifierBrowserPath $BrowserPath
    if ([String]::IsNullOrWhiteSpace($browser) -or -not (Test-Path -LiteralPath $browser -PathType Leaf)) {
        throw [InvalidOperationException]::new('BROWSER_EXECUTABLE_UNAVAILABLE')
    }

    $self = [Diagnostics.Process]::GetCurrentProcess()
    try {
        $script:ChildStartTicks = Get-VerifierProcessStartTicks $self
        $script:ChildExecutablePath = [string]$self.MainModule.FileName
    } finally {
        if ($null -ne $self) { $self.Dispose() }
    }
    if ($script:ChildStartTicks -le 0 -or [String]::IsNullOrWhiteSpace($script:ChildExecutablePath)) {
        throw [InvalidOperationException]::new('CHILD_PROCESS_IDENTITY_MISSING')
    }
    $childCurrent = Get-VerifierCurrentProcessRecordById $PID
    $parentCurrent = Get-VerifierCurrentProcessRecordById $ExpectedParentProcessId
    if ($null -eq $childCurrent -or $null -eq $parentCurrent -or
            [int]$childCurrent.ProcessId -ne [int]$PID -or
            [long]$childCurrent.ProcessStartTicks -ne [long]$script:ChildStartTicks -or
            [int]$childCurrent.ParentProcessId -ne [int]$ExpectedParentProcessId -or
            [long]$parentCurrent.ProcessStartTicks -ne [long]$ExpectedParentProcessStartTicks -or
            -not [string]::Equals([IO.Path]::GetFullPath([string]$childCurrent.ExecutablePath),
                [IO.Path]::GetFullPath($script:ChildExecutablePath), [StringComparison]::OrdinalIgnoreCase) -or
            -not (Test-VerifierCommandLinePath $childCurrent.CommandLine $childScript) -or
            -not (Test-VerifierCommandLineSwitch $childCurrent.CommandLine '-TaskNonce' $TaskNonce) -or
            -not (Test-VerifierCommandLineSwitch $childCurrent.CommandLine '-RepositoryRoot' $RepositoryRoot) -or
            -not (Test-VerifierCommandLineSwitch $childCurrent.CommandLine '-ModulePath' $modulePath) -or
            -not (Test-VerifierCommandLineSwitch $childCurrent.CommandLine '-EvidenceDirectory' $EvidenceDirectory) -or
            -not (Test-VerifierCommandLinePath $parentCurrent.CommandLine $parentScript)) {
        throw [InvalidOperationException]::new('CHILD_PARENT_NATIVE_IDENTITY_MISMATCH')
    }
    $script:ChildParentProcessId = [int]$childCurrent.ParentProcessId
    $script:ChildParentProcessStartTicks = [long]$parentCurrent.ProcessStartTicks
    $script:ChildCommandLine = [string]$childCurrent.CommandLine

    $script:Stage = 'context-created'
    $script:Context = New-VerifierRunContext $RepositoryRoot $EvidenceDirectory
    # Persist the ownership root before starting the preview or browser.
    $initialWitness = Write-Witness 'context-created' 'SETUP_IN_PROGRESS'
    $script:Stage = 'active-preview-start'
    $previewWatch = [Diagnostics.Stopwatch]::StartNew()
    $baseUrl = Start-VerifierOwnedPreview $script:Context $previewScript 60 $false
    $script:PreviewElapsedMs = [long]$previewWatch.ElapsedMilliseconds
    if ($script:Context.Server.Owner -cne 'run' -or
            $script:Context.Server.State -cne 'run-owned-verified' -or
            $script:Context.Server.CleanupResult -cne 'pending' -or
            -not [bool]$script:Context.Server.IdentityVerified -or
            -not [bool]$script:Context.Server.ProcessIdentityKnown -or
            [bool]$script:Context.Server.OwnershipUncertain -or
            [String]::IsNullOrWhiteSpace([string]$baseUrl) -or
            [string]$script:Context.Server.BaseUrl -cne [string]$baseUrl -or
            [int]$script:Context.Server.ProcessId -le 0 -or
            [long]$script:Context.Server.ProcessStartTicks -le 0 -or
            [int]$script:Context.Server.ProcessParentProcessId -ne $PID -or
            [long]$script:Context.Server.ProcessParentProcessStartTicks -ne $script:ChildStartTicks) {
        throw [InvalidOperationException]::new('OWNED_ACTIVE_PREVIEW_START_NOT_EXACT')
    }
    [void](Write-Witness 'preview-ready' 'SETUP_IN_PROGRESS')

    $script:Stage = 'browser-attach'
    $browserWatch = [Diagnostics.Stopwatch]::StartNew()
    $script:BrowserSession = New-VerifierBrowserSession $script:Context 'active-preview-recovery' 'about:blank' $browser 45 $false
    $script:BrowserElapsedMs = [long]$browserWatch.ElapsedMilliseconds
    $record = $script:BrowserSession.Record
    if ($null -eq $record -or $script:BrowserSession.Browser.GetType() -ne [Diagnostics.Process] -or [bool]$script:BrowserSession.Browser.HasExited -or [int]$script:BrowserSession.Browser.Id -ne [int]$record.ProcessId -or [long]$record.ProcessStartTicks -le 0 -or [string]$record.ExpectedUrl -cne 'about:blank' -or [string]$record.Status -cne 'attached' -or [string]$record.CleanupResult -cne 'pending' -or [string]$record.RecoveryReceipt.State -cne 'bound' -or [bool]$record.RecoveryReceipt.CloseAttempted -or $script:BrowserSession.Socket.State -ne [Net.WebSockets.WebSocketState]::Open) {
        throw [InvalidOperationException]::new('BROWSER_FACTORY_ATTACHMENT_PROOF_INCOMPLETE')
    }
    [void](Assert-VerifierDurableBrowserSession $record $script:Context 'terminal preview recovery child attached session')
    [void](Write-Witness 'browser-attached' 'SETUP_IN_PROGRESS')

    $script:Stage = 'active-preview-preserved'
    $previewRecord = $script:Context.Server
    $previewLease = $previewRecord.Lease
    $previewProcess = $previewRecord.Process
    $browserRecord = $script:BrowserSession.Record
    if ($null -eq $previewProcess -or [bool]$previewProcess.HasExited -or
            [int]$previewProcess.Id -ne [int]$previewRecord.ProcessId -or
            [long]$previewRecord.ProcessStartTicks -le 0 -or
            $previewRecord.Owner -cne 'run' -or $previewRecord.State -cne 'run-owned-verified' -or
            $previewRecord.CleanupResult -cne 'pending' -or -not [bool]$previewRecord.IdentityVerified -or
            -not [bool]$previewRecord.ProcessIdentityKnown -or [bool]$previewRecord.OwnershipUncertain -or
            $null -eq $previewLease -or $previewLease.Status -cne 'bound' -or
            $previewLease.ClaimState -cne 'bound' -or $previewLease.ReleaseState -cne 'active' -or
            $previewLease.ReleaseJournalState -cne 'active' -or [bool]$previewLease.MutexReleased -or
            [bool]$previewLease.ReleaseBlocked -or [int]$previewLease.ClaimOwnerPid -ne $PID -or
            [long]$previewLease.ClaimOwnerStartTicks -ne $script:ChildStartTicks -or
            [int]$previewLease.BoundProcessId -ne [int]$previewRecord.ProcessId -or
            [long]$previewLease.BoundProcessStartTicks -ne [long]$previewRecord.ProcessStartTicks -or
            -not [bool]$previewLease.ListenerInspectionSuccess -or
            -not [bool]$previewLease.ListenerInspectionKnown -or
            -not [bool]$previewLease.ListenerHasListeners -or [bool]$previewLease.ListenerAbsent -or
            [bool]$previewLease.ProcessTerminationProven -or [bool]$previewLease.ProcessAbsent -or
            -not (Test-Path -LiteralPath $previewLease.Path -PathType Leaf)) {
        throw [InvalidOperationException]::new('ACTIVE_PREVIEW_OWNER_PROOF_INCOMPLETE')
    }
    if ($null -eq $browserRecord -or [bool]$script:BrowserSession.Browser.HasExited -or
            $script:BrowserSession.Socket.State -ne [Net.WebSockets.WebSocketState]::Open -or
            $browserRecord.Status -cne 'attached' -or $browserRecord.CleanupResult -cne 'pending' -or
            $browserRecord.RecoveryReceipt.State -cne 'bound' -or [bool]$browserRecord.RecoveryReceipt.CloseAttempted -or
            [int]$browserRecord.ProcessParentProcessId -ne $PID -or
            [long]$browserRecord.ProcessParentProcessStartTicks -ne $script:ChildStartTicks) {
        throw [InvalidOperationException]::new('ACTIVE_PREVIEW_BROWSER_ATTACHMENT_PROOF_INCOMPLETE')
    }
    [void](Assert-VerifierDurableServerLease $script:Context 'active-preview child preserved server')
    [void](Assert-VerifierDurableBrowserSession $browserRecord $script:Context 'active-preview child preserved browser')
    if ($script:Context.CleanupState -cne 'pending' -or
            @($script:Context.LeaseRecords).Count -ne 2 -or
            @($script:Context.BrowserSessions).Count -ne 1) {
        throw [InvalidOperationException]::new('ACTIVE_PREVIEW_CONTEXT_RESOURCE_GRAPH_NOT_EXACT')
    }
    [void](Write-VerifierManifest $script:Context)

    $script:Stage = 'source-pins-final'
    Assert-ExactPin 'module' $modulePath $ExpectedModuleSha256
    Assert-ExactPin 'preview' $previewScript $ExpectedPreviewSha256
    Assert-ExactPin 'parent-test' $parentScript $ExpectedParentScriptSha256
    Assert-ExactPin 'child-test' $childScript $ExpectedChildScriptSha256
    $script:Stage = 'ready-witness'
    [void](Write-Witness 'ready' 'READY_FOR_PARENT_RECOVERY')
    $script:SetupWatch.Stop()
    $childEnvelope = [ordered]@{
        protocol = 'troubleshootjs-verifier-active-preview-recovery-child-result-v1'
        status = 'READY'
        case = $Case
        taskNonce = $TaskNonce
        runId = [string]$script:Context.RunId
        witnessFile = $script:LatestWitness
    }
} catch {
    $failure = $_
    if ($null -ne $script:Context) {
        $script:Stage = 'failure-cleanup'
        $cleanupWatch = [Diagnostics.Stopwatch]::StartNew()
        $script:CleanupAttempted = $true
        try {
            $cleanup = Complete-VerifierRun $script:Context
            $script:CleanupSucceeded = ($null -ne $cleanup -and $cleanup.PSObject.Properties['Success'] -and [bool]$cleanup.Success -and [string]$script:Context.CleanupState -ceq 'complete')
        } catch {
            $cleanupIdentity = Get-FailureIdentity $_
            $script:CleanupFailureType = [string]$cleanupIdentity.type
            $script:CleanupFailureId = [string]$cleanupIdentity.id
        } finally {
            $script:CleanupElapsedMs = [long]$cleanupWatch.ElapsedMilliseconds
            $cleanupWatch.Stop()
        }
        try {
            $script:Stage = 'failure-witness'
            $script:LatestWitness = Write-Witness 'failure' 'SETUP_OR_CLEANUP_FAILED'
        } catch { }
    }
}

if ($null -eq $failure) {
    $childEnvelope = [ordered]@{
        protocol = 'troubleshootjs-verifier-active-preview-recovery-child-result-v1'
        status = 'READY'
        case = $Case
        taskNonce = $TaskNonce
        runId = [string]$script:Context.RunId
        witnessFile = $script:LatestWitness
    }
} else {
    $failureIdentity = Get-FailureIdentity $failure
    $childEnvelope = [ordered]@{
        protocol = 'troubleshootjs-verifier-active-preview-recovery-child-result-v1'
        status = 'FAIL'
        case = $Case
        taskNonce = $TaskNonce
        runId = if ($null -ne $script:Context) { [string]$script:Context.RunId } else { '' }
        witnessFile = [string]$script:LatestWitness
        stage = [string]$script:Stage
        failureType = [string]$failureIdentity.type
        failureId = [string]$failureIdentity.id
        cleanupAttempted = [bool]$script:CleanupAttempted
        cleanupSucceeded = [bool]$script:CleanupSucceeded
        cleanupElapsedMs = $script:CleanupElapsedMs
        cleanupFailureType = $script:CleanupFailureType
        cleanupFailureId = $script:CleanupFailureId
    }
    $exitCode = 2
}

$script:Stage = 'exit-sidechannel'
try {
    $childEnvelopeJson = ConvertTo-Json -InputObject $childEnvelope -Depth 8 -Compress
    [void](Write-ChildExitSidechannel $childEnvelopeJson $exitCode $childEnvelope $modulePath $previewScript $parentScript $childScript $EvidenceDirectory)
} catch {
    $sidechannelFailure = $_
    $sidechannelIdentity = Get-FailureIdentity $sidechannelFailure
    $script:FinalizationFailureType = [string]$sidechannelIdentity.type
    $script:FinalizationFailureId = [string]$sidechannelIdentity.id
    if ($null -eq $failure) { $failure = $sidechannelFailure }
    $exitCode = 2
    $script:Stage = 'exit-sidechannel-failure-cleanup'
    $script:SetupWatch.Stop()

    if ($null -ne $script:Context -and -not $script:CleanupAttempted) {
        $cleanupWatch = [Diagnostics.Stopwatch]::StartNew()
        $script:CleanupAttempted = $true
        try {
            $cleanup = Complete-VerifierRun $script:Context
            $script:CleanupSucceeded = ($null -ne $cleanup -and
                $null -ne $cleanup.PSObject.Properties['Success'] -and
                [bool]$cleanup.Success -and
                [string]$script:Context.CleanupState -ceq 'complete')
        } catch {
            $cleanupIdentity = Get-FailureIdentity $_
            $script:CleanupFailureType = [string]$cleanupIdentity.type
            $script:CleanupFailureId = [string]$cleanupIdentity.id
        } finally {
            $script:CleanupElapsedMs = [long]$cleanupWatch.ElapsedMilliseconds
            $cleanupWatch.Stop()
        }
    }

    if ($null -ne $script:Context) {
        $failureWitnessPath = Join-Path $script:Context.EvidenceDirectory 'witness-failure.json'
        try {
            if (Test-Path -LiteralPath $failureWitnessPath -PathType Leaf) {
                [void](Assert-VerifierNoReparseAncestors $script:Context.EvidenceDirectory)
                [void](Assert-VerifierPhysicalOwnedPath $script:Context.EvidenceNamespaceRoot $failureWitnessPath)
                $script:LatestWitness = $failureWitnessPath
            } else {
                $script:LatestWitness = Write-Witness 'failure' 'CHILD_EXIT_SIDECAR_WRITE_FAILED'
            }
        } catch {
            $witnessFailureIdentity = Get-FailureIdentity $_
            $script:FinalizationWitnessFailureType = [string]$witnessFailureIdentity.type
            $script:FinalizationWitnessFailureId = [string]$witnessFailureIdentity.id
        }
    }

    $failureIdentity = Get-FailureIdentity $failure
    $childEnvelope = [ordered]@{
        protocol = 'troubleshootjs-verifier-active-preview-recovery-child-result-v1'
        status = 'FAIL'
        case = $Case
        taskNonce = $TaskNonce
        runId = if ($null -ne $script:Context) { [string]$script:Context.RunId } else { '' }
        witnessFile = [string]$script:LatestWitness
        stage = [string]$script:Stage
        failureType = [string]$failureIdentity.type
        failureId = [string]$failureIdentity.id
        finalizationFailureType = [string]$script:FinalizationFailureType
        finalizationFailureId = [string]$script:FinalizationFailureId
        cleanupAttempted = [bool]$script:CleanupAttempted
        cleanupSucceeded = [bool]$script:CleanupSucceeded
        cleanupElapsedMs = $script:CleanupElapsedMs
        cleanupFailureType = $script:CleanupFailureType
        cleanupFailureId = $script:CleanupFailureId
    }

    $childEnvelopeJson = ConvertTo-Json -InputObject $childEnvelope -Depth 8 -Compress
    try {
        [void](Write-ChildExitSidechannel $childEnvelopeJson $exitCode $childEnvelope $modulePath $previewScript $parentScript $childScript $EvidenceDirectory)
    } catch {
        $retryFailureIdentity = Get-FailureIdentity $_
        $script:FinalizationRetryFailureType = [string]$retryFailureIdentity.type
        $script:FinalizationRetryFailureId = [string]$retryFailureIdentity.id
    }

    if ($null -ne $script:Context) {
        $boundWitnessPath = [string]$script:LatestWitness
        try {
            [void](Write-Witness 'sidechannel-failure' 'CHILD_EXIT_SIDECAR_WRITE_FAILED')
        } catch {
            if ([String]::IsNullOrWhiteSpace($script:FinalizationWitnessFailureType)) {
                $witnessFailureIdentity = Get-FailureIdentity $_
                $script:FinalizationWitnessFailureType = [string]$witnessFailureIdentity.type
                $script:FinalizationWitnessFailureId = [string]$witnessFailureIdentity.id
            }
        } finally {
            $script:LatestWitness = $boundWitnessPath
        }
    }
}
[Console]::Out.WriteLine($childEnvelopeJson)
exit $exitCode
