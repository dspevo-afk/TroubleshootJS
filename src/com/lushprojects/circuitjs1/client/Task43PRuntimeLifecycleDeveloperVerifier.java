package com.lushprojects.circuitjs1.client;

/**
 * Developer-only canaries for exception propagation and cleanup of a temporary
 * active measurement.  Each case runs against a disposable generated owner and
 * restores the exact owner snapshot before the next case starts.
 */
final class Task43PRuntimeLifecycleDeveloperVerifier {
    private static final String FAMILY_ID = QuickPlayFamilyRegistry.PARALLEL_DUAL_INDICATOR;
    private static final long SEED = 0;

    private Task43PRuntimeLifecycleDeveloperVerifier() { }

    static String verify(CirSim sim) {
        require(sim != null, "task43p-runtime-missing-simulator");
        GeneratedBoardInstance originalOwner = sim.getGeneratedBoardInstance();
        GeneratedChallengeController originalChallenge = sim.getGeneratedChallengeController();
        require(originalOwner != null && originalChallenge != null && originalChallenge.isReady(),
            "task43p-runtime-original-owner-not-ready");

        Task41SimulationSnapshot snapshot = Task41SimulationSnapshot.capture(sim);
        int cleanupSafetyCaseCount = Task43PMeasurementCleanupDeveloperVerifier.verify(sim);
        StringBuilder cases = new StringBuilder();
        boolean originalOwnerRestored = false;
        int caseCount = 0;
        try {
            CirSim.Task43PMeasurementFailureStage[] stages = {
                CirSim.Task43PMeasurementFailureStage.READER,
                CirSim.Task43PMeasurementFailureStage.AFTER_STIMULUS_REMOVE
            };
            for (CirSim.Task43PMeasurementFailureStage stage : stages) {
                for (int queuedPower = 0; queuedPower != 2; queuedPower++) {
                    snapshot.beginProof(sim);
                    CaseObservation observation = null;
                    try {
                        observation = runCase(sim, stage, queuedPower != 0);
                        if (caseCount != 0)
                            cases.append(',');
                        cases.append(observation.toJson());
                        caseCount++;
                    } finally {
                        /* Capture is complete before either hook state or the
                         * disposable graph is changed. */
                        try {
                            sim.clearTask43PMeasurementFailureForDeveloperVerification();
                        } finally {
                            snapshot.restore(sim);
                            snapshot.assertRestored(sim);
                            require(sim.getGeneratedBoardInstance() == originalOwner &&
                                    sim.getGeneratedChallengeController() == originalChallenge,
                                "task43p-runtime-original-owner-not-restored-after-case");
                        }
                    }
                }
            }
            originalOwnerRestored = sim.getGeneratedBoardInstance() == originalOwner &&
                sim.getGeneratedChallengeController() == originalChallenge;
            require(originalOwnerRestored && caseCount == 4,
                "task43p-runtime-incomplete-case-corpus");
            return "{\"protocol\":\"TSJ-TASK43P-D-1\",\"status\":\"OBSERVED\",\"originalOwnerRestored\":" +
                originalOwnerRestored + ",\"caseCount\":" + caseCount +
                ",\"cleanupSafetyCaseCount\":" + cleanupSafetyCaseCount +
                ",\"cases\":[" + cases.toString() + "]}";
        } finally {
            /* This also repairs the owner if an unexpected exception or a
             * missing injection aborts the case loop before its inner finally. */
            try {
                sim.clearTask43PMeasurementFailureForDeveloperVerification();
            } finally {
                if (sim.getGeneratedBoardInstance() != originalOwner ||
                        sim.getGeneratedChallengeController() != originalChallenge) {
                    snapshot.restore(sim);
                    snapshot.assertRestored(sim);
                }
            }
        }
    }

