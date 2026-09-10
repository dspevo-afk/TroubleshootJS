package com.lushprojects.circuitjs1.client;

import java.util.Vector;
import com.google.gwt.core.client.Scheduler;
import com.lushprojects.circuitjs1.client.SolverExecutionBoundary.*;

/** Real compiled ownership/failure probes, separated from normal player routes. */
final class A07SolverDeveloperVerifier {
    private final CirSim sim;
    private int assertions, pureAssertions;
    private String models, scale;
    private long started;
    private final StringBuilder cases = new StringBuilder("[");
    private final StringBuilder latencies = new StringBuilder("[");
    private A07SolverDeveloperVerifier(CirSim sim) { this.sim = sim; }
    static void start(final CirSim sim, final boolean forcedFailure) {
        Scheduler.get().scheduleDeferred(new Scheduler.ScheduledCommand() {
            public void execute() {
                try {
                    if (!sim.troubleshootDebug || !sim.troubleshootA07Verification || !sim.developerVerifierRunning)
                        throw new IllegalStateException("A07 requires the explicit developer route");
                    if (forcedFailure) throw new IllegalStateException("a07-explicit-failure-canary");
                    new A07SolverDeveloperVerifier(sim).run();
                } catch (Throwable failure) { sim.finishA07Verification(null, failure); }
            }
        });
    }
    private void run() {
        started = System.currentTimeMillis();
        pureAssertions = A07ExecutionContractVectors.run();
        observations();
        reentryAndIsolation();
        failure("nonfinite", Outcome.NUMERICAL_FAILURE);
        failure("singular", Outcome.NUMERICAL_FAILURE);
        failure("nonconvergent", Outcome.NONCONVERGENCE);
        scheduledState();
        finiteFeedback();
        staleOwner();
        cleanupFailure();
        models = A07ModelPilots.run(sim);
        scale = A01MeasurementVerifier.measureSolverScaleForA07(sim);
        asyncSuccess();
    }
    private void observations() {
        GeneratedBoardInstance owner = sim.generatedBoardInstance;
        sim.solverExecutor.advanceSteps(1);
        Observation before = sim.solverExecutor.observation(owner);
        require(before != null, "current player has an accepted observation");
        double time = sim.t;
        sim.t = time + sim.timeStep;
        require(sim.solverExecutor.observation(owner) == null, "bookkeeping time cannot mint a solved state");
        sim.t = time;
        require(sim.solverExecutor.observation(owner) == null, "restored timestamp cannot resurrect an invalidated sample");
        sim.solverExecutor.advanceSteps(1);
        Observation fresh = sim.solverExecutor.observation(owner);
        require(fresh != null && fresh != before && fresh.acceptedStep > before.acceptedStep,
            "only a new accepted solve refreshes an observation");
        String id = owner.getBoard().getPowerInputIds().get(0);
        ExternalPowerSimulationBinding source = owner.getExternalPowerBindings().getBinding(id);
        boolean connected = source.isConnected();
        source.setConnected(!connected); source.setConnected(connected);
        require(sim.solverExecutor.observation(owner) == null,
            "same-time source round trip invalidates the receipt through its revision");
        expect(Outcome.STALE_OWNER, new Runnable() { public void run() { sim.solverExecutor.advanceSteps(1); }});
        sim.needAnalyze(); sim.analyzeCircuit(); sim.analyzeFlag = false; sim.dcAnalysisFlag = false;
        sim.solverExecutor.advanceSteps(1);
        require(sim.solverExecutor.observation(owner) != null, "source change needs a real subsequent solve");
        record("accepted-state-source-and-time-identity");
    }
    private void reentryAndIsolation() {
        Task41SimulationSnapshot before = Task41SimulationSnapshot.capture(sim);
        String protectedDump = sim.dumpCircuit();
        final PrivateSolverContext proof = PrivateSolverContext.open(sim);
        try {
            Fixture fixture = new Fixture("reentry"); proof.install(fixture.elements); proof.analyze();
            double privateTime = sim.t;
            sim.updateCircuit(); sim.resetAction();
            require(sim.t == privateTime, "paint and reset cannot step an owned private graph");
            expect(Outcome.BUSY, new Runnable() { public void run() { sim.runCircuit(true); }});
            expect(Outcome.BUSY, new Runnable() { public void run() { sim.analyzeCircuit(); }});
            expect(Outcome.BUSY, new Runnable() { public void run() { sim.setBoardPowerState(BoardPowerState.POWERED); }});
            proof.advanceSteps(3);
            require(((ProbeResistor)fixture.resistor).reentryRejected,
                "a real model doStep callback cannot recursively enter the solver");
            require(sim.t > privateTime, "authorized private permit can actually solve");
        } finally { proof.close(); }
        before.assertRestored(sim);
        require(protectedDump.equals(sim.dumpCircuit()), "private execution preserved original companion/model values");
        record("exclusive-private-graph-and-reentrant-model");
    }
    private void failure(String mode, Outcome expected) {
        Task41SimulationSnapshot before = Task41SimulationSnapshot.capture(sim);
        PrivateSolverContext proof = PrivateSolverContext.open(sim);
        boolean failed = false;
        String observedFailure = "NO_FAILURE";
        try {
            sim.adjustTimeStep = false;
            Fixture fixture = new Fixture(mode); proof.install(fixture.elements);
            proof.analyze(); proof.advanceSteps(1);
        } catch (Failure failure) { failed = failure.outcome == expected; observedFailure = failure.outcome + ":" + failure.getMessage();
        } finally { proof.close(); }
        require(failed, "real CircuitJS " + mode + " requires " + expected + "; observed " + observedFailure);
        before.assertRestored(sim); record("real-" + mode);
    }
    private void scheduledState() {
        String small = scheduledTrace(1), large = scheduledTrace(5);
        require(small.equals("123") && large.equals(small), "actual accepted-step events are independent of work batching");
        record("real-accepted-step-scheduling");
    }
    private String scheduledTrace(int batch) {
        PrivateSolverContext proof = PrivateSolverContext.open(sim);
        try {
            sim.timeStep = sim.maxTimeStep = sim.minTimeStep = .0001; sim.adjustTimeStep = false;
            Fixture fixture = new Fixture("normal"); proof.install(fixture.elements); proof.analyze();
            final StringBuilder trace = new StringBuilder();
            for (int i = 1; i <= 3; i++) {
                final int event = i;
                proof.events().schedule(i * .0002, new SolverEventQueue.Action() {
                    public void fire(double due, double accepted) { trace.append(event); }
                });
            }
            for (int step = 0; step < 10; step += batch) proof.advanceSteps(batch);
            return trace.toString();
        } finally { proof.close(); }
    }
    private void finiteFeedback() {
        final PrivateSolverContext proof = PrivateSolverContext.open(sim);
        boolean exhausted = false;
        try {
            Fixture fixture = new Fixture("normal"); proof.install(fixture.elements); proof.analyze();
            final SolverEventQueue.Action[] action = new SolverEventQueue.Action[1];
            final SolverEventQueue queue = proof.events();
            action[0] = new SolverEventQueue.Action() {
                public void fire(double due, double accepted) { queue.schedule(due, action[0]); }
            };
            queue.schedule(0, action[0]);
            proof.advanceSteps(1);
        } catch (Failure failure) { exhausted = failure.outcome == Outcome.WORK_EXHAUSTED;
        } finally { proof.close(); }
        require(exhausted, "real accepted-step callback feedback must exhaust finite work");
        record("real-finite-event-feedback");
    }
    private void staleOwner() {
        Task41SimulationSnapshot before = Task41SimulationSnapshot.capture(sim);
        PrivateSolverContext proof = PrivateSolverContext.open(sim);
        final Outcome[] outcome = {Outcome.RUNNING};
        Vector<CircuitElm> successor = new Vector<CircuitElm>();
        boolean stale = false;
        try {
            Fixture fixture = new Fixture("normal"); proof.install(fixture.elements); proof.analyze();
            CircuitSolverExecutor.AsyncRun operation = proof.advanceAsync(.001,
                new CircuitSolverExecutor.Completion() {
                    public void finished(CircuitSolverExecutor.AsyncRun run) { outcome[0] = run.getOutcome(); }
                });
            // Deliberate test-only foreign installation bypass. The executor must fail closed.
            sim.elmList = successor; sim.t = 123;
            require(!operation.execute() && outcome[0] == Outcome.STALE_OWNER && !operation.canPublish(),
                "an obsolete callback cannot solve or publish against a successor graph");
            try { proof.close(); } catch (Failure failure) { stale = failure.outcome == Outcome.STALE_OWNER; }
            require(stale && sim.elmList == successor && sim.t == 123 && !operation.execute(),
                "stale proof cleanup leaves the successor graph and clock untouched");
        } finally {
            try { proof.close(); } finally { before.restore(sim); before.assertRestored(sim); }
        }
        record("real-stale-owner-callback-and-restore-refusal");
    }
    private void cleanupFailure() {
        Task41SimulationSnapshot before = Task41SimulationSnapshot.capture(sim);
        PrivateSolverContext proof = PrivateSolverContext.open(sim);
        boolean failed = false;
        try {
            Fixture fixture = new Fixture("normal"); proof.install(fixture.elements); proof.analyze();
            proof.advanceSteps(2);
            Task41SimulationSnapshot.setInjectedRestoreFailureStageForDeveloperVerification(1);
            try { proof.close(); } catch (Failure failure) { failed = failure.outcome == Outcome.CLEANUP_FAILURE; }
            require(failed && !sim.solverExecutor.isUnavailable() && !sim.simRunning &&
                sim.solverExecutor.observation(sim.generatedBoardInstance) == null,
                "failed restoration is typed, retires the lease and quarantines only the original owner");
        } finally {
            Task41SimulationSnapshot.clearInjectedRestoreFailureStageForDeveloperVerification();
            try { proof.close(); } finally { before.restore(sim); before.assertRestored(sim); }
        }
        record("real-injected-cleanup-failure-and-explicit-recovery");
    }
    private void asyncSuccess() {
        final Task41SimulationSnapshot before = Task41SimulationSnapshot.capture(sim);
        final String protectedDump = sim.dumpCircuit();
        final PrivateSolverContext proof = PrivateSolverContext.open(sim);
        try {
            sim.timeStep = sim.maxTimeStep = sim.minTimeStep = .000005; sim.adjustTimeStep = false;
            final Fixture fixture = new Fixture("async-reentry"); proof.install(fixture.elements); proof.analyze();
            final int[] heartbeats = {0};
            final com.google.gwt.user.client.Timer heartbeat = new com.google.gwt.user.client.Timer() {
                public void run() { heartbeats[0]++; schedule(0); }
            };
            CircuitSolverExecutor.AsyncRun async = proof.advanceAsync(.001, new CircuitSolverExecutor.Completion() {
                public void finished(CircuitSolverExecutor.AsyncRun run) {
                    heartbeat.cancel();
                    try {
                        require(run.getOutcome() == Outcome.COMPLETE && run.canPublish() && run.getAcceptedSteps() == 200,
                            "real yielded operation publishes only its completed accepted state: " + run.getOutcome() + " " + run.getDiagnostic());
                        require(heartbeats[0] > 1, "independent browser callbacks run between multiple private execution slices");
                        require(((ProbeResistor)fixture.resistor).reentryRejected, "the same async lease rejects recursive execution");
                        latency("completed", run);
                        proof.close(); before.assertRestored(sim);
                        require(protectedDump.equals(sim.dumpCircuit()) && !run.canPublish(),
                            "closing a proof retires its receipt without changing player model state");
                        record("real-browser-yield-and-accepted-publication");
                        deferCancel(0);
                    } catch (Throwable failure) { fail(proof, failure); }
                }
            });
            ((ProbeResistor)fixture.resistor).cancelRun = async;
            heartbeat.schedule(0);
        } catch (Throwable failure) { fail(proof, failure); }
    }
    private void deferCancel(final int mode) {
        Scheduler.get().scheduleDeferred(new Scheduler.ScheduledCommand() {
            public void execute() {
                try { if (mode < 5) asyncCancel(mode); else complete(); }
                catch (Throwable failure) { sim.finishA07Verification(null, failure); }
            }
        });
    }
    private void asyncCancel(final int mode) {
        final Task41SimulationSnapshot before = Task41SimulationSnapshot.capture(sim);
        final String protectedDump = sim.dumpCircuit();
        final PrivateSolverContext proof = PrivateSolverContext.open(sim);
        try {
            sim.timeStep = sim.maxTimeStep = sim.minTimeStep = .000005; sim.adjustTimeStep = false;
            final Fixture fixture = new Fixture(mode == 2 ? "cancel-trial" : mode == 3 ? "cancel-final" : "normal");
            proof.install(fixture.elements); proof.analyze();
            final CircuitSolverExecutor.AsyncRun run = proof.advanceAsync(mode == 3 ? .000015 : .01,
                new CircuitSolverExecutor.Completion() {
                    public void finished(CircuitSolverExecutor.AsyncRun operation) {
                        try {
                            require(operation.getOutcome() == Outcome.CANCELLED && !operation.canPublish(),
                                "cancel mode " + mode + " cannot complete or publish: " + operation.getOutcome());
                            int accepted = operation.getAcceptedSteps();
                            require(mode == 0 || mode == 2 || mode == 4 ? accepted == 0 : mode == 3 ? accepted == 2 : accepted > 0,
                                "cancel mode " + mode + " stops at its actual execution boundary");
                            proof.close(); before.assertRestored(sim);
                            require(protectedDump.equals(sim.dumpCircuit()), "cancel mode " + mode + " did not step player elements");
                            require(!operation.execute() && !operation.canPublish(),
                                "retired queued callback cannot advance or publish after cancel mode " + mode);
                            before.assertRestored(sim);
                            latency("cancel-" + mode, operation);
                            record("cancel-" + mode + "-and-obsolete-callback");
                            deferCancel(mode + 1);
                        } catch (Throwable failure) { fail(proof, failure); }
                    }
                });
            ((ProbeResistor)fixture.resistor).cancelRun = run;
            if (mode == 4) proof.close();
            else if (mode == 0) run.cancel();
            else if (mode == 1) {
                new com.google.gwt.user.client.Timer() {
                    public void run() { run.cancel(); }
                }.schedule(0); // Real queued browser cancellation after the first slice.
            }
            else run.execute();
        } catch (Throwable failure) { fail(proof, failure); }
    }
    // Terminal developer-only cases deliberately replace the disposable route's
    // board through public schematic entrypoints, after all private-owner proofs.
    private void schematicLifecycles() {
        sim.setSimRunning(false);
        Fixture fixture = new Fixture("normal");
        String schematic = fixture.source.dump() + "\n" + fixture.resistor.dump() + "\n" +
            fixture.elements.get(2).dump() + "\n";
        sim.readCircuit(schematic, CirSim.RC_NO_CENTER);
        schematicSteps(10);
        Vector<CircuitElm> sameList = sim.elmList;
        SolverEventQueue oldQueue = sim.solverExecutor.events(null);
        double previousTime = sim.t;
        require(oldQueue.nextTime() == Double.POSITIVE_INFINITY, "reload starts with an empty event queue");
        sim.readCircuit(schematic, CirSim.RC_NO_CENTER);
        require(sim.elmList == sameList && sim.t == 0, "reload reuses the list but restarts the circuit clock");
        schematicSteps(1);
        require(sim.t > 0 && sim.t < previousTime, "first reloaded step accepts before the old clock catches up");
        require(sim.solverExecutor.events(null) != oldQueue, "replacement retires even an empty old queue");
        record("schematic-reload-empty-event-clock");

        schematicSteps(9);
        final int[] staleCallbacks = {0};
        SolverEventQueue.Action stale = new SolverEventQueue.Action() {
            public void fire(double due, double accepted) { staleCallbacks[0]++; }
        };
        oldQueue = sim.solverExecutor.events(null);
        double oldDue = sim.t + sim.timeStep;
        oldQueue.schedule(oldDue, stale);
        sim.readCircuit(schematic, CirSim.RC_NO_CENTER);
        schematicSteps(20);
        require(sim.t > oldDue && staleCallbacks[0] == 0, "old scheduled callbacks never enter the replacement circuit");
        require(sim.elmList == sameList && sim.solverExecutor.events(null) != oldQueue,
            "same-list replacement gets a distinct execution queue");
        record("schematic-reload-discards-old-events");

        final int[] retainedCallbacks = {0};
        SolverEventQueue.Action retained = new SolverEventQueue.Action() {
            public void fire(double due, double accepted) { retainedCallbacks[0]++; }
        };
        SolverEventQueue currentQueue = sim.solverExecutor.events(null);
        currentQueue.schedule(sim.t, retained);
        previousTime = sim.t;
        sim.needAnalyze();
        require(sim.t == previousTime && sim.solverExecutor.events(null) == currentQueue,
            "ordinary reanalysis keeps the event phase and queue");
        schematicSteps(1);
        require(retainedCallbacks[0] == 1 && sim.t > previousTime, "pending events survive ordinary reanalysis");
        record("schematic-reanalysis-preserves-events");

        currentQueue.schedule(sim.t, retained);
        previousTime = sim.t;
        CircuitElm retainedElement = sim.getElm(1);
        int elementCount = sim.elmList.size();
        sim.readCircuit(fixture.resistor.dump() + "\n", CirSim.RC_RETAIN | CirSim.RC_NO_CENTER);
        require(sim.elmList == sameList && sim.elmList.size() == elementCount + 1 && sim.getElm(1) == retainedElement,
            "retaining import appends without replacing the current elements");
        require(sim.t == previousTime && sim.solverExecutor.events(null) == currentQueue,
            "retaining import preserves the existing clock and queue");
        schematicSteps(1);
        require(retainedCallbacks[0] == 2 && sim.t > previousTime, "pending events survive retaining import");
        record("schematic-retaining-import-preserves-events");

        sim.readCircuit(schematic, CirSim.RC_NO_CENTER);
        schematicSteps(10);
        sim.undoStack.clear(); sim.redoStack.clear();
        sim.pushUndo();
        fixture.resistor.setResistance(2000);
        String changed = fixture.source.dump() + "\n" + fixture.resistor.dump() + "\n" +
            fixture.elements.get(2).dump() + "\n";
        sim.readCircuit(changed, CirSim.RC_NO_CENTER);
        schematicSteps(10);
        oldQueue = sim.solverExecutor.events(null);
        oldDue = sim.t + sim.timeStep;
        oldQueue.schedule(oldDue, stale);
        sim.doUndo();
        require(sim.elmList == sameList && sim.t == 0 && ((ResistorElm)sim.getElm(1)).getResistance() == 1000,
            "actual undo restores its schematic and restarts the reused-list clock");
        schematicSteps(1);
        require(sim.t > 0 && sim.t < oldDue && sim.solverExecutor.events(null) != oldQueue,
            "first undo step is accepted with a fresh queue");
        sim.solverExecutor.advanceSteps(19);
        require(sim.t > oldDue && staleCallbacks[0] == 0, "undo discards the prior schematic's callbacks");
        record("schematic-undo-retires-event-clock");

        oldQueue = sim.solverExecutor.events(null);
        oldDue = sim.t + sim.timeStep;
        oldQueue.schedule(oldDue, stale);
        sim.doRedo();
        require(sim.elmList == sameList && sim.t == 0 && ((ResistorElm)sim.getElm(1)).getResistance() == 2000,
            "actual redo restores its schematic and restarts the reused-list clock");
        schematicSteps(1);
        require(sim.t > 0 && sim.t < oldDue && sim.solverExecutor.events(null) != oldQueue,
            "first redo step is accepted with a fresh queue");
        sim.solverExecutor.advanceSteps(39);
        require(sim.t > oldDue && staleCallbacks[0] == 0, "redo discards the prior schematic's callbacks");
        require(!sim.solverExecutor.isUnavailable(), "schematic lifecycle probes release every solver lease");
        record("schematic-redo-retires-event-clock");
    }
    private void schematicSteps(int count) {
        sim.timeStep = sim.maxTimeStep = sim.minTimeStep = .0001;
        sim.adjustTimeStep = false;
        sim.analyzeCircuit(); sim.analyzeFlag = false; sim.dcAnalysisFlag = false;
        sim.solverExecutor.advanceSteps(count);
    }
    private void complete() {
        require(!sim.solverExecutor.isUnavailable() && sim.isGeneratedRuntimeSettled(), "all proof leases were released");
        schematicLifecycles();
        cases.append("]"); latencies.append("]");
        String report = "{\"version\":\"TSJ-A07-SOLVER-1\",\"status\":\"PASS\"," +
            "\"pureAssertions\":" + pureAssertions + ",\"runtimeAssertions\":" + assertions +
            ",\"runtimeCases\":" + cases + ",\"latencies\":" + latencies +
            ",\"models\":" + models + ",\"scale\":" + scale +
            ",\"wallMs\":" + (System.currentTimeMillis() - started) + ",\"cleanup\":\"PASS\"}";
        sim.finishA07Verification(report, null);
    }
    private void fail(PrivateSolverContext proof, Throwable primary) {
        try { proof.close(); } catch (Throwable cleanup) { if (cleanup != primary) primary.addSuppressed(cleanup); }
        sim.finishA07Verification(null, primary);
    }
    private void require(boolean value, String message) {
        assertions++; if (!value) throw new AssertionError("A07 runtime: " + message);
    }
    private void expect(Outcome outcome, Runnable action) {
        boolean rejected = false;
        try { action.run(); } catch (Failure failure) { rejected = failure.outcome == outcome; }
        require(rejected, "expected actual execution failure " + outcome);
    }
    private void latency(String name, CircuitSolverExecutor.AsyncRun run) {
        require(run.getWallMillis() >= 0 && run.getMaxSliceMillis() >= 0 && run.getMaxYieldMillis() >= 0 &&
            run.getCancellationMillis() >= -1, "finite nonnegative millisecond-resolution latency observations");
        if (latencies.length() > 1) latencies.append(",");
        latencies.append("{\"case\":\"").append(name).append("\",\"slices\":").append(run.getSliceCount())
            .append(",\"acceptedSteps\":").append(run.getAcceptedSteps())
            .append(",\"wallMs\":").append(run.getWallMillis())
            .append(",\"maxSliceMs\":").append(run.getMaxSliceMillis())
            .append(",\"maxYieldMs\":").append(run.getMaxYieldMillis())
            .append(",\"cancelToTerminalMs\":");
        if (run.getCancellationMillis() < 0) latencies.append("null");
        else latencies.append(run.getCancellationMillis());
        latencies.append("}");
    }
    private void record(String name) {
        if (cases.length() > 1) cases.append(",");
        cases.append("{\"case\":\"").append(name).append("\",\"status\":\"PASS\"}");
    }
    private static final class Fixture {
        final Vector<CircuitElm> elements = new Vector<CircuitElm>();
        final DCVoltageElm source;
        final ResistorElm resistor;
        Fixture(String mode) {
            source = new DCVoltageElm(64, 256); source.drag(64, 128);
            source.maxVoltage = "nonfinite".equals(mode) ? Double.NaN : 5;
            resistor = new ProbeResistor(64, 128, mode); resistor.drag(64, 256); resistor.setResistance(1000);
            GroundElm ground = new GroundElm(64, 256); ground.drag(64, 288);
            elements.add(source); elements.add(resistor); elements.add(ground);
        }
    }
    private static final class ProbeResistor extends ResistorElm {
        final String mode;
        boolean reentryRejected;
        int finishedSteps;
        CircuitSolverExecutor.AsyncRun cancelRun;
        ProbeResistor(int x, int y, String mode) { super(x, y); this.mode = mode; }
        int getVoltageSourceCount() { return "singular".equals(mode) ? 1 : 0; }
        boolean nonLinear() { return "nonconvergent".equals(mode) || "reentry".equals(mode); }
        void doStep() {
            super.doStep();
            if ("nonconvergent".equals(mode)) sim.converged = false;
            if ("reentry".equals(mode) && !reentryRejected) {
                try { sim.runCircuit(true); }
                catch (Failure failure) { if (failure.outcome == Outcome.BUSY) reentryRejected = true; else throw failure; }
            }
            if ("async-reentry".equals(mode) && cancelRun != null && !reentryRejected) {
                try { cancelRun.execute(); }
                catch (Failure failure) { if (failure.outcome == Outcome.BUSY) reentryRejected = true; else throw failure; }
            }
            if ("cancel-trial".equals(mode) && cancelRun != null) cancelRun.cancel();
        }
        void stepFinished() {
            super.stepFinished(); finishedSteps++;
            if ("cancel-final".equals(mode) && finishedSteps == 3 && cancelRun != null) cancelRun.cancel();
        }
    }
}
