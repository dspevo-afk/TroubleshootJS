package com.lushprojects.circuitjs1.client;

import java.util.Vector;

class PhysicalResistorPart {
    private final String id;
    private final ResistorNameplate nameplate;
    private final ResistorElm element;
    private final GeneratedFaultBinding faultBinding;
    private final CircuitMeasurementEndpoint firstTerminal;
    private final CircuitMeasurementEndpoint secondTerminal;
    private ResistorPartLocation location;

    PhysicalResistorPart(String id, ResistorNameplate nameplate, ResistorElm element,
            GeneratedFaultBinding faultBinding, ResistorPartLocation location) {
        if (id == null || id.length() == 0 || nameplate == null || element == null ||
                location == null)
            throw new IllegalArgumentException("Invalid physical resistor part");
        this.id = id;
        this.nameplate = nameplate;
        this.element = element;
        this.faultBinding = faultBinding;
        this.firstTerminal = new CircuitPostMeasurementEndpoint(element, 0);
        this.secondTerminal = faultBinding == null
            ? new CircuitPostMeasurementEndpoint(element, 1)
            : faultBinding.getPublicTerminal(element, 1);
        this.location = location;
    }

    String getId() { return id; }
    ResistorNameplate getNameplate() { return nameplate; }
    ResistorElm getElement() { return element; }
    GeneratedFaultBinding getFaultBinding() { return faultBinding; }
    ResistorPartLocation getLocation() { return location; }
    boolean isOriginal() { return id.endsWith("_ORIGINAL"); }
    boolean isFaulted() { return faultBinding != null && faultBinding.isApplied(); }

    CircuitMeasurementEndpoint getPublicTerminal(int terminal) {
        if (terminal == 0)
            return firstTerminal;
        if (terminal == 1)
            return secondTerminal;
        throw new IllegalArgumentException("Invalid resistor part terminal: " + terminal);
    }

    Vector<CircuitElm> getBackingElements() {
        Vector<CircuitElm> elements = new Vector<CircuitElm>();
        elements.add(element);
        if (faultBinding != null)
            elements.addAll(faultBinding.getPrivateSimulationElements());
        return elements;
    }

    void setLocation(ResistorPartLocation location) { this.location = location; }
}
