[CmdletBinding()]
param()

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

# Reuse maintained Q100/A04 strict reader tests and fixture builders.
# This synthetic contract does not start a browser, preview, simulator or
# CircuitJS runtime.
. (Join-Path $PSScriptRoot 'verify-q100-evidence.ps1')

# Load only the three maintained E06 readers from their AST source.
$readerPath = Join-Path $PSScriptRoot 'verify-a03-browser.ps1'
$tokens = $null
$parseErrors = $null
$ast = [Management.Automation.Language.Parser]::ParseFile(
    $readerPath, [ref]$tokens, [ref]$parseErrors)
if ($parseErrors.Count) { throw 'E06 browser reader syntax error.' }
foreach ($name in @(
        'Test-E06AcceptanceCanaryReport',
        'Test-E06AcceptanceMatrix',
        'Get-E06AcceptanceRouteDefinitions')) {
    $nodes = @($ast.FindAll({ param($node)
        $node -is [Management.Automation.Language.FunctionDefinitionAst] -and $node.Name -ceq $name
    }, $true))
    if ($nodes.Count -ne 1) { throw ('Missing unique E06 reader function ' + $name) }
    . ([ScriptBlock]::Create($nodes[0].Extent.Text))
}

$script:E06AssertionCount = 0
function Assert-E06([bool]$Condition, [string]$Label) {
    $script:E06AssertionCount++
    if (-not $Condition) { throw ('E06 acceptance contract failed: ' + $Label) }
}

function New-E06CanaryFixture([string]$Canary) {
    $late = $Canary -ceq 'e06-late-callback'
    $message = if ($late) { 'e06-explicit-late-step-finished' } else { 'e06-explicit-accepted-event' }
    $phase = if ($late) { 'STEP_FINISHED_AFTER_E06_STAGE' } else { 'ACCEPTED_EVENT_AFTER_E06_STAGE' }
    return [pscustomobject][ordered]@{
        protocol='tsj-e06-acceptance-boundary-canary-v1'
        status='PASS_E06_ACCEPTANCE_BOUNDARY_CANARY'
        epoch='tsj-generation-dependencies-v26'
        canary=$Canary; family='RB56_CONTROL'; seed='-8360266000248569150'
        cleanup='PASS'; cleanupError=$null; error=$null
        quickPlaySelectionVerified=$true; fixedStepVerified=$true
        exactCountVerified=$true; expectedFailureObserved=$true
        operationMs=1000; cleanupMs=50; maxUnitMs=500
        operationLimitMs=90000; cleanupLimitMs=120000; maxUnitLimitMs=5000
        interTrialLimitMs=500; maxBatchLimitSteps=64; maxBatchSteps=64
        maxStepTimeError=0.0; work=23; acceptedSteps=1280
        timeStepAtRejection=0.000025; minTimeStepAtRejection=0.000025
        maxTimeStepAtRejection=0.000025; adjustTimeStepAtRejection=$false
        expectedFailureType='java.lang.IllegalStateException'
        observedFailureType='java.lang.IllegalStateException'
        expectedFailureMessage=$message; observedFailureMessage=$message; failurePhase=$phase
        predecessor=[pscustomobject][ordered]@{
            e06CountBefore=1; e06CountAfter=1
            graphIdentityStable=$true; ownerIdentityStable=$true; exactDumpRestored=$true
            quickPlaySessionIdentityStable=$true; quickPlaySelectionIdentityStable=$true
            modelIdentityStable=$true; acceptedSnapshotUnchangedBefore=$true
            acceptedSnapshotUnchangedAfter=$true; acceptedPredictorAvailableBefore=$true
            acceptedPredictorAvailableAfter=$true
        }
        candidate=[pscustomobject][ordered]@{
            e06Count=1; acceptedPredictorAvailableBefore=$true
            privateCandidatePendingObserved=$true; privateCandidatePendingAfterRejection=$true
            committedSnapshotUnchanged=$true; simulatedSecondsAdvanced=$true
            candidateOutOfLiveGraph=$true; privateAuthorityClosed=$true
            acceptedStepAtRejectedTime=$false; postStepCallbackObserved=$late
            eventCallbackObserved=(-not $late); eventCandidateObserved=(-not $late)
            rejectedAttemptSteps=1; rejectedAttemptTimeStepCount=1
            simulatedSecondsBefore=0.032; simulatedSecondsAfter=0.032025
        }
    }
}

