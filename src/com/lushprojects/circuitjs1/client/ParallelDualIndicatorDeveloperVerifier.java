package com.lushprojects.circuitjs1.client;

import java.util.HashMap;
import java.util.Vector;

/** URL-gated electrical, topology, repair, and active-meter verification. */
class ParallelDualIndicatorDeveloperVerifier {
    static void verify(CirSim sim) {
        GeneratedBoardInstance instance = sim.getGeneratedBoardInstance();
        GeneratedChallengeController challenge = sim.getGeneratedChallengeController();
        require(instance != null && "PARALLEL_DUAL_INDICATOR".equals(instance.getCircuitFamilyId()),
            "Parallel verifier is not running on the parallel family");
        require(challenge != null && challenge.isReady(), "Parallel challenge did not become ready");
        WorkbenchCapabilityDeveloperVerifier.verifyRegisteredProviders(sim);
        verifyScenario(instance, challenge);
        verifyGeneratedMetadata();
        verifyLogicalTopology(instance);
        verifyPhysicalBindings(instance);
        verifySharedNodeIdentity(sim, instance);
        verifyHealthyAndFaultIsolation(sim, instance, challenge);
        verifyDcMeasurements(sim, instance);
        verifyParallelResistanceFixture(sim, instance);
        verifyRepair(sim, instance, challenge);
        sim.setCircuitTitle("Parallel verification passed");
    }

    private static void verifyGeneratedMetadata() {
        for (long seed : new long[] { 0, 2, 3 }) {
            GeneratedBoardInstance board = new ParallelDualIndicatorGenerator().generate(seed);
            require("DUAL_PARALLEL_BRANCHES".equals(board.getTopologyVariantId()),
                "Unexpected parallel topology variant for seed " + seed);
            String expectedFaultId = seed == 3 ? "PARALLEL_R1_INCORRECT_VALUE" :
                "PARALLEL_R1_OPEN";
            GeneratedFaultType expectedFaultType = seed == 3 ?
                GeneratedFaultType.RESISTOR_INCORRECT_VALUE : GeneratedFaultType.RESISTOR_OPEN;
            require(expectedFaultId.equals(board.getChallengeDefinition().getFault().getId()) &&
                expectedFaultType == board.getChallengeDefinition().getFault().getType() &&
                "R1".equals(board.getChallengeDefinition().getFault().getTargetComponentId()) &&
                board.getChallengeDefinition().getSelectionSeed() == seed,
                "Unexpected parallel fault metadata for seed " + seed);
            GeneratedChallengeDefinition repeat = new ParallelDualIndicatorGenerator().generate(seed)
                .getChallengeDefinition();
            require(expectedFaultId.equals(repeat.getFault().getId()) &&
                expectedFaultType == repeat.getFault().getType(),
                "Parallel fault selection was not reproducible for seed " + seed);
            double expectedR1 = seed == 0 ? 330 : seed == 2 ? 680 : 1000;
            double expectedR2 = seed == 0 ? 680 : seed == 2 ? 1500 : 2200;
            requireApproximately(expectedR1, StandardPhysicalDefinitionProviders.RESISTOR.require(
                board.getPhysicalSpecifications(), "R1").getNominalResistanceOhms(), .001,
                "Unexpected parallel R1 value for seed " + seed);
            requireApproximately(expectedR2, StandardPhysicalDefinitionProviders.RESISTOR.require(
                board.getPhysicalSpecifications(), "R2").getNominalResistanceOhms(), .001,
                "Unexpected parallel R2 value for seed " + seed);
        }
        require(new ParallelDualIndicatorGenerator().generate(0).getChallengeDefinition().getFault()
            .getType() != new ParallelDualIndicatorGenerator().generate(3).getChallengeDefinition()
            .getFault().getType(),
            "Parallel fault selection did not produce multiple deterministic fault types");
    }

