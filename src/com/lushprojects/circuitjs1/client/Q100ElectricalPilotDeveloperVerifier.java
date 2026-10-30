package com.lushprojects.circuitjs1.client;

import com.google.gwt.core.client.Scheduler;
import com.google.gwt.dom.client.Document;
import com.google.gwt.dom.client.NativeEvent;
import com.google.gwt.event.dom.client.MouseWheelEvent;
import com.google.gwt.user.client.Event;
import java.util.Vector;

/** Explicit Developer-only raw electrical reference; grants no family admission. */
final class Q100ElectricalPilotDeveloperVerifier {
    private Q100ElectricalPilotDeveloperVerifier() { }

    private static final String E06_ACCEPTANCE_CANARY_PROTOCOL =
        "tsj-e06-acceptance-boundary-canary-v1";
    private static final String E06_ACCEPTANCE_CANARY_FAMILY = Rb56Plan.FAMILY_ID;
    private static final long E06_ACCEPTANCE_CANARY_SEED = -8360266000248569150L;
    private static final double E06_ACCEPTANCE_CANARY_DT = .000025;
    private static final int E06_ACCEPTANCE_CANARY_BATCH = 64;
    private static final int E06_ACCEPTANCE_CANARY_MAX_PRIME_STEPS = 8000;
    private static final int E06_ACCEPTANCE_CANARY_MAX_WORK = 640;
    private static final double E06_ACCEPTANCE_CANARY_MAX_UNIT_MS = 5000;
    private static final int E06_ACCEPTANCE_CANARY_MAX_INTERTRIAL_MS = 500;
    private static final double E06_ACCEPTANCE_CANARY_OPERATION_LIMIT_MS = 90000;
    private static final double E06_ACCEPTANCE_CANARY_CLEANUP_LIMIT_MS = 120000;

    /**
     * Developer-only accepted-state boundary probe.  The selected RB56 owner is
     * retained as the real predecessor while a detached, actual CircuitJS E06
     * graph is stepped through one of the two failure points.
     */
    static void startE06AcceptanceBoundaryCanary(final CirSim sim, final String canaryText) {
        Scheduler.get().scheduleDeferred(new Scheduler.ScheduledCommand() {
            public void execute() {
                try {
                    Rb100PilotReference.require(TroubleshootBuild.isDeveloper() &&
                        sim.troubleshootDebug && sim.troubleshootQ100Verification,
                        "explicit Developer Q100 canary route required");
                    Rb100PilotReference.require(!sim.developerVerifierRunning &&
                        sim.playerSessionController == null && !sim.activeMeasurementOverlay &&
                        sim.isGeneratedRuntimeSettled() &&
                        CircuitElm.sim == sim && !sim.solverExecutor.isUnavailable() &&
                        !GeneratedDiagnosticSolvabilityAdmission.isInternalProofRunning() &&
                        (sim.generationCoordinator == null || !sim.generationCoordinator.isRunning()),
                        "idle settled QuickPlay predecessor required");
                    Rb100PilotReference.require("e06-late-callback".equals(canaryText) ||
                        "e06-event-failure".equals(canaryText), "unsupported E06 acceptance canary");
                    Rb100PilotReference.require(CircuitSolverExecutor.WALL_LIMIT_MS ==
                        E06_ACCEPTANCE_CANARY_MAX_INTERTRIAL_MS &&
                        CircuitSolverExecutor.TEMPORAL_WALL_LIMIT_MS == E06_ACCEPTANCE_CANARY_MAX_UNIT_MS,
                        "accepted-state canary solver time budgets changed");
                    GeneratedBoardInstance owner = sim.generatedBoardInstance;
                    QuickPlaySelection selection = sim.getQuickPlaySelectionForDeveloperVerification();
                    Rb100PilotReference.require(owner != null &&
                        E06_ACCEPTANCE_CANARY_FAMILY.equals(owner.getCircuitFamilyId()) &&
                        owner.getSeed() == E06_ACCEPTANCE_CANARY_SEED && sim.quickPlaySession != null &&
                        sim.quickPlaySession.getInstance() == owner && selection != null &&
                        E06_ACCEPTANCE_CANARY_FAMILY.equals(selection.getFamilyId()) &&
                        selection.getSeed() == E06_ACCEPTANCE_CANARY_SEED,
                        "exact RB56 QuickPlay predecessor required");
                    Vector<E06AveragedConverterElm> models = findE06Models(sim.elmList);
                    Rb100PilotReference.require(models.size() == 1 &&
                        models.firstElement().hasAcceptedPredictorForDeveloperVerification(),
                        "settled predecessor requires one accepted E06 predictor");
                    new E06BoundaryCanaryRunner(sim, canaryText, owner,
                        models.firstElement()).begin();
                } catch (Throwable failure) {
                    publishE06AcceptanceCanary("{\"protocol\":" + quote(E06_ACCEPTANCE_CANARY_PROTOCOL) +
                        ",\"epoch\":" + quote(GenerationDependencyContext.INTERPRETATION_EPOCH) +
                        ",\"status\":\"REJECTED_BEFORE_PRIVATE_GRAPH\",\"canary\":" +
                        quote(canaryText) + ",\"family\":" + quote(E06_ACCEPTANCE_CANARY_FAMILY) +
                        ",\"seed\":" + quote(Long.toString(E06_ACCEPTANCE_CANARY_SEED)) +
                        ",\"cleanup\":\"NOT_APPLICABLE\",\"cleanupError\":null,\"error\":" +
                        quote(failure.getMessage()) + "}");
                    CirSim.publishBrowserVerificationResult("FAIL:e06-acceptance-canary");
                }
            }
        });
    }

    private static Vector<E06AveragedConverterElm> findE06Models(Vector<CircuitElm> elements) {
        Vector<E06AveragedConverterElm> result = new Vector<E06AveragedConverterElm>();
        if (elements != null) for (CircuitElm element : elements)
            if (element instanceof E06AveragedConverterElm)
                result.add((E06AveragedConverterElm)element);
        return result;
    }

