package com.lushprojects.circuitjs1.client;

/** Production diagnostic recipe owned by the led-indicator variant. */
final class LedIndicatorDiagnosticProvider extends LeafDiagnosticProvider {
    LedIndicatorDiagnosticProvider(long seed) { super(seed); }
    public String getProviderId() { return "led-indicator-diagnostic@1"; }

    public GeneratedDiagnosticPlan getDiagnosticPlan() {
        return new GeneratedDiagnosticPlan("STEADY_STATE_COMPONENT_PATH", "J1.2",
                new String[] { "J1.1", "R1.1", "R1.2", "LED1.A", "LED1.K" },
                new String[] { "DC_VOLTAGE", "RESISTANCE", "CONTINUITY" },
                new String[] { "BOARD_POWER_ON_INITIAL", "BOARD_POWER_OFF", "BOARD_POWER_ON_RETEST" },
                new String[] { WorkbenchOperation.REMOVE },
                new String[] { WorkbenchOperation.CATALOG_INSTALL },
                new String[] { GeneratedBoardOperationIds.CUSTOMER_RETEST },
                new String[] { "STEADY_STATE_SAMPLE" },
                new String[] { "VIN", "LED_NODE", "GND" }, 4, false, false, "NONE");
    }

    public GeneratedBoardInstance generateHypothesis(GeneratedFaultCandidate hypothesis) {
        return new LedIndicatorGenerator().generateForFaultVerification(seed, hypothesis.getFault().getType());
    }

    public String getCorrectCatalogId(GeneratedBoardInstance instance, String componentId) {
        if ("LED1".equals(componentId)) return LedReplacementCatalog.CORRECT;
        return super.getCorrectCatalogId(instance, componentId);
    }

    public GeneratedDiagnosticProgram getObservationProgram() {
        GeneratedDiagnosticPlan plan = getDiagnosticPlan();
        GeneratedDiagnosticProgram.Builder program = GeneratedDiagnosticProgram.builder(plan);
        dcSweep(program, plan, "STEADY_STATE");
        program.power("BOARD_POWER_OFF", BoardPowerState.UNPOWERED).settle();
        passivePair(program, "R1.1", "R1.2");
        return program.build();
    }
}
