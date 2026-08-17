package com.lushprojects.circuitjs1.client;

import java.util.Vector;

import com.google.gwt.dom.client.NativeEvent;

/** URL-gated regression check for the production active-resistance transaction. */
class ResistanceMeasurementDeveloperVerifier {
    private static String diodeForwardSummary;
    private static double healthyLedCurrent;

    static void verify(CirSim sim) {
        if (sim.getGeneratedChallengeController() != null) {
            MeterLifecycleDeveloperVerifier.verify(sim);
            return;
        }
        GeneratedBoardInstance instance = sim.getGeneratedBoardInstance();
        if (instance == null)
            throw new IllegalStateException("Resistance verification requires a generated board");

        verifyGeneratedPhysicalSpecifications();

        TroubleshootBoard board = instance.getBoard();
        BoardPad j11 = board.getPad("J1.1");
        BoardPad r11 = board.getPad("R1.1");
        BoardPad r12 = board.getPad("R1.2");
        BoardPad led1k = board.getPad("LED1.K");
        BoardNet vin = board.getNet(j11.getNetId());
        BoardNet resistorOut = board.getNet(r12.getNetId());
        CircuitPostProbeTarget j11Probe = getProbe(sim, instance, "J1.1");
        CircuitPostProbeTarget r11Probe = getProbe(sim, instance, "R1.1");
        CircuitPostProbeTarget r12Probe = getProbe(sim, instance, "R1.2");
        CircuitPostProbeTarget led1aProbe = getProbe(sim, instance, "LED1.A");
        CircuitPostProbeTarget led1kProbe = getProbe(sim, instance, "LED1.K");
        CircuitPostProbeTarget j12Probe = getProbe(sim, instance, "J1.2");
        sim.setBoardPowerState(BoardPowerState.POWERED);
        sim.instrumentController.setResistanceProbesForDeveloperVerification(r11Probe, r12Probe);
        require("POWER OFF".equals(sim.instrumentController.getReadingForDeveloperVerification()),
            "Powered resistance readout was not blocked");

        sim.setBoardPowerState(BoardPowerState.UNPOWERED);
		sim.updateCircuit();
        Vector<CircuitElm> elementsBefore = new Vector<CircuitElm>(sim.elmList);
        String exportBefore = sim.dumpCircuit();
        int undoBefore = sim.undoStack.size();
        int redoBefore = sim.redoStack.size();
        boolean unsavedBefore = sim.unsavedChanges;
        measure(sim, r11Probe, r12Probe, 680, 2);
        measure(sim, r12Probe, r11Probe, 680, 2);
        measure(sim, j11Probe, r11Probe, 0, .001);
        sim.instrumentController.setResistanceProbesForDeveloperVerification(r11Probe, led1kProbe);
        require("OL".equals(sim.instrumentController.getReadingForDeveloperVerification()),
            "Open resistance path did not display OL");

        verifyNoRefreshFromDrawCycle(sim);
        verifyAutomaticRefreshAfterAnalysis(sim, r11Probe, r12Probe);
        verifyOccupiedFormerMidpoint(sim, r11Probe, r12Probe);
        verifyInvalidProbeClearsReading(sim, r11Probe, r12Probe);
        sim.instrumentController.clearTargets();
        require("--- Ohm".equals(sim.instrumentController.getReadingForDeveloperVerification()),
            "Missing OHM probes did not display the OHM placeholder");
        measure(sim, r11Probe, r12Probe, 680, 2);
        verifyLegacyCircuitIsBlocked(sim, instance, r11Probe, r12Probe);

        verifyContinuity(sim, instance, j11Probe, r11Probe, r12Probe, led1kProbe);
        verifyDiode(sim, instance, j11Probe, r11Probe, r12Probe, led1aProbe, led1kProbe,
            j12Probe);
        verifySemanticPointerClicks(sim, j11Probe, r11Probe, j12Probe, led1aProbe, led1kProbe);

        sim.requestPowerOnDuringActiveMeasurementForDeveloperVerification();
        verifyQueuedPowerOnFinalState(sim, instance, j11Probe, j12Probe, r11Probe, r12Probe);
        sim.setBoardPowerState(BoardPowerState.UNPOWERED);
        sim.updateCircuit();
        measure(sim, r11Probe, r12Probe, 680, 2);

        require(sim.elmList.equals(elementsBefore), "Temporary stimulus elements remained installed");
        require(exportBefore.equals(sim.dumpCircuit()), "Circuit export changed after measurement");
        require(undoBefore == sim.undoStack.size() && redoBefore == sim.redoStack.size(),
            "Undo or redo history changed after measurement");
        require(unsavedBefore == sim.unsavedChanges, "Unsaved state changed after measurement");
        require(board.getPad("J1.1") == j11 && board.getPad("R1.1") == r11 &&
            board.getPad("R1.2") == r12 && board.getPad("LED1.K") == led1k,
            "Board pad identity changed after measurement");
        require(board.getNet(j11.getNetId()) == vin && board.getNet(r12.getNetId()) == resistorOut,
            "Board net identity changed after measurement");
        require(sim.getBoardPowerController().isElectricallyUnpowered(),
            "External board power was not left electrically off");
        require(r11Probe.isValid() && r12Probe.isValid(), "Probe targets did not remain valid");
        sim.analyzeCircuit();
        sim.runCircuit(true);
        sim.runCircuit(true);
        sim.verifyGeneratedBoard();
        measure(sim, r11Probe, r12Probe, 680, 2);
        verifyComponentIsolation(sim, instance, r11Probe, r12Probe);
        verifyPcbWorkbench(sim, instance);
        sim.setCircuitTitle("Resistance verification passed; diode " + diodeForwardSummary +
            "; LED " + healthyLedCurrent + " A");
    }