    private static final class E06BoundaryCanaryRunner {
        final CirSim sim;
        final String canary;
        final GeneratedBoardInstance predecessorOwner;
        final E06AveragedConverterElm predecessorModel;
        final E06AveragedConverterElm.AcceptedStateSnapshot predecessorSnapshot;
        final Vector<CircuitElm> predecessorGraph;
        final QuickPlaySession predecessorQuickPlaySession;
        final QuickPlaySelection predecessorQuickPlaySelection;
        final String predecessorDump;
        final boolean predecessorRunning;
        final String diode, zener, transistor, led;
        final double started = CircuitSolverExecutor.wallTimeMillis();
        PrivateSolverContext proof;
        E06ConverterFixtures.Fixture fixture;
        E06AveragedConverterElm converter;
        E06AveragedConverterElm.AcceptedStateSnapshot candidateSnapshot;
        E06BoundaryFailureElm lateFailure;
        boolean installed, finished;
        boolean acceptedPredictorBefore, predecessorSnapshotBefore, predecessorPredictorBefore;
        boolean predecessorModelIdentityStable;
        boolean pendingObserved, pendingAfterFailure, advancedTime, expectedFailureObserved;
        boolean committedSnapshotUnchanged, acceptedAtRejectedTime;
        boolean postStepCallbackObserved, eventCallbackObserved, eventCandidateObserved;
        boolean quickPlaySessionIdentityStable, quickPlaySelectionIdentityStable;
        double simulatedSecondsBefore = Double.NaN, simulatedSecondsAfter = Double.NaN;
        double privateTimeStepAtRejection = Double.NaN;
        double privateMinTimeStepAtRejection = Double.NaN;
        double privateMaxTimeStepAtRejection = Double.NaN;
        boolean privateAdjustTimeStepAtRejection;
        Throwable injectedFailure;
        String expectedFailureMessage;
        int primeSteps, candidateE06Count, predecessorE06CountAfter;
        int work, acceptedSteps, maxBatchSteps, rejectedAttemptSteps, rejectedAttemptTimeStepCount;
        double maxUnitMs, maxStepTimeError;
        boolean fixedStepVerified = true, unitLimitViolated;
        boolean exactCountVerified = true;

        E06BoundaryCanaryRunner(CirSim sim, String canary,
                GeneratedBoardInstance predecessorOwner, E06AveragedConverterElm predecessorModel) {
            this.sim = sim; this.canary = canary; this.predecessorOwner = predecessorOwner;
            this.predecessorModel = predecessorModel;
            predecessorSnapshot = predecessorModel.captureAcceptedStateForDeveloperVerification();
            predecessorSnapshotBefore = predecessorModel.matchesAcceptedStateForDeveloperVerification(
                predecessorSnapshot);
            predecessorPredictorBefore = predecessorModel.hasAcceptedPredictorForDeveloperVerification();
            predecessorGraph = sim.elmList; predecessorDump = sim.dumpCircuit();
            predecessorQuickPlaySession = sim.quickPlaySession;
            predecessorQuickPlaySelection = sim.getQuickPlaySelectionForDeveloperVerification();
            predecessorRunning = sim.simIsRunning();
            diode = DiodeElm.lastModelName; zener = ZenerElm.lastZenerModelName;
            transistor = TransistorElm.lastModelName; led = LEDElm.lastLEDModelName;
        }

        void begin() {
            try {
                Rb100PilotReference.require(predecessorSnapshotBefore && predecessorPredictorBefore,
                    "predecessor E06 accepted predictor changed during canary setup");
                double constructionStarted = beginUnit();
                try {
                    proof = PrivateSolverContext.open(sim);
                    sim.developerVerifierRunning = true;
                    sim.timeStep = sim.maxTimeStep = sim.minTimeStep = E06_ACCEPTANCE_CANARY_DT;
                    sim.adjustTimeStep = false;
                    fixture = E06ConverterFixtures.averaged();
                    converter = fixture.averaged;
                    candidateE06Count = findE06Models(fixture.elements).size();
                    Rb100PilotReference.require(converter != null && candidateE06Count == 1,
                        "private E06 fixture omitted its averaged converter");
                    lateFailure = new E06BoundaryFailureElm(this);
                    fixture.elements.add(lateFailure);
                    fixture.enable.setVoltage(5);
                    proof.install(fixture.elements);
                    installed = true;
                } finally { endUnit(constructionStarted); }
                requireUnitBudgets();
                double analysisStarted = beginUnit();
                try { proof.analyze(); }
                finally { endUnit(analysisStarted); }
                fixedStepVerified &= fixedStepCurrent();
                requireUnitBudgets();
                publishE06AcceptanceCanary("{\"protocol\":" + quote(E06_ACCEPTANCE_CANARY_PROTOCOL) +
                    ",\"epoch\":" + quote(GenerationDependencyContext.INTERPRETATION_EPOCH) +
                    ",\"status\":\"RUNNING\",\"canary\":" + quote(canary) +
                    ",\"family\":" + quote(E06_ACCEPTANCE_CANARY_FAMILY) +
                    ",\"seed\":" + quote(Long.toString(E06_ACCEPTANCE_CANARY_SEED)) +
                    ",\"quickPlaySelectionVerified\":true}");
                deferPrime();
            } catch (Throwable failure) { finish(failure); }
        }

        void deferPrime() {
            Scheduler.get().scheduleDeferred(new Scheduler.ScheduledCommand() {
                public void execute() {
                    if (finished) return;
                    try {
                        checkOperationBudget();
                        if (primeReady()) { runFailureCanary(); return; }
                        if (primeSteps >= E06_ACCEPTANCE_CANARY_MAX_PRIME_STEPS)
                            throw new IllegalStateException("E06 predecessor fixture did not reach a compatible predictor");
                        int count = Math.min(E06_ACCEPTANCE_CANARY_BATCH,
                            E06_ACCEPTANCE_CANARY_MAX_PRIME_STEPS - primeSteps);
                        fixedStepVerified &= fixedStepCurrent();
                        double batchBefore = sim.t;
                        int batchCountBefore = sim.timeStepCount;
                        double batchStarted = beginUnit();
                        try { proof.advanceFor(count * E06_ACCEPTANCE_CANARY_DT); }
                        finally { endUnit(batchStarted); }
                        fixedStepVerified &= fixedStepCurrent();
                        int batchAccepted = sim.timeStepCount - batchCountBefore;
                        double stepTimeError = Math.abs((sim.t - batchBefore) -
                            batchAccepted * E06_ACCEPTANCE_CANARY_DT);
                        if (batchAccepted != count || batchAccepted < 1 ||
                                batchAccepted > E06_ACCEPTANCE_CANARY_BATCH ||
                                !finite(stepTimeError) || stepTimeError > 1e-8 ||
                                !sim.solverExecutor.hasAcceptedStepAt(sim.t))
                            throw new IllegalStateException("E06 private prime did not advance the bounded accepted steps");
                        maxStepTimeError = Math.max(maxStepTimeError, stepTimeError);
                        exactCountVerified &= batchAccepted == count && stepTimeError <= 1e-8;
                        primeSteps += batchAccepted;
                        acceptedSteps += batchAccepted;
                        maxBatchSteps = Math.max(maxBatchSteps, batchAccepted);
                        requireUnitBudgets();
                        deferPrime();
                    } catch (Throwable failure) { finish(failure); }
                }
            });
        }

