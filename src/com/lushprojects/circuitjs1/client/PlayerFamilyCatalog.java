package com.lushprojects.circuitjs1.client;

import java.util.Vector;

/** Public content names and candidate routing. Final labels require live assessment. */
final class PlayerFamilyCatalog {
    private PlayerFamilyCatalog() { }
    static Vector<String> families() {
        Vector<String> result = QuickPlayFamilyRegistry.getNormalPlayerFamilyIds();
        result.add(ControlledIndicatorBlockContributions.FAMILY_ID); return result;
    }
    static boolean contains(String id) { return families().contains(id); }
    static long selectNormalPlayerSeed(String id, long entropy) {
        if (!contains(id)) throw new IllegalArgumentException("Unknown player family");
        return entropy;
    }
    static String name(String id) {
        if (QuickPlayFamilyRegistry.LED_INDICATOR.equals(id)) return "Indicator board";
        if (QuickPlayFamilyRegistry.DIODE_PROTECTED_INDICATOR.equals(id)) return "Protected indicator";
        if (QuickPlayFamilyRegistry.PARALLEL_DUAL_INDICATOR.equals(id)) return "Dual indicator";
        if (QuickPlayFamilyRegistry.RC_DELAY.equals(id)) return "Timing board";
        if (QuickPlayFamilyRegistry.NPN_LOW_SIDE_SWITCH.equals(id)) return "BJT output driver";
        if (QuickPlayFamilyRegistry.NMOS_LOW_SIDE_SWITCH.equals(id)) return "MOSFET output driver";
        if (QuickPlayFamilyRegistry.RELAY_OUTPUT.equals(id)) return "Relay output board";
        if (Rb15Plan.FAMILY_ID.equals(id)) return "Procedural control board";
        if (ControlledIndicatorBlockContributions.FAMILY_ID.equals(id)) return "Two-channel controller";
        throw new IllegalArgumentException("Unknown player family");
    }
    static DifficultyProfile candidateProfile(String id) {
        if (!contains(id)) throw new IllegalArgumentException("Unknown player family");
        return ControlledIndicatorBlockContributions.FAMILY_ID.equals(id) ? DifficultyProfile.MEDIUM : DifficultyProfile.EASY;
    }
    static String fromRoute(String route) {
        String[] names = {"led", "diode", "parallel", "rc", "npn", "nmos", "relay", "control-board", "controlled-indicator"};
        Vector<String> ids = families();
        for (int i = 0; i < names.length; i++) if (names[i].equals(route)) return ids.get(i);
        throw new IllegalArgumentException("Unsupported challenge route");
    }
}
