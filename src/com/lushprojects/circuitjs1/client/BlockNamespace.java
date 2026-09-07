package com.lushprojects.circuitjs1.client;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;

/**
 * Immutable namespace for block-local logical identities within one device
 * schema.  It performs lookup and encoding only; it does not allocate runtime
 * elements or merge electrical nets.
 *
 * <p>Encoding version 1 is exactly
 * {@code tsj-block-v1/<device-schema>@<positive-schema-version>/<instance>/<kind>/<local>}.
 * Device schema, instance, and local identifiers use the ASCII grammar
 * {@code [A-Za-z0-9_][A-Za-z0-9_.-]{0,127}}.  The schema version is positive
 * and is part of the namespace; a block type or block schema version is
 * descriptor metadata and is intentionally not another encoded segment.</p>
 */
final class BlockNamespace {
    private static final String ENCODING_PREFIX = "tsj-block-v1/";

    private final String deviceSchemaId;
    private final int deviceSchemaVersion;
    private final Map<String, FunctionalBlockDescriptor> blocks;

    BlockNamespace(String deviceSchemaId, int deviceSchemaVersion,
            Collection<FunctionalBlockDescriptor> blocks) {
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
        TreeMap<String, FunctionalBlockDescriptor> blockMap
                = new TreeMap<String, FunctionalBlockDescriptor>();
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

    /** Validate the canonical encoding only; live consumers still prove declaration/ownership. */
    static boolean isQualifiedId(String value, FunctionalBlockDescriptor.EntityKind kind) {
        if (value == null || kind == null || !value.startsWith(ENCODING_PREFIX)) return false;
        String[] fields = value.substring(ENCODING_PREFIX.length()).split("/", -1);
        if (fields.length != 4 || !kind.getToken().equals(fields[2])) return false;
        int separator = fields[0].lastIndexOf('@');
        if (separator < 1) return false;
        try {
            String versionText = fields[0].substring(separator + 1);
            int version = Integer.parseInt(versionText);
            if (version <= 0 || !Integer.toString(version).equals(versionText)) return false;
            FunctionalBlockDescriptor.requireId(fields[0].substring(0, separator), "deviceSchemaId");
            FunctionalBlockDescriptor.requireId(fields[1], "instanceKey");
            FunctionalBlockDescriptor.requireId(fields[3], "localId");
            return true;
        } catch (IllegalArgumentException invalid) { return false; }
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
}
