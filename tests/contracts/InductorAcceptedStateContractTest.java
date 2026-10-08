package com.lushprojects.circuitjs1.client;

import java.util.HashMap;
import java.util.Vector;

/** Actual CircuitJS retry versus the identical accepted RL history at the final step size. */
public final class InductorAcceptedStateContractTest {
    private static final double HALF = .00005, WIDE = .0001;
    private static int assertions;

    public static void main(String[] args) {
        CirSim previous = CircuitElm.sim, singleton = CirSim.theSim;
        Point p1 = CircuitElm.ps1, p2 = CircuitElm.ps2;
        HashMap<String, String> localization = CirSim.localizationMap;
        Throwable failure = null;
        try {
            CircuitElm.ps1 = new Point(); CircuitElm.ps2 = new Point();
            pair("RL_TRAP", 0, false);
            pair("RL_BE", Inductor.FLAG_BACK_EULER, false);
            pair("RELAY_BE_ALIAS", Inductor.FLAG_BACK_EULER, true);
        } catch (Throwable error) { failure = error;
        } finally {
            CircuitElm.sim = previous; CirSim.theSim = singleton;
            CircuitElm.ps1 = p1; CircuitElm.ps2 = p2; CirSim.localizationMap = localization;
        }
        if (failure != null) raise(failure);
        check(CircuitElm.sim == previous && CirSim.theSim == singleton &&
            CircuitElm.ps1 == p1 && CircuitElm.ps2 == p2 && CirSim.localizationMap == localization,
            "native global owners restored");
        System.out.println("PASS: native inductor accepted-state contracts assertions=" + assertions +
            " cases=3 scope=RL_REJECTED_STEP_HISTORY_AND_RELAY_ALIAS");
    }

    private static void pair(String id, int flags, boolean relay) {
        Fixture fixed = null, retry = null; Throwable failure = null;
        try {
            fixed = new Fixture(); fixed.build(flags, relay); fixed.prime();
            retry = new Fixture(); retry.build(flags, relay); retry.prime();
            compare(fixed.read(), retry.read(), id + " identical accepted prime");
            Reading prime = retry.read();
            check(Math.abs(prime.voltage) > .001 && Math.abs(prime.current) > .001,
                id + " real accepted nonzero storage voltage and current");
            double before = retry.sim.t;
            long accepted = retry.sim.a01AcceptedStepCount, trials = retry.sim.a01SubIterationCount;
            final int[] events = { 0 };
            final Fixture observed = retry;
            retry.activate();
            retry.sim.solverExecutor.events(null).schedule(Math.nextUp(before), new SolverEventQueue.Action() {
                public void fire(double due, double time) {
                    check(time == observed.sim.t && observed.sim.a01AcceptedStepCount > 0,
                        "accepted event observes the actual committed solver time");
                    events[0]++;
                }
            });
            retry.sim.maxTimeStep = retry.sim.timeStep = WIDE;
            retry.sim.solverExecutor.goodIterations = 0;
            retry.sim.solverExecutor.goodIteration = true;
            retry.sim.stampCircuit(); // Normal matrix restamp for the sole test step-size change.
            retry.veto.armed = true;
            retry.advance(1);
            check(retry.veto.wideTrials == 100 && retry.veto.fineTrials == 2,
                id + " exactly one full 100-trial rejection followed by ordinary convergence");
            check(retry.sim.a01SubIterationCount - trials == 102 &&
                retry.sim.a01AcceptedStepCount - accepted == 1 && events[0] == 1,
                id + " rejected trials cannot increment accepted count or dispatch events");
            check(retry.veto.lastWideTime == before && retry.veto.firstFineTime == before,
                id + " rejected attempt and half-step attempt have no intervening time advance");
            near(retry.sim.t - before, HALF, id + " sole accepted half-step duration");
            near(retry.veto.firstFineAlias, prime.current, id + " restored caller alias before control logic");
            near(retry.veto.firstFineHelper, prime.current, id + " restored companion history before Newton");
            fixed.advance(1);
            compare(fixed.read(), retry.read(), id + " rejected-wide versus direct-half accepted result");
            retry.veto.armed = false; retry.sim.maxTimeStep = HALF;
            for (int n = 0; n < 8; n++) {
                fixed.advance(1); retry.advance(1);
                compare(fixed.read(), retry.read(), id + " subsequent accepted history " + n);
            }
            check(retry.veto.wideTrials == 100, id + " no second forced rejection");
            System.out.println("INDUCTOR_RETRY_CASE id=" + id + " rejectedTrials=100 acceptedRetrySteps=1" +
                " primeCurrent=" + prime.current + " primeVoltage=" + prime.voltage +
                " finalCurrent=" + retry.read().current + " finalTime=" + retry.sim.t);
        } catch (Throwable error) { failure = error;
        } finally {
            failure = dispose(retry, id + "_retry", failure);
            failure = dispose(fixed, id + "_fixed", failure);
        }
        if (failure != null) raise(failure);
    }

