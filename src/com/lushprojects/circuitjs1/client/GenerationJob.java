package com.lushprojects.circuitjs1.client;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;

/**
 * Pure, serial generation coordinator.  The service owns all live state; this
 * class only owns ordering, budgets, lifecycle and the receipt boundary.
 */
final class GenerationJob {
    static final int MAX_CANDIDATES = 80;
    private static final int STAGE_COUNT = 6;

    interface Services {
        int candidateCount();
        String manifest(int candidate);
        void beginCandidate(int index);
        String resolve();
        String healthy();
        String physical();
        boolean proveNext();
        String symptom();
        String dependencies();
        void publish(GenerationReceipt receipt);
        void abort();
        boolean isCurrent();
        long nowMillis();
    }

    enum Stage {
        RESOLVE,
        HEALTHY,
        PHYSICAL,
        HYPOTHESES,
        SYMPTOM,
        PUBLISH
    }

    enum Outcome {
        RUNNING,
        PASS,
        EXPECTED_REJECTION,
        CANCELLED,
        STALE,
        PROGRAMMING_FAILURE,
        INFRASTRUCTURE_FAILURE,
        WORK_EXHAUSTED,
        TIMEOUT
    }

    /** A typed failure returned by checkpoint-sensitive callers. */
    static class Failure extends IllegalStateException {
        final Outcome outcome;

        Failure(Outcome outcome, String message) {
            super(message);
            if (outcome == null)
                throw new IllegalArgumentException("Missing generation failure outcome");
            this.outcome = outcome;
        }

        Failure(Outcome outcome, String message, Throwable cause) {
            super(message, cause);
            if (outcome == null)
                throw new IllegalArgumentException("Missing generation failure outcome");
            this.outcome = outcome;
        }

        Outcome getOutcome() {
            return outcome;
        }
    }

    /**
     * The only exception a service may use to reject one candidate and permit
     * canonical retry.  All other RuntimeExceptions and Errors are terminal.
     */
    static class Rejected extends RuntimeException {
        Rejected() {
            super();
        }

        Rejected(String message) {
            super(message);
        }

        Rejected(String message, Throwable cause) {
            super(message, cause);
        }

        Rejected(Throwable cause) {
            super(cause);
        }
    }

    /** Base type for failures thrown by checkpoint(). */
    static class CheckpointFailure extends Failure {
        CheckpointFailure(Outcome outcome, String message) {
            super(outcome, message);
        }
    }

    static final class Cancelled extends CheckpointFailure {
        Cancelled(String message) {
            super(Outcome.CANCELLED, message);
        }
    }

    static final class Deadline extends CheckpointFailure {
        Deadline(String message) {
            super(Outcome.TIMEOUT, message);
        }
    }

    static final class Stale extends CheckpointFailure {
        Stale(String message) {
            super(Outcome.STALE, message);
        }
    }

    private static final class Candidate {
        final int sourceIndex;
        final String manifest;

        Candidate(int sourceIndex, String manifest) {
            this.sourceIndex = sourceIndex;
            this.manifest = manifest;
        }
    }

    private final Services services;
    private final long maxJobMillis;
    private final int maxSteps;
    private final long maxStepMillis;
    private final long startedMillis;
    private final Candidate[] candidates;
    private final Object issuerToken = new Object();

    private long lastObservedMillis;
    private long completedElapsedMillis;
    private int nextCandidatePosition;
    private int candidateIndex = -1;
    private int stepCount;
    private int proofStepCount;
    private boolean candidateActive;
    private boolean needsAbort;
    private boolean abortAttempted;
    private boolean unitActive;
    private long unitStartedMillis;

    private Stage stage = Stage.RESOLVE;
    private Outcome outcome = Outcome.RUNNING;
    private Throwable failure;
    private GenerationReceipt receipt;
    private String physicalDependencies;
    private final String[] stageReceipts = new String[STAGE_COUNT];
    private final long[] stageElapsedMillis = new long[STAGE_COUNT];
    private final int[] stageWorkCounts = new int[STAGE_COUNT];

