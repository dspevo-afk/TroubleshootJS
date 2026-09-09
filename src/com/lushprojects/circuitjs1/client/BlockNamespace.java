package com.lushprojects.circuitjs1.client;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Immutable namespace for one resolved device realization.
 *
 * <p>The namespace is the sole encoder for declared semantic identities. A
 * namespace made without realization records is intentionally limited to the
 * provisional addresses needed by preflight; resolved assembly namespaces
 * include the provider and variant in every identity so two implementations
 * cannot silently share an address.</p>
 */
final class BlockNamespace {
    private static final String PROVISIONAL_PREFIX = "tsj-preflight-v1/";
    private static final String REALIZATION_PREFIX = "tsj-realization-v1/";
    private static final String TERMINAL_TOKEN = "terminal";

    private final String deviceSchemaId;
    private final int deviceSchemaVersion;
    private final Map<String, FunctionalBlockDescriptor> blocks;
    private final Map<String, BlockRealizationIdentity> realizations;
    private final Set<String> semanticNetIds;

    /** Build the provisional namespace used by syntax-only preflight. */
    BlockNamespace(String deviceSchemaId, int deviceSchemaVersion,
            Collection<FunctionalBlockDescriptor> blocks) {
        this(deviceSchemaId, deviceSchemaVersion, blocks, null, false);
    }

    /** Build a resolved namespace; realization records must cover all blocks. */
    BlockNamespace(String deviceSchemaId, int deviceSchemaVersion,
            Collection<FunctionalBlockDescriptor> blocks,
            Collection<BlockRealizationIdentity> realizations) {
        this(deviceSchemaId, deviceSchemaVersion, blocks, realizations, true);
    }

    private BlockNamespace(String deviceSchemaId, int deviceSchemaVersion,
            Collection<FunctionalBlockDescriptor> blocks,
            Collection<BlockRealizationIdentity> realizationValues,
            boolean requireRealizations) {
        this.deviceSchemaId = FunctionalBlockDescriptor.requireId(
                deviceSchemaId, "deviceSchemaId");
        FunctionalBlockDescriptor.requirePositiveVersion(deviceSchemaVersion,
                "deviceSchemaVersion", this.deviceSchemaId);
        this.deviceSchemaVersion = deviceSchemaVersion;
        if (blocks == null) {
            throw new BlockContractException(
                    BlockContractException.Code.MISSING_DECLARATION,
                    "blocks", this.deviceSchemaId,
                    "Block collection is required");
        }
        TreeMap<String, FunctionalBlockDescriptor> blockMap =
                new TreeMap<String, FunctionalBlockDescriptor>();
        for (FunctionalBlockDescriptor block : blocks) {
            if (block == null) {
                throw new BlockContractException(
                        BlockContractException.Code.MISSING_DECLARATION,
                        "blocks", this.deviceSchemaId,
                        "Block declaration is required");
            }
            String instanceKey = block.getInstanceKey();
            if (blockMap.put(instanceKey, block) != null) {
                throw new BlockContractException(
                        BlockContractException.Code.DUPLICATE_DECLARATION,
                        "blocks.instanceKey", instanceKey,
                        "Duplicate block instance key");
            }
        }
        this.blocks = Collections.unmodifiableMap(
                new TreeMap<String, FunctionalBlockDescriptor>(blockMap));
        this.realizations = requireRealizations
                ? validateRealizations(realizationValues, blockMap)
                : Collections.unmodifiableMap(
                        new TreeMap<String, BlockRealizationIdentity>());

        TreeSet<String> nets = new TreeSet<String>();
        for (Map.Entry<String, FunctionalBlockDescriptor> entry
                : blockMap.entrySet()) {
            for (String localNet : entry.getValue().getNetIds()) {
                nets.add(idFor(entry.getKey(),
                        FunctionalBlockDescriptor.EntityKind.NET, localNet));
            }
        }
        this.semanticNetIds = Collections.unmodifiableSet(nets);
    }

    String getDeviceSchemaId() { return deviceSchemaId; }

    int getDeviceSchemaVersion() { return deviceSchemaVersion; }

    Map<String, FunctionalBlockDescriptor> getBlocks() { return blocks; }

    Map<String, BlockRealizationIdentity> getRealizations() {
        return realizations;
    }

