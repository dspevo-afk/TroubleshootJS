package com.lushprojects.circuitjs1.client;

import java.util.Vector;

/** Deterministic, family-neutral v1 plan catalog. */
final class GeneratedDiagnosticPlanCatalog {
    private GeneratedDiagnosticPlanCatalog() { }

    static Vector<GeneratedDiagnosticPlan> forFamily(String familyId) {
        Vector<GeneratedDiagnosticPlan> result = new Vector<GeneratedDiagnosticPlan>();
        if (QuickPlayFamilyRegistry.LED_INDICATOR.equals(familyId))
            result.add(simple("STEADY_STATE_COMPONENT_PATH", "J1.2",
                new String[] { "J1.1", "R1.1", "R1.2", "LED1.A", "LED1.K" },
                new String[] { "DC_VOLTAGE", "RESISTANCE", "CONTINUITY" },
                new String[] { "BOARD_POWER_ON_INITIAL", "BOARD_POWER_OFF", "BOARD_POWER_ON_RETEST" },
                new String[] { WorkbenchOperation.REMOVE },
                new String[] { WorkbenchOperation.CATALOG_INSTALL },
                new String[] { GeneratedBoardOperationIds.CUSTOMER_RETEST },
                new String[] { "STEADY_STATE_SAMPLE" },
                new String[] { "VIN", "LED_NODE", "GND" }, 4, false));
        else if (QuickPlayFamilyRegistry.DIODE_PROTECTED_INDICATOR.equals(familyId))
            result.add(simple("DIODE_FORWARD_PATH_CHECK", "J1.2",
                new String[] { "J1.1", "D1.A", "D1.K", "LED1.A", "LED1.K" },
                new String[] { "DC_VOLTAGE", "RESISTANCE", "CONTINUITY", "DIODE" },
                new String[] { "BOARD_POWER_ON_INITIAL", "BOARD_POWER_OFF", "BOARD_POWER_ON_RETEST" },
                new String[] { WorkbenchOperation.REMOVE },
                new String[] { WorkbenchOperation.CATALOG_INSTALL },
                new String[] { GeneratedBoardOperationIds.CUSTOMER_RETEST },
                new String[] { "FORWARD_DROP_SAMPLE" },
                new String[] { "VIN", "DIODE_OUT", "LED_NODE", "GND" }, 4, false));
        else if (QuickPlayFamilyRegistry.PARALLEL_DUAL_INDICATOR.equals(familyId))
            result.add(simple("PARALLEL_BRANCH_COMPONENT_CHECK", "J1.2",
                new String[] { "J1.1", "R1.1", "R1.2", "LED1.A", "R2.1", "R2.2", "LED2.A" },
                new String[] { "DC_VOLTAGE", "RESISTANCE", "CONTINUITY" },
                new String[] { "BOARD_POWER_ON_INITIAL", "BOARD_POWER_OFF", "BOARD_POWER_ON_RETEST" },
                new String[] { WorkbenchOperation.REMOVE },
                new String[] { WorkbenchOperation.CATALOG_INSTALL },
                new String[] { GeneratedBoardOperationIds.CUSTOMER_RETEST },
                new String[] { "BRANCH1_SAMPLE", "BRANCH2_SAMPLE" },
                new String[] { "VIN", "BRANCH1_NODE", "BRANCH2_NODE", "GND" }, 5, true));
        else if (QuickPlayFamilyRegistry.RC_DELAY.equals(familyId))
            result.add(simpleWithWorkflow("RC_POWER_CYCLE_TEMPORAL_CHECK", "J2.2",
                new String[] { "J1.1", "J2.1", "C1.+", "C1.-" },
                new String[] { "DC_VOLTAGE", "RESISTANCE", "CONTINUITY" },
                new String[] { "BOARD_POWER_OFF_INITIAL", "RC_POWER_ON", "RC_RESIDUAL_SAMPLE",
                    "RC_EARLY_SAMPLE", "RC_LATE_SAMPLE", "BOARD_POWER_OFF_FINAL" },
                new String[] { WorkbenchOperation.REMOVE, WorkbenchOperation.LIFT_LEAD },
                new String[] { WorkbenchOperation.CATALOG_INSTALL },
                new String[] { WorkbenchOperation.RECONNECT_LEAD },
                new String[] { GeneratedBoardOperationIds.CUSTOMER_RETEST },
                new String[] { "RC_RESIDUAL_SAMPLE", "RC_EARLY_SAMPLE", "RC_LATE_SAMPLE" },
                new String[] { "VIN", "RC_OUT", "GND" }, 6, false));
        else if (QuickPlayFamilyRegistry.NPN_LOW_SIDE_SWITCH.equals(familyId))
            result.add(simple("NPN_CONTROL_AND_PUBLIC_TERMINAL_CHECK", "J1.2",
                new String[] { "J1.1", "J2.1", "Q1.B", "Q1.C", "Q1.E" },
                new String[] { "DC_VOLTAGE", "RESISTANCE", "CONTINUITY" },
                new String[] { "CONTROL_INPUT_HIGH", "CONTROL_INPUT_LOW", "BOARD_POWER_OFF",
                    "BOARD_POWER_ON" },
                new String[] { WorkbenchOperation.REMOVE },
                new String[] { WorkbenchOperation.CATALOG_INSTALL },
                new String[] { GeneratedBoardOperationIds.CONTROL_INPUT_HIGH,
                    GeneratedBoardOperationIds.CONTROL_INPUT_LOW,
                    GeneratedBoardOperationIds.CUSTOMER_RETEST },
                new String[] { "CONTROL_HIGH_SAMPLE", "CONTROL_LOW_SAMPLE" },
                new String[] { "LOAD_SUPPLY", "CONTROL_INPUT", "COLLECTOR", "GND" }, 6, false));
        else if (QuickPlayFamilyRegistry.NMOS_LOW_SIDE_SWITCH.equals(familyId))
            result.add(simpleWithWorkflow("NMOS_CONTROL_AND_GDS_TERMINAL_CHECK", "J1.2",
                new String[] { "J1.1", "J2.1", "Q1.G", "Q1.D", "Q1.S" },
                new String[] { "DC_VOLTAGE", "RESISTANCE", "CONTINUITY" },
                new String[] { "CONTROL_INPUT_HIGH", "CONTROL_INPUT_LOW", "BOARD_POWER_OFF",
                    "BOARD_POWER_ON" },
                new String[] { WorkbenchOperation.REMOVE },
                new String[] { WorkbenchOperation.CATALOG_INSTALL },
                new String[] { WorkbenchOperation.RECONNECT_LEAD },
                new String[] { GeneratedBoardOperationIds.CONTROL_INPUT_HIGH,
                    GeneratedBoardOperationIds.CONTROL_INPUT_LOW,
                    GeneratedBoardOperationIds.CUSTOMER_RETEST },
                new String[] { "CONTROL_HIGH_SAMPLE", "CONTROL_LOW_SAMPLE" },
                new String[] { "LOAD_SUPPLY", "CONTROL_INPUT", "DRAIN", "GND" }, 6, false));
        else
            throw new IllegalArgumentException("No diagnostic plan catalog for family: " + familyId);
        return result;
    }

    private static GeneratedDiagnosticPlan simple(String template, String reference,
            String[] probes, String[] meters, String[] transitions, String[] isolation,
            String[] repair, String[] operations, String[] temporal, String[] domains,
            int depth, boolean parallel) {
        return new GeneratedDiagnosticPlan(template, reference, probes, meters, transitions,
            isolation, repair, operations, temporal, domains, depth, parallel, false, "NONE");
    }

    private static GeneratedDiagnosticPlan simpleWithWorkflow(String template, String reference,
            String[] probes, String[] meters, String[] transitions, String[] isolation,
            String[] repair, String[] workflows, String[] operations, String[] temporal,
            String[] domains, int depth, boolean parallel) {
        return new GeneratedDiagnosticPlan(template, reference, probes, meters, transitions,
            isolation, repair, workflows, operations, temporal, domains, depth, parallel,
            false, "NONE");
    }
}