function ConvertTo-E06Text([object]$Value) {
    return ($Value | ConvertTo-Json -Depth 24 -Compress)
}

function Set-E06Path([object]$Target, [string]$Path, [object]$Value, [bool]$Remove) {
    $parts = $Path.Split('.')
    $owner = $Target
    for ($i = 0; $i -lt ($parts.Count - 1); $i++) {
        $property = $owner.PSObject.Properties[$parts[$i]]
        if ($null -eq $property) { throw ('Fixture path missing: ' + $Path) }
        $owner = $property.Value
    }
    if ($Remove) {
        [void]$owner.PSObject.Properties.Remove($parts[$parts.Count - 1])
    } else {
        $owner.PSObject.Properties[$parts[$parts.Count - 1]].Value = $Value
    }
}

function Assert-E06CanaryRejected([object]$Base, [string]$Canary, [object]$Case) {
    $copy = (ConvertTo-E06Text $Base) | ConvertFrom-Json -ErrorAction Stop
    Set-E06Path $copy $Case.path $Case.value ([bool]$Case.remove)
    $text = ConvertTo-E06Text $copy
    Assert-E06 (-not (Test-E06AcceptanceCanaryReport $text $Canary)) $Case.label
}

function New-E06ExpectedRouteCensus() {
    return @(
        [pscustomobject]@{ name='e06-late-callback'; kind='e06-late-callback'; variant=$null; fault=$null; dt=$null
            query='tsjQuickPlay=true&tsjQuickPlayTestFamily=11&tsjQuickPlayTestSeed=-8360266000248569150&tsjDebug=true&tsjVerifyQ100=true&tsjQ100Canary=e06-late-callback&running=true'; report='e06Acceptance' },
        [pscustomobject]@{ name='e06-event-failure'; kind='e06-event-failure'; variant=$null; fault=$null; dt=$null
            query='tsjQuickPlay=true&tsjQuickPlayTestFamily=11&tsjQuickPlayTestSeed=-8360266000248569150&tsjDebug=true&tsjVerifyQ100=true&tsjQ100Canary=e06-event-failure&running=true'; report='e06Acceptance' },
        [pscustomobject]@{ name='q100-healthy-SHARED_HYSTERETIC_LINEAR-100us'; kind='q100-healthy'; variant='SHARED_HYSTERETIC_LINEAR'; fault='HEALTHY'; dt=0.0001
            query='tsjChallenge=led&seed=3&tsjDebug=true&tsjVerifyQ100=true&tsjQ100Variant=SHARED_HYSTERETIC_LINEAR&tsjQ100Dt=0.0001&running=true'; report='q100' },
        [pscustomobject]@{ name='q100-healthy-SHARED_HYSTERETIC_LINEAR-50us'; kind='q100-healthy'; variant='SHARED_HYSTERETIC_LINEAR'; fault='HEALTHY'; dt=0.00005
            query='tsjChallenge=led&seed=3&tsjDebug=true&tsjVerifyQ100=true&tsjQ100Variant=SHARED_HYSTERETIC_LINEAR&tsjQ100Dt=0.00005&running=true'; report='q100' },
        [pscustomobject]@{ name='q100-healthy-SEPARATE_DIRECT_SWITCHING-100us'; kind='q100-healthy'; variant='SEPARATE_DIRECT_SWITCHING'; fault='HEALTHY'; dt=0.0001
            query='tsjChallenge=led&seed=3&tsjDebug=true&tsjVerifyQ100=true&tsjQ100Variant=SEPARATE_DIRECT_SWITCHING&tsjQ100Dt=0.0001&running=true'; report='q100' },
        [pscustomobject]@{ name='q100-healthy-SEPARATE_DIRECT_SWITCHING-50us'; kind='q100-healthy'; variant='SEPARATE_DIRECT_SWITCHING'; fault='HEALTHY'; dt=0.00005
            query='tsjChallenge=led&seed=3&tsjDebug=true&tsjVerifyQ100=true&tsjQ100Variant=SEPARATE_DIRECT_SWITCHING&tsjQ100Dt=0.00005&running=true'; report='q100' },
        [pscustomobject]@{ name='q100-fault-SHARED_HYSTERETIC_LINEAR-RENAC_RESISTANCE'; kind='q100-fault'; variant='SHARED_HYSTERETIC_LINEAR'; fault='RENAC_RESISTANCE'; dt=0.00005
            query='tsjChallenge=led&seed=3&tsjDebug=true&tsjVerifyQ100=true&tsjQ100Variant=SHARED_HYSTERETIC_LINEAR&tsjQ100Fault=RENAC_RESISTANCE&tsjQ100Dt=0.00005&running=true'; report='q100' },
        [pscustomobject]@{ name='q100-fault-SHARED_HYSTERETIC_LINEAR-CIN33_SHUNT'; kind='q100-fault'; variant='SHARED_HYSTERETIC_LINEAR'; fault='CIN33_SHUNT'; dt=0.00005
            query='tsjChallenge=led&seed=3&tsjDebug=true&tsjVerifyQ100=true&tsjQ100Variant=SHARED_HYSTERETIC_LINEAR&tsjQ100Fault=CIN33_SHUNT&tsjQ100Dt=0.00005&running=true'; report='q100' },
        [pscustomobject]@{ name='q100-fault-SEPARATE_DIRECT_SWITCHING-RENAC_RESISTANCE'; kind='q100-fault'; variant='SEPARATE_DIRECT_SWITCHING'; fault='RENAC_RESISTANCE'; dt=0.00005
            query='tsjChallenge=led&seed=3&tsjDebug=true&tsjVerifyQ100=true&tsjQ100Variant=SEPARATE_DIRECT_SWITCHING&tsjQ100Fault=RENAC_RESISTANCE&tsjQ100Dt=0.00005&running=true'; report='q100' },
        [pscustomobject]@{ name='q100-fault-SEPARATE_DIRECT_SWITCHING-CIN33_SHUNT'; kind='q100-fault'; variant='SEPARATE_DIRECT_SWITCHING'; fault='CIN33_SHUNT'; dt=0.00005
            query='tsjChallenge=led&seed=3&tsjDebug=true&tsjVerifyQ100=true&tsjQ100Variant=SEPARATE_DIRECT_SWITCHING&tsjQ100Fault=CIN33_SHUNT&tsjQ100Dt=0.00005&running=true'; report='q100' }
    )
}

