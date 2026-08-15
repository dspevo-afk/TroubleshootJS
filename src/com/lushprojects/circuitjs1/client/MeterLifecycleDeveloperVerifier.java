package com.lushprojects.circuitjs1.client;

import com.google.gwt.dom.client.NativeEvent;

class MeterLifecycleDeveloperVerifier {
    static void verify(CirSim sim) {
        GeneratedBoardInstance instance = sim.getGeneratedBoardInstance();
        require(instance != null && sim.getGeneratedChallengeController() != null &&
            sim.getGeneratedChallengeController().isReady(), "Meter verification requires ready challenge");
        installCorrectReplacement(sim, instance);
        verifyLiftedLeadResistance(sim, instance, "R1.2");
        verifyLiftedLeadResistance(sim, instance, "R1.1");
        verifyRetainedLiftedLeadVoltage(sim, instance);
        verifyPhysicalTargetInvalidation(sim, instance);
        sim.setCircuitTitle("Meter lifecycle verification passed");
    }

    private static void installCorrectReplacement(CirSim sim, GeneratedBoardInstance instance) {
        sim.setBoardPowerState(BoardPowerState.UNPOWERED);
        sim.updateCircuit();
        ResistorSlotController slots = sim.getResistorSlotController();
        if (!instance.getR1Slot().isEmpty())
            require(slots.removeInstalledPart(), "Could not remove original R1");
        require(slots.install("R1_REPLACEMENT_1"), "Could not install correct R1 replacement");
        sim.setBoardPowerState(BoardPowerState.POWERED);
        settle(sim);
        require(instance.getOperationalStates().isIlluminated("LED1"),
            "Correct replacement did not illuminate LED before meter tests");
    }

    private static void verifyLiftedLeadResistance(CirSim sim, GeneratedBoardInstance instance,
            String liftedPadId) {
        PcbWorkbenchRenderer renderer = sim.pcbWorkbenchController.getRenderer();
        String attachedPadId = "R1.1".equals(liftedPadId) ? "R1.2" : "R1.1";
        double expected = instance.getR1Slot().getInstalledPart().getNameplate().getNominalResistanceOhms();
        double tolerance = expected * instance.getR1Slot().getInstalledPart().getNameplate()
            .getTolerancePercent() / 100.0;
        sim.setBoardPowerState(BoardPowerState.UNPOWERED);
        sim.updateCircuit();
        ProbeTarget installedFirst = hitPad(sim, renderer, "R1.1");
        ProbeTarget installedSecond = hitPad(sim, renderer, "R1.2");
        sim.instrumentController.activateResistanceModeForDeveloperVerification();
        placeProbes(sim, installedFirst, installedSecond);
        require(!"OL".equals(sim.instrumentController.getReadingForDeveloperVerification()),
            "Installed healthy R1 unexpectedly measured OL");
        require(sim.getBoardModificationController().liftLead("R1", liftedPadId),
            "Could not lift " + liftedPadId);
        sim.updateCircuit();
        ProbeTarget componentLead1 = hitLead(sim, renderer, "R1.1");
        ProbeTarget componentLead2 = hitLead(sim, renderer, "R1.2");
        ProbeTarget boardLiftedPad = hitPad(sim, renderer, liftedPadId);
        ProbeTarget boardAttachedPad = hitPad(sim, renderer, attachedPadId);
        require(!componentLead1.isSameTarget(componentLead2) &&
            !componentLead2.isSameTarget(boardLiftedPad),
            "Lifted component lead and PCB pad were not distinct physical targets");
        placeProbes(sim, componentLead1, componentLead2);
		requireLiftedResistance(sim, instance, componentLead1, componentLead2, expected, tolerance,
		    "forward " + liftedPadId);
        placeProbes(sim, componentLead2, componentLead1);
		requireLiftedResistance(sim, instance, componentLead2, componentLead1, expected, tolerance,
		    "reverse " + liftedPadId);
        placeProbes(sim, componentLead2, boardLiftedPad);
        require("OL".equals(sim.instrumentController.getReadingForDeveloperVerification()),
            "Lifted lead gap did not measure OL");
        ProbeTarget attachedLead = "R1.1".equals(attachedPadId) ? componentLead1 : componentLead2;
        placeProbes(sim, attachedLead, boardAttachedPad);
        requireApproximately(0, sim.instrumentController.getLatestResistanceReadingForDeveloperVerification(),
            .001, "Still-attached lead was not connected to its PCB pad");
        require(sim.getBoardModificationController().reconnectLead("R1", liftedPadId),
            "Could not reconnect " + liftedPadId);
        sim.setBoardPowerState(BoardPowerState.POWERED);
        settle(sim);
        require(instance.getOperationalStates().isIlluminated("LED1"),
            "Reconnecting " + liftedPadId + " did not restore LED operation");
        require(sim.isActiveMeasurementSolverRestoredForDeveloperVerification(),
            "Resistance overlay did not restore after lifting " + liftedPadId);
    }

