package com.lushprojects.circuitjs1.client;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Vector;

/** External baseline fixture; invokes current production methods only. */
public final class A02F3Baseline {

    public static void main(String[] args) throws Exception {
        System.out.println("F3_BASELINE_BEGIN");
        printMixedSelection();
        printHardcodedProofRoutes();
        try {
            printGeneratedCorpus();
        } catch (RuntimeException expected) {
            System.out.println("GENERATED_CORPUS|BLOCKED|" + expected.getClass().getSimpleName() +
                "|" + expected.getMessage());
        }
        System.out.println("F3_BASELINE_END");
    }

    private static void printGeneratedCorpus() {
        String[] families = {
            QuickPlayFamilyRegistry.LED_INDICATOR,
            QuickPlayFamilyRegistry.DIODE_PROTECTED_INDICATOR,
            QuickPlayFamilyRegistry.PARALLEL_DUAL_INDICATOR,
            QuickPlayFamilyRegistry.RC_DELAY,
            QuickPlayFamilyRegistry.NPN_LOW_SIDE_SWITCH,
            QuickPlayFamilyRegistry.NMOS_LOW_SIDE_SWITCH
        };
        for (String family : families) {
            GeneratedBoardInstance instance = QuickPlayFamilyRegistry.generate(family, 0);
            Vector<GeneratedFaultCandidate> candidates = instance.getFaultCandidates();
            System.out.println("CORPUS|" + family + "|seed=0|total=" + candidates.size() +
                "|admitted=" + instance.getDiagnosticSolvabilityContract().getAdmittedCandidateCount() +
                "|owners=" + instance.getDiagnosticSolvabilityContract().getAdmittedPhysicalOwnerCount());
            for (GeneratedFaultCandidate candidate : candidates)
                System.out.println("CANDIDATE|" + family + "|" + candidate.getFault().getId() +
                    "|type=" + candidate.getFault().getType() + "|target=" +
                    candidate.getFault().getTargetComponentId() + "|compatible=" +
                    candidate.isCompatible() + "|admitted=" + candidate.isAdmitted());
        }
    }

    private static void printMixedSelection() {
        Vector<GeneratedFaultCandidate> mixed = new Vector<GeneratedFaultCandidate>();
        mixed.add(GeneratedFaultEngine.resistorOpen("F3_BAD_OPEN", "F3_FIXTURE", 0,
            "R_BAD", new SwitchElm(0, 0)));
        mixed.add(GeneratedFaultEngine.resistorOpen("F3_GOOD_OPEN", "F3_FIXTURE", 0,
            "R_GOOD", new SwitchElm(0, 0)));
        GeneratedFaultCandidate invalid = mixed.firstElement();
        GeneratedFaultServiceability noRepair = new GeneratedFaultServiceability(
            GeneratedFaultLocus.componentInternal("R_BAD"),
            new String[] { GeneratedFaultServiceability.OBSERVE_COMPONENT_TERMINALS },
            new String[] { WorkbenchOperation.REMOVE }, new String[0],
            GeneratedBoardOperationIds.CUSTOMER_RETEST);
        GeneratedFault fault = new GeneratedFault("F3_BAD_OPEN", GeneratedFaultType.RESISTOR_OPEN,
            "R_BAD", "F3_FIXTURE", 0);
        invalid = new GeneratedFaultCandidate(new GeneratedFaultBinding(fault,
            new SwitchOpenFaultEffect(new SwitchElm(0, 0)), noRepair), true);
        mixed.set(0, invalid);
        System.out.println("MIXED|count=" + GeneratedDiagnosticSolvabilityAdmission
            .getAdmittedCandidateCount(mixed) + "|owners=" + GeneratedDiagnosticSolvabilityAdmission
            .getPhysicalOwnerCount(mixed));
        try {
            GeneratedFaultEngine.select(0, mixed);
            System.out.println("MIXED_SELECT|RETURNED");
        } catch (RuntimeException expected) {
            System.out.println("MIXED_SELECT|THREW|" + expected.getClass().getSimpleName() +
                "|" + expected.getMessage());
        }
        try {
            GeneratedFaultEngine.select(GeneratedFaultType.RESISTOR_OPEN, mixed);
            System.out.println("MIXED_REQUIRED_SELECT|RETURNED");
        } catch (RuntimeException expected) {
            System.out.println("MIXED_REQUIRED_SELECT|THREW|" + expected.getClass().getSimpleName() +
                "|" + expected.getMessage());
        }
    }

    private static void printHardcodedProofRoutes() throws Exception {
        Class<?> routeClass = Class.forName(
            "com.lushprojects.circuitjs1.client.Task41DeveloperVerifier$Route");
        Constructor<?> ctor = routeClass.getDeclaredConstructor(String.class, long.class,
            GeneratedFaultType.class);
        ctor.setAccessible(true);
        Method method = Task41DeveloperVerifier.class.getDeclaredMethod("candidateRoutes",
            routeClass, GeneratedBoardInstance.class);
        method.setAccessible(true);
        String[] families = {
            QuickPlayFamilyRegistry.LED_INDICATOR,
            QuickPlayFamilyRegistry.DIODE_PROTECTED_INDICATOR,
            QuickPlayFamilyRegistry.PARALLEL_DUAL_INDICATOR,
            QuickPlayFamilyRegistry.RC_DELAY,
            QuickPlayFamilyRegistry.NPN_LOW_SIDE_SWITCH,
            QuickPlayFamilyRegistry.NMOS_LOW_SIDE_SWITCH
        };
        Field type = routeClass.getDeclaredField("type");
        Field target = routeClass.getDeclaredField("targetComponentId");
        type.setAccessible(true); target.setAccessible(true);
        for (String family : families) {
            Object route = ctor.newInstance(family, 0L, null);
            Vector<?> result = (Vector<?>) method.invoke(null, route, null);
            StringBuilder row = new StringBuilder("PROOF_ROUTES|").append(family).append("|");
            for (Object candidate : result) {
                if (row.charAt(row.length() - 1) != '|') row.append(',');
                row.append(type.get(candidate)).append('@').append(target.get(candidate));
            }
            System.out.println(row.toString());
        }
    }
}