package com.lushprojects.circuitjs1.client;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;

/**
 * Immutable namespace for block-local logical identities within one device
 * schema. It performs lookup and encoding only; it does not allocate runtime
 * elements or merge electrical nets.
 */
final class BlockNamespace {
    private static final String ENCODING_PREFIX = "tsj-block-v1/";
    private static final String REALIZATION_PREFIX = "tsj-realization-v1/";
    private static final String ROLE_PREFIX = "tsj-role-v1/";
    private static final String PORT_PREFIX = "tsj-port-v1/";
    private static final String TERMINAL_TOKEN = "terminal";

    private final String deviceSchemaId;
    private final int deviceSchemaVersion;
    private final Map<String, FunctionalBlockDescriptor> blocks;
    private final Map<String, BlockRealizationIdentity> realizations;
    private final Map<String, String> durableLocalNetIds;

    BlockNamespace(String deviceSchemaId, int deviceSchemaVersion,
            Collection<FunctionalBlockDescriptor> blocks) {
        this(deviceSchemaId, deviceSchemaVersion, blocks, null, false);
    }

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
            if (blockMap.containsKey(instanceKey)) {
                throw new BlockContractException(
                        BlockContractException.Code.DUPLICATE_DECLARATION,
                        "blocks.instanceKey", instanceKey,
                        "Duplicate block instance key");
            }
            blockMap.put(instanceKey, block);
        }
        this.blocks = Collections.unmodifiableMap(
                new TreeMap<String, FunctionalBlockDescriptor>(blockMap));
        if (requireRealizations) {
            this.realizations = validateRealizations(realizationValues,
                    blockMap);
            this.durableLocalNetIds = deriveDurableLocalNetIds(blockMap,
                    this.realizations, this.deviceSchemaId,
                    this.deviceSchemaVersion);
        } else {
            this.realizations = Collections.unmodifiableMap(
                    new TreeMap<String, BlockRealizationIdentity>());
            this.durableLocalNetIds = deriveDurableLocalNetIds(blockMap,
                    this.realizations, this.deviceSchemaId,
                    this.deviceSchemaVersion);
        }
    }

    String getDeviceSchemaId() {
        return deviceSchemaId;
    }

    int getDeviceSchemaVersion() {
        return deviceSchemaVersion;
    }

    Map<String, FunctionalBlockDescriptor> getBlocks() {
        return blocks;
    }

    Map<String, BlockRealizationIdentity> getRealizations() {
        return realizations;
    }

    BlockRealizationIdentity realizationFor(String instanceKey) {
        String key = FunctionalBlockDescriptor.requireId(
                instanceKey, "instanceKey");
        if (realizations.isEmpty()) {
            throw new IllegalStateException(
                    "Durable realization identities are not registered");
        }
        BlockRealizationIdentity result = realizations.get(key);
        if (result == null) {
            throw new BlockContractException(
                    BlockContractException.Code.DANGLING_REFERENCE,
                    "instanceKey", key, "Realization is not registered");
        }
        return result;
    }

    Map<String, String> getDurableLocalNetIds() {
        return durableLocalNetIds;
    }
    /** Validate the canonical compatibility encoding only. */
    static boolean isQualifiedId(String value,
            FunctionalBlockDescriptor.EntityKind kind) {
        if (value == null || kind == null || !value.startsWith(ENCODING_PREFIX)) {
            return false;
        }
        String[] fields = value.substring(ENCODING_PREFIX.length())
                .split("/", -1);
        if (fields.length != 4 || !kind.getToken().equals(fields[2])) {
            return false;
        }
        int separator = fields[0].lastIndexOf('@');
        if (separator < 1) {
            return false;
        }
        try {
            String versionText = fields[0].substring(separator + 1);
            int version = Integer.parseInt(versionText);
            if (version <= 0 || !Integer.toString(version).equals(versionText)) {
                return false;
            }
            FunctionalBlockDescriptor.requireId(
                    fields[0].substring(0, separator), "deviceSchemaId");
            FunctionalBlockDescriptor.requireId(fields[1], "instanceKey");
            FunctionalBlockDescriptor.requireId(fields[3], "localId");
            return true;
        } catch (IllegalArgumentException invalid) {
            return false;
        }
    }

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
        return ENCODING_PREFIX + deviceSchemaId + "@"
                + deviceSchemaVersion + "/" + validatedInstanceKey + "/"
                + kind.getToken() + "/" + validatedLocalId;
    }

    String instanceIdFor(String instanceKey) {
        String key = validatedBlock(instanceKey).getInstanceKey();
        BlockRealizationIdentity realization = realizationFor(key);
        return ROLE_PREFIX + deviceSchemaId + "@" + deviceSchemaVersion
                + "/" + key + "/"
                + realization.getRole().toCanonical();
    }

    String durableIdFor(String instanceKey,
            FunctionalBlockDescriptor.EntityKind kind, String localId) {
        String key = validatedBlock(instanceKey).getInstanceKey();
        String validatedLocalId = FunctionalBlockDescriptor.requireId(
                localId, "localId");
        if (kind == null) {
            throw new BlockContractException(
                    BlockContractException.Code.MISSING_DECLARATION,
                    "kind", key, "Entity kind is required");
        }
        FunctionalBlockDescriptor block = validatedBlock(key);
        if (!block.declares(kind, validatedLocalId)) {
            throw new BlockContractException(
                    BlockContractException.Code.DANGLING_REFERENCE,
                    "localId", validatedLocalId,
                    "Entity is not declared by block instance");
        }
        BlockRealizationIdentity realization = realizationFor(key);
        if (kind == FunctionalBlockDescriptor.EntityKind.ROLE) {
            return stableExternalId(ROLE_PREFIX, key,
                    realization.getRole(), validatedLocalId);
        }
        if (kind == FunctionalBlockDescriptor.EntityKind.PORT) {
            return stableExternalId(PORT_PREFIX, key,
                    realization.getRole(), validatedLocalId);
        }
        return variantOwnedId(realization, kind.getToken(), validatedLocalId);
    }

    String durableTerminalIdFor(String instanceKey,
            String componentLocalId, String terminalName) {
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
        BlockRealizationIdentity realization = realizationFor(key);
        return variantOwnedId(realization, TERMINAL_TOKEN,
                terminalKey(component, terminal));
    }

    /** Keep the legacy readable form while framing dotted IDs injectively. */
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

    private String stableExternalId(String prefix, String instanceKey,
            ChallengeDescriptor.VersionedId role, String localId) {
        return prefix + deviceSchemaId + "@" + deviceSchemaVersion + "/"
                + instanceKey + "/" + role.toCanonical() + "/" + localId;
    }

    private String variantOwnedId(BlockRealizationIdentity realization,
            String kind, String localId) {
        return REALIZATION_PREFIX + deviceSchemaId + "@"
                + deviceSchemaVersion + "/" + realization.getInstanceKey()
                + "/" + realization.getProvider().toCanonical() + "/"
                + realization.getVariant().toCanonical() + "/" + kind
                + "/" + localId;
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
    private static Map<String, String> deriveDurableLocalNetIds(
            Map<String, FunctionalBlockDescriptor> blockMap,
            Map<String, BlockRealizationIdentity> realizationMap,
            String schemaId, int schemaVersion) {
        TreeMap<String, String> result = new TreeMap<String, String>();
        for (Map.Entry<String, FunctionalBlockDescriptor> entry
                : blockMap.entrySet()) {
            String instance = entry.getKey();
            FunctionalBlockDescriptor descriptor = entry.getValue();
            for (String localNet : descriptor.getNetIds()) {
                String legacy = ENCODING_PREFIX + schemaId + "@"
                        + schemaVersion + "/" + instance + "/net/"
                        + localNet;
                BlockRealizationIdentity realization = realizationMap.get(instance);
                String durable = realizationMap.isEmpty() ? legacy
                        : REALIZATION_PREFIX + schemaId + "@"
                        + schemaVersion + "/" + instance + "/"
                        + realization.getProvider().toCanonical() + "/"
                        + realization.getVariant().toCanonical()
                        + "/net/" + localNet;
                result.put(legacy, durable);
            }
        }
        return Collections.unmodifiableMap(result);
    }
}
