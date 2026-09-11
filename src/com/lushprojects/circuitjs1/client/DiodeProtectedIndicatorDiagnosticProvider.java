package com.lushprojects.circuitjs1.client;

/** Production diagnostic recipe owned by the diode-protected-indicator variant. */
final class DiodeProtectedIndicatorDiagnosticProvider extends LeafDiagnosticProvider {
    DiodeProtectedIndicatorDiagnosticProvider(long seed) { super(seed); }
    public String getProviderId() { return "diode-protected-indicator-diagnostic@1"; }

    public GeneratedDiagnosticPlan getDiagnosticPlan() {
        return new GeneratedDiagnosticPlan("DIODE_FORWARD_PATH_CHECK", "J1.2",
                new String[] { "J1.1", "D1.A", "D1.K", "LED1.A", "LED1.K" },
                new String[] { "DC_VOLTAGE", "RESISTANCE", "CONTINUITY", "DIODE" },
                new String[] { "BOARD_POWER_ON_INITIAL", "BOARD_POWER_OFF", "BOARD_POWER_ON_RETEST" },
                new String[] { WorkbenchOperation.REMOVE },
                new String[] { WorkbenchOperation.CATALOG_INSTALL },
                new String[] { GeneratedBoardOperationIds.CUSTOMER_RETEST },
                new String[] { "FORWARD_DROP_SAMPLE" },
                new String[] { "VIN", "DIODE_OUT", "LED_NODE", "GND" }, 4, false, false, "NONE");
    }

    public GeneratedBoardInstance generateHypothesis(GeneratedFaultCandidate hypothesis) {
        return new DiodeProtectedIndicatorGenerator().generate(seed);
    }

    public String getCorrectCatalogId(GeneratedBoardInstance instance, String componentId) {
        if ("D1".equals(componentId)) return DiodeReplacementCatalog.CORRECT;
        return super.getCorrectCatalogId(instance, componentId);
    }

    public GeneratedDiagnosticProgram getObservationProgram() {
        GeneratedDiagnosticPlan plan = getDiagnosticPlan();
        GeneratedDiagnosticProgram.Builder program = GeneratedDiagnosticProgram.builder(plan);
        dcSweep(program, plan, "STEADY_STATE");
        program.power("BOARD_POWER_OFF", BoardPowerState.UNPOWERED).settle();
        passivePair(program, "D1.A", "D1.K");
        program.measure(GeneratedDiagnosticProgram.Kind.DIODE, "DIODE_FORWARD", "D1.A", "D1.K");
        return program.build();
    }
}
