package com.lushprojects.circuitjs1.client;

import java.util.HashMap;
import java.util.Vector;

/** Generic physical definitions with power-input metadata kept separate. */
class BoardPhysicalSpecifications {
    private final HashMap<String, BoardPhysicalDefinition> definitions =
        new HashMap<String, BoardPhysicalDefinition>();
    private final HashMap<String, PowerInputNameplate> powerInputs =
        new HashMap<String, PowerInputNameplate>();
    private boolean sealed;

    void addPhysicalDefinition(String componentId, PhysicalSpecification specification,
            PhysicalNameplate nameplate, PhysicalPackage physicalPackage) {
        requireNotSealed();
        BoardPhysicalDefinition definition = new BoardPhysicalDefinition(componentId,
            specification, nameplate, physicalPackage);
        add(definitions, componentId, definition, "physical definition");
    }

    void addPowerInputNameplate(PowerInputNameplate nameplate) {
        requireNotSealed();
        if (nameplate == null) throw new IllegalArgumentException("Missing power input nameplate");
        add(powerInputs, nameplate.getPowerInputId(), nameplate, "power input nameplate");
    }

    void seal() { sealed = true; }
    BoardPhysicalDefinition getPhysicalDefinition(String componentId) {
        return definitions.get(componentId);
    }
    PhysicalSpecification getSpecification(String componentId) {
        BoardPhysicalDefinition definition = definitions.get(componentId);
        return definition == null ? null : definition.getSpecification();
    }
    PhysicalNameplate getNameplate(String componentId) {
        BoardPhysicalDefinition definition = definitions.get(componentId);
        return definition == null ? null : definition.getNameplate();
    }
    PhysicalPackage getPackage(String componentId) {
        BoardPhysicalDefinition definition = definitions.get(componentId);
        return definition == null ? null : definition.getPhysicalPackage();
    }
    Vector<String> getPhysicalComponentIds() { return keys(definitions); }
    Vector<PhysicalSpecification> getSpecifications() {
        Vector<PhysicalSpecification> result = new Vector<PhysicalSpecification>();
        for (BoardPhysicalDefinition definition : definitions.values())
            result.add(definition.getSpecification());
        return result;
    }
    PowerInputNameplate getPowerInputNameplate(String powerInputId) {
        return powerInputs.get(powerInputId);
    }

    private static <T> void add(HashMap<String, T> values, String id, T value, String type) {
        if (id == null || id.length() == 0 || value == null)
            throw new IllegalArgumentException("Missing " + type + " ID");
        if (values.containsKey(id))
            throw new IllegalArgumentException("Duplicate " + type + ": " + id);
        values.put(id, value);
    }

    private static <T> Vector<String> keys(HashMap<String, T> values) {
        Vector<String> result = new Vector<String>();
        result.addAll(values.keySet());
        return result;
    }

    private void requireNotSealed() {
        if (sealed)
            throw new IllegalStateException("Generated physical specifications are immutable");
    }
}
