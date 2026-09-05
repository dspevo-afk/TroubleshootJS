package com.lushprojects.circuitjs1.client;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Immutable, package-local description of one functional block.
 *
 * <p>This class describes local declarations only.  It does not allocate
 * simulation elements, merge nets, or retain a reference to a runtime owner.</p>
 */
final class FunctionalBlockDescriptor {
    enum EntityKind {
        COMPONENT("component"),
        PAD("pad"),
        NET("net"),
        ENDPOINT("endpoint"),
        PORT("port"),
        ROLE("role");

        private final String token;

        EntityKind(String token) {
            this.token = token;
        }

        String getToken() {
            return token;
        }
    }

    enum Requirement {
        REQUIRED,
        OPTIONAL
    }

    static final class LocalRef {
        private final EntityKind kind;
        private final String id;

        LocalRef(EntityKind kind, String id) {
            if (kind == null) {
                throw new BlockContractException(
                        BlockContractException.Code.MISSING_DECLARATION,
                        "kind", id, "Reference kind is required");
            }
            this.kind = kind;
            this.id = requireId(id, "id");
        }

        EntityKind getKind() {
            return kind;
        }

        String getId() {
            return id;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof LocalRef)) {
                return false;
            }
            LocalRef that = (LocalRef) other;
            return kind == that.kind && id.equals(that.id);
        }

        @Override
        public int hashCode() {
            int result = kind.hashCode();
            result = 31 * result + id.hashCode();
            return result;
        }
    }

    static final class Value {
        enum Kind {
            BOOLEAN,
            INTEGER,
            DECIMAL,
            TEXT
        }

        private final Kind kind;
        private final boolean booleanValue;
        private final int integerValue;
        private final double decimalValue;
        private final String textValue;

        private Value(Kind kind, boolean booleanValue, int integerValue,
                double decimalValue, String textValue) {
            this.kind = kind;
            this.booleanValue = booleanValue;
            this.integerValue = integerValue;
            this.decimalValue = decimalValue;
            this.textValue = textValue;
        }

        static Value ofBoolean(boolean value) {
            return new Value(Kind.BOOLEAN, value, 0, 0.0, null);
        }

        static Value ofInteger(int value) {
            return new Value(Kind.INTEGER, false, value, 0.0, null);
        }

        static Value ofDecimal(double value) {
            if (Double.isNaN(value) || Double.isInfinite(value)) {
                throw new BlockContractException(
                        BlockContractException.Code.INVALID_PARAMETER,
                        "value", null, "Decimal value must be finite");
            }
            double normalized = value == 0.0 ? 0.0 : value;
            return new Value(Kind.DECIMAL, false, 0, normalized, null);
        }

        static Value ofText(String value) {
            if (value == null) {
                throw new BlockContractException(
                        BlockContractException.Code.INVALID_PARAMETER,
                        "value", null, "Text value is required");
            }
            return new Value(Kind.TEXT, false, 0, 0.0, value);
        }

        Kind getKind() {
            return kind;
        }

        boolean getBoolean() {
            requireKind(Kind.BOOLEAN);
            return booleanValue;
        }

        int getInteger() {
            requireKind(Kind.INTEGER);
            return integerValue;
        }

        double getDecimal() {
            requireKind(Kind.DECIMAL);
            return decimalValue;
        }

        String getText() {
            requireKind(Kind.TEXT);
            return textValue;
        }

        private void requireKind(Kind expected) {
            if (kind != expected) {
                throw new IllegalStateException("Value kind is " + kind
                        + ", not " + expected);
            }
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof Value)) {
                return false;
            }
            Value that = (Value) other;
            if (kind != that.kind) {
                return false;
            }
            switch (kind) {
            case BOOLEAN:
                return booleanValue == that.booleanValue;
            case INTEGER:
                return integerValue == that.integerValue;
            case DECIMAL:
                return Double.doubleToLongBits(decimalValue)
                        == Double.doubleToLongBits(that.decimalValue);
            case TEXT:
                return textValue.equals(that.textValue);
            default:
                return false;
            }
        }

        @Override
        public int hashCode() {
            int result = kind.hashCode();
            switch (kind) {
            case BOOLEAN:
                result = 31 * result + (booleanValue ? 1 : 0);
                break;
            case INTEGER:
                result = 31 * result + integerValue;
                break;
            case DECIMAL:
                long bits = Double.doubleToLongBits(decimalValue);
                result = 31 * result + (int) (bits ^ (bits >>> 32));
                break;
            case TEXT:
                result = 31 * result + textValue.hashCode();
                break;
            default:
                break;
            }
            return result;
        }
    }

    static final class Parameter {
        private final String id;
        private final Value value;

        Parameter(String id, Value value) {
            this.id = requireId(id, "id");
            if (value == null) {
                throw new BlockContractException(
                        BlockContractException.Code.INVALID_PARAMETER,
                        "value", this.id, "Parameter value is required");
            }
            this.value = value;
        }

        String getId() {
            return id;
        }

        Value getValue() {
            return value;
        }
    }

    static final class Component {
        private final String id;
        private final String typeId;
        private final List<String> terminalIds;

        Component(String id, String typeId, java.util.Collection<String> terminalIds) {
            this.id = requireId(id, "id");
            this.typeId = requireId(typeId, "typeId");
            if (terminalIds == null) {
                throw new BlockContractException(
                        BlockContractException.Code.MISSING_DECLARATION,
                        "terminalIds", this.id, "Terminal declarations are required");
            }
            TreeSet<String> sorted = new TreeSet<String>();
            for (String terminalId : terminalIds) {
                if (terminalId == null) {
                    throw new BlockContractException(
                            BlockContractException.Code.MISSING_DECLARATION,
                            "terminalIds", this.id, "Terminal ID is required");
                }
                String validated = requireId(terminalId, "terminalIds");
                if (!sorted.add(validated)) {
                    throw new BlockContractException(
                            BlockContractException.Code.DUPLICATE_DECLARATION,
                            "terminalIds", this.id,
                            "Duplicate terminal " + validated);
                }
            }
            this.terminalIds = Collections.unmodifiableList(
                    new ArrayList<String>(sorted));
        }

        String getId() {
            return id;
        }

        String getTypeId() {
            return typeId;
        }

        List<String> getTerminalIds() {
            return terminalIds;
        }
    }

    static final class Endpoint {
        private final String id;
        private final String componentId;
        private final String terminalId;

        Endpoint(String id, String componentId, String terminalId) {
            this.id = requireId(id, "id");
            this.componentId = requireId(componentId, "componentId");
            this.terminalId = requireId(terminalId, "terminalId");
        }

        String getId() {
            return id;
        }

        String getComponentId() {
            return componentId;
        }

        String getTerminalId() {
            return terminalId;
        }
    }

    static final class Pad {
        private final String id;
        private final String endpointId;
        private final String netId;

        Pad(String id, String endpointId, String netId) {
            this.id = requireId(id, "id");
            this.endpointId = requireId(endpointId, "endpointId");
            this.netId = requireId(netId, "netId");
        }

        String getId() {
            return id;
        }

        String getEndpointId() {
            return endpointId;
        }

        String getNetId() {
            return netId;
        }
    }

    static final class Role {
        private final String id;
        private final Requirement requirement;
        private final List<LocalRef> members;

        Role(String id, Requirement requirement,
                java.util.Collection<LocalRef> members) {
            this.id = requireId(id, "id");
            if (requirement == null) {
                throw new BlockContractException(
                        BlockContractException.Code.INVALID_ROLE,
                        "requirement", this.id, "Role requirement is required");
            }
            this.requirement = requirement;
            if (members == null) {
                throw new BlockContractException(
                        BlockContractException.Code.MISSING_DECLARATION,
                        "members", this.id, "Role members are required");
            }
            ArrayList<LocalRef> copy = new ArrayList<LocalRef>();
            Set<LocalRef> seen = new HashSet<LocalRef>();
            for (LocalRef member : members) {
                if (member == null) {
                    throw new BlockContractException(
                            BlockContractException.Code.MISSING_DECLARATION,
                            "members", this.id, "Role member is required");
                }
                if (!seen.add(member)) {
                    throw new BlockContractException(
                            BlockContractException.Code.DUPLICATE_DECLARATION,
                            "members", this.id,
                            "Duplicate role member " + member.getKind().getToken()
                                    + "/" + member.getId());
                }
                copy.add(member);
            }
            Collections.sort(copy, LOCAL_REF_COMPARATOR);
            this.members = Collections.unmodifiableList(copy);
        }

        String getId() {
            return id;
        }

        Requirement getRequirement() {
            return requirement;
        }

        List<LocalRef> getMembers() {
            return members;
        }
    }

    static final class Port {
        private final String id;
        private final String roleId;
        private final LocalRef attachment;

        Port(String id, String roleId, LocalRef attachment) {
            this.id = requireId(id, "id");
            this.roleId = requireId(roleId, "roleId");
            if (attachment == null) {
                throw new BlockContractException(
                        BlockContractException.Code.INVALID_ATTACHMENT,
                        "attachment", this.id, "Port attachment is required");
            }
            this.attachment = attachment;
        }

        String getId() {
            return id;
        }

        String getRoleId() {
            return roleId;
        }

        LocalRef getAttachment() {
            return attachment;
        }
    }

    private static final Comparator<LocalRef> LOCAL_REF_COMPARATOR
            = new Comparator<LocalRef>() {
        @Override
        public int compare(LocalRef first, LocalRef second) {
            int kindComparison = first.getKind().getToken()
                    .compareTo(second.getKind().getToken());
            if (kindComparison != 0) {
                return kindComparison;
            }
            return first.getId().compareTo(second.getId());
        }
    };

    private final String typeId;
    private final int schemaVersion;
    private final String instanceKey;
    private final Map<String, Parameter> parameters;
    private final Map<String, Component> components;
    private final List<String> netIds;
    private final Map<String, Endpoint> endpoints;
    private final Map<String, Pad> pads;
    private final Map<String, Role> roles;
    private final Map<String, Port> ports;

    FunctionalBlockDescriptor(String typeId, int schemaVersion,
            String instanceKey, java.util.Collection<Parameter> parameters,
            java.util.Collection<Component> components,
            java.util.Collection<String> netIds,
            java.util.Collection<Endpoint> endpoints,
            java.util.Collection<Pad> pads,
            java.util.Collection<Role> roles,
            java.util.Collection<Port> ports) {
        this.typeId = requireId(typeId, "typeId");
        requirePositiveVersion(schemaVersion, "schemaVersion", this.typeId);
        this.schemaVersion = schemaVersion;
        this.instanceKey = requireId(instanceKey, "instanceKey");

        if (parameters == null) {
            throw missingCollection("parameters", this.instanceKey);
        }
        if (components == null) {
            throw missingCollection("components", this.instanceKey);
        }
        if (netIds == null) {
            throw missingCollection("netIds", this.instanceKey);
        }
        if (endpoints == null) {
            throw missingCollection("endpoints", this.instanceKey);
        }
        if (pads == null) {
            throw missingCollection("pads", this.instanceKey);
        }
        if (roles == null) {
            throw missingCollection("roles", this.instanceKey);
        }
        if (ports == null) {
            throw missingCollection("ports", this.instanceKey);
        }

        TreeMap<String, Parameter> parameterMap
                = new TreeMap<String, Parameter>();
        for (Parameter parameter : parameters) {
            if (parameter == null) {
                throw missingElement("parameters", this.instanceKey);
            }
            putUnique(parameterMap, parameter.getId(), parameter,
                    "parameters");
        }

        TreeMap<String, Component> componentMap
                = new TreeMap<String, Component>();
        for (Component component : components) {
            if (component == null) {
                throw missingElement("components", this.instanceKey);
            }
            putUnique(componentMap, component.getId(), component,
                    "components");
        }

        TreeSet<String> netSet = new TreeSet<String>();
        for (String netId : netIds) {
            if (netId == null) {
                throw missingElement("netIds", this.instanceKey);
            }
            String validated = requireId(netId, "netIds");
            if (!netSet.add(validated)) {
                throw duplicate("netIds", validated,
                        "Duplicate net declaration");
            }
        }

        TreeMap<String, Endpoint> endpointMap
                = new TreeMap<String, Endpoint>();
        Map<TerminalKey, Endpoint> endpointByTerminal
                = new HashMap<TerminalKey, Endpoint>();
        for (Endpoint endpoint : endpoints) {
            if (endpoint == null) {
                throw missingElement("endpoints", this.instanceKey);
            }
            putUnique(endpointMap, endpoint.getId(), endpoint, "endpoints");
            Component component = componentMap.get(endpoint.getComponentId());
            if (component == null) {
                throw dangling("endpoints.componentId", endpoint.getId(),
                        "Unknown component " + endpoint.getComponentId());
            }
            if (!component.getTerminalIds().contains(endpoint.getTerminalId())) {
                throw dangling("endpoints.terminalId", endpoint.getId(),
                        "Unknown terminal " + endpoint.getTerminalId());
            }
            TerminalKey terminalKey = new TerminalKey(endpoint.getComponentId(),
                    endpoint.getTerminalId());
            if (endpointByTerminal.containsKey(terminalKey)) {
                throw new BlockContractException(
                        BlockContractException.Code.DUPLICATE_TERMINAL_ENDPOINT,
                        "endpoints", endpoint.getId(),
                        "Component terminal already has an endpoint: "
                                + endpoint.getComponentId() + "/"
                                + endpoint.getTerminalId());
            }
            endpointByTerminal.put(terminalKey, endpoint);
        }
        for (Component component : componentMap.values()) {
            for (String terminalId : component.getTerminalIds()) {
                TerminalKey terminalKey = new TerminalKey(component.getId(),
                        terminalId);
                if (!endpointByTerminal.containsKey(terminalKey)) {
                    throw new BlockContractException(
                            BlockContractException.Code.MISSING_DECLARATION,
                            "endpoints", component.getId(),
                            "Missing endpoint for terminal " + terminalId);
                }
            }
        }

        TreeMap<String, Pad> padMap = new TreeMap<String, Pad>();
        Map<String, Pad> padByEndpoint = new HashMap<String, Pad>();
        for (Pad pad : pads) {
            if (pad == null) {
                throw missingElement("pads", this.instanceKey);
            }
            putUnique(padMap, pad.getId(), pad, "pads");
            if (!endpointMap.containsKey(pad.getEndpointId())) {
                throw dangling("pads.endpointId", pad.getId(),
                        "Unknown endpoint " + pad.getEndpointId());
            }
            if (!netSet.contains(pad.getNetId())) {
                throw dangling("pads.netId", pad.getId(),
                        "Unknown net " + pad.getNetId());
            }
            if (padByEndpoint.containsKey(pad.getEndpointId())) {
                throw new BlockContractException(
                        BlockContractException.Code.INVALID_ATTACHMENT,
                        "pads.endpointId", pad.getId(),
                        "An endpoint may map to at most one pad");
            }
            padByEndpoint.put(pad.getEndpointId(), pad);
        }

        TreeMap<String, Role> roleMap = new TreeMap<String, Role>();
        for (Role role : roles) {
            if (role == null) {
                throw missingElement("roles", this.instanceKey);
            }
            Role prior = roleMap.get(role.getId());
            if (prior != null) {
                if (prior.getRequirement() != role.getRequirement()) {
                    throw new BlockContractException(
                            BlockContractException.Code.CONTRADICTORY_ROLE,
                            "roles.requirement", role.getId(),
                            "Role is declared with both requirements");
                }
                throw duplicate("roles", role.getId(),
                        "Duplicate role declaration");
            }
            roleMap.put(role.getId(), role);
        }
        for (Role role : roleMap.values()) {
            if (role.getRequirement() == Requirement.REQUIRED
                    && role.getMembers().isEmpty()) {
                throw new BlockContractException(
                        BlockContractException.Code.INVALID_ROLE,
                        "roles.members", role.getId(),
                        "Required role must have at least one member");
            }
            for (LocalRef member : role.getMembers()) {
                EntityKind kind = member.getKind();
                if (kind == EntityKind.PORT || kind == EntityKind.ROLE) {
                    throw new BlockContractException(
                            BlockContractException.Code.INVALID_ROLE,
                            "roles.members", role.getId(),
                            "Role members may not reference "
                                    + kind.getToken());
                }
                if (!declares(kind, member.getId(), componentMap, padMap,
                        netSet, endpointMap, roleMap, null)) {
                    throw new BlockContractException(
                            BlockContractException.Code.DANGLING_REFERENCE,
                            "roles.members", role.getId(),
                            "Unknown role member " + kind.getToken() + "/"
                                    + member.getId());
                }
            }
        }

        TreeMap<String, Port> portMap = new TreeMap<String, Port>();
        for (Port port : ports) {
            if (port == null) {
                throw missingElement("ports", this.instanceKey);
            }
            putUnique(portMap, port.getId(), port, "ports");
            Role role = roleMap.get(port.getRoleId());
            if (role == null) {
                throw dangling("ports.roleId", port.getId(),
                        "Unknown role " + port.getRoleId());
            }
            LocalRef attachment = port.getAttachment();
            EntityKind attachmentKind = attachment.getKind();
            if (attachmentKind != EntityKind.PAD
                    && attachmentKind != EntityKind.NET
                    && attachmentKind != EntityKind.ENDPOINT) {
                throw new BlockContractException(
                        BlockContractException.Code.INVALID_ATTACHMENT,
                        "ports.attachment", port.getId(),
                        "Port attachment must be a pad, net, or endpoint");
            }
            if (!declares(attachmentKind, attachment.getId(), componentMap,
                    padMap, netSet, endpointMap, roleMap, portMap)) {
                throw dangling("ports.attachment", port.getId(),
                        "Unknown attachment " + attachmentKind.getToken()
                                + "/" + attachment.getId());
            }
            if (!role.getMembers().contains(attachment)) {
                throw new BlockContractException(
                        BlockContractException.Code.INVALID_ATTACHMENT,
                        "ports.attachment", port.getId(),
                        "Port attachment is not a member of its role");
            }
        }

        ArrayList<String> sortedNetIds = new ArrayList<String>(netSet);
        this.parameters = immutableMap(parameterMap);
        this.components = immutableMap(componentMap);
        this.netIds = Collections.unmodifiableList(sortedNetIds);
        this.endpoints = immutableMap(endpointMap);
        this.pads = immutableMap(padMap);
        this.roles = immutableMap(roleMap);
        this.ports = immutableMap(portMap);
    }

    String getTypeId() {
        return typeId;
    }

    int getSchemaVersion() {
        return schemaVersion;
    }

    String getInstanceKey() {
        return instanceKey;
    }

    Map<String, Parameter> getParameters() {
        return parameters;
    }

    Map<String, Component> getComponents() {
        return components;
    }

    List<String> getNetIds() {
        return netIds;
    }

    Map<String, Endpoint> getEndpoints() {
        return endpoints;
    }

    Map<String, Pad> getPads() {
        return pads;
    }

    Map<String, Role> getRoles() {
        return roles;
    }

    Map<String, Port> getPorts() {
        return ports;
    }

    boolean declares(EntityKind kind, String id) {
        if (kind == null || id == null) {
            return false;
        }
        switch (kind) {
        case COMPONENT:
            return components.containsKey(id);
        case PAD:
            return pads.containsKey(id);
        case NET:
            return netIds.contains(id);
        case ENDPOINT:
            return endpoints.containsKey(id);
        case PORT:
            return ports.containsKey(id);
        case ROLE:
            return roles.containsKey(id);
        default:
            return false;
        }
    }

    static String requireId(String value, String fieldId) {
        if (value == null || value.length() == 0 || value.length() > 128) {
            throw invalidId(fieldId, value);
        }
        char first = value.charAt(0);
        if (!isAsciiLetter(first) && !isAsciiDigit(first) && first != '_') {
            throw invalidId(fieldId, value);
        }
        for (int index = 1; index < value.length(); index++) {
            char character = value.charAt(index);
            if (!isAsciiLetter(character) && !isAsciiDigit(character)
                    && character != '_' && character != '.'
                    && character != '-') {
                throw invalidId(fieldId, value);
            }
        }
        return value;
    }

    static void requirePositiveVersion(int version, String fieldId,
            String entityId) {
        if (version <= 0) {
            throw new BlockContractException(
                    BlockContractException.Code.INVALID_VERSION, fieldId,
                    entityId, "Version must be positive");
        }
    }

    private static boolean isAsciiLetter(char value) {
        return (value >= 'A' && value <= 'Z')
                || (value >= 'a' && value <= 'z');
    }

    private static boolean isAsciiDigit(char value) {
        return value >= '0' && value <= '9';
    }

    private static BlockContractException invalidId(String fieldId,
            String value) {
        return new BlockContractException(
                BlockContractException.Code.INVALID_ID, fieldId, value,
                "ID must match [A-Za-z0-9_][A-Za-z0-9_.-]{0,127}");
    }

    private static BlockContractException missingCollection(String fieldId,
            String entityId) {
        return new BlockContractException(
                BlockContractException.Code.MISSING_DECLARATION, fieldId,
                entityId, "Collection is required");
    }

    private static BlockContractException missingElement(String fieldId,
            String entityId) {
        return new BlockContractException(
                BlockContractException.Code.MISSING_DECLARATION, fieldId,
                entityId, "Collection element is required");
    }

    private static BlockContractException duplicate(String fieldId,
            String entityId, String detail) {
        return new BlockContractException(
                BlockContractException.Code.DUPLICATE_DECLARATION, fieldId,
                entityId, detail);
    }

    private static BlockContractException dangling(String fieldId,
            String entityId, String detail) {
        return new BlockContractException(
                BlockContractException.Code.DANGLING_REFERENCE, fieldId,
                entityId, detail);
    }

    private static <T> void putUnique(Map<String, T> target, String id,
            T value, String fieldId) {
        if (target.containsKey(id)) {
            throw duplicate(fieldId, id, "Duplicate declaration");
        }
        target.put(id, value);
    }

    private static <T> Map<String, T> immutableMap(Map<String, T> source) {
        return Collections.unmodifiableMap(new TreeMap<String, T>(source));
    }

    private static boolean declares(EntityKind kind, String id,
            Map<String, Component> componentMap, Map<String, Pad> padMap,
            Set<String> netSet, Map<String, Endpoint> endpointMap,
            Map<String, Role> roleMap, Map<String, Port> portMap) {
        if (kind == null || id == null) {
            return false;
        }
        switch (kind) {
        case COMPONENT:
            return componentMap.containsKey(id);
        case PAD:
            return padMap.containsKey(id);
        case NET:
            return netSet.contains(id);
        case ENDPOINT:
            return endpointMap.containsKey(id);
        case ROLE:
            return roleMap.containsKey(id);
        case PORT:
            return portMap != null && portMap.containsKey(id);
        default:
            return false;
        }
    }

    private static final class TerminalKey {
        private final String componentId;
        private final String terminalId;

        TerminalKey(String componentId, String terminalId) {
            this.componentId = componentId;
            this.terminalId = terminalId;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof TerminalKey)) {
                return false;
            }
            TerminalKey that = (TerminalKey) other;
            return componentId.equals(that.componentId)
                    && terminalId.equals(that.terminalId);
        }

        @Override
        public int hashCode() {
            int result = componentId.hashCode();
            result = 31 * result + terminalId.hashCode();
            return result;
        }
    }
}
