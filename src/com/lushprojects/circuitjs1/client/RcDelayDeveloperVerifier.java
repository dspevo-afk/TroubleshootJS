package com.lushprojects.circuitjs1.client;

/** Focused direct proof for the deterministic RC family and replacement seam. */
final class RcDelayDeveloperVerifier {
    private RcDelayDeveloperVerifier() { }

    static void verify(CirSim sim) {
        GeneratedBoardInstance instance = sim.getGeneratedBoardInstance();
        GeneratedChallengeController challenge = sim.getGeneratedChallengeController();
        require(instance != null && challenge != null && challenge.isReady() &&
            RcDelayGenerator.FAMILY_ID.equals(instance.getCircuitFamilyId()),
            "RC verifier requires a ready RC challenge");
        ReplaceableCapacitorBoardCapability capability =
            ReplaceableCapacitorBoardCapability.require(instance);
        CapacitorSlotController slots = capability.getController();
        PhysicalCapacitorPart original = capability.getSlot().getInstalledPart();
        PhysicalCapacitorPart fixedC2 = (PhysicalCapacitorPart) instance
            .getPhysicalBoardRuntime().getInstalledPart("C2");
        require(slots != null && original != null && original.isOriginal() &&
            original.ownsGeneratedFault(instance.getFaultBinding()) && original.isFaulted(),
            "RC original C1 did not retain selected-fault ownership");
        require(fixedC2 != null, "RC fixed C2 physical part is missing");
        requirePhysicalNameplate(original);
        requirePhysicalNameplate(fixedC2);
        require(PhysicalPackages.RADIAL_ELECTROLYTIC_CAPACITOR.isEquivalentTo(
                original.getPackage()) && PhysicalPackages.RADIAL_CERAMIC_CAPACITOR
                .isEquivalentTo(fixedC2.getPackage()),
            "RC capacitor packages are not typed distinctly");
        PhysicalPartRenderDeveloperVerifier.verify(sim);
        RcDelayTemporalBehavior temporal = (RcDelayTemporalBehavior) instance.getTemporalBehavior();
        require(temporal != null && finite(temporal.getHealthyResidualVoltageForDeveloperVerification()) &&
            finite(temporal.getHealthyEarlyVoltageForDeveloperVerification()) &&
            finite(temporal.getHealthyLateVoltageForDeveloperVerification()) &&
            temporal.getHealthyEarlyVoltageForDeveloperVerification() <
                temporal.getHealthyLateVoltageForDeveloperVerification(),
            "RC healthy transient profile is not live or stable");
        double nominalSupply = temporal.getNominalSupplyForDeveloperVerification();
        double healthyResidual = temporal.getHealthyResidualVoltageForDeveloperVerification();
        double healthyEarly = temporal.getHealthyEarlyVoltageForDeveloperVerification();
        double healthyLate = temporal.getHealthyLateVoltageForDeveloperVerification();
        require(healthyResidual < ActiveMeasurementReadiness.RESIDUAL_VOLTAGE_THRESHOLD_VOLTS &&
            healthyEarly > nominalSupply * .10 && healthyEarly < healthyLate * .60 &&
            healthyLate > nominalSupply * .35 && healthyLate - healthyEarly >
                nominalSupply * .20,
            "RC healthy profile no longer has a visible solver-backed charge/discharge delay: " +
            "residual=" + healthyResidual + " early=" + healthyEarly + " late=" +
            healthyLate + " nominal=" + nominalSupply);
        require(finite(temporal.getResidualVoltageForDeveloperVerification()) &&
            finite(temporal.getEarlyVoltageForDeveloperVerification()) &&
            finite(temporal.getLateVoltageForDeveloperVerification()) &&
            (temporal.getObservedBehavior() == GeneratedObservedBehavior.RC_DELAY_TOO_FAST ||
                temporal.getObservedBehavior() == GeneratedObservedBehavior.RC_DELAY_STUCK_LOW),
            "RC faulted transient profile is not live or meaningful");
        if (instance.getFaultBinding().getFault().getType() ==
                GeneratedFaultType.CAPACITOR_SHORT) {
            CircuitElm r1 = instance.getComponentBindings().getSingleElement("R1");
            double r1Current = r1.getCurrent();
            require(finite(r1Current) && Math.abs(r1Current) < .010,
                "R1 did not limit the real C1 short current: " + r1Current);
        }

        boolean expectedOpen = instance.getFaultBinding().getFault().getType() ==
            GeneratedFaultType.CAPACITOR_OPEN;
        BoardModificationController modifications = sim.getBoardModificationController();
        sim.setBoardPowerState(BoardPowerState.UNPOWERED);
        sim.updateCircuit();
        require(modifications.liftLead("C1", "C1.+") &&
            !modifications.isComponentInstalled("C1"),
            "RC original positive lead did not lift");
        requireOriginalFaultInfrastructurePresent(sim, instance, original, "lifted");
        requireOriginalFaultResistance(sim, original, expectedOpen, "lifted");
        requireDetachedOriginalDoesNotBypassBoard(sim, instance, "lifted");
        require(modifications.reconnectLead("C1", "C1.+") &&
            modifications.isComponentInstalled("C1"),
            "RC original positive lead did not reconnect");
        require(modifications.liftLead("C1", "C1.-") &&
            !modifications.isComponentInstalled("C1"),
            "RC original negative lead did not lift");
        requireOriginalFaultInfrastructurePresent(sim, instance, original, "negative-lifted");
        requireOriginalFaultResistance(sim, original, expectedOpen, "negative-lifted");
        requireDetachedOriginalDoesNotBypassBoard(sim, instance, "negative-lifted");
        require(modifications.reconnectLead("C1", "C1.-") &&
            modifications.isComponentInstalled("C1"),
            "RC original negative lead did not reconnect");

        sim.setBoardPowerState(BoardPowerState.UNPOWERED);
        sim.updateCircuit();
        String originalId = original.getId();
        require(slots.removeInstalledPart() && !original.isInstalled(),
            "RC original removal did not preserve its physical identity");
        requireOriginalFaultInfrastructurePresent(sim, instance, original, "loose");
        requireOriginalFaultResistance(sim, original, expectedOpen, "loose");
        requireDetachedOriginalDoesNotBypassBoard(sim, instance, "loose");
        require(challenge.getRepairStatus() != GeneratedRepairStatus.CORRECTLY_RESTORED,
            "RC missing capacitor passed the temporal functional check");
        PhysicalPartRenderDeveloperVerifier.verify(sim);
        require(slots.install(originalId) && capability.getSlot().getInstalledPart() == original &&
            original.isFaulted(), "RC original removal/reinstall lost identity or fault ownership");
        sim.setBoardPowerState(BoardPowerState.POWERED);
        require(challenge.getRepairStatus() != GeneratedRepairStatus.CORRECTLY_RESTORED,
            "RC reinstalled original faulted capacitor passed the temporal functional check");

        sim.setBoardPowerState(BoardPowerState.UNPOWERED);
        sim.updateCircuit();
        require(slots.removeInstalledPart() && slots.installNewFromCatalog(
            CapacitorReplacementCatalog.WRONG_LOW), "RC low-value catalog installation failed");
        PhysicalCapacitorPart low = capability.getSlot().getInstalledPart();
        require(low.getSpecification() == capability.getCatalog().get(
            CapacitorReplacementCatalog.WRONG_LOW).getSpecification() && low != original &&
            !low.isFaulted(),
            "RC catalog acquisition did not retain its immutable specification or allocate identity");
        requirePhysicalNameplate(low);
        String lowId = low.getId();
        require(slots.removeInstalledPart() && slots.install(lowId) &&
            capability.getSlot().getInstalledPart() == low &&
            low.getSpecification() == capability.getCatalog().get(
                CapacitorReplacementCatalog.WRONG_LOW).getSpecification(),
            "RC catalog part removal/reinstall lost physical identity or specification");
        sim.setBoardPowerState(BoardPowerState.POWERED);
        require(challenge.getRepairStatus() != GeneratedRepairStatus.CORRECTLY_RESTORED,
            "RC low-value replacement passed without a temporal delay");

        sim.setBoardPowerState(BoardPowerState.UNPOWERED);
        sim.updateCircuit();
        require(slots.removeInstalledPart() && slots.installNewFromCatalog(
            CapacitorReplacementCatalog.WRONG_HIGH), "RC high-value catalog installation failed");
        sim.setBoardPowerState(BoardPowerState.POWERED);
        require(challenge.getRepairStatus() != GeneratedRepairStatus.CORRECTLY_RESTORED,
            "RC high-value replacement passed without reaching its delayed state");

        sim.setBoardPowerState(BoardPowerState.UNPOWERED);
        sim.updateCircuit();
        require(slots.removeInstalledPart() && slots.installNewFromCatalog(
            CapacitorReplacementCatalog.CORRECT), "RC correct catalog installation failed");
        sim.setBoardPowerState(BoardPowerState.POWERED);
        require(challenge.getRepairStatus() == GeneratedRepairStatus.CORRECTLY_RESTORED,
            "RC correct electrical replacement did not pass the real transient profile");
        require(!sim.activeMeasurementOverlay && sim.getBoardModificationController().isFullyRestored(),
            "RC verification contaminated a meter overlay or board modification state");
        sim.setCircuitTitle("RC delay verification passed");
    }

