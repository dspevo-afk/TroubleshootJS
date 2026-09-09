[CmdletBinding()]
param(
    [AllowEmptyString()]
    [string]$BaseUrl = '',
    $TimeoutSeconds = 90,
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
    [switch]$Task43PRuntime,
    [string]$Task43PFamily = '',
    [switch]$Task43PForcedNegative,
    [string]$Task43PSourceExperiment = '',
    $Task43PStartupSettleMilliseconds = 0,
    [switch]$Task43Integrated,
    [int]$PlayerSeed = 3,
    [string]$EvidenceDirectory,
    [switch]$PersistentPreviewEvidence,
    [switch]$GateBListenerProofProbe,
    [switch]$GateBContractProbe,
    [switch]$GateBForcedNegativeProofProbe,
    [switch]$GateBContractProbeFailure,
    [switch]$GateBExplicitExit2Probe,
    [switch]$GateBExplicitExit2TypedProbe,
    [switch]$GateBHangAfterContext,
    [string]$ParentLedgerPath = '',
    [string]$ParentNamespaceRoot = '',
    [AllowEmptyString()]
    [string]$ExecutionRepositoryRoot = '',
    [AllowEmptyString()]
    [string]$ExecutionWebRoot = '',
    [AllowEmptyString()]
    [string]$ExecutionScriptRoot = '',
    [AllowEmptyString()]
    [string]$ExpectedExecutionProvenanceDigest = '',
    [AllowEmptyString()]
    [string]$ExpectedCandidateSha = ''
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
try {
    . (Join-Path $PSScriptRoot 'Task43PCandidateIdentity.ps1')
    . (Join-Path $PSScriptRoot 'Task43PRuntimeEvidence.ps1')
    . (Join-Path $PSScriptRoot 'Task43PPublicActionEvidence.ps1')
} catch {
    Write-VerifierEarlySetupFailure ('Task43P evidence helpers could not be loaded: ' +
        (Get-VerifierEarlySetupMessage $_))
    exit 2
}
function ConvertTo-Task43PStartupSettleMilliseconds($Value) {
    # This is a CLI boundary; only canonical decimal strings may become an
    # exact integer. Internal delay calls below still require an integer.
    if ((Test-VerifierStrictStringValue $Value) -and $Value -match '^(0|[1-9][0-9]*)$') {
        $parsed = 0L
        if ([long]::TryParse($Value, [Globalization.NumberStyles]::None,
                [Globalization.CultureInfo]::InvariantCulture, [ref]$parsed)) {
            $Value = $parsed
        }
    }
    if (-not (Test-VerifierStrictIntegralValue $Value 0L 45000L)) {
        Throw-VerifierInfrastructure 'Task43P startup settle must be an exact integer from 0 through 45000 milliseconds.'
    }
    return [int]$Value
}

try {
    # PowerShell presents an untyped command-line scalar as a string.  Accept
    # only its canonical decimal form at this script boundary, then hand the
    # resulting exact integral value to the shared validator.  Internal calls
    # to Assert-VerifierTimeoutSeconds remain strict about numeric strings.
    if ((Test-VerifierStrictStringValue $TimeoutSeconds) -and
            $TimeoutSeconds -match '^[0-9]+$') {
        $parsedTimeoutSeconds = 0L
        if ([long]::TryParse($TimeoutSeconds,
                [Globalization.NumberStyles]::None,
                [Globalization.CultureInfo]::InvariantCulture,
                [ref]$parsedTimeoutSeconds)) {
            $TimeoutSeconds = $parsedTimeoutSeconds
        }
    }
    $TimeoutSeconds = Assert-VerifierTimeoutSeconds $TimeoutSeconds 'verify-browser TimeoutSeconds'
    $Task43PStartupSettleMilliseconds = ConvertTo-Task43PStartupSettleMilliseconds `
        $Task43PStartupSettleMilliseconds
    if ($Task43PStartupSettleMilliseconds -gt 0 -and -not ($Task43P -or $Task43PForcedNegative)) {
        Throw-VerifierInfrastructure 'Task43P startup settle requires a Task43P route selection.'
    }
    if ($Task43PFamily -cne '' -and
            (-not $Task43P -or $Task43PForcedNegative -or
                $Task43PFamily -cnotin @('led', 'diode', 'rc', 'npn', 'nmos', 'parallel'))) {
        Throw-VerifierInfrastructure 'Task43P family selection requires a normal Task43P route and one exact supported family.'
    }
    if ($Task43PRuntime) {
        $runtimeAllowedParameters = @('Task43P', 'Task43PRuntime', 'BaseUrl',
            'TimeoutSeconds', 'BrowserPath', 'EvidenceDirectory',
            'Task43PStartupSettleMilliseconds', 'ExecutionRepositoryRoot',
            'ExecutionWebRoot', 'ExecutionScriptRoot', 'ExpectedExecutionProvenanceDigest',
            'ExpectedCandidateSha')
        if (-not $Task43P -or @($PSBoundParameters.Keys | Where-Object {
                $_ -notin $runtimeAllowedParameters }).Count -ne 0) {
            Throw-VerifierInfrastructure 'Task43P runtime requires its sole normal Task43P route with the fixed runtime corpus.'
        }
    }
} catch {
    [Console]::Error.WriteLine('FAIL verifier setup - verifier infrastructure exit 2: ' +
        (Get-VerifierEarlySetupMessage $_))
    exit 2
}

# All evidence I/O after module initialization is verifier infrastructure. Keep
# these wrappers at the script boundary so directory/file failures cannot be
# reclassified as an application assertion by a later generic catch.
function New-VerifierEvidenceDirectory([string]$Path, [string]$Description,
        [switch]$Force) {
    try {
        if ($Force) {
            New-Item -ItemType Directory -Path $Path -Force -ErrorAction Stop | Out-Null
        } else {
            New-Item -ItemType Directory -Path $Path -ErrorAction Stop | Out-Null
        }
    } catch {
        if (Test-VerifierInfrastructureError $_) { throw }
        Throw-VerifierInfrastructure ("Could not create $Description directory '$Path': " +
            (Get-VerifierErrorMessage $_))
    }
}

function Write-VerifierEvidenceText([string]$Path, [string]$Text,
        [string]$Description) {
    try {
        [IO.File]::WriteAllText($Path, $Text, [Text.UTF8Encoding]::new($false))
    } catch {
        if (Test-VerifierInfrastructureError $_) { throw }
        Throw-VerifierInfrastructure ("Could not write $Description '$Path': " +
            (Get-VerifierErrorMessage $_))
    }
}

function Write-VerifierEvidenceBytes([string]$Path, [byte[]]$Bytes,
        [string]$Description) {
    try {
        [IO.File]::WriteAllBytes($Path, $Bytes)
    } catch {
        if (Test-VerifierInfrastructureError $_) { throw }
        Throw-VerifierInfrastructure ("Could not write $Description '$Path': " +
            (Get-VerifierErrorMessage $_))
    }
}

$script:VerifierContext = $null
$script:VerifierEvidenceDirectory = ''
$script:VerifierFailureExitCode = 0
$script:VerifierFailureMessage = ''
$script:VerifierFailureKind = ''
$script:VerifierCurrentRouteId = ''
$script:VerifierResolvedBrowserPath = ''
$script:Task43PHistoricalBaselineSha =
    '8bf442416a2fa2c0c9d654d1efa14e754c2b7ee7'
$script:Task43PCandidateSha = ''
$script:Task43PInvocationRepositoryState = $null
if ($PSBoundParameters.ContainsKey('ExpectedCandidateSha')) {
    try {
        $script:Task43PCandidateSha = Get-Task43PCandidateSha `
            ([IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))) $ExpectedCandidateSha
    } catch {
        Write-VerifierEarlySetupFailure (Get-VerifierEarlySetupMessage $_)
        exit 2
    }
}
$script:Task43ForcedNegativeProof = [pscustomobject]@{
    Invocation = $false
    ExpectedMarker = ''
    ExpectedRoute = ''
    RunId = ''
    RouteId = ''
    Nonce = ''
    RequestId = ''
    ExecutionDigest = ''
    MarkerObserved = $false
    ObservedMarker = ''
    MarkerRunId = ''
    MarkerRouteId = ''
    MarkerExpectedMarker = ''
    AnchoredDiagnosticProven = $false
    AnchoredJavaDiagnostic = ''
    DiagnosticExpectedMarker = ''
    DiagnosticRunId = ''
    DiagnosticRouteId = ''
    DiagnosticBaselineHead = ''
    RoutePassedAfterCleanup = $false
    FinalCleanupProven = $false
    Invalidated = $false
}
$script:task43ExpectedFailureObserved = $false
$script:task43ExpectedFailureRoutePassed = $false
$script:Task43PExecutionRepositoryRoot = ''
$script:Task43PExecutionWebRoot = ''
$script:Task43PExecutionScriptRoot = ''
$script:Task43PExecutionPreviewScript = ''
$script:Task43PExecutionRoots = $null
$script:Task43PExecutionProvenance = $null
$script:Task43PExpectedExecutionProvenanceDigest = ''
$script:Task43PSourceDefinition = $null
$script:Task43PSourceObservation = $null
$script:Task43PRuntimeOutcome = $null
$script:Task43PRuntimeRoutePassedAfterCleanup = $false
$script:VerifierParentLedgerWritten = $false
# Allow synchronous developer-proof gaps to exceed one CDP receive while the route deadline remains authoritative.
$CdpReceiveTimeoutMilliseconds = 60000
$CdpSendTimeoutMilliseconds = 5000
$script:CdpRouteDeadline = [DateTime]::MinValue

function Resolve-Task43PExecutionRoots() {
    $provided = @(
        foreach ($rootValue in @($ExecutionRepositoryRoot, $ExecutionWebRoot,
                $ExecutionScriptRoot)) {
            if ($null -ne $rootValue -and $rootValue.GetType() -ne [string]) {
                Throw-VerifierInfrastructure 'Task43P execution roots must remain exact strings before path normalization.'
            }
            if (-not [String]::IsNullOrWhiteSpace($rootValue)) {
                $rootValue
            }
        }
    )
    $repositoryRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
    if ($provided.Count -ne 0 -and $provided.Count -ne 3) {
        Throw-VerifierInfrastructure 'Task43P execution roots must provide repository, web, and script roots together.'
    }
    if ($provided.Count -ne 0 -and -not ($Task43P -or $Task43PForcedNegative)) {
        Throw-VerifierInfrastructure 'Explicit Task43P execution roots are developer-only and require a Task43P route.'
    }
    if ($provided.Count -eq 3) {
        # The repository wrapper remains the provenance owner. These explicit
        # roots only select the isolated compiled source/extraction surface and
        # must describe one disposable tree whose conventional war/scripts
        # children are the exact paths served by its preview identity.
        $repositoryRoot = Get-VerifierFullPath $ExecutionRepositoryRoot
        $webRoot = Get-VerifierFullPath $ExecutionWebRoot
        $scriptRoot = Get-VerifierFullPath $ExecutionScriptRoot
    } else {
        $repositoryRoot = Get-VerifierFullPath $repositoryRoot
        $webRoot = Get-VerifierFullPath (Join-Path $repositoryRoot 'war')
        $scriptRoot = Get-VerifierFullPath (Join-Path $repositoryRoot 'scripts')
    }
    $expectedWebRoot = Get-VerifierFullPath (Join-Path $repositoryRoot 'war')
    $expectedScriptRoot = Get-VerifierFullPath (Join-Path $repositoryRoot 'scripts')
    if (-not (Test-VerifierCanonicalWindowsPathValue $webRoot $expectedWebRoot) -or
            -not (Test-VerifierCanonicalWindowsPathValue $scriptRoot $expectedScriptRoot)) {
        Throw-VerifierInfrastructure 'Task43P execution web/script roots did not match the selected repository root.'
    }
    if (-not (Test-Path -LiteralPath $repositoryRoot -PathType Container) -or
            -not (Test-Path -LiteralPath $webRoot -PathType Container) -or
            -not (Test-Path -LiteralPath $scriptRoot -PathType Container)) {
        Throw-VerifierInfrastructure 'Task43P execution roots were not existing directories.'
    }
    $sourceRoot = Get-VerifierFullPath (Join-Path $repositoryRoot 'src')
    $previewScript = Get-VerifierFullPath (Join-Path $scriptRoot 'preview.ps1')
    if (-not (Test-Path -LiteralPath $sourceRoot -PathType Container) -or
            -not (Test-Path -LiteralPath $previewScript -PathType Leaf)) {
        Throw-VerifierInfrastructure 'Task43P execution roots did not contain source and preview inputs.'
    }
    Assert-VerifierNoReparseAncestors $repositoryRoot
    [void](Assert-VerifierPhysicalOwnedPath $repositoryRoot $sourceRoot -ValidateTree)
    [void](Assert-VerifierPhysicalOwnedPath $repositoryRoot $webRoot -ValidateTree)
    [void](Assert-VerifierPhysicalOwnedPath $repositoryRoot $scriptRoot -ValidateTree)
    [void](Assert-VerifierPhysicalOwnedPath $scriptRoot $previewScript)
    $provenance = Get-VerifierExecutionTreeProvenance $repositoryRoot
    if (-not [String]::IsNullOrWhiteSpace($ExpectedExecutionProvenanceDigest)) {
        if (-not (Test-VerifierStrictStringValue $ExpectedExecutionProvenanceDigest) -or
                $ExpectedExecutionProvenanceDigest -notmatch '^[0-9a-f]{64}$' -or
                $provenance.Digest -cne $ExpectedExecutionProvenanceDigest) {
            Throw-VerifierInfrastructure 'Task43P selected execution tree did not match the expected candidate provenance digest.'
        }
    }
    return [pscustomobject]@{
        RepositoryRoot = $repositoryRoot
        WebRoot = $webRoot
        ScriptRoot = $scriptRoot
        PreviewScript = $previewScript
        Explicit = ($provided.Count -eq 3)
        Provenance = $provenance
    }
}

function Assert-Task43PPreviewExecutionIdentity([string]$BaseUrl) {
    if ($null -eq $script:Task43PExecutionRoots -or
            $null -eq $script:Task43PExecutionProvenance) {
        Throw-VerifierInfrastructure 'Task43P preview identity was checked before selected execution provenance was established.'
    }
    try {
        $uri = [Uri]$BaseUrl
        if ($uri.Scheme -ne 'http' -or $uri.Host -notin @('127.0.0.1', 'localhost') -or
                $uri.AbsolutePath -notin @('', '/') -or $uri.Query -or $uri.Fragment -or
                $uri.Port -lt 1 -or $uri.Port -gt 65535) {
            Throw-VerifierInfrastructure 'Task43P preview identity request required a loopback preview root URL.'
        }
        $response = Invoke-WebRequest -UseBasicParsing `
            -Uri ($BaseUrl.TrimEnd('/') + '/__tsj/verify-identity') -TimeoutSec 5
        $identity = $response.Content | ConvertFrom-Json -ErrorAction Stop
        if ($null -eq $identity -or $identity -is [System.Array] -or
                $identity -isnot [pscustomobject]) {
            Throw-VerifierInfrastructure 'Task43P preview identity was not an exact JSON object.'
        }
        $allowed = @('protocol', 'repositoryRoot', 'previewScript', 'webRoot',
            'sourceRoot', 'scriptRoot', 'sourceDigest', 'scriptDigest', 'webDigest',
            'executionDigest', 'executionFileCount', 'previewPort', 'processId',
            'processStartTicks', 'verifierRunId', 'verifierNonce')
        foreach ($property in @($identity.PSObject.Properties)) {
            if ($allowed -cnotcontains [string]$property.Name) {
                Throw-VerifierInfrastructure "Task43P preview identity contained unknown field '$($property.Name)'."
            }
        }
        foreach ($name in @('protocol', 'repositoryRoot', 'previewScript', 'webRoot',
                'sourceRoot', 'scriptRoot', 'sourceDigest', 'scriptDigest', 'webDigest',
                'executionDigest', 'verifierRunId', 'verifierNonce')) {
            if ($null -eq $identity.PSObject.Properties[$name] -or
                    -not (Test-VerifierStrictStringValue $identity.PSObject.Properties[$name].Value)) {
                Throw-VerifierInfrastructure "Task43P preview identity omitted or malformed '$name'."
            }
        }
        foreach ($name in @('previewPort', 'processId', 'processStartTicks',
                'executionFileCount')) {
            if ($null -eq $identity.PSObject.Properties[$name] -or
                    -not (Test-VerifierStrictIntegralValue $identity.PSObject.Properties[$name].Value 0 ([long]::MaxValue))) {
                Throw-VerifierInfrastructure "Task43P preview identity omitted or malformed '$name'."
            }
        }
        foreach ($name in @('sourceDigest', 'scriptDigest', 'webDigest', 'executionDigest')) {
            if ([string]$identity.$name -notmatch '^[0-9a-f]{64}$') {
                Throw-VerifierInfrastructure "Task43P preview identity carried a malformed '$name'."
            }
        }
        $provenance = $script:Task43PExecutionProvenance
        if ($identity.protocol -cne 'troubleshootjs-preview-identity-v1' -or
                -not (Test-VerifierCanonicalWindowsPathValue $identity.repositoryRoot `
                    $provenance.RepositoryRoot) -or
                -not (Test-VerifierCanonicalWindowsPathValue $identity.previewScript `
                    $script:Task43PExecutionPreviewScript) -or
                -not (Test-VerifierCanonicalWindowsPathValue $identity.webRoot `
                    $provenance.WebRoot) -or
                -not (Test-VerifierCanonicalWindowsPathValue $identity.sourceRoot `
                    $provenance.SourceRoot) -or
                -not (Test-VerifierCanonicalWindowsPathValue $identity.scriptRoot `
                    $provenance.ScriptRoot) -or
                [int]$identity.previewPort -ne [int]$uri.Port -or
                $identity.sourceDigest -cne $provenance.SourceDigest -or
                $identity.scriptDigest -cne $provenance.ScriptDigest -or
                $identity.webDigest -cne $provenance.WebDigest -or
                $identity.executionDigest -cne $provenance.Digest -or
                [long]$identity.executionFileCount -ne [long]$provenance.FileCount) {
            Throw-VerifierInfrastructure 'Task43P preview identity did not match the selected execution tree provenance.'
        }
        if ([int]$identity.processId -le 0 -or [long]$identity.processStartTicks -le 0) {
            Throw-VerifierInfrastructure 'Task43P preview identity did not provide a positive process start identity.'
        }
        return $identity
    } catch {
        if (Test-VerifierInfrastructureError $_) { throw }
        Throw-VerifierInfrastructure ('Task43P preview identity verification failed: ' +
            (Get-VerifierErrorMessage $_))
    }
}

function Get-VerifierDurableBooleanValue($Object, [string]$Name,
        [string]$Label, [switch]$AllowNull) {
    $dictionaryKey = $null
    $hasProperty = if ($Object -is [System.Collections.IDictionary]) {
        foreach ($key in $Object.Keys) {
            if ([string]::Equals([string]$key, $Name,
                    [StringComparison]::OrdinalIgnoreCase)) {
                $dictionaryKey = $key
                break
            }
        }
        $null -ne $dictionaryKey
    } else {
        $null -ne $Object -and $null -ne $Object.PSObject.Properties[$Name]
    }
    if ($null -eq $Object -or [String]::IsNullOrWhiteSpace($Name) -or
            -not $hasProperty) {
        Throw-VerifierInfrastructure "Durable record omitted required Boolean field '$Label'."
    }
    $value = if ($Object -is [System.Collections.IDictionary]) {
        $Object[$dictionaryKey]
    } else {
        $Object.PSObject.Properties[$Name].Value
    }
    if ($null -eq $value) {
        if ($AllowNull) { return $null }
        Throw-VerifierInfrastructure "Durable record field '$Label' was null instead of an exact Boolean."
    }
    if ($value.GetType() -ne [bool]) {
        Throw-VerifierInfrastructure "Durable record field '$Label' was not an exact Boolean."
    }
    return ,$value
}

function Assert-VerifierParentLedgerListenerOwnerTuple($Lease,
        [string]$Label = 'integrated child lease') {
    if ($null -eq $Lease) {
        Throw-VerifierInfrastructure "$Label was null before durable serialization."
    }
    foreach ($propertyName in @('ListenerProcessId', 'ListenerProcessStartTicks',
            'ListenerOwnerKind', 'ListenerOwnerProof', 'ListenerOwnerEvidence')) {
        if ($null -eq $Lease.PSObject.Properties[$propertyName]) {
            Throw-VerifierInfrastructure "$Label omitted required durable listener owner field '$propertyName'."
        }
    }
    # The module owns the security policy. Keep this wrapper responsible only
    # for the parent-ledger field boundary and delegate tuple semantics.
    Assert-VerifierDurableListenerOwnerTuple $Lease $Label
}

function Assert-VerifierParentLedgerContextComplete($Context) {
    if ($null -eq $Context) {
        Throw-VerifierInfrastructure 'Integrated child ledger context was null before durable serialization.'
    }
    foreach ($propertyName in @('RunId', 'RepositoryIdentity', 'WorktreeRoot',
            'RunRoot', 'ManifestPath', 'EvidenceDirectory', 'CleanupState')) {
        $property = $Context.PSObject.Properties[$propertyName]
        if ($null -eq $property -or
                -not (Test-VerifierStrictStringValue $property.Value)) {
            Throw-VerifierInfrastructure "Integrated child ledger context omitted or malformed '$propertyName'."
        }
    }
    foreach ($collectionName in @('Artifacts', 'BrowserSessions', 'CleanupErrors',
            'LeaseRecords')) {
        $property = $Context.PSObject.Properties[$collectionName]
        if ($null -eq $property -or $null -eq $property.Value -or
                $property.Value -is [string] -or
                -not ($property.Value -is [System.Collections.IEnumerable])) {
            Throw-VerifierInfrastructure "Integrated child ledger context omitted or malformed collection '$collectionName'."
        }
    }
    $serverProperty = $Context.PSObject.Properties['Server']
    if ($null -eq $serverProperty) {
        Throw-VerifierInfrastructure 'Integrated child ledger context omitted its explicit Server owner state.'
    }
    # A null Server is the explicit ownerless/caller-free state.  A non-null
    # server is always validated by the module before it can be projected.
    Assert-VerifierDurableServerLease $Context -AllowMissing
    foreach ($lease in @($Context.LeaseRecords)) {
        Assert-VerifierDurableLeaseRecord $lease $Context 'integrated child lease'
    }
    foreach ($artifact in @($Context.Artifacts)) {
        if ($null -eq $artifact -or -not (Test-VerifierStrictStringValue $artifact)) {
            Throw-VerifierInfrastructure 'Integrated child ledger carried a malformed evidence artifact entry.'
        }
    }
    foreach ($session in @($Context.BrowserSessions)) {
        # Durable session schema and lease association are owned by the
        # module. The integrated ledger must consume the same strict validator
        # instead of maintaining a weaker parallel projection contract.
        Assert-VerifierDurableBrowserSession $session $Context `
            'integrated child browser session'
    }
    foreach ($errorItem in @($Context.CleanupErrors)) {
        if ($null -eq $errorItem -or -not (Test-VerifierStrictStringValue $errorItem)) {
            Throw-VerifierInfrastructure 'Integrated child ledger carried a malformed cleanup error entry.'
        }
    }
}

function Write-VerifierParentLedger($State, $ErrorMessage = '') {
    if ([String]::IsNullOrWhiteSpace($ParentLedgerPath) -or
            $null -eq $script:VerifierContext) { return }
    try {
        if (-not (Test-VerifierStrictStringValue $State) -or
                -not (Test-VerifierStrictStringValue $ErrorMessage)) {
            Throw-VerifierInfrastructure 'Integrated child ledger writer received malformed lifecycle text.'
        }
        Assert-VerifierParentLedgerContextComplete $script:VerifierContext
        foreach ($lease in @($script:VerifierContext.LeaseRecords)) {
            Assert-VerifierParentLedgerListenerOwnerTuple $lease 'integrated child lease'
        }
        if ($null -ne $script:VerifierContext.Server -and
                $script:VerifierContext.Server.PSObject.Properties['Lease'] -and
                $null -ne $script:VerifierContext.Server.Lease) {
            Assert-VerifierParentLedgerListenerOwnerTuple `
                $script:VerifierContext.Server.Lease 'integrated child server lease'
        }
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
                registered = Get-VerifierDurableBooleanValue $_ 'Registered' 'lease registered'
                releaseBlocked = Get-VerifierDurableBooleanValue $_ 'ReleaseBlocked' 'lease releaseBlocked'
                releaseBlockReason = $_.ReleaseBlockReason
                claimState = $_.ClaimState
                  releaseState = $_.ReleaseState
                  releaseJournalState = $_.ReleaseJournalState
                  browserPath = $_.BrowserPath
                  claimOwnerPid = $_.ClaimOwnerPid
                  claimOwnerStartTicks = $_.ClaimOwnerStartTicks
                  mutexReleased = Get-VerifierDurableBooleanValue $_ 'MutexReleased' 'lease mutexReleased'
                 boundProcessId = $_.BoundProcessId
                 boundProcessStartTicks = $_.BoundProcessStartTicks
                 listenerProcessId = $_.ListenerProcessId
                 listenerProcessStartTicks = $_.ListenerProcessStartTicks
                 listenerOwnerKind = $_.ListenerOwnerKind
                 listenerOwnerProof = $_.ListenerOwnerProof
                 listenerOwnerEvidence = $_.ListenerOwnerEvidence
                claim = [ordered]@{
                    protocol = 'troubleshootjs-verifier-port-claim-v1'
                    runId = $script:VerifierContext.RunId
                    repositoryIdentity = $script:VerifierContext.RepositoryIdentity
                    worktreeRoot = $script:VerifierContext.WorktreeRoot
                    leaseId = $_.LeaseId; path = $_.Path; kind = $_.Kind; port = $_.Port
                    mutexName = $_.ClaimName
                    ownerPid = $_.ClaimOwnerPid
                    ownerStartTicks = $_.ClaimOwnerStartTicks
                }
                bindValidatedUtc = $_.BindValidatedUtc
                releasedUtc = $_.ReleasedUtc
                 profile = $_.ProfilePath
                 processTerminationProven = Get-VerifierDurableBooleanValue $_ `
                     'ProcessTerminationProven' 'lease processTerminationProven'
                 processAbsent = Get-VerifierDurableBooleanValue $_ 'ProcessAbsent' `
                     'lease processAbsent'
                 listenerInspectionSuccess = Get-VerifierDurableBooleanValue $_ `
                     'ListenerInspectionSuccess' 'lease listenerInspectionSuccess'
                listenerInspectionKnown = Get-VerifierDurableBooleanValue $_ `
                    'ListenerInspectionKnown' 'lease listenerInspectionKnown'
                listenerInspectionUtc = $_.ListenerInspectionUtc
                listenerHasListeners = Get-VerifierDurableBooleanValue $_ `
                    'ListenerHasListeners' 'lease listenerHasListeners' -AllowNull
                listenerAbsent = Get-VerifierDurableBooleanValue $_ `
                    'ListenerAbsent' 'lease listenerAbsent' -AllowNull
                processProofRequired = Get-VerifierDurableBooleanValue $_ `
                    'ProcessProofRequired' 'lease processProofRequired'
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
                processParentProcessId = $_.ProcessParentProcessId
                processParentProcessStartTicks = $_.ProcessParentProcessStartTicks
                processCommandLine = $_.ProcessCommandLine
                browserPath = $_.BrowserPath
                routeId = $_.RouteId; routeName = $_.RouteName
                targetId = $_.TargetId; expectedUrl = $_.ExpectedUrl
                expectedRunMarker = 'tsjVerifierRun=' + $_.RunId
                expectedRouteMarker = 'tsjVerifierRoute=' + $_.RouteId
                status = $_.Status; cleanupResult = $_.CleanupResult
                profileProcessScanCompleted = Get-VerifierDurableBooleanValue $_ `
                    'ProfileProcessScanCompleted' 'profile process scan completed'
                profileInspectionFailed = Get-VerifierDurableBooleanValue $_ `
                    'ProfileInspectionFailed' 'profile inspection failed'
                error = $_.Error
            }
        })
        $ledger = [ordered]@{
            protocol = 'troubleshootjs-integrated-child-ledger-v1'
            state = $State
            forcedNegativeProof = Get-Task43ForcedNegativeProofRecord
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
                     repositoryRoot = $script:VerifierContext.Server.RepositoryRoot
                     webRoot = $script:VerifierContext.Server.WebRoot
                     identityProtocol = $script:VerifierContext.Server.IdentityProtocol
                     identityVerified = Get-VerifierDurableBooleanValue `
                         $script:VerifierContext.Server 'IdentityVerified' 'server identityVerified'
                     callerOwned = Get-VerifierDurableBooleanValue `
                         $script:VerifierContext.Server 'CallerOwned' 'server callerOwned'
                     processId = $script:VerifierContext.Server.ProcessId
                    processStartTicks = $script:VerifierContext.Server.ProcessStartTicks
                    processParentProcessId = $script:VerifierContext.Server.ProcessParentProcessId
                    processParentProcessStartTicks = $script:VerifierContext.Server.ProcessParentProcessStartTicks
                     processCommandLine = $script:VerifierContext.Server.ProcessCommandLine
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
                             $null -ne $script:VerifierContext.Server.Lease) {
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
                            $null -ne $script:VerifierContext.Server.Lease) {
                        Get-VerifierDurableBooleanValue $script:VerifierContext.Server.Lease `
                            'ListenerAbsent' 'server lease listenerAbsent' -AllowNull
                    } else { $null }
                     leaseProcessProofRequired = if ($script:VerifierContext.Server.PSObject.Properties['Lease'] -and
                             $null -ne $script:VerifierContext.Server.Lease) {
                         Get-VerifierDurableBooleanValue $script:VerifierContext.Server.Lease `
                             'ProcessProofRequired' 'server lease processProofRequired'
                     } else { $null }
                     leaseListenerOwnerKind = if ($script:VerifierContext.Server.PSObject.Properties['Lease'] -and
                             $null -ne $script:VerifierContext.Server.Lease) {
                         $script:VerifierContext.Server.Lease.ListenerOwnerKind
                     } else { 'none' }
                     leaseListenerOwnerProof = if ($script:VerifierContext.Server.PSObject.Properties['Lease'] -and
                             $null -ne $script:VerifierContext.Server.Lease) {
                         $script:VerifierContext.Server.Lease.ListenerOwnerProof
                     } else { '' }
                     leaseListenerOwnerEvidence = if ($script:VerifierContext.Server.PSObject.Properties['Lease'] -and
                             $null -ne $script:VerifierContext.Server.Lease) {
                         $script:VerifierContext.Server.Lease.ListenerOwnerEvidence
                     } else { '' }
                    state = $script:VerifierContext.Server.State
                    cleanupResult = $script:VerifierContext.Server.CleanupResult
                    error = $script:VerifierContext.Server.Error
                    processIdentityKnown = Get-VerifierDurableBooleanValue `
                        $script:VerifierContext.Server 'ProcessIdentityKnown' 'server processIdentityKnown'
                    ownershipUncertain = Get-VerifierDurableBooleanValue `
                        $script:VerifierContext.Server 'OwnershipUncertain' 'server ownershipUncertain'
                    processTerminationProven = Get-VerifierDurableBooleanValue `
                        $script:VerifierContext.Server 'ProcessTerminationProven' 'server processTerminationProven'
                    processAbsent = Get-VerifierDurableBooleanValue `
                        $script:VerifierContext.Server 'ProcessAbsent' 'server processAbsent'
                    listenerInspectionProven = Get-VerifierDurableBooleanValue `
                        $script:VerifierContext.Server 'ListenerInspectionProven' 'server listenerInspectionProven'
                    listenerAbsent = Get-VerifierDurableBooleanValue `
                        $script:VerifierContext.Server 'ListenerAbsent' 'server listenerAbsent' -AllowNull
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
            Write-VerifierEvidenceText $temporary ($ledger | ConvertTo-Json -Depth 12) `
                'integrated child ledger temporary evidence'
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

function Invoke-VerifierParentLedgerWriterTupleCanary($ValidLease) {
    $priorContext = $script:VerifierContext
    $priorLedgerPath = $script:ParentLedgerPath
    $priorNamespaceRoot = $script:ParentNamespaceRoot
    $priorParentLedgerWritten = $script:VerifierParentLedgerWritten
    $canaryRoot = Get-VerifierFullPath (Join-Path ([IO.Path]::GetTempPath()) `
        ('TroubleshootJS\verify\parent-writer-tuple-' + [Guid]::NewGuid().ToString('N')))
    $canaryPath = Join-Path $canaryRoot 'ledger.json'
    try {
        New-Item -ItemType Directory -Path $canaryRoot -Force -ErrorAction Stop | Out-Null
        $validLease = $ValidLease | ConvertTo-Json -Depth 16 | ConvertFrom-Json
        Add-Member -InputObject $validLease -MemberType NoteProperty `
            -Name LeaseId -Value 'gate-b-parent-writer-lease' -Force
        Add-Member -InputObject $validLease -MemberType NoteProperty `
            -Name Path -Value (Join-Path (Split-Path -Parent $canaryPath) 'gate-b-parent-writer.lease') -Force
        Add-Member -InputObject $validLease -MemberType NoteProperty `
            -Name ClaimName -Value (Get-VerifierPortMutexName $null 40192) -Force
        # The parent writer canary is intentionally a complete durable lease,
        # not merely a listener-tuple fixture.  Missing fields must fail before
        # any temporary ledger file is created.
        $canaryWorktree = Get-VerifierFullPath (Join-Path $PSScriptRoot '..')
        $durableLeaseFields = [ordered]@{
            Kind = 'cdp'; Port = 40192; RunId = 'gate-b-parent-writer-run'
            RepositoryIdentity = 'gate-b-parent-writer-repository'
            WorktreeRoot = $canaryWorktree; Status = 'released'
            ClaimState = 'released'; ProfilePath = ''; BrowserPath = ''
            BindValidatedUtc = ''; ReleasedUtc = ''; ReleaseState = 'complete'
            ReleaseJournalState = 'complete'; ReleaseBlockReason = ''
            ListenerInspectionUtc = ''
            ClaimOwnerPid = [int]$PID
            ClaimOwnerStartTicks = [long](Get-VerifierProcessStartTicks (Get-Process -Id $PID))
            BoundProcessId = [int]$PID; BoundProcessStartTicks = [long](Get-VerifierProcessStartTicks (Get-Process -Id $PID))
            Registered = $true; ReleaseBlocked = $false; MutexReleased = $true
        }
        foreach ($field in $durableLeaseFields.GetEnumerator()) {
            Add-Member -InputObject $validLease -MemberType NoteProperty `
                -Name $field.Key -Value $field.Value -Force
        }
        $validContext = [pscustomobject]@{
            RunId = 'gate-b-parent-writer-run'
            RepositoryIdentity = 'gate-b-parent-writer-repository'
            WorktreeRoot = Get-VerifierFullPath (Join-Path $PSScriptRoot '..')
            RunRoot = (Split-Path -Parent $canaryPath)
            ManifestPath = (Join-Path (Split-Path -Parent $canaryPath) 'manifest.json')
            EvidenceDirectory = (Split-Path -Parent $canaryPath)
            Artifacts = @(); BrowserSessions = @(); CleanupState = 'pending'
            CleanupErrors = @(); LeaseRecords = @($validLease); Server = $null
        }
        $script:VerifierContext = $validContext
        $script:ParentLedgerPath = $canaryPath
        $script:ParentNamespaceRoot = ''
        $validWriterError = $null
        try { Write-VerifierParentLedger 'started' } catch {
            $validWriterError = $_
        }
        if ($null -ne $validWriterError -or -not (Test-Path -LiteralPath $canaryPath)) {
            throw 'parent ledger writer rejected the valid durable listener tuple or did not write its ledger.'
        }
        $validLedger = Get-Content -LiteralPath $canaryPath -Raw -ErrorAction Stop | ConvertFrom-Json
        if ($validLedger.leases.Count -ne 1 -or
                $validLedger.leases[0].listenerOwnerKind -cne 'user-process' -or
                $validLedger.leases[0].listenerOwnerProof -cne 'diagnostics-process-start-v1' -or
                $validLedger.leases[0].listenerOwnerEvidence -cne 'system-diagnostics-process-starttime') {
            throw 'parent ledger writer did not preserve the valid durable listener tuple.'
        }
        Remove-VerifierOwnedTree (Get-VerifierFullPath (Join-Path ([IO.Path]::GetTempPath()) `
            'TroubleshootJS\verify')) (Get-VerifierFullPath $canaryPath)

        foreach ($missingField in @('ClaimState', 'ReleaseState',
                'ClaimOwnerPid', 'BoundProcessId', 'BrowserPath',
                'ProcessProofRequired')) {
            $incompleteLease = $validLease | ConvertTo-Json -Depth 16 | ConvertFrom-Json
            [void]$incompleteLease.PSObject.Properties.Remove($missingField)
            $script:VerifierContext = $validContext | Select-Object *
            $script:VerifierContext.LeaseRecords = @($incompleteLease)
            $script:ParentLedgerPath = $canaryPath
            $rejected = $false
            try { Write-VerifierParentLedger 'started' } catch {
                $rejected = Test-VerifierInfrastructureError $_
            }
            $temporaryLedgerFiles = @(Get-ChildItem -LiteralPath $canaryRoot `
                -Filter ((Split-Path -Leaf $canaryPath) + '.*.tmp') -File `
                -ErrorAction SilentlyContinue)
            if (-not $rejected -or (Test-Path -LiteralPath $canaryPath) -or
                    $temporaryLedgerFiles.Count -ne 0) {
                throw "parent ledger writer accepted incomplete durable field '$missingField' or wrote before rejection."
            }
        }
        $script:VerifierContext = $validContext

        $malformedArtifactContext = $validContext | Select-Object *
        $malformedArtifactContext.Artifacts = @([pscustomobject]@{ Path = 'not-an-artifact-string' })
        $script:VerifierContext = $malformedArtifactContext
        $script:ParentLedgerPath = $canaryPath
        $rejected = $false
        try { Write-VerifierParentLedger 'started' } catch {
            $rejected = Test-VerifierInfrastructureError $_
        }
        $temporaryLedgerFiles = @(Get-ChildItem -LiteralPath $canaryRoot `
            -Filter ((Split-Path -Leaf $canaryPath) + '.*.tmp') -File `
            -ErrorAction SilentlyContinue)
        if (-not $rejected -or (Test-Path -LiteralPath $canaryPath) -or
                $temporaryLedgerFiles.Count -ne 0) {
            throw 'parent ledger writer accepted a malformed evidence artifact or wrote before rejection.'
        }
        $script:VerifierContext = $validContext

        $malformedLease = $ValidLease | ConvertTo-Json -Depth 16 | ConvertFrom-Json
        [void]$malformedLease.PSObject.Properties.Remove('listenerOwnerKind')
        $script:VerifierContext = [pscustomobject]@{
            LeaseRecords = @($malformedLease); Server = $null
        }
        $script:ParentLedgerPath = $canaryPath
        $script:ParentNamespaceRoot = ''
        $rejected = $false
        try { Write-VerifierParentLedger 'started' } catch {
            $rejected = Test-VerifierInfrastructureError $_
        }
        if (-not $rejected -or (Test-Path -LiteralPath $canaryPath)) {
            throw 'parent ledger writer accepted malformed owner tuple or wrote before rejection.'
        }
        $missingServerLease = [pscustomobject]@{
            Owner = 'run'; CallerOwned = $false
        }
        $script:VerifierContext = [pscustomobject]@{
            LeaseRecords = @($ValidLease); Server = $missingServerLease
        }
        $rejected = $false
        try { Write-VerifierParentLedger 'started' } catch {
            $rejected = Test-VerifierInfrastructureError $_
        }
        $canaryParent = Split-Path -Parent $canaryPath
        $temporaryLedgerFiles = @(Get-ChildItem -LiteralPath $canaryParent `
            -Filter ((Split-Path -Leaf $canaryPath) + '.*.tmp') -File `
            -ErrorAction SilentlyContinue)
        if (-not $rejected -or (Test-Path -LiteralPath $canaryPath) -or
                $temporaryLedgerFiles.Count -ne 0) {
            throw 'parent ledger writer accepted or wrote a run-owned server without Server.Lease.'
        }
    } finally {
        if (Test-Path -LiteralPath $canaryRoot) {
            try {
                Remove-VerifierOwnedTree (Get-VerifierFullPath (Join-Path ([IO.Path]::GetTempPath()) `
                    'TroubleshootJS\verify')) (Get-VerifierFullPath $canaryRoot)
            } catch { }
        }
        $script:VerifierContext = $priorContext
        $script:ParentLedgerPath = $priorLedgerPath
        $script:ParentNamespaceRoot = $priorNamespaceRoot
        $script:VerifierParentLedgerWritten = $priorParentLedgerWritten
    }
}

function Invoke-VerifierCleanupArrayReaderCanary() {
    $validLedger = [pscustomobject]@{ cleanupErrors = @() }
    $validManifest = [pscustomobject]@{ errors = @() }
    $validPair = Assert-VerifierDurableArrayPair $validLedger $validManifest `
        'cleanupErrors' 'errors' 'cleanup error reader' -CompareItems
    if ($validPair.Ledger.GetType() -ne $validPair.Manifest.GetType() -or
            $validPair.Ledger.Count -ne 0) {
        throw 'paired explicit empty cleanup-error arrays were not retained as valid arrays.'
    }
    foreach ($variantDefinition in @(
            [pscustomobject]@{ Name = 'missing ledger cleanupErrors'; Side = 'ledger'; Value = $null; Remove = $true }
            [pscustomobject]@{ Name = 'null ledger cleanupErrors'; Side = 'ledger'; Value = $null; Remove = $false }
            [pscustomobject]@{ Name = 'scalar ledger cleanupErrors'; Side = 'ledger'; Value = 'none'; Remove = $false }
            [pscustomobject]@{ Name = 'missing manifest cleanup errors'; Side = 'manifest'; Value = $null; Remove = $true }
            [pscustomobject]@{ Name = 'null manifest cleanup errors'; Side = 'manifest'; Value = $null; Remove = $false }
            [pscustomobject]@{ Name = 'scalar manifest cleanup errors'; Side = 'manifest'; Value = 'none'; Remove = $false }
            [pscustomobject]@{ Name = 'mismatched cleanup arrays'; Side = 'ledger'; Value = @('retained-error'); Remove = $false }
        )) {
        $ledgerVariant = $validLedger | Select-Object *
        $manifestVariant = $validManifest | Select-Object *
        if ($variantDefinition.Side -eq 'ledger') {
            if ($variantDefinition.Remove) {
                [void]$ledgerVariant.PSObject.Properties.Remove('cleanupErrors')
            } else { $ledgerVariant.cleanupErrors = $variantDefinition.Value }
        } else {
            if ($variantDefinition.Remove) {
                [void]$manifestVariant.PSObject.Properties.Remove('errors')
            } else { $manifestVariant.errors = $variantDefinition.Value }
        }
        $rejected = $false
        try {
            [void](Assert-VerifierDurableArrayPair $ledgerVariant $manifestVariant `
                'cleanupErrors' 'errors' 'cleanup error reader' -CompareItems)
        } catch { $rejected = Test-VerifierInfrastructureError $_ }
        if (-not $rejected) {
            throw "durable cleanup array reader accepted $($variantDefinition.Name)."
        }
    }
    Write-Host 'PASS:paired cleanup-error reader requires explicit actual arrays and rejects missing/null/scalar/mismatched copies'
}

function Invoke-VerifierDurableReaderSchemaCanary() {
    $validLedger = [pscustomobject]@{
        protocol = 'troubleshootjs-integrated-child-ledger-v1'; state = 'completed'
        runId = 'reader-schema-canary-run'; repositoryIdentity = 'reader-schema-canary-repository'
        worktreeRoot = 'C:\reader-schema-canary'; parentNamespaceRoot = 'C:\reader-schema-canary\parent'
        runRoot = 'C:\reader-schema-canary\parent\run'
        manifestPath = 'C:\reader-schema-canary\parent\run\manifest.json'
        evidenceDirectory = 'C:\reader-schema-canary\parent\run\evidence'
        cleanupState = 'complete'; error = ''; updatedUtc = '2026-08-31T00:00:00.0000000Z'
        forcedNegativeProof = $null; evidence = @(); leases = @(); profiles = @(); server = $null
    }
    $validManifest = [pscustomobject]@{
        protocol = 'troubleshootjs-verifier-run-v1'; runId = $validLedger.runId
        repositoryIdentity = $validLedger.repositoryIdentity
        worktreeRoot = $validLedger.worktreeRoot; runRoot = $validLedger.runRoot
        evidenceDirectory = $validLedger.evidenceDirectory
        manifestPath = $validLedger.manifestPath
        runNamespaceRoot = $validLedger.parentNamespaceRoot
        evidenceNamespaceRoot = $validLedger.runRoot
        createdUtc = '2026-08-31T00:00:00.0000000Z'; baseUrl = ''
        previewNonce = 'reader-schema-canary-nonce'
        leases = @(); browserSessions = @(); artifacts = @()
        server = $null
        cleanup = [pscustomobject]@{ state = 'complete'; completedUtc = '2026-08-31T00:00:01.0000000Z'; errors = @() }
    }
    $ledgerJson = $validLedger | ConvertTo-Json -Depth 16 -Compress
    $manifestJson = $validManifest | ConvertTo-Json -Depth 16 -Compress
    Assert-VerifierIntegratedDurableReaderSchema $validLedger $validManifest `
        $ledgerJson $manifestJson
    foreach ($variantDefinition in @(
            [pscustomobject]@{ Name = 'missing baseUrl'; Field = 'baseUrl'; Value = $null; Remove = $true }
            [pscustomobject]@{ Name = 'null baseUrl'; Field = 'baseUrl'; Value = $null; Remove = $false }
            [pscustomobject]@{ Name = 'wrong-type baseUrl'; Field = 'baseUrl'; Value = 123; Remove = $false }
            [pscustomobject]@{ Name = 'missing previewNonce'; Field = 'previewNonce'; Value = $null; Remove = $true }
            [pscustomobject]@{ Name = 'null previewNonce'; Field = 'previewNonce'; Value = $null; Remove = $false }
            [pscustomobject]@{ Name = 'wrong-type previewNonce'; Field = 'previewNonce'; Value = 123; Remove = $false }
            [pscustomobject]@{ Name = 'missing cleanup completedUtc'; Field = 'completedUtc'; Value = $null; Remove = $true }
            [pscustomobject]@{ Name = 'null cleanup completedUtc'; Field = 'completedUtc'; Value = $null; Remove = $false }
            [pscustomobject]@{ Name = 'wrong-type cleanup completedUtc'; Field = 'completedUtc'; Value = 123; Remove = $false }
        )) {
        $manifestVariant = ConvertFrom-VerifierDurableJson $manifestJson
        if ($variantDefinition.Field -eq 'completedUtc') {
            if ($variantDefinition.Remove) {
                [void]$manifestVariant.cleanup.PSObject.Properties.Remove('completedUtc')
            } else { $manifestVariant.cleanup.completedUtc = $variantDefinition.Value }
        } elseif ($variantDefinition.Remove) {
            [void]$manifestVariant.PSObject.Properties.Remove($variantDefinition.Field)
        } else { $manifestVariant.($variantDefinition.Field) = $variantDefinition.Value }
        $variantJson = $manifestVariant | ConvertTo-Json -Depth 16 -Compress
        $rejected = $false
        try {
            Assert-VerifierIntegratedDurableReaderSchema $validLedger $manifestVariant `
                $ledgerJson $variantJson
        } catch { $rejected = Test-VerifierInfrastructureError $_ }
        if (-not $rejected) {
            throw "integrated durable reader accepted $($variantDefinition.Name)."
        }
    }
    Write-Host 'PASS:paired durable reader requires typed manifest baseUrl, previewNonce, and cleanup completedUtc fields'
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
    $forcedNegativeInvocation = ($null -ne $script:Task43ForcedNegativeProof -and
        $script:Task43ForcedNegativeProof.Invocation -eq $true)
    if ($forcedNegativeInvocation) {
        # Once a forced-negative route has any later failure, its earlier
        # marker/diagnostic observation is evidence only.  The route cannot
        # retain a success claim, and an untyped boundary error is uncertainty
        # for this route rather than an application exit.
        Invalidate-Task43ForcedNegativeProof
    }
    # Preserve the ordinary verifier's existing typed application-versus-
    # infrastructure classification.  Only an active forced-negative route
    # gets the stricter monotonic uncertainty rule.
    $isInfrastructure = if ($forcedNegativeInvocation) { $true } else {
        Test-VerifierInfrastructureError $ErrorRecord
    }
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

function Throw-VerifierExit($ExitCode, [string]$Message) {
    if (-not (Test-VerifierStrictIntegralValue $ExitCode 0 2)) {
        Throw-VerifierInfrastructure 'Verifier exit code must be one exact whitelisted value: 0, 1, or 2.'
    }
    $ExitCode = [int]$ExitCode
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
        $rawExitCode = $exception.Data['VerifierExitCode']
        if (-not (Test-VerifierStrictIntegralValue $rawExitCode 0 2)) {
            # An explicit but malformed request is itself infrastructure. Do
            # not parse or pass through values such as -1, 3, '1', or $true.
            return 2
        }
        $explicitExitCode = [int]$rawExitCode
    }
    if ($explicitExitCode -eq 2) { return 2 }
    # A run-level infrastructure result is monotonic.  It dominates a later
    # route/child application exception carrying an explicit exit 1.
    if ($script:VerifierFailureExitCode -eq 2) { return 2 }
    if ($null -ne $explicitExitCode) {
        return $explicitExitCode
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
    Set-Task43ForcedNegativeRouteIdentity
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

function Test-Task43PExecutionProvenanceEqual($Before, $After) {
    if ($null -eq $Before -or $null -eq $After) { return $false }
    foreach ($propertyName in @('Protocol', 'RepositoryRoot', 'SourceRoot',
            'ScriptRoot', 'WebRoot', 'SourceDigest', 'ScriptDigest', 'WebDigest',
            'Digest', 'FileCount')) {
        if ($null -eq $Before.PSObject.Properties[$propertyName] -or
                $null -eq $After.PSObject.Properties[$propertyName]) {
            return $false
        }
        if ($propertyName -eq 'FileCount') {
            if (-not (Test-VerifierStrictIntegralValue $Before.$propertyName 1 ([long]::MaxValue)) -or
                    -not (Test-VerifierStrictIntegralValue $After.$propertyName 1 ([long]::MaxValue)) -or
                    [long]$Before.$propertyName -ne [long]$After.$propertyName) {
                return $false
            }
        } elseif ($Before.$propertyName -cne $After.$propertyName) {
            return $false
        }
    }
    return $true
}

function Get-Task43PRepositoryState($ExecutionRoots = $null) {
    try {
        $repositoryRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
        $executionProvenance = $null
        if ($null -ne $ExecutionRoots) {
            foreach ($propertyName in @('RepositoryRoot', 'WebRoot', 'ScriptRoot',
                    'PreviewScript', 'Provenance')) {
                if ($null -eq $ExecutionRoots.PSObject.Properties[$propertyName]) {
                    Throw-VerifierInfrastructure "Task43P execution roots omitted '$propertyName' while capturing repository state."
                }
            }
            $executionProvenance = Get-VerifierExecutionTreeProvenance $ExecutionRoots.RepositoryRoot
            if (-not (Test-VerifierCanonicalWindowsPathValue $executionProvenance.RepositoryRoot `
                    $ExecutionRoots.RepositoryRoot) -or
                    -not (Test-VerifierCanonicalWindowsPathValue $executionProvenance.WebRoot `
                        $ExecutionRoots.WebRoot) -or
                    -not (Test-VerifierCanonicalWindowsPathValue $executionProvenance.ScriptRoot `
                        $ExecutionRoots.ScriptRoot) -or
                    -not (Test-VerifierCanonicalWindowsPathValue `
                        (Join-Path $executionProvenance.ScriptRoot 'preview.ps1') `
                        $ExecutionRoots.PreviewScript) -or
                    -not (Test-Task43PExecutionProvenanceEqual $executionProvenance `
                        $ExecutionRoots.Provenance)) {
                Throw-VerifierInfrastructure 'Task43P selected execution roots changed or carried inconsistent provenance during repository capture.'
            }
            if (-not [String]::IsNullOrWhiteSpace($ExpectedExecutionProvenanceDigest) -and
                    $executionProvenance.Digest -cne $ExpectedExecutionProvenanceDigest) {
                Throw-VerifierInfrastructure 'Task43P repository capture did not match the expected candidate provenance digest.'
            }
        }
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
            if (-not (Test-Path -LiteralPath $root -PathType Container -ErrorAction Stop)) {
                Throw-VerifierInfrastructure "Task43P could not inspect source root: $root"
            }
            foreach ($file in @(Get-ChildItem -LiteralPath $root -Recurse -File -ErrorAction Stop |
                    Sort-Object FullName)) {
                $relativePath = $file.FullName.Substring($repositoryRoot.Length).TrimStart('\', '/')
                $fileHash = Get-VerifierFileSha256 $file.FullName
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
        $state = [ordered]@{
            # This object is wrapper-owned repository evidence.  Java/GWT never
            # receives or emits these claims.
            headSha = $head.ToLowerInvariant()
            sourceVerifierDigest = $digest
            dirty = -not [String]::IsNullOrWhiteSpace($status)
            sourceVerifierFileCount = $fileRecords.Count
            statusText = $status
            executionProvenance = $executionProvenance
        }
        if ($script:Task43PCandidateSha -cne '' -and
                ($state.headSha -cne $script:Task43PCandidateSha -or
                    ($null -ne $script:Task43PInvocationRepositoryState -and
                        -not (Test-Task43PCandidateRepositoryState $script:Task43PCandidateSha `
                            $script:Task43PInvocationRepositoryState $state)))) {
            Throw-VerifierInfrastructure 'Task43P repository changed from the frozen invocation candidate.'
        }
        return $state
    } catch {
        if (Test-VerifierInfrastructureError $_) { throw }
        Throw-VerifierInfrastructure ('Task43P repository state read failed closed: ' +
            (Get-VerifierErrorMessage $_))
    }
}

function Assert-Task43PJavaEvidenceObject($Value, [string]$Path,
        [string[]]$AllowedProperties) {
    if ($null -eq $Value -or $Value -is [System.Array] -or
            $Value -isnot [pscustomobject]) {
        Throw-VerifierInfrastructure ("Task43P Java evidence expected an object at $Path.")
    }
    foreach ($property in @($Value.PSObject.Properties)) {
        $propertyName = [string]$property.Name
        # This exact allowlist is the Java evidence schema. It is deliberately
        # fail-closed: repository authority aliases in any case or formatting
        # cannot be persisted because unknown names are rejected at every
        # nested object, rather than relying on an incomplete deny-list.
        if ($AllowedProperties -cnotcontains $propertyName) {
            Throw-VerifierInfrastructure ("Task43P Java evidence contained an unknown field " +
                "'$propertyName' at $Path; repository authority is wrapper-owned.")
        }
    }
}

function Assert-Task43PJavaEvidenceRequired($Value, [string]$Path,
        [string[]]$RequiredProperties) {
    foreach ($propertyName in $RequiredProperties) {
        if ($null -eq $Value.PSObject.Properties[$propertyName]) {
            Throw-VerifierInfrastructure ("Task43P Java evidence omitted required field " +
                "'$propertyName' at $Path.")
        }
    }
}

function Assert-Task43PJavaEvidenceString($Value, [string]$Path,
        [switch]$AllowNull) {
    if ($AllowNull -and $null -eq $Value) { return }
    if ($Value -isnot [string] -or [String]::IsNullOrWhiteSpace([string]$Value)) {
        Throw-VerifierInfrastructure ("Task43P Java evidence expected a non-empty string at $Path.")
    }
}

function Assert-Task43PJavaEvidenceBoolean($Value, [string]$Path) {
    if ($Value -isnot [bool]) {
        Throw-VerifierInfrastructure ("Task43P Java evidence expected a Boolean at $Path.")
    }
}

function Assert-Task43PJavaEvidenceNumber($Value, [string]$Path) {
    if ($null -eq $Value -or $Value -is [string] -or $Value -is [bool] -or
            $Value -isnot [ValueType]) {
        Throw-VerifierInfrastructure ("Task43P Java evidence expected a JSON number at $Path.")
    }
}

function Assert-Task43PJavaEvidenceStringArray($Value, [string]$Path) {
    if ($Value -isnot [System.Array]) {
        Throw-VerifierInfrastructure ("Task43P Java evidence expected a string array at $Path.")
    }
    $index = 0
    foreach ($item in @($Value)) {
        Assert-Task43PJavaEvidenceString $item ($Path + '[' + $index + ']')
        $index++
    }
}

function Assert-Task43PJavaEvidencePoint($Value, [string]$Path) {
    if ($Value -isnot [System.Array] -or @($Value).Count -ne 2) {
        Throw-VerifierInfrastructure ("Task43P Java evidence expected a two-number point at $Path.")
    }
    for ($index = 0; $index -lt 2; $index++) {
        Assert-Task43PJavaEvidenceNumber @($Value)[$index] ($Path + '[' + $index + ']')
    }
}

function Assert-Task43PJavaEvidenceProvenance($Value, [string]$Path = 'root') {
    $rootProperties = @(
        'protocol', 'status', 'developerOnly', 'forcedNegativeRequested', 'family',
        'topology', 'seed', 'fault', 'faultType', 'faultOwner', 'faultApplied',
        'earlyFinishBlocked', 'state', 'ready', 'completed', 'repairStatus',
        'retestPresent', 'generatedVerificationPending',
        'generatedVerificationAnalyzed', 'developerVerifierRunning', 'lifecycle',
        'verifierDesignStateBefore', 'verifierDesignStateAfter', 'mutationCleanup',
        'lanes', 'epochContract', 'forcedNegativeProof', 'triad'
    )
    Assert-Task43PJavaEvidenceObject $Value $Path $rootProperties
    Assert-Task43PJavaEvidenceRequired $Value $Path $rootProperties
    foreach ($name in @('protocol', 'status', 'family', 'topology', 'fault',
            'faultType', 'state', 'repairStatus', 'verifierDesignStateBefore',
            'verifierDesignStateAfter')) {
        Assert-Task43PJavaEvidenceString $Value.$name ($Path + '.' + $name)
    }
    Assert-Task43PJavaEvidenceString $Value.faultOwner ($Path + '.faultOwner') -AllowNull
    Assert-Task43PJavaEvidenceNumber $Value.seed ($Path + '.seed')
    foreach ($name in @('developerOnly', 'forcedNegativeRequested', 'faultApplied',
            'earlyFinishBlocked', 'ready', 'completed', 'retestPresent',
            'generatedVerificationPending', 'generatedVerificationAnalyzed',
            'developerVerifierRunning')) {
        Assert-Task43PJavaEvidenceBoolean $Value.$name ($Path + '.' + $name)
    }

    $forcedProofPath = $Path + '.forcedNegativeProof'
    $forcedProofProperties = @('requested', 'nonce', 'routeId', 'requestId',
        'executionDigest')
    Assert-Task43PJavaEvidenceObject $Value.forcedNegativeProof $forcedProofPath `
        $forcedProofProperties
    Assert-Task43PJavaEvidenceRequired $Value.forcedNegativeProof $forcedProofPath `
        $forcedProofProperties
    Assert-Task43PJavaEvidenceBoolean $Value.forcedNegativeProof.requested `
        ($forcedProofPath + '.requested')
    foreach ($name in @('nonce', 'routeId', 'requestId', 'executionDigest')) {
        if ($Value.forcedNegativeProof.$name -isnot [string]) {
            Throw-VerifierInfrastructure ("Task43P Java evidence expected an exact string at " +
                $forcedProofPath + '.' + $name + '.')
        }
    }
    if ([bool]$Value.forcedNegativeProof.requested) {
        foreach ($name in @('nonce', 'routeId', 'requestId', 'executionDigest')) {
            if ([String]::IsNullOrWhiteSpace([string]$Value.forcedNegativeProof.$name)) {
                Throw-VerifierInfrastructure ("Task43P Java forced-negative proof omitted " +
                    "required '$name'.")
            }
        }
        if ([string]$Value.forcedNegativeProof.executionDigest -notmatch '^[0-9a-f]{64}$') {
            Throw-VerifierInfrastructure 'Task43P Java forced-negative proof carried a malformed execution digest.'
        }
    } else {
        foreach ($name in @('nonce', 'routeId', 'requestId', 'executionDigest')) {
            if (-not [String]::IsNullOrWhiteSpace([string]$Value.forcedNegativeProof.$name)) {
                Throw-VerifierInfrastructure ("Task43P non-forced evidence carried an unexpected " +
                    "forced-negative '$name' identity.")
            }
        }
    }

    $lifecyclePath = $Path + '.lifecycle'
    $lifecycleProperties = @('healthyInstalled', 'healthyAnalyzed', 'faultApplied',
        'faultAnalyzed', 'faultValidated', 'readyAfterValidation')
    Assert-Task43PJavaEvidenceObject $Value.lifecycle $lifecyclePath $lifecycleProperties
    Assert-Task43PJavaEvidenceRequired $Value.lifecycle $lifecyclePath $lifecycleProperties
    foreach ($name in $lifecycleProperties) {
        Assert-Task43PJavaEvidenceBoolean $Value.lifecycle.$name ($lifecyclePath + '.' + $name)
    }

    $cleanupPath = $Path + '.mutationCleanup'
    $cleanupProperties = @('readOnly', 'activeMeasurementOverlay',
        'pendingBoardPowerState', 'temporarySolverRestored', 'fullyRestoredAtEntry')
    Assert-Task43PJavaEvidenceObject $Value.mutationCleanup $cleanupPath $cleanupProperties
    Assert-Task43PJavaEvidenceRequired $Value.mutationCleanup $cleanupPath $cleanupProperties
    foreach ($name in @('readOnly', 'activeMeasurementOverlay', 'temporarySolverRestored',
            'fullyRestoredAtEntry')) {
        Assert-Task43PJavaEvidenceBoolean $Value.mutationCleanup.$name ($cleanupPath + '.' + $name)
    }
    Assert-Task43PJavaEvidenceString $Value.mutationCleanup.pendingBoardPowerState `
        ($cleanupPath + '.pendingBoardPowerState') -AllowNull

    $lanesPath = $Path + '.lanes'
    $laneIds = @('A', 'B', 'C', 'D', 'E', 'F', 'G', 'H', 'I')
    Assert-Task43PJavaEvidenceObject $Value.lanes $lanesPath $laneIds
    Assert-Task43PJavaEvidenceRequired $Value.lanes $lanesPath $laneIds
    foreach ($laneId in $laneIds) {
        $lanePath = $lanesPath + '.' + $laneId
        Assert-Task43PJavaEvidenceObject $Value.lanes.$laneId $lanePath @('status', 'observation')
        Assert-Task43PJavaEvidenceRequired $Value.lanes.$laneId $lanePath @('status', 'observation')
        Assert-Task43PJavaEvidenceString $Value.lanes.$laneId.status ($lanePath + '.status')
        Assert-Task43PJavaEvidenceString $Value.lanes.$laneId.observation ($lanePath + '.observation')
    }

    $epochPath = $Path + '.epochContract'
    $epochProperties = @('requestEpoch', 'boardEpoch', 'sessionEpoch')
    Assert-Task43PJavaEvidenceObject $Value.epochContract $epochPath $epochProperties
    Assert-Task43PJavaEvidenceRequired $Value.epochContract $epochPath $epochProperties
    foreach ($name in $epochProperties) {
        Assert-Task43PJavaEvidenceBoolean $Value.epochContract.$name ($epochPath + '.' + $name)
    }

    $triadPath = $Path + '.triad'
    $triadProperties = @(
        'status', 'validator', 'manifestAuthoring', 'observationSources', 'family',
        'topology', 'seed', 'fault', 'faultType', 'faultOwner', 'terminalCount',
        'rawTraceChecks', 'renderedSurfaceChecks', 'solverChecks',
        'negativeFixtureScope', 'liveReadings', 'negativeFixtures', 'terminals'
    )
    Assert-Task43PJavaEvidenceObject $Value.triad $triadPath $triadProperties
    Assert-Task43PJavaEvidenceRequired $Value.triad $triadPath $triadProperties
    foreach ($name in @('status', 'validator', 'manifestAuthoring', 'family',
            'topology', 'fault', 'faultType', 'negativeFixtureScope')) {
        Assert-Task43PJavaEvidenceString $Value.triad.$name ($triadPath + '.' + $name)
    }
    Assert-Task43PJavaEvidenceString $Value.triad.faultOwner ($triadPath + '.faultOwner') -AllowNull
    Assert-Task43PJavaEvidenceNumber $Value.triad.seed ($triadPath + '.seed')
    foreach ($name in @('terminalCount', 'rawTraceChecks', 'renderedSurfaceChecks',
            'solverChecks')) {
        Assert-Task43PJavaEvidenceNumber $Value.triad.$name ($triadPath + '.' + $name)
    }
    Assert-Task43PJavaEvidenceStringArray $Value.triad.observationSources `
        ($triadPath + '.observationSources')
    Assert-Task43PJavaEvidenceStringArray $Value.triad.liveReadings `
        ($triadPath + '.liveReadings')

    $fixtureProperties = @('id', 'sourceMutation', 'caught')
    $fixtureIndex = 0
    if ($Value.triad.negativeFixtures -isnot [System.Array]) {
        Throw-VerifierInfrastructure "Task43P Java evidence expected a negative-fixture array."
    }
    foreach ($fixture in @($Value.triad.negativeFixtures)) {
        $fixturePath = $triadPath + '.negativeFixtures[' + $fixtureIndex + ']'
        Assert-Task43PJavaEvidenceObject $fixture $fixturePath $fixtureProperties
        Assert-Task43PJavaEvidenceRequired $fixture $fixturePath $fixtureProperties
        Assert-Task43PJavaEvidenceString $fixture.id ($fixturePath + '.id')
        Assert-Task43PJavaEvidenceString $fixture.sourceMutation ($fixturePath + '.sourceMutation')
        Assert-Task43PJavaEvidenceBoolean $fixture.caught ($fixturePath + '.caught')
        $fixtureIndex++
    }

    $terminalProperties = @(
        'padId', 'rawNetId', 'rawEndpoint', 'renderedPadId', 'renderedTerminalId',
        'renderedPadPoint', 'packageId', 'variant', 'transform', 'solverOracleNetId',
        'solverClass', 'solverPost', 'solverNode'
    )
    $terminalIndex = 0
    if ($Value.triad.terminals -isnot [System.Array]) {
        Throw-VerifierInfrastructure "Task43P Java evidence expected a terminal array."
    }
    foreach ($terminal in @($Value.triad.terminals)) {
        $terminalPath = $triadPath + '.terminals[' + $terminalIndex + ']'
        Assert-Task43PJavaEvidenceObject $terminal $terminalPath $terminalProperties
        Assert-Task43PJavaEvidenceRequired $terminal $terminalPath $terminalProperties
        foreach ($name in @('padId', 'rawNetId', 'renderedPadId', 'renderedTerminalId',
                'packageId', 'variant', 'transform', 'solverOracleNetId', 'solverClass')) {
            Assert-Task43PJavaEvidenceString $terminal.$name ($terminalPath + '.' + $name)
        }
        Assert-Task43PJavaEvidencePoint $terminal.rawEndpoint ($terminalPath + '.rawEndpoint')
        Assert-Task43PJavaEvidencePoint $terminal.renderedPadPoint `
            ($terminalPath + '.renderedPadPoint')
        Assert-Task43PJavaEvidenceNumber $terminal.solverPost ($terminalPath + '.solverPost')
        Assert-Task43PJavaEvidenceNumber $terminal.solverNode ($terminalPath + '.solverNode')
        $terminalIndex++
    }
}

function Assert-Task43PJavaEvidencePayload($Value, [string]$RouteName,
        [string]$Expected, [string]$Observed) {
    # Re-run the exact Java schema at the persistence boundary as well as at
    # the caller. This keeps future call sites fail-closed before any file is
    # created, even if they forget the separate provenance call.
    Assert-Task43PJavaEvidenceProvenance $Value
    if ($null -eq $Value -or $Value -is [System.Array] -or
            $Value -isnot [pscustomobject]) {
        Throw-VerifierInfrastructure ("Task43P route '$RouteName' published a null, array, " +
            'or non-object evidence payload.')
    }
    foreach ($required in @('protocol', 'status', 'developerOnly',
            'forcedNegativeRequested', 'family', 'topology', 'seed', 'fault',
            'faultType', 'faultApplied', 'earlyFinishBlocked', 'state', 'ready',
            'completed', 'repairStatus', 'retestPresent', 'generatedVerificationPending',
            'generatedVerificationAnalyzed', 'developerVerifierRunning', 'lifecycle',
            'verifierDesignStateBefore', 'verifierDesignStateAfter', 'mutationCleanup',
            'lanes', 'epochContract', 'forcedNegativeProof', 'triad')) {
        if (-not $Value.PSObject.Properties[$required]) {
            Throw-VerifierInfrastructure ("Task43P route '$RouteName' omitted required evidence " +
                "property '$required'.")
        }
    }
    if ($Value.protocol -isnot [string] -or
            [string]::IsNullOrWhiteSpace([string]$Value.protocol) -or
            [string]$Value.protocol -cne 'TSJ-TASK43P-2') {
        Throw-VerifierInfrastructure ("Task43P route '$RouteName' published an unexpected " +
            "protocol: $($Value.protocol)")
    }
    if ($Value.status -isnot [string] -or
            [string]::IsNullOrWhiteSpace([string]$Value.status) -or
            [string]$Value.status -cne 'UNPROVEN') {
        Throw-VerifierInfrastructure ("Task43P route '$RouteName' published a malformed or " +
            "contradictory status: $($Value.status)")
    }
    if ($Value.developerOnly -isnot [bool] -or [bool]$Value.developerOnly -ne $true) {
        Throw-VerifierInfrastructure ("Task43P route '$RouteName' did not prove developer-only " +
            'evidence ownership.')
    }
    $forcedRoute = $RouteName -like '*forced-negative*'
    if ($Value.forcedNegativeRequested -isnot [bool] -or
            [bool]$Value.forcedNegativeRequested -ne $forcedRoute) {
        Throw-VerifierInfrastructure ("Task43P route '$RouteName' published a contradictory " +
            'forced-negative marker.')
    }
    if (-not $forcedRoute -and $Expected -eq 'UNPROVEN:task43p' -and
            $Expected -ne $Observed) {
        Throw-VerifierInfrastructure ("Task43P route '$RouteName' published observed result " +
            "'$Observed' instead of '$Expected'.")
    }
    foreach ($identityProperty in @('family', 'topology', 'fault', 'faultType',
            'state', 'repairStatus', 'verifierDesignStateBefore',
            'verifierDesignStateAfter')) {
        if ($Value.$identityProperty -isnot [string] -or
                [string]::IsNullOrWhiteSpace([string]$Value.$identityProperty)) {
            Throw-VerifierInfrastructure ("Task43P route '$RouteName' published an empty or " +
                "non-string '$identityProperty' field.")
        }
    }
    foreach ($booleanProperty in @('faultApplied', 'earlyFinishBlocked', 'ready', 'completed',
            'retestPresent', 'generatedVerificationPending',
            'generatedVerificationAnalyzed', 'developerVerifierRunning')) {
        if ($Value.$booleanProperty -isnot [bool]) {
            Throw-VerifierInfrastructure ("Task43P route '$RouteName' published a non-Boolean " +
                "'$booleanProperty' field.")
        }
    }
    foreach ($objectProperty in @('lifecycle', 'mutationCleanup', 'lanes',
            'epochContract', 'forcedNegativeProof')) {
        if ($Value.$objectProperty -is [System.Array] -or
                $Value.$objectProperty -isnot [pscustomobject]) {
            Throw-VerifierInfrastructure ("Task43P route '$RouteName' published a malformed " +
                "'$objectProperty' object.")
        }
    }
    if ([string]::IsNullOrWhiteSpace([string]$Value.family) -or
            [string]::IsNullOrWhiteSpace([string]$Value.topology) -or
            [string]::IsNullOrWhiteSpace([string]$Value.fault)) {
        Throw-VerifierInfrastructure ("Task43P route '$RouteName' published empty board identity " +
            'fields.')
    }
    if ($Value.triad -is [System.Array] -or $Value.triad -isnot [pscustomobject]) {
        Throw-VerifierInfrastructure ("Task43P route '$RouteName' published a malformed triad " +
            'object.')
    }
    foreach ($requiredTriad in @('status', 'validator', 'manifestAuthoring',
            'terminalCount', 'rawTraceChecks', 'renderedSurfaceChecks', 'solverChecks',
            'liveReadings', 'negativeFixtures', 'terminals')) {
        if (-not $Value.triad.PSObject.Properties[$requiredTriad]) {
            Throw-VerifierInfrastructure ("Task43P route '$RouteName' triad omitted required " +
                "property '$requiredTriad'.")
        }
    }
    if ($Value.triad.status -isnot [string] -or
            $Value.triad.validator -isnot [string] -or
            $Value.triad.manifestAuthoring -isnot [string] -or
            [string]$Value.triad.status -cne 'PASS' -or
            [string]$Value.triad.validator -cne 'canonical-source-observations-v2' -or
            [string]$Value.triad.manifestAuthoring -cne 'independent') {
        Throw-VerifierInfrastructure ("Task43P route '$RouteName' published a malformed or " +
            'contradictory triad status.')
    }
    $expectedObservationSources = @('raw-logical-board', 'raw-pcb-layout-copper',
        'renderer', 'physical-package', 'solver-post')
    $observedObservationSources = @($Value.triad.observationSources)
    if ($observedObservationSources.Count -ne $expectedObservationSources.Count) {
        Throw-VerifierInfrastructure ("Task43P route '$RouteName' published an incomplete " +
            'observation-source schema.')
    }
    $observationSourceSet = [Collections.Generic.HashSet[string]]::new(
        [StringComparer]::Ordinal)
    foreach ($source in $observedObservationSources) {
        if (-not $observationSourceSet.Add([string]$source)) {
            Throw-VerifierInfrastructure ("Task43P route '$RouteName' published duplicate " +
                "observation source '$source'.")
        }
    }
    foreach ($expectedSource in $expectedObservationSources) {
        if (-not $observationSourceSet.Contains($expectedSource)) {
            Throw-VerifierInfrastructure ("Task43P route '$RouteName' omitted observation " +
                "source '$expectedSource'.")
        }
    }
    $terminalCount = 0
    if (-not [int]::TryParse([string]$Value.triad.terminalCount,
            [Globalization.NumberStyles]::Integer,
            [Globalization.CultureInfo]::InvariantCulture, [ref]$terminalCount)) {
        Throw-VerifierInfrastructure ("Task43P route '$RouteName' published an invalid terminal " +
            'count.')
    }
    $terminals = @($Value.triad.terminals)
    if ($terminalCount -le 0 -or $terminals.Count -ne $terminalCount) {
        Throw-VerifierInfrastructure ("Task43P route '$RouteName' published incomplete terminal " +
            "evidence: count=$terminalCount entries=$($terminals.Count).")
    }
    $rawTraceChecks = 0
    $renderedSurfaceChecks = 0
    $solverChecks = 0
    if (-not [int]::TryParse([string]$Value.triad.rawTraceChecks,
            [Globalization.NumberStyles]::Integer,
            [Globalization.CultureInfo]::InvariantCulture, [ref]$rawTraceChecks) -or
            -not [int]::TryParse([string]$Value.triad.renderedSurfaceChecks,
                [Globalization.NumberStyles]::Integer,
                [Globalization.CultureInfo]::InvariantCulture, [ref]$renderedSurfaceChecks) -or
            -not [int]::TryParse([string]$Value.triad.solverChecks,
                [Globalization.NumberStyles]::Integer,
                [Globalization.CultureInfo]::InvariantCulture, [ref]$solverChecks) -or
            $rawTraceChecks -le 0 -or
            $renderedSurfaceChecks -ne $terminalCount -or
            $solverChecks -ne $terminalCount -or
            @($Value.triad.liveReadings).Count -ne $terminalCount) {
        Throw-VerifierInfrastructure ("Task43P route '$RouteName' published incomplete positive " +
            'triad checks.')
    }
    foreach ($reading in @($Value.triad.liveReadings)) {
        if ($reading -isnot [string] -or [string]::IsNullOrWhiteSpace([string]$reading)) {
            Throw-VerifierInfrastructure ("Task43P route '$RouteName' published malformed live " +
                'reading evidence.')
        }
    }
    foreach ($terminal in $terminals) {
        if ($null -eq $terminal -or $terminal -is [System.Array] -or
                $terminal -isnot [pscustomobject] -or
                $terminal.padId -isnot [string] -or
                $terminal.renderedPadId -isnot [string] -or
                $terminal.solverClass -isnot [string] -or
                [string]::IsNullOrWhiteSpace([string]$terminal.padId) -or
                [string]::IsNullOrWhiteSpace([string]$terminal.renderedPadId) -or
                [string]::IsNullOrWhiteSpace([string]$terminal.solverClass)) {
            Throw-VerifierInfrastructure ("Task43P route '$RouteName' published incomplete terminal " +
                'evidence.')
        }
    }
    $fixtures = @($Value.triad.negativeFixtures)
    $expectedFixtureIds = @(
        'renderer-only-pad-offset',
        'renderer-only-lead-offset',
        'raw-copper-endpoint-gap',
        'raw-net-mismatch',
        'solver-binding-post-mismatch',
        'raw-empty-unmanifested-net',
        'mirror-transform-mismatch',
        'internally-self-consistent-wrong-mapping',
        'omitted-terminal'
    )
    if ($fixtures.Count -ne $expectedFixtureIds.Count) {
        Throw-VerifierInfrastructure ("Task43P route '$RouteName' published too few canonical " +
            "negative fixtures: $($fixtures.Count).")
    }
    $observedFixtureIds = [Collections.Generic.HashSet[string]]::new(
        [StringComparer]::Ordinal)
    foreach ($fixture in $fixtures) {
        if ($null -eq $fixture -or $fixture -is [System.Array] -or
                $fixture -isnot [pscustomobject] -or
                $fixture.id -isnot [string] -or
                $fixture.sourceMutation -isnot [string] -or
                [string]::IsNullOrWhiteSpace([string]$fixture.id) -or
                [string]::IsNullOrWhiteSpace([string]$fixture.sourceMutation) -or
                $fixture.caught -isnot [bool] -or [bool]$fixture.caught -ne $true) {
            Throw-VerifierInfrastructure ("Task43P route '$RouteName' published an incomplete or " +
                'uncaught negative fixture.')
        }
        if (-not $observedFixtureIds.Add([string]$fixture.id)) {
            Throw-VerifierInfrastructure ("Task43P route '$RouteName' published duplicate " +
                "negative fixture '$($fixture.id)'.")
        }
    }
    foreach ($expectedFixtureId in $expectedFixtureIds) {
        if (-not $observedFixtureIds.Contains($expectedFixtureId)) {
            Throw-VerifierInfrastructure ("Task43P route '$RouteName' omitted required negative " +
                "fixture '$expectedFixtureId'.")
        }
    }
}

function Capture-Task43PEvidence($socket, [ref]$nextId, [DateTime]$deadline,
        [ref]$failures, [string]$routeName, [string]$expected, [string]$observed,
        $repositoryBefore) {
    $script:VerifierEvidenceStage = 'java-payload'
    $payload = [string](evaluateCdp $socket $nextId `
        "document.documentElement.getAttribute('data-tsj-task43p-evidence') || ''" `
        $failures $deadline)
    if ([String]::IsNullOrWhiteSpace($payload)) {
        Throw-VerifierInfrastructure "Task43P route '$routeName' did not publish structured evidence."
    }
    try {
        $parsed = $payload | ConvertFrom-Json -ErrorAction Stop
    } catch {
        Throw-VerifierInfrastructure ("Task43P route '$routeName' published invalid JSON evidence: " +
            (Get-VerifierErrorMessage $_))
    }
    Assert-Task43PJavaEvidenceProvenance $parsed
    Assert-Task43PJavaEvidencePayload $parsed $routeName $expected $observed
    $runtimeParsed = $null
    $runtimeOutcome = $null
    if ($Task43PRuntime) {
        $script:VerifierEvidenceStage = 'runtime-payload'
        if ($expected -cne 'OBSERVED:task43p-runtime' -or $observed -cne $expected) {
            Throw-VerifierInfrastructure 'Task43P runtime capture did not observe its exact route marker.'
        }
        $runtimePayload = evaluateCdp $socket $nextId `
            "document.documentElement.getAttribute('data-tsj-task43p-runtime-evidence') || ''" `
            $failures $deadline
        if ($runtimePayload -isnot [string] -or [String]::IsNullOrWhiteSpace($runtimePayload)) {
            Throw-VerifierInfrastructure 'Task43P runtime route did not publish structured evidence.'
        }
        try {
            try { $runtimeParsed = $runtimePayload | ConvertFrom-Json -ErrorAction Stop } catch {
                Throw-VerifierInfrastructure ('Task43P runtime route published invalid JSON: ' +
                    (Get-VerifierErrorMessage $_))
            }
            $runtimeOutcome = Assert-Task43PRuntimeEvidence $runtimeParsed `
                $script:VerifierContext.RunId $script:VerifierCurrentRouteId
        } catch {
            $runtimeRejection = $_
            # Preserve the raw observation for diagnosis without assigning an
            # outcome or qualifying it as accepted runtime evidence.
            try {
                $rejectedPath = getVerifierEvidencePath ('task43p-runtime-unvalidated-' +
                    $script:VerifierCurrentRouteId + '.json')
                Write-VerifierEvidenceText $rejectedPath $runtimePayload `
                    'unvalidated Task43P runtime observation'
                Register-VerifierEvidenceArtifact $script:VerifierContext $rejectedPath
                Write-Host ('TASK43P UNVALIDATED runtime observation path=' + $rejectedPath)
            } catch {
                Write-Warning ('Could not retain unvalidated runtime observation: ' +
                    (Get-VerifierErrorMessage $_))
            }
            throw $runtimeRejection
        }
    }
    if ($routeName -notlike '*forced-negative*' -and $expected -eq 'UNPROVEN:task43p' -and
            $expected -like 'UNPROVEN:*' -and $expected -ne $observed) {
        Throw-VerifierInfrastructure ("Task43P route '$routeName' published evidence for " +
            "observed result '$observed' rather than the expected route result '$expected'.")
    }
    if ($expected -eq 'UNPROVEN:task43p' -and
            $expected -like 'UNPROVEN:*' -and $routeName -like '*forced-negative*') {
        if (-not $parsed.PSObject.Properties['forcedNegativeRequested'] -or
                [bool]$parsed.forcedNegativeRequested -ne $true) {
            Throw-VerifierInfrastructure ("Task43P forced-negative route '$routeName' did not " +
                'publish its run-request marker.')
        }
    }
    $script:VerifierEvidenceStage = 'repository-provenance'
    $repositoryAfter = Get-Task43PRepositoryState $script:Task43PExecutionRoots
    if ($null -eq $repositoryBefore -or
            $repositoryBefore.headSha -cne $script:Task43PCandidateSha) {
        Throw-VerifierInfrastructure ("Task43P route '$routeName' did not start from the " +
            "frozen invocation candidate $script:Task43PCandidateSha.")
    }
    if ($repositoryBefore.headSha -ne $repositoryAfter.headSha -or
            $repositoryBefore.sourceVerifierDigest -ne $repositoryAfter.sourceVerifierDigest -or
            $repositoryBefore.sourceVerifierFileCount -ne $repositoryAfter.sourceVerifierFileCount -or
            $repositoryBefore.dirty -ne $repositoryAfter.dirty -or
            $repositoryBefore.statusText -ne $repositoryAfter.statusText) {
        Throw-VerifierInfrastructure ("Task43P route '$routeName' changed repository state while " +
            'running developer evidence: before=' + $repositoryBefore.sourceVerifierDigest +
            ' after=' + $repositoryAfter.sourceVerifierDigest)
    }
    $forcedRoute = $routeName -like '*forced-negative*'
    $forcedProof = $parsed.forcedNegativeProof
    if ($forcedProof.requested -ne [bool]$forcedRoute) {
        Throw-VerifierInfrastructure ("Task43P route '$RouteName' published a forced-negative " +
            'proof for a different route type.')
    }
    foreach ($name in @('nonce', 'routeId', 'requestId', 'executionDigest')) {
        if ($forcedProof.$name -isnot [string]) {
            Throw-VerifierInfrastructure ("Task43P route '$RouteName' forced-negative proof " +
                "field '$name' was not an exact string.")
        }
    }
    if ($forcedRoute) {
        $proof = $script:Task43ForcedNegativeProof
        if ($null -eq $proof -or $proof.Invocation -ne $true -or
                $forcedProof.nonce -cne [string]$proof.Nonce -or
                $forcedProof.routeId -cne [string]$proof.RouteId -or
                $forcedProof.requestId -cne [string]$proof.RequestId -or
                $forcedProof.executionDigest -cne [string]$proof.ExecutionDigest -or
                $forcedProof.routeId -cne [string]$script:VerifierCurrentRouteId -or
                $null -eq $script:Task43PExecutionProvenance -or
                $forcedProof.executionDigest -cne [string]$script:Task43PExecutionProvenance.Digest) {
            Throw-VerifierInfrastructure ("Task43P route '$RouteName' Java forced-negative " +
                'proof was not bound to the current verifier request, route, and execution tree.')
        }
    } else {
        foreach ($name in @('nonce', 'routeId', 'requestId', 'executionDigest')) {
            if (-not [String]::IsNullOrWhiteSpace([string]$forcedProof.$name)) {
                Throw-VerifierInfrastructure ("Task43P route '$RouteName' non-forced proof carried " +
                    "unexpected '$name' identity.")
            }
        }
    }
    if (-not (Test-Task43PExecutionProvenanceEqual `
            $repositoryBefore.executionProvenance $repositoryAfter.executionProvenance)) {
        Throw-VerifierInfrastructure ("Task43P route '$routeName' changed or failed to prove " +
            'the selected execution tree provenance.')
    }
    try {
        $script:VerifierEvidenceStage = 'artifact-persistence'
        $safeRouteName = $routeName -replace '[^A-Za-z0-9._-]', '-'
        $path = getVerifierEvidencePath ('task43p-' + $safeRouteName + '.json')
        $record = [ordered]@{
            protocol = 'troubleshootjs-task43p-evidence-capture-v2'
            route = $routeName
            expected = $expected
            observed = $observed
            capturedUtc = [DateTime]::UtcNow.ToString('o', [Globalization.CultureInfo]::InvariantCulture)
            browserStartupSettleMilliseconds = $Task43PStartupSettleMilliseconds
            runId = $script:VerifierContext.RunId
            routeId = $script:VerifierCurrentRouteId
            baselineSha = $script:Task43PHistoricalBaselineSha
            candidateSha = $script:Task43PCandidateSha
            repositoryBefore = $repositoryBefore
            repositoryAfter = $repositoryAfter
            executionProvenance = $repositoryAfter.executionProvenance
            evidencePath = $path
            evidence = $parsed
        }
        if ($Task43PRuntime) {
            $record.runtimeEvidence = $runtimeParsed
            $record.runtimeOutcome = $runtimeOutcome
        }
        Write-VerifierEvidenceText $path ($record | ConvertTo-Json -Depth 30) `
            'Task43P route evidence'
        Register-VerifierEvidenceArtifact $script:VerifierContext $path
    } catch {
        Throw-VerifierInfrastructure ("Task43P route '$routeName' evidence persistence failed: " +
            (Get-VerifierErrorMessage $_))
    }
    Write-Host ("TASK43P EVIDENCE $routeName path=$path head=$($repositoryAfter.headSha) " +
        "sourceVerifierDigest=$($repositoryAfter.sourceVerifierDigest)")
    if ($Task43PRuntime) { $script:Task43PRuntimeOutcome = $runtimeOutcome }
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
    $script:VerifierLastCdpOperation = $method
    $script:VerifierLastCdpStage = 'send'
    [void](sendCdp $socket $id $method $parameters)
    $script:VerifierLastCdpStage = 'receive'
    $response = receiveCdp $socket $id $failures (resolveCdpRouteDeadline $deadline)
    $script:VerifierLastCdpStage = 'validate-response'
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
    $script:VerifierLastCdpStage = 'complete'
    return $response
}

function Wait-Task43PBrowserStartup($socket, [ref]$nextId, [ref]$failures,
        [DateTime]$deadline, $Milliseconds) {
    if (-not (Test-VerifierStrictIntegralValue $Milliseconds 0L 45000L)) {
        Throw-VerifierInfrastructure 'Task43P internal startup settle was not a bounded exact integer.'
    }
    if ($Milliseconds -eq 0) { return }
    $deadline = resolveCdpRouteDeadline $deadline
    if ($deadline -eq [DateTime]::MinValue -or
            ($deadline - [DateTime]::UtcNow).TotalMilliseconds -le $Milliseconds) {
        Throw-VerifierInfrastructure 'Task43P startup settle does not fit the existing route deadline.'
    }
    $elapsed = [Diagnostics.Stopwatch]::StartNew()
    $minimumTicks = [long][Math]::Ceiling(([decimal]$Milliseconds *
        [Diagnostics.Stopwatch]::Frequency) / [decimal]1000)
    $response = invokeCdp $socket $nextId 'Runtime.evaluate' @{
        expression = ('new Promise(resolve=>setTimeout(()=>resolve({url:location.href,' +
            'state:document.readyState}),' + [string]$Milliseconds + '))')
        awaitPromise = $true; returnByValue = $true
    } $failures $deadline
    if ([DateTime]::UtcNow -ge $deadline -or
            $elapsed.ElapsedTicks -lt $minimumTicks) {
        Throw-VerifierInfrastructure 'Task43P startup settle returned outside its required timing window.'
    }
    if ($null -eq $response -or $null -eq $response.PSObject.Properties['result'] -or
            $response.result -is [array] -or $response.result -isnot [pscustomobject]) {
        Throw-VerifierInfrastructure 'Task43P startup settle returned a malformed CDP result.'
    }
    $document = $null
    if ($response.result.PSObject.Properties['result'] -and
            $null -ne $response.result.result -and
            $response.result.result.PSObject.Properties['value']) {
        $document = $response.result.result.value
    }
    if ($response.result.PSObject.Properties['exceptionDetails'] -or
            $null -eq $document -or $document -is [array] -or
            $document -isnot [pscustomobject] -or
            $null -eq $document.PSObject.Properties['url'] -or
            $null -eq $document.PSObject.Properties['state'] -or
            $document.url -isnot [string] -or $document.url -cne 'about:blank' -or
            $document.state -isnot [string] -or $document.state -cne 'complete') {
        Throw-VerifierInfrastructure 'Task43P startup settle did not retain the complete owned blank document.'
    }
    Write-Host ("TASK43P STARTUP SETTLED milliseconds=$Milliseconds (existing route deadline retained)")
}

function navigateAndWaitForDocument($socket, [ref]$nextId, [string]$url,
        [DateTime]$deadline, [ref]$failures, $NavigationMarker = $null) {
    if ($null -ne $NavigationMarker -and $NavigationMarker -isnot [ref]) {
        Throw-VerifierInfrastructure 'Navigation marker output must be a reference when supplied.'
    }
    $marker = [Guid]::NewGuid().ToString('N')
    if ($null -ne $NavigationMarker) { $NavigationMarker.Value = $marker }
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

function Get-Task43PSourceExperimentDefinition([string]$Id) {
    $path = 'GeneratedBoardInstance.java'
    $family = 'LED_INDICATOR'
    $topology = 'DIRECT_SERIES'
    $urlFamily = 'led'
    $failure = switch -CaseSensitive ($Id) {
        'renderer-only-j1-1-plus-20px' {
            $path = 'PcbWorkbenchRenderer.java'
            'task43p-renderer-pad-projection-mismatch:J1.1'
        }
        'renderer-only-j1-1-lead-plus-20px' {
            $path = 'PhysicalPartRenderTerminal.java'
            'task43p-renderer-surface-mismatch:J1.1'
        }
        'raw-copper-j1-1-endpoint-gap' { 'task43p-raw-copper-start-gap:J1.1' }
        'raw-net-mismatch' {
            'task43p-set-mismatch:manifest/raw board nets:expected=[GND, LED_NODE, VIN]:actual=[GND, LED_NODE, TASK43P_UNMANIFESTED_EMPTY, VIN]'
        }
        'solver-binding-j1-1-post-mismatch' {
            $path = 'BoardSimulationBindings.java'
            'task43p-solver-binding-endpoint-identity-mismatch:J1.1'
        }
        'solver-detachable-c1-plus-identity-mismatch' {
            $path = 'BoardSimulationBindings.java'
            $family = 'RC_DELAY'
            $topology = 'RC_CHARGE_DELAY'
            $urlFamily = 'rc'
            'task43p-solver-binding-endpoint-identity-mismatch:C1.+'
        }
        'package-mirror-mismatch' {
            $path = 'PcbComponentPlacement.java'
            'task43p-physical-package-variant-mismatch:J1'
        }
        'internally-self-consistent-wrong-mapping' {
            $path = 'LedIndicatorGenerator.java'
            'task43p-solver-oracle-net-mismatch:R1.1'
        }
        'omitted-manifest-terminal' {
            $path = 'Task43PPhysicalTruthDeveloperVerifier.java'
            'task43p-set-mismatch:manifest/raw board pads:expected=[J1.1, J1.2, LED1.A, R1.1, R1.2]:actual=[J1.1, J1.2, LED1.A, LED1.K, R1.1, R1.2]'
        }
        'snapshot-restore-resistance-current-omitted' {
            $path = 'Task41SimulationSnapshot.java'
            'Task 41 restore changed lastResistanceTestCurrent'
        }
        'public-remove-action-disabled' {
            $path = 'PcbWorkbenchController.java'
            'task43p-public-remove-direct-control-passed'
        }
        default { Throw-VerifierInfrastructure 'Unsupported exact Task43P source experiment ID.' }
    }
    return [pscustomobject]@{
        id = $Id; relativePath = 'src/com/lushprojects/circuitjs1/client/' + $path
        family = $family; topology = $topology
        seed = if ($Id -ceq 'public-remove-action-disabled') { 3 } else { 0 }
        urlFamily = $urlFamily
        expectedMarker = 'FAIL:' + $failure
    }
}

function Initialize-Task43PSourceExperiment() {
    if ($Task43PSourceExperiment -ceq '') { return }
    if (-not $Task43PForcedNegative -or $Task43P -or $Task43ForcedNegative -or
            $Task43Integrated -or [String]::IsNullOrWhiteSpace($EvidenceDirectory) -or
            $ExpectedExecutionProvenanceDigest -cnotmatch '^[0-9a-f]{64}$') {
        Throw-VerifierInfrastructure 'Task43P source experiment requires its sole forced-negative route, explicit evidence directory, and compiled execution provenance.'
    }
    $definition = Get-Task43PSourceExperimentDefinition $Task43PSourceExperiment
    $originalRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
    if ([StringComparer]::OrdinalIgnoreCase.Equals($originalRoot,
            $script:Task43PExecutionRepositoryRoot)) {
        Throw-VerifierInfrastructure 'Task43P source experiment requires a distinct disposable execution tree.'
    }
    $originalHash = Get-VerifierFileSha256 (Join-Path $originalRoot $definition.relativePath)
    $mutatedHash = Get-VerifierFileSha256 (Join-Path $script:Task43PExecutionRepositoryRoot `
        $definition.relativePath)
    if ($originalHash -cnotmatch '^[0-9a-f]{64}$' -or
            $mutatedHash -cnotmatch '^[0-9a-f]{64}$' -or $originalHash -ceq $mutatedHash) {
        Throw-VerifierInfrastructure 'Task43P source experiment did not prove distinct original and mutated source bytes.'
    }
    $script:Task43PSourceDefinition = $definition
    $script:Task43PSourceObservation = [ordered]@{
        sourceMutationBeforeSha256 = $originalHash
        sourceMutationAfterSha256 = $mutatedHash
        repositoryBefore = $null; repositoryAfter = $null
    }
}

function Test-Task43PForcedNegativeMarker([string]$Marker) {
    return $Marker -ceq 'FAIL:task43p-forced-negative-canary' -or
        ($null -ne $script:Task43PSourceDefinition -and
            $Marker -ceq $script:Task43PSourceDefinition.expectedMarker)
}

function Test-Task43PSourceRepositoryStateEqual($Before, $After) {
    if (-not (Test-Task43PCandidateRepositoryState $script:Task43PCandidateSha `
            $Before $After)) { return $false }
    return Test-Task43PExecutionProvenanceEqual $Before.executionProvenance `
        $After.executionProvenance
}

function Capture-Task43PSourceObservation($RepositoryBefore) {
    $after = Get-Task43PRepositoryState $script:Task43PExecutionRoots
    if ($null -eq $script:Task43PSourceObservation -or
            -not (Test-Task43PSourceRepositoryStateEqual $RepositoryBefore $after)) {
        Throw-VerifierInfrastructure 'Task43P source-negative route changed its source, verifier, or compiled execution tree.'
    }
    $script:Task43PSourceObservation.repositoryBefore = $RepositoryBefore
    $script:Task43PSourceObservation.repositoryAfter = $after
}

function Get-Task43PPublicRemovePlayerState($Socket, [ref]$NextId,
        [ref]$Failures, [DateTime]$Deadline) {
    $expression = @'
(()=>{
 const buttons=[...document.querySelectorAll('button')];
 const remove=buttons.filter(b=>b.innerText.trim()==='Remove component');
 const button=remove.length===1?remove[0]:null;
 const rect=button?button.getBoundingClientRect():null;
 const x=rect?rect.left+rect.width/2:0,y=rect?rect.top+rect.height/2:0;
 const hit=button?document.elementFromPoint(x,y):null;
 const panel=button?button.closest('.tsj-component-panel'):null;
 const body=document.body?document.body.innerText:'';
 const power=buttons.filter(b=>b.innerText.trim()==='Board Power: OFF');
 return {point:{x,y},state:{
  powerOff:power.length===1&&!power[0].disabled,
  selectedInstalledR1:!!panel&&/\bR1\b/.test(panel.innerText)&&panel.innerText.includes('State: Installed')&&panel.innerText.includes('Type: resistor'),
  emptyTray:body.includes('No removed parts'),
  removedOriginalVisible:buttons.some(b=>b.innerText.trim()==='R1_ORIGINAL - Removed resistor'),
  removeButtonCount:remove.length,removeButtonDisabled:!!button&&button.disabled,
  removeButtonVisible:!!rect&&rect.width>0&&rect.height>0&&rect.top>=0&&rect.bottom<=innerHeight&&rect.left>=0&&rect.right<=innerWidth,
  removeButtonHitTest:!!button&&(hit===button||button.contains(hit)),
  liftLeadEnabled:!!panel&&[...panel.querySelectorAll('button')].some(b=>b.innerText.trim().startsWith('Lift lead ')&&!b.disabled)
 }};
})()
'@
    return evaluateCdp $Socket $NextId $expression $Failures $Deadline
}

function Invoke-Task43PPublicRemoveProbe($Socket, [ref]$NextId,
        [DateTime]$Deadline, [ref]$Failures) {
    $proof = $script:Task43ForcedNegativeProof
    $raw = evaluateCdp $Socket $NextId `
        "document.documentElement.getAttribute('data-tsj-task43p-public-remove-control') || ''" `
        $Failures $Deadline
    if ($raw -isnot [string] -or [String]::IsNullOrWhiteSpace($raw)) {
        Throw-VerifierInfrastructure 'Public Remove direct controller did not publish its proof.'
    }
    try { $direct = $raw | ConvertFrom-Json -ErrorAction Stop } catch {
        Throw-VerifierInfrastructure 'Public Remove direct controller proof was not valid JSON.'
    }
    # Start a fresh normal-player document on this same owned browser. Geometry
    # is only the existing coordinate aid; no Task43P/forced/debug query remains.
    $playerUrl = "$BaseUrl/circuitjs.html?tsjChallenge=led&seed=3&tsjVerifyGeometry=true"
    $navigationMarker = ''
    [void](navigateAndWaitForDocument $Socket $NextId $playerUrl $Deadline $Failures `
        ([ref]$navigationMarker))
    waitForCdp $Socket $NextId `
        "document.body&&document.body.innerText.includes('Indicator does not light.')&&!!window.__tsjPcbGeometry" `
        $Deadline $Failures 'public Remove normal-player challenge'
    $r1 = getCanvasPoint $Socket $NextId 'component:R1' $Failures
    if ($null -eq $r1) { Throw-VerifierInfrastructure 'Public Remove normal-player R1 coordinate was missing.' }
    clickPoint $Socket $NextId $r1 'left' $Failures
    waitForCdp $Socket $NextId `
        "[...document.querySelectorAll('button')].some(b=>b.innerText.trim()==='Remove component')" `
        $Deadline $Failures 'public Remove component controls'
    clickButtonAndWaitForPredicate $Socket $NextId 'Board Power: ON' `
        "[...document.querySelectorAll('button')].some(b=>b.innerText.trim()==='Board Power: OFF')" `
        $Deadline $Failures 'public Remove unpowered board'
    waitForAnimationFrames $Socket $NextId $Deadline $Failures
    $before = $null
    for ($attempt = 0; $attempt -lt 12; $attempt++) {
        $before = Get-Task43PPublicRemovePlayerState $Socket $NextId $Failures $Deadline
        if ($before.state.removeButtonVisible) { break }
        $wheelY = if ([double]$before.point.y -gt 0) { 480 } else { -480 }
        [void](invokeCdp $Socket $NextId 'Input.dispatchMouseEvent' @{
            type = 'mouseWheel'; x = [Math]::Max(1, [Math]::Min(1439, [double]$before.point.x))
            y = 500; deltaX = 0; deltaY = $wheelY
        } $Failures $Deadline)
        waitForAnimationFrames $Socket $NextId $Deadline $Failures
    }
    Assert-Task43PPublicRemoveState $before.state 'before'
    # clickPoint always sends a real pressed/released pair, including for a
    # disabled button. The DOM is inspected, never used to dispatch a click.
    clickPoint $Socket $NextId $before.point 'left' $Failures
    waitForAnimationFrames $Socket $NextId $Deadline $Failures
    $after = Get-Task43PPublicRemovePlayerState $Socket $NextId $Failures $Deadline
    $normalUrl = evaluateCdp $Socket $NextId 'location.href' $Failures $Deadline
    $evidence = [pscustomobject]@{
        protocol = 'TSJ-TASK43P-PUBLIC-REMOVE-1'; runId = $proof.RunId; routeId = $proof.RouteId
        navigationMarker = $navigationMarker
        normalPlayerUrl = $normalUrl; inputMethod = 'Input.dispatchMouseEvent'; inputEventCount = 2
        directControllerPositive = $direct; before = $before.state; after = $after.state
    }
    Assert-Task43PPublicRemoveEvidence $evidence $proof.RunId $proof.RouteId `
        $proof.Nonce $proof.RequestId $proof.ExecutionDigest
    captureBrowserScreenshot $Socket $NextId `
        (getVerifierEvidencePath 'task43p-public-remove-disabled.png') $Failures
    $script:Task43PSourceObservation.publicRemove = $evidence
    Write-Host 'OBSERVED public Remove unreachable through real mouse input; independent workbench dispatch succeeded.'
}

function Write-Task43PSourceNegativeFinalProof([int]$ExitCode) {
    $definition = $script:Task43PSourceDefinition
    $observation = $script:Task43PSourceObservation
    if ($ExitCode -ne 1 -or $null -eq $definition -or $null -eq $observation -or
            -not (Test-Task43ForcedNegativeProof $definition.expectedMarker -RequireFinalCleanup)) {
        Throw-VerifierInfrastructure 'Task43P source-negative proof was incomplete after final verifier cleanup.'
    }
    $after = Get-Task43PRepositoryState $script:Task43PExecutionRoots
    if (-not (Test-Task43PSourceRepositoryStateEqual $observation.repositoryBefore $after) -or
            -not (Test-Task43PSourceRepositoryStateEqual $observation.repositoryAfter $after)) {
        Throw-VerifierInfrastructure 'Task43P source-negative provenance changed before durable final proof.'
    }
    $record = [ordered]@{
        protocol = 'troubleshootjs-task43p-source-negative-proof-v1'; exit = 1
        experiment = [ordered]@{
            id = $definition.id; relativePath = $definition.relativePath
            family = $definition.family; topology = $definition.topology; seed = $definition.seed
            expectedMarker = $definition.expectedMarker
        }
        sourceMutationBeforeSha256 = $observation.sourceMutationBeforeSha256
        sourceMutationAfterSha256 = $observation.sourceMutationAfterSha256
        compiledExecutionDigest = $script:Task43PExecutionProvenance.Digest
        repositoryBefore = $observation.repositoryBefore; repositoryAfter = $after
        browserStartupSettleMilliseconds = $Task43PStartupSettleMilliseconds
        forcedNegativeProof = Get-Task43ForcedNegativeProofRecord
    }
    if ($definition.id -ceq 'public-remove-action-disabled') {
        if (-not $observation.Contains('publicRemove')) {
            Throw-VerifierInfrastructure 'Public Remove source proof omitted real player input evidence.'
        }
        $proof = $script:Task43ForcedNegativeProof
        Assert-Task43PPublicRemoveEvidence $observation.publicRemove $proof.RunId `
            $proof.RouteId $proof.Nonce $proof.RequestId $proof.ExecutionDigest
        $record.publicRemove = $observation.publicRemove
    }
    $path = getVerifierEvidencePath 'task43p-source-negative-proof.json'
    Write-VerifierEvidenceText $path ($record | ConvertTo-Json -Depth 30) `
        'Task43P source-negative final proof'
    Register-VerifierEvidenceArtifact $script:VerifierContext $path
    Write-Host ("TASK43P SOURCE NEGATIVE CAUGHT id=$($definition.id) proof=$path")
}

function Get-Task43ForcedNegativeExpectedMarker() {
    if ($null -ne $script:Task43PSourceDefinition) {
        return $script:Task43PSourceDefinition.expectedMarker
    }
    if ($Task43PForcedNegative) { return 'FAIL:task43p-forced-negative-canary' }
    if ($Task43ForcedNegative) { return 'FAIL:task43-forced-negative-canary' }
    return ''
}

function Get-Task43ForcedNegativeExpectedRoute([string]$ExpectedMarker) {
    if ($null -ne $script:Task43PSourceDefinition -and
            $ExpectedMarker -ceq $script:Task43PSourceDefinition.expectedMarker) {
        return 'task43p source-negative ' + $script:Task43PSourceDefinition.id
    }
    if ($ExpectedMarker -eq 'FAIL:task43p-forced-negative-canary') {
        return 'task43p forced-negative canary'
    }
    if ($ExpectedMarker -eq 'FAIL:task43-forced-negative-canary') {
        return 'task43 forced-negative canary'
    }
    return ''
}

function Get-Task43ForcedNegativeExpectedDiagnostic([string]$ExpectedMarker,
        [string]$Family = 'led/controlled-indicator', [int]$Seed = 3) {
    $proof = $script:Task43ForcedNegativeProof
    if ($null -eq $proof -or $proof.Invocation -ne $true -or
            [string]$proof.ExpectedMarker -cne $ExpectedMarker) {
        Throw-VerifierInfrastructure 'Forced-negative diagnostic construction had no matching active proof.'
    }
    $sourceRoute = $null -ne $script:Task43PSourceDefinition -and
        $ExpectedMarker -ceq $script:Task43PSourceDefinition.expectedMarker
    if ($sourceRoute) {
        $Family = $script:Task43PSourceDefinition.family + '/' + $script:Task43PSourceDefinition.topology
        $Seed = $script:Task43PSourceDefinition.seed
    }
    $diagnostic = 'Console failure: exception in runCircuit java.lang.IllegalStateException: ' +
        'Generated board verification failed for ' + $Family + ', seed ' + [string]$Seed + ': ' +
        $ExpectedMarker.Substring(5)
    if (Test-Task43PForcedNegativeMarker $ExpectedMarker) {
        foreach ($field in @('Nonce', 'RouteId', 'RequestId', 'ExecutionDigest')) {
            if ([String]::IsNullOrWhiteSpace([string]$proof.$field)) {
                Throw-VerifierInfrastructure "Task43P forced-negative diagnostic omitted proof field '$field'."
            }
        }
        $diagnostic += ' nonce=' + [string]$proof.Nonce +
            ' route=' + [string]$proof.RouteId +
            ' request=' + [string]$proof.RequestId +
            ' execution=' + [string]$proof.ExecutionDigest
        if ($sourceRoute) {
            $diagnostic += ' experiment=' + $script:Task43PSourceDefinition.id +
                ' run=' + [string]$proof.RunId
        }
    }
    return $diagnostic
}

function Test-Task43ForcedFailureDiagnosticText([string]$Failure,
        [string]$ExpectedFailure, [string]$ExpectedNonce = '',
        [string]$ExpectedRouteId = '', [string]$ExpectedRequestId = '',
        [string]$ExpectedExecutionDigest = '') {
    if ($ExpectedFailure -ne 'FAIL:task43-forced-negative-canary' -and
            -not (Test-Task43PForcedNegativeMarker $ExpectedFailure)) { return $false }
    if ([String]::IsNullOrWhiteSpace($Failure)) { return $false }
    $marker = [regex]::Escape($ExpectedFailure.Substring(5))
    # Keep this a complete-string match.  A substring or a diagnostic with a
    # trailing error line is not positive proof of the Java forced-negative
    # boundary.
    $familyPattern = '[^,\r\n]+, seed \d+'
    $sourceRoute = $null -ne $script:Task43PSourceDefinition -and
        $ExpectedFailure -ceq $script:Task43PSourceDefinition.expectedMarker
    if ($sourceRoute) {
        $familyPattern = [regex]::Escape($script:Task43PSourceDefinition.family + '/' +
            $script:Task43PSourceDefinition.topology + ', seed ' +
            [string]$script:Task43PSourceDefinition.seed)
    }
    $pattern = '\AConsole failure: exception in runCircuit ' +
        'java\.lang\.IllegalStateException: Generated board verification failed for ' +
        $familyPattern + ': ' + $marker
    if (Test-Task43PForcedNegativeMarker $ExpectedFailure) {
        if ([String]::IsNullOrWhiteSpace($ExpectedNonce) -or
                [String]::IsNullOrWhiteSpace($ExpectedRouteId) -or
                [String]::IsNullOrWhiteSpace($ExpectedRequestId) -or
                $ExpectedExecutionDigest -notmatch '^[0-9a-f]{64}$') {
            return $false
        }
        $separator = if ($sourceRoute) { ' ' } else { '\s+' }
        $pattern += $separator + 'nonce=' + [regex]::Escape($ExpectedNonce) +
            $separator + 'route=' + [regex]::Escape($ExpectedRouteId) +
            $separator + 'request=' + [regex]::Escape($ExpectedRequestId) +
            $separator + 'execution=' + [regex]::Escape($ExpectedExecutionDigest)
        if ($sourceRoute) {
            $proof = $script:Task43ForcedNegativeProof
            if ($null -eq $proof -or [String]::IsNullOrWhiteSpace([string]$proof.RunId)) {
                return $false
            }
            $pattern += ' experiment=' + [regex]::Escape($script:Task43PSourceDefinition.id) +
                ' run=' + [regex]::Escape([string]$proof.RunId)
        }
    }
    $pattern += '\z'
    return [regex]::IsMatch($Failure, $pattern)
}

function Get-Task43ForcedNegativeNavigationUrl([string]$Url) {
    $proof = $script:Task43ForcedNegativeProof
    if ($null -eq $proof -or $proof.Invocation -ne $true -or
            -not (Test-Task43PForcedNegativeMarker $proof.ExpectedMarker)) {
        return $Url
    }
    Set-Task43ForcedNegativeRouteIdentity
    foreach ($field in @('Nonce', 'RouteId', 'RequestId', 'ExecutionDigest')) {
        if ([String]::IsNullOrWhiteSpace([string]$proof.$field)) {
            Throw-VerifierInfrastructure "Task43P forced-negative navigation omitted proof field '$field'."
        }
    }
    $query = @(
        'tsjVerifierForcedNonce=' + [Uri]::EscapeDataString([string]$proof.Nonce)
        'tsjVerifierForcedRoute=' + [Uri]::EscapeDataString([string]$proof.RouteId)
        'tsjVerifierForcedRequest=' + [Uri]::EscapeDataString([string]$proof.RequestId)
        'tsjVerifierExecutionDigest=' + [Uri]::EscapeDataString([string]$proof.ExecutionDigest)
    )
    if ($null -ne $script:Task43PSourceDefinition) {
        $query += 'tsjTask43PSourceExperiment=' + [Uri]::EscapeDataString(
            $script:Task43PSourceDefinition.id)
    }
    $fragment = ''
    $fragmentIndex = $Url.IndexOf('#')
    $baseUrl = $Url
    if ($fragmentIndex -ge 0) {
        $baseUrl = $Url.Substring(0, $fragmentIndex)
        $fragment = $Url.Substring($fragmentIndex)
    }
    $separator = if ($baseUrl.IndexOf('?') -ge 0) { '&' } else { '?' }
    return $baseUrl + $separator + ($query -join '&') + $fragment
}

function Reset-Task43ForcedNegativeProof([string]$ExpectedMarker,
        [string]$RouteName) {
    if ($ExpectedMarker -ne 'FAIL:task43-forced-negative-canary' -and
            -not (Test-Task43PForcedNegativeMarker $ExpectedMarker)) {
        Throw-VerifierInfrastructure 'Forced-negative proof reset received an unsupported expected marker.'
    }
    $runId = if ($null -ne $script:VerifierContext) {
        [string]$script:VerifierContext.RunId
    } else { '' }
    $script:Task43ForcedNegativeProof = [pscustomobject]@{
        Invocation = $true
        ExpectedMarker = $ExpectedMarker
        ExpectedRoute = $RouteName
        RunId = $runId
        RouteId = ''
        Nonce = if (Test-Task43PForcedNegativeMarker $ExpectedMarker) {
            [Guid]::NewGuid().ToString('N')
        } else { '' }
        RequestId = if (Test-Task43PForcedNegativeMarker $ExpectedMarker) {
            [Guid]::NewGuid().ToString('N')
        } else { '' }
        ExecutionDigest = if (Test-Task43PForcedNegativeMarker $ExpectedMarker) {
            if ($null -ne $script:Task43PExecutionProvenance -and
                    $script:Task43PExecutionProvenance.Digest -match '^[0-9a-f]{64}$') {
                [string]$script:Task43PExecutionProvenance.Digest
            } else {
                ('0' * 64)
            }
        } else { '' }
        MarkerObserved = $false
        ObservedMarker = ''
        MarkerRunId = ''
        MarkerRouteId = ''
        MarkerExpectedMarker = ''
        AnchoredDiagnosticProven = $false
        AnchoredJavaDiagnostic = ''
        DiagnosticExpectedMarker = ''
        DiagnosticRunId = ''
        DiagnosticRouteId = ''
        DiagnosticBaselineHead = ''
        RoutePassedAfterCleanup = $false
        FinalCleanupProven = $false
        Invalidated = $false
    }
    $script:task43ExpectedFailureObserved = $false
    $script:task43ExpectedFailureRoutePassed = $false
}

function Set-Task43ForcedNegativeRouteIdentity() {
    $proof = $script:Task43ForcedNegativeProof
    if ($null -eq $proof -or $proof.Invocation -ne $true) { return }
    if ($null -eq $script:VerifierContext -or
            [String]::IsNullOrWhiteSpace([string]$script:VerifierCurrentRouteId)) {
        Throw-VerifierInfrastructure 'Forced-negative route did not establish a run/route identity.'
    }
    if ([String]::IsNullOrWhiteSpace([string]$proof.RunId)) {
        $proof.RunId = [string]$script:VerifierContext.RunId
    }
    if ([string]$proof.RunId -ne [string]$script:VerifierContext.RunId) {
        Throw-VerifierInfrastructure 'Forced-negative route run identity changed after proof reset.'
    }
    if ((Test-Task43PForcedNegativeMarker $proof.ExpectedMarker) -and
            ([String]::IsNullOrWhiteSpace([string]$proof.Nonce) -or
             [String]::IsNullOrWhiteSpace([string]$proof.RequestId) -or
             [string]$proof.ExecutionDigest -notmatch '^[0-9a-f]{64}$')) {
        Throw-VerifierInfrastructure 'Task43P forced-negative route did not establish nonce/request/execution identity.'
    }
    $proof.RouteId = [string]$script:VerifierCurrentRouteId
}

function Set-Task43ForcedNegativeMarkerObserved([string]$Marker) {
    $proof = $script:Task43ForcedNegativeProof
    if ($null -eq $proof -or $proof.Invocation -ne $true) { return }
    if ($Marker -ne [string]$proof.ExpectedMarker) {
        Throw-VerifierInfrastructure 'Forced-negative route observed a marker different from its expected marker.'
    }
    Set-Task43ForcedNegativeRouteIdentity
    $proof.MarkerObserved = $true
    $proof.ObservedMarker = $Marker
    $proof.MarkerRunId = [string]$proof.RunId
    $proof.MarkerRouteId = [string]$proof.RouteId
    $proof.MarkerExpectedMarker = $Marker
    $script:task43ExpectedFailureObserved = $true
}

function Set-Task43ForcedNegativeAnchoredDiagnostic([string]$Diagnostic,
        [string]$BaselineHead = '') {
    $proof = $script:Task43ForcedNegativeProof
    if ($null -eq $proof -or $proof.Invocation -ne $true) { return }
    if (-not $proof.MarkerObserved -or
            -not (Test-Task43ForcedFailureDiagnosticText $Diagnostic `
                ([string]$proof.ExpectedMarker) ([string]$proof.Nonce) `
                ([string]$proof.RouteId) ([string]$proof.RequestId) `
                ([string]$proof.ExecutionDigest))) {
        Throw-VerifierInfrastructure 'Forced-negative route did not prove its exact anchored Java diagnostic.'
    }
    if ((Test-Task43PForcedNegativeMarker $proof.ExpectedMarker) -and
            $BaselineHead -ne $script:Task43PCandidateSha) {
        Throw-VerifierInfrastructure 'Task43P forced-negative route did not retain the frozen candidate identity.'
    }
    Set-Task43ForcedNegativeRouteIdentity
    $proof.AnchoredDiagnosticProven = $true
    $proof.AnchoredJavaDiagnostic = $Diagnostic
    $proof.DiagnosticExpectedMarker = [string]$proof.ExpectedMarker
    $proof.DiagnosticRunId = [string]$proof.RunId
    $proof.DiagnosticRouteId = [string]$proof.RouteId
    # Retain the protocol field name; this records the frozen candidate HEAD.
    $proof.DiagnosticBaselineHead = [string]$BaselineHead
}

function Set-Task43ForcedNegativeRoutePassedAfterCleanup() {
    $proof = $script:Task43ForcedNegativeProof
    if ($null -eq $proof -or $proof.Invocation -ne $true) { return }
    if ($proof.Invalidated -eq $true -or
            -not $proof.MarkerObserved -or
            -not $proof.AnchoredDiagnosticProven -or
            [String]::IsNullOrWhiteSpace([string]$proof.RouteId)) {
        Throw-VerifierInfrastructure 'Forced-negative route bookkeeping was incomplete before cleanup success.'
    }
    $proof.RoutePassedAfterCleanup = $true
    $script:task43ExpectedFailureRoutePassed = $true
}

function Invalidate-Task43ForcedNegativeProof() {
    $proof = $script:Task43ForcedNegativeProof
    if ($null -eq $proof -or $proof.Invocation -ne $true) { return }
    # Preserve marker/diagnostic observations for evidence, but never preserve
    # a success claim after any later route, cleanup, or ledger uncertainty.
    $proof.RoutePassedAfterCleanup = $false
    $proof.FinalCleanupProven = $false
    $proof.Invalidated = $true
    $script:task43ExpectedFailureRoutePassed = $false
}

function Set-Task43ForcedNegativeFinalCleanupProven([bool]$Proven) {
    $proof = $script:Task43ForcedNegativeProof
    if ($null -eq $proof -or $proof.Invocation -ne $true) { return }
    if (-not $Proven -or $proof.Invalidated -eq $true -or
            $proof.RoutePassedAfterCleanup -ne $true) {
        Invalidate-Task43ForcedNegativeProof
        return
    }
    $proof.FinalCleanupProven = $true
}

function Get-Task43ForcedNegativeProofRecord() {
    $proof = $script:Task43ForcedNegativeProof
    $invocation = if ($null -ne $proof -and $proof.Invocation -eq $true) {
        $true
    } else { [bool]($Task43ForcedNegative -or $Task43PForcedNegative) }
    $expectedMarker = if ($null -ne $proof -and
            -not [String]::IsNullOrWhiteSpace([string]$proof.ExpectedMarker)) {
        [string]$proof.ExpectedMarker
    } else { Get-Task43ForcedNegativeExpectedMarker }
    $expectedRoute = if ($null -ne $proof -and
            -not [String]::IsNullOrWhiteSpace([string]$proof.ExpectedRoute)) {
        [string]$proof.ExpectedRoute
    } else { Get-Task43ForcedNegativeExpectedRoute $expectedMarker }
    return [ordered]@{
        protocol = 'troubleshootjs-forced-negative-proof-v1'
        invocation = $invocation
        expectedMarker = $expectedMarker
        expectedRoute = $expectedRoute
        runId = if ($null -ne $proof) { [string]$proof.RunId } else { '' }
        routeId = if ($null -ne $proof) { [string]$proof.RouteId } else { '' }
        nonce = if ($null -ne $proof) { [string]$proof.Nonce } else { '' }
        requestId = if ($null -ne $proof) { [string]$proof.RequestId } else { '' }
        executionDigest = if ($null -ne $proof) { [string]$proof.ExecutionDigest } else { '' }
        markerObserved = if ($null -ne $proof) { [bool]$proof.MarkerObserved } else { $false }
        observedMarker = if ($null -ne $proof) { [string]$proof.ObservedMarker } else { '' }
        markerRunId = if ($null -ne $proof) { [string]$proof.MarkerRunId } else { '' }
        markerRouteId = if ($null -ne $proof) { [string]$proof.MarkerRouteId } else { '' }
        markerExpectedMarker = if ($null -ne $proof) { [string]$proof.MarkerExpectedMarker } else { '' }
        anchoredDiagnosticProven = if ($null -ne $proof) { [bool]$proof.AnchoredDiagnosticProven } else { $false }
        anchoredJavaDiagnostic = if ($null -ne $proof) { [string]$proof.AnchoredJavaDiagnostic } else { '' }
        diagnosticExpectedMarker = if ($null -ne $proof) { [string]$proof.DiagnosticExpectedMarker } else { '' }
        diagnosticRunId = if ($null -ne $proof) { [string]$proof.DiagnosticRunId } else { '' }
        diagnosticRouteId = if ($null -ne $proof) { [string]$proof.DiagnosticRouteId } else { '' }
        diagnosticBaselineHead = if ($null -ne $proof) { [string]$proof.DiagnosticBaselineHead } else { '' }
        # Keep the descriptive fields above for local diagnostics and emit the
        # compact contract names as durable child-ledger fields.  The reader
        # validates both copies, so neither name can become an unchecked alias.
        routePassedAfterCleanup = if ($null -ne $proof) { [bool]$proof.RoutePassedAfterCleanup } else { $false }
        finalCleanupProven = if ($null -ne $proof) { [bool]$proof.FinalCleanupProven } else { $false }
        routePassed = if ($null -ne $proof) { [bool]$proof.RoutePassedAfterCleanup } else { $false }
        cleanupProven = if ($null -ne $proof) { [bool]$proof.FinalCleanupProven } else { $false }
        invalidated = if ($null -ne $proof) { [bool]$proof.Invalidated } else { $false }
    }
}

function Test-Task43ForcedNegativeProof([string]$ExpectedMarker,
        [switch]$RequireFinalCleanup) {
    $proof = $script:Task43ForcedNegativeProof
    if ($null -eq $proof -or $proof.Invocation -ne $true -or
            [string]$proof.ExpectedMarker -ne $ExpectedMarker -or
            [string]$proof.ExpectedRoute -ne
                (Get-Task43ForcedNegativeExpectedRoute $ExpectedMarker) -or
            $proof.Invalidated -eq $true -or
            $proof.MarkerObserved -ne $true -or
            [string]$proof.ObservedMarker -ne $ExpectedMarker -or
            [string]$proof.MarkerExpectedMarker -ne $ExpectedMarker -or
            $proof.AnchoredDiagnosticProven -ne $true -or
            [String]::IsNullOrWhiteSpace([string]$proof.AnchoredJavaDiagnostic) -or
            -not (Test-Task43ForcedFailureDiagnosticText `
                ([string]$proof.AnchoredJavaDiagnostic) $ExpectedMarker `
                ([string]$proof.Nonce) ([string]$proof.RouteId) `
                ([string]$proof.RequestId) ([string]$proof.ExecutionDigest)) -or
            [string]$proof.DiagnosticExpectedMarker -ne $ExpectedMarker -or
            [String]::IsNullOrWhiteSpace([string]$proof.RunId) -or
            [String]::IsNullOrWhiteSpace([string]$proof.RouteId) -or
            [string]$proof.MarkerRunId -ne [string]$proof.RunId -or
            [string]$proof.MarkerRouteId -ne [string]$proof.RouteId -or
            [string]$proof.DiagnosticRunId -ne [string]$proof.RunId -or
            [string]$proof.DiagnosticRouteId -ne [string]$proof.RouteId -or
            $proof.RoutePassedAfterCleanup -ne $true) {
        return $false
    }
    if ((Test-Task43PForcedNegativeMarker $ExpectedMarker) -and
            [string]$proof.DiagnosticBaselineHead -ne
                $script:Task43PCandidateSha) {
        return $false
    }
    if ($null -ne $script:VerifierContext -and
            [string]$script:VerifierContext.RunId -ne [string]$proof.RunId) {
        return $false
    }
    if (-not [String]::IsNullOrWhiteSpace([string]$script:VerifierCurrentRouteId) -and
            [string]$script:VerifierCurrentRouteId -ne [string]$proof.RouteId) {
        return $false
    }
    if ($script:VerifierFailureExitCode -ne 0 -or
            -not [String]::IsNullOrWhiteSpace([string]$script:VerifierFailureKind) -or
            -not [String]::IsNullOrWhiteSpace([string]$script:VerifierFailureMessage)) {
        return $false
    }
    if ($RequireFinalCleanup -and $proof.FinalCleanupProven -ne $true) {
        return $false
    }
    return $true
}

function Get-Task43ForcedNegativeRouteExitCode([string]$ExpectedMarker) {
    if (Test-Task43ForcedNegativeProof $ExpectedMarker) { return 1 }
    Invalidate-Task43ForcedNegativeProof
    return 2
}

function Resolve-Task43ForcedNegativeTopLevelExitCode($CandidateExitCode,
        [string]$ExpectedMarker) {
    if ((Test-VerifierStrictIntegralValue $CandidateExitCode 0 2) -and
            [int]$CandidateExitCode -eq 1 -and
            (Test-Task43ForcedNegativeProof $ExpectedMarker -RequireFinalCleanup)) {
        return 1
    }
    Invalidate-Task43ForcedNegativeProof
    return 2
}

function isExpectedTask43ForcedFailureDiagnostic([string]$failure,
        [string]$expectedFailure, [string]$runId = '', [string]$routeId = '',
        [string]$baselineHead = '') {
    $proof = $script:Task43ForcedNegativeProof
    $expectedNonce = if ((Test-Task43PForcedNegativeMarker $expectedFailure) -and
            $null -ne $proof) { [string]$proof.Nonce } else { '' }
    $expectedRequestId = if ((Test-Task43PForcedNegativeMarker $expectedFailure) -and
            $null -ne $proof) { [string]$proof.RequestId } else { '' }
    $expectedExecutionDigest = if ((Test-Task43PForcedNegativeMarker $expectedFailure) -and
            $null -ne $proof) { [string]$proof.ExecutionDigest } else { '' }
    if (-not (Test-Task43ForcedFailureDiagnosticText $failure $expectedFailure `
            $expectedNonce $routeId $expectedRequestId $expectedExecutionDigest)) {
        return $false
    }
    if ([String]::IsNullOrWhiteSpace($runId) -or
            [String]::IsNullOrWhiteSpace($routeId) -or
            $null -eq $script:VerifierContext -or
            $script:VerifierContext.RunId -ne $runId -or
            $script:VerifierCurrentRouteId -ne $routeId) { return $false }
    if ((Test-Task43PForcedNegativeMarker $expectedFailure) -and
            $baselineHead -ne $script:Task43PCandidateSha) { return $false }
    return $true
}

function Write-VerifierRouteTiming([string]$Name, [string]$Phase, [string]$Outcome,
        [Diagnostics.Stopwatch]$Clock, [DateTime]$Deadline,
        [string]$Operation, [string]$OperationStage, [string]$Cleanup,
        [string]$EvidenceStage = '') {
    # Diagnostic output is not proof and must never replace the primary error.
    # Record method names only: no expressions, URLs, or browser payloads.
    try {
        $remaining = if ($Deadline -eq [DateTime]::MinValue) { $null } else {
            [Math]::Round([Math]::Max(0, ($Deadline - [DateTime]::UtcNow).TotalMilliseconds))
        }
        $record = [ordered]@{
            protocol = 'troubleshootjs-route-timing-v1'; route = $Name
            phase = $Phase; outcome = $Outcome
            elapsedMilliseconds = [Math]::Round($Clock.Elapsed.TotalMilliseconds)
            remainingRouteMilliseconds = $remaining
            cdpOperation = $Operation; cdpStage = $OperationStage; cleanup = $Cleanup
            evidenceStage = $EvidenceStage
        }
        Write-Host ('VERIFIER_ROUTE_TIMING ' + ($record | ConvertTo-Json -Compress))
    } catch { }
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
    $routeClock = [Diagnostics.Stopwatch]::StartNew()
    $phase = 'browser-startup'
    $deadline = [DateTime]::MinValue
    $cleanupState = 'not-started'
    $script:VerifierLastCdpOperation = ''
    $script:VerifierLastCdpStage = ''
    $script:VerifierEvidenceStage = ''
    $isTask43ForcedRoute = ($expectedFailure -eq 'FAIL:task43-forced-negative-canary' -or
        (Test-Task43PForcedNegativeMarker $expectedFailure))
    if ($isTask43ForcedRoute) {
        # This is the only reset point for the route proof.  Later failures
        # invalidate it; they never create a fresh success state.
        Reset-Task43ForcedNegativeProof $expectedFailure $name
    }
    if ($name -like 'task43p *') {
        $task43pRepositoryBefore = Get-Task43PRepositoryState $script:Task43PExecutionRoots
    }
    $task43pBaselineHead = if ($null -eq $task43pRepositoryBefore) { '' } else {
        [string]$task43pRepositoryBefore.headSha
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
        $phase = 'protocol-enable'
        [void](invokeCdp $socket ([ref]$nextId) 'Runtime.enable' @{} ([ref]$failures) $deadline)
        [void](invokeCdp $socket ([ref]$nextId) 'Page.enable' @{} ([ref]$failures) $deadline)
        if ($name -like 'task43p *') {
            $phase = 'startup-settle'
            Wait-Task43PBrowserStartup $socket ([ref]$nextId) ([ref]$failures) `
                $deadline $Task43PStartupSettleMilliseconds
        }
        $routeUrl = Get-Task43ForcedNegativeNavigationUrl $url
        $phase = 'navigation'
        [void](navigateAndWaitForDocument $socket ([ref]$nextId) $routeUrl $deadline ([ref]$failures))
        if ($expectedComplaint) {
            $escapedComplaint = $expectedComplaint.Replace("'", "\\'")
            $ticketExpression = "(()=>{const title=[...document.querySelectorAll('.tsj-component-title')].find(e=>e.textContent.trim()==='Service Ticket');if(!title||!title.parentElement)return false;const lines=title.parentElement.innerText.split(/\r?\n+/).map(x=>x.trim()).filter(Boolean);return lines.length===2&&lines[0]==='Service Ticket'&&lines[1]==='$escapedComplaint';})()"
            waitForCdp $socket ([ref]$nextId) $ticketExpression $deadline ([ref]$failures) 'solver-validated Service Ticket complaint'
        }
        $phase = 'application-result'
        do {
            $response = invokeCdp $socket ([ref]$nextId) 'Runtime.evaluate' @{
                expression = "document.documentElement.getAttribute('data-tsj-verification') || ''"; returnByValue = $true
            } ([ref]$failures) $deadline
            $verificationResult = [string]$response.result.result.value
            if ($verificationResult.StartsWith('FAIL:')) {
                if ($expectedFailure -and $verificationResult -eq $expectedFailure) {
                    $expectedFailureObserved = $true
                    if ($isTask43ForcedRoute) {
                        Set-Task43ForcedNegativeMarkerObserved $verificationResult
                    }
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
                isExpectedTask43ForcedFailureDiagnostic ([string]$_) $expectedFailure `
                    $script:VerifierContext.RunId $script:VerifierCurrentRouteId `
                    $task43pBaselineHead
            })
            if ($expectedFailureDiagnostics.Count -eq 0) {
                throw "expected application failure '$expectedFailure' had no anchored Java console diagnostic"
            }
            if ($isTask43ForcedRoute) {
                Set-Task43ForcedNegativeAnchoredDiagnostic `
                    ([string]$expectedFailureDiagnostics[0]) $task43pBaselineHead
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
            # Drain final page diagnostics before serializing/hashing evidence;
            # report I/O must not precede another route-deadline CDP call.
            $phase = 'page-diagnostics'
            Start-Sleep -Milliseconds 100
            [void](evaluateCdp $socket ([ref]$nextId) "document.readyState" ([ref]$failures) $deadline)
            $phase = 'evidence-capture'
            if ($null -ne $script:Task43PSourceDefinition) {
                if ($script:Task43PSourceDefinition.id -ceq 'public-remove-action-disabled') {
                    Invoke-Task43PPublicRemoveProbe $socket ([ref]$nextId) $deadline ([ref]$failures)
                }
                Capture-Task43PSourceObservation $task43pRepositoryBefore
            } else {
                Capture-Task43PEvidence $socket ([ref]$nextId) $deadline ([ref]$failures) `
                    $name $expected $verificationResult $task43pRepositoryBefore
            }
            Write-VerifierRouteTiming $name $phase 'recorded' $routeClock $deadline `
                $script:VerifierLastCdpOperation $script:VerifierLastCdpStage $cleanupState $script:VerifierEvidenceStage
        } else {
            $phase = 'page-diagnostics'
            Start-Sleep -Milliseconds 100
            [void](evaluateCdp $socket ([ref]$nextId) "document.readyState" ([ref]$failures) $deadline)
        }
        if ($script:VerifierEvidenceDirectory -and $name -match '^seed=(0|2) parallel$') {
            $phase = 'screenshot'
            captureBrowserScreenshot $socket ([ref]$nextId) (getVerifierEvidencePath ("parallel-seed-" + $Matches[1] + ".png")) ([ref]$failures)
        }
        if ($failures.Count -gt 0) {
            $unexpectedFailures = @($failures | Where-Object {
                -not $expectedFailureObserved -or
                    -not (isExpectedTask43ForcedFailureDiagnostic ([string]$_) $expectedFailure `
                        $script:VerifierContext.RunId $script:VerifierCurrentRouteId `
                        $task43pBaselineHead)
            })
            if ($unexpectedFailures.Count -gt 0) { throw ($unexpectedFailures -join '; ') }
        }
        $phase = 'browser-cleanup'
        $cleanupState = 'in-progress'
        cleanupBrowser $browser $socket $profile
        $cleanupState = 'complete'
        $socket = $null
        $browser = $null
        if ($isTask43ForcedRoute) {
            Set-Task43ForcedNegativeRoutePassedAfterCleanup
        }
        if ($expectedFailureObserved) {
            Write-Host "EXPECTED FAILURE $name - $expectedFailure (anchored Java console diagnostic observed)"
        } elseif ($verificationResult.StartsWith('UNPROVEN:')) {
            Write-Host "UNPROVEN $name - $verificationResult (typed evidence recorded)"
        } elseif ($verificationResult -ceq 'OBSERVED:task43p-runtime') {
            Write-Host ("OBSERVED $name - typed runtime evidence recorded; open blockers=" +
                $script:Task43PRuntimeOutcome.OpenBlockerCount)
        } else {
            Write-Host "PASS $name"
        }
        $success = $true
    } catch {
        Write-VerifierRouteTiming $name $phase 'failed' $routeClock $deadline `
            $script:VerifierLastCdpOperation $script:VerifierLastCdpStage $cleanupState $script:VerifierEvidenceStage
        Set-VerifierFailure $_ $name
    } finally {
        try {
            cleanupBrowser $browser $socket $profile
            $cleanupState = 'complete'
        } catch {
            $cleanupState = 'failed'
            Set-VerifierFailure $_ ($name + ' cleanup')
            $success = $false
        }
        if ($isTask43ForcedRoute -and -not $success) {
            Invalidate-Task43ForcedNegativeProof
        }
        Write-VerifierRouteTiming $name 'cleanup' $cleanupState $routeClock $deadline `
            $script:VerifierLastCdpOperation $script:VerifierLastCdpStage $cleanupState $script:VerifierEvidenceStage
        $routeClock.Stop()
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
            waitForCdp $socket $nextId $successExpression $settleDeadline $failures $description
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
            waitForCdp $socket $nextId "[...document.querySelectorAll('.tsj-component-panel')].some(p=>p.innerText.includes('$escapedSelectedText')&&p.innerText.includes('State: Loose'))" $selectionDeadline $failures 'selected loose tray part panel'
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
            clickPoint $socket $nextId $attemptInfo 'left' $failures
            $selectionDeadline = [DateTime]::UtcNow.AddSeconds(5)
            waitForCdp $socket $nextId "document.activeElement===document.querySelectorAll('select')[$selectIndex]" $selectionDeadline $failures 'catalog select focus'
            sendKey $socket $nextId 'Escape' 27 $failures
            waitForCdp $socket $nextId "document.activeElement===document.querySelectorAll('select')[$selectIndex]" $selectionDeadline $failures 'catalog select focus after closing popup'
            sendKey $socket $nextId 'Home' 36 $failures
            waitForCdp $socket $nextId "(()=>{const e=document.querySelectorAll('select')[$selectIndex];return document.activeElement===e&&e.selectedIndex===0&&e.selectedOptions[0].text===e.options[0].text;})()" $selectionDeadline $failures 'catalog first option after Home'
            for ($index = 1; $index -le [int]$attemptInfo.index; $index++) {
                sendKey $socket $nextId 'ArrowDown' 40 $failures
                $stepIndex = $index
                waitForCdp $socket $nextId "(()=>{const e=document.querySelectorAll('select')[$selectIndex],i=$stepIndex;return document.activeElement===e&&e.selectedIndex===i&&e.selectedOptions[0].text===e.options[i].text;})()" $selectionDeadline $failures "catalog option $stepIndex after ArrowDown"
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
        Write-VerifierEvidenceBytes $path ([Convert]::FromBase64String($result.result.data)) `
            'screenshot evidence'
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
        New-VerifierEvidenceDirectory $evidence 'stress-damage evidence' -Force
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
        $evidencePrefix = 'task39-player-' + [string]$session.RouteId + '-'
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
            captureBrowserScreenshot $socket ([ref]$nextId) (getVerifierEvidencePath ($evidencePrefix + 'initial.png')) ([ref]$failures)
        }
        foreach ($command in $commandButtons) {
            clickButton $socket ([ref]$nextId) $command ([ref]$failures)
            waitForAnimationFrames $socket ([ref]$nextId) $deadline ([ref]$failures)
        }
        if ($script:VerifierEvidenceDirectory) {
            captureBrowserScreenshot $socket ([ref]$nextId) (getVerifierEvidencePath ($evidencePrefix + 'after-inputs.png')) ([ref]$failures)
        }
        clickButton $socket ([ref]$nextId) $retestButton ([ref]$failures)
        waitForCdp $socket ([ref]$nextId) "document.body.innerText.includes('Customer retest did not pass.')" $deadline ([ref]$failures) 'Task 39 visible customer retest result'
        if ($script:VerifierEvidenceDirectory) {
            captureBrowserScreenshot $socket ([ref]$nextId) (getVerifierEvidencePath ($evidencePrefix + 'retest-result.png')) ([ref]$failures)
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
    # This is the raw Process.ExitCode boundary. Do not stringify or parse:
    # strings, fractions, booleans, arrays, and null are not exact child
    # process evidence and must be infrastructure failures.
    if (-not (Test-VerifierStrictIntegralValue $RawExitCode 0 ([int]::MaxValue))) {
        Throw-VerifierInfrastructure 'Integrated child returned a malformed non-integral exit code.'
    }
    $numericExitCode = [int]$RawExitCode
    if ($numericExitCode -eq 0) {
        return (Resolve-VerifierChildExitCode $true $numericExitCode)
    }
    return (Resolve-VerifierChildExitCode $false $numericExitCode)
}

function Start-VerifierIntegratedChildProcess($FilePath, $Arguments) {
    # Keep both values raw until the same shared boundary used by every other
    # verifier process launch. In particular, do not construct even a
    # Diagnostics.Process object before malformed input has been rejected.
    $invocation = Assert-VerifierProcessInvocationBoundary $FilePath $Arguments `
        'integrated child'
    $process = $null
    try {
        $process = New-Object Diagnostics.Process
        $startInfo = New-Object Diagnostics.ProcessStartInfo
        $startInfo.FileName = $invocation.FilePath
        $startInfo.Arguments = ConvertTo-VerifierArgumentString $invocation.Arguments
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

function Invoke-VerifierIntegratedProcessInvocationBoundaryCanary() {
    $cases = @(
        [pscustomobject]@{ Name = 'null-path'; FilePath = $null; Arguments = @() }
        [pscustomobject]@{ Name = 'boolean-path'; FilePath = $true; Arguments = @() }
        [pscustomobject]@{ Name = 'array-path'; FilePath = @('powershell.exe'); Arguments = @() }
        [pscustomobject]@{ Name = 'null-arguments'; FilePath = 'powershell.exe'; Arguments = $null }
        [pscustomobject]@{ Name = 'scalar-arguments'; FilePath = 'powershell.exe'; Arguments = '-NoProfile' }
        [pscustomobject]@{ Name = 'boolean-arguments'; FilePath = 'powershell.exe'; Arguments = $true }
        [pscustomobject]@{ Name = 'boolean-element'; FilePath = 'powershell.exe'; Arguments = @('-NoProfile', $true) }
        [pscustomobject]@{ Name = 'object-element'; FilePath = 'powershell.exe'; Arguments = @([pscustomobject]@{ Value = '-NoProfile' }) }
    )
    foreach ($case in $cases) {
        $rejected = $false
        $returnedProcess = $null
        try {
            $returnedProcess = Start-VerifierIntegratedChildProcess $case.FilePath $case.Arguments
        } catch {
            $rejected = Test-VerifierInfrastructureError $_
        }
        if ($null -ne $returnedProcess) {
            try { Stop-VerifierIntegratedChildExact $returnedProcess } catch { }
            throw "Integrated process boundary canary '$($case.Name)' returned a process for malformed input."
        }
        if (-not $rejected) {
            throw "Integrated process boundary canary '$($case.Name)' was not rejected before process creation."
        }
    }
    Write-Host 'PASS:integrated child raw invocation boundary rejects malformed input before process construction'
}

function Invoke-VerifierRequestedExitCodeCanary() {
    $cases = @(
        [pscustomobject]@{ Name = 'zero'; Value = 0; Expected = 0 }
        [pscustomobject]@{ Name = 'one'; Value = 1; Expected = 1 }
        [pscustomobject]@{ Name = 'two'; Value = 2; Expected = 2 }
        [pscustomobject]@{ Name = 'negative'; Value = -1; Expected = 2 }
        [pscustomobject]@{ Name = 'arbitrary'; Value = 3; Expected = 2 }
        [pscustomobject]@{ Name = 'numeric-string'; Value = '1'; Expected = 2 }
        [pscustomobject]@{ Name = 'fraction'; Value = 1.5; Expected = 2 }
        [pscustomobject]@{ Name = 'boolean'; Value = $true; Expected = 2 }
        [pscustomobject]@{ Name = 'array'; Value = @(1); Expected = 2 }
    )
    foreach ($case in $cases) {
        $exception = [System.InvalidOperationException]::new(
            ('requested-exit-code canary ' + $case.Name))
        $exception.Data['VerifierExitCode'] = $case.Value
        $script:VerifierFailureExitCode = 0
        $actual = Get-VerifierRequestedExitCode $exception
        if ([int]$actual -ne [int]$case.Expected) {
            throw "Requested exit-code canary '$($case.Name)' returned $actual instead of $($case.Expected)."
        }
    }
    Write-Host 'PASS:requested verifier exits are strictly whitelisted to 0/1/2; malformed codes resolve to exit 2'
}

function Stop-VerifierIntegratedChildExact($Process) {
    if ($null -eq $Process) { return }
    try {
        $Process.Refresh()
        if (-not [bool]$Process.HasExited) {
            $current = Get-Process -Id ([int]$Process.Id) -ErrorAction Stop
            $expectedStart = [long](Get-VerifierProcessStartTicks $Process)
            $actualStart = [long](Get-VerifierProcessStartTicks $current)
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

function Resolve-VerifierIntegratedChildShellStatus([bool]$ShellSuccess,
        $RawLastExitCode) {
    # This helper models only the suffix that runs when the invoked PowerShell
    # script returns to its parent shell.  A non-success or nonzero
    # LASTEXITCODE in that situation is ambiguous (it may be stale), so it is
    # infrastructure.  A direct child `exit 1/2` terminates the child host and
    # is classified from the outer Process.ExitCode instead.
    if (-not $ShellSuccess) { return 2 }
    if ($null -eq $RawLastExitCode -or
            [String]::IsNullOrWhiteSpace([string]$RawLastExitCode)) { return 0 }
    $numericExitCode = 0
    if (-not [int]::TryParse([string]$RawLastExitCode,
            [Globalization.NumberStyles]::Integer,
            [Globalization.CultureInfo]::InvariantCulture,
            [ref]$numericExitCode)) { return 2 }
    if ($numericExitCode -eq 0) { return 0 }
    return 2
}

function Get-VerifierIntegratedChildShellStatusTail() {
    return '; $tsjChildSuccess = [bool]$?; $tsjChildExit = $LASTEXITCODE; ' +
        'if (-not $tsjChildSuccess) { exit 2 } ' +
        'elseif ($null -ne $tsjChildExit -and $tsjChildExit -ne 0) { exit 2 } ' +
        'else { exit 0 }'
}

function Assert-IntegratedChildOutputContract([string]$label, [int]$expectedExit,
        [int]$childExit, [string[]]$childOutput, $ChildLedger = $null,
        [string]$ExpectedForcedMarker = '') {
    $printedFailures = @()
    $forcedNegativeExpectedFailure = ($expectedExit -eq 1 -and
        -not [String]::IsNullOrWhiteSpace($ExpectedForcedMarker))
    if ($forcedNegativeExpectedFailure) {
        # The generic child contract maps expected 1/actual 0 to application
        # exit 1.  Forced-negative children have a stricter boundary: prove
        # the exact child exit and durable Java proof before any generic
        # mismatch can select an application exit.
        if ($childExit -ne 1) {
            Throw-VerifierInfrastructure ("integrated forced-negative child $label " +
                "expected exact application exit 1 but returned $childExit")
        }
        Assert-VerifierIntegratedForcedNegativeProof $ChildLedger $expectedExit `
            $ExpectedForcedMarker
    }
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
    if ($forcedNegativeExpectedFailure) {
        # Generic 0/1/2 classification is intentionally insufficient for the
        # forced-negative child.  Exit 1 is accepted only with the durable
        # proof emitted by the child after its route and final cleanup.
        Assert-VerifierIntegratedForcedNegativeProof $ChildLedger $expectedExit `
            $ExpectedForcedMarker
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
        New-VerifierEvidenceDirectory $ledgerRoot 'integrated child ledger' -Force
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
        Write-VerifierEvidenceText $ledgerPath ($record | ConvertTo-Json -Depth 8) `
            'integrated child ledger'
        return $ledgerPath
    } catch {
        if (Test-VerifierInfrastructureError $_) { throw }
        Throw-VerifierInfrastructure ('Could not create the parent-visible integrated-child ledger: ' +
            (Get-VerifierErrorMessage $_))
    }
}

function Get-VerifierLedgerProperty($Object, [string]$Name, $IgnoredDefault) {
    if ($null -eq $Object -or [String]::IsNullOrWhiteSpace($Name)) {
        Throw-VerifierInfrastructure "Durable ledger omitted required field '$Name'."
    }
    if ($Object -is [System.Collections.IDictionary]) {
        if (-not $Object.Contains($Name)) {
            Throw-VerifierInfrastructure "Durable ledger omitted required field '$Name'."
        }
        return $Object[$Name]
    }
    $property = $Object.PSObject.Properties[$Name]
    if ($null -eq $property) {
        Throw-VerifierInfrastructure "Durable ledger omitted required field '$Name'."
    }
    return $property.Value
}

function Get-VerifierCanaryLedgerProperty($Object, [string]$Name,
        $Default = $null) {
    if ($null -eq $Object) { return $Default }
    if ($Object -is [System.Collections.IDictionary]) {
        if (-not $Object.Contains($Name)) { return $Default }
        return $Object[$Name]
    }
    if ($null -eq $Object.PSObject.Properties[$Name]) { return $Default }
    return $Object.PSObject.Properties[$Name].Value
}

function Get-VerifierRequiredLedgerProperty($Object, [string]$Name,
        [string]$Label) {
    if ($null -eq $Object -or [String]::IsNullOrWhiteSpace($Name) -or
            $null -eq $Object.PSObject.Properties[$Name]) {
        Throw-VerifierInfrastructure "$Label omitted required durable field '$Name'."
    }
    return $Object.PSObject.Properties[$Name].Value
}

function Get-VerifierLedgerPath($Path, [string]$Label, [string]$Root,
        [switch]$AllowRoot) {
    if (-not (Test-VerifierStrictStringValue $Path) -or
            [String]::IsNullOrWhiteSpace($Path)) {
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
    return $value -eq $Expected
}

function Assert-VerifierDurableBooleanPair($LedgerValue, $ManifestValue,
        [string]$Name, [switch]$AllowNull) {
    $ledgerBoolean = Get-VerifierDurableBooleanValue $LedgerValue $Name `
        ('ledger ' + $Name) -AllowNull
    $manifestBoolean = Get-VerifierDurableBooleanValue $ManifestValue $Name `
        ('manifest ' + $Name) -AllowNull
    if (-not $AllowNull -and ($null -eq $ledgerBoolean -or $null -eq $manifestBoolean)) {
        Throw-VerifierInfrastructure "Durable Boolean pair '$Name' was null instead of an exact Boolean."
    }
    if (($null -eq $ledgerBoolean) -xor ($null -eq $manifestBoolean) -or
            ($null -ne $ledgerBoolean -and $ledgerBoolean -ne $manifestBoolean)) {
        Throw-VerifierInfrastructure "Durable ledger and manifest disagreed on exact Boolean field '$Name'."
    }
}

function Assert-VerifierDurableStringPair($LedgerValue, $ManifestValue,
        [string]$Name) {
    $timestampField = $Name -in @('bindValidatedUtc', 'releasedUtc', 'listenerInspectionUtc')
    $normalizedValues = @()
    foreach ($copy in @(
            [pscustomobject]@{ Label = 'ledger'; Value = $LedgerValue }
            [pscustomobject]@{ Label = 'manifest'; Value = $ManifestValue }
        )) {
        $property = if ($null -eq $copy.Value) { $null } else {
            $copy.Value.PSObject.Properties[$Name]
        }
        if ($null -eq $property) {
            Throw-VerifierInfrastructure "Durable $($copy.Label) '$Name' was missing or was not an exact string."
        }
        if ($timestampField) {
            $normalizedValues += ConvertTo-VerifierStrictTimestampText $property.Value $true `
                ("Durable $($copy.Label) '$Name'") -AllowJsonDateTime
        } elseif (-not (Test-VerifierStrictStringProperty $copy.Value $Name)) {
            Throw-VerifierInfrastructure "Durable $($copy.Label) '$Name' was missing or was not an exact string."
        } else {
            $normalizedValues += $property.Value
        }
    }
    if (-not [String]::Equals($normalizedValues[0], $normalizedValues[1],
                [StringComparison]::Ordinal)) {
        Throw-VerifierInfrastructure "Durable ledger and manifest disagreed on exact string field '$Name'."
    }
    return $normalizedValues[0]
}

function Get-VerifierDurableArrayProperty($Object, [string]$Name,
        [string]$Label, [string]$RawJson = '') {
    if ($null -eq $Object -or [String]::IsNullOrWhiteSpace($Name) -or
            $null -eq $Object.PSObject.Properties[$Name]) {
        Throw-VerifierInfrastructure "$Label omitted its required array '$Name'."
    }
    $value = $Object.PSObject.Properties[$Name].Value
    if ($null -eq $value) {
        Throw-VerifierInfrastructure "$Label carried a non-array '$Name'; only an explicit empty array may represent absence."
    }
    if (-not ($value -is [array])) {
        # Windows PowerShell materializes a one-element JSON array as the
        # element object.  Accept that representation only when the original
        # JSON syntax proves the property was an array, then normalize to an
        # actual collection without inventing a missing/default value.
        if (-not (Test-VerifierRawJsonArrayProperty $RawJson $Name)) {
            Throw-VerifierInfrastructure "$Label carried a scalar '$Name' where the durable JSON did not prove an array."
        }
        return ,@($value)
    }
    return ,$value
}

function Assert-VerifierDurableArrayPair($LedgerValue, $ManifestValue,
        [string]$LedgerName, [string]$ManifestName, [string]$Label,
        [switch]$CompareItems, [string]$LedgerJson = '',
        [string]$ManifestJson = '') {
    $ledgerArray = Get-VerifierDurableArrayProperty $LedgerValue $LedgerName `
        ('ledger ' + $Label) $LedgerJson
    $manifestArray = Get-VerifierDurableArrayProperty $ManifestValue $ManifestName `
        ('manifest ' + $Label) $ManifestJson
    if ($ledgerArray.GetType() -ne $manifestArray.GetType() -or
            $ledgerArray.Count -ne $manifestArray.Count) {
        Throw-VerifierInfrastructure "Durable ledger and manifest disagreed on the exact '$Label' array shape."
    }
    if ($CompareItems) {
        for ($index = 0; $index -lt $ledgerArray.Count; $index++) {
            $ledgerItem = $ledgerArray[$index]
            $manifestItem = $manifestArray[$index]
            if (($null -eq $ledgerItem) -xor ($null -eq $manifestItem) -or
                    ($null -ne $ledgerItem -and
                     ($ledgerItem.GetType() -ne $manifestItem.GetType() -or
                      -not $ledgerItem.Equals($manifestItem)))) {
                Throw-VerifierInfrastructure "Durable ledger and manifest disagreed on exact '$Label' array value at index $index."
            }
        }
    }
    return [pscustomobject]@{ Ledger = $ledgerArray; Manifest = $manifestArray }
}

function Assert-VerifierDurableIntegralPair($LedgerValue, $ManifestValue,
        [string]$Name, [long]$Minimum = [long]::MinValue,
        [long]$Maximum = [long]::MaxValue) {
    foreach ($copy in @(
            [pscustomobject]@{ Label = 'ledger'; Value = $LedgerValue }
            [pscustomobject]@{ Label = 'manifest'; Value = $ManifestValue }
        )) {
        $property = if ($null -eq $copy.Value) { $null } else {
            $copy.Value.PSObject.Properties[$Name]
        }
        if ($null -eq $property -or
                -not (Test-VerifierStrictNonnegativeIntegerProperty $copy.Value $Name)) {
            Throw-VerifierInfrastructure "Durable $($copy.Label) '$Name' was missing or was not an exact integral value."
        }
        $value = $property.Value
        try {
            $inRange = ([long]$value -ge $Minimum -and [long]$value -le $Maximum)
        } catch { $inRange = $false }
        if (-not $inRange) {
            Throw-VerifierInfrastructure "Durable $($copy.Label) '$Name' was outside its allowed integral range."
        }
    }
    $ledgerProperty = $LedgerValue.PSObject.Properties[$Name]
    $manifestProperty = $ManifestValue.PSObject.Properties[$Name]
    if ($ledgerProperty.Value.GetType() -ne $manifestProperty.Value.GetType() -or
            -not $ledgerProperty.Value.Equals($manifestProperty.Value)) {
        Throw-VerifierInfrastructure "Durable ledger and manifest disagreed on exact integral field '$Name'."
    }
    return $ledgerProperty.Value
}

function Assert-VerifierDurableIntegralProperty($Object, [string]$Name,
        [long]$Minimum = [long]::MinValue, [long]$Maximum = [long]::MaxValue) {
    if ($null -eq $Object -or
            -not (Test-VerifierStrictNonnegativeIntegerProperty $Object $Name)) {
        Throw-VerifierInfrastructure "Durable '$Name' was missing or was not an exact integral value."
    }
    $property = $Object.PSObject.Properties[$Name]
    try {
        if ([long]$property.Value -lt $Minimum -or [long]$property.Value -gt $Maximum) {
            Throw-VerifierInfrastructure "Durable '$Name' was outside its allowed integral range."
        }
    } catch {
        if (Test-VerifierInfrastructureError $_) { throw }
        Throw-VerifierInfrastructure "Durable '$Name' was outside its allowed integral range."
    }
    return $property.Value
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

function Test-VerifierStrictStringProperty($Object, [string]$Name) {
    if ($null -eq $Object -or [String]::IsNullOrWhiteSpace($Name) -or
            $null -eq $Object.PSObject.Properties[$Name]) { return $false }
    $value = $Object.PSObject.Properties[$Name].Value
    return ($null -ne $value -and $value.GetType() -eq [string])
}

function Test-VerifierRawJsonArrayProperty([string]$Json, [string]$Name) {
    if ([String]::IsNullOrWhiteSpace($Json) -or [String]::IsNullOrWhiteSpace($Name)) {
        return $false
    }
    $depth = 0
    $inString = $false
    $escaped = $false
    $matches = 0
    $arrayMatch = $false
    $nonArrayMatch = $false
    for ($index = 0; $index -lt $Json.Length; $index++) {
        $character = $Json[$index]
        if ($inString) {
            if ($escaped) { $escaped = $false }
            elseif ($character -eq '\') { $escaped = $true }
            elseif ($character -eq '"') { $inString = $false }
            continue
        }
        if ($character -eq '"') {
            if ($depth -ge 1) {
                $keyStart = $index + 1
                $keyEnd = $keyStart
                $keyEscaped = $false
                for (; $keyEnd -lt $Json.Length; $keyEnd++) {
                    $keyCharacter = $Json[$keyEnd]
                    if ($keyEscaped) { $keyEscaped = $false; continue }
                    if ($keyCharacter -eq '\') { $keyEscaped = $true; continue }
                    if ($keyCharacter -eq '"') { break }
                }
                if ($keyEnd -ge $Json.Length) { return $false }
                $key = $Json.Substring($keyStart, $keyEnd - $keyStart)
                $valueIndex = $keyEnd + 1
                while ($valueIndex -lt $Json.Length -and
                        [Char]::IsWhiteSpace($Json[$valueIndex])) { $valueIndex++ }
                if ($valueIndex -ge $Json.Length -or $Json[$valueIndex] -ne ':') {
                    $index = $keyEnd
                    continue
                }
                $valueIndex++
                while ($valueIndex -lt $Json.Length -and
                        [Char]::IsWhiteSpace($Json[$valueIndex])) { $valueIndex++ }
                if ($key -ceq $Name) {
                    $matches++
                    if ($valueIndex -lt $Json.Length -and $Json[$valueIndex] -eq '[') {
                        $arrayMatch = $true
                    } else {
                        $nonArrayMatch = $true
                    }
                }
                $index = $keyEnd
                continue
            }
            $inString = $true
            continue
        }
        if ($character -eq '{') { $depth++; continue }
        if ($character -eq '}') { $depth--; continue }
    }
    return ($matches -eq 1 -and $arrayMatch -and -not $nonArrayMatch)
}

function Test-VerifierIntegratedServerListenerOwnerSchema($Server, $Lease = $null) {
    # The canonical module boundary owns Test-VerifierListenerOwnerTuple and
    # all serialized server/lease association checks.
    return Test-VerifierSerializedListenerOwnerSchema $Lease $Server
}

function Test-VerifierIntegratedListenerOwnerSchema($Lease, $Server = $null) {
    # The canonical module boundary owns Test-VerifierListenerOwnerTuple and
    # all serialized lease/server association checks.
    return Test-VerifierSerializedListenerOwnerSchema $Lease $Server
}

function Test-VerifierIntegratedLeaseTerminal($Lease, $ClaimPath,
        $Server = $null, [string]$ExpectedRunId = '',
        [string]$ExpectedRepositoryIdentity = '',
        [string]$ExpectedWorktreeRoot = '') {
    $runtimeLeaseProperty = if ($null -eq $Lease) { $null } else {
        @($Lease.PSObject.Properties | Where-Object { $_.Name -ceq 'LeaseId' })
    }
    if ($null -ne $Lease -and @($runtimeLeaseProperty).Count -eq 1) {
        if (-not (Test-VerifierStrictStringValue $ClaimPath) -or
                [String]::IsNullOrWhiteSpace($ClaimPath) -or
                -not (Test-VerifierCanonicalWindowsPathValue $ClaimPath $Lease.Path)) {
            return $false
        }
        if ((-not [String]::IsNullOrWhiteSpace($ExpectedRunId) -and
                $Lease.RunId -cne $ExpectedRunId) -or
                (-not [String]::IsNullOrWhiteSpace($ExpectedRepositoryIdentity) -and
                $Lease.RepositoryIdentity -cne $ExpectedRepositoryIdentity) -or
                (-not [String]::IsNullOrWhiteSpace($ExpectedWorktreeRoot) -and
                -not (Test-VerifierCanonicalWindowsPathValue $Lease.WorktreeRoot $ExpectedWorktreeRoot))) {
            return $false
        }
        try {
            [void](Assert-VerifierDurableLeaseTerminal $Lease)
        } catch {
            throw
        }
        return $true
    }
    return Test-VerifierSerializedLeaseTerminal $Lease $ClaimPath $Server `
        $ExpectedRunId $ExpectedRepositoryIdentity $ExpectedWorktreeRoot
}

function Invoke-GateBIntegratedKernelLedgerCanary([string]$RepositoryRoot,
        [string]$PreviewScript, [string]$WebRoot) {
    # This is an actual integrated completion-consumer canary.  The process
    # identities are deterministic test records, while the released-listener
    # query remains the production Confirm-VerifierReleasedListener call so
    # the kernel branch proves real absence without ever treating PID 4 as a
    # terminable process.
    $readerContext = $null
    $readerRoot = Get-VerifierFullPath (Join-Path ([IO.Path]::GetTempPath()) `
        ('TroubleshootJS\verify\integrated-kernel-reader-' +
            [Guid]::NewGuid().ToString('N')))
    $oldRecordedProcessAbsent = $null
    $oldBrowserSnapshot = $null
    try {
        $readerContext = New-VerifierRunContext $RepositoryRoot '' $readerRoot
        $readerPort = 40194
        $readerLeaseId = 'gate-b-integrated-kernel-lease'
        $readerClaimName = Get-VerifierPortMutexName $null $readerPort
        $readerClaimPath = Get-VerifierFullPath (Join-Path $readerContext.PortLeaseRoot `
            ([string]$readerPort + '-' + $readerLeaseId + '.lease'))
        $claimOwnerProcess = Get-Process -Id $PID -ErrorAction Stop
        $claimOwnerStart = [long](Get-VerifierProcessStartTicks $claimOwnerProcess)
        $boundProcessId = 65402
        $boundProcessStart = 234502L
        $readerLease = [pscustomobject]([ordered]@{
            leaseId = $readerLeaseId; kind = 'preview'; port = $readerPort
            path = $readerClaimPath; runId = $readerContext.RunId
            repositoryIdentity = $readerContext.RepositoryIdentity
            worktreeRoot = $readerContext.WorktreeRoot
            status = 'released'; claimName = $readerClaimName
            claimState = 'released'; claimOwnerPid = [int]$PID
            claimOwnerStartTicks = $claimOwnerStart
            profile = ''; browserPath = ''; registered = $true
            boundProcessId = $boundProcessId
            boundProcessStartTicks = $boundProcessStart
            listenerProcessId = 4; listenerProcessStartTicks = $null
            listenerOwnerKind = 'kernel-transport'
            listenerOwnerProof = 'run-owned-preview-http-sys-v1'
            listenerOwnerEvidence = 'pid-4-system-http-sys'
            bindValidatedUtc = ''; releasedUtc = ''
            releaseState = 'complete'; releaseJournalState = 'complete'
            releaseBlocked = $false; releaseBlockReason = ''
            mutexReleased = $true; listenerInspectionSuccess = $true
            listenerInspectionKnown = $true; listenerHasListeners = $false
            listenerAbsent = $true; listenerInspectionUtc = ''
            processTerminationProven = $true; processAbsent = $true
            processProofRequired = $true; ClaimMutex = $null
            claim = [ordered]@{
                protocol = 'troubleshootjs-verifier-port-claim-v1'
                runId = $readerContext.RunId
                repositoryIdentity = $readerContext.RepositoryIdentity
                worktreeRoot = $readerContext.WorktreeRoot
                leaseId = $readerLeaseId; path = $readerClaimPath
                kind = 'preview'; port = $readerPort
                mutexName = $readerClaimName; ownerPid = [int]$PID
                ownerStartTicks = $claimOwnerStart
            }
        })
        $serverLogRoot = Get-VerifierFullPath (Join-Path $readerContext.RunRoot 'server')
        New-Item -ItemType Directory -Path $serverLogRoot -Force -ErrorAction Stop | Out-Null
        $stdoutLog = Get-VerifierFullPath (Join-Path $serverLogRoot 'stdout.log')
        $stderrLog = Get-VerifierFullPath (Join-Path $serverLogRoot 'stderr.log')
        Write-VerifierEvidenceText $stdoutLog 'gate-b kernel reader stdout' `
            'Gate B integrated kernel reader stdout'
        Write-VerifierEvidenceText $stderrLog '' `
            'Gate B integrated kernel reader stderr'
        $readerCommandLine = 'powershell.exe -File "' + $PreviewScript +
            '" -Port ' + [string]$readerPort + ' -VerifierRunId ' +
            $readerContext.RunId + ' -VerifierNonce ' + $readerContext.PreviewNonce
        $readerServer = [pscustomobject]([ordered]@{
            owner = 'run'; baseUrl = 'http://127.0.0.1:' + [string]$readerPort
            repositoryIdentity = $readerContext.RepositoryIdentity
            worktreeRoot = $readerContext.WorktreeRoot
            repositoryRoot = $RepositoryRoot; webRoot = $WebRoot
            identityProtocol = 'troubleshootjs-preview-identity-v1'
            identityVerified = $true; callerOwned = $false
            port = $readerPort; processId = $boundProcessId
            processStartTicks = $boundProcessStart
            processParentProcessId = 65403
            processParentProcessStartTicks = 234503L
            processCommandLine = $readerCommandLine; script = $PreviewScript
            runId = $readerContext.RunId; nonce = $readerContext.PreviewNonce
            leaseId = $readerLeaseId; leaseKind = 'preview'
            leaseClaimName = $readerClaimName; leaseClaimState = 'released'
            leaseReleaseState = 'complete'
            leaseReleaseJournalState = 'complete'
            leasePath = $readerClaimPath; leaseOwnerPid = [int]$PID
            leaseOwnerStartTicks = $claimOwnerStart
            leaseListenerAbsent = $true; leaseProcessProofRequired = $true
            leaseListenerOwnerKind = 'kernel-transport'
            leaseListenerOwnerProof = 'run-owned-preview-http-sys-v1'
            leaseListenerOwnerEvidence = 'pid-4-system-http-sys'
            state = 'cleaned'; stdoutLog = $stdoutLog; stderrLog = $stderrLog
            cleanupResult = 'complete'; error = ''
            processIdentityKnown = $true; ownershipUncertain = $false
            processTerminationProven = $true; processAbsent = $true
            listenerInspectionProven = $true; listenerAbsent = $true
        })
        $manifest = ConvertFrom-VerifierDurableJson (
            Get-Content -LiteralPath $readerContext.ManifestPath -Raw)
        $manifest.leases = @($readerLease)
        $manifest.browserSessions = @()
        $manifest.artifacts = @()
        $manifest.server = $readerServer
        $manifest.cleanup.state = 'complete'
        $manifest.cleanup.completedUtc = '2026-08-31T00:00:01.0000000Z'
        $manifest.cleanup.errors = @()
        $manifestText = $manifest | ConvertTo-Json -Depth 16
        Write-VerifierEvidenceText $readerContext.ManifestPath $manifestText `
            'Gate B integrated kernel reader manifest'
        $readerLedgerPath = Get-VerifierFullPath (Join-Path $readerContext.RunNamespaceRoot `
            'completed-kernel-transport.json')
        $ledger = [ordered]@{
            protocol = 'troubleshootjs-integrated-child-ledger-v1'
            state = 'completed'; forcedNegativeProof = $null
            runId = $readerContext.RunId
            repositoryIdentity = $readerContext.RepositoryIdentity
            worktreeRoot = $readerContext.WorktreeRoot
            parentNamespaceRoot = $readerContext.RunNamespaceRoot
            runRoot = $readerContext.RunRoot
            manifestPath = $readerContext.ManifestPath
            evidenceDirectory = $readerContext.EvidenceDirectory
            evidence = @(); leases = @($readerLease); profiles = @()
            server = $readerServer; cleanupState = 'complete'
            cleanupErrors = @(); error = ''; updatedUtc = Get-VerifierUtcText
        }
        $ledgerText = $ledger | ConvertTo-Json -Depth 16
        Write-VerifierEvidenceText $readerLedgerPath $ledgerText `
            'Gate B integrated kernel reader ledger'
        $baselineLedgerText = [IO.File]::ReadAllText($readerLedgerPath)
        $baselineManifestText = [IO.File]::ReadAllText($readerContext.ManifestPath)

        $oldRecordedProcessAbsent = (Get-Command Confirm-VerifierRecordedProcessAbsent `
            -CommandType Function -ErrorAction Stop).ScriptBlock
        $oldBrowserSnapshot = (Get-Command Get-VerifierBrowserProcessSnapshot `
            -CommandType Function -ErrorAction Stop).ScriptBlock
        $script:GateBKernelReaderProcessCalls = 0
        $script:GateBKernelReaderExpectedRunId = $readerContext.RunId
        $script:GateBKernelReaderExpectedPort = $readerPort
        Set-Item Function:\Confirm-VerifierRecordedProcessAbsent -Force -Value {
            param($Recorded, [string]$Role, [string]$ExpectedCommandLine,
                $Port, [string]$Script, [string]$RunId, [string]$Nonce,
                [string]$Profile)
            $recordedPid = $Recorded.PSObject.Properties['ProcessId']
            $recordedStart = $Recorded.PSObject.Properties['ProcessStartTicks']
            if ($null -eq $recordedPid -or $null -eq $recordedStart -or
                    [long]$recordedPid.Value -ne 65402L -or
                    [long]$recordedStart.Value -ne 234502L) {
                throw 'Gate B kernel reader process-proof hook received an unexpected identity.'
            }
            $script:GateBKernelReaderProcessCalls++
            return [pscustomobject]@{
                QueryProven = $true; Absent = $true; Replaced = $false
                Current = $null
            }
        }
        Set-Item Function:\Get-VerifierBrowserProcessSnapshot -Force -Value {
            param([string]$BrowserPath = '', [string]$Profile = '',
                [string]$RunId = '', [string]$RepositoryIdentity = '', $Port = 0,
                [string]$Script = '', [string]$Nonce = '')
            if ($RunId -cne $script:GateBKernelReaderExpectedRunId -or
                    [int]$Port -ne [int]$script:GateBKernelReaderExpectedPort) {
                throw 'Gate B kernel reader process-snapshot hook received an unexpected identity.'
            }
            return @()
        }
        try {
            $accepted = Read-VerifierIntegratedChildLedger $readerLedgerPath 2
            if ([string]$accepted.state -cne 'completed' -or
                    [int]$script:GateBKernelReaderProcessCalls -ne 2) {
                throw 'completed paired kernel ledger/manifest reader did not consume both exact process proofs.'
            }
            foreach ($variantDefinition in @(
                    [pscustomobject]@{ Name = 'missing listener owner kind'; Action = 'remove-kind' }
                    [pscustomobject]@{ Name = 'PID 4 with non-null start'; Action = 'kernel-start' }
                    [pscustomobject]@{ Name = 'arbitrary listener owner proof'; Action = 'arbitrary-proof' }
                    [pscustomobject]@{ Name = 'wrong listener owner record'; Action = 'wrong-owner' }
                )) {
                $variantLedger = ConvertFrom-VerifierDurableJson $baselineLedgerText
                $variantManifest = ConvertFrom-VerifierDurableJson $baselineManifestText
                $variantLease = $variantLedger.leases[0]
                $variantManifestLease = $variantManifest.leases[0]
                switch ($variantDefinition.Action) {
                    'remove-kind' {
                        [void]$variantLease.PSObject.Properties.Remove('listenerOwnerKind')
                        [void]$variantManifestLease.PSObject.Properties.Remove('listenerOwnerKind')
                    }
                    'kernel-start' {
                        $variantLease.listenerProcessStartTicks = 234502L
                        $variantManifestLease.listenerProcessStartTicks = 234502L
                    }
                    'arbitrary-proof' {
                        $variantLease.listenerOwnerProof = 'arbitrary-record'
                        $variantManifestLease.listenerOwnerProof = 'arbitrary-record'
                    }
                    'wrong-owner' {
                        $variantLease.listenerProcessId = 5
                        $variantManifestLease.listenerProcessId = 5
                    }
                }
                $variantLedgerText = $variantLedger | ConvertTo-Json -Depth 16
                $variantManifestText = $variantManifest | ConvertTo-Json -Depth 16
                Write-VerifierEvidenceText $readerLedgerPath $variantLedgerText `
                    ('Gate B integrated ' + $variantDefinition.Name + ' ledger')
                Write-VerifierEvidenceText $readerContext.ManifestPath $variantManifestText `
                    ('Gate B integrated ' + $variantDefinition.Name + ' manifest')
                $callsBefore = [int]$script:GateBKernelReaderProcessCalls
                $rejected = $false
                try {
                    [void](Read-VerifierIntegratedChildLedger $readerLedgerPath 2)
                } catch { $rejected = Test-VerifierInfrastructureError $_ }
                $ledgerAfter = [IO.File]::ReadAllText($readerLedgerPath)
                $manifestAfter = [IO.File]::ReadAllText($readerContext.ManifestPath)
                if (-not $rejected -or
                        $ledgerAfter -cne $variantLedgerText -or
                        $manifestAfter -cne $variantManifestText -or
                        [int]$script:GateBKernelReaderProcessCalls -ne $callsBefore) {
                    throw "integrated $($variantDefinition.Name) was accepted, mutated, or queried before schema rejection."
                }
                Write-VerifierEvidenceText $readerLedgerPath $baselineLedgerText `
                    'Gate B integrated kernel reader ledger restore'
                Write-VerifierEvidenceText $readerContext.ManifestPath $baselineManifestText `
                    'Gate B integrated kernel reader manifest restore'
            }
        } finally {
            Set-Item Function:\Confirm-VerifierRecordedProcessAbsent -Force `
                -Value $oldRecordedProcessAbsent
            Set-Item Function:\Get-VerifierBrowserProcessSnapshot -Force `
                -Value $oldBrowserSnapshot
            Remove-Variable -Name GateBKernelReaderProcessCalls -Scope Script `
                -Force -ErrorAction SilentlyContinue
            Remove-Variable -Name GateBKernelReaderExpectedRunId -Scope Script `
                -Force -ErrorAction SilentlyContinue
            Remove-Variable -Name GateBKernelReaderExpectedPort -Scope Script `
                -Force -ErrorAction SilentlyContinue
        }
        Write-Host 'PASS:completed paired kernel ledger/manifest listener absence used ExpectedProcessId=0/ExpectedStartTicks=0, rejected missing/malformed/arbitrary owner records before process queries, and performed no PID 4 termination'
    } finally {
        if ($null -ne $oldRecordedProcessAbsent) {
            Set-Item Function:\Confirm-VerifierRecordedProcessAbsent -Force `
                -Value $oldRecordedProcessAbsent
        }
        if ($null -ne $oldBrowserSnapshot) {
            Set-Item Function:\Get-VerifierBrowserProcessSnapshot -Force `
                -Value $oldBrowserSnapshot
        }
        Remove-Variable -Name GateBKernelReaderProcessCalls -Scope Script `
            -Force -ErrorAction SilentlyContinue
        Remove-Variable -Name GateBKernelReaderExpectedRunId -Scope Script `
            -Force -ErrorAction SilentlyContinue
        Remove-Variable -Name GateBKernelReaderExpectedPort -Scope Script `
            -Force -ErrorAction SilentlyContinue
        if (Test-Path -LiteralPath $readerRoot) {
            Remove-VerifierOwnedTree (Get-VerifierFullPath (Join-Path ([IO.Path]::GetTempPath()) `
                'TroubleshootJS\verify')) $readerRoot
        }
    }
}

function Assert-VerifierIntegratedForcedNegativeProof($Proof,
        [int]$ExpectedExit, [string]$ExpectedMarker = '') {
    if ($ExpectedExit -ne 1) { return }
    if ($null -eq $Proof -or $Proof -is [System.Array]) {
        Throw-VerifierInfrastructure 'Integrated expected-exit-1 child omitted its forced-negative proof record.'
    }
    foreach ($stringProperty in @('protocol', 'expectedMarker', 'expectedRoute',
            'runId', 'routeId', 'observedMarker', 'markerExpectedMarker',
            'markerRunId', 'markerRouteId', 'diagnosticExpectedMarker',
            'diagnosticRunId', 'diagnosticRouteId', 'anchoredJavaDiagnostic',
            'diagnosticBaselineHead', 'nonce', 'requestId', 'executionDigest')) {
        $property = $Proof.PSObject.Properties[$stringProperty]
        if ($null -eq $property -or
                -not (Test-VerifierStrictStringValue $property.Value)) {
            Throw-VerifierInfrastructure "Integrated expected-exit-1 child proof omitted or malformed '$stringProperty'."
        }
    }
    if ((Get-VerifierLedgerProperty $Proof 'protocol') -ne
            'troubleshootjs-forced-negative-proof-v1') {
        Throw-VerifierInfrastructure 'Integrated expected-exit-1 child carried an invalid forced-negative proof protocol.'
    }
    $proofExpectedMarker = Get-VerifierLedgerProperty $Proof 'expectedMarker'
    if ([String]::IsNullOrWhiteSpace($ExpectedMarker)) {
        $ExpectedMarker = $proofExpectedMarker
    }
    if ($ExpectedMarker -notin @('FAIL:task43-forced-negative-canary',
            'FAIL:task43p-forced-negative-canary') -or
            $proofExpectedMarker -ne $ExpectedMarker) {
        Throw-VerifierInfrastructure 'Integrated expected-exit-1 child carried an unsupported or mismatched expected marker.'
    }
    $expectedRoute = Get-Task43ForcedNegativeExpectedRoute $ExpectedMarker
    if ((Get-VerifierLedgerProperty $Proof 'expectedRoute') -ne $expectedRoute) {
        Throw-VerifierInfrastructure 'Integrated expected-exit-1 child carried the wrong forced-negative route identity.'
    }
    foreach ($requiredTrue in @('invocation', 'markerObserved',
            'anchoredDiagnosticProven', 'routePassedAfterCleanup',
            'finalCleanupProven', 'routePassed', 'cleanupProven')) {
        if (-not (Test-VerifierStrictBooleanProperty $Proof $requiredTrue $true)) {
            Throw-VerifierInfrastructure "Integrated expected-exit-1 child did not prove forced-negative $requiredTrue."
        }
    }
    if (-not (Test-VerifierStrictBooleanProperty $Proof 'invalidated' $false)) {
        Throw-VerifierInfrastructure 'Integrated expected-exit-1 child carried an invalidated forced-negative proof.'
    }
    $runId = Get-VerifierLedgerProperty $Proof 'runId'
    $routeId = Get-VerifierLedgerProperty $Proof 'routeId'
    if ([String]::IsNullOrWhiteSpace($runId) -or
            [String]::IsNullOrWhiteSpace($routeId) -or
            (Get-VerifierLedgerProperty $Proof 'observedMarker') -ne $ExpectedMarker -or
            (Get-VerifierLedgerProperty $Proof 'markerExpectedMarker') -ne $ExpectedMarker -or
            (Get-VerifierLedgerProperty $Proof 'markerRunId') -ne $runId -or
            (Get-VerifierLedgerProperty $Proof 'markerRouteId') -ne $routeId -or
            (Get-VerifierLedgerProperty $Proof 'diagnosticExpectedMarker') -ne $ExpectedMarker -or
            (Get-VerifierLedgerProperty $Proof 'diagnosticRunId') -ne $runId -or
            (Get-VerifierLedgerProperty $Proof 'diagnosticRouteId') -ne $routeId) {
        Throw-VerifierInfrastructure 'Integrated expected-exit-1 child forced-negative marker/diagnostic run or route identity was incomplete.'
    }
    $proofNonce = Get-VerifierLedgerProperty $Proof 'nonce'
    $proofRequestId = Get-VerifierLedgerProperty $Proof 'requestId'
    $proofExecutionDigest = Get-VerifierLedgerProperty $Proof 'executionDigest'
    if (Test-Task43PForcedNegativeMarker $ExpectedMarker) {
        if ([String]::IsNullOrWhiteSpace($proofNonce) -or
                [String]::IsNullOrWhiteSpace($proofRequestId) -or
                $proofExecutionDigest -notmatch '^[0-9a-f]{64}$') {
            Throw-VerifierInfrastructure 'Integrated expected-exit-1 Task43P child omitted its nonce/request/execution identity.'
        }
    } elseif (-not [String]::IsNullOrWhiteSpace($proofNonce) -or
            -not [String]::IsNullOrWhiteSpace($proofRequestId) -or
            -not [String]::IsNullOrWhiteSpace($proofExecutionDigest)) {
        Throw-VerifierInfrastructure 'Integrated legacy forced-negative child carried unexpected Task43P identity fields.'
    }
    $diagnostic = Get-VerifierLedgerProperty $Proof 'anchoredJavaDiagnostic'
    if (-not (Test-Task43ForcedFailureDiagnosticText $diagnostic $ExpectedMarker `
            $proofNonce $routeId $proofRequestId $proofExecutionDigest)) {
        Throw-VerifierInfrastructure 'Integrated expected-exit-1 child omitted the exact anchored Java forced-negative diagnostic.'
    }
    $baselineHead = Get-VerifierLedgerProperty $Proof 'diagnosticBaselineHead'
    if ((Test-Task43PForcedNegativeMarker $ExpectedMarker) -and
            $baselineHead -ne $script:Task43PCandidateSha) {
        Throw-VerifierInfrastructure 'Integrated expected-exit-1 Task43P child did not prove the frozen candidate identity.'
    }
    if ($ExpectedMarker -eq 'FAIL:task43-forced-negative-canary' -and
            -not [String]::IsNullOrWhiteSpace($baselineHead)) {
        Throw-VerifierInfrastructure 'Integrated legacy forced-negative child carried an unexpected Task43P baseline field.'
    }
}

function Assert-VerifierIntegratedLedgerString($Object, [string]$Name,
        [string]$Label, [switch]$Timestamp, [switch]$AllowEmpty) {
    $property = if ($null -eq $Object) { $null } else {
        $Object.PSObject.Properties[$Name]
    }
    if ($null -eq $property) {
        Throw-VerifierInfrastructure "$Label omitted or malformed exact string '$Name'."
    }
    if ($Timestamp) {
        return ConvertTo-VerifierStrictTimestampText $property.Value ([bool]$AllowEmpty) `
            ("$Label $Name") -AllowJsonDateTime
    }
    if (-not (Test-VerifierStrictStringValue $property.Value)) {
        Throw-VerifierInfrastructure "$Label omitted or malformed exact string '$Name'."
    }
    return $property.Value
}

function Assert-VerifierIntegratedLedgerIntegral($Object, [string]$Name,
        [long]$Minimum, [long]$Maximum, [string]$Label) {
    $property = if ($null -eq $Object) { $null } else {
        $Object.PSObject.Properties[$Name]
    }
    if ($null -eq $property -or
            -not (Test-VerifierStrictIntegralValue $property.Value $Minimum $Maximum)) {
        Throw-VerifierInfrastructure "$Label omitted or malformed exact integral '$Name'."
    }
    return $property.Value
}

function Assert-VerifierIntegratedLedgerBoolean($Object, [string]$Name,
        [string]$Label, [switch]$AllowNull) {
    $property = if ($null -eq $Object) { $null } else {
        $Object.PSObject.Properties[$Name]
    }
    if ($null -eq $property) {
        Throw-VerifierInfrastructure "$Label omitted required Boolean '$Name'."
    }
    if ($null -eq $property.Value) {
        if ($AllowNull) { return }
        Throw-VerifierInfrastructure "$Label carried null Boolean '$Name'."
    }
    if (-not (Test-VerifierStrictBooleanValue $property.Value)) {
        Throw-VerifierInfrastructure "$Label carried malformed Boolean '$Name'."
    }
}

function Assert-VerifierIntegratedDurableLeaseSchema($Lease, [string]$Label) {
    Assert-VerifierSerializedLeaseRecord $Lease $Label
}

function Read-VerifierIntegratedClaimRecord($Claim, $Lease, [string]$ClaimPath,
        [string]$ExpectedRunId, [string]$ExpectedRepositoryIdentity,
        [string]$ExpectedWorktreeRoot,
        [string]$Label = 'integrated claim') {
    # This is the only integrated timeout/ledger claim reader.  It validates
    # the raw JSON object and its paired durable lease before any cast,
    # comparison, path use, absence decision, or cleanup decision is allowed.
    Assert-VerifierIntegratedDurableLeaseSchema $Lease ($Label + ' lease')
    if ($null -eq $Claim -or $Claim -is [array] -or
            $Claim -isnot [pscustomobject]) {
        Throw-VerifierInfrastructure "$Label was not an exact durable claim object."
    }
    if (-not (Test-VerifierStrictStringValue $ClaimPath) -or
            [String]::IsNullOrWhiteSpace($ClaimPath) -or
            -not (Test-VerifierStrictStringValue $ExpectedRunId) -or
            [String]::IsNullOrWhiteSpace($ExpectedRunId) -or
            -not (Test-VerifierStrictStringValue $ExpectedRepositoryIdentity) -or
            [String]::IsNullOrWhiteSpace($ExpectedRepositoryIdentity) -or
            -not (Test-VerifierStrictStringValue $ExpectedWorktreeRoot) -or
            [String]::IsNullOrWhiteSpace($ExpectedWorktreeRoot)) {
        Throw-VerifierInfrastructure "$Label was given malformed association inputs."
    }
    foreach ($name in @('protocol', 'runId', 'repositoryIdentity',
            'worktreeRoot', 'leaseId', 'path', 'kind', 'mutexName')) {
        [void](Assert-VerifierIntegratedLedgerString $Claim $name $Label)
    }
    foreach ($number in @(
            [pscustomobject]@{ Name = 'port'; Minimum = 1L; Maximum = 65535L }
            [pscustomobject]@{ Name = 'ownerPid'; Minimum = 1L; Maximum = [int]::MaxValue }
            [pscustomobject]@{ Name = 'ownerStartTicks'; Minimum = 1L; Maximum = [long]::MaxValue }
        )) {
        [void](Assert-VerifierIntegratedLedgerIntegral $Claim $number.Name `
            $number.Minimum $number.Maximum $Label)
    }

    $claimProtocol = $Claim.PSObject.Properties['protocol'].Value
    $claimRunId = $Claim.PSObject.Properties['runId'].Value
    $claimRepositoryIdentity = $Claim.PSObject.Properties['repositoryIdentity'].Value
    $claimWorktreeRoot = $Claim.PSObject.Properties['worktreeRoot'].Value
    $claimLeaseId = $Claim.PSObject.Properties['leaseId'].Value
    $claimPath = $Claim.PSObject.Properties['path'].Value
    $claimKind = $Claim.PSObject.Properties['kind'].Value
    $claimMutexName = $Claim.PSObject.Properties['mutexName'].Value
    $leaseRunId = $Lease.PSObject.Properties['runId'].Value
    $leaseRepositoryIdentity = $Lease.PSObject.Properties['repositoryIdentity'].Value
    $leaseWorktreeRoot = $Lease.PSObject.Properties['worktreeRoot'].Value
    $leaseId = $Lease.PSObject.Properties['leaseId'].Value
    $leasePath = $Lease.PSObject.Properties['path'].Value
    $leaseKind = $Lease.PSObject.Properties['kind'].Value
    $leaseClaimName = $Lease.PSObject.Properties['claimName'].Value
    $leasePort = $Lease.PSObject.Properties['port'].Value
    $leaseOwnerPid = $Lease.PSObject.Properties['claimOwnerPid'].Value
    $leaseOwnerStart = $Lease.PSObject.Properties['claimOwnerStartTicks'].Value
    $claimPort = $Claim.PSObject.Properties['port'].Value
    $claimOwnerPid = $Claim.PSObject.Properties['ownerPid'].Value
    $claimOwnerStart = $Claim.PSObject.Properties['ownerStartTicks'].Value

    if ($claimProtocol -cne 'troubleshootjs-verifier-port-claim-v1' -or
            -not (Test-VerifierSupportedPortLeaseKind $claimKind) -or
            $claimKind -cne $leaseKind -or
            $claimRunId -cne $leaseRunId -or $claimRunId -cne $ExpectedRunId -or
            $claimRepositoryIdentity -cne $leaseRepositoryIdentity -or
            $claimRepositoryIdentity -cne $ExpectedRepositoryIdentity -or
            $claimLeaseId -cne $leaseId -or
            $claimMutexName -cne $leaseClaimName -or
            -not $claimPort.Equals($leasePort) -or
            -not $claimOwnerPid.Equals($leaseOwnerPid) -or
            -not $claimOwnerStart.Equals($leaseOwnerStart)) {
        Throw-VerifierInfrastructure "$Label was foreign, stale, or mismatched with its durable lease."
    }

    $claimCanonicalPath = Get-VerifierCanonicalWindowsPath $claimPath
    $leaseCanonicalPath = Get-VerifierCanonicalWindowsPath $leasePath
    $claimRoot = Get-VerifierCanonicalWindowsPath $claimWorktreeRoot
    $leaseRoot = Get-VerifierCanonicalWindowsPath $leaseWorktreeRoot
    $expectedClaimPath = Get-VerifierCanonicalWindowsPath $ClaimPath
    $expectedRoot = Get-VerifierCanonicalWindowsPath $ExpectedWorktreeRoot
    if ([String]::IsNullOrWhiteSpace($claimCanonicalPath) -or
            [String]::IsNullOrWhiteSpace($leaseCanonicalPath) -or
            [String]::IsNullOrWhiteSpace($claimRoot) -or
            [String]::IsNullOrWhiteSpace($leaseRoot) -or
            [String]::IsNullOrWhiteSpace($expectedClaimPath) -or
            [String]::IsNullOrWhiteSpace($expectedRoot) -or
            -not (Test-VerifierCanonicalWindowsPathValue $claimCanonicalPath $expectedClaimPath) -or
            -not (Test-VerifierCanonicalWindowsPathValue $claimCanonicalPath $leaseCanonicalPath) -or
            -not (Test-VerifierCanonicalWindowsPathValue $claimRoot $leaseRoot) -or
            -not (Test-VerifierCanonicalWindowsPathValue $claimRoot $expectedRoot)) {
        Throw-VerifierInfrastructure "$Label path or worktree association was not exact."
    }

    # All raw scalar and association checks precede these typed values.  The
    # mutex name is derived only from the already validated durable port.
    $typedPort = [int]$claimPort
    if ($claimMutexName -cne (Get-VerifierPortMutexName $null $typedPort)) {
        Throw-VerifierInfrastructure "$Label did not carry the canonical mutex name for its leased port."
    }
    return [pscustomobject]@{
        Protocol = $claimProtocol
        RunId = $claimRunId
        RepositoryIdentity = $claimRepositoryIdentity
        WorktreeRoot = $claimWorktreeRoot
        LeaseId = $claimLeaseId
        Path = $claimCanonicalPath
        Kind = $claimKind
        MutexName = $claimMutexName
        Port = $typedPort
        OwnerPid = [int]$claimOwnerPid
        OwnerStartTicks = [long]$claimOwnerStart
    }
}

function Assert-VerifierIntegratedDurableProfileSchema($Profile,
        [string]$Label) {
    Assert-VerifierSerializedBrowserSessionRecord $Profile $Label
}

function Assert-VerifierIntegratedDurableServerSchema($Server,
        [string]$Label) {
    Assert-VerifierSerializedServerRecord $Server $Label
}

function Assert-VerifierIntegratedLedgerCopySchema($Ledger, [string]$LedgerJson) {
    if ($null -eq $Ledger -or $Ledger -is [array] -or
            $Ledger -isnot [pscustomobject]) {
        Throw-VerifierInfrastructure 'Integrated child ledger was not an exact durable object.'
    }
    foreach ($name in @('protocol', 'state', 'runId', 'repositoryIdentity',
            'worktreeRoot', 'parentNamespaceRoot', 'runRoot', 'manifestPath',
            'evidenceDirectory', 'cleanupState', 'error')) {
        [void](Assert-VerifierIntegratedLedgerString $Ledger $name 'integrated child ledger')
    }
    [void](Assert-VerifierIntegratedLedgerString $Ledger 'updatedUtc' `
        'integrated child ledger' -Timestamp)
    $proofProperty = $Ledger.PSObject.Properties['forcedNegativeProof']
    if ($null -eq $proofProperty) {
        Throw-VerifierInfrastructure 'Integrated child ledger omitted its explicit forced-negative proof state.'
    }
    if ($null -ne $proofProperty.Value) {
        $proof = $proofProperty.Value
        if ($proof -is [array] -or $proof -isnot [pscustomobject]) {
            Throw-VerifierInfrastructure 'Integrated child ledger forced-negative proof was not an exact object or explicit null.'
        }
        foreach ($name in @('protocol', 'expectedMarker', 'expectedRoute', 'runId',
                'routeId', 'observedMarker', 'markerExpectedMarker', 'markerRunId',
                'markerRouteId', 'anchoredJavaDiagnostic', 'diagnosticExpectedMarker',
                'diagnosticRunId', 'diagnosticRouteId', 'diagnosticBaselineHead',
                'nonce', 'requestId', 'executionDigest')) {
            [void](Assert-VerifierIntegratedLedgerString $proof $name `
                'integrated child forced-negative proof')
        }
        foreach ($name in @('invocation', 'markerObserved', 'anchoredDiagnosticProven',
                'routePassedAfterCleanup', 'finalCleanupProven', 'routePassed',
                'cleanupProven', 'invalidated')) {
            Assert-VerifierIntegratedLedgerBoolean $proof $name `
                'integrated child forced-negative proof'
        }
    }
    $ledgerLeases = Get-VerifierDurableArrayProperty $Ledger 'leases' `
        'integrated child ledger leases' $LedgerJson
    foreach ($lease in @($ledgerLeases)) {
        Assert-VerifierIntegratedDurableLeaseSchema $lease 'integrated child ledger lease'
    }
    $ledgerProfiles = Get-VerifierDurableArrayProperty $Ledger 'profiles' `
        'integrated child ledger profiles' $LedgerJson
    foreach ($profile in @($ledgerProfiles)) {
        Assert-VerifierIntegratedDurableProfileSchema $profile 'integrated child ledger profile'
    }
    $arrayProperty = $Ledger.PSObject.Properties['evidence']
    if ($null -eq $arrayProperty -or $null -eq $arrayProperty.Value) {
        Throw-VerifierInfrastructure "integrated child ledger evidence omitted its required array 'evidence'."
    }
    $items = $arrayProperty.Value
    if (-not ($items -is [array])) {
        if (-not (Test-VerifierRawJsonArrayProperty $LedgerJson 'evidence')) {
            Throw-VerifierInfrastructure 'integrated child ledger evidence carried a scalar evidence value where the durable JSON did not prove an array.'
        }
        $items = ,@($items)
    }
    foreach ($item in @($items)) {
        if (-not (Test-VerifierStrictStringValue $item)) {
            Throw-VerifierInfrastructure 'integrated child ledger evidence carried a non-string retained artifact entry.'
        }
    }
    $serverProperty = $Ledger.PSObject.Properties['server']
    if ($null -eq $serverProperty) {
        Throw-VerifierInfrastructure 'Integrated child ledger omitted its explicit server state.'
    }
    if ($null -ne $serverProperty.Value) {
        Assert-VerifierIntegratedDurableServerSchema $serverProperty.Value 'integrated child ledger server'
    }
}

function Assert-VerifierIntegratedManifestCopySchema($Manifest, [string]$ManifestJson) {
    if ($null -eq $Manifest -or $Manifest -is [array] -or
            $Manifest -isnot [pscustomobject]) {
        Throw-VerifierInfrastructure 'Integrated child manifest was not an exact durable object.'
    }
    foreach ($name in @('protocol', 'runId', 'repositoryIdentity',
            'worktreeRoot', 'runRoot', 'evidenceDirectory', 'manifestPath',
            'evidenceNamespaceRoot', 'runNamespaceRoot',
            'baseUrl', 'previewNonce')) {
        [void](Assert-VerifierIntegratedLedgerString $Manifest $name 'integrated child manifest')
    }
    [void](Assert-VerifierIntegratedLedgerString $Manifest 'createdUtc' `
        'integrated child manifest' -Timestamp)
    $manifestPreviewNonce = Assert-VerifierIntegratedLedgerString $Manifest `
        'previewNonce' 'integrated child manifest'
    if ([String]::IsNullOrWhiteSpace($manifestPreviewNonce)) {
        Throw-VerifierInfrastructure 'Integrated child manifest omitted its non-empty previewNonce.'
    }
    $manifestLeases = Get-VerifierDurableArrayProperty $Manifest 'leases' `
        'integrated child manifest leases' $ManifestJson
    foreach ($lease in @($manifestLeases)) {
        Assert-VerifierIntegratedDurableLeaseSchema $lease 'integrated child manifest lease'
    }
    $manifestProfiles = Get-VerifierDurableArrayProperty $Manifest 'browserSessions' `
        'integrated child manifest browser sessions' $ManifestJson
    foreach ($profile in @($manifestProfiles)) {
        Assert-VerifierIntegratedDurableProfileSchema $profile 'integrated child manifest browser session'
    }
    $arrayProperty = $Manifest.PSObject.Properties['artifacts']
    if ($null -eq $arrayProperty -or $null -eq $arrayProperty.Value) {
        Throw-VerifierInfrastructure "integrated child manifest artifacts omitted its required array 'artifacts'."
    }
    $items = $arrayProperty.Value
    if (-not ($items -is [array])) {
        if (-not (Test-VerifierRawJsonArrayProperty $ManifestJson 'artifacts')) {
            Throw-VerifierInfrastructure 'integrated child manifest artifacts carried a scalar artifacts value where the durable JSON did not prove an array.'
        }
        $items = ,@($items)
    }
    foreach ($item in @($items)) {
        if (-not (Test-VerifierStrictStringValue $item)) {
            Throw-VerifierInfrastructure 'integrated child manifest artifacts carried a non-string retained artifact entry.'
        }
    }
    $cleanupProperty = $Manifest.PSObject.Properties['cleanup']
    if ($null -eq $cleanupProperty -or $null -eq $cleanupProperty.Value -or
            $cleanupProperty.Value -is [array] -or
            $cleanupProperty.Value -isnot [pscustomobject]) {
        Throw-VerifierInfrastructure 'Integrated child manifest omitted an exact cleanup object.'
    }
    $cleanup = $cleanupProperty.Value
    $cleanupState = Assert-VerifierIntegratedLedgerString $cleanup 'state' `
        'integrated child manifest cleanup'
    if ($cleanupState -cnotin @('pending', 'complete', 'infrastructure-failure')) {
        Throw-VerifierInfrastructure 'Integrated child manifest cleanup carried an unknown finite cleanup state.'
    }
    $cleanupCompletedUtc = Assert-VerifierIntegratedLedgerString $cleanup 'completedUtc' `
        'integrated child manifest cleanup' -Timestamp `
        -AllowEmpty:($cleanupState -ne 'complete')
    if ($cleanupState -ceq 'pending' -and $cleanupCompletedUtc -cne '') {
        Throw-VerifierInfrastructure 'Integrated child manifest pending cleanup carried a completion timestamp.'
    }
    $cleanupErrors = Get-VerifierDurableArrayProperty $cleanup 'errors' `
        'integrated child manifest cleanup' $ManifestJson
    foreach ($errorItem in @($cleanupErrors)) {
        if (-not (Test-VerifierStrictStringValue $errorItem)) {
            Throw-VerifierInfrastructure 'Integrated child manifest cleanup errors carried a non-string entry.'
        }
    }
    $serverProperty = $Manifest.PSObject.Properties['server']
    if ($null -eq $serverProperty) {
        Throw-VerifierInfrastructure 'Integrated child manifest omitted its explicit server state.'
    }
    if ($null -ne $serverProperty.Value) {
        Assert-VerifierIntegratedDurableServerSchema $serverProperty.Value 'integrated child manifest server'
    }
}

function Assert-VerifierIntegratedDurableReaderSchema($Ledger, $Manifest,
        [string]$LedgerJson, [string]$ManifestJson) {
    Assert-VerifierIntegratedLedgerCopySchema $Ledger $LedgerJson
    Assert-VerifierIntegratedManifestCopySchema $Manifest $ManifestJson
    $serverProperty = $Ledger.PSObject.Properties['server']
    $manifestServerProperty = $Manifest.PSObject.Properties['server']
    if ($null -eq $serverProperty -or $null -eq $manifestServerProperty) {
        Throw-VerifierInfrastructure 'Integrated child durable copies omitted their explicit server state.'
    }
    if (($null -eq $serverProperty.Value) -xor ($null -eq $manifestServerProperty.Value)) {
        Throw-VerifierInfrastructure 'Integrated child durable copies disagreed on explicit server absence.'
    }
    if ($null -ne $serverProperty.Value) {
        Assert-VerifierIntegratedDurableServerSchema $serverProperty.Value 'integrated child ledger server'
        Assert-VerifierIntegratedDurableServerSchema $manifestServerProperty.Value 'integrated child manifest server'
    }
}

function Read-VerifierIntegratedChildLedger([string]$LedgerPath, [int]$ExpectedExit,
        [switch]$AllowIncomplete, [string]$ExpectedForcedMarker = '') {
    $expectedWorktree = if ($null -ne $script:VerifierContext) {
        [string]$script:VerifierContext.WorktreeRoot
    } else { Get-VerifierFullPath (Join-Path $PSScriptRoot '..') }
    $verifyRoot = Get-VerifierFullPath (Join-Path ([IO.Path]::GetTempPath()) `
        'TroubleshootJS\verify')
    $canonicalLedger = Get-VerifierLedgerPath $LedgerPath 'ledger' $verifyRoot
    if (-not (Test-Path -LiteralPath $canonicalLedger -PathType Leaf)) {
        Throw-VerifierInfrastructure "Integrated child ledger was not produced: $canonicalLedger"
    }
    try {
        $ledgerJson = Get-Content -LiteralPath $canonicalLedger -Raw
        $ledger = ConvertFrom-VerifierDurableJson $ledgerJson
    } catch {
        Throw-VerifierInfrastructure ('Integrated child ledger was not valid JSON: ' +
            (Get-VerifierErrorMessage $_))
    }
    # The child ledger is untrusted input.  Establish the complete typed
    # schema immediately after parsing, before lifecycle decisions, casts,
    # path resolution, or filesystem queries can consume any field.
    Assert-VerifierIntegratedLedgerCopySchema $ledger $ledgerJson
    $state = [string](Get-VerifierLedgerProperty $ledger 'state' '')
    if ([string](Get-VerifierLedgerProperty $ledger 'protocol' '') -cne 'troubleshootjs-integrated-child-ledger-v1' -or
            $state -cnotin @('started', 'resource-started', 'incomplete', 'cleanup-failed', 'completed')) {
        Throw-VerifierInfrastructure 'Integrated child ledger had an invalid protocol or lifecycle state.'
    }
    $incompleteState = ($state -cne 'completed')
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
    try {
        $manifestJson = Get-Content -LiteralPath $manifestPath -Raw
        $manifest = ConvertFrom-VerifierDurableJson $manifestJson
    } catch {
        Throw-VerifierInfrastructure ('Integrated child manifest was not valid JSON: ' +
            (Get-VerifierErrorMessage $_))
    }
    # Apply the same boundary to the second durable copy before any manifest
    # field is projected into a path, lifecycle comparison, or proof decision.
    Assert-VerifierIntegratedManifestCopySchema $manifest $manifestJson
    # Prove every ownership/process/lifecycle scalar and collection in both
    # durable copies before any legacy projection or comparison below.  The
    # reader never uses a default to manufacture a proof field.
    Assert-VerifierIntegratedDurableReaderSchema $ledger $manifest `
        $ledgerJson $manifestJson
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

    $manifestCleanup = Get-VerifierRequiredLedgerProperty $manifest 'cleanup' `
        'integrated child manifest cleanup'
    if ($null -eq $manifestCleanup -or $manifestCleanup -is [array] -or
            $manifestCleanup -isnot [pscustomobject]) {
        Throw-VerifierInfrastructure 'Integrated child manifest cleanup was not an exact object.'
    }
    $ledgerCleanupState = Get-VerifierRequiredLedgerProperty $ledger 'cleanupState' `
        'integrated child ledger cleanup'
    if ($null -eq $ledgerCleanupState -or
            $ledgerCleanupState.GetType() -ne [string] -or
            $ledgerCleanupState -cnotin @('pending', 'complete',
                'infrastructure-failure')) {
        Throw-VerifierInfrastructure 'Integrated child ledger cleanupState was missing or was not a recognized finite exact string.'
    }
    $manifestCleanupState = Get-VerifierRequiredLedgerProperty $manifestCleanup 'state' `
        'integrated child manifest cleanup'
    if ($null -eq $manifestCleanupState -or
            $manifestCleanupState.GetType() -ne [string] -or
            $manifestCleanupState -cne $ledgerCleanupState) {
        Throw-VerifierInfrastructure 'Integrated child ledger and manifest cleanup states differed or were not exact strings.'
    }
    $cleanupArrays = Assert-VerifierDurableArrayPair $ledger $manifestCleanup `
        'cleanupErrors' 'errors' 'cleanup error ledger' -CompareItems `
        -LedgerJson $ledgerJson -ManifestJson $manifestJson
    $cleanupErrors = $cleanupArrays.Ledger

    $evidenceArrays = Assert-VerifierDurableArrayPair $manifest $ledger `
        'artifacts' 'evidence' 'evidence ledger' -CompareItems `
        -LedgerJson $manifestJson -ManifestJson $ledgerJson
    $manifestArtifacts = $evidenceArrays.Ledger
    $ledgerEvidence = $evidenceArrays.Manifest
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

    $leaseArrays = Assert-VerifierDurableArrayPair $manifest $ledger `
        'leases' 'leases' 'lease ledger' -LedgerJson $manifestJson -ManifestJson $ledgerJson
    $manifestLeases = $leaseArrays.Ledger
    $ledgerLeases = $leaseArrays.Manifest
    $earlyManifestProfilesValue = Get-VerifierDurableArrayProperty $manifest `
        'browserSessions' 'manifest browser session ledger' $manifestJson
    $earlyLedgerProfilesValue = Get-VerifierDurableArrayProperty $ledger `
        'profiles' 'ledger browser profile ledger' $ledgerJson
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
        $candidateIdProperty = if ($null -eq $manifestLeaseCandidate) { $null } else {
            $manifestLeaseCandidate.PSObject.Properties['leaseId']
        }
        if ($null -eq $candidateIdProperty -or
                -not (Test-VerifierStrictStringProperty $manifestLeaseCandidate 'leaseId')) {
            Throw-VerifierInfrastructure 'Integrated child manifest lease ledger omitted or malformed its exact string leaseId.'
        }
        $candidateId = $candidateIdProperty.Value
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
        $candidatePortValue = Assert-VerifierDurableIntegralProperty $manifestLeaseCandidate `
            'port' 1 65535
        $candidatePort = [int]$candidatePortValue
        if ($candidatePort -lt 1 -or $candidatePort -gt 65535) {
            Throw-VerifierInfrastructure "Integrated child manifest lease $candidateId had an invalid port $candidatePort; expected 1..65535."
        }
        if ($candidatePort -in @(8888, 8898, 8899, 9876)) {
            Throw-VerifierInfrastructure "Integrated child manifest lease $candidateId used an ordinary developer port that is excluded from verifier claims."
        }
        $candidateMutex = Get-VerifierPortMutexName $null $candidatePort
        $candidateOwnerPidValue = Assert-VerifierDurableIntegralProperty $manifestLeaseCandidate `
            'claimOwnerPid' 1 ([int]::MaxValue)
        $candidateOwnerStartValue = Assert-VerifierDurableIntegralProperty $manifestLeaseCandidate `
            'claimOwnerStartTicks' 1
        $candidateOwnerPid = [int]$candidateOwnerPidValue
        $candidateOwnerStart = [long]$candidateOwnerStartValue
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
        $leaseIdProperty = if ($null -eq $lease) { $null } else {
            $lease.PSObject.Properties['leaseId']
        }
        if ($null -eq $leaseIdProperty -or
                -not (Test-VerifierStrictStringProperty $lease 'leaseId')) {
            Throw-VerifierInfrastructure 'Integrated child ledger lease omitted or malformed its exact string leaseId.'
        }
        $leaseId = $leaseIdProperty.Value
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
        $ledgerPortValue = Assert-VerifierDurableIntegralProperty $lease 'port' 1 65535
        $ledgerPort = [int]$ledgerPortValue
        if ($ledgerPort -lt 1 -or $ledgerPort -gt 65535) {
            Throw-VerifierInfrastructure "Integrated child lease $leaseId had an invalid port $ledgerPort; expected 1..65535."
        }
        if ($ledgerPort -in @(8888, 8898, 8899, 9876)) {
            Throw-VerifierInfrastructure "Integrated child lease $leaseId used an ordinary developer port that is excluded from verifier claims."
        }
        $ledgerMutex = Get-VerifierPortMutexName $null $ledgerPort
        $ledgerOwnerPidValue = Assert-VerifierDurableIntegralProperty $lease `
            'claimOwnerPid' 1 ([int]::MaxValue)
        $ledgerOwnerStartValue = Assert-VerifierDurableIntegralProperty $lease `
            'claimOwnerStartTicks' 1
        $ledgerOwnerPid = [int]$ledgerOwnerPidValue
        $ledgerOwnerStart = [long]$ledgerOwnerStartValue
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
        foreach ($numericProperty in @(
                [pscustomobject]@{ Name = 'port'; Minimum = 1L; Maximum = 65535L }
                [pscustomobject]@{ Name = 'claimOwnerPid'; Minimum = 1L; Maximum = [int]::MaxValue }
                [pscustomobject]@{ Name = 'claimOwnerStartTicks'; Minimum = 1L; Maximum = [long]::MaxValue }
                [pscustomobject]@{ Name = 'boundProcessId'; Minimum = 0L; Maximum = [int]::MaxValue }
                [pscustomobject]@{ Name = 'boundProcessStartTicks'; Minimum = 0L; Maximum = [long]::MaxValue }
                [pscustomobject]@{ Name = 'listenerProcessId'; Minimum = 0L; Maximum = [int]::MaxValue }
            )) {
            [void](Assert-VerifierDurableIntegralPair $lease $ml $numericProperty.Name `
                $numericProperty.Minimum $numericProperty.Maximum)
        }
        foreach ($identityProperty in @('leaseId', 'runId', 'repositoryIdentity', 'worktreeRoot',
                'kind', 'browserPath', 'profile', 'claimName', 'path', 'status',
                'claimState', 'releaseState', 'releaseJournalState',
                'bindValidatedUtc', 'releasedUtc', 'releaseBlockReason',
                'listenerInspectionUtc')) {
            [void](Assert-VerifierDurableStringPair $lease $ml $identityProperty)
        }
        foreach ($numericProperty in @(
                [pscustomobject]@{ Name = 'claimOwnerPid'; Minimum = 1L; Maximum = [int]::MaxValue }
                [pscustomobject]@{ Name = 'claimOwnerStartTicks'; Minimum = 1L; Maximum = [long]::MaxValue }
            )) {
            [void](Assert-VerifierDurableIntegralPair $lease $ml $numericProperty.Name `
                $numericProperty.Minimum $numericProperty.Maximum)
        }
        foreach ($durableBoolean in @(
                [pscustomobject]@{ Name = 'registered'; AllowNull = $false }
                [pscustomobject]@{ Name = 'releaseBlocked'; AllowNull = $false }
                [pscustomobject]@{ Name = 'mutexReleased'; AllowNull = $false }
                [pscustomobject]@{ Name = 'listenerInspectionSuccess'; AllowNull = $false }
                [pscustomobject]@{ Name = 'listenerInspectionKnown'; AllowNull = $false }
                [pscustomobject]@{ Name = 'listenerHasListeners'; AllowNull = $true }
                [pscustomobject]@{ Name = 'listenerAbsent'; AllowNull = $true }
                [pscustomobject]@{ Name = 'processProofRequired'; AllowNull = $false }
                [pscustomobject]@{ Name = 'processTerminationProven'; AllowNull = $false }
                [pscustomobject]@{ Name = 'processAbsent'; AllowNull = $false }
            )) {
            Assert-VerifierDurableBooleanPair $lease $ml $durableBoolean.Name `
                -AllowNull:$durableBoolean.AllowNull
        }
        if (-not (Test-VerifierIntegratedListenerOwnerSchema $lease) -or
                -not (Test-VerifierIntegratedListenerOwnerSchema $ml)) {
            Throw-VerifierInfrastructure "Integrated child lease $leaseId omitted or malformed its listener owner kind/proof/evidence in one durable ledger copy."
        }
        foreach ($listenerOwnerProperty in @('listenerProcessId',
                'listenerProcessStartTicks', 'listenerOwnerKind',
                'listenerOwnerProof', 'listenerOwnerEvidence')) {
            $ledgerProperty = $lease.PSObject.Properties[$listenerOwnerProperty]
            $manifestProperty = $ml.PSObject.Properties[$listenerOwnerProperty]
            if ($null -eq $ledgerProperty -or $null -eq $manifestProperty) {
                Throw-VerifierInfrastructure "Integrated child lease $leaseId omitted $listenerOwnerProperty from one durable ledger copy."
            }
            $ledgerValue = $ledgerProperty.Value
            $manifestValue = $manifestProperty.Value
            if ($listenerOwnerProperty -eq 'listenerProcessStartTicks' -and
                    $null -eq $ledgerValue -and $null -eq $manifestValue) {
                continue
            }
            if (($null -eq $ledgerValue) -xor ($null -eq $manifestValue) -or
                    ($null -ne $ledgerValue -and
                     ($ledgerValue.GetType() -ne $manifestValue.GetType() -or
                      -not $ledgerValue.Equals($manifestValue)))) {
                Throw-VerifierInfrastructure "Integrated child lease $leaseId disagreed with the manifest on $listenerOwnerProperty."
            }
        }
        $leasePortForIdentity = [int](Assert-VerifierDurableIntegralProperty $lease 'port' 1 65535)
        $manifestPortForIdentity = [int](Assert-VerifierDurableIntegralProperty $ml 'port' 1 65535)
        if ([string](Get-VerifierLedgerProperty $lease 'runId' '') -ne $runId -or
                [string](Get-VerifierLedgerProperty $lease 'repositoryIdentity' '') -ne $repositoryIdentity -or
                -not (Test-VerifierCanonicalWindowsPathValue ([string](Get-VerifierLedgerProperty $lease 'worktreeRoot' '')) $worktreeRoot) -or
                -not (Test-VerifierCanonicalWindowsPathValue ([string](Get-VerifierLedgerProperty $ml 'worktreeRoot' '')) $worktreeRoot) -or
                $leasePortForIdentity -in @(8888, 8898, 8899, 9876) -or
                $manifestPortForIdentity -ne $leasePortForIdentity -or
                -not (Test-VerifierCanonicalWindowsPathValue ([string](Get-VerifierLedgerProperty $ml 'path' '')) $claimPath)) {
            Throw-VerifierInfrastructure "Integrated child lease $leaseId had foreign, incomplete, or mismatched identity fields."
        }
        $leaseKind = [string](Get-VerifierLedgerProperty $lease 'kind' '')
        if (-not (Test-VerifierSupportedPortLeaseKind $leaseKind)) {
            Throw-VerifierInfrastructure "Integrated child lease $leaseId used an unsupported lease kind '$leaseKind'."
        }
        $leasePortForPath = $leasePortForIdentity
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
            if ($claimProperty -in @('port', 'ownerPid', 'ownerStartTicks')) {
                $claimRange = if ($claimProperty -eq 'port') {
                    [pscustomobject]@{ Minimum = 1L; Maximum = 65535L }
                } elseif ($claimProperty -eq 'ownerPid') {
                    [pscustomobject]@{ Minimum = 1L; Maximum = [int]::MaxValue }
                } else {
                    [pscustomobject]@{ Minimum = 1L; Maximum = [long]::MaxValue }
                }
                $claimValue = Assert-VerifierDurableIntegralProperty $claimDescriptor `
                    $claimProperty $claimRange.Minimum $claimRange.Maximum
                $manifestClaimValue = Assert-VerifierDurableIntegralProperty `
                    $manifestClaimDescriptor $claimProperty $claimRange.Minimum $claimRange.Maximum
                if ($claimValue.GetType() -ne $manifestClaimValue.GetType() -or
                        -not $claimValue.Equals($manifestClaimValue)) {
                    Throw-VerifierInfrastructure "Integrated child lease $leaseId manifest claim descriptor disagreed on exact integral field $claimProperty."
                }
                $expectedValue = if ($claimProperty -eq 'port') {
                    Assert-VerifierDurableIntegralProperty $lease 'port' 1 65535
                } elseif ($claimProperty -eq 'ownerPid') {
                    Assert-VerifierDurableIntegralProperty $lease 'claimOwnerPid' 1 ([int]::MaxValue)
                } else {
                    Assert-VerifierDurableIntegralProperty $lease 'claimOwnerStartTicks' 1
                }
                if ([long]$claimValue -ne [long]$expectedValue) {
                    Throw-VerifierInfrastructure "Integrated child lease $leaseId claim descriptor disagreed on $claimProperty."
                }
                continue
            }
            [void](Assert-VerifierDurableStringPair $claimDescriptor `
                $manifestClaimDescriptor $claimProperty)
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
            } elseif ($claimProperty -eq 'mutexName') {
                $claimExpected = [string](Get-VerifierLedgerProperty $lease 'claimName' '')
            }
            if ([string](Get-VerifierLedgerProperty $claimDescriptor $claimProperty '') -ne [string]$claimExpected) {
                Throw-VerifierInfrastructure "Integrated child lease $leaseId claim descriptor disagreed on $claimProperty."
            }
            if ([string](Get-VerifierLedgerProperty $manifestClaimDescriptor $claimProperty '') -ne
                    [string](Get-VerifierLedgerProperty $claimDescriptor $claimProperty '')) {
                Throw-VerifierInfrastructure "Integrated child lease $leaseId manifest claim descriptor disagreed on $claimProperty."
            }
        }
        foreach ($claimStringProperty in @('worktreeRoot', 'path')) {
            [void](Assert-VerifierDurableStringPair $claimDescriptor `
                $manifestClaimDescriptor $claimStringProperty)
        }
        if (-not (Test-VerifierCanonicalWindowsPathValue `
                ([string](Get-VerifierLedgerProperty $claimDescriptor 'worktreeRoot' '')) $worktreeRoot) -or
                -not (Test-VerifierCanonicalWindowsPathValue `
                ([string](Get-VerifierLedgerProperty $claimDescriptor 'path' '')) $claimPath) -or
                [string](Get-VerifierLedgerProperty $claimDescriptor 'mutexName' '') -ne $expectedMutexName -or
                 [string](Get-VerifierLedgerProperty $manifestClaimDescriptor 'mutexName' '') -ne $expectedMutexName) {
            Throw-VerifierInfrastructure "Integrated child lease $leaseId claim descriptor had foreign or mismatched ownership paths."
        }
        $descriptorOwnerPid = [int](Assert-VerifierDurableIntegralProperty $claimDescriptor `
            'ownerPid' 1 ([int]::MaxValue))
        $descriptorOwnerStart = [long](Assert-VerifierDurableIntegralProperty `
            $claimDescriptor 'ownerStartTicks' 1)
        [void](Assert-VerifierDurableIntegralProperty $claimDescriptor 'port' 1 65535)
        [void](Assert-VerifierDurableIntegralProperty $manifestClaimDescriptor `
            'ownerPid' 1 ([int]::MaxValue))
        [void](Assert-VerifierDurableIntegralProperty $manifestClaimDescriptor `
            'ownerStartTicks' 1)
        [void](Assert-VerifierDurableIntegralProperty $manifestClaimDescriptor `
            'port' 1 65535)
        $claimState = [string](Get-VerifierLedgerProperty $lease 'claimState' '')
        $releaseState = [string](Get-VerifierLedgerProperty $lease 'releaseState' '')
        $completedLease = ($state -eq 'completed')
        $terminalServer = if ([string](Get-VerifierLedgerProperty $lease 'kind' '') -eq 'preview') {
            Get-VerifierLedgerProperty $ledger 'server' $null
        } else { $null }
        # A released-looking incomplete record is not terminal evidence. Use the
        # same strict release/proof predicate for both completed and retained
        # child ledgers, so registration, release blocking, journal, mutex, and
        # process/listener proof cannot be bypassed by three lifecycle strings.
        $terminalLeaseReleased = Test-VerifierIntegratedLeaseTerminal $lease $claimPath `
            $terminalServer $runId $repositoryIdentity $worktreeRoot
        $terminalManifestReleased = Test-VerifierIntegratedLeaseTerminal $ml $claimPath `
            $terminalServer $runId $repositoryIdentity $worktreeRoot
        $terminalReleased = ($terminalLeaseReleased -and $terminalManifestReleased)
        if ($completedLease) {
            if (-not $terminalReleased -or
                    -not (Test-VerifierCanonicalWindowsPathValue ([string](Get-VerifierLedgerProperty $ml 'path' '')) $claimPath) -or
                    $manifestPortForIdentity -ne $leasePortForIdentity -or
                    [string](Get-VerifierLedgerProperty $ml 'status' '') -ne 'released' -or
                    [string](Get-VerifierLedgerProperty $ml 'releaseState' '') -ne 'complete') {
                Throw-VerifierInfrastructure 'Completed integrated child lease did not prove release, claim absence, and listener absence.'
            }
        } else {
            $leaseReleased = $terminalReleased
            if ([String]::IsNullOrWhiteSpace($claimState) -or
                    (Test-Path -LiteralPath $claimPath -PathType Leaf) -ne (-not $leaseReleased)) {
                Throw-VerifierInfrastructure 'Incomplete integrated child lease did not retain exactly the expected claim resource.'
            }
            if (-not $leaseReleased) {
                try { $claim = Get-Content -LiteralPath $claimPath -Raw | ConvertFrom-Json } catch {
                    Throw-VerifierInfrastructure 'Incomplete integrated child claim was not readable evidence.'
                }
                [void](Read-VerifierIntegratedClaimRecord $claim $lease $claimPath `
                    $runId $repositoryIdentity $worktreeRoot `
                    ('integrated child lease ' + $leaseId + ' claim'))
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

    $profileArrays = Assert-VerifierDurableArrayPair $manifest $ledger `
        'browserSessions' 'profiles' 'browser profile ledger' `
        -LedgerJson $manifestJson -ManifestJson $ledgerJson
    $manifestProfiles = $profileArrays.Ledger
    $ledgerProfiles = $profileArrays.Manifest
    # ConvertFrom-Json exposes a one-item JSON array as a scalar in some
    # PowerShell versions. The raw JSON shape was already proven above; retain
    # that proof for bounded timeout cleanup readers without making a missing
    # or null property look like an explicit empty array.
    $manifest.artifacts = [object[]]$manifestArtifacts
    $ledger.evidence = [object[]]$ledgerEvidence
    $manifest.leases = [object[]]$manifestLeases
    $ledger.leases = [object[]]$ledgerLeases
    $manifest.browserSessions = [object[]]$manifestProfiles
    $ledger.profiles = [object[]]$ledgerProfiles
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
                'worktreeRoot', 'routeId', 'routeName', 'profile', 'cdpLeasePath',
                'processCommandLine', 'browserPath', 'targetId', 'expectedUrl',
                'expectedRunMarker', 'expectedRouteMarker', 'status',
                'cleanupResult', 'error')) {
            [void](Assert-VerifierDurableStringPair $profile $matchedProfile $identityProperty)
        }
        foreach ($numericProperty in @(
                [pscustomobject]@{ Name = 'cdpPort'; Minimum = 1L; Maximum = 65535L }
                [pscustomobject]@{ Name = 'processId'; Minimum = 0L; Maximum = [int]::MaxValue }
                [pscustomobject]@{ Name = 'processStartTicks'; Minimum = 0L; Maximum = [long]::MaxValue }
                [pscustomobject]@{ Name = 'processParentProcessId'; Minimum = 0L; Maximum = [int]::MaxValue }
                [pscustomobject]@{ Name = 'processParentProcessStartTicks'; Minimum = 0L; Maximum = [long]::MaxValue }
            )) {
            [void](Assert-VerifierDurableIntegralPair $profile $matchedProfile $numericProperty.Name `
                $numericProperty.Minimum $numericProperty.Maximum)
        }
        foreach ($profileBooleanProperty in @('profileProcessScanCompleted',
                'profileInspectionFailed')) {
            Assert-VerifierDurableBooleanPair $profile $matchedProfile `
                $profileBooleanProperty
        }
        if (-not (Test-VerifierCanonicalWindowsPathValue $profile.worktreeRoot $worktreeRoot) -or
                -not (Test-VerifierCanonicalWindowsPathValue $matchedProfile.worktreeRoot $worktreeRoot)) {
                Throw-VerifierInfrastructure 'Integrated child profile worktree identity was foreign or incomplete.'
        }
        $browserRoot = Get-VerifierFullPath (Join-Path $runRoot 'browser')
        if (-not (Test-VerifierChildPath $browserRoot $profilePath)) {
            Throw-VerifierInfrastructure "Integrated child browser profile '$profilePath' was not directly owned by its browser run namespace."
        }
        foreach ($browserPathValue in @(
                $profile.browserPath,
                $matchedProfile.browserPath)) {
            if ([String]::IsNullOrWhiteSpace($browserPathValue) -or
                    [String]::IsNullOrWhiteSpace((Get-VerifierCanonicalWindowsPath $browserPathValue)) -or
                    -not (Test-VerifierCanonicalWindowsPathValue $browserPathValue $expectedBrowserPath)) {
                Throw-VerifierInfrastructure 'Integrated child browser profile omitted or mismatched its resolved BrowserPath.'
            }
        }
        $manifestLeasePath = Get-VerifierLedgerPath $matchedProfile.cdpLeasePath `
            'manifest browser lease' $claimDirectory
        if (-not (Test-VerifierCanonicalWindowsPathValue $ledgerLeasePath $manifestLeasePath)) {
            Throw-VerifierInfrastructure 'Integrated child profile lease identity was not bijective.'
        }
        $profileLease = $ledgerLeaseByPath[$ledgerLeasePath.ToLowerInvariant()]
        $profileLeaseKind = [string](Get-VerifierLedgerProperty $profileLease 'kind' '')
        $profilePort = [int](Assert-VerifierDurableIntegralProperty $profile 'cdpPort' 1 65535)
        $matchedProfilePort = [int](Assert-VerifierDurableIntegralProperty `
            $matchedProfile 'cdpPort' 1 65535)
        $profileLeasePort = [int](Assert-VerifierDurableIntegralProperty `
            $profileLease 'port' 1 65535)
        if ([string](Get-VerifierLedgerProperty $profile 'owner' '') -ne 'run' -or
                $profileLeaseKind -ne 'cdp' -or
                [string](Get-VerifierLedgerProperty $profileLease 'kind' '') -ne 'cdp' -or
                $matchedProfilePort -ne $profilePort -or
                $profilePort -ne $profileLeasePort) {
            Throw-VerifierInfrastructure 'Integrated child profile did not identify its exact run-owned cdp lease and valid port.'
        }
        if (-not (Test-VerifierCanonicalWindowsPathValue $profile.profile $profilePath) -or
                -not (Test-VerifierCanonicalWindowsPathValue $matchedProfile.profile $profilePath) -or
                -not (Test-VerifierCanonicalWindowsPathValue $profile.cdpLeasePath $ledgerLeasePath) -or
                -not (Test-VerifierCanonicalWindowsPathValue $matchedProfile.cdpLeasePath $ledgerLeasePath) -or
                $profile.runId -ne $runId -or
                $matchedProfile.runId -ne $runId) {
            Throw-VerifierInfrastructure 'Integrated child browser profile path, lease path, or run identity was not canonical and exact.'
        }
        $profileProcessId = [int](Assert-VerifierDurableIntegralProperty $profile `
            'processId' 0 ([int]::MaxValue))
        $profileProcessStart = [long](Assert-VerifierDurableIntegralProperty $profile `
            'processStartTicks' 0)
        $profileParentProcessId = [int](Assert-VerifierDurableIntegralProperty $profile `
            'processParentProcessId' 0 ([int]::MaxValue))
        $profileParentProcessStart = [long](Assert-VerifierDurableIntegralProperty `
            $profile 'processParentProcessStartTicks' 0)
        if ($profileProcessId -gt 0 -and
                ($profileProcessStart -le 0 -or
                 $profileParentProcessId -le 0 -or
                 $profileParentProcessStart -le 0 -or
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
        $inverseProfilePort = [int](Assert-VerifierDurableIntegralProperty $inverseProfile `
            'cdpPort' 1 65535)
        $inverseLeasePort = [int](Assert-VerifierDurableIntegralProperty $lease 'port' 1 65535)
        if (-not (Test-VerifierCanonicalWindowsPathValue `
                ([string](Get-VerifierLedgerProperty $inverseProfile 'profile' '')) $leaseProfilePath) -or
                -not (Test-VerifierCanonicalWindowsPathValue `
                ([string](Get-VerifierLedgerProperty $inverseProfile 'cdpLeasePath' '')) $leasePath) -or
                $inverseProfilePort -ne $inverseLeasePort -or
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
    foreach ($serverProofStringProperty in @(
            'owner', 'baseUrl', 'repositoryIdentity', 'worktreeRoot',
            'repositoryRoot', 'webRoot', 'identityProtocol',
            'processCommandLine', 'script', 'runId', 'nonce', 'leaseId',
            'leaseKind', 'leaseClaimName', 'leaseClaimState',
            'leaseReleaseState', 'leaseReleaseJournalState', 'leasePath',
             'state', 'cleanupResult', 'stdoutLog', 'stderrLog', 'error')) {
        [void](Assert-VerifierDurableStringPair $server $manifestServer `
            $serverProofStringProperty)
    }
    $serverOwner = $server.owner
    $manifestServerOwner = $manifestServer.owner
    if ($serverOwner -notin @('none', 'run', 'caller') -or $serverOwner -ne $manifestServerOwner) {
        Throw-VerifierInfrastructure 'Integrated child server ownership was missing, foreign, or differed between ledgers.'
    }
    foreach ($serverProperty in @('owner', 'baseUrl', 'repositoryIdentity', 'worktreeRoot', 'repositoryRoot',
            'webRoot', 'identityProtocol',
            'processCommandLine',
            'script', 'runId', 'nonce', 'leaseId', 'leaseKind', 'leaseClaimName',
            'leaseClaimState', 'leaseReleaseState', 'leaseReleaseJournalState',
            'leasePath', 'stdoutLog', 'stderrLog',
            'state', 'cleanupResult', 'error',
            'leaseListenerAbsent', 'leaseProcessProofRequired')) {
        if ($null -eq $server.PSObject.Properties[$serverProperty] -or
                $null -eq $manifestServer.PSObject.Properties[$serverProperty]) {
            Throw-VerifierInfrastructure "Integrated child server omitted $serverProperty from one durable ledger copy."
        }
        if ([string](Get-VerifierLedgerProperty $server $serverProperty '') -ne
                [string](Get-VerifierLedgerProperty $manifestServer $serverProperty '')) {
            Throw-VerifierInfrastructure "Integrated child server ledger differed from the manifest on $serverProperty."
        }
    }
    foreach ($numericProperty in @(
            [pscustomobject]@{ Name = 'port'; Minimum = 0L; Maximum = 65535L }
            [pscustomobject]@{ Name = 'processId'; Minimum = 0L; Maximum = [int]::MaxValue }
            [pscustomobject]@{ Name = 'processStartTicks'; Minimum = 0L; Maximum = [long]::MaxValue }
            [pscustomobject]@{ Name = 'processParentProcessId'; Minimum = 0L; Maximum = [int]::MaxValue }
            [pscustomobject]@{ Name = 'processParentProcessStartTicks'; Minimum = 0L; Maximum = [long]::MaxValue }
            [pscustomobject]@{ Name = 'leaseOwnerPid'; Minimum = 0L; Maximum = [int]::MaxValue }
            [pscustomobject]@{ Name = 'leaseOwnerStartTicks'; Minimum = 0L; Maximum = [long]::MaxValue }
        )) {
        [void](Assert-VerifierDurableIntegralPair $server $manifestServer $numericProperty.Name `
            $numericProperty.Minimum $numericProperty.Maximum)
    }
    foreach ($durableBoolean in @(
            [pscustomobject]@{ Name = 'identityVerified'; AllowNull = $false }
            [pscustomobject]@{ Name = 'callerOwned'; AllowNull = $false }
            [pscustomobject]@{ Name = 'processIdentityKnown'; AllowNull = $false }
            [pscustomobject]@{ Name = 'ownershipUncertain'; AllowNull = $false }
            [pscustomobject]@{ Name = 'processTerminationProven'; AllowNull = $false }
            [pscustomobject]@{ Name = 'processAbsent'; AllowNull = $false }
            [pscustomobject]@{ Name = 'listenerInspectionProven'; AllowNull = $false }
            [pscustomobject]@{ Name = 'listenerAbsent'; AllowNull = $true }
            [pscustomobject]@{ Name = 'leaseListenerAbsent'; AllowNull = $true }
            [pscustomobject]@{ Name = 'leaseProcessProofRequired'; AllowNull = $true }
        )) {
        Assert-VerifierDurableBooleanPair $server $manifestServer $durableBoolean.Name `
            -AllowNull:$durableBoolean.AllowNull
    }
    $serverOwnerLeaseForSchema = $null
    $manifestServerOwnerLeaseForSchema = $null
    if ($serverOwner -eq 'run') {
        $serverLeaseIdForSchema = [string](Get-VerifierLedgerProperty $server 'leaseId' '')
        $serverOwnerLeaseCandidates = @($ledgerLeases | Where-Object {
            [string](Get-VerifierLedgerProperty $_ 'leaseId' '') -ceq $serverLeaseIdForSchema
        })
        $manifestServerOwnerLeaseCandidates = @($manifestLeases | Where-Object {
            [string](Get-VerifierLedgerProperty $_ 'leaseId' '') -ceq $serverLeaseIdForSchema
        })
        if (@($serverOwnerLeaseCandidates).Count -ne 1 -or
                @($manifestServerOwnerLeaseCandidates).Count -ne 1) {
            Throw-VerifierInfrastructure 'Integrated child run-owned server listener owner proof did not identify exactly one paired lease in each durable copy.'
        }
        $serverOwnerLeaseForSchema = $serverOwnerLeaseCandidates[0]
        $manifestServerOwnerLeaseForSchema = $manifestServerOwnerLeaseCandidates[0]
    }
    if (-not (Test-VerifierIntegratedServerListenerOwnerSchema $server $serverOwnerLeaseForSchema) -or
            -not (Test-VerifierIntegratedServerListenerOwnerSchema $manifestServer $manifestServerOwnerLeaseForSchema)) {
        Throw-VerifierInfrastructure 'Integrated child server omitted or malformed its explicit listener owner tuple.'
    }
    foreach ($serverListenerOwnerProperty in @('leaseListenerOwnerKind',
            'leaseListenerOwnerProof', 'leaseListenerOwnerEvidence')) {
        $ledgerProperty = $server.PSObject.Properties[$serverListenerOwnerProperty]
        $manifestProperty = $manifestServer.PSObject.Properties[$serverListenerOwnerProperty]
        if ($ledgerProperty.Value.GetType() -ne $manifestProperty.Value.GetType() -or
                -not $ledgerProperty.Value.Equals($manifestProperty.Value)) {
            Throw-VerifierInfrastructure "Integrated child server disagreed on $serverListenerOwnerProperty."
        }
    }
    if ($serverOwner -ne 'run') {
        foreach ($serverOwnerCopy in @($server, $manifestServer)) {
            if (-not (Test-VerifierListenerOwnerTuple `
                    $serverOwnerCopy.leaseListenerOwnerKind `
                    $serverOwnerCopy.leaseListenerOwnerProof `
                    $serverOwnerCopy.leaseListenerOwnerEvidence 0 0L $true)) {
                Throw-VerifierInfrastructure 'Integrated child ownerless/caller-owned server carried a noncanonical listener owner tuple.'
            }
        }
    }
    if ([string](Get-VerifierLedgerProperty $server 'repositoryIdentity' '') -ne $repositoryIdentity -or
            -not (Test-VerifierCanonicalWindowsPathValue `
                ([string](Get-VerifierLedgerProperty $server 'worktreeRoot' '')) $worktreeRoot)) {
        Throw-VerifierInfrastructure 'Integrated child server repository/worktree identity was foreign or incomplete.'
    }
    $serverScript = $server.script
    $manifestScript = $manifestServer.script
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
        # The strict ordinal pair check above is the only accepted boundary for
        # these durable paths. Do not coerce malformed JSON values before using
        # them for path ownership or equality checks.
        $serverLog = $server.PSObject.Properties[$serverLogProperty].Value
        $manifestLog = $manifestServer.PSObject.Properties[$serverLogProperty].Value
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
        if ($serverOwner -eq 'run' -and -not (Test-VerifierCanonicalWindowsPathValue $manifestLog $serverLog)) {
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
        $serverLeaseProcessProof = Get-VerifierDurableBooleanValue $server `
            'leaseProcessProofRequired' 'run-owned server lease processProofRequired' -AllowNull
        if ($null -eq $serverLeaseProcessProof) {
            Throw-VerifierInfrastructure 'Integrated child run-owned server omitted its exact lease process-proof Boolean.'
        }
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
        if (-not (Test-VerifierIntegratedListenerOwnerSchema $serverLease[0] $server) -or
                -not (Test-VerifierIntegratedServerListenerOwnerSchema $manifestServer $serverLease[0])) {
            Throw-VerifierInfrastructure 'Integrated child run-owned server and lease owner tuples were not exact and mutually consistent.'
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
            $leaseKindForOwner = [string](Get-VerifierLedgerProperty $lease 'kind' '')
            $ownerServer = if ($leaseKindForOwner -eq 'preview') { $server } else { $null }
            if (-not (Test-VerifierIntegratedListenerOwnerSchema $lease $ownerServer)) {
                Throw-VerifierInfrastructure 'Completed integrated child lease omitted or malformed its listener owner proof before revalidation.'
            }
            $listenerPid = [int]$lease.PSObject.Properties['listenerProcessId'].Value
            $listenerOwnerKind = $lease.PSObject.Properties['listenerOwnerKind'].Value
            $kernelTransport = ($listenerOwnerKind -ceq 'kernel-transport')
            $expectedPid = if ($kernelTransport) { 0 } elseif ($listenerPid -gt 0) {
                $listenerPid
            } else { $boundPid }
            $expectedStart = if ($kernelTransport) { 0L } else { $boundStart }
            if (-not $kernelTransport -and $listenerPid -gt 0) {
                $listenerStartProperty = $lease.PSObject.Properties['listenerProcessStartTicks']
                if ($null -eq $listenerStartProperty -or $null -eq $listenerStartProperty.Value) {
                    Throw-VerifierInfrastructure 'Completed integrated child user-process listener omitted its start identity.'
                }
                if (-not (Test-VerifierStrictNonnegativeIntegerProperty $lease `
                        'listenerProcessStartTicks')) {
                    Throw-VerifierInfrastructure 'Completed integrated child user-process listener carried a malformed start identity.'
                }
                $expectedStart = [long]$listenerStartProperty.Value
            }
            $leaseProfile = [string](Get-VerifierLedgerProperty $lease 'profile' '')
            $leaseProcess = [pscustomobject]@{
                ProcessId = $boundPid
                ProcessStartTicks = $boundStart
            }
            $leaseProcessProof = Confirm-VerifierRecordedProcessAbsent $leaseProcess `
                ('completed lease ' + [string](Get-VerifierLedgerProperty $lease 'leaseId' '')) `
                '' $leasePort '' $runId '' $leaseProfile
            if (-not (Test-VerifierStrictBooleanProperty $leaseProcessProof 'QueryProven' $true) -or
                    -not (Test-VerifierStrictBooleanProperty $leaseProcessProof 'Absent' $true)) {
                Throw-VerifierInfrastructure 'Completed integrated child lease owner process was still present or not independently proven absent.'
            }
            $leaseListenerProof = Confirm-VerifierReleasedListener $leasePort $expectedPid `
                $expectedStart '' 0 '' $runId '' $leaseProfile
            if (-not (Test-VerifierStrictBooleanProperty $leaseListenerProof 'QueryProven' $true) -or
                    -not (Test-VerifierStrictBooleanProperty $leaseListenerProof 'OldOwnerAbsent' $true)) {
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
            if (-not (Test-VerifierStrictBooleanProperty $profileProof 'QueryProven' $true) -or
                    -not (Test-VerifierStrictBooleanProperty $profileProof 'Absent' $true)) {
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
            if (-not (Test-VerifierStrictBooleanProperty $serverProcessProof 'QueryProven' $true) -or
                    -not (Test-VerifierStrictBooleanProperty $serverProcessProof 'Absent' $true)) {
                Throw-VerifierInfrastructure 'Completed integrated run-owned preview server process was still present or not independently proven absent.'
            }
            $serverKernelTransport = ([string](Get-VerifierLedgerProperty $server `
                'leaseListenerOwnerKind' '') -eq 'kernel-transport')
            $serverListenerProof = if ($serverKernelTransport) {
                # A kernel transport owner has no process start identity. The
                # independent released-listener check is absence-only; any
                # remaining PID 4 listener is rejected by the shared query.
                Confirm-VerifierReleasedListener $serverPort 0 0 '' 0 '' $runId '' '' 0
            } else {
                Confirm-VerifierReleasedListener $serverPort `
                    ([int](Get-VerifierLedgerProperty $server 'processId' 0)) `
                    ([long](Get-VerifierLedgerProperty $server 'processStartTicks' 0)) `
                    ([string](Get-VerifierLedgerProperty $server 'processCommandLine' '')) `
                    ([int](Get-VerifierLedgerProperty $server 'processParentProcessId' 0)) `
                    $serverScript $runId `
                    ([string](Get-VerifierLedgerProperty $server 'nonce' '')) '' `
                    ([long](Get-VerifierLedgerProperty $server 'processParentProcessStartTicks' 0))
            }
            if (-not (Test-VerifierStrictBooleanProperty $serverListenerProof 'QueryProven' $true) -or
                    -not (Test-VerifierStrictBooleanProperty $serverListenerProof 'OldOwnerAbsent' $true)) {
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
    if ($state -eq 'completed') {
        if ($ledgerCleanupState -cne 'complete' -or
                $cleanupErrors.Count -ne 0) {
            Throw-VerifierInfrastructure 'Integrated child ledger claimed completion with cleanup errors.'
        }
    } elseif ($AllowIncomplete -and $state -eq 'cleanup-failed' -and
            $cleanupErrors.Count -eq 0) {
        Throw-VerifierInfrastructure 'Incomplete cleanup-failed child ledger omitted the required retained cleanup error/evidence record.'
    } elseif (-not $AllowIncomplete) {
        Throw-VerifierInfrastructure 'Only the explicit parent-timeout path may accept an incomplete child ledger.'
    }
    $forcedNegativeProof = Get-VerifierLedgerProperty $ledger 'forcedNegativeProof' $null
    if ($ExpectedExit -eq 1) {
        Assert-VerifierIntegratedForcedNegativeProof $forcedNegativeProof $ExpectedExit `
            $ExpectedForcedMarker
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
        $timeoutProfiles = Get-VerifierDurableArrayProperty $Ledger 'profiles' `
            'integrated timeout browser profile ledger'
        foreach ($profile in $timeoutProfiles) {
            $profileIdentity = Assert-VerifierDurableProcessIdentityTuple $profile `
                -ProcessIdPropertyName 'processId' `
                -ProcessStartPropertyName 'processStartTicks' `
                -ParentProcessIdPropertyName 'processParentProcessId' `
                -ParentProcessStartPropertyName 'processParentProcessStartTicks' `
                -CommandLinePropertyName 'processCommandLine' `
                -Label 'integrated timeout browser profile process identity'
            $profilePath = Get-VerifierLedgerPath ([string](Get-VerifierLedgerProperty $profile 'profile' '')) `
                'timeout browser profile' $runRoot
            if (-not (Test-Path -LiteralPath $profilePath -PathType Container)) {
                Throw-VerifierInfrastructure "Integrated timeout browser profile was not retained: $profilePath"
            }
            Assert-VerifierNoReparseTree $profilePath
            $profilePid = $profileIdentity.ProcessId
            $profileStart = $profileIdentity.ProcessStartTicks
            $profileParent = $profileIdentity.ParentProcessId
            $profileParentStart = $profileIdentity.ParentProcessStartTicks
            $profileCommand = $profileIdentity.CommandLine
            $profilePort = [int](Get-VerifierLedgerProperty $profile 'cdpPort' 0)
            $profileBrowserPath = [string](Get-VerifierLedgerProperty $profile 'browserPath' '')
            if ($profilePid -gt 0 -and ($profilePort -lt 1 -or
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
        $timeoutLeases = Get-VerifierDurableArrayProperty $Ledger 'leases' `
            'integrated timeout lease ledger'
        foreach ($lease in $timeoutLeases) {
            $leaseId = [string](Get-VerifierLedgerProperty $lease 'leaseId' '')
            $leasePath = Get-VerifierLedgerPath ([string](Get-VerifierLedgerProperty $lease 'path' '')) `
                ('timeout lease ' + $leaseId) $claimDirectory
            if (-not (Test-Path -LiteralPath $leasePath -PathType Leaf)) {
                Throw-VerifierInfrastructure "Integrated timeout lease $leaseId was not retained as exact evidence."
            }
            $claim = Get-Content -LiteralPath $leasePath -Raw -ErrorAction Stop | ConvertFrom-Json
            $claimRecord = Read-VerifierIntegratedClaimRecord $claim $lease $leasePath `
                $runId $repositoryIdentity $worktreeRoot `
                ('integrated timeout lease ' + $leaseId + ' claim')
            $leasePort = $claimRecord.Port
            $claimOwnerPid = $claimRecord.OwnerPid
            $claimOwnerStart = $claimRecord.OwnerStartTicks
            $claimOwnerProof = Confirm-VerifierRecordedProcessAbsent `
                ([pscustomobject]@{
                    ProcessId = $claimOwnerPid
                    ProcessStartTicks = $claimOwnerStart
                }) ('timeout lease ' + $leaseId + ' claim owner')
            if (-not (Test-VerifierStrictBooleanProperty $claimOwnerProof 'QueryProven' $true) -or
                    -not (Test-VerifierStrictBooleanProperty $claimOwnerProof 'Absent' $true)) {
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
    $expectedForcedMarker = ''
    if ($expectedExit -eq 1) {
        if ($routeArguments -contains '-Task43PForcedNegative') {
            $expectedForcedMarker = 'FAIL:task43p-forced-negative-canary'
        } elseif ($routeArguments -contains '-Task43ForcedNegative') {
            $expectedForcedMarker = 'FAIL:task43-forced-negative-canary'
        } else {
            Throw-VerifierInfrastructure "Integrated child $label expected exit 1 but did not select a forced-negative route."
        }
    }
    $childLedgerPath = New-VerifierIntegratedChildLedger $label $expectedExit
    $quotePowerShellArgument = {
        param([string]$value)
        if ($null -eq $value) { return "''" }
        return "'" + $value.Replace("'", "''") + "'"
    }
    $commandParts = @('&', (& $quotePowerShellArgument $PSCommandPath),
        '-BaseUrl', (& $quotePowerShellArgument $BaseUrl),
        '-TimeoutSeconds', ('([int]' + [string]$TimeoutSeconds + ')'),
        '-BrowserPath', (& $quotePowerShellArgument $childBrowserPath),
        '-ParentLedgerPath', (& $quotePowerShellArgument $childLedgerPath),
        '-ParentNamespaceRoot', (& $quotePowerShellArgument (Split-Path -Parent $childLedgerPath)))
    if ($routeArguments -contains '-Task43P' -or $routeArguments -contains '-Task43PForcedNegative') {
        if ($script:Task43PCandidateSha -cnotmatch '^[0-9a-f]{40}$') {
            Throw-VerifierInfrastructure 'Integrated Task43P child requires a frozen parent candidate.'
        }
        $commandParts += @('-ExpectedCandidateSha',
            (& $quotePowerShellArgument $script:Task43PCandidateSha))
    }
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
    $childShellCommand = ($commandParts -join ' ') +
        (Get-VerifierIntegratedChildShellStatusTail)
    $childArguments = @('-NoProfile', '-ExecutionPolicy', 'Bypass', '-Command',
        $childShellCommand)
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
    $childLedger = $null
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
                        $childLedgerPath $expectedExit -AllowIncomplete `
                        -ExpectedForcedMarker $expectedForcedMarker
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
                [void](Read-VerifierIntegratedChildLedger $childLedgerPath $expectedExit `
                    -AllowIncomplete -ExpectedForcedMarker $expectedForcedMarker)
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
                    $expectedExit -AllowIncomplete -ExpectedForcedMarker $expectedForcedMarker
                if ([string]$retainedLedger.state -ne 'completed') {
                    Write-Host ("INTEGRATED CHILD TIMEOUT EVIDENCE RETAINED $label ledger=$childLedgerPath " +
                        "runRoot=$($retainedLedger.runRoot) resourceProof=$([bool]($null -ne $timeoutResourceProof))")
                }
            } else {
                # A process that terminated (even with expected infrastructure
                # exit 2) must have completed its own ledger cleanup before the
                # parent accepts its result.
                [void](Read-VerifierIntegratedChildLedger $childLedgerPath $expectedExit `
                    -ExpectedForcedMarker $expectedForcedMarker)
            }
        } catch {
            Throw-VerifierInfrastructure ('Integrated child status failure lacked the required parent ledger proof: ' +
                (Get-VerifierErrorMessage $_))
        }
        if (Test-VerifierInfrastructureError $failure) { throw $failure }
        Throw-VerifierInfrastructure ('Integrated child status could not be proven: ' +
            (Get-VerifierErrorMessage $failure))
    }
    $childLedger = Read-VerifierIntegratedChildLedger $childLedgerPath $expectedExit `
        -ExpectedForcedMarker $expectedForcedMarker
    Write-Host ("INTEGRATED CHILD LEDGER $label state=$($childLedger.state) path=$childLedgerPath")
    foreach ($line in $childOutput) { Write-Host ([string]$line) }
    Assert-IntegratedChildOutputContract $label $expectedExit $childExit $childOutput `
        $childLedger $expectedForcedMarker
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
    $marker = Get-Task43ForcedNegativeExpectedMarker
    $routeName = Get-Task43ForcedNegativeExpectedRoute $marker
    try {
        $family = 'led'
        $seed = 3
        if ($null -ne $script:Task43PSourceDefinition) {
            $family = $script:Task43PSourceDefinition.urlFamily
            $seed = $script:Task43PSourceDefinition.seed
        }
        [void](verifyRoute $routeName `
            "$BaseUrl/circuitjs.html?tsjChallenge=$family&seed=$seed&tsjVerifyTask43P=true&tsjTask43PForcedFailure=true&running=true" `
            'UNPROVEN:task43p' '' $marker)
    } catch {
        Invalidate-Task43ForcedNegativeProof
        Write-Host "FAIL task43p forced-negative canary - verifier infrastructure: $($_.Exception.Message)"
        return 2
    }
    return (Get-Task43ForcedNegativeRouteExitCode $marker)
}
if ($Task43P) {
    if ($Task43PRuntime) {
        $runtimeRoute = "$BaseUrl/circuitjs.html?tsjChallenge=led&seed=3&" +
            'tsjVerifyTask43P=true&tsjTask43PRuntime=true&running=true'
        if (-not (verifyRoute 'task43p runtime lanes' $runtimeRoute `
                'OBSERVED:task43p-runtime')) { return 2 }
        if ($null -eq $script:Task43PRuntimeOutcome) {
            Throw-VerifierInfrastructure 'Task43P runtime route completed without its validated outcome.'
        }
        $script:Task43PRuntimeRoutePassedAfterCleanup = $true
        return $script:Task43PRuntimeOutcome.ExitCode
    }
    $task43pSeeds = if ($script:VerifierBoundParameters.ContainsKey('Seeds')) { $Seeds } else {
        @(0, 2, 3)
    }
    $task43pFamilies = @('led', 'diode', 'rc', 'npn', 'nmos', 'parallel')
    if ($Task43PFamily -cne '') { $task43pFamilies = @($Task43PFamily) }
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
        [void](verifyRoute 'task43 forced-negative canary' `
            "$BaseUrl/circuitjs.html?tsjChallenge=led&seed=3&tsjVerifyTask43=true&tsjTask43ForcedFailure=true&running=true" `
            'PASS:task43' '' 'FAIL:task43-forced-negative-canary')
    } catch {
        Invalidate-Task43ForcedNegativeProof
        Write-Host "FAIL task43 forced-negative canary - verifier infrastructure: $($_.Exception.Message)"
        return 2
    }
    return (Get-Task43ForcedNegativeRouteExitCode `
        'FAIL:task43-forced-negative-canary')
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

function Invoke-GateBListenerProofCanary() {
    $module = @(Get-Module VerifierIsolation | Select-Object -First 1)
    if ($module.Count -ne 1) { throw 'VerifierIsolation module was unavailable for listener proof canary.' }
    $kernelPreviewMutex = $null
    $kernelPreviewMutexHeld = $false
    $kernelPreviewClaimName = ''
    try {
    $repositoryRoot = Get-VerifierFullPath (Join-Path $PSScriptRoot '..')
    $previewScript = Get-VerifierFullPath (Join-Path $repositoryRoot 'scripts\preview.ps1')
    $webRoot = Get-VerifierFullPath (Join-Path $repositoryRoot 'war')
    $repositoryIdentity = 'gate-b-listener-proof-repository'
    $runId = 'gate-b-listener-proof-run'
    $nonce = 'gate-b-listener-proof-nonce'
    $userClaimPath = Get-VerifierFullPath (Join-Path ([IO.Path]::GetTempPath()) `
        ('TroubleshootJS\verify\listener-proof-user-' + [Guid]::NewGuid().ToString('N') + '.lease'))
    $kernelClaimPath = Get-VerifierFullPath (Join-Path ([IO.Path]::GetTempPath()) `
        ('TroubleshootJS\verify\listener-proof-kernel-' + [Guid]::NewGuid().ToString('N') + '.lease'))
    $userProcess = Get-Process -Id $PID -ErrorAction Stop
    $userProcessStartTicks = [long](Get-VerifierProcessStartTicks $userProcess)
    $userPreviewContext = [pscustomobject]@{
        Server = $null; WorktreeRoot = $repositoryRoot
        RepositoryIdentity = $repositoryIdentity; RunId = $runId
        PreviewNonce = $nonce
    }
    $userDirectOwner = [pscustomobject]@{
        DirectProcessOwner = $true; Process = $userProcess; ProcessId = $PID
        ProcessStartTicks = $userProcessStartTicks
        IdentityProof = 'retained-process-object-v1'
    }
    $createTerminalLease = {
        param([string]$Kind, [int]$ListenerProcessId, $ListenerStart,
            [string]$ListenerOwnerKind, [string]$ListenerOwnerProof,
            [string]$ListenerOwnerEvidence, [int]$BoundProcessId,
            [long]$BoundProcessStartTicks, [int]$Port)
        $claimPath = if ($Kind -eq 'preview') { $kernelClaimPath } else { $userClaimPath }
        return [pscustomobject]([ordered]@{
            leaseId = 'gate-b-listener-proof-' + $Kind + '-' + [string]$Port
            kind = $Kind; port = $Port; path = $claimPath
            runId = $runId; repositoryIdentity = $repositoryIdentity
            worktreeRoot = $repositoryRoot
            status = 'released'; claimName = Get-VerifierPortMutexName $null $Port
            claimState = 'released'; releaseState = 'complete'
            releaseJournalState = 'complete'; mutexReleased = $true
            profile = ''; profilePath = ''; browserPath = ''
            bindValidatedUtc = ''; releasedUtc = ''; releaseBlockReason = ''
            listenerInspectionSuccess = $true; listenerInspectionKnown = $true
            listenerHasListeners = $false; listenerAbsent = $true
            boundProcessId = $BoundProcessId
            boundProcessStartTicks = $BoundProcessStartTicks
            listenerProcessId = $ListenerProcessId
            listenerProcessStartTicks = $ListenerStart
            listenerOwnerKind = $ListenerOwnerKind
            listenerOwnerProof = $ListenerOwnerProof
            listenerOwnerEvidence = $ListenerOwnerEvidence
            listenerInspectionUtc = ''
            claimOwnerPid = [int]$PID
            claimOwnerStartTicks = [long]$userProcessStartTicks
            registered = $true; releaseBlocked = $false
            processProofRequired = $true
             processTerminationProven = $true; processAbsent = $true
             ClaimMutex = $null
             claim = [ordered]@{
                protocol = 'troubleshootjs-verifier-port-claim-v1'
                runId = $runId; repositoryIdentity = $repositoryIdentity
                worktreeRoot = $repositoryRoot
                leaseId = 'gate-b-listener-proof-' + $Kind + '-' + [string]$Port
                path = $claimPath; kind = $Kind; port = $Port
                mutexName = Get-VerifierPortMutexName $null $Port
                ownerPid = [int]$PID; ownerStartTicks = [long]$userProcessStartTicks
            }
        })
    }
    $userPort = 40192
    $userLease = & $createTerminalLease 'cdp' 0 0L `
        'none' '' '' $PID $userProcessStartTicks $userPort
    $userPositiveInspection = [pscustomobject]@{
        Success = $true; Known = $true; HasListeners = $true
        Source = 'Get-NetTCPConnection'
        Listeners = @([pscustomobject]@{
            LocalAddress = '127.0.0.1'; Port = $userPort; ProcessId = $PID
            ProcessStartTicks = $userProcessStartTicks; Source = 'Get-NetTCPConnection'
             ListenerOwnerKind = 'user-process'
             ListenerOwnerProof = 'diagnostics-process-start-v1'
             ListenerOwnerEvidence = 'system-diagnostics-process-starttime'
         })
         ListenerOwnerKind = 'user-process'
         ListenerOwnerProof = 'diagnostics-process-start-v1'
         ListenerOwnerEvidence = 'system-diagnostics-process-starttime'
         Error = ''
    }
    $absenceInspection = [pscustomobject]@{
        Success = $true; Known = $true; HasListeners = $false
        Source = 'Get-NetTCPConnection'
        Listeners = @()
         ListenerOwnerKind = 'none'; ListenerOwnerProof = ''
         ListenerOwnerEvidence = ''; Error = ''
    }
    $staleUserLease = $userLease | ConvertTo-Json -Depth 16 | ConvertFrom-Json
    $staleUserInspection = $userPositiveInspection | ConvertTo-Json -Depth 16 | ConvertFrom-Json
    $staleUserInspection.Listeners[0].ProcessStartTicks = $userProcessStartTicks + 1L
    $staleBefore = $staleUserLease | ConvertTo-Json -Depth 16 -Compress
    $staleRejected = $false
    try {
        & $module[0] {
            param($targetLease, $inspection, $previewContext, $previewOwner,
                $processId, $processStartTicks)
            Set-VerifierLeaseListenerInspection $targetLease $inspection `
                $previewContext $previewOwner $processId $processStartTicks
        } $staleUserLease $staleUserInspection $userPreviewContext `
            $userDirectOwner $PID $userProcessStartTicks
    } catch {
        $staleRejected = Test-VerifierInfrastructureError $_
    }
    if (-not $staleRejected -or
            ($staleUserLease | ConvertTo-Json -Depth 16 -Compress) -ne $staleBefore) {
        throw 'stale user-process listener Set path did not fail closed before mutation.'
    }
    & $module[0] {
        param($targetLease, $inspection, $previewContext, $previewOwner,
            $processId, $processStartTicks)
        Set-VerifierLeaseListenerInspection $targetLease $inspection `
            $previewContext $previewOwner $processId $processStartTicks
    } $userLease $userPositiveInspection $userPreviewContext $userDirectOwner `
        $PID $userProcessStartTicks
    if ([string]$userLease.listenerOwnerKind -ne 'user-process' -or
            [string]$userLease.listenerOwnerProof -ne 'diagnostics-process-start-v1' -or
            [string]$userLease.listenerOwnerEvidence -ne 'system-diagnostics-process-starttime') {
        throw 'user-process positive listener bind did not retain its exact owner proof.'
    }
    $missingKindLiveLease = $userLease | ConvertTo-Json -Depth 16 | ConvertFrom-Json
    $missingKindLiveInspection = $userPositiveInspection | ConvertTo-Json -Depth 16 |
        ConvertFrom-Json
    [void]$missingKindLiveInspection.PSObject.Properties.Remove('ListenerOwnerKind')
    [void]$missingKindLiveInspection.Listeners[0].PSObject.Properties.Remove('ListenerOwnerKind')
    $missingKindLiveBefore = $missingKindLiveLease | ConvertTo-Json -Depth 16 -Compress
    $missingKindLiveRejected = $false
    try {
        & $module[0] {
            param($targetLease, $inspection, $previewContext, $previewOwner,
                $processId, $processStartTicks)
            Set-VerifierLeaseListenerInspection $targetLease $inspection `
                $previewContext $previewOwner $processId $processStartTicks
        } $missingKindLiveLease $missingKindLiveInspection $userPreviewContext `
            $userDirectOwner $PID $userProcessStartTicks
    } catch { $missingKindLiveRejected = Test-VerifierInfrastructureError $_ }
    if (-not $missingKindLiveRejected -or
            ($missingKindLiveLease | ConvertTo-Json -Depth 16 -Compress) -ne
            $missingKindLiveBefore) {
        throw 'live listener consumer accepted or mutated an inspection missing ListenerOwnerKind.'
    }
    Invoke-VerifierParentLedgerWriterTupleCanary $userLease
    Invoke-VerifierCleanupArrayReaderCanary
    Invoke-VerifierDurableReaderSchemaCanary
    & $module[0] {
        param($targetLease, $inspection)
        Set-VerifierLeaseListenerInspection $targetLease $inspection
    } $userLease $absenceInspection
    if ([string]$userLease.listenerOwnerKind -ne 'user-process' -or
            [string]$userLease.listenerOwnerProof -ne 'diagnostics-process-start-v1' -or
            [string]$userLease.listenerOwnerEvidence -ne 'system-diagnostics-process-starttime') {
        throw 'user-process listener proof was erased by the pre-completion absence observation.'
    }
    $identityContext = [pscustomobject]@{
        RunId = $runId; RepositoryIdentity = $repositoryIdentity
        WorktreeRoot = $repositoryRoot; LeaseRecords = @($userLease)
    }
    $validSession = [pscustomobject]@{
        RunId = $runId; RepositoryIdentity = $repositoryIdentity
        WorktreeRoot = $repositoryRoot; RouteId = 'gate-b-listener-proof-route'
        RouteName = 'listener-proof'; BrowserPath = $previewScript
        Profile = (Join-Path $repositoryRoot 'gate-b-listener-proof-profile')
        CdpPort = $userPort; ProcessId = 0; ProcessStartTicks = 0
        ProcessParentProcessId = 0; ProcessParentProcessStartTicks = 0
        ProcessCommandLine = ''; TargetId = ''; ExpectedUrl = ''
        Status = 'cleaned'; CleanupResult = 'complete'; Error = ''
        ProfileInspectionFailed = $false; ProfileProcessScanCompleted = $true
        Lease = $userLease; RecoveryReceipt = $null; ContainmentLaunch = $null
    }
    # The terminal, unbound session shape remains durable only when it records
    # both the revoked receipt and its never-launched containment ledger.
    $validSession.RecoveryReceipt = [pscustomobject]@{
        Protocol = 'troubleshootjs-verifier-browser-recovery-v1'
        AuthorityToken = (('a' * 64) -join '')
        IssuedUtc = '2026-09-08T00:00:00.0000000Z'
        State = 'closed'; BoundUtc = ''; ClosedUtc = '2026-09-08T00:00:01.0000000Z'
        CloseAttempted = $false; CloseAttemptedUtc = ''
        RunId = $validSession.RunId; RepositoryIdentity = $validSession.RepositoryIdentity
        WorktreeRoot = $validSession.WorktreeRoot; RouteId = $validSession.RouteId
        RouteName = $validSession.RouteName; BrowserPath = $validSession.BrowserPath
        Profile = $validSession.Profile; LeaseId = $userLease.LeaseId
        CdpPort = $validSession.CdpPort; RootProcessId = 0; RootProcessStartTicks = 0L
        RootParentProcessId = 0; RootParentProcessStartTicks = 0L
        RootProcessCommandLine = ''; ListenerProcessId = 0; ListenerProcessStartTicks = 0L
    }
    $validSession.ContainmentLaunch = & $module[0] {
        param($fixtureContext, $fixtureSession)
        [pscustomobject]@{
            Protocol = 'troubleshootjs-verifier-browser-containment-launch-v1'
            JobName = Get-VerifierBrowserContainmentJobName $fixtureContext $fixtureSession
            State = 'unlaunched'; LaunchProcessId = 0; LaunchedUtc = ''
        }
    } $identityContext $validSession
    $absenceIdentity = Assert-VerifierDurableProcessIdentityTuple $validSession `
        -ProcessIdPropertyName 'ProcessId' `
        -ProcessStartPropertyName 'ProcessStartTicks' `
        -ParentProcessIdPropertyName 'ProcessParentProcessId' `
        -ParentProcessStartPropertyName 'ProcessParentProcessStartTicks' `
        -CommandLinePropertyName 'ProcessCommandLine' `
        -Label 'listener-proof absent browser session'
    if ($absenceIdentity.State -cne 'absent') {
        throw 'durable browser session explicit absence identity was not accepted.'
    }
    Assert-VerifierDurableBrowserSession $validSession $identityContext `
        'listener-proof absent browser session'
    $positiveSession = $validSession | ConvertTo-Json -Depth 16 | ConvertFrom-Json
    $positiveSession.ProcessId = $PID
    $positiveSession.ProcessStartTicks = $userProcessStartTicks
    $positiveSession.ProcessParentProcessId = $PID
    $positiveSession.ProcessParentProcessStartTicks = $userProcessStartTicks
    $positiveSession.ProcessCommandLine = 'powershell.exe -NoProfile'
    $positiveSession.Lease = $userLease
    $positiveSession.RecoveryReceipt.State = 'closed'
    $positiveSession.RecoveryReceipt.BoundUtc = '2026-09-08T00:00:01.0000000Z'
    $positiveSession.RecoveryReceipt.ClosedUtc = '2026-09-08T00:00:02.0000000Z'
    $positiveSession.RecoveryReceipt.CloseAttempted = $true
    $positiveSession.RecoveryReceipt.CloseAttemptedUtc = '2026-09-08T00:00:02.0000000Z'
    $positiveSession.RecoveryReceipt.RootProcessId = $PID
    $positiveSession.RecoveryReceipt.RootProcessStartTicks = $userProcessStartTicks
    $positiveSession.RecoveryReceipt.RootParentProcessId = $PID
    $positiveSession.RecoveryReceipt.RootParentProcessStartTicks = $userProcessStartTicks
    $positiveSession.RecoveryReceipt.RootProcessCommandLine = 'powershell.exe -NoProfile'
    $positiveSession.RecoveryReceipt.ListenerProcessId = $PID
    $positiveSession.RecoveryReceipt.ListenerProcessStartTicks = $userProcessStartTicks
    $positiveSession.ContainmentLaunch.State = 'launched'
    $positiveSession.ContainmentLaunch.LaunchProcessId = $PID
    $positiveSession.ContainmentLaunch.LaunchedUtc = '2026-09-08T00:00:00.0000000Z'
    $positiveIdentity = Assert-VerifierDurableProcessIdentityTuple $positiveSession `
        -ProcessIdPropertyName 'ProcessId' `
        -ProcessStartPropertyName 'ProcessStartTicks' `
        -ParentProcessIdPropertyName 'ProcessParentProcessId' `
        -ParentProcessStartPropertyName 'ProcessParentProcessStartTicks' `
        -CommandLinePropertyName 'ProcessCommandLine' `
        -Label 'listener-proof positive browser session'
    if ($positiveIdentity.State -cne 'positive') {
        throw 'durable browser session positive identity was not accepted.'
    }
    Assert-VerifierDurableBrowserSession $positiveSession $identityContext `
        'listener-proof positive browser session'
    $malformedCompletionSession = $validSession | ConvertTo-Json -Depth 16 | ConvertFrom-Json
    $malformedCompletionSession.ProfileProcessScanCompleted = $false
    $malformedCompletionBefore = $malformedCompletionSession | ConvertTo-Json -Depth 16 -Compress
    $malformedCompletionRejected = $false
    try {
        Assert-VerifierDurableBrowserSession $malformedCompletionSession $identityContext `
            'listener-proof malformed completion session'
    } catch { $malformedCompletionRejected = Test-VerifierInfrastructureError $_ }
    if (-not $malformedCompletionRejected -or
            ($malformedCompletionSession | ConvertTo-Json -Depth 16 -Compress) -ne
            $malformedCompletionBefore) {
        throw 'malformed browser-session completion was not rejected without mutation.'
    }
    $validProfile = [pscustomobject]@{
        owner = 'run'; runId = $runId; repositoryIdentity = $repositoryIdentity
        worktreeRoot = $repositoryRoot; routeId = 'gate-b-listener-proof-route'
        routeName = 'listener-proof'; profile = $validSession.Profile
        cdpLeasePath = $userClaimPath; browserPath = $previewScript
        processCommandLine = ''; targetId = ''; expectedUrl = ''
        expectedRunMarker = 'tsjVerifierRun=' + $runId
        expectedRouteMarker = 'tsjVerifierRoute=gate-b-listener-proof-route'
        status = 'cleaned'; cleanupResult = 'complete'; error = ''
        cdpPort = $userPort; processId = 0; processStartTicks = 0
        processParentProcessId = 0; processParentProcessStartTicks = 0
        profileProcessScanCompleted = $true; profileInspectionFailed = $false
    }
    Assert-VerifierIntegratedDurableProfileSchema $validProfile `
        'listener-proof absent integrated browser profile'
    $positiveProfile = $validProfile | ConvertTo-Json -Depth 16 | ConvertFrom-Json
    $positiveProfile.processId = $PID
    $positiveProfile.processStartTicks = $userProcessStartTicks
    $positiveProfile.processParentProcessId = $PID
    $positiveProfile.processParentProcessStartTicks = $userProcessStartTicks
    $positiveProfile.processCommandLine = 'powershell.exe -NoProfile'
    Assert-VerifierIntegratedDurableProfileSchema $positiveProfile `
        'listener-proof positive integrated browser profile'
    foreach ($identityVariant in @(
            [pscustomobject]@{ Name = 'absent PID with positive start'; ProcessId = 0; ProcessStartTicks = 123L }
            [pscustomobject]@{ Name = 'positive PID with absent start'; ProcessId = 123; ProcessStartTicks = 0L }
            [pscustomobject]@{ Name = 'positive PID with absent parent'; ProcessId = 123; ProcessStartTicks = 123L; ProcessParentProcessId = 0; ProcessParentProcessStartTicks = 0L; ProcessCommandLine = '' }
        )) {
        $sessionVariant = $validSession | ConvertTo-Json -Depth 16 | ConvertFrom-Json
        $sessionVariant.ProcessId = $identityVariant.ProcessId
        $sessionVariant.ProcessStartTicks = $identityVariant.ProcessStartTicks
        if ($identityVariant.PSObject.Properties['ProcessParentProcessId']) {
            $sessionVariant.ProcessParentProcessId = $identityVariant.ProcessParentProcessId
            $sessionVariant.ProcessParentProcessStartTicks = $identityVariant.ProcessParentProcessStartTicks
            $sessionVariant.ProcessCommandLine = $identityVariant.ProcessCommandLine
        }
        $sessionRejected = $false
        try {
            Assert-VerifierDurableBrowserSession $sessionVariant $identityContext `
                ('listener-proof malformed ' + $identityVariant.Name)
        } catch { $sessionRejected = Test-VerifierInfrastructureError $_ }
        if (-not $sessionRejected) {
            throw "durable browser session accepted $($identityVariant.Name)."
        }
        $profileVariant = $validProfile | ConvertTo-Json -Depth 16 | ConvertFrom-Json
        $profileVariant.processId = $identityVariant.ProcessId
        $profileVariant.processStartTicks = $identityVariant.ProcessStartTicks
        if ($identityVariant.PSObject.Properties['ProcessParentProcessId']) {
            $profileVariant.processParentProcessId = $identityVariant.ProcessParentProcessId
            $profileVariant.processParentProcessStartTicks = $identityVariant.ProcessParentProcessStartTicks
            $profileVariant.processCommandLine = $identityVariant.ProcessCommandLine
        }
        $profileRejected = $false
        try {
            Assert-VerifierIntegratedDurableProfileSchema $profileVariant `
                ('listener-proof malformed integrated ' + $identityVariant.Name)
        } catch { $profileRejected = Test-VerifierInfrastructureError $_ }
        if (-not $profileRejected) {
            throw "integrated browser profile accepted $($identityVariant.Name)."
        }
    }
    $terminalInvariantVariants = @(
        [pscustomobject]@{ Name = 'unregistered'; Field = 'registered'; Value = $false }
        [pscustomobject]@{ Name = 'release blocked'; Field = 'releaseBlocked'; Value = $true }
        [pscustomobject]@{ Name = 'incomplete release journal'; Field = 'releaseJournalState'; Value = 'pre-delete' }
        [pscustomobject]@{ Name = 'mutex not released'; Field = 'mutexReleased'; Value = $false }
        [pscustomobject]@{ Name = 'listener inspection incomplete'; Field = 'listenerInspectionKnown'; Value = $false }
        [pscustomobject]@{ Name = 'process proof incomplete'; Field = 'processTerminationProven'; Value = $false }
        [pscustomobject]@{ Name = 'release block reason retained'; Field = 'releaseBlockReason'; Value = 'ownership uncertain' }
        [pscustomobject]@{ Name = 'pathless terminal record'; Field = 'path'; Value = '' }
    )
    foreach ($identityVariant in $terminalInvariantVariants) {
        $terminalVariant = $userLease | ConvertTo-Json -Depth 16 | ConvertFrom-Json
        $terminalVariant.($identityVariant.Field) = $identityVariant.Value
        $terminalBefore = $terminalVariant | ConvertTo-Json -Depth 16 -Compress
        if (Test-VerifierIntegratedLeaseTerminal $terminalVariant $userClaimPath) {
            throw "strict terminal predicate accepted $($identityVariant.Name)."
        }
        if (($terminalVariant | ConvertTo-Json -Depth 16 -Compress) -ne $terminalBefore) {
            throw "strict terminal predicate mutated $($identityVariant.Name)."
        }
    }
    $userTerminalSerializedLease = $userLease | ConvertTo-Json -Depth 16 | ConvertFrom-Json
    if (-not (Test-VerifierIntegratedLeaseTerminal $userTerminalSerializedLease $userClaimPath)) {
        throw 'completed user-process listener lease did not validate after proof-preserving absence.'
    }

    $forgedPid4UserLease = $userLease | ConvertTo-Json -Depth 16 | ConvertFrom-Json
    $forgedPid4UserLease.listenerProcessId = 4
    $forgedPid4UserLease.listenerProcessStartTicks = $userProcessStartTicks
    foreach ($copyName in @('ledger', 'manifest')) {
        if (Test-VerifierIntegratedListenerOwnerSchema $forgedPid4UserLease) {
            throw "paired $copyName reader accepted a forged user-process PID 4 owner tuple."
        }
        if (Test-VerifierIntegratedLeaseTerminal $forgedPid4UserLease $userClaimPath) {
            throw "paired $copyName terminal reader accepted a forged user-process PID 4 owner tuple."
        }
    }

    $terminalReleaseLease = $userLease | ConvertTo-Json -Depth 16 | ConvertFrom-Json
    $terminalReleaseLease.listenerProcessId = 4
    $terminalReleaseLease.listenerProcessStartTicks = $userProcessStartTicks
    $terminalReleaseBefore = $terminalReleaseLease | ConvertTo-Json -Depth 16 -Compress
    $terminalReleaseRejected = $false
    try {
        Release-VerifierPortLease $null $terminalReleaseLease
    } catch {
        $terminalReleaseRejected = Test-VerifierInfrastructureError $_
    }
    if (-not $terminalReleaseRejected -or
            ($terminalReleaseLease | ConvertTo-Json -Depth 16 -Compress) -ne $terminalReleaseBefore) {
        throw 'terminal release accepted or mutated a malformed user-process PID 4 owner tuple.'
    }
    $releaseBoundaryContext = [pscustomobject]@{
        RunId = $runId; RepositoryIdentity = $repositoryIdentity
        WorktreeRoot = $repositoryRoot; PortLeaseRoot = $repositoryRoot
    }
    $missingKindCleanupLease = $userTerminalSerializedLease | ConvertTo-Json -Depth 16 |
        ConvertFrom-Json
    [void]$missingKindCleanupLease.PSObject.Properties.Remove('listenerOwnerKind')
    $missingKindCleanupBefore = $missingKindCleanupLease | ConvertTo-Json -Depth 16 -Compress
    $missingKindCleanupRejected = $false
    try {
        Release-VerifierPortLease $releaseBoundaryContext $missingKindCleanupLease
    } catch { $missingKindCleanupRejected = Test-VerifierInfrastructureError $_ }
    if (-not $missingKindCleanupRejected -or
            ($missingKindCleanupLease | ConvertTo-Json -Depth 16 -Compress) -ne
            $missingKindCleanupBefore) {
        throw 'cleanup listener consumer accepted or mutated a lease missing ListenerOwnerKind.'
    }
    foreach ($releaseVariantDefinition in @(
            [pscustomobject]@{ Name = 'pathless'; Field = 'path'; Value = '' }
            [pscustomobject]@{ Name = 'release-blocked'; Field = 'releaseBlocked'; Value = $true }
        )) {
        $releaseVariant = $userTerminalSerializedLease | ConvertTo-Json -Depth 16 | ConvertFrom-Json
        $releaseVariant.($releaseVariantDefinition.Field) = $releaseVariantDefinition.Value
        $releaseVariantBefore = $releaseVariant | ConvertTo-Json -Depth 16 -Compress
        $releaseVariantRejected = $false
        try {
            Release-VerifierPortLease $releaseBoundaryContext $releaseVariant
        } catch { $releaseVariantRejected = Test-VerifierInfrastructureError $_ }
        if (-not $releaseVariantRejected -or
                ($releaseVariant | ConvertTo-Json -Depth 16 -Compress) -ne $releaseVariantBefore -or
                -not (Test-VerifierLeaseNeedsCleanup $releaseVariant)) {
            throw "terminal $($releaseVariantDefinition.Name) release did not fail closed without mutation or retained cleanup need."
        }
    }

    $kernelPort = 40193
    $kernelPreviewClaimName = Get-VerifierPortMutexName $null $kernelPort
    $kernelPreviewMutex = [Threading.Mutex]::new($false, $kernelPreviewClaimName)
    $kernelPreviewMutexHeld = $kernelPreviewMutex.WaitOne(0)
    if (-not $kernelPreviewMutexHeld) {
        Throw-VerifierInfrastructure 'listener proof canary could not acquire its kernel preview mutex.'
    }
    & $module[0] {
        param($claimName, $ownerRunId, $mutex)
        $script:VerifierHeldPortClaims[$claimName] = $ownerRunId
        $script:VerifierHeldPortMutexes[$claimName] = $mutex
    } $kernelPreviewClaimName $runId $kernelPreviewMutex
    $kernelCommandLine = 'powershell.exe -File "' + $previewScript + '" -Port ' +
        [string]$kernelPort + ' -VerifierRunId ' + $runId + ' -VerifierNonce ' + $nonce
    $kernelPreviewLease = [pscustomobject]@{
        Kind = 'preview'; Port = $kernelPort; Status = 'bound'; ClaimState = 'bound'
        RunId = $runId; RepositoryIdentity = $repositoryIdentity
        WorktreeRoot = $repositoryRoot
        LeaseId = 'gate-b-listener-proof-kernel-lease'
        ClaimName = Get-VerifierPortMutexName $null $kernelPort
        Path = $kernelClaimPath
        ProfilePath = ''; BrowserPath = ''; BindValidatedUtc = ''; ReleasedUtc = ''
        ReleaseState = 'active'; ReleaseJournalState = 'active'
        ReleaseBlocked = $false; ReleaseBlockReason = ''; MutexReleased = $false
        Registered = $true
        ClaimOwnerPid = $PID; ClaimOwnerStartTicks = $userProcessStartTicks
        BoundProcessId = $PID; BoundProcessStartTicks = $userProcessStartTicks
        ListenerProcessId = 4; ListenerProcessStartTicks = $null
        ListenerOwnerKind = 'kernel-transport'
        ListenerOwnerProof = 'run-owned-preview-http-sys-v1'
        ListenerOwnerEvidence = 'pid-4-system-http-sys'
        ListenerInspectionSuccess = $true; ListenerInspectionKnown = $true
        ListenerHasListeners = $true; ListenerAbsent = $false
        ListenerInspectionUtc = ''
        ProcessProofRequired = $true
        ProcessTerminationProven = $false; ProcessAbsent = $false
        ClaimMutex = $kernelPreviewMutex
    }
    $kernelPreviewContext = [pscustomobject]@{
        Server = $null; WorktreeRoot = $repositoryRoot
        RepositoryIdentity = $repositoryIdentity; RunId = $runId
        PreviewNonce = $nonce
    }
    $kernelPreviewOwner = [pscustomobject]([ordered]@{
        Owner = 'run'; BaseUrl = 'http://127.0.0.1:' + [string]$kernelPort
         Port = $kernelPort; ProcessId = $PID; ProcessStartTicks = (Get-VerifierProcessStartTicks (Get-Process -Id $PID))
        ProcessParentProcessId = 65403; ProcessParentProcessStartTicks = 234503L
        ProcessCommandLine = $kernelCommandLine; Script = $previewScript
        RepositoryRoot = $repositoryRoot; WebRoot = $webRoot
        IdentityProtocol = 'troubleshootjs-preview-identity-v1'
        IdentityVerified = $true; CallerOwned = $false
        RunId = $runId; Nonce = $nonce; State = 'run-owned-verified'
        Lease = $kernelPreviewLease; Process = (Get-Process -Id $PID)
        StdoutLog = (Join-Path $repositoryRoot 'gate-b-listener-proof-kernel-stdout.log')
        StderrLog = (Join-Path $repositoryRoot 'gate-b-listener-proof-kernel-stderr.log')
        CleanupResult = 'pending'; Error = ''
        ProcessIdentityKnown = $true; OwnershipUncertain = $false
        ProcessTerminationProven = $false; ProcessAbsent = $false
        ListenerInspectionProven = $true; ListenerAbsent = $false
    })
    $kernelPreviewContext.Server = $kernelPreviewOwner
    $kernelTerminalLease = & $createTerminalLease 'preview' 4 $null `
        'kernel-transport' 'run-owned-preview-http-sys-v1' 'pid-4-system-http-sys' `
        65402 234502L $kernelPort
    $kernelLease = & $createTerminalLease 'preview' 0 0L `
        'none' '' '' 65402 234502L $kernelPort
    $kernelPositiveInspection = [pscustomobject]@{
        Success = $true; Known = $true; HasListeners = $true
        Source = 'Get-NetTCPConnection'
        Listeners = @([pscustomobject]@{
            LocalAddress = '127.0.0.1'; Port = $kernelPort; ProcessId = 4
            ProcessStartTicks = $null; Source = 'Get-NetTCPConnection'
             ListenerOwnerKind = 'kernel-transport'
             ListenerOwnerProof = 'run-owned-preview-http-sys-v1'
             ListenerOwnerEvidence = 'pid-4-system-http-sys'
         })
         ListenerOwnerKind = 'kernel-transport'
         ListenerOwnerProof = 'run-owned-preview-http-sys-v1'
         ListenerOwnerEvidence = 'pid-4-system-http-sys'
         Error = ''
    }
    # Kernel listener consumers no longer perform a second semantic
    # authorization. Create the opaque capability at the initial preview
    # boundary and carry it into the setter below.
    $kernelAuthorizationProof = & $module[0] {
        param($previewContext, $previewOwner, $port)
        New-VerifierRunOwnedPreviewHttpSysAuthorizationProof `
            $previewContext $previewOwner $port
    } $kernelPreviewContext $kernelPreviewOwner $kernelPort
    if ($null -eq $kernelAuthorizationProof) {
        throw 'kernel transport proof canary could not create its initial opaque authorization proof.'
    }
    & $module[0] {
        param($targetLease, $inspection, $previewContext, $previewOwner,
            $authorizationProof)
        Set-VerifierLeaseListenerInspection $targetLease $inspection `
            $previewContext $previewOwner $null $null $authorizationProof
    } $kernelLease $kernelPositiveInspection $kernelPreviewContext `
        $kernelPreviewOwner $kernelAuthorizationProof
    if ([string]$kernelLease.listenerOwnerKind -ne 'kernel-transport' -or
            [string]$kernelLease.listenerOwnerProof -ne 'run-owned-preview-http-sys-v1' -or
            [string]$kernelLease.listenerOwnerEvidence -ne 'pid-4-system-http-sys' -or
            $null -ne $kernelLease.listenerProcessStartTicks) {
        throw 'kernel transport positive listener bind did not retain its null-start owner proof.'
    }
    & $module[0] {
        param($targetLease, $inspection)
        Set-VerifierLeaseListenerInspection $targetLease $inspection
    } $kernelLease $absenceInspection
    if ([string]$kernelLease.listenerOwnerKind -ne 'kernel-transport' -or
            [string]$kernelLease.listenerOwnerProof -ne 'run-owned-preview-http-sys-v1' -or
            [string]$kernelLease.listenerOwnerEvidence -ne 'pid-4-system-http-sys' -or
            $null -ne $kernelLease.listenerProcessStartTicks) {
        throw 'kernel transport listener proof was erased by a positive absence observation.'
    }
    $kernelServer = [pscustomobject]([ordered]@{
        owner = 'run'; baseUrl = 'http://127.0.0.1:' + [string]$kernelPort
        repositoryIdentity = $repositoryIdentity; worktreeRoot = $repositoryRoot
        repositoryRoot = $repositoryRoot; webRoot = $webRoot
        identityProtocol = 'troubleshootjs-preview-identity-v1'
        identityVerified = $true; callerOwned = $false
        port = $kernelPort; processId = 65402; processStartTicks = 234502L
        processParentProcessId = 65403; processParentProcessStartTicks = 234503L
        processCommandLine = $kernelCommandLine; script = $previewScript
        runId = $runId; nonce = $nonce; state = 'cleaned'
        cleanupResult = 'complete'
        stdoutLog = ''; stderrLog = ''; error = ''
        leaseId = $kernelTerminalLease.leaseId
        leaseKind = 'preview'
        leaseClaimName = $kernelTerminalLease.claimName
        leaseClaimState = 'released'
        leaseReleaseState = 'complete'
        leaseReleaseJournalState = 'complete'
        leasePath = $kernelClaimPath
        leaseOwnerPid = [int]$PID
        leaseOwnerStartTicks = [long]$userProcessStartTicks
        leaseListenerAbsent = $true
        leaseProcessProofRequired = $true
        leaseListenerOwnerKind = 'kernel-transport'
        leaseListenerOwnerProof = 'run-owned-preview-http-sys-v1'
        leaseListenerOwnerEvidence = 'pid-4-system-http-sys'
        processIdentityKnown = $true; ownershipUncertain = $false
        processTerminationProven = $true; processAbsent = $true
        listenerInspectionProven = $true; listenerAbsent = $true
    })
    $kernelTerminalSerializedLease = $kernelTerminalLease | ConvertTo-Json -Depth 16 | ConvertFrom-Json
    if (-not (Test-VerifierIntegratedLeaseTerminal $kernelTerminalSerializedLease $kernelClaimPath `
            $kernelServer $runId $repositoryIdentity $repositoryRoot)) {
        throw 'completed kernel-transport listener lease did not validate after proof-preserving absence.'
    }
    Invoke-GateBIntegratedKernelLedgerCanary $repositoryRoot $previewScript $webRoot

    foreach ($remainingLease in @($userLease, $kernelLease)) {
        $remainingVariant = $remainingLease | ConvertTo-Json -Depth 16 | ConvertFrom-Json
        $remainingVariant.listenerHasListeners = $true
        $remainingVariant.listenerAbsent = $false
        $remainingPath = if ([int]$remainingVariant.listenerProcessId -eq 4) {
            $kernelClaimPath
        } else { $userClaimPath }
        $remainingServer = if ([int]$remainingVariant.listenerProcessId -eq 4) {
            $kernelServer
        } else { $null }
        if (Test-VerifierIntegratedLeaseTerminal $remainingVariant $remainingPath `
                $remainingServer $runId $repositoryIdentity $repositoryRoot) {
            throw 'terminal validation accepted a remaining listener claim.'
        }
    }

    $ownerProofFields = @('listenerOwnerKind', 'listenerOwnerProof',
        'listenerOwnerEvidence')
    foreach ($copyName in @('ledger', 'manifest')) {
        foreach ($ownerProofField in $ownerProofFields) {
            $userVariant = $userLease | ConvertTo-Json -Depth 16 | ConvertFrom-Json
            [void]$userVariant.PSObject.Properties.Remove($ownerProofField)
            if (Test-VerifierIntegratedLeaseTerminal $userVariant $userClaimPath) {
                throw "terminal validation accepted $copyName user-process copy missing $ownerProofField."
            }
            $kernelVariant = $kernelLease | ConvertTo-Json -Depth 16 | ConvertFrom-Json
            [void]$kernelVariant.PSObject.Properties.Remove($ownerProofField)
            if (Test-VerifierIntegratedLeaseTerminal $kernelVariant $kernelClaimPath `
                    $kernelServer $runId $repositoryIdentity $repositoryRoot) {
                throw "terminal validation accepted $copyName kernel-transport copy missing $ownerProofField."
            }
        }
    }
    foreach ($variantDefinition in @(
        [pscustomobject]@{ Name = 'scalar-array owner kind'; Field = 'listenerOwnerKind'; Value = @('user-process') },
        [pscustomobject]@{ Name = 'legacy user proof'; Field = 'listenerOwnerProof'; Value = 'legacy-proof' },
        [pscustomobject]@{ Name = 'scalar-array listener start'; Field = 'listenerProcessStartTicks'; Value = @([long]234501) },
        [pscustomobject]@{ Name = 'user/kernel tuple mismatch'; Field = 'listenerOwnerProof'; Value = 'run-owned-preview-http-sys-v1' }
    )) {
        $userVariant = $userLease | ConvertTo-Json -Depth 16 | ConvertFrom-Json
        $userVariant.($variantDefinition.Field) = $variantDefinition.Value
        if (Test-VerifierIntegratedLeaseTerminal $userVariant $userClaimPath) {
            throw "terminal validation accepted $($variantDefinition.Name)."
        }
    }
    foreach ($variantDefinition in @(
        [pscustomobject]@{ Name = 'kernel wrong PID'; Field = 'listenerProcessId'; Value = 5 },
        [pscustomobject]@{ Name = 'kernel fabricated start'; Field = 'listenerProcessStartTicks'; Value = 1L },
        [pscustomobject]@{ Name = 'kernel owner kind changed'; Field = 'listenerOwnerKind'; Value = 'user-process' }
    )) {
        $kernelVariant = $kernelLease | ConvertTo-Json -Depth 16 | ConvertFrom-Json
        $kernelVariant.($variantDefinition.Field) = $variantDefinition.Value
        if (Test-VerifierIntegratedLeaseTerminal $kernelVariant $kernelClaimPath `
                $kernelServer $runId $repositoryIdentity $repositoryRoot) {
            throw "terminal validation accepted $($variantDefinition.Name)."
        }
    }
    $serverTupleVariant = $kernelServer | ConvertTo-Json -Depth 16 | ConvertFrom-Json
    $serverTupleVariant.leaseListenerOwnerKind = 'user-process'
    if (Test-VerifierIntegratedLeaseTerminal $kernelTerminalSerializedLease $kernelClaimPath `
            $serverTupleVariant $runId $repositoryIdentity $repositoryRoot) {
        throw 'terminal validation accepted a server/lease owner tuple mismatch.'
    }
    foreach ($pairDefinition in @(
        [pscustomobject]@{ Name = 'RunId'; Field = 'runId'; Value = 'gate-b-string-pair-run'; Mismatch = 'Gate-b-string-pair-run' }
        [pscustomobject]@{ Name = 'protocol'; Field = 'protocol'; Value = 'troubleshootjs-verifier-port-claim-v1'; Mismatch = 'Troubleshootjs-verifier-port-claim-v1' }
        [pscustomobject]@{ Name = 'lifecycle'; Field = 'releaseState'; Value = 'complete'; Mismatch = 'Complete' }
        [pscustomobject]@{ Name = 'owner'; Field = 'owner'; Value = 'run'; Mismatch = 'Run' }
        [pscustomobject]@{ Name = 'listener owner kind'; Field = 'listenerOwnerKind'; Value = 'user-process'; Mismatch = 'User-process' }
    )) {
        $ledgerPair = [pscustomobject]@{}
        $manifestPair = [pscustomobject]@{}
        Add-Member -InputObject $ledgerPair -MemberType NoteProperty `
            -Name $pairDefinition.Field -Value $pairDefinition.Value
        Add-Member -InputObject $manifestPair -MemberType NoteProperty `
            -Name $pairDefinition.Field -Value $pairDefinition.Mismatch
        $pairRejected = $false
        try {
            [void](Assert-VerifierDurableStringPair $ledgerPair $manifestPair `
                $pairDefinition.Field)
        } catch {
            $pairRejected = Test-VerifierInfrastructureError $_
        }
        if (-not $pairRejected) {
            throw "durable string pair accepted a case-only $($pairDefinition.Name) mismatch."
        }
    }
    foreach ($typeDefinition in @(
        [pscustomobject]@{ Name = 'numeric'; Value = 7 }
        [pscustomobject]@{ Name = 'array'; Value = @('gate-b-string-pair-run') }
        [pscustomobject]@{ Name = 'object'; Value = [pscustomobject]@{ Value = 'gate-b-string-pair-run' } }
    )) {
        $ledgerPair = [pscustomobject]@{ runId = 'gate-b-string-pair-run' }
        $manifestPair = [pscustomobject]@{ runId = $typeDefinition.Value }
        $pairRejected = $false
        try {
            [void](Assert-VerifierDurableStringPair $ledgerPair $manifestPair 'runId')
        } catch {
            $pairRejected = Test-VerifierInfrastructureError $_
        }
        if (-not $pairRejected) {
            throw "durable string pair accepted a $($typeDefinition.Name) type mismatch."
        }
    }
    Write-Host 'PASS:listener owner proof bind/absence preservation, user/kernel terminal validation, actual paired kernel ledger/manifest completion-reader absence proof, paired forged user/PID4 rejection, malformed terminal release fail-closed/no-mutation, canonical delegation, remaining-listener fail-closed behavior, paired tuple equality, and malformed scalar/legacy/mismatch rejection'
    } finally {
        if ($kernelPreviewClaimName -and $null -ne $kernelPreviewMutex) {
            & $module[0] {
                param($claimName, $ownerRunId, $mutex)
                if ($script:VerifierHeldPortClaims.ContainsKey($claimName) -and
                        [string]$script:VerifierHeldPortClaims[$claimName] -eq $ownerRunId) {
                    [void]$script:VerifierHeldPortClaims.Remove($claimName)
                }
                if ($script:VerifierHeldPortMutexes.ContainsKey($claimName) -and
                        [object]::ReferenceEquals($script:VerifierHeldPortMutexes[$claimName], $mutex)) {
                    [void]$script:VerifierHeldPortMutexes.Remove($claimName)
                }
            } $kernelPreviewClaimName $runId $kernelPreviewMutex
        }
        if ($kernelPreviewMutexHeld) {
            try { [void]$kernelPreviewMutex.ReleaseMutex() } catch { }
        }
        if ($null -ne $kernelPreviewMutex) {
            try { $kernelPreviewMutex.Dispose() } catch { }
        }
    }
}

function Invoke-Task43ForcedNegativeContractCanaries() {
    $priorCandidateSha = $script:Task43PCandidateSha
    $script:Task43PCandidateSha = ('c' * 40)
    $priorContext = $script:VerifierContext
    $priorRouteId = $script:VerifierCurrentRouteId
    $priorProof = $script:Task43ForcedNegativeProof
    $priorSourceDefinition = $script:Task43PSourceDefinition
    $script:Task43PSourceDefinition = $null
    $priorObserved = $script:task43ExpectedFailureObserved
    $priorRoutePassed = $script:task43ExpectedFailureRoutePassed
    $priorFailureExit = $script:VerifierFailureExitCode
    $priorFailureKind = $script:VerifierFailureKind
    $priorFailureMessage = $script:VerifierFailureMessage
    $legacyMarker = 'FAIL:task43-forced-negative-canary'
    $task43pMarker = 'FAIL:task43p-forced-negative-canary'
    $runId = 'gate-b-forced-negative-run'
    $routeId = 'gate-b-forced-negative-route'
    $script:VerifierContext = [pscustomobject]@{ RunId = $runId }

    $reset = {
        param([string]$Marker)
        $script:VerifierFailureExitCode = 0
        $script:VerifierFailureKind = ''
        $script:VerifierFailureMessage = ''
        Reset-Task43ForcedNegativeProof $Marker `
            (Get-Task43ForcedNegativeExpectedRoute $Marker)
        $script:VerifierCurrentRouteId = $routeId
        Set-Task43ForcedNegativeRouteIdentity
    }
    $setValid = {
        param([string]$Marker, [string]$Diagnostic, [string]$BaselineHead)
        & $reset $Marker
        if ([String]::IsNullOrWhiteSpace($Diagnostic) -and
                (Test-Task43PForcedNegativeMarker $Marker)) {
            $Diagnostic = Get-Task43ForcedNegativeExpectedDiagnostic $Marker
        }
        Set-Task43ForcedNegativeMarkerObserved $Marker
        if (-not (isExpectedTask43ForcedFailureDiagnostic $Diagnostic $Marker `
                $runId $routeId $BaselineHead)) {
            throw "deterministic exact forced-negative diagnostic canary did not match: $Marker"
        }
        Set-Task43ForcedNegativeAnchoredDiagnostic $Diagnostic $BaselineHead
        Set-Task43ForcedNegativeRoutePassedAfterCleanup
    }
    try {
        if ((Resolve-VerifierIntegratedChildShellStatus $true 1) -ne 2 -or
                (Resolve-VerifierIntegratedChildShellStatus $false 1) -ne 2 -or
                (Resolve-VerifierIntegratedChildShellStatus $true 'stale') -ne 2 -or
                (Resolve-VerifierIntegratedChildShellStatus $true 0) -ne 0 -or
                (Resolve-VerifierIntegratedChildShellStatus $true $null) -ne 0) {
            throw 'integrated child $?/$LASTEXITCODE ambiguity did not remain infrastructure.'
        }
        Write-Host 'PASS:integrated child $?/$LASTEXITCODE stale-status ambiguity -> exit 2'
        # Construct the valid proof only after the reset so its nonce/request
        # identity cannot be carried across routes.
        & $setValid $task43pMarker '' $script:Task43PCandidateSha
        $task43pDiagnostic = Get-Task43ForcedNegativeExpectedDiagnostic $task43pMarker
        if ((Get-Task43ForcedNegativeRouteExitCode $task43pMarker) -ne 1) {
            throw 'exact anchored Task43P proof did not resolve to application exit 1.'
        }
        Set-Task43ForcedNegativeFinalCleanupProven $true
        if ((Resolve-Task43ForcedNegativeTopLevelExitCode 1 $task43pMarker) -ne 1) {
            throw 'exact anchored Task43P proof did not survive the independent top-level gate.'
        }
        $validLedgerProof = Get-Task43ForcedNegativeProofRecord | ConvertTo-Json -Depth 8 | ConvertFrom-Json
        Assert-IntegratedChildOutputContract 'Gate B forced-negative proof' 1 1 `
            @('EXPECTED FAILURE task43p forced-negative canary') $validLedgerProof $task43pMarker
        Write-Host 'PASS:forced-negative exact anchored Java proof and expected-route ledger -> exit 1'

        foreach ($wrongBaseline in @($script:Task43PHistoricalBaselineSha,
                '0000000000000000000000000000000000000000', '')) {
            if (isExpectedTask43ForcedFailureDiagnostic $task43pDiagnostic $task43pMarker `
                    $runId $routeId $wrongBaseline) {
                throw 'wrong candidate accepted an otherwise valid forced-negative diagnostic.'
            }
        }
        Write-Host 'PASS:forced-negative historical baseline, foreign, and missing candidate rejected'

        & $reset $task43pMarker
        try { Throw-VerifierInfrastructure 'synthetic preview died before application' } catch {
            Set-VerifierFailure $_ 'Gate B forced-negative preview startup' -Quiet
        }
        if ((Get-Task43ForcedNegativeRouteExitCode $task43pMarker) -ne 2 -or
                (Resolve-Task43ForcedNegativeTopLevelExitCode 1 $task43pMarker) -ne 2) {
            throw 'preview-before-application forced-negative uncertainty was not exit 2.'
        }
        Write-Host 'PASS:forced-negative preview-before-application uncertainty -> exit 2'

        & $reset $task43pMarker
        Set-Task43ForcedNegativeMarkerObserved $task43pMarker
        $forgedPageDiagnostic = 'Console failure: exception in runCircuit java.lang.IllegalStateException: ' +
            'Generated board verification failed for led/controlled-indicator, seed 3: ' +
            $task43pMarker.Substring(5)
        if (isExpectedTask43ForcedFailureDiagnostic $forgedPageDiagnostic $task43pMarker `
                $runId $routeId $script:Task43PCandidateSha) {
            throw 'forged-page marker/console canary was accepted without the Java request proof.'
        }
        if ((Get-Task43ForcedNegativeRouteExitCode $task43pMarker) -ne 2) {
            throw 'forged-page marker/console canary did not resolve to exit 2.'
        }
        Write-Host 'PASS:forged-page Task43P marker/console without Java request proof -> exit 2'

        foreach ($uncertainty in @(
            [pscustomobject]@{ Name = 'browser identity unavailable'; Message = 'synthetic browser identity unavailable' }
            [pscustomobject]@{ Name = 'WMI/CIM uncertainty'; Message = 'synthetic WMI/CIM process identity uncertainty' }
        )) {
            & $reset $task43pMarker
            try {
                $uncertain = [System.UnauthorizedAccessException]::new($uncertainty.Message)
                throw $uncertain
            } catch {
                Set-VerifierFailure $_ ('Gate B forced-negative ' + $uncertainty.Name) -Quiet
            }
            if ((Get-Task43ForcedNegativeRouteExitCode $task43pMarker) -ne 2) {
                throw "$($uncertainty.Name) was not dominant over forced-negative application exit."
            }
            Write-Host ("PASS:forced-negative $($uncertainty.Name) -> exit 2")
        }

        & $reset $task43pMarker
        if (isExpectedTask43ForcedFailureDiagnostic '' $task43pMarker $runId $routeId `
                $script:Task43PCandidateSha) {
            throw 'missing forced-negative marker was accepted as expected proof.'
        }
        if ((Get-Task43ForcedNegativeRouteExitCode $task43pMarker) -ne 2) {
            throw 'missing forced-negative marker did not resolve to exit 2.'
        }
        Write-Host 'PASS:forced-negative marker missing -> nonexpected/exit 2'

        & $reset $task43pMarker
        Set-Task43ForcedNegativeMarkerObserved $task43pMarker
        $task43pDiagnostic = Get-Task43ForcedNegativeExpectedDiagnostic $task43pMarker
        $wrongDiagnostic = $task43pDiagnostic.Replace($task43pMarker.Substring(5), 'wrong-java-text')
        if (isExpectedTask43ForcedFailureDiagnostic $wrongDiagnostic $task43pMarker `
                $runId $routeId $script:Task43PCandidateSha) {
            throw 'wrong Java forced-negative text was accepted as anchored proof.'
        }
        if ((Get-Task43ForcedNegativeRouteExitCode $task43pMarker) -ne 2) {
            throw 'wrong Java forced-negative text did not resolve to exit 2.'
        }
        Write-Host 'PASS:forced-negative wrong Java text -> not expected success/exit 2'

        foreach ($sourceId in @('renderer-only-j1-1-plus-20px',
                'renderer-only-j1-1-lead-plus-20px', 'raw-copper-j1-1-endpoint-gap',
                'raw-net-mismatch', 'solver-binding-j1-1-post-mismatch',
                'solver-detachable-c1-plus-identity-mismatch', 'package-mirror-mismatch',
                'internally-self-consistent-wrong-mapping', 'omitted-manifest-terminal',
                'snapshot-restore-resistance-current-omitted', 'public-remove-action-disabled')) {
            $script:Task43PSourceDefinition = Get-Task43PSourceExperimentDefinition $sourceId
            $sourceMarker = $script:Task43PSourceDefinition.expectedMarker
            & $setValid $sourceMarker '' $script:Task43PCandidateSha
            $sourceDiagnostic = Get-Task43ForcedNegativeExpectedDiagnostic $sourceMarker
            foreach ($badDiagnostic in @(
                $sourceDiagnostic.Replace('experiment=' + $sourceId, 'experiment=wrong-source-id'),
                $sourceDiagnostic.Replace('run=' + $runId, 'run=wrong-run-id'),
                $sourceDiagnostic.Replace(', seed ' + [string]$script:Task43PSourceDefinition.seed + ':', ', seed 99:'),
                $sourceDiagnostic.Replace(' nonce=', "`nnonce="),
                ($sourceDiagnostic + "`nunrelated error"),
                $sourceDiagnostic.Replace($sourceMarker.Substring(5), 'different-validator-failure'))) {
                if (isExpectedTask43ForcedFailureDiagnostic $badDiagnostic $sourceMarker `
                        $runId $routeId $script:Task43PCandidateSha) {
                    throw "source-negative wrong diagnostic was accepted: $sourceId"
                }
            }
            foreach ($field in @('RunId', 'RouteId', 'Nonce', 'RequestId',
                    'ExecutionDigest', 'DiagnosticBaselineHead')) {
                $originalValue = $script:Task43ForcedNegativeProof.$field
                $script:Task43ForcedNegativeProof.$field = 'different-proof-identity'
                if (Test-Task43ForcedNegativeProof $sourceMarker) {
                    throw "source-negative mismatched $field was accepted: $sourceId"
                }
                $script:Task43ForcedNegativeProof.$field = $originalValue
            }
            Set-Task43ForcedNegativeFinalCleanupProven $true
            if ((Resolve-Task43ForcedNegativeTopLevelExitCode 1 $sourceMarker) -ne 1) {
                throw "complete exact source-negative proof was rejected: $sourceId"
            }
            & $setValid $sourceMarker '' $script:Task43PCandidateSha
            if ((Resolve-Task43ForcedNegativeTopLevelExitCode 1 $sourceMarker) -ne 2) {
                throw "source-negative unproven final cleanup was accepted: $sourceId"
            }
            & $reset $sourceMarker
            Set-Task43ForcedNegativeMarkerObserved $sourceMarker
            if ((Get-Task43ForcedNegativeRouteExitCode $sourceMarker) -ne 2) {
                throw "source-negative DOM-only marker was accepted: $sourceId"
            }
        }
        $unknownSourceRejected = $false
        try { [void](Get-Task43PSourceExperimentDefinition 'arbitrary-failure') } catch {
            $unknownSourceRejected = Test-VerifierInfrastructureError $_
        }
        if (-not $unknownSourceRejected) { throw 'unknown source-negative ID was accepted' }
        Write-Host 'PASS:source-negative exact Java/DOM identities, malformed diagnostics, missing proof, and final-cleanup gates'
        $script:Task43PSourceDefinition = $null

        $legacyDiagnostic = 'Console failure: exception in runCircuit java.lang.IllegalStateException: ' +
            'Generated board verification failed for led/controlled-indicator, seed 3: ' +
            $legacyMarker.Substring(5)
        & $setValid $legacyMarker $legacyDiagnostic ''
        if ((Get-Task43ForcedNegativeRouteExitCode $legacyMarker) -ne 1) {
            throw 'ordinary Task43ForcedNegative exact proof did not resolve to exit 1.'
        }
        Set-Task43ForcedNegativeFinalCleanupProven $true
        if ((Resolve-Task43ForcedNegativeTopLevelExitCode 1 $legacyMarker) -ne 1) {
            throw 'ordinary Task43ForcedNegative exact proof failed the top-level gate.'
        }
        Write-Host 'PASS:ordinary Task43ForcedNegative exact proof -> exit 1'

        & $setValid $legacyMarker $legacyDiagnostic ''
        try { Throw-VerifierInfrastructure 'synthetic cleanup uncertainty after marker' } catch {
            Set-VerifierFailure $_ 'Gate B forced-negative later cleanup' -Quiet
        }
        if ($script:Task43ForcedNegativeProof.RoutePassedAfterCleanup -eq $true -or
                $script:Task43ForcedNegativeProof.FinalCleanupProven -eq $true -or
                (Get-Task43ForcedNegativeRouteExitCode $legacyMarker) -ne 2 -or
                (Resolve-Task43ForcedNegativeTopLevelExitCode 1 $legacyMarker) -ne 2) {
            throw 'forced-negative marker followed by cleanup uncertainty retained a success claim.'
        }
        Write-Host 'PASS:forced-negative marker then later infrastructure/cleanup -> exit 2'

        foreach ($withoutProofCase in @(
            [pscustomobject]@{ Name = 'actual-exit-0'; Exit = 0 }
            [pscustomobject]@{ Name = 'actual-exit-1'; Exit = 1 }
        )) {
            $integratedWithoutProof = $false
            try {
                Assert-IntegratedChildOutputContract `
                    ('Gate B forced-negative ' + $withoutProofCase.Name + ' without proof') `
                    1 $withoutProofCase.Exit `
                    @('EXPECTED FAILURE task43 forced-negative canary') $null $legacyMarker
            } catch {
                $integratedWithoutProof = Test-VerifierInfrastructureError $_
            }
            if (-not $integratedWithoutProof) {
                throw ('integrated expected-exit-1 ' + $withoutProofCase.Name +
                    ' without durable forced proof was accepted.')
            }
            Write-Host ('PASS:integrated expected-exit-1 ' + $withoutProofCase.Name +
                ' without forced proof -> infrastructure exit 2')
        }
    } finally {
        $script:Task43PCandidateSha = $priorCandidateSha
        $script:VerifierContext = $priorContext
        $script:VerifierCurrentRouteId = $priorRouteId
        $script:Task43ForcedNegativeProof = $priorProof
        $script:Task43PSourceDefinition = $priorSourceDefinition
        $script:task43ExpectedFailureObserved = $priorObserved
        $script:task43ExpectedFailureRoutePassed = $priorRoutePassed
        $script:VerifierFailureExitCode = $priorFailureExit
        $script:VerifierFailureKind = $priorFailureKind
        $script:VerifierFailureMessage = $priorFailureMessage
    }
}

# A deterministic developer-only probe used by Gate B.  It exercises the same
# wait/invoke classification functions without requiring a browser or a
# fabricated application result.
if ($GateBListenerProofProbe) {
    try {
        Invoke-GateBListenerProofCanary
        exit 0
    } catch {
        [Console]::Error.WriteLine('FAIL:listener proof canary - ' + $_.Exception.Message)
        exit 2
    }
}
if ($GateBForcedNegativeProofProbe) {
    try {
        Invoke-Task43ForcedNegativeContractCanaries
        exit 0
    } catch {
        [Console]::Error.WriteLine('FAIL:forced-negative proof canary - ' + $_.Exception.Message)
        exit 2
    }
}
if ($GateBContractProbe) {
    try {
        if ($GateBContractProbeFailure) {
            Throw-VerifierInfrastructure 'deterministic Gate B CDP contract-probe failure'
        }
        Invoke-Task43ForcedNegativeContractCanaries
        Invoke-VerifierIntegratedProcessInvocationBoundaryCanary
        Invoke-VerifierRequestedExitCodeCanary
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
        $invalidChildStatuses = New-Object Collections.ArrayList
        [void]$invalidChildStatuses.Add($null)
        [void]$invalidChildStatuses.Add('')
        [void]$invalidChildStatuses.Add('not-a-number')
        [void]$invalidChildStatuses.Add('1')
        [void]$invalidChildStatuses.Add(1.5)
        [void]$invalidChildStatuses.Add($true)
        [void]$invalidChildStatuses.Add(@(1, 2))
        [void]$invalidChildStatuses.Add([pscustomobject]@{ Value = 1 })
        foreach ($invalidChildStatus in @($invalidChildStatuses)) {
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
        Invoke-GateBListenerProofCanary
        # Normally terminated children, including expected infrastructure
        # failures, must publish a completed ledger whose paths and resources
        # are real and owned. Only a parent timeout may accept a retained
        # incomplete ledger, and that result remains infrastructure failure.
        $ledgerCanaryRoot = Get-VerifierCanonicalWindowsPath (Join-Path ([IO.Path]::GetTempPath()) `
            ('TroubleshootJS\verify\ledger-completion-' + [Guid]::NewGuid().ToString('N')))
        $ledgerCanaryFailure = $null
        try {
            New-VerifierEvidenceDirectory $ledgerCanaryRoot 'Gate B ledger canary' -Force
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
                     $profileValue = [string](Get-VerifierCanaryLedgerProperty $candidate 'profile' '')
                     if ([String]::IsNullOrWhiteSpace($profileValue)) {
                         $profileValue = [string](Get-VerifierCanaryLedgerProperty $candidate 'ProfilePath' '')
                     }
                     $ownerPid = [int](Get-VerifierLedgerProperty $candidate 'claimOwnerPid' $PID)
                     $ownerStart = [long](Get-VerifierLedgerProperty $candidate 'claimOwnerStartTicks' 1)
                     $mutexReleased = Get-VerifierDurableBooleanValue $candidate `
                         'MutexReleased' ('canary lease ' + $leaseId + ' mutexReleased')
                     $candidateListenerPid = [int](Get-VerifierLedgerProperty $candidate 'listenerProcessId' 0)
                     $listenerStartValue = Get-VerifierCanaryLedgerProperty $candidate `
                         'listenerProcessStartTicks' $null
                     $listenerOwnerKindValue = [string](Get-VerifierCanaryLedgerProperty `
                         $candidate 'listenerOwnerKind' '')
                     $listenerOwnerProofValue = [string](Get-VerifierCanaryLedgerProperty `
                         $candidate 'listenerOwnerProof' '')
                     $listenerOwnerEvidenceValue = [string](Get-VerifierCanaryLedgerProperty `
                         $candidate 'listenerOwnerEvidence' '')
                     $normalizedLeases += [pscustomobject]([ordered]@{
                        runId = $Context.RunId; repositoryIdentity = $Context.RepositoryIdentity
                        worktreeRoot = $Context.WorktreeRoot; leaseId = $leaseId
                        path = [string](Get-VerifierLedgerProperty $candidate 'path' '')
                        kind = $kind; port = $port; claimName = $claimName
                        status = [string](Get-VerifierLedgerProperty $candidate 'status' 'held')
                          registered = Get-VerifierDurableBooleanValue $candidate `
                              'Registered' ('canary lease ' + $leaseId + ' registered')
                          releaseBlocked = Get-VerifierDurableBooleanValue $candidate `
                              'ReleaseBlocked' ('canary lease ' + $leaseId + ' releaseBlocked')
                          releaseBlockReason = Get-VerifierLedgerProperty $candidate 'releaseBlockReason' ''
                          claimState = [string](Get-VerifierLedgerProperty $candidate 'claimState' 'held')
                          releaseState = [string](Get-VerifierLedgerProperty $candidate 'releaseState' 'active')
                          releaseJournalState = Get-VerifierLedgerProperty $candidate 'releaseJournalState' $null
                          bindValidatedUtc = [string](Get-VerifierLedgerProperty $candidate 'bindValidatedUtc' '')
                          releasedUtc = [string](Get-VerifierLedgerProperty $candidate 'releasedUtc' '')
                          claimOwnerPid = $ownerPid; claimOwnerStartTicks = $ownerStart
                          mutexReleased = $mutexReleased
                          profile = $profileValue
                          browserPath = $browserPath
                         processTerminationProven = Get-VerifierDurableBooleanValue $candidate `
                             'ProcessTerminationProven' ('canary lease ' + $leaseId + ' processTerminationProven')
                         processAbsent = Get-VerifierDurableBooleanValue $candidate `
                             'ProcessAbsent' ('canary lease ' + $leaseId + ' processAbsent')
                        boundProcessId = [int](Get-VerifierLedgerProperty $candidate 'boundProcessId' 0)
                        boundProcessStartTicks = [long](Get-VerifierLedgerProperty $candidate 'boundProcessStartTicks' 0)
                        listenerProcessId = $candidateListenerPid
                        listenerProcessStartTicks = $listenerStartValue
                        listenerOwnerKind = $listenerOwnerKindValue
                        listenerOwnerProof = $listenerOwnerProofValue
                        listenerOwnerEvidence = $listenerOwnerEvidenceValue
                          listenerInspectionSuccess = Get-VerifierDurableBooleanValue $candidate `
                              'ListenerInspectionSuccess' ('canary lease ' + $leaseId + ' listenerInspectionSuccess')
                          listenerInspectionKnown = Get-VerifierDurableBooleanValue $candidate `
                              'ListenerInspectionKnown' ('canary lease ' + $leaseId + ' listenerInspectionKnown')
                          listenerInspectionUtc = Get-VerifierLedgerProperty $candidate 'listenerInspectionUtc' ''
                          listenerHasListeners = Get-VerifierDurableBooleanValue $candidate `
                              'ListenerHasListeners' ('canary lease ' + $leaseId + ' listenerHasListeners') -AllowNull
                          listenerAbsent = Get-VerifierDurableBooleanValue $candidate `
                              'ListenerAbsent' ('canary lease ' + $leaseId + ' listenerAbsent') -AllowNull
                          processProofRequired = Get-VerifierDurableBooleanValue $candidate `
                              'ProcessProofRequired' ('canary lease ' + $leaseId + ' processProofRequired')
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
                        routeId = [string](Get-VerifierLedgerProperty $candidate 'routeId' '')
                        routeName = [string](Get-VerifierLedgerProperty $candidate 'routeName' '')
                        processId = [int](Get-VerifierLedgerProperty $candidate 'processId' 0)
                        processStartTicks = [long](Get-VerifierLedgerProperty $candidate 'processStartTicks' 0)
                        processParentProcessId = [int](Get-VerifierLedgerProperty $candidate 'processParentProcessId' 0)
                        processParentProcessStartTicks = [long](Get-VerifierLedgerProperty $candidate 'processParentProcessStartTicks' 0)
                        processCommandLine = [string](Get-VerifierLedgerProperty $candidate 'processCommandLine' '')
                        targetId = [string](Get-VerifierLedgerProperty $candidate 'targetId' '')
                        expectedUrl = [string](Get-VerifierLedgerProperty $candidate 'expectedUrl' '')
                        expectedRunMarker = 'tsjVerifierRun=' + $Context.RunId
                        expectedRouteMarker = 'tsjVerifierRoute=' + [string](Get-VerifierLedgerProperty $candidate 'routeId' '')
                        status = [string](Get-VerifierLedgerProperty $candidate 'status' 'startup-failed')
                        cleanupResult = [string](Get-VerifierLedgerProperty $candidate 'cleanupResult' 'infrastructure-failure')
                        profileProcessScanCompleted = Get-VerifierDurableBooleanValue $candidate `
                            'ProfileProcessScanCompleted' 'canary profile process scan completed'
                        profileInspectionFailed = Get-VerifierDurableBooleanValue $candidate `
                            'ProfileInspectionFailed' 'canary profile inspection failed'
                        error = [string](Get-VerifierLedgerProperty $candidate 'error' '')
                    })
                }
                $manifestForLedger = Get-Content -LiteralPath $Context.ManifestPath -Raw | ConvertFrom-Json
                $record = [ordered]@{
                    protocol = 'troubleshootjs-integrated-child-ledger-v1'; state = $State
                    forcedNegativeProof = $null
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
                    cleanupErrors = @(); error = ''; updatedUtc = Get-VerifierUtcText
                }
                Write-VerifierEvidenceText $Path ($record | ConvertTo-Json -Depth 12) `
                    'Gate B ledger canary'
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
                    $null -eq $_ -or $null -eq (Get-VerifierCanaryLedgerProperty $_ 'claim' $null)
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
                if ($null -ne $Context.PSObject.Properties['CleanupState']) {
                    $manifest.cleanup.state = [string]$Context.CleanupState
                }
                Write-VerifierEvidenceText $Context.ManifestPath `
                    ($manifest | ConvertTo-Json -Depth 12) 'Gate B manifest canary'
            }
            $repoForLedger = Get-VerifierFullPath (Join-Path $PSScriptRoot '..')
            # Every run-owned browser/paired cdp resource must carry the same
            # resolved executable identity that the live verifier will use.
            $ledgerBrowserPath = Resolve-VerifierBrowserPath $BrowserPath
            $zeroParent = Get-VerifierFullPath (Join-Path $ledgerCanaryRoot 'completed-zero')
            $zeroContext = New-VerifierRunContext $repoForLedger '' $zeroParent
            $zeroContext.CleanupState = 'complete'
            $zeroContext.CleanupCompletedUtc = '2026-08-31T00:00:01.0000000Z'
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
                Write-VerifierEvidenceText $realResourceContext.ManifestPath `
                    ($missingBrowserPathManifest | ConvertTo-Json -Depth 16) `
                    'Gate B missing-browser-path manifest canary'
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
                Write-VerifierEvidenceText $missingBrowserPathPath `
                    ($missingBrowserPathLedger | ConvertTo-Json -Depth 16) `
                    'Gate B missing-browser-path ledger canary'
                try { [void](Read-VerifierIntegratedChildLedger $missingBrowserPathPath 2) } catch {
                    $missingBrowserPathRejected = Test-VerifierInfrastructureError $_
                }
                if (-not $missingBrowserPathRejected) {
                    throw 'missing-configured-BrowserPath ledger was accepted and could permit root cleanup/adoption.'
                }
                Write-Host 'INFO:missing-configured-BrowserPath ledger rejected before ownership cleanup'
            } finally {
                Write-VerifierEvidenceText $realResourceContext.ManifestPath `
                    $missingBrowserPathManifestText 'Gate B manifest canary restoration'
            }
            $realResourceProfile = Get-VerifierFullPath (Join-Path $realResourceContext.RunRoot 'browser\real\profile')
            New-VerifierEvidenceDirectory $realResourceProfile 'Gate B browser profile canary' -Force
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
                routeId = 'gate-b-real-resource'; routeName = 'Gate B real resource'
                targetId = ''; expectedUrl = ''
                expectedRunMarker = 'tsjVerifierRun=' + $realResourceContext.RunId
                expectedRouteMarker = 'tsjVerifierRoute=gate-b-real-resource'
                status = 'cleaned'; cleanupResult = 'complete'
                profileProcessScanCompleted = $true; profileInspectionFailed = $false
                error = ''
            }
            Remove-VerifierOwnedTree $realResourceContext.RunRoot $realResourceProfile
            $realResourceContext.CleanupState = 'complete'
            $realResourceContext.CleanupCompletedUtc = Get-VerifierUtcText
            Write-VerifierManifest $realResourceContext
            Set-GateBManifestResources $realResourceContext -Leases @($realResourceLease) `
                -Profiles @($realResourceProfileRecord) -Server $null
            $realResourceLedgerPath = Get-VerifierFullPath (Join-Path $realResourceParent 'real-completed.json')
            Write-GateBLedger $realResourceContext $realResourceLedgerPath 'completed' `
                -Leases @($realResourceLease) -Profiles @($realResourceProfileRecord) `
                -Server $null -CleanupState 'complete'
            # A user-process tuple must never accept PID zero merely because its
            # retained start/proof fields look plausible. Mutate both durable
            # copies together so this exercises the paired reader boundary.
            $zeroUserListenerManifestBaseline = [IO.File]::ReadAllText($realResourceContext.ManifestPath)
            $zeroUserListenerPath = Get-VerifierFullPath (Join-Path $realResourceParent 'zero-user-listener.json')
            try {
                $zeroUserListenerManifest = $zeroUserListenerManifestBaseline | ConvertFrom-Json
                $zeroUserListenerManifestLease = @($zeroUserListenerManifest.leases)[0]
                $zeroUserListenerManifestLease.listenerProcessId = 0
                $zeroUserListenerManifestLease.listenerProcessStartTicks = 234501L
                $zeroUserListenerManifestLease.listenerOwnerKind = 'user-process'
                $zeroUserListenerManifestLease.listenerOwnerProof = 'diagnostics-process-start-v1'
                $zeroUserListenerManifestLease.listenerOwnerEvidence = 'system-diagnostics-process-starttime'
                Write-VerifierEvidenceText $realResourceContext.ManifestPath `
                    ($zeroUserListenerManifest | ConvertTo-Json -Depth 16) `
                    'Gate B zero-user-listener manifest canary'
                $zeroUserListenerLedger = Get-Content -LiteralPath $realResourceLedgerPath -Raw | ConvertFrom-Json
                $zeroUserListenerLedgerLease = @($zeroUserListenerLedger.leases)[0]
                $zeroUserListenerLedgerLease.listenerProcessId = 0
                $zeroUserListenerLedgerLease.listenerProcessStartTicks = 234501L
                $zeroUserListenerLedgerLease.listenerOwnerKind = 'user-process'
                $zeroUserListenerLedgerLease.listenerOwnerProof = 'diagnostics-process-start-v1'
                $zeroUserListenerLedgerLease.listenerOwnerEvidence = 'system-diagnostics-process-starttime'
                Write-VerifierEvidenceText $zeroUserListenerPath `
                    ($zeroUserListenerLedger | ConvertTo-Json -Depth 16) `
                    'Gate B zero-user-listener ledger canary'
                $zeroUserListenerRejected = $false
                try { [void](Read-VerifierIntegratedChildLedger $zeroUserListenerPath 2) } catch {
                    $zeroUserListenerRejected = Test-VerifierInfrastructureError $_
                }
                if (-not $zeroUserListenerRejected) {
                    throw 'paired durable reader accepted a user-process listener with PID zero.'
                }
            } finally {
                Write-VerifierEvidenceText $realResourceContext.ManifestPath `
                    $zeroUserListenerManifestBaseline 'Gate B zero-user-listener manifest restoration'
            }

            # Missing/null durable collections and typed identity coercion must
            # fail closed in either durable copy. These variants deliberately
            # damage one copy at a time and restore the manifest after each
            # reader call.
            $durableCopyBaseline = [IO.File]::ReadAllText($realResourceContext.ManifestPath)
            $assertDamagedDurableCopyRejected = {
                param([string]$Name, [scriptblock]$LedgerMutation,
                    [scriptblock]$ManifestMutation = $null)
                $variantPath = Get-VerifierFullPath (Join-Path $realResourceParent ($Name + '.json'))
                try {
                    if ($null -ne $ManifestMutation) {
                        $manifestVariant = $durableCopyBaseline | ConvertFrom-Json
                        & $ManifestMutation $manifestVariant
                        Write-VerifierEvidenceText $realResourceContext.ManifestPath `
                            ($manifestVariant | ConvertTo-Json -Depth 16) `
                            ('Gate B ' + $Name + ' manifest canary')
                    }
                    $ledgerVariant = Get-Content -LiteralPath $realResourceLedgerPath -Raw | ConvertFrom-Json
                    & $LedgerMutation $ledgerVariant
                    Write-VerifierEvidenceText $variantPath `
                        ($ledgerVariant | ConvertTo-Json -Depth 16) `
                        ('Gate B ' + $Name + ' ledger canary')
                    $rejected = $false
                    try { [void](Read-VerifierIntegratedChildLedger $variantPath 2) } catch {
                        $rejected = Test-VerifierInfrastructureError $_
                    }
                    if (-not $rejected) {
                        throw "durable-copy canary '$Name' was accepted despite damaged ownership evidence."
                    }
                } finally {
                    Write-VerifierEvidenceText $realResourceContext.ManifestPath `
                        $durableCopyBaseline ('Gate B ' + $Name + ' manifest restoration')
                }
            }
            & $assertDamagedDurableCopyRejected 'missing-ledger-evidence' {
                param($ledgerVariant)
                [void]$ledgerVariant.PSObject.Properties.Remove('evidence')
            }
            & $assertDamagedDurableCopyRejected 'null-ledger-leases' {
                param($ledgerVariant)
                $ledgerVariant.leases = $null
            }
            & $assertDamagedDurableCopyRejected 'missing-ledger-profiles' {
                param($ledgerVariant)
                [void]$ledgerVariant.PSObject.Properties.Remove('profiles')
            }
            & $assertDamagedDurableCopyRejected 'missing-manifest-artifacts' {
                param($ledgerVariant)
            } {
                param($manifestVariant)
                [void]$manifestVariant.PSObject.Properties.Remove('artifacts')
            }
            & $assertDamagedDurableCopyRejected 'null-manifest-browser-sessions' {
                param($ledgerVariant)
            } {
                param($manifestVariant)
                $manifestVariant.browserSessions = $null
            }
            & $assertDamagedDurableCopyRejected 'string-ledger-port' {
                param($ledgerVariant)
                $ledgerVariant.leases[0].port = [string]$ledgerVariant.leases[0].port
            }
            & $assertDamagedDurableCopyRejected 'numeric-ledger-lease-id' {
                param($ledgerVariant)
                $ledgerVariant.leases[0].leaseId = 12345
            }
            & $assertDamagedDurableCopyRejected 'array-ledger-lease-id' {
                param($ledgerVariant)
                $ledgerVariant.leases[0].leaseId = @([string]$ledgerVariant.leases[0].leaseId)
            }
            & $assertDamagedDurableCopyRejected 'object-ledger-lease-id' {
                param($ledgerVariant)
                $ledgerVariant.leases[0].leaseId = [pscustomobject]@{
                    Value = [string]$ledgerVariant.leases[0].leaseId
                }
            }
            & $assertDamagedDurableCopyRejected 'null-ledger-lease-id' {
                param($ledgerVariant)
                $ledgerVariant.leases[0].leaseId = $null
            }
            & $assertDamagedDurableCopyRejected 'missing-ledger-lease-id' {
                param($ledgerVariant)
                [void]$ledgerVariant.leases[0].PSObject.Properties.Remove('leaseId')
            }
            & $assertDamagedDurableCopyRejected 'numeric-manifest-lease-id' {
                param($ledgerVariant)
            } {
                param($manifestVariant)
                $manifestVariant.leases[0].leaseId = 12345
            }
            & $assertDamagedDurableCopyRejected 'array-manifest-lease-id' {
                param($ledgerVariant)
            } {
                param($manifestVariant)
                $manifestVariant.leases[0].leaseId = @([string]$manifestVariant.leases[0].leaseId)
            }
            & $assertDamagedDurableCopyRejected 'object-manifest-lease-id' {
                param($ledgerVariant)
            } {
                param($manifestVariant)
                $manifestVariant.leases[0].leaseId = [pscustomobject]@{
                    Value = [string]$manifestVariant.leases[0].leaseId
                }
            }
            & $assertDamagedDurableCopyRejected 'null-manifest-lease-id' {
                param($ledgerVariant)
            } {
                param($manifestVariant)
                $manifestVariant.leases[0].leaseId = $null
            }
            & $assertDamagedDurableCopyRejected 'missing-manifest-lease-id' {
                param($ledgerVariant)
            } {
                param($manifestVariant)
                [void]$manifestVariant.leases[0].PSObject.Properties.Remove('leaseId')
            }
            $leaseIdCaseBaseline = Get-Content -LiteralPath $realResourceLedgerPath -Raw | ConvertFrom-Json
            $leaseIdCaseBase = [string]$leaseIdCaseBaseline.leases[0].leaseId
            $leaseIdCaseVariant = $leaseIdCaseBase.ToUpperInvariant()
            if ($leaseIdCaseVariant -ceq $leaseIdCaseBase) {
                $leaseIdCaseVariant = $leaseIdCaseBase.ToLowerInvariant()
            }
            if ($leaseIdCaseVariant -ceq $leaseIdCaseBase) {
                $leaseIdCaseVariant = 'case-' + $leaseIdCaseBase
            }
            & $assertDamagedDurableCopyRejected 'case-mismatched-ledger-lease-id' {
                param($ledgerVariant)
                $ledgerVariant.leases[0].leaseId = $leaseIdCaseVariant
            }
            & $assertDamagedDurableCopyRejected 'case-mismatched-manifest-lease-id' {
                param($ledgerVariant)
            } {
                param($manifestVariant)
                $manifestVariant.leases[0].leaseId = $leaseIdCaseVariant
            }
            Write-Host 'PASS:paired durable leaseId fields require present exact strings and ordinal equality in both copies'
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
            Write-VerifierEvidenceText $missingTerminalPath `
                ($missingTerminalLedger | ConvertTo-Json -Depth 16) `
                'Gate B missing-terminal ledger canary'
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
            Write-VerifierEvidenceText $liveTerminalPath `
                ($liveTerminalLedger | ConvertTo-Json -Depth 16) `
                'Gate B live-terminal ledger canary'
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
            Write-VerifierEvidenceText $falseCleanupPath `
                ($falseCleanupLedger | ConvertTo-Json -Depth 16) `
                'Gate B false-cleanup ledger canary'
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
                    port=40126; processId=0; processStartTicks=0; processParentProcessId=0; processParentProcessStartTicks=0; processCommandLine=''
                    script=$callerScript; runId=''; nonce=''; leaseId=''; leaseKind=''; leaseClaimName=''
                    leaseClaimState=''; leaseReleaseState=''; leaseReleaseJournalState=''
                    leaseOwnerPid=0; leaseOwnerStartTicks=0; leasePath=''
                    leaseListenerOwnerKind='none'; leaseListenerOwnerProof=''; leaseListenerOwnerEvidence=''
                    leaseListenerAbsent=$null; leaseProcessProofRequired=$null
                    stdoutLog=''; stderrLog=''; state='caller-verified'; cleanupResult='not-owned'
                    error=''
                    Lease=$null; Process=$null
                    processIdentityKnown=$false; ownershipUncertain=$false; processTerminationProven=$false
                    processAbsent=$true; listenerInspectionProven=$false; listenerAbsent=$false
                }
                $callerCanaryContext.Server = [pscustomobject]$callerServerLedger
                $callerCanaryContext.CleanupState = 'complete'
                $callerCanaryContext.CleanupCompletedUtc = Get-VerifierUtcText
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
                        Write-VerifierEvidenceText $callerCanaryContext.ManifestPath `
                            ($callerManifestVariant | ConvertTo-Json -Depth 16) `
                            'Gate B caller manifest canary'
                        Write-VerifierEvidenceText $callerVariantPath `
                            ($callerLedgerVariant | ConvertTo-Json -Depth 16) `
                            'Gate B caller ledger canary'
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
                        Write-VerifierEvidenceText $callerCanaryContext.ManifestPath `
                            $callerManifestBaseline 'Gate B caller manifest restoration'
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
            Write-VerifierEvidenceText $normalIncompletePath `
                ($normalIncompleteLedger | ConvertTo-Json -Depth 12) `
                'Gate B incomplete-ledger canary'
            $normalIncompleteRejected = $false
            try { [void](Read-VerifierIntegratedChildLedger $normalIncompletePath 2) } catch {
                $normalIncompleteRejected = Test-VerifierInfrastructureError $_
            }
            if (-not $normalIncompleteRejected) {
                throw 'normally terminated expected-exit-2 child with an incomplete ledger was accepted.'
            }

            $malformedPath = Join-Path $zeroParent 'malformed.json'
            Write-VerifierEvidenceText $malformedPath `
                '{"protocol":"troubleshootjs-integrated-child-ledger-v1","state":"completed"}' `
                'Gate B malformed-ledger canary'
            $malformedRejected = $false
            try { [void](Read-VerifierIntegratedChildLedger $malformedPath 2) } catch {
                $malformedRejected = Test-VerifierInfrastructureError $_
            }
            if (-not $malformedRejected) { throw 'malformed/pathless child ledger was accepted.' }

            # A malformed lifecycle scalar must be rejected by the copy schema
            # before the reader asks its legacy-property accessor for state or
            # any later path/lifecycle projection.
            $malformedStatePath = Join-Path $zeroParent 'malformed-state.json'
            $malformedStateLedger = Get-Content -LiteralPath $zeroLedgerPath -Raw | ConvertFrom-Json
            $malformedStateLedger.state = 0
            Write-VerifierEvidenceText $malformedStatePath `
                ($malformedStateLedger | ConvertTo-Json -Depth 12) `
                'Gate B malformed-state ordering canary'
            $oldLedgerPropertyFunction = (Get-Command Get-VerifierLedgerProperty `
                -CommandType Function -ErrorAction Stop).ScriptBlock
            try {
                $script:IntegratedReaderLegacyAccessorObserved = $false
                Set-Item Function:\Get-VerifierLedgerProperty -Force -Value {
                    $script:IntegratedReaderLegacyAccessorObserved = $true
                    return $null
                }
                $malformedStateRejected = $false
                try { [void](Read-VerifierIntegratedChildLedger $malformedStatePath 2) } catch {
                    $malformedStateRejected = Test-VerifierInfrastructureError $_
                }
                if (-not $malformedStateRejected -or
                        [bool]$script:IntegratedReaderLegacyAccessorObserved) {
                    throw 'malformed reader state was not rejected before legacy access.'
                }
            } finally {
                Set-Item Function:\Get-VerifierLedgerProperty -Force `
                    -Value $oldLedgerPropertyFunction
                Remove-Variable -Name IntegratedReaderLegacyAccessorObserved -Scope Script `
                    -Force -ErrorAction SilentlyContinue
            }

            $foreignPath = Join-Path $zeroParent 'foreign.json'
            $foreignLedger = Get-Content -LiteralPath $zeroLedgerPath -Raw | ConvertFrom-Json
            $foreignLedger.runRoot = Get-VerifierFullPath (Join-Path $ledgerCanaryRoot 'foreign-run-root')
            Write-VerifierEvidenceText $foreignPath `
                ($foreignLedger | ConvertTo-Json -Depth 12) 'Gate B foreign-ledger canary'
            $foreignRejected = $false
            try { [void](Read-VerifierIntegratedChildLedger $foreignPath 2) } catch {
                $foreignRejected = Test-VerifierInfrastructureError $_
            }
            if (-not $foreignRejected) { throw 'foreign child run-root ledger was accepted.' }

            $failedPath = Join-Path $zeroParent 'cleanup-failed.json'
            $failedLedger = Get-Content -LiteralPath $zeroLedgerPath -Raw | ConvertFrom-Json
            $failedLedger.state = 'cleanup-failed'
            $failedLedger.cleanupState = 'infrastructure-failure'
            Write-VerifierEvidenceText $failedPath `
                ($failedLedger | ConvertTo-Json -Depth 12) 'Gate B failed-ledger canary'
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
            $staleLease = [ordered]@{ runId=$staleContext.RunId; repositoryIdentity=$staleContext.RepositoryIdentity; worktreeRoot=$staleContext.WorktreeRoot; leaseId=$staleLeaseId; path=$staleClaimPath; kind='cdp'; port=40123; claimName=$staleClaimName; browserPath=$ledgerBrowserPath; claimOwnerPid=$PID; claimOwnerStartTicks=1; status='released'; claimState='released'; releaseState='complete'; releaseJournalState='complete'; registered=$true; releaseBlocked=$false; releaseBlockReason=''; bindValidatedUtc=''; releasedUtc=''; mutexReleased=$false; profile=''; listenerInspectionSuccess=$true; listenerInspectionKnown=$true; listenerInspectionUtc=''; listenerHasListeners=$false; listenerAbsent=$true; processTerminationProven=$false; processAbsent=$false; processProofRequired=$false; boundProcessId=0; boundProcessStartTicks=0; listenerProcessId=0; listenerProcessStartTicks=0; listenerOwnerKind='none'; listenerOwnerProof=''; listenerOwnerEvidence=''; claim=$staleClaim }
            Write-VerifierEvidenceText $staleClaimPath `
                ($staleClaim | ConvertTo-Json -Depth 8) 'Gate B stale-claim canary'
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
            New-VerifierEvidenceDirectory $retainedProfile 'Gate B retained browser profile canary' -Force
            $retainedServerLeaseId = [Guid]::NewGuid().ToString('N')
            $retainedServerClaimPath = Get-VerifierFullPath (Join-Path $retainedContext.PortLeaseRoot ('40124-' + $retainedServerLeaseId + '.lease'))
            $retainedServerClaimName = Get-VerifierPortMutexName $null 40124
            $retainedServerClaim = [ordered]@{ protocol='troubleshootjs-verifier-port-claim-v1'; runId=$retainedContext.RunId; repositoryIdentity=$retainedContext.RepositoryIdentity; worktreeRoot=$retainedContext.WorktreeRoot; leaseId=$retainedServerLeaseId; path=$retainedServerClaimPath; kind='preview'; port=40124; mutexName=$retainedServerClaimName; ownerPid=$PID; ownerStartTicks=1 }
            $retainedServerLease = [ordered]@{ runId=$retainedContext.RunId; repositoryIdentity=$retainedContext.RepositoryIdentity; worktreeRoot=$retainedContext.WorktreeRoot; leaseId=$retainedServerLeaseId; path=$retainedServerClaimPath; kind='preview'; port=40124; claimName=$retainedServerClaimName; claimOwnerPid=$PID; claimOwnerStartTicks=1; status='bound'; claimState='bound'; releaseState='active'; releaseJournalState='active'; registered=$true; releaseBlocked=$false; releaseBlockReason=''; listenerInspectionUtc=''; bindValidatedUtc=''; releasedUtc=''; browserPath=$ledgerBrowserPath; mutexReleased=$false; profile=''; listenerInspectionSuccess=$true; listenerInspectionKnown=$true; listenerHasListeners=$true; listenerAbsent=$false; processTerminationProven=$false; processAbsent=$false; processProofRequired=$true; boundProcessId=54321; boundProcessStartTicks=12345L; listenerProcessId=54321; listenerProcessStartTicks=12345L; listenerOwnerKind='user-process'; listenerOwnerProof='diagnostics-process-start-v1'; listenerOwnerEvidence='system-diagnostics-process-starttime'; processParentProcessStartTicks=12344L; claim=$retainedServerClaim }
            Write-VerifierEvidenceText $retainedServerClaimPath `
                ($retainedServerClaim | ConvertTo-Json -Depth 8) `
                'Gate B retained preview claim canary'
            $retainedClaimName = Get-VerifierPortMutexName $null 40125
            $retainedClaim = [ordered]@{ protocol='troubleshootjs-verifier-port-claim-v1'; runId=$retainedContext.RunId; repositoryIdentity=$retainedContext.RepositoryIdentity; worktreeRoot=$retainedContext.WorktreeRoot; leaseId=$retainedLeaseId; path=$retainedClaimPath; kind='cdp'; port=40125; mutexName=$retainedClaimName; ownerPid=$PID; ownerStartTicks=1 }
            $retainedLease = [ordered]@{ runId=$retainedContext.RunId; repositoryIdentity=$retainedContext.RepositoryIdentity; worktreeRoot=$retainedContext.WorktreeRoot; leaseId=$retainedLeaseId; path=$retainedClaimPath; kind='cdp'; port=40125; claimName=$retainedClaimName; browserPath=$ledgerBrowserPath; bindValidatedUtc=''; releasedUtc=''; claimOwnerPid=$PID; claimOwnerStartTicks=1; status='leased'; claimState='held'; releaseState='active'; releaseJournalState='active'; registered=$true; releaseBlocked=$false; releaseBlockReason=''; listenerInspectionUtc=''; mutexReleased=$false; profile=$retainedProfile; listenerInspectionSuccess=$false; listenerInspectionKnown=$false; listenerHasListeners=$null; listenerAbsent=$null; processTerminationProven=$false; processAbsent=$false; processProofRequired=$false; boundProcessId=0; boundProcessStartTicks=0; listenerProcessId=0; listenerProcessStartTicks=0; listenerOwnerKind='none'; listenerOwnerProof=''; listenerOwnerEvidence=''; claim=$retainedClaim }
            Write-VerifierEvidenceText $retainedClaimPath `
                ($retainedClaim | ConvertTo-Json -Depth 8) 'Gate B retained CDP claim canary'
            $retainedProfileRecord = [ordered]@{ owner='run'; runId=$retainedContext.RunId; repositoryIdentity=$retainedContext.RepositoryIdentity; worktreeRoot=$retainedContext.WorktreeRoot; routeId='retained-timeout'; routeName='retained-timeout'; profile=$retainedProfile; cdpLeasePath=$retainedClaimPath; cdpPort=40125; browserPath=$ledgerBrowserPath; processId=0; processStartTicks=0; processParentProcessId=0; processParentProcessStartTicks=0; processCommandLine=''; targetId=''; expectedUrl=''; expectedRunMarker='tsjVerifierRun=' + $retainedContext.RunId; expectedRouteMarker='tsjVerifierRoute=retained-timeout'; status='startup-failed'; cleanupResult='infrastructure-failure'; profileProcessScanCompleted=$false; profileInspectionFailed=$false; error='retained timeout' }
            $retainedServerRoot = Get-VerifierFullPath (Join-Path $retainedContext.RunRoot 'server')
            New-VerifierEvidenceDirectory $retainedServerRoot 'Gate B retained preview server canary' -Force
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
                State='cleanup-failed'; RunId=$retainedContext.RunId; Nonce=$retainedContext.PreviewNonce
                StdoutLog=(Join-Path $retainedServerRoot 'stdout.log')
                StderrLog=(Join-Path $retainedServerRoot 'stderr.log')
                CleanupResult='infrastructure-failure'; Error='synthetic retained timeout'
                Lease=$retainedServerLease; Process=$null; ProcessIdentityKnown=$true
                OwnershipUncertain=$false; ProcessTerminationProven=$false; ProcessAbsent=$false
                ListenerInspectionProven=$true; ListenerAbsent=$false
            }
            Write-VerifierEvidenceText $retainedServer.StdoutLog 'retained server evidence' `
                'Gate B retained preview stdout canary'
            Write-VerifierEvidenceText $retainedServer.StderrLog '' `
                'Gate B retained preview stderr canary'
            $retainedContext.Server = $retainedServer
            $retainedContext.CleanupState = 'infrastructure-failure'
            $retainedServerLedger = [ordered]@{
                owner='run'; baseUrl='http://127.0.0.1:40124'; repositoryIdentity=$retainedContext.RepositoryIdentity
                worktreeRoot=$retainedContext.WorktreeRoot; repositoryRoot=$retainedContext.WorktreeRoot
                webRoot=(Get-VerifierFullPath (Join-Path $retainedContext.WorktreeRoot 'war'))
                port=40124; processId=54321; processStartTicks=12345L
                processParentProcessId=54320; processParentProcessStartTicks=12344; processCommandLine=$retainedServerCommand; script=$retainedServerScript
                runId=$retainedContext.RunId; nonce=$retainedContext.PreviewNonce
                identityProtocol='troubleshootjs-preview-identity-v1'
                identityVerified=$true; callerOwned=$false
                leaseId=$retainedServerLeaseId; leaseKind='preview'; leaseClaimName=$retainedServerClaimName
                leaseClaimState='bound'; leaseReleaseState='active'; leaseOwnerPid=$PID; leaseOwnerStartTicks=1
                leaseReleaseJournalState='active'; leasePath=$retainedServerClaimPath; state='cleanup-failed'
                stdoutLog=$retainedServer.StdoutLog; stderrLog=$retainedServer.StderrLog
                cleanupResult='infrastructure-failure'; error='synthetic retained timeout'; processIdentityKnown=$true; ownershipUncertain=$false
                processTerminationProven=$false; processAbsent=$false; listenerInspectionProven=$true; listenerAbsent=$false
                leaseListenerAbsent=$false; leaseProcessProofRequired=$true
                leaseListenerOwnerKind='user-process'; leaseListenerOwnerProof='diagnostics-process-start-v1'
                leaseListenerOwnerEvidence='system-diagnostics-process-starttime'
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
                        Write-VerifierEvidenceText $retainedContext.ManifestPath `
                            ($manifestVariant | ConvertTo-Json -Depth 16) `
                            'Gate B retained manifest variant'
                    }
                    if ($null -ne $ClaimMutation) { & $ClaimMutation }
                    Write-VerifierEvidenceText $variantPath `
                        ($Variant | ConvertTo-Json -Depth 16) 'Gate B retained ledger variant'
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
                        Write-VerifierEvidenceText $retainedContext.ManifestPath `
                            $retainedManifestBaseline 'Gate B retained manifest restoration'
                    }
                }
            }

            # Server log paths are security-sensitive paired strings. Exercise
            # both durable copies for missing, non-string, and ordinal-mismatch
            # values so the reader cannot fall through to path normalization or
            # string coercion.
            foreach ($logField in @('stdoutLog', 'stderrLog')) {
                $logFieldCases = @(
                    [pscustomobject]@{ Name = 'missing-' + $logField; Mode = 'missing'; Value = $null }
                    [pscustomobject]@{ Name = 'numeric-' + $logField; Mode = 'value'; Value = 0 }
                    [pscustomobject]@{ Name = 'array-' + $logField; Mode = 'value'; Value = @('malformed-log') }
                    [pscustomobject]@{ Name = 'object-' + $logField; Mode = 'value'; Value = [pscustomobject]@{ path = 'malformed-log' } }
                    [pscustomobject]@{ Name = 'case-mismatch-' + $logField; Mode = 'case-mismatch'; Value = $null }
                )
                foreach ($logFieldCase in $logFieldCases) {
                    $logVariant = Get-Content -LiteralPath $retainedLedgerPath -Raw | ConvertFrom-Json
                    $logProperty = $logVariant.server.PSObject.Properties[$logField]
                    if ($null -eq $logProperty) {
                        throw "retained ledger omitted its baseline $logField fixture"
                    }
                    if ($logFieldCase.Mode -eq 'missing') {
                        [void]$logVariant.server.PSObject.Properties.Remove($logField)
                    } elseif ($logFieldCase.Mode -eq 'case-mismatch') {
                        $logProperty.Value = [string]$logProperty.Value + '.CASE'
                    } else {
                        $logProperty.Value = $logFieldCase.Value
                    }
                    $capturedLogField = $logField
                    $capturedLogCase = $logFieldCase
                    $logManifestMutation = {
                        param($manifestVariant)
                        $manifestProperty = $manifestVariant.server.PSObject.Properties[$capturedLogField]
                        if ($null -eq $manifestProperty) {
                            throw "retained manifest omitted its baseline $capturedLogField fixture"
                        }
                        if ($capturedLogCase.Mode -eq 'missing') {
                            [void]$manifestVariant.server.PSObject.Properties.Remove($capturedLogField)
                        } elseif ($capturedLogCase.Mode -eq 'case-mismatch') {
                            $manifestProperty.Value = [string]$manifestProperty.Value + '.CASE'
                        } else {
                            $manifestProperty.Value = $capturedLogCase.Value
                        }
                    }.GetNewClosure()
                    Assert-GateBLedgerVariantRejected $logVariant $logFieldCase.Name `
                        $logManifestMutation
                }
            }
            Write-Host 'PASS:durable server stdout/stderr log pairs require exact strings and ordinal equality'

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
            Write-VerifierEvidenceText $retainedContext.ManifestPath `
                ($duplicateClaimPathManifest | ConvertTo-Json -Depth 16) `
                'Gate B duplicate-claim-path manifest canary'
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
                Write-VerifierEvidenceText $duplicateLiveClaimPath `
                    ($duplicateLiveLease.claim | ConvertTo-Json -Depth 8) `
                    'Gate B duplicate-live-port claim canary'
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
                Write-VerifierEvidenceText $retainedClaimPath `
                    ($wrongProfileKindClaim | ConvertTo-Json -Depth 8) `
                    'Gate B wrong-profile-kind claim canary'
            } {
                Write-VerifierEvidenceText $retainedClaimPath $wrongProfileKindClaimBaseline `
                    'Gate B wrong-profile-kind claim restoration'
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
$forcedNegativeInvocation = [bool]($Task43ForcedNegative -or $Task43PForcedNegative)
$forcedNegativeExpectedMarker = Get-Task43ForcedNegativeExpectedMarker
$requestedExitCode = 2
$cleanupResult = $null
try {
    if ($Task43P -or $Task43PForcedNegative -or
            $PSBoundParameters.ContainsKey('ExpectedCandidateSha')) {
        if ($script:Task43PCandidateSha -ceq '') {
            $script:Task43PCandidateSha = Get-Task43PCandidateSha `
                ([IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..')))
        }
        $script:Task43PInvocationRepositoryState = Get-Task43PRepositoryState
    }
    $executionRoots = Resolve-Task43PExecutionRoots
    $script:Task43PExecutionRepositoryRoot = $executionRoots.RepositoryRoot
    $script:Task43PExecutionWebRoot = $executionRoots.WebRoot
    $script:Task43PExecutionScriptRoot = $executionRoots.ScriptRoot
    $script:Task43PExecutionPreviewScript = $executionRoots.PreviewScript
    $script:Task43PExecutionRoots = $executionRoots
    $script:Task43PExecutionProvenance = $executionRoots.Provenance
    $script:Task43PExpectedExecutionProvenanceDigest = $ExpectedExecutionProvenanceDigest
    Initialize-Task43PSourceExperiment
    $forcedNegativeExpectedMarker = Get-Task43ForcedNegativeExpectedMarker
    $worktreeRoot = $executionRoots.RepositoryRoot
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
                $script:Task43PExecutionPreviewScript $TimeoutSeconds)
        } else {
            $BaseUrl = [string](Set-VerifierCallerOwnedPreview $script:VerifierContext $BaseUrl)
        }
        if (-not $Task43Integrated -and ($Task43P -or $Task43PForcedNegative)) {
            [void](Assert-Task43PPreviewExecutionIdentity $BaseUrl)
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
        if ($null -ne $script:Task43PInvocationRepositoryState) {
            try {
                [void](Get-Task43PRepositoryState $script:Task43PExecutionRoots)
            } catch {
                Invalidate-Task43ForcedNegativeProof
                Set-VerifierFailure $_ 'final candidate provenance' -Quiet
                $requestedExitCode = 2
            }
        }
        if ($forcedNegativeInvocation) {
            if (-not $cleanupExceptionCaught -and $null -ne $cleanupResult -and
                    [bool]$cleanupResult.Success) {
                Set-Task43ForcedNegativeFinalCleanupProven $true
            } else {
                Invalidate-Task43ForcedNegativeProof
            }
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
$infrastructureFailureRecorded = ($script:VerifierFailureExitCode -eq 2 -or
    $script:VerifierFailureKind -eq 'infrastructure' -or
    ([string]$script:VerifierFailureMessage).IndexOf(
        'VERIFIER_INFRASTRUCTURE:', [StringComparison]::OrdinalIgnoreCase) -ge 0 -or
    ($null -ne $cleanupResult -and -not [bool]$cleanupResult.Success))
if ($forcedNegativeInvocation) {
    # A requested exit of 1 is independently revalidated after all run-owned
    # cleanup and parent-ledger writes.  No generic exception classification
    # or stale route flag may authorize the application-failure exit.
    $requestedExitCode = Resolve-Task43ForcedNegativeTopLevelExitCode `
        $requestedExitCode $forcedNegativeExpectedMarker
}
if ($infrastructureFailureRecorded -and $requestedExitCode -ne 2) {
    $requestedExitCode = 2
}
if ($Task43PRuntime -and $requestedExitCode -ne 2) {
    if ($null -eq $script:Task43PRuntimeOutcome -or
            -not $script:Task43PRuntimeRoutePassedAfterCleanup -or
            $null -eq $cleanupResult -or -not [bool]$cleanupResult.Success -or
            $requestedExitCode -ne $script:Task43PRuntimeOutcome.ExitCode) {
        $requestedExitCode = 2
        [Console]::Error.WriteLine('FAIL Task43P runtime final proof - outcome or exact cleanup was not proven.')
    }
}
if ($null -ne $script:Task43PSourceDefinition -and $requestedExitCode -eq 1) {
    try {
        Write-Task43PSourceNegativeFinalProof $requestedExitCode
    } catch {
        Invalidate-Task43ForcedNegativeProof
        $requestedExitCode = 2
        [Console]::Error.WriteLine('FAIL source-negative final proof - verifier infrastructure: ' +
            (Get-VerifierErrorMessage $_))
    }
}
exit ([int]$requestedExitCode)
