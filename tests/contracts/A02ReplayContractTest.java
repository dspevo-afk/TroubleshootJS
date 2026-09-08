package com.lushprojects.circuitjs1.client;

/** A02's narrow dispatch boundary; the existing suites qualify assembly recipes. */
public final class A02ReplayContractTest {
    private static int assertions;

    public static void main(String[] args) {
        String[] seeded = { "LED_INDICATOR", "DIODE_PROTECTED_INDICATOR",
            "PARALLEL_DUAL_INDICATOR" };
        long[] seeds = { 0, 2, 3, Long.MIN_VALUE, Long.MAX_VALUE };
        for (String family : seeded) {
            for (long seed : seeds) {
                ChallengeDescriptor legacy = ChallengeDescriptor.parse(
                    ChallengeDescriptor.legacy(family, seed).toCanonical());
                ChallengeDescriptor corrected = ChallengeDescriptor.parse(
                    ChallengeDescriptor.correctedSeeded(family, seed).toCanonical());
                LegacyChallengeReplay.requireSupported(legacy);
                LegacyChallengeReplay.requireSupported(corrected);
                require(legacy.getGenerator().getVersion() == 1 &&
                    corrected.getGenerator().getVersion() == 2, "explicit generator version");
                require(legacy.getGeometryVersion().getValue() == 3 &&
                    corrected.getGeometryVersion().getValue() == 3 &&
                    PcbGeometryContractVersion.CURRENT == 3, "package contract remains v3");
                require(LegacyChallengeReplay.layoutAlgorithmVersion(legacy) == 3 &&
                    LegacyChallengeReplay.layoutAlgorithmVersion(corrected) == 4,
                    "old/new descriptor dispatches to old/new algorithm");
                require(legacy.getRootSeed() == seed && corrected.getRootSeed() == seed,
                    "exact signed seed retained");
                require(LegacyChallengeReplay.describe(legacy).contains(
                    "reserved-not-consumed-by-legacy-leaf@1") &&
                    LegacyChallengeReplay.describe(corrected).contains(
                    "reserved-not-consumed-by-legacy-leaf@2"), "versioned diagnostics");
                reject(corrected.toCanonical().replace("geometry=3", "geometry=4"),
                    ChallengeContractException.Code.UNSUPPORTED_VERSION, "geometry");
                reject(corrected.toCanonical().replace("legacy-leaf@2", "legacy-leaf@99"),
                    ChallengeContractException.Code.UNSUPPORTED_VERSION, "generator");
            }
        }
        for (String fixed : new String[] { "RC_DELAY", "NPN_LOW_SIDE_SWITCH", "NMOS_LOW_SIDE_SWITCH" }) {
            ChallengeDescriptor legacy = ChallengeDescriptor.legacy(fixed, 0);
            LegacyChallengeReplay.requireSupported(legacy);
            require(LegacyChallengeReplay.layoutAlgorithmVersion(legacy) == 3,
                "fixed legacy family remains supported");
            reject(ChallengeDescriptor.correctedSeeded(fixed, 0).toCanonical(),
                ChallengeContractException.Code.UNSUPPORTED_VERSION, "generator");
        }
        reject(ChallengeDescriptor.correctedSeeded("UNKNOWN_FAMILY", 0).toCanonical(),
            ChallengeContractException.Code.UNSUPPORTED_ID, "device-intent");
        System.out.println("PASS: A02ReplayContractTest assertions=" + assertions);
    }

    private static void reject(String encoded, ChallengeContractException.Code code, String field) {
        try {
            LegacyChallengeReplay.requireSupported(ChallengeDescriptor.parse(encoded));
            throw new AssertionError("Accepted unsupported replay: " + field);
        } catch (ChallengeContractException failure) {
            require(failure.getCode() == code && field.equals(failure.getFieldId()),
                "stable replay rejection " + field);
        }
    }

    private static void require(boolean condition, String label) {
        assertions++;
        if (!condition) throw new AssertionError(label);
    }
}
