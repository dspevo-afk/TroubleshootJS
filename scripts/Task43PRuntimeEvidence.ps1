param(
    [switch]$Task43PRuntimeEvidenceContractProbe
)

function Invoke-Task43PRuntimeInfrastructureError([string]$Message) {
    if (Get-Command Throw-VerifierInfrastructure -ErrorAction SilentlyContinue) {
        Throw-VerifierInfrastructure $Message
    }
    throw [InvalidOperationException]$Message
}

function Assert-Task43PRuntimeObject($Value, [string]$Path, [string[]]$Allowed,
        [string[]]$Required) {
    if ($null -eq $Value -or $Value -is [System.Array] -or
            $Value -isnot [pscustomobject]) {
        Invoke-Task43PRuntimeInfrastructureError "Task43P runtime evidence expected an object at $Path."
    }
    foreach ($property in @($Value.PSObject.Properties)) {
        if ($Allowed -cnotcontains [string]$property.Name) {
            Invoke-Task43PRuntimeInfrastructureError "Task43P runtime evidence contained unknown field '$($property.Name)' at $Path."
        }
    }
    foreach ($name in $Required) {
        if ($null -eq $Value.PSObject.Properties[$name]) {
            Invoke-Task43PRuntimeInfrastructureError "Task43P runtime evidence omitted required field '$name' at $Path."
        }
    }
}

function Assert-Task43PRuntimeString($Value, [string]$Path, [switch]$AllowNull) {
    if ($AllowNull -and $null -eq $Value) { return }
    if ($Value -isnot [string] -or [string]::IsNullOrWhiteSpace([string]$Value)) {
        Invoke-Task43PRuntimeInfrastructureError "Task43P runtime evidence expected a non-empty string at $Path."
    }
}

function Assert-Task43PRuntimeBoolean($Value, [string]$Path) {
    if ($Value -isnot [bool]) {
        Invoke-Task43PRuntimeInfrastructureError "Task43P runtime evidence expected an exact Boolean at $Path."
    }
}

function Assert-Task43PRuntimeNumber($Value, [string]$Path) {
    if ($null -eq $Value -or $Value -is [string] -or $Value -is [bool] -or
            $Value -isnot [ValueType]) {
        Invoke-Task43PRuntimeInfrastructureError "Task43P runtime evidence expected a JSON number at $Path."
    }
    try { $number = [double]$Value } catch {
        Invoke-Task43PRuntimeInfrastructureError "Task43P runtime evidence carried a malformed number at $Path."
    }
    if ([double]::IsNaN($number) -or [double]::IsInfinity($number)) {
        Invoke-Task43PRuntimeInfrastructureError "Task43P runtime evidence carried a non-finite number at $Path."
    }
}

function Assert-Task43PRuntimeInteger($Value, [string]$Path) {
    Assert-Task43PRuntimeNumber $Value $Path
    $number = [double]$Value
    if ($number -ne [math]::Truncate($number)) {
        Invoke-Task43PRuntimeInfrastructureError "Task43P runtime evidence expected an integral number at $Path."
    }
}

function Assert-Task43PRuntimeArray($Value, [string]$Path) {
    if ($Value -isnot [System.Array]) {
        Invoke-Task43PRuntimeInfrastructureError "Task43P runtime evidence expected an array at $Path."
    }
}

function Assert-Task43PRuntimeNullableBoolean($Value, [string]$Path) {
    if ($null -ne $Value) { Assert-Task43PRuntimeBoolean $Value $Path }
}

function Assert-Task43PRuntimeEnum($Value, [string]$Path, [string[]]$Values) {
    Assert-Task43PRuntimeString $Value $Path
    if ($Values -cnotcontains [string]$Value) {
        Invoke-Task43PRuntimeInfrastructureError "Task43P runtime evidence carried an invalid value at $Path."
    }
}

function Assert-Task43PRuntimePart($Value, [string]$Path) {
    $p = @('id','installed','faulted','original')
    Assert-Task43PRuntimeObject $Value $Path $p $p
    Assert-Task43PRuntimeString $Value.id "$Path.id"
    foreach ($n in @('installed','faulted','original')) { Assert-Task43PRuntimeBoolean $Value.$n "$Path.$n" }
}

function Assert-Task43PRuntimeExistingCase($Value, [string]$Path) {
    $p = @('lane','family','topology','seed','helperAssertionsCompleted','faultBefore',
        'faultAfter','originalPartBefore','installedTargetAfter','challengeStateAfter',
        'repairStatusAfter','completedAfter','retestPresentAfter','boardPowerAfter',
        'verificationPendingAfter','verificationAnalyzedAfter','modificationsFullyRestoredAfter',
        'candidateOwnerCurrent','candidateWasIsolated','ownerRestored')
    Assert-Task43PRuntimeObject $Value $Path $p $p
    Assert-Task43PRuntimeString $Value.lane "$Path.lane"
    Assert-Task43PRuntimeString $Value.family "$Path.family"
    Assert-Task43PRuntimeString $Value.topology "$Path.topology"
    Assert-Task43PRuntimeInteger $Value.seed "$Path.seed"
    Assert-Task43PRuntimeBoolean $Value.helperAssertionsCompleted "$Path.helperAssertionsCompleted"
    if (-not $Value.helperAssertionsCompleted) {
        Invoke-Task43PRuntimeInfrastructureError `
            "Task43P runtime existing helper assertions were not completed at $Path."
    }
    $fp=@('id','type','bindingIdentity','applied')
    foreach ($pair in @(@('faultBefore',$fp),@('faultAfter',@('id','type','target','bindingIdentityPreserved','applied')))) {
        $name=[string]$pair[0]; $allowed=[string[]]$pair[1]; Assert-Task43PRuntimeObject $Value.$name "$Path.$name" $allowed $allowed
        foreach ($s in @('id','type')) { Assert-Task43PRuntimeString $Value.$name.$s "$Path.$name.$s" }
        if ($name -eq 'faultAfter') {
            Assert-Task43PRuntimeString $Value.$name.target `
                "$Path.$name.target"
            Assert-Task43PRuntimeBoolean $Value.$name.bindingIdentityPreserved `
                "$Path.$name.bindingIdentityPreserved"
        } else {
            Assert-Task43PRuntimeBoolean $Value.$name.bindingIdentity `
                "$Path.$name.bindingIdentity"
        }
        Assert-Task43PRuntimeBoolean $Value.$name.applied "$Path.$name.applied"
    }
    Assert-Task43PRuntimePart $Value.originalPartBefore "$Path.originalPartBefore"
    if ($null -ne $Value.installedTargetAfter) { Assert-Task43PRuntimePart $Value.installedTargetAfter "$Path.installedTargetAfter" }
    foreach ($s in @('challengeStateAfter','repairStatusAfter','boardPowerAfter')) { Assert-Task43PRuntimeString $Value.$s "$Path.$s" }
    foreach ($b in @(
            'completedAfter', 'retestPresentAfter',
            'verificationPendingAfter', 'verificationAnalyzedAfter',
            'modificationsFullyRestoredAfter', 'candidateOwnerCurrent',
            'candidateWasIsolated', 'ownerRestored')) {
        Assert-Task43PRuntimeBoolean $Value.$b "$Path.$b"
    }
    if (-not $Value.candidateOwnerCurrent -or -not $Value.candidateWasIsolated -or
            -not $Value.ownerRestored -or
            -not $Value.faultBefore.bindingIdentity -or
            -not $Value.faultBefore.applied -or
            -not $Value.faultAfter.bindingIdentityPreserved -or
            -not $Value.faultAfter.applied -or
            -not $Value.originalPartBefore.installed -or
            -not $Value.originalPartBefore.faulted -or
            -not $Value.originalPartBefore.original) {
        Invoke-Task43PRuntimeInfrastructureError `
            "Task43P runtime existing execution/provenance assertions were false at $Path."
    }
    if ($Value.faultBefore.id -ne $Value.faultAfter.id -or
            $Value.faultBefore.type -ne $Value.faultAfter.type) {
        Invoke-Task43PRuntimeInfrastructureError `
            "Task43P runtime existing fault identity changed at $Path."
    }
}

