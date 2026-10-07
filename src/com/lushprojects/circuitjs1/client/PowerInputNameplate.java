package com.lushprojects.circuitjs1.client;

/** Immutable input marking. AC nominal voltage is explicitly RMS, never sine peak. */
class PowerInputNameplate {
    enum Convention { DC, AC_RMS }
    private final String powerInputId;
    private final double nominalVoltage;
    private final Convention convention;
    private final double frequencyHz;

    PowerInputNameplate(String powerInputId, double nominalVoltage) {
        this(powerInputId, nominalVoltage, Convention.DC, 0);
    }

    static PowerInputNameplate acRms(String powerInputId, double rmsVoltage, double frequencyHz) {
        return new PowerInputNameplate(powerInputId, rmsVoltage, Convention.AC_RMS, frequencyHz);
    }

    private PowerInputNameplate(String powerInputId, double nominalVoltage,
            Convention convention, double frequencyHz) {
        if (powerInputId == null || powerInputId.length() == 0 ||
                !finite(nominalVoltage) || nominalVoltage <= 0 || convention == null ||
                !finite(frequencyHz) ||
                (convention == Convention.AC_RMS ? frequencyHz <= 0 : frequencyHz != 0))
            throw new IllegalArgumentException("Invalid power input nameplate");
        this.powerInputId = powerInputId;
        this.nominalVoltage = nominalVoltage;
        this.convention = convention;
        this.frequencyHz = frequencyHz;
    }

    String getPowerInputId() { return powerInputId; }
    /** DC nominal voltage or AC RMS voltage according to getConvention(). */
    double getNominalVoltage() { return nominalVoltage; }
    Convention getConvention() { return convention; }
    double getFrequencyHz() { return frequencyHz; }

    String getDisplayLabel() {
        return convention == Convention.AC_RMS ?
            format(nominalVoltage) + " VAC RMS / " + format(frequencyHz) + " Hz" :
            "+" + format(nominalVoltage) + "V";
    }

    private static String format(double value) {
        return value == Math.rint(value) ? Long.toString((long)value) : Double.toString(value);
    }
    private static boolean finite(double value) {
        return !Double.isNaN(value) && !Double.isInfinite(value);
    }
}
