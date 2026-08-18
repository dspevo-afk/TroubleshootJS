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

    CapacitorSpecification(String specificationId, double capacitanceFarads,
            double tolerancePercent, double ratedVoltage, PhysicalPackage physicalPackage,
            CapacitorNameplate nameplate) {
        if (specificationId == null || specificationId.length() == 0 ||
                !isFinitePositive(capacitanceFarads) || !isFinitePositive(tolerancePercent) ||
                !isFinitePositive(ratedVoltage) || physicalPackage == null || nameplate == null)
            throw new IllegalArgumentException("Invalid capacitor specification");
        this.specificationId = specificationId;
        this.capacitanceFarads = capacitanceFarads;
        this.tolerancePercent = tolerancePercent;
        this.ratedVoltage = ratedVoltage;
        voltageRating = new VoltageRating(ratedVoltage);
        this.physicalPackage = physicalPackage;
        this.nameplate = nameplate;
    }

    public String getSpecificationId() { return specificationId; }
    public Vector<PhysicalRating> getRatings() {
        Vector<PhysicalRating> result = new Vector<PhysicalRating>();
        result.add(voltageRating);
        return result;
    }

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
        return !Double.isNaN(value) && !Double.isInfinite(value) && value > 0;
    }
}
