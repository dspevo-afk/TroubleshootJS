package com.lushprojects.circuitjs1.client;

class GeneratedChallengeDefinition {
    private final String id;
    private final String circuitFamilyId;
    private final String topologyVariantId;
    private final long selectionSeed;
    private final String completionText;
    private final GeneratedFault fault;
    private final GeneratedFaultBinding faultBinding;
    private final GeneratedChallengeBehaviorContract behaviorContract;
    private final GeneratedScenarioCatalog<GeneratedObservedBehavior> scenarioCatalog;

    GeneratedChallengeDefinition(String id, String circuitFamilyId, String topologyVariantId,
            long selectionSeed, GeneratedScenarioCatalog<GeneratedObservedBehavior> scenarioCatalog,
            GeneratedFault fault, GeneratedFaultBinding faultBinding,
            GeneratedChallengeBehaviorContract behaviorContract) {
        this(id, circuitFamilyId, topologyVariantId, selectionSeed, scenarioCatalog,
            "Repair verified. Indicator operating normally.", fault, faultBinding,
            behaviorContract);
    }

    GeneratedChallengeDefinition(String id, String circuitFamilyId, String topologyVariantId,
            long selectionSeed, GeneratedScenarioCatalog<GeneratedObservedBehavior> scenarioCatalog,
            String completionText,
            GeneratedFault fault, GeneratedFaultBinding faultBinding,
            GeneratedChallengeBehaviorContract behaviorContract) {
        requireText(id, "challenge ID");
        requireText(circuitFamilyId, "circuit family ID");
        requireText(topologyVariantId, "topology variant ID");
        requireText(completionText, "completion text");
        if (scenarioCatalog == null || fault == null || faultBinding == null || behaviorContract == null)
            throw new IllegalArgumentException("Generated challenge requires fault metadata");
        if (faultBinding.getFault() != fault)
            throw new IllegalArgumentException("Challenge fault binding identity disagrees");
        this.id = id;
        this.circuitFamilyId = circuitFamilyId;
        this.topologyVariantId = topologyVariantId;
        this.selectionSeed = selectionSeed;
        this.completionText = completionText;
        this.fault = fault;
        this.faultBinding = faultBinding;
        this.behaviorContract = behaviorContract;
        this.scenarioCatalog = scenarioCatalog;
    }

    String getId() { return id; }
    String getCircuitFamilyId() { return circuitFamilyId; }
    String getTopologyVariantId() { return topologyVariantId; }
    long getSelectionSeed() { return selectionSeed; }
    String getCompletionText() { return completionText; }
    GeneratedFault getFault() { return fault; }
    GeneratedFaultBinding getFaultBinding() { return faultBinding; }
    GeneratedChallengeBehaviorContract getBehaviorContract() { return behaviorContract; }
    GeneratedScenarioCatalog<GeneratedObservedBehavior> getScenarioCatalog() { return scenarioCatalog; }

    private static void requireText(String value, String name) {
        if (value == null || value.length() == 0)
            throw new IllegalArgumentException("Missing " + name);
    }
}
