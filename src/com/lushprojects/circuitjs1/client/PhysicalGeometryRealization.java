package com.lushprojects.circuitjs1.client;

/**
 * Immutable identity of one canonical package geometry realization.
 *
 * Board coordinates are intentionally absent.  A placement owns coordinates;
 * this value only records which package-local realization was selected.
 */
final class PhysicalGeometryRealization {
    private final PhysicalPackage physicalPackage;
    private final PhysicalPackageGeometry physicalGeometry;
    private final String variantKey;
    private final String transformKey;
    private final PcbGeometryContractVersion geometryContractVersion;

    PhysicalGeometryRealization(PhysicalPackage physicalPackage,
            PhysicalPackageGeometry physicalGeometry, String variantKey,
            String transformKey, PcbGeometryContractVersion geometryContractVersion) {
        if (physicalPackage == null || physicalGeometry == null || variantKey == null ||
                variantKey.trim().length() == 0 || transformKey == null ||
                transformKey.trim().length() == 0 || geometryContractVersion == null)
            throw new IllegalArgumentException("Invalid physical geometry realization");
        if (!physicalPackage.acceptsGeometry(physicalGeometry))
            throw new IllegalArgumentException("Foreign or undeclared physical geometry");
        PhysicalPackage.GeometryVariant variant = physicalPackage.getGeometryVariant(variantKey);
        if (variant == null || variant.getGeometry() != physicalGeometry ||
                !transformKey.equals(variant.getTransformKey()))
            throw new IllegalArgumentException("Physical geometry variant identity mismatch");
        if (!geometryContractVersion.equals(physicalPackage.getGeometryContractVersion()) ||
                !geometryContractVersion.equals(physicalGeometry.getGeometryContractVersion()))
            throw new IllegalArgumentException("Physical geometry contract version mismatch");

        this.physicalPackage = physicalPackage;
        this.physicalGeometry = physicalGeometry;
        this.variantKey = variantKey;
        this.transformKey = transformKey;
        this.geometryContractVersion = geometryContractVersion;
    }

    static PhysicalGeometryRealization fromPlacement(PcbComponentPlacement placement) {
        if (placement == null)
            throw new IllegalArgumentException("Missing PCB component placement");
        return new PhysicalGeometryRealization(placement.getPhysicalPackage(),
            placement.getPhysicalGeometry(), placement.getGeometryVariantKey(),
            placement.getGeometryTransformKey(), placement.getGeometryContractVersion());
    }

    PhysicalPackage getPhysicalPackage() { return physicalPackage; }
    PhysicalPackage getPackage() { return physicalPackage; }
    PhysicalPackageGeometry getPhysicalGeometry() { return physicalGeometry; }
    PhysicalPackageGeometry getGeometry() { return physicalGeometry; }
    String getVariantKey() { return variantKey; }
    String getGeometryVariantKey() { return variantKey; }
    String getTransformKey() { return transformKey; }
    String getGeometryTransformKey() { return transformKey; }
    PcbGeometryContractVersion getGeometryContractVersion() { return geometryContractVersion; }
    int getGeometryContractVersionValue() { return geometryContractVersion.getValue(); }

    /** Stable package-local identity; coordinates and electrical identity are excluded. */
    String fingerprint() {
        return physicalPackage.getId() + "|variant=" + variantKey + "|transform=" +
            transformKey + "|version=" + geometryContractVersion.getValue() + "|geometry=" +
            physicalGeometry.getWidth() + "x" + physicalGeometry.getHeight();
    }

    String geometryFingerprint() { return fingerprint(); }

    boolean isEquivalentTo(PhysicalGeometryRealization other) {
        return other != null && physicalPackage.isEquivalentTo(other.physicalPackage) &&
            physicalGeometry.isEquivalentTo(other.physicalGeometry) &&
            variantKey.equals(other.variantKey) && transformKey.equals(other.transformKey) &&
            geometryContractVersion.equals(other.geometryContractVersion);
    }

    public boolean equals(Object other) {
        return other instanceof PhysicalGeometryRealization &&
            isEquivalentTo((PhysicalGeometryRealization) other);
    }

    public int hashCode() { return fingerprint().hashCode(); }

    public String toString() { return fingerprint(); }
}
