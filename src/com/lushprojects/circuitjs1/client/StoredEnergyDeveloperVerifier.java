package com.lushprojects.circuitjs1.client;

/** Direct proof that a real, charged RC board remains safe to observe but not actively probe. */
final class StoredEnergyDeveloperVerifier {
    private StoredEnergyDeveloperVerifier() { }

    static void verify(CirSim sim) {
        GeneratedBoardInstance instance = sim.getGeneratedBoardInstance();
        require(instance != null && RcDelayGenerator.FAMILY_ID.equals(instance.getCircuitFamilyId()),
            "Stored-energy verifier requires RC board");
        ReplaceableCapacitorBoardCapability capability =
            ReplaceableCapacitorBoardCapability.require(instance);
        CapacitorSlotController slots = capability.getController();
        PhysicalCapacitorPart original = capability.getSlot().getInstalledPart();
        RcDelayTemporalBehavior temporal = (RcDelayTemporalBehavior) instance.getTemporalBehavior();
        require(slots != null && original != null && original.isFaulted() && temporal != null,
            "Stored-energy verifier requires the original fault-owning C1");

        CircuitPostMeasurementEndpoint output = endpoint(instance, "J2.1");
        CircuitPostMeasurementEndpoint ground = endpoint(instance, "J2.2");
        CircuitPostMeasurementEndpoint r1Vin = endpoint(instance, "R1.1");
        CircuitPostMeasurementEndpoint r1Output = endpoint(instance, "R1.2");
        ProbeTarget outputProbe = probe(sim, output);
        ProbeTarget groundProbe = probe(sim, ground);
        ProbeTarget r1VinProbe = probe(sim, r1Vin);
        ProbeTarget r1OutputProbe = probe(sim, r1Output);
        CircuitMeasurementAdapter adapter = new CircuitMeasurementAdapter(sim);

        require(sim.getActiveMeasurementReadiness(output, ground) ==
            ActiveMeasurementReadiness.POWER_OFF, "Powered RC board allowed an active meter");
        sim.instrumentController.setResistanceProbesForDeveloperVerification(outputProbe,
            groundProbe);
        require("POWER OFF".equals(sim.instrumentController.getReadingForDeveloperVerification()) &&
            !sim.activeMeasurementOverlay,
            "Powered RC board did not show the external-isolation meter state");
        sim.instrumentController.exitInstrumentModeForDeveloperVerification();

        String originalId = original.getId();
        sim.setBoardPowerState(BoardPowerState.UNPOWERED);
        require(slots.removeInstalledPart() && slots.installNewFromCatalog(
                CapacitorReplacementCatalog.CORRECT),
            "Stored-energy verifier could not install a real healthy C1");
        PhysicalCapacitorPart healthy = capability.getSlot().getInstalledPart();
        require(healthy != null && !healthy.isFaulted() && healthy != original,
            "Stored-energy verifier did not create an independent healthy C1");

        // Charge the real replacement through R1, then use the normal board
        // power seam to isolate it. This is solver time, not a scripted UI
        // curve, and leaves R2 as the only C1 discharge path.
        sim.setBoardPowerState(BoardPowerState.POWERED);
        temporal.advanceForDeveloperVerification(sim, .800);
        double chargedVoltage = voltage(output, ground);
        requireFinite(chargedVoltage, "Healthy RC output did not charge");
        require(chargedVoltage > temporal.getNominalSupplyForDeveloperVerification() * .30,
            "Healthy RC output did not reach a material stored voltage: " + chargedVoltage);
        sim.instrumentController.setDcVoltageProbesForDeveloperVerification(outputProbe, groundProbe);
        requireFinite(sim.instrumentController.getLatestDcVoltageForDeveloperVerification(),
            "Charged RC board did not visibly report DC voltage");

        sim.setBoardPowerState(BoardPowerState.UNPOWERED);
        temporal.advanceForDeveloperVerification(sim,
            RcDelayTemporalBehavior.PLAYER_RESELECT_SECONDS);
        double interactionResidual = voltage(output, ground);
        requireFinite(interactionResidual, "RC residual became non-finite after power-off");
        require(interactionResidual > chargedVoltage * .55 && interactionResidual > 1,
            "Natural R2 discharge was not visible through normal re-selection latency: charged=" +
            chargedVoltage + " residual=" + interactionResidual);
        sim.instrumentController.setDcVoltageProbesForDeveloperVerification(outputProbe, groundProbe);
        require(sim.instrumentController.getLatestDcVoltageForDeveloperVerification() > 1,
            "Isolated RC board did not visibly retain its material DC residual");
        verifyChargedMeasurementBlock(sim, adapter, output, ground, outputProbe, groundProbe,
            r1Vin, r1Output, r1VinProbe, r1OutputProbe);

        temporal.advanceNaturalDischargeForDeveloperVerification(sim);
        require(sim.getActiveMeasurementReadiness(output, ground) == ActiveMeasurementReadiness.READY,
            "RC natural discharge did not become ready for active measurement");
        verifyNoncanonicalR1Ready(sim, temporal, adapter, r1Vin, r1Output, r1VinProbe,
            r1OutputProbe);
        requireFinite(sim.measureResistance(output, ground),
            "Post-discharge output resistance did not operate");
        require(!sim.activeMeasurementOverlay && sim.isActiveMeasurementSolverRestoredForDeveloperVerification(),
            "Post-discharge meter overlay did not clean up");

        // A power-on and DC re-selection must show a real rising output, not
        // a precomputed final value or an identity-derived completion state.
        sim.setBoardPowerState(BoardPowerState.POWERED);
        temporal.advanceForDeveloperVerification(sim, .060);
        double risingEarly = voltage(output, ground);
        sim.instrumentController.setDcVoltageProbesForDeveloperVerification(outputProbe, groundProbe);
        temporal.advanceForDeveloperVerification(sim, .300);
        double risingLate = voltage(output, ground);
        requireFinite(risingEarly, "RC rising output did not produce an early solver sample");
        requireFinite(risingLate, "RC rising output did not produce a late solver sample");
        require(risingEarly > 0 && risingLate > risingEarly + .25,
            "RC power-on did not retain a player-visible rising output: early=" + risingEarly +
            " late=" + risingLate);

        sim.setBoardPowerState(BoardPowerState.UNPOWERED);
        require(slots.removeInstalledPart() && slots.install(originalId) &&
            capability.getSlot().getInstalledPart() == original && original.isFaulted(),
            "Stored-energy verifier did not restore the original fault-owning C1");
        sim.setBoardPowerState(BoardPowerState.POWERED);
        sim.instrumentController.exitInstrumentModeForDeveloperVerification();
        require(!sim.activeMeasurementOverlay && sim.getBoardModificationController().isFullyRestored(),
            "Stored-energy verification contaminated the restored board state");
        sim.setCircuitTitle("Stored-energy verification passed");
    }

