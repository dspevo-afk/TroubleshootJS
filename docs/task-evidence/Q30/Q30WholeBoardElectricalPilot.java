package com.lushprojects.circuitjs1.client;

import java.util.Vector;

/** Live CircuitJS power-domain canary on the 33-package Q30 candidate. */
public final class Q30WholeBoardElectricalPilot {
    private static int assertions;

    private Q30WholeBoardElectricalPilot() { }

    public static void main(String[] args) {
        boolean includeBrownout = true;
        boolean warmBrownout = false;
        boolean traceWarmBrownout = false;
        boolean fineWarmBrownout = false;
        long seed = 0L;
        for (String arg : args) {
            if ("skip-brownout".equals(arg)) includeBrownout = false;
            else if ("warm-brownout".equals(arg)) {
                includeBrownout = false;
                warmBrownout = true;
            }
            else if ("trace-warm-brownout".equals(arg)) {
                includeBrownout = false;
                warmBrownout = true;
                traceWarmBrownout = true;
            }
            else if ("fine-warm-brownout".equals(arg)) {
                includeBrownout = false;
                warmBrownout = true;
                fineWarmBrownout = true;
            }
            else if (arg.startsWith("seed="))
                seed = Long.parseLong(arg.substring("seed=".length()));
            else throw new IllegalArgumentException("Unknown Q30 pilot option " + arg);
        }
        State off = observe("both-unloaded", seed, 0, 0,
            true, true, true, true, 12);
        State a = observe("a-loaded", seed, 5, 0,
            true, true, true, true, 12);
        State both = observe("both-loaded", seed, 5, 5,
            true, true, true, true, 12);
        State noMain = observe("main-isolated", seed, 5, 5,
            false, true, true, true, 12);
        State noLoad = observe("load-isolated", seed, 5, 5,
            true, false, true, true, 12);
        State noSensorA = observe("sensor-a-isolated", seed, 5, 5,
            true, true, false, true, 12);
        State brownout = includeBrownout ? observe("main-4v", seed, 5, 5,
            true, true, true, true, 4) : null;

        require(off.rail > 4.75 && off.outA < 1 && off.outB < 1,
            "unloaded control rail and outputs");
        require(a.rail > 4.75 && a.outA > 8 && a.outB < 1,
            "one loaded output");
        require(both.rail > 4.75 && both.outA > 8 && both.outB > 8,
            "both loaded outputs");
        require(off.regInputAmps < a.regInputAmps &&
                a.regInputAmps < both.regInputAmps &&
                off.rail > a.rail && a.rail > both.rail,
            "causal regulator loading");
        require(noMain.rail < .2 && noMain.outA < 1 && noMain.outB < 1,
            "external sensor/load backfeed cannot power control");
        require(noLoad.rail > 4.75 && noLoad.coilA > .02 &&
                noLoad.coilB > .02 && noLoad.outA < 1 && noLoad.outB < 1,
            "isolated load domain cannot borrow control power");
        require(noSensorA.rail > 4.75 && noSensorA.outA < 1 &&
                noSensorA.outB > 8,
            "sensor A partial power leaves B active");
        if (includeBrownout)
            require(brownout.rail < 3.5 && brownout.outA < 1 &&
                    brownout.outB < 1,
                "main input brownout removes both control outputs");
        if (warmBrownout) {
            State warm = observeWarmBrownout(seed, traceWarmBrownout,
                fineWarmBrownout);
            require(warm.rail < 3.5 && warm.outA < 1 && warm.outB < 1,
                "warm main input brownout removes both control outputs");
        }

        PowerDomainContract domains = Rb30PowerDomains.create(
            Rb30Plan.resolve(seed));
        require(MeasurementReferencePolicy.check(domains,
                MeasurementReferencePolicy.Mode.DIFFERENTIAL,
                "RAIL5", "CTRL_RETURN", null).admitsReading(),
            "declared control reference");
        require(MeasurementReferencePolicy.check(domains,
                MeasurementReferencePolicy.Mode.DIFFERENTIAL,
                "OUT_A", "LOAD_RETURN", null).admitsReading(),
            "declared load reference");
        require(MeasurementReferencePolicy.check(domains,
                MeasurementReferencePolicy.Mode.DIFFERENTIAL,
                "RAIL5", "LOAD_RETURN", null).getDecision() ==
                    MeasurementReferencePolicy.Decision.REJECTED,
            "cross-domain reference rejected");
        System.out.println("PASS: Q30 whole-board electrical pilot seed=" +
            seed + " assertions=" + assertions);
    }

