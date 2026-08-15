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
    private final GeneratedBoardValidator familyValidator;

    GeneratedBoardInstance(TroubleshootBoard board, Vector<CircuitElm> simulationElements,
            long seed, String circuitFamilyId, String topologyVariantId, String description,
            GeneratedComponentBindings componentBindings,
            GeneratedExternalPowerBindings externalPowerBindings,
            GeneratedBoardValidator familyValidator) {
        this.board = board;
        this.simulationElements = new Vector<CircuitElm>(simulationElements);
        this.seed = seed;
        this.circuitFamilyId = circuitFamilyId;
        this.topologyVariantId = topologyVariantId;
        this.description = description;
        this.componentBindings = componentBindings;
        this.externalPowerBindings = externalPowerBindings;
        this.familyValidator = familyValidator;
    }

    TroubleshootBoard getBoard() {
        return board;
    }

    Vector<CircuitElm> getSimulationElements() {
        return new Vector<CircuitElm>(simulationElements);
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

    GeneratedBoardValidator getFamilyValidator() {
        return familyValidator;
    }
}
