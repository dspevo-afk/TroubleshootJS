package com.lushprojects.circuitjs1.client;

import java.util.Vector;

/** Fixed ordinary axial fuse recipe used by the E05 AC input and Q60 assembly. */
final class FuseSpecification implements PhysicalSpecification {
    static final FuseSpecification E05_AC = new FuseSpecification();
    static final double RESISTANCE_OHMS = .1;
    static final double THERMAL_CAPACITY_AMP_SQUARED_SECONDS = .1;
    private FuseSpecification() { }
    public String getSpecificationId() { return "AXIAL_FUSE_100_MILLIOHM_0P1_A2S_E05_V1"; }
    public Vector<PhysicalRating> getRatings() { return new Vector<PhysicalRating>(); }
    String catalogLabel() { return "Axial fuse (0.1 ohm)"; }
    PhysicalNameplate nameplate(String physicalPartId) {
        return new PhysicalNameplate(physicalPartId,catalogLabel(),"Thermal capacity","0.1 A^2 s");
    }
    static void requireBacking(PhysicalSpecification specification, PhysicalPackage pkg,
            Vector<CircuitElm> elements) {
        if (specification != E05_AC || pkg != PhysicalPackages.AXIAL_FUSE || elements == null ||
                elements.size() != 1 || elements.get(0) == null ||
                elements.get(0).getClass() != ProtectionFuseElm.class)
            throw new IllegalArgumentException("AC fuse requires its declared ordinary axial backing");
        ProtectionFuseElm fuse = (ProtectionFuseElm)elements.get(0);
        if (fuse.resistance != RESISTANCE_OHMS || fuse.i2t != THERMAL_CAPACITY_AMP_SQUARED_SECONDS ||
                fuse.getPost(0).equals(fuse.getPost(1)))
            throw new IllegalArgumentException("AC fuse resistance or thermal recipe changed");
    }
}
