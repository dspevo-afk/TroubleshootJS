package com.lushprojects.circuitjs1.client;

import java.util.HashSet;
import java.util.Vector;

/** Allocates a detached real NTransistorElm for a catalog acquisition. */
final class DynamicNpnBackingAllocator {
    private static final int START_X = 1600;
    private static final int START_Y = 1600;
    private static final int STEP = 64;

    private DynamicNpnBackingAllocator() { }

    static NTransistorElm create(Vector<CircuitElm> occupiedElements,
            NpnSpecification specification) {
        if (occupiedElements == null || specification == null)
            throw new IllegalArgumentException("Missing NPN backing allocation context");
        HashSet<String> occupied = new HashSet<String>();
        for (CircuitElm element : occupiedElements) {
            occupied.add(key(element.x, element.y));
            occupied.add(key(element.x2, element.y2));
        }
        for (int index = 0; ; index++) {
            int x = START_X + (index % 24) * STEP;
            int y = START_Y + (index / 24) * STEP;
            int x2 = x + STEP;
            if (!occupied.contains(key(x, y)) && !occupied.contains(key(x2, y))) {
                NTransistorElm transistor = new NTransistorElm(x, y);
                transistor.drag(x2, y);
                transistor.setBeta(specification.getBeta());
                return transistor;
            }
        }
    }

    private static String key(int x, int y) { return x + ":" + y; }
}