    private static boolean finite(double value) {
        return !Double.isNaN(value) && !Double.isInfinite(value);
    }

    private static void requirePhysicalNameplate(PhysicalCapacitorPart part) {
        require(part.getPlayerVisibleNameplate().getId().equals(part.getId()) &&
            !part.getPlayerVisibleNameplate().getId().equals(
                part.getSpecification().getSpecificationId()),
            "Capacitor nameplate was not materialized for physical identity: " + part.getId());
    }

    private static void requireOriginalFaultInfrastructurePresent(CirSim sim,
            GeneratedBoardInstance instance, PhysicalCapacitorPart original, String state) {
        for (CircuitElm element : instance.getFaultBinding().getPrivateSimulationElements())
            require(sim.containsElement(element), "Detached original lost private fault graph: " +
                state);
        CircuitPostMeasurementEndpoint positive = post(original.getPublicTerminal(0));
        CircuitPostMeasurementEndpoint negative = post(original.getPublicTerminal(1));
        require(sim.containsElement(positive.getElement()) && sim.containsElement(
            negative.getElement()), "Detached original lost real measurement endpoints: " + state);
    }

    private static void requireOriginalFaultResistance(CirSim sim,
            PhysicalCapacitorPart original, boolean expectedOpen, String state) {
        double resistance = sim.measureResistance(post(original.getPublicTerminal(0)),
            post(original.getPublicTerminal(1)));
        require(!sim.activeMeasurementOverlay,
            "Detached original measurement left an overlay installed: " + state);
        if (expectedOpen)
            require(!finite(resistance) || resistance >= ResistanceInstrumentMode.MAX_RESISTANCE,
                "Detached original open fault was not solver-measurable: " + state +
                " resistance=" + resistance);
        else
            require(finite(resistance) && resistance <=
                ResistanceInstrumentMode.CONTINUITY_THRESHOLD_OHMS,
                "Detached original short fault was not solver-measurable: " + state +
                " resistance=" + resistance);
    }

