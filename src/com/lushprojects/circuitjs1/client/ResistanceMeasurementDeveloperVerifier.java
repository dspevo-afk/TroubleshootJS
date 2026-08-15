package com.lushprojects.circuitjs1.client;

import java.util.Vector;

/** URL-gated regression check for the production active-resistance transaction. */
class ResistanceMeasurementDeveloperVerifier {
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
        sim.setCircuitTitle("Resistance verification passed");
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

    private static void verifyLiveDcVoltage(CirSim sim, ProbeTarget vin, ProbeTarget ground) {
        int resistanceMeasurementsBefore =
            sim.instrumentController.getResistanceMeasurementCountForDeveloperVerification();
        sim.instrumentController.setDcVoltageProbesForDeveloperVerification(vin, ground);
        sim.updateCircuit();
        requireApproximately(9, sim.instrumentController.getDcVoltageDifferenceForDeveloperVerification(vin, ground),
            .1, "Powered DC reading was not approximately +9 V");
        requireVoltageReadout(sim, 9, .1, "Powered DC readout was not visibly +9 V");
        sim.setBoardPowerState(BoardPowerState.UNPOWERED);
        sim.updateCircuit();
        requireApproximately(0, sim.instrumentController.getDcVoltageDifferenceForDeveloperVerification(vin, ground),
            .01, "Persistent DC probes did not update to 0 V with board power off");
        requireVoltageReadout(sim, 0, .01, "Unpowered DC readout was not visibly 0 V");
        sim.setBoardPowerState(BoardPowerState.POWERED);
        sim.updateCircuit();
        requireApproximately(9, sim.instrumentController.getDcVoltageDifferenceForDeveloperVerification(vin, ground),
            .1, "Persistent DC probes did not update to +9 V after repower");
        requireVoltageReadout(sim, 9, .1, "Repowered DC readout was not visibly +9 V");
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

    private static void requireVoltageReadout(CirSim sim, double expected, double tolerance,
            String message) {
        String readout = sim.instrumentController.getReadingForDeveloperVerification();
        require(!"--- V".equals(readout) && readout.endsWith("V"), message + ": " + readout);
    }

    private static void require(boolean condition, String message) {
        if (!condition)
            throw new IllegalStateException(message);
    }
}