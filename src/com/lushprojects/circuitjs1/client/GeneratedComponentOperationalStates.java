package com.lushprojects.circuitjs1.client;

import java.util.HashMap;

class GeneratedComponentOperationalStates {
    private static final double LED_ILLUMINATED_CURRENT = .001;
    private final HashMap<String, LEDElm> leds = new HashMap<String, LEDElm>();

    void bindLed(String componentId, LEDElm led) {
        if (componentId == null || led == null || leds.containsKey(componentId))
            throw new IllegalArgumentException("Invalid LED operational state binding");
        leds.put(componentId, led);
    }

    boolean isIlluminated(String componentId) {
        LEDElm led = leds.get(componentId);
        return led != null && led.getCurrent() >= LED_ILLUMINATED_CURRENT;
    }
}