    private static CaseObservation runCase(CirSim sim,
            CirSim.Task43PMeasurementFailureStage stage, boolean queuedPower) {
        GeneratedBoardInstance candidate = QuickPlayFamilyRegistry.generate(FAMILY_ID, SEED);
        sim.installGeneratedChallengeForDeveloperVerification(candidate);
        require(sim.getAttachedPcbWorkbenchCountForDeveloperVerification() == 0,
            "task43p-runtime-candidate-retained-player-workbench");
        settleReady(sim, candidate);
        sim.setBoardPowerState(BoardPowerState.UNPOWERED);
        GeneratedRuntimeDeveloperSettlement.settle(sim, candidate,
            "task43p-runtime-unpowered");
        require(sim.getBoardPowerController().getState() == BoardPowerState.UNPOWERED &&
                sim.getBoardPowerController().isElectricallyUnpowered(),
            "task43p-runtime-candidate-did-not-reach-electrically-unpowered");

        CircuitElm healthyResistor = candidate.getComponentBindings().getSingleElement("R2");
        require(healthyResistor != null && healthyResistor.getPostCount() == 2,
            "task43p-runtime-healthy-r2-binding-invalid");
        CircuitPostProbeTarget red = new CircuitPostProbeTarget(sim, healthyResistor, 0);
        CircuitPostProbeTarget black = new CircuitPostProbeTarget(sim, healthyResistor, 1);
        require(red.isValid() && black.isValid(),
            "task43p-runtime-healthy-r2-probes-invalid");
        CircuitMeasurementEndpoint redEndpoint = red.getMeasurementEndpoint();
        CircuitMeasurementEndpoint blackEndpoint = black.getMeasurementEndpoint();
        require(redEndpoint instanceof CircuitPostMeasurementEndpoint &&
                blackEndpoint instanceof CircuitPostMeasurementEndpoint,
            "task43p-runtime-healthy-r2-probes-not-circuit-posts");
        require(sim.getActiveMeasurementReadiness((CircuitPostMeasurementEndpoint) redEndpoint,
                (CircuitPostMeasurementEndpoint) blackEndpoint) == ActiveMeasurementReadiness.READY,
            "task43p-runtime-healthy-r2-measurement-not-ready");

        sim.instrumentController.setResistanceProbesForDeveloperVerification(red, black);
        double baselineResistance =
            sim.instrumentController.getLatestResistanceReadingForDeveloperVerification();
        require(!Double.isNaN(baselineResistance) && !Double.isInfinite(baselineResistance) &&
                baselineResistance > 0,
            "task43p-runtime-healthy-r2-control-was-not-finite-positive");
        require(!sim.activeMeasurementOverlay &&
                sim.isActiveMeasurementSolverRestoredForDeveloperVerification(),
            "task43p-runtime-healthy-r2-control-left-measurement-residue");
        sim.instrumentController.clearTargets();
        sim.instrumentController.exitInstrumentModeForDeveloperVerification();
        require(!sim.activeMeasurementOverlay && sim.pendingBoardPowerState == null,
            "task43p-runtime-control-exit-left-measurement-state");
        GeneratedChallengeController candidateChallenge = sim.getGeneratedChallengeController();
        require(candidateChallenge != null && candidateChallenge.isReady(),
            "task43p-runtime-candidate-challenge-lost-before-injection");

        sim.clearTask43PMeasurementFailureForDeveloperVerification();
        if (queuedPower)
            sim.requestPowerOnDuringActiveMeasurementForDeveloperVerification();
        sim.armTask43PMeasurementFailureForDeveloperVerification(stage);

        CirSim.Task43PInjectedMeasurementFailure caught = null;
        try {
            sim.instrumentController.setResistanceProbesForDeveloperVerification(red, black);
        } catch (CirSim.Task43PInjectedMeasurementFailure failure) {
            caught = failure;
        } catch (RuntimeException failure) {
            throw new IllegalStateException("task43p-runtime-unexpected-runtime-failure-" +
                stage + "-queued-" + queuedPower, failure);
        } catch (Error failure) {
            throw failure;
        }
        require(caught != null,
            "task43p-runtime-missing-injected-failure-" + stage + "-queued-" + queuedPower);
        require(caught.getStage() == stage,
            "task43p-runtime-wrong-injected-failure-stage-" + caught.getStage());

        ActiveMeasurementStimulus stimulus = sim.lastActiveMeasurementStimulus;
        require(stimulus != null,
            "task43p-runtime-missing-last-measurement-stimulus-" + stage);
        CircuitElm[] temporaryElements = stimulus.getTemporaryElements();
        require(temporaryElements != null && temporaryElements.length != 0,
            "task43p-runtime-measurement-stimulus-had-no-temporary-elements-" + stage);

        boolean temporaryElementsAbsentFromElmList =
            temporaryElementsAbsentFromElmList(sim, temporaryElements);
        boolean temporaryElementsAbsentFromVoltageSources =
            temporaryElementsAbsentFromVoltageSources(sim, temporaryElements);
        boolean temporaryElementsAbsentFromNodeLinks =
            temporaryElementsAbsentFromNodeLinks(sim, temporaryElements);
        boolean matrixPresent = sim.circuitMatrix != null;
        boolean solverRestored = sim.isActiveMeasurementSolverRestoredForDeveloperVerification();
        boolean candidateOwnerCurrent = sim.getGeneratedBoardInstance() == candidate;
        boolean candidateChallengeCurrent = sim.getGeneratedChallengeController() == candidateChallenge &&
            candidateChallenge.isReady();
        boolean injectionRecorded =
            sim.task43PMeasurementFailureLastInjectedStage == stage &&
            sim.task43PMeasurementFailureInjectionCount == 1 &&
            sim.task43PMeasurementFailureStage ==
                CirSim.Task43PMeasurementFailureStage.NONE;
        require(injectionRecorded,
            "task43p-runtime-injection-provenance-invalid-" + stage + "-queued-" + queuedPower);
        boolean pendingCleared = sim.pendingBoardPowerState == null;
        BoardPowerState expectedPower = queuedPower ? BoardPowerState.POWERED :
            BoardPowerState.UNPOWERED;
        boolean powerRestored = sim.getBoardPowerController().getState() == expectedPower &&
            (queuedPower ? sim.getBoardPowerController().getBindingsForDeveloperVerification() != null &&
                sim.getBoardPowerController().getBindingsForDeveloperVerification().areAllConnected() :
                sim.getBoardPowerController().isElectricallyUnpowered());
        boolean cleanupClosed = caught.getStage() == stage && injectionRecorded &&
            candidateOwnerCurrent && candidateChallengeCurrent &&
            temporaryElementsAbsentFromElmList &&
            temporaryElementsAbsentFromVoltageSources &&
            temporaryElementsAbsentFromNodeLinks && matrixPresent &&
            !sim.activeMeasurementOverlay && sim.activeMeasurementSolverRestored && solverRestored &&
            pendingCleared && powerRestored;

        return new CaseObservation(stage, queuedPower, baselineResistance,
            caught.getClass().getName(), caught.getStage(), stimulus != null,
            temporaryElements.length, injectionRecorded,
            sim.task43PMeasurementFailureInjectionCount,
            sim.task43PMeasurementFailureStage, candidateOwnerCurrent, candidateChallengeCurrent,
            temporaryElementsAbsentFromElmList, temporaryElementsAbsentFromVoltageSources,
            temporaryElementsAbsentFromNodeLinks, matrixPresent, sim.activeMeasurementOverlay,
            sim.activeMeasurementSolverRestored, solverRestored,
            sim.pendingBoardPowerState, sim.getBoardPowerController().getState(),
            powerRestored, sim.instrumentController.isResistanceRefreshPendingForDeveloperVerification(),
            cleanupClosed);
    }