    GenerationJob(Services services, long maxJobMillis, int maxSteps, long maxStepMillis) {
        if (services == null)
            throw new IllegalArgumentException("Generation services are required");
        if (maxJobMillis <= 0)
            throw new IllegalArgumentException("Generation wall budget must be positive");
        if (maxSteps <= 0)
            throw new IllegalArgumentException("Generation work budget must be positive");
        if (maxStepMillis <= 0)
            throw new IllegalArgumentException("Generation work-unit budget must be positive");
        this.services = services;
        this.maxJobMillis = maxJobMillis;
        this.maxSteps = maxSteps;
        this.maxStepMillis = maxStepMillis;
        this.startedMillis = services.nowMillis();
        this.lastObservedMillis = startedMillis;

        int count = services.candidateCount();
        if (count <= 0 || count > MAX_CANDIDATES)
            throw new IllegalArgumentException("Generation candidate count is outside 1.." +
                MAX_CANDIDATES);
        ArrayList<Candidate> collected = new ArrayList<Candidate>(count);
        for (int sourceIndex = 0; sourceIndex < count; sourceIndex++) {
            String manifest = services.manifest(sourceIndex);
            requireText(manifest, "candidate manifest");
            collected.add(new Candidate(sourceIndex, manifest));
        }
        Collections.sort(collected, new Comparator<Candidate>() {
            public int compare(Candidate left, Candidate right) {
                int byManifest = left.manifest.compareTo(right.manifest);
                if (byManifest != 0)
                    return byManifest;
                return left.sourceIndex < right.sourceIndex ? -1 :
                    (left.sourceIndex == right.sourceIndex ? 0 : 1);
            }
        });
        for (int i = 1; i < collected.size(); i++) {
            if (collected.get(i - 1).manifest.equals(collected.get(i).manifest))
                throw new IllegalArgumentException("Candidate manifests must be unique");
        }
        this.candidates = collected.toArray(new Candidate[collected.size()]);
    }

    boolean advance() {
        if (outcome != Outcome.RUNNING) {
            if (needsAbort)
                finishAbort();
            return false;
        }
        final Stage attempted = stage;
        long stageStarted = lastObservedMillis;
        boolean stageAttempted = false;
        boolean telemetryRecorded = false;
        unitActive = false;
        try {
            // A browser yield may have elapsed since the previous unit.  The
            // global checkpoint accounts for that time before starting the
            // active work-unit clock.
            checkpoint();
            stageStarted = lastObservedMillis;
            unitStartedMillis = lastObservedMillis;
            unitActive = true;
            if (stepCount >= maxSteps)
                throw new Failure(Outcome.WORK_EXHAUSTED,
                    "Generation work budget exhausted at " + maxSteps + " steps");
            stepCount++;
            stageAttempted = true;
            stageWorkCounts[attempted.ordinal()]++;
            boolean more = runStage(attempted);
            recordStageTelemetry(attempted, stageStarted, lastObservedMillis);
            telemetryRecorded = true;
            if (outcome == Outcome.PASS)
                completedElapsedMillis = elapsedAt(lastObservedMillis);
            return more;
        } catch (Rejected rejected) {
            unitActive = false;
            if (stageAttempted && !telemetryRecorded)
                recordStageTelemetry(attempted, stageStarted, lastObservedMillis);
            return handleRejection(rejected);
        } catch (CheckpointFailure checkpointFailure) {
            unitActive = false;
            if (stageAttempted && !telemetryRecorded)
                recordStageTelemetry(attempted, stageStarted, lastObservedMillis);
            return failTerminal(checkpointFailure, checkpointFailure.getOutcome());
        } catch (Failure boundedFailure) {
            unitActive = false;
            if (stageAttempted && !telemetryRecorded)
                recordStageTelemetry(attempted, stageStarted, lastObservedMillis);
            return failTerminal(boundedFailure, boundedFailure.getOutcome());
        } catch (Throwable unexpected) {
            unitActive = false;
            long failureFinished = sampleFailureMillis();
            if (stageAttempted && !telemetryRecorded)
                recordStageTelemetry(attempted, stageStarted, failureFinished);
            return failTerminal(unexpected, Outcome.PROGRAMMING_FAILURE);
        } finally {
            unitActive = false;
        }
    }