    private static void verifyScenario(GeneratedBoardInstance instance,
            GeneratedChallengeController challenge) {
        GeneratedScenario<GeneratedObservedBehavior> scenario = challenge.getScenario();
        require(scenario != null && scenario.getObservedBehavior() ==
            GeneratedObservedBehavior.ASYMMETRIC_INDICATORS &&
            "INDICATORS_DO_NOT_MATCH".equals(scenario.getComplaintId()) &&
            "The two indicators do not behave the same.".equals(scenario.getComplaintText()),
            "Parallel scenario identity or complaint is incorrect");
        require(!scenario.getComplaintText().contains("R1") &&
            !scenario.getComplaintText().contains("D1") &&
            !scenario.getComplaintText().toLowerCase().contains("fault"),
            "Parallel complaint leaked diagnosis metadata");
        require(scenario.isCompatible(instance, null, BoardPowerState.POWERED),
            "Parallel complaint does not match solved asymmetric behavior");
        GeneratedScenario<GeneratedObservedBehavior> repeat =
            GeneratedScenarioLibrary.parallelIndicators().select(instance.getSeed(), instance,
                null, BoardPowerState.POWERED);
        require(scenario.getScenarioId().equals(repeat.getScenarioId()) &&
            scenario.getComplaintText().equals(repeat.getComplaintText()),
            "Parallel scenario selection was not reproducible");
    }

    private static void verifyLogicalTopology(GeneratedBoardInstance instance) {
        TroubleshootBoard board = instance.getBoard();
        require(board.getComponentIds().size() == 5 && board.getPadIds().size() == 10 &&
            board.getNetIds().size() == 4, "Parallel logical board dimensions are incorrect");
        require(board.getComponent("J1") != null && board.getComponent("R1") != null &&
            board.getComponent("LED1") != null && board.getComponent("R2") != null &&
            board.getComponent("LED2") != null, "Parallel logical components are incomplete");
        requirePads(board, "VIN", new String[] { "J1.1", "R1.1", "R2.1" });
        requirePads(board, "GND", new String[] { "J1.2", "LED1.K", "LED2.K" });
        requirePads(board, "BRANCH1_NODE", new String[] { "R1.2", "LED1.A" });
        requirePads(board, "BRANCH2_NODE", new String[] { "R2.2", "LED2.A" });
    }

    private static void requirePads(TroubleshootBoard board, String netId, String[] expected) {
        Vector<String> actual = board.getNet(netId).getPadIds();
        require(actual.size() == expected.length, "Unexpected pad count for " + netId);
        for (String padId : expected)
            require(actual.contains(padId), "Missing " + netId + " pad " + padId);
    }

    private static void verifyPhysicalBindings(GeneratedBoardInstance instance) {
        require(ParallelDualIndicatorGeneratedBoardValidator.resistor(instance, "R1") != null &&
            ParallelDualIndicatorGeneratedBoardValidator.resistor(instance, "R2") != null &&
            ParallelDualIndicatorGeneratedBoardValidator.led(instance, "LED1") != null &&
            ParallelDualIndicatorGeneratedBoardValidator.led(instance, "LED2") != null,
            "Parallel physical bindings are incomplete");
        Vector<CircuitElm> elements = instance.getSimulationElements();
        HashMap<CircuitElm, Boolean> seen = new HashMap<CircuitElm, Boolean>();
        for (CircuitElm element : elements)
            require(seen.put(element, Boolean.TRUE) == null,
                "Parallel generated simulation element identity is duplicated");
    }

    private static void verifySharedNodeIdentity(CirSim sim, GeneratedBoardInstance instance) {
        sim.analyzeCircuit();
        sim.runCircuit(true);
        int j11 = node(instance, "J1.1");
        int r11 = node(instance, "R1.1");
        int r21 = node(instance, "R2.1");
        int j12 = node(instance, "J1.2");
        int led1k = node(instance, "LED1.K");
        int led2k = node(instance, "LED2.K");
        require(j11 == r11 && j11 == r21, "VIN pads do not share one transient solver node");
        require(j12 == led1k && j12 == led2k, "GND pads do not share one transient solver node");
    }

