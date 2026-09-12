package com.lushprojects.circuitjs1.client;

/** Exclusive solver work and accepted-state identity; no electrical model. */
final class SolverExecutionBoundary {
    enum Outcome { RUNNING, COMPLETE, CANCELLED, STALE_OWNER, BUSY,
        NUMERICAL_FAILURE, NONCONVERGENCE, WORK_EXHAUSTED, DEADLINE, CLEANUP_FAILURE }
    static final class Failure extends IllegalStateException {
        final Outcome outcome;
        Failure(Outcome outcome, String message) { super(message); this.outcome = outcome; }
    }
    static final class Observation {
        final Object owner, graph;
        final long generation, revision, acceptedStep, operation;
        final double simulationTime;
        private Observation(Object owner, Object graph, long generation, long revision,
                long acceptedStep, long operation, double simulationTime) {
            this.owner = owner; this.graph = graph; this.generation = generation;
            this.revision = revision; this.acceptedStep = acceptedStep;
            this.operation = operation; this.simulationTime = simulationTime;
        }
    }
    static final class Operation {
        final Object owner, graph;
        final long generation, revision, id, startedAt, deadline;
        final int acceptedLimit, trialLimit;
        int acceptedSteps, nonlinearTrials;
        boolean trialInProgress;
        long lastWallTime;
        double lastAcceptedTime;
        Outcome outcome = Outcome.RUNNING;
        private Observation candidate;
        private Operation(Object owner, Object graph, long generation, long revision,
                long id, int acceptedLimit, int trialLimit, long now, long wallLimit,
                double initialTime) {
            this.owner = owner; this.graph = graph; this.generation = generation;
            this.revision = revision; this.id = id; this.acceptedLimit = acceptedLimit;
            this.trialLimit = trialLimit; startedAt = now; lastWallTime = now;
            deadline = now + wallLimit; lastAcceptedTime = initialTime;
        }
    }
    private Object owner, graph;
    private long generation, revision, acceptedSerial, operationSerial;
    private Operation active;
    private Observation latest;