    void cancel() {
        if (outcome != Outcome.RUNNING)
            return;
        Cancelled cancelled = new Cancelled("Generation cancelled");
        markTerminal(cancelled.getOutcome(), cancelled);
        unitActive = false;
        finishAbort();
    }

    /**
     * Records a coordinator or owner-boundary failure and restores the live
     * service before returning control to the caller.  A committed publication
     * cannot be rolled back; callers receive their original runtime failure or
     * error in that case.
     */
    void failInfrastructure(Throwable infrastructureFailure) {
        if (infrastructureFailure == null)
            infrastructureFailure = new IllegalStateException(
                "Missing generation infrastructure failure");
        if (outcome == Outcome.PASS) {
            rethrowCommittedInfrastructure(infrastructureFailure);
            return;
        }
        unitActive = false;
        if (outcome == Outcome.RUNNING) {
            markTerminal(Outcome.INFRASTRUCTURE_FAILURE, infrastructureFailure);
            // Scope entry can fail before a candidate is active.  It still
            // owns a live generation service that must receive one abort.
            needsAbort = true;
            finishAbort();
            completedElapsedMillis = elapsedAt(lastObservedMillis);
            return;
        }

        // Preserve an already classified pre-publication failure while making
        // the coordinator failure visible as the secondary cause.
        if (failure == null)
            failure = infrastructureFailure;
        else
            addSuppressed(failure, infrastructureFailure);
        outcome = Outcome.INFRASTRUCTURE_FAILURE;
        if (needsAbort) {
            Throwable primary = failure;
            boolean aborted = finishAbort();
            if (!aborted && primary != null && failure != primary) {
                Throwable abortFailure = failure;
                addSuppressed(primary, abortFailure);
                failure = primary;
            }
        }
        completedElapsedMillis = elapsedAt(lastObservedMillis);
    }

    private static void rethrowCommittedInfrastructure(Throwable infrastructureFailure) {
        if (infrastructureFailure instanceof RuntimeException)
            throw (RuntimeException) infrastructureFailure;
        if (infrastructureFailure instanceof Error)
            throw (Error) infrastructureFailure;
        throw new IllegalStateException(
            "Infrastructure failure after generation publication", infrastructureFailure);
    }

    /**
     * Called by a live service at bounded points inside a stage.  A completed
     * publication intentionally does not ask the old owner whether it is
     * still current.
     */
    void checkpoint() {
        if (outcome == Outcome.PASS)
            return;
        if (outcome != Outcome.RUNNING) {
            if (failure instanceof CheckpointFailure)
                throw (CheckpointFailure) failure;
            throw new Failure(outcome, "Generation is " + outcome);
        }
        if (!services.isCurrent()) {
            Stale stale = new Stale("Generation owner is stale");
            markTerminal(stale.getOutcome(), stale);
            throw stale;
        }
        checkClock();
        checkUnitClock();
    }

    boolean isRunning() {
        return outcome == Outcome.RUNNING;
    }

    Outcome getOutcome() {
        return outcome;
    }

    Stage getStage() {
        return stage;
    }

    GenerationReceipt getReceipt() {
        return receipt;
    }

    Throwable getFailure() {
        return failure;
    }

    int getStepCount() {
        return stepCount;
    }

    int getCandidateIndex() {
        return candidateIndex;
    }

    long getElapsedMillis() {
        if (outcome != Outcome.RUNNING)
            return completedElapsedMillis;
        return elapsedAt(lastObservedMillis);
    }

    long getStageElapsedMillis(Stage requestedStage) {
        if (requestedStage == null)
            throw new IllegalArgumentException("Missing generation stage");
        return stageElapsedMillis[requestedStage.ordinal()];
    }

