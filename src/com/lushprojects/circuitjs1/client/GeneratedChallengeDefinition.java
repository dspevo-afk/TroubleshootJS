package com.lushprojects.circuitjs1.client;

class GeneratedChallengeDefinition {
    private final String id;
    private final String circuitFamilyId;
    private final String topologyVariantId;
    private final long selectionSeed;
    private final String complaintId;
    private final String complaintText;
    private final String completionText;
    private final GeneratedFault fault;
    private final GeneratedFaultBinding faultBinding;
    private final GeneratedChallengeBehaviorContract behaviorContract;

    GeneratedChallengeDefinition(String id, String circuitFamilyId, String topologyVariantId,
            long selectionSeed, String complaintId, String complaintText, GeneratedFault fault,
            GeneratedFaultBinding faultBinding, GeneratedChallengeBehaviorContract behaviorContract) {
        this(id, circuitFamilyId, topologyVariantId, selectionSeed, complaintId, complaintText,
            "Repair verified. Indicator operating normally.", fault, faultBinding,
            behaviorContract);
    }

    GeneratedChallengeDefinition(String id, String circuitFamilyId, String topologyVariantId,
            long selectionSeed, String complaintId, String complaintText, String completionText,
            GeneratedFault fault, GeneratedFaultBinding faultBinding,
            GeneratedChallengeBehaviorContract behaviorContract) {
        requireText(id, "challenge ID");
        requireText(circuitFamilyId, "circuit family ID");
        requireText(topologyVariantId, "topology variant ID");
        requireText(complaintId, "complaint ID");
        requireText(complaintText, "complaint text");
        requireText(completionText, "completion text");
        if (fault == null || faultBinding == null || behaviorContract == null)
            throw new IllegalArgumentException("Generated challenge requires fault metadata");
        if (faultBinding.getFault() != fault)
            throw new IllegalArgumentException("Challenge fault binding identity disagrees");
        this.id = id;
        this.circuitFamilyId = circuitFamilyId;
        this.topologyVariantId = topologyVariantId;
        this.selectionSeed = selectionSeed;
        this.complaintId = complaintId;
        this.complaintText = complaintText;
        this.completionText = completionText;
        this.fault = fault;
        this.faultBinding = faultBinding;
        this.behaviorContract = behaviorContract;
    }

    String getId() { return id; }
    String getCircuitFamilyId() { return circuitFamilyId; }
    String getTopologyVariantId() { return topologyVariantId; }
    long getSelectionSeed() { return selectionSeed; }
    String getComplaintId() { return complaintId; }
    String getComplaintText() { return complaintText; }
    String getCompletionText() { return completionText; }
    GeneratedFault getFault() { return fault; }
    GeneratedFaultBinding getFaultBinding() { return faultBinding; }
    GeneratedChallengeBehaviorContract getBehaviorContract() { return behaviorContract; }

    private static void requireText(String value, String name) {
        if (value == null || value.length() == 0)
            throw new IllegalArgumentException("Missing " + name);
    }
}
