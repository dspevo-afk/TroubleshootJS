[CmdletBinding()]
param(
    [AllowEmptyString()]
    [string]$BaseUrl = '',
    [ValidateRange(10, 300)]
    [int]$TimeoutSeconds = 90,
    [AllowEmptyString()]
    [string]$BrowserPath = '',
    [int[]]$Seeds = @(0, 2, 3),
    [string]$Route,
    [switch]$NormalPlayer,
    [switch]$Diode,
    [switch]$DiodeShort,
    [switch]$DiodeNormalPlayer,
    [switch]$Parallel,
    [switch]$ParallelNormalPlayer,
    [switch]$LedParts,
    [switch]$LedNormalPlayer,
    [switch]$WrongRepair,
    [switch]$WrongRepairNormalPlayer,
    [switch]$StressDamage,
    [switch]$StressDamageNormalPlayer,
    [switch]$QuickPlay,
    [switch]$Layout,
    [switch]$Architecture,
    [switch]$Rc,
    [switch]$StoredEnergy,
    [switch]$RcNormalPlayer,
    [switch]$Npn,
    [switch]$NpnNatural,
    [switch]$Nmos,
    [switch]$NmosNatural,
    [switch]$Task39,
    [switch]$Task40,
    [switch]$Task41,
    [switch]$Task43,
    [switch]$Task43ForcedNegative,
    [switch]$Task43P,
    [switch]$Task43PForcedNegative,
    [switch]$Task43Integrated,
    [int]$PlayerSeed = 3,
    [string]$EvidenceDirectory,
    [switch]$PersistentPreviewEvidence,
    [switch]$GateBContractProbe,
    [switch]$GateBContractProbeFailure,
    [switch]$GateBExplicitExit2Probe,
    [switch]$GateBExplicitExit2TypedProbe,
    [switch]$GateBHangAfterContext,
    [string]$ParentLedgerPath = '',
    [string]$ParentNamespaceRoot = ''
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

# Module loading is verifier infrastructure. Keep this boundary independent of
# VerifierIsolation.psm1 so a missing/malformed module cannot fall through to
# PowerShell's generic exit-1 handling before the run owner exists.
function Get-VerifierEarlySetupMessage($ErrorRecord) {
    if ($null -eq $ErrorRecord) { return '' }
    if ($ErrorRecord.PSObject.Properties['Exception'] -and
            $null -ne $ErrorRecord.Exception) {
        return [string]$ErrorRecord.Exception.Message
    }
    return [string]$ErrorRecord
}

function Write-VerifierEarlySetupFailure([string]$Message) {
    $runId = [Guid]::NewGuid().ToString('N')
    $runRoot = ''
    try {
        $worktreeRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
        $tempRoot = [IO.Path]::GetFullPath([IO.Path]::GetTempPath())
        $runRoot = Join-Path $tempRoot ('TroubleshootJS\verify\module-import-failure\' + $runId)
        New-Item -ItemType Directory -Path $runRoot -Force -ErrorAction Stop | Out-Null
        $setupPath = Join-Path $runRoot 'setup-failure.json'
        $record = [ordered]@{
            protocol = 'troubleshootjs-verifier-early-setup-failure-v1'
            runId = $runId
            worktreeRoot = $worktreeRoot
            runRoot = $runRoot
            failedUtc = [DateTime]::UtcNow.ToString('o', [Globalization.CultureInfo]::InvariantCulture)
            error = $Message
        }
        [IO.File]::WriteAllText($setupPath, ($record | ConvertTo-Json -Depth 8),
            [Text.UTF8Encoding]::new($false))
        Write-Host ("FAIL verifier setup - verifier infrastructure exit 2: " +
            $Message + "; setup record retained at '" + $setupPath + "'.")
    } catch {
        Write-Host ("FAIL verifier setup - verifier infrastructure exit 2: " +
            $Message + "; setup record unavailable: " +
            (Get-VerifierEarlySetupMessage $_))
    }
}

$modulePath = Join-Path $PSScriptRoot 'VerifierIsolation.psm1'
try {
    Import-Module $modulePath -Force -ErrorAction Stop
} catch {
    Write-VerifierEarlySetupFailure ('VerifierIsolation.psm1 could not be imported: ' +
        (Get-VerifierEarlySetupMessage $_))
    exit 2
}
$script:VerifierContext = $null
$script:VerifierEvidenceDirectory = ''
$script:VerifierFailureExitCode = 0
$script:VerifierFailureMessage = ''
$script:VerifierFailureKind = ''
$script:VerifierCurrentRouteId = ''
$script:VerifierResolvedBrowserPath = ''
$script:VerifierParentLedgerWritten = $false
# Allow synchronous developer-proof gaps to exceed one CDP receive while the route deadline remains authoritative.
$CdpReceiveTimeoutMilliseconds = 60000
$CdpSendTimeoutMilliseconds = 5000
$script:CdpRouteDeadline = [DateTime]::MinValue

function Write-VerifierParentLedger([string]$State, [string]$ErrorMessage = '') {
    if ([String]::IsNullOrWhiteSpace($ParentLedgerPath) -or
            $null -eq $script:VerifierContext) { return }
    try {
        $ledgerPath = Get-VerifierFullPath $ParentLedgerPath
        $verifyRoot = Get-VerifierFullPath (Join-Path -Path ([IO.Path]::GetTempPath()) `
            -ChildPath 'TroubleshootJS\verify')
        if (-not (Test-VerifierChildPath $verifyRoot $ledgerPath)) {
            Throw-VerifierInfrastructure 'Integrated child ledger path escaped the verifier temp root.'
        }
        $ledgerParent = Get-VerifierFullPath (Split-Path -Parent $ledgerPath)
        if (-not (Test-Path -LiteralPath $ledgerParent -PathType Container)) {
            Throw-VerifierInfrastructure "Integrated child ledger parent does not exist: $ledgerParent"
        }
        $namespaceRoot = if ([String]::IsNullOrWhiteSpace($ParentNamespaceRoot)) {
            $ledgerParent
        } else { Get-VerifierFullPath $ParentNamespaceRoot }
        if (-not (Test-VerifierChildPath $verifyRoot $namespaceRoot) -or
                -not (Test-VerifierCanonicalWindowsPathValue $namespaceRoot $ledgerParent)) {
            Throw-VerifierInfrastructure 'Integrated child ledger namespace was foreign or did not own its ledger parent.'
        }
        [void](Assert-VerifierPhysicalOwnedPath $verifyRoot $ledgerParent)
        [void](Assert-VerifierPhysicalOwnedPath $ledgerParent $ledgerPath)
        $leases = @($script:VerifierContext.LeaseRecords | ForEach-Object {
            [ordered]@{
                runId = $script:VerifierContext.RunId
                repositoryIdentity = $script:VerifierContext.RepositoryIdentity
                worktreeRoot = $script:VerifierContext.WorktreeRoot
                leaseId = $_.LeaseId; path = $_.Path; kind = $_.Kind; port = $_.Port
                claimName = $_.ClaimName; status = $_.Status
                claimState = if ($_.PSObject.Properties['ClaimState']) { $_.ClaimState } else { 'active' }
                  releaseState = if ($_.PSObject.Properties['ReleaseState']) { $_.ReleaseState } else { 'active' }
                  releaseJournalState = if ($_.PSObject.Properties['ReleaseJournalState']) { $_.ReleaseJournalState } else { $null }
                  browserPath = if ($_.PSObject.Properties['BrowserPath']) { $_.BrowserPath } else { '' }
                  claimOwnerPid = if ($_.PSObject.Properties['ClaimOwnerPid']) { $_.ClaimOwnerPid } else { 0 }
                  claimOwnerStartTicks = if ($_.PSObject.Properties['ClaimOwnerStartTicks']) { $_.ClaimOwnerStartTicks } else { 0 }
                  mutexReleased = if ($_.PSObject.Properties['MutexReleased']) { [bool]$_.MutexReleased } else { $false }
                  boundProcessId = if ($_.PSObject.Properties['BoundProcessId']) { $_.BoundProcessId } else { 0 }
                 boundProcessStartTicks = if ($_.PSObject.Properties['BoundProcessStartTicks']) { $_.BoundProcessStartTicks } else { 0 }
                 listenerProcessId = if ($_.PSObject.Properties['ListenerProcessId']) { $_.ListenerProcessId } else { 0 }
                 listenerProcessStartTicks = if ($_.PSObject.Properties['ListenerProcessStartTicks']) { $_.ListenerProcessStartTicks } else { 0 }
                claim = [ordered]@{
                    protocol = 'troubleshootjs-verifier-port-claim-v1'
                    runId = $script:VerifierContext.RunId
                    repositoryIdentity = $script:VerifierContext.RepositoryIdentity
                    worktreeRoot = $script:VerifierContext.WorktreeRoot
                    leaseId = $_.LeaseId; path = $_.Path; kind = $_.Kind; port = $_.Port
                    mutexName = $_.ClaimName
                    ownerPid = if ($_.PSObject.Properties['ClaimOwnerPid']) { $_.ClaimOwnerPid } else { 0 }
                    ownerStartTicks = if ($_.PSObject.Properties['ClaimOwnerStartTicks']) { $_.ClaimOwnerStartTicks } else { 0 }
                }
                 profile = if ($_.PSObject.Properties['ProfilePath']) { $_.ProfilePath } else { '' }
                 processTerminationProven = if ($_.PSObject.Properties['ProcessTerminationProven']) {
                     [bool]$_.ProcessTerminationProven
                 } else { $false }
                 processAbsent = if ($_.PSObject.Properties['ProcessAbsent']) {
                     [bool]$_.ProcessAbsent
                 } else { $false }
                 listenerInspectionSuccess = if ($_.PSObject.Properties['ListenerInspectionSuccess']) { $_.ListenerInspectionSuccess } else { $false }
                listenerInspectionKnown = if ($_.PSObject.Properties['ListenerInspectionKnown']) { $_.ListenerInspectionKnown } else { $false }
                listenerHasListeners = if ($_.PSObject.Properties['ListenerHasListeners']) { $_.ListenerHasListeners } else { $null }
                listenerAbsent = if ($_.PSObject.Properties['ListenerAbsent']) { $_.ListenerAbsent } else { $null }
                processProofRequired = if ($_.PSObject.Properties['ProcessProofRequired']) { $_.ProcessProofRequired } else { $null }
            }
        })
        $profiles = @($script:VerifierContext.BrowserSessions | ForEach-Object {
            [ordered]@{
                owner = 'run'; runId = $_.RunId
                repositoryIdentity = $_.RepositoryIdentity
                worktreeRoot = $script:VerifierContext.WorktreeRoot
                profile = $_.Profile; cdpLeasePath = $_.Lease.Path
                 cdpPort = $_.CdpPort; processId = $_.ProcessId
                 processStartTicks = $_.ProcessStartTicks
                 processParentProcessId = if ($_.PSObject.Properties['ProcessParentProcessId']) { $_.ProcessParentProcessId } else { 0 }
                 processParentProcessStartTicks = if ($_.PSObject.Properties['ProcessParentProcessStartTicks']) { $_.ProcessParentProcessStartTicks } else { 0 }
                 processCommandLine = if ($_.PSObject.Properties['ProcessCommandLine']) { $_.ProcessCommandLine } else { '' }
                 browserPath = if ($_.PSObject.Properties['BrowserPath']) { $_.BrowserPath } else { '' }
                 status = $_.Status; cleanupResult = $_.CleanupResult
            }
        })
        $ledger = [ordered]@{
            protocol = 'troubleshootjs-integrated-child-ledger-v1'
            state = $State
            runId = $script:VerifierContext.RunId
            repositoryIdentity = $script:VerifierContext.RepositoryIdentity
            worktreeRoot = $script:VerifierContext.WorktreeRoot
            parentNamespaceRoot = $namespaceRoot
            runRoot = $script:VerifierContext.RunRoot
            manifestPath = $script:VerifierContext.ManifestPath
            evidenceDirectory = $script:VerifierContext.EvidenceDirectory
            evidence = @($script:VerifierContext.Artifacts)
            leases = $leases
            profiles = $profiles
            server = if ($script:VerifierContext.Server) {
                [ordered]@{
                    owner = $script:VerifierContext.Server.Owner
                     baseUrl = $script:VerifierContext.Server.BaseUrl
                     repositoryIdentity = $script:VerifierContext.RepositoryIdentity
                     worktreeRoot = $script:VerifierContext.WorktreeRoot
                     repositoryRoot = if ($script:VerifierContext.Server.PSObject.Properties['RepositoryRoot']) {
                         $script:VerifierContext.Server.RepositoryRoot
                     } else { $script:VerifierContext.WorktreeRoot }
                     webRoot = if ($script:VerifierContext.Server.PSObject.Properties['WebRoot']) {
                         $script:VerifierContext.Server.WebRoot
                     } else { '' }
                     identityProtocol = if ($script:VerifierContext.Server.PSObject.Properties['IdentityProtocol']) {
                         $script:VerifierContext.Server.IdentityProtocol
                     } else { '' }
                     identityVerified = if ($script:VerifierContext.Server.PSObject.Properties['IdentityVerified']) {
                         [bool]$script:VerifierContext.Server.IdentityVerified
                     } else { $false }
                     callerOwned = if ($script:VerifierContext.Server.PSObject.Properties['CallerOwned']) {
                         [bool]$script:VerifierContext.Server.CallerOwned
                     } else { $false }
                     processId = $script:VerifierContext.Server.ProcessId
                    processStartTicks = $script:VerifierContext.Server.ProcessStartTicks
                    processParentProcessId = if ($script:VerifierContext.Server.PSObject.Properties['ProcessParentProcessId']) {
                        $script:VerifierContext.Server.ProcessParentProcessId
                    } else { 0 }
                    processParentProcessStartTicks = if ($script:VerifierContext.Server.PSObject.Properties['ProcessParentProcessStartTicks']) {
                        $script:VerifierContext.Server.ProcessParentProcessStartTicks
                    } else { 0 }
                     processCommandLine = if ($script:VerifierContext.Server.PSObject.Properties['ProcessCommandLine']) {
                         $script:VerifierContext.Server.ProcessCommandLine
                     } else { '' }
                     leaseId = if ($script:VerifierContext.Server.PSObject.Properties['Lease'] -and
                             $null -ne $script:VerifierContext.Server.Lease) {
                         $script:VerifierContext.Server.Lease.LeaseId
                     } else { '' }
                     leaseKind = if ($script:VerifierContext.Server.PSObject.Properties['Lease'] -and
                             $null -ne $script:VerifierContext.Server.Lease) {
                         $script:VerifierContext.Server.Lease.Kind
                     } else { '' }
                     leaseClaimName = if ($script:VerifierContext.Server.PSObject.Properties['Lease'] -and
                             $null -ne $script:VerifierContext.Server.Lease) {
                         $script:VerifierContext.Server.Lease.ClaimName
                     } else { '' }
                     leaseClaimState = if ($script:VerifierContext.Server.PSObject.Properties['Lease'] -and
                             $null -ne $script:VerifierContext.Server.Lease) {
                         $script:VerifierContext.Server.Lease.ClaimState
                     } else { '' }
                     leaseReleaseState = if ($script:VerifierContext.Server.PSObject.Properties['Lease'] -and
                             $null -ne $script:VerifierContext.Server.Lease) {
                         $script:VerifierContext.Server.Lease.ReleaseState
                     } else { '' }
                     leaseReleaseJournalState = if ($script:VerifierContext.Server.PSObject.Properties['Lease'] -and
                             $null -ne $script:VerifierContext.Server.Lease -and
                             $script:VerifierContext.Server.Lease.PSObject.Properties['ReleaseJournalState']) {
                         $script:VerifierContext.Server.Lease.ReleaseJournalState
                     } else { '' }
                     leaseOwnerPid = if ($script:VerifierContext.Server.PSObject.Properties['Lease'] -and
                             $null -ne $script:VerifierContext.Server.Lease) {
                         $script:VerifierContext.Server.Lease.ClaimOwnerPid
                     } else { 0 }
                     leaseOwnerStartTicks = if ($script:VerifierContext.Server.PSObject.Properties['Lease'] -and
                             $null -ne $script:VerifierContext.Server.Lease) {
                         $script:VerifierContext.Server.Lease.ClaimOwnerStartTicks
                     } else { 0 }
                     port = $script:VerifierContext.Server.Port
                    script = $script:VerifierContext.Server.Script
                    runId = $script:VerifierContext.Server.RunId
                    nonce = $script:VerifierContext.Server.Nonce
                    stdoutLog = $script:VerifierContext.Server.StdoutLog
                    stderrLog = $script:VerifierContext.Server.StderrLog
                    leasePath = if ($script:VerifierContext.Server.PSObject.Properties['Lease'] -and
                            $null -ne $script:VerifierContext.Server.Lease) {
                        $script:VerifierContext.Server.Lease.Path
                    } else { '' }
                    leaseListenerAbsent = if ($script:VerifierContext.Server.PSObject.Properties['Lease'] -and
                            $null -ne $script:VerifierContext.Server.Lease -and
                            $script:VerifierContext.Server.Lease.PSObject.Properties['ListenerAbsent']) {
                        $script:VerifierContext.Server.Lease.ListenerAbsent
                    } else { $null }
                    leaseProcessProofRequired = if ($script:VerifierContext.Server.PSObject.Properties['Lease'] -and
                            $null -ne $script:VerifierContext.Server.Lease -and
                            $script:VerifierContext.Server.Lease.PSObject.Properties['ProcessProofRequired']) {
                        $script:VerifierContext.Server.Lease.ProcessProofRequired
                    } else { $null }
                    state = $script:VerifierContext.Server.State
                    cleanupResult = $script:VerifierContext.Server.CleanupResult
                    processIdentityKnown = if ($script:VerifierContext.Server.PSObject.Properties['ProcessIdentityKnown']) {
                        [bool]$script:VerifierContext.Server.ProcessIdentityKnown
                    } else { $false }
                    ownershipUncertain = if ($script:VerifierContext.Server.PSObject.Properties['OwnershipUncertain']) {
                        [bool]$script:VerifierContext.Server.OwnershipUncertain
                    } else { $false }
                    processTerminationProven = $script:VerifierContext.Server.ProcessTerminationProven
                    processAbsent = $script:VerifierContext.Server.ProcessAbsent
                    listenerInspectionProven = $script:VerifierContext.Server.ListenerInspectionProven
                    listenerAbsent = $script:VerifierContext.Server.ListenerAbsent
                }
            } else { $null }
            cleanupState = $script:VerifierContext.CleanupState
            cleanupErrors = @($script:VerifierContext.CleanupErrors)
            error = $ErrorMessage
            updatedUtc = [DateTime]::UtcNow.ToString('o', [Globalization.CultureInfo]::InvariantCulture)
        }
        $temporary = $ledgerPath + '.' + [Guid]::NewGuid().ToString('N') + '.tmp'
        try {
            [void](Assert-VerifierPhysicalOwnedPath $ledgerParent $temporary)
            [void](Assert-VerifierPhysicalOwnedPath $ledgerParent $ledgerPath)
            [IO.File]::WriteAllText($temporary, ($ledger | ConvertTo-Json -Depth 12),
                [Text.UTF8Encoding]::new($false))
            [void](Assert-VerifierPhysicalOwnedPath $ledgerParent $temporary)
            [void](Assert-VerifierPhysicalOwnedPath $ledgerParent $ledgerPath)
            Move-Item -LiteralPath $temporary -Destination $ledgerPath -Force -ErrorAction Stop
        } catch {
            try {
                if (Test-VerifierPhysicalChildPath $ledgerParent $temporary) {
                    Remove-VerifierOwnedTree $ledgerParent $temporary
                }
            } catch { }
            throw
        }
        $script:VerifierParentLedgerWritten = $true
    } catch {
        if (Test-VerifierInfrastructureError $_) { throw }
        Throw-VerifierInfrastructure ('Could not persist integrated child ledger: ' +
            (Get-VerifierErrorMessage $_))
    }
}

function resolveCdpRouteDeadline([DateTime]$deadline) {
    $activeDeadline = $script:CdpRouteDeadline
    if ($activeDeadline -eq [DateTime]::MinValue) { return $deadline }
    if ($deadline -eq [DateTime]::MinValue -or $activeDeadline -lt $deadline) {
        return $activeDeadline
    }
    return $deadline
}

function sendCdp($socket, [int]$id, [string]$method, $parameters) {
    $message = @{ id = $id; method = $method; params = $parameters } | ConvertTo-Json -Compress -Depth 8
    $bytes = [Text.Encoding]::UTF8.GetBytes($message)
    $sendTimeout = New-Object Threading.CancellationTokenSource $CdpSendTimeoutMilliseconds
    try {
        $socket.SendAsync((New-Object ArraySegment[byte] -ArgumentList (,$bytes)),
            [Net.WebSockets.WebSocketMessageType]::Text, $true,
            $sendTimeout.Token).GetAwaiter().GetResult()
    } catch {
        Throw-VerifierInfrastructure "CDP send failed for $method`: $(Get-VerifierErrorMessage $_)"
    } finally {
        $sendTimeout.Dispose()
    }
}

function Set-VerifierFailure($ErrorRecord, [string]$Scope, [switch]$Quiet) {
    $message = Get-VerifierErrorMessage $ErrorRecord
    $isInfrastructure = Test-VerifierInfrastructureError $ErrorRecord
    $newExitCode = Merge-VerifierFailureExitCode $script:VerifierFailureExitCode $isInfrastructure
    if ($newExitCode -eq 2 -and $script:VerifierFailureExitCode -ne 2) {
        $script:VerifierFailureKind = 'infrastructure'
        $script:VerifierFailureMessage = $message
    } elseif ($script:VerifierFailureExitCode -eq 0) {
        $script:VerifierFailureKind = if ($isInfrastructure) { 'infrastructure' } else { 'application' }
        $script:VerifierFailureMessage = $message
    }
    $script:VerifierFailureExitCode = $newExitCode
    $label = if ($isInfrastructure) { 'verifier infrastructure' } else { 'application' }
    if (-not $Quiet) { Write-Host "FAIL $Scope - $label`: $message" }
}

function Get-VerifierRouteFailureExitCode() {
    if ($script:VerifierFailureExitCode -eq 2) { return 2 }
    return 1
}

function Throw-VerifierExit([int]$ExitCode, [string]$Message) {
    $exception = [System.InvalidOperationException]::new($Message)
    $exception.Data['VerifierExitCode'] = $ExitCode
    if ($ExitCode -eq 2) {
        $exception.Data['VerifierFailureKind'] = 'infrastructure'
    }
    throw $exception
}

function Throw-VerifierRouteDeadline([string]$Description) {
    Throw-VerifierInfrastructure ("Verifier route deadline expired while waiting for $Description.")
}

function Get-VerifierRequestedExitCode($ErrorRecord) {
    $exception = if ($ErrorRecord -and $ErrorRecord.PSObject.Properties['Exception'] -and
            $null -ne $ErrorRecord.Exception) { $ErrorRecord.Exception } else { $ErrorRecord }
    $explicitExitCode = $null
    if ($exception -and $exception.Data -and
            $exception.Data.Contains('VerifierExitCode')) {
        $parsedExitCode = 0
        if ([int]::TryParse([string]$exception.Data['VerifierExitCode'],
                [Globalization.NumberStyles]::Integer,
                [Globalization.CultureInfo]::InvariantCulture,
                [ref]$parsedExitCode)) {
            $explicitExitCode = $parsedExitCode
        }
    }
    if ($explicitExitCode -eq 2) { return 2 }
    # A run-level infrastructure result is monotonic.  It dominates a later
    # route/child application exception carrying an explicit exit 1.
    if ($script:VerifierFailureExitCode -eq 2) { return 2 }
    if ($null -ne $explicitExitCode) {
        return [int]$explicitExitCode
    }
    if (Test-VerifierInfrastructureError $ErrorRecord) { return 2 }
    return 1
}

function cleanupBrowser($browser, $socket, [string]$profile) {
    if ($null -eq $script:VerifierContext -or [String]::IsNullOrWhiteSpace($profile)) { return }
    $sessionRecord = @($script:VerifierContext.BrowserSessions |
        Where-Object { Test-VerifierCanonicalWindowsPathValue ([string]$_.Profile) $profile } |
        Select-Object -First 1)
    if ($sessionRecord.Count -eq 0) {
        Throw-VerifierInfrastructure "Browser profile '$profile' is not registered to this verifier run."
    }
    Complete-VerifierBrowserSession $script:VerifierContext $sessionRecord[0]
}

function startVerifierBrowser([string]$routeName, [string]$url) {
    if ($null -eq $script:VerifierContext) {
        Throw-VerifierInfrastructure 'Verifier run context was not initialized before browser launch.'
    }
    $session = New-VerifierBrowserSession $script:VerifierContext $routeName $url `
        $script:VerifierResolvedBrowserPath $TimeoutSeconds
    $script:VerifierCurrentRouteId = $session.RouteId
    return $session
}

function getVerifierEvidencePath([string]$fileName) {
    if ($null -eq $script:VerifierContext) {
        Throw-VerifierInfrastructure 'Verifier run context was not initialized before evidence capture.'
    }
    if ([String]::IsNullOrWhiteSpace($fileName) -or
            $fileName.IndexOf([IO.Path]::DirectorySeparatorChar) -ge 0 -or
            $fileName.IndexOf([IO.Path]::AltDirectorySeparatorChar) -ge 0) {
        Throw-VerifierInfrastructure "Evidence filename was not a simple collision-safe name: $fileName"
    }
    $path = Join-Path $script:VerifierEvidenceDirectory $fileName
    if (Test-Path -LiteralPath $path) {
        Throw-VerifierInfrastructure "Refusing to overwrite existing run evidence: $path"
    }
    return $path
}

function Get-Task43PRepositoryState() {
    $repositoryRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
    $safeRepositoryRoot = $repositoryRoot.Replace('\', '/')
    $headArgs = @('-c', "safe.directory=$safeRepositoryRoot", '-C', $repositoryRoot,
        'rev-parse', 'HEAD')
    $head = (& git @headArgs 2>$null | Out-String).Trim()
    if ($LASTEXITCODE -ne 0 -or $head -notmatch '^[0-9a-fA-F]{40}$') {
        Throw-VerifierInfrastructure ("Task43P could not prove repository HEAD: " + $head)
    }
    $statusArgs = @('-c', "safe.directory=$safeRepositoryRoot", '-C', $repositoryRoot,
        'status', '--porcelain=v1', '--untracked-files=all')
    $status = (& git @statusArgs 2>$null | Out-String).Trim()
    if ($LASTEXITCODE -ne 0) {
        Throw-VerifierInfrastructure 'Task43P could not prove repository worktree status.'
    }
    # Hash source and verifier files directly.  This includes untracked Task43P
    # files and avoids treating Git's platform-specific line-ending warnings as
    # evidence or as a route result.
    $fileRecords = New-Object Collections.Generic.List[string]
    foreach ($relativeRoot in @('src', 'scripts')) {
        $root = Join-Path $repositoryRoot $relativeRoot
        if (-not (Test-Path -LiteralPath $root -PathType Container)) {
            Throw-VerifierInfrastructure "Task43P could not inspect source root: $root"
        }
        foreach ($file in @(Get-ChildItem -LiteralPath $root -Recurse -File -ErrorAction Stop |
                Sort-Object FullName)) {
            $relativePath = $file.FullName.Substring($repositoryRoot.Length).TrimStart('\', '/')
            $fileHash = (Get-FileHash -LiteralPath $file.FullName -Algorithm SHA256).Hash.ToLowerInvariant()
            [void]$fileRecords.Add($relativePath.Replace('\', '/') + '=' + $fileHash)
        }
    }
    $hasher = [Security.Cryptography.SHA256]::Create()
    try {
        $bytes = [Text.UTF8Encoding]::new($false).GetBytes(($fileRecords -join "`n"))
        $digest = [BitConverter]::ToString($hasher.ComputeHash($bytes)).Replace('-', '').ToLowerInvariant()
    } finally {
        $hasher.Dispose()
    }
    return [ordered]@{
        headSha = $head.ToLowerInvariant()
        worktreeDigest = $digest
        dirty = -not [String]::IsNullOrWhiteSpace($status)
        hashedFileCount = $fileRecords.Count
    }
}

function Capture-Task43PEvidence($socket, [ref]$nextId, [DateTime]$deadline,
        [ref]$failures, [string]$routeName, [string]$expected, [string]$observed,
        $repositoryBefore) {
    $payload = [string](evaluateCdp $socket ([ref]$nextId) `
        "document.documentElement.getAttribute('data-tsj-task43p-evidence') || ''" `
        ([ref]$failures) $deadline)
    if ([String]::IsNullOrWhiteSpace($payload)) {
        Throw-VerifierInfrastructure "Task43P route '$routeName' did not publish structured evidence."
    }
    try {
        $parsed = $payload | ConvertFrom-Json -Depth 30 -ErrorAction Stop
    } catch {
        Throw-VerifierInfrastructure ("Task43P route '$routeName' published invalid JSON evidence: " +
            (Get-VerifierErrorMessage $_))
    }
    $repositoryAfter = Get-Task43PRepositoryState
    if ($repositoryBefore.headSha -ne $repositoryAfter.headSha -or
            $repositoryBefore.worktreeDigest -ne $repositoryAfter.worktreeDigest) {
        Throw-VerifierInfrastructure ("Task43P route '$routeName' changed repository state while " +
            'running developer evidence: before=' + $repositoryBefore.worktreeDigest +
            ' after=' + $repositoryAfter.worktreeDigest)
    }
    $safeRouteName = $routeName -replace '[^A-Za-z0-9._-]', '-'
    $record = [ordered]@{
        protocol = 'troubleshootjs-task43p-evidence-capture-v1'
        route = $routeName
        expected = $expected
        observed = $observed
        capturedUtc = [DateTime]::UtcNow.ToString('o', [Globalization.CultureInfo]::InvariantCulture)
        runId = $script:VerifierContext.RunId
        routeId = $script:VerifierCurrentRouteId
        baselineSha = '3bfaab093f85247fc20aec068824c83dc3d214c8'
        currentHeadSha = $repositoryAfter.headSha
        worktreeDigestBefore = $repositoryBefore.worktreeDigest
        worktreeDigestAfter = $repositoryAfter.worktreeDigest
        worktreeDirty = $repositoryAfter.dirty
        hashedFileCount = $repositoryAfter.hashedFileCount
        evidence = $parsed
    }
    $path = getVerifierEvidencePath ('task43p-' + $safeRouteName + '.json')
    [IO.File]::WriteAllText($path, ($record | ConvertTo-Json -Depth 30),
        [Text.UTF8Encoding]::new($false))
    Register-VerifierEvidenceArtifact $script:VerifierContext $path
    Write-Host ("TASK43P EVIDENCE $routeName path=$path head=$($repositoryAfter.headSha) " +
        "worktreeDigest=$($repositoryAfter.worktreeDigest)")
}

function receiveCdp($socket, [int]$wantedId, [ref]$failures,
        [DateTime]$deadline = [DateTime]::MinValue) {
    $deadline = resolveCdpRouteDeadline $deadline
    while ($true) {
        $stream = New-Object IO.MemoryStream
        do {
            $buffer = New-Object byte[] 65536
            $receiveTimeoutMilliseconds = $CdpReceiveTimeoutMilliseconds
            if ($deadline -ne [DateTime]::MinValue) {
                $remainingMilliseconds = ($deadline - [DateTime]::UtcNow).TotalMilliseconds
                $receiveTimeoutMilliseconds = [int][Math]::Max(0, [Math]::Min(
                    $CdpReceiveTimeoutMilliseconds, [Math]::Floor($remainingMilliseconds)))
            }
            $receiveTimeout = New-Object Threading.CancellationTokenSource $receiveTimeoutMilliseconds
            try {
                $result = $socket.ReceiveAsync((New-Object ArraySegment[byte] -ArgumentList (,$buffer)),
                    $receiveTimeout.Token).GetAwaiter().GetResult()
            } catch {
                Throw-VerifierInfrastructure "CDP receive failed: $(Get-VerifierErrorMessage $_)"
            } finally {
                $receiveTimeout.Dispose()
            }
            if ($null -eq $result -or $result.Count -lt 0) {
                Throw-VerifierInfrastructure 'CDP receive returned an invalid transport result.'
            }
            $stream.Write($buffer, 0, $result.Count)
        } while (-not $result.EndOfMessage)
        try {
            $message = [Text.Encoding]::UTF8.GetString($stream.ToArray()) | ConvertFrom-Json
        } catch {
            Throw-VerifierInfrastructure "CDP returned invalid JSON: $(Get-VerifierErrorMessage $_)"
        }
        try {
            $method = if ($message.PSObject.Properties['method']) { $message.method } else { $null }
            if ($method -eq 'Runtime.exceptionThrown') {
                $details = $message.params.exceptionDetails
                $description = if ($details.exception -and $details.exception.description) {
                    $details.exception.description
                } else { $details.text }
                $stack = ''
                if ($details.stackTrace -and $details.stackTrace.callFrames) {
                    $stack = ' ' + (($details.stackTrace.callFrames | Select-Object -First 8 |
                        ForEach-Object { "$($_.functionName)@$($_.url):$($_.lineNumber)" }) -join ' <- ')
                }
                $failures.Value += 'JavaScript exception: ' + $description + $stack
            }
            if ($method -eq 'Runtime.consoleAPICalled') {
                $text = ($message.params.args | ForEach-Object {
                    if ($_.PSObject.Properties['value']) { $_.value }
                    elseif ($_.PSObject.Properties['description']) { $_.description }
                    else { $_.type }
                }) -join ' '
                if ($text -match '(?i)verification failed|generated board verification failed|Unable to generate|pcb_generator_failure|parallel_generator_failure|rc_generator_failure|uncaught|exception') {
                    $failures.Value += 'Console failure: ' + $text
                }
            }
            if ($message.PSObject.Properties['id'] -and $message.id -eq $wantedId) { return $message }
        } catch {
            if (Test-VerifierInfrastructureError $_) { throw }
            Throw-VerifierInfrastructure "Could not process CDP event/protocol response: $(Get-VerifierErrorMessage $_)"
        }
    }
}

function invokeCdp($socket, [ref]$nextId, [string]$method, $parameters, [ref]$failures,
        [DateTime]$deadline = [DateTime]::MinValue) {
    $id = $nextId.Value
    $nextId.Value++
    [void](sendCdp $socket $id $method $parameters)
    $response = receiveCdp $socket $id $failures (resolveCdpRouteDeadline $deadline)
    if ($null -eq $response -or -not $response.PSObject.Properties['id'] -or
            [int]$response.id -ne $id) {
        Throw-VerifierInfrastructure "CDP returned no matching response for $method."
    }
    if ($response.PSObject.Properties['error']) {
        $detail = $response.error | ConvertTo-Json -Compress -Depth 8
        Throw-VerifierInfrastructure "CDP protocol error for $method`: $detail"
    }
    if ($response.PSObject.Properties['result'] -and
            $response.result -and $response.result.PSObject.Properties['errorText']) {
        Throw-VerifierInfrastructure "CDP navigation/protocol error for $method`: $($response.result.errorText)"
    }
    return $response
}

function navigateAndWaitForDocument($socket, [ref]$nextId, [string]$url,
        [DateTime]$deadline, [ref]$failures) {
    $marker = [Guid]::NewGuid().ToString('N')
    $query = @('tsjVerifierNavigation=' + $marker)
    if ($script:VerifierContext -and $script:VerifierCurrentRouteId) {
        $query += 'tsjVerifierRun=' + $script:VerifierContext.RunId
        $query += 'tsjVerifierRoute=' + $script:VerifierCurrentRouteId
    }
    $separator = if ($url.IndexOf('?') -ge 0) { '&' } else { '?' }
    $navigationUrl = $url + $separator + ($query -join '&')
    [void](invokeCdp $socket $nextId 'Page.navigate' @{ url = $navigationUrl } $failures $deadline)
    $escapedMarker = $marker.Replace("'", "\\'")
    waitForCdp $socket $nextId "location.href.includes('$escapedMarker')&&document.readyState==='complete'" $deadline $failures 'synchronized page navigation'
    if ($script:VerifierContext -and $script:VerifierCurrentRouteId) {
        $runMarker = $script:VerifierContext.RunId.Replace("'", "\\'")
        $routeMarker = $script:VerifierCurrentRouteId.Replace("'", "\\'")
        waitForCdp $socket $nextId "location.href.includes('tsjVerifierRun=$runMarker')&&location.href.includes('tsjVerifierRoute=$routeMarker')" $deadline $failures 'run-owned browser navigation marker'
    }
    return evaluateCdp $socket $nextId 'performance.timeOrigin' $failures $deadline
}

function isExpectedTask43ForcedFailureDiagnostic([string]$failure,
        [string]$expectedFailure) {
    if ($expectedFailure -ne 'FAIL:task43-forced-negative-canary' -and
            $expectedFailure -ne 'FAIL:task43p-forced-negative-canary') { return $false }
    $marker = [regex]::Escape($expectedFailure.Substring(5))
    return $failure -match '^Console failure: exception in runCircuit ' +
        'java\.lang\.IllegalStateException: Generated board verification failed for ' +
        '[^,]+, seed [^:]+: ' + $marker + '$'
}

function verifyRoute([string]$name, [string]$url, [string]$expected,
        [string]$expectedComplaint = '', [string]$expectedFailure = '') {
    $profile = $null
    $browser = $null
    $socket = $null
    $session = $null
    $success = $false
    $expectedFailureObserved = $false
    $task43pRepositoryBefore = $null
    if ($name -like 'task43p *') {
        $task43pRepositoryBefore = Get-Task43PRepositoryState
    }
    if ($expectedFailure) {
        $script:task43ExpectedFailureObserved = $false
        $script:task43ExpectedFailureRoutePassed = $false
    }
    try {
        $session = startVerifierBrowser $name $url
        $profile = $session.Profile
        $browser = $session.Browser
        $socket = $session.Socket
        $deadline = $session.Deadline
        $nextId = 1
        $failures = @()
        $script:CdpRouteDeadline = $deadline
        [void](invokeCdp $socket ([ref]$nextId) 'Runtime.enable' @{} ([ref]$failures) $deadline)
        [void](invokeCdp $socket ([ref]$nextId) 'Page.enable' @{} ([ref]$failures) $deadline)
        [void](navigateAndWaitForDocument $socket ([ref]$nextId) $url $deadline ([ref]$failures))
        if ($expectedComplaint) {
            $escapedComplaint = $expectedComplaint.Replace("'", "\\'")
            $ticketExpression = "(()=>{const title=[...document.querySelectorAll('.tsj-component-title')].find(e=>e.textContent.trim()==='Service Ticket');if(!title||!title.parentElement)return false;const lines=title.parentElement.innerText.split(/\r?\n+/).map(x=>x.trim()).filter(Boolean);return lines.length===2&&lines[0]==='Service Ticket'&&lines[1]==='$escapedComplaint';})()"
            waitForCdp $socket ([ref]$nextId) $ticketExpression $deadline ([ref]$failures) 'solver-validated Service Ticket complaint'
        }
        do {
            $response = invokeCdp $socket ([ref]$nextId) 'Runtime.evaluate' @{
                expression = "document.documentElement.getAttribute('data-tsj-verification') || ''"; returnByValue = $true
            } ([ref]$failures) $deadline
            $verificationResult = [string]$response.result.result.value
            if ($verificationResult.StartsWith('FAIL:')) {
                if ($expectedFailure -and $verificationResult -eq $expectedFailure) {
                    $expectedFailureObserved = $true
                    $script:task43ExpectedFailureObserved = $true
                    break
                }
                throw "unexpected application failure: $verificationResult"
            }
            if ($expectedFailure -and $verificationResult -eq $expected) {
                throw "unexpected application success: $verificationResult"
            }
            if ($verificationResult -eq $expected) { break }
            Start-Sleep -Milliseconds 250
        } while ([DateTime]::UtcNow -lt $deadline)
        if ($expectedFailure -and -not $expectedFailureObserved) {
            $diagnostic = evaluateCdp $socket ([ref]$nextId) "({status:document.documentElement.getAttribute('data-tsj-verification')||'',body:(document.body&&document.body.innerText||'').slice(-900)})" ([ref]$failures) $deadline
            Write-Host ("VERIFIER DIAGNOSTIC status=$($diagnostic.status) body=$($diagnostic.body.Replace("`n", ' | '))")
            if ($failures.Count -gt 0) { Write-Host ("VERIFIER CDP FAILURES: " + ($failures -join '; ')) }
            Throw-VerifierRouteDeadline "expected application failure '$expectedFailure'"
        }
        if ($expectedFailureObserved) {
            $expectedFailureDiagnostics = @($failures | Where-Object {
                isExpectedTask43ForcedFailureDiagnostic ([string]$_) $expectedFailure
            })
            if ($expectedFailureDiagnostics.Count -eq 0) {
                throw "expected application failure '$expectedFailure' had no anchored Java console diagnostic"
            }
        }
        if (-not $expectedFailure -and $verificationResult -ne $expected) {
            $diagnostic = evaluateCdp $socket ([ref]$nextId) "({status:document.documentElement.getAttribute('data-tsj-verification')||'',body:(document.body&&document.body.innerText||'').slice(-900)})" ([ref]$failures) $deadline
            Write-Host ("VERIFIER DIAGNOSTIC status=$($diagnostic.status) body=$($diagnostic.body.Replace("`n", ' | '))")
            if ($failures.Count -gt 0) { Write-Host ("VERIFIER CDP FAILURES: " + ($failures -join '; ')) }
            Throw-VerifierRouteDeadline "verification result '$expected'"
        }
        if ($name -eq 'quick-play selector/session') {
            $quickPlayReport = evaluateCdp $socket ([ref]$nextId) "document.documentElement.getAttribute('data-tsj-quick-play-report') || ''" ([ref]$failures) $deadline
            if ($quickPlayReport -ne 'unrepaired-finish-blocked;correct-finish-passed;fresh-session-isolated') {
                throw "Quick Play focused report was incomplete: $quickPlayReport"
            }
        }
        if ($name -eq 'seed=3 stress-damage') {
            $stressReport = evaluateCdp $socket ([ref]$nextId) "document.documentElement.getAttribute('data-tsj-stress-report') || ''" ([ref]$failures) $deadline
            if (-not $stressReport) { throw 'stress verifier did not publish its developer electrical report' }
            Write-Host "TASK34 ELECTRICAL REPORT: $stressReport"
        }
        if ($name -like 'npn-*') {
            $npnElectricalReport = evaluateCdp $socket ([ref]$nextId) "document.documentElement.getAttribute('data-tsj-npn-electrical-report') || ''" ([ref]$failures) $deadline
            if (-not $npnElectricalReport) { throw 'NPN verifier did not publish its developer electrical report' }
            Write-Host "NPN ELECTRICAL REPORT: $npnElectricalReport"
        }
        if ($name -like 'task43p *') {
            Capture-Task43PEvidence $socket ([ref]$nextId) $deadline ([ref]$failures) `
                $name $expected $verificationResult $task43pRepositoryBefore
        }
        Start-Sleep -Milliseconds 100
        [void](evaluateCdp $socket ([ref]$nextId) "document.readyState" ([ref]$failures) $deadline)
        if ($script:VerifierEvidenceDirectory -and $name -match '^seed=(0|2) parallel$') {
            captureBrowserScreenshot $socket ([ref]$nextId) (getVerifierEvidencePath ("parallel-seed-" + $Matches[1] + ".png")) ([ref]$failures)
        }
        if ($failures.Count -gt 0) {
            $unexpectedFailures = @($failures | Where-Object {
                -not $expectedFailureObserved -or
                    -not (isExpectedTask43ForcedFailureDiagnostic $_ $expectedFailure)
            })
            if ($unexpectedFailures.Count -gt 0) { throw ($unexpectedFailures -join '; ') }
        }
        cleanupBrowser $browser $socket $profile
        $socket = $null
        $browser = $null
        if ($expectedFailureObserved) {
            Write-Host "EXPECTED FAILURE $name - $expectedFailure (anchored Java console diagnostic observed)"
        } elseif ($verificationResult.StartsWith('UNPROVEN:')) {
            Write-Host "UNPROVEN $name - $verificationResult (typed evidence recorded)"
        } else {
            Write-Host "PASS $name"
        }
        $success = $true
        if ($expectedFailureObserved) { $script:task43ExpectedFailureRoutePassed = $true }
    } catch {
        Set-VerifierFailure $_ $name
    } finally {
        try {
            cleanupBrowser $browser $socket $profile
        } catch {
            Set-VerifierFailure $_ ($name + ' cleanup')
            $success = $false
        }
        $script:CdpRouteDeadline = [DateTime]::MinValue
    }
    return $success
}

function evaluateCdp($socket, [ref]$nextId, [string]$expression, [ref]$failures,
        [DateTime]$deadline = [DateTime]::MinValue) {
    $deadline = resolveCdpRouteDeadline $deadline
    $response = invokeCdp $socket $nextId 'Runtime.evaluate' @{
        expression = $expression; returnByValue = $true
    } $failures $deadline
    if ($null -eq $response -or -not $response.PSObject.Properties['result'] -or
            $null -eq $response.result -or
            -not $response.result.PSObject.Properties['result']) {
        Throw-VerifierInfrastructure "CDP Runtime.evaluate returned a malformed result."
    }
    if ($response.result.PSObject.Properties['exceptionDetails']) {
        Throw-VerifierInfrastructure ("CDP Runtime.evaluate reported a page/protocol exception: " +
            [string]$response.result.exceptionDetails.text)
    }
    if ($response.result.result.PSObject.Properties['value']) {
        return $response.result.result.value
    }
    return $null
}

function waitForCdp($socket, [ref]$nextId, [string]$expression, [DateTime]$deadline,
        [ref]$failures, [string]$description) {
    $deadline = resolveCdpRouteDeadline $deadline
    do {
        if (evaluateCdp $socket $nextId $expression $failures $deadline) { return }
        Start-Sleep -Milliseconds 200
    } while ([DateTime]::UtcNow -lt $deadline)
    Throw-VerifierInfrastructure "Timed out waiting for $description."
}

function clickPoint($socket, [ref]$nextId, $point, [string]$button, [ref]$failures) {
    [void](invokeCdp $socket $nextId 'Input.dispatchMouseEvent' @{
        type = 'mousePressed'; x = [double]$point.x; y = [double]$point.y; button = $button; clickCount = 1
    } $failures)
    [void](invokeCdp $socket $nextId 'Input.dispatchMouseEvent' @{
        type = 'mouseReleased'; x = [double]$point.x; y = [double]$point.y; button = $button; clickCount = 1
    } $failures)
}

function clickButton($socket, [ref]$nextId, [string]$text, [ref]$failures) {
    $escaped = $text.Replace("'", "\'")
    $point = $null
    for ($scrollAttempt = 0; $scrollAttempt -lt 20; $scrollAttempt++) {
        $point = evaluateCdp $socket $nextId "(()=>{const e=[...document.querySelectorAll('button')].find(x=>x.innerText.trim()==='$escaped');if(!e)return null;const r=e.getBoundingClientRect();return {x:r.left+r.width/2,y:r.top+r.height/2,visible:r.top>=0&&r.bottom<=innerHeight};})()" $failures
        if ($null -eq $point -or $point.visible) { break }
        $wheelY = if ([double]$point.y -gt 0) { 640 } else { -640 }
        [void](invokeCdp $socket $nextId 'Input.dispatchMouseEvent' @{
            type = 'mouseWheel'; x = [Math]::Max(1, [Math]::Min(1439, [double]$point.x));
            y = 500; deltaX = 0; deltaY = $wheelY
        } $failures)
        Start-Sleep -Milliseconds 80
    }
    if ($null -eq $point) { Throw-VerifierInfrastructure "button not found: $text" }
    if (-not $point.visible) { Throw-VerifierInfrastructure "button did not scroll into view: $text" }
    clickPoint $socket $nextId $point 'left' $failures
}

function clickButtonAndWaitForPredicate($socket, [ref]$nextId, [string]$text,
        [string]$successExpression, [DateTime]$deadline, [ref]$failures,
        [string]$description) {
    $escaped = $text.Replace("'", "\'")
    $lastDiagnostic = ''
    for ($clickAttempt = 0; $clickAttempt -lt 5; $clickAttempt++) {
        try {
            $point = $null
            for ($visibilityAttempt = 0; $visibilityAttempt -lt 5; $visibilityAttempt++) {
                $point = evaluateCdp $socket $nextId "(()=>{const e=[...document.querySelectorAll('button')].find(x=>x.innerText.trim()==='$escaped');if(!e)return null;const r=e.getBoundingClientRect();return {x:r.left+r.width/2,y:r.top+r.height/2,visible:r.top>=0&&r.bottom<=innerHeight,enabled:!e.disabled};})()" $failures
                if ($null -eq $point) { break }
                if ($point.visible -and $point.enabled) { break }
                if (-not $point.visible) {
                    $wheelY = if ([double]$point.y -gt 0) { 640 } else { -640 }
                    [void](invokeCdp $socket $nextId 'Input.dispatchMouseEvent' @{
                        type = 'mouseWheel'; x = [Math]::Max(1, [Math]::Min(1439, [double]$point.x));
                        y = 500; deltaX = 0; deltaY = $wheelY
                    } $failures)
                }
                Start-Sleep -Milliseconds 80
            }
            if ($null -eq $point) { Throw-VerifierInfrastructure "button not found: $text" }
            if (-not $point.visible) { Throw-VerifierInfrastructure "button did not scroll into view: $text" }
            if (-not $point.enabled) { Throw-VerifierInfrastructure "button is disabled: $text" }

            clickPoint $socket $nextId $point 'left' $failures
            $settleDeadline = [DateTime]::UtcNow.AddSeconds(3)
            if ($settleDeadline -gt $deadline) { $settleDeadline = $deadline }
            waitForCdp $socket $nextId $successExpression $settleDeadline ([ref]$failures) $description
            return
        } catch {
            if (Test-VerifierInfrastructureError $_) { throw }
            $lastDiagnostic = $_.Exception.Message
            try {
                $state = evaluateCdp $socket $nextId "(()=>{const button=[...document.querySelectorAll('button')].find(x=>x.innerText.trim()==='$escaped');return {button:button?{visible:(()=>{const r=button.getBoundingClientRect();return r.top>=0&&r.bottom<=innerHeight;})(),enabled:!button.disabled}:null,body:(document.body.innerText||'').slice(-900)};})()" $failures
                $lastDiagnostic = $lastDiagnostic + ' state=' + ($state | ConvertTo-Json -Compress)
            } catch {
                $lastDiagnostic = $lastDiagnostic + ' state-diagnostic-failed=' + $_.Exception.Message
            }
            if ([DateTime]::UtcNow -ge $deadline) { break }
            Start-Sleep -Milliseconds 120
        }
    }
    Throw-VerifierInfrastructure "button interaction did not settle for $text after 5 mouse-click attempts: $lastDiagnostic"
}

function clickTrayPartAndWaitForSelection($socket, [ref]$nextId, [string]$buttonText,
        [DateTime]$deadline, [ref]$failures) {
    $escapedButtonText = $buttonText.Replace("'", "\'")
    $escapedSelectedText = ("Selected: " + $buttonText).Replace("'", "\'")
    $lastDiagnostic = ''
    for ($selectionAttempt = 0; $selectionAttempt -lt 5; $selectionAttempt++) {
        try {
            $button = $null
            for ($visibilityAttempt = 0; $visibilityAttempt -lt 5; $visibilityAttempt++) {
                $button = evaluateCdp $socket $nextId "(()=>{const e=[...document.querySelectorAll('button')].find(x=>x.innerText.trim()==='$escapedButtonText');if(!e)return null;const r=e.getBoundingClientRect();return {x:r.left+r.width/2,y:r.top+r.height/2,visible:r.top>=0&&r.bottom<=innerHeight,enabled:!e.disabled};})()" $failures
                if ($null -eq $button) { break }
                if ($button.visible -and $button.enabled) { break }
                if (-not $button.visible) {
                    $wheelY = if ([double]$button.y -gt 0) { 640 } else { -640 }
                    [void](invokeCdp $socket $nextId 'Input.dispatchMouseEvent' @{
                        type = 'mouseWheel'; x = [Math]::Max(1, [Math]::Min(1439, [double]$button.x));
                        y = 500; deltaX = 0; deltaY = $wheelY
                    } $failures)
                }
                Start-Sleep -Milliseconds 80
            }
            if ($null -eq $button) { Throw-VerifierInfrastructure "tray button not found: $buttonText" }
            if (-not $button.visible) { Throw-VerifierInfrastructure "tray button did not scroll into view: $buttonText" }
            if (-not $button.enabled) { Throw-VerifierInfrastructure "tray button is disabled: $buttonText" }
            clickPoint $socket $nextId $button 'left' $failures
            $selectionDeadline = [DateTime]::UtcNow.AddSeconds(3)
            if ($selectionDeadline -gt $deadline) { $selectionDeadline = $deadline }
            waitForCdp $socket $nextId "[...document.querySelectorAll('.tsj-component-panel')].some(p=>p.innerText.includes('$escapedSelectedText')&&p.innerText.includes('State: Loose'))" $selectionDeadline ([ref]$failures) 'selected loose tray part panel'
            return evaluateCdp $socket $nextId "(()=>{const panel=[...document.querySelectorAll('.tsj-component-panel')].find(p=>p.innerText.includes('$escapedSelectedText')&&p.innerText.includes('State: Loose'));return panel?panel.innerText:'';})()" $failures
        } catch {
            if (Test-VerifierInfrastructureError $_) { throw }
            $lastDiagnostic = $_.Exception.Message
            $state = evaluateCdp $socket $nextId "(()=>{const button=[...document.querySelectorAll('button')].find(x=>x.innerText.trim()==='$escapedButtonText');return {button:button?{visible:(()=>{const r=button.getBoundingClientRect();return r.top>=0&&r.bottom<=innerHeight;})(),enabled:!button.disabled}:null,panels:[...document.querySelectorAll('.tsj-component-panel')].map(p=>p.innerText).slice(-4)};})()" $failures
            $lastDiagnostic = $lastDiagnostic + ' state=' + ($state | ConvertTo-Json -Compress)
            if ([DateTime]::UtcNow -ge $deadline) { break }
            Start-Sleep -Milliseconds 120
        }
    }
    Throw-VerifierInfrastructure "tray part selection did not settle for $buttonText after 5 mouse-click attempts: $lastDiagnostic"
}

function getCanvasPoint($socket, [ref]$nextId, [string]$targetKey, [ref]$failures) {
    $escaped = $targetKey.Replace("'", "\\'")
    return evaluateCdp $socket $nextId "(()=>{const c=[...document.querySelectorAll('canvas')].find(x=>{const r=x.getBoundingClientRect();return r.width>100&&r.height>100});const g=window.__tsjPcbGeometry;if(!c||!g||!g.points||!g.points['$escaped'])return null;const r=c.getBoundingClientRect(),p=g.points['$escaped'];return {x:r.left+p.x*r.width/c.width,y:r.top+p.y*r.height/c.height};})()" $failures
}

function getMeterVoltage($socket, [ref]$nextId, [ref]$failures, [string]$description) {
    $reading = [string](evaluateCdp $socket $nextId "(()=>{const e=document.querySelector('.tsj-meter-display');return e?e.innerText:'';})()" $failures)
    $match = [regex]::Match($reading, '^\s*(-?(?:\d+(?:\.\d*)?|\.\d+))\s*([pnμµumkMG]?)V\s*$')
    if (-not $match.Success) { throw "$description was not a DC voltage reading: $reading" }
    $scale = switch ($match.Groups[2].Value) {
        'p' { 1e-12; break }
        'n' { 1e-9; break }
        'μ' { 1e-6; break }
        'µ' { 1e-6; break }
        'u' { 1e-6; break }
        'm' { 1e-3; break }
        'k' { 1e3; break }
        'M' { 1e6; break }
        'G' { 1e9; break }
        default { 1; break }
    }
    return [double]::Parse($match.Groups[1].Value, [Globalization.CultureInfo]::InvariantCulture) * $scale
}

function getPlayerValueLeakDiagnostics($socket, [ref]$nextId, [string]$originalValue,
        [ref]$failures) {
    $escaped = $originalValue.Replace('\', '\\').Replace('"', '\"')
    $expression = '(()=>{const pattern=new RegExp("' + $escaped + '\\s*ohm","i");' +
        'const outside=e=>e&&!e.closest("select");let text=[],walker=document.createTreeWalker(' +
        'document.body,NodeFilter.SHOW_TEXT),node;while((node=walker.nextNode())!==null)' +
        'if(outside(node.parentElement)&&pattern.test(node.nodeValue))text.push(node.nodeValue.trim());' +
        'let attributes=[];for(const e of document.querySelectorAll("*"))if(outside(e))' +
        'for(const a of e.attributes)if(pattern.test(a.value))attributes.push(a.name+":"+a.value);' +
        'return {safe:text.length===0&&attributes.length===0,text:text,attributes:attributes};})()'
    return evaluateCdp $socket $nextId $expression $failures
}

function getResistorBandColors($socket, [ref]$nextId, [string]$firstPad,
        [string]$secondPad, [ref]$failures) {
    $first = $firstPad.Replace('"', '\"')
    $second = $secondPad.Replace('"', '\"')
    $expression = '(()=>{const c=[...document.querySelectorAll("canvas")].find(x=>{const r=x.getBoundingClientRect();' +
        'return r.width>100&&r.height>100}),r=c.getBoundingClientRect(),g=c.getContext("2d"),' +
        'points=window.__tsjPcbGeometry.points,a=points["' + $first + '"],b=points["' + $second + '"],' +
        'x1=Math.min(a.x,b.x),x2=Math.max(a.x,b.x),y=(a.y+b.y)/2,colors={brown:"125,74,45",' +
        'black:"34,34,34",red:"181,35,45",orange:"204,108,43",blue:"53,92,170",' +
        'gray:"115,119,123",gold:"199,163,59"},found={};' +
        'for(let x=x1;x<=x2;x++){const p=[...g.getImageData(Math.round(x*c.width/r.width),' +
        'Math.round(y*c.height/r.height),1,1).data].slice(0,3).join(",");for(const name in colors)' +
        'if(p===colors[name])found[name]=true;}return found;})()'
    return evaluateCdp $socket $nextId $expression $failures
}

function getResistorBandSequence($socket, [ref]$nextId, [string]$firstPad,
        [string]$secondPad, [ref]$failures) {
    $first = $firstPad.Replace('"', '\"')
    $second = $secondPad.Replace('"', '\"')
    $expression = '(()=>{const c=[...document.querySelectorAll("canvas")].find(x=>{const r=x.getBoundingClientRect();' +
        'return r.width>100&&r.height>100}),r=c.getBoundingClientRect(),g=c.getContext("2d"),' +
        'points=window.__tsjPcbGeometry.points,a=points["' + $first + '"],b=points["' + $second + '"],' +
        'x1=Math.min(a.x,b.x),x2=Math.max(a.x,b.x),y=(a.y+b.y)/2,colors={brown:"125,74,45",' +
        'black:"34,34,34",red:"181,35,45",orange:"204,108,43",blue:"53,92,170",' +
        'gray:"115,119,123",gold:"199,163,59"},hits=[];' +
        'for(let x=x1;x<=x2;x++){const p=[...g.getImageData(Math.round(x*c.width/r.width),' +
        'Math.round(y*c.height/r.height),1,1).data].slice(0,3).join(","),' +
        'name=Object.keys(colors).find(candidate=>p===colors[candidate]);' +
        'if(name!==undefined)hits.push({x:x,name:name});}' +
        'const bands=[],last={x:null,name:null};for(const hit of hits)' +
        'if(last.x===null||hit.x-last.x>8||hit.name!==last.name){bands.push(hit.name);last.x=hit.x;last.name=hit.name;}' +
        'return bands;})()'
    return evaluateCdp $socket $nextId $expression $failures
}

function getDiodeNormalPlayerExpectation([int]$playerSeed) {
    switch ($playerSeed) {
        0 { return @{ Value = '330'; Bands = @('orange', 'orange', 'brown', 'gold') } }
        2 { return @{ Value = '680'; Bands = @('blue', 'gray', 'brown', 'gold') } }
        3 { return @{ Value = '1000'; Bands = @('brown', 'black', 'red', 'gold') } }
        default { throw "unsupported diode normal-player seed: $playerSeed (expected 0, 2, or 3)" }
    }
}

function sendKey($socket, [ref]$nextId, [string]$key, [int]$code, [ref]$failures) {
    [void](invokeCdp $socket $nextId 'Input.dispatchKeyEvent' @{
        type = 'keyDown'; key = $key; code = $key; windowsVirtualKeyCode = $code;
        nativeVirtualKeyCode = $code
    } $failures)
    [void](invokeCdp $socket $nextId 'Input.dispatchKeyEvent' @{
        type = 'keyUp'; key = $key; code = $key; windowsVirtualKeyCode = $code;
        nativeVirtualKeyCode = $code
    } $failures)
    Start-Sleep -Milliseconds 30
}

function selectOptionWithKeyboard($socket, [ref]$nextId, [int]$selectIndex,
        [string]$optionText, [ref]$failures) {
    $escaped = $optionText.Replace("'", "\'")
    $info = $null
    for ($scrollAttempt = 0; $scrollAttempt -lt 20; $scrollAttempt++) {
        $info = evaluateCdp $socket $nextId "(()=>{const e=document.querySelectorAll('select')[$selectIndex];if(!e)return null;const r=e.getBoundingClientRect();return {x:r.left+r.width/2,y:r.top+r.height/2,index:[...e.options].findIndex(o=>o.text==='$escaped'),visible:r.top>=0&&r.bottom<=innerHeight};})()" $failures
        if ($null -eq $info -or $info.visible) { break }
        $wheelY = if ([double]$info.y -gt 0) { 640 } else { -640 }
        [void](invokeCdp $socket $nextId 'Input.dispatchMouseEvent' @{
            type = 'mouseWheel'; x = [Math]::Max(1, [Math]::Min(1439, [double]$info.x));
            y = 500; deltaX = 0; deltaY = $wheelY
        } $failures)
        Start-Sleep -Milliseconds 80
    }
    if ($null -eq $info -or [int]$info.index -lt 0) { Throw-VerifierInfrastructure "catalog option not found: $optionText" }
    if (-not $info.visible) { Throw-VerifierInfrastructure "catalog did not scroll into view: $optionText" }
    $actual = ''
    $lastSelectionFailure = ''
    for ($selectionAttempt = 0; $selectionAttempt -lt 3 -and $actual -ne $optionText;
            $selectionAttempt++) {
        try {
            $attemptInfo = evaluateCdp $socket $nextId "(()=>{const e=document.querySelectorAll('select')[$selectIndex];if(!e)return null;const r=e.getBoundingClientRect();return {x:r.left+r.width/2,y:r.top+r.height/2,index:[...e.options].findIndex(o=>o.text==='$escaped'),visible:r.top>=0&&r.bottom<=innerHeight};})()" $failures
            if ($null -eq $attemptInfo -or [int]$attemptInfo.index -lt 0) {
                throw "catalog option not found: $optionText"
            }
            if (-not $attemptInfo.visible) { throw "catalog did not scroll into view: $optionText" }
            $focusPlan = evaluateCdp $socket $nextId "(()=>{const target=document.querySelectorAll('select')[$selectIndex];return {found:!!target&&!target.disabled&&target.getClientRects().length>0};})()" $failures
            if (-not $focusPlan.found) { throw "catalog is not keyboard reachable: $optionText" }
            clickPoint $socket $nextId $attemptInfo 'left' ([ref]$failures)
            $selectionDeadline = [DateTime]::UtcNow.AddSeconds(5)
            waitForCdp $socket $nextId "document.activeElement===document.querySelectorAll('select')[$selectIndex]" $selectionDeadline ([ref]$failures) 'catalog select focus'
            sendKey $socket $nextId 'Escape' 27 $failures
            waitForCdp $socket $nextId "document.activeElement===document.querySelectorAll('select')[$selectIndex]" $selectionDeadline ([ref]$failures) 'catalog select focus after closing popup'
            sendKey $socket $nextId 'Home' 36 $failures
            waitForCdp $socket $nextId "(()=>{const e=document.querySelectorAll('select')[$selectIndex];return document.activeElement===e&&e.selectedIndex===0&&e.selectedOptions[0].text===e.options[0].text;})()" $selectionDeadline ([ref]$failures) 'catalog first option after Home'
            for ($index = 1; $index -le [int]$attemptInfo.index; $index++) {
                sendKey $socket $nextId 'ArrowDown' 40 $failures
                $stepIndex = $index
                waitForCdp $socket $nextId "(()=>{const e=document.querySelectorAll('select')[$selectIndex],i=$stepIndex;return document.activeElement===e&&e.selectedIndex===i&&e.selectedOptions[0].text===e.options[i].text;})()" $selectionDeadline ([ref]$failures) "catalog option $stepIndex after ArrowDown"
            }
            $actual = evaluateCdp $socket $nextId "document.querySelectorAll('select')[$selectIndex].selectedOptions[0].text" $failures
            if ($actual -ne $optionText) {
                throw "catalog keyboard selection chose $actual instead of $optionText"
            }
        } catch {
            if (Test-VerifierInfrastructureError $_) { throw }
            $lastSelectionFailure = $_.Exception.Message
            $actual = ''
        }
    }
    if ($actual -ne $optionText) {
        $focus = evaluateCdp $socket $nextId "(()=>{const e=document.querySelectorAll('select')[$selectIndex];return {active:document.activeElement===e,tag:document.activeElement&&document.activeElement.tagName,y:e.getBoundingClientRect().top};})()" $failures
        Throw-VerifierInfrastructure "catalog keyboard selection chose $actual instead of $optionText (active=$($focus.active), tag=$($focus.tag), y=$($focus.y), lastFailure=$lastSelectionFailure)"
    }
}

function waitForAnimationFrames($socket, [ref]$nextId, [DateTime]$deadline, [ref]$failures) {
    [void](evaluateCdp $socket $nextId "window.__tsjFrameWait=0;requestAnimationFrame(()=>requestAnimationFrame(()=>window.__tsjFrameWait=1));true" $failures)
    waitForCdp $socket $nextId "window.__tsjFrameWait===1" $deadline $failures 'two rendered animation frames'
}

function captureBrowserScreenshot($socket, [ref]$nextId, [string]$path, [ref]$failures) {
    if ($null -eq $script:VerifierContext -or
            -not (Test-VerifierChildPath $script:VerifierContext.EvidenceDirectory $path)) {
        Throw-VerifierInfrastructure "Screenshot path is outside this run's evidence namespace: $path"
    }
    if (Test-Path -LiteralPath $path) {
        Throw-VerifierInfrastructure "Refusing to overwrite existing screenshot evidence: $path"
    }
    $result = invokeCdp $socket $nextId 'Page.captureScreenshot' @{
        format = 'png'; fromSurface = $true; captureBeyondViewport = $false
    } $failures
    try {
        [IO.File]::WriteAllBytes($path, [Convert]::FromBase64String($result.result.data))
    } catch {
        Throw-VerifierInfrastructure "Could not write screenshot evidence '$path': $(Get-VerifierErrorMessage $_)"
    }
    Register-VerifierEvidenceArtifact $script:VerifierContext $path
}

function verifyQuickPlayNormalPlayer([string]$url, [bool]$finishProof) {
    if (-not $finishProof) { throw 'Quick Play fresh-page check did not follow finish-success proof' }
    $profile = $null
    $browser = $null
    $socket = $null
    $session = $null
    try {
        $session = startVerifierBrowser 'quick-play-normal-player' $url
        $profile = $session.Profile
        $browser = $session.Browser
        $socket = $session.Socket
        $deadline = $session.Deadline
        $script:CdpRouteDeadline = $deadline
        $nextId = 1
        $failures = @()
        [void](invokeCdp $socket ([ref]$nextId) 'Runtime.enable' @{} ([ref]$failures))
        [void](invokeCdp $socket ([ref]$nextId) 'Page.enable' @{} ([ref]$failures))
        $firstDocumentTimeOrigin = navigateAndWaitForDocument $socket ([ref]$nextId) $url $deadline ([ref]$failures)
        waitForCdp $socket ([ref]$nextId) "document.body&&document.body.innerText.includes('Service Ticket')&&document.body.innerText.includes('Finish Job')&&document.querySelectorAll('canvas').length>0" $deadline ([ref]$failures) 'normal Quick Play PCB and Finish Job'
        $privacy = evaluateCdp $socket ([ref]$nextId) "(()=>{const body=document.body.innerText||'',lower=body.toLowerCase(),root=document.documentElement;return {body:body,hidden:/fault|stress|damage|rating|specification|answer|wattage/.test(lower),family:root.getAttribute('data-tsj-quick-play-family'),seed:root.getAttribute('data-tsj-quick-play-seed'),report:root.getAttribute('data-tsj-quick-play-report'),cleanParts:body.includes('No removed parts')&&!body.includes('State: Loose'),failure:body.includes('Functional check failed.'),finish:[...document.querySelectorAll('button')].filter(x=>x.innerText.trim()==='Finish Job').length};})()" ([ref]$failures)
        if ($privacy.hidden -or $privacy.family -or $privacy.seed -or $privacy.report -or -not $privacy.cleanParts -or $privacy.failure -or $privacy.finish -ne 1) {
            throw "Quick Play normal-player privacy or control boundary failed: $($privacy | ConvertTo-Json -Compress)"
        }
        $priorDocumentMarker = [Guid]::NewGuid().ToString('N')
        [void](evaluateCdp $socket ([ref]$nextId) "window.__tsjVerifierPriorDocument='$priorDocumentMarker';true" ([ref]$failures))
        if ($script:VerifierEvidenceDirectory) {
            captureBrowserScreenshot $socket ([ref]$nextId) (getVerifierEvidencePath 'quick-play-initial.png') ([ref]$failures)
        }
        $secondDocumentTimeOrigin = navigateAndWaitForDocument $socket ([ref]$nextId) $url $deadline ([ref]$failures)
        waitForCdp $socket ([ref]$nextId) "document.body&&document.body.innerText.includes('Service Ticket')&&document.body.innerText.includes('Finish Job')&&!document.body.innerText.includes('Functional check failed.')" $deadline ([ref]$failures) 'fresh Quick Play reload state'
        $freshState = evaluateCdp $socket ([ref]$nextId) "(()=>{const body=document.body.innerText||'',root=document.documentElement;return {prior:typeof window.__tsjVerifierPriorDocument==='undefined'?'':window.__tsjVerifierPriorDocument,cleanParts:body.includes('No removed parts')&&!body.includes('State: Loose'),failure:body.includes('Functional check failed.'),family:root.getAttribute('data-tsj-quick-play-family'),seed:root.getAttribute('data-tsj-quick-play-seed'),report:root.getAttribute('data-tsj-quick-play-report'),finish:[...document.querySelectorAll('button')].filter(x=>x.innerText.trim()==='Finish Job').length};})()" ([ref]$failures)
        if ([double]$firstDocumentTimeOrigin -eq [double]$secondDocumentTimeOrigin -or
                $freshState.prior -ne '' -or -not $freshState.cleanParts -or
                $freshState.failure -or $freshState.family -or $freshState.seed -or
                $freshState.report -or $freshState.finish -ne 1) {
            throw "Quick Play reload did not create a clean new document: first=$firstDocumentTimeOrigin second=$secondDocumentTimeOrigin state=$($freshState | ConvertTo-Json -Compress)"
        }
        if ($failures.Count -gt 0) { throw ($failures -join '; ') }
        cleanupBrowser $browser $socket $profile
        $socket = $null
        $browser = $null
        Write-Host 'PASS quick-play-normal-player fresh-reload privacy=clean finish-job=visible'
        return $true
    } catch {
        Set-VerifierFailure $_ 'quick-play-normal-player'
        return $false
    } finally {
        try { cleanupBrowser $browser $socket $profile } catch { Set-VerifierFailure $_ 'quick-play-normal-player cleanup' }
        $script:CdpRouteDeadline = [DateTime]::MinValue
    }
}

function verifyNormalPlayer([string]$url) {
    $profile = $null
    $browser = $null
    $socket = $null
    $session = $null
    try {
        $session = startVerifierBrowser 'normal-player' $url
        $profile = $session.Profile
        $browser = $session.Browser
        $socket = $session.Socket
        $deadline = $session.Deadline
        $script:CdpRouteDeadline = $deadline
        $nextId = 1
        $failures = @()
        [void](invokeCdp $socket ([ref]$nextId) 'Runtime.enable' @{} ([ref]$failures))
        [void](invokeCdp $socket ([ref]$nextId) 'Page.enable' @{} ([ref]$failures))
        [void](navigateAndWaitForDocument $socket ([ref]$nextId) $url $deadline ([ref]$failures))
        waitForCdp $socket ([ref]$nextId) "document.body&&document.body.innerText.includes('Indicator does not light.')" $deadline ([ref]$failures) 'ready seed-3 challenge'
        waitForCdp $socket ([ref]$nextId) "!!window.__tsjPcbGeometry&&!!window.__tsjPcbGeometry.points" $deadline ([ref]$failures) 'procedural PCB geometry bridge'
        Write-Host 'PLAYER ready'
        $initial = evaluateCdp $socket ([ref]$nextId) "({canvas:!!document.querySelector('canvas'),meter:!!document.querySelector('.tsj-meter-panel'),power:[...document.querySelectorAll('button')].some(x=>x.innerText.includes('Board Power')),catalog:document.body.innerText.includes('Replacement Catalog'),empty:document.body.innerText.includes('No removed parts')})" ([ref]$failures)
        if (-not ($initial.canvas -and $initial.meter -and $initial.power -and $initial.catalog -and $initial.empty)) {
            throw 'initial PCB, meter, power, catalog, or empty tray UI was missing'
        }
        $installedBands = getResistorBandColors $socket ([ref]$nextId) 'pad:R1.1' 'pad:R1.2' ([ref]$failures)
        foreach ($band in @('brown', 'black', 'red', 'gold')) {
            if (-not ($installedBands.PSObject.Properties.Name -contains $band)) {
                throw "installed R1 color band was not visible: $($installedBands | ConvertTo-Json -Compress)"
            }
        }
        $r1 = getCanvasPoint $socket ([ref]$nextId) 'component:R1' ([ref]$failures)
        clickPoint $socket ([ref]$nextId) $r1 'left' ([ref]$failures)
        waitForCdp $socket ([ref]$nextId) "document.body.innerText.includes('Remove component')" $deadline ([ref]$failures) 'R1 component controls'
        $r1Panel = evaluateCdp $socket ([ref]$nextId) "document.querySelectorAll('.tsj-component-panel')[1].innerText" ([ref]$failures)
        if (-not ($r1Panel -match 'R1' -and $r1Panel -match 'Type: resistor' -and
                $r1Panel -match 'State: Installed' -and $r1Panel -match 'Markings: Color bands' -and
                $r1Panel -notmatch 'Value: 1000 Ohm')) {
            throw "original R1 panel did not preserve physical identity without its numeric value: $r1Panel"
        }
        $r1Leak = getPlayerValueLeakDiagnostics $socket ([ref]$nextId) '1000' ([ref]$failures)
        if (-not $r1Leak.safe) {
            throw "original R1 value leaked into ordinary UI: $($r1Leak | ConvertTo-Json -Compress)"
        }
        clickButton $socket ([ref]$nextId) 'Board Power: ON' ([ref]$failures)
        clickButton $socket ([ref]$nextId) 'Remove component' ([ref]$failures)
        waitForCdp $socket ([ref]$nextId) "[...document.querySelectorAll('button')].some(x=>x.innerText.trim()==='R1_ORIGINAL - Removed resistor')" $deadline ([ref]$failures) 'faulted original in tray'
        waitForAnimationFrames $socket ([ref]$nextId) $deadline ([ref]$failures)
        $removedLeak = getPlayerValueLeakDiagnostics $socket ([ref]$nextId) '1000' ([ref]$failures)
        if (-not $removedLeak.safe) {
            throw "removed original R1 value leaked into ordinary UI: $($removedLeak | ConvertTo-Json -Compress)"
        }
        $originalBands = getResistorBandColors $socket ([ref]$nextId) 'loose:R1_ORIGINAL:0' 'loose:R1_ORIGINAL:1' ([ref]$failures)
        foreach ($band in @('brown', 'black', 'red', 'gold')) {
            if (-not ($originalBands.PSObject.Properties.Name -contains $band)) {
                throw "removed R1 color band was not visible: $($originalBands | ConvertTo-Json -Compress)"
            }
        }
        $selectedOriginal = clickTrayPartAndWaitForSelection $socket ([ref]$nextId) 'R1_ORIGINAL - Removed resistor' $deadline ([ref]$failures)
        if ($selectedOriginal -notmatch 'Selected: R1_ORIGINAL - Removed resistor' -or
                $selectedOriginal -notmatch 'State: Loose') {
            throw "selected original R1 lost its privacy-safe identity: $selectedOriginal"
        }
        $selectedOriginalLeak = getPlayerValueLeakDiagnostics $socket ([ref]$nextId) '1000' ([ref]$failures)
        if (-not $selectedOriginalLeak.safe) {
            throw "selected original R1 exposed its numeric value: $($selectedOriginalLeak | ConvertTo-Json -Compress)"
        }
        Write-Host 'PLAYER original removed'

        selectOptionWithKeyboard $socket ([ref]$nextId) 0 '1000 Ohm +/-5%' ([ref]$failures)
        clickButton $socket ([ref]$nextId) 'Install new resistor' ([ref]$failures)
        waitForCdp $socket ([ref]$nextId) "[...document.querySelectorAll('button')].some(x=>x.innerText.trim()==='R1_ORIGINAL - Removed resistor')&&[...document.querySelectorAll('select option')].some(x=>x.text==='1000 Ohm +/-5%')" $deadline ([ref]$failures) 'installed replacement excluded from tray while catalog value remains available'
        clickButtonAndWaitForPredicate $socket ([ref]$nextId) 'Board Power: OFF' "document.body.innerText.includes('Board Power: ON')" $deadline ([ref]$failures) 'power on before customer retest'
        clickButtonAndWaitForPredicate $socket ([ref]$nextId) 'Retest Customer' "document.body.innerText.includes('Repair verified. Indicator operating normally.')" $deadline ([ref]$failures) 'solver-backed customer retest completion'
        $terminal = evaluateCdp $socket ([ref]$nextId) "(()=>{const b=document.body.innerText||'',buttons=[...document.querySelectorAll('button')];const power=buttons.find(x=>x.innerText.includes('Board Power:'));const retest=buttons.find(x=>x.innerText.trim()==='Retest Customer');return {completed:b.includes('Repair verified. Indicator operating normally.'),powerDisabled:!!power&&power.disabled,retestDisabled:!!retest&&retest.disabled};})()" ([ref]$failures)
        if (-not ($terminal.completed -and $terminal.powerDisabled -and $terminal.retestDisabled)) {
            throw "completed resistor challenge did not enter the physical terminal state: $($terminal | ConvertTo-Json -Compress)"
        }
        Write-Host 'PLAYER repair verified; physical state is terminal'
        if ($failures.Count -gt 0) { throw ($failures -join '; ') }
        cleanupBrowser $browser $socket $profile
        $socket = $null
        $browser = $null
        Write-Host "PASS normal-player seed=3 terminal=mutation-free"
        return $true
    } catch {
        Set-VerifierFailure $_ 'normal-player seed=3'
        if ($null -ne $socket) {
            try {
                $snapshot = evaluateCdp $socket ([ref]$nextId) "document.body.innerText" ([ref]$failures)
                $start = [Math]::Max(0, $snapshot.Length - 1400)
                Write-Host ("NORMAL PLAYER UI SNAPSHOT: " + $snapshot.Substring($start).Replace("`n", ' | '))
            } catch { }
        }
        return $false
    } finally {
        try { cleanupBrowser $browser $socket $profile } catch { Set-VerifierFailure $_ 'normal-player cleanup' }
        $script:CdpRouteDeadline = [DateTime]::MinValue
    }
}

function verifyNormalParallelPlayer([string]$url) {
    $profile = $null
    $browser = $null
    $socket = $null
    $session = $null
    try {
        $session = startVerifierBrowser 'parallel-normal-player' $url
        $profile = $session.Profile
        $browser = $session.Browser
        $socket = $session.Socket
        $deadline = $session.Deadline
        $script:CdpRouteDeadline = $deadline
        $nextId = 1
        $failures = @()
        [void](invokeCdp $socket ([ref]$nextId) 'Runtime.enable' @{} ([ref]$failures))
        [void](invokeCdp $socket ([ref]$nextId) 'Page.enable' @{} ([ref]$failures))
        [void](navigateAndWaitForDocument $socket ([ref]$nextId) $url $deadline ([ref]$failures))
        waitForCdp $socket ([ref]$nextId) "document.body&&document.body.innerText.includes('The two indicators do not behave the same.')" $deadline ([ref]$failures) 'ready parallel challenge'
        waitForCdp $socket ([ref]$nextId) "!!window.__tsjPcbGeometry&&!!window.__tsjPcbGeometry.points" $deadline ([ref]$failures) 'parallel PCB geometry bridge'
        $initial = evaluateCdp $socket ([ref]$nextId) "({canvas:!!document.querySelector('canvas'),catalog:document.body.innerText.includes('Resistor Replacement Catalog'),noLedCatalog:!document.body.innerText.includes('LED Replacement Catalog'),empty:document.body.innerText.includes('No removed parts'),complaint:document.body.innerText.includes('The two indicators do not behave the same.')})" ([ref]$failures)
        if (-not ($initial.canvas -and $initial.catalog -and $initial.noLedCatalog -and $initial.empty -and $initial.complaint)) {
            throw 'initial parallel PCB, resistor catalog, tray, or complaint UI was incorrect'
        }
        $parallelR1Bands = getResistorBandColors $socket ([ref]$nextId) 'pad:R1.1' 'pad:R1.2' ([ref]$failures)
        foreach ($band in @('brown', 'black', 'red', 'gold')) {
            if (-not ($parallelR1Bands.PSObject.Properties.Name -contains $band)) {
                throw "parallel R1 color band was not visible: $($parallelR1Bands | ConvertTo-Json -Compress)"
            }
        }
        $r1 = getCanvasPoint $socket ([ref]$nextId) 'component:R1' ([ref]$failures)
        clickPoint $socket ([ref]$nextId) $r1 'left' ([ref]$failures)
        waitForCdp $socket ([ref]$nextId) "document.body.innerText.includes('Markings: Color bands')" $deadline ([ref]$failures) 'parallel original R1 markings'
        $parallelR1Panel = evaluateCdp $socket ([ref]$nextId) "document.querySelectorAll('.tsj-component-panel')[1].innerText" ([ref]$failures)
        if ($parallelR1Panel -match 'Value: 1000 Ohm') { throw "parallel original R1 value leaked: $parallelR1Panel" }
        $parallelR1Leak = getPlayerValueLeakDiagnostics $socket ([ref]$nextId) '1000' ([ref]$failures)
        if (-not $parallelR1Leak.safe) {
            throw "parallel original R1 value leaked into ordinary UI: $($parallelR1Leak | ConvertTo-Json -Compress)"
        }
        $r2 = getCanvasPoint $socket ([ref]$nextId) 'component:R2' ([ref]$failures)
        clickPoint $socket ([ref]$nextId) $r2 'left' ([ref]$failures)
        waitForCdp $socket ([ref]$nextId) "document.body.innerText.includes('Markings: Color bands')" $deadline ([ref]$failures) 'parallel original R2 markings'
        $parallelR2Panel = evaluateCdp $socket ([ref]$nextId) "document.querySelectorAll('.tsj-component-panel')[1].innerText" ([ref]$failures)
        if (-not ($parallelR2Panel -match 'R2' -and $parallelR2Panel -match 'Type: resistor' -and
                $parallelR2Panel -match 'State: Installed' -and $parallelR2Panel -notmatch 'Value: 2200 Ohm')) {
            throw "parallel original R2 panel did not preserve identity without its numeric value: $parallelR2Panel"
        }
        $parallelR2Leak = getPlayerValueLeakDiagnostics $socket ([ref]$nextId) '2200' ([ref]$failures)
        if (-not $parallelR2Leak.safe) {
            throw "parallel original R2 value leaked into ordinary UI: $($parallelR2Leak | ConvertTo-Json -Compress)"
        }
        $parallelR2Bands = getResistorBandColors $socket ([ref]$nextId) 'pad:R2.1' 'pad:R2.2' ([ref]$failures)
        foreach ($band in @('red', 'gold')) {
            if (-not ($parallelR2Bands.PSObject.Properties.Name -contains $band)) {
                throw "parallel R2 color band was not visible: $($parallelR2Bands | ConvertTo-Json -Compress)"
            }
        }
        if ($script:VerifierEvidenceDirectory) {
            captureBrowserScreenshot $socket ([ref]$nextId) (getVerifierEvidencePath 'parallel-seed-3.png') ([ref]$failures)
            captureBrowserScreenshot $socket ([ref]$nextId) (getVerifierEvidencePath 'parallel-faulted.png') ([ref]$failures)
        }
        clickButton $socket ([ref]$nextId) 'DC V' ([ref]$failures)
        $vin = getCanvasPoint $socket ([ref]$nextId) 'pad:J1.1' ([ref]$failures)
        $ground = getCanvasPoint $socket ([ref]$nextId) 'pad:J1.2' ([ref]$failures)
        clickPoint $socket ([ref]$nextId) $vin 'left' ([ref]$failures)
        clickPoint $socket ([ref]$nextId) $ground 'right' ([ref]$failures)
        waitForCdp $socket ([ref]$nextId) "document.querySelector('.tsj-meter-display').innerText.includes('V')" $deadline ([ref]$failures) 'parallel supply voltage reading'
        if ($script:VerifierEvidenceDirectory) {
            captureBrowserScreenshot $socket ([ref]$nextId) (getVerifierEvidencePath 'parallel-measurement.png') ([ref]$failures)
        }
        Write-Host 'PARALLEL PLAYER supply measurement complete'
        clickButton $socket ([ref]$nextId) 'DC V' ([ref]$failures)
        Write-Host 'PARALLEL PLAYER DC mode exited'
        clickButton $socket ([ref]$nextId) 'Board Power: ON' ([ref]$failures)
        Write-Host 'PARALLEL PLAYER power on'
        $currentR1 = getCanvasPoint $socket ([ref]$nextId) 'component:R1' ([ref]$failures)
        if ($null -eq $currentR1) { throw 'parallel current R1 geometry was unavailable after power transition' }
        clickPoint $socket ([ref]$nextId) $currentR1 'left' ([ref]$failures)
        $currentR1Panel = evaluateCdp $socket ([ref]$nextId) "document.querySelectorAll('.tsj-component-panel')[1].innerText" ([ref]$failures)
        if ($currentR1Panel -notmatch '(?m)^R1$' -or $currentR1Panel -notmatch 'Remove component') {
            throw "parallel post-power contextual panel did not identify R1 with expected action: $currentR1Panel"
        }
        waitForCdp $socket ([ref]$nextId) "document.body.innerText.includes('Remove component')" $deadline ([ref]$failures) 'parallel R1 component controls'
        clickButton $socket ([ref]$nextId) 'Remove component' ([ref]$failures)
        waitForCdp $socket ([ref]$nextId) "[...document.querySelectorAll('button')].some(x=>x.innerText.trim()==='R1_ORIGINAL - Removed resistor')" $deadline ([ref]$failures) 'parallel faulted original tray part'
        $parallelRemovedLeak = getPlayerValueLeakDiagnostics $socket ([ref]$nextId) '1000' ([ref]$failures)
        if (-not $parallelRemovedLeak.safe) {
            throw "parallel removed original R1 value leaked into ordinary UI: $($parallelRemovedLeak | ConvertTo-Json -Compress)"
        }
        $parallelSelectedOriginal = clickTrayPartAndWaitForSelection $socket ([ref]$nextId) 'R1_ORIGINAL - Removed resistor' $deadline ([ref]$failures)
        if ($parallelSelectedOriginal -notmatch 'Selected: R1_ORIGINAL - Removed resistor' -or
                $parallelSelectedOriginal -notmatch 'State: Loose') {
            throw "parallel selected original R1 lost its privacy-safe identity: $parallelSelectedOriginal"
        }
        $parallelSelectedOriginalLeak = getPlayerValueLeakDiagnostics $socket ([ref]$nextId) '1000' ([ref]$failures)
        if (-not $parallelSelectedOriginalLeak.safe) {
            throw "parallel selected original R1 exposed its numeric value: $($parallelSelectedOriginalLeak | ConvertTo-Json -Compress)"
        }
        selectOptionWithKeyboard $socket ([ref]$nextId) 0 '1000 Ohm +/-5%' ([ref]$failures)
        clickButton $socket ([ref]$nextId) 'Install new resistor' ([ref]$failures)
        clickButtonAndWaitForPredicate $socket ([ref]$nextId) 'Board Power: OFF' "document.body.innerText.includes('Board Power: ON')" $deadline ([ref]$failures) 'parallel power on before customer retest'
        clickButtonAndWaitForPredicate $socket ([ref]$nextId) 'Retest Customer' "document.body.innerText.includes('Repair verified. Both indicators operating normally.')" $deadline ([ref]$failures) 'parallel customer retest'
        if ($script:VerifierEvidenceDirectory) {
            waitForAnimationFrames $socket ([ref]$nextId) $deadline ([ref]$failures)
            captureBrowserScreenshot $socket ([ref]$nextId) (getVerifierEvidencePath 'parallel-repaired.png') ([ref]$failures)
        }
        if ($failures.Count -gt 0) { throw ($failures -join '; ') }
        cleanupBrowser $browser $socket $profile
        $socket = $null
        $browser = $null
        Write-Host 'PASS parallel-normal-player seed=3 supply=solver-backed repair=verified'
        return $true
    } catch {
        Set-VerifierFailure $_ 'parallel-normal-player seed=3'
        if ($null -ne $socket) {
            try {
                $snapshot = evaluateCdp $socket ([ref]$nextId) "document.body.innerText" ([ref]$failures)
                $start = [Math]::Max(0, $snapshot.Length - 1800)
                Write-Host ("PARALLEL PLAYER UI SNAPSHOT: " + $snapshot.Substring($start).Replace([Environment]::NewLine, ' | '))
            } catch { }
        }
        return $false
    } finally {
        try { cleanupBrowser $browser $socket $profile } catch { Set-VerifierFailure $_ 'parallel-normal-player cleanup' }
        $script:CdpRouteDeadline = [DateTime]::MinValue
    }
}

function verifyNormalDiodePlayer([string]$url, [int]$playerSeed = 3) {
    $diodeExpectation = getDiodeNormalPlayerExpectation $playerSeed
    $diodeValue = [string]$diodeExpectation.Value
    $expectedDiodeBands = @($diodeExpectation.Bands)
    $profile = $null
    $browser = $null
    $socket = $null
    $session = $null
    try {
        $session = startVerifierBrowser 'diode-normal-player' $url
        $profile = $session.Profile
        $browser = $session.Browser
        $socket = $session.Socket
        $deadline = $session.Deadline
        $script:CdpRouteDeadline = $deadline
        $nextId = 1
        $failures = @()
        [void](invokeCdp $socket ([ref]$nextId) 'Runtime.enable' @{} ([ref]$failures))
        [void](invokeCdp $socket ([ref]$nextId) 'Page.enable' @{} ([ref]$failures))
        [void](navigateAndWaitForDocument $socket ([ref]$nextId) $url $deadline ([ref]$failures))
        waitForCdp $socket ([ref]$nextId) "document.body&&document.body.innerText.includes('Indicator does not light.')" $deadline ([ref]$failures) 'ready diode challenge'
        waitForCdp $socket ([ref]$nextId) "!!window.__tsjPcbGeometry&&!!window.__tsjPcbGeometry.points" $deadline ([ref]$failures) 'procedural diode PCB geometry bridge'
        $initial = evaluateCdp $socket ([ref]$nextId) "({canvas:!!document.querySelector('canvas'),catalog:document.body.innerText.includes('Replacement Catalog'),empty:document.body.innerText.includes('No removed parts'),disclosed:/D1 failed|diode is open/i.test(document.body.innerText)})" ([ref]$failures)
        if (-not ($initial.canvas -and $initial.catalog -and $initial.empty) -or $initial.disclosed) {
            throw 'initial diode workbench, catalog, tray, or vague complaint was incorrect'
        }
        $diodeR1Bands = getResistorBandColors $socket ([ref]$nextId) 'pad:R1.1' 'pad:R1.2' ([ref]$failures)
        $diodeR1Sequence = @(getResistorBandSequence $socket ([ref]$nextId) 'pad:R1.1' 'pad:R1.2' ([ref]$failures))
        $sequenceMatches = $diodeR1Sequence.Count -eq $expectedDiodeBands.Count
        if ($sequenceMatches) {
            for ($bandIndex = 0; $bandIndex -lt $expectedDiodeBands.Count; $bandIndex++) {
                if ($diodeR1Sequence[$bandIndex] -ne $expectedDiodeBands[$bandIndex]) {
                    $sequenceMatches = $false
                    break
                }
            }
        }
        if (-not $sequenceMatches) {
            $observed = if ($diodeR1Sequence.Count -eq 0) { '<none>' } else { $diodeR1Sequence -join ', ' }
            throw "diode-family R1 bands did not match seed=$playerSeed value=$diodeValue Ohm; expected [$($expectedDiodeBands -join ', ')] but observed [$observed]; recognized pixels=$($diodeR1Bands | ConvertTo-Json -Compress)"
        }
        $diodeR1 = getCanvasPoint $socket ([ref]$nextId) 'component:R1' ([ref]$failures)
        clickPoint $socket ([ref]$nextId) $diodeR1 'left' ([ref]$failures)
        waitForCdp $socket ([ref]$nextId) "document.body.innerText.includes('Markings: Color bands')" $deadline ([ref]$failures) 'diode-family original R1 markings'
        $diodeR1Panel = evaluateCdp $socket ([ref]$nextId) "document.querySelectorAll('.tsj-component-panel')[1].innerText" ([ref]$failures)
        if (-not ($diodeR1Panel -match 'R1' -and $diodeR1Panel -match 'Type: resistor' -and
                $diodeR1Panel -match 'State: Installed' -and
                $diodeR1Panel -notmatch ('Value: ' + [regex]::Escape($diodeValue) + ' Ohm'))) {
            throw "diode-family original R1 panel did not preserve identity without its numeric value for seed=${playerSeed}: $diodeR1Panel"
        }
        $diodeR1Leak = getPlayerValueLeakDiagnostics $socket ([ref]$nextId) $diodeValue ([ref]$failures)
        if (-not $diodeR1Leak.safe) {
            throw "diode-family original R1 value=$diodeValue leaked into ordinary UI for seed=${playerSeed}: $($diodeR1Leak | ConvertTo-Json -Compress)"
        }
        if ($script:VerifierEvidenceDirectory) {
            $initialEvidenceName = if ($PersistentPreviewEvidence) {
                'persistent-preview-fresh-load.png'
            } else { 'initial-board.png' }
            captureBrowserScreenshot $socket ([ref]$nextId) (getVerifierEvidencePath $initialEvidenceName) ([ref]$failures)
        }
        if ($PersistentPreviewEvidence) {
            cleanupBrowser $browser $socket $profile
            $socket = $null
            $browser = $null
            Write-Host 'PASS persistent-preview fresh diode normal-player load'
            return $true
        }
        $pixels = evaluateCdp $socket ([ref]$nextId) "(()=>{const c=[...document.querySelectorAll('canvas')].find(x=>{const r=x.getBoundingClientRect();return r.width>100&&r.height>100}),r=c.getBoundingClientRect(),g=c.getContext('2d'),p=window.__tsjPcbGeometry.points,at=(x,y)=>[...g.getImageData(Math.round(x*c.width/r.width),Math.round(y*c.height/r.height),1,1).data].slice(0,3).join(','),a=p['pad:D1.A'],k=p['pad:D1.K'],la=p['pad:LED1.A'],lk=p['pad:LED1.K'];return {body:at((a.x+k.x)/2,a.y),band:at(k.x-45,k.y),led:at((la.x+lk.x)/2,la.y-33)};})()" ([ref]$failures)
        if ($pixels.body -eq $pixels.led -or $pixels.band -eq $pixels.body) {
            throw "D1 body, cathode band, and LED1 were not visibly distinct: $($pixels | ConvertTo-Json -Compress)"
        }
        Write-Host "DIODE PLAYER rendered body=$($pixels.body) band=$($pixels.band) led=$($pixels.led)"

        clickButton $socket ([ref]$nextId) 'Board Power: ON' ([ref]$failures)
        $d1 = getCanvasPoint $socket ([ref]$nextId) 'component:D1' ([ref]$failures)
        clickPoint $socket ([ref]$nextId) $d1 'left' ([ref]$failures)
        waitForCdp $socket ([ref]$nextId) "document.body.innerText.includes('Remove component')" $deadline ([ref]$failures) 'D1 component controls'
        clickButton $socket ([ref]$nextId) 'DIODE' ([ref]$failures)
        $d1Anode = getCanvasPoint $socket ([ref]$nextId) 'pad:D1.A' ([ref]$failures)
        $d1Cathode = getCanvasPoint $socket ([ref]$nextId) 'pad:D1.K' ([ref]$failures)
        clickPoint $socket ([ref]$nextId) $d1Anode 'left' ([ref]$failures)
        clickPoint $socket ([ref]$nextId) $d1Cathode 'right' ([ref]$failures)
        waitForCdp $socket ([ref]$nextId) "document.querySelector('.tsj-meter-display').innerText==='OL'" $deadline ([ref]$failures) 'installed open D1 forward OL'
        clickPoint $socket ([ref]$nextId) $d1Cathode 'left' ([ref]$failures)
        waitForCdp $socket ([ref]$nextId) "document.querySelector('.tsj-meter-display').innerText!=='OL'" $deadline ([ref]$failures) 'installed D1 intermediate measurement'
        clickPoint $socket ([ref]$nextId) $d1Anode 'right' ([ref]$failures)
        waitForCdp $socket ([ref]$nextId) "document.querySelector('.tsj-meter-display').innerText==='OL'" $deadline ([ref]$failures) 'installed open D1 reverse OL'
        clickButton $socket ([ref]$nextId) 'DIODE' ([ref]$failures)
        waitForCdp $socket ([ref]$nextId) "(()=>{const button=[...document.querySelectorAll('button')].find(x=>x.innerText.trim()==='DIODE');return !!button&&!button.className.includes('chsel');})()" $deadline ([ref]$failures) 'diode mode exit cleanup'
        clickButton $socket ([ref]$nextId) 'Remove component' ([ref]$failures)
        waitForCdp $socket ([ref]$nextId) "document.body.innerText.includes('D1_ORIGINAL - Generic silicon diode')" $deadline ([ref]$failures) 'loose original diode'
        waitForCdp $socket ([ref]$nextId) "!!window.__tsjPcbGeometry.points['loose:D1_ORIGINAL:0']&&!!window.__tsjPcbGeometry.points['loose:D1_ORIGINAL:1']" $deadline ([ref]$failures) 'loose original diode geometry'

        clickButton $socket ([ref]$nextId) 'DIODE' ([ref]$failures)
        $originalAnode = getCanvasPoint $socket ([ref]$nextId) 'loose:D1_ORIGINAL:0' ([ref]$failures)
        $originalCathode = getCanvasPoint $socket ([ref]$nextId) 'loose:D1_ORIGINAL:1' ([ref]$failures)
        clickPoint $socket ([ref]$nextId) $originalAnode 'left' ([ref]$failures)
        clickPoint $socket ([ref]$nextId) $originalCathode 'right' ([ref]$failures)
        waitForCdp $socket ([ref]$nextId) "document.querySelector('.tsj-meter-display').innerText==='OL'" $deadline ([ref]$failures) 'loose original forward OL'
        clickPoint $socket ([ref]$nextId) $originalCathode 'left' ([ref]$failures)
        waitForCdp $socket ([ref]$nextId) "document.querySelector('.tsj-meter-display').innerText!=='OL'" $deadline ([ref]$failures) 'loose original intermediate measurement'
        clickPoint $socket ([ref]$nextId) $originalAnode 'right' ([ref]$failures)
        waitForCdp $socket ([ref]$nextId) "document.querySelector('.tsj-meter-display').innerText==='OL'" $deadline ([ref]$failures) 'loose original reverse OL'
        clickButton $socket ([ref]$nextId) 'DIODE' ([ref]$failures)
        clickButton $socket ([ref]$nextId) 'Install new diode' ([ref]$failures)
        waitForCdp $socket ([ref]$nextId) "document.body.innerText.includes('Remove D1 before installing a replacement.')" $deadline ([ref]$failures) 'installed catalog diode'
        clickButtonAndWaitForPredicate $socket ([ref]$nextId) 'Board Power: OFF' "document.body.innerText.includes('Board Power: ON')" $deadline ([ref]$failures) 'diode power on before customer retest'
        clickButtonAndWaitForPredicate $socket ([ref]$nextId) 'Retest Customer' "document.body.innerText.includes('Repair verified. Indicator operating normally.')" $deadline ([ref]$failures) 'diode customer retest'
        $terminal = evaluateCdp $socket ([ref]$nextId) "(()=>{const b=document.body.innerText||'',buttons=[...document.querySelectorAll('button')];const power=buttons.find(x=>x.innerText.includes('Board Power:'));const retest=buttons.find(x=>x.innerText.trim()==='Retest Customer');return {completed:b.includes('Repair verified. Indicator operating normally.'),powerDisabled:!!power&&power.disabled,retestDisabled:!!retest&&retest.disabled};})()" ([ref]$failures)
        if (-not ($terminal.completed -and $terminal.powerDisabled -and $terminal.retestDisabled)) {
            throw "completed diode challenge did not enter the physical terminal state: $($terminal | ConvertTo-Json -Compress)"
        }
        Write-Host 'DIODE PLAYER repair verified; physical state is terminal'
        if ($failures.Count -gt 0) { throw ($failures -join '; ') }
        cleanupBrowser $browser $socket $profile
        $socket = $null
        $browser = $null
        Write-Host "PASS diode-normal-player seed=$playerSeed terminal=mutation-free"
        return $true
    } catch {
        Set-VerifierFailure $_ ("diode-normal-player seed=$playerSeed")
        if ($null -ne $socket) {
            try {
                $snapshot = evaluateCdp $socket ([ref]$nextId) "document.body.innerText" ([ref]$failures)
                $start = [Math]::Max(0, $snapshot.Length - 1200)
                Write-Host ("DIODE PLAYER UI SNAPSHOT: " + $snapshot.Substring($start).Replace("`n", " | "))
            } catch { }
        }
        return $false
    } finally {
        try { cleanupBrowser $browser $socket $profile } catch { Set-VerifierFailure $_ 'diode-normal-player cleanup' }
        $script:CdpRouteDeadline = [DateTime]::MinValue
    }
}

function verifyWrongRepairNormalPlayer([string]$url) {
    $profile = $null
    $browser = $null
    $socket = $null
    $session = $null
    try {
        $session = startVerifierBrowser 'wrong-repair-normal-player' $url
        $profile = $session.Profile
        $browser = $session.Browser
        $socket = $session.Socket
        $deadline = $session.Deadline
        $script:CdpRouteDeadline = $deadline
        $nextId = 1
        $failures = @()
        [void](invokeCdp $socket ([ref]$nextId) 'Runtime.enable' @{} ([ref]$failures))
        [void](invokeCdp $socket ([ref]$nextId) 'Page.enable' @{} ([ref]$failures))
        [void](navigateAndWaitForDocument $socket ([ref]$nextId) $url $deadline ([ref]$failures))
        waitForCdp $socket ([ref]$nextId) "document.body&&document.body.innerText.includes('Indicator does not light.')" $deadline ([ref]$failures) 'ready LED seed-3 wrong-repair challenge'
        waitForCdp $socket ([ref]$nextId) "!!window.__tsjPcbGeometry&&!!window.__tsjPcbGeometry.points" $deadline ([ref]$failures) 'procedural LED PCB geometry bridge'
        $initial = evaluateCdp $socket ([ref]$nextId) "({canvas:!!document.querySelector('canvas'),catalog:document.body.innerText.includes('Resistor Replacement Catalog'),empty:document.body.innerText.includes('No removed parts'),complaint:document.body.innerText.includes('Indicator does not light.')})" ([ref]$failures)
        if (-not ($initial.canvas -and $initial.catalog -and $initial.empty -and $initial.complaint)) {
            throw 'initial LED workbench or original complaint was missing'
        }
        if ($script:VerifierEvidenceDirectory) {
            captureBrowserScreenshot $socket ([ref]$nextId) (getVerifierEvidencePath 'initial-board.png') ([ref]$failures)
        }

        $r1 = getCanvasPoint $socket ([ref]$nextId) 'component:R1' ([ref]$failures)
        clickPoint $socket ([ref]$nextId) $r1 'left' ([ref]$failures)
        waitForCdp $socket ([ref]$nextId) "document.body.innerText.includes('R1')&&document.body.innerText.includes('Remove component')" $deadline ([ref]$failures) 'R1 component controls'
        if ($script:VerifierEvidenceDirectory) {
            captureBrowserScreenshot $socket ([ref]$nextId) (getVerifierEvidencePath 'r1-selected.png') ([ref]$failures)
        }
        clickButtonAndWaitForPredicate $socket ([ref]$nextId) 'Board Power: ON' "document.body.innerText.includes('Board Power: OFF')" $deadline ([ref]$failures) 'board power off before R1 removal'
        clickButtonAndWaitForPredicate $socket ([ref]$nextId) 'Remove component' "document.body.innerText.includes('R1_ORIGINAL - Removed resistor')" $deadline ([ref]$failures) 'faulted original R1 removal'
        selectOptionWithKeyboard $socket ([ref]$nextId) 0 '2200 Ohm +/-5%' ([ref]$failures)
        clickButton $socket ([ref]$nextId) 'Install new resistor' ([ref]$failures)
        waitForAnimationFrames $socket ([ref]$nextId) $deadline ([ref]$failures)
        $installed2200Point = getCanvasPoint $socket ([ref]$nextId) 'component:R1' ([ref]$failures)
        if ($null -eq $installed2200Point) { throw 'component:R1 geometry target unavailable after 2200 Ohm installation' }
        clickPoint $socket ([ref]$nextId) $installed2200Point 'left' ([ref]$failures)
        waitForCdp $socket ([ref]$nextId) "document.body.innerText.includes('Value: 2200 Ohm +/-5%')" $deadline ([ref]$failures) '2.2 kOhm physical resistor installation'
        waitForAnimationFrames $socket ([ref]$nextId) $deadline ([ref]$failures)
        $wrongBands = getResistorBandColors $socket ([ref]$nextId) 'pad:R1.1' 'pad:R1.2' ([ref]$failures)
        $wrongBandNames = if ($null -eq $wrongBands) { @() } else { @($wrongBands.PSObject.Properties | ForEach-Object { $_.Name }) }
        foreach ($band in @('red', 'gold')) {
            if (-not ($wrongBandNames -contains $band)) {
                throw "2.2 kOhm installed resistor markings were not visible: $($wrongBands | ConvertTo-Json -Compress)"
            }
        }
        clickButtonAndWaitForPredicate $socket ([ref]$nextId) 'Board Power: OFF' "document.body.innerText.includes('Board Power: ON')" $deadline ([ref]$failures) 'power on wrong replacement'
        $wrongUi = evaluateCdp $socket ([ref]$nextId) "(()=>{const body=document.body.innerText||'',lower=body.toLowerCase();return {complaint:body.includes('Indicator does not light.'),power:body.includes('Board Power: ON'),value:body.includes('Value: 2200 Ohm +/-5%'),completed:body.includes('Repair verified. Indicator operating normally.'),diagnostic:lower.includes('wrong resistor')||lower.includes('incorrect resistor')||lower.includes('diagnos')};})()" ([ref]$failures)
        if (-not ($wrongUi.complaint -and $wrongUi.power -and $wrongUi.value) -or $wrongUi.completed -or $wrongUi.diagnostic) {
            throw "wrong powered repair UI did not preserve complaint without a diagnostic: $($wrongUi | ConvertTo-Json -Compress)"
        }
        if ($script:VerifierEvidenceDirectory) {
            captureBrowserScreenshot $socket ([ref]$nextId) (getVerifierEvidencePath 'wrong-repair-powered.png') ([ref]$failures)
        }

        clickButtonAndWaitForPredicate $socket ([ref]$nextId) 'Board Power: ON' "document.body.innerText.includes('Board Power: OFF')" $deadline ([ref]$failures) 'board power off before wrong replacement removal'
        clickButtonAndWaitForPredicate $socket ([ref]$nextId) 'Remove component' "document.body.innerText.includes('R1_CATALOG_PART_0 - 2200 Ohm +/-5%')" $deadline ([ref]$failures) '2.2 kOhm replacement removal'
        selectOptionWithKeyboard $socket ([ref]$nextId) 0 '1000 Ohm +/-5%' ([ref]$failures)
        clickButton $socket ([ref]$nextId) 'Install new resistor' ([ref]$failures)
        waitForAnimationFrames $socket ([ref]$nextId) $deadline ([ref]$failures)
        $installed1000Point = getCanvasPoint $socket ([ref]$nextId) 'component:R1' ([ref]$failures)
        if ($null -eq $installed1000Point) { throw 'component:R1 geometry target unavailable after 1000 Ohm installation' }
        clickPoint $socket ([ref]$nextId) $installed1000Point 'left' ([ref]$failures)
        waitForCdp $socket ([ref]$nextId) "document.body.innerText.includes('Value: 1000 Ohm +/-5%')" $deadline ([ref]$failures) '1 kOhm physical resistor installation'
        waitForAnimationFrames $socket ([ref]$nextId) $deadline ([ref]$failures)
        $correctBands = getResistorBandColors $socket ([ref]$nextId) 'pad:R1.1' 'pad:R1.2' ([ref]$failures)
        $correctBandNames = if ($null -eq $correctBands) { @() } else { @($correctBands.PSObject.Properties | ForEach-Object { $_.Name }) }
        foreach ($band in @('brown', 'black', 'red', 'gold')) {
            if (-not ($correctBandNames -contains $band)) {
                throw "1 kOhm installed resistor markings were not visible: $($correctBands | ConvertTo-Json -Compress)"
            }
        }
        clickButtonAndWaitForPredicate $socket ([ref]$nextId) 'Board Power: OFF' "document.body.innerText.includes('Board Power: ON')" $deadline ([ref]$failures) 'power on correct replacement'
        clickButtonAndWaitForPredicate $socket ([ref]$nextId) 'Retest Customer' "document.body.innerText.includes('Repair verified. Indicator operating normally.')" $deadline ([ref]$failures) 'generic customer retest completion text'
        if ($script:VerifierEvidenceDirectory) {
            captureBrowserScreenshot $socket ([ref]$nextId) (getVerifierEvidencePath 'completed.png') ([ref]$failures)
        }
        if ($failures.Count -gt 0) { throw ($failures -join '; ') }
        cleanupBrowser $browser $socket $profile
        $socket = $null
        $browser = $null
        Write-Host 'PASS wrong-repair-normal-player seed=3 2200-ohm-degraded 1000-ohm-restored'
        return $true
    } catch {
        Set-VerifierFailure $_ 'wrong-repair-normal-player seed=3'
        if ($null -ne $socket) {
            try {
                $snapshot = evaluateCdp $socket ([ref]$nextId) "document.body.innerText" ([ref]$failures)
                $start = [Math]::Max(0, $snapshot.Length - 1800)
                Write-Host ("WRONG REPAIR PLAYER UI SNAPSHOT: " + $snapshot.Substring($start).Replace("`n", ' | '))
            } catch { }
        }
        return $false
    } finally {
        try { cleanupBrowser $browser $socket $profile } catch { Set-VerifierFailure $_ 'wrong-repair-normal-player cleanup' }
        $script:CdpRouteDeadline = [DateTime]::MinValue
    }
}

function verifyStressDamageNormalPlayer([string]$url) {
    $profile = $null
    $browser = $null
    $socket = $null
    $session = $null
    try {
        $session = startVerifierBrowser 'stress-damage-normal-player' $url
        $profile = $session.Profile
        $browser = $session.Browser
        $socket = $session.Socket
        $deadline = $session.Deadline
        $script:CdpRouteDeadline = $deadline
        $nextId = 1
        $failures = @()
        [void](invokeCdp $socket ([ref]$nextId) 'Runtime.enable' @{} ([ref]$failures))
        [void](invokeCdp $socket ([ref]$nextId) 'Page.enable' @{} ([ref]$failures))
        [void](navigateAndWaitForDocument $socket ([ref]$nextId) $url $deadline ([ref]$failures))
        waitForCdp $socket ([ref]$nextId) "document.body&&document.body.innerText.includes('Indicator does not light.')" $deadline ([ref]$failures) 'ready LED stress challenge'
        waitForCdp $socket ([ref]$nextId) "!!window.__tsjPcbGeometry&&!!window.__tsjPcbGeometry.points" $deadline ([ref]$failures) 'stress PCB geometry bridge'
        $evidence = $script:VerifierEvidenceDirectory
        [IO.Directory]::CreateDirectory($evidence) | Out-Null
        captureBrowserScreenshot $socket ([ref]$nextId) (getVerifierEvidencePath 'initial-board.png') ([ref]$failures)

        $r1 = getCanvasPoint $socket ([ref]$nextId) 'component:R1' ([ref]$failures)
        clickPoint $socket ([ref]$nextId) $r1 'left' ([ref]$failures)
        waitForCdp $socket ([ref]$nextId) "document.body.innerText.includes('R1')&&document.body.innerText.includes('Remove component')" $deadline ([ref]$failures) 'stress R1 component controls'
        clickButtonAndWaitForPredicate $socket ([ref]$nextId) 'Board Power: ON' "document.body.innerText.includes('Board Power: OFF')" $deadline ([ref]$failures) 'stress initial board power-off'
        clickButtonAndWaitForPredicate $socket ([ref]$nextId) 'Remove component' "document.body.innerText.includes('R1_ORIGINAL - Removed resistor')" $deadline ([ref]$failures) 'stress original R1 removal'
        selectOptionWithKeyboard $socket ([ref]$nextId) 0 '220 Ohm +/-5%' ([ref]$failures)
        clickButtonAndWaitForPredicate $socket ([ref]$nextId) 'Install new resistor' "document.body.innerText.includes('Value: 220 Ohm +/-5%')" $deadline ([ref]$failures) 'stress severe replacement installation'
        clickButtonAndWaitForPredicate $socket ([ref]$nextId) 'Board Power: OFF' "document.body.innerText.includes('Board Power: ON')" $deadline ([ref]$failures) 'stress severe replacement power-on'
        $initialUi = evaluateCdp $socket ([ref]$nextId) "(()=>{const b=(document.body.innerText||''),l=b.toLowerCase();return {complaint:b.includes('Indicator does not light.'),value:b.includes('Value: 220 Ohm +/-5%'),power:b.includes('Board Power: ON'),diagnostic:/watt|stress|damage|overheat/.test(l),complete:b.includes('Repair verified. Indicator operating normally.')}})()" ([ref]$failures)
        if (-not ($initialUi.complaint -and $initialUi.value -and $initialUi.power) -or $initialUi.diagnostic -or $initialUi.complete) {
            throw "severe-overload player UI leaked diagnostics or completed unexpectedly: $($initialUi | ConvertTo-Json -Compress)"
        }
        captureBrowserScreenshot $socket ([ref]$nextId) (getVerifierEvidencePath 'severe-overload-powered.png') ([ref]$failures)

        $advance = evaluateCdp $socket ([ref]$nextId) "(()=>{if(typeof window.__tsjAdvanceResistorServiceTime!=='function')return false;window.__tsjAdvanceResistorServiceTime(0.5);return true;})()" ([ref]$failures)
        if (-not $advance) { throw 'developer service-time bridge was unavailable' }
        Write-Host ("TASK34 PLAYER AFTER POWERED ADVANCE: " + (evaluateCdp $socket ([ref]$nextId) "typeof window.__tsjGetResistorStressState==='function'?window.__tsjGetResistorStressState():''" ([ref]$failures)))
        clickButtonAndWaitForPredicate $socket ([ref]$nextId) 'Board Power: ON' "document.body.innerText.includes('Board Power: OFF')" $deadline ([ref]$failures) 'stress power-off pause'
        $paused = evaluateCdp $socket ([ref]$nextId) "(()=>{window.__tsjAdvanceResistorServiceTime(5);return true;})()" ([ref]$failures)
        if (-not $paused) { throw 'powered-off service-time pause did not run' }
        clickButtonAndWaitForPredicate $socket ([ref]$nextId) 'Board Power: OFF' "document.body.innerText.includes('Board Power: ON')" $deadline ([ref]$failures) 'stress resume power-on'
        [void](evaluateCdp $socket ([ref]$nextId) "window.__tsjAdvanceResistorServiceTime(3);true" ([ref]$failures))
        Write-Host ("TASK34 PLAYER AFTER RESUME ADVANCE: " + (evaluateCdp $socket ([ref]$nextId) "typeof window.__tsjGetResistorStressState==='function'?window.__tsjGetResistorStressState():''" ([ref]$failures)))
        waitForAnimationFrames $socket ([ref]$nextId) $deadline ([ref]$failures)
        $failureState = evaluateCdp $socket ([ref]$nextId) "typeof window.__tsjGetResistorStressState==='function'?window.__tsjGetResistorStressState():''" ([ref]$failures)
        Write-Host "TASK34 PLAYER SECONDARY STATE: $failureState"
        if ([string]$failureState -notmatch 'failed=true' -or [string]$failureState -notmatch 'open=true') {
            throw "developer service-time advance did not reach the owned secondary-open state: $failureState"
        }
        $failureUi = evaluateCdp $socket ([ref]$nextId) "(()=>{const b=(document.body.innerText||''),l=b.toLowerCase();return {complaint:b.includes('Indicator does not light.'),value:b.includes('Value: 220 Ohm +/-5%'),power:b.includes('Board Power: ON'),diagnostic:/watt|stress|damage|overheat/.test(l),complete:b.includes('Repair verified. Indicator operating normally.')}})()" ([ref]$failures)
        if (-not ($failureUi.complaint -and $failureUi.value -and $failureUi.power) -or $failureUi.diagnostic -or $failureUi.complete) {
            throw "secondary-failure player UI leaked diagnostics or completed unexpectedly: $($failureUi | ConvertTo-Json -Compress)"
        }
        if ($failures.Count -gt 0) {
            throw ("secondary-failure normal-player path emitted console/page exceptions: " +
                ($failures -join '; '))
        }
        captureBrowserScreenshot $socket ([ref]$nextId) (getVerifierEvidencePath 'secondary-failure.png') ([ref]$failures)

        clickButtonAndWaitForPredicate $socket ([ref]$nextId) 'Board Power: ON' "document.body.innerText.includes('Board Power: OFF')" $deadline ([ref]$failures) 'stress power-off before repair'
        clickButtonAndWaitForPredicate $socket ([ref]$nextId) 'Remove component' "document.body.innerText.includes('R1_CATALOG_PART_0 - 220 Ohm +/-5%')" $deadline ([ref]$failures) 'stress severe replacement removal'
        selectOptionWithKeyboard $socket ([ref]$nextId) 0 '1000 Ohm +/-5%' ([ref]$failures)
        clickButtonAndWaitForPredicate $socket ([ref]$nextId) 'Install new resistor' "document.body.innerText.includes('Value: 1000 Ohm +/-5%')" $deadline ([ref]$failures) 'stress correct replacement installation'
        clickButtonAndWaitForPredicate $socket ([ref]$nextId) 'Board Power: OFF' "document.body.innerText.includes('Board Power: ON')" $deadline ([ref]$failures) 'stress correct replacement power-on'
        clickButtonAndWaitForPredicate $socket ([ref]$nextId) 'Retest Customer' "document.body.innerText.includes('Repair verified. Indicator operating normally.')" $deadline ([ref]$failures) 'stress natural solver-backed customer retest'
        captureBrowserScreenshot $socket ([ref]$nextId) (Join-Path $evidence 'correct-restored.png') ([ref]$failures)
        if ($failures.Count -gt 0) { throw ($failures -join '; ') }
        cleanupBrowser $browser $socket $profile
        $socket = $null
        $browser = $null
        Write-Host 'PASS stress-damage-normal-player seed=3 severe-open natural-behavior no-diagnostic-ui no-console-or-page-exceptions'
        return $true
    } catch {
        Set-VerifierFailure $_ 'stress-damage-normal-player seed=3'
        if ($null -ne $socket) {
            try {
                $snapshot = evaluateCdp $socket ([ref]$nextId) "document.body.innerText" ([ref]$failures)
                $start = [Math]::Max(0, $snapshot.Length - 1800)
                Write-Host ("STRESS PLAYER UI SNAPSHOT: " + $snapshot.Substring($start).Replace("`n", ' | '))
            } catch { }
        }
        return $false
    } finally {
        try { cleanupBrowser $browser $socket $profile } catch { Set-VerifierFailure $_ 'stress-damage-normal-player cleanup' }
        $script:CdpRouteDeadline = [DateTime]::MinValue
    }
}

function verifyNormalLedPlayer([string]$url, [int]$playerSeed = 3) {
    $profile = $null
    $browser = $null
    $socket = $null
    $session = $null
    try {
        $session = startVerifierBrowser 'led-normal-player' $url
        $profile = $session.Profile
        $browser = $session.Browser
        $socket = $session.Socket
        $deadline = $session.Deadline
        $script:CdpRouteDeadline = $deadline
        $nextId = 1
        $failures = @()
        [void](invokeCdp $socket ([ref]$nextId) 'Runtime.enable' @{} ([ref]$failures))
        [void](invokeCdp $socket ([ref]$nextId) 'Page.enable' @{} ([ref]$failures))
        [void](navigateAndWaitForDocument $socket ([ref]$nextId) $url $deadline ([ref]$failures))
        waitForCdp $socket ([ref]$nextId) "document.body&&document.body.innerText.includes('Indicator does not light.')" $deadline ([ref]$failures) 'ready LED challenge'
        waitForCdp $socket ([ref]$nextId) "!!window.__tsjPcbGeometry&&!!window.__tsjPcbGeometry.points" $deadline ([ref]$failures) 'procedural LED PCB geometry bridge'
        $initial = evaluateCdp $socket ([ref]$nextId) "({canvas:!!document.querySelector('canvas'),ledCatalog:document.body.innerText.includes('LED Replacement Catalog'),empty:document.body.innerText.includes('No removed parts')})" ([ref]$failures)
        if (-not ($initial.canvas -and $initial.ledCatalog -and $initial.empty)) {
            throw 'initial LED workbench, catalog, or empty tray was missing'
        }
        if ($script:VerifierEvidenceDirectory) {
            $initialEvidenceName = if ($PersistentPreviewEvidence) {
                'persistent-preview-fresh-load.png'
            } else { 'initial-board.png' }
            captureBrowserScreenshot $socket ([ref]$nextId) (getVerifierEvidencePath $initialEvidenceName) ([ref]$failures)
        }
        if ($PersistentPreviewEvidence) {
            cleanupBrowser $browser $socket $profile
            $socket = $null
            $browser = $null
            Write-Host 'PASS persistent-preview fresh normal-player load'
            return $true
        }
        $led = getCanvasPoint $socket ([ref]$nextId) 'component:LED1' ([ref]$failures)
        clickPoint $socket ([ref]$nextId) $led 'left' ([ref]$failures)
        waitForCdp $socket ([ref]$nextId) "document.body.innerText.includes('LED1')&&document.body.innerText.includes('Lead A: LED1.A')&&document.body.innerText.includes('Remove component')" $deadline ([ref]$failures) 'LED1 component controls'
        if ($script:VerifierEvidenceDirectory) {
            captureBrowserScreenshot $socket ([ref]$nextId) (getVerifierEvidencePath 'led-selected.png') ([ref]$failures)
        }
        clickButton $socket ([ref]$nextId) 'Board Power: ON' ([ref]$failures)
        clickButtonAndWaitForPredicate $socket ([ref]$nextId) 'Remove component' "document.body.innerText.includes('LED1_ORIGINAL - Generic red LED')&&!document.body.innerText.includes('No removed parts')" $deadline ([ref]$failures) 'loose original LED state'
        waitForCdp $socket ([ref]$nextId) "document.body.innerText.includes('LED1_ORIGINAL - Generic red LED')" $deadline ([ref]$failures) 'loose original LED'
        waitForCdp $socket ([ref]$nextId) "!!window.__tsjPcbGeometry.points['loose:LED1_ORIGINAL:0']&&!!window.__tsjPcbGeometry.points['loose:LED1_ORIGINAL:1']" $deadline ([ref]$failures) 'loose original LED geometry'
        if ($script:VerifierEvidenceDirectory) {
            captureBrowserScreenshot $socket ([ref]$nextId) (getVerifierEvidencePath 'led-removed-parts-tray.png') ([ref]$failures)
        }
        clickButton $socket ([ref]$nextId) 'DIODE' ([ref]$failures)
        $originalAnode = getCanvasPoint $socket ([ref]$nextId) 'loose:LED1_ORIGINAL:0' ([ref]$failures)
        $originalCathode = getCanvasPoint $socket ([ref]$nextId) 'loose:LED1_ORIGINAL:1' ([ref]$failures)
        clickPoint $socket ([ref]$nextId) $originalAnode 'left' ([ref]$failures)
        clickPoint $socket ([ref]$nextId) $originalCathode 'right' ([ref]$failures)
        if ($playerSeed -eq 4) {
            waitForCdp $socket ([ref]$nextId) "document.querySelector('.tsj-meter-display').innerText==='OL'" $deadline ([ref]$failures) 'loose original LED_OPEN forward OL'
            clickPoint $socket ([ref]$nextId) $originalCathode 'left' ([ref]$failures)
            waitForCdp $socket ([ref]$nextId) "document.querySelector('.tsj-meter-display').innerText!=='OL'" $deadline ([ref]$failures) 'LED_OPEN probe reversal transition'
            clickPoint $socket ([ref]$nextId) $originalAnode 'right' ([ref]$failures)
            waitForCdp $socket ([ref]$nextId) "document.querySelector('.tsj-meter-display').innerText==='OL'" $deadline ([ref]$failures) 'loose original LED_OPEN reverse OL'
        } else {
            waitForCdp $socket ([ref]$nextId) "(()=>{const t=document.querySelector('.tsj-meter-display').innerText;return t!=='OL'&&t!=='--- V';})()" $deadline ([ref]$failures) 'loose original LED forward drop'
            $forward = evaluateCdp $socket ([ref]$nextId) "document.querySelector('.tsj-meter-display').innerText" ([ref]$failures)
            clickPoint $socket ([ref]$nextId) $originalCathode 'left' ([ref]$failures)
            waitForCdp $socket ([ref]$nextId) "document.querySelector('.tsj-meter-display').innerText!=='$forward'" $deadline ([ref]$failures) 'LED probe reversal transition'
            clickPoint $socket ([ref]$nextId) $originalAnode 'right' ([ref]$failures)
            waitForCdp $socket ([ref]$nextId) "document.querySelector('.tsj-meter-display').innerText==='OL'" $deadline ([ref]$failures) 'loose original LED reverse OL'
        }
        clickButton $socket ([ref]$nextId) 'DIODE' ([ref]$failures)

        if ($playerSeed -eq 4) {
            clickButton $socket ([ref]$nextId) 'Install new LED' ([ref]$failures)
            clickButtonAndWaitForPredicate $socket ([ref]$nextId) 'Board Power: OFF' "document.body.innerText.includes('Board Power: ON')" $deadline ([ref]$failures) 'LED_OPEN replacement power on'
            clickButtonAndWaitForPredicate $socket ([ref]$nextId) 'Retest Customer' "document.body.innerText.includes('Repair verified. Indicator operating normally.')" $deadline ([ref]$failures) 'LED_OPEN customer retest'
            $terminal = evaluateCdp $socket ([ref]$nextId) "(()=>{const b=document.body.innerText||'',buttons=[...document.querySelectorAll('button')];const power=buttons.find(x=>x.innerText.includes('Board Power:'));const retest=buttons.find(x=>x.innerText.trim()==='Retest Customer');return {completed:b.includes('Repair verified. Indicator operating normally.'),powerDisabled:!!power&&power.disabled,retestDisabled:!!retest&&retest.disabled};})()" ([ref]$failures)
            if (-not ($terminal.completed -and $terminal.powerDisabled -and $terminal.retestDisabled)) {
                throw "completed LED_OPEN challenge did not enter the physical terminal state: $($terminal | ConvertTo-Json -Compress)"
            }
            waitForAnimationFrames $socket ([ref]$nextId) $deadline ([ref]$failures)
            if ($script:VerifierEvidenceDirectory) {
                captureBrowserScreenshot $socket ([ref]$nextId) (getVerifierEvidencePath 'repaired-board.png') ([ref]$failures)
            }
            Write-Host "LED PLAYER repair verified; physical state is terminal seed=$playerSeed"
            if ($failures.Count -gt 0) { throw ($failures -join '; ') }
            cleanupBrowser $browser $socket $profile
            $socket = $null
            $browser = $null
            Write-Host "PASS led-normal-player seed=$playerSeed terminal=mutation-free"
            return $true
        }

        clickButton $socket ([ref]$nextId) 'Install new LED' ([ref]$failures)
        clickButton $socket ([ref]$nextId) 'Board Power: OFF' ([ref]$failures)
        waitForAnimationFrames $socket ([ref]$nextId) $deadline ([ref]$failures)
        $stillFaulted = evaluateCdp $socket ([ref]$nextId) "!document.body.innerText.includes('Repair verified.')&&document.body.innerText.includes('Indicator does not light.')" ([ref]$failures)
        if (-not $stillFaulted) { throw 'healthy LED incorrectly bypassed the original R1 fault' }
        clickButton $socket ([ref]$nextId) 'Board Power: ON' ([ref]$failures)
        waitForAnimationFrames $socket ([ref]$nextId) $deadline ([ref]$failures)
        $led = getCanvasPoint $socket ([ref]$nextId) 'component:LED1' ([ref]$failures)
        clickPoint $socket ([ref]$nextId) $led 'left' ([ref]$failures)
        waitForCdp $socket ([ref]$nextId) "document.body.innerText.includes('Remove component')" $deadline ([ref]$failures) 'healthy LED component controls'
        clickButtonAndWaitForPredicate $socket ([ref]$nextId) 'Remove component' "document.body.innerText.includes('LED1_CATALOG_PART_0 - Generic red LED')&&!document.body.innerText.includes('No removed parts')" $deadline ([ref]$failures) 'healthy loose LED state'

        selectOptionWithKeyboard $socket ([ref]$nextId) 1 'Generic red LED (reversed)' ([ref]$failures)
        clickButton $socket ([ref]$nextId) 'Install new LED' ([ref]$failures)
        $r1 = getCanvasPoint $socket ([ref]$nextId) 'component:R1' ([ref]$failures)
        clickPoint $socket ([ref]$nextId) $r1 'left' ([ref]$failures)
        clickButtonAndWaitForPredicate $socket ([ref]$nextId) 'Remove component' "document.body.innerText.includes('R1_ORIGINAL - Removed resistor')&&!document.body.innerText.includes('No removed parts')" $deadline ([ref]$failures) 'loose original R1 state'
        selectOptionWithKeyboard $socket ([ref]$nextId) 0 '1000 Ohm +/-5%' ([ref]$failures)
        clickButton $socket ([ref]$nextId) 'Install new resistor' ([ref]$failures)
        clickButton $socket ([ref]$nextId) 'Board Power: OFF' ([ref]$failures)
        waitForAnimationFrames $socket ([ref]$nextId) $deadline ([ref]$failures)
        $reversedBlocked = evaluateCdp $socket ([ref]$nextId) "!document.body.innerText.includes('Repair verified.')&&document.body.innerText.includes('Indicator does not light.')" ([ref]$failures)
        if (-not $reversedBlocked) { throw 'reversed LED incorrectly completed the challenge' }
        Write-Host 'LED PLAYER reversed installation remained nonfunctional'

        clickButton $socket ([ref]$nextId) 'Board Power: ON' ([ref]$failures)
        $led = getCanvasPoint $socket ([ref]$nextId) 'component:LED1' ([ref]$failures)
        clickPoint $socket ([ref]$nextId) $led 'left' ([ref]$failures)
        waitForCdp $socket ([ref]$nextId) "document.body.innerText.includes('Remove component')" $deadline ([ref]$failures) 'reversed LED component controls'
        clickButtonAndWaitForPredicate $socket ([ref]$nextId) 'Remove component' "document.body.innerText.includes('LED1_CATALOG_PART_0 - Generic red LED')&&!document.body.innerText.includes('No removed parts')" $deadline ([ref]$failures) 'reversed LED loose state'
        waitForCdp $socket ([ref]$nextId) "document.body.innerText.includes('LED1_CATALOG_PART_0 - Generic red LED')" $deadline ([ref]$failures) 'healthy loose LED identity'
        waitForCdp $socket ([ref]$nextId) "!!window.__tsjPcbGeometry.points['loose:LED1_CATALOG_PART_0:0']&&!!window.__tsjPcbGeometry.points['loose:LED1_CATALOG_PART_0:1']" $deadline ([ref]$failures) 'healthy loose LED geometry'
        clickButton $socket ([ref]$nextId) 'LED1_CATALOG_PART_0 - Generic red LED' ([ref]$failures)
        clickButton $socket ([ref]$nextId) 'Install as LED1' ([ref]$failures)
        clickButtonAndWaitForPredicate $socket ([ref]$nextId) 'Board Power: OFF' "document.body.innerText.includes('Board Power: ON')" $deadline ([ref]$failures) 'LED power on before customer retest'
        clickButtonAndWaitForPredicate $socket ([ref]$nextId) 'Retest Customer' "document.body.innerText.includes('Repair verified. Indicator operating normally.')" $deadline ([ref]$failures) 'LED and R1 customer retest'
        $terminal = evaluateCdp $socket ([ref]$nextId) "(()=>{const b=document.body.innerText||'',buttons=[...document.querySelectorAll('button')];const power=buttons.find(x=>x.innerText.includes('Board Power:'));const retest=buttons.find(x=>x.innerText.trim()==='Retest Customer');return {completed:b.includes('Repair verified. Indicator operating normally.'),powerDisabled:!!power&&power.disabled,retestDisabled:!!retest&&retest.disabled};})()" ([ref]$failures)
        if (-not ($terminal.completed -and $terminal.powerDisabled -and $terminal.retestDisabled)) {
            throw "completed LED challenge did not enter the physical terminal state: $($terminal | ConvertTo-Json -Compress)"
        }
        waitForAnimationFrames $socket ([ref]$nextId) $deadline ([ref]$failures)
        if ($script:VerifierEvidenceDirectory) {
            captureBrowserScreenshot $socket ([ref]$nextId) (getVerifierEvidencePath 'repaired-board.png') ([ref]$failures)
        }
        Write-Host 'LED PLAYER repair verified'

        Write-Host 'LED PLAYER repair verified; physical state is terminal'
        if ($failures.Count -gt 0) { throw ($failures -join '; ') }
        cleanupBrowser $browser $socket $profile
        $socket = $null
        $browser = $null
        Write-Host "PASS led-normal-player seed=$playerSeed terminal=mutation-free"
        return $true
    } catch {
        Set-VerifierFailure $_ ("led-normal-player seed=$playerSeed")
        if ($null -ne $socket) {
            try {
                $snapshot = evaluateCdp $socket ([ref]$nextId) "document.body.innerText" ([ref]$failures)
                $start = [Math]::Max(0, $snapshot.Length - 1800)
                Write-Host ("LED PLAYER UI SNAPSHOT: " + $snapshot.Substring($start).Replace("`n", " | "))
            } catch { }
        }
        return $false
    } finally {
        try { cleanupBrowser $browser $socket $profile } catch { Set-VerifierFailure $_ 'led-normal-player cleanup' }
        $script:CdpRouteDeadline = [DateTime]::MinValue
    }
}

function verifyRcNormalPlayer([string]$url) {
    $profile = $null
    $browser = $null
    $socket = $null
    $session = $null
    try {
        $session = startVerifierBrowser 'rc-normal-player' $url
        $profile = $session.Profile
        $browser = $session.Browser
        $socket = $session.Socket
        $deadline = $session.Deadline
        $script:CdpRouteDeadline = $deadline
        $nextId = 1
        $failures = @()
        [void](invokeCdp $socket ([ref]$nextId) 'Runtime.enable' @{} ([ref]$failures))
        [void](invokeCdp $socket ([ref]$nextId) 'Page.enable' @{} ([ref]$failures))
        [void](navigateAndWaitForDocument $socket ([ref]$nextId) $url $deadline ([ref]$failures))
        waitForCdp $socket ([ref]$nextId) "document.body&&document.body.innerText.includes('controller responds immediately after power-up.')" $deadline ([ref]$failures) 'ready RC challenge'
        waitForCdp $socket ([ref]$nextId) "!!window.__tsjPcbGeometry&&!!window.__tsjPcbGeometry.points['component:C1']&&!!window.__tsjPcbGeometry.points['component:C2']" $deadline ([ref]$failures) 'RC provider-owned capacitor geometry'
        $ticket = evaluateCdp $socket ([ref]$nextId) "(()=>{const t=[...document.querySelectorAll('.tsj-component-title')].find(x=>x.textContent.trim()==='Service Ticket');return t&&t.parentElement?t.parentElement.innerText:'';})()" ([ref]$failures)
        if ($ticket -match '(?i)c1|capacitor|fault|open|short|1\s*uF') {
            throw "RC complaint disclosed implementation details: $ticket"
        }
        if ($script:VerifierEvidenceDirectory) {
            captureBrowserScreenshot $socket ([ref]$nextId) (getVerifierEvidencePath 'rc-initial.png') ([ref]$failures)
        }
        $c1 = getCanvasPoint $socket ([ref]$nextId) 'component:C1' ([ref]$failures)
        clickPoint $socket ([ref]$nextId) $c1 'left' ([ref]$failures)
        waitForCdp $socket ([ref]$nextId) "document.body.innerText.includes('Remove component')&&document.body.innerText.includes('C1')" $deadline ([ref]$failures) 'RC capacitor controls'
        clickButtonAndWaitForPredicate $socket ([ref]$nextId) 'Board Power: ON' "document.body.innerText.includes('Board Power: OFF')" $deadline ([ref]$failures) 'power off RC board'
        clickButtonAndWaitForPredicate $socket ([ref]$nextId) 'Remove component' "document.body.innerText.includes('C1_ORIGINAL - Electrolytic capacitor')&&!document.body.innerText.includes('No removed parts')" $deadline ([ref]$failures) 'remove fault-owning C1'
        waitForCdp $socket ([ref]$nextId) "!!window.__tsjPcbGeometry.points['loose:C1_ORIGINAL:0']&&!!window.__tsjPcbGeometry.points['loose:C1_ORIGINAL:1']" $deadline ([ref]$failures) 'loose capacitor geometry and probes'
        selectOptionWithKeyboard $socket ([ref]$nextId) 0 '33 uF 16 V' ([ref]$failures)
        clickButtonAndWaitForPredicate $socket ([ref]$nextId) 'Install new capacitor' "!document.body.innerText.includes('State: C1 slot empty')&&document.body.innerText.includes('C1_ORIGINAL - Electrolytic capacitor')" $deadline ([ref]$failures) 'install RC replacement'
        clickButtonAndWaitForPredicate $socket ([ref]$nextId) 'Board Power: OFF' "document.body.innerText.includes('Board Power: ON')" $deadline ([ref]$failures) 'power on before RC customer retest'
        clickButtonAndWaitForPredicate $socket ([ref]$nextId) 'Power-cycle and Retest Customer' "document.body.innerText.includes('Customer retest passed.')&&document.body.innerText.includes('Repair verified. The controller delay is operating normally.')" $deadline ([ref]$failures) 'solver-backed RC customer retest'
        $terminal = evaluateCdp $socket ([ref]$nextId) "(()=>{const b=document.body.innerText||'',buttons=[...document.querySelectorAll('button')];const power=buttons.find(x=>x.innerText.includes('Board Power:'));const retest=buttons.find(x=>x.innerText.trim()==='Power-cycle and Retest Customer');return {completed:b.includes('Repair verified. The controller delay is operating normally.'),powerDisabled:!!power&&power.disabled,retestDisabled:!!retest&&retest.disabled};})()" ([ref]$failures)
        if (-not ($terminal.completed -and $terminal.powerDisabled -and $terminal.retestDisabled)) {
            throw "completed RC challenge did not enter the physical terminal state: $($terminal | ConvertTo-Json -Compress)"
        }
        if ($script:VerifierEvidenceDirectory) {
            captureBrowserScreenshot $socket ([ref]$nextId) (getVerifierEvidencePath 'rc-repaired-terminal.png') ([ref]$failures)
        }
        if ($failures.Count -gt 0) { throw ($failures -join '; ') }
        cleanupBrowser $browser $socket $profile
        $socket = $null
        $browser = $null
        Write-Host "PASS rc-normal-player provider-geometry=visible repair=customer-retest-authoritative terminal=mutation-free"
        return $true
    } catch {
        Set-VerifierFailure $_ 'rc-normal-player'
        if ($null -ne $socket) {
            try {
                $snapshot = evaluateCdp $socket ([ref]$nextId) "document.body.innerText" ([ref]$failures)
                $start = [Math]::Max(0, $snapshot.Length - 1600)
                $meter = evaluateCdp $socket ([ref]$nextId) "(()=>{const e=document.querySelector('.tsj-meter-display');return e?e.innerText:'';})()" ([ref]$failures)
                Write-Host ("RC PLAYER UI SNAPSHOT: " + $snapshot.Substring($start).Replace("`n", " | ") + " meter=" + $meter)
            } catch { }
        }
        return $false
    } finally {
        try { cleanupBrowser $browser $socket $profile } catch { Set-VerifierFailure $_ 'rc-normal-player cleanup' }
        $script:CdpRouteDeadline = [DateTime]::MinValue
    }
}

function verifyTask39NormalPlayer([string]$url,
        [string[]]$commandButtons, [string]$retestButton) {
    $profile = $null
    $browser = $null
    $socket = $null
    $session = $null
    try {
        $session = startVerifierBrowser 'task39-normal-player' $url
        $profile = $session.Profile
        $browser = $session.Browser
        $socket = $session.Socket
        $deadline = $session.Deadline
        $script:CdpRouteDeadline = $deadline
        $nextId = 1
        $failures = @()
        [void](invokeCdp $socket ([ref]$nextId) 'Runtime.enable' @{} ([ref]$failures))
        [void](invokeCdp $socket ([ref]$nextId) 'Page.enable' @{} ([ref]$failures))
        [void](navigateAndWaitForDocument $socket ([ref]$nextId) $url $deadline ([ref]$failures))
        waitForCdp $socket ([ref]$nextId) "document.body&&document.body.innerText.includes('Service Ticket')&&document.body.innerText.includes('Customer retest:')" $deadline ([ref]$failures) 'Task 39 visible customer operation profile'
        $initial = evaluateCdp $socket ([ref]$nextId) "(()=>{const b=document.body.innerText||'',l=b.toLowerCase();return {hidden:/fault|answer|random seed|solver node|generatedfaulttype/.test(l),canvas:!!document.querySelector('canvas'),profile:b.includes('Customer retest:'),retest:[...document.querySelectorAll('button')].some(x=>x.innerText.trim()==='$retestButton')};})()" ([ref]$failures)
        if ($initial.hidden -or -not $initial.canvas -or -not $initial.profile -or -not $initial.retest) {
            throw "Task 39 normal-player privacy/control boundary failed: $($initial | ConvertTo-Json -Compress)"
        }
        if ($script:VerifierEvidenceDirectory) {
            captureBrowserScreenshot $socket ([ref]$nextId) (getVerifierEvidencePath 'task39-player-initial.png') ([ref]$failures)
        }
        foreach ($command in $commandButtons) {
            clickButton $socket ([ref]$nextId) $command ([ref]$failures)
            waitForAnimationFrames $socket ([ref]$nextId) $deadline ([ref]$failures)
        }
        if ($script:VerifierEvidenceDirectory) {
            captureBrowserScreenshot $socket ([ref]$nextId) (getVerifierEvidencePath 'task39-player-after-inputs.png') ([ref]$failures)
        }
        clickButton $socket ([ref]$nextId) $retestButton ([ref]$failures)
        waitForCdp $socket ([ref]$nextId) "document.body.innerText.includes('Customer retest did not pass.')" $deadline ([ref]$failures) 'Task 39 visible customer retest result'
        if ($script:VerifierEvidenceDirectory) {
            captureBrowserScreenshot $socket ([ref]$nextId) (getVerifierEvidencePath 'task39-player-retest-result.png') ([ref]$failures)
        }
        if ($failures.Count -gt 0) { throw ($failures -join '; ') }
        cleanupBrowser $browser $socket $profile
        $socket = $null
        $browser = $null
        Write-Host "PASS task39-normal-player retest=$retestButton"
        return $true
    } catch {
        Set-VerifierFailure $_ 'task39-normal-player'
        return $false
    } finally {
        try { cleanupBrowser $browser $socket $profile } catch { Set-VerifierFailure $_ 'task39-normal-player cleanup' }
        $script:CdpRouteDeadline = [DateTime]::MinValue
    }
}

function getIntegratedPowerShellExecutable() {
    $commandName = if ($PSVersionTable.PSEdition -eq 'Core') { 'pwsh.exe' } else {
        'powershell.exe'
    }
    $command = Get-Command $commandName -ErrorAction Stop
    $path = [string]$command.Path
    if (-not $path) { $path = [string]$command.Source }
    if (-not $path) { throw "Could not resolve integrated child host '$commandName'" }
    return $path
}

function Resolve-VerifierIntegratedChildExitCode([bool]$TerminationProven,
        $RawExitCode) {
    if (-not $TerminationProven) {
        Throw-VerifierInfrastructure 'Integrated child termination was not proven before exit classification.'
    }
    if ($null -eq $RawExitCode -or
            [String]::IsNullOrWhiteSpace([string]$RawExitCode)) {
        Throw-VerifierInfrastructure 'Integrated child returned no verifiable numeric exit code.'
    }
    $numericExitCode = 0
    $rawText = [string]$RawExitCode
    if (-not [int]::TryParse($rawText,
            [Globalization.NumberStyles]::Integer,
            [Globalization.CultureInfo]::InvariantCulture,
            [ref]$numericExitCode)) {
        Throw-VerifierInfrastructure "Integrated child returned an unparseable exit code: '$rawText'."
    }
    if ($numericExitCode -eq 0) {
        return (Resolve-VerifierChildExitCode $true 0)
    }
    return (Resolve-VerifierChildExitCode $false $numericExitCode)
}

function Start-VerifierIntegratedChildProcess([string]$FilePath,
        [string[]]$Arguments) {
    $process = New-Object Diagnostics.Process
    try {
        $startInfo = New-Object Diagnostics.ProcessStartInfo
        $startInfo.FileName = $FilePath
        $startInfo.Arguments = ConvertTo-VerifierArgumentString $Arguments
        $startInfo.UseShellExecute = $false
        $startInfo.CreateNoWindow = $true
        $startInfo.RedirectStandardOutput = $true
        $startInfo.RedirectStandardError = $true
        $process.StartInfo = $startInfo
        if (-not $process.Start()) {
            Throw-VerifierInfrastructure "Integrated child '$FilePath' did not start."
        }
        return $process
    } catch {
        try { $process.Dispose() } catch { }
        if (Test-VerifierInfrastructureError $_) { throw }
        Throw-VerifierInfrastructure ('Could not start integrated child process: ' +
            (Get-VerifierErrorMessage $_))
    }
}

function Stop-VerifierIntegratedChildExact($Process) {
    if ($null -eq $Process) { return }
    try {
        $Process.Refresh()
        if (-not [bool]$Process.HasExited) {
            $current = Get-Process -Id ([int]$Process.Id) -ErrorAction Stop
            $expectedStart = [long]$Process.StartTime.ToUniversalTime().Ticks
            $actualStart = [long]$current.StartTime.ToUniversalTime().Ticks
            if ($expectedStart -ne $actualStart) {
                Throw-VerifierInfrastructure "Refusing to stop integrated child PID $($Process.Id): start identity changed."
            }
            $currentWmiRecord = Get-VerifierCurrentProcessRecordById ([int]$Process.Id)
            if ($null -eq $currentWmiRecord) {
                Throw-VerifierInfrastructure "Integrated child PID $($Process.Id) disappeared before its termination identity was proven."
            }
            [void](Stop-VerifierVerifiedProcessExactly $current $expectedStart 5000 $currentWmiRecord)
        }
        # Do not dispose a redirected child handle until its termination has
        # been synchronized and the independent current-process query proves
        # that the PID is gone.  This is also the proof used by timeout
        # cleanup; a wrapper exit alone does not prove that owned resources
        # cannot still be running.
        if (-not $Process.WaitForExit(5000)) {
            Throw-VerifierInfrastructure "Integrated child PID $($Process.Id) did not complete WaitForExit within the cleanup bound."
        }
        $Process.Refresh()
        if (-not [bool]$Process.HasExited) {
            Throw-VerifierInfrastructure "Integrated child PID $($Process.Id) exit state was not proven."
        }
        $currentAfterStop = Get-VerifierCurrentProcessRecordById ([int]$Process.Id)
        if ($null -ne $currentAfterStop) {
            Throw-VerifierInfrastructure "Integrated child PID $($Process.Id) remained after exact cleanup."
        }
    } catch {
        if (Test-VerifierInfrastructureError $_) { throw }
        Throw-VerifierInfrastructure ('Could not stop exact integrated child PID ' +
            [string]$Process.Id + ': ' + (Get-VerifierErrorMessage $_))
    }
}

function Assert-IntegratedChildOutputContract([string]$label, [int]$expectedExit,
        [int]$childExit, [string[]]$childOutput) {
    $printedFailures = @()
    if ($expectedExit -eq 0 -and $childExit -eq 0) {
        $printedFailures = @($childOutput | Where-Object {
            ([string]$_) -match '(?i)(^|\s)FAIL(?::|\s)'
        })
        if ($printedFailures.Count -gt 0) {
            Throw-VerifierExit 1 "FAIL integrated child $label - child printed FAIL but returned 0"
        }
    }
    if ($expectedExit -eq 2 -and $childExit -ne 2) {
        Throw-VerifierInfrastructure ("integrated child $label expected infrastructure exit 2 but returned $childExit")
    }
    $contract = Test-VerifierChildContract $expectedExit $childExit `
        ($printedFailures.Count -gt 0) ($expectedExit -eq 0)
    if (-not $contract.Pass) {
        Write-Host "FAIL integrated child $label - expected-exit=$expectedExit actual-exit=$childExit"
        # Preserve the child's application (1) versus infrastructure (2)
        # contract. A zero exit here is never accepted for an expected failure.
        Throw-VerifierExit $contract.ExitCode "integrated child contract failed: $($contract.Reason)"
    }
}

function New-VerifierIntegratedChildLedger([string]$Label, [int]$ExpectedExit) {
    try {
        $ledgerRoot = if ($null -ne $script:VerifierContext) {
            Join-Path $script:VerifierContext.RunRoot `
                ('integrated-child-' + [Guid]::NewGuid().ToString('N'))
        } else {
            $parentRoot = Join-Path ([IO.Path]::GetTempPath()) `
                ('TroubleshootJS\verify\integrated-parent-' + [Guid]::NewGuid().ToString('N'))
            $parentRoot
        }
        New-Item -ItemType Directory -Path $ledgerRoot -Force -ErrorAction Stop | Out-Null
        $ledgerRoot = Get-VerifierFullPath $ledgerRoot
        $ledgerParent = Get-VerifierFullPath (Split-Path -Parent $ledgerRoot)
        Assert-VerifierNoReparseAncestors $ledgerParent
        if (-not (Test-VerifierPhysicalChildPath $ledgerParent $ledgerRoot)) {
            Throw-VerifierInfrastructure 'Integrated child ledger root escaped its physical parent namespace.'
        }
        $ledgerPath = Get-VerifierFullPath (Join-Path $ledgerRoot 'child-ledger.json')
        Assert-VerifierNoReparseAncestors $ledgerRoot
        if (-not (Test-VerifierPhysicalChildPath $ledgerRoot $ledgerPath)) {
            Throw-VerifierInfrastructure 'Integrated child ledger path escaped its physical ledger namespace.'
        }
        $record = [ordered]@{
            protocol = 'troubleshootjs-integrated-child-ledger-v1'
            state = 'parent-created'
            label = $Label
            expectedExit = $ExpectedExit
            parentNamespaceRoot = $ledgerRoot
            createdUtc = [DateTime]::UtcNow.ToString('o', [Globalization.CultureInfo]::InvariantCulture)
        }
        [IO.File]::WriteAllText($ledgerPath, ($record | ConvertTo-Json -Depth 8),
            [Text.UTF8Encoding]::new($false))
        return $ledgerPath
    } catch {
        if (Test-VerifierInfrastructureError $_) { throw }
        Throw-VerifierInfrastructure ('Could not create the parent-visible integrated-child ledger: ' +
            (Get-VerifierErrorMessage $_))
    }
}

function Get-VerifierLedgerProperty($Object, [string]$Name, $Default = $null) {
    if ($null -eq $Object) { return $Default }
    if ($Object -is [System.Collections.IDictionary]) {
        if (-not $Object.Contains($Name)) { return $Default }
        return $Object[$Name]
    }
    if ($null -eq $Object.PSObject.Properties[$Name]) { return $Default }
    return $Object.PSObject.Properties[$Name].Value
}

function Get-VerifierLedgerPath([string]$Path, [string]$Label, [string]$Root,
        [switch]$AllowRoot) {
    if ([String]::IsNullOrWhiteSpace($Path)) {
        Throw-VerifierInfrastructure "Integrated child ledger omitted its $Label path."
    }
    $canonical = Get-VerifierCanonicalWindowsPath $Path
    if ([String]::IsNullOrWhiteSpace($canonical)) {
        Throw-VerifierInfrastructure "Integrated child ledger had an invalid $Label path."
    }
    # Ledger paths are ownership inputs, not merely lexical strings. Recheck
    # the physical namespace so a junction/reparse replacement cannot make a
    # foreign claim, profile, log, or evidence path appear owned.
    if ($AllowRoot) {
        [void](Assert-VerifierPhysicalOwnedPath $Root $canonical -AllowRoot)
    } else {
        [void](Assert-VerifierPhysicalOwnedPath $Root $canonical)
    }
    return $canonical
}

function Test-VerifierStrictBooleanProperty($Object, [string]$Name, [bool]$Expected) {
    if ($null -eq $Object -or [String]::IsNullOrWhiteSpace($Name) -or
            $null -eq $Object.PSObject.Properties[$Name]) {
        return $false
    }
    $value = $Object.PSObject.Properties[$Name].Value
    if ($null -eq $value -or $value.GetType() -ne [bool]) { return $false }
    return ([bool]$value -eq $Expected)
}

function Test-VerifierStrictNonnegativeIntegerProperty($Object, [string]$Name) {
    if ($null -eq $Object -or [String]::IsNullOrWhiteSpace($Name) -or
            $null -eq $Object.PSObject.Properties[$Name]) {
        return $false
    }
    $value = $Object.PSObject.Properties[$Name].Value
    # Do not let a string, null, floating-point value, or an out-of-range
    # conversion become a forged zero through an implicit [int] cast.
    if ($null -eq $value -or
            -not ($value -is [byte] -or $value -is [sbyte] -or
                $value -is [int16] -or $value -is [uint16] -or
                $value -is [int32] -or $value -is [uint32] -or
                $value -is [int64] -or $value -is [uint64])) {
        return $false
    }
    try { return ([long]$value -ge 0) } catch { return $false }
}

function Test-VerifierIntegratedLeaseTerminal($Lease, [string]$ClaimPath) {
    if ($null -eq $Lease -or [String]::IsNullOrWhiteSpace($ClaimPath)) { return $false }
    if ([string](Get-VerifierLedgerProperty $Lease 'status' '') -ne 'released' -or
            [string](Get-VerifierLedgerProperty $Lease 'claimState' '') -ne 'released' -or
            [string](Get-VerifierLedgerProperty $Lease 'releaseState' '') -ne 'complete') {
        return $false
    }
    try {
        if (Test-Path -LiteralPath $ClaimPath -PathType Leaf -ErrorAction Stop) { return $false }
    } catch {
        Throw-VerifierInfrastructure ('Could not prove terminal lease claim absence: ' +
            (Get-VerifierErrorMessage $_))
    }
    if (-not (Test-VerifierStrictBooleanProperty $Lease 'mutexReleased' $true) -or
            -not (Test-VerifierStrictBooleanProperty $Lease 'listenerInspectionSuccess' $true) -or
            -not (Test-VerifierStrictBooleanProperty $Lease 'listenerInspectionKnown' $true) -or
            -not (Test-VerifierStrictBooleanProperty $Lease 'listenerHasListeners' $false) -or
            -not (Test-VerifierStrictBooleanProperty $Lease 'listenerAbsent' $true)) {
        return $false
    }
    if ($null -eq $Lease.PSObject.Properties['releaseJournalState'] -or
            [string]$Lease.PSObject.Properties['releaseJournalState'].Value -ne 'complete') {
        return $false
    }
    foreach ($numericProperty in @('boundProcessId', 'boundProcessStartTicks',
            'listenerProcessId', 'listenerProcessStartTicks')) {
        if (-not (Test-VerifierStrictNonnegativeIntegerProperty $Lease $numericProperty)) {
            return $false
        }
    }
    $boundPid = [int](Get-VerifierLedgerProperty $Lease 'boundProcessId' 0)
    $boundStart = [long](Get-VerifierLedgerProperty $Lease 'boundProcessStartTicks' 0)
    $listenerPid = [int](Get-VerifierLedgerProperty $Lease 'listenerProcessId' 0)
    $listenerStart = [long](Get-VerifierLedgerProperty $Lease 'listenerProcessStartTicks' 0)
    if (($boundPid -eq 0 -and $boundStart -ne 0) -or
            ($boundPid -gt 0 -and $boundStart -le 0) -or
            ($listenerPid -eq 0 -and $listenerStart -ne 0) -or
            ($listenerPid -gt 0 -and $listenerStart -le 0)) {
        return $false
    }
    $processProofProperty = $Lease.PSObject.Properties['processProofRequired']
    if ($null -eq $processProofProperty -or
            $null -eq $processProofProperty.Value -or
            $processProofProperty.Value.GetType() -ne [bool]) {
        return $false
    }
    $processProofRequired = [bool]$processProofProperty.Value
    $requiresProcessProof = [bool]$processProofRequired -or $boundPid -gt 0 -or $listenerPid -gt 0
    if (($boundPid -gt 0 -or $listenerPid -gt 0) -and -not $processProofRequired) {
        return $false
    }
    # A terminal bound lease must retain both sides of its proof: the process
    # that was launched and the exact listener observed on the leased port.
    # The listener may be an owned descendant, so these PIDs need not be
    # equal, but either missing identity makes the tombstone incomplete.
    if ($processProofRequired -and ($boundPid -le 0 -or $listenerPid -le 0)) {
        return $false
    }
    if ($requiresProcessProof -and
            (-not (Test-VerifierStrictBooleanProperty $Lease 'processTerminationProven' $true) -or
             -not (Test-VerifierStrictBooleanProperty $Lease 'processAbsent' $true))) {
        return $false
    }
    if (-not $requiresProcessProof -and
            (-not (Test-VerifierStrictBooleanProperty $Lease 'processTerminationProven' $false) -or
             -not (Test-VerifierStrictBooleanProperty $Lease 'processAbsent' $false))) {
        return $false
    }
    return $true
}

function Read-VerifierIntegratedChildLedger([string]$LedgerPath, [int]$ExpectedExit,
        [switch]$AllowIncomplete) {
    $expectedWorktree = if ($null -ne $script:VerifierContext) {
        [string]$script:VerifierContext.WorktreeRoot
    } else { Get-VerifierFullPath (Join-Path $PSScriptRoot '..') }
    $verifyRoot = Get-VerifierFullPath (Join-Path ([IO.Path]::GetTempPath()) `
        'TroubleshootJS\verify')
    $canonicalLedger = Get-VerifierLedgerPath $LedgerPath 'ledger' $verifyRoot
    if (-not (Test-Path -LiteralPath $canonicalLedger -PathType Leaf)) {
        Throw-VerifierInfrastructure "Integrated child ledger was not produced: $canonicalLedger"
    }
    try { $ledger = Get-Content -LiteralPath $canonicalLedger -Raw | ConvertFrom-Json } catch {
        Throw-VerifierInfrastructure ('Integrated child ledger was not valid JSON: ' +
            (Get-VerifierErrorMessage $_))
    }
    $state = [string](Get-VerifierLedgerProperty $ledger 'state' '')
    if ([string](Get-VerifierLedgerProperty $ledger 'protocol' '') -ne 'troubleshootjs-integrated-child-ledger-v1' -or
            $state -notin @('started', 'resource-started', 'incomplete', 'cleanup-failed', 'completed')) {
        Throw-VerifierInfrastructure 'Integrated child ledger had an invalid protocol or lifecycle state.'
    }
    $incompleteState = ($state -ne 'completed')
    if (-not $AllowIncomplete -and $incompleteState) {
        Throw-VerifierInfrastructure "Integrated child ledger did not prove cleanup for expected exit $ExpectedExit."
    }
    if ($AllowIncomplete -and -not $incompleteState) { $AllowIncomplete = $false }

    $runId = [string](Get-VerifierLedgerProperty $ledger 'runId' '')
    $repositoryIdentity = [string](Get-VerifierLedgerProperty $ledger 'repositoryIdentity' '')
    $worktreeRoot = Get-VerifierCanonicalWindowsPath `
        ([string](Get-VerifierLedgerProperty $ledger 'worktreeRoot' ''))
    if ([String]::IsNullOrWhiteSpace($worktreeRoot)) {
        Throw-VerifierInfrastructure 'Integrated child ledger had an invalid worktree path.'
    }
    if (-not (Test-VerifierCanonicalWindowsPathValue $worktreeRoot $expectedWorktree) -or
            $repositoryIdentity -ne (Get-VerifierRepositoryIdentity $expectedWorktree) -or
            [String]::IsNullOrWhiteSpace($runId)) {
        Throw-VerifierInfrastructure 'Integrated child ledger worktree/repository/run identity did not match the parent verifier.'
    }
    $namespaceRoot = Get-VerifierLedgerPath `
        ([string](Get-VerifierLedgerProperty $ledger 'parentNamespaceRoot' '')) `
        'parent namespace' $verifyRoot
    $ledgerParent = Get-VerifierFullPath (Split-Path -Parent $canonicalLedger)
    if (-not (Test-VerifierCanonicalWindowsPathValue $namespaceRoot $ledgerParent)) {
        Throw-VerifierInfrastructure 'Integrated child ledger was not stored by its recorded parent namespace.'
    }
    $runRoot = Get-VerifierLedgerPath ([string](Get-VerifierLedgerProperty $ledger 'runRoot' '')) `
        'run root' $namespaceRoot
    if (-not (Test-Path -LiteralPath $runRoot -PathType Container)) {
        Throw-VerifierInfrastructure 'Integrated child ledger did not retain its canonical run root.'
    }
    # Ledger paths are later used for recursive resource and evidence scans.
    # Prove the complete retained run tree has no reparse point before any of
    # those scans can follow a path supplied by the child.
    Assert-VerifierNoReparseTree $runRoot
    $manifestPath = Get-VerifierLedgerPath `
        ([string](Get-VerifierLedgerProperty $ledger 'manifestPath' '')) 'manifest' $runRoot
    $expectedManifest = Get-VerifierFullPath (Join-Path $runRoot 'manifest.json')
    $evidenceDirectory = Get-VerifierLedgerPath `
        ([string](Get-VerifierLedgerProperty $ledger 'evidenceDirectory' '')) 'evidence' $runRoot
    if (-not (Test-VerifierCanonicalWindowsPathValue $manifestPath $expectedManifest) -or
            -not (Test-Path -LiteralPath $manifestPath -PathType Leaf) -or
            -not (Test-Path -LiteralPath $evidenceDirectory -PathType Container)) {
        Throw-VerifierInfrastructure 'Integrated child ledger did not retain canonical manifest/evidence resources.'
    }
    try { $manifest = Get-Content -LiteralPath $manifestPath -Raw | ConvertFrom-Json } catch {
        Throw-VerifierInfrastructure ('Integrated child manifest was not valid JSON: ' +
            (Get-VerifierErrorMessage $_))
    }
    $manifestRunRoot = Get-VerifierLedgerPath ([string](Get-VerifierLedgerProperty $manifest 'runRoot' '')) `
        'manifest run root' $namespaceRoot
    $manifestEvidence = Get-VerifierLedgerPath `
        ([string](Get-VerifierLedgerProperty $manifest 'evidenceDirectory' '')) 'manifest evidence' $runRoot
    if ([string](Get-VerifierLedgerProperty $manifest 'protocol' '') -ne 'troubleshootjs-verifier-run-v1' -or
            [string](Get-VerifierLedgerProperty $manifest 'runId' '') -ne $runId -or
            [string](Get-VerifierLedgerProperty $manifest 'repositoryIdentity' '') -ne $repositoryIdentity -or
            -not (Test-VerifierCanonicalWindowsPathValue ([string](Get-VerifierLedgerProperty $manifest 'worktreeRoot' '')) $worktreeRoot) -or
            -not (Test-VerifierCanonicalWindowsPathValue $manifestRunRoot $runRoot) -or
            -not (Test-VerifierCanonicalWindowsPathValue $manifestEvidence $evidenceDirectory) -or
            -not (Test-VerifierCanonicalWindowsPathValue ([string](Get-VerifierLedgerProperty $manifest 'manifestPath' '')) $manifestPath) -or
            -not (Test-VerifierCanonicalWindowsPathValue ([string](Get-VerifierLedgerProperty $manifest 'runNamespaceRoot' '')) $namespaceRoot)) {
            Throw-VerifierInfrastructure 'Integrated child manifest identity or ownership paths were inconsistent.'
    }

    $manifestArtifactsValue = Get-VerifierLedgerProperty $manifest 'artifacts' $null
    $ledgerEvidenceValue = Get-VerifierLedgerProperty $ledger 'evidence' $null
    $manifestArtifacts = if ($null -eq $manifestArtifactsValue) { @() } else { @($manifestArtifactsValue) }
    $ledgerEvidence = if ($null -eq $ledgerEvidenceValue) { @() } else { @($ledgerEvidenceValue) }
    if (@($manifestArtifacts).Count -ne @($ledgerEvidence).Count) {
        Throw-VerifierInfrastructure 'Integrated child ledger and manifest evidence ledgers differed.'
    }
    $manifestEvidenceByPath = @{}
    foreach ($artifact in $manifestArtifacts) {
        $artifactPath = Get-VerifierLedgerPath ([string]$artifact) 'manifest evidence artifact' $evidenceDirectory
        if ($manifestEvidenceByPath.ContainsKey($artifactPath.ToLowerInvariant())) {
            Throw-VerifierInfrastructure 'Integrated child manifest evidence ledger contained a duplicate path.'
        }
        $manifestEvidenceByPath[$artifactPath.ToLowerInvariant()] = $artifactPath
        if (-not (Test-Path -LiteralPath $artifactPath -PathType Leaf)) {
            Throw-VerifierInfrastructure "Integrated child manifest evidence artifact was not retained: $artifactPath"
        }
    }
    $ledgerEvidenceByPath = @{}
    foreach ($artifact in $ledgerEvidence) {
        $artifactPath = Get-VerifierLedgerPath ([string]$artifact) 'ledger evidence artifact' $evidenceDirectory
        if ($ledgerEvidenceByPath.ContainsKey($artifactPath.ToLowerInvariant()) -or
                -not $manifestEvidenceByPath.ContainsKey($artifactPath.ToLowerInvariant())) {
            Throw-VerifierInfrastructure 'Integrated child ledger evidence was duplicate, foreign, or omitted by the manifest.'
        }
        $ledgerEvidenceByPath[$artifactPath.ToLowerInvariant()] = $artifactPath
    }
    $retainedEvidenceFiles = @(
        if (Test-Path -LiteralPath $evidenceDirectory -PathType Container) {
            Get-ChildItem -LiteralPath $evidenceDirectory -File -Recurse -Force -ErrorAction Stop
        }
    )
    foreach ($retainedEvidenceFile in $retainedEvidenceFiles) {
        $retainedEvidencePath = Get-VerifierFullPath $retainedEvidenceFile.FullName
        if (-not $ledgerEvidenceByPath.ContainsKey($retainedEvidencePath.ToLowerInvariant())) {
            Throw-VerifierInfrastructure 'Integrated child retained unrecorded or foreign evidence.'
        }
    }
    if (@($retainedEvidenceFiles).Count -ne @($ledgerEvidence).Count) {
        Throw-VerifierInfrastructure 'Integrated child evidence resource set was not bijective.'
    }

    $manifestLeasesValue = Get-VerifierLedgerProperty $manifest 'leases' $null
    $ledgerLeasesValue = Get-VerifierLedgerProperty $ledger 'leases' $null
    $manifestLeases = if ($null -eq $manifestLeasesValue) { @() } else { @($manifestLeasesValue) }
    $ledgerLeases = if ($null -eq $ledgerLeasesValue) { @() } else { @($ledgerLeasesValue) }
    $earlyManifestProfilesValue = Get-VerifierLedgerProperty $manifest 'browserSessions' $null
    $earlyLedgerProfilesValue = Get-VerifierLedgerProperty $ledger 'profiles' $null
    $requiresBrowserExecutableIdentity = $false
    foreach ($earlyLease in @($manifestLeases) + @($ledgerLeases)) {
        if ([string](Get-VerifierLedgerProperty $earlyLease 'kind' '') -eq 'cdp') {
            $requiresBrowserExecutableIdentity = $true
            break
        }
    }
    if ((($null -ne $earlyManifestProfilesValue) -and
            @($earlyManifestProfilesValue).Count -gt 0) -or
            (($null -ne $earlyLedgerProfilesValue) -and
            @($earlyLedgerProfilesValue).Count -gt 0)) {
        $requiresBrowserExecutableIdentity = $true
    }
    $expectedBrowserPath = ''
    if ($requiresBrowserExecutableIdentity) {
        $requestedBrowserPath = [string]$script:VerifierResolvedBrowserPath
        if ([String]::IsNullOrWhiteSpace($requestedBrowserPath)) {
            $requestedBrowserPath = [string]$script:BrowserPath
        }
        if ([String]::IsNullOrWhiteSpace($requestedBrowserPath)) {
            $expectedBrowserPath = Resolve-VerifierBrowserPath ''
        } else {
            $expectedBrowserPath = Resolve-VerifierBrowserPath $requestedBrowserPath
        }
        $expectedBrowserPath = Get-VerifierFullPath $expectedBrowserPath
        if ([String]::IsNullOrWhiteSpace($expectedBrowserPath) -or
                -not (Test-Path -LiteralPath $expectedBrowserPath -PathType Leaf)) {
            Throw-VerifierInfrastructure 'Integrated child could not establish its resolved browser executable identity.'
        }
    }
    $claimDirectory = Get-VerifierFullPath (Join-Path $runRoot 'port-leases')
    if (@($manifestLeases).Count -ne @($ledgerLeases).Count) {
        Throw-VerifierInfrastructure ('Integrated child ledger and manifest lease ledgers differed: manifest=' +
            [string]@($manifestLeases).Count + ' ledger=' + [string]@($ledgerLeases).Count +
            ' manifestJson=' + (($manifestLeases | ConvertTo-Json -Compress -Depth 6)) +
            ' ledgerJson=' + (($ledgerLeases | ConvertTo-Json -Compress -Depth 6)))
    }
    $manifestLeaseById = @{}
    $manifestLeasePathByPath = @{}
    $manifestLeaseByPort = @{}
    $manifestLeaseByMutex = @{}
    foreach ($manifestLeaseCandidate in $manifestLeases) {
        $candidateId = [string](Get-VerifierLedgerProperty $manifestLeaseCandidate 'leaseId' '')
        if ([String]::IsNullOrWhiteSpace($candidateId) -or $manifestLeaseById.ContainsKey($candidateId)) {
            Throw-VerifierInfrastructure 'Integrated child manifest lease ledger contained a duplicate or empty lease identity.'
        }
        $candidatePath = Get-VerifierLedgerPath ([string](Get-VerifierLedgerProperty $manifestLeaseCandidate 'path' '')) `
            'manifest lease claim' $claimDirectory
        $candidateKind = [string](Get-VerifierLedgerProperty $manifestLeaseCandidate 'kind' '')
        if ($candidateKind -eq 'cdp') {
            $candidateBrowserPath = [string](Get-VerifierLedgerProperty $manifestLeaseCandidate 'browserPath' '')
            if ([String]::IsNullOrWhiteSpace($candidateBrowserPath) -or
                    [String]::IsNullOrWhiteSpace((Get-VerifierCanonicalWindowsPath $candidateBrowserPath)) -or
                    -not (Test-VerifierCanonicalWindowsPathValue $candidateBrowserPath $expectedBrowserPath)) {
                Throw-VerifierInfrastructure "Integrated child manifest cdp lease $candidateId omitted or mismatched its resolved BrowserPath."
            }
        }
        $candidatePort = [int](Get-VerifierLedgerProperty $manifestLeaseCandidate 'port' 0)
        if ($candidatePort -lt 1 -or $candidatePort -gt 65535) {
            Throw-VerifierInfrastructure "Integrated child manifest lease $candidateId had an invalid port $candidatePort; expected 1..65535."
        }
        if ($candidatePort -in @(8888, 8898, 8899, 9876)) {
            Throw-VerifierInfrastructure "Integrated child manifest lease $candidateId used an ordinary developer port that is excluded from verifier claims."
        }
        $candidateMutex = Get-VerifierPortMutexName $null $candidatePort
        $candidateOwnerPid = [int](Get-VerifierLedgerProperty $manifestLeaseCandidate 'claimOwnerPid' 0)
        $candidateOwnerStart = [long](Get-VerifierLedgerProperty $manifestLeaseCandidate 'claimOwnerStartTicks' 0)
        if ($candidateOwnerPid -le 0 -or $candidateOwnerStart -le 0) {
            Throw-VerifierInfrastructure "Integrated child manifest lease $candidateId omitted a positive claim owner PID/start identity."
        }
        if ($manifestLeaseByPort.ContainsKey([string]$candidatePort)) {
            $prior = $manifestLeaseByPort[[string]$candidatePort]
            $priorPath = [string]$prior.Path
            if (-not (Test-VerifierIntegratedLeaseTerminal $prior.Lease $priorPath) -or
                    -not (Test-VerifierIntegratedLeaseTerminal $manifestLeaseCandidate $candidatePath)) {
                Throw-VerifierInfrastructure "Integrated child manifest contained duplicate live port $candidatePort."
            }
        }
        if ($manifestLeaseByMutex.ContainsKey($candidateMutex)) {
            $priorMutex = $manifestLeaseByMutex[$candidateMutex]
            if ([string]$priorMutex.Port -ne [string]$candidatePort -or
                    -not (Test-VerifierIntegratedLeaseTerminal $priorMutex.Lease $priorMutex.Path) -or
                    -not (Test-VerifierIntegratedLeaseTerminal $manifestLeaseCandidate $candidatePath)) {
                Throw-VerifierInfrastructure "Integrated child manifest contained duplicate live global mutex '$candidateMutex'."
            }
        }
        if ($manifestLeasePathByPath.ContainsKey($candidatePath.ToLowerInvariant())) {
            Throw-VerifierInfrastructure 'Integrated child manifest lease ledger contained duplicate claim paths.'
        }
        $manifestLeasePathByPath[$candidatePath.ToLowerInvariant()] = $candidateId
        $manifestLeaseById[$candidateId] = $manifestLeaseCandidate
        $manifestLeaseByPort[[string]$candidatePort] = [pscustomobject]@{
            Lease = $manifestLeaseCandidate; Path = $candidatePath; Port = $candidatePort
        }
        $manifestLeaseByMutex[$candidateMutex] = [pscustomobject]@{
            Lease = $manifestLeaseCandidate; Path = $candidatePath; Port = $candidatePort
        }
    }
    $ledgerLeaseIds = @{}
    $ledgerLeasePathByPath = @{}
    $ledgerLeaseByPath = @{}
    $ledgerLeaseByPort = @{}
    $ledgerLeaseByMutex = @{}
    foreach ($lease in $ledgerLeases) {
        $leaseId = [string](Get-VerifierLedgerProperty $lease 'leaseId' '')
        if ([String]::IsNullOrWhiteSpace($leaseId) -or $ledgerLeaseIds.ContainsKey($leaseId)) {
            Throw-VerifierInfrastructure ('Integrated child ledger lease ledger contained a duplicate or empty lease identity: ' +
                ([string]($lease | ConvertTo-Json -Compress -Depth 6)))
        }
        $ledgerLeaseIds[$leaseId] = $true
        $claimPath = Get-VerifierLedgerPath ([string](Get-VerifierLedgerProperty $lease 'path' '')) `
            'lease claim' $claimDirectory
        $ledgerKind = [string](Get-VerifierLedgerProperty $lease 'kind' '')
        if ($ledgerKind -eq 'cdp') {
            $ledgerBrowserPath = [string](Get-VerifierLedgerProperty $lease 'browserPath' '')
            if ([String]::IsNullOrWhiteSpace($ledgerBrowserPath) -or
                    [String]::IsNullOrWhiteSpace((Get-VerifierCanonicalWindowsPath $ledgerBrowserPath)) -or
                    -not (Test-VerifierCanonicalWindowsPathValue $ledgerBrowserPath $expectedBrowserPath)) {
                Throw-VerifierInfrastructure "Integrated child cdp lease $leaseId omitted or mismatched its resolved BrowserPath."
            }
        }
        $ledgerPort = [int](Get-VerifierLedgerProperty $lease 'port' 0)
        if ($ledgerPort -lt 1 -or $ledgerPort -gt 65535) {
            Throw-VerifierInfrastructure "Integrated child lease $leaseId had an invalid port $ledgerPort; expected 1..65535."
        }
        if ($ledgerPort -in @(8888, 8898, 8899, 9876)) {
            Throw-VerifierInfrastructure "Integrated child lease $leaseId used an ordinary developer port that is excluded from verifier claims."
        }
        $ledgerMutex = Get-VerifierPortMutexName $null $ledgerPort
        $ledgerOwnerPid = [int](Get-VerifierLedgerProperty $lease 'claimOwnerPid' 0)
        $ledgerOwnerStart = [long](Get-VerifierLedgerProperty $lease 'claimOwnerStartTicks' 0)
        if ($ledgerOwnerPid -le 0 -or $ledgerOwnerStart -le 0) {
            Throw-VerifierInfrastructure "Integrated child lease $leaseId omitted a positive claim owner PID/start identity."
        }
        if ($ledgerLeaseByPort.ContainsKey([string]$ledgerPort)) {
            $prior = $ledgerLeaseByPort[[string]$ledgerPort]
            if (-not (Test-VerifierIntegratedLeaseTerminal $prior.Lease $prior.Path) -or
                    -not (Test-VerifierIntegratedLeaseTerminal $lease $claimPath)) {
                Throw-VerifierInfrastructure "Integrated child ledger contained duplicate live port $ledgerPort."
            }
        }
        if ($ledgerLeaseByMutex.ContainsKey($ledgerMutex)) {
            $priorMutex = $ledgerLeaseByMutex[$ledgerMutex]
            if ([string]$priorMutex.Port -ne [string]$ledgerPort -or
                    -not (Test-VerifierIntegratedLeaseTerminal $priorMutex.Lease $priorMutex.Path) -or
                    -not (Test-VerifierIntegratedLeaseTerminal $lease $claimPath)) {
                Throw-VerifierInfrastructure "Integrated child ledger contained duplicate live global mutex '$ledgerMutex'."
            }
        }
        if ($ledgerLeasePathByPath.ContainsKey($claimPath.ToLowerInvariant())) {
            Throw-VerifierInfrastructure 'Integrated child ledger contained duplicate claim paths.'
        }
        $ledgerLeasePathByPath[$claimPath.ToLowerInvariant()] = $leaseId
        $ledgerLeaseByPath[$claimPath.ToLowerInvariant()] = $lease
        $ledgerLeaseByPort[[string]$ledgerPort] = [pscustomobject]@{
            Lease = $lease; Path = $claimPath; Port = $ledgerPort
        }
        $ledgerLeaseByMutex[$ledgerMutex] = [pscustomobject]@{
            Lease = $lease; Path = $claimPath; Port = $ledgerPort
        }
        if (-not $manifestLeaseById.ContainsKey($leaseId)) {
            Throw-VerifierInfrastructure 'Integrated child ledger contained a lease omitted by the manifest.'
        }
        $ml = $manifestLeaseById[$leaseId]
        if (-not $manifestLeasePathByPath.ContainsKey($claimPath.ToLowerInvariant()) -or
                $manifestLeasePathByPath[$claimPath.ToLowerInvariant()] -ne $leaseId) {
            Throw-VerifierInfrastructure "Integrated child lease $leaseId claim path was omitted or mapped to another lease."
        }
        foreach ($identityProperty in @('runId', 'repositoryIdentity', 'kind', 'browserPath', 'claimName',
                'status', 'claimState', 'releaseState', 'claimOwnerPid',
                'claimOwnerStartTicks', 'mutexReleased', 'releaseJournalState',
                'listenerAbsent', 'processProofRequired')) {
            if ([string](Get-VerifierLedgerProperty $lease $identityProperty '') -ne
                    [string](Get-VerifierLedgerProperty $ml $identityProperty '')) {
                Throw-VerifierInfrastructure "Integrated child lease $leaseId disagreed with the manifest on $identityProperty."
            }
        }
        if ([string](Get-VerifierLedgerProperty $lease 'runId' '') -ne $runId -or
                [string](Get-VerifierLedgerProperty $lease 'repositoryIdentity' '') -ne $repositoryIdentity -or
                -not (Test-VerifierCanonicalWindowsPathValue ([string](Get-VerifierLedgerProperty $lease 'worktreeRoot' '')) $worktreeRoot) -or
                -not (Test-VerifierCanonicalWindowsPathValue ([string](Get-VerifierLedgerProperty $ml 'worktreeRoot' '')) $worktreeRoot) -or
                [int](Get-VerifierLedgerProperty $lease 'port' 0) -lt 1 -or
                [int](Get-VerifierLedgerProperty $lease 'port' 0) -gt 65535 -or
                [int](Get-VerifierLedgerProperty $lease 'port' 0) -in @(8888, 8898, 8899, 9876) -or
                [int](Get-VerifierLedgerProperty $ml 'port' 0) -ne [int](Get-VerifierLedgerProperty $lease 'port' 0) -or
                -not (Test-VerifierCanonicalWindowsPathValue ([string](Get-VerifierLedgerProperty $ml 'path' '')) $claimPath)) {
            Throw-VerifierInfrastructure "Integrated child lease $leaseId had foreign, incomplete, or mismatched identity fields."
        }
        $leaseKind = [string](Get-VerifierLedgerProperty $lease 'kind' '')
        if ($leaseKind -notin @('preview', 'cdp')) {
            Throw-VerifierInfrastructure "Integrated child lease $leaseId used an unsupported lease kind '$leaseKind'."
        }
        $leasePortForPath = [int](Get-VerifierLedgerProperty $lease 'port' 0)
        $expectedMutexName = Get-VerifierPortMutexName $null $leasePortForPath
        $expectedClaimPath = Get-VerifierFullPath (Join-Path $claimDirectory ($leasePortForPath.ToString() + '-' + $leaseId + '.lease'))
        if ([string](Get-VerifierLedgerProperty $lease 'claimName' '') -ne $expectedMutexName -or
                [string](Get-VerifierLedgerProperty $ml 'claimName' '') -ne $expectedMutexName -or
                -not (Test-VerifierCanonicalWindowsPathValue $claimPath $expectedClaimPath)) {
            Throw-VerifierInfrastructure "Integrated child lease $leaseId had a noncanonical global mutex or claim path."
        }
        $claimDescriptor = Get-VerifierLedgerProperty $lease 'claim' $null
        $manifestClaimDescriptor = Get-VerifierLedgerProperty $ml 'claim' $null
        if ($null -eq $claimDescriptor -or $null -eq $manifestClaimDescriptor) {
            Throw-VerifierInfrastructure "Integrated child lease $leaseId omitted its claim identity descriptor."
        }
        foreach ($claimProperty in @('protocol', 'runId', 'repositoryIdentity',
                'leaseId', 'kind', 'port', 'mutexName', 'ownerPid', 'ownerStartTicks')) {
            if ($claimProperty -eq 'protocol') {
                $claimExpected = 'troubleshootjs-verifier-port-claim-v1'
            } elseif ($claimProperty -eq 'runId') {
                $claimExpected = $runId
            } elseif ($claimProperty -eq 'repositoryIdentity') {
                $claimExpected = $repositoryIdentity
            } elseif ($claimProperty -eq 'leaseId') {
                $claimExpected = $leaseId
            } elseif ($claimProperty -eq 'kind') {
                $claimExpected = [string](Get-VerifierLedgerProperty $lease 'kind' '')
            } elseif ($claimProperty -eq 'port') {
                $claimExpected = [int](Get-VerifierLedgerProperty $lease 'port' 0)
            } elseif ($claimProperty -eq 'mutexName') {
                $claimExpected = [string](Get-VerifierLedgerProperty $lease 'claimName' '')
            } elseif ($claimProperty -eq 'ownerPid') {
                $claimExpected = [int](Get-VerifierLedgerProperty $lease 'claimOwnerPid' 0)
            } else {
                $claimExpected = [long](Get-VerifierLedgerProperty $lease 'claimOwnerStartTicks' 0)
            }
            if ([string](Get-VerifierLedgerProperty $claimDescriptor $claimProperty '') -ne [string]$claimExpected) {
                Throw-VerifierInfrastructure "Integrated child lease $leaseId claim descriptor disagreed on $claimProperty."
            }
            if ([string](Get-VerifierLedgerProperty $manifestClaimDescriptor $claimProperty '') -ne
                    [string](Get-VerifierLedgerProperty $claimDescriptor $claimProperty '')) {
                Throw-VerifierInfrastructure "Integrated child lease $leaseId manifest claim descriptor disagreed on $claimProperty."
            }
        }
        if (-not (Test-VerifierCanonicalWindowsPathValue `
                ([string](Get-VerifierLedgerProperty $claimDescriptor 'worktreeRoot' '')) $worktreeRoot) -or
                -not (Test-VerifierCanonicalWindowsPathValue `
                ([string](Get-VerifierLedgerProperty $claimDescriptor 'path' '')) $claimPath) -or
                [string](Get-VerifierLedgerProperty $claimDescriptor 'mutexName' '') -ne $expectedMutexName -or
                 [string](Get-VerifierLedgerProperty $manifestClaimDescriptor 'mutexName' '') -ne $expectedMutexName) {
            Throw-VerifierInfrastructure "Integrated child lease $leaseId claim descriptor had foreign or mismatched ownership paths."
        }
        $descriptorOwnerPid = [int](Get-VerifierLedgerProperty $claimDescriptor 'ownerPid' 0)
        $descriptorOwnerStart = [long](Get-VerifierLedgerProperty $claimDescriptor 'ownerStartTicks' 0)
        if ($descriptorOwnerPid -le 0 -or $descriptorOwnerStart -le 0 -or
                [int](Get-VerifierLedgerProperty $claimDescriptor 'port' 0) -lt 1 -or
                [int](Get-VerifierLedgerProperty $claimDescriptor 'port' 0) -gt 65535 -or
                [int](Get-VerifierLedgerProperty $manifestClaimDescriptor 'ownerPid' 0) -le 0 -or
                [long](Get-VerifierLedgerProperty $manifestClaimDescriptor 'ownerStartTicks' 0) -le 0) {
            Throw-VerifierInfrastructure "Integrated child lease $leaseId claim descriptor omitted a valid port or positive owner identity."
        }
        $claimState = [string](Get-VerifierLedgerProperty $lease 'claimState' '')
        $releaseState = [string](Get-VerifierLedgerProperty $lease 'releaseState' '')
        $completedLease = ($state -eq 'completed')
        if ($completedLease) {
            if (-not (Test-VerifierIntegratedLeaseTerminal $lease $claimPath) -or
                    -not (Test-VerifierCanonicalWindowsPathValue ([string](Get-VerifierLedgerProperty $ml 'path' '')) $claimPath) -or
                    [int](Get-VerifierLedgerProperty $ml 'port' 0) -ne [int](Get-VerifierLedgerProperty $lease 'port' 0) -or
                    [string](Get-VerifierLedgerProperty $ml 'status' '') -ne 'released' -or
                    [string](Get-VerifierLedgerProperty $ml 'releaseState' '') -ne 'complete' -or
                    -not (Test-VerifierIntegratedLeaseTerminal $ml $claimPath)) {
                Throw-VerifierInfrastructure 'Completed integrated child lease did not prove release, claim absence, and listener absence.'
            }
        } else {
            $leaseReleased = ([string](Get-VerifierLedgerProperty $lease 'status' '') -eq 'released' -and
                $claimState -eq 'released' -and $releaseState -eq 'complete')
            if ([String]::IsNullOrWhiteSpace($claimState) -or
                    (Test-Path -LiteralPath $claimPath -PathType Leaf) -ne (-not $leaseReleased)) {
                Throw-VerifierInfrastructure 'Incomplete integrated child lease did not retain exactly the expected claim resource.'
            }
            if (-not $leaseReleased) {
                try { $claim = Get-Content -LiteralPath $claimPath -Raw | ConvertFrom-Json } catch {
                    Throw-VerifierInfrastructure 'Incomplete integrated child claim was not readable evidence.'
                }
                if ([string](Get-VerifierLedgerProperty $claim 'protocol' '') -ne 'troubleshootjs-verifier-port-claim-v1' -or
                        [string](Get-VerifierLedgerProperty $claim 'runId' '') -ne $runId -or
                        [string](Get-VerifierLedgerProperty $claim 'repositoryIdentity' '') -ne $repositoryIdentity -or
                        -not (Test-VerifierCanonicalWindowsPathValue ([string](Get-VerifierLedgerProperty $claim 'worktreeRoot' '')) $worktreeRoot) -or
                        [string](Get-VerifierLedgerProperty $claim 'leaseId' '') -ne $leaseId -or
                        [string](Get-VerifierLedgerProperty $claim 'kind' '') -ne [string](Get-VerifierLedgerProperty $lease 'kind' '') -or
                        -not (Test-VerifierCanonicalWindowsPathValue ([string](Get-VerifierLedgerProperty $claim 'path' '')) $claimPath) -or
                        [int](Get-VerifierLedgerProperty $claim 'port' 0) -ne [int](Get-VerifierLedgerProperty $lease 'port' 0) -or
                        [string](Get-VerifierLedgerProperty $claim 'mutexName' '') -ne [string](Get-VerifierLedgerProperty $lease 'claimName' '') -or
                         [int](Get-VerifierLedgerProperty $claim 'ownerPid' 0) -ne [int](Get-VerifierLedgerProperty $lease 'claimOwnerPid' 0) -or
                         [long](Get-VerifierLedgerProperty $claim 'ownerStartTicks' 0) -ne [long](Get-VerifierLedgerProperty $lease 'claimOwnerStartTicks' 0) -or
                         [int](Get-VerifierLedgerProperty $claim 'port' 0) -lt 1 -or
                         [int](Get-VerifierLedgerProperty $claim 'port' 0) -gt 65535 -or
                         [int](Get-VerifierLedgerProperty $claim 'ownerPid' 0) -le 0 -or
                         [long](Get-VerifierLedgerProperty $claim 'ownerStartTicks' 0) -le 0) {
                    Throw-VerifierInfrastructure 'Incomplete integrated child claim was foreign, stale, or mismatched.'
                }
            }
        }
    }
    $claimFiles = @(
        if (Test-Path -LiteralPath $claimDirectory -PathType Container) {
            Get-ChildItem -LiteralPath $claimDirectory -Filter '*.lease' -File -Force -ErrorAction Stop
        }
    )
    $ledgerClaimPaths = @{}
    foreach ($lease in $ledgerLeases) {
        $leaseClaimPath = Get-VerifierLedgerPath ([string](Get-VerifierLedgerProperty $lease 'path' '')) `
            'lease claim' $claimDirectory
        $ledgerClaimPaths[$leaseClaimPath.ToLowerInvariant()] = $true
    }
    foreach ($claimFile in $claimFiles) {
        $claimFilePath = Get-VerifierFullPath $claimFile.FullName
        if (-not $ledgerClaimPaths.ContainsKey($claimFilePath.ToLowerInvariant())) {
            Throw-VerifierInfrastructure 'Integrated child retained an unrecorded or foreign port claim.'
        }
    }
    if ($state -eq 'completed' -and @($claimFiles).Count -ne 0) {
        Throw-VerifierInfrastructure 'Completed integrated child retained an unrecorded port claim.'
    }

    $manifestProfilesValue = Get-VerifierLedgerProperty $manifest 'browserSessions' $null
    $ledgerProfilesValue = Get-VerifierLedgerProperty $ledger 'profiles' $null
    $manifestProfiles = if ($null -eq $manifestProfilesValue) { @() } else { @($manifestProfilesValue) }
    $ledgerProfiles = if ($null -eq $ledgerProfilesValue) { @() } else { @($ledgerProfilesValue) }
    if (@($manifestProfiles).Count -ne @($ledgerProfiles).Count) {
        Throw-VerifierInfrastructure 'Integrated child ledger and manifest profile ledgers differed.'
    }
    $manifestProfileByPath = @{}
    $manifestProfileByLeasePath = @{}
    foreach ($manifestProfile in $manifestProfiles) {
        $manifestProfilePath = Get-VerifierLedgerPath `
            ([string](Get-VerifierLedgerProperty $manifestProfile 'profile' '')) `
            'manifest browser profile' $runRoot
        $profileKey = $manifestProfilePath.ToLowerInvariant()
        if ($manifestProfileByPath.ContainsKey($profileKey)) {
            Throw-VerifierInfrastructure 'Integrated child manifest profile ledger contained a duplicate path.'
        }
        $manifestProfileLeasePath = Get-VerifierLedgerPath `
            ([string](Get-VerifierLedgerProperty $manifestProfile 'cdpLeasePath' '')) `
            'manifest browser lease' $claimDirectory
        if ($manifestProfileByLeasePath.ContainsKey($manifestProfileLeasePath.ToLowerInvariant())) {
            Throw-VerifierInfrastructure 'Integrated child manifest profile ledger contained duplicate lease identities.'
        }
        $manifestProfileByLeasePath[$manifestProfileLeasePath.ToLowerInvariant()] = $profileKey
        $manifestProfileByPath[$profileKey] = $manifestProfile
    }
    $ledgerProfileByPath = @{}
    $ledgerProfileByLeasePath = @{}
    foreach ($profile in $ledgerProfiles) {
        $profilePath = Get-VerifierLedgerPath ([string](Get-VerifierLedgerProperty $profile 'profile' '')) `
            'browser profile' $runRoot
        $profileKey = $profilePath.ToLowerInvariant()
        if ($ledgerProfileByPath.ContainsKey($profileKey) -or
                -not $manifestProfileByPath.ContainsKey($profileKey)) {
            Throw-VerifierInfrastructure 'Integrated child profile ledger was duplicate, omitted, or foreign.'
        }
        $ledgerProfileByPath[$profileKey] = $profile
        $matchedProfile = $manifestProfileByPath[$profileKey]
        $ledgerLeasePath = Get-VerifierLedgerPath ([string](Get-VerifierLedgerProperty $profile 'cdpLeasePath' '')) `
            'browser lease' $claimDirectory
        if ($ledgerProfileByLeasePath.ContainsKey($ledgerLeasePath.ToLowerInvariant()) -or
                -not $manifestProfileByLeasePath.ContainsKey($ledgerLeasePath.ToLowerInvariant()) -or
                -not $ledgerLeasePathByPath.ContainsKey($ledgerLeasePath.ToLowerInvariant())) {
            Throw-VerifierInfrastructure 'Integrated child profile ledger had a duplicate, omitted, or foreign lease identity.'
        }
        $ledgerProfileByLeasePath[$ledgerLeasePath.ToLowerInvariant()] = $profileKey
        foreach ($identityProperty in @('owner', 'runId', 'repositoryIdentity',
                'cdpPort', 'processId', 'processStartTicks', 'processParentProcessId',
                'processParentProcessStartTicks',
                'processCommandLine', 'browserPath', 'status', 'cleanupResult')) {
            if ([string](Get-VerifierLedgerProperty $profile $identityProperty '') -ne
                    [string](Get-VerifierLedgerProperty $matchedProfile $identityProperty '')) {
                Throw-VerifierInfrastructure "Integrated child profile $profilePath disagreed with the manifest on $identityProperty."
            }
        }
        if (-not (Test-VerifierCanonicalWindowsPathValue ([string](Get-VerifierLedgerProperty $profile 'worktreeRoot' '')) $worktreeRoot) -or
                -not (Test-VerifierCanonicalWindowsPathValue ([string](Get-VerifierLedgerProperty $matchedProfile 'worktreeRoot' '')) $worktreeRoot)) {
                Throw-VerifierInfrastructure 'Integrated child profile worktree identity was foreign or incomplete.'
        }
        $browserRoot = Get-VerifierFullPath (Join-Path $runRoot 'browser')
        if (-not (Test-VerifierChildPath $browserRoot $profilePath)) {
            Throw-VerifierInfrastructure "Integrated child browser profile '$profilePath' was not directly owned by its browser run namespace."
        }
        foreach ($browserPathValue in @(
                [string](Get-VerifierLedgerProperty $profile 'browserPath' ''),
                [string](Get-VerifierLedgerProperty $matchedProfile 'browserPath' ''))) {
            if ([String]::IsNullOrWhiteSpace($browserPathValue) -or
                    [String]::IsNullOrWhiteSpace((Get-VerifierCanonicalWindowsPath $browserPathValue)) -or
                    -not (Test-VerifierCanonicalWindowsPathValue $browserPathValue $expectedBrowserPath)) {
                Throw-VerifierInfrastructure 'Integrated child browser profile omitted or mismatched its resolved BrowserPath.'
            }
        }
        $manifestLeasePath = Get-VerifierLedgerPath ([string](Get-VerifierLedgerProperty $matchedProfile 'cdpLeasePath' '')) `
            'manifest browser lease' $claimDirectory
        if (-not (Test-VerifierCanonicalWindowsPathValue $ledgerLeasePath $manifestLeasePath)) {
            Throw-VerifierInfrastructure 'Integrated child profile lease identity was not bijective.'
        }
        $profileLease = $ledgerLeaseByPath[$ledgerLeasePath.ToLowerInvariant()]
        $profileLeaseKind = [string](Get-VerifierLedgerProperty $profileLease 'kind' '')
        $profilePort = [int](Get-VerifierLedgerProperty $profile 'cdpPort' 0)
        if ([string](Get-VerifierLedgerProperty $profile 'owner' '') -ne 'run' -or
                $profileLeaseKind -ne 'cdp' -or
                [string](Get-VerifierLedgerProperty $profileLease 'kind' '') -ne 'cdp' -or
                [int](Get-VerifierLedgerProperty $matchedProfile 'cdpPort' 0) -lt 1 -or
                [int](Get-VerifierLedgerProperty $matchedProfile 'cdpPort' 0) -gt 65535 -or
                $profilePort -lt 1 -or $profilePort -gt 65535 -or
                $profilePort -ne
                    [int](Get-VerifierLedgerProperty $profileLease 'port' 0)) {
            Throw-VerifierInfrastructure 'Integrated child profile did not identify its exact run-owned cdp lease and valid port.'
        }
        if (-not (Test-VerifierCanonicalWindowsPathValue ([string](Get-VerifierLedgerProperty $profile 'profile' '')) $profilePath) -or
                -not (Test-VerifierCanonicalWindowsPathValue ([string](Get-VerifierLedgerProperty $matchedProfile 'profile' '')) $profilePath) -or
                -not (Test-VerifierCanonicalWindowsPathValue ([string](Get-VerifierLedgerProperty $profile 'cdpLeasePath' '')) $ledgerLeasePath) -or
                -not (Test-VerifierCanonicalWindowsPathValue ([string](Get-VerifierLedgerProperty $matchedProfile 'cdpLeasePath' '')) $ledgerLeasePath) -or
                [string](Get-VerifierLedgerProperty $profile 'runId' '') -ne $runId -or
                [string](Get-VerifierLedgerProperty $matchedProfile 'runId' '') -ne $runId) {
            Throw-VerifierInfrastructure 'Integrated child browser profile path, lease path, or run identity was not canonical and exact.'
        }
        $profileProcessId = [int](Get-VerifierLedgerProperty $profile 'processId' 0)
        if ($profileProcessId -gt 0 -and
                ([long](Get-VerifierLedgerProperty $profile 'processStartTicks' 0) -le 0 -or
                 [int](Get-VerifierLedgerProperty $profile 'processParentProcessId' 0) -le 0 -or
                 [long](Get-VerifierLedgerProperty $profile 'processParentProcessStartTicks' 0) -le 0 -or
                 [String]::IsNullOrWhiteSpace([string](Get-VerifierLedgerProperty $profile 'processCommandLine' '')))) {
            Throw-VerifierInfrastructure 'Integrated child browser profile omitted its recorded root parent/start/command identity.'
        }
        $profileComplete = ([string](Get-VerifierLedgerProperty $profile 'cleanupResult' '') -eq 'complete' -and
            [string](Get-VerifierLedgerProperty $profile 'status' '') -eq 'cleaned')
        if ($state -eq 'completed') {
            if (-not $profileComplete -or (Test-Path -LiteralPath $profilePath)) {
                Throw-VerifierInfrastructure 'Completed integrated child retained a browser profile or incomplete profile cleanup.'
            }
        } elseif ($profileComplete) {
            if (Test-Path -LiteralPath $profilePath) {
                Throw-VerifierInfrastructure 'Incomplete integrated child claimed a cleaned profile that still exists.'
            }
        } elseif (-not (Test-Path -LiteralPath $profilePath -PathType Container)) {
            Throw-VerifierInfrastructure 'Incomplete integrated child did not retain its owned browser profile evidence.'
        }
    }
    foreach ($manifestProfileKey in $manifestProfileByPath.Keys) {
        if (-not $ledgerProfileByPath.ContainsKey($manifestProfileKey)) {
            Throw-VerifierInfrastructure 'Integrated child manifest retained a browser profile omitted by the ledger.'
        }
    }
    foreach ($manifestProfileLeaseKey in $manifestProfileByLeasePath.Keys) {
        if (-not $ledgerProfileByLeasePath.ContainsKey($manifestProfileLeaseKey)) {
            Throw-VerifierInfrastructure 'Integrated child manifest retained a browser lease/profile omitted by the ledger.'
        }
    }
    # The profile relation is a bijection in both directions.  A browser
    # profile can be satisfied only by its one cdp lease; a preview lease can
    # never be used as a browser/profile resource, and a cdp lease may not be
    # hidden from the inverse profile ledger.
    $profileInverseCountByLease = @{}
    foreach ($profile in $ledgerProfiles) {
        $inverseLeasePath = Get-VerifierLedgerPath `
            ([string](Get-VerifierLedgerProperty $profile 'cdpLeasePath' '')) `
            'profile inverse lease' $claimDirectory
        $inverseKey = $inverseLeasePath.ToLowerInvariant()
        if (-not $profileInverseCountByLease.ContainsKey($inverseKey)) {
            $profileInverseCountByLease[$inverseKey] = 0
        }
        $profileInverseCountByLease[$inverseKey]++
    }
    foreach ($lease in $ledgerLeases) {
        $leaseId = [string](Get-VerifierLedgerProperty $lease 'leaseId' '')
        $leaseKind = [string](Get-VerifierLedgerProperty $lease 'kind' '')
        $leasePath = Get-VerifierLedgerPath ([string](Get-VerifierLedgerProperty $lease 'path' '')) `
            'profile-bijection lease' $claimDirectory
        $leaseKey = $leasePath.ToLowerInvariant()
        $manifestLease = $manifestLeaseById[$leaseId]
        $ledgerProfileText = [string](Get-VerifierLedgerProperty $lease 'profile' '')
        $manifestProfileText = [string](Get-VerifierLedgerProperty $manifestLease 'profile' '')
        if ($leaseKind -eq 'preview') {
            if (-not [String]::IsNullOrWhiteSpace($ledgerProfileText) -or
                    -not [String]::IsNullOrWhiteSpace($manifestProfileText) -or
                    $profileInverseCountByLease.ContainsKey($leaseKey)) {
                Throw-VerifierInfrastructure "Preview lease $leaseId incorrectly carried a browser profile inverse."
            }
            continue
        }
        if ($leaseKind -ne 'cdp' -or [String]::IsNullOrWhiteSpace($ledgerProfileText) -or
                [String]::IsNullOrWhiteSpace($manifestProfileText) -or
                -not $profileInverseCountByLease.ContainsKey($leaseKey) -or
                $profileInverseCountByLease[$leaseKey] -ne 1) {
            Throw-VerifierInfrastructure "Cdp lease $leaseId did not have exactly one inverse browser profile."
        }
        $leaseProfilePath = Get-VerifierLedgerPath $ledgerProfileText `
            'cdp lease profile' $runRoot
        $manifestLeaseProfilePath = Get-VerifierLedgerPath $manifestProfileText `
            'manifest cdp lease profile' $runRoot
        if (-not (Test-VerifierCanonicalWindowsPathValue $leaseProfilePath $manifestLeaseProfilePath) -or
                -not $ledgerProfileByPath.ContainsKey($leaseProfilePath.ToLowerInvariant())) {
            Throw-VerifierInfrastructure "Cdp lease $leaseId profile inverse was omitted or foreign."
        }
        $inverseProfile = $ledgerProfileByPath[$leaseProfilePath.ToLowerInvariant()]
        if (-not (Test-VerifierCanonicalWindowsPathValue `
                ([string](Get-VerifierLedgerProperty $inverseProfile 'profile' '')) $leaseProfilePath) -or
                -not (Test-VerifierCanonicalWindowsPathValue `
                ([string](Get-VerifierLedgerProperty $inverseProfile 'cdpLeasePath' '')) $leasePath) -or
                [int](Get-VerifierLedgerProperty $inverseProfile 'cdpPort' 0) -ne
                    [int](Get-VerifierLedgerProperty $lease 'port' 0) -or
                [string](Get-VerifierLedgerProperty $inverseProfile 'runId' '') -ne $runId -or
                [string](Get-VerifierLedgerProperty $inverseProfile 'repositoryIdentity' '') -ne $repositoryIdentity) {
            Throw-VerifierInfrastructure "Cdp lease $leaseId and browser profile inverse did not match exactly."
        }
    }
    foreach ($profileLeaseKey in $profileInverseCountByLease.Keys) {
        if (-not $ledgerLeaseByPath.ContainsKey($profileLeaseKey) -or
                [string](Get-VerifierLedgerProperty $ledgerLeaseByPath[$profileLeaseKey] 'kind' '') -ne 'cdp') {
            Throw-VerifierInfrastructure 'Integrated child profile inverse pointed to a missing or non-cdp lease.'
        }
    }
    $browserRoot = Get-VerifierFullPath (Join-Path $runRoot 'browser')
    $retainedProfiles = @(
        if (Test-Path -LiteralPath $browserRoot -PathType Container) {
            Get-ChildItem -LiteralPath $browserRoot -Directory -Recurse -Force -ErrorAction Stop |
                Where-Object { $_.Name -eq 'profile' }
        }
    )
    foreach ($retainedProfileDirectory in $retainedProfiles) {
        $retainedProfilePath = Get-VerifierFullPath $retainedProfileDirectory.FullName
        if (-not $ledgerProfileByPath.ContainsKey($retainedProfilePath.ToLowerInvariant())) {
            Throw-VerifierInfrastructure 'Integrated child retained an unrecorded or foreign browser profile.'
        }
    }
    if ($state -eq 'completed') {
        if (@($retainedProfiles).Count -ne 0) {
            Throw-VerifierInfrastructure 'Completed integrated child retained an unrecorded browser profile.'
        }
    }

    $server = Get-VerifierLedgerProperty $ledger 'server' $null
    $manifestServer = Get-VerifierLedgerProperty $manifest 'server' $null
    if ($null -eq $server -or $null -eq $manifestServer -or
            @($server).Count -ne 1 -or @($manifestServer).Count -ne 1) {
        Throw-VerifierInfrastructure 'Integrated child ledger and manifest did not both record the server resource.'
    }
    $serverOwner = [string](Get-VerifierLedgerProperty $server 'owner' '')
    $manifestServerOwner = [string](Get-VerifierLedgerProperty $manifestServer 'owner' '')
    if ($serverOwner -notin @('none', 'run', 'caller') -or $serverOwner -ne $manifestServerOwner) {
        Throw-VerifierInfrastructure 'Integrated child server ownership was missing, foreign, or differed between ledgers.'
    }
    foreach ($serverProperty in @('owner', 'baseUrl', 'repositoryIdentity', 'worktreeRoot', 'repositoryRoot',
            'webRoot', 'identityProtocol', 'identityVerified', 'callerOwned', 'port', 'processId',
            'processStartTicks', 'processParentProcessId', 'processParentProcessStartTicks',
            'processCommandLine',
            'script', 'runId', 'nonce', 'leaseId', 'leaseKind', 'leaseClaimName',
            'leaseClaimState', 'leaseReleaseState', 'leaseReleaseJournalState',
            'leaseOwnerPid',
            'leaseOwnerStartTicks', 'leasePath', 'stdoutLog', 'stderrLog',
            'state', 'cleanupResult',
            'processIdentityKnown', 'ownershipUncertain', 'processTerminationProven',
            'processAbsent', 'listenerInspectionProven', 'listenerAbsent',
            'leaseListenerAbsent', 'leaseProcessProofRequired')) {
        if ([string](Get-VerifierLedgerProperty $server $serverProperty '') -ne
                [string](Get-VerifierLedgerProperty $manifestServer $serverProperty '')) {
            Throw-VerifierInfrastructure "Integrated child server ledger differed from the manifest on $serverProperty."
        }
    }
    if ([string](Get-VerifierLedgerProperty $server 'repositoryIdentity' '') -ne $repositoryIdentity -or
            -not (Test-VerifierCanonicalWindowsPathValue `
                ([string](Get-VerifierLedgerProperty $server 'worktreeRoot' '')) $worktreeRoot)) {
        Throw-VerifierInfrastructure 'Integrated child server repository/worktree identity was foreign or incomplete.'
    }
    $serverScript = [string](Get-VerifierLedgerProperty $server 'script' '')
    $manifestScript = [string](Get-VerifierLedgerProperty $manifestServer 'script' '')
    if (-not [String]::IsNullOrWhiteSpace($serverScript)) {
        $serverScript = Get-VerifierFullPath $serverScript
        if (-not (Test-VerifierChildPath $worktreeRoot $serverScript)) {
            Throw-VerifierInfrastructure 'Integrated child server script escaped the owned worktree.'
        }
        if (-not (Test-Path -LiteralPath $serverScript -PathType Leaf)) {
            Throw-VerifierInfrastructure 'Integrated child server script was not retained by its owned worktree.'
        }
    }
    if (-not [String]::IsNullOrWhiteSpace($manifestScript) -and
            -not (Test-VerifierCanonicalWindowsPathValue $manifestScript $serverScript)) {
        Throw-VerifierInfrastructure 'Integrated child server script path was not canonical and bijective.'
    }
    if ($serverOwner -eq 'run') {
        $canonicalPreviewScript = Get-VerifierFullPath (Join-Path $worktreeRoot 'scripts\preview.ps1')
        if (-not (Test-VerifierCanonicalWindowsPathValue $serverScript $canonicalPreviewScript) -or
                -not (Test-VerifierCanonicalWindowsPathValue $manifestScript $canonicalPreviewScript)) {
            Throw-VerifierInfrastructure 'Integrated child run-owned server did not reference the canonical scripts\preview.ps1.'
        }
    }
    $serverLogRoot = Get-VerifierFullPath (Join-Path $runRoot 'server')
    $expectedStdoutLog = Get-VerifierFullPath (Join-Path $serverLogRoot 'stdout.log')
    $expectedStderrLog = Get-VerifierFullPath (Join-Path $serverLogRoot 'stderr.log')
    foreach ($serverLogProperty in @('stdoutLog', 'stderrLog')) {
        $serverLog = [string](Get-VerifierLedgerProperty $server $serverLogProperty '')
        $manifestLog = [string](Get-VerifierLedgerProperty $manifestServer $serverLogProperty '')
        if ($serverOwner -eq 'run' -and [String]::IsNullOrWhiteSpace($serverLog)) {
            Throw-VerifierInfrastructure "Integrated child run-owned server omitted its $serverLogProperty path."
        }
        if (-not [String]::IsNullOrWhiteSpace($serverLog)) {
            $serverLog = Get-VerifierLedgerPath $serverLog $serverLogProperty $runRoot
            if ($state -eq 'completed' -or [int](Get-VerifierLedgerProperty $server 'processId' 0) -gt 0) {
                if (-not (Test-Path -LiteralPath $serverLog -PathType Leaf)) {
                    Throw-VerifierInfrastructure "Integrated child server $serverLogProperty was not retained."
                }
            }
        }
        if ($serverOwner -eq 'run') {
            $expectedLog = if ($serverLogProperty -eq 'stdoutLog') { $expectedStdoutLog } else { $expectedStderrLog }
            if (-not (Test-VerifierCanonicalWindowsPathValue $serverLog $expectedLog)) {
                Throw-VerifierInfrastructure "Integrated child run-owned server $serverLogProperty was not the canonical direct log child."
            }
        }
        if (-not [String]::IsNullOrWhiteSpace($manifestLog) -and
                -not (Test-VerifierCanonicalWindowsPathValue $manifestLog $serverLog)) {
            Throw-VerifierInfrastructure "Integrated child server $serverLogProperty path was not canonical and bijective."
        }
    }
    $serverLeasePathText = [string](Get-VerifierLedgerProperty $server 'leasePath' '')
    $manifestLeasePathText = [string](Get-VerifierLedgerProperty $manifestServer 'leasePath' '')
    $serverLeasePath = if ([String]::IsNullOrWhiteSpace($serverLeasePathText)) { '' } else {
        Get-VerifierLedgerPath $serverLeasePathText 'server lease' $claimDirectory
    }
    $manifestLeasePath = if ([String]::IsNullOrWhiteSpace($manifestLeasePathText)) { '' } else {
        Get-VerifierLedgerPath $manifestLeasePathText 'manifest server lease' $claimDirectory
    }
    if ([String]::IsNullOrWhiteSpace($serverLeasePath) -ne
            [String]::IsNullOrWhiteSpace($manifestLeasePath) -or
            (-not [String]::IsNullOrWhiteSpace($serverLeasePath) -and
             -not (Test-VerifierCanonicalWindowsPathValue $serverLeasePath $manifestLeasePath))) {
        Throw-VerifierInfrastructure 'Integrated child server lease identity was not bijective.'
    }
    if ($serverOwner -eq 'none') {
        if (-not [String]::IsNullOrWhiteSpace([string](Get-VerifierLedgerProperty $server 'baseUrl' '')) -or
            [int](Get-VerifierLedgerProperty $server 'port' 0) -ne 0 -or
            [int](Get-VerifierLedgerProperty $server 'processId' 0) -ne 0 -or
            [long](Get-VerifierLedgerProperty $server 'processStartTicks' 0) -ne 0 -or
            -not [String]::IsNullOrWhiteSpace([string](Get-VerifierLedgerProperty $server 'processCommandLine' '')) -or
            -not [String]::IsNullOrWhiteSpace([string](Get-VerifierLedgerProperty $server 'leaseId' '')) -or
            -not [String]::IsNullOrWhiteSpace($serverLeasePath) -or
            -not [String]::IsNullOrWhiteSpace($serverScript) -or
            -not [String]::IsNullOrWhiteSpace([string](Get-VerifierLedgerProperty $server 'runId' '')) -or
            -not [String]::IsNullOrWhiteSpace([string](Get-VerifierLedgerProperty $server 'nonce' '')) -or
            -not [String]::IsNullOrWhiteSpace([string](Get-VerifierLedgerProperty $server 'stdoutLog' '')) -or
            -not [String]::IsNullOrWhiteSpace([string](Get-VerifierLedgerProperty $server 'stderrLog' ''))) {
            Throw-VerifierInfrastructure 'Integrated child marked a live server resource as ownerless.'
        }
    } elseif ($serverOwner -eq 'caller') {
        # Caller-owned previews are verified non-owned resources. They may be
        # used as the integrated child's BaseUrl, but the verifier must never
        # attach a cleanup lease, adopt their process, or independently stop
        # their listener.
        # Do not use PowerShell truthiness for any proof field. In Windows
        # PowerShell 5.1 a JSON string such as "false" is a non-empty string
        # and therefore casts to $true. Both the child ledger and the
        # persisted manifest are independent ownership evidence, so validate
        # their exact Boolean values before the structural caller checks.
        $callerProofCopies = @(
            [pscustomobject]@{ Label = 'ledger'; Value = $server }
            [pscustomobject]@{ Label = 'manifest'; Value = $manifestServer }
        )
        $callerBooleanProofs = @(
            [pscustomobject]@{ Name = 'identityVerified'; Expected = $true }
            [pscustomobject]@{ Name = 'callerOwned'; Expected = $true }
            [pscustomobject]@{ Name = 'processIdentityKnown'; Expected = $false }
            [pscustomobject]@{ Name = 'processTerminationProven'; Expected = $false }
            [pscustomobject]@{ Name = 'ownershipUncertain'; Expected = $false }
            [pscustomobject]@{ Name = 'processAbsent'; Expected = $true }
            [pscustomobject]@{ Name = 'listenerInspectionProven'; Expected = $false }
            [pscustomobject]@{ Name = 'listenerAbsent'; Expected = $false }
        )
        foreach ($callerProofCopy in $callerProofCopies) {
            foreach ($callerBooleanProof in $callerBooleanProofs) {
                if (-not (Test-VerifierStrictBooleanProperty $callerProofCopy.Value `
                        $callerBooleanProof.Name $callerBooleanProof.Expected)) {
                    Throw-VerifierInfrastructure ('Integrated child caller-owned ' +
                        $callerProofCopy.Label + ' proof field ' +
                        $callerBooleanProof.Name + ' was not the exact Boolean value required.')
                }
            }
            # These are explicitly not-applicable for a caller-owned server,
            # and Write-GateBLedger may persist them as JSON null or omit them.
            # A string or another value must not become an implicit lease proof.
            foreach ($callerNullProofName in @('leaseListenerAbsent',
                    'leaseProcessProofRequired')) {
                $callerNullProofProperty = $callerProofCopy.Value.PSObject.Properties[
                    $callerNullProofName]
                if ($null -ne $callerNullProofProperty -and
                        $null -ne $callerNullProofProperty.Value) {
                    Throw-VerifierInfrastructure ('Integrated child caller-owned ' +
                        $callerProofCopy.Label + ' carried a non-null ' +
                        $callerNullProofName + ' lease proof.')
                }
            }
        }
        $callerPort = [int](Get-VerifierLedgerProperty $server 'port' 0)
        $callerBaseUrl = [string](Get-VerifierLedgerProperty $server 'baseUrl' '')
        $expectedCallerBaseUrl = 'http://127.0.0.1:' + [string]$callerPort
        $expectedCallerScript = Get-VerifierFullPath (Join-Path $worktreeRoot 'scripts\preview.ps1')
        $expectedCallerWebRoot = Get-VerifierFullPath (Join-Path $worktreeRoot 'war')
        if ($callerPort -lt 1 -or $callerPort -gt 65535 -or
                $callerBaseUrl -cne $expectedCallerBaseUrl -or
                -not (Test-VerifierCanonicalWindowsPathValue `
                    ([string](Get-VerifierLedgerProperty $server 'repositoryRoot' '')) $worktreeRoot) -or
                -not (Test-VerifierCanonicalWindowsPathValue `
                    ([string](Get-VerifierLedgerProperty $manifestServer 'repositoryRoot' '')) $worktreeRoot) -or
                -not (Test-VerifierCanonicalWindowsPathValue `
                    ([string](Get-VerifierLedgerProperty $server 'webRoot' '')) $expectedCallerWebRoot) -or
                -not (Test-VerifierCanonicalWindowsPathValue `
                    ([string](Get-VerifierLedgerProperty $manifestServer 'webRoot' '')) $expectedCallerWebRoot) -or
                -not (Test-VerifierCanonicalWindowsPathValue $serverScript $expectedCallerScript) -or
                -not (Test-VerifierCanonicalWindowsPathValue $manifestScript $expectedCallerScript) -or
                [string](Get-VerifierLedgerProperty $server 'identityProtocol' '') -ne 'troubleshootjs-preview-identity-v1' -or
                [string](Get-VerifierLedgerProperty $manifestServer 'identityProtocol' '') -ne 'troubleshootjs-preview-identity-v1') {
            Throw-VerifierInfrastructure 'Integrated child caller-owned preview identity was not an exact verified loopback/root/script/web-root record.'
        }
        if ([int](Get-VerifierLedgerProperty $server 'processId' 0) -ne 0 -or
                [long](Get-VerifierLedgerProperty $server 'processStartTicks' 0) -ne 0 -or
                [int](Get-VerifierLedgerProperty $server 'processParentProcessId' 0) -ne 0 -or
                -not [String]::IsNullOrWhiteSpace([string](Get-VerifierLedgerProperty $server 'processCommandLine' '')) -or
                -not [String]::IsNullOrWhiteSpace([string](Get-VerifierLedgerProperty $server 'runId' '')) -or
                -not [String]::IsNullOrWhiteSpace([string](Get-VerifierLedgerProperty $server 'nonce' '')) -or
                -not [String]::IsNullOrWhiteSpace([string](Get-VerifierLedgerProperty $server 'leaseId' '')) -or
                -not [String]::IsNullOrWhiteSpace([string](Get-VerifierLedgerProperty $server 'leaseKind' '')) -or
                -not [String]::IsNullOrWhiteSpace([string](Get-VerifierLedgerProperty $server 'leaseClaimName' '')) -or
                -not [String]::IsNullOrWhiteSpace([string](Get-VerifierLedgerProperty $server 'leaseClaimState' '')) -or
                -not [String]::IsNullOrWhiteSpace([string](Get-VerifierLedgerProperty $server 'leaseReleaseState' '')) -or
                [int](Get-VerifierLedgerProperty $server 'leaseOwnerPid' 0) -ne 0 -or
                [long](Get-VerifierLedgerProperty $server 'leaseOwnerStartTicks' 0) -ne 0 -or
                -not [String]::IsNullOrWhiteSpace($serverLeasePath) -or
                -not [String]::IsNullOrWhiteSpace([string](Get-VerifierLedgerProperty $server 'stdoutLog' '')) -or
                -not [String]::IsNullOrWhiteSpace([string](Get-VerifierLedgerProperty $server 'stderrLog' '')) -or
                [string](Get-VerifierLedgerProperty $server 'state' '') -ne 'caller-verified' -or
                [string](Get-VerifierLedgerProperty $server 'cleanupResult' '') -ne 'not-owned') {
            Throw-VerifierInfrastructure 'Integrated child caller-owned preview carried an owned-process or cleanup claim.'
        }
    } else {
        if ([String]::IsNullOrWhiteSpace([string](Get-VerifierLedgerProperty $server 'runId' '')) -or
                [string](Get-VerifierLedgerProperty $server 'runId' '') -ne $runId -or
                [String]::IsNullOrWhiteSpace([string](Get-VerifierLedgerProperty $server 'nonce' '')) -or
                [int](Get-VerifierLedgerProperty $server 'port' 0) -lt 1 -or
                [int](Get-VerifierLedgerProperty $server 'port' 0) -gt 65535 -or
                [String]::IsNullOrWhiteSpace($serverScript)) {
            Throw-VerifierInfrastructure 'Integrated child run-owned server omitted its exact run, nonce, port, or script identity.'
        }
        $serverPort = [int](Get-VerifierLedgerProperty $server 'port' 0)
        $expectedBaseUrl = 'http://127.0.0.1:' + [string]$serverPort
        if ([string](Get-VerifierLedgerProperty $server 'baseUrl' '') -cne $expectedBaseUrl -or
                [string](Get-VerifierLedgerProperty $manifestServer 'baseUrl' '') -cne $expectedBaseUrl) {
            Throw-VerifierInfrastructure 'Integrated child run-owned server BaseUrl was not the exact loopback root for its port.'
        }
        $serverProcessId = [int](Get-VerifierLedgerProperty $server 'processId' 0)
        if ($serverProcessId -gt 0 -and
                ([long](Get-VerifierLedgerProperty $server 'processStartTicks' 0) -le 0 -or
                 [int](Get-VerifierLedgerProperty $server 'processParentProcessId' 0) -le 0 -or
                 [long](Get-VerifierLedgerProperty $server 'processParentProcessStartTicks' 0) -le 0 -or
                 [String]::IsNullOrWhiteSpace([string](Get-VerifierLedgerProperty $server 'processCommandLine' '')))) {
            Throw-VerifierInfrastructure 'Integrated child run-owned server had an incomplete current process identity.'
        }
        $serverCommandLine = [string](Get-VerifierLedgerProperty $server 'processCommandLine' '')
        if (-not [String]::IsNullOrWhiteSpace($serverCommandLine) -and
                (-not (Test-VerifierCommandLinePath $serverCommandLine $serverScript) -or
                 -not (Test-VerifierCommandLineSwitch $serverCommandLine '-Port' ([string](Get-VerifierLedgerProperty $server 'port' 0))) -or
                 -not (Test-VerifierCommandLineSwitch $serverCommandLine '-VerifierRunId' $runId) -or
                 -not (Test-VerifierCommandLineSwitch $serverCommandLine '-VerifierNonce' `
                    ([string](Get-VerifierLedgerProperty $server 'nonce' ''))))) {
            Throw-VerifierInfrastructure 'Integrated child run-owned server command identity was foreign or incomplete.'
        }
        if ([String]::IsNullOrWhiteSpace($serverLeasePath) -or
                -not $ledgerClaimPaths.ContainsKey($serverLeasePath.ToLowerInvariant())) {
            Throw-VerifierInfrastructure 'Integrated child run-owned server omitted or foreigned its lease resource.'
        }
        $serverLease = @($ledgerLeases | Where-Object {
            $candidatePath = Get-VerifierLedgerPath ([string](Get-VerifierLedgerProperty $_ 'path' '')) 'server lease' $claimDirectory
            $candidatePath.ToLowerInvariant() -eq $serverLeasePath.ToLowerInvariant()
        })
        if (@($serverLease).Count -ne 1 -or
                [string](Get-VerifierLedgerProperty $server 'leaseId' '') -ne
                    [string](Get-VerifierLedgerProperty $serverLease[0] 'leaseId' '') -or
                [string](Get-VerifierLedgerProperty $server 'leaseKind' '') -ne
                    [string](Get-VerifierLedgerProperty $serverLease[0] 'kind' '') -or
                [string](Get-VerifierLedgerProperty $server 'leaseClaimName' '') -ne
                    [string](Get-VerifierLedgerProperty $serverLease[0] 'claimName' '') -or
                [string](Get-VerifierLedgerProperty $server 'leaseClaimState' '') -ne
                    [string](Get-VerifierLedgerProperty $serverLease[0] 'claimState' '') -or
                [string](Get-VerifierLedgerProperty $server 'leaseReleaseState' '') -ne
                    [string](Get-VerifierLedgerProperty $serverLease[0] 'releaseState' '') -or
                -not (Test-VerifierCanonicalWindowsPathValue $serverLeasePath `
                    (Get-VerifierLedgerPath ([string](Get-VerifierLedgerProperty $serverLease[0] 'path' '')) `
                        'server lease record' $claimDirectory)) -or
                [int](Get-VerifierLedgerProperty $server 'leaseOwnerPid' 0) -ne
                    [int](Get-VerifierLedgerProperty $serverLease[0] 'claimOwnerPid' 0) -or
                [long](Get-VerifierLedgerProperty $server 'leaseOwnerStartTicks' 0) -ne
                    [long](Get-VerifierLedgerProperty $serverLease[0] 'claimOwnerStartTicks' 0) -or
                [string](Get-VerifierLedgerProperty $server 'runId' '') -ne $runId -or
                [string](Get-VerifierLedgerProperty $server 'runId' '') -ne
                    [string](Get-VerifierLedgerProperty $serverLease[0] 'runId' '') -or
                [string](Get-VerifierLedgerProperty $server 'repositoryIdentity' '') -ne
                    $repositoryIdentity -or
                [int](Get-VerifierLedgerProperty $serverLease[0] 'port' 0) -ne
                    [int](Get-VerifierLedgerProperty $server 'port' 0)) {
            Throw-VerifierInfrastructure 'Integrated child server lease identity did not match exactly one lease record.'
        }
        $serverLeasePort = [int](Get-VerifierLedgerProperty $serverLease[0] 'port' 0)
        $serverMutexName = Get-VerifierPortMutexName $null $serverLeasePort
        if ([string](Get-VerifierLedgerProperty $serverLease[0] 'kind' '') -ne 'preview' -or
                [string](Get-VerifierLedgerProperty $server 'leaseKind' '') -ne 'preview' -or
                [string](Get-VerifierLedgerProperty $serverLease[0] 'claimName' '') -ne $serverMutexName -or
                [string](Get-VerifierLedgerProperty $server 'leaseClaimName' '') -ne $serverMutexName -or
                $serverLeasePort -ne [int](Get-VerifierLedgerProperty $server 'port' 0)) {
            Throw-VerifierInfrastructure 'Integrated child run-owned server lease kind, mutex, or port was not canonical.'
        }
        if ($state -eq 'completed') {
            if ([string](Get-VerifierLedgerProperty $server 'cleanupResult' '') -ne 'complete' -or
                    [string](Get-VerifierLedgerProperty $server 'state' '') -ne 'cleaned' -or
                    [int](Get-VerifierLedgerProperty $server 'processId' 0) -le 0 -or
                    [long](Get-VerifierLedgerProperty $server 'processStartTicks' 0) -le 0 -or
                    [String]::IsNullOrWhiteSpace([string](Get-VerifierLedgerProperty $server 'processCommandLine' '')) -or
                    -not (Test-VerifierStrictBooleanProperty $server 'processTerminationProven' $true) -or
                    -not (Test-VerifierStrictBooleanProperty $server 'processAbsent' $true) -or
                    -not (Test-VerifierStrictBooleanProperty $server 'listenerInspectionProven' $true) -or
                    -not (Test-VerifierStrictBooleanProperty $server 'listenerAbsent' $true) -or
                    -not (Test-VerifierStrictBooleanProperty $server 'processIdentityKnown' $true) -or
                    -not (Test-VerifierStrictBooleanProperty $server 'ownershipUncertain' $false) -or
                    (Test-Path -LiteralPath $serverLeasePath -PathType Leaf)) {
                Throw-VerifierInfrastructure 'Completed integrated child did not prove run-owned server shutdown and listener absence.'
            }
        } else {
            if ([string](Get-VerifierLedgerProperty $server 'state' '') -eq 'cleaned' -or
                    [string](Get-VerifierLedgerProperty $server 'cleanupResult' '') -eq 'complete' -or
                    -not (Test-Path -LiteralPath $serverLeasePath -PathType Leaf) -or
                    [string](Get-VerifierLedgerProperty $serverLease[0] 'releaseState' '') -eq 'complete') {
                Throw-VerifierInfrastructure 'Incomplete integrated child did not retain its run-owned server and exact claim resources.'
            }
        }
    }
    if ($state -eq 'completed') {
        # Completion flags are assertions, not evidence. Re-query every
        # recorded owner and listener now, after all bijection/path checks,
        # so a forged processAbsent/listenerAbsent/profile-scan flag cannot
        # turn an unproven cleanup into a successful child result.
        foreach ($lease in $ledgerLeases) {
            $leasePort = [int](Get-VerifierLedgerProperty $lease 'port' 0)
            $boundPid = [int](Get-VerifierLedgerProperty $lease 'boundProcessId' 0)
            $boundStart = [long](Get-VerifierLedgerProperty $lease 'boundProcessStartTicks' 0)
            $listenerPid = [int](Get-VerifierLedgerProperty $lease 'listenerProcessId' 0)
            $listenerStart = [long](Get-VerifierLedgerProperty $lease 'listenerProcessStartTicks' 0)
            $expectedPid = if ($listenerPid -gt 0) { $listenerPid } else { $boundPid }
            $expectedStart = if ($listenerPid -gt 0) { $listenerStart } else { $boundStart }
            $leaseProfile = [string](Get-VerifierLedgerProperty $lease 'profile' '')
            $leaseProcess = [pscustomobject]@{
                ProcessId = $boundPid
                ProcessStartTicks = $boundStart
            }
            $leaseProcessProof = Confirm-VerifierRecordedProcessAbsent $leaseProcess `
                ('completed lease ' + [string](Get-VerifierLedgerProperty $lease 'leaseId' '')) `
                '' $leasePort '' $runId '' $leaseProfile
            if (-not [bool]$leaseProcessProof.QueryProven -or
                    -not [bool]$leaseProcessProof.Absent) {
                Throw-VerifierInfrastructure 'Completed integrated child lease owner process was still present or not independently proven absent.'
            }
            $leaseListenerProof = Confirm-VerifierReleasedListener $leasePort $expectedPid `
                $expectedStart '' 0 '' $runId '' $leaseProfile
            if (-not [bool]$leaseListenerProof.QueryProven -or
                    -not [bool]$leaseListenerProof.OldOwnerAbsent) {
                Throw-VerifierInfrastructure 'Completed integrated child lease listener absence was not independently proven.'
            }
        }
        foreach ($profile in $ledgerProfiles) {
            $profilePath = Get-VerifierLedgerPath ([string](Get-VerifierLedgerProperty $profile 'profile' '')) `
                'completed browser profile' $runRoot
            $profilePid = [int](Get-VerifierLedgerProperty $profile 'processId' 0)
            $profileStart = [long](Get-VerifierLedgerProperty $profile 'processStartTicks' 0)
            $profileProcess = [pscustomobject]@{
                ProcessId = $profilePid
                ProcessStartTicks = $profileStart
                ParentProcessId = [int](Get-VerifierLedgerProperty $profile 'processParentProcessId' 0)
                ParentProcessStartTicks = [long](Get-VerifierLedgerProperty $profile 'processParentProcessStartTicks' 0)
                CommandLine = [string](Get-VerifierLedgerProperty $profile 'processCommandLine' '')
            }
            $profileBrowserPath = [string](Get-VerifierLedgerProperty $profile 'browserPath' '')
            $profileProof = Confirm-VerifierRecordedProcessAbsent $profileProcess `
                ('completed browser profile ' + $profilePath) `
                ([string](Get-VerifierLedgerProperty $profile 'processCommandLine' '')) `
                ([int](Get-VerifierLedgerProperty $profile 'cdpPort' 0)) '' $runId '' $profilePath
            if (-not [bool]$profileProof.QueryProven -or
                    -not [bool]$profileProof.Absent) {
                Throw-VerifierInfrastructure "Completed integrated browser profile process for '$profilePath' was not independently proven absent."
            }
            $profileSnapshot = @(Get-VerifierBrowserProcessSnapshot $profileBrowserPath $profilePath `
                $runId $repositoryIdentity ([int](Get-VerifierLedgerProperty $profile 'cdpPort' 0)))
            $profileReferences = @(Get-VerifierProfileReferenceRecords $profileSnapshot $profilePath)
            if (@($profileReferences).Count -ne 0) {
                Throw-VerifierInfrastructure "Completed integrated browser profile '$profilePath' still had an independently observed process reference."
            }
        }
        if ($serverOwner -eq 'run') {
            $serverProcess = [pscustomobject]@{
                ProcessId = [int](Get-VerifierLedgerProperty $server 'processId' 0)
                ProcessStartTicks = [long](Get-VerifierLedgerProperty $server 'processStartTicks' 0)
                ParentProcessId = [int](Get-VerifierLedgerProperty $server 'processParentProcessId' 0)
                ParentProcessStartTicks = [long](Get-VerifierLedgerProperty $server 'processParentProcessStartTicks' 0)
                CommandLine = [string](Get-VerifierLedgerProperty $server 'processCommandLine' '')
            }
            $serverPort = [int](Get-VerifierLedgerProperty $server 'port' 0)
            $serverProcessProof = Confirm-VerifierRecordedProcessAbsent $serverProcess `
                'completed run-owned preview server' `
                ([string](Get-VerifierLedgerProperty $server 'processCommandLine' '')) `
                $serverPort $serverScript $runId `
                ([string](Get-VerifierLedgerProperty $server 'nonce' '')) ''
            if (-not [bool]$serverProcessProof.QueryProven -or
                    -not [bool]$serverProcessProof.Absent) {
                Throw-VerifierInfrastructure 'Completed integrated run-owned preview server process was still present or not independently proven absent.'
            }
            $serverListenerProof = Confirm-VerifierReleasedListener $serverPort `
                ([int](Get-VerifierLedgerProperty $server 'processId' 0)) `
                ([long](Get-VerifierLedgerProperty $server 'processStartTicks' 0)) `
                ([string](Get-VerifierLedgerProperty $server 'processCommandLine' '')) `
                ([int](Get-VerifierLedgerProperty $server 'processParentProcessId' 0)) `
                $serverScript $runId `
                ([string](Get-VerifierLedgerProperty $server 'nonce' '')) '' `
                ([long](Get-VerifierLedgerProperty $server 'processParentProcessStartTicks' 0))
            if (-not [bool]$serverListenerProof.QueryProven -or
                    -not [bool]$serverListenerProof.OldOwnerAbsent) {
                Throw-VerifierInfrastructure 'Completed integrated run-owned preview listener absence was not independently proven.'
            }
            # Listener absence alone does not prove that a preview helper or
            # descendant with the same run/script/port identity exited. Scan
            # the current relevant process set independently of the recorded
            # root flags; any surviving candidate keeps the child incomplete.
            $remainingServerProcesses = @(Get-VerifierBrowserProcessSnapshot '' '' $runId `
                $repositoryIdentity $serverPort $serverScript `
                ([string](Get-VerifierLedgerProperty $server 'nonce' '')))
            if (@($remainingServerProcesses).Count -ne 0) {
                Throw-VerifierInfrastructure 'Completed integrated run-owned preview still had a relevant run-marked process.'
            }
        }
    }
    $cleanup = Get-VerifierLedgerProperty $ledger 'cleanupState' ''
    if ($state -eq 'completed') {
        if ([string]$cleanup -ne 'complete' -or
                @(Get-VerifierLedgerProperty $ledger 'cleanupErrors' @()).Count -ne 0) {
            Throw-VerifierInfrastructure 'Integrated child ledger claimed completion with cleanup errors.'
        }
    } elseif ($AllowIncomplete -and $state -eq 'cleanup-failed' -and
            @(Get-VerifierLedgerProperty $ledger 'cleanupErrors' @()).Count -eq 0) {
        Throw-VerifierInfrastructure 'Incomplete cleanup-failed child ledger omitted the required retained cleanup error/evidence record.'
    } elseif (-not $AllowIncomplete) {
        Throw-VerifierInfrastructure 'Only the explicit parent-timeout path may accept an incomplete child ledger.'
    }
    return $ledger
}

function Invoke-VerifierIntegratedTimeoutResourceProof($Ledger,
        [int]$WaitMilliseconds = 5000) {
    if ($null -eq $Ledger -or $WaitMilliseconds -lt 1) {
        Throw-VerifierInfrastructure 'Integrated timeout cleanup requires a ledger and a positive process bound.'
    }
    $runRoot = Get-VerifierLedgerPath ([string](Get-VerifierLedgerProperty $Ledger 'runRoot' '')) `
        'timeout child run root' `
        (Get-VerifierLedgerPath ([string](Get-VerifierLedgerProperty $Ledger 'parentNamespaceRoot' '')) `
            'timeout parent namespace' `
            (Get-VerifierFullPath (Join-Path ([IO.Path]::GetTempPath()) 'TroubleshootJS\verify')))
    $claimDirectory = Get-VerifierFullPath (Join-Path $runRoot 'port-leases')
    if (-not (Test-Path -LiteralPath $runRoot -PathType Container) -or
            -not (Test-Path -LiteralPath $claimDirectory -PathType Container)) {
        Throw-VerifierInfrastructure 'Integrated timeout cleanup did not retain its canonical run/claim namespace.'
    }
    Assert-VerifierNoReparseAncestors $runRoot
    $runId = [string](Get-VerifierLedgerProperty $Ledger 'runId' '')
    $repositoryIdentity = [string](Get-VerifierLedgerProperty $Ledger 'repositoryIdentity' '')
    $stopped = New-Object Collections.ArrayList
    try {
        # Profiles are retained on a parent timeout. The parent may stop only a
        # process whose current WMI record, start identity, parent, and exact
        # run/profile/port markers still match the child ledger.
        foreach ($profile in @(
                if ($null -eq (Get-VerifierLedgerProperty $Ledger 'profiles' $null)) {
                    @()
                } else { @(Get-VerifierLedgerProperty $Ledger 'profiles' @()) }
        )) {
            $profilePath = Get-VerifierLedgerPath ([string](Get-VerifierLedgerProperty $profile 'profile' '')) `
                'timeout browser profile' $runRoot
            if (-not (Test-Path -LiteralPath $profilePath -PathType Container)) {
                Throw-VerifierInfrastructure "Integrated timeout browser profile was not retained: $profilePath"
            }
            Assert-VerifierNoReparseTree $profilePath
            $profilePid = [int](Get-VerifierLedgerProperty $profile 'processId' 0)
            $profileStart = [long](Get-VerifierLedgerProperty $profile 'processStartTicks' 0)
            $profileParent = [int](Get-VerifierLedgerProperty $profile 'processParentProcessId' 0)
            $profileParentStart = [long](Get-VerifierLedgerProperty $profile 'processParentProcessStartTicks' 0)
            $profileCommand = [string](Get-VerifierLedgerProperty $profile 'processCommandLine' '')
            $profilePort = [int](Get-VerifierLedgerProperty $profile 'cdpPort' 0)
            $profileBrowserPath = [string](Get-VerifierLedgerProperty $profile 'browserPath' '')
            if ($profilePid -gt 0 -and ($profileStart -le 0 -or $profileParent -le 0 -or
                    $profileParentStart -le 0 -or
                    [String]::IsNullOrWhiteSpace($profileCommand) -or $profilePort -lt 1 -or
                    $profilePort -gt 65535)) {
                Throw-VerifierInfrastructure 'Integrated timeout browser profile omitted a complete process/port identity.'
            }
            $owner = [pscustomobject]@{
                ProcessId = $profilePid; ProcessStartTicks = $profileStart
                ProcessParentProcessId = $profileParent; ProcessCommandLine = $profileCommand
                ProcessParentProcessStartTicks = $profileParentStart
                Profile = $profilePath; RunId = $runId
                RepositoryIdentity = $repositoryIdentity; CdpPort = $profilePort
                BrowserPath = $profileBrowserPath
            }
            $snapshot = @(Get-VerifierBrowserOwnershipSnapshot $profileBrowserPath $profilePath `
                $runId $repositoryIdentity $profilePort)
            $descendants = @()
            if ($profilePid -gt 0) {
                $profileRootSnapshot = @($snapshot | Where-Object {
                    [int]$_.ProcessId -eq $profilePid
                } | Select-Object -First 1)
                if ($profileRootSnapshot.Count -ne 1) {
                    Throw-VerifierInfrastructure 'Integrated timeout browser root was absent from the complete ownership snapshot.'
                }
                $profileRootRecord = [pscustomobject]@{
                    ProcessId = $profilePid
                    ProcessStartTicks = $profileStart
                    CommandLine = $profileCommand
                    ParentProcessId = $profileParent
                    ParentProcessStartTicks = $profileParentStart
                    VerifierDepth = 0
                }
                # Prove the current root identity before any descendant can be
                # considered stoppable. A stale/reparented root blocks the
                # whole timeout cleanup and leaves its claim/profile retained.
                [void](Get-VerifierCurrentOwnedProcess $owner $profileRootRecord -Root)
                $descendants = @(Get-VerifierDescendantProcessRecords $owner $snapshot)
            } else {
                # A zero/stale root PID never waives the complete profile scan.
                # Relevant candidates are still inspected below and any exact
                # profile reference prevents cleanup.
                $descendants = @()
            }
            foreach ($child in @($descendants | Sort-Object `
                    @{Expression={ if ($_.PSObject.Properties['VerifierDepth']) {
                        [int]$_.VerifierDepth } else { 0 } }; Descending=$true},
                    @{Expression={ [int]$_.ProcessId }; Descending=$true})) {
                $currentChild = Get-VerifierCurrentOwnedProcess $owner $child
                [void](Stop-VerifierVerifiedProcessExactly $currentChild.Process `
                    ([long]$currentChild.Record.ProcessStartTicks) $WaitMilliseconds `
                    $currentChild.Record)
                [void]$stopped.Add([int]$child.ProcessId)
            }
            if ($profilePid -gt 0) {
                $currentRootRecord = Get-VerifierCurrentProcessRecordById $profilePid
                if ($null -ne $currentRootRecord) {
                    $currentRootRecord | Add-Member -NotePropertyName ParentProcessStartTicks `
                        -NotePropertyValue (Get-VerifierCurrentParentStartTicks `
                            ([int]$currentRootRecord.ParentProcessId)) -Force
                    $currentRoot = Get-VerifierCurrentOwnedProcess $owner $currentRootRecord -Root
                    [void](Stop-VerifierVerifiedProcessExactly $currentRoot.Process `
                        ([long]$currentRoot.Record.ProcessStartTicks) $WaitMilliseconds `
                        $currentRoot.Record)
                    [void]$stopped.Add($profilePid)
                }
            }
            # Re-query after all exact stops; a late or foreign equivalent-path
            # helper remains a hard infrastructure failure and the profile is
            # deliberately retained for diagnosis.
            $finalProfileSnapshot = @(Get-VerifierBrowserProcessSnapshot $profileBrowserPath `
                $profilePath $runId $repositoryIdentity $profilePort)
            Assert-VerifierProfileSnapshotQuiescent $finalProfileSnapshot $profilePath
        }

        $server = Get-VerifierLedgerProperty $Ledger 'server' $null
        if ($null -ne $server -and [string](Get-VerifierLedgerProperty $server 'owner' '') -eq 'run') {
            $serverPid = [int](Get-VerifierLedgerProperty $server 'processId' 0)
            $serverPort = [int](Get-VerifierLedgerProperty $server 'port' 0)
            if ($serverPort -lt 1 -or $serverPort -gt 65535) {
                Throw-VerifierInfrastructure 'Integrated timeout server carried an invalid port.'
            }
            if ($serverPid -le 0) {
                # A run-owned preview may have acquired a claim before its
                # process identity was durably captured.  On a parent timeout
                # that is an unresolved survival/ownership state: do not infer
                # that no server exists merely from PID=0.  Retain the ledger,
                # claim, and evidence for a later exact recovery attempt.
                Throw-VerifierInfrastructure 'Integrated timeout run-owned server omitted a positive process identity; listener/process ownership cannot be proven.'
            }
            if ($serverPid -gt 0) {
                $serverStart = [long](Get-VerifierLedgerProperty $server 'processStartTicks' 0)
                $serverParent = [int](Get-VerifierLedgerProperty $server 'processParentProcessId' 0)
                $serverParentStart = [long](Get-VerifierLedgerProperty $server 'processParentProcessStartTicks' 0)
                $serverCommand = [string](Get-VerifierLedgerProperty $server 'processCommandLine' '')
                $serverScript = [string](Get-VerifierLedgerProperty $server 'script' '')
                $serverNonce = [string](Get-VerifierLedgerProperty $server 'nonce' '')
                if ($serverStart -le 0 -or $serverParent -le 0 -or $serverParentStart -le 0 -or
                        [String]::IsNullOrWhiteSpace($serverCommand) -or
                        [String]::IsNullOrWhiteSpace($serverScript) -or
                        [String]::IsNullOrWhiteSpace($serverNonce)) {
                    Throw-VerifierInfrastructure 'Integrated timeout server omitted its exact process identity.'
                }
                $serverOwner = [pscustomobject]@{
                    ProcessId = $serverPid; ProcessStartTicks = $serverStart
                    ProcessParentProcessId = $serverParent
                    ProcessParentProcessStartTicks = $serverParentStart
                    ProcessCommandLine = $serverCommand
                    Script = $serverScript; Port = $serverPort
                    RunId = $runId; Nonce = $serverNonce
                    RepositoryIdentity = $repositoryIdentity
                }
                # Establish the current preview-root identity before any
                # descendant is considered stoppable. A reparented or
                # repurposed same-PID wrapper must block the entire timeout
                # cleanup, even if an old helper still carries the run marker.
                [void](Get-VerifierCurrentProcessIdentity $serverPid $serverStart `
                    $serverParent $serverCommand $serverScript $serverPort $runId `
                    $serverNonce $serverParentStart)
                # Use the full candidate snapshot for PPID traversal. A
                # preview descendant may have a different executable name,
                # but it must still carry the exact script/port/run/nonce
                # identity before the parent can stop it.
                $serverSnapshot = @(Get-VerifierBrowserOwnershipSnapshot '' '' `
                    $runId $repositoryIdentity $serverPort $serverScript $serverNonce)
                if ($serverPid -gt 0) {
                    $serverDescendants = @(Get-VerifierDescendantProcessRecords `
                        $serverOwner $serverSnapshot)
                    foreach ($serverChild in @($serverDescendants |
                            Sort-Object `
                            @{Expression={ if ($_.PSObject.Properties['VerifierDepth']) {
                                [int]$_.VerifierDepth } else { 0 } }; Descending=$true},
                            @{Expression={ [int]$_.ProcessId }; Descending=$true})) {
                        $currentServerChild = Get-VerifierCurrentOwnedProcess $serverOwner `
                            $serverChild
                        [void](Stop-VerifierVerifiedProcessExactly $currentServerChild.Process `
                            ([long]$currentServerChild.Record.ProcessStartTicks) $WaitMilliseconds `
                            $currentServerChild.Record)
                        [void]$stopped.Add([int]$serverChild.ProcessId)
                    }
                    $currentServerRecord = Get-VerifierCurrentProcessRecordById $serverPid
                    if ($null -ne $currentServerRecord) {
                        $currentServer = Get-VerifierCurrentProcessIdentity $serverPid $serverStart `
                            $serverParent $serverCommand $serverScript $serverPort $runId $serverNonce `
                            $serverParentStart
                        [void](Stop-VerifierVerifiedProcessExactly $currentServer.Process `
                            $serverStart $WaitMilliseconds $currentServer.Record)
                        [void]$stopped.Add($serverPid)
                    }
                }
                $serverFinalSnapshot = @(Get-VerifierBrowserOwnershipSnapshot '' '' `
                    $runId $repositoryIdentity $serverPort $serverScript $serverNonce)
                $remainingServerProcesses = @(Select-VerifierRelevantProcessRecords `
                    $serverFinalSnapshot '' '' $runId $repositoryIdentity $serverPort `
                    $serverScript $serverNonce)
                if (@($remainingServerProcesses).Count -ne 0) {
                    Throw-VerifierInfrastructure 'Integrated timeout cleanup left a run-owned preview process after exact descendant/root proof.'
                }
            }
            $serverInspection = Get-VerifierLoopbackListenerRecords $serverPort
            if (-not $serverInspection.Success -or -not $serverInspection.Known -or
                    $serverInspection.HasListeners) {
                Throw-VerifierInfrastructure 'Integrated timeout cleanup could not prove run-owned server listener absence.'
            }
        }

        # Claims remain durable evidence after a parent timeout. Re-read each
        # exact descriptor and positively prove the old port is not listening;
        # never remove a claim or a newer run's mutex/port resource here.
        foreach ($lease in @(
                if ($null -eq (Get-VerifierLedgerProperty $Ledger 'leases' $null)) {
                    @()
                } else { @(Get-VerifierLedgerProperty $Ledger 'leases' @()) }
        )) {
            $leaseId = [string](Get-VerifierLedgerProperty $lease 'leaseId' '')
            $leasePath = Get-VerifierLedgerPath ([string](Get-VerifierLedgerProperty $lease 'path' '')) `
                ('timeout lease ' + $leaseId) $claimDirectory
            if (-not (Test-Path -LiteralPath $leasePath -PathType Leaf)) {
                Throw-VerifierInfrastructure "Integrated timeout lease $leaseId was not retained as exact evidence."
            }
            $claim = Get-Content -LiteralPath $leasePath -Raw -ErrorAction Stop | ConvertFrom-Json
            $leasePort = [int](Get-VerifierLedgerProperty $lease 'port' 0)
            if ([string](Get-VerifierLedgerProperty $claim 'runId' '') -ne $runId -or
                    [string](Get-VerifierLedgerProperty $claim 'leaseId' '') -ne $leaseId -or
                    [int](Get-VerifierLedgerProperty $claim 'port' 0) -ne $leasePort -or
                    [string](Get-VerifierLedgerProperty $claim 'mutexName' '') -ne
                        (Get-VerifierPortMutexName $null $leasePort)) {
                Throw-VerifierInfrastructure "Integrated timeout lease $leaseId claim identity changed or was foreign."
            }
            $claimOwnerPid = [int](Get-VerifierLedgerProperty $claim 'ownerPid' 0)
            $claimOwnerStart = [long](Get-VerifierLedgerProperty $claim 'ownerStartTicks' 0)
            if ($claimOwnerPid -le 0 -or $claimOwnerStart -le 0) {
                Throw-VerifierInfrastructure "Integrated timeout lease $leaseId omitted its positive owner identity."
            }
            $claimOwnerProof = Confirm-VerifierRecordedProcessAbsent `
                ([pscustomobject]@{
                    ProcessId = $claimOwnerPid
                    ProcessStartTicks = $claimOwnerStart
                }) ('timeout lease ' + $leaseId + ' claim owner')
            if (-not [bool]$claimOwnerProof.QueryProven -or
                    -not [bool]$claimOwnerProof.Absent) {
                Throw-VerifierInfrastructure "Integrated timeout lease $leaseId owner process remained present or was not proven absent."
            }
            $inspection = Get-VerifierLoopbackListenerRecords $leasePort
            if (-not $inspection.Success -or -not $inspection.Known -or $inspection.HasListeners) {
                Throw-VerifierInfrastructure "Integrated timeout lease $leaseId did not positively prove listener absence."
            }
            Assert-VerifierNoReparseAncestors $claimDirectory
            if (-not (Test-VerifierPhysicalChildPath $claimDirectory $leasePath)) {
                Throw-VerifierInfrastructure "Integrated timeout lease $leaseId claim escaped its physical namespace."
            }
        }
        return [pscustomobject]@{
            Success = $true; RetainedEvidence = $true; StoppedProcessIds = @($stopped)
            ClaimDeletionDeferred = $true
        }
    } catch {
        if (Test-VerifierInfrastructureError $_) { throw }
        Throw-VerifierInfrastructure ('Integrated timeout resource ownership proof failed: ' +
            (Get-VerifierErrorMessage $_))
    }
}

function invokeIntegratedChild([string]$label, [string[]]$routeArguments,
        [int]$expectedExit = 0, [string]$childBrowserPath = '') {
    if ([String]::IsNullOrWhiteSpace($childBrowserPath)) {
        $childBrowserPath = $script:VerifierResolvedBrowserPath
    }
    try {
        $hostExecutable = getIntegratedPowerShellExecutable
    } catch {
        Throw-VerifierExit 2 "FAIL integrated child $label - verifier infrastructure: $($_.Exception.Message)"
    }
    $childLedgerPath = New-VerifierIntegratedChildLedger $label $expectedExit
    $quotePowerShellArgument = {
        param([string]$value)
        if ($null -eq $value) { return "''" }
        return "'" + $value.Replace("'", "''") + "'"
    }
    $commandParts = @('&', (& $quotePowerShellArgument $PSCommandPath),
        '-BaseUrl', (& $quotePowerShellArgument $BaseUrl),
        '-TimeoutSeconds', (& $quotePowerShellArgument ([string]$TimeoutSeconds)),
        '-BrowserPath', (& $quotePowerShellArgument $childBrowserPath),
        '-ParentLedgerPath', (& $quotePowerShellArgument $childLedgerPath),
        '-ParentNamespaceRoot', (& $quotePowerShellArgument (Split-Path -Parent $childLedgerPath)))
    for ($argumentIndex = 0; $argumentIndex -lt $routeArguments.Count; $argumentIndex++) {
        $routeArgument = [string]$routeArguments[$argumentIndex]
        if ($routeArgument -eq '-Seeds') {
            if ($argumentIndex + 1 -ge $routeArguments.Count) {
                Throw-VerifierExit 2 "FAIL integrated child $label - verifier infrastructure: -Seeds has no value"
            }
            $argumentIndex++
            $seedExpression = [string]$routeArguments[$argumentIndex]
            if ($seedExpression -notmatch '^\d+(,\d+)*$') {
                Throw-VerifierExit 2 "FAIL integrated child $label - verifier infrastructure: unsupported -Seeds expression '$seedExpression'"
            }
            # Keep numeric comma lists as PowerShell expressions so [int[]]$Seeds
            # receives every value instead of one string that coerces to 23.
            $commandParts += @('-Seeds', $seedExpression)
        } elseif ($routeArgument.StartsWith('-')) {
            $commandParts += $routeArgument
        } else {
            $commandParts += (& $quotePowerShellArgument $routeArgument)
        }
    }
    $childArguments = @('-NoProfile', '-ExecutionPolicy', 'Bypass', '-Command',
        (($commandParts -join ' ') + '; $tsjChildSuccess = $?; $tsjChildExit = $LASTEXITCODE; if ($tsjChildSuccess) { exit 0 } elseif ($tsjChildExit -eq 1 -or $tsjChildExit -eq 2) { exit $tsjChildExit } else { exit 2 }'))
    Write-Host ("INTEGRATED CHILD START $label expected-exit=$expectedExit")
    $childProcess = $null
    $terminationProven = $false
    $parentTimeoutPath = $false
    $childOutput = @()
    $childExit = 2
    $failure = $null
    $cleanupFailure = $null
    $timeoutResourceProof = $null
    $stdoutTask = $null
    $stderrTask = $null
    $stdout = ''
    $stderr = ''
    $outputCaptured = $false
    try {
        $childProcess = Start-VerifierIntegratedChildProcess $hostExecutable $childArguments
        $stdoutTask = $childProcess.StandardOutput.ReadToEndAsync()
        $stderrTask = $childProcess.StandardError.ReadToEndAsync()
        $waitMilliseconds = if ($routeArguments -contains '-GateBHangAfterContext') {
            5000
        } else {
            [int][Math]::Min(600000,
                [Math]::Max(60000, (($TimeoutSeconds + 30) * 1000)))
        }
        if (-not $childProcess.WaitForExit($waitMilliseconds)) {
            $parentTimeoutPath = $true
            Throw-VerifierInfrastructure "Integrated child $label did not terminate within $waitMilliseconds milliseconds."
        }
        $childProcess.Refresh()
        if (-not [bool]$childProcess.HasExited) {
            Throw-VerifierInfrastructure "Integrated child $label termination was not proven after WaitForExit."
        }
        $terminationProven = $true
        if (-not $stdoutTask.Wait(5000) -or -not $stderrTask.Wait(5000)) {
            Throw-VerifierInfrastructure "Integrated child $label output streams did not close within the post-termination bound."
        }
        $stdout = [string]$stdoutTask.GetAwaiter().GetResult()
        $stderr = [string]$stderrTask.GetAwaiter().GetResult()
        $outputCaptured = $true
        if (-not [String]::IsNullOrEmpty([string]$stdout)) {
            $childOutput += @(([string]$stdout) -split "`r?`n")
        }
        if (-not [String]::IsNullOrEmpty([string]$stderr)) {
            $childOutput += @(([string]$stderr) -split "`r?`n")
        }
        $childProcess.Refresh()
        if (-not [bool]$childProcess.HasExited) {
            Throw-VerifierInfrastructure "Integrated child $label changed to an unproven running state while capturing output."
        }
        $childExit = Resolve-VerifierIntegratedChildExitCode $terminationProven `
            $childProcess.ExitCode
    } catch {
        $failure = $_
    } finally {
        if ($null -ne $childProcess) {
            if (-not $terminationProven) {
                try {
                    Stop-VerifierIntegratedChildExact $childProcess
                    # The exact stop helper proves WaitForExit/Refresh/HasExited
                    # and current-process absence.  Only after it returns may
                    # the parent run the ledger/resource timeout proof.
                    $terminationProven = $true
                } catch {
                    $cleanupFailure = Get-VerifierErrorMessage $_
                }
            }
            # A normal exit, exit-code/protocol failure, or parent timeout all
            # use the same bounded final-handle and redirected-stream proof.
            # If this proof fails, the child ledger is not trusted and the
            # caller receives infrastructure exit 2 with evidence retained.
            try {
                if (-not $childProcess.WaitForExit(5000)) {
                    Throw-VerifierInfrastructure "Integrated child $label did not complete its final WaitForExit during cleanup."
                }
                $childProcess.Refresh()
                if (-not [bool]$childProcess.HasExited) {
                    Throw-VerifierInfrastructure "Integrated child $label remained alive during cleanup."
                }
                $currentAfterCleanup = Get-VerifierCurrentProcessRecordById ([int]$childProcess.Id)
                if ($null -ne $currentAfterCleanup) {
                    Throw-VerifierInfrastructure "Integrated child $label still had a current process identity during cleanup."
                }
                if ($stdoutTask -and $stderrTask) {
                    if (-not $stdoutTask.Wait(5000) -or -not $stderrTask.Wait(5000)) {
                        Throw-VerifierInfrastructure "Integrated child $label output streams did not close during cleanup."
                    }
                    if (-not $outputCaptured) {
                        $stdout = [string]$stdoutTask.GetAwaiter().GetResult()
                        $stderr = [string]$stderrTask.GetAwaiter().GetResult()
                        $outputCaptured = $true
                        if (-not [String]::IsNullOrEmpty($stdout)) {
                            $childOutput += @($stdout -split "`r?`n")
                        }
                        if (-not [String]::IsNullOrEmpty($stderr)) {
                            $childOutput += @($stderr -split "`r?`n")
                        }
                    }
                }
            } catch {
                $cleanupMessage = Get-VerifierErrorMessage $_
                if ($cleanupFailure) { $cleanupFailure += '; ' + $cleanupMessage }
                else { $cleanupFailure = $cleanupMessage }
            }
            if ($parentTimeoutPath -and $terminationProven -and -not $cleanupFailure) {
                try {
                    # The wrapper's finally block cannot run after a parent
                    # timeout. Use its durable parent-visible ledger to prove
                    # each exact child process/profile/listener/claim resource;
                    # claims and evidence remain retained for recovery.
                    $timeoutLedger = Read-VerifierIntegratedChildLedger `
                        $childLedgerPath $expectedExit -AllowIncomplete
                    $timeoutResourceProof = Invoke-VerifierIntegratedTimeoutResourceProof `
                        $timeoutLedger 5000
                } catch {
                    $cleanupFailure = 'timeout resource cleanup was not proven: ' +
                        (Get-VerifierErrorMessage $_)
                }
            }
            try { $childProcess.Dispose() } catch {
                $disposeMessage = Get-VerifierErrorMessage $_
                if ($cleanupFailure) { $cleanupFailure += '; ' + $disposeMessage }
                else { $cleanupFailure = $disposeMessage }
            }
        }
    }
    if ($cleanupFailure) {
        if ($parentTimeoutPath) {
            try {
                # A timeout may retain an incomplete ledger, but it must still
                # prove that the child left its manifest/evidence/active claims
                # behind for diagnosis.  This path always throws infrastructure
                # below; it can never turn a timeout into success.
                [void](Read-VerifierIntegratedChildLedger $childLedgerPath $expectedExit -AllowIncomplete)
            } catch {
                $cleanupFailure += '; retained timeout ledger proof: ' +
                    (Get-VerifierErrorMessage $_)
            }
        }
        $failureText = if ($failure) { Get-VerifierErrorMessage $failure } else {
            'unknown integrated child failure'
        }
        Throw-VerifierInfrastructure ('Integrated child cleanup was not proven: ' +
            $failureText + '; cleanup: ' + $cleanupFailure)
    }
    if ($failure) {
        try {
            if ($parentTimeoutPath) {
                $retainedLedger = Read-VerifierIntegratedChildLedger $childLedgerPath `
                    $expectedExit -AllowIncomplete
                if ([string]$retainedLedger.state -ne 'completed') {
                    Write-Host ("INTEGRATED CHILD TIMEOUT EVIDENCE RETAINED $label ledger=$childLedgerPath " +
                        "runRoot=$($retainedLedger.runRoot) resourceProof=$([bool]($null -ne $timeoutResourceProof))")
                }
            } else {
                # A process that terminated (even with expected infrastructure
                # exit 2) must have completed its own ledger cleanup before the
                # parent accepts its result.
                [void](Read-VerifierIntegratedChildLedger $childLedgerPath $expectedExit)
            }
        } catch {
            Throw-VerifierInfrastructure ('Integrated child status failure lacked the required parent ledger proof: ' +
                (Get-VerifierErrorMessage $_))
        }
        if (Test-VerifierInfrastructureError $failure) { throw $failure }
        Throw-VerifierInfrastructure ('Integrated child status could not be proven: ' +
            (Get-VerifierErrorMessage $failure))
    }
    $childLedger = Read-VerifierIntegratedChildLedger $childLedgerPath $expectedExit
    Write-Host ("INTEGRATED CHILD LEDGER $label state=$($childLedger.state) path=$childLedgerPath")
    foreach ($line in $childOutput) { Write-Host ([string]$line) }
    Assert-IntegratedChildOutputContract $label $expectedExit $childExit $childOutput
    Write-Host ("PASS integrated child $label exit=$childExit")
}

function InvokeVerifierMain {
$script:VerifierFailureExitCode = 0
$script:VerifierResolvedBrowserPath = Resolve-VerifierBrowserPath $BrowserPath
$npnValidationSeeds = if ($script:VerifierBoundParameters.ContainsKey('Seeds')) { $Seeds } else {
    @(0, 1, 2, 3)
}
$nmosValidationSeeds = if ($script:VerifierBoundParameters.ContainsKey('Seeds')) { $Seeds } else {
    @(0, 1, 2)
}
$npnNormalPlayerSeeds = if ($script:VerifierBoundParameters.ContainsKey('Seeds')) { $Seeds } else {
    @(0, 1, 2)
}
if (-not (Test-Path -LiteralPath $script:VerifierResolvedBrowserPath -PathType Leaf)) {
    $missingBrowserRoute = if ($Task43PForcedNegative) { 'task43p forced-negative canary' }
        elseif ($Task43P) { 'task43p' }
        elseif ($Task43ForcedNegative) { 'task43 forced-negative canary' }
        elseif ($Task43Integrated) { 'task43 integrated' }
        elseif ($Task43) { 'task43' }
        else { 'browser verifier' }
    Throw-VerifierInfrastructure "Browser not found for $missingBrowserRoute`: $BrowserPath"
}
if ($Task43Integrated) {
    # This is the scripted regression authority, not built-in Browser evidence.
    # Each positive lane is a separate child process and must return 0.  The
    # final three children prove the process contract: a real forced negative
    # returns 1 only with its exact DOM marker plus anchored Java diagnostic;
    # missing-browser infrastructure returns 2; ordinary Task43 then returns 0.
    $integratedRoutes = @(
        @{ Label = '-Task43'; Args = @('-Task43') },
        @{ Label = '-Layout -PlayerSeed 3'; Args = @('-Layout', '-PlayerSeed', '3') },
        @{ Label = '-Task39'; Args = @('-Task39') },
        @{ Label = '-Task40'; Args = @('-Task40') },
        @{ Label = '-Task41'; Args = @('-Task41') },
        @{ Label = '-Rc -Seeds 0,2,3'; Args = @('-Rc', '-Seeds', '0,2,3') },
        @{ Label = '-StoredEnergy -Seeds 0,2,3'; Args = @('-StoredEnergy', '-Seeds', '0,2,3') },
        @{ Label = '-Rc -StoredEnergy -Seeds 3'; Args = @('-Rc', '-StoredEnergy', '-Seeds', '3') },
        @{ Label = '-Npn -Seeds 0,1,2,3'; Args = @('-Npn', '-Seeds', '0,1,2,3') },
        @{ Label = '-NpnNatural'; Args = @('-NpnNatural') },
        @{ Label = '-Nmos -Seeds 0,1,2'; Args = @('-Nmos', '-Seeds', '0,1,2') },
        @{ Label = '-NmosNatural'; Args = @('-NmosNatural') },
        @{ Label = '-LedParts -Seeds 0,2,3,4'; Args = @('-LedParts', '-Seeds', '0,2,3,4') },
        @{ Label = '-Diode -Seeds 0,2,3'; Args = @('-Diode', '-Seeds', '0,2,3') },
        @{ Label = '-DiodeShort -Seeds 0,2,3'; Args = @('-DiodeShort', '-Seeds', '0,2,3') },
        @{ Label = '-Parallel -Seeds 0,2,3'; Args = @('-Parallel', '-Seeds', '0,2,3') },
        @{ Label = 'legacy default -Seeds 0,2,3'; Args = @('-Seeds', '0,2,3') },
        @{ Label = '-WrongRepair'; Args = @('-WrongRepair') },
        @{ Label = '-QuickPlay'; Args = @('-QuickPlay') },
        @{ Label = '-NormalPlayer -PlayerSeed 3'; Args = @('-NormalPlayer', '-PlayerSeed', '3') },
        @{ Label = '-WrongRepairNormalPlayer'; Args = @('-WrongRepairNormalPlayer') },
        @{ Label = '-LedNormalPlayer -PlayerSeed 4'; Args = @('-LedNormalPlayer', '-PlayerSeed', '4') },
        @{ Label = '-DiodeNormalPlayer -PlayerSeed 0'; Args = @('-DiodeNormalPlayer', '-PlayerSeed', '0') },
        @{ Label = '-ParallelNormalPlayer'; Args = @('-ParallelNormalPlayer') },
        @{ Label = '-RcNormalPlayer -PlayerSeed 0'; Args = @('-RcNormalPlayer', '-PlayerSeed', '0') }
    )
    foreach ($integratedRoute in $integratedRoutes) {
        invokeIntegratedChild $integratedRoute.Label $integratedRoute.Args 0
    }
    invokeIntegratedChild '-Task43ForcedNegative (expected exit 1)' @('-Task43ForcedNegative') 1
    $missingBrowserPath = Join-Path $env:TEMP (
        'troubleshootjs-missing-browser-' + [Guid]::NewGuid().ToString('N') + '.exe')
    invokeIntegratedChild '-Task43 missing BrowserPath (expected exit 2)' @('-Task43') 2 $missingBrowserPath
    invokeIntegratedChild '-Task43 post-process-contract' @('-Task43') 0
    Write-Host 'Integrated Task 43 browser/regression orchestration passed.'
    return 0
}
if ($QuickPlay) {
    $selectorPassed = verifyRoute 'quick-play selector/session' "$BaseUrl/circuitjs.html?tsjQuickPlay=true&tsjVerifyQuickPlay=true&tsjQuickPlayTestSeed=3" 'PASS:quick-play' | Select-Object -Last 1
    if (-not $selectorPassed) { return (Get-VerifierRouteFailureExitCode) }
    $rcFinishPassed = verifyRoute 'quick-play rc finish' "$BaseUrl/circuitjs.html?tsjQuickPlay=true&tsjVerifyQuickPlay=true&tsjQuickPlayTestFamily=3&tsjQuickPlayTestSeed=3" 'PASS:quick-play' | Select-Object -Last 1
    if (-not $rcFinishPassed) { return (Get-VerifierRouteFailureExitCode) }
    $explicitPassed = verifyRoute 'quick-play explicit precedence' "$BaseUrl/circuitjs.html?tsjQuickPlay=true&tsjChallenge=led&seed=3&tsjVerifyQuickPlay=true&tsjQuickPlayTestSeed=3" 'PASS:quick-play-explicit' | Select-Object -Last 1
    if (-not $explicitPassed) { return (Get-VerifierRouteFailureExitCode) }
    $normalPassed = verifyQuickPlayNormalPlayer "$BaseUrl/circuitjs.html?tsjQuickPlay=true" ([bool]$selectorPassed) | Select-Object -Last 1
    if (-not $normalPassed) { return (Get-VerifierRouteFailureExitCode) }
    $npnQuickPlayCase = 0
    foreach ($seed in $npnNormalPlayerSeeds) {
        $route = "$BaseUrl/circuitjs.html?tsjQuickPlay=true&tsjVerifyQuickPlay=true&" +
            "tsjQuickPlayTestFamily=4&tsjQuickPlayTestSeed=$seed"
        $label = if ($seed -eq 1) { 'quick-play npn seed 1 C-E-short finish' } else {
            "quick-play npn seed $seed"
        }
        if (-not (verifyRoute $label $route 'PASS:quick-play')) {
            return (Get-VerifierRouteFailureExitCode)
        }
        $npnQuickPlayCase++
    }
    $nmosQuickPlayCase = 0
    foreach ($seed in $nmosValidationSeeds) {
        $route = "$BaseUrl/circuitjs.html?tsjQuickPlay=true&tsjVerifyQuickPlay=true&" +
            "tsjQuickPlayTestFamily=5&tsjQuickPlayTestSeed=$seed"
        if (-not (verifyRoute "quick-play nmos seed $seed" $route 'PASS:quick-play')) {
            return (Get-VerifierRouteFailureExitCode)
        }
        $nmosQuickPlayCase++
    }
    return 0
}
if ($Layout) {
    if (-not (verifyRoute "procedural-layout" "$BaseUrl/circuitjs.html?tsjChallenge=led&seed=$PlayerSeed&tsjVerifyLayout=true&tsjVerifyGeometry=true" 'PASS:layout')) { return (Get-VerifierRouteFailureExitCode) }
    return 0
}
if ($Architecture) {
    if (-not (verifyRoute 'architecture seams' "$BaseUrl/circuitjs.html?tsjChallenge=led&seed=3&tsjVerifyArchitecture=true" 'PASS:architecture')) { return (Get-VerifierRouteFailureExitCode) }
    return 0
}
if ($Npn) {
    $faults = @('TRANSISTOR_CE_OPEN', 'TRANSISTOR_CE_SHORT',
        'BASE_RESISTOR_OPEN', 'LOAD_PATH_OPEN')
    $case = 0
    foreach ($seed in $npnValidationSeeds) {
        foreach ($fault in $faults) {
            $route = "$BaseUrl/circuitjs.html?tsjChallenge=npn&seed=$seed&" +
                "tsjNpnFault=$fault&tsjVerifyNpn=true&running=true"
            if (-not (verifyRoute "npn-$fault-seed-$seed" $route 'PASS:npn')) {
                return (Get-VerifierRouteFailureExitCode)
            }
            $case++
        }
    }
    return 0
}
if ($NpnNatural) {
    $naturalSeeds = @(0, 1, 2)
    $case = 0
    foreach ($seed in $naturalSeeds) {
        $route = "$BaseUrl/circuitjs.html?tsjChallenge=npn&seed=$seed&tsjVerifyNpn=true&running=true"
        if (-not (verifyRoute "npn-natural-seed-$seed" $route 'PASS:npn')) {
            return (Get-VerifierRouteFailureExitCode)
        }
        $case++
    }
    return 0
}
if ($Nmos) {
    $faults = @('NMOS_DS_OPEN', 'NMOS_DS_SHORT', 'NMOS_GATE_OPEN')
    $case = 0
    foreach ($seed in $nmosValidationSeeds) {
        foreach ($fault in $faults) {
            $route = "$BaseUrl/circuitjs.html?tsjChallenge=nmos&seed=$seed&" +
                "tsjNmosFault=$fault&tsjVerifyNmos=true&running=true"
            if (-not (verifyRoute "nmos-$fault-seed-$seed" $route 'PASS:nmos')) {
                return (Get-VerifierRouteFailureExitCode)
            }
            $case++
        }
    }
    return 0
}
if ($NmosNatural) {
    $case = 0
    foreach ($seed in $nmosValidationSeeds) {
        $route = "$BaseUrl/circuitjs.html?tsjChallenge=nmos&seed=$seed&tsjVerifyNmos=true&running=true"
        if (-not (verifyRoute "nmos-natural-seed-$seed" $route 'PASS:nmos')) {
            return (Get-VerifierRouteFailureExitCode)
        }
        $case++
    }
    return 0
}
if ($Task39) {
    if (-not (verifyRoute 'task39 NPN healthy operation boundary' "$BaseUrl/circuitjs.html?tsjFixture=npn&seed=0&tsjVerifyTask39=true&running=true" 'PASS:task39')) { return (Get-VerifierRouteFailureExitCode) }
    if (-not (verifyRoute 'task39 NMOS healthy operation boundary' "$BaseUrl/circuitjs.html?tsjFixture=nmos&seed=0&tsjVerifyTask39=true&running=true" 'PASS:task39')) { return (Get-VerifierRouteFailureExitCode) }
    if (-not (verifyRoute 'task39 RC customer retest boundary' "$BaseUrl/circuitjs.html?tsjChallenge=rc&seed=0&tsjVerifyTask39=true&running=true" 'PASS:task39')) { return (Get-VerifierRouteFailureExitCode) }
    if (-not (verifyTask39NormalPlayer "$BaseUrl/circuitjs.html?tsjChallenge=npn&seed=0&running=true" @('Set control HIGH', 'Set control LOW') 'Retest Customer')) { return (Get-VerifierRouteFailureExitCode) }
    if (-not (verifyTask39NormalPlayer "$BaseUrl/circuitjs.html?tsjChallenge=nmos&seed=0&running=true" @('Set control HIGH', 'Set control LOW') 'Retest Customer')) { return (Get-VerifierRouteFailureExitCode) }
    if (-not (verifyTask39NormalPlayer "$BaseUrl/circuitjs.html?tsjChallenge=rc&seed=0&running=true" @() 'Power-cycle and Retest Customer')) { return (Get-VerifierRouteFailureExitCode) }
    return 0
}
if ($Task40) {
    if (-not (verifyRoute 'task40 physical locus/serviceability admission' "$BaseUrl/circuitjs.html?tsjChallenge=npn&seed=0&tsjVerifyTask40=true&running=true" 'PASS:task40')) { return (Get-VerifierRouteFailureExitCode) }
    return 0
}
if ($Task41) {
    if (-not (verifyRoute 'task41 diagnostic solvability' "$BaseUrl/circuitjs.html?tsjChallenge=npn&seed=0&tsjVerifyTask41=true&running=true" 'PASS:task41')) { return (Get-VerifierRouteFailureExitCode) }
    return 0
}
if ($Task43PForcedNegative) {
    try {
        $script:task43ExpectedFailureObserved = $false
        $script:task43ExpectedFailureRoutePassed = $false
        [void](verifyRoute 'task43p forced-negative canary' `
            "$BaseUrl/circuitjs.html?tsjChallenge=led&seed=3&tsjVerifyTask43P=true&tsjTask43PForcedFailure=true&running=true" `
            'UNPROVEN:task43p' '' 'FAIL:task43p-forced-negative-canary')
    } catch {
        Write-Host "FAIL task43p forced-negative canary - verifier infrastructure: $($_.Exception.Message)"
        return 2
    }
    if ($script:task43ExpectedFailureObserved -ne $true -or
            $script:task43ExpectedFailureRoutePassed -ne $true) { return 2 }
    return 1
}
if ($Task43P) {
    $task43pSeeds = if ($script:VerifierBoundParameters.ContainsKey('Seeds')) { $Seeds } else {
        @(0, 2, 3)
    }
    $task43pFamilies = @('led', 'diode', 'rc', 'npn', 'nmos', 'parallel')
    foreach ($task43pFamily in $task43pFamilies) {
        foreach ($seed in $task43pSeeds) {
            $task43pRoute = "$BaseUrl/circuitjs.html?tsjChallenge=$task43pFamily&seed=$seed&" +
                'tsjVerifyTask43P=true&running=true'
            if (-not (verifyRoute "task43p $task43pFamily seed $seed" $task43pRoute `
                    'UNPROVEN:task43p')) {
                return (Get-VerifierRouteFailureExitCode)
            }
        }
    }
    # The positive route deliberately carries exit 2 until all A-I runtime
    # settlement/epoch claims have independently been closed.  Its structured
    # evidence and all caught negative canaries are still durable evidence.
    return 2
}
if ($Task43ForcedNegative) {
    try {
        $script:task43ExpectedFailureObserved = $false
        $script:task43ExpectedFailureRoutePassed = $false
        [void](verifyRoute 'task43 forced-negative canary' `
            "$BaseUrl/circuitjs.html?tsjChallenge=led&seed=3&tsjVerifyTask43=true&tsjTask43ForcedFailure=true&running=true" `
            'PASS:task43' '' 'FAIL:task43-forced-negative-canary')
    } catch {
        Write-Host "FAIL task43 forced-negative canary - verifier infrastructure: $($_.Exception.Message)"
        return 2
    }
    if ($script:task43ExpectedFailureObserved -ne $true -or
            $script:task43ExpectedFailureRoutePassed -ne $true) { return 2 }
    return 1
}
if ($Task43) {
    $task43Results = @(verifyRoute 'task43 physical package geometry contract' `
        "$BaseUrl/circuitjs.html?tsjChallenge=led&seed=3&tsjVerifyTask43=true&running=true" `
        'PASS:task43' | Where-Object { $_ -is [bool] })
    if ($task43Results.Count -ne 1 -or $task43Results[0] -ne $true) { return (Get-VerifierRouteFailureExitCode) }
    return 0
}
if ($WrongRepair) {
    if (-not (verifyRoute 'seed=3 wrong-repair' "$BaseUrl/circuitjs.html?tsjChallenge=led&seed=3&tsjVerifyWrongRepair=true" 'PASS:wrong-repair')) { return (Get-VerifierRouteFailureExitCode) }
    return 0
}
if ($NormalPlayer) {
    if (-not (verifyNormalPlayer "$BaseUrl/circuitjs.html?tsjChallenge=led&seed=$PlayerSeed&tsjVerifyGeometry=true")) { return (Get-VerifierRouteFailureExitCode) }
    return 0
}
if ($WrongRepairNormalPlayer) {
    if (-not (verifyWrongRepairNormalPlayer "$BaseUrl/circuitjs.html?tsjChallenge=led&seed=3&tsjVerifyGeometry=true")) { return (Get-VerifierRouteFailureExitCode) }
    return 0
}
if ($StressDamage) {
    if (-not (verifyRoute 'seed=3 stress-damage' "$BaseUrl/circuitjs.html?tsjChallenge=led&seed=3&tsjVerifyStress=true" 'PASS:stress')) { return (Get-VerifierRouteFailureExitCode) }
    return 0
}
if ($StressDamageNormalPlayer) {
    if (-not (verifyStressDamageNormalPlayer "$BaseUrl/circuitjs.html?tsjChallenge=led&seed=3&tsjVerifyStress=true&tsjStressDeferred=true&tsjVerifyGeometry=true")) { return (Get-VerifierRouteFailureExitCode) }
    return 0
}
if ($DiodeNormalPlayer) {
    if (-not (verifyNormalDiodePlayer "$BaseUrl/circuitjs.html?tsjChallenge=diode&seed=$PlayerSeed&tsjVerifyGeometry=true" $PlayerSeed)) { return (Get-VerifierRouteFailureExitCode) }
    return 0
}
if ($ParallelNormalPlayer) {
    if (-not (verifyNormalParallelPlayer "$BaseUrl/circuitjs.html?tsjChallenge=parallel&seed=3&tsjVerifyGeometry=true")) { return (Get-VerifierRouteFailureExitCode) }
    return 0
}
if ($LedNormalPlayer) {
    if (-not (verifyNormalLedPlayer "$BaseUrl/circuitjs.html?tsjChallenge=led&seed=$PlayerSeed&tsjVerifyGeometry=true" $PlayerSeed)) { return (Get-VerifierRouteFailureExitCode) }
    return 0
}
if ($RcNormalPlayer) {
    if (-not (verifyRcNormalPlayer "$BaseUrl/circuitjs.html?tsjChallenge=rc&seed=$PlayerSeed&tsjVerifyGeometry=true")) { return (Get-VerifierRouteFailureExitCode) }
    return 0
}
$family = 'led'
$routes = @(
    @{ Name = 'resistance'; Query = 'tsjVerifyResistance=true'; Expected = 'PASS:resistance'; Complaint = 'Indicator does not light.' },
    @{ Name = 'meter'; Query = 'tsjVerifyMeter=true'; Expected = 'PASS:meter'; Complaint = 'Indicator does not light.' },
    @{ Name = 'challenge'; Query = 'tsjVerifyChallenge=true'; Expected = 'PASS:challenge'; Complaint = 'Indicator does not light.' },
    @{ Name = 'replacement'; Query = 'tsjVerifyReplacement=true'; Expected = 'PASS:replacement'; Complaint = 'Indicator does not light.' },
    @{ Name = 'challenge+replacement'; Query = 'tsjVerifyChallenge=true&tsjVerifyReplacement=true'; Expected = 'PASS:replacement'; Complaint = 'Indicator does not light.' }
)
if ($DiodeShort) {
    $family = 'diode'
    $routes = @(@{ Name = 'diode-short'; Query = 'tsjVerifyDiode=true&tsjDiodeShort=true'; Expected = 'PASS:diode'; Complaint = 'The indicator is brighter than expected.' })
} elseif ($Diode) {
    $family = 'diode'
    $routes = @(@{ Name = 'diode'; Query = 'tsjVerifyDiode=true'; Expected = 'PASS:diode'; Complaint = 'Indicator does not light.' })
}
if ($Parallel) {
    $family = 'parallel'
    $routes = @(@{ Name = 'parallel'; Query = 'tsjVerifyParallel=true'; Expected = 'PASS:parallel'; Complaint = 'The two indicators do not behave the same.' })
}
if ($LedParts) {
    $family = 'led'
    $routes = @(@{ Name = 'led-parts'; Query = 'tsjVerifyLedParts=true'; Expected = 'PASS:led-parts'; Complaint = 'Indicator does not light.' })
}
if ($Rc) {
    $family = 'rc'
    $routes = @(@{ Name = 'rc'; Query = 'tsjVerifyRc=true'; Expected = 'PASS:rc' })
    if ($StoredEnergy) {
        $routes = @(@{ Name = 'rc-stored-energy'; Query = 'tsjVerifyRc=true&tsjVerifyStoredEnergy=true'; Expected = 'PASS:rc' })
    }
} elseif ($StoredEnergy) {
    $family = 'rc'
    $routes = @(@{ Name = 'stored-energy'; Query = 'tsjVerifyStoredEnergy=true'; Expected = 'PASS:stored-energy' })
}
$passed = $true
$index = 0
if ($Route) { $routes = @($routes | Where-Object { $_.Name -eq $Route }) }
if ($routes.Count -eq 0) { throw "Unknown route: $Route" }
$expectedCount = $Seeds.Count * $routes.Count
foreach ($seed in $Seeds) {
    foreach ($routeDefinition in $routes) {
        $url = "$BaseUrl/circuitjs.html?tsjChallenge=$family&seed=$seed&$($routeDefinition.Query)"
        $complaint = if ($routeDefinition.PSObject.Properties['Complaint']) {
            [string]$routeDefinition.Complaint
        } else { '' }
        $routePassed = verifyRoute "seed=$seed $($routeDefinition.Name)" $url $routeDefinition.Expected $complaint |
            Select-Object -Last 1
        if (-not $routePassed) { $passed = $false }
        $index++
    }
}
if (-not $passed) { return (Get-VerifierRouteFailureExitCode) }
Write-Host "All $expectedCount browser verifier routes passed."
return 0
}

# A deterministic developer-only probe used by Gate B.  It exercises the same
# wait/invoke classification functions without requiring a browser or a
# fabricated application result.
if ($GateBContractProbe) {
    try {
        if ($GateBContractProbeFailure) {
            Throw-VerifierInfrastructure 'deterministic Gate B CDP contract-probe failure'
        }
        function evaluateCdp {
            param($socket, [ref]$nextId, [string]$expression, [ref]$failures,
                [DateTime]$deadline = [DateTime]::MinValue)
            return $false
        }
        $probeNextId = 1
        $probeFailures = @()
        $timeoutObserved = $false
        try {
            waitForCdp $null ([ref]$probeNextId) 'false' ([DateTime]::UtcNow.AddMilliseconds(-1)) `
                ([ref]$probeFailures) 'Gate B deterministic timeout'
        } catch {
            $timeoutObserved = Test-VerifierInfrastructureError $_
        }
        if (-not $timeoutObserved) {
            throw 'CDP timeout probe was not classified as infrastructure.'
        }
        $script:VerifierFailureExitCode = 0
        $routeDeadlineObserved = $false
        try {
            Throw-VerifierRouteDeadline 'Gate B deterministic route-deadline probe'
        } catch {
            $routeDeadlineObserved = Test-VerifierInfrastructureError $_
            Set-VerifierFailure $_ 'Gate B route deadline' -Quiet
        }
        if (-not $routeDeadlineObserved -or (Get-VerifierRouteFailureExitCode) -ne 2) {
            throw 'route deadline probe was not classified as infrastructure exit 2.'
        }
        function sendCdp {
            param($socket, [int]$id, [string]$method, $parameters)
        }
        function receiveCdp {
            param($socket, [int]$wantedId, [ref]$failures,
                [DateTime]$deadline = [DateTime]::MinValue)
            return [pscustomobject]@{
                id = $wantedId
                error = [pscustomobject]@{ code = -32000; message = 'Gate B protocol probe' }
            }
        }
        $protocolObserved = $false
        try {
            [void](invokeCdp $null ([ref]$probeNextId) 'GateB.protocolProbe' @{} `
                ([ref]$probeFailures))
        } catch {
            $protocolObserved = Test-VerifierInfrastructureError $_
        }
        if (-not $protocolObserved) {
            throw 'CDP protocol-error probe was not classified as infrastructure.'
        }
        # Exercise both route-level deadline branches with a deterministic fake
        # session. No application result is fabricated: the fake only supplies
        # the transport shape needed to reach the verifier deadline paths.
        function startVerifierBrowser {
            param([string]$routeName, [string]$url)
            return [pscustomobject]@{
                Profile = 'gate-b-route-deadline-profile'
                Browser = $null
                Socket = $null
                Deadline = [DateTime]::UtcNow.AddMilliseconds(100)
                RouteId = 'gate-b-route-deadline'
            }
        }
        function cleanupBrowser {
            param($browser, $socket, [string]$profile)
        }
        function invokeCdp {
            param($socket, [ref]$nextId, [string]$method, $parameters,
                [ref]$failures, [DateTime]$deadline = [DateTime]::MinValue)
            return [pscustomobject]@{
                result = [pscustomobject]@{
                    result = [pscustomobject]@{ value = '' }
                }
            }
        }
        function evaluateCdp {
            param($socket, [ref]$nextId, [string]$expression, [ref]$failures,
                [DateTime]$deadline = [DateTime]::MinValue)
            if ($expression.IndexOf('location.href', [StringComparison]::Ordinal) -ge 0) {
                return $true
            }
            if ($expression -eq 'performance.timeOrigin') { return 1 }
            if ($expression.StartsWith('({status:')) {
                return [pscustomobject]@{ status = ''; body = '' }
            }
            return $false
        }
        $script:VerifierFailureExitCode = 0
        $expectedDeadlineResult = verifyRoute 'Gate B expected-failure deadline' `
            'http://127.0.0.1/gate-b' 'PASS:synthetic' '' 'FAIL:synthetic'
        if ($expectedDeadlineResult -or (Get-VerifierRouteFailureExitCode) -ne 2) {
            throw 'expected-failure route deadline was not classified as infrastructure exit 2.'
        }
        $script:VerifierFailureExitCode = 0
        $resultDeadlineResult = verifyRoute 'Gate B expected-result deadline' `
            'http://127.0.0.1/gate-b' 'PASS:synthetic'
        if ($resultDeadlineResult -or (Get-VerifierRouteFailureExitCode) -ne 2) {
            throw 'expected-result route deadline was not classified as infrastructure exit 2.'
        }
        $script:VerifierFailureExitCode = 0
        try { Throw-VerifierInfrastructure 'Gate B severity probe infrastructure' } catch {
            Set-VerifierFailure $_ 'Gate B severity infrastructure' -Quiet
        }
        Set-VerifierFailure ([InvalidOperationException]::new('Gate B severity probe application')) `
            'Gate B severity application' -Quiet
        if ($script:VerifierFailureExitCode -ne 2) {
            throw 'infrastructure-then-application severity probe downgraded to exit 1.'
        }
        foreach ($typedMarker in @($false, $true)) {
            $explicitExit2 = [System.InvalidOperationException]::new(
                'Gate B explicit exit-2 result matrix')
            $explicitExit2.Data['VerifierExitCode'] = 2
            if ($typedMarker) {
                $explicitExit2.Data['VerifierFailureKind'] = 'infrastructure'
            }
            $script:VerifierFailureExitCode = 0
            if (-not (Test-VerifierInfrastructureError $explicitExit2)) {
                throw ('explicit exit-2 result without/with typed marker was not ' +
                    'recognized as infrastructure: typed=' + [string]$typedMarker)
            }
            Set-VerifierFailure $explicitExit2 'Gate B explicit exit-2 matrix' -Quiet
            if ($script:VerifierFailureExitCode -ne 2 -or
                    (Get-VerifierRequestedExitCode $explicitExit2) -ne 2) {
                throw ('explicit exit-2 result without/with typed marker was downgraded: typed=' +
                    [string]$typedMarker)
            }
        }
        $integratedExplicitExit2Observed = $false
        try {
            Assert-IntegratedChildOutputContract 'Gate B explicit exit-2 result' 0 2 @()
        } catch {
            $integratedExplicitExit2Observed = Test-VerifierInfrastructureError $_
        }
        if (-not $integratedExplicitExit2Observed) {
            throw 'Task43Integrated explicit child exit 2 was not preserved as infrastructure.'
        }
        foreach ($invalidChildStatus in @($null, '', 'not-a-number')) {
            $invalidStatusObserved = $false
            try {
                [void](Resolve-VerifierIntegratedChildExitCode $true $invalidChildStatus)
            } catch {
                $invalidStatusObserved = Test-VerifierInfrastructureError $_
            }
            if (-not $invalidStatusObserved) {
                throw 'null/unparseable integrated child status was not classified as infrastructure exit 2.'
            }
        }
        $unprovenStatusObserved = $false
        try {
            [void](Resolve-VerifierIntegratedChildExitCode $false 0)
        } catch {
            $unprovenStatusObserved = Test-VerifierInfrastructureError $_
        }
        if (-not $unprovenStatusObserved) {
            throw 'unproven integrated child termination was not classified as infrastructure exit 2.'
        }
        $falsePassObserved = $false
        try {
            Assert-IntegratedChildOutputContract 'Gate B false-pass' 0 0 @('FAIL:synthetic false pass')
        } catch {
            if ($_.Exception.Data.Contains('VerifierExitCode') -and
                    [int]$_.Exception.Data['VerifierExitCode'] -eq 1) {
                $falsePassObserved = $true
            } else { throw }
        }
        if (-not $falsePassObserved) {
            throw 'integrated child false-pass probe was accepted.'
        }
        # Normally terminated children, including expected infrastructure
        # failures, must publish a completed ledger whose paths and resources
        # are real and owned. Only a parent timeout may accept a retained
        # incomplete ledger, and that result remains infrastructure failure.
        $ledgerCanaryRoot = Get-VerifierCanonicalWindowsPath (Join-Path ([IO.Path]::GetTempPath()) `
            ('TroubleshootJS\verify\ledger-completion-' + [Guid]::NewGuid().ToString('N')))
        $ledgerCanaryFailure = $null
        try {
            New-Item -ItemType Directory -Path $ledgerCanaryRoot -Force -ErrorAction Stop | Out-Null
            function Write-GateBLedger($Context, [string]$Path, [string]$State,
                    $Leases, $Profiles, $Server, [string]$CleanupState) {
                $normalizedLeases = @()
                foreach ($candidate in @($Leases)) {
                    $candidateLeaseId = Get-VerifierLedgerProperty $candidate 'leaseId' $null
                    if ($null -eq $candidate -or [String]::IsNullOrWhiteSpace([string]$candidateLeaseId)) {
                        continue
                    }
                    $leaseId = [string]$candidateLeaseId
                    $port = [int](Get-VerifierLedgerProperty $candidate 'port' 0)
                    $kind = [string](Get-VerifierLedgerProperty $candidate 'kind' 'canary')
                    $claimName = [string](Get-VerifierLedgerProperty $candidate 'claimName' '')
                    $browserPath = [string](Get-VerifierLedgerProperty $candidate 'browserPath' '')
                    if ([String]::IsNullOrWhiteSpace($browserPath)) {
                        $browserPath = [string](Get-VerifierLedgerProperty $candidate 'BrowserPath' '')
                    }
                    if ($kind -eq 'cdp' -and
                            ([String]::IsNullOrWhiteSpace($browserPath) -or
                             [String]::IsNullOrWhiteSpace((Get-VerifierCanonicalWindowsPath $browserPath)))) {
                        throw 'missing-configured-BrowserPath cdp lease cannot be written.'
                    }
                     if ([String]::IsNullOrWhiteSpace($claimName)) {
                         $claimName = 'Global\TroubleshootJS.Verifier.Port.' + [string]$port
                     }
                     $profileValue = [string](Get-VerifierLedgerProperty $candidate 'profile' '')
                     if ([String]::IsNullOrWhiteSpace($profileValue)) {
                         $profileValue = [string](Get-VerifierLedgerProperty $candidate 'ProfilePath' '')
                     }
                      $ownerPid = [int](Get-VerifierLedgerProperty $candidate 'claimOwnerPid' $PID)
                     $ownerStart = [long](Get-VerifierLedgerProperty $candidate 'claimOwnerStartTicks' 1)
                     $mutexReleased = $false
                     if ($candidate.PSObject.Properties['MutexReleased']) {
                         $mutexReleased = [bool]$candidate.MutexReleased
                     } elseif ($candidate.PSObject.Properties['mutexReleased']) {
                         $mutexReleased = [bool]$candidate.mutexReleased
                     }
                     $normalizedLeases += [pscustomobject]([ordered]@{
                        runId = $Context.RunId; repositoryIdentity = $Context.RepositoryIdentity
                        worktreeRoot = $Context.WorktreeRoot; leaseId = $leaseId
                        path = [string](Get-VerifierLedgerProperty $candidate 'path' '')
                        kind = $kind; port = $port; claimName = $claimName
                        status = [string](Get-VerifierLedgerProperty $candidate 'status' 'held')
                          claimState = [string](Get-VerifierLedgerProperty $candidate 'claimState' 'held')
                          releaseState = [string](Get-VerifierLedgerProperty $candidate 'releaseState' 'active')
                          releaseJournalState = Get-VerifierLedgerProperty $candidate 'releaseJournalState' $null
                          claimOwnerPid = $ownerPid; claimOwnerStartTicks = $ownerStart
                          mutexReleased = $mutexReleased
                          profile = $profileValue
                          browserPath = $browserPath
                         processTerminationProven = if ($candidate.PSObject.Properties['ProcessTerminationProven']) {
                             [bool]$candidate.ProcessTerminationProven
                         } elseif ($candidate.PSObject.Properties['processTerminationProven']) {
                             [bool]$candidate.processTerminationProven
                         } else { $false }
                         processAbsent = if ($candidate.PSObject.Properties['ProcessAbsent']) {
                             [bool]$candidate.ProcessAbsent
                         } elseif ($candidate.PSObject.Properties['processAbsent']) {
                             [bool]$candidate.processAbsent
                         } else { $false }
                         boundProcessId = [int](Get-VerifierLedgerProperty $candidate 'boundProcessId' 0)
                        boundProcessStartTicks = [long](Get-VerifierLedgerProperty $candidate 'boundProcessStartTicks' 0)
                        listenerProcessId = [int](Get-VerifierLedgerProperty $candidate 'listenerProcessId' 0)
                        listenerProcessStartTicks = [long](Get-VerifierLedgerProperty $candidate 'listenerProcessStartTicks' 0)
                          listenerInspectionSuccess = [bool](Get-VerifierLedgerProperty $candidate 'listenerInspectionSuccess' $false)
                          listenerInspectionKnown = [bool](Get-VerifierLedgerProperty $candidate 'listenerInspectionKnown' $false)
                          listenerHasListeners = Get-VerifierLedgerProperty $candidate 'listenerHasListeners' $null
                          listenerAbsent = Get-VerifierLedgerProperty $candidate 'listenerAbsent' $null
                          processProofRequired = Get-VerifierLedgerProperty $candidate 'processProofRequired' $null
                          claim = [ordered]@{
                            protocol = 'troubleshootjs-verifier-port-claim-v1'
                            runId = $Context.RunId; repositoryIdentity = $Context.RepositoryIdentity
                            worktreeRoot = $Context.WorktreeRoot; leaseId = $leaseId
                            path = [string](Get-VerifierLedgerProperty $candidate 'path' '')
                            kind = $kind; port = $port; mutexName = $claimName
                            ownerPid = $ownerPid; ownerStartTicks = $ownerStart
                        }
                    })
                }
                $normalizedProfiles = @()
                foreach ($candidate in @($Profiles)) {
                    $candidateProfile = Get-VerifierLedgerProperty $candidate 'profile' $null
                    if ($null -eq $candidate -or [String]::IsNullOrWhiteSpace([string]$candidateProfile)) {
                        continue
                    }
                    $profileBrowserPath = [string](Get-VerifierLedgerProperty $candidate 'browserPath' '')
                    if ([String]::IsNullOrWhiteSpace($profileBrowserPath) -or
                            [String]::IsNullOrWhiteSpace((Get-VerifierCanonicalWindowsPath $profileBrowserPath))) {
                        throw 'missing-configured-BrowserPath browser profile cannot be written.'
                    }
                    $normalizedProfiles += [pscustomobject]([ordered]@{
                        owner = 'run'; runId = $Context.RunId
                        repositoryIdentity = $Context.RepositoryIdentity
                        worktreeRoot = $Context.WorktreeRoot
                        profile = [string]$candidateProfile
                        cdpLeasePath = [string](Get-VerifierLedgerProperty $candidate 'cdpLeasePath' '')
                        cdpPort = [int](Get-VerifierLedgerProperty $candidate 'cdpPort' 0)
                         browserPath = $profileBrowserPath
                        processId = [int](Get-VerifierLedgerProperty $candidate 'processId' 0)
                        processStartTicks = [long](Get-VerifierLedgerProperty $candidate 'processStartTicks' 0)
                        processParentProcessId = [int](Get-VerifierLedgerProperty $candidate 'processParentProcessId' 0)
                        processParentProcessStartTicks = [long](Get-VerifierLedgerProperty $candidate 'processParentProcessStartTicks' 0)
                        processCommandLine = [string](Get-VerifierLedgerProperty $candidate 'processCommandLine' '')
                        status = [string](Get-VerifierLedgerProperty $candidate 'status' 'startup-failed')
                        cleanupResult = [string](Get-VerifierLedgerProperty $candidate 'cleanupResult' 'infrastructure-failure')
                    })
                }
                $manifestForLedger = Get-Content -LiteralPath $Context.ManifestPath -Raw | ConvertFrom-Json
                $record = [ordered]@{
                    protocol = 'troubleshootjs-integrated-child-ledger-v1'; state = $State
                    runId = $Context.RunId; repositoryIdentity = $Context.RepositoryIdentity
                    worktreeRoot = $Context.WorktreeRoot
                    parentNamespaceRoot = $Context.RunNamespaceRoot
                    runRoot = $Context.RunRoot; manifestPath = $Context.ManifestPath
                    evidenceDirectory = $Context.EvidenceDirectory
                    evidence = @($manifestForLedger.artifacts)
                    leases = $normalizedLeases
                    profiles = $normalizedProfiles
                    server = if ($null -ne $Server) { $Server } else { $manifestForLedger.server }
                    cleanupState = $CleanupState
                    cleanupErrors = @(); updatedUtc = Get-VerifierUtcText
                }
                [IO.File]::WriteAllText($Path, ($record | ConvertTo-Json -Depth 12),
                    [Text.UTF8Encoding]::new($false))
            }
            function Set-GateBManifestResources($Context, $Leases, $Profiles, $Server = $null) {
                $manifest = Get-Content -LiteralPath $Context.ManifestPath -Raw | ConvertFrom-Json
                # Real LeaseRecord objects do not carry the nested claim
                # descriptor required by the integrated-ledger reader. Keep
                # the production manifest's already durable canonical lease
                # records for those resources; synthetic retained ledgers
                # provide canonical descriptors explicitly.
                $providedLeases = @($Leases)
                $providedAreCanonical = (@($providedLeases | Where-Object {
                    $null -eq $_ -or $null -eq (Get-VerifierLedgerProperty $_ 'claim' $null)
                }).Count -eq 0)
                if ($providedAreCanonical) {
                    $manifest.leases = $providedLeases
                } elseif (@($manifest.leases).Count -eq @($providedLeases).Count) {
                    $manifest.leases = @($manifest.leases)
                } else {
                    throw 'Gate B canary could not establish a canonical manifest lease ledger.'
                }
                $manifest.browserSessions = @($Profiles)
                if ($null -ne $Server) { $manifest.server = $Server }
                [IO.File]::WriteAllText($Context.ManifestPath,
                    ($manifest | ConvertTo-Json -Depth 12), [Text.UTF8Encoding]::new($false))
            }
            $repoForLedger = Get-VerifierFullPath (Join-Path $PSScriptRoot '..')
            # Every run-owned browser/paired cdp resource must carry the same
            # resolved executable identity that the live verifier will use.
            $ledgerBrowserPath = Resolve-VerifierBrowserPath $BrowserPath
            $zeroParent = Get-VerifierFullPath (Join-Path $ledgerCanaryRoot 'completed-zero')
            $zeroContext = New-VerifierRunContext $repoForLedger '' $zeroParent
            $zeroContext.CleanupState = 'complete'
            Write-VerifierManifest $zeroContext
            $zeroLedgerPath = Get-VerifierFullPath (Join-Path $zeroParent 'expected2-completed.json')
            Write-GateBLedger $zeroContext $zeroLedgerPath 'completed' `
                -Leases @() -Profiles @() -Server $null -CleanupState 'complete'
            $acceptedCompletedLedger = Read-VerifierIntegratedChildLedger $zeroLedgerPath 2
            if ([string]$acceptedCompletedLedger.state -ne 'completed') {
                throw 'expected-exit-2 completed zero-resource ledger was not accepted.'
            }
            $realResourceParent = Get-VerifierFullPath (Join-Path $ledgerCanaryRoot 'completed-real-lease')
            $realResourceContext = New-VerifierRunContext $repoForLedger '' $realResourceParent
            $realResourceLease = New-VerifierPortLease $realResourceContext 'cdp' 0 $ledgerBrowserPath
            # Exercise the missing-configured-BrowserPath rejection before any
            # profile/process cleanup is attempted. The manifest/ledger are
            # temporarily changed together, and the original manifest is
            # restored in the same finally block.
            $missingBrowserPathManifestText = [IO.File]::ReadAllText($realResourceContext.ManifestPath)
            $missingBrowserPathPath = Get-VerifierFullPath (
                Join-Path $realResourceParent 'missing-configured-BrowserPath.json')
            $missingBrowserPathRejected = $false
            try {
                $missingBrowserPathManifest = $missingBrowserPathManifestText | ConvertFrom-Json
                $missingBrowserPathManifest.leases[0].browserPath = ''
                [IO.File]::WriteAllText($realResourceContext.ManifestPath,
                    ($missingBrowserPathManifest | ConvertTo-Json -Depth 16),
                    [Text.UTF8Encoding]::new($false))
                $missingBrowserPathLedger = [ordered]@{
                    protocol = 'troubleshootjs-integrated-child-ledger-v1'
                    state = 'completed'; runId = $realResourceContext.RunId
                    repositoryIdentity = $realResourceContext.RepositoryIdentity
                    worktreeRoot = $realResourceContext.WorktreeRoot
                    parentNamespaceRoot = $realResourceContext.RunNamespaceRoot
                    runRoot = $realResourceContext.RunRoot
                    manifestPath = $realResourceContext.ManifestPath
                    evidenceDirectory = $realResourceContext.EvidenceDirectory
                    evidence = @(); leases = @($missingBrowserPathManifest.leases)
                    profiles = @(); server = $null; cleanupState = 'complete'
                    cleanupErrors = @(); updatedUtc = Get-VerifierUtcText
                }
                [IO.File]::WriteAllText($missingBrowserPathPath,
                    ($missingBrowserPathLedger | ConvertTo-Json -Depth 16),
                    [Text.UTF8Encoding]::new($false))
                try { [void](Read-VerifierIntegratedChildLedger $missingBrowserPathPath 2) } catch {
                    $missingBrowserPathRejected = Test-VerifierInfrastructureError $_
                }
                if (-not $missingBrowserPathRejected) {
                    throw 'missing-configured-BrowserPath ledger was accepted and could permit root cleanup/adoption.'
                }
                Write-Host 'INFO:missing-configured-BrowserPath ledger rejected before ownership cleanup'
            } finally {
                [IO.File]::WriteAllText($realResourceContext.ManifestPath,
                    $missingBrowserPathManifestText, [Text.UTF8Encoding]::new($false))
            }
            $realResourceProfile = Get-VerifierFullPath (Join-Path $realResourceContext.RunRoot 'browser\real\profile')
            New-Item -ItemType Directory -Path $realResourceProfile -Force -ErrorAction Stop | Out-Null
            $realResourceLease.ProfilePath = $realResourceProfile
            $realResourceLease.OwnerType = 'browser'
            $realResourceLease.BrowserPath = $ledgerBrowserPath
            Release-VerifierPortLease $realResourceContext $realResourceLease
            $realResourceProfileRecord = [ordered]@{
                owner = 'run'; runId = $realResourceContext.RunId
                repositoryIdentity = $realResourceContext.RepositoryIdentity
                worktreeRoot = $realResourceContext.WorktreeRoot
                profile = $realResourceProfile
                cdpLeasePath = $realResourceLease.Path; cdpPort = $realResourceLease.Port
                browserPath = $ledgerBrowserPath; processId = 0; processStartTicks = 0
                processParentProcessId = 0; processParentProcessStartTicks = 0
                processCommandLine = ''
                status = 'cleaned'; cleanupResult = 'complete'
            }
            Remove-VerifierOwnedTree $realResourceContext.RunRoot $realResourceProfile
            $realResourceContext.CleanupState = 'complete'
            Write-VerifierManifest $realResourceContext
            Set-GateBManifestResources $realResourceContext -Leases @($realResourceLease) `
                -Profiles @($realResourceProfileRecord) -Server $null
            $realResourceLedgerPath = Get-VerifierFullPath (Join-Path $realResourceParent 'real-completed.json')
            Write-GateBLedger $realResourceContext $realResourceLedgerPath 'completed' `
                -Leases @($realResourceLease) -Profiles @($realResourceProfileRecord) `
                -Server $null -CleanupState 'complete'
            $realResourceAccepted = Read-VerifierIntegratedChildLedger $realResourceLedgerPath 2
            if ([string]$realResourceAccepted.state -ne 'completed') {
                throw 'real completed lease resource ledger was not accepted after independent release proof.'
            }
            # Terminal reuse is allowed only with a complete durable tombstone
            # and strict listener/process proof fields. Missing or contradictory
            # fields must remain infrastructure failures, even when the claim
            # file is already absent.
            $missingTerminalLedger = Get-Content -LiteralPath $realResourceLedgerPath -Raw | ConvertFrom-Json
            $missingTerminalLedger.leases[0].PSObject.Properties.Remove('listenerAbsent')
            $missingTerminalPath = Get-VerifierFullPath (Join-Path $realResourceParent 'terminal-missing-proof.json')
            [IO.File]::WriteAllText($missingTerminalPath,
                ($missingTerminalLedger | ConvertTo-Json -Depth 16), [Text.UTF8Encoding]::new($false))
            $missingTerminalRejected = $false
            try { [void](Read-VerifierIntegratedChildLedger $missingTerminalPath 2) } catch {
                $missingTerminalRejected = Test-VerifierInfrastructureError $_
            }
            if (-not $missingTerminalRejected) {
                throw 'completed lease ledger accepted a terminal tombstone with missing listener proof.'
            }
            $liveTerminalLedger = Get-Content -LiteralPath $realResourceLedgerPath -Raw | ConvertFrom-Json
            $liveTerminalLedger.leases[0].listenerAbsent = $false
            $liveTerminalPath = Get-VerifierFullPath (Join-Path $realResourceParent 'terminal-live-listener.json')
            [IO.File]::WriteAllText($liveTerminalPath,
                ($liveTerminalLedger | ConvertTo-Json -Depth 16), [Text.UTF8Encoding]::new($false))
            $liveTerminalRejected = $false
            try { [void](Read-VerifierIntegratedChildLedger $liveTerminalPath 2) } catch {
                $liveTerminalRejected = Test-VerifierInfrastructureError $_
            }
            if (-not $liveTerminalRejected) {
                throw 'completed lease ledger accepted contradictory live-listener terminal proof.'
            }
            $falseCleanupLedger = Get-Content -LiteralPath $realResourceLedgerPath -Raw | ConvertFrom-Json
            $falseCleanupLedger.leases[0].listenerHasListeners = $true
            $falseCleanupPath = Get-VerifierFullPath (Join-Path $realResourceParent 'false-cleanup-flags.json')
            [IO.File]::WriteAllText($falseCleanupPath, ($falseCleanupLedger | ConvertTo-Json -Depth 16),
                [Text.UTF8Encoding]::new($false))
            $falseCleanupRejected = $false
            try { [void](Read-VerifierIntegratedChildLedger $falseCleanupPath 2) } catch {
                $falseCleanupRejected = Test-VerifierInfrastructureError $_
            }
            if (-not $falseCleanupRejected) {
                throw 'completed real lease ledger accepted a listener-cleanup flag without independent proof.'
            }
            if (Test-Path -LiteralPath $realResourceContext.RunRoot) {
                $ledgerVerifierRoot = Get-VerifierFullPath (Join-Path ([IO.Path]::GetTempPath()) `
                    'TroubleshootJS\verify')
                Remove-VerifierOwnedTree $ledgerVerifierRoot $realResourceContext.RunRoot
            }
            # An explicitly supplied preview is a verified, non-owned server:
            # the integrated reader may accept its exact identity, but no
            # process/lease/listener cleanup is attached to that record.
            $callerCanaryRoot = Get-VerifierCanonicalWindowsPath (Join-Path $ledgerCanaryRoot 'caller-owned')
            $callerCanaryContext = $null
            $callerCanaryPassed = $false
            try {
                $callerCanaryContext = New-VerifierRunContext $repoForLedger '' $callerCanaryRoot
                $callerScript = Get-VerifierFullPath (Join-Path $repoForLedger 'scripts\preview.ps1')
                $callerWebRoot = Get-VerifierFullPath (Join-Path $repoForLedger 'war')
                $callerServerLedger = [ordered]@{
                    owner='caller'; baseUrl='http://127.0.0.1:40126'
                    repositoryIdentity=$callerCanaryContext.RepositoryIdentity
                    worktreeRoot=$callerCanaryContext.WorktreeRoot
                    repositoryRoot=$callerCanaryContext.WorktreeRoot; webRoot=$callerWebRoot
                    identityProtocol='troubleshootjs-preview-identity-v1'; identityVerified=$true; callerOwned=$true
                    port=40126; processId=0; processStartTicks=0; processParentProcessId=0; processCommandLine=''
                    script=$callerScript; runId=''; nonce=''; leaseId=''; leaseKind=''; leaseClaimName=''
                    leaseClaimState=''; leaseReleaseState=''; leaseOwnerPid=0; leaseOwnerStartTicks=0; leasePath=''
                    stdoutLog=''; stderrLog=''; state='caller-verified'; cleanupResult='not-owned'
                    error=''
                    Lease=$null; Process=$null
                    processIdentityKnown=$false; ownershipUncertain=$false; processTerminationProven=$false
                    processAbsent=$true; listenerInspectionProven=$false; listenerAbsent=$false
                }
                $callerCanaryContext.Server = [pscustomobject]$callerServerLedger
                $callerCanaryContext.CleanupState = 'complete'
                Write-VerifierManifest $callerCanaryContext
                Set-GateBManifestResources $callerCanaryContext -Leases @() -Profiles @() `
                    -Server $callerServerLedger
                $callerLedgerPath = Get-VerifierFullPath (Join-Path $callerCanaryRoot 'caller-owned.json')
                Write-GateBLedger $callerCanaryContext $callerLedgerPath 'completed' `
                    -Leases @() -Profiles @() -Server $callerServerLedger -CleanupState 'complete'
                $callerAccepted = Read-VerifierIntegratedChildLedger $callerLedgerPath 0
                if ([string]$callerAccepted.server.owner -ne 'caller') {
                    throw 'caller-owned integrated ledger did not preserve its non-owned server record.'
                }
                # Exercise both independent copies of the caller proof.  The
                # reader must reject JSON strings and other wrong types even
                # when the ledger and manifest contain the same forged value;
                # otherwise PowerShell 5.1 truthiness can turn malformed
                # ownership evidence into a false PASS.
                $callerLedgerBaseline = Get-Content -LiteralPath $callerLedgerPath -Raw
                $callerManifestBaseline = Get-Content -LiteralPath $callerCanaryContext.ManifestPath -Raw
                $callerProofTypeCases = @(
                    [pscustomobject]@{ Name = 'identityVerified'; Value = 'false' }
                    [pscustomobject]@{ Name = 'callerOwned'; Value = 'true' }
                    [pscustomobject]@{ Name = 'processAbsent'; Value = 'true' }
                    [pscustomobject]@{ Name = 'processIdentityKnown'; Value = 1 }
                    [pscustomobject]@{ Name = 'processTerminationProven'; Value = 'false' }
                    [pscustomobject]@{ Name = 'ownershipUncertain'; Value = 'false' }
                    [pscustomobject]@{ Name = 'listenerInspectionProven'; Value = 'false' }
                    [pscustomobject]@{ Name = 'listenerAbsent'; Value = 1 }
                )
                foreach ($callerProofTypeCase in $callerProofTypeCases) {
                    $callerVariantPath = Get-VerifierFullPath (Join-Path $callerCanaryRoot `
                        ('caller-proof-type-' + $callerProofTypeCase.Name + '.json'))
                    try {
                        $callerLedgerVariant = $callerLedgerBaseline | ConvertFrom-Json
                        $callerLedgerProperty = $callerLedgerVariant.server.PSObject.Properties[
                            $callerProofTypeCase.Name]
                        if ($null -eq $callerLedgerProperty) {
                            throw ('caller ledger proof fixture omitted ' + $callerProofTypeCase.Name)
                        }
                        $callerLedgerProperty.Value = $callerProofTypeCase.Value
                        $callerManifestVariant = $callerManifestBaseline | ConvertFrom-Json
                        $callerManifestProperty = $callerManifestVariant.server.PSObject.Properties[
                            $callerProofTypeCase.Name]
                        if ($null -eq $callerManifestProperty) {
                            throw ('caller manifest proof fixture omitted ' + $callerProofTypeCase.Name)
                        }
                        $callerManifestProperty.Value = $callerProofTypeCase.Value
                        [IO.File]::WriteAllText($callerCanaryContext.ManifestPath,
                            ($callerManifestVariant | ConvertTo-Json -Depth 16),
                            [Text.UTF8Encoding]::new($false))
                        [IO.File]::WriteAllText($callerVariantPath,
                            ($callerLedgerVariant | ConvertTo-Json -Depth 16),
                            [Text.UTF8Encoding]::new($false))
                        $callerVariantRejected = $false
                        try {
                            [void](Read-VerifierIntegratedChildLedger $callerVariantPath 0)
                        } catch {
                            $callerVariantRejected = Test-VerifierInfrastructureError $_
                        }
                        if (-not $callerVariantRejected) {
                            throw ('caller proof type variant ' + $callerProofTypeCase.Name +
                                ' was accepted despite a malformed ledger/manifest Boolean.')
                        }
                    } finally {
                        [IO.File]::WriteAllText($callerCanaryContext.ManifestPath,
                            $callerManifestBaseline, [Text.UTF8Encoding]::new($false))
                    }
                }
                Write-Host 'PASS:caller-owned ledger/manifest strict Boolean proof negative cases'
                $callerCleanup = Complete-VerifierRun $callerCanaryContext
                if ($null -eq $callerCleanup -or -not [bool]$callerCleanup.Success -or
                        $callerCanaryContext.Server.Owner -ne 'caller' -or
                        [int]$callerCanaryContext.Server.ProcessId -ne 0 -or
                        $null -ne $callerCanaryContext.Server.Lease) {
                    throw 'caller-owned integrated cleanup adopted, killed, or attached a lease to the caller server.'
                }
                $callerCanaryPassed = $true
            } catch {
                $callerCanaryFailure = $_
            } finally {
                if ($callerCanaryPassed -and (Test-Path -LiteralPath $callerCanaryRoot)) {
                    Remove-VerifierOwnedTree $ledgerCanaryRoot $callerCanaryRoot
                }
            }
            if (-not $callerCanaryPassed) {
                Throw-VerifierInfrastructure ('caller-owned integrated ledger canary failed; evidence retained at ' +
                    $callerCanaryRoot + ': ' + (Get-VerifierErrorMessage $callerCanaryFailure))
            }
            Write-Host 'PASS:verified caller-owned integrated preview is non-owned and never adopted'
            $normalIncompletePath = Get-VerifierFullPath (Join-Path $zeroParent 'expected2-incomplete-normal.json')
            $normalIncompleteLedger = Get-Content -LiteralPath $zeroLedgerPath -Raw | ConvertFrom-Json
            $normalIncompleteLedger.state = 'resource-started'
            $normalIncompleteLedger.cleanupState = 'infrastructure-failure'
            [IO.File]::WriteAllText($normalIncompletePath,
                ($normalIncompleteLedger | ConvertTo-Json -Depth 12), [Text.UTF8Encoding]::new($false))
            $normalIncompleteRejected = $false
            try { [void](Read-VerifierIntegratedChildLedger $normalIncompletePath 2) } catch {
                $normalIncompleteRejected = Test-VerifierInfrastructureError $_
            }
            if (-not $normalIncompleteRejected) {
                throw 'normally terminated expected-exit-2 child with an incomplete ledger was accepted.'
            }

            $malformedPath = Join-Path $zeroParent 'malformed.json'
            [IO.File]::WriteAllText($malformedPath, '{"protocol":"troubleshootjs-integrated-child-ledger-v1","state":"completed"}',
                [Text.UTF8Encoding]::new($false))
            $malformedRejected = $false
            try { [void](Read-VerifierIntegratedChildLedger $malformedPath 2) } catch {
                $malformedRejected = Test-VerifierInfrastructureError $_
            }
            if (-not $malformedRejected) { throw 'malformed/pathless child ledger was accepted.' }

            $foreignPath = Join-Path $zeroParent 'foreign.json'
            $foreignLedger = Get-Content -LiteralPath $zeroLedgerPath -Raw | ConvertFrom-Json
            $foreignLedger.runRoot = Get-VerifierFullPath (Join-Path $ledgerCanaryRoot 'foreign-run-root')
            [IO.File]::WriteAllText($foreignPath, ($foreignLedger | ConvertTo-Json -Depth 12),
                [Text.UTF8Encoding]::new($false))
            $foreignRejected = $false
            try { [void](Read-VerifierIntegratedChildLedger $foreignPath 2) } catch {
                $foreignRejected = Test-VerifierInfrastructureError $_
            }
            if (-not $foreignRejected) { throw 'foreign child run-root ledger was accepted.' }

            $failedPath = Join-Path $zeroParent 'cleanup-failed.json'
            $failedLedger = Get-Content -LiteralPath $zeroLedgerPath -Raw | ConvertFrom-Json
            $failedLedger.state = 'cleanup-failed'
            $failedLedger.cleanupState = 'infrastructure-failure'
            [IO.File]::WriteAllText($failedPath, ($failedLedger | ConvertTo-Json -Depth 12),
                [Text.UTF8Encoding]::new($false))
            $failedRejected = $false
            try { [void](Read-VerifierIntegratedChildLedger $failedPath 2 -AllowIncomplete) } catch {
                $failedRejected = Test-VerifierInfrastructureError $_
            }
            if (-not $failedRejected) { throw 'cleanup-failed child ledger was accepted as timeout evidence.' }

            $staleParent = Get-VerifierFullPath (Join-Path $ledgerCanaryRoot 'stale-claim')
            $staleContext = New-VerifierRunContext $repoForLedger '' $staleParent
            $staleLeaseId = [Guid]::NewGuid().ToString('N')
            $staleClaimPath = Get-VerifierFullPath (Join-Path $staleContext.PortLeaseRoot ('40123-' + $staleLeaseId + '.lease'))
            $staleClaimName = Get-VerifierPortMutexName $null 40123
            $staleClaim = [ordered]@{ protocol='troubleshootjs-verifier-port-claim-v1'; runId=$staleContext.RunId; repositoryIdentity=$staleContext.RepositoryIdentity; worktreeRoot=$staleContext.WorktreeRoot; leaseId=$staleLeaseId; path=$staleClaimPath; kind='cdp'; port=40123; mutexName=$staleClaimName; ownerPid=$PID; ownerStartTicks=1 }
            $staleLease = [ordered]@{ runId=$staleContext.RunId; repositoryIdentity=$staleContext.RepositoryIdentity; worktreeRoot=$staleContext.WorktreeRoot; leaseId=$staleLeaseId; path=$staleClaimPath; kind='cdp'; port=40123; claimName=$staleClaimName; browserPath=$ledgerBrowserPath; claimOwnerPid=$PID; claimOwnerStartTicks=1; status='released'; claimState='released'; releaseState='complete'; mutexReleased=$false; profile=''; listenerInspectionSuccess=$true; listenerInspectionKnown=$true; listenerHasListeners=$false; boundProcessId=0; boundProcessStartTicks=0; listenerProcessId=0; listenerProcessStartTicks=0; claim=$staleClaim }
            [IO.File]::WriteAllText($staleClaimPath, ($staleClaim | ConvertTo-Json -Depth 8), [Text.UTF8Encoding]::new($false))
            Set-GateBManifestResources $staleContext -Leases @($staleLease) -Profiles @()
            $staleLedgerPath = Get-VerifierFullPath (Join-Path $staleParent 'stale-claim.json')
            Write-GateBLedger $staleContext $staleLedgerPath 'completed' `
                -Leases @($staleLease) -Profiles @() -Server $null -CleanupState 'complete'
            $staleRejected = $false
            try { [void](Read-VerifierIntegratedChildLedger $staleLedgerPath 2) } catch {
                $staleRejected = Test-VerifierInfrastructureError $_
            }
            if (-not $staleRejected) { throw 'completed child ledger accepted a stale claim file.' }

            $retainedParent = Get-VerifierFullPath (Join-Path $ledgerCanaryRoot 'timeout-retained')
            $retainedContext = New-VerifierRunContext $repoForLedger '' $retainedParent
            $retainedLeaseId = [Guid]::NewGuid().ToString('N')
            $retainedClaimPath = Get-VerifierFullPath (Join-Path $retainedContext.PortLeaseRoot ('40125-' + $retainedLeaseId + '.lease'))
            $retainedProfile = Get-VerifierFullPath (Join-Path $retainedContext.RunRoot 'browser\retained\profile')
            New-Item -ItemType Directory -Path $retainedProfile -Force -ErrorAction Stop | Out-Null
            $retainedServerLeaseId = [Guid]::NewGuid().ToString('N')
            $retainedServerClaimPath = Get-VerifierFullPath (Join-Path $retainedContext.PortLeaseRoot ('40124-' + $retainedServerLeaseId + '.lease'))
            $retainedServerClaimName = Get-VerifierPortMutexName $null 40124
            $retainedServerClaim = [ordered]@{ protocol='troubleshootjs-verifier-port-claim-v1'; runId=$retainedContext.RunId; repositoryIdentity=$retainedContext.RepositoryIdentity; worktreeRoot=$retainedContext.WorktreeRoot; leaseId=$retainedServerLeaseId; path=$retainedServerClaimPath; kind='preview'; port=40124; mutexName=$retainedServerClaimName; ownerPid=$PID; ownerStartTicks=1 }
            $retainedServerLease = [ordered]@{ runId=$retainedContext.RunId; repositoryIdentity=$retainedContext.RepositoryIdentity; worktreeRoot=$retainedContext.WorktreeRoot; leaseId=$retainedServerLeaseId; path=$retainedServerClaimPath; kind='preview'; port=40124; claimName=$retainedServerClaimName; claimOwnerPid=$PID; claimOwnerStartTicks=1; status='held'; claimState='held'; releaseState='active'; mutexReleased=$false; profile=''; listenerInspectionSuccess=$false; listenerInspectionKnown=$false; listenerHasListeners=$null; boundProcessId=54321; boundProcessStartTicks=12345L; listenerProcessId=54321; listenerProcessStartTicks=12345L; processParentProcessStartTicks=12344L; claim=$retainedServerClaim }
            [IO.File]::WriteAllText($retainedServerClaimPath, ($retainedServerClaim | ConvertTo-Json -Depth 8), [Text.UTF8Encoding]::new($false))
            $retainedClaimName = Get-VerifierPortMutexName $null 40125
            $retainedClaim = [ordered]@{ protocol='troubleshootjs-verifier-port-claim-v1'; runId=$retainedContext.RunId; repositoryIdentity=$retainedContext.RepositoryIdentity; worktreeRoot=$retainedContext.WorktreeRoot; leaseId=$retainedLeaseId; path=$retainedClaimPath; kind='cdp'; port=40125; mutexName=$retainedClaimName; ownerPid=$PID; ownerStartTicks=1 }
            $retainedLease = [ordered]@{ runId=$retainedContext.RunId; repositoryIdentity=$retainedContext.RepositoryIdentity; worktreeRoot=$retainedContext.WorktreeRoot; leaseId=$retainedLeaseId; path=$retainedClaimPath; kind='cdp'; port=40125; claimName=$retainedClaimName; browserPath=$ledgerBrowserPath; claimOwnerPid=$PID; claimOwnerStartTicks=1; status='held'; claimState='held'; releaseState='active'; mutexReleased=$false; profile=$retainedProfile; listenerInspectionSuccess=$false; listenerInspectionKnown=$false; listenerHasListeners=$null; boundProcessId=0; boundProcessStartTicks=0; listenerProcessId=0; listenerProcessStartTicks=0; claim=$retainedClaim }
            [IO.File]::WriteAllText($retainedClaimPath, ($retainedClaim | ConvertTo-Json -Depth 8), [Text.UTF8Encoding]::new($false))
            $retainedProfileRecord = [ordered]@{ owner='run'; runId=$retainedContext.RunId; repositoryIdentity=$retainedContext.RepositoryIdentity; worktreeRoot=$retainedContext.WorktreeRoot; profile=$retainedProfile; cdpLeasePath=$retainedClaimPath; cdpPort=40125; browserPath=$ledgerBrowserPath; processId=0; processStartTicks=0; processParentProcessId=0; processParentProcessStartTicks=0; processCommandLine=''; status='startup-failed'; cleanupResult='infrastructure-failure' }
            $retainedServerRoot = Get-VerifierFullPath (Join-Path $retainedContext.RunRoot 'server')
            New-Item -ItemType Directory -Path $retainedServerRoot -Force -ErrorAction Stop | Out-Null
            $retainedServerScript = Get-VerifierFullPath (Join-Path $repoForLedger 'scripts\preview.ps1')
            $retainedServerCommand = 'powershell.exe ' + (ConvertTo-VerifierArgumentString @(
                '-File', $retainedServerScript, '-Port', '40124',
                '-VerifierRunId', $retainedContext.RunId,
                '-VerifierNonce', $retainedContext.PreviewNonce))
            $retainedServer = [pscustomobject]@{
                Owner='run'; BaseUrl='http://127.0.0.1:40124'; RepositoryIdentity=$retainedContext.RepositoryIdentity
                WorktreeRoot=$retainedContext.WorktreeRoot; Port=40124; ProcessId=54321
                ProcessStartTicks=12345L; ProcessParentProcessId=54320
                ProcessParentProcessStartTicks=12344L
                ProcessCommandLine=$retainedServerCommand; Script=$retainedServerScript
                State='started'; RunId=$retainedContext.RunId; Nonce=$retainedContext.PreviewNonce
                StdoutLog=(Join-Path $retainedServerRoot 'stdout.log')
                StderrLog=(Join-Path $retainedServerRoot 'stderr.log')
                CleanupResult='infrastructure-failure'; Error='synthetic retained timeout'
                Lease=$retainedServerLease; Process=$null; ProcessIdentityKnown=$true
                OwnershipUncertain=$false; ProcessTerminationProven=$false; ProcessAbsent=$false
                ListenerInspectionProven=$false; ListenerAbsent=$false
            }
            [IO.File]::WriteAllText($retainedServer.StdoutLog, 'retained server evidence', [Text.UTF8Encoding]::new($false))
            [IO.File]::WriteAllText($retainedServer.StderrLog, '', [Text.UTF8Encoding]::new($false))
            $retainedContext.Server = $retainedServer
            $retainedServerLedger = [ordered]@{
                owner='run'; baseUrl='http://127.0.0.1:40124'; repositoryIdentity=$retainedContext.RepositoryIdentity
                worktreeRoot=$retainedContext.WorktreeRoot; port=40124; processId=54321; processStartTicks=12345L
                processParentProcessId=54320; processParentProcessStartTicks=12344; processCommandLine=$retainedServerCommand; script=$retainedServerScript
                runId=$retainedContext.RunId; nonce=$retainedContext.PreviewNonce
                leaseId=$retainedServerLeaseId; leaseKind='preview'; leaseClaimName=$retainedServerClaimName
                leaseClaimState='held'; leaseReleaseState='active'; leaseOwnerPid=$PID; leaseOwnerStartTicks=1
                leasePath=$retainedServerClaimPath; state='started'
                stdoutLog=$retainedServer.StdoutLog; stderrLog=$retainedServer.StderrLog
                cleanupResult='infrastructure-failure'; processIdentityKnown=$true; ownershipUncertain=$false
                processTerminationProven=$false; processAbsent=$false; listenerInspectionProven=$false; listenerAbsent=$false
            }
            Set-GateBManifestResources $retainedContext -Leases @($retainedServerLease, $retainedLease) `
                -Profiles @($retainedProfileRecord) -Server $retainedServerLedger
            $retainedLedgerPath = Get-VerifierFullPath (Join-Path $retainedParent 'timeout.json')
            Write-GateBLedger $retainedContext $retainedLedgerPath 'resource-started' `
                -Leases @($retainedServerLease, $retainedLease) -Profiles @($retainedProfileRecord) `
                -Server $retainedServerLedger -CleanupState 'infrastructure-failure'
            $retainedLedger = Read-VerifierIntegratedChildLedger $retainedLedgerPath 2 -AllowIncomplete
            if ([string]$retainedLedger.state -ne 'resource-started' -or
                    -not (Test-Path -LiteralPath $retainedClaimPath -PathType Leaf) -or
                    -not (Test-Path -LiteralPath $retainedProfile -PathType Container)) {
                throw 'timeout ledger did not retain canonical claim/profile evidence.'
            }
            $retainedManifestBaseline = Get-Content -LiteralPath $retainedContext.ManifestPath -Raw
            function Assert-GateBLedgerVariantRejected($Variant, [string]$Name,
                    [scriptblock]$ManifestMutation = $null,
                    [scriptblock]$ClaimMutation = $null,
                    [scriptblock]$ClaimRestore = $null) {
                # Keep each mutation beside the retained ledger so the reader
                # reaches the intended resource assertion instead of failing
                # earlier because the ledger file itself moved outside its
                # recorded parent namespace.
                $variantPath = Get-VerifierFullPath (Join-Path $retainedParent ($Name + '.json'))
                try {
                    if ($null -ne $ManifestMutation) {
                        $manifestVariant = Get-Content -LiteralPath $retainedContext.ManifestPath -Raw | ConvertFrom-Json
                        & $ManifestMutation $manifestVariant
                        [IO.File]::WriteAllText($retainedContext.ManifestPath,
                            ($manifestVariant | ConvertTo-Json -Depth 16), [Text.UTF8Encoding]::new($false))
                    }
                    if ($null -ne $ClaimMutation) { & $ClaimMutation }
                    [IO.File]::WriteAllText($variantPath, ($Variant | ConvertTo-Json -Depth 16),
                        [Text.UTF8Encoding]::new($false))
                    $rejected = $false
                    try {
                        [void](Read-VerifierIntegratedChildLedger $variantPath 2 -AllowIncomplete)
                    } catch {
                        $rejected = Test-VerifierInfrastructureError $_
                    }
                    if (-not $rejected) {
                        throw "integrated child ledger variant '$Name' was accepted despite an invalid resource set."
                    }
                } finally {
                    # Canonical-negative variants may alter the real retained
                    # manifest only for the duration of their reader call.
                    if ($null -ne $ClaimRestore) { & $ClaimRestore }
                    if (Test-Path -LiteralPath $retainedContext.ManifestPath -PathType Leaf) {
                        [IO.File]::WriteAllText($retainedContext.ManifestPath,
                            $retainedManifestBaseline, [Text.UTF8Encoding]::new($false))
                    }
                }
            }
            $duplicateLeaseLedger = Get-Content -LiteralPath $retainedLedgerPath -Raw | ConvertFrom-Json
            $duplicateLeaseLedger.leases = @($duplicateLeaseLedger.leases) + @($duplicateLeaseLedger.leases[0])
            Assert-GateBLedgerVariantRejected $duplicateLeaseLedger 'duplicate-claim'
            $duplicateClaimPathLedger = Get-Content -LiteralPath $retainedLedgerPath -Raw | ConvertFrom-Json
            $duplicateClaimPathLease = $duplicateClaimPathLedger.leases[0] | ConvertTo-Json -Depth 16 | ConvertFrom-Json
            $duplicateClaimPathLease.leaseId = [Guid]::NewGuid().ToString('N')
            $duplicateClaimPathLease.claim.leaseId = [string]$duplicateClaimPathLease.leaseId
            $duplicateClaimPathLedger.leases = @($duplicateClaimPathLedger.leases) + @($duplicateClaimPathLease)
            $duplicateClaimPathManifest = Get-Content -LiteralPath $retainedContext.ManifestPath -Raw | ConvertFrom-Json
            $duplicateClaimPathManifestLease = $duplicateClaimPathManifest.leases[0] | ConvertTo-Json -Depth 16 | ConvertFrom-Json
            $duplicateClaimPathManifestLease.leaseId = [string]$duplicateClaimPathLease.leaseId
            $duplicateClaimPathManifest.leases = @($duplicateClaimPathManifest.leases) + @($duplicateClaimPathManifestLease)
            [IO.File]::WriteAllText($retainedContext.ManifestPath,
                ($duplicateClaimPathManifest | ConvertTo-Json -Depth 16), [Text.UTF8Encoding]::new($false))
            Assert-GateBLedgerVariantRejected $duplicateClaimPathLedger 'duplicate-claim-path'
            Set-GateBManifestResources $retainedContext -Leases @($retainedServerLease, $retainedLease) `
                -Profiles @($retainedProfileRecord) -Server $retainedServerLedger
            $omittedLeaseLedger = Get-Content -LiteralPath $retainedLedgerPath -Raw | ConvertFrom-Json
            $omittedLeaseLedger.leases = @()
            Assert-GateBLedgerVariantRejected $omittedLeaseLedger 'omitted-claim'
            $foreignClaimLedger = Get-Content -LiteralPath $retainedLedgerPath -Raw | ConvertFrom-Json
            $foreignClaimLedger.leases[0].path = Get-VerifierFullPath (
                Join-Path $ledgerCanaryRoot 'foreign-claim.lease')
            Assert-GateBLedgerVariantRejected $foreignClaimLedger 'foreign-claim'

            # A second live descriptor with a different ID/path is still an
            # active collision when it reuses the global per-port mutex.
            $duplicateLiveLedger = Get-Content -LiteralPath $retainedLedgerPath -Raw | ConvertFrom-Json
            $duplicateLiveLease = $duplicateLiveLedger.leases[0] | ConvertTo-Json -Depth 16 | ConvertFrom-Json
            $duplicateLiveLeaseId = [Guid]::NewGuid().ToString('N')
            $duplicateLiveClaimPath = Get-VerifierFullPath (Join-Path $retainedContext.PortLeaseRoot `
                ('40124-' + $duplicateLiveLeaseId + '.lease'))
            $duplicateLiveLease.leaseId = $duplicateLiveLeaseId
            $duplicateLiveLease.path = $duplicateLiveClaimPath
            $duplicateLiveLease.claim.leaseId = $duplicateLiveLeaseId
            $duplicateLiveLease.claim.path = $duplicateLiveClaimPath
            $duplicateLiveLedger.leases = @($duplicateLiveLedger.leases) + @($duplicateLiveLease)
            Assert-GateBLedgerVariantRejected $duplicateLiveLedger 'duplicate-live-port' {
                param($manifestVariant)
                $manifestDuplicate = $manifestVariant.leases[0] | ConvertTo-Json -Depth 16 | ConvertFrom-Json
                $manifestDuplicate.leaseId = $duplicateLiveLeaseId
                $manifestDuplicate.path = $duplicateLiveClaimPath
                $manifestDuplicate.claim.leaseId = $duplicateLiveLeaseId
                $manifestDuplicate.claim.path = $duplicateLiveClaimPath
                $manifestVariant.leases = @($manifestVariant.leases) + @($manifestDuplicate)
            } {
                [IO.File]::WriteAllText($duplicateLiveClaimPath,
                    ($duplicateLiveLease.claim | ConvertTo-Json -Depth 8), [Text.UTF8Encoding]::new($false))
            } {
                if (Test-Path -LiteralPath $duplicateLiveClaimPath) {
                    Remove-VerifierOwnedTree $retainedContext.PortLeaseRoot $duplicateLiveClaimPath
                }
            }

            foreach ($invalidPort in @(0, 65536, 70000)) {
                $invalidPortLedger = Get-Content -LiteralPath $retainedLedgerPath -Raw | ConvertFrom-Json
                $invalidPortLedger.leases[0].port = $invalidPort
                $invalidPortLedger.leases[0].claim.port = $invalidPort
                Assert-GateBLedgerVariantRejected $invalidPortLedger `
                    ('invalid-port-' + [string]$invalidPort) {
                    param($manifestVariant)
                    $manifestVariant.leases[0].port = $invalidPort
                    $manifestVariant.leases[0].claim.port = $invalidPort
                }
            }

            $zeroOwnerLedger = Get-Content -LiteralPath $retainedLedgerPath -Raw | ConvertFrom-Json
            $zeroOwnerLedger.leases[0].claimOwnerPid = 0
            $zeroOwnerLedger.leases[0].claim.ownerPid = 0
            Assert-GateBLedgerVariantRejected $zeroOwnerLedger 'zero-claim-owner' {
                param($manifestVariant)
                $manifestVariant.leases[0].claimOwnerPid = 0
                $manifestVariant.leases[0].claim.ownerPid = 0
            }
            $negativeOwnerLedger = Get-Content -LiteralPath $retainedLedgerPath -Raw | ConvertFrom-Json
            $negativeOwnerLedger.leases[0].claimOwnerStartTicks = -1
            $negativeOwnerLedger.leases[0].claim.ownerStartTicks = -1
            Assert-GateBLedgerVariantRejected $negativeOwnerLedger 'negative-claim-owner-start' {
                param($manifestVariant)
                $manifestVariant.leases[0].claimOwnerStartTicks = -1
                $manifestVariant.leases[0].claim.ownerStartTicks = -1
            }

            # A browser profile is paired with a CDP lease, never with the
            # preview/server lease. Mutate the descriptor and retained claim
            # together so this reaches the profile-to-lease kind check.
            $wrongProfileKindLedger = Get-Content -LiteralPath $retainedLedgerPath -Raw | ConvertFrom-Json
            $wrongProfileLeaseIndex = -1
            for ($wrongIndex = 0; $wrongIndex -lt @($wrongProfileKindLedger.leases).Count; $wrongIndex++) {
                if ([string]$wrongProfileKindLedger.leases[$wrongIndex].path -eq $retainedClaimPath) {
                    $wrongProfileLeaseIndex = $wrongIndex
                    break
                }
            }
            if ($wrongProfileLeaseIndex -lt 0) {
                throw 'wrong-profile-lease-kind canary could not locate the paired cdp lease.'
            }
            $wrongProfileKindLedger.leases[$wrongProfileLeaseIndex].kind = 'preview'
            $wrongProfileKindLedger.leases[$wrongProfileLeaseIndex].claim.kind = 'preview'
            $wrongProfileKindClaimBaseline = Get-Content -LiteralPath $retainedClaimPath -Raw
            Assert-GateBLedgerVariantRejected $wrongProfileKindLedger 'wrong-profile-lease-kind' {
                param($manifestVariant)
                $manifestVariant.leases[$wrongProfileLeaseIndex].kind = 'preview'
                $manifestVariant.leases[$wrongProfileLeaseIndex].claim.kind = 'preview'
            } {
                $wrongProfileKindClaim = Get-Content -LiteralPath $retainedClaimPath -Raw | ConvertFrom-Json
                $wrongProfileKindClaim.kind = 'preview'
                [IO.File]::WriteAllText($retainedClaimPath,
                    ($wrongProfileKindClaim | ConvertTo-Json -Depth 8), [Text.UTF8Encoding]::new($false))
            } {
                [IO.File]::WriteAllText($retainedClaimPath, $wrongProfileKindClaimBaseline,
                    [Text.UTF8Encoding]::new($false))
            }

            $duplicateProfileLedger = Get-Content -LiteralPath $retainedLedgerPath -Raw | ConvertFrom-Json
            $duplicateProfileLedger.profiles = @($duplicateProfileLedger.profiles) +
                @($duplicateProfileLedger.profiles[0])
            Assert-GateBLedgerVariantRejected $duplicateProfileLedger 'duplicate-profile'
            $omittedProfileLedger = Get-Content -LiteralPath $retainedLedgerPath -Raw | ConvertFrom-Json
            $omittedProfileLedger.profiles = @()
            Assert-GateBLedgerVariantRejected $omittedProfileLedger 'omitted-profile'
            $foreignProfileLedger = Get-Content -LiteralPath $retainedLedgerPath -Raw | ConvertFrom-Json
            $foreignProfileLedger.profiles[0].profile = Get-VerifierFullPath (
                Join-Path $ledgerCanaryRoot 'foreign-profile')
            Assert-GateBLedgerVariantRejected $foreignProfileLedger 'foreign-profile'

            $omittedServerLedger = Get-Content -LiteralPath $retainedLedgerPath -Raw | ConvertFrom-Json
            $omittedServerLedger.server = $null
            Assert-GateBLedgerVariantRejected $omittedServerLedger 'omitted-server'
            $foreignServerLedger = Get-Content -LiteralPath $retainedLedgerPath -Raw | ConvertFrom-Json
            $foreignServerLedger.server.owner = 'foreign'
            Assert-GateBLedgerVariantRejected $foreignServerLedger 'foreign-server'
            $duplicateServerLedger = Get-Content -LiteralPath $retainedLedgerPath -Raw | ConvertFrom-Json
            $duplicateServerLedger.server = @($duplicateServerLedger.server) + @($duplicateServerLedger.server)
            Assert-GateBLedgerVariantRejected $duplicateServerLedger 'duplicate-server'
            $customMutexLedger = Get-Content -LiteralPath $retainedLedgerPath -Raw | ConvertFrom-Json
            $customMutexLedger.leases[0].claimName = 'Global\TroubleshootJS.Verifier.Port.49999'
            $customMutexLedger.leases[0].claim.mutexName = 'Global\TroubleshootJS.Verifier.Port.49999'
            Assert-GateBLedgerVariantRejected $customMutexLedger 'custom-mutex' {
                param($manifestVariant)
                $manifestVariant.leases[0].claimName = 'Global\TroubleshootJS.Verifier.Port.49999'
                $manifestVariant.leases[0].claim.mutexName = 'Global\TroubleshootJS.Verifier.Port.49999'
            }
            $wrongScriptLedger = Get-Content -LiteralPath $retainedLedgerPath -Raw | ConvertFrom-Json
            $wrongScriptLedger.server.script = Get-VerifierFullPath (Join-Path $repoForLedger 'scripts\start-preview.ps1')
            Assert-GateBLedgerVariantRejected $wrongScriptLedger 'wrong-preview-script' {
                param($manifestVariant)
                $manifestVariant.server.script = Get-VerifierFullPath (Join-Path $repoForLedger 'scripts\start-preview.ps1')
            }
            $wrongBaseUrlLedger = Get-Content -LiteralPath $retainedLedgerPath -Raw | ConvertFrom-Json
            $wrongBaseUrlLedger.server.baseUrl = 'http://localhost:40124/path?foreign=1'
            Assert-GateBLedgerVariantRejected $wrongBaseUrlLedger 'non-loopback-base-url' {
                param($manifestVariant)
                $manifestVariant.server.baseUrl = 'http://localhost:40124/path?foreign=1'
            }
            $wrongPortLedger = Get-Content -LiteralPath $retainedLedgerPath -Raw | ConvertFrom-Json
            $wrongPortLedger.server.port = 40125
            Assert-GateBLedgerVariantRejected $wrongPortLedger 'wrong-server-port' {
                param($manifestVariant)
                $manifestVariant.server.port = 40125
            }
            $foreignLogLedger = Get-Content -LiteralPath $retainedLedgerPath -Raw | ConvertFrom-Json
            $foreignLogLedger.server.stdoutLog = Get-VerifierFullPath (Join-Path $ledgerCanaryRoot 'foreign-server.log')
            Assert-GateBLedgerVariantRejected $foreignLogLedger 'foreign-server-log' {
                param($manifestVariant)
                $manifestVariant.server.stdoutLog = Get-VerifierFullPath (Join-Path $ledgerCanaryRoot 'foreign-server.log')
            }
            Remove-VerifierOwnedTree $retainedContext.PortLeaseRoot $retainedClaimPath
            Remove-VerifierOwnedTree $retainedContext.PortLeaseRoot $retainedServerClaimPath
            Remove-VerifierOwnedTree $retainedContext.RunRoot $retainedProfile
        } catch {
            $ledgerCanaryFailure = $_
        } finally {
            # The fixture root is evidence when any proof fails. Recursive
            # removal is permitted only after every resource assertion passed;
            # otherwise retain manifests, claims, profiles, and variant ledgers
            # for the independent reviewer.
            if ($null -eq $ledgerCanaryFailure -and
                    (Test-Path -LiteralPath $ledgerCanaryRoot)) {
                try { Remove-VerifierOwnedTree (Get-VerifierFullPath ([IO.Path]::GetTempPath())) $ledgerCanaryRoot } catch {
                    if ($null -eq $ledgerCanaryFailure) { $ledgerCanaryFailure = $_ }
                }
            }
        }
        if ($null -ne $ledgerCanaryFailure) {
            Throw-VerifierInfrastructure ('expected-exit-2 ledger resource proof failed; evidence retained at ' +
                $ledgerCanaryRoot + ': ' + (Get-VerifierErrorMessage $ledgerCanaryFailure))
        }
        Write-Host 'PASS:expected-exit-2 ledger resource proof (completed/incomplete/bijective duplicate/live-port/invalid-port/owner-kind/omitted/foreign claim/profile/server/caller cases)'
        $integratedTimeoutObserved = $false
        try {
            invokeIntegratedChild 'Gate B owned-resource timeout' @('-GateBHangAfterContext') 2
        } catch {
            $integratedTimeoutObserved = Test-VerifierInfrastructureError $_
        }
        if (-not $integratedTimeoutObserved) {
            throw 'integrated child owned-resource timeout was not retained as infrastructure.'
        }
        Write-Host 'PASS:CDP timeout, protocol-error, route-deadline, explicit-exit2, and child-status infrastructure probes'
        exit 0
    } catch {
        # Write-Error leaves a non-terminating error record which Windows
        # PowerShell can surface as process exit 1 even when the explicit
        # infrastructure exit below is 2.  Emit the diagnostic directly to
        # stderr so the typed verifier exit contract remains authoritative.
        [Console]::Error.WriteLine('FAIL:CDP infrastructure probes - ' + $_.Exception.Message)
        # This developer-only probe exercises verifier transport/protocol
        # handling. Inability to execute it is infrastructure, never an
        # application assertion result.
        exit 2
    }
}

# One top-level owner closes every run.  Route functions return the historical
# application result; infrastructure exceptions and cleanup failures are
# promoted here to the verifier's exit-2 contract.
$script:VerifierBoundParameters = $PSBoundParameters
$requestedExitCode = 2
$cleanupResult = $null
try {
    $worktreeRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
    $script:VerifierContext = New-VerifierRunContext $worktreeRoot $EvidenceDirectory $ParentNamespaceRoot
    $script:VerifierEvidenceDirectory = $script:VerifierContext.EvidenceDirectory
    if (-not [String]::IsNullOrWhiteSpace($ParentLedgerPath)) {
        Write-VerifierParentLedger 'started'
    }

    if ($GateBExplicitExit2Probe -or $GateBExplicitExit2TypedProbe) {
        $explicitExit2 = [System.InvalidOperationException]::new(
            'deterministic explicit verifier infrastructure exit-2 probe')
        $explicitExit2.Data['VerifierExitCode'] = 2
        if ($GateBExplicitExit2TypedProbe) {
            $explicitExit2.Data['VerifierFailureKind'] = 'infrastructure'
        }
        throw $explicitExit2
    } elseif ($GateBHangAfterContext) {
        # The timeout canary uses the same production lease vocabulary as the
        # integrated reader; its retained claim is a cdp-kind port resource.
        $hangBrowserPath = Resolve-VerifierBrowserPath $BrowserPath
        $hangLease = New-VerifierPortLease $script:VerifierContext 'cdp' 0 $hangBrowserPath
        Write-VerifierParentLedger 'resource-started'
        Write-Host ("INTEGRATED CHILD HANG RESOURCE STARTED runRoot=$($script:VerifierContext.RunRoot) " +
            "claim=$($hangLease.Path) port=$($hangLease.Port)")
        Start-Sleep -Seconds 120
        $requestedExitCode = 0
    } elseif ($Task43Integrated) {
        # Integrated orchestration intentionally does not own a parent preview
        # when BaseUrl is omitted: each child receives its own run context and
        # preview. An explicit BaseUrl is verified once and remains caller-owned.
        if (-not [String]::IsNullOrWhiteSpace($BaseUrl)) {
            [void](Set-VerifierCallerOwnedPreview $script:VerifierContext $BaseUrl)
        }
        $requestedExitCode = [int](InvokeVerifierMain)
    } else {
        # Resolve before starting a run-owned preview so a missing configured
        # browser cannot leave a preview running as a side effect. The same
        # resolver is used by the live Gate B Edge ownership canary.
        $script:VerifierResolvedBrowserPath = Resolve-VerifierBrowserPath $BrowserPath
        if ([String]::IsNullOrWhiteSpace($BaseUrl)) {
            $BaseUrl = [string](Start-VerifierOwnedPreview $script:VerifierContext `
                (Join-Path $worktreeRoot 'scripts\preview.ps1') $TimeoutSeconds)
        } else {
            $BaseUrl = [string](Set-VerifierCallerOwnedPreview $script:VerifierContext $BaseUrl)
        }
        $requestedExitCode = [int](InvokeVerifierMain)
    }
} catch {
    if ($null -eq $script:VerifierContext) {
        # Context construction is infrastructure by definition.  Keep this
        # path explicit so directory, manifest, or evidence setup failures can
        # never escape as a generic application result.
        $setupMessage = Get-VerifierErrorMessage $_
        $script:VerifierFailureExitCode = 2
        $script:VerifierFailureKind = 'infrastructure'
        $script:VerifierFailureMessage = $setupMessage
        Write-Host "FAIL verifier setup - verifier infrastructure`: $setupMessage"
        $requestedExitCode = 2
    } else {
        Set-VerifierFailure $_ 'verifier run'
        $requestedExitCode = Get-VerifierRequestedExitCode $_
    }
} finally {
    if ($null -ne $script:VerifierContext) {
        $cleanupExceptionCaught = $false
        try {
            $cleanupResult = Complete-VerifierRun $script:VerifierContext
        } catch {
            # Cleanup is verifier infrastructure. Do not let an unexpected
            # Complete-VerifierRun exception escape the finally block as the
            # host's generic application exit 1.
            $cleanupException = $_
            try {
                Throw-VerifierInfrastructure ('verifier cleanup threw before a result was recorded: ' +
                    (Get-VerifierErrorMessage $cleanupException))
            } catch {
                Set-VerifierFailure $_ 'verifier cleanup'
            }
            $requestedExitCode = 2
            $cleanupExceptionCaught = $true
        }
        if (-not $cleanupExceptionCaught -and
                ($null -eq $cleanupResult -or -not [bool]$cleanupResult.Success)) {
            $cleanupErrors = if ($cleanupResult) {
                @($cleanupResult.Errors) -join '; '
            } else { 'Complete-VerifierRun returned no result.' }
            try {
                Throw-VerifierInfrastructure ('verifier cleanup did not prove complete: ' +
                    $cleanupErrors)
            } catch {
                Set-VerifierFailure $_ 'verifier cleanup'
            }
            $requestedExitCode = 2
        }
        if (-not [String]::IsNullOrWhiteSpace($ParentLedgerPath)) {
            try {
                $ledgerState = if ($cleanupResult -and [bool]$cleanupResult.Success) {
                    'completed'
                } else { 'cleanup-failed' }
                $ledgerError = if ($cleanupResult -and $cleanupResult.Errors) {
                    @($cleanupResult.Errors) -join '; '
                } else { '' }
                Write-VerifierParentLedger $ledgerState $ledgerError
            } catch {
                $requestedExitCode = 2
                try { Set-VerifierFailure $_ 'integrated child ledger' -Quiet } catch { }
            }
        }
    }
}
if ($script:VerifierFailureExitCode -eq 2 -and $requestedExitCode -ne 2) {
    $requestedExitCode = 2
}
exit ([int]$requestedExitCode)