        double simulatedPrimeStart = 0;

        boolean primeReady() {
            return sim.t >= .030 && converter.getOutputVoltage() > .1 &&
                converter.hasAcceptedPredictorForDeveloperVerification() &&
                E06ConverterContract.cycleIndex(sim.t + E06_ACCEPTANCE_CANARY_DT) ==
                    E06ConverterContract.cycleIndex(sim.t);
        }

        void runFailureCanary() {
            Rb100PilotReference.require(primeReady(), "E06 private fixture lacks a current accepted predictor");
            acceptedPredictorBefore = converter.hasAcceptedPredictorForDeveloperVerification();
            candidateSnapshot = converter.captureAcceptedStateForDeveloperVerification();
            simulatedSecondsBefore = sim.t;
            int rejectedTimeStepCountBefore = sim.timeStepCount;
            expectedFailureMessage = "e06-late-callback".equals(canary) ?
                "e06-explicit-late-step-finished" : "e06-explicit-accepted-event";
            if ("e06-late-callback".equals(canary)) {
                lateFailure.arm();
            } else {
                proof.events().schedule(sim.t + E06_ACCEPTANCE_CANARY_DT / 2.0,
                    new SolverEventQueue.Action() {
                        public void fire(double scheduledTime, double acceptedTime) {
                            eventCallbackObserved = true;
                            eventCandidateObserved = converter.hasPendingAcceptedStateForDeveloperVerification() &&
                                converter.matchesAcceptedStateForDeveloperVerification(candidateSnapshot) &&
                                sim.t > simulatedSecondsBefore && acceptedTime == sim.t &&
                                scheduledTime > simulatedSecondsBefore &&
                                !sim.solverExecutor.hasAcceptedStepAt(sim.t);
                            if (!eventCandidateObserved)
                                throw new IllegalStateException("e06-event-failure-candidate-not-staged");
                            throw new IllegalStateException(expectedFailureMessage);
                        }
                    });
            }
            fixedStepVerified &= fixedStepCurrent();
            double injectionStarted = beginUnit();
            try { proof.advanceSteps(1); }
            catch (Throwable failure) { injectedFailure = failure; }
            finally { endUnit(injectionStarted); }
            privateTimeStepAtRejection = sim.timeStep;
            privateMinTimeStepAtRejection = sim.minTimeStep;
            privateMaxTimeStepAtRejection = sim.maxTimeStep;
            privateAdjustTimeStepAtRejection = sim.adjustTimeStep;
            simulatedSecondsAfter = sim.t;
            advancedTime = simulatedSecondsAfter > simulatedSecondsBefore;
            rejectedAttemptSteps = (int)Math.round((simulatedSecondsAfter - simulatedSecondsBefore) /
                E06_ACCEPTANCE_CANARY_DT);
            rejectedAttemptTimeStepCount = sim.timeStepCount - rejectedTimeStepCountBefore;
            double rejectedStepTimeError = Math.abs((simulatedSecondsAfter - simulatedSecondsBefore) -
                rejectedAttemptTimeStepCount * E06_ACCEPTANCE_CANARY_DT);
            maxStepTimeError = Math.max(maxStepTimeError, rejectedStepTimeError);
            exactCountVerified &= rejectedAttemptTimeStepCount == 1 && rejectedAttemptSteps == 1 &&
                finite(rejectedStepTimeError) && rejectedStepTimeError <= 1e-8;
            fixedStepVerified &= fixedStepCurrent();
            pendingAfterFailure = converter.hasPendingAcceptedStateForDeveloperVerification();
            committedSnapshotUnchanged = converter.matchesAcceptedStateForDeveloperVerification(candidateSnapshot);
            acceptedAtRejectedTime = sim.solverExecutor.hasAcceptedStepAt(sim.t);
            boolean expectedFailure = injectedFailure != null &&
                injectedFailure.getClass() == IllegalStateException.class &&
                expectedFailureMessage.equals(injectedFailure.getMessage());
            expectedFailureObserved = expectedFailure;
            if ("e06-late-callback".equals(canary)) {
                pendingObserved = lateFailure.pendingObserved;
                postStepCallbackObserved = lateFailure.callbackObserved;
            } else pendingObserved = eventCandidateObserved;
            boolean success = expectedFailure && pendingObserved && pendingAfterFailure &&
                committedSnapshotUnchanged && acceptedPredictorBefore &&
                advancedTime && !acceptedAtRejectedTime &&
                ("e06-late-callback".equals(canary) ? postStepCallbackObserved : eventCallbackObserved);
            if (!success) throw new IllegalStateException("E06 acceptance boundary canary did not observe the exact rejected candidate",
                injectedFailure);
            requireUnitBudgets();
            finish(null);
        }

        void checkOperationBudget() {
            double elapsed = CircuitSolverExecutor.wallTimeMillis() - started;
            if (!finite(elapsed) || elapsed > E06_ACCEPTANCE_CANARY_OPERATION_LIMIT_MS)
                throw new IllegalStateException("E06 acceptance canary exceeded its 90 second operation budget");
        }

        double beginUnit() {
            work++;
            return CircuitSolverExecutor.wallTimeMillis();
        }

        void endUnit(double unitStarted) {
            double elapsed = CircuitSolverExecutor.wallTimeMillis() - unitStarted;
            if (!finite(elapsed) || elapsed < 0 || elapsed > E06_ACCEPTANCE_CANARY_MAX_UNIT_MS)
                unitLimitViolated = true;
            if (finite(elapsed) && elapsed >= 0) maxUnitMs = Math.max(maxUnitMs, elapsed);
        }

        void requireUnitBudgets() {
            if (work < 1 || work > E06_ACCEPTANCE_CANARY_MAX_WORK)
                throw new IllegalStateException("E06 acceptance canary exceeded its 640 work-unit budget");
            if (unitLimitViolated || maxUnitMs > E06_ACCEPTANCE_CANARY_MAX_UNIT_MS)
                throw new IllegalStateException("E06 acceptance canary exceeded its 5 second unit budget");
        }

