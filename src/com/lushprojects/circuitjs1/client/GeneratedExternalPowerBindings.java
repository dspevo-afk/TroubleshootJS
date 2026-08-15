package com.lushprojects.circuitjs1.client;

import java.util.HashMap;
import java.util.Vector;

class GeneratedExternalPowerBindings {
    private final TroubleshootBoard board;
    private final HashMap<String, ExternalPowerSimulationBinding> powerBindings =
        new HashMap<String, ExternalPowerSimulationBinding>();

    GeneratedExternalPowerBindings(TroubleshootBoard board) {
        this.board = board;
    }

    void bindPowerInput(String powerInputId, ExternalPowerSimulationBinding binding) {
        if (board.getPowerInput(powerInputId) == null)
            throw new IllegalArgumentException("Unknown board power input: " + powerInputId);
        if (binding == null)
            throw new IllegalArgumentException("Missing external power simulation binding: " + powerInputId);
        if (powerBindings.containsKey(powerInputId))
            throw new IllegalArgumentException("Duplicate external power simulation binding: " + powerInputId);
        powerBindings.put(powerInputId, binding);
    }

    ExternalPowerSimulationBinding getBinding(String powerInputId) {
        ExternalPowerSimulationBinding binding = powerBindings.get(powerInputId);
        if (binding == null)
            throw new IllegalArgumentException("Unknown external power simulation binding: " + powerInputId);
        return binding;
    }

    void validateElementsAreOwnedBy(Vector<CircuitElm> simulationElements) {
        for (String powerInputId : powerBindings.keySet()) {
            for (CircuitElm element : powerBindings.get(powerInputId).getBackingElements()) {
                if (!simulationElements.contains(element))
                    throw new IllegalStateException("Power binding is not owned by generated board: " + powerInputId);
            }
        }
    }
}