    private static State observe(String name, long seed, double sensorA,
            double sensorB, boolean mainConnected, boolean loadConnected,
            boolean sensorAConnected, boolean sensorBConnected,
            double mainVolts) {
        CirSim sim = new CirSim();
        sim.gridSize = 16;
        sim.gridMask = ~15;
        sim.gridRound = 7;
        sim.maxTimeStep = 1e-4;
        sim.minTimeStep = 1e-7;
        sim.adjustTimeStep = true;
        CircuitElm.sim = sim;
        Rb30Generator.Candidate candidate = new Rb30Generator().construct(
            Rb30Plan.resolve(seed));
        Rb30TopologyValidator.require(candidate);
        sim.elmList = candidate.elements();
        sim.adjustables = new Vector<Adjustable>();
        candidate.assembly.power.getBinding("MAIN12")
            .getLimitedSupply().configure(mainVolts, .25);
        candidate.assembly.power.getBinding("SENSOR_A")
            .getLimitedSupply().configure(sensorA, .25);
        candidate.assembly.power.getBinding("SENSOR_B")
            .getLimitedSupply().configure(sensorB, .25);
        candidate.assembly.power.getBinding("MAIN12")
            .setConnected(mainConnected);
        candidate.assembly.power.getBinding("LOAD12")
            .setConnected(loadConnected);
        candidate.assembly.power.getBinding("SENSOR_A")
            .setConnected(sensorAConnected);
        candidate.assembly.power.getBinding("SENSOR_B")
            .setConnected(sensorBConnected);
        sim.analyzeCircuit();
        try {
            sim.solverExecutor.advanceFor(.03);
        } catch (RuntimeException failure) {
            System.out.println("Q30_WHOLE_FAILURE " + name + " time=" + sim.t +
                " input=" + candidate.regulator.getInputVoltage() +
                " rail=" + candidate.regulator.getOutputVoltage() +
                " enable=" + candidate.regulator.getEnableVoltage() +
                " coilA=" + candidate.relayA.coilCurrent +
                " coilB=" + candidate.relayB.coilCurrent +
                " reason=" + failure.getMessage());
            throw failure;
        }
        State state = new State(name, candidate);
        System.out.println(state);
        return state;
    }

    private static void require(boolean okay, String message) {
        assertions++;
        if (!okay) throw new AssertionError("Q30 " + message);
    }

    private static State observeWarmBrownout(long seed, boolean trace,
            boolean fine) {
        CirSim sim = new CirSim();
        sim.gridSize = 16;
        sim.gridMask = ~15;
        sim.gridRound = 7;
        sim.maxTimeStep = fine ? 5e-6 : 1e-4;
        sim.minTimeStep = fine ? 50e-12 : 1e-7;
        sim.adjustTimeStep = true;
        CircuitElm.sim = sim;
        Rb30Generator.Candidate candidate = new Rb30Generator().construct(
            Rb30Plan.resolve(seed));
        Rb30TopologyValidator.require(candidate);
        sim.elmList = candidate.elements();
        sim.adjustables = new Vector<Adjustable>();
        candidate.assembly.power.getBinding("MAIN12")
            .getLimitedSupply().configure(12, .25);
        candidate.assembly.power.getBinding("SENSOR_A")
            .getLimitedSupply().configure(5, .25);
        candidate.assembly.power.getBinding("SENSOR_B")
            .getLimitedSupply().configure(5, .25);
        sim.analyzeCircuit();
        sim.solverExecutor.advanceFor(.03);
        System.out.println(new State("before-warm-4v", candidate));
        candidate.assembly.power.getBinding("MAIN12")
            .getLimitedSupply().configure(4, .25);
        sim.analyzeCircuit();
        try {
            if (trace) {
                for (int step = 0; step < 30; step++) {
                    sim.solverExecutor.advanceFor(.0001);
                    System.out.println("Q30_WARM_TRACE t=" + sim.t +
                        " main=" + candidate.assembly.power.getBinding(
                            "MAIN12").getLimitedSupply().getOutputVoltage() +
                        " input=" + candidate.regulator.getInputVoltage() +
                        " rail=" + candidate.regulator.getOutputVoltage() +
                        " coilA=" + candidate.relayA.coilCurrent +
                        " coilB=" + candidate.relayB.coilCurrent);
                }
                sim.solverExecutor.advanceFor(.027);
            } else sim.solverExecutor.advanceFor(.03);
        } catch (RuntimeException failure) {
            System.out.println("Q30_WHOLE_FAILURE warm-main-4v time=" + sim.t +
                " input=" + candidate.regulator.getInputVoltage() +
                " rail=" + candidate.regulator.getOutputVoltage() +
                " reason=" + failure.getMessage());
            throw failure;
        }
        State state = new State("warm-main-4v", candidate);
        System.out.println(state);
        return state;
    }

    private static final class State {
        final String name;
        final double rail, outA, outB, coilA, coilB, regInputAmps;

        State(String name, Rb30Generator.Candidate candidate) {
            this.name = name;
            rail = candidate.regulator.getOutputVoltage();
            outA = output(candidate, "A");
            outB = output(candidate, "B");
            coilA = candidate.relayA.coilCurrent;
            coilB = candidate.relayB.coilCurrent;
            regInputAmps = candidate.regulator.getInputCurrent();
        }

        public String toString() {
            return "Q30_WHOLE_BOARD " + name + " rail=" + rail +
                " outA=" + outA + " outB=" + outB +
                " coilA=" + coilA + " coilB=" + coilB +
                " regulatorInputAmps=" + regInputAmps;
        }
    }

    private static double output(Rb30Generator.Candidate candidate,
            String channel) {
        BoundedExternalLoadElm load = (BoundedExternalLoadElm)
            candidate.backing.get("JO" + channel);
        return load.getPostVoltage(0) - load.getPostVoltage(1);
    }
}
