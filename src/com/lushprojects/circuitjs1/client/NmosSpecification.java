package com.lushprojects.circuitjs1.client;

import java.util.Vector;

/** Immutable NMOS electrical/package specification; the solver remains CircuitJS. */
final class NmosSpecification implements PhysicalSpecification {
    private final String specificationId;
    private final double thresholdVoltage;
    private final double beta;

    NmosSpecification(String specificationId, double thresholdVoltage, double beta) {
        if (specificationId == null || specificationId.length() == 0 ||
                Double.isNaN(thresholdVoltage) || Double.isInfinite(thresholdVoltage) ||
                thresholdVoltage <= 0 || Double.isNaN(beta) || Double.isInfinite(beta) ||
                beta <= 0)
            throw new IllegalArgumentException("Invalid NMOS specification");
        this.specificationId = specificationId;
        this.thresholdVoltage = thresholdVoltage;
        this.beta = beta;
    }

    public String getSpecificationId() { return specificationId; }
    public Vector<PhysicalRating> getRatings() { return new Vector<PhysicalRating>(); }
    double getThresholdVoltage() { return thresholdVoltage; }
    double getBeta() { return beta; }
    PhysicalPackage getPhysicalPackage() { return PhysicalPackages.TO92_NMOS; }
}
