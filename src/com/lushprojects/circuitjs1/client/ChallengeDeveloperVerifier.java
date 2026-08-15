package com.lushprojects.circuitjs1.client;

import java.util.Vector;

class ChallengeDeveloperVerifier {
    static void verify(CirSim sim) {
        GeneratedChallengeController challenge = sim.getGeneratedChallengeController();
        GeneratedBoardInstance instance = sim.getGeneratedBoardInstance();
        require(challenge != null && challenge.isReady(), "Challenge did not become ready");
        verifyLifecycleEvidence(challenge);
        verifyDeterministicMetadata(instance, challenge.getDefinition());
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
        sim.getGeneratedChallengeController().getDefinition().getFaultValidator().verify(instance,
            sim.getBoardModificationController(), BoardPowerState.POWERED);
    }

    private static void verifyFaultedUnpowered(CirSim sim, GeneratedBoardInstance instance) {
        CircuitPostProbeTarget r11 = getProbe(sim, instance, "R1.1");
        CircuitPostProbeTarget r12 = getProbe(sim, instance, "R1.2");
        sim.instrumentController.setResistanceProbesForDeveloperVerification(r11, r12);
        require("OL".equals(sim.instrumentController.getReadingForDeveloperVerification()),
            "Faulted R1 PCB pads did not measure OL: " +
            sim.getLastResistanceMeasurementDiagnosticsForDeveloperVerification());
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
        verifyLedPolarity(sim, instance);
    }

    private static void verifyLedPolarity(CirSim sim, GeneratedBoardInstance instance) {
        CircuitPostProbeTarget anode = getProbe(sim, instance, "LED1.A");
        CircuitPostProbeTarget cathode = getProbe(sim, instance, "LED1.K");
        CircuitPostProbeTarget vin = getProbe(sim, instance, "J1.1");
        CircuitPostProbeTarget r11 = getProbe(sim, instance, "R1.1");
        Vector<CircuitElm> elements = new Vector<CircuitElm>(sim.elmList);
        String export = sim.dumpCircuit();
        int undo = sim.undoStack.size();
        int redo = sim.redoStack.size();
        boolean unsaved = sim.unsavedChanges;
        sim.instrumentController.setResistanceProbesForDeveloperVerification(anode, cathode);
        double forwardResistance = sim.instrumentController.getLatestResistanceReadingForDeveloperVerification();
        require(!Double.isNaN(forwardResistance) && !Double.isInfinite(forwardResistance) &&
            forwardResistance < 10000000, "LED forward OHM was not finite: " + forwardResistance);
		verifyNeutralResistanceReference(sim, "forward LED OHM");
        verifyMeasurementRestoration(sim, instance, elements, export, undo, redo, unsaved,
            "forward LED OHM");
        sim.instrumentController.setResistanceProbesForDeveloperVerification(cathode, anode);
        require("OL".equals(sim.instrumentController.getReadingForDeveloperVerification()),
            "LED reverse OHM was not OL");
		verifyNeutralResistanceReference(sim, "reverse LED OHM");
        verifyMeasurementRestoration(sim, instance, elements, export, undo, redo, unsaved,
            "reverse LED OHM");
        sim.instrumentController.setContinuityProbesForDeveloperVerification(anode, cathode);
        require(!Double.isNaN(sim.instrumentController.getLatestResistanceReadingForDeveloperVerification()) &&
            !sim.instrumentController.isContinuityDetectedForDeveloperVerification() &&
            !sim.instrumentController.isContinuityIndicatorVisibleForDeveloperVerification() &&
            !sim.instrumentController.isContinuityFeedbackRequestedForDeveloperVerification(),
            "LED forward CONT produced false continuity");
        verifyMeasurementRestoration(sim, instance, elements, export, undo, redo, unsaved,
            "forward LED CONT");
        sim.instrumentController.setContinuityProbesForDeveloperVerification(cathode, anode);
        require("OL".equals(sim.instrumentController.getReadingForDeveloperVerification()) &&
            !sim.instrumentController.isContinuityDetectedForDeveloperVerification(),
            "LED reverse CONT was not OL without continuity");
		verifyNeutralResistanceReference(sim, "reverse LED CONT");
        verifyMeasurementRestoration(sim, instance, elements, export, undo, redo, unsaved,
            "reverse LED CONT");
        sim.instrumentController.setDiodeProbesForDeveloperVerification(anode, cathode);
        require(!"OL".equals(sim.instrumentController.getReadingForDeveloperVerification()),
            "LED forward diode reading was OL");
        sim.instrumentController.setDiodeProbesForDeveloperVerification(cathode, anode);
        require("OL".equals(sim.instrumentController.getReadingForDeveloperVerification()),
            "LED reverse diode reading was not OL");
        sim.instrumentController.setContinuityProbesForDeveloperVerification(vin, r11);
        require(sim.instrumentController.isContinuityDetectedForDeveloperVerification(),
            "Same-net continuity did not activate");
        sim.instrumentController.exitInstrumentModeForDeveloperVerification();
    }