    private static void verifyComponentIsolation(CirSim sim, GeneratedBoardInstance instance,
            CircuitPostProbeTarget r11Probe, CircuitPostProbeTarget r12Probe) {
    BoardModificationController modifications = sim.getBoardModificationController();
    GeneratedComponentConnectionBindings connections = instance.getConnectionBindings();
    GeneratedComponentConnectionBinding r11 = connections.get("R1", "R1.1");
    GeneratedComponentConnectionBinding r12 = connections.get("R1", "R1.2");
    CircuitPostProbeTarget componentLead1 = getProbe(sim, r11.getComponentEndpoint());
    CircuitPostProbeTarget componentLead2 = getProbe(sim, r12.getComponentEndpoint());
    Vector<CircuitElm> canonicalElements = instance.getSimulationElements();
    String canonicalExport = sim.dumpCircuit();
    verifyModificationLookupRejections(sim, modifications);
    require(modifications.getComponentState("R1") == ComponentPhysicalState.INSTALLED,
        "R1 did not begin installed");
    measure(sim, componentLead1, componentLead2, 680, 2);
    require(modifications.liftLead("R1", "R1.1"), "Initial R1 lead lift was ignored");
    require(!modifications.liftLead("R1", "R1.1"), "Repeated R1 lead lift was not idempotent");
    require(modifications.getComponentState("R1") == ComponentPhysicalState.LEAD_LIFTED,
        "One lifted R1 lead was treated as removal");
    sim.updateCircuit();
    sim.instrumentController.setResistanceProbesForDeveloperVerification(r11Probe, componentLead1);
    require("OL".equals(sim.instrumentController.getReadingForDeveloperVerification()),
        "Lifted R1 lead did not isolate the persistent board pad");
    measure(sim, componentLead1, componentLead2, 680, 2);
    require(modifications.reconnectLead("R1", "R1.1"), "R1 lead reconnect was ignored");
    require(!modifications.reconnectLead("R1", "R1.1"),
        "Repeated R1 reconnect was not idempotent");
    sim.updateCircuit();
    require(modifications.getComponentState("R1") == ComponentPhysicalState.INSTALLED,
        "Reconnecting R1 did not restore installed state");
    require(sim.elmList.equals(canonicalElements),
        "R1 reconnect did not restore canonical generated-element order");
    require(canonicalExport.equals(sim.dumpCircuit()),
        "R1 reconnect did not restore the exact circuit export");
    measure(sim, r11Probe, r12Probe, 680, 2);

    sim.elmList.add(r11.getConnectionElement());
    boolean duplicateRejected = false;
    try {
        modifications.verifyStructuralState();
    } catch (IllegalStateException expected) {
        duplicateRejected = true;
    }

    require(duplicateRejected, "Structural verification accepted a duplicate detachable element");
    sim.elmList.removeElementAt(sim.elmList.lastIndexOf(r11.getConnectionElement()));
    modifications.verifyStructuralState();

    modifications.liftLead("R1", "R1.1");
    modifications.removeComponent("R1");
    require(modifications.getComponentState("R1") == ComponentPhysicalState.REMOVED,
        "Removing lead-lifted R1 did not disconnect every lead");
    require(!modifications.removeComponent("R1"), "Repeated R1 removal was not idempotent");
    require(!sim.elmList.contains(r11.getConnectionElement()) &&
        !sim.elmList.contains(r12.getConnectionElement()),
        "Removing R1 left a detachable lead in the graph");
    measure(sim, componentLead1, componentLead2, 680, 2);
    sim.setBoardPowerState(BoardPowerState.POWERED);
    sim.updateCircuit();
    requireApproximately(9,
        sim.instrumentController.getDcVoltageDifferenceForDeveloperVerification(r11Probe,
            getProbe(sim, instance, "J1.2")), .1,
        "Powered removed board did not retain VIN");
    LEDElm led = (LEDElm) instance.getComponentBindings().getSingleElement("LED1");
    require(Math.abs(led.getCurrent()) < .000001,
        "Removed R1 allowed unexpected LED current: " + led.getCurrent());
    sim.verifyGeneratedBoard();
    boolean poweredRejected = false;
    try {
        modifications.restoreComponent("R1");
    } catch (BoardModificationRejectedException e) {
        poweredRejected = true;
    }
    require(poweredRejected && modifications.getComponentState("R1") ==
            ComponentPhysicalState.REMOVED,
        "Powered component restoration changed the board");
    sim.setBoardPowerState(BoardPowerState.UNPOWERED);
    modifications.restoreComponent("R1");
    require(!modifications.restoreComponent("R1"), "Repeated R1 restore was not idempotent");
    sim.updateCircuit();
    require(modifications.getComponentState("R1") == ComponentPhysicalState.INSTALLED,
        "Restoring R1 did not reconnect every lead");
    require(sim.elmList.equals(canonicalElements),
        "R1 restore did not reproduce canonical generated-element order");
    require(canonicalExport.equals(sim.dumpCircuit()),
        "R1 restore did not reproduce the exact circuit export");
    sim.verifyGeneratedBoard();
    measure(sim, r11Probe, r12Probe, 680, 2);
    sim.setBoardPowerState(BoardPowerState.POWERED);
    sim.updateCircuit();
    healthyLedCurrent = ((LEDElm) instance.getComponentBindings().getSingleElement("LED1"))
        .getCurrent();
    require(healthyLedCurrent >= .005 && healthyLedCurrent <= .015,
        "Restored R1 did not return LED current to the healthy range: " + healthyLedCurrent);
    sim.verifyGeneratedBoard();
    sim.setBoardPowerState(BoardPowerState.UNPOWERED);
    sim.updateCircuit();
    measure(sim, r11Probe, r12Probe, 680, 2);
    }

    private static void verifyGeneratedPhysicalSpecifications() {
        verifyPhysicalSpecificationRejections();
        verifyGeneratedPhysicalSpecification(0, 5, 330, new ResistorColorBand[] {
            ResistorColorBand.ORANGE, ResistorColorBand.ORANGE, ResistorColorBand.BROWN,
            ResistorColorBand.GOLD });
        verifyGeneratedPhysicalSpecification(2, 9, 680, new ResistorColorBand[] {
            ResistorColorBand.BLUE, ResistorColorBand.GRAY, ResistorColorBand.BROWN,
            ResistorColorBand.GOLD });
        verifyGeneratedPhysicalSpecification(3, 12, 1000, new ResistorColorBand[] {
            ResistorColorBand.BROWN, ResistorColorBand.BLACK, ResistorColorBand.RED,
            ResistorColorBand.GOLD });
    }

    private static void verifyPhysicalSpecificationRejections() {
        BoardPhysicalSpecifications specifications = new BoardPhysicalSpecifications();
        StandardPhysicalDefinitionProviders.RESISTOR.add(specifications,
            new ResistorNameplate("R1", 330, 5));
        StandardPhysicalDefinitionProviders.RESISTOR.add(specifications,
            new ResistorNameplate("R2", 680, 5));
        requireBands(new ResistorColorBand[] { ResistorColorBand.ORANGE,
            ResistorColorBand.ORANGE, ResistorColorBand.BROWN, ResistorColorBand.GOLD },
            ResistorColorCode.getFourBandCode(StandardPhysicalDefinitionProviders.RESISTOR
                .require(specifications, "R1")), -1);
        requireBands(new ResistorColorBand[] { ResistorColorBand.BLUE, ResistorColorBand.GRAY,
            ResistorColorBand.BROWN, ResistorColorBand.GOLD },
            ResistorColorCode.getFourBandCode(StandardPhysicalDefinitionProviders.RESISTOR
                .require(specifications, "R2")), -2);
        requireColorCodeRejected(332);
        requireColorCodeRejected(101);
        requireInvalidResistorNameplate(Double.NaN, 5);
        requireInvalidResistorNameplate(Double.POSITIVE_INFINITY, 5);
        requireInvalidResistorNameplate(Double.NEGATIVE_INFINITY, 5);
        requireInvalidResistorNameplate(0, 5);
        requireInvalidResistorNameplate(-1, 5);
        requireInvalidResistorNameplate(330, Double.NaN);
        requireInvalidResistorNameplate(330, Double.POSITIVE_INFINITY);
        requireInvalidResistorNameplate(330, Double.NEGATIVE_INFINITY);
        requireInvalidResistorNameplate(330, 0);
        requireInvalidResistorNameplate(330, -1);
        requireInvalidPowerInputNameplate(Double.NaN);
        requireInvalidPowerInputNameplate(Double.POSITIVE_INFINITY);
        requireInvalidPowerInputNameplate(Double.NEGATIVE_INFINITY);
        requireInvalidPowerInputNameplate(0);
        requireInvalidPowerInputNameplate(-1);
    }

