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
        BoardComponent component = board.getComponent(componentId);
        if (component != null) {
            for (String padId : component.getPadIds()) {
                GeneratedComponentConnectionBinding binding = bindings.get(padId);
                if (binding != null)
                    result.add(binding);
            }
        }
        if (result.isEmpty())
            throw new IllegalArgumentException("Component has no detachable connections: " + componentId);
        return result;
    }

    Vector<GeneratedComponentConnectionBinding> getForComponentOrEmpty(String componentId) {
        Vector<GeneratedComponentConnectionBinding> result = new Vector<GeneratedComponentConnectionBinding>();
        BoardComponent component = board.getComponent(componentId);
        if (component == null)
            return result;
        for (String padId : component.getPadIds()) {
            GeneratedComponentConnectionBinding binding = bindings.get(padId);
            if (binding != null)
                result.add(binding);
        }
        return result;
    }

    Vector<GeneratedComponentConnectionBinding> getAll() {
        Vector<GeneratedComponentConnectionBinding> result = new Vector<GeneratedComponentConnectionBinding>();
        for (String padId : board.getPadIds()) {
            GeneratedComponentConnectionBinding binding = bindings.get(padId);
            if (binding != null)
                result.add(binding);
        }
        return result;
    }

    boolean isConnectionElement(CircuitElm element) {
        for (GeneratedComponentConnectionBinding binding : bindings.values())
            if (binding.getConnectionElement() == element)
                return true;
        return false;
    }

    void validateAgainst(TroubleshootBoard board, Vector<CircuitElm> simulationElements,
            GeneratedComponentBindings componentBindings,
            GeneratedExternalPowerBindings externalPowerBindings, GeneratedFaultBinding faultBinding) {
        HashMap<CircuitElm, Boolean> connectionElements = new HashMap<CircuitElm, Boolean>();
        for (GeneratedComponentConnectionBinding binding : bindings.values()) {
            if (board.getSimulationBindings().getEndpoint(binding.getPadId()) !=
                    binding.getBoardEndpoint())
                throw new IllegalStateException("Connection board endpoint does not match pad: " +
                    binding.getPadId());
            CircuitPostMeasurementEndpoint boardPost = validateEndpoint(binding.getBoardEndpoint(),
                simulationElements, binding.getPadId());
                CircuitPostMeasurementEndpoint componentPost = validateEndpoint(
                    binding.getComponentEndpoint(), simulationElements, binding.getPadId());
                if (!componentBindings.isElementBoundToComponent(binding.getComponentId(),
                    componentPost.getElement()) && !isFaultEndpoint(binding, componentPost,
                    componentBindings, faultBinding))
                throw new IllegalStateException("Connection component endpoint is not owned by component: " +
                    binding.getPadId());
            if (boardPost.getElement() == binding.getConnectionElement() ||
                    componentBindings.isElementBoundToComponent(binding.getComponentId(),
                        boardPost.getElement()))
                throw new IllegalStateException("Connection board endpoint is not persistent: " +
                    binding.getPadId());
            if (externalPowerBindings.isBackingElement(binding.getConnectionElement()))
                throw new IllegalStateException("Detachable connection is external power infrastructure: " +
                    binding.getPadId());
            if (boardPost.getElement() == componentPost.getElement() &&
                    boardPost.getPostIndex() == componentPost.getPostIndex())
                throw new IllegalStateException("Connection endpoints are not separable: " +
                    binding.getPadId());
            Point boardPoint = boardPost.getElement().getPost(boardPost.getPostIndex());
            Point componentPoint = componentPost.getElement().getPost(componentPost.getPostIndex());
            if (boardPoint.equals(componentPoint) ||
                    !touchesPoint(binding.getConnectionElement(), boardPoint) ||
                    !touchesPoint(binding.getConnectionElement(), componentPoint))
                throw new IllegalStateException("Detachable connection does not separate its endpoints: " +
                    binding.getPadId());
            if (!simulationElements.contains(binding.getConnectionElement()) ||
                    connectionElements.put(binding.getConnectionElement(), Boolean.TRUE) != null)
                throw new IllegalStateException("Invalid or shared detachable connection: " +
                    binding.getPadId());
        }
    }

    private boolean isFaultEndpoint(GeneratedComponentConnectionBinding connection,
            CircuitPostMeasurementEndpoint endpoint, GeneratedComponentBindings componentBindings,
            GeneratedFaultBinding faultBinding) {
        if (faultBinding == null || !faultBinding.getFault().getTargetComponentId().equals(
                connection.getComponentId()))
            return false;
        try {
            CircuitElm backing = componentBindings.getSingleElement(connection.getComponentId());
            return faultBinding.isPublicTerminal(backing, endpoint, 1);
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    private CircuitPostMeasurementEndpoint validateEndpoint(CircuitMeasurementEndpoint endpoint,
            Vector<CircuitElm> simulationElements, String padId) {
        if (!(endpoint instanceof CircuitPostMeasurementEndpoint))
            throw new IllegalStateException("Unsupported detachable endpoint: " + padId);
        CircuitPostMeasurementEndpoint post = (CircuitPostMeasurementEndpoint) endpoint;
        if (!simulationElements.contains(post.getElement()) || post.getPostIndex() < 0 ||
                post.getPostIndex() >= post.getElement().getPostCount())
            throw new IllegalStateException("Invalid detachable endpoint: " + padId);
        return post;
    }

    private boolean touchesPoint(CircuitElm element, Point point) {
        for (int postIndex = 0; postIndex < element.getPostCount(); postIndex++) {
            if (point.equals(element.getPost(postIndex)))
                return true;
        }
        return false;
    }
}
