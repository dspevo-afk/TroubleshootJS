package com.lushprojects.circuitjs1.client;

class ReplaceableComponentSlot {
    private final String componentId;
    private final ResistorNameplate intendedNameplate;
    private PhysicalResistorPart installedPart;

    ReplaceableComponentSlot(String componentId, ResistorNameplate intendedNameplate,
            PhysicalResistorPart installedPart) {
        if (componentId == null || componentId.length() == 0 || intendedNameplate == null ||
                installedPart == null)
            throw new IllegalArgumentException("Invalid replaceable component slot");
        this.componentId = componentId;
        this.intendedNameplate = intendedNameplate;
        this.installedPart = installedPart;
    }

    String getComponentId() { return componentId; }
    ResistorNameplate getIntendedNameplate() { return intendedNameplate; }
    PhysicalResistorPart getInstalledPart() { return installedPart; }
    boolean isEmpty() { return installedPart == null; }
    void clear() { installedPart = null; }
    void install(PhysicalResistorPart part) { installedPart = part; }
}