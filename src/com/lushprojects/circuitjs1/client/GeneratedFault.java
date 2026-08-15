package com.lushprojects.circuitjs1.client;

class GeneratedFault {
    private final String id;
    private final GeneratedFaultType type;
    private final String targetComponentId;
    private final String circuitFamilyId;
    private final long selectionSeed;

    GeneratedFault(String id, GeneratedFaultType type, String targetComponentId,
            String circuitFamilyId, long selectionSeed) {
        if (id == null || type == null || targetComponentId == null || circuitFamilyId == null)
            throw new IllegalArgumentException("Generated fault requires stable identity");
        this.id = id;
        this.type = type;
        this.targetComponentId = targetComponentId;
        this.circuitFamilyId = circuitFamilyId;
        this.selectionSeed = selectionSeed;
    }

    String getId() { return id; }
    GeneratedFaultType getType() { return type; }
    String getTargetComponentId() { return targetComponentId; }
    String getCircuitFamilyId() { return circuitFamilyId; }
    long getSelectionSeed() { return selectionSeed; }
}
