package com.lushprojects.circuitjs1.client;

class PhysicalResistorPart {
    private final String id;
    private final ResistorNameplate nameplate;
    private final ResistorElm element;
    private final GeneratedFaultBinding faultBinding;
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
        this.location = location;
    }

    String getId() { return id; }
    ResistorNameplate getNameplate() { return nameplate; }
    ResistorElm getElement() { return element; }
    GeneratedFaultBinding getFaultBinding() { return faultBinding; }
    ResistorPartLocation getLocation() { return location; }
    boolean isFaulted() { return faultBinding != null && faultBinding.isApplied(); }

    void setLocation(ResistorPartLocation location) { this.location = location; }

    void moveTo(int x1, int y1, int x2, int y2) {
        element.x = x1;
        element.y = y1;
        element.x2 = x2;
        element.y2 = y2;
        element.setPoints();
    }
}