    private static void verifyMeasurementRestoration(CirSim sim, GeneratedBoardInstance instance,
            Vector<CircuitElm> elements, String export, int undo, int redo, boolean unsaved,
            String mode) {
        require(!sim.activeMeasurementOverlay && sim.elmList.equals(elements) &&
            export.equals(sim.dumpCircuit()) && undo == sim.undoStack.size() &&
            redo == sim.redoStack.size() && unsaved == sim.unsavedChanges &&
            sim.getBoardPowerController().isElectricallyUnpowered() &&
            instance.getFaultBinding().isApplied(), "Measurement did not restore " + mode);
    }

    private static void verifyNeutralResistanceReference(CirSim sim, String mode) {
        require(sim.hasElectricallyNeutralResistanceReferenceForDeveloperVerification(),
            "Resistance reference was not neutral during " + mode + ": " +
            sim.getLastResistanceMeasurementDiagnosticsForDeveloperVerification());
    }

    private static void verifyLifecycleEvidence(GeneratedChallengeController challenge) {
        GeneratedChallengeLifecycleEvidence evidence = challenge.getLifecycleEvidence();
        require(evidence.healthyGenerationInstalled && evidence.healthyGraphAnalyzedAfterTimeAdvance &&
            evidence.healthyFamilyValidated && evidence.selectedFaultApplied &&
            evidence.faultedGraphAnalyzedAfterTimeAdvance && evidence.selectedFaultValidated &&
            evidence.readyAfterValidation, "Challenge lifecycle evidence is incomplete");
    }

    private static void verifyDeterministicMetadata(GeneratedBoardInstance instance,
            GeneratedChallengeDefinition definition) {
        require("LED_INDICATOR_NO_LIGHT".equals(definition.getId()) &&
            "INDICATOR_DOES_NOT_LIGHT".equals(definition.getComplaintId()) &&
            "Indicator does not light.".equals(definition.getComplaintText()) &&
            definition.getFault() == instance.getFaultBinding().getFault(),
            "Selected challenge metadata disagrees with binding");
        verifySeed(0, 5, 330);
        verifySeed(2, 9, 680);
        verifySeed(3, 12, 1000);
    }

    private static void verifySeed(long seed, double voltage, double resistance) {
        GeneratedBoardInstance variant = new LedIndicatorGenerator().generate(seed);
        GeneratedChallengeDefinition definition = variant.getChallengeDefinition();
        requireApproximately(voltage, variant.getPhysicalSpecifications()
            .getPowerInputNameplate("VIN_INPUT").getNominalVoltage(), .001,
            "Unexpected deterministic VIN for seed " + seed);
        requireApproximately(resistance, variant.getPhysicalSpecifications()
            .getResistorNameplate("R1").getNominalResistanceOhms(), .001,
            "Unexpected deterministic R1 for seed " + seed);
        require("LED_R1_OPEN".equals(definition.getFault().getId()) &&
            "R1".equals(definition.getFault().getTargetComponentId()) &&
            definition.getSelectionSeed() == seed, "Unexpected deterministic challenge metadata");
    }

    private static void verifyPhysicalPersistence(CirSim sim, GeneratedBoardInstance instance,
            BoardModificationController modifications, GeneratedFaultController faults) {
		verifyFailedOriginalLiftedLeadVoltage(sim, instance, modifications, faults);
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
        sim.instrumentController.exitInstrumentModeForDeveloperVerification();
    }

