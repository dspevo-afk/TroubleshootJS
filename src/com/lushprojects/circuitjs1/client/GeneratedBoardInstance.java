package com.lushprojects.circuitjs1.client;

import java.util.Vector;

class GeneratedBoardInstance {
    private final TroubleshootBoard board;
    private final Vector<CircuitElm> simulationElements;
    private final long seed;
    private final String circuitFamilyId;
    private final String topologyVariantId;
    private final String description;
    private final GeneratedComponentBindings componentBindings;
    private final GeneratedExternalPowerBindings externalPowerBindings;
    private final GeneratedComponentConnectionBindings connectionBindings;
    private final GeneratedChallengeBehaviorContract behaviorContract;
    private final PcbBoardLayout pcbLayout;
    private final BoardPhysicalSpecifications physicalSpecifications;
    private final GeneratedFaultBinding faultBinding;
    private final GeneratedComponentOperationalStates operationalStates;
    private final GeneratedChallengeDefinition challengeDefinition;
    private final GeneratedBoardFamilyState familyState;

    GeneratedBoardInstance(TroubleshootBoard board, Vector<CircuitElm> simulationElements,
            long seed, String circuitFamilyId, String topologyVariantId, String description,
            GeneratedComponentBindings componentBindings,
            GeneratedExternalPowerBindings externalPowerBindings,
            GeneratedComponentConnectionBindings connectionBindings,
            GeneratedChallengeBehaviorContract behaviorContract, PcbBoardLayout pcbLayout,
            BoardPhysicalSpecifications physicalSpecifications, GeneratedFaultBinding faultBinding,
            GeneratedComponentOperationalStates operationalStates,
            GeneratedChallengeDefinition challengeDefinition, GeneratedBoardFamilyState familyState) {
        this.board = board;
        this.simulationElements = new Vector<CircuitElm>(simulationElements);
        this.seed = seed;
        this.circuitFamilyId = circuitFamilyId;
        this.topologyVariantId = topologyVariantId;
        this.description = description;
        this.componentBindings = componentBindings;
        this.externalPowerBindings = externalPowerBindings;
        this.connectionBindings = connectionBindings;
        if (behaviorContract == null)
            throw new IllegalArgumentException("Missing generated challenge behavior contract");
        this.behaviorContract = behaviorContract;
        this.pcbLayout = pcbLayout;
        if (physicalSpecifications == null)
            throw new IllegalArgumentException("Missing generated physical specifications");
        this.physicalSpecifications = physicalSpecifications;
        physicalSpecifications.seal();
        this.faultBinding = faultBinding;
        this.operationalStates = operationalStates;
        this.challengeDefinition = challengeDefinition;
        this.familyState = familyState;
        connectionBindings.validateAgainst(board, this.simulationElements, componentBindings,
            externalPowerBindings, faultBinding);
    }

    TroubleshootBoard getBoard() {
        return board;
    }

    Vector<CircuitElm> getSimulationElements() {
        return new Vector<CircuitElm>(simulationElements);
    }

    void registerRuntimeSimulationElement(CircuitElm element) {
        if (element == null || simulationElements.contains(element))
            throw new IllegalArgumentException("Invalid runtime generated element");
        simulationElements.add(element);
    }

    BoardSimulationBindings getSimulationBindings() {
        return board.getSimulationBindings();
    }

    long getSeed() {
        return seed;
    }

    String getCircuitFamilyId() {
        return circuitFamilyId;
    }

    String getTopologyVariantId() {
        return topologyVariantId;
    }

    String getDescription() {
        return description;
    }

    GeneratedComponentBindings getComponentBindings() {
        return componentBindings;
    }

    GeneratedExternalPowerBindings getExternalPowerBindings() {
        return externalPowerBindings;
    }

    GeneratedComponentConnectionBindings getConnectionBindings() {
        return connectionBindings;
    }

    GeneratedChallengeBehaviorContract getBehaviorContract() {
        return behaviorContract;
    }

    BoardPhysicalSpecifications getPhysicalSpecifications() {
        return physicalSpecifications;
    }

    GeneratedFaultBinding getFaultBinding() { return faultBinding; }
    GeneratedComponentOperationalStates getOperationalStates() { return operationalStates; }
    GeneratedChallengeDefinition getChallengeDefinition() { return challengeDefinition; }
    GeneratedBoardFamilyState getFamilyState() { return familyState; }

    PcbBoardLayout getPcbLayout() {
        return pcbLayout;
    }
}
