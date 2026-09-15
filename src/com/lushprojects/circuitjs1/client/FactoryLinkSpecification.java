package com.lushprojects.circuitjs1.client;

import java.util.Vector;

/** Fixed P06 low-voltage prototype. Finite resistance is a model choice, not a zero-ohm wire stamp. */
final class FactoryLinkSpecification implements PhysicalSpecification {
    static final FactoryLinkSpecification STANDARD = new FactoryLinkSpecification();
    static final double RESISTANCE_OHMS = 0.05;
    private FactoryLinkSpecification() { }
    public String getSpecificationId() { return "RAISED_INSULATED_LINK_50_MILLIOHM"; }
    public Vector<PhysicalRating> getRatings() { return new Vector<PhysicalRating>(); }

    ResistorElm createElement(int x, int y) {
        ResistorElm element = new ResistorElm(x, y);
        element.x2 = x + 16; element.y2 = y;
        element.resistance = RESISTANCE_OHMS; element.setPoints();
        return element;
    }

    static void requireBacking(PhysicalSpecification spec, PhysicalPackage physical,
            Vector<CircuitElm> elements) {
        if (spec != STANDARD || physical != PhysicalPackages.RAISED_FACTORY_LINK ||
                elements == null || elements.size() != 1 ||
                elements.get(0).getClass() != ResistorElm.class ||
                ((ResistorElm)elements.get(0)).resistance != RESISTANCE_OHMS)
            throw new IllegalArgumentException("Factory link requires its declared finite electrical backing");
    }
}
