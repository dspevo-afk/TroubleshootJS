package com.lushprojects.circuitjs1.client;

import java.util.Vector;
import com.lushprojects.circuitjs1.client.SolverExecutionBoundary.Operation;
import com.lushprojects.circuitjs1.client.SolverExecutionBoundary.Observation;
import com.lushprojects.circuitjs1.client.SolverExecutionBoundary.Outcome;
import com.lushprojects.circuitjs1.client.SolverExecutionBoundary.Failure;

/** The sole entry to this singleton-sensitive CircuitJS analysis/stepping context. */
final class CircuitSolverExecutor {
    static final long WALL_LIMIT_MS = 500;
    // Existing synchronous temporal consumers request up to 200,000 accepted
    // steps. Their total budget is distinct from a UI slice or one stiff step.
    static final long TEMPORAL_WALL_LIMIT_MS = 5000;
    static final int STEP_LIMIT = 200000;
    static final int TRIAL_LIMIT = 1000000;
    private final CirSim sim;
    private final SolverExecutionBoundary boundary = new SolverExecutionBoundary();
    private Object boundOwner, boundGraph, privateOwner;
    private Vector<CircuitElm> privateGraph;
    private GeneratedExternalPowerBindings.ControlObservation observedControls;
    private boolean restoringPrivate;
    private boolean requiresAnalysis = true;
    private Operation stepOperation;
    private int stepAcceptedCount;
    private long stepStartedAt;
    private SolverEventQueue events = new SolverEventQueue(1024, 1024, 0);
    int goodIterations = 100;
    boolean goodIteration = true;