function Assert-Task43PRuntimeExisting($Value, [string]$Path) {
    $properties = @('protocol', 'status', 'caseCount', 'cases',
        'originalOwnerRestored')
    Assert-Task43PRuntimeObject $Value $Path $properties $properties
    Assert-Task43PRuntimeString $Value.protocol "$Path.protocol"
    if ($Value.protocol -cne 'TSJ-TASK43P-ABCEF-1') {
        Invoke-Task43PRuntimeInfrastructureError 'Task43P runtime existing protocol mismatch.'
    }
    Assert-Task43PRuntimeEnum $Value.status "$Path.status" @('OBSERVED')
    Assert-Task43PRuntimeInteger $Value.caseCount "$Path.caseCount"
    Assert-Task43PRuntimeBoolean $Value.originalOwnerRestored "$Path.originalOwnerRestored"
    if (-not $Value.originalOwnerRestored) {
        Invoke-Task43PRuntimeInfrastructureError 'Task43P runtime existing owner restoration was false.'
    }
    Assert-Task43PRuntimeArray $Value.cases "$Path.cases"
    if (@($Value.cases).Count -ne 4 -or [int]$Value.caseCount -ne 4) {
        Invoke-Task43PRuntimeInfrastructureError 'Task43P runtime existing corpus count mismatch.'
    }

    $expected = @('A', 'B/F', 'C', 'E')
    $expectedFamilies = @{
        'A'   = @('LED_INDICATOR', '3')
        'B/F' = @('LED_INDICATOR', '0')
        'C'   = @('LED_INDICATOR', '3')
        'E'   = @('RC_DELAY', '2')
    }
    $seen = @{}
    for ($i = 0; $i -lt 4; $i++) {
        $case = @($Value.cases)[$i]
        $casePath = "$Path.cases[$i]"
        Assert-Task43PRuntimeExistingCase $case $casePath
        $lane = [string]$case.lane
        if ($seen.ContainsKey($lane) -or $expected -cnotcontains $lane) {
            Invoke-Task43PRuntimeInfrastructureError 'Task43P runtime existing lane corpus was duplicated or unexpected.'
        }
        $seen[$lane] = $true
        if (-not $case.ownerRestored) {
            Invoke-Task43PRuntimeInfrastructureError 'Task43P runtime existing owner was not restored.'
        }
        $expectedFamily = $expectedFamilies[$lane]
        if ($null -eq $expectedFamily -or [string]$case.family -ne $expectedFamily[0] -or
                [string]$case.seed -ne $expectedFamily[1]) {
            Invoke-Task43PRuntimeInfrastructureError 'Task43P runtime existing family/seed corpus mismatch.'
        }
    }
    foreach ($lane in $expected) {
        if (-not $seen.ContainsKey($lane)) {
            Invoke-Task43PRuntimeInfrastructureError "Task43P runtime existing lane '$lane' was omitted."
        }
    }
}

