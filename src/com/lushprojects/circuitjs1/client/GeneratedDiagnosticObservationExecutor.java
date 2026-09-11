package com.lushprojects.circuitjs1.client;

import java.util.Vector;

/** Executes data-only provider observations through the real player/solver seams. */
final class GeneratedDiagnosticObservationExecutor {
    private GeneratedDiagnosticObservationExecutor() { }

    static Vector<GeneratedDiagnosticSample> collect(CirSim sim, GeneratedBoardInstance instance,
            GeneratedDiagnosticProgram program, GeneratedDiagnosticExecutionTrace.Builder trace) {
        require(sim != null && instance != null && sim.getGeneratedBoardInstance() == instance &&
                program != null && trace != null && instance.getDiagnosticProvider() != null,
            "Missing current production diagnostic execution context");
        program.validatePlan(instance.getDiagnosticProvider().getDiagnosticPlan());
        validateAvailability(instance, program);
        for (GeneratedDiagnosticProgram.Step step : program.getSteps())
            if (step.red != null) {
                boardProbe(sim, instance, step.red); boardProbe(sim, instance, step.black);
            }
        Vector<GeneratedDiagnosticSample> samples = new Vector<GeneratedDiagnosticSample>();
        try {
            for (GeneratedDiagnosticProgram.Step step : program.getSteps()) {
                switch (step.kind) {
                case INPUT:
                    sim.instrumentController.exitInstrumentModeForDeveloperVerification();
                    require(sim.getGeneratedChallengeController().invokePlayerOperation(step.id),
                        "Unavailable diagnostic player input: " + step.id);
                    GeneratedRuntimeDeveloperSettlement.settle(sim, instance, "diagnostic-input-" + step.id);
                    trace.recordInputPowerTransition(step.id);
                    break;
                case POWER:
                case PROFILE_POWER:
                    sim.instrumentController.exitInstrumentModeForDeveloperVerification();
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
                    sim.instrumentController.exitInstrumentModeForDeveloperVerification();
                    if (step.seconds > 0) sim.advanceGeneratedTemporalProfile(step.seconds);
                    trace.recordTemporalWaitSample(step.id, step.seconds);
                    break;
                case SETTLE:
                    GeneratedRuntimeDeveloperSettlement.settle(sim, instance, "diagnostic-observation-settle");
                    break;
                case DC_VOLTAGE:
                    addSample(samples, step.id, measureDc(sim, instance, step.red, step.black, trace));
                    break;
                default:
                    ProbeTarget red = boardProbe(sim, instance, step.red);
                    ProbeTarget black = boardProbe(sim, instance, step.black);
                    require(sim.instrumentController.getActiveMeasurementReadinessForStrategy(red, black).isReady(),
                        "Unavailable active diagnostic measurement: " + step.id);
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
                require(sim.getGeneratedBoardInstance() == instance && !sim.activeMeasurementOverlay,
                    "Diagnostic step lost its board or left a measurement overlay");
            }
            require(!samples.isEmpty(), "Production diagnostic observations are empty");
            return samples;
        } finally {
            sim.instrumentController.exitInstrumentModeForDeveloperVerification();
            require(!sim.activeMeasurementOverlay, "Production diagnostic measurement cleanup failed");
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

    private static double measureDc(CirSim sim, GeneratedBoardInstance instance,
            String redId, String blackId, GeneratedDiagnosticExecutionTrace.Builder trace) {
        ProbeTarget red = boardProbe(sim, instance, redId);
        ProbeTarget black = boardProbe(sim, instance, blackId);
        require(sim.isChallengeInteractionEnabled(),
            "Production diagnostic DC sample requires settled player interaction: " + redId);
        sim.instrumentController.setDcVoltageProbesForDeveloperVerification(red, black);
        trace.recordMeterMode("DC_VOLTAGE");
        double reading = sim.instrumentController.getLatestDcVoltageForDeveloperVerification();
        require(!Double.isNaN(reading) && !Double.isInfinite(reading),
            "Production diagnostic solver returned a non-finite DC sample: " + redId);
        return reading;
    }

    private static ProbeTarget boardProbe(CirSim sim, GeneratedBoardInstance instance,
            String padId) {
        PcbWorkbenchRenderer renderer = sim.pcbWorkbenchController.getRenderer();
        require(instance.getBoard().getPad(padId) != null && renderer.hasPad(padId),
            "Production diagnostic route lacks rendered probe target: " + padId);
        BoardPadProbeTarget target = new BoardPadProbeTarget(sim, instance, padId, renderer);
        require(target.isValid(), "Production diagnostic probe target is not valid: " + padId);
        return target;
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
