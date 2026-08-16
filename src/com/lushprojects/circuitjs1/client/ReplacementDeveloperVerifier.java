package com.lushprojects.circuitjs1.client;

import java.util.Vector;
import java.util.HashSet;

class ReplacementDeveloperVerifier {
    static void verify(CirSim sim) {
        GeneratedBoardInstance instance = sim.getGeneratedBoardInstance();
        GeneratedChallengeController challenge = sim.getGeneratedChallengeController();
        require(instance != null && challenge != null && challenge.isReady(),
            "Replacement verification requires a ready challenge");
        verifyInventoryMetadata();
        ResistorSlotController slots = sim.getResistorSlotController();
        PhysicalResistorPart original = LedIndicatorFamilyState.require(instance).getResistorInventory().get("R1_ORIGINAL");
        sim.setBoardPowerState(BoardPowerState.UNPOWERED);
        sim.updateCircuit();
        verifyPartTopology(sim, instance);
        require(slots.removeInstalledPart() && LedIndicatorFamilyState.require(instance).getR1Slot().isEmpty() &&
            original.getLocation() == ResistorPartLocation.LOOSE && original.isFaulted(),
            "Removing original failed R1 did not empty slot and preserve fault");
        verifyResistance(sim, instance, original, true);
        verifyPartTopology(sim, instance);
        require(slots.install(original.getId()), "Original failed R1 did not reinstall");
        sim.setBoardPowerState(BoardPowerState.POWERED);
        settle(sim);
        boolean originalOpen = instance.getFaultBinding().getFault().getType() ==
            GeneratedFaultType.RESISTOR_OPEN;
        require((originalOpen ? Math.abs(getLedCurrent(instance)) < .000001 :
                getLedCurrent(instance) > .000001 && getLedCurrent(instance) < .001) &&
            !challenge.isCompleted() &&
            !instance.getOperationalStates().isIlluminated("LED1") &&
            !challenge.getDefinition().getBehaviorContract().isFunctionallyRepaired(instance,
                sim.getBoardModificationController(), BoardPowerState.POWERED, false),
            "Reinstalled failed R1 unexpectedly repaired challenge");
        sim.setBoardPowerState(BoardPowerState.UNPOWERED);
        require(slots.removeInstalledPart(), "Reinstalled failed R1 did not remove");
        verifyResistance(sim, instance, original, true);
        verifyPartTopology(sim, instance);
        verifyReplacement(sim, instance, challenge, slots, 10, .025, 1, true, false);
        verifyReplacement(sim, instance, challenge, slots, 10000000, 0, .001, false, false);
        double correctResistance = instance.getPhysicalSpecifications().getResistorNameplate("R1")
            .getNominalResistanceOhms();
        require(slots.installNewFromCatalog(catalogId(correctResistance)),
            "Correct catalog replacement did not install");
        String correctPartId = LedIndicatorFamilyState.require(instance).getR1Slot().getInstalledPart().getId();
        verifyInstalledResistance(sim, instance, correctResistance);
        sim.setBoardPowerState(BoardPowerState.POWERED);
        settle(sim);
        require(getLedCurrent(instance) >= .005 && getLedCurrent(instance) <= .015 &&
            Math.abs(getLedCurrent(instance) - getInstalledResistorCurrent(instance)) <= .0001 &&
            challenge.isCompleted() &&
            challenge.getDefinition().getBehaviorContract().isFunctionallyRepaired(instance,
                sim.getBoardModificationController(), BoardPowerState.POWERED, false) &&
            instance.getOperationalStates().isIlluminated("LED1"),
            "Correct replacement did not complete solved repair");
        verifyPassiveDcVoltageCases(sim, instance, correctResistance);
        verifyHealthyReplacementLiftedLeadVoltage(sim, instance, slots, correctPartId,
            correctResistance);
        require(original.getLocation() == ResistorPartLocation.LOOSE && original.isFaulted(),
            "Completion altered original failed part");
        sim.setBoardPowerState(BoardPowerState.UNPOWERED);
        verifyResistance(sim, instance, original, true);
        verifyInstalledResistance(sim, instance, correctResistance);
        String looseHealthyPartId = verifyUnlimitedAcquisition(sim, instance, slots);
        verifyUnpoweredDcVoltageCases(sim, instance, original, looseHealthyPartId);
        verifyPartTopology(sim, instance);
        sim.setCircuitTitle("Replacement verification passed");
    }