    int getStageWorkCount(Stage requestedStage) {
        if (requestedStage == null)
            throw new IllegalArgumentException("Missing generation stage");
        return stageWorkCounts[requestedStage.ordinal()];
    }

    /**
     * Validates an issued receipt immediately before publication.  The receipt
     * is tied to this exact job and to the dependency context captured at the
     * physical stage; equal text from another job is not sufficient.
     */
    void validateReceipt(GenerationReceipt candidateReceipt) {
        if (candidateReceipt == null)
            throw new IllegalStateException("Missing generation receipt");
        if (outcome == Outcome.PASS && candidateReceipt == receipt)
            return;
        if (outcome != Outcome.RUNNING)
            throw new Stale("Generation receipt is outside the live publication boundary");
        if (!candidateReceipt.belongsTo(issuerToken) || !candidateReceipt.isCompleteInternal() ||
                candidateReceipt.getMaxJobMillisInternal() != maxJobMillis ||
                candidateReceipt.getMaxStepMillisInternal() != maxStepMillis ||
                candidateReceipt.getMaxStepsInternal() != maxSteps || candidateReceipt.getManifest() == null ||
                !candidateReceipt.getManifest().equals(currentManifest()) ||
                candidateReceipt.getDependencies() == null || physicalDependencies == null ||
                !candidateReceipt.getDependencies().equals(physicalDependencies) ||
                candidateReceipt.getStageCount() != STAGE_COUNT ||
                candidateReceipt.getStageReceiptInternal(Stage.RESOLVE.ordinal()) == null ||
                candidateReceipt.getStageReceiptInternal(Stage.HEALTHY.ordinal()) == null ||
                candidateReceipt.getStageReceiptInternal(Stage.PHYSICAL.ordinal()) == null ||
                candidateReceipt.getStageReceiptInternal(Stage.HYPOTHESES.ordinal()) == null ||
                candidateReceipt.getStageReceiptInternal(Stage.SYMPTOM.ordinal()) == null ||
                candidateReceipt.getStageReceiptInternal(Stage.PUBLISH.ordinal()) == null)
            throw new Stale("Foreign or incomplete generation receipt");
        for (int i = 0; i < STAGE_COUNT; i++) {
            if (stageReceipts[i] == null ||
                    !stageReceipts[i].equals(candidateReceipt.getStageReceiptInternal(i)))
                throw new Stale("Generation receipt lineage changed");
        }
        String currentDependencies = services.dependencies();
        requireText(currentDependencies, "generation dependencies");
        if (!candidateReceipt.getDependencies().equals(currentDependencies))
            throw new Stale("Generation dependency context changed");
    }

    private boolean runStage(Stage attempted) {
        switch (attempted) {
        case RESOLVE:
            runResolve();
            return true;
        case HEALTHY:
            runHealthy();
            return true;
        case PHYSICAL:
            runPhysical();
            return true;
        case HYPOTHESES:
            runHypotheses();
            return true;
        case SYMPTOM:
            runSymptom();
            return true;
        case PUBLISH:
            runPublish();
            return false;
        default:
            throw new IllegalStateException("Unknown generation stage");
        }
    }

    private void runResolve() {
        if (!candidateActive) {
            if (nextCandidatePosition >= candidates.length)
                throw new Failure(Outcome.WORK_EXHAUSTED, "No generation candidate remains");
            candidateIndex = nextCandidatePosition;
            nextCandidatePosition++;
            resetCandidateAttempt();
            candidateActive = true;
            services.beginCandidate(candidates[candidateIndex].sourceIndex);
            checkpoint();
        }
        String resolved = services.resolve();
        checkpoint();
        stageReceipts[Stage.RESOLVE.ordinal()] = requireText(resolved, "resolve stage output");
        stage = Stage.HEALTHY;
    }

    private void runHealthy() {
        String healthy = services.healthy();
        checkpoint();
        if (healthy == null)
            return;
        stageReceipts[Stage.HEALTHY.ordinal()] = requireText(healthy, "healthy stage output");
        stage = Stage.PHYSICAL;
    }

