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
        sim.setBoardPowerState(BoardPowerState.POWERED);
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

        sim.requestPowerOnDuringActiveMeasurementForDeveloperVerification();
        measure(sim, r11Probe, r12Probe, 680, 2);
        require(sim.getBoardPowerController().getState() == BoardPowerState.POWERED,
            "Queued power-on request was not applied after measurement cleanup");
        sim.setBoardPowerState(BoardPowerState.UNPOWERED);

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
    }

    private static void require(boolean condition, String message) {
        if (!condition)
            throw new IllegalStateException(message);
    }
}