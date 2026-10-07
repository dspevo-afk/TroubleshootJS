package com.lushprojects.circuitjs1.client;

import java.util.Vector;

/** Native source/control contracts and the same real CircuitJS E05 electrical cases. */
public final class E05PowerContractTest {
    private static int assertions;
    private E05PowerContractTest() { }

    public static void main(String[] args) {
        CirSim previous = CircuitElm.sim, previousSingleton = CirSim.theSim;
        TestCirSim sim = new TestCirSim();
        CircuitElm.sim = sim;
        try {
            sourceConvention(sim);
            isolationPoles();
            aggregateOff();
            fixedTopology();
            rejectNativeEscape(sim);
            String report = E05ReferenceIsolationPilot.verifyNative(sim);
            check(report.contains("\"execution\":\"NATIVE_RAW_GRAPH\"") &&
                report.contains("\"playerOwnerRestored\":null"),
                "native numerical evidence makes no player-lifecycle claim");
            check(sim.elmList.isEmpty() && !sim.solverExecutor.isUnavailable(),
                "native pilot retires its raw graph and execution lease");
            String electrical = E05ElectricalDeveloperVerifier.verifyNative(sim);
            check(electrical.contains("\"execution\":\"NATIVE_RAW_GRAPH\"") &&
                electrical.contains("\"playerOwnerRestored\":null"),
                "native electrical acceptance keeps the same limited lifecycle claim");
            check(sim.elmList.isEmpty() && !sim.solverExecutor.isUnavailable(),
                "native electrical acceptance retires graph and execution lease");
            System.out.println("E05_REFERENCE_PILOT " + report);
            System.out.println("E05_ELECTRICAL_ACCEPTANCE " + electrical);
            System.out.println("PASS: E05 power contracts assertions=" + assertions);
        } finally {
            CircuitElm.sim = previous; CirSim.theSim = previousSingleton;
        }
    }

    private static void sourceConvention(CirSim sim) {
        near(E05AcInputModel.RMS_VOLTS, 120, 0, "fixed source RMS");
        near(E05AcInputModel.PEAK_VOLTS, Math.sqrt(28800), 1e-12, "RMS-to-peak convention");
        near(E05AcInputModel.FREQUENCY_HZ, 60, 0, "fixed source frequency");
        near(E05AcInputModel.SERIES_OHMS, 22, 0, "finite source impedance");
        E05AcInputModel.requireTimeStep(.00005);
        E05AcInputModel.requireTimeStep(.000025);
        for (final double bad : new double[] {0, -.00001, .0000500001,
                Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY}) {
            reject(new Runnable() { public void run() { E05AcInputModel.requireTimeStep(bad); } },
                "unsupported continuous timestep");
        }
        final Point line = new Point(64, 96), returned = new Point(64, 256);
        reject(new Runnable() { public void run() { E05AcInputModel.create(0, 0, null, returned); } },
            "missing line");
        reject(new Runnable() { public void run() { E05AcInputModel.create(0, 0, line, null); } },
            "missing return");
        reject(new Runnable() { public void run() { E05AcInputModel.create(0, 0, line, line); } },
            "aliased source terminals");
        E05AcInputModel.Input input = E05AcInputModel.create(-256, 96, line, returned);
        Vector<CircuitElm> backing = input.binding.getBackingElements();
        try {
            check(input.source.waveform == VoltageElm.WF_AC, "actual source is sinusoidal");
            near(input.source.bias, 0, 0, "source has no DC bias");
            near(input.source.phaseShift, 0, 0, "source starts at a zero crossing");
            near(input.impedance.getResistance(), 22, 0, "impedance is a real resistor");
            near(input.source.maxVoltage, Math.sqrt(28800), 1e-12, "source stores peak volts");
            double sum = 0, square = 0, peak = 0;
            for (int n = 0; n < 800; n++) {
                sim.t = n / 48000.0; // One 60 Hz cycle, independent literal sampling grid.
                double value = input.source.getVoltage();
                sum += value; square += value * value; peak = Math.max(peak, Math.abs(value));
            }
            near(sum / 800, 0, 1e-10, "one-cycle source mean");
            near(Math.sqrt(square / 800), 120, 1e-10, "sampled sine RMS");
            near(peak, Math.sqrt(28800), 1e-10, "sampled sine peak");
            sim.t = 1.0 / 240;
            near(input.source.getVoltage(), Math.sqrt(28800), 1e-10, "positive quarter cycle");
            sim.t = 1.0 / 80;
            near(input.source.getVoltage(), -Math.sqrt(28800), 1e-10, "negative quarter cycle");
            input.elements.clear();
            check(input.binding.getBackingElements().size() == 4,
                "caller source-vector mutation does not alter binding ownership");
            Vector<CircuitElm> escaped = input.binding.getBackingElements(); escaped.clear();
            check(input.binding.getBackingElements().size() == 4,
                "binding backing-vector access is defensive");
            reject(new Runnable() { public void run() { LowVoltageSourceModel.validate(120, .25); } },
                "E05 does not widen the low-voltage source envelope");
        } finally {
            sim.t = 0; for (CircuitElm element : backing) element.delete();
        }
    }