    private static void verifyChargedMeasurementBlock(CirSim sim,
            CircuitMeasurementAdapter adapter, CircuitPostMeasurementEndpoint output,
            CircuitPostMeasurementEndpoint ground, ProbeTarget outputProbe, ProbeTarget groundProbe,
            CircuitPostMeasurementEndpoint r1Vin, CircuitPostMeasurementEndpoint r1Output,
            ProbeTarget r1VinProbe, ProbeTarget r1OutputProbe) {
        require(sim.getActiveMeasurementReadiness(output, ground) ==
                ActiveMeasurementReadiness.DISCHARGE,
            "Charged RC output was not classified as discharge-pending");
        sim.instrumentController.setResistanceProbesForDeveloperVerification(outputProbe,
            groundProbe);
        require("DISCHARGE".equals(sim.instrumentController.getReadingForDeveloperVerification()) &&
            !sim.activeMeasurementOverlay,
            "Charged RC resistance measurement was not visibly refused");
        sim.instrumentController.exitInstrumentModeForDeveloperVerification();
        sim.instrumentController.setContinuityProbesForDeveloperVerification(outputProbe,
            groundProbe);
        require("DISCHARGE".equals(sim.instrumentController.getReadingForDeveloperVerification()) &&
            !sim.activeMeasurementOverlay,
            "Charged RC continuity measurement was not visibly refused");
        sim.instrumentController.exitInstrumentModeForDeveloperVerification();
        sim.instrumentController.setDiodeProbesForDeveloperVerification(outputProbe, groundProbe);
        require("DISCHARGE".equals(sim.instrumentController.getReadingForDeveloperVerification()) &&
            !sim.activeMeasurementOverlay,
            "Charged RC diode measurement was not visibly refused");
        require(Double.isNaN(sim.measureResistance(output, ground)) &&
            sim.measureDiode(output, ground) == null && !sim.activeMeasurementOverlay,
            "Charged RC board installed an active meter overlay");
        verifyNoncanonicalR1Block(sim, adapter, r1Vin, r1Output, r1VinProbe, r1OutputProbe);
    }