    private static void verifyRetainedLiftedLeadVoltage(CirSim sim, GeneratedBoardInstance instance) {
        PcbWorkbenchRenderer renderer = sim.pcbWorkbenchController.getRenderer();
        double resistance = instance.getR1Slot().getInstalledPart().getNameplate().getNominalResistanceOhms();
        double vin = instance.getPhysicalSpecifications().getPowerInputNameplate("VIN_INPUT")
            .getNominalVoltage();
        double expected = vin * DcVoltageMeasurementStimulus.INPUT_RESISTANCE /
            (DcVoltageMeasurementStimulus.INPUT_RESISTANCE + resistance);
        sim.setBoardPowerState(BoardPowerState.UNPOWERED);
        require(sim.getBoardModificationController().liftLead("R1", "R1.2"),
            "Could not lift downstream R1 lead for DC test");
        sim.updateCircuit();
        ProbeTarget liftedLead = hitLead(sim, renderer, "R1.2");
        ProbeTarget ground = hitPad(sim, renderer, "J1.2");
        ProbeTarget boardPad = hitPad(sim, renderer, "R1.2");
        sim.instrumentController.activateDcVoltageModeForDeveloperVerification();
        int beforePlacement = sim.instrumentController.getDcVoltageMeasurementCountForDeveloperVerification();
        placeProbes(sim, liftedLead, ground);
        require(beforePlacement + 2 == sim.instrumentController.getDcVoltageMeasurementCountForDeveloperVerification(),
            "DC probe placement did not consume exactly one refresh per changed probe");
        int beforePowerOn = sim.instrumentController.getDcVoltageMeasurementCountForDeveloperVerification();
        sim.setBoardPowerState(BoardPowerState.POWERED);
        sim.updateCircuit();
        require(beforePowerOn + 1 == sim.instrumentController.getDcVoltageMeasurementCountForDeveloperVerification(),
            "DC power-on refresh was not consumed exactly once");
        requireDisplayVoltage(sim, expected, .02, "Retained lifted-lead DC reading");
        requireApproximately(0, measureDisplayedDc(sim, boardPad, ground), .02,
            "Separate lifted R1.2 PCB pad did not remain near ground");
		placeProbes(sim, liftedLead, ground);
		requireDisplayVoltage(sim, expected, .02,
		    "Restored retained lifted-lead DC reading after PCB-pad comparison");
        require(Math.abs(getLedCurrent(instance)) < .000001 &&
            !instance.getOperationalStates().isIlluminated("LED1"),
            "Lifted downstream R1 lead allowed LED current");
        int afterMeasurement = sim.instrumentController.getDcVoltageMeasurementCountForDeveloperVerification();
        sim.updateCircuit();
        sim.updateCircuit();
        require(afterMeasurement == sim.instrumentController.getDcVoltageMeasurementCountForDeveloperVerification(),
            "Canvas repaint caused recurring DC transactions");
        int beforePowerOff = sim.instrumentController.getDcVoltageMeasurementCountForDeveloperVerification();
        sim.setBoardPowerState(BoardPowerState.UNPOWERED);
        sim.updateCircuit();
        require(beforePowerOff + 1 == sim.instrumentController.getDcVoltageMeasurementCountForDeveloperVerification(),
            "DC power-off refresh was not consumed exactly once");
        requireDisplayVoltage(sim, 0, .001, "Retained lifted-lead DC power-off reading");
        sim.setBoardPowerState(BoardPowerState.POWERED);
        sim.updateCircuit();
        requireDisplayVoltage(sim, expected, .02, "Retained lifted-lead DC repower reading");
        sim.setBoardPowerState(BoardPowerState.UNPOWERED);
        require(sim.getBoardModificationController().reconnectLead("R1", "R1.2"),
            "Could not reconnect downstream R1 lead after DC test");
        sim.setBoardPowerState(BoardPowerState.POWERED);
        settle(sim);
        require(instance.getOperationalStates().isIlluminated("LED1"),
            "Reconnecting downstream R1 lead did not restore LED");
    }

