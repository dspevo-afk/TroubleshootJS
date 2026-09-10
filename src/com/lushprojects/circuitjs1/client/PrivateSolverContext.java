package com.lushprojects.circuitjs1.client;

import java.util.Vector;

/** Detached, exclusive proof graph. It never steps the captured player's elements. */
final class PrivateSolverContext {
    private final CirSim sim;
    private final Object permit = new Object();
    private final Task41SimulationSnapshot snapshot;
    private final Vector<CircuitElm> protectedElements;
    private final Vector<CircuitElm> graph = new Vector<CircuitElm>();
    private final CircuitSolverExecutor.PrivateState saved;
    private CircuitSolverExecutor.AsyncRun async;
    private boolean installed, closed, closing, closeRequested;

    private PrivateSolverContext(CirSim sim) {
        if (sim == null || !sim.isGeneratedRuntimeSettled() || sim.generatedBoardInstance == null)
            throw new IllegalStateException("Private execution requires a settled player owner");
        sim.solverExecutor.requirePublicAccess();
        this.sim = sim;
        snapshot = Task41SimulationSnapshot.capture(sim);
        protectedElements = new Vector<CircuitElm>(sim.elmList);
        saved = sim.solverExecutor.claimPrivate(permit, graph);
        try {
            snapshot.beginProof(sim);
            sim.elmList = graph;
            sim.adjustables = new Vector<Adjustable>();
            sim.undoStack = new Vector<String>(); sim.redoStack = new Vector<String>();
            sim.generatedBoardInstance = null; sim.generatedChallengeController = null;
            sim.boardModificationController = null; sim.pcbWorkbenchController = null;
            sim.scopeCount = 0; sim.scopes = new Scope[20]; sim.scopeColCount = new int[20];
            sim.t = 0; sim.timeStepAccum = 0; sim.timeStepCount = 0;
            sim.lastIterTime = 0; sim.stopMessage = null;
            sim.analyzeFlag = false; sim.dcAnalysisFlag = false;
            sim.generatedBoardVerificationPending = false;
        } catch (Throwable failure) {
            boolean restored = false;
            try {
                sim.solverExecutor.preparePrivateRestore(permit);
                snapshot.restore(sim); snapshot.assertRestored(sim); restored = true;
            } catch (Throwable cleanup) { retain(failure, cleanup);
            } finally {
                try { sim.solverExecutor.releasePrivate(permit, saved, restored); }
                catch (Throwable cleanup) { retain(failure, cleanup); }
            }
            if (failure instanceof Error) throw (Error)failure;
            if (failure instanceof RuntimeException) throw (RuntimeException)failure;
            throw new IllegalStateException("Private execution construction failed", failure);
        }
    }
    static PrivateSolverContext open(CirSim sim) { return new PrivateSolverContext(sim); }
    void install(Vector<CircuitElm> elements) {
        requireOpen();
        if (installed || elements == null || elements.isEmpty())
            throw new IllegalArgumentException("A private proof installs exactly one nonempty graph");
        for (CircuitElm element : elements) {
            if (element == null || protectedElements.contains(element) || graph.contains(element))
                throw new IllegalArgumentException("Private proof reused or duplicated a solver element");
            graph.add(element);
        }
        installed = true;
    }
    void analyze() { requireOpen(); sim.solverExecutor.analyze(permit); }
    void advanceSteps(int count) { requireOpen(); sim.solverExecutor.advanceSteps(permit, count); }
    void advanceFor(double duration) { requireOpen(); sim.solverExecutor.advanceFor(permit, duration); }
    SolverEventQueue events() { requireOpen(); return sim.solverExecutor.events(permit); }
    CircuitSolverExecutor.AsyncRun advanceAsync(double duration, CircuitSolverExecutor.Completion completion) {
        requireOpen();
        async = sim.solverExecutor.startAsync(permit, duration, completion);
        return async;
    }
    private void requireOpen() {
        if (closed || closing || closeRequested || !sim.solverExecutor.ownsPrivate(permit, graph))
            throw new SolverExecutionBoundary.Failure(SolverExecutionBoundary.Outcome.STALE_OWNER,
                "Private proof is closed or no longer owns its graph");
    }
    void close() {
        if (closed || closing) return;
        if (async != null) {
            async.deferCompletionForClose();
            async.cancel();
            if (async.isExecuting()) {
                if (!closeRequested) {
                    closeRequested = true;
                    com.google.gwt.core.client.Scheduler.get().scheduleDeferred(
                        new com.google.gwt.core.client.Scheduler.ScheduledCommand() {
                            public void execute() { close(); }
                        });
                }
                return;
            }
        }
        closing = true;
        Throwable failure = null;
        boolean restored = false;
        try {
            if (!sim.solverExecutor.ownsPrivate(permit, graph))
                throw new SolverExecutionBoundary.Failure(SolverExecutionBoundary.Outcome.STALE_OWNER,
                    "Refusing to restore an old proof over a successor graph");
            sim.solverExecutor.preparePrivateRestore(permit);
            snapshot.restore(sim); snapshot.assertRestored(sim); restored = true;
        } catch (Throwable problem) { failure = problem;
        } finally {
            try { sim.solverExecutor.releasePrivate(permit, saved, restored); }
            catch (Throwable problem) { failure = retain(failure, problem); }
            for (CircuitElm element : graph) {
                try {
                    if (sim.elmList.contains(element) || protectedElements.contains(element))
                        throw new IllegalStateException("Proof cleanup cannot delete a current owner element");
                    element.delete();
                } catch (Throwable problem) { failure = retain(failure, problem); }
            }
            closed = true; closing = false;
        }
        if (failure != null && sim.elmList == saved.graph &&
                (sim.generatedBoardInstance == saved.owner || sim == saved.owner))
            sim.setSimRunning(false); // Quarantine only the exact original owner, never a successor.
        if (async != null) async.deliverDeferredCompletion(failure != null);
        if (failure instanceof SolverExecutionBoundary.Failure &&
                ((SolverExecutionBoundary.Failure)failure).outcome == SolverExecutionBoundary.Outcome.STALE_OWNER)
            throw (SolverExecutionBoundary.Failure)failure;
        if (failure != null) {
            SolverExecutionBoundary.Failure cleanup = new SolverExecutionBoundary.Failure(
                SolverExecutionBoundary.Outcome.CLEANUP_FAILURE, "Private proof cleanup failed: " + failure.getMessage());
            cleanup.addSuppressed(failure); throw cleanup;
        }
    }
    private static Throwable retain(Throwable original, Throwable next) {
        if (original == null) return next;
        if (original != next) original.addSuppressed(next);
        return original;
    }
}
