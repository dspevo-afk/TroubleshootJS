package com.lushprojects.circuitjs1.client;

import java.util.Vector;

class ReplacementDeveloperVerifier {
    static void verify(CirSim sim) {
        GeneratedBoardInstance instance = sim.getGeneratedBoardInstance();
        GeneratedChallengeController challenge = sim.getGeneratedChallengeController();
        require(instance != null && challenge != null && challenge.isReady(),
            "Replacement verification requires a ready challenge");
        verifyInventoryMetadata();
        ResistorSlotController slots = sim.getResistorSlotController();
        PhysicalResistorPart original = instance.getResistorInventory().get("R1_ORIGINAL");
        sim.setBoardPowerState(BoardPowerState.UNPOWERED);
        sim.updateCircuit();
        verifyPartTopology(sim, instance);
        require(slots.removeInstalledPart() && instance.getR1Slot().isEmpty() &&
            original.getLocation() == ResistorPartLocation.LOOSE && original.isFaulted(),
            "Removing original failed R1 did not empty slot and preserve fault");
        verifyResistance(sim, instance, original, true);
        verifyPartTopology(sim, instance);
        require(slots.install(original.getId()), "Original failed R1 did not reinstall");
        sim.setBoardPowerState(BoardPowerState.POWERED);
        settle(sim);
        require(Math.abs(getLedCurrent(instance)) < .000001 && !challenge.isCompleted() &&
            !instance.getOperationalStates().isIlluminated("LED1"),
            "Reinstalled failed R1 unexpectedly repaired challenge");
        sim.setBoardPowerState(BoardPowerState.UNPOWERED);
        require(slots.removeInstalledPart(), "Reinstalled failed R1 did not remove");
        verifyResistance(sim, instance, original, true);
        verifyPartTopology(sim, instance);
        verifyReplacement(sim, instance, challenge, slots, "R1_REPLACEMENT_0", .025, .035, true,
            false);
        verifyReplacement(sim, instance, challenge, slots, "R1_REPLACEMENT_2", .0005, .001, false,
            false);
        require(slots.install("R1_REPLACEMENT_1"), "Correct replacement did not install");
        double correctResistance = instance.getResistorInventory().get("R1_REPLACEMENT_1")
            .getNameplate().getNominalResistanceOhms();
        verifyInstalledResistance(sim, instance, correctResistance);
        sim.setBoardPowerState(BoardPowerState.POWERED);
        settle(sim);
        require(getLedCurrent(instance) >= .005 && getLedCurrent(instance) <= .015 &&
            Math.abs(getLedCurrent(instance) - getInstalledResistorCurrent(instance)) <= .0001 &&
            challenge.isCompleted() && instance.getOperationalStates().isIlluminated("LED1"),
            "Correct replacement did not complete solved repair");
        verifyPassiveDcVoltageCases(sim, instance, correctResistance);
        verifyHealthyReplacementLiftedLeadVoltage(sim, instance, slots, "R1_REPLACEMENT_1",
            correctResistance);
        require(original.getLocation() == ResistorPartLocation.LOOSE && original.isFaulted(),
            "Completion altered original failed part");
        sim.setBoardPowerState(BoardPowerState.UNPOWERED);
		verifyUnpoweredDcVoltageCases(sim, instance, original, "R1_REPLACEMENT_0");
        verifyResistance(sim, instance, original, true);
        verifyResistance(sim, instance, instance.getResistorInventory().get("R1_REPLACEMENT_0"), false);
        verifyResistance(sim, instance, instance.getResistorInventory().get("R1_REPLACEMENT_2"), false);
        verifyInstalledResistance(sim, instance, correctResistance);
        verifyPartTopology(sim, instance);
        sim.setCircuitTitle("Replacement verification passed");
    }

