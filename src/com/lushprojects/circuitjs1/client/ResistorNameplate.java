package com.lushprojects.circuitjs1.client;

class ResistorNameplate implements PhysicalSpecification {
    static final double DEFAULT_RATED_WATTAGE = .25;
    private final String componentId;
    private final double nominalResistanceOhms;
    private final double tolerancePercent;
    private final double ratedWattage;

    ResistorNameplate(String componentId, double nominalResistanceOhms, double tolerancePercent) {
        this(componentId, nominalResistanceOhms, tolerancePercent, DEFAULT_RATED_WATTAGE);
    }

    ResistorNameplate(String componentId, double nominalResistanceOhms, double tolerancePercent,
            double ratedWattage) {
        if (componentId == null || componentId.length() == 0 ||
            !isFinitePositive(nominalResistanceOhms) || !isFinitePositive(tolerancePercent) ||
            !isFinitePositive(ratedWattage))
            throw new IllegalArgumentException("Invalid resistor nameplate");
        this.componentId = componentId;
        this.nominalResistanceOhms = nominalResistanceOhms;
        this.tolerancePercent = tolerancePercent;
        this.ratedWattage = ratedWattage;
    }

    String getComponentId() { return componentId; }
    public String getSpecificationId() { return componentId; }
    String getDisplayName() { return getDisplayValue(); }
    double getNominalResistanceOhms() { return nominalResistanceOhms; }
    double getTolerancePercent() { return tolerancePercent; }
    double getRatedWattage() { return ratedWattage; }

    String getDisplayValue() {
        return format(nominalResistanceOhms) + " Ohm +/-" + format(tolerancePercent) + "%";
    }

    private static String format(double value) {
        if (value == Math.rint(value))
            return String.valueOf((long) value);
        return String.valueOf(value);
    }

    private static boolean isFinitePositive(double value) {
        return !Double.isNaN(value) && !Double.isInfinite(value) && value > 0;
    }
}
