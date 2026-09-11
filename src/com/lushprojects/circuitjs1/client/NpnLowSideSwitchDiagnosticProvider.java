package com.lushprojects.circuitjs1.client;

/** Production diagnostic recipe owned by the npn-low-side-switch variant. */
final class NpnLowSideSwitchDiagnosticProvider extends LeafDiagnosticProvider {
    NpnLowSideSwitchDiagnosticProvider(long seed) { super(seed); }
    public String getProviderId() { return "npn-low-side-switch-diagnostic@1"; }

    public GeneratedDiagnosticPlan getDiagnosticPlan() {
        return new GeneratedDiagnosticPlan("NPN_CONTROL_AND_PUBLIC_TERMINAL_CHECK", "J1.2",
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
                new String[] { "LOAD_SUPPLY", "CONTROL_INPUT", "COLLECTOR", "GND" }, 6, false, false, "NONE");
    }

    public GeneratedBoardInstance generateHypothesis(GeneratedFaultCandidate hypothesis) {
        return new NpnLowSideSwitchGenerator().generateForDiagnosticSolvability(seed, hypothesis.getFault().getType());
    }

    public String getCorrectCatalogId(GeneratedBoardInstance instance, String componentId) {
        if ("Q1".equals(componentId)) return NpnReplacementCatalog.CORRECT;
        return super.getCorrectCatalogId(instance, componentId);
    }

    public GeneratedDiagnosticProgram getObservationProgram() {
        GeneratedDiagnosticPlan plan = getDiagnosticPlan();
        GeneratedDiagnosticProgram.Builder program = GeneratedDiagnosticProgram.builder(plan);
        program.input(GeneratedBoardOperationIds.CONTROL_INPUT_HIGH);
        dcSweep(program, plan, "CONTROL_HIGH");
        program.input(GeneratedBoardOperationIds.CONTROL_INPUT_LOW);
        dcSweep(program, plan, "CONTROL_LOW");
        program.power("BOARD_POWER_OFF", BoardPowerState.UNPOWERED).settle();
        passivePair(program, "Q1.B", "Q1.C");
        return program.build();
    }
}