    private static void verifyPassiveDcVoltageCases(CirSim sim, GeneratedBoardInstance instance,
            double resistance) {
        CircuitPostProbeTarget vin = getProbe(sim, instance.getSimulationBindings().getEndpoint("J1.1"));
        CircuitPostProbeTarget ground = getProbe(sim, instance.getSimulationBindings().getEndpoint("J1.2"));
        CircuitPostProbeTarget r11 = getProbe(sim, instance.getSimulationBindings().getEndpoint("R1.1"));
        CircuitPostProbeTarget r12 = getProbe(sim, instance.getSimulationBindings().getEndpoint("R1.2"));
        CircuitPostProbeTarget ledAnode = getProbe(sim, instance.getSimulationBindings().getEndpoint("LED1.A"));
        CircuitPostProbeTarget ledCathode = getProbe(sim, instance.getSimulationBindings().getEndpoint("LED1.K"));
        double nominalVin = instance.getPhysicalSpecifications().getPowerInputNameplate("VIN_INPUT")
            .getNominalVoltage();
        requireApproximately(nominalVin, measureDc(sim, vin, ground), .02, "VIN DC measurement");
        requireApproximately(-nominalVin, measureDc(sim, ground, vin), .02, "Reverse VIN DC measurement");
        requireApproximately(0, measureDc(sim, vin, vin), .0001, "Same-target DC measurement");
        double r1Input = measureDc(sim, r11, ground);
        double r1Output = measureDc(sim, r12, ground);
        double r1Drop = measureDc(sim, r11, r12);
        double ledDrop = measureDc(sim, ledAnode, ledCathode);
        requireApproximately(nominalVin, r1Input, .02, "Installed R1 lead 1 DC measurement");
        requireApproximately(r1Input - r1Output, r1Drop, .02,
            "Installed R1 DC drop did not match solved lead voltages");
        requireApproximately(r1Output, ledDrop, .02,
            "LED DC drop did not match solved LED-node voltage");
        require(r1Drop > 0 && ledDrop > 0 && resistance > 0,
            "Installed R1 or LED did not have a positive solved voltage drop");
    }

    private static void verifyHealthyReplacementLiftedLeadVoltage(CirSim sim,
            GeneratedBoardInstance instance, ResistorSlotController slots, String partId,
            double resistance) {
        PhysicalResistorPart part = instance.getResistorInventory().get(partId);
        CircuitPostProbeTarget ground = getProbe(sim, instance.getSimulationBindings().getEndpoint("J1.2"));
        CircuitPostProbeTarget boardPad2 = getProbe(sim, instance.getSimulationBindings().getEndpoint("R1.2"));
        ProbeTarget liftedLead2 = new ComponentLeadProbeTarget(sim, instance, "R1", "R1.2",
            sim.pcbWorkbenchController.getRenderer());
        require(!instance.getR1Slot().isEmpty() && instance.getR1Slot().getInstalledPart() == part,
            "Correct replacement was not installed before lifted-lead voltage check");
        sim.setBoardPowerState(BoardPowerState.UNPOWERED);
        require(sim.getBoardModificationController().liftLead("R1", "R1.2"),
            "Healthy replacement lead 2 did not lift");
        sim.setBoardPowerState(BoardPowerState.POWERED);
        sim.analyzeCircuit();
        sim.runCircuit(true);
        double nominalVin = instance.getPhysicalSpecifications().getPowerInputNameplate("VIN_INPUT")
            .getNominalVoltage();
        double expectedLiftedVoltage = nominalVin * DcVoltageMeasurementStimulus.INPUT_RESISTANCE /
            (DcVoltageMeasurementStimulus.INPUT_RESISTANCE + resistance);
        double liftedVoltage = measureDc(sim, liftedLead2, ground);
        double boardPadVoltage = measureDc(sim, boardPad2, ground);
        requireApproximately(expectedLiftedVoltage, liftedVoltage, .02,
            "Healthy lifted R1 lead 2 did not measure the 10 Mohm divider voltage");
        require(Math.abs(liftedVoltage - boardPadVoltage) > nominalVin * .5,
            "Lifted R1 lead 2 was confused with its separate board-side pad");
        require(Math.abs(getLedCurrent(instance)) < .000001 &&
            !instance.getOperationalStates().isIlluminated("LED1"),
            "Lifted healthy replacement allowed LED current");
        sim.setBoardPowerState(BoardPowerState.UNPOWERED);
        require(sim.getBoardModificationController().reconnectLead("R1", "R1.2"),
            "Healthy replacement lead 2 did not reconnect");
        sim.setBoardPowerState(BoardPowerState.POWERED);
        settle(sim);
        require(getLedCurrent(instance) >= .005 && instance.getOperationalStates().isIlluminated("LED1"),
            "Reconnecting healthy replacement did not restore LED operation");
    }