    private static void verifyHealthyAndFaultIsolation(CirSim sim, GeneratedBoardInstance instance,
            GeneratedChallengeController challenge) {
        GeneratedFaultController faults = challenge.getFaultController();
        challenge.beginDeveloperVerificationScope();
        double healthyBranch2;
        try {
            require(faults.clearForDeveloperVerification(), "Parallel fault did not clear in developer scope");
            sim.setBoardPowerState(BoardPowerState.POWERED);
            settle(sim);
            instance.getBehaviorContract().verifyHealthy(instance, BoardPowerState.POWERED);
            ResistorElm r2 = ParallelDualIndicatorGeneratedBoardValidator.resistor(instance, "R2");
            healthyBranch2 = r2.getCurrent();
            require(instance.getOperationalStates().isIlluminated("LED1") &&
                instance.getOperationalStates().isIlluminated("LED2"),
                "Healthy parallel branches are not both illuminated");
            require(faults.apply(), "Parallel fault did not reapply");
            settle(sim);
            challenge.getDefinition().getBehaviorContract().verifyFaulted(instance,
                sim.getBoardModificationController(), BoardPowerState.POWERED);
            require(!challenge.getDefinition().getBehaviorContract().isFunctionallyRepaired(instance,
                sim.getBoardModificationController(), BoardPowerState.POWERED, false),
                "Faulted parallel board incorrectly satisfied functional completion");
            require(Math.abs(r2.getCurrent() - healthyBranch2) <= .00001,
                "Healthy parallel branch changed after R1 open fault");
        } finally {
            if (!faults.isApplied()) faults.apply();
            challenge.endDeveloperVerificationScope();
        }
        sim.setBoardPowerState(BoardPowerState.UNPOWERED);
        sim.updateCircuit();
    }

    private static void verifyDcMeasurements(CirSim sim, GeneratedBoardInstance instance) {
        sim.setBoardPowerState(BoardPowerState.POWERED);
        settle(sim);
        CircuitPostProbeTarget supply = getProbe(sim, instance, "J1.1");
        CircuitPostProbeTarget ground = getProbe(sim, instance, "J1.2");
        CircuitPostProbeTarget r2a = getProbe(sim, instance, "R2.1");
        CircuitPostProbeTarget r2b = getProbe(sim, instance, "R2.2");
        CircuitPostProbeTarget led2a = getProbe(sim, instance, "LED2.A");
        CircuitPostProbeTarget led2k = getProbe(sim, instance, "LED2.K");
        double configured = instance.getPhysicalSpecifications().getPowerInputNameplate("VIN_INPUT")
            .getNominalVoltage();
        requireApproximately(configured, sim.instrumentController
            .getDcVoltageDifferenceForDeveloperVerification(supply, ground), .1,
            "Solver-backed supply DC measurement is wrong");
        double resistorDrop = sim.instrumentController.getDcVoltageDifferenceForDeveloperVerification(
            r2a, r2b);
        double ledDrop = sim.instrumentController.getDcVoltageDifferenceForDeveloperVerification(
            led2a, led2k);
        requireApproximately(configured, resistorDrop + ledDrop, .02,
            "Solver-backed parallel branch DC measurements do not sum to supply");
        sim.setBoardPowerState(BoardPowerState.UNPOWERED);
        sim.updateCircuit();
    }

