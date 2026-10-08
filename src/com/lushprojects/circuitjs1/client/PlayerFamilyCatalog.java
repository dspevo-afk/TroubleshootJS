package com.lushprojects.circuitjs1.client;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Vector;

/** Normal-player catalog projection and staged-family provider registration. */
final class PlayerFamilyCatalog {
    static final String EXECUTION_DECLARATION_VERSION = "PLAYER_FAMILY_EXECUTION@1";
    private static final String[] BUILT_IN_ROUTES = {
        "led", "diode", "parallel", "rc", "npn", "nmos", "relay",
        "sensor-control", "control-board", "controlled-indicator"
    };
    private static final RegistrationBoundary CURRENT = currentRegistration();

    private PlayerFamilyCatalog() { }

    private static RegistrationBoundary currentRegistration() {
        RegistrationBoundary result = new RegistrationBoundary();
        result.registerStagedFamily(new Rb30PlayerFamilyCapability(), true);
        result.registerStagedFamily(new Rb56PlayerFamilyCapability(), false);
        return result;
    }

    /** Enabled families shown by the ordinary player menu. */
    static Vector<String> families() { return CURRENT.families(); }

    /** Every installed family identity, including providers held from normal admission. */
    static Vector<String> registeredFamilies() { return CURRENT.registeredFamilies(); }

    /** True means registered; normal publication is checked separately. */
    static boolean contains(String id) { return CURRENT.isRegistered(id); }
    static boolean isRegistered(String id) { return CURRENT.isRegistered(id); }
    static boolean isNormalPlayerEnabled(String id) { return CURRENT.isNormalPlayerEnabled(id); }

    /** Called by normal admission before any active job or owner is changed. */
    static void requireNormalPlayerEnabled(String id) {
        if (!CURRENT.isNormalPlayerEnabled(id))
            throw new IllegalArgumentException("This family is not enabled for normal-player generation");
    }

    static long selectNormalPlayerSeed(String id, long entropy) {
        requireNormalPlayerEnabled(id);
        return entropy;
    }

    static String name(String id) { return CURRENT.name(id); }
    static DifficultyProfile candidateProfile(String id) {
        return CURRENT.executionDeclaration(id).candidateProfile;
    }
    static ExecutionDeclaration executionDeclaration(String id) {
        return CURRENT.executionDeclaration(id);
    }
    static StagedFamilyCapability stagedCapability(String id) {
        return CURRENT.stagedCapability(id);
    }
    static String fromRoute(String route) { return CURRENT.fromRoute(route); }

    /** Independent registration view used by contracts and other catalog owners. */
    static RegistrationBoundary newRegistrationBoundary() { return CURRENT.copy(); }

    static final class RegistrationBoundary {
        private final LinkedHashMap<String, StagedFamilyRegistration> staged =
            new LinkedHashMap<String, StagedFamilyRegistration>();

        private RegistrationBoundary() { }

        Vector<String> families() {
            Vector<String> result = baseFamilies();
            for (Map.Entry<String, StagedFamilyRegistration> entry : staged.entrySet())
                if (entry.getValue().normalPlayerEnabled) result.add(entry.getKey());
            return result;
        }

        Vector<String> registeredFamilies() {
            Vector<String> result = baseFamilies();
            for (String id : staged.keySet()) result.add(id);
            return result;
        }

        boolean isRegistered(String id) {
            return isBaseFamily(id) || staged.containsKey(id);
        }

        boolean isNormalPlayerEnabled(String id) {
            if (isBaseFamily(id)) return true;
            StagedFamilyRegistration registration = staged.get(id);
            return registration != null && registration.normalPlayerEnabled;
        }

        void requireNormalPlayerEnabled(String id) {
            if (!isNormalPlayerEnabled(id))
                throw new IllegalArgumentException("This family is not enabled for normal-player generation");
        }

        void registerStagedFamily(StagedFamilyCapability capability, boolean normalPlayerEnabled) {
            if (capability == null || capability.familyId() == null || capability.familyId().length() == 0 ||
                    capability.displayName() == null || capability.displayName().length() == 0 ||
                    (normalPlayerEnabled && (capability.routeId() == null || capability.routeId().length() == 0)) ||
                    capability.candidateProfile() == null || capability.executionPolicy() == null ||
                    capability.physicalAdmissionIdentity() == null ||
                    capability.physicalAdmissionIdentity().length() == 0)
                throw new IllegalArgumentException("Incomplete staged-family registration");
            String id = capability.familyId();
            if (isBaseFamily(id) || staged.containsKey(id))
                throw new IllegalArgumentException("Duplicate registered player family: " + id);
            if (capability.routeId() != null && capability.routeId().length() > 0) {
                for (String route : BUILT_IN_ROUTES)
                    if (route.equals(capability.routeId()))
                        throw new IllegalArgumentException("Duplicate staged-family route: " + capability.routeId());
                for (StagedFamilyRegistration registration : staged.values())
                    if (capability.routeId().equals(registration.capability.routeId()))
                        throw new IllegalArgumentException("Duplicate staged-family route: " + capability.routeId());
            }
            staged.put(id, new StagedFamilyRegistration(capability, normalPlayerEnabled));
        }

