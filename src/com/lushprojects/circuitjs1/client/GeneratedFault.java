package com.lushprojects.circuitjs1.client;

class GeneratedFault {
    private final String id;
    private final GeneratedFaultType type;
    private final String targetComponentId;
    private final String circuitFamilyId;
    private final long selectionSeed;

    GeneratedFault(String id, GeneratedFaultType type, String targetComponentId,
            String circuitFamilyId, long selectionSeed) {
        if (id == null || id.length() == 0 || type == null || targetComponentId == null ||
            targetComponentId.length() == 0 || circuitFamilyId == null ||
            circuitFamilyId.length() == 0)
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