    static void verifyWrongRepair(CirSim sim) {
        GeneratedBoardInstance instance = sim.getGeneratedBoardInstance();
        GeneratedChallengeController challenge = sim.getGeneratedChallengeController();
        require(instance != null && challenge != null && challenge.isReady(),
            "Wrong-repair verification requires a ready challenge");
        require(instance.getCircuitFamilyId().equals("LED_INDICATOR") && instance.getSeed() == 3,
            "Wrong-repair verification requires LED seed 3");
        require("R1".equals(instance.getFaultBinding().getFault().getTargetComponentId()) &&
            "Indicator does not light.".equals(challenge.getComplaintText()),
            "Wrong-repair route did not retain the original R1 challenge");

        LedIndicatorFamilyState family = LedIndicatorFamilyState.require(instance);
        ResistorSlotController slots = sim.getResistorSlotController();
        PhysicalResistorPart original = family.getResistorInventory().get("R1_ORIGINAL");
        sim.setBoardPowerState(BoardPowerState.UNPOWERED);
        require(slots.removeInstalledPart(), "Faulted R1 did not remove through slot controller");
        require(family.getR1Slot().isEmpty() && original.getLocation() == ResistorPartLocation.LOOSE &&
            original.isFaulted(), "Removing R1 changed original physical fault ownership");
        verifyPartTopology(sim, instance);
        require(challenge.getDefinition().getBehaviorContract().getRepairStatus(instance,
            sim.getBoardModificationController(), BoardPowerState.UNPOWERED, false) ==
            GeneratedRepairStatus.STILL_FAULTED_OR_NONFUNCTIONAL && !challenge.isCompleted(),
            "Open or removed R1 did not remain incomplete");
        sim.setBoardPowerState(BoardPowerState.POWERED);
        settle(sim);
        require(challenge.getDefinition().getBehaviorContract().getRepairStatus(instance,
            sim.getBoardModificationController(), BoardPowerState.POWERED, false) ==
            GeneratedRepairStatus.STILL_FAULTED_OR_NONFUNCTIONAL && !challenge.isCompleted(),
            "Open or removed R1 did not remain nonfunctional after a solved powered check");
        sim.setBoardPowerState(BoardPowerState.UNPOWERED);

        ResistorCatalogEntry wrongCatalog = family.getResistorCatalog().get(catalogId(2200));
        require(wrongCatalog.getNameplate().getNominalResistanceOhms() == 2200,
            "2.2 kOhm catalog entry has the wrong nameplate");
        require(slots.installNewFromCatalog(wrongCatalog.getId()),
            "2.2 kOhm catalog replacement was not accepted");
        PhysicalResistorPart wrong = family.getR1Slot().getInstalledPart();
        require(wrong != null && wrong != original && wrong.getLocation() == ResistorPartLocation.INSTALLED &&
            family.getResistorInventory().get(wrong.getId()) == wrong &&
            wrong.getNameplate().getNominalResistanceOhms() == wrongCatalog.getNameplate()
                .getNominalResistanceOhms() &&
            instance.getComponentBindings().getSingleElement("R1") == wrong.getElement() &&
            wrong.getElement().getResistance() == 2200,
            "2.2 kOhm catalog -> physical part -> ResistorElm identity chain was not preserved");
        settle(sim);
        verifyPartTopology(sim, instance);
        sim.setBoardPowerState(BoardPowerState.POWERED);
        settle(sim);
        double wrongLedCurrent = getLedCurrent(instance);
        require(wrongLedCurrent > .001 && wrongLedCurrent < .005 &&
            Math.abs(wrongLedCurrent - getInstalledResistorCurrent(instance)) <= .0001 &&
            instance.getOperationalStates().isIlluminated("LED1") && !challenge.isCompleted() &&
            challenge.getDefinition().getBehaviorContract().getRepairStatus(instance,
                sim.getBoardModificationController(), BoardPowerState.POWERED, false) ==
                GeneratedRepairStatus.DEGRADED_BUT_OPERATING &&
            !challenge.getDefinition().getBehaviorContract().isFunctionallyRepaired(instance,
                sim.getBoardModificationController(), BoardPowerState.POWERED, false),
            "2.2 kOhm replacement did not produce degraded solved LED operation");

        sim.setBoardPowerState(BoardPowerState.UNPOWERED);
        require(slots.removeInstalledPart() && wrong.getLocation() == ResistorPartLocation.LOOSE,
            "2.2 kOhm replacement did not remove through slot controller");
        verifyPartTopology(sim, instance);
        ResistorCatalogEntry correctCatalog = family.getResistorCatalog().get(catalogId(1000));
        require(correctCatalog.getNameplate().getNominalResistanceOhms() == 1000,
            "1 kOhm catalog entry has the wrong nameplate");
        require(slots.installNewFromCatalog(correctCatalog.getId()),
            "1 kOhm catalog replacement was not accepted");
        PhysicalResistorPart correct = family.getR1Slot().getInstalledPart();
        require(correct != wrong && correct != original &&
            correct.getLocation() == ResistorPartLocation.INSTALLED &&
            family.getResistorInventory().get(correct.getId()) == correct &&
            correct.getNameplate().getNominalResistanceOhms() == 1000 &&
            instance.getComponentBindings().getSingleElement("R1") == correct.getElement() &&
            correct.getElement().getResistance() == 1000,
            "1 kOhm catalog -> physical part -> ResistorElm identity chain was not preserved");
        settle(sim);
        verifyPartTopology(sim, instance);
        sim.setBoardPowerState(BoardPowerState.POWERED);
        settle(sim);
        double correctLedCurrent = getLedCurrent(instance);
        require(correctLedCurrent > .005 && correctLedCurrent < .015 &&
            Math.abs(correctLedCurrent - getInstalledResistorCurrent(instance)) <= .0001 &&
            instance.getOperationalStates().isIlluminated("LED1") && challenge.isCompleted() &&
            challenge.getDefinition().getBehaviorContract().getRepairStatus(instance,
                sim.getBoardModificationController(), BoardPowerState.POWERED, false) ==
                GeneratedRepairStatus.CORRECTLY_RESTORED &&
            challenge.getDefinition().getBehaviorContract().isFunctionallyRepaired(instance,
                sim.getBoardModificationController(), BoardPowerState.POWERED, false),
            "1 kOhm replacement did not restore solved LED operation");
        require(original.getLocation() == ResistorPartLocation.LOOSE && original.isFaulted(),
            "Repair changed the original part's fault binding");
        sim.setCircuitTitle("Wrong repair verification passed");
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
        PhysicalResistorPart part = LedIndicatorFamilyState.require(instance).getResistorInventory().get(partId);
        CircuitPostProbeTarget ground = getProbe(sim, instance.getSimulationBindings().getEndpoint("J1.2"));
        CircuitPostProbeTarget boardPad2 = getProbe(sim, instance.getSimulationBindings().getEndpoint("R1.2"));
        ProbeTarget liftedLead2 = new ComponentLeadProbeTarget(sim, instance, "R1", "R1.2",
            sim.pcbWorkbenchController.getRenderer());
        require(!LedIndicatorFamilyState.require(instance).getR1Slot().isEmpty() && LedIndicatorFamilyState.require(instance).getR1Slot().getInstalledPart() == part,
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
            GeneratedChallengeController challenge, ResistorSlotController slots, double resistance,
            double minimumCurrent, double maximumCurrent, boolean expectedIllumination,
            boolean expectedCompletion) {
        require(slots.installNewFromCatalog(catalogId(resistance)), "Catalog replacement did not install");
        PhysicalResistorPart part = LedIndicatorFamilyState.require(instance).getR1Slot().getInstalledPart();
        require(slots.removeInstalledPart(), "Catalog replacement did not become a loose physical part");
        verifyResistance(sim, instance, part, false);
        require(slots.install(part.getId()), "Measured catalog replacement did not reinstall");
        sim.setBoardPowerState(BoardPowerState.POWERED);
        settle(sim);
        verifyPartTopology(sim, instance);
        double current = getLedCurrent(instance);
        require(current >= minimumCurrent && current <= maximumCurrent,
            "Unexpected functional current for " + part.getId() + ": " + current);
        require(instance.getOperationalStates().isIlluminated("LED1") == expectedIllumination,
            "Unexpected LED state for " + part.getId());
        require(challenge.getDefinition().getBehaviorContract().isFunctionallyRepaired(instance,
            sim.getBoardModificationController(), BoardPowerState.POWERED, false) == expectedCompletion,
            "Functional behavior contract disagreed for " + part.getId());
        require(challenge.isCompleted() == expectedCompletion,
            "Unexpected completion state for " + part.getId());
        sim.setBoardPowerState(BoardPowerState.UNPOWERED);
        require(slots.removeInstalledPart(), "Replacement did not remove: " + part.getId());
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
        boolean originalFaultOpen = part.isOriginal() && instance.getFaultBinding().isApplied() &&
            instance.getFaultBinding().getFault().getType() == GeneratedFaultType.RESISTOR_OPEN;
        if (expectedOpen && originalFaultOpen)
            require("OL".equals(forward) && "OL".equals(reverse), "Failed original was not OL");
        else if (expectedOpen && part.isOriginal() && instance.getFaultBinding().isApplied() &&
                instance.getFaultBinding().getFault().getType() == GeneratedFaultType.RESISTOR_INCORRECT_VALUE) {
            double expected = instance.getFaultBinding().getFault().getEffectiveValue();
            double tolerance = expected * .05;
            require(!"OL".equals(forward) && !"OL".equals(reverse) &&
                isWithinTolerance(expected, forwardValue, tolerance) &&
                isWithinTolerance(expected, reverseValue, tolerance),
                "Faulted original resistor did not measure its effective value: " + part.getId() +
                " forward=" + forwardValue + " reverse=" + reverseValue);
        }
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
        ReplaceableComponentSlot slot = LedIndicatorFamilyState.require(instance).getR1Slot();
        int installedCount = 0;
        for (PhysicalResistorPart part : LedIndicatorFamilyState.require(instance).getResistorInventory().getAll()) {
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
                    "R1 attachment does not reach installed part: " + binding.getPadId() +
                    " attachmentNode=" + getNode(binding.getConnectionElement(), attachmentPartPost) +
                    " partNode=" + getNode(slot.getInstalledPart().getPublicTerminal(terminal)) +
                    " attachment=" + describeEndpoint(binding.getConnectionElement(), attachmentPartPost) +
                    " part=" + describeEndpoint(slot.getInstalledPart().getPublicTerminal(terminal)));
                for (PhysicalResistorPart part : LedIndicatorFamilyState.require(instance).getResistorInventory().getAll())
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

    private static String describeEndpoint(CircuitMeasurementEndpoint endpoint) {
        if (!(endpoint instanceof CircuitPostMeasurementEndpoint))
            return String.valueOf(endpoint);
        CircuitPostMeasurementEndpoint post = (CircuitPostMeasurementEndpoint) endpoint;
        return describeEndpoint(post.getElement(), post.getPostIndex());
    }

    private static String describeEndpoint(CircuitElm element, int postIndex) {
        Point point = element.getPost(postIndex);
        return element.getClass().getName() + "@" + point.x + "," + point.y + "#" + postIndex;
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
        verifySeed(0, 330);
        verifySeed(2, 680);
        verifySeed(3, 1000);
    }

    private static void verifySeed(long seed, double expected) {
        GeneratedBoardInstance instance = new LedIndicatorGenerator().generate(seed);
        require(LedIndicatorFamilyState.require(instance).getResistorInventory().size() == 1 &&
            LedIndicatorFamilyState.require(instance).getResistorInventory().getLooseParts().isEmpty() &&
            LedIndicatorFamilyState.require(instance).getResistorCatalog().size() == 73 &&
            LedIndicatorFamilyState.require(instance).getResistorCatalog().get(catalogId(expected)).getNameplate()
                .getNominalResistanceOhms() == expected,
            "Invalid catalog state for seed " + seed);
    }

    private static String verifyUnlimitedAcquisition(CirSim sim, GeneratedBoardInstance instance,
            ResistorSlotController slots) {
        int initialCount = LedIndicatorFamilyState.require(instance).getResistorInventory().size();
        int catalogSize = LedIndicatorFamilyState.require(instance).getResistorCatalog().size();
        Vector<ResistorCatalogEntry> catalogEntries = LedIndicatorFamilyState.require(instance).getResistorCatalog().getEntries();
        Vector<String> ids = new Vector<String>();
        Vector<CircuitElm> elements = new Vector<CircuitElm>();
        Vector<CircuitMeasurementEndpoint> firstEndpoints = new Vector<CircuitMeasurementEndpoint>();
        Vector<CircuitMeasurementEndpoint> secondEndpoints = new Vector<CircuitMeasurementEndpoint>();
        HashSet<String> canonicalCoordinates = collectCoordinates(instance.getSimulationElements());
        HashSet<String> acquiredCoordinates = new HashSet<String>();
        PcbWorkbenchRenderer renderer = sim.pcbWorkbenchController.getRenderer();
        String lastAcquiredPartId = null;
        sim.setBoardPowerState(BoardPowerState.UNPOWERED);
        require(slots.removeInstalledPart(), "Could not remove correct replacement before acquisition loop");
        for (int index = 0; index < 12; index++) {
            require(slots.installNewFromCatalog(catalogId(1000)), "Catalog depleted during acquisition");
            PhysicalResistorPart part = LedIndicatorFamilyState.require(instance).getR1Slot().getInstalledPart();
            require(!ids.contains(part.getId()) && !elements.contains(part.getElement()) &&
                !firstEndpoints.contains(part.getPublicTerminal(0)) &&
                !secondEndpoints.contains(part.getPublicTerminal(1)) &&
                countOccurrences(sim.elmList, part.getElement()) == 1 &&
                instance.getSimulationElements().contains(part.getElement()),
                "Catalog acquisition reused a physical part or backing");
            ids.add(part.getId());
            elements.add(part.getElement());
            firstEndpoints.add(part.getPublicTerminal(0));
            secondEndpoints.add(part.getPublicTerminal(1));
            verifyNewBackingCoordinates(part, canonicalCoordinates, acquiredCoordinates);
            require(slots.removeInstalledPart(), "Could not remove acquired catalog part");
            lastAcquiredPartId = part.getId();
        }
        require(LedIndicatorFamilyState.require(instance).getResistorInventory().size() == initialCount + 12 &&
            LedIndicatorFamilyState.require(instance).getResistorInventory().getLooseParts().size() >= 12 &&
            LedIndicatorFamilyState.require(instance).getResistorCatalog().size() == catalogSize &&
            sameCatalogEntries(catalogEntries, LedIndicatorFamilyState.require(instance).getResistorCatalog().getEntries()),
            "Repeated catalog acquisition did not retain physical tray parts or depleted catalog");
        verifyTrayPaginationAndProbeGeometry(sim, instance, renderer, lastAcquiredPartId);
        require(lastAcquiredPartId != null, "No healthy catalog part was acquired");
        require(slots.installNewFromCatalog(catalogId(instance.getPhysicalSpecifications()
            .getResistorNameplate("R1").getNominalResistanceOhms())), "Could not restore correct catalog part");
        sim.analyzeCircuit();
        sim.runCircuit(true);
        return lastAcquiredPartId;
    }

    private static void verifyNewBackingCoordinates(PhysicalResistorPart part,
            HashSet<String> canonicalCoordinates, HashSet<String> acquiredCoordinates) {
        HashSet<String> partCoordinates = new HashSet<String>();
        for (CircuitElm backing : part.getBackingElements()) {
            String first = coordinate(backing.x, backing.y);
            String second = coordinate(backing.x2, backing.y2);
            require(!first.equals(second) &&
                (partCoordinates.contains(first) || (!canonicalCoordinates.contains(first) &&
                !acquiredCoordinates.contains(first))) &&
                (partCoordinates.contains(second) || (!canonicalCoordinates.contains(second) &&
                !acquiredCoordinates.contains(second))),
                "Catalog backing coordinate overlaps canonical or acquired graph: " + part.getId() +
                " first=" + first + " second=" + second);
            if (partCoordinates.add(first))
                acquiredCoordinates.add(first);
            if (partCoordinates.add(second))
                acquiredCoordinates.add(second);
        }
    }

    private static void verifyTrayPaginationAndProbeGeometry(CirSim sim,
            GeneratedBoardInstance instance, PcbWorkbenchRenderer renderer, String retainedPartId) {
        Vector<PhysicalResistorPart> loose = LedIndicatorFamilyState.require(instance).getResistorInventory().getLooseParts();
        int expectedPages = Math.max(1, (loose.size() + 2) / 3);
        require(renderer.getTrayPageCount() == expectedPages && expectedPages >= 2,
            "Tray page count did not match loose inventory");
        renderer.setTrayPage(0);
        require(renderer.getVisibleLooseParts().size() <= 3,
            "Tray page exposes more than three physical parts");
        PhysicalResistorPart firstPagePart = renderer.getVisibleLooseParts().get(0);
        verifyVisiblePartProbeTarget(sim, renderer, firstPagePart);
        renderer.setSelectedPartId(firstPagePart.getId());
        renderer.setTrayPage(1);
        require(renderer.getSelectedPartId() == null,
            "Changing tray page did not clear selection for hidden part");
        require(renderer.getLoosePartLeadPoint(firstPagePart.getId(), 0) == null &&
            renderer.getLoosePartLeadPoint(firstPagePart.getId(), 1) == null,
            "Hidden tray part still has a marker point");
        PhysicalResistorPart retainedPart = LedIndicatorFamilyState.require(instance).getResistorInventory().get(retainedPartId);
        int retainedPage = loose.indexOf(retainedPart) / 3;
        renderer.setTrayPage(retainedPage);
        PhysicalResistorPartProbeTarget retainedTarget = new PhysicalResistorPartProbeTarget(sim,
            instance, retainedPartId, 0, renderer);
        Point retainedMarker = retainedTarget.getMarkerPoint();
        require(retainedMarker != null, "Retained loose part was not visible on its own tray page");
        renderer.setTrayPage(retainedPage == 0 ? 1 : 0);
        require(retainedTarget.getMarkerPoint() == null,
            "Retained loose probe marker moved onto another tray page");
        renderer.setTrayPage(retainedPage);
        Point restoredMarker = retainedTarget.getMarkerPoint();
        require(restoredMarker != null && restoredMarker.x == retainedMarker.x &&
            restoredMarker.y == retainedMarker.y,
            "Returning to tray page did not restore marker for the same physical part");
        for (int page = 0; page < renderer.getTrayPageCount(); page++) {
            renderer.setTrayPage(page);
            require(renderer.getVisibleLooseParts().size() <= 3,
                "Tray page exposes more than three physical parts");
            for (PhysicalResistorPart part : renderer.getVisibleLooseParts())
                verifyVisiblePartProbeTarget(sim, renderer, part);
        }
        renderer.setTrayPage(0);
    }

    private static void verifyVisiblePartProbeTarget(CirSim sim, PcbWorkbenchRenderer renderer,
            PhysicalResistorPart part) {
        for (int terminal = 0; terminal < 2; terminal++) {
            Point point = renderer.getLoosePartLeadPoint(part.getId(), terminal);
            ProbeTarget target = renderer.findProbeTarget(sim, point.x, point.y);
            require(target instanceof PhysicalResistorPartProbeTarget &&
                target.isSameTarget(new PhysicalResistorPartProbeTarget(sim,
                    sim.getGeneratedBoardInstance(), part.getId(), terminal, renderer)),
                "Visible tray lead did not resolve to its physical probe target: " + part.getId());
        }
    }

    private static HashSet<String> collectCoordinates(Vector<CircuitElm> elements) {
        HashSet<String> coordinates = new HashSet<String>();
        for (CircuitElm element : elements) {
            coordinates.add(coordinate(element.x, element.y));
            coordinates.add(coordinate(element.x2, element.y2));
        }
        return coordinates;
    }

    private static String coordinate(int x, int y) { return x + ":" + y; }

    private static boolean sameCatalogEntries(Vector<ResistorCatalogEntry> expected,
            Vector<ResistorCatalogEntry> actual) {
        if (expected.size() != actual.size())
            return false;
        for (int index = 0; index < expected.size(); index++)
            if (expected.get(index) != actual.get(index))
                return false;
        return true;
    }

    private static String catalogId(double resistance) { return "R_CATALOG_" + (long) resistance; }

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
