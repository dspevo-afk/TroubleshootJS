[CmdletBinding()]
param(
    [string]$JavaHome = $env:JAVA_HOME,
    [string]$PythonExe = 'python',
    [string]$ReceiptOutputPath = '',
    [string[]]$Suite = @(),
    [long[]]$Q30ServiceSeeds = @(7,13,4,14,43,3,10,64),
    [long[]]$Q30SensitivitySeeds = @(7,13,4,14,43,3,10,64),
    [ValidateSet('DREV_OPEN','REN_OPEN','SENSOR_A_OPEN','DRIVE_A_OPEN','RELAY_A_COIL_OPEN','RELAY_B_COIL_OPEN')]
    [string[]]$Q30SensitivityFaults = @(),
    [long[]]$Q30CorpusSeeds = @(0,1,15,14,44,8,10,12,48,35,6,18,43,93,20,64,
        56,13,4,11,2,3,19,9,7,42,24,21,75,22,105,27,53,50,16,25,60,100,59,84,
        70,41,45,23,5,38,40,26,[long]::MinValue,[long]::MaxValue,9007199254740993L),
    [long[]]$QuickPlayGateSeeds = @(),
    [long[]]$Q30PlanSeeds = @()
)

# Maintained current seed, identity, geometry, recipe and construction contracts.
# Compiles the real client source once; only the Q30/full-suite JVM path uses an
# exact scratch replacement for CirSim's JSNI console logger. The actual GWT
# solver/player gates remain separate.
Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

function Write-Q30CasePlanMetadataFailure {
    param(
        [Parameter(Mandatory = $true)][string]$Seed,
        [Parameter(Mandatory = $true)][string]$Violation,
        [string[]]$Lines = @()
    )
    $safeViolation = [regex]::Replace($Violation,
        '(?i)(?:[A-Z]:\\|\\\\)[^\r\n,;)]*', '<path>')
    Write-Host ('Q30_CASE_PLAN_PREFLIGHT_FAILURE seed=' + $Seed +
        ' violation=' + $safeViolation)
    foreach ($line in @($Lines)) {
        if ($line -cmatch '^Q30_CASE_PLAN(?:\s|$)') {
            Write-Host $line
        }
    }
}
function Get-Q30CasePlanMetadata {
    param(
        [Parameter(Mandatory = $true)][string]$JavaExe,
        [Parameter(Mandatory = $true)][string]$ClassPath,
        [Parameter(Mandatory = $true)][string]$Seed
    )
    $arguments = @('-ea', '-cp', $ClassPath,
        'com.lushprojects.circuitjs1.client.Q30ServiceFlowContractTest',
        '--describe-plan', $Seed)
    $lines = @()
    try {
    $described = Invoke-VerifierBoundedProcess $JavaExe $arguments 60000
    $lines = @($described.Stdout -split '\r?\n' | Where-Object { $_.Length -gt 0 })
    if (-not $described.TerminationProven -or $described.ExitCode -ne 0 -or
            $lines.Count -ne 1 -or $lines[0] -cnotmatch '^Q30_CASE_PLAN \{.+\}$') {
        throw ('Q30 plan description failed exact seed ' + $Seed +
            ', exit=' + $described.ExitCode + ', termination=' +
            $described.TerminationProven)
    }
    $rawLine = $lines[0]
    try {
        $plan = $rawLine.Substring('Q30_CASE_PLAN '.Length) | ConvertFrom-Json -ErrorAction Stop
    } catch {
        throw ('Q30 plan description JSON is invalid for seed ' + $Seed + ': ' +
            $_.Exception.Message)
    }
    if ($plan.schema -ne 1 -or $plan.seed -isnot [string] -or
            $plan.seed -cne $Seed -or $plan.planEpoch -ne 4 -or
            $plan.canonical -isnot [string] -or
            $plan.canonical -cnotmatch ('^rb30-plan@4;seed=' +
                [regex]::Escape($Seed) + ';topology=[A-Z0-9_]+;support=')) {
        throw ('Q30 plan description has foreign schema, epoch, seed, or identity for ' + $Seed)
    }
    if ($plan.channelCount -notin @(1, 2)) {
        throw ('Q30 plan description has unsupported channel count for seed ' + $Seed)
    }
    [string[]]$expectedChannels = if ($plan.channelCount -eq 1) { @('A') } else { @('A', 'B') }
    $actualChannels = @($plan.activeChannels)
    if ($actualChannels.Count -ne $expectedChannels.Count) {
        throw ('Q30 active-channel count mismatch for seed ' + $Seed)
    }
    for ($index = 0; $index -lt $expectedChannels.Count; $index++) {
        if ($actualChannels[$index] -cne $expectedChannels[$index]) {
            throw ('Q30 active-channel order mismatch for seed ' + $Seed)
        }
    }
    if ($plan.support.C12 -isnot [bool] -or $plan.support.S5 -isnot [bool] -or
            $plan.support.S12 -isnot [bool] -or $plan.support.bleeder5 -isnot [bool] -or
            $plan.support.filters -notin @(0, 1, 2, 3) -or
            $plan.support.outputIndicators -notin @(0, 1, 2, 3)) {
        throw ('Q30 support identity is malformed for seed ' + $Seed)
    }
    $supportIdentity = 'C12:' + $plan.support.C12.ToString().ToLowerInvariant() +
        ',S5:' + $plan.support.S5.ToString().ToLowerInvariant() +
        ',S12:' + $plan.support.S12.ToString().ToLowerInvariant() +
        ',filters:' + $plan.support.filters +
        ',outputIndicators:' + $plan.support.outputIndicators +
        ',bleeder5:' + $plan.support.bleeder5.ToString().ToLowerInvariant()
    if ($plan.canonical -cnotmatch (';support=' + [regex]::Escape($supportIdentity) + ';')) {
        throw ('Q30 plan support JSON disagrees with canonical identity for seed ' + $Seed)
    }
    $axisMatch = [regex]::Match($plan.canonical,
        ';topology=(RB30_CH[12]_(SEPARATE_DIRECT|SHARED_DIRECT|SHARED_HYSTERETIC)_A_(BJT|NMOS)(?:_B_(BJT|NMOS))?);')
    if (-not $axisMatch.Success -or
            [int]$axisMatch.Groups[1].Value.Substring(7, 1) -ne $plan.channelCount -or
            ($plan.channelCount -eq 1 -and $axisMatch.Groups[4].Success) -or
            ($plan.channelCount -eq 2 -and -not $axisMatch.Groups[4].Success) -or
            $plan.referenceArrangement -cne $axisMatch.Groups[2].Value) {
        throw ('Q30 topology/reference identity is malformed for seed ' + $Seed)
    }
    $expectedTopology = $axisMatch.Groups[1].Value +
        '_C12_' + $(if ($plan.support.C12) { 'Y' } else { 'N' }) +
        '_S5_' + $(if ($plan.support.S5) { 'Y' } else { 'N' }) +
        '_S12_' + $(if ($plan.support.S12) { 'Y' } else { 'N' }) +
        '_F' + $plan.support.filters + '_O' + $plan.support.outputIndicators +
        '_B5_' + $(if ($plan.support.bleeder5) { 'Y' } else { 'N' })
    if ($plan.topology -cne $expectedTopology -or
            $plan.canonical -cnotmatch (';packages=' + $plan.packageCount + ';') -or
            $plan.packageCount -lt 20 -or $plan.packageCount -gt 40) {
        throw ('Q30 topology/package identity is inconsistent for seed ' + $Seed)
    }
    $relay = if ($plan.channelCount -eq 1) { 'KA' } else { 'KB' }
    $relayFault = if ($plan.channelCount -eq 1) {
        'RELAY_A_COIL_OPEN'
    } else {
        'RELAY_B_COIL_OPEN'
    }
    $expectedFaults = @(
        @{ Id = 'DREV_OPEN'; Owner = 'DREV' },
        @{ Id = 'REN_OPEN'; Owner = 'REN' },
        @{ Id = 'SENSOR_A_OPEN'; Owner = 'RSA' },
        @{ Id = 'DRIVE_A_OPEN'; Owner = 'RDA' },
        @{ Id = $relayFault; Owner = $relay }
    )
    $faults = @($plan.faults)
    if ($faults.Count -ne $expectedFaults.Count) {
        throw ('Q30 plan fault count mismatch for seed ' + $Seed)
    }
    for ($index = 0; $index -lt $expectedFaults.Count; $index++) {
        if ($faults[$index].id -cne $expectedFaults[$index].Id -or
                $faults[$index].owner -cne $expectedFaults[$index].Owner) {
            throw ('Q30 plan fault/owner/order mismatch for seed ' + $Seed +
                ' at ordinal ' + $index)
        }
    }
    $stateCount = 1 -shl $plan.channelCount
    $expectedSamples = (7 + $plan.channelCount) * $stateCount + 1
    $expectedWorkUnits = $stateCount + 1
    if ($plan.diagnosticProviderDeveloper -cne 'rb30-control-diagnostic@2' -or
            $plan.diagnosticProviderNormal -cne 'rb30-control-diagnostic@4' -or
            $plan.diagnosticTemplate -cne 'RB30_CHANNEL_INPUT_SWEEP_V1' -or
            $plan.temporalContract -cne 'RB30_CHANNEL_FUNCTION@3' -or
            $plan.diagnosticSamplesPerHypothesis -ne $expectedSamples -or
            $plan.sampleSeconds -ne 0.03 -or
            $plan.profileWorkUnits -ne $expectedWorkUnits -or
            $plan.customerRetestWorkUnits -ne $expectedWorkUnits -or
            $plan.maxJobMillis -ne 90000 -or $plan.maxJobSteps -ne 640 -or
            $plan.activeOperationMillis -ne 5000) {
        throw ('Q30 plan provider, evidence, or unchanged job-budget contract mismatch for seed ' + $Seed)
    }
    $executionFaults = New-Object Collections.Generic.List[string]
    $executionFaults.Add($relayFault)
    foreach ($fault in $expectedFaults[0..3]) { $executionFaults.Add($fault.Id) }
    return [pscustomobject]@{
        Seed = $Seed
        RawLine = $rawLine
        Canonical = $plan.canonical
        SupportIdentity = $supportIdentity
        Topology = $expectedTopology
        ChannelCount = [int]$plan.channelCount
        ActiveChannels = $expectedChannels
        PackageCount = [int]$plan.packageCount
        RelayFault = $relayFault
        FaultIds = @($executionFaults.ToArray())
    }
    } catch {
        Write-Q30CasePlanMetadataFailure -Seed $Seed -Violation ([string]$_.Exception.Message) -Lines $lines
        throw
    }
}

