package com.lushprojects.circuitjs1.client;

import java.util.HashMap;
import java.util.Vector;

class GeneratedComponentConnectionBindings {
    private final TroubleshootBoard board;
    private final HashMap<String, GeneratedComponentConnectionBinding> bindings =
        new HashMap<String, GeneratedComponentConnectionBinding>();

    GeneratedComponentConnectionBindings(TroubleshootBoard board) {
        this.board = board;
    }

    void bind(String componentId, String padId, CircuitMeasurementEndpoint boardEndpoint,
            CircuitMeasurementEndpoint componentEndpoint, CircuitElm connectionElement) {
        BoardComponent component = board.getComponent(componentId);
        BoardPad pad = board.getPad(padId);
        if (component == null || pad == null || !componentId.equals(pad.getComponentId()))
            throw new IllegalArgumentException("Invalid generated component connection: " + componentId + "/" + padId);
        if (boardEndpoint == null || componentEndpoint == null || connectionElement == null ||
                bindings.containsKey(padId))
            throw new IllegalArgumentException("Invalid or duplicate generated connection: " + padId);
        bindings.put(padId, new GeneratedComponentConnectionBinding(componentId, padId,
            boardEndpoint, componentEndpoint, connectionElement));
    }

    GeneratedComponentConnectionBinding get(String componentId, String padId) {
        GeneratedComponentConnectionBinding binding = bindings.get(padId);
        if (binding == null || !componentId.equals(binding.getComponentId()))
            throw new IllegalArgumentException("Unknown component connection: " + componentId + "/" + padId);
        return binding;
    }

    Vector<GeneratedComponentConnectionBinding> getForComponent(String componentId) {
        Vector<GeneratedComponentConnectionBinding> result = new Vector<GeneratedComponentConnectionBinding>();
        for (GeneratedComponentConnectionBinding binding : bindings.values())
            if (componentId.equals(binding.getComponentId()))
                result.add(binding);
        if (result.isEmpty())
            throw new IllegalArgumentException("Component has no detachable connections: " + componentId);
        return result;
    }

    Vector<GeneratedComponentConnectionBinding> getAll() {
        return new Vector<GeneratedComponentConnectionBinding>(bindings.values());
    }

    boolean isConnectionElement(CircuitElm element) {
        for (GeneratedComponentConnectionBinding binding : bindings.values())
            if (binding.getConnectionElement() == element)
                return true;
        return false;
    }

    void validateAgainst(TroubleshootBoard board, Vector<CircuitElm> simulationElements) {
        HashMap<CircuitElm, Boolean> connectionElements = new HashMap<CircuitElm, Boolean>();
        for (GeneratedComponentConnectionBinding binding : bindings.values()) {
            if (board.getSimulationBindings().getEndpoint(binding.getPadId()) !=
                    binding.getBoardEndpoint())
                throw new IllegalStateException("Connection board endpoint does not match pad: " +
                    binding.getPadId());
            validateEndpoint(binding.getBoardEndpoint(), simulationElements, binding.getPadId());
            validateEndpoint(binding.getComponentEndpoint(), simulationElements, binding.getPadId());
            if (!simulationElements.contains(binding.getConnectionElement()) ||
                    connectionElements.put(binding.getConnectionElement(), Boolean.TRUE) != null)
                throw new IllegalStateException("Invalid or shared detachable connection: " +
                    binding.getPadId());
        }
    }

    private void validateEndpoint(CircuitMeasurementEndpoint endpoint,
            Vector<CircuitElm> simulationElements, String padId) {
        if (!(endpoint instanceof CircuitPostMeasurementEndpoint))
            throw new IllegalStateException("Unsupported detachable endpoint: " + padId);
        CircuitPostMeasurementEndpoint post = (CircuitPostMeasurementEndpoint) endpoint;
        if (!simulationElements.contains(post.getElement()) || post.getPostIndex() < 0 ||
                post.getPostIndex() >= post.getElement().getPostCount())
            throw new IllegalStateException("Invalid detachable endpoint: " + padId);
    }
}