function New-E06FixtureRoutes([object[]]$Census) {
    $routes = New-Object Collections.Generic.List[object]
    for ($i = 0; $i -lt $Census.Count; $i++) {
        $item = $Census[$i]
        if ($i -lt 2) {
            $reportText = ConvertTo-E06Text (New-E06CanaryFixture $item.name)
            [void]$routes.Add([pscustomobject]@{
                name=$item.name; kind=$item.kind; status='PASS'; verification='PASS:e06-acceptance-canary'
                e06AcceptanceReportAttributePresent=$true
                reports=[pscustomobject]@{ e06Acceptance=$reportText }
            })
        } else {
            $reportText = ConvertTo-Q100FixtureText (New-Q100FixtureReport $item.variant $item.fault $item.dt)
            $route = New-Q100FixtureRoute $item.name $item.kind $reportText
            [void]$routes.Add([pscustomobject]@{
                name=$route.name; kind=$route.kind; status='PASS'; verification='PASS:q100'
                q100ReportAttributePresent=$route.q100ReportAttributePresent; reports=$route.reports
            })
        }
    }
    return $routes.ToArray()
}

$lateFixture = New-E06CanaryFixture 'e06-late-callback'
$eventFixture = New-E06CanaryFixture 'e06-event-failure'
$latePositive = Test-E06AcceptanceCanaryReport (ConvertTo-E06Text $lateFixture) 'e06-late-callback'
Assert-E06 $latePositive 'literal late-callback positive fixture'
$eventPositive = Test-E06AcceptanceCanaryReport (ConvertTo-E06Text $eventFixture) 'e06-event-failure'
Assert-E06 $eventPositive 'literal accepted-event positive fixture'

