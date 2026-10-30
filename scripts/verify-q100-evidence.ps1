[CmdletBinding()]
param()

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
Import-Module (Join-Path $PSScriptRoot 'VerifierIsolation.psm1') -Force

# Exercise the exact maintained browser report readers without starting a
# preview, browser, or CircuitJS session. Fixtures exist only in memory.
$readerPath = Join-Path $PSScriptRoot 'verify-a03-browser.ps1'
$tokens = $null
$parseErrors = $null
$ast = [Management.Automation.Language.Parser]::ParseFile($readerPath, [ref]$tokens, [ref]$parseErrors)
if ($parseErrors.Count) { throw 'Q100 browser reader syntax error.' }
$readerNames = @(
    'Get-SafeText', 'Get-DiagnosticText',
    'Get-ExactReportText', 'Test-Q100FiniteNumber', 'Test-Q100Number',
    'Test-Q100TemporalBatchMetadata',
    'Get-Q100FaultOracle', 'Get-Q100FaultIds', 'ConvertFrom-Q100ReportText',
    'Test-Q100HealthyObservation', 'Test-Q100SeparatedLoadShortFaultRails',
    'Test-JsonReport', 'Test-A04Report', 'Test-Q100Report', 'Test-Q100PreflightReport',
    'Test-Q100InjectedFailureReport', 'Test-Q100InteractionLockReport', 'Get-Q100RecordField', 'Test-Q100HealthyMatrix', 'Test-Q100FaultMatrix',
    'Test-Q100PlayerDeveloperLeak', 'Test-NormalWorkbenchReady', 'Test-Q100PlayerPrivacy',
    'Test-Q100PlayerPrivacyCanaryEvidence',
    'Test-Q100PlayerArtifactMarkers', 'Get-Q100PlayerRouteDefinition', 'Test-RouteRequiresTicketEntry',
    'Test-RouteReady', 'Get-Q100RouteDefinitions', 'Get-RouteDefinitions'
)
foreach ($name in $readerNames) {
    $found = @($ast.FindAll({ param($node)
        $node -is [Management.Automation.Language.FunctionDefinitionAst] -and $node.Name -ceq $name
    }, $true))
    if ($found.Count -ne 1) { throw "Missing unique Q100 reader function $name" }
    . ([ScriptBlock]::Create($found[0].Extent.Text))
}

function Assert-Q100Reader([bool]$Condition, [string]$Label) {
    if (-not $Condition) { throw "Q100 reader contract failed: $Label" }
}

$script:A04ReaderAssertionCount = 0
function Assert-A04Reader([bool]$Condition, [string]$Label) {
    $script:A04ReaderAssertionCount++
    if (-not $Condition) { throw "A04 reader contract failed: $Label" }
}

function New-A04FixtureReport() {
    $cases = @()
    foreach ($seed in @('1', '2', '3')) {
        foreach ($route in @('resistive', 'controlled')) {
            $cases += [pscustomobject]@{
                route = $route; seed = $seed; generatorVersion = 6
                replayVerified = $true; ownerRestored = $true
                elapsedMs = 120L; assemblyMs = 18L
            }
        }
    }
    return [pscustomobject]@{
        protocol = 'TSJ-A04-CONSTRUCTION-1'; status = 'PASS'
        contractAssertions = 120L; runtimeAssertions = 80L
        coordinateIsolation = $true; explicitJoins = $true
        terminalCorrespondence = $true; failureIsolation = $true
        recipeIdentity = $true; multiUnitPackage = $true
        originalOwnerRestored = $true; candidateCleanup = 'PASS'
        cases = $cases; elapsedMs = 750L
    }
}

function ConvertTo-A04FixtureText($Report) {
    return ($Report | ConvertTo-Json -Depth 12 -Compress)
}

$a04Fixture = New-A04FixtureReport
$a04FixtureText = ConvertTo-A04FixtureText $a04Fixture
Assert-A04Reader (Test-A04Report $a04FixtureText) `
    'literal six-case resistive/controlled seed 1-3 report with generator version 6'

$a04Legacy = New-A04FixtureReport
foreach ($case in $a04Legacy.cases) { $case.generatorVersion = 5 }
Assert-A04Reader (-not (Test-A04Report (ConvertTo-A04FixtureText $a04Legacy))) `
    'all-legacy generator version 5 report is rejected'
$a04MixedLegacy = New-A04FixtureReport
$a04MixedLegacy.cases[2].generatorVersion = 5
Assert-A04Reader (-not (Test-A04Report (ConvertTo-A04FixtureText $a04MixedLegacy))) `
    'mixed legacy/current generator versions are rejected'
$a04Future = New-A04FixtureReport
$a04Future.cases[0].generatorVersion = 7
Assert-A04Reader (-not (Test-A04Report (ConvertTo-A04FixtureText $a04Future))) `
    'future generator version 7 is rejected'
$a04Fractional = New-A04FixtureReport
$a04Fractional.cases[0].generatorVersion = 6.5
Assert-A04Reader (-not (Test-A04Report (ConvertTo-A04FixtureText $a04Fractional))) `
    'fractional generator version is rejected'
$a04StringVersion = New-A04FixtureReport
$a04StringVersion.cases[0].generatorVersion = '6'
Assert-A04Reader (-not (Test-A04Report (ConvertTo-A04FixtureText $a04StringVersion))) `
    'string generator version is rejected'
$a04MissingVersion = New-A04FixtureReport
[void]$a04MissingVersion.cases[0].PSObject.Properties.Remove('generatorVersion')
Assert-A04Reader (-not (Test-A04Report (ConvertTo-A04FixtureText $a04MissingVersion))) `
    'missing generator version is rejected'
$a04WrongProtocol = New-A04FixtureReport
$a04WrongProtocol.protocol = 'TSJ-A04-CONSTRUCTION-0'
Assert-A04Reader (-not (Test-A04Report (ConvertTo-A04FixtureText $a04WrongProtocol))) `
    'wrong A04 protocol is rejected'
$a04FalseOwner = New-A04FixtureReport
$a04FalseOwner.cases[0].ownerRestored = $false
Assert-A04Reader (-not (Test-A04Report (ConvertTo-A04FixtureText $a04FalseOwner))) `
    'case with false original-owner restoration is rejected'
$a04FalseTopOwner = New-A04FixtureReport
$a04FalseTopOwner.originalOwnerRestored = $false
Assert-A04Reader (-not (Test-A04Report (ConvertTo-A04FixtureText $a04FalseTopOwner))) `
    'false top-level original-owner restoration is rejected'
$a04DuplicateCase = New-A04FixtureReport
$a04DuplicateCase.cases[5] = $a04DuplicateCase.cases[4]
Assert-A04Reader (-not (Test-A04Report (ConvertTo-A04FixtureText $a04DuplicateCase))) `
    'duplicate route/seed case is rejected'
$a04MissingCase = New-A04FixtureReport
$a04MissingCase.cases = @($a04MissingCase.cases | Select-Object -First 5)
Assert-A04Reader (-not (Test-A04Report (ConvertTo-A04FixtureText $a04MissingCase))) `
    'missing route/seed case is rejected'
$a04InvalidElapsed = New-A04FixtureReport
$a04InvalidElapsed.cases[0].elapsedMs = -1L
Assert-A04Reader (-not (Test-A04Report (ConvertTo-A04FixtureText $a04InvalidElapsed))) `
    'negative case elapsed time is rejected'
$a04InvalidAssembly = New-A04FixtureReport
$a04InvalidAssembly.cases[0].assemblyMs = '18'
Assert-A04Reader (-not (Test-A04Report (ConvertTo-A04FixtureText $a04InvalidAssembly))) `
    'string assembly time is rejected'

