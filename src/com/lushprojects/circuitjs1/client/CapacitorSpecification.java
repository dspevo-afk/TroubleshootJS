package com.lushprojects.circuitjs1.client;

import java.util.Vector;

/** Immutable typed electrical/nameplate definition for a capacitor catalog row. */
final class CapacitorSpecification implements PhysicalSpecification {
    private final String specificationId;
    private final double capacitanceFarads;
    private final double tolerancePercent;
    private final double ratedVoltage;
    private final VoltageRating voltageRating;
    private final PhysicalPackage physicalPackage;
    private final CapacitorNameplate nameplate;
    private final boolean explicitModelRecipe;
    private final double esrOhms;
    private final int integrationFlags;
    private final double initialVoltage;

    CapacitorSpecification(String specificationId, double capacitanceFarads,
            double tolerancePercent, double ratedVoltage, PhysicalPackage physicalPackage,
            CapacitorNameplate nameplate) {
        this(specificationId, capacitanceFarads, tolerancePercent, ratedVoltage,
            physicalPackage, nameplate, 0, false, .001, false);
    }

    /** Explicit package model; ordinary historical specifications keep their defaults. */
    CapacitorSpecification(String specificationId, double capacitanceFarads,
            double tolerancePercent, double ratedVoltage, PhysicalPackage physicalPackage,
            CapacitorNameplate nameplate, double esrOhms, boolean backwardEuler,
            double initialVoltage) {
        this(specificationId, capacitanceFarads, tolerancePercent, ratedVoltage,
            physicalPackage, nameplate, esrOhms, backwardEuler, initialVoltage, true);
    }

    private CapacitorSpecification(String specificationId, double capacitanceFarads,
            double tolerancePercent, double ratedVoltage, PhysicalPackage physicalPackage,
            CapacitorNameplate nameplate, double esrOhms, boolean backwardEuler,
            double initialVoltage, boolean explicitModelRecipe) {
        if (specificationId == null || specificationId.length() == 0 ||
                !isFinitePositive(capacitanceFarads) || !isFinitePositive(tolerancePercent) ||
                !isFinitePositive(ratedVoltage) || physicalPackage == null || nameplate == null ||
                !isFinite(esrOhms) || esrOhms < 0 || !isFinite(initialVoltage))
            throw new IllegalArgumentException("Invalid capacitor specification");
        this.specificationId = specificationId;
        this.capacitanceFarads = capacitanceFarads;
        this.tolerancePercent = tolerancePercent;
        this.ratedVoltage = ratedVoltage;
        voltageRating = new VoltageRating(ratedVoltage);
        this.physicalPackage = physicalPackage;
        this.nameplate = nameplate;
        this.explicitModelRecipe = explicitModelRecipe;
        this.esrOhms = esrOhms;
        integrationFlags = backwardEuler ? CapacitorElm.FLAG_BACK_EULER : 0;
        this.initialVoltage = initialVoltage;
    }

    public String getSpecificationId() { return specificationId; }
    public Vector<PhysicalRating> getRatings() {
        Vector<PhysicalRating> result = new Vector<PhysicalRating>();
        result.add(voltageRating);
        return result;
    }

    boolean hasExplicitModelRecipe() { return explicitModelRecipe; }
    double getEsrOhms() { return esrOhms; }
    int getIntegrationFlags() { return integrationFlags; }
    double getInitialVoltage() { return initialVoltage; }
    double getCapacitanceFarads() { return capacitanceFarads; }
    double getTolerancePercent() { return tolerancePercent; }
    double getRatedVoltage() { return ratedVoltage; }
    VoltageRating getVoltageRating() { return voltageRating; }
    PhysicalPackage getPhysicalPackage() { return physicalPackage; }
    CapacitorNameplate getNameplate() { return nameplate; }
    boolean isPolarized() {
        return physicalPackage.isEquivalentTo(PhysicalPackages.RADIAL_ELECTROLYTIC_CAPACITOR);
    }

    private static boolean isFinitePositive(double value) {
        return isFinite(value) && value > 0;
    }
    private static boolean isFinite(double value) {
        return !Double.isNaN(value) && !Double.isInfinite(value);
    }
}
