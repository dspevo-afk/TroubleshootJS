package com.lushprojects.circuitjs1.client;

import java.util.Vector;

/**
 * An immutable, answer-blind observation sequence. Providers declare this data
 * before hypothesis enumeration. There are no callbacks, branches, fault IDs,
 * solver handles or runtime objects in a program.
 */
final class GeneratedDiagnosticProgram {
    static final int VERSION = 1;
    static final int MAX_STEPS = 4096;
    static final double MAX_TOTAL_WAIT_SECONDS = 10.0;
    enum Kind { INPUT, POWER, PROFILE_POWER, WAIT, SETTLE,
        DC_VOLTAGE, RESISTANCE, CONTINUITY, DIODE }

    static final class Step {
        final Kind kind;
        final String id;
        final String red;
        final String black;
        final BoardPowerState power;
        final double seconds;
        private Step(Kind kind, String id, String red, String black,
                BoardPowerState power, double seconds) {
            this.kind = kind; this.id = id; this.red = red; this.black = black;
            this.power = power; this.seconds = seconds;
        }
    }

    private final Vector<Step> steps;
    private final String planIdentity;
    private final String canonical;

    private GeneratedDiagnosticProgram(GeneratedDiagnosticPlan plan, Vector<Step> values) {
        GeneratedDiagnosticSolvabilityAdmission.validatePlan(plan);
        if (values == null || values.isEmpty() || values.size() > MAX_STEPS)
            throw new IllegalArgumentException("Diagnostic program is empty or exceeds its step bound");
        steps = new Vector<Step>(values);
        planIdentity = describePlan(plan);
        Vector<String> samples = new Vector<String>();
        double totalWait = 0;
        StringBuilder identity = new StringBuilder("diagnostic-program-v1");
        frame(identity, planIdentity);
        for (Step step : steps) {
            if (step == null || step.kind == null)
                throw new IllegalArgumentException("Missing diagnostic step");
            switch (step.kind) {
            case INPUT:
                requireContains(plan.getInputPowerTransitions(), step.id, "input transition");
                requireContains(plan.getPlayerOperationIds(), step.id, "player operation");
                break;
            case POWER:
            case PROFILE_POWER:
                requireContains(plan.getInputPowerTransitions(), step.id, "power transition");
                if (step.power == null)
                    throw new IllegalArgumentException("Diagnostic power step has no state");
                break;
            case WAIT:
                requireContains(plan.getTemporalWaitSampleIds(), step.id, "temporal sample");
                if (Double.isNaN(step.seconds) || Double.isInfinite(step.seconds) ||
                        step.seconds < 0 || step.seconds > 1.0)
                    throw new IllegalArgumentException("Invalid diagnostic wait duration");
                totalWait += step.seconds;
                if (totalWait > MAX_TOTAL_WAIT_SECONDS)
                    throw new IllegalArgumentException("Diagnostic program exceeds its time bound");
                break;
            case SETTLE:
                break;
            default:
                requireContains(plan.getMeterModeIds(), step.kind.name(), "instrument");
                requireProbe(plan, step.red); requireProbe(plan, step.black);
                publicId(step.id);
                if (step.kind == Kind.DIODE) {
                    addSampleId(samples, step.id + "_VOLTAGE");
                    addSampleId(samples, step.id + "_CURRENT");
                } else addSampleId(samples, step.id);
                break;
            }
            frame(identity, step.kind.name()); frame(identity, step.id);
            frame(identity, step.red); frame(identity, step.black);
            frame(identity, step.power == null ? null : step.power.name());
            frame(identity, Long.toHexString(Double.doubleToLongBits(step.seconds)));
        }
        if (samples.isEmpty())
            throw new IllegalArgumentException("Diagnostic program contains no observations");
        canonical = identity.toString();
    }

    Vector<Step> getSteps() { return new Vector<Step>(steps); }
    String canonical() { return canonical; }
    void validatePlan(GeneratedDiagnosticPlan plan) {
        if (!planIdentity.equals(describePlan(plan)))
            throw new IllegalArgumentException("Diagnostic observation program changed its declared plan");
    }

    static Builder builder(GeneratedDiagnosticPlan plan) { return new Builder(plan); }

    static final class Builder {
        private final GeneratedDiagnosticPlan plan;
        private final Vector<Step> steps = new Vector<Step>();
        private Builder(GeneratedDiagnosticPlan plan) {
            if (plan == null) throw new IllegalArgumentException("Missing diagnostic plan");
            this.plan = plan;
        }
        Builder input(String id) { return add(Kind.INPUT, id, null, null, null, 0); }
        Builder power(String id, BoardPowerState state) {
            return add(Kind.POWER, id, null, null, state, 0);
        }
        Builder profilePower(String id, BoardPowerState state) {
            return add(Kind.PROFILE_POWER, id, null, null, state, 0);
        }
        Builder waitSample(String id, double seconds) {
            return add(Kind.WAIT, id, null, null, null, seconds);
        }
        Builder settle() { return add(Kind.SETTLE, null, null, null, null, 0); }
        Builder measure(Kind kind, String id, String red, String black) {
            if (kind != Kind.DC_VOLTAGE && kind != Kind.RESISTANCE &&
                    kind != Kind.CONTINUITY && kind != Kind.DIODE)
                throw new IllegalArgumentException("Not a diagnostic instrument");
            return add(kind, id, red, black, null, 0);
        }
        private Builder add(Kind kind, String id, String red, String black,
                BoardPowerState state, double seconds) {
            if (steps.size() >= MAX_STEPS)
                throw new IllegalArgumentException("Diagnostic program exceeds its step bound");
            steps.add(new Step(kind, id, red, black, state, seconds));
            return this;
        }
        GeneratedDiagnosticProgram build() { return new GeneratedDiagnosticProgram(plan, steps); }
    }

    static String describePlan(GeneratedDiagnosticPlan plan) {
        if (plan == null) throw new IllegalArgumentException("Missing diagnostic plan");
        StringBuilder result = new StringBuilder();
        frame(result, plan.getTemplateId()); frame(result, plan.getReferenceTargetId());
        frames(result, plan.getProbeTargetIds()); frames(result, plan.getMeterModeIds());
        frames(result, plan.getInputPowerTransitions()); frames(result, plan.getIsolationActionIds());
        frames(result, plan.getRepairActionIds()); frames(result, plan.getWorkflowActionIds());
        frames(result, plan.getPlayerOperationIds()); frames(result, plan.getTemporalWaitSampleIds());
        frames(result, plan.getRailDomainIds()); frame(result, Integer.toString(plan.getDepth()));
        frame(result, Boolean.toString(plan.hasParallelPathAmbiguity()));
        frame(result, Boolean.toString(plan.hasUnaffectedFunctionRetestObservation()));
        frame(result, plan.getEquivalentRepairClass());
        return result.toString();
    }
    private static void requireProbe(GeneratedDiagnosticPlan plan, String id) {
        publicId(id);
        if (!plan.getReferenceTargetId().equals(id) && !plan.getProbeTargetIds().contains(id))
            throw new IllegalArgumentException("Diagnostic program uses an undeclared probe: " + id);
    }
    private static void requireContains(Vector<String> allowed, String id, String kind) {
        publicId(id);
        if (!allowed.contains(id))
            throw new IllegalArgumentException("Diagnostic program uses an undeclared " + kind + ": " + id);
    }
    private static void publicId(String id) {
        if (id == null || id.length() == 0)
            throw new IllegalArgumentException("Missing public diagnostic ID");
        String upper = id.toUpperCase();
        for (String hidden : new String[] { "DEVELOPER", "PRIVATE", "SOLVER", "ANSWER",
                "HINT", "CANDIDATE", "FAULT", "NODE_NUMBER", "COORD", "INDEX", "UUID" })
            if (upper.indexOf(hidden) >= 0)
                throw new IllegalArgumentException("Hidden-answer diagnostic program ID: " + id);
    }
    private static void addSampleId(Vector<String> samples, String id) {
        if (samples.contains(id)) throw new IllegalArgumentException("Duplicate diagnostic sample: " + id);
        // The value object supplies the shared sample-name syntax, not fabricated evidence.
        new GeneratedDiagnosticSample(id, 0, 0);
        samples.add(id);
    }
    private static void frame(StringBuilder out, String value) {
        if (value == null) out.append("-1:");
        else out.append(value.length()).append(':').append(value);
    }
    private static void frames(StringBuilder out, Vector<String> values) {
        out.append(values.size()).append('[');
        for (String value : values) frame(out, value);
        out.append(']');
    }
}