foreach ($diagnostic in @('FAIL:task47:null', 'FAIL:a03:stage=install',
        'R1:FAULT', 'http://127.0.0.1:8943/circuitjs.html')) {
    Assert-Q100Reader ((Get-DiagnosticText $diagnostic) -ceq $diagnostic) `
        'diagnostic markers and loopback URLs are not mistaken for drive paths'
}
Assert-Q100Reader ((Get-DiagnosticText 'Missing C:\owned\verifier\profile.txt; retry') -ceq
    'Missing <owned-path>; retry') 'backslash drive path is redacted'
Assert-Q100Reader ((Get-DiagnosticText 'Missing D:/owned/verifier/profile.txt; retry') -ceq
    'Missing <owned-path>; retry') 'forward-slash drive path is redacted'

function New-Q100FixtureNormalReady() {
    return [pscustomobject]@{
        workbenchNavigationReady = $true
        programReady = $true
        retestCustomerReady = $true
        boardPowerReady = $true
        boardPowerLabel = 'Board Power: ON'
    }
}

function New-Q100FixtureOrdinaryPrivacyReports() {
    return [pscustomobject]@{
        ready='complete'; verification=''; verificationPresent=$false
        q100=''; q100Present=$false; developerAttributes=[pscustomobject]@{}
        normalReady=(New-Q100FixtureNormalReady)
    }
}

function New-Q100FixturePrivacyCanaryEvidence() {
    $sentinels = @('data-tsj-q100-fault','data-tsj-future-answer')
    $cases = @(
        [pscustomobject]@{ attribute=$sentinels[0]; sentinelValue='CANARY_ONLY'; reportContainsAttribute=$true; developerLeakDetected=$true; playerPrivacyRejected=$true },
        [pscustomobject]@{ attribute=$sentinels[1]; sentinelValue='CANARY_ONLY'; reportContainsAttribute=$true; developerLeakDetected=$true; playerPrivacyRejected=$true }
    )
    $cleanupCases = @(
        [pscustomobject]@{ attribute=$sentinels[0]; removed=$true },
        [pscustomobject]@{ attribute=$sentinels[1]; removed=$true }
    )
    return [pscustomobject]@{
        canary=[pscustomobject]@{
            protocol='tsj-q100-player-dom-privacy-canary-v1'; status='PASS'
            sentinels=$sentinels; cases=$cases; finalPlayerPrivacy='PASS'
        }
        cleanup=[pscustomobject]@{ status='PASS'; removed=$sentinels; cases=$cleanupCases }
    }
}

Assert-Q100Reader ((Test-RouteRequiresTicketEntry 'q100-player') -and
    (Test-RouteRequiresTicketEntry 'q100-debugfalse') -and
    (Test-RouteRequiresTicketEntry 'a04-debugoff') -and
    -not (Test-RouteRequiresTicketEntry 'q100-healthy') -and
    -not (Test-RouteRequiresTicketEntry 'q100-interaction-lock')) `
    'ticket entry is limited to routes that require ordinary UI readiness'

$script:Q100FixtureContexts = @('LOW','HIGH','AC_ONLY','OVERLOAD','RECOVERED','LOW_AGAIN')
$script:Q100FixtureNames = @('RAIL12','RAIL5','RAIL3V3','OUT_A','OUT_B','OUT_C','OUT_D','OUT_E','OUT_F',
    'MON_SENSE','MON_FILTER','MON_REF','MON_CMD','AUX_GATE')

function New-Q100FixtureValues([string]$Context) {
    $values = [double[]]@(12.0, 5.0, 3.3, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0,
        0.10, 0.10, 0.165, 0.0, 5.0)
    $enabled = switch ($Context) {
        'LOW' { @() }
        'HIGH' { @(0,1,2,3,4,5) }
        'AC_ONLY' { @(0,2,4) }
        'OVERLOAD' { @(0,1,4,5) }
        'RECOVERED' { @(0,1,2,3,4,5) }
        'LOW_AGAIN' { @() }
        default { throw "Unknown Q100 fixture context: $Context" }
    }
    foreach ($index in $enabled) { $values[3 + $index] = 12.0 }
    if ($Context -ceq 'OVERLOAD') {
        $values[9] = 0.20
        $values[10] = 0.20
        $values[12] = 3.3
        $values[13] = 0.0
    }
    return ,$values
}

function New-Q100FixtureReport([string]$Variant = 'SHARED_HYSTERETIC_LINEAR',
        [string]$Fault = 'HEALTHY', [double]$Dt = 0.0001) {
    $observations = New-Object Collections.Generic.List[object]
    $isHealthy = $Fault -ceq 'HEALTHY'
    $faultRow = if ($isHealthy) { $null } else {
        @(Get-Q100FaultOracle | Where-Object { $_.id -ceq $Fault }) | Select-Object -First 1
    }
    if (-not $isHealthy -and $null -eq $faultRow) { throw "Unknown fixture fault $Fault" }
    $fixtureSimulatedSeconds = if ($isHealthy) { 0.6 } else { 1.2 }
    $fixturePhaseCount = if ($isHealthy) { 1L } else { 2L }
    $fixtureAcceptedSteps = [long][Math]::Round($fixtureSimulatedSeconds / $Dt)
    $fixtureLongBatchCount = [long][Math]::Ceiling((0.4 / $Dt) / 64.0)
    $fixtureShortBatchCount = [long][Math]::Ceiling((0.04 / $Dt) / 64.0)
    $fixtureStepBatches = $fixturePhaseCount * ($fixtureLongBatchCount + (5L * $fixtureShortBatchCount))
    $fixtureWork = [long]($fixtureStepBatches + (6L * $fixturePhaseCount) + 1L)
    for ($phaseIndex = 0; $phaseIndex -lt $(if ($isHealthy) { 1 } else { 2 }); $phaseIndex++) {
        for ($contextIndex = 0; $contextIndex -lt 6; $contextIndex++) {
            $context = $script:Q100FixtureContexts[$contextIndex]
            $values = New-Q100FixtureValues $context
            $phase = if ($isHealthy) { 'HEALTHY' } elseif ($phaseIndex -eq 0) { 'FAULT' } else { 'REPAIRED' }
            if ($phase -ceq 'FAULT' -and $context -ceq $faultRow.context) {
                $witnessIndex = [Array]::IndexOf([string[]]$script:Q100FixtureNames, [string]$faultRow.witness)
                if ($faultRow.above) { $values[$witnessIndex] = [double]$faultRow.threshold + 1.0 }
                else { $values[$witnessIndex] = [double]$faultRow.threshold - 0.1 }
            }
            [void]$observations.Add([pscustomobject]@{
                phase = $phase; context = $context
                time = if ($phaseIndex -eq 0) { 0.4 + ($contextIndex * 0.04) } else { 1.0 + ($contextIndex * 0.04) }
                values = $values
            })
        }
    }
    $reference = [pscustomobject]@{
        protocol = 'q100-electrical-reference-v3'; status = 'PASS_ELECTRICAL_REFERENCE_ONLY'
        seed = '9007199254740993'; variant = $Variant; modelVersion = 4
        planVersion = 1; faultPopulationVersion = 1
        fault = $Fault; dt = $Dt; packages = 100; simulatedSeconds = $fixtureSimulatedSeconds
        advanceMode = 'TEMPORAL_BATCH'; operationLimitMs = 5000
        trialBoundaryLimitMs = 500; maxBatchSteps = 64; fixedStepVerified = $true
        acceptedSteps = $fixtureAcceptedSteps; stepBatches = $fixtureStepBatches
        maxBatchAcceptedSteps = 64
        solverElements = 200; matrixFull = 20
        matrixReduced = 18; work = $fixtureWork; wallMs = 2000; maxUnitMs = 100
        maxStepTimeError = 0.0; sampleNames = $script:Q100FixtureNames; observations = $observations.ToArray()
    }
    return [pscustomobject]@{
        status = 'PASS_ELECTRICAL_REFERENCE_ONLY'; operationMs = 2100; cleanupMs = 5
        cleanup = 'PASS'; error = $null; cleanupError = $null; canary = $null; reference = $reference
    }
}

function ConvertTo-Q100FixtureText($Report) {
    return ($Report | ConvertTo-Json -Depth 20 -Compress)
}

function New-Q100FixtureCanary([string]$Canary) {
    $cancelled = $Canary -ceq 'cancel'
    $diagnosticReference = [pscustomobject]@{
        protocol = 'q100-electrical-reference-v3'; status = 'INCOMPLETE_ELECTRICAL_REFERENCE'
        seed = '9007199254740993'; variant = 'SHARED_HYSTERETIC_LINEAR'
        modelVersion = 4; planVersion = 1; faultPopulationVersion = 1
        fault = 'HEALTHY'; dt = 0.0001
        packages = 100; solverElements = 200; simulatedSeconds = 0.0064
        advanceMode = 'TEMPORAL_BATCH'; operationLimitMs = 5000
        trialBoundaryLimitMs = 500; maxBatchSteps = 64; fixedStepVerified = $true
        acceptedSteps = 64; stepBatches = 1; maxBatchAcceptedSteps = 64
        matrixFull = 20; matrixReduced = 18; work = 3; wallMs = 20
        maxUnitMs = 5; maxStepTimeError = 0.0
        sampleNames = $script:Q100FixtureNames; observations = @()
    }
    return [pscustomobject]@{
        status = if ($cancelled) { 'CANCELLED_REFERENCE' } else { 'FAIL_REFERENCE' }
        operationMs = 50; cleanupMs = 5; cleanup = 'PASS'
        error = 'q100-explicit-' + $Canary + '-after-accepted-step'
        cleanupError = $null; canary = $Canary; reference = $diagnosticReference
    }
}

function New-Q100FixtureInteractionLock() {
    $report = New-Q100FixtureCanary 'cancel'
    $report.status = 'CANCELLED_REFERENCE'
    $report.error = 'q100-explicit-interaction-lock-after-accepted-step'
    $report.canary = 'interaction-lock'
    $report.reference.simulatedSeconds = 0.0064
    $report.reference.work = 3
    $report | Add-Member -NotePropertyName interactionLock -NotePropertyValue ([pscustomobject]@{
        status='PASS'; selectionAttemptBlocked=$true; deletionAttemptBlocked=$true; runToggleBlocked=$true
        graphIdentityStable=$true; elementIdentityStable=$true; elementCountBefore=200; elementCountAfter=200
        selectionStable=$true; undoDepthBefore=0; undoDepthAfter=0
        simulationTimeBefore=0.0; simulationTimeAfter=0.0
        acceptedStepCountBefore=0; acceptedStepCountAfter=0; runningBefore=$true; runningAfter=$true
        privateOwnerStillUnavailable=$true
        wheelAttemptBlocked=$true; nativeWheelPreviewBlocked=$true; nativeChangePreviewBlocked=$true
        privateCircuitStable=$true; wheelRBefore=22000.0; wheelRAfter=22000.0
        wheelDelta=120.0; popupUnchanged=$true
    })
    return $report
}

function New-Q100FixtureRoute([string]$Name, [string]$Kind, [string]$ReportText) {
    return [pscustomobject]@{
        name = $Name; kind = $Kind; q100ReportAttributePresent = $true
        reports = [pscustomobject]@{ q100 = $ReportText }
    }
}

$healthy = New-Q100FixtureReport
$healthyText = ConvertTo-Q100FixtureText $healthy
Assert-Q100Reader (Test-Q100Report $healthyText 'SHARED_HYSTERETIC_LINEAR' 'HEALTHY' 0.0001) 'six-context healthy report'
Assert-Q100Reader ($healthy.reference.work -eq 105) '100 us healthy work count is batches plus six analyses and construction'
$fault = New-Q100FixtureReport 'SHARED_HYSTERETIC_LINEAR' 'RSA_RESISTANCE' 0.00005
$faultText = ConvertTo-Q100FixtureText $fault
Assert-Q100Reader (Test-Q100Report $faultText 'SHARED_HYSTERETIC_LINEAR' 'RSA_RESISTANCE' 0.00005) 'fault plus six repaired contexts'
Assert-Q100Reader ($fault.reference.work -eq 393) '50 us fault work count is both phases plus twelve analyses and construction'
Assert-Q100Reader (Test-Q100Report $faultText 'SHARED_HYSTERETIC_LINEAR' 'RSA_RESISTANCE' 0.0001) `
    'fault reader remains pinned to 50 us despite a 100 us generic expectation'
$faultAt100us = New-Q100FixtureReport 'SHARED_HYSTERETIC_LINEAR' 'RSA_RESISTANCE' 0.0001
Assert-Q100Reader (-not (Test-Q100Report (ConvertTo-Q100FixtureText $faultAt100us) `
    'SHARED_HYSTERETIC_LINEAR' 'RSA_RESISTANCE' 0.0001)) '100 us fault is rejected even when the caller expects 100 us'
