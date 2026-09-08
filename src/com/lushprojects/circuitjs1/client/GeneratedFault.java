package com.lushprojects.circuitjs1.client;

class GeneratedFault {
    private final String id;
    private final GeneratedFaultType type;
    private final String targetComponentId;
    private final String circuitFamilyId;
    private final long selectionSeed;
    private final double healthyValue;
    private final double effectiveValue;

    GeneratedFault(String id, GeneratedFaultType type, String targetComponentId,
            String circuitFamilyId, long selectionSeed) {
        this(id, type, targetComponentId, circuitFamilyId, selectionSeed,
            Double.NaN, Double.NaN);
    }

    GeneratedFault(String id, GeneratedFaultType type, String targetComponentId,
            String circuitFamilyId, long selectionSeed, double healthyValue,
            double effectiveValue) {
        if (id == null || id.length() == 0 || type == null || targetComponentId == null ||
            targetComponentId.length() == 0 || circuitFamilyId == null ||
            circuitFamilyId.length() == 0)
            throw new IllegalArgumentException("Generated fault requires stable identity");
        this.id = id;
        this.type = type;
        this.targetComponentId = targetComponentId;
        this.circuitFamilyId = circuitFamilyId;
        this.selectionSeed = selectionSeed;
        this.healthyValue = healthyValue;
        this.effectiveValue = effectiveValue;
    }

    String getId() { return id; }
    GeneratedFaultType getType() { return type; }
    String getTargetComponentId() { return targetComponentId; }
    String getCircuitFamilyId() { return circuitFamilyId; }
    long getSelectionSeed() { return selectionSeed; }
    double getHealthyValue() { return healthyValue; }
    double getEffectiveValue() { return effectiveValue; }

    /**
     * Stable semantic identity for this diagnostic hypothesis.  The key is
     * deliberately independent of generation order, solver nodes, geometry,
     * and the selection seed.  The fault id is retained because the current
     * generators assign a stable semantic id to each supported hypothesis;
     * the value pair distinguishes value-mutation hypotheses on one owner.
     */
    String getHypothesisKey() {
        StringBuilder result = new StringBuilder("fault-hypothesis-v1|");
        appendField(result, circuitFamilyId);
        appendField(result, type.name());
        appendField(result, targetComponentId);
        appendField(result, id);
        // Use the canonical IEEE-754 bit pattern rather than a runtime's
        // decimal formatter.  JDK and GWT can spell an integral double
        // differently (for example, 330.0), while the bits are the semantic
        // value used by the generated fault.  doubleToLongBits also gives all
        // NaN payloads one deterministic spelling and preserves signed zero.
        appendField(result, Long.toString(Double.doubleToLongBits(healthyValue)));
        appendField(result, Long.toString(Double.doubleToLongBits(effectiveValue)));
        return result.toString();
    }

    private static void appendField(StringBuilder result, String value) {
        result.append(value.length()).append(':').append(value);
    }
}
