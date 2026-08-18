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

    private static String key(int x, int y) { return x + ":" + y; }
}
