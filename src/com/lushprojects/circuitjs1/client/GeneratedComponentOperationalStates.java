package com.lushprojects.circuitjs1.client;

import java.util.HashMap;

class GeneratedComponentOperationalStates {
    private static final double LED_ILLUMINATED_CURRENT = .001;
    private final HashMap<String, LEDElm> leds = new HashMap<String, LEDElm>();

    void completePhysicalBindings(PhysicalBoardRuntime runtime) {
        for (PhysicalBoardSlot slot : runtime.getSlots()) {
            PhysicalPart<?> part = slot.getInstalledPart();
            if (!(part instanceof PhysicalLedPart)) continue;
            LEDElm led = ((PhysicalLedPart)part).getElement();
            LEDElm prior = leds.get(slot.getComponentId());
            if (prior != null && prior != led) throw new IllegalStateException("Foreign physical LED observation");
            if (prior == null) bindLed(slot.getComponentId(), led);
        }
    }

    void bindLed(String componentId, LEDElm led) {
        if (componentId == null || led == null || leds.containsKey(componentId))
            throw new IllegalArgumentException("Invalid LED operational state binding");
        leds.put(componentId, led);
    }

    void replaceLed(String componentId, LEDElm led) {
        if (componentId == null || led == null || !leds.containsKey(componentId))
            throw new IllegalArgumentException("Invalid LED operational state replacement");
        leds.put(componentId, led);
    }

    void requireOwnedBy(java.util.Vector<CircuitElm> elements) {
        for (LEDElm led : leds.values())
            if (!elements.contains(led))
                throw new IllegalArgumentException("Operational state references a foreign solver element");
    }

    boolean isIlluminated(String componentId) {
        LEDElm led = leds.get(componentId);
        return led != null && led.getCurrent() >= LED_ILLUMINATED_CURRENT;
    }
}