    private static void requireColorCodeRejected(double resistance) {
        boolean rejected = false;
        try {
            ResistorColorCode.getFourBandCode(new ResistorNameplate("R1", resistance, 5));
        } catch (IllegalArgumentException expected) {
            rejected = true;
        }
        require(rejected, "Non-representable four-band resistance was encoded: " + resistance);
    }

    private static void requireInvalidResistorNameplate(double resistance, double tolerance) {
        boolean rejected = false;
        try {
            new ResistorNameplate("R1", resistance, tolerance);
        } catch (IllegalArgumentException expected) {
            rejected = true;
        }
        require(rejected, "Invalid resistor nameplate was accepted");
    }

    private static void requireInvalidPowerInputNameplate(double voltage) {
        boolean rejected = false;
        try {
            new PowerInputNameplate("VIN_INPUT", voltage);
        } catch (IllegalArgumentException expected) {
            rejected = true;
        }
        require(rejected, "Invalid power input nameplate was accepted");
    }

    private static void verifyGeneratedPhysicalSpecification(long seed, double voltage,
            double resistance, ResistorColorBand[] expectedBands) {
        GeneratedBoardInstance instance = new LedIndicatorGenerator().generate(seed);
        ResistorNameplate resistorNameplate = StandardPhysicalDefinitionProviders.RESISTOR.require(
            instance.getPhysicalSpecifications(), "R1");
        PowerInputNameplate inputNameplate = instance.getPhysicalSpecifications()
            .getPowerInputNameplate("VIN_INPUT");
        require(resistorNameplate != null && inputNameplate != null,
            "Generated physical specifications are incomplete for seed " + seed);
        requireApproximately(resistance, resistorNameplate.getNominalResistanceOhms(), 0,
            "Resistor nameplate differs from generated value for seed " + seed);
        requireApproximately(voltage, inputNameplate.getNominalVoltage(), 0,
            "Input nameplate differs from generated value for seed " + seed);
        require(("+" + (long) voltage + "V").equals(inputNameplate.getDisplayLabel()),
            "Input label differs from generated value for seed " + seed);
        requireBands(expectedBands, ResistorColorCode.getFourBandCode(resistorNameplate), seed);
        CircuitElm resistor = instance.getComponentBindings().getSingleElement("R1");
        require(resistor instanceof ResistorElm &&
            ((ResistorElm) resistor).getResistance() == resistance,
            "CircuitJS resistor differs from nameplate for seed " + seed);
        boolean sourceMatches = false;
        for (CircuitElm element : instance.getSimulationElements()) {
            if (element instanceof DCVoltageElm && ((DCVoltageElm) element).maxVoltage == voltage)
                sourceMatches = true;
        }
        require(sourceMatches, "CircuitJS supply differs from nameplate for seed " + seed);
    }

    private static void requireBands(ResistorColorBand[] expected, ResistorColorBand[] actual,
            long seed) {
        require(expected.length == actual.length, "Band count differs for seed " + seed);
        for (int index = 0; index < expected.length; index++)
            require(expected[index] == actual[index], "Band " + index + " differs for seed " + seed);
    }

    private static void verifyModificationLookupRejections(CirSim sim,
            BoardModificationController modifications) {
        Vector<CircuitElm> graphBefore = new Vector<CircuitElm>(sim.elmList);
        boolean unknownComponentRejected = false;
        boolean unknownPadRejected = false;
        boolean noConnectionsRejected = false;
        try {
            modifications.liftLead("UNKNOWN", "R1.1");
        } catch (IllegalArgumentException expected) {
            unknownComponentRejected = true;
        }
        try {
            modifications.liftLead("R1", "UNKNOWN");
        } catch (IllegalArgumentException expected) {
            unknownPadRejected = true;
        }
        try {
            modifications.removeComponent("J1");
        } catch (IllegalArgumentException expected) {
            noConnectionsRejected = true;
        }
        require(unknownComponentRejected && unknownPadRejected && noConnectionsRejected,
            "Modification lookup accepted an unknown ID or component without connections");
        require(sim.elmList.equals(graphBefore),
            "Rejected modification lookup changed the simulation graph");
    }

    private static void verifyPcbWorkbench(CirSim sim, GeneratedBoardInstance instance) {
        if (sim.pcbWorkbenchController == null)
            return;
        PcbWorkbenchRenderer renderer = sim.pcbWorkbenchController.getRenderer();
        require("+9V".equals(renderer.getPowerInputLabelForDeveloperVerification()),
            "PCB input marking did not use its nameplate");
        requireBands(new ResistorColorBand[] { ResistorColorBand.BLUE, ResistorColorBand.GRAY,
            ResistorColorBand.BROWN, ResistorColorBand.GOLD }, getDeveloperResistorBands(instance),
            instance.getSeed());
        renderer.setSelectedComponentId("R1");
        sim.pcbWorkbenchController.refresh();
        String panelText = sim.pcbWorkbenchController.getPanelTextForDeveloperVerification();
        require(panelText.contains("R1") && panelText.contains("Type: resistor") &&
            panelText.contains("State: Installed") && panelText.contains("Markings: Color bands") &&
            !panelText.contains("Value: 680 Ohm +/-5%"),
            "PCB component panel exposed the original resistor value or omitted its markings");
        ProbeTarget r11 = hitPcbPad(sim, renderer, "R1.1");
        ProbeTarget r12 = hitPcbPad(sim, renderer, "R1.2");
        sim.instrumentController.setResistanceProbesForDeveloperVerification(r11, r12);
        requireApproximately(680,
            sim.instrumentController.getLatestResistanceReadingForDeveloperVerification(), 2,
            "PCB R1 pad measurement was not approximately 680 Ohm");
        int measurementCount =
            sim.instrumentController.getResistanceMeasurementCountForDeveloperVerification();
        clickPcbTarget(sim, NativeEvent.BUTTON_LEFT, renderer.getPadPoint("R1.1"));
        clickPcbTarget(sim, NativeEvent.BUTTON_RIGHT, renderer.getPadPoint("R1.2"));
        require(measurementCount ==
            sim.instrumentController.getResistanceMeasurementCountForDeveloperVerification(),
            "Same physical PCB pad clicks remeasured");

        BoardModificationController modifications = sim.getBoardModificationController();
        modifications.liftLead("R1", "R1.1");
        sim.updateCircuit();
        requireBands(new ResistorColorBand[] { ResistorColorBand.BLUE, ResistorColorBand.GRAY,
            ResistorColorBand.BROWN, ResistorColorBand.GOLD }, getDeveloperResistorBands(instance),
            instance.getSeed());
        require(r11.isValid() && r12.isValid(),
            "PCB pad targets did not survive lead lift and reanalysis");
        ProbeTarget componentLead1 = hitPcbLead(sim, renderer, "R1", "R1.1");
        GeneratedComponentConnectionBinding r12Binding =
            instance.getConnectionBindings().get("R1", "R1.2");
        ProbeTarget componentLead2 = new ComponentLeadProbeTarget(sim, instance, "R1", "R1.2",
            renderer);
        sim.instrumentController.setResistanceProbesForDeveloperVerification(r11, componentLead1);
        require("OL".equals(sim.instrumentController.getReadingForDeveloperVerification()),
            "Lifted PCB lead did not open the board-to-component path");
        sim.instrumentController.setContinuityProbesForDeveloperVerification(r11, r12);
        require("OL".equals(sim.instrumentController.getReadingForDeveloperVerification()) &&
                !sim.instrumentController.isContinuityDetectedForDeveloperVerification(),
            "Open PCB board path produced continuity");
        sim.instrumentController.setResistanceProbesForDeveloperVerification(componentLead1,
            componentLead2);
        requireApproximately(680,
            sim.instrumentController.getLatestResistanceReadingForDeveloperVerification(), 2,
            "Lifted PCB component lead measurement was not approximately 680 Ohm");
        require(r12Binding.getComponentEndpoint() == componentLead2.getMeasurementEndpoint(),
            "Component-side PCB target did not resolve through its declared binding");

        modifications.removeComponent("R1");
        sim.updateCircuit();
        requireBands(new ResistorColorBand[] { ResistorColorBand.BLUE, ResistorColorBand.GRAY,
            ResistorColorBand.BROWN, ResistorColorBand.GOLD }, getDeveloperResistorBands(instance),
            instance.getSeed());
        require(r11.isValid() && r12.isValid(),
            "PCB pad targets did not survive component removal and reanalysis");
        componentLead1 = hitPcbLead(sim, renderer, "R1", "R1.1");
        componentLead2 = hitPcbLead(sim, renderer, "R1", "R1.2");
        sim.instrumentController.setResistanceProbesForDeveloperVerification(componentLead1,
            componentLead2);
        requireApproximately(680,
            sim.instrumentController.getLatestResistanceReadingForDeveloperVerification(), 2,
            "Removed tray resistor did not measure approximately 680 Ohm");

        modifications.restoreComponent("R1");
        sim.updateCircuit();
        requireBands(new ResistorColorBand[] { ResistorColorBand.BLUE, ResistorColorBand.GRAY,
            ResistorColorBand.BROWN, ResistorColorBand.GOLD }, getDeveloperResistorBands(instance),
            instance.getSeed());
        sim.instrumentController.setResistanceProbesForDeveloperVerification(r11, r12);
        requireApproximately(680,
            sim.instrumentController.getLatestResistanceReadingForDeveloperVerification(), 2,
            "Restored PCB pad measurement was not approximately 680 Ohm");
    }

