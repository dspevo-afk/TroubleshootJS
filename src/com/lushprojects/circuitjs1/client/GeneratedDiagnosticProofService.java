package com.lushprojects.circuitjs1.client;

import java.util.Collections;
import java.util.Comparator;
import java.util.Vector;

/**
 * Production serial admission. Provider recipes own replay, observations and
 * replacement selection; this service owns the invariant proof/restore boundary.
 * It has no family dispatch and is not a developer-verifier entrypoint.
 *
 * <p>The session API is a bounded form of the same proof. A session restores the
 * original player owner after every hypothesis and retains only the current
 * private candidate between bounded units, so a caller can yield without
 * publishing a partial receipt.
 */
final class GeneratedDiagnosticProofService {
    private GeneratedDiagnosticProofService() { }

    // The shared CircuitJS context admits one production proof session. The
    // ambient internal-proof depth also belongs to verifier clients, so it is
    // not an exclusive session lease. Retain this lease across yielded units
    // and failed private cleanup; release it only after exact cleanup succeeds.
    private static Session activeSession;

    /* One-shot failure seam for the coordinator's proof-cancel/outer-abort
     * canary.  It is consumed only after this session has performed its real
     * restore and admission cleanup. */
    private static boolean cancelFailureForDeveloperVerification;
    private static boolean cleanupFailureForDeveloperVerification;
    private static boolean restoreFailureForDeveloperVerification;

    static void setCancelFailureForDeveloperVerification(boolean fail) {
        cancelFailureForDeveloperVerification = fail;
    }

    /** One-shot cleanup canary consumed before the first private element delete. */
    static void setCleanupFailureForDeveloperVerification(boolean fail) {
        cleanupFailureForDeveloperVerification = fail;
    }
    static void setRestoreFailureForDeveloperVerification(boolean fail) {
        restoreFailureForDeveloperVerification = fail;
    }

    interface Checkpoint {
        void check();
    }

    private static final Checkpoint NO_CHECKPOINT = new Checkpoint() {
        public void check() { }
    };

    /* Nine bounded proof phases surround the immutable provider program.  A
     * temporal owner adds healthy settlement and deferred retest completion. */
    private static final int FIXED_HYPOTHESIS_UNIT_COUNT = 9;
    private static final int TEMPORAL_FIXED_HYPOTHESIS_UNIT_COUNT =
        FIXED_HYPOTHESIS_UNIT_COUNT + 2;

    static GeneratedDiagnosticProofReceipt prove(CirSim sim, GeneratedBoardInstance owner,
            GeneratedChallengeController controller) {
        Session session = begin(sim, owner, controller);
        while (session.step()) { }
        return session.finish();
    }

    static Session begin(CirSim sim, GeneratedBoardInstance owner,
            GeneratedChallengeController controller) {
        return begin(sim, owner, controller, null);
    }

    static Session begin(CirSim sim, GeneratedBoardInstance owner,
            GeneratedChallengeController controller, Checkpoint checkpoint) {
        return new Session(sim, owner, controller,
            checkpoint == null ? NO_CHECKPOINT : checkpoint);
    }

    static Vector<GeneratedFaultCandidate> hypothesesFor(GeneratedBoardInstance owner) {
        if (owner == null) throw new IllegalArgumentException("Missing diagnostic owner");
        Vector<GeneratedFaultCandidate> candidates = GeneratedFaultServiceabilityAdmission
            .getAdmittedCandidates(owner.getFaultCandidates());
        require(!candidates.isEmpty(), "Production diagnostic live owner has no admitted hypotheses");
        Collections.sort(candidates, new Comparator<GeneratedFaultCandidate>() {
            public int compare(GeneratedFaultCandidate a, GeneratedFaultCandidate b) {
                return a.getHypothesisKey().compareTo(b.getHypothesisKey());
            }
        });
        for (GeneratedFaultCandidate candidate : candidates)
            GeneratedFaultServiceabilityAdmission.validateCandidate(candidate);
        return candidates;
    }

    /**
     * Returns the structural proof work count for one owner. The five
     * GenerationJob stage units (resolve, healthy, physical, symptom and
     * publish) are deliberately outside this value.
     */
    static int requiredWorkUnits(GeneratedBoardInstance owner) {
        if (owner == null) throw new IllegalArgumentException("Missing diagnostic owner");
        GeneratedDiagnosticProvider provider = owner.getDiagnosticProvider();
        if (provider == null) throw new IllegalArgumentException("Missing diagnostic provider");
        GeneratedDiagnosticProgram program = provider.getObservationProgram();
        if (program == null) throw new IllegalArgumentException("Missing diagnostic program");
        int fixedUnits = owner.getTemporalBehavior() == null ? FIXED_HYPOTHESIS_UNIT_COUNT :
            TEMPORAL_FIXED_HYPOTHESIS_UNIT_COUNT;
        int perHypothesis = fixedUnits + program.getSteps().size();
        int count = hypothesesFor(owner).size();
        if (perHypothesis <= 0 || count > Integer.MAX_VALUE / perHypothesis)
            throw new IllegalArgumentException("Diagnostic proof work count overflow");
        return count * perHypothesis;
    }

    /**
     * Developer-facing cleanup evidence.  This is deliberately a value object
     * rather than live solver state: callers can retain a receipt after the
     * session has closed without retaining a private graph as reusable proof.
     */
    static final class CleanupAudit {
        private final int cleanupAttemptCount;
        private final int disposedHypothesisCount;
        private final int disconnectedBindingCount;
        private final int disposedElementCount;
        private final int disposalFailureCount;
        private final int lastBindingCount;
        private final int lastDisconnectedBindingCount;
        private final int lastElementCount;
        private final int lastDeletedElementCount;
        private final boolean lastOwnerGuardPassed;
        private final boolean lastBindingsActuallyDisconnected;
        private final boolean lastElementsActuallyDeleted;
        private final boolean lastGraphDetached;
        private final boolean lastCleanupComplete;

        private CleanupAudit(int cleanupAttemptCount, int disposedHypothesisCount,
                int disconnectedBindingCount, int disposedElementCount,
                int disposalFailureCount, int lastBindingCount,
                int lastDisconnectedBindingCount, int lastElementCount,
                int lastDeletedElementCount, boolean lastOwnerGuardPassed,
                boolean lastBindingsActuallyDisconnected,
                boolean lastElementsActuallyDeleted, boolean lastGraphDetached,
                boolean lastCleanupComplete) {
            this.cleanupAttemptCount = cleanupAttemptCount;
            this.disposedHypothesisCount = disposedHypothesisCount;
            this.disconnectedBindingCount = disconnectedBindingCount;
            this.disposedElementCount = disposedElementCount;
            this.disposalFailureCount = disposalFailureCount;
            this.lastBindingCount = lastBindingCount;
            this.lastDisconnectedBindingCount = lastDisconnectedBindingCount;
            this.lastElementCount = lastElementCount;
            this.lastDeletedElementCount = lastDeletedElementCount;
            this.lastOwnerGuardPassed = lastOwnerGuardPassed;
            this.lastBindingsActuallyDisconnected = lastBindingsActuallyDisconnected;
            this.lastElementsActuallyDeleted = lastElementsActuallyDeleted;
            this.lastGraphDetached = lastGraphDetached;
            this.lastCleanupComplete = lastCleanupComplete;
        }