    /** R1 pads deliberately use different backing posts than J1/J2. */
    private static void verifyNoncanonicalR1Block(CirSim sim, CircuitMeasurementAdapter adapter,
            CircuitPostMeasurementEndpoint r1Vin, CircuitPostMeasurementEndpoint r1Output,
            ProbeTarget r1VinProbe, ProbeTarget r1OutputProbe) {
        ActiveMeasurementReadiness readiness = sim.getActiveMeasurementReadiness(r1Vin, r1Output);
        require(readiness == ActiveMeasurementReadiness.DISCHARGE,
            "Charged noncanonical R1 pads were not classified as stored-energy relevant");
        require(adapter.getActiveMeasurementReadiness(r1VinProbe, r1OutputProbe) == readiness &&
            Double.isNaN(adapter.measureResistance(r1VinProbe, r1OutputProbe)) &&
            adapter.measureDiode(r1VinProbe, r1OutputProbe) == null && !sim.activeMeasurementOverlay,
            "Measurement adapter installed an overlay through charged R1 pads");
        sim.instrumentController.setResistanceProbesForDeveloperVerification(r1VinProbe,
            r1OutputProbe);
        require("DISCHARGE".equals(sim.instrumentController.getReadingForDeveloperVerification()) &&
            !sim.activeMeasurementOverlay,
            "Charged R1 resistance measurement was not visibly refused");
        sim.instrumentController.exitInstrumentModeForDeveloperVerification();
        sim.instrumentController.setContinuityProbesForDeveloperVerification(r1VinProbe,
            r1OutputProbe);
        require("DISCHARGE".equals(sim.instrumentController.getReadingForDeveloperVerification()) &&
            !sim.activeMeasurementOverlay,
            "Charged R1 continuity measurement was not visibly refused");
        sim.instrumentController.exitInstrumentModeForDeveloperVerification();
        sim.instrumentController.setDiodeProbesForDeveloperVerification(r1VinProbe,
            r1OutputProbe);
        require("DISCHARGE".equals(sim.instrumentController.getReadingForDeveloperVerification()) &&
            !sim.activeMeasurementOverlay,
            "Charged R1 diode measurement was not visibly refused");
        require(Double.isNaN(sim.measureResistance(r1Vin, r1Output)),
            "CirSim resistance bypassed charged R1 readiness");
        require(sim.measureDiode(r1Vin, r1Output) == null,
            "CirSim diode bypassed charged R1 readiness");
        require(!sim.activeMeasurementOverlay,
            "CirSim left an overlay through charged R1 pads");
        sim.instrumentController.exitInstrumentModeForDeveloperVerification();
    }

