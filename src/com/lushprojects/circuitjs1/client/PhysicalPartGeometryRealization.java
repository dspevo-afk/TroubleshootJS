package com.lushprojects.circuitjs1.client;

/** One-time mutable binding state owned by a physical part instance. */
final class PhysicalPartGeometryRealization {
    private PhysicalGeometryRealization realization;

    PhysicalGeometryRealization getGeometryRealization() { return realization; }

    boolean isBound() { return realization != null; }

    void bind(PhysicalPackage expectedPackage, PhysicalGeometryRealization candidate) {
        if (expectedPackage == null)
            throw new IllegalArgumentException("Missing physical part package");
        if (candidate == null || !expectedPackage.isEquivalentTo(candidate.getPhysicalPackage()))
            throw new IllegalArgumentException("Physical part geometry package mismatch");
        bind(candidate);
    }

    void bind(PhysicalGeometryRealization candidate) {
        if (candidate == null)
            throw new IllegalArgumentException("Missing physical geometry realization");
        if (realization == null) {
            realization = candidate;
            return;
        }
        if (!realization.isEquivalentTo(candidate))
            throw new IllegalStateException("Physical part geometry realization cannot change");
    }
}
