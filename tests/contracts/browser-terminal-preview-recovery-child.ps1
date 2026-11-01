
[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [ValidateSet('TerminalPreview', 'Ownerless')]
    [string]$Case,
    [string]$RepositoryRoot = '',
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
$script:SetupWatch = [Diagnostics.Stopwatch]::StartNew()
$script:PreviewElapsedMs = $null
$script:BrowserElapsedMs = $null
$script:PreviewTerminalizationElapsedMs = $null
$script:CleanupAttempted = $false
$script:CleanupSucceeded = $false
$script:CleanupElapsedMs = $null
$script:CleanupFailureType = ''
$script:CleanupFailureId = ''
$script:LatestWitness = ''

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
        claimFilePresent = [bool](Test-Path -LiteralPath ([string]$Lease.Path) -PathType Leaf)
    }
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
        protocol = 'troubleshootjs-terminal-preview-recovery-child-witness-v1'
        taskNonce = $TaskNonce
        case = $Case
        phase = $Phase
        outcome = $Outcome
        childProcess = [ordered]@{
            processId = $PID
            processStartTicks = [long]$script:ChildStartTicks
            executablePath = $script:ChildExecutablePath
        }
        context = $contextView
        preview = $preview
        browser = $browser
        timing = [ordered]@{
            setupElapsedMs = [long]$script:SetupWatch.ElapsedMilliseconds
            previewStartElapsedMs = $script:PreviewElapsedMs
            browserStartElapsedMs = $script:BrowserElapsedMs
            previewTerminalizationElapsedMs = $script:PreviewTerminalizationElapsedMs
            cleanupAttempted = [bool]$script:CleanupAttempted
            cleanupSucceeded = [bool]$script:CleanupSucceeded
            cleanupElapsedMs = $script:CleanupElapsedMs
            cleanupFailureType = $script:CleanupFailureType
            cleanupFailureId = $script:CleanupFailureId
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
        $RepositoryRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..\..'))
    } else {
        $RepositoryRoot = [IO.Path]::GetFullPath($RepositoryRoot)
    }
    $modulePath = Join-Path $RepositoryRoot 'scripts\VerifierIsolation.psm1'
    $previewScript = Join-Path $RepositoryRoot 'scripts\preview.ps1'
    $parentScript = Join-Path $PSScriptRoot 'browser-terminal-preview-recovery.ps1'
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

    $script:Stage = 'context-created'
    $script:Context = New-VerifierRunContext $RepositoryRoot $EvidenceDirectory
    # Persist the ownership root before starting the preview or browser.
    $initialWitness = Write-Witness 'context-created' 'SETUP_IN_PROGRESS'
    $script:Stage = 'preview-start'
    if ($Case -ceq 'TerminalPreview') {
        $previewWatch = [Diagnostics.Stopwatch]::StartNew()
        $baseUrl = Start-VerifierOwnedPreview $script:Context $previewScript 60 $false
        $script:PreviewElapsedMs = [long]$previewWatch.ElapsedMilliseconds
        if ($script:Context.Server.Owner -cne 'run' -or $script:Context.Server.State -cne 'run-owned-verified' -or $script:Context.Server.CleanupResult -cne 'pending' -or [String]::IsNullOrWhiteSpace([string]$baseUrl) -or [string]$script:Context.BaseUrl -cne [string]$baseUrl) {
            throw [InvalidOperationException]::new('OWNED_PREVIEW_START_NOT_EXACT')
        }
        [void](Write-Witness 'preview-ready' 'SETUP_IN_PROGRESS')
    } elseif ($script:Context.Server.Owner -cne 'none' -or -not [String]::IsNullOrEmpty([string]$script:Context.BaseUrl)) {
        throw [InvalidOperationException]::new('OWNERLESS_CONTEXT_NOT_EMPTY')
    }

    $script:Stage = 'browser-attach'
    $browserWatch = [Diagnostics.Stopwatch]::StartNew()
    $script:BrowserSession = New-VerifierBrowserSession $script:Context 'terminal-preview-recovery' 'about:blank' $browser 45 $false
    $script:BrowserElapsedMs = [long]$browserWatch.ElapsedMilliseconds
    $record = $script:BrowserSession.Record
    if ($null -eq $record -or $script:BrowserSession.Browser.GetType() -ne [Diagnostics.Process] -or [bool]$script:BrowserSession.Browser.HasExited -or [int]$script:BrowserSession.Browser.Id -ne [int]$record.ProcessId -or [long]$record.ProcessStartTicks -le 0 -or [string]$record.ExpectedUrl -cne 'about:blank' -or [string]$record.Status -cne 'attached' -or [string]$record.CleanupResult -cne 'pending' -or [string]$record.RecoveryReceipt.State -cne 'bound' -or [bool]$record.RecoveryReceipt.CloseAttempted -or $script:BrowserSession.Socket.State -ne [Net.WebSockets.WebSocketState]::Open) {
        throw [InvalidOperationException]::new('BROWSER_FACTORY_ATTACHMENT_PROOF_INCOMPLETE')
    }
    [void](Assert-VerifierDurableBrowserSession $record $script:Context 'terminal preview recovery child attached session')
    [void](Write-Witness 'browser-attached' 'SETUP_IN_PROGRESS')

    if ($Case -ceq 'TerminalPreview') {
        $script:Stage = 'preview-terminalization'
        $previewCloseWatch = [Diagnostics.Stopwatch]::StartNew()
        $moduleInfo = Get-Module -Name 'VerifierIsolation' | Where-Object {
            [IO.Path]::GetFullPath([string]$_.Path).Equals([IO.Path]::GetFullPath($modulePath), [StringComparison]::OrdinalIgnoreCase)
        } | Select-Object -First 1
        if ($null -eq $moduleInfo) { throw [InvalidOperationException]::new('VERIFIER_MODULE_SCOPE_UNAVAILABLE') }
        [void](& $moduleInfo { Complete-VerifierPreview $args[0] } $script:Context)
        $script:PreviewTerminalizationElapsedMs = [long]$previewCloseWatch.ElapsedMilliseconds
        $previewLease = $script:Context.Server.Lease
        if ($script:Context.Server.Owner -cne 'run' -or $script:Context.Server.CleanupResult -cne 'complete' -or -not [bool]$script:Context.Server.ProcessIdentityKnown -or -not [bool]$script:Context.Server.ProcessTerminationProven -or -not [bool]$script:Context.Server.ProcessAbsent -or -not [bool]$script:Context.Server.ListenerInspectionProven -or -not [bool]$script:Context.Server.ListenerAbsent -or $null -eq $previewLease -or $previewLease.Status -cne 'released' -or $previewLease.ClaimState -cne 'released' -or $previewLease.ReleaseState -cne 'complete' -or $previewLease.ReleaseJournalState -cne 'complete' -or -not [bool]$previewLease.MutexReleased -or -not [bool]$previewLease.ProcessTerminationProven -or -not [bool]$previewLease.ProcessAbsent -or -not [bool]$previewLease.ListenerInspectionSuccess -or -not [bool]$previewLease.ListenerInspectionKnown -or [bool]$previewLease.ListenerHasListeners -or -not [bool]$previewLease.ListenerAbsent -or [bool]$previewLease.ReleaseBlocked -or (Test-Path -LiteralPath $previewLease.Path)) {
            throw [InvalidOperationException]::new('OWNED_PREVIEW_TERMINAL_PROOF_INCOMPLETE')
        }
        [void](Write-VerifierManifest $script:Context)
    }

    $script:Stage = 'source-pins-final'
    Assert-ExactPin 'module' $modulePath $ExpectedModuleSha256
    Assert-ExactPin 'preview' $previewScript $ExpectedPreviewSha256
    Assert-ExactPin 'parent-test' $parentScript $ExpectedParentScriptSha256
    Assert-ExactPin 'child-test' $childScript $ExpectedChildScriptSha256
    $script:Stage = 'ready-witness'
    [void](Write-Witness 'ready' 'READY_FOR_PARENT_RECOVERY')
    $script:SetupWatch.Stop()
    $childEnvelope = [ordered]@{
        protocol = 'troubleshootjs-terminal-preview-recovery-child-result-v1'
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
        protocol = 'troubleshootjs-terminal-preview-recovery-child-result-v1'
        status = 'READY'
        case = $Case
        taskNonce = $TaskNonce
        runId = [string]$script:Context.RunId
        witnessFile = $script:LatestWitness
    }
} else {
    $failureIdentity = Get-FailureIdentity $failure
    $childEnvelope = [ordered]@{
        protocol = 'troubleshootjs-terminal-preview-recovery-child-result-v1'
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

[Console]::Out.WriteLine((ConvertTo-Json -InputObject $childEnvelope -Depth 8 -Compress))
exit $exitCode