    private void runPhysical() {
        String physical = services.physical();
        checkpoint();
        stageReceipts[Stage.PHYSICAL.ordinal()] = requireText(physical, "physical stage output");
        String dependencies = services.dependencies();
        requireText(dependencies, "generation dependencies");
        checkpoint();
        physicalDependencies = dependencies;
        stage = Stage.HYPOTHESES;
    }

    private void runHypotheses() {
        boolean more = services.proveNext();
        checkpoint();
        proofStepCount++;
        if (more)
            return;
        if (physicalDependencies == null)
            throw new IllegalStateException("Hypotheses have no physical dependency context");
        stageReceipts[Stage.HYPOTHESES.ordinal()] = "workUnits=" + proofStepCount +
            ";complete=true;dependencies=" + physicalDependencies;
        stage = Stage.SYMPTOM;
    }

    private void runSymptom() {
        String symptom = services.symptom();
        checkpoint();
        stageReceipts[Stage.SYMPTOM.ordinal()] = requireText(symptom, "symptom stage output");
        stage = Stage.PUBLISH;
    }

    private void runPublish() {
        if (physicalDependencies == null)
            throw new IllegalStateException("Publish has no physical dependency context");
        for (int i = Stage.RESOLVE.ordinal(); i <= Stage.SYMPTOM.ordinal(); i++) {
            if (stageReceipts[i] == null)
                throw new IllegalStateException("Publish has an incomplete stage lineage");
        }
        stageReceipts[Stage.PUBLISH.ordinal()] = "complete=true;atomic=true";
        GenerationReceipt candidateReceipt = GenerationReceipt.issue(issuerToken,
            currentManifest(), physicalDependencies, stageReceipts, stepCount,
            elapsedAt(lastObservedMillis), stageElapsedMillis, stageWorkCounts, maxJobMillis,
            maxStepMillis, maxSteps);
        validateReceipt(candidateReceipt);
        checkpoint();
        if (!services.isCurrent()) {
            Stale stale = new Stale("Generation owner changed before publication");
            markTerminal(stale.getOutcome(), stale);
            throw stale;
        }
        services.publish(candidateReceipt);
        observeAfterPublish();
        receipt = candidateReceipt;
        completedElapsedMillis = elapsedAt(lastObservedMillis);
        candidateActive = false;
        needsAbort = false;
        failure = null;
        outcome = Outcome.PASS;
    }

    private boolean handleRejection(Rejected rejected) {
        if (outcome != Outcome.RUNNING) {
            addSuppressed(failure, rejected);
            finishAbort();
            return false;
        }
        try {
            checkClock();
        } catch (CheckpointFailure deadline) {
            addSuppressed(deadline, rejected);
            finishAbort();
            return false;
        } catch (Throwable clockFailure) {
            return failTerminal(clockFailure, Outcome.PROGRAMMING_FAILURE);
        }
        failure = rejected;
        needsAbort = true;
        if (!finishAbort())
            return false;
        if (nextCandidatePosition >= candidates.length) {
            candidateActive = false;
            outcome = Outcome.EXPECTED_REJECTION;
            completedElapsedMillis = elapsedAt(lastObservedMillis);
            return false;
        }
        candidateActive = false;
        candidateIndex = -1;
        stage = Stage.RESOLVE;
        clearCandidateLineage();
        failure = null;
        outcome = Outcome.RUNNING;
        abortAttempted = false;
        return true;
    }

    private boolean failTerminal(Throwable primary, Outcome desired) {
        if (outcome == Outcome.PASS)
            return false;
        if (outcome == Outcome.RUNNING)
            markTerminal(desired, primary);
        else if (failure == null)
            failure = primary;
        finishAbort();
        completedElapsedMillis = elapsedAt(lastObservedMillis);
        return false;
    }

    private void markTerminal(Outcome terminal, Throwable primary) {
        if (outcome != Outcome.RUNNING)
            return;
        outcome = terminal;
        failure = primary;
        needsAbort = true;
        completedElapsedMillis = elapsedAt(lastObservedMillis);
    }

