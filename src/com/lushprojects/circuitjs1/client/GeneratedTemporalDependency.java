package com.lushprojects.circuitjs1.client;

/**
 * Immutable, data-only dependency contract for a generated temporal behavior.
 *
 * <p>The contract describes the recipe and the healthy reference values that
 * a temporal proof consumes.  It deliberately contains no simulator, graph,
 * element, event, node-voltage, or array reference.</p>
 */
final class GeneratedTemporalDependency {
    static final int CURRENT_VERSION = 1;
    /**
     * The generated-board install boundary clears the graph, sets the solver
     * time and accumulated frame time to zero, creates an empty event queue,
     * and marks the fresh graph for analysis.  Temporal proofs may therefore
     * identify their initial state by this explicit owner contract instead of
     * retaining mutable solver readings in the dependency value.
     */
    static final String FRESH_GENERATED_OWNER_COLD_V1 =
        "owner=new-generated-board;graph=fresh;solver.t=0;solver.timeStepCount=0;" +
        "solver.timeStepAccum=0;eventQueue=empty;requiresAnalysis=true";

    private static final int MAX_TEXT_LENGTH = 256;
    private static final int MAX_CANONICAL_LENGTH = 16 * 1024;

    private final String behaviorId;
    private final int behaviorVersion;
    private final String initialStateContract;
    private final String outputEndpointId;
    private final String groundEndpointId;
    private final long nominalSupplyBits;
    private final long playerReselectBits;
    private final long naturalDischargeBits;
    private final long earlySampleBits;
    private final long lateSampleBits;
    private final long maxSolverAdvanceBits;
    private final long liveSolverAdvanceBits;
    private final long residualThresholdBits;
    private final long healthyRiseMinimumBits;
    private final long healthyLateMinimumBits;
    private final long healthyEarlyMaximumBits;
    private final long classificationRiseMinimumBits;
    private final long classificationLateMaximumBits;
    private final long classificationEarlyDifferenceBits;
    private final long classificationHealthyEarlyDifferenceBits;
    private final long classificationHealthyLateDifferenceBits;
    private final long healthyResidualBits;
    private final long healthyEarlyBits;
    private final long healthyLateBits;
    private final String canonical;
    private final int hash;