    private static void requireDetachedOriginalDoesNotBypassBoard(CirSim sim,
            GeneratedBoardInstance instance, String state) {
        CircuitPostMeasurementEndpoint vin = (CircuitPostMeasurementEndpoint) instance
            .getSimulationBindings().getEndpoint("J1.1");
        CircuitPostMeasurementEndpoint output = (CircuitPostMeasurementEndpoint) instance
            .getSimulationBindings().getEndpoint("J2.1");
        CircuitPostMeasurementEndpoint ground = (CircuitPostMeasurementEndpoint) instance
            .getSimulationBindings().getEndpoint("J2.2");
        sim.setBoardPowerState(BoardPowerState.POWERED);
        sim.advanceGeneratedTemporalProfile(.750);
        double inputVoltage = voltage(vin, ground);
        double outputVoltage = voltage(output, ground);
        require(finite(inputVoltage) && finite(outputVoltage) && inputVoltage > 0 &&
            outputVoltage > inputVoltage * .30 && outputVoltage < inputVoltage * .60,
            "Detached original left a private bypass connected to the board: " + state +
            " vin=" + inputVoltage + " output=" + outputVoltage);
        sim.setBoardPowerState(BoardPowerState.UNPOWERED);
        sim.advanceGeneratedTemporalProfile(.350);
    }

    private static CircuitPostMeasurementEndpoint post(CircuitMeasurementEndpoint endpoint) {
        if (!(endpoint instanceof CircuitPostMeasurementEndpoint))
            throw new IllegalStateException("RC physical terminal is not CircuitJS-backed");
        return (CircuitPostMeasurementEndpoint) endpoint;
    }

    private static double voltage(CircuitPostMeasurementEndpoint first,
            CircuitPostMeasurementEndpoint second) {
        return first.getElement().getPostVoltage(first.getPostIndex()) -
            second.getElement().getPostVoltage(second.getPostIndex());
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
