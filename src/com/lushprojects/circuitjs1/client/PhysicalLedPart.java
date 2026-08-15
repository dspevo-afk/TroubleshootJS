package com.lushprojects.circuitjs1.client;

import java.util.Vector;

class PhysicalLedPart {
    private final String id;
    private final LedNameplate nameplate;
    private final LEDElm element;
    private final CircuitMeasurementEndpoint anode;
    private final CircuitMeasurementEndpoint cathode;
    private final boolean reversedInstallation;
    private LedPartLocation location;

    PhysicalLedPart(String id, LedNameplate nameplate, LEDElm element,
            boolean reversedInstallation, LedPartLocation location) {
        if (id == null || id.length() == 0 || nameplate == null || element == null || location == null)
            throw new IllegalArgumentException("Invalid physical LED part");
        this.id = id;
        this.nameplate = nameplate;
        this.element = element;
        this.reversedInstallation = reversedInstallation;
        this.location = location;
        anode = new CircuitPostMeasurementEndpoint(element, 0);
        cathode = new CircuitPostMeasurementEndpoint(element, 1);
    }

    String getId() { return id; }
    LedNameplate getNameplate() { return nameplate; }
    LEDElm getElement() { return element; }
    boolean isReversedInstallation() { return reversedInstallation; }
    LedPartLocation getLocation() { return location; }
    void setLocation(LedPartLocation location) { this.location = location; }

    CircuitMeasurementEndpoint getTerminal(int terminal) {
        if (terminal == 0) return anode;
        if (terminal == 1) return cathode;
        throw new IllegalArgumentException("Invalid LED terminal: " + terminal);
    }

    CircuitMeasurementEndpoint getTerminalForBoardPad(String padId) {
        boolean anodePad = "LED1.A".equals(padId);
        return getTerminal((anodePad ^ reversedInstallation) ? 0 : 1);
    }

    Vector<CircuitElm> getBackingElements() {
        Vector<CircuitElm> result = new Vector<CircuitElm>();
        result.add(element);
        return result;
    }
}