    private static void verifyNoncanonicalR1Ready(CirSim sim,
            RcDelayTemporalBehavior temporal, CircuitMeasurementAdapter adapter,
            CircuitPostMeasurementEndpoint r1Vin, CircuitPostMeasurementEndpoint r1Output,
            ProbeTarget r1VinProbe, ProbeTarget r1OutputProbe) {
        require(sim.getActiveMeasurementReadiness(r1Vin, r1Output) ==
            ActiveMeasurementReadiness.READY && adapter.isActiveMeasurementAllowed(r1VinProbe,
                r1OutputProbe), "Discharged R1 pads remained blocked");
        requireFinite(adapter.measureResistance(r1VinProbe, r1OutputProbe),
            "Measurement adapter did not operate through discharged R1 pads");
        require(!sim.activeMeasurementOverlay && sim.isActiveMeasurementSolverRestoredForDeveloperVerification(),
            "Measurement adapter resistance left an overlay through discharged R1 pads");
        restoreNaturalReadiness(sim, temporal, r1Vin, r1Output,
            "adapter resistance recharge did not naturally clear");
        require(adapter.isActiveMeasurementAllowed(r1VinProbe, r1OutputProbe),
            "Measurement adapter diode was no longer ready through discharged R1 pads");
        require(adapter.measureDiode(r1VinProbe, r1OutputProbe) != null,
            "Measurement adapter diode did not operate through discharged R1 pads");
        require(!sim.activeMeasurementOverlay,
            "Measurement adapter diode left an overlay through discharged R1 pads");
        restoreNaturalReadiness(sim, temporal, r1Vin, r1Output,
            "adapter diode recharge did not naturally clear");
        int resistanceCount = sim.instrumentController.getResistanceMeasurementCountForDeveloperVerification();
        sim.instrumentController.setResistanceProbesForDeveloperVerification(r1VinProbe,
            r1OutputProbe);
        require(sim.instrumentController.getResistanceMeasurementCountForDeveloperVerification() >
                resistanceCount, "Discharged R1 resistance measurement did not operate: before=" +
                resistanceCount + " after=" + sim.instrumentController
                .getResistanceMeasurementCountForDeveloperVerification() + " readiness=" +
                sim.getActiveMeasurementReadiness(r1Vin, r1Output));
        require(!sim.activeMeasurementOverlay,
            "Discharged R1 resistance measurement left an overlay installed");
        sim.instrumentController.exitInstrumentModeForDeveloperVerification();
        restoreNaturalReadiness(sim, temporal, r1Vin, r1Output,
            "UI resistance recharge did not naturally clear");
        int continuityCount = sim.instrumentController.getContinuityMeasurementCountForDeveloperVerification();
        sim.instrumentController.setContinuityProbesForDeveloperVerification(r1VinProbe,
            r1OutputProbe);
        require(sim.instrumentController.getContinuityMeasurementCountForDeveloperVerification() >
                continuityCount && !sim.activeMeasurementOverlay,
            "Discharged R1 continuity measurement did not operate");
        sim.instrumentController.exitInstrumentModeForDeveloperVerification();
        restoreNaturalReadiness(sim, temporal, r1Vin, r1Output,
            "UI continuity recharge did not naturally clear");
        int diodeCount = sim.instrumentController.getDiodeMeasurementCountForDeveloperVerification();
        sim.instrumentController.setDiodeProbesForDeveloperVerification(r1VinProbe,
            r1OutputProbe);
        require(sim.instrumentController.getDiodeMeasurementCountForDeveloperVerification() >
                diodeCount && !sim.activeMeasurementOverlay,
            "Discharged R1 diode measurement did not operate");
        sim.instrumentController.exitInstrumentModeForDeveloperVerification();
        restoreNaturalReadiness(sim, temporal, r1Vin, r1Output,
            "UI diode recharge did not naturally clear");
        requireFinite(sim.measureResistance(r1Vin, r1Output),
            "CirSim did not operate through discharged R1 pads");
        require(!sim.activeMeasurementOverlay && sim.isActiveMeasurementSolverRestoredForDeveloperVerification(),
            "CirSim resistance did not clean up through discharged R1 pads");
        restoreNaturalReadiness(sim, temporal, r1Vin, r1Output,
            "CirSim resistance recharge did not naturally clear");
        require(sim.measureDiode(r1Vin, r1Output) != null && !sim.activeMeasurementOverlay &&
            sim.isActiveMeasurementSolverRestoredForDeveloperVerification(),
            "CirSim diode did not clean up through discharged R1 pads");
        restoreNaturalReadiness(sim, temporal, r1Vin, r1Output,
            "CirSim diode recharge did not naturally clear");
    }

    private static void restoreNaturalReadiness(CirSim sim, RcDelayTemporalBehavior temporal,
            CircuitPostMeasurementEndpoint red, CircuitPostMeasurementEndpoint black,
            String message) {
        temporal.advanceNaturalDischargeForDeveloperVerification(sim);
        require(sim.getActiveMeasurementReadiness(red, black) == ActiveMeasurementReadiness.READY,
            message);
    }

    private static CircuitPostMeasurementEndpoint endpoint(GeneratedBoardInstance instance,
            String padId) {
        CircuitMeasurementEndpoint endpoint = instance.getSimulationBindings().getEndpoint(padId);
        if (!(endpoint instanceof CircuitPostMeasurementEndpoint))
            throw new IllegalStateException("RC pad is not CircuitJS-backed: " + padId);
        return (CircuitPostMeasurementEndpoint) endpoint;
    }

    private static ProbeTarget probe(CirSim sim, CircuitPostMeasurementEndpoint endpoint) {
        return new CircuitPostProbeTarget(sim, endpoint.getElement(), endpoint.getPostIndex());
    }

    private static double voltage(CircuitPostMeasurementEndpoint first,
            CircuitPostMeasurementEndpoint second) {
        return first.getElement().getPostVoltage(first.getPostIndex()) -
            second.getElement().getPostVoltage(second.getPostIndex());
    }

    private static void requireFinite(double value, String message) {
        require(!Double.isNaN(value) && !Double.isInfinite(value), message);
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