    private static void verifyParallelResistanceFixture(CirSim sim, GeneratedBoardInstance instance) {
        require(sim.getBoardPowerController().isElectricallyUnpowered(),
            "Parallel resistance fixture started with board power connected");
        ParallelResistanceMeasurementFixture fixture = new ParallelResistanceMeasurementFixture();
        fixture.install(sim);
        CircuitPostProbeTarget forward = new CircuitPostProbeTarget(sim, fixture.getOneKilohm(), 0);
        CircuitPostProbeTarget reverse = new CircuitPostProbeTarget(sim, fixture.getOneKilohm(), 1);
        try {
            settle(sim);
            measureResistance(sim, forward, reverse, 909.09, 2);
            measureResistance(sim, reverse, forward, 909.09, 2);
            fixture.removeTenKilohm(sim);
            settle(sim);
            measureResistance(sim, forward, reverse, 1000, 3);
            fixture.restoreTenKilohm(sim);
            fixture.removeOneKilohm(sim);
            settle(sim);
            CircuitPostProbeTarget tenForward = new CircuitPostProbeTarget(sim,
                fixture.getTenKilohm(), 0);
            CircuitPostProbeTarget tenReverse = new CircuitPostProbeTarget(sim,
                fixture.getTenKilohm(), 1);
            measureResistance(sim, tenForward, tenReverse, 10000, 20);
        } finally {
            fixture.remove(sim);
            sim.needAnalyze();
            settle(sim);
            sim.instrumentController.exitInstrumentModeForDeveloperVerification();
        }
    }

    private static void verifyRepair(CirSim sim, GeneratedBoardInstance instance,
            GeneratedChallengeController challenge) {
        sim.setBoardPowerState(BoardPowerState.UNPOWERED);
        sim.updateCircuit();
        ReplaceableComponentSlot slot = ReplaceableResistorBoardCapability.require(instance)
            .getSlot();
        require(sim.getResistorSlotController().removeInstalledPart(), "Parallel R1 did not remove");
        String catalogId = "R_CATALOG_" + (long)slot.getIntendedNameplate().getNominalResistanceOhms();
        require(sim.getResistorSlotController().installNewFromCatalog(catalogId),
            "Parallel R1 correct catalog replacement did not install");
        sim.setBoardPowerState(BoardPowerState.POWERED);
        settle(sim);
        require(challenge.getDefinition().getBehaviorContract().isFunctionallyRepaired(instance,
            sim.getBoardModificationController(), BoardPowerState.POWERED, false),
            "Parallel correct replacement did not pass functional repair validation");
        sim.verifyGeneratedBoard();
        require(challenge.isCompleted(),
            "Parallel correct replacement did not transition the controller to COMPLETED");
        require(instance.getOperationalStates().isIlluminated("LED1") &&
            instance.getOperationalStates().isIlluminated("LED2"),
            "Parallel repair did not restore both indicators");
    }

    private static void measureResistance(CirSim sim, CircuitPostProbeTarget red,
            CircuitPostProbeTarget black, double expected, double tolerance) {
        sim.instrumentController.setResistanceProbesForDeveloperVerification(red, black);
        requireApproximately(expected, sim.instrumentController
            .getLatestResistanceReadingForDeveloperVerification(), tolerance,
            "Parallel active resistance fixture mismatch (" +
            sim.getLastResistanceMeasurementDiagnosticsForDeveloperVerification() + ")");
    }

    private static int node(GeneratedBoardInstance instance, String padId) {
        CircuitPostMeasurementEndpoint endpoint = (CircuitPostMeasurementEndpoint)
            instance.getSimulationBindings().getEndpoint(padId);
        return endpoint.getElement().nodes[endpoint.getPostIndex()];
    }

    private static CircuitPostProbeTarget getProbe(CirSim sim, GeneratedBoardInstance instance,
            String padId) {
        CircuitPostMeasurementEndpoint endpoint = (CircuitPostMeasurementEndpoint)
            instance.getSimulationBindings().getEndpoint(padId);
        return new CircuitPostProbeTarget(sim, endpoint.getElement(), endpoint.getPostIndex());
    }

    private static void settle(CirSim sim) {
        sim.analyzeCircuit();
        sim.runCircuit(true);
        sim.runCircuit(true);
        sim.runCircuit(true);
    }

    private static void requireApproximately(double expected, double actual, double tolerance,
            String message) {
        require(!Double.isNaN(actual) && !Double.isInfinite(actual) &&
            Math.abs(expected - actual) <= tolerance, message + ": " + actual);
    }

    private static void require(boolean condition, String message) {
        if (!condition)
            throw new IllegalStateException(message);
    }
}
