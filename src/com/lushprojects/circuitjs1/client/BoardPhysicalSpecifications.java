package com.lushprojects.circuitjs1.client;

import java.util.HashMap;

class BoardPhysicalSpecifications {
    private final HashMap<String, ResistorNameplate> resistorNameplates =
        new HashMap<String, ResistorNameplate>();
    private final HashMap<String, PowerInputNameplate> powerInputNameplates =
        new HashMap<String, PowerInputNameplate>();
    private final HashMap<String, DiodeNameplate> diodeNameplates =
        new HashMap<String, DiodeNameplate>();
    private boolean sealed;

    void addResistorNameplate(ResistorNameplate nameplate) {
        requireNotSealed();
        add(resistorNameplates, nameplate.getComponentId(), nameplate, "resistor nameplate");
    }

    void addPowerInputNameplate(PowerInputNameplate nameplate) {
        requireNotSealed();
        add(powerInputNameplates, nameplate.getPowerInputId(), nameplate,
            "power input nameplate");
    }

    void addDiodeNameplate(DiodeNameplate nameplate) {
        requireNotSealed();
        add(diodeNameplates, nameplate.getComponentId(), nameplate, "diode nameplate");
    }

    void seal() { sealed = true; }

    ResistorNameplate getResistorNameplate(String componentId) {
        return resistorNameplates.get(componentId);
    }

    PowerInputNameplate getPowerInputNameplate(String powerInputId) {
        return powerInputNameplates.get(powerInputId);
    }

    DiodeNameplate getDiodeNameplate(String componentId) { return diodeNameplates.get(componentId); }

    private static <T> void add(HashMap<String, T> values, String id, T value, String type) {
        if (id == null || id.length() == 0)
            throw new IllegalArgumentException("Missing " + type + " ID");
        if (values.containsKey(id))
            throw new IllegalArgumentException("Duplicate " + type + ": " + id);
        values.put(id, value);
    }

    private void requireNotSealed() {
        if (sealed)
            throw new IllegalStateException("Generated physical specifications are immutable");
    }
}
