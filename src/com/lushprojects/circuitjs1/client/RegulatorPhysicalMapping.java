package com.lushprojects.circuitjs1.client;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Vector;

/**
 * Physical/electrical binding for the bounded four-terminal regulator models.
 *
 * <p>This is deliberately a small construction helper rather than a provider
 * or a gameplay registry.  It admits a regulator only when the board carries
 * the declared physical package and each exposed pad is bound to the exact
 * CircuitJS regulator element and post expected for that terminal.  A package
 * name or a terminal list alone cannot create a mapping.</p>
 */
final class RegulatorPhysicalMapping {
    static final int VERSION = 1;

    private final TroubleshootBoard board;
    private final String componentId;
    private final PhysicalPackage physicalPackage;
    private final AbstractRailRegulatorElm regulator;
    private final Vector<String> terminalIds;
    private final Map<String, String> padIdByTerminal;
    private final Map<String, Integer> postIndexByTerminal;
    private final Map<String, CircuitPostMeasurementEndpoint> endpointByTerminal;
    private final boolean requiresDirectBoardBindings;

    private RegulatorPhysicalMapping(TroubleshootBoard board, String componentId,
            PhysicalPackage physicalPackage, AbstractRailRegulatorElm regulator,
            Vector<String> terminalIds, Map<String, String> padIdByTerminal,
            Map<String, Integer> postIndexByTerminal,
            Map<String, CircuitPostMeasurementEndpoint> endpointByTerminal,
            boolean requiresDirectBoardBindings) {
        this.board = board;
        this.componentId = componentId;
        this.physicalPackage = physicalPackage;
        this.regulator = regulator;
        this.terminalIds = new Vector<String>(terminalIds);
        this.padIdByTerminal = immutableCopy(padIdByTerminal);
        this.postIndexByTerminal = immutableCopy(postIndexByTerminal);
        this.endpointByTerminal = immutableCopy(endpointByTerminal);
        this.requiresDirectBoardBindings = requiresDirectBoardBindings;
    }

    /**
     * Validates and installs the four board-pad bindings for one regulator.
     * Existing bindings are accepted only when they are the exact expected
     * regulator post bindings; wrong elements, posts, or endpoint kinds fail
     * before any missing binding is installed.
     */
    static RegulatorPhysicalMapping bind(TroubleshootBoard board, String componentId,
            AbstractRailRegulatorElm regulator) {
        return create(board, componentId, regulator, true);
    }

    /**
     * Validates the physical terminal-to-regulator-post contract without
     * claiming that a detachable board pad is the component post.  Generated
     * serviceable boards use this form: their immutable board endpoint is
     * copper/source infrastructure and a physical lead bridges it to U1.
     */
    static RegulatorPhysicalMapping mapComponentTerminals(TroubleshootBoard board,
            String componentId, AbstractRailRegulatorElm regulator) {
        return create(board, componentId, regulator, false);
    }

    private static RegulatorPhysicalMapping create(TroubleshootBoard board, String componentId,
            AbstractRailRegulatorElm regulator, boolean requireDirectBoardBindings) {
        if (board == null || componentId == null || componentId.length() == 0 ||
                regulator == null)
            throw invalid("board, componentId and regulator are required");

        Vector<String> terminals = expectedTerminalIds(regulator.getContract());
        BoardComponent component = board.getComponent(componentId);
        if (component == null)
            throw invalid("unknown regulator component: " + componentId);
        PhysicalPackage physical = component.getPhysicalPackage();
        if (physical == null || !physical.isEquivalentTo(PhysicalPackages.TO220_REGULATOR_4))
            throw invalid("component does not use the declared four-lead regulator package");
        if (!physical.getTerminalIds().equals(terminals) || component.getPadIds().size() !=
                terminals.size())
            throw invalid("regulator package/pad inventory does not match the rail terminals");
        if (regulator.getPostCount() != terminals.size())
            throw invalid("regulator post count does not match the physical terminal count");

        LinkedHashMap<String, String> pads = new LinkedHashMap<String, String>();
        LinkedHashMap<String, Integer> posts = new LinkedHashMap<String, Integer>();
        LinkedHashMap<String, CircuitPostMeasurementEndpoint> endpoints =
            new LinkedHashMap<String, CircuitPostMeasurementEndpoint>();
        Vector<Integer> usedPosts = new Vector<Integer>();
        BoardSimulationBindings bindings = board.getSimulationBindings();

        for (int index = 0; index < terminals.size(); index++) {
            String terminalId = terminals.get(index);
            String padId = componentId + "." + terminalId;
            BoardPad pad = board.getPad(padId);
            if (pad == null || !componentId.equals(pad.getComponentId()) ||
                    !terminalId.equals(pad.getTerminalId()) ||
                    !component.getPadIds().contains(padId))
                throw invalid("missing or mismatched regulator pad: " + padId);

            int postIndex = postIndexFor(terminalId);
            if (usedPosts.contains(Integer.valueOf(postIndex)))
                throw invalid("regulator post is mapped more than once: " + postIndex);
            usedPosts.add(Integer.valueOf(postIndex));
            if (regulator.getPost(postIndex) == null)
                throw invalid("regulator post is not initialized: " + postIndex);

            CircuitPostMeasurementEndpoint endpoint = new CircuitPostMeasurementEndpoint(regulator,
                postIndex);
            CircuitMeasurementEndpoint existing = bindings.getEndpoint(padId);
            if (requireDirectBoardBindings && existing != null) {
                if (!(existing instanceof CircuitPostMeasurementEndpoint))
                    throw invalid("regulator pad is not bound to a CircuitJS post: " + padId);
                CircuitPostMeasurementEndpoint existingPost =
                    (CircuitPostMeasurementEndpoint) existing;
                if (existingPost.getElement() != regulator ||
                        existingPost.getPostIndex() != postIndex)
                    throw invalid("regulator pad is bound to the wrong CircuitJS post: " + padId);
                endpoint = existingPost;
            }
            pads.put(terminalId, padId);
            posts.put(terminalId, Integer.valueOf(postIndex));
            endpoints.put(terminalId, endpoint);
        }

        // Check for extra component pads before mutating shared board bindings.
        for (String padId : component.getPadIds())
            if (!pads.containsValue(padId))
                throw invalid("regulator component has an undeclared pad: " + padId);

        if (requireDirectBoardBindings)
            for (String terminalId : terminals) {
                String padId = pads.get(terminalId);
                if (bindings.getEndpoint(padId) == null)
                    bindings.bindPad(padId, endpoints.get(terminalId));
            }

        RegulatorPhysicalMapping result = new RegulatorPhysicalMapping(board, componentId,
            physical, regulator, terminals, pads, posts, endpoints,
            requireDirectBoardBindings);
        result.verifyCurrentBindings();
        return result;
    }

    String getComponentId() { return componentId; }
    PhysicalPackage getPhysicalPackage() { return physicalPackage; }
    AbstractRailRegulatorElm getRegulator() { return regulator; }
    int getTerminalCount() { return terminalIds.size(); }
    Vector<String> getTerminalIds() { return new Vector<String>(terminalIds); }
    String getPadId(String terminalId) { return padIdByTerminal.get(terminalId); }
    Integer getPostIndex(String terminalId) { return postIndexByTerminal.get(terminalId); }
    CircuitPostMeasurementEndpoint getEndpoint(String terminalId) {
        return endpointByTerminal.get(terminalId);
    }

    /** Fails if a later lifecycle operation removed or replaced a live binding. */
    void verifyCurrentBindings() {
        BoardSimulationBindings bindings = board.getSimulationBindings();
        Vector<Integer> seenPosts = new Vector<Integer>();
        for (String terminalId : terminalIds) {
            String padId = padIdByTerminal.get(terminalId);
            CircuitPostMeasurementEndpoint expected = endpointByTerminal.get(terminalId);
            CircuitMeasurementEndpoint actual = bindings.getEndpoint(padId);
            Integer postIndex = postIndexByTerminal.get(terminalId);
            if ((requiresDirectBoardBindings && actual != expected) ||
                    expected.getElement() != regulator ||
                    expected.getPostIndex() != postIndex.intValue() ||
                    regulator.getPost(postIndex.intValue()) == null ||
                    seenPosts.contains(postIndex))
                throw invalid("regulator physical binding is stale or non-bijective: " + terminalId);
            seenPosts.add(postIndex);
        }
    }

    private static Vector<String> expectedTerminalIds(RailRegulationContract contract) {
        if (contract == null || !RailRegulationContract.INPUT_TERMINAL.equals(
                contract.getInputTerminalId()) ||
                !RailRegulationContract.OUTPUT_TERMINAL.equals(contract.getOutputTerminalId()) ||
                !RailRegulationContract.RETURN_TERMINAL.equals(contract.getReturnTerminalId()) ||
                !RailRegulationContract.ENABLE_TERMINAL.equals(contract.getEnableTerminalId()))
            throw invalid("regulator contract does not use the declared physical terminal IDs");
        Vector<String> result = new Vector<String>();
        result.add(RailRegulationContract.INPUT_TERMINAL);
        result.add(RailRegulationContract.OUTPUT_TERMINAL);
        result.add(RailRegulationContract.RETURN_TERMINAL);
        result.add(RailRegulationContract.ENABLE_TERMINAL);
        return result;
    }

    private static int postIndexFor(String terminalId) {
        if (RailRegulationContract.INPUT_TERMINAL.equals(terminalId))
            return AbstractRailRegulatorElm.INPUT_POST;
        if (RailRegulationContract.OUTPUT_TERMINAL.equals(terminalId))
            return AbstractRailRegulatorElm.OUTPUT_POST;
        if (RailRegulationContract.RETURN_TERMINAL.equals(terminalId))
            return AbstractRailRegulatorElm.RETURN_POST;
        if (RailRegulationContract.ENABLE_TERMINAL.equals(terminalId))
            return AbstractRailRegulatorElm.ENABLE_POST;
        throw invalid("unsupported regulator terminal: " + terminalId);
    }

    private static <T> Map<String, T> immutableCopy(Map<String, T> source) {
        return Collections.unmodifiableMap(new LinkedHashMap<String, T>(source));
    }

    private static IllegalArgumentException invalid(String message) {
        return new IllegalArgumentException("Invalid regulator physical mapping: " + message);
    }
}