    private static void verifyUnpoweredDcVoltageCases(CirSim sim, GeneratedBoardInstance instance,
            PhysicalResistorPart original, String healthyPartId) {
        CircuitPostProbeTarget vin = getProbe(sim, instance.getSimulationBindings().getEndpoint("J1.1"));
        CircuitPostProbeTarget ground = getProbe(sim, instance.getSimulationBindings().getEndpoint("J1.2"));
        requireApproximately(0, measureDc(sim, vin, ground), .001, "Unpowered VIN DC measurement");
        ProbeTarget healthyLead1 = new PhysicalResistorPartProbeTarget(sim, instance, healthyPartId, 0,
            sim.pcbWorkbenchController.getRenderer());
        ProbeTarget healthyLead2 = new PhysicalResistorPartProbeTarget(sim, instance, healthyPartId, 1,
            sim.pcbWorkbenchController.getRenderer());
        ProbeTarget failedLead1 = new PhysicalResistorPartProbeTarget(sim, instance, original.getId(), 0,
            sim.pcbWorkbenchController.getRenderer());
        ProbeTarget failedLead2 = new PhysicalResistorPartProbeTarget(sim, instance, original.getId(), 1,
            sim.pcbWorkbenchController.getRenderer());
        requireApproximately(0, measureDc(sim, healthyLead1, healthyLead2), .001,
            "Loose healthy resistor DC measurement");
        requireApproximately(0, measureDc(sim, failedLead1, failedLead2), .001,
            "Loose failed resistor DC measurement");
    }

    private static double measureDc(CirSim sim, ProbeTarget red, ProbeTarget black) {
        return sim.instrumentController.getDcVoltageDifferenceForDeveloperVerification(red, black);
    }

    private static void verifyReplacement(CirSim sim, GeneratedBoardInstance instance,
            GeneratedChallengeController challenge, ResistorSlotController slots, String partId,
            double minimumCurrent, double maximumCurrent, boolean expectedIllumination,
            boolean expectedCompletion) {
        PhysicalResistorPart part = instance.getResistorInventory().get(partId);
        verifyResistance(sim, instance, part, false);
        require(slots.install(partId), "Replacement did not install: " + partId);
        sim.setBoardPowerState(BoardPowerState.POWERED);
        settle(sim);
        verifyPartTopology(sim, instance);
        double current = getLedCurrent(instance);
        require(current >= minimumCurrent && current <= maximumCurrent,
            "Unexpected functional current for " + partId + ": " + current);
        require(instance.getOperationalStates().isIlluminated("LED1") == expectedIllumination,
            "Unexpected LED state for " + partId);
        require(challenge.isCompleted() == expectedCompletion,
            "Unexpected completion state for " + partId);
        sim.setBoardPowerState(BoardPowerState.UNPOWERED);
        require(slots.removeInstalledPart(), "Replacement did not remove: " + partId);
        verifyResistance(sim, instance, part, false);
        verifyPartTopology(sim, instance);
    }

