package com.lushprojects.circuitjs1.client;

class ResistorNameplate {
    private final String componentId;
    private final double nominalResistanceOhms;
    private final double tolerancePercent;

    ResistorNameplate(String componentId, double nominalResistanceOhms, double tolerancePercent) {
        if (componentId == null || componentId.length() == 0 || nominalResistanceOhms <= 0 ||
                tolerancePercent <= 0)
            throw new IllegalArgumentException("Invalid resistor nameplate");
        this.componentId = componentId;
        this.nominalResistanceOhms = nominalResistanceOhms;
        this.tolerancePercent = tolerancePercent;
    }

    String getComponentId() { return componentId; }
    double getNominalResistanceOhms() { return nominalResistanceOhms; }
    double getTolerancePercent() { return tolerancePercent; }

    String getDisplayValue() {
        return format(nominalResistanceOhms) + " Ohm +/-" + format(tolerancePercent) + "%";
    }

    private static String format(double value) {
        if (value == Math.rint(value))
            return String.valueOf((long) value);
        return String.valueOf(value);
    }
}