$badCanaryCases = @(
    @{label='wrong protocol'; path='protocol'; value='tsj-e06-acceptance-boundary-canary-v0'; remove=$false},
    @{label='missing protocol'; path='protocol'; value=$null; remove=$true},
    @{label='wrong status'; path='status'; value='PASS'; remove=$false},
    @{label='missing epoch'; path='epoch'; value=$null; remove=$true},
    @{label='wrong dependency epoch'; path='epoch'; value='tsj-generation-dependencies-v20'; remove=$false},
    @{label='wrong family'; path='family'; value='OTHER_FAMILY'; remove=$false},
    @{label='seed rounded'; path='seed'; value='-8360266000248569000'; remove=$false},
    @{label='seed changed to numeric JSON'; path='seed'; value=[long](-8360266000248569150); remove=$false},
    @{label='missing seed'; path='seed'; value=$null; remove=$true},
    @{label='cleanup failure'; path='cleanup'; value='FAIL'; remove=$false},
    @{label='cleanup error'; path='cleanupError'; value='cleanup-failed'; remove=$false},
    @{label='unexpected operation error'; path='error'; value='unexpected-error'; remove=$false},
    @{label='expected failure type mismatch'; path='expectedFailureType'; value='java.lang.RuntimeException'; remove=$false},
    @{label='observed failure type mismatch'; path='observedFailureType'; value='java.lang.RuntimeException'; remove=$false},
    @{label='expected failure message mismatch'; path='expectedFailureMessage'; value='other'; remove=$false},
    @{label='observed failure message mismatch'; path='observedFailureMessage'; value='other'; remove=$false},
    @{label='wrong failure phase'; path='failurePhase'; value='BEFORE_E06_STAGE'; remove=$false},
    @{label='expected failure proof false'; path='expectedFailureObserved'; value=$false; remove=$false},
    @{label='fixed-step proof false'; path='fixedStepVerified'; value=$false; remove=$false},
    @{label='exact-count proof false'; path='exactCountVerified'; value=$false; remove=$false},
    @{label='quick-play selection proof false'; path='quickPlaySelectionVerified'; value=$false; remove=$false},
    @{label='boolean supplied as string'; path='fixedStepVerified'; value='true'; remove=$false},
    @{label='accepted steps mismatch'; path='acceptedSteps'; value=1344; remove=$false},
    @{label='accepted steps string'; path='acceptedSteps'; value='1280'; remove=$false},
    @{label='accepted steps fractional'; path='acceptedSteps'; value=1280.5; remove=$false},
    @{label='accepted steps out of range'; path='acceptedSteps'; value=8001; remove=$false},
    @{label='work formula mismatch'; path='work'; value=22; remove=$false},
    @{label='work supplied as string'; path='work'; value='23'; remove=$false},
    @{label='operation time string'; path='operationMs'; value='1000'; remove=$false},
    @{label='operation time above limit'; path='operationMs'; value=90001; remove=$false},
    @{label='cleanup time above limit'; path='cleanupMs'; value=120001; remove=$false},
    @{label='unit time above limit'; path='maxUnitMs'; value=5001; remove=$false},
    @{label='operation limit changed'; path='operationLimitMs'; value=89999; remove=$false},
    @{label='cleanup limit changed'; path='cleanupLimitMs'; value=119999; remove=$false},
    @{label='unit limit changed'; path='maxUnitLimitMs'; value=5001; remove=$false},
    @{label='intertrial limit changed'; path='interTrialLimitMs'; value=499; remove=$false},
    @{label='batch limit changed'; path='maxBatchLimitSteps'; value=65; remove=$false},
    @{label='observed batch changed'; path='maxBatchSteps'; value=32; remove=$false},
    @{label='step-time error out of range'; path='maxStepTimeError'; value=0.0001; remove=$false},
    @{label='rejection timestep changed'; path='timeStepAtRejection'; value=0.00005; remove=$false},
    @{label='minimum rejection timestep changed'; path='minTimeStepAtRejection'; value=0.00005; remove=$false},
    @{label='maximum rejection timestep changed'; path='maxTimeStepAtRejection'; value=0.00005; remove=$false},
    @{label='adaptive stepping true'; path='adjustTimeStepAtRejection'; value=$true; remove=$false},
    @{label='missing release proof'; path='candidate.privateAuthorityClosed'; value=$null; remove=$true},
    @{label='missing identity proof'; path='predecessor.graphIdentityStable'; value=$null; remove=$true},
    @{label='predecessor snapshot changed'; path='predecessor.acceptedSnapshotUnchangedAfter'; value=$false; remove=$false},
    @{label='candidate pending after rejection false'; path='candidate.privateCandidatePendingAfterRejection'; value=$false; remove=$false},
    @{label='candidate committed snapshot changed'; path='candidate.committedSnapshotUnchanged'; value=$false; remove=$false},
    @{label='accepted step claimed at rejected time'; path='candidate.acceptedStepAtRejectedTime'; value=$true; remove=$false},
    @{label='late post-step callback flag changed'; path='candidate.postStepCallbackObserved'; value=$false; remove=$false},
    @{label='late event callback flag changed'; path='candidate.eventCallbackObserved'; value=$true; remove=$false},
    @{label='late event candidate flag changed'; path='candidate.eventCandidateObserved'; value=$true; remove=$false},
    @{label='predecessor graph identity unstable'; path='predecessor.graphIdentityStable'; value=$false; remove=$false},
    @{label='predecessor owner identity unstable'; path='predecessor.ownerIdentityStable'; value=$false; remove=$false},
    @{label='predecessor exact dump not restored'; path='predecessor.exactDumpRestored'; value=$false; remove=$false},
    @{label='predecessor quick-play session changed'; path='predecessor.quickPlaySessionIdentityStable'; value=$false; remove=$false},
    @{label='predecessor selection changed'; path='predecessor.quickPlaySelectionIdentityStable'; value=$false; remove=$false},
    @{label='predecessor model identity changed'; path='predecessor.modelIdentityStable'; value=$false; remove=$false},
    @{label='predecessor accepted snapshot changed before'; path='predecessor.acceptedSnapshotUnchangedBefore'; value=$false; remove=$false},
    @{label='predecessor predictor unavailable before'; path='predecessor.acceptedPredictorAvailableBefore'; value=$false; remove=$false},
    @{label='predecessor predictor unavailable after'; path='predecessor.acceptedPredictorAvailableAfter'; value=$false; remove=$false},
    @{label='candidate predictor unavailable'; path='candidate.acceptedPredictorAvailableBefore'; value=$false; remove=$false},
    @{label='private candidate was not observed pending'; path='candidate.privateCandidatePendingObserved'; value=$false; remove=$false},
    @{label='candidate simulated time did not advance'; path='candidate.simulatedSecondsAdvanced'; value=$false; remove=$false},
    @{label='candidate remains in live graph'; path='candidate.candidateOutOfLiveGraph'; value=$false; remove=$false},
    @{label='predecessor count changed'; path='predecessor.e06CountAfter'; value=2; remove=$false},
    @{label='candidate count changed'; path='candidate.e06Count'; value=2; remove=$false},
    @{label='rejected step count changed'; path='candidate.rejectedAttemptSteps'; value=2; remove=$false},
    @{label='rejected timestep count changed'; path='candidate.rejectedAttemptTimeStepCount'; value=2; remove=$false},
    @{label='accepted time before not derived from count'; path='candidate.simulatedSecondsBefore'; value=0.031; remove=$false},
    @{label='rejected attempt advanced accepted time'; path='candidate.simulatedSecondsAfter'; value=0.0321; remove=$false}
)
foreach ($case in $badCanaryCases) {
    Assert-E06CanaryRejected $lateFixture 'e06-late-callback' $case
}
$nonFiniteText = (ConvertTo-E06Text $lateFixture).Replace('"operationMs":1000', '"operationMs":1e999')
$nonFiniteRejected = -not (Test-E06AcceptanceCanaryReport $nonFiniteText 'e06-late-callback')
Assert-E06 $nonFiniteRejected 'nonfinite operation time token is rejected'
$unsupportedCanaryRejected = -not (Test-E06AcceptanceCanaryReport (ConvertTo-E06Text $lateFixture) 'unsupported-canary')
Assert-E06 $unsupportedCanaryRejected 'unsupported canary argument is rejected'
$wrongCanary = (ConvertTo-E06Text $lateFixture).Replace('e06-late-callback', 'e06-event-failure')
Assert-E06 (-not (Test-E06AcceptanceCanaryReport $wrongCanary 'e06-late-callback')) 'wrong canary identity is rejected'
$wrongExpectedPhase = (ConvertTo-E06Text $lateFixture).Replace('STEP_FINISHED_AFTER_E06_STAGE', 'BEFORE_E06_STAGE')
Assert-E06 (-not (Test-E06AcceptanceCanaryReport $wrongExpectedPhase 'e06-late-callback')) 'wrong late phase is rejected'
$badEventCallback = (ConvertTo-E06Text $eventFixture).Replace('"eventCallbackObserved":true', '"eventCallbackObserved":false')
Assert-E06 (-not (Test-E06AcceptanceCanaryReport $badEventCallback 'e06-event-failure')) 'event callback proof is required'
$badEventCandidate = (ConvertTo-E06Text $eventFixture).Replace('"eventCandidateObserved":true', '"eventCandidateObserved":false')
Assert-E06 (-not (Test-E06AcceptanceCanaryReport $badEventCandidate 'e06-event-failure')) 'event candidate proof is required'
$badEventPostStep = (ConvertTo-E06Text $eventFixture).Replace('"postStepCallbackObserved":false', '"postStepCallbackObserved":true')
Assert-E06 (-not (Test-E06AcceptanceCanaryReport $badEventPostStep 'e06-event-failure')) 'event canary rejects post-step callback proof'