function Test-Q30SensitivityCaseOutput {
    param(
        [Parameter(Mandatory = $true)][string]$Stdout,
        [Parameter(Mandatory = $true)][string]$Seed,
        [Parameter(Mandatory = $true)][string]$FaultId,
        [Parameter(Mandatory = $true)][string]$SupportIdentity,
        [Parameter(Mandatory = $true)][string]$Topology,
        [Parameter(Mandatory = $true)][string]$Canonical,
        [Parameter(Mandatory = $true)][bool]$TerminationProven,
        [Parameter(Mandatory = $true)][int]$ExitCode
    )
    $pairRows = @($Stdout -split '\r?\n' | Where-Object {
        $_.StartsWith('Q30_STEP_PAIR ')
    })
    $caseIdentityPattern = ' support=' + [regex]::Escape($SupportIdentity) +
        ' topology=' + [regex]::Escape($Topology) +
        ' canonicalPlan=' + [regex]::Escape($Canonical)
    $pairPattern = '^Q30_STEP_PAIR seed=' + [regex]::Escape($Seed) +
        $caseIdentityPattern + ' fault=' + [regex]::Escape($FaultId) + ' '
    $finalPattern = '(?m)^PASS: Q30 production solver step sensitivity assertions=\d+ seed=' +
        [regex]::Escape($Seed) + $caseIdentityPattern + ' faults=1 ' +
        'referenceMaximumStepSeconds=2\.50000000e-06 ' +
        'productionCandidateMaximumStepSeconds=5\.00000000e-06 ' +
        'candidateKind=PRODUCTION_5_US elapsedMillis=\d+\r?$'
    return $TerminationProven -and $ExitCode -eq 0 -and
        $pairRows.Count -eq 1 -and [regex]::IsMatch($pairRows[0], $pairPattern) -and
        [regex]::IsMatch($Stdout, $finalPattern)
}