        int getCleanupAttemptCount() { return cleanupAttemptCount; }
        int getDisposedHypothesisCount() { return disposedHypothesisCount; }
        int getDisconnectedBindingCount() { return disconnectedBindingCount; }
        int getDisposedElementCount() { return disposedElementCount; }
        int getDisposalFailureCount() { return disposalFailureCount; }
        int getLastBindingCount() { return lastBindingCount; }
        int getLastDisconnectedBindingCount() { return lastDisconnectedBindingCount; }
        int getLastElementCount() { return lastElementCount; }
        int getLastDeletedElementCount() { return lastDeletedElementCount; }
        boolean wasLastOwnerGuardPassed() { return lastOwnerGuardPassed; }
        boolean wereLastBindingsActuallyDisconnected() {
            return lastBindingsActuallyDisconnected;
        }
        boolean wereLastElementsActuallyDeleted() { return lastElementsActuallyDeleted; }
        boolean wasLastGraphDetached() { return lastGraphDetached; }
        boolean wasLastCleanupComplete() { return lastCleanupComplete; }
    }

    /**
     * One resumable production diagnostic proof. The controller admission token
     * remains private to this session until finish() publishes the complete
     * classified receipt.
     */
    static final class Session {
        private enum Unit {
            REPLAY_INSTALL,
            CANDIDATE_HEALTHY_SETTLE,
            CANDIDATE_SETTLE,
            OBSERVATION_STEP,
            POWER_OFF_SETTLE,
            REMOVE_SETTLE,
            REPLACE_SETTLE,
            POWER_ON_SETTLE,
            REPAIR_STATUS_SETTLE,
            CUSTOMER_RETEST,
            CUSTOMER_RETEST_COMPLETION,
            EVIDENCE_RESTORE_PRIMARY,
            COMPLETE
        }

        private final CirSim sim;
        private final GeneratedBoardInstance owner;
        private final GeneratedChallengeController controller;
        private final GeneratedDiagnosticProvider provider;
        private final GeneratedDiagnosticPlan plan;
        private final GeneratedDiagnosticProgram program;
        private final Vector<GeneratedFaultCandidate> candidates;
        private final Vector<CircuitElm> primaryGraph;
        private final Vector<GeneratedDiagnosticSolvabilityEvidence> evidence =
            new Vector<GeneratedDiagnosticSolvabilityEvidence>();
        /* Retained only for cross-step graph-disjointness checks, never as evidence. */
        private final Vector<GeneratedBoardInstance> privateBoards =
            new Vector<GeneratedBoardInstance>();
        private final Task41SimulationSnapshot snapshot;
        private final Object attempt;
        private final Checkpoint checkpoint;
        private final long startedMillis;

        private GeneratedBoardInstance activeCandidate;
        private GeneratedChallengeController activeCandidateController;
        private Vector<CircuitElm> activePrivateGraph;
        private Vector<Adjustable> activePrivateAdjustables;
        private Vector<String> activePrivateUndoStack;
        private Vector<String> activePrivateRedoStack;
        private boolean candidateInstallationInProgress;
        /* Retained until exact private cleanup has either completed or been
         * handed to the outer staged installation.  These are owner handles,
         * never reusable diagnostic evidence. */
        private boolean privateCleanupPending;
        private GeneratedFaultCandidate activeHypothesis;
        private GeneratedDiagnosticExecutionTrace.Builder activeTrace;
        private GeneratedDiagnosticObservationExecutor.Cursor observationCursor;
        private Vector<GeneratedDiagnosticSample> activeSamples;
        private GeneratedCustomerRetestResult activeRetest;
        private Runnable pendingRetestCompletion;
        private GeneratedChallengeController.RetestCompletionDispatch savedRetestCompletionDispatch;
        private GeneratedChallengeController retestDispatchOwner;
        private boolean retestDispatchInstalled;
        private String repairComponentId;
        private PhysicalPart<?> repairOriginalPart;
        private String repairCatalogId;
        private Unit nextUnit = Unit.REPLAY_INSTALL;
        private int nextHypothesis;
        private boolean internalProof;
        private boolean closed;
        private boolean failed;
        private boolean cancelled;
        private long elapsedMillis = -1;

        private final Vector<GeneratedBoardInstance> cleanupAttemptedCandidates =
            new Vector<GeneratedBoardInstance>();
        private final Vector<GeneratedBoardInstance> cleanupCountedCandidates =
            new Vector<GeneratedBoardInstance>();
        private final Vector<GeneratedBoardInstance> cleanupFailureCandidates =
            new Vector<GeneratedBoardInstance>();
        private int cleanupAttemptCount;
        private int disposedHypothesisCount;
        private int disconnectedBindingCount;
        private int disposedElementCount;
        private int disposalFailureCount;
        private int lastBindingCount;
        private int lastDisconnectedBindingCount;
        private int lastElementCount;
        private int lastDeletedElementCount;
        private boolean lastOwnerGuardPassed;
        private boolean lastBindingsActuallyDisconnected;
        private boolean lastElementsActuallyDeleted;
        private boolean lastGraphDetached;
        private boolean lastCleanupComplete;
        private GeneratedBoardInstance cleanupCandidate;
        private Vector<CircuitElm> cleanupElements;
        private final Vector<CircuitElm> cleanupDeletedElements =
            new Vector<CircuitElm>();
        private boolean cleanupCandidateFailure;
        private boolean cleanupCandidateGuardPassed;
        private boolean cleanupCandidateBindingsDisconnected;
        private boolean cleanupCandidateElementsDeleted;
        private int cleanupCandidateBindingCount;
        private int cleanupCandidateDisconnectedCount;
        private int cleanupCandidateElementCount;
        private int cleanupCandidateDeletedCount;

        private Session(CirSim sim, GeneratedBoardInstance owner,
                GeneratedChallengeController controller, Checkpoint checkpoint) {
            require(activeSession == null, "Another production proof session is active");
            requireInitialContext(sim, owner, controller);
            GeneratedDiagnosticSolvabilityAdmission.validate(sim, owner);
            provider = owner.getDiagnosticProvider();
            require(provider != null && provider.getProviderId() != null &&
                provider.getProviderId().length() > 0,
                "Production proof has no identified diagnostic provider");
            plan = provider.getDiagnosticPlan();
            program = provider.getObservationProgram();
            require(program != null, "Production diagnostic provider has no executable observations");
            program.validatePlan(plan);
            require(owner.getDiagnosticSolvabilityContract().getPlans().size() == 1 &&
                    GeneratedDiagnosticProgram.describePlan(plan).equals(
                        GeneratedDiagnosticProgram.describePlan(owner.getDiagnosticSolvabilityContract()
                            .getPlans().firstElement())),
                "Production provider plan differs from the admitted contract");
            candidates = hypothesesFor(owner);

            this.sim = sim;
            this.owner = owner;
            this.controller = controller;
            this.checkpoint = checkpoint;
            primaryGraph = sim.elmList;
            requireCurrentOwner("begin");
            /* begin() owns the exact primary candidate for its first unit. */
            sim.generatedRuntimeInstallationInProgress = false;
            snapshot = Task41SimulationSnapshot.capture(sim);
            attempt = controller.beginDiagnosticAdmission();
            startedMillis = System.currentTimeMillis();
            activeSession = this;
        }

