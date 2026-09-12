package com.lushprojects.circuitjs1.client;

import java.util.Vector;

/** Actual CircuitJS comparison against one accepted step per call and RC theory. */
final class A10TemporalBatchVerifier {
    private static final double TARGET = .005129;
    private A10TemporalBatchVerifier() { }

    static void verify(CirSim sim) {
        for (int adaptive = 0; adaptive < 2; adaptive++) {
            String serial = run(sim, false, adaptive != 0);
            String batched = run(sim, true, adaptive != 0);
            if (!serial.equals(batched))
                throw new IllegalStateException("Temporal batch changed accepted times, currents or RC state");
        }
        stoppedStep(sim, false);
        stoppedStep(sim, true);
    }

    private static String run(final CirSim sim, boolean batched, boolean adaptive) {
        PrivateSolverContext proof = PrivateSolverContext.open(sim);
        boolean measuring = sim.a01MeasurementRunning;
        long[] priorCounters = counters(sim);
        try {
            sim.a01MeasurementRunning = true;
            restoreCounters(sim, new long[8]);
            sim.maxTimeStep = .00001;
            sim.minTimeStep = adaptive ? .00000125 : .00001;
            sim.timeStep = sim.minTimeStep;
            sim.adjustTimeStep = adaptive;
            DCVoltageElm source = new DCVoltageElm(0, 64); source.drag(0, 0);
            source.maxVoltage = 5;
            final ResistorElm resistor = new ResistorElm(0, 0); resistor.drag(64, 0);
            resistor.setResistance(1000);
            final CapacitorElm capacitor = new CapacitorElm(64, 0); capacitor.drag(64, 64);
            capacitor.setCapacitance(.000001);
            final WireElm wire = new WireElm(64, 64); wire.drag(0, 64);
            GroundElm ground = new GroundElm(0, 64); ground.drag(0, 96);
            Vector<CircuitElm> elements = new Vector<CircuitElm>();
            elements.add(source); elements.add(resistor); elements.add(capacitor);
            elements.add(wire); elements.add(ground);
            proof.install(elements); proof.analyze();
            final StringBuilder evidence = new StringBuilder();
            double[] dueTimes = { .000255, .001285, .005125 };
            for (double dueTime : dueTimes) {
                proof.events().schedule(dueTime, new SolverEventQueue.Action() {
                    public void fire(double due, double accepted) {
                        append(evidence, accepted);
                        append(evidence, capacitor.getPostVoltage(0) - capacitor.getPostVoltage(1));
                        append(evidence, resistor.getCurrent());
                        append(evidence, wire.getCurrent());
                    }
                });
            }
            if (batched) proof.advanceFor(TARGET);
            else {
                int work = 0;
                while (sim.t + 1e-12 < TARGET && work++ < 2000) proof.advanceSteps(1);
            }
            if (sim.t + 1e-12 < TARGET || sim.t >= TARGET + sim.maxTimeStep + 1e-12)
                throw new IllegalStateException("Temporal batch overshot its first accepted target step");
            double volts = capacitor.getPostVoltage(0) - capacitor.getPostVoltage(1);
            double expected = 5 * (1 - Math.exp(-sim.t / .001));
            if (Math.abs(volts - expected) > .002)
                throw new IllegalStateException("Actual RC response differs from the independent exponential oracle");
            append(evidence, sim.t); append(evidence, sim.timeStep);
            append(evidence, sim.timeStepAccum);
            evidence.append(sim.timeStepCount).append(':');
            for (CircuitElm element : elements) {
                append(evidence, element.getCurrent());
                for (int post = 0; post < element.getPostCount(); post++)
                    append(evidence, element.getPostVoltage(post));
            }
            for (long counter : counters(sim)) evidence.append(counter).append(':');
            return evidence.toString();
        } finally {
            try { proof.close(); }
            finally { sim.a01MeasurementRunning = measuring; restoreCounters(sim, priorCounters); }
        }
    }

    private static long[] counters(CirSim sim) {
        return new long[] {sim.a01AnalysisCount, sim.a01StampCount, sim.a01FactorizationCount,
            sim.a01SolveCount, sim.a01IterationCount, sim.a01SubIterationCount,
            sim.a01AcceptedStepCount, sim.steps};
    }
    private static void restoreCounters(CirSim sim, long[] values) {
        sim.a01AnalysisCount = values[0]; sim.a01StampCount = values[1];
        sim.a01FactorizationCount = values[2]; sim.a01SolveCount = values[3];
        sim.a01IterationCount = values[4]; sim.a01SubIterationCount = values[5];
        sim.a01AcceptedStepCount = values[6]; sim.steps = (int)values[7];
    }

    private static void stoppedStep(CirSim sim, boolean batched) {
        PrivateSolverContext proof = PrivateSolverContext.open(sim);
        try {
            sim.timeStep = sim.maxTimeStep = sim.minTimeStep = .0001;
            sim.adjustTimeStep = false;
            DCVoltageElm source = new DCVoltageElm(0, 64); source.drag(0, 0);
            source.maxVoltage = 5;
            StopAfterStep resistor = new StopAfterStep(); resistor.drag(64, 0);
            WireElm wire = new WireElm(64, 0); wire.drag(64, 64);
            WireElm returnWire = new WireElm(64, 64); returnWire.drag(0, 64);
            GroundElm ground = new GroundElm(0, 64); ground.drag(0, 96);
            Vector<CircuitElm> elements = new Vector<CircuitElm>();
            elements.add(source); elements.add(resistor); elements.add(wire);
            elements.add(returnWire); elements.add(ground);
            proof.install(elements); proof.analyze();
            final int[] events = {0};
            proof.events().schedule(0, new SolverEventQueue.Action() {
                public void fire(double due, double accepted) { events[0]++; }
            });
            boolean rejected = false;
            try {
                if (batched) proof.advanceFor(.001);
                else proof.advanceSteps(10);
            } catch (SolverExecutionBoundary.Failure failure) {
                rejected = failure.outcome == SolverExecutionBoundary.Outcome.NUMERICAL_FAILURE;
            }
            if (!rejected || resistor.finished != 1 || events[0] != 1 || sim.t != .0001)
                throw new IllegalStateException("A stopped accepted step continued into another temporal iteration");
        } finally { proof.close(); }
    }

    private static final class StopAfterStep extends ResistorElm {
        int finished;
        StopAfterStep() { super(0, 0); setResistance(1000); }
        @Override void stepFinished() {
            super.stepFinished(); finished++;
            sim.stop("A10 accepted-step stop canary", this);
        }
    }

    private static void append(StringBuilder out, double value) {
        out.append(Double.doubleToLongBits(value)).append(':');
    }
}
