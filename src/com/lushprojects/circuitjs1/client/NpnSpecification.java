package com.lushprojects.circuitjs1.client;

import java.util.Vector;

/** Immutable technical specification for the generated NPN/TO-92 part. */
final class NpnSpecification implements PhysicalSpecification {
    private final String specificationId;
    private final double beta;

    NpnSpecification(String specificationId, double beta) {
        if (specificationId == null || specificationId.length() == 0 ||
                Double.isNaN(beta) || Double.isInfinite(beta) || beta <= 0)
            throw new IllegalArgumentException("Invalid NPN specification");
        this.specificationId = specificationId;
        this.beta = beta;
    }

    public String getSpecificationId() { return specificationId; }
    public Vector<PhysicalRating> getRatings() { return new Vector<PhysicalRating>(); }
    double getBeta() { return beta; }
    PhysicalPackage getPhysicalPackage() { return PhysicalPackages.TO92_NPN; }
}
