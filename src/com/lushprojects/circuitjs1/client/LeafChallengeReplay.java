package com.lushprojects.circuitjs1.client;

import java.util.Map;
import java.util.TreeMap;

/**
 * Resolver for the current replayable leaf challenges.
 *
 * <p>The descriptor is one current interpretation: generator {@code leaf@1}
 * always reaches the corrected layout algorithm and the exact signed seed is
 * passed to the selected family.  Historical descriptors are rejected before
 * any board, solver element, or runtime owner is created.</p>
 */
final class LeafChallengeReplay {
    private LeafChallengeReplay() { }

    static GeneratedBoardInstance generate(ChallengeDescriptor descriptor) {
        requireSupported(descriptor);
        return QuickPlayFamilyRegistry.generate(descriptor.getDeviceIntent().getId(),
            descriptor.getRootSeed());
    }

    static int layoutAlgorithmVersion(ChallengeDescriptor descriptor) {
        requireSupported(descriptor);
        return SeededPcbLayoutGenerator.CURRENT_VERSION;
    }

    /** Resolve all descriptor inputs before constructing live elements. */
    static void requireSupported(ChallengeDescriptor descriptor) {
        ChallengeContractException.required(descriptor, "descriptor");
        if (descriptor.getSchemaVersion() != ChallengeDescriptor.SCHEMA_VERSION)
            reject(ChallengeContractException.Code.UNSUPPORTED_VERSION, "schemaVersion");
        requireIdentity(descriptor.getGenerator(), ChallengeDescriptor.LEAF_GENERATOR_ID,
            ChallengeDescriptor.LEAF_GENERATOR_VERSION, "generator");
        if (!QuickPlayFamilyRegistry.isNormalPlayerEligible(
                descriptor.getDeviceIntent().getId()))
            reject(ChallengeContractException.Code.UNSUPPORTED_ID, "device-intent");
        if (descriptor.getDeviceIntent().getVersion() != ChallengeDescriptor.LEAF_INTENT_VERSION)
            reject(ChallengeContractException.Code.UNSUPPORTED_VERSION, "device-intent");
        requireIdentity(descriptor.getDifficultyProfile(),
            ChallengeDescriptor.QUICK_PLAY_DIFFICULTY_ID,
            ChallengeDescriptor.QUICK_PLAY_DIFFICULTY_VERSION, "difficulty-profile");
        if (descriptor.getGeometryVersion().getValue() != ChallengeDescriptor.GEOMETRY_VERSION
                || PcbGeometryContractVersion.CURRENT != ChallengeDescriptor.GEOMETRY_VERSION)
            reject(ChallengeContractException.Code.UNSUPPORTED_VERSION, "geometry");
        if (!descriptor.getConstraints().isUnspecified())
            reject(ChallengeContractException.Code.UNSUPPORTED_CONSTRAINT, "constraints");
    }

    /** Pure diagnostic text; this method never mutates the active challenge. */
    static String describe(ChallengeDescriptor descriptor) {
        ChallengeContractException.required(descriptor, "descriptor");
        StringBuilder result = new StringBuilder(descriptor.toCanonical());
        result.append("\nnamed-derivation=").append(NamedRandomStreams.DERIVATION_VERSION)
            .append("\nnamed-stream-use=reserved-not-consumed-by-leaf@")
            .append(descriptor.getGenerator().getVersion());
        try {
            requireSupported(descriptor);
            result.append("\nreplay-resolution=supported;admission=not-assessed-by-description");
        } catch (ChallengeContractException rejection) {
            result.append("\nreplay-rejection=").append(rejection.getCode().name())
                .append(";field=").append(rejection.getFieldId());
        }
        NamedRandomStreams streams = new NamedRandomStreams(NamedRandomStreams.DERIVATION_VERSION,
            descriptor.getRootSeed(), descriptor.getDeviceIntent().getId(),
            descriptor.getDeviceIntent().getVersion());
        TreeMap<String, String> seeds = new TreeMap<String, String>();
        for (NamedRandomStreams.Concern concern : NamedRandomStreams.Concern.values()) {
            if (concern != NamedRandomStreams.Concern.BLOCK &&
                    concern != NamedRandomStreams.Concern.VALUES)
                seeds.put("device/" + concern.getToken() + "/default@1",
                    Long.toString(streams.deviceSeed(concern, 1, "default")));
            seeds.put("block/leaf/" + concern.getToken() + "/default@1",
                Long.toString(streams.blockSeed("leaf", concern, 1, "default")));
        }
        for (Map.Entry<String, String> seed : seeds.entrySet())
            result.append("\nnamed-seed[").append(seed.getKey()).append("]=").append(seed.getValue());
        return result.toString();
    }

    private static void requireIdentity(ChallengeDescriptor.VersionedId actual,
            String id, int version, String field) {
        if (!id.equals(actual.getId()))
            reject(ChallengeContractException.Code.UNSUPPORTED_ID, field);
        if (actual.getVersion() != version)
            reject(ChallengeContractException.Code.UNSUPPORTED_VERSION, field);
    }

    private static void reject(ChallengeContractException.Code code, String field) {
        throw new ChallengeContractException(code, field,
            "Input is not supported by the current leaf replay adapter");
    }
}