    CircuitSolverExecutor(CirSim sim) { this.sim = sim; }
    boolean isUnavailable() { return privateOwner != null || boundary.isBusy(); }
    void requirePublicAccess() {
        if (privateOwner != null) throw new Failure(Outcome.BUSY, "A private solver proof owns the context");
        boundary.requireIdle();
    }
    private void access(Object permit) {
        if (permit != privateOwner)
            throw new Failure(Outcome.BUSY, "Solver access requires its exact private permit");
        boundary.requireIdle();
    }
    private Object currentOwner() {
        return privateOwner != null ? privateOwner :
            sim.generatedBoardInstance != null ? sim.generatedBoardInstance : sim;
    }
    private boolean controlsCurrent() {
        return sim.generatedBoardInstance == null ? observedControls == null :
            observedControls != null && observedControls.isCurrent();
    }
    private void bindCurrent() {
        if (CircuitElm.sim != sim || sim.elmList == null)
            throw new Failure(Outcome.STALE_OWNER, "CircuitJS singleton context changed");
        if (privateOwner != null && (sim.elmList != privateGraph || sim.generatedBoardInstance != null))
            throw new Failure(Outcome.STALE_OWNER, "Private solver graph was replaced");
        Object owner = currentOwner();
        if (boundOwner != owner || boundGraph != sim.elmList) {
            boundary.bind(owner, sim.elmList);
            boundOwner = owner; boundGraph = sim.elmList; requiresAnalysis = true;
            events = new SolverEventQueue(1024, 1024, sim.t);
            goodIterations = 100; goodIteration = true;
        } else if (!controlsCurrent()) { boundary.invalidate(); requiresAnalysis = true; }
        Observation priorSample = boundary.observation();
        if (priorSample != null && priorSample.simulationTime != sim.t) boundary.invalidate();
        observedControls = sim.generatedBoardInstance == null ? null :
            sim.generatedBoardInstance.getExternalPowerBindings().observeControls();
    }
    void invalidate() {
        if (restoringPrivate) return;
        requirePublicAccess(); bindCurrent(); boundary.invalidate(); requiresAnalysis = true;
    }
    void retire() {
        if (restoringPrivate) return;
        requirePublicAccess(); boundary.retire();
        boundOwner = null; boundGraph = null;
        events = new SolverEventQueue(1024, 1024, sim.t);
        goodIterations = 100; goodIteration = true;
    }
    Observation observation(GeneratedBoardInstance expectedOwner) {
        if (isUnavailable() || expectedOwner == null || sim.generatedBoardInstance != expectedOwner ||
                sim.stopMessage != null || sim.analyzeFlag || sim.dcAnalysisFlag) return null;
        bindCurrent();
        Observation sample = boundary.observation();
        return boundary.isCurrent(sample, expectedOwner, sim.elmList, sim.t) ? sample : null;
    }
    boolean isCurrent(Observation sample, GeneratedBoardInstance expectedOwner) {
        return sample != null && observation(expectedOwner) == sample;
    }
    void analyze() { analyze(null); }
    void analyze(Object permit) {
        access(permit); bindCurrent(); boundary.invalidate(); requiresAnalysis = true;
        Operation operation = begin(permit, 1, true);
        Outcome result = Outcome.COMPLETE;
        try {
            sim.analyzeCircuitOwned(operation);
            check(operation);
            requireSolvedMatrix(); requiresAnalysis = false;
        } catch (RuntimeException failure) {
            result = outcome(failure); throw failure;
        } catch (Error failure) { result = Outcome.NUMERICAL_FAILURE; throw failure;
        } finally { boundary.finish(operation, result); }
    }
    void advanceUi(boolean didAnalyze) {
        Operation operation = begin(null, STEP_LIMIT);
        Outcome result = Outcome.COMPLETE;
        try {
            sim.runCircuitOwned(operation, didAnalyze, 0);
            check(operation); requireSolvedMatrix();
        } catch (RuntimeException failure) {
            result = outcome(failure); throw failure;
        } catch (Error failure) { result = Outcome.NUMERICAL_FAILURE; throw failure;
        } finally { boundary.finish(operation, result); }
    }
    void advanceSteps(int count) { advanceSteps(null, count); }
    void advanceSteps(Object permit, int count) {
        Operation operation = begin(permit, count);
        Outcome result = Outcome.COMPLETE;
        try {
            for (int step = 0; step < count; step++) advanceOne(operation);
            check(operation);
        } catch (RuntimeException failure) {
            result = outcome(failure); throw failure;
        } catch (Error failure) { result = Outcome.NUMERICAL_FAILURE; throw failure;
        } finally { boundary.finish(operation, result); }
    }
    void advanceFor(double duration) { advanceFor(null, duration); }
    void advanceFor(Object permit, double duration) {
        double target = target(duration);
        Operation operation = begin(permit, STEP_LIMIT, false, TEMPORAL_WALL_LIMIT_MS);
        Outcome result = Outcome.COMPLETE;
        try {
            while (sim.t + 1e-12 < target) advanceOne(operation);
            check(operation);
        } catch (RuntimeException failure) {
            result = outcome(failure); throw failure;
        } catch (Error failure) { result = Outcome.NUMERICAL_FAILURE; throw failure;
        } finally { boundary.finish(operation, result); }
    }
    private Operation begin(Object permit, int count) { return begin(permit, count, false); }
    private Operation begin(Object permit, int count, boolean analyzing) {
        return begin(permit, count, analyzing, WALL_LIMIT_MS);
    }
    private Operation begin(Object permit, int count, boolean analyzing, long wallLimit) {
        access(permit); bindCurrent();
        if (!analyzing && requiresAnalysis)
            throw new Failure(Outcome.STALE_OWNER, "CircuitJS graph/source revision requires analysis before stepping");
        if (count < 1 || count > STEP_LIMIT) throw new IllegalArgumentException("Invalid solver step limit");
        return boundary.begin(count, TRIAL_LIMIT, System.currentTimeMillis(), wallLimit, sim.t);
    }
    void check(Operation operation) {
        boundary.check(operation, System.currentTimeMillis());
        if (CircuitElm.sim != sim || operation.owner != currentOwner() || operation.graph != sim.elmList ||
                !controlsCurrent() ||
                (privateOwner != null && (sim.elmList != privateGraph || sim.generatedBoardInstance != null)))
            throw new Failure(Outcome.STALE_OWNER, "Solver state changed during an owned operation");
    }
    void beginTrial(Operation operation) {
        check(operation);
        long now = System.currentTimeMillis();
        if (stepOperation != operation || stepAcceptedCount != operation.acceptedSteps) {
            stepOperation = operation; stepAcceptedCount = operation.acceptedSteps; stepStartedAt = now;
        } else if (now < stepStartedAt || now - stepStartedAt >= WALL_LIMIT_MS) {
            throw new Failure(Outcome.DEADLINE, "One CircuitJS accepted-step attempt exceeded its wall budget");
        }
        boundary.beginTrial(operation, now);
    }
    void accepted(Operation operation) {
        check(operation);
        requireFinite(sim.nodeVoltages); requireFinite(sim.circuitRightSide);
        events.dispatchAccepted(sim.t);
        boundary.accepted(operation, sim.t, System.currentTimeMillis());
    }
    void requireFinite(double[] values) {
        if (values == null) return;
        for (double value : values)
            if (!SolverExecutionBoundary.finite(value))
                throw new Failure(Outcome.NUMERICAL_FAILURE, "CircuitJS produced a nonfinite solved value");
    }
    private void requireSolvedMatrix() {
        if (sim.stopMessage != null)
            throw new Failure(sim.stopMessage.indexOf("Convergence") >= 0 ? Outcome.NONCONVERGENCE :
                Outcome.NUMERICAL_FAILURE, "CircuitJS stopped: " + sim.stopMessage);
        if (sim.circuitMatrix == null && !sim.elmList.isEmpty())
            throw new Failure(Outcome.NUMERICAL_FAILURE, "CircuitJS has no analyzed matrix");
    }
    private void advanceOne(Operation operation) {
        check(operation);
        int before = operation.acceptedSteps;
        sim.runCircuitOwned(operation, true, 1);
        check(operation); requireSolvedMatrix();
        if (operation.acceptedSteps != before + 1)
            throw new Failure(Outcome.NONCONVERGENCE, "CircuitJS did not accept the requested step");
    }
    private double target(double duration) {
        if (!SolverExecutionBoundary.finite(duration) || duration <= 0 ||
                !SolverExecutionBoundary.finite(sim.t + duration) || sim.t + duration <= sim.t)
            throw new IllegalArgumentException("Invalid simulation-time duration");
        return sim.t + duration;
    }
    private static Outcome outcome(RuntimeException failure) {
        return failure instanceof Failure ? ((Failure) failure).outcome : Outcome.NUMERICAL_FAILURE;
    }
    SolverEventQueue events(Object permit) {
        access(permit); bindCurrent(); return events;
    }
    static final class PrivateState {
        final Object owner, graph;
        final SolverEventQueue events;
        final int goodIterations;
        final boolean goodIteration, requiresAnalysis;
        PrivateState(Object owner, Object graph, SolverEventQueue events, int goodIterations, boolean goodIteration, boolean requiresAnalysis) {
            this.owner = owner; this.graph = graph; this.events = events;
            this.goodIterations = goodIterations; this.goodIteration = goodIteration; this.requiresAnalysis = requiresAnalysis;
        }
    }
    PrivateState snapshotState() {
        requirePublicAccess(); bindCurrent();
        return new PrivateState(boundOwner, boundGraph, events, goodIterations, goodIteration, requiresAnalysis);
    }
    void snapshotRestored(PrivateState saved) {
        if (privateOwner != null) return; // Exact private permit owns its separate restoration.
        requirePublicAccess(); boundary.retire(); boundOwner = null; boundGraph = null; bindCurrent();
        if (boundOwner == saved.owner && boundGraph == saved.graph) {
            events = saved.events; goodIterations = saved.goodIterations; goodIteration = saved.goodIteration;
            requiresAnalysis = saved.requiresAnalysis;
        }
    }
    void preparePrivateRestore(Object permit) {
        if (permit != privateOwner) throw new Failure(Outcome.STALE_OWNER, "Private restore permit retired");
        boundary.requireIdle(); restoringPrivate = true;
    }
    PrivateState claimPrivate(Object permit, Vector<CircuitElm> graph) {
        requirePublicAccess(); bindCurrent();
        if (permit == null || graph == null || graph == sim.elmList)
            throw new IllegalArgumentException("Private execution needs detached graph ownership");
        PrivateState saved = new PrivateState(boundOwner, boundGraph, events, goodIterations, goodIteration, requiresAnalysis);
        boundary.invalidate(); privateOwner = permit; privateGraph = graph;
        return saved;
    }
    boolean ownsPrivate(Object permit, Vector<CircuitElm> graph) {
        return privateOwner == permit && privateGraph == graph && sim.elmList == graph &&
            sim.generatedBoardInstance == null && CircuitElm.sim == sim;
    }
    void releasePrivate(Object permit, PrivateState saved, boolean restored) {
        if (privateOwner != permit) throw new Failure(Outcome.STALE_OWNER, "Private solver permit retired");
        boundary.requireIdle(); privateOwner = null; privateGraph = null; restoringPrivate = false; boundary.retire();
        bindCurrent();
        if (restored && boundOwner == saved.owner && boundGraph == saved.graph) {
            events = saved.events; goodIterations = saved.goodIterations; goodIteration = saved.goodIteration;
            requiresAnalysis = saved.requiresAnalysis;
        }
    }
    interface Completion { void finished(AsyncRun run); }
    final class AsyncRun {
        private final Operation operation;
        private final double target;
        private final Completion completion;
        private boolean done, insideSlice, deferCompletion, completionDelivered;
        private int slices;
        private long lastSliceEnded = -1, maxSliceMillis, maxYieldMillis, finishedAt;
        private long cancelledAt = -1;
        private Outcome result = Outcome.RUNNING;
        private String diagnostic = "";
        private Observation sample;
        private final com.google.gwt.user.client.Timer continuation = new com.google.gwt.user.client.Timer() {
            public void run() { if (execute()) schedule(0); }
        };
        private AsyncRun(Object permit, double duration, Completion completion) {
            if (completion == null) throw new IllegalArgumentException("Missing execution completion");
            target = target(duration); this.completion = completion;
            operation = begin(permit, STEP_LIMIT);
        }
        public boolean execute() {
            if (done) return false;
            if (insideSlice) throw new Failure(Outcome.BUSY, "The same async solver lease cannot be reentered");
            insideSlice = true;
            long sliceStarted = System.currentTimeMillis();
            slices++;
            if (lastSliceEnded >= 0) maxYieldMillis = Math.max(maxYieldMillis, sliceStarted - lastSliceEnded);
            Outcome terminal = null;
            try {
                long start = System.currentTimeMillis();
                int steps = 0;
                check(operation);
                while (sim.t + 1e-12 < target && steps < 16 && System.currentTimeMillis() - start < 8) {
                    advanceOne(operation); steps++;
                }
                check(operation);
                if (sim.t + 1e-12 >= target) terminal = Outcome.COMPLETE;
            } catch (RuntimeException failure) {
                terminal = outcome(failure); diagnostic = failure.getMessage();
            } catch (Error failure) {
                terminal = Outcome.NUMERICAL_FAILURE; diagnostic = failure.getMessage();
            } finally {
                insideSlice = false;
                lastSliceEnded = System.currentTimeMillis();
                maxSliceMillis = Math.max(maxSliceMillis, lastSliceEnded - sliceStarted);
            }
            if (terminal != null) finish(terminal);
            return !done;
        }
        void cancel() {
            if (done) return;
            if (cancelledAt < 0) cancelledAt = System.currentTimeMillis();
            boundary.cancel(operation);
            if (!insideSlice) finish(Outcome.CANCELLED);
        }
        private void finish(Outcome outcome) {
            if (done) return;
            continuation.cancel();
            result = boundary.finish(operation, outcome); done = true; finishedAt = System.currentTimeMillis();
            sample = result == Outcome.COMPLETE ? boundary.observation() : null;
            if (!deferCompletion) deliverDeferredCompletion(false);
        }
        boolean isExecuting() { return insideSlice; }
        void deferCompletionForClose() { deferCompletion = true; }
        void deliverDeferredCompletion(boolean cleanupFailed) {
            if (!done || completionDelivered) return;
            if (cleanupFailed) { result = Outcome.CLEANUP_FAILURE; sample = null; }
            completionDelivered = true;
            completion.finished(this);
        }
        Outcome getOutcome() { return result; }
        String getDiagnostic() { return diagnostic; }
        int getAcceptedSteps() { return operation.acceptedSteps; }
        int getSliceCount() { return slices; }
        long getWallMillis() { return finishedAt - operation.startedAt; }
        long getMaxSliceMillis() { return maxSliceMillis; }
        long getMaxYieldMillis() { return maxYieldMillis; }
        long getCancellationMillis() { return cancelledAt < 0 ? -1 : finishedAt - cancelledAt; }
        boolean canPublish() {
            return done && sample != null && operation.owner == currentOwner() && operation.graph == sim.elmList &&
                CircuitElm.sim == sim && sim.t == sample.simulationTime && controlsCurrent() &&
                boundary.canPublish(operation, sample);
        }
    }
    AsyncRun startAsync(Object permit, double duration, Completion completion) {
        AsyncRun run = new AsyncRun(permit, duration, completion);
        // A distinct timer turn guarantees a browser yield between work slices.
        run.continuation.schedule(0);
        return run;
    }
}
