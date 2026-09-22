package com.lushprojects.circuitjs1.client;

import java.util.HashMap;
import java.util.Map;
import java.util.Vector;

/** Executes data-only provider observations through the real player/solver seams. */
final class GeneratedDiagnosticObservationExecutor {
    private GeneratedDiagnosticObservationExecutor() { }

    /**
     * Creates a resumable observation cursor. Construction performs only the
     * detached program and rendered-probe checks; Cursor.step() executes one
     * real player-visible program operation.
     */
    static Cursor begin(CirSim sim, GeneratedBoardInstance instance,
            GeneratedDiagnosticProgram program, GeneratedDiagnosticExecutionTrace.Builder trace) {
        return new Cursor(sim, instance, program, trace);
    }

    /**
     * Serial reference path. It intentionally uses the same cursor and
     * per-step implementation as the bounded proof session.
     */
    static Vector<GeneratedDiagnosticSample> collect(CirSim sim, GeneratedBoardInstance instance,
            GeneratedDiagnosticProgram program, GeneratedDiagnosticExecutionTrace.Builder trace) {
        Cursor cursor = begin(sim, instance, program, trace);
        try {
            while (cursor.step()) { }
            return cursor.finish();
        } catch (Throwable failure) {
            try {
                cursor.cancel();
            } catch (Throwable cleanup) {
                failure = retain(failure, cleanup);
            }
            throwFailure(failure);
            return new Vector<GeneratedDiagnosticSample>();
        }
    }

    /** One bounded, resumable diagnostic program execution. */
    static final class Cursor {
        private final CirSim sim;
        private final GeneratedBoardInstance instance;
        private final GeneratedChallengeController controller;
        private final Vector<CircuitElm> graph;
        private final PcbWorkbenchRenderer renderer;
        private final Vector<GeneratedDiagnosticProgram.Step> steps;
        private final GeneratedDiagnosticExecutionTrace.Builder trace;
        private final Map<String, BoardPadProbeTarget> probeTargets =
            new HashMap<String, BoardPadProbeTarget>();
        private final Vector<GeneratedDiagnosticSample> samples =
            new Vector<GeneratedDiagnosticSample>();
        private int nextStep;
        private boolean complete;
        private boolean closed;
        private boolean cancelling;

        private Cursor(CirSim sim, GeneratedBoardInstance instance,
                GeneratedDiagnosticProgram program, GeneratedDiagnosticExecutionTrace.Builder trace) {
            require(sim != null && instance != null && sim.getGeneratedBoardInstance() == instance &&
                program != null && trace != null && instance.getDiagnosticProvider() != null,
                "Missing current production diagnostic execution context");
            program.validatePlan(instance.getDiagnosticProvider().getDiagnosticPlan());
            validateAvailability(instance, program);
            this.sim = sim;
            this.instance = instance;
            this.controller = sim.getGeneratedChallengeController();
            this.graph = sim.elmList;
            this.renderer = sim.pcbWorkbenchController.getRenderer();
            this.trace = trace;
            steps = program.getSteps();
            for (GeneratedDiagnosticProgram.Step step : steps)
                if (step.red != null) {
                    boardProbe(step.red);
                    boardProbe(step.black);
                }
        }

        /** Executes exactly one canonical program step. */
        boolean step() {
            ensureOpen();
            if (complete) return false;
            requireCurrentContext("observation-step");
            Throwable failure = null;
            try {
                executeStep(steps.get(nextStep));
                require(sim.getGeneratedBoardInstance() == instance &&
                    sim.getGeneratedChallengeController() == controller &&
                    !sim.activeMeasurementOverlay,
                    "Diagnostic step lost its board or left a measurement overlay");
            } catch (Throwable problem) {
                failure = problem;
            }
            if (isCurrentContext()) {
                try {
                    if (failure != null || !keepsDcModeForNextStep())
                        closeInstrumentMode();
                } catch (Throwable cleanup) {
                    failure = retain(failure, cleanup);
                }
            } else {
                failure = retain(failure, new IllegalStateException(
                    "Diagnostic observation refused cleanup over a successor owner"));
            }
            if (failure != null) {
                throwFailure(failure);
                return false;
            }
            if (steps.get(nextStep).kind != GeneratedDiagnosticProgram.Kind.SETTLE)
                trace.recordCompletedSemanticAction();
            nextStep++;
            if (nextStep >= steps.size()) {
                require(!samples.isEmpty(), "Production diagnostic observations are empty");
                complete = true;
            }
            return !complete;
        }

        boolean isComplete() { return complete; }

        /** Returns the observations only after the entire program completed. */
        Vector<GeneratedDiagnosticSample> finish() {
            ensureOpen();
            require(complete, "Diagnostic observation cursor is incomplete");
            Throwable failure = null;
            try {
                requireCurrentContext("observation-finish");
                closeInstrumentMode();
                require(!samples.isEmpty(), "Production diagnostic observations are empty");
            } catch (Throwable problem) {
                failure = problem;
            }
            if (failure != null) {
                throwFailure(failure);
                return new Vector<GeneratedDiagnosticSample>();
            }
            closed = true;
            return new Vector<GeneratedDiagnosticSample>(samples);
        }

        /** Cleans a partial observation without publishing any samples. */
        void cancel() {
            if (closed) return;
            cancelling = true;
            requireCurrentContext("observation-cancel");
            closeInstrumentMode();
            // Failed cleanup retains this exact cursor for a guarded retry.
            closed = true;
        }

        private void ensureOpen() {
            if (closed || cancelling)
                throw new IllegalStateException("Diagnostic observation cursor is closed");
        }

        private void requireCurrentContext(String boundary) {
            require(isCurrentContext(),
                "Diagnostic observation lost its exact candidate at " + boundary);
        }

        private boolean isCurrentContext() {
            return sim.getGeneratedBoardInstance() == instance &&
                sim.getGeneratedChallengeController() == controller && sim.elmList == graph;
        }

        /** Keep one ordinary DC mode selected across consecutive real probe moves. */
        private boolean keepsDcModeForNextStep() {
            return nextStep + 1 < steps.size() &&
                steps.get(nextStep).kind == GeneratedDiagnosticProgram.Kind.DC_VOLTAGE &&
                steps.get(nextStep + 1).kind == GeneratedDiagnosticProgram.Kind.DC_VOLTAGE;
        }

        private void closeInstrumentMode() {
            Throwable failure = null;
            try {
                sim.instrumentController.exitInstrumentModeIfActiveForDeveloperVerification();
            } catch (Throwable cleanup) {
                failure = cleanup;
            }
            try {
                require(!sim.activeMeasurementOverlay,
                    "Production diagnostic measurement cleanup failed");
            } catch (Throwable cleanup) {
                failure = retain(failure, cleanup);
            }
            if (failure != null) throwFailure(failure);
        }

        private void executeStep(GeneratedDiagnosticProgram.Step step) {
        switch (step.kind) {
        case INPUT:
            sim.instrumentController.exitInstrumentModeIfActiveForDeveloperVerification();
            require(sim.getGeneratedChallengeController().invokePlayerOperation(step.id),
                "Unavailable diagnostic player input: " + step.id);
            GeneratedRuntimeDeveloperSettlement.settle(sim, instance, "diagnostic-input-" + step.id);
            trace.recordInputPowerTransition(step.id);
            break;
        case POWER:
        case PROFILE_POWER:
            sim.instrumentController.exitInstrumentModeIfActiveForDeveloperVerification();
            if (step.kind == GeneratedDiagnosticProgram.Kind.PROFILE_POWER)
                sim.setBoardPowerStateForGeneratedTemporalProfile(step.power);
            else {
                sim.setBoardPowerState(step.power);
                sim.updateCircuit();
            }
            require(sim.getBoardPowerController().getState() == step.power,
                "Diagnostic power transition was unavailable: " + step.id);
            trace.recordInputPowerTransition(step.id);
            break;
        case WAIT:
            sim.instrumentController.exitInstrumentModeIfActiveForDeveloperVerification();
            if (step.seconds > 0) sim.advanceGeneratedTemporalProfile(step.seconds);
            trace.recordTemporalWaitSample(step.id, step.seconds);
            break;
        case SETTLE:
            GeneratedRuntimeDeveloperSettlement.settle(sim, instance, "diagnostic-observation-settle");
            break;
        case DC_VOLTAGE:
            addSample(samples, step.id, measureDc(step.red, step.black));
            break;
        default:
            ProbeTarget red = boardProbe(step.red);
            ProbeTarget black = boardProbe(step.black);
            ActiveMeasurementReadiness readiness = sim.instrumentController.getActiveMeasurementReadinessForStrategy(red, black);
            require(readiness.isReady(),
                "Unavailable active diagnostic measurement: " + step.id + ":" + readiness);
            if (step.kind == GeneratedDiagnosticProgram.Kind.RESISTANCE) {
                int count = sim.instrumentController.getResistanceMeasurementCountForDeveloperVerification();
                sim.instrumentController.setResistanceProbesForDeveloperVerification(red, black);
                require(sim.instrumentController.getResistanceMeasurementCountForDeveloperVerification() == count + 1,
                    "Diagnostic resistance measurement did not execute: " + step.id);
                addResistanceSample(sim, samples, step.id,
                    sim.instrumentController.getLatestResistanceReadingForDeveloperVerification(), trace);
            } else if (step.kind == GeneratedDiagnosticProgram.Kind.CONTINUITY) {
                sim.instrumentController.setContinuityProbesForDeveloperVerification(red, black);
                trace.recordMeterMode("CONTINUITY");
                addSample(samples, step.id,
                    sim.instrumentController.isContinuityDetectedForDeveloperVerification() ? 1 : 0);
            } else if (step.kind == GeneratedDiagnosticProgram.Kind.DIODE) {
                sim.instrumentController.setDiodeProbesForDeveloperVerification(red, black);
                trace.recordMeterMode("DIODE");
                addSample(samples, step.id + "_VOLTAGE", sim.getLastDiodeMeasurementVoltageForDeveloperVerification());
                addSample(samples, step.id + "_CURRENT", sim.getLastDiodeMeasurementCurrentForDeveloperVerification());
            } else throw new IllegalArgumentException("Unsupported diagnostic step");
            break;
        }
    }

        private double measureDc(String redId, String blackId) {
            ProbeTarget red = boardProbe(redId);
            ProbeTarget black = boardProbe(blackId);
            require(sim.isChallengeInteractionEnabled(),
                "Production diagnostic DC sample requires settled player interaction: " + redId);
            sim.instrumentController.setDcVoltageProbesForDeveloperVerification(red, black);
            trace.recordMeterMode("DC_VOLTAGE");
            double reading = sim.instrumentController.getLatestDcVoltageForDeveloperVerification();
            require(!Double.isNaN(reading) && !Double.isInfinite(reading),
                "Production diagnostic solver returned a non-finite DC sample: " + redId);
            return reading;
        }

        /**
         * Each cursor is bound to one private owner, graph and renderer. Cache
         * only the physical probe wrapper; validate its live pad/endpoint at
         * every use so a mutation or rerender cannot become stale evidence.
         */
        private ProbeTarget boardProbe(String padId) {
            require(sim.pcbWorkbenchController.getRenderer() == renderer,
                "Production diagnostic route lost its exact rendered board");
            require(instance.getBoard().getPad(padId) != null && renderer.hasPad(padId),
                "Production diagnostic route lacks rendered probe target: " + padId);
            BoardPadProbeTarget target = probeTargets.get(padId);
            if (target == null) {
                target = new BoardPadProbeTarget(sim, instance, padId, renderer);
                probeTargets.put(padId, target);
            }
            require(target.isValid(), "Production diagnostic probe target is not valid: " + padId);
            return target;
        }
    }

    /** Detached preflight of the real owner catalog and semantic bindings. */
    static void validateAvailability(GeneratedBoardInstance instance, GeneratedDiagnosticProgram program) {
        require(instance != null && program != null, "Missing diagnostic availability context");
        for (GeneratedDiagnosticProgram.Step step : program.getSteps()) {
            if (step.kind == GeneratedDiagnosticProgram.Kind.INPUT)
                require(instance.getOperationCatalog().find(step.id) != null,
                    "Unavailable diagnostic input: " + step.id);
            if (step.red != null)
                require(instance.getBoard().getPad(step.red) != null &&
                    instance.getBoard().getPad(step.black) != null &&
                    instance.getSimulationBindings().getEndpoint(step.red) != null &&
                    instance.getSimulationBindings().getEndpoint(step.black) != null,
                    "Unavailable diagnostic probe: " + step.id);
        }
    }

    private static void addSample(Vector<GeneratedDiagnosticSample> samples, String sampleId,
            double value) {
        require(!Double.isNaN(value) && !Double.isInfinite(value),
            "Production diagnostic solver returned a non-finite sample: " + sampleId);
        samples.add(new GeneratedDiagnosticSample(sampleId, value,
            Math.max(.01, Math.abs(value) * .02)));
    }

    /** Only successful player-visible resistance outcomes may distinguish hypotheses. */
    private static void addResistanceSample(CirSim sim,
            Vector<GeneratedDiagnosticSample> samples, String sampleId, double reading,
            GeneratedDiagnosticExecutionTrace.Builder trace) {
        trace.recordMeterMode("RESISTANCE");
        GeneratedDiagnosticSample sample = resistanceObservation(sampleId, reading);
        String expectedDisplay = sample.isOverRange() ? "OL" : CircuitElm.getUnitText(reading, "Ohm");
        require(expectedDisplay.equals(sim.instrumentController.getReadingForDeveloperVerification()),
            "Diagnostic resistance was not a completed player-visible measurement: " + sampleId);
        samples.add(sample);
    }

    /** Pure encoding of a completed reading; NaN/failure is not an open circuit. */
    static GeneratedDiagnosticSample resistanceObservation(String sampleId, double reading) {
        require(!Double.isNaN(reading) && reading >= 0,
            "Invalid diagnostic resistance measurement: " + sampleId);
        // Positive infinity is the real meter's open-circuit result. A finite
        // reading above its range is visually identical, not a secret number.
        if (reading > ResistanceInstrumentMode.MAX_RESISTANCE)
            return GeneratedDiagnosticSample.overRange(sampleId);
        return new GeneratedDiagnosticSample(sampleId, reading,
            Math.max(.01, Math.abs(reading) * .02));
    }

    private static void throwFailure(Throwable failure) {
        if (failure instanceof RuntimeException) throw (RuntimeException)failure;
        if (failure instanceof Error) throw (Error)failure;
        throw new IllegalStateException("Production diagnostic observation failed", failure);
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