$expectedCensus = @(New-E06ExpectedRouteCensus)
$definitions = @(Get-E06AcceptanceRouteDefinitions)
Assert-E06 ($expectedCensus.Count -eq 10) 'independent route census has ten cases'
Assert-E06 ($definitions.Count -eq 10) 'maintained route definitions have ten cases'
for ($i = 0; $i -lt 10; $i++) {
    $expected = $expectedCensus[$i]
    $actual = $definitions[$i]
    $matches = $actual.name -ceq $expected.name -and $actual.kind -ceq $expected.kind -and
        $actual.query -ceq $expected.query -and @($actual.reportNames).Count -eq 1 -and
        $actual.reportNames[0] -ceq $expected.report
    Assert-E06 $matches ('literal route definition ' + $expected.name)
    if ($i -ge 2) {
        $referenceMatches = $actual.variant -ceq $expected.variant -and
            $actual.fault -ceq $expected.fault -and [double]$actual.dt -eq [double]$expected.dt
        Assert-E06 $referenceMatches ('literal reference case ' + $expected.name)
    }
}

$goodRoutes = @(New-E06FixtureRoutes $expectedCensus)
Assert-E06 (Test-E06AcceptanceMatrix $goodRoutes) 'two canaries and eight finite references pass'
$missingCase = @($goodRoutes | Select-Object -Skip 1)
Assert-E06 (-not (Test-E06AcceptanceMatrix $missingCase)) 'missing matrix case rejected'
$duplicateCase = @(New-E06FixtureRoutes $expectedCensus)
$duplicateCase[9] = $duplicateCase[8]
Assert-E06 (-not (Test-E06AcceptanceMatrix $duplicateCase)) 'duplicate matrix case rejected'
$substitutedCase = @(New-E06FixtureRoutes $expectedCensus)
$substitutedCase[8].name = 'q100-fault-SEPARATE_DIRECT_SWITCHING-OTHER_SHUNT'
Assert-E06 (-not (Test-E06AcceptanceMatrix $substitutedCase)) 'substituted matrix identity rejected'
$reorderedCase = @(New-E06FixtureRoutes $expectedCensus)
$swap = $reorderedCase[0]; $reorderedCase[0] = $reorderedCase[1]; $reorderedCase[1] = $swap
Assert-E06 (-not (Test-E06AcceptanceMatrix $reorderedCase)) 'canary order is exact'
$wrongTerminal = @(New-E06FixtureRoutes $expectedCensus)
$wrongTerminal[0].verification = 'PASS:wrong-terminal'
Assert-E06 (-not (Test-E06AcceptanceMatrix $wrongTerminal)) 'wrong canary terminal rejected'
$wrongReferenceTerminal = @(New-E06FixtureRoutes $expectedCensus)
$wrongReferenceTerminal[2].verification = 'PASS:other-suite'
Assert-E06 (-not (Test-E06AcceptanceMatrix $wrongReferenceTerminal)) 'wrong reference terminal rejected'
$missingAttribute = @(New-E06FixtureRoutes $expectedCensus)
[void]$missingAttribute[0].PSObject.Properties.Remove('e06AcceptanceReportAttributePresent')
Assert-E06 (-not (Test-E06AcceptanceMatrix $missingAttribute)) 'missing canary report attribute rejected'
$falseAttribute = @(New-E06FixtureRoutes $expectedCensus)
$falseAttribute[2].q100ReportAttributePresent = $false
Assert-E06 (-not (Test-E06AcceptanceMatrix $falseAttribute)) 'false Q100 report attribute rejected'
$missingPayload = @(New-E06FixtureRoutes $expectedCensus)
[void]$missingPayload[1].reports.PSObject.Properties.Remove('e06Acceptance')
Assert-E06 (-not (Test-E06AcceptanceMatrix $missingPayload)) 'missing canary report payload rejected'
$wrongStatus = @(New-E06FixtureRoutes $expectedCensus)
$wrongStatus[7].status = 'FAIL'
Assert-E06 (-not (Test-E06AcceptanceMatrix $wrongStatus)) 'non-PASS selected route rejected'
$wrongKind = @(New-E06FixtureRoutes $expectedCensus)
$wrongKind[6].kind = 'q100-healthy'
Assert-E06 (-not (Test-E06AcceptanceMatrix $wrongKind)) 'wrong selected route kind rejected'