    private static void verifyPhysicalTargetInvalidation(CirSim sim, GeneratedBoardInstance instance) {
        PcbWorkbenchRenderer renderer = sim.pcbWorkbenchController.getRenderer();
        sim.setBoardPowerState(BoardPowerState.UNPOWERED);
        require(sim.getBoardModificationController().liftLead("R1", "R1.2"),
            "Could not lift R1 before target identity test");
        sim.updateCircuit();
        ProbeTarget previousPartLead = hitLead(sim, renderer, "R1.2");
        require(previousPartLead.isValid(), "Lifted lead target was not valid for selected part");
        require(sim.getBoardModificationController().reconnectLead("R1", "R1.2"),
            "Could not reconnect R1 during target identity test");
        sim.updateCircuit();
        require(!previousPartLead.isValid(), "Reconnected internal lead remained exposed as a probe target");
        require(sim.getResistorSlotController().removeInstalledPart(),
            "Could not remove selected physical resistor");
        require(!previousPartLead.isValid(), "Removed physical part retained an installed-lead probe target");
        require(sim.getResistorSlotController().install("R1_REPLACEMENT_0"),
            "Could not install alternate physical resistor");
        require(sim.getBoardModificationController().liftLead("R1", "R1.2"),
            "Could not lift alternate physical resistor");
        sim.updateCircuit();
        ProbeTarget replacementLead = hitLead(sim, renderer, "R1.2");
        require(!previousPartLead.isSameTarget(replacementLead) && previousPartLead != replacementLead,
            "Probe identity followed the R1 slot to another physical resistor");
        sim.instrumentController.activateDcVoltageModeForDeveloperVerification();
        placeProbes(sim, previousPartLead, replacementLead);
        require("--- V".equals(sim.instrumentController.getReadingForDeveloperVerification()),
            "Invalid physical probe was not cleared before another DC measurement");
        sim.setBoardPowerState(BoardPowerState.UNPOWERED);
        require(sim.getResistorSlotController().removeInstalledPart(),
            "Could not remove alternate physical resistor");
        require(sim.getResistorSlotController().install("R1_REPLACEMENT_1"),
            "Could not reinstall correct physical resistor");
        sim.setBoardPowerState(BoardPowerState.POWERED);
        settle(sim);
    }

    private static double measureDisplayedDc(CirSim sim, ProbeTarget red, ProbeTarget black) {
        sim.instrumentController.activateDcVoltageModeForDeveloperVerification();
        placeProbes(sim, red, black);
        return sim.instrumentController.getLatestDcVoltageForDeveloperVerification();
    }

    private static void placeProbes(CirSim sim, ProbeTarget red, ProbeTarget black) {
        sim.instrumentController.handlePointerInput(NativeEvent.BUTTON_LEFT, red);
        sim.instrumentController.handlePointerInput(NativeEvent.BUTTON_RIGHT, black);
    }

    private static ProbeTarget hitPad(CirSim sim, PcbWorkbenchRenderer renderer, String padId) {
        Point point = renderer.getPadPoint(padId);
        ProbeTarget target = sim.pcbWorkbenchController.findProbeTarget(point.x, point.y);
        require(target instanceof BoardPadProbeTarget, "PCB pad hit did not resolve: " + padId);
        return target;
    }

    private static ProbeTarget hitLead(CirSim sim, PcbWorkbenchRenderer renderer, String padId) {
        Point point = renderer.getComponentLeadPoint("R1", padId);
        ProbeTarget target = sim.pcbWorkbenchController.findProbeTarget(point.x, point.y);
        require(target instanceof ComponentLeadProbeTarget, "PCB lead hit did not resolve: " + padId);
        return target;
    }