        /** Executes exactly one bounded canonical proof unit. */
        boolean step() {
            ensureOpen();
            if (nextUnit == Unit.COMPLETE) {
                requireCurrentOwner("complete-step");
                return false;
            }

            Throwable failure = null;
            boolean restorationAttempted = false;
            try {
                requireCurrentWorkingOwner("step");
                sim.generatedRuntimeInstallationInProgress = false;
                GeneratedDiagnosticSolvabilityAdmission.beginInternalProof();
                internalProof = true;
                checkpointAndRequireWorkingOwner();

                Unit attempted = nextUnit;
                runUnit(attempted);
                require(!sim.activeMeasurementOverlay,
                    "Production diagnostic proof retained a measurement overlay between units");
                if (attempted == Unit.EVIDENCE_RESTORE_PRIMARY) {
                    /* Evidence is private until the candidate has restored. */
                    restorationAttempted = true;
                    Throwable restoration = restoreOriginal();
                    if (restoration != null) throwFailure(restoration);
                    nextHypothesis++;
                    clearActiveHypothesis();
                    nextUnit = nextHypothesis < candidates.size() ?
                        Unit.REPLAY_INSTALL : Unit.COMPLETE;
                }
            } catch (Throwable problem) {
                failure = problem;
            } finally {
                if (failure != null) {
                    failure = retain(failure, clearRetestCompletionState());
                    failure = retain(failure, cancelObservationCursor());
                    if (!restorationAttempted)
                        failure = retain(failure, restoreOriginal());
                }
                failure = retain(failure, endInternalProof());
            }

            if (failure != null) {
                failure = retain(failure, abortAdmission());
                failure = retain(failure, endInternalProof());
                closeAsFailed(failure);
                throwFailure(failure);
            }
            return nextUnit != Unit.COMPLETE;
        }

        private void runUnit(Unit attempted) {
            switch (attempted) {
            case REPLAY_INSTALL:
                runReplayAndInstall();
                nextUnit = owner.getTemporalBehavior() == null ? Unit.CANDIDATE_SETTLE :
                    Unit.CANDIDATE_HEALTHY_SETTLE;
                return;
            case CANDIDATE_HEALTHY_SETTLE:
                runCandidateHealthySettle();
                nextUnit = Unit.CANDIDATE_SETTLE;
                return;
            case CANDIDATE_SETTLE:
                runCandidateSettleAndPrepareObservations();
                nextUnit = Unit.OBSERVATION_STEP;
                return;
            case OBSERVATION_STEP:
                runObservationStep();
                return;
            case POWER_OFF_SETTLE:
                runPowerOffAndSettle();
                nextUnit = Unit.REMOVE_SETTLE;
                return;
            case REMOVE_SETTLE:
                runRemoveAndSettle();
                nextUnit = Unit.REPLACE_SETTLE;
                return;
            case REPLACE_SETTLE:
                runReplaceAndSettle();
                nextUnit = Unit.POWER_ON_SETTLE;
                return;
            case POWER_ON_SETTLE:
                runPowerOnAndSettle();
                nextUnit = Unit.REPAIR_STATUS_SETTLE;
                return;
            case REPAIR_STATUS_SETTLE:
                runRepairStatusAndSettle();
                nextUnit = Unit.CUSTOMER_RETEST;
                return;
            case CUSTOMER_RETEST:
                runCustomerRetest();
                nextUnit = pendingRetestCompletion == null ? Unit.EVIDENCE_RESTORE_PRIMARY :
                    Unit.CUSTOMER_RETEST_COMPLETION;
                return;
            case CUSTOMER_RETEST_COMPLETION:
                runCustomerRetestCompletion();
                nextUnit = Unit.EVIDENCE_RESTORE_PRIMARY;
                return;
            case EVIDENCE_RESTORE_PRIMARY:
                runEvidence();
                return;
            default:
                throw new IllegalStateException("Unknown production diagnostic proof unit");
            }
        }

        private void runReplayAndInstall() {
            checkpointAndRequireOwner();
            snapshot.beginProof(sim);
            requireCurrentOwner("step-start");
            activeHypothesis = candidates.get(nextHypothesis);
            GeneratedBoardInstance candidate = provider.generateHypothesis(activeHypothesis);
            /* Retain any non-null returned owner before admission rejects it;
             * even a developer-only replay may have allocated a private graph. */
            if (candidate != null) {
                activeCandidate = candidate;
                candidateInstallationInProgress = true;
                privateCleanupPending = true;
            }
            require(candidate != null && !candidate.isDeveloperOnlyFaultRoute(),
                "Diagnostic provider replayed a missing or developer-only hypothesis");
            requireCurrentOwner("after-replay-generation");
            FreshGeneratedRuntimeInstallation.requireDisjoint(owner, candidate);
            for (GeneratedBoardInstance previous : privateBoards)
                FreshGeneratedRuntimeInstallation.requireDisjoint(previous, candidate);
            validateReplay(owner, candidate, provider, program, activeHypothesis);
            checkpointAndRequireOwner();
            privateBoards.add(candidate);

            /* The primary graph remains authoritative and must be restored
             * with its original power state after this hypothesis.  Isolate its
             * external sources before installing the disjoint private graph so
             * the two owners can never be powered concurrently. */
            owner.getExternalPowerBindings().setConnected(false);
            require(owner.getExternalPowerBindings().areAllDisconnected(),
                "Production diagnostic proof failed to isolate the primary power owner");
            checkpointAndRequireOwner();

            /* CirSim's installation path clears its active containers and
             * calls delete() on every element it finds.  Give this private
             * hypothesis fresh containers so the saved primary graph is never
             * traversed as installation work. */
            activePrivateGraph = new Vector<CircuitElm>();
            activePrivateAdjustables = new Vector<Adjustable>();
            activePrivateUndoStack = new Vector<String>();
            activePrivateRedoStack = new Vector<String>();
            sim.elmList = activePrivateGraph;
            sim.adjustables = activePrivateAdjustables;
            sim.undoStack = activePrivateUndoStack;
            sim.redoStack = activePrivateRedoStack;
            sim.installGeneratedChallengeForDeveloperVerification(candidate);
            activeCandidateController = sim.getGeneratedChallengeController();
            require(sim.getGeneratedBoardInstance() == candidate,
                "Production diagnostic proof installed a different private candidate");
            require(activeCandidateController != null,
                "Production diagnostic proof installed a private candidate without a controller");
            candidateInstallationInProgress = false;
            rememberPrivateOwnerForCleanup();
            checkpointAndRequireCandidate();
            require(sim.getAttachedPcbWorkbenchCountForDeveloperVerification() == 0,
                "Production diagnostic proof attached a private board to the player UI");
        }

        private void runCandidateSettleAndPrepareObservations() {
            checkpointAndRequireCandidate();
            GeneratedRuntimeDeveloperSettlement.settle(sim, activeCandidate,
                "production-diagnostic-candidate");
            checkpointAndRequireCandidate();
            GeneratedDiagnosticSolvabilityAdmission.validate(sim, activeCandidate);
            GeneratedFaultServiceabilityAdmission.validateExecutableRuntime(sim, activeCandidate,
                activeCandidate.getFaultBinding());
            checkpointAndRequireCandidate();
            activeTrace = GeneratedDiagnosticExecutionTrace.builder();
            observationCursor = GeneratedDiagnosticObservationExecutor.begin(
                sim, activeCandidate, program, activeTrace);
            checkpointAndRequireCandidate();
        }

        private void runCandidateHealthySettle() {
            checkpointAndRequireCandidate();
            require(activeCandidate != null && owner.getTemporalBehavior() != null &&
                    activeCandidate.getTemporalBehavior() != null,
                "Temporal diagnostic proof lost its healthy profile owner");
            GeneratedRuntimeDeveloperSettlement.settleHealthy(sim, activeCandidate,
                "production-diagnostic-candidate-healthy");
            checkpointAndRequireCandidate();
        }

        private void runObservationStep() {
            require(observationCursor != null, "Missing diagnostic observation cursor");
            checkpointAndRequireCandidate();
            boolean more = observationCursor.step();
            if (!more) {
                activeSamples = observationCursor.finish();
                observationCursor = null;
            }
            checkpointAndRequireCandidate();
            if (!more) nextUnit = Unit.POWER_OFF_SETTLE;
        }

