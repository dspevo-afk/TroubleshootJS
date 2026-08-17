package com.lushprojects.circuitjs1.client;

/** Type-neutral physical definition for one stable board component identity. */
final class BoardPhysicalDefinition {
    private final String componentId;
    private final PhysicalSpecification specification;
    private final PhysicalNameplate nameplate;
    private final PhysicalPackage physicalPackage;

    BoardPhysicalDefinition(String componentId, PhysicalSpecification specification,
            PhysicalNameplate nameplate, PhysicalPackage physicalPackage) {
        if (componentId == null || componentId.length() == 0 || specification == null ||
                nameplate == null || physicalPackage == null)
            throw new IllegalArgumentException("Invalid physical definition: " + componentId);
        this.componentId = componentId;
        this.specification = specification;
        this.nameplate = nameplate;
        this.physicalPackage = physicalPackage;
    }

    String getComponentId() { return componentId; }
    PhysicalSpecification getSpecification() { return specification; }
    PhysicalNameplate getNameplate() { return nameplate; }
    PhysicalPackage getPhysicalPackage() { return physicalPackage; }
}
