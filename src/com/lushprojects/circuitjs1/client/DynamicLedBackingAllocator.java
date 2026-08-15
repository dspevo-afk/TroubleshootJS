package com.lushprojects.circuitjs1.client;

import java.util.HashSet;
import java.util.Vector;

class DynamicLedBackingAllocator {
    static LEDElm create(Vector<CircuitElm> occupiedElements, LedNameplate nameplate) {
        HashSet<String> occupied = new HashSet<String>();
        for (CircuitElm element : occupiedElements) {
            occupied.add(key(element.x, element.y));
            occupied.add(key(element.x2, element.y2));
        }
        for (int index = 0; ; index++) {
            int x = 1200 + (index % 32) * 48;
            int y = 2000 + (index / 32) * 48;
            int x2 = x + 24;
            if (!occupied.contains(key(x, y)) && !occupied.contains(key(x2, y))) {
                LEDElm led = new LEDElm(x, y);
                led.drag(x2, y);
                led.modelName = nameplate.getModelName();
                led.setup();
                led.colorR = nameplate.getRed();
                led.colorG = nameplate.getGreen();
                led.colorB = nameplate.getBlue();
                return led;
            }
        }
    }

    private static String key(int x, int y) { return x + ":" + y; }
}