        private void runPowerOffAndSettle() {
            checkpointAndRequireCandidate();
            sim.instrumentController.clearTargets();
            checkpointAndRequireCandidate();
            sim.setBoardPowerState(BoardPowerState.UNPOWERED);
            checkpointAndRequireCandidate();
            GeneratedRuntimeDeveloperSettlement.settle(sim, activeCandidate,
                "production-diagnostic-repair-start");
            checkpointAndRequireCandidate();
        }

        private void runRemoveAndSettle() {
            checkpointAndRequireCandidate();
            require(activeCandidate != null && activeHypothesis != null && activeTrace != null,
                "Missing diagnostic repair context");
            repairComponentId = activeCandidate.getFaultLocus().getComponentId();
            repairOriginalPart = activeCandidate.getPhysicalBoardRuntime().getInstalledPart(repairComponentId);
            require(repairOriginalPart != null && repairOriginalPart.isInstalled(),
                "Diagnostic repair owner is not installed");
            require(activeCandidate.getFaultServiceability().getFaultClearingRepairActionIds()
                .contains(WorkbenchOperation.CATALOG_INSTALL),
                "Diagnostic provider has no supported fault-clearing replacement action");
            repairCatalogId = activeCandidate.getDiagnosticProvider().getCorrectCatalogId(
                activeCandidate, repairComponentId);
            require(repairCatalogId != null && repairCatalogId.length() > 0,
                "Diagnostic provider has no legal replacement recipe");

            checkpointAndRequireCandidate();
            dispatch(sim, WorkbenchOperation.forPart(WorkbenchOperation.REMOVE, repairOriginalPart));
            activeTrace.recordRepairAction(WorkbenchOperation.REMOVE);
            checkpointAndRequireCandidate();
            GeneratedRuntimeDeveloperSettlement.settle(sim, activeCandidate,
                "production-diagnostic-remove");
            checkpointAndRequireCandidate();
        }

        private void runReplaceAndSettle() {
            checkpointAndRequireCandidate();
            require(repairComponentId != null && repairCatalogId != null && activeTrace != null,
                "Missing diagnostic replacement context");
            checkpointAndRequireCandidate();
            dispatch(sim, WorkbenchOperation.forCatalog(repairComponentId, repairCatalogId));
            activeTrace.recordRepairAction(WorkbenchOperation.CATALOG_INSTALL);
            checkpointAndRequireCandidate();
            GeneratedRuntimeDeveloperSettlement.settle(sim, activeCandidate,
                "production-diagnostic-replace");
            checkpointAndRequireCandidate();
            require(activeCandidate.getPhysicalBoardRuntime().getInstalledPart(repairComponentId) !=
                repairOriginalPart,
                "Diagnostic replacement reused the original fault owner");
        }

        private void runPowerOnAndSettle() {
            checkpointAndRequireCandidate();
            sim.setBoardPowerState(BoardPowerState.POWERED);
            activeTrace.recordInputPowerTransition("BOARD_POWER_ON_RETEST");
            checkpointAndRequireCandidate();
            GeneratedRuntimeDeveloperSettlement.settle(sim, activeCandidate,
                "production-diagnostic-retest-power");
            checkpointAndRequireCandidate();
        }

        private void runRepairStatusAndSettle() {
            checkpointAndRequireCandidate();
            GeneratedChallengeController challenge = sim.getGeneratedChallengeController();
            require(challenge == activeCandidateController &&
                challenge.getRepairStatus() == GeneratedRepairStatus.CORRECTLY_RESTORED,
                "Diagnostic replacement failed the device's electrical repair behavior");
            checkpointAndRequireCandidate();
            GeneratedRuntimeDeveloperSettlement.settle(sim, activeCandidate,
                "production-diagnostic-retest-ready");
            checkpointAndRequireCandidate();
        }

        private void runCustomerRetest() {
            checkpointAndRequireCandidate();
            GeneratedChallengeController challenge = sim.getGeneratedChallengeController();
            require(challenge == activeCandidateController, "Diagnostic retest lost its controller");
            checkpointAndRequireCandidate();
            if (activeCandidate.getTemporalBehavior() != null) {
                captureTemporalRetestCompletion(challenge);
                checkpointAndRequireCandidate();
                return;
            }
            GeneratedCustomerRetestResult retest = challenge.performCustomerRetest();
            verifyCustomerRetest(challenge, retest);
        }

        private void runCustomerRetestCompletion() {
            checkpointAndRequireCandidate();
            GeneratedChallengeController challenge = sim.getGeneratedChallengeController();
            require(challenge == activeCandidateController && activeRetest != null &&
                    pendingRetestCompletion != null,
                "Missing temporal customer retest completion");
            Runnable completion = pendingRetestCompletion;
            pendingRetestCompletion = null;
            Throwable failure = null;
            try {
                completion.run();
            } catch (Throwable problem) {
                failure = problem;
            } finally {
                failure = retain(failure, clearRetestCompletionDispatch());
            }
            if (failure != null) throwFailure(failure);
            checkpointAndRequireCandidate();
            verifyCustomerRetest(challenge, activeRetest);
        }

        private void captureTemporalRetestCompletion(final GeneratedChallengeController challenge) {
            require(challenge == activeCandidateController && activeCandidate.getTemporalBehavior() != null,
                "Temporal customer retest requires its exact controller");
            require(pendingRetestCompletion == null && !retestDispatchInstalled,
                "Temporal customer retest already has a completion callback");
            activeRetest = null;
            savedRetestCompletionDispatch =
                challenge.getRetestCompletionDispatchForDeveloperVerification();
            retestDispatchOwner = challenge;
            retestDispatchInstalled = true;
            challenge.setRetestCompletionDispatchForDeveloperVerification(
                new GeneratedChallengeController.RetestCompletionDispatch() {
                    public void dispatch(Runnable completion) {
                        if (completion == null || pendingRetestCompletion != null)
                            throw new IllegalStateException(
                                "Temporal customer retest dispatched an invalid completion");
                        pendingRetestCompletion = completion;
                    }
                });
            Throwable failure = null;
            try {
                activeRetest = challenge.performCustomerRetest();
            } catch (Throwable problem) {
                failure = problem;
            } finally {
                failure = retain(failure, clearRetestCompletionDispatch());
            }
            if (failure != null) throwFailure(failure);
            require(activeRetest != null && pendingRetestCompletion != null,
                "Temporal customer retest did not provide its completion callback");
        }

        private void verifyCustomerRetest(GeneratedChallengeController challenge,
                GeneratedCustomerRetestResult retest) {
            require(retest != null && retest.isPassed() && challenge.getCustomerRetestResult() == retest,
                "Diagnostic repair did not publish a passed real customer retest");
            activeTrace.recordAction(GeneratedBoardOperationIds.CUSTOMER_RETEST);
            checkpointAndRequireCandidate();
            require(sim.getGeneratedBoardInstance() == activeCandidate &&
                !sim.activeMeasurementOverlay && sim.getBoardModificationController().isFullyRestored() &&
                sim.getBoardPowerController().getState() == BoardPowerState.POWERED,
                "Diagnostic retest lost its owner, unaffected function or restored physical state");
        }

