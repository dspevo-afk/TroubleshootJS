package com.lushprojects.circuitjs1.client;

import java.util.Vector;

/** URL-gated regression check for the production active-resistance transaction. */
class ResistanceMeasurementDeveloperVerifier {
    private static String diodeForwardSummary;

    static void verify(CirSim sim) {
        GeneratedBoardInstance instance = sim.getGeneratedBoardInstance();
        if (instance == null)
            throw new IllegalStateException("Resistance verification requires a generated board");

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
        verifyLiveDcVoltage(sim, j11Probe, j12Probe);
        sim.instrumentController.setResistanceProbesForDeveloperVerification(r11Probe, r12Probe);
        require("POWER OFF".equals(sim.instrumentController.getReadingForDeveloperVerification()),
            "Powered resistance readout was not blocked");

        sim.setBoardPowerState(BoardPowerState.UNPOWERED);
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
        sim.setCircuitTitle("Resistance verification passed; diode " + diodeForwardSummary);
    }

    private static CircuitPostProbeTarget getProbe(CirSim sim, GeneratedBoardInstance instance,
            String padId) {
        CircuitMeasurementEndpoint endpoint = instance.getSimulationBindings().getEndpoint(padId);
        if (!(endpoint instanceof CircuitPostMeasurementEndpoint))
            throw new IllegalStateException("Missing circuit post binding for " + padId);
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
        require(sim.isResistanceSolverRestoredForDeveloperVerification(),
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
        require(sim.isResistanceSolverRestoredForDeveloperVerification(),
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
        require(sim.isResistanceSolverRestoredForDeveloperVerification(),
            "Forward diode transaction did not restore the normal solver graph");
    }

    private static void verifyDiodeReverseAndShort(CirSim sim, ProbeTarget ledAnode,
            ProbeTarget ledCathode, ProbeTarget sameNetA, ProbeTarget sameNetB) {
        sim.instrumentController.setDiodeProbesForDeveloperVerification(ledCathode, ledAnode);
        require("OL".equals(sim.instrumentController.getReadingForDeveloperVerification()),
            "Reverse LED diode test was not OL");
        double reverseVoltage = sim.instrumentController.getLatestDiodeVoltageForDeveloperVerification();
        double reverseCurrent = sim.instrumentController.getLatestDiodeCurrentForDeveloperVerification();
        require(Double.isNaN(reverseVoltage) &&
            (reverseCurrent < InstrumentController.DIODE_MINIMUM_CURRENT ||
             reverseCurrent >= InstrumentController.DIODE_COMPLIANCE_THRESHOLD),
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
        require(sim.isResistanceSolverRestoredForDeveloperVerification(),
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
        requireApproximately(9, sim.instrumentController.getDcVoltageDifferenceForDeveloperVerification(vin, ground),
            .1, "Powered DC reading was not approximately +9 V");
        requireVoltageReadout(sim, "Powered DC readout was not visibly +9 V");
        sim.setBoardPowerState(BoardPowerState.UNPOWERED);
        sim.updateCircuit();
        requireApproximately(0, sim.instrumentController.getDcVoltageDifferenceForDeveloperVerification(vin, ground),
            .01, "Persistent DC probes did not update to 0 V with board power off");
        requireVoltageReadout(sim, "Unpowered DC readout was not visibly 0 V");
        sim.setBoardPowerState(BoardPowerState.POWERED);
        sim.updateCircuit();
        requireApproximately(9, sim.instrumentController.getDcVoltageDifferenceForDeveloperVerification(vin, ground),
            .1, "Persistent DC probes did not update to +9 V after repower");
        requireVoltageReadout(sim, "Repowered DC readout was not visibly +9 V");
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
        require(sim.isResistanceSolverRestoredForDeveloperVerification(),
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

    private static void require(boolean condition, String message) {
        if (!condition)
            throw new IllegalStateException(message);
    }
}