    private static void settleReady(CirSim sim, GeneratedBoardInstance instance) {
        GeneratedRuntimeDeveloperSettlement.settle(sim, instance,
            "task43p-runtime-candidate-" + instance.getCircuitFamilyId());
    }

    private static boolean temporaryElementsAbsentFromElmList(CirSim sim,
            CircuitElm[] temporaryElements) {
        if (sim.elmList == null)
            return false;
        for (CircuitElm element : temporaryElements)
            if (element == null || sim.elmList.contains(element))
                return false;
        return true;
    }

    private static boolean temporaryElementsAbsentFromVoltageSources(CirSim sim,
            CircuitElm[] temporaryElements) {
        if (sim.voltageSources == null)
            return false;
        for (CircuitElm voltageSource : sim.voltageSources)
            for (CircuitElm element : temporaryElements)
                if (voltageSource == element)
                    return false;
        return true;
    }

    private static boolean temporaryElementsAbsentFromNodeLinks(CirSim sim,
            CircuitElm[] temporaryElements) {
        if (sim.nodeList == null)
            return false;
        for (CircuitNode node : sim.nodeList) {
            if (node == null || node.links == null)
                return false;
            for (CircuitNodeLink link : node.links) {
                if (link == null)
                    return false;
                for (CircuitElm element : temporaryElements)
                    if (link.elm == element)
                        return false;
            }
        }
        return true;
    }