$wrongAdvanceMode = New-Q100FixtureReport
$wrongAdvanceMode.reference.advanceMode = 'PER_STEP'
Assert-Q100Reader (-not (Test-Q100Report (ConvertTo-Q100FixtureText $wrongAdvanceMode) `
    'SHARED_HYSTERETIC_LINEAR' 'HEALTHY' 0.0001)) 'wrong temporal advance mode is rejected'
$missingAdvanceMode = New-Q100FixtureReport
[void]$missingAdvanceMode.reference.PSObject.Properties.Remove('advanceMode')
Assert-Q100Reader (-not (Test-Q100Report (ConvertTo-Q100FixtureText $missingAdvanceMode) `
    'SHARED_HYSTERETIC_LINEAR' 'HEALTHY' 0.0001)) 'missing temporal advance mode is rejected'
$missingWork = New-Q100FixtureReport
[void]$missingWork.reference.PSObject.Properties.Remove('work')
Assert-Q100Reader (-not (Test-Q100Report (ConvertTo-Q100FixtureText $missingWork) `
    'SHARED_HYSTERETIC_LINEAR' 'HEALTHY' 0.0001)) 'missing exact work count is rejected'
$stringWork = New-Q100FixtureReport
$stringWork.reference.work = '105'
Assert-Q100Reader (-not (Test-Q100Report (ConvertTo-Q100FixtureText $stringWork) `
    'SHARED_HYSTERETIC_LINEAR' 'HEALTHY' 0.0001)) 'string work count is rejected'
$underCountWork = New-Q100FixtureReport
$underCountWork.reference.work--
Assert-Q100Reader (-not (Test-Q100Report (ConvertTo-Q100FixtureText $underCountWork) `
    'SHARED_HYSTERETIC_LINEAR' 'HEALTHY' 0.0001)) 'under-counted work is rejected'
$overCountWork = New-Q100FixtureReport
$overCountWork.reference.work++
Assert-Q100Reader (-not (Test-Q100Report (ConvertTo-Q100FixtureText $overCountWork) `
    'SHARED_HYSTERETIC_LINEAR' 'HEALTHY' 0.0001)) 'over-counted work is rejected'
$wrongOperationLimit = New-Q100FixtureReport
$wrongOperationLimit.reference.operationLimitMs = 5001
Assert-Q100Reader (-not (Test-Q100Report (ConvertTo-Q100FixtureText $wrongOperationLimit) `
    'SHARED_HYSTERETIC_LINEAR' 'HEALTHY' 0.0001)) 'wrong temporal operation limit is rejected'
$wrongTrialBoundaryLimit = New-Q100FixtureReport
$wrongTrialBoundaryLimit.reference.trialBoundaryLimitMs = 501
Assert-Q100Reader (-not (Test-Q100Report (ConvertTo-Q100FixtureText $wrongTrialBoundaryLimit) `
    'SHARED_HYSTERETIC_LINEAR' 'HEALTHY' 0.0001)) 'wrong trial-boundary limit is rejected'
$wrongMaxBatchSteps = New-Q100FixtureReport
$wrongMaxBatchSteps.reference.maxBatchSteps = 63
Assert-Q100Reader (-not (Test-Q100Report (ConvertTo-Q100FixtureText $wrongMaxBatchSteps) `
    'SHARED_HYSTERETIC_LINEAR' 'HEALTHY' 0.0001)) 'wrong maximum batch size is rejected'
$falseFixedStep = New-Q100FixtureReport
$falseFixedStep.reference.fixedStepVerified = $false
Assert-Q100Reader (-not (Test-Q100Report (ConvertTo-Q100FixtureText $falseFixedStep) `
    'SHARED_HYSTERETIC_LINEAR' 'HEALTHY' 0.0001)) 'false fixed-step verification is rejected'
$stringFixedStep = New-Q100FixtureReport
$stringFixedStep.reference.fixedStepVerified = 'true'
Assert-Q100Reader (-not (Test-Q100Report (ConvertTo-Q100FixtureText $stringFixedStep) `
    'SHARED_HYSTERETIC_LINEAR' 'HEALTHY' 0.0001)) 'string fixed-step verification is rejected'
$wrongAcceptedSteps = New-Q100FixtureReport
$wrongAcceptedSteps.reference.acceptedSteps--
Assert-Q100Reader (-not (Test-Q100Report (ConvertTo-Q100FixtureText $wrongAcceptedSteps) `
    'SHARED_HYSTERETIC_LINEAR' 'HEALTHY' 0.0001)) 'accepted-step total mismatch is rejected'
$stringAcceptedSteps = New-Q100FixtureReport
$stringAcceptedSteps.reference.acceptedSteps = '6000'
Assert-Q100Reader (-not (Test-Q100Report (ConvertTo-Q100FixtureText $stringAcceptedSteps) `
    'SHARED_HYSTERETIC_LINEAR' 'HEALTHY' 0.0001)) 'string accepted-step total is rejected'
$wrongStepBatches = New-Q100FixtureReport
$wrongStepBatches.reference.stepBatches--
Assert-Q100Reader (-not (Test-Q100Report (ConvertTo-Q100FixtureText $wrongStepBatches) `
    'SHARED_HYSTERETIC_LINEAR' 'HEALTHY' 0.0001)) 'temporal batch count mismatch is rejected'