# The full 2-by-31 Q100 fault set cannot substitute for this finite matrix.
$faultDefinitions = @(Get-Q100RouteDefinitions $true | Where-Object { $_.kind -ceq 'q100-fault' })
Assert-E06 ($faultDefinitions.Count -eq 62) 'full Q100 fault population enumerates 62 cases'
$expanded = New-Object Collections.Generic.List[object]
foreach ($route in $goodRoutes) { [void]$expanded.Add($route) }
foreach ($definition in $faultDefinitions) {
    $text = ConvertTo-Q100FixtureText (New-Q100FixtureReport $definition.variant $definition.fault 0.00005)
    $route = New-Q100FixtureRoute $definition.name $definition.kind $text
    [void]$expanded.Add([pscustomobject]@{
        name=$route.name; kind=$route.kind; status='PASS'; verification='PASS:q100'
        q100ReportAttributePresent=$true; reports=$route.reports
    })
}
Assert-E06 ($expanded.Count -eq 72) 'ten selected routes and 62 full-population routes remain distinct'
Assert-E06 (-not (Test-E06AcceptanceMatrix $expanded.ToArray())) 'full matrix cannot masquerade as ten-route E06'

Assert-E06 (Test-Q100PlayerArtifactMarkers 'clean compiled Player artifact') 'clean Player artifact is accepted'
foreach ($marker in @(
        'data-tsj-e06-acceptance-canary',
        'tsj-e06-acceptance-boundary-canary-v1',
        'e06-explicit-late-step-finished',
        'e06-explicit-accepted-event')) {
    $markerRejected = -not (Test-Q100PlayerArtifactMarkers ('injected marker ' + $marker))
    Assert-E06 $markerRejected ('Player artifact rejects marker ' + $marker)
}

Write-Output ('PASS: E06 accepted-state reader fixtures assertions={0}; two typed canaries, 10-route matrix, malformed cases and four Player marker rejections.' -f $script:E06AssertionCount)