$taskRoot = ''
$resultCode = 2
try {
    if ($PSBoundParameters.ContainsKey('QuickPlayGateSeeds')) {
        $fixtureSuites = @($Suite)
        if ($fixtureSuites.Count -ne 1 -or $fixtureSuites[0] -cne 'QuickPlayGateCorpus') {
            throw '-QuickPlayGateSeeds is allowed only with exactly -Suite QuickPlayGateCorpus.'
        }
        $distinctFixtureSeeds = @($QuickPlayGateSeeds | Select-Object -Unique)
        if ($QuickPlayGateSeeds.Count -lt 1 -or $QuickPlayGateSeeds.Count -gt 128 -or
                $distinctFixtureSeeds.Count -ne $QuickPlayGateSeeds.Count) {
            throw '-QuickPlayGateSeeds requires 1-128 distinct signed-long values.'
        }
    }
    if ($PSBoundParameters.ContainsKey('Q30PlanSeeds')) {
        $planFixtureSuites = @($Suite)
        if ($planFixtureSuites.Count -ne 1 -or $planFixtureSuites[0] -cne 'Q30PlanContractTest') {
            throw '-Q30PlanSeeds is allowed only with exactly -Suite Q30PlanContractTest.'
        }
        $distinctPlanSeeds = @($Q30PlanSeeds | Select-Object -Unique)
        if ($Q30PlanSeeds.Count -lt 1 -or $Q30PlanSeeds.Count -gt 128 -or
                $distinctPlanSeeds.Count -ne $Q30PlanSeeds.Count) {
            throw '-Q30PlanSeeds requires 1-128 distinct signed-long values.'
        }
    }
    Import-Module (Join-Path $PSScriptRoot 'VerifierIsolation.psm1') -Force
    $repositoryRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
    if ([String]::IsNullOrWhiteSpace($JavaHome)) {
        throw 'Select JDK 8 with -JavaHome or JAVA_HOME.'
    }
    $selectedJavaHome = (Resolve-Path -LiteralPath $JavaHome).Path
    $java = Join-Path $selectedJavaHome 'bin/java.exe'
    $javac = Join-Path $selectedJavaHome 'bin/javac.exe'
    $javaVersion = Invoke-VerifierBoundedProcess $java @('-version') 15000
    $javacVersion = Invoke-VerifierBoundedProcess $javac @('-version') 15000
    if ($javaVersion.ExitCode -ne 0 -or -not $javaVersion.TerminationProven -or
            ($javaVersion.Stdout + $javaVersion.Stderr) -notmatch 'version\s+"1\.8\.' -or
            $javacVersion.ExitCode -ne 0 -or -not $javacVersion.TerminationProven -or
            ($javacVersion.Stdout + $javacVersion.Stderr) -notmatch 'javac\s+1\.8\.') {
        throw 'The selected toolchain is not a verified JDK 8 compiler/runtime.'
    }
    Write-Host ('JDK: ' + ($javacVersion.Stdout + $javacVersion.Stderr).Trim())

    $tempRoot = [IO.Path]::GetFullPath([IO.Path]::GetTempPath())
    $taskRoot = Join-Path $tempRoot ('TroubleshootJS-current-contracts-' +
        [Guid]::NewGuid().ToString('N'))
    Assert-VerifierNoReparseAncestors $taskRoot
    if (Test-Path -LiteralPath $taskRoot) { throw 'Current-contract scratch already exists.' }
    New-Item -ItemType Directory -Path $taskRoot | Out-Null
    $classes = Join-Path $taskRoot 'classes'
    $emptySourcePath = Join-Path $taskRoot 'empty-sourcepath'
    New-Item -ItemType Directory -Path $classes, $emptySourcePath | Out-Null
    $gwtJars = @('gwt-user-2.7.0.jar', 'gwt-dev-2.7.0.jar') | ForEach-Object {
        $jar = Join-Path $repositoryRoot ('.tools/gwt-2.7.0/' + $_)
        if (-not (Test-Path -LiteralPath $jar -PathType Leaf)) {
            throw ('Missing repository GWT dependency: ' + $_)
        }
        $jar
    }
    $classPath = [String]::Join(';', @($classes) + @($gwtJars))

    # The accepted Task35 developer verifier contains a generic reference
    # comparison accepted by GWT but rejected by javac8. Exclude only that
    # unrelated verifier, with a fail-closed replacement: invoking it fails.
    # All current production source comes from the checkout, except for the
    # exact single-method CirSim.console scratch shim selected below.
    $stub = Join-Path $taskRoot 'PhysicalSpecificationDeveloperVerifier.java'
    [IO.File]::WriteAllText($stub, @'
package com.lushprojects.circuitjs1.client;
final class PhysicalSpecificationDeveloperVerifier {
    static void verify(CirSim sim) {
        throw new UnsupportedOperationException(
            "Task35 verifier requires the separate GWT production gate");
    }
}
'@, (New-Object Text.UTF8Encoding($false)))
    $needsQ30NativeLoggerBridge = $Suite.Count -eq 0 -or
        $Suite -contains 'Q30ServiceFlowContractTest' -or
        $Suite -contains 'Q30PowerReadinessContractTest' -or
        $Suite -contains 'Q30TemporalWorkContractTest' -or
        $Suite -contains 'DiagnosticServicePreparationContractTest' -or
        $Suite -contains 'Q30CatalogTransferContractTest' -or
        $Suite -contains 'Q30SolverStepSensitivityContractTest'
    $nativeLoggerShim = ''
    if ($needsQ30NativeLoggerBridge) {
        # CircuitJS's unconnected-node and convergence diagnostics use JSNI.
        # Keep those diagnostics visible on the JVM and preserve every solver
        # method/model by changing only the exact logger declaration in a
        # task-owned source copy. Never edit the checked-in production source.
        $cirSimSource = Join-Path $repositoryRoot 'src/com/lushprojects/circuitjs1/client/CirSim.java'
        $cirSimText = [IO.File]::ReadAllText($cirSimSource)
        $loggerPattern = 'public static native void console\(String text\)\s*/\*\-\{\s*console\.log\(text\);\s*\}\-\*/;'
        $loggerRegex = New-Object Text.RegularExpressions.Regex($loggerPattern)
        $loggerMatches = $loggerRegex.Matches($cirSimText)
        if ($loggerMatches.Count -ne 1) {
            throw ('Expected exactly one CirSim JSNI console logger, found ' +
                $loggerMatches.Count)
        }
        $nativeLoggerShim = Join-Path $taskRoot 'CirSim.java'
        $loggerShimText = $loggerRegex.Replace($cirSimText,
            'public static void console(String text) { System.err.println(text); }', 1)
        if ($loggerShimText -eq $cirSimText) {
            throw 'CirSim JSNI logger replacement did not change the scratch source.'
        }
        [IO.File]::WriteAllText($nativeLoggerShim, $loggerShimText,
            (New-Object Text.UTF8Encoding($false)))
        Write-Host 'NATIVE BRIDGE: scratch-only exact CirSim.console -> System.err.println; CircuitJS solver/models unchanged.'
    }
    $clientSource = Join-Path $repositoryRoot 'src/com/lushprojects/circuitjs1/client'
    $sourcePaths = @(Get-ChildItem -LiteralPath $clientSource -Filter '*.java' |
        Where-Object { $_.Name -ne 'PhysicalSpecificationDeveloperVerifier.java' -and
            (-not $needsQ30NativeLoggerBridge -or $_.Name -ne 'CirSim.java') } |
        Sort-Object Name | ForEach-Object { $_.FullName })
    $sourcePaths += $stub
    if ($needsQ30NativeLoggerBridge) { $sourcePaths += $nativeLoggerShim }
    $testDefinitions = @(
        @{ Name = 'StagedFamilyRegistrationContractTest'; Marker = 'staged family registration contracts ' },
        @{ Name = 'GeneratedExternalPowerBindingsControlObservationContractTest'; Marker = 'generated power control observation contracts assertions=' },
        @{ Name = 'ProceduralFamilyContractTest'; Marker = 'procedural family contracts ' },
        @{ Name = 'QuickPlayPhysicalMatrixContractTest'; Marker = 'Quick Play physical matrix contracts ' },
        # Schedule the largest short-budget construction cohort first; cases and budgets are unchanged.
        @{ Name = 'QuickPlayContractTest'; Marker = 'Quick Play current seed envelopes and construction' },
        @{ Name = 'PcbCompactionContractTest'; Marker = 'PCB compaction contracts ' },
        @{ Name = 'DensePcbPackingContractTest'; Marker = 'dense PCB packing ' },
        @{ Name = 'VisualWorkbenchContractTest'; Marker = 'visual workbench contracts ' },
        @{ Name = 'Task43PhysicalEndpointContractTest'; Marker = 'Task43 current physical endpoint contracts ' },
        @{ Name = 'ArchitectureFootprintContractTest'; Marker = 'architecture footprint contracts ' },
        @{ Name = 'PhysicalServiceabilityContractTest'; Marker = 'physical serviceability contracts ' },
        @{ Name = 'U04SessionContractTest'; Marker = 'U04 session contracts ' },
        @{ Name = 'U05DifficultyContractTest'; Marker = 'U05 difficulty contracts ' },
        @{ Name = 'E03RelayContractTest'; Marker = 'E03 relay contracts ' },
        @{ Name = 'E02RegulatorContractTest'; Marker = 'E02 regulator contracts ' },
        @{ Name = 'E04SensorControlContractTest'; Marker = 'E04 sensor-control contracts ' },
        @{ Name = 'SensorControlFamilyContractTest'; Marker = 'SensorControl family contracts ' },
        @{ Name = 'Q15ControlBoardContractTest'; Marker = 'Q15 control board contracts ' },
        @{ Name = 'Q30PlanContractTest'; Marker = 'Q30 plan contracts ' },
        @{ Name = 'Q30RelayServiceContractTest'; Marker = 'Q30 relay service contracts ' },
        @{ Name = 'Q30ServiceFlowContractTest'; Marker = 'Q30 service flow contracts ' },
        @{ Name = 'Q30PowerReadinessContractTest'; Marker = 'Q30 power readiness contracts ' },
        @{ Name = 'Q30TemporalWorkContractTest'; Marker = 'Q30 temporal work contracts ' },
        @{ Name = 'DiagnosticServicePreparationContractTest'; Marker = 'diagnostic service preparation contracts ' },
        @{ Name = 'Q30SolverStepSensitivityContractTest'; Marker = 'Q30 production solver step sensitivity ' },
        @{ Name = 'Q30NormalCorpusContractTest'; Marker = 'Q30 normal corpus contracts ' },
        @{ Name = 'Q30GenerationRequestContractTest'; Marker = 'q30 generation request contracts ' },
        @{ Name = 'Q30GenerationMeasurementBudgetContractTest'; Marker = 'Q30 generation measurement budget contracts ' },
        @{ Name = 'Q30NormalExecutionPolicyContractTest'; Marker = 'Q30 normal execution policy contracts ' },
        @{ Name = 'Q30CatalogIdentityContractTest'; Marker = 'Q30 catalog identity contracts ' },
        @{ Name = 'Q30CatalogTransferContractTest'; Marker = 'Q30 catalog transfer contracts ' },
        @{ Name = 'Rb30PhysicalMetadataContractTest'; Marker = 'Q30 physical metadata contracts ' },
        @{ Name = 'MediumBoardPhysicalPolicyContractTest'; Marker = 'medium physical policy contracts ' },
        @{ Name = 'MediumBoardNormalAdmissionContractTest'; Marker = 'medium normal admission contracts ' },
        @{ Name = 'MediumBoardFloorplanningContractTest'; Marker = 'medium board floorplanning contracts ' },
        @{ Name = 'ChallengeDescriptorContractTest'; Marker = 'Task46 ' },
        @{ Name = 'FunctionalBlockContractTest'; Marker = 'Task44 ' },
        @{ Name = 'ElectricalPortContractTest'; Marker = 'Task45 ' },
        @{ Name = 'BoundedAssemblyContractTest'; Marker = 'current resistive assembly contracts ' },
        @{ Name = 'ControlledIndicatorAssemblyContractTest'; Marker = 'current controlled-indicator contracts ' },
        @{ Name = 'SwitchedLowSideCompatibilityContractTest'; Marker = 'current switched-low-side compatibility ' },
        @{ Name = 'Task49ValueSynthesisContractTest'; Marker = 'current value synthesis ' },
        @{ Name = 'A05RoleContractTest'; Marker = 'A05 role providers ' },
        @{ Name = 'E01SourceContractTest'; Marker = 'E01 source contracts ' },
        @{ Name = 'A06PowerContractTest'; Marker = 'A06 power contracts ' },
        @{ Name = 'A07ExecutionContractTest'; Marker = 'A07 execution contracts ' },
        @{ Name = 'Q30OwnedLuZeroRowContractTest'; Marker = 'Q30 owned LU zero-row contracts ' },
        @{ Name = 'Q30LuPivotCollectionContractTest'; Marker = 'Q30 LU pivot collection contracts ' },
        @{ Name = 'A09DiagnosticContractTest'; Marker = 'A09 diagnostic contracts ' },
        @{ Name = 'D01DiagnosticContractTest'; Marker = 'D01 diagnostic contracts ' },
        @{ Name = 'A10GenerationContractTest'; Marker = 'A10 generation contracts ' },
        @{ Name = 'A10RoutingContractTest'; Marker = 'A10 routing rejection contracts ' },
        @{ Name = 'A10DependencyContractTest'; Marker = 'A10 dependency contracts ' },
        @{ Name = 'A11ProviderConformanceTest'; Marker = 'A11 provider conformance' },
        @{ Name = 'A03IdentityContractTest'; Marker = 'A03IdentityContractTest ' },
        @{ Name = 'A02CandidateContractTest'; Marker = 'A02CandidateContractTest' },
        @{ Name = 'A02GeometryContractTest'; Marker = 'A02GeometryContractTest ' },
        @{ Name = 'P01PhysicalPoseContractTest'; Marker = 'P01 physical pose contracts ' },
        @{ Name = 'P02ConductorContractTest'; Marker = 'P02 conductor contracts ' },
        @{ Name = 'U01ViewportContractTest'; Marker = 'U01 viewport contracts ' },
        @{ Name = 'U02MeasurementContractTest'; Marker = 'U02 measurement contracts ' },
        @{ Name = 'U03ObservationContractTest'; Marker = 'U03 observation contracts ' },
        @{ Name = 'WorkbenchLiftedLeadAppearanceContractTest'; Marker = 'lifted lead appearance contracts ' },
        @{ Name = 'P03PlacementContractTest'; Marker = 'P03 placement contracts ' },
        @{ Name = 'RouterQueueContractTest'; Marker = 'router queue parity ' },
        @{ Name = 'P04RoutingContractTest'; Marker = 'P04 routing contracts ' },
        @{ Name = 'P05RoutingContractTest'; Marker = 'P05 routing contracts ' },
        @{ Name = 'P06FactoryLinkContractTest'; Marker = 'P06 factory-link contracts ' },
        @{ Name = 'P07TwoLayerContractTest'; Marker = 'P07 two-layer contracts ' },
        @{ Name = 'PcbLayerRoutingResumptionContractTest'; Marker = 'Pcb layer routing resumption contracts ' },
        @{ Name = 'P08ScalabilityContractTest'; Marker = 'P08 scalable physical contracts ' },
        @{ Name = 'P09EnvelopeCorpus'; Marker = 'P09 physical envelope corpus ' },
        @{ Name = 'QuickPlayGateCorpus'; Marker = 'Quick Play gate corpus ' },
        @{ Name = 'QuickPlayGateHoldoutCorpus'; Marker = 'Quick Play gate corpus ' },
        @{ Name = 'QuickPlayGateContractTest'; Marker = 'Quick Play gate contracts ' },
        @{ Name = 'P09EnvelopeContractTest'; Marker = 'P09 physical envelope contracts ' },
        @{ Name = 'P07LayerCorpus'; Marker = 'P07 frozen layer corpus ' },
        @{ Name = 'P05RoutingCorpus'; Marker = 'P05 frozen corpus ' },
        @{ Name = 'P05GenerationCorpus'; Marker = 'P05 generation comparison ' },
        @{ Name = 'A02ReplayContractTest'; Marker = 'A02ReplayContractTest ' },
        @{ Name = 'ElectricalUnitPackageMapContractTest'; Marker = 'ElectricalUnitPackageMapContractTest ' },
        @{ Name = 'A04ConstructionContractTest'; Marker = 'A04ConstructionContractTest ' },
        @{ Name = 'A04PhysicalDeclarationContractTest'; Marker = 'A04PhysicalDeclarationContractTest ' })
    $testClasses = @($testDefinitions | ForEach-Object { $_.Name })
    foreach ($selectedSuite in $Suite) {
        if ($testClasses -notcontains $selectedSuite) { throw ('Unknown current suite: ' + $selectedSuite) }
    }
    foreach ($testClass in $testClasses) {
        $testSource = Join-Path $repositoryRoot ('tests/contracts/' + $testClass + '.java')
        if (-not (Test-Path -LiteralPath $testSource -PathType Leaf)) {
            throw ('Missing current contract: ' + $testClass)
        }
        $sourcePaths += $testSource
    }
    $argumentsFile = Join-Path $taskRoot 'sources.txt'
    $sourcePaths += (Join-Path $repositoryRoot 'tests/contracts/P03StructuralFixtures.java')
    $sourcePaths += (Join-Path $repositoryRoot 'tests/contracts/P08ReferenceConductorBuilder.java')
    $sourcePaths += (Join-Path $repositoryRoot 'tests/contracts/P08StructuralFixtures.java')
    [IO.File]::WriteAllLines($argumentsFile, @($sourcePaths | ForEach-Object {
        '"' + $_.Replace('\', '/') + '"'
    }), (New-Object Text.UTF8Encoding($false)))
    $compiled = Invoke-VerifierBoundedProcess $javac @('-source', '7', '-target', '7',
        '-encoding', 'UTF-8', '-classpath', $classPath, '-sourcepath', $emptySourcePath,
        '-d', $classes, ('@' + $argumentsFile)) 60000
    Write-Host $compiled.Stdout
    if ($compiled.Stderr) { Write-Host $compiled.Stderr }
    if (-not $compiled.TerminationProven -or $compiled.ExitCode -ne 0) {
        throw ('Current production-class compilation failed, exit ' + $compiled.ExitCode)
    }
    $receipts = New-Object Collections.Generic.List[string]
    $outputs = @{}
    $q30PlanBySeed = @{}
    $q30SensitivityFaultsBySeed = @{}
    $needsQ30CorpusPlans = $Suite.Count -eq 0 -or
        $Suite -contains 'Q30NormalCorpusContractTest'
    $needsQ30ServicePlans = $Suite.Count -eq 0 -or
        $Suite -contains 'Q30ServiceFlowContractTest'
    $needsQ30SensitivityPlans = $Suite.Count -eq 0 -or
        $Suite -contains 'Q30SolverStepSensitivityContractTest'
    $q30RequestedSeedValues = New-Object Collections.Generic.List[long]
    if ($needsQ30CorpusPlans) {
        if ($Q30CorpusSeeds.Count -lt 1 -or $Q30CorpusSeeds.Count -gt 64 -or
                @($Q30CorpusSeeds | Select-Object -Unique).Count -ne $Q30CorpusSeeds.Count) {
            throw 'Q30 corpus requires 1-64 distinct exact signed-long seeds.'
        }
        foreach ($seedValue in $Q30CorpusSeeds) { $q30RequestedSeedValues.Add($seedValue) }
    }
    if ($needsQ30ServicePlans) {
        if ($Q30ServiceSeeds.Count -lt 1 -or $Q30ServiceSeeds.Count -gt 32 -or
                @($Q30ServiceSeeds | Select-Object -Unique).Count -ne $Q30ServiceSeeds.Count) {
            throw 'Q30 service seeds require 1-32 distinct exact signed-long values.'
        }
        foreach ($seedValue in $Q30ServiceSeeds) { $q30RequestedSeedValues.Add($seedValue) }
    }
    if ($needsQ30SensitivityPlans) {
        if ($Q30SensitivitySeeds.Count -lt 1 -or $Q30SensitivitySeeds.Count -gt 32 -or
                @($Q30SensitivitySeeds | Select-Object -Unique).Count -ne $Q30SensitivitySeeds.Count) {
            throw 'Q30 sensitivity seeds require 1-32 distinct exact signed-long values.'
        }
        if ($PSBoundParameters.ContainsKey('Q30SensitivityFaults') -and
                ($Q30SensitivityFaults.Count -lt 1 -or
                    @($Q30SensitivityFaults | Select-Object -Unique).Count -ne $Q30SensitivityFaults.Count)) {
            throw 'Explicit Q30 sensitivity faults must be nonempty and distinct.'
        }
        foreach ($seedValue in $Q30SensitivitySeeds) { $q30RequestedSeedValues.Add($seedValue) }
    }
    $q30RequestedSeedTexts = New-Object Collections.Generic.List[string]
    $q30RequestedSeedSeen = @{}
    foreach ($seedValue in $q30RequestedSeedValues) {
        $seedText = $seedValue.ToString([Globalization.CultureInfo]::InvariantCulture)
        if (-not $q30RequestedSeedSeen.ContainsKey($seedText)) {
            $q30RequestedSeedSeen[$seedText] = $true
            $q30RequestedSeedTexts.Add($seedText)
        }
    }
    foreach ($seedText in $q30RequestedSeedTexts) {
        $planMetadata = Get-Q30CasePlanMetadata $java $classPath $seedText
        $q30PlanBySeed[$seedText] = $planMetadata
        Write-Host $planMetadata.RawLine
        $receipts.Add($planMetadata.RawLine)
    }
    if ($needsQ30SensitivityPlans) {
        foreach ($seedValue in $Q30SensitivitySeeds) {
            $seedText = $seedValue.ToString([Globalization.CultureInfo]::InvariantCulture)
            $planMetadata = $q30PlanBySeed[$seedText]
            $selectedFaults = if ($PSBoundParameters.ContainsKey('Q30SensitivityFaults')) {
                @($Q30SensitivityFaults)
            } else {
                @($planMetadata.FaultIds)
            }
            foreach ($faultId in $selectedFaults) {
                if ($planMetadata.FaultIds -cnotcontains $faultId) {
                    throw ('Explicit Q30 sensitivity fault ' + $faultId +
                        ' is not declared by seed ' + $seedText + '; no cases were started.')
                }
            }
            $q30SensitivityFaultsBySeed[$seedText] = @($selectedFaults)
        }
    }
    foreach ($definition in $testDefinitions) {
        $testClass = $definition.Name
        if ($Suite.Count -gt 0 -and $Suite -notcontains $testClass) { continue }
        if ($testClass -eq 'QuickPlayPhysicalMatrixContractTest') {
            # Preserve the original ten-family aggregate allowance. The added
            # medium census has 48 roots maximum plus 20 exact replays; its
            # separately reviewed 600s test cap does not change player budgets.
            $smallFamilies = @('LED_INDICATOR', 'DIODE_PROTECTED_INDICATOR',
                'PARALLEL_DUAL_INDICATOR', 'RC_DELAY', 'NPN_LOW_SIDE_SWITCH',
                'NMOS_LOW_SIDE_SWITCH', 'RELAY_OUTPUT', 'SENSOR_CONTROL',
                'RB15_CONTROL', 'COMPOSED_CONTROLLED_INDICATOR')
            $physicalGroups = @(
                @{ Args = @('--small-catalog'); Families = $smallFamilies; Budget = 180000 },
                @{ Args = @('--family', 'RB30_CONTROL'); Families = @('RB30_CONTROL'); Budget = 600000 }
            )
            $physicalOutputs = New-Object Collections.Generic.List[string]
            foreach ($group in $physicalGroups) {
                $arguments = @('-ea', '-cp', $classPath,
                    'com.lushprojects.circuitjs1.client.QuickPlayPhysicalMatrixContractTest') + $group.Args
                $tested = Invoke-VerifierBoundedProcess $java $arguments $group.Budget
                Write-Host $tested.Stdout
                if ($tested.Stderr) { Write-Host $tested.Stderr }
                $receipts.Add($tested.Stdout)
                $physicalOutputs.Add($tested.Stdout)
                $matrixRows = @($tested.Stdout -split '\r?\n' | Where-Object { $_.StartsWith('PHYSICAL_MATRIX|') })
                $completion = '(?m)^PASS: Quick Play physical matrix contracts pairs=' + $group.Families.Count + ' assertions='
                if (-not $tested.TerminationProven -or $tested.ExitCode -ne 0 -or
                        $tested.Stdout -notmatch $completion -or $matrixRows.Count -ne $group.Families.Count) {
                    throw ('Physical matrix group failed or incomplete: ' + [String]::Join(',', $group.Families) +
                        ', exit ' + $tested.ExitCode)
                }
                for ($rowIndex = 0; $rowIndex -lt $matrixRows.Count; $rowIndex++) {
                    $fields = $matrixRows[$rowIndex] -split '\|'
                    $family = $group.Families[$rowIndex]
                    $profile = if ($family -in @('COMPOSED_CONTROLLED_INDICATOR','RB30_CONTROL')) { 'MEDIUM' } else { 'EASY' }
                    if ($fields.Count -ne 13 -or $fields[1] -ne $family -or $fields[2] -ne $profile -or
                            $fields[3] -ne '20' -or $fields[4] -ne '20' -or
                            [int]$fields[11] -gt 48 -or [int]$fields[11] -lt 20) {
                        throw ('Physical matrix family/count identity changed: ' + $family)
                    }
                }
            }
            $outputs[$testClass] = [String]::Join([Environment]::NewLine, $physicalOutputs)
            $coverage = 'PASS: physical matrix partition coverage families=11 accepted=220 exactReplays=220 smallChildBudgetMs=180000 mediumChildBudgetMs=600000'
            Write-Host $coverage
            $receipts.Add($coverage)
            continue
        }
        if ($testClass -eq 'ProceduralFamilyContractTest') {
            # The prior ten families retain their exact-seed oracle and 60s
            # cohort cap. Q30 separately checks the same 16 values as public
            # roots: at most four candidates plus one exact accepted replay.
            # Its reviewed 300s cohort cap covers at most 40 constructions;
            # production budgets and the independent raw Q30 corpus are unchanged.
            $proceduralClass = 'com.lushprojects.circuitjs1.client.ProceduralFamilyContractTest'
            $listed = Invoke-VerifierBoundedProcess $java @('-ea', '-cp', $classPath,
                $proceduralClass, '--list-families') 60000
            if (-not $listed.TerminationProven -or $listed.ExitCode -ne 0) {
                throw ('Procedural family catalog enumeration failed, exit ' + $listed.ExitCode)
            }
            $familyFrames = @([regex]::Matches($listed.Stdout,
                '(?m)^PROCEDURAL_FAMILIES\|([A-Z0-9_,]+)\r?$'))
            if ($familyFrames.Count -ne 1) { throw 'Missing unique procedural family catalog frame' }
            $families = @($familyFrames[0].Groups[1].Value -split ',')
            $expectedFamilies = @('LED_INDICATOR', 'DIODE_PROTECTED_INDICATOR',
                'PARALLEL_DUAL_INDICATOR', 'RC_DELAY', 'NPN_LOW_SIDE_SWITCH',
                'NMOS_LOW_SIDE_SWITCH', 'RELAY_OUTPUT', 'SENSOR_CONTROL',
                'RB15_CONTROL', 'COMPOSED_CONTROLLED_INDICATOR', 'RB30_CONTROL')
            if ($families.Count -ne $expectedFamilies.Count -or
                    [String]::Join(',', $families) -ne
                    [String]::Join(',', $expectedFamilies)) {
                throw 'Invalid procedural family catalog enumeration'
            }
            $cohortSeeds = @{
                '0' = @('0', '1', '4', '17', '42', '-1',
                    '-9223372036854775808', '9223372036854775807')
                '1' = @('936927718510540323', '-6751984890832468710',
                    '4374486180868546127', '-1470617604193128648',
                    '9007199254740993', '-9007199254740993',
                    '281474976710656', '-4194978729361594021')
            }
            $proceduralReceipts = New-Object Collections.Generic.List[string]
            foreach ($family in $families) {
                $rootMode = $family -ceq 'RB30_CONTROL'
                $cohortBudget = if ($rootMode) { 300000 } else { 60000 }
                $rowPrefix = if ($rootMode) { 'PROCEDURAL_ROOT_ROW|' } else { 'PROCEDURAL_ROW|' }
                $summaryPrefix = if ($rootMode) { 'PROCEDURAL_ROOT_SUMMARY|' } else { 'PROCEDURAL_SUMMARY|' }
                foreach ($cohort in @(0, 1)) {
                    $tested = Invoke-VerifierBoundedProcess $java @('-ea', '-cp', $classPath,
                        $proceduralClass, '--family', $family, '--cohort',
                        [string]$cohort) $cohortBudget
                    Write-Host $tested.Stdout
                    if ($tested.Stderr) { Write-Host $tested.Stderr }
                    $rows = @($tested.Stdout -split '\r?\n' | Where-Object {
                        $_.StartsWith($rowPrefix)
                    })
                    $summaries = @($tested.Stdout -split '\r?\n' | Where-Object {
                        $_.StartsWith($summaryPrefix)
                    })
                    $summary = $summaryPrefix + $family + '|' + $cohort + '|'
                    if (-not $tested.TerminationProven -or $tested.ExitCode -ne 0 -or
                            $tested.Stdout -notmatch ('(?m)^PASS: ' +
                                [regex]::Escape($definition.Marker)) -or
                            $rows.Count -ne 8 -or $summaries.Count -ne 1 -or
                            -not $summaries[0].StartsWith($summary)) {
                        throw ('Procedural family cohort did not provide a qualified result: ' +
                            $family + '/' + $cohort + ', exit ' + $tested.ExitCode)
                    }
                    $attempts = @($tested.Stdout -split '\r?\n' | Where-Object {
                        $_.StartsWith('PROCEDURAL_ROOT_ATTEMPT|')
                    })
                    $replays = @($tested.Stdout -split '\r?\n' | Where-Object {
                        $_.StartsWith('PROCEDURAL_ROOT_REPLAY|')
                    })
                    $checkedAttempts = 0
                    $acceptedRoots = 0
                    for ($rowIndex = 0; $rowIndex -lt 8; $rowIndex++) {
                        $fields = $rows[$rowIndex] -split '\|'
                        if ($fields.Count -lt 5 -or $fields[1] -ne [string]$cohort -or
                                $fields[2] -ne $family -or
                                $fields[3] -ne $cohortSeeds[[string]$cohort][$rowIndex]) {
                            throw ('Procedural family seed/order mismatch: ' +
                                $family + '/' + $cohort + '/' + $rowIndex)
                        }
                        if ($rootMode) {
                            $rootSeed = $cohortSeeds[[string]$cohort][$rowIndex]
                            $attemptPrefix = 'PROCEDURAL_ROOT_ATTEMPT|' + $cohort + '|' + $family + '|' + $rootSeed + '|'
                            $rootAttempts = @($attempts | Where-Object { $_.StartsWith($attemptPrefix) })
                            if ($fields.Count -ne 12 -or $fields[4] -cnotin @('ACCEPT', 'REJECTED') -or
                                    $rootAttempts.Count -lt 1 -or $rootAttempts.Count -gt 4) {
                                throw ('Invalid Q30 root census row: ' + $rows[$rowIndex])
                            }
                            for ($ordinal = 0; $ordinal -lt $rootAttempts.Count; $ordinal++) {
                                $attempt = $rootAttempts[$ordinal] -split '\|', 8
                                $expectedSeed = [bigint]::Parse($rootSeed) +
                                    [bigint]::Parse('-7046029254386353131') * $ordinal
                                while ($expectedSeed -lt [long]::MinValue) {
                                    $expectedSeed += [bigint]::Parse('18446744073709551616')
                                }
                                while ($expectedSeed -gt [long]::MaxValue) {
                                    $expectedSeed -= [bigint]::Parse('18446744073709551616')
                                }
                                $expectedOutcome = if ($fields[4] -ceq 'ACCEPT' -and
                                    $ordinal -eq $rootAttempts.Count - 1) { 'ACCEPT' } else { 'REJECT' }
                                if ($attempt.Count -ne 8 -or $attempt[4] -cne [string]$ordinal -or
                                        $attempt[5] -cne $expectedSeed.ToString([Globalization.CultureInfo]::InvariantCulture) -or
                                        ($expectedOutcome -ceq 'ACCEPT' -and $attempt[6] -cne 'ACCEPT') -or
                                        ($expectedOutcome -ceq 'REJECT' -and $attempt[6] -cnotin @('ROUTE_REJECT', 'ENVELOPE_REJECT'))) {
                                    throw ('Q30 candidate identity/order/outcome mismatch: ' + $rootAttempts[$ordinal])
                                }
                            }
                            $checkedAttempts += $rootAttempts.Count
                            if ($fields[4] -ceq 'ACCEPT') {
                                $acceptedRoots++
                                $lastAttempt = $rootAttempts[-1] -split '\|', 8
                                $replay = 'PROCEDURAL_ROOT_REPLAY|' + $cohort + '|' + $family + '|' + $rootSeed + '|' + $fields[5] + '|PASS'
                                if ($fields[5] -cne $lastAttempt[5] -or $fields[6] -cne $lastAttempt[4] -or
                                        @($replays | Where-Object { $_ -ceq $replay }).Count -ne 1) {
                                    throw ('Q30 accepted root lacks its exact candidate replay: ' + $rootSeed)
                                }
                            } elseif ($rootAttempts.Count -ne 4 -or $fields[5] -cne 'none' -or $fields[6] -cne '-1') {
                                throw ('Q30 rejected root did not exhaust exactly four candidates: ' + $rootSeed)
                            }
                        }
                    }
                    if ($rootMode -and ($checkedAttempts -ne $attempts.Count -or
                            $replays.Count -ne $acceptedRoots -or $acceptedRoots -lt 6)) {
                        throw 'Q30 root attempt/replay population is incomplete or contains foreign rows'
                    }
                    $proceduralReceipts.Add($tested.Stdout)
                    $receipts.Add($tested.Stdout)
                }
            }
            $outputs[$testClass] = [String]::Join([Environment]::NewLine,
                $proceduralReceipts)
            $proceduralCoverage = 'PASS: procedural family partition coverage families=' +
                $families.Count + ' cohorts=' + $proceduralReceipts.Count +
                ' rows=' + ($proceduralReceipts.Count * 8) +
                ' exactSeedRows=160 rootRows=16 smallCohortBudgetMs=60000 q30RootCohortBudgetMs=300000'
            Write-Host $proceduralCoverage
            $receipts.Add($proceduralCoverage)
            continue
        }
        $testArguments = @('-ea', '-cp', $classPath,
            ('com.lushprojects.circuitjs1.client.' + $testClass))
        if ($testClass -eq 'Q30PlanContractTest' -and
                $PSBoundParameters.ContainsKey('Q30PlanSeeds')) {
            $planSeedTexts = @($Q30PlanSeeds | ForEach-Object {
                $_.ToString([Globalization.CultureInfo]::InvariantCulture)
            })
            $planSeedCsv = [String]::Join(',', $planSeedTexts)
            $exportArguments = @($testArguments) + @('--describe-plans', $planSeedCsv)
            $exported = Invoke-VerifierBoundedProcess $java $exportArguments 60000
            Write-Host $exported.Stdout
            if ($exported.Stderr) { Write-Host $exported.Stderr }
            $exportLines = @($exported.Stdout -split '\r?\n' |
                Where-Object { $_.Length -gt 0 })
            $exportMarker = 'PASS: Q30 plan contracts exported=' + $planSeedTexts.Count
            if (-not $exported.TerminationProven -or $exported.ExitCode -ne 0 -or
                    $exportLines.Count -ne ($planSeedTexts.Count + 1) -or
                    $exportLines[-1] -cne $exportMarker) {
                throw ('Q30 pure-plan export did not return the exact requested census; exit=' +
                    $exported.ExitCode + ', termination=' + $exported.TerminationProven)
            }
            $planExportSeen = @{}
            for ($planIndex = 0; $planIndex -lt $planSeedTexts.Count; $planIndex++) {
                $rawPlanRow = $exportLines[$planIndex]
                $planRowFields = $rawPlanRow -split '\|', 3
                $expectedPlanSeed = $planSeedTexts[$planIndex]
                if ($planRowFields.Count -ne 3 -or
                        $planRowFields[0] -cne 'Q30_PLAN_CANONICAL' -or
                        $planRowFields[1] -cne ('seed=' + $expectedPlanSeed) -or
                        $planRowFields[2] -cnotmatch ('^plan=rb30-plan@4;seed=' +
                            [regex]::Escape($expectedPlanSeed) + ';topology=[A-Z0-9_]+;support=')) {
                    throw ('Q30 pure-plan export row/order/canonical mismatch at ordinal ' +
                        $planIndex + ' for seed ' + $expectedPlanSeed)
                }
                if ($planExportSeen.ContainsKey($expectedPlanSeed)) {
                    throw ('Duplicate Q30 pure-plan export seed: ' + $expectedPlanSeed)
                }
                $planExportSeen[$expectedPlanSeed] = $true
                $receipts.Add($rawPlanRow)
            }
            if ($planExportSeen.Count -ne $planSeedTexts.Count) {
                throw ('Q30 pure-plan export expected ' + $planSeedTexts.Count +
                    ' distinct seeds, found ' + $planExportSeen.Count)
            }
            $receipts.Add($exportMarker)
            $outputs[$testClass] = $exported.Stdout
            continue
        }
        if ($testClass -eq 'QuickPlayGateCorpus' -and
                $PSBoundParameters.ContainsKey('QuickPlayGateSeeds')) {
            $testArguments += '--fixture-census'
            foreach ($seedValue in $QuickPlayGateSeeds) {
                $testArguments += $seedValue.ToString([Globalization.CultureInfo]::InvariantCulture)
            }
        }
        if ($testClass -eq 'Q30NormalCorpusContractTest') {
            if ($Q30CorpusSeeds.Count -lt 1 -or $Q30CorpusSeeds.Count -gt 64 -or
                    @($Q30CorpusSeeds | Select-Object -Unique).Count -ne $Q30CorpusSeeds.Count) {
                throw 'Q30 corpus requires 1-64 distinct exact signed-long seeds.'
            }
            $corpusFailures = New-Object Collections.Generic.List[string]
            $corpusOutputs = New-Object Collections.Generic.List[string]
            $acceptedRows = 0
            $rejectedRows = 0
            foreach ($seedValue in $Q30CorpusSeeds) {
                $seedText = $seedValue.ToString([Globalization.CultureInfo]::InvariantCulture)
                $tested = Invoke-VerifierBoundedProcess $java (@($testArguments) +
                    @('--seed', $seedText)) 60000
                Write-Host $tested.Stdout
                if ($tested.Stderr) { Write-Host $tested.Stderr }
                $jsonLines = @($tested.Stdout -split '\r?\n' | Where-Object {
                    $_.StartsWith('Q30_CORPUS_JSON:')
                })
                $marker = '(?m)^PASS: Q30 normal corpus contracts .* seed=' +
                    [regex]::Escape($seedText) + '\r?$'
                $qualified = $tested.TerminationProven -and $tested.ExitCode -eq 0 -and
                    $jsonLines.Count -eq 1 -and [regex]::IsMatch($tested.Stdout, $marker)
                if ($qualified) {
                    try {
                        $row = $jsonLines[0].Substring('Q30_CORPUS_JSON:'.Length) | ConvertFrom-Json
                        $qualified = $row.seed -ceq $seedText -and $row.cleanup -eq $true -and
                            $row.outcome -cin @('ACCEPTED', 'REJECTED')
                        if ($qualified -and $row.outcome -ceq 'ACCEPTED') { $acceptedRows++ }
                        if ($qualified -and $row.outcome -ceq 'REJECTED') { $rejectedRows++ }
                    } catch { $qualified = $false }
                }
                if (-not $qualified) { $corpusFailures.Add($seedText) }
                $rowReceipt = 'Q30_CORPUS_CASE seed=' + $seedText + ' exit=' +
                    $tested.ExitCode + ' termination=' + $tested.TerminationProven +
                    ' rowContract=' + $qualified + [Environment]::NewLine +
                    $tested.Stdout + [Environment]::NewLine + $tested.Stderr
                $receipts.Add($rowReceipt)
                $corpusOutputs.Add($rowReceipt)
            }
            $corpusSummary = 'Q30_CORPUS_CENSUS attempted=' + $Q30CorpusSeeds.Count +
                ' accepted=' + $acceptedRows + ' rejected=' + $rejectedRows +
                ' failed=' + $corpusFailures.Count + ' childBudgetMs=60000'
            Write-Host $corpusSummary
            $receipts.Add($corpusSummary)
            $corpusOutputs.Add($corpusSummary)
            $outputs[$testClass] = [String]::Join([Environment]::NewLine, $corpusOutputs)
            if ($ReceiptOutputPath -and $corpusFailures.Count -gt 0) {
                [IO.File]::WriteAllText([IO.Path]::GetFullPath($ReceiptOutputPath),
                    [String]::Join([Environment]::NewLine, $receipts),
                    (New-Object Text.UTF8Encoding($false)))
            }
            if ($corpusFailures.Count -gt 0) {
                throw ('Q30 corpus failed row contracts: ' + [String]::Join(',', $corpusFailures))
            }
            continue
        }
        if ($testClass -eq 'Q30SolverStepSensitivityContractTest') {
            if ($Q30SensitivitySeeds.Count -lt 1 -or $Q30SensitivitySeeds.Count -gt 32 -or
                    @($Q30SensitivitySeeds | Select-Object -Unique).Count -ne $Q30SensitivitySeeds.Count) {
                throw 'Q30 sensitivity requires 1-32 distinct exact signed-long seeds.'
            }
            $sensitivityFailures = New-Object Collections.Generic.List[string]
            $sensitivityOutputs = New-Object Collections.Generic.List[string]
            $sensitivityPassed = 0
            $sensitivityAttempted = 0
            foreach ($exactSeed in $Q30SensitivitySeeds) {
                $seedText = $exactSeed.ToString([Globalization.CultureInfo]::InvariantCulture)
                foreach ($faultId in $q30SensitivityFaultsBySeed[$seedText]) {
                    $sensitivityAttempted++
                    $tested = Invoke-VerifierBoundedProcess $java (@($testArguments) +
                        @('--seed', $seedText, '--fault', $faultId)) 60000
                    Write-Host $tested.Stdout
                    if ($tested.Stderr) { Write-Host $tested.Stderr }
                    $planMetadata = $q30PlanBySeed[$seedText]
                    $qualified = Test-Q30SensitivityCaseOutput -Stdout $tested.Stdout `
                        -Seed $seedText -FaultId $faultId `
                        -SupportIdentity $planMetadata.SupportIdentity `
                        -Topology $planMetadata.Topology -Canonical $planMetadata.Canonical `
                        -TerminationProven $tested.TerminationProven -ExitCode $tested.ExitCode
                    if ($qualified) { $sensitivityPassed++ }
                    else { $sensitivityFailures.Add($seedText + '/' + $faultId) }
                    $caseReceipt = 'Q30_SENSITIVITY_CASE seed=' + $seedText + ' fault=' +
                        $faultId + ' exit=' + $tested.ExitCode + ' termination=' +
                        $tested.TerminationProven + ' qualified=' + $qualified +
                        [Environment]::NewLine + $tested.Stdout + [Environment]::NewLine + $tested.Stderr
                    $receipts.Add($caseReceipt)
                    $sensitivityOutputs.Add($caseReceipt)
                }
            }
            $sensitivitySummary = 'Q30_SENSITIVITY_CENSUS attempted=' +
                $sensitivityAttempted +
                ' passed=' + $sensitivityPassed + ' failed=' + $sensitivityFailures.Count +
                ' childBudgetMs=60000'
            Write-Host $sensitivitySummary
            $receipts.Add($sensitivitySummary)
            $sensitivityOutputs.Add($sensitivitySummary)
            $outputs[$testClass] = [String]::Join([Environment]::NewLine, $sensitivityOutputs)
            if ($ReceiptOutputPath -and $sensitivityFailures.Count -gt 0) {
                [IO.File]::WriteAllText([IO.Path]::GetFullPath($ReceiptOutputPath),
                    [String]::Join([Environment]::NewLine, $receipts),
                    (New-Object Text.UTF8Encoding($false)))
            }
            if ($sensitivityFailures.Count -gt 0) {
                throw ('Q30 solver sensitivity failed cases: ' + [String]::Join(',', $sensitivityFailures))
            }
            continue
        }
        if ($testClass -eq 'Q30ServiceFlowContractTest') {
            # Keep every seed/fault service path inside its own original child
            # budget. Start with the metadata-declared relay so its physical
            # energy guard precedes the four common hypotheses.
            if ($Q30ServiceSeeds.Count -lt 1 -or $Q30ServiceSeeds.Count -gt 32 -or
                    @($Q30ServiceSeeds | Select-Object -Unique).Count -ne $Q30ServiceSeeds.Count) {
                throw 'Q30 service seeds require 1-32 distinct exact signed-long values.'
            }
            $q30Seeds = @($Q30ServiceSeeds | ForEach-Object {
                $_.ToString([Globalization.CultureInfo]::InvariantCulture)
            })
            $q30CaseReceipts = New-Object Collections.Generic.List[string]
            $q30CaseFailures = New-Object Collections.Generic.List[string]
            $q30Seen = @{}
            $q30ExpectedCount = 0
            foreach ($q30Seed in $q30Seeds) {
                $q30Faults = @($q30PlanBySeed[$q30Seed].FaultIds)
                $q30ExpectedCount += $q30Faults.Count
                foreach ($q30Fault in $q30Faults) {
                    $caseArguments = @($testArguments) + @('--seed', $q30Seed,
                        '--fault', $q30Fault)
                    $tested = Invoke-VerifierBoundedProcess $java $caseArguments 60000
                    Write-Host $tested.Stdout
                    if ($tested.Stderr) { Write-Host $tested.Stderr }
                    $passRows = @($tested.Stdout -split '\r?\n' | Where-Object {
                        $_ -cmatch '^PASS: Q30 service flow seed='
                    })
                    $finalMarkers = @($tested.Stdout -split '\r?\n' | Where-Object {
                        $_ -cmatch '^PASS: Q30 service flow contracts '
                    })
                    $rowPattern = '(?m)^PASS: Q30 service flow seed=' +
                        [regex]::Escape($q30Seed) + ' fault=' +
                        [regex]::Escape($q30Fault) +
                        ' target=[A-Z0-9_]+ fingerprintHash=[0-9a-fA-F]{1,8}$'
                    $finalPattern = '(?m)^PASS: Q30 service flow contracts \d+ assertions seed=' +
                        [regex]::Escape($q30Seed) + ' fault=' +
                        [regex]::Escape($q30Fault) + '\r?$'
                    $caseKey = $q30Seed + '|' + $q30Fault
                    if ($q30Seen.ContainsKey($caseKey)) {
                        throw ('Duplicate Q30 service case receipt: ' + $caseKey)
                    }
                    $rowQualified = $passRows.Count -eq 1 -and
                        [regex]::IsMatch($passRows[0], $rowPattern)
                    $finalQualified = $finalMarkers.Count -eq 1 -and
                        [regex]::IsMatch($finalMarkers[0], $finalPattern)
                    $caseIssues = New-Object Collections.Generic.List[string]
                    if (-not $tested.TerminationProven) { $caseIssues.Add('termination unproven') }
                    if ($tested.ExitCode -ne 0) { $caseIssues.Add('exit=' + $tested.ExitCode) }
                    if (-not $rowQualified) { $caseIssues.Add('exact PASS row missing or ambiguous') }
                    if (-not $finalQualified) { $caseIssues.Add('exact final marker missing or ambiguous') }
                    $caseOutcome = if ($caseIssues.Count -eq 0) { 'PASS' } else { 'FAIL' }
                    $caseReason = if ($caseIssues.Count -eq 0) { 'qualified' } else {
                        [String]::Join(', ', $caseIssues)
                    }
                    $q30Seen[$caseKey] = $caseOutcome
                    if ($caseOutcome -eq 'FAIL') {
                        $q30CaseFailures.Add($caseKey)
                        Write-Host ('Q30_CASE_FAIL|' + $caseKey + '|' + $caseReason)
                    }
                    $caseReceipt = 'Q30_CASE|' + $caseKey + '|outcome=' +
                        $caseOutcome + '|reason=' + $caseReason + [Environment]::NewLine +
                        'STDOUT_BEGIN' + [Environment]::NewLine + $tested.Stdout +
                        [Environment]::NewLine + 'STDOUT_END' + [Environment]::NewLine +
                        'STDERR_BEGIN' + [Environment]::NewLine + $tested.Stderr +
                        [Environment]::NewLine + 'STDERR_END'
                    $q30CaseReceipts.Add($caseReceipt)
                    $receipts.Add($caseReceipt)
                }
            }
            foreach ($q30Seed in $q30Seeds) {
                foreach ($q30Fault in $q30PlanBySeed[$q30Seed].FaultIds) {
                    $caseKey = $q30Seed + '|' + $q30Fault
                    if (-not $q30Seen.ContainsKey($caseKey)) {
                        throw ('Missing Q30 service case from complete census: ' + $caseKey)
                    }
                }
            }
            if ($q30Seen.Count -ne $q30ExpectedCount) {
                throw ('Q30 service census expected ' + $q30ExpectedCount +
                    ' attempted cases, found ' + $q30Seen.Count)
            }
            $q30PassedCount = 0
            foreach ($outcome in $q30Seen.Values) {
                if ($outcome -eq 'PASS') { $q30PassedCount++ }
            }
            $q30CensusPrefix = if ($q30CaseFailures.Count -eq 0) { 'PASS:' } else { 'FAIL:' }
            $q30FailedText = if ($q30CaseFailures.Count -eq 0) { 'none' } else {
                [String]::Join(',', $q30CaseFailures)
            }
            $q30Census = $q30CensusPrefix + ' Q30 service flow census attempted=' +
                $q30ExpectedCount + ' passed=' +
                $q30PassedCount + ' failed=' + $q30CaseFailures.Count +
                ' failedCases=' + $q30FailedText + ' seeds=' +
                [String]::Join(',', $q30Seeds) + ' faults=5 ' +
                'order=RELAY_<channel>_COIL_OPEN,DREV_OPEN,REN_OPEN,SENSOR_A_OPEN,DRIVE_A_OPEN ' +
                'childBudgetMs=60000'
            Write-Host $q30Census
            $q30CaseReceipts.Add($q30Census)
            $receipts.Add($q30Census)
            $outputs[$testClass] = [String]::Join([Environment]::NewLine,
                $q30CaseReceipts)
            if ($ReceiptOutputPath -and $q30CaseFailures.Count -gt 0) {
                [IO.File]::WriteAllText([IO.Path]::GetFullPath($ReceiptOutputPath),
                    [String]::Join([Environment]::NewLine, $receipts),
                    (New-Object Text.UTF8Encoding($false)))
            }
            if ($q30CaseFailures.Count -gt 0) {
                throw ('Q30 service census failed cases: ' +
                    [String]::Join(',', $q30CaseFailures))
            }
            continue
        }
        if ($testClass -eq 'A03IdentityContractTest') {
            $testArguments += (Join-Path $taskRoot 'parity')
        }
        # The NEW P07 54-row structural comparison has its own explicit ten-minute budget.
        # Existing suites retain their original one-minute bounds.
        $testBudget = if ($testClass -eq 'P07LayerCorpus') { 600000 } elseif ($testClass -in @('P09EnvelopeCorpus','QuickPlayPhysicalMatrixContractTest')) { 180000 } elseif ($testClass -in @('QuickPlayGateCorpus','QuickPlayGateHoldoutCorpus')) { 600000 } else { 60000 }
        $tested = Invoke-VerifierBoundedProcess $java $testArguments $testBudget
        Write-Host $tested.Stdout
        if ($tested.Stderr) { Write-Host $tested.Stderr }
        $receipts.Add($tested.Stdout)
        $outputs[$testClass] = $tested.Stdout
        if (-not $tested.TerminationProven -or $tested.ExitCode -ne 0 -or
                $tested.Stdout -notmatch ('(?m)^PASS: ' + [regex]::Escape($definition.Marker))) {
            throw ('Current contract did not provide a qualified result: ' + $testClass +
                ', exit ' + $tested.ExitCode)
        }
    }
    if ($Suite.Count -gt 0) {
        if ($ReceiptOutputPath) {
            [IO.File]::WriteAllText([IO.Path]::GetFullPath($ReceiptOutputPath),
                [String]::Join([Environment]::NewLine, $receipts),
                (New-Object Text.UTF8Encoding($false)))
        }
        Write-Host ('PASS: focused current contracts; ' + $Suite.Count + ' Java suites. Full matrix and independent oracles NOT RUN.')
    } else {
    $python = (Get-Command $PythonExe -ErrorAction Stop).Source
    if (-not $python -or -not (Test-Path -LiteralPath $python -PathType Leaf)) {
        throw 'Select an available Python executable with -PythonExe.'
    }
    $valueMatch = [regex]::Match($outputs['Task49ValueSynthesisContractTest'],
        '(?m)^CURRENT_VALUE_RECEIPT_BEGIN\r?\n([\s\S]*?)^CURRENT_VALUE_RECEIPT_END\r?$')
    $seedMatch = [regex]::Match($outputs['ChallengeDescriptorContractTest'],
        '(?m)^TASK46_PARITY_BEGIN\r?\n([\s\S]*?)^TASK46_PARITY_END\r?$')
    if (-not $valueMatch.Success -or -not $seedMatch.Success) {
        throw 'A current seed/value corpus is missing its complete frame.'
    }
    $valuePath = Join-Path $taskRoot 'current-values.txt'
    [IO.File]::WriteAllText($valuePath, $valueMatch.Groups[1].Value,
        (New-Object Text.UTF8Encoding($false)))
    $rolesPath = Join-Path $taskRoot 'a05-roles.txt'
    [IO.File]::WriteAllText($rolesPath, $outputs['A05RoleContractTest'],
        (New-Object Text.UTF8Encoding($false)))
    $providerMatch = [regex]::Match($outputs['A11ProviderConformanceTest'],
        '(?m)^A11_PROVIDER_REPORT (.+)\r?$')
    if (-not $providerMatch.Success) { throw 'Missing actual A11 declaration report.' }
    $providerPath = Join-Path $taskRoot 'a11-providers.json'
    [IO.File]::WriteAllText($providerPath, $providerMatch.Groups[1].Value.Trim(),
        (New-Object Text.UTF8Encoding($false)))
    $oracles = @(
        @{ File = 'task46_seed_reference.py'; Arguments = @(); Marker = 'Task46 independent seed oracle ' },
        @{ File = 'a11_provider_contract.py'; Arguments = @('--report', $providerPath); Marker = 'A11 provider boundaries/report ' },
        @{ File = 'a09_diagnostic_contract.py'; Arguments = @(); Marker = 'A09 independent diagnostic contracts ' },
        @{ File = 'task49_value_synthesis_reference.py'; Arguments = @($valuePath, $rolesPath); Marker = 'current value synthesis oracle ' })
    foreach ($oracle in $oracles) {
        $oracleArguments = @((Join-Path $repositoryRoot ('tests/contracts/' + $oracle.File))) + $oracle.Arguments
        $checked = Invoke-VerifierBoundedProcess $python $oracleArguments 60000
        Write-Host $checked.Stdout
        if ($checked.Stderr) { Write-Host $checked.Stderr }
        if (-not $checked.TerminationProven -or $checked.ExitCode -ne 0 -or
                $checked.Stdout -notmatch ('(?m)^PASS: ' + [regex]::Escape($oracle.Marker))) {
            throw ('Independent current oracle failed: ' + $oracle.File + ', exit ' + $checked.ExitCode)
        }
        $receipts.Add($checked.Stdout)
    }
    $powershell = Join-Path $env:SystemRoot 'System32/WindowsPowerShell/v1.0/powershell.exe'
    $protocol = Invoke-VerifierBoundedProcess $powershell @('-NoProfile', '-ExecutionPolicy',
        'Bypass', '-File', (Join-Path $repositoryRoot 'tests/contracts/a04-report-contract.ps1')) 60000
    Write-Host $protocol.Stdout
    if ($protocol.Stderr) { Write-Host $protocol.Stderr }
    if (-not $protocol.TerminationProven -or $protocol.ExitCode -ne 0 -or
            $protocol.Stdout -notmatch '(?m)^PASS: A04 report contracts assertions=') {
        throw ('Current browser report protocol failed, exit ' + $protocol.ExitCode)
    }
    $receipts.Add($protocol.Stdout)
    $listenerContract = Invoke-VerifierBoundedProcess $powershell @('-NoProfile', '-ExecutionPolicy',
        'Bypass', '-File', (Join-Path $repositoryRoot 'tests/contracts/preview-listener-identity.ps1')) 60000
    Write-Host $listenerContract.Stdout
    if ($listenerContract.Stderr) { Write-Host $listenerContract.Stderr }
    if (-not $listenerContract.TerminationProven -or $listenerContract.ExitCode -ne 0 -or
            $listenerContract.Stdout -notmatch '(?m)^PASS: preview listener identity contracts assertions=') {
        throw ('Preview listener identity contract failed, exit ' + $listenerContract.ExitCode)
    }
    $receipts.Add($listenerContract.Stdout)
    if ($ReceiptOutputPath) {
        foreach ($parityName in @('vectors', 'manifest-resistive', 'manifest-controlled', 'manifest-controlled-alt')) {
            $paritySource = Join-Path $taskRoot ('parity.' + $parityName + '.txt')
            if (-not (Test-Path -LiteralPath $paritySource -PathType Leaf)) {
                throw ('Missing exact JVM parity artifact: ' + $parityName)
            }
            [IO.File]::WriteAllBytes([IO.Path]::GetFullPath(
                    $ReceiptOutputPath + '.' + $parityName + '.txt'),
                [IO.File]::ReadAllBytes($paritySource))
        }
        [IO.File]::WriteAllText([IO.Path]::GetFullPath($ReceiptOutputPath + '.seeds.txt'),
            $seedMatch.Groups[1].Value, (New-Object Text.UTF8Encoding($false)))
        [IO.File]::WriteAllText([IO.Path]::GetFullPath($ReceiptOutputPath + '.roles.txt'),
            $outputs['A05RoleContractTest'], (New-Object Text.UTF8Encoding($false)))
        [IO.File]::WriteAllText([IO.Path]::GetFullPath($ReceiptOutputPath + '.values.txt'),
            $valueMatch.Groups[1].Value, (New-Object Text.UTF8Encoding($false)))
        [IO.File]::WriteAllText([IO.Path]::GetFullPath($ReceiptOutputPath),
            [String]::Join([Environment]::NewLine, $receipts),
            (New-Object Text.UTF8Encoding($false)))
    }
    Write-Host ('PASS: current contracts; ' + $testDefinitions.Count + ' Java suites, independent seed/value/role oracles and report protocol.')
    }
    $resultCode = 0
} catch {
    Write-Host ('CURRENT_CONTRACT_FAILURE: ' + $_.Exception.Message)
    $resultCode = 2
} finally {
    if ($taskRoot -and (Test-Path -LiteralPath $taskRoot)) {
        try {
            Remove-VerifierOwnedTree ([IO.Path]::GetFullPath([IO.Path]::GetTempPath())) $taskRoot
            Write-Host 'CLEANUP: current JVM contract classes and task-owned scratch removed.'
        } catch {
            Write-Host ('CURRENT_CONTRACT_CLEANUP: ' + $_.Exception.Message)
            $resultCode = 2
        }
    }
}
exit $resultCode
