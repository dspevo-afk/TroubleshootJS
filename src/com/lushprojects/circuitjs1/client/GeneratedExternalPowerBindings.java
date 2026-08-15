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

    boolean hasControlsForAllInputs() {
    for (String powerInputId : board.getPowerInputIds()) {
        ExternalPowerSimulationBinding binding = powerBindings.get(powerInputId);
        if (binding == null || !binding.hasControl())
        return false;
    }
    return !board.getPowerInputIds().isEmpty();
    }

    void setConnected(boolean connected) {
    if (!hasControlsForAllInputs())
        throw new IllegalStateException("Generated board power inputs are not fully controllable");
    for (String powerInputId : board.getPowerInputIds())
        powerBindings.get(powerInputId).setConnected(connected);
    }

    boolean areAllConnected() {
    if (!hasControlsForAllInputs())
        return false;
    for (String powerInputId : board.getPowerInputIds()) {
        if (!powerBindings.get(powerInputId).isConnected())
        return false;
    }
    return true;
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