function Assert-Task43PRuntimeDCase($Value,[string]$Path) {
    $properties = @(
        'stage', 'queuedPower', 'probeComponent', 'baselineResistance',
        'caughtSentinel', 'caughtType', 'caughtStage',
        'lastActiveMeasurementStimulusPresent', 'temporaryElementCount',
        'injectionRecorded', 'injectionCount', 'armedStageAfterCatch',
        'candidateOwnerCurrent', 'candidateChallengeCurrent',
        'temporaryElementsAbsentFromElmList',
        'temporaryElementsAbsentFromVoltageSources',
        'temporaryElementsAbsentFromNodeLinks', 'circuitMatrixPresent',
        'activeMeasurementOverlay', 'activeMeasurementSolverRestored',
        'solverRestoredWrapper', 'pendingBoardPowerState', 'boardPowerState',
        'powerRestored', 'resistanceRefreshPending', 'cleanup', 'disposition')
    Assert-Task43PRuntimeObject $Value $Path $properties $properties
    Assert-Task43PRuntimeEnum $Value.stage "$Path.stage" @(
        'READER', 'AFTER_STIMULUS_REMOVE')
    Assert-Task43PRuntimeBoolean $Value.queuedPower "$Path.queuedPower"
    Assert-Task43PRuntimeString $Value.probeComponent "$Path.probeComponent"
    if ($Value.probeComponent -cne 'R2') {
        Invoke-Task43PRuntimeInfrastructureError 'Task43P D probe component mismatch.'
    }
    Assert-Task43PRuntimeNumber $Value.baselineResistance "$Path.baselineResistance"
    if ($Value.baselineResistance -le 0) {
        Invoke-Task43PRuntimeInfrastructureError "Task43P D baseline resistance was not positive at $Path."
    }
    Assert-Task43PRuntimeBoolean $Value.caughtSentinel "$Path.caughtSentinel"
    Assert-Task43PRuntimeString $Value.caughtType "$Path.caughtType"
    if ($Value.caughtType -cne 'com.lushprojects.circuitjs1.client.CirSim$Task43PInjectedMeasurementFailure') {
        Invoke-Task43PRuntimeInfrastructureError `
            "Task43P D caught type was not the injected measurement failure at $Path."
    }
    Assert-Task43PRuntimeEnum $Value.caughtStage "$Path.caughtStage" @(
        'READER', 'AFTER_STIMULUS_REMOVE')
    Assert-Task43PRuntimeEnum $Value.armedStageAfterCatch "$Path.armedStageAfterCatch" @('NONE')
    Assert-Task43PRuntimeInteger $Value.temporaryElementCount "$Path.temporaryElementCount"
    Assert-Task43PRuntimeInteger $Value.injectionCount "$Path.injectionCount"
    foreach ($name in @(
            'lastActiveMeasurementStimulusPresent', 'injectionRecorded',
            'candidateOwnerCurrent', 'candidateChallengeCurrent',
            'temporaryElementsAbsentFromElmList',
            'temporaryElementsAbsentFromVoltageSources',
            'temporaryElementsAbsentFromNodeLinks', 'circuitMatrixPresent',
            'activeMeasurementOverlay', 'activeMeasurementSolverRestored',
            'solverRestoredWrapper', 'powerRestored',
            'resistanceRefreshPending')) {
        Assert-Task43PRuntimeBoolean $Value.$name "$Path.$name"
    }
    Assert-Task43PRuntimeString $Value.pendingBoardPowerState `
        "$Path.pendingBoardPowerState" -AllowNull
    if ($null -ne $Value.pendingBoardPowerState) {
        Assert-Task43PRuntimeEnum $Value.pendingBoardPowerState `
            "$Path.pendingBoardPowerState" @('POWERED', 'UNPOWERED')
    }
    Assert-Task43PRuntimeEnum $Value.boardPowerState "$Path.boardPowerState" @('POWERED', 'UNPOWERED')
    Assert-Task43PRuntimeEnum $Value.cleanup "$Path.cleanup" @('CLOSED', 'OPEN_BLOCKER')
    Assert-Task43PRuntimeEnum $Value.disposition "$Path.disposition" @('CLOSED', 'OPEN_BLOCKER')

    if (-not $Value.lastActiveMeasurementStimulusPresent -or
            [int]$Value.temporaryElementCount -le 0 -or
            $Value.stage -ne $Value.caughtStage -or
            -not $Value.caughtSentinel -or
            -not $Value.injectionRecorded -or
            [int]$Value.injectionCount -ne 1 -or
            $Value.armedStageAfterCatch -ne 'NONE') {
        Invoke-Task43PRuntimeInfrastructureError `
            "Task43P D injection provenance contradicted at $Path."
    }
    $expectedPower = if ($Value.queuedPower) { 'POWERED' } else { 'UNPOWERED' }
    $closed = $Value.caughtStage -eq $Value.stage -and
        $Value.injectionRecorded -and [int]$Value.injectionCount -eq 1 -and
        $Value.candidateOwnerCurrent -and $Value.candidateChallengeCurrent -and
        $Value.temporaryElementsAbsentFromElmList -and
        $Value.temporaryElementsAbsentFromVoltageSources -and
        $Value.temporaryElementsAbsentFromNodeLinks -and
        $Value.circuitMatrixPresent -and -not $Value.activeMeasurementOverlay -and
        $Value.activeMeasurementSolverRestored -and $Value.solverRestoredWrapper -and
        $null -eq $Value.pendingBoardPowerState -and $Value.powerRestored -and
        $Value.boardPowerState -eq $expectedPower
    # Applying queued power invalidates the resistance reading and requests a
    # later refresh. That cache flag is recorded, but is not temporary graph,
    # solver, overlay, or pending-power residue (the Java predicate agrees).
    if (($closed -and $Value.cleanup -ne 'CLOSED') -or
            (-not $closed -and $Value.cleanup -ne 'OPEN_BLOCKER') -or
            $Value.cleanup -ne $Value.disposition) {
        Invoke-Task43PRuntimeInfrastructureError `
            "Task43P D cleanup/disposition contradicted at $Path."
    }
}

function Assert-Task43PRuntimeMeasurement($Value, [string]$Path) {
    $properties = @('protocol', 'status', 'originalOwnerRestored',
        'caseCount', 'cleanupSafetyCaseCount', 'cases')
    Assert-Task43PRuntimeObject $Value $Path $properties $properties
    if ($Value.protocol -cne 'TSJ-TASK43P-D-1') {
        Invoke-Task43PRuntimeInfrastructureError 'Task43P D protocol mismatch.'
    }
    Assert-Task43PRuntimeEnum $Value.status "$Path.status" @('OBSERVED')
    Assert-Task43PRuntimeBoolean $Value.originalOwnerRestored "$Path.originalOwnerRestored"
    if (-not $Value.originalOwnerRestored) {
        Invoke-Task43PRuntimeInfrastructureError 'Task43P D owner restoration was false.'
    }
    Assert-Task43PRuntimeInteger $Value.caseCount "$Path.caseCount"
    Assert-Task43PRuntimeInteger $Value.cleanupSafetyCaseCount "$Path.cleanupSafetyCaseCount"
    if ($Value.cleanupSafetyCaseCount -ne 7) {
        Invoke-Task43PRuntimeInfrastructureError 'Task43P D cleanup safety corpus did not complete.'
    }
    Assert-Task43PRuntimeArray $Value.cases "$Path.cases"
    if (@($Value.cases).Count -ne 4 -or [int]$Value.caseCount -ne 4) {
        Invoke-Task43PRuntimeInfrastructureError 'Task43P D corpus count mismatch.'
    }

    $seen = @{}
    foreach ($case in @($Value.cases)) {
        Assert-Task43PRuntimeDCase $case "$Path.cases"
        $key = ([string]$case.stage) + '|' + ([string]$case.queuedPower)
        if ($seen.ContainsKey($key)) {
            Invoke-Task43PRuntimeInfrastructureError 'Task43P D case duplicated.'
        }
        $seen[$key] = $true
    }
    foreach ($stage in @('READER', 'AFTER_STIMULUS_REMOVE')) {
        foreach ($queued in @('False', 'True')) {
            if (-not $seen.ContainsKey($stage + '|' + $queued)) {
                Invoke-Task43PRuntimeInfrastructureError 'Task43P D case omitted.'
            }
        }
    }
}

function Assert-Task43PRuntimeGPoint($Value, [string]$Path) {
    $properties = @('label', 'ready', 'state', 'completed',
        'preGeneratedVerificationPending', 'preGeneratedVerificationAnalyzed',
        'analyzeFlag', 'generatedVerificationPending',
        'generatedVerificationAnalyzed', 't', 'power', 'retestPassed', 'finish')
    Assert-Task43PRuntimeObject $Value $Path $properties $properties
    Assert-Task43PRuntimeString $Value.label "$Path.label"
    foreach ($name in @('ready', 'completed', 'analyzeFlag',
            'generatedVerificationPending', 'generatedVerificationAnalyzed')) {
        Assert-Task43PRuntimeBoolean $Value.$name "$Path.$name"
    }
    Assert-Task43PRuntimeNullableBoolean $Value.preGeneratedVerificationPending `
        "$Path.preGeneratedVerificationPending"
    Assert-Task43PRuntimeNullableBoolean $Value.preGeneratedVerificationAnalyzed `
        "$Path.preGeneratedVerificationAnalyzed"
    Assert-Task43PRuntimeNullableBoolean $Value.retestPassed "$Path.retestPassed"
    Assert-Task43PRuntimeNullableBoolean $Value.finish "$Path.finish"
    Assert-Task43PRuntimeString $Value.state "$Path.state"
    Assert-Task43PRuntimeString $Value.power "$Path.power"
    Assert-Task43PRuntimeNumber $Value.t "$Path.t"
}

function Assert-Task43PRuntimeSettlement($Value, [string]$Path) {
    $properties = @('protocol', 'status', 'originalOwnerRestored', 'G', 'H',
        'snapshotCalibration')
    Assert-Task43PRuntimeObject $Value $Path $properties $properties
    if ($Value.protocol -cne 'TSJ-TASK43P-GH-1') {
        Invoke-Task43PRuntimeInfrastructureError 'Task43P GH protocol mismatch.'
    }
    Assert-Task43PRuntimeEnum $Value.status "$Path.status" @('OBSERVED')
    Assert-Task43PRuntimeBoolean $Value.originalOwnerRestored `
        "$Path.originalOwnerRestored"
    if (-not $Value.originalOwnerRestored) {
        Invoke-Task43PRuntimeInfrastructureError 'Task43P GH owner restoration was false.'
    }

    $gProperties = @('family', 'seed', 'candidateOwnerCurrent',
        'healthyPreRetestPassed', 'healthyPreFinish', 'naturalRetestPassed',
        'naturalFinish', 'repeatedFinish', 'repeatedRetestPassed', 'states',
        'rapid', 'disposition')
    Assert-Task43PRuntimeObject $Value.G "$Path.G" $gProperties $gProperties
    Assert-Task43PRuntimeString $Value.G.family "$Path.G.family"
    Assert-Task43PRuntimeInteger $Value.G.seed "$Path.G.seed"
    if ($Value.G.family -cne 'LED_INDICATOR' -or [long]$Value.G.seed -ne 3) {
        Invoke-Task43PRuntimeInfrastructureError 'Task43P G candidate family/seed mismatch.'
    }
    foreach ($name in @('candidateOwnerCurrent', 'healthyPreRetestPassed',
            'healthyPreFinish', 'naturalRetestPassed', 'naturalFinish',
            'repeatedFinish', 'repeatedRetestPassed')) {
        Assert-Task43PRuntimeBoolean $Value.G.$name "$Path.G.$name"
    }
    Assert-Task43PRuntimeArray $Value.G.states "$Path.G.states"
    if (@($Value.G.states).Count -ne 5) {
        Invoke-Task43PRuntimeInfrastructureError 'Task43P G state corpus count mismatch.'
    }
    $labels = @('readyOriginal', 'removedPausedPowered',
        'wrong2200PausedPowered', 'healthy1000BeforeNaturalUpdate',
        'healthy1000AfterNaturalUpdate')
    for ($i = 0; $i -lt 5; $i++) {
        $state = @($Value.G.states)[$i]
        Assert-Task43PRuntimeGPoint $state "$Path.G.states[$i]"
        if ($state.label -cne $labels[$i]) {
            Invoke-Task43PRuntimeInfrastructureError 'Task43P G state labels were reordered or omitted.'
        }
    }

    $rapidProperties = @('family', 'seed', 'meterResistance', 'modeBeforeExit',
        'retestPassed', 'finish', 'pendingPower', 'activeMeasurementOverlay',
        'targetsEmpty', 'disposition')
    Assert-Task43PRuntimeObject $Value.G.rapid "$Path.G.rapid" `
        $rapidProperties $rapidProperties
    Assert-Task43PRuntimeString $Value.G.rapid.family "$Path.G.rapid.family"
    Assert-Task43PRuntimeInteger $Value.G.rapid.seed "$Path.G.rapid.seed"
    if ($Value.G.rapid.family -cne 'LED_INDICATOR' -or
            [long]$Value.G.rapid.seed -ne 3) {
        Invoke-Task43PRuntimeInfrastructureError 'Task43P G rapid family/seed mismatch.'
    }
    Assert-Task43PRuntimeNumber $Value.G.rapid.meterResistance `
        "$Path.G.rapid.meterResistance"
    Assert-Task43PRuntimeInteger $Value.G.rapid.modeBeforeExit `
        "$Path.G.rapid.modeBeforeExit"
    foreach ($name in @('retestPassed', 'finish', 'activeMeasurementOverlay',
            'targetsEmpty')) {
        Assert-Task43PRuntimeBoolean $Value.G.rapid.$name "$Path.G.rapid.$name"
    }
    Assert-Task43PRuntimeString $Value.G.rapid.pendingPower `
        "$Path.G.rapid.pendingPower" -AllowNull
    Assert-Task43PRuntimeEnum $Value.G.disposition "$Path.G.disposition" `
        @('CLOSED', 'OPEN_BLOCKER')
    Assert-Task43PRuntimeEnum $Value.G.rapid.disposition `
        "$Path.G.rapid.disposition" @('CLOSED', 'OPEN_BLOCKER')

    $calProperties = @('freshDetachedCandidate', 'field', 'exactRoundTrip',
        'task43pDetectorFieldComparison', 'runtimeSimulatedOmissionRejected',
        'task41AssertRestoredRejectedPostRestoreSentinel',
        'task41AssertRestoredAcceptedPostRestoreSentinel', 'sourceMutation',
        'rejectionReason', 'supportedInventoryCases', 'disposition')
    Assert-Task43PRuntimeObject $Value.snapshotCalibration `
        "$Path.snapshotCalibration" $calProperties $calProperties
    foreach ($name in @('freshDetachedCandidate', 'exactRoundTrip',
            'task43pDetectorFieldComparison',
            'runtimeSimulatedOmissionRejected',
            'task41AssertRestoredRejectedPostRestoreSentinel',
            'task41AssertRestoredAcceptedPostRestoreSentinel', 'sourceMutation')) {
        Assert-Task43PRuntimeBoolean $Value.snapshotCalibration.$name `
            "$Path.snapshotCalibration.$name"
    }
    Assert-Task43PRuntimeString $Value.snapshotCalibration.field `
        "$Path.snapshotCalibration.field"
    Assert-Task43PRuntimeEnum $Value.snapshotCalibration.disposition `
        "$Path.snapshotCalibration.disposition" @('CLOSED', 'OPEN_BLOCKER')
    $calibration = $Value.snapshotCalibration
    Assert-Task43PRuntimeInteger $calibration.supportedInventoryCases "$Path.snapshotCalibration.supportedInventoryCases"
    if ($calibration.supportedInventoryCases -ne 4 -or
            ($calibration.runtimeSimulatedOmissionRejected -and
                $calibration.rejectionReason -cne 'Task 41 restore changed lastResistanceTestCurrent') -or
            (-not $calibration.runtimeSimulatedOmissionRejected -and $null -ne $calibration.rejectionReason)) {
        Invoke-Task43PRuntimeInfrastructureError 'Task43P snapshot calibration did not prove the intended assertion mismatch.'
    }
    if (-not $calibration.freshDetachedCandidate -or
            $calibration.field -cne 'lastResistanceTestCurrent' -or
            -not $calibration.exactRoundTrip -or
            -not $calibration.task43pDetectorFieldComparison -or
            ($calibration.runtimeSimulatedOmissionRejected -ne
                $calibration.task41AssertRestoredRejectedPostRestoreSentinel) -or
            ($calibration.task41AssertRestoredAcceptedPostRestoreSentinel -eq
                $calibration.runtimeSimulatedOmissionRejected) -or
            $calibration.sourceMutation) {
        Invoke-Task43PRuntimeInfrastructureError `
            'Task43P snapshot calibration evidence was internally inconsistent.'
    }
    $calibrationClosed = $calibration.freshDetachedCandidate -and
        $calibration.field -eq 'lastResistanceTestCurrent' -and
        $calibration.exactRoundTrip -and
        $calibration.task43pDetectorFieldComparison -and
        $calibration.runtimeSimulatedOmissionRejected -and
        $calibration.task41AssertRestoredRejectedPostRestoreSentinel -and
        -not $calibration.task41AssertRestoredAcceptedPostRestoreSentinel -and
        -not $calibration.sourceMutation
    $calibrationExpected = if ($calibrationClosed) { 'CLOSED' } else { 'OPEN_BLOCKER' }
    if ($calibration.disposition -ne $calibrationExpected) {
        Invoke-Task43PRuntimeInfrastructureError `
            'Task43P snapshot calibration disposition contradicted its raw observations.'
    }

    $removed = @($Value.G.states)[1]
    $wrong = @($Value.G.states)[2]
    $healthy = @($Value.G.states)[4]
    $invalidStatesClosed = $removed.ready -and $removed.power -eq 'POWERED' -and
        -not $removed.completed -and $removed.retestPassed -eq $false -and
        $removed.finish -eq $false -and $wrong.ready -and
        $wrong.power -eq 'POWERED' -and -not $wrong.completed -and
        $wrong.retestPassed -eq $false -and $wrong.finish -eq $false
    $healthyClosed = $healthy.ready -and $healthy.completed -and
        $healthy.retestPassed -eq $true -and $healthy.finish -eq $true
    $completionPathClosed =
        ($Value.G.healthyPreRetestPassed -and $Value.G.healthyPreFinish -and
            @($Value.G.states)[3].completed) -or
        ($Value.G.naturalRetestPassed -and $Value.G.naturalFinish -and
            $healthy.completed)
    $terminalIdempotenceClosed = -not $Value.G.repeatedFinish -and
        -not $Value.G.repeatedRetestPassed
    $rapidClosed = -not $Value.G.rapid.retestPassed -and
        -not $Value.G.rapid.finish -and
        $null -eq $Value.G.rapid.pendingPower -and
        -not $Value.G.rapid.activeMeasurementOverlay -and
        $Value.G.rapid.targetsEmpty -and
        $Value.G.rapid.disposition -eq 'CLOSED'
    $gClosed = $Value.G.candidateOwnerCurrent -and $invalidStatesClosed -and
        $healthyClosed -and $completionPathClosed -and
        $terminalIdempotenceClosed -and $rapidClosed
    $gExpected = if ($gClosed) { 'CLOSED' } else { 'OPEN_BLOCKER' }
    if ($Value.G.disposition -ne $gExpected) {
        Invoke-Task43PRuntimeInfrastructureError `
            'Task43P G disposition contradicted owner, power, repair, or settlement observations.'
    }
}

function Assert-Task43PRuntimeH($Value, [string]$Path) {
    $properties = @('caseCount', 'cases', 'forwardReverseExact', 'disposition')
    Assert-Task43PRuntimeObject $Value $Path $properties $properties
    Assert-Task43PRuntimeInteger $Value.caseCount "$Path.caseCount"
    Assert-Task43PRuntimeBoolean $Value.forwardReverseExact `
        "$Path.forwardReverseExact"
    Assert-Task43PRuntimeEnum $Value.disposition "$Path.disposition" `
        @('CLOSED', 'OPEN_BLOCKER')
    Assert-Task43PRuntimeArray $Value.cases "$Path.cases"
    if (@($Value.cases).Count -ne 6 -or [int]$Value.caseCount -ne 6) {
        Invoke-Task43PRuntimeInfrastructureError 'Task43P H corpus count mismatch.'
    }
    $expected = @(
        @('forward', 'LED_INDICATOR', 3),
        @('forward', 'NPN_LOW_SIDE_SWITCH', 0),
        @('forward', 'RC_DELAY', 2),
        @('reverse', 'RC_DELAY', 2),
        @('reverse', 'NPN_LOW_SIDE_SWITCH', 0),
        @('reverse', 'LED_INDICATOR', 3))
    $byIdentity = @{}
    for ($i = 0; $i -lt 6; $i++) {
        $case = @($Value.cases)[$i]
        $casePath = "$Path.cases[$i]"
        $caseProperties = @('order', 'family', 'seed', 'resetOwnerPreserved',
            'freshRetestEmpty', 'freshCompletionEmpty',
            'freshInstrumentTargetsEmpty', 'resetHadRetest', 'resetHadTargets',
            'resetPreservedRetestReference', 'resetPreservedProbeReferences',
            'modeAtFreshReady', 'fingerprint')
        Assert-Task43PRuntimeObject $case $casePath $caseProperties $caseProperties
        Assert-Task43PRuntimeString $case.order "$casePath.order"
        Assert-Task43PRuntimeString $case.family "$casePath.family"
        Assert-Task43PRuntimeInteger $case.seed "$casePath.seed"
        foreach ($name in @('resetOwnerPreserved', 'freshRetestEmpty',
                'freshCompletionEmpty', 'freshInstrumentTargetsEmpty',
                'resetHadRetest', 'resetHadTargets',
                'resetPreservedRetestReference',
                'resetPreservedProbeReferences')) {
            Assert-Task43PRuntimeBoolean $case.$name "$casePath.$name"
        }
        Assert-Task43PRuntimeInteger $case.modeAtFreshReady `
            "$casePath.modeAtFreshReady"
        Assert-Task43PRuntimeString $case.fingerprint "$casePath.fingerprint"
        if ([string]$case.order -ne $expected[$i][0] -or
                [string]$case.family -ne $expected[$i][1] -or
                [long]$case.seed -ne [long]$expected[$i][2]) {
            Invoke-Task43PRuntimeInfrastructureError `
                'Task43P H expected family/order/seed corpus mismatch.'
        }
        if (-not $case.resetOwnerPreserved -or -not $case.freshRetestEmpty -or
                -not $case.freshCompletionEmpty -or
                -not $case.freshInstrumentTargetsEmpty -or
                -not $case.resetHadRetest -or -not $case.resetHadTargets) {
            Invoke-Task43PRuntimeInfrastructureError `
                'Task43P H fresh owner reset evidence was incomplete.'
        }
        $identity = [string]$case.family + '|' + [string]$case.seed
        if ($byIdentity.ContainsKey($identity)) {
            if ($byIdentity[$identity].fingerprint -ne $case.fingerprint) {
                Invoke-Task43PRuntimeInfrastructureError `
                    'Task43P H forward/reverse fingerprint mismatch.'
            }
        } else {
            $byIdentity[$identity] = $case
        }
    }
}

function Assert-Task43PRuntimeCallback($Value, [string]$Path) {
    $properties = @('protocol', 'status', 'actualScheduledCallback',
        'callbackSequence', 'scheduledFamily', 'scheduledSeed', 'currentFamily',
        'currentSeed', 'ownerAtEntryIsCurrent', 'ownerAtExitIsCurrent',
        'pendingAfterSwitch', 'analyzedAfterSwitch', 'pendingAfterCallback',
        'analyzedAfterCallback', 'oldTargetsInvalidAfterSwitch',
        'targetsClearedAfterSwitch', 'noOldTargetsAfterCallback',
        'oldOwnerUnchanged', 'oldOwnerBefore', 'oldOwnerAtEntry',
        'oldOwnerAfter', 'overlayAfterCallback', 'disposition',
        'originalOwnerRestored')
    Assert-Task43PRuntimeObject $Value $Path $properties $properties
    if ($Value.protocol -cne 'TSJ-TASK43P-I-1') {
        Invoke-Task43PRuntimeInfrastructureError 'Task43P I protocol mismatch.'
    }
    Assert-Task43PRuntimeEnum $Value.status "$Path.status" @('OBSERVED')
    foreach ($name in @('actualScheduledCallback', 'ownerAtEntryIsCurrent',
            'ownerAtExitIsCurrent', 'pendingAfterSwitch',
            'analyzedAfterSwitch', 'pendingAfterCallback',
            'analyzedAfterCallback', 'oldTargetsInvalidAfterSwitch',
            'targetsClearedAfterSwitch', 'noOldTargetsAfterCallback',
            'oldOwnerUnchanged', 'overlayAfterCallback',
            'originalOwnerRestored')) {
        Assert-Task43PRuntimeBoolean $Value.$name "$Path.$name"
    }
    foreach ($name in @('callbackSequence', 'scheduledSeed', 'currentSeed')) {
        Assert-Task43PRuntimeInteger $Value.$name "$Path.$name"
    }
    foreach ($name in @('scheduledFamily', 'currentFamily',
            'oldOwnerBefore', 'oldOwnerAtEntry', 'oldOwnerAfter')) {
        Assert-Task43PRuntimeString $Value.$name "$Path.$name"
    }
    Assert-Task43PRuntimeEnum $Value.disposition "$Path.disposition" `
        @('CLOSED', 'OPEN_BLOCKER')
    if ($Value.scheduledFamily -cne 'LED_INDICATOR' -or
            [long]$Value.scheduledSeed -ne 3 -or
            $Value.currentFamily -cne 'NPN_LOW_SIDE_SWITCH' -or
            [long]$Value.currentSeed -ne 0 -or
            -not $Value.originalOwnerRestored) {
        Invoke-Task43PRuntimeInfrastructureError `
            'Task43P callback identity or owner restoration was invalid.'
    }
    if (-not $Value.actualScheduledCallback -or
            [long]$Value.callbackSequence -le 0) {
        Invoke-Task43PRuntimeInfrastructureError `
            'Task43P callback did not prove a real positive scheduled event.'
    }
    $fingerprintEqual = $Value.oldOwnerBefore -eq $Value.oldOwnerAtEntry -and
        $Value.oldOwnerAtEntry -eq $Value.oldOwnerAfter
    $closed = $Value.ownerAtEntryIsCurrent -and
        $Value.ownerAtExitIsCurrent -and
        $Value.oldTargetsInvalidAfterSwitch -and
        $Value.targetsClearedAfterSwitch -and
        $Value.noOldTargetsAfterCallback -and
        $Value.oldOwnerUnchanged -and -not $Value.overlayAfterCallback -and
        $fingerprintEqual
    $expectedDisposition = if ($closed) { 'CLOSED' } else { 'OPEN_BLOCKER' }
    if ($Value.disposition -ne $expectedDisposition) {
        Invoke-Task43PRuntimeInfrastructureError `
            'Task43P callback disposition contradicted its raw owner/target/fingerprint observations.'
    }
}

function Assert-Task43PRuntimeEvidence($Value, [string]$RunId, [string]$RouteId) {
    $root = @(
        'protocol', 'status', 'developerOnly', 'runId', 'routeId',
        'sameOwnerAfterSynchronousCases', 'existing', 'measurement',
        'settlement', 'callback')
    Assert-Task43PRuntimeObject $Value 'runtime' $root $root
    if ($Value.protocol -cne 'TSJ-TASK43P-RUNTIME-1') {
        Invoke-Task43PRuntimeInfrastructureError 'Task43P runtime protocol mismatch.'
    }
    Assert-Task43PRuntimeEnum $Value.status 'runtime.status' @('OBSERVED')
    Assert-Task43PRuntimeBoolean $Value.developerOnly 'runtime.developerOnly'
    if (-not $Value.developerOnly) {
        Invoke-Task43PRuntimeInfrastructureError `
            'Task43P runtime evidence was not developer-only.'
    }
    Assert-Task43PRuntimeString $Value.runId 'runtime.runId'
    Assert-Task43PRuntimeString $Value.routeId 'runtime.routeId'
    if ($Value.runId -cne $RunId -or $Value.routeId -cne $RouteId) {
        Invoke-Task43PRuntimeInfrastructureError `
            'Task43P runtime evidence run/route provenance mismatch.'
    }
    Assert-Task43PRuntimeBoolean $Value.sameOwnerAfterSynchronousCases `
        'runtime.sameOwnerAfterSynchronousCases'
    if (-not $Value.sameOwnerAfterSynchronousCases) {
        Invoke-Task43PRuntimeInfrastructureError `
            'Task43P synchronous owner restoration was not proven.'
    }

    $open = 0
    Assert-Task43PRuntimeExisting $Value.existing 'runtime.existing'
    Assert-Task43PRuntimeMeasurement $Value.measurement 'runtime.measurement'
    Assert-Task43PRuntimeSettlement $Value.settlement 'runtime.settlement'
    Assert-Task43PRuntimeH $Value.settlement.H 'runtime.settlement.H'
    Assert-Task43PRuntimeCallback $Value.callback 'runtime.callback'

    foreach ($case in @($Value.measurement.cases)) {
        if ($case.disposition -eq 'OPEN_BLOCKER') { $open++ }
    }
    if ($Value.settlement.G.disposition -eq 'OPEN_BLOCKER') { $open++ }
    if ($Value.settlement.G.rapid.disposition -eq 'OPEN_BLOCKER') { $open++ }
    if ($Value.settlement.snapshotCalibration.disposition -eq 'OPEN_BLOCKER') {
        $open++
    }
    if ($Value.settlement.H.disposition -eq 'OPEN_BLOCKER' -or
            -not $Value.settlement.H.forwardReverseExact) {
        $open++
    }
    if ($Value.callback.disposition -eq 'OPEN_BLOCKER') { $open++ }

    return [pscustomobject]@{
        ExitCode = $(if ($open -gt 0) { 1 } else { 0 })
        OpenBlockerCount = $open
        VerifiedLanes = @('A', 'B/F', 'C', 'E', 'D', 'G', 'H', 'I')
    }
}

function New-Task43PRuntimeContractProbeValue([string]$RunId,[string]$RouteId) {
    # Keep this packet independent of browser or Java execution.  It is a
    # complete positive corpus, so every negative below exercises the same
    # production validator rather than a probe-only shortcut.
    function New-ProbePart($id) { [pscustomobject]@{id=$id;installed=$true;faulted=$true;original=$true} }
    function New-ProbeExistingCase($lane,$family,$topology,$seed) { [pscustomobject]@{lane=$lane;family=$family;topology=$topology;seed=$seed;helperAssertionsCompleted=$true;faultBefore=[pscustomobject]@{id='F';type='OPEN';bindingIdentity=$true;applied=$true};faultAfter=[pscustomobject]@{id='F';type='OPEN';target='R1';bindingIdentityPreserved=$true;applied=$true};originalPartBefore=(New-ProbePart 'R1');installedTargetAfter=(New-ProbePart 'R1');challengeStateAfter='READY';repairStatusAfter='UNREPAIRED';completedAfter=$false;retestPresentAfter=$false;boardPowerAfter='POWERED';verificationPendingAfter=$false;verificationAnalyzedAfter=$true;modificationsFullyRestoredAfter=$true;candidateOwnerCurrent=$true;candidateWasIsolated=$true;ownerRestored=$true} }
    $existingCases=@((New-ProbeExistingCase 'A' 'LED_INDICATOR' 'single' 3),(New-ProbeExistingCase 'B/F' 'LED_INDICATOR' 'single' 0),(New-ProbeExistingCase 'C' 'LED_INDICATOR' 'single' 3),(New-ProbeExistingCase 'E' 'RC_DELAY' 'delay' 2))
    function New-ProbeDCase($stage,$queued) { $power=if($queued){'POWERED'}else{'UNPOWERED'}; [pscustomobject]@{stage=$stage;queuedPower=$queued;probeComponent='R2';baselineResistance=1000.0;caughtSentinel=$true;caughtType='com.lushprojects.circuitjs1.client.CirSim$Task43PInjectedMeasurementFailure';caughtStage=$stage;lastActiveMeasurementStimulusPresent=$true;temporaryElementCount=2;injectionRecorded=$true;injectionCount=1;armedStageAfterCatch='NONE';candidateOwnerCurrent=$true;candidateChallengeCurrent=$true;temporaryElementsAbsentFromElmList=$true;temporaryElementsAbsentFromVoltageSources=$true;temporaryElementsAbsentFromNodeLinks=$true;circuitMatrixPresent=$true;activeMeasurementOverlay=$false;activeMeasurementSolverRestored=$true;solverRestoredWrapper=$true;pendingBoardPowerState=$null;boardPowerState=$power;powerRestored=$true;resistanceRefreshPending=$false;cleanup='CLOSED';disposition='CLOSED'} }
    $dcases=@((New-ProbeDCase 'READER' $false),(New-ProbeDCase 'READER' $true),(New-ProbeDCase 'AFTER_STIMULUS_REMOVE' $false),(New-ProbeDCase 'AFTER_STIMULUS_REMOVE' $true))
    function New-ProbeGPoint($label,$completed,$retest,$finish) { [pscustomobject]@{label=$label;ready=$true;state='READY';completed=$completed;preGeneratedVerificationPending=$null;preGeneratedVerificationAnalyzed=$null;analyzeFlag=$true;generatedVerificationPending=$false;generatedVerificationAnalyzed=$true;t=1.0;power='POWERED';retestPassed=$retest;finish=$finish} }
    $states=@((New-ProbeGPoint 'readyOriginal' $false $null $null),(New-ProbeGPoint 'removedPausedPowered' $false $false $false),(New-ProbeGPoint 'wrong2200PausedPowered' $false $false $false),(New-ProbeGPoint 'healthy1000BeforeNaturalUpdate' $true $true $true),(New-ProbeGPoint 'healthy1000AfterNaturalUpdate' $true $true $true))
    $rapid=[pscustomobject]@{family='LED_INDICATOR';seed=3;meterResistance=1000.0;modeBeforeExit=2;retestPassed=$false;finish=$false;pendingPower=$null;activeMeasurementOverlay=$false;targetsEmpty=$true;disposition='CLOSED'}
    function New-ProbeHCase($order,$family,$seed,$fingerprint) { [pscustomobject]@{order=$order;family=$family;seed=$seed;resetOwnerPreserved=$true;freshRetestEmpty=$true;freshCompletionEmpty=$true;freshInstrumentTargetsEmpty=$true;resetHadRetest=$true;resetHadTargets=$true;resetPreservedRetestReference=$true;resetPreservedProbeReferences=$true;modeAtFreshReady=0;fingerprint=$fingerprint} }
    $hcases=@((New-ProbeHCase 'forward' 'LED_INDICATOR' 3 'led'),(New-ProbeHCase 'forward' 'NPN_LOW_SIDE_SWITCH' 0 'npn'),(New-ProbeHCase 'forward' 'RC_DELAY' 2 'rc'),(New-ProbeHCase 'reverse' 'RC_DELAY' 2 'rc'),(New-ProbeHCase 'reverse' 'NPN_LOW_SIDE_SWITCH' 0 'npn'),(New-ProbeHCase 'reverse' 'LED_INDICATOR' 3 'led'))
    $snapshot=[pscustomobject]@{freshDetachedCandidate=$true;field='lastResistanceTestCurrent';rejectionReason='Task 41 restore changed lastResistanceTestCurrent';supportedInventoryCases=4;exactRoundTrip=$true;task43pDetectorFieldComparison=$true;runtimeSimulatedOmissionRejected=$true;task41AssertRestoredRejectedPostRestoreSentinel=$true;task41AssertRestoredAcceptedPostRestoreSentinel=$false;sourceMutation=$false;disposition='CLOSED'}
    $callback=[pscustomobject]@{protocol='TSJ-TASK43P-I-1';status='OBSERVED';actualScheduledCallback=$true;callbackSequence=1;scheduledFamily='LED_INDICATOR';scheduledSeed=3;currentFamily='NPN_LOW_SIDE_SWITCH';currentSeed=0;ownerAtEntryIsCurrent=$true;ownerAtExitIsCurrent=$true;pendingAfterSwitch=$true;analyzedAfterSwitch=$false;pendingAfterCallback=$true;analyzedAfterCallback=$false;oldTargetsInvalidAfterSwitch=$true;targetsClearedAfterSwitch=$true;noOldTargetsAfterCallback=$true;oldOwnerUnchanged=$true;oldOwnerBefore='fp';oldOwnerAtEntry='fp';oldOwnerAfter='fp';overlayAfterCallback=$false;disposition='CLOSED';originalOwnerRestored=$true}
    return [pscustomobject]@{protocol='TSJ-TASK43P-RUNTIME-1';status='OBSERVED';developerOnly=$true;runId=$RunId;routeId=$RouteId;sameOwnerAfterSynchronousCases=$true;existing=[pscustomobject]@{protocol='TSJ-TASK43P-ABCEF-1';status='OBSERVED';caseCount=4;cases=$existingCases;originalOwnerRestored=$true};measurement=[pscustomobject]@{protocol='TSJ-TASK43P-D-1';status='OBSERVED';originalOwnerRestored=$true;caseCount=4;cleanupSafetyCaseCount=7;cases=$dcases};settlement=[pscustomobject]@{protocol='TSJ-TASK43P-GH-1';status='OBSERVED';originalOwnerRestored=$true;G=[pscustomobject]@{family='LED_INDICATOR';seed=3;candidateOwnerCurrent=$true;healthyPreRetestPassed=$true;healthyPreFinish=$true;naturalRetestPassed=$false;naturalFinish=$false;repeatedFinish=$false;repeatedRetestPassed=$false;states=$states;rapid=$rapid;disposition='CLOSED'};H=[pscustomobject]@{caseCount=6;cases=$hcases;forwardReverseExact=$true;disposition='CLOSED'};snapshotCalibration=$snapshot};callback=$callback}
}

if ($Task43PRuntimeEvidenceContractProbe) {
    $module = Join-Path $PSScriptRoot 'VerifierIsolation.psm1'
    Import-Module $module -Force -ErrorAction Stop
    $run = 'probe-run-00000001'; $route = 'probe-route-00000001'
    $valid = New-Task43PRuntimeContractProbeValue $run $route
    try { $result=Assert-Task43PRuntimeEvidence $valid $run $route; if($result.ExitCode -ne 0){throw 'positive contract packet was not PASS'} } catch { Write-Error ('contract probe positive packet failed: ' + $_.Exception.Message); exit 1 }
    $queuedRefresh = ($valid | ConvertTo-Json -Depth 100 | ConvertFrom-Json)
    $queuedRefresh.measurement.cases[1].resistanceRefreshPending = $true
    try {
        $result = Assert-Task43PRuntimeEvidence $queuedRefresh $run $route
        if ($result.ExitCode -ne 0) { throw 'queued power cache refresh was classified as graph residue' }
        $queuedRefresh.measurement.cases[3].activeMeasurementOverlay = $true
        $queuedRefresh.measurement.cases[3].activeMeasurementSolverRestored = $false
        $queuedRefresh.measurement.cases[3].solverRestoredWrapper = $false
        $queuedRefresh.measurement.cases[3].pendingBoardPowerState = 'POWERED'
        $queuedRefresh.measurement.cases[3].cleanup = 'OPEN_BLOCKER'
        $queuedRefresh.measurement.cases[3].disposition = 'OPEN_BLOCKER'
        $result = Assert-Task43PRuntimeEvidence $queuedRefresh $run $route
        if ($result.ExitCode -ne 1 -or $result.OpenBlockerCount -ne 1) {
            throw 'temporary measurement residue did not retain its blocker outcome'
        }
        $queuedRefresh.measurement.cases[3].cleanup = 'CLOSED'
        $queuedRefresh.measurement.cases[3].disposition = 'CLOSED'
        $rejected = $false
        try { [void](Assert-Task43PRuntimeEvidence $queuedRefresh $run $route) } catch {
            $rejected = Test-VerifierInfrastructureError $_
        }
        if (-not $rejected) { throw 'temporary measurement residue accepted a false CLOSED claim' }
    } catch { Write-Error ('queued refresh/residue contract failed: ' + $_.Exception.Message); exit 1 }
    $tests = @(
        { param($v) $v.PSObject.Properties.Remove('callback') },
        { param($v) Add-Member -InputObject $v.measurement -NotePropertyName unknown -NotePropertyValue $true },
        { param($v) $v.developerOnly = 'true' },
        { param($v) $v.settlement.G.seed = [double]::NaN },
        { param($v) $v.existing.caseCount = 4.1 },
        { param($v) $v.existing.cases[0].seed = 3.1 },
        { param($v) $v.existing.cases[0].lane = 'B/F' },
        { param($v) $v.measurement.cases = @($v.measurement.cases | Select-Object -First 3) },
        { param($v) $v.measurement.cleanupSafetyCaseCount = 5 },
        { param($v) $v.measurement.PSObject.Properties.Remove('cleanupSafetyCaseCount') },
        { param($v) $v.settlement.snapshotCalibration.rejectionReason = 'unrelated failure' },
        { param($v) $v.settlement.snapshotCalibration.supportedInventoryCases = 3 },
        { param($v) $v.runId = 'foreign-run' },
        { param($v) $v.measurement.cases[0].injectionCount = 2 },
        { param($v) $v.measurement.cases[0].injectionCount = 1.1 },
        { param($v) $v.measurement.cases[0].baselineResistance = 0 },
        { param($v) $v.measurement.cases[0].baselineResistance = -1 },
        { param($v) $v.measurement.cases[0].boardPowerState = 'INVALID'; $v.measurement.cases[0].cleanup = 'OPEN_BLOCKER'; $v.measurement.cases[0].disposition = 'OPEN_BLOCKER' },
        { param($v) $v.measurement.cases[0].pendingBoardPowerState = 'INVALID'; $v.measurement.cases[0].cleanup = 'OPEN_BLOCKER'; $v.measurement.cases[0].disposition = 'OPEN_BLOCKER' },
        { param($v) $v.settlement.H.cases[3].fingerprint = 'altered' },
        { param($v) $v.callback.callbackSequence = 1.1 },
        { param($v) $v.callback.oldOwnerAfter = 'altered' },
        { param($v) $v.existing.originalOwnerRestored = $false },
        { param($v) $v.existing.cases[0].helperAssertionsCompleted = $false },
        { param($v) $v.existing.cases[0].faultBefore.bindingIdentity = $false },
        { param($v) $v.existing.cases[0].faultAfter.bindingIdentityPreserved = $false },
        { param($v) $v.existing.cases[0].candidateWasIsolated = $false },
        { param($v) $v.existing.cases[0].originalPartBefore.faulted = $false },
        { param($v) $v.measurement.cases[0].caughtType = 'RuntimeException' },
        { param($v) $v.settlement.G.rapid.activeMeasurementOverlay = $true }
    )
    $index=0
    foreach($test in $tests){$copy=($valid | ConvertTo-Json -Depth 100 | ConvertFrom-Json);& $test $copy;try{[void](Assert-Task43PRuntimeEvidence $copy $run $route);Write-Error "contract probe accepted negative $index";exit 1}catch{if(-not (Test-VerifierInfrastructureError $_)){Write-Error "negative $index was not a typed infrastructure rejection";exit 1}};$index++}
    $open = ($valid | ConvertTo-Json -Depth 100 | ConvertFrom-Json)
    $open.settlement.snapshotCalibration.runtimeSimulatedOmissionRejected = $false
    $open.settlement.snapshotCalibration.rejectionReason = $null
    $open.settlement.snapshotCalibration.task41AssertRestoredRejectedPostRestoreSentinel = $false
    $open.settlement.snapshotCalibration.task41AssertRestoredAcceptedPostRestoreSentinel = $true
    $open.settlement.snapshotCalibration.disposition = 'OPEN_BLOCKER'
    try { $openResult=Assert-Task43PRuntimeEvidence $open $run $route; if($openResult.ExitCode -ne 1 -or $openResult.OpenBlockerCount -lt 1){Write-Error 'contract probe did not preserve a truthful OPEN_BLOCKER as exit 1';exit 1} } catch { Write-Error ('contract probe truthful OPEN_BLOCKER failed: ' + $_.Exception.Message); exit 1 }
    $falsePass = ($valid | ConvertTo-Json -Depth 100 | ConvertFrom-Json)
    $falsePass.settlement.snapshotCalibration.runtimeSimulatedOmissionRejected = $false
    $falsePass.settlement.snapshotCalibration.rejectionReason = $null
    $falsePass.settlement.snapshotCalibration.task41AssertRestoredRejectedPostRestoreSentinel = $false
    $falsePass.settlement.snapshotCalibration.task41AssertRestoredAcceptedPostRestoreSentinel = $true
    $falsePass.settlement.snapshotCalibration.disposition = 'CLOSED'
    try { [void](Assert-Task43PRuntimeEvidence $falsePass $run $route); Write-Error 'contract probe accepted false-pass calibration'; exit 1 } catch { if(-not (Test-VerifierInfrastructureError $_)){Write-Error 'false-pass calibration did not produce typed infrastructure rejection';exit 1} }
    Write-Output 'PASS:Task43PRuntimeEvidenceContractProbe'; exit 0
}
