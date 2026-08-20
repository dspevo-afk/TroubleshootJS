package com.lushprojects.circuitjs1.client;

import java.util.Vector;
import java.util.Collections;

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
    private final PhysicalBoardRuntime physicalRuntime;
    private final GeneratedFaultBinding faultBinding;
    private final Vector<GeneratedFaultCandidate> faultCandidates;
    private final GeneratedComponentOperationalStates operationalStates;
    private final GeneratedChallengeDefinition challengeDefinition;
    private final GeneratedBoardFamilyState familyState;
    private final GeneratedTemporalBehavior temporalBehavior;
    private final boolean developerOnlyFaultRoute;
    private final GeneratedDiagnosticSolvabilityContract diagnosticSolvabilityContract;

    GeneratedBoardInstance(TroubleshootBoard board, Vector<CircuitElm> simulationElements,
            long seed, String circuitFamilyId, String topologyVariantId, String description,
            GeneratedComponentBindings componentBindings,
            GeneratedExternalPowerBindings externalPowerBindings,
            GeneratedComponentConnectionBindings connectionBindings,
            GeneratedChallengeBehaviorContract behaviorContract, PcbBoardLayout pcbLayout,
            BoardPhysicalSpecifications physicalSpecifications, GeneratedFaultBinding faultBinding,
            GeneratedComponentOperationalStates operationalStates,
            GeneratedChallengeDefinition challengeDefinition, GeneratedBoardFamilyState familyState,
            PhysicalBoardRuntime physicalRuntime) {
        this(board, simulationElements, seed, circuitFamilyId, topologyVariantId, description,
            componentBindings, externalPowerBindings, connectionBindings, behaviorContract,
            pcbLayout, physicalSpecifications, faultBinding, operationalStates,
            challengeDefinition, familyState, physicalRuntime, null, false);
    }

    GeneratedBoardInstance(TroubleshootBoard board, Vector<CircuitElm> simulationElements,
            long seed, String circuitFamilyId, String topologyVariantId, String description,
            GeneratedComponentBindings componentBindings,
            GeneratedExternalPowerBindings externalPowerBindings,
            GeneratedComponentConnectionBindings connectionBindings,
            GeneratedChallengeBehaviorContract behaviorContract, PcbBoardLayout pcbLayout,
            BoardPhysicalSpecifications physicalSpecifications, GeneratedFaultBinding faultBinding,
            GeneratedComponentOperationalStates operationalStates,
            GeneratedChallengeDefinition challengeDefinition, GeneratedBoardFamilyState familyState,
            PhysicalBoardRuntime physicalRuntime, GeneratedTemporalBehavior temporalBehavior) {
        this(board, simulationElements, seed, circuitFamilyId, topologyVariantId, description,
            componentBindings, externalPowerBindings, connectionBindings, behaviorContract,
            pcbLayout, physicalSpecifications, faultBinding, operationalStates,
            challengeDefinition, familyState, physicalRuntime, temporalBehavior, false);
    }

    GeneratedBoardInstance(TroubleshootBoard board, Vector<CircuitElm> simulationElements,
            long seed, String circuitFamilyId, String topologyVariantId, String description,
            GeneratedComponentBindings componentBindings,
            GeneratedExternalPowerBindings externalPowerBindings,
            GeneratedComponentConnectionBindings connectionBindings,
            GeneratedChallengeBehaviorContract behaviorContract, PcbBoardLayout pcbLayout,
            BoardPhysicalSpecifications physicalSpecifications, GeneratedFaultBinding faultBinding,
            GeneratedComponentOperationalStates operationalStates,
            GeneratedChallengeDefinition challengeDefinition, GeneratedBoardFamilyState familyState,
            PhysicalBoardRuntime physicalRuntime, GeneratedTemporalBehavior temporalBehavior,
            boolean developerOnlyFaultRoute) {
        this(board, simulationElements, seed, circuitFamilyId, topologyVariantId, description,
            componentBindings, externalPowerBindings, connectionBindings, behaviorContract,
            pcbLayout, physicalSpecifications, faultBinding, operationalStates,
            challengeDefinition, familyState, physicalRuntime, temporalBehavior,
            developerOnlyFaultRoute, null);
    }

    GeneratedBoardInstance(TroubleshootBoard board, Vector<CircuitElm> simulationElements,
            long seed, String circuitFamilyId, String topologyVariantId, String description,
            GeneratedComponentBindings componentBindings,
            GeneratedExternalPowerBindings externalPowerBindings,
            GeneratedComponentConnectionBindings connectionBindings,
            GeneratedChallengeBehaviorContract behaviorContract, PcbBoardLayout pcbLayout,
            BoardPhysicalSpecifications physicalSpecifications, GeneratedFaultBinding faultBinding,
            GeneratedComponentOperationalStates operationalStates,
            GeneratedChallengeDefinition challengeDefinition, GeneratedBoardFamilyState familyState,
            PhysicalBoardRuntime physicalRuntime, GeneratedTemporalBehavior temporalBehavior,
            boolean developerOnlyFaultRoute, Vector<GeneratedFaultCandidate> faultCandidates) {
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
        if (physicalRuntime == null)
            throw new IllegalArgumentException("Missing generated physical board runtime");
        this.physicalRuntime = physicalRuntime;
        if (pcbLayout != null)
            physicalRuntime.bindGeometryRealizations(pcbLayout);
        physicalRuntime.validate();
        this.faultBinding = faultBinding;
        this.faultCandidates = faultCandidates == null ?
            new Vector<GeneratedFaultCandidate>() :
            new Vector<GeneratedFaultCandidate>(faultCandidates);
        this.operationalStates = operationalStates;
        this.challengeDefinition = challengeDefinition;
        this.familyState = familyState;
        this.temporalBehavior = temporalBehavior;
        this.developerOnlyFaultRoute = developerOnlyFaultRoute;
        this.diagnosticSolvabilityContract = GeneratedDiagnosticSolvabilityContract
            .forGeneratedBoard(circuitFamilyId, topologyVariantId, seed, this.faultCandidates);
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

    PhysicalBoardRuntime getPhysicalBoardRuntime() {
        return physicalRuntime;
    }

    GeneratedFaultBinding getFaultBinding() { return faultBinding; }
    Vector<GeneratedFaultCandidate> getFaultCandidates() {
        return new Vector<GeneratedFaultCandidate>(faultCandidates);
    }
    Vector<String> getAdmittedFaultPhysicalOwnerIds() {
        return GeneratedFaultServiceabilityAdmission.getPhysicalOwnerIds(faultCandidates);
    }
    int getAdmittedFaultPhysicalOwnerCount() {
        return getAdmittedFaultPhysicalOwnerIds().size();
    }
    GeneratedFaultServiceability getFaultServiceability() {
        return faultBinding == null ? null : faultBinding.getServiceability();
    }
    GeneratedFaultLocus getFaultLocus() {
        return faultBinding == null ? null : faultBinding.getFaultLocus();
    }
    Vector<String> getFaultPhysicalOwnerIds() {
        Vector<String> result = new Vector<String>();
        GeneratedFaultLocus locus = getFaultLocus();
        if (locus != null && getFaultServiceability() != null &&
                getFaultServiceability().isAdmissible())
            result.add(locus.getOwnerId());
        Collections.sort(result);
        return result;
    }
    int getFaultPhysicalOwnerCount() { return getFaultPhysicalOwnerIds().size(); }
    boolean isDeveloperOnlyFaultRoute() { return developerOnlyFaultRoute; }
    GeneratedDiagnosticSolvabilityContract getDiagnosticSolvabilityContract() {
        return diagnosticSolvabilityContract;
    }
    GeneratedComponentOperationalStates getOperationalStates() { return operationalStates; }
    GeneratedChallengeDefinition getChallengeDefinition() { return challengeDefinition; }
    GeneratedBoardFamilyState getFamilyState() { return familyState; }
    GeneratedTemporalBehavior getTemporalBehavior() { return temporalBehavior; }

    GeneratedBoardOperationCatalog getOperationCatalog() {
        return familyState.getOperationCatalog();
    }

    GeneratedCustomerRetestProfile getCustomerRetestProfile() {
        return familyState.getCustomerRetestProfile();
    }

    GeneratedCustomerRetestResult invokeOperation(String stableId, CirSim sim) {
        return getOperationCatalog().invoke(stableId, sim, this);
    }

    PcbBoardLayout getPcbLayout() {
        return pcbLayout;
    }
}
