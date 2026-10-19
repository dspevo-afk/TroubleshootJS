package com.lushprojects.circuitjs1.client;

import java.util.Vector;

/** Bounded planner-only sizing and visit-selection policies for canonical passive poses. */
final class PcbPassivePosePolicy {
    static final int LEGACY = 0;
    static final int ORTHOGONAL = 1;
    static final int SIZING_ONLY = 2;

    private PcbPassivePosePolicy() { }

    static boolean supportsVersion(int version) {
        return version == LEGACY || version == ORTHOGONAL || version == SIZING_ONLY;
    }

    /** Package allowlist is deliberately based on canonical package identity. */
    static boolean supportsPackage(PhysicalPackage physicalPackage) {
        return physicalPackage == PhysicalPackages.AXIAL_RESISTOR ||
            physicalPackage == PhysicalPackages.AXIAL_DIODE ||
            physicalPackage == PhysicalPackages.RADIAL_ELECTROLYTIC_CAPACITOR ||
            physicalPackage == PhysicalPackages.RADIAL_CERAMIC_CAPACITOR;
    }

    /**
     * Build the one alternate pose from the geometry already selected for the
     * TOP0 source. This method has no registry or random input by design.
     */
    static PcbFootprint alternativeFor(BoardComponent component, PcbFootprint source,
            int policyVersion, boolean eligibleBranch) {
        if (!supportsVersion(policyVersion))
            throw new IllegalArgumentException("Unknown passive pose policy interpretation");
        if (policyVersion == LEGACY || !eligibleBranch)
            return null;
        if (component == null || source == null)
            throw new IllegalArgumentException("Missing eligible passive pose source");
        PhysicalPackage physicalPackage = component.getPhysicalPackage();
        if (!supportsPackage(physicalPackage))
            return null;

        PcbComponentPlacement placement = source.getPlacement();
        if (!component.getId().equals(placement.getComponentId()) ||
                physicalPackage != placement.getPhysicalPackage() ||
                placement.getRotation() != PcbRotation.DEG_0 ||
                placement.getMountingSide() != PcbBoardSide.TOP ||
                !physicalPackage.acceptsGeometry(placement.getPhysicalGeometry()))
            throw new IllegalArgumentException("Eligible passive pose source must be canonical TOP0 geometry");
        Vector<String> componentPads = component.getPadIds();
        Vector<PcbPadPlacement> sourcePads = source.getPads();
        if (!componentPads.equals(padIds(sourcePads)))
            throw new IllegalArgumentException("Passive pose source pad identities do not match its component");

        PcbPackagePose pose = new PcbPackagePose(placement.getX(), placement.getY(),
            PcbRotation.DEG_90, PcbBoardSide.TOP);
        return PcbFootprint.fromPhysicalPackage(component, pose,
            placement.getPhysicalGeometry());
    }

    /** Select one template for an existing origin visit, without adding visits. */
    static PcbFootprint forVisit(PcbFootprint source, PcbFootprint alternative,
            int policyVersion, long visitOrdinal) {
        if (!supportsVersion(policyVersion))
            throw new IllegalArgumentException("Unknown passive pose policy interpretation");
        if (visitOrdinal < 0)
            throw new IllegalArgumentException("Negative passive pose visit ordinal");
        if (source == null)
            throw new IllegalArgumentException("Missing passive pose source footprint");
        if (policyVersion == LEGACY || policyVersion == SIZING_ONLY || alternative == null ||
                (visitOrdinal & 1L) == 0L)
            return source;
        return alternative;
    }

    private static Vector<String> padIds(Vector<PcbPadPlacement> pads) {
        Vector<String> result = new Vector<String>();
        for (PcbPadPlacement pad : pads)
            result.add(pad.getPadId());
        return result;
    }
}