    private static void verifyFailedOriginalLiftedLeadVoltage(CirSim sim,
            GeneratedBoardInstance instance, BoardModificationController modifications,
            GeneratedFaultController faults) {
        GeneratedComponentConnectionBinding lead2 = instance.getConnectionBindings().get("R1", "R1.2");
        CircuitPostProbeTarget liftedLead2 = getProbe(sim, lead2.getComponentEndpoint());
        CircuitPostProbeTarget boardPad2 = getProbe(sim, instance, "R1.2");
        CircuitPostProbeTarget ground = getProbe(sim, instance, "J1.2");
        require(modifications.getComponentState("R1") == ComponentPhysicalState.INSTALLED &&
            faults.isApplied(), "Failed original was not installed before lifted-lead voltage check");
        require(modifications.liftLead("R1", "R1.2"), "Failed original lead 2 did not lift");
        require(!sim.elmList.contains(lead2.getConnectionElement()),
            "Lifted failed-original lead 2 remained attached to its PCB pad");
        sim.setBoardPowerState(BoardPowerState.POWERED);
        sim.analyzeCircuit();
        sim.runCircuit(true);
        requireApproximately(0, sim.instrumentController.getDcVoltageDifferenceForDeveloperVerification(
            liftedLead2, ground), .01,
            "Failed original lifted public lead 2 incorrectly measured VIN");
        requireApproximately(0, sim.instrumentController.getDcVoltageDifferenceForDeveloperVerification(
            boardPad2, ground), .01, "Failed-original R1.2 board pad was not isolated");
        require(Math.abs(((LEDElm) instance.getComponentBindings().getSingleElement("LED1")).getCurrent()) < .000001 &&
            !instance.getOperationalStates().isIlluminated("LED1"),
            "Lifted failed original allowed LED current");
        sim.setBoardPowerState(BoardPowerState.UNPOWERED);
        sim.instrumentController.setResistanceProbesForDeveloperVerification(
            getProbe(sim, instance.getConnectionBindings().get("R1", "R1.1").getComponentEndpoint()),
            liftedLead2);
        require("OL".equals(sim.instrumentController.getReadingForDeveloperVerification()),
            "Lifted failed original did not remain OL across public leads");
        require(modifications.reconnectLead("R1", "R1.2"),
            "Failed original lead 2 did not reconnect");
        sim.setBoardPowerState(BoardPowerState.POWERED);
        sim.analyzeCircuit();
        sim.runCircuit(true);
        instance.getChallengeDefinition().getFaultValidator().verify(instance, modifications,
            BoardPowerState.POWERED);
        require(faults.isApplied(), "Reconnecting failed original lead 2 bypassed its internal fault");
        sim.setBoardPowerState(BoardPowerState.UNPOWERED);
    }

    private static void verifyDeveloperClearAndReapply(CirSim sim, GeneratedBoardInstance instance,
            GeneratedFaultController faults) {
        GeneratedChallengeController challenge = sim.getGeneratedChallengeController();
        challenge.beginDeveloperVerificationScope();
        try {
            require(faults.clearForDeveloperVerification(), "Developer fault clear was ignored");
            sim.setBoardPowerState(BoardPowerState.POWERED);
            sim.analyzeCircuit();
            sim.runCircuit(true);
            sim.runCircuit(true);
            instance.getFamilyValidator().verify(instance, BoardPowerState.POWERED);
            require(instance.getOperationalStates().isIlluminated("LED1"),
                "Developer-cleared LED did not illuminate");
            require(!faults.clearForDeveloperVerification(), "Repeated developer clear was not idempotent");
            require(faults.apply(), "Developer fault reapply was ignored");
            require(!faults.apply(), "Repeated fault apply was not idempotent");
            sim.analyzeCircuit();
            sim.runCircuit(true);
            sim.runCircuit(true);
            challenge.getDefinition().getFaultValidator().verify(instance,
                sim.getBoardModificationController(), BoardPowerState.POWERED);
        } finally {
            challenge.endDeveloperVerificationScope();
        }
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
