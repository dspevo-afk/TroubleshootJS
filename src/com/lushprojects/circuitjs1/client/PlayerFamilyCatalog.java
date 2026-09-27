package com.lushprojects.circuitjs1.client;

import java.util.Vector;

/** Public content names and candidate routing. Final labels require live assessment. */
final class PlayerFamilyCatalog {
    static final String EXECUTION_DECLARATION_VERSION = "PLAYER_FAMILY_EXECUTION@1";

    private PlayerFamilyCatalog() { }
    static Vector<String> families() {
        Vector<String> result = QuickPlayFamilyRegistry.getNormalPlayerFamilyIds();
        result.add(ControlledIndicatorBlockContributions.FAMILY_ID);
        result.add(Rb30Plan.FAMILY_ID);
        return result;
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
        if (QuickPlayFamilyRegistry.SENSOR_CONTROL.equals(id)) return "Sensor control board";
        if (Rb15Plan.FAMILY_ID.equals(id)) return "Procedural control board";
        if (ControlledIndicatorBlockContributions.FAMILY_ID.equals(id)) return "Two-channel controller";
        if (Rb30Plan.FAMILY_ID.equals(id)) return "Multi-rail control board";
        throw new IllegalArgumentException("Unknown player family");
    }
    static DifficultyProfile candidateProfile(String id) {
        return executionDeclaration(id).candidateProfile;
    }
    /** Versioned normal-player execution and physical-admission declaration. */
    static ExecutionDeclaration executionDeclaration(String id) {
        if (!contains(id)) throw new IllegalArgumentException("Unknown player family");
        DifficultyProfile profile = ControlledIndicatorBlockContributions.FAMILY_ID.equals(id) ||
            Rb30Plan.FAMILY_ID.equals(id) ? DifficultyProfile.MEDIUM : DifficultyProfile.EASY;
        boolean normalMedium = Rb30Plan.FAMILY_ID.equals(id);
        return new ExecutionDeclaration(id, profile,
            normalMedium ? GenerationExecutionPolicy.NORMAL_MEDIUM : GenerationExecutionPolicy.SMALL_BOARD,
            normalMedium ? MediumBoardNormalAdmission.IDENTITY : SupportedEnvelope.current().identity());
    }
    static String fromRoute(String route) {
        String[] names = {"led", "diode", "parallel", "rc", "npn", "nmos", "relay", "sensor-control", "control-board", "controlled-indicator", "multirail-control"};
        Vector<String> ids = families();
        for (int i = 0; i < names.length; i++) if (names[i].equals(route)) return ids.get(i);
        throw new IllegalArgumentException("Unsupported challenge route");
    }

    static final class ExecutionDeclaration {
        final String familyId;
        final DifficultyProfile candidateProfile;
        final GenerationExecutionPolicy policy;
        final String physicalAdmissionIdentity;

        private ExecutionDeclaration(String familyId, DifficultyProfile candidateProfile,
                GenerationExecutionPolicy policy, String physicalAdmissionIdentity) {
            if (familyId == null || candidateProfile == null || policy == null ||
                    physicalAdmissionIdentity == null || physicalAdmissionIdentity.length() == 0)
                throw new IllegalArgumentException("Incomplete player execution declaration");
            this.familyId = familyId;
            this.candidateProfile = candidateProfile;
            this.policy = policy;
            this.physicalAdmissionIdentity = physicalAdmissionIdentity;
        }

        String canonical() {
            return EXECUTION_DECLARATION_VERSION + ";family=" + familyId + ";candidateProfile=" +
                candidateProfile.name() + "@" + DifficultyProfile.VERSION + ";policy=" +
                policy.canonical() + ";physicalAdmission=" + physicalAdmissionIdentity;
        }
    }
}
