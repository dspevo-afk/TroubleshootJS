package com.lushprojects.circuitjs1.client;

/** Production diagnostic recipe owned by the parallel-dual-indicator variant. */
final class ParallelDualIndicatorDiagnosticProvider extends LeafDiagnosticProvider {
    ParallelDualIndicatorDiagnosticProvider(long seed) { super(seed); }
    public String getProviderId() { return "parallel-dual-indicator-diagnostic@1"; }

    public GeneratedDiagnosticPlan getDiagnosticPlan() {
        return new GeneratedDiagnosticPlan("PARALLEL_BRANCH_COMPONENT_CHECK", "J1.2",
                new String[] { "J1.1", "R1.1", "R1.2", "LED1.A", "R2.1", "R2.2", "LED2.A" },
                new String[] { "DC_VOLTAGE", "RESISTANCE", "CONTINUITY" },
                new String[] { "BOARD_POWER_ON_INITIAL", "BOARD_POWER_OFF", "BOARD_POWER_ON_RETEST" },
                new String[] { WorkbenchOperation.REMOVE },
                new String[] { WorkbenchOperation.CATALOG_INSTALL },
                new String[] { GeneratedBoardOperationIds.CUSTOMER_RETEST },
                new String[] { "BRANCH1_SAMPLE", "BRANCH2_SAMPLE" },
                new String[] { "VIN", "BRANCH1_NODE", "BRANCH2_NODE", "GND" }, 5, true, false, "NONE");
    }

    public GeneratedBoardInstance generateHypothesis(GeneratedFaultCandidate hypothesis) {
        return new ParallelDualIndicatorGenerator().generateForFaultVerification(seed, hypothesis.getFault().getType());
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
