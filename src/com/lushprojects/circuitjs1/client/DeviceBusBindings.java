package com.lushprojects.circuitjs1.client;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Immutable projection from temporary electrical equivalence classes to
 * explicit device buses or variant-owned local nets.
 */
final class DeviceBusBindings {
    static final String ENCODING_PREFIX = "tsj-bus-v1/";

    static final class Declaration {
        private final String semanticKey;
        private final List<String> anchorAliases;
        private final List<String> externalRefs;

        Declaration(String semanticKey, Collection<String> anchorAliases,
                Collection<String> externalRefs) {
            this.semanticKey = FunctionalBlockDescriptor.requireId(
                    semanticKey, "semanticKey");
            this.anchorAliases = immutableReferences(anchorAliases,
                    "anchorAliases", true);
            this.externalRefs = immutableReferences(externalRefs,
                    "externalRefs", false);
        }

        String getSemanticKey() {
            return semanticKey;
        }

        String getKey() {
            return semanticKey;
        }

        List<String> getAnchorAliases() {
            return anchorAliases;
        }

        List<String> getExternalRefs() {
            return externalRefs;
        }
        private static List<String> immutableReferences(
                Collection<String> values, String field, boolean required) {
            if (values == null) {
                if (!required) {
                    return Collections.unmodifiableList(
                            new ArrayList<String>());
                }
                throw new BlockContractException(
                        BlockContractException.Code.MISSING_DECLARATION,
                        field, null, "Collection is required");
            }
            ArrayList<String> copy = new ArrayList<String>();
            for (String value : values) {
                if (value == null || value.length() == 0) {
                    throw new BlockContractException(
                            BlockContractException.Code.INVALID_ID,
                            field, null, "Reference is required");
                }
                for (int i = 0; i < value.length(); i++) {
                    char c = value.charAt(i);
                    if (c <= 0x20 || c > 0x7e || c == '|' || c == '\\') {
                        throw new BlockContractException(
                                BlockContractException.Code.INVALID_ID,
                                field, value, "Reference contains invalid characters");
                    }
                }
                if (copy.contains(value)) {
                    throw new BlockContractException(
                            BlockContractException.Code.DUPLICATE_DECLARATION,
                            field, value, "Duplicate reference");
                }
                copy.add(value);
            }
            if (required && copy.isEmpty()) {
                throw new BlockContractException(
                        BlockContractException.Code.MISSING_DECLARATION,
                        field, null, "At least one reference is required");
            }
            Collections.sort(copy);
            return Collections.unmodifiableList(copy);
        }

        @Override
        public String toString() {
            return semanticKey + ":" + anchorAliases + ":" + externalRefs;
        }
    }

    private final Map<String, String> durableNets;
    private final Map<String, List<String>> busAliases;
    private final List<Declaration> declarations;
    private final Map<String, String> busIds;

    DeviceBusBindings(BlockNamespace namespace,
            Collection<Declaration> declarations,
            Map<String, String> electricalAliases) {
        if (namespace == null) {
            throw new IllegalArgumentException("Namespace is required");
        }
        if (namespace.getRealizations().isEmpty()
                || namespace.getRealizations().size() != namespace.getBlocks().size()) {
            throw new BlockContractException(
                    BlockContractException.Code.MISSING_DECLARATION,
                    "namespace.realizations", namespace.getDeviceSchemaId(),
                    "Durable bus bindings require explicit realization identities");
        }
        if (declarations == null) {
            throw new BlockContractException(
                    BlockContractException.Code.MISSING_DECLARATION,
                    "declarations", null, "Bus declarations are required");
        }
        if (electricalAliases == null) {
            throw new BlockContractException(
                    BlockContractException.Code.MISSING_DECLARATION,
                    "electricalAliases", null,
                    "Electrical alias map is required");
        }

        Map<String, String> localNetIds = namespace.getDurableLocalNetIds();
        TreeMap<String, String> normalized = normalizeAliases(
                localNetIds.keySet(), electricalAliases);
        TreeMap<String, TreeSet<String>> groups = groupAliases(normalized);
        TreeMap<String, Declaration> declarationMap =
                new TreeMap<String, Declaration>();
        TreeMap<String, String> ownerByGroup = new TreeMap<String, String>();
        TreeMap<String, String> anchorOwner = new TreeMap<String, String>();
        TreeMap<String, String> externalOwner = new TreeMap<String, String>();
        for (Declaration declaration : declarations) {
            if (declaration == null) {
                throw new BlockContractException(
                        BlockContractException.Code.MISSING_DECLARATION,
                        "declarations", null, "Declaration is required");
            }
            if (declarationMap.put(declaration.getSemanticKey(),
                    declaration) != null) {
                throw new BlockContractException(
                        BlockContractException.Code.DUPLICATE_DECLARATION,
                        "semanticKey", declaration.getSemanticKey(),
                        "Duplicate bus declaration");
            }
            String group = null;
            for (String alias : declaration.getAnchorAliases()) {
                if (!normalized.containsKey(alias)) {
                    throw new BlockContractException(
                            BlockContractException.Code.DANGLING_REFERENCE,
                            "anchorAliases", alias,
                            "Anchor is not a declared namespace net");
                }
                String priorAnchor = anchorOwner.put(alias,
                        declaration.getSemanticKey());
                if (priorAnchor != null) {
                    throw new BlockContractException(
                            BlockContractException.Code.DUPLICATE_DECLARATION,
                            "anchorAliases", alias,
                            "Anchor belongs to multiple buses");
                }
                String resolved = normalized.get(alias);
                if (group == null) {
                    group = resolved;
                } else if (!group.equals(resolved)) {
                    throw new BlockContractException(
                            BlockContractException.Code.CONTRADICTORY_ROLE,
                            "anchorAliases", declaration.getSemanticKey(),
                            "Bus anchors must share one electrical group");
                }
            }
            String priorOwner = ownerByGroup.put(group,
                    declaration.getSemanticKey());
            if (priorOwner != null
                    && !priorOwner.equals(declaration.getSemanticKey())) {
                throw new BlockContractException(
                        BlockContractException.Code.CONTRADICTORY_ROLE,
                        "declarations", group,
                        "Electrical group has conflicting bus owners");
            }
            for (String externalRef : declaration.getExternalRefs()) {
                String priorExternal = externalOwner.put(externalRef,
                        declaration.getSemanticKey());
                if (priorExternal != null) {
                    throw new BlockContractException(
                            BlockContractException.Code.DUPLICATE_DECLARATION,
                            "externalRefs", externalRef,
                            "External reference belongs to multiple buses");
                }
            }
        }
        for (Map.Entry<String, TreeSet<String>> entry : groups.entrySet()) {
            String group = entry.getKey();
            TreeSet<String> aliases = entry.getValue();
            String ownerKey = ownerByGroup.get(group);
            Declaration owner = ownerKey == null ? null
                    : declarationMap.get(ownerKey);
            if (aliases.size() > 1 && owner == null) {
                throw new BlockContractException(
                        BlockContractException.Code.MISSING_DECLARATION,
                        "declarations", group,
                        "Merged electrical group has no bus declaration");
            }
            if (owner != null && aliases.size() == 1
                    && owner.getExternalRefs().isEmpty()) {
                throw new BlockContractException(
                        BlockContractException.Code.INVALID_ATTACHMENT,
                        "declarations", owner.getSemanticKey(),
                        "Singleton internal net cannot be invented as a bus");
            }
        }
        TreeMap<String, String> durable = new TreeMap<String, String>();
        TreeMap<String, List<String>> aliasesByBus =
                new TreeMap<String, List<String>>();
        TreeMap<String, String> ids = new TreeMap<String, String>();
        for (Declaration declaration : declarationMap.values()) {
            ids.put(declaration.getSemanticKey(), busId(namespace,
                    declaration.getSemanticKey()));
        }
        for (Map.Entry<String, TreeSet<String>> entry : groups.entrySet()) {
            String group = entry.getKey();
            String ownerKey = ownerByGroup.get(group);
            Declaration owner = ownerKey == null ? null
                    : declarationMap.get(ownerKey);
            if (owner != null) {
                String id = ids.get(owner.getSemanticKey());
                ArrayList<String> members = new ArrayList<String>();
                for (String alias : entry.getValue()) {
                    durable.put(alias, id);
                    members.add(localNetIds.get(alias));
                }
                Collections.sort(members);
                aliasesByBus.put(id, Collections.unmodifiableList(members));
            } else {
                for (String alias : entry.getValue()) {
                    durable.put(alias, localNetIds.get(alias));
                }
            }
        }
        if (durable.size() != localNetIds.size()) {
            throw new BlockContractException(
                    BlockContractException.Code.MISSING_DECLARATION,
                    "electricalAliases", null,
                    "Every namespace net must receive a durable identity");
        }
        this.durableNets = Collections.unmodifiableMap(durable);
        this.busAliases = immutableNestedMap(aliasesByBus);
        ArrayList<Declaration> ordered =
                new ArrayList<Declaration>(declarationMap.values());
        this.declarations = Collections.unmodifiableList(ordered);
        this.busIds = Collections.unmodifiableMap(ids);
    }

    Map<String, String> getDurableNets() {
        return durableNets;
    }

    Map<String, List<String>> getBusAliases() {
        return busAliases;
    }

    List<Declaration> getDeclarations() {
        return declarations;
    }

    String getBusId(String semanticKey) {
        String key = FunctionalBlockDescriptor.requireId(
                semanticKey, "semanticKey");
        String id = busIds.get(key);
        if (id == null) {
            throw new IllegalArgumentException("Unknown device bus " + key);
        }
        return id;
    }

    String busId(String semanticKey) {
        return getBusId(semanticKey);
    }

    String durableNetFor(String legacyQualifiedNet) {
        String result = durableNets.get(legacyQualifiedNet);
        if (result == null) {
            throw new IllegalArgumentException(
                    "Unknown namespace net " + legacyQualifiedNet);
        }
        return result;
    }
    private static TreeMap<String, String> normalizeAliases(
            java.util.Set<String> expected, Map<String, String> aliases) {
        if (aliases.size() != expected.size()
                || !aliases.keySet().equals(expected)) {
            throw new BlockContractException(
                    BlockContractException.Code.DANGLING_REFERENCE,
                    "electricalAliases", null,
                    "Electrical aliases must exactly cover namespace nets");
        }
        TreeMap<String, String> result = new TreeMap<String, String>();
        for (Map.Entry<String, String> entry : aliases.entrySet()) {
            String value = entry.getValue();
            if (value == null || !expected.contains(value)) {
                throw new BlockContractException(
                        BlockContractException.Code.DANGLING_REFERENCE,
                        "electricalAliases", entry.getKey(),
                        "Electrical alias points outside namespace nets");
            }
            String normalized = aliases.get(value);
            if (!value.equals(normalized)) {
                throw new BlockContractException(
                        BlockContractException.Code.INVALID_ATTACHMENT,
                        "electricalAliases", entry.getKey(),
                        "Electrical aliases must be normalized to DSU roots");
            }
            result.put(entry.getKey(), value);
        }
        return result;
    }

    private static TreeMap<String, TreeSet<String>> groupAliases(
            Map<String, String> normalized) {
        TreeMap<String, TreeSet<String>> result =
                new TreeMap<String, TreeSet<String>>();
        for (Map.Entry<String, String> entry : normalized.entrySet()) {
            TreeSet<String> group = result.get(entry.getValue());
            if (group == null) {
                group = new TreeSet<String>();
                result.put(entry.getValue(), group);
            }
            group.add(entry.getKey());
        }
        return result;
    }

    private static String busId(BlockNamespace namespace, String semanticKey) {
        return ENCODING_PREFIX + namespace.getDeviceSchemaId() + "@"
                + namespace.getDeviceSchemaVersion() + "/" + semanticKey;
    }

    private static Map<String, List<String>> immutableNestedMap(
            Map<String, List<String>> source) {
        TreeMap<String, List<String>> copy =
                new TreeMap<String, List<String>>();
        for (Map.Entry<String, List<String>> entry : source.entrySet()) {
            copy.put(entry.getKey(), Collections.unmodifiableList(
                    new ArrayList<String>(entry.getValue())));
        }
        return Collections.unmodifiableMap(copy);
    }
}