        private void runEvidence() {
            checkpointAndRequireCandidate();
            require(activeCandidate != null && activeHypothesis != null && activeTrace != null &&
                activeSamples != null, "Missing diagnostic evidence context");
            checkpointAndRequireCandidate();
            GeneratedDiagnosticRepairSemantics semantics =
                GeneratedDiagnosticRepairSemantics.forServiceability(activeCandidate.getFaultServiceability());
            evidence.add(new GeneratedDiagnosticSolvabilityEvidence(
                activeCandidate.getCircuitFamilyId() + "/" + activeHypothesis.getFault().getType().name() + "/" +
                    activeHypothesis.getFault().getTargetComponentId(),
                activeCandidate.getCircuitFamilyId(), activeCandidate.getSeed(), activeHypothesis.getHypothesisKey(),
                candidates.size(), owner.getDiagnosticSolvabilityContract().getAdmittedPhysicalOwnerCount(),
                plan, activeSamples, activeTrace.freeze(semantics), true, "NONE", "PASS", "NONE", true, true,
                !sim.activeMeasurementOverlay && sim.getBoardModificationController().isFullyRestored() &&
                sim.getBoardPowerController().getState() == BoardPowerState.POWERED));
            checkpointAndRequireCandidate();
        }

        /** True only after every hypothesis completed and its owner was restored. */
        boolean isComplete() {
            return !failed && !cancelled && nextUnit == Unit.COMPLETE &&
                nextHypothesis == candidates.size() && activeCandidate == null;
        }

        int getCompletedCount() { return evidence.size(); }

        int getTotalCount() { return candidates.size(); }

        /** Exact owner predicate used while FreshGeneratedRuntimeInstallation is active. */
        boolean ownsWorkingOwner() {
            if (closed) return false;
            if (sim.getGeneratedBoardInstance() == owner &&
                    sim.getGeneratedChallengeController() == controller &&
                    (sim.elmList == primaryGraph ||
                     (candidateInstallationInProgress && activePrivateGraph != null &&
                      activePrivateGraph != primaryGraph && sim.elmList == activePrivateGraph)))
                return true;
            if (activeCandidate == null || sim.getGeneratedBoardInstance() != activeCandidate)
                return false;
            return activePrivateGraph != null && activePrivateGraph != primaryGraph &&
                sim.elmList == activePrivateGraph && (candidateInstallationInProgress ||
                (activeCandidateController != null &&
                    sim.getGeneratedChallengeController() == activeCandidateController));
        }

        /** Snapshot of cleanup state for the developer verifier. */
        CleanupAudit getCleanupAudit() {
            return new CleanupAudit(cleanupAttemptCount, disposedHypothesisCount,
                disconnectedBindingCount, disposedElementCount, disposalFailureCount,
                lastBindingCount, lastDisconnectedBindingCount, lastElementCount,
                lastDeletedElementCount, lastOwnerGuardPassed,
                lastBindingsActuallyDisconnected, lastElementsActuallyDeleted,
                lastGraphDetached, lastCleanupComplete);
        }

        /** True while a failed exact attempt still owns private cleanup state. */
        boolean hasPendingPrivateCleanup() { return privateCleanupPending; }

        /** Marks a paused proof owner as installation-in-progress without retaining the guard. */
        void pause() {
            if (!closed && ownsWorkingOwner())
                sim.generatedRuntimeInstallationInProgress = true;
        }

        /** Elapsed wall-clock time for this session, including yielded intervals. */
        long getElapsedMillis() {
            return elapsedMillis >= 0 ? elapsedMillis : elapsedNow();
        }

        /**
         * Classifies the full population and publishes the controller-local
         * receipt. A partial session cannot issue evidence.
         */
        GeneratedDiagnosticProofReceipt finish() {
            ensureOpen();
            Throwable failure = null;
            GeneratedDiagnosticProofReceipt receipt = null;
            try {
                require(isComplete(), "Production diagnostic proof is incomplete");
                requireCurrentOwner("finish");
                sim.generatedRuntimeInstallationInProgress = false;
                checkpointAndRequireOwner();
                receipt = new GeneratedDiagnosticProofReceipt(owner, controller, attempt,
                    program, new Vector<GeneratedDiagnosticSolvabilityEvidence>(evidence),
                    elapsedNow());
                /* The final checkpoint is immediately before controller publication. */
                checkpointAndRequireOwner();
                controller.completeDiagnosticAdmission(receipt, attempt);
            } catch (Throwable problem) {
                failure = problem;
            }

            if (failure != null) {
                failure = retain(failure, clearRetestCompletionState());
                failure = retain(failure, restoreOriginal());
                failure = retain(failure, abortAdmission());
                failure = retain(failure, endInternalProof());
                closeAsFailed(failure);
                throwFailure(failure);
            }
            failure = retain(null, abortAdmission());
            failure = retain(failure, endInternalProof());
            if (failure != null) {
                /* Publication already happened; surface cleanup failure honestly. */
                closeAsFailed(failure);
                throwFailure(failure);
            }
            closeAsFinished();
            return receipt;
        }

        /** Aborts this exact attempt without publishing a receipt. */
        void cancel() {
            if (closed) {
                if (!privateCleanupPending) {
                    Throwable retry = endInternalProof();
                    releaseSessionWhenClean();
                    if (retry != null) throwFailure(retry);
                    return;
                }
                /* A failed close may have left the private graph attached.  A
                 * later abort may retry its exact restore, but never touch a
                 * successor owner.  The pending flag remains authoritative if
                 * the retry cannot prove this session's current context. */
                if (!isCurrentProofContext())
                    throw new IllegalStateException(
                        "Production diagnostic proof retained pending cleanup after owner succession");
                Throwable retry = clearRetestCompletionState();
                retry = retain(retry, restoreOriginal());
                retry = retain(retry, endInternalProof());
                if (retry != null) throwFailure(retry);
                if (privateCleanupPending)
                    throw new IllegalStateException(
                        "Production diagnostic proof retained pending private cleanup");
                releaseSessionWhenClean();
                return;
            }
            Throwable failure = clearRetestCompletionState();
            failure = retain(failure, cancelObservationCursor());
            failure = retain(failure, restoreOriginal());
            failure = retain(failure, abortAdmission());
            failure = retain(failure, endInternalProof());
            cancelled = true;
            closeAsCancelled();
            if (consumeCancelFailureForDeveloperVerification())
                failure = retain(failure, new IllegalStateException(
                    "Injected diagnostic proof cancellation failure after cleanup"));
            if (failure != null) throwFailure(failure);
        }

        private static boolean consumeCancelFailureForDeveloperVerification() {
            if (!cancelFailureForDeveloperVerification) return false;
            cancelFailureForDeveloperVerification = false;
            return true;
        }

        private static boolean consumeCleanupFailureForDeveloperVerification() {
            if (!cleanupFailureForDeveloperVerification) return false;
            cleanupFailureForDeveloperVerification = false;
            return true;
        }

        private Throwable clearRetestCompletionDispatch() {
            if (!retestDispatchInstalled) return null;
            if (retestDispatchOwner == null)
                return new IllegalStateException(
                    "Production diagnostic proof lost its retest dispatch owner");
            try {
                retestDispatchOwner.setRetestCompletionDispatchForDeveloperVerification(
                    savedRetestCompletionDispatch);
                retestDispatchInstalled = false;
                retestDispatchOwner = null;
                savedRetestCompletionDispatch = null;
                return null;
            } catch (Throwable failure) {
                /* Retain the exact dispatch handles so a closed-session retry
                 * can restore them without touching a successor owner. */
                return failure;
            }
        }

        private Throwable clearRetestCompletionState() {
            Throwable failure = clearRetestCompletionDispatch();
            if (failure == null) {
                pendingRetestCompletion = null;
                activeRetest = null;
            }
            return failure;
        }

        private void checkpointAndRequireWorkingOwner() {
            checkpoint.check();
            requireCurrentWorkingOwner("checkpoint");
        }

