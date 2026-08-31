[CmdletBinding()]
param(
    [string]$JavaHome = '',
    [switch]$SkipJdkCheck,
    [switch]$GateBDriverInfrastructureProbe,
    [switch]$GateBHangingChildProbe,
    [switch]$GateBLeaseReleaseProbe,
    [switch]$GateBPreviewIdentityFailureProbe,
    [switch]$GateBStopPreviewProbe,
    [switch]$GateBCdpHandshakeProbe,
    [switch]$GateBArgumentPathProbe,
    [switch]$GateBLateMarkerlessProbe,
    [switch]$GateBRootGoneProbe,
    [switch]$GateBRealEdgeOwnershipProbe,
    [switch]$GateBProcessOwnershipProbe,
    [switch]$GateBProcessStartIdentityProbe,
    [switch]$GateBKernelTransportProbe,
    [switch]$GateBListenerRecordConsumerProbe
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
$script:GateBDriverExitCode = 0

$modulePath = Join-Path $PSScriptRoot 'VerifierIsolation.psm1'
try {
    Import-Module $modulePath -Force
    $repositoryRoot = Get-VerifierFullPath (Join-Path $PSScriptRoot '..')
} catch {
    Write-Host ('FAIL:Gate B deterministic checks - infrastructure exit 2: could not load ' +
        "VerifierIsolation.psm1: $($_.Exception.Message)")
    exit 2
}

function Assert-GateB([bool]$Condition, [string]$Message) {
    if (-not $Condition) { throw "Gate B check failed: $Message" }
}

function Test-GateBExactBooleanProperty($Object, [string]$Name,
        [bool]$Expected) {
    if ($null -eq $Object -or [String]::IsNullOrWhiteSpace($Name) -or
            $null -eq $Object.PSObject.Properties[$Name]) {
        return $false
    }
    $value = $Object.PSObject.Properties[$Name].Value
    if ($null -eq $value -or $value.GetType() -ne [bool]) { return $false }
    return $value -eq $Expected
}

function Throw-GateBInfrastructure([string]$Message) {
    Throw-VerifierInfrastructure ('Gate B infrastructure: ' + $Message)
}

function Assert-GateBInfrastructure([bool]$Condition, [string]$Message) {
    if (-not $Condition) { Throw-GateBInfrastructure $Message }
}

function Resolve-GateBInfrastructureChildExitCode($RawExitCode,
        [bool]$TerminationProven = $true) {
    if (-not $TerminationProven) {
        Throw-GateBInfrastructure 'expected-infrastructure child termination was not proven.'
    }
    if (-not (Test-VerifierStrictIntegralValue $RawExitCode `
            ([int]::MinValue) ([int]::MaxValue))) {
        Throw-GateBInfrastructure 'expected-infrastructure child returned no exact integral exit code.'
    }
    $numericExitCode = [int]$RawExitCode
    if ($numericExitCode -ne 2) {
        Throw-GateBInfrastructure ("expected-infrastructure child returned exit " +
            [string]$numericExitCode + ' instead of infrastructure exit 2.')
    }
    return 2
}

function Resolve-GateBChildExitCode($ChildResult, [string]$Label) {
    if ($null -eq $ChildResult -or
            -not $ChildResult.PSObject.Properties['ExitCode'] -or
            -not $ChildResult.PSObject.Properties['TerminationProven']) {
        Throw-GateBInfrastructure "$Label did not return a complete bounded-child result."
    }
    $exitCode = $ChildResult.PSObject.Properties['ExitCode'].Value
    $terminationProven = $ChildResult.PSObject.Properties['TerminationProven'].Value
    if ($null -eq $exitCode -or
            $exitCode.GetType() -notin @([byte], [sbyte], [int16], [uint16],
                [int32], [uint32], [int64], [uint64]) -or
            $null -eq $terminationProven -or
            $terminationProven.GetType() -ne [bool] -or
            -not $terminationProven) {
        Throw-GateBInfrastructure "$Label returned an unproven or malformed bounded-child status."
    }
    try { $numericExitCode = [long]$exitCode } catch {
        Throw-GateBInfrastructure "$Label returned an out-of-range bounded-child status."
    }
    if ($numericExitCode -notin @(0, 1, 2)) {
        Throw-GateBInfrastructure "$Label returned unexpected exit $numericExitCode."
    }
    return [int]$numericExitCode
}

function Invoke-GateBParserChecks() {
    $files = @(Get-ChildItem -LiteralPath $PSScriptRoot -File |
        Where-Object { $_.Extension -in @('.ps1', '.psm1') })
    Assert-GateB ($files.Count -gt 0) 'no PowerShell verifier sources were found'
    foreach ($file in $files) {
        $tokens = $null
        $parseErrors = $null
        [System.Management.Automation.Language.Parser]::ParseFile(
            $file.FullName, [ref]$tokens, [ref]$parseErrors) | Out-Null
        if ($parseErrors.Count -gt 0) {
            throw (($parseErrors | ForEach-Object {
                "$($file.Name):$($_.Extent.StartLineNumber): $($_.Message)"
            }) -join '; ')
        }
    }
    Write-Host "PASS:PowerShell parser ($($files.Count) files)"
}

function Invoke-GateBSourceChecks() {
    $browserPath = Join-Path $PSScriptRoot 'verify-browser.ps1'
    $moduleText = Get-Content -LiteralPath $modulePath -Raw
    $browserText = Get-Content -LiteralPath $browserPath -Raw
    $previewText = Get-Content -LiteralPath (Join-Path $PSScriptRoot 'preview.ps1') -Raw
    $startPreviewText = Get-Content -LiteralPath (Join-Path $PSScriptRoot 'start-preview.ps1') -Raw
    $stopPreviewText = Get-Content -LiteralPath (Join-Path $PSScriptRoot 'stop-preview.ps1') -Raw
    $gateText = Get-Content -LiteralPath (Join-Path $PSScriptRoot 'verify-gate-b.ps1') -Raw
    $buildText = Get-Content -LiteralPath (Join-Path $PSScriptRoot 'build.ps1') -Raw
    $readmeText = Get-Content -LiteralPath (Join-Path $repositoryRoot 'README.md') -Raw
    $developmentText = Get-Content -LiteralPath (Join-Path $repositoryRoot 'DEVELOPMENT.md') -Raw
    $workflowPath = Join-Path $repositoryRoot '.github\workflows\windows-gate-b.yml'
    Assert-GateB (Test-Path -LiteralPath $workflowPath -PathType Leaf) `
        'Windows Gate B workflow is missing'
    $workflowText = Get-Content -LiteralPath $workflowPath -Raw

    # The live Edge ownership lane is supplemental and opt-in. Keep this
    # structural guard close to the source checks so a future default-driver
    # call cannot silently turn the protected deterministic job into a
    # machine-dependent live-browser check.
    $driverBodyMatch = [regex]::Match($gateText,
        '(?ms)function\s+Invoke-GateBDriver\s*\(\s*\)\s*\{\s*(?<body>.*?)\r?\n\}\r?\n\r?\n\$driverResult')
    Assert-GateB $driverBodyMatch.Success `
        'Gate B driver body could not be located for live-Edge separation'
    $driverBody = $driverBodyMatch.Groups['body'].Value
    $optInRealEdgeMatch = [regex]::Match($driverBody,
        '(?ms)if\s*\(\$GateBRealEdgeOwnershipProbe\)\s*\{\s*Invoke-GateBRealEdgeOwnershipCanary\s*\r?\n\s*return\s+0\s*\}')
    Assert-GateB $optInRealEdgeMatch.Success `
        'live-Edge ownership canary lost its explicit opt-in branch'
    $defaultDriverBody = $driverBody.Replace($optInRealEdgeMatch.Value, '')
    Assert-GateB ($defaultDriverBody -notmatch '\bInvoke-GateBRealEdgeOwnershipCanary\b') `
        'default Gate B driver invokes the supplemental live-Edge ownership canary'

    foreach ($token in @(
        'New-VerifierRunContext',
        'Get-VerifierProcessStartTicks',
        'New-VerifierBrowserSession',
        'Complete-VerifierBrowserSession',
        'Connect-VerifierCdpSocket',
        'Start-VerifierOwnedPreview',
        'Complete-VerifierRun',
        'Test-VerifierChildContract',
        'Test-VerifierProcessIdentity',
        'Test-VerifierPreviewProcessIdentity',
        'Resolve-VerifierBrowserPath',
        'Select-VerifierRelevantProcessRecords',
        'Get-VerifierBrowserOwnershipSnapshot',
        'Get-VerifierDescendantCandidateRecords',
        'Stop-VerifierVerifiedProcessExactly',
        'Test-VerifierDescendantOwnership',
        'Test-VerifierDescendantExecutableIdentity',
        'Test-VerifierConfiguredExecutableIdentity',
        'A cdp lease requires a resolved, existing BrowserPath executable identity',
        'Root browser PID',
        'BrowserPath = [string]$BrowserPath',
        'Get-VerifierCommandLineSwitchPresence',
        'Test-VerifierPreviewCleanupReadiness',
        'FailNextPreviewIdentityCapture',
        'New-VerifierPortLease',
        'Confirm-VerifierPortLeaseBound',
        'Merge-VerifierFailureExitCode',
        'Write-VerifierManifest',
        'Test-VerifierListenerOwnerTuple',
        'Assert-VerifierDurableListenerOwnerTuple',
        'Get-VerifierLoopbackListenerRecords',
        'Parse-VerifierNetstatListenerOutput',
        'Test-VerifierListenerRecordSchema',
        'Test-VerifierRunOwnedPreviewHttpSysAuthorization',
        'Test-VerifierKernelTransportListenerRecord',
        'Test-VerifierRunOwnedPreviewHttpSysListener',
        'VerifierUserProcessOwnerKind',
        'VerifierUserProcessOwnerProof',
        'VerifierUserProcessOwnerEvidence',
        'kernel-transport',
        'run-owned-preview-http-sys-v1',
        'pid-4-system-http-sys',
        'ProcessStartTicks = $null',
        'Refusing to terminate PID 4',
        'Write-VerifierLeaseRollbackRecord',
        'FailNextManifestWrite',
        'FailNextClaimWrite',
        'FailNextLeaseRelease',
        'FailNextLeaseMutexDispose',
        'FailNextFinalManifestWrite',
        'FailNextPostDeleteBeforeFinalState',
        'ProfileInspectionFailed',
        'Assert-VerifierBrowserProfileIsQuiescent',
        'Assert-VerifierProfileSnapshotQuiescent',
        'Get-VerifierProfileReferenceRecords',
        'Test-VerifierCanonicalWindowsPathValue',
        'Get-VerifierCanonicalWindowsPath',
        'Get-VerifierPhysicalExistingPath',
        'Test-VerifierPhysicalChildPath',
        'Assert-VerifierNoReparseAncestors',
        'Assert-VerifierNoReparseTree',
        'Remove-VerifierOwnedTree',
        'Test-VerifierCurrentProcessRecordMatches',
        'Get-VerifierCommandLineSwitchPresence',
        'ExecutablePath',
        'configured browser executable identity',
        'Get-VerifierCurrentOwnedProcess',
        'Get-VerifierCurrentProcessIdentity',
         'Get-VerifierCurrentProcessRecordById',
         'Confirm-VerifierRecordedProcessAbsent',
         'Confirm-VerifierReleasedListener',
        'Get-VerifierPortMutexName',
         'Test-VerifierCurrentProcessCarriesOwnership',
         'Test-VerifierCommandLineEquivalent',
         'Test-VerifierCommandLineCanonicalPathToken',
         'ProcessParentProcessId',
        'ProcessCommandLine',
        'leaseId',
        'Stop-Process -InputObject',
        'RunNamespaceRoot',
        'processTerminationProven',
        'listenerInspectionProven',
        'Assert-VerifierProcessSnapshotComplete',
        'Success = $Success',
        'Known = $Known',
        'HasListeners ='
    )) {
        Assert-GateB ($moduleText.IndexOf($token, [StringComparison]::Ordinal) -ge 0) `
            "isolation module is missing $token"
    }
    foreach ($token in @(
        'Throw-VerifierInfrastructure',
        'Resolve-VerifierBrowserPath',
        'VerifierResolvedBrowserPath',
        'Write-VerifierEarlySetupFailure',
        'Get-VerifierEarlySetupMessage',
        '$modulePath = Join-Path $PSScriptRoot',
        'tsjVerifierRun=',
        'tsjVerifierRoute=',
        'getVerifierEvidencePath',
        'Set-VerifierCallerOwnedPreview',
        'Start-VerifierOwnedPreview',
        'Get-VerifierRouteFailureExitCode',
        'Resolve-VerifierChildExitCode',
        'Invoke-VerifierIntegratedTimeoutResourceProof',
        'Assert-IntegratedChildOutputContract',
        'FAIL:task43-forced-negative-canary',
        'FAIL:task43p-forced-negative-canary',
        'isExpectedTask43ForcedFailureDiagnostic',
        'Get-Task43ForcedNegativeExpectedMarker',
        'Test-Task43ForcedNegativeProof',
        'Resolve-Task43ForcedNegativeTopLevelExitCode',
        'Assert-VerifierIntegratedForcedNegativeProof',
        'forcedNegativeProof',
        'ExpectedForcedMarker',
        'Get-VerifierIntegratedChildShellStatusTail',
        '$tsjChildSuccess',
        'Invoke-Task43ForcedNegativeContractCanaries',
        'Task43ForcedNegative',
        'GateBContractProbe',
        'GateBContractProbeFailure',
        'GateBListenerProofProbe',
        'parentTimeoutPath',
        'ParentNamespaceRoot',
        'Get-VerifierLedgerPath',
        'Get-VerifierLedgerProperty',
        'Invoke-GateBListenerProofCanary',
        'Test-VerifierIntegratedListenerOwnerSchema',
        'expected-exit-2 ledger resource proof',
        'real-completed.json',
        'false-cleanup-flags',
        'custom-mutex',
        'wrong-preview-script',
        'non-loopback-base-url',
        'wrong-server-port',
         'foreign-server-log',
         'duplicate-live-port',
         'invalid-port-',
         'zero-claim-owner',
         'negative-claim-owner-start',
         'wrong-profile-lease-kind',
         'caller-owned integrated ledger canary',
         'callerProofTypeCases',
         'caller-proof-type-',
         'Test-VerifierStrictBooleanProperty $callerProofCopy.Value',
         'strict Boolean proof negative cases',
         'caller-verified',
         'identityProtocol',
         'repositoryRoot',
         'webRoot',
         'retainedServerLedger',
        'canonicalPreviewScript',
        'expectedBaseUrl',
        'expectedStdoutLog',
        'GateBExplicitExit2Probe',
        'GateBExplicitExit2TypedProbe',
        'VerifierExitCode',
        'Merge-VerifierFailureExitCode',
        'Throw-VerifierRouteDeadline',
        'output streams did not close within',
        'requiresBrowserExecutableIdentity',
        'expectedBrowserPath',
        'missing-configured-BrowserPath'
    )) {
        Assert-GateB ($browserText.IndexOf($token, [StringComparison]::Ordinal) -ge 0) `
            "browser verifier is missing $token"
    }
    foreach ($token in @(
        '/__tsj/verify-identity',
        'repositoryRoot',
        'previewScript',
        'verifierNonce',
        'processStartTicks',
        'previewPort'
    )) {
        Assert-GateB ($previewText.IndexOf($token, [StringComparison]::Ordinal) -ge 0) `
            "preview identity contract is missing $token"
    }
    Assert-GateB ($previewText.IndexOf(
        'Assert-VerifierPhysicalOwnedPath $webRoot $webRoot -AllowRoot',
        [StringComparison]::Ordinal) -ge 0) `
        'preview serving does not recheck the owned web root with an explicit root allowance'
    Assert-GateB ($startPreviewText.IndexOf('Start-VerifierProcess', [StringComparison]::Ordinal) -ge 0) `
        'developer preview launch does not use the shared Windows argument helper'
    Assert-GateB ($startPreviewText.IndexOf('Invoke-VerifierBoundedProcess', [StringComparison]::Ordinal) -ge 0 -and
        $startPreviewText.IndexOf('$versionResult', [StringComparison]::Ordinal) -ge 0 -and
        $startPreviewText.IndexOf('$buildResult', [StringComparison]::Ordinal) -ge 0) `
        'developer preview JDK/build probes are not bounded by the shared process runner'
    Assert-GateB ($startPreviewText -notmatch '(?m)^\s*&\s*\$javaPath\b' -and
        $startPreviewText -notmatch '(?m)^\s*&\s*\$powershell\b') `
        'developer preview retains an unbounded Java or build subprocess invocation'
    foreach ($token in @(
        'Get-VerifierCurrentProcessIdentity',
        'Stop-VerifierVerifiedProcessExactly',
        'WaitForExit',
        'Get-VerifierLoopbackListenerRecords',
        'Remove-VerifierOwnedTree'
    )) {
        Assert-GateB ($stopPreviewText.IndexOf($token, [StringComparison]::Ordinal) -ge 0) `
            "stop-preview contract is missing $token"
    }
    Assert-GateB ($stopPreviewText -notmatch 'Stop-Process\s+-Id') `
        'stop-preview retains PID-only termination'
    Assert-GateB ($startPreviewText.IndexOf('"`"$previewScript`""', [StringComparison]::Ordinal) -lt 0) `
        'developer preview launch retains ad-hoc script-path quoting'
    Assert-GateB ($startPreviewText.IndexOf('$previewRootUrl =', [StringComparison]::Ordinal) -ge 0 -and
        $startPreviewText.IndexOf('Preview root URL (for verify-browser -BaseUrl):',
            [StringComparison]::Ordinal) -ge 0 -and
        $startPreviewText.IndexOf('Preview page URL:', [StringComparison]::Ordinal) -ge 0) `
        'developer preview does not print separate root and page URLs'
    Assert-GateB ($startPreviewText.IndexOf('$previewRootUrl = "http://127.0.0.1:$Port"',
        [StringComparison]::Ordinal) -ge 0 -and
        $startPreviewText.IndexOf('"$previewRootUrl/circuitjs.html?',
            [StringComparison]::Ordinal) -ge 0) `
        'developer preview root/page URL source contract is not path/query separated'
    Assert-GateB ($readmeText.IndexOf('pass the exact Preview root URL',
        [StringComparison]::OrdinalIgnoreCase) -ge 0 -and
        $developmentText.IndexOf('pass the exact Preview root URL',
            [StringComparison]::OrdinalIgnoreCase) -ge 0) `
        'caller-owned preview guidance does not require the root URL'
    Assert-GateB ($readmeText.IndexOf('Preview page URL is not a verifier BaseUrl',
        [StringComparison]::OrdinalIgnoreCase) -ge 0 -and
        $developmentText.IndexOf('Preview page URL is not a verifier BaseUrl',
            [StringComparison]::OrdinalIgnoreCase) -ge 0) `
        'caller-owned preview guidance does not reject the page URL as BaseUrl'

    # Ordinary developer preview ports are explicitly excluded by the shared
    # allocator, but they must never appear as verifier URL/launch defaults.
    foreach ($ordinaryPort in @('8888', '8898', '8899', '9876')) {
        foreach ($forbiddenDefault in @(
                ('http://127.0.0.1:' + $ordinaryPort),
                ('127.0.0.1:' + $ordinaryPort),
                ('--remote-debugging-port=' + $ordinaryPort),
                ("'-Port', '" + $ordinaryPort))) {
            Assert-GateB ($browserText.IndexOf($forbiddenDefault, [StringComparison]::OrdinalIgnoreCase) -lt 0) `
                "browser verifier still embeds ordinary port $ordinaryPort as a launch/default value"
        }
    }
    Assert-GateB ($moduleText -match '\$ordinaryPorts\s*=\s*@\(8888,\s*8898,\s*8899,\s*9876\)') `
        'shared allocator does not explicitly exclude ordinary developer ports'
    foreach ($fixedFamily in @('9350', '9450', '9490', '9500', '9600', '9700')) {
        Assert-GateB ($browserText.IndexOf($fixedFamily, [StringComparison]::Ordinal) -lt 0) `
            "browser verifier still embeds legacy CDP port family $fixedFamily"
    }
    foreach ($forbidden in @(
        'Start-Process -FilePath $BrowserPath',
        'Get-CimInstance',
        'Stop-Process -Name',
        'Get-Process *',
        'taskkill',
        'tsj-browser-'
    )) {
        Assert-GateB ($browserText.IndexOf($forbidden, [StringComparison]::OrdinalIgnoreCase) -lt 0) `
            "browser verifier retains forbidden broad/shared ownership path '$forbidden'"
    }
    Assert-GateB ($browserText -match '\[string\]\$BaseUrl\s*=\s*''\s*') `
        'omitted BaseUrl is not routed to run-owned preview allocation'
    Assert-GateB ($browserText -match '\[string\]\$BrowserPath\s*=\s*''\s*' -and
        $browserText.IndexOf('Resolve-VerifierBrowserPath $BrowserPath',
            [StringComparison]::Ordinal) -ge 0) `
        'browser verifier does not use the shared PATH/Program Files browser resolver'
    Assert-GateB ([regex]::Matches($browserText, "Page\.navigate").Count -eq 1) `
        'browser navigation is not centralized in the shared route path'
    Assert-GateB ([regex]::Matches($browserText, 'startVerifierBrowser').Count -ge 10) `
        'specialized verifier routes do not use the shared browser launch path'
    foreach ($token in @(
        'actions/setup-java@v4',
        "java-version: '8'",
        'scripts\build.ps1',
        'scripts\verify-gate-b.ps1 -JavaHome',
        'Gate B Windows JDK8 deterministic verification',
        'visible Browser validation remains a separate required/manual lane'
    )) {
        Assert-GateB ($workflowText.IndexOf($token, [StringComparison]::OrdinalIgnoreCase) -ge 0) `
            "Windows Gate B workflow is missing $token"
    }

    Assert-GateB ($moduleText.IndexOf('Threading.Mutex', [StringComparison]::Ordinal) -ge 0) `
        'port claims are not held by a named mutex'
    Assert-GateB ($moduleText.IndexOf("return 'Global\TroubleshootJS.Verifier.Port.' + [string]`$Port", `
        [StringComparison]::Ordinal) -ge 0) `
        'port mutex identity is not global per port'
    Assert-GateB ($moduleText -notmatch "Verifier\.Port\.'\s*\+\s*`$Context\.RepositoryIdentity") `
        'port mutex identity is still scoped by repository/worktree'
    Assert-GateB ($moduleText.IndexOf('ClaimState', [StringComparison]::Ordinal) -ge 0 -and
        $moduleText.IndexOf('Confirm-VerifierPortLeaseBound', [StringComparison]::Ordinal) -ge 0) `
        'port claims do not record retained-bind validation'
    foreach ($token in @(
        'Invoke-VerifierBoundedProcess',
        'Stop-VerifierBoundedProcessExactly',
        'RedirectStandardOutput',
        'RedirectStandardError',
        'netstatResult.ExitCode',
        'WaitForExit($WaitMilliseconds)'
    )) {
        Assert-GateB ($moduleText.IndexOf($token, [StringComparison]::Ordinal) -ge 0) `
            "listener bounded-process implementation is missing $token"
    }
    foreach ($token in @(
        'Invoke-BuildBoundedProcess',
        'Stop-BuildProcessExactly',
        'Get-CimInstance Win32_Process',
        'Stop-VerifierVerifiedProcessExactly',
        'ProcessTimeoutSeconds',
        'BuildProcessCanary',
        'RedirectStandardOutput',
        'RedirectStandardError',
        'WaitForExit'
    )) {
        Assert-GateB ($buildText.IndexOf($token, [StringComparison]::Ordinal) -ge 0) `
            "build bounded-process implementation is missing $token"
    }
    Assert-GateB ($buildText -notmatch 'Stop-Process\s+-Id') `
        'build retains PID-only termination'
    foreach ($token in @('Get-CimInstance Win32_Process',
            'Stop-VerifierVerifiedProcessExactly', 'WaitForExit(5000)',
            'current identity validation')) {
        Assert-GateB ($gateText.IndexOf($token, [StringComparison]::Ordinal) -ge 0) `
            "Gate B termination proof is missing $token"
    }
    Assert-GateB ($gateText -notmatch 'Stop-Process\s+-Id') `
        'Gate B canary retains PID-only termination'
    Assert-GateB ($gateText -notmatch '(?im)^\s*ProcessStartTicks\s*=\s*\[DateTime\]::UtcNow\.Ticks\s*$') `
        'Gate B contains a current-time ProcessStartTicks identity fixture'
    foreach ($directStartTimeSource in @(
        $browserText, $previewText, $startPreviewText, $buildText, $gateText
    )) {
        Assert-GateB ($directStartTimeSource -notmatch '(?m)\$[A-Za-z_][A-Za-z0-9_]*\.StartTime\b') `
            'a verifier-side direct StartTime read bypasses the shared bounded identity contract'
    }
    Assert-GateB ([regex]::Matches($moduleText, '(?m)\$Process\.StartTime\b').Count -eq 1) `
        'the isolation module has more than one raw retained-process StartTime boundary'
    Assert-GateB ($browserText -match '(?ms)function\s+Test-VerifierIntegratedListenerOwnerSchema\b') `
        'integrated listener owner schema validation is missing'
    $integratedOwnerFunctionMatch = [regex]::Match($browserText,
        '(?ms)function\s+Test-VerifierIntegratedListenerOwnerSchema\b.*?(?=\r?\nfunction\s+)')
    Assert-GateB $integratedOwnerFunctionMatch.Success `
        'integrated listener owner schema boundary could not be located'
    Assert-GateB ($integratedOwnerFunctionMatch.Groups[0].Value -match
        '\bTest-VerifierListenerOwnerTuple\b') `
        'integrated listener owner schema does not delegate to the canonical module validator'
    $parentOwnerFunctionMatch = [regex]::Match($browserText,
        '(?ms)function\s+Assert-VerifierParentLedgerListenerOwnerTuple\b.*?(?=\r?\nfunction\s+)')
    Assert-GateB $parentOwnerFunctionMatch.Success `
        'parent ledger listener owner boundary could not be located'
    Assert-GateB ($parentOwnerFunctionMatch.Groups[0].Value -match
        '\bAssert-VerifierDurableListenerOwnerTuple\b') `
        'parent ledger listener owner validation does not delegate to the canonical module validator'
    $serverOwnerFunctionMatch = [regex]::Match($browserText,
        '(?ms)function\s+Test-VerifierIntegratedServerListenerOwnerSchema\b.*?(?=\r?\nfunction\s+)')
    Assert-GateB $serverOwnerFunctionMatch.Success `
        'integrated server listener owner boundary could not be located'
    Assert-GateB ($serverOwnerFunctionMatch.Groups[0].Value -match
        '\bTest-VerifierListenerOwnerTuple\b') `
        'integrated server listener owner validation does not delegate to the canonical module validator'
    Assert-GateB ($browserText -notmatch '(?ms)\$listenerOwnerKind\s*=\s*if\s*\(\$null\s*-eq\s*\$listenerOwnerKindProperty') `
        'integrated listener validation silently defaults a missing owner kind'
    Assert-GateB ($browserText -notmatch '(?ms)\$listenerOwnerProof\s*=\s*\[string\]\(Get-VerifierLedgerProperty\s+\$Lease\s+''listenerOwnerProof''') `
        'integrated listener validation silently defaults a missing owner proof'
    Assert-GateB ($browserText -notmatch '(?ms)\$listenerOwnerEvidence\s*=\s*\[string\]\(Get-VerifierLedgerProperty\s+\$Lease\s+''listenerOwnerEvidence''') `
        'integrated listener validation silently defaults missing owner evidence'
    $kernelRecordFunctionMatch = [regex]::Match($moduleText,
        '(?ms)function\s+New-VerifierKernelTransportListenerRecord\b.*?(?=\r?\nfunction\s+)')
    Assert-GateB $kernelRecordFunctionMatch.Success `
        'the kernel transport listener record boundary could not be located'
    $kernelRecordFunctionCode = $kernelRecordFunctionMatch.Groups[0].Value -replace
        '(?m)^\s*#.*$', ''
    Assert-GateB ($kernelRecordFunctionCode -notmatch
        '\(\[string\]\$LocalAddress|\[int\]\$Port|\[string\]\$Source') `
        'kernel transport listener constructor binds raw listener scalars before validation'
    Assert-GateB ($kernelRecordFunctionCode -notmatch
        'StartTime|Get-VerifierProcessStartTicks|Get-VerifierProcessById|Stop-Process') `
        'kernel transport listener classification reads or terminates a process identity'
    $listenerReaderFunctionMatch = [regex]::Match($moduleText,
        '(?ms)function\s+Get-VerifierListenerProcessRecord\b.*?(?=\r?\nfunction\s+)')
    Assert-GateB $listenerReaderFunctionMatch.Success `
        'listener process-record reader boundary could not be located'
    $listenerReaderFunctionCode = $listenerReaderFunctionMatch.Groups[0].Value
    Assert-GateB ($listenerReaderFunctionCode -notmatch
        '\(\[int\]\$ProcessId|\[string\]\$LocalAddress|\[int\]\$Port|\[string\]\$Source') `
        'listener process-record reader binds raw listener scalars before validation'
    foreach ($rawListenerFunctionDefinition in @(
        [pscustomobject]@{ Name = 'netstat parser'; Pattern = '(?ms)function\s+Parse-VerifierNetstatListenerOutput\b.*?(?=\r?\nfunction\s+)' }
        [pscustomobject]@{ Name = 'loopback listener reader'; Pattern = '(?ms)function\s+Get-VerifierLoopbackListenerRecords\b.*?(?=\r?\nfunction\s+)' }
    )) {
        $rawListenerFunctionMatch = [regex]::Match($moduleText,
            $rawListenerFunctionDefinition.Pattern)
        Assert-GateB $rawListenerFunctionMatch.Success `
            "$($rawListenerFunctionDefinition.Name) boundary could not be located"
        $rawListenerFunctionCode = $rawListenerFunctionMatch.Groups[0].Value
        Assert-GateB ($rawListenerFunctionCode -notmatch '\(\[int\]\$Port') `
            "$($rawListenerFunctionDefinition.Name) binds its raw port before validation"
        Assert-GateB ($rawListenerFunctionCode.IndexOf(
            'Test-VerifierStrictIntegralValue $Port 1 65535',
            [StringComparison]::Ordinal) -ge 0) `
            "$($rawListenerFunctionDefinition.Name) lacks strict raw port validation"
    }
    $pid4BranchMatch = [regex]::Match($moduleText,
        '(?ms)if\s*\(\$ProcessId\s*-eq\s*4\).*?return\s+\(New-VerifierKernelTransportListenerRecord')
    Assert-GateB $pid4BranchMatch.Success `
        'listener PID 4 does not have an explicit transport-owner branch'
    Assert-GateB ($pid4BranchMatch.Groups[0].Value -notmatch
        'StartTime|Get-VerifierProcessStartTicks|Get-VerifierProcessById|Stop-Process') `
        'listener PID 4 branch falls through to a process identity or termination path'
    Assert-GateB ($moduleText -match
        '(?ms)Get-VerifierLoopbackListenerRecords\s*\(\[int\]\$Lease\.Port\)\s*\$Context\s*\$previewListenerOwner') `
        'run-owned preview bind does not pass its exact listener authorization context'
    Assert-GateB ($moduleText -match
        '(?ms)Test-VerifierRunOwnedPreviewHttpSysAuthorization.*?IdentityVerified.*?ProcessIdentityKnown.*?ProcessCommandLine') `
        'kernel listener authorization does not require retained preview identity proof'
    $listenerInspectionFunctionMatch = [regex]::Match($moduleText,
        '(?ms)function\s+Set-VerifierLeaseListenerInspection\b.*?(?=\r?\nfunction\s+)')
    Assert-GateB $listenerInspectionFunctionMatch.Success `
        'listener lease inspection preservation boundary could not be located'
    $listenerInspectionFunctionCode = $listenerInspectionFunctionMatch.Groups[0].Value
    Assert-GateB ($listenerInspectionFunctionCode.IndexOf('$hasListeners =',
        [StringComparison]::Ordinal) -ge 0 -and
        $listenerInspectionFunctionCode.IndexOf('if ($hasListeners)',
            [StringComparison]::Ordinal) -ge 0 -and
        $listenerInspectionFunctionCode.IndexOf(
            '$Lease.ListenerOwnerKind = $ownerKind',
            [StringComparison]::Ordinal) -ge 0) `
        'listener inspection does not update durable owner proof only for positive observations'
    Assert-GateB ($listenerInspectionFunctionMatch.Groups[0].Value -notmatch
        'ListenerOwnerKind\s*=\s*if\s*\(\$Inspection\.PSObject\.Properties\[\x27HasListeners\x27\]') `
        'listener absence inspection overwrites the durable owner proof tuple'
    Assert-GateB ($moduleText -match
        '(?ms)function\s+Assert-VerifierDurableListenerOwnerTuple\b.*?Test-VerifierListenerOwnerTuple') `
        'durable manifest listener owner tuples are not validated before projection'
    Assert-GateB ($moduleText -match
        '(?ms)function\s+Write-VerifierManifest\b.*?Assert-VerifierDurableManifestContext') `
        'manifest writer does not reject malformed listener owner evidence before any write'
    Assert-GateB ($moduleText -match
        'Positive listener inspection lacked canonical live owner authorization') `
        'listener inspection setter does not require canonical live positive authorization'
    Assert-GateB ($moduleText -match
        '(?ms)function\s+Assert-VerifierPreviewIdentitySchema\b.*?previewPort') `
        'caller-owned preview identity does not have an exact scalar schema boundary'
    $listenerSchemaFunctionMatch = [regex]::Match($moduleText,
        '(?ms)function\s+Test-VerifierListenerRecordSchema\b.*?(?=\r?\nfunction\s+)')
    Assert-GateB $listenerSchemaFunctionMatch.Success `
        'shared live listener-record schema validator could not be located'
    $listenerSchemaFunctionCode = $listenerSchemaFunctionMatch.Groups[0].Value
    Assert-GateB ($listenerSchemaFunctionCode.IndexOf(
        'Test-VerifierListenerOwnerTuple', [StringComparison]::Ordinal) -ge 0) `
        'shared listener-record schema validator does not delegate owner semantics to the canonical tuple validator'
    Assert-GateB ($listenerSchemaFunctionCode.IndexOf(
        'Test-VerifierRunOwnedPreviewHttpSysAuthorization',
        [StringComparison]::Ordinal) -ge 0) `
        'shared listener-record schema validator is missing kernel owner context validation'
    Assert-GateB ($listenerSchemaFunctionCode -notmatch
        'Verifier(UserProcess|KernelTransport)Owner(Proof|Evidence)') `
        'shared listener-record schema validator duplicates canonical owner proof policy'
    $listenerInspectionFunctionMatch = [regex]::Match($moduleText,
        '(?ms)function\s+Test-VerifierListenerInspectionSchema\b.*?(?=\r?\nfunction\s+)')
    Assert-GateB $listenerInspectionFunctionMatch.Success `
        'shared listener-inspection schema validator could not be located'
    $listenerInspectionFunctionCode = $listenerInspectionFunctionMatch.Groups[0].Value
    Assert-GateB ($listenerInspectionFunctionCode.IndexOf(
        'Test-VerifierListenerOwnerTuple', [StringComparison]::Ordinal) -ge 0) `
        'shared listener-inspection schema validator does not delegate owner semantics to the canonical tuple validator'
    Assert-GateB ($listenerInspectionFunctionCode -notmatch
        'Verifier(UserProcess|KernelTransport)Owner(Proof|Evidence)') `
        'shared listener-inspection schema validator duplicates canonical owner proof policy'
    $durableStringPairFunctionMatch = [regex]::Match($browserText,
        '(?ms)function\s+Assert-VerifierDurableStringPair\b.*?(?=\r?\nfunction\s+)')
    Assert-GateB $durableStringPairFunctionMatch.Success `
        'durable string-pair validator could not be located'
    Assert-GateB ($durableStringPairFunctionMatch.Groups[0].Value.IndexOf(
        '[StringComparison]::Ordinal', [StringComparison]::Ordinal) -ge 0) `
        'durable string-pair validator does not use ordinal equality'
    Assert-GateB ($moduleText.IndexOf("else { 'user-process' }",
        [StringComparison]::Ordinal) -lt 0) `
        'live listener consumers retain a missing-kind user-process fallback'
    foreach ($listenerConsumerDefinition in @(
        [pscustomobject]@{ Name = 'ownership'; Pattern = '(?ms)function\s+Test-VerifierListenerBelongsToOwner\b.*?(?=\r?\nfunction\s+)' },
        [pscustomobject]@{ Name = 'bind'; Pattern = '(?ms)function\s+Confirm-VerifierPortLeaseBound\b.*?(?=\r?\nfunction\s+)' },
        [pscustomobject]@{ Name = 'inspection setter'; Pattern = '(?ms)function\s+Set-VerifierLeaseListenerInspection\b.*?(?=\r?\nfunction\s+)' },
        [pscustomobject]@{ Name = 'release'; Pattern = '(?ms)function\s+Confirm-VerifierReleasedListener\b.*?(?=\r?\nfunction\s+)' },
        [pscustomobject]@{ Name = 'inspection creation'; Pattern = '(?ms)function\s+New-VerifierListenerInspection\b.*?(?=\r?\nfunction\s+)' }
    )) {
        $consumerMatch = [regex]::Match($moduleText, $listenerConsumerDefinition.Pattern)
        Assert-GateB $consumerMatch.Success `
            "listener $($listenerConsumerDefinition.Name) consumer boundary could not be located"
        Assert-GateB ($consumerMatch.Groups[0].Value.IndexOf(
            'Test-VerifierListenerRecordSchema', [StringComparison]::Ordinal) -ge 0) `
            "listener $($listenerConsumerDefinition.Name) consumer does not use the shared exact schema validator"
    }
    $startIdentityFunctionMatch = [regex]::Match($moduleText,
        '(?ms)function\s+Get-VerifierProcessStartTicks\b.*?(?=\r?\nfunction\s+)')
    Assert-GateB $startIdentityFunctionMatch.Success `
        'the shared bounded process-identity helper could not be located'
    $startIdentityFunctionText = $startIdentityFunctionMatch.Value
    Assert-GateB ($startIdentityFunctionText -notmatch 'Get-Process\s+-Id|Get-VerifierProcessById') `
        'the shared process-identity retry loop re-fetches a process by PID'
    Assert-GateB ($buildText -notmatch '(?m)^\s*&\s*\$java\b') `
        'build still invokes Java through an unbounded direct call'
    Assert-GateB ($moduleText -notmatch '(?m)^\s*\$netstatOutput\s*=\s*@\(\s*&') `
        'listener inspection still invokes netstat through an unbounded direct call'
    Assert-GateB ([regex]::Matches($workflowText, 'actions/setup-java@v4').Count -eq 1) `
        'workflow JDK setup is not deterministic'
    Assert-GateB ($workflowText -match '(?m)^\s{4}timeout-minutes:\s*15\s*$') `
        'workflow has no bounded job timeout backstop'
    Assert-GateB ($gateText.IndexOf('Invoke-GateBListenerInspectionFailureCheck',
        [StringComparison]::Ordinal) -ge 0 -and
        $gateText.IndexOf('GateBDriverInfrastructureProbe', [StringComparison]::Ordinal) -ge 0) `
        'deterministic listener inspection check is not wired into Gate B'
    foreach ($token in @(
        'ConvertTo-VerifierWindowsArgument',
        'ConvertTo-VerifierArgumentString',
        'Start-VerifierProcess',
        'Start-GateBRedirectedProcess',
        'Resolve-VerifierIntegratedChildExitCode',
        'Resolve-GateBInfrastructureChildExitCode',
        'GateBDriverExitCode',
        'Invoke-GateBModuleImportSetupCheck',
        'Invoke-GateBCommandLineRoundTripCheck',
        'Assert-VerifierProfileSnapshotQuiescent',
        'canonical-path/late-helper',
        'Test-VerifierCurrentProcessRecordMatches',
        'PID-replacement',
        'equivalentWorktree',
        'equivalentStatePath',
        'Invoke-GateBBoundedProcess',
        'Invoke-GateBHangingChildCanary',
        'Invoke-GateBProcessStartIdentityCanary',
        'Invoke-GateBPreviewIdentityFailureCanary',
        'Invoke-GateBDescendantCleanupCanary',
        'Invoke-GateBLateMarkerlessCleanupCanary',
         'Invoke-GateBRootGoneCleanupCanary',
         'Invoke-GateBRealEdgeOwnershipCanary',
         'root-gone',
         'different-name-markerless-helper',
         'late-markerless',
         'fixed-point',
         'missing-descendant-executable',
         'BrowserDrainAfterInitialGraphSignalPath',
         'rootGoneRejected',
         'profile and port ownership are retained',
         'markerless Edge helper',
         'real Edge ownership canary',
         'missing configured BrowserPath',
         'Invoke-GateBStopPreviewContractCanary',
        'GateBHangAfterContext',
        'New-VerifierIntegratedChildLedger',
        'Read-VerifierIntegratedChildLedger',
         'ReadToEndAsync',
         'output streams did not close within',
         'child-status',
        'Stop-GateBListenerExact',
        'WaitForExit(5000)',
        'competitionResult.TerminationProven',
        'competitionResult.ExitCode',
        'competitionTerminationProven',
        'CLEANUP_JSON',
        'troubleshootjs-child-cleanup-v1',
        'lease-release:',
        'complete-exception:',
        'Assert-GateBContextResourcesReleased',
        'cleanup-failure evidence-retention canary',
        'isolation canary cleanup failed; run evidence was retained'
    )) {
        Assert-GateB ($gateText.IndexOf($token, [StringComparison]::Ordinal) -ge 0) `
            "Gate B canary is missing $token"
    }
    $descendantFunctionStart = $moduleText.IndexOf(
        'function Get-VerifierDescendantProcessRecords', [StringComparison]::Ordinal)
    $descendantFunctionText = if ($descendantFunctionStart -ge 0) {
        $moduleText.Substring($descendantFunctionStart)
    } else { '' }
    Assert-GateB ($descendantFunctionText.IndexOf('$rootOwner.PSObject.Properties',
        [StringComparison]::Ordinal) -ge 0 -and
        $descendantFunctionText.IndexOf('$ownerRoot.PSObject.Properties',
            [StringComparison]::Ordinal) -lt 0) `
        'descendant cleanup can authorize a child through an unset/colliding owner-root variable'
    $browserCleanupStart = $moduleText.IndexOf(
        'function Complete-VerifierBrowserSession', [StringComparison]::Ordinal)
    $browserCleanupText = if ($browserCleanupStart -ge 0) {
        $moduleText.Substring($browserCleanupStart)
    } else { '' }
    Assert-GateB ($browserCleanupText.IndexOf('$sessionRoot = $null',
        [StringComparison]::Ordinal) -ge 0 -and
        $browserCleanupText.IndexOf('$ownerRoot = $null',
        [StringComparison]::Ordinal) -ge 0 -and
        $browserCleanupText.IndexOf(
            'Stop-VerifierBrowserProcessTreeToFixedPoint',
            [StringComparison]::Ordinal) -ge 0 -and
        $moduleText.IndexOf(
            'Get-VerifierDescendantProcessRecords $OwnerRoot $snapshot',
            [StringComparison]::Ordinal) -ge 0) `
        'browser cleanup does not initialize one exact owner-root/session-root record and use the fixed-point graph drain'
    Assert-GateB ($gateText.IndexOf('$edgePath = Resolve-VerifierBrowserPath',
        [StringComparison]::Ordinal) -ge 0 -and
        $gateText.IndexOf('$edgeProcess = Start-VerifierProcess $edgePath $arguments',
            [StringComparison]::Ordinal) -ge 0 -and
        $gateText.IndexOf('$descendants = @(Get-VerifierDescendantProcessRecords $session $snapshot)',
            [StringComparison]::Ordinal) -ge 0) `
        'real Edge canary is not using the shared resolver and complete ancestry cleanup path'
    Assert-GateB ($gateText.IndexOf('Test-VerifierDescendantOwnership $owner $helperChild',
        [StringComparison]::Ordinal) -ge 0 -and
        $gateText.IndexOf('different-executable helper was accepted from PPID alone',
            [StringComparison]::Ordinal) -ge 0) `
        'markerless/root-only and different-executable descendant negatives are not wired'
    $descendantExecutableStart = $moduleText.IndexOf(
        'function Test-VerifierDescendantExecutableIdentity',
        [StringComparison]::Ordinal)
    $descendantExecutableText = if ($descendantExecutableStart -ge 0) {
        $nextFunction = $moduleText.IndexOf("`nfunction ",
            $descendantExecutableStart + 1, [StringComparison]::Ordinal)
        if ($nextFunction -gt $descendantExecutableStart) {
            $moduleText.Substring($descendantExecutableStart,
                $nextFunction - $descendantExecutableStart)
        } else { $moduleText.Substring($descendantExecutableStart) }
    } else { '' }
    Assert-GateB ($descendantExecutableText.IndexOf('-RequireExecutablePath',
        [StringComparison]::Ordinal) -ge 0 -and
        $moduleText.IndexOf(
            'Test-VerifierDescendantExecutableIdentity $rootOwner',
            [StringComparison]::Ordinal) -ge 0) `
        'run-owned browser descendants do not require executable-path identity at discovery and revalidation'

    Write-Host 'PASS:Gate-B isolation/source contract'
}

function Invoke-GateBWorkflowChecks() {
    $workflowPath = Join-Path $repositoryRoot '.github\workflows\windows-gate-b.yml'
    $text = Get-Content -LiteralPath $workflowPath -Raw
    Assert-GateB ($text -match '(?m)^name:\s+Gate B Windows JDK8 verification\s*$') `
        'workflow name is missing or changed'
    Assert-GateB ($text -match '(?m)^on:\s*$' -and $text -match '(?m)^\s+push:\s*$' -and
        $text -match '(?m)^\s+pull_request:\s*$' -and $text -match '(?m)^\s+workflow_dispatch:\s*$') `
        'workflow triggers are incomplete'
    Assert-GateB ($text -match '(?m)^jobs:\s*$' -and $text -match '(?m)^\s{2}gate-b:\s*$' -and
        $text -match '(?m)^\s{4}name:\s+Gate B Windows JDK8 deterministic verification\s*$') `
        'workflow job/check identity is missing'
    Assert-GateB ($text -notmatch '(?i)verify-browser\.ps1\s+-') `
        'visible browser verifier was incorrectly placed in deterministic CI'
    Assert-GateB ($text -match '(?m)^\s+uses:\s+actions/checkout@v4\s*$' -and
        $text -match '(?m)^\s+run:\s+\.\\scripts\\build\.ps1\s+-JavaHome') `
        'workflow does not use the pinned checkout/build path'
    Assert-GateB ($text -match 'war\\circuitjs1\\circuitjs1\.nocache\.js' -and
        $text -match '\*\.cache\.js') `
        'workflow artifact assertions are missing'
    Assert-GateB ($text -notmatch "`t") 'workflow contains tab indentation'
    foreach ($step in @(
        'Check out repository',
        'Select JDK 8',
        'Verify selected java and javac are JDK 8',
        'Compile pinned GWT 2.7.0 mainline artifact',
        'Assert compiled GWT artifacts',
        'Verify renderer/provider boundary',
        'Run deterministic Gate B isolation and Task 43 contract checks'
    )) {
        Assert-GateB ($text.IndexOf('- name: ' + $step, [StringComparison]::Ordinal) -ge 0) `
            "workflow step '$step' is missing"
    }
    $setupIndex = $text.IndexOf('actions/setup-java@v4', [StringComparison]::Ordinal)
    $compileIndex = $text.IndexOf('scripts\build.ps1 -JavaHome', [StringComparison]::Ordinal)
    $checkIndex = $text.IndexOf('scripts\verify-gate-b.ps1 -JavaHome', [StringComparison]::Ordinal)
    Assert-GateB ($setupIndex -ge 0 -and $compileIndex -gt $setupIndex -and
        $checkIndex -gt $compileIndex) 'workflow validation steps are out of order'
    $yamlCommand = Get-Command ConvertFrom-Yaml -ErrorAction SilentlyContinue
    if ($null -ne $yamlCommand) {
        try {
            $parsed = $text | ConvertFrom-Yaml
            Assert-GateB ($null -ne $parsed.jobs.'gate-b') 'ConvertFrom-Yaml did not produce gate-b job'
        } catch {
            throw "workflow YAML parse failed: $($_.Exception.Message)"
        }
    } else {
        # PowerShell 5.1 has no built-in YAML parser; the ordered key/step
        # checks above are the deterministic fallback used on that supported
        # host and are intentionally stricter than a token-presence check.
        Write-Host 'INFO:ConvertFrom-Yaml unavailable; workflow static structure fallback used'
    }
    Write-Host 'PASS:workflow YAML structural contract'
}

function Invoke-GateBSetupFailureCheck() {
    $setupCanaryRoot = Join-Path ([IO.Path]::GetTempPath()) `
        ('TroubleshootJS\gate-b-setup-canary-' + [Guid]::NewGuid().ToString('N'))
    try {
        New-Item -ItemType Directory -Path $setupCanaryRoot -Force -ErrorAction Stop | Out-Null
        $blockedEvidenceParent = Join-Path $setupCanaryRoot 'evidence-parent-file.txt'
        [IO.File]::WriteAllText($blockedEvidenceParent, 'not a directory',
            [Text.UTF8Encoding]::new($false))
    } catch {
        Throw-GateBInfrastructure ('could not prepare setup-failure canary: ' +
            (Get-VerifierErrorMessage $_))
    }
    $setupMessage = ''
    try {
        [void](New-VerifierRunContext $repositoryRoot $blockedEvidenceParent)
        throw 'context setup unexpectedly succeeded with a file as EvidenceParent'
    } catch {
        $setupMessage = Get-VerifierErrorMessage $_
        Assert-GateB (Test-VerifierInfrastructureError $_) `
            'context setup failure was not classified as infrastructure'
    }
    $recordMatch = [regex]::Match($setupMessage, "Setup record retained at '([^']+)'\.")
    Assert-GateB $recordMatch.Success 'context setup did not retain its minimal failure record'
    $recordPath = $recordMatch.Groups[1].Value
    Assert-GateB (Test-Path -LiteralPath $recordPath -PathType Leaf) `
        'retained context setup failure record is missing'
    try {
        $record = Get-Content -LiteralPath $recordPath -Raw | ConvertFrom-Json
    } catch {
        Throw-GateBInfrastructure ('could not read retained setup-failure record: ' +
            (Get-VerifierErrorMessage $_))
    }
    Assert-GateB ($record.protocol -eq 'troubleshootjs-verifier-setup-failure-v1') `
        'retained setup failure record protocol is incorrect'
    $runRoot = Split-Path -Parent $recordPath
    $verifyRoot = Join-Path ([IO.Path]::GetTempPath()) 'TroubleshootJS\verify'
    Assert-GateB (Test-VerifierChildPath $verifyRoot $runRoot) `
        'setup canary record escaped the verifier temp root'
    try {
        Remove-VerifierOwnedTree $verifyRoot $runRoot
        Remove-VerifierOwnedTree (Get-VerifierFullPath ([IO.Path]::GetTempPath())) $setupCanaryRoot
    } catch {
        Throw-GateBInfrastructure ('could not clean setup-failure canary evidence: ' +
            (Get-VerifierErrorMessage $_))
    }
    Write-Host 'PASS:context setup infrastructure/failure-record contract'
}

function Invoke-GateBModuleImportSetupCheck() {
    $sourcePath = Join-Path $PSScriptRoot 'verify-browser.ps1'
    $sourceText = Get-Content -LiteralPath $sourcePath -Raw
    $canaryRoot = Join-Path ([IO.Path]::GetTempPath()) `
        ('TroubleshootJS\gate-b-module-import-' + [Guid]::NewGuid().ToString('N'))
    $primaryFailure = $null
    $cleanupErrors = New-Object Collections.ArrayList
    try {
        New-Item -ItemType Directory -Path $canaryRoot -Force -ErrorAction Stop | Out-Null
        $powershell = (Get-Command powershell.exe -ErrorAction Stop).Source
        $utf8WithBom = [Text.UTF8Encoding]::new($true)
        foreach ($case in @(
            [pscustomobject]@{ Name = 'missing-module'; Malformed = $false },
            [pscustomobject]@{ Name = 'malformed-module'; Malformed = $true }
        )) {
            $caseRoot = Join-Path $canaryRoot $case.Name
            New-Item -ItemType Directory -Path $caseRoot -Force -ErrorAction Stop | Out-Null
            $probePath = Join-Path $caseRoot 'verify-browser.ps1'
            [IO.File]::WriteAllText($probePath, $sourceText,
                $utf8WithBom)
            if ($case.Malformed) {
                [IO.File]::WriteAllText((Join-Path $caseRoot 'VerifierIsolation.psm1'),
                    'function Broken-Module {', $utf8WithBom)
            }
            $childResult = Invoke-GateBBoundedProcess $powershell @(
                '-NoProfile', '-ExecutionPolicy', 'Bypass', '-File', $probePath,
                '-GateBContractProbe') 30000 "$($case.Name) module-import probe"
            $childExit = Resolve-GateBInfrastructureChildExitCode $childResult.ExitCode `
                $childResult.TerminationProven
            $outputText = @($childResult.Stdout, $childResult.Stderr) -join "`n"
            [IO.File]::WriteAllText((Join-Path $caseRoot 'child-output.txt'), $outputText,
                [Text.UTF8Encoding]::new($false))
            if ($outputText -notmatch '(?i)FAIL verifier setup') {
                Throw-GateBInfrastructure ("$($case.Name) probe did not emit the guarded setup failure: " +
                    $outputText)
            }
            $recordMatch = [regex]::Match($outputText, "setup record retained at '([^']+)'")
            if (-not $recordMatch.Success) {
                Throw-GateBInfrastructure ("$($case.Name) probe did not retain a minimal setup record: " +
                    $outputText)
            }
            $recordPath = [IO.Path]::GetFullPath($recordMatch.Groups[1].Value)
            $verifyRoot = Join-Path ([IO.Path]::GetTempPath()) 'TroubleshootJS\verify'
            $recordRunRoot = Split-Path -Parent $recordPath
            if ([IO.Path]::GetFileName($recordPath) -ne 'setup-failure.json' -or
                    -not (Test-VerifierChildPath $verifyRoot $recordRunRoot) -or
                    -not (Test-Path -LiteralPath $recordPath -PathType Leaf)) {
                Throw-GateBInfrastructure ("$($case.Name) setup record was missing or outside the verifier temp root: " +
                    $recordPath)
            }
            try {
                $record = Get-Content -LiteralPath $recordPath -Raw | ConvertFrom-Json
            } catch {
                Throw-GateBInfrastructure ("$($case.Name) setup record was not valid JSON: " +
                    (Get-VerifierErrorMessage $_))
            }
            if ([string]$record.protocol -ne 'troubleshootjs-verifier-early-setup-failure-v1' -or
                    [string]$record.runRoot -ne $recordRunRoot) {
                Throw-GateBInfrastructure ("$($case.Name) setup record protocol/identity was invalid.")
            }
            Remove-VerifierOwnedTree $verifyRoot $recordRunRoot
            if (Test-Path -LiteralPath $recordRunRoot) {
                Throw-GateBInfrastructure ("$($case.Name) setup record root remained after exact cleanup.")
            }
        }
    } catch {
        $primaryFailure = $_
    }
    if ($null -eq $primaryFailure) {
        try {
            Remove-VerifierOwnedTree (Get-VerifierFullPath ([IO.Path]::GetTempPath())) $canaryRoot
        } catch {
            [void]$cleanupErrors.Add((Get-VerifierErrorMessage $_))
        }
    }
    if ($null -ne $primaryFailure) {
        if (Test-VerifierInfrastructureError $primaryFailure) { throw $primaryFailure }
        Throw-GateBInfrastructure ('module import/setup canary failed; evidence was retained at ' +
            $canaryRoot + ': ' + (Get-VerifierErrorMessage $primaryFailure))
    }
    if ($cleanupErrors.Count -gt 0) {
        Throw-GateBInfrastructure ('module import/setup canary cleanup failed; evidence was retained at ' +
            $canaryRoot + ': ' + ($cleanupErrors -join '; '))
    }
    Write-Host 'PASS:missing/malformed verifier-module import infrastructure probe'
}

function Invoke-GateBProcessOwnershipCanary() {
    $profile = Join-Path ([IO.Path]::GetTempPath()) 'TroubleshootJS\gate-b-relevance-profile'
    $runId = 'gate-b-relevance-run'
    $repositoryIdentity = 'gate-b-relevance-worktree'
    $browserPath = 'C:\Program Files\Browser With Spaces\msedge.exe'
    $previewScript = Join-Path $repositoryRoot 'scripts\preview.ps1'
    $irrelevant = @(
        [pscustomobject]@{ ProcessId = 0; ParentProcessId = 0; Name = 'System Idle Process'; CommandLine = $null }
        [pscustomobject]@{ ProcessId = 321; ParentProcessId = 1; Name = 'unrelated.exe'; CommandLine = $null }
    )
    $selected = @(Select-VerifierRelevantProcessRecords $irrelevant $browserPath $profile `
        $runId $repositoryIdentity 45123)
    Assert-GateB ($selected.Count -eq 0) `
        'irrelevant PID0/null-command WMI records were treated as browser ownership'

    $inaccessibleRelevant = [pscustomobject]@{
        ProcessId = 322; ParentProcessId = 1; Name = 'msedge.exe'; CommandLine = $null
    }
    $inaccessibleObserved = $false
    try {
        [void](Select-VerifierRelevantProcessRecords @($inaccessibleRelevant) $browserPath $profile `
            $runId $repositoryIdentity 45123)
    } catch {
        $inaccessibleObserved = Test-VerifierInfrastructureError $_
    }
    Assert-GateB $inaccessibleObserved `
        'inaccessible relevant browser command-line record was not infrastructure failure'

    $relevantCommand = 'msedge.exe --user-data-dir="' + $profile +
        '" --tsj-verifier-run=' + $runId + ' --tsj-verifier-worktree=' +
        $repositoryIdentity + ' --remote-debugging-port=45123'
    foreach ($variant in @(
            [pscustomobject]@{ Name = 'string ProcessId'; ProcessId = '325'; ParentProcessId = 1; NameValue = 'msedge.exe'; CommandLine = $relevantCommand }
            [pscustomobject]@{ Name = 'fractional ProcessId'; ProcessId = 325.5; ParentProcessId = 1; NameValue = 'msedge.exe'; CommandLine = $relevantCommand }
            [pscustomobject]@{ Name = 'Boolean ProcessId'; ProcessId = $true; ParentProcessId = 1; NameValue = 'msedge.exe'; CommandLine = $relevantCommand }
            [pscustomobject]@{ Name = 'array ProcessId'; ProcessId = [object[]]@(325, 326); ParentProcessId = 1; NameValue = 'msedge.exe'; CommandLine = $relevantCommand }
            [pscustomobject]@{ Name = 'object ProcessId'; ProcessId = [pscustomobject]@{ Value = 325 }; ParentProcessId = 1; NameValue = 'msedge.exe'; CommandLine = $relevantCommand }
            [pscustomobject]@{ Name = 'null ProcessId'; ProcessId = $null; ParentProcessId = 1; NameValue = 'msedge.exe'; CommandLine = $relevantCommand }
            [pscustomobject]@{ Name = 'string ParentProcessId'; ProcessId = 325; ParentProcessId = '1'; NameValue = 'msedge.exe'; CommandLine = $relevantCommand }
            [pscustomobject]@{ Name = 'fractional ParentProcessId'; ProcessId = 325; ParentProcessId = 1.5; NameValue = 'msedge.exe'; CommandLine = $relevantCommand }
            [pscustomobject]@{ Name = 'Boolean ParentProcessId'; ProcessId = 325; ParentProcessId = $true; NameValue = 'msedge.exe'; CommandLine = $relevantCommand }
            [pscustomobject]@{ Name = 'array ParentProcessId'; ProcessId = 325; ParentProcessId = [object[]]@(1, 2); NameValue = 'msedge.exe'; CommandLine = $relevantCommand }
            [pscustomobject]@{ Name = 'object ParentProcessId'; ProcessId = 325; ParentProcessId = [pscustomobject]@{ Value = 1 }; NameValue = 'msedge.exe'; CommandLine = $relevantCommand }
            [pscustomobject]@{ Name = 'null ParentProcessId'; ProcessId = 325; ParentProcessId = $null; NameValue = 'msedge.exe'; CommandLine = $relevantCommand }
            [pscustomobject]@{ Name = 'string Name'; ProcessId = 325; ParentProcessId = 1; NameValue = 325; CommandLine = $relevantCommand }
            [pscustomobject]@{ Name = 'array Name'; ProcessId = 325; ParentProcessId = 1; NameValue = [object[]]@('msedge.exe', 'helper.exe'); CommandLine = $relevantCommand }
            [pscustomobject]@{ Name = 'object Name'; ProcessId = 325; ParentProcessId = 1; NameValue = [pscustomobject]@{ Value = 'msedge.exe' }; CommandLine = $relevantCommand }
            [pscustomobject]@{ Name = 'null Name'; ProcessId = 325; ParentProcessId = 1; NameValue = $null; CommandLine = $relevantCommand }
            [pscustomobject]@{ Name = 'string CommandLine'; ProcessId = 325; ParentProcessId = 1; NameValue = 'msedge.exe'; CommandLine = 325 }
            [pscustomobject]@{ Name = 'array CommandLine'; ProcessId = 325; ParentProcessId = 1; NameValue = 'msedge.exe'; CommandLine = [object[]]@($relevantCommand, 'extra') }
            [pscustomobject]@{ Name = 'object CommandLine'; ProcessId = 325; ParentProcessId = 1; NameValue = 'msedge.exe'; CommandLine = [pscustomobject]@{ Value = $relevantCommand } }
            [pscustomobject]@{ Name = 'null CommandLine'; ProcessId = 325; ParentProcessId = 1; NameValue = 'msedge.exe'; CommandLine = $null }
        )) {
        $malformedObserved = $false
        try {
            $record = [pscustomobject]@{
                ProcessId = $variant.ProcessId; ParentProcessId = $variant.ParentProcessId
                Name = $variant.NameValue; CommandLine = $variant.CommandLine
            }
            [void](Select-VerifierRelevantProcessRecords @($record) $browserPath $profile `
                $runId $repositoryIdentity 45123)
        } catch {
            $malformedObserved = Test-VerifierInfrastructureError $_
        }
        Assert-GateB $malformedObserved `
            "raw process ownership $($variant.Name) was not rejected before selection"
    }

    $foreignProfile = [pscustomobject]@{
        ProcessId = 323; ParentProcessId = 1; Name = 'powershell.exe'
        CommandLine = 'powershell.exe --user-data-dir="' + $profile + '"'
    }
    $foreignSelected = @(Select-VerifierRelevantProcessRecords @($foreignProfile) '' $profile `
        $runId $repositoryIdentity 45123)
    Assert-GateB ($foreignSelected.Count -eq 1 -and
        [int]$foreignSelected[0].ProcessId -eq 323) `
        'exact foreign-profile marker was not included in the relevant snapshot'
    $equivalentProfile = ($profile.ToUpperInvariant().Replace('\', '/') + '/./')
    $equivalentForeignProfile = [pscustomobject]@{
        ProcessId = 324; ParentProcessId = 1; Name = 'helper.exe'
        CommandLine = 'helper.exe --user-data-dir="' + $equivalentProfile + '"'
    }
    Assert-GateB (Test-VerifierCommandLineSwitch $equivalentForeignProfile.CommandLine `
        '--user-data-dir' $profile) `
        'equivalent Windows profile path was not canonicalized for exact identity'
    $equivalentSelected = @(Select-VerifierRelevantProcessRecords @($equivalentForeignProfile) '' `
        $profile $runId $repositoryIdentity 45123)
    Assert-GateB ($equivalentSelected.Count -eq 1 -and
        [int]$equivalentSelected[0].ProcessId -eq 324) `
        'equivalent-path foreign profile helper was omitted from the relevant snapshot'

    $owner = [pscustomobject]@{
        ProcessId = 400; Profile = $profile; RunId = $runId
        RepositoryIdentity = $repositoryIdentity; CdpPort = 45123
        BrowserPath = $browserPath; Name = 'msedge.exe'
    }
    $exactChild = [pscustomobject]@{
        ProcessId = 401; ParentProcessId = 400; ProcessStartTicks = 1L
        Name = 'msedge.exe'; ExecutablePath = $browserPath
        CommandLine = 'msedge.exe --user-data-dir="' + $profile + '" --tsj-verifier-run=' +
            $runId + ' --tsj-verifier-worktree=' + $repositoryIdentity +
            ' --remote-debugging-port=45123'
    }
    $foreignChild = [pscustomobject]@{
        ProcessId = 402; ParentProcessId = 400; ProcessStartTicks = 1L
        Name = 'msedge.exe'; ExecutablePath = $browserPath
        CommandLine = 'msedge.exe --user-data-dir="C:\foreign-profile" --tsj-verifier-run=' +
            $runId + ' --tsj-verifier-worktree=' + $repositoryIdentity +
            ' --remote-debugging-port=45123'
    }
    $staleChild = [pscustomobject]@{
        ProcessId = 403; ParentProcessId = 400; ProcessStartTicks = 0L
        Name = 'msedge.exe'; ExecutablePath = $browserPath
        CommandLine = $exactChild.CommandLine
    }
    $helperChild = [pscustomobject]@{
        ProcessId = 404; ParentProcessId = 400; ProcessStartTicks = 1L
        # Real Edge helpers commonly omit the root-only verifier markers.
        # They must still expose the configured executable identity.
        Name = 'msedge.exe'; ExecutablePath = $browserPath
        CommandLine = 'msedge.exe --type=renderer --lang=en-US'
    }
    $staleHelperChild = [pscustomobject]@{
        ProcessId = 405; ParentProcessId = 400; ProcessStartTicks = 0L
        Name = 'msedge.exe'; ExecutablePath = $browserPath
        CommandLine = $helperChild.CommandLine
    }
    $differentNameHelper = [pscustomobject]@{
        ProcessId = 406; ParentProcessId = 400; ProcessStartTicks = 1L
        Name = 'browser-helper.exe'
        ExecutablePath = 'C:\Program Files\Other Browser\browser-helper.exe'
        CommandLine = $helperChild.CommandLine
    }
    $missingExecutableChild = [pscustomobject]@{
        ProcessId = 408; ParentProcessId = 400; ProcessStartTicks = 1L
        Name = 'msedge.exe'; ExecutablePath = ''
        # This child is markerless and ancestry-shaped, but WMI did not expose
        # a usable executable identity. It must be retained, never stopped.
        CommandLine = $helperChild.CommandLine
    }
    Assert-GateB (Test-VerifierDescendantOwnership $owner $exactChild 400) `
        'exact descendant identity was rejected'
    Assert-GateB (-not (Test-VerifierDescendantOwnership $owner $foreignChild 400)) `
        'foreign descendant with matching PPID was treated as owned'
    Assert-GateB (-not (Test-VerifierDescendantOwnership $owner $staleChild 400)) `
        'stale-PID descendant with matching PPID was treated as owned'
    $ownershipCandidates = @(Get-VerifierDescendantCandidateRecords $owner @(
        $exactChild, $foreignChild, $staleChild, $helperChild, $staleHelperChild,
        $differentNameHelper, $missingExecutableChild))
    Assert-GateB (@($ownershipCandidates | Where-Object { [int]$_.ProcessId -eq 404 }).Count -eq 1) `
        'markerless Edge helper descendant was omitted from the ownership traversal'
    Assert-GateB (@($ownershipCandidates | Where-Object { [int]$_.ProcessId -eq 405 }).Count -eq 1) `
        'stale helper descendant was omitted before its start identity could be rejected'
    Assert-GateB (@($ownershipCandidates | Where-Object { [int]$_.ProcessId -eq 408 }).Count -eq 1) `
        'missing-executable descendant was omitted before its identity could be rejected'
    Assert-GateB (Test-VerifierDescendantOwnership $owner $helperChild 400) `
        'markerless same-executable Edge helper was rejected after ancestry proof'
    Assert-GateB (-not (Test-VerifierDescendantOwnership $owner $missingExecutableChild 400)) `
        'markerless descendant with missing ExecutablePath was accepted for cleanup'
    Assert-GateB (-not (Test-VerifierDescendantOwnership $owner $differentNameHelper 400)) `
        'different-executable helper was accepted from PPID alone'
    Assert-GateB (-not (Test-VerifierDescendantOwnership $owner $staleHelperChild 400)) `
        'stale different-name helper identity was treated as owned'
    $rootIdentityProcess = Get-Process -Id $PID -ErrorAction Stop
    $rootIdentityStartTicks = [long](Get-VerifierProcessStartTicks $rootIdentityProcess)
    $wrongExecutableRoot = [pscustomobject]@{
        ProcessId = $PID; ProcessStartTicks = $rootIdentityStartTicks
        Profile = $profile; RunId = $runId; RepositoryIdentity = $repositoryIdentity
        CdpPort = 45123; BrowserPath = $browserPath
    }
    $wrongExecutableSnapshot = [pscustomobject]@{
        ProcessId = $PID; ParentProcessId = 1; Name = 'msedge.exe'
        ExecutablePath = 'C:\Program Files\Other Browser\other.exe'
        CommandLine = $exactChild.CommandLine
    }
    Assert-GateB (-not (Test-VerifierProcessIdentity $wrongExecutableRoot @($wrongExecutableSnapshot))) `
        'browser root with a mismatched executable path was accepted despite matching markers'
    $missingExecutableSnapshot = [pscustomobject]@{
        ProcessId = $PID; ParentProcessId = 1; Name = 'msedge.exe'
        CommandLine = $exactChild.CommandLine
    }
    Assert-GateB (-not (Test-VerifierProcessIdentity $wrongExecutableRoot @($missingExecutableSnapshot))) `
        'browser root with an inaccessible executable path was accepted'
    $missingConfiguredBrowserRoot = [pscustomobject]@{
        ProcessId = $PID; ProcessStartTicks = $rootIdentityStartTicks
        Profile = $profile; RunId = $runId; RepositoryIdentity = $repositoryIdentity
        CdpPort = 45123
    }
    Assert-GateB (-not (Test-VerifierProcessIdentity $missingConfiguredBrowserRoot `
        @($wrongExecutableSnapshot))) `
        'browser root with a missing configured BrowserPath was accepted'
    $recordedRootState = [pscustomobject]@{
        ProcessId = $PID; ParentProcessId = 1; ProcessStartTicks = $rootIdentityStartTicks
        CommandLine = $exactChild.CommandLine; Name = 'msedge.exe'; ExecutablePath = $browserPath
    }
    $currentRootState = [pscustomobject]@{
        ProcessId = $PID; ParentProcessId = 1; ProcessStartTicks = $rootIdentityStartTicks
        CommandLine = $exactChild.CommandLine; Name = 'msedge.exe'; ExecutablePath = $browserPath
    }
    Assert-GateB (Test-VerifierCurrentProcessRecordMatches $recordedRootState $currentRootState) `
        'current root executable identity did not match its recorded process state'
    Assert-GateB (-not (Test-VerifierCurrentProcessRecordMatches $recordedRootState `
        ([pscustomobject]@{
            ProcessId = $PID; ParentProcessId = 1; ProcessStartTicks = $rootIdentityStartTicks
            CommandLine = $exactChild.CommandLine; Name = 'msedge.exe'
            ExecutablePath = 'C:\Program Files\Other Browser\other.exe'
        }))) `
        'current root executable replacement was accepted at the termination boundary'
    $recordedProcess = [pscustomobject]@{
        ProcessId = 404; ParentProcessId = 400; ProcessStartTicks = 17L
        CommandLine = $exactChild.CommandLine; ExecutablePath = $browserPath
    }
    $currentProcess = [pscustomobject]@{
        ProcessId = 404; ParentProcessId = 400; ProcessStartTicks = 17L
        CommandLine = $exactChild.CommandLine; ExecutablePath = $browserPath
    }
    Assert-GateB (Test-VerifierCurrentProcessRecordMatches $recordedProcess $currentProcess) `
        'current exact process record did not match its discovery record'
    Assert-GateB (-not (Test-VerifierCurrentProcessRecordMatches $recordedProcess `
        ([pscustomobject]@{
            ProcessId = 404; ParentProcessId = 400; ProcessStartTicks = 17L
            CommandLine = $exactChild.CommandLine
        }))) `
        'current descendant with missing executable identity was accepted at the termination boundary'
    foreach ($replacement in @(
        [pscustomobject]@{ ProcessId=404; ParentProcessId=400; ProcessStartTicks=18L; CommandLine=$exactChild.CommandLine; ExecutablePath=$browserPath },
        [pscustomobject]@{ ProcessId=404; ParentProcessId=401; ProcessStartTicks=17L; CommandLine=$exactChild.CommandLine; ExecutablePath=$browserPath },
        [pscustomobject]@{ ProcessId=404; ParentProcessId=400; ProcessStartTicks=17L; CommandLine=$foreignChild.CommandLine; ExecutablePath=$browserPath },
        [pscustomobject]@{ ProcessId=404; ParentProcessId=400; ProcessStartTicks=17L; CommandLine=$exactChild.CommandLine; ExecutablePath='C:\Program Files\Other Browser\other.exe' }
    )) {
        Assert-GateB (-not (Test-VerifierCurrentProcessRecordMatches $recordedProcess $replacement)) `
            'PID-replacement command/parent/start mismatch was accepted for termination'
    }
    $lateProfileHelper = [pscustomobject]@{
        ProcessId = 407; ParentProcessId = 1; Name = 'late-profile-helper.exe'
        CommandLine = 'late-profile-helper.exe --user-data-dir="' + $equivalentProfile + '"'
    }
    $lateProfileObserved = $false
    try {
        Assert-VerifierProfileSnapshotQuiescent @($lateProfileHelper) $profile
    } catch {
        $lateProfileObserved = Test-VerifierInfrastructureError $_
    }
    Assert-GateB $lateProfileObserved `
        'equivalent-path late profile helper was not detected by final quiescence proof'

    # Prefix-like markers must not satisfy any identity path. These strings
    # deliberately contain the expected values behind an adversarial switch
    # name or `--evil=<value>` token; only exact parsed Windows argument tokens
    # may authorize browser/preview cleanup.
    $currentIdentityProcess = Get-Process -Id $PID -ErrorAction Stop
    $currentStartTicks = [long](Get-VerifierProcessStartTicks $currentIdentityProcess)
    $prefixBrowserOwner = [pscustomobject]@{
        ProcessId = $PID; ProcessStartTicks = $currentStartTicks; Profile = $profile
        RunId = $runId; RepositoryIdentity = $repositoryIdentity; CdpPort = 45123
    }
    $prefixBrowser = [pscustomobject]@{
        ProcessId = $PID
        CommandLine = 'msedge.exe --evil-user-data-dir="' + $profile +
            '" --evil-tsj-verifier-run="' + $runId +
            '" --evil-tsj-verifier-worktree="' + $repositoryIdentity +
            '" --evil-remote-debugging-port="45123"'
    }
    Assert-GateB (-not (Test-VerifierProcessIdentity $prefixBrowserOwner @($prefixBrowser))) `
        'prefix browser markers incorrectly passed exact process identity'
    $prefixPreviewOwner = [pscustomobject]@{
        ProcessId = $PID; ProcessStartTicks = $currentStartTicks
        Script = $previewScript; Port = 45123
        RunId = $runId; Nonce = 'gate-b-preview-nonce'
    }
    $prefixPreview = [pscustomobject]@{
        ProcessId = $PID; ProcessStartTicks = $currentStartTicks
        CommandLine = 'powershell.exe --evil=' + $previewScript +
            ' --evil-Port="45123" --evil-VerifierRunId="' + $runId +
            '" --evil-VerifierNonce="gate-b-preview-nonce"'
    }
    Assert-GateB (-not (Test-VerifierPreviewProcessIdentity $prefixPreviewOwner @($prefixPreview))) `
        'prefix preview markers incorrectly passed exact preview identity'
    $previewDirectory = Split-Path -Parent $previewScript
    $previewLeaf = Split-Path -Leaf $previewScript
    $equivalentPreviewScript = ($previewDirectory + '/./' + $previewLeaf).ToUpperInvariant()
    $canonicalPreview = [pscustomobject]@{
        ProcessId = $PID; ProcessStartTicks = $currentStartTicks
        CommandLine = 'powershell.exe -File "' + $equivalentPreviewScript +
            '" -Port "45123" -VerifierRunId "' + $runId +
            '" -VerifierNonce "gate-b-preview-nonce"'
    }
    Assert-GateB (Test-VerifierPreviewProcessIdentity $prefixPreviewOwner @($canonicalPreview)) `
        'equivalent Windows preview script path was not canonicalized for identity'
    $equivalentWorktree = ($repositoryRoot.ToUpperInvariant().Replace('\', '/') + '/./')
    $statePath = Join-Path $repositoryRoot '.tools\preview\state.json'
    $equivalentStatePath = ($statePath.ToUpperInvariant().Replace('\', '/') + '/./')
    Assert-GateB (Test-VerifierCanonicalWindowsPathValue $equivalentWorktree $repositoryRoot) `
        'equivalent worktree path was not canonicalized'
    Assert-GateB (Test-VerifierCanonicalWindowsPathValue $equivalentStatePath $statePath) `
        'equivalent preview-state path was not canonicalized'
    $prefixCleanupChild = [pscustomobject]@{
        ProcessId = 407; ParentProcessId = 400; ProcessStartTicks = 1L
        CommandLine = $prefixBrowser.CommandLine
    }
    Assert-GateB (-not (Test-VerifierDescendantOwnership $owner $prefixCleanupChild 400)) `
        'prefix cleanup markers incorrectly authorized descendant termination'

    $unknownServer = [pscustomobject]@{
        ProcessId = 404; ProcessStartTicks = 0L; ProcessIdentityKnown = $false
    }
    Assert-GateB (-not (Test-VerifierPreviewCleanupReadiness $unknownServer $false $true $true)) `
        'delayed-bind preview with unknown process identity was cleanup-ready'
    $knownServer = [pscustomobject]@{
        ProcessId = 405; ProcessStartTicks = 1L; ProcessIdentityKnown = $true
    }
    Assert-GateB (Test-VerifierPreviewCleanupReadiness $knownServer $true $true $true) `
        'verified preview process/listener cleanup proof was rejected'
    Write-Host 'PASS:relevant browser snapshot, canonical-path/late-helper quiescence, complete descendant/helper graph, missing-descendant-executable, stale-PPID/foreign descendant, prefix-marker, and delayed-preview ownership canary'
}

function Invoke-GateBProcessStartIdentityCanary() {
    $currentProcess = $null
    $childProcess = $null
    $exitedProcess = $null
    $childStartTicks = 0L
    $transientAttempts = 0
    $unavailableAttempts = 0
    $unavailableElapsed = 0.0
    $malformedAttempts = 0
    $nonPositiveAttempts = 0
    $multipleAttempts = 0
    $arbitraryExceptionAttempts = 0
    $cleanupErrors = New-Object Collections.ArrayList
    $failure = $null
    try {
        $currentProcess = Get-Process -Id $PID -ErrorAction Stop
        $currentTicks = [long](Get-VerifierProcessStartTicks $currentProcess)
        Assert-GateB ($currentTicks -gt 0) `
            'current retained process did not produce a positive start identity'

        # Exercise the accessor boundary with the same retained real Process:
        # PowerShell no-output and one-element-null unavailable reads are
        # followed by a deterministic DateTime reconstructed from the already
        # captured positive identity.
        # No fake process object or wall-clock value participates in this path.
        $transientState = [pscustomobject]@{ Count = 0 }
        $capturedStartTime = [DateTime]::new($currentTicks, [DateTimeKind]::Utc)
        $transientAccessor = ({
            param($retainedProcess)
            $transientState.Count++
            if ($transientState.Count -eq 1) { return }
            if ($transientState.Count -eq 2) {
                Write-Output (,$null)
                return
            }
            return $capturedStartTime
        }).GetNewClosure()
        $transientTicks = [long](Get-VerifierProcessStartTicks $currentProcess $transientAccessor)
        $transientAttempts = $transientState.Count
        Assert-GateB ($transientTicks -eq $currentTicks -and $transientAttempts -eq 3) `
            'retained real Process did not recover from bounded transient StartTime failures'

        $unavailableState = [pscustomobject]@{ Count = 0 }
        $unavailableAccessor = ({
            param($retainedProcess)
            $unavailableState.Count++
            return $null
        }).GetNewClosure()
        $unavailableStarted = [Diagnostics.Stopwatch]::GetTimestamp()
        $unavailableObserved = $false
        try {
            [void](Get-VerifierProcessStartTicks $currentProcess $unavailableAccessor)
        } catch {
            $unavailableObserved = Test-VerifierInfrastructureError $_
        }
        $unavailableElapsed = (([double]([Diagnostics.Stopwatch]::GetTimestamp() -
            $unavailableStarted)) * 1000.0) / [double][Diagnostics.Stopwatch]::Frequency
        $unavailableAttempts = $unavailableState.Count
        Assert-GateB $unavailableObserved `
            'permanently unavailable retained StartTime was not typed infrastructure failure'
        Assert-GateB ($unavailableElapsed -lt 2000) `
            'unavailable retained StartTime identity did not time out in a small bound'
        Assert-GateB ($unavailableAttempts -eq 5) `
            'permanently unavailable retained StartTime did not consume exactly the bounded attempt budget'

        $malformedState = [pscustomobject]@{ Count = 0 }
        $malformedAccessor = ({
            param($retainedProcess)
            $malformedState.Count++
            return 'not-a-DateTime'
        }).GetNewClosure()
        $malformedObserved = $false
        try {
            [void](Get-VerifierProcessStartTicks $currentProcess $malformedAccessor)
        } catch {
            $malformedObserved = Test-VerifierInfrastructureError $_
        }
        $malformedAttempts = $malformedState.Count
        Assert-GateB $malformedObserved `
            'malformed retained StartTime accessor output was not rejected as infrastructure'
        Assert-GateB ($malformedAttempts -eq 1) `
            'malformed retained StartTime accessor was retried'

        $nonPositiveState = [pscustomobject]@{ Count = 0 }
        $nonPositiveAccessor = ({
            param($retainedProcess)
            $nonPositiveState.Count++
            return [DateTime]::new(0, [DateTimeKind]::Utc)
        }).GetNewClosure()
        $nonPositiveObserved = $false
        try {
            [void](Get-VerifierProcessStartTicks $currentProcess $nonPositiveAccessor)
        } catch {
            $nonPositiveObserved = Test-VerifierInfrastructureError $_
        }
        $nonPositiveAttempts = $nonPositiveState.Count
        Assert-GateB $nonPositiveObserved `
            'non-positive retained StartTime accessor output was not rejected as infrastructure'
        Assert-GateB ($nonPositiveAttempts -eq 1) `
            'non-positive retained StartTime accessor was retried'

        $multipleState = [pscustomobject]@{ Count = 0 }
        $multipleAccessor = ({
            param($retainedProcess)
            $multipleState.Count++
            Write-Output $capturedStartTime
            Write-Output $capturedStartTime
        }).GetNewClosure()
        $multipleObserved = $false
        try {
            [void](Get-VerifierProcessStartTicks $currentProcess $multipleAccessor)
        } catch {
            $multipleObserved = Test-VerifierInfrastructureError $_
        }
        $multipleAttempts = $multipleState.Count
        Assert-GateB $multipleObserved `
            'multiple retained StartTime accessor outputs were not rejected as infrastructure'
        Assert-GateB ($multipleAttempts -eq 1) `
            'multiple retained StartTime accessor output was retried'

        $arbitraryExceptionState = [pscustomobject]@{ Count = 0 }
        $arbitraryExceptionAccessor = ({
            param($retainedProcess)
            $arbitraryExceptionState.Count++
            throw [Exception]::new('injected arbitrary StartTime accessor failure')
        }).GetNewClosure()
        $arbitraryExceptionObserved = $false
        try {
            [void](Get-VerifierProcessStartTicks $currentProcess $arbitraryExceptionAccessor)
        } catch {
            $arbitraryExceptionObserved = Test-VerifierInfrastructureError $_
        }
        $arbitraryExceptionAttempts = $arbitraryExceptionState.Count
        Assert-GateB $arbitraryExceptionObserved `
            'arbitrary retained StartTime accessor exception was not rejected as infrastructure'
        Assert-GateB ($arbitraryExceptionAttempts -eq 1) `
            'arbitrary retained StartTime accessor exception was retried'

        $nullObserved = $false
        try { [void](Get-VerifierProcessStartTicks $null) } catch {
            $nullObserved = Test-VerifierInfrastructureError $_
        }
        Assert-GateB $nullObserved `
            'null process identity input was not rejected as infrastructure'

        $wrongTypeObserved = $false
        try {
            [void](Get-VerifierProcessStartTicks ([pscustomobject]@{ Id = $PID }))
        } catch {
            $wrongTypeObserved = Test-VerifierInfrastructureError $_
        }
        Assert-GateB $wrongTypeObserved `
            'non-System.Diagnostics.Process identity input was not rejected as infrastructure'

        $disposedProcess = New-Object System.Diagnostics.Process
        $disposedProcess.Dispose()
        $disposedObserved = $false
        try {
            [void](Get-VerifierProcessStartTicks $disposedProcess)
        } catch {
            $disposedObserved = Test-VerifierInfrastructureError $_
        }
        Assert-GateB $disposedObserved `
            'disposed System.Diagnostics.Process identity input was not rejected as infrastructure'

        $powershell = (Get-Command powershell.exe -ErrorAction Stop).Source
        $childProcess = Start-VerifierProcess $powershell @(
            '-NoProfile', '-ExecutionPolicy', 'Bypass', '-Command',
            'Start-Sleep -Milliseconds 900')
        $childStartTicks = [long](Get-VerifierProcessStartTicks $childProcess)
        Assert-GateB ($childStartTicks -gt 0) `
            'newly spawned child did not produce a positive start identity'
        Assert-GateB $childProcess.WaitForExit(5000) `
            'newly spawned child did not reach natural termination within the cleanup bound'
        $childProcess.Refresh()
        Assert-GateB ([bool]$childProcess.HasExited) `
            'newly spawned child natural termination was not proven'

        $exitedProcess = Start-VerifierProcess $powershell @(
            '-NoProfile', '-ExecutionPolicy', 'Bypass', '-Command', 'exit 0')
        Assert-GateB $exitedProcess.WaitForExit(5000) `
            'natural-exit identity canary child did not terminate within the bound'
        $exitedProcess.Refresh()
        Assert-GateB ([bool]$exitedProcess.HasExited) `
            'natural-exit identity canary child exit was not proven'
        $naturalExitObserved = $false
        try {
            [void](Get-VerifierProcessStartTicks $exitedProcess)
        } catch {
            $naturalExitObserved = Test-VerifierInfrastructureError $_
        }
        Assert-GateB $naturalExitObserved `
            'process that exited before identity acquisition was not typed infrastructure failure'
    } catch {
        $failure = $_
    } finally {
        foreach ($record in @(
            [pscustomobject]@{ Process = $childProcess; StartTicks = $childStartTicks; Name = 'spawned-child' },
            [pscustomobject]@{ Process = $exitedProcess; StartTicks = 0L; Name = 'natural-exit-child' }
        )) {
            if ($null -eq $record.Process) { continue }
            try {
                $record.Process.Refresh()
                if (-not [bool]$record.Process.HasExited) {
                    if ([long]$record.StartTicks -le 0) {
                        Throw-GateBInfrastructure ("$($record.Name) remained alive without a positive retained identity")
                    }
                    [void](Stop-VerifierVerifiedProcessExactly $record.Process `
                        $record.StartTicks 5000)
                } else {
                    [void]$record.Process.WaitForExit(5000)
                }
                $record.Process.Dispose()
            } catch {
                [void]$cleanupErrors.Add((Get-VerifierErrorMessage $_))
            }
        }
    }
    if ($cleanupErrors.Count -gt 0) {
        Throw-GateBInfrastructure ('process start-identity canary cleanup was not proven: ' +
            ($cleanupErrors -join '; '))
    }
    if ($null -ne $failure) { throw $failure }
    Write-Host ('PASS:retained real-process start identity, null-only retry classification, ' +
        'malformed/non-positive/multiple/arbitrary immediate rejection, natural-exit failure, ' +
        ('and bounded-unavailable timeout canaries ' +
         '(transientAttempts={0}; unavailableAttempts={1}; unavailableElapsedMs={2:N1}; ' +
         'malformedAttempts={3}; nonPositiveAttempts={4}; multipleAttempts={5}; arbitraryExceptionAttempts={6})') -f `
            $transientAttempts, $unavailableAttempts, $unavailableElapsed,
            $malformedAttempts, $nonPositiveAttempts, $multipleAttempts,
            $arbitraryExceptionAttempts)
}

function Invoke-GateBKernelTransportCanary() {
    $module = @(Get-Module VerifierIsolation | Select-Object -First 1)
    if ($module.Count -ne 1) { throw 'VerifierIsolation module was unavailable for kernel transport canary.' }
    $repositoryRootForCanary = Get-VerifierFullPath (Join-Path $PSScriptRoot '..')
    $previewScript = Get-VerifierFullPath (Join-Path $repositoryRootForCanary 'scripts\preview.ps1')
    $webRoot = Get-VerifierFullPath (Join-Path $repositoryRootForCanary 'war')
    $port = 40191
    $runId = 'gate-b-kernel-transport-run'
    $nonce = 'gate-b-kernel-transport-nonce'
    $claimName = Get-VerifierPortMutexName $null $port
    $claimMutex = [Threading.Mutex]::new($false, $claimName)
    $claimMutexHeld = $false
    try {
        $claimMutexHeld = $claimMutex.WaitOne(0)
        if (-not $claimMutexHeld) {
            Throw-GateBInfrastructure 'kernel transport canary mutex was not acquired.'
        }
    } catch {
        try { $claimMutex.Dispose() } catch { }
        throw
    }
    try {
        $currentProcess = Get-Process -Id $PID -ErrorAction Stop
    $currentStart = [long](Get-VerifierProcessStartTicks $currentProcess)
    $commandLine = 'powershell.exe -NoProfile -ExecutionPolicy Bypass -File "' +
        $previewScript + '" -Port ' + [string]$port +
        ' -VerifierRunId ' + $runId + ' -VerifierNonce ' + $nonce
    $lease = [pscustomobject]@{
        Kind = 'preview'; Port = $port; Status = 'bound'; ClaimState = 'bound'
        RunId = $runId; RepositoryIdentity = 'gate-b-kernel-transport-repository'
        WorktreeRoot = $repositoryRootForCanary
        LeaseId = 'gate-b-kernel-transport-lease'
        ClaimName = $claimName
        Path = (Join-Path (Join-Path $repositoryRootForCanary `
            'gate-b-kernel-port-leases') `
            '40191-gate-b-kernel-transport-lease.lease')
        ProfilePath = ''; BrowserPath = ''; BindValidatedUtc = ''; ReleasedUtc = ''
        ReleaseState = 'active'; ReleaseJournalState = 'active'
        ReleaseBlocked = $false; ReleaseBlockReason = ''; MutexReleased = $false
        Registered = $true
        ClaimOwnerPid = $PID; ClaimOwnerStartTicks = $currentStart
        BoundProcessId = $PID; BoundProcessStartTicks = $currentStart
        ListenerProcessId = 4; ListenerProcessStartTicks = $null
        ListenerOwnerKind = 'kernel-transport'
        ListenerOwnerProof = 'run-owned-preview-http-sys-v1'
        ListenerOwnerEvidence = 'pid-4-system-http-sys'
        ListenerInspectionSuccess = $true; ListenerInspectionKnown = $true
        ListenerHasListeners = $true; ListenerAbsent = $false
        ListenerInspectionUtc = ''
        ProcessProofRequired = $true
        ProcessTerminationProven = $false; ProcessAbsent = $false
        ClaimMutex = $claimMutex
    }
    & $module[0] {
        param($heldClaimName, $heldRunId, $heldMutex)
        $script:VerifierHeldPortClaims[$heldClaimName] = $heldRunId
        $script:VerifierHeldPortMutexes[$heldClaimName] = $heldMutex
    } $claimName $runId $claimMutex
    $context = [pscustomobject]@{
        Server = $null; WorktreeRoot = $repositoryRootForCanary
        PortLeaseRoot = (Join-Path $repositoryRootForCanary 'gate-b-kernel-port-leases')
        RepositoryIdentity = 'gate-b-kernel-transport-repository'
        RunId = $runId; PreviewNonce = $nonce
    }
    $owner = [pscustomobject]@{
        Owner = 'run'; BaseUrl = 'http://127.0.0.1:' + [string]$port
        Port = $port; ProcessId = $PID; ProcessStartTicks = $currentStart
        ProcessParentProcessId = 1; ProcessParentProcessStartTicks = 1L
        ProcessCommandLine = $commandLine; Script = $previewScript
        RepositoryRoot = $repositoryRootForCanary; WebRoot = $webRoot
        IdentityProtocol = 'troubleshootjs-preview-identity-v1'
        IdentityVerified = $true; CallerOwned = $false
        RunId = $runId; Nonce = $nonce; State = 'run-owned-verified'
        Lease = $lease; Process = $currentProcess
        StdoutLog = (Join-Path $repositoryRootForCanary 'gate-b-kernel-stdout.log')
        StderrLog = (Join-Path $repositoryRootForCanary 'gate-b-kernel-stderr.log')
        CleanupResult = 'pending'; Error = ''
        ProcessIdentityKnown = $true; OwnershipUncertain = $false
        ProcessTerminationProven = $false; ProcessAbsent = $false
        ListenerInspectionProven = $true; ListenerAbsent = $false
    }
    $context.Server = $owner
    $kernelListener = [pscustomobject]@{
        LocalAddress = '127.0.0.1'; Port = $port; ProcessId = 4
        ProcessStartTicks = $null; ListenerOwnerKind = 'kernel-transport'
        ListenerOwnerProof = 'run-owned-preview-http-sys-v1'
        ListenerOwnerEvidence = 'pid-4-system-http-sys'
        Source = 'Get-NetTCPConnection'
    }
    $constructedKernel = & $module[0] {
        param($address, $listenerPort, $source, $previewContext, $previewOwner)
        New-VerifierKernelTransportListenerRecord $address $listenerPort $source `
            $previewContext $previewOwner
    } '127.0.0.1' $port 'Get-NetTCPConnection' $context $owner
    Assert-GateB ($null -ne $constructedKernel -and
        $constructedKernel.ProcessId -eq 4 -and
        $null -eq $constructedKernel.ProcessStartTicks -and
        $constructedKernel.ListenerOwnerKind -ceq 'kernel-transport' -and
        $constructedKernel.ListenerOwnerProof -ceq 'run-owned-preview-http-sys-v1' -and
        $constructedKernel.ListenerOwnerEvidence -ceq 'pid-4-system-http-sys') `
        'valid kernel transport listener constructor record was rejected or altered'
    Assert-GateB (Test-VerifierKernelTransportListenerRecord $kernelListener `
            $context $owner) `
        'valid PID 4 kernel transport record was rejected'
    Assert-GateB (Test-VerifierRunOwnedPreviewHttpSysAuthorization $context $owner $port) `
        'exact run-owned preview identity handshake proof was rejected'
    Assert-GateB (Test-VerifierRunOwnedPreviewHttpSysListener $context $owner $kernelListener) `
        'PID 4 kernel transport listener was not accepted after exact preview proof'

    $callerOwner = $owner | Select-Object *
    $callerOwner.Owner = 'caller'; $callerOwner.State = 'caller-verified'
    $callerOwner.IdentityVerified = $true; $callerOwner.CallerOwned = $true
    $callerOwner.ProcessIdentityKnown = $false
    $context.Server = $callerOwner
    Assert-GateB (-not (Test-VerifierRunOwnedPreviewHttpSysAuthorization $context $callerOwner $port)) `
        'caller-owned preview was authorized as a kernel transport owner'

    $browserOwner = $owner | Select-Object *
    $browserOwner.Owner = 'caller'; $browserOwner.State = 'caller-verified'
    $browserOwner.IdentityVerified = $true; $browserOwner.CallerOwned = $true
    $browserOwner.ProcessIdentityKnown = $false
    $context.Server = $browserOwner
    Assert-GateB (-not (Test-VerifierRunOwnedPreviewHttpSysListener $context $browserOwner $kernelListener)) `
        'browser/caller listener path was authorized as a kernel transport owner'

    $unverifiedOwner = $owner | Select-Object *
    $unverifiedOwner.State = 'starting'; $unverifiedOwner.IdentityVerified = $false
    $context.Server = $unverifiedOwner
    Assert-GateB (-not (Test-VerifierRunOwnedPreviewHttpSysAuthorization $context $unverifiedOwner $port)) `
        'unverified run-owned preview was authorized as a kernel transport owner'

    $missingProofOwner = $owner | Select-Object *
    $missingProofOwner.ProcessIdentityKnown = $false
    $context.Server = $missingProofOwner
    Assert-GateB (-not (Test-VerifierRunOwnedPreviewHttpSysAuthorization $context $missingProofOwner $port)) `
        'preview with missing retained process identity proof was authorized'

    $malformedClaimMutexOwner = $owner | Select-Object *
    $malformedClaimMutexOwner.Lease = $owner.Lease | Select-Object *
    $malformedClaimMutexOwner.Lease.ClaimMutex = 'not-a-threading-mutex'
    $context.Server = $malformedClaimMutexOwner
    $malformedAuthorizationResult = & $module[0] {
        param($authorizationContext, $authorizationOwner, $authorizationPort)
        $oldProcessStartLookup = (Get-Command Get-VerifierProcessStartTicks `
            -CommandType Function -ErrorAction Stop).ScriptBlock
        try {
            $script:GateBKernelAuthorizationQueryObserved = $false
            Set-Item Function:\Get-VerifierProcessStartTicks -Force -Value {
                $script:GateBKernelAuthorizationQueryObserved = $true
                return 0L
            }
            return [pscustomobject]@{
                Accepted = [bool](Test-VerifierRunOwnedPreviewHttpSysAuthorization `
                    $authorizationContext $authorizationOwner $authorizationPort)
                QueryObserved = [bool]$script:GateBKernelAuthorizationQueryObserved
            }
        } finally {
            Set-Item Function:\Get-VerifierProcessStartTicks -Force `
                -Value $oldProcessStartLookup
            Remove-Variable -Name GateBKernelAuthorizationQueryObserved -Scope Script `
                -Force -ErrorAction SilentlyContinue
        }
    } $context $malformedClaimMutexOwner $port
    Assert-GateB (-not [bool]$malformedAuthorizationResult.Accepted -and
        -not [bool]$malformedAuthorizationResult.QueryObserved) `
        'HTTP.sys authorization accepted malformed ClaimMutex or queried before rejection'

    $wrongProofListener = $kernelListener | Select-Object *
    $wrongProofListener.ListenerOwnerProof = 'wrong-proof'
    $context.Server = $owner
    Assert-GateB (-not (Test-VerifierKernelTransportListenerRecord $wrongProofListener `
            $context $owner)) `
        'kernel listener with a wrong durable proof was accepted'
    $wrongEvidenceListener = $kernelListener | Select-Object *
    $wrongEvidenceListener.ListenerOwnerEvidence = 'wrong-evidence'
    Assert-GateB (-not (Test-VerifierKernelTransportListenerRecord $wrongEvidenceListener `
            $context $owner)) `
        'kernel listener with wrong durable evidence was accepted'
    $wrongPidListener = $kernelListener | Select-Object *
    $wrongPidListener.ProcessId = 5
    Assert-GateB (-not (Test-VerifierKernelTransportListenerRecord $wrongPidListener `
            $context $owner)) `
        'kernel listener with a non-System PID was accepted'
    $numericKernelListener = $kernelListener | Select-Object *
    $numericKernelListener.ProcessStartTicks = 1L
    Assert-GateB (-not (Test-VerifierKernelTransportListenerRecord $numericKernelListener `
            $context $owner)) `
        'kernel listener with a fabricated numeric start identity was accepted'
    $userListener = $kernelListener | Select-Object *
    $userListener.ProcessId = $PID; $userListener.ProcessStartTicks = $currentStart
    $userListener.ListenerOwnerKind = 'user-process'
    $userListener.ListenerOwnerProof = 'diagnostics-process-start-v1'
    $userListener.ListenerOwnerEvidence = 'system-diagnostics-process-starttime'
    Assert-GateB (-not (Test-VerifierKernelTransportListenerRecord $userListener)) `
        'user-mode listener was misclassified as the kernel transport owner'

    foreach ($ownerVariant in @(
        [pscustomobject]@{ Name = 'wrong owner kind'; Field = 'Owner'; Value = 'caller' },
        [pscustomobject]@{ Name = 'wrong identity boolean'; Field = 'IdentityVerified'; Value = 'true' },
        [pscustomobject]@{ Name = 'wrong lease run'; Field = 'RunId'; Value = 'foreign-run' },
        [pscustomobject]@{ Name = 'wrong lease repository'; Field = 'RepositoryIdentity'; Value = 'foreign-repository' },
        [pscustomobject]@{ Name = 'wrong lease worktree'; Field = 'WorktreeRoot'; Value = $env:SystemRoot }
    )) {
        $ownerVariantRecord = $owner | Select-Object *
        if ($ownerVariant.Field -eq 'RunId' -or $ownerVariant.Field -eq 'RepositoryIdentity' -or
                $ownerVariant.Field -eq 'WorktreeRoot') {
            $ownerVariantRecord.Lease = $ownerVariantRecord.Lease | Select-Object *
            $ownerVariantRecord.Lease.($ownerVariant.Field) = $ownerVariant.Value
        } else {
            $ownerVariantRecord.($ownerVariant.Field) = $ownerVariant.Value
        }
        $context.Server = $ownerVariantRecord
        Assert-GateB (-not (Test-VerifierRunOwnedPreviewHttpSysAuthorization `
                $context $ownerVariantRecord $port)) `
            "kernel transport accepted $($ownerVariant.Name)"
    }

    $context.Server = $owner
    $pid4Process = Get-Process -Id 4 -ErrorAction SilentlyContinue
    if ($null -ne $pid4Process) {
        $pid4Rejected = $false
        try {
            [void](Stop-VerifierVerifiedProcessExactly $pid4Process 1L 1)
        } catch {
            $pid4Rejected = Test-VerifierInfrastructureError $_
        }
        Assert-GateB $pid4Rejected 'PID 4 termination guard did not fail closed'
    }
        Write-Host 'PASS:PID 4 HTTP.sys transport owner requires exact run-owned preview proof; caller/browser/unverified/malformed records and PID 4 termination remain rejected'
    } finally {
        & $module[0] {
            param($heldClaimName, $heldRunId, $heldMutex)
            if ($script:VerifierHeldPortClaims.ContainsKey($heldClaimName) -and
                    [string]$script:VerifierHeldPortClaims[$heldClaimName] -eq $heldRunId) {
                [void]$script:VerifierHeldPortClaims.Remove($heldClaimName)
            }
            if ($script:VerifierHeldPortMutexes.ContainsKey($heldClaimName) -and
                    [object]::ReferenceEquals($script:VerifierHeldPortMutexes[$heldClaimName], $heldMutex)) {
                [void]$script:VerifierHeldPortMutexes.Remove($heldClaimName)
            }
        } $claimName $runId $claimMutex
        if ($claimMutexHeld) {
            try { [void]$claimMutex.ReleaseMutex() } catch { }
        }
        try { $claimMutex.Dispose() } catch { }
    }
}

function Invoke-GateBListenerRecordScalarCanary() {
    $module = @(Get-Module VerifierIsolation | Select-Object -First 1)
    if ($module.Count -ne 1) { throw 'VerifierIsolation module was unavailable for listener scalar canary.' }
    $port = 40202
    $address = '127.0.0.1'
    $source = 'Get-NetTCPConnection'
    $malformedValues = @(
        [pscustomobject]@{ Name = 'numeric-string'; Value = [string]$PID }
        [pscustomobject]@{ Name = 'floating'; Value = [double]$PID }
        [pscustomobject]@{ Name = 'Boolean'; Value = $true }
        [pscustomobject]@{ Name = 'array'; Value = @($PID) }
        [pscustomobject]@{ Name = 'object'; Value = [pscustomobject]@{ Value = $PID } }
        [pscustomobject]@{ Name = 'null'; Value = $null }
    )
    $invalidPortValues = @(
        [pscustomobject]@{ Name = 'numeric-string'; Value = [string]$port }
        [pscustomobject]@{ Name = 'floating'; Value = [double]$port }
        [pscustomobject]@{ Name = 'Boolean'; Value = $true }
        [pscustomobject]@{ Name = 'array'; Value = @($port) }
        [pscustomobject]@{ Name = 'object'; Value = [pscustomobject]@{ Value = $port } }
        [pscustomobject]@{ Name = 'null'; Value = $null }
        [pscustomobject]@{ Name = 'zero'; Value = 0 }
        [pscustomobject]@{ Name = 'negative'; Value = -1 }
        [pscustomobject]@{ Name = 'above maximum'; Value = 65536 }
    )
    $validNetstatOutput = @(
        'Active Connections'
        '  Proto  Local Address          Foreign Address        State           PID'
        ('  TCP    127.0.0.1:{0}        0.0.0.0:0              LISTENING       {1}' -f
            $port, $PID)
    )
    foreach ($variant in $invalidPortValues) {
        $parseRejected = & $module[0] {
            param($candidatePort, $output)
            try {
                [void](Parse-VerifierNetstatListenerOutput $candidatePort $output 0)
                return $false
            } catch {
                return (Test-VerifierInfrastructureError $_)
            }
        } $variant.Value $validNetstatOutput
        Assert-GateB ([bool]$parseRejected) `
            "netstat parser accepted raw port $($variant.Name)"

        $readerRejected = & $module[0] {
            param($candidatePort)
            try {
                [void](Get-VerifierLoopbackListenerRecords $candidatePort)
                return $false
            } catch {
                return (Test-VerifierInfrastructureError $_)
            }
        } $variant.Value
        Assert-GateB ([bool]$readerRejected) `
            "loopback listener reader accepted raw port $($variant.Name)"
    }
    $validParsed = & $module[0] {
        param($candidatePort, $output)
        Parse-VerifierNetstatListenerOutput $candidatePort $output 0
    } $port $validNetstatOutput
    Assert-GateB ($null -ne $validParsed -and [bool]$validParsed.HasListeners -and
        @($validParsed.Listeners).Count -eq 1 -and
        $validParsed.Listeners[0].ProcessId -eq $PID -and
        $validParsed.Listeners[0].ListenerOwnerKind -ceq 'user-process') `
        'valid numeric synthetic netstat port was not preserved through the parser'

    # Exercise the live Get-NetTCPConnection boundary with an untyped query
    # object.  The production reader must validate each raw object field before
    # any cast or owning-process lookup; otherwise a fractional/string port can
    # be truncated into a false listener identity.
    $rawQueryRecord = [pscustomobject]@{
        LocalAddress = $address; LocalPort = $port; OwningProcess = $PID
        State = 'Listen'
    }
    $invokeRawQuery = {
        param($record, [int]$candidatePort)
        & $module[0] {
            param($targetRecord, $port)
            $oldFunction = Get-Command Get-NetTCPConnection -CommandType Function `
                -ErrorAction SilentlyContinue
            $oldScriptBlock = if ($null -ne $oldFunction) {
                $oldFunction.ScriptBlock
            } else { $null }
            try {
                $script:GateBInjectedNetConnection = $targetRecord
                Set-Item Function:\Get-NetTCPConnection -Force -Value {
                    [CmdletBinding()]
                    param($LocalPort, $State)
                    return $script:GateBInjectedNetConnection
                }
                return (Get-VerifierLoopbackListenerRecords $port)
            } finally {
                if ($null -ne $oldFunction) {
                    Set-Item Function:\Get-NetTCPConnection -Force -Value $oldScriptBlock
                } else {
                    Remove-Item Function:\Get-NetTCPConnection -Force `
                        -ErrorAction SilentlyContinue
                }
                Remove-Variable -Name GateBInjectedNetConnection -Scope Script `
                    -Force -ErrorAction SilentlyContinue
            }
        } $record $candidatePort
    }
    $rawQueryInspection = & $invokeRawQuery $rawQueryRecord $port
    Assert-GateB ($null -ne $rawQueryInspection -and
        [bool]$rawQueryInspection.HasListeners -and
        @($rawQueryInspection.Listeners).Count -eq 1 -and
        $rawQueryInspection.Listeners[0].ProcessId -eq $PID) `
        'valid Get-NetTCPConnection raw scalar fields were rejected or altered'
    foreach ($rawQueryVariantDefinition in @(
            [pscustomobject]@{ Field = 'LocalPort'; Name = 'numeric-string'; Value = [string]$port }
            [pscustomobject]@{ Field = 'LocalPort'; Name = 'fractional'; Value = [double]($port + 0.5) }
            [pscustomobject]@{ Field = 'LocalPort'; Name = 'Boolean'; Value = $true }
            [pscustomobject]@{ Field = 'LocalPort'; Name = 'array'; Value = @($port) }
            [pscustomobject]@{ Field = 'LocalPort'; Name = 'object'; Value = [pscustomobject]@{ Value = $port } }
            [pscustomobject]@{ Field = 'LocalPort'; Name = 'null'; Value = $null }
            [pscustomobject]@{ Field = 'OwningProcess'; Name = 'numeric-string'; Value = [string]$PID }
            [pscustomobject]@{ Field = 'OwningProcess'; Name = 'fractional'; Value = [double]($PID + 0.5) }
            [pscustomobject]@{ Field = 'OwningProcess'; Name = 'Boolean'; Value = $true }
            [pscustomobject]@{ Field = 'OwningProcess'; Name = 'array'; Value = @($PID) }
            [pscustomobject]@{ Field = 'OwningProcess'; Name = 'object'; Value = [pscustomobject]@{ Value = $PID } }
            [pscustomobject]@{ Field = 'OwningProcess'; Name = 'null'; Value = $null }
            [pscustomobject]@{ Field = 'State'; Name = 'malformed-string'; Value = 'not-listening' }
            [pscustomobject]@{ Field = 'State'; Name = 'floating'; Value = [double]1 }
            [pscustomobject]@{ Field = 'State'; Name = 'Boolean'; Value = $true }
            [pscustomobject]@{ Field = 'State'; Name = 'array'; Value = @('Listen') }
            [pscustomobject]@{ Field = 'State'; Name = 'object'; Value = [pscustomobject]@{ Value = 'Listen' } }
            [pscustomobject]@{ Field = 'State'; Name = 'null'; Value = $null }
            [pscustomobject]@{ Field = 'LocalAddress'; Name = 'malformed-string'; Value = 'not-an-ip-address' }
            [pscustomobject]@{ Field = 'LocalAddress'; Name = 'floating'; Value = [double]127 }
            [pscustomobject]@{ Field = 'LocalAddress'; Name = 'Boolean'; Value = $true }
            [pscustomobject]@{ Field = 'LocalAddress'; Name = 'array'; Value = @($address) }
            [pscustomobject]@{ Field = 'LocalAddress'; Name = 'object'; Value = [pscustomobject]@{ Value = $address } }
            [pscustomobject]@{ Field = 'LocalAddress'; Name = 'null'; Value = $null }
        )) {
        $rawQueryVariant = $rawQueryRecord | Select-Object *
        $rawQueryVariant.($rawQueryVariantDefinition.Field) = $rawQueryVariantDefinition.Value
        $rawQueryRejected = $false
        try {
            [void](& $invokeRawQuery $rawQueryVariant $port)
        } catch {
            $rawQueryRejected = Test-VerifierInfrastructureError $_
        }
        Assert-GateB $rawQueryRejected `
            "Get-NetTCPConnection reader accepted raw $($rawQueryVariantDefinition.Field) $($rawQueryVariantDefinition.Name)"
    }
    # The CIM wrapper has its own raw-port boundary. Mock the OS query so the
    # canary proves malformed values are rejected before Get-CimInstance is
    # reached, while an exact integer still reaches the query path.
    $queryPortValues = New-Object Collections.ArrayList
    [void]$queryPortValues.Add($null)
    [void]$queryPortValues.Add([string]$port)
    [void]$queryPortValues.Add([double]($port + 0.5))
    [void]$queryPortValues.Add($true)
    [void]$queryPortValues.Add(@($port))
    [void]$queryPortValues.Add([pscustomobject]@{ Value = $port })
    foreach ($snapshotKind in @('process', 'ownership')) {
        foreach ($candidatePort in @($queryPortValues)) {
            $queryProbe = & $module[0] {
                param($kind, $rawPort)
                $oldFunction = Get-Command Get-CimInstance -CommandType Function `
                    -ErrorAction SilentlyContinue
                $oldScriptBlock = if ($null -ne $oldFunction) {
                    $oldFunction.ScriptBlock
                } else { $null }
                try {
                    $script:GateBSnapshotQueryReached = $false
                    Set-Item Function:\Get-CimInstance -Force -Value {
                        $script:GateBSnapshotQueryReached = $true
                        return @()
                    }
                    try {
                        if ($kind -eq 'process') {
                            [void](Get-VerifierBrowserProcessSnapshot '' '' '' '' $rawPort)
                        } else {
                            [void](Get-VerifierBrowserOwnershipSnapshot '' '' '' '' $rawPort)
                        }
                        return [pscustomobject]@{ Rejected = $false; Queried = $script:GateBSnapshotQueryReached }
                    } catch {
                        return [pscustomobject]@{
                            Rejected = (Test-VerifierInfrastructureError $_)
                            Queried = $script:GateBSnapshotQueryReached
                        }
                    }
                } finally {
                    if ($null -ne $oldFunction) {
                        Set-Item Function:\Get-CimInstance -Force -Value $oldScriptBlock
                    } else {
                        Remove-Item Function:\Get-CimInstance -Force -ErrorAction SilentlyContinue
                    }
                    Remove-Variable -Name GateBSnapshotQueryReached -Scope Script `
                        -Force -ErrorAction SilentlyContinue
                }
            } $snapshotKind $candidatePort
            Assert-GateB ([bool]$queryProbe.Rejected -and -not [bool]$queryProbe.Queried) `
                "$snapshotKind browser snapshot accepted malformed raw port before OS-query validation"
        }
        $validQueryProbe = & $module[0] {
            param($kind, $rawPort)
            $oldFunction = Get-Command Get-CimInstance -CommandType Function `
                -ErrorAction SilentlyContinue
            $oldScriptBlock = if ($null -ne $oldFunction) {
                $oldFunction.ScriptBlock
            } else { $null }
            try {
                $script:GateBSnapshotQueryReached = $false
                Set-Item Function:\Get-CimInstance -Force -Value {
                    $script:GateBSnapshotQueryReached = $true
                    return @()
                }
                if ($kind -eq 'process') {
                    [void](Get-VerifierBrowserProcessSnapshot '' '' '' '' $rawPort)
                } else {
                    [void](Get-VerifierBrowserOwnershipSnapshot '' '' '' '' $rawPort)
                }
                return $script:GateBSnapshotQueryReached
            } finally {
                if ($null -ne $oldFunction) {
                    Set-Item Function:\Get-CimInstance -Force -Value $oldScriptBlock
                } else {
                    Remove-Item Function:\Get-CimInstance -Force -ErrorAction SilentlyContinue
                }
                Remove-Variable -Name GateBSnapshotQueryReached -Scope Script `
                    -Force -ErrorAction SilentlyContinue
            }
        } $snapshotKind $port
        Assert-GateB ([bool]$validQueryProbe) `
            "$snapshotKind browser snapshot rejected an exact integral raw port"
    }
    Write-Host 'PASS:Get-NetTCPConnection raw LocalPort/State/LocalAddress fields require exact scalar values before cast or process lookup'
    foreach ($field in @('LocalAddress', 'Port', 'Source')) {
        foreach ($variant in $malformedValues) {
            $localAddress = $address
            $listenerPort = $port
            $listenerSource = $source
            if ($field -eq 'LocalAddress') { $localAddress = $variant.Value }
            elseif ($field -eq 'Port') { $listenerPort = $variant.Value }
            else { $listenerSource = $variant.Value }
            $rejected = & $module[0] {
                param($candidateAddress, $candidatePort, $candidateSource)
                try {
                    [void](New-VerifierKernelTransportListenerRecord `
                        $candidateAddress $candidatePort $candidateSource)
                    return $false
                } catch {
                    return (Test-VerifierInfrastructureError $_)
                }
            } $localAddress $listenerPort $listenerSource
            Assert-GateB ([bool]$rejected) `
                "kernel listener constructor accepted $field $($variant.Name)"
        }
    }

    $validReader = & $module[0] {
        param($processId, $candidateAddress, $candidatePort, $candidateSource)
        Get-VerifierListenerProcessRecord $processId $candidateAddress `
            $candidatePort $candidateSource
    } $PID $address $port $source
    Assert-GateB ($null -ne $validReader -and
        $validReader.ProcessId -eq $PID -and
        $validReader.ProcessStartTicks -gt 0 -and
        $validReader.ListenerOwnerKind -ceq 'user-process' -and
        $validReader.ListenerOwnerProof -ceq 'diagnostics-process-start-v1' -and
        $validReader.ListenerOwnerEvidence -ceq 'system-diagnostics-process-starttime') `
        'valid user-process listener reader record was rejected or altered'
    foreach ($field in @('ProcessId', 'LocalAddress', 'Port', 'Source')) {
        foreach ($variant in $malformedValues) {
            $processId = $PID
            $localAddress = $address
            $listenerPort = $port
            $listenerSource = $source
            if ($field -eq 'ProcessId') { $processId = $variant.Value }
            elseif ($field -eq 'LocalAddress') { $localAddress = $variant.Value }
            elseif ($field -eq 'Port') { $listenerPort = $variant.Value }
            else { $listenerSource = $variant.Value }
            $rejected = & $module[0] {
                param($candidateProcessId, $candidateAddress, $candidatePort, $candidateSource)
                try {
                    [void](Get-VerifierListenerProcessRecord $candidateProcessId `
                        $candidateAddress $candidatePort $candidateSource)
                    return $false
                } catch {
                    return (Test-VerifierInfrastructureError $_)
                }
            } $processId $localAddress $listenerPort $listenerSource
            Assert-GateB ([bool]$rejected) `
                "listener process-record reader accepted $field $($variant.Name)"
        }
    }
    Write-Host 'PASS:listener constructor/reader reject numeric-string, floating, Boolean, array, object, and null raw scalars before record creation'
}

function Invoke-GateBCallerIdentityScalarCanary() {
    $module = @(Get-Module VerifierIsolation | Select-Object -First 1)
    if ($module.Count -ne 1) { throw 'VerifierIsolation module was unavailable for caller identity scalar canary.' }
    $repoRoot = Get-VerifierFullPath (Join-Path $PSScriptRoot '..')
    $previewScript = Get-VerifierFullPath (Join-Path $repoRoot 'scripts\preview.ps1')
    $webRoot = Get-VerifierFullPath (Join-Path $repoRoot 'war')
    $port = 40251
    $identity = [ordered]@{
        protocol = 'troubleshootjs-preview-identity-v1'
        repositoryRoot = $repoRoot; previewScript = $previewScript; webRoot = $webRoot
        previewPort = $port; processId = 0; processStartTicks = 0
        verifierRunId = ''; verifierNonce = ''
    }
    foreach ($variantDefinition in @(
        [pscustomobject]@{ Name = 'numeric-string preview port'; Field = 'previewPort'; Value = [string]$port; Remove = $false }
        [pscustomobject]@{ Name = 'floating preview port'; Field = 'previewPort'; Value = [double]$port; Remove = $false }
        [pscustomobject]@{ Name = 'array process PID'; Field = 'processId'; Value = @([int]0); Remove = $false }
        [pscustomobject]@{ Name = 'object process start'; Field = 'processStartTicks'; Value = [pscustomobject]@{ Value = 0 }; Remove = $false }
        [pscustomobject]@{ Name = 'missing preview nonce'; Field = 'verifierNonce'; Value = $null; Remove = $true }
        [pscustomobject]@{ Name = 'missing process start'; Field = 'processStartTicks'; Value = $null; Remove = $true }
    )) {
        $variant = $identity | ConvertTo-Json -Depth 8 | ConvertFrom-Json
        if ($variantDefinition.Remove) {
            [void]$variant.PSObject.Properties.Remove($variantDefinition.Field)
        } else {
            $variant.($variantDefinition.Field) = $variantDefinition.Value
        }
        $context = [pscustomobject]@{
            WorktreeRoot = $repoRoot; BaseUrl = ''
            Server = [pscustomobject]@{
                Owner = 'caller'; BaseUrl = 'http://127.0.0.1:49999'
            }
        }
        $before = $context | ConvertTo-Json -Depth 8 -Compress
        $rejected = & $module[0] {
            param($targetContext, $identityContent, $identityUrl)
            $oldFunction = Get-Command Invoke-WebRequest -CommandType Function `
                -ErrorAction SilentlyContinue
            $oldScriptBlock = if ($null -ne $oldFunction) {
                $oldFunction.ScriptBlock
            } else { $null }
            try {
                $script:GateBCallerIdentityContent = $identityContent
                Set-Item Function:\Invoke-WebRequest -Force -Value {
                    param($UseBasicParsing, $Uri, $TimeoutSec)
                    return [pscustomobject]@{
                        Content = $script:GateBCallerIdentityContent
                    }
                }
                try {
                    Set-VerifierCallerOwnedPreview $targetContext $identityUrl
                    return $false
                } catch {
                    return (Test-VerifierInfrastructureError $_)
                }
            } finally {
                if ($null -ne $oldFunction) {
                    Set-Item Function:\Invoke-WebRequest -Force -Value $oldScriptBlock
                } else {
                    Remove-Item Function:\Invoke-WebRequest -Force `
                        -ErrorAction SilentlyContinue
                }
                Remove-Variable -Name GateBCallerIdentityContent -Scope Script `
                    -Force -ErrorAction SilentlyContinue
            }
        } $context ($variant | ConvertTo-Json -Depth 8) `
            ('http://127.0.0.1:' + [string]$port)
        Assert-GateB ([bool]$rejected) `
            "caller-owned preview accepted $($variantDefinition.Name) identity proof"
        Assert-GateB (($context | ConvertTo-Json -Depth 8 -Compress) -eq $before) `
            "caller-owned preview scalar rejection mutated state for $($variantDefinition.Name)"
    }
    Write-Host 'PASS:caller-owned preview identity rejects missing, numeric-string, floating, array, and object scalar fields before mutation'
}

function Invoke-GateBListenerTrustBoundaryCanary() {
    $module = @(Get-Module VerifierIsolation | Select-Object -First 1)
    if ($module.Count -ne 1) { throw 'VerifierIsolation module was unavailable for listener trust-boundary canary.' }
    $currentProcess = Get-Process -Id $PID -ErrorAction Stop
        $currentStart = [long](Get-VerifierProcessStartTicks $currentProcess)
    $repositoryRootForCanary = Get-VerifierFullPath (Join-Path $PSScriptRoot '..')
    $validInspection = [pscustomobject]@{
        Success = $true; Known = $true; HasListeners = $true
        Listeners = @([pscustomobject]@{
            LocalAddress = '127.0.0.1'; Port = 40205; ProcessId = $PID
            ProcessStartTicks = $currentStart; ListenerOwnerKind = 'user-process'
            ListenerOwnerProof = 'diagnostics-process-start-v1'
            ListenerOwnerEvidence = 'system-diagnostics-process-starttime'
            Source = 'Get-NetTCPConnection'
        })
        ListenerOwnerKind = 'user-process'
        ListenerOwnerProof = 'diagnostics-process-start-v1'
        ListenerOwnerEvidence = 'system-diagnostics-process-starttime'
        Source = 'Get-NetTCPConnection'; Error = ''
    }
    $liveContext = [pscustomobject]@{}
    foreach ($propertyDefinition in @(
            [pscustomobject]@{ Name = 'numeric-string'; Field = 'CdpPort'; Value = [string]40205 }
            [pscustomobject]@{ Name = 'floating'; Field = 'CdpPort'; Value = [double]40205.5 }
            [pscustomobject]@{ Name = 'Boolean'; Field = 'CdpPort'; Value = $true }
            [pscustomobject]@{ Name = 'array'; Field = 'CdpPort'; Value = @(40205) }
            [pscustomobject]@{ Name = 'object'; Field = 'CdpPort'; Value = [pscustomobject]@{ Value = 40205 } }
            [pscustomobject]@{ Name = 'null'; Field = 'CdpPort'; Value = $null }
            [pscustomobject]@{ Name = 'numeric-string Port'; Field = 'Port'; Value = [string]40205 }
            [pscustomobject]@{ Name = 'floating Port'; Field = 'Port'; Value = [double]40205.5 }
            [pscustomobject]@{ Name = 'Boolean Port'; Field = 'Port'; Value = $true }
            [pscustomobject]@{ Name = 'array Port'; Field = 'Port'; Value = @(40205) }
            [pscustomobject]@{ Name = 'object Port'; Field = 'Port'; Value = [pscustomobject]@{ Value = 40205 } }
            [pscustomobject]@{ Name = 'null Port'; Field = 'Port'; Value = $null }
        )) {
        $owner = [pscustomobject]@{ Profile = 'C:\listener-trust-boundary-profile' }
        Add-Member -InputObject $owner -NotePropertyName $propertyDefinition.Field `
            -NotePropertyValue $propertyDefinition.Value -Force
        $liveResult = & $module[0] {
            param($inspection, $context, $owner, $processId, $startTicks)
            $oldFunction = (Get-Command Get-VerifierBrowserOwnershipSnapshot `
                -CommandType Function -ErrorAction Stop).ScriptBlock
            try {
                $script:GateBOwnershipSnapshotQueried = $false
                Set-Item Function:\Get-VerifierBrowserOwnershipSnapshot -Force -Value {
                    $script:GateBOwnershipSnapshotQueried = $true
                    return @()
                }
                $accepted = Test-VerifierLiveListenerInspectionAuthorization `
                    $inspection $context $owner $processId $startTicks
                return [pscustomobject]@{
                    Accepted = [bool]$accepted
                    QueryObserved = [bool]$script:GateBOwnershipSnapshotQueried
                }
            } finally {
                Set-Item Function:\Get-VerifierBrowserOwnershipSnapshot -Force `
                    -Value $oldFunction
                Remove-Variable -Name GateBOwnershipSnapshotQueried -Scope Script `
                    -Force -ErrorAction SilentlyContinue
            }
        } $validInspection $liveContext $owner $PID $currentStart
        Assert-GateB (-not [bool]$liveResult.Accepted -and
            -not [bool]$liveResult.QueryObserved) `
            "live listener caller accepted malformed raw owner $($propertyDefinition.Name) or queried ownership"
    }

    $invokeBindBoundaryCase = {
        param([string]$CaseName, $InvalidValue)
        & $module[0] {
            param($root, $caseName, $invalidValue)
            $runId = 'gate-b-listener-trust-boundary-run'
            $repositoryIdentity = 'gate-b-listener-trust-boundary-repository'
            $port = 40206
            $current = Get-Process -Id $PID -ErrorAction Stop
            $startTicks = [long](Get-VerifierProcessStartTicks $current)
            $claimName = Get-VerifierPortMutexName $null $port
            $mutex = [Threading.Mutex]::new($false)
            $mutexHeld = $false
            try {
                $mutexHeld = $mutex.WaitOne(0)
                if (-not $mutexHeld) { Throw-VerifierInfrastructure 'trust-boundary canary mutex was not acquired.' }
                $lease = [pscustomobject]@{
                    LeaseId = 'gate-b-listener-trust-boundary-lease'; Kind = 'cdp'
                    Port = $port; Path = (Join-Path $root 'trust-boundary.lease')
                    RunId = $runId; RepositoryIdentity = $repositoryIdentity
                    WorktreeRoot = $root; Status = 'leased'; ClaimName = $claimName
                    ClaimState = 'held'; ProfilePath = ''; BrowserPath = ''
                    BindValidatedUtc = ''; ReleasedUtc = ''
                    ReleaseState = 'active'; ReleaseJournalState = 'active'
                    ReleaseBlockReason = ''; ListenerInspectionUtc = ''
                    ClaimOwnerPid = $PID; ClaimOwnerStartTicks = $startTicks
                    BoundProcessId = 0; BoundProcessStartTicks = 0
                    ListenerProcessId = 0; ListenerProcessStartTicks = 0L
                    ListenerOwnerKind = 'none'; ListenerOwnerProof = ''
                    ListenerOwnerEvidence = ''; Registered = $true
                    ReleaseBlocked = $false; MutexReleased = $false
                    ListenerInspectionSuccess = $false; ListenerInspectionKnown = $false
                    ListenerHasListeners = $null; ListenerAbsent = $null
                    ProcessProofRequired = $false; ProcessTerminationProven = $false
                    ProcessAbsent = $false; ClaimMutex = $mutex
                }
                $server = [pscustomobject]@{
                    Owner = 'none'; BaseUrl = ''; Port = 0; ProcessId = 0
                    ProcessStartTicks = 0L; ProcessParentProcessId = 0
                    ProcessParentProcessStartTicks = 0L; ProcessCommandLine = ''
                    Script = ''; RepositoryRoot = $root; WebRoot = (Join-Path $root 'war')
                    IdentityProtocol = ''; IdentityVerified = $false; CallerOwned = $false
                    RunId = ''; Nonce = ''; Lease = $null; Process = $null
                    State = 'not-started'; StdoutLog = ''; StderrLog = ''
                    CleanupResult = 'not-applicable'; Error = ''
                    ProcessIdentityKnown = $false; OwnershipUncertain = $false
                    ProcessTerminationProven = $false; ProcessAbsent = $false
                    ListenerInspectionProven = $false; ListenerAbsent = $null
                }
                $context = [pscustomobject]@{
                    Server = $server; WorktreeRoot = $root
                    RepositoryIdentity = $repositoryIdentity; RunId = $runId
                    PreviewNonce = 'gate-b-listener-trust-boundary-nonce'
                }
                $server.Lease = $lease
                $script:VerifierHeldPortClaims[$claimName] = $runId
                $script:VerifierHeldPortMutexes[$claimName] = $mutex
                $oldFunction = (Get-Command Get-VerifierLoopbackListenerRecords `
                    -CommandType Function -ErrorAction Stop).ScriptBlock
                try {
                    $script:GateBListenerQueryObserved = $false
                    Set-Item Function:\Get-VerifierLoopbackListenerRecords -Force -Value {
                        $script:GateBListenerQueryObserved = $true
                        return [pscustomobject]@{
                            Success = $true; Known = $true; HasListeners = $false
                            Listeners = @(); ListenerOwnerKind = 'none'
                            ListenerOwnerProof = ''; ListenerOwnerEvidence = ''
                            Source = 'Get-NetTCPConnection'; Error = ''
                        }
                    }
                    $owner = $null
                    switch ($caseName) {
                        'server-port' { $context.Server.Port = $invalidValue }
                        'lease-pid' { $lease.ClaimOwnerPid = $invalidValue }
                        'claim-mutex' { $lease.ClaimMutex = $invalidValue }
                        'owner-port' { $owner = [pscustomobject]@{ Port = $invalidValue } }
                    }
                    $before = ConvertTo-Json ([pscustomobject]@{
                        LeasePort = $lease.Port; ClaimOwnerPid = $lease.ClaimOwnerPid
                        ClaimState = $lease.ClaimState; ServerPort = $context.Server.Port
                    }) -Compress
                    $rejected = $false
                    try {
                        Confirm-VerifierPortLeaseBound $context $lease $PID $startTicks $owner
                    } catch { $rejected = Test-VerifierInfrastructureError $_ }
                    $after = ConvertTo-Json ([pscustomobject]@{
                        LeasePort = $lease.Port; ClaimOwnerPid = $lease.ClaimOwnerPid
                        ClaimState = $lease.ClaimState; ServerPort = $context.Server.Port
                    }) -Compress
                    return [pscustomobject]@{
                        Rejected = [bool]$rejected
                        QueryObserved = [bool]$script:GateBListenerQueryObserved
                        Unchanged = ($before -ceq $after)
                    }
                } finally {
                    Set-Item Function:\Get-VerifierLoopbackListenerRecords -Force `
                        -Value $oldFunction
                    Remove-Variable -Name GateBListenerQueryObserved -Scope Script `
                        -Force -ErrorAction SilentlyContinue
                }
            } finally {
                if ($script:VerifierHeldPortClaims.ContainsKey($claimName)) {
                    [void]$script:VerifierHeldPortClaims.Remove($claimName)
                }
                if ($script:VerifierHeldPortMutexes.ContainsKey($claimName) -and
                        [object]::ReferenceEquals($script:VerifierHeldPortMutexes[$claimName], $mutex)) {
                    [void]$script:VerifierHeldPortMutexes.Remove($claimName)
                }
                if ($mutexHeld) { [void]$mutex.ReleaseMutex() }
                $mutex.Dispose()
            }
        } $repositoryRootForCanary $CaseName $InvalidValue
    }
    foreach ($caseDefinition in @(
            [pscustomobject]@{ Name = 'server-port'; Value = [string]40206 }
            [pscustomobject]@{ Name = 'lease-pid'; Value = [string]$PID }
            [pscustomobject]@{ Name = 'claim-mutex'; Value = 'not-a-mutex' }
        )) {
        $result = & $invokeBindBoundaryCase $caseDefinition.Name $caseDefinition.Value
        Assert-GateB ([bool]$result.Rejected -and -not [bool]$result.QueryObserved -and
            [bool]$result.Unchanged) `
            "bind consumer did not fail closed without mutation for malformed $($caseDefinition.Name)"
    }
    foreach ($ownerPortValue in @(
            [string]40206, [double]40206.5, $true, @(40206),
            [pscustomobject]@{ Value = 40206 }, $null
        )) {
        $result = & $invokeBindBoundaryCase 'owner-port' $ownerPortValue
        Assert-GateB ([bool]$result.Rejected -and -not [bool]$result.QueryObserved -and
            [bool]$result.Unchanged) `
            'bind consumer cast or queried a malformed raw owner port before rejection'
    }

    $invokePreviewBoundaryCase = {
        param([string]$Field, $InvalidValue)
        & $module[0] {
            param($root, $field, $invalidValue)
            $runId = 'gate-b-preview-trust-boundary-run'
            $repositoryIdentity = 'gate-b-preview-trust-boundary-repository'
            $port = 40207
            $previewScript = Get-VerifierFullPath (Join-Path $root 'scripts\preview.ps1')
            $webRoot = Get-VerifierFullPath (Join-Path $root 'war')
            $current = Get-Process -Id $PID -ErrorAction Stop
            $startTicks = [long](Get-VerifierProcessStartTicks $current)
            $lease = [pscustomobject]@{
                LeaseId = 'gate-b-preview-trust-boundary-lease'; Kind = 'preview'
                Port = $port; Path = (Join-Path $root 'preview-trust-boundary.lease')
                RunId = $runId; RepositoryIdentity = $repositoryIdentity
                WorktreeRoot = $root; Status = 'bound'
                ClaimName = Get-VerifierPortMutexName $null $port; ClaimState = 'bound'
                ProfilePath = ''; BrowserPath = ''; BindValidatedUtc = ''; ReleasedUtc = ''
                ReleaseState = 'active'; ReleaseJournalState = 'active'; ReleaseBlockReason = ''
                ListenerInspectionUtc = ''; ClaimOwnerPid = $PID
                ClaimOwnerStartTicks = $startTicks; BoundProcessId = $PID
                BoundProcessStartTicks = $startTicks; ListenerProcessId = 0
                ListenerProcessStartTicks = 0L; ListenerOwnerKind = 'none'
                ListenerOwnerProof = ''; ListenerOwnerEvidence = ''
                Registered = $true; ReleaseBlocked = $false; MutexReleased = $false
                ListenerInspectionSuccess = $false; ListenerInspectionKnown = $false
                ListenerHasListeners = $null; ListenerAbsent = $null
                ProcessProofRequired = $true; ProcessTerminationProven = $false
                ProcessAbsent = $false
            }
            $server = [pscustomobject]@{
                Owner = 'run'; BaseUrl = 'http://127.0.0.1:' + [string]$port
                Port = $port; ProcessId = $PID; ProcessStartTicks = $startTicks
                ProcessParentProcessId = 1; ProcessParentProcessStartTicks = 1L
                ProcessCommandLine = 'powershell.exe -File ' + $previewScript
                Script = $previewScript; RepositoryRoot = $root; WebRoot = $webRoot
                IdentityProtocol = 'troubleshootjs-preview-identity-v1'
                IdentityVerified = $true; CallerOwned = $false; RunId = $runId
                Nonce = 'gate-b-preview-trust-boundary-nonce'; State = 'run-owned-verified'
                Lease = $lease; Process = $current
                StdoutLog = (Join-Path $root 'preview-trust-boundary.stdout.log')
                StderrLog = (Join-Path $root 'preview-trust-boundary.stderr.log')
                CleanupResult = 'pending'; Error = ''; ProcessIdentityKnown = $true
                OwnershipUncertain = $false; ProcessTerminationProven = $false
                ProcessAbsent = $false; ListenerInspectionProven = $false
                ListenerAbsent = $false
            }
            $context = [pscustomobject]@{
                Server = $server; WorktreeRoot = $root
                RepositoryIdentity = $repositoryIdentity; RunId = $runId
                PreviewNonce = 'gate-b-preview-trust-boundary-nonce'
            }
            $oldProcessLookup = (Get-Command Get-VerifierProcessById `
                -CommandType Function -ErrorAction Stop).ScriptBlock
            $oldListenerLookup = (Get-Command Get-VerifierLoopbackListenerRecords `
                -CommandType Function -ErrorAction Stop).ScriptBlock
            $oldStop = (Get-Command Stop-VerifierVerifiedProcessExactly `
                -CommandType Function -ErrorAction Stop).ScriptBlock
            try {
                $script:GateBPreviewProcessLookupObserved = $false
                $script:GateBPreviewListenerLookupObserved = $false
                $script:GateBPreviewStopObserved = $false
                Set-Item Function:\Get-VerifierProcessById -Force -Value {
                    $script:GateBPreviewProcessLookupObserved = $true
                    return $null
                }
                Set-Item Function:\Get-VerifierLoopbackListenerRecords -Force -Value {
                    $script:GateBPreviewListenerLookupObserved = $true
                    return $null
                }
                Set-Item Function:\Stop-VerifierVerifiedProcessExactly -Force -Value {
                    $script:GateBPreviewStopObserved = $true
                    return $true
                }
                $context.Server.($field) = $invalidValue
                $before = ConvertTo-Json ([pscustomobject]@{
                    Port = $context.Server.Port; ProcessId = $context.Server.ProcessId
                    ProcessStartTicks = $context.Server.ProcessStartTicks
                    ParentProcessId = $context.Server.ProcessParentProcessId
                    ParentProcessStartTicks = $context.Server.ProcessParentProcessStartTicks
                    CleanupResult = $context.Server.CleanupResult
                }) -Compress
                $rejected = $false
                try { Complete-VerifierPreview $context }
                catch { $rejected = Test-VerifierInfrastructureError $_ }
                $after = ConvertTo-Json ([pscustomobject]@{
                    Port = $context.Server.Port; ProcessId = $context.Server.ProcessId
                    ProcessStartTicks = $context.Server.ProcessStartTicks
                    ParentProcessId = $context.Server.ProcessParentProcessId
                    ParentProcessStartTicks = $context.Server.ProcessParentProcessStartTicks
                    CleanupResult = $context.Server.CleanupResult
                }) -Compress
                return [pscustomobject]@{
                    Rejected = [bool]$rejected; Unchanged = ($before -ceq $after)
                    ProcessLookupObserved = [bool]$script:GateBPreviewProcessLookupObserved
                    ListenerLookupObserved = [bool]$script:GateBPreviewListenerLookupObserved
                    StopObserved = [bool]$script:GateBPreviewStopObserved
                    ProcessAlive = -not [bool]$current.HasExited
                }
            } finally {
                Set-Item Function:\Get-VerifierProcessById -Force -Value $oldProcessLookup
                Set-Item Function:\Get-VerifierLoopbackListenerRecords -Force `
                    -Value $oldListenerLookup
                Set-Item Function:\Stop-VerifierVerifiedProcessExactly -Force -Value $oldStop
                Remove-Variable -Name GateBPreviewProcessLookupObserved -Scope Script `
                    -Force -ErrorAction SilentlyContinue
                Remove-Variable -Name GateBPreviewListenerLookupObserved -Scope Script `
                    -Force -ErrorAction SilentlyContinue
                Remove-Variable -Name GateBPreviewStopObserved -Scope Script `
                    -Force -ErrorAction SilentlyContinue
            }
        } $repositoryRootForCanary $Field $InvalidValue
    }
    foreach ($serverField in @('Port', 'ProcessId', 'ProcessStartTicks',
            'ProcessParentProcessId', 'ProcessParentProcessStartTicks')) {
        foreach ($invalidServerValue in @(
                [string]40207, [double]40207.5, $true, @(40207),
                [pscustomobject]@{ Value = 40207 }, $null
            )) {
            $result = & $invokePreviewBoundaryCase $serverField $invalidServerValue
            Assert-GateB ([bool]$result.Rejected -and [bool]$result.Unchanged -and
                -not [bool]$result.ProcessLookupObserved -and
                -not [bool]$result.ListenerLookupObserved -and
                -not [bool]$result.StopObserved -and [bool]$result.ProcessAlive) `
                "preview cleanup cast, queried, stopped, or mutated before rejecting malformed raw $serverField"
        }
    }

    $invokeAbsenceBoundaryCase = {
        param($CandidateProcessId, $CandidateStartTicks)
        & $module[0] {
            param($processId, $startTicks)
            $oldFunction = (Get-Command Get-VerifierCurrentProcessRecordById `
                -CommandType Function -ErrorAction Stop).ScriptBlock
            try {
                $script:GateBRecordedProcessQueryObserved = $false
                Set-Item Function:\Get-VerifierCurrentProcessRecordById -Force -Value {
                    $script:GateBRecordedProcessQueryObserved = $true
                    return $null
                }
                $record = [pscustomobject]@{
                    ProcessId = $processId; ProcessStartTicks = $startTicks
                }
                $rejected = $false
                try { [void](Confirm-VerifierRecordedProcessAbsent $record 'trust-boundary canary') }
                catch { $rejected = Test-VerifierInfrastructureError $_ }
                return [pscustomobject]@{
                    Rejected = [bool]$rejected
                    QueryObserved = [bool]$script:GateBRecordedProcessQueryObserved
                }
            } finally {
                Set-Item Function:\Get-VerifierCurrentProcessRecordById -Force `
                    -Value $oldFunction
                Remove-Variable -Name GateBRecordedProcessQueryObserved -Scope Script `
                    -Force -ErrorAction SilentlyContinue
            }
        } $CandidateProcessId $CandidateStartTicks
    }
    foreach ($invalidProcessId in @(
            [string]$PID, [double]$PID, $true, @($PID),
            [pscustomobject]@{ Value = $PID }, $null
        )) {
        $result = & $invokeAbsenceBoundaryCase $invalidProcessId $currentStart
        Assert-GateB ([bool]$result.Rejected -and -not [bool]$result.QueryObserved) `
            'recorded-process absence consumer accepted or queried a malformed raw PID'
    }
    foreach ($invalidStartTicks in @(
            [string]$currentStart, [double]$currentStart + 0.5, $true,
            @($currentStart), [pscustomobject]@{ Value = $currentStart }, $null
        )) {
        $result = & $invokeAbsenceBoundaryCase $PID $invalidStartTicks
        Assert-GateB ([bool]$result.Rejected -and -not [bool]$result.QueryObserved) `
            'recorded-process absence consumer accepted or queried a malformed raw start identity'
    }
    $explicitAbsence = & $module[0] {
        $oldFunction = (Get-Command Get-VerifierCurrentProcessRecordById `
            -CommandType Function -ErrorAction Stop).ScriptBlock
        try {
            $script:GateBRecordedProcessQueryObserved = $false
            Set-Item Function:\Get-VerifierCurrentProcessRecordById -Force -Value {
                $script:GateBRecordedProcessQueryObserved = $true
                return $null
            }
            $zero = Confirm-VerifierRecordedProcessAbsent `
                ([pscustomobject]@{ ProcessId = 0; ProcessStartTicks = 0L }) `
                'trust-boundary explicit absence canary'
            $nullRecord = Confirm-VerifierRecordedProcessAbsent $null `
                'trust-boundary null absence canary'
            $malformedZeroRejected = $false
            try {
                [void](Confirm-VerifierRecordedProcessAbsent `
                    ([pscustomobject]@{
                        ProcessId = 0; ProcessStartTicks = 0L; ParentProcessId = '0'
                    }) 'trust-boundary malformed explicit absence canary')
            } catch { $malformedZeroRejected = Test-VerifierInfrastructureError $_ }
            return [pscustomobject]@{
                Valid = ($zero.Absent -and $nullRecord.Absent -and $malformedZeroRejected)
                QueryObserved = [bool]$script:GateBRecordedProcessQueryObserved
            }
        } finally {
            Set-Item Function:\Get-VerifierCurrentProcessRecordById -Force `
                -Value $oldFunction
            Remove-Variable -Name GateBRecordedProcessQueryObserved -Scope Script `
                -Force -ErrorAction SilentlyContinue
        }
    }
    Assert-GateB ([bool]$explicitAbsence.Valid -and
        -not [bool]$explicitAbsence.QueryObserved) `
        'explicit process absence state was not accepted without a process query'
    Write-Host ('PASS:live/bind/absence listener callers reject malformed raw ports, ' +
        'lease/server/mutex state, and PID/start identities before OS queries or mutation')
}

function Invoke-GateBListenerRecordConsumerCanary() {
    $module = @(Get-Module VerifierIsolation | Select-Object -First 1)
    if ($module.Count -ne 1) { throw 'VerifierIsolation module was unavailable for listener consumer canary.' }
    $currentProcess = Get-Process -Id $PID -ErrorAction Stop
    $currentStart = [long](Get-VerifierProcessStartTicks $currentProcess)
    $port = 40201
    $validUserListener = [pscustomobject]@{
        LocalAddress = '127.0.0.1'; Port = $port; ProcessId = $PID
        ProcessStartTicks = $currentStart; Source = 'Get-NetTCPConnection'
        ListenerOwnerKind = 'user-process'
         ListenerOwnerProof = 'diagnostics-process-start-v1'
         ListenerOwnerEvidence = 'system-diagnostics-process-starttime'
     }
    $directOwner = [pscustomobject]@{
        DirectProcessOwner = $true; Process = $currentProcess; ProcessId = $PID
        ProcessStartTicks = $currentStart; IdentityProof = 'retained-process-object-v1'
    }
    $belongsToOwner = & $module[0] {
        param($owner, $listener, $processId, $startTicks)
        Test-VerifierListenerBelongsToOwner $owner $listener $processId $startTicks $null
    } $directOwner $validUserListener $PID $currentStart
    Assert-GateB ([bool]$belongsToOwner) `
        'exact user-process listener was rejected by the live ownership consumer'
    $pidOnlyBelongs = & $module[0] {
        param($listener, $processId, $startTicks)
        Test-VerifierListenerBelongsToOwner $null $listener $processId $startTicks $null
    } $validUserListener $PID $currentStart
    Assert-GateB (-not [bool]$pidOnlyBelongs) `
        'PID/start-only listener ownership was accepted without an owner record'

    $canonicalDelegationObserved = & $module[0] {
        param($listener)
        $oldCanonical = (Get-Command Test-VerifierListenerOwnerTuple `
            -CommandType Function -ErrorAction Stop).ScriptBlock
        try {
            Set-Item Function:\Test-VerifierListenerOwnerTuple -Force -Value {
                param($OwnerKind, $OwnerProof, $OwnerEvidence, $ProcessId,
                    $ProcessStartTicks, $AllowNone)
                return $false
            }
            $recordRejected = -not (Test-VerifierListenerRecordSchema $listener)
            $absence = [pscustomobject]@{
                Success = $true; Known = $true; HasListeners = $false
                Listeners = @(); ListenerOwnerKind = 'none'
                ListenerOwnerProof = ''; ListenerOwnerEvidence = ''
                Source = 'Get-NetTCPConnection'; Error = ''
            }
            $inspectionRejected = -not (Test-VerifierListenerInspectionSchema $absence)
            return ($recordRejected -and $inspectionRejected)
        } finally {
            Set-Item Function:\Test-VerifierListenerOwnerTuple -Force `
                -Value $oldCanonical
        }
    } $validUserListener
    Assert-GateB ([bool]$canonicalDelegationObserved) `
        'listener record/inspection wrappers did not delegate semantic owner validation to the canonical tuple validator'

    $nullListenerInspection = [pscustomobject]([ordered]@{
        Success = $true; Known = $true; HasListeners = $false
        Listeners = $null; ListenerOwnerKind = 'none'
        ListenerOwnerProof = ''; ListenerOwnerEvidence = ''
        Source = 'Get-NetTCPConnection'; Error = ''
    })
    $newNullCollectionRejected = $false
    try {
        & $module[0] {
            [void](New-VerifierListenerInspection $true $true $null `
                'Get-NetTCPConnection')
        }
    } catch {
        $newNullCollectionRejected = Test-VerifierInfrastructureError $_
    }
    Assert-GateB $newNullCollectionRejected `
        'listener inspection constructor normalized a null collection to absence'
    $explicitAbsenceInspection = & $module[0] {
        New-VerifierListenerInspection $true $true @() 'Get-NetTCPConnection'
    }
    Assert-GateB ($null -ne $explicitAbsenceInspection -and
        -not [bool]$explicitAbsenceInspection.HasListeners -and
        @($explicitAbsenceInspection.Listeners).Count -eq 0) `
        'explicit empty listener collection no longer proves valid absence'

    $nullLease = [pscustomobject]@{
        Status = 'held'; ClaimOwnerPid = $PID
        ClaimOwnerStartTicks = $currentStart; Port = $port
        ListenerProcessId = 0; ListenerProcessStartTicks = 0
        ListenerOwnerKind = 'none'; ListenerOwnerProof = ''
        ListenerOwnerEvidence = ''
        ListenerInspectionSuccess = $false; ListenerInspectionKnown = $false
        ListenerHasListeners = $null; ListenerAbsent = $null
    }
    $setNullCollectionRejected = $false
    try {
        & $module[0] {
            param($lease, $inspection)
            Set-VerifierLeaseListenerInspection $lease $inspection
        } $nullLease $nullListenerInspection
    } catch {
        $setNullCollectionRejected = Test-VerifierInfrastructureError $_
    }
    Assert-GateB $setNullCollectionRejected `
        'listener lease setter accepted an inspection with a null collection'

    $boundNullCollectionRejected = & $module[0] {
        param($inspection, $lease, $processId, $startTicks)
        $oldFunction = (Get-Command Get-VerifierLoopbackListenerRecords `
            -CommandType Function -ErrorAction Stop).ScriptBlock
        try {
            $script:GateBInjectedListenerBoundaryInspection = $inspection
            Set-Item Function:\Get-VerifierLoopbackListenerRecords -Force -Value {
                param([int]$Port, $PreviewContext, $PreviewOwner)
                return $script:GateBInjectedListenerBoundaryInspection
            }
            try {
                Confirm-VerifierPortLeaseBound $null $lease $processId $startTicks $null
                return $false
            } catch {
                return (Test-VerifierInfrastructureError $_)
            }
        } finally {
            Set-Item Function:\Get-VerifierLoopbackListenerRecords -Force -Value $oldFunction
            Remove-Variable -Name GateBInjectedListenerBoundaryInspection `
                -Scope Script -Force -ErrorAction SilentlyContinue
        }
    } $nullListenerInspection $nullLease $PID $currentStart
    Assert-GateB ([bool]$boundNullCollectionRejected) `
        'bound-port consumer accepted an inspection with a null collection'

    $releasedNullCollectionRejected = & $module[0] {
        param($inspection, $boundPort)
        $oldFunction = (Get-Command Get-VerifierLoopbackListenerRecords `
            -CommandType Function -ErrorAction Stop).ScriptBlock
        try {
            $script:GateBInjectedListenerBoundaryInspection = $inspection
            Set-Item Function:\Get-VerifierLoopbackListenerRecords -Force -Value {
                param([int]$Port, $PreviewContext, $PreviewOwner)
                return $script:GateBInjectedListenerBoundaryInspection
            }
            try {
                Confirm-VerifierReleasedListener $boundPort
                return $false
            } catch {
                return (Test-VerifierInfrastructureError $_)
            }
        } finally {
            Set-Item Function:\Get-VerifierLoopbackListenerRecords -Force -Value $oldFunction
            Remove-Variable -Name GateBInjectedListenerBoundaryInspection `
                -Scope Script -Force -ErrorAction SilentlyContinue
        }
    } $nullListenerInspection $port
    Assert-GateB ([bool]$releasedNullCollectionRejected) `
        'released-listener consumer accepted an inspection with a null collection'

    $mismatchedPortInspection = [pscustomobject]@{
        Success = $true; Known = $true; HasListeners = $true
        Source = 'Get-NetTCPConnection'
        Listeners = @($validUserListener | Select-Object *)
        ListenerOwnerKind = 'user-process'
        ListenerOwnerProof = 'diagnostics-process-start-v1'
        ListenerOwnerEvidence = 'system-diagnostics-process-starttime'
        Error = ''
    }
    $mismatchedPortInspection.Listeners[0].Port = $port + 1
    $setPortMismatchLease = $nullLease | Select-Object *
    $setPortMismatchBefore = $setPortMismatchLease | ConvertTo-Json -Depth 16 -Compress
    $setPortMismatchRejected = $false
    try {
        & $module[0] {
            param($lease, $inspection)
            Set-VerifierLeaseListenerInspection $lease $inspection
        } $setPortMismatchLease $mismatchedPortInspection
    } catch {
        $setPortMismatchRejected = Test-VerifierInfrastructureError $_
    }
    Assert-GateB $setPortMismatchRejected `
        'listener lease setter accepted a listener on a port different from the lease'
    Assert-GateB (($setPortMismatchLease | ConvertTo-Json -Depth 16 -Compress) -eq
        $setPortMismatchBefore) `
        'listener lease setter mutated state after rejecting a mismatched listener port'

    $boundPortMismatchRejected = & $module[0] {
        param($inspection, $lease, $processId, $startTicks)
        $oldFunction = (Get-Command Get-VerifierLoopbackListenerRecords `
            -CommandType Function -ErrorAction Stop).ScriptBlock
        try {
            $script:GateBInjectedListenerBoundaryInspection = $inspection
            Set-Item Function:\Get-VerifierLoopbackListenerRecords -Force -Value {
                param([int]$Port, $PreviewContext, $PreviewOwner)
                return $script:GateBInjectedListenerBoundaryInspection
            }
            try {
                Confirm-VerifierPortLeaseBound $null $lease $processId $startTicks $null
                return $false
            } catch {
                return (Test-VerifierInfrastructureError $_)
            }
        } finally {
            Set-Item Function:\Get-VerifierLoopbackListenerRecords -Force -Value $oldFunction
            Remove-Variable -Name GateBInjectedListenerBoundaryInspection `
                -Scope Script -Force -ErrorAction SilentlyContinue
        }
    } $mismatchedPortInspection $nullLease $PID $currentStart
    Assert-GateB ([bool]$boundPortMismatchRejected) `
        'bound-port consumer accepted a listener on a port different from the lease'

    $releasedPortMismatchRejected = & $module[0] {
        param($inspection, $leasedPort)
        $oldFunction = (Get-Command Get-VerifierLoopbackListenerRecords `
            -CommandType Function -ErrorAction Stop).ScriptBlock
        try {
            $script:GateBInjectedListenerBoundaryInspection = $inspection
            Set-Item Function:\Get-VerifierLoopbackListenerRecords -Force -Value {
                param([int]$Port, $PreviewContext, $PreviewOwner)
                return $script:GateBInjectedListenerBoundaryInspection
            }
            try {
                Confirm-VerifierReleasedListener $leasedPort
                return $false
            } catch {
                return (Test-VerifierInfrastructureError $_)
            }
        } finally {
            Set-Item Function:\Get-VerifierLoopbackListenerRecords -Force -Value $oldFunction
            Remove-Variable -Name GateBInjectedListenerBoundaryInspection `
                -Scope Script -Force -ErrorAction SilentlyContinue
        }
    } $mismatchedPortInspection $port
    Assert-GateB ([bool]$releasedPortMismatchRejected) `
        'released-listener consumer accepted a listener on a port different from the lease'

    $manifestContext = [pscustomobject]@{
        Protocol = 'troubleshootjs-verifier-run-v1'
        RunId = 'gate-b-manifest-run'; RepositoryIdentity = 'gate-b-manifest-repository'
        WorktreeRoot = $repositoryRoot; RunRoot = 'C:\gate-b\run'
        RunNamespaceRoot = 'C:\gate-b'; EvidenceDirectory = 'C:\gate-b\run\evidence'
        EvidenceNamespaceRoot = 'C:\gate-b\run'; ManifestPath = 'C:\gate-b\run\manifest.json'
        PortLeaseRoot = 'C:\gate-b\run\port-leases'
        CreatedUtc = '2026-08-30T00:00:00.0000000Z'; BaseUrl = ''
        PreviewNonce = 'gate-b-manifest-nonce'; LeaseRecords = @()
        BrowserSessions = @(); Artifacts = @(); CleanupState = 'pending'
        CleanupCompletedUtc = ''; CleanupErrors = @()
        Server = [pscustomobject]@{
            Owner = 'caller'; BaseUrl = 'http://127.0.0.1:40201'; Port = 40201
            ProcessId = 0; ProcessStartTicks = 0; ProcessParentProcessId = 0
            ProcessParentProcessStartTicks = 0; ProcessCommandLine = ''
            RepositoryRoot = $repositoryRoot; WebRoot = (Join-Path $repositoryRoot 'war')
            IdentityProtocol = 'troubleshootjs-preview-identity-v1'
            IdentityVerified = $true; CallerOwned = $true
            Script = (Join-Path $repositoryRoot 'scripts\preview.ps1')
            RunId = ''; Nonce = ''; Lease = $null; Process = $null
            State = 'caller-verified'; StdoutLog = ''; StderrLog = ''
            CleanupResult = 'not-owned'; Error = ''
            ProcessIdentityKnown = $false; OwnershipUncertain = $false
            ProcessTerminationProven = $false; ProcessAbsent = $true
            ListenerInspectionProven = $false; ListenerAbsent = $false
        }
    }
    $manifestLease = [pscustomobject]@{
        LeaseId = 'gate-b-manifest-lease'; Kind = 'cdp'; Port = 40201
        RunId = $manifestContext.RunId; RepositoryIdentity = $manifestContext.RepositoryIdentity
        WorktreeRoot = $manifestContext.WorktreeRoot
        Path = 'C:\gate-b\run\port-leases\manifest.lease'; Status = 'released'
        ClaimName = Get-VerifierPortMutexName $null 40201; ClaimState = 'released'
        ClaimOwnerPid = $PID; ClaimOwnerStartTicks = $currentStart
        ProfilePath = ''; BrowserPath = ''; Registered = $true
        BoundProcessId = 0; BoundProcessStartTicks = 0
        ListenerProcessId = 0; ListenerProcessStartTicks = 0
        ListenerOwnerKind = 'none'; ListenerOwnerProof = ''
        ListenerOwnerEvidence = ''; BindValidatedUtc = ''; ReleasedUtc = ''
        ReleaseState = 'complete'; ReleaseJournalState = 'complete'
        ReleaseBlocked = $false; ReleaseBlockReason = ''; MutexReleased = $true
        ClaimMutex = $null
        ListenerInspectionSuccess = $true; ListenerInspectionKnown = $true
        ListenerHasListeners = $false; ListenerAbsent = $true
        ListenerInspectionUtc = ''; ProcessTerminationProven = $true
        ProcessAbsent = $true; ProcessProofRequired = $false
    }
    $manifestContext.LeaseRecords = @($manifestLease)
    $manifestViewJson = & $module[0] {
        param($context)
        (Get-VerifierManifestView $context | ConvertTo-Json -Depth 12)
    } $manifestContext
    $manifestView = $manifestViewJson | ConvertFrom-Json
    $manifestServerView = $manifestView.server
    foreach ($releaseFieldCase in @(
            [pscustomobject]@{ Name = 'missing ReleaseState'; Field = 'ReleaseState'; Value = $null; Remove = $true }
            [pscustomobject]@{ Name = 'numeric ReleaseState'; Field = 'ReleaseState'; Value = 0; Remove = $false }
            [pscustomobject]@{ Name = 'missing ReleaseJournalState'; Field = 'ReleaseJournalState'; Value = $null; Remove = $true }
            [pscustomobject]@{ Name = 'numeric ReleaseJournalState'; Field = 'ReleaseJournalState'; Value = 0; Remove = $false }
        )) {
        $mutatedLease = $manifestLease | Select-Object *
        if ($releaseFieldCase.Remove) {
            [void]$mutatedLease.PSObject.Properties.Remove($releaseFieldCase.Field)
        } else {
            $mutatedLease.($releaseFieldCase.Field) = $releaseFieldCase.Value
        }
        $mutatedContext = $manifestContext | Select-Object *
        $mutatedContext.LeaseRecords = @($mutatedLease)
        $beforeMutation = $mutatedLease | ConvertTo-Json -Depth 16 -Compress
        $writerRejected = $false
        try {
            & $module[0] { param($context) Write-VerifierManifest $context } $mutatedContext
        } catch {
            $writerRejected = Test-VerifierInfrastructureError $_
        }
        Assert-GateB $writerRejected `
            "manifest writer accepted $($releaseFieldCase.Name)"
        Assert-GateB (($mutatedLease | ConvertTo-Json -Depth 16 -Compress) -eq $beforeMutation) `
            "manifest writer mutated the malformed $($releaseFieldCase.Field) fixture"
    }
    Write-Host 'PASS:manifest writer requires exact durable release and journal state fields before projection'
    foreach ($validOwner in @(
            [pscustomobject]@{ Kind = 'none'; ProcessId = 0; StartTicks = 0L; Proof = ''; Evidence = '' }
            [pscustomobject]@{ Kind = 'user-process'; ProcessId = 12345; StartTicks = 123456L; Proof = 'diagnostics-process-start-v1'; Evidence = 'system-diagnostics-process-starttime' }
            [pscustomobject]@{ Kind = 'kernel-transport'; ProcessId = 4; StartTicks = $null; Proof = 'run-owned-preview-http-sys-v1'; Evidence = 'pid-4-system-http-sys' }
        )) {
        $validLease = $manifestLease | Select-Object *
        $validLease.ListenerProcessId = $validOwner.ProcessId
        $validLease.ListenerProcessStartTicks = $validOwner.StartTicks
        $validLease.ListenerOwnerKind = $validOwner.Kind
        $validLease.ListenerOwnerProof = $validOwner.Proof
        $validLease.ListenerOwnerEvidence = $validOwner.Evidence
        $validContext = $manifestContext | Select-Object *
        $validContext.LeaseRecords = @($validLease)
        $validJson = & $module[0] {
            param($context)
            Get-VerifierManifestView $context | ConvertTo-Json -Depth 12
        } $validContext
        $validSerialized = $validJson | ConvertFrom-Json
        Assert-GateB ($validSerialized.leases[0].listenerOwnerKind -ceq $validOwner.Kind -and
            $validSerialized.leases[0].listenerOwnerProof -ceq $validOwner.Proof -and
            $validSerialized.leases[0].listenerOwnerEvidence -ceq $validOwner.Evidence) `
            "manifest view did not serialize valid $($validOwner.Kind) listener owner tuple"
    }
    foreach ($validServerOwner in @(
            [pscustomobject]@{ Kind = 'none'; ProcessId = 0; StartTicks = 0L; Proof = ''; Evidence = '' }
            [pscustomobject]@{ Kind = 'user-process'; ProcessId = 12345; StartTicks = 123456L; Proof = 'diagnostics-process-start-v1'; Evidence = 'system-diagnostics-process-starttime' }
            [pscustomobject]@{ Kind = 'kernel-transport'; ProcessId = 4; StartTicks = $null; Proof = 'run-owned-preview-http-sys-v1'; Evidence = 'pid-4-system-http-sys' }
        )) {
        $serverLease = $manifestLease | Select-Object *
        $serverLease.ListenerProcessId = $validServerOwner.ProcessId
        $serverLease.ListenerProcessStartTicks = $validServerOwner.StartTicks
        $serverLease.ListenerOwnerKind = $validServerOwner.Kind
        $serverLease.ListenerOwnerProof = $validServerOwner.Proof
        $serverLease.ListenerOwnerEvidence = $validServerOwner.Evidence
        $runServer = $manifestContext.Server | Select-Object *
        $runServer.Owner = 'run'; $runServer.CallerOwned = $false
        $runServer.State = 'run-owned-verified'; $runServer.Lease = $serverLease
        $runServer.RunId = $manifestContext.RunId; $runServer.Nonce = $manifestContext.PreviewNonce
        $runServer.StdoutLog = 'C:\gate-b\run\server\stdout.log'
        $runServer.StderrLog = 'C:\gate-b\run\server\stderr.log'
        $runServer.ProcessAbsent = $false
        $runServerContext = $manifestContext | Select-Object *
        $runServerContext.Server = $runServer
        $runServerJson = & $module[0] {
            param($context)
            Get-VerifierManifestView $context | ConvertTo-Json -Depth 12
        } $runServerContext
        $runServerView = $runServerJson | ConvertFrom-Json
        Assert-GateB ($runServerView.server.leaseListenerOwnerKind -ceq $validServerOwner.Kind -and
            $runServerView.server.leaseListenerOwnerProof -ceq $validServerOwner.Proof -and
            $runServerView.server.leaseListenerOwnerEvidence -ceq $validServerOwner.Evidence) `
            "manifest view did not serialize valid run-owned $($validServerOwner.Kind) server lease"
    }
    foreach ($ownerMutation in @(
            [pscustomobject]@{ Name = 'missing listener owner kind'; Field = 'ListenerOwnerKind'; Value = $null; Remove = $true }
            [pscustomobject]@{ Name = 'null listener owner proof'; Field = 'ListenerOwnerProof'; Value = $null; Remove = $false }
            [pscustomobject]@{ Name = 'unknown listener owner evidence'; Field = 'ListenerOwnerEvidence'; Value = 'legacy-evidence'; Remove = $false }
            [pscustomobject]@{ Name = 'mismatched listener owner tuple'; Field = 'ListenerOwnerEvidence'; Value = 'pid-4-system-http-sys'; Remove = $false }
        )) {
        $mutatedLease = $manifestLease | Select-Object *
        if ($ownerMutation.Remove) {
            [void]$mutatedLease.PSObject.Properties.Remove($ownerMutation.Field)
        } else {
            $mutatedLease.($ownerMutation.Field) = $ownerMutation.Value
        }
        $mutatedContext = $manifestContext | Select-Object *
        $mutatedContext.LeaseRecords = @($mutatedLease)
        $writerRejected = $false
        try {
            & $module[0] { param($context) Write-VerifierManifest $context } $mutatedContext
        } catch {
            $writerRejected = Test-VerifierInfrastructureError $_
        }
        Assert-GateB $writerRejected `
            "manifest writer accepted $($ownerMutation.Name) durable listener tuple"
    }
    $missingServerLeaseRoot = Join-Path ([IO.Path]::GetTempPath()) `
        ('TroubleshootJS\verify\gate-b-missing-server-lease-' + [Guid]::NewGuid().ToString('N'))
    try {
        New-Item -ItemType Directory -Path $missingServerLeaseRoot -Force `
            -ErrorAction Stop | Out-Null
        $missingServerLeaseContext = $manifestContext | Select-Object *
        $missingServerLeaseContext.RunRoot = $missingServerLeaseRoot
        $missingServerLeaseContext.RunNamespaceRoot = $missingServerLeaseRoot
        $missingServerLeaseContext.EvidenceDirectory = Join-Path $missingServerLeaseRoot 'evidence'
        $missingServerLeaseContext.EvidenceNamespaceRoot = $missingServerLeaseRoot
        $missingServerLeaseContext.PortLeaseRoot = Join-Path $missingServerLeaseRoot 'port-leases'
        $missingServerLeaseContext.ManifestPath = Join-Path $missingServerLeaseRoot 'manifest.json'
        $missingServerLease = $manifestContext.Server | Select-Object *
        $missingServerLease.Owner = 'run'
        $missingServerLease.CallerOwned = $false
        [void]$missingServerLease.PSObject.Properties.Remove('Lease')
        $missingServerLeaseContext.Server = $missingServerLease

        $viewRejected = $false
        try {
            & $module[0] { param($context) [void](Get-VerifierManifestView $context) } `
                $missingServerLeaseContext
        } catch {
            $viewRejected = Test-VerifierInfrastructureError $_
        }
        Assert-GateB $viewRejected `
            'manifest view accepted a run-owned server with missing Server.Lease'

        $writerRejected = $false
        try {
            & $module[0] { param($context) Write-VerifierManifest $context } `
                $missingServerLeaseContext
        } catch {
            $writerRejected = Test-VerifierInfrastructureError $_
        }
        $temporaryFiles = @(Get-ChildItem -LiteralPath $missingServerLeaseRoot `
            -Filter 'manifest.json.*.tmp' -File -ErrorAction SilentlyContinue)
        Assert-GateB $writerRejected `
            'manifest writer accepted a run-owned server with missing Server.Lease'
        Assert-GateB (-not (Test-Path -LiteralPath $missingServerLeaseContext.ManifestPath) -and
            $temporaryFiles.Count -eq 0) `
            'manifest writer created or renamed durable output before rejecting missing Server.Lease'
    } finally {
        if (Test-Path -LiteralPath $missingServerLeaseRoot) {
            Remove-VerifierOwnedTree (Get-VerifierFullPath ([IO.Path]::GetTempPath())) `
                (Get-VerifierFullPath $missingServerLeaseRoot)
        }
    }

    # Exercise the complete durable-context validator against an already
    # committed manifest. Every malformed proof-adjacent field must be
    # rejected before a temporary file is created or the prior manifest is
    # replaced.
    $manifestNoWriteContext = $null
    try {
        $manifestNoWriteContext = New-VerifierRunContext $repositoryRoot
        $manifestBeforeNoWrite = [IO.File]::ReadAllText($manifestNoWriteContext.ManifestPath)
        foreach ($noWriteCase in @(
                [pscustomobject]@{ Name = 'missing server command line'; Target = 'server'; Field = 'ProcessCommandLine'; Value = $null; Remove = $true }
                [pscustomobject]@{ Name = 'fractional server parent PID'; Target = 'server'; Field = 'ProcessParentProcessId'; Value = 1.5; Remove = $false }
                [pscustomobject]@{ Name = 'missing lease inspection UTC'; Target = 'lease'; Field = 'ListenerInspectionUtc'; Value = $null; Remove = $true }
                [pscustomobject]@{ Name = 'missing browser session path'; Target = 'session'; Field = 'BrowserPath'; Value = $null; Remove = $true }
            )) {
            $candidateContext = $manifestNoWriteContext | Select-Object *
            if ($noWriteCase.Target -eq 'server') {
                $candidateServer = $manifestNoWriteContext.Server | Select-Object *
                if ($noWriteCase.Remove) {
                    [void]$candidateServer.PSObject.Properties.Remove($noWriteCase.Field)
                } else {
                    $candidateServer.($noWriteCase.Field) = $noWriteCase.Value
                }
                $candidateContext.Server = $candidateServer
            } elseif ($noWriteCase.Target -eq 'lease') {
                $candidateLease = $manifestLease | Select-Object *
                $candidateLease.RunId = $candidateContext.RunId
                $candidateLease.RepositoryIdentity = $candidateContext.RepositoryIdentity
                $candidateLease.WorktreeRoot = $candidateContext.WorktreeRoot
                $candidateLease.Path = Join-Path $candidateContext.PortLeaseRoot 'manifest-canary.lease'
                [void]$candidateLease.PSObject.Properties.Remove($noWriteCase.Field)
                $candidateContext.LeaseRecords = @($candidateLease)
            } else {
                $candidateSession = [pscustomobject]@{
                    RunId = $candidateContext.RunId
                    RepositoryIdentity = $candidateContext.RepositoryIdentity
                    WorktreeRoot = $candidateContext.WorktreeRoot
                    RouteId = 'manifest-canary-route'; RouteName = 'manifest-canary'
                    Profile = ''; ProcessCommandLine = ''; TargetId = ''; ExpectedUrl = ''
                    Status = 'pending'; CleanupResult = ''; Error = ''
                    CdpPort = 40202; ProcessId = 0; ProcessStartTicks = 0
                    ProcessParentProcessId = 0; ProcessParentProcessStartTicks = 0
                    ProfileInspectionFailed = $false; ProfileProcessScanCompleted = $false
                    Lease = $null
                }
                $candidateContext.BrowserSessions = @($candidateSession)
            }
            $rejected = $false
            try {
                Write-VerifierManifest $candidateContext
            } catch {
                $rejected = Test-VerifierInfrastructureError $_
            }
            $temporaryFiles = @(Get-ChildItem -LiteralPath $candidateContext.RunRoot `
                -Filter 'manifest.json.*.tmp' -File -ErrorAction SilentlyContinue)
            Assert-GateB $rejected `
                "manifest writer accepted $($noWriteCase.Name)"
            Assert-GateB ([IO.File]::ReadAllText($manifestNoWriteContext.ManifestPath) -eq
                $manifestBeforeNoWrite -and $temporaryFiles.Count -eq 0) `
                "manifest writer wrote or replaced output for $($noWriteCase.Name)"
        }
    } finally {
        if ($null -ne $manifestNoWriteContext -and
                (Test-Path -LiteralPath $manifestNoWriteContext.RunRoot)) {
            Remove-VerifierOwnedTree (Get-VerifierFullPath ([IO.Path]::GetTempPath())) `
                (Get-VerifierFullPath $manifestNoWriteContext.RunRoot)
        }
    }
    Write-Host 'PASS:malformed durable manifest proof fields were rejected without temporary or replacement writes'

    foreach ($booleanField in @('identityVerified', 'callerOwned',
            'processIdentityKnown', 'ownershipUncertain',
            'processTerminationProven', 'processAbsent',
            'listenerInspectionProven', 'listenerAbsent')) {
        Assert-GateB ($manifestServerView.PSObject.Properties[$booleanField] -and
            $manifestServerView.PSObject.Properties[$booleanField].Value.GetType() -eq [bool]) `
            "manifest writer did not preserve exact Boolean field $booleanField"
    }
    foreach ($mutation in @(
            [pscustomobject]@{ Name = 'missing ProcessIdentityKnown'; Field = 'ProcessIdentityKnown'; Value = $null; Remove = $true }
            [pscustomobject]@{ Name = 'string ProcessIdentityKnown'; Field = 'ProcessIdentityKnown'; Value = 'false'; Remove = $false }
            [pscustomobject]@{ Name = 'missing OwnershipUncertain'; Field = 'OwnershipUncertain'; Value = $null; Remove = $true }
            [pscustomobject]@{ Name = 'string OwnershipUncertain'; Field = 'OwnershipUncertain'; Value = 'false'; Remove = $false }
        )) {
        $mutatedServer = $manifestContext.Server | Select-Object *
        if ($mutation.Remove) {
            [void]$mutatedServer.PSObject.Properties.Remove($mutation.Field)
        } else {
            $mutatedServer.($mutation.Field) = $mutation.Value
        }
        $mutatedContext = $manifestContext | Select-Object *
        $mutatedContext.Server = $mutatedServer
        $writerRejected = $false
        try {
            & $module[0] { param($context) [void](Get-VerifierManifestView $context) } $mutatedContext
        } catch {
            $writerRejected = Test-VerifierInfrastructureError $_
        }
        Assert-GateB $writerRejected `
            "manifest writer accepted $($mutation.Name) as durable proof"
    }

    $variants = New-Object Collections.ArrayList
    foreach ($variantDefinition in @(
        [pscustomobject]@{ Name = 'missing kind'; Field = 'ListenerOwnerKind'; Value = $null },
        [pscustomobject]@{ Name = 'empty kind'; Field = 'ListenerOwnerKind'; Value = '' },
        [pscustomobject]@{ Name = 'unknown kind'; Field = 'ListenerOwnerKind'; Value = 'unknown-owner' },
        [pscustomobject]@{ Name = 'missing proof'; Field = 'ListenerOwnerProof'; Value = $null },
        [pscustomobject]@{ Name = 'empty proof'; Field = 'ListenerOwnerProof'; Value = '' },
        [pscustomobject]@{ Name = 'missing evidence'; Field = 'ListenerOwnerEvidence'; Value = $null },
        [pscustomobject]@{ Name = 'empty evidence'; Field = 'ListenerOwnerEvidence'; Value = '' },
        [pscustomobject]@{ Name = 'arbitrary kind'; Field = 'ListenerOwnerKind'; Value = 'legacy-user-process' },
        [pscustomobject]@{ Name = 'arbitrary proof'; Field = 'ListenerOwnerProof'; Value = 'legacy-proof' },
        [pscustomobject]@{ Name = 'legacy proof'; Field = 'ListenerOwnerProof'; Value = 'process-id-only-v1' },
        [pscustomobject]@{ Name = 'arbitrary evidence'; Field = 'ListenerOwnerEvidence'; Value = 'legacy-evidence' },
        [pscustomobject]@{ Name = 'malformed evidence'; Field = 'ListenerOwnerEvidence'; Value = [pscustomobject]@{ Value = 'array-object' } },
        [pscustomobject]@{ Name = 'malformed port'; Field = 'Port'; Value = 'not-a-port' },
        [pscustomobject]@{ Name = 'non-positive start'; Field = 'ProcessStartTicks'; Value = 0L },
        [pscustomobject]@{ Name = 'string start'; Field = 'ProcessStartTicks'; Value = [string]$currentStart },
        [pscustomobject]@{ Name = 'floating start'; Field = 'ProcessStartTicks'; Value = [double]$currentStart },
        [pscustomobject]@{ Name = 'stale start'; Field = 'ProcessStartTicks'; Value = $currentStart + 1L },
        [pscustomobject]@{ Name = 'array pid'; Field = 'ProcessId'; Value = @($PID) },
        [pscustomobject]@{ Name = 'tuple mismatch'; Field = 'ListenerOwnerEvidence'; Value = 'pid-4-system-http-sys' }
    )) {
        $variant = $validUserListener | Select-Object *
        if ($null -eq $variantDefinition.Value) {
            [void]$variant.PSObject.Properties.Remove($variantDefinition.Field)
        } else {
            $variant.($variantDefinition.Field) = $variantDefinition.Value
        }
        [void]$variants.Add([pscustomobject]@{
            Name = $variantDefinition.Name; Listener = $variant
        })
    }
    $duplicateListener = $validUserListener | Select-Object *
    $ambiguousIdentityListener = $validUserListener | Select-Object *
    $ambiguousIdentityListener.LocalAddress = '::1'
    $conflictingStartListener = $validUserListener | Select-Object *
    $conflictingStartListener.ProcessStartTicks = $currentStart + 1L
    [void]$variants.Add([pscustomobject]@{
        Name = 'duplicate endpoint record'; Listener = $validUserListener
        Listeners = @($validUserListener, $duplicateListener)
    })
    [void]$variants.Add([pscustomobject]@{
        Name = 'duplicate identity record'; Listener = $validUserListener
        Listeners = @($validUserListener, $ambiguousIdentityListener)
    })
    [void]$variants.Add([pscustomobject]@{
        Name = 'conflicting identity start'; Listener = $validUserListener
        Listeners = @($validUserListener, $conflictingStartListener)
    })

    foreach ($variantEntry in $variants) {
        $variant = $variantEntry.Listener
        $variantListeners = if ($variantEntry.PSObject.Properties['Listeners']) {
            @($variantEntry.Listeners)
        } else { @($variant) }
        $belongs = & $module[0] {
            param($owner, $listener, $processId, $startTicks)
            Test-VerifierListenerBelongsToOwner $owner $listener $processId $startTicks $null
        } $directOwner $variant $PID $currentStart
        $belongsExpected = $variantEntry.PSObject.Properties['Listeners'] -and
            $variantListeners.Count -gt 1
        Assert-GateB ([bool]$belongs -eq [bool]$belongsExpected) `
            "live ownership consumer classified $($variantEntry.Name) listener proof incorrectly"

        $newInspectionRejected = $false
        try {
            & $module[0] {
                param($listener)
                [void](New-VerifierListenerInspection $true $true $listener `
                    'Get-NetTCPConnection')
            } $variantListeners
        } catch {
            $newInspectionRejected = Test-VerifierInfrastructureError $_
        }
        Assert-GateB $newInspectionRejected `
            "listener inspection accepted $($variantEntry.Name) listener proof"

        $inspection = [pscustomobject]@{
            Success = $true; Known = $true; HasListeners = $true
            Source = 'Get-NetTCPConnection'
            Listeners = $variantListeners
            ListenerOwnerKind = 'user-process'
            ListenerOwnerProof = 'diagnostics-process-start-v1'
            ListenerOwnerEvidence = 'system-diagnostics-process-starttime'
            Error = ''
        }
        $setRejected = $false
        try {
            & $module[0] {
                param($inspection)
                $lease = [pscustomobject]@{
                    ListenerProcessId = 0; ListenerProcessStartTicks = 0
                    ListenerOwnerKind = 'none'; ListenerOwnerProof = ''
                    ListenerOwnerEvidence = ''
                    ListenerInspectionSuccess = $false
                    ListenerInspectionKnown = $false
                    ListenerHasListeners = $null; ListenerAbsent = $null
                }
                Set-VerifierLeaseListenerInspection $lease $inspection
            } $inspection
        } catch {
            $setRejected = Test-VerifierInfrastructureError $_
        }
        Assert-GateB $setRejected `
            "lease inspection setter accepted $($variantEntry.Name) listener proof"

        $boundRejected = & $module[0] {
            param($listener, $lease, $processId, $startTicks)
            $oldFunction = (Get-Command Get-VerifierLoopbackListenerRecords `
                -CommandType Function -ErrorAction Stop).ScriptBlock
            $oldVariable = Get-Variable -Name GateBInjectedListenerBoundaryInspection `
                -Scope Script -ErrorAction SilentlyContinue
            $oldValue = if ($null -ne $oldVariable) { $oldVariable.Value } else { $null }
            try {
                $script:GateBInjectedListenerBoundaryInspection = [pscustomobject]@{
                    Success = $true; Known = $true; HasListeners = $true
                    Source = 'Get-NetTCPConnection'
                    Listeners = $listener
                    ListenerOwnerKind = 'user-process'
                    ListenerOwnerProof = 'diagnostics-process-start-v1'
                    ListenerOwnerEvidence = 'system-diagnostics-process-starttime'
                    Error = ''
                }
                Set-Item Function:\Get-VerifierLoopbackListenerRecords -Force -Value {
                    param([int]$Port, $PreviewContext, $PreviewOwner)
                    return $script:GateBInjectedListenerBoundaryInspection
                }
                $testLease = $lease | ConvertTo-Json -Depth 16 | ConvertFrom-Json
                try {
                    Confirm-VerifierPortLeaseBound $null $testLease $processId $startTicks $null
                    return $false
                } catch {
                    return (Test-VerifierInfrastructureError $_)
                }
            } finally {
                Set-Item Function:\Get-VerifierLoopbackListenerRecords -Force `
                    -Value $oldFunction
                if ($null -ne $oldVariable) {
                    $script:GateBInjectedListenerBoundaryInspection = $oldValue
                } else {
                    Remove-Variable -Name GateBInjectedListenerBoundaryInspection `
                        -Scope Script -Force -ErrorAction SilentlyContinue
                }
            }
        } $variantListeners ([pscustomobject]@{
            Status = 'held'; ClaimOwnerPid = $PID
            ClaimOwnerStartTicks = $currentStart; Port = $port
            ListenerProcessId = 0; ListenerProcessStartTicks = 0
            ListenerOwnerKind = 'none'; ListenerOwnerProof = ''
            ListenerOwnerEvidence = ''
            ListenerInspectionSuccess = $false; ListenerInspectionKnown = $false
            ListenerHasListeners = $null; ListenerAbsent = $null
        }) $PID $currentStart
        Assert-GateB ([bool]$boundRejected) `
            "bound-port consumer accepted $($variantEntry.Name) listener proof"

        $releasedRejected = & $module[0] {
            param($listener, $port)
            $oldFunction = (Get-Command Get-VerifierLoopbackListenerRecords `
                -CommandType Function -ErrorAction Stop).ScriptBlock
            $oldVariable = Get-Variable -Name GateBInjectedListenerBoundaryInspection `
                -Scope Script -ErrorAction SilentlyContinue
            $oldValue = if ($null -ne $oldVariable) { $oldVariable.Value } else { $null }
            try {
                $script:GateBInjectedListenerBoundaryInspection = [pscustomobject]@{
                    Success = $true; Known = $true; HasListeners = $true
                    Source = 'Get-NetTCPConnection'
                    Listeners = $listener
                    ListenerOwnerKind = 'user-process'
                    ListenerOwnerProof = 'diagnostics-process-start-v1'
                    ListenerOwnerEvidence = 'system-diagnostics-process-starttime'
                    Error = ''
                }
                Set-Item Function:\Get-VerifierLoopbackListenerRecords -Force -Value {
                    param([int]$Port, $PreviewContext, $PreviewOwner)
                    return $script:GateBInjectedListenerBoundaryInspection
                }
                try {
                    Confirm-VerifierReleasedListener $port
                    return $false
                } catch {
                    return (Test-VerifierInfrastructureError $_)
                }
            } finally {
                Set-Item Function:\Get-VerifierLoopbackListenerRecords -Force `
                    -Value $oldFunction
                if ($null -ne $oldVariable) {
                    $script:GateBInjectedListenerBoundaryInspection = $oldValue
                } else {
                    Remove-Variable -Name GateBInjectedListenerBoundaryInspection `
                        -Scope Script -Force -ErrorAction SilentlyContinue
                }
            }
        } $variantListeners $port
        Assert-GateB ([bool]$releasedRejected) `
            "released-listener consumer accepted $($variantEntry.Name) listener proof"
    }

    $malformedAbsenceVariants = @()
    $malformedAbsenceVariants += [pscustomobject]@{
        Name = 'absence cardinality mismatch'; Success = $true; Known = $true
        HasListeners = $true; Listeners = @(); ListenerOwnerKind = 'none'
        ListenerOwnerProof = ''; ListenerOwnerEvidence = ''
    }
    $malformedAbsenceVariants += [pscustomobject]@{
        Name = 'absence listener mismatch'; Success = $true; Known = $true
        HasListeners = $false; Listeners = @($validUserListener)
        ListenerOwnerKind = 'none'; ListenerOwnerProof = ''; ListenerOwnerEvidence = ''
    }
    $malformedAbsenceVariants += [pscustomobject]@{
        Name = 'absence legacy tuple'; Success = $true; Known = $true
        HasListeners = $false; Listeners = @(); ListenerOwnerKind = 'none'
        ListenerOwnerProof = 'legacy-proof'; ListenerOwnerEvidence = ''
    }
    $malformedAbsenceVariants += [pscustomobject]@{
        Name = 'absence scalar listener collection'; Success = $true; Known = $true
        HasListeners = $true; Listeners = $validUserListener
        ListenerOwnerKind = 'user-process'; ListenerOwnerProof = 'diagnostics-process-start-v1'
        ListenerOwnerEvidence = 'system-diagnostics-process-starttime'
    }
    $malformedAbsenceVariants += [pscustomobject]@{
        Name = 'absence string success'; Success = 'true'; Known = $true
        HasListeners = $false; Listeners = @(); ListenerOwnerKind = 'none'
        ListenerOwnerProof = ''; ListenerOwnerEvidence = ''
    }
    foreach ($absenceVariant in $malformedAbsenceVariants) {
        $schemaRejected = & $module[0] {
            param($inspection)
            -not (Test-VerifierListenerInspectionSchema $inspection)
        } ([pscustomobject]([ordered]@{
            Success = $absenceVariant.Success; Known = $absenceVariant.Known
            HasListeners = $absenceVariant.HasListeners; Listeners = $absenceVariant.Listeners
            ListenerOwnerKind = $absenceVariant.ListenerOwnerKind
            ListenerOwnerProof = $absenceVariant.ListenerOwnerProof
            ListenerOwnerEvidence = $absenceVariant.ListenerOwnerEvidence
            Source = 'Get-NetTCPConnection'; Error = ''
        }))
        Assert-GateB ([bool]$schemaRejected) `
            "canonical inspection validator accepted $($absenceVariant.Name)"

        $absenceInspection = [pscustomobject]([ordered]@{
            Success = $absenceVariant.Success; Known = $absenceVariant.Known
            HasListeners = $absenceVariant.HasListeners; Listeners = $absenceVariant.Listeners
            ListenerOwnerKind = $absenceVariant.ListenerOwnerKind
            ListenerOwnerProof = $absenceVariant.ListenerOwnerProof
            ListenerOwnerEvidence = $absenceVariant.ListenerOwnerEvidence
            Source = 'Get-NetTCPConnection'; Error = ''
        })
        $absenceLease = [pscustomobject]@{
            Status = 'held'; ClaimOwnerPid = $PID; ClaimOwnerStartTicks = $currentStart
            Port = $port; ListenerProcessId = 0; ListenerProcessStartTicks = 0
            ListenerOwnerKind = 'none'; ListenerOwnerProof = ''; ListenerOwnerEvidence = ''
            ListenerInspectionSuccess = $false; ListenerInspectionKnown = $false
            ListenerHasListeners = $null; ListenerAbsent = $null
        }
        $setAbsenceRejected = $false
        try { & $module[0] { param($l,$i) Set-VerifierLeaseListenerInspection $l $i } $absenceLease $absenceInspection }
        catch { $setAbsenceRejected = Test-VerifierInfrastructureError $_ }
        Assert-GateB $setAbsenceRejected "setter accepted $($absenceVariant.Name)"

        foreach ($consumer in @('bound', 'released')) {
            $consumerRejected = & $module[0] {
                param($inspection, $consumerName, $boundLease, $processId, $startTicks, $boundPort)
                $oldFunction = (Get-Command Get-VerifierLoopbackListenerRecords `
                    -CommandType Function -ErrorAction Stop).ScriptBlock
                try {
                    $script:GateBInjectedListenerBoundaryInspection = $inspection
                    Set-Item Function:\Get-VerifierLoopbackListenerRecords -Force -Value {
                        param([int]$Port, $PreviewContext, $PreviewOwner)
                        return $script:GateBInjectedListenerBoundaryInspection
                    }
                    if ($consumerName -eq 'bound') {
                        Confirm-VerifierPortLeaseBound $null $boundLease $processId $startTicks $null
                    } else {
                        Confirm-VerifierReleasedListener $boundPort
                    }
                    return $false
                } catch { return (Test-VerifierInfrastructureError $_) }
                finally {
                    Set-Item Function:\Get-VerifierLoopbackListenerRecords -Force -Value $oldFunction
                    Remove-Variable -Name GateBInjectedListenerBoundaryInspection `
                        -Scope Script -Force -ErrorAction SilentlyContinue
                }
            } $absenceInspection $consumer $absenceLease $PID $currentStart $port
            Assert-GateB ([bool]$consumerRejected) `
                "$consumer consumer accepted $($absenceVariant.Name)"
        }
    }
    $newFailureCanary = $false
    try {
        & $module[0] { [void](New-VerifierListenerInspection $false $true @() `
            'Get-NetTCPConnection' '') }
    } catch { $newFailureCanary = Test-VerifierInfrastructureError $_ }
    Assert-GateB $newFailureCanary 'inspection constructor accepted a failed/mutated absence result'
    Write-Host 'PASS:live listener consumers require exact user-process/kernel tuples; malformed tuples, duplicates, ambiguous identities, and mutated absence fail typed infrastructure'
}

function Invoke-GateBDescendantCleanupCanary() {
    $canaryRoot = Join-Path ([IO.Path]::GetTempPath()) `
        ('TroubleshootJS\gate-b-descendant-cleanup-' + [Guid]::NewGuid().ToString('N'))
    $context = $null
    $browserSessionRecord = $null
    $rootProcess = $null
    $rootIdentity = $null
    $descendantPids = @()
    $evidencePath = $null
    $junctionPath = Join-Path $canaryRoot 'owned-reparse-junction'
    $outsidePath = Join-Path $canaryRoot 'reparse-target'
    $primaryFailure = $null
    $cleanupErrors = New-Object Collections.ArrayList
    $canarySucceeded = $false
    try {
        New-Item -ItemType Directory -Path $canaryRoot -Force -ErrorAction Stop | Out-Null
        New-Item -ItemType Directory -Path $outsidePath -Force -ErrorAction Stop | Out-Null
        try {
            New-Item -ItemType Junction -Path $junctionPath -Target $outsidePath `
                -ErrorAction Stop | Out-Null
        } catch {
            Throw-GateBInfrastructure ('reparse-point canary could not create its test junction; ' +
                'the canary is infrastructure-blocked and retained no unsafe deletion target: ' +
                (Get-VerifierErrorMessage $_))
        }
        $reparseRejected = $false
        try {
            [void](Test-VerifierChildPath $canaryRoot (Join-Path $junctionPath 'payload.txt'))
        } catch {
            $reparseRejected = Test-VerifierInfrastructureError $_
        }
        Assert-GateB $reparseRejected `
            'physical child-path validation accepted a junction/reparse path'

        try {
            [void](Get-CimInstance Win32_Process -ErrorAction Stop)
        } catch {
            Throw-GateBInfrastructure ('end-to-end descendant cleanup requires complete Win32_Process ' +
                'inspection; this host returned: ' + (Get-VerifierErrorMessage $_))
        }
        $wscriptPath = (Get-Command wscript.exe -ErrorAction Stop).Source
        # Use the Windows Script Host for this deterministic end-to-end
        # process fixture. It is a real WMI-visible executable that can spawn
        # a same-executable helper without creating a console-host child that
        # is unrelated to browser ownership. The separate real-Edge canary
        # below remains the browser-specific ownership proof.
        $browserPath = $wscriptPath
        $context = New-VerifierRunContext $repositoryRoot $canaryRoot
        $browserSessionRecord = New-VerifierBrowserLease $context 'descendant-cleanup-e2e' $browserPath
        $profile = [string]$browserSessionRecord.Profile
        $runId = [string]$context.RunId
        $repositoryIdentity = [string]$context.RepositoryIdentity
        $port = [int]$browserSessionRecord.CdpPort
        $evidencePath = Join-Path $context.EvidenceDirectory 'descendant-cleanup-proof.txt'
        [IO.File]::WriteAllText($evidencePath,
            ('owned descendant cleanup proof for ' + $runId),
            [Text.UTF8Encoding]::new($false))
        Register-VerifierEvidenceArtifact $context $evidencePath
        $childScript = Join-Path $canaryRoot 'different-name-helper.ps1'
        $rootScript = Join-Path $canaryRoot 'browser-root-launcher.ps1'
        $childScript = [IO.Path]::ChangeExtension($childScript, '.vbs')
        $rootScript = [IO.Path]::ChangeExtension($rootScript, '.vbs')
        $childText = [string]::Join([Environment]::NewLine, @(
            'Option Explicit'
            'WScript.Sleep 120000'
        ))
        $rootText = [string]::Join([Environment]::NewLine, @(
            'Option Explicit'
            'Dim shell, commandLine'
            'Function Q(value)'
            '    Q = Chr(34) & Replace(CStr(value), Chr(34), Chr(34) & Chr(34)) & Chr(34)'
            'End Function'
            'Set shell = CreateObject("WScript.Shell")'
            'commandLine = Q(WScript.FullName) & " //B " & Q(WScript.Arguments(0)) & " " & Q(WScript.Arguments(1))'
            'shell.Run commandLine, 0, False'
            'WScript.Sleep 120000'
        ))
        [IO.File]::WriteAllText($childScript, $childText, [Text.UTF8Encoding]::new($false))
        [IO.File]::WriteAllText($rootScript, $rootText, [Text.UTF8Encoding]::new($false))
        Assert-VerifierNoReparseAncestors $canaryRoot
        if (-not (Test-VerifierPhysicalChildPath $canaryRoot $childScript) -or
                -not (Test-VerifierPhysicalChildPath $canaryRoot $rootScript)) {
            Throw-GateBInfrastructure 'descendant cleanup helper scripts escaped the physical canary namespace'
        }
        $rootArguments = @(
            '//B', $rootScript, $childScript, $profile, $runId,
            $repositoryIdentity, [string]$port,
            # Keep the positional fixture arguments above for the launcher,
            # and also put the exact browser identity markers on the root
            # command line. Complete-VerifierBrowserSession must prove the
            # root independently before it can enumerate or stop helpers.
            '--user-data-dir', $profile,
            '--tsj-verifier-run', $runId,
            '--tsj-verifier-worktree', $repositoryIdentity,
            '--remote-debugging-port', [string]$port)
        $rootProcess = Start-VerifierProcess $wscriptPath $rootArguments
        $rootStart = [long](Get-VerifierProcessStartTicks $rootProcess)
        $rootIdentity = Get-VerifierCurrentProcessIdentity ([int]$rootProcess.Id) $rootStart `
            0 '' '' 0 $runId '' 0 $browserPath
        $browserSessionRecord.ProcessId = [int]$rootProcess.Id
        $browserSessionRecord.ProcessStartTicks = $rootStart
        $browserSessionRecord.ProcessParentProcessId = [int]$rootIdentity.Record.ParentProcessId
        $browserSessionRecord.ProcessParentProcessStartTicks = [long]$rootIdentity.Record.ParentProcessStartTicks
        $browserSessionRecord.ProcessCommandLine = [string]$rootIdentity.Record.CommandLine
        $browserSessionRecord.Status = 'started'
        Write-VerifierManifest $context
        Start-Sleep -Milliseconds 700
        $snapshot = @(Get-VerifierBrowserOwnershipSnapshot $browserPath $profile $runId `
            $repositoryIdentity $port)
        $candidateRecords = @(Get-VerifierDescendantCandidateRecords $browserSessionRecord $snapshot $true)
        $descendantPids = @($candidateRecords | ForEach-Object { [int]$_.ProcessId } |
            Sort-Object -Unique)
        $markerlessHelperRecords = @($candidateRecords | Where-Object {
            [int]$_.ParentProcessId -eq [int]$browserSessionRecord.ProcessId -and
            ([string]$_.Name).Equals([IO.Path]::GetFileName($browserPath),
                [StringComparison]::OrdinalIgnoreCase) -and
            -not (Test-VerifierCommandLineSwitch ([string]$_.CommandLine) '--user-data-dir' $profile) -and
            -not (Test-VerifierCommandLineSwitch ([string]$_.CommandLine) '--tsj-verifier-run' $runId) -and
            -not (Test-VerifierCommandLineSwitch ([string]$_.CommandLine) '--tsj-verifier-worktree' $repositoryIdentity) -and
            -not (Test-VerifierCommandLineSwitch ([string]$_.CommandLine) '--remote-debugging-port' ([string]$port))
        })
        Assert-GateB ($markerlessHelperRecords.Count -ge 1) `
            'end-to-end helper did not exercise markerless same-executable ancestry ownership'

        # A same-PID/root record with a changed parent must block cleanup before
        # any process is stopped. Restore the exact launch parent only after the
        # negative proof, then exercise the full descendant stop/profile/lease
        # cleanup path.
        $browserSessionRecord.ProcessParentProcessId = [int]$rootIdentity.Record.ParentProcessId + 1000
        Write-VerifierManifest $context
        $reparentRejected = $false
        try { Complete-VerifierBrowserSession $context $browserSessionRecord } catch {
            $reparentRejected = Test-VerifierInfrastructureError $_
        }
        Assert-GateB $reparentRejected 'reparented browser root was treated as owned and stoppable'
        $rootProcess.Refresh()
        Assert-GateB (-not [bool]$rootProcess.HasExited) 'reparent mismatch cleanup stopped the browser root'
        $browserSessionRecord.ProcessParentProcessId = [int]$rootIdentity.Record.ParentProcessId
        $browserSessionRecord.ProcessParentProcessStartTicks = [long]$rootIdentity.Record.ParentProcessStartTicks
        $browserSessionRecord.Status = 'started'
        $browserSessionRecord.CleanupResult = 'pending'
        $browserSessionRecord.Error = ''
        # The intentional reparent negative path durably blocks lease release
        # when it rejects cleanup. Restore the exact fixture's verified owner
        # state before exercising the independent positive root-alive cleanup;
        # production cleanup never clears a blocker without a new proof.
        $browserSessionRecord.Lease.ReleaseBlocked = $false
        $browserSessionRecord.Lease.ReleaseBlockReason = ''
        Write-VerifierManifest $context
        Complete-VerifierBrowserSession $context $browserSessionRecord
        if ($null -eq $rootProcess) {
            Throw-GateBInfrastructure 'end-to-end descendant cleanup did not retain the launched browser-root process object'
        }
        $rootProcess.Refresh()
        Assert-GateB ([bool]$rootProcess.HasExited) `
            'end-to-end descendant cleanup returned before the browser root was proven terminated'
        foreach ($descendantPid in @($descendantPids + [int]$rootProcess.Id | Sort-Object -Unique)) {
            $remaining = Get-VerifierCurrentProcessRecordById ([int]$descendantPid)
            Assert-GateB ($null -eq $remaining) `
                "end-to-end descendant cleanup left PID $descendantPid present after exact termination"
        }
        Assert-GateB (-not (Test-Path -LiteralPath $profile)) `
            'end-to-end descendant cleanup left the owned profile behind'
        Assert-GateB (-not (Test-Path -LiteralPath $browserSessionRecord.Lease.Path)) `
            'end-to-end descendant cleanup left the owned claim behind'
        Assert-GateB ((Test-VerifierLeaseNeedsCleanup $browserSessionRecord.Lease) -eq $false) `
            'end-to-end descendant cleanup left the lease active'
        Assert-GateBContextResourcesReleased $context
        Assert-GateB (Test-Path -LiteralPath $evidencePath -PathType Leaf) `
            'end-to-end descendant cleanup did not retain its exact evidence artifact before run-root removal'
        $canarySucceeded = $true
    } catch {
        $primaryFailure = $_
    } finally {
        if ($null -ne $context) {
            # A canary failure must not leave a known helper running merely
            # because the assertion path failed. Restore the recorded root
            # parent only when the exact launch identity is available; any
            # mismatch remains retained evidence and is never force-killed.
            try {
                if (-not $canarySucceeded -and $null -ne $browserSessionRecord -and
                        $null -ne $rootIdentity) {
                    $browserSessionRecord.ProcessParentProcessId = [int]$rootIdentity.Record.ParentProcessId
                    $browserSessionRecord.ProcessParentProcessStartTicks = [long]$rootIdentity.Record.ParentProcessStartTicks
                    $browserSessionRecord.Status = 'started'
                    $browserSessionRecord.CleanupResult = 'pending'
                    $browserSessionRecord.Error = ''
                    Write-VerifierManifest $context
                }
                $cleanupAttempt = Complete-VerifierRun $context
                if ($null -eq $cleanupAttempt -or -not [bool]$cleanupAttempt.Success) {
                    $detail = if ($cleanupAttempt) { @($cleanupAttempt.Errors) -join '; ' } else {
                        'Complete-VerifierRun returned no result.'
                    }
                    [void]$cleanupErrors.Add($detail)
                }
            } catch {
                [void]$cleanupErrors.Add((Get-VerifierErrorMessage $_))
            }
        }
        if ($null -ne $rootProcess) {
            # The Process object is a retained ownership handle, not a
            # termination shortcut.  Synchronize and dispose it only after
            # the exact browser-session cleanup attempt; if it is still
            # alive, retain the canary root and surface infrastructure rather
            # than deleting evidence or guessing at a PID.
            try {
                if (-not $rootProcess.WaitForExit(5000)) {
                    [void]$cleanupErrors.Add('end-to-end descendant root did not complete its final WaitForExit')
                }
                $rootProcess.Refresh()
                if (-not [bool]$rootProcess.HasExited) {
                    [void]$cleanupErrors.Add('end-to-end descendant root remained alive after cleanup')
                } elseif ($null -ne (Get-VerifierCurrentProcessRecordById ([int]$rootProcess.Id))) {
                    [void]$cleanupErrors.Add('end-to-end descendant root still had a current process identity after cleanup')
                }
            } catch {
                [void]$cleanupErrors.Add(('end-to-end descendant root termination proof: ' +
                    (Get-VerifierErrorMessage $_)))
            }
            try { $rootProcess.Dispose() } catch {
                [void]$cleanupErrors.Add(('end-to-end descendant root handle disposal: ' +
                    (Get-VerifierErrorMessage $_)))
            }
        }
        # The junction is removed only as the exact link object; the canary
        # never recursively follows it. If cleanup is uncertain, preserve the
        # canary root and any run evidence instead of deleting it.
        if ($junctionPath -and (Test-Path -LiteralPath $junctionPath)) {
            try {
                $junctionItem = Get-Item -LiteralPath $junctionPath -Force -ErrorAction Stop
                if ($junctionItem.Attributes -band [IO.FileAttributes]::ReparsePoint) {
                    Remove-Item -LiteralPath $junctionPath -Force -ErrorAction Stop
                } else {
                    [void]$cleanupErrors.Add('reparse canary path was no longer the expected junction')
                }
            } catch {
                [void]$cleanupErrors.Add((Get-VerifierErrorMessage $_))
            }
        }
        if ($canarySucceeded -and $context) {
            try { Remove-GateBCanaryRoots $canaryRoot @($context) } catch {
                [void]$cleanupErrors.Add((Get-VerifierErrorMessage $_))
            }
            if ($cleanupErrors.Count -eq 0 -and (Test-Path -LiteralPath $canaryRoot)) {
                [void]$cleanupErrors.Add('end-to-end descendant cleanup left its canary/run-root evidence namespace behind')
            }
            if ($cleanupErrors.Count -eq 0 -and $context -and
                    (Test-Path -LiteralPath $context.RunRoot)) {
                [void]$cleanupErrors.Add('end-to-end descendant cleanup left its isolated run root behind')
            }
        }
        if ($null -ne $primaryFailure -and $cleanupErrors.Count -gt 0) {
            Throw-GateBInfrastructure ('descendant/reparent/reparse canary failed and cleanup was not proven; ' +
                'evidence was retained at ' + $canaryRoot + ': ' +
                (Get-VerifierErrorMessage $primaryFailure) + '; ' + ($cleanupErrors -join '; '))
        }
        if ($cleanupErrors.Count -gt 0) {
            Throw-GateBInfrastructure ('descendant/reparent/reparse canary cleanup was not proven; evidence was retained at ' +
                $canaryRoot + ': ' + ($cleanupErrors -join '; '))
        }
    }
    if ($null -ne $primaryFailure) {
        if (Test-VerifierInfrastructureError $primaryFailure) { throw $primaryFailure }
        Throw-GateBInfrastructure ('descendant/reparent/reparse canary failed: ' +
            (Get-VerifierErrorMessage $primaryFailure))
    }
    if ($canarySucceeded) {
        Write-Host 'PASS:end-to-end descendant cleanup, root-parent identity, and physical reparse safety canary'
    }
}

function Invoke-GateBLateMarkerlessCleanupCanary() {
    $canaryRoot = Join-Path ([IO.Path]::GetTempPath()) `
        ('TroubleshootJS\gate-b-late-markerless-' + [Guid]::NewGuid().ToString('N'))
    $context = $null
    $session = $null
    $rootProcess = $null
    $rootIdentity = $null
    $browserPath = ''
    $profile = ''
    $runId = ''
    $repositoryIdentity = ''
    $port = 0
    $helperScript = ''
    $rootScript = ''
    $signalPath = ''
    $helperReadyPath = ''
    $evidencePath = ''
    $primaryFailure = $null
    $cleanupErrors = New-Object Collections.ArrayList
    $cleanupSucceeded = $false
    $retainedOutcome = $false
    $recoveryComplete = $false
    try {
        New-Item -ItemType Directory -Path $canaryRoot -Force -ErrorAction Stop | Out-Null
        try {
            [void](Get-CimInstance Win32_Process -ErrorAction Stop)
        } catch {
            Throw-GateBInfrastructure ('late-markerless cleanup canary requires complete Win32_Process ' +
                'inspection; this host returned: ' + (Get-VerifierErrorMessage $_))
        }
        $browserPath = (Get-Command wscript.exe -ErrorAction Stop).Source
        $context = New-VerifierRunContext $repositoryRoot $canaryRoot
        $session = New-VerifierBrowserLease $context 'late-markerless-cleanup' $browserPath
        $profile = [string]$session.Profile
        $runId = [string]$context.RunId
        $repositoryIdentity = [string]$context.RepositoryIdentity
        $port = [int]$session.CdpPort
        $evidencePath = Join-Path $context.EvidenceDirectory 'late-markerless-retained-proof.txt'
        [IO.File]::WriteAllText($evidencePath,
            ('late markerless cleanup proof for ' + $runId),
            [Text.UTF8Encoding]::new($false))
        Register-VerifierEvidenceArtifact $context $evidencePath

        $helperScript = [IO.Path]::ChangeExtension(
            (Join-Path $canaryRoot 'late-markerless-helper.ps1'), '.vbs')
        $rootScript = [IO.Path]::ChangeExtension(
            (Join-Path $canaryRoot 'late-markerless-root.ps1'), '.vbs')
        $signalPath = Join-Path $context.RunRoot 'late-helper-trigger.signal'
        $helperReadyPath = Join-Path $context.RunRoot 'late-helper.started'
        $helperText = [string]::Join([Environment]::NewLine, @(
            'Option Explicit'
            'Dim fileSystem, markerFile'
            'Set fileSystem = CreateObject("Scripting.FileSystemObject")'
            'Set markerFile = fileSystem.CreateTextFile(CStr(WScript.Arguments(0)), True)'
            'markerFile.WriteLine "late markerless helper started"'
            'markerFile.Close'
            # No profile or verifier switches are passed to this child. Its
            # only ownership proof is current same-executable ancestry.
            'WScript.Sleep 20000'
        ))
        $rootText = [string]::Join([Environment]::NewLine, @(
            'Option Explicit'
            'Dim shell, fileSystem, commandLine'
            'Function Q(value)'
            '    Q = Chr(34) & Replace(CStr(value), Chr(34), Chr(34) & Chr(34)) & Chr(34)'
            'End Function'
            'Set shell = CreateObject("WScript.Shell")'
            'Set fileSystem = CreateObject("Scripting.FileSystemObject")'
            'Do Until fileSystem.FileExists(CStr(WScript.Arguments(1)))'
            '    WScript.Sleep 50'
            'Loop'
            # The helper is created only after the cleanup has captured its
            # first complete empty graph. The root then remains alive long
            # enough for the fixed-point drain to discover and stop it.
            'commandLine = Q(WScript.FullName) & " //B " & Q(WScript.Arguments(0)) & " " & Q(WScript.Arguments(2))'
            'shell.Run commandLine, 0, False'
            'WScript.Sleep 120000'
        ))
        [IO.File]::WriteAllText($helperScript, $helperText,
            [Text.UTF8Encoding]::new($false))
        [IO.File]::WriteAllText($rootScript, $rootText,
            [Text.UTF8Encoding]::new($false))
        Assert-VerifierNoReparseAncestors $canaryRoot
        [void](Assert-VerifierPhysicalOwnedPath $context.RunRoot $signalPath)
        [void](Assert-VerifierPhysicalOwnedPath $context.RunRoot $helperReadyPath)
        if (-not (Test-VerifierPhysicalChildPath $canaryRoot $helperScript) -or
                -not (Test-VerifierPhysicalChildPath $canaryRoot $rootScript)) {
            Throw-GateBInfrastructure 'late-markerless canary scripts escaped the physical fixture namespace'
        }

        $rootArguments = @(
            '//B', $rootScript, $helperScript, $signalPath, $helperReadyPath,
            '--user-data-dir', $profile,
            '--tsj-verifier-run', $runId,
            '--tsj-verifier-worktree', $repositoryIdentity,
            '--remote-debugging-port', [string]$port)
        $rootProcess = Start-VerifierProcess $browserPath $rootArguments
        # Preserve the exact launch handle/PID before any fallible identity
        # query. Cleanup must never fall back to a bare PID or profile guess.
        $session.Runtime.Browser = $rootProcess
        $session.ProcessId = [int]$rootProcess.Id
        $session.Status = 'started'
        Write-VerifierManifest $context
        $rootStartTicks = Get-VerifierProcessStartTicks $rootProcess
        $rootIdentity = Get-VerifierCurrentProcessIdentity $session.ProcessId `
            $rootStartTicks 0 '' '' 0 $runId '' 0 $browserPath
        $session.ProcessStartTicks = $rootStartTicks
        $session.ProcessParentProcessId = [int]$rootIdentity.Record.ParentProcessId
        $session.ProcessParentProcessStartTicks = [long]$rootIdentity.Record.ParentProcessStartTicks
        $session.ProcessCommandLine = [string]$rootIdentity.Record.CommandLine
        $session.Status = 'started'
        Write-VerifierManifest $context

        # This hook is inert in ordinary runs. It asks the shared production
        # drain to signal the fixture only after it captured an empty graph.
        $context.TestHooks.BrowserDrainAfterInitialGraphSignalPath = $signalPath
        $context.TestHooks.BrowserDrainAfterInitialGraphReadyPath = $helperReadyPath
        $context.TestHooks.BrowserDrainAfterInitialGraphSignalWritten = $false
        Complete-VerifierBrowserSession $context $session
        Assert-GateB ([bool]$context.TestHooks.BrowserDrainAfterInitialGraphSignalWritten) `
            'late-markerless cleanup did not capture its initial empty graph before signaling the helper'
        Assert-GateB (Test-Path -LiteralPath $helperReadyPath -PathType Leaf) `
            'late-markerless helper did not start after the initial graph capture'
        $rootProcess.Refresh()
        Assert-GateB ([bool]$rootProcess.HasExited) `
            'late-markerless fixed-point cleanup returned before the root terminated'
        Assert-GateB ($null -eq (Get-VerifierCurrentProcessRecordById ([int]$rootProcess.Id))) `
            'late-markerless fixed-point cleanup left the root in the current process view'
        $lateHelperRecords = @(Get-CimInstance Win32_Process -ErrorAction Stop |
            Where-Object {
                $_.Name -and ([string]$_.Name).Equals(
                    [IO.Path]::GetFileName($browserPath), [StringComparison]::OrdinalIgnoreCase) -and
                $_.CommandLine -and
                (Test-VerifierCommandLineCanonicalPathToken ([string]$_.CommandLine) $helperScript)
            })
        Assert-GateB ($lateHelperRecords.Count -eq 0) `
            'late-markerless helper survived after root cleanup'
        Assert-GateB (-not (Test-Path -LiteralPath $profile)) `
            'late-markerless cleanup deleted neither its profile nor the complete graph proof'
        Assert-GateB (-not (Test-Path -LiteralPath $session.Lease.Path)) `
            'late-markerless cleanup left its released claim behind'
        $runCleanup = Complete-VerifierRun $context
        Assert-GateB ($null -ne $runCleanup -and [bool]$runCleanup.Success) `
            'late-markerless fixed-point run cleanup did not complete'
        Assert-GateBContextResourcesReleased $context
        $cleanupSucceeded = $true
    } catch {
        $primaryFailure = $_
        # A bounded fixed-point implementation may conservatively retain the
        # exact run when the helper cannot be revalidated. That is safe only
        # if the helper is demonstrably alive and every owned resource remains
        # retained; a deleted claim/profile with a surviving helper is a hard
        # canary failure.
        try {
            $lateHelperRecords = @()
            if ($helperScript -and $browserPath) {
                $lateHelperRecords = @(Get-CimInstance Win32_Process -ErrorAction Stop |
                    Where-Object {
                        $_.Name -and ([string]$_.Name).Equals(
                            [IO.Path]::GetFileName($browserPath), [StringComparison]::OrdinalIgnoreCase) -and
                        $_.CommandLine -and
                        (Test-VerifierCommandLineCanonicalPathToken ([string]$_.CommandLine) $helperScript)
                    })
            }
            $retainedOutcome = ($lateHelperRecords.Count -gt 0 -and
                $null -ne $context -and $null -ne $session -and
                [string]$session.CleanupResult -eq 'infrastructure-failure' -and
                (Test-GateBExactBooleanProperty $session.Lease 'ReleaseBlocked' $true) -and
                (Test-Path -LiteralPath $profile -PathType Container) -and
                (Test-Path -LiteralPath $session.Lease.Path -PathType Leaf) -and
                (Test-Path -LiteralPath $evidencePath -PathType Leaf) -and
                (Test-Path -LiteralPath $context.RunRoot -PathType Container))
        } catch {
            [void]$cleanupErrors.Add(('late-markerless retained-resource inspection: ' +
                (Get-VerifierErrorMessage $_)))
        }
    } finally {
        # Give a started helper its bounded natural-exit window. It is never
        # broad-killed; if it remains alive, exact evidence is retained.
        if ($helperScript -and $browserPath) {
            try {
                $helperDeadline = [DateTime]::UtcNow.AddSeconds(30)
                do {
                    $lateHelperRecords = @(Get-CimInstance Win32_Process -ErrorAction Stop |
                        Where-Object {
                            $_.Name -and ([string]$_.Name).Equals(
                                [IO.Path]::GetFileName($browserPath), [StringComparison]::OrdinalIgnoreCase) -and
                            $_.CommandLine -and
                            (Test-VerifierCommandLineCanonicalPathToken ([string]$_.CommandLine) $helperScript)
                        })
                    if ($lateHelperRecords.Count -eq 0) { break }
                    if ([DateTime]::UtcNow -ge $helperDeadline) {
                        [void]$cleanupErrors.Add('late-markerless helper remained alive after its bounded natural-exit window')
                        break
                    }
                    Start-Sleep -Milliseconds 100
                } while ($true)
            } catch {
                [void]$cleanupErrors.Add(('late-markerless helper absence proof: ' +
                    (Get-VerifierErrorMessage $_)))
            }
        }
        if ($null -ne $context -and -not $cleanupSucceeded -and -not $retainedOutcome) {
            # A setup/assertion failure may leave the root alive. Give the
            # normal exact cleanup path one bounded attempt; it retains the
            # namespace if root/descendant identity cannot be proven.
            try {
                $cleanupAttempt = Complete-VerifierRun $context
                if ($null -eq $cleanupAttempt -or -not [bool]$cleanupAttempt.Success) {
                    $detail = if ($cleanupAttempt) { @($cleanupAttempt.Errors) -join '; ' } else {
                        'Complete-VerifierRun returned no result during late-markerless fixture cleanup.'
                    }
                    [void]$cleanupErrors.Add($detail)
                }
            } catch {
                [void]$cleanupErrors.Add(('late-markerless fixture cleanup: ' +
                    (Get-VerifierErrorMessage $_)))
            }
        }
        if ($null -ne $rootProcess) {
            try {
                if (-not $rootProcess.WaitForExit(5000)) {
                    [void]$cleanupErrors.Add('late-markerless root did not complete its bounded final wait')
                }
                $rootProcess.Refresh()
                if (-not [bool]$rootProcess.HasExited) {
                    [void]$cleanupErrors.Add('late-markerless root remained alive; retaining exact evidence')
                } elseif ($null -ne (Get-VerifierCurrentProcessRecordById ([int]$rootProcess.Id))) {
                    [void]$cleanupErrors.Add('late-markerless root remained in the current process view')
                }
            } catch {
                [void]$cleanupErrors.Add(('late-markerless root termination/absence proof: ' +
                    (Get-VerifierErrorMessage $_)))
            }
            try { $rootProcess.Dispose() } catch {
                [void]$cleanupErrors.Add(('late-markerless root handle disposal: ' +
                    (Get-VerifierErrorMessage $_)))
            }
        }

        if ($retainedOutcome -and $null -ne $context -and $null -ne $session -and
                $cleanupErrors.Count -eq 0) {
            # This is test-fixture recovery only. Production root-gone cleanup
            # never clears its release blocker merely because a helper later
            # exits; this path is allowed to recover its own exact fixture once
            # all process proof is complete.
            try {
                Assert-VerifierBrowserProfileIsQuiescent $profile $session.BrowserPath `
                    $runId $repositoryIdentity $port
                $session.Lease.ReleaseBlocked = $false
                $session.Lease.ReleaseBlockReason = ''
                Release-VerifierPortLease $context $session.Lease
                if (Test-Path -LiteralPath $profile) {
                    [void](Assert-VerifierPhysicalOwnedPath $context.RunRoot $profile -ValidateTree)
                    Remove-VerifierOwnedTree $context.RunRoot $profile
                }
                if (Test-Path -LiteralPath $profile) {
                    Throw-GateBInfrastructure 'late-markerless retained-resource recovery left its exact profile behind'
                }
                $session.Status = 'cleaned'
                $session.CleanupResult = 'complete'
                $session.Error = ''
                Write-VerifierManifest $context
                $recovered = Complete-VerifierRun $context
                if ($null -eq $recovered -or -not [bool]$recovered.Success) {
                    $detail = if ($recovered) { @($recovered.Errors) -join '; ' } else {
                        'Complete-VerifierRun returned no result during late-markerless retained recovery.'
                    }
                    Throw-GateBInfrastructure $detail
                }
                Assert-GateBContextResourcesReleased $context
                Remove-GateBCanaryRoots $canaryRoot @($context)
                if (Test-Path -LiteralPath $canaryRoot) {
                    Throw-GateBInfrastructure 'late-markerless retained recovery left its exact evidence namespace behind'
                }
                $recoveryComplete = $true
            } catch {
                [void]$cleanupErrors.Add(('late-markerless retained-resource exact recovery: ' +
                    (Get-VerifierErrorMessage $_)))
            }
        } elseif ($cleanupSucceeded -and $null -ne $context -and
                $cleanupErrors.Count -eq 0) {
            try {
                Remove-GateBCanaryRoots $canaryRoot @($context)
                if (Test-Path -LiteralPath $canaryRoot) {
                    Throw-GateBInfrastructure 'late-markerless successful cleanup left its exact evidence namespace behind'
                }
                $recoveryComplete = $true
            } catch {
                [void]$cleanupErrors.Add(('late-markerless successful cleanup evidence removal: ' +
                    (Get-VerifierErrorMessage $_)))
            }
        }
    }
    if ($cleanupErrors.Count -gt 0) {
        $primaryDetail = if ($null -ne $primaryFailure) {
            'primary=' + (Get-VerifierErrorMessage $primaryFailure) + '; '
        } else { '' }
        Throw-GateBInfrastructure ('late-markerless cleanup canary could not prove exact cleanup; ' +
            'evidence was retained at ' + $canaryRoot + ': ' + $primaryDetail +
            ($cleanupErrors -join '; '))
    }
    if ($null -ne $primaryFailure -and -not $retainedOutcome) {
        if (Test-VerifierInfrastructureError $primaryFailure) { throw $primaryFailure }
        Throw-GateBInfrastructure ('late-markerless cleanup canary failed: ' +
            (Get-VerifierErrorMessage $primaryFailure))
    }
    if (-not $recoveryComplete) {
        Throw-GateBInfrastructure 'late-markerless cleanup canary did not complete exact cleanup or retained-resource recovery'
    }
    if ($retainedOutcome) {
        Write-Host 'PASS:late-markerless helper created after the initial graph was retained with profile/claim/evidence on infrastructure 2 before exact fixture recovery'
    } else {
        Write-Host 'PASS:late-markerless helper created after the initial graph was discovered by fixed-point cleanup and exact resources were released'
    }
}

function Invoke-GateBRootGoneCleanupCanary() {
    $canaryRoot = Join-Path ([IO.Path]::GetTempPath()) `
        ('TroubleshootJS\gate-b-root-gone-' + [Guid]::NewGuid().ToString('N'))
    $context = $null
    $session = $null
    $rootProcess = $null
    $helperProcess = $null
    $rootIdentity = $null
    $helperIdentity = $null
    $helperIdentityRecord = $null
    $profile = ''
    $runId = ''
    $repositoryIdentity = ''
    $port = 0
    $rootStartTicks = 0L
    $helperStartTicks = 0L
    $helperScript = ''
    $rootScript = ''
    $rootStopPath = ''
    $evidencePath = ''
    $rootGoneObserved = $false
    $rootGoneRejected = $false
    $canarySucceeded = $false
    $recoveryComplete = $false
    $primaryFailure = $null
    $cleanupErrors = New-Object Collections.ArrayList
    try {
        New-Item -ItemType Directory -Path $canaryRoot -Force -ErrorAction Stop | Out-Null
        try {
            [void](Get-CimInstance Win32_Process -ErrorAction Stop)
        } catch {
            Throw-GateBInfrastructure ('root-gone cleanup canary requires complete Win32_Process ' +
                'inspection; this host returned: ' + (Get-VerifierErrorMessage $_))
        }
        $browserPath = (Get-Command wscript.exe -ErrorAction Stop).Source
        $helperExecutable = (Get-Command cscript.exe -ErrorAction Stop).Source
        $context = New-VerifierRunContext $repositoryRoot $canaryRoot
        $session = New-VerifierBrowserLease $context 'root-gone-cleanup' $browserPath
        $profile = [string]$session.Profile
        $runId = [string]$context.RunId
        $repositoryIdentity = [string]$context.RepositoryIdentity
        $port = [int]$session.CdpPort
        $evidencePath = Join-Path $context.EvidenceDirectory 'root-gone-retained-proof.txt'
        [IO.File]::WriteAllText($evidencePath,
            ('root-gone cleanup retention proof for ' + $runId),
            [Text.UTF8Encoding]::new($false))
        Register-VerifierEvidenceArtifact $context $evidencePath

        $helperScript = [IO.Path]::ChangeExtension(
            (Join-Path $canaryRoot 'different-name-markerless-helper.ps1'), '.vbs')
        $rootScript = [IO.Path]::ChangeExtension(
            (Join-Path $canaryRoot 'root-gone-launcher.ps1'), '.vbs')
        $rootStopPath = Join-Path $canaryRoot 'root-gone-stop.signal'
        $helperText = [string]::Join([Environment]::NewLine, @(
            'Option Explicit'
            # The helper has no verifier switches. The profile is passed as a
            # positional fixture argument solely to model a markerless
            # differently named process that may still retain a profile.
            'WScript.Sleep 15000'
        ))
        $rootText = [string]::Join([Environment]::NewLine, @(
            'Option Explicit'
            'Dim shell, commandLine, fileSystem, i'
            'Function Q(value)'
            '    Q = Chr(34) & Replace(CStr(value), Chr(34), Chr(34) & Chr(34)) & Chr(34)'
            'End Function'
            'Set shell = CreateObject("WScript.Shell")'
            'commandLine = Q("cscript.exe") & " //B " & Q(WScript.Arguments(0)) & " " & Q(WScript.Arguments(1))'
            'shell.Run commandLine, 0, False'
            'Set fileSystem = CreateObject("Scripting.FileSystemObject")'
            # Hold the root until the canary has captured the helper's exact
            # WMI identity, then let the canary signal a bounded exit. This
            # makes the root-gone transition deterministic without relying on
            # an arbitrary sleep racing elevated WMI queries.
            'For i = 1 To 300'
            '    If fileSystem.FileExists(CStr(WScript.Arguments(2))) Then Exit For'
            '    WScript.Sleep 100'
            'Next'
        ))
        [IO.File]::WriteAllText($helperScript, $helperText,
            [Text.UTF8Encoding]::new($false))
        [IO.File]::WriteAllText($rootScript, $rootText,
            [Text.UTF8Encoding]::new($false))
        Assert-VerifierNoReparseAncestors $canaryRoot
        if (-not (Test-VerifierPhysicalChildPath $canaryRoot $helperScript) -or
                -not (Test-VerifierPhysicalChildPath $canaryRoot $rootScript)) {
            Throw-GateBInfrastructure 'root-gone canary fixture scripts escaped the physical namespace'
        }

        $rootArguments = @(
            '//B', $rootScript, $helperScript, $profile, $rootStopPath,
            '--user-data-dir', $profile,
            '--tsj-verifier-run', $runId,
            '--tsj-verifier-worktree', $repositoryIdentity,
            '--remote-debugging-port', [string]$port)
        $rootProcess = Start-VerifierProcess $browserPath $rootArguments
        # Retain the launch handle/PID before any fallible identity query.
        $session.Runtime.Browser = $rootProcess
        $session.ProcessId = [int]$rootProcess.Id
        $session.Status = 'started'
        Write-VerifierManifest $context
        $rootStartTicks = Get-VerifierProcessStartTicks $rootProcess
        $rootIdentity = Get-VerifierCurrentProcessIdentity $session.ProcessId `
            $rootStartTicks 0 '' '' 0 $runId '' 0 $browserPath
        $session.ProcessStartTicks = $rootStartTicks
        $session.ProcessParentProcessId = [int]$rootIdentity.Record.ParentProcessId
        $session.ProcessParentProcessStartTicks = [long]$rootIdentity.Record.ParentProcessStartTicks
        $session.ProcessCommandLine = [string]$rootIdentity.Record.CommandLine
        Write-VerifierManifest $context

        $helperCandidate = $null
        $helperDeadline = [DateTime]::UtcNow.AddSeconds(20)
        do {
            $rootProcess.Refresh()
            if ([bool]$rootProcess.HasExited) {
                Throw-GateBInfrastructure 'root-gone canary root exited before its helper identity was captured'
            }
            $helperCandidates = @(Get-CimInstance Win32_Process -ErrorAction Stop |
                Where-Object {
                    [int]$_.ParentProcessId -eq [int]$rootProcess.Id -and
                    $_.Name -and
                    ([string]$_.Name).Equals([IO.Path]::GetFileName($helperExecutable),
                        [StringComparison]::OrdinalIgnoreCase) -and
                    $_.CommandLine -and
                    (Test-VerifierCommandLineCanonicalPathToken ([string]$_.CommandLine) $helperScript)
                })
            if ($helperCandidates.Count -gt 1) {
                Throw-GateBInfrastructure 'root-gone canary found multiple differently named helper candidates'
            }
            if ($helperCandidates.Count -eq 1) {
                $helperCandidate = $helperCandidates[0]
                break
            }
            Start-Sleep -Milliseconds 100
        } while ([DateTime]::UtcNow -lt $helperDeadline)
        if ($null -eq $helperCandidate) {
            Throw-GateBInfrastructure 'root-gone canary did not expose its differently named markerless helper'
        }
        $helperProcess = Get-Process -Id ([int]$helperCandidate.ProcessId) -ErrorAction SilentlyContinue
        if ($null -eq $helperProcess) {
            Throw-GateBInfrastructure 'root-gone canary helper disappeared before identity capture'
        }
        $helperStartTicks = Get-VerifierProcessStartTicks $helperProcess
        # cscript.exe receives its VBS path positionally rather than through
        # PowerShell's -File switch. Let the shared identity routine prove the
        # current PID/start/parent/executable, then prove the positional script
        # and profile tokens with the exact canonical token check below.
        $helperIdentity = Get-VerifierCurrentProcessIdentity `
            ([int]$helperCandidate.ProcessId) $helperStartTicks `
            ([int]$rootProcess.Id) '' '' 0 '' '' $rootStartTicks $helperExecutable
        $helperIdentityRecord = $helperIdentity.Record
        Assert-GateB (Test-VerifierCommandLineCanonicalPathToken $helperIdentityRecord.CommandLine $helperScript) `
            'root-gone canary helper command line did not retain its exact helper identity'
        Assert-GateB (Test-VerifierCommandLineCanonicalPathToken $helperIdentityRecord.CommandLine $profile) `
            'root-gone canary helper did not retain its positional profile fixture'
        foreach ($markerCheck in @(
                @{ Switch = '--user-data-dir'; Value = [string]$profile },
                @{ Switch = '--tsj-verifier-run'; Value = [string]$runId },
                @{ Switch = '--tsj-verifier-worktree'; Value = [string]$repositoryIdentity },
                @{ Switch = '--remote-debugging-port'; Value = [string]$port })) {
            $markerPresence = Get-VerifierCommandLineSwitchPresence `
                $helperIdentityRecord.CommandLine $markerCheck.Switch $markerCheck.Value
            Assert-GateB (-not [bool]$markerPresence.Present) `
                "root-gone helper unexpectedly carried verifier marker $($markerCheck.Switch)"
        }
        $helperProcess.Refresh()
        Assert-GateB (-not [bool]$helperProcess.HasExited) `
            'root-gone canary helper exited before the root-gone negative test'

        Assert-VerifierNoReparseAncestors $canaryRoot
        if (-not (Test-VerifierPhysicalChildPath $canaryRoot $rootStopPath)) {
            Throw-GateBInfrastructure 'root-gone canary stop signal escaped its physical fixture namespace'
        }
        [IO.File]::WriteAllText($rootStopPath, 'stop-root-gone-canary',
            [Text.UTF8Encoding]::new($false))

        $rootDeadline = [DateTime]::UtcNow.AddSeconds(35)
        do {
            $rootProcess.Refresh()
            if ([bool]$rootProcess.HasExited) { break }
            Start-Sleep -Milliseconds 100
        } while ([DateTime]::UtcNow -lt $rootDeadline)
        $rootProcess.Refresh()
        if (-not [bool]$rootProcess.HasExited) {
            Throw-GateBInfrastructure 'root-gone canary root did not terminate within its bound'
        }
        $rootGoneObserved = $true
        Assert-GateB ($null -eq (Get-VerifierCurrentProcessRecordById ([int]$rootProcess.Id))) `
            'root-gone canary root was not absent from both process views'
        $helperProcess.Refresh()
        Assert-GateB (-not [bool]$helperProcess.HasExited) `
            'root-gone canary helper did not survive the root termination'
        $helperCurrent = Get-VerifierCurrentProcessRecordById ([int]$helperProcess.Id)
        Assert-GateB ($null -ne $helperCurrent -and
            [long]$helperCurrent.ProcessStartTicks -eq $helperStartTicks -and
            [int]$helperCurrent.ParentProcessId -eq [int]$rootProcess.Id) `
            'root-gone canary helper lost its exact current ancestry/start identity'

        $cleanupFailure = $null
        try { Complete-VerifierBrowserSession $context $session } catch {
            $cleanupFailure = $_
        }
        $rootGoneRejected = ($null -ne $cleanupFailure -and
            (Test-VerifierInfrastructureError $cleanupFailure))
        Assert-GateB $rootGoneRejected `
            'root-gone cleanup did not return typed infrastructure failure'
        Assert-GateB ([string]$session.CleanupResult -eq 'infrastructure-failure' -and
            (Test-GateBExactBooleanProperty $session.Lease 'ReleaseBlocked' $true)) `
            'root-gone cleanup did not durably block the independent lease-drain path'
        Assert-GateB (Test-Path -LiteralPath $profile -PathType Container) `
            'root-gone cleanup deleted the owned profile after root absence'
        Assert-GateB (Test-Path -LiteralPath $session.Lease.Path -PathType Leaf) `
            'root-gone cleanup deleted the owned claim after root absence'
        Assert-GateB (Test-Path -LiteralPath $evidencePath -PathType Leaf) `
            'root-gone cleanup lost its retained evidence after root absence'
        Assert-GateB (Test-Path -LiteralPath $context.RunRoot -PathType Container) `
            'root-gone cleanup deleted its run root after root absence'
        Assert-GateB (Test-VerifierLeaseNeedsCleanup $session.Lease) `
            'root-gone cleanup made the retained lease appear terminal'

        # Exercise the independent final-drain path as well. It must not
        # release a blocked browser lease merely because the filtered profile
        # snapshot cannot see the markerless different executable.
        $drainResult = Complete-VerifierRun $context
        Assert-GateB ($null -ne $drainResult -and -not [bool]$drainResult.Success) `
            'root-gone final cleanup drain reported success despite retained ownership'
        Assert-GateB ((Test-Path -LiteralPath $profile -PathType Container) -and
            (Test-Path -LiteralPath $session.Lease.Path -PathType Leaf)) `
            'root-gone final cleanup drain released retained resources'
        # The helper was proven alive after the root disappeared and before
        # cleanup was attempted. It may naturally reach its bounded sleep
        # completion while the retained-resource assertions run; if it is
        # still alive, re-check its exact identity, but do not turn a natural
        # exit into a false canary failure.
        $helperProcess.Refresh()
        if (-not [bool]$helperProcess.HasExited) {
            $helperAfterDrain = Get-VerifierCurrentProcessRecordById ([int]$helperProcess.Id)
            Assert-GateB ($null -ne $helperAfterDrain -and
                [long]$helperAfterDrain.ProcessStartTicks -eq $helperStartTicks) `
                'root-gone final cleanup drain changed the surviving helper identity'
        }
        $canarySucceeded = $true
    } catch {
        $primaryFailure = $_
    } finally {
        # The negative proof intentionally retains resources while the helper
        # is alive. Once the canary-owned helper exits naturally, perform a
        # separate exact test-fixture recovery. This recovery is never used by
        # production cleanup to authorize a root-gone session.
        if ($null -ne $helperProcess) {
            try {
                if (-not $helperProcess.WaitForExit(30000)) {
                    [void]$cleanupErrors.Add('root-gone helper did not complete its bounded natural exit')
                }
                $helperProcess.Refresh()
                if (-not [bool]$helperProcess.HasExited) {
                    [void]$cleanupErrors.Add('root-gone helper remained alive; retaining exact evidence')
                } elseif ($null -ne (Get-VerifierCurrentProcessRecordById ([int]$helperProcess.Id))) {
                    [void]$cleanupErrors.Add('root-gone helper remained in the current process view')
                }
            } catch {
                [void]$cleanupErrors.Add(('root-gone helper termination/absence proof: ' +
                    (Get-VerifierErrorMessage $_)))
            }
        }
        if ($null -ne $rootProcess) {
            try {
                if (-not $rootProcess.WaitForExit(5000)) {
                    [void]$cleanupErrors.Add('root-gone root did not complete its final WaitForExit')
                }
                $rootProcess.Refresh()
                if (-not [bool]$rootProcess.HasExited) {
                    [void]$cleanupErrors.Add('root-gone root remained alive; retaining exact evidence')
                } elseif ($null -ne (Get-VerifierCurrentProcessRecordById ([int]$rootProcess.Id))) {
                    [void]$cleanupErrors.Add('root-gone root remained in the current process view')
                }
            } catch {
                [void]$cleanupErrors.Add(('root-gone root termination/absence proof: ' +
                    (Get-VerifierErrorMessage $_)))
            }
        }
        if ($rootGoneObserved -and $rootGoneRejected -and
                $null -ne $context -and $null -ne $session -and
                $null -ne $helperProcess -and $helperProcess.HasExited -and
                $cleanupErrors.Count -eq 0) {
            try {
                Assert-VerifierBrowserProfileIsQuiescent $profile $session.BrowserPath `
                    $runId $repositoryIdentity $port
                $session.Lease.ReleaseBlocked = $false
                $session.Lease.ReleaseBlockReason = ''
                Release-VerifierPortLease $context $session.Lease
                if (Test-Path -LiteralPath $profile) {
                    [void](Assert-VerifierPhysicalOwnedPath $context.RunRoot $profile -ValidateTree)
                    Remove-VerifierOwnedTree $context.RunRoot $profile
                }
                if (Test-Path -LiteralPath $profile) {
                    Throw-GateBInfrastructure 'root-gone canary recovery left its exact profile behind'
                }
                $session.Status = 'cleaned'
                $session.CleanupResult = 'complete'
                $session.Error = ''
                Write-VerifierManifest $context
                $recovered = Complete-VerifierRun $context
                if ($null -eq $recovered -or -not [bool]$recovered.Success) {
                    $detail = if ($recovered) { @($recovered.Errors) -join '; ' } else {
                        'Complete-VerifierRun returned no result during root-gone recovery.'
                    }
                    Throw-GateBInfrastructure $detail
                }
                Assert-GateBContextResourcesReleased $context
                Remove-GateBCanaryRoots $canaryRoot @($context)
                if (Test-Path -LiteralPath $canaryRoot) {
                    Throw-GateBInfrastructure 'root-gone canary recovery left its exact evidence namespace behind'
                }
                $recoveryComplete = $true
            } catch {
                [void]$cleanupErrors.Add(('root-gone canary exact recovery failed: ' +
                    (Get-VerifierErrorMessage $_)))
            }
        } elseif ($null -ne $context -and -not $rootGoneObserved -and
                $null -ne $rootIdentity) {
            # If fixture setup failed before the intentional root-gone branch,
            # use the normal exact cleanup path only while the verified root is
            # still available. Any uncertainty retains the canary namespace.
            try {
                $fallbackCleanup = Complete-VerifierRun $context
                if ($null -eq $fallbackCleanup -or -not [bool]$fallbackCleanup.Success) {
                    $detail = if ($fallbackCleanup) { @($fallbackCleanup.Errors) -join '; ' } else {
                        'Complete-VerifierRun returned no result during fixture cleanup.'
                    }
                    [void]$cleanupErrors.Add($detail)
                } elseif ($cleanupErrors.Count -eq 0) {
                    Remove-GateBCanaryRoots $canaryRoot @($context)
                }
            } catch {
                [void]$cleanupErrors.Add((Get-VerifierErrorMessage $_))
            }
        }
        if ($null -ne $helperProcess) {
            try { $helperProcess.Dispose() } catch {
                [void]$cleanupErrors.Add(('root-gone helper handle disposal: ' +
                    (Get-VerifierErrorMessage $_)))
            }
        }
        if ($null -ne $rootProcess) {
            try { $rootProcess.Dispose() } catch {
                [void]$cleanupErrors.Add(('root-gone root handle disposal: ' +
                    (Get-VerifierErrorMessage $_)))
            }
        }
    }
    if ($cleanupErrors.Count -gt 0) {
        $primaryDetail = if ($null -ne $primaryFailure) {
            'primary=' + (Get-VerifierErrorMessage $primaryFailure) + '; '
        } else { '' }
        Throw-GateBInfrastructure ('root-gone cleanup canary could not prove exact cleanup; ' +
            'evidence was retained at ' + $canaryRoot + ': ' + $primaryDetail +
            ($cleanupErrors -join '; '))
    }
    if ($null -ne $primaryFailure) {
        if (Test-VerifierInfrastructureError $primaryFailure) { throw $primaryFailure }
        Throw-GateBInfrastructure ('root-gone cleanup canary failed: ' +
            (Get-VerifierErrorMessage $primaryFailure))
    }
    if (-not $canarySucceeded -or -not $recoveryComplete) {
        Throw-GateBInfrastructure 'root-gone cleanup canary did not complete its retained-resource proof and exact recovery'
    }
    Write-Host 'PASS:root-gone browser cleanup retained markerless helper/profile/claim/evidence and returned infrastructure 2 before exact recovery'
}

function Invoke-GateBRealEdgeOwnershipCanary() {
    $canaryRoot = Join-Path ([IO.Path]::GetTempPath()) `
        ('TroubleshootJS\gate-b-real-edge-' + [Guid]::NewGuid().ToString('N'))
    $context = $null
    $session = $null
    $edgeProcess = $null
    $primaryFailure = $null
    $cleanupErrors = New-Object Collections.ArrayList
    $canarySucceeded = $false
    try {
        New-Item -ItemType Directory -Path $canaryRoot -Force -ErrorAction Stop | Out-Null
        try {
            [void](Get-CimInstance Win32_Process -ErrorAction Stop)
        } catch {
            Throw-GateBInfrastructure ('real Edge ownership canary requires complete Win32_Process ' +
                'inspection; this host returned: ' + (Get-VerifierErrorMessage $_))
        }
        $edgePath = Resolve-VerifierBrowserPath ''
        if (-not (Test-Path -LiteralPath $edgePath -PathType Leaf)) {
            Throw-GateBInfrastructure 'the shared Edge resolver returned a non-existent executable.'
        }
        $context = New-VerifierRunContext $repositoryRoot $canaryRoot
        $session = New-VerifierBrowserLease $context 'real-edge-ownership' $edgePath
        $profile = [string]$session.Profile
        $runId = [string]$context.RunId
        $port = [int]$session.CdpPort
        $arguments = @(
            '--headless=new', '--disable-gpu', '--disable-sync', '--no-first-run',
            '--no-default-browser-check', '--remote-debugging-address=127.0.0.1',
            '--user-data-dir=' + $profile,
            '--remote-debugging-port=' + [string]$port,
            '--tsj-verifier-run=' + $runId,
            '--tsj-verifier-worktree=' + [string]$context.RepositoryIdentity,
            'about:blank'
        )
        $edgeProcess = Start-VerifierProcess $edgePath $arguments
        # Retain the process object and PID before any fallible start/identity
        # query. A delayed bind or identity failure must never release this
        # lease and later let a different Edge instance inherit the port.
        $session.Runtime.Browser = $edgeProcess
        $session.ProcessId = [int]$edgeProcess.Id
        $session.Status = 'started'
        Write-VerifierManifest $context
        $session.ProcessStartTicks = Get-VerifierProcessStartTicks $edgeProcess
        $identity = Get-VerifierCurrentProcessIdentity $session.ProcessId `
            $session.ProcessStartTicks 0 '' '' $port $runId '' 0 $edgePath
        $session.ProcessParentProcessId = [int]$identity.Record.ParentProcessId
        $session.ProcessParentProcessStartTicks = [long]$identity.Record.ParentProcessStartTicks
        $session.ProcessCommandLine = [string]$identity.Record.CommandLine
        Write-VerifierManifest $context

        $deadline = [DateTime]::UtcNow.AddSeconds(30)
        $descendants = @()
        do {
            $edgeProcess.Refresh()
            if ([bool]$edgeProcess.HasExited) {
                Throw-GateBInfrastructure 'real Edge exited before an owned descendant and listener could be proven.'
            }
            $snapshot = @(Get-VerifierBrowserOwnershipSnapshot $edgePath $profile $runId `
                $context.RepositoryIdentity $port)
            $descendants = @(Get-VerifierDescendantProcessRecords $session $snapshot)
            if ($descendants.Count -gt 0) { break }
            Start-Sleep -Milliseconds 200
        } while ([DateTime]::UtcNow -lt $deadline)
        if ($descendants.Count -eq 0) {
            Throw-GateBInfrastructure 'real Edge did not expose a complete owned descendant graph within the canary bound.'
        }
        Confirm-VerifierPortLeaseBound $context $session.Lease $session.ProcessId `
            $session.ProcessStartTicks $session
        Complete-VerifierBrowserSession $context $session
        if (-not $edgeProcess.WaitForExit(5000)) {
            Throw-GateBInfrastructure 'real Edge root did not complete its bounded cleanup wait.'
        }
        $edgeProcess.Refresh()
        if (-not [bool]$edgeProcess.HasExited) {
            Throw-GateBInfrastructure 'real Edge root remained alive after exact cleanup.'
        }
        Assert-GateBContextResourcesReleased $context
        Assert-GateB (-not (Test-Path -LiteralPath $profile)) `
            'real Edge ownership canary left the browser profile behind'
        $canarySucceeded = $true
    } catch {
        $primaryFailure = $_
    } finally {
        if ($null -ne $context) {
            try {
                $cleanup = Complete-VerifierRun $context
                if ($null -eq $cleanup -or -not [bool]$cleanup.Success) {
                    $detail = if ($cleanup) { @($cleanup.Errors) -join '; ' } else {
                        'Complete-VerifierRun returned no result.'
                    }
                    [void]$cleanupErrors.Add($detail)
                }
            } catch {
                [void]$cleanupErrors.Add((Get-VerifierErrorMessage $_))
            }
        }
        if ($null -ne $edgeProcess) {
            try {
                if (-not $edgeProcess.WaitForExit(5000)) {
                    [void]$cleanupErrors.Add('real Edge process did not complete WaitForExit')
                }
                $edgeProcess.Refresh()
                if (-not [bool]$edgeProcess.HasExited) {
                    [void]$cleanupErrors.Add('real Edge process remained alive; run evidence was retained')
                } elseif ($null -ne (Get-VerifierCurrentProcessRecordById ([int]$edgeProcess.Id))) {
                    [void]$cleanupErrors.Add('real Edge process identity remained present after cleanup')
                }
            } catch {
                [void]$cleanupErrors.Add(('real Edge process termination proof: ' +
                    (Get-VerifierErrorMessage $_)))
            }
            try { $edgeProcess.Dispose() } catch {
                [void]$cleanupErrors.Add(('real Edge process handle disposal: ' +
                    (Get-VerifierErrorMessage $_)))
            }
        }
        if ($canarySucceeded -and $cleanupErrors.Count -eq 0) {
            try { Remove-GateBCanaryRoots $canaryRoot @($context) } catch {
                [void]$cleanupErrors.Add((Get-VerifierErrorMessage $_))
            }
        } elseif ($null -eq $context -and $null -eq $edgeProcess -and
                $null -eq $primaryFailure) {
            try { Remove-GateBCanaryRoots $canaryRoot @() } catch {
                [void]$cleanupErrors.Add((Get-VerifierErrorMessage $_))
            }
        }
    }
    if ($cleanupErrors.Count -gt 0) {
        Throw-GateBInfrastructure ('real Edge ownership canary cleanup was not proven; evidence was retained at ' +
            $canaryRoot + ': ' + ($cleanupErrors -join '; '))
    }
    if ($null -ne $primaryFailure) {
        if (Test-VerifierInfrastructureError $primaryFailure) { throw $primaryFailure }
        Throw-GateBInfrastructure ('real Edge ownership canary failed: ' +
            (Get-VerifierErrorMessage $primaryFailure))
    }
    Write-Host 'PASS:real Edge markerless-descendant ancestry, profile, claim, listener, and evidence cleanup canary'
}

function Invoke-GateBPreviewIdentityFailureCanary() {
    $canaryRoot = Join-Path ([IO.Path]::GetTempPath()) `
        ('TroubleshootJS\gate-b-preview-identity-' + [Guid]::NewGuid().ToString('N'))
    $context = $null
    $failure = $null
    $cleanupErrors = New-Object Collections.ArrayList
    $cleanup = $null
    try {
        New-Item -ItemType Directory -Path $canaryRoot -Force -ErrorAction Stop | Out-Null
        $context = New-VerifierRunContext $repositoryRoot $canaryRoot
        $context.TestHooks.FailNextPreviewIdentityCapture = $true
        $previewScript = Join-Path $repositoryRoot 'scripts\preview.ps1'
        $identityFailureObserved = $false
        $identityFailureMessage = ''
        try {
            [void](Start-VerifierOwnedPreview $context $previewScript 10)
        } catch {
            $identityFailureObserved = Test-VerifierInfrastructureError $_
            $identityFailureMessage = Get-VerifierErrorMessage $_
        }
        Assert-GateB $identityFailureObserved `
            'injected preview identity-capture failure was not infrastructure'
        $identityRetained = ([int]$context.Server.ProcessId -gt 0 -and
            $null -ne $context.Server.Process -and
            (Test-GateBExactBooleanProperty $context.Server `
                'ProcessIdentityKnown' $false))
        if (-not $identityRetained) {
            Assert-GateB $false ("preview identity failure did not retain the started process handle/PID; " +
                "state=$($context.Server.State), pid=$($context.Server.ProcessId), " +
                "process=$([bool]($null -ne $context.Server.Process)), " +
                "identityKnown=$([bool]$context.Server.ProcessIdentityKnown), " +
                "serverError=$($context.Server.Error), failure=$identityFailureMessage, " +
                "claimPath=$($context.Server.Lease.Path)")
        }
        Assert-GateB (Test-Path -LiteralPath $context.Server.Lease.Path -PathType Leaf) `
            'preview identity failure did not retain its exact port claim'
        Stop-GateBExactProcess $context.Server.Process $context.Server.Port
        $cleanup = Complete-VerifierRun $context
        Assert-GateB ($cleanup -and [bool]$cleanup.Success) `
            'preview identity failure could not complete after exact process termination proof'
        Assert-GateBContextResourcesReleased $context
    } catch {
        $failure = $_
    } finally {
        if ($null -ne $context) {
            try {
                $cleanup = Complete-VerifierRun $context
                if ($null -eq $cleanup -or -not [bool]$cleanup.Success) {
                    $detail = if ($cleanup) { @($cleanup.Errors) -join '; ' } else {
                        'Complete-VerifierRun returned no result.'
                    }
                    [void]$cleanupErrors.Add($detail)
                }
            } catch {
                [void]$cleanupErrors.Add((Get-VerifierErrorMessage $_))
            }
        }
        if ($null -eq $failure -and $cleanupErrors.Count -eq 0 -and $null -ne $context) {
            try {
                Remove-GateBCanaryRoots $canaryRoot @($context)
            } catch {
                [void]$cleanupErrors.Add((Get-VerifierErrorMessage $_))
            }
        }
    }
    if ($cleanupErrors.Count -gt 0) {
        Throw-GateBInfrastructure ('preview identity-failure canary cleanup was not proven; evidence was retained: ' +
            ($cleanupErrors -join '; '))
    }
    if ($null -ne $failure) {
        if (Test-VerifierInfrastructureError $failure) { throw $failure }
        Throw-GateBInfrastructure ('preview identity-failure canary failed: ' +
            (Get-VerifierErrorMessage $failure))
    }
    Write-Host 'PASS:run-owned preview identity-capture failure retained process/claim until exact cleanup proof'
}

function Invoke-GateBListenerInspectionFailureCheck() {
    $cases = @(
        [pscustomobject]@{ Name = 'empty output'; Output = @(); ExitCode = 0 },
        [pscustomobject]@{ Name = 'error text'; Output = @('ERROR: Access is denied.'); ExitCode = 0 },
        [pscustomobject]@{ Name = 'malformed TCP record'; Output = @('TCP 127.0.0.1:50001 malformed'); ExitCode = 0 },
        [pscustomobject]@{ Name = 'nonzero command'; Output = @('netstat failed'); ExitCode = 1 }
    )
    foreach ($case in $cases) {
        $classified = $false
        try {
            [void](Parse-VerifierNetstatListenerOutput 50001 $case.Output $case.ExitCode)
        } catch {
            $classified = Test-VerifierInfrastructureError $_
        }
        Assert-GateB $classified ("listener inspection case '$($case.Name)' was not fail-closed infrastructure")
    }
    foreach ($variant in @(
            [pscustomobject]@{ Name = 'numeric-string zero'; Value = '0' }
            [pscustomobject]@{ Name = 'fractional zero'; Value = [double]0.5 }
            [pscustomobject]@{ Name = 'Boolean false'; Value = $false }
            [pscustomobject]@{ Name = 'array zero'; Value = @([int]0) }
            [pscustomobject]@{ Name = 'object zero'; Value = [pscustomobject]@{ Value = 0 } }
            [pscustomobject]@{ Name = 'null'; Value = $null }
        )) {
        $classified = $false
        try {
            [void](Parse-VerifierNetstatListenerOutput 50001 @('Active Connections') $variant.Value)
        } catch {
            $classified = Test-VerifierInfrastructureError $_
        }
        Assert-GateB $classified `
            "raw netstat parser accepted malformed ExitCode $($variant.Name)"
    }
    foreach ($validExitCode in @(0, [int64]0)) {
        $classified = $false
        try {
            [void](Parse-VerifierNetstatListenerOutput 50001 `
                @('TCP 127.0.0.1:50001 0.0.0.0:0 LISTENING ' + [string]$PID) $validExitCode)
            $classified = $true
        } catch {
            $classified = $false
        }
        Assert-GateB $classified `
            "raw netstat parser rejected exact integral ExitCode type $($validExitCode.GetType().Name)"
    }
    $emptyHeaderInspection = $null
    try {
        $emptyHeaderInspection = Parse-VerifierNetstatListenerOutput 50001 `
            @('Active Connections',
              '  Proto  Local Address          Foreign Address        State           PID') 0
    } catch {
        Assert-GateB $false 'exact netstat table header was not accepted as explicit empty absence'
    }
    Assert-GateB ($null -ne $emptyHeaderInspection -and
        $emptyHeaderInspection.Success -and $emptyHeaderInspection.Known -and
        -not $emptyHeaderInspection.HasListeners -and
        @($emptyHeaderInspection.Listeners).Count -eq 0) `
        'exact successful netstat empty output did not prove listener absence'
    Write-Host 'PASS:listener inspection malformed/error fail-closed contract'
}

function Invoke-GateBCdpHandshakeCanary() {
    $listener = $null
    $acceptTask = $null
    $acceptedClient = $null
    $socket = $null
    $failure = $null
    $cleanupErrors = New-Object Collections.ArrayList
    $port = 0
    try {
        $listener = [Net.Sockets.TcpListener]::new([Net.IPAddress]::Loopback, 0)
        $listener.Start()
        $port = [int]$listener.LocalEndpoint.Port
        # Accept the TCP connection but deliberately never answer the HTTP
        # Upgrade request. This isolates the WebSocket handshake deadline from
        # TCP connect success and from any browser implementation.
        $acceptTask = $listener.AcceptTcpClientAsync()
        $observed = $false
        try {
            $socket = Connect-VerifierCdpSocket ([Uri]('ws://127.0.0.1:' +
                [string]$port + '/devtools/page/gate-b-hanging-handshake')) `
                ([DateTime]::UtcNow.AddMilliseconds(500))
        } catch {
            $observed = Test-VerifierInfrastructureError $_
        }
        if ($null -ne $socket) {
            try { $socket.Abort() } catch { }
            try { $socket.Dispose() } catch { }
            $socket = $null
            Throw-GateBInfrastructure 'hanging CDP handshake unexpectedly returned an open socket.'
        }
        Assert-GateBInfrastructure $observed `
            'a TCP endpoint that never completed the CDP WebSocket handshake was not typed infrastructure exit 2'
    } catch {
        $failure = $_
    } finally {
        if ($null -ne $socket) {
            try { $socket.Abort() } catch {
                [void]$cleanupErrors.Add(('CDP socket abort: ' + (Get-VerifierErrorMessage $_)))
            }
            try { $socket.Dispose() } catch {
                [void]$cleanupErrors.Add(('CDP socket dispose: ' + (Get-VerifierErrorMessage $_)))
            }
            $socket = $null
        }
        if ($null -ne $acceptTask) {
            try {
                if (-not $acceptTask.IsCompleted -and -not $acceptTask.Wait(2000)) {
                    [void]$cleanupErrors.Add('CDP handshake accept task did not complete during cleanup')
                }
                if ($acceptTask.IsCompleted) {
                    try { $acceptedClient = $acceptTask.GetAwaiter().GetResult() } catch { }
                }
            } catch {
                [void]$cleanupErrors.Add(('CDP handshake accept cleanup: ' +
                    (Get-VerifierErrorMessage $_)))
            }
        }
        if ($null -ne $acceptedClient) {
            try { $acceptedClient.Close() } catch {
                [void]$cleanupErrors.Add(('accepted client close: ' + (Get-VerifierErrorMessage $_)))
            }
            try { $acceptedClient.Dispose() } catch {
                [void]$cleanupErrors.Add(('accepted client dispose: ' + (Get-VerifierErrorMessage $_)))
            }
        }
        if ($null -ne $listener) {
            try { Stop-GateBListenerExact $listener $port } catch {
                [void]$cleanupErrors.Add((Get-VerifierErrorMessage $_))
            }
        }
    }
    if ($cleanupErrors.Count -gt 0) {
        Throw-GateBInfrastructure ('CDP handshake canary cleanup was not proven; evidence retained: ' +
            ($cleanupErrors -join '; '))
    }
    if ($null -ne $failure) {
        if (Test-VerifierInfrastructureError $failure) { throw $failure }
        Throw-GateBInfrastructure ('CDP handshake canary failed: ' +
            (Get-VerifierErrorMessage $failure))
    }
    Write-Host 'PASS:bounded CDP WebSocket hanging-handshake infrastructure canary'
}

function Invoke-GateBGwtModuleCheck() {
    $modulePathOnDisk = Join-Path $repositoryRoot 'src\com\lushprojects\circuitjs1\circuitjs1.gwt.xml'
    Assert-GateB (Test-Path -LiteralPath $modulePathOnDisk -PathType Leaf) `
        'pinned GWT module XML is missing'
    [xml]$module = Get-Content -LiteralPath $modulePathOnDisk -Raw
    Assert-GateB ($module.module.inherits.Count -ge 2) 'GWT module has too few inherits'
    Assert-GateB ($module.module.'entry-point'.class -eq 'com.lushprojects.circuitjs1.client.circuitjs1') `
        'GWT module entry point changed'
    Assert-GateB ($module.module.'rename-to' -eq 'circuitjs1') 'GWT module rename-to changed'
    Write-Host 'PASS:GWT module XML contract'
}

function Invoke-GateBJdkCheck() {
    try {
        $java = $null
        $javac = $null
        if (-not [String]::IsNullOrWhiteSpace($JavaHome)) {
            $java = Join-Path $JavaHome 'bin\java.exe'
            $javac = Join-Path $JavaHome 'bin\javac.exe'
        } else {
            $javaCommand = Get-Command java.exe -ErrorAction SilentlyContinue
            $javacCommand = Get-Command javac.exe -ErrorAction SilentlyContinue
            if ($javaCommand) { $java = $javaCommand.Source }
            if ($javacCommand) { $javac = $javacCommand.Source }
        }
        Assert-GateBInfrastructure ($java -and (Test-Path -LiteralPath $java -PathType Leaf)) `
            'java.exe was not found; select JDK 8 explicitly'
        Assert-GateBInfrastructure ($javac -and (Test-Path -LiteralPath $javac -PathType Leaf)) `
            'javac.exe was not found; select JDK 8 explicitly'
        $javaResult = Invoke-GateBBoundedProcess $java @('-version') 15000 'java version probe'
        $javaVersion = @($javaResult.Stdout, $javaResult.Stderr) -join "`n"
        $javaExit = $javaResult.ExitCode
        $javacResult = Invoke-GateBBoundedProcess $javac @('-version') 15000 'javac version probe'
        $javacVersion = @($javacResult.Stdout, $javacResult.Stderr) -join "`n"
        $javacExit = $javacResult.ExitCode
        Assert-GateBInfrastructure ($javaExit -eq 0 -and $javaVersion -match 'version\s+"1\.8\.') `
            "java is not JDK 8: $($javaVersion.Trim())"
        Assert-GateBInfrastructure ($javacExit -eq 0 -and $javacVersion -match 'javac\s+1\.8\.') `
            "javac is not JDK 8: $($javacVersion.Trim())"
        Write-Host "PASS:JDK8 java=$($javaVersion.Trim()) javac=$($javacVersion.Trim())"
    } catch {
        if (Test-VerifierInfrastructureError $_) { throw }
        Throw-GateBInfrastructure ("JDK8 selection/version probe failed: " + (Get-VerifierErrorMessage $_))
    }
}

function Invoke-GateBExitContractChecks() {
    foreach ($case in @(
        [pscustomobject]@{ Expected = 0; Actual = 0; Pass = $true; ExitCode = 0 }
        [pscustomobject]@{ Expected = 0; Actual = 1; Pass = $false; ExitCode = 1 }
        [pscustomobject]@{ Expected = 0; Actual = 2; Pass = $false; ExitCode = 2 }
        [pscustomobject]@{ Expected = 1; Actual = 0; Pass = $false; ExitCode = 1 }
        [pscustomobject]@{ Expected = 1; Actual = 1; Pass = $true; ExitCode = 0 }
        [pscustomobject]@{ Expected = 1; Actual = 2; Pass = $false; ExitCode = 2 }
        [pscustomobject]@{ Expected = 2; Actual = 0; Pass = $false; ExitCode = 2 }
        [pscustomobject]@{ Expected = 2; Actual = 1; Pass = $false; ExitCode = 2 }
        [pscustomobject]@{ Expected = 2; Actual = 2; Pass = $true; ExitCode = 0 }
        [pscustomobject]@{ Expected = 0; Actual = 99; Pass = $false; ExitCode = 2 }
        [pscustomobject]@{ Expected = 1; Actual = 99; Pass = $false; ExitCode = 2 }
        [pscustomobject]@{ Expected = 2; Actual = 99; Pass = $false; ExitCode = 2 }
    )) {
        $caseResult = Test-VerifierChildContract $case.Expected $case.Actual $false $false
        Assert-GateB ($caseResult.Pass -eq $case.Pass -and
            $caseResult.ExitCode -eq $case.ExitCode) `
            "child exit mismatch matrix failed for expected=$($case.Expected), actual=$($case.Actual)"
    }
    $positive = Test-VerifierChildContract 0 0 $false $true
    Assert-GateB $positive.Pass 'positive child 0/0 contract failed'
    $falsePass = Test-VerifierChildContract 0 0 $true $true
    Assert-GateB (-not $falsePass.Pass -and $falsePass.ExitCode -eq 1) `
        'positive child that prints FAIL while exiting 0 was accepted'
    $forced = Test-VerifierChildContract 1 1 $true $false
    Assert-GateB $forced.Pass 'forced negative child 1/1 contract failed'
    $infrastructure = Test-VerifierChildContract 2 2 $false $false
    Assert-GateB $infrastructure.Pass 'infrastructure child 2/2 contract failed'
    Assert-GateB ((Resolve-VerifierChildExitCode $true 0) -eq 0) `
        'successful child invocation did not resolve to 0'
    Assert-GateB ((Resolve-VerifierChildExitCode $false 1) -eq 1) `
        'application child exit 1 was not preserved'
    Assert-GateB ((Resolve-VerifierChildExitCode $false 2) -eq 2) `
        'infrastructure child exit 2 was not preserved'
    Assert-GateB ((Resolve-VerifierChildExitCode $false 99) -eq 2) `
        'unknown child failure was not classified as infrastructure'
    Assert-GateB ((Resolve-VerifierChildExitCode $true 1) -eq 1) `
        'observed application child exit 1 was downgraded by invocation success'
    $invalidModuleExitValues = New-Object Collections.ArrayList
    [void]$invalidModuleExitValues.Add($null)
    [void]$invalidModuleExitValues.Add('1')
    [void]$invalidModuleExitValues.Add(1.5)
    [void]$invalidModuleExitValues.Add($true)
    [void]$invalidModuleExitValues.Add(@(1, 2))
    [void]$invalidModuleExitValues.Add([pscustomobject]@{ Value = 1 })
    foreach ($invalidExitValue in @($invalidModuleExitValues)) {
        $invalidExitType = if ($null -eq $invalidExitValue) { 'null' } else {
            $invalidExitValue.GetType().FullName
        }
        Assert-GateB ((Resolve-VerifierChildExitCode $true $invalidExitValue) -eq 2) `
            "malformed module child exit value was not classified as infrastructure: $invalidExitType"
    }
    Assert-GateB ((Merge-VerifierFailureExitCode 0 $false) -eq 1) `
        'application failure did not resolve to exit 1'
    Assert-GateB ((Merge-VerifierFailureExitCode 0 $true) -eq 2) `
        'infrastructure failure did not resolve to exit 2'
    Assert-GateB ((Merge-VerifierFailureExitCode 2 $false) -eq 2) `
        'application failure downgraded an earlier infrastructure failure'
    Assert-GateB ((Merge-VerifierFailureExitCode 1 $true) -eq 2) `
        'infrastructure failure did not dominate an earlier application failure'
    $infraThenApp = Merge-VerifierFailureExitCode (Merge-VerifierFailureExitCode 0 $true) $false
    Assert-GateB ($infraThenApp -eq 2) `
        'deterministic infrastructure-then-application severity check failed'
    $integratedFalsePass = Test-VerifierChildContract 0 0 $true $true
    Assert-GateB (-not $integratedFalsePass.Pass -and $integratedFalsePass.ExitCode -eq 1) `
        'integrated positive-child false-pass contract was accepted'
    $browserVerifier = Join-Path $PSScriptRoot 'verify-browser.ps1'
    try {
        $powershell = (Get-Command powershell.exe -ErrorAction Stop).Source
        $contractResult = Invoke-GateBBoundedProcess $powershell @(
            '-NoProfile', '-ExecutionPolicy', 'Bypass', '-File', $browserVerifier,
            '-GateBContractProbe') 60000 'browser CDP contract probe'
        $contractOutput = @($contractResult.Stdout, $contractResult.Stderr)
        $contractExit = $contractResult.ExitCode
    } catch {
        Throw-GateBInfrastructure ('could not execute browser contract probe: ' +
            (Get-VerifierErrorMessage $_))
    }
    if ($contractExit -ne 0) {
        Throw-GateBInfrastructure ('deterministic timeout/CDP protocol infrastructure probes failed with exit ' +
            [string]$contractExit + ': ' + (@($contractOutput) -join ' | '))
    }
    if ((@($contractOutput) -join "`n") -notmatch '(?i)child-status') {
        Throw-GateBInfrastructure 'deterministic integrated child null/unparseable status probe did not run.'
    }
    if ((@($contractOutput) -join "`n") -notmatch '(?i)expected-exit-2 ledger resource proof') {
        Throw-GateBInfrastructure 'deterministic expected-exit-2 ledger resource-proof probe did not run.'
    }
    if ((@($contractOutput) -join "`n") -notmatch '(?i)listener owner proof bind/absence preservation') {
        Throw-GateBInfrastructure 'deterministic listener owner proof preservation/terminal probe did not run.'
    }
    foreach ($forcedProbeLine in @(
            'integrated child $?/$LASTEXITCODE stale-status ambiguity',
            'forced-negative exact anchored Java proof',
            'forced-negative preview-before-application uncertainty',
            'forced-negative browser identity unavailable',
            'forced-negative WMI/CIM uncertainty',
            'forced-negative marker missing',
            'forced-negative wrong Java text',
            'ordinary Task43ForcedNegative exact proof',
            'forced-negative marker then later infrastructure/cleanup',
            'integrated expected-exit-1 actual-exit-0 without forced proof',
            'integrated expected-exit-1 actual-exit-1 without forced proof'
        )) {
        if ((@($contractOutput) -join "`n").IndexOf($forcedProbeLine,
                [StringComparison]::OrdinalIgnoreCase) -lt 0) {
            Throw-GateBInfrastructure ("deterministic forced-negative contract canary did not run: " +
                $forcedProbeLine)
        }
    }
    foreach ($explicitProbe in @(
            [pscustomobject]@{ Switch = '-GateBExplicitExit2Probe'; Label = 'unmarked explicit exit-2 top-level probe' }
            [pscustomobject]@{ Switch = '-GateBExplicitExit2TypedProbe'; Label = 'typed explicit exit-2 top-level probe' }
        )) {
        try {
            $explicitResult = Invoke-GateBBoundedProcess $powershell @(
                '-NoProfile', '-ExecutionPolicy', 'Bypass', '-File', $browserVerifier,
                $explicitProbe.Switch) 60000 $explicitProbe.Label
        } catch {
            Throw-GateBInfrastructure ("could not execute $($explicitProbe.Label): " +
                (Get-VerifierErrorMessage $_))
        }
        if ($explicitResult.ExitCode -ne 2) {
            Throw-GateBInfrastructure ("$($explicitProbe.Label) returned exit " +
                [string]$explicitResult.ExitCode + ' instead of infrastructure exit 2: ' +
                (@($explicitResult.Stdout, $explicitResult.Stderr) -join ' | '))
        }
    }
    Write-Host 'PASS:top-level explicit exit-2 matrix (unmarked and typed)'
    $previousErrorActionPreference = $ErrorActionPreference
    try {
        # The deliberate failing probe writes a diagnostic with Write-Error.
        # Capture it as child evidence while classifying by the proven process
        # exit code; the parent driver must not turn that diagnostic into its
        # own application catch path.
        $ErrorActionPreference = 'Continue'
        $probeFailureResult = Invoke-GateBBoundedProcess $powershell @(
            '-NoProfile', '-ExecutionPolicy', 'Bypass', '-File', $browserVerifier,
            '-GateBContractProbe', '-GateBContractProbeFailure') 60000 `
            'failing browser CDP contract probe'
        $probeFailureOutput = @($probeFailureResult.Stdout, $probeFailureResult.Stderr)
        $probeFailureExit = $probeFailureResult.ExitCode
    } catch {
        Throw-GateBInfrastructure ('could not execute failing browser contract probe: ' +
            (Get-VerifierErrorMessage $_))
    } finally {
        $ErrorActionPreference = $previousErrorActionPreference
    }
    if ($probeFailureExit -ne 2) {
        Throw-GateBInfrastructure ('failing CDP contract probe returned exit ' +
            [string]$probeFailureExit + ' instead of infrastructure exit 2: ' +
            (@($probeFailureOutput) -join ' | '))
    }
    Write-Host 'PASS:0/1/2 mismatch matrix, false-pass, child-status, and CDP probe exit contracts'
}

function Invoke-GateBDriverInfrastructureCheck() {
    $matrix = @(
        [pscustomobject]@{ Name = 'actual=0'; Raw = 0; MustPass = $false },
        [pscustomobject]@{ Name = 'actual=1'; Raw = 1; MustPass = $false },
        [pscustomobject]@{ Name = 'actual=2'; Raw = 2; MustPass = $true },
        [pscustomobject]@{ Name = 'actual=null'; Raw = $null; MustPass = $false },
        [pscustomobject]@{ Name = 'actual=unparseable'; Raw = 'not-a-number'; MustPass = $false }
    )
    foreach ($case in $matrix) {
        $threw = $false
        $failure = $null
        $resolved = $null
        try {
            $resolved = Resolve-GateBInfrastructureChildExitCode $case.Raw $true
        } catch {
            $threw = $true
            $failure = $_
        }
        if ($case.MustPass) {
            if ($threw -or $resolved -ne 2) {
                Throw-GateBInfrastructure ("expected=2 $($case.Name) was not accepted as exit 2.")
            }
        } elseif (-not $threw -or -not (Test-VerifierInfrastructureError $failure)) {
            Throw-GateBInfrastructure ("expected=2 $($case.Name) did not produce typed infrastructure exit 2.")
        }
    }
    $unproven = $false
    try {
        [void](Resolve-GateBInfrastructureChildExitCode 2 $false)
    } catch {
        $unproven = Test-VerifierInfrastructureError $_
    }
    if (-not $unproven) {
        Throw-GateBInfrastructure 'unproven expected-infrastructure child termination was not classified as exit 2.'
    }
    $ordinaryApplicationFailure = $false
    try {
        Assert-GateB $false 'deterministic ordinary application assertion sentinel'
    } catch {
        $ordinaryApplicationFailure = -not (Test-VerifierInfrastructureError $_)
    }
    if (-not $ordinaryApplicationFailure) {
        Throw-GateBInfrastructure 'ordinary application assertion was not preserved as non-infrastructure failure.'
    }
    Write-Host 'PASS:expected-infrastructure child exit mismatch matrix (2 versus 0/1/2/null/unparseable); ordinary application assertion remains 1'

    $probeStarted = $false
    try {
        $powershell = (Get-Command powershell.exe -ErrorAction Stop).Source
        $driverPath = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot 'verify-gate-b.ps1'))
        $probeStarted = $true
        $childResult = Invoke-GateBBoundedProcess $powershell @(
            '-NoProfile', '-ExecutionPolicy', 'Bypass', '-File', $driverPath,
            '-SkipJdkCheck', '-GateBDriverInfrastructureProbe') 60000 `
            'Gate B driver infrastructure child'
        $childOutput = @($childResult.Stdout, $childResult.Stderr)
        $outerClassification = Resolve-GateBChildExitCode $childResult `
            'Gate B driver infrastructure child'
        if ($outerClassification -ne 2) {
            Throw-GateBInfrastructure ('real bounded PowerShell child exit-2 canary was classified as outer exit ' +
                [string]$outerClassification + ' instead of exactly 2.')
        }
        $childExit = Resolve-GateBInfrastructureChildExitCode $childResult.ExitCode `
            $childResult.TerminationProven
    } catch {
        if ($probeStarted -and (Test-VerifierInfrastructureError $_)) { throw }
        if ($probeStarted) {
            Throw-GateBInfrastructure ('Gate B driver infrastructure child status was not safely classified: ' +
                (Get-VerifierErrorMessage $_))
        }
        if (Test-VerifierInfrastructureError $_) { throw }
        Throw-GateBInfrastructure ('Gate B driver infrastructure probe could not execute: ' +
            (Get-VerifierErrorMessage $_))
    }
    Write-Host 'PASS:Gate B driver infrastructure exit-2 mapping'
}

function New-GateBCanaryBrowserRecord($Context, $Lease, [string]$RouteName,
        [string]$BrowserPath = '') {
    try {
        if ([String]::IsNullOrWhiteSpace($BrowserPath)) {
            $BrowserPath = if ($Lease.PSObject.Properties['BrowserPath'] -and
                    -not [String]::IsNullOrWhiteSpace([string]$Lease.BrowserPath)) {
                [string]$Lease.BrowserPath
            } else { Resolve-VerifierBrowserPath '' }
        } else {
            $BrowserPath = Get-VerifierFullPath $BrowserPath
        }
        $Lease.BrowserPath = $BrowserPath
        $routeId = [Guid]::NewGuid().ToString('N')
        $profile = Join-Path (Join-Path (Join-Path $Context.RunRoot 'browser') $routeId) 'profile'
        New-Item -ItemType Directory -Path $profile -Force -ErrorAction Stop | Out-Null
        $Lease.ProfilePath = Get-VerifierFullPath $profile
        $Lease.OwnerType = 'browser'
        $record = [pscustomobject]@{
            RunId = $Context.RunId; RepositoryIdentity = $Context.RepositoryIdentity
            RouteId = $routeId; RouteName = $RouteName
            CdpPort = $Lease.Port; Lease = $Lease; Profile = Get-VerifierFullPath $profile
            BrowserPath = $BrowserPath
            ProcessId = 0; ProcessStartTicks = 0; ProcessParentProcessId = 0
            ProcessParentProcessStartTicks = 0; ProcessCommandLine = ''
            TargetId = ''; ExpectedUrl = ''
            Status = 'leased'; CleanupResult = 'pending'; Error = ''
            ProfileInspectionFailed = $false; ProfileProcessScanCompleted = $false
            Runtime = [pscustomobject]@{ Browser = $null; Socket = $null }
        }
        [void]$Context.BrowserSessions.Add($record)
        Write-VerifierManifest $Context
        return $record
    } catch {
        if (Test-VerifierInfrastructureError $_) { throw }
        Throw-GateBInfrastructure ('could not prepare canary browser profile: ' +
            (Get-VerifierErrorMessage $_))
    }
}

function Stop-GateBListenerExact($Listener, [int]$Port) {
    if ($null -eq $Listener) { return }
    $stopFailure = $null
    $inspection = $null
    $inspectionFailure = $null
    try {
        try { $Listener.Stop() } catch {
            $stopFailure = Get-VerifierErrorMessage $_
        }
    } finally {
        # Even a Stop() exception must be followed by an exact listener query.
        # A failed/unknown query is retained as infrastructure evidence; it is
        # never treated as proof that the temporary listener disappeared.
        try { $inspection = Get-VerifierLoopbackListenerRecords $Port } catch {
            $inspectionFailure = Get-VerifierErrorMessage $_
        }
    }
    try {
        if ($stopFailure -or $inspectionFailure) {
            $details = @()
            if ($stopFailure) { $details += 'stop: ' + $stopFailure }
            if ($inspectionFailure) { $details += 'inspection: ' + $inspectionFailure }
            Throw-GateBInfrastructure ('could not prove exact canary listener cleanup on port ' +
                [string]$Port + ': ' + ($details -join '; '))
        }
        if (-not $inspection.Success -or -not $inspection.Known -or
                $inspection.HasListeners) {
            Throw-GateBInfrastructure "could not positively prove loopback port $Port was released after listener stop"
        }
    } catch {
        if (Test-VerifierInfrastructureError $_) { throw }
        Throw-GateBInfrastructure ('could not stop exact canary listener on port ' +
            [string]$Port + ': ' + (Get-VerifierErrorMessage $_))
    }
}

function Get-GateBFreeTcpPort() {
    $listener = $null
    $port = 0
    $failure = $null
    $cleanupFailure = $null
    try {
        $listener = [Net.Sockets.TcpListener]::new([Net.IPAddress]::Loopback, 0)
        $listener.Start()
        $port = [int]$listener.LocalEndpoint.Port
        Stop-GateBListenerExact $listener $port
        $listener = $null
        $inspection = Get-VerifierLoopbackListenerRecords $port
        if (-not $inspection.Success -or -not $inspection.Known -or
                $inspection.HasListeners) {
            Throw-GateBInfrastructure "could not positively prove canary port $port was released"
        }
    } catch {
        $failure = $_
    } finally {
        if ($null -ne $listener) {
            try { Stop-GateBListenerExact $listener $port } catch {
                $cleanupFailure = Get-VerifierErrorMessage $_
            }
        }
    }
    if ($cleanupFailure) {
        $failureText = if ($failure) { Get-VerifierErrorMessage $failure } else {
            'unknown canary-port allocation failure'
        }
        Throw-GateBInfrastructure ('canary-port cleanup was not proven; evidence was retained: ' +
            $failureText + '; cleanup: ' + $cleanupFailure)
    }
    if ($failure) {
        if (Test-VerifierInfrastructureError $failure) { throw $failure }
        Throw-GateBInfrastructure ('could not obtain a canary TCP port: ' +
            (Get-VerifierErrorMessage $failure))
    }
    return $port
}

function Start-GateBSeparateListener([int]$Port) {
    $process = $null
    $returned = $false
    $failure = $null
    $cleanupFailure = $null
    try {
        $powershell = (Get-Command powershell.exe -ErrorAction Stop).Source
        $command = '$l = [Net.Sockets.TcpListener]::new([Net.IPAddress]::Loopback,' +
            [string]$Port + '); $l.Start(); Start-Sleep -Seconds 60'
        $process = Start-VerifierProcess $powershell @(
            '-NoProfile', '-ExecutionPolicy', 'Bypass', '-Command', $command)
        $deadline = [DateTime]::UtcNow.AddSeconds(5)
        do {
            $process.Refresh()
            if ([bool]$process.HasExited) {
                Throw-GateBInfrastructure "foreign listener process exited before binding port $Port"
            }
            try {
                $inspection = Get-VerifierLoopbackListenerRecords $Port
                if ($inspection.Success -and $inspection.Known -and $inspection.HasListeners) {
                    try {
                        $startTicks = [long](Get-VerifierProcessStartTicks $process)
                    } catch {
                        Throw-GateBInfrastructure ('could not read separate listener process start identity: ' +
                            (Get-VerifierErrorMessage $_))
                    }
                    $ownedListener = @($inspection.Listeners | Where-Object {
                        [int]$_.ProcessId -eq [int]$process.Id -and
                        [long]$_.ProcessStartTicks -eq [long]$startTicks
                    })
                    if ($ownedListener.Count -ne 1) {
                        Throw-GateBInfrastructure "listener on port $Port was not proven to belong to the canary process"
                    }
                    $returned = $true
                    return $process
                }
            } catch {
                if (Test-VerifierInfrastructureError $_) { throw }
                Throw-GateBInfrastructure "loopback listener inspection failed while waiting for port ${Port}: $(Get-VerifierErrorMessage $_)"
            }
            Start-Sleep -Milliseconds 100
        } while ([DateTime]::UtcNow -lt $deadline)
        Throw-GateBInfrastructure "foreign listener process did not bind port $Port within the canary bound"
    } catch {
        $failure = $_
    } finally {
        # A process that started but never reached the returned/owned state is
        # always cleaned through the exact PID/start/port proof path. Cleanup
        # failures are retained as infrastructure, never swallowed.
        if ($null -ne $process -and -not $returned) {
            try { Stop-GateBExactProcess $process $Port } catch {
                $cleanupFailure = Get-VerifierErrorMessage $_
            }
        }
    }
    if ($null -ne $cleanupFailure) {
        $failureText = if ($failure) { Get-VerifierErrorMessage $failure } else {
            'unknown startup failure'
        }
        Throw-GateBInfrastructure ('foreign listener startup failed and exact cleanup was not proven: ' +
            $failureText + '; cleanup: ' + $cleanupFailure)
    }
    if ($null -ne $failure) {
        if (Test-VerifierInfrastructureError $failure) { throw $failure }
        Throw-GateBInfrastructure ('could not start separate foreign listener: ' +
            (Get-VerifierErrorMessage $failure))
    }
}

function Invoke-GateBLifecycleBooleanCanary() {
    $module = @(Get-Module VerifierIsolation | Select-Object -First 1)
    if ($module.Count -ne 1) { throw 'VerifierIsolation module was unavailable for lifecycle Boolean canary.' }
    $canaryRoot = Join-Path ([IO.Path]::GetTempPath()) `
        ('TroubleshootJS\gate-b-lifecycle-boolean-' + [Guid]::NewGuid().ToString('N'))
    $context = $null
    $lease = $null
    try {
        New-Item -ItemType Directory -Path $canaryRoot -Force -ErrorAction Stop | Out-Null
        $context = New-VerifierRunContext $repositoryRoot $canaryRoot
        $lease = New-VerifierPortLease $context 'canary-lifecycle-booleans'
        # A run-owned server without its exact Server.Lease must be rejected
        # before release mutates lifecycle state or attempts a durable write.
        $serverBeforeMalformedRelease = $context.Server
        $context.Server = [pscustomobject]@{
            Owner = 'run'; CallerOwned = $false
        }
        $leaseBeforeMalformedRelease = $lease | ConvertTo-Json -Depth 16 -Compress
        $manifestBeforeMalformedRelease = [IO.File]::ReadAllText($context.ManifestPath)
        $malformedServerReleaseRejected = $false
        try {
            Release-VerifierPortLease $context $lease
        } catch {
            $malformedServerReleaseRejected = Test-VerifierInfrastructureError $_
        } finally {
            $context.Server = $serverBeforeMalformedRelease
        }
        Assert-GateB $malformedServerReleaseRejected `
            'release accepted a run-owned server without Server.Lease'
        Assert-GateB (($lease | ConvertTo-Json -Depth 16 -Compress) -eq
            $leaseBeforeMalformedRelease) `
            'malformed run-owned server release mutated in-memory lease state'
        Assert-GateB ([IO.File]::ReadAllText($context.ManifestPath) -eq
            $manifestBeforeMalformedRelease) `
            'malformed run-owned server release performed a durable write'

        # A malformed durable Server.Port must fail before the release path
        # can change either the in-memory lease or its manifest evidence.
        $serverBeforeMalformedPort = $context.Server
        $originalServerPort = $context.Server.Port
        $context.Server.Port = 'not-an-integral-port'
        $leaseBeforeMalformedPort = $lease | ConvertTo-Json -Depth 16 -Compress
        $manifestBeforeMalformedPort = [IO.File]::ReadAllText($context.ManifestPath)
        $malformedServerPortRejected = $false
        try {
            Release-VerifierPortLease $context $lease
        } catch {
            $malformedServerPortRejected = Test-VerifierInfrastructureError $_
        } finally {
            $context.Server.Port = $originalServerPort
        }
        Assert-GateB $malformedServerPortRejected `
            'release accepted a server with a non-integral Server.Port'
        Assert-GateB (($lease | ConvertTo-Json -Depth 16 -Compress) -eq
            $leaseBeforeMalformedPort) `
            'malformed Server.Port release mutated in-memory lease state'
        Assert-GateB ([IO.File]::ReadAllText($context.ManifestPath) -eq
            $manifestBeforeMalformedPort) `
            'malformed Server.Port release performed a durable write'
        $context.Server = $serverBeforeMalformedPort
        Write-Host 'PASS:malformed server Port release was rejected without mutation'

        # ClaimMutex is a live ownership handle and must be validated before
        # release-state inspection or any manifest transaction. This direct
        # canary proves a malformed replacement leaves both memory and the
        # already-persisted lease untouched.
        $originalClaimMutex = $lease.ClaimMutex
        $leaseBeforeMalformedMutex = $lease | ConvertTo-Json -Depth 16 -Compress
        $lease.ClaimMutex = 'not-a-threading-mutex'
        $manifestBeforeMalformedMutex = [IO.File]::ReadAllText($context.ManifestPath)
        $malformedMutexRejected = $false
        try {
            Release-VerifierPortLease $context $lease
        } catch {
            $malformedMutexRejected = Test-VerifierInfrastructureError $_
        } finally {
            $lease.ClaimMutex = $originalClaimMutex
        }
        Assert-GateB $malformedMutexRejected `
            'release accepted a malformed ClaimMutex handle'
        Assert-GateB (($lease | ConvertTo-Json -Depth 16 -Compress) -eq
            $leaseBeforeMalformedMutex) `
            'malformed ClaimMutex release mutated in-memory lease state'
        Assert-GateB ([IO.File]::ReadAllText($context.ManifestPath) -eq
            $manifestBeforeMalformedMutex) `
            'malformed ClaimMutex release performed a durable write'
        Write-Host 'PASS:malformed ClaimMutex release was rejected without mutation or write'

        # Complete-VerifierRun must apply the same complete live preflight
        # before it evaluates cleanup candidates.  Make both query consumers
        # observable and prove a malformed handle cannot reach them or alter
        # the lease/context/manifest.
        $completionLeaseStateBefore = ConvertTo-Json ([pscustomobject]@{
            Status = $lease.Status; ClaimState = $lease.ClaimState
            ReleaseState = $lease.ReleaseState
            ReleaseJournalState = $lease.ReleaseJournalState
            MutexReleased = $lease.MutexReleased
        }) -Compress
        $completionContextStateBefore = ConvertTo-Json ([pscustomobject]@{
            CleanupState = $context.CleanupState
            CleanupCompletedUtc = $context.CleanupCompletedUtc
            CleanupErrors = @($context.CleanupErrors)
        }) -Compress
        $completionManifestBefore = [IO.File]::ReadAllText($context.ManifestPath)
        $completionClaimMutex = $lease.ClaimMutex
        $lease.ClaimMutex = 'not-a-threading-mutex'
        $completionResult = $null
        try {
            $completionResult = & $module[0] {
                param($completionContext)
                $oldProcessLookup = (Get-Command Get-VerifierProcessById `
                    -CommandType Function -ErrorAction Stop).ScriptBlock
                $oldListenerLookup = (Get-Command Get-VerifierLoopbackListenerRecords `
                    -CommandType Function -ErrorAction Stop).ScriptBlock
                try {
                    $script:GateBCompletionQueryObserved = $false
                    Set-Item Function:\Get-VerifierProcessById -Force -Value {
                        $script:GateBCompletionQueryObserved = $true
                        return $null
                    }
                    Set-Item Function:\Get-VerifierLoopbackListenerRecords -Force -Value {
                        $script:GateBCompletionQueryObserved = $true
                        return $null
                    }
                    $rejected = $false
                    try { [void](Complete-VerifierRun $completionContext) } catch {
                        $rejected = Test-VerifierInfrastructureError $_
                    }
                    return [pscustomobject]@{
                        Rejected = [bool]$rejected
                        QueryObserved = [bool]$script:GateBCompletionQueryObserved
                    }
                } finally {
                    Set-Item Function:\Get-VerifierProcessById -Force `
                        -Value $oldProcessLookup
                    Set-Item Function:\Get-VerifierLoopbackListenerRecords -Force `
                        -Value $oldListenerLookup
                    Remove-Variable -Name GateBCompletionQueryObserved -Scope Script `
                        -Force -ErrorAction SilentlyContinue
                }
            } $context
        } finally {
            $lease.ClaimMutex = $completionClaimMutex
        }
        $completionRejected = [bool]$completionResult.Rejected
        $completionQueryObserved = [bool]$completionResult.QueryObserved
        Assert-GateB $completionRejected `
            'completion accepted a malformed ClaimMutex lease'
        Assert-GateB (-not $completionQueryObserved) `
            'completion queried a process/listener before malformed ClaimMutex rejection'
        Assert-GateB ((ConvertTo-Json ([pscustomobject]@{
                Status = $lease.Status; ClaimState = $lease.ClaimState
                ReleaseState = $lease.ReleaseState
                ReleaseJournalState = $lease.ReleaseJournalState
                MutexReleased = $lease.MutexReleased
            }) -Compress) -ceq $completionLeaseStateBefore -and
            (ConvertTo-Json ([pscustomobject]@{
                CleanupState = $context.CleanupState
                CleanupCompletedUtc = $context.CleanupCompletedUtc
                CleanupErrors = @($context.CleanupErrors)
            }) -Compress) -ceq $completionContextStateBefore -and
            [IO.File]::ReadAllText($context.ManifestPath) -ceq $completionManifestBefore) `
            'malformed ClaimMutex completion mutated lease/context/manifest state'
        Write-Host 'PASS:malformed ClaimMutex completion was rejected before cleanup queries or mutation'

        foreach ($mutation in @(
                [pscustomobject]@{ Field = 'MutexReleased'; Value = 'false'; Name = 'string MutexReleased' }
                [pscustomobject]@{ Field = 'MutexReleased'; Value = 0; Name = 'numeric MutexReleased' }
                [pscustomobject]@{ Field = 'ReleaseBlocked'; Value = 'false'; Name = 'string ReleaseBlocked' }
                [pscustomobject]@{ Field = 'ReleaseBlocked'; Value = 0; Name = 'numeric ReleaseBlocked' }
            )) {
            $original = $lease.PSObject.Properties[$mutation.Field].Value
            $lease.($mutation.Field) = $mutation.Value
            $rejected = $false
            try {
                Release-VerifierPortLease $context $lease
            } catch {
                $rejected = Test-VerifierInfrastructureError $_
            }
            $lease.($mutation.Field) = $original
            Assert-GateB $rejected `
                "lifecycle release accepted $($mutation.Name) as a security Boolean"
        }
        foreach ($missingField in @('MutexReleased', 'ReleaseBlocked')) {
            $original = $lease.PSObject.Properties[$missingField].Value
            [void]$lease.PSObject.Properties.Remove($missingField)
            $rejected = $false
            try {
                Release-VerifierPortLease $context $lease
            } catch {
                $rejected = Test-VerifierInfrastructureError $_
            }
            Add-Member -InputObject $lease -MemberType NoteProperty -Name $missingField `
                -Value $original -Force
            Assert-GateB $rejected `
                "lifecycle release accepted missing $missingField as a security Boolean"
        }

        $readinessServer = [pscustomobject]@{
            ProcessIdentityKnown = $true; ProcessId = 0; ProcessStartTicks = 0
        }
        Assert-GateB (& $module[0] {
            param($server)
            Test-VerifierPreviewCleanupReadiness $server $true $true $true
        } $readinessServer) 'valid lifecycle cleanup proof was rejected'
        foreach ($fieldMutation in @(
                [pscustomobject]@{ Field = 'ProcessIdentityKnown'; Value = 'false'; Name = 'string ProcessIdentityKnown' }
                [pscustomobject]@{ Field = 'ProcessIdentityKnown'; Value = 0; Name = 'numeric ProcessIdentityKnown' }
                [pscustomobject]@{ Field = 'ProcessId'; Value = '0'; Name = 'string ProcessId' }
            )) {
            $original = $readinessServer.PSObject.Properties[$fieldMutation.Field].Value
            $readinessServer.($fieldMutation.Field) = $fieldMutation.Value
            $ready = & $module[0] {
                param($server)
                Test-VerifierPreviewCleanupReadiness $server $true $true $true
            } $readinessServer
            $readinessServer.($fieldMutation.Field) = $original
            Assert-GateB (-not [bool]$ready) `
                "cleanup readiness accepted $($fieldMutation.Name)"
        }
        [void]$readinessServer.PSObject.Properties.Remove('ProcessIdentityKnown')
        $missingReady = & $module[0] {
            param($server)
            Test-VerifierPreviewCleanupReadiness $server $true $true $true
        } $readinessServer
        Add-Member -InputObject $readinessServer -MemberType NoteProperty `
            -Name ProcessIdentityKnown -Value $true -Force
        Assert-GateB (-not [bool]$missingReady) `
            'cleanup readiness accepted missing ProcessIdentityKnown'
        Write-Host 'PASS:lifecycle Boolean fail-closed canary'
    } finally {
        if ($null -ne $context) {
            try { [void](Complete-VerifierRun $context) } catch { }
        }
        if (Test-Path -LiteralPath $canaryRoot) {
            Remove-GateBCanaryRoots $canaryRoot @($context)
        }
    }
}

function Invoke-GateBLeaseRollbackCheck() {
    $rollbackRoot = Join-Path ([IO.Path]::GetTempPath()) `
        ('TroubleshootJS\gate-b-rollback-canary-' + [Guid]::NewGuid().ToString('N'))
    try {
        New-Item -ItemType Directory -Path $rollbackRoot -Force -ErrorAction Stop | Out-Null
    } catch {
        Throw-GateBInfrastructure ('could not create rollback canary root: ' +
            (Get-VerifierErrorMessage $_))
    }
    $context = $null
    $reuseContext = $null
    $reuseNewContext = $null
    $reuseOldLease = $null
    $reuseNewLease = $null
    $reuseListener = $null
    $checksValidated = $false
    $canarySucceeded = $false
    try {
        $context = New-VerifierRunContext $repositoryRoot $rollbackRoot
        foreach ($failureHook in @('FailNextManifestWrite', 'FailNextClaimWrite')) {
            $port = Get-GateBFreeTcpPort
            $context.TestHooks.$failureHook = $true
            $classified = $false
            try {
                [void](New-VerifierPortLease $context ('canary-' + $failureHook) $port)
            } catch {
                $classified = Test-VerifierInfrastructureError $_
            }
            Assert-GateB $classified "$failureHook was not classified as infrastructure"
            Assert-GateB (@($context.LeaseRecords | Where-Object {
                $_.Status -ne 'released'
            }).Count -eq 0) "$failureHook left an unregistered LeaseRecord"
            Assert-GateB (@(Get-ChildItem -LiteralPath $context.PortLeaseRoot `
                -Filter '*.lease' -File -ErrorAction Stop).Count -eq 0) `
                "$failureHook leaked a claim file"

            # The same exact requested port must be immediately acquirable by
            # this independent attempt, proving both mutex release and partial
            # claim-file rollback rather than merely checking a directory.
            $reacquired = New-VerifierPortLease $context ('canary-reacquire-' + $failureHook) $port
            Release-VerifierPortLease $context $reacquired
            Assert-GateB (@(Get-ChildItem -LiteralPath $context.PortLeaseRoot `
                -Filter '*.lease' -File -ErrorAction Stop).Count -eq 0) `
                "$failureHook left a claim after successful reacquisition cleanup"
        }
        foreach ($releaseHook in @('FailNextLeaseRelease',
                'FailNextLeaseMutexDispose', 'FailNextFinalManifestWrite',
                'FailNextPostDeleteJournalWrite',
                'FailNextPostDeleteFinalManifestWrite',
                'FailNextPostDeleteBeforeFinalState')) {
            $lease = New-VerifierPortLease $context ('canary-' + $releaseHook)
            $context.TestHooks.$releaseHook = $true
            $releaseFailed = $false
            try {
                Release-VerifierPortLease $context $lease
            } catch {
                $releaseFailed = Test-VerifierInfrastructureError $_
            }
            Assert-GateB $releaseFailed "$releaseHook was not classified as infrastructure"
            if ($releaseHook -eq 'FailNextPostDeleteJournalWrite') {
                # The journal write failed while the durable pre-delete marker
                # was still current. The claim is gone, but memory must remain
                # at that exact durable state until retry commits the terminal
                # tombstone.
                Assert-GateB (-not (Test-Path -LiteralPath $lease.Path -PathType Leaf)) `
                    "$releaseHook retained a claim after the injected journal-write failure"
                Assert-GateB ([string]$lease.ReleaseState -eq 'complete' -and
                    [string]$lease.ClaimState -eq 'delete-pending' -and
                    [string]$lease.ReleaseJournalState -eq 'pre-delete') `
                    "$releaseHook left in-memory state ahead of the durable pre-delete journal"
                $durableManifest = Get-Content -LiteralPath $context.ManifestPath -Raw | ConvertFrom-Json
                $durableLease = @($durableManifest.leases | Where-Object {
                    [string]$_.leaseId -eq [string]$lease.LeaseId
                })
                Assert-GateB ($durableLease.Count -eq 1 -and
                    [string]$durableLease[0].releaseJournalState -eq 'pre-delete' -and
                    [string]$durableLease[0].claimState -eq 'delete-pending') `
                    "$releaseHook did not retain the durable pre-delete journal"
            } elseif ($releaseHook -in @('FailNextPostDeleteBeforeFinalState',
                    'FailNextPostDeleteFinalManifestWrite')) {
                # The claim is intentionally gone, but the durable
                # post-delete journal marker must still make the in-memory
                # lease incomplete and recoverable rather than silently
                # successful.  Read the manifest as a separate durability
                # proof; an in-memory field alone is not a crash-safe journal.
                Assert-GateB (-not (Test-Path -LiteralPath $lease.Path -PathType Leaf)) `
                    "$releaseHook retained a claim after the injected post-delete interruption"
                Assert-GateB ([string]$lease.ReleaseState -eq 'complete' -and
                    [string]$lease.ClaimState -eq 'delete-pending') `
                    "$releaseHook did not retain the in-memory post-delete tombstone"
                $durableManifest = Get-Content -LiteralPath $context.ManifestPath -Raw | ConvertFrom-Json
                $durableLease = @($durableManifest.leases | Where-Object {
                    [string]$_.leaseId -eq [string]$lease.LeaseId
                })
                Assert-GateB ($durableLease.Count -eq 1 -and
                    [string]$durableLease[0].releaseJournalState -eq 'post-delete-pending' -and
                    [string]$durableLease[0].claimState -eq 'delete-pending') `
                    "$releaseHook did not persist a post-delete tombstone before interruption"
            } else {
                Assert-GateB (Test-Path -LiteralPath $lease.Path -PathType Leaf) `
                    "$releaseHook did not retain the exact claim evidence"
                Assert-GateB ($lease.Status -ne 'released' -or
                    [string]$lease.ReleaseState -ne 'complete') `
                    "$releaseHook incorrectly reported a complete release"
            }
            $context.TestHooks.$releaseHook = $false
            $retryCleanup = Complete-VerifierRun $context
            Assert-GateB ($retryCleanup -and [bool]$retryCleanup.Success) `
                "$releaseHook could not complete exact retry cleanup"
            Assert-GateB (-not (Test-Path -LiteralPath $lease.Path)) `
                "$releaseHook left its claim after exact retry cleanup"
        }

        # A durable OS-release tombstone must remain recoverable after a newer
        # run acquires and binds the same port. The old recovery may delete
        # only its exact claim and must not require the port to be absent or
        # disturb the new run's claim/listener.
        $reuseContext = New-VerifierRunContext $repositoryRoot $rollbackRoot
        $reusePort = Get-GateBFreeTcpPort
        $reuseOldLease = New-VerifierPortLease $reuseContext 'canary-port-reuse-old' $reusePort
        $reuseContext.TestHooks.FailNextFinalManifestWrite = $true
        $oldReleaseFailure = $false
        try {
            Release-VerifierPortLease $reuseContext $reuseOldLease
        } catch {
            $oldReleaseFailure = Test-VerifierInfrastructureError $_
        }
        Assert-GateB $oldReleaseFailure `
            'injected pre-delete tombstone failure was not infrastructure'
        Assert-GateB (Test-Path -LiteralPath $reuseOldLease.Path -PathType Leaf) `
            'old exact claim was not retained for port-reuse recovery'
        Assert-GateB ((Test-GateBExactBooleanProperty $reuseOldLease `
                'MutexReleased' $true) -and
            [string]$reuseOldLease.ReleaseState -eq 'os-released') `
            'old lease did not retain a durable OS-release marker'

        $reuseNewContext = New-VerifierRunContext $repositoryRoot $rollbackRoot
        $reuseNewLease = New-VerifierPortLease $reuseNewContext 'canary-port-reuse-new' $reusePort
        $reuseListener = [Net.Sockets.TcpListener]::new([Net.IPAddress]::Loopback, $reusePort)
        $reuseListener.Start()
        $reuseNewProcess = Get-Process -Id $PID -ErrorAction Stop
        $reuseNewStartTicks = [long](Get-VerifierProcessStartTicks $reuseNewProcess)
        Confirm-VerifierPortLeaseBound $reuseNewContext $reuseNewLease $PID $reuseNewStartTicks
        $oldRetry = Complete-VerifierRun $reuseContext
        Assert-GateB ($oldRetry -and [bool]$oldRetry.Success) `
            'old OS-released tombstone could not recover while a newer run reused its port'
        Assert-GateB (-not (Test-Path -LiteralPath $reuseOldLease.Path)) `
            'old tombstone recovery did not remove the exact old claim'
        Assert-GateB (Test-Path -LiteralPath $reuseNewLease.Path -PathType Leaf) `
            'old tombstone recovery removed or corrupted the newer run claim'
        $reuseInspection = Get-VerifierLoopbackListenerRecords $reusePort
        Assert-GateB ($reuseInspection.Success -and $reuseInspection.Known -and
            $reuseInspection.HasListeners -and @($reuseInspection.Listeners | Where-Object {
                [int]$_.ProcessId -eq $PID -and [long]$_.ProcessStartTicks -eq $reuseNewStartTicks
            }).Count -gt 0) `
            'newer reused-port listener was not preserved during old tombstone recovery'
        Stop-GateBListenerExact $reuseListener $reusePort
        $reuseListener = $null
        $newRetry = Complete-VerifierRun $reuseNewContext
        Assert-GateB ($newRetry -and [bool]$newRetry.Success) `
            'newer reused-port lease could not complete after old tombstone recovery'
        Assert-GateBContextResourcesReleased $reuseContext
        Assert-GateBContextResourcesReleased $reuseNewContext
        $checksValidated = $true
    } finally {
        $cleanup = $null
        $cleanupErrors = New-Object Collections.ArrayList
        if ($null -ne $context) {
            try {
                $cleanup = Complete-VerifierRun $context
                if ($null -eq $cleanup -or -not [bool]$cleanup.Success) {
                    $detail = if ($cleanup) { (@($cleanup.Errors) -join '; ') } else {
                        'Complete-VerifierRun returned no result.'
                    }
                    [void]$cleanupErrors.Add($detail)
                }
            } catch {
                [void]$cleanupErrors.Add((Get-VerifierErrorMessage $_))
            }
            if ($cleanup -and [bool]$cleanup.Success) {
                try { Assert-GateBContextResourcesReleased $context } catch {
                    [void]$cleanupErrors.Add((Get-VerifierErrorMessage $_))
                }
            }
        }
        if ($null -ne $reuseListener) {
            try { Stop-GateBListenerExact $reuseListener $reusePort } catch {
                [void]$cleanupErrors.Add((Get-VerifierErrorMessage $_))
            }
        }
        foreach ($reuse in @($reuseContext, $reuseNewContext)) {
            if ($null -ne $reuse) {
                try {
                    $reuseCleanup = Complete-VerifierRun $reuse
                    if ($null -eq $reuseCleanup -or -not [bool]$reuseCleanup.Success) {
                        $reuseDetail = if ($reuseCleanup) {
                            @($reuseCleanup.Errors) -join '; '
                        } else { 'port-reuse context cleanup returned no result.' }
                        [void]$cleanupErrors.Add($reuseDetail)
                    }
                } catch {
                    [void]$cleanupErrors.Add((Get-VerifierErrorMessage $_))
                }
            }
        }
        if ($checksValidated -and $cleanup -and [bool]$cleanup.Success -and
                $cleanupErrors.Count -eq 0) {
            try {
                Remove-GateBCanaryRoots $rollbackRoot @($context, $reuseContext, $reuseNewContext)
                $canarySucceeded = $true
            } catch {
                [void]$cleanupErrors.Add((Get-VerifierErrorMessage $_))
            }
        }
        if ($cleanupErrors.Count -gt 0) {
            Throw-GateBInfrastructure ('transactional rollback canary cleanup failed; evidence was retained: ' +
                ($cleanupErrors -join '; '))
        }
    }
    if ($canarySucceeded) { Write-Host 'PASS:transactional port-claim rollback canary' }
}

function Invoke-GateBCleanupRetentionCheck() {
    $retentionRoot = Join-Path ([IO.Path]::GetTempPath()) `
        ('TroubleshootJS\gate-b-cleanup-retention-' + [Guid]::NewGuid().ToString('N'))
    try {
        New-Item -ItemType Directory -Path $retentionRoot -Force -ErrorAction Stop | Out-Null
    } catch {
        Throw-GateBInfrastructure ('could not create cleanup-retention canary root: ' +
            (Get-VerifierErrorMessage $_))
    }
    $context = $null
    $listener = $null
    $checksValidated = $false
    $canarySucceeded = $false
    try {
        $context = New-VerifierRunContext $repositoryRoot $retentionRoot
        $lease = New-VerifierPortLease $context 'canary-cleanup-retention'
        $listener = [Net.Sockets.TcpListener]::new([Net.IPAddress]::Loopback, $lease.Port)
        $listener.Start()

        $failedCleanup = Complete-VerifierRun $context
        Assert-GateB ($null -ne $failedCleanup -and -not [bool]$failedCleanup.Success) `
            'cleanup-failure retention canary unexpectedly reported success with a live listener'
        Assert-GateB (Test-Path -LiteralPath $context.RunRoot -PathType Container) `
            'cleanup-failure retention canary deleted the run root after an uncertain cleanup'
        Assert-GateB (Test-Path -LiteralPath $context.ManifestPath -PathType Leaf) `
            'cleanup-failure retention canary did not retain its manifest'
        Assert-GateB (Test-Path -LiteralPath $lease.Path -PathType Leaf) `
            'cleanup-failure retention canary released its claim while the port was listening'

        $listener.Stop()
        $listener = $null
        $successfulCleanup = Complete-VerifierRun $context
        Assert-GateB ($null -ne $successfulCleanup -and [bool]$successfulCleanup.Success) `
            'cleanup-failure retention canary could not complete after the listener was removed'
        Assert-GateB (-not (Test-Path -LiteralPath $lease.Path)) `
            'cleanup-failure retention canary left its claim after successful retry'
        Assert-GateBContextResourcesReleased $context
        $checksValidated = $true
    } finally {
        $cleanupErrors = New-Object Collections.ArrayList
        if ($null -ne $listener) {
            try { Stop-GateBListenerExact $listener $lease.Port; $listener = $null } catch {
                [void]$cleanupErrors.Add((Get-VerifierErrorMessage $_))
            }
        }
        if ($null -ne $context) {
            try {
                $cleanup = Complete-VerifierRun $context
                if ($null -eq $cleanup -or -not [bool]$cleanup.Success) {
                    $detail = if ($cleanup) { (@($cleanup.Errors) -join '; ') } else {
                        'Complete-VerifierRun returned no result.'
                    }
                    [void]$cleanupErrors.Add($detail)
                }
            } catch {
                [void]$cleanupErrors.Add((Get-VerifierErrorMessage $_))
            }
            if ($cleanup -and [bool]$cleanup.Success) {
                try { Assert-GateBContextResourcesReleased $context } catch {
                    [void]$cleanupErrors.Add((Get-VerifierErrorMessage $_))
                }
            }
        }
        if ($checksValidated -and $cleanupErrors.Count -eq 0) {
            try {
                Remove-GateBCanaryRoots $retentionRoot @($context)
                $canarySucceeded = $true
            } catch {
                [void]$cleanupErrors.Add((Get-VerifierErrorMessage $_))
            }
        }
        if ($cleanupErrors.Count -gt 0) {
            Throw-GateBInfrastructure ('cleanup-retention canary cleanup failed; evidence was retained: ' +
                ($cleanupErrors -join '; '))
        }
    }
    if ($canarySucceeded) { Write-Host 'PASS:cleanup-failure evidence-retention canary' }
}

function Start-GateBForeignProfileProcess([string]$Profile) {
    $process = $null
    $returned = $false
    $failure = $null
    $cleanupFailure = $null
    try {
        $powershell = (Get-Command powershell.exe -ErrorAction Stop).Source
        $marker = '--user-data-dir=' + $Profile
        $foreignCommand = '$marker = ' + [string][char]39 + $marker + [string][char]39 +
            '; Start-Sleep -Seconds 60'
        $process = Start-VerifierProcess $powershell @(
            '-NoProfile', '-ExecutionPolicy', 'Bypass', '-Command', $foreignCommand)
        $deadline = [DateTime]::UtcNow.AddSeconds(5)
        do {
            $process.Refresh()
            if ([bool]$process.HasExited) {
                Throw-GateBInfrastructure "foreign profile process exited before it could be inspected"
            }
            Start-Sleep -Milliseconds 100
        } while ([DateTime]::UtcNow -lt $deadline)
        $returned = $true
        return $process
    } catch {
        $failure = $_
    } finally {
        if ($null -ne $process -and -not $returned) {
            try { Stop-GateBExactProcess $process } catch {
                $cleanupFailure = Get-VerifierErrorMessage $_
            }
        }
    }
    if ($null -ne $cleanupFailure) {
        $failureText = if ($failure) { Get-VerifierErrorMessage $failure } else {
            'unknown startup failure'
        }
        Throw-GateBInfrastructure ('foreign profile startup failed and exact cleanup was not proven: ' +
            $failureText + '; cleanup: ' + $cleanupFailure)
    }
    if ($null -ne $failure) {
        if (Test-VerifierInfrastructureError $failure) { throw $failure }
        Throw-GateBInfrastructure ('could not start separate foreign profile process: ' +
            (Get-VerifierErrorMessage $failure))
    }
}

function Stop-GateBExactProcess($Process, [int]$ExpectedPort = 0,
        [string]$ExpectedCommandLine = '') {
    if ($null -eq $Process) { return }
    try {
        $Process.Refresh()
        if (-not [bool]$Process.HasExited) {
            $current = Get-Process -Id ([int]$Process.Id) -ErrorAction Stop
            $expectedStart = [long](Get-VerifierProcessStartTicks $Process)
            $actualStart = [long](Get-VerifierProcessStartTicks $current)
            if ($expectedStart -ne $actualStart) {
                throw "canary process PID $($Process.Id) changed start identity"
            }
            $currentRecords = @(Get-CimInstance Win32_Process `
                -Filter "ProcessId = $($Process.Id)" -ErrorAction Stop)
            if ($currentRecords.Count -ne 1 -or
                    -not $currentRecords[0].PSObject.Properties['ProcessId'] -or
                    -not $currentRecords[0].PSObject.Properties['ParentProcessId'] -or
                    -not $currentRecords[0].PSObject.Properties['CommandLine'] -or
                    [String]::IsNullOrWhiteSpace([string]$currentRecords[0].CommandLine) -or
                    [int]$currentRecords[0].ParentProcessId -le 0 -or
                    [int]$currentRecords[0].ProcessId -ne [int]$Process.Id) {
                throw "canary process PID $($Process.Id) had no complete current command-line identity"
            }
            if ($Process.StartInfo -and
                    -not [String]::IsNullOrWhiteSpace([string]$Process.StartInfo.FileName) -and
                    -not (Test-VerifierConfiguredExecutableIdentity `
                        ([string]$Process.StartInfo.FileName) $currentRecords[0] `
                        -RequireExecutablePath)) {
                throw "canary process PID $($Process.Id) executable identity changed"
            }
            if (-not [String]::IsNullOrWhiteSpace($ExpectedCommandLine) -and
                    -not (Test-VerifierCommandLineEquivalent ([string]$currentRecords[0].CommandLine) $ExpectedCommandLine)) {
                throw "canary process PID $($Process.Id) command-line identity changed"
            }
            if ($ExpectedPort -gt 0) {
                $portMarkerMatches = (Test-VerifierCommandLineSwitch `
                    ([string]$currentRecords[0].CommandLine) '-Port' ([string]$ExpectedPort)) -or
                    (Test-VerifierCommandLineSwitch ([string]$currentRecords[0].CommandLine) `
                        '--remote-debugging-port' ([string]$ExpectedPort))
                if (-not $portMarkerMatches) {
                    # The separate-listener fixture owns its port through the
                    # actual socket rather than a browser-style command-line
                    # switch. Prove that exact PID/start identity is the
                    # current listener before allowing its Process object to
                    # be stopped; a missing marker is never a reason to use a
                    # bare PID or skip the ownership proof.
                    $listenerProof = Get-VerifierLoopbackListenerRecords $ExpectedPort
                    $exactListener = @($listenerProof.Listeners | Where-Object {
                        [int]$_.ProcessId -eq [int]$Process.Id -and
                        [long]$_.ProcessStartTicks -eq [long]$expectedStart
                    })
                    if (-not $listenerProof.Success -or -not $listenerProof.Known -or
                            $exactListener.Count -ne 1) {
                        throw "canary process PID $($Process.Id) no longer carries or owns port $ExpectedPort"
                    }
                }
            }
            # Re-query after the WMI comparison and stop only this current
            # verified Process object, never a bare PID.
            $verifiedCurrent = Get-Process -Id ([int]$Process.Id) -ErrorAction Stop
            $verifiedCurrent.Refresh()
            if ([bool]$verifiedCurrent.HasExited -or
                    ([long](Get-VerifierProcessStartTicks $verifiedCurrent) -ne $expectedStart)) {
                throw "canary process PID $($Process.Id) changed or exited after identity validation"
            }
            $canaryCurrentRecord = [pscustomobject]@{
                ProcessId = [int]$currentRecords[0].ProcessId
                ParentProcessId = [int]$currentRecords[0].ParentProcessId
                ProcessStartTicks = $expectedStart
                CommandLine = [string]$currentRecords[0].CommandLine
            }
            [void](Stop-VerifierVerifiedProcessExactly $verifiedCurrent $expectedStart 5000 `
                $canaryCurrentRecord)
        }
        # WaitForExit is required even when the object already reported an
        # exit.  It synchronizes the Process handle before the final
        # independent PID/WMI absence proof and prevents a caller from
        # disposing a handle while a child may still be terminating.
        if (-not $Process.WaitForExit(5000)) {
            throw "canary process PID $($Process.Id) did not complete WaitForExit within the cleanup bound"
        }
        $Process.Refresh()
        if (-not [bool]$Process.HasExited) {
            throw "canary process PID $($Process.Id) exit state was not proven"
        }
        # Get-Process alone can report a stale exited object.  Require the
        # shared current-process proof to positively return absence as well.
        $currentAfterStop = Get-VerifierCurrentProcessRecordById ([int]$Process.Id)
        if ($null -ne $currentAfterStop) {
            throw "canary process PID $($Process.Id) still exists after cleanup"
        }
        if ($ExpectedPort -gt 0) {
            $inspection = Get-VerifierLoopbackListenerRecords $ExpectedPort
            if (-not $inspection.Success -or -not $inspection.Known -or
                $inspection.HasListeners) {
                throw "canary process cleanup did not positively prove loopback port $ExpectedPort was released"
            }
        }
    } catch {
        Throw-GateBInfrastructure "could not stop exact canary PID $($Process.Id): $(Get-VerifierErrorMessage $_)"
    }
}

function ConvertTo-GateBPowerShellLiteral([string]$Value) {
    return [string][char]39 + $Value.Replace([string][char]39, ([string][char]39 +
        [string][char]39)) + [string][char]39
}

function Start-GateBRedirectedProcess([string]$FilePath, [string[]]$Arguments) {
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
            Throw-GateBInfrastructure "Process '$FilePath' did not start."
        }
        return $process
    } catch {
        if (Test-VerifierInfrastructureError $_) { throw }
        try { $process.Dispose() } catch { }
        Throw-GateBInfrastructure ('Could not start redirected process: ' +
            (Get-VerifierErrorMessage $_))
    }
}

function Invoke-GateBBoundedProcess([string]$FilePath, [string[]]$Arguments,
        [int]$TimeoutMilliseconds = 30000, [string]$Label = 'Gate B child') {
    $process = $null
    $terminationProven = $false
    $failure = $null
    $cleanupFailure = $null
    $stdout = ''
    $stderr = ''
    $stdoutTask = $null
    $stderrTask = $null
    $numericExitCode = $null
    $timedOut = $false
    try {
        $process = Start-GateBRedirectedProcess $FilePath $Arguments
        $stdoutTask = $process.StandardOutput.ReadToEndAsync()
        $stderrTask = $process.StandardError.ReadToEndAsync()
        if (-not $process.WaitForExit($TimeoutMilliseconds)) {
            $timedOut = $true
            Throw-GateBInfrastructure "$Label did not terminate within $TimeoutMilliseconds milliseconds."
        }
        $process.Refresh()
        if (-not [bool]$process.HasExited) {
            Throw-GateBInfrastructure "$Label termination was not proven after WaitForExit."
        }
        $terminationProven = $true
        if (-not $stdoutTask.Wait(5000) -or -not $stderrTask.Wait(5000)) {
            Throw-GateBInfrastructure "$Label output streams did not close within the post-termination bound."
        }
        $stdout = [string]$stdoutTask.GetAwaiter().GetResult()
        $stderr = [string]$stderrTask.GetAwaiter().GetResult()
        $process.Refresh()
        if (-not [bool]$process.HasExited) {
            Throw-GateBInfrastructure "$Label changed to an unproven running state while capturing output."
        }
        $rawExitCode = $process.ExitCode
        if (-not (Test-VerifierStrictIntegralValue $rawExitCode `
                ([int]::MinValue) ([int]::MaxValue))) {
            Throw-GateBInfrastructure "$Label did not expose an exact integral exit code."
        }
        $numericExitCode = [int]$rawExitCode
    } catch {
        $failure = $_
    } finally {
        if ($null -ne $process) {
            if (-not $terminationProven) {
                try {
                    Stop-GateBExactProcess $process
                    # Stop-GateBExactProcess returns only after the current
                    # process object has been refreshed, WaitForExit has
                    # succeeded, HasExited is true, and independent absence
                    # proof has passed.  Carry that proof into the typed
                    # timeout result so a caller cannot mistake a generic
                    # timeout exception for a proven parent termination.
                    $terminationProven = $true
                } catch {
                    $cleanupFailure = Get-VerifierErrorMessage $_
                }
            }
            # A timeout/identity/exit-code failure must not dispose a process
            # handle until the redirected streams have been given a bounded
            # chance to close.  If exact termination failed, leave the
            # evidence/handle state unproven and surface infrastructure 2.
            if ($stdoutTask -and $stderrTask) {
                try {
                    if (-not $stdoutTask.Wait(5000) -or -not $stderrTask.Wait(5000)) {
                        $streamMessage = "$Label output streams did not close during cleanup"
                        if ($cleanupFailure) { $cleanupFailure += '; ' + $streamMessage }
                        else { $cleanupFailure = $streamMessage }
                    } else {
                        if ([String]::IsNullOrEmpty($stdout)) {
                            $stdout = [string]$stdoutTask.GetAwaiter().GetResult()
                        }
                        if ([String]::IsNullOrEmpty($stderr)) {
                            $stderr = [string]$stderrTask.GetAwaiter().GetResult()
                        }
                    }
                } catch {
                    $streamMessage = Get-VerifierErrorMessage $_
                    if ($cleanupFailure) { $cleanupFailure += '; ' + $streamMessage }
                    else { $cleanupFailure = $streamMessage }
                }
            }
            try {
                $process.Refresh()
                if (-not [bool]$process.HasExited) {
                    $aliveMessage = "$Label process remained alive during cleanup"
                    if ($cleanupFailure) { $cleanupFailure += '; ' + $aliveMessage }
                    else { $cleanupFailure = $aliveMessage }
                } elseif (-not $process.WaitForExit(0)) {
                    $waitMessage = "$Label process did not prove final WaitForExit"
                    if ($cleanupFailure) { $cleanupFailure += '; ' + $waitMessage }
                    else { $cleanupFailure = $waitMessage }
                }
            } catch {
                $stateMessage = Get-VerifierErrorMessage $_
                if ($cleanupFailure) { $cleanupFailure += '; ' + $stateMessage }
                else { $cleanupFailure = $stateMessage }
            }
            try { $process.Dispose() } catch {
                $disposeMessage = Get-VerifierErrorMessage $_
                if ($cleanupFailure) { $cleanupFailure += '; ' + $disposeMessage }
                else { $cleanupFailure = $disposeMessage }
            }
        }
    }
    if ($cleanupFailure) {
        if ($failure -and $failure.Exception -and $failure.Exception.Data) {
            $failure.Exception.Data['GateBProcessTimedOut'] = [bool]$timedOut
            $failure.Exception.Data['GateBProcessTerminationProven'] = [bool]$terminationProven
        }
        $failureText = if ($failure) { Get-VerifierErrorMessage $failure } else {
            'unknown bounded-process failure'
        }
        Throw-GateBInfrastructure ("$Label cleanup/termination was not proven: " +
            $failureText + '; cleanup: ' + $cleanupFailure)
    }
    if ($failure) {
        if ($failure.Exception -and $failure.Exception.Data) {
            $failure.Exception.Data['GateBProcessTimedOut'] = [bool]$timedOut
            $failure.Exception.Data['GateBProcessTerminationProven'] = [bool]$terminationProven
        }
        if (Test-VerifierInfrastructureError $failure) { throw $failure }
        Throw-GateBInfrastructure ("$Label could not be completed safely: " +
            (Get-VerifierErrorMessage $failure))
    }
    return [pscustomobject]@{
        ExitCode = [int]$numericExitCode
        Stdout = $stdout
        Stderr = $stderr
        TerminationProven = $terminationProven
    }
}

function Assert-GateBContextResourcesReleased($Context) {
    if ($null -eq $Context) { return }
    $unreleased = @($Context.LeaseRecords | Where-Object {
        Test-VerifierLeaseNeedsCleanup $_
    })
    if ($unreleased.Count -ne 0) {
        Throw-GateBInfrastructure ("Cleanup left $($unreleased.Count) owned port lease(s) unreleased.")
    }
    foreach ($lease in @($Context.LeaseRecords)) {
        if (Test-Path -LiteralPath $lease.Path) {
            Throw-GateBInfrastructure "Cleanup left owned claim evidence at '$($lease.Path)'."
        }
        $skipCurrentListenerCheck = ((Test-GateBExactBooleanProperty $lease `
                'MutexReleased' $true) -and
            [string]$lease.ReleaseState -in @('os-released', 'complete', 'claim-delete-failed') -and
            -not (Test-Path -LiteralPath $lease.Path -PathType Leaf -ErrorAction SilentlyContinue))
        if (-not $skipCurrentListenerCheck) {
            $inspection = Get-VerifierLoopbackListenerRecords ([int]$lease.Port)
            if (-not $inspection.Success -or -not $inspection.Known -or $inspection.HasListeners) {
                Throw-GateBInfrastructure "Cleanup could not positively prove that leased port $($lease.Port) is no longer listening."
            }
        }
    }
    foreach ($record in @($Context.BrowserSessions)) {
        if (-not [String]::IsNullOrWhiteSpace([string]$record.Profile) -and
                (Test-Path -LiteralPath $record.Profile)) {
            Throw-GateBInfrastructure "Cleanup left owned browser profile evidence at '$($record.Profile)'."
        }
    }
}

function Remove-GateBCanaryRoots([string]$CanaryRoot, $Contexts) {
    # This function is called only after all exact resource proofs succeed.
    # Each target was created by this canary and no caller/foreign path is
    # resolved from mutable process or repository state here.
    foreach ($context in @($Contexts)) {
        if ($null -eq $context) { continue }
        $verifyRoot = Join-Path ([IO.Path]::GetTempPath()) 'TroubleshootJS\verify'
        if (-not (Test-VerifierChildPath $verifyRoot $context.RunRoot)) {
            Throw-GateBInfrastructure "Refusing to remove a verifier run root outside the isolated temp root."
        }
        if (Test-Path -LiteralPath $context.RunRoot) {
            Remove-VerifierOwnedTree $verifyRoot $context.RunRoot
        }
    }
    if (Test-Path -LiteralPath $CanaryRoot) {
        $tempRoot = Get-VerifierFullPath ([IO.Path]::GetTempPath())
        Remove-VerifierOwnedTree $tempRoot (Get-VerifierFullPath $CanaryRoot)
    }
}

function Invoke-GateBArgumentPathCanary([string]$CanaryRoot) {
    $argumentRoot = Join-Path $CanaryRoot 'argument worktree with spaces'
    $profileRoot = Join-Path $argumentRoot 'profile with spaces'
    $scriptPath = Join-Path $argumentRoot 'argument child script with spaces.ps1'
    $stdoutPath = Join-Path $argumentRoot 'argument child stdout.txt'
    $stderrPath = Join-Path $argumentRoot 'argument child stderr.txt'
    $markerPath = Join-Path $profileRoot 'received-argument.txt'
    $failureRecord = $null
    try {
        New-Item -ItemType Directory -Path $profileRoot -Force -ErrorAction Stop | Out-Null
        $scriptText = [string]::Join([Environment]::NewLine, @(
            'param([string]$ProfilePath)'
            '$ErrorActionPreference = ''Stop'''
            'if ([String]::IsNullOrWhiteSpace($ProfilePath)) { throw ''profile argument was empty'' }'
            'New-Item -ItemType Directory -Path $ProfilePath -Force -ErrorAction Stop | Out-Null'
            '[IO.File]::WriteAllText((Join-Path $ProfilePath ''received-argument.txt''), [IO.Path]::GetFullPath($ProfilePath))'
            'Write-Output (''ARGUMENT_CANARY_PROFILE='' + [IO.Path]::GetFullPath($ProfilePath))'
        ))
        [IO.File]::WriteAllText($scriptPath, $scriptText, [Text.UTF8Encoding]::new($false))
        $powershell = (Get-Command powershell.exe -ErrorAction Stop).Source
        # Use the same shared Windows argument builder through the redirected
        # ProcessStartInfo path so this canary proves a real launch with both
        # the script path and profile path containing spaces.
        $argumentResult = Invoke-GateBBoundedProcess $powershell @(
            '-NoProfile', '-ExecutionPolicy', 'Bypass', '-File', $scriptPath,
            '-ProfilePath', $profileRoot) 10000 'spaces-path argument canary'
        $argumentStdout = $argumentResult.Stdout
        $argumentStderr = $argumentResult.Stderr
        [IO.File]::WriteAllText($stdoutPath, [string]$argumentStdout,
            [Text.UTF8Encoding]::new($false))
        [IO.File]::WriteAllText($stderrPath, [string]$argumentStderr,
            [Text.UTF8Encoding]::new($false))
        $observedProcessExit = $argumentResult.ExitCode
        if ([int]$observedProcessExit -ne 0) {
            Throw-GateBInfrastructure ('spaces-path argument canary returned exit ' + [string]$observedProcessExit)
        }
        if (-not (Test-Path -LiteralPath $markerPath -PathType Leaf)) {
            Throw-GateBInfrastructure 'spaces-path argument canary did not receive its profile path'
        }
        if ([IO.File]::ReadAllText($markerPath) -ne [IO.Path]::GetFullPath($profileRoot)) {
            Throw-GateBInfrastructure 'spaces-path argument canary received a corrupted profile path'
        }
        Remove-VerifierOwnedTree $CanaryRoot $argumentRoot
        if (Test-Path -LiteralPath $argumentRoot) {
            Throw-GateBInfrastructure 'spaces-path argument canary left its exact temporary root behind'
        }
    } catch {
        $failureRecord = $_
    } finally { }
    if ($null -ne $failureRecord) {
        if (Test-VerifierInfrastructureError $failureRecord) { throw $failureRecord }
        Throw-GateBInfrastructure ('spaces-path argument canary failed: ' +
            (Get-VerifierErrorMessage $failureRecord))
    }
    Write-Host 'PASS:PS5.1 quoted spaces-path process argument canary'
}

function Invoke-GateBCommandLineRoundTripCheck() {
    $profile = Join-Path ([IO.Path]::GetTempPath()) 'TroubleshootJS\round-trip profile with spaces'
    $previewScript = Join-Path ([IO.Path]::GetTempPath()) 'TroubleshootJS\preview script with spaces.ps1'
    $runId = [Guid]::NewGuid().ToString('N')
    $nonce = [Guid]::NewGuid().ToString('N')
    $port = 49123
    $processIdentityProcess = Get-Process -Id $PID -ErrorAction Stop
    $processStartTicks = [long](Get-VerifierProcessStartTicks $processIdentityProcess)

    $previewRecord = [pscustomobject]@{
        ProcessId = $PID; ProcessStartTicks = $processStartTicks
        Script = $previewScript; Port = $port; RunId = $runId; Nonce = $nonce
    }
    $previewCommand = 'powershell.exe ' + (ConvertTo-VerifierArgumentString @(
        '-File', $previewScript,
        '-Port', [string]$port,
        '-VerifierRunId', $runId,
        '-VerifierNonce', $nonce))
    $previewSnapshot = @([pscustomobject]@{
        ProcessId = $PID; ProcessStartTicks = $processStartTicks; CommandLine = $previewCommand
    })
    Assert-GateB (Test-VerifierPreviewProcessIdentity $previewRecord $previewSnapshot) `
        'quoted preview command-line round-trip was not recognized'
    Assert-GateB (Test-VerifierPreviewProcessIdentity $previewRecord @(
        [pscustomobject]@{ ProcessId = 0 }
        [pscustomobject]@{ ProcessId = 4 }
        $previewSnapshot[0]
    )) `
        'complete preview ownership snapshot rejected unrelated PID0/PID4 OS records'
    $invalidPreviewPids = New-Object Collections.ArrayList
    [void]$invalidPreviewPids.Add('' + [string]$PID)
    [void]$invalidPreviewPids.Add([double]($PID + 0.5))
    [void]$invalidPreviewPids.Add($true)
    [void]$invalidPreviewPids.Add(@($PID))
    [void]$invalidPreviewPids.Add($null)
    [void]$invalidPreviewPids.Add(4)
    foreach ($invalidPreviewPid in @($invalidPreviewPids)) {
        $malformedPreviewRecord = $previewRecord | Select-Object *
        $malformedPreviewRecord.ProcessId = $invalidPreviewPid
        Assert-GateB (-not (Test-VerifierPreviewProcessIdentity $malformedPreviewRecord `
            $previewSnapshot)) `
            'preview identity accepted a malformed, PID-only, or PID4 process identity'
    }
    $stalePreviewRecord = $previewRecord | Select-Object *
    $stalePreviewStart = if ($processStartTicks -eq [long]::MaxValue) {
        $processStartTicks - 1L
    } else { $processStartTicks + 1L }
    $stalePreviewRecord.ProcessStartTicks = $stalePreviewStart
    $stalePreviewSnapshot = @([pscustomobject]@{
        ProcessId = $PID; ProcessStartTicks = $stalePreviewStart; CommandLine = $previewCommand
    })
    Assert-GateB (-not (Test-VerifierPreviewProcessIdentity $stalePreviewRecord `
        $stalePreviewSnapshot)) `
        'preview identity accepted a stale process start identity'
    Assert-GateB (-not (Test-VerifierPreviewProcessIdentity $previewRecord `
        @($previewSnapshot[0], $previewSnapshot[0]))) `
        'preview identity accepted duplicate matching process records'
    $malformedPreviewSnapshot = @([pscustomobject]@{
        ProcessId = [string]$PID; ProcessStartTicks = $processStartTicks
        CommandLine = $previewCommand
    })
    Assert-GateB (-not (Test-VerifierPreviewProcessIdentity $previewRecord `
        $malformedPreviewSnapshot)) `
        'preview identity accepted a numeric-string snapshot PID'
    $equivalentPreviewScript = (Split-Path -Parent $previewScript).ToUpperInvariant().Replace('\', '/') +
        '/./' + (Split-Path -Leaf $previewScript)
    $equivalentPreviewCommand = 'powershell.exe ' + (ConvertTo-VerifierArgumentString @(
        '-File', $equivalentPreviewScript,
        '-Port', [string]$port,
        '-VerifierRunId', $runId,
        '-VerifierNonce', $nonce))
    Assert-GateB (Test-VerifierCommandLineEquivalent $equivalentPreviewCommand $previewCommand) `
        'equivalent canonical preview command-line paths were not accepted'
    $previewRecordedCurrent = [pscustomobject]@{
        ProcessId = [int]$PID; ParentProcessId = 7; ProcessStartTicks = $processStartTicks
        CommandLine = $previewCommand
    }
    foreach ($previewReplacement in @(
            [pscustomobject]@{ ProcessId = [int]$PID; ParentProcessId = 7; ProcessStartTicks = ($processStartTicks + 1); CommandLine = $previewCommand }
            [pscustomobject]@{ ProcessId = [int]$PID; ParentProcessId = 8; ProcessStartTicks = $processStartTicks; CommandLine = $previewCommand }
            [pscustomobject]@{ ProcessId = [int]$PID; ParentProcessId = 7; ProcessStartTicks = $processStartTicks; CommandLine = $equivalentPreviewCommand.Replace('-VerifierNonce', '-evil-VerifierNonce') }
        )) {
        Assert-GateB (-not (Test-VerifierCurrentProcessRecordMatches $previewRecordedCurrent $previewReplacement)) `
            'run-owned preview PID replacement/mismatch was accepted for current-object cleanup'
    }

    $browserRecord = [pscustomobject]@{
        ProcessId = $PID; ProcessStartTicks = $processStartTicks; Profile = $profile
        RunId = $runId; RepositoryIdentity = 'roundtrip-repository'; CdpPort = $port
        BrowserPath = 'C:\Program Files\Microsoft Edge\Application\msedge.exe'
    }
    $browserCommand = 'msedge.exe ' + (ConvertTo-VerifierArgumentString @(
        '--user-data-dir', $profile,
        '--tsj-verifier-run', $runId,
        '--tsj-verifier-worktree', $browserRecord.RepositoryIdentity,
        '--remote-debugging-port', [string]$port))
    $browserSnapshot = @([pscustomobject]@{
        ProcessId = $PID; ParentProcessId = 1; Name = 'msedge.exe'
        ExecutablePath = $browserRecord.BrowserPath
        CommandLine = $browserCommand
    })
    Assert-GateB (Test-VerifierProcessIdentity $browserRecord $browserSnapshot) `
        'quoted browser command-line round-trip was not recognized'
    Write-Host 'PASS:shared Windows argument command-line identity round-trip'
}

function Invoke-GateBStopPreviewContractCanary([string]$CanaryRoot) {
    $stateDirectory = Join-Path $repositoryRoot '.tools\preview'
    $statePath = Join-Path $stateDirectory `
        ('gate-b-stop-preview-' + [Guid]::NewGuid().ToString('N') + '.json')
    $stateCreated = $false
    $failure = $null
    try {
        New-Item -ItemType Directory -Path $stateDirectory -Force -ErrorAction Stop | Out-Null
        $stateCreated = $true
        $repositoryEquivalent = (Get-VerifierFullPath $repositoryRoot).ToUpperInvariant().Replace('\', '/') + '/./'
        $previewScript = Get-VerifierFullPath (Join-Path $repositoryRoot 'scripts\preview.ps1')
        $scriptEquivalent = $previewScript.ToUpperInvariant().Replace('\', '/') + '/./'
        $parentProcess = Get-Process -Id $PID -ErrorAction Stop
        $parentStart = [long](Get-VerifierProcessStartTicks $parentProcess)
        $state = [ordered]@{
            repositoryRoot = $repositoryEquivalent
            previewScript = $scriptEquivalent
            processId = [int]$PID
            processStartTicks = $parentStart
            processParentProcessId = 1
            processCommandLine = 'powershell.exe -File "' + $previewScript + '" -Port 49321'
            port = 49321
        }
        [IO.File]::WriteAllText($statePath, ($state | ConvertTo-Json -Depth 8),
            [Text.UTF8Encoding]::new($false))

        $powershell = (Get-Command powershell.exe -ErrorAction Stop).Source
        $stopResult = Invoke-GateBBoundedProcess $powershell @(
            '-NoProfile', '-ExecutionPolicy', 'Bypass', '-File',
            (Join-Path $PSScriptRoot 'stop-preview.ps1'), '-StateFile', $statePath) `
            15000 'stop-preview equivalent-path mismatch canary'
        Assert-GateB ($stopResult.TerminationProven -and [int]$stopResult.ExitCode -eq 2) `
            ('stop-preview mismatch canary did not prove bounded infrastructure exit 2: exit=' +
                [string]$stopResult.ExitCode + ' stdout=' + [string]$stopResult.Stdout +
                ' stderr=' + [string]$stopResult.Stderr)
        Assert-GateB (Test-Path -LiteralPath $statePath -PathType Leaf) `
            'stop-preview mismatch canary deleted state without current ownership proof'

        $prefixPreview = [pscustomobject]@{
            ProcessId = [int]$PID; ProcessStartTicks = $parentStart
            Script = $previewScript; Port = 49321
            RunId = 'stop-preview-run'; Nonce = 'stop-preview-nonce'
        }
        $prefixCommand = 'powershell.exe --evil-File="' + $previewScript +
            '" --evil-Port=49321 --evil-VerifierRunId=stop-preview-run ' +
            '--evil-VerifierNonce=stop-preview-nonce'
        Assert-GateB (-not (Test-VerifierPreviewProcessIdentity $prefixPreview `
            @([pscustomobject]@{
                ProcessId = [int]$PID; ProcessStartTicks = $parentStart; CommandLine = $prefixCommand
            }))) `
            'stop-preview foreign prefix markers passed exact identity'

        $recorded = [pscustomobject]@{
            ProcessId = 493; ParentProcessId = 492; ProcessStartTicks = 17L
            CommandLine = 'powershell.exe -File "' + $previewScript + '" -Port 49321'
        }
        foreach ($replacement in @(
                [pscustomobject]@{ ProcessId = 493; ParentProcessId = 492; ProcessStartTicks = 18L; CommandLine = $recorded.CommandLine }
                [pscustomobject]@{ ProcessId = 493; ParentProcessId = 491; ProcessStartTicks = 17L; CommandLine = $recorded.CommandLine }
                [pscustomobject]@{ ProcessId = 493; ParentProcessId = 492; ProcessStartTicks = 17L; CommandLine = 'powershell.exe --evil-File="foreign.ps1" -Port 49321' }
            )) {
            Assert-GateB (-not (Test-VerifierCurrentProcessRecordMatches $recorded $replacement)) `
                'stop-preview PID replacement/mismatch passed current-object identity proof'
        }
        Assert-GateB ($stopResult.Stdout -notmatch '(?i)Stopped TroubleshootJS preview') `
            'stop-preview mismatch canary printed a successful stop without proof'
    } catch {
        $failure = $_
    } finally {
        if ($null -eq $failure -and $stateCreated -and
                (Test-Path -LiteralPath $statePath -PathType Leaf)) {
            try {
                Remove-VerifierOwnedTree (Split-Path -Parent $statePath) $statePath
            } catch { $failure = $_ }
        }
    }
    if ($null -ne $failure) {
        Throw-GateBInfrastructure ('stop-preview ownership/retention canary failed; state retained at ' +
            $statePath + ': ' + (Get-VerifierErrorMessage $failure))
    }
    Write-Host 'PASS:stop-preview canonical paths, prefix rejection, PID replacement, and post-stop proof canary'
}

function Invoke-GateBIsolationCanary() {
    $canaryId = [Guid]::NewGuid().ToString('N')
    $canaryRoot = Join-Path ([IO.Path]::GetTempPath()) ('TroubleshootJS\gate-b-canary-' + $canaryId)
    try {
        New-Item -ItemType Directory -Path $canaryRoot -Force -ErrorAction Stop | Out-Null
    } catch {
        Throw-GateBInfrastructure ('could not create isolation canary root: ' +
            (Get-VerifierErrorMessage $_))
    }
    $contextA = $null
    $contextB = $null
    $worktreeBRoot = Join-Path $canaryRoot 'worktree-B with spaces'
    $leaseA = $null
    $leaseB = $null
    $recordA = $null
    $recordB = $null
    $bindListener = $null
    $competitionProcess = $null
    $competitionScriptPath = Join-Path $worktreeBRoot 'cross-worktree-child script.ps1'
    $competitionExitCode = $null
    $competitionTerminationProven = $false
    $childRunRoot = $null
    $childCleanupJsonPath = $null
    $childCleanupProven = $false
    $competitionOutputPath = Join-Path $canaryRoot 'cross-worktree-child.stdout.txt'
    $competitionErrorPath = Join-Path $canaryRoot 'cross-worktree-child.stderr.txt'
    $foreignListenerProcess = $null
    $foreignProfileProcess = $null
    $checksValidated = $false
    $canarySucceeded = $false
    $primaryFailure = $null
    $browserPath = ''
    $rootScript = Join-Path $canaryRoot 'isolation-browser-root.vbs'
    $rootProcessA = $null
    $rootProcessB = $null
    $rootIdentityA = $null
    $rootIdentityB = $null
    $profileInspectionAvailable = $true
    $profileInspectionError = ''
    try {
        # Use a real WMI-visible Windows Script Host process as the synthetic
        # browser root. It keeps the deterministic isolation canary independent
        # of the supplemental live-Edge lane while still exercising the exact
        # root executable/parent/start/profile/port ownership proof.
        $browserPath = (Get-Command wscript.exe -ErrorAction Stop).Source
        Invoke-GateBArgumentPathCanary $canaryRoot
        try { [void](Get-CimInstance Win32_Process -ErrorAction Stop) } catch {
            $profileInspectionAvailable = $false
            $profileInspectionError = $_.Exception.Message
        }
        try {
            New-Item -ItemType Directory -Path $worktreeBRoot -Force -ErrorAction Stop | Out-Null
        } catch {
            Throw-GateBInfrastructure ('could not create competing worktree root: ' +
                (Get-VerifierErrorMessage $_))
        }
        $contextA = New-VerifierRunContext $repositoryRoot $canaryRoot
        $contextB = New-VerifierRunContext $worktreeBRoot $canaryRoot
        $leaseA = New-VerifierPortLease $contextA 'canary-retained-bind'
        $recordA = New-GateBCanaryBrowserRecord $contextA $leaseA 'canary-timeout-cleanup-A' $browserPath
        [IO.File]::WriteAllText($rootScript,
            [string]::Join([Environment]::NewLine, @(
                'Option Explicit'
                'WScript.Sleep 120000'
            )), [Text.UTF8Encoding]::new($false))
        Assert-VerifierNoReparseAncestors $rootScript
        if (-not (Test-VerifierPhysicalChildPath $canaryRoot $rootScript)) {
            Throw-GateBInfrastructure 'isolation browser-root fixture escaped its exact canary namespace'
        }
        Assert-GateB ($leaseA.ClaimState -eq 'held') 'new port claim was not retained before bind'
        Assert-GateB (Test-Path -LiteralPath $leaseA.Path -PathType Leaf) `
            'retained port claim record was not created'

        # Bind a real loopback listener while the named claim is held, then
        # validate that the claim records the actual owning process identity.
        $bindListener = [Net.Sockets.TcpListener]::new([Net.IPAddress]::Loopback, $leaseA.Port)
        $bindListener.Start()
        Confirm-VerifierPortLeaseBound $contextA $leaseA $PID `
            ([long](Get-VerifierCurrentProcessStartTicks))
        Assert-GateB ($leaseA.ClaimState -eq 'bound' -and $leaseA.BoundProcessId -eq $PID) `
            'retained claim did not record successful bind identity'
        $manifestA = Get-Content -LiteralPath $contextA.ManifestPath -Raw | ConvertFrom-Json
        $manifestLeaseA = @($manifestA.leases | Where-Object { [int]$_.port -eq [int]$leaseA.Port })
        Assert-GateB ($manifestLeaseA.Count -eq 1 -and
            [string]$manifestLeaseA[0].claimState -eq 'bound' -and
            [int]$manifestLeaseA[0].boundProcessId -eq $PID) `
            'run manifest did not retain claim/bind ownership state'
        Stop-GateBListenerExact $bindListener $leaseA.Port
        $bindListener = $null

        # A separate process is listening on the claimed port, but the
        # verifier is given this process's identity. Exact listener PID/start
        # validation must reject the foreign listener as infrastructure.
        $foreignListenerProcess = Start-GateBSeparateListener $leaseA.Port
        $foreignRejected = $false
        try {
            Confirm-VerifierPortLeaseBound $contextA $leaseA $PID `
                ([long](Get-VerifierCurrentProcessStartTicks))
        } catch {
            $foreignRejected = Test-VerifierInfrastructureError $_
        }
        Assert-GateB $foreignRejected `
            'separate foreign listener was accepted as the owned port bind'
        Stop-GateBExactProcess $foreignListenerProcess $leaseA.Port
        $foreignListenerProcess = $null

        # Compete from a separate PowerShell process representing a different
        # worktree. Its real process exit must be `2`, not merely a diagnostic
        # string, when the global per-port claim rejects the exact request.
        try {
            $powershell = (Get-Command powershell.exe -ErrorAction Stop).Source
        } catch {
            Throw-GateBInfrastructure ('could not locate PowerShell for cross-worktree canary: ' +
                (Get-VerifierErrorMessage $_))
        }
        $moduleLiteral = ConvertTo-GateBPowerShellLiteral $modulePath
        $worktreeLiteral = ConvertTo-GateBPowerShellLiteral $worktreeBRoot
        $competitionCommand = [string]::Join(' ', @(
            '$ErrorActionPreference = ''Stop'';'
            ('Import-Module ' + $moduleLiteral + ' -Force;')
            '$childExit = 2; $childContext = $null; $competingLease = $null;'
            '$childCleanupErrors = New-Object Collections.ArrayList; $childCleanup = $null;'
            '$childCleanupJsonPath = ''''; $childRunRoot = '''';'
            'try { $childContext = New-VerifierRunContext ' + $worktreeLiteral + ' '''';'
            ' $competingLease = New-VerifierPortLease $childContext ''canary-competing'' ' +
                [string]$leaseA.Port + ';'
            ' Write-Output ''ACQUIRED:exit=0''; $childExit = 0; }'
            ' catch { if (Test-VerifierInfrastructureError $_) {'
            ' Write-Output (''BLOCKED:exit=2:'' + $_.Exception.Message); $childExit = 2; }'
            ' else { Write-Output (''FAILED:exit=1:'' + $_.Exception.Message); $childExit = 1; } }'
            ' finally { if ($null -ne $competingLease) {'
            ' try { Release-VerifierPortLease $childContext $competingLease }'
            ' catch { $childExit = 2; [void]$childCleanupErrors.Add((''lease-release: '' + $_.Exception.Message)) } };'
            ' if ($null -ne $childContext) { $childRunRoot = $childContext.RunRoot;'
            ' try { $childCleanup = Complete-VerifierRun $childContext;'
            ' if ($null -eq $childCleanup -or -not [bool]$childCleanup.Success) {'
            ' $childExit = 2; if ($childCleanup) { [void]$childCleanupErrors.Add((''complete: '' + (@($childCleanup.Errors) -join ''; ''))) }'
            ' else { [void]$childCleanupErrors.Add(''complete: no result'') } }'
            ' } catch { $childExit = 2; [void]$childCleanupErrors.Add((''complete-exception: '' + $_.Exception.Message)) };'
            ' try { $childManifest = Get-Content -LiteralPath $childContext.ManifestPath -Raw | ConvertFrom-Json;'
            ' $childClaims = @($childManifest.leases); $childProfiles = @($childManifest.browserSessions | ForEach-Object { $_.profile });'
            ' $portInspection = Get-VerifierLoopbackListenerRecords ' + [string]$leaseA.Port + ';'
            ' $childCleanupSuccess = ($childCleanupErrors.Count -eq 0 -and $childCleanup -and [bool]$childCleanup.Success -and'
            ' $childManifest.cleanup.state -eq ''complete'' -and @($childManifest.cleanup.errors).Count -eq 0 -and'
            ' @($childClaims | Where-Object { $_.status -ne ''released'' }).Count -eq 0 -and'
            ' $portInspection.Success -and $portInspection.Known -and -not $portInspection.HasListeners);'
            ' if (-not $childCleanupSuccess) { $childExit = 2; [void]$childCleanupErrors.Add(''manifest/port cleanup proof failed'') }'
            ' $childCleanupRecord = [pscustomobject]@{ protocol = ''troubleshootjs-child-cleanup-v1''; success = [bool]$childCleanupSuccess;'
            ' runRoot = $childRunRoot; manifestPath = $childContext.ManifestPath; leaseLedger = $childClaims;'
            ' claimPaths = @($childClaims | Where-Object { $_.status -ne ''released'' } | ForEach-Object { $_.path }); profiles = $childProfiles;'
            ' requestedPort = ' + [string]$leaseA.Port + '; requestedPortInspectionSuccess = [bool]$portInspection.Success;'
            ' requestedPortKnown = [bool]$portInspection.Known; requestedPortHasListeners = [bool]$portInspection.HasListeners;'
            ' errors = @($childCleanupErrors) };'
            ' $childCleanupJsonPath = Join-Path $childContext.EvidenceDirectory ''child-cleanup.json'';'
            ' [IO.File]::WriteAllText($childCleanupJsonPath, ($childCleanupRecord | ConvertTo-Json -Depth 8), [Text.UTF8Encoding]::new($false));'
            ' } catch { $childExit = 2; [void]$childCleanupErrors.Add((''cleanup-proof-exception: '' + $_.Exception.Message)) } } }'
            ' Write-Output (''RUNROOT='' + $childRunRoot); Write-Output (''CLEANUP_JSON='' + $childCleanupJsonPath);'
            ' Write-Output (''CLEANUP_SUCCESS='' + [string]($childCleanupErrors.Count -eq 0 -and $childCleanup -and [bool]$childCleanup.Success));'
            ' if ($childCleanupErrors.Count -gt 0) { Write-Output (''CLEANUP_ERRORS='' + (@($childCleanupErrors) -join '' | '')) }'
            ' exit $childExit'
        ))
        try {
            [IO.File]::WriteAllText($competitionScriptPath, $competitionCommand,
                [Text.UTF8Encoding]::new($false))
            $competitionResult = Invoke-GateBBoundedProcess $powershell @(
                '-NoProfile', '-ExecutionPolicy', 'Bypass', '-File', $competitionScriptPath) `
                30000 'cross-worktree competition process'
            $competitionTerminationProven = [bool]$competitionResult.TerminationProven
            $competitionOutput = [string]$competitionResult.Stdout
            $competitionError = [string]$competitionResult.Stderr
            $competitionExitCode = [int]$competitionResult.ExitCode
            [IO.File]::WriteAllText($competitionOutputPath, [string]$competitionOutput,
                [Text.UTF8Encoding]::new($false))
            [IO.File]::WriteAllText($competitionErrorPath, [string]$competitionError,
                [Text.UTF8Encoding]::new($false))
        } catch {
            if (Test-VerifierInfrastructureError $_) { throw }
            Throw-GateBInfrastructure ('could not capture cross-worktree competition result: ' +
                (Get-VerifierErrorMessage $_))
        }
        try {
            if ([String]::IsNullOrWhiteSpace([string]$competitionOutput)) {
                Throw-GateBInfrastructure ('cross-worktree competition produced no stdout evidence. ' +
                    [string]$competitionError)
            }
        } catch {
            if (Test-VerifierInfrastructureError $_) { throw }
            Throw-GateBInfrastructure ('could not validate cross-worktree competition evidence: ' +
                (Get-VerifierErrorMessage $_))
        }
        if ([int]$competitionExitCode -ne 2) {
            Throw-GateBInfrastructure ('cross-worktree child expected infrastructure exit 2 but returned ' +
                [string]$competitionExitCode + ': ' + [string]$competitionOutput)
        }
        if ($competitionOutput -notmatch '(?i)BLOCKED:exit=2:') {
            Throw-GateBInfrastructure 'cross-worktree competing process did not emit its infrastructure classification marker'
        }
        $childRunRootMatch = [regex]::Match($competitionOutput, '(?m)^RUNROOT=(.+)$')
        if (-not $childRunRootMatch.Success) {
            Throw-GateBInfrastructure 'competing child did not report its run root for exact canary cleanup'
        }
        $childRunRoot = $childRunRootMatch.Groups[1].Value.Trim()
        $verifyRoot = Join-Path ([IO.Path]::GetTempPath()) 'TroubleshootJS\verify'
        if ([String]::IsNullOrWhiteSpace($childRunRoot) -or
                -not (Test-VerifierChildPath $verifyRoot $childRunRoot)) {
            Throw-GateBInfrastructure 'competing child run root escaped the verifier temp root or was empty'
        }
        $childCleanupJsonMatch = [regex]::Match($competitionOutput, '(?m)^CLEANUP_JSON=(.+)$')
        if (-not $childCleanupJsonMatch.Success) {
            Throw-GateBInfrastructure 'competing child did not report structured cleanup evidence'
        }
        $childCleanupJsonPath = $childCleanupJsonMatch.Groups[1].Value.Trim()
        if (-not (Test-VerifierChildPath $childRunRoot $childCleanupJsonPath) -or
                -not (Test-Path -LiteralPath $childCleanupJsonPath -PathType Leaf)) {
            Throw-GateBInfrastructure 'competing child cleanup evidence was outside its run root or missing'
        }
        try {
            $childCleanupRecord = Get-Content -LiteralPath $childCleanupJsonPath -Raw | ConvertFrom-Json
        } catch {
            Throw-GateBInfrastructure ('could not parse competing child cleanup evidence: ' +
                (Get-VerifierErrorMessage $_))
        }
        if ([String]::IsNullOrWhiteSpace([string]$childCleanupRecord.runRoot) -or
                -not (Test-VerifierChildPath $verifyRoot ([string]$childCleanupRecord.runRoot))) {
            Throw-GateBInfrastructure 'competing child cleanup record reported an empty or foreign run root'
        }
        if ([string]$childCleanupRecord.protocol -ne 'troubleshootjs-child-cleanup-v1' -or
                -not [bool]$childCleanupRecord.success -or
                -not (Test-VerifierCanonicalWindowsPathValue ([string]$childCleanupRecord.runRoot) $childRunRoot) -or
                [int]$childCleanupRecord.requestedPort -ne [int]$leaseA.Port -or
                @($childCleanupRecord.errors).Count -ne 0 -or
                -not [bool]$childCleanupRecord.requestedPortInspectionSuccess -or
                -not [bool]$childCleanupRecord.requestedPortKnown -or
                [bool]$childCleanupRecord.requestedPortHasListeners -or
                @($childCleanupRecord.claimPaths).Count -ne 0 -or
                @($childCleanupRecord.leaseLedger | Where-Object { $_.status -ne 'released' }).Count -ne 0) {
            Throw-GateBInfrastructure 'competing child cleanup ledger did not prove complete owned-resource release'
        }
        foreach ($profilePath in @($childCleanupRecord.profiles)) {
            if (-not [String]::IsNullOrWhiteSpace([string]$profilePath) -and
                    (Test-Path -LiteralPath ([string]$profilePath))) {
                Throw-GateBInfrastructure ('competing child cleanup left profile evidence behind: ' +
                    [string]$profilePath)
            }
        }
        $childLeaseDirectory = Join-Path $childRunRoot 'port-leases'
        if (-not (Test-Path -LiteralPath $childLeaseDirectory -PathType Container)) {
            Throw-GateBInfrastructure 'competing child cleanup did not retain its expected lease directory for inspection'
        }
        $childClaimFiles = @(Get-ChildItem -LiteralPath $childLeaseDirectory -Filter '*.lease' -File -ErrorAction Stop)
        if ($childClaimFiles.Count -ne 0) {
            Throw-GateBInfrastructure 'competing child cleanup left an unrecorded claim file behind'
        }
        $childBrowserRoot = Join-Path $childRunRoot 'browser'
        if (Test-Path -LiteralPath $childBrowserRoot -PathType Container) {
            $childProfileDirectories = @(Get-ChildItem -LiteralPath $childBrowserRoot -Directory -Recurse -Force -ErrorAction Stop |
                Where-Object { $_.Name -eq 'profile' })
            if ($childProfileDirectories.Count -ne 0) {
                Throw-GateBInfrastructure 'competing child cleanup left an unrecorded browser profile behind'
            }
        }
        $childManifestPath = [string]$childCleanupRecord.manifestPath
        if (-not (Test-VerifierChildPath $childRunRoot $childManifestPath) -or
                -not (Test-Path -LiteralPath $childManifestPath -PathType Leaf)) {
            Throw-GateBInfrastructure 'competing child cleanup did not retain a verifiable manifest path'
        }
        $childManifest = Get-Content -LiteralPath $childManifestPath -Raw | ConvertFrom-Json
        if ([string]$childManifest.cleanup.state -ne 'complete' -or
                @($childManifest.cleanup.errors).Count -ne 0) {
            Throw-GateBInfrastructure 'competing child manifest did not record complete cleanup'
        }
        $childCleanupProven = $true

        $leaseB = New-VerifierPortLease $contextB 'canary-foreign-resource-B'
        $recordB = New-GateBCanaryBrowserRecord $contextB $leaseB 'canary-foreign-resource-B' $browserPath
        Assert-GateB ($contextA.RunId -ne $contextB.RunId) 'run IDs collided'
        Assert-GateB ($contextA.RunRoot -ne $contextB.RunRoot) 'run roots collided'
        Assert-GateB ($contextA.EvidenceDirectory -ne $contextB.EvidenceDirectory) 'evidence roots collided'
        Assert-GateB ($contextA.ManifestPath -ne $contextB.ManifestPath) 'manifests collided'
        Assert-GateB ($contextA.WorktreeRoot -ne $contextB.WorktreeRoot -and
            $contextA.RepositoryIdentity -ne $contextB.RepositoryIdentity) `
            'canary worktree identities did not differ'
        Assert-GateB ($leaseA.Port -ne $leaseB.Port) 'independent ports collided'
        Assert-GateB ($recordA.Profile -ne $recordB.Profile) 'browser profiles collided'

        $foreignEvidence = Join-Path $contextB.EvidenceDirectory 'foreign-run-marker.txt'
        [IO.File]::WriteAllText($foreignEvidence, $contextB.RunId,
            [Text.UTF8Encoding]::new($false))
        Register-VerifierEvidenceArtifact $contextB $foreignEvidence
        [IO.File]::WriteAllText((Join-Path $recordB.Profile 'foreign-profile-marker.txt'),
            $contextB.RunId, [Text.UTF8Encoding]::new($false))
        $manifestBBefore = [IO.File]::ReadAllText($contextB.ManifestPath)
        Write-Host 'INFO:global claim, cross-worktree contention, and foreign-listener checks complete; cleanup pending'

        if (-not $profileInspectionAvailable) {
            Throw-GateBInfrastructure ('browser profile ownership canary requires complete ' +
                'Win32_Process command-line/start-identity inspection; this host returned: ' +
                $profileInspectionError)
        }

        # A models a startup/cleanup failure after a real browser root was
        # recorded. The root stays alive until Complete-VerifierBrowserSession
        # proves the exact current process and full descendant graph, so this
        # path exercises the same fail-closed ownership contract as a real
        # browser session.
        $rootProcessA = Start-VerifierProcess $browserPath @(
            '//B', $rootScript,
            '--user-data-dir', $recordA.Profile,
            '--tsj-verifier-run', $contextA.RunId,
            '--tsj-verifier-worktree', $contextA.RepositoryIdentity,
            '--remote-debugging-port', [string]$recordA.CdpPort)
        $recordA.Runtime.Browser = $rootProcessA
        $recordA.ProcessId = [int]$rootProcessA.Id
        $recordA.Status = 'started'
        Write-VerifierManifest $contextA
        $rootStartA = Get-VerifierProcessStartTicks $rootProcessA
        $rootIdentityA = Get-VerifierCurrentProcessIdentity $rootProcessA.Id $rootStartA `
            0 '' '' 0 $contextA.RunId '' 0 $browserPath
        $recordA.ProcessStartTicks = $rootStartA
        $recordA.ProcessParentProcessId = [int]$rootIdentityA.Record.ParentProcessId
        $recordA.ProcessParentProcessStartTicks = [long]$rootIdentityA.Record.ParentProcessStartTicks
        $recordA.ProcessCommandLine = [string]$rootIdentityA.Record.CommandLine
        Write-VerifierManifest $contextA

        # Cleanup can remove only A's profile and retained claim; it must not
        # adopt B's evidence/profile.
        $recordA.Status = 'startup-failed'
        $recordA.Error = 'deterministic canary timeout'
        Write-VerifierManifest $contextA
        Complete-VerifierBrowserSession $contextA $recordA
        Assert-GateB (Test-Path -LiteralPath $foreignEvidence -PathType Leaf) `
            'cleanup of run A deleted run B evidence'
        Assert-GateB (Test-Path -LiteralPath $recordB.Profile -PathType Container) `
            'cleanup of run A deleted run B browser profile'
        Assert-GateB (Test-Path -LiteralPath $contextB.ManifestPath -PathType Leaf) `
            'cleanup of run A deleted run B manifest'
        Assert-GateB (Test-Path -LiteralPath $leaseB.Path -PathType Leaf) `
            'cleanup of run A released run B port claim'
        Assert-GateB (-not (Test-Path -LiteralPath $recordA.Profile)) `
            'owned run A profile was not cleaned'
        Assert-GateB (-not (Test-Path -LiteralPath $leaseA.Path)) `
            'owned run A port claim was not released'
        Assert-GateB ([IO.File]::ReadAllText($contextB.ManifestPath) -eq $manifestBBefore) `
            'cleanup of run A changed run B manifest state'

        # Keep B's recorded PID stale/zero and leave a separate process
        # referencing B's profile. Cleanup must refuse deletion and lease
        # release while that process exists.
        $foreignProfileProcess = Start-GateBForeignProfileProcess $recordB.Profile
        $profileRejected = $false
        try {
            Complete-VerifierBrowserSession $contextB $recordB
        } catch {
            $profileRejected = Test-VerifierInfrastructureError $_
        }
        Assert-GateB $profileRejected `
            'stale-recorded-PID cleanup adopted a foreign profile process'
        Assert-GateB (Test-Path -LiteralPath $recordB.Profile -PathType Container) `
            'profile was deleted while a foreign process referenced it'
        Assert-GateB (Test-Path -LiteralPath $leaseB.Path -PathType Leaf) `
            'lease was released while a foreign process referenced the profile'
        Stop-GateBExactProcess $foreignProfileProcess
        $foreignProfileProcess = $null

        # Restore B to a real, positively identified synthetic browser root
        # only after the stale/foreign-profile negative proof has completed.
        # This lets the positive cleanup path satisfy the root-gone contract
        # without weakening the required fail-closed behavior for a missing
        # recorded root.
        $rootProcessB = Start-VerifierProcess $browserPath @(
            '//B', $rootScript,
            '--user-data-dir', $recordB.Profile,
            '--tsj-verifier-run', $contextB.RunId,
            '--tsj-verifier-worktree', $contextB.RepositoryIdentity,
            '--remote-debugging-port', [string]$recordB.CdpPort)
        $recordB.Runtime.Browser = $rootProcessB
        $recordB.ProcessId = [int]$rootProcessB.Id
        $recordB.Status = 'started'
        $recordB.CleanupResult = 'pending'
        $recordB.Error = ''
        $recordB.Lease.ReleaseBlocked = $false
        $recordB.Lease.ReleaseBlockReason = ''
        Write-VerifierManifest $contextB
        $rootStartB = Get-VerifierProcessStartTicks $rootProcessB
        $rootIdentityB = Get-VerifierCurrentProcessIdentity $rootProcessB.Id $rootStartB `
            0 '' '' 0 $contextB.RunId '' 0 $browserPath
        $recordB.ProcessStartTicks = $rootStartB
        $recordB.ProcessParentProcessId = [int]$rootIdentityB.Record.ParentProcessId
        $recordB.ProcessParentProcessStartTicks = [long]$rootIdentityB.Record.ParentProcessStartTicks
        $recordB.ProcessCommandLine = [string]$rootIdentityB.Record.CommandLine
        Write-VerifierManifest $contextB
        Complete-VerifierBrowserSession $contextB $recordB
        Assert-GateB (-not (Test-Path -LiteralPath $recordB.Profile)) `
            'profile was not cleaned after the foreign process exited'
        Assert-GateB (-not (Test-Path -LiteralPath $leaseB.Path)) `
            'lease was not cleaned after the foreign process exited'

        $staleStartTicks = [long]$rootStartB
        if ($staleStartTicks -eq [long]::MaxValue) {
            $staleStartTicks = $staleStartTicks - 1L
        } else {
            $staleStartTicks = $staleStartTicks + 1L
        }
        if ($staleStartTicks -le 0 -or $staleStartTicks -eq [long]$rootStartB) {
            Throw-GateBInfrastructure 'could not derive a deterministic positive stale identity from the captured root identity'
        }
        $staleRecord = [pscustomobject]@{
            RunId = $contextB.RunId; RepositoryIdentity = $contextB.RepositoryIdentity; ProcessId = $PID
            ProcessStartTicks = $staleStartTicks
            Profile = $recordB.Profile; CdpPort = $recordB.CdpPort
        }
        Assert-GateB (-not (Test-VerifierProcessIdentity $staleRecord)) `
            'stale/foreign process identity was adopted'
        $quotedProfile = Join-Path $contextB.RunRoot 'browser\profile with spaces'
        $quotedIdentityRecord = [pscustomobject]@{
            RunId = $contextB.RunId; RepositoryIdentity = $contextB.RepositoryIdentity
            ProcessId = $PID
            ProcessStartTicks = [long](Get-VerifierCurrentProcessStartTicks)
            Profile = $quotedProfile; CdpPort = $recordB.CdpPort
            BrowserPath = $recordB.BrowserPath
        }
        $quotedCommand = 'powershell.exe ' + (ConvertTo-VerifierArgumentString @(
            '--user-data-dir', $quotedProfile,
            '--tsj-verifier-run', $contextB.RunId,
            '--tsj-verifier-worktree', $contextB.RepositoryIdentity,
            '--remote-debugging-port', [string]$recordB.CdpPort))
        $quotedSnapshot = @([pscustomobject]@{
            ProcessId = $PID; Name = [IO.Path]::GetFileName($recordB.BrowserPath)
            ExecutablePath = $recordB.BrowserPath; CommandLine = $quotedCommand
        })
        Assert-GateB (Test-VerifierProcessIdentity $quotedIdentityRecord $quotedSnapshot) `
            'quoted Windows command-line ownership identity was not recognized'
        $previewScriptRoundTrip = Join-Path $contextB.RunRoot 'preview script with spaces.ps1'
        $previewNonceRoundTrip = [Guid]::NewGuid().ToString('N')
        $previewRoundTripRecord = [pscustomobject]@{
            ProcessId = $PID; ProcessStartTicks = [long](Get-VerifierCurrentProcessStartTicks)
            Script = $previewScriptRoundTrip; Port = $recordB.CdpPort
            RunId = $contextB.RunId; Nonce = $previewNonceRoundTrip
        }
        $previewRoundTripCommand = 'powershell.exe ' + (ConvertTo-VerifierArgumentString @(
            '-File', $previewScriptRoundTrip,
            '-Port', [string]$recordB.CdpPort,
            '-VerifierRunId', $contextB.RunId,
            '-VerifierNonce', $previewNonceRoundTrip))
        $previewRoundTripSnapshot = @([pscustomobject]@{
            ProcessId = $PID; ProcessStartTicks = $previewRoundTripRecord.ProcessStartTicks
            CommandLine = $previewRoundTripCommand
        })
        Assert-GateB (Test-VerifierPreviewProcessIdentity $previewRoundTripRecord `
            $previewRoundTripSnapshot) `
            'shared Windows argument round-trip did not recognize quoted preview identity'
        Assert-GateB ([IO.File]::ReadAllText($foreignEvidence) -eq $contextB.RunId) `
            'foreign evidence was modified'
        $checksValidated = $true
    } catch {
        # Preserve the first canary failure. Cleanup must still run, but a
        # cleanup exception must not erase the diagnostic that explains where
        # the ownership/contract proof stopped.
        $primaryFailure = $_
    } finally {
        $cleanupErrors = New-Object Collections.ArrayList
        if ($null -ne $bindListener) {
            try { Stop-GateBListenerExact $bindListener $leaseA.Port; $bindListener = $null } catch {
                [void]$cleanupErrors.Add((Get-VerifierErrorMessage $_))
            }
        }
        if ($null -ne $foreignListenerProcess) {
            try { Stop-GateBExactProcess $foreignListenerProcess; $foreignListenerProcess = $null } catch {
                [void]$cleanupErrors.Add((Get-VerifierErrorMessage $_))
            }
        }
        if ($null -ne $foreignProfileProcess) {
            try { Stop-GateBExactProcess $foreignProfileProcess; $foreignProfileProcess = $null } catch {
                [void]$cleanupErrors.Add((Get-VerifierErrorMessage $_))
            }
        }
        if ($null -ne $competitionProcess) {
            try {
                Stop-GateBExactProcess $competitionProcess
                $competitionTerminationProven = $true
            } catch {
                [void]$cleanupErrors.Add((Get-VerifierErrorMessage $_))
            }
            try { $competitionProcess.Dispose() } catch {
                [void]$cleanupErrors.Add((Get-VerifierErrorMessage $_))
            }
        }

        $cleanupA = $null
        $cleanupB = $null
        if ($null -ne $contextA) {
            try { $cleanupA = Complete-VerifierRun $contextA } catch {
                [void]$cleanupErrors.Add((Get-VerifierErrorMessage $_))
            }
            if ($null -eq $cleanupA -or -not [bool]$cleanupA.Success) {
                $detail = if ($cleanupA) { (@($cleanupA.Errors) -join '; ') } else {
                    'Complete-VerifierRun returned no result for run A.'
                }
                [void]$cleanupErrors.Add($detail)
            }
        }
        if ($null -ne $contextB) {
            try { $cleanupB = Complete-VerifierRun $contextB } catch {
                [void]$cleanupErrors.Add((Get-VerifierErrorMessage $_))
            }
            if ($null -eq $cleanupB -or -not [bool]$cleanupB.Success) {
                $detail = if ($cleanupB) { (@($cleanupB.Errors) -join '; ') } else {
                    'Complete-VerifierRun returned no result for run B.'
                }
                [void]$cleanupErrors.Add($detail)
            }
        }
        # Keep an exact process handle for every synthetic browser root until
        # the run cleanup result has been obtained. If a partial cleanup left
        # one alive, prove and stop only that revalidated process object; a
        # failure retains the canary namespace and all ownership evidence.
        foreach ($rootCandidate in @(
                [pscustomobject]@{ Process = $rootProcessA; Port = if ($recordA) { [int]$recordA.CdpPort } else { 0 } }
                [pscustomobject]@{ Process = $rootProcessB; Port = if ($recordB) { [int]$recordB.CdpPort } else { 0 } }
            )) {
            if ($null -ne $rootCandidate.Process) {
                try {
                    Stop-GateBExactProcess $rootCandidate.Process $rootCandidate.Port
                } catch {
                    [void]$cleanupErrors.Add((Get-VerifierErrorMessage $_))
                }
            }
        }
        if ($cleanupA -and [bool]$cleanupA.Success) {
            try { Assert-GateBContextResourcesReleased $contextA } catch {
                [void]$cleanupErrors.Add((Get-VerifierErrorMessage $_))
            }
        }
        if ($cleanupB -and [bool]$cleanupB.Success) {
            try { Assert-GateBContextResourcesReleased $contextB } catch {
                [void]$cleanupErrors.Add((Get-VerifierErrorMessage $_))
            }
        }
        if ($checksValidated -and $cleanupA -and [bool]$cleanupA.Success -and
                $cleanupB -and [bool]$cleanupB.Success -and
                $competitionTerminationProven -and $childCleanupProven -and
                $cleanupErrors.Count -eq 0) {
            try {
                if ($childRunRoot -and (Test-Path -LiteralPath $childRunRoot)) {
                    if (-not (Test-VerifierChildPath $verifyRoot $childRunRoot)) {
                        Throw-GateBInfrastructure 'Refusing to remove a competing child root outside the verifier temp root.'
                    }
                    Remove-VerifierOwnedTree $verifyRoot $childRunRoot
                }
                Remove-GateBCanaryRoots $canaryRoot @($contextA, $contextB)
                $canarySucceeded = $true
            } catch {
                [void]$cleanupErrors.Add((Get-VerifierErrorMessage $_))
            }
        }
        if ($null -ne $primaryFailure -and $cleanupErrors.Count -gt 0) {
            Throw-GateBInfrastructure ('isolation canary failed and cleanup was also not proven; evidence was retained: ' +
                (Get-VerifierErrorMessage $primaryFailure) + '; cleanup: ' + ($cleanupErrors -join '; '))
        }
        if ($cleanupErrors.Count -gt 0) {
            Throw-GateBInfrastructure ('isolation canary cleanup failed; run evidence was retained: ' +
                ($cleanupErrors -join '; '))
        }
        if ($null -ne $primaryFailure) {
            throw $primaryFailure
        }
    }
    if ($canarySucceeded) { Write-Host 'PASS:atomic global claim, listener ownership, and A/B resource canary' }
}

function Invoke-GateBHangingChildCanary() {
    $canaryRoot = Join-Path ([IO.Path]::GetTempPath()) `
        ('TroubleshootJS\gate-b-hanging-child-' + [Guid]::NewGuid().ToString('N'))
    $contextB = $null
    $childResult = $null
    $worktreeWithSpaces = $null
    $childScriptPath = $null
    $primaryFailure = $null
    $cleanupErrors = New-Object Collections.ArrayList
    $timeoutObserved = $false
    $timeoutTerminationProven = $false
    $checksValidated = $false
    $canarySucceeded = $false
    try {
        New-Item -ItemType Directory -Path $canaryRoot -Force -ErrorAction Stop | Out-Null
        $worktreeWithSpaces = Join-Path $canaryRoot 'child worktree with spaces'
        New-Item -ItemType Directory -Path $worktreeWithSpaces -Force -ErrorAction Stop | Out-Null
        $childLedgerPath = Join-Path $canaryRoot 'child-ledger.json'
        $childScriptPath = Join-Path $canaryRoot 'hanging-child.ps1'
        $childEvidenceRoot = Join-Path $canaryRoot 'child-evidence'
        $childScript = @'
param(
    [string]$ModulePath,
    [string]$WorktreeRoot,
    [string]$EvidenceRoot,
    [string]$LedgerPath
)
Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
Import-Module $ModulePath -Force -ErrorAction Stop
$childContext = New-VerifierRunContext $WorktreeRoot $EvidenceRoot
$childLease = New-VerifierPortLease $childContext 'hanging-child-owned-resource'
$ledger = [ordered]@{
    protocol = 'troubleshootjs-hanging-child-ledger-v1'
    runRoot = $childContext.RunRoot
    manifestPath = $childContext.ManifestPath
    evidenceDirectory = $childContext.EvidenceDirectory
    claimPath = $childLease.Path
    port = $childLease.Port
    childPid = $PID
    state = 'owned-resource-started'
}
[IO.File]::WriteAllText($LedgerPath, ($ledger | ConvertTo-Json -Depth 8),
    [Text.UTF8Encoding]::new($false))
Start-Sleep -Seconds 30
'@
        [IO.File]::WriteAllText($childScriptPath, $childScript,
            [Text.UTF8Encoding]::new($false))
        $contextB = New-VerifierRunContext $repositoryRoot $canaryRoot
        $leaseB = New-VerifierPortLease $contextB 'foreign-run-survival'
        $powershell = (Get-Command powershell.exe -ErrorAction Stop).Source
        try {
            $childResult = Invoke-GateBBoundedProcess $powershell @(
                '-NoProfile', '-ExecutionPolicy', 'Bypass', '-File', $childScriptPath,
                '-ModulePath', $modulePath, '-WorktreeRoot', $worktreeWithSpaces,
                '-EvidenceRoot', $childEvidenceRoot, '-LedgerPath', $childLedgerPath) `
                10000 'hanging owned-resource child'
            Throw-GateBInfrastructure 'hanging owned-resource child unexpectedly exited within its timeout canary bound'
        } catch {
            if (-not (Test-VerifierInfrastructureError $_)) { throw }
            # The bounded runner reports the expected parent timeout as typed
            # infrastructure only after its exact current-process stop,
            # WaitForExit/Refresh/HasExited, and absence proof succeeded. A
            # generic infrastructure error is not enough to make this canary
            # pass because the child could still be alive.
            $timeoutData = if ($_.Exception -and $_.Exception.Data) {
                $_.Exception.Data
            } else { $null }
            $timeoutObserved = ($timeoutData -and
                $timeoutData.Contains('GateBProcessTimedOut') -and
                [bool]$timeoutData['GateBProcessTimedOut'])
            $timeoutTerminationProven = ($timeoutData -and
                $timeoutData.Contains('GateBProcessTerminationProven') -and
                [bool]$timeoutData['GateBProcessTerminationProven'])
            if (-not $timeoutObserved -or -not $timeoutTerminationProven) {
                Throw-GateBInfrastructure ('hanging owned-resource child did not provide a proven timeout/termination result; ' +
                    'the child run root and any claim/evidence were retained: ' +
                    (Get-VerifierErrorMessage $_))
            }
        }
        Assert-GateB ($timeoutObserved -and $timeoutTerminationProven) `
            'hanging owned-resource child timeout/termination was not proven as typed infrastructure'
        if (-not (Test-Path -LiteralPath $childLedgerPath -PathType Leaf)) {
            Throw-GateBInfrastructure 'hanging child did not publish its parent-visible ledger before the bounded timeout'
        }
        $childLedger = Get-Content -LiteralPath $childLedgerPath -Raw | ConvertFrom-Json
        if ([string]$childLedger.protocol -ne 'troubleshootjs-hanging-child-ledger-v1' -or
                [string]$childLedger.state -ne 'owned-resource-started') {
            Throw-GateBInfrastructure 'hanging child ledger did not prove resource startup before the bounded timeout'
        }
        $verifyRoot = Join-Path ([IO.Path]::GetTempPath()) 'TroubleshootJS\verify'
        Assert-GateB (Test-VerifierChildPath $verifyRoot ([string]$childLedger.runRoot)) `
            'hanging child run root escaped the verifier temp root'
        Assert-GateB (Test-Path -LiteralPath ([string]$childLedger.manifestPath) -PathType Leaf) `
            'hanging child manifest was not retained after bounded timeout'
        Assert-GateB (Test-Path -LiteralPath ([string]$childLedger.claimPath) -PathType Leaf) `
            'hanging child claim/evidence was deleted without child cleanup proof'
        Assert-GateB ([int]$childLedger.port -ne [int]$leaseB.Port) `
            'hanging child collided with the independent survivor run'
        Assert-GateB (Test-Path -LiteralPath $leaseB.Path -PathType Leaf) `
            'independent survivor run claim was corrupted by hanging child timeout'
        $checksValidated = $true
    } catch {
        $primaryFailure = if ($primaryFailure) { $primaryFailure } else { $_ }
    } finally {
        if ($null -ne $contextB) {
            try {
                $cleanupB = Complete-VerifierRun $contextB
                if ($null -eq $cleanupB -or -not [bool]$cleanupB.Success) {
                    $detail = if ($cleanupB) { @($cleanupB.Errors) -join '; ' } else {
                        'Complete-VerifierRun returned no result for survivor run B.'
                    }
                    [void]$cleanupErrors.Add($detail)
                }
                if ($cleanupB -and [bool]$cleanupB.Success) {
                    try { Assert-GateBContextResourcesReleased $contextB } catch {
                        [void]$cleanupErrors.Add((Get-VerifierErrorMessage $_))
                    }
                }
            } catch {
                [void]$cleanupErrors.Add((Get-VerifierErrorMessage $_))
            }
        }
        # The hanging child's exact run root/claim is deliberately retained:
        # the parent killed only its proven PID and cannot prove child finally
        # cleanup. Remove only the canary launcher/worktree if survivor cleanup
        # succeeded; never recursively remove the retained child evidence.
        if ($cleanupErrors.Count -eq 0) {
            try {
                if ($childScriptPath -and (Test-Path -LiteralPath $childScriptPath)) {
                    Remove-VerifierOwnedTree $canaryRoot $childScriptPath
                }
                if ($worktreeWithSpaces -and (Test-Path -LiteralPath $worktreeWithSpaces)) {
                    Remove-VerifierOwnedTree $canaryRoot $worktreeWithSpaces
                }
            } catch {
                [void]$cleanupErrors.Add((Get-VerifierErrorMessage $_))
            }
        }
        if ($cleanupErrors.Count -gt 0) {
            Throw-GateBInfrastructure ('hanging-child canary cleanup was not proven; evidence was retained at ' +
                $canaryRoot + ': ' + ($cleanupErrors -join '; '))
        }
        if ($null -eq $primaryFailure -and $checksValidated -and $timeoutObserved) {
            $canarySucceeded = $true
        }
    }
    if ($null -ne $primaryFailure -and -not (Test-VerifierInfrastructureError $primaryFailure)) {
        throw $primaryFailure
    }
    if ($null -ne $primaryFailure -and (Test-VerifierInfrastructureError $primaryFailure) -and
            -not $timeoutObserved) {
        throw $primaryFailure
    }
    if (-not $canarySucceeded) {
        Throw-GateBInfrastructure 'hanging-child canary did not validate termination, retained evidence, and survivor cleanup.'
    }
    Write-Host ("PASS:hanging integrated child was bounded, exact parent termination was proven, " +
        'and child evidence was retained without affecting run B: ' + $childLedger.runRoot)
}

function Invoke-GateBDriver() {
    try {
        if ($GateBDriverInfrastructureProbe) {
            Throw-GateBInfrastructure 'deterministic driver infrastructure probe'
        }
        if ($GateBLeaseReleaseProbe) {
            Invoke-GateBLifecycleBooleanCanary
            Invoke-GateBLeaseRollbackCheck
            return 0
        }
        if ($GateBPreviewIdentityFailureProbe) {
            Invoke-GateBPreviewIdentityFailureCanary
            return 0
        }
        if ($GateBStopPreviewProbe) {
            Invoke-GateBStopPreviewContractCanary $repositoryRoot
            return 0
        }
        if ($GateBCdpHandshakeProbe) {
            Invoke-GateBCdpHandshakeCanary
            return 0
        }
        if ($GateBArgumentPathProbe) {
            $argumentCanaryRoot = Join-Path ([IO.Path]::GetTempPath()) `
                ('TroubleshootJS\gate-b-argument-probe-' + [Guid]::NewGuid().ToString('N'))
            New-Item -ItemType Directory -Path $argumentCanaryRoot -Force -ErrorAction Stop | Out-Null
            Invoke-GateBArgumentPathCanary $argumentCanaryRoot
            Invoke-GateBCommandLineRoundTripCheck
            if (Test-Path -LiteralPath $argumentCanaryRoot) {
                Remove-VerifierOwnedTree (Get-VerifierFullPath ([IO.Path]::GetTempPath())) $argumentCanaryRoot
            }
            return 0
        }
        if ($GateBHangingChildProbe) {
            Invoke-GateBHangingChildCanary
            return 0
        }
        if ($GateBLateMarkerlessProbe) {
            Invoke-GateBLateMarkerlessCleanupCanary
            return 0
        }
        if ($GateBRootGoneProbe) {
            Invoke-GateBRootGoneCleanupCanary
            return 0
        }
        if ($GateBRealEdgeOwnershipProbe) {
            Invoke-GateBRealEdgeOwnershipCanary
            return 0
        }
        if ($GateBProcessOwnershipProbe) {
            Invoke-GateBProcessOwnershipCanary
            return 0
        }
        if ($GateBProcessStartIdentityProbe) {
            Invoke-GateBProcessStartIdentityCanary
            return 0
        }
        if ($GateBKernelTransportProbe) {
            Invoke-GateBKernelTransportCanary
            return 0
        }
        if ($GateBListenerRecordConsumerProbe) {
            Invoke-GateBListenerTrustBoundaryCanary
            Invoke-GateBListenerRecordConsumerCanary
            Invoke-GateBListenerRecordScalarCanary
            Invoke-GateBCallerIdentityScalarCanary
            return 0
        }
        Invoke-GateBParserChecks
        Invoke-GateBSourceChecks
        Invoke-GateBWorkflowChecks
        Invoke-GateBGwtModuleCheck
        Invoke-GateBModuleImportSetupCheck
        Invoke-GateBKernelTransportCanary
        Invoke-GateBListenerTrustBoundaryCanary
        Invoke-GateBListenerRecordConsumerCanary
        Invoke-GateBListenerRecordScalarCanary
        Invoke-GateBCallerIdentityScalarCanary
        Invoke-GateBRootGoneCleanupCanary
        Invoke-GateBLateMarkerlessCleanupCanary
        Invoke-GateBDescendantCleanupCanary
        # The real Edge ownership proof is a separate opt-in supplemental
        # lane. The protected default remains deterministic and nonvisual.
        Invoke-GateBProcessOwnershipCanary
        Invoke-GateBProcessStartIdentityCanary
        Invoke-GateBPreviewIdentityFailureCanary
        Invoke-GateBSetupFailureCheck
        Invoke-GateBListenerInspectionFailureCheck
        Invoke-GateBCdpHandshakeCanary
        Invoke-GateBDriverInfrastructureCheck
        if ($SkipJdkCheck) {
            Write-Host 'SKIP:JDK8 check explicitly requested for local static/isolation-only validation'
        } else {
            Invoke-GateBJdkCheck
        }
        Invoke-GateBExitContractChecks
        Invoke-GateBLifecycleBooleanCanary
        Invoke-GateBLeaseRollbackCheck
        Invoke-GateBCleanupRetentionCheck
        Invoke-GateBCommandLineRoundTripCheck
        Invoke-GateBStopPreviewContractCanary $repositoryRoot
        Invoke-GateBHangingChildCanary
        Invoke-GateBIsolationCanary
        $rendererPowerShell = (Get-Command powershell.exe -ErrorAction Stop).Source
        $rendererResult = Invoke-GateBBoundedProcess $rendererPowerShell @(
            '-NoProfile', '-ExecutionPolicy', 'Bypass', '-File',
            (Join-Path $PSScriptRoot 'verify-renderer-boundary.ps1')) 60000 `
            'renderer boundary check'
        $rendererExit = Resolve-GateBChildExitCode $rendererResult 'renderer boundary check'
        if ($rendererExit -ne 0) {
            $rendererMessage = "renderer boundary check returned exit ${rendererExit}: $($rendererResult.Stdout) $($rendererResult.Stderr)"
            if ($rendererExit -eq 2) { Throw-GateBInfrastructure $rendererMessage }
            throw $rendererMessage
        }
        Write-Host 'PASS:Gate B deterministic checks'
        return 0
    } catch {
        if (Test-VerifierInfrastructureError $_) {
            Write-Host ("FAIL:Gate B deterministic checks - infrastructure exit 2: " + $_.Exception.Message)
            $script:GateBDriverExitCode = 2
            return 2
        }
        Write-Host ("FAIL:Gate B deterministic checks - application/contract exit 1: " + $_.Exception.Message)
        $script:GateBDriverExitCode = 1
        return 1
    }
}

$driverResult = [int](Invoke-GateBDriver)
if ($script:GateBDriverExitCode -eq 2 -or $driverResult -eq 2) {
    exit 2
}
exit $driverResult
