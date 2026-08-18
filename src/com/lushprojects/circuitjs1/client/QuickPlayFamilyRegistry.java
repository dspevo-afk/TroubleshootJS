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
    private static final long[] NORMAL_PLAYER_SEEDS = { 0, 2, 3 };

    private QuickPlayFamilyRegistry() { }

    static Vector<String> getNormalPlayerFamilyIds() {
        Vector<String> result = new Vector<String>();
        result.add(LED_INDICATOR);
        result.add(DIODE_PROTECTED_INDICATOR);
        result.add(PARALLEL_DUAL_INDICATOR);
        result.add(RC_DELAY);
        return result;
    }

    static boolean isNormalPlayerEligible(String familyId) {
        return LED_INDICATOR.equals(familyId) ||
            DIODE_PROTECTED_INDICATOR.equals(familyId) ||
            PARALLEL_DUAL_INDICATOR.equals(familyId) || RC_DELAY.equals(familyId);
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
        for (long seed : NORMAL_PLAYER_SEEDS)
            if (seed == selectionValue)
                return seed;
        int index = (int) (selectionValue % NORMAL_PLAYER_SEEDS.length);
        if (index < 0)
            index += NORMAL_PLAYER_SEEDS.length;
        return NORMAL_PLAYER_SEEDS[index];
    }
}