$wrongMaxBatchAccepted = New-Q100FixtureReport
$wrongMaxBatchAccepted.reference.maxBatchAcceptedSteps = 63
Assert-Q100Reader (-not (Test-Q100Report (ConvertTo-Q100FixtureText $wrongMaxBatchAccepted) `
    'SHARED_HYSTERETIC_LINEAR' 'HEALTHY' 0.0001)) 'maximum accepted batch mismatch is rejected'

$wrongSeedType = New-Q100FixtureReport
$wrongSeedType.reference.seed = [long]9007199254740993
Assert-Q100Reader (-not (Test-Q100Report (ConvertTo-Q100FixtureText $wrongSeedType) 'SHARED_HYSTERETIC_LINEAR' 'HEALTHY' 0.0001)) 'numeric seed is rejected'
$wrongSeedValue = New-Q100FixtureReport
$wrongSeedValue.reference.seed = '9007199254740992'
Assert-Q100Reader (-not (Test-Q100Report (ConvertTo-Q100FixtureText $wrongSeedValue) 'SHARED_HYSTERETIC_LINEAR' 'HEALTHY' 0.0001)) 'rounded seed is rejected'
$wrongVariant = New-Q100FixtureReport
$wrongVariant.reference.variant = 'SEPARATE_DIRECT_SWITCHING'
Assert-Q100Reader (-not (Test-Q100Report (ConvertTo-Q100FixtureText $wrongVariant) 'SHARED_HYSTERETIC_LINEAR' 'HEALTHY' 0.0001)) 'wrong variant is rejected'
$wrongPopulation = New-Q100FixtureReport
$wrongPopulation.reference.fault = 'NOT_A_FAULT'
$wrongPopulation.reference.dt = 0.00005
Assert-Q100Reader (-not (Test-Q100Report (ConvertTo-Q100FixtureText $wrongPopulation) 'SHARED_HYSTERETIC_LINEAR' 'NOT_A_FAULT' 0.00005)) 'unknown fault population member is rejected'
$wrongPlanVersion = New-Q100FixtureReport
$wrongPlanVersion.reference.planVersion = 2
Assert-Q100Reader (-not (Test-Q100Report (ConvertTo-Q100FixtureText $wrongPlanVersion) 'SHARED_HYSTERETIC_LINEAR' 'HEALTHY' 0.0001)) 'wrong plan version is rejected'
$historicalModelVersion = New-Q100FixtureReport
$historicalModelVersion.reference.modelVersion = 3
Assert-Q100Reader (-not (Test-Q100Report (ConvertTo-Q100FixtureText $historicalModelVersion) 'SHARED_HYSTERETIC_LINEAR' 'HEALTHY' 0.0001)) 'historical model version 3 is rejected'
$missingModelVersion = New-Q100FixtureReport
[void]$missingModelVersion.reference.PSObject.Properties.Remove('modelVersion')
Assert-Q100Reader (-not (Test-Q100Report (ConvertTo-Q100FixtureText $missingModelVersion) 'SHARED_HYSTERETIC_LINEAR' 'HEALTHY' 0.0001)) 'missing model version is rejected'
$wrongPopulationVersion = New-Q100FixtureReport
$wrongPopulationVersion.reference.faultPopulationVersion = 2
Assert-Q100Reader (-not (Test-Q100Report (ConvertTo-Q100FixtureText $wrongPopulationVersion) 'SHARED_HYSTERETIC_LINEAR' 'HEALTHY' 0.0001)) 'wrong fault population version is rejected'
$wrongProtocol = New-Q100FixtureReport
$wrongProtocol.reference.protocol = 'q100-electrical-reference-v1'
Assert-Q100Reader (-not (Test-Q100Report (ConvertTo-Q100FixtureText $wrongProtocol) 'SHARED_HYSTERETIC_LINEAR' 'HEALTHY' 0.0001)) 'historical report protocol v1 is rejected'
$historicalProtocolV2 = New-Q100FixtureReport
$historicalProtocolV2.reference.protocol = 'q100-electrical-reference-v2'
Assert-Q100Reader (-not (Test-Q100Report (ConvertTo-Q100FixtureText $historicalProtocolV2) `
    'SHARED_HYSTERETIC_LINEAR' 'HEALTHY' 0.0001)) 'historical report protocol v2 is rejected'
$wrongPackages = New-Q100FixtureReport
$wrongPackages.reference.packages = 99
Assert-Q100Reader (-not (Test-Q100Report (ConvertTo-Q100FixtureText $wrongPackages) 'SHARED_HYSTERETIC_LINEAR' 'HEALTHY' 0.0001)) 'wrong package count is rejected'
$lateOperation = New-Q100FixtureReport
$lateOperation.operationMs = 90001
Assert-Q100Reader (-not (Test-Q100Report (ConvertTo-Q100FixtureText $lateOperation) 'SHARED_HYSTERETIC_LINEAR' 'HEALTHY' 0.0001)) 'over-budget operation is rejected'
$lateCleanup = New-Q100FixtureReport
$lateCleanup.cleanupMs = 120001
Assert-Q100Reader (-not (Test-Q100Report (ConvertTo-Q100FixtureText $lateCleanup) 'SHARED_HYSTERETIC_LINEAR' 'HEALTHY' 0.0001)) 'over-budget cleanup is rejected'
$lateUnit = New-Q100FixtureReport
$lateUnit.reference.maxUnitMs = 5001
Assert-Q100Reader (-not (Test-Q100Report (ConvertTo-Q100FixtureText $lateUnit) 'SHARED_HYSTERETIC_LINEAR' 'HEALTHY' 0.0001)) 'over-budget unit is rejected'
$lateWall = New-Q100FixtureReport
$lateWall.reference.wallMs = 90001
Assert-Q100Reader (-not (Test-Q100Report (ConvertTo-Q100FixtureText $lateWall) 'SHARED_HYSTERETIC_LINEAR' 'HEALTHY' 0.0001)) 'over-budget reference is rejected'
$wrongCleanup = New-Q100FixtureReport
$wrongCleanup.cleanup = 'FAIL'
Assert-Q100Reader (-not (Test-Q100Report (ConvertTo-Q100FixtureText $wrongCleanup) 'SHARED_HYSTERETIC_LINEAR' 'HEALTHY' 0.0001)) 'failed cleanup is rejected'
$numericString = New-Q100FixtureReport
$stringValues = New-Object object[] 14
[Array]::Copy($numericString.reference.observations[0].values, $stringValues, 14)
$stringValues[0] = '12'
$numericString.reference.observations[0].values = $stringValues
Assert-Q100Reader (-not (Test-Q100Report (ConvertTo-Q100FixtureText $numericString) 'SHARED_HYSTERETIC_LINEAR' 'HEALTHY' 0.0001)) 'numeric string sample is rejected'
Assert-Q100Reader (-not (Test-Q100FiniteNumber ([double]::NaN))) 'NaN sample is rejected'
$badSampleCount = New-Q100FixtureReport
$badSampleCount.reference.sampleNames = @($badSampleCount.reference.sampleNames | Select-Object -First 13)
Assert-Q100Reader (-not (Test-Q100Report (ConvertTo-Q100FixtureText $badSampleCount) 'SHARED_HYSTERETIC_LINEAR' 'HEALTHY' 0.0001)) 'missing sample identity is rejected'
$badContext = New-Q100FixtureReport
$badContext.reference.observations[1].context = 'LOW'
Assert-Q100Reader (-not (Test-Q100Report (ConvertTo-Q100FixtureText $badContext) 'SHARED_HYSTERETIC_LINEAR' 'HEALTHY' 0.0001)) 'duplicate context is rejected'
$missingContext = New-Q100FixtureReport
$missingContext.reference.observations[2].context = 'MISSING'
Assert-Q100Reader (-not (Test-Q100Report (ConvertTo-Q100FixtureText $missingContext) 'SHARED_HYSTERETIC_LINEAR' 'HEALTHY' 0.0001)) 'missing context is rejected'
$missingRepair = New-Q100FixtureReport 'SHARED_HYSTERETIC_LINEAR' 'RSA_RESISTANCE' 0.00005
$missingRepair.reference.observations = @($missingRepair.reference.observations | Select-Object -First 6)
Assert-Q100Reader (-not (Test-Q100Report (ConvertTo-Q100FixtureText $missingRepair) 'SHARED_HYSTERETIC_LINEAR' 'RSA_RESISTANCE' 0.00005)) 'missing repair contexts are rejected'
$badWitness = New-Q100FixtureReport 'SHARED_HYSTERETIC_LINEAR' 'RSA_RESISTANCE' 0.00005
$badWitness.reference.observations[1].values[3] = 12.0
Assert-Q100Reader (-not (Test-Q100Report (ConvertTo-Q100FixtureText $badWitness) 'SHARED_HYSTERETIC_LINEAR' 'RSA_RESISTANCE' 0.00005)) 'wrong fault witness is rejected'
$separatedShortFaults = @('DE_SHUNT','DF_SHUNT','CSTORE_E_SHUNT','CSTORE_F_SHUNT')
foreach ($faultId in $separatedShortFaults) {
    $shortFault = New-Q100FixtureReport 'SHARED_HYSTERETIC_LINEAR' $faultId 0.00005
    Assert-Q100Reader (Test-Q100Report (ConvertTo-Q100FixtureText $shortFault) `
        'SHARED_HYSTERETIC_LINEAR' $faultId 0.00005) "$faultId keeps every control rail healthy while its output witness collapses"
}
$collapsedShortFaultRail = New-Q100FixtureReport 'SHARED_HYSTERETIC_LINEAR' 'DE_SHUNT' 0.00005
$collapsedShortFaultRail.reference.observations[0].values[0] = 0.0
Assert-Q100Reader (-not (Test-Q100Report (ConvertTo-Q100FixtureText $collapsedShortFaultRail) `
    'SHARED_HYSTERETIC_LINEAR' 'DE_SHUNT' 0.00005)) 'collapsed RAIL12 during a separated-load short fault is rejected'
