package com.lushprojects.circuitjs1.client;

import java.util.HashSet;
import java.util.Vector;

class DynamicDiodeBackingAllocator {
    static DiodeElm create(Vector<CircuitElm> occupiedElements) {
        HashSet<String> occupied = new HashSet<String>();
        for (CircuitElm element : occupiedElements) {
            occupied.add(key(element.x, element.y));
            occupied.add(key(element.x2, element.y2));
        }
        for (int index = 0; ; index++) {
            int x = 1200 + (index % 32) * 48;
            int y = 1600 + (index / 32) * 48;
            int x2 = x + 24;
            if (!occupied.contains(key(x, y)) && !occupied.contains(key(x2, y))) {
                DiodeElm diode = new DiodeElm(x, y);
                diode.drag(x2, y);
                diode.modelName = "default";
                diode.setup();
                return diode;
            }
        }
    }

    private static String key(int x, int y) { return x + ":" + y; }
}
