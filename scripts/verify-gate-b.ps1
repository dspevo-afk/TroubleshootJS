[CmdletBinding()]
param(
    [string]$JavaHome = '',
    [AllowEmptyString()]
    [string]$ExpectedCandidateSha = '',
    [switch]$SkipJdkCheck,
    [switch]$GateBDriverInfrastructureProbe,
    [switch]$GateBHangingChildProbe,
    [switch]$GateBLeaseReleaseProbe,
    [switch]$GateBPreviewIdentityFailureProbe,
    [switch]$GateBBrowserIdentityRetryProbe,
    [switch]$GateBBrowserDescendantIdentityRetryProbe,
    [switch]$GateBBrowserDrainNaturalExitProbe,
    [switch]$GateBBrowserNaturalShutdownProbe,
    [switch]$GateBBrowserContainmentProbe,
    [switch]$GateBListenerAuthorizationRetryProbe,
    [switch]$GateBDescendantSnapshotRefreshProbe,
    [switch]$GateBBrowserRootListenerFastPathProbe,
    [switch]$GateBStopPreviewProbe,
    [switch]$GateBCdpHandshakeProbe,
    [switch]$GateBCdpReferenceProbe,
    [switch]$GateBTask43PRuntimeEvidenceProbe,
    [switch]$GateBArgumentPathProbe,
    [switch]$GateBEdgeDescendantCompatibilityProbe,
    [switch]$GateBLateMarkerlessProbe,
    [switch]$GateBRootGoneProbe,
    [switch]$GateBRealEdgeOwnershipProbe,
    [switch]$GateBProcessOwnershipProbe,
    [switch]$GateBProcessIdentityPidZeroProbe,
    [switch]$GateBProcessStartIdentityProbe,
    [switch]$GateBKernelTransportProbe,
    [switch]$GateBListenerRecordConsumerProbe,
    [switch]$GateBCleanupRetentionProbe,
    [switch]$GateBBrowserLeaseProbe,
    [switch]$GateBNativeProcessInspectionProbe,
    [switch]$GateBTcpListenerPreviewProbe,
    [switch]$GateBStartPreviewAdoptionProbe,
    [switch]$GateBIsolationProbe
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
$script:GateBDriverExitCode = 0
$script:GateBCandidateSha = ''

$modulePath = Join-Path $PSScriptRoot 'VerifierIsolation.psm1'
try {
    Import-Module $modulePath -Force
    . (Join-Path $PSScriptRoot 'Task43PCandidateIdentity.ps1')
    $repositoryRoot = Get-VerifierFullPath (Join-Path $PSScriptRoot '..')
    if ($PSBoundParameters.ContainsKey('ExpectedCandidateSha')) {
        $script:GateBCandidateSha = Get-Task43PCandidateSha $repositoryRoot $ExpectedCandidateSha
    }
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

function New-GateBCanaryLease($Context, $RequestedPort = 0) {
    $browserPath = (Get-Command powershell.exe -ErrorAction Stop).Source
    return New-VerifierPortLease $Context 'cdp' $RequestedPort $browserPath
}

function Invoke-GateBTimeoutBoundaryCanary() {
    $secondsCases = @(
        [pscustomobject]@{ Name = 'string'; Value = '30' }
        [pscustomobject]@{ Name = 'Boolean'; Value = $true }
        [pscustomobject]@{ Name = 'fraction'; Value = [double]30.5 }
        [pscustomobject]@{ Name = 'array'; Value = [object[]]@(30) }
        [pscustomobject]@{ Name = 'object'; Value = [pscustomobject]@{ Seconds = 30 } }
        [pscustomobject]@{ Name = 'null'; Value = $null }
        [pscustomobject]@{ Name = 'below-range'; Value = 9 }
        [pscustomobject]@{ Name = 'above-range'; Value = 301 }
    )
    foreach ($case in $secondsCases) {
        $rejected = $false
        try { [void](Assert-VerifierTimeoutSeconds $case.Value 'timeout boundary canary') } catch {
            $rejected = Test-VerifierInfrastructureError $_
        }
        Assert-GateB $rejected "timeout seconds accepted malformed $($case.Name) input"
    }

    $millisecondsCases = @(
        [pscustomobject]@{ Name = 'string'; Value = '1000' }
        [pscustomobject]@{ Name = 'Boolean'; Value = $true }
        [pscustomobject]@{ Name = 'fraction'; Value = [double]1000.5 }
        [pscustomobject]@{ Name = 'array'; Value = [object[]]@(1000) }
        [pscustomobject]@{ Name = 'object'; Value = [pscustomobject]@{ Milliseconds = 1000 } }
        [pscustomobject]@{ Name = 'null'; Value = $null }
        [pscustomobject]@{ Name = 'zero'; Value = 0 }
        [pscustomobject]@{ Name = 'negative'; Value = -1 }
        [pscustomobject]@{ Name = 'int-overflow'; Value = [long]([int]::MaxValue) + 1L }
    )
    foreach ($case in $millisecondsCases) {
        $rejected = $false
        try {
            [void](Invoke-GateBBoundedProcess 'not-a-real-process.exe' @() `
                $case.Value 'timeout boundary canary')
        } catch {
            $rejected = Test-VerifierInfrastructureError $_
        }
        Assert-GateB $rejected "bounded process accepted malformed $($case.Name) timeout before process start"
    }
    Write-Host 'PASS:raw timeout boundaries reject malformed values before lease or process mutation'
}

function Invoke-GateBStrictListenerDeadlineCanary() {
    $module = @(Get-Module VerifierIsolation | Select-Object -First 1)
    if ($module.Count -ne 1) {
        Throw-GateBInfrastructure 'VerifierIsolation module was unavailable for strict listener deadline canary.'
    }
    $emptyInspection = [pscustomobject]@{
        Success = $true; Known = $true; HasListeners = $false
        Listeners = @(); ListenerOwnerKind = 'none'
        ListenerOwnerProof = ''; ListenerOwnerEvidence = ''
        Source = 'Get-NetTCPConnection'; Error = ''
    }
    $kernelListener = [pscustomobject]@{
        LocalAddress = '127.0.0.1'; Port = 45125; ProcessId = 4
        ProcessStartTicks = $null; ListenerOwnerKind = 'kernel-transport'
        ListenerOwnerProof = 'run-owned-preview-http-sys-v1'
        ListenerOwnerEvidence = 'pid-4-system-http-sys'
        Source = 'Get-NetTCPConnection'
    }
    $kernelInspection = [pscustomobject]@{
        Success = $true; Known = $true; HasListeners = $true
        Listeners = @($kernelListener); ListenerOwnerKind = 'kernel-transport'
        ListenerOwnerProof = 'run-owned-preview-http-sys-v1'
        ListenerOwnerEvidence = 'pid-4-system-http-sys'
        Source = 'Get-NetTCPConnection'; Error = ''
    }
    $userListener = [pscustomobject]@{
        LocalAddress = '127.0.0.1'; Port = 45125; ProcessId = 9001
        ProcessStartTicks = 701L; ListenerOwnerKind = 'user-process'
        ListenerOwnerProof = 'diagnostics-process-start-v1'
        ListenerOwnerEvidence = 'system-diagnostics-process-starttime'
        Source = 'Get-NetTCPConnection'
    }
    $userInspection = [pscustomobject]@{
        Success = $true; Known = $true; HasListeners = $true
        Listeners = @($userListener); ListenerOwnerKind = 'user-process'
        ListenerOwnerProof = 'diagnostics-process-start-v1'
        ListenerOwnerEvidence = 'system-diagnostics-process-starttime'
        Source = 'Get-NetTCPConnection'; Error = ''
    }
    $context = [pscustomobject]@{}
    $lease = [pscustomobject]@{ Port = 45125 }
    $owner = [pscustomobject]@{}
    $kernelOwner = [pscustomobject]@{}
    $proof = [pscustomobject]@{ Authorized = $true }
    $probe = & $module[0] {
        param($empty, $kernel, $user, $contextValue, $leaseValue,
            $ownerValue, $kernelOwnerValue, $proofValue)
        $saved = @{}
        foreach ($name in @(
                'Test-VerifierListenerInspectionSchema',
                'Test-VerifierListenerRecordSchema',
                'Test-VerifierRunOwnedPreviewHttpSysAuthorizationProof',
                'Get-VerifierLoopbackListenerRecords',
                'Test-VerifierListenerBelongsToOwner')) {
            $saved[$name] = (Get-Command $name -CommandType Function `
                -ErrorAction Stop).ScriptBlock
        }
        try {
            $script:GateBStrictDeadlineMode = 'fast'
            $script:GateBStrictDeadlineInspection = $user
            Set-Item Function:\Test-VerifierListenerInspectionSchema -Force -Value {
                if ($script:GateBStrictDeadlineMode -eq 'schema-delay') {
                    Start-Sleep -Milliseconds 650
                }
                return $true
            }
            Set-Item Function:\Test-VerifierListenerRecordSchema -Force -Value {
                return $true
            }
            Set-Item Function:\Test-VerifierRunOwnedPreviewHttpSysAuthorizationProof `
                -Force -Value {
                    if ($script:GateBStrictDeadlineMode -eq 'kernel-delay') {
                        Start-Sleep -Milliseconds 650
                    }
                    return $true
                }
            Set-Item Function:\Get-VerifierLoopbackListenerRecords -Force -Value {
                if ($script:GateBStrictDeadlineMode -eq 'initial-delay') {
                    Start-Sleep -Milliseconds 650
                }
                return $script:GateBStrictDeadlineInspection
            }
            Set-Item Function:\Test-VerifierListenerBelongsToOwner -Force -Value {
                if ($script:GateBStrictDeadlineMode -eq 'direct-delay') {
                    Start-Sleep -Milliseconds 650
                }
                return $true
            }
            $cases = @(
                [pscustomobject]@{ Name = 'schema-fast'; Kind = 'live-empty'; Mode = 'fast' }
                [pscustomobject]@{ Name = 'schema-delay'; Kind = 'live-empty'; Mode = 'schema-delay' }
                [pscustomobject]@{ Name = 'kernel-fast'; Kind = 'live-kernel'; Mode = 'fast' }
                [pscustomobject]@{ Name = 'kernel-delay'; Kind = 'live-kernel'; Mode = 'kernel-delay' }
                [pscustomobject]@{ Name = 'retained-direct-fast'; Kind = 'bound-user'; Mode = 'fast' }
                [pscustomobject]@{ Name = 'retained-direct-delay'; Kind = 'bound-user'; Mode = 'direct-delay' }
                [pscustomobject]@{ Name = 'initial-listener-delay'; Kind = 'bound-user'; Mode = 'initial-delay' }
            )
            $results = New-Object Collections.ArrayList
            foreach ($case in $cases) {
                $script:GateBStrictDeadlineMode = $case.Mode
                $script:GateBStrictDeadlineInspection = $user
                $accepted = $false
                $typedFailure = $false
                try {
                    if ($case.Kind -eq 'live-empty') {
                        $accepted = [bool](Test-VerifierLiveListenerInspectionAuthorization `
                            $empty [pscustomobject]@{} [pscustomobject]@{} 9000 700L)
                    } elseif ($case.Kind -eq 'live-kernel') {
                        $accepted = [bool](Test-VerifierLiveListenerInspectionAuthorization `
                            $kernel $contextValue $kernelOwnerValue 9000 700L $proofValue)
                    } else {
                        $accepted = $null -ne (Get-VerifierPortLeaseBoundOwnershipProof `
                            $contextValue $leaseValue 9000 700L $ownerValue $null)
                    }
                } catch {
                    $typedFailure = Test-VerifierInfrastructureError $_
                }
                [void]$results.Add([pscustomobject]@{
                    Name = $case.Name; Accepted = $accepted
                    TypedFailure = $typedFailure
                })
            }
            return @($results)
        } finally {
            foreach ($name in $saved.Keys) {
                Set-Item Function:\$name -Force -Value $saved[$name]
            }
            Remove-Variable -Name GateBStrictDeadlineMode,`
                GateBStrictDeadlineInspection -Scope Script -Force `
                -ErrorAction SilentlyContinue
        }
    } $emptyInspection $kernelInspection $userInspection $context $lease `
        $owner $kernelOwner $proof
    foreach ($fastName in @('schema-fast', 'kernel-fast', 'retained-direct-fast')) {
        $fast = @($probe | Where-Object Name -eq $fastName)
        Assert-GateB ($fast.Count -eq 1 -and $fast[0].Accepted -and
            -not $fast[0].TypedFailure) `
            "$fastName valid fast proof was rejected"
    }
    foreach ($delayedName in @(
            'schema-delay', 'kernel-delay', 'retained-direct-delay',
            'initial-listener-delay')) {
        $delayed = @($probe | Where-Object Name -eq $delayedName)
        Assert-GateB ($delayed.Count -eq 1 -and -not $delayed[0].Accepted) `
            "$delayedName proof was accepted after the strict monotonic deadline"
        if ($delayedName -in @('retained-direct-delay', 'initial-listener-delay')) {
            Assert-GateB $delayed[0].TypedFailure `
                "$delayedName did not fail as typed infrastructure uncertainty"
        }
    }
    Write-Host 'PASS:strict listener deadline covers initial, schema, direct retained, and kernel proof dependencies'
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

    $provenanceStart = $moduleText.IndexOf(
        'function Get-VerifierExecutionTreeProvenance', [StringComparison]::Ordinal)
    $provenanceEnd = $moduleText.IndexOf(
        'function Complete-VerifierProcessOutputCapture', $provenanceStart,
        [StringComparison]::Ordinal)
    Assert-GateB ($provenanceStart -ge 0 -and $provenanceEnd -gt $provenanceStart) `
        'execution provenance function boundary could not be located'
    $provenanceText = $moduleText.Substring($provenanceStart,
        $provenanceEnd - $provenanceStart)
    Assert-GateB ($provenanceText.IndexOf(
        '$filePaths.Sort([StringComparer]::OrdinalIgnoreCase)',
        [StringComparison]::Ordinal) -ge 0) `
        'execution provenance does not use explicit ordinal path ordering'
    Assert-GateB ($provenanceText.IndexOf(
        'duplicate or case-colliding file path', [StringComparison]::Ordinal) -ge 0) `
        'execution provenance does not fail closed on duplicate/case-colliding paths'
    Assert-GateB ($provenanceText.IndexOf('Sort-Object', [StringComparison]::Ordinal) -lt 0) `
        'execution provenance retained a culture-sensitive Sort-Object boundary'

    $currentProcessStartCommand = Get-Command `
        Get-VerifierCurrentProcessStartTicks -CommandType Function `
        -ErrorAction SilentlyContinue
    Assert-GateB ($null -ne $currentProcessStartCommand) `
        'Gate B required current-process start identity helper is not exported by VerifierIsolation'

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
    $optInEdgeCompatibilityMatch = [regex]::Match($driverBody,
        '(?ms)if\s*\(\$GateBEdgeDescendantCompatibilityProbe\)\s*\{\s*Invoke-GateBEdgeDescendantCompatibilityCanary\s*\r?\n\s*return\s+0\s*\}')
    Assert-GateB $optInEdgeCompatibilityMatch.Success `
        'Edge descendant compatibility canary lost its explicit opt-in branch'
    $defaultDriverBody = $defaultDriverBody.Replace($optInEdgeCompatibilityMatch.Value, '')
    Assert-GateB ($defaultDriverBody -notmatch '\bInvoke-GateBEdgeDescendantCompatibilityCanary\b') `
        'default Gate B driver invokes the machine-dependent Edge compatibility canary'

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
        'Assert-VerifierLeaseTransition',
        'Get-VerifierLoopbackListenerRecords',
        'Parse-VerifierNetstatListenerOutput',
        'Test-VerifierListenerRecordSchema',
        'Test-VerifierSameRootBrowserListenerEligibility',
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
        'Get-VerifierFileSha256',
        'Get-VerifierPreviewAdoptionCandidateRecords',
         'Get-VerifierCurrentProcessRecordById',
         'New-VerifierBrowserDrainScope',
         'New-VerifierBrowserDrainAttestation',
         'Get-VerifierBrowserDrainNaturalExitResult',
         'browser-drain-scope-v1',
         'browser-drain-attestation-v1',
         'VerifierDrainAttestation',
         'Stop-VerifierBrowserProcessTreeToFixedPointCore',
         'Dispose-VerifierBrowserDrainScope',
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
    Assert-GateB ($startPreviewText.IndexOf('Get-VerifierProcessSnapshotWithFallback',
        [StringComparison]::Ordinal) -ge 0 -and
        $startPreviewText.IndexOf('Get-VerifierPreviewAdoptionCandidateRecords',
            [StringComparison]::Ordinal) -ge 0 -and
        $startPreviewText -notmatch 'Get-CimInstance') `
        'developer preview adoption does not use the shared WMI-first/native-fallback identity path'
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
    Assert-GateB ($moduleText -match 'Assert-VerifierLeaseTransition' -and
        $moduleText -notmatch '(?i)manifested') `
        'lease lifecycle does not use one finite transition validator without the stray manifested state'
    Assert-GateB ($browserText.Contains('Get-Task43PCandidateSha') -and
        $browserText.Contains('candidateSha = $script:Task43PCandidateSha') -and
        $browserText.Contains('baselineSha = $script:Task43PHistoricalBaselineSha')) `
        'Task43P evidence wrapper does not separate frozen candidate and historical baseline'
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
        'Invoke-GateBStartPreviewAdoptionCanary',
         'Invoke-GateBProcessStartIdentityCanary',
         'Invoke-GateBPreviewIdentityFailureCanary',
         'Invoke-GateBDescendantCleanupCanary',
         'Invoke-GateBBrowserDrainNaturalExitCanary',
         'Invoke-GateBEdgeDescendantCompatibilityCanary',
         'Invoke-GateBLateMarkerlessCleanupCanary',
         'Invoke-GateBRootGoneCleanupCanary',
         'Invoke-GateBRealEdgeOwnershipCanary',
         'Invoke-GateBBrowserRootListenerFastPathCanary',
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
         'GateBEdgeDescendantCompatibilityProbe',
         'identity_helper.exe',
         'Get-AuthenticodeSignature',
         'SignerThumbprint',
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
    $contextResourcesStart = $moduleText.IndexOf(
        'function Assert-VerifierContextPhysicalResources', [StringComparison]::Ordinal)
    $contextResourcesEnd = $moduleText.IndexOf(
        'function Write-VerifierManifest', $contextResourcesStart,
        [StringComparison]::Ordinal)
    Assert-GateB ($contextResourcesStart -ge 0 -and
        $contextResourcesEnd -gt $contextResourcesStart) `
        'manifest physical-resource validation boundary could not be located'
    $contextResourcesText = $moduleText.Substring($contextResourcesStart,
        $contextResourcesEnd - $contextResourcesStart)
    Assert-GateB ($contextResourcesText.IndexOf('$mutableBrowserProfile',
            [StringComparison]::Ordinal) -ge 0 -and
        $contextResourcesText.IndexOf('$validateProfileTrees = -not $mutableBrowserProfile',
            [StringComparison]::Ordinal) -ge 0 -and
        $contextResourcesText.IndexOf(
            'Assert-VerifierPhysicalOwnedPath $Context.RunNamespaceRoot $Context.RunRoot)',
            [StringComparison]::Ordinal) -ge 0) `
        'manifest validation does not defer recursive run/profile checks for a mutable browser profile'
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
    $initialProfileOwnershipIndex = $browserCleanupText.IndexOf(
        'Assert-VerifierPhysicalOwnedPath $sessionRoot $sessionProfile',
        [StringComparison]::Ordinal)
    $drainIndex = $browserCleanupText.IndexOf(
        'Stop-VerifierBrowserProcessTreeToFixedPoint', [StringComparison]::Ordinal)
    $recursiveProfileOwnershipIndex = $browserCleanupText.IndexOf(
        'Assert-VerifierPhysicalOwnedPath $sessionRoot $sessionProfile -ValidateTree',
        $initialProfileOwnershipIndex + 1, [StringComparison]::Ordinal)
    Assert-GateB ($initialProfileOwnershipIndex -ge 0 -and
        $drainIndex -gt $initialProfileOwnershipIndex -and
        $recursiveProfileOwnershipIndex -gt $drainIndex) `
        'browser cleanup recursively walks the live profile before fixed-point process drain'
    $browserSessionFunctionMatch = [regex]::Match($moduleText,
        '(?ms)function\s+New-VerifierBrowserSession\b.*?(?=\r?\nfunction\s+)')
    Assert-GateB $browserSessionFunctionMatch.Success `
        'browser session startup transaction boundary could not be located'
    $browserSessionFunctionText = $browserSessionFunctionMatch.Groups[0].Value
    $browserIdentityCaptureIndex = $browserSessionFunctionText.IndexOf(
        'Get-VerifierFreshLaunchedBrowserIdentity $browser',
        [StringComparison]::Ordinal)
    $browserBindTransactionIndex = $browserSessionFunctionText.IndexOf(
        'Invoke-VerifierBrowserBindTransaction $Context $browserSessionRecord',
        [StringComparison]::Ordinal)
    $browserSessionTupleAssignment = [regex]::IsMatch($browserSessionFunctionText,
        '\$browserSessionRecord\.(?:ProcessId|ProcessStartTicks|ProcessParentProcessId|ProcessParentProcessStartTicks|ProcessCommandLine)\s*=')
    Assert-GateB ($browserIdentityCaptureIndex -ge 0 -and
        $browserBindTransactionIndex -gt $browserIdentityCaptureIndex -and
        -not $browserSessionTupleAssignment) `
        'browser session startup does not keep its durable tuple absent until the atomic bind transaction follows complete identity capture'
    $browserBindTransactionMatch = [regex]::Match($moduleText,
        '(?ms)function\s+Invoke-VerifierBrowserBindTransaction\b.*?(?=\r?\nfunction\s+)')
    $browserBindTransactionText = if ($browserBindTransactionMatch.Success) {
        $browserBindTransactionMatch.Groups[0].Value
    } else { '' }
    $browserBindSnapshotIndex = $browserBindTransactionText.IndexOf(
        'New-VerifierBrowserBindTransactionSnapshot $SessionRecord',
        [StringComparison]::Ordinal)
    $browserBindLeaseIndex = $browserBindTransactionText.IndexOf(
        '& $BindLease $SessionRecord', [StringComparison]::Ordinal)
    $browserBindManifestIndex = $browserBindTransactionText.IndexOf(
        'Write-VerifierManifest $Context', [StringComparison]::Ordinal)
    $browserBindRestoreIndex = $browserBindTransactionText.IndexOf(
        'Restore-VerifierBrowserBindTransactionSnapshot $SessionRecord $snapshot',
        [StringComparison]::Ordinal)
    Assert-GateB ($browserBindTransactionMatch.Success -and
        $browserBindSnapshotIndex -ge 0 -and $browserBindLeaseIndex -gt $browserBindSnapshotIndex -and
        $browserBindManifestIndex -gt $browserBindLeaseIndex -and
        $browserBindRestoreIndex -gt $browserBindManifestIndex) `
        'browser bind transaction does not snapshot, prove the listener, atomically commit, and restore on failure'
    $freshBrowserIdentityMatch = [regex]::Match($moduleText,
        '(?ms)function\s+Get-VerifierFreshLaunchedBrowserIdentity\b.*?(?=\r?\nfunction\s+)')
    Assert-GateB ($freshBrowserIdentityMatch.Success -and
        $freshBrowserIdentityMatch.Groups[0].Value.IndexOf(
            'Get-VerifierCurrentProcessIdentityWithRetry $browserProcessId',
            [StringComparison]::Ordinal) -ge 0 -and
        $freshBrowserIdentityMatch.Groups[0].Value.IndexOf(
            'Get-VerifierProcessStartTicks $BrowserProcess',
            [StringComparison]::Ordinal) -ge 0 -and
        $freshBrowserIdentityMatch.Groups[0].Value.IndexOf('$attempt -le 3',
            [StringComparison]::Ordinal) -ge 0) `
        'fresh browser startup helper does not retain its exact handle/start and bounded complete identity proof'
    $receiptRootMatch = [regex]::Match($moduleText,
        '(?ms)function\s+Get-VerifierBrowserReceiptNaturalShutdownRoot\b.*?(?=\r?\nfunction\s+)')
    $receiptShutdownMatch = [regex]::Match($moduleText,
        '(?ms)function\s+Close-VerifierBrowserSessionWithBoundRecoveryReceipt\b.*?(?=\r?\nfunction\s+)')
    $receiptJournalIndex = if ($receiptShutdownMatch.Success) {
        $receiptShutdownMatch.Groups[0].Value.IndexOf(
            'Record-VerifierBrowserRecoveryCloseAttempt $Context $SessionRecord',
            [StringComparison]::Ordinal)
    } else { -1 }
    $receiptPostJournalProofIndex = if ($receiptJournalIndex -ge 0) {
        $receiptShutdownMatch.Groups[0].Value.IndexOf('-AfterCloseAttemptJournal',
            $receiptJournalIndex, [StringComparison]::Ordinal)
    } else { -1 }
    $receiptSendIndex = if ($receiptPostJournalProofIndex -ge 0) {
        $receiptShutdownMatch.Groups[0].Value.IndexOf(
            'Send-VerifierBrowserCloseAndReadAcknowledgement',
            $receiptPostJournalProofIndex, [StringComparison]::Ordinal)
    } else { -1 }
    Assert-GateB ($receiptRootMatch.Success -and
        $receiptRootMatch.Groups[0].Value.IndexOf(
            '[switch]$AfterCloseAttemptJournal', [StringComparison]::Ordinal) -ge 0 -and
        $receiptRootMatch.Groups[0].Value.IndexOf('$receiptAttemptStateMatches',
            [StringComparison]::Ordinal) -ge 0 -and
        $receiptJournalIndex -ge 0 -and
        $receiptPostJournalProofIndex -gt $receiptJournalIndex -and
        $receiptSendIndex -gt $receiptPostJournalProofIndex -and
        $receiptShutdownMatch.Groups[0].Value -notmatch
            '(?m)^\s*Assert-VerifierBrowserParentExitRecoveryListener\b') `
        'receipt shutdown can retry a journaled close, skip its final identity proof, or leak listener proof output'
    $retryFunctionStart = $moduleText.IndexOf(
        'function Get-VerifierCurrentProcessIdentityWithRetry',
        [StringComparison]::Ordinal)
    Assert-GateB ($retryFunctionStart -ge 0 -and
        $moduleText.IndexOf('$attempt -le 3', $retryFunctionStart,
            [StringComparison]::Ordinal) -gt $retryFunctionStart -and
        $moduleText.IndexOf('StartNew()', $retryFunctionStart,
            [StringComparison]::Ordinal) -gt $retryFunctionStart -and
         ($moduleText.IndexOf('Elapsed.TotalMilliseconds', $retryFunctionStart,
             [StringComparison]::Ordinal) -gt $retryFunctionStart -or
          $moduleText.IndexOf('ElapsedTicks', $retryFunctionStart,
             [StringComparison]::Ordinal) -gt $retryFunctionStart) -and
        $moduleText.IndexOf('Assert-VerifierRawProcessIdentityRecord', $retryFunctionStart,
            [StringComparison]::Ordinal) -gt $retryFunctionStart) `
        'browser identity startup retry does not retain bounded complete-proof checks'
    $descendantRetryStart = $moduleText.IndexOf(
        'function Get-VerifierCurrentOwnedDescendantWithRetry',
        [StringComparison]::Ordinal)
    $descendantRetryText = if ($descendantRetryStart -ge 0) {
        $nextFunction = $moduleText.IndexOf("`nfunction ",
            $descendantRetryStart + 1, [StringComparison]::Ordinal)
        if ($nextFunction -gt $descendantRetryStart) {
            $moduleText.Substring($descendantRetryStart,
                $nextFunction - $descendantRetryStart)
        } else { $moduleText.Substring($descendantRetryStart) }
    } else { '' }
    Assert-GateB ($descendantRetryText.IndexOf('$attempt -le 3',
            [StringComparison]::Ordinal) -ge 0 -and
        $descendantRetryText.IndexOf('StartNew()',
            [StringComparison]::Ordinal) -ge 0 -and
        ($descendantRetryText.IndexOf('Elapsed.TotalMilliseconds',
            [StringComparison]::Ordinal) -ge 0 -or
         $descendantRetryText.IndexOf('ElapsedTicks',
            [StringComparison]::Ordinal) -ge 0) -and
        $descendantRetryText.IndexOf('Get-VerifierCurrentProcessRecordById',
            [StringComparison]::Ordinal) -ge 0 -and
         $descendantRetryText.IndexOf('Test-VerifierDescendantExecutableIdentity',
             [StringComparison]::Ordinal) -ge 0 -and
        $descendantRetryText.IndexOf('Get-VerifierCurrentOwnedProcessOnce',
            [StringComparison]::Ordinal) -ge 0 -and
        $moduleText.IndexOf(
            'Get-VerifierCurrentOwnedDescendantWithRetry $rootOwner',
            [StringComparison]::Ordinal) -ge 0) `
        'browser descendant cleanup does not retain a bounded complete executable-identity retry'
    $listenerAuthorizationStart = $moduleText.IndexOf(
        'function Test-VerifierLiveListenerInspectionAuthorization',
        [StringComparison]::Ordinal)
    $listenerAuthorizationText = if ($listenerAuthorizationStart -ge 0) {
        $nextFunction = $moduleText.IndexOf("`nfunction ",
            $listenerAuthorizationStart + 1, [StringComparison]::Ordinal)
        if ($nextFunction -gt $listenerAuthorizationStart) {
            $moduleText.Substring($listenerAuthorizationStart,
                $nextFunction - $listenerAuthorizationStart)
        } else { $moduleText.Substring($listenerAuthorizationStart) }
    } else { '' }
    Assert-GateB ($listenerAuthorizationText.IndexOf('$attempt -le 3',
            [StringComparison]::Ordinal) -ge 0 -and
        $listenerAuthorizationText.IndexOf('StartNew()',
            [StringComparison]::Ordinal) -ge 0 -and
        $listenerAuthorizationText.IndexOf('$retryBudget.Start()',
            [StringComparison]::Ordinal) -lt 0 -and
        $listenerAuthorizationText.IndexOf('ElapsedTicks',
            [StringComparison]::Ordinal) -ge 0 -and
        $listenerAuthorizationText.IndexOf('Get-VerifierLoopbackListenerRecords',
            [StringComparison]::Ordinal) -ge 0 -and
        $listenerAuthorizationText.IndexOf('Get-VerifierBrowserOwnershipSnapshot',
            [StringComparison]::Ordinal) -ge 0 -and
        $listenerAuthorizationText.IndexOf('Get-VerifierCurrentProcessRecordById',
            [StringComparison]::Ordinal) -ge 0 -and
        $listenerAuthorizationText.IndexOf('Get-VerifierMissingProcessId',
            [StringComparison]::Ordinal) -ge 0 -and
        $listenerAuthorizationText.IndexOf('-PreferNetstat',
            [StringComparison]::Ordinal) -ge 0 -and
        $listenerAuthorizationText.IndexOf(
            'Test-VerifierSameRootBrowserListenerEligibility',
            [StringComparison]::Ordinal) -ge 0 -and
        $listenerAuthorizationText.IndexOf(
            'Test-VerifierListenerBelongsToOwner $PreviewOwner',
            [StringComparison]::Ordinal) -ge 0) `
        'listener authorization boundary does not retain a bounded fresh full-proof retry'
    $listenerBudgetIndex = $listenerAuthorizationText.IndexOf(
        '$retryBudget = [Diagnostics.Stopwatch]::StartNew()',
        [StringComparison]::Ordinal)
    $listenerSecondViewIndex = $listenerAuthorizationText.IndexOf(
        'Get-VerifierCurrentProcessRecordById $missingProcessId',
        [StringComparison]::Ordinal)
    $listenerRefreshStartIndex = $listenerAuthorizationText.IndexOf(
        'StartNew()', [StringComparison]::Ordinal)
    $listenerSchemaIndex = $listenerAuthorizationText.IndexOf(
        '$inspectionSchemaValid = Test-VerifierListenerInspectionSchema',
        [StringComparison]::Ordinal)
    $kernelAuthorizationIndex = $listenerAuthorizationText.IndexOf(
        'if ($null -eq $AuthorizationProof) { return $false }',
        [StringComparison]::Ordinal)
    $kernelAuthorizationReturnIndex = $listenerAuthorizationText.IndexOf(
        'Test-VerifierRunOwnedPreviewHttpSysAuthorizationProof',
        $kernelAuthorizationIndex, [StringComparison]::Ordinal)
    Assert-GateB ($listenerBudgetIndex -ge 0 -and
        $listenerSecondViewIndex -ge 0 -and
        $listenerRefreshStartIndex -lt $listenerSecondViewIndex -and
        $listenerSchemaIndex -gt $listenerBudgetIndex -and
        $kernelAuthorizationIndex -gt $listenerSchemaIndex -and
        $kernelAuthorizationReturnIndex -gt $kernelAuthorizationIndex -and
        $listenerAuthorizationText.IndexOf(
            '-StructuralOnly', $listenerSchemaIndex,
            [StringComparison]::Ordinal) -ge 0 -and
        $listenerAuthorizationText.IndexOf(
            'Test-VerifierRunOwnedPreviewHttpSysAuthorizationProof',
            $kernelAuthorizationIndex, [StringComparison]::Ordinal) -ge 0) `
        'listener authorization does not separate structural schema from its single semantic kernel proof'
    $portOwnershipStart = $moduleText.IndexOf(
        'function Get-VerifierPortLeaseBoundOwnershipProof',
        [StringComparison]::Ordinal)
    $portOwnershipText = if ($portOwnershipStart -ge 0) {
        $nextFunction = $moduleText.IndexOf("`nfunction ",
            $portOwnershipStart + 1, [StringComparison]::Ordinal)
        if ($nextFunction -gt $portOwnershipStart) {
            $moduleText.Substring($portOwnershipStart,
                $nextFunction - $portOwnershipStart)
        } else { $moduleText.Substring($portOwnershipStart) }
    } else { '' }
    Assert-GateB ($portOwnershipText.IndexOf('$attempt -le 3',
            [StringComparison]::Ordinal) -ge 0 -and
        $portOwnershipText.IndexOf('StartNew()',
            [StringComparison]::Ordinal) -ge 0 -and
        $portOwnershipText.IndexOf('ElapsedTicks',
            [StringComparison]::Ordinal) -ge 0 -and
        $portOwnershipText.IndexOf('Get-VerifierLoopbackListenerRecords',
            [StringComparison]::Ordinal) -ge 0 -and
        $portOwnershipText.IndexOf('Get-VerifierCurrentProcessRecordById',
            [StringComparison]::Ordinal) -ge 0 -and
        $portOwnershipText.IndexOf('Get-VerifierMissingProcessId',
            [StringComparison]::Ordinal) -ge 0 -and
        $portOwnershipText.IndexOf(
            'Test-VerifierSameRootBrowserListenerEligibility',
            [StringComparison]::Ordinal) -ge 0 -and
        $portOwnershipText.IndexOf(
            'Test-VerifierRunOwnedPreviewHttpSysListener',
            [StringComparison]::Ordinal) -ge 0 -and
        $portOwnershipText.IndexOf('-PreferNetstat',
            [StringComparison]::Ordinal) -ge 0) `
        'port lease ownership proof does not retain a bounded fresh full-proof retry'
    Assert-GateB ($portOwnershipText.IndexOf(
            '$retryBudget = [Diagnostics.Stopwatch]::StartNew()',
            [StringComparison]::Ordinal) -ge 0 -and
        $portOwnershipText.IndexOf('$retryBudget.Start()',
            [StringComparison]::Ordinal) -lt 0 -and
        $portOwnershipText.IndexOf(
            '$retryBudget = [Diagnostics.Stopwatch]::StartNew()',
            [StringComparison]::Ordinal) -lt
        $portOwnershipText.IndexOf(
            'Get-VerifierCurrentProcessRecordById $missingProcessId',
            [StringComparison]::Ordinal) -and
        $portOwnershipText.IndexOf(
            'New-VerifierRunOwnedPreviewHttpSysAuthorizationProof',
            [StringComparison]::Ordinal) -ge 0 -and
        $portOwnershipText.IndexOf('ProofStage',
            [StringComparison]::Ordinal) -ge 0 -and
        $portOwnershipText.IndexOf('ProofElapsedMilliseconds',
            [StringComparison]::Ordinal) -ge 0) `
        'port ownership proof does not separate ordinary kernel authorization from refresh budget or failure-stage evidence'
    $previewSemanticStart = $moduleText.IndexOf(
        'function Test-VerifierRunOwnedPreviewHttpSysAuthorizationProof',
        [StringComparison]::Ordinal)
    $previewSemanticText = if ($previewSemanticStart -ge 0) {
        $nextFunction = $moduleText.IndexOf("`nfunction ",
            $previewSemanticStart + 1, [StringComparison]::Ordinal)
        if ($nextFunction -gt $previewSemanticStart) {
            $moduleText.Substring($previewSemanticStart,
                $nextFunction - $previewSemanticStart)
        } else { $moduleText.Substring($previewSemanticStart) }
    } else { '' }
    Assert-GateB ($previewSemanticText.IndexOf(
            '[object]::ReferenceEquals($Context.Server, $OwnerRecord)',
            [StringComparison]::Ordinal) -ge 0 -and
        $previewSemanticText.IndexOf('CleanupState',
            [StringComparison]::Ordinal) -ge 0 -and
        $previewSemanticText.IndexOf('ReleaseState',
            [StringComparison]::Ordinal) -ge 0 -and
        $previewSemanticText.IndexOf('ReleaseJournalState',
            [StringComparison]::Ordinal) -ge 0 -and
        $previewSemanticText.IndexOf(
            'Test-VerifierRunOwnedPreviewHttpSysProofFieldSnapshot',
            [StringComparison]::Ordinal) -ge 0) `
        'kernel semantic proof does not bind current Context.Server and lifecycle/release snapshots'
    $releaseStart = $moduleText.IndexOf(
        'function Release-VerifierPortLease', [StringComparison]::Ordinal)
    $releaseText = if ($releaseStart -ge 0) {
        $nextFunction = $moduleText.IndexOf("`nfunction ",
            $releaseStart + 1, [StringComparison]::Ordinal)
        if ($nextFunction -gt $releaseStart) {
            $moduleText.Substring($releaseStart, $nextFunction - $releaseStart)
        } else { $moduleText.Substring($releaseStart) }
    } else { '' }
    Assert-GateB ($releaseText.IndexOf('$releaseAuthorizationProof',
            [StringComparison]::Ordinal) -ge 0 -and
        $releaseText.IndexOf("'AuthorizationProof'", [StringComparison]::Ordinal) -ge 0 -and
        $releaseText.IndexOf('Set-VerifierLeaseListenerInspection $Lease $inspection',
            [StringComparison]::Ordinal) -ge 0) `
        'port-lease release does not carry the retained opaque kernel proof into its setter'
    $bindStart = $moduleText.IndexOf(
        'function Confirm-VerifierPortLeaseBound', [StringComparison]::Ordinal)
    $bindText = if ($bindStart -ge 0) {
        $nextFunction = $moduleText.IndexOf("`nfunction ",
            $bindStart + 1, [StringComparison]::Ordinal)
        if ($nextFunction -gt $bindStart) {
            $moduleText.Substring($bindStart, $nextFunction - $bindStart)
        } else { $moduleText.Substring($bindStart) }
    } else { '' }
    Assert-GateB ($bindText.IndexOf('$authorizationProof',
            [StringComparison]::Ordinal) -ge 0 -and
        $bindText.IndexOf('Name AuthorizationProof',
            [StringComparison]::Ordinal) -ge 0 -and
        $bindText.IndexOf('$authorizationProof)',
            [StringComparison]::Ordinal) -ge 0) `
        'port-lease bind does not retain and pass the opaque kernel proof'
    Assert-GateB ($browserSessionFunctionText.IndexOf(
        'startup failure evidence could not be written:',
        [StringComparison]::Ordinal) -ge 0 -and
        $browserSessionFunctionText.IndexOf(
            'exact cleanup was not proven; resource evidence was retained:',
            [StringComparison]::Ordinal) -ge 0 -and
        $browserSessionFunctionText.IndexOf(
            'startupMessage = Get-VerifierErrorMessage $startupError',
            [StringComparison]::Ordinal) -ge 0) `
        'browser session startup catch does not retain the originating typed reason and evidence outcome'
    Assert-GateB ($gateText.IndexOf('$edgePath = Resolve-VerifierBrowserPath',
        [StringComparison]::Ordinal) -ge 0 -and
        $gateText.IndexOf('$opened = New-VerifierBrowserSession $context',
            [StringComparison]::Ordinal) -ge 0 -and
        $gateText.IndexOf('$session = $opened.Record', [StringComparison]::Ordinal) -ge 0 -and
        $gateText.IndexOf('$edgeProcess = $opened.Browser', [StringComparison]::Ordinal) -ge 0 -and
        $gateText.IndexOf('RecoveryReceipt.State -ceq ''bound''',
            [StringComparison]::Ordinal) -ge 0) `
        'real Edge canary is not using the shared resolver and bound durable receipt path'
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

    $inaccessibleUnmarkedPeer = [pscustomobject]@{
        ProcessId = 322; ParentProcessId = 1; Name = 'msedge.exe'; CommandLine = $null
    }
    $unmarkedPeerSelected = @(Select-VerifierRelevantProcessRecords `
        @($inaccessibleUnmarkedPeer) $browserPath $profile $runId $repositoryIdentity 45123)
    Assert-GateB ($unmarkedPeerSelected.Count -eq 0) `
        'an unrelated same-executable browser peer was treated as an ownership candidate'

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
            # Explicit null is an uninspectable, unmarked peer, covered above;
            # it must stay outside ownership selection rather than be coerced
            # into a malformed owned record.
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

function Invoke-GateBEdgeDescendantCompatibilityCanary() {
    # Exercise the descendant-only Edge companion capability against the real
    # installed, signed files. No Edge process is started and no process/PID
    # ownership proof is claimed by this canary.
    $fixtureRoot = ''
    $failure = $null
    $cleanupError = $null
    $coldElapsed = 0.0
    $warmTripleElapsed = 0.0
    try {
        $edgePath = Resolve-VerifierBrowserPath
        Assert-GateBInfrastructure (Test-Path -LiteralPath $edgePath -PathType Leaf) `
            'installed Edge executable was not available for the descendant companion canary'
        $edgeVersionInfo = [Diagnostics.FileVersionInfo]::GetVersionInfo($edgePath)
        Assert-GateBInfrastructure ($null -ne $edgeVersionInfo -and
            [string]$edgeVersionInfo.FileVersion -match '^\d+\.\d+\.\d+\.\d+$') `
            'installed Edge executable did not expose a strict four-part file version'
        $edgeVersion = [string]$edgeVersionInfo.FileVersion
        $edgeDirectory = Split-Path -Parent $edgePath
        $helperPath = Join-Path (Join-Path $edgeDirectory $edgeVersion) `
            'identity_helper.exe'
        Assert-GateBInfrastructure (Test-Path -LiteralPath $helperPath -PathType Leaf) `
            'installed Edge identity_helper.exe was not available for the descendant companion canary'

        $probeId = [Guid]::NewGuid().ToString('N')
        $profile = Join-Path ([IO.Path]::GetTempPath()) `
            ('TroubleshootJS\gate-b-edge-companion-' + $probeId + '\profile')
        $owner = [pscustomobject]@{
            ProcessId = 400; Profile = $profile; RunId = 'gate-b-edge-run'
            RepositoryIdentity = 'gate-b-edge-worktree'; CdpPort = 45129
            BrowserPath = $edgePath; Name = 'msedge.exe'
        }
        $commandLine = ('"' + $helperPath + '" --type=utility ' +
            '--utility-sub-type=winrt_app_id.mojom.WinrtAppIdService ' +
            '--service-sandbox-type=windows_package_identity ' +
            '--user-data-dir="' + $profile + '"')
        $positive = [pscustomobject]@{
            ProcessId = 401; ParentProcessId = 400; ProcessStartTicks = 1L
            Name = 'identity_helper.exe'; ExecutablePath = $helperPath
            CommandLine = $commandLine
        }
        $coldWatch = [Diagnostics.Stopwatch]::StartNew()
        Assert-GateB (Test-VerifierDescendantExecutableIdentity $owner $positive) `
            'actual signed Edge identity_helper.exe was rejected by descendant policy'
        $coldWatch.Stop()
        $coldElapsed = $coldWatch.Elapsed.TotalMilliseconds
        $warmWatch = [Diagnostics.Stopwatch]::StartNew()
        foreach ($attempt in 1..3) {
            Assert-GateB (Test-VerifierDescendantExecutableIdentity $owner $positive) `
                "actual signed Edge identity_helper.exe failed warm matcher attempt $attempt"
        }
        $warmWatch.Stop()
        $warmTripleElapsed = $warmWatch.Elapsed.TotalMilliseconds
        Assert-GateB (Test-VerifierDescendantOwnership $owner $positive) `
            'synthetic descendant companion identity and marker policy proof failed'
        Assert-GateB (-not (Test-VerifierConfiguredExecutableIdentity $edgePath $positive `
            -RequireExecutablePath)) `
            'identity_helper.exe was admitted by the strict root executable identity seam'

        $negativeCases = @(
            [pscustomobject]@{ Name = 'wrong-root'; Owner = ($owner | Select-Object *)
                Candidate = ($positive | Select-Object *) }
            [pscustomobject]@{ Name = 'wrong-path'; Owner = $owner
                Candidate = ($positive | Select-Object *) }
            [pscustomobject]@{ Name = 'wrong-version'; Owner = $owner
                Candidate = ($positive | Select-Object *) }
            [pscustomobject]@{ Name = 'wrong-name'; Owner = $owner
                Candidate = ($positive | Select-Object *) }
            [pscustomobject]@{ Name = 'wrong-profile'; Owner = $owner
                Candidate = ($positive | Select-Object *) }
            [pscustomobject]@{ Name = 'wrong-type'; Owner = $owner
                Candidate = ($positive | Select-Object *) }
            [pscustomobject]@{ Name = 'wrong-subtype'; Owner = $owner
                Candidate = ($positive | Select-Object *) }
            [pscustomobject]@{ Name = 'wrong-sandbox'; Owner = $owner
                Candidate = ($positive | Select-Object *) }
            [pscustomobject]@{ Name = 'missing-name'; Owner = $owner
                Candidate = ($positive | Select-Object *) }
            [pscustomobject]@{ Name = 'missing-path'; Owner = $owner
                Candidate = ($positive | Select-Object *) }
            [pscustomobject]@{ Name = 'missing-commandline'; Owner = $owner
                Candidate = ($positive | Select-Object *) }
        )
        $negativeCases[0].Owner.BrowserPath = Join-Path $edgeDirectory 'missing-msedge.exe'
        $negativeCases[1].Candidate.ExecutablePath = Join-Path $edgeDirectory 'wrong-helper.exe'
        $negativeCases[2].Candidate.ExecutablePath = Join-Path $edgeDirectory `
            '0.0.0.0\identity_helper.exe'
        $negativeCases[3].Candidate.Name = 'msedge.exe'
        $negativeCases[4].Candidate.CommandLine = $commandLine.Replace(
            ('--user-data-dir="' + $profile + '"'),
            '--user-data-dir="C:\wrong-profile"')
        $negativeCases[5].Candidate.CommandLine = $commandLine.Replace(
            '--type=utility', '--type=renderer')
        $negativeCases[6].Candidate.CommandLine = $commandLine.Replace(
            '--utility-sub-type=winrt_app_id.mojom.WinrtAppIdService',
            '--utility-sub-type=wrong.Service')
        $negativeCases[7].Candidate.CommandLine = $commandLine.Replace(
            '--service-sandbox-type=windows_package_identity',
            '--service-sandbox-type=wrong')
        $negativeCases[8].Candidate.PSObject.Properties.Remove('Name')
        $negativeCases[9].Candidate.PSObject.Properties.Remove('ExecutablePath')
        $negativeCases[10].Candidate.PSObject.Properties.Remove('CommandLine')
        foreach ($case in $negativeCases) {
            Assert-GateB (-not (Test-VerifierDescendantExecutableIdentity `
                $case.Owner $case.Candidate)) `
                "Edge companion negative case '$($case.Name)' was accepted"
        }

        # Use disposable copies only for the signature negative. This keeps
        # the installed Edge files untouched while proving an otherwise exact
        # companion with a damaged Authenticode hash fails closed.
        $tempNamespace = Get-VerifierFullPath ([IO.Path]::GetTempPath())
        $fixtureRoot = Join-Path $tempNamespace `
            ('TroubleshootJS\gate-b-edge-companion-fixture-' + $probeId)
        $fixtureVersionDirectory = Join-Path $fixtureRoot $edgeVersion
        New-Item -ItemType Directory -Path $fixtureVersionDirectory -Force `
            -ErrorAction Stop | Out-Null
        $fixtureEdgePath = Join-Path $fixtureRoot 'msedge.exe'
        $fixtureHelperPath = Join-Path $fixtureVersionDirectory 'identity_helper.exe'
        Copy-Item -LiteralPath $edgePath -Destination $fixtureEdgePath `
            -Force -ErrorAction Stop
        Copy-Item -LiteralPath $helperPath -Destination $fixtureHelperPath `
            -Force -ErrorAction Stop
        $sourceEdgeVersionInfo = [Diagnostics.FileVersionInfo]::GetVersionInfo($edgePath)
        $sourceHelperVersionInfo = [Diagnostics.FileVersionInfo]::GetVersionInfo($helperPath)
        $fixtureEdgeVersionInfo = [Diagnostics.FileVersionInfo]::GetVersionInfo($fixtureEdgePath)
        $fixtureHelperVersionInfo = [Diagnostics.FileVersionInfo]::GetVersionInfo($fixtureHelperPath)
        Assert-GateB ($fixtureEdgeVersionInfo.FileVersion -ceq $sourceEdgeVersionInfo.FileVersion -and
            $fixtureEdgeVersionInfo.OriginalFilename -ceq $sourceEdgeVersionInfo.OriginalFilename -and
            $fixtureHelperVersionInfo.FileVersion -ceq $sourceHelperVersionInfo.FileVersion -and
            $fixtureHelperVersionInfo.OriginalFilename -ceq $sourceHelperVersionInfo.OriginalFilename) `
            'untouched copied Edge signature fixture changed file metadata'
        $fixtureEdgeSignature = @(Get-AuthenticodeSignature -LiteralPath $fixtureEdgePath `
            -ErrorAction Stop)
        $fixtureHelperSignature = @(Get-AuthenticodeSignature -LiteralPath $fixtureHelperPath `
            -ErrorAction Stop)
        Assert-GateB ($fixtureEdgeSignature.Count -eq 1 -and
            $fixtureHelperSignature.Count -eq 1 -and
            [string]$fixtureEdgeSignature[0].Status -ceq 'Valid' -and
            [string]$fixtureHelperSignature[0].Status -ceq 'Valid') `
            'untouched copied Edge signature fixture was not Authenticode-valid'
        $signatureOwner = $owner | Select-Object *
        $signatureOwner.BrowserPath = $fixtureEdgePath
        $signatureCandidate = $positive | Select-Object *
        $signatureCandidate.ExecutablePath = $fixtureHelperPath
        $signatureCandidate.CommandLine = $commandLine.Replace($helperPath,
            $fixtureHelperPath)
        Assert-GateB (Test-VerifierDescendantExecutableIdentity `
            $signatureOwner $signatureCandidate) `
            'untouched copied signed Edge companion was rejected by descendant policy'
        $fixtureBytes = [IO.File]::ReadAllBytes($fixtureHelperPath)
        Assert-GateBInfrastructure ($fixtureBytes.Length -gt 2048) `
            'signature fixture helper was unexpectedly short'
        $fixtureBytes[2048] = [byte]($fixtureBytes[2048] -bxor 0xFF)
        [IO.File]::WriteAllBytes($fixtureHelperPath, $fixtureBytes)
        $mutatedHelperVersionInfo = [Diagnostics.FileVersionInfo]::GetVersionInfo($fixtureHelperPath)
        Assert-GateB ($mutatedHelperVersionInfo.FileVersion -ceq $fixtureHelperVersionInfo.FileVersion -and
            $mutatedHelperVersionInfo.OriginalFilename -ceq $fixtureHelperVersionInfo.OriginalFilename) `
            'damaged Authenticode companion fixture changed file metadata'
        $mutatedHelperSignature = @(Get-AuthenticodeSignature -LiteralPath $fixtureHelperPath `
            -ErrorAction Stop)
        Assert-GateB ($mutatedHelperSignature.Count -eq 1 -and
            [string]$mutatedHelperSignature[0].Status -ceq 'HashMismatch') `
            ('damaged Authenticode companion fixture did not report HashMismatch ' +
                "($([string]$mutatedHelperSignature[0].Status))")
        Assert-GateB (-not (Test-VerifierDescendantExecutableIdentity `
            $signatureOwner $signatureCandidate)) `
            'damaged Authenticode companion fixture was accepted'
    } catch {
        $failure = $_
    } finally {
        if (-not [String]::IsNullOrWhiteSpace($fixtureRoot) -and
                (Test-Path -LiteralPath $fixtureRoot)) {
            try {
                Remove-VerifierOwnedTree (Get-VerifierFullPath ([IO.Path]::GetTempPath())) `
                    $fixtureRoot
            } catch {
                $cleanupError = $_
            }
        }
    }
    if ($null -ne $cleanupError) {
        Throw-GateBInfrastructure ('Edge companion canary fixture cleanup was not proven: ' +
            (Get-VerifierErrorMessage $cleanupError))
    }
    if ($null -ne $failure) { throw $failure }
    Write-Host ('PASS:Edge descendant companion metadata/path/signature/command-line ' +
        ('canary (coldMs={0:N1}; warmTripleMs={1:N1}; no Edge process ownership claimed)' -f `
            $coldElapsed, $warmTripleElapsed))
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

function Invoke-GateBBoundedProcessLaunchRaceCanary() {
    # Exercise the real short-lived netstat child while an actual loopback
    # listener is held open. The bounded runner must capture launch identity
    # before reader startup, retain complete output, and prove cleanup without
    # adopting a current process by PID.
    $listener = $null
    $listenerPort = 0
    $listenerCleanupProven = $false
    $timeoutEvidencePath = ''
    $canaryFailure = $null
    $cleanupErrors = New-Object Collections.ArrayList
    $checksValidated = $false
    try {
        $powershell = (Get-Command powershell.exe -ErrorAction Stop).Source
        $netstat = (Get-Command netstat.exe -ErrorAction Stop).Source
        $listener = [Net.Sockets.TcpListener]::new(
            [Net.IPAddress]::Parse('127.0.0.1'), 0)
        $listener.Start()
        $listenerPort = [int]([Net.IPEndPoint]$listener.LocalEndpoint).Port
        Assert-GateB ($listenerPort -ge 1 -and $listenerPort -le 65535) `
            'real loopback listener did not receive a valid ephemeral port'
        $ownerStartTicks = [long](Get-VerifierCurrentProcessStartTicks)

        # This is the real consumer route: netstat.exe is short-lived, its
        # stdout is parsed, and the listener record is tied back to this live
        # owner using the exact Process.StartTime identity.
        $inspection = Get-VerifierLoopbackListenerRecords $listenerPort `
            -PreferNetstat
        $listeners = @($inspection.Listeners | Where-Object {
            [int]$_.Port -eq $listenerPort -and
            [int]$_.ProcessId -eq [int]$PID
        })
        Assert-GateB ([string]$inspection.Source -ceq 'netstat' -and
            [bool]$inspection.Success -and [bool]$inspection.Known -and
            $listeners.Count -eq 1 -and
            [long]$listeners[0].ProcessStartTicks -eq $ownerStartTicks) `
            'real netstat listener route did not preserve the current owner start identity'

        # A same-process, immediate-exit child covers the launch/readers race.
        # Its exact numeric exit and complete marker output are accepted only
        # after the retained launch handle supplied a positive StartTicks.
        $quickResult = Invoke-VerifierBoundedProcess $powershell @(
            '-NoLogo', '-NoProfile', '-NonInteractive', '-ExecutionPolicy',
            'Bypass', '-Command', "Write-Output 'gate-b-launch-race'; exit 0"
        ) 5000
        $quickStdout = [string]$quickResult.Stdout
        $quickMarkerExact = $quickStdout -ceq 'gate-b-launch-race' -or
            $quickStdout -ceq "gate-b-launch-race`n" -or
            $quickStdout -ceq "gate-b-launch-race`r`n"
        Assert-GateB ([long]$quickResult.ProcessStartTicks -gt 0 -and
            [int]$quickResult.ExitCode -eq 0 -and
            [bool]$quickResult.TerminationProven -and
            $quickMarkerExact -and
            -not (Test-Path -LiteralPath $quickResult.ProcessRoot)) `
            'short-lived bounded child did not prove launch identity, exit, output, and log cleanup'

        # A long-lived child must still take the exact retained-identity stop
        # path when the bounded deadline expires. Wrap only the module's exact
        # stop helper inside its own scope so the canary records the Process
        # handle PID/start tuple actually supplied to the stop proof. The
        # original helper still performs the real stop and absence checks.
        $timeoutArguments = @(
            '-NoLogo', '-NoProfile', '-NonInteractive', '-ExecutionPolicy',
            'Bypass', '-Command',
            'Start-Sleep -Milliseconds 3000'
        )
        $module = @(Get-Module VerifierIsolation | Select-Object -First 1)
        Assert-GateB ($module.Count -eq 1) `
            'VerifierIsolation module was unavailable for bounded timeout proof'
        $timeoutProbe = & $module[0] {
            param($filePath, $childArguments)
            $oldStop = (Get-Command Stop-VerifierBoundedProcessExactly `
                -CommandType Function -ErrorAction Stop).ScriptBlock
            try {
                $script:GateBLaunchRaceStopCalls = 0
                $script:GateBLaunchRaceStopPid = 0
                $script:GateBLaunchRaceStopStartTicks = 0L
                $script:GateBLaunchRaceStopProcessType = ''
                $script:GateBLaunchRaceStopWaitMilliseconds = 0
                $script:GateBLaunchRaceStopOriginal = $oldStop
                Set-Item Function:\Stop-VerifierBoundedProcessExactly -Force -Value {
                    param($retainedProcess, $expectedStartTicks,
                        $waitMilliseconds = 5000)
                    $script:GateBLaunchRaceStopCalls =
                        [int]$script:GateBLaunchRaceStopCalls + 1
                    $script:GateBLaunchRaceStopPid = [int]$retainedProcess.Id
                    $script:GateBLaunchRaceStopStartTicks = [long]$expectedStartTicks
                    $script:GateBLaunchRaceStopProcessType =
                        [string]$retainedProcess.GetType().FullName
                    $script:GateBLaunchRaceStopWaitMilliseconds =
                        [int]$waitMilliseconds
                    & $script:GateBLaunchRaceStopOriginal $retainedProcess `
                        $expectedStartTicks $waitMilliseconds
                }
                $typedFailure = $false
                $errorMessage = ''
                try {
                    [void](Invoke-VerifierBoundedProcess $filePath `
                        $childArguments 100)
                } catch {
                    $typedFailure = Test-VerifierInfrastructureError $_
                    $errorMessage = Get-VerifierErrorMessage $_
                }
                return [pscustomobject]@{
                    TypedFailure = $typedFailure
                    Error = $errorMessage
                    StopCalls = [int]$script:GateBLaunchRaceStopCalls
                    StopPid = [int]$script:GateBLaunchRaceStopPid
                    StopStartTicks = [long]$script:GateBLaunchRaceStopStartTicks
                    StopProcessType = [string]$script:GateBLaunchRaceStopProcessType
                    StopWaitMilliseconds = [int]$script:GateBLaunchRaceStopWaitMilliseconds
                }
            } finally {
                Set-Item Function:\Stop-VerifierBoundedProcessExactly `
                    -Force -Value $oldStop
                Remove-Variable -Name GateBLaunchRaceStopCalls,`
                    GateBLaunchRaceStopPid,GateBLaunchRaceStopStartTicks,`
                    GateBLaunchRaceStopProcessType,GateBLaunchRaceStopWaitMilliseconds,`
                    GateBLaunchRaceStopOriginal `
                    -Scope Script -Force -ErrorAction SilentlyContinue
            }
        } $powershell $timeoutArguments
        $timeoutObserved = [bool]$timeoutProbe.TypedFailure
        $timeoutError = [string]$timeoutProbe.Error
        Assert-GateB $timeoutObserved `
            'bounded-process timeout did not return typed infrastructure failure'
        Assert-GateB ([int]$timeoutProbe.StopCalls -eq 1 -and
            [int]$timeoutProbe.StopPid -gt 0 -and
            [long]$timeoutProbe.StopStartTicks -gt 0 -and
            [string]$timeoutProbe.StopProcessType -ceq
                'System.Diagnostics.Process' -and
            [int]$timeoutProbe.StopWaitMilliseconds -eq 5000) `
            'bounded-process timeout did not invoke exact stop with one retained Process identity'
        # Extract only the evidence path needed to construct the canonical
        # expected message. Acceptance is a whole-message equality check: the
        # typed infrastructure prefix, exact executable, exact deadline text,
        # and the same evidence root must appear in the one permitted order.
        # A cleanup/disposal/output uncertainty inserted before the evidence
        # marker therefore cannot qualify as a timeout pass.
        $timeoutEvidenceMatch = [regex]::Match($timeoutError,
            "logs retained at '([^']+)'")
        Assert-GateB $timeoutEvidenceMatch.Success `
            'bounded-process timeout did not name a retained evidence root'
        $timeoutEvidencePath = $timeoutEvidenceMatch.Groups[1].Value
        $expectedTimeoutError =
            "VERIFIER_INFRASTRUCTURE: VERIFIER_INFRASTRUCTURE: Bounded process '$powershell' exceeded 100ms; logs retained at '$timeoutEvidencePath'. Bounded-process evidence: '$timeoutEvidencePath'."
        $timeoutDiagnosticOracle = {
            param($candidate, $expected)
            return ([string]$candidate -ceq [string]$expected)
        }
        Assert-GateB (& $timeoutDiagnosticOracle $timeoutError `
            $expectedTimeoutError) `
            'bounded-process timeout did not match the complete canonical diagnostic'
        # Focused negative oracle: the same clean timeout with appended
        # cleanup uncertainty must be rejected by the whole-message matcher.
        $uncertainTimeoutError = $expectedTimeoutError.Replace(
            ' Bounded-process evidence:',
            '; bounded-process cleanup was not proven: injected uncertainty. Bounded-process evidence:')
        Assert-GateB (-not (& $timeoutDiagnosticOracle $uncertainTimeoutError `
            $expectedTimeoutError)) `
            'timeout diagnostic oracle accepted appended cleanup uncertainty'

        $tempNamespace = Get-VerifierFullPath ([IO.Path]::GetTempPath())
        Assert-VerifierNoReparseAncestors $timeoutEvidencePath
        Assert-GateB (Test-VerifierPhysicalChildPath $tempNamespace $timeoutEvidencePath) `
            'bounded-process timeout evidence escaped the owned temp namespace'
        $timeoutStdoutPath = Join-Path $timeoutEvidencePath 'stdout.log'
        $timeoutStderrPath = Join-Path $timeoutEvidencePath 'stderr.log'
        Assert-GateB ((Test-Path -LiteralPath $timeoutStdoutPath -PathType Leaf) -and
            (Test-Path -LiteralPath $timeoutStderrPath -PathType Leaf) -and
            (Test-VerifierPhysicalChildPath $timeoutEvidencePath $timeoutStdoutPath) -and
            (Test-VerifierPhysicalChildPath $timeoutEvidencePath $timeoutStderrPath)) `
            'bounded-process timeout did not retain both owned output streams'
        # A timed-out child is allowed to produce no output. Read both retained
        # files directly so an empty stream remains a valid closed capture;
        # Get-Content -Raw returns $null for a zero-byte file and `.Trim()`
        # would turn that valid timeout into a null-valued-expression failure.
        try {
            $timeoutStdout = [IO.File]::ReadAllText($timeoutStdoutPath)
            $timeoutStderr = [IO.File]::ReadAllText($timeoutStderrPath)
        } catch {
            Throw-GateBInfrastructure ('bounded-process timeout retained output files that could not be read: ' +
                (Get-VerifierErrorMessage $_))
        }
        Assert-GateB ($timeoutStdout -is [string] -and $timeoutStderr -is [string]) `
            'bounded-process timeout retained output streams without readable string contents'
        # The real stop wrapper records the retained Process handle identity;
        # timeout proof must use that tuple and never depend on child stdout.
        $timeoutPid = [int]$timeoutProbe.StopPid
        Assert-GateB ((Test-VerifierStrictIntegralValue $timeoutPid 1 ([int]::MaxValue)) -and
            (Test-VerifierStrictIntegralValue $timeoutProbe.StopStartTicks `
                1 ([long]::MaxValue))) `
            'bounded-process timeout stop tuple carried malformed PID or retained start identity'
        $timeoutRecord = [pscustomobject]@{
            ProcessId = $timeoutPid
            ProcessStartTicks = [long]$timeoutProbe.StopStartTicks
            CommandLine = ''
        }
        $timeoutAbsence = Confirm-VerifierRecordedProcessAbsent $timeoutRecord `
            'bounded timeout child'
        Assert-GateB ([bool]$timeoutAbsence.QueryProven -and
            [bool]$timeoutAbsence.Absent -and
            $null -eq $timeoutAbsence.Current -and
            -not [bool]$timeoutAbsence.Replaced) `
            'bounded-process timeout did not prove exact child termination and current-process absence'
        $checksValidated = $true
    } catch {
        $canaryFailure = $_
    } finally {
        if ($null -ne $listener) {
            try {
                $listener.Stop()
                $listener = $null
            } catch {
                [void]$cleanupErrors.Add(('real loopback listener stop failed: ' +
                    (Get-VerifierErrorMessage $_)))
            }
        }
        if ($listenerPort -gt 0) {
            try {
                $postStopInspection = Get-VerifierLoopbackListenerRecords `
                    $listenerPort -PreferNetstat
                if (-not $postStopInspection.Success -or
                        -not $postStopInspection.Known -or
                        $postStopInspection.HasListeners) {
                    throw "post-stop listener inspection was not proven quiescent for port $listenerPort"
                }
                $listenerCleanupProven = $true
            } catch {
                [void]$cleanupErrors.Add(('real loopback listener absence was not proven: ' +
                    (Get-VerifierErrorMessage $_)))
            }
        }
    }
    if ($cleanupErrors.Count -gt 0) {
        $evidenceText = if ([String]::IsNullOrWhiteSpace($timeoutEvidencePath)) {
            ''
        } else { "; timeout evidence retained at '$timeoutEvidencePath'" }
        Throw-GateBInfrastructure ('real launch/netstat canary cleanup was not proven' +
            $evidenceText + ': ' + ($cleanupErrors -join '; '))
    }
    if ($null -ne $canaryFailure) { throw $canaryFailure }
    Assert-GateB ($checksValidated -and $listenerCleanupProven) `
        'real launch/netstat canary did not complete its checks and fixture cleanup proof'
    Write-Host ('PASS:real netstat listener and bounded launch race prove retained ' +
        'start identity, complete output/log cleanup, exact timeout stop/absence, ' +
        'and listener cleanup ' +
        "(port=$listenerPort; ownerStartTicks=$ownerStartTicks; timeoutPid=$timeoutPid; " +
        "timeoutStartTicks=$($timeoutProbe.StopStartTicks); timeoutStopWaitMs=$($timeoutProbe.StopWaitMilliseconds); " +
        "timeoutEvidence='$timeoutEvidencePath'; " +
        "timeoutAbsent=$($timeoutAbsence.Absent); listenerAbsent=$listenerCleanupProven)")
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
        RunId = $runId; PreviewNonce = $nonce; ManifestWritePhase = ''
        CleanupState = 'pending'; CleanupCompletedUtc = ''
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
            $context $owner -StructuralOnly) `
        'valid PID 4 kernel transport structural record was rejected'
    Assert-GateB (-not (Test-VerifierKernelTransportListenerRecord $kernelListener `
            $context $owner)) `
        'kernel transport record was semantically reauthorized without a carried proof'
    Assert-GateB (Test-VerifierRunOwnedPreviewHttpSysAuthorization $context $owner $port) `
        'exact run-owned preview identity handshake proof was rejected'
    $initialKernelProof = & $module[0] {
        param($proofContext, $proofOwner, $proofPort)
        New-VerifierRunOwnedPreviewHttpSysAuthorizationProof `
            $proofContext $proofOwner $proofPort
    } $context $owner $port
    Assert-GateB ($null -ne $initialKernelProof -and
        (Test-VerifierRunOwnedPreviewHttpSysListener $context $owner `
            $kernelListener $initialKernelProof)) `
        'PID 4 kernel transport listener was not accepted with the carried preview proof'

    # A valid kernel preview must not spend the bounded browser-snapshot
    # budget on an irrelevant census.  Inject a deliberately slow snapshot
    # dependency; listener-first proof must bind without invoking it.
    $kernelBindProbe = & $module[0] {
        param($probeContext, $probeLease, $probeOwner, $probeInspection,
            $probeProcessId, $probeProcessStart)
        $oldLoopback = (Get-Command Get-VerifierLoopbackListenerRecords `
            -CommandType Function -ErrorAction Stop).ScriptBlock
        $oldSnapshot = (Get-Command Get-VerifierBrowserOwnershipSnapshot `
            -CommandType Function -ErrorAction Stop).ScriptBlock
        $oldAuthorization = (Get-Command Test-VerifierRunOwnedPreviewHttpSysAuthorization `
            -CommandType Function -ErrorAction Stop).ScriptBlock
        try {
            $script:GateBSlowKernelSnapshotCalled = $false
            $script:GateBKernelSemanticAuthorizationCalls = 0
            $script:GateBKernelListenerQueries = 0
            Set-Item Function:\Get-VerifierLoopbackListenerRecords -Force -Value {
                $script:GateBKernelListenerQueries++
                return $probeInspection
            }
            Set-Item Function:\Get-VerifierBrowserOwnershipSnapshot -Force -Value {
                $script:GateBSlowKernelSnapshotCalled = $true
                Start-Sleep -Milliseconds 600
                return @()
            }
            Set-Item Function:\Test-VerifierRunOwnedPreviewHttpSysAuthorization -Force -Value {
                $script:GateBKernelSemanticAuthorizationCalls++
                return [bool](& $script:GateBKernelSemanticAuthorizationOriginal `
                    $args[0] $args[1] $args[2])
            }
            $script:GateBKernelSemanticAuthorizationOriginal = $oldAuthorization
            $accepted = $false
            $proof = $null
            try {
                $proof = Get-VerifierPortLeaseBoundOwnershipProof `
                    $probeContext $probeLease $probeProcessId $probeProcessStart `
                    $probeOwner $probeOwner
                $accepted = $null -ne $proof
            } catch { $accepted = $false }
            return [pscustomobject]@{
                Accepted = $accepted
                SnapshotCalled = [bool]$script:GateBSlowKernelSnapshotCalled
                SemanticAuthorizationCalls = [int]$script:GateBKernelSemanticAuthorizationCalls
                ListenerQueries = [int]$script:GateBKernelListenerQueries
                ProofStage = if ($null -eq $proof) { '' } else { [string]$proof.ProofStage }
                ProofElapsedMilliseconds = if ($null -eq $proof) { -1L } else {
                    [long]$proof.ProofElapsedMilliseconds
                }
            }
        } finally {
            Set-Item Function:\Get-VerifierLoopbackListenerRecords -Force `
                -Value $oldLoopback
            Set-Item Function:\Get-VerifierBrowserOwnershipSnapshot -Force `
                -Value $oldSnapshot
            Set-Item Function:\Test-VerifierRunOwnedPreviewHttpSysAuthorization -Force `
                -Value $oldAuthorization
            Remove-Variable -Name GateBSlowKernelSnapshotCalled,`
                GateBKernelSemanticAuthorizationCalls,GateBKernelSemanticAuthorizationOriginal,`
                GateBKernelListenerQueries `
                -Force -ErrorAction SilentlyContinue
        }
    } $context $lease $owner ([pscustomobject]@{
        Success = $true; Known = $true; HasListeners = $true
        Listeners = @($kernelListener); ListenerOwnerKind = 'kernel-transport'
        ListenerOwnerProof = 'run-owned-preview-http-sys-v1'
        ListenerOwnerEvidence = 'pid-4-system-http-sys'
        Source = 'Get-NetTCPConnection'; Error = ''
    }) $PID $currentStart
    Assert-GateB ([bool]$kernelBindProbe.Accepted -and
        -not [bool]$kernelBindProbe.SnapshotCalled -and
        [int]$kernelBindProbe.SemanticAuthorizationCalls -eq 1 -and
        [int]$kernelBindProbe.ListenerQueries -eq 1 -and
        $kernelBindProbe.ProofStage -ceq 'kernel-semantic-authorization' -and
        [long]$kernelBindProbe.ProofElapsedMilliseconds -ge 0) `
        'valid kernel preview bind invoked or failed on a slow browser snapshot dependency'

    # A structurally mixed inspection is not a durable lease state (the lease
    # records one canonical owner tuple), but the bound-proof routine still
    # has a defensive mixed branch for an OS snapshot containing both the
    # HTTP.sys transport record and a user-process record.  Exercise that
    # branch with only the structural inspection aggregate mocked: the kernel
    # token must be created once, consumed by the kernel listener, and never
    # reacquired by a downstream consumer.
    $mixedKernelBindProbe = & $module[0] {
        param($probeContext, $probeLease, $probeOwner, $kernelValue,
            $processId, $processStart)
        $saved = @{}
        foreach ($name in @(
                'Get-VerifierLoopbackListenerRecords',
                'Get-VerifierBrowserOwnershipSnapshot',
                'Test-VerifierRunOwnedPreviewHttpSysAuthorization',
                'Test-VerifierListenerInspectionSchema',
                'Test-VerifierListenerBelongsToOwner',
                'Test-VerifierRunOwnedPreviewHttpSysListener')) {
            $saved[$name] = (Get-Command $name -CommandType Function `
                -ErrorAction Stop).ScriptBlock
        }
        try {
            $userValue = $kernelValue | Select-Object *
            $userValue.ProcessId = [int]$processId
            $userValue.ProcessStartTicks = [long]$processStart
            $userValue.ListenerOwnerKind = 'user-process'
            $userValue.ListenerOwnerProof = 'diagnostics-process-start-v1'
            $userValue.ListenerOwnerEvidence = 'system-diagnostics-process-starttime'
            $mixedInspection = [pscustomobject]@{
                Success = $true; Known = $true; HasListeners = $true
                Listeners = @($kernelValue, $userValue)
                ListenerOwnerKind = 'kernel-transport'
                ListenerOwnerProof = 'run-owned-preview-http-sys-v1'
                ListenerOwnerEvidence = 'pid-4-system-http-sys'
                Source = 'Get-NetTCPConnection'; Error = ''
            }
            $script:GateBMixedListenerQueries = 0
            $script:GateBMixedSnapshotQueries = 0
            $script:GateBMixedBelongsQueries = 0
            $script:GateBMixedSemanticCalls = 0
            $script:GateBMixedProofConsumerCalls = 0
            $script:GateBMixedMissingProof = $false
            $script:GateBMixedAuthorizationOriginal =
                $saved['Test-VerifierRunOwnedPreviewHttpSysAuthorization']
            $script:GateBMixedListenerOriginal =
                $saved['Test-VerifierRunOwnedPreviewHttpSysListener']
            Set-Item Function:\Get-VerifierLoopbackListenerRecords -Force -Value {
                $script:GateBMixedListenerQueries++
                return $script:GateBMixedInspection
            }
            Set-Item Function:\Get-VerifierBrowserOwnershipSnapshot -Force -Value {
                $script:GateBMixedSnapshotQueries++
                return @([pscustomobject]@{ ProcessId = $processId })
            }
            Set-Item Function:\Test-VerifierRunOwnedPreviewHttpSysAuthorization `
                -Force -Value {
                $script:GateBMixedSemanticCalls++
                return $true
            }
            # The aggregate's owner kind is necessarily one scalar in the
            # existing inspection schema.  This probe admits only this
            # synthetic mixed aggregate; each listener still crosses the real
            # structural record validator and the real proof consumer.
            Set-Item Function:\Test-VerifierListenerInspectionSchema -Force -Value {
                param($candidateInspection)
                return [object]::ReferenceEquals($candidateInspection,
                    $script:GateBMixedInspection) -or
                    ($null -ne $candidateInspection -and
                     $candidateInspection.PSObject.Properties['Listeners'] -and
                     @($candidateInspection.Listeners).Count -eq 2)
            }
            Set-Item Function:\Test-VerifierListenerBelongsToOwner -Force -Value {
                $script:GateBMixedBelongsQueries++
                return $true
            }
            Set-Item Function:\Test-VerifierRunOwnedPreviewHttpSysListener `
                -Force -Value {
                param($candidateContext, $candidateOwner, $candidateListener,
                    $candidateProof)
                if ($candidateListener.ListenerOwnerKind -ceq 'kernel-transport') {
                    $script:GateBMixedProofConsumerCalls++
                    if ($null -eq $candidateProof) {
                        $script:GateBMixedMissingProof = $true
                        return $false
                    }
                }
                return [bool](& $script:GateBMixedListenerOriginal `
                    $candidateContext $candidateOwner $candidateListener $candidateProof)
            }
            $script:GateBMixedInspection = $mixedInspection
            $proofResult = $null
            $accepted = $false
            $typedFailure = $false
            try {
                $proofResult = Get-VerifierPortLeaseBoundOwnershipProof `
                    $probeContext $probeLease $processId $processStart `
                    $probeOwner $probeOwner
                $accepted = $null -ne $proofResult
            } catch {
                $typedFailure = Test-VerifierInfrastructureError $_
            }
            return [pscustomobject]@{
                Accepted = $accepted; TypedFailure = $typedFailure
                ListenerQueries = [int]$script:GateBMixedListenerQueries
                SnapshotQueries = [int]$script:GateBMixedSnapshotQueries
                BelongsQueries = [int]$script:GateBMixedBelongsQueries
                SemanticCalls = [int]$script:GateBMixedSemanticCalls
                ProofConsumerCalls = [int]$script:GateBMixedProofConsumerCalls
                MissingProof = [bool]$script:GateBMixedMissingProof
                HasProof = ($null -ne $proofResult -and
                    $null -ne $proofResult.AuthorizationProof)
            }
        } finally {
            foreach ($name in $saved.Keys) {
                Set-Item Function:\$name -Force -Value $saved[$name]
            }
            Remove-Variable -Name GateBMixedInspection,GateBMixedListenerQueries,`
                GateBMixedSnapshotQueries,GateBMixedBelongsQueries,`
                GateBMixedSemanticCalls,GateBMixedProofConsumerCalls,`
                GateBMixedMissingProof,GateBMixedAuthorizationOriginal,`
                GateBMixedListenerOriginal -Scope Script -Force `
                -ErrorAction SilentlyContinue
        }
    } $context $lease $owner $kernelListener $PID $currentStart
    Assert-GateB ([bool]$mixedKernelBindProbe.Accepted -and
        -not [bool]$mixedKernelBindProbe.TypedFailure -and
        [int]$mixedKernelBindProbe.ListenerQueries -eq 1 -and
        [int]$mixedKernelBindProbe.SnapshotQueries -eq 1 -and
        [int]$mixedKernelBindProbe.BelongsQueries -eq 1 -and
        [int]$mixedKernelBindProbe.SemanticCalls -eq 1 -and
        [int]$mixedKernelBindProbe.ProofConsumerCalls -eq 1 -and
        -not [bool]$mixedKernelBindProbe.MissingProof -and
        [bool]$mixedKernelBindProbe.HasProof) `
        'mixed kernel/user bound-proof path did not carry one opaque semantic authorization proof'

    # Release performs a new structural listener query, but it is a downstream
    # consumer of the capability retained on the bound lease.  Use the same
    # exact live owner/lease and force a positive kernel observation; release
    # must reject the still-listening port after the setter consumes the
    # carried proof, without invoking the semantic handshake again.
    $releaseKernelProbe = & $module[0] {
        param($probeContext, $probeLease, $probeOwner, $kernelValue,
            $processStart)
        $saved = @{}
        foreach ($name in @(
                'Get-VerifierLoopbackListenerRecords',
                'Test-VerifierRunOwnedPreviewHttpSysAuthorization',
                'Test-Path', 'Get-Content',
                'Assert-VerifierPhysicalOwnedPath',
                'Test-VerifierPhysicalChildPath',
                'Assert-VerifierNoReparseAncestors')) {
            # Test-Path/Get-Content are cmdlets rather than module functions;
            # retain their command kind so the temporary function shadow can
            # be removed cleanly in the finally block.
            $command = Get-Command $name -ErrorAction Stop
            # Keep immutable command-kind/script text values.  A live
            # FunctionInfo is mutated in place when Set-Item replaces its
            # function, so retaining it would make its ScriptBlock point at
            # the temporary mock and restore that mock after this canary.
            $saved[$name] = [pscustomobject]@{
                CommandType = [string]$command.CommandType
                ScriptBlock = if ([string]$command.CommandType -ceq 'Function') {
                    $command.ScriptBlock
                } else { $null }
            }
        }
        $probeResult = $null
        try {
            $releaseInspection = [pscustomobject]@{
                Success = $true; Known = $true; HasListeners = $true
                Listeners = @($kernelValue)
                ListenerOwnerKind = 'kernel-transport'
                ListenerOwnerProof = 'run-owned-preview-http-sys-v1'
                ListenerOwnerEvidence = 'pid-4-system-http-sys'
                Source = 'Get-NetTCPConnection'; Error = ''
            }
            $claimValue = [pscustomobject]@{
                protocol = 'troubleshootjs-verifier-port-claim-v1'
                runId = $probeContext.RunId
                repositoryIdentity = $probeContext.RepositoryIdentity
                worktreeRoot = $probeContext.WorktreeRoot
                kind = $probeLease.Kind; leaseId = $probeLease.LeaseId
                path = $probeLease.Path; port = $probeLease.Port
                mutexName = $probeLease.ClaimName; ownerPid = $PID
                ownerStartTicks = $processStart
            }
            $global:GateBReleaseInspection = $releaseInspection
            $global:GateBReleaseClaim = $claimValue
            $global:GateBReleaseListenerQueries = 0
            $global:GateBReleaseSemanticCalls = 0
            Set-Item Function:\Get-VerifierLoopbackListenerRecords -Force -Value {
                $global:GateBReleaseListenerQueries++
                return $global:GateBReleaseInspection
            }
            Set-Item Function:\Test-VerifierRunOwnedPreviewHttpSysAuthorization `
                -Force -Value {
                $counter = Get-Variable GateBReleaseSemanticCalls -Scope Global `
                    -ErrorAction SilentlyContinue
                if ($null -eq $counter) {
                    Set-Variable -Name GateBReleaseSemanticCalls -Scope Global `
                        -Value 0 -Force
                }
                $global:GateBReleaseSemanticCalls =
                    [int]$global:GateBReleaseSemanticCalls + 1
                return $true
            }
            # The synthetic claim is already represented by the held lease;
            # these path-boundary shims keep the probe focused on proof
            # carriage and never permit the release body to delete a file.
            Set-Item Function:\Test-Path -Force -Value {
                param($LiteralPath, $PathType, $ErrorAction)
                return ([string]$LiteralPath -ceq
                    [string]$global:GateBReleaseClaim.path)
            }
            Set-Item Function:\Get-Content -Force -Value {
                param($LiteralPath, [switch]$Raw, $ErrorAction)
                return ($global:GateBReleaseClaim | ConvertTo-Json -Compress -Depth 8)
            }
            Set-Item Function:\Assert-VerifierPhysicalOwnedPath -Force -Value { }
            Set-Item Function:\Test-VerifierPhysicalChildPath -Force -Value { return $true }
            Set-Item Function:\Assert-VerifierNoReparseAncestors -Force -Value { }

            if (-not $probeLease.PSObject.Properties['AuthorizationProof']) {
                Add-Member -InputObject $probeLease -MemberType NoteProperty `
                    -Name AuthorizationProof -Value $null
            }
            $proof = $null
            $proofCreationError = ''
            try {
                $proof = New-VerifierRunOwnedPreviewHttpSysAuthorizationProof `
                    $probeContext $probeOwner ([int]$probeLease.Port)
                $probeLease.AuthorizationProof = $proof
            } catch {
                $proofCreationError = Get-VerifierErrorMessage $_
            }
            $typedFailure = $false
            $releaseError = ''
            if ($null -eq $proof) {
                $typedFailure = $false
                $releaseError = 'proof creation: ' + $proofCreationError
            } else {
              try {
                Release-VerifierPortLease $probeContext $probeLease
              } catch {
                $typedFailure = Test-VerifierInfrastructureError $_
                $releaseError = Get-VerifierErrorMessage $_
              }
            }
            $semanticCallCount = if (Get-Variable GateBReleaseSemanticCalls `
                    -Scope Global -ErrorAction SilentlyContinue) {
                [int]$global:GateBReleaseSemanticCalls
            } else { -1 }
            $probeResult = [pscustomobject]@{
                TypedFailure = $typedFailure
                ProofCreated = ($null -ne $proof)
                ListenerQueries = [int]$global:GateBReleaseListenerQueries
                SemanticCalls = $semanticCallCount
                SetterReceivedProof = ($probeLease.ListenerOwnerKind -ceq
                    'kernel-transport' -and
                    [object]::ReferenceEquals($probeLease.AuthorizationProof, $proof))
                LeaseStillHeld = ($probeLease.ReleaseState -ceq 'active' -and
                    -not $probeLease.MutexReleased)
                RestorationExact = $false
                Error = $releaseError
            }
        } finally {
            foreach ($name in $saved.Keys) {
                if ($saved[$name].CommandType -ceq 'Function') {
                    Set-Item Function:\$name -Force `
                        -Value $saved[$name].ScriptBlock
                } else {
                    Remove-Item Function:\$name -Force `
                        -ErrorAction SilentlyContinue
                }
            }
            $restorationExact = $true
            foreach ($name in $saved.Keys) {
                $expected = $saved[$name]
                $actual = Get-Command $name -ErrorAction Stop
                if ($expected.CommandType -ceq 'Function') {
                    $expectedText = if ($null -eq $expected.ScriptBlock) {
                        ''
                    } else { $expected.ScriptBlock.ToString() }
                    $actualText = if ($null -eq $actual.ScriptBlock) {
                        ''
                    } else { $actual.ScriptBlock.ToString() }
                    if ($actual.CommandType -ne 'Function' -or
                            $actualText -cne $expectedText) {
                        $restorationExact = $false
                    }
                } elseif ([string]$actual.CommandType -cne $expected.CommandType -or
                        [string]$actual.Name -cne $name) {
                    $restorationExact = $false
                }
            }
            if ($null -ne $probeResult) {
                $probeResult.RestorationExact = $restorationExact
            }
            Remove-Variable -Name GateBReleaseInspection,GateBReleaseClaim,`
                GateBReleaseListenerQueries,GateBReleaseSemanticCalls `
                -Scope Global -Force `
                -ErrorAction SilentlyContinue
            }
        return $probeResult
    } $context $lease $owner $kernelListener $currentStart
    Assert-GateB ([bool]$releaseKernelProbe.TypedFailure -and
        [bool]$releaseKernelProbe.ProofCreated -and
        [int]$releaseKernelProbe.ListenerQueries -eq 1 -and
        [int]$releaseKernelProbe.SemanticCalls -eq 1 -and
        [bool]$releaseKernelProbe.SetterReceivedProof -and
        [bool]$releaseKernelProbe.LeaseStillHeld) `
        ('release listener consumer reauthorized or failed to carry the one-time kernel proof ' +
         '(typed={0}; created={1}; queries={2}; semantic={3}; setterProof={4}; leaseHeld={5}; error={6})' -f
            $releaseKernelProbe.TypedFailure, $releaseKernelProbe.ProofCreated,
            $releaseKernelProbe.ListenerQueries, $releaseKernelProbe.SemanticCalls,
            $releaseKernelProbe.SetterReceivedProof, $releaseKernelProbe.LeaseStillHeld,
            $releaseKernelProbe.Error)
    Assert-GateB ([bool]$releaseKernelProbe.RestorationExact) `
        'kernel release probe did not restore the exact pre-canary command definitions'

    # The release probe temporarily shadows both module functions and native
    # commands.  Prove that the same PowerShell process can immediately create
    # and complete a real run context after those shadows are removed.
    $sequentialKernelContext = $null
    $sequentialKernelScratch = Join-Path ([IO.Path]::GetTempPath()) `
        ('TroubleshootJS\gate-b-kernel-sequential-' +
         [Guid]::NewGuid().ToString('N'))
    try {
        $sequentialKernelContext = New-VerifierRunContext $repositoryRootForCanary
        $sequentialKernelCleanup = Complete-VerifierRun $sequentialKernelContext
        Assert-GateB ($null -ne $sequentialKernelCleanup -and
            [bool]$sequentialKernelCleanup.Success) `
            'same-process real run context failed after kernel transport canary'
        Assert-GateBContextResourcesReleased $sequentialKernelContext
        Remove-GateBCanaryRoots $sequentialKernelScratch @($sequentialKernelContext)
        Assert-GateB (-not (Test-Path -LiteralPath $sequentialKernelContext.RunRoot)) `
            'same-process sequential kernel context cleanup retained its run root'
    } finally {
        if ($null -ne $sequentialKernelContext -and
                (Test-Path -LiteralPath $sequentialKernelContext.RunRoot)) {
            try {
                $retryCleanup = Complete-VerifierRun $sequentialKernelContext
                if ($retryCleanup -and [bool]$retryCleanup.Success) {
                    Remove-GateBCanaryRoots $sequentialKernelScratch @($sequentialKernelContext)
                }
            } catch { }
        }
    }

    # A kernel proof is an opaque, one-semantic-auth capability.  Its later
    # consumers must reject both forged markers and mutation of any retained
    # owner/lease identity, while still re-reading the live exact process.
    $context.Server = $owner
    $proofForMutation = & $module[0] {
        param($proofContext, $proofOwner, $proofPort)
        New-VerifierRunOwnedPreviewHttpSysAuthorizationProof `
            $proofContext $proofOwner $proofPort
    } $context $owner $port
    Assert-GateB ($null -ne $proofForMutation -and
        (Test-VerifierRunOwnedPreviewHttpSysListener $context $owner `
            $kernelListener $proofForMutation)) `
        'valid kernel authorization proof was not consumable after creation'
    $replacementServer = $owner | Select-Object *
    $context.Server = $replacementServer
    Assert-GateB (-not (Test-VerifierRunOwnedPreviewHttpSysListener $context `
            $owner $kernelListener $proofForMutation)) `
        'kernel proof remained valid after Context.Server replacement'
    $context.Server = $owner
    $savedOwnerState = $owner.State
    $owner.State = 'cleaned'
    Assert-GateB (-not (Test-VerifierRunOwnedPreviewHttpSysListener $context `
            $owner $kernelListener $proofForMutation)) `
        'kernel proof remained valid after owner lifecycle mutation'
    $owner.State = $savedOwnerState
    $savedLeaseReleaseState = $owner.Lease.ReleaseState
    $owner.Lease.ReleaseState = 'os-released'
    Assert-GateB (-not (Test-VerifierRunOwnedPreviewHttpSysListener $context `
            $owner $kernelListener $proofForMutation)) `
        'kernel proof remained valid after lease release-state mutation'
    $owner.Lease.ReleaseState = $savedLeaseReleaseState
    $savedContextCleanupState = $context.CleanupState
    $context.CleanupState = 'complete'
    Assert-GateB (-not (Test-VerifierRunOwnedPreviewHttpSysListener $context `
            $owner $kernelListener $proofForMutation)) `
        'kernel proof remained valid after context lifecycle mutation'
    $context.CleanupState = $savedContextCleanupState
    $savedOwnerPid = $owner.ProcessId
    $owner.ProcessId = $savedOwnerPid + 1
    Assert-GateB (-not (Test-VerifierRunOwnedPreviewHttpSysListener $context `
            $owner $kernelListener $proofForMutation)) `
        'kernel proof remained valid after owner PID mutation'
    $owner.ProcessId = $savedOwnerPid
    $savedOwnerStart = $owner.ProcessStartTicks
    $owner.ProcessStartTicks = [long]$savedOwnerStart + 1L
    Assert-GateB (-not (Test-VerifierRunOwnedPreviewHttpSysListener $context `
            $owner $kernelListener $proofForMutation)) `
        'kernel proof remained valid after owner start identity mutation'
    $owner.ProcessStartTicks = $savedOwnerStart
    $savedLeaseStart = $owner.Lease.BoundProcessStartTicks
    $owner.Lease.BoundProcessStartTicks = [long]$savedLeaseStart + 1L
    Assert-GateB (-not (Test-VerifierRunOwnedPreviewHttpSysListener $context `
            $owner $kernelListener $proofForMutation)) `
        'kernel proof remained valid after lease identity mutation'
    $owner.Lease.BoundProcessStartTicks = $savedLeaseStart
    $forgedProof = $proofForMutation | Select-Object *
    $forgedProof.Marker = [object]::new()
    Assert-GateB (-not (Test-VerifierRunOwnedPreviewHttpSysListener $context `
            $owner $kernelListener $forgedProof)) `
        'forged kernel authorization proof marker was accepted'

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
    Assert-GateB (-not (Test-VerifierKernelTransportListenerRecord $kernelListener `
        $context $browserOwner)) `
        'browser/caller listener schema consumed a kernel record without a carried proof'

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
    $malformedServerRejected = & $module[0] {
        param($diagnosticContext)
        try {
            Assert-VerifierDurableServerLease $diagnosticContext 'malformed claim diagnostic'
            return $false
        } catch { return (Test-VerifierInfrastructureError $_) }
    } $context
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
    Assert-GateB ([bool]$malformedServerRejected -and
        -not [bool]$malformedAuthorizationResult.QueryObserved) `
        ('HTTP.sys authorization boundary accepted malformed ClaimMutex or queried before rejection ' +
         '(serverRejected={0}; queried={1}; claimType={2})' -f
            $malformedServerRejected,
            $malformedAuthorizationResult.QueryObserved,
            $malformedClaimMutexOwner.Lease.ClaimMutex.GetType().FullName)

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

    # The live-listener authorization boundary must account for schema work
    # before it can return or enter the kernel proof.  Exercise the real schema
    # after a delayed wrapper; downstream proof functions must remain untouched.
    $userInspection = [pscustomobject]@{
        Success = $true; Known = $true; HasListeners = $true
        Listeners = @($userListener); ListenerOwnerKind = 'user-process'
        ListenerOwnerProof = 'diagnostics-process-start-v1'
        ListenerOwnerEvidence = 'system-diagnostics-process-starttime'
        Source = 'Get-NetTCPConnection'; Error = ''
    }
    $schemaDeadlineProbe = & $module[0] {
        param($inspectionValue, $ownerValue)
        $saved = @{}
        foreach ($name in @(
                'Test-VerifierListenerInspectionSchema',
                'Get-VerifierLoopbackListenerRecords',
                'Get-VerifierBrowserOwnershipSnapshot',
                'Get-VerifierCurrentProcessRecordById',
                'Test-VerifierListenerBelongsToOwner')) {
            $saved[$name] = (Get-Command $name -CommandType Function `
                -ErrorAction Stop).ScriptBlock
        }
        try {
            $script:GateBDeadlineSchemaCalls = 0
            $script:GateBDeadlineDownstreamCalls = 0
            $script:GateBDeadlineSchemaOriginal = $saved['Test-VerifierListenerInspectionSchema']
            Set-Item Function:\Test-VerifierListenerInspectionSchema -Force -Value {
                param($candidateInspection, $candidateContext, $candidateOwner)
                $script:GateBDeadlineSchemaCalls++
                Start-Sleep -Milliseconds 600
                return [bool](& $script:GateBDeadlineSchemaOriginal `
                    $candidateInspection $candidateContext $candidateOwner)
            }
            foreach ($name in @(
                    'Get-VerifierLoopbackListenerRecords',
                    'Get-VerifierBrowserOwnershipSnapshot',
                    'Get-VerifierCurrentProcessRecordById',
                    'Test-VerifierListenerBelongsToOwner')) {
                Set-Item Function:\$name -Force -Value {
                    $script:GateBDeadlineDownstreamCalls++
                    Throw-VerifierInfrastructure 'schema deadline canary reached a downstream proof dependency'
                }
            }
            $accepted = $false
            $typedFailure = $false
            try {
                $accepted = [bool](Test-VerifierLiveListenerInspectionAuthorization `
                    $inspectionValue $null $ownerValue 0 0L)
            } catch {
                $typedFailure = Test-VerifierInfrastructureError $_
            }
            return [pscustomobject]@{
                Accepted = $accepted; TypedFailure = $typedFailure
                SchemaCalls = [int]$script:GateBDeadlineSchemaCalls
                DownstreamCalls = [int]$script:GateBDeadlineDownstreamCalls
            }
        } finally {
            foreach ($name in $saved.Keys) {
                Set-Item Function:\$name -Force -Value $saved[$name]
            }
            Remove-Variable -Name GateBDeadlineSchemaCalls,`
                GateBDeadlineDownstreamCalls,GateBDeadlineSchemaOriginal `
                -Scope Script -Force -ErrorAction SilentlyContinue
        }
    } $userInspection $owner
    Assert-GateB (-not [bool]$schemaDeadlineProbe.Accepted -and
        [int]$schemaDeadlineProbe.SchemaCalls -eq 1 -and
        [int]$schemaDeadlineProbe.DownstreamCalls -eq 0) `
        'delayed live-listener schema was accepted or reached a downstream proof dependency'

    # Ordinary kernel authorization is a single semantic proof after the OS
    # listener query. It is not the missing-descendant refresh lane, so a slow
    # (but otherwise valid) handshake must not be rejected by that 500ms cap.
    $kernelInspection = [pscustomobject]@{
        Success = $true; Known = $true; HasListeners = $true
        Listeners = @($kernelListener); ListenerOwnerKind = 'kernel-transport'
        ListenerOwnerProof = 'run-owned-preview-http-sys-v1'
        ListenerOwnerEvidence = 'pid-4-system-http-sys'
        Source = 'Get-NetTCPConnection'; Error = ''
    }
    $kernelAuthorizationDeadlineProbe = & $module[0] {
        param($inspectionValue, $contextValue, $ownerValue, $proofValue)
        $oldAuthorization = (Get-Command Test-VerifierRunOwnedPreviewHttpSysAuthorization `
            -CommandType Function -ErrorAction Stop).ScriptBlock
        try {
            $script:GateBDeadlineAuthorizationCalls = 0
            $script:GateBDeadlineAuthorizationOriginal = $oldAuthorization
            Set-Item Function:\Test-VerifierRunOwnedPreviewHttpSysAuthorization -Force -Value {
                $script:GateBDeadlineAuthorizationCalls++
                if ($script:GateBDeadlineAuthorizationCalls -eq 1) {
                    Start-Sleep -Milliseconds 600
                }
                return [bool](& $script:GateBDeadlineAuthorizationOriginal `
                    $args[0] $args[1] $args[2])
            }
            $accepted = $false
            $typedFailure = $false
            try {
                $accepted = [bool](Test-VerifierLiveListenerInspectionAuthorization `
                    $inspectionValue $contextValue $ownerValue 0 0L $proofValue)
            } catch {
                $typedFailure = Test-VerifierInfrastructureError $_
            }
            return [pscustomobject]@{
                Accepted = $accepted; TypedFailure = $typedFailure
                AuthorizationCalls = [int]$script:GateBDeadlineAuthorizationCalls
            }
        } finally {
            Set-Item Function:\Test-VerifierRunOwnedPreviewHttpSysAuthorization `
                -Force -Value $oldAuthorization
            Remove-Variable -Name GateBDeadlineAuthorizationCalls,`
                GateBDeadlineAuthorizationOriginal -Scope Script -Force `
                -ErrorAction SilentlyContinue
        }
    } $kernelInspection $context $owner $proofForMutation
    Assert-GateB ([bool]$kernelAuthorizationDeadlineProbe.Accepted -and
        [int]$kernelAuthorizationDeadlineProbe.AuthorizationCalls -eq 0) `
        'carried kernel proof was rejected or downstream listener authorization reauthorized'

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
    $manifestView = ConvertFrom-VerifierDurableJson $manifestViewJson
    $manifestServerView = $manifestView.server
    $manifestCreatedUtc = & $module[0] {
        param($value)
        ConvertTo-VerifierStrictTimestampText $value $false 'Gate B manifest createdUtc' -AllowJsonDateTime
    } $manifestView.createdUtc
    $manifestCleanupCompletedUtc = & $module[0] {
        param($value)
        ConvertTo-VerifierStrictTimestampText $value $true 'Gate B manifest cleanup completedUtc' -AllowJsonDateTime
    } $manifestView.cleanup.completedUtc
    Assert-GateB ($manifestCreatedUtc -ceq $manifestContext.CreatedUtc -and
        $manifestView.cleanup.state -ceq 'pending' -and
        $manifestCleanupCompletedUtc -ceq '') `
        'valid manifest JSON round-trip did not preserve strict context timestamp/state fields'
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
                [pscustomobject]@{ Name = 'empty CreatedUtc'; Target = 'context'; Field = 'CreatedUtc'; Value = ''; Remove = $false }
                [pscustomobject]@{ Name = 'malformed CreatedUtc'; Target = 'context'; Field = 'CreatedUtc'; Value = 'not-a-timestamp'; Remove = $false }
                [pscustomobject]@{ Name = 'empty CleanupState'; Target = 'context'; Field = 'CleanupState'; Value = ''; Remove = $false }
                [pscustomobject]@{ Name = 'unknown CleanupState'; Target = 'context'; Field = 'CleanupState'; Value = 'legacy'; Remove = $false }
                [pscustomobject]@{ Name = 'malformed CleanupCompletedUtc'; Target = 'context'; Field = 'CleanupCompletedUtc'; Value = 'not-a-timestamp'; Remove = $false }
                [pscustomobject]@{ Name = 'pending CleanupCompletedUtc'; Target = 'context'; Field = 'CleanupCompletedUtc'; Value = '2026-08-30T00:00:00.0000000Z'; Remove = $false }
            )) {
            $candidateContext = $manifestNoWriteContext | Select-Object *
            if ($noWriteCase.Target -eq 'context') {
                if ($noWriteCase.Remove) {
                    [void]$candidateContext.PSObject.Properties.Remove($noWriteCase.Field)
                } else {
                    $candidateContext.($noWriteCase.Field) = $noWriteCase.Value
                }
            } elseif ($noWriteCase.Target -eq 'server') {
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

function Invoke-GateBIntegratedListenerConsumerCanary() {
    # The direct listener canary above exercises the live mutation consumers.
    # Run the browser wrapper's deterministic proof canary as a real child so
    # serialized server/manifest and integrated ledger readers are tested at
    # their process boundary too; shell-collapsed nonzero status is not enough.
    $powershell = (Get-Command powershell.exe -ErrorAction Stop).Source
    $browserVerifier = Join-Path $PSScriptRoot 'verify-browser.ps1'
    $childResult = Invoke-GateBBoundedProcess $powershell @(
        '-NoLogo', '-NoProfile', '-NonInteractive', '-ExecutionPolicy', 'Bypass',
        '-File', $browserVerifier, '-GateBListenerProofProbe') 120000 `
        'integrated listener consumer canary'
    $childExit = Resolve-GateBChildExitCode $childResult `
        'integrated listener consumer canary'
    if ($childExit -ne 0) {
        $message = 'integrated listener consumer canary returned exit ' +
            [string]$childExit + ': ' + [string]$childResult.Stdout + ' ' +
            [string]$childResult.Stderr
        if ($childExit -eq 2) { Throw-GateBInfrastructure $message }
        throw $message
    }
    Assert-GateB ([string]$childResult.Stdout -match
        'PASS:listener owner proof') `
        'integrated listener consumer canary did not publish its proof-bearing PASS output'
    Write-Host 'PASS:integrated listener consumer canary exercised serialized server/manifest and ledger readers in a bounded child'
}

function Invoke-GateBDescendantCleanupCanary() {
    $canaryRoot = Join-Path ([IO.Path]::GetTempPath()) `
        ('TroubleshootJS\gate-b-descendant-cleanup-' + [Guid]::NewGuid().ToString('N'))
    $context = $null
    $browserSessionRecord = $null
    $containmentJob = $null
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
            [void](Get-VerifierProcessSnapshotWithFallback `
                'end-to-end descendant cleanup canary')
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
        # Use the real atomic containment boundary for this process-tree test:
        # the same-executable markerless helper must remain in the job until
        # fixed-point cleanup has independently discovered it.
        $verifierModule = @(Get-Module VerifierIsolation | Select-Object -First 1)
        if ($verifierModule.Count -ne 1) {
            Throw-GateBInfrastructure 'end-to-end descendant canary could not enter the verifier containment launch boundary'
        }
        $containedLaunch = & $verifierModule[0] {
            param($fixtureContext, $fixtureSession, $fixtureBrowserPath,
                $fixtureArguments, $fixtureWorkingDirectory)
            $job = New-VerifierBrowserContainmentJob $fixtureContext $fixtureSession
            $fixtureSession.Runtime.ContainmentJob = $job
            Prepare-VerifierBrowserContainmentLaunch $fixtureContext $fixtureSession $job
            $processId = Start-VerifierBrowserProcessInContainmentJob `
                -ContainmentJob $job -FilePath $fixtureBrowserPath `
                -Arguments $fixtureArguments -WorkingDirectory $fixtureWorkingDirectory
            Set-VerifierBrowserContainmentLaunch $fixtureContext $fixtureSession $job $processId
            Write-VerifierManifest $fixtureContext
            return [pscustomobject]@{ Job = $job; ProcessId = [int]$processId }
        } $context $browserSessionRecord $wscriptPath $rootArguments $context.WorktreeRoot
        if ($null -eq $containedLaunch -or $null -eq $containedLaunch.Job -or
                -not (Test-VerifierStrictIntegralValue $containedLaunch.ProcessId 1 ([int]::MaxValue))) {
            Throw-GateBInfrastructure 'end-to-end descendant containment launch did not return its exact job/PID tuple'
        }
        $containmentJob = $containedLaunch.Job
        $rootProcess = Get-Process -Id ([int]$containedLaunch.ProcessId) -ErrorAction Stop
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
    $containmentJob = $null
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
            [void](Get-VerifierProcessSnapshotWithFallback `
                'late-markerless cleanup canary')
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
        # Launch the delayed-helper root through the exact production
        # containment boundary. Its later markerless child therefore remains in
        # the same no-breakaway job while the fixed-point drain discovers it.
        $verifierModule = @(Get-Module VerifierIsolation | Select-Object -First 1)
        if ($verifierModule.Count -ne 1) {
            Throw-GateBInfrastructure 'late-markerless canary could not enter the verifier containment launch boundary'
        }
        $containedLaunch = & $verifierModule[0] {
            param($fixtureContext, $fixtureSession, $fixtureBrowserPath,
                $fixtureArguments, $fixtureWorkingDirectory)
            $job = New-VerifierBrowserContainmentJob $fixtureContext $fixtureSession
            $fixtureSession.Runtime.ContainmentJob = $job
            Prepare-VerifierBrowserContainmentLaunch $fixtureContext $fixtureSession $job
            $processId = Start-VerifierBrowserProcessInContainmentJob `
                -ContainmentJob $job -FilePath $fixtureBrowserPath `
                -Arguments $fixtureArguments -WorkingDirectory $fixtureWorkingDirectory
            Set-VerifierBrowserContainmentLaunch $fixtureContext $fixtureSession $job $processId
            Write-VerifierManifest $fixtureContext
            return [pscustomobject]@{ Job = $job; ProcessId = [int]$processId }
        } $context $session $browserPath $rootArguments $context.WorktreeRoot
        if ($null -eq $containedLaunch -or $null -eq $containedLaunch.Job -or
                -not (Test-VerifierStrictIntegralValue $containedLaunch.ProcessId 1 ([int]::MaxValue))) {
            Throw-GateBInfrastructure 'late-markerless containment launch did not return its exact job/PID tuple'
        }
        $containmentJob = $containedLaunch.Job
        $rootProcess = Get-Process -Id ([int]$containedLaunch.ProcessId) -ErrorAction Stop
        # Preserve the exact launch handle before any fallible identity query.
        # Do not publish a positive PID until its complete start/parent/command
        # tuple has been proved; cleanup must never serialize a mixed identity.
        $session.Runtime.Browser = $rootProcess
        $rootStartTicks = Get-VerifierProcessStartTicks $rootProcess
        $rootIdentity = Get-VerifierCurrentProcessIdentity ([int]$rootProcess.Id) `
            $rootStartTicks 0 '' '' 0 $runId '' 0 $browserPath
        $session.ProcessId = [int]$rootProcess.Id
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
        Assert-GateB (-not [bool]$session.Lease.ProcessProofRequired -and
            -not [bool]$session.Lease.ProcessTerminationProven -and
            -not [bool]$session.Lease.ProcessAbsent) `
            'late-markerless cleanup copied root process proof into an unbound lease'
        Assert-GateB ([bool]$context.TestHooks.BrowserDrainAfterInitialGraphSignalWritten) `
            'late-markerless cleanup did not capture its initial empty graph before signaling the helper'
        Assert-GateB (Test-Path -LiteralPath $helperReadyPath -PathType Leaf) `
            'late-markerless helper did not start after the initial graph capture'
        $rootProcess.Refresh()
        Assert-GateB ([bool]$rootProcess.HasExited) `
            'late-markerless fixed-point cleanup returned before the root terminated'
        Assert-GateB ($null -eq (Get-VerifierCurrentProcessRecordById ([int]$rootProcess.Id))) `
            'late-markerless fixed-point cleanup left the root in the current process view'
        $lateHelperRecords = @(Get-VerifierProcessSnapshotWithFallback `
            'late-markerless helper discovery' |
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
                $lateHelperRecords = @(Get-VerifierProcessSnapshotWithFallback `
                    'late-markerless helper polling' |
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
                    $lateHelperRecords = @(Get-VerifierProcessSnapshotWithFallback `
                        'late-markerless helper polling' |
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
                $verifierModule = @(Get-Module VerifierIsolation | Select-Object -First 1)
                if ($verifierModule.Count -ne 1) {
                    Throw-GateBInfrastructure 'late-markerless canary could not enter the verifier containment/receipt recovery boundary'
                }
                Assert-VerifierBrowserProfileIsQuiescent $profile $session.BrowserPath `
                    $runId $repositoryIdentity $port
                & $verifierModule[0] {
                    param($fixtureSession)
                    $job = $fixtureSession.Runtime.ContainmentJob
                    if ($null -eq $job -or @($job.GetMemberProcessIds()).Count -ne 0) {
                        Throw-VerifierInfrastructure 'late-markerless canary containment job was not exactly empty after root/helper absence proof.'
                    }
                    Dispose-VerifierBrowserContainmentJob $fixtureSession
                } $session
                $containmentJob = $null
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
                & $verifierModule[0] {
                    param($fixtureSession)
                    Close-VerifierBrowserRecoveryReceipt $fixtureSession
                } $session
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
    $helperStopPath = ''
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
            [void](Get-VerifierProcessSnapshotWithFallback 'root-gone cleanup canary')
        } catch {
            Throw-GateBInfrastructure ('root-gone cleanup canary requires complete Win32_Process ' +
                'inspection; this host returned: ' + (Get-VerifierErrorMessage $_))
        }
        $browserPath = (Get-Command wscript.exe -ErrorAction Stop).Source
        # cscript.exe is unavailable on this host because Windows Script Host
        # cannot load its settings under the sandbox token. Use the supported
        # absolute PowerShell executable for the markerless differently named
        # helper instead; its command line still carries only positional
        # fixture arguments and remains subject to the same exact identity
        # proof.
        $helperExecutable = (Get-Command powershell.exe -ErrorAction Stop).Source
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

        $helperScript = Join-Path $canaryRoot 'different-name-markerless-helper.ps1'
        $rootScript = [IO.Path]::ChangeExtension(
            (Join-Path $canaryRoot 'root-gone-launcher.ps1'), '.vbs')
        $rootStopPath = Join-Path $canaryRoot 'root-gone-stop.signal'
        $helperStopPath = Join-Path $canaryRoot 'helper-stop.signal'
        $helperText = [string]::Join([Environment]::NewLine, @(
            'param([string]$ProfilePath, [string]$StopPath)'
            '# The helper has no verifier switches. The profile is passed as a'
            '# positional fixture argument solely to model a markerless'
            '# differently named process that may still retain a profile.'
            '$deadline = [DateTime]::UtcNow.AddMinutes(10)'
            'while (-not [IO.File]::Exists($StopPath) -and [DateTime]::UtcNow -lt $deadline) {'
            '    Start-Sleep -Milliseconds 100'
            '}'
        ))
        $rootText = [string]::Join([Environment]::NewLine, @(
            'Option Explicit'
            'Dim shell, commandLine, fileSystem, i, helperExecution'
            'Function Q(value)'
            '    Q = Chr(34) & Replace(CStr(value), Chr(34), Chr(34) & Chr(34)) & Chr(34)'
            'End Function'
            'Set shell = CreateObject("WScript.Shell")'
            # Use the already-resolved absolute helper host. Resolving a
            # helper inside WScript would re-enter the inherited case-
            # colliding Path/PATH environment on affected hosts.
            'commandLine = Q(WScript.Arguments(4)) & " -NoProfile -ExecutionPolicy Bypass -File " & Q(WScript.Arguments(0)) & " " & Q(WScript.Arguments(1)) & " " & Q(WScript.Arguments(3))'
            'Set helperExecution = shell.Exec(commandLine)'
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
                -not (Test-VerifierPhysicalChildPath $canaryRoot $rootScript) -or
                -not (Test-VerifierPhysicalChildPath $canaryRoot $rootStopPath) -or
                -not (Test-VerifierPhysicalChildPath $canaryRoot $helperStopPath)) {
            Throw-GateBInfrastructure 'root-gone canary fixture scripts escaped the physical namespace'
        }

        $rootArguments = @(
            '//B', $rootScript, $helperScript, $profile, $rootStopPath, $helperStopPath,
            $helperExecutable,
            '--user-data-dir', $profile,
            '--tsj-verifier-run', $runId,
            '--tsj-verifier-worktree', $repositoryIdentity,
            '--remote-debugging-port', [string]$port)
        # Exercise the same atomic no-breakaway launch boundary as production.
        # The markerless helper inherits this job from its WScript root, while
        # the canary retains the original handle until that helper's natural
        # exit has been proved during the deliberate root-gone recovery.
        $verifierModule = @(Get-Module VerifierIsolation | Select-Object -First 1)
        if ($verifierModule.Count -ne 1) {
            Throw-GateBInfrastructure 'root-gone canary could not enter the verifier containment launch boundary'
        }
        $containedLaunch = & $verifierModule[0] {
            param($fixtureContext, $fixtureSession, $fixtureBrowserPath,
                $fixtureArguments, $fixtureWorkingDirectory)
            $job = New-VerifierBrowserContainmentJob $fixtureContext $fixtureSession
            $fixtureSession.Runtime.ContainmentJob = $job
            Prepare-VerifierBrowserContainmentLaunch $fixtureContext $fixtureSession $job
            $processId = Start-VerifierBrowserProcessInContainmentJob `
                -ContainmentJob $job -FilePath $fixtureBrowserPath `
                -Arguments $fixtureArguments -WorkingDirectory $fixtureWorkingDirectory
            Set-VerifierBrowserContainmentLaunch $fixtureContext $fixtureSession $job $processId
            Write-VerifierManifest $fixtureContext
            return [pscustomobject]@{ Job = $job; ProcessId = [int]$processId }
        } $context $session $browserPath $rootArguments $context.WorktreeRoot
        if ($null -eq $containedLaunch -or $null -eq $containedLaunch.Job -or
                -not (Test-VerifierStrictIntegralValue $containedLaunch.ProcessId 1 ([int]::MaxValue))) {
            Throw-GateBInfrastructure 'root-gone canary containment launch did not return its exact job/PID tuple'
        }
        $containmentJob = $containedLaunch.Job
        $rootProcessId = [int]$containedLaunch.ProcessId
        # Retain the launch handle before any fallible identity query.  Do not
        # publish a positive PID into the durable session until its complete
        # start/parent/command tuple has been proved; the durable schema must
        # never contain an intentionally mixed identity.
        $rootProcess = Get-Process -Id $rootProcessId -ErrorAction Stop
        $session.Runtime.Browser = $rootProcess
        $rootStartTicks = Get-VerifierProcessStartTicks $rootProcess
        $rootIdentity = Get-VerifierCurrentProcessIdentity ([int]$rootProcess.Id) `
            $rootStartTicks 0 '' '' 0 $runId '' 0 $browserPath
        $session.ProcessId = [int]$rootProcess.Id
        $session.ProcessStartTicks = $rootStartTicks
        $session.ProcessParentProcessId = [int]$rootIdentity.Record.ParentProcessId
        $session.ProcessParentProcessStartTicks = [long]$rootIdentity.Record.ParentProcessStartTicks
        $session.ProcessCommandLine = [string]$rootIdentity.Record.CommandLine
        $session.Status = 'started'
        Write-VerifierManifest $context

        $helperCandidate = $null
        $helperDeadline = [DateTime]::UtcNow.AddSeconds(20)
        do {
            $rootProcess.Refresh()
            if ([bool]$rootProcess.HasExited) {
                Throw-GateBInfrastructure 'root-gone canary root exited before its helper identity was captured'
            }
            $helperCandidates = @(Get-VerifierProcessSnapshotWithFallback `
                'root-gone helper discovery' |
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
        if (-not [String]::IsNullOrWhiteSpace($helperStopPath)) {
            try {
                if (Test-VerifierPhysicalChildPath $canaryRoot $helperStopPath) {
                    [IO.File]::WriteAllText($helperStopPath, 'stop-root-gone-helper',
                        [Text.UTF8Encoding]::new($false))
                }
            } catch {
                [void]$cleanupErrors.Add(('root-gone helper stop signal: ' +
                    (Get-VerifierErrorMessage $_)))
            }
        }
        if (-not [String]::IsNullOrWhiteSpace($rootStopPath)) {
            try {
                if (Test-VerifierPhysicalChildPath $canaryRoot $rootStopPath) {
                    [IO.File]::WriteAllText($rootStopPath, 'stop-root-gone-root',
                        [Text.UTF8Encoding]::new($false))
                }
            } catch {
                [void]$cleanupErrors.Add(('root-gone root stop signal: ' +
                    (Get-VerifierErrorMessage $_)))
            }
        }
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
                $verifierModule = @(Get-Module VerifierIsolation | Select-Object -First 1)
                if ($verifierModule.Count -ne 1) {
                    Throw-GateBInfrastructure 'root-gone canary could not enter the verifier containment/receipt recovery boundary'
                }
                Assert-VerifierBrowserProfileIsQuiescent $profile $session.BrowserPath `
                    $runId $repositoryIdentity $port
                & $verifierModule[0] {
                    param($fixtureSession)
                    $job = $fixtureSession.Runtime.ContainmentJob
                    if ($null -eq $job -or @($job.GetMemberProcessIds()).Count -ne 0) {
                        Throw-VerifierInfrastructure 'root-gone canary containment job was not exactly empty after the helper natural-exit proof.'
                    }
                    Dispose-VerifierBrowserContainmentJob $fixtureSession
                } $session
                $containmentJob = $null
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
                # This fixture allocated a receipt but never used the production
                # browser bind path. Close it as an unbound revocation only
                # after the helper, profile, and lease are all proven absent.
                & $verifierModule[0] {
                    param($fixtureSession)
                    Close-VerifierBrowserRecoveryReceipt $fixtureSession
                } $session
                Assert-GateB ($session.RecoveryReceipt.State -ceq 'closed' -and
                    -not [bool]$session.RecoveryReceipt.CloseAttempted -and
                    [String]::IsNullOrWhiteSpace([string]$session.RecoveryReceipt.BoundUtc)) `
                    'root-gone canary did not retain an exact closed-unbound receipt after fixture recovery'
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
            [void](Get-VerifierProcessSnapshotWithFallback 'real Edge ownership canary')
        } catch {
            Throw-GateBInfrastructure ('real Edge ownership canary requires complete Win32_Process ' +
                'inspection; this host returned: ' + (Get-VerifierErrorMessage $_))
        }
        $edgePath = Resolve-VerifierBrowserPath ''
        if (-not (Test-Path -LiteralPath $edgePath -PathType Leaf)) {
            Throw-GateBInfrastructure 'the shared Edge resolver returned a non-existent executable.'
        }
        $context = New-VerifierRunContext $repositoryRoot $canaryRoot
        # Exercise the production session path rather than reconstructing a
        # transient root tuple in this canary.  The durable session cannot be
        # written with ProcessId alone, and the production path records the
        # full PID/start/parent/command receipt before it is persisted.
        $opened = New-VerifierBrowserSession $context 'real-edge-ownership' `
            'about:blank' $edgePath 30
        $session = $opened.Record
        $edgeProcess = $opened.Browser
        $profile = [string]$session.Profile
        $runId = [string]$context.RunId
        $port = [int]$session.CdpPort

        Assert-GateB ($session.Status -ceq 'attached' -and
            $session.Lease.Status -ceq 'bound' -and
            $session.RecoveryReceipt.State -ceq 'bound' -and
            [int]$session.RecoveryReceipt.RootProcessId -eq [int]$session.ProcessId -and
            [long]$session.RecoveryReceipt.RootProcessStartTicks -eq
                [long]$session.ProcessStartTicks -and
            [int]$session.RecoveryReceipt.ListenerProcessId -eq [int]$session.ProcessId -and
            [long]$session.RecoveryReceipt.ListenerProcessStartTicks -eq
                [long]$session.ProcessStartTicks) `
            'real Edge session did not bind an exact root/listener recovery receipt'
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
        $primaryDetail = if ($null -ne $primaryFailure) {
            'primary=' + (Get-VerifierErrorMessage $primaryFailure) + '; '
        } else { '' }
        Throw-GateBInfrastructure ('real Edge ownership canary cleanup was not proven; evidence was retained at ' +
            $canaryRoot + ': ' + $primaryDetail + ($cleanupErrors -join '; '))
    }
    if ($null -ne $primaryFailure) {
        if (Test-VerifierInfrastructureError $primaryFailure) { throw $primaryFailure }
        Throw-GateBInfrastructure ('real Edge ownership canary failed: ' +
            (Get-VerifierErrorMessage $primaryFailure))
    }
    Write-Host 'PASS:real Edge exact receipt/root/listener, profile, claim, and evidence cleanup canary'
}

function Invoke-GateBBrowserIdentityRetryCanary() {
    $module = @(Get-Module VerifierIsolation | Select-Object -First 1)
    if ($module.Count -ne 1) {
        Throw-GateBInfrastructure 'VerifierIsolation module was unavailable for browser identity retry canary.'
    }
    $process = Get-Process -Id ([int]$PID) -ErrorAction Stop
    if ($null -eq $process) {
        Throw-GateBInfrastructure 'Browser identity retry canary could not retain its current process.'
    }
    $startTicks = Get-VerifierProcessStartTicks $process
    $browserPath = (Get-Command powershell.exe -ErrorAction Stop).Source
    $current = Get-VerifierCurrentProcessRecordById ([int]$PID)
    if ($null -eq $current -or [String]::IsNullOrWhiteSpace([string]$current.CommandLine)) {
        Throw-GateBInfrastructure 'Browser identity retry canary could not obtain a complete current command line.'
    }
    $validRecord = [pscustomobject]@{
        ProcessId = [int]$PID
        ProcessStartTicks = [long]$startTicks
        ParentProcessId = [int]$current.ParentProcessId
        ParentProcessStartTicks = 1L
        CommandLine = [string]$current.CommandLine
        Name = 'powershell.exe'
        ExecutablePath = [string]$browserPath
    }
    $probe = & $module[0] {
        param($pidValue, $startValue, $expectedPath, $processValue, $recordValue)
        $oldFunction = Get-Command Get-VerifierCurrentProcessIdentity `
            -CommandType Function -ErrorAction Stop
        $oldScriptBlock = $oldFunction.ScriptBlock
        try {
            $results = New-Object Collections.ArrayList
            foreach ($case in @(
                    [pscustomobject]@{ Name = 'transient-empty-path'; Mode = 'transient' }
                    [pscustomobject]@{ Name = 'stable-empty-path'; Mode = 'empty' }
                    [pscustomobject]@{ Name = 'stable-wrong-path'; Mode = 'wrong' }
                    [pscustomobject]@{ Name = 'delayed-proof'; Mode = 'delayed' }
                )) {
                $script:GateBRetryAttempts = 0
                $script:GateBRetryMode = $case.Mode
                $script:GateBRetryExpectedPath = $expectedPath
                $script:GateBRetryProcess = $processValue
                $script:GateBRetryRecord = $recordValue
                Set-Item Function:\Get-VerifierCurrentProcessIdentity -Force -Value {
                    $script:GateBRetryAttempts++
                    if ($script:GateBRetryMode -eq 'delayed') {
                        Start-Sleep -Milliseconds 600
                    }
                    $record = $script:GateBRetryRecord | Select-Object *
                    if ($script:GateBRetryMode -eq 'transient' -and
                            $script:GateBRetryAttempts -eq 1) {
                        $record.ExecutablePath = ''
                    } elseif ($script:GateBRetryMode -eq 'empty') {
                        $record.ExecutablePath = ''
                    } elseif ($script:GateBRetryMode -eq 'wrong') {
                        $record.ExecutablePath = 'C:\Windows\System32\not-the-browser.exe'
                    }
                    return [pscustomobject]@{
                        Process = $script:GateBRetryProcess
                        Record = $record
                    }
                }
                $accepted = $false
                $typedFailure = $false
                try {
                    [void](Get-VerifierCurrentProcessIdentityWithRetry $pidValue `
                        $startValue 0 '' '' 0 '' '' 0 $expectedPath)
                    $accepted = $true
                } catch {
                    $typedFailure = Test-VerifierInfrastructureError $_
                }
                [void]$results.Add([pscustomobject]@{
                    Name = $case.Name
                    Attempts = [int]$script:GateBRetryAttempts
                    Accepted = $accepted
                    TypedFailure = $typedFailure
                })
            }
            return @($results)
        } finally {
            Set-Item Function:\Get-VerifierCurrentProcessIdentity -Force `
                -Value $oldScriptBlock
            Remove-Variable -Name GateBRetryAttempts,GateBRetryMode,`
                GateBRetryExpectedPath,GateBRetryProcess,GateBRetryRecord `
                -Scope Script -Force -ErrorAction SilentlyContinue
        }
    } ([int]$PID) ([long]$startTicks) $browserPath $process $validRecord
    $transient = @($probe | Where-Object Name -eq 'transient-empty-path')
    $empty = @($probe | Where-Object Name -eq 'stable-empty-path')
    $wrong = @($probe | Where-Object Name -eq 'stable-wrong-path')
    $delayed = @($probe | Where-Object Name -eq 'delayed-proof')
    Assert-GateB ($transient.Count -eq 1 -and $transient[0].Accepted -and
        [int]$transient[0].Attempts -eq 2) `
        'transient empty executable path was not retried to a complete identity'
    foreach ($negative in @($empty, $wrong)) {
        Assert-GateB ($negative.Count -eq 1 -and -not $negative[0].Accepted -and
            $negative[0].TypedFailure -and [int]$negative[0].Attempts -eq 3) `
            'stable empty/wrong executable path did not remain typed infrastructure rejection'
    }
    Assert-GateB ($delayed.Count -eq 1 -and -not $delayed[0].Accepted -and
        $delayed[0].TypedFailure -and [int]$delayed[0].Attempts -eq 1) `
        'delayed complete identity proof was accepted after the monotonic retry budget'
    Write-Host 'PASS:browser identity retry accepts transient empty path only after complete proof, rejects stable empty/wrong paths, and enforces its monotonic budget'
}

function Invoke-GateBBrowserDescendantIdentityRetryCanary() {
    $module = @(Get-Module VerifierIsolation | Select-Object -First 1)
    if ($module.Count -ne 1) {
        Throw-GateBInfrastructure 'VerifierIsolation module was unavailable for browser descendant identity retry canary.'
    }
    $process = Get-Process -Id ([int]$PID) -ErrorAction Stop
    if ($null -eq $process -or $process.GetType() -ne [Diagnostics.Process]) {
        Throw-GateBInfrastructure 'Browser descendant identity retry canary could not retain its live process.'
    }
    $startTicks = Get-VerifierProcessStartTicks $process
    $current = Get-VerifierCurrentProcessRecordById ([int]$PID)
    if ($null -eq $current -or
            [String]::IsNullOrWhiteSpace([string]$current.Name) -or
            [String]::IsNullOrWhiteSpace([string]$current.ExecutablePath) -or
            [String]::IsNullOrWhiteSpace([string]$current.CommandLine)) {
        Throw-GateBInfrastructure 'Browser descendant identity retry canary could not obtain a complete current process record.'
    }
    $parentStartTicks = & $module[0] {
        param($parentId)
        Get-VerifierCurrentParentStartTicks $parentId
    } ([int]$current.ParentProcessId)
    if ([long]$parentStartTicks -le 0) {
        Throw-GateBInfrastructure 'Browser descendant identity retry canary could not obtain the live parent start identity.'
    }
    $profile = Join-Path ([IO.Path]::GetTempPath()) `
        ('TroubleshootJS\gate-b-descendant-profile-' + [Guid]::NewGuid().ToString('N'))
    $runId = 'gate-b-descendant-run'
    $worktree = 'gate-b-descendant-worktree'
    $port = 45123
    $owner = [pscustomobject]@{
        ProcessId = [int]$current.ParentProcessId; ProcessStartTicks = [long]$parentStartTicks
        ProcessParentProcessId = 8999; ProcessParentProcessStartTicks = 699L
        ProcessCommandLine = 'launcher.exe'
        Profile = $profile
        BrowserPath = [string]$current.ExecutablePath
        RunId = $runId; RepositoryIdentity = $worktree
        CdpPort = $port
    }
    $record = [pscustomobject]@{
        ProcessId = [int]$PID; ProcessStartTicks = [long]$startTicks
        ParentProcessId = [int]$current.ParentProcessId
        ParentProcessStartTicks = [long]$parentStartTicks
        Name = [string]$current.Name; ExecutablePath = ''
        CommandLine = ([IO.Path]::GetFileName([string]$current.ExecutablePath) +
            ' -NoProfile -Command gate-b-descendant-retry-canary')
    }
    $probe = & $module[0] {
        param($ownerValue, $recordValue, $processValue)
        $oldProbe = (Get-Command Get-VerifierCurrentProcessRecordById `
            -CommandType Function -ErrorAction Stop).ScriptBlock
        $oldOnce = (Get-Command Get-VerifierCurrentOwnedProcessOnce `
            -CommandType Function -ErrorAction Stop).ScriptBlock
        $oldParentStart = (Get-Command Get-VerifierCurrentParentStartTicks `
            -CommandType Function -ErrorAction Stop).ScriptBlock
        try {
            $completePath = [string]$ownerValue.BrowserPath
            $completeRecord = $recordValue | Select-Object *
            $completeRecord.ExecutablePath = $completePath
            $wrongRecord = $recordValue | Select-Object *
            $wrongRecord.ExecutablePath = 'C:\Program Files\Other\other.exe'
            $changedParent = $completeRecord | Select-Object *
            $changedParent.ParentProcessId = 9002
            $changedStart = $completeRecord | Select-Object *
            $changedStart.ProcessStartTicks = 702L
            $malformedCurrent = $completeRecord | Select-Object *
            $malformedCurrent.CommandLine = ''
            $cases = @(
                [pscustomobject]@{
                    Name = 'transient-empty-path'
                    Mode = 'transient'; Records = @($recordValue, $completeRecord)
                }
                [pscustomobject]@{
                    Name = 'stable-empty-path'
                    Mode = 'stable-empty'; Records = @($recordValue)
                }
                [pscustomobject]@{
                    Name = 'stable-wrong-path'
                    Mode = 'stable-wrong'; Records = @($wrongRecord)
                }
                [pscustomobject]@{
                    Name = 'changed-parent'
                    Mode = 'changed-parent'; Records = @($changedParent)
                }
                [pscustomobject]@{
                    Name = 'changed-start'
                    Mode = 'changed-start'; Records = @($changedStart)
                }
                [pscustomobject]@{
                    Name = 'disappeared'
                    Mode = 'disappeared'; Records = @()
                }
                [pscustomobject]@{
                    Name = 'malformed-current'
                    Mode = 'malformed-current'; Records = @($malformedCurrent)
                }
                [pscustomobject]@{
                    Name = 'null-process'
                    Mode = 'null-process'; Records = @($completeRecord)
                }
                [pscustomobject]@{
                    Name = 'malformed-process'
                    Mode = 'malformed-process'; Records = @($completeRecord)
                }
                [pscustomobject]@{
                    Name = 'once-typed-failure'
                    Mode = 'once-typed-failure'; Records = @($completeRecord)
                }
                [pscustomobject]@{
                    Name = 'delayed-final-proof'
                    Mode = 'delayed-final-proof'; Records = @($completeRecord)
                }
                [pscustomobject]@{
                    Name = 'delayed-proof'
                    Mode = 'delayed-proof'; Records = @($completeRecord)
                }
            )
            $results = New-Object Collections.ArrayList
            foreach ($case in $cases) {
                $state = [pscustomobject]@{
                    Index = 0; Once = 0; Mode = $case.Mode; Records = $case.Records
                }
                $script:GateBDescendantRetryState = $state
                Set-Item Function:\Get-VerifierCurrentProcessRecordById -Force -Value {
                    $state = $script:GateBDescendantRetryState
                    if ($state.Mode -eq 'delayed-proof') {
                        Start-Sleep -Milliseconds 600
                    }
                    if ($state.Mode -eq 'disappeared') {
                        $state.Index++
                        return $null
                    }
                    $index = [Math]::Min($state.Index, $state.Records.Count - 1)
                    $state.Index++
                    return ($state.Records[$index] | Select-Object *)
                }
                Set-Item Function:\Get-VerifierCurrentOwnedProcessOnce -Force -Value {
                    $state = $script:GateBDescendantRetryState
                    $state.Once++
                    if ($state.Mode -eq 'delayed-final-proof') {
                        Start-Sleep -Milliseconds 600
                    }
                    if ($state.Mode -eq 'once-typed-failure') {
                        Throw-VerifierInfrastructure 'synthetic termination-boundary failure'
                    }
                    $current = $state.Records[[Math]::Min($state.Index - 1,
                        $state.Records.Count - 1)] | Select-Object *
                    $verifiedProcess = if ($state.Mode -eq 'null-process') {
                        $null
                    } elseif ($state.Mode -eq 'malformed-process') {
                        [pscustomobject]@{ Id = [int]$processValue.Id }
                    } else { $processValue }
                    return [pscustomobject]@{
                        Process = $verifiedProcess; Record = $current
                    }
                }
                Set-Item Function:\Get-VerifierCurrentParentStartTicks -Force -Value {
                    param($parentId)
                    if ([int]$parentId -eq [int]$recordValue.ParentProcessId) {
                        return [long]$recordValue.ParentProcessStartTicks
                    }
                    return 999L
                }
                $accepted = $false
                $typedFailure = $false
                try {
                    [void](Get-VerifierCurrentOwnedDescendantWithRetry $ownerValue $recordValue)
                    $accepted = $true
                } catch {
                    $typedFailure = Test-VerifierInfrastructureError $_
                }
                [void]$results.Add([pscustomobject]@{
                    Name = $case.Name
                    Attempts = [int]$state.Index
                    OnceAttempts = [int]$state.Once
                    Accepted = $accepted
                    TypedFailure = $typedFailure
                })
            }
            return @($results)
        } finally {
            Set-Item Function:\Get-VerifierCurrentProcessRecordById -Force -Value $oldProbe
            Set-Item Function:\Get-VerifierCurrentOwnedProcessOnce -Force -Value $oldOnce
            Set-Item Function:\Get-VerifierCurrentParentStartTicks -Force -Value $oldParentStart
            Remove-Variable -Name GateBDescendantRetryState -Scope Script -Force `
                -ErrorAction SilentlyContinue
        }
    } $owner $record $process
    $transient = @($probe | Where-Object Name -eq 'transient-empty-path')
    $empty = @($probe | Where-Object Name -eq 'stable-empty-path')
    $wrong = @($probe | Where-Object Name -eq 'stable-wrong-path')
    $parent = @($probe | Where-Object Name -eq 'changed-parent')
    $start = @($probe | Where-Object Name -eq 'changed-start')
    $disappeared = @($probe | Where-Object Name -eq 'disappeared')
    $malformedCurrent = @($probe | Where-Object Name -eq 'malformed-current')
    $nullProcess = @($probe | Where-Object Name -eq 'null-process')
    $malformedProcess = @($probe | Where-Object Name -eq 'malformed-process')
    $onceFailure = @($probe | Where-Object Name -eq 'once-typed-failure')
    $delayedFinal = @($probe | Where-Object Name -eq 'delayed-final-proof')
    $delayed = @($probe | Where-Object Name -eq 'delayed-proof')
    Assert-GateB ($transient.Count -eq 1 -and $transient[0].Accepted -and
        $transient[0].TypedFailure -eq $false -and
        [int]$transient[0].Attempts -eq 2 -and
        [int]$transient[0].OnceAttempts -eq 1) `
        'transient empty descendant executable path did not recover on a complete retry'
    Assert-GateB ($empty.Count -eq 1 -and -not $empty[0].Accepted -and
        $empty[0].TypedFailure -and [int]$empty[0].Attempts -eq 3 -and
        [int]$empty[0].OnceAttempts -eq 0) `
        'stable empty descendant executable path did not remain bounded typed rejection'
    foreach ($negative in @($wrong, $parent, $start, $disappeared, $malformedCurrent)) {
        Assert-GateB ($negative.Count -eq 1 -and -not $negative[0].Accepted -and
            $negative[0].TypedFailure -and [int]$negative[0].Attempts -eq 1 -and
            [int]$negative[0].OnceAttempts -eq 0) `
            'wrong descendant path, changed identity, disappearance, or malformed current record was retried or accepted'
    }
    foreach ($negative in @($nullProcess, $malformedProcess, $onceFailure)) {
        Assert-GateB ($negative.Count -eq 1 -and -not $negative[0].Accepted -and
            $negative[0].TypedFailure -and [int]$negative[0].Attempts -eq 1 -and
            [int]$negative[0].OnceAttempts -eq 1) `
            'null/malformed Process or a termination-boundary failure was retried or accepted'
    }
    Assert-GateB ($delayedFinal.Count -eq 1 -and $delayedFinal[0].Accepted -and
        -not $delayedFinal[0].TypedFailure -and [int]$delayedFinal[0].Attempts -eq 1 -and
        [int]$delayedFinal[0].OnceAttempts -eq 1) `
        'complete descendant identity observed within its retry bound was rejected because later exact validation was slow'
    Assert-GateB ($delayed.Count -eq 1 -and -not $delayed[0].Accepted -and
        $delayed[0].TypedFailure -and [int]$delayed[0].Attempts -eq 1 -and
        [int]$delayed[0].OnceAttempts -eq 0) `
        'delayed complete descendant proof was accepted after the monotonic retry budget'
    Write-Host 'PASS:browser descendant identity retry bounds path publication, permits later exact proof, and rejects identity, termination, and Process-object failures immediately'
}

function Invoke-GateBBrowserDrainNaturalExitCanary() {
    $module = @(Get-Module VerifierIsolation | Select-Object -First 1)
    if ($module.Count -ne 1) {
        Throw-GateBInfrastructure 'VerifierIsolation module was unavailable for browser drain natural-exit canary.'
    }
    $probe = & $module[0] {
        $cases = New-Object Collections.ArrayList
        $oldAbsence = (Get-Command Get-VerifierNaturalExitCurrentAbsenceObservation `
            -CommandType Function -ErrorAction Stop).ScriptBlock
        $oldWmiById = (Get-Command Get-VerifierProcessRecordsByIdWithFallback `
            -CommandType Function -ErrorAction Stop).ScriptBlock
        $oldAttestation = (Get-Command Get-VerifierBrowserDrainAttestation `
            -CommandType Function -ErrorAction Stop).ScriptBlock
        $oldStart = (Get-Command Get-VerifierRetainedProcessStartTimeValue `
            -CommandType Function -ErrorAction Stop).ScriptBlock
        $script:GateBDrainOriginalAbsence = $oldAbsence
        $script:GateBDrainOriginalWmiById = $oldWmiById
        $script:GateBDrainOriginalAttestation = $oldAttestation
        $script:GateBDrainOriginalStart = $oldStart
        function New-DrainCanaryFixture([int]$Milliseconds = 1200) {
            $process = Start-VerifierProcess (Get-Command powershell.exe `
                -ErrorAction Stop).Source @(
                '-NoLogo', '-NoProfile', '-NonInteractive', '-ExecutionPolicy',
                'Bypass', '-Command', "Start-Sleep -Milliseconds $Milliseconds")
            $record = $null
            $scope = $null
            try {
                $record = Get-VerifierCurrentProcessRecordById ([int]$process.Id)
                if ($null -eq $record) {
                    Throw-VerifierInfrastructure ('browser drain canary child PID ' +
                        [string]$process.Id + ' disappeared before record capture (' +
                        [string]$Milliseconds + 'ms fixture).')
                }
                [void]($record | Add-Member -MemberType NoteProperty `
                    -Name ParentProcessStartTicks -Value `
                    (Get-VerifierCurrentParentStartTicks $record.ParentProcessId) -Force)
                $scope = New-VerifierBrowserDrainScope
                $attestation = New-VerifierBrowserDrainAttestation $scope `
                    $record $record.Process
                [void]($record | Add-Member -MemberType NoteProperty `
                    -Name VerifierDrainAttestation -Value $attestation -Force
                )
                return [pscustomobject]@{
                    Process = $process
                    Record = $record
                    Scope = $scope
                    StartTicks = [long]$record.ProcessStartTicks
                }
            } catch {
                $fixtureFailure = $_
                $fixtureErrors = New-Object Collections.ArrayList
                try {
                    if (-not $process.WaitForExit($Milliseconds + 5000)) {
                        [void]$fixtureErrors.Add('bounded canary child did not exit after fixture creation failed')
                    }
                } catch { [void]$fixtureErrors.Add((Get-VerifierErrorMessage $_)) }
                if ($null -ne $scope) {
                    try {
                        foreach ($failure in @(Dispose-VerifierBrowserDrainScope $scope)) {
                            [void]$fixtureErrors.Add($failure)
                        }
                    } catch { [void]$fixtureErrors.Add((Get-VerifierErrorMessage $_)) }
                }
                try { $process.Dispose() } catch {
                    [void]$fixtureErrors.Add((Get-VerifierErrorMessage $_))
                }
                if ($fixtureErrors.Count -gt 0) {
                    Throw-VerifierInfrastructure ((Get-VerifierErrorMessage $fixtureFailure) +
                        '; fixture cleanup: ' + ($fixtureErrors -join '; '))
                    }
                throw $fixtureFailure
            }
        }
        function Dispose-DrainCanaryFixture($Fixture, [bool]$StopLive = $false) {
            $errors = New-Object Collections.ArrayList
            if ($null -eq $Fixture) { return @() }
            $fixturePid = 0
            $fixtureExited = $false
            if ($null -ne $Fixture.Process) {
                try {
                    $fixturePid = [int]$Fixture.Process.Id
                } catch { [void]$errors.Add((Get-VerifierErrorMessage $_)) }
            }
            if ($StopLive -and $null -ne $Fixture.Process) {
                try {
                    [void](Stop-VerifierVerifiedProcessExactly $Fixture.Process `
                        ([long]$Fixture.StartTicks) 5000 $Fixture.Record)
                } catch { [void]$errors.Add((Get-VerifierErrorMessage $_)) }
            }
            if ($null -ne $Fixture.Process) {
                try { $Fixture.Process.Refresh(); $fixtureExited = [bool]$Fixture.Process.HasExited } catch {
                    [void]$errors.Add((Get-VerifierErrorMessage $_))
                }
            }
            if ($null -ne $Fixture.Scope) {
                try {
                    $dispose = @(Dispose-VerifierBrowserDrainScope $Fixture.Scope)
                    foreach ($error in $dispose) { [void]$errors.Add([string]$error) }
                } catch { [void]$errors.Add((Get-VerifierErrorMessage $_)) }
            }
            if ($null -ne $Fixture.Process) {
                if (-not $fixtureExited) {
                    [void]$errors.Add('browser drain canary fixture PID ' +
                        [string]$fixturePid + ' remained alive during disposal')
                }
                try { $Fixture.Process.Dispose() } catch {
                    [void]$errors.Add((Get-VerifierErrorMessage $_))
                }
            }
            return @($errors)
        }
        function Assert-DrainCanaryTypedFailure($Action, [string]$Name) {
            $typed = $false
            try { & $Action } catch { $typed = Test-VerifierInfrastructureError $_ }
            if (-not $typed) {
                Throw-VerifierInfrastructure "browser drain canary case '$Name' did not reject with typed infrastructure failure."
            }
            return $true
        }
        $positive = $null
        $live = $null
        $positiveResult = $false
        $liveStopped = $false
        $negativeFixtures = New-Object Collections.ArrayList
        $foreignHandle = $null
        $emptyScope = $null
        try {
            # Positive path: this is a real short-lived child and a real
            # retained Process handle. The natural lane must prove two current
            # absence observations and must not stop the already-exited child.
            $positive = New-DrainCanaryFixture 3000
            if (-not $positive.Process.WaitForExit(5000)) {
                Throw-VerifierInfrastructure 'browser drain positive child did not naturally exit in its bound.'
            }
            $natural = Get-VerifierBrowserDrainNaturalExitResult $positive.Scope `
                $positive.Record
            if ($null -eq $natural -or -not [bool]$natural.NaturalExit -or
                    -not [bool]$natural.TerminationProven) {
                Throw-VerifierInfrastructure 'browser drain positive natural-exit result was incomplete.'
            }
            $positiveResult = $true

            # Each injected delay delegates to the actual proof/accessor.
            # Acceptance must cover the entire call, including the last view.
            Set-Item Function:\Get-VerifierBrowserDrainAttestation -Force -Value {
                param($DrainScope, $Record)
                Start-Sleep -Milliseconds 650
                & $script:GateBDrainOriginalAttestation $DrainScope $Record
            }
            [void](Assert-DrainCanaryTypedFailure {
                Get-VerifierBrowserDrainNaturalExitResult $positive.Scope $positive.Record
            } 'delayed-attestation-proof')
            Set-Item Function:\Get-VerifierBrowserDrainAttestation -Force -Value $oldAttestation
            Set-Item Function:\Get-VerifierRetainedProcessStartTimeValue -Force -Value {
                param($Process)
                Start-Sleep -Milliseconds 650
                & $script:GateBDrainOriginalStart $Process
            }
            [void](Assert-DrainCanaryTypedFailure {
                Get-VerifierBrowserDrainNaturalExitResult $positive.Scope $positive.Record
            } 'delayed-retained-handle-proof')
            Set-Item Function:\Get-VerifierRetainedProcessStartTimeValue -Force -Value {
                param($Process)
                (& $script:GateBDrainOriginalStart $Process).AddTicks(1)
            }
            [void](Assert-DrainCanaryTypedFailure {
                Get-VerifierBrowserDrainNaturalExitResult $positive.Scope $positive.Record
            } 'exited-retained-handle-start-mismatch')
            Set-Item Function:\Get-VerifierRetainedProcessStartTimeValue -Force -Value $oldStart
            foreach ($delayAt in @(1, 2)) {
                $script:GateBDrainAbsenceCalls = 0
                $script:GateBDrainDelayAt = $delayAt
                Set-Item Function:\Get-VerifierNaturalExitCurrentAbsenceObservation -Force -Value {
                    param($ProcessId)
                    $script:GateBDrainAbsenceCalls++
                    if ($script:GateBDrainAbsenceCalls -eq $script:GateBDrainDelayAt) {
                        Start-Sleep -Milliseconds 650
                    }
                    & $script:GateBDrainOriginalAbsence $ProcessId
                }
                [void](Assert-DrainCanaryTypedFailure {
                    Get-VerifierBrowserDrainNaturalExitResult $positive.Scope $positive.Record
                } ('delayed-current-view-' + $delayAt))
                if ($script:GateBDrainAbsenceCalls -ne $delayAt) {
                    Throw-VerifierInfrastructure 'browser drain continued to another current view after its deadline.'
                }
            }
            Set-Item Function:\Get-VerifierNaturalExitCurrentAbsenceObservation -Force -Value $oldAbsence

            # The retained native handle already proves the exiting process's
            # PID/start identity. A stale-but-well-formed WMI record must not
            # turn that into a cleanup pass or a process stop; the next
            # independent empty observation is the only accepted absence.
            $script:GateBDrainWmiLagCalls = 0
            Set-Item Function:\Get-VerifierProcessRecordsByIdWithFallback -Force -Value {
                param($ProcessId, $Purpose)
                $script:GateBDrainWmiLagCalls++
                if ($script:GateBDrainWmiLagCalls -eq 1) {
                    return @([pscustomobject]@{ ProcessId = [int]$ProcessId })
                }
                return @()
            }
            $laggedNatural = Get-VerifierBrowserDrainNaturalExitResult $positive.Scope `
                $positive.Record
            if ($null -ne $laggedNatural -or $script:GateBDrainWmiLagCalls -ne 1) {
                Throw-VerifierInfrastructure 'browser drain accepted a retained exited process before the stale WMI record cleared.'
            }
            $settledNatural = Get-VerifierBrowserDrainNaturalExitResult $positive.Scope `
                $positive.Record
            if ($null -eq $settledNatural -or -not [bool]$settledNatural.NaturalExit -or
                    -not [bool]$settledNatural.TerminationProven -or
                    $script:GateBDrainWmiLagCalls -ne 3) {
                Throw-VerifierInfrastructure 'browser drain did not accept a retained exited process only after two fresh empty WMI observations.'
            }
            Set-Item Function:\Get-VerifierProcessRecordsByIdWithFallback -Force -Value {
                param($ProcessId, $Purpose)
                return @([pscustomobject]@{ ProcessId = [int]$ProcessId + 1 })
            }
            [void](Assert-DrainCanaryTypedFailure {
                Get-VerifierBrowserDrainNaturalExitResult $positive.Scope $positive.Record
            } 'malformed-stale-wmi-pid')
            Set-Item Function:\Get-VerifierProcessRecordsByIdWithFallback -Force -Value $oldWmiById

            # Missing/unverified handle and copied/foreign scope/attestation
            # cases must fail before any current-process fallback is accepted.
            $missing = New-DrainCanaryFixture 5000
            [void]$negativeFixtures.Add($missing)
            $emptyScope = New-VerifierBrowserDrainScope
            $unmintedScopeCopy = $emptyScope | Select-Object *
            [void](Assert-DrainCanaryTypedFailure {
                New-VerifierBrowserDrainAttestation $unmintedScopeCopy `
                    $missing.Record $missing.Record.Process
            } 'copied-scope-mint-before-any-original-attestation')
            $emptyDispose = @(Dispose-VerifierBrowserDrainScope $emptyScope)
            if ($emptyDispose.Count -gt 0) {
                Throw-VerifierInfrastructure ('empty scope cleanup failed: ' + ($emptyDispose -join '; '))
            }
            $disposedScope = $emptyScope
            $emptyScope = $null
            [void](Assert-DrainCanaryTypedFailure {
                New-VerifierBrowserDrainAttestation $disposedScope `
                    $missing.Record $missing.Record.Process
            } 'disposed-scope-mint')
            $missingCopy = $missing.Record | Select-Object *
            [void]$missingCopy.PSObject.Properties.Remove('VerifierDrainAttestation')
            [void](Assert-DrainCanaryTypedFailure {
                Get-VerifierBrowserDrainNaturalExitResult $missing.Scope $missingCopy
            } 'missing-unverified-handle')
            $missingAttestation = $missing.Record.VerifierDrainAttestation
            $missingProcess = $missingAttestation.Process
            $missingAttestation.Process = $null
            [void](Assert-DrainCanaryTypedFailure {
                Get-VerifierBrowserDrainNaturalExitResult $missing.Scope $missing.Record
            } 'unverified-retained-handle')
            $missingAttestation.Process = $missingProcess
            $originalIdentity = $missingAttestation.Identity
            $missingAttestation.Identity = $originalIdentity | Select-Object *
            [void](Assert-DrainCanaryTypedFailure {
                Get-VerifierBrowserDrainNaturalExitResult $missing.Scope $missing.Record
            } 'replaced-immutable-identity-tuple')
            $missingAttestation.Identity = $originalIdentity

            # A distinct real Process object for the SAME live PID/start is
            # not the handle registered when this child was fully proved.
            $foreignHandle = [Diagnostics.Process]::GetProcessById([int]$missing.Record.ProcessId)
            [void]$foreignHandle.Handle
            if ([object]::ReferenceEquals($foreignHandle, $missingProcess) -or
                    (Get-VerifierProcessStartTicks $foreignHandle) -ne $missing.StartTicks) {
                Throw-VerifierInfrastructure 'foreign-handle canary did not establish a distinct object for the same PID/start.'
            }
            $missingAttestation.Process = $foreignHandle
            $missing.Record.Process = $foreignHandle
            [void](Assert-DrainCanaryTypedFailure {
                Get-VerifierBrowserDrainNaturalExitResult $missing.Scope $missing.Record
            } 'substituted-same-pid-start-handle-while-live')
            if (-not $missing.Process.WaitForExit(7000)) {
                Throw-VerifierInfrastructure 'foreign-handle child did not naturally exit in its bound.'
            }
            [void](Assert-DrainCanaryTypedFailure {
                Get-VerifierBrowserDrainNaturalExitResult $missing.Scope $missing.Record
            } 'substituted-same-pid-start-handle-after-exit')
            $missingAttestation.Process = $missingProcess
            $missing.Record.Process = $missingProcess
            $foreignHandle.Dispose()
            $foreignHandle = $null

            $startMismatch = New-DrainCanaryFixture 5000
            [void]$negativeFixtures.Add($startMismatch)
            $startMismatch.Record.VerifierDrainAttestation.ProcessStartTicks =
                [long]$startMismatch.Record.ProcessStartTicks + 1L
            [void](Assert-DrainCanaryTypedFailure {
                Get-VerifierBrowserDrainNaturalExitResult $startMismatch.Scope `
                    $startMismatch.Record
            } 'start-mismatch')

            $foreign = New-DrainCanaryFixture 3000
            [void]$negativeFixtures.Add($foreign)
            if (-not $foreign.Process.WaitForExit(5000)) {
                Throw-VerifierInfrastructure 'browser drain foreign-scope child did not exit in its bound.'
            }
            $foreignScopeCopy = $foreign.Scope | Select-Object *
            [void](Assert-DrainCanaryTypedFailure {
                Get-VerifierBrowserDrainNaturalExitResult $foreignScopeCopy `
                    $foreign.Record
            } 'copied-foreign-scope')
            $foreignRecordCopy = $foreign.Record | Select-Object *
            [void](Assert-DrainCanaryTypedFailure {
                Get-VerifierBrowserDrainNaturalExitResult $foreign.Scope `
                    $foreignRecordCopy
            } 'copied-foreign-attestation')

            # PID reuse/current presence and an unreadable/disagreeing current
            # view are both rejected after the retained handle reports exit.
            $presence = New-DrainCanaryFixture 3000
            [void]$negativeFixtures.Add($presence)
            if (-not $presence.Process.WaitForExit(5000)) {
                Throw-VerifierInfrastructure 'browser drain PID-reuse child did not exit in its bound.'
            }
            Set-Item Function:\Get-VerifierNaturalExitCurrentAbsenceObservation -Force -Value {
                param($ProcessId)
                return [pscustomobject]@{
                    Absent = $false; Transient = $false
                    Current = [pscustomobject]@{ ProcessId = [int]$ProcessId }
                }
            }
            [void](Assert-DrainCanaryTypedFailure {
                Get-VerifierBrowserDrainNaturalExitResult $presence.Scope `
                    $presence.Record
            } 'pid-reuse-current-presence')
            Set-Item Function:\Get-VerifierNaturalExitCurrentAbsenceObservation -Force -Value {
                param($ProcessId)
                Throw-VerifierInfrastructure 'injected current absence-view failure'
            }
            [void](Assert-DrainCanaryTypedFailure {
                Get-VerifierBrowserDrainNaturalExitResult $presence.Scope `
                    $presence.Record
            } 'current-view-failure')
            Set-Item Function:\Get-VerifierNaturalExitCurrentAbsenceObservation `
                -Force -Value $oldAbsence

            # A live retained handle takes the ordinary current-identity/stop
            # lane. The natural helper must return null and leave it alive.
            $live = New-DrainCanaryFixture 5000
            $liveResult = Get-VerifierBrowserDrainNaturalExitResult $live.Scope `
                $live.Record
            if ($null -ne $liveResult) {
                Throw-VerifierInfrastructure 'browser drain live child entered the natural-exit lane.'
            }
            $live.Process.Refresh()
            if ([bool]$live.Process.HasExited) {
                Throw-VerifierInfrastructure 'browser drain live child exited before its ordinary stop path.'
            }
            $wrongPreStopRecord = $live.Record | Select-Object *
            $wrongPreStopRecord.ParentProcessId = [int]$live.Record.ParentProcessId + 1
            [void](Assert-DrainCanaryTypedFailure {
                Stop-VerifierVerifiedProcessExactly $live.Process `
                    ([long]$live.StartTicks) 5000 $wrongPreStopRecord
            } 'live-prestop-parent-mismatch')
            $live.Process.Refresh()
            if ($live.Process.HasExited) {
                Throw-VerifierInfrastructure 'live ownership-mismatch canary terminated its child.'
            }
            $liveStop = Stop-VerifierVerifiedProcessExactly $live.Process `
                ([long]$live.StartTicks) 5000 $live.Record
            if (-not [bool]$liveStop.TerminationProven -or
                    [bool]$liveStop.NaturalExit) {
                Throw-VerifierInfrastructure 'browser drain live child did not use the ordinary exact stop path.'
            }
            $liveStopped = $true

            # Changing an ownership scalar after attestation is not a natural
            # exit and must remain typed infrastructure failure.
            $ownership = New-DrainCanaryFixture 3000
            [void]$negativeFixtures.Add($ownership)
            if (-not $ownership.Process.WaitForExit(5000)) {
                Throw-VerifierInfrastructure 'browser drain ownership child did not exit in its bound.'
            }
            $ownership.Record.ParentProcessId = [int]$ownership.Record.ParentProcessId + 1
            [void](Assert-DrainCanaryTypedFailure {
                Get-VerifierBrowserDrainNaturalExitResult $ownership.Scope `
                    $ownership.Record
            } 'ownership-mismatch')
            $positive.Record.Process.Dispose()
            [void](Assert-DrainCanaryTypedFailure {
                Get-VerifierBrowserDrainNaturalExitResult $positive.Scope $positive.Record
            } 'disposed-original-handle')

            return [pscustomobject]@{
                Positive = [bool]$positiveResult
                MissingHandle = $true
                StartMismatch = $true
                CopiedForeignScope = $true
                PidReusePresence = $true
                CurrentViewFailure = $true
                WmiLagRetry = $true
                MalformedWmiLag = $true
                LiveOrdinaryStop = [bool]$liveStopped
                OwnershipMismatch = $true
                ExactScopeAndHandle = $true
                WholeCallDeadline = $true
                ExitedStartIdentity = $true
                DisposedHandle = $true
            }
        } finally {
            Set-Item Function:\Get-VerifierNaturalExitCurrentAbsenceObservation `
                -Force -Value $oldAbsence
            Set-Item Function:\Get-VerifierProcessRecordsByIdWithFallback `
                -Force -Value $oldWmiById
            Set-Item Function:\Get-VerifierBrowserDrainAttestation -Force -Value $oldAttestation
            Set-Item Function:\Get-VerifierRetainedProcessStartTimeValue -Force -Value $oldStart
            $cleanupErrors = New-Object Collections.ArrayList
            if ($null -ne $foreignHandle) {
                try { $foreignHandle.Dispose() } catch { [void]$cleanupErrors.Add((Get-VerifierErrorMessage $_)) }
            }
            if ($null -ne $emptyScope) {
                try {
                    foreach ($failure in @(Dispose-VerifierBrowserDrainScope $emptyScope)) {
                        [void]$cleanupErrors.Add($failure)
                    }
                } catch { [void]$cleanupErrors.Add((Get-VerifierErrorMessage $_)) }
            }
            foreach ($fixture in @($negativeFixtures) + @($live, $positive)) {
                try {
                    foreach ($failure in @(Dispose-DrainCanaryFixture $fixture $true)) {
                        [void]$cleanupErrors.Add($failure)
                    }
                } catch { [void]$cleanupErrors.Add((Get-VerifierErrorMessage $_)) }
            }
            foreach ($variable in @('GateBDrainOriginalAbsence', 'GateBDrainOriginalWmiById',
                    'GateBDrainOriginalAttestation', 'GateBDrainOriginalStart',
                    'GateBDrainAbsenceCalls', 'GateBDrainWmiLagCalls', 'GateBDrainDelayAt')) {
                Remove-Variable -Scope Script -Name $variable -ErrorAction SilentlyContinue
            }
            if ($cleanupErrors.Count -gt 0) {
                Throw-VerifierInfrastructure ('browser drain canary fixture cleanup failed: ' +
                    ($cleanupErrors -join '; '))
                }
        }
    }
    Assert-GateB ([bool]$probe.Positive -and [bool]$probe.MissingHandle -and
        [bool]$probe.StartMismatch -and [bool]$probe.CopiedForeignScope -and
        [bool]$probe.PidReusePresence -and [bool]$probe.CurrentViewFailure -and
        [bool]$probe.WmiLagRetry -and [bool]$probe.MalformedWmiLag -and
        [bool]$probe.LiveOrdinaryStop -and [bool]$probe.OwnershipMismatch -and
        [bool]$probe.ExactScopeAndHandle -and [bool]$probe.WholeCallDeadline -and
        [bool]$probe.ExitedStartIdentity -and [bool]$probe.DisposedHandle) `
        'browser drain retained-handle natural-exit canary did not validate every positive/negative lane'
    Write-Host 'PASS:browser drain retained-handle natural-exit proof, stale-WMI retry, exact absence, live stop, and typed negative lanes'
}

function Invoke-GateBBrowserPrecloseAttestedDisappearanceCanary() {
    $module = @(Get-Module VerifierIsolation | Select-Object -First 1)
    if ($module.Count -ne 1) {
        Throw-GateBInfrastructure 'VerifierIsolation module was unavailable for the paired browser descendant disappearance canary.'
    }
    $probe = & $module[0] {
        $functionNames = @(
            'Get-VerifierCurrentOwnedProcess',
            'Get-VerifierCurrentProcessRecordById',
            'Get-VerifierCurrentParentStartTicks',
            'Get-VerifierProcessById',
            'Get-VerifierProcessRecordsByIdWithFallback',
            'Get-VerifierProcessRecordsByParentWithFallback',
            'Get-VerifierPreviouslyAttestedBrowserDescendantExit',
            'Stop-VerifierVerifiedProcessExactly')
        $originals = @{}
        foreach ($name in $functionNames) {
            $command = Get-Command $name -CommandType Function -ErrorAction Stop
            $originals[$name] = $command.ScriptBlock
        }
        $fixtures = New-Object Collections.ArrayList
        $cleanupErrors = New-Object Collections.ArrayList
        $shell = (Get-Command powershell.exe -ErrorAction Stop).Source
        $browserPath = [string]$shell
        $rootProcess = Get-Process -Id ([int]$PID) -ErrorAction Stop
        if ($null -eq $rootProcess -or $rootProcess.GetType() -ne [Diagnostics.Process]) {
            Throw-VerifierInfrastructure 'paired browser descendant canary could not retain its real root Process object.'
        }
        $rootCurrent = & $originals['Get-VerifierCurrentProcessRecordById'] ([int]$PID)
        if ($null -eq $rootCurrent -or
                [String]::IsNullOrWhiteSpace([string]$rootCurrent.CommandLine)) {
            Throw-VerifierInfrastructure 'paired browser descendant canary could not capture the real root command line.'
        }
        $rootStart = Get-VerifierProcessStartTicks $rootProcess
        # The live child/root relation is real; the launcher above this
        # verifier process may be a short-lived harness wrapper. Keep that
        # outer parent as a positive synthetic identity so this canary does
        # not depend on the harness retaining its own launcher process.
        $rootParentStart = 1L
        if ([long]$rootStart -le 0 -or [long]$rootParentStart -le 0) {
            Throw-VerifierInfrastructure 'paired browser descendant canary could not capture the real root start identities.'
        }
        $owner = [pscustomobject]@{
            ProcessId = [int]$PID
            ProcessStartTicks = [long]$rootStart
            ProcessParentProcessId = [int]$rootCurrent.ParentProcessId
            ProcessParentProcessStartTicks = [long]$rootParentStart
            ProcessCommandLine = [string]$rootCurrent.CommandLine
            BrowserPath = $browserPath
            Profile = (Join-Path ([IO.Path]::GetTempPath()) `
                ('TroubleshootJS\gate-b-paired-profile-' + [Guid]::NewGuid().ToString('N')))
            RunId = 'gate-b-paired-run'
            RepositoryIdentity = 'gate-b-paired-worktree'
            CdpPort = 45871
        }
        $rootVerified = [pscustomobject]@{
            Process = $rootProcess
            Record = [pscustomobject]@{
                ProcessId = [int]$owner.ProcessId
                ProcessStartTicks = [long]$owner.ProcessStartTicks
                ParentProcessId = [int]$owner.ProcessParentProcessId
                ParentProcessStartTicks = [long]$owner.ProcessParentProcessStartTicks
                Name = if ($rootCurrent.PSObject.Properties['Name']) { [string]$rootCurrent.Name } else { '' }
                ExecutablePath = $browserPath
                CommandLine = [string]$owner.ProcessCommandLine
            }
        }
        $snapshot = @([pscustomobject]@{
            ProcessId = 2147483000
            ProcessStartTicks = 1L
            ParentProcessId = 1
            ParentProcessStartTicks = 1L
            Name = 'unrelated-process.exe'
            ExecutablePath = $browserPath
            CommandLine = 'unrelated-process'
        })
        $state = [pscustomobject]@{
            Owner = $owner
            OwnerPid = [int]$owner.ProcessId
            OwnerStart = [long]$owner.ProcessStartTicks
            RootParentStart = [long]$owner.ProcessParentProcessStartTicks
            OwnerProcess = $rootProcess
            RootCurrent = $rootCurrent
            RootWmi = [pscustomobject]@{
                ProcessId = [int]$owner.ProcessId
                ParentProcessId = [int]$owner.ProcessParentProcessId
                Name = if ($rootCurrent.PSObject.Properties['Name']) { [string]$rootCurrent.Name } else { '' }
                ExecutablePath = $browserPath
                CommandLine = [string]$owner.ProcessCommandLine
            }
            RootVerified = $rootVerified
            ChildPid = 0
            ChildProcess = $null
            ChildCurrent = $null
            ChildWmi = $null
            Candidate = $null
            Mode = 'normal'
            ChildExited = $false
            ChildCurrentCalls = 0
            NaturalCurrentCalls = 0
            NaturalWmiAbsenceCalls = 0
            PreviouslyAttestedCalls = 0
            InNatural = $false
            LateProofSlept = $false
            NonNullExitedLookups = 0
            UnattestedNonNullExitedLookups = 0
            TransientWmiCalls = 0
            TransientNonNullExitedLookups = 0
            FinalNonNullExitProcessByIdCalls = 0
            FinalNonNullExitLiveLookups = 0
            FinalNonNullExitLookups = 0
            FinalNonNullExitReleaseCalls = 0
            NullInitialLookups = 0
            CurrentProofFixture = $null
            FinalProofFixture = $null
            IdentityProofFixture = $null
            StopCalls = 0
        }
        $script:GateBPairedState = $state
        $script:GateBPairedOriginalCurrent = $originals['Get-VerifierCurrentProcessRecordById']
        $script:GateBPairedOriginalParentStart = $originals['Get-VerifierCurrentParentStartTicks']
        $script:GateBPairedOriginalGetProcess = $originals['Get-VerifierProcessById']
        $script:GateBPairedOriginalOwned = $originals['Get-VerifierCurrentOwnedProcess']
        $script:GateBPairedOriginalById = $originals['Get-VerifierProcessRecordsByIdWithFallback']
        $script:GateBPairedOriginalByParent = $originals['Get-VerifierProcessRecordsByParentWithFallback']
        $script:GateBPairedOriginalPrevious = $originals['Get-VerifierPreviouslyAttestedBrowserDescendantExit']
        $script:GateBPairedOriginalStop = $originals['Stop-VerifierVerifiedProcessExactly']

        function New-PairedDescendantFixture() {
            $process = $null
            $scope = $null
            $releasePath = Join-Path ([IO.Path]::GetTempPath()) `
                ('TroubleshootJS-gate-b-paired-release-' + [Guid]::NewGuid().ToString('N') + '.signal')
            try {
                $command = "while (-not [IO.File]::Exists('$releasePath')) { Start-Sleep -Milliseconds 25 }"
                $process = Start-VerifierProcess $shell @(
                    '-NoLogo', '-NoProfile', '-NonInteractive', '-ExecutionPolicy',
                    'Bypass', '-Command', $command)
                if ($null -eq $process -or $process.GetType() -ne [Diagnostics.Process]) {
                    Throw-VerifierInfrastructure 'paired browser descendant fixture did not return a real Process object.'
                }
                $wmiRecords = @(& $script:GateBPairedOriginalById ([int]$process.Id) `
                    'paired browser descendant fixture record')
                if ($wmiRecords.Count -ne 1 -or $null -eq $wmiRecords[0]) {
                    Throw-VerifierInfrastructure ('paired browser descendant fixture PID ' +
                        [string]$process.Id + ' had no unique current WMI record.')
                }
                $wmiRecord = $wmiRecords[0]
                $retainedProcess = [Diagnostics.Process]::GetProcessById([int]$process.Id)
                $record = [pscustomobject]@{
                    Process = $retainedProcess
                    ProcessId = [int]$wmiRecord.ProcessId
                    ProcessStartTicks = [long](Get-VerifierProcessStartTicks $retainedProcess)
                    ParentProcessId = [int]$wmiRecord.ParentProcessId
                    ParentProcessStartTicks = [long]$state.OwnerStart
                    Name = [string]$wmiRecord.Name
                    ExecutablePath = [string]$wmiRecord.ExecutablePath
                    CommandLine = [string]$wmiRecord.CommandLine
                }
                $record | Add-Member -MemberType NoteProperty `
                    -Name ParentProcessStartTicks -Value ([long]$state.OwnerStart) -Force
                $scope = New-VerifierBrowserDrainScope
                $candidate = $record | Select-Object *
                return [pscustomobject]@{
                    Process = $process
                    Record = $record
                    Candidate = $candidate
                    Scope = $scope
                    ReleasePath = $releasePath
                }
            } catch {
                if ($null -ne $scope) {
                    try { [void](Dispose-VerifierBrowserDrainScope $scope) } catch { }
                }
                if ($null -ne $process) {
                    try { [void]$process.WaitForExit(10000) } catch { }
                    try { $process.Dispose() } catch { }
                }
                if (Test-Path -LiteralPath $releasePath) {
                    Remove-Item -LiteralPath $releasePath -Force -ErrorAction SilentlyContinue
                }
                throw
            }
        }
        function Release-PairedDescendantFixture($Fixture) {
            if ($null -eq $Fixture -or $null -eq $Fixture.Process) {
                Throw-VerifierInfrastructure 'paired browser descendant release omitted its fixture Process.'
            }
            try {
                [IO.File]::WriteAllText([string]$Fixture.ReleasePath, 'release')
            } catch {
                Throw-VerifierInfrastructure ('Could not signal paired browser descendant natural exit: ' +
                    (Get-VerifierErrorMessage $_))
            }
            try {
                if (-not $Fixture.Process.WaitForExit(15000)) {
                    Throw-VerifierInfrastructure 'paired browser descendant did not naturally exit after its release signal.'
                }
                $Fixture.Process.Refresh()
                if (-not [bool]$Fixture.Process.HasExited) {
                    Throw-VerifierInfrastructure 'paired browser descendant remained live after its release signal.'
                }
            } catch {
                if (Test-VerifierInfrastructureError $_) { throw }
                Throw-VerifierInfrastructure ('Could not prove paired browser descendant natural exit: ' +
                    (Get-VerifierErrorMessage $_))
            }
        }
        function Dispose-PairedDescendantFixture($Fixture) {
            if ($null -eq $Fixture) { return }
            if ($null -ne $Fixture.Process) {
                try {
                    $Fixture.Process.Refresh()
                    if (-not [bool]$Fixture.Process.HasExited) {
                        [IO.File]::WriteAllText([string]$Fixture.ReleasePath, 'cleanup-release')
                        if (-not $Fixture.Process.WaitForExit(15000)) {
                            [void]$cleanupErrors.Add('paired browser descendant fixture remained live during bounded cleanup')
                        }
                    }
                } catch { [void]$cleanupErrors.Add((Get-VerifierErrorMessage $_)) }
            }
            if ($null -ne $Fixture.Scope) {
                try {
                    foreach ($failure in @(Dispose-VerifierBrowserDrainScope $Fixture.Scope)) {
                        [void]$cleanupErrors.Add([string]$failure)
                    }
                } catch { [void]$cleanupErrors.Add((Get-VerifierErrorMessage $_)) }
            }
            if ($null -ne $Fixture.Process) {
                try { $Fixture.Process.Dispose() } catch {
                    [void]$cleanupErrors.Add((Get-VerifierErrorMessage $_))
                }
            }
            if (-not [String]::IsNullOrWhiteSpace([string]$Fixture.ReleasePath) -and
                    (Test-Path -LiteralPath $Fixture.ReleasePath)) {
                try { Remove-Item -LiteralPath $Fixture.ReleasePath -Force -ErrorAction Stop } catch {
                    [void]$cleanupErrors.Add((Get-VerifierErrorMessage $_))
                }
            }
        }
        function Assert-PairedTypedFailure($Action, [string]$Name) {
            $typed = $false
            try { [void](& $Action) } catch {
                $typed = Test-VerifierInfrastructureError $_
            }
            if (-not $typed) {
                Throw-VerifierInfrastructure "paired browser descendant case '$Name' did not reject with typed infrastructure failure."
            }
            return $true
        }
        function Invoke-PairedDescendant($Fixture) {
            return @(Get-VerifierDescendantProcessRecords $state.Owner $snapshot $Fixture.Scope)
        }
        function Set-PairedChildState($Fixture, [string]$Mode,
                [bool]$Exited) {
            if ($null -eq $Fixture -or $null -eq $Fixture.Record) {
                Throw-VerifierInfrastructure 'paired browser child state omitted its fixture record.'
            }
            $state.ChildPid = [int]$Fixture.Record.ProcessId
            $state.ChildProcess = $Fixture.Process
            $state.ChildCurrent = $Fixture.Record
            $state.ChildWmi = [pscustomobject]@{
                ProcessId = [int]$Fixture.Record.ProcessId
                ParentProcessId = [int]$Fixture.Record.ParentProcessId
                Name = [string]$Fixture.Record.Name
                ExecutablePath = [string]$Fixture.Record.ExecutablePath
                CommandLine = [string]$Fixture.Record.CommandLine
            }
            $state.Candidate = $Fixture.Candidate
            $state.Mode = $Mode
            $state.ChildExited = $Exited
            $state.ChildCurrentCalls = 0
            $state.NaturalCurrentCalls = 0
            $state.NaturalWmiAbsenceCalls = 0
        }
        # Capture real child records before installing lower-provider seams.
        # Release signals are held until each fixture has reached its intended
        # attestation/disappearance phase.
        $positive = New-PairedDescendantFixture
        [void]$fixtures.Add($positive)
        $transientWmi = New-PairedDescendantFixture
        [void]$fixtures.Add($transientWmi)
        $missing = New-PairedDescendantFixture
        [void]$fixtures.Add($missing)
        $missingExited = New-PairedDescendantFixture
        [void]$fixtures.Add($missingExited)
        $nullInitial = New-PairedDescendantFixture
        [void]$fixtures.Add($nullInitial)
        $currentProof = New-PairedDescendantFixture
        [void]$fixtures.Add($currentProof)
        $finalProof = New-PairedDescendantFixture
        [void]$fixtures.Add($finalProof)
        $identityProof = New-PairedDescendantFixture
        [void]$fixtures.Add($identityProof)
        try {
            Set-Item Function:\Get-VerifierCurrentOwnedProcess -Force -Value {
                param($OwnerRecord, $Recorded, [switch]$Root)
                if ($Root) { return $script:GateBPairedState.RootVerified }
                return & $script:GateBPairedOriginalOwned $OwnerRecord $Recorded
            }
            Set-Item Function:\Get-VerifierCurrentProcessRecordById -Force -Value {
                param($ProcessId)
                $stateValue = $script:GateBPairedState
                if ([int]$ProcessId -eq [int]$stateValue.OwnerPid) {
                    return $stateValue.RootCurrent
                }
                if ([int]$ProcessId -eq [int]$stateValue.ChildPid) {
                    $stateValue.ChildCurrentCalls++
                    if ($stateValue.InNatural) { $stateValue.NaturalCurrentCalls++ }
                    if ($stateValue.Mode -eq 'typed-current-exit' -and
                            -not $stateValue.InNatural -and
                            $stateValue.ChildCurrentCalls -eq 1) {
                        if (-not $stateValue.ChildExited) {
                            if ($null -eq $stateValue.CurrentProofFixture) {
                                Throw-VerifierInfrastructure 'paired current-proof exit omitted its live fixture.'
                            }
                            Release-PairedDescendantFixture $stateValue.CurrentProofFixture
                            $stateValue.ChildExited = $true
                        }
                        return $stateValue.ChildCurrent
                    }
                    if ($stateValue.Mode -eq 'identity-mismatch' -and
                            -not $stateValue.InNatural -and
                            $stateValue.ChildCurrentCalls -eq 1) {
                        $mismatched = $stateValue.ChildCurrent | Select-Object *
                        $mismatched.CommandLine = [string]$stateValue.ChildCurrent.CommandLine +
                            ' mismatched-current-proof'
                        return $mismatched
                    }
                    if ($stateValue.ChildExited) { return $null }
                    return $stateValue.ChildCurrent
                }
                return & $script:GateBPairedOriginalCurrent ([int]$ProcessId)
            }
            Set-Item Function:\Get-VerifierCurrentParentStartTicks -Force -Value {
                param($ParentProcessId)
                $stateValue = $script:GateBPairedState
                if ([int]$ParentProcessId -eq [int]$stateValue.OwnerPid) {
                    return [long]$stateValue.OwnerStart
                }
                if ([int]$ParentProcessId -eq [int]$stateValue.RootCurrent.ParentProcessId) {
                    return [long]$stateValue.RootParentStart
                }
                return & $script:GateBPairedOriginalParentStart ([int]$ParentProcessId)
            }
            Set-Item Function:\Get-VerifierProcessById -Force -Value {
                param($ProcessId)
                $stateValue = $script:GateBPairedState
                if ([int]$ProcessId -eq [int]$stateValue.ChildPid) {
                    if ($stateValue.Mode -eq 'initial-miss') { return $null }
                    if ($stateValue.Mode -eq 'initial-null-exit') {
                        $stateValue.NullInitialLookups++
                        return $null
                    }
                    if ($stateValue.Mode -eq 'final-nonnull-exit') {
                        $stateValue.FinalNonNullExitProcessByIdCalls++
                        $liveProcess = $stateValue.ChildCurrent.Process
                        if ($null -eq $liveProcess -or
                                $liveProcess.GetType() -ne [Diagnostics.Process]) {
                            Throw-VerifierInfrastructure 'paired final non-null exit proof did not retain a real Process object.'
                        }
                        if ([int]$stateValue.FinalNonNullExitProcessByIdCalls -le 2) {
                            try {
                                $liveProcess.Refresh()
                                if ([bool]$liveProcess.HasExited) {
                                    Throw-VerifierInfrastructure 'paired final non-null exit proof lost its live Process before the final lookup.'
                                }
                            } catch {
                                if (Test-VerifierInfrastructureError $_) { throw }
                                Throw-VerifierInfrastructure ('Could not inspect paired final non-null exit live lookup: ' +
                                    (Get-VerifierErrorMessage $_))
                            }
                            $stateValue.FinalNonNullExitLiveLookups++
                            return $liveProcess
                        }
                        if ([int]$stateValue.FinalNonNullExitProcessByIdCalls -eq 3) {
                            if ($null -eq $stateValue.FinalProofFixture) {
                                Throw-VerifierInfrastructure 'paired final non-null exit proof omitted its live fixture.'
                            }
                            Release-PairedDescendantFixture $stateValue.FinalProofFixture
                            $stateValue.ChildExited = $true
                            $stateValue.FinalNonNullExitReleaseCalls++
                            $stateValue.FinalNonNullExitLookups++
                            # Return the exact retained Process instance after its
                            # natural exit so Get-VerifierCurrentOwnedProcessOnce
                            # must traverse the final non-null Refresh/HasExited
                            # guard rather than the initial-null branch.
                            return $liveProcess
                        }
                        Throw-VerifierInfrastructure 'paired final non-null exit proof performed an unexpected extra Process lookup.'
                    }
                    if ($stateValue.ChildExited -and
                            $stateValue.Mode -in @('normal', 'initial-exited-miss', 'transient-wmi')) {
                        $stale = $stateValue.ChildCurrent.Process
                        if ($null -ne $stale -and
                                $stale.GetType() -eq [Diagnostics.Process]) {
                            try {
                                $stale.Refresh()
                                if ([bool]$stale.HasExited) {
                                    if ($stateValue.Mode -eq 'normal') {
                                        $stateValue.NonNullExitedLookups++
                                    } elseif ($stateValue.Mode -eq 'transient-wmi') {
                                        $stateValue.TransientNonNullExitedLookups++
                                    } else {
                                        $stateValue.UnattestedNonNullExitedLookups++
                                    }
                                    return $stale
                                }
                            } catch { }
                        }
                    }
                    if ($stateValue.Mode -eq 'typed-current-exit') {
                        if ($stateValue.ChildExited) { return $null }
                        return $stateValue.ChildCurrent.Process
                    }
                    if ($stateValue.Mode -eq 'identity-mismatch') {
                        return $stateValue.ChildCurrent.Process
                    }
                }
                $processResult = & $script:GateBPairedOriginalGetProcess ([int]$ProcessId)
                return $processResult
            }
            Set-Item Function:\Get-VerifierProcessRecordsByIdWithFallback -Force -Value {
                param($ProcessId, $Label)
                $stateValue = $script:GateBPairedState
                if ([int]$ProcessId -eq [int]$stateValue.OwnerPid) {
                    return @($stateValue.RootWmi)
                }
                if ([int]$ProcessId -eq [int]$stateValue.ChildPid) {
                    if ($stateValue.Mode -eq 'transient-wmi') {
                        $stateValue.TransientWmiCalls++
                        if ($stateValue.TransientWmiCalls -eq 1) {
                            return @($stateValue.ChildWmi)
                        }
                        return @()
                    }
                    if ($stateValue.ChildExited -and
                            $stateValue.Mode -in @('normal', 'initial-null-exit',
                                'typed-current-exit', 'final-nonnull-exit', 'late-proof')) {
                        $stateValue.NaturalWmiAbsenceCalls++
                        if ($stateValue.Mode -eq 'late-proof' -and
                                -not $stateValue.LateProofSlept) {
                            $stateValue.LateProofSlept = $true
                            Start-Sleep -Milliseconds 650
                        }
                        return @()
                    }
                    return @($stateValue.ChildWmi)
                }
                return & $script:GateBPairedOriginalById $ProcessId $Label
            }
            Set-Item Function:\Get-VerifierProcessRecordsByParentWithFallback -Force -Value {
                param($ParentProcessId, $Label)
                $stateValue = $script:GateBPairedState
                if ([int]$ParentProcessId -eq [int]$stateValue.OwnerPid) {
                    return @($stateValue.Candidate)
                }
                return @()
            }
            Set-Item Function:\Get-VerifierPreviouslyAttestedBrowserDescendantExit -Force -Value {
                param($DrainScope, $CandidateRecord)
                $stateValue = $script:GateBPairedState
                $stateValue.PreviouslyAttestedCalls++
                $stateValue.InNatural = $true
                try {
                    return & $script:GateBPairedOriginalPrevious $DrainScope $CandidateRecord
                } finally {
                    $stateValue.InNatural = $false
                }
            }
            Set-Item Function:\Stop-VerifierVerifiedProcessExactly -Force -Value {
                param($Process, $ExpectedStartTicks, $WaitMilliseconds, $ExpectedRecord)
                $script:GateBPairedState.StopCalls++
                return & $script:GateBPairedOriginalStop $Process $ExpectedStartTicks `
                    $WaitMilliseconds $ExpectedRecord
            }
            # First prove the normal live path and retain the exact record,
            # Process object, native handle, and immutable identity tuple.
            Set-PairedChildState $positive 'normal' $false
            $first = @(Invoke-PairedDescendant $positive)
            if ($first.Count -ne 1 -or $null -eq $first[0] -or
                    -not $first[0].PSObject.Properties['VerifierDrainAttestation'] -or
                    $first[0].Process.GetType() -ne [Diagnostics.Process]) {
                Throw-VerifierInfrastructure 'paired browser descendant live proof did not produce one fully attested real child record.'
            }
            $firstRecord = $first[0]
            $firstProcess = $firstRecord.Process
            $firstPid = [int]$firstRecord.ProcessId
            $firstStart = [long]$firstRecord.ProcessStartTicks
            Release-PairedDescendantFixture $positive
            $state.ChildExited = $true
            $state.Mode = 'normal'
            $state.ChildCurrentCalls = 0
            $state.NaturalCurrentCalls = 0
            $second = @(Invoke-PairedDescendant $positive)
            if ($second.Count -ne 1 -or
                    -not [object]::ReferenceEquals($second[0], $firstRecord) -or
                    -not [object]::ReferenceEquals($second[0].Process, $firstProcess) -or
                    [int]$second[0].ProcessId -ne $firstPid -or
                    [long]$second[0].ProcessStartTicks -ne $firstStart -or
                    [int]$state.NonNullExitedLookups -lt 1 -or
                    [int]$state.PreviouslyAttestedCalls -ne 1 -or
                    [int]$state.NaturalWmiAbsenceCalls -ne 2) {
                Throw-VerifierInfrastructure 'paired browser descendant natural exit did not return the original attested record after two current absence proofs.'
            }

            # A well-formed WMI record can survive briefly after the retained
            # native handle has exited. It is neither accepted as absence nor
            # turned into a Stop-Process target: preserve only the original
            # attested record, mark it privately pending, then require two
            # fresh empty observations on the next bounded attempt.
            Set-PairedChildState $transientWmi 'normal' $false
            $transientFirst = @(Invoke-PairedDescendant $transientWmi)
            if ($transientFirst.Count -ne 1 -or
                    $transientFirst[0].Process.GetType() -ne [Diagnostics.Process]) {
                Throw-VerifierInfrastructure 'paired transient-WMI fixture did not produce a live attested record.'
            }
            $transientRecord = $transientFirst[0]
            $transientProcess = $transientRecord.Process
            $beforeTransientHelpers = [int]$state.PreviouslyAttestedCalls
            Release-PairedDescendantFixture $transientWmi
            Set-PairedChildState $transientWmi 'transient-wmi' $true
            $state.TransientWmiCalls = 0
            $transientPending = @(Invoke-PairedDescendant $transientWmi)
            if ($transientPending.Count -ne 1 -or
                    -not [object]::ReferenceEquals($transientPending[0], $transientRecord) -or
                    -not [object]::ReferenceEquals($transientPending[0].Process, $transientProcess) -or
                    -not $transientPending[0].PSObject.Properties['VerifierPendingNaturalExit'] -or
                    -not [object]::ReferenceEquals($transientPending[0].VerifierPendingNaturalExit,
                        $script:VerifierBrowserDrainPendingNaturalExitMarker) -or
                    [int]$state.TransientWmiCalls -ne 1 -or
                    [int]$state.TransientNonNullExitedLookups -lt 1 -or
                    [int]$state.PreviouslyAttestedCalls -ne ($beforeTransientHelpers + 1)) {
                Throw-VerifierInfrastructure 'paired transient-WMI exit did not retain only the original pending attestation.'
            }
            $transientSettled = @(Invoke-PairedDescendant $transientWmi)
            if ($transientSettled.Count -ne 1 -or
                    -not [object]::ReferenceEquals($transientSettled[0], $transientRecord) -or
                    -not [object]::ReferenceEquals($transientSettled[0].Process, $transientProcess) -or
                    $transientSettled[0].PSObject.Properties['VerifierPendingNaturalExit'] -or
                    [int]$state.TransientWmiCalls -ne 3 -or
                    [int]$state.PreviouslyAttestedCalls -ne ($beforeTransientHelpers + 2)) {
                Throw-VerifierInfrastructure 'paired transient-WMI exit did not retry to two fresh empty absence observations.'
            }

            # The same attested natural exit must also be accepted when the
            # initial post-exit provider lookup is a true null rather than a
            # stale exited Process object.
            Set-PairedChildState $nullInitial 'normal' $false
            $nullInitialFirst = @(Invoke-PairedDescendant $nullInitial)
            if ($nullInitialFirst.Count -ne 1 -or
                    $nullInitialFirst[0].Process.GetType() -ne [Diagnostics.Process]) {
                Throw-VerifierInfrastructure 'paired null-initial fixture did not produce a live attested record.'
            }
            $nullInitialRecord = $nullInitialFirst[0]
            $nullInitialProcess = $nullInitialRecord.Process
            $nullInitialBeforeHelpers = [int]$state.PreviouslyAttestedCalls
            Release-PairedDescendantFixture $nullInitial
            Set-PairedChildState $nullInitial 'initial-null-exit' $true
            $InvokeNullInitial = @(Invoke-PairedDescendant $nullInitial)
            if ($InvokeNullInitial.Count -ne 1 -or
                    -not [object]::ReferenceEquals($InvokeNullInitial[0], $nullInitialRecord) -or
                    -not [object]::ReferenceEquals($InvokeNullInitial[0].Process, $nullInitialProcess) -or
                    [int]$state.NullInitialLookups -lt 1 -or
                    [int]$state.PreviouslyAttestedCalls -ne ($nullInitialBeforeHelpers + 1) -or
                    [int]$state.NaturalWmiAbsenceCalls -ne 2) {
                Throw-VerifierInfrastructure 'paired null-initial natural exit did not return the original attested record after two current absence proofs.'
            }

            # A child that exits during the lower current-child proof starts
            # live, is first attested, then disappears only after the first
            # current record callback. The typed missing callback may reuse
            # that prior attestation; it must not mint a new handle.
            Set-PairedChildState $currentProof 'normal' $false
            $currentFirst = @(Invoke-PairedDescendant $currentProof)
            if ($currentFirst.Count -ne 1 -or
                    $currentFirst[0].Process.GetType() -ne [Diagnostics.Process]) {
                Throw-VerifierInfrastructure 'paired current-proof fixture did not produce a live attested record.'
            }
            $currentFirstRecord = $currentFirst[0]
            $currentFirstProcess = $currentFirstRecord.Process
            $state.CurrentProofFixture = $currentProof
            Set-PairedChildState $currentProof 'typed-current-exit' $false
            $beforeCurrentExitHelpers = [int]$state.PreviouslyAttestedCalls
            $duringProof = @(Invoke-PairedDescendant $currentProof)
            if ($duringProof.Count -ne 1 -or
                    -not [object]::ReferenceEquals($duringProof[0], $currentFirstRecord) -or
                    -not [object]::ReferenceEquals($duringProof[0].Process, $currentFirstProcess) -or
                    [int]$state.PreviouslyAttestedCalls -ne ($beforeCurrentExitHelpers + 1) -or
                    [int]$state.NaturalWmiAbsenceCalls -ne 2) {
                Throw-VerifierInfrastructure 'paired browser descendant current-proof exit did not reuse the original attestation and its two absence proofs.'
            }

            # A child that exits only at the final Process lookup must traverse
            # the non-null Refresh/HasExited guard in Get-VerifierCurrentOwnedProcessOnce.
            # The candidate lookup and that helper's initial lookup both remain
            # live; only the third lookup returns the same real Process object
            # after natural exit. The retained helper then reuses the original
            # attestation and performs its two independent current absences.
            Set-PairedChildState $finalProof 'normal' $false
            $finalFirst = @(Invoke-PairedDescendant $finalProof)
            if ($finalFirst.Count -ne 1 -or
                    $finalFirst[0].Process.GetType() -ne [Diagnostics.Process]) {
                Throw-VerifierInfrastructure 'paired final non-null exit fixture did not produce a live attested record.'
            }
            $finalFirstRecord = $finalFirst[0]
            $finalFirstProcess = $finalFirstRecord.Process
            $state.FinalProofFixture = $finalProof
            $state.FinalNonNullExitProcessByIdCalls = 0
            $state.FinalNonNullExitLiveLookups = 0
            $state.FinalNonNullExitLookups = 0
            $state.FinalNonNullExitReleaseCalls = 0
            $beforeFinalExitHelpers = [int]$state.PreviouslyAttestedCalls
            Set-PairedChildState $finalProof 'final-nonnull-exit' $false
            $finalDuringProof = @(Invoke-PairedDescendant $finalProof)
            if ($finalDuringProof.Count -ne 1 -or
                    -not [object]::ReferenceEquals($finalDuringProof[0], $finalFirstRecord) -or
                    -not [object]::ReferenceEquals($finalDuringProof[0].Process, $finalFirstProcess) -or
                    [int]$state.FinalNonNullExitProcessByIdCalls -ne 3 -or
                    [int]$state.FinalNonNullExitLiveLookups -ne 2 -or
                    [int]$state.FinalNonNullExitLookups -ne 1 -or
                    [int]$state.FinalNonNullExitReleaseCalls -ne 1 -or
                    -not [bool]$state.ChildExited -or
                    [int]$state.PreviouslyAttestedCalls -ne ($beforeFinalExitHelpers + 1) -or
                    [int]$state.NaturalWmiAbsenceCalls -ne 2) {
                Throw-VerifierInfrastructure 'paired final non-null exit did not traverse the final exited-Process guard, retain the original attestation, and prove two current absences.'
            }

            # An untyped current-record identity mismatch also starts live and
            # must fail at the current-child callback before the retained-exit
            # helper is consulted.
            Set-PairedChildState $identityProof 'normal' $false
            $identityFirst = @(Invoke-PairedDescendant $identityProof)
            if ($identityFirst.Count -ne 1 -or
                    $identityFirst[0].Process.GetType() -ne [Diagnostics.Process]) {
                Throw-VerifierInfrastructure 'paired identity-proof fixture did not produce a live attested record.'
            }
            Set-PairedChildState $identityProof 'identity-mismatch' $false
            $beforeMismatchHelpers = [int]$state.PreviouslyAttestedCalls
            [void](Assert-PairedTypedFailure {
                [void](Invoke-PairedDescendant $identityProof)
            } 'live-current-record-identity-mismatch')
            if ([int]$state.PreviouslyAttestedCalls -ne $beforeMismatchHelpers) {
                Throw-VerifierInfrastructure 'live current-record identity mismatch incorrectly entered the retained natural-exit helper.'
            }

            # Candidate identity changes are rejected even when a matching PID
            # has already left the OS. They cannot borrow a prior attestation.
            Set-PairedChildState $positive 'normal' $true
            foreach ($case in @(
                    [pscustomobject]@{ Name='altered-name'; Field='Name'; Value='other.exe' }
                    [pscustomobject]@{ Name='altered-path'; Field='ExecutablePath'; Value='C:\other\browser.exe' }
                    [pscustomobject]@{ Name='altered-command'; Field='CommandLine'; Value='different-command' }
                    [pscustomobject]@{ Name='altered-parent'; Field='ParentProcessId'; Value=[int]$owner.ProcessParentProcessId })) {
                $candidate = $positive.Candidate | Select-Object *
                $candidate.($case.Field) = $case.Value
                $state.Mode = 'normal'
                $state.Candidate = $candidate
                $state.ChildCurrentCalls = 0
                $state.NaturalCurrentCalls = 0
                $before = [int]$state.PreviouslyAttestedCalls
                [void](Assert-PairedTypedFailure {
                    [void](Invoke-PairedDescendant $positive)
                } $case.Name)
                $expectedHelperDelta = if ($case.Name -eq 'altered-command') { 1 } else { 0 }
                if ([int]$state.PreviouslyAttestedCalls -ne ($before + $expectedHelperDelta)) {
                    Throw-VerifierInfrastructure "candidate identity change '$($case.Name)' incorrectly entered the retained natural-exit helper."
                }
            }

            # Scope and attestation capabilities are reference-bound. Copies,
            # foreign scopes, copied attestations, and scalar mutations fail.
            $state.Candidate = $positive.Candidate
            $state.Mode = 'normal'
            $scopeCopy = $positive.Scope | Select-Object *
            [void](Assert-PairedTypedFailure {
                [void](Get-VerifierDescendantProcessRecords $state.Owner $snapshot $scopeCopy)
            } 'copied-scope')
            $foreignScope = New-VerifierBrowserDrainScope
            try {
                [void](Assert-PairedTypedFailure {
                    [void](Get-VerifierDescendantProcessRecords $state.Owner $snapshot $foreignScope)
                } 'foreign-scope')
            } finally {
                foreach ($failure in @(Dispose-VerifierBrowserDrainScope $foreignScope)) {
                    [void]$cleanupErrors.Add([string]$failure)
                }
            }
            $attestation = $firstRecord.VerifierDrainAttestation
            $attestationCopy = $attestation | Select-Object *
            $firstRecord.VerifierDrainAttestation = $attestationCopy
            try {
                [void](Assert-PairedTypedFailure {
                    [void](Invoke-PairedDescendant $positive)
                } 'copied-attestation')
            } finally { $firstRecord.VerifierDrainAttestation = $attestation }
            $originalScope = $attestation.Scope
            $attestation.Scope = $foreignScope
            try {
                [void](Assert-PairedTypedFailure {
                    [void](Invoke-PairedDescendant $positive)
                } 'foreign-attestation')
            } finally { $attestation.Scope = $originalScope }
            $originalCommand = [string]$attestation.CommandLine
            $attestation.CommandLine = $originalCommand + ' mutated'
            try {
                [void](Assert-PairedTypedFailure {
                    [void](Invoke-PairedDescendant $positive)
                } 'mutated-attestation')
            } finally { $attestation.CommandLine = $originalCommand }

            # A late current absence proof is bounded and remains typed; the
            # helper never widens the 500 ms proof interval.
            $state.Mode = 'late-proof'
            $state.Candidate = $positive.Candidate
            $state.ChildCurrentCalls = 0
            $state.NaturalCurrentCalls = 0
            $state.NaturalWmiAbsenceCalls = 0
            $state.LateProofSlept = $false
            [void](Assert-PairedTypedFailure {
                [void](Invoke-PairedDescendant $positive)
            } 'late-absence-proof')
            if (-not $state.LateProofSlept -or [int]$state.NaturalWmiAbsenceCalls -ne 1) {
                Throw-VerifierInfrastructure 'late natural-exit absence proof did not stop inside the bounded helper.'
            }

            # First-ever/unattested disappearance is never eligible for the
            # retained-exit lane, even when the candidate record is otherwise
            # complete and the Process exited naturally.
            Set-PairedChildState $missing 'initial-miss' $false
            Release-PairedDescendantFixture $missing
            Set-PairedChildState $missing 'initial-miss' $true
            $beforeMissingHelpers = [int]$state.PreviouslyAttestedCalls
            [void](Assert-PairedTypedFailure {
                [void](Invoke-PairedDescendant $missing)
            } 'first-ever-unattested-missing-child')
            if ([int]$state.PreviouslyAttestedCalls -ne ($beforeMissingHelpers + 1) -or
                    [int]$state.NaturalWmiAbsenceCalls -ne 0) {
                Throw-VerifierInfrastructure 'first-ever missing child was treated as a retained natural exit.'
            }

            # A first-ever child that is returned as a stale exited Process is
            # equally ineligible for the retained-exit lane.
            Set-PairedChildState $missingExited 'initial-exited-miss' $false
            Release-PairedDescendantFixture $missingExited
            Set-PairedChildState $missingExited 'initial-exited-miss' $true
            $beforeMissingExitedHelpers = [int]$state.PreviouslyAttestedCalls
            [void](Assert-PairedTypedFailure {
                [void](Invoke-PairedDescendant $missingExited)
            } 'first-ever-unattested-exited-child')
            if ([int]$state.UnattestedNonNullExitedLookups -lt 1 -or
                    [int]$state.PreviouslyAttestedCalls -ne ($beforeMissingExitedHelpers + 1) -or
                    [int]$state.NaturalWmiAbsenceCalls -ne 0) {
                Throw-VerifierInfrastructure 'first-ever exited child was treated as a retained natural exit.'
            }

            if ([int]$state.StopCalls -ne 0) {
                Throw-VerifierInfrastructure 'paired natural-exit canary invoked exact process stop for a naturally exited child.'
            }
            return [pscustomobject]@{
                PositiveInitialAttestation = $true
                PositiveNonNullExitedLookup = ([int]$state.NonNullExitedLookups -gt 0)
                TransientWmiRetry = $true
                PositiveNullInitialLookupExit = ([int]$state.NullInitialLookups -gt 0)
                PositiveCurrentProofExit = $true
                PositiveFinalNonNullExitGuard = ([int]$state.FinalNonNullExitLookups -eq 1 -and
                    [int]$state.FinalNonNullExitLiveLookups -eq 2 -and
                    [int]$state.FinalNonNullExitReleaseCalls -eq 1)
                TwoCurrentAbsenceProofs = $true
                FirstEverUnattestedMissing = $true
                FirstEverUnattestedExited = $true
                PresentOrReusedRejected = $true
                LiveCurrentIdentityMismatchRejected = $true
                AlteredIdentityRejected = $true
                CopiedForeignMutatedAttestationRejected = $true
                LateProofRejected = $true
                NoNaturalExitStop = ([int]$state.StopCalls -eq 0)
            }
        } finally {
            foreach ($fixture in @($fixtures)) {
                Dispose-PairedDescendantFixture $fixture
            }
            foreach ($name in $functionNames) {
                Set-Item -LiteralPath ('Function:\' + $name) `
                    -Value $originals[$name] -Force
            }
            foreach ($name in @('GateBPairedState', 'GateBPairedOriginalCurrent',
                    'GateBPairedOriginalParentStart', 'GateBPairedOriginalGetProcess',
                    'GateBPairedOriginalOwned', 'GateBPairedOriginalById',
                    'GateBPairedOriginalByParent', 'GateBPairedOriginalPrevious',
                    'GateBPairedOriginalStop')) {
                Remove-Variable -Scope Script -Name $name -Force -ErrorAction SilentlyContinue
            }
            if ($cleanupErrors.Count -gt 0) {
                Throw-VerifierInfrastructure ('paired browser descendant fixture cleanup failed: ' +
                    ($cleanupErrors -join '; '))
            }
        }
    }
    Assert-GateB ([bool]$probe.PositiveInitialAttestation -and
        [bool]$probe.PositiveNonNullExitedLookup -and
        [bool]$probe.TransientWmiRetry -and
        [bool]$probe.PositiveNullInitialLookupExit -and
        [bool]$probe.PositiveCurrentProofExit -and
        [bool]$probe.PositiveFinalNonNullExitGuard -and
        [bool]$probe.TwoCurrentAbsenceProofs -and
        [bool]$probe.FirstEverUnattestedMissing -and
        [bool]$probe.FirstEverUnattestedExited -and
        [bool]$probe.PresentOrReusedRejected -and
        [bool]$probe.LiveCurrentIdentityMismatchRejected -and
        [bool]$probe.AlteredIdentityRejected -and
        [bool]$probe.CopiedForeignMutatedAttestationRejected -and
        [bool]$probe.LateProofRejected -and
        [bool]$probe.NoNaturalExitStop) `
        'paired browser descendant disappearance canary did not validate every positive/negative lane'
    Write-Host 'PASS:paired browser descendant natural-exit retention, transient-WMI retry, final exited-Process guard, current-proof race, identity/attestation negatives, bounded absence, and no-stop contract'
}

function Invoke-GateBBrowserContainmentJobCanary() {
    $module = @(Get-Module VerifierIsolation | Select-Object -First 1)
    if ($module.Count -ne 1) {
        Throw-GateBInfrastructure 'VerifierIsolation module was unavailable for the browser containment-job canary.'
    }
    $probe = & $module[0] {
        $canaryRoot = Join-Path ([IO.Path]::GetTempPath()) `
            ('TroubleshootJS\gate-b-browser-containment-' + [Guid]::NewGuid().ToString('N'))
        $signalPath = Join-Path $canaryRoot 'release.signal'
        $job = $null
        $reopenedJob = $null
        $parent = $null
        $jobEmpty = $false
        $jobDisposed = $false
        $remnantsTerminated = $false
        $retainedProcesses = New-Object Collections.ArrayList
        $releaseWritten = $false
        $cleanupErrors = New-Object Collections.ArrayList
        try {
            New-Item -ItemType Directory -Path $canaryRoot -Force -ErrorAction Stop | Out-Null
            $api = Initialize-VerifierBrowserContainmentJobApi
            $jobName = 'Local\TroubleshootJS.Verifier.GateBContainment.' +
                [Guid]::NewGuid().ToString('N')
            $job = $api::CreateNew($jobName)
            $shell = (Get-Command powershell.exe -ErrorAction Stop).Source
            $escapedSignal = $signalPath.Replace("'", "''")
            $childScript = "while (-not [IO.File]::Exists('$escapedSignal')) { Start-Sleep -Milliseconds 25 }"
            $encodedChild = [Convert]::ToBase64String(
                [Text.Encoding]::Unicode.GetBytes($childScript))
            $escapedShell = $shell.Replace("'", "''")
            # The atomically contained parent starts this child and exits.
            # The child then proves that job membership survives a parent exit
            # without relying on a readable browser marker or a PID/PPID stop.
            $parentScript = "Start-Process -FilePath '$escapedShell' -ArgumentList @(" +
                "'-NoLogo','-NoProfile','-NonInteractive','-ExecutionPolicy','Bypass','-EncodedCommand','$encodedChild') " +
                '-WindowStyle Hidden | Out-Null'
            $parentPid = [int](Start-VerifierBrowserProcessInContainmentJob `
                -ContainmentJob $job -FilePath $shell -Arguments @(
                    '-NoLogo', '-NoProfile', '-NonInteractive', '-ExecutionPolicy',
                    'Bypass', '-Command', $parentScript) `
                -WorkingDirectory (Get-Location).Path)
            $parent = Get-Process -Id $parentPid -ErrorAction Stop
            $parent.Refresh()
            if ([bool]$parent.HasExited -or [int]$parent.Id -ne $parentPid) {
                Throw-VerifierInfrastructure 'browser containment canary did not retain a live Process handle after its durable launch PID.'
            }
            if (-not $parent.WaitForExit(5000)) {
                Throw-VerifierInfrastructure 'browser containment canary parent did not exit naturally.'
            }
            $parent.Refresh()
            if (-not [bool]$parent.HasExited) {
                Throw-VerifierInfrastructure 'browser containment canary parent had no proven natural exit.'
            }
            $retainedAfterParentExit = $false
            for ($attempt = 0; $attempt -lt 80; $attempt++) {
                $members = @($job.GetMemberProcessIds())
                if ($members.Count -gt 0 -and $members -notcontains $parentPid) {
                    $retainedAfterParentExit = $true
                    break
                }
                Start-Sleep -Milliseconds 25
            }
            if (-not $retainedAfterParentExit) {
                Throw-VerifierInfrastructure 'browser containment canary did not retain the post-parent descendant in its exact job.'
            }
            # The verifier's original job handle can now disappear.  The
            # launched parent was the only root-held query reference and has
            # exited, so KILL_ON_JOB_CLOSE must naturally terminate every
            # exact remaining job member instead of allowing this child to
            # outlive its root.  Issued-contained recovery has a live browser
            # root holding the same query-only reference; that distinct path
            # is exercised below by Invoke-GateBIssuedContainedBrowserRecoveryCanary.
            foreach ($memberId in $members) {
                if ([int]$memberId -eq $parentPid) { continue }
                try {
                    $memberProcess = Get-Process -Id ([int]$memberId) -ErrorAction Stop
                    $memberProcess.Refresh()
                    if ([bool]$memberProcess.HasExited -or [int]$memberProcess.Id -ne [int]$memberId) {
                        Throw-VerifierInfrastructure "browser containment canary could not retain a live exact job member PID $memberId before final-handle close."
                    }
                    [void]$retainedProcesses.Add($memberProcess)
                } catch {
                    if (Test-VerifierInfrastructureError $_) { throw }
                    Throw-VerifierInfrastructure ('browser containment canary could not capture an exact retained job member before final-handle close: ' +
                        (Get-VerifierErrorMessage $_))
                }
            }
            if ($retainedProcesses.Count -eq 0) {
                Throw-VerifierInfrastructure 'browser containment canary had no exact live descendant handle before final-handle close.'
            }
            $job.Dispose()
            $job = $null
            $jobDisposed = $true
            foreach ($retainedProcess in @($retainedProcesses)) {
                if (-not $retainedProcess.WaitForExit(5000)) {
                    Throw-VerifierInfrastructure "browser containment canary exact member PID $($retainedProcess.Id) survived final containment-handle close."
                }
                $retainedProcess.Refresh()
                if (-not [bool]$retainedProcess.HasExited) {
                    Throw-VerifierInfrastructure "browser containment canary exact member PID $($retainedProcess.Id) had no proven termination after final containment-handle close."
                }
            }
            $remnantsTerminated = $true
            $jobWasClosed = $false
            try {
                $unexpectedJob = $api::OpenExisting($jobName)
                try {
                    $unexpectedMembers = @($unexpectedJob.GetMemberProcessIds())
                    Throw-VerifierInfrastructure ('browser containment canary left a reopenable job after its final handle closed' +
                        " [members=$($unexpectedMembers -join ',')].")
                } finally {
                    $unexpectedJob.Dispose()
                }
            } catch {
                if (Test-VerifierInfrastructureError $_) { throw }
                $jobWasClosed = $true
            }
            if (-not $jobWasClosed) {
                Throw-VerifierInfrastructure 'browser containment canary did not prove the final containment handle and job name were gone.'
            }
            $jobEmpty = $true
            return [pscustomobject]@{
                ParentExited = [bool]$parent.HasExited
                DescendantRetained = $retainedAfterParentExit
                RemnantsTerminatedOnFinalHandleClose = $remnantsTerminated
                JobClosedAfterFinalHandleClose = $jobWasClosed
            }
        } finally {
            if (-not $releaseWritten -and (Test-Path -LiteralPath $canaryRoot)) {
                try {
                    [IO.File]::WriteAllText($signalPath, 'cleanup-release')
                    $releaseWritten = $true
                } catch { [void]$cleanupErrors.Add((Get-VerifierErrorMessage $_)) }
            }
            if ($jobDisposed -and -not $remnantsTerminated -and
                    -not $releaseWritten -and (Test-Path -LiteralPath $canaryRoot)) {
                try {
                    [IO.File]::WriteAllText($signalPath, 'post-close-cleanup-release')
                    $releaseWritten = $true
                } catch { [void]$cleanupErrors.Add((Get-VerifierErrorMessage $_)) }
            }
            if ($jobDisposed -and -not $remnantsTerminated) {
                foreach ($retainedProcess in @($retainedProcesses)) {
                    try {
                        if (-not [bool]$retainedProcess.HasExited -and
                                -not $retainedProcess.WaitForExit(5000)) {
                            throw "Exact containment canary member PID $($retainedProcess.Id) remained alive after its natural release signal."
                        }
                    } catch { [void]$cleanupErrors.Add((Get-VerifierErrorMessage $_)) }
                }
            }
            $cleanupJob = if ($null -ne $reopenedJob) { $reopenedJob } else { $job }
            if ($null -ne $cleanupJob -and -not $jobEmpty) {
                try {
                    $cleanupBudget = [Diagnostics.Stopwatch]::StartNew()
                    $cleanupTicks = [long][Math]::Ceiling(
                        ([double][Diagnostics.Stopwatch]::Frequency * 15000) / 1000.0)
                    Wait-VerifierBrowserContainmentJobEmpty $cleanupJob $cleanupBudget $cleanupTicks
                    $jobEmpty = $true
                } catch { [void]$cleanupErrors.Add((Get-VerifierErrorMessage $_)) }
            }
            if ($null -ne $parent) {
                try { $parent.Dispose() } catch { [void]$cleanupErrors.Add((Get-VerifierErrorMessage $_)) }
            }
            foreach ($retainedProcess in @($retainedProcesses)) {
                try { $retainedProcess.Dispose() } catch { [void]$cleanupErrors.Add((Get-VerifierErrorMessage $_)) }
            }
            if ($null -ne $reopenedJob) {
                try { $reopenedJob.Dispose() } catch { [void]$cleanupErrors.Add((Get-VerifierErrorMessage $_)) }
            }
            if ($null -ne $job) {
                try { $job.Dispose() } catch { [void]$cleanupErrors.Add((Get-VerifierErrorMessage $_)) }
            }
            if ($jobEmpty -and (Test-Path -LiteralPath $canaryRoot)) {
                try {
                    Remove-VerifierOwnedTree (Get-VerifierFullPath ([IO.Path]::GetTempPath())) $canaryRoot
                } catch { [void]$cleanupErrors.Add((Get-VerifierErrorMessage $_)) }
            }
            if ($cleanupErrors.Count -gt 0) {
                Throw-VerifierInfrastructure ('browser containment canary cleanup failed: ' +
                    ($cleanupErrors -join '; '))
            }
        }
    }
    Assert-GateB ($probe.ParentExited -and $probe.DescendantRetained -and
        $probe.RemnantsTerminatedOnFinalHandleClose -and
        $probe.JobClosedAfterFinalHandleClose) `
        'browser containment job did not retain an exact descendant then terminate it when the final root/verifier handle closed'
    Write-Host 'PASS:atomically assigned no-breakaway browser job retained a post-parent descendant, then KILL_ON_JOB_CLOSE removed it with the final exact handle'
}

function Invoke-GateBIssuedContainedBrowserRecoveryCanary([switch]$LaunchLedgerPublication) {
    $module = @(Get-Module VerifierIsolation | Select-Object -First 1)
    if ($module.Count -ne 1) {
        Throw-GateBInfrastructure 'VerifierIsolation module was unavailable for the issued-contained recovery canary.'
    }
    $canaryRoot = Join-Path ([IO.Path]::GetTempPath()) `
        ('TroubleshootJS\gate-b-issued-contained-recovery-' + [Guid]::NewGuid().ToString('N'))
    $childScriptPath = Join-Path $canaryRoot 'issued-contained-child.ps1'
    $childResultPath = Join-Path $canaryRoot 'issued-contained-child.json'
    $recoveryScriptPath = Join-Path $canaryRoot 'issued-contained-recovery.ps1'
    $recoveryFailureResultPath = Join-Path $canaryRoot 'issued-contained-recovery-failure.json'
    $recoverySuccessResultPath = Join-Path $canaryRoot 'issued-contained-recovery-success.json'
    $runRoot = ''
    $manifestPath = ''
    $primaryFailure = $null
    $cleanupErrors = New-Object Collections.ArrayList
    $failureMode = if ($LaunchLedgerPublication) { 'ledger' } else { 'handle' }
    try {
        New-Item -ItemType Directory -Path $canaryRoot -Force -ErrorAction Stop | Out-Null
        $childSource = @'
param([string]$ModulePath, [string]$RepositoryRoot, [string]$CanaryRoot, [string]$ResultPath,
    [string]$FailureMode)
$ErrorActionPreference = 'Stop'
# Import-Module has no -LiteralPath parameter. ModulePath is passed as one
# exact generated path argument by this canary; -Force makes this child load
# the candidate implementation rather than a prior session module.
Import-Module $ModulePath -Force
$context = New-VerifierRunContext $RepositoryRoot $CanaryRoot
if ($FailureMode -eq 'handle') {
    $context.TestHooks.FailNextBrowserContainmentHandleAcquire = $true
    $expectedFailure = 'post-containment-launch Process-handle acquisition failure'
} elseif ($FailureMode -eq 'ledger') {
    $context.TestHooks.FailNextBrowserContainmentLaunchLedgerPublish = $true
    $expectedFailure = 'post-CreateProcess containment launch-ledger publication failure'
} else {
    exit 4
}
$context.TestHooks.FailNextIssuedContainedRecovery = $true
$threw = $false
$message = ''
try {
    [void](New-VerifierBrowserSession $context 'issued-contained-recovery' 'about:blank' '' 45)
} catch {
    $threw = Test-VerifierInfrastructureError $_
    $message = Get-VerifierErrorMessage $_
}
$manifest = Get-Content -LiteralPath $context.ManifestPath -Raw | ConvertFrom-Json
$session = @($manifest.browserSessions)[0]
$result = [ordered]@{
    threw = $threw
    message = $message
    manifestPath = $context.ManifestPath
    runRoot = $context.RunRoot
    cleanupState = $manifest.cleanup.state
    sessionStatus = $session.status
    cleanupResult = $session.cleanupResult
    receiptState = $session.recoveryReceipt.state
    launchState = $session.containmentLaunch.state
    launchProcessId = $session.containmentLaunch.launchProcessId
    sessionProcessId = $session.processId
    failureMode = $FailureMode
}
[IO.File]::WriteAllText($ResultPath, ($result | ConvertTo-Json -Compress), [Text.UTF8Encoding]::new($false))
if (-not $threw -or $message -notmatch $expectedFailure) { exit 3 }
exit 0
'@
        [IO.File]::WriteAllText($childScriptPath, $childSource,
            [Text.UTF8Encoding]::new($false))
        $recoverySource = @'
param([string]$ModulePath, [string]$ManifestPath, [string]$ResultPath,
    [switch]$InjectListenerBindFailure)
$ErrorActionPreference = 'Stop'
Import-Module $ModulePath -Force
$success = $false
$typed = $false
$message = ''
try {
    if ($InjectListenerBindFailure) {
        [void](Invoke-VerifierRetainedBrowserRecovery $ManifestPath `
            -TestFailAfterIssuedContainedRootReattestation)
    } else {
        [void](Invoke-VerifierRetainedBrowserRecovery $ManifestPath)
    }
    $success = $true
} catch {
    $typed = Test-VerifierInfrastructureError $_
    $message = Get-VerifierErrorMessage $_
}
[IO.File]::WriteAllText($ResultPath, ([ordered]@{
    success = $success
    typed = $typed
    message = $message
    manifestPath = $ManifestPath
} | ConvertTo-Json -Compress), [Text.UTF8Encoding]::new($false))
exit 0
'@
        [IO.File]::WriteAllText($recoveryScriptPath, $recoverySource,
            [Text.UTF8Encoding]::new($false))
        $powershell = (Get-Command powershell.exe -ErrorAction Stop).Source
        $child = Invoke-GateBBoundedProcess $powershell @(
            '-NoLogo', '-NoProfile', '-NonInteractive', '-ExecutionPolicy', 'Bypass',
            '-File', $childScriptPath,
            '-ModulePath', (Join-Path $PSScriptRoot 'VerifierIsolation.psm1'),
            '-RepositoryRoot', $repositoryRoot,
            '-CanaryRoot', $canaryRoot,
            '-ResultPath', $childResultPath,
            '-FailureMode', $failureMode) 90000 'issued-contained browser recovery child'
        Assert-GateB ($child.TerminationProven -and [int]$child.ExitCode -eq 0 -and
            (Test-Path -LiteralPath $childResultPath -PathType Leaf)) `
            'issued-contained recovery child did not terminate with its exact durable failure evidence'
        $childResult = Get-Content -LiteralPath $childResultPath -Raw | ConvertFrom-Json
        $manifestPath = Get-VerifierFullPath ([string]$childResult.manifestPath)
        $runRoot = Get-VerifierFullPath ([string]$childResult.runRoot)
        # New-VerifierBrowserSession records the failed session transaction but
        # does not complete the enclosing run. Its run cleanup stays pending so
        # the separate retained-recovery process can resume the exact receipt.
        $expectedLaunchState = if ($LaunchLedgerPublication) { 'launch-pending' } else { 'launched' }
        $expectedFailure = if ($LaunchLedgerPublication) {
            'post-CreateProcess containment launch-ledger publication failure'
        } else {
            'post-containment-launch Process-handle acquisition failure'
        }
        $launchEvidenceValid = if ($LaunchLedgerPublication) {
            [int]$childResult.launchProcessId -eq 0
        } else {
            [int]$childResult.launchProcessId -gt 0
        }
        Assert-GateB ($childResult.threw -and
            $childResult.failureMode -eq $failureMode -and
            $childResult.message -match $expectedFailure -and
            $childResult.cleanupState -eq 'pending' -and
            $childResult.sessionStatus -eq 'cleanup-failed' -and
            $childResult.cleanupResult -eq 'infrastructure-failure' -and
            $childResult.receiptState -eq 'issued' -and
            $childResult.launchState -eq $expectedLaunchState -and
            $launchEvidenceValid -and
            [int]$childResult.sessionProcessId -eq 0) `
            'post-CreateProcess failure did not retain only the exact issued containment job/launch evidence for durable recovery'
        Assert-GateB (Test-Path -LiteralPath $manifestPath -PathType Leaf) `
            'issued-contained recovery child did not retain its exact failed manifest'
        $listenerBindRecovery = Invoke-GateBBoundedProcess $powershell @(
            '-NoLogo', '-NoProfile', '-NonInteractive', '-ExecutionPolicy', 'Bypass',
            '-File', $recoveryScriptPath,
            '-ModulePath', (Join-Path $PSScriptRoot 'VerifierIsolation.psm1'),
            '-ManifestPath', $manifestPath,
            '-ResultPath', $recoveryFailureResultPath,
            '-InjectListenerBindFailure') 90000 'issued-contained post-reattest listener recovery'
        Assert-GateB ($listenerBindRecovery.TerminationProven -and
            [int]$listenerBindRecovery.ExitCode -eq 0 -and
            (Test-Path -LiteralPath $recoveryFailureResultPath -PathType Leaf)) `
            'issued-contained post-reattest listener recovery child did not terminate with exact failure evidence'
        $listenerBindFailure = Get-Content -LiteralPath $recoveryFailureResultPath -Raw | ConvertFrom-Json
        Assert-GateB (-not $listenerBindFailure.success -and $listenerBindFailure.typed) `
            'issued-contained recovery did not fail closed at the injected post-reattest listener boundary'
        $postBindFailureManifest = Get-Content -LiteralPath $manifestPath -Raw | ConvertFrom-Json
        $postBindFailureSession = @($postBindFailureManifest.browserSessions)[0]
        $postBindFailureLease = @($postBindFailureManifest.leases)[0]
        $postBindLaunchEvidenceValid = if ($LaunchLedgerPublication) {
            [int]$postBindFailureSession.containmentLaunch.launchProcessId -eq 0
        } else {
            [int]$postBindFailureSession.containmentLaunch.launchProcessId -gt 0
        }
        Assert-GateB ($postBindFailureManifest.cleanup.state -eq 'infrastructure-failure' -and
            $postBindFailureSession.status -eq 'cleanup-failed' -and
            $postBindFailureSession.cleanupResult -eq 'infrastructure-failure' -and
            $postBindFailureSession.error -match
                'listener-bind failure after root reattestation' -and
            $postBindFailureSession.recoveryReceipt.state -eq 'issued' -and
            [int]$postBindFailureSession.processId -eq 0 -and
            [long]$postBindFailureSession.processStartTicks -eq 0 -and
            [int]$postBindFailureSession.processParentProcessId -eq 0 -and
            [long]$postBindFailureSession.processParentProcessStartTicks -eq 0 -and
            [string]::IsNullOrWhiteSpace([string]$postBindFailureSession.processCommandLine) -and
            $postBindFailureSession.containmentLaunch.state -eq $expectedLaunchState -and
            $postBindLaunchEvidenceValid -and
            $postBindFailureLease.status -eq 'leased' -and
            $postBindFailureLease.claimState -eq 'held' -and
            [int]$postBindFailureLease.boundProcessId -eq 0 -and
            [int]$postBindFailureLease.listenerProcessId -eq 0) `
            'post-reattest listener failure published a partial root/lease/receipt state instead of the exact issued containment ledger'
        $recoveryChild = Invoke-GateBBoundedProcess $powershell @(
            '-NoLogo', '-NoProfile', '-NonInteractive', '-ExecutionPolicy', 'Bypass',
            '-File', $recoveryScriptPath,
            '-ModulePath', (Join-Path $PSScriptRoot 'VerifierIsolation.psm1'),
            '-ManifestPath', $manifestPath,
            '-ResultPath', $recoverySuccessResultPath) 90000 'issued-contained final retained recovery'
        Assert-GateB ($recoveryChild.TerminationProven -and
            [int]$recoveryChild.ExitCode -eq 0 -and
            (Test-Path -LiteralPath $recoverySuccessResultPath -PathType Leaf)) `
            'issued-contained final retained recovery child did not terminate with exact result evidence'
        $recovery = Get-Content -LiteralPath $recoverySuccessResultPath -Raw | ConvertFrom-Json
        Assert-GateB ($recovery.success -and $recovery.manifestPath -ceq $manifestPath) `
            'issued-contained recovery did not complete the exact retained browser transaction'
        $finalManifest = Get-Content -LiteralPath $manifestPath -Raw | ConvertFrom-Json
        $finalSession = @($finalManifest.browserSessions)[0]
        $finalLease = @($finalManifest.leases)[0]
        Assert-GateB ($finalManifest.cleanup.state -eq 'complete' -and
            $finalSession.status -eq 'cleaned' -and
            $finalSession.cleanupResult -eq 'complete' -and
            $finalSession.recoveryReceipt.state -eq 'closed' -and
            $finalSession.containmentLaunch.state -eq 'launched' -and
            ((-not $LaunchLedgerPublication -and
              [int]$finalSession.containmentLaunch.launchProcessId -eq
                [int]$childResult.launchProcessId) -or
             ($LaunchLedgerPublication -and
              [int]$finalSession.containmentLaunch.launchProcessId -gt 0)) -and
            $finalLease.status -eq 'released' -and
            $finalLease.claimState -eq 'released' -and
            -not (Test-Path -LiteralPath $finalLease.path) -and
            -not (Test-Path -LiteralPath $finalSession.profile)) `
            'issued-contained recovery did not close the receipt and release only its exact profile/claim resources'
        # The final root's duplicated query handle is released after its job
        # membership drains. Do not repeatedly reopen the named job here: each
        # probe itself prolongs the kernel object's lifetime. One bounded grace
        # interval followed by one exact open attempt proves the name vanished.
        Start-Sleep -Milliseconds 10000
        $jobDisposed = & $module[0] {
            param($innerContext, $innerSession)
            $jobName = [string]$innerSession.containmentLaunch.jobName
            $api = Initialize-VerifierBrowserContainmentJobApi
            try {
                $job = $api::OpenExisting($jobName)
                try { return $false } finally { $job.Dispose() }
            } catch { return $true }
        } $null $finalSession
        Assert-GateB $jobDisposed `
            'issued-contained recovery left the exact browser containment job reopenable after natural Browser.close'
    } catch {
        $primaryFailure = $_
    } finally {
        if ($null -eq $primaryFailure -and -not [String]::IsNullOrWhiteSpace($runRoot) -and
                (Test-Path -LiteralPath $runRoot)) {
            try {
                $verifyRoot = Join-Path ([IO.Path]::GetTempPath()) 'TroubleshootJS\verify'
                Remove-VerifierOwnedTree $verifyRoot $runRoot
            } catch { [void]$cleanupErrors.Add((Get-VerifierErrorMessage $_)) }
        }
        if ($null -eq $primaryFailure -and (Test-Path -LiteralPath $canaryRoot)) {
            try {
                Remove-VerifierOwnedTree (Get-VerifierFullPath ([IO.Path]::GetTempPath())) $canaryRoot
            } catch { [void]$cleanupErrors.Add((Get-VerifierErrorMessage $_)) }
        }
    }
    if ($null -ne $primaryFailure) {
        if (Test-VerifierInfrastructureError $primaryFailure) { throw $primaryFailure }
        Throw-GateBInfrastructure ('issued-contained browser recovery canary failed; exact retained evidence remains at ' +
            $canaryRoot + ': ' + (Get-VerifierErrorMessage $primaryFailure))
    }
    if ($cleanupErrors.Count -gt 0) {
        Throw-GateBInfrastructure ('issued-contained browser recovery canary cleanup failed: ' +
            ($cleanupErrors -join '; '))
    }
    if ($LaunchLedgerPublication) {
        Write-Host 'PASS:pre-ledger durable launch intent reattached the exact contained root after parent exit and recovered it without PID termination'
    } else {
        Write-Host 'PASS:post-CreateProcess handle failure retained the exact no-breakaway launch ledger and recovered it after parent exit without PID termination'
    }
}

function Invoke-GateBBoundReceiptRecoveryBoundaryCanary() {
    $module = @(Get-Module VerifierIsolation | Select-Object -First 1)
    if ($module.Count -ne 1) {
        Throw-GateBInfrastructure 'VerifierIsolation module was unavailable for the bound-receipt recovery boundary canary.'
    }
    $missingReceipt = & $module[0] {
        $savedManifest = (Get-Command Assert-VerifierDurableManifestContext `
            -CommandType Function -ErrorAction Stop).ScriptBlock
        $savedSession = (Get-Command Assert-VerifierDurableBrowserSession `
            -CommandType Function -ErrorAction Stop).ScriptBlock
        $savedWrite = (Get-Command Write-VerifierManifest `
            -CommandType Function -ErrorAction Stop).ScriptBlock
        $script:GateBBoundReceiptMissingWrites = 0
        try {
            Set-Item Function:\Assert-VerifierDurableManifestContext -Force -Value { param($Context) }
            Set-Item Function:\Assert-VerifierDurableBrowserSession -Force -Value {
                param($Session, $Context, $Label)
            }
            Set-Item Function:\Write-VerifierManifest -Force -Value {
                param($Context)
                $script:GateBBoundReceiptMissingWrites++
            }
            $typed = $false
            try {
                Complete-VerifierBrowserSession ([pscustomobject]@{}) `
                    ([pscustomobject]@{ RecoveryReceipt = $null })
            } catch { $typed = Test-VerifierInfrastructureError $_ }
            return [pscustomobject]@{
                Typed = $typed; Writes = [int]$script:GateBBoundReceiptMissingWrites
            }
        } finally {
            Set-Item Function:\Assert-VerifierDurableManifestContext -Force -Value $savedManifest
            Set-Item Function:\Assert-VerifierDurableBrowserSession -Force -Value $savedSession
            Set-Item Function:\Write-VerifierManifest -Force -Value $savedWrite
            Remove-Variable -Name GateBBoundReceiptMissingWrites -Scope Script `
                -Force -ErrorAction SilentlyContinue
        }
    }

    $protectedHelper = & $module[0] {
        $functionNames = @(
            'Get-VerifierProcessById',
            'Get-VerifierProcessRecordsByIdWithFallback',
            'Get-VerifierProcessRecordsByParentWithFallback',
            'Get-VerifierProcessStartTicks',
            'Stop-VerifierVerifiedProcessExactly')
        $saved = @{}
        foreach ($name in $functionNames) {
            $saved[$name] = (Get-Command $name -CommandType Function -ErrorAction Stop).ScriptBlock
        }
        $scope = $null
        $reuseScope = $null
        $escapedGrandchildScope = $null
        $startCaptureScope = $null
        $leafNatural = $false
        $leafSnapshotRejected = $false
        $startCaptureExitAccepted = $false
        $child = $null
        $releasePath = Join-Path ([IO.Path]::GetTempPath()) `
            ('TroubleshootJS-gate-b-bound-receipt-' + [Guid]::NewGuid().ToString('N') + '.signal')
        $cleanupErrors = New-Object Collections.ArrayList
        try {
            $shell = (Get-Command powershell.exe -ErrorAction Stop).Source
            $command = "while (-not [IO.File]::Exists('$releasePath')) { Start-Sleep -Milliseconds 25 }"
            $child = Start-VerifierProcess $shell @(
                '-NoLogo', '-NoProfile', '-NonInteractive', '-ExecutionPolicy',
                'Bypass', '-Command', $command)
            $childWmi = @(& $saved['Get-VerifierProcessRecordsByIdWithFallback'] `
                ([int]$child.Id) 'bound-receipt protected-helper fixture')
            if ($childWmi.Count -ne 1 -or $null -eq $childWmi[0]) {
                Throw-VerifierInfrastructure 'bound-receipt protected-helper fixture had no unique current WMI record.'
            }
            $rootProcess = Get-Process -Id ([int]$PID) -ErrorAction Stop
            $rootWmi = @(& $saved['Get-VerifierProcessRecordsByIdWithFallback'] `
                ([int]$PID) 'bound-receipt protected-helper root')
            if ($rootWmi.Count -ne 1 -or $null -eq $rootWmi[0]) {
                Throw-VerifierInfrastructure 'bound-receipt protected-helper fixture could not retain its current root record.'
            }
            $rawChild = [pscustomobject]@{
                ProcessId = [int]$childWmi[0].ProcessId
                ParentProcessId = [int]$childWmi[0].ParentProcessId
                Name = if ($childWmi[0].PSObject.Properties['Name']) {
                    [string]$childWmi[0].Name
                } else { 'powershell.exe' }
                ExecutablePath = $shell
                # Null is the protected/uninspectable helper condition. The
                # receipt path must retain it for natural shutdown rather than
                # ignore it or authorize a stop by PID/PPID.
                CommandLine = $null
                CreationDate = $childWmi[0].CreationDate
            }
            if ([int]$rawChild.ParentProcessId -ne [int]$PID) {
                Throw-VerifierInfrastructure 'bound-receipt protected-helper fixture was not a direct child of the canary root.'
            }
            $rootRecord = [pscustomobject]@{
                Process = $rootProcess; ProcessId = [int]$PID
                ProcessStartTicks = [long](Get-VerifierProcessStartTicks $rootProcess)
                ParentProcessId = [int]$rootWmi[0].ParentProcessId
                ParentProcessStartTicks = 1L
                Name = if ($rootWmi[0].PSObject.Properties['Name']) {
                    [string]$rootWmi[0].Name
                } else { 'powershell.exe' }
                ExecutablePath = $shell
                CommandLine = [string]$rootWmi[0].CommandLine
            }
            if ([long]$rootRecord.ProcessStartTicks -le 0 -or
                    [String]::IsNullOrWhiteSpace($rootRecord.CommandLine)) {
                Throw-VerifierInfrastructure 'bound-receipt protected-helper fixture could not retain a complete live root identity.'
            }
            $state = [pscustomobject]@{
                RootPid = [int]$PID; ChildPid = [int]$rawChild.ProcessId
                ChildProcess = $child; RawChild = $rawChild; ChildPresent = $true
                IdentityMode = 'current'; HideDirectChild = $false
                ReusedRawChild = $null; EscapedGrandchild = $null
                FailStartCapture = $false
                StopCalls = 0
            }
            $state.ReusedRawChild = [pscustomobject]@{
                ProcessId = [int]$rawChild.ProcessId
                ParentProcessId = [int]$rawChild.ParentProcessId
                Name = [string]$rawChild.Name
                ExecutablePath = [string]$rawChild.ExecutablePath
                CommandLine = $null
                CreationDate = $rawChild.CreationDate.AddSeconds(1)
            }
            $state.EscapedGrandchild = [pscustomobject]@{
                ProcessId = 2147482999
                ParentProcessId = [int]$rawChild.ProcessId
                Name = 'markerless-grandchild.exe'
                ExecutablePath = $shell
                CommandLine = $null
                CreationDate = $rawChild.CreationDate.AddSeconds(2)
            }
            $script:GateBBoundReceiptState = $state
            Set-Item Function:\Get-VerifierProcessById -Force -Value {
                param($ProcessId)
                $s = $script:GateBBoundReceiptState
                if ([int]$ProcessId -eq [int]$s.ChildPid) {
                    if ($s.HideDirectChild) { return $null }
                    return $s.ChildProcess
                }
                return & $saved['Get-VerifierProcessById'] $ProcessId
            }
            Set-Item Function:\Get-VerifierProcessRecordsByIdWithFallback -Force -Value {
                param($ProcessId, $Purpose)
                $s = $script:GateBBoundReceiptState
                if ([int]$ProcessId -eq [int]$s.ChildPid) {
                    if ($s.IdentityMode -eq 'reused') { return @($s.ReusedRawChild) }
                    if ($s.ChildPresent) { return @($s.RawChild) }
                    return @()
                }
                return & $saved['Get-VerifierProcessRecordsByIdWithFallback'] $ProcessId $Purpose
            }
            Set-Item Function:\Get-VerifierProcessRecordsByParentWithFallback -Force -Value {
                param($ParentProcessId, $Purpose)
                $s = $script:GateBBoundReceiptState
                if ([int]$ParentProcessId -eq [int]$s.RootPid) {
                    if ($s.ChildPresent) { return @($s.RawChild) }
                    return @()
                }
                if ([int]$ParentProcessId -eq [int]$s.ChildPid) {
                    if ($s.HideDirectChild) { return @($s.EscapedGrandchild) }
                    return @()
                }
                return & $saved['Get-VerifierProcessRecordsByParentWithFallback'] $ParentProcessId $Purpose
            }
            Set-Item Function:\Get-VerifierProcessStartTicks -Force -Value {
                param($Process, $StartTimeAccessor = $null)
                $s = $script:GateBBoundReceiptState
                $processId = 0
                try { $processId = [int]$Process.Id } catch { $processId = 0 }
                if ($processId -eq [int]$s.ChildPid -and $s.FailStartCapture) {
                    $s.FailStartCapture = $false
                    [IO.File]::WriteAllText($releasePath, 'start-identity-race')
                    if (-not $s.ChildProcess.WaitForExit(15000)) {
                        Throw-VerifierInfrastructure 'bound-receipt protected-helper start-capture fixture did not naturally exit.'
                    }
                    $s.ChildPresent = $false
                    Throw-VerifierInfrastructure 'Injected protected-helper exit during retained start-identity capture.'
                }
                return & $saved['Get-VerifierProcessStartTicks'] $Process $StartTimeAccessor
            }
            Set-Item Function:\Stop-VerifierVerifiedProcessExactly -Force -Value {
                param($Context, $Record, $WaitMilliseconds)
                $script:GateBBoundReceiptState.StopCalls++
                Throw-VerifierInfrastructure 'bound-receipt protected-helper canary observed an unauthorized stop attempt.'
            }
            $scope = New-VerifierBrowserDrainScope
            $snapshot = @(
                $rawChild,
                [pscustomobject]@{
                    ProcessId = 2147483000; ParentProcessId = 1; Name = 'unrelated.exe'
                    ExecutablePath = $shell; CommandLine = $null
                    CreationDate = $rootWmi[0].CreationDate
                })
            $descendants = @(Get-VerifierBoundReceiptNaturalShutdownDescendants `
                $rootRecord $snapshot $scope)
            if ($descendants.Count -ne 1 -or -not $descendants[0].ObservationOnly -or
                    $descendants[0].CommandLine -cne '' -or
                    $descendants[0].Process.GetType() -ne [Diagnostics.Process]) {
                Throw-VerifierInfrastructure 'bound-receipt protected-helper was not retained as one observation-only natural-shutdown record.'
            }
            # The visible tuple is intentionally unchanged while CreationDate
            # differs. A PID reuse cannot be adopted just because name/path/
            # command data still resemble the original protected helper.
            $reusedRejected = $false
            $state.IdentityMode = 'reused'
            $reuseScope = New-VerifierBrowserDrainScope
            try {
                [void]@(Get-VerifierBoundReceiptNaturalShutdownDescendants `
                    $rootRecord $snapshot $reuseScope)
            } catch {
                $reusedRejected = Test-VerifierInfrastructureError $_
            } finally {
                $state.IdentityMode = 'current'
                if ($null -ne $reuseScope) {
                    try {
                        foreach ($failure in @(Dispose-VerifierBrowserDrainScope $reuseScope)) {
                            [void]$cleanupErrors.Add([string]$failure)
                        }
                    } catch { [void]$cleanupErrors.Add((Get-VerifierErrorMessage $_)) }
                    $reuseScope = $null
                }
            }
            if (-not $reusedRejected) {
                Throw-VerifierInfrastructure 'bound-receipt protected-helper census accepted a same-visible-field PID/start reuse.'
            }
            # A direct helper that vanished before native retention could have
            # spawned this otherwise markerless/null-command grandchild after
            # the snapshot. The census must retain the run rather than allow
            # Browser.close based on an unprovable PID absence.
            $escapedGrandchildRejected = $false
            $state.HideDirectChild = $true
            $escapedGrandchildScope = New-VerifierBrowserDrainScope
            try {
                [void]@(Get-VerifierBoundReceiptNaturalShutdownDescendants `
                    $rootRecord $snapshot $escapedGrandchildScope)
            } catch {
                $escapedGrandchildRejected = Test-VerifierInfrastructureError $_
            } finally {
                $state.HideDirectChild = $false
                if ($null -ne $escapedGrandchildScope) {
                    try {
                        foreach ($failure in @(Dispose-VerifierBrowserDrainScope $escapedGrandchildScope)) {
                            [void]$cleanupErrors.Add([string]$failure)
                        }
                    } catch { [void]$cleanupErrors.Add((Get-VerifierErrorMessage $_)) }
                    $escapedGrandchildScope = $null
                }
            }
            if (-not $escapedGrandchildRejected) {
                Throw-VerifierInfrastructure 'bound-receipt protected-helper census accepted a disappearing direct child with an escaped grandchild.'
            }
            # Exit after the initial live Refresh but before the first retained
            # start-identity read. The candidate has no snapshot descendants,
            # so only the existing parent-plus-two-absence proof may permit it
            # to disappear before native attestation.
            $state.FailStartCapture = $true
            $startCaptureScope = New-VerifierBrowserDrainScope
            $startCaptureDescendants = @(Get-VerifierBoundReceiptNaturalShutdownDescendants `
                $rootRecord $snapshot $startCaptureScope)
            $startCaptureExitAccepted = ($startCaptureDescendants.Count -eq 0 -and
                -not $state.ChildPresent -and [int]$state.StopCalls -eq 0)
            if (-not $startCaptureExitAccepted) {
                Throw-VerifierInfrastructure 'bound-receipt protected-helper start-identity race was not resolved through exact natural absence proof.'
            }
            # The retained direct helper has now naturally exited after its
            # original live attestation. Its leaf branch is safe to omit from
            # further census only after exact natural-exit and child-absence
            # proof; a snapshot child must still reject fail-closed.
            $leafNatural = Confirm-VerifierBoundReceiptObservedLeafNaturalExit `
                $scope $descendants[0] @{}
            $leafSnapshot = @{}
            $leafSnapshot[[int]$rawChild.ProcessId] = New-Object Collections.ArrayList
            [void]$leafSnapshot[[int]$rawChild.ProcessId].Add($state.EscapedGrandchild)
            try {
                [void](Confirm-VerifierBoundReceiptObservedLeafNaturalExit `
                    $scope $descendants[0] $leafSnapshot)
            } catch {
                $leafSnapshotRejected = Test-VerifierInfrastructureError $_
            }
            if (-not $leafSnapshotRejected) {
                Throw-VerifierInfrastructure 'bound-receipt exited protected-helper leaf accepted an unretained snapshot child branch.'
            }
            $natural = Get-VerifierBrowserDrainNaturalExitResult $scope $descendants[0]
            if ($null -eq $natural -or [int]$state.StopCalls -ne 0) {
                Throw-VerifierInfrastructure 'bound-receipt protected-helper natural exit was not proven without a stop attempt.'
            }
            return [pscustomobject]@{
                Retained = $true; Natural = $true; ReuseRejected = $reusedRejected
                EscapedGrandchildRejected = $escapedGrandchildRejected
                StartCaptureExitAccepted = [bool]$startCaptureExitAccepted
                LeafNatural = [bool]$leafNatural
                LeafSnapshotRejected = [bool]$leafSnapshotRejected
                Stops = [int]$state.StopCalls
            }
        } finally {
            if ($null -ne $child) {
                try {
                    $child.Refresh()
                    if (-not $child.HasExited) {
                        [IO.File]::WriteAllText($releasePath, 'cleanup-release')
                        [void]$child.WaitForExit(15000)
                    }
                } catch { [void]$cleanupErrors.Add((Get-VerifierErrorMessage $_)) }
            }
            if ($null -ne $scope) {
                try {
                    foreach ($failure in @(Dispose-VerifierBrowserDrainScope $scope)) {
                        [void]$cleanupErrors.Add([string]$failure)
                    }
                } catch { [void]$cleanupErrors.Add((Get-VerifierErrorMessage $_)) }
            }
            if ($null -ne $startCaptureScope) {
                try {
                    foreach ($failure in @(Dispose-VerifierBrowserDrainScope $startCaptureScope)) {
                        [void]$cleanupErrors.Add([string]$failure)
                    }
                } catch { [void]$cleanupErrors.Add((Get-VerifierErrorMessage $_)) }
            }
            if ($null -ne $child) {
                try { $child.Dispose() } catch { [void]$cleanupErrors.Add((Get-VerifierErrorMessage $_)) }
            }
            if (Test-Path -LiteralPath $releasePath) {
                try { Remove-Item -LiteralPath $releasePath -Force -ErrorAction Stop } catch {
                    [void]$cleanupErrors.Add((Get-VerifierErrorMessage $_))
                }
            }
            foreach ($name in $functionNames) {
                Set-Item -LiteralPath ('Function:\' + $name) -Value $saved[$name] -Force
            }
            Remove-Variable -Name GateBBoundReceiptState -Scope Script -Force `
                -ErrorAction SilentlyContinue
            if ($cleanupErrors.Count -gt 0) {
                Throw-VerifierInfrastructure ('bound-receipt protected-helper fixture cleanup failed: ' +
                    ($cleanupErrors -join '; '))
            }
        }
    }
    Assert-GateB ($missingReceipt.Typed -and $missingReceipt.Writes -eq 0) `
        'browser cleanup accepted a missing receipt or reached a legacy cleanup/write path'
    Assert-GateB ($protectedHelper.Retained -and $protectedHelper.Natural -and
        $protectedHelper.ReuseRejected -and $protectedHelper.EscapedGrandchildRejected -and
        $protectedHelper.StartCaptureExitAccepted -and
        $protectedHelper.LeafNatural -and $protectedHelper.LeafSnapshotRejected -and
        $protectedHelper.Stops -eq 0) `
        'bound receipt did not retain an uninspectable helper, prove an exited leaf, reject PID/start reuse and unretained child branches, and avoid stops'
    Write-Host 'PASS:bound receipt rejects missing authority, PID/start reuse, and unretained child branches while naturally proving null-command helpers and exited leaves'
}

function Invoke-GateBBrowserNaturalShutdownCanary() {
    # The TCP fixture exercises the selected ClientWebSocket implementation.
    # It is bounded, loopback-only, and owns its listener, client, and worker.
    if (-not ('GateBBrowserCloseServer' -as [type])) {
        Add-Type -TypeDefinition @'
using System;
using System.IO;
using System.Net;
using System.Net.Sockets;
using System.Security.Cryptography;
using System.Text;
using System.Threading;
using System.Threading.Tasks;
public sealed class GateBBrowserCloseServer : IDisposable {
    public readonly TcpListener Listener;
    public readonly int Port;
    public string Request;
    public Exception Failure;
    private readonly Task worker;
    private TcpClient client;
    private static byte[] ReadExact(Stream stream, int count) {
        byte[] bytes = new byte[count];
        for (int offset = 0; offset < count;) {
            int read = stream.Read(bytes, offset, count - offset);
            if (read == 0) throw new IOException("unexpected fixture EOF");
            offset += read;
        }
        return bytes;
    }
    public GateBBrowserCloseServer(string[] replies, int delay, bool drop, bool binary) {
        Listener = new TcpListener(IPAddress.Loopback, 0);
        Listener.Start();
        Port = ((IPEndPoint)Listener.LocalEndpoint).Port;
        worker = Task.Factory.StartNew(delegate {
            try {
                Task<TcpClient> accept = Listener.AcceptTcpClientAsync();
                if (!accept.Wait(4000)) throw new TimeoutException("fixture accept timeout");
                client = accept.GetAwaiter().GetResult();
                client.ReceiveTimeout = 4000;
                client.SendTimeout = 4000;
                NetworkStream stream = client.GetStream();
                StringBuilder header = new StringBuilder();
                while (!header.ToString().EndsWith("\r\n\r\n", StringComparison.Ordinal)) {
                    header.Append((char)ReadExact(stream, 1)[0]);
                    if (header.Length > 8192) throw new IOException("fixture header too long");
                }
                string key = null;
                foreach (string line in header.ToString().Split(new string[]{"\r\n"}, StringSplitOptions.None)) {
                    if (line.StartsWith("Sec-WebSocket-Key:", StringComparison.OrdinalIgnoreCase))
                        key = line.Substring(line.IndexOf(':') + 1).Trim();
                }
                if (key == null) throw new IOException("fixture omitted WebSocket key");
                string hash;
                using (SHA1 sha = SHA1.Create()) {
                    hash = Convert.ToBase64String(sha.ComputeHash(Encoding.ASCII.GetBytes(
                        key + "258EAFA5-E914-47DA-95CA-C5AB0DC85B11")));
                }
                byte[] upgrade = Encoding.ASCII.GetBytes("HTTP/1.1 101 Switching Protocols\r\n" +
                    "Upgrade: websocket\r\nConnection: Upgrade\r\nSec-WebSocket-Accept: " + hash + "\r\n\r\n");
                stream.Write(upgrade, 0, upgrade.Length);
                byte[] frame = ReadExact(stream, 2);
                int length = frame[1] & 127;
                if (frame[0] != 129 || (frame[1] & 128) == 0 || length >= 126)
                    throw new IOException("fixture received unexpected close command frame");
                byte[] mask = ReadExact(stream, 4);
                byte[] body = ReadExact(stream, length);
                for (int i = 0; i < length; i++) body[i] ^= mask[i % 4];
                Request = new UTF8Encoding(false, true).GetString(body);
                if (drop) return;
                if (delay > 0) Thread.Sleep(delay);
                foreach (string reply in replies) {
                    byte[] payload = Encoding.UTF8.GetBytes(reply);
                    if (payload.Length >= 126) throw new IOException("fixture reply too long");
                    stream.WriteByte((byte)(binary ? 130 : 129));
                    stream.WriteByte((byte)payload.Length);
                    stream.Write(payload, 0, payload.Length);
                }
            } catch (Exception error) { Failure = error; }
            finally { if (client != null) client.Close(); }
        });
    }
    public void Dispose() {
        Listener.Stop();
        if (client != null) client.Close();
        if (!worker.Wait(5000)) throw new TimeoutException("fixture worker cleanup timeout");
        worker.Dispose();
    }
}
'@ -ErrorAction Stop
    }
    $module = @(Get-Module VerifierIsolation | Select-Object -First 1)
    Assert-GateBInfrastructure ($module.Count -eq 1) 'natural shutdown module was unavailable'
    & $module[0] {
        function Assert-ShutdownFailure($Action, [string]$Name) {
            $typed = $false
            try { [void](& $Action) } catch { $typed = Test-VerifierInfrastructureError $_ }
            if (-not $typed) { Throw-VerifierInfrastructure "shutdown canary $Name did not reject with typed infrastructure failure." }
        }
        $target = 'a7dfa1a9-0f28-4ac8-b5a2-05edb2b8c289'
        $goodUrl = 'ws://127.0.0.1:51234/devtools/browser/' + $target
        $version = [pscustomobject]@{ webSocketDebuggerUrl = $goodUrl }
        if ((Get-VerifierBrowserShutdownEndpoint $version 51234) -cne $goodUrl) {
            Throw-VerifierInfrastructure 'shutdown canary rejected the exact browser endpoint.'
        }
        foreach ($badUrl in @($goodUrl.Replace('127.0.0.1', 'localhost'),
                $goodUrl.Replace('51234', '51235'), $goodUrl.Replace('/browser/', '/page/'),
                $goodUrl.Replace('ws:', 'wss:'), ($goodUrl + '?x=1'), ($goodUrl + '#x'),
                $goodUrl.Replace('/browser/', '/browser/%2f'),
                $goodUrl.Replace('127.0.0.1', 'u:p@127.0.0.1'),
                $goodUrl.Replace($target, [Guid]::Empty.ToString()), $null)) {
            $badVersion = [pscustomobject]@{ webSocketDebuggerUrl = $badUrl }
            Assert-ShutdownFailure { Get-VerifierBrowserShutdownEndpoint $badVersion 51234 } 'foreign-or-malformed-endpoint'
        }
        $badVersion = [pscustomobject]@{ webSocketDebuggerUrl = @($goodUrl) }
        Assert-ShutdownFailure { Get-VerifierBrowserShutdownEndpoint $badVersion 51234 } 'array-endpoint'
        foreach ($badPort in @(0, 65536, '51234', $null)) {
            Assert-ShutdownFailure { Get-VerifierBrowserShutdownEndpoint $version $badPort } 'malformed-port'
        }
        Assert-ShutdownFailure { Get-VerifierBrowserShutdownEndpoint $version @([int]51234) } 'array-port'
        foreach ($wire in @(
                [pscustomobject]@{ Name='success'; Replies=@('{"id":1,"result":{}}'); Pass=$true; Delay=0; Drop=$false; Binary=$false },
                [pscustomobject]@{ Name='notification-then-success'; Replies=@('{"method":"Target.targetDestroyed","params":{}}','{ "result" : {}, "id" : 1 }'); Pass=$true; Delay=0; Drop=$false; Binary=$false },
                [pscustomobject]@{ Name='wrong-id'; Replies=@('{"id":2,"result":{}}'); Pass=$false; Delay=0; Drop=$false; Binary=$false },
                [pscustomobject]@{ Name='string-id'; Replies=@('{"id":"1","result":{}}'); Pass=$false; Delay=0; Drop=$false; Binary=$false },
                [pscustomobject]@{ Name='duplicate-id'; Replies=@('{"id":2,"id":1,"result":{}}'); Pass=$false; Delay=0; Drop=$false; Binary=$false },
                [pscustomobject]@{ Name='cdp-error'; Replies=@('{"id":1,"error":{"code":-1}}'); Pass=$false; Delay=0; Drop=$false; Binary=$false },
                [pscustomobject]@{ Name='extra-field'; Replies=@('{"id":1,"result":{},"extra":true}'); Pass=$false; Delay=0; Drop=$false; Binary=$false },
                [pscustomobject]@{ Name='nonempty-result'; Replies=@('{"id":1,"result":{"x":1}}'); Pass=$false; Delay=0; Drop=$false; Binary=$false },
                [pscustomobject]@{ Name='array-result'; Replies=@('{"id":1,"result":[]}'); Pass=$false; Delay=0; Drop=$false; Binary=$false },
                [pscustomobject]@{ Name='array-reply'; Replies=@('[{"id":1,"result":{}}]'); Pass=$false; Delay=0; Drop=$false; Binary=$false },
                [pscustomobject]@{ Name='malformed-json'; Replies=@('{'); Pass=$false; Delay=0; Drop=$false; Binary=$false },
                [pscustomobject]@{ Name='binary'; Replies=@('{"id":1,"result":{}}'); Pass=$false; Delay=0; Drop=$false; Binary=$true },
                [pscustomobject]@{ Name='drop'; Replies=@(); Pass=$false; Delay=0; Drop=$true; Binary=$false },
                [pscustomobject]@{ Name='timeout'; Replies=@(); Pass=$false; Delay=2400; Drop=$false; Binary=$false })) {
            $server = $null
            $socket = $null
            $cleanupErrors = New-Object Collections.ArrayList
            try {
                $server = [GateBBrowserCloseServer]::new([string[]]$wire.Replies,
                    [int]$wire.Delay, [bool]$wire.Drop, [bool]$wire.Binary)
                $socket = Connect-VerifierCdpSocket (
                    'ws://127.0.0.1:' + $server.Port + '/devtools/browser/' + $target) ([DateTime]::UtcNow.AddSeconds(3))
                $budget = [Diagnostics.Stopwatch]::StartNew()
                $ticks = [long]([Diagnostics.Stopwatch]::Frequency * 5)
                $action = { Send-VerifierBrowserCloseAndReadAcknowledgement $socket $budget $ticks }
                if ($wire.Pass) { & $action } else { Assert-ShutdownFailure $action $wire.Name }
            } finally {
                if ($null -ne $socket) {
                    try { $socket.Abort() } catch { [void]$cleanupErrors.Add((Get-VerifierErrorMessage $_)) }
                    try { $socket.Dispose() } catch { [void]$cleanupErrors.Add((Get-VerifierErrorMessage $_)) }
                }
                if ($null -ne $server) {
                    try { $server.Dispose() } catch { [void]$cleanupErrors.Add((Get-VerifierErrorMessage $_)) }
                    if ($null -ne $server.Failure) { [void]$cleanupErrors.Add($server.Failure.Message) }
                    if ($server.Request -cne '{"id":1,"method":"Browser.close"}') {
                        [void]$cleanupErrors.Add('fixture did not receive the exact close request')
                    }
                    try {
                        $inspection = Get-VerifierLoopbackListenerRecords $server.Port
                        if (-not $inspection.Success -or -not $inspection.Known -or $inspection.HasListeners) {
                            [void]$cleanupErrors.Add('fixture listener absence was not proven')
                        }
                    } catch { [void]$cleanupErrors.Add((Get-VerifierErrorMessage $_)) }
                }
                if ($cleanupErrors.Count -gt 0) {
                    Throw-VerifierInfrastructure ('shutdown wire fixture cleanup: ' + ($cleanupErrors -join '; '))
                }
            }
        }
    }
    Write-Host 'PASS:natural browser shutdown exact endpoint and actual ClientWebSocket acknowledgement/negative transport canaries'

    # These are explicit orchestration fault injections. Native retained-handle
    # proof is covered by the separate real-process drain canary; actual Edge
    # ancestry and shutdown remain a required live prerequisite after review.
    & $module[0] {
        $functionNames = @('Assert-VerifierDurableBrowserSession',
            'Get-VerifierPortLeaseBoundOwnershipProof', 'Invoke-RestMethod',
            'Connect-VerifierCdpSocket', 'Get-VerifierBrowserOwnershipSnapshot',
            'Get-VerifierBrowserRootRecordFromSnapshot', 'Get-VerifierCurrentOwnedProcess',
            'New-VerifierBrowserDrainAttestation', 'Get-VerifierDescendantProcessRecords',
            'Add-VerifierBrowserDrainHandle', 'Send-VerifierBrowserCloseAndReadAcknowledgement',
            'Get-VerifierBrowserDrainNaturalExitResult', 'Assert-VerifierNoResidualBrowserDescendants',
            'Dispose-VerifierBrowserDrainScope', 'Stop-VerifierBrowserProcessTreeToFixedPoint')
        $originals = @{}
        foreach ($name in $functionNames) {
            $found = Get-Command $name -CommandType Function -ErrorAction SilentlyContinue
            $originals[$name] = if ($null -ne $found) { $found.ScriptBlock } else { $null }
        }
        $script:GateBShutdownOriginalDispose = $originals['Dispose-VerifierBrowserDrainScope']
        $script:GateBShutdownCalls = $null
        $script:GateBShutdownMode = ''
        $script:GateBShutdownSnapshotCount = 0
        $script:GateBShutdownChildCount = 0
        $script:GateBShutdownListenerCount = 0
        $script:GateBShutdownFakeSocket = $null
        $script:GateBShutdownRecord = $null
        $testAttemptKeys = New-Object Collections.ArrayList
        try {
            Set-Item Function:\Assert-VerifierDurableBrowserSession -Value { param($SessionRecord,$Context,$Label) }
            Set-Item Function:\Invoke-RestMethod -Value {
                param($Uri,$TimeoutSec)
                return [pscustomobject]@{ webSocketDebuggerUrl='ws://127.0.0.1:51234/devtools/browser/a7dfa1a9-0f28-4ac8-b5a2-05edb2b8c289' }
            }
            Set-Item Function:\Get-VerifierPortLeaseBoundOwnershipProof -Value {
                param($Context,$Lease,$ProcessId,$Start,$Session,$Preview)
                $script:GateBShutdownListenerCount++
                [void]$script:GateBShutdownCalls.Add('listener')
                if ($script:GateBShutdownMode -eq 'listener-change' -and $script:GateBShutdownListenerCount -eq 3) {
                    Throw-VerifierInfrastructure 'injected current listener owner change'
                }
                if ($script:GateBShutdownMode -eq 'preclose-deadline' -and $script:GateBShutdownListenerCount -eq 3) {
                    Start-Sleep -Milliseconds 15020
                }
            }
            Set-Item Function:\Connect-VerifierCdpSocket -Value {
                param($Uri,$Deadline)
                return $script:GateBShutdownFakeSocket
            }
            Set-Item Function:\Get-VerifierBrowserOwnershipSnapshot -Value {
                param($BrowserPath,$Profile,$RunId,$RepositoryIdentity,$CdpPort)
                $script:GateBShutdownSnapshotCount++
                [void]$script:GateBShutdownCalls.Add('snapshot')
                return @($script:GateBShutdownRecord)
            }
            Set-Item Function:\Get-VerifierBrowserRootRecordFromSnapshot -Value {
                param($OwnerRoot,$Snapshot)
                if ($script:GateBShutdownMode -eq 'missing-root') { Throw-VerifierInfrastructure 'injected missing initial root' }
                return $script:GateBShutdownRecord
            }
            Set-Item Function:\Get-VerifierCurrentOwnedProcess -Value {
                param($OwnerRoot,$Recorded,[switch]$Root)
                [void]$script:GateBShutdownCalls.Add('current-root')
                return [pscustomobject]@{ Record=$script:GateBShutdownRecord; Process=[object]::new() }
            }
            Set-Item Function:\New-VerifierBrowserDrainAttestation -Value { param($Scope,$Record,$Process) return [object]::new() }
            Set-Item Function:\Add-VerifierBrowserDrainHandle -Value { param($Scope,$Process) }
            Set-Item Function:\Get-VerifierDescendantProcessRecords -Value {
                param($OwnerRoot,$Snapshot,$Scope)
                $script:GateBShutdownChildCount++
                [void]$script:GateBShutdownCalls.Add('children')
                if ($script:GateBShutdownMode -eq 'missing-child') { Throw-VerifierInfrastructure 'injected unattested missing child' }
                $child = [pscustomobject]@{
                    ProcessId=202; ProcessStartTicks=200L; ParentProcessId=101;
                    ParentProcessStartTicks=100L; Name='fixture.exe'; ExecutablePath='fixture.exe'; CommandLine='fixture child'
                }
                if ($script:GateBShutdownMode -eq 'changed-child' -and $script:GateBShutdownChildCount -gt 1) {
                    $child.ProcessStartTicks++
                }
                $child
                if ($script:GateBShutdownMode -eq 'new-child' -and $script:GateBShutdownChildCount -gt 1) {
                    $newChild = $child | Select-Object *
                    $newChild.ProcessId = 203
                    $newChild
                }
            }
            Set-Item Function:\Send-VerifierBrowserCloseAndReadAcknowledgement -Value {
                param($Socket,$Budget,$BudgetTicks)
                [void]$script:GateBShutdownCalls.Add('send')
                if ($script:GateBShutdownChildCount -lt 2 -or $script:GateBShutdownListenerCount -ne 3) {
                    Throw-VerifierInfrastructure 'orchestration omitted complete graph/listener refresh before close'
                }
                if ($script:GateBShutdownMode -eq 'dropped-ack') { Throw-VerifierInfrastructure 'injected dropped acknowledgement' }
            }
            Set-Item Function:\Get-VerifierBrowserDrainNaturalExitResult -Value {
                param($Scope,$Record)
                [void]$script:GateBShutdownCalls.Add('natural:' + $Record.ProcessId)
                if ($script:GateBShutdownMode -eq 'unproven-exit') { Throw-VerifierInfrastructure 'injected retained exit proof failure' }
                return [pscustomobject]@{ TerminationProven=$true; NaturalExit=$true }
            }
            Set-Item Function:\Assert-VerifierNoResidualBrowserDescendants -Value {
                param($OwnerRoot,$RootRecord,$Children,$Snapshot)
                [void]$script:GateBShutdownCalls.Add('final-graph')
                if ($script:GateBShutdownMode -eq 'late-unknown') { Throw-VerifierInfrastructure 'injected unknown late descendant' }
            }
            Set-Item Function:\Stop-VerifierBrowserProcessTreeToFixedPoint -Value {
                param($Context,$OwnerRoot,$Wait)
                Throw-VerifierInfrastructure 'unexpected exact-stop fallback during natural shutdown'
            }
            Set-Item Function:\Dispose-VerifierBrowserDrainScope -Value {
                param($Scope)
                & $script:GateBShutdownOriginalDispose $Scope
                if ($script:GateBShutdownMode -eq 'disposal-failure') { 'injected handle disposal failure' }
                if ($script:GateBShutdownMode -eq 'final-deadline') { Start-Sleep -Milliseconds 15020 }
            }
            foreach ($mode in @('success','new-child','missing-root','missing-child','changed-child',
                    'listener-change','dropped-ack','unproven-exit','late-unknown','disposal-failure',
                    'preclose-deadline','final-deadline')) {
                $script:GateBShutdownMode = $mode
                $script:GateBShutdownCalls = New-Object Collections.ArrayList
                $script:GateBShutdownSnapshotCount = 0
                $script:GateBShutdownChildCount = 0
                $script:GateBShutdownListenerCount = 0
                $script:GateBShutdownFakeSocket = [pscustomobject]@{ Aborted=$false; Disposed=$false }
                $script:GateBShutdownFakeSocket | Add-Member ScriptMethod Abort { $this.Aborted=$true }
                $script:GateBShutdownFakeSocket | Add-Member ScriptMethod Dispose { $this.Disposed=$true }
                $script:GateBShutdownRecord = [pscustomobject]@{
                    ProcessId=101; ProcessStartTicks=100L; ParentProcessId=99; ParentProcessStartTicks=90L;
                    Name='fixture.exe'; ExecutablePath='fixture.exe'; CommandLine='fixture root'
                }
                $session = [pscustomobject]@{
                    RunId=[Guid]::NewGuid().ToString('N'); RouteId=[Guid]::NewGuid().ToString('N');
                    ProcessId=101; ProcessStartTicks=100L; ProcessParentProcessId=99; ProcessParentProcessStartTicks=90L;
                    ProcessCommandLine='fixture root'; BrowserPath='fixture.exe'; Profile='fixture-profile';
                    CdpPort=51234; RepositoryIdentity='fixture-repository'; Lease=[object]::new(); Runtime=[pscustomobject]@{}
                }
                $owner = $session | Select-Object ProcessId,ProcessStartTicks,ProcessParentProcessId,
                    ProcessParentProcessStartTicks,ProcessCommandLine,BrowserPath,Profile,CdpPort,RunId,RepositoryIdentity
                $key = Get-VerifierBrowserCloseAttemptKey $session
                [void]$testAttemptKeys.Add($key)
                if ($mode -eq 'success') {
                    foreach ($field in @('ProcessId','ProcessStartTicks','ProcessParentProcessId','ProcessParentProcessStartTicks',
                            'ProcessCommandLine','BrowserPath','Profile','CdpPort','RunId','RepositoryIdentity')) {
                        $badOwner = $owner | Select-Object *
                        $badOwner.$field = if ($badOwner.$field -is [string]) { 'foreign' } else { 1 }
                        $rejected = $false
                        try { Assert-VerifierBrowserShutdownOwner $session $badOwner } catch { $rejected=Test-VerifierInfrastructureError $_ }
                        if (-not $rejected) { Throw-VerifierInfrastructure "shutdown owner accepted changed $field" }
                    }
                    foreach ($badFlag in @('false', 0, $null)) {
                        $session.Runtime | Add-Member NoteProperty GracefulCloseAttempted $badFlag -Force
                        $rejected = $false
                        try { Assert-VerifierBrowserCloseNotAttempted $session } catch { $rejected=Test-VerifierInfrastructureError $_ }
                        if (-not $rejected) { Throw-VerifierInfrastructure 'shutdown accepted malformed attempt flag' }
                    }
                    $session.Runtime.PSObject.Properties.Remove('GracefulCloseAttempted')
                }
                $proof = $null
                $typed = $false
                try { $proof = Close-VerifierBrowserSessionNaturally ([object]::new()) $session $owner }
                catch { $typed = Test-VerifierInfrastructureError $_ }
                $positive = $mode -in @('success','new-child')
                if ($positive) {
                    $expectedChildren = if ($mode -eq 'new-child') { 2 } else { 1 }
                    if ($typed -or $null -eq $proof -or -not $proof.ProcessTerminationProven -or
                            -not $proof.NaturalShutdownProven -or $proof.AttestedDescendantCount -ne $expectedChildren -or
                            $script:GateBShutdownCalls[-1] -cne 'final-graph' -or
                            @($script:GateBShutdownCalls | Where-Object { $_ -like 'natural:*' }).Count -ne (1+$expectedChildren)) {
                        Throw-VerifierInfrastructure "shutdown orchestration positive $mode omitted proof or graph refresh."
                    }
                } elseif (-not $typed -or $null -ne $proof) {
                    Throw-VerifierInfrastructure "shutdown orchestration negative $mode was accepted or not typed infrastructure."
                }
                $sent = $script:GateBShutdownCalls.Contains('send')
                if ($mode -in @('missing-root','missing-child','changed-child','listener-change','preclose-deadline') -and $sent) {
                    Throw-VerifierInfrastructure "shutdown $mode sent a close command after a failed precondition."
                }
                if (-not $script:GateBShutdownFakeSocket.Aborted -or -not $script:GateBShutdownFakeSocket.Disposed) {
                    Throw-VerifierInfrastructure "shutdown $mode did not dispose its private socket."
                }
                if ($sent) {
                    # A caller cannot erase the object flag or copy the session
                    # to escape the module's private attempt registry.
                    $session.Runtime.PSObject.Properties.Remove('GracefulCloseAttempted')
                    $copy = $session | Select-Object *
                    $rejected = $false
                    try { Assert-VerifierBrowserCloseNotAttempted $copy } catch { $rejected=Test-VerifierInfrastructureError $_ }
                    if (-not $rejected) { Throw-VerifierInfrastructure 'copied close attempt selected retry/force-stop fallback' }
                    $copy.RouteId = [Guid]::NewGuid().ToString('N')
                    $copy.RunId = [Guid]::NewGuid().ToString('N')
                    $rejected = $false
                    try { Assert-VerifierBrowserCloseNotAttempted $copy } catch { $rejected=Test-VerifierInfrastructureError $_ }
                    if (-not $rejected) { Throw-VerifierInfrastructure 'relabeled close attempt selected retry/force-stop fallback' }
                }
                # All cases deliberately use the same synthetic PID/start;
                # remove only this fixture's marker between independent cases.
                $script:VerifierBrowserCloseAttempts.Remove($key)
            }
        } finally {
            foreach ($name in $functionNames) {
                if ($null -eq $originals[$name]) { Remove-Item -LiteralPath ('Function:\' + $name) -ErrorAction SilentlyContinue }
                else { Set-Item -LiteralPath ('Function:\' + $name) -Value $originals[$name] -Force }
            }
            foreach ($key in $testAttemptKeys) { $script:VerifierBrowserCloseAttempts.Remove($key) }
            foreach ($name in @('GateBShutdownOriginalDispose','GateBShutdownCalls','GateBShutdownMode',
                    'GateBShutdownSnapshotCount','GateBShutdownChildCount','GateBShutdownListenerCount',
                    'GateBShutdownFakeSocket','GateBShutdownRecord')) {
                Remove-Variable -Scope Script -Name $name -ErrorAction SilentlyContinue
            }
        }
    }
    Write-Host 'PASS:natural shutdown orchestration identity, graph refresh, deadline, disposal, and no-retry fault injections'
}

function Invoke-GateBListenerAuthorizationRetryCanary() {
    $module = @(Get-Module VerifierIsolation | Select-Object -First 1)
    if ($module.Count -ne 1) {
        Throw-GateBInfrastructure 'VerifierIsolation module was unavailable for listener authorization retry canary.'
    }
    $owner = [pscustomobject]@{
        ProcessId = 9000; ProcessStartTicks = 700L
        Profile = 'C:\Temp\gate-b-listener-retry-profile'
        BrowserPath = 'C:\Program Files\Edge\Application\msedge.exe'
        RunId = 'gate-b-listener-retry-run'; RepositoryIdentity = 'gate-b-listener-retry-worktree'
        CdpPort = 45124
    }
    $listener = [pscustomobject]@{
        LocalAddress = '127.0.0.1'; Port = 45124
        ProcessId = 9001; ProcessStartTicks = 701L
        ListenerOwnerKind = 'user-process'
        ListenerOwnerProof = 'diagnostics-process-start-v1'
        ListenerOwnerEvidence = 'system-diagnostics-process-starttime'
        Source = 'Get-NetTCPConnection'
    }
    $inspection = [pscustomobject]@{
        Success = $true; Known = $true; HasListeners = $true
        Listeners = @($listener)
        ListenerOwnerKind = 'user-process'
        ListenerOwnerProof = 'diagnostics-process-start-v1'
        ListenerOwnerEvidence = 'system-diagnostics-process-starttime'
        Source = 'Get-NetTCPConnection'; Error = ''
    }
    $context = [pscustomobject]@{}
    $probe = & $module[0] {
        param($ownerValue, $inspectionValue, $contextValue)
        $oldSchema = (Get-Command Test-VerifierListenerInspectionSchema `
            -CommandType Function -ErrorAction Stop).ScriptBlock
        $oldLoopback = (Get-Command Get-VerifierLoopbackListenerRecords `
            -CommandType Function -ErrorAction Stop).ScriptBlock
        $oldSnapshot = (Get-Command Get-VerifierBrowserOwnershipSnapshot `
            -CommandType Function -ErrorAction Stop).ScriptBlock
        $oldCurrent = (Get-Command Get-VerifierCurrentProcessRecordById `
            -CommandType Function -ErrorAction Stop).ScriptBlock
        $oldBelongs = (Get-Command Test-VerifierListenerBelongsToOwner `
            -CommandType Function -ErrorAction Stop).ScriptBlock
        $oldProcessIdentity = (Get-Command Test-VerifierProcessIdentity `
            -CommandType Function -ErrorAction Stop).ScriptBlock
        $oldPreviewIdentity = (Get-Command Test-VerifierPreviewProcessIdentity `
            -CommandType Function -ErrorAction Stop).ScriptBlock
        try {
            $freshInspection = $inspectionValue | Select-Object *
            $freshInspection.Listeners = @($inspectionValue.Listeners)
            $changedInspection = $inspectionValue | Select-Object *
            $changedListener = $inspectionValue.Listeners[0] | Select-Object *
            $changedListener.ProcessStartTicks = 702L
            $changedInspection.Listeners = @($changedListener)
            $snapshotValue = @([pscustomobject]@{
                ProcessId = 9000; ParentProcessId = 1
                CommandLine = 'msedge.exe --owned'
            })
            $cases = @(
                [pscustomobject]@{ Name = 'transient-missing'; Mode = 'transient'; Fresh = $freshInspection }
                [pscustomobject]@{ Name = 'persistent-missing'; Mode = 'persistent-missing'; Fresh = $freshInspection }
                [pscustomobject]@{ Name = 'foreign'; Mode = 'foreign'; Fresh = $freshInspection }
                [pscustomobject]@{ Name = 'inaccessible'; Mode = 'inaccessible'; Fresh = $freshInspection }
                [pscustomobject]@{ Name = 'listener-start-changed'; Mode = 'listener-start-changed'; Fresh = $changedInspection }
                [pscustomobject]@{ Name = 'delayed-proof'; Mode = 'delayed-proof'; Fresh = $freshInspection }
                [pscustomobject]@{ Name = 'delayed-second-view'; Mode = 'delayed-second-view'; Fresh = $freshInspection }
                [pscustomobject]@{ Name = 'late-second-view'; Mode = 'late-second-view'; Fresh = $freshInspection }
            )
            $results = New-Object Collections.ArrayList
            foreach ($case in $cases) {
                $state = [pscustomobject]@{
                    Mode = $case.Mode; Fresh = $case.Fresh; Snapshot = $snapshotValue
                    ListenerQueries = 0; SnapshotQueries = 0; CurrentQueries = 0
                    BelongsQueries = 0; SecondViewQueries = 0
                }
                $script:GateBListenerRetryState = $state
                Set-Item Function:\Test-VerifierListenerInspectionSchema -Force -Value {
                    param($candidateInspection, $candidateContext, $candidateOwner)
                    return $true
                }
                Set-Item Function:\Get-VerifierLoopbackListenerRecords -Force -Value {
                    $state = $script:GateBListenerRetryState
                    $state.ListenerQueries++
                    return $state.Fresh
                }
                Set-Item Function:\Get-VerifierBrowserOwnershipSnapshot -Force -Value {
                    $state = $script:GateBListenerRetryState
                    $state.SnapshotQueries++
                    if ($state.Mode -eq 'delayed-proof') {
                        Start-Sleep -Milliseconds 600
                    }
                    if ($state.Mode -eq 'inaccessible') {
                        Throw-VerifierInfrastructure 'synthetic inaccessible ownership snapshot'
                    }
                    if ($state.Mode -eq 'persistent-missing') {
                        Throw-VerifierInfrastructureMissingProcess `
                            'synthetic ownership snapshot missing candidate' 9003
                    }
                    return $state.Snapshot
                }
                Set-Item Function:\Get-VerifierCurrentProcessRecordById -Force -Value {
                    param($processId)
                    $state = $script:GateBListenerRetryState
                    $state.CurrentQueries++
                    if ([int]$processId -eq 9003) {
                        $state.SecondViewQueries++
                        if ($state.Mode -eq 'delayed-second-view') {
                            Start-Sleep -Milliseconds 600
                        }
                        return $null
                    }
                    if ([int]$processId -eq 9000) {
                        return [pscustomobject]@{
                            ProcessId = 9000; ProcessStartTicks = 700L
                        }
                    }
                    if ([int]$processId -eq 9001) {
                        return [pscustomobject]@{
                            ProcessId = 9001; ProcessStartTicks = 701L
                        }
                    }
                    return $null
                }
                Set-Item Function:\Test-VerifierProcessIdentity -Force -Value {
                    return $true
                }
                Set-Item Function:\Test-VerifierPreviewProcessIdentity -Force -Value {
                    return $true
                }
                Set-Item Function:\Test-VerifierListenerBelongsToOwner -Force -Value {
                    $state = $script:GateBListenerRetryState
                    $state.BelongsQueries++
                    if ($state.Mode -eq 'foreign') { return $false }
                    if (($state.Mode -eq 'transient' -or
                            $state.Mode -eq 'delayed-proof' -or
                            $state.Mode -eq 'listener-start-changed' -or
                            $state.Mode -eq 'delayed-second-view') -and
                            $state.BelongsQueries -eq 1) {
                        Throw-VerifierInfrastructureMissingProcess `
                            'synthetic descendant disappeared during ownership proof' 9003
                    }
                    if ($state.Mode -eq 'persistent-missing') {
                        Throw-VerifierInfrastructureMissingProcess `
                            'synthetic descendant remained absent during ownership proof' 9003
                    }
                    if ($state.Mode -eq 'late-second-view') {
                        Start-Sleep -Milliseconds 600
                        Throw-VerifierInfrastructureMissingProcess `
                            'synthetic descendant proof became stale before its second view' 9003
                    }
                    return $true
                }
                $accepted = $false
                try {
                    $accepted = [bool](Test-VerifierLiveListenerInspectionAuthorization `
                        $inspectionValue $contextValue $ownerValue 9000 700L)
                } catch { $accepted = $false }
                [void]$results.Add([pscustomobject]@{
                    Name = $case.Name; Accepted = $accepted
                    ListenerQueries = [int]$state.ListenerQueries
                    SnapshotQueries = [int]$state.SnapshotQueries
                    CurrentQueries = [int]$state.CurrentQueries
                    BelongsQueries = [int]$state.BelongsQueries
                    SecondViewQueries = [int]$state.SecondViewQueries
                })
            }
            return @($results)
        } finally {
            Set-Item Function:\Test-VerifierListenerInspectionSchema -Force -Value $oldSchema
            Set-Item Function:\Get-VerifierLoopbackListenerRecords -Force -Value $oldLoopback
            Set-Item Function:\Get-VerifierBrowserOwnershipSnapshot -Force -Value $oldSnapshot
            Set-Item Function:\Get-VerifierCurrentProcessRecordById -Force -Value $oldCurrent
            Set-Item Function:\Test-VerifierListenerBelongsToOwner -Force -Value $oldBelongs
            Set-Item Function:\Test-VerifierProcessIdentity -Force -Value $oldProcessIdentity
            Set-Item Function:\Test-VerifierPreviewProcessIdentity -Force -Value $oldPreviewIdentity
            Remove-Variable -Name GateBListenerRetryState -Scope Script -Force `
                -ErrorAction SilentlyContinue
        }
    } $owner $inspection $context
    $transient = @($probe | Where-Object Name -eq 'transient-missing')
    $persistent = @($probe | Where-Object Name -eq 'persistent-missing')
    $foreign = @($probe | Where-Object Name -eq 'foreign')
    $inaccessible = @($probe | Where-Object Name -eq 'inaccessible')
    $changed = @($probe | Where-Object Name -eq 'listener-start-changed')
    $delayed = @($probe | Where-Object Name -eq 'delayed-proof')
    $delayedSecondView = @($probe | Where-Object Name -eq 'delayed-second-view')
    Assert-GateB ($transient.Count -eq 1 -and $transient[0].Accepted -and
        [int]$transient[0].ListenerQueries -eq 1 -and
        [int]$transient[0].SnapshotQueries -eq 2 -and
        [int]$transient[0].BelongsQueries -eq 2) `
        'transient missing descendant was not recovered by a fresh full listener/ownership proof'
    Assert-GateB ($persistent.Count -eq 1 -and -not $persistent[0].Accepted -and
        [int]$persistent[0].ListenerQueries -eq 2 -and
        [int]$persistent[0].SnapshotQueries -eq 3) `
        'persistent missing descendant did not remain bounded infrastructure failure'
    foreach ($negative in @($foreign, $inaccessible)) {
        Assert-GateB ($negative.Count -eq 1 -and -not $negative[0].Accepted -and
            [int]$negative[0].ListenerQueries -eq 0 -and
            [int]$negative[0].SnapshotQueries -eq 1) `
            'foreign or inaccessible listener ownership was retried or accepted'
    }
    Assert-GateB ($changed.Count -eq 1 -and -not $changed[0].Accepted -and
        [int]$changed[0].ListenerQueries -eq 1 -and
        [int]$changed[0].SnapshotQueries -eq 1) `
        'listener PID/start change was accepted after a retry'
    Assert-GateB ($delayed.Count -eq 1 -and -not $delayed[0].Accepted -and
        [int]$delayed[0].ListenerQueries -eq 0 -and
        [int]$delayed[0].SnapshotQueries -eq 1 -and
        [int]$delayed[0].CurrentQueries -eq 2 -and
        [int]$delayed[0].BelongsQueries -eq 0) `
        'delayed initial ownership snapshot was accepted after its strict deadline'
    Assert-GateB ($delayedSecondView.Count -eq 1 -and
        -not $delayedSecondView[0].Accepted -and
        [int]$delayedSecondView[0].ListenerQueries -eq 0 -and
        [int]$delayedSecondView[0].SnapshotQueries -eq 1 -and
        [int]$delayedSecondView[0].CurrentQueries -eq 3 -and
        [int]$delayedSecondView[0].BelongsQueries -eq 1 -and
        [int]$delayedSecondView[0].SecondViewQueries -eq 1) `
        'delayed missing-PID absence proof was accepted after the strict deadline'
    $lateSecondView = @($probe | Where-Object Name -eq 'late-second-view')
    Assert-GateB ($lateSecondView.Count -eq 1 -and -not $lateSecondView[0].Accepted -and
        [int]$lateSecondView[0].ListenerQueries -eq 0 -and
        [int]$lateSecondView[0].SnapshotQueries -eq 1 -and
        [int]$lateSecondView[0].CurrentQueries -eq 2 -and
        [int]$lateSecondView[0].BelongsQueries -eq 1 -and
        [int]$lateSecondView[0].SecondViewQueries -eq 0) `
        'listener authorization accepted a late ownership proof after its strict deadline'
    Write-Host 'PASS:listener authorization retries only a proven disappeared candidate and repeats exact listener/root/ownership proof'
}

function Invoke-GateBBrowserRootListenerFastPathCanary() {
    $module = @(Get-Module VerifierIsolation | Select-Object -First 1)
    if ($module.Count -ne 1) {
        Throw-GateBInfrastructure 'VerifierIsolation module was unavailable for browser-root listener fast-path canary.'
    }
    $currentProcess = Get-Process -Id $PID -ErrorAction Stop
    $currentStart = [long](Get-VerifierProcessStartTicks $currentProcess)
    $browserPath = [string]$currentProcess.Path
    if ([String]::IsNullOrWhiteSpace($browserPath)) {
        $browserPath = [string](Get-Command powershell.exe -ErrorAction Stop).Source
    }
    $profile = Join-Path ([IO.Path]::GetTempPath()) 'gate-b-browser-fast-profile'
    $owner = [pscustomobject]@{
        ProcessId = [int]$PID; ProcessStartTicks = $currentStart
        ProcessParentProcessId = 9200; ProcessParentProcessStartTicks = 9201L
        ProcessCommandLine = 'synthetic browser owner command line'
        Profile = $profile; BrowserPath = $browserPath
        RunId = 'gate-b-browser-fast-run'
        RepositoryIdentity = 'gate-b-browser-fast-worktree'; CdpPort = 45126
    }
    $listener = [pscustomobject]@{
        LocalAddress = '127.0.0.1'; Port = 45126
        ProcessId = [int]$PID; ProcessStartTicks = $currentStart
        ListenerOwnerKind = 'user-process'
        ListenerOwnerProof = 'diagnostics-process-start-v1'
        ListenerOwnerEvidence = 'system-diagnostics-process-starttime'
        Source = 'Get-NetTCPConnection'
    }
    $inspection = [pscustomobject]@{
        Success = $true; Known = $true; HasListeners = $true
        Listeners = @($listener); ListenerOwnerKind = 'user-process'
        ListenerOwnerProof = 'diagnostics-process-start-v1'
        ListenerOwnerEvidence = 'system-diagnostics-process-starttime'
        Source = 'Get-NetTCPConnection'; Error = ''
    }
    # The setter is a durable-state consumer and therefore receives the
    # smallest real run identity that its preflight boundary requires.  The
    # durable validators are replaced with no-ops inside the synthetic probe;
    # the listener authorization itself remains the production implementation.
    $context = [pscustomobject]@{
        WorktreeRoot = 'C:\Temp\gate-b-browser-fast-worktree'
        RunId = 'gate-b-browser-fast-run'
        RepositoryIdentity = 'gate-b-browser-fast-worktree'
    }
    $lease = [pscustomobject]@{ Port = 45126 }
    $probe = & $module[0] {
        param($ownerValue, $inspectionValue, $contextValue, $leaseValue)
        $saved = @{}
        foreach ($name in @(
                'Test-VerifierListenerInspectionSchema',
                'Test-VerifierListenerRecordSchema',
                'Get-VerifierLoopbackListenerRecords',
                'Get-VerifierBrowserOwnershipSnapshot',
                'Get-VerifierCurrentProcessRecordById',
                'Get-VerifierCurrentParentStartTicks',
                'Get-VerifierProcessRecordsByIdWithFallback',
                'Test-VerifierListenerBelongsToOwner',
                'Get-VerifierProcessStartTicks',
                'Assert-VerifierDurableLeaseRecord',
                'Assert-VerifierDurableServerLease')) {
            $saved[$name] = (Get-Command $name -CommandType Function `
                -ErrorAction Stop).ScriptBlock
        }
        try {
            $script:GateBBrowserFastOriginalBelongs =
                $saved['Test-VerifierListenerBelongsToOwner']
            $script:GateBBrowserFastOriginalStartTicks =
                $saved['Get-VerifierProcessStartTicks']
            $results = New-Object Collections.ArrayList
            $setterResults = New-Object Collections.ArrayList
            Set-Item Function:\Assert-VerifierDurableLeaseRecord -Force -Value {
                param($lease, $context, $label, [switch]$serialized)
                return
            }
            Set-Item Function:\Assert-VerifierDurableServerLease -Force -Value {
                param($context, $label, [switch]$allowMissing)
                return
            }
            Set-Item Function:\Get-VerifierProcessStartTicks -Force -Value {
                param($process, $startTimeAccessor)
                $s = $script:GateBBrowserFastState
                if ($null -ne $s -and $s.Mode -eq 'wrong-start') {
                    return [long]$s.Owner.ProcessStartTicks + 1L
                }
                if ($null -eq $startTimeAccessor) {
                    return [long](& $script:GateBBrowserFastOriginalStartTicks $process)
                }
                return [long](& $script:GateBBrowserFastOriginalStartTicks $process $startTimeAccessor)
            }
            foreach ($mode in @(
                    'valid-fast', 'wrong-start', 'wrong-executable',
                    'wrong-parent', 'wrong-markers', 'late-proof', 'descendant',
                    'malformed-parent', 'malformed-parent-start',
                    'malformed-command')) {
                $candidateOwner = $ownerValue | Select-Object *
                if ($mode -eq 'malformed-parent') {
                    $candidateOwner.ProcessParentProcessId = 0
                } elseif ($mode -eq 'malformed-parent-start') {
                    $candidateOwner.ProcessParentProcessStartTicks = '9201'
                } elseif ($mode -eq 'malformed-command') {
                    $candidateOwner.ProcessCommandLine = ''
                }
                $state = [pscustomobject]@{
                    Mode = $mode; Owner = $candidateOwner; Inspection = $inspectionValue
                    SnapshotQueries = 0; CurrentQueries = 0; ParentQueries = 0
                    IdentityQueries = 0; BelongsQueries = 0; SnapshotWasNull = $false
                }
                $script:GateBBrowserFastState = $state
                Set-Item Function:\Test-VerifierListenerInspectionSchema -Force -Value {
                    return $true
                }
                Set-Item Function:\Test-VerifierListenerRecordSchema -Force -Value {
                    return $true
                }
                Set-Item Function:\Get-VerifierLoopbackListenerRecords -Force -Value {
                    $s = $script:GateBBrowserFastState
                    $result = $s.Inspection | Select-Object *
                    $result.Listeners = @($s.Inspection.Listeners | ForEach-Object {
                        $_ | Select-Object *
                    })
                    if ($s.Mode -eq 'descendant') {
                        $result.Listeners[0].ProcessId = [int]$s.Owner.ProcessId + 1
                        $result.Listeners[0].ProcessStartTicks =
                            [long]$s.Owner.ProcessStartTicks + 1L
                    }
                    return $result
                }
                Set-Item Function:\Get-VerifierBrowserOwnershipSnapshot -Force -Value {
                    $s = $script:GateBBrowserFastState
                    $s.SnapshotQueries++
                    return @([pscustomobject]@{
                        ProcessId = [int]$s.Owner.ProcessId
                        ParentProcessId = 9200
                        Name = [IO.Path]::GetFileName([string]$s.Owner.BrowserPath)
                        ExecutablePath = [string]$s.Owner.BrowserPath
                        CommandLine = ('"' + [string]$s.Owner.BrowserPath +
                            '" --user-data-dir="' + [string]$s.Owner.Profile +
                            '" --tsj-verifier-run="' + [string]$s.Owner.RunId +
                            '" --tsj-verifier-worktree="' +
                            [string]$s.Owner.RepositoryIdentity +
                            '" --remote-debugging-port="' +
                            [string]$s.Owner.CdpPort + '"')
                    })
                }
                Set-Item Function:\Get-VerifierCurrentProcessRecordById -Force -Value {
                    param($processId)
                    $s = $script:GateBBrowserFastState
                    $s.CurrentQueries++
                    $pidValue = [int]$processId
                    if ($pidValue -eq 4) {
                        $start = 0L
                    } elseif ($pidValue -eq [int]$s.Owner.ProcessId) {
                        $start = if ($s.Mode -eq 'wrong-start') {
                            [long]$s.Owner.ProcessStartTicks + 1L
                        } else { [long]$s.Owner.ProcessStartTicks }
                    } else {
                        $start = [long]$s.Owner.ProcessStartTicks + 1L
                    }
                    return [pscustomobject]@{
                        ProcessId = $pidValue; ProcessStartTicks = $start
                    }
                }
                Set-Item Function:\Get-VerifierCurrentParentStartTicks -Force -Value {
                    $s = $script:GateBBrowserFastState
                    $s.ParentQueries++
                    if ($s.Mode -eq 'wrong-parent') { return 9999L }
                    return 9201L
                }
                Set-Item Function:\Get-VerifierProcessRecordsByIdWithFallback -Force -Value {
                    param($processId, $purpose)
                    $s = $script:GateBBrowserFastState
                    $s.IdentityQueries++
                    if ([int]$processId -ne [int]$s.Owner.ProcessId) {
                        return @()
                    }
                    $command = '"' + [string]$s.Owner.BrowserPath +
                        '" --user-data-dir="' + [string]$s.Owner.Profile +
                        '" --tsj-verifier-run="' + [string]$s.Owner.RunId +
                        '" --tsj-verifier-worktree="' +
                        [string]$s.Owner.RepositoryIdentity +
                        '" --remote-debugging-port="' +
                        [string]$s.Owner.CdpPort + '"'
                    if ($s.Mode -eq 'wrong-markers') {
                        $command = '"' + [string]$s.Owner.BrowserPath + '"'
                    }
                    $executablePath = if ($s.Mode -eq 'wrong-executable') {
                        'C:\Program Files\Other Browser\other.exe'
                    } else { [string]$s.Owner.BrowserPath }
                    return @([pscustomobject]@{
                        ProcessId = [int]$s.Owner.ProcessId
                        ParentProcessId = 9200
                        Name = [IO.Path]::GetFileName([string]$s.Owner.BrowserPath)
                        ExecutablePath = $executablePath
                        CommandLine = $command
                    })
                }
                Set-Item Function:\Test-VerifierListenerBelongsToOwner -Force -Value {
                    param($candidateOwner, $candidateListener, $processId,
                        $processStartTicks, $snapshot)
                    $s = $script:GateBBrowserFastState
                    $s.BelongsQueries++
                    $s.SnapshotWasNull = $null -eq $snapshot
                    if ($s.Mode -eq 'late-proof') {
                        Start-Sleep -Milliseconds 600
                        return $true
                    }
                    if ($s.Mode -eq 'descendant') { return $true }
                    return [bool](& $script:GateBBrowserFastOriginalBelongs `
                        $candidateOwner $candidateListener $processId `
                        $processStartTicks $snapshot)
                }
                $accepted = $false
                $typedFailure = $false
                $errorMessage = ''
                try {
                    $proof = Get-VerifierPortLeaseBoundOwnershipProof `
                        $contextValue $leaseValue $state.Owner.ProcessId `
                        $state.Owner.ProcessStartTicks $state.Owner $null
                    $accepted = $null -ne $proof
                } catch {
                    $typedFailure = Test-VerifierInfrastructureError $_
                    $errorMessage = Get-VerifierErrorMessage $_
                }
                [void]$results.Add([pscustomobject]@{
                    Mode = $state.Mode; Accepted = $accepted
                    TypedFailure = $typedFailure; Error = $errorMessage
                    SnapshotQueries = [int]$state.SnapshotQueries
                    CurrentQueries = [int]$state.CurrentQueries
                    ParentQueries = [int]$state.ParentQueries
                    IdentityQueries = [int]$state.IdentityQueries
                    BelongsQueries = [int]$state.BelongsQueries
                    SnapshotWasNull = [bool]$state.SnapshotWasNull
                })
            }
            # Exercise the actual downstream durable setter with the same
            # synthetic OS views.  Keep these records nested under the
            # existing valid-fast result so the proof rows above retain their
            # historical one-row-per-mode shape.
            foreach ($setterCase in @(
                    [pscustomobject]@{ Mode = 'valid-fast'; AuthorizedProcessId = [int]$ownerValue.ProcessId; AuthorizedProcessStartTicks = [long]$ownerValue.ProcessStartTicks }
                    [pscustomobject]@{ Mode = 'wrong-start'; AuthorizedProcessId = [int]$ownerValue.ProcessId; AuthorizedProcessStartTicks = [long]$ownerValue.ProcessStartTicks }
                    [pscustomobject]@{ Mode = 'wrong-executable'; AuthorizedProcessId = [int]$ownerValue.ProcessId; AuthorizedProcessStartTicks = [long]$ownerValue.ProcessStartTicks }
                    [pscustomobject]@{ Mode = 'wrong-parent'; AuthorizedProcessId = [int]$ownerValue.ProcessId; AuthorizedProcessStartTicks = [long]$ownerValue.ProcessStartTicks }
                    [pscustomobject]@{ Mode = 'wrong-markers'; AuthorizedProcessId = [int]$ownerValue.ProcessId; AuthorizedProcessStartTicks = [long]$ownerValue.ProcessStartTicks }
                    [pscustomobject]@{ Mode = 'changed-root-pid'; AuthorizedProcessId = [int]$ownerValue.ProcessId + 1; AuthorizedProcessStartTicks = [long]$ownerValue.ProcessStartTicks }
                    [pscustomobject]@{ Mode = 'changed-listener-pid-start'; AuthorizedProcessId = [int]$ownerValue.ProcessId; AuthorizedProcessStartTicks = [long]$ownerValue.ProcessStartTicks }
                    [pscustomobject]@{ Mode = 'malformed-parent'; AuthorizedProcessId = [int]$ownerValue.ProcessId; AuthorizedProcessStartTicks = [long]$ownerValue.ProcessStartTicks }
                    [pscustomobject]@{ Mode = 'malformed-parent-start'; AuthorizedProcessId = [int]$ownerValue.ProcessId; AuthorizedProcessStartTicks = [long]$ownerValue.ProcessStartTicks }
                    [pscustomobject]@{ Mode = 'malformed-command'; AuthorizedProcessId = [int]$ownerValue.ProcessId; AuthorizedProcessStartTicks = [long]$ownerValue.ProcessStartTicks }
                    [pscustomobject]@{ Mode = 'descendant'; AuthorizedProcessId = [int]$ownerValue.ProcessId; AuthorizedProcessStartTicks = [long]$ownerValue.ProcessStartTicks }
                    [pscustomobject]@{ Mode = 'mixed'; AuthorizedProcessId = [int]$ownerValue.ProcessId; AuthorizedProcessStartTicks = [long]$ownerValue.ProcessStartTicks }
                    [pscustomobject]@{ Mode = 'late-proof'; AuthorizedProcessId = [int]$ownerValue.ProcessId; AuthorizedProcessStartTicks = [long]$ownerValue.ProcessStartTicks }
                )) {
                $candidateOwner = $ownerValue | Select-Object *
                if ($setterCase.Mode -eq 'malformed-parent') {
                    $candidateOwner.ProcessParentProcessId = 0
                } elseif ($setterCase.Mode -eq 'malformed-parent-start') {
                    $candidateOwner.ProcessParentProcessStartTicks = '9201'
                } elseif ($setterCase.Mode -eq 'malformed-command') {
                    $candidateOwner.ProcessCommandLine = ''
                }
                $candidateInspection = $inspectionValue | Select-Object *
                $candidateInspection.Listeners = @($inspectionValue.Listeners | ForEach-Object {
                    $_ | Select-Object *
                })
                if ($setterCase.Mode -eq 'descendant') {
                    $candidateInspection.Listeners[0].ProcessId =
                        [int]$candidateOwner.ProcessId + 1
                    $candidateInspection.Listeners[0].ProcessStartTicks =
                        [long]$candidateOwner.ProcessStartTicks + 1L
                } elseif ($setterCase.Mode -eq 'changed-listener-pid-start') {
                    $candidateInspection.Listeners[0].ProcessId =
                        [int]$candidateOwner.ProcessId + 1
                    $candidateInspection.Listeners[0].ProcessStartTicks =
                        [long]$candidateOwner.ProcessStartTicks + 1L
                } elseif ($setterCase.Mode -eq 'mixed') {
                    $kernelListener = $candidateInspection.Listeners[0] | Select-Object *
                    $kernelListener.ProcessId = 4
                    $kernelListener.ProcessStartTicks = 0L
                    $kernelListener.ListenerOwnerKind = 'kernel-transport'
                    $kernelListener.ListenerOwnerProof = 'run-owned-preview-http-sys-v1'
                    $kernelListener.ListenerOwnerEvidence = 'pid-4-system-http-sys'
                    $candidateInspection.Listeners = @(
                        $candidateInspection.Listeners[0], $kernelListener)
                }
                $state = [pscustomobject]@{
                    Mode = $setterCase.Mode; Owner = $candidateOwner
                    Inspection = $candidateInspection; SnapshotQueries = 0
                    CurrentQueries = 0; ParentQueries = 0; IdentityQueries = 0
                    BelongsQueries = 0; SnapshotWasNull = $false
                }
                $script:GateBBrowserFastState = $state
                $setterLease = [pscustomobject]@{
                    Port = 45126; ListenerProcessId = 0
                    ListenerProcessStartTicks = 0L; ListenerOwnerKind = 'none'
                    ListenerOwnerProof = ''; ListenerOwnerEvidence = ''
                    ListenerInspectionSuccess = $false
                    ListenerInspectionKnown = $false
                    ListenerHasListeners = $null; ListenerAbsent = $null
                }
                $accepted = $false
                $typedFailure = $false
                $errorMessage = ''
                try {
                    Set-VerifierLeaseListenerInspection $setterLease `
                        $candidateInspection $contextValue $candidateOwner `
                        $setterCase.AuthorizedProcessId `
                        $setterCase.AuthorizedProcessStartTicks
                    $accepted = $true
                } catch {
                    $typedFailure = Test-VerifierInfrastructureError $_
                    $errorMessage = Get-VerifierErrorMessage $_
                }
                [void]$setterResults.Add([pscustomobject]@{
                    Mode = $setterCase.Mode; Accepted = $accepted
                    TypedFailure = $typedFailure; Error = $errorMessage
                    SnapshotQueries = [int]$state.SnapshotQueries
                    CurrentQueries = [int]$state.CurrentQueries
                    ParentQueries = [int]$state.ParentQueries
                    IdentityQueries = [int]$state.IdentityQueries
                    BelongsQueries = [int]$state.BelongsQueries
                    SnapshotWasNull = [bool]$state.SnapshotWasNull
                    ListenerProcessId = [int]$setterLease.ListenerProcessId
                    ListenerProcessStartTicks = $setterLease.ListenerProcessStartTicks
                    ListenerInspectionSuccess = [bool]$setterLease.ListenerInspectionSuccess
                    ListenerInspectionKnown = [bool]$setterLease.ListenerInspectionKnown
                    ListenerHasListeners = $setterLease.ListenerHasListeners
                    ListenerAbsent = $setterLease.ListenerAbsent
                })
            }
            $validResult = @($results | Where-Object Mode -eq 'valid-fast' |
                Select-Object -First 1)
            if ($validResult.Count -eq 1) {
                [void](Add-Member -InputObject $validResult[0] -MemberType NoteProperty `
                    -Name SetterCases -Value @($setterResults) -Force
                )
            }
            return @($results)
        } finally {
            foreach ($name in $saved.Keys) {
                Set-Item Function:\$name -Force -Value $saved[$name]
            }
            Remove-Variable -Name GateBBrowserFastState,`
                GateBBrowserFastOriginalBelongs,GateBBrowserFastOriginalStartTicks `
                -Scope Script -Force `
                -ErrorAction SilentlyContinue
        }
    } $owner $inspection $context $lease

    $valid = @($probe | Where-Object Mode -eq 'valid-fast')
    Assert-GateB ($valid.Count -eq 1 -and $valid[0].Accepted -and
        -not $valid[0].TypedFailure -and
        [int]$valid[0].SnapshotQueries -eq 0 -and
        [bool]$valid[0].SnapshotWasNull -and
        [int]$valid[0].IdentityQueries -eq 1) `
        'same-root browser listener did not use the PID-scoped identity fast path'

    foreach ($negativeMode in @(
            'wrong-start', 'wrong-executable', 'wrong-parent', 'wrong-markers')) {
        $negative = @($probe | Where-Object Mode -eq $negativeMode)
        Assert-GateB ($negative.Count -eq 1 -and -not $negative[0].Accepted -and
            $negative[0].TypedFailure -and
            [int]$negative[0].SnapshotQueries -eq 0) `
            "$negativeMode browser identity was accepted or fell back to the broad snapshot"
    }
    foreach ($malformedMode in @(
            'malformed-parent', 'malformed-parent-start', 'malformed-command')) {
        $malformed = @($probe | Where-Object Mode -eq $malformedMode)
        Assert-GateB ($malformed.Count -eq 1 -and
            [int]$malformed[0].SnapshotQueries -eq 1 -and
            -not [bool]$malformed[0].SnapshotWasNull) `
            "$malformedMode browser owner did not use the complete snapshot fallback"
    }
    $malformedParent = @($probe | Where-Object Mode -eq 'malformed-parent')
    $malformedParentStart = @($probe | Where-Object Mode -eq 'malformed-parent-start')
    $malformedCommand = @($probe | Where-Object Mode -eq 'malformed-command')
    Assert-GateB ($malformedParent[0].Accepted -and
        -not $malformedParent[0].TypedFailure -and
        $malformedCommand[0].Accepted -and
        -not $malformedCommand[0].TypedFailure -and
        -not $malformedParentStart[0].Accepted -and
        $malformedParentStart[0].TypedFailure) `
        'complete browser snapshot fallback did not preserve malformed-owner outcomes'
    $late = @($probe | Where-Object Mode -eq 'late-proof')
    Assert-GateB ($late.Count -eq 1 -and -not $late[0].Accepted -and
        $late[0].TypedFailure -and [int]$late[0].SnapshotQueries -eq 0 -and
        [bool]$late[0].SnapshotWasNull -and
        [string]$late[0].Error -match 'stage=browser-root-listener-fast-identity' -and
        [string]$late[0].Error -match 'elapsedMs=') `
        'same-root browser listener accepted a late PID-scoped identity proof'

    $descendant = @($probe | Where-Object Mode -eq 'descendant')
    Assert-GateB ($descendant.Count -eq 1 -and $descendant[0].Accepted -and
        -not $descendant[0].TypedFailure -and
        [int]$descendant[0].SnapshotQueries -eq 1 -and
        -not [bool]$descendant[0].SnapshotWasNull) `
        'descendant browser listener bypassed the complete ownership snapshot path'

    $setterCases = @($valid[0].SetterCases)
    Assert-GateB ($valid.Count -eq 1 -and $setterCases.Count -eq 13) `
        'downstream listener setter fast-path canary did not return every required case'
    $setterFast = @($setterCases | Where-Object Mode -eq 'valid-fast')
    Assert-GateB ($setterFast.Count -eq 1 -and $setterFast[0].Accepted -and
        -not $setterFast[0].TypedFailure -and
        [int]$setterFast[0].SnapshotQueries -eq 0 -and
        [bool]$setterFast[0].SnapshotWasNull -and
        [int]$setterFast[0].IdentityQueries -eq 1 -and
        [int]$setterFast[0].ListenerProcessId -eq [int]$owner.ProcessId -and
        [long]$setterFast[0].ListenerProcessStartTicks -eq [long]$owner.ProcessStartTicks -and
        [bool]$setterFast[0].ListenerInspectionSuccess -and
        [bool]$setterFast[0].ListenerInspectionKnown -and
        [bool]$setterFast[0].ListenerHasListeners -and
        -not [bool]$setterFast[0].ListenerAbsent) `
        'downstream listener setter did not persist the valid PID-scoped fast-path proof'
    foreach ($setterNegativeMode in @(
            'wrong-start', 'wrong-executable', 'wrong-parent', 'wrong-markers')) {
        $setterNegative = @($setterCases | Where-Object Mode -eq $setterNegativeMode)
        Assert-GateB ($setterNegative.Count -eq 1 -and
            -not $setterNegative[0].Accepted -and
            $setterNegative[0].TypedFailure -and
            [int]$setterNegative[0].SnapshotQueries -eq 0 -and
            [bool]$setterNegative[0].SnapshotWasNull -and
            [int]$setterNegative[0].ListenerProcessId -eq 0 -and
            -not [bool]$setterNegative[0].ListenerInspectionSuccess) `
            "$setterNegativeMode downstream listener setter accepted or broadened a fast-path identity negative"
    }
    foreach ($setterFallbackMode in @(
            'changed-root-pid', 'changed-listener-pid-start',
            'malformed-parent', 'malformed-parent-start', 'malformed-command',
            'descendant', 'mixed')) {
        $setterFallback = @($setterCases | Where-Object Mode -eq $setterFallbackMode)
        Assert-GateB ($setterFallback.Count -eq 1 -and
            [int]$setterFallback[0].SnapshotQueries -eq 1 -and
            -not [bool]$setterFallback[0].SnapshotWasNull) `
            "$setterFallbackMode downstream listener setter did not use the complete snapshot fallback"
    }
    $setterChangedRoot = @($setterCases | Where-Object Mode -eq 'changed-root-pid')
    $setterChangedListener = @($setterCases | Where-Object Mode -eq 'changed-listener-pid-start')
    Assert-GateB (-not $setterChangedRoot[0].Accepted -and
        $setterChangedRoot[0].TypedFailure -and
        -not $setterChangedListener[0].Accepted -and
        $setterChangedListener[0].TypedFailure) `
        'downstream listener setter accepted a changed root or listener identity'
    foreach ($setterAcceptedFallbackMode in @(
            'malformed-parent', 'malformed-command', 'descendant')) {
        $setterAcceptedFallback = @($setterCases | Where-Object Mode -eq $setterAcceptedFallbackMode)
        Assert-GateB ($setterAcceptedFallback[0].Accepted -and
            -not $setterAcceptedFallback[0].TypedFailure -and
            [bool]$setterAcceptedFallback[0].ListenerInspectionSuccess -and
            [bool]$setterAcceptedFallback[0].ListenerInspectionKnown) `
            "$setterAcceptedFallbackMode downstream complete proof did not authorize the retained listener"
    }
    $setterMalformedStart = @($setterCases | Where-Object Mode -eq 'malformed-parent-start')
    Assert-GateB (-not $setterMalformedStart[0].Accepted -and
        $setterMalformedStart[0].TypedFailure -and
        [int]$setterMalformedStart[0].ListenerProcessId -eq 0) `
        'downstream listener setter accepted a malformed parent start identity'
    $setterDescendant = @($setterCases | Where-Object Mode -eq 'descendant')
    Assert-GateB ([int]$setterDescendant[0].ListenerProcessId -eq [int]$owner.ProcessId + 1 -and
        [long]$setterDescendant[0].ListenerProcessStartTicks -eq [long]$owner.ProcessStartTicks + 1L) `
        'downstream listener setter did not retain the complete-proof descendant listener identity'
    $setterMixed = @($setterCases | Where-Object Mode -eq 'mixed')
    Assert-GateB (-not $setterMixed[0].Accepted -and $setterMixed[0].TypedFailure -and
        [int]$setterMixed[0].CurrentQueries -eq 3 -and
        [int]$setterMixed[0].ListenerProcessId -eq 0) `
        'downstream listener setter accepted a mixed user/kernel listener set'
    $setterLate = @($setterCases | Where-Object Mode -eq 'late-proof')
    Assert-GateB (-not $setterLate[0].Accepted -and $setterLate[0].TypedFailure -and
        [int]$setterLate[0].SnapshotQueries -eq 0 -and
        [bool]$setterLate[0].SnapshotWasNull -and
        [int]$setterLate[0].ListenerProcessId -eq 0) `
        'downstream listener setter accepted a delayed PID-scoped proof'
    Write-Host ('PASS:same-root browser listener fast path preserves PID-scoped executable/parent/marker ' +
        'identity negatives, strict deadline, downstream setter authorization, and descendant complete-snapshot fallback')
}

function Invoke-GateBDescendantSnapshotRefreshCanary() {
    $module = @(Get-Module VerifierIsolation | Select-Object -First 1)
    if ($module.Count -ne 1) {
        Throw-GateBInfrastructure 'VerifierIsolation module was unavailable for descendant snapshot refresh canary.'
    }
    $owner = [pscustomobject]@{
        ProcessId = 9100; ProcessStartTicks = 800L
        Profile = 'C:\Temp\gate-b-descendant-refresh-profile'
        BrowserPath = 'C:\Program Files\Edge\Application\msedge.exe'
        RunId = 'gate-b-descendant-refresh-run'
        RepositoryIdentity = 'gate-b-descendant-refresh-worktree'; CdpPort = 45125
    }
    $listener = [pscustomobject]@{
        LocalAddress = '127.0.0.1'; Port = 45125
        ProcessId = 9101; ProcessStartTicks = 801L
        ListenerOwnerKind = 'user-process'
        ListenerOwnerProof = 'diagnostics-process-start-v1'
        ListenerOwnerEvidence = 'system-diagnostics-process-starttime'
        Source = 'Get-NetTCPConnection'
    }
    $inspection = [pscustomobject]@{
        Success = $true; Known = $true; HasListeners = $true
        Listeners = @($listener); ListenerOwnerKind = 'user-process'
        ListenerOwnerProof = 'diagnostics-process-start-v1'
        ListenerOwnerEvidence = 'system-diagnostics-process-starttime'
        Source = 'Get-NetTCPConnection'; Error = ''
    }
    $context = [pscustomobject]@{}
    $lease = [pscustomobject]@{ Port = 45125 }
    $probe = & $module[0] {
        param($ownerValue, $inspectionValue, $contextValue, $leaseValue)
        $saved = @{}
        foreach ($name in @(
                'Test-VerifierListenerInspectionSchema',
                'Test-VerifierListenerRecordSchema',
                'Get-VerifierLoopbackListenerRecords',
                'Get-VerifierBrowserOwnershipSnapshot',
                'Get-VerifierCurrentProcessRecordById',
                 'Test-VerifierListenerBelongsToOwner',
                 'Test-VerifierRunOwnedPreviewHttpSysListener',
                 'New-VerifierRunOwnedPreviewHttpSysAuthorizationProof')) {
            $saved[$name] = (Get-Command $name -CommandType Function -ErrorAction Stop).ScriptBlock
        }
        try {
                $results = New-Object Collections.ArrayList
                foreach ($mode in @('transient', 'present', 'inaccessible', 'foreign',
                    'listener-start-changed', 'delayed-proof', 'delayed-listener',
                    'delayed-http-proof', 'delayed-second-view',
                    'late-second-view')) {
                $state = [pscustomobject]@{
                    Mode = $mode; ListenerQueries = 0; SnapshotQueries = 0
                    CurrentQueries = 0; BelongsQueries = 0; SecondViewQueries = 0
                    HttpProofQueries = 0; TypedFailure = $false
                }
                $script:GateBDescendantRefreshState = $state
                Set-Item Function:\Test-VerifierListenerInspectionSchema -Force -Value {
                    param($candidateInspection, $candidateContext, $candidateOwner)
                    return $true
                }
                Set-Item Function:\Test-VerifierListenerRecordSchema -Force -Value {
                    param($candidateListener, $candidateContext, $candidateOwner)
                    return $true
                }
                Set-Item Function:\Get-VerifierLoopbackListenerRecords -Force -Value {
                    $s = $script:GateBDescendantRefreshState
                    $s.ListenerQueries++
                    if ($s.Mode -eq 'delayed-listener' -and
                            $s.ListenerQueries -gt 1) {
                        Start-Sleep -Milliseconds 600
                    }
                    $resultInspection = $inspectionValue | Select-Object *
                    $resultInspection.Listeners = @($inspectionValue.Listeners)
                    if ($s.Mode -eq 'listener-start-changed' -and $s.ListenerQueries -gt 1) {
                        $changed = $inspectionValue.Listeners[0] | Select-Object *
                        $changed.ProcessStartTicks = 802L
                        $resultInspection.Listeners = @($changed)
                    }
                    if ($s.Mode -eq 'delayed-http-proof') {
                        $kernelInspection = $inspectionValue | Select-Object *
                        $kernelListener = $inspectionValue.Listeners[0] | Select-Object *
                        $kernelListener.ProcessId = 4
                        $kernelListener.ProcessStartTicks = $null
                        $kernelListener.ListenerOwnerKind = 'kernel-transport'
                        $kernelListener.ListenerOwnerProof = 'run-owned-preview-http-sys-v1'
                        $kernelListener.ListenerOwnerEvidence = 'pid-4-system-http-sys'
                        $kernelInspection.Listeners = @($kernelListener)
                        $kernelInspection.ListenerOwnerKind = 'kernel-transport'
                        $kernelInspection.ListenerOwnerProof = 'run-owned-preview-http-sys-v1'
                        $kernelInspection.ListenerOwnerEvidence = 'pid-4-system-http-sys'
                        return $kernelInspection
                    }
                    return $resultInspection
                }
                Set-Item Function:\Get-VerifierBrowserOwnershipSnapshot -Force -Value {
                    $s = $script:GateBDescendantRefreshState
                    $s.SnapshotQueries++
                    if ($s.Mode -eq 'delayed-proof' -and
                            $s.SnapshotQueries -gt 1) {
                        Start-Sleep -Milliseconds 600
                    }
                    return [pscustomobject]@{
                        Version = [int]$s.SnapshotQueries; Root = $ownerValue.RunId
                        Profile = $ownerValue.Profile; Port = $ownerValue.CdpPort
                    }
                }
                Set-Item Function:\Get-VerifierCurrentProcessRecordById -Force -Value {
                    param($processId)
                    $s = $script:GateBDescendantRefreshState
                    $s.CurrentQueries++
                    if ([int]$processId -eq 9103) {
                        $s.SecondViewQueries++
                        if ($s.Mode -eq 'present') {
                            return [pscustomobject]@{ ProcessId = 9103; ProcessStartTicks = 803L }
                        }
                        if ($s.Mode -eq 'inaccessible') {
                            Throw-VerifierInfrastructure 'synthetic second process view inaccessible'
                        }
                        if ($s.Mode -eq 'delayed-second-view') {
                            Start-Sleep -Milliseconds 600
                        }
                        return $null
                    }
                    if ([int]$processId -eq 9100) {
                        return [pscustomobject]@{ ProcessId = 9100; ProcessStartTicks = 800L }
                    }
                    if ([int]$processId -eq 9101) {
                        return [pscustomobject]@{ ProcessId = 9101; ProcessStartTicks = 801L }
                    }
                    return $null
                }
                Set-Item Function:\Test-VerifierListenerBelongsToOwner -Force -Value {
                    param($candidateOwner, $candidateListener, $processId,
                        $processStartTicks, $snapshot)
                    $s = $script:GateBDescendantRefreshState
                    $s.BelongsQueries++
                    if ([string]$candidateOwner.Profile -cne [string]$ownerValue.Profile -or
                            [string]$candidateOwner.RunId -cne [string]$ownerValue.RunId -or
                            [int]$candidateOwner.CdpPort -ne [int]$ownerValue.CdpPort -or
                            [int]$candidateListener.ProcessId -ne 9101 -or
                            [long]$candidateListener.ProcessStartTicks -ne 801L) {
                        Throw-VerifierInfrastructure 'synthetic exact owner/listener tuple changed'
                    }
                    if ($s.Mode -eq 'foreign') { return $false }
                    if (($s.Mode -eq 'transient' -or
                            $s.Mode -eq 'delayed-proof' -or
                            $s.Mode -eq 'delayed-listener' -or
                            $s.Mode -eq 'listener-start-changed' -or
                            $s.Mode -eq 'delayed-second-view') -and
                            $s.BelongsQueries -eq 1) {
                        Throw-VerifierInfrastructureMissingProcess `
                            'synthetic descendant disappeared from stale snapshot' 9103
                    }
                    if ($s.Mode -eq 'present' -or $s.Mode -eq 'inaccessible') {
                        Throw-VerifierInfrastructureMissingProcess `
                            'synthetic descendant snapshot candidate unavailable' 9103
                    }
                    if ($s.Mode -eq 'transient' -and
                            [int]$snapshot.Version -ne 2) {
                        Throw-VerifierInfrastructure 'synthetic stale ownership snapshot was reused'
                    }
                    if ($s.Mode -eq 'late-second-view') {
                        Start-Sleep -Milliseconds 600
                        Throw-VerifierInfrastructureMissingProcess `
                            'synthetic descendant proof became stale before its second view' 9103
                    }
                    return $true
                }
                Set-Item Function:\Test-VerifierRunOwnedPreviewHttpSysListener -Force -Value {
                    $s = $script:GateBDescendantRefreshState
                    $s.HttpProofQueries++
                    if ($s.Mode -eq 'delayed-http-proof') {
                        Start-Sleep -Milliseconds 600
                    }
                    return $true
                }
                Set-Item Function:\New-VerifierRunOwnedPreviewHttpSysAuthorizationProof -Force -Value {
                    $s = $script:GateBDescendantRefreshState
                    $s.HttpProofQueries++
                    if ($s.Mode -eq 'delayed-http-proof') {
                        Start-Sleep -Milliseconds 600
                        return $null
                    }
                    return [pscustomobject]@{ Protocol = 'synthetic-proof'; Authorized = $true }
                }
                $accepted = $false
                $typedFailure = $false
                $previewListenerOwner = if ($mode -eq 'delayed-http-proof') {
                    [pscustomobject]@{ Name = 'synthetic-kernel-preview-owner' }
                } else { $null }
                try {
                    $accepted = [bool](Get-VerifierPortLeaseBoundOwnershipProof `
                        $contextValue $leaseValue 9100 800L $ownerValue $previewListenerOwner)
                } catch {
                    $accepted = $false
                    $typedFailure = Test-VerifierInfrastructureError $_
                }
                [void]$results.Add([pscustomobject]@{
                    Mode = $state.Mode; Accepted = $accepted
                    ListenerQueries = [int]$state.ListenerQueries
                    SnapshotQueries = [int]$state.SnapshotQueries
                    CurrentQueries = [int]$state.CurrentQueries
                    BelongsQueries = [int]$state.BelongsQueries
                    SecondViewQueries = [int]$state.SecondViewQueries
                    HttpProofQueries = [int]$state.HttpProofQueries
                    TypedFailure = $typedFailure
                })
            }
            return @($results)
        } finally {
            foreach ($name in $saved.Keys) {
                Set-Item Function:\$name -Force -Value $saved[$name]
            }
            Remove-Variable -Name GateBDescendantRefreshState -Scope Script -Force `
                -ErrorAction SilentlyContinue
        }
    } $owner $inspection $context $lease
    $transient = @($probe | Where-Object Mode -eq 'transient')
    $present = @($probe | Where-Object Mode -eq 'present')
    $inaccessible = @($probe | Where-Object Mode -eq 'inaccessible')
    $foreign = @($probe | Where-Object Mode -eq 'foreign')
    $changed = @($probe | Where-Object Mode -eq 'listener-start-changed')
    $delayed = @($probe | Where-Object Mode -eq 'delayed-proof')
    $delayedListener = @($probe | Where-Object Mode -eq 'delayed-listener')
    $delayedHttp = @($probe | Where-Object Mode -eq 'delayed-http-proof')
    $delayedSecondView = @($probe | Where-Object Mode -eq 'delayed-second-view')
    $lateSecondView = @($probe | Where-Object Mode -eq 'late-second-view')
    Assert-GateB ($transient.Count -eq 1 -and $transient[0].Accepted -and
        [int]$transient[0].ListenerQueries -eq 2 -and
        [int]$transient[0].SnapshotQueries -eq 2 -and
        [int]$transient[0].CurrentQueries -eq 5 -and
        [int]$transient[0].BelongsQueries -eq 2) `
        'absent descendant did not trigger a fresh complete ownership proof'
    foreach ($negative in @($present, $inaccessible, $foreign)) {
        Assert-GateB ($negative.Count -eq 1 -and -not $negative[0].Accepted) `
            'present, inaccessible, or foreign descendant was accepted during refresh'
    }
    Assert-GateB ([int]$present[0].SnapshotQueries -eq 1 -and
        [int]$inaccessible[0].SnapshotQueries -eq 1 -and
        [int]$foreign[0].SnapshotQueries -eq 1) `
        'non-absent descendant failure reused or refreshed a stale snapshot'
    Assert-GateB ($changed.Count -eq 1 -and -not $changed[0].Accepted -and
        [int]$changed[0].ListenerQueries -eq 2 -and
        [int]$changed[0].SnapshotQueries -eq 1) `
        'listener PID/start change was accepted during descendant snapshot refresh'
    Assert-GateB ($delayed.Count -eq 1 -and -not $delayed[0].Accepted -and
        $delayed[0].TypedFailure -and
        [int]$delayed[0].ListenerQueries -eq 2 -and
        [int]$delayed[0].SnapshotQueries -eq 2 -and
        [int]$delayed[0].CurrentQueries -eq 5 -and
        [int]$delayed[0].BelongsQueries -eq 1) `
        'delayed refresh-lane ownership proof was accepted after its monotonic budget'
    Assert-GateB ($delayedListener.Count -eq 1 -and
        -not $delayedListener[0].Accepted -and $delayedListener[0].TypedFailure -and
        [int]$delayedListener[0].ListenerQueries -eq 2 -and
        [int]$delayedListener[0].SnapshotQueries -eq 1 -and
        [int]$delayedListener[0].CurrentQueries -eq 3 -and
        [int]$delayedListener[0].BelongsQueries -eq 1 -and
        [int]$delayedListener[0].HttpProofQueries -eq 0) `
        'delayed refresh-lane listener inspection was accepted after its monotonic budget'
    Assert-GateB ($delayedHttp.Count -eq 1 -and
        -not $delayedHttp[0].Accepted -and $delayedHttp[0].TypedFailure -and
        [int]$delayedHttp[0].ListenerQueries -eq 1 -and
        [int]$delayedHttp[0].HttpProofQueries -eq 1 -and
        [int]$delayedHttp[0].SnapshotQueries -eq 0 -and
        [int]$delayedHttp[0].CurrentQueries -eq 0 -and
        [int]$delayedHttp[0].BelongsQueries -eq 0) `
        'delayed HTTP.sys proof was not rejected before returning kernel ownership'
    Assert-GateB ($delayedSecondView.Count -eq 1 -and
        -not $delayedSecondView[0].Accepted -and
        $delayedSecondView[0].TypedFailure -and
        [int]$delayedSecondView[0].ListenerQueries -eq 1 -and
        [int]$delayedSecondView[0].SnapshotQueries -eq 1 -and
        [int]$delayedSecondView[0].CurrentQueries -eq 3 -and
        [int]$delayedSecondView[0].BelongsQueries -eq 1 -and
        [int]$delayedSecondView[0].SecondViewQueries -eq 1) `
        'delayed missing-PID absence proof was accepted after the strict deadline'
    Assert-GateB ($lateSecondView.Count -eq 1 -and
        -not $lateSecondView[0].Accepted -and $lateSecondView[0].TypedFailure -and
        [int]$lateSecondView[0].ListenerQueries -eq 1 -and
        [int]$lateSecondView[0].SnapshotQueries -eq 1 -and
        [int]$lateSecondView[0].CurrentQueries -eq 2 -and
        [int]$lateSecondView[0].BelongsQueries -eq 1 -and
        [int]$lateSecondView[0].SecondViewQueries -eq 0) `
        'late missing-PID proof was accepted after the strict deadline expired'
    Write-Host 'PASS:descendant snapshot refresh requires exact disappearance and repeats the complete unchanged ownership proof'
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

function Invoke-GateBStartPreviewAdoptionCanary() {
    $startPreviewPath = Join-Path $PSScriptRoot 'start-preview.ps1'
    $startPreviewText = Get-Content -LiteralPath $startPreviewPath -Raw -ErrorAction Stop
    Assert-GateB ($startPreviewText.IndexOf('Get-VerifierProcessSnapshotWithFallback',
        [StringComparison]::Ordinal) -ge 0 -and
        $startPreviewText.IndexOf('Get-VerifierPreviewAdoptionCandidateRecords',
            [StringComparison]::Ordinal) -ge 0 -and
        $startPreviewText -notmatch 'Get-CimInstance') `
        'start-preview adoption retained a direct WMI-only process snapshot'

    $module = @(Get-Module VerifierIsolation | Select-Object -First 1)
    if ($module.Count -ne 1) {
        throw 'VerifierIsolation module was unavailable for start-preview adoption canary.'
    }
    $previewScript = Get-VerifierFullPath (Join-Path $PSScriptRoot 'preview.ps1')
    $port = 45678
    $command = '"C:\Windows\System32\WindowsPowerShell\v1.0\powershell.exe" ' +
        '-NoProfile -ExecutionPolicy Bypass -File "' + $previewScript +
        '" -Port ' + [string]$port
    $validRecord = [pscustomobject]@{
        ProcessId = 123456; ParentProcessId = 654321; Name = 'powershell.exe'
        CommandLine = $command
    }
    $forcedProbe = & $module[0] {
        param($candidateScript, $candidatePort, $candidateRecord)
        $oldFunction = Get-Command Get-CimInstance -CommandType Function `
            -ErrorAction SilentlyContinue
        $oldScriptBlock = if ($null -ne $oldFunction) {
            $oldFunction.ScriptBlock
        } else { $null }
        try {
            Set-Item Function:\Get-CimInstance -Force -Value {
                throw [UnauthorizedAccessException]::new('forced WMI denial')
            }
            $snapshot = @(Get-VerifierProcessSnapshotWithFallback `
                'start-preview adoption forced WMI denial')
            $currentRecord = Get-VerifierCurrentProcessRecordById ([int]$PID)
            $selected = @(Get-VerifierPreviewAdoptionCandidateRecords `
                @($candidateRecord) $candidateScript $candidatePort 'powershell.exe')
            return [pscustomobject]@{
                SnapshotCount = $snapshot.Count
                CurrentRecord = $currentRecord
                SelectedCount = $selected.Count
            }
        } finally {
            if ($null -ne $oldFunction) {
                Set-Item Function:\Get-CimInstance -Force -Value $oldScriptBlock
            } else {
                Remove-Item Function:\Get-CimInstance -Force `
                    -ErrorAction SilentlyContinue
            }
        }
    } $previewScript $port $validRecord
    Assert-GateB ([int]$forcedProbe.SnapshotCount -gt 0 -and
        $null -ne $forcedProbe.CurrentRecord -and
        [int]$forcedProbe.CurrentRecord.ProcessId -eq [int]$PID -and
        [int]$forcedProbe.CurrentRecord.ParentProcessId -gt 0 -and
        [long]$forcedProbe.CurrentRecord.ProcessStartTicks -gt 0 -and
        -not [String]::IsNullOrWhiteSpace([string]$forcedProbe.CurrentRecord.CommandLine)) `
        'forced WMI denial did not produce a native current process record'
    Assert-GateB ([int]$forcedProbe.SelectedCount -eq 1) `
        'valid preview adoption candidate was not selected from the native path'

    foreach ($negative in @(
            [pscustomobject]@{ Name = 'malformed relevant PID'; Record = [pscustomobject]@{
                ProcessId = '123456'; ParentProcessId = 654321; Name = 'powershell.exe'
                CommandLine = $command } }
            [pscustomobject]@{ Name = 'malformed relevant command'; Record = [pscustomobject]@{
                ProcessId = 123456; ParentProcessId = 654321; Name = 'powershell.exe'
                CommandLine = [pscustomobject]@{ Value = $command } } }
            [pscustomobject]@{ Name = 'foreign marked candidate'; Record = [pscustomobject]@{
                ProcessId = 123456; ParentProcessId = 654321; Name = 'pwsh.exe'
                CommandLine = $command } }
        )) {
        $rejected = $false
        try {
            [void](Get-VerifierPreviewAdoptionCandidateRecords `
                @($negative.Record) $previewScript $port 'powershell.exe')
        } catch {
            $rejected = Test-VerifierInfrastructureError $_
        }
        Assert-GateB $rejected `
            "start-preview adoption accepted $($negative.Name)"
    }
    Write-Host ('PASS:start-preview adoption uses forced-WMI-denial native records, ' +
        'current parent/start identity, and rejects malformed/foreign candidates')
}

function Invoke-GateBNativeProcessInspectionCanary() {
    $module = @(Get-Module VerifierIsolation | Select-Object -First 1)
    if ($module.Count -ne 1) {
        throw 'VerifierIsolation module was unavailable for native process-inspection canary.'
    }
    $candidatePid = [int]$PID
    $probe = & $module[0] {
        param($candidatePid)
        $oldFunction = Get-Command Get-CimInstance -CommandType Function `
            -ErrorAction SilentlyContinue
        $oldScriptBlock = if ($null -ne $oldFunction) {
            $oldFunction.ScriptBlock
        } else { $null }
        try {
            Set-Item Function:\Get-CimInstance -Force -Value {
                throw [UnauthorizedAccessException]::new('Access is denied')
            }
            $rawRecords = @(Get-VerifierProcessRecordsByIdWithFallback $candidatePid `
                'native WMI access-denied canary')
            $snapshot = @(Get-VerifierProcessSnapshotWithFallback `
                'native WMI access-denied canary')
            $currentRecord = Get-VerifierCurrentProcessRecordById $candidatePid
            $current = @($snapshot | Where-Object {
                [int]$_.ProcessId -eq $candidatePid
            })
            $bounded = Invoke-VerifierBoundedProcess `
                (Get-Command powershell.exe -ErrorAction Stop).Source `
                @('-NoProfile', '-Command', 'Start-Sleep -Milliseconds 100') `
                10000
            return [pscustomobject]@{
                RecordCount = $rawRecords.Count
                SnapshotCount = $snapshot.Count
                CurrentCount = $current.Count
                RawRecord = if ($rawRecords.Count -eq 1) { $rawRecords[0] } else { $null }
                Record = $currentRecord
                Current = if ($current.Count -eq 1) { $current[0] } else { $null }
                BoundedExit = [int]$bounded.ExitCode
                BoundedTerminationProven = [bool]$bounded.TerminationProven
            }
        } finally {
            if ($null -ne $oldFunction) {
                Set-Item Function:\Get-CimInstance -Force -Value $oldScriptBlock
            } else {
                Remove-Item Function:\Get-CimInstance -Force `
                    -ErrorAction SilentlyContinue
            }
        }
    } $candidatePid
    Assert-GateB ([int]$probe.RecordCount -eq 1 -and
        [int]$probe.SnapshotCount -gt 0 -and [int]$probe.CurrentCount -eq 1) `
        'WMI access-denied fallback did not return a native current tuple and complete snapshot'
    Assert-GateB ($null -ne $probe.RawRecord -and
        [int]$probe.RawRecord.ProcessId -eq $candidatePid -and
        [int]$probe.RawRecord.ParentProcessId -gt 0 -and
        -not [String]::IsNullOrWhiteSpace([string]$probe.RawRecord.CommandLine)) `
        'native access-denied adapter did not return the raw PID/parent/command tuple'
    Assert-GateB ($null -ne $probe.Record -and
        [int]$probe.Record.ProcessId -eq $candidatePid -and
        [int]$probe.Record.ParentProcessId -gt 0 -and
        [long]$probe.Record.ProcessStartTicks -gt 0 -and
        -not [String]::IsNullOrWhiteSpace([string]$probe.Record.CommandLine) -and
        -not [String]::IsNullOrWhiteSpace([string]$probe.Record.ExecutablePath)) `
        'native access-denied tuple did not carry positive PID/parent/start/command/path identity'
    Assert-GateB ($null -ne $probe.Current -and
        [int]$probe.Current.ProcessId -eq $candidatePid) `
        'native access-denied snapshot omitted the current process'
    Assert-GateB ([int]$probe.BoundedExit -eq 0 -and
        [bool]$probe.BoundedTerminationProven) `
        'exact bounded cleanup did not complete through the native process view'

    foreach ($malformed in @(
            [pscustomobject]@{ Name = 'missing command'; Value = [pscustomobject]@{
                ProcessId = $candidatePid; ParentProcessId = 1 } }
            [pscustomobject]@{ Name = 'string parent'; Value = [pscustomobject]@{
                ProcessId = $candidatePid; ParentProcessId = '1'; CommandLine = 'native-canary' } }
            [pscustomobject]@{ Name = 'Boolean command'; Value = [pscustomobject]@{
                ProcessId = $candidatePid; ParentProcessId = 1; CommandLine = $true } }
        )) {
        $rejected = & $module[0] {
            param($candidatePid, $malformedValue)
            $oldCim = Get-Command Get-CimInstance -CommandType Function `
                -ErrorAction SilentlyContinue
            $oldCimScript = if ($null -ne $oldCim) { $oldCim.ScriptBlock } else { $null }
            $oldNative = Get-Command Get-VerifierNativeProcessSnapshot `
                -CommandType Function -ErrorAction Stop
            $oldNativeScript = $oldNative.ScriptBlock
            try {
                Set-Item Function:\Get-CimInstance -Force -Value {
                    throw [UnauthorizedAccessException]::new('Access is denied')
                }
                $script:GateBNativeMalformedValue = $malformedValue.Value
                Set-Item Function:\Get-VerifierNativeProcessSnapshot -Force -Value {
                    return @($script:GateBNativeMalformedValue)
                }
                try {
                    [void](Get-VerifierCurrentProcessRecordById $candidatePid)
                    return $false
                } catch {
                    return (Test-VerifierInfrastructureError $_)
                }
            } finally {
                if ($null -ne $oldCim) {
                    Set-Item Function:\Get-CimInstance -Force -Value $oldCimScript
                } else {
                    Remove-Item Function:\Get-CimInstance -Force `
                        -ErrorAction SilentlyContinue
                }
                Set-Item Function:\Get-VerifierNativeProcessSnapshot -Force `
                    -Value $oldNativeScript
                Remove-Variable -Name GateBNativeMalformedValue -Scope Script `
                    -Force -ErrorAction SilentlyContinue
            }
        } $candidatePid $malformed
        Assert-GateB ([bool]$rejected) `
            "malformed native process tuple '$($malformed.Name)' was accepted"
    }

    $pid4Rejected = & $module[0] {
        $oldCim = Get-Command Get-CimInstance -CommandType Function `
            -ErrorAction SilentlyContinue
        $oldCimScript = if ($null -ne $oldCim) { $oldCim.ScriptBlock } else { $null }
        try {
            Set-Item Function:\Get-CimInstance -Force -Value {
                throw [UnauthorizedAccessException]::new('Access is denied')
            }
            try {
                [void](Get-VerifierCurrentProcessRecordById 4)
                return $false
            } catch {
                return (Test-VerifierInfrastructureError $_)
            }
        } finally {
            if ($null -ne $oldCim) {
                Set-Item Function:\Get-CimInstance -Force -Value $oldCimScript
            } else {
                Remove-Item Function:\Get-CimInstance -Force `
                    -ErrorAction SilentlyContinue
            }
        }
    }
    Assert-GateB ([bool]$pid4Rejected) `
        'PID 4 was not rejected as a user-process identity under native inspection'
    Write-Host 'PASS:native Toolhelp/NtQuery process inspection, malformed tuple rejection, PID4 guard, and exact cleanup canary'
}

function Invoke-GateBTcpListenerPreviewCanary() {
    $canaryRoot = Join-Path ([IO.Path]::GetTempPath()) `
        ('TroubleshootJS\gate-b-tcp-preview-' + [Guid]::NewGuid().ToString('N'))
    $context = $null
    $failure = $null
    $cleanupErrors = New-Object Collections.ArrayList
    $cleanup = $null
    $stdoutLog = ''
    try {
        New-Item -ItemType Directory -Path $canaryRoot -Force -ErrorAction Stop | Out-Null
        $context = New-VerifierRunContext $repositoryRoot $canaryRoot
        $previewScript = Join-Path $repositoryRoot 'scripts\preview.ps1'
        # Let the shared allocator retain the selected port before the preview
        # starts. A probe-then-release port would allow unrelated host churn to
        # replace the intended listener between those two operations.
        [void](Start-VerifierOwnedPreview $context $previewScript 30 $true 0)
        $baseUrl = [string]$context.Server.BaseUrl
        $page = Invoke-WebRequest -UseBasicParsing -Uri ($baseUrl + '/circuitjs.html') `
            -TimeoutSec 5
        $identityResponse = Invoke-WebRequest -UseBasicParsing `
            -Uri ($baseUrl + '/__tsj/verify-identity') -TimeoutSec 5
        $identity = $identityResponse.Content | ConvertFrom-Json -ErrorAction Stop
        Assert-GateB ([int]$page.StatusCode -eq 200 -and
            [int]$identityResponse.StatusCode -eq 200 -and
            -not [String]::IsNullOrWhiteSpace([string]$page.Content)) `
            'TcpListener preview did not return the static page and identity route'
        Assert-GateB ([string]$identity.protocol -ceq 'troubleshootjs-preview-identity-v1' -and
            [int]$identity.previewPort -eq [int]$context.Server.Port -and
            [int]$identity.processId -eq [int]$context.Server.ProcessId -and
            [string]$identity.verifierRunId -ceq [string]$context.RunId -and
            [string]$identity.verifierNonce -ceq [string]$context.PreviewNonce) `
            'TcpListener identity route did not preserve the exact run/nonce/process/port handshake'
        $stdoutLog = [string]$context.Server.StdoutLog
        $cleanup = Complete-VerifierRun $context
        Assert-GateB ($cleanup -and [bool]$cleanup.Success) `
            'TcpListener preview exact process/listener/claim cleanup was not proven'
        Assert-GateBContextResourcesReleased $context
        Assert-GateB (Test-Path -LiteralPath $stdoutLog -PathType Leaf) `
            'TcpListener preview did not retain its owned launcher output'
        $transportOutput = Get-Content -LiteralPath $stdoutLog -Raw -ErrorAction Stop
        Assert-GateB ($transportOutput -match 'transport=TcpListener') `
            'forced TcpListener preview did not report the selected loopback transport'
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
        Throw-GateBInfrastructure ('TcpListener preview canary cleanup was not proven; evidence was retained at ' +
            $canaryRoot + ': ' + ($cleanupErrors -join '; '))
    }
    if ($null -ne $failure) {
        if (Test-VerifierInfrastructureError $failure) { throw $failure }
        Throw-GateBInfrastructure ('TcpListener preview canary failed: ' +
            (Get-VerifierErrorMessage $failure))
    }
    Write-Host 'PASS:forced TcpListener identity/static-page route, duplicate-PATH-safe launcher, and exact owned cleanup canary'
}

function Invoke-GateBNetstatPreferenceCanary() {
    $module = @(Get-Module VerifierIsolation | Select-Object -First 1)
    if ($module.Count -ne 1) {
        Throw-GateBInfrastructure 'VerifierIsolation module was unavailable for netstat preference canary.'
    }
    $port = 50001
    $process = Get-Process -Id $PID -ErrorAction Stop
    $processStartTicks = [long](Get-VerifierProcessStartTicks $process)
    $probe = & $module[0] {
        param($probePort, $probePid, $probeStartTicks)
        $oldNetFunction = Get-Command Get-NetTCPConnection `
            -CommandType Function -ErrorAction SilentlyContinue
        $oldNetScriptBlock = if ($null -ne $oldNetFunction) {
            $oldNetFunction.ScriptBlock
        } else { $null }
        $oldBoundedFunction = (Get-Command Invoke-VerifierBoundedProcess `
            -CommandType Function -ErrorAction Stop).ScriptBlock
        $result = $null
        try {
            $script:GateBNetstatPreferenceProviderCalls = 0
            $script:GateBNetstatPreferenceProcessCalls = 0
            $script:GateBNetstatPreferenceOutputMode = 'valid'
            Set-Item Function:\Get-NetTCPConnection -Force -Value {
                [CmdletBinding()]
                param($LocalPort, $State)
                $script:GateBNetstatPreferenceProviderCalls++
                Start-Sleep -Milliseconds 650
                return [pscustomobject]@{
                    LocalAddress = '127.0.0.1'
                    LocalPort = $LocalPort
                    OwningProcess = $probePid
                    State = 'Listen'
                }
            }
            Set-Item Function:\Invoke-VerifierBoundedProcess -Force -Value {
                param($FilePath, $Arguments, $TimeoutMilliseconds)
                $script:GateBNetstatPreferenceProcessCalls++
                $validLine = 'TCP 127.0.0.1:' + [string]$probePort +
                    ' 0.0.0.0:0 LISTENING ' + [string]$probePid
                if ($script:GateBNetstatPreferenceOutputMode -ceq 'valid') {
                    return [pscustomobject]@{
                        ExitCode = 0
                        Stdout = "Active Connections`r`n  Proto  Local Address          Foreign Address        State           PID`r`n$validLine"
                        Stderr = ''
                    }
                }
                if ($script:GateBNetstatPreferenceOutputMode -ceq 'empty') {
                    return [pscustomobject]@{ ExitCode = 0; Stdout = ''; Stderr = '' }
                }
                if ($script:GateBNetstatPreferenceOutputMode -ceq 'malformed') {
                    return [pscustomobject]@{
                        ExitCode = 0; Stdout = 'TCP 127.0.0.1:' +
                            [string]$probePort + ' malformed'; Stderr = ''
                    }
                }
                return [pscustomobject]@{
                    ExitCode = 1; Stdout = 'netstat failed'; Stderr = ''
                }
            }

            $defaultInspection = Get-VerifierLoopbackListenerRecords $probePort
            $defaultListener = @($defaultInspection.Listeners)
            $defaultProviderCalls = [int]$script:GateBNetstatPreferenceProviderCalls
            $defaultProcessCalls = [int]$script:GateBNetstatPreferenceProcessCalls

            $script:GateBNetstatPreferenceProviderCalls = 0
            $script:GateBNetstatPreferenceProcessCalls = 0
            $script:GateBNetstatPreferenceOutputMode = 'valid'
            $preferredInspection = Get-VerifierLoopbackListenerRecords `
                $probePort -PreferNetstat
            $preferredListener = @($preferredInspection.Listeners)
            $preferredProviderCalls = [int]$script:GateBNetstatPreferenceProviderCalls
            $preferredProcessCalls = [int]$script:GateBNetstatPreferenceProcessCalls
            $preferredInspectionSchema = Test-VerifierListenerInspectionSchema `
                $preferredInspection $null $null $null -StructuralOnly
            $preferredListenerSchema = if ($preferredListener.Count -eq 1) {
                Test-VerifierListenerRecordSchema $preferredListener[0] `
                    $null $null $null -StructuralOnly
            } else { $false }

            $failureResults = New-Object Collections.ArrayList
            foreach ($mode in @('empty', 'malformed', 'error')) {
                $script:GateBNetstatPreferenceProviderCalls = 0
                $script:GateBNetstatPreferenceProcessCalls = 0
                $script:GateBNetstatPreferenceOutputMode = $mode
                $rejected = $false
                $typedFailure = $false
                try {
                    [void](Get-VerifierLoopbackListenerRecords $probePort `
                        -PreferNetstat)
                } catch {
                    $rejected = $true
                    $typedFailure = Test-VerifierInfrastructureError $_
                }
                [void]$failureResults.Add([pscustomobject]@{
                    Mode = $mode
                    Rejected = $rejected
                    TypedFailure = $typedFailure
                    ProviderCalls = [int]$script:GateBNetstatPreferenceProviderCalls
                    ProcessCalls = [int]$script:GateBNetstatPreferenceProcessCalls
                })
            }
            $result = [pscustomobject]@{
                DefaultInspectionSource = [string]$defaultInspection.Source
                DefaultProviderCalls = $defaultProviderCalls
                DefaultProcessCalls = $defaultProcessCalls
                DefaultListenerCount = $defaultListener.Count
                PreferredInspectionSource = [string]$preferredInspection.Source
                PreferredProviderCalls = $preferredProviderCalls
                PreferredProcessCalls = $preferredProcessCalls
                PreferredInspectionSchema = [bool]$preferredInspectionSchema
                PreferredListenerSchema = [bool]$preferredListenerSchema
                PreferredListener = if ($preferredListener.Count -eq 1) {
                    $preferredListener[0]
                } else { $null }
                FailureResults = @($failureResults)
            }
        } finally {
            if ($null -ne $oldNetFunction) {
                Set-Item Function:\Get-NetTCPConnection -Force `
                    -Value $oldNetScriptBlock
            } else {
                Remove-Item Function:\Get-NetTCPConnection -Force `
                    -ErrorAction SilentlyContinue
            }
            Set-Item Function:\Invoke-VerifierBoundedProcess -Force `
                -Value $oldBoundedFunction
            Remove-Variable -Name GateBNetstatPreferenceProviderCalls,`
                GateBNetstatPreferenceProcessCalls,GateBNetstatPreferenceOutputMode `
                -Scope Script -Force -ErrorAction SilentlyContinue
        }
        return $result
    } $port $PID $processStartTicks

    Assert-GateB ($probe.DefaultInspectionSource -ceq 'Get-NetTCPConnection' -and
        [int]$probe.DefaultProviderCalls -eq 1 -and
        [int]$probe.DefaultProcessCalls -eq 0 -and
        [int]$probe.DefaultListenerCount -eq 1) `
        'ordinary loopback listener query did not retain the Get-NetTCPConnection-primary route'
    $preferredListener = $probe.PreferredListener
    Assert-GateB ($probe.PreferredInspectionSource -ceq 'netstat' -and
        [int]$probe.PreferredProviderCalls -eq 0 -and
        [int]$probe.PreferredProcessCalls -eq 1 -and
        [bool]$probe.PreferredInspectionSchema -and
        [bool]$probe.PreferredListenerSchema -and
        $null -ne $preferredListener -and
        [int]$preferredListener.Port -eq $port -and
        [int]$preferredListener.ProcessId -eq $PID -and
        [long]$preferredListener.ProcessStartTicks -eq $processStartTicks -and
        [string]$preferredListener.LocalAddress -ceq '127.0.0.1' -and
        [string]$preferredListener.Source -ceq 'netstat' -and
        [string]$preferredListener.ListenerOwnerKind -ceq 'user-process' -and
        [string]$preferredListener.ListenerOwnerProof -ceq 'diagnostics-process-start-v1' -and
        [string]$preferredListener.ListenerOwnerEvidence -ceq 'system-diagnostics-process-starttime') `
        'opt-in netstat listener query did not preserve exact PID/start/listener/schema identity'
    foreach ($failure in @($probe.FailureResults)) {
        Assert-GateB ($failure.Rejected -and $failure.TypedFailure -and
            [int]$failure.ProviderCalls -eq 0 -and
            [int]$failure.ProcessCalls -eq 1) `
            "opt-in netstat $($failure.Mode) output was not fail-closed"
    }
    Write-Host 'PASS:opt-in netstat listener route skips the slow provider, preserves exact identity/schema, and fails closed on malformed/empty/error output'
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

function Invoke-GateBRouteCompletionCanary() {
    # Execute the real route body with bounded transport/file-I/O doubles.
    # This proves completion ordering and rejection, not Java or live CDP.
    $scope = New-Module -ScriptBlock { }
    & $scope {
        param($SourcePath)
        Set-StrictMode -Version Latest
        $tokens = $null; $errors = $null
        $ast = [Management.Automation.Language.Parser]::ParseFile($SourcePath, [ref]$tokens, [ref]$errors)
        if ($errors.Count -ne 0) { throw 'Route completion source did not parse.' }
        foreach ($name in @('verifyRoute', 'Write-VerifierRouteTiming')) {
            $definition = @($ast.EndBlock.Statements | Where-Object {
                $_ -is [Management.Automation.Language.FunctionDefinitionAst] -and $_.Name -ceq $name
            })
            if ($definition.Count -ne 1) { throw 'Route completion helper was not unique.' }
            . ([scriptblock]::Create($definition[0].Extent.Text))
        }
        function Write-Host($Object) { [void]$script:messages.Add([string]$Object) }
        function Test-Task43PForcedNegativeMarker($value) { return $false }
        function Get-Task43PRepositoryState($roots) { return [pscustomobject]@{headSha='fixture'} }
        function startVerifierBrowser($name, $url) {
            return [pscustomobject]@{Profile='fixture';Browser='fixture';Socket='fixture';Deadline=[DateTime]::UtcNow.AddSeconds(30)}
        }
        function invokeCdp($socket, [ref]$counter, $method, $parameters, [ref]$failures, $deadline) {
            return [pscustomobject]@{result=[pscustomobject]@{result=[pscustomobject]@{value='PASS:fixture'}}}
        }
        function Wait-Task43PBrowserStartup { }
        function Get-Task43ForcedNegativeNavigationUrl($url) { return $url }
        function navigateAndWaitForDocument { }
        function Start-Sleep { }
        function evaluateCdp($socket, [ref]$counter, $expression, [ref]$failures, $deadline) {
            if ($expression -cne 'document.readyState') { throw 'Unexpected completion CDP expression.' }
            [void]$script:events.Add('diagnostics')
            if ($script:captured) { throw 'fixture: post-persistence transport deadline expired' }
            if ($script:case -ceq 'diagnostic-error') { throw 'fixture: diagnostic transport failed' }
            return 'complete'
        }
        function Capture-Task43PEvidence($socket, [ref]$counter, $deadline, [ref]$failures) {
            [void]$script:events.Add('capture')
            $script:captured = $true
            if ($script:case -ceq 'capture-error') { throw 'fixture: persistence failed' }
            if ($script:case -ceq 'console-error') { $failures.Value += 'fixture: unexpected Java error' }
        }
        function Capture-Task43PSourceObservation {
            [void]$script:events.Add('capture'); $script:captured = $true
        }
        function cleanupBrowser($browser, $socket, $profile) {
            if ($null -eq $browser) { return }
            [void]$script:events.Add('cleanup')
            if ($script:case -ceq 'cleanup-error') { throw 'fixture: cleanup failed' }
        }
        function Set-VerifierFailure($errorRecord, $name) { [void]$script:rejections.Add([string]$errorRecord) }
        $script:Task43PExecutionRoots = $null
        $script:VerifierEvidenceDirectory = ''
        $Task43PStartupSettleMilliseconds = 0
        foreach ($script:case in @('capture-last', 'source-last', 'normal-route', 'diagnostic-error', 'capture-error', 'console-error', 'cleanup-error')) {
            $script:events = [Collections.Generic.List[string]]::new()
            $script:messages = [Collections.Generic.List[string]]::new()
            $script:rejections = [Collections.Generic.List[string]]::new()
            $script:captured = $false
            $script:Task43PSourceDefinition = if ($script:case -ceq 'source-last') { [pscustomobject]@{id='fixture'} } else { $null }
            $route = if ($script:case -ceq 'normal-route') { 'normal fixture' } else { 'task43p fixture' }
            $actual = verifyRoute $route 'http://127.0.0.1:1/fixture' 'PASS:fixture'
            $expected = $script:case -in @('capture-last', 'source-last', 'normal-route')
            if ($actual -isnot [bool] -or $actual -ne $expected) { throw ('Route completion outcome changed: ' + $script:case) }
            if ($expected -and $script:rejections.Count -ne 0) { throw 'Successful route retained a rejection.' }
            if (-not $expected -and $script:rejections.Count -eq 0) { throw 'Failed route lost its rejection.' }
            if ($script:case -in @('capture-last', 'source-last') -and
                    ($script:events -join ',') -cne 'diagnostics,capture,cleanup') { throw 'CDP remained after evidence persistence.' }
            if ($script:case -ceq 'normal-route' -and
                    ($script:events -join ',') -cne 'diagnostics,cleanup') { throw 'Normal route diagnostics changed.' }
            $timings = @($script:messages | Where-Object { $_.StartsWith('VERIFIER_ROUTE_TIMING ') } | ForEach-Object {
                $_.Substring('VERIFIER_ROUTE_TIMING '.Length) | ConvertFrom-Json
            })
            if ($timings.Count -lt 1 -or $timings[-1].phase -cne 'cleanup' -or
                    $timings[-1].elapsedMilliseconds -lt 0 -or $timings[-1].remainingRouteMilliseconds -lt 0) { throw 'Route timing/cleanup diagnostics missing.' }
            if (-not $expected -and @($timings | Where-Object outcome -CEQ 'failed').Count -eq 0) { throw 'Failed route timing lost its phase.' }
        }
    } (Join-Path $PSScriptRoot 'verify-browser.ps1')
    Write-Host 'PASS:actual route completion preserves normal diagnostics; Task43P captures last; transport, persistence, Java-error, and cleanup failures still reject with timing'
}

function Invoke-GateBCdpReferenceCanary() {
    Invoke-GateBRouteCompletionCanary
    # Load the actual helper bodies without running the browser driver's setup.
    # Inject only the CDP transport and a capture-boundary observer; these cases
    # prove reference/JSON handling, not Java acceptance or visible interaction.
    $tokens = $null
    $parseErrors = $null
    $ast = [Management.Automation.Language.Parser]::ParseFile(
        (Join-Path $PSScriptRoot 'verify-browser.ps1'), [ref]$tokens, [ref]$parseErrors)
    Assert-GateB ($parseErrors.Count -eq 0) 'CDP helper source did not parse'
    foreach ($name in @('Capture-Task43PEvidence', 'evaluateCdp', 'invokeCdp',
            'resolveCdpRouteDeadline', 'waitForCdp', 'clickPoint', 'sendKey',
            'clickButtonAndWaitForPredicate', 'clickTrayPartAndWaitForSelection',
            'selectOptionWithKeyboard', 'ConvertTo-Task43PStartupSettleMilliseconds',
            'Wait-Task43PBrowserStartup', 'navigateAndWaitForDocument')) {
        $definitions = @($ast.EndBlock.Statements | Where-Object {
            $_ -is [Management.Automation.Language.FunctionDefinitionAst] -and
                $_.Name -ceq $name
        })
        Assert-GateB ($definitions.Count -eq 1) "CDP helper '$name' was not unique"
        . ([scriptblock]::Create($definitions[0].Extent.Text))
    }
    function sendCdp($socket, [int]$id, [string]$method, $parameters) {
        [void]$socket.Requests.Add([pscustomobject]@{ Id = $id; Method = $method; Parameters = $parameters })
    }
    function receiveCdp($socket, [int]$wantedId, [ref]$failures, [DateTime]$deadline) {
        Assert-GateB ($socket.Requests[-1].Id -eq $wantedId) 'CDP response ID changed'
        Assert-GateB ($socket.Results.Count -gt 0) 'CDP helper exceeded its response fixture'
        $failures.Value += ('event:' + $wantedId)
        if ($socket.PSObject.Properties['DelayMilliseconds'] -and $socket.DelayMilliseconds -gt 0) {
            Start-Sleep -Milliseconds $socket.DelayMilliseconds
        }
        if ($socket.PSObject.Properties['RawResponse']) {
            [void]$socket.Results.Dequeue()
            return $socket.RawResponse
        }
        return [pscustomobject]@{ id = $wantedId; result = [pscustomobject]@{
            result = [pscustomobject]@{ value = $socket.Results.Dequeue() }
        } }
    }
    function Assert-Task43PJavaEvidenceProvenance($Value) {
        Assert-GateB ($Value.deep.entries -is [array] -and
            $Value.deep.entries.Count -eq 2 -and
            $Value.deep.entries[0].ok -is [bool] -and $Value.deep.entries[0].ok -and
            $Value.deep.entries[0].number -eq 7 -and
            $null -eq $Value.deep.entries[1] -and
            $Value.deep.text -ceq 'canary') 'capture changed nested JSON values or types'
        # Stop at the observed decode boundary before repository or file work.
        Throw-VerifierInfrastructure 'gate-b-capture-decoded-boundary'
    }
    $savedDeadline = Get-Variable CdpRouteDeadline -Scope Script -ErrorAction SilentlyContinue
    $savedDeadlineValue = if ($null -eq $savedDeadline) { $null } else { $savedDeadline.Value }
    $savedContext = Get-Variable VerifierContext -Scope Script -ErrorAction SilentlyContinue
    $savedContextValue = if ($null -eq $savedContext) { $null } else { $savedContext.Value }
    $savedRoute = Get-Variable VerifierCurrentRouteId -Scope Script -ErrorAction SilentlyContinue
    $savedRouteValue = if ($null -eq $savedRoute) { $null } else { $savedRoute.Value }
    try {
        $script:CdpRouteDeadline = [DateTime]::UtcNow.AddSeconds(30)
        $script:VerifierContext = [pscustomobject]@{ RunId = ('a' * 32) }
        $script:VerifierCurrentRouteId = 'b' * 32
        foreach ($caseName in @('omitted', 'reference', 'invalid')) {
            $socket = [pscustomobject]@{
                Requests = [Collections.ArrayList]::new()
                Results = [Collections.Queue]::new()
            }
            if ($caseName -cne 'invalid') {
                foreach ($value in @($true, $true, $true, 123456)) { $socket.Results.Enqueue($value) }
            }
            $counter = 41; $failures = @(); $marker = $null; $observedError = $null
            $timeOrigin = $null
            try {
                if ($caseName -ceq 'omitted') {
                    $timeOrigin = navigateAndWaitForDocument $socket ([ref]$counter) `
                        'http://127.0.0.1:12345/circuitjs.html?tsjChallenge=led' `
                        $script:CdpRouteDeadline ([ref]$failures)
                } else {
                    $output = if ($caseName -ceq 'reference') { [ref]$marker } else { 'not-a-reference' }
                    $timeOrigin = navigateAndWaitForDocument $socket ([ref]$counter) `
                        'http://127.0.0.1:12345/circuitjs.html?tsjChallenge=led' `
                        $script:CdpRouteDeadline ([ref]$failures) $output
                }
            } catch { $observedError = $_ }
            if ($caseName -ceq 'invalid') {
                Assert-GateB ($null -ne $observedError -and
                    (Test-VerifierInfrastructureError $observedError) -and
                    (Get-VerifierErrorMessage $observedError).Contains('Navigation marker output must be a reference') -and
                    $socket.Requests.Count -eq 0 -and $counter -eq 41 -and $failures.Count -eq 0) `
                    'invalid navigation output did not fail typed infrastructure before transport'
                continue
            }
            Assert-GateB ($null -eq $observedError -and $timeOrigin -eq 123456 -and
                $socket.Results.Count -eq 0 -and $socket.Requests.Count -eq 4 -and
                $counter -eq 45 -and $failures.Count -eq 4 -and
                ($socket.Requests.Method -join ',') -ceq
                    'Page.navigate,Runtime.evaluate,Runtime.evaluate,Runtime.evaluate') `
                "navigation $caseName lost its result, references, or document waits: $observedError"
            $navigationUrl = [string]$socket.Requests[0].Parameters.url
            $navigationMatch = [regex]::Match($navigationUrl, '&tsjVerifierNavigation=([0-9a-f]{32})&')
            Assert-GateB ($navigationMatch.Success -and
                $navigationUrl.EndsWith(('&tsjVerifierRun=' + ('a' * 32) + '&tsjVerifierRoute=' + ('b' * 32))) -and
                $socket.Requests[1].Parameters.expression.Contains($navigationMatch.Groups[1].Value) -and
                $socket.Requests[2].Parameters.expression.Contains(('tsjVerifierRun=' + ('a' * 32))) -and
                $socket.Requests[3].Parameters.expression -ceq 'performance.timeOrigin') `
                "navigation $caseName did not wait for its exact document and owner markers"
            if ($caseName -ceq 'reference') {
                Assert-GateB ($marker -is [string] -and $marker -ceq $navigationMatch.Groups[1].Value) `
                    'navigation output did not return the actual document token'
            }
        }
        Write-Host 'PASS:actual navigation helper accepts omitted/reference output and rejects malformed output before transport'
        $point = [pscustomobject]@{ x = 5; y = 5; visible = $true; enabled = $true; index = 1 }
        foreach ($caseName in @('capture-json', 'capture-malformed', 'capture-empty',
                'button', 'tray', 'select')) {
            $socket = [pscustomobject]@{
                Requests = [Collections.ArrayList]::new()
                Results = [Collections.Queue]::new()
            }
            $expectedError = ''
            $mouseMethods = @('Runtime.evaluate', 'Input.dispatchMouseEvent',
                'Input.dispatchMouseEvent', 'Runtime.evaluate')
            switch ($caseName) {
                'capture-json' {
                    $values = @('{"deep":{"entries":[{"ok":true,"number":7},null],"text":"canary"}}')
                    $expectedError = 'gate-b-capture-decoded-boundary'
                    $expectedMethods = @('Runtime.evaluate')
                }
                'capture-malformed' {
                    $values = @('{"deep":')
                    $expectedError = 'published invalid JSON evidence'
                    $expectedMethods = @('Runtime.evaluate')
                }
                'capture-empty' {
                    $values = @('')
                    $expectedError = 'did not publish structured evidence'
                    $expectedMethods = @('Runtime.evaluate')
                }
                'button' { $values = @($point, $true, $true, $true); $expectedMethods = $mouseMethods }
                'tray' {
                    $values = @($point, $true, $true, $true, 'selected canary')
                    $expectedMethods = $mouseMethods + @('Runtime.evaluate')
                }
                'select' {
                    $values = @($point, $point, [pscustomobject]@{ found = $true }) +
                        (@($true) * 12) + @('canary option')
                    $expectedMethods = @('Runtime.evaluate', 'Runtime.evaluate', 'Runtime.evaluate',
                        'Input.dispatchMouseEvent', 'Input.dispatchMouseEvent', 'Runtime.evaluate',
                        'Input.dispatchKeyEvent', 'Input.dispatchKeyEvent', 'Runtime.evaluate',
                        'Input.dispatchKeyEvent', 'Input.dispatchKeyEvent', 'Runtime.evaluate',
                        'Input.dispatchKeyEvent', 'Input.dispatchKeyEvent', 'Runtime.evaluate', 'Runtime.evaluate')
                }
            }
            foreach ($value in $values) { $socket.Results.Enqueue($value) }
            $counter = 41
            $failures = @('existing diagnostic')
            $observedError = $null
            try {
                switch -Wildcard ($caseName) {
                    'capture-*' { Capture-Task43PEvidence $socket ([ref]$counter) `
                        $script:CdpRouteDeadline ([ref]$failures) 'helper canary' '' '' $null }
                    'button' { clickButtonAndWaitForPredicate $socket ([ref]$counter) 'canary' `
                        'true' $script:CdpRouteDeadline ([ref]$failures) 'canary predicate' }
                    'tray' { [void](clickTrayPartAndWaitForSelection $socket ([ref]$counter) `
                        'canary' $script:CdpRouteDeadline ([ref]$failures)) }
                    'select' { selectOptionWithKeyboard $socket ([ref]$counter) 0 `
                        'canary option' ([ref]$failures) }
                }
            } catch { $observedError = $_ }
            if ($expectedError) {
                Assert-GateB ($null -ne $observedError -and
                    (Test-VerifierInfrastructureError $observedError) -and
                    (Get-VerifierErrorMessage $observedError).Contains($expectedError)) `
                    "$caseName did not reach its expected typed capture boundary: $observedError"
            } else {
                Assert-GateB ($null -eq $observedError) "$caseName failed: $observedError"
            }
            Assert-GateB ($socket.Results.Count -eq 0 -and
                ($socket.Requests.Method -join ',') -ceq ($expectedMethods -join ',') -and
                $counter -eq (41 + $expectedMethods.Count)) "$caseName lost counter updates or helper calls"
            Assert-GateB ($failures.Count -eq (1 + $expectedMethods.Count) -and
                $failures[0] -ceq 'existing diagnostic') "$caseName lost the caller's diagnostic array"
            for ($index = 0; $index -lt $expectedMethods.Count; $index++) {
                Assert-GateB ($socket.Requests[$index].Id -eq (41 + $index) -and
                    $failures[1 + $index] -ceq ('event:' + (41 + $index))) `
                    "$caseName failed to preserve exact CDP IDs/diagnostics at $index"
            }
        }
        foreach ($value in @(0, 45000, '30000')) {
            $converted = ConvertTo-Task43PStartupSettleMilliseconds $value
            Assert-GateB ($converted -is [int] -and $converted -eq [int]$value) 'canonical settle input changed'
        }
        foreach ($value in @($null, $true, [double]3, -1, 45001, '030', '3.0',
                '9223372036854775808', [object[]]@(1, 2))) {
            $rejected = $false
            try { [void](ConvertTo-Task43PStartupSettleMilliseconds $value) } catch {
                $rejected = Test-VerifierInfrastructureError $_
            }
            Assert-GateB $rejected 'malformed/out-of-range settle input was accepted'
        }
        foreach ($caseName in @('zero', 'positive', 'early', 'deadline-before',
                'deadline-after', 'wrong-url', 'wrong-state', 'missing-result',
                'script-exception', 'protocol-error', 'invalid-internal')) {
            $document = [pscustomobject]@{ url = 'about:blank'; state = 'complete' }
            $socket = [pscustomobject]@{ Requests = [Collections.ArrayList]::new()
                Results = [Collections.Queue]::new(); DelayMilliseconds = 25 }
            $delay = 10
            $deadline = [DateTime]::UtcNow.AddSeconds(5)
            $expectedRequests = 1
            switch ($caseName) {
                'zero' { $delay = 0; $expectedRequests = 0 }
                'early' { $delay = 1000; $socket.DelayMilliseconds = 0 }
                'deadline-before' { $deadline = [DateTime]::UtcNow.AddMilliseconds(1); $expectedRequests = 0 }
                'deadline-after' { $deadline = [DateTime]::UtcNow.AddMilliseconds(100); $socket.DelayMilliseconds = 200 }
                'wrong-url' { $document.url = 'https://example.invalid/' }
                'wrong-state' { $document.state = 'loading' }
                'missing-result' { $socket | Add-Member RawResponse ([pscustomobject]@{ id = 41 }) }
                'script-exception' { $socket | Add-Member RawResponse ([pscustomobject]@{
                    id = 41; result = [pscustomobject]@{ result = [pscustomobject]@{ value = $document }
                        exceptionDetails = [pscustomobject]@{ text = 'canary' } } }) }
                'protocol-error' { $socket | Add-Member RawResponse ([pscustomobject]@{
                    id = 41; error = [pscustomobject]@{ message = 'canary' } }) }
                'invalid-internal' { $delay = '10'; $expectedRequests = 0 }
            }
            $socket.Results.Enqueue($document)
            $counter = 41; $failures = @(); $observedError = $null
            try { Wait-Task43PBrowserStartup $socket ([ref]$counter) ([ref]$failures) $deadline $delay } catch {
                $observedError = $_
            }
            if ($caseName -in @('zero', 'positive')) {
                Assert-GateB ($null -eq $observedError) "startup settle $caseName failed: $observedError"
            } else {
                Assert-GateB ($null -ne $observedError -and (Test-VerifierInfrastructureError $observedError)) `
                    "startup settle $caseName did not fail typed infrastructure: $observedError"
            }
            Assert-GateB ($socket.Requests.Count -eq $expectedRequests -and
                $counter -eq (41 + $expectedRequests) -and $failures.Count -eq $expectedRequests) `
                "startup settle $caseName changed counter/diagnostic forwarding or sent before validation"
            if ($expectedRequests -gt 0) {
                $request = $socket.Requests[0]
                Assert-GateB ($request.Method -ceq 'Runtime.evaluate' -and
                    $request.Parameters.awaitPromise -eq $true -and
                    $request.Parameters.returnByValue -eq $true -and
                    $request.Parameters.expression.Contains(('}),' + [string]$delay + '))'))) `
                    "startup settle $caseName did not issue its bounded real Promise request"
            }
        }
        foreach ($case in @(
                [pscustomobject]@{ Value = '-1'; Message = 'startup settle must be an exact integer' },
                [pscustomobject]@{ Value = '30000'; Message = 'startup settle requires a Task43P route selection' })) {
            $child = Invoke-GateBBoundedProcess (Get-Command powershell.exe -ErrorAction Stop).Source @(
                '-NoLogo', '-NoProfile', '-NonInteractive', '-File',
                (Join-Path $PSScriptRoot 'verify-browser.ps1'),
                '-Task43PStartupSettleMilliseconds', $case.Value) 10000 'startup settle CLI rejection'
            $childExit = Resolve-GateBChildExitCode $child 'startup settle CLI rejection'
            Assert-GateB ($childExit -eq 2 -and
                ([string]$child.Stdout + [string]$child.Stderr).Contains($case.Message)) `
                'startup settle CLI did not fail with its specific pre-context infrastructure result'
        }
        Write-Host 'PASS:Task43P opt-in startup settle CLI scalars, reference handoff, timing/deadline, and document/protocol rejection'
    } finally {
        if ($null -ne $savedContext) { $script:VerifierContext = $savedContextValue } else {
            Remove-Variable VerifierContext -Scope Script -ErrorAction Stop
        }
        if ($null -ne $savedRoute) { $script:VerifierCurrentRouteId = $savedRouteValue } else {
            Remove-Variable VerifierCurrentRouteId -Scope Script -ErrorAction Stop
        }
        if ($null -ne $savedDeadline) {
            $script:CdpRouteDeadline = $savedDeadlineValue
        } else {
            Remove-Variable CdpRouteDeadline -Scope Script -ErrorAction Stop
        }
    }
    Write-Host 'PASS:actual capture/input helper references and JSON parsing with injected CDP transport'
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
        # Even synthetic startup-failure fixtures participate in the current
        # durable receipt contract. They remain in the issued/unbound state,
        # which cannot authorize Browser.close but lets the existing exact
        # process-tree cleanup exercise its intended pre-bind path.
        $recoveryReceipt = [pscustomobject]@{
            Protocol = 'troubleshootjs-verifier-browser-recovery-v1'
            AuthorityToken = (([Guid]::NewGuid().ToString('N') +
                [Guid]::NewGuid().ToString('N')).ToLowerInvariant())
            IssuedUtc = Get-VerifierUtcText
            State = 'issued'; BoundUtc = ''; ClosedUtc = ''
            CloseAttempted = $false; CloseAttemptedUtc = ''
            RunId = $Context.RunId; RepositoryIdentity = $Context.RepositoryIdentity
            WorktreeRoot = $Context.WorktreeRoot; RouteId = $routeId; RouteName = $RouteName
            BrowserPath = $BrowserPath; Profile = Get-VerifierFullPath $profile
            LeaseId = $Lease.LeaseId; CdpPort = $Lease.Port
            RootProcessId = 0; RootProcessStartTicks = 0L
            RootParentProcessId = 0; RootParentProcessStartTicks = 0L
            RootProcessCommandLine = ''
            ListenerProcessId = 0; ListenerProcessStartTicks = 0L
        }
        $record = [pscustomobject]@{
            RunId = $Context.RunId; RepositoryIdentity = $Context.RepositoryIdentity
            WorktreeRoot = $Context.WorktreeRoot
            RouteId = $routeId; RouteName = $RouteName
            CdpPort = $Lease.Port; Lease = $Lease; Profile = Get-VerifierFullPath $profile
            BrowserPath = $BrowserPath
            ProcessId = 0; ProcessStartTicks = 0; ProcessParentProcessId = 0
            ProcessParentProcessStartTicks = 0; ProcessCommandLine = ''
            TargetId = ''; ExpectedUrl = ''
            Status = 'leased'; CleanupResult = 'pending'; Error = ''
            ProfileInspectionFailed = $false; ProfileProcessScanCompleted = $false
            RecoveryReceipt = $recoveryReceipt
            ContainmentLaunch = $null
            Runtime = [pscustomobject]@{
                Browser = $null; Socket = $null; ContainmentJob = $null
            }
        }
        $module = @(Get-Module VerifierIsolation | Select-Object -First 1)
        if ($module.Count -ne 1) {
            Throw-GateBInfrastructure 'VerifierIsolation module was unavailable for the canary containment launch record.'
        }
        $record.ContainmentLaunch = & $module[0] {
            param($innerContext, $innerRecord)
            [pscustomobject]@{
                Protocol = 'troubleshootjs-verifier-browser-containment-launch-v1'
                JobName = Get-VerifierBrowserContainmentJobName $innerContext $innerRecord
                State = 'unlaunched'; LaunchProcessId = 0; LaunchedUtc = ''
            }
        } $Context $record
        [void]$Context.BrowserSessions.Add($record)
        Write-VerifierManifest $Context
        return $record
    } catch {
        if (Test-VerifierInfrastructureError $_) { throw }
        Throw-GateBInfrastructure ('could not prepare canary browser profile: ' +
            (Get-VerifierErrorMessage $_))
    }
}

function Invoke-GateBBrowserLeaseConstructorCanary() {
    $browserPath = (Get-Command powershell.exe -ErrorAction Stop).Source
    $context = $null
    $validRecord = $null
    $primaryFailure = $null
    $cleanupErrors = New-Object Collections.ArrayList
    $canaryRoot = Join-Path ([IO.Path]::GetTempPath()) `
        ('TroubleshootJS\gate-b-browser-lease-' + [Guid]::NewGuid().ToString('N'))
    try {
        New-Item -ItemType Directory -Path $canaryRoot -Force -ErrorAction Stop | Out-Null
        $context = New-VerifierRunContext $repositoryRoot $canaryRoot
        foreach ($routeCase in @(
                [pscustomobject]@{ Name = 'null'; Value = $null }
                [pscustomobject]@{ Name = 'empty'; Value = '' }
                [pscustomobject]@{ Name = 'whitespace'; Value = '   ' }
                [pscustomobject]@{ Name = 'Boolean'; Value = $true }
                [pscustomobject]@{ Name = 'array'; Value = [object[]]@('route') }
            )) {
            $leaseCountBefore = @($context.LeaseRecords).Count
            $sessionCountBefore = @($context.BrowserSessions).Count
            $rejected = $false
            try {
                [void](New-VerifierBrowserLease $context $routeCase.Value $browserPath)
            } catch {
                $rejected = Test-VerifierInfrastructureError $_
            }
            Assert-GateB $rejected `
                "live browser lease accepted malformed $($routeCase.Name) RouteName"
            Assert-GateB (@($context.LeaseRecords).Count -eq $leaseCountBefore -and
                @($context.BrowserSessions).Count -eq $sessionCountBefore) `
                "malformed $($routeCase.Name) RouteName changed live ownership state"
        }

        $validRecord = New-VerifierBrowserLease $context 'constructor-valid' $browserPath
        Assert-GateB ((Test-VerifierStrictStringValue $validRecord.WorktreeRoot) -and
            $validRecord.WorktreeRoot -ceq $context.WorktreeRoot -and
            $validRecord.RouteName -ceq 'constructor-valid') `
            'valid browser lease did not retain the complete live session identity'
        $manifest = Get-Content -LiteralPath $context.ManifestPath -Raw | ConvertFrom-Json
        Assert-GateB (@($manifest.browserSessions).Count -eq 1 -and
            $manifest.browserSessions[0].worktreeRoot -ceq $context.WorktreeRoot -and
            $manifest.browserSessions[0].routeName -ceq 'constructor-valid') `
            'valid browser lease did not retain WorktreeRoot and RouteName in its first durable write'
        $serializedSession = $manifest.browserSessions[0]
        Assert-VerifierSerializedBrowserSessionRecord $serializedSession `
            'browser constructor serialized session'
        foreach ($routeMutation in @(
                [pscustomobject]@{ Name = 'empty'; Value = '' }
                [pscustomobject]@{ Name = 'Boolean'; Value = $true }
                [pscustomobject]@{ Name = 'array'; Value = [object[]]@('route') }
            )) {
            $mutatedSession = $serializedSession | Select-Object *
            $mutatedSession.routeName = $routeMutation.Value
            $serializedRejected = $false
            try {
                Assert-VerifierSerializedBrowserSessionRecord $mutatedSession `
                    ('serialized browser session ' + $routeMutation.Name)
            } catch {
                $serializedRejected = Test-VerifierInfrastructureError $_
            }
            Assert-GateB $serializedRejected `
                "serialized browser session accepted malformed $($routeMutation.Name) RouteName"
        }

        $context.TestHooks.FailNextBrowserLeaseManifestWrite = $true
        $rollbackRejected = $false
        try {
            [void](New-VerifierBrowserLease $context 'constructor-rollback' $browserPath)
        } catch {
            $rollbackRejected = Test-VerifierInfrastructureError $_
        }
        Assert-GateB $rollbackRejected `
            'browser lease constructor did not fail the injected prewrite canary'
        Assert-GateB (@($context.BrowserSessions | Where-Object {
                [object]::ReferenceEquals($_, $validRecord)
            }).Count -eq 1 -and @($context.BrowserSessions).Count -eq 1) `
            'browser lease prewrite failure left a session record in the live ledger'
        $browserDirectories = @(Get-ChildItem -LiteralPath (Join-Path $context.RunRoot 'browser') `
            -Directory -ErrorAction Stop)
        Assert-GateB ($browserDirectories.Count -eq 1 -and
            $browserDirectories[0].FullName -ceq (Split-Path -Parent $validRecord.Profile)) `
            'browser lease prewrite failure left an owned browser route directory'
        $rollbackLeases = @($context.LeaseRecords | Where-Object {
            -not [object]::ReferenceEquals($_, $validRecord.Lease)
        })
        Assert-GateB ($rollbackLeases.Count -eq 1 -and
            $rollbackLeases[0].Status -ceq 'released' -and
            $rollbackLeases[0].ClaimState -ceq 'released' -and
            $rollbackLeases[0].MutexReleased -eq $true -and
            -not (Test-Path -LiteralPath $rollbackLeases[0].Path) -and
            -not (Test-Path -LiteralPath $rollbackLeases[0].ProfilePath)) `
            'browser lease prewrite failure did not retain only a terminal, released lease proof'
        Write-Host 'PASS:browser lease constructor validates complete session identity, RouteName, and exact prewrite rollback'
    } catch {
        $primaryFailure = $_
    } finally {
        if ($null -ne $context) {
            if ($null -ne $validRecord) {
                try { [void]$context.BrowserSessions.Remove($validRecord) } catch {
                    [void]$cleanupErrors.Add('browser session cleanup: ' + (Get-VerifierErrorMessage $_))
                }
                try {
                    if (Test-Path -LiteralPath $validRecord.Profile -ErrorAction Stop) {
                        Assert-VerifierNoReparseAncestors $context.RunRoot
                        Remove-VerifierOwnedTree $context.RunRoot $validRecord.Profile
                    }
                    $validRouteRoot = Split-Path -Parent $validRecord.Profile
                    if (Test-Path -LiteralPath $validRouteRoot -ErrorAction Stop) {
                        Remove-VerifierOwnedTree $context.RunRoot $validRouteRoot
                    }
                } catch {
                    [void]$cleanupErrors.Add('browser profile cleanup: ' + (Get-VerifierErrorMessage $_))
                }
                try { Release-VerifierPortLease $context $validRecord.Lease } catch {
                    [void]$cleanupErrors.Add('browser lease cleanup: ' + (Get-VerifierErrorMessage $_))
                }
            }
            try {
                $cleanup = Complete-VerifierRun $context
                if ($null -eq $cleanup -or -not [bool]$cleanup.Success) {
                    $cleanupDetail = if ($cleanup) { @($cleanup.Errors) -join '; ' } else {
                        'browser constructor context cleanup returned no result'
                    }
                    [void]$cleanupErrors.Add($cleanupDetail)
                }
            } catch {
                [void]$cleanupErrors.Add('browser constructor context cleanup: ' + (Get-VerifierErrorMessage $_))
            }
        }
    }
    if ($cleanupErrors.Count -gt 0) {
        $cleanupMessage = 'browser lease constructor canary cleanup failed; evidence was retained: ' +
            ($cleanupErrors -join '; ')
        if ($null -ne $primaryFailure) {
            Throw-GateBInfrastructure ((Get-VerifierErrorMessage $primaryFailure) + '; ' + $cleanupMessage)
        }
        Throw-GateBInfrastructure $cleanupMessage
    }
    if ($null -ne $primaryFailure) {
        if (Test-VerifierInfrastructureError $primaryFailure) { throw $primaryFailure }
        throw $primaryFailure
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

function Test-GateBPortSelectionRace($ErrorRecord) {
    $message = Get-VerifierErrorMessage $ErrorRecord
    # This narrow predicate is used only by bounded canary port-reselection
    # paths.  A verified foreign listener or a requested-port bind loss means
    # that the free-port probe lost its race; unknown inspection/cleanup
    # remains a hard failure.
    return ($message -match '(?i)Loopback port \d+ is listening under foreign PID \d+/start \d+, not the recorded owner\.' -or
        $message -match '(?i)Could not claim requested isolated port \d+ for')
}

function Test-GateBSocketAddressAlreadyInUse($ErrorRecord) {
    $exception = if ($ErrorRecord -is [Management.Automation.ErrorRecord]) {
        $ErrorRecord.Exception
    } elseif ($ErrorRecord -is [Exception]) {
        $ErrorRecord
    } else { $null }
    while ($null -ne $exception) {
        if ($exception -is [Net.Sockets.SocketException] -and
                $exception.SocketErrorCode -eq [Net.Sockets.SocketError]::AddressAlreadyInUse) {
            return $true
        }
        $exception = $exception.InnerException
    }
    return $false
}

function Test-GateBInitialBindSelectionRace($ErrorRecord) {
    $portRace = Test-GateBPortSelectionRace $ErrorRecord
    $socketRace = Test-GateBSocketAddressAlreadyInUse $ErrorRecord
    return ($portRace -or $socketRace)
}

function Complete-GateBInitialBindAttemptCleanup($Context, $Lease,
        $Listener, [int]$Port) {
    $cleanupErrors = New-Object Collections.ArrayList
    $absenceProven = $false
    if ($Port -lt 1 -or $Port -gt 65535) {
        [void]$cleanupErrors.Add('initial bind retry cleanup received an invalid port')
    }
    if ($null -ne $Listener) {
        try { $Listener.Stop() } catch {
            [void]$cleanupErrors.Add(('initial bind listener stop: ' +
                (Get-VerifierErrorMessage $_)))
        }
    }
    if ($cleanupErrors.Count -eq 0) {
        $deadline = [DateTime]::UtcNow.AddSeconds(5)
        $lastInspection = $null
        while ([DateTime]::UtcNow -lt $deadline) {
            try {
                $lastInspection = Get-VerifierLoopbackListenerRecords $Port
                if (-not $lastInspection.Success -or -not $lastInspection.Known) {
                    Throw-GateBInfrastructure ('initial bind retry cleanup could not positively inspect port ' +
                        [string]$Port)
                }
                $currentStart = [long](Get-VerifierCurrentProcessStartTicks)
                $currentListeners = @($lastInspection.Listeners | Where-Object {
                    [int]$_.ProcessId -eq [int]$PID -and
                    [long]$_.ProcessStartTicks -eq $currentStart
                })
                # The exact listener object was stopped above.  A current
                # listener record after that point is therefore cleanup
                # uncertainty, never a reason to release or retry blindly.
                if ($currentListeners.Count -eq 0 -and
                        -not $lastInspection.HasListeners) {
                    $absenceProven = $true
                    break
                }
            } catch {
                [void]$cleanupErrors.Add(('initial bind retry listener inspection: ' +
                    (Get-VerifierErrorMessage $_)))
                break
            }
            Start-Sleep -Milliseconds 100
        }
        if (-not $absenceProven) {
            $inspectionDetail = if ($null -eq $lastInspection) { 'no inspection' } else {
                'success=' + [string]$lastInspection.Success +
                    ', known=' + [string]$lastInspection.Known +
                    ', hasListeners=' + [string]$lastInspection.HasListeners
            }
            [void]$cleanupErrors.Add(('initial bind retry cleanup did not prove port ' +
                [string]$Port + ' absence within 5 seconds (' + $inspectionDetail + ')'))
        }
    }
    if ($null -ne $Lease -and $absenceProven -and $cleanupErrors.Count -eq 0) {
        try {
            Release-VerifierPortLease $Context $Lease
            if (Test-Path -LiteralPath $Lease.Path -PathType Leaf) {
                [void]$cleanupErrors.Add(('initial bind retry cleanup left claim evidence at ' +
                    [string]$Lease.Path))
            }
        } catch {
            [void]$cleanupErrors.Add(('initial bind retry claim release: ' +
                (Get-VerifierErrorMessage $_)))
        }
    }
    return [pscustomobject]@{
        Success = ($cleanupErrors.Count -eq 0 -and
            ($null -eq $Lease -or $absenceProven))
        Errors = @($cleanupErrors)
    }
}

function Set-GateBLeaseProcessProofAfterExactCleanup($Lease) {
    $module = @(Get-Module VerifierIsolation | Select-Object -First 1)
    if ($module.Count -ne 1) {
        Throw-GateBInfrastructure 'VerifierIsolation module was unavailable for lease process-proof propagation.'
    }
    & $module[0] {
        param($item)
        [void](Set-VerifierLeaseProcessProofFromCleanup $item $true)
    } $Lease
}

function New-GateBLeaseAfterExactPortReselection($Context, [ref]$Port) {
    $maxAttempts = 8
    $lastRace = ''
    for ($attempt = 1; $attempt -le $maxAttempts; $attempt++) {
        $candidatePort = 0
        try {
            $candidatePort = Get-GateBFreeTcpPort
            $lease = New-GateBCanaryLease $Context $candidatePort
            $Port.Value = $candidatePort
            return $lease
        } catch {
            if (-not (Test-GateBPortSelectionRace $_) -or
                    $attempt -ge $maxAttempts) {
                throw
            }
            $lastRace = Get-VerifierErrorMessage $_
            Start-Sleep -Milliseconds 25
        }
    }
    Throw-GateBInfrastructure ('bounded exact free-port reselection exhausted after ' +
        [string]$maxAttempts + ' attempts: ' + $lastRace)
}

function Complete-GateBPortReuseAttemptCleanup($ReuseContext,
        $ReuseNewContext, $ReuseNewLease, $ReuseNewProcess, $ReuseListener,
        [int]$ReusePort) {
    $cleanupErrors = New-Object Collections.ArrayList
    if ($null -ne $ReuseNewProcess) {
        try {
            Stop-GateBExactProcess $ReuseNewProcess $ReusePort
            if ($null -ne $ReuseNewLease) {
                Set-GateBLeaseProcessProofAfterExactCleanup $ReuseNewLease
            }
        } catch {
            [void]$cleanupErrors.Add((Get-VerifierErrorMessage $_))
        }
    }
    if ($null -ne $ReuseListener) {
        try { Stop-GateBListenerExact $ReuseListener $ReusePort } catch {
            [void]$cleanupErrors.Add((Get-VerifierErrorMessage $_))
        }
    }
    foreach ($reuse in @($ReuseContext, $ReuseNewContext)) {
        if ($null -ne $reuse) {
            try {
                $reuseCleanup = Complete-VerifierRun $reuse
                if ($null -eq $reuseCleanup -or -not [bool]$reuseCleanup.Success) {
                    $reuseDetail = if ($reuseCleanup) {
                        @($reuseCleanup.Errors) -join '; '
                    } else { 'port-reuse retry cleanup returned no result.' }
                    [void]$cleanupErrors.Add($reuseDetail)
                }
            } catch {
                [void]$cleanupErrors.Add((Get-VerifierErrorMessage $_))
            }
        }
    }
    return [pscustomobject]@{
        Success = ($cleanupErrors.Count -eq 0)
        Errors = @($cleanupErrors)
    }
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
    foreach ($transition in @(
            [pscustomobject]@{ Status = 'leased'; ClaimState = 'held'; ReleaseState = 'active'; Journal = 'active' }
            [pscustomobject]@{ Status = 'bound'; ClaimState = 'bound'; ReleaseState = 'active'; Journal = 'active' }
            [pscustomobject]@{ Status = 'releasing'; ClaimState = 'releasing'; ReleaseState = 'releasing'; Journal = 'releasing' }
            [pscustomobject]@{ Status = 'releasing'; ClaimState = 'os-released'; ReleaseState = 'os-released'; Journal = 'os-released' }
            [pscustomobject]@{ Status = 'releasing'; ClaimState = 'os-released'; ReleaseState = 'claim-delete-failed'; Journal = 'claim-delete-failed' }
            [pscustomobject]@{ Status = 'released'; ClaimState = 'delete-pending'; ReleaseState = 'complete'; Journal = 'pre-delete' }
            [pscustomobject]@{ Status = 'released'; ClaimState = 'delete-pending'; ReleaseState = 'complete'; Journal = 'post-delete-pending' }
            [pscustomobject]@{ Status = 'released'; ClaimState = 'released'; ReleaseState = 'complete'; Journal = 'complete' }
        )) {
        $transitionRecord = [pscustomobject]@{
            Status = $transition.Status; ClaimState = $transition.ClaimState
            ReleaseState = $transition.ReleaseState
            ReleaseJournalState = $transition.Journal
        }
        [void](Assert-VerifierLeaseTransition $transitionRecord 'finite transition canary')
    }
    foreach ($transition in @(
            [pscustomobject]@{ Name = 'released+held+active'; Status = 'released'; ClaimState = 'held'; ReleaseState = 'active'; Journal = 'active' }
            [pscustomobject]@{ Name = 'released+released+active'; Status = 'released'; ClaimState = 'released'; ReleaseState = 'active'; Journal = 'complete' }
            [pscustomobject]@{ Name = 'releasing+bound+active'; Status = 'releasing'; ClaimState = 'bound'; ReleaseState = 'active'; Journal = 'active' }
            [pscustomobject]@{ Name = 'manifested'; Status = 'released'; ClaimState = 'released'; ReleaseState = 'manifested'; Journal = 'complete' }
        )) {
        $rejected = $false
        try {
            [void](Assert-VerifierLeaseTransition ([pscustomobject]@{
                Status = $transition.Status; ClaimState = $transition.ClaimState
                ReleaseState = $transition.ReleaseState
                ReleaseJournalState = $transition.Journal
            }) ('finite transition canary ' + $transition.Name))
        } catch { $rejected = Test-VerifierInfrastructureError $_ }
        Assert-GateB $rejected "finite lease transition accepted $($transition.Name)"
    }
    Write-Host 'PASS:finite lease transition validator accepts only canonical lifecycle tuples and rejects impossible cross-field states'

    # Session/root cleanup can prove termination even when its browser lease
    # never bound a positive process/listener identity.  Exercise the shared
    # propagation boundary directly for both lease classes: an unbound lease
    # must stay false/false, while a bound lease must retain both positives.
    $unboundProofLease = [pscustomobject]@{
        ProcessProofRequired = $false
        ProcessTerminationProven = $true
        ProcessAbsent = $true
    }
    $boundProofLease = [pscustomobject]@{
        ProcessProofRequired = $true
        ProcessTerminationProven = $false
        ProcessAbsent = $false
    }
    & $module[0] {
        param($unbound, $bound)
        [void](Set-VerifierLeaseProcessProofFromCleanup $unbound $true)
        [void](Set-VerifierLeaseProcessProofFromCleanup $bound $true)
    } $unboundProofLease $boundProofLease
    Assert-GateB (-not [bool]$unboundProofLease.ProcessTerminationProven -and
        -not [bool]$unboundProofLease.ProcessAbsent) `
        'unbound lease cleanup proof was not kept at explicit false/false'
    Assert-GateB ([bool]$boundProofLease.ProcessTerminationProven -and
        [bool]$boundProofLease.ProcessAbsent) `
        'bound lease cleanup proof was not retained as positive termination/absence'
    Write-Host 'PASS:lease cleanup proof propagation keeps unbound leases false/false and preserves bound termination/absence proof'
    $canaryRoot = Join-Path ([IO.Path]::GetTempPath()) `
        ('TroubleshootJS\gate-b-lifecycle-boolean-' + [Guid]::NewGuid().ToString('N'))
    $context = $null
    $lease = $null
    try {
        New-Item -ItemType Directory -Path $canaryRoot -Force -ErrorAction Stop | Out-Null
        $context = New-VerifierRunContext $repositoryRoot $canaryRoot
        $lease = New-GateBCanaryLease $context
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
    $reuseNewProcess = $null
    $reuseListener = $null
    $checksValidated = $false
    $canarySucceeded = $false
    try {
        $context = New-VerifierRunContext $repositoryRoot $rollbackRoot
        foreach ($failureHook in @('FailNextManifestWrite', 'FailNextClaimWrite')) {
            $port = 0
            $context.TestHooks.$failureHook = $true
            $classified = $false
            try {
                [void](New-GateBLeaseAfterExactPortReselection $context ([ref]$port))
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
            $reacquired = New-GateBCanaryLease $context $port
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
            $lease = New-GateBCanaryLease $context
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
        $reuseAttemptLimit = 5
        $reuseAttemptSucceeded = $false
        for ($reuseAttempt = 1; $reuseAttempt -le $reuseAttemptLimit; $reuseAttempt++) {
            try {
                $reuseContext = New-VerifierRunContext $repositoryRoot $rollbackRoot
                $reusePort = 0
                $reuseOldLease = New-GateBLeaseAfterExactPortReselection `
                    $reuseContext ([ref]$reusePort)
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
                $reuseNewLease = New-GateBCanaryLease $reuseNewContext $reusePort
                $reuseNewProcess = Start-GateBSeparateListener $reusePort
                $reuseNewStartTicks = [long](Get-VerifierProcessStartTicks $reuseNewProcess)
                $reuseOwner = [pscustomobject]@{
                    DirectProcessOwner = $true
                    Process = $reuseNewProcess
                    ProcessId = [int]$reuseNewProcess.Id
                    ProcessStartTicks = $reuseNewStartTicks
                    IdentityProof = 'retained-process-object-v1'
                }
                Confirm-VerifierPortLeaseBound $reuseNewContext $reuseNewLease `
                    $reuseNewProcess.Id `
                    $reuseNewStartTicks $reuseOwner
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
                        [int]$_.ProcessId -eq [int]$reuseOwner.ProcessId -and
                        [long]$_.ProcessStartTicks -eq $reuseNewStartTicks
                    }).Count -gt 0) `
                    'newer reused-port listener was not preserved during old tombstone recovery'
                Stop-GateBExactProcess $reuseNewProcess $reusePort
                Set-GateBLeaseProcessProofAfterExactCleanup $reuseNewLease
                Assert-GateB ($reuseNewLease.ProcessProofRequired -and
                    $reuseNewLease.ProcessTerminationProven -and
                    $reuseNewLease.ProcessAbsent) `
                    'newer reused-port lease did not retain exact child termination/absence proof'
                $reuseNewProcess = $null
                $newRetry = Complete-VerifierRun $reuseNewContext
                Assert-GateB ($newRetry -and [bool]$newRetry.Success) `
                    'newer reused-port lease could not complete after old tombstone recovery'
                Assert-GateBContextResourcesReleased $reuseContext
                Assert-GateBContextResourcesReleased $reuseNewContext
                $checksValidated = $true
                $reuseAttemptSucceeded = $true
                break
            } catch {
                if (-not (Test-GateBPortSelectionRace $_) -or
                        $reuseAttempt -ge $reuseAttemptLimit) {
                    throw
                }
                $retryCleanup = Complete-GateBPortReuseAttemptCleanup `
                    $reuseContext $reuseNewContext $reuseNewLease $reuseNewProcess `
                    $reuseListener $reusePort
                if (-not [bool]$retryCleanup.Success) {
                    Throw-GateBInfrastructure ('port-reselection attempt cleanup was not proven: ' +
                        (@($retryCleanup.Errors) -join '; '))
                }
                Write-Host ('INFO: transactional rollback port-selection race on attempt ' +
                    [string]$reuseAttempt + '; exact cleanup proven, selecting a fresh port.')
                $reuseContext = $null
                $reuseNewContext = $null
                $reuseOldLease = $null
                $reuseNewLease = $null
                $reuseNewProcess = $null
                $reuseListener = $null
                $reusePort = 0
            }
        }
        if (-not $reuseAttemptSucceeded) {
            Throw-GateBInfrastructure ('transactional rollback port-reselection exhausted after ' +
                [string]$reuseAttemptLimit + ' attempts.')
        }
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
        if ($null -ne $reuseNewProcess) {
            try { Stop-GateBExactProcess $reuseNewProcess $reusePort } catch {
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
        $lease = New-GateBCanaryLease $context
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
        Assert-GateB (@($context.CleanupErrors).Count -gt 0) `
            'cleanup-failure retention canary did not retain the current failed-pass error evidence'

        $listener.Stop()
        $listener = $null
        $successfulCleanup = Complete-VerifierRun $context
        Assert-GateB ($null -ne $successfulCleanup -and [bool]$successfulCleanup.Success) `
            'cleanup-failure retention canary could not complete after the listener was removed'
        Assert-GateB (@($successfulCleanup.Errors).Count -eq 0) `
            'cleanup retry returned stale or current errors after successful release'
        Assert-GateB ([string]$context.CleanupState -ceq 'complete' -and
            @($context.CleanupErrors).Count -eq 0) `
            'cleanup retry did not replace stale errors with an exact completed empty error ledger'
        Assert-GateB (-not (Test-Path -LiteralPath $lease.Path)) `
            'cleanup-failure retention canary left its claim after successful retry'
        $retryManifestJson = Get-Content -LiteralPath $context.ManifestPath -Raw
        $retryManifest = ConvertFrom-VerifierDurableJson $retryManifestJson
        Assert-GateB ([string]$retryManifest.cleanup.state -ceq 'complete' -and
            $retryManifest.cleanup.errors -is [array] -and
            @($retryManifest.cleanup.errors).Count -eq 0) `
            'cleanup retry manifest did not prove cleanupState=complete with cleanupErrors=[]'
        $retryLease = @($retryManifest.leases | Where-Object {
            [string]$_.leaseId -ceq [string]$lease.LeaseId
        })
        Assert-GateB ($retryLease.Count -eq 1 -and
            [string]$retryLease[0].status -ceq 'released' -and
            [string]$retryLease[0].claimState -ceq 'released' -and
            [string]$retryLease[0].releaseState -ceq 'complete' -and
            [string]$retryLease[0].releaseJournalState -ceq 'complete' -and
            [bool]$retryLease[0].mutexReleased) `
            'cleanup retry manifest did not retain the exact terminal lease release proof'
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
            $currentRecords = @(Get-VerifierProcessRecordsByIdWithFallback `
                ([int]$Process.Id) 'Gate B canary process identity')
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

function Wait-GateBPortQuiescence($Port) {
    if (-not (Test-VerifierStrictIntegralValue $Port 1 65535)) {
        Throw-GateBInfrastructure 'canary port quiescence received an invalid port.'
    }
    $maxAttempts = 10
    $delayMilliseconds = 100
    $lastDetail = 'no listener observation was captured'
    for ($attempt = 1; $attempt -le $maxAttempts; $attempt++) {
        try {
            $inspection = Get-VerifierLoopbackListenerRecords ([int]$Port)
            $inspectionShapeValid = $null -ne $inspection -and
                $inspection -isnot [array] -and
                $null -ne $inspection.PSObject.Properties['Success'] -and
                $null -ne $inspection.PSObject.Properties['Known'] -and
                $null -ne $inspection.PSObject.Properties['HasListeners'] -and
                (Test-VerifierStrictBooleanValue $inspection.Success) -and
                (Test-VerifierStrictBooleanValue $inspection.Known) -and
                (Test-VerifierStrictBooleanValue $inspection.HasListeners)
            if ($inspectionShapeValid) {
                if ($inspection.Success -and $inspection.Known -and
                        -not $inspection.HasListeners) {
                    return $inspection
                }
                $lastDetail = 'success=' + [string]$inspection.Success +
                    ', known=' + [string]$inspection.Known +
                    ', hasListeners=' + [string]$inspection.HasListeners
            } else {
                $lastDetail = 'listener inspection was missing or malformed'
            }
        } catch {
            $lastDetail = Get-VerifierErrorMessage $_
        }
        if ($attempt -lt $maxAttempts) {
            Start-Sleep -Milliseconds $delayMilliseconds
        }
    }
    Throw-GateBInfrastructure ('canary port ' + [string]$Port +
        ' did not reach a proven quiescent state after ' + [string]$maxAttempts +
        ' bounded observations: ' + $lastDetail)
}

function ConvertTo-GateBPowerShellLiteral([string]$Value) {
    return [string][char]39 + $Value.Replace([string][char]39, ([string][char]39 +
        [string][char]39)) + [string][char]39
}

function Start-GateBRedirectedProcess($FilePath, $Arguments) {
    $invocation = Assert-VerifierProcessInvocationBoundary $FilePath $Arguments `
        'Gate B redirected process'
    $process = New-Object Diagnostics.Process
    try {
        $startInfo = New-Object Diagnostics.ProcessStartInfo
        $startInfo.FileName = $invocation.FilePath
        $startInfo.Arguments = ConvertTo-VerifierArgumentString $invocation.Arguments
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

function Invoke-GateBBoundedProcess($FilePath, $Arguments,
        $TimeoutMilliseconds = 30000, $Label = 'Gate B child') {
    if (-not (Test-VerifierStrictStringValue $Label) -or
            [String]::IsNullOrWhiteSpace($Label)) {
        Throw-GateBInfrastructure 'Gate B bounded-process label must be an exact non-empty string.'
    }
    $invocation = Assert-VerifierProcessInvocationBoundary $FilePath $Arguments `
        $Label
    if (-not (Test-VerifierStrictIntegralValue $TimeoutMilliseconds 1L ([int]::MaxValue))) {
        Throw-GateBInfrastructure "$Label timeout must be an exact positive integral value before process start."
    }
    $TimeoutMilliseconds = [int]$TimeoutMilliseconds
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
        $process = Start-GateBRedirectedProcess $invocation.FilePath $invocation.Arguments
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
    $powershell = (Get-Command powershell.exe -ErrorAction Stop).Source
    Invoke-GateBProcessInvocationBoundaryCanary $powershell
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

function Invoke-GateBProcessInvocationBoundaryCanary([string]$Powershell) {
    $malformedCases = @(
        [pscustomobject]@{ Name = 'null path'; FilePath = $null; Arguments = @() }
        [pscustomobject]@{ Name = 'Boolean path'; FilePath = $true; Arguments = @() }
        [pscustomobject]@{ Name = 'array path'; FilePath = [object[]]@($Powershell); Arguments = @() }
        [pscustomobject]@{ Name = 'null arguments'; FilePath = $Powershell; Arguments = $null }
        [pscustomobject]@{ Name = 'scalar arguments'; FilePath = $Powershell; Arguments = '-NoProfile' }
        [pscustomobject]@{ Name = 'Boolean arguments'; FilePath = $Powershell; Arguments = $true }
        [pscustomobject]@{ Name = 'Boolean argument element'; FilePath = $Powershell; Arguments = [object[]]@('-NoProfile', $true) }
        [pscustomobject]@{ Name = 'object argument element'; FilePath = $Powershell; Arguments = [object[]]@('-NoProfile', [pscustomobject]@{}) }
    )
    $processRoot = Join-Path ([IO.Path]::GetTempPath()) 'TroubleshootJS'
    $beforeProcessLogs = if (Test-Path -LiteralPath $processRoot -PathType Container) {
        @((Get-ChildItem -LiteralPath $processRoot -Directory -Filter 'verify-process-*' -ErrorAction Stop).Name)
    } else { @() }
    foreach ($case in $malformedCases) {
        foreach ($wrapper in @(
                [pscustomobject]@{ Name = 'module Start'; Invoke = {
                    param($item) [void](Start-VerifierProcess $item.FilePath $item.Arguments)
                }}
                [pscustomobject]@{ Name = 'module bounded'; Invoke = {
                    param($item) [void](Invoke-VerifierBoundedProcess $item.FilePath $item.Arguments 1000)
                }}
                [pscustomobject]@{ Name = 'Gate B Start'; Invoke = {
                    param($item) [void](Start-GateBRedirectedProcess $item.FilePath $item.Arguments)
                }}
                [pscustomobject]@{ Name = 'Gate B bounded'; Invoke = {
                    param($item) [void](Invoke-GateBBoundedProcess $item.FilePath $item.Arguments 1000 'raw process boundary canary')
                }}
            )) {
            $rejected = $false
            try { & $wrapper.Invoke $case } catch {
                $rejected = Test-VerifierInfrastructureError $_
            }
            Assert-GateB $rejected `
                "$($wrapper.Name) accepted malformed $($case.Name) input"
        }
    }
    $afterProcessLogs = if (Test-Path -LiteralPath $processRoot -PathType Container) {
        @((Get-ChildItem -LiteralPath $processRoot -Directory -Filter 'verify-process-*' -ErrorAction Stop).Name)
    } else { @() }
    Assert-GateB ((@($beforeProcessLogs) -join '|') -eq (@($afterProcessLogs) -join '|')) `
        'malformed process invocation inputs created bounded-process filesystem evidence'
    Write-Host 'PASS:raw process path/argument arrays reject malformed values before process start or log-root mutation'
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
    # Win32_Process and the native fallback both expose a real retained
    # process's PID/command-line tuple without a ProcessStartTicks property.
    # Keep this WMI-shaped snapshot separate from the PID-only negative below:
    # the retained System.Diagnostics.Process re-read remains the source of
    # exact start identity.
    $wmiShapedPreviewSnapshot = @([pscustomobject]@{
        ProcessId = [int]$PID; ParentProcessId = 1
        Name = 'powershell.exe'; ExecutablePath = ''
        CommandLine = $previewCommand
    })
    Assert-GateB (-not $wmiShapedPreviewSnapshot[0].PSObject.Properties['ProcessStartTicks']) `
        'WMI-shaped preview canary unexpectedly fabricated a snapshot start identity'
    Assert-GateB (Test-VerifierPreviewProcessIdentity $previewRecord `
        $wmiShapedPreviewSnapshot) `
        'retained preview identity rejected a WMI/native snapshot without ProcessStartTicks'
    Assert-GateB (-not (Test-VerifierPreviewProcessIdentity $previewRecord @(
        [pscustomobject]@{ ProcessId = $PID }
    ))) `
        'preview identity accepted a PID-only snapshot without command or start identity'
    Assert-GateB (-not (Test-VerifierPreviewProcessIdentity $previewRecord @(
        [pscustomobject]@{ ProcessId = [int]$PID; ParentProcessId = 1
            Name = 'powershell.exe'; CommandLine = '' }
    ))) `
        'preview identity accepted a WMI-shaped snapshot with an empty command line'
    foreach ($malformedStart in @($null, 0L, 'not-a-start')) {
        $malformedNoStart = [pscustomobject]@{
            ProcessId = [int]$PID; ParentProcessId = 1
            Name = 'powershell.exe'; ExecutablePath = ''
            CommandLine = $previewCommand; ProcessStartTicks = $malformedStart
        }
        Assert-GateB (-not (Test-VerifierPreviewProcessIdentity $previewRecord `
            @($malformedNoStart))) `
            'preview identity accepted a malformed snapshot ProcessStartTicks value'
    }
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

function Invoke-GateBIsolationCanary([switch]$SkipArgumentPathCanary) {
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
        $verifierModule = @(Get-Module VerifierIsolation | Select-Object -First 1)
        if ($verifierModule.Count -ne 1) {
            Throw-GateBInfrastructure 'isolation canary could not enter the verifier containment launch boundary'
        }
        if (-not $SkipArgumentPathCanary) {
            Invoke-GateBArgumentPathCanary $canaryRoot
        }
        try { [void](Get-VerifierProcessSnapshotWithFallback 'isolation canary') } catch {
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
        $recordA = $null
        [IO.File]::WriteAllText($rootScript,
            [string]::Join([Environment]::NewLine, @(
                'Option Explicit'
                'WScript.Sleep 120000'
            )), [Text.UTF8Encoding]::new($false))
        Assert-VerifierNoReparseAncestors $rootScript
        if (-not (Test-VerifierPhysicalChildPath $canaryRoot $rootScript)) {
            Throw-GateBInfrastructure 'isolation browser-root fixture escaped its exact canary namespace'
        }
        # Bind a real loopback listener while the named claim is held, then
        # validate that the claim records the actual owning process identity.
        # The free-port probe and the later bind are separate OS operations;
        # retry only the exact foreign-listener/address-in-use race, releasing
        # the exact claim only after the complete current port observation is
        # positively absent.
        $initialBindMaxAttempts = 8
        $initialBindSucceeded = $false
        $initialBindLastRace = ''
        for ($initialBindAttempt = 1;
                $initialBindAttempt -le $initialBindMaxAttempts;
                $initialBindAttempt++) {
            $candidateLease = $null
            $candidateListener = $null
            $candidatePort = 0
            try {
                # New-VerifierPortLease holds the exact global claim through
                # its own availability probe.  The remaining gap is the
                # canary's external bind below, which is the bounded race
                # handled by this loop.
                $candidateLease = New-GateBCanaryLease $contextA
                $candidatePort = [int]$candidateLease.Port
                Assert-GateB ($candidateLease.ClaimState -eq 'held') `
                    'new port claim was not retained before bind'
                Assert-GateB (Test-Path -LiteralPath $candidateLease.Path -PathType Leaf) `
                    'retained port claim record was not created'
                $candidateListener = [Net.Sockets.TcpListener]::new(
                    [Net.IPAddress]::Loopback, $candidateLease.Port)
                $candidateListener.Start()
                $initialOwnerProcess = Get-Process -Id ([int]$PID) -ErrorAction Stop
                if ($null -eq $initialOwnerProcess -or
                        $initialOwnerProcess -is [array] -or
                        -not ($initialOwnerProcess -is [Diagnostics.Process]) -or
                        -not (Test-VerifierStrictIntegralValue $initialOwnerProcess.Id `
                            1 ([int]::MaxValue)) -or
                        [int]$initialOwnerProcess.Id -ne [int]$PID) {
                    Throw-GateBInfrastructure 'initial bind owner was not the exact current Diagnostics.Process object.'
                }
                $initialOwnerStartTicks = [long](Get-VerifierProcessStartTicks `
                    $initialOwnerProcess)
                $currentOwnerStartTicks = [long](Get-VerifierCurrentProcessStartTicks)
                if ([bool]$initialOwnerProcess.HasExited -or
                        -not (Test-VerifierStrictIntegralValue $initialOwnerStartTicks 1) -or
                        $initialOwnerStartTicks -ne $currentOwnerStartTicks) {
                    Throw-GateBInfrastructure 'initial bind owner process identity changed before confirmation.'
                }
                $initialOwner = [pscustomobject]@{
                    DirectProcessOwner = $true
                    Process = $initialOwnerProcess
                    ProcessId = [int]$initialOwnerProcess.Id
                    ProcessStartTicks = $initialOwnerStartTicks
                    IdentityProof = 'retained-process-object-v1'
                }
                Confirm-VerifierPortLeaseBound $contextA $candidateLease $PID `
                    $currentOwnerStartTicks $initialOwner
                $leaseA = $candidateLease
                $bindListener = $candidateListener
                $candidateLease = $null
                $candidateListener = $null
                $initialBindSucceeded = $true
                break
            } catch {
                $race = Test-GateBInitialBindSelectionRace $_
                $raceMessage = Get-VerifierErrorMessage $_
                if ($null -ne $candidateLease -or $null -ne $candidateListener) {
                    $attemptCleanup = Complete-GateBInitialBindAttemptCleanup `
                        $contextA $candidateLease $candidateListener $candidatePort
                    if (-not $attemptCleanup.Success) {
                        Throw-GateBInfrastructure ('initial isolation bind race cleanup was not proven; ' +
                            'evidence was retained at ' + $canaryRoot + ': ' +
                            (@($attemptCleanup.Errors) -join '; '))
                    }
                }
                if (-not $race -or $initialBindAttempt -ge $initialBindMaxAttempts) {
                    if ($race) {
                        Throw-GateBInfrastructure ('initial isolation bind port-reselection exhausted after ' +
                            [string]$initialBindMaxAttempts + ' attempts: ' + $raceMessage)
                    }
                    throw
                }
                $initialBindLastRace = $raceMessage
                Write-Host ('INFO: initial isolation bind port-selection race on attempt ' +
                    [string]$initialBindAttempt + '; exact cleanup proven, selecting a fresh port.')
                Start-Sleep -Milliseconds 25
            }
        }
        if (-not $initialBindSucceeded) {
            Throw-GateBInfrastructure ('initial isolation bind did not complete after ' +
                [string]$initialBindMaxAttempts + ' attempts: ' + $initialBindLastRace)
        }
        $recordA = New-GateBCanaryBrowserRecord $contextA $leaseA `
            'canary-timeout-cleanup-A' $browserPath
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
        [void](Wait-GateBPortQuiescence $leaseA.Port)

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
        $browserLiteral = ConvertTo-GateBPowerShellLiteral $browserPath
        $competitionCommand = [string]::Join(' ', @(
            '$ErrorActionPreference = ''Stop'';'
            ('Import-Module ' + $moduleLiteral + ' -Force;')
            '$childExit = 2; $childContext = $null; $competingLease = $null;'
            '$childCleanupErrors = New-Object Collections.ArrayList; $childCleanup = $null;'
            '$childCleanupJsonPath = ''''; $childRunRoot = '''';'
            'try { $childContext = New-VerifierRunContext ' + $worktreeLiteral + ' '''';'
            ' $competingLease = New-VerifierPortLease $childContext ''cdp'' ' +
                [string]$leaseA.Port + ' ' + $browserLiteral + ';'
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
            ' $portInspection = $null; $portInspectionSuccess = $false;'
            ' $portInspectionKnown = $false; $portInspectionHasListeners = $true;'
            ' $portQuiescenceProven = $false; $portInspectionDetail = ''not observed'';'
            ' for ($portAttempt = 1; $portAttempt -le 10; $portAttempt++) {'
            '  try { $candidateInspection = Get-VerifierLoopbackListenerRecords ' +
                [string]$leaseA.Port + ';'
            '   $inspectionShapeValid = $null -ne $candidateInspection -and'
            '    $candidateInspection -isnot [array] -and'
            '    $null -ne $candidateInspection.PSObject.Properties[''Success''] -and'
            '    $null -ne $candidateInspection.PSObject.Properties[''Known''] -and'
            '    $null -ne $candidateInspection.PSObject.Properties[''HasListeners''] -and'
            '    (Test-VerifierStrictBooleanValue $candidateInspection.Success) -and'
            '    (Test-VerifierStrictBooleanValue $candidateInspection.Known) -and'
            '    (Test-VerifierStrictBooleanValue $candidateInspection.HasListeners);'
            '   if ($inspectionShapeValid) {'
            '    $portInspection = $candidateInspection;'
            '    $portInspectionSuccess = [bool]$candidateInspection.Success;'
            '    $portInspectionKnown = [bool]$candidateInspection.Known;'
            '    $portInspectionHasListeners = [bool]$candidateInspection.HasListeners;'
            '    $portInspectionDetail = ''success='' + [string]$portInspectionSuccess +'
            '     '', known='' + [string]$portInspectionKnown + '', hasListeners='' +'
            '     [string]$portInspectionHasListeners;'
            '    if ($candidateInspection.Success -and $candidateInspection.Known -and'
            '        -not $candidateInspection.HasListeners) { $portQuiescenceProven = $true; break }'
            '   } else { $portInspectionDetail = ''listener inspection was missing or malformed'' }'
            '  } catch { $portInspection = $null; $portInspectionSuccess = $false;'
            '   $portInspectionKnown = $false; $portInspectionHasListeners = $true;'
            '   $portInspectionDetail = $_.Exception.Message }'
            '  if ($portAttempt -lt 10) { Start-Sleep -Milliseconds 100 }'
            ' }'
            ' if (-not $portQuiescenceProven) { [void]$childCleanupErrors.Add(''port-quiescence: complete current absence was not proven after 10 bounded observations: '' + $portInspectionDetail) };'
            ' $childCleanupSuccess = ($childCleanupErrors.Count -eq 0 -and $childCleanup -and [bool]$childCleanup.Success -and'
            ' $childManifest.cleanup.state -eq ''complete'' -and @($childManifest.cleanup.errors).Count -eq 0 -and'
            ' @($childClaims | Where-Object { $_.status -ne ''released'' }).Count -eq 0 -and'
            ' $portQuiescenceProven -and $portInspectionSuccess -and $portInspectionKnown -and'
            ' -not $portInspectionHasListeners);'
            ' if (-not $childCleanupSuccess) { $childExit = 2; [void]$childCleanupErrors.Add(''manifest/port cleanup proof failed'') }'
            ' $childCleanupRecord = [pscustomobject]@{ protocol = ''troubleshootjs-child-cleanup-v1''; success = [bool]$childCleanupSuccess;'
            ' runRoot = $childRunRoot; manifestPath = $childContext.ManifestPath; leaseLedger = $childClaims;'
            ' claimPaths = @($childClaims | Where-Object { $_.status -ne ''released'' } | ForEach-Object { $_.path }); profiles = $childProfiles;'
            ' requestedPort = ' + [string]$leaseA.Port + '; requestedPortInspectionSuccess = [bool]$portInspectionSuccess;'
            ' requestedPortKnown = [bool]$portInspectionKnown; requestedPortHasListeners = [bool]$portInspectionHasListeners;'
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

        $leaseB = New-GateBCanaryLease $contextB
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
        $launchProcessIdA = & $verifierModule[0] {
            param($fixtureContext, $fixtureRecord, $fixtureBrowserPath,
                $fixtureArguments, $fixtureWorkingDirectory)
            $job = New-VerifierBrowserContainmentJob $fixtureContext $fixtureRecord
            $fixtureRecord.Runtime.ContainmentJob = $job
            Prepare-VerifierBrowserContainmentLaunch $fixtureContext $fixtureRecord $job
            $processId = Start-VerifierBrowserProcessInContainmentJob `
                -ContainmentJob $job -FilePath $fixtureBrowserPath `
                -Arguments $fixtureArguments -WorkingDirectory $fixtureWorkingDirectory
            Set-VerifierBrowserContainmentLaunch $fixtureContext $fixtureRecord $job $processId
            Write-VerifierManifest $fixtureContext
            return [int]$processId
        } $contextA $recordA $browserPath @(
            '//B', $rootScript,
            '--user-data-dir', $recordA.Profile,
            '--tsj-verifier-run', $contextA.RunId,
            '--tsj-verifier-worktree', $contextA.RepositoryIdentity,
            '--remote-debugging-port', [string]$recordA.CdpPort) $contextA.WorktreeRoot
        if (-not (Test-VerifierStrictIntegralValue $launchProcessIdA 1 ([int]::MaxValue))) {
            Throw-GateBInfrastructure 'isolation canary containment launch A did not return its exact root PID'
        }
        $rootProcessA = Get-Process -Id ([int]$launchProcessIdA) -ErrorAction Stop
        $recordA.Runtime.Browser = $rootProcessA
        $rootStartA = Get-VerifierProcessStartTicks $rootProcessA
        $rootIdentityA = Get-VerifierCurrentProcessIdentity $rootProcessA.Id $rootStartA `
            0 '' '' 0 $contextA.RunId '' 0 $browserPath
        $recordA.ProcessId = [int]$rootIdentityA.Record.ProcessId
        $recordA.ProcessStartTicks = [long]$rootIdentityA.Record.ProcessStartTicks
        $recordA.ProcessParentProcessId = [int]$rootIdentityA.Record.ParentProcessId
        $recordA.ProcessParentProcessStartTicks = [long]$rootIdentityA.Record.ParentProcessStartTicks
        $recordA.ProcessCommandLine = [string]$rootIdentityA.Record.CommandLine
        $recordA.Status = 'started'
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

        # The deliberate missing-root negative above leaves this synthetic
        # record in cleanup-failed/blocked state. Reconstruct only its original
        # issued, rootless fixture lifecycle after the foreign process is gone;
        # production recovery never clears a failed cleanup record this way.
        Assert-GateB ([int]$recordB.ProcessId -eq 0 -and
            [long]$recordB.ProcessStartTicks -eq 0 -and
            [int]$recordB.ProcessParentProcessId -eq 0 -and
            [long]$recordB.ProcessParentProcessStartTicks -eq 0 -and
            [string]$recordB.ProcessCommandLine -eq '' -and
            $recordB.RecoveryReceipt.State -eq 'issued' -and
            -not [bool]$recordB.RecoveryReceipt.CloseAttempted -and
            $recordB.ContainmentLaunch.State -eq 'unlaunched') `
            'foreign-profile negative did not retain the exact rootless issued fixture state'
        $recordB.Status = 'leased'
        $recordB.CleanupResult = 'pending'
        $recordB.Error = ''
        $recordB.Lease.ReleaseBlocked = $false
        $recordB.Lease.ReleaseBlockReason = ''
        $recordB.Lease.ProcessTerminationProven = $false
        $recordB.Lease.ProcessAbsent = $false
        Write-VerifierManifest $contextB

        # Restore B to a real, positively identified synthetic browser root
        # only after the stale/foreign-profile negative proof has completed.
        # This lets the positive cleanup path satisfy the root-gone contract
        # without weakening the required fail-closed behavior for a missing
        # recorded root.
        $launchProcessIdB = & $verifierModule[0] {
            param($fixtureContext, $fixtureRecord, $fixtureBrowserPath,
                $fixtureArguments, $fixtureWorkingDirectory)
            $job = New-VerifierBrowserContainmentJob $fixtureContext $fixtureRecord
            $fixtureRecord.Runtime.ContainmentJob = $job
            Prepare-VerifierBrowserContainmentLaunch $fixtureContext $fixtureRecord $job
            $processId = Start-VerifierBrowserProcessInContainmentJob `
                -ContainmentJob $job -FilePath $fixtureBrowserPath `
                -Arguments $fixtureArguments -WorkingDirectory $fixtureWorkingDirectory
            Set-VerifierBrowserContainmentLaunch $fixtureContext $fixtureRecord $job $processId
            Write-VerifierManifest $fixtureContext
            return [int]$processId
        } $contextB $recordB $browserPath @(
            '//B', $rootScript,
            '--user-data-dir', $recordB.Profile,
            '--tsj-verifier-run', $contextB.RunId,
            '--tsj-verifier-worktree', $contextB.RepositoryIdentity,
            '--remote-debugging-port', [string]$recordB.CdpPort) $contextB.WorktreeRoot
        if (-not (Test-VerifierStrictIntegralValue $launchProcessIdB 1 ([int]::MaxValue))) {
            Throw-GateBInfrastructure 'isolation canary containment launch B did not return its exact root PID'
        }
        $rootProcessB = Get-Process -Id ([int]$launchProcessIdB) -ErrorAction Stop
        $recordB.Runtime.Browser = $rootProcessB
        $rootStartB = Get-VerifierProcessStartTicks $rootProcessB
        $rootIdentityB = Get-VerifierCurrentProcessIdentity $rootProcessB.Id $rootStartB `
            0 '' '' 0 $contextB.RunId '' 0 $browserPath
        $recordB.ProcessId = [int]$rootIdentityB.Record.ProcessId
        $recordB.ProcessStartTicks = [long]$rootIdentityB.Record.ProcessStartTicks
        $recordB.ProcessParentProcessId = [int]$rootIdentityB.Record.ParentProcessId
        $recordB.ProcessParentProcessStartTicks = [long]$rootIdentityB.Record.ParentProcessStartTicks
        $recordB.ProcessCommandLine = [string]$rootIdentityB.Record.CommandLine
        $recordB.Status = 'started'
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
        $quotedCurrentRecord = Get-VerifierCurrentProcessRecordById ([int]$PID)
        if ($null -eq $quotedCurrentRecord -or
                $quotedCurrentRecord -is [array] -or
                $null -eq $quotedCurrentRecord.PSObject.Properties['ParentProcessId'] -or
                -not (Test-VerifierStrictIntegralValue `
                    $quotedCurrentRecord.PSObject.Properties['ParentProcessId'].Value `
                    1 ([int]::MaxValue))) {
            Throw-GateBInfrastructure `
                'quoted Windows command-line canary could not prove a positive current parent identity'
        }
        $quotedParentProcessId = [int]$quotedCurrentRecord.ParentProcessId
        $quotedSnapshot = @([pscustomobject]@{
            ProcessId = $PID; Name = [IO.Path]::GetFileName($recordB.BrowserPath)
            ParentProcessId = $quotedParentProcessId
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

function Invoke-GateBProcessIdentityPidZeroCanary() {
    $canaryRoot = Join-Path ([IO.Path]::GetTempPath()) `
        ('TroubleshootJS\gate-b-pid-zero-identity-' + [Guid]::NewGuid().ToString('N'))
    $fixtureScript = Join-Path $canaryRoot 'identity-fixture.vbs'
    $fixtureProfile = Join-Path $canaryRoot 'profile'
    $fixtureProcess = $null
    $fixtureIdentity = $null
    $fixtureRecord = $null
    $primaryFailure = $null
    $cleanupFailure = $null
    $cleanupProven = $false
    try {
        New-Item -ItemType Directory -Path $canaryRoot -Force -ErrorAction Stop | Out-Null
        New-Item -ItemType Directory -Path $fixtureProfile -Force -ErrorAction Stop | Out-Null
        [IO.File]::WriteAllText($fixtureScript, [string]::Join([Environment]::NewLine, @(
            'Option Explicit'
            'WScript.Sleep 60000'
        )), [Text.UTF8Encoding]::new($false))
        Assert-VerifierNoReparseAncestors $fixtureScript
        $browserPath = (Get-Command wscript.exe -ErrorAction Stop).Source
        $runId = 'gate-b-pid-zero-run-' + [Guid]::NewGuid().ToString('N')
        $repositoryIdentity = 'gate-b-pid-zero-worktree'
        $port = 49329
        $fixtureCommandArguments = @(
            '//B', $fixtureScript,
            '--user-data-dir', $fixtureProfile,
            '--tsj-verifier-run', $runId,
            '--tsj-verifier-worktree', $repositoryIdentity,
            '--remote-debugging-port', [string]$port)
        $fixtureProcess = Start-Process -FilePath $browserPath `
            -ArgumentList $fixtureCommandArguments -PassThru -WindowStyle Hidden -ErrorAction Stop
        $fixtureStartTicks = [long](Get-VerifierProcessStartTicks $fixtureProcess)
        $fixtureIdentity = Get-VerifierCurrentProcessIdentity $fixtureProcess.Id $fixtureStartTicks `
            0 '' '' 0 $runId '' 0 $browserPath
        $fixtureRecord = [pscustomobject]@{
            ProcessId = [int]$fixtureIdentity.Record.ProcessId
            ProcessStartTicks = [long]$fixtureIdentity.Record.ProcessStartTicks
            ProcessParentProcessId = [int]$fixtureIdentity.Record.ParentProcessId
            ProcessParentProcessStartTicks = [long]$fixtureIdentity.Record.ParentProcessStartTicks
            Profile = $fixtureProfile
            RunId = $runId
            RepositoryIdentity = $repositoryIdentity
            CdpPort = $port
            BrowserPath = $browserPath
        }

        # Read the actual complete Win32_Process snapshot. This focused
        # regression deliberately requires WMI because native Toolhelp does
        # not expose the PID-0/System Idle record that caused the production
        # failure. The real PID-0 tuple must be ignored only as this exact
        # out-of-band record, while the positively identified WSH target
        # remains fully validated.
        $completeSnapshot = @(Get-CimInstance Win32_Process -ErrorAction Stop)
        $idleRecords = @($completeSnapshot | Where-Object {
            $null -ne $_ -and
            $null -ne $_.PSObject.Properties['ProcessId'] -and
            $null -ne $_.PSObject.Properties['ParentProcessId'] -and
            $null -ne $_.PSObject.Properties['Name'] -and
            $null -ne $_.PSObject.Properties['CommandLine'] -and
            (Test-VerifierStrictIntegralValue $_.ProcessId 0 0) -and
            (Test-VerifierStrictIntegralValue $_.ParentProcessId 0 0) -and
            (Test-VerifierStrictStringValue $_.Name) -and
            $_.Name -ceq 'System Idle Process' -and
            ($null -eq $_.CommandLine -or
                ((Test-VerifierStrictStringValue $_.CommandLine) -and $_.CommandLine -ceq ''))
        })
        Assert-GateB ($idleRecords.Count -eq 1) `
            'real complete Win32_Process snapshot did not contain exactly one PID-0 System Idle record'
        $targetRecords = @($completeSnapshot | Where-Object {
            $null -ne $_ -and $null -ne $_.PSObject.Properties['ProcessId'] -and
            (Test-VerifierStrictIntegralValue $_.ProcessId 1 ([int]::MaxValue)) -and
            [int]$_.ProcessId -eq [int]$fixtureRecord.ProcessId
        })
        Assert-GateB ($targetRecords.Count -eq 1) `
            'real complete Win32_Process snapshot did not contain the exact WSH target'
        Assert-GateB (Test-VerifierProcessIdentity $fixtureRecord $completeSnapshot) `
            'exact WSH target identity was rejected when the real complete snapshot contained PID-0 System Idle'
        foreach ($invalidRecord in @(
                [pscustomobject]@{ ProcessId = '0'; ParentProcessId = 0; Name = 'System Idle Process'; CommandLine = ''; ExecutablePath = '' }
                [pscustomobject]@{ ProcessId = 0; ParentProcessId = 1; Name = 'System Idle Process'; CommandLine = ''; ExecutablePath = '' }
                [pscustomobject]@{ ProcessId = 0; ParentProcessId = 0; Name = 'system idle process'; CommandLine = ''; ExecutablePath = '' }
                [pscustomobject]@{ ProcessId = 0; ParentProcessId = 0; Name = 'foreign.exe'; CommandLine = ''; ExecutablePath = '' }
                [pscustomobject]@{ ProcessId = 0; ParentProcessId = 0; Name = 'System Idle Process'; CommandLine = 'foreign'; ExecutablePath = '' }
                [pscustomobject]@{ ProcessId = -1; ParentProcessId = 0; Name = 'System Idle Process'; CommandLine = ''; ExecutablePath = '' }
            )) {
            Assert-GateB (-not (Test-VerifierProcessIdentity $fixtureRecord @(
                $invalidRecord, $targetRecords[0]))) `
                'malformed/non-idle PID-0 snapshot record passed exact identity validation'
        }
        [void](Stop-VerifierVerifiedProcessExactly $fixtureIdentity.Process `
            ([long]$fixtureIdentity.Record.ProcessStartTicks) 5000 $fixtureIdentity.Record)
        $fixtureProcess = $null
        $cleanupProven = $true
        Write-Host 'PASS:real complete Win32_Process PID-0 System Idle exception and malformed tuple rejection'
    } catch {
        $primaryFailure = $_
    } finally {
        if ($null -ne $fixtureProcess) {
            try {
                if ($null -ne $fixtureIdentity) {
                    [void](Stop-VerifierVerifiedProcessExactly $fixtureIdentity.Process `
                        ([long]$fixtureIdentity.Record.ProcessStartTicks) 5000 $fixtureIdentity.Record)
                    $fixtureProcess = $null
                }
            } catch {
                $cleanupFailure = Get-VerifierErrorMessage $_
            }
        }
        if ($null -eq $primaryFailure -and $null -eq $cleanupFailure -and $cleanupProven -and
                (Test-Path -LiteralPath $canaryRoot)) {
            try {
                Remove-VerifierOwnedTree (Get-VerifierFullPath ([IO.Path]::GetTempPath())) $canaryRoot
            } catch {
                $cleanupFailure = Get-VerifierErrorMessage $_
            }
        }
    }
    if ($null -ne $cleanupFailure) {
        Throw-GateBInfrastructure ('PID-0 identity canary cleanup was not proven; evidence was retained at ' +
            $canaryRoot + ': ' + $cleanupFailure)
    }
    if ($null -ne $primaryFailure) {
        if (Test-VerifierInfrastructureError $primaryFailure) { throw $primaryFailure }
        Throw-GateBInfrastructure ('PID-0 identity canary failed; evidence was retained at ' +
            $canaryRoot + ': ' + (Get-VerifierErrorMessage $primaryFailure))
    }
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
$childLease = New-VerifierPortLease $childContext 'cdp' 0 ((Get-Command powershell.exe -ErrorAction Stop).Source)
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
        $leaseB = New-GateBCanaryLease $contextB
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

function Invoke-GateBSourceNegativeContractCheck() {
    $shell = (Get-Command powershell.exe -ErrorAction Stop).Source
    foreach ($probe in @('-ContractProbe', '-IdentityCanary', '-MutationPreflight')) {
        $result = Invoke-GateBBoundedProcess $shell @('-NoLogo', '-NoProfile',
            '-NonInteractive', '-File',
            (Join-Path $PSScriptRoot 'verify-task43p-source-experiments.ps1'), $probe,
            '-ExpectedCandidateSha', $script:GateBCandidateSha) `
            30000 ('source-negative ' + $probe)
        $exitCode = Resolve-GateBChildExitCode $result ('source-negative ' + $probe)
        if ($exitCode -ne 0) {
            Throw-GateBInfrastructure ("Source-negative $probe returned exit ${exitCode}: " +
                $result.Stdout + ' ' + $result.Stderr)
        }
    }
    $candidateCheck = Invoke-GateBBoundedProcess $shell @('-NoLogo', '-NoProfile',
        '-NonInteractive', '-File', (Join-Path $PSScriptRoot 'verify-task43p-candidate-identity.ps1')) `
        30000 'candidate identity regressions'
    if ((Resolve-GateBChildExitCode $candidateCheck 'candidate identity regressions') -ne 0) {
        Throw-GateBInfrastructure ('Candidate identity regressions failed: ' +
            $candidateCheck.Stdout + ' ' + $candidateCheck.Stderr)
    }
    Write-Host 'PASS:source-negative complete proof, late-failure, cleanup, and preview-identity contracts'
}

function Invoke-GateBTask43PRuntimeCaptureCanary() {
    $tokens = $null; $parseErrors = $null
    $ast = [Management.Automation.Language.Parser]::ParseFile(
        (Join-Path $PSScriptRoot 'verify-browser.ps1'), [ref]$tokens, [ref]$parseErrors)
    Assert-GateB ($parseErrors.Count -eq 0) 'runtime capture source did not parse'
    foreach ($name in @('Capture-Task43PEvidence', 'getVerifierEvidencePath',
            'Write-VerifierEvidenceText', 'captureBrowserScreenshot', 'Write-VerifierEvidenceBytes')) {
        $definitions = @($ast.EndBlock.Statements | Where-Object {
            $_ -is [Management.Automation.Language.FunctionDefinitionAst] -and $_.Name -ceq $name
        })
        Assert-GateB ($definitions.Count -eq 1) "runtime capture helper '$name' was not unique"
        . ([scriptblock]::Create($definitions[0].Extent.Text))
    }
    . (Join-Path $PSScriptRoot 'Task43PRuntimeEvidence.ps1')
    # Only transport/base admission and manifest registration are doubles. The
    # actual capture, runtime schema, filename guard, and file writer execute.
    function evaluateCdp($Socket, [ref]$Id, $Expression, [ref]$Failures, $Deadline) {
        return $Socket.Dequeue()
    }
    function invokeCdp($Socket, [ref]$Id, $Method, $Parameters, [ref]$Failures) {
        Assert-GateB ($Method -ceq 'Page.captureScreenshot' -and $Parameters.format -ceq 'png') `
            'screenshot capture used an unexpected transport command'
        return $Socket.Dequeue()
    }
    function Assert-Task43PJavaEvidenceProvenance($Value) { }
    function Assert-Task43PJavaEvidencePayload($Value, $RouteName, $Expected, $Observed) { }
    function Register-VerifierEvidenceArtifact($Context, $Path) { [void]$Context.Artifacts.Add($Path) }
    $saved = @{}
    foreach ($name in @('VerifierContext', 'VerifierCurrentRouteId',
            'VerifierEvidenceDirectory', 'Task43PRuntimeOutcome')) {
        $variable = Get-Variable $name -Scope Script -ErrorAction SilentlyContinue
        $saved[$name] = [pscustomobject]@{ Exists = $null -ne $variable
            Value = $(if ($null -ne $variable) { $variable.Value } else { $null }) }
    }
    $canaryRoot = Join-Path ([IO.Path]::GetTempPath()) `
        ('TroubleshootJS-gate-b-runtime-capture-' + [Guid]::NewGuid().ToString('N'))
    try {
        New-Item -ItemType Directory -Path $canaryRoot -ErrorAction Stop | Out-Null
        $Task43PRuntime = $true
        foreach ($case in @('schema', 'malformed', 'write-failure')) {
            $script:VerifierContext = [pscustomobject]@{
                RunId = ('a' * 32); Artifacts = [Collections.ArrayList]::new() }
            $script:VerifierCurrentRouteId = [Guid]::NewGuid().ToString('N')
            $script:VerifierEvidenceDirectory = $canaryRoot
            $script:Task43PRuntimeOutcome = $null
            $value = New-Task43PRuntimeContractProbeValue `
                $script:VerifierContext.RunId $script:VerifierCurrentRouteId
            $value.measurement.cases[0].activeMeasurementOverlay = $true
            $raw = $value | ConvertTo-Json -Depth 100
            $signature = 'cleanup/disposition contradicted'
            if ($case -ceq 'malformed') { $raw = '{"protocol":'; $signature = 'published invalid JSON' }
            if ($case -ceq 'write-failure') {
                $script:VerifierEvidenceDirectory = Join-Path $canaryRoot 'absent-directory'
            }
            $socket = [Collections.Queue]::new(); $socket.Enqueue('{}'); $socket.Enqueue($raw)
            $counter = 1; $failures = @(); $rejection = $null
            try {
                Capture-Task43PEvidence $socket ([ref]$counter) ([DateTime]::UtcNow.AddSeconds(10)) `
                    ([ref]$failures) 'runtime capture canary' 'OBSERVED:task43p-runtime' `
                    'OBSERVED:task43p-runtime' $null
            } catch { $rejection = $_ }
            Assert-GateB ($null -ne $rejection -and (Test-VerifierInfrastructureError $rejection) -and
                (Get-VerifierErrorMessage $rejection).Contains($signature) -and
                $null -eq $script:Task43PRuntimeOutcome -and $socket.Count -eq 0) `
                "runtime capture $case lost the original typed rejection or assigned an outcome"
            if ($case -ceq 'write-failure') {
                Assert-GateB ($script:VerifierContext.Artifacts.Count -eq 0) `
                    'failed diagnostic write registered a nonexistent artifact'
            } else {
                $path = Join-Path $canaryRoot ('task43p-runtime-unvalidated-' +
                    $script:VerifierCurrentRouteId + '.json')
                Assert-GateB ($script:VerifierContext.Artifacts.Count -eq 1 -and
                    $script:VerifierContext.Artifacts[0] -ceq $path -and
                    [IO.File]::ReadAllText($path) -ceq $raw) `
                    "runtime capture $case did not preserve the exact rejected observation"
            }
        }
        # Exercise the actual command-mode call and byte writer under Windows
        # PowerShell, including typed failures that must register no artifact.
        $expectedBytes = [byte[]]@(137, 80, 78, 71, 13, 10, 26, 10)
        foreach ($case in @('bytes', 'invalid-base64', 'write-failure', 'existing-path')) {
            $script:VerifierContext = [pscustomobject]@{
                EvidenceDirectory = $canaryRoot; Artifacts = [Collections.ArrayList]::new() }
            $path = Join-Path $canaryRoot ('screenshot-' + $case + '.png')
            $encoded = [Convert]::ToBase64String($expectedBytes)
            if ($case -ceq 'invalid-base64') { $encoded = 'invalid!' }
            if ($case -ceq 'write-failure') { $path = Join-Path $canaryRoot 'absent-directory/screenshot.png' }
            if ($case -ceq 'existing-path') { [IO.File]::WriteAllBytes($path, [byte[]]@(42)) }
            $socket = [Collections.Queue]::new()
            $socket.Enqueue([pscustomobject]@{ result = [pscustomobject]@{ data = $encoded } })
            $counter = 1; $failures = @(); $rejection = $null
            try { captureBrowserScreenshot $socket ([ref]$counter) $path ([ref]$failures) }
            catch { $rejection = $_ }
            if ($case -ceq 'bytes') {
                Assert-GateB ($null -eq $rejection -and $socket.Count -eq 0 -and
                    $script:VerifierContext.Artifacts.Count -eq 1 -and
                    $script:VerifierContext.Artifacts[0] -ceq $path -and
                    [Convert]::ToBase64String([IO.File]::ReadAllBytes($path)) -ceq $encoded) `
                    'actual screenshot capture did not decode/write/register exact bytes'
            } else {
                Assert-GateB ($null -ne $rejection -and (Test-VerifierInfrastructureError $rejection) -and
                    $script:VerifierContext.Artifacts.Count -eq 0) `
                    "screenshot $case was not a typed infrastructure failure without an artifact"
                if ($case -ceq 'existing-path') {
                    Assert-GateB ($socket.Count -eq 1 -and
                        [Convert]::ToBase64String([IO.File]::ReadAllBytes($path)) -ceq 'Kg==') `
                        'existing screenshot was overwritten or transport ran before refusal'
                } else {
                    Assert-GateB (-not (Test-Path -LiteralPath $path)) 'failed screenshot write created a file'
                }
            }
        }
        Write-Host 'PASS:actual screenshot byte capture and invalid/failed/overwrite rejection paths'
        Write-Host 'PASS:runtime rejected observations retained without accepted outcomes; persistence failure preserves typed rejection'
    } finally {
        foreach ($name in $saved.Keys) {
            if ($saved[$name].Exists) { Set-Variable $name -Scope Script -Value $saved[$name].Value } else {
                Remove-Variable $name -Scope Script -ErrorAction SilentlyContinue
            }
        }
        if (Test-Path -LiteralPath $canaryRoot) {
            Remove-VerifierOwnedTree (Get-VerifierFullPath ([IO.Path]::GetTempPath())) $canaryRoot
        }
    }
}

function Invoke-GateBTask43PRuntimeEvidenceCheck() {
    Invoke-GateBTask43PRuntimeCaptureCanary
    $shell = (Get-Command powershell.exe -ErrorAction Stop).Source
    $result = Invoke-GateBBoundedProcess $shell @('-NoLogo', '-NoProfile',
        '-NonInteractive', '-File',
        (Join-Path $PSScriptRoot 'Task43PRuntimeEvidence.ps1'),
        '-Task43PRuntimeEvidenceContractProbe') 30000 'Task43P runtime evidence schema'
    $exitCode = Resolve-GateBChildExitCode $result 'Task43P runtime evidence schema'
    if ($exitCode -ne 0) {
        Throw-GateBInfrastructure ("Task43P runtime evidence schema returned exit ${exitCode}: " +
            $result.Stdout + ' ' + $result.Stderr)
    }
    foreach ($case in @(
            @('-Task43PRuntime'),
            @('-Task43P', '-Task43PRuntime', '-Seeds', '3'),
            @('-Task43P', '-Task43PRuntime', '-Task43PForcedNegative'),
            @('-Task43P', '-Task43PRuntime', '-NormalPlayer'),
            @('-Task43P', '-Task43PRuntime', '-Task43PFamily', 'led'))) {
        $result = Invoke-GateBBoundedProcess $shell (@('-NoLogo', '-NoProfile',
            '-NonInteractive', '-File', (Join-Path $PSScriptRoot 'verify-browser.ps1')) +
            $case) 10000 'Task43P runtime route rejection'
        $exitCode = Resolve-GateBChildExitCode $result 'Task43P runtime route rejection'
        Assert-GateB ($exitCode -eq 2 -and ($result.Stdout + $result.Stderr).Contains(
            'Task43P runtime requires its sole normal Task43P route')) `
            ('Task43P runtime route did not reject conflicting selection: ' + ($case -join ' '))
    }
    Write-Host 'PASS:Task43P runtime evidence schema, blocker classification, and fixed-corpus route contracts'
}

function Invoke-GateBDriver() {
    try {
        if ($GateBDriverInfrastructureProbe) {
            Throw-GateBInfrastructure 'deterministic driver infrastructure probe'
        }
        Invoke-GateBTimeoutBoundaryCanary
        Invoke-GateBStrictListenerDeadlineCanary
        if ($GateBNativeProcessInspectionProbe) {
            Invoke-GateBNativeProcessInspectionCanary
            return 0
        }
        if ($GateBStartPreviewAdoptionProbe) {
            Invoke-GateBStartPreviewAdoptionCanary
            return 0
        }
        if ($GateBIsolationProbe) {
            Invoke-GateBIsolationCanary -SkipArgumentPathCanary
            return 0
        }
        if ($GateBTcpListenerPreviewProbe) {
            Invoke-GateBTcpListenerPreviewCanary
            return 0
        }
        if ($GateBBrowserLeaseProbe) {
            Invoke-GateBBrowserLeaseConstructorCanary
            return 0
        }
        if ($GateBLeaseReleaseProbe) {
            Invoke-GateBLifecycleBooleanCanary
            Invoke-GateBLeaseRollbackCheck
            return 0
        }
        if ($GateBCleanupRetentionProbe) {
            Invoke-GateBCleanupRetentionCheck
            return 0
        }
        if ($GateBPreviewIdentityFailureProbe) {
            Invoke-GateBPreviewIdentityFailureCanary
            return 0
        }
        if ($GateBBrowserIdentityRetryProbe) {
            Invoke-GateBBrowserIdentityRetryCanary
            return 0
        }
        if ($GateBBrowserDescendantIdentityRetryProbe) {
            Invoke-GateBBrowserDescendantIdentityRetryCanary
            return 0
        }
        if ($GateBBrowserDrainNaturalExitProbe) {
            Invoke-GateBBrowserDrainNaturalExitCanary
            return 0
        }
        if ($GateBBrowserNaturalShutdownProbe) {
            Invoke-GateBBrowserContainmentJobCanary
            Invoke-GateBIssuedContainedBrowserRecoveryCanary
            Invoke-GateBIssuedContainedBrowserRecoveryCanary -LaunchLedgerPublication
            Invoke-GateBBoundReceiptRecoveryBoundaryCanary
            Invoke-GateBBrowserPrecloseAttestedDisappearanceCanary
            Invoke-GateBBrowserNaturalShutdownCanary
            return 0
        }
        if ($GateBBrowserContainmentProbe) {
            Invoke-GateBBrowserContainmentJobCanary
            return 0
        }
        if ($GateBListenerAuthorizationRetryProbe) {
            Invoke-GateBListenerAuthorizationRetryCanary
            return 0
        }
        if ($GateBDescendantSnapshotRefreshProbe) {
            Invoke-GateBDescendantSnapshotRefreshCanary
            return 0
        }
        if ($GateBBrowserRootListenerFastPathProbe) {
            Invoke-GateBBrowserRootListenerFastPathCanary
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
        if ($GateBCdpReferenceProbe) {
            Invoke-GateBCdpReferenceCanary
            return 0
        }
        if ($GateBTask43PRuntimeEvidenceProbe) {
            Invoke-GateBTask43PRuntimeEvidenceCheck
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
        if ($GateBEdgeDescendantCompatibilityProbe) {
            Invoke-GateBEdgeDescendantCompatibilityCanary
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
            Invoke-GateBDescendantCleanupCanary
            return 0
        }
        if ($GateBProcessIdentityPidZeroProbe) {
            Invoke-GateBProcessIdentityPidZeroCanary
            return 0
        }
        if ($GateBProcessStartIdentityProbe) {
            Invoke-GateBBoundedProcessLaunchRaceCanary
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
            Invoke-GateBIntegratedListenerConsumerCanary
            Invoke-GateBListenerRecordScalarCanary
            Invoke-GateBCallerIdentityScalarCanary
            Invoke-GateBNetstatPreferenceCanary
            return 0
        }
        if ($script:GateBCandidateSha -ceq '') {
            $script:GateBCandidateSha = Get-Task43PCandidateSha $repositoryRoot
        }
        Invoke-GateBParserChecks
        Invoke-GateBSourceChecks
        Invoke-GateBWorkflowChecks
        Invoke-GateBGwtModuleCheck
        Invoke-GateBModuleImportSetupCheck
        # Run the real short-lived netstat/launch-handle regression immediately
        # after module setup, before the larger mocked ownership matrix. The
        # focused ProcessStartIdentity probe uses the same callable canary.
        Invoke-GateBBoundedProcessLaunchRaceCanary
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
        Invoke-GateBBrowserIdentityRetryCanary
        Invoke-GateBBrowserDescendantIdentityRetryCanary
        Invoke-GateBListenerAuthorizationRetryCanary
        Invoke-GateBDescendantSnapshotRefreshCanary
        Invoke-GateBPreviewIdentityFailureCanary
        Invoke-GateBSetupFailureCheck
        Invoke-GateBNetstatPreferenceCanary
        Invoke-GateBListenerInspectionFailureCheck
        Invoke-GateBCdpHandshakeCanary
        Invoke-GateBCdpReferenceCanary
        Invoke-GateBBrowserContainmentJobCanary
        Invoke-GateBIssuedContainedBrowserRecoveryCanary
        Invoke-GateBIssuedContainedBrowserRecoveryCanary -LaunchLedgerPublication
        Invoke-GateBBoundReceiptRecoveryBoundaryCanary
        Invoke-GateBBrowserPrecloseAttestedDisappearanceCanary
        Invoke-GateBBrowserNaturalShutdownCanary
        Invoke-GateBDriverInfrastructureCheck
        if ($SkipJdkCheck) {
            Write-Host 'SKIP:JDK8 check explicitly requested for local static/isolation-only validation'
        } else {
            Invoke-GateBJdkCheck
        }
        Invoke-GateBExitContractChecks
        Invoke-GateBSourceNegativeContractCheck
        Invoke-GateBTask43PRuntimeEvidenceCheck
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
        [void](Get-Task43PCandidateSha $repositoryRoot $script:GateBCandidateSha)
        Write-Host ('PASS:Gate B deterministic checks candidate=' + $script:GateBCandidateSha)
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
