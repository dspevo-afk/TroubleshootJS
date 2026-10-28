package com.lushprojects.circuitjs1.client;

/** Owns installation, solver sampling, and fail-closed cleanup for temporary meter elements. */
final class TemporaryMeasurementTransaction {
    private TemporaryMeasurementTransaction() { }

    static double run(CirSim sim, ActiveMeasurementStimulus stimulus,
            ActiveMeasurementResultReader reader, boolean activeStimulus) {
        if (sim.activeMeasurementOverlay)
            throw new IllegalStateException("A temporary measurement is already active or awaiting cleanup");
        if (!sim.isGeneratedRuntimeSettled())
            throw new IllegalStateException("A temporary measurement requires a settled runtime");
        sim.lastActiveMeasurementStimulus = stimulus;
        final GeneratedBoardInstance measurementOwner = sim.generatedBoardInstance;
        sim.activeMeasurementSolverRestored = false;
        sim.activeMeasurementOverlay = true;
        double result = Double.NaN;
        Throwable failure = null;
        try {
            stimulus.install(sim);
            if (sim.requestPowerOnDuringActiveMeasurementForDeveloperVerification) {
                sim.requestPowerOnDuringActiveMeasurementForDeveloperVerification = false;
                sim.setBoardPowerState(BoardPowerState.POWERED);
            }
            sim.analyzeCircuit();
            TemporaryMeasurementSettlement.acquire(sim, activeStimulus);
            if (activeStimulus && measurementOwner != null)
                measurementOwner.getPhysicalBoardRuntime().settleActiveMeasurement(sim, measurementOwner, true);
            injectFailure(sim, CirSim.Task43PMeasurementFailureStage.READER);
            result = reader.readResult();
        } catch (Throwable measurementFailure) {
            failure = measurementFailure;
        }
        try {
            stimulus.remove(sim);
            injectFailure(sim, CirSim.Task43PMeasurementFailureStage.AFTER_STIMULUS_REMOVE);
        } catch (Throwable removalFailure) {
            failure = retainFailure(failure, removalFailure);
        }
        /* Removal and solver reconstruction are separate cleanup stages. An
         * exception after removal must not strand the old solver indexes. Never
         * analyze or energize a graph that still contains a meter element. */
        boolean restored = false;
        try {
            if (!isStimulusAbsentFromElementList(sim, stimulus))
                throw new IllegalStateException("Temporary measurement elements remain in the board graph");
            sim.analyzeCircuit();
            TemporaryMeasurementSettlement.restore(sim);
            if (activeStimulus && measurementOwner != null)
                measurementOwner.getPhysicalBoardRuntime().settleActiveMeasurement(sim, measurementOwner, false);
            restored = sim.stopMessage == null && isStimulusAbsentFromSolver(sim, stimulus);
            if (!restored)
                throw new IllegalStateException("Temporary measurement solver restoration failed: " + sim.stopMessage);
        } catch (Throwable restorationFailure) {
            failure = retainFailure(failure, restorationFailure);
        }
        sim.activeMeasurementSolverRestored = restored;
        if (restored) {
            sim.activeMeasurementOverlay = false;
            if (sim.pendingBoardPowerState != null) {
                BoardPowerState requestedState = sim.pendingBoardPowerState;
                sim.pendingBoardPowerState = null;
                try {
                    // This request was accepted while this synchronous measurement
                    // owned the overlay. Cleanup has proven all temporary elements
                    // absent; pending analysis must not silently drop the request.
                    sim.restoreQueuedMeasurementPowerState(requestedState, stimulus);
                } catch (Throwable powerFailure) {
                    restored = false;
                    if (sim.pendingBoardPowerState == null)
                        sim.pendingBoardPowerState = requestedState;
                    failure = retainFailure(failure, powerFailure);
                }
            }
            /* With no persistent power/board change, leave any existing validation
             * pending. An ordinary measurement must not manufacture a retest. */
        }
        if (!restored) {
            sim.activeMeasurementSolverRestored = false;
            sim.activeMeasurementOverlay = true;
            /* Bypass the queuing UI seam only to disconnect the actual external
             * inputs. Keep the latest request pending, lock out further measurement,
             * and stop the solver until the board is reloaded. */
            try {
                if (sim.generatedBoardInstance != null) {
                    sim.boardPowerController.setState(BoardPowerState.UNPOWERED);
                    sim.generatedBoardInstance.getPhysicalBoardRuntime()
                            .onBoardPowerStateChanged(BoardPowerState.UNPOWERED);
                }
            } catch (Throwable isolationFailure) {
                failure = retainFailure(failure, isolationFailure);
            }
            try {
                sim.stopAfterTemporaryMeasurementCleanupFailure();
            } catch (Throwable stopFailure) {
                failure = retainFailure(failure, stopFailure);
            }
        }
        try {
            if (sim.generatedBoardInstance != null)
                sim.generatedBoardInstance.getPhysicalBoardRuntime().synchronizeSimulationTime(sim.t);
        } catch (Throwable synchronizationFailure) {
            failure = retainFailure(failure, synchronizationFailure);
        }
        if (failure instanceof RuntimeException)
            throw (RuntimeException) failure;
        if (failure instanceof Error)
            throw (Error) failure;
        if (failure != null)
            throw new IllegalStateException("Temporary measurement failed", failure);
        return result;
    }

    private static Throwable retainFailure(Throwable original, Throwable next) {
        if (original == null)
            return next;
        if (original != next)
            original.addSuppressed(next);
        return original;
    }

    private static void injectFailure(CirSim sim, CirSim.Task43PMeasurementFailureStage stage) {
        if (!sim.troubleshootTask43PVerification || !sim.developerVerifierRunning ||
                sim.task43PMeasurementFailureStage != stage)
            return;
        sim.task43PMeasurementFailureStage = CirSim.Task43PMeasurementFailureStage.NONE;
        sim.task43PMeasurementFailureLastInjectedStage = stage;
        sim.task43PMeasurementFailureInjectionCount++;
        throw new CirSim.Task43PInjectedMeasurementFailure(stage);
    }

    private static boolean isStimulusAbsentFromElementList(CirSim sim, ActiveMeasurementStimulus stimulus) {
        CircuitElm elements[] = stimulus.getTemporaryElements();
        if (elements == null || sim.elmList == null)
            return false;
        for (CircuitElm element : elements)
            if (element == null || sim.elmList.contains(element))
                return false;
        return true;
    }

    static boolean isStimulusAbsentFromSolver(CirSim sim, ActiveMeasurementStimulus stimulus) {
        if (!isStimulusAbsentFromElementList(sim, stimulus) || sim.voltageSources == null || sim.nodeList == null)
            return false;
        CircuitElm elements[] = stimulus.getTemporaryElements();
        for (CircuitElm voltageSource : sim.voltageSources) {
            for (CircuitElm element : elements)
                if (voltageSource == element)
                    return false;
        }
        for (CircuitNode node : sim.nodeList) {
            for (CircuitNodeLink link : node.links) {
                for (CircuitElm element : elements)
                    if (link.elm == element)
                        return false;
            }
        }
        return sim.circuitMatrix != null;
    }
}