        private void checkpointAndRequireOwner() {
            checkpoint.check();
            requireCurrentOwner("checkpoint");
        }

        private void checkpointAndRequireCandidate() {
            checkpoint.check();
            requireCurrentCandidate("checkpoint");
        }

        private void ensureOpen() {
            if (closed)
                throw new IllegalStateException("Production diagnostic proof session is closed");
            require(activeSession == this, "Production proof session lost its exclusive lease");
        }

        private void requireCurrentOwner(String boundary) {
            require(sim.getGeneratedBoardInstance() == owner && sim.elmList == primaryGraph &&
                    sim.getGeneratedChallengeController() == controller,
                "Production diagnostic proof lost its exact original owner at " + boundary);
        }

        private void requireCurrentWorkingOwner(String boundary) {
            require(ownsWorkingOwner(),
                "Production diagnostic proof lost its exact working owner at " + boundary);
        }

        private void requireCurrentCandidate(String boundary) {
            require(sim.getGeneratedBoardInstance() == activeCandidate &&
                    sim.elmList == activePrivateGraph &&
                    sim.getGeneratedChallengeController() == activeCandidateController,
                "Production diagnostic proof lost its private candidate at " + boundary);
        }

        private boolean isCurrentProofContext() {
            return (sim.getGeneratedBoardInstance() == owner &&
                    sim.getGeneratedChallengeController() == controller &&
                    (sim.elmList == primaryGraph ||
                     (candidateInstallationInProgress && activePrivateGraph != null &&
                      activePrivateGraph != primaryGraph && sim.elmList == activePrivateGraph))) ||
                (activeCandidate != null && sim.getGeneratedBoardInstance() == activeCandidate &&
                    activePrivateGraph != null && activePrivateGraph != primaryGraph &&
                    sim.elmList == activePrivateGraph &&
                    (candidateInstallationInProgress ||
                        (activeCandidateController != null &&
                            sim.getGeneratedChallengeController() == activeCandidateController)));
        }

        private boolean isCurrentOwner() {
            return sim.getGeneratedBoardInstance() == owner && sim.elmList == primaryGraph &&
                sim.getGeneratedChallengeController() == controller;
        }

        private boolean isCurrentCandidate(GeneratedBoardInstance candidate) {
            if (candidate == null || candidate != activeCandidate || candidate == owner ||
                    sim.getGeneratedBoardInstance() != candidate ||
                    activePrivateGraph == null || activePrivateGraph == primaryGraph ||
                    sim.elmList != activePrivateGraph)
                return false;
            GeneratedChallengeController current = sim.getGeneratedChallengeController();
            if (candidateInstallationInProgress)
                return activeCandidateController == null ? current == null :
                    current == activeCandidateController;
            return activeCandidateController != null && current == activeCandidateController;
        }

        private boolean isCurrentPrivatePreparation() {
            return activeCandidate != null && candidateInstallationInProgress &&
                sim.getGeneratedBoardInstance() == owner &&
                sim.getGeneratedChallengeController() == controller &&
                sim.elmList == activePrivateGraph && activePrivateGraph != null &&
                activePrivateGraph != primaryGraph;
        }

        private void rememberPrivateOwnerForCleanup() {
            if (activeCandidate == null || activeCandidate == owner ||
                    sim.getGeneratedBoardInstance() != activeCandidate)
                return;
            GeneratedChallengeController current = sim.getGeneratedChallengeController();
            if (activeCandidateController == null && current != null)
                activeCandidateController = current;
        }

        private boolean isDisjointFromProtectedOwner(GeneratedBoardInstance candidate) {
            if (candidate == null || candidate == owner ||
                    candidate.getBoard() == owner.getBoard() ||
                    candidate.getSimulationBindings() == owner.getSimulationBindings() ||
                    candidate.getComponentBindings() == owner.getComponentBindings() ||
                    candidate.getConnectionBindings() == owner.getConnectionBindings() ||
                    candidate.getExternalPowerBindings() == owner.getExternalPowerBindings() ||
                    candidate.getPhysicalBoardRuntime() == owner.getPhysicalBoardRuntime())
                return false;
            Vector<CircuitElm> candidateElements = candidate.getSimulationElements();
            Vector<CircuitElm> ownerElements = owner.getSimulationElements();
            for (CircuitElm candidateElement : candidateElements)
                for (CircuitElm ownerElement : ownerElements)
                    if (candidateElement == ownerElement)
                        return false;
            return true;
        }

        private Throwable cancelObservationCursor() {
            if (observationCursor == null) return null;
            GeneratedDiagnosticObservationExecutor.Cursor cursor = observationCursor;
            observationCursor = null;
            if (activeCandidate == null ||
                    sim.getGeneratedBoardInstance() != activeCandidate ||
                    sim.getGeneratedChallengeController() != activeCandidateController)
                return new IllegalStateException(
                    "Production diagnostic proof refused to clean observation over a successor owner");
            try {
                cursor.cancel();
                return null;
            } catch (Throwable failure) {
                return failure;
            }
        }

        private void clearActiveHypothesis() {
            activeHypothesis = null;
            activeTrace = null;
            activeSamples = null;
            activeRetest = null;
            repairComponentId = null;
            repairOriginalPart = null;
            repairCatalogId = null;
        }

        /** Cleans and restores only while the simulator still belongs to this proof. */
        private Throwable restoreOriginal() {
            Throwable failure = null;
            if (!isCurrentProofContext()) {
                return new IllegalStateException(
                    "Production diagnostic proof refused to restore over a successor owner");
            }
            GeneratedBoardInstance candidate = activeCandidate;
            if (candidate != null)
                rememberPrivateOwnerForCleanup();
            try {
                sim.instrumentController.clearTargets();
                sim.instrumentController.exitInstrumentModeForDeveloperVerification();
                require(!sim.activeMeasurementOverlay,
                    "Production diagnostic overlay cleanup failed");
            } catch (Throwable cleanup) {
                failure = retain(failure, cleanup);
            }
            if (!isCurrentProofContext()) {
                failure = retain(failure, new IllegalStateException(
                    "Production diagnostic proof refused to restore after owner succession"));
                if (candidate != null) privateCleanupPending = true;
                return failure;
            }

            /* A queued completion belongs to the exact private controller.  It
             * must be restored and discarded before detaching that controller. */
            if (retestDispatchInstalled || pendingRetestCompletion != null) {
                failure = retain(failure, new IllegalStateException(
                    "Production diagnostic proof retained a customer retest callback"));
                if (candidate != null) privateCleanupPending = true;
                return failure;
            }

            boolean privateCleanupComplete = candidate == null;
            if (candidate != null) {
                // A failed snapshot assertion may already have restored the
                // exact primary graph after every private element was deleted.
                // Retry that restoration without reclaiming disposal authority
                // over the now-current protected graph.
                boolean disposedBeforeRestore = isCurrentOwner() && cleanupCandidate == candidate &&
                    cleanupCandidateGuardPassed && cleanupCandidateBindingsDisconnected &&
                    cleanupCandidateElementsDeleted && !cleanupCandidateFailure;
                if (!disposedBeforeRestore)
                    failure = retain(failure, disposePrivateCandidate(candidate));
                privateCleanupComplete = cleanupCandidate == candidate &&
                    cleanupCandidateGuardPassed && cleanupCandidateBindingsDisconnected &&
                    cleanupCandidateElementsDeleted && !cleanupCandidateFailure;
                if (!privateCleanupComplete) {
                    /* Never restore the snapshot over a private owner whose
                     * graph or external sources were not fully disposed. */
                    privateCleanupPending = true;
                    failure = retain(failure, new IllegalStateException(
                        "Production diagnostic proof deferred snapshot restore until private cleanup completes"));
                    return failure;
                }
            }

            boolean snapshotRestored = false;
            if (!isCurrentProofContext()) {
                failure = retain(failure, new IllegalStateException(
                    "Production diagnostic proof refused snapshot restore after owner succession"));
                if (candidate != null) privateCleanupPending = true;
                return failure;
            }
            try {
                snapshot.restore(sim);
                if (restoreFailureForDeveloperVerification) {
                    restoreFailureForDeveloperVerification = false;
                    throw new IllegalStateException("Injected diagnostic snapshot restoration interruption");
                }
                snapshot.assertRestored(sim);
                requireCurrentOwner("restore");
                snapshotRestored = true;
            } catch (Throwable restoration) {
                failure = retain(failure, restoration);
                if (isCurrentOwner() || (candidate != null && isCurrentCandidate(candidate))) {
                    try {
                        sim.markGeneratedRuntimeFailure(sim.getGeneratedBoardInstance(), failure);
                    } catch (Throwable markFailure) {
                        failure = retain(failure, markFailure);
                    }
                } else {
                    failure = retain(failure, new IllegalStateException(
                        "Production diagnostic proof refused failure marking over a successor owner"));
                }
            }
            if (candidate != null) {
                if (snapshotRestored) {
                    finalizePrivateCleanup(candidate);
                    if (isCurrentOwner() && lastCleanupComplete)
                        clearActiveCandidate();
                    else
                        privateCleanupPending = true;
                } else {
                    /* Keep the exact candidate handles for a guarded retry;
                     * a failed snapshot restore is also not a clean close. */
                    privateCleanupPending = true;
                }
            } else {
                activeCandidateController = null;
                candidateInstallationInProgress = false;
            }
            return failure;
        }

