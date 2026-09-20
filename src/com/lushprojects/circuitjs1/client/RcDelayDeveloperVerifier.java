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
        setPower(sim, instance, BoardPowerState.UNPOWERED);
        sim.updateCircuit();
        require(readyModifications(sim, instance, modifications).liftLead("C1", "C1.+") &&
            !modifications.isComponentInstalled("C1"),
            "RC original positive lead did not lift");
        requireOriginalFaultInfrastructurePresent(sim, instance, original, "lifted");
        requireOriginalFaultResistance(sim, original, expectedOpen, "lifted");
        requireDetachedOriginalDoesNotBypassBoard(sim, instance, "lifted");
        require(readyModifications(sim, instance, modifications).reconnectLead("C1", "C1.+") &&
            modifications.isComponentInstalled("C1"),
            "RC original positive lead did not reconnect");
        require(readyModifications(sim, instance, modifications).liftLead("C1", "C1.-") &&
            !modifications.isComponentInstalled("C1"),
            "RC original negative lead did not lift");
        requireOriginalFaultInfrastructurePresent(sim, instance, original, "negative-lifted");
        requireOriginalFaultResistance(sim, original, expectedOpen, "negative-lifted");
        requireDetachedOriginalDoesNotBypassBoard(sim, instance, "negative-lifted");
        require(readyModifications(sim, instance, modifications).reconnectLead("C1", "C1.-") &&
            modifications.isComponentInstalled("C1"),
            "RC original negative lead did not reconnect");

        setPower(sim, instance, BoardPowerState.UNPOWERED);
        sim.updateCircuit();
        String originalId = original.getId();
        require(readySlots(sim, instance, slots).removeInstalledPart() && !original.isInstalled(),
            "RC original removal did not preserve its physical identity");
        requireOriginalFaultInfrastructurePresent(sim, instance, original, "loose");
        requireOriginalFaultResistance(sim, original, expectedOpen, "loose");
        requireDetachedOriginalDoesNotBypassBoard(sim, instance, "loose");
        require(challenge.getRepairStatus() != GeneratedRepairStatus.CORRECTLY_RESTORED,
            "RC missing capacitor passed the temporal functional check");
        PhysicalPartRenderDeveloperVerifier.verify(sim);
        require(readySlots(sim, instance, slots).install(originalId) && capability.getSlot().getInstalledPart() == original &&
            original.isFaulted(), "RC original removal/reinstall lost identity or fault ownership");
        setPower(sim, instance, BoardPowerState.POWERED);
        require(challenge.getRepairStatus() != GeneratedRepairStatus.CORRECTLY_RESTORED,
            "RC reinstalled original faulted capacitor passed the temporal functional check");

        setPower(sim, instance, BoardPowerState.UNPOWERED);
        sim.updateCircuit();
        require(readySlots(sim, instance, slots).removeInstalledPart() && readySlots(sim, instance, slots).installNewFromCatalog(
            CapacitorReplacementCatalog.WRONG_LOW), "RC low-value catalog installation failed");
        PhysicalCapacitorPart low = capability.getSlot().getInstalledPart();
        require(low.getSpecification() == capability.getCatalog().get(
            CapacitorReplacementCatalog.WRONG_LOW).getSpecification() && low != original &&
            !low.isFaulted(),
            "RC catalog acquisition did not retain its immutable specification or allocate identity");
        requirePhysicalNameplate(low);
        String lowId = low.getId();
        require(readySlots(sim, instance, slots).removeInstalledPart() && readySlots(sim, instance, slots).install(lowId) &&
            capability.getSlot().getInstalledPart() == low &&
            low.getSpecification() == capability.getCatalog().get(
                CapacitorReplacementCatalog.WRONG_LOW).getSpecification(),
            "RC catalog part removal/reinstall lost physical identity or specification");
        setPower(sim, instance, BoardPowerState.POWERED);
        require(challenge.getRepairStatus() != GeneratedRepairStatus.CORRECTLY_RESTORED,
            "RC low-value replacement passed without a temporal delay");

        setPower(sim, instance, BoardPowerState.UNPOWERED);
        sim.updateCircuit();
        require(readySlots(sim, instance, slots).removeInstalledPart() && readySlots(sim, instance, slots).installNewFromCatalog(
            CapacitorReplacementCatalog.WRONG_HIGH), "RC high-value catalog installation failed");
        setPower(sim, instance, BoardPowerState.POWERED);
        require(challenge.getRepairStatus() != GeneratedRepairStatus.CORRECTLY_RESTORED,
            "RC high-value replacement passed without reaching its delayed state");

        setPower(sim, instance, BoardPowerState.UNPOWERED);
        sim.updateCircuit();
        require(readySlots(sim, instance, slots).removeInstalledPart() && readySlots(sim, instance, slots).installNewFromCatalog(
            CapacitorReplacementCatalog.CORRECT), "RC correct catalog installation failed");
        setPower(sim, instance, BoardPowerState.POWERED);
        require(challenge.getRepairStatus() == GeneratedRepairStatus.CORRECTLY_RESTORED,
            "RC correct electrical replacement did not pass the real transient profile");
        verifyLiveDcDisplayDuringPowerTransition(sim);
        require(!sim.activeMeasurementOverlay && sim.getBoardModificationController().isFullyRestored(),
            "RC verification contaminated a meter overlay or board modification state");
        sim.setCircuitTitle("RC delay verification passed");
    }

    private static BoardModificationController readyModifications(CirSim sim,
            GeneratedBoardInstance owner, BoardModificationController modifications) {
        GeneratedRuntimeDeveloperSettlement.settle(sim, owner, "RC before lead mutation");
        require(sim.getBoardModificationController() == modifications, "RC mutation owner changed");
        return modifications;
    }

    private static CapacitorSlotController readySlots(CirSim sim,
            GeneratedBoardInstance owner, CapacitorSlotController slots) {
        GeneratedRuntimeDeveloperSettlement.settle(sim, owner, "RC before slot mutation");
        require(ReplaceableCapacitorBoardCapability.require(owner).getController() == slots,
            "RC capacitor owner changed");
        return slots;
    }

    private static void setPower(CirSim sim, GeneratedBoardInstance owner, BoardPowerState state) {
        GeneratedRuntimeDeveloperSettlement.settle(sim, owner, "RC before power request");
        sim.setBoardPowerState(state);
        GeneratedRuntimeDeveloperSettlement.settle(sim, owner, "RC after power request");
        require(sim.getBoardPowerController().getState() == state, "RC power request was not applied");
    }

    private static boolean finite(double value) {
        return !Double.isNaN(value) && !Double.isInfinite(value);
    }

    private static void verifyLiveDcDisplayDuringPowerTransition(CirSim sim) {
        GeneratedRuntimeDeveloperSettlement.settle(sim, "RC before live DC transition");
        require(sim.pcbWorkbenchController != null,
            "RC live DC verification has no PCB renderer");
        PcbWorkbenchRenderer renderer = sim.pcbWorkbenchController.getRenderer();
        ProbeTarget output = probeForPad(sim, renderer, "J2.1");
        ProbeTarget ground = probeForPad(sim, renderer, "J2.2");
        sim.instrumentController.setDcVoltageProbesForDeveloperVerification(output, ground);
        double poweredVoltage = sim.instrumentController.getLatestDcVoltageForDeveloperVerification();
        require(finite(poweredVoltage) &&
                !"--- V".equals(sim.instrumentController.getReadingForDeveloperVerification()),
            "RC live DC path did not produce an initial powered display");
        sim.setBoardPowerState(BoardPowerState.UNPOWERED);
        // Give the actual C1 graph enough accepted solver time to discharge;
        // the retained probes do not move during this setup.
        sim.advanceGeneratedTemporalProfile(.750);
        sim.updateCircuit();
        double unpoweredVoltage = sim.instrumentController.getLatestDcVoltageForDeveloperVerification();
        require(finite(unpoweredVoltage) && Math.abs(unpoweredVoltage - poweredVoltage) > .001,
            "RC live DC path did not show a real voltage change on power-off");
        sim.setBoardPowerState(BoardPowerState.POWERED);
        sim.updateCircuit();
        double firstPoweredVoltage = sim.instrumentController.getLatestDcVoltageForDeveloperVerification();
        require(finite(firstPoweredVoltage) &&
                !"--- V".equals(sim.instrumentController.getReadingForDeveloperVerification()) &&
                Math.abs(firstPoweredVoltage - unpoweredVoltage) > .001,
            "RC live DC path did not update to the powered solver value");
        int measurementsAfterPowerOn = sim.instrumentController
            .getDcVoltageMeasurementCountForDeveloperVerification();
        double nextReacquisitionAt = sim.t + CirSim.AC_VOLTAGE_CAPTURE_SECONDS;
        int placeholdersAfterPowerOn = sim.instrumentController
            .getDcVoltagePlaceholderDisplayCountForDeveloperVerification();
        // The regular live solver can yield before consuming its configured
        // frame budget.  The deadline is therefore solver time, never an
        // assumed number of UI frames.  Before it, no second burden
        // transaction may occur; once reached, one transaction must update
        // without a probe, power, or topology mutation.
        boolean reacquired = false;
        double reacquiredPoweredVoltage = Double.NaN;
        int measurementsAfterReacquisition = measurementsAfterPowerOn;
        for (int cycle = 0; cycle < 64; cycle++) {
            sim.updateCircuit();
            require(!"--- V".equals(sim.instrumentController.getReadingForDeveloperVerification()) &&
                    finite(sim.instrumentController.getLatestDcVoltageForDeveloperVerification()),
                "RC live DC display entered a placeholder during cycle " + cycle);
            measurementsAfterReacquisition = sim.instrumentController
                .getDcVoltageMeasurementCountForDeveloperVerification();
            if (sim.t + 1e-9 < nextReacquisitionAt) {
                require(measurementsAfterReacquisition == measurementsAfterPowerOn,
                    "RC live DC meter reacquired before its bounded solver-time deadline");
                continue;
            }
            reacquiredPoweredVoltage = sim.instrumentController
                .getLatestDcVoltageForDeveloperVerification();
            require(measurementsAfterReacquisition == measurementsAfterPowerOn + 1,
                "RC live DC meter did not take exactly one burden transaction at its deadline: t=" +
                sim.t + " due=" + nextReacquisitionAt + " measurements=" +
                measurementsAfterReacquisition + " baseline=" + measurementsAfterPowerOn);
            reacquired = true;
            break;
        }
        require(reacquired,
            "RC live solver did not reach the bounded 50 ms DMM deadline: t=" + sim.t +
                " due=" + nextReacquisitionAt + " measurements=" +
                measurementsAfterReacquisition + " baseline=" + measurementsAfterPowerOn);
        require(finite(reacquiredPoweredVoltage) &&
                Math.abs(reacquiredPoweredVoltage - firstPoweredVoltage) > .010,
            "RC live DC meter retained a stale capacitor voltage after solver-time reacquisition");
        require(sim.instrumentController.getDcVoltagePlaceholderDisplayCountForDeveloperVerification() ==
                placeholdersAfterPowerOn,
            "RC live DC display flickered after the real power transition settled");
        sim.instrumentController.exitInstrumentModeForDeveloperVerification();
    }

    private static ProbeTarget probeForPad(CirSim sim, PcbWorkbenchRenderer renderer,
            String padId) {
        Point point = renderer.getPadPoint(padId);
        ProbeTarget target = sim.pcbWorkbenchController.findProbeTarget(point.x, point.y);
        require(target != null && target.isValid(), "RC probe target was not valid: " + padId);
        return target;
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
        setPower(sim, instance, BoardPowerState.POWERED);
        sim.advanceGeneratedTemporalProfile(.750);
        double inputVoltage = voltage(vin, ground);
        double outputVoltage = voltage(output, ground);
        require(finite(inputVoltage) && finite(outputVoltage) && inputVoltage > 0 &&
            outputVoltage > inputVoltage * .30 && outputVoltage < inputVoltage * .60,
            "Detached original left a private bypass connected to the board: " + state +
            " vin=" + inputVoltage + " output=" + outputVoltage);
        setPower(sim, instance, BoardPowerState.UNPOWERED);
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
