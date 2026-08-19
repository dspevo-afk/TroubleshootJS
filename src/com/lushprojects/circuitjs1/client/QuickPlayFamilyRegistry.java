package com.lushprojects.circuitjs1.client;

import java.util.Vector;

/**
 * Normal-player family boundary for Quick Play. The registry deliberately
 * excludes developer-only fault variants and future families.
 */
final class QuickPlayFamilyRegistry {
    static final String LED_INDICATOR = "LED_INDICATOR";
    static final String DIODE_PROTECTED_INDICATOR = "DIODE_PROTECTED_INDICATOR";
    static final String PARALLEL_DUAL_INDICATOR = "PARALLEL_DUAL_INDICATOR";
    static final String RC_DELAY = "RC_DELAY";
    static final String NPN_LOW_SIDE_SWITCH = "NPN_LOW_SIDE_SWITCH";
    static final String NMOS_LOW_SIDE_SWITCH = "NMOS_LOW_SIDE_SWITCH";
    private static final long[] LEGACY_NORMAL_PLAYER_SEEDS = { 0, 2, 3 };
    // Keep the established LED seeds 0/2/3 unchanged; seed 4 is the first
    // normal envelope entry for the additional LED-owned fault route.
    private static final long[] LED_NORMAL_PLAYER_SEEDS = { 0, 2, 3, 4 };
    private static final long[] NPN_NORMAL_PLAYER_SEEDS = { 0, 1, 2 };
    private static final long[] NMOS_NORMAL_PLAYER_SEEDS = { 0, 1, 2 };

    private QuickPlayFamilyRegistry() { }

    static Vector<String> getNormalPlayerFamilyIds() {
        Vector<String> result = new Vector<String>();
        result.add(LED_INDICATOR);
        result.add(DIODE_PROTECTED_INDICATOR);
        result.add(PARALLEL_DUAL_INDICATOR);
        result.add(RC_DELAY);
        result.add(NPN_LOW_SIDE_SWITCH);
        result.add(NMOS_LOW_SIDE_SWITCH);
        return result;
    }

    static boolean isNormalPlayerEligible(String familyId) {
        return LED_INDICATOR.equals(familyId) ||
            DIODE_PROTECTED_INDICATOR.equals(familyId) ||
            PARALLEL_DUAL_INDICATOR.equals(familyId) || RC_DELAY.equals(familyId) ||
            NPN_LOW_SIDE_SWITCH.equals(familyId) || NMOS_LOW_SIDE_SWITCH.equals(familyId);
    }

    static GeneratedBoardInstance generate(String familyId, long seed) {
        if (LED_INDICATOR.equals(familyId))
            return new LedIndicatorGenerator().generate(seed);
        if (DIODE_PROTECTED_INDICATOR.equals(familyId))
            return new DiodeProtectedIndicatorGenerator().generate(seed);
        if (PARALLEL_DUAL_INDICATOR.equals(familyId))
            return new ParallelDualIndicatorGenerator().generate(seed);
        if (RC_DELAY.equals(familyId))
            return new RcDelayGenerator().generate(seed);
        if (NPN_LOW_SIDE_SWITCH.equals(familyId))
            return new NpnLowSideSwitchGenerator().generate(seed);
        if (NMOS_LOW_SIDE_SWITCH.equals(familyId))
            return new NmosLowSideSwitchGenerator().generate(seed);
        throw new IllegalArgumentException("Quick Play family is not normal-player eligible: " +
            familyId);
    }

    /**
     * The current family generators have a small, validated seed envelope.
     * Selection may vary those seeds, but generation remains deterministic.
     */
    static long selectNormalPlayerSeed(String familyId, long selectionValue) {
        if (!isNormalPlayerEligible(familyId))
            throw new IllegalArgumentException("Quick Play family is not normal-player eligible: " +
                familyId);
        long[] normalPlayerSeeds = seedsFor(familyId);
        for (long seed : normalPlayerSeeds)
            if (seed == selectionValue)
                return seed;
        int index = (int) (selectionValue % normalPlayerSeeds.length);
        if (index < 0)
            index += normalPlayerSeeds.length;
        return normalPlayerSeeds[index];
    }

    private static long[] seedsFor(String familyId) {
        if (LED_INDICATOR.equals(familyId)) return LED_NORMAL_PLAYER_SEEDS;
        if (NPN_LOW_SIDE_SWITCH.equals(familyId)) return NPN_NORMAL_PLAYER_SEEDS;
        if (NMOS_LOW_SIDE_SWITCH.equals(familyId)) return NMOS_NORMAL_PLAYER_SEEDS;
        return
            LEGACY_NORMAL_PLAYER_SEEDS;
    }
}