    private boolean finishAbort() {
        if (!needsAbort || abortAttempted)
            return outcome != Outcome.INFRASTRUCTURE_FAILURE;
        abortAttempted = true;
        try {
            services.abort();
            needsAbort = false;
            candidateActive = false;
            return true;
        } catch (Throwable abortFailure) {
            addSuppressed(abortFailure, failure);
            failure = abortFailure;
            outcome = Outcome.INFRASTRUCTURE_FAILURE;
            needsAbort = false;
            completedElapsedMillis = elapsedAt(lastObservedMillis);
            return false;
        }
    }

    private void resetCandidateAttempt() {
        clearCandidateLineage();
        stage = Stage.RESOLVE;
        abortAttempted = false;
        needsAbort = false;
        failure = null;
    }

    private void clearCandidateLineage() {
        for (int i = 0; i < STAGE_COUNT; i++) {
            stageReceipts[i] = null;
        }
        proofStepCount = 0;
        physicalDependencies = null;
    }

    private void recordStageTelemetry(Stage completedStage, long stageStarted, long stageFinished) {
        int index = completedStage.ordinal();
        long delta = stageFinished >= stageStarted ? stageFinished - stageStarted : 0;
        if (delta < 0)
            delta = Long.MAX_VALUE;
        if (Long.MAX_VALUE - stageElapsedMillis[index] < delta)
            stageElapsedMillis[index] = Long.MAX_VALUE;
        else
            stageElapsedMillis[index] += delta;
    }

    private long sampleFailureMillis() {
        try {
            long sampled = services.nowMillis();
            if (sampled >= lastObservedMillis)
                lastObservedMillis = sampled;
        } catch (Throwable ignored) {
            // Failure telemetry must never replace the stage's primary error.
        }
        return lastObservedMillis;
    }

    private void checkClock() {
        long now = services.nowMillis();
        if (now < lastObservedMillis) {
            Deadline deadline = new Deadline("Generation clock regressed");
            markTerminal(deadline.getOutcome(), deadline);
            throw deadline;
        }
        lastObservedMillis = now;
        if (elapsedAt(now) >= maxJobMillis) {
            Deadline deadline = new Deadline("Generation wall budget exhausted");
            markTerminal(deadline.getOutcome(), deadline);
            throw deadline;
        }
    }

    private void checkUnitClock() {
        if (!unitActive)
            return;
        long elapsed;
        if (lastObservedMillis < unitStartedMillis)
            elapsed = Long.MAX_VALUE;
        else {
            elapsed = lastObservedMillis - unitStartedMillis;
            if (elapsed < 0)
                elapsed = Long.MAX_VALUE;
        }
        if (elapsed >= maxStepMillis) {
            Deadline deadline = new Deadline("Generation work-unit budget exhausted");
            markTerminal(deadline.getOutcome(), deadline);
            throw deadline;
        }
    }

    private void observeAfterPublish() {
        try {
            long now = services.nowMillis();
            if (now >= lastObservedMillis)
                lastObservedMillis = now;
        } catch (Throwable ignored) {
            // Publication has already crossed the intentional owner boundary.
            // Telemetry cannot turn an atomic publication into a failed one.
        }
    }

    private long elapsedAt(long now) {
        if (now < startedMillis)
            return 0;
        long elapsed = now - startedMillis;
        return elapsed < 0 ? Long.MAX_VALUE : elapsed;
    }

    private String currentManifest() {
        if (candidateIndex < 0 || candidateIndex >= candidates.length)
            throw new IllegalStateException("No active generation candidate");
        return candidates[candidateIndex].manifest;
    }

    private static String requireText(String value, String label) {
        if (value == null || value.trim().length() == 0)
            throw new IllegalStateException("Missing " + label);
        return value;
    }

    private static void addSuppressed(Throwable target, Throwable suppressed) {
        if (target == null || suppressed == null || target == suppressed)
            return;
        target.addSuppressed(suppressed);
    }
}