    private static void isolationPoles() {
        final SwitchElm first = switchAt(0), second = switchAt(128);
        SwitchElm escaped = switchAt(256);
        SwitchElm[] poles = {first, second};
        SwitchExternalPowerControl control = new SwitchExternalPowerControl(poles);
        poles[0] = escaped;
        try {
            check(control.isConnected(), "fresh poles connected");
            control.setConnected(false);
            check(first.position == 1 && second.position == 1 && escaped.position == 0,
                "defensive pole-array copy owns exact original switches");
            check(!control.isConnected(), "two open poles prove isolation");
            first.toggle();
            check(control.isConnected(), "line-only closed is conservatively connected");
            control.setConnected(false);
            second.toggle();
            check(control.isConnected(), "return-only closed is conservatively connected");
            control.setConnected(false);
            control.setConnected(true);
            check(first.position == 0 && second.position == 0, "ON closes both poles");
            control.setConnected(true);
            check(first.position == 0 && second.position == 0, "idempotent ON retains both poles");
            control.setConnected(false); control.setConnected(false);
            check(first.position == 1 && second.position == 1, "idempotent OFF retains isolation");
            SwitchExternalPowerControl single = new SwitchExternalPowerControl(escaped);
            single.setConnected(false); check(escaped.position == 1, "single-pole callers retained");
            reject(new Runnable() { public void run() { new SwitchExternalPowerControl((SwitchElm)null); } },
                "missing single pole");
            reject(new Runnable() { public void run() { new SwitchExternalPowerControl((SwitchElm[])null); } },
                "missing pole array");
            reject(new Runnable() { public void run() { new SwitchExternalPowerControl(new SwitchElm[0]); } },
                "empty pole array");
            reject(new Runnable() { public void run() {
                new SwitchExternalPowerControl(new SwitchElm[] {first, null});
            } }, "missing member pole");
            reject(new Runnable() { public void run() {
                new SwitchExternalPowerControl(new SwitchElm[] {first, first});
            } }, "duplicate physical pole");
        } finally {
            first.delete(); second.delete(); escaped.delete();
        }
    }

