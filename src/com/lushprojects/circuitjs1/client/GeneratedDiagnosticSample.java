package com.lushprojects.circuitjs1.client;

/** Immutable solver observation retained as developer-only diagnostic evidence. */
final class GeneratedDiagnosticSample {
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
        this.sampleId = sampleId;
        this.value = value;
        this.comparisonTolerance = comparisonTolerance;
    }

    String getSampleId() { return sampleId; }
    double getValue() { return value; }
    double getComparisonTolerance() { return comparisonTolerance; }
}