    void bind(Object nextOwner, Object nextGraph) {
        if (nextOwner == null || nextGraph == null)
            throw new IllegalArgumentException("Solver owner and graph identities are required");
        if (owner == nextOwner && graph == nextGraph) return;
        requireIdle();
        generation = increment(generation); revision = increment(revision);
        owner = nextOwner; graph = nextGraph; latest = null;
    }
    void invalidate() {
        requireIdle(); revision = increment(revision); latest = null;
    }
    void retire() {
        generation = increment(generation); revision = increment(revision); latest = null;
        if (active != null) active.outcome = Outcome.STALE_OWNER;
        active = null;
    }
    void requireIdle() {
        if (active != null) throw new Failure(Outcome.BUSY, "CircuitJS execution is already owned");
    }
    boolean isBusy() { return active != null; }
    long getGeneration() { return generation; }
    long getRevision() { return revision; }
    Operation begin(int acceptedLimit, int trialLimit, long now, long wallLimit, double initialTime) {
        requireIdle();
        if (owner == null || graph == null || acceptedLimit < 1 || trialLimit < 1 ||
                now < 0 || wallLimit < 1 || now > Long.MAX_VALUE - wallLimit || !finite(initialTime))
            throw new IllegalArgumentException("Invalid bounded solver request");
        operationSerial = increment(operationSerial);
        active = new Operation(owner, graph, generation, revision, operationSerial,
            acceptedLimit, trialLimit, now, wallLimit, initialTime);
        active.candidate = latest; // A no-work tick retains, but never refreshes, a sample.
        return active;
    }
    void check(Operation operation, long now) {
        if (operation == null || operation != active || operation.owner != owner ||
                operation.graph != graph || operation.generation != generation ||
                operation.revision != revision)
            throw new Failure(Outcome.STALE_OWNER, "Retired or foreign solver operation");
        if (operation.outcome != Outcome.RUNNING)
            throw new Failure(operation.outcome, "Solver operation is " + operation.outcome);
        if (now < operation.lastWallTime || now >= operation.deadline) {
            operation.outcome = Outcome.DEADLINE;
            throw new Failure(Outcome.DEADLINE, "Solver deadline reached or wall clock regressed; accepted=" + operation.acceptedSteps +
                "; trials=" + operation.nonlinearTrials);
        }
        operation.lastWallTime = now;
    }
    void beginTrial(Operation operation, long now) {
        check(operation, now);
        if (operation.acceptedSteps >= operation.acceptedLimit ||
                operation.nonlinearTrials >= operation.trialLimit) {
            operation.outcome = Outcome.WORK_EXHAUSTED;
            throw new Failure(Outcome.WORK_EXHAUSTED, "Solver deterministic work budget exhausted");
        }
        operation.nonlinearTrials++; operation.trialInProgress = true;
        // Rejected trials may mutate companion models, so no old sample is publishable.
        latest = null; operation.candidate = null;
    }
    void accepted(Operation operation, double time, long now) {
        check(operation, now);
        if (!operation.trialInProgress || !finite(time) || time <= operation.lastAcceptedTime) {
            operation.outcome = Outcome.NUMERICAL_FAILURE;
            throw new Failure(Outcome.NUMERICAL_FAILURE, "Accepted simulation time must advance finitely");
        }
        if (operation.acceptedSteps >= operation.acceptedLimit) {
            operation.outcome = Outcome.WORK_EXHAUSTED;
            throw new Failure(Outcome.WORK_EXHAUSTED, "Accepted step budget exhausted");
        }
        acceptedSerial = increment(acceptedSerial); operation.acceptedSteps++;
        operation.lastAcceptedTime = time; operation.trialInProgress = false;
        // An in-flight operation cannot expose an observation. Keep the
        // accepted identity/time, and allocate its one final value at finish.
    }
    void cancel(Operation operation) {
        if (operation != null && operation == active && operation.outcome == Outcome.RUNNING)
            operation.outcome = Outcome.CANCELLED;
    }
    Outcome finish(Operation operation, Outcome requested) {
        if (operation == null || operation != active) return Outcome.STALE_OWNER;
        if (requested == null || requested == Outcome.RUNNING)
            throw new IllegalArgumentException("Solver completion requires a terminal outcome");
        Outcome result = operation.outcome == Outcome.RUNNING ? requested : operation.outcome;
        if (result == Outcome.COMPLETE && operation.trialInProgress) result = Outcome.NONCONVERGENCE;
        if (result == Outcome.COMPLETE && operation.acceptedSteps > 0)
            operation.candidate = new Observation(owner, graph, generation, revision,
                acceptedSerial, operation.id, operation.lastAcceptedTime);
        operation.outcome = result; active = null;
        latest = result == Outcome.COMPLETE ? operation.candidate : null;
        return result;
    }
    Observation observation() { return active == null ? latest : null; }
    boolean isCurrent(Observation observation, Object expectedOwner, Object expectedGraph, double time) {
        return active == null && observation != null && observation == latest &&
            observation.owner == owner && observation.owner == expectedOwner &&
            observation.graph == graph && observation.graph == expectedGraph &&
            observation.generation == generation && observation.revision == revision &&
            finite(time) && observation.simulationTime == time;
    }
    boolean canPublish(Operation operation, Observation observation) {
        return operation != null && operation.outcome == Outcome.COMPLETE &&
            operation.acceptedSteps > 0 && observation != null &&
            observation.operation == operation.id && operation.candidate == observation &&
            isCurrent(observation, operation.owner, operation.graph, observation.simulationTime);
    }
    static boolean finite(double value) { return !Double.isNaN(value) && !Double.isInfinite(value); }
    private static long increment(long value) {
        if (value == Long.MAX_VALUE) throw new IllegalStateException("Solver identity space exhausted");
        return value + 1;
    }
}