    private static void verifyResistance(CirSim sim, GeneratedBoardInstance instance,
            PhysicalResistorPart part, boolean expectedOpen) {
        ProbeTarget first = new PhysicalResistorPartProbeTarget(sim, instance, part.getId(), 0,
            sim.pcbWorkbenchController.getRenderer());
        ProbeTarget second = new PhysicalResistorPartProbeTarget(sim, instance, part.getId(), 1,
            sim.pcbWorkbenchController.getRenderer());
        sim.instrumentController.setResistanceProbesForDeveloperVerification(first, second);
        String forward = sim.instrumentController.getReadingForDeveloperVerification();
        double forwardValue = sim.instrumentController.getLatestResistanceReadingForDeveloperVerification();
        String forwardDiagnostics = sim.getLastResistanceMeasurementDiagnosticsForDeveloperVerification();
		verifyNeutralResistanceReference(sim, part.getId() + " forward");
        sim.instrumentController.setResistanceProbesForDeveloperVerification(second, first);
        String reverse = sim.instrumentController.getReadingForDeveloperVerification();
        double reverseValue = sim.instrumentController.getLatestResistanceReadingForDeveloperVerification();
        String reverseDiagnostics = sim.getLastResistanceMeasurementDiagnosticsForDeveloperVerification();
		verifyNeutralResistanceReference(sim, part.getId() + " reverse");
        if (expectedOpen)
            require("OL".equals(forward) && "OL".equals(reverse), "Failed original was not OL");
        else {
            double expected = part.getNameplate().getNominalResistanceOhms();
            double tolerance = expected * part.getNameplate().getTolerancePercent() / 100.0;
            require(!"OL".equals(forward) && !"OL".equals(reverse) &&
                isWithinTolerance(expected, forwardValue, tolerance) &&
                isWithinTolerance(expected, reverseValue, tolerance),
                "Healthy replacement reading disagrees with nameplate: " + part.getId() +
                " forward=" + forwardValue + " reverse=" + reverseValue + " " +
                "forwardDiagnostics=" + forwardDiagnostics + " reverseDiagnostics=" +
                reverseDiagnostics);
        }
        sim.instrumentController.exitInstrumentModeForDeveloperVerification();
        require(!sim.activeMeasurementOverlay && sim.isActiveMeasurementSolverRestoredForDeveloperVerification(),
            "Meter overlay did not tear down for " + part.getId());
    }

    private static void verifyInstalledResistance(CirSim sim, GeneratedBoardInstance instance,
            double expectedResistance) {
        CircuitPostProbeTarget first = getProbe(sim,
            instance.getSimulationBindings().getEndpoint("R1.1"));
        CircuitPostProbeTarget second = getProbe(sim,
            instance.getSimulationBindings().getEndpoint("R1.2"));
        sim.instrumentController.setResistanceProbesForDeveloperVerification(first, second);
        double actual = sim.instrumentController.getLatestResistanceReadingForDeveloperVerification();
		verifyNeutralResistanceReference(sim, "installed R1");
        require(isWithinTolerance(expectedResistance, actual, expectedResistance * .05),
            "Installed R1 resistance disagrees with part: " + actual);
        sim.instrumentController.exitInstrumentModeForDeveloperVerification();
    }

    private static void verifyPartTopology(CirSim sim, GeneratedBoardInstance instance) {
        ReplaceableComponentSlot slot = instance.getR1Slot();
        int installedCount = 0;
        for (PhysicalResistorPart part : instance.getResistorInventory().getAll()) {
            if (part.getLocation() == ResistorPartLocation.INSTALLED)
                installedCount++;
            for (CircuitElm element : part.getBackingElements())
                require(countOccurrences(sim.elmList, element) == 1,
                    "Part backing is missing or duplicated: " + part.getId());
        }
        require((slot.isEmpty() && installedCount == 0) || (!slot.isEmpty() && installedCount == 1 &&
            slot.getInstalledPart().getLocation() == ResistorPartLocation.INSTALLED),
            "Part locations disagree with slot occupancy");
        for (GeneratedComponentConnectionBinding binding : instance.getConnectionBindings().getForComponent("R1")) {
            boolean attached = !slot.isEmpty();
            require(countOccurrences(sim.elmList, binding.getConnectionElement()) == (attached ? 1 : 0),
                "R1 attachment occurrence disagrees with slot occupancy");
            if (attached) {
                int terminal = "R1.1".equals(binding.getPadId()) ? 0 : 1;
                int attachmentPartPost = terminal == 0 ? 1 : 0;
                require(getNode(binding.getConnectionElement(), attachmentPartPost) == getNode(
                    slot.getInstalledPart().getPublicTerminal(terminal)),
                    "R1 attachment does not reach installed part");
                for (PhysicalResistorPart part : instance.getResistorInventory().getAll())
                    if (part != slot.getInstalledPart())
                        require(getNode(binding.getConnectionElement(), attachmentPartPost) != getNode(
                            part.getPublicTerminal(terminal)),
                            "Loose part remains attached to R1: " + part.getId());
            }
        }
    }

