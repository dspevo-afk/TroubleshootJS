package com.lushprojects.circuitjs1.client;

/** Current-only leaf descriptor and replay dispatch contract. */
public final class A02ReplayContractTest {
    private static int assertions;

    public static void main(String[] args) {
        CirSim sim = new CirSim();
        sim.gridSize = 16;
        sim.gridMask = ~(sim.gridSize - 1);
        sim.gridRound = sim.gridSize / 2 - 1;
        CircuitElm.sim = sim;
        String[] families = { QuickPlayFamilyRegistry.LED_INDICATOR,
            QuickPlayFamilyRegistry.DIODE_PROTECTED_INDICATOR,
            QuickPlayFamilyRegistry.PARALLEL_DUAL_INDICATOR,
            QuickPlayFamilyRegistry.RC_DELAY,
            QuickPlayFamilyRegistry.NPN_LOW_SIDE_SWITCH,
            QuickPlayFamilyRegistry.NMOS_LOW_SIDE_SWITCH };
        long[] seeds = { 0L, 2L, Long.MIN_VALUE, Long.MAX_VALUE };
        for (String family : families) {
            for (long seed : seeds) {
                ChallengeDescriptor descriptor = ChallengeDescriptor.parse(
                    ChallengeDescriptor.current(family, seed).toCanonical());
                LeafChallengeReplay.requireSupported(descriptor);
                require(descriptor.getSchemaVersion() == ChallengeDescriptor.SCHEMA_VERSION,
                    "current schema version");
                require("leaf".equals(descriptor.getGenerator().getId()) &&
                    descriptor.getGenerator().getVersion() == 1,
                    "current leaf generator identity");
                require(descriptor.getGeometryVersion().getValue() == 3 &&
                    PcbGeometryContractVersion.CURRENT == 3,
                    "geometry contract remains independently versioned");
                require(LeafChallengeReplay.layoutAlgorithmVersion(descriptor) ==
                    SeededPcbLayoutGenerator.CURRENT_VERSION,
                    "current replay reaches the current layout");
                require(descriptor.getRootSeed() == seed, "exact signed seed retained");
                GeneratedBoardInstance replay = LeafChallengeReplay.generate(descriptor);
                require(replay.getPcbLayout().getLayoutAlgorithmVersion() ==
                    SeededPcbLayoutGenerator.CURRENT_VERSION,
                    "replayed board uses current layout");
            }
        }

        ChallengeDescriptor current = ChallengeDescriptor.current(
            QuickPlayFamilyRegistry.LED_INDICATOR, 0L);
        reject(current.toCanonical().replace("tsj-challenge/2", "tsj-challenge/1"),
            ChallengeContractException.Code.UNSUPPORTED_VERSION, "schemaVersion");
        reject(current.toCanonical().replace("leaf@1", "legacy-leaf@1"),
            ChallengeContractException.Code.UNSUPPORTED_ID, "generator");
        reject(current.toCanonical().replace("geometry=3", "geometry=4"),
            ChallengeContractException.Code.UNSUPPORTED_VERSION, "geometry");
        reject(ChallengeDescriptor.current("UNKNOWN_FAMILY", 0L).toCanonical(),
            ChallengeContractException.Code.UNSUPPORTED_ID, "device-intent");
        System.out.println("PASS: A02ReplayContractTest assertions=" + assertions);
    }

    private static void reject(String encoded, ChallengeContractException.Code code, String field) {
        try {
            LeafChallengeReplay.requireSupported(ChallengeDescriptor.parse(encoded));
            throw new AssertionError("Accepted unsupported current replay: " + field);
        } catch (ChallengeContractException failure) {
            require(failure.getCode() == code && field.equals(failure.getFieldId()),
                "stable current replay rejection " + field);
        }
    }

    private static void require(boolean condition, String label) {
        assertions++;
        if (!condition) throw new AssertionError(label);
    }
}