    private static void requireDisplayVoltage(CirSim sim, double expected, double tolerance,
            String message) {
        requireApproximately(expected, sim.instrumentController.getLatestDcVoltageForDeveloperVerification(),
            tolerance, message);
        String reading = sim.instrumentController.getReadingForDeveloperVerification();
        require(!"--- V".equals(reading) && reading.endsWith("V"),
            message + " did not update the visible DC reading: " + reading);
        require(sim.isActiveMeasurementSolverRestoredForDeveloperVerification(),
            message + " left temporary meter elements in the solver");
    }

    private static void requireLiftedResistance(CirSim sim, GeneratedBoardInstance instance,
            ProbeTarget red, ProbeTarget black, double expected, double tolerance, String phase) {
        double actual = sim.instrumentController.getLatestResistanceReadingForDeveloperVerification();
        if (Double.isNaN(actual) || Math.abs(expected - actual) > tolerance)
            throw new IllegalStateException("Lifted resistance " + phase + " expected=" + expected +
                " actual=" + actual + " label=" +
                sim.instrumentController.getReadingForDeveloperVerification() + " installed=" +
                instance.getR1Slot().getInstalledPart().getId() + " state=" +
                sim.getBoardModificationController().getComponentState("R1") + " red=" +
                describeTarget(sim, instance, red) + " black=" +
                describeTarget(sim, instance, black) + " mode=" +
                sim.instrumentController.getActiveModeForDeveloperVerification() + " pending=" +
                sim.instrumentController.isResistanceRefreshPendingForDeveloperVerification() +
                " powered=" + sim.getBoardPowerController().getState() + " overlay=" +
                sim.activeMeasurementOverlay + " attachments=" +
                sim.elmList.contains(instance.getConnectionBindings().get("R1", "R1.1")
                    .getConnectionElement()) + "/" + sim.elmList.contains(instance.getConnectionBindings()
                    .get("R1", "R1.2").getConnectionElement()));
    }

    private static String describeTarget(CirSim sim, GeneratedBoardInstance instance,
            ProbeTarget target) {
        String description = target == null ? "null" : target.getClass().getName() +
            " valid=" + target.isValid() + " marker=" + target.getMarkerPoint();
        if (target instanceof ComponentLeadProbeTarget) {
            ComponentLeadProbeTarget lead = (ComponentLeadProbeTarget) target;
            description += " part=" + lead.getPhysicalPartIdForDeveloperVerification() +
                " component=" + lead.getComponentIdForDeveloperVerification() + " pad=" +
                lead.getPadIdForDeveloperVerification();
        }
        CircuitMeasurementEndpoint endpoint = target == null ? null : target.getMeasurementEndpoint();
        description += " endpoint=" + endpoint;
        if (endpoint instanceof CircuitPostMeasurementEndpoint) {
            CircuitPostMeasurementEndpoint post = (CircuitPostMeasurementEndpoint) endpoint;
            CircuitElm element = post.getElement();
            description += " elm=" + element.getClass().getName() + "@" +
                System.identityHashCode(element) + " post=" + post.getPostIndex() +
                " installedTerminal=" + belongsToInstalledPart(instance, element);
        }
        return description;
    }

    private static boolean belongsToInstalledPart(GeneratedBoardInstance instance, CircuitElm element) {
        if (instance.getR1Slot().isEmpty())
            return false;
        PhysicalResistorPart part = instance.getR1Slot().getInstalledPart();
		CircuitMeasurementEndpoint first = part.getPublicTerminal(0);
		CircuitMeasurementEndpoint second = part.getPublicTerminal(1);
		return (first instanceof CircuitPostMeasurementEndpoint &&
			((CircuitPostMeasurementEndpoint) first).getElement() == element) ||
			(second instanceof CircuitPostMeasurementEndpoint &&
			((CircuitPostMeasurementEndpoint) second).getElement() == element);
    }

    private static void settle(CirSim sim) {
        sim.analyzeCircuit();
        sim.runCircuit(true);
        sim.runCircuit(true);
        sim.verifyGeneratedBoard();
    }

    private static double getLedCurrent(GeneratedBoardInstance instance) {
        return Math.abs(((LEDElm) instance.getComponentBindings().getSingleElement("LED1")).getCurrent());
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