    GeneratedTemporalDependency(String behaviorId, int behaviorVersion,
            String initialStateContract, String outputEndpointId,
            String groundEndpointId, double nominalSupply,
            double playerReselectSeconds, double naturalDischargeSeconds,
            double earlySampleSeconds, double lateSampleSeconds,
            double maxSolverAdvanceSeconds, double liveSolverAdvanceSeconds,
            double residualThresholdVolts, double healthyRiseMinimumFraction,
            double healthyLateMinimumFraction, double healthyEarlyMaximumFraction,
            double classificationRiseMinimumFraction,
            double classificationLateMaximumFraction,
            double classificationEarlyDifferenceFraction,
            double classificationHealthyEarlyDifferenceFraction,
            double classificationHealthyLateDifferenceFraction,
            double healthyResidualVoltage, double healthyEarlyVoltage,
            double healthyLateVoltage) {
        this.behaviorId = text(behaviorId, "Missing temporal behavior ID");
        if (behaviorVersion <= 0)
            throw new IllegalArgumentException("Invalid temporal behavior version");
        this.behaviorVersion = behaviorVersion;
        this.initialStateContract = text(initialStateContract,
            "Missing temporal initial-state contract");
        this.outputEndpointId = text(outputEndpointId,
            "Missing temporal output endpoint ID");
        this.groundEndpointId = text(groundEndpointId,
            "Missing temporal ground endpoint ID");
        nominalSupplyBits = positiveBits(nominalSupply,
            "Invalid temporal nominal supply");
        playerReselectBits = positiveBits(playerReselectSeconds,
            "Invalid temporal player reselect duration");
        naturalDischargeBits = positiveBits(naturalDischargeSeconds,
            "Invalid temporal natural discharge duration");
        earlySampleBits = positiveBits(earlySampleSeconds,
            "Invalid temporal early sample duration");
        lateSampleBits = positiveBits(lateSampleSeconds,
            "Invalid temporal late sample duration");
        maxSolverAdvanceBits = positiveBits(maxSolverAdvanceSeconds,
            "Invalid temporal solver segment duration");
        liveSolverAdvanceBits = positiveBits(liveSolverAdvanceSeconds,
            "Invalid temporal live solver duration");
        residualThresholdBits = positiveBits(residualThresholdVolts,
            "Invalid temporal residual threshold");
        healthyRiseMinimumBits = positiveBits(healthyRiseMinimumFraction,
            "Invalid temporal healthy rise threshold");
        healthyLateMinimumBits = positiveBits(healthyLateMinimumFraction,
            "Invalid temporal healthy late threshold");
        healthyEarlyMaximumBits = positiveBits(healthyEarlyMaximumFraction,
            "Invalid temporal healthy early threshold");
        classificationRiseMinimumBits = positiveBits(classificationRiseMinimumFraction,
            "Invalid temporal classification rise threshold");
        classificationLateMaximumBits = positiveBits(classificationLateMaximumFraction,
            "Invalid temporal classification late threshold");
        classificationEarlyDifferenceBits = positiveBits(classificationEarlyDifferenceFraction,
            "Invalid temporal classification fast threshold");
        classificationHealthyEarlyDifferenceBits = positiveBits(
            classificationHealthyEarlyDifferenceFraction,
            "Invalid temporal classification early difference threshold");
        classificationHealthyLateDifferenceBits = positiveBits(
            classificationHealthyLateDifferenceFraction,
            "Invalid temporal classification late difference threshold");
        healthyResidualBits = bits(healthyResidualVoltage,
            "Invalid temporal healthy residual reference");
        healthyEarlyBits = bits(healthyEarlyVoltage,
            "Invalid temporal healthy early reference");
        healthyLateBits = bits(healthyLateVoltage,
            "Invalid temporal healthy late reference");

        StringBuilder value = new StringBuilder();
        frame(value, "generated-temporal-dependency-v1");
        appendField(value, "behavior.id", behaviorId);
        appendField(value, "behavior.version", Integer.toString(behaviorVersion));
        appendField(value, "initial-state.contract", initialStateContract);
        appendField(value, "endpoint.output", outputEndpointId);
        appendField(value, "endpoint.ground", groundEndpointId);
        appendField(value, "nominal-supply.bits", hex(nominalSupplyBits));
        appendField(value, "time.player-reselect.bits", hex(playerReselectBits));
        appendField(value, "time.natural-discharge.bits", hex(naturalDischargeBits));
        appendField(value, "time.early-sample.bits", hex(earlySampleBits));
        appendField(value, "time.late-sample.bits", hex(lateSampleBits));
        appendField(value, "time.max-solver-advance.bits", hex(maxSolverAdvanceBits));
        appendField(value, "time.live-solver-advance.bits", hex(liveSolverAdvanceBits));
        appendField(value, "threshold.residual.bits", hex(residualThresholdBits));
        appendField(value, "threshold.healthy-rise-min.bits", hex(healthyRiseMinimumBits));
        appendField(value, "threshold.healthy-late-min.bits", hex(healthyLateMinimumBits));
        appendField(value, "threshold.healthy-early-max.bits", hex(healthyEarlyMaximumBits));
        appendField(value, "threshold.classification-rise-min.bits",
            hex(classificationRiseMinimumBits));
        appendField(value, "threshold.classification-late-max.bits",
            hex(classificationLateMaximumBits));
        appendField(value, "threshold.classification-early-difference.bits",
            hex(classificationEarlyDifferenceBits));
        appendField(value, "threshold.classification-healthy-early-difference.bits",
            hex(classificationHealthyEarlyDifferenceBits));
        appendField(value, "threshold.classification-healthy-late-difference.bits",
            hex(classificationHealthyLateDifferenceBits));
        appendField(value, "reference.healthy-residual.bits", hex(healthyResidualBits));
        appendField(value, "reference.healthy-early.bits", hex(healthyEarlyBits));
        appendField(value, "reference.healthy-late.bits", hex(healthyLateBits));
        if (value.length() > MAX_CANONICAL_LENGTH)
            throw new IllegalArgumentException("Temporal dependency exceeds its bound");
        canonical = value.toString();
        hash = canonical.hashCode();
    }

    String getBehaviorId() { return behaviorId; }
    int getBehaviorVersion() { return behaviorVersion; }
    String getInitialStateContract() { return initialStateContract; }
    String getOutputEndpointId() { return outputEndpointId; }
    String getGroundEndpointId() { return groundEndpointId; }

    String canonical() { return canonical; }

    @Override
    public boolean equals(Object other) {
        return this == other || (other instanceof GeneratedTemporalDependency &&
            canonical.equals(((GeneratedTemporalDependency) other).canonical));
    }

    @Override
    public int hashCode() { return hash; }

    private static String text(String value, String message) {
        if (value == null || value.length() == 0 || value.length() > MAX_TEXT_LENGTH)
            throw new IllegalArgumentException(message);
        return value;
    }

    private static long bits(double value, String message) {
        if (Double.isNaN(value) || Double.isInfinite(value))
            throw new IllegalArgumentException(message);
        return Double.doubleToLongBits(value);
    }

    private static long positiveBits(double value, String message) {
        if (value <= 0 || Double.isNaN(value) || Double.isInfinite(value))
            throw new IllegalArgumentException(message);
        return Double.doubleToLongBits(value);
    }

    private static String hex(long value) { return Long.toHexString(value); }

    private static void appendField(StringBuilder out, String key, String value) {
        out.append('F');
        frame(out, key);
        frame(out, value);
        if (out.length() > MAX_CANONICAL_LENGTH)
            throw new IllegalArgumentException("Temporal dependency exceeds its bound");
    }

    private static void frame(StringBuilder out, String value) {
        if (value == null) out.append("-1:");
        else out.append(value.length()).append(':').append(value);
    }
}