    private static void require(boolean condition, String message) {
        if (!condition)
            throw new IllegalStateException(message);
    }

    private static String stageName(CirSim.Task43PMeasurementFailureStage stage) {
        return stage == null ? null : stage.toString();
    }

    private static String powerName(BoardPowerState state) {
        return state == null ? null : state.toString();
    }

    private static String q(String value) {
        if (value == null)
            return "null";
        StringBuilder result = new StringBuilder("\"");
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            if (character == '\\') result.append("\\\\");
            else if (character == '"') result.append("\\\"");
            else if (character == '\n') result.append("\\n");
            else if (character == '\r') result.append("\\r");
            else if (character == '\t') result.append("\\t");
            else result.append(character);
        }
        return result.append('"').toString();
    }

    private static final class CaseObservation {
        private final CirSim.Task43PMeasurementFailureStage stage;
        private final boolean queuedPower;
        private final double baselineResistance;
        private final String caughtType;
        private final CirSim.Task43PMeasurementFailureStage caughtStage;
        private final boolean lastMeasurementStimulusPresent;
        private final int temporaryElementCount;
        private final boolean injectionRecorded;
        private final int injectionCount;
        private final CirSim.Task43PMeasurementFailureStage armedStageAfterCatch;
        private final boolean candidateOwnerCurrent;
        private final boolean candidateChallengeCurrent;
        private final boolean temporaryElementsAbsentFromElmList;
        private final boolean temporaryElementsAbsentFromVoltageSources;
        private final boolean temporaryElementsAbsentFromNodeLinks;
        private final boolean matrixPresent;
        private final boolean activeMeasurementOverlay;
        private final boolean activeMeasurementSolverRestored;
        private final boolean solverRestored;
        private final BoardPowerState pendingPower;
        private final BoardPowerState boardPower;
        private final boolean powerRestored;
        private final boolean resistanceRefreshPending;
        private final boolean cleanupClosed;

        CaseObservation(CirSim.Task43PMeasurementFailureStage stage, boolean queuedPower,
                double baselineResistance, String caughtType,
                CirSim.Task43PMeasurementFailureStage caughtStage,
                boolean lastMeasurementStimulusPresent, int temporaryElementCount,
                boolean injectionRecorded, int injectionCount,
                CirSim.Task43PMeasurementFailureStage armedStageAfterCatch,
                boolean candidateOwnerCurrent, boolean candidateChallengeCurrent,
                boolean temporaryElementsAbsentFromElmList,
                boolean temporaryElementsAbsentFromVoltageSources,
                boolean temporaryElementsAbsentFromNodeLinks, boolean matrixPresent,
                boolean activeMeasurementOverlay, boolean activeMeasurementSolverRestored,
                boolean solverRestored, BoardPowerState pendingPower,
                BoardPowerState boardPower, boolean powerRestored,
                boolean resistanceRefreshPending, boolean cleanupClosed) {
            this.stage = stage;
            this.queuedPower = queuedPower;
            this.baselineResistance = baselineResistance;
            this.caughtType = caughtType;
            this.caughtStage = caughtStage;
            this.lastMeasurementStimulusPresent = lastMeasurementStimulusPresent;
            this.temporaryElementCount = temporaryElementCount;
            this.injectionRecorded = injectionRecorded;
            this.injectionCount = injectionCount;
            this.armedStageAfterCatch = armedStageAfterCatch;
            this.candidateOwnerCurrent = candidateOwnerCurrent;
            this.candidateChallengeCurrent = candidateChallengeCurrent;
            this.temporaryElementsAbsentFromElmList = temporaryElementsAbsentFromElmList;
            this.temporaryElementsAbsentFromVoltageSources = temporaryElementsAbsentFromVoltageSources;
            this.temporaryElementsAbsentFromNodeLinks = temporaryElementsAbsentFromNodeLinks;
            this.matrixPresent = matrixPresent;
            this.activeMeasurementOverlay = activeMeasurementOverlay;
            this.activeMeasurementSolverRestored = activeMeasurementSolverRestored;
            this.solverRestored = solverRestored;
            this.pendingPower = pendingPower;
            this.boardPower = boardPower;
            this.powerRestored = powerRestored;
            this.resistanceRefreshPending = resistanceRefreshPending;
            this.cleanupClosed = cleanupClosed;
        }

        String toJson() {
            StringBuilder result = new StringBuilder();
            result.append("{\"stage\":").append(q(stageName(stage)))
                .append(",\"queuedPower\":").append(queuedPower)
                .append(",\"probeComponent\":\"R2\"")
                .append(",\"baselineResistance\":").append(Double.toString(baselineResistance))
                .append(",\"caughtSentinel\":true")
                .append(",\"caughtType\":").append(q(caughtType))
                .append(",\"caughtStage\":").append(q(stageName(caughtStage)))
                .append(",\"lastActiveMeasurementStimulusPresent\":")
                .append(lastMeasurementStimulusPresent)
                .append(",\"temporaryElementCount\":").append(temporaryElementCount)
                .append(",\"injectionRecorded\":").append(injectionRecorded)
                .append(",\"injectionCount\":").append(injectionCount)
                .append(",\"armedStageAfterCatch\":").append(q(stageName(armedStageAfterCatch)))
                .append(",\"candidateOwnerCurrent\":").append(candidateOwnerCurrent)
                .append(",\"candidateChallengeCurrent\":").append(candidateChallengeCurrent)
                .append(",\"temporaryElementsAbsentFromElmList\":")
                .append(temporaryElementsAbsentFromElmList)
                .append(",\"temporaryElementsAbsentFromVoltageSources\":")
                .append(temporaryElementsAbsentFromVoltageSources)
                .append(",\"temporaryElementsAbsentFromNodeLinks\":")
                .append(temporaryElementsAbsentFromNodeLinks)
                .append(",\"circuitMatrixPresent\":").append(matrixPresent)
                .append(",\"activeMeasurementOverlay\":").append(activeMeasurementOverlay)
                .append(",\"activeMeasurementSolverRestored\":")
                .append(activeMeasurementSolverRestored)
                .append(",\"solverRestoredWrapper\":").append(solverRestored)
                .append(",\"pendingBoardPowerState\":")
                .append(q(powerName(pendingPower)))
                .append(",\"boardPowerState\":").append(q(powerName(boardPower)))
                .append(",\"powerRestored\":").append(powerRestored)
                .append(",\"resistanceRefreshPending\":")
                .append(resistanceRefreshPending)
                .append(",\"cleanup\":").append(q(cleanupClosed ? "CLOSED" : "OPEN_BLOCKER"))
                .append(",\"disposition\":")
                .append(q(cleanupClosed ? "CLOSED" : "OPEN_BLOCKER"))
                .append('}');
            return result.toString();
        }
    }
}
