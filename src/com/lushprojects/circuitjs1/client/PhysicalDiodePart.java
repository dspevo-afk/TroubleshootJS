package com.lushprojects.circuitjs1.client;

import java.util.Vector;

class PhysicalDiodePart {
    private final String id;
    private final DiodeNameplate nameplate;
    private final DiodeElm element;
    private final GeneratedFaultBinding faultBinding;
    private final CircuitMeasurementEndpoint anode;
    private final CircuitMeasurementEndpoint cathode;
    private final boolean reversedInstallation;
    private DiodePartLocation location;

    PhysicalDiodePart(String id, DiodeNameplate nameplate, DiodeElm element,
            GeneratedFaultBinding faultBinding, boolean reversedInstallation,
            DiodePartLocation location) {
        if (id == null || id.length() == 0 || nameplate == null || element == null || location == null)
            throw new IllegalArgumentException("Invalid physical diode part");
        this.id = id;
        this.nameplate = nameplate;
        this.element = element;
        this.faultBinding = faultBinding;
        this.reversedInstallation = reversedInstallation;
        anode = new CircuitPostMeasurementEndpoint(element, 0);
        cathode = faultBinding == null ? new CircuitPostMeasurementEndpoint(element, 1) :
            new CircuitPostMeasurementEndpoint(faultBinding.getIsolationElement(), 1);
        this.location = location;
    }

    String getId() { return id; }
    DiodeNameplate getNameplate() { return nameplate; }
    DiodeElm getElement() { return element; }
    GeneratedFaultBinding getFaultBinding() { return faultBinding; }
    boolean isFaulted() { return faultBinding != null && faultBinding.isApplied(); }
    boolean isReversedInstallation() { return reversedInstallation; }
    DiodePartLocation getLocation() { return location; }
    void setLocation(DiodePartLocation location) { this.location = location; }

    CircuitMeasurementEndpoint getTerminal(int terminal) {
        if (terminal == 0)
            return anode;
        if (terminal == 1)
            return cathode;
        throw new IllegalArgumentException("Invalid diode terminal: " + terminal);
    }

    CircuitMeasurementEndpoint getTerminalForBoardPad(String padId) {
        boolean anodePad = "D1.A".equals(padId);
        return getTerminal((anodePad ^ reversedInstallation) ? 0 : 1);
    }

    Vector<CircuitElm> getBackingElements() {
        Vector<CircuitElm> result = new Vector<CircuitElm>();
        result.add(element);
        if (faultBinding != null)
            result.add(faultBinding.getIsolationElement());
        return result;
    }
}
