package com.lushprojects.circuitjs1.client;

import java.util.Vector;

/** Fixed E06 output winding: one package owns the ordinary L and its series resistance. */
final class InductorSpecification implements PhysicalSpecification {
    static final InductorSpecification STANDARD = new InductorSpecification();
    static final double INDUCTANCE_HENRIES = .020;
    static final double WINDING_RESISTANCE_OHMS = .5;
    /** Residual current criterion for a graph detach; this is not a nameplate rating. */
    static final double DETACH_CURRENT_LIMIT_AMPS = 1e-6;
    private InductorSpecification() { }
    public String getSpecificationId() { return "RADIAL_INDUCTOR_20MH_500_MILLIOHM_E06_V1"; }
    public Vector<PhysicalRating> getRatings() { return new Vector<PhysicalRating>(); }
    String catalogLabel() { return "20 mH inductor (0.5 ohm winding resistance)"; }

    Vector<CircuitElm> createBacking(int x, int y) {
        Vector<CircuitElm> elements = new Vector<CircuitElm>();
        boolean complete = false;
        try {
            InductorElm winding = new InductorElm(x, y); elements.add(winding);
            winding.x2 = x + 16; winding.y2 = y;
            winding.flags &= ~Inductor.FLAG_BACK_EULER;
            winding.setInductance(INDUCTANCE_HENRIES); winding.setPoints();
            ResistorElm resistance = new ResistorElm(x + 16, y); elements.add(resistance);
            resistance.x2 = x + 32; resistance.y2 = y;
            resistance.setResistance(WINDING_RESISTANCE_OHMS); resistance.setPoints();
            complete = true; return elements;
        } finally { if (!complete) for (CircuitElm element : elements) element.delete(); }
    }

    static void requireBacking(PhysicalSpecification spec, PhysicalPackage pkg, Vector<CircuitElm> elements) {
        if (spec != STANDARD || pkg != PhysicalPackages.RADIAL_INDUCTOR_2 || elements == null ||
                elements.size() != 2 || elements.get(0) == null || elements.get(1) == null ||
                elements.get(0).getClass() != InductorElm.class || elements.get(1).getClass() != ResistorElm.class)
            throw new IllegalArgumentException("Inductor requires its declared two-element winding backing");
        InductorElm winding = (InductorElm)elements.get(0);
        ResistorElm resistance = (ResistorElm)elements.get(1);
        if (winding.inductance != INDUCTANCE_HENRIES || resistance.resistance != WINDING_RESISTANCE_OHMS ||
                (winding.flags & Inductor.FLAG_BACK_EULER) != 0 || winding.ind == null ||
                winding.ind.inductance != INDUCTANCE_HENRIES || winding.ind.flags != winding.flags ||
                !winding.getPost(1).equals(resistance.getPost(0)) ||
                winding.getPost(0).equals(resistance.getPost(1)))
            throw new IllegalArgumentException("Inductor winding recipe or internal series junction changed");
    }
}