$collapsedShortFaultRail = New-Q100FixtureReport 'SHARED_HYSTERETIC_LINEAR' 'DF_SHUNT' 0.00005
$collapsedShortFaultRail.reference.observations[1].values[1] = 0.0
Assert-Q100Reader (-not (Test-Q100Report (ConvertTo-Q100FixtureText $collapsedShortFaultRail) `
    'SHARED_HYSTERETIC_LINEAR' 'DF_SHUNT' 0.00005)) 'collapsed RAIL5 during a separated-load short fault is rejected'
$collapsedShortFaultRail = New-Q100FixtureReport 'SHARED_HYSTERETIC_LINEAR' 'CSTORE_E_SHUNT' 0.00005
$collapsedShortFaultRail.reference.observations[3].values[2] = 0.0
Assert-Q100Reader (-not (Test-Q100Report (ConvertTo-Q100FixtureText $collapsedShortFaultRail) `
    'SHARED_HYSTERETIC_LINEAR' 'CSTORE_E_SHUNT' 0.00005)) 'collapsed RAIL3V3 during a separated-load short fault is rejected'
$unhealthyRepairedShortFault = New-Q100FixtureReport 'SHARED_HYSTERETIC_LINEAR' 'CSTORE_F_SHUNT' 0.00005
$unhealthyRepairedShortFault.reference.observations[6].values[0] = 0.0
Assert-Q100Reader (-not (Test-Q100Report (ConvertTo-Q100FixtureText $unhealthyRepairedShortFault) `
    'SHARED_HYSTERETIC_LINEAR' 'CSTORE_F_SHUNT' 0.00005)) 'healthy repaired observations remain required after a separated-load short fault'
$badStatus = New-Q100FixtureReport
$badStatus.status = 'FAIL'
Assert-Q100Reader (-not (Test-Q100Report (ConvertTo-Q100FixtureText $badStatus) 'SHARED_HYSTERETIC_LINEAR' 'HEALTHY' 0.0001)) 'non-pass status is rejected'

$preflight = [pscustomobject]@{
    status='REJECTED_BEFORE_PRIVATE_GRAPH'; error='Unknown RB100 electrical-reference fault'
    cleanup='NOT_APPLICABLE'
}
$preflightText = ConvertTo-Q100FixtureText $preflight
Assert-Q100Reader (Test-Q100PreflightReport $preflightText 'Unknown RB100 electrical-reference fault') 'expected preflight rejection'
Assert-Q100Reader (-not (Test-Q100PreflightReport $preflightText 'other error')) 'preflight error identity is exact'
$preflightReports = [pscustomobject]@{
    verification='FAIL:q100:preflight:Unknown RB100 electrical-reference fault'
    q100=$preflightText; q100Present=$true
}
$preflightDefinition = [pscustomobject]@{ expectedError='Unknown RB100 electrical-reference fault' }
Assert-Q100Reader (Test-RouteReady 'q100-preflight-fault' $preflightReports $preflightDefinition) 'preflight status and report pair'
$preflightReports.verification = 'FAIL:q100:preflight:other error'
Assert-Q100Reader (-not (Test-RouteReady 'q100-preflight-fault' $preflightReports $preflightDefinition)) 'wrong preflight terminal text is rejected'
$preflightReports.verification = 'FAIL:q100:preflight:Unknown RB100 structural variant'
$preflightReports.q100 = ConvertTo-Q100FixtureText ([pscustomobject]@{
    status='REJECTED_BEFORE_PRIVATE_GRAPH'; error='Unknown RB100 structural variant'; cleanup='NOT_APPLICABLE'
})
$variantDefinition = [pscustomobject]@{ expectedError='Unknown RB100 structural variant' }
Assert-Q100Reader (Test-RouteReady 'q100-preflight-variant' $preflightReports $variantDefinition) 'invalid variant is rejected before graph mutation'
$preflightReports.verification = 'FAIL:q100:preflight:RB100 reference: unsupported reference timestep'
$preflightReports.q100 = ConvertTo-Q100FixtureText ([pscustomobject]@{
    status='REJECTED_BEFORE_PRIVATE_GRAPH'; error='RB100 reference: unsupported reference timestep'; cleanup='NOT_APPLICABLE'
})
$dtDefinition = [pscustomobject]@{ expectedError='RB100 reference: unsupported reference timestep' }
Assert-Q100Reader (Test-RouteReady 'q100-preflight-dt' $preflightReports $dtDefinition) 'invalid timestep is rejected before graph mutation'
foreach ($canary in @('after-step','cancel')) {
    $canaryText = ConvertTo-Q100FixtureText (New-Q100FixtureCanary $canary)
    Assert-Q100Reader (Test-Q100InjectedFailureReport $canaryText $canary) "expected $canary cleanup"
    $otherCanary = if ($canary -ceq 'cancel') { 'after-step' } else { 'cancel' }
    Assert-Q100Reader (-not (Test-Q100InjectedFailureReport $canaryText $otherCanary)) "$canary outcome cannot masquerade as $otherCanary"
    $canaryReports = [pscustomobject]@{
        verification = if ($canary -ceq 'cancel') { 'CANCELLED:q100' } else { 'FAIL:q100' }
        q100=$canaryText; q100Present=$true
    }
    Assert-Q100Reader (Test-RouteReady ('q100-' + $canary) $canaryReports) "$canary terminal status and report pair"
}
$historicalCanaryProtocol = New-Q100FixtureCanary 'after-step'
$historicalCanaryProtocol.reference.protocol = 'q100-electrical-reference-v1'
Assert-Q100Reader (-not (Test-Q100InjectedFailureReport (ConvertTo-Q100FixtureText $historicalCanaryProtocol) 'after-step')) `
    'incomplete report with historical protocol v1 is rejected'
$historicalCanaryProtocolV2 = New-Q100FixtureCanary 'after-step'
$historicalCanaryProtocolV2.reference.protocol = 'q100-electrical-reference-v2'
Assert-Q100Reader (-not (Test-Q100InjectedFailureReport (ConvertTo-Q100FixtureText $historicalCanaryProtocolV2) 'after-step')) `
    'incomplete report with historical protocol v2 is rejected'
$wrongCanaryAcceptedSteps = New-Q100FixtureCanary 'after-step'
$wrongCanaryAcceptedSteps.reference.acceptedSteps = 63
Assert-Q100Reader (-not (Test-Q100InjectedFailureReport (ConvertTo-Q100FixtureText $wrongCanaryAcceptedSteps) 'after-step')) `
    'incomplete canary with mismatched accepted-step total is rejected'
$wrongCanaryBatches = New-Q100FixtureCanary 'after-step'
$wrongCanaryBatches.reference.stepBatches = 2
Assert-Q100Reader (-not (Test-Q100InjectedFailureReport (ConvertTo-Q100FixtureText $wrongCanaryBatches) 'after-step')) `
    'incomplete canary with mismatched batch count is rejected'
$wrongCanaryMaxBatch = New-Q100FixtureCanary 'after-step'
$wrongCanaryMaxBatch.reference.maxBatchAcceptedSteps = 63
Assert-Q100Reader (-not (Test-Q100InjectedFailureReport (ConvertTo-Q100FixtureText $wrongCanaryMaxBatch) 'after-step')) `
    'incomplete canary with mismatched accepted batch size is rejected'
$noAcceptedStep = New-Q100FixtureCanary 'after-step'
$noAcceptedStep.reference.simulatedSeconds = 0.0
Assert-Q100Reader (-not (Test-Q100InjectedFailureReport (ConvertTo-Q100FixtureText $noAcceptedStep) 'after-step')) 'canary without accepted simulation time is rejected'
$noCanaryWork = New-Q100FixtureCanary 'cancel'
$noCanaryWork.reference.work = 2
Assert-Q100Reader (-not (Test-Q100InjectedFailureReport (ConvertTo-Q100FixtureText $noCanaryWork) 'cancel')) 'canary without accepted-step work is rejected'
$historicalCanaryModelVersion = New-Q100FixtureCanary 'after-step'
$historicalCanaryModelVersion.reference.modelVersion = 3
Assert-Q100Reader (-not (Test-Q100InjectedFailureReport (ConvertTo-Q100FixtureText $historicalCanaryModelVersion) 'after-step')) 'incomplete report with historical model version 3 is rejected'
$missingCanaryModelVersion = New-Q100FixtureCanary 'after-step'
[void]$missingCanaryModelVersion.reference.PSObject.Properties.Remove('modelVersion')
Assert-Q100Reader (-not (Test-Q100InjectedFailureReport (ConvertTo-Q100FixtureText $missingCanaryModelVersion) 'after-step')) 'incomplete report without model version is rejected'
$interactionLock = New-Q100FixtureInteractionLock
$interactionLockText = ConvertTo-Q100FixtureText $interactionLock
Assert-Q100Reader (Test-Q100InteractionLockReport $interactionLockText) 'interaction-lock has two accepted turns and a complete lock witness'
$historicalInteractionLockModelVersion = New-Q100FixtureInteractionLock
$historicalInteractionLockModelVersion.reference.modelVersion = 3
Assert-Q100Reader (-not (Test-Q100InteractionLockReport (ConvertTo-Q100FixtureText $historicalInteractionLockModelVersion))) `
    'interaction-lock with historical model version 3 is rejected'
$historicalInteractionLockProtocol = New-Q100FixtureInteractionLock
$historicalInteractionLockProtocol.reference.protocol = 'q100-electrical-reference-v1'
Assert-Q100Reader (-not (Test-Q100InteractionLockReport (ConvertTo-Q100FixtureText $historicalInteractionLockProtocol))) `
    'interaction-lock with historical protocol v1 is rejected'
$historicalInteractionLockProtocolV2 = New-Q100FixtureInteractionLock
$historicalInteractionLockProtocolV2.reference.protocol = 'q100-electrical-reference-v2'
Assert-Q100Reader (-not (Test-Q100InteractionLockReport (ConvertTo-Q100FixtureText $historicalInteractionLockProtocolV2))) `
    'interaction-lock with historical protocol v2 is rejected'
$wrongInteractionLockStepCount = New-Q100FixtureInteractionLock
$wrongInteractionLockStepCount.reference.acceptedSteps = 63
Assert-Q100Reader (-not (Test-Q100InteractionLockReport (ConvertTo-Q100FixtureText $wrongInteractionLockStepCount))) `
    'interaction-lock with a mismatched temporal step count is rejected'
$interactionLockReports = [pscustomobject]@{
    verification='CANCELLED:q100'; q100=$interactionLockText; q100Present=$true
}
Assert-Q100Reader (Test-RouteReady 'q100-interaction-lock' $interactionLockReports) 'interaction-lock cancellation and lock report agree'
$unblockedSelection = New-Q100FixtureInteractionLock
$unblockedSelection.interactionLock.selectionAttemptBlocked = $false
Assert-Q100Reader (-not (Test-Q100InteractionLockReport (ConvertTo-Q100FixtureText $unblockedSelection))) 'unblocked selection attempt is rejected'
foreach ($field in @('deletionAttemptBlocked','runToggleBlocked','graphIdentityStable',
        'elementIdentityStable','selectionStable','privateOwnerStillUnavailable')) {
    $badLockFlag = New-Q100FixtureInteractionLock
    $badLockFlag.interactionLock.$field = $false
    Assert-Q100Reader (-not (Test-Q100InteractionLockReport (ConvertTo-Q100FixtureText $badLockFlag))) `
        "false interaction-lock witness '$field' is rejected"
}
foreach ($field in @('wheelAttemptBlocked','nativeWheelPreviewBlocked',
        'nativeChangePreviewBlocked','privateCircuitStable','popupUnchanged')) {
    $badWheelLockFlag = New-Q100FixtureInteractionLock
    $badWheelLockFlag.interactionLock.$field = $false
    Assert-Q100Reader (-not (Test-Q100InteractionLockReport (ConvertTo-Q100FixtureText $badWheelLockFlag))) `
        "false wheel interaction-lock witness '$field' is rejected"
}
$changedWheelResistance = New-Q100FixtureInteractionLock
$changedWheelResistance.interactionLock.wheelRAfter++
Assert-Q100Reader (-not (Test-Q100InteractionLockReport (ConvertTo-Q100FixtureText $changedWheelResistance))) `
    'changed wheel resistance is rejected'
$invalidWheelResistance = New-Q100FixtureInteractionLock
$invalidWheelResistance.interactionLock.wheelRBefore = '22000'
Assert-Q100Reader (-not (Test-Q100InteractionLockReport (ConvertTo-Q100FixtureText $invalidWheelResistance))) `
    'string wheel resistance is rejected'
$zeroWheelDelta = New-Q100FixtureInteractionLock
$zeroWheelDelta.interactionLock.wheelDelta = 0.0
Assert-Q100Reader (-not (Test-Q100InteractionLockReport (ConvertTo-Q100FixtureText $zeroWheelDelta))) `
    'wheel event without nonzero delta is rejected'
$invalidWheelDelta = New-Q100FixtureInteractionLock
$invalidWheelDelta.interactionLock.wheelDelta = '120'
Assert-Q100Reader (-not (Test-Q100InteractionLockReport (ConvertTo-Q100FixtureText $invalidWheelDelta))) `
    'string wheel delta is rejected'
$missingWheelWitness = New-Q100FixtureInteractionLock
[void]$missingWheelWitness.interactionLock.PSObject.Properties.Remove('nativeWheelPreviewBlocked')
Assert-Q100Reader (-not (Test-Q100InteractionLockReport (ConvertTo-Q100FixtureText $missingWheelWitness))) `
    'missing native wheel preview witness is rejected'
$changedGraph = New-Q100FixtureInteractionLock
$changedGraph.interactionLock.elementCountAfter++
Assert-Q100Reader (-not (Test-Q100InteractionLockReport (ConvertTo-Q100FixtureText $changedGraph))) 'changed private graph count is rejected'
$changedUndo = New-Q100FixtureInteractionLock
$changedUndo.interactionLock.undoDepthAfter = 1
Assert-Q100Reader (-not (Test-Q100InteractionLockReport (ConvertTo-Q100FixtureText $changedUndo))) 'changed undo depth is rejected'
$changedLockTime = New-Q100FixtureInteractionLock
$changedLockTime.interactionLock.simulationTimeAfter = 0.1
Assert-Q100Reader (-not (Test-Q100InteractionLockReport (ConvertTo-Q100FixtureText $changedLockTime))) 'changed public simulation time is rejected'
$changedAcceptedCount = New-Q100FixtureInteractionLock
$changedAcceptedCount.interactionLock.acceptedStepCountAfter = 1
Assert-Q100Reader (-not (Test-Q100InteractionLockReport (ConvertTo-Q100FixtureText $changedAcceptedCount))) 'changed accepted-step count is rejected'
$changedSelection = New-Q100FixtureInteractionLock
$changedSelection.interactionLock.selectionStable = $false
Assert-Q100Reader (-not (Test-Q100InteractionLockReport (ConvertTo-Q100FixtureText $changedSelection))) 'changed element selection is rejected'
$changedRunState = New-Q100FixtureInteractionLock
$changedRunState.interactionLock.runningAfter = $false
Assert-Q100Reader (-not (Test-Q100InteractionLockReport (ConvertTo-Q100FixtureText $changedRunState))) 'changed run state is rejected'
$oneInteractionTurn = New-Q100FixtureInteractionLock
$oneInteractionTurn.reference.work = 2
$oneInteractionTurn.reference.simulatedSeconds = 0.0
Assert-Q100Reader (-not (Test-Q100InteractionLockReport (ConvertTo-Q100FixtureText $oneInteractionTurn))) 'one accepted interaction turn is insufficient'
$missingLockWitness = New-Q100FixtureInteractionLock
[void]$missingLockWitness.PSObject.Properties.Remove('interactionLock')
Assert-Q100Reader (-not (Test-Q100InteractionLockReport (ConvertTo-Q100FixtureText $missingLockWitness))) 'terminal cancellation without lock evidence is rejected'
$wrongInteractionTerminal = [pscustomobject]@{
    verification='FAIL:q100'; q100=$interactionLockText; q100Present=$true
}
Assert-Q100Reader (-not (Test-RouteReady 'q100-interaction-lock' $wrongInteractionTerminal)) 'wrong interaction-lock terminal is rejected'
$wrongInteractionError = New-Q100FixtureInteractionLock
$wrongInteractionError.error = 'q100-explicit-after-step-after-accepted-step'
Assert-Q100Reader (-not (Test-Q100InteractionLockReport (ConvertTo-Q100FixtureText $wrongInteractionError))) 'wrong interaction-lock error identity is rejected'
$missingElementWitness = New-Q100FixtureInteractionLock
[void]$missingElementWitness.interactionLock.PSObject.Properties.Remove('elementIdentityStable')
Assert-Q100Reader (-not (Test-Q100InteractionLockReport (ConvertTo-Q100FixtureText $missingElementWitness))) 'missing element identity witness is rejected'

$privacyReports = New-Q100FixtureOrdinaryPrivacyReports
Assert-Q100Reader (Test-RouteReady 'q100-debugfalse' $privacyReports) 'debug-false privacy and ordinary readiness'
$missingWorkbenchNavigation = New-Q100FixtureNormalReady
$missingWorkbenchNavigation.PSObject.Properties.Remove('workbenchNavigationReady')
Assert-Q100Reader (-not (Test-NormalWorkbenchReady $missingWorkbenchNavigation)) 'missing visible Workbench navigation is rejected'
$wrongWorkbenchNavigation = New-Q100FixtureNormalReady
$wrongWorkbenchNavigation.workbenchNavigationReady = 'true'
Assert-Q100Reader (-not (Test-NormalWorkbenchReady $wrongWorkbenchNavigation)) 'non-boolean Workbench navigation evidence is rejected'
$missingRetestControl = New-Q100FixtureNormalReady
$missingRetestControl.retestCustomerReady = $false
Assert-Q100Reader (-not (Test-NormalWorkbenchReady $missingRetestControl)) 'missing current customer retest control is rejected'
$missingBoardPower = New-Q100FixtureNormalReady
$missingBoardPower.boardPowerReady = $false
Assert-Q100Reader (-not (Test-NormalWorkbenchReady $missingBoardPower)) 'missing visible board power control is rejected'
$unknownBoardPowerLabel = New-Q100FixtureNormalReady
$unknownBoardPowerLabel.boardPowerLabel = 'Power'
Assert-Q100Reader (-not (Test-NormalWorkbenchReady $unknownBoardPowerLabel)) 'unknown board power semantic label is rejected'
$wrongProgramReadiness = New-Q100FixtureNormalReady
$wrongProgramReadiness.programReady = $false
Assert-Q100Reader (-not (Test-NormalWorkbenchReady $wrongProgramReadiness)) 'inconsistent public workbench readiness is rejected'
$leakedPrivacyReports = New-Q100FixtureOrdinaryPrivacyReports
$leakedPrivacyReports.q100Present = $true
Assert-Q100Reader (-not (Test-RouteReady 'q100-debugfalse' $leakedPrivacyReports)) 'debug-false report attribute leak is rejected'
foreach ($attribute in @('data-tsj-q100-report','data-tsj-a03-report',
        'data-tsj-task46-parity','data-tsj-future-answer','data-tsj-verification')) {
    $leakedDebugfalseAttribute = New-Q100FixtureOrdinaryPrivacyReports
    Add-Member -InputObject $leakedDebugfalseAttribute.developerAttributes `
        -MemberType NoteProperty -Name $attribute -Value $true
    Assert-Q100Reader (-not (Test-RouteReady 'q100-debugfalse' $leakedDebugfalseAttribute)) `
        "debug-false rejects every collected developer attribute: $attribute"
}
$verificationPresentWithoutText = New-Q100FixtureOrdinaryPrivacyReports
$verificationPresentWithoutText.verificationPresent = $true
Assert-Q100Reader (-not (Test-RouteReady 'q100-debugfalse' $verificationPresentWithoutText)) `
    'debug-false rejects a present empty verifier attribute'
$missingDebugfalseAttributes = New-Q100FixtureOrdinaryPrivacyReports
[void]$missingDebugfalseAttributes.PSObject.Properties.Remove('developerAttributes')
Assert-Q100Reader (-not (Test-RouteReady 'q100-debugfalse' $missingDebugfalseAttributes)) `
    'debug-false fails closed when developer-attribute collection is missing'
$nullDebugfalseAttributes = New-Q100FixtureOrdinaryPrivacyReports
$nullDebugfalseAttributes.developerAttributes = $null
Assert-Q100Reader (-not (Test-RouteReady 'q100-debugfalse' $nullDebugfalseAttributes)) `
    'debug-false fails closed when developer-attribute collection is null'
$emptyArrayDebugfalseAttributes = New-Q100FixtureOrdinaryPrivacyReports
$emptyArrayDebugfalseAttributes.developerAttributes = @()
Assert-Q100Reader (-not (Test-RouteReady 'q100-debugfalse' $emptyArrayDebugfalseAttributes)) `
    'empty array cannot masquerade as an empty developer-attribute object'
$duplicateArrayDebugfalseAttributes = New-Q100FixtureOrdinaryPrivacyReports
$duplicateArrayDebugfalseAttributes.developerAttributes = @('data-tsj-a03-report','data-tsj-a03-report')
Assert-Q100Reader (-not (Test-RouteReady 'q100-debugfalse' $duplicateArrayDebugfalseAttributes)) `
    'duplicate/lax developer-attribute arrays are rejected'

$playerAttributes = [pscustomobject]@{}
$playerReports = [pscustomobject]@{
    ready='complete'; verification=''; verificationPresent=$false; q100=''; q100Present=$false
    developerAttributes=$playerAttributes
    normalReady=(New-Q100FixtureNormalReady)
}
Assert-Q100Reader ($playerReports.ready -ceq 'complete' -and
    $playerReports.verificationPresent -is [bool] -and -not $playerReports.verificationPresent -and
    $playerReports.q100Present -is [bool] -and -not $playerReports.q100Present -and
    (Test-NormalWorkbenchReady $playerReports.normalReady)) `
    'Player fixture is fully loaded and ordinarily ready'
Assert-Q100Reader ($playerAttributes -is [pscustomobject] -and @($playerAttributes.PSObject.Properties).Count -eq 0) `
    'Player fixture has an empty developer-attribute object'
Assert-Q100Reader (Test-RouteReady 'q100-player' $playerReports) 'ordinary Player UI is ready without any developer attribute'
$privacyCanaryFixture = New-Q100FixturePrivacyCanaryEvidence
Assert-Q100Reader (Test-Q100PlayerPrivacyCanaryEvidence $privacyCanaryFixture.canary $privacyCanaryFixture.cleanup) `
    'both real-collector privacy sentinels are rejected and cleaned'
$privacyCanaryMissedLeak = New-Q100FixturePrivacyCanaryEvidence
$privacyCanaryMissedLeak.canary.cases[0].developerLeakDetected = $false
Assert-Q100Reader (-not (Test-Q100PlayerPrivacyCanaryEvidence $privacyCanaryMissedLeak.canary $privacyCanaryMissedLeak.cleanup)) `
    'privacy canary metadata cannot claim a missed leak'
$privacyCanaryAccepted = New-Q100FixturePrivacyCanaryEvidence
$privacyCanaryAccepted.canary.cases[1].playerPrivacyRejected = $false
Assert-Q100Reader (-not (Test-Q100PlayerPrivacyCanaryEvidence $privacyCanaryAccepted.canary $privacyCanaryAccepted.cleanup)) `
    'privacy canary metadata cannot claim an accepted sentinel'
$privacyCanaryWrongOrder = New-Q100FixturePrivacyCanaryEvidence
$privacyCanaryWrongOrder.canary.sentinels = @('data-tsj-future-answer','data-tsj-q100-fault')
Assert-Q100Reader (-not (Test-Q100PlayerPrivacyCanaryEvidence $privacyCanaryWrongOrder.canary $privacyCanaryWrongOrder.cleanup)) `
    'privacy canary sentinels must match the exact expected identities and order'
$privacyCanaryCleanupMissing = New-Q100FixturePrivacyCanaryEvidence
$privacyCanaryCleanupMissing.cleanup.cases[0].removed = $false
Assert-Q100Reader (-not (Test-Q100PlayerPrivacyCanaryEvidence $privacyCanaryCleanupMissing.canary $privacyCanaryCleanupMissing.cleanup)) `
    'privacy canary cannot pass without both sentinel cleanups'
Assert-Q100Reader (Test-Q100PlayerArtifactMarkers 'compiled-player-module') 'clean compiled Player text passes marker scan'
foreach ($marker in @('data-tsj-q100-report','q100-electrical-reference-v1','q100-electrical-reference-v2',
        'q100-electrical-reference-v3',
        'RB100_CONTROL_PILOT','Rb100ElectricalPilot','Rb100PilotReference',
        'Rb100PilotFaults','Rb100Plan','tsjQ100Fault','tsjVerifyQ100','tsjQ100Variant',
        'tsjQ100Canary','tsjQ100Dt','Q100ElectricalPilotDeveloperVerifier')) {
    Assert-Q100Reader (-not (Test-Q100PlayerArtifactMarkers ('compiled-player-module ' + $marker))) `
        "compiled Player marker '$marker' is rejected"
}
$forgedPlayerReport = [pscustomobject]@{
    ready='complete'; verification='PASS:q100'; verificationPresent=$true; q100='{}'; q100Present=$true
    developerAttributes=[pscustomobject]@{'data-tsj-q100-report'=$true}
    normalReady=(New-Q100FixtureNormalReady)
}
Assert-Q100Reader (Test-Q100PlayerDeveloperLeak $forgedPlayerReport) 'Player developer report is identified as a leak'
Assert-Q100Reader (-not (Test-RouteReady 'q100-player' $forgedPlayerReport)) 'forged Q100 report cannot certify Player privacy'
$forgedOtherDeveloperAttribute = [pscustomobject]@{
    ready='complete'; verification=''; verificationPresent=$false; q100=''; q100Present=$false
    developerAttributes=[pscustomobject]@{'data-tsj-a03-report'=$true}
    normalReady=(New-Q100FixtureNormalReady)
}
Assert-Q100Reader (-not (Test-RouteReady 'q100-player' $forgedOtherDeveloperAttribute)) 'other developer report attributes are forbidden in Player'
$forgedTask46Attribute = [pscustomobject]@{
    ready='complete'; verification=''; verificationPresent=$false; q100=''; q100Present=$false
    developerAttributes=[pscustomobject]@{'data-tsj-task46-parity'=$true}
    normalReady=(New-Q100FixtureNormalReady)
}
Assert-Q100Reader (-not (Test-RouteReady 'q100-player' $forgedTask46Attribute)) 'legacy task46 diagnostic attributes are forbidden in Player'
$forgedFutureAttribute = [pscustomobject]@{
    ready='complete'; verification=''; verificationPresent=$false; q100=''; q100Present=$false
    developerAttributes=[pscustomobject]@{'data-tsj-future-answer'=$true}
    normalReady=(New-Q100FixtureNormalReady)
}
Assert-Q100Reader (-not (Test-RouteReady 'q100-player' $forgedFutureAttribute)) `
    'unknown future developer attributes are forbidden in Player'
$playerNotReady = [pscustomobject]@{
    ready='complete'; verification=''; verificationPresent=$false; q100=''; q100Present=$false
    developerAttributes=$playerAttributes
    normalReady=(New-Q100FixtureNormalReady)
}
$playerNotReady.normalReady.programReady = $false
Assert-Q100Reader (-not (Test-RouteReady 'q100-player' $playerNotReady)) 'Player privacy alone cannot certify a not-ready LED UI'
$playerDefinition = Get-Q100PlayerRouteDefinition
Assert-Q100Reader ($playerDefinition.kind -ceq 'q100-player' -and $playerDefinition.reportNames.Count -eq 0 -and
    $playerDefinition.query -match 'tsjDebug=true' -and $playerDefinition.query -match 'tsjVerifyQ100=true' -and
    $playerDefinition.query -match 'tsjQ100Variant=SHARED_HYSTERETIC_LINEAR' -and
    $playerDefinition.query -match 'tsjQ100Fault=RSA_RESISTANCE' -and
    $playerDefinition.query -match 'tsjQ100Canary=after-step') 'Q100Player uses one explicit developer-probe URL'

$q100Definitions = @(Get-Q100RouteDefinitions $false)
$q100FaultDefinitions = @(Get-Q100RouteDefinitions $true)
$currentDefinitions = @(Get-RouteDefinitions 'Current' $false)
$playerDefinitions = @(Get-RouteDefinitions 'Q100Player' $false)
$interactionLockDefinitions = @($q100Definitions | Where-Object { $_.kind -ceq 'q100-interaction-lock' })
Assert-Q100Reader ($q100Definitions.Count -eq 11) 'focused Q100 has four healthy, three preflights, three canaries, and privacy'
Assert-Q100Reader (@($q100Definitions | Where-Object { $_.kind -ceq 'q100-healthy' }).Count -eq 4) 'Q100 has both variants at both timesteps'
Assert-Q100Reader (@($q100Definitions | Where-Object { $_.kind -like 'q100-preflight-*' }).Count -eq 3) 'Q100 has three invalid preflight routes'
Assert-Q100Reader (@($q100Definitions | Where-Object { $_.kind -in @('q100-after-step','q100-cancel','q100-interaction-lock','q100-debugfalse') }).Count -eq 4) 'Q100 has three canaries and the privacy route'
Assert-Q100Reader ($interactionLockDefinitions.Count -eq 1 -and
    $interactionLockDefinitions[0].query -match 'tsjQ100Canary=interaction-lock') `
    'Q100 records exactly one interaction-lock canary route'
Assert-Q100Reader (@($q100Definitions | Where-Object { $_.kind -ceq 'q100-preflight-variant' })[0].expectedError -ceq 'Unknown RB100 structural variant') 'invalid variant expects stable producer error'
Assert-Q100Reader ($q100FaultDefinitions.Count -eq 62) 'separate Q100 fault gate is exactly 2x31'
Assert-Q100Reader (@($q100FaultDefinitions | Where-Object { $_.dt -ne 0.00005 }).Count -eq 0 -and
    @($q100FaultDefinitions | Where-Object { $_.query -notlike '*tsjQ100Dt=0.00005*' }).Count -eq 0) `
    'all 62 Q100 fault routes select 50 us'
Assert-Q100Reader ($playerDefinitions.Count -eq 1 -and $playerDefinitions[0].kind -ceq 'q100-player') 'Q100Player is a dedicated one-route gate'
Assert-Q100Reader ($currentDefinitions.Count -eq 9) 'Current browser regression route count remains nine'

$healthyRoutes = New-Object Collections.Generic.List[object]
foreach ($variant in @('SHARED_HYSTERETIC_LINEAR','SEPARATE_DIRECT_SWITCHING')) {
    foreach ($step in @([pscustomobject]@{ dt=0.0001; token='100us' },
            [pscustomobject]@{ dt=0.00005; token='50us' })) {
        $reportText = ConvertTo-Q100FixtureText (New-Q100FixtureReport $variant 'HEALTHY' $step.dt)
        [void]$healthyRoutes.Add((New-Q100FixtureRoute ('q100-healthy-' + $variant + '-' + $step.token) 'q100-healthy' $reportText))
    }
}
$healthy50Reference = ConvertFrom-Q100ReportText $healthyRoutes[1].reports.q100
Assert-Q100Reader ($healthy50Reference.reference.work -eq 197) '50 us healthy work count is batches plus six analyses and construction'
Assert-Q100Reader (Test-Q100HealthyMatrix $healthyRoutes.ToArray()) 'both variants and timestep-paired healthy matrix'
$sensitiveRoutes = @($healthyRoutes.ToArray())
$sensitiveReport = New-Q100FixtureReport 'SHARED_HYSTERETIC_LINEAR' 'HEALTHY' 0.00005
$sensitiveReport.reference.observations[0].values[0] = 12.2
$sensitiveRoutes[1].reports.q100 = ConvertTo-Q100FixtureText $sensitiveReport
Assert-Q100Reader (-not (Test-Q100HealthyMatrix $sensitiveRoutes)) 'timestep sensitivity above 0.15 V is rejected'

$faultRoutes = New-Object Collections.Generic.List[object]
foreach ($variant in @('SHARED_HYSTERETIC_LINEAR','SEPARATE_DIRECT_SWITCHING')) {
    foreach ($faultId in (Get-Q100FaultIds)) {
        $reportText = ConvertTo-Q100FixtureText (New-Q100FixtureReport $variant $faultId 0.00005)
        [void]$faultRoutes.Add((New-Q100FixtureRoute ('q100-fault-' + $variant + '-' + $faultId) 'q100-fault' $reportText))
    }
}
Assert-Q100Reader (Test-Q100FaultMatrix $faultRoutes.ToArray()) 'exact ordered two-variant 31-fault matrix'
$missingFault = @($faultRoutes.ToArray() | Select-Object -Skip 1)
Assert-Q100Reader (-not (Test-Q100FaultMatrix $missingFault)) 'missing fault route is rejected'
$reorderedFaults = @($faultRoutes.ToArray())
$temporary = $reorderedFaults[0]
$reorderedFaults[0] = $reorderedFaults[1]
$reorderedFaults[1] = $temporary
Assert-Q100Reader (-not (Test-Q100FaultMatrix $reorderedFaults)) 'fault order is exact'
$duplicateFaults = @($faultRoutes.ToArray())
$duplicateFaults[1] = $duplicateFaults[0]
Assert-Q100Reader (-not (Test-Q100FaultMatrix $duplicateFaults)) 'duplicate fault is rejected'
$wrongVariantRoutes = @($faultRoutes.ToArray())
$wrongVariantReport = ConvertTo-Q100FixtureText (New-Q100FixtureReport 'SEPARATE_DIRECT_SWITCHING' 'RENAC_RESISTANCE' 0.00005)
$wrongVariantRoutes[0].reports.q100 = $wrongVariantReport
Assert-Q100Reader (-not (Test-Q100FaultMatrix $wrongVariantRoutes)) 'fault route with wrong variant is rejected'
$wrongFaultRoutes = @($faultRoutes.ToArray())
$wrongFaultReport = ConvertTo-Q100FixtureText (New-Q100FixtureReport 'SHARED_HYSTERETIC_LINEAR' 'REN_RESISTANCE' 0.00005)
$wrongFaultRoutes[0].reports.q100 = $wrongFaultReport
Assert-Q100Reader (-not (Test-Q100FaultMatrix $wrongFaultRoutes)) 'fault route with wrong identity is rejected'

Write-Output ("PASS: A04 report reader fixtures assertions={0}; six-case current version 6 and malformed reports." -f $script:A04ReaderAssertionCount)
Write-Output 'PASS: Q100 strict reader contracts, independent electrical oracle, canaries, interaction lock, Player privacy/artifact markers, timestep and 2x31 matrix.'
