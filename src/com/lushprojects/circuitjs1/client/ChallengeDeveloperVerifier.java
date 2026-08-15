package com.lushprojects.circuitjs1.client;

class ChallengeDeveloperVerifier {
    static void verify(CirSim sim) {
        GeneratedChallengeController challenge = sim.getGeneratedChallengeController();
        GeneratedBoardInstance instance = sim.getGeneratedBoardInstance();
        require(challenge != null && challenge.isReady(), "Challenge did not become ready");
        GeneratedFaultController faults = challenge.getFaultController();
        BoardModificationController modifications = sim.getBoardModificationController();
        require(faults.isApplied(), "Challenge fault was not applied");
        require(modifications.getComponentState("R1") == ComponentPhysicalState.INSTALLED,
            "Faulted R1 is not physically installed");
        verifyFaultedPowered(sim, instance);
        sim.setBoardPowerState(BoardPowerState.UNPOWERED);
        sim.updateCircuit();
        verifyFaultedUnpowered(sim, instance);
        verifyPhysicalPersistence(sim, instance, modifications, faults);
        verifyDeveloperClearAndReapply(sim, instance, faults);
        sim.setCircuitTitle("Challenge verification passed");
    }

    private static void verifyFaultedPowered(CirSim sim, GeneratedBoardInstance instance) {
        require(sim.getBoardPowerController().getState() == BoardPowerState.POWERED,
            "Faulted challenge is not powered");
        CircuitPostProbeTarget vin = getProbe(sim, instance, "J1.1");
        CircuitPostProbeTarget ground = getProbe(sim, instance, "J1.2");
        double expected = instance.getPhysicalSpecifications().getPowerInputNameplate("VIN_INPUT")
            .getNominalVoltage();
        requireApproximately(expected, sim.instrumentController
            .getDcVoltageDifferenceForDeveloperVerification(vin, ground), .1,
            "Faulted VIN differs from nameplate");
        LedIndicatorFaultValidator.verifyOpenResistor(instance,
            sim.getBoardModificationController(), BoardPowerState.POWERED);
    }

    private static void verifyFaultedUnpowered(CirSim sim, GeneratedBoardInstance instance) {
        CircuitPostProbeTarget r11 = getProbe(sim, instance, "R1.1");
        CircuitPostProbeTarget r12 = getProbe(sim, instance, "R1.2");
        sim.instrumentController.setResistanceProbesForDeveloperVerification(r11, r12);
        require("OL".equals(sim.instrumentController.getReadingForDeveloperVerification()),
            "Faulted R1 PCB pads did not measure OL");
        GeneratedComponentConnectionBindings connections = instance.getConnectionBindings();
        CircuitPostProbeTarget componentLead1 = getProbe(sim,
            connections.get("R1", "R1.1").getComponentEndpoint());
        CircuitPostProbeTarget componentLead2 = getProbe(sim,
            connections.get("R1", "R1.2").getComponentEndpoint());
        sim.instrumentController.setResistanceProbesForDeveloperVerification(componentLead1,
            componentLead2);
        require("OL".equals(sim.instrumentController.getReadingForDeveloperVerification()),
            "Faulted R1 component leads did not measure OL");
        sim.instrumentController.exitInstrumentModeForDeveloperVerification();
    }

    private static void verifyPhysicalPersistence(CirSim sim, GeneratedBoardInstance instance,
            BoardModificationController modifications, GeneratedFaultController faults) {
        modifications.liftLead("R1", "R1.1");
        require(modifications.getComponentState("R1") == ComponentPhysicalState.LEAD_LIFTED &&
            faults.isApplied(), "Lead lift cleared the internal fault");
        modifications.removeComponent("R1");
        require(modifications.getComponentState("R1") == ComponentPhysicalState.REMOVED &&
            faults.isApplied(), "Removal cleared the internal fault");
        CircuitPostProbeTarget componentLead1 = getProbe(sim,
            instance.getConnectionBindings().get("R1", "R1.1").getComponentEndpoint());
        CircuitPostProbeTarget componentLead2 = getProbe(sim,
            instance.getConnectionBindings().get("R1", "R1.2").getComponentEndpoint());
        sim.instrumentController.setResistanceProbesForDeveloperVerification(componentLead1,
            componentLead2);
        require("OL".equals(sim.instrumentController.getReadingForDeveloperVerification()),
            "Removed failed R1 did not measure OL in tray");
        modifications.restoreComponent("R1");
        require(modifications.getComponentState("R1") == ComponentPhysicalState.INSTALLED &&
            faults.isApplied(), "Restoration cleared the internal fault");
    }

    private static void verifyDeveloperClearAndReapply(CirSim sim, GeneratedBoardInstance instance,
            GeneratedFaultController faults) {
        require(faults.clearForDeveloperVerification(), "Developer fault clear was ignored");
        sim.setBoardPowerState(BoardPowerState.POWERED);
        sim.analyzeCircuit();
        sim.runCircuit(true);
        sim.runCircuit(true);
        instance.getFamilyValidator().verify(instance, BoardPowerState.POWERED);
        require(instance.getOperationalStates().isIlluminated("LED1"),
            "Developer-cleared LED did not illuminate");
        require(faults.apply(), "Developer fault reapply was ignored");
        sim.analyzeCircuit();
        sim.runCircuit(true);
        sim.runCircuit(true);
        LedIndicatorFaultValidator.verifyOpenResistor(instance,
            sim.getBoardModificationController(), BoardPowerState.POWERED);
    }

    private static CircuitPostProbeTarget getProbe(CirSim sim, GeneratedBoardInstance instance,
            String padId) {
        return getProbe(sim, instance.getSimulationBindings().getEndpoint(padId));
    }

    private static CircuitPostProbeTarget getProbe(CirSim sim, CircuitMeasurementEndpoint endpoint) {
        CircuitPostMeasurementEndpoint post = (CircuitPostMeasurementEndpoint) endpoint;
        return new CircuitPostProbeTarget(sim, post.getElement(), post.getPostIndex());
    }

    private static void requireApproximately(double expected, double actual, double tolerance,
            String message) {
        require(Math.abs(expected - actual) <= tolerance, message + ": " + actual);
    }

    private static void require(boolean condition, String message) {
        if (!condition)
            throw new IllegalStateException(message);
    }
}