        void setNormalPlayerEnabled(String id, boolean enabled) {
            StagedFamilyRegistration registration = staged.get(id);
            if (registration == null)
                throw new IllegalArgumentException("Unknown staged-family registration");
            staged.put(id, new StagedFamilyRegistration(registration.capability, enabled));
        }

        StagedFamilyCapability stagedCapability(String id) {
            StagedFamilyRegistration registration = staged.get(id);
            return registration == null ? null : registration.capability;
        }

        ExecutionDeclaration executionDeclaration(String id) {
            if (!isRegistered(id)) throw new IllegalArgumentException("Unknown player family");
            StagedFamilyCapability capability = stagedCapability(id);
            DifficultyProfile profile = capability != null ? capability.candidateProfile() :
                ControlledIndicatorBlockContributions.FAMILY_ID.equals(id) ?
                    DifficultyProfile.MEDIUM : DifficultyProfile.EASY;
            GenerationExecutionPolicy policy = capability != null ? capability.executionPolicy() :
                GenerationExecutionPolicy.SMALL_BOARD;
            String physicalAdmission = capability != null ? capability.physicalAdmissionIdentity() :
                SupportedEnvelope.current().identity();
            return new ExecutionDeclaration(id, profile, policy, physicalAdmission,
                isNormalPlayerEnabled(id));
        }

        String name(String id) {
            StagedFamilyCapability capability = stagedCapability(id);
            if (capability != null) return capability.displayName();
            if (!isBaseFamily(id)) throw new IllegalArgumentException("Unknown player family");
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
            throw new IllegalArgumentException("Unknown player family");
        }

        String fromRoute(String route) {
            Vector<String> ids = baseFamilies();
            int count = Math.min(BUILT_IN_ROUTES.length, ids.size());
            for (int i = 0; i < count; i++)
                if (BUILT_IN_ROUTES[i].equals(route)) return ids.get(i);
            for (Map.Entry<String, StagedFamilyRegistration> entry : staged.entrySet()) {
                StagedFamilyRegistration registration = entry.getValue();
                if (registration.normalPlayerEnabled && registration.capability.routeId() != null &&
                        registration.capability.routeId().equals(route))
                    return entry.getKey();
            }
            throw new IllegalArgumentException("Unsupported challenge route");
        }

        boolean supportsQuickPlay(String id, DifficultyProfile profile) {
            if (!isRegistered(id) || profile == null || !profile.isAvailable()) return false;
            return profile == executionDeclaration(id).candidateProfile;
        }

        RegistrationBoundary copy() {
            RegistrationBoundary result = new RegistrationBoundary();
            for (Map.Entry<String, StagedFamilyRegistration> entry : staged.entrySet()) {
                StagedFamilyRegistration registration = entry.getValue();
                result.registerStagedFamily(registration.capability, registration.normalPlayerEnabled);
            }
            return result;
        }

        private static Vector<String> baseFamilies() {
            Vector<String> result = QuickPlayFamilyRegistry.getNormalPlayerFamilyIds();
            result.add(ControlledIndicatorBlockContributions.FAMILY_ID);
            return result;
        }

        private static boolean isBaseFamily(String id) {
            return id != null && baseFamilies().contains(id);
        }
    }

    private static final class StagedFamilyRegistration {
        final StagedFamilyCapability capability;
        final boolean normalPlayerEnabled;
        StagedFamilyRegistration(StagedFamilyCapability capability, boolean normalPlayerEnabled) {
            this.capability = capability;
            this.normalPlayerEnabled = normalPlayerEnabled;
        }
    }

    static final class ExecutionDeclaration {
        final String familyId;
        final DifficultyProfile candidateProfile;
        final GenerationExecutionPolicy policy;
        final String physicalAdmissionIdentity;
        final boolean normalPlayerEnabled;

        private ExecutionDeclaration(String familyId, DifficultyProfile candidateProfile,
                GenerationExecutionPolicy policy, String physicalAdmissionIdentity,
                boolean normalPlayerEnabled) {
            if (familyId == null || candidateProfile == null || policy == null ||
                    physicalAdmissionIdentity == null || physicalAdmissionIdentity.length() == 0)
                throw new IllegalArgumentException("Incomplete player execution declaration");
            this.familyId = familyId;
            this.candidateProfile = candidateProfile;
            this.policy = policy;
            this.physicalAdmissionIdentity = physicalAdmissionIdentity;
            this.normalPlayerEnabled = normalPlayerEnabled;
        }

        String canonical() {
            return EXECUTION_DECLARATION_VERSION + ";family=" + familyId + ";candidateProfile=" +
                candidateProfile.name() + "@" + DifficultyProfile.VERSION + ";policy=" +
                policy.canonical() + ";physicalAdmission=" + physicalAdmissionIdentity;
        }
    }
}
