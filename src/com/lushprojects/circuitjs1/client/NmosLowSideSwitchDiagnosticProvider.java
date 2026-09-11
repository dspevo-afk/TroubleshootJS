package com.lushprojects.circuitjs1.client;

/** Production diagnostic recipe owned by the nmos-low-side-switch variant. */
final class NmosLowSideSwitchDiagnosticProvider extends LeafDiagnosticProvider {
    NmosLowSideSwitchDiagnosticProvider(long seed) { super(seed); }
    public String getProviderId() { return "nmos-low-side-switch-diagnostic@1"; }

    public GeneratedDiagnosticPlan getDiagnosticPlan() {
        return new GeneratedDiagnosticPlan("NMOS_CONTROL_AND_GDS_TERMINAL_CHECK", "J1.2",
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
                new String[] { "LOAD_SUPPLY", "CONTROL_INPUT", "DRAIN", "GND" }, 6, false, false, "NONE");
    }

    public GeneratedBoardInstance generateHypothesis(GeneratedFaultCandidate hypothesis) {
        return new NmosLowSideSwitchGenerator().generateForFaultVerification(seed, hypothesis.getFault().getType());
    }

    public String getCorrectCatalogId(GeneratedBoardInstance instance, String componentId) {
        if ("Q1".equals(componentId)) return NmosReplacementCatalog.CORRECT;
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
        passivePair(program, "Q1.G", "Q1.D");
        return program.build();
    }
}