        /**
         * Isolates and deletes only a candidate that is still an exact private
         * owner, or a candidate already detached while the exact primary owner
         * is current.  Snapshot restore remains the graph detachment boundary.
         */
        private Throwable disposePrivateCandidate(GeneratedBoardInstance candidate) {
            Throwable failure = null;
            boolean retryingSameCandidate = cleanupCandidate == candidate &&
                cleanupElements != null;
            if (!retryingSameCandidate)
                cleanupDeletedElements.clear();
            cleanupCandidate = candidate;
            cleanupElements = null;
            cleanupCandidateFailure = false;
            cleanupCandidateGuardPassed = false;
            cleanupCandidateBindingsDisconnected = false;
            cleanupCandidateElementsDeleted = false;
            cleanupCandidateBindingCount = 0;
            cleanupCandidateDisconnectedCount = 0;
            cleanupCandidateElementCount = 0;
            cleanupCandidateDeletedCount = 0;
            lastOwnerGuardPassed = false;
            lastBindingsActuallyDisconnected = false;
            lastElementsActuallyDeleted = false;
            lastGraphDetached = false;
            lastCleanupComplete = false;

            boolean privateOwner = isCurrentCandidate(candidate) ||
                isCurrentPrivatePreparation();
            boolean primaryOwner = candidateInstallationInProgress && isCurrentOwner() &&
                (activePrivateGraph == null ||
                    (activePrivateGraph != primaryGraph && sim.elmList == activePrivateGraph));
            try {
                cleanupCandidateGuardPassed = (privateOwner || primaryOwner) &&
                    isDisjointFromProtectedOwner(candidate);
            } catch (Throwable guardFailure) {
                failure = retain(failure, guardFailure);
            }
            lastOwnerGuardPassed = cleanupCandidateGuardPassed;
            if (!containsIdentity(cleanupAttemptedCandidates, candidate)) {
                cleanupAttemptedCandidates.add(candidate);
                cleanupAttemptCount++;
            }
            if (!cleanupCandidateGuardPassed) {
                failure = retain(failure, new IllegalStateException(
                    "Production diagnostic proof refused private cleanup over a foreign owner"));
                cleanupCandidateFailure = true;
                recordCleanupFailure(candidate);
                return failure;
            }

            try {
                cleanupElements = candidate.getSimulationElements();
                cleanupCandidateElementCount = cleanupElements.size();
                for (CircuitElm element : cleanupElements)
                    if (containsIdentity(cleanupDeletedElements, element))
                        cleanupCandidateDeletedCount++;
            } catch (Throwable graphFailure) {
                failure = retain(failure, graphFailure);
                cleanupCandidateFailure = true;
            }

            if (privateOwner) {
                try { sim.invalidateGeneratedOwnerWork(); }
                catch (Throwable cleanup) { failure = retain(failure, cleanup); }
                try { sim.setSimRunning(false); }
                catch (Throwable cleanup) { failure = retain(failure, cleanup); }
            }

            try {
                candidate.getExternalPowerBindings().setConnected(false);
            } catch (Throwable cleanup) {
                failure = retain(failure, cleanup);
                cleanupCandidateFailure = true;
            }
            try {
                java.util.Map<String, PowerOperatingAssessment.SourceState> states =
                    candidate.getExternalPowerBindings().getSourceStates();
                cleanupCandidateBindingCount = states.size();
                for (PowerOperatingAssessment.SourceState state : states.values())
                    if (state != null &&
                            state.getConnection() == PowerOperatingAssessment.Connection.ISOLATED)
                        cleanupCandidateDisconnectedCount++;
                cleanupCandidateBindingsDisconnected = cleanupCandidateBindingCount > 0 &&
                    cleanupCandidateDisconnectedCount == cleanupCandidateBindingCount &&
                    candidate.getExternalPowerBindings().areAllDisconnected();
            } catch (Throwable stateFailure) {
                failure = retain(failure, stateFailure);
                cleanupCandidateFailure = true;
            }
            lastBindingCount = cleanupCandidateBindingCount;
            lastDisconnectedBindingCount = cleanupCandidateDisconnectedCount;
            lastBindingsActuallyDisconnected = cleanupCandidateBindingsDisconnected;
            if (!cleanupCandidateBindingsDisconnected) {
                failure = retain(failure, new IllegalStateException(
                    "Production diagnostic private power bindings remained connected"));
                cleanupCandidateFailure = true;
            }

            if (cleanupElements == null) {
                cleanupCandidateFailure = true;
            } else {
                Vector<CircuitElm> protectedElements = null;
                try {
                    protectedElements = owner.getSimulationElements();
                } catch (Throwable protectedGraphFailure) {
                    failure = retain(failure, protectedGraphFailure);
                    cleanupCandidateFailure = true;
                }
                if (protectedElements != null) {
                    if (cleanupCandidateElementCount > 0 &&
                            cleanupCandidateDeletedCount == 0 &&
                            consumeCleanupFailureForDeveloperVerification()) {
                        failure = retain(failure, new IllegalStateException(
                            "Injected diagnostic private cleanup failure before element deletion"));
                        cleanupCandidateFailure = true;
                        cleanupCandidateElementsDeleted = false;
                        lastElementCount = cleanupCandidateElementCount;
                        lastDeletedElementCount = 0;
                        lastElementsActuallyDeleted = false;
                        recordCleanupFailure(candidate);
                        return failure;
                    }
                    for (CircuitElm element : cleanupElements) {
                        if (element == null || containsIdentity(protectedElements, element) ||
                                (primaryOwner && (sim.elmList == null ||
                                    sim.elmList.contains(element)))) {
                            failure = retain(failure, new IllegalStateException(
                                "Production diagnostic proof refused to delete a protected element"));
                            cleanupCandidateFailure = true;
                            continue;
                        }
                        if (containsIdentity(cleanupDeletedElements, element))
                            continue;
                        try {
                            element.delete();
                            cleanupDeletedElements.add(element);
                            cleanupCandidateDeletedCount++;
                        } catch (Throwable cleanup) {
                            failure = retain(failure, cleanup);
                            cleanupCandidateFailure = true;
                        }
                    }
                }
            }
            cleanupCandidateElementsDeleted = cleanupElements != null &&
                cleanupCandidateDeletedCount == cleanupCandidateElementCount &&
                cleanupCandidateElementCount > 0;
            lastElementCount = cleanupCandidateElementCount;
            lastDeletedElementCount = cleanupCandidateDeletedCount;
            lastElementsActuallyDeleted = cleanupCandidateElementsDeleted;
            if (!cleanupCandidateElementsDeleted) {
                failure = retain(failure, new IllegalStateException(
                    "Production diagnostic private graph disposal was incomplete"));
                cleanupCandidateFailure = true;
            }
            if (failure != null) cleanupCandidateFailure = true;
            if (cleanupCandidateFailure) recordCleanupFailure(candidate);
            return failure;
        }