    private static int getNode(CircuitMeasurementEndpoint endpoint) {
        if (!(endpoint instanceof CircuitPostMeasurementEndpoint))
            throw new IllegalStateException("Unsupported resistor part endpoint");
        CircuitPostMeasurementEndpoint post = (CircuitPostMeasurementEndpoint) endpoint;
        return getNode(post.getElement(), post.getPostIndex());
    }

    private static CircuitPostProbeTarget getProbe(CirSim sim, CircuitMeasurementEndpoint endpoint) {
        if (!(endpoint instanceof CircuitPostMeasurementEndpoint))
            throw new IllegalStateException("Unsupported board pad endpoint");
        CircuitPostMeasurementEndpoint post = (CircuitPostMeasurementEndpoint) endpoint;
        return new CircuitPostProbeTarget(sim, post.getElement(), post.getPostIndex());
    }

    private static int getNode(CircuitElm element, int post) { return element.nodes[post]; }
    private static int countOccurrences(Vector<CircuitElm> elements, CircuitElm target) {
        int count = 0;
        for (CircuitElm element : elements)
            if (element == target)
                count++;
        return count;
    }

    private static double getLedCurrent(GeneratedBoardInstance instance) {
        return Math.abs(((LEDElm) instance.getComponentBindings().getSingleElement("LED1")).getCurrent());
    }

    private static double getInstalledResistorCurrent(GeneratedBoardInstance instance) {
        return Math.abs(((ResistorElm) instance.getComponentBindings().getSingleElement("R1")).getCurrent());
    }

    private static boolean isWithinTolerance(double expected, double actual, double tolerance) {
        return !Double.isNaN(actual) && !Double.isInfinite(actual) &&
            Math.abs(expected - actual) <= tolerance;
    }

    private static void requireApproximately(double expected, double actual, double tolerance,
            String message) {
		require(!Double.isNaN(actual) && !Double.isInfinite(actual) &&
		    Math.abs(expected - actual) <= tolerance, message + ": " + actual);
	}

    private static void verifyNeutralResistanceReference(CirSim sim, String measurement) {
        require(sim.hasElectricallyNeutralResistanceReferenceForDeveloperVerification(),
            "Resistance reference was not neutral for " + measurement + ": " +
            sim.getLastResistanceMeasurementDiagnosticsForDeveloperVerification());
    }

    private static void verifyInventoryMetadata() {
        verifySeed(0, new double[] { 100, 330, 4700 });
        verifySeed(2, new double[] { 220, 680, 10000 });
        verifySeed(3, new double[] { 330, 1000, 15000 });
    }

    private static void verifySeed(long seed, double[] expected) {
        GeneratedBoardInstance instance = new LedIndicatorGenerator().generate(seed);
        for (int index = 0; index < expected.length; index++) {
            PhysicalResistorPart part = instance.getResistorInventory().get("R1_REPLACEMENT_" + index);
            require(part.getLocation() == ResistorPartLocation.LOOSE &&
                part.getNameplate().getNominalResistanceOhms() == expected[index] &&
                ResistorColorCode.getFourBandCode(part.getNameplate()).length == 4,
                "Invalid deterministic inventory for seed " + seed);
        }
    }

    private static void settle(CirSim sim) {
        sim.analyzeCircuit();
        sim.runCircuit(true);
        sim.runCircuit(true);
        sim.verifyGeneratedBoard();
    }

    private static void require(boolean condition, String message) {
        if (!condition)
            throw new IllegalStateException(message);
    }
}