    /** Every declared net address in this namespace, in canonical order. */
    Set<String> getSemanticNetIds() { return semanticNetIds; }

    BlockRealizationIdentity realizationFor(String instanceKey) {
        String key = FunctionalBlockDescriptor.requireId(
                instanceKey, "instanceKey");
        if (realizations.isEmpty()) {
            throw new IllegalStateException(
                    "Resolved realization identities are not registered");
        }
        BlockRealizationIdentity result = realizations.get(key);
        if (result == null) {
            throw new BlockContractException(
                    BlockContractException.Code.DANGLING_REFERENCE,
                    "instanceKey", key, "Realization is not registered");
        }
        return result;
    }

    /** Validate a provisional compatibility address used by preflight only. */
    static boolean isQualifiedId(String value,
            FunctionalBlockDescriptor.EntityKind kind) {
        if (value == null || kind == null) {
            return false;
        }
        if (value.startsWith(REALIZATION_PREFIX))
            return isRealizationId(value, kind);
        if (!value.startsWith(PROVISIONAL_PREFIX)) return false;
        String[] fields = value.substring(PROVISIONAL_PREFIX.length())
                .split("/", -1);
        if (fields.length != 4 || !kind.getToken().equals(fields[2])) {
            return false;
        }
        return validSchemaAndParts(fields[0], fields[1], fields[3]);
    }

    private static boolean isRealizationId(String value,
            FunctionalBlockDescriptor.EntityKind kind) {
        String[] fields = value.substring(REALIZATION_PREFIX.length())
                .split("/", -1);
        if (fields.length != 6 || !kind.getToken().equals(fields[4])
                || !validSchemaAndParts(fields[0], fields[1], fields[5])) {
            return false;
        }
        try {
            ChallengeDescriptor.VersionedId.parse(fields[2], "provider");
            ChallengeDescriptor.VersionedId.parse(fields[3], "variant");
            return true;
        } catch (IllegalArgumentException invalid) {
            return false;
        }
    }

    /**
     * Encode one declared semantic entity. Resolved namespaces always use
     * provider/variant ownership; provisional namespaces use the preflight
     * address because providers have not been selected yet.
     */
    String idFor(String instanceKey, FunctionalBlockDescriptor.EntityKind kind,
            String localId) {
        String validatedInstanceKey = FunctionalBlockDescriptor.requireId(
                instanceKey, "instanceKey");
        if (kind == null) {
            throw new BlockContractException(
                    BlockContractException.Code.MISSING_DECLARATION, "kind",
                    validatedInstanceKey, "Entity kind is required");
        }
        String validatedLocalId = FunctionalBlockDescriptor.requireId(localId,
                "localId");
        FunctionalBlockDescriptor block = blocks.get(validatedInstanceKey);
        if (block == null) {
            throw new BlockContractException(
                    BlockContractException.Code.DANGLING_REFERENCE,
                    "instanceKey", validatedInstanceKey,
                    "Block instance is not registered");
        }
        if (!block.declares(kind, validatedLocalId)) {
            throw new BlockContractException(
                    BlockContractException.Code.DANGLING_REFERENCE,
                    "localId", validatedLocalId,
                    "Entity is not declared by block instance");
        }
        if (realizations.isEmpty()) {
            return PROVISIONAL_PREFIX + deviceSchemaId + "@"
                    + deviceSchemaVersion + "/" + validatedInstanceKey + "/"
                    + kind.getToken() + "/" + validatedLocalId;
        }
        return variantOwnedId(realizationFor(validatedInstanceKey),
                kind.getToken(), validatedLocalId);
    }

    /** Encode a component terminal as a declared semantic identity. */
    String terminalIdFor(String instanceKey, String componentLocalId,
            String terminalName) {
        String key = validatedBlock(instanceKey).getInstanceKey();
        String component = FunctionalBlockDescriptor.requireId(
                componentLocalId, "componentLocalId");
        String terminal = FunctionalBlockDescriptor.requireId(
                terminalName, "terminalName");
        FunctionalBlockDescriptor.Component declaration =
                validatedBlock(key).getComponents().get(component);
        if (declaration == null || !declaration.getTerminalIds().contains(terminal)) {
            throw new BlockContractException(
                    BlockContractException.Code.DANGLING_REFERENCE,
                    "terminalName", terminal,
                    "Terminal is not declared by component");
        }
        String local = terminalKey(component, terminal);
        if (realizations.isEmpty()) {
            return PROVISIONAL_PREFIX + deviceSchemaId + "@"
                    + deviceSchemaVersion + "/" + key + "/" + TERMINAL_TOKEN
                    + "/" + local;
        }
        return variantOwnedId(realizationFor(key), TERMINAL_TOKEN, local);
    }

