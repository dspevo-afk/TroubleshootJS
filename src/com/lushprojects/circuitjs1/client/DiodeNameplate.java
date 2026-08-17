package com.lushprojects.circuitjs1.client;

final class DiodeNameplate implements PhysicalSpecification {
    private final String componentId;
    private final String displayName;
    private final String modelName;

    DiodeNameplate(String componentId, String displayName, String modelName) {
        if (componentId == null || componentId.length() == 0 || displayName == null ||
                displayName.length() == 0 || modelName == null || modelName.length() == 0)
            throw new IllegalArgumentException("Invalid diode nameplate");
        this.componentId = componentId;
        this.displayName = displayName;
        this.modelName = modelName;
    }

    String getComponentId() { return componentId; }
    public String getSpecificationId() { return componentId; }
    String getDisplayName() { return displayName; }
    String getModelName() { return modelName; }
}
