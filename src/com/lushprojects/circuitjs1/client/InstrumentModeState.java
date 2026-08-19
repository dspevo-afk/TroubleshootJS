package com.lushprojects.circuitjs1.client;

/** Mutable provider-owned state exposed only through generic developer accessors. */
final class InstrumentModeState {
    private String displayText;
    private double primaryValue = Double.NaN;
    private double secondaryValue = Double.NaN;
    private int measurementCount;
    private boolean continuityDetected;
    private boolean refreshPending;

    InstrumentModeState(String initialDisplayText) {
        displayText = initialDisplayText;
    }

    String getDisplayText() {
        return displayText;
    }

    void setDisplayText(String displayText) {
        this.displayText = displayText;
    }

    double getPrimaryValue() {
        return primaryValue;
    }

    void setPrimaryValue(double primaryValue) {
        this.primaryValue = primaryValue;
    }

    double getSecondaryValue() {
        return secondaryValue;
    }

    void setSecondaryValue(double secondaryValue) {
        this.secondaryValue = secondaryValue;
    }

    int getMeasurementCount() {
        return measurementCount;
    }

    void incrementMeasurementCount() {
        measurementCount++;
    }

    void setMeasurementCountForDeveloperVerification(int measurementCount) {
        if (measurementCount < 0)
            throw new IllegalArgumentException("Negative instrument measurement count");
        this.measurementCount = measurementCount;
    }

    boolean isContinuityDetected() {
        return continuityDetected;
    }

    void setContinuityDetected(boolean continuityDetected) {
        this.continuityDetected = continuityDetected;
    }

    boolean isRefreshPending() {
        return refreshPending;
    }

    void setRefreshPending(boolean refreshPending) {
        this.refreshPending = refreshPending;
    }

    void clearMeasurement() {
        primaryValue = Double.NaN;
        secondaryValue = Double.NaN;
        continuityDetected = false;
        refreshPending = false;
    }
}