    private static void compare(Reading a, Reading b, String context) {
        near(a.time, b.time, context + " time"); near(a.voltage, b.voltage, context + " winding voltage");
        near(a.terminalVoltage, b.terminalVoltage, context + " terminal voltage");
        near(a.current, b.current, context + " current"); near(a.energy, b.energy, context + " stored energy");
        check(a.position == b.position, context + " actual relay position");
    }
    private static void near(double a, double b, String context) {
        check(SolverExecutionBoundary.finite(a) && SolverExecutionBoundary.finite(b) &&
            Math.abs(a - b) <= 1e-12 + 1e-10 * Math.max(Math.abs(a), Math.abs(b)), context);
    }
    private static void check(boolean condition, String context) {
        assertions++; if (!condition) throw new AssertionError(context);
    }
    private static void raise(Throwable error) {
        if (error instanceof Error) throw (Error) error;
        if (error instanceof RuntimeException) throw (RuntimeException) error;
        throw new IllegalStateException(error);
    }
    private static Throwable dispose(Fixture fixture, String id, Throwable failure) {
        if (fixture == null) return failure;
        Throwable cleanup = null; fixture.activate(); int attempted = 0;
        for (CircuitElm element : new Vector<CircuitElm>(fixture.owned)) {
            attempted++;
            try { element.delete(); }
            catch (Throwable error) { if (cleanup == null) cleanup = error; else cleanup.addSuppressed(error); }
        }
        fixture.owned.clear(); fixture.sim.elmList = fixture.original;
        fixture.sim.a01MeasurementRunning = false; fixture.sim.activeMeasurementOverlay = false;
        try { fixture.sim.solverExecutor.retire(); fixture.sim.solverExecutor.invalidate(); }
        catch (Throwable error) { if (cleanup == null) cleanup = error; else cleanup.addSuppressed(error); }
        fixture.sim.circuitMatrix = null;
        boolean clean = cleanup == null && fixture.original.isEmpty() && fixture.owned.isEmpty() &&
            fixture.sim.elmList == fixture.original && !fixture.sim.solverExecutor.isUnavailable() &&
            fixture.sim.generatedBoardInstance == null && !fixture.sim.activeMeasurementOverlay;
        System.out.println("INDUCTOR_RETRY_CLEANUP id=" + id + " candidateDisposed=" + clean +
            " attemptedDeletes=" + attempted + " subscriptionsCreated=0 executorRetired=" + clean);
        if (!clean && cleanup == null) cleanup = new AssertionError("Failed native RL fixture cleanup " + id);
        if (cleanup != null) { if (failure == null) failure = cleanup; else failure.addSuppressed(cleanup); }
        return failure;
    }