    private static String terminalKey(String component, String terminal) {
        if (component.indexOf('.') < 0 && terminal.indexOf('.') < 0) {
            return component + "." + terminal;
        }
        return component.length() + ":" + component + terminal.length()
                + ":" + terminal;
    }

    private FunctionalBlockDescriptor validatedBlock(String instanceKey) {
        String key = FunctionalBlockDescriptor.requireId(
                instanceKey, "instanceKey");
        FunctionalBlockDescriptor block = blocks.get(key);
        if (block == null) {
            throw new BlockContractException(
                    BlockContractException.Code.DANGLING_REFERENCE,
                    "instanceKey", key, "Block instance is not registered");
        }
        return block;
    }

    private String variantOwnedId(BlockRealizationIdentity realization,
            String kind, String localId) {
        return REALIZATION_PREFIX + deviceSchemaId + "@"
                + deviceSchemaVersion + "/" + realization.getInstanceKey()
                + "/" + realization.getProvider().toCanonical() + "/"
                + realization.getVariant().toCanonical() + "/" + kind
                + "/" + localId;
    }

    private static boolean validSchemaAndParts(String schema,
            String instance, String local) {
        int separator = schema.lastIndexOf('@');
        if (separator < 1 || !validVersion(schema.substring(separator + 1))) {
            return false;
        }
        try {
            FunctionalBlockDescriptor.requireId(
                    schema.substring(0, separator), "deviceSchemaId");
            FunctionalBlockDescriptor.requireId(instance, "instanceKey");
            FunctionalBlockDescriptor.requireId(local, "localId");
            return true;
        } catch (IllegalArgumentException invalid) {
            return false;
        }
    }

    private static boolean validVersion(String value) {
        if (value == null || value.length() == 0
                || (value.length() > 1 && value.charAt(0) == '0')) {
            return false;
        }
        try {
            int parsed = Integer.parseInt(value);
            return parsed > 0 && Integer.toString(parsed).equals(value);
        } catch (NumberFormatException invalid) {
            return false;
        }
    }

    private static Map<String, BlockRealizationIdentity> validateRealizations(
            Collection<BlockRealizationIdentity> values,
            Map<String, FunctionalBlockDescriptor> blockMap) {
        if (values == null) {
            throw new BlockContractException(
                    BlockContractException.Code.MISSING_DECLARATION,
                    "realizations", null, "Realization collection is required");
        }
        TreeMap<String, BlockRealizationIdentity> result =
                new TreeMap<String, BlockRealizationIdentity>();
        for (BlockRealizationIdentity value : values) {
            if (value == null) {
                throw new BlockContractException(
                        BlockContractException.Code.MISSING_DECLARATION,
                        "realizations", null, "Realization is required");
            }
            if (result.put(value.getInstanceKey(), value) != null) {
                throw new BlockContractException(
                        BlockContractException.Code.DUPLICATE_DECLARATION,
                        "realizations.instanceKey", value.getInstanceKey(),
                        "Duplicate realization instance key");
            }
        }
        if (!result.keySet().equals(blockMap.keySet())) {
            throw new BlockContractException(
                    BlockContractException.Code.DANGLING_REFERENCE,
                    "realizations", null,
                    "Realization identities must exactly cover blocks");
        }
        for (Map.Entry<String, FunctionalBlockDescriptor> entry
                : blockMap.entrySet()) {
            BlockRealizationIdentity realization = result.get(entry.getKey());
            if (!realization.getVariant().getId().equals(
                    entry.getValue().getTypeId())
                    || realization.getVariant().getVersion()
                            != entry.getValue().getSchemaVersion()) {
                throw new BlockContractException(
                        BlockContractException.Code.CONTRADICTORY_ROLE,
                        "realizations.variant", entry.getKey(),
                        "Variant identity must match block descriptor");
            }
        }
        return Collections.unmodifiableMap(result);
    }
}
