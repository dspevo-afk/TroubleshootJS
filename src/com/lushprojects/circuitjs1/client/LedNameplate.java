package com.lushprojects.circuitjs1.client;

final class LedNameplate {
    private final String componentId;
    private final String displayName;
    private final String modelName;
    private final double red;
    private final double green;
    private final double blue;

    LedNameplate(String componentId, String displayName, String modelName,
            double red, double green, double blue) {
        if (componentId == null || componentId.length() == 0 || displayName == null ||
                displayName.length() == 0 || modelName == null || modelName.length() == 0 ||
                red < 0 || red > 1 || green < 0 || green > 1 || blue < 0 || blue > 1)
            throw new IllegalArgumentException("Invalid LED nameplate");
        this.componentId = componentId;
        this.displayName = displayName;
        this.modelName = modelName;
        this.red = red;
        this.green = green;
        this.blue = blue;
    }

    String getComponentId() { return componentId; }
    String getDisplayName() { return displayName; }
    String getModelName() { return modelName; }
    double getRed() { return red; }
    double getGreen() { return green; }
    double getBlue() { return blue; }
}