    private static void aggregateOff() {
        TroubleshootBoard board = new TroubleshootBoard("E05_SOURCE_CONTROLS");
        addInput(board, "J1", "AC_A"); addInput(board, "J2", "AC_B");
        GeneratedExternalPowerBindings bindings = new GeneratedExternalPowerBindings(board);
        E05AcInputModel.Input a = E05AcInputModel.create(-256, 96,
            new Point(64, 96), new Point(64, 256));
        E05AcInputModel.Input b = E05AcInputModel.create(512, 96,
            new Point(832, 96), new Point(832, 256));
        bindings.bindPowerInput("AC_A", a.binding); bindings.bindPowerInput("AC_B", b.binding);
        BoardPowerController power = new BoardPowerController();
        try {
            power.attach(bindings);
            long revision = a.binding.getConnectionRevision();
            power.setSourceConnected("AC_A", false);
            check(power.getState() == BoardPowerState.POWERED && !bindings.areAllDisconnected(),
                "one isolated source is not aggregate OFF");
            check(a.linePole.position == 1 && a.returnPole.position == 1 &&
                a.binding.getConnectionRevision() == revision + 1,
                "one existing source command opens both poles with one revision");
            power.setSourceConnected("AC_B", false);
            check(power.isElectricallyUnpowered() && bindings.areAllDisconnected(),
                "all sources and all poles isolated admit aggregate OFF");
            a.linePole.toggle();
            check(!bindings.areAllDisconnected() && !power.isElectricallyUnpowered(),
                "partially closed line rejects aggregate electrical OFF");
            power.setState(BoardPowerState.UNPOWERED);
            check(a.linePole.position == 1 && a.returnPole.position == 1 &&
                power.isElectricallyUnpowered(), "OFF repairs partial line isolation command");
            a.returnPole.toggle();
            check(!bindings.areAllDisconnected() && !power.isElectricallyUnpowered(),
                "partially closed return rejects aggregate electrical OFF");
            power.setState(BoardPowerState.UNPOWERED);
            check(a.linePole.position == 1 && a.returnPole.position == 1 &&
                power.isElectricallyUnpowered(), "OFF repairs partial return isolation command");
            power.setState(BoardPowerState.POWERED);
            check(bindings.areAllConnected() && a.linePole.position == 0 &&
                a.returnPole.position == 0 && b.linePole.position == 0 &&
                b.returnPole.position == 0, "aggregate ON closes all actual source poles");
            GeneratedExternalPowerBindings.SavedControls controls = bindings.saveControls();
            power.setSourceConnected("AC_A", false); controls.restore(bindings);
            check(controls.matches() && a.linePole.position == 0 && a.returnPole.position == 0,
                "existing saved control owner restores both poles");
            power.setState(BoardPowerState.UNPOWERED);
        } finally {
            power.detach();
            for (CircuitElm element : a.elements) element.delete();
            for (CircuitElm element : b.elements) element.delete();
        }
    }

    private static void fixedTopology() {
        E05ElectricalFixtures.Fixture direct = E05ElectricalFixtures.rectifierBulk();
        E05ElectricalFixtures.Fixture isolated = E05ElectricalFixtures.isolatedTransformerBulk();
        E05ElectricalFixtures.Fixture reference = E05ElectricalFixtures.transformerReference(0, 0, false);
        try {
            near(direct.bulk.capacitance, .000047, 0, "direct real storage");
            near(direct.bleed.getResistance(), 100000, 0, "direct real bleeder");
            near(direct.load.getResistance(), 20000, 0, "direct passive load");
            near(direct.fuse.resistance, .1, 0, "declared fuse resistance");
            near(direct.fuse.i2t, .1, 0, "declared fuse accepted-energy rating");
            near(isolated.transformer.inductance, 4, 0, "actual primary inductance");
            near(isolated.transformer.ratio, .1, 0, "actual 10:1 step-down ratio");
            near(isolated.transformer.couplingCoef, .999, 0, "declared linear coupling");
            check(!isolated.transformer.isTrapezoidal(), "isolated bridge explicitly selects existing backward Euler");
            check(reference.transformer.isTrapezoidal(), "independent reference energy graph retains A07 trapezoidal");
            near(isolated.bulk.capacitance, .000470, 0, "secondary real storage");
            near(isolated.bleed.getResistance(), 10000, 0, "secondary real bleeder");
            near(isolated.load.getResistance(), 1000, 0, "secondary passive load");
            check(direct.bridge.diodes.length == 4 && isolated.bridge.diodes.length == 4,
                "four actual diodes in each bridge");
            for (DiodeElm diode : direct.bridge.diodes)
                check("default".equals(diode.modelName), "bridge diode model explicitly selected");
            check(direct.bulk.initialVoltage == 0 && isolated.bulk.initialVoltage == 0,
                "new storage owners start uncharged");
            check(!isolated.transformer.getConnection(0, 1) &&
                !isolated.transformer.getConnection(2, 3), "windings have no galvanic connection");
        } finally {
            for (CircuitElm element : direct.elements) element.delete();
            for (CircuitElm element : isolated.elements) element.delete();
            for (CircuitElm element : reference.elements) element.delete();
        }
    }

