package com.lushprojects.circuitjs1.client;

/** Immutable player-observable result. Over-range has no hidden numeric payload. */
final class GeneratedDiagnosticSample {
    enum Outcome { NUMERIC, OVER_RANGE }
    private final Outcome outcome;
    private final String sampleId;
    private final double value;
    private final double comparisonTolerance;

    GeneratedDiagnosticSample(String sampleId, double value, double comparisonTolerance) {
        if (sampleId == null || sampleId.length() == 0 ||
                !sampleId.matches("[A-Za-z0-9_.+\\-]+"))
            throw new IllegalArgumentException("Invalid diagnostic sample ID");
        if (Double.isNaN(value) || Double.isInfinite(value) ||
                Double.isNaN(comparisonTolerance) || Double.isInfinite(comparisonTolerance) ||
                comparisonTolerance < 0)
            throw new IllegalArgumentException("Invalid diagnostic sample value or tolerance");
        this.outcome = Outcome.NUMERIC;
        this.sampleId = sampleId;
        this.value = value;
        this.comparisonTolerance = comparisonTolerance;
    }

    private GeneratedDiagnosticSample(String sampleId) {
        if (sampleId == null || !sampleId.matches("[A-Za-z0-9_.+\\-]+"))
            throw new IllegalArgumentException("Invalid diagnostic sample ID");
        this.sampleId = sampleId;
        this.outcome = Outcome.OVER_RANGE;
        this.value = Double.NaN;
        this.comparisonTolerance = Double.NaN;
    }

    static GeneratedDiagnosticSample overRange(String sampleId) {
        return new GeneratedDiagnosticSample(sampleId);
    }
    String getSampleId() { return sampleId; }
    Outcome getOutcome() { return outcome; }
    boolean isOverRange() { return outcome == Outcome.OVER_RANGE; }
    double getValue() {
        requireNumeric();
        return value;
    }
    double getComparisonTolerance() {
        requireNumeric();
        return comparisonTolerance;
    }
    private void requireNumeric() {
        if (isOverRange()) throw new IllegalStateException("Over-range observation has no numeric value or tolerance");
    }
}