        boolean fixedStepCurrent() {
            return sim.timeStep == E06_ACCEPTANCE_CANARY_DT &&
                sim.maxTimeStep == E06_ACCEPTANCE_CANARY_DT &&
                sim.minTimeStep == E06_ACCEPTANCE_CANARY_DT && !sim.adjustTimeStep;
        }

        void finish(Throwable failure) {
            if (finished) return;
            finished = true;
            double operationMs = CircuitSolverExecutor.wallTimeMillis() - started;
            double cleanupStarted = CircuitSolverExecutor.wallTimeMillis();
            Throwable cleanupFailure = null;
            try { if (proof != null) proof.close(); }
            catch (Throwable problem) { cleanupFailure = problem; }
            if (!installed && fixture != null) {
                for (CircuitElm element : fixture.elements) {
                    try { element.delete(); }
                    catch (Throwable problem) { cleanupFailure = retain(cleanupFailure, problem); }
                }
                fixture.elements.clear();
            }
            boolean graphStable = false, ownerStable = false, dumpStable = false;
            boolean predecessorStateUnchanged = false, predecessorPredictorAvailable = false;
            boolean candidateOutOfLiveGraph = false, privateAuthorityClosed = false;
            try {
                graphStable = sim.elmList == predecessorGraph;
                ownerStable = sim.generatedBoardInstance == predecessorOwner && CircuitElm.sim == sim &&
                    !sim.solverExecutor.isUnavailable() && sim.simIsRunning() == predecessorRunning &&
                    !sim.developerVerifierRunning;
                quickPlaySessionIdentityStable = sim.quickPlaySession == predecessorQuickPlaySession &&
                    predecessorQuickPlaySession != null && predecessorQuickPlaySession.getInstance() == predecessorOwner;
                quickPlaySelectionIdentityStable = sim.getQuickPlaySelectionForDeveloperVerification() ==
                    predecessorQuickPlaySelection && predecessorQuickPlaySelection != null &&
                    E06_ACCEPTANCE_CANARY_FAMILY.equals(predecessorQuickPlaySelection.getFamilyId()) &&
                    predecessorQuickPlaySelection.getSeed() == E06_ACCEPTANCE_CANARY_SEED;
                privateAuthorityClosed = !sim.solverExecutor.isUnavailable();
                candidateOutOfLiveGraph = fixture == null || graphStable &&
                    !containsAny(sim.elmList, fixture.elements);
                Vector<E06AveragedConverterElm> restoredModels = findE06Models(sim.elmList);
                predecessorE06CountAfter = restoredModels.size();
                predecessorModelIdentityStable = restoredModels.size() == 1 &&
                    restoredModels.firstElement() == predecessorModel;
                boolean safePredecessor = cleanupFailure == null && graphStable && ownerStable &&
                    quickPlaySessionIdentityStable && quickPlaySelectionIdentityStable &&
                    predecessorModelIdentityStable && privateAuthorityClosed;
                if (safePredecessor) {
                    DiodeElm.lastModelName = diode; ZenerElm.lastZenerModelName = zener;
                    TransistorElm.lastModelName = transistor; LEDElm.lastLEDModelName = led;
                }
                dumpStable = safePredecessor && predecessorDump.equals(sim.dumpCircuit());
                predecessorStateUnchanged = predecessorModel.matchesAcceptedStateForDeveloperVerification(
                    predecessorSnapshot);
                predecessorPredictorAvailable = predecessorModel.hasAcceptedPredictorForDeveloperVerification();
                Rb100PilotReference.require(cleanupFailure == null && graphStable && ownerStable && dumpStable &&
                    predecessorStateUnchanged && predecessorPredictorAvailable &&
                    predecessorModelIdentityStable && candidateOutOfLiveGraph &&
                    privateAuthorityClosed && quickPlaySessionIdentityStable &&
                    quickPlaySelectionIdentityStable,
                    "E06 canary predecessor was not exactly restored");
            } catch (Throwable problem) { cleanupFailure = retain(cleanupFailure, problem); }
            double cleanupMs = CircuitSolverExecutor.wallTimeMillis() - cleanupStarted;
            if ((!finite(operationMs) || operationMs > E06_ACCEPTANCE_CANARY_OPERATION_LIMIT_MS) && failure == null)
                failure = new IllegalStateException("E06 acceptance canary exceeded its 90 second operation budget");
            if ((!finite(cleanupMs) || cleanupMs > E06_ACCEPTANCE_CANARY_CLEANUP_LIMIT_MS) && cleanupFailure == null)
                cleanupFailure = new IllegalStateException("E06 acceptance canary cleanup exceeded 120 seconds");
            boolean success = failure == null && cleanupFailure == null && injectedFailure != null &&
                expectedFailureObserved && pendingObserved && pendingAfterFailure && committedSnapshotUnchanged &&
                acceptedPredictorBefore && advancedTime && !acceptedAtRejectedTime && graphStable && ownerStable &&
                dumpStable && predecessorSnapshotBefore && predecessorPredictorBefore &&
                predecessorStateUnchanged && predecessorPredictorAvailable &&
                predecessorModelIdentityStable && candidateE06Count == 1 && candidateOutOfLiveGraph &&
                privateAuthorityClosed && quickPlaySessionIdentityStable && quickPlaySelectionIdentityStable &&
                work >= 1 && work <= E06_ACCEPTANCE_CANARY_MAX_WORK &&
                !unitLimitViolated && maxUnitMs <= E06_ACCEPTANCE_CANARY_MAX_UNIT_MS &&
                maxBatchSteps >= 1 && maxBatchSteps <= E06_ACCEPTANCE_CANARY_BATCH && fixedStepVerified &&
                exactCountVerified && maxStepTimeError <= 1e-8 && rejectedAttemptSteps == 1 &&
                rejectedAttemptTimeStepCount == 1 &&
                ("e06-late-callback".equals(canary) ?
                    postStepCallbackObserved && !eventCallbackObserved && !eventCandidateObserved :
                    !postStepCallbackObserved && eventCallbackObserved && eventCandidateObserved);
            String status = cleanupFailure != null ? "FAIL_CLEANUP" :
                success ? "PASS_E06_ACCEPTANCE_BOUNDARY_CANARY" : "FAIL_E06_ACCEPTANCE_BOUNDARY_CANARY";
            publishE06AcceptanceCanary("{\"protocol\":" + quote(E06_ACCEPTANCE_CANARY_PROTOCOL) +
                ",\"epoch\":" + quote(GenerationDependencyContext.INTERPRETATION_EPOCH) +
                ",\"status\":" + quote(status) + ",\"canary\":" + quote(canary) +
                ",\"family\":" + quote(E06_ACCEPTANCE_CANARY_FAMILY) +
                ",\"seed\":" + quote(Long.toString(E06_ACCEPTANCE_CANARY_SEED)) +
                ",\"quickPlaySelectionVerified\":true" +
                ",\"operationMs\":" + number(operationMs) + ",\"cleanupMs\":" + number(cleanupMs) +
                ",\"operationLimitMs\":" + E06_ACCEPTANCE_CANARY_OPERATION_LIMIT_MS +
                ",\"cleanupLimitMs\":" + E06_ACCEPTANCE_CANARY_CLEANUP_LIMIT_MS +
                ",\"maxUnitLimitMs\":" + E06_ACCEPTANCE_CANARY_MAX_UNIT_MS +
                ",\"maxBatchLimitSteps\":" + E06_ACCEPTANCE_CANARY_BATCH +
                ",\"work\":" + work + ",\"maxUnitMs\":" + number(maxUnitMs) +
                ",\"acceptedSteps\":" + acceptedSteps + ",\"maxBatchSteps\":" + maxBatchSteps +
                // GWT's boolean &= accumulation can use numeric JS storage.
                // Emit literal JSON booleans rather than concatenating that storage.
                ",\"fixedStepVerified\":" + (fixedStepVerified ? "true" : "false") +
                ",\"exactCountVerified\":" + (exactCountVerified ? "true" : "false") +
                ",\"maxStepTimeError\":" + number(maxStepTimeError) +
                ",\"timeStepAtRejection\":" + number(privateTimeStepAtRejection) +
                ",\"minTimeStepAtRejection\":" + number(privateMinTimeStepAtRejection) +
                ",\"maxTimeStepAtRejection\":" + number(privateMaxTimeStepAtRejection) +
                ",\"adjustTimeStepAtRejection\":" + privateAdjustTimeStepAtRejection +
                ",\"interTrialLimitMs\":" + CircuitSolverExecutor.WALL_LIMIT_MS +
                ",\"cleanup\":" + quote(cleanupFailure == null ? "PASS" : "FAIL") +
                ",\"cleanupError\":" + quote(cleanupFailure == null ? null : cleanupFailure.getMessage()) +
                ",\"error\":" + quote(failure == null ? null : failure.getMessage()) +
                ",\"expectedFailureType\":\"java.lang.IllegalStateException\",\"expectedFailureMessage\":" +
                quote(expectedFailureMessage) + ",\"observedFailureType\":" +
                quote(injectedFailure == null ? null : injectedFailure.getClass().getName()) +
                ",\"observedFailureMessage\":" +
                quote(injectedFailure == null ? null : injectedFailure.getMessage()) +
                ",\"expectedFailureObserved\":" + expectedFailureObserved +
                ",\"failurePhase\":" + quote("e06-late-callback".equals(canary) ?
                    "STEP_FINISHED_AFTER_E06_STAGE" : "ACCEPTED_EVENT_AFTER_E06_STAGE") +
                ",\"predecessor\":{\"graphIdentityStable\":" + graphStable +
                ",\"ownerIdentityStable\":" + ownerStable + ",\"exactDumpRestored\":" + dumpStable +
                ",\"quickPlaySessionIdentityStable\":" + quickPlaySessionIdentityStable +
                ",\"quickPlaySelectionIdentityStable\":" + quickPlaySelectionIdentityStable +
                ",\"e06CountBefore\":1,\"e06CountAfter\":" + predecessorE06CountAfter +
                ",\"modelIdentityStable\":" + predecessorModelIdentityStable +
                ",\"acceptedSnapshotUnchangedBefore\":" + predecessorSnapshotBefore +
                ",\"acceptedSnapshotUnchangedAfter\":" + predecessorStateUnchanged +
                ",\"acceptedPredictorAvailableBefore\":" + predecessorPredictorBefore +
                ",\"acceptedPredictorAvailableAfter\":" + predecessorPredictorAvailable +
                "},\"candidate\":{\"e06Count\":" + candidateE06Count +
                ",\"acceptedPredictorAvailableBefore\":" + acceptedPredictorBefore +
                ",\"privateCandidatePendingObserved\":" + pendingObserved +
                ",\"privateCandidatePendingAfterRejection\":" + pendingAfterFailure +
                ",\"committedSnapshotUnchanged\":" + committedSnapshotUnchanged +
                ",\"simulatedSecondsBefore\":" + number(simulatedSecondsBefore) +
                ",\"simulatedSecondsAfter\":" + number(simulatedSecondsAfter) +
                ",\"simulatedSecondsAdvanced\":" + advancedTime +
                ",\"rejectedAttemptSteps\":" + rejectedAttemptSteps +
                ",\"rejectedAttemptTimeStepCount\":" + rejectedAttemptTimeStepCount +
                ",\"acceptedStepAtRejectedTime\":" + acceptedAtRejectedTime +
                ",\"postStepCallbackObserved\":" + postStepCallbackObserved +
                ",\"eventCallbackObserved\":" + eventCallbackObserved +
                ",\"eventCandidateObserved\":" + eventCandidateObserved +
                ",\"candidateOutOfLiveGraph\":" + candidateOutOfLiveGraph +
                ",\"privateAuthorityClosed\":" + privateAuthorityClosed + "}}" );
            CirSim.publishBrowserVerificationResult(success ? "PASS:e06-acceptance-canary" :
                "FAIL:e06-acceptance-canary");
        }

