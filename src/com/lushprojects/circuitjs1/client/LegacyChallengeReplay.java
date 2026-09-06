package com.lushprojects.circuitjs1.client;

import java.util.Map;
import java.util.TreeMap;

/**
 * Explicit resolver for the accepted leaf algorithms. This adapter generates a
 * fresh challenge owner; installing it and running admission remain CirSim's
 * responsibilities. It never remaps the seed into the Quick Play envelope.
 */
final class LegacyChallengeReplay {
    private LegacyChallengeReplay() { }

    static GeneratedBoardInstance generate(ChallengeDescriptor descriptor) {
        requireSupported(descriptor);
        return QuickPlayFamilyRegistry.generate(descriptor.getDeviceIntent().getId(),
            descriptor.getRootSeed());
    }

    /** Resolve every effective input before constructing any live elements. */
    static void requireSupported(ChallengeDescriptor descriptor) {
        ChallengeContractException.required(descriptor, "descriptor");
        requireIdentity(descriptor.getGenerator(), ChallengeDescriptor.LEGACY_GENERATOR_ID,
            ChallengeDescriptor.LEGACY_GENERATOR_VERSION, "generator");
        if (!QuickPlayFamilyRegistry.isNormalPlayerEligible(descriptor.getDeviceIntent().getId()))
            reject(ChallengeContractException.Code.UNSUPPORTED_ID, "device-intent");
        if (descriptor.getDeviceIntent().getVersion() != ChallengeDescriptor.LEGACY_INTENT_VERSION)
            reject(ChallengeContractException.Code.UNSUPPORTED_VERSION, "device-intent");
        requireIdentity(descriptor.getDifficultyProfile(), ChallengeDescriptor.LEGACY_DIFFICULTY_ID,
            ChallengeDescriptor.LEGACY_DIFFICULTY_VERSION, "difficulty-profile");
        // CURRENT changing must not silently reinterpret an old descriptor.
        // Leaf algorithm changes also require a new explicit generator version.
        if (descriptor.getGeometryVersion().getValue() != ChallengeDescriptor.LEGACY_GEOMETRY_VERSION ||
                PcbGeometryContractVersion.CURRENT != ChallengeDescriptor.LEGACY_GEOMETRY_VERSION)
            reject(ChallengeContractException.Code.UNSUPPORTED_VERSION, "geometry");
        if (!descriptor.getConstraints().isUnspecified())
            reject(ChallengeContractException.Code.UNSUPPORTED_CONSTRAINT, "constraints");
    }

    /**
     * Pure diagnostic text. The caller owns the developer-only publication
     * boundary. Seed inspection does not open or draw from a random cursor.
     * The synthetic leaf scope documents reserved names, not a composed block.
     */
    static String describe(ChallengeDescriptor descriptor) {
        ChallengeContractException.required(descriptor, "descriptor");
        StringBuilder result = new StringBuilder(descriptor.toCanonical());
        result.append("\nnamed-derivation=").append(NamedRandomStreams.DERIVATION_VERSION)
            .append("\nnamed-stream-use=reserved-not-consumed-by-legacy-leaf@1");
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
        if (!id.equals(actual.getId())) reject(ChallengeContractException.Code.UNSUPPORTED_ID, field);
        if (actual.getVersion() != version)
            reject(ChallengeContractException.Code.UNSUPPORTED_VERSION, field);
    }

    private static void reject(ChallengeContractException.Code code, String field) {
        throw new ChallengeContractException(code, field, "Input is not supported by legacy-leaf@1");
    }
}