    private static void rejectNativeEscape(final TestCirSim sim) {
        rejectState(new Runnable() { public void run() { E05ReferenceIsolationPilot.verifyNative(null); } },
            "native pilot rejects missing graph");
        final ResistorElm occupied = new ResistorElm(0, 0); occupied.drag(32, 0);
        sim.elmList.add(occupied);
        try {
            rejectState(new Runnable() { public void run() { E05ReferenceIsolationPilot.verifyNative(sim); } },
                "native pilot cannot replace an occupied graph");
        } finally { sim.elmList.clear(); occupied.delete(); }
        rejectState(new Runnable() { public void run() { E05ReferenceIsolationPilot.verify(sim, false); } },
            "compiled pilot still requires an explicit developer route");
    }

    private static void addInput(TroubleshootBoard board, String connector, String id) {
        String line = id + "_LINE", returned = id + "_RETURN";
        board.addNet(new BoardNet(line)); board.addNet(new BoardNet(returned));
        board.addComponent(new BoardComponent(connector, "CONNECTOR",
            PhysicalPackages.THROUGH_HOLE_CONNECTOR_2));
        board.addPad(new BoardPad(connector + ".1", connector, "1", line));
        board.addPad(new BoardPad(connector + ".2", connector, "2", returned));
        board.addPowerInput(new ExternalBoardPowerInput(id, connector + ".1",
            connector + ".2", line, returned));
    }

    private static SwitchElm switchAt(int x) {
        SwitchElm result = new SwitchElm(x, 0); result.drag(x + 64, 0); return result;
    }

    private static void reject(Runnable operation, String label) {
        try { operation.run(); } catch (IllegalArgumentException expected) { assertions++; return; }
        throw new AssertionError(label);
    }

    private static void rejectState(Runnable operation, String label) {
        try { operation.run(); } catch (IllegalStateException expected) { assertions++; return; }
        throw new AssertionError(label);
    }

    private static void near(double actual, double expected, double tolerance, String label) {
        check(PowerDomainContract.finite(actual) && Math.abs(actual - expected) <= tolerance,
            label + ": " + actual + " expected " + expected);
    }

    private static void check(boolean passed, String label) {
        assertions++; if (!passed) throw new AssertionError("E05 contract: " + label);
    }

    /** UI-free host adapter; numerical analysis and stepping remain production CircuitJS. */
    private static final class TestCirSim extends CirSim {
        TestCirSim() {
            gridSize = 16; gridMask = ~15; gridRound = 7;
            elmList = new Vector<CircuitElm>(); adjustables = new Vector<Adjustable>();
            undoStack = new Vector<String>(); redoStack = new Vector<String>();
            maxTimeStep = minTimeStep = timeStep = .00005;
            adjustTimeStep = false;
        }
        @Override void needAnalyze() {
            if (elmList != null && CircuitElm.sim == this) solverExecutor.invalidate();
            analyzeFlag = true;
        }
        @Override public void setSimRunning(boolean running) { simRunning = running; }
        @Override void repaint() { }
        @Override void refreshBoardModificationControls() { }
        @Override void refreshChallengeInteractionState() { }
    }
}