    private static ProbeTarget hitPcbPad(CirSim sim, PcbWorkbenchRenderer renderer, String padId) {
        Point point = renderer.getPadPoint(padId);
        ProbeTarget target = sim.pcbWorkbenchController.findProbeTarget(point.x, point.y);
        require(target instanceof BoardPadProbeTarget,
            "PCB pad hit test did not produce a board-pad target: " + padId);
        return target;
    }

    private static ProbeTarget hitPcbLead(CirSim sim, PcbWorkbenchRenderer renderer,
            String componentId, String padId) {
        Point point = renderer.getComponentLeadPoint(componentId, padId);
        ProbeTarget target = sim.pcbWorkbenchController.findProbeTarget(point.x, point.y);
        require(target instanceof ComponentLeadProbeTarget,
            "PCB component lead hit test did not produce a lead target: " + padId);
        return target;
    }

    private static void clickPcbTarget(CirSim sim, int button, Point point) {
        sim.instrumentController.handlePointerInput(button,
            sim.pcbWorkbenchController.findProbeTarget(point.x, point.y));
    }

    private static CircuitPostProbeTarget getProbe(CirSim sim, GeneratedBoardInstance instance,
            String padId) {
	return getProbe(sim, instance.getSimulationBindings().getEndpoint(padId));
    }

    private static CircuitPostProbeTarget getProbe(CirSim sim, CircuitMeasurementEndpoint endpoint) {
        if (!(endpoint instanceof CircuitPostMeasurementEndpoint))
            throw new IllegalStateException("Missing circuit post measurement binding");
        CircuitPostMeasurementEndpoint post = (CircuitPostMeasurementEndpoint) endpoint;
        return new CircuitPostProbeTarget(sim, post.getElement(), post.getPostIndex());
    }

    private static void measure(CirSim sim, ProbeTarget red, ProbeTarget black, double expected,
            double tolerance) {
        sim.instrumentController.setResistanceProbesForDeveloperVerification(red, black);
        String reading = sim.instrumentController.getReadingForDeveloperVerification();
        if ("OL".equals(reading) || "POWER OFF".equals(reading))
            throw new IllegalStateException("Expected " + expected + " Ohm, got " + reading);
        double actual = sim.instrumentController.getLatestResistanceReadingForDeveloperVerification();
        require(Math.abs(actual - expected) <= tolerance,
            "Expected " + expected + " Ohm, got " + actual + " Ohm");
        require(sim.isActiveMeasurementSolverRestoredForDeveloperVerification(),
            "Resistance transaction did not restore the normal analyzed solver graph");
    }

    private static void verifyNoRefreshFromDrawCycle(CirSim sim) {
        int measurementsBefore = sim.instrumentController.getResistanceMeasurementCountForDeveloperVerification();
        sim.updateCircuit();
        sim.updateCircuit();
        require(measurementsBefore == sim.instrumentController.getResistanceMeasurementCountForDeveloperVerification(),
            "Repeated update/draw cycles started another resistance transaction");
    }

    private static void verifyAutomaticRefreshAfterAnalysis(CirSim sim, ProbeTarget red,
            ProbeTarget black) {
        sim.instrumentController.setResistanceProbesForDeveloperVerification(red, black);
        int measurementsBefore = sim.instrumentController.getResistanceMeasurementCountForDeveloperVerification();
        sim.needAnalyze();
        require("--- Ohm".equals(sim.instrumentController.getReadingForDeveloperVerification()),
            "Topology invalidation did not clear the cached OHM reading");
        sim.updateCircuit();
        int measurementsAfter = sim.instrumentController.getResistanceMeasurementCountForDeveloperVerification();
        require(measurementsBefore + 1 == measurementsAfter,
            "Post-analysis OHM refresh count was " + measurementsAfter +
            " after " + measurementsBefore + " prior transactions");
        require("680 Ohm".equals(sim.instrumentController.getReadingForDeveloperVerification()),
            "Post-analysis OHM refresh did not restore the expected reading");
    }

    private static void verifyOccupiedFormerMidpoint(CirSim sim, CircuitPostProbeTarget red,
            CircuitPostProbeTarget black) {
        Point redPoint = red.getMarkerPoint();
        GroundElm occupiedFormerMidpoint = new GroundElm(redPoint.x, redPoint.y + 64);
        occupiedFormerMidpoint.drag(redPoint.x, redPoint.y + 96);
        occupiedFormerMidpoint.setBbox(redPoint.x, redPoint.y + 64,
            redPoint.x, redPoint.y + 96);
        sim.elmList.add(occupiedFormerMidpoint);
        sim.needAnalyze();
        sim.updateCircuit();
        require("680 Ohm".equals(sim.instrumentController.getReadingForDeveloperVerification()),
            "Occupied former midpoint altered the resistance reading");
        require(sim.isActiveMeasurementSolverRestoredForDeveloperVerification(),
            "Collision test left temporary elements in the solver graph");
        sim.elmList.remove(occupiedFormerMidpoint);
        sim.needAnalyze();
        sim.updateCircuit();
    }

    private static void verifyInvalidProbeClearsReading(CirSim sim, CircuitPostProbeTarget red,
            CircuitPostProbeTarget black) {
        int elementIndex = sim.elmList.indexOf(red.getElement());
        sim.elmList.remove(red.getElement());
        sim.needAnalyze();
        require("--- Ohm".equals(sim.instrumentController.getReadingForDeveloperVerification()),
            "Removing a probed element did not clear the cached OHM reading");
        sim.updateCircuit();
        require("--- Ohm".equals(sim.instrumentController.getReadingForDeveloperVerification()),
            "Invalid probe retained a stale OHM reading after analysis");
        sim.elmList.add(elementIndex, red.getElement());
        sim.needAnalyze();
        sim.updateCircuit();
        require("--- Ohm".equals(sim.instrumentController.getReadingForDeveloperVerification()),
            "Restoring an element silently restored a cleared probe or stale OHM reading");
    }

    private static void verifyLegacyCircuitIsBlocked(CirSim sim, GeneratedBoardInstance instance,
            ProbeTarget red, ProbeTarget black) {
        sim.getBoardPowerController().detach();
        sim.instrumentController.setResistanceProbesForDeveloperVerification(red, black);
        require("POWER OFF".equals(sim.instrumentController.getReadingForDeveloperVerification()),
            "Legacy CircuitJS graph was allowed to install an active measurement");
        sim.getBoardPowerController().attach(instance.getExternalPowerBindings());
        sim.setBoardPowerState(BoardPowerState.UNPOWERED);
        sim.updateCircuit();
    }

    private static void verifyContinuity(CirSim sim, GeneratedBoardInstance instance,
            CircuitPostProbeTarget j11Probe, CircuitPostProbeTarget r11Probe,
            CircuitPostProbeTarget r12Probe, CircuitPostProbeTarget led1kProbe) {
        verifyContinuityResult(sim, j11Probe, r11Probe, 0, .001, true);
        verifyContinuityResult(sim, r11Probe, r12Probe, 680, 2, false);
        verifyContinuityResult(sim, r12Probe, r11Probe, 680, 2, false);
        sim.instrumentController.setContinuityProbesForDeveloperVerification(r11Probe, led1kProbe);
        require("OL".equals(sim.instrumentController.getReadingForDeveloperVerification()),
            "Open continuity path did not display OL");
        requireContinuityInactive(sim, "Open continuity path left feedback active");

        verifyInvalidContinuityProbeClearsFeedback(sim, j11Probe, r11Probe);
        verifyContinuityThresholds(sim, instance, r11Probe, r12Probe);
        verifyContinuityPowerTransition(sim, instance, j11Probe, r11Probe, r12Probe);
        verifyContinuityLegacyBlock(sim, instance, j11Probe, r11Probe);
        verifyContinuityModeSwitching(sim, j11Probe, r11Probe);
        verifyContinuityRepaintBehavior(sim, j11Probe, r11Probe);
    }

    private static void verifyDiode(CirSim sim, GeneratedBoardInstance instance,
            CircuitPostProbeTarget j11Probe, CircuitPostProbeTarget r11Probe,
            CircuitPostProbeTarget r12Probe, CircuitPostProbeTarget led1aProbe,
            CircuitPostProbeTarget led1kProbe, CircuitPostProbeTarget j12Probe) {
        verifyDiodeForwardLed(sim, led1aProbe, led1kProbe);
        verifyDiodeReverseAndShort(sim, led1aProbe, led1kProbe, j11Probe, r11Probe);
        verifyDiodeInvalidProbe(sim, led1aProbe, led1kProbe);
        verifyDiodeTopologyAndRepaint(sim, led1aProbe, led1kProbe);
        verifyDiodePowerAndLegacy(sim, instance, led1aProbe, led1kProbe);
        verifyDiodeModeSwitching(sim, j11Probe, r11Probe, j12Probe);
        verifyDiodeQueuedPower(sim, instance, led1aProbe, led1kProbe, j11Probe, j12Probe);
        sim.setBoardPowerState(BoardPowerState.UNPOWERED);
        sim.updateCircuit();
    }

    private static void verifySemanticPointerClicks(CirSim sim, CircuitPostProbeTarget sameNetA,
            CircuitPostProbeTarget sameNetB, CircuitPostProbeTarget differentTarget,
            CircuitPostProbeTarget diodeRed, CircuitPostProbeTarget diodeBlack) {
        CircuitPostProbeTarget canvasSameNetA = getCanvasTarget(sim, sameNetA);
        CircuitPostProbeTarget canvasSameNetB = getCanvasTarget(sim, sameNetB);
        CircuitPostProbeTarget canvasDifferentTarget = getCanvasTarget(sim, differentTarget);
        CircuitPostProbeTarget canvasDiodeRed = getCanvasTarget(sim, diodeRed);
        CircuitPostProbeTarget canvasDiodeBlack = getCanvasTarget(sim, diodeBlack);
        sim.instrumentController.setContinuityProbesForDeveloperVerification(canvasSameNetA,
            canvasSameNetB);
        int continuityMeasurements =
            sim.instrumentController.getResistanceMeasurementCountForDeveloperVerification();
        int prepares = sim.instrumentController.getContinuityFeedbackPrepareCountForDeveloperVerification();
        clickProbe(sim, NativeEvent.BUTTON_LEFT, canvasSameNetA);
        clickProbe(sim, NativeEvent.BUTTON_RIGHT, canvasSameNetB);
        require(continuityMeasurements ==
            sim.instrumentController.getResistanceMeasurementCountForDeveloperVerification(),
            "Same CONT probe clicks ran a resistance transaction");
        require(prepares == sim.instrumentController.getContinuityFeedbackPrepareCountForDeveloperVerification(),
            "Same CONT probe clicks prepared audio");
        clickProbe(sim, NativeEvent.BUTTON_LEFT, canvasDifferentTarget);
        require(continuityMeasurements + 1 ==
            sim.instrumentController.getResistanceMeasurementCountForDeveloperVerification(),
            "Different CONT probe click did not run exactly one resistance transaction");
        require(prepares + 1 == sim.instrumentController.getContinuityFeedbackPrepareCountForDeveloperVerification(),
            "Different CONT probe click did not prepare audio");
        int afterEmptyClick = sim.instrumentController.getResistanceMeasurementCountForDeveloperVerification();
        sim.instrumentController.handlePointerInput(NativeEvent.BUTTON_LEFT, -1, -1);
        require(afterEmptyClick == sim.instrumentController.getResistanceMeasurementCountForDeveloperVerification(),
            "Empty CONT canvas click ran a resistance transaction");

        sim.instrumentController.setDiodeProbesForDeveloperVerification(canvasDiodeRed, canvasDiodeBlack);
        int diodeMeasurements = sim.instrumentController.getDiodeMeasurementCountForDeveloperVerification();
        int diodePrepares = sim.instrumentController.getContinuityFeedbackPrepareCountForDeveloperVerification();
        clickProbe(sim, NativeEvent.BUTTON_LEFT, canvasDiodeRed);
        clickProbe(sim, NativeEvent.BUTTON_RIGHT, canvasDiodeBlack);
        require(diodeMeasurements == sim.instrumentController.getDiodeMeasurementCountForDeveloperVerification(),
            "Same DIODE probe clicks ran a diode transaction");
        require(diodePrepares == sim.instrumentController.getContinuityFeedbackPrepareCountForDeveloperVerification(),
            "DIODE probe click prepared continuity audio");
        clickProbe(sim, NativeEvent.BUTTON_LEFT, canvasDifferentTarget);
        require(diodeMeasurements + 1 == sim.instrumentController.getDiodeMeasurementCountForDeveloperVerification(),
            "Different DIODE probe click did not run exactly one diode transaction");
    }

    private static void clickProbe(CirSim sim, int button, CircuitPostProbeTarget target) {
        Point point = target.getMarkerPoint();
        sim.instrumentController.handlePointerInput(button, sim.transformX(point.x),
            sim.transformY(point.y));
    }

    private static CircuitPostProbeTarget getCanvasTarget(CirSim sim, CircuitPostProbeTarget target) {
        Point point = target.getMarkerPoint();
        CircuitPostProbeTarget canvasTarget = sim.findPostTarget(sim.transformX(point.x),
            sim.transformY(point.y));
        if (canvasTarget == null)
            throw new IllegalStateException("Unable to hit-test persistent probe target");
        return canvasTarget;
    }

    private static void verifyDiodeForwardLed(CirSim sim, ProbeTarget red, ProbeTarget black) {
        sim.instrumentController.setDiodeProbesForDeveloperVerification(red, black);
        double voltage = sim.instrumentController.getLatestDiodeVoltageForDeveloperVerification();
        double current = sim.instrumentController.getLatestDiodeCurrentForDeveloperVerification();
        require(!"OL".equals(sim.instrumentController.getReadingForDeveloperVerification()),
            "Forward LED diode test displayed OL");
        require(voltage > .5 && voltage < InstrumentController.DIODE_COMPLIANCE_THRESHOLD,
            "Forward LED diode voltage was not defensible: " + voltage);
        require(current >= InstrumentController.DIODE_MINIMUM_CURRENT,
            "Forward LED diode current was not meaningful: " + current);
        diodeForwardSummary = voltage + " V at " + current + " A";
        require(sim.isActiveMeasurementSolverRestoredForDeveloperVerification(),
            "Forward diode transaction did not restore the normal solver graph");
    }

    private static void verifyDiodeReverseAndShort(CirSim sim, ProbeTarget ledAnode,
            ProbeTarget ledCathode, ProbeTarget sameNetA, ProbeTarget sameNetB) {
        sim.instrumentController.setDiodeProbesForDeveloperVerification(ledCathode, ledAnode);
        require("OL".equals(sim.instrumentController.getReadingForDeveloperVerification()),
            "Reverse LED diode test was not OL");
        double reverseVoltage = sim.instrumentController.getLatestDiodeVoltageForDeveloperVerification();
        double reverseCurrent = sim.instrumentController.getLatestDiodeCurrentForDeveloperVerification();
        require(Double.isNaN(reverseVoltage) && !Double.isNaN(reverseCurrent) &&
            !Double.isInfinite(reverseCurrent) &&
            reverseCurrent < InstrumentController.DIODE_MINIMUM_CURRENT,
            "Reverse diode solve did not meet OL conditions");
        sim.instrumentController.setDiodeProbesForDeveloperVerification(sameNetA, sameNetB);
        requireApproximately(0, sim.instrumentController.getLatestDiodeVoltageForDeveloperVerification(), .001,
            "Same-node diode test was not approximately 0 V");
        require(!"OL".equals(sim.instrumentController.getReadingForDeveloperVerification()),
            "Same-node diode test displayed OL");
    }

    private static void verifyDiodeInvalidProbe(CirSim sim, CircuitPostProbeTarget red,
            CircuitPostProbeTarget black) {
        verifyDiodeForwardLed(sim, red, black);
        int elementIndex = sim.elmList.indexOf(red.getElement());
        sim.elmList.remove(red.getElement());
        sim.needAnalyze();
        require("--- V".equals(sim.instrumentController.getReadingForDeveloperVerification()),
            "Invalid diode probe did not clear cached reading");
        sim.updateCircuit();
        sim.elmList.add(elementIndex, red.getElement());
        sim.needAnalyze();
        sim.updateCircuit();
        require("--- V".equals(sim.instrumentController.getReadingForDeveloperVerification()),
            "Restoring an element silently restored a diode probe");
    }

    private static void verifyDiodeTopologyAndRepaint(CirSim sim, ProbeTarget red, ProbeTarget black) {
        sim.instrumentController.setDiodeProbesForDeveloperVerification(red, black);
        int before = sim.instrumentController.getDiodeMeasurementCountForDeveloperVerification();
        sim.needAnalyze();
        require("--- V".equals(sim.instrumentController.getReadingForDeveloperVerification()),
            "Diode topology invalidation did not clear cached reading");
        sim.updateCircuit();
        require(before + 1 == sim.instrumentController.getDiodeMeasurementCountForDeveloperVerification(),
            "Diode topology change did not refresh exactly once");
        sim.updateCircuit();
        sim.updateCircuit();
        require(before + 1 == sim.instrumentController.getDiodeMeasurementCountForDeveloperVerification(),
            "Diode repaint cycle reran the active transaction");
    }

    private static void verifyDiodePowerAndLegacy(CirSim sim, GeneratedBoardInstance instance,
            ProbeTarget red, ProbeTarget black) {
        sim.setBoardPowerState(BoardPowerState.POWERED);
        sim.instrumentController.setDiodeProbesForDeveloperVerification(red, black);
        require("POWER OFF".equals(sim.instrumentController.getReadingForDeveloperVerification()),
            "Powered diode measurement was not blocked");
        requireContinuityInactive(sim, "Diode mode activated continuity feedback");
        sim.setBoardPowerState(BoardPowerState.UNPOWERED);
        sim.getBoardPowerController().detach();
        sim.instrumentController.setDiodeProbesForDeveloperVerification(red, black);
        require("POWER OFF".equals(sim.instrumentController.getReadingForDeveloperVerification()),
            "Legacy graph was allowed to run diode measurement");
        sim.getBoardPowerController().attach(instance.getExternalPowerBindings());
        sim.setBoardPowerState(BoardPowerState.UNPOWERED);
        sim.updateCircuit();
    }

    private static void verifyDiodeModeSwitching(CirSim sim, ProbeTarget sameNetA,
            ProbeTarget sameNetB, ProbeTarget ground) {
        sim.instrumentController.setContinuityProbesForDeveloperVerification(sameNetA, sameNetB);
        require(sim.instrumentController.isContinuityDetectedForDeveloperVerification(),
            "CONT setup for diode switching did not activate BEEP");
        sim.instrumentController.setDiodeProbesForDeveloperVerification(sameNetA, sameNetB);
        requireContinuityInactive(sim, "DIODE did not stop BEEP immediately");
        int continuityBefore = sim.instrumentController.getResistanceMeasurementCountForDeveloperVerification();
        sim.instrumentController.setContinuityProbesForDeveloperVerification(sameNetA, sameNetB);
        require(continuityBefore + 1 == sim.instrumentController.getResistanceMeasurementCountForDeveloperVerification(),
            "DIODE to CONT did not run one continuity measurement");
        int resistanceBefore = sim.instrumentController.getResistanceMeasurementCountForDeveloperVerification();
        sim.instrumentController.setResistanceProbesForDeveloperVerification(sameNetA, sameNetB);
        require(resistanceBefore + 1 == sim.instrumentController.getResistanceMeasurementCountForDeveloperVerification(),
            "DIODE to OHM did not run one resistance measurement");
        sim.instrumentController.setDiodeProbesForDeveloperVerification(sameNetA, sameNetB);
        sim.instrumentController.setDcVoltageProbesForDeveloperVerification(sameNetA, ground);
        requireVoltageReadout(sim, "DIODE to DC V did not restore live DC display");
        sim.instrumentController.setDiodeProbesForDeveloperVerification(sameNetA, sameNetB);
        sim.instrumentController.exitInstrumentModeForDeveloperVerification();
        require(!sim.instrumentController.isHandlingPointerInput(),
            "Exiting diode mode did not restore normal pointer handling");
    }

    private static void verifyDiodeQueuedPower(CirSim sim, GeneratedBoardInstance instance,
            ProbeTarget red, ProbeTarget black, ProbeTarget vin, ProbeTarget ground) {
        sim.requestPowerOnDuringActiveMeasurementForDeveloperVerification();
        sim.instrumentController.setDiodeProbesForDeveloperVerification(red, black);
        require("POWER OFF".equals(sim.instrumentController.getReadingForDeveloperVerification()),
            "Queued power-on published a diode result");
        require(sim.getBoardPowerController().getState() == BoardPowerState.POWERED &&
            instance.getExternalPowerBindings().areAllConnected(),
            "Queued diode power-on did not restore the final powered graph");
        requireApproximately(9, sim.instrumentController.getDcVoltageDifferenceForDeveloperVerification(vin, ground), .1,
            "Queued diode power-on did not restore VIN");
        require(sim.isActiveMeasurementSolverRestoredForDeveloperVerification(),
            "Queued diode power-on left a temporary overlay in the solver");
        sim.verifyGeneratedBoard();
    }

    private static void verifyContinuityResult(CirSim sim, ProbeTarget red, ProbeTarget black,
            double expected, double tolerance, boolean expectedContinuity) {
        sim.instrumentController.setContinuityProbesForDeveloperVerification(red, black);
        double actual = sim.instrumentController.getLatestResistanceReadingForDeveloperVerification();
        requireApproximately(expected, actual, tolerance, "Unexpected continuity resistance");
        require(expectedContinuity == sim.instrumentController.isContinuityDetectedForDeveloperVerification(),
            "Continuity threshold state did not match measured resistance");
        require(expectedContinuity == sim.instrumentController.isContinuityIndicatorVisibleForDeveloperVerification(),
            "Continuity indicator did not match measured resistance");
        require(expectedContinuity == sim.instrumentController.isContinuityFeedbackRequestedForDeveloperVerification(),
            "Continuity feedback request did not match measured resistance");
    }

    private static void verifyContinuityThresholds(CirSim sim, GeneratedBoardInstance instance,
            CircuitPostProbeTarget red, CircuitPostProbeTarget black) {
        ResistorElm resistor = (ResistorElm) instance.getComponentBindings().getSingleElement("R1");
        double originalResistance = resistor.getResistance();
        try {
            sim.instrumentController.setContinuityProbesForDeveloperVerification(red, black);
            resistor.setResistance(49);
            verifyContinuityTopologyRefresh(sim, 49, .2, true);
            resistor.setResistance(50);
            verifyContinuityTopologyRefresh(sim, 50, .2, true);
            resistor.setResistance(51);
            verifyContinuityTopologyRefresh(sim, 51, .2, false);
            resistor.setResistance(50);
            verifyContinuityTopologyRefresh(sim, 50, .2, true);
        } finally {
            resistor.setResistance(originalResistance);
            sim.needAnalyze();
            sim.updateCircuit();
        }
    }

    private static void verifyContinuityTopologyRefresh(CirSim sim, double expected,
            double tolerance, boolean expectedContinuity) {
        int measurementsBefore = sim.instrumentController.getResistanceMeasurementCountForDeveloperVerification();
        sim.needAnalyze();
        require("--- Ohm".equals(sim.instrumentController.getReadingForDeveloperVerification()),
            "Continuity topology change did not clear the cached result");
        sim.updateCircuit();
        require(measurementsBefore + 1 ==
            sim.instrumentController.getResistanceMeasurementCountForDeveloperVerification(),
            "Continuity topology change did not trigger exactly one refresh");
        requireApproximately(expected, sim.instrumentController.getLatestResistanceReadingForDeveloperVerification(),
            tolerance, "Unexpected continuity threshold resistance");
        require(expectedContinuity == sim.instrumentController.isContinuityDetectedForDeveloperVerification(),
            "Continuity threshold transition did not update feedback state");
    }

    private static void verifyContinuityPowerTransition(CirSim sim, GeneratedBoardInstance instance,
            CircuitPostProbeTarget j11Probe, CircuitPostProbeTarget r11Probe,
            CircuitPostProbeTarget r12Probe) {
        verifyContinuityResult(sim, j11Probe, r11Probe, 0, .001, true);
        sim.setBoardPowerState(BoardPowerState.POWERED);
        requireContinuityInactive(sim, "Power transition did not stop continuity feedback immediately");
        sim.updateCircuit();
        require("POWER OFF".equals(sim.instrumentController.getReadingForDeveloperVerification()),
            "Powered continuity readout was not blocked");
        require(instance.getExternalPowerBindings().areAllConnected(),
            "Powered continuity graph did not reconnect external isolation");
        sim.verifyGeneratedBoard();
        sim.setBoardPowerState(BoardPowerState.UNPOWERED);
        sim.updateCircuit();
        verifyContinuityResult(sim, r11Probe, r12Probe, 680, 2, false);
    }

    private static void verifyContinuityLegacyBlock(CirSim sim, GeneratedBoardInstance instance,
            ProbeTarget red, ProbeTarget black) {
        sim.getBoardPowerController().detach();
        sim.instrumentController.setContinuityProbesForDeveloperVerification(red, black);
        require("POWER OFF".equals(sim.instrumentController.getReadingForDeveloperVerification()),
            "Legacy graph was allowed to run active continuity");
        requireContinuityInactive(sim, "Legacy graph left continuity feedback active");
        sim.getBoardPowerController().attach(instance.getExternalPowerBindings());
        sim.setBoardPowerState(BoardPowerState.UNPOWERED);
        sim.updateCircuit();
    }

    private static void verifyInvalidContinuityProbeClearsFeedback(CirSim sim,
            CircuitPostProbeTarget red, CircuitPostProbeTarget black) {
        verifyContinuityResult(sim, red, black, 0, .001, true);
        int elementIndex = sim.elmList.indexOf(red.getElement());
        sim.elmList.remove(red.getElement());
        sim.needAnalyze();
        require("--- Ohm".equals(sim.instrumentController.getReadingForDeveloperVerification()),
            "Invalid CONT probe did not clear the cached resistance");
        requireContinuityInactive(sim, "Invalid CONT probe did not stop feedback immediately");
        sim.updateCircuit();
        require("--- Ohm".equals(sim.instrumentController.getReadingForDeveloperVerification()),
            "Invalid CONT probe retained a stale reading after analysis");
        requireContinuityInactive(sim, "Invalid CONT probe restarted feedback after analysis");
        sim.elmList.add(elementIndex, red.getElement());
        sim.needAnalyze();
        sim.updateCircuit();
        require("--- Ohm".equals(sim.instrumentController.getReadingForDeveloperVerification()),
            "Restoring an element silently restored a cleared CONT probe");
    }

    private static void verifyContinuityModeSwitching(CirSim sim, ProbeTarget red, ProbeTarget black) {
        verifyContinuityResult(sim, red, black, 0, .001, true);
        sim.instrumentController.setResistanceProbesForDeveloperVerification(red, black);
        requireContinuityInactive(sim, "Switching CONT to OHM did not stop feedback");
        sim.instrumentController.setContinuityProbesForDeveloperVerification(red, black);
        sim.instrumentController.setDcVoltageProbesForDeveloperVerification(red, black);
        requireContinuityInactive(sim, "Switching CONT to DC V did not stop feedback");
        sim.instrumentController.setContinuityProbesForDeveloperVerification(red, black);
        sim.instrumentController.exitInstrumentModeForDeveloperVerification();
        requireContinuityInactive(sim, "Exiting CONT did not stop feedback");
        require(!sim.instrumentController.isHandlingPointerInput(),
            "Instrument mode exit did not restore normal pointer handling");
    }

    private static void verifyContinuityRepaintBehavior(CirSim sim, ProbeTarget red, ProbeTarget black) {
        verifyContinuityResult(sim, red, black, 0, .001, true);
        int measurementsBefore = sim.instrumentController.getResistanceMeasurementCountForDeveloperVerification();
        int preparesBefore = sim.instrumentController.getContinuityFeedbackPrepareCountForDeveloperVerification();
        int startsBefore = sim.instrumentController.getContinuityFeedbackStartCountForDeveloperVerification();
        int stopsBefore = sim.instrumentController.getContinuityFeedbackStopCountForDeveloperVerification();
        sim.updateCircuit();
        sim.updateCircuit();
        require(measurementsBefore == sim.instrumentController.getResistanceMeasurementCountForDeveloperVerification(),
            "Repeated continuity repaint cycles started an active transaction");
        require(startsBefore == sim.instrumentController.getContinuityFeedbackStartCountForDeveloperVerification() &&
            stopsBefore == sim.instrumentController.getContinuityFeedbackStopCountForDeveloperVerification(),
            "Repeated continuity repaint cycles changed feedback state");
        sim.instrumentController.handlePointerInput(0, -1, -1);
        require(measurementsBefore == sim.instrumentController.getResistanceMeasurementCountForDeveloperVerification() &&
            startsBefore == sim.instrumentController.getContinuityFeedbackStartCountForDeveloperVerification() &&
            stopsBefore == sim.instrumentController.getContinuityFeedbackStopCountForDeveloperVerification(),
            "Empty continuity click changed active measurement or feedback state");
        require(preparesBefore == sim.instrumentController.getContinuityFeedbackPrepareCountForDeveloperVerification(),
            "Empty continuity click prepared audio");
    }

    private static void requireContinuityInactive(CirSim sim, String message) {
        require(!sim.instrumentController.isContinuityDetectedForDeveloperVerification() &&
            !sim.instrumentController.isContinuityIndicatorVisibleForDeveloperVerification() &&
            !sim.instrumentController.isContinuityFeedbackRequestedForDeveloperVerification(), message);
    }

    private static void verifyLiveDcVoltage(CirSim sim, ProbeTarget vin, ProbeTarget ground) {
        int resistanceMeasurementsBefore =
            sim.instrumentController.getResistanceMeasurementCountForDeveloperVerification();
        sim.instrumentController.setDcVoltageProbesForDeveloperVerification(vin, ground);
        sim.updateCircuit();
		sim.instrumentController.setDcVoltageProbesForDeveloperVerification(vin, ground);
        requireApproximately(9, sim.instrumentController.getDcVoltageDifferenceForDeveloperVerification(vin, ground),
            .1, "Powered DC reading was not approximately +9 V");
        sim.setBoardPowerState(BoardPowerState.UNPOWERED);
        sim.updateCircuit();
		sim.instrumentController.setDcVoltageProbesForDeveloperVerification(vin, ground);
        requireApproximately(0, sim.instrumentController.getDcVoltageDifferenceForDeveloperVerification(vin, ground),
            .01, "Persistent DC probes did not update to 0 V with board power off");
        sim.setBoardPowerState(BoardPowerState.POWERED);
        sim.updateCircuit();
		sim.instrumentController.setDcVoltageProbesForDeveloperVerification(vin, ground);
        requireApproximately(9, sim.instrumentController.getDcVoltageDifferenceForDeveloperVerification(vin, ground),
            .1, "Persistent DC probes did not update to +9 V after repower");
        sim.updateCircuit();
        sim.updateCircuit();
        require(resistanceMeasurementsBefore ==
            sim.instrumentController.getResistanceMeasurementCountForDeveloperVerification(),
            "Passive DC refresh executed a resistance transaction");
    }

    private static void verifyQueuedPowerOnFinalState(CirSim sim, GeneratedBoardInstance instance,
            ProbeTarget vin, ProbeTarget ground, ProbeTarget red, ProbeTarget black) {
        sim.instrumentController.setResistanceProbesForDeveloperVerification(red, black);
        require("POWER OFF".equals(sim.instrumentController.getReadingForDeveloperVerification()),
            "Queued power-on published a stale resistance reading");
        require(sim.getBoardPowerController().getState() == BoardPowerState.POWERED,
            "Queued power-on request was not applied after measurement cleanup");
        require(instance.getExternalPowerBindings().areAllConnected(),
            "External isolation control was not connected after queued power-on");
        require(sim.isActiveMeasurementSolverRestoredForDeveloperVerification(),
            "Powered final graph did not restore the solver without temporary elements");
        requireApproximately(9,
            sim.instrumentController.getDcVoltageDifferenceForDeveloperVerification(vin, ground), .1,
            "Powered final solver graph did not restore board-side VIN");
        sim.verifyGeneratedBoard();
    }

    private static void requireApproximately(double expected, double actual, double tolerance,
            String message) {
        require(Math.abs(actual - expected) <= tolerance,
            message + ": " + actual);
    }

    private static void requireVoltageReadout(CirSim sim, String message) {
        String readout = sim.instrumentController.getReadingForDeveloperVerification();
        require(!"--- V".equals(readout) && readout.endsWith("V"), message + ": " + readout);
    }

    private static ResistorColorBand[] getDeveloperResistorBands(GeneratedBoardInstance instance) {
        ResistorNameplate nameplate = StandardPhysicalDefinitionProviders.RESISTOR.require(
            instance.getPhysicalSpecifications(), "R1");
        return ResistorColorCode.getFourBandCode(nameplate);
    }

    private static void require(boolean condition, String message) {
        if (!condition)
            throw new IllegalStateException(message);
    }
}
