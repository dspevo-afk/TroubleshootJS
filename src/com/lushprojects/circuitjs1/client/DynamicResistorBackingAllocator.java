package com.lushprojects.circuitjs1.client;

import java.util.HashSet;
import java.util.Vector;

class DynamicResistorBackingAllocator {
    private static final int START_X = 1200;
    private static final int START_Y = 1200;
    private static final int STEP = 48;

    static ResistorElm create(Vector<CircuitElm> occupiedElements, double resistanceOhms) {
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
                ResistorElm resistor = new ResistorElm(x, y);
                resistor.drag(x2, y);
                resistor.setResistance(resistanceOhms);
                return resistor;
            }
        }
    }

    private static String key(int x, int y) { return x + ":" + y; }
}