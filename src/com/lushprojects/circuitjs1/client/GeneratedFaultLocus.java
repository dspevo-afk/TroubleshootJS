package com.lushprojects.circuitjs1.client;

/**
 * Semantic physical identity for a fault.  This deliberately contains no
 * CircuitJS node, geometry, collection index, or private solver identity.
 */
final class GeneratedFaultLocus {
    private final GeneratedFaultLocusType type;
    private final String componentId;
    private final String terminalId;
    private final String pathId;

    private GeneratedFaultLocus(GeneratedFaultLocusType type, String componentId,
            String terminalId, String pathId) {
        if (type == null)
            throw new IllegalArgumentException("Missing generated fault locus type");
        requireSemanticId(componentId, "component ID", type != GeneratedFaultLocusType.TRACE_SEGMENT);
        requireSemanticId(terminalId, "terminal ID", type == GeneratedFaultLocusType.TERMINAL_ATTACHMENT ||
            type == GeneratedFaultLocusType.CONNECTOR_CONTACT);
        requireSemanticId(pathId, "path ID", type == GeneratedFaultLocusType.TRACE_SEGMENT);
        if (type == GeneratedFaultLocusType.TRACE_SEGMENT) {
            if (pathId == null)
                throw new IllegalArgumentException("Trace locus requires a stable path ID");
        } else if (componentId == null) {
            throw new IllegalArgumentException("Physical component locus requires an owner");
        }
        if (type == GeneratedFaultLocusType.TERMINAL_ATTACHMENT ||
                type == GeneratedFaultLocusType.CONNECTOR_CONTACT) {
            if (terminalId == null)
                throw new IllegalArgumentException("Terminal locus requires a stable terminal ID");
        } else if (terminalId != null) {
            throw new IllegalArgumentException("Non-terminal locus cannot carry a terminal ID");
        }
        if (type != GeneratedFaultLocusType.TRACE_SEGMENT && pathId != null)
            throw new IllegalArgumentException("Component locus cannot carry a path ID");
        this.type = type;
        this.componentId = componentId;
        this.terminalId = terminalId;
        this.pathId = pathId;
    }

    static GeneratedFaultLocus componentInternal(String componentId) {
        return new GeneratedFaultLocus(GeneratedFaultLocusType.COMPONENT_INTERNAL,
            componentId, null, null);
    }

    static GeneratedFaultLocus terminalAttachment(String componentId, String terminalId) {
        return new GeneratedFaultLocus(GeneratedFaultLocusType.TERMINAL_ATTACHMENT,
            componentId, terminalId, null);
    }

    static GeneratedFaultLocus connectorContact(String componentId, String terminalId) {
        return new GeneratedFaultLocus(GeneratedFaultLocusType.CONNECTOR_CONTACT,
            componentId, terminalId, null);
    }

    static GeneratedFaultLocus traceSegment(String pathId) {
        return new GeneratedFaultLocus(GeneratedFaultLocusType.TRACE_SEGMENT,
            null, null, pathId);
    }

    GeneratedFaultLocusType getType() { return type; }
    String getComponentId() { return componentId; }
    String getTerminalId() { return terminalId; }
    String getPathId() { return pathId; }

    /** The stable owner identity used for deterministic owner counts. */
    String getOwnerId() {
        if (type == GeneratedFaultLocusType.TRACE_SEGMENT)
            return pathId;
        // The physical owner is the stable component identity.  A terminal is
        // part of the locus, but not a second owner for the same installed part.
        return componentId;
    }

    private static void requireSemanticId(String value, String name, boolean required) {
        if (value == null) {
            if (required)
                throw new IllegalArgumentException("Missing stable semantic " + name);
            return;
        }
        if (value.length() == 0 || !value.matches("[A-Za-z0-9_.+\\-]+"))
            throw new IllegalArgumentException("Invalid stable semantic " + name);
        String upper = value.toUpperCase();
        String[] forbiddenTokens = { "NODE", "COORD", "INDEX", "UUID", "SWITCH" };
        for (String token : forbiddenTokens)
            if (upper.indexOf(token) >= 0)
                throw new IllegalArgumentException("Physical locus must not encode " + token);
    }
}
