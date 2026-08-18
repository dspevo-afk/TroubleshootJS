package com.lushprojects.circuitjs1.client;

import java.util.HashSet;
import java.util.Vector;

/** Allocates a detached real NMosfetElm for a catalog acquisition. */
final class DynamicNmosBackingAllocator {
    private static final int START_X = 1800;
    private static final int START_Y = 1600;
    private static final int STEP = 64;

    private DynamicNmosBackingAllocator() { }

    static NMosfetElm create(Vector<CircuitElm> occupiedElements,
            NmosSpecification specification) {
        if (occupiedElements == null || specification == null)
            throw new IllegalArgumentException("Missing NMOS backing allocation context");
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
                NMosfetElm mosfet = new NMosfetElm(x, y);
                mosfet.drag(x2, y);
                mosfet.vt = specification.getThresholdVoltage();
                mosfet.beta = specification.getBeta();
                return mosfet;
            }
        }
    }

    private static String key(int x, int y) { return x + ":" + y; }
}