        private void finalizePrivateCleanup(GeneratedBoardInstance candidate) {
            if (cleanupCandidate != candidate) return;
            boolean detached = isCurrentOwner() && cleanupElements != null;
            if (detached)
                for (CircuitElm element : cleanupElements)
                    if (element != null && (sim.elmList == null || sim.elmList.contains(element))) {
                        detached = false;
                        break;
                    }
            lastGraphDetached = detached;
            boolean complete = cleanupCandidateGuardPassed &&
                cleanupCandidateBindingsDisconnected && cleanupCandidateElementsDeleted &&
                detached && !cleanupCandidateFailure;
            lastCleanupComplete = complete;
            if (complete && !containsIdentity(cleanupCountedCandidates, candidate)) {
                cleanupCountedCandidates.add(candidate);
                disposedHypothesisCount++;
                disconnectedBindingCount += cleanupCandidateDisconnectedCount;
                disposedElementCount += cleanupCandidateDeletedCount;
            }
            if (!complete) privateCleanupPending = true;
        }

        private void recordCleanupFailure(GeneratedBoardInstance candidate) {
            if (!containsIdentity(cleanupFailureCandidates, candidate)) {
                cleanupFailureCandidates.add(candidate);
                disposalFailureCount++;
            }
        }

        private static boolean containsIdentity(Vector<GeneratedBoardInstance> values,
                GeneratedBoardInstance target) {
            for (GeneratedBoardInstance value : values)
                if (value == target) return true;
            return false;
        }

        private static boolean containsIdentity(Vector<CircuitElm> values, CircuitElm target) {
            if (values == null) return false;
            for (CircuitElm value : values)
                if (value == target) return true;
            return false;
        }

        private void clearActiveCandidate() {
            activeCandidate = null;
            activeCandidateController = null;
            candidateInstallationInProgress = false;
            privateCleanupPending = false;
            cleanupCandidate = null;
            cleanupElements = null;
            cleanupDeletedElements.clear();
            activePrivateGraph = null;
            activePrivateAdjustables = null;
            activePrivateUndoStack = null;
            activePrivateRedoStack = null;
        }

        private Throwable abortAdmission() {
            try {
                controller.abortDiagnosticAdmission(attempt);
                return null;
            } catch (Throwable failure) {
                return failure;
            }
        }

        private Throwable endInternalProof() {
            if (!internalProof) return null;
            try {
                GeneratedDiagnosticSolvabilityAdmission.endInternalProof();
                internalProof = false;
                return null;
            } catch (Throwable failure) {
                return failure;
            }
        }

        private void closeAsFailed(Throwable failure) {
            closed = true;
            failed = true;
            elapsedMillis = elapsedNow();
            privateBoards.clear();
            observationCursor = null;
            clearActiveHypothesis();
            if (!privateCleanupPending)
                clearActiveCandidate();
            releaseSessionWhenClean();
        }

        private void closeAsCancelled() {
            closed = true;
            elapsedMillis = elapsedNow();
            privateBoards.clear();
            observationCursor = null;
            clearActiveHypothesis();
            if (!privateCleanupPending)
                clearActiveCandidate();
            releaseSessionWhenClean();
        }

        private void closeAsFinished() {
            closed = true;
            elapsedMillis = elapsedNow();
            privateBoards.clear();
            observationCursor = null;
            clearActiveHypothesis();
            if (!privateCleanupPending)
                clearActiveCandidate();
            releaseSessionWhenClean();
        }

        private void releaseSessionWhenClean() {
            if (!privateCleanupPending && !internalProof && activeSession == this)
                activeSession = null;
        }

        private long elapsedNow() {
            return Math.max(0, System.currentTimeMillis() - startedMillis);
        }
    }

    private static void requireInitialContext(CirSim sim, GeneratedBoardInstance owner,
            GeneratedChallengeController controller) {
        require(sim != null && owner != null && controller != null &&
                sim.getGeneratedBoardInstance() == owner &&
                sim.getGeneratedChallengeController() == controller &&
                !owner.isDeveloperOnlyFaultRoute(),
            "Production proof requires its current normal owner");
    }

    private static void validateReplay(GeneratedBoardInstance owner, GeneratedBoardInstance candidate,
            GeneratedDiagnosticProvider provider, GeneratedDiagnosticProgram program,
            GeneratedFaultCandidate hypothesis) {
        GeneratedDiagnosticProvider replay = candidate.getDiagnosticProvider();
        require(owner.getCircuitFamilyId().equals(candidate.getCircuitFamilyId()) &&
                owner.getTopologyVariantId().equals(candidate.getTopologyVariantId()) &&
                owner.getSeed() == candidate.getSeed() && owner.getPcbLayout().geometryFingerprint().equals(
                    candidate.getPcbLayout().geometryFingerprint()),
            "Diagnostic hypothesis replay changed the recipe, topology or physical layout");
        require(replay != null && provider.getProviderId().equals(replay.getProviderId()) &&
                program.canonical().equals(replay.getObservationProgram().canonical()),
            "Diagnostic observation program depends on the selected hypothesis");
        require(candidate.getFaultBinding() != null && hypothesis.getHypothesisKey().equals(
                candidate.getFaultBinding().getFault().getHypothesisKey()),
            "Diagnostic replay selected a different canonical hypothesis");
        GeneratedFaultServiceabilityAdmission.validateHypothesisPopulation(candidate.getFaultCandidates(),
            owner.getDiagnosticSolvabilityContract().getHypothesisKeys());
        require(GeneratedDiagnosticRepairSemantics.forServiceability(hypothesis.getServiceability())
                .isEquivalentTo(GeneratedDiagnosticRepairSemantics.forServiceability(candidate.getFaultServiceability())),
            "Diagnostic replay changed the legal physical repair semantics");
    }

    private static void dispatch(CirSim sim, WorkbenchOperation operation) {
        require(sim.pcbWorkbenchController.isAvailable(operation) &&
                sim.pcbWorkbenchController.dispatch(operation),
            "Diagnostic repair action is unavailable: " + operation.getId());
    }

    private static void throwFailure(Throwable failure) {
        if (failure instanceof RuntimeException) throw (RuntimeException)failure;
        if (failure instanceof Error) throw (Error)failure;
        throw new IllegalStateException("Production diagnostic proof failed", failure);
    }

    private static Throwable retain(Throwable first, Throwable next) {
        if (next == null) return first;
        if (first == null) return next;
        if (first != next) first.addSuppressed(next);
        return first;
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
