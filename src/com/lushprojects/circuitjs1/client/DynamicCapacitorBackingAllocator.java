package com.lushprojects.circuitjs1.client;

import java.util.HashSet;
import java.util.Vector;

/** Allocates a detached CircuitJS capacitor backing for a new physical instance. */
final class DynamicCapacitorBackingAllocator {
    private static final int START_X = 1200;
    private static final int START_Y = 2400;
    private static final int STEP = 48;

    private DynamicCapacitorBackingAllocator() { }

    static CapacitorElm create(Vector<CircuitElm> occupiedElements,
            CapacitorSpecification specification) {
        if (occupiedElements == null || specification == null)
            throw new IllegalArgumentException("Missing capacitor backing allocation context");
        if (specification.hasExplicitModelRecipe())
            throw new IllegalArgumentException("Explicit capacitor recipe requires complete backing allocation");
        HashSet<String> occupied = new HashSet<String>();
        for (CircuitElm element : occupiedElements) {
            occupied.add(key(element.x, element.y));
            occupied.add(key(element.x2, element.y2));
        }
        for (int index = 0; ; index++) {
            int x = START_X + (index % 32) * STEP;
            int y = START_Y + (index / 32) * STEP;
            int x2 = x + STEP / 2;
            if (!occupied.contains(key(x, y)) && !occupied.contains(key(x2, y))) {
                CapacitorElm capacitor = new CapacitorElm(x, y);
                capacitor.drag(x2, y);
                capacitor.setCapacitance(specification.getCapacitanceFarads());
                return capacitor;
            }
        }
    }

    /** Complete model allocation, with legacy allocation unchanged when not opted in. */
    static Backing createBacking(Vector<CircuitElm> occupiedElements,
            CapacitorSpecification specification) {
        if (occupiedElements == null || specification == null)
            throw new IllegalArgumentException("Missing capacitor backing allocation context");
        if (!specification.hasExplicitModelRecipe())
            return new Backing(create(occupiedElements, specification), null);
        HashSet<String> occupied = new HashSet<String>();
        for (CircuitElm element : occupiedElements)
            for (int post = 0; post < element.getPostCount(); post++) {
                Point point = element.getPost(post);
                occupied.add(key(point.x, point.y));
            }
        for (int index = 0; ; index++) {
            int x = START_X + (index % 32) * STEP;
            int y = START_Y + (index / 32) * STEP;
            int internalX = x + STEP / 3, negativeX = x + 2 * STEP / 3;
            if (occupied.contains(key(x, y)) || occupied.contains(key(negativeX, y)) ||
                    (specification.getEsrOhms() > 0 && occupied.contains(key(internalX, y))))
                continue;
            CapacitorElm capacitor = new CapacitorElm(
                specification.getEsrOhms() > 0 ? internalX : x, y);
            capacitor.x2 = negativeX; capacitor.y2 = y; capacitor.setPoints();
            capacitor.setCapacitance(specification.getCapacitanceFarads());
            capacitor.flags = specification.getIntegrationFlags();
            capacitor.initialVoltage = specification.getInitialVoltage();
            capacitor.reset();
            ResistorElm esr = null;
            if (specification.getEsrOhms() > 0) {
                esr = new ResistorElm(x, y);
                esr.x2 = internalX; esr.y2 = y; esr.setPoints();
                esr.setResistance(specification.getEsrOhms());
            }
            return new Backing(capacitor, esr);
        }
    }

    static final class Backing {
        private final CapacitorElm capacitor;
        private final ResistorElm esr;
        private Backing(CapacitorElm capacitor, ResistorElm esr) {
            this.capacitor = capacitor; this.esr = esr;
        }
        CapacitorElm getCapacitor() { return capacitor; }
        ResistorElm getEsrElement() { return esr; }
        Vector<CircuitElm> getElements() {
            Vector<CircuitElm> result = new Vector<CircuitElm>();
            result.add(capacitor); if (esr != null) result.add(esr); return result;
        }
    }

    private static String key(int x, int y) { return x + ":" + y; }
}