    private static final class Reading {
        double time, voltage, terminalVoltage, current, energy; int position;
    }
    private static final class Fixture {
        final NativeSim sim = new NativeSim();
        final Vector<CircuitElm> original = sim.elmList, owned = new Vector<CircuitElm>();
        InductorElm inductor; RelayElm relay; ConvergenceVeto veto;
        void activate() { CircuitElm.sim = sim; CirSim.theSim = sim; }
        void build(int flags, boolean useRelay) {
            activate(); Q30ServiceFlowContractTest.configureSimulator(sim, HALF);
            sim.elmList = owned; sim.a01MeasurementRunning = true;
            VoltageElm source = new VoltageElm(0, 256, VoltageElm.WF_DC);
            source.maxVoltage = 12; source.setPosition(0, 256, 0, 0); owned.add(source);
            GroundElm ground = new GroundElm(0, 256); ground.setPosition(0, 256, 0, 288); owned.add(ground);
            Point input, returned;
            if (!useRelay) {
                inductor = new InductorElm(96, 0, 0, 256, flags, new StringTokenizer(".02 0"));
                inductor.setPoints(); owned.add(inductor); input = inductor.getPost(0); returned = inductor.getPost(1);
            } else {
                relay = new RelayElm(256, 0); relay.setPosition(256, 0, 352, 0); owned.add(relay);
                input = relay.getPost(relay.nCoil1); returned = relay.getPost(relay.nCoil2);
                for (int n = 0; n < 3; n++) {
                    Point post = relay.getPost(n); ResistorElm load = new ResistorElm(post.x, post.y);
                    load.setPosition(post.x, post.y, 0, 256); load.setResistance(1000); owned.add(load);
                }
            }
            veto = new ConvergenceVeto(this, 0, 0);
            veto.setPosition(0, 0, input.x, input.y); veto.setResistance(20); owned.add(veto);
            if (returned.x != 0 || returned.y != 256) {
                WireElm wire = new WireElm(returned.x, returned.y);
                wire.setPosition(returned.x, returned.y, 0, 256); owned.add(wire);
            }
        }
        void prime() {
            activate(); sim.solverExecutor.analyze(); sim.analyzeFlag = sim.dcAnalysisFlag = false;
            advance(24);
        }
        void advance(int count) { activate(); sim.solverExecutor.advanceSteps(count); }
        double alias() { return relay == null ? inductor.current : relay.coilCurrent; }
        double helper() { return relay == null ? inductor.ind.current : relay.ind.current; }
        Reading read() {
            Reading r = new Reading(); r.time = sim.t; r.current = alias();
            near(r.current, helper(), "live companion/caller current agrees after accepted solve");
            if (relay == null) {
                r.voltage = r.terminalVoltage = inductor.volts[0] - inductor.volts[1];
                r.energy = .5 * inductor.inductance * r.current * r.current; r.position = -1;
            } else {
                r.voltage = relay.volts[relay.nCoil1] - relay.volts[relay.nCoil3];
                r.terminalVoltage = relay.volts[relay.nCoil1] - relay.volts[relay.nCoil2];
                r.energy = .5 * relay.inductance * r.current * r.current; r.position = relay.i_position;
            }
            return r;
        }
    }
    /** The only artificial behavior rejects convergence; its ordinary resistor stamp never changes. */
    private static final class ConvergenceVeto extends ResistorElm {
        final Fixture fixture; boolean armed; int wideTrials, fineTrials;
        double lastWideTime, firstFineTime, firstFineAlias, firstFineHelper;
        ConvergenceVeto(Fixture fixture, int x, int y) { super(x, y); this.fixture = fixture; }
        boolean nonLinear() { return true; }
        void stamp() { super.stamp(); sim.stampNonLinear(nodes[0]); sim.stampNonLinear(nodes[1]); }
        void doStep() {
            if (!armed) return;
            if (sim.timeStep > HALF * 1.5) {
                wideTrials++; lastWideTime = sim.t; sim.converged = false;
            } else {
                if (fineTrials++ == 0) {
                    firstFineTime = sim.t; firstFineAlias = fixture.alias(); firstFineHelper = fixture.helper();
                }
            }
        }
    }
    private static final class NativeSim extends Q30ServiceFlowContractTest.NativeServiceCirSim {
        NativeSim() {
            elmList = new Vector<CircuitElm>(); adjustables = new Vector<Adjustable>();
            undoStack = new Vector<String>(); redoStack = new Vector<String>();
        }
        @Override void stop(String message, CircuitElm element) {
            stopMessage = message; stopElm = element; circuitMatrix = null; simRunning = false; analyzeFlag = false;
        }
        @Override public void setSimRunning(boolean running) { simRunning = running; }
    }
}