        private boolean finite(double value) { return !Double.isNaN(value) && !Double.isInfinite(value); }
        private boolean containsAny(Vector<CircuitElm> graph, Vector<CircuitElm> candidateElements) {
            for (CircuitElm element : candidateElements) if (graph.contains(element)) return true;
            return false;
        }
        private String number(double value) { return finite(value) ? Double.toString(value) : "null"; }
        private Throwable retain(Throwable original, Throwable next) {
            if (original == null) return next;
            if (original != next) original.addSuppressed(next);
            return original;
        }
    }

    private static final class E06BoundaryFailureElm extends CircuitElm {
        final E06BoundaryCanaryRunner runner;
        boolean armed, callbackObserved, pendingObserved;
        E06BoundaryFailureElm(E06BoundaryCanaryRunner runner) { super(0, 0); this.runner = runner; }
        int getDumpType() { return 'x'; }
        int getPostCount() { return 0; }
        void setPoints() {
            point1 = new Point(x, y); point2 = point1;
            boundingBox = new Rectangle(x, y, 1, 1);
        }
        void arm() { armed = true; callbackObserved = false; pendingObserved = false; }
        void stepFinished() {
            if (!armed) return;
            armed = false; callbackObserved = true;
            pendingObserved = runner.converter.hasPendingAcceptedStateForDeveloperVerification() &&
                runner.converter.matchesAcceptedStateForDeveloperVerification(runner.candidateSnapshot) &&
                runner.sim.t > runner.simulatedSecondsBefore &&
                !runner.sim.solverExecutor.hasAcceptedStepAt(runner.sim.t);
            if (!pendingObserved)
                throw new IllegalStateException("e06-late-callback-candidate-not-staged");
            runner.pendingObserved = true;
            throw new IllegalStateException(runner.expectedFailureMessage);
        }
    }

    static void start(final CirSim sim, final String variantText, final String faultText,
            final String dtText, final String canaryText) {
        Scheduler.get().scheduleDeferred(new Scheduler.ScheduledCommand() {
            public void execute() {
                try {
                    Rb100PilotReference.require(TroubleshootBuild.isDeveloper() && sim.troubleshootDebug &&
                        sim.troubleshootQ100Verification, "explicit Developer route required");
                    Rb100PilotReference.require(!sim.developerVerifierRunning && sim.playerSessionController == null &&
                        !sim.activeMeasurementOverlay && sim.isGeneratedRuntimeSettled() &&
                        !GeneratedDiagnosticSolvabilityAdmission.isInternalProofRunning() &&
                        (sim.generationCoordinator == null || !sim.generationCoordinator.isRunning()),
                        "idle settled private workbench required");
                    Rb100Plan.Variant variant = parseVariant(variantText);
                    Rb100PilotFaults.Fault fault = faultText == null || "HEALTHY".equals(faultText) ?
                        null : Rb100PilotFaults.find(faultText);
                    double dt = dtText == null ? (fault == null ? .0001 : .00005) : Double.parseDouble(dtText);
                    Rb100PilotReference.require(dt == .0001 || dt == .00005, "unsupported reference timestep");
                    Rb100PilotReference.require(canaryText == null || "cancel".equals(canaryText) ||
                        "after-step".equals(canaryText) || "interaction-lock".equals(canaryText),
                        "unsupported reference canary");
                    new Runner(sim, variant, fault, dt, canaryText).begin();
                } catch (Throwable failure) {
                    publish("{\"status\":\"REJECTED_BEFORE_PRIVATE_GRAPH\",\"error\":" + quote(failure.getMessage()) +
                        ",\"cleanup\":\"NOT_APPLICABLE\"}");
                    CirSim.publishBrowserVerificationResult("FAIL:q100:preflight:" + failure.getMessage());
                }
            }
        });
    }
    private static Rb100Plan.Variant parseVariant(String text) {
        if (text == null) return Rb100Plan.Variant.SHARED_HYSTERETIC_LINEAR;
        for (Rb100Plan.Variant variant : Rb100Plan.Variant.values())
            if (variant.name().equals(text)) return variant;
        throw new IllegalArgumentException("Unknown RB100 structural variant");
    }
    private static final class Runner {
        final CirSim sim;
        final Rb100Plan.Variant variant;
        final Rb100PilotFaults.Fault fault;
        final double dt;
        final String canary;
        final long started = System.currentTimeMillis();
        final Vector<CircuitElm> predecessor;
        final String predecessorDump;
        final GeneratedBoardInstance predecessorOwner;
        final boolean predecessorRunning;
        final String diode, zener, transistor, led;
        PrivateSolverContext proof;
        Rb100ElectricalPilot.Candidate candidate;
        Rb100PilotReference reference;
        boolean installed, finished, injectedCancellation;
        String interactionLockJson;
        int turns;
        Runner(CirSim sim, Rb100Plan.Variant variant, Rb100PilotFaults.Fault fault, double dt, String canary) {
            this.sim = sim; this.variant = variant; this.fault = fault; this.dt = dt; this.canary = canary;
            predecessor = sim.elmList; predecessorDump = sim.dumpCircuit();
            predecessorOwner = sim.generatedBoardInstance; predecessorRunning = sim.simIsRunning();
            diode = DiodeElm.lastModelName; zener = ZenerElm.lastZenerModelName;
            transistor = TransistorElm.lastModelName; led = LEDElm.lastLEDModelName;
        }
        void begin() {
            try {
                proof = PrivateSolverContext.open(sim);
                sim.developerVerifierRunning = true;
                sim.timeStep = sim.maxTimeStep = sim.minTimeStep = dt;
                sim.adjustTimeStep = false;
                candidate = Rb100ElectricalPilot.construct(Rb100Plan.create(Rb100PilotReference.SEED, variant));
                GeneratedFaultEffect effect = fault == null ? null : Rb100PilotFaults.bind(candidate, fault);
                proof.install(candidate.elements()); installed = true;
                reference = new Rb100PilotReference(sim, candidate, fault, effect,
                    new Rb100PilotReference.Stepper() {
                        public void analyze() { proof.analyze(); }
                        public void advance(int count) { proof.advanceFor(count * dt); }
                    }, dt, started);
                CirSim.publishBrowserVerificationResult("RUNNING:q100");
                defer();
            } catch (Throwable failure) { finish(null, failure); }
        }
        void defer() {
            Scheduler.get().scheduleDeferred(new Scheduler.ScheduledCommand() {
                public void execute() {
                    if (finished) return;
                    try {
                        boolean done = reference.advance();
                        int turn = ++turns;
                        if (turn == 1 && "interaction-lock".equals(canary))
                            interactionLockJson = verifyInteractionLock();
                        if (turn == 2 && canary != null) {
                            injectedCancellation = "cancel".equals(canary) || "interaction-lock".equals(canary);
                            throw new IllegalStateException("q100-explicit-" + canary + "-after-accepted-step");
                        }
                        if (done) finish(reference.report(), null); else defer();
                    } catch (Throwable failure) { finish(null, failure); }
                }
            });
        }
        String verifyInteractionLock() {
            Rb100PilotReference.require(sim.solverExecutor.isUnavailable() && sim.elmList != null &&
                sim.elmList != predecessor && sim.generatedBoardInstance == null && CircuitElm.sim == sim,
                "interaction lock requires the owned private solver graph");
            final Vector<CircuitElm> graph = sim.elmList;
            final int countBefore = graph.size();
            Rb100PilotReference.require(countBefore > 1 && sim.undoStack != null,
                "interaction lock canary requires an editable private graph");
            final CircuitElm[] elements = new CircuitElm[countBefore];
            final boolean[] selected = new boolean[countBefore];
            graph.firstElement().setSelected(true);
            boolean hasUnselectedElement = false;
            for (int i = 0; i < countBefore; i++) {
                elements[i] = graph.elementAt(i);
                selected[i] = elements[i].isSelected();
                if (!selected[i]) hasUnselectedElement = true;
            }
            Rb100PilotReference.require(hasUnselectedElement,
                "interaction lock canary requires an unselected private element");
            final int undoDepthBefore = sim.undoStack.size();
            final double timeBefore = sim.t;
            final long acceptedStepsBefore = sim.a01AcceptedStepCount;
            final boolean runningBefore = sim.simIsRunning();

            Rb100PilotReference.require(sim.noEditCheckItem != null &&
                !sim.dialogIsShowing() && CirSim.scrollValuePopup == null,
                "wheel interaction lock canary requires an unobstructed private schematic");
            final ResistorElm wheelResistor = candidate.backing.get("RMON") instanceof ResistorElm ?
                (ResistorElm) candidate.backing.get("RMON") : null;
            Rb100PilotReference.require(wheelResistor != null && wheelResistor.getResistance() > 0 &&
                !Double.isNaN(wheelResistor.getResistance()) && !Double.isInfinite(wheelResistor.getResistance()),
                "wheel interaction lock canary requires the actual private RMON resistor");
            final double wheelRBefore = wheelResistor.getResistance();
            final boolean noEditBefore = sim.noEditCheckItem.getState();
            final int scopeBefore = sim.scopeSelected;
            final long zoomTimeBefore = sim.zoomTime;
            final CircuitElm mouseElmBefore = sim.getMouseElmForDeveloperVerification();
            final ScrollValuePopup popupBefore = CirSim.scrollValuePopup;
            final long wheelAttemptZoomTime = System.currentTimeMillis() - 5000L;
            boolean wheelAttemptBlocked = false;
            boolean popupUnchanged = false;
            boolean wheelUiStable = false;
            boolean nativeWheelPreviewBlocked;
            boolean nativeChangePreviewBlocked;
            double wheelRAfter = Double.NaN;
            int wheelDelta = 0;
            try {
                sim.noEditCheckItem.setState(false);
                sim.scopeSelected = -1;
                sim.zoomTime = wheelAttemptZoomTime;
                sim.restoreMouseElmForDeveloperVerification(wheelResistor);

                MouseWheelEvent directWheel = new MouseWheelEvent() { };
                directWheel.setNativeEvent(createWheelNativeEvent());
                wheelDelta = directWheel.getDeltaY();
                Rb100PilotReference.require(wheelDelta != 0,
                    "wheel interaction lock canary requires a nonzero native wheel delta");
                sim.onMouseWheel(directWheel);
                wheelRAfter = wheelResistor.getResistance();
                popupUnchanged = CirSim.scrollValuePopup == popupBefore;
                wheelUiStable = !sim.noEditCheckItem.getState() && sim.scopeSelected == -1 &&
                    sim.zoomTime == wheelAttemptZoomTime &&
                    sim.getMouseElmForDeveloperVerification() == wheelResistor;
                wheelAttemptBlocked = wheelRBefore == wheelRAfter && popupUnchanged && wheelUiStable;

                nativeWheelPreviewBlocked = !Event.fireNativePreviewEvent(createWheelNativeEvent());
                nativeChangePreviewBlocked = !Event.fireNativePreviewEvent(Document.get().createChangeEvent());
            } finally {
                ScrollValuePopup popupAfterAttempt = CirSim.scrollValuePopup;
                try {
                    if (wheelResistor.getResistance() != wheelRBefore)
                        wheelResistor.setResistance(wheelRBefore);
                } finally {
                    try {
                        if (popupAfterAttempt != popupBefore) {
                            CirSim.scrollValuePopup = popupBefore;
                            if (popupAfterAttempt != null) popupAfterAttempt.hide();
                        }
                    } finally {
                        try {
                            sim.noEditCheckItem.setState(noEditBefore);
                            sim.scopeSelected = scopeBefore;
                            sim.zoomTime = zoomTimeBefore;
                        } finally {
                            sim.restoreMouseElmForDeveloperVerification(mouseElmBefore);
                        }
                    }
                }
            }

            sim.menuPerformed("key", "selectAll");
            sim.menuPerformed("elm", "delete");
            sim.toggleSimRunningFromUserInput();

            boolean graphIdentityStable = sim.elmList == graph;
            boolean elementIdentityStable = graph.size() == countBefore;
            boolean selectionStable = graph.size() == countBefore;
            for (int i = 0; i < countBefore && graph.size() == countBefore; i++) {
                elementIdentityStable &= graph.elementAt(i) == elements[i];
                selectionStable &= elements[i].isSelected() == selected[i];
            }
            boolean deletionAttemptBlocked = graphIdentityStable && elementIdentityStable &&
                sim.undoStack.size() == undoDepthBefore;
            boolean selectionAttemptBlocked = selectionStable;
            boolean runToggleBlocked = sim.simIsRunning() == runningBefore;
            boolean privateOwnerStillUnavailable = sim.solverExecutor.isUnavailable();
            double timeAfter = sim.t;
            long acceptedStepsAfter = sim.a01AcceptedStepCount;
            int undoDepthAfter = sim.undoStack.size();
            int countAfter = graph.size();
            boolean privateCircuitStable = graphIdentityStable && elementIdentityStable && selectionStable &&
                timeAfter == timeBefore && acceptedStepsAfter == acceptedStepsBefore &&
                sim.simIsRunning() == runningBefore && undoDepthAfter == undoDepthBefore &&
                wheelAttemptBlocked && wheelUiStable && nativeWheelPreviewBlocked &&
                nativeChangePreviewBlocked && popupUnchanged;
            boolean allBlocked = graphIdentityStable && elementIdentityStable && selectionStable &&
                deletionAttemptBlocked && selectionAttemptBlocked && runToggleBlocked &&
                privateOwnerStillUnavailable && timeAfter == timeBefore &&
                acceptedStepsAfter == acceptedStepsBefore && sim.simIsRunning() == runningBefore &&
                undoDepthAfter == undoDepthBefore && privateCircuitStable;
            Rb100PilotReference.require(allBlocked, "normal schematic input escaped private solver interaction lock");
            return "{\"status\":\"PASS\",\"selectionAttemptBlocked\":true,\"deletionAttemptBlocked\":true," +
                "\"runToggleBlocked\":true,\"graphIdentityStable\":true,\"elementIdentityStable\":true," +
                "\"elementCountBefore\":" + countBefore + ",\"elementCountAfter\":" + countAfter +
                ",\"selectionStable\":true,\"undoDepthBefore\":" + undoDepthBefore +
                ",\"undoDepthAfter\":" + undoDepthAfter + ",\"simulationTimeBefore\":" + timeBefore +
                ",\"simulationTimeAfter\":" + timeAfter + ",\"acceptedStepCountBefore\":" + acceptedStepsBefore +
                ",\"acceptedStepCountAfter\":" + acceptedStepsAfter + ",\"runningBefore\":" + runningBefore +
                ",\"runningAfter\":" + sim.simIsRunning() +
                ",\"privateOwnerStillUnavailable\":true," +
                "\"wheelAttemptBlocked\":" + wheelAttemptBlocked +
                ",\"nativeWheelPreviewBlocked\":" + nativeWheelPreviewBlocked +
                ",\"nativeChangePreviewBlocked\":" + nativeChangePreviewBlocked +
                ",\"privateCircuitStable\":" + privateCircuitStable +
                ",\"wheelRBefore\":" + wheelRBefore + ",\"wheelRAfter\":" + wheelRAfter +
                ",\"wheelDelta\":" + wheelDelta + ",\"popupUnchanged\":" + popupUnchanged + "}";
        }
        void finish(String report, Throwable failure) {
            if (finished) return;
            finished = true;
            if (report == null && reference != null) {
                try { report = reference.diagnosticReport(); }
                catch (Throwable problem) {
                    if (failure == null) failure = problem; else failure.addSuppressed(problem);
                }
            }
            long operationMillis = System.currentTimeMillis() - started;
            long cleanupStarted = System.currentTimeMillis();
            Throwable cleanupFailure = null;
            try { if (proof != null) proof.close(); }
            catch (Throwable problem) { cleanupFailure = problem; }
            try { if (!installed && candidate != null) candidate.dispose(); }
            catch (Throwable problem) {
                if (cleanupFailure == null) cleanupFailure = problem; else cleanupFailure.addSuppressed(problem);
            }
            boolean safePredecessorRestored = false;
            try {
                Rb100PilotReference.require(proof != null && cleanupFailure == null && sim.elmList == predecessor &&
                    sim.generatedBoardInstance == predecessorOwner && CircuitElm.sim == sim &&
                    !sim.solverExecutor.isUnavailable() && sim.simIsRunning() == predecessorRunning &&
                    !sim.developerVerifierRunning, "private reference predecessor owner safely restored");
                safePredecessorRestored = true;
            } catch (Throwable problem) {
                if (cleanupFailure == null) cleanupFailure = problem; else cleanupFailure.addSuppressed(problem);
            }
            if (safePredecessorRestored) {
                DiodeElm.lastModelName = diode; ZenerElm.lastZenerModelName = zener;
                TransistorElm.lastModelName = transistor; LEDElm.lastLEDModelName = led;
                try {
                    Rb100PilotReference.require(predecessorDump.equals(sim.dumpCircuit()),
                        "exact predecessor restored after private reference");
                } catch (Throwable problem) {
                    if (cleanupFailure == null) cleanupFailure = problem; else cleanupFailure.addSuppressed(problem);
                }
            }
            long cleanupMillis = System.currentTimeMillis() - cleanupStarted;
            if (cleanupMillis > 120000 && cleanupFailure == null)
                cleanupFailure = new IllegalStateException("RB100 private cleanup exceeded 120 seconds");
            String status = cleanupFailure != null ? "FAIL_CLEANUP" : failure != null ?
                (injectedCancellation ? "CANCELLED_REFERENCE" : "FAIL_REFERENCE") : "PASS_ELECTRICAL_REFERENCE_ONLY";
            publish("{\"status\":" + quote(status) + ",\"operationMs\":" + operationMillis +
                ",\"cleanupMs\":" + cleanupMillis + ",\"cleanup\":" + quote(cleanupFailure == null ? "PASS" : "FAIL") +
                ",\"error\":" + quote(failure == null ? null : failure.getMessage()) +
                ",\"cleanupError\":" + quote(cleanupFailure == null ? null : cleanupFailure.getMessage()) +
                ",\"canary\":" + quote(canary) + ",\"reference\":" + (report == null ? "null" : report) +
                ("interaction-lock".equals(canary) ? ",\"interactionLock\":" +
                    (interactionLockJson == null ? "null" : interactionLockJson) : "") + "}");
            CirSim.publishBrowserVerificationResult((failure == null && cleanupFailure == null ? "PASS" :
                injectedCancellation && cleanupFailure == null ? "CANCELLED" : "FAIL") + ":q100");
        }
    }
    private static String quote(String value) {
        if (value == null) return "null";
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"")
            .replace("\r", "\\r").replace("\n", "\\n").replace("\t", "\\t") + "\"";
    }
    private static native void publish(String evidence) /*-{
        $doc.documentElement.setAttribute("data-tsj-q100-report", evidence);
    }-*/;
    private static native void publishE06AcceptanceCanary(String evidence) /*-{
        $doc.documentElement.setAttribute("data-tsj-e06-acceptance-canary", evidence);
    }-*/;
    private static native NativeEvent createWheelNativeEvent() /*-{
        var event = $doc.createEvent("MouseEvents");
        event.initMouseEvent("mousewheel", true, true, $wnd, 3,
            0, 0, 0, 0, false, false, false, false, 0, null);
        try {
            Object.defineProperty(event, "wheelDelta", {value: -120});
        } catch (failure) {
            event.wheelDelta = -120;
        }
        return event;
    }-*/;
}
