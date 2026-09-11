package com.lushprojects.circuitjs1.client;

/** Production diagnostic recipe owned by the rc-delay variant. */
final class RcDelayDiagnosticProvider extends LeafDiagnosticProvider {
    RcDelayDiagnosticProvider(long seed) { super(seed); }
    public String getProviderId() { return "rc-delay-diagnostic@1"; }

    public GeneratedDiagnosticPlan getDiagnosticPlan() {
        return new GeneratedDiagnosticPlan("RC_POWER_CYCLE_TEMPORAL_CHECK", "J2.2",
                new String[] { "J1.1", "J2.1", "C1.+", "C1.-" },
                new String[] { "DC_VOLTAGE", "RESISTANCE", "CONTINUITY" },
                new String[] { "BOARD_POWER_OFF_INITIAL", "RC_POWER_ON", "RC_RESIDUAL_SAMPLE",
                    "RC_EARLY_SAMPLE", "RC_LATE_SAMPLE", "BOARD_POWER_OFF_FINAL" },
                new String[] { WorkbenchOperation.REMOVE, WorkbenchOperation.LIFT_LEAD },
                new String[] { WorkbenchOperation.CATALOG_INSTALL },
                new String[] { WorkbenchOperation.RECONNECT_LEAD },
                new String[] { GeneratedBoardOperationIds.CUSTOMER_RETEST },
                new String[] { "RC_RESIDUAL_SAMPLE", "RC_EARLY_SAMPLE", "RC_LATE_SAMPLE", "RC_POWER_OFF_SETTLE" },
                new String[] { "VIN", "RC_OUT", "GND" }, 6, false, false, "NONE");
    }

    public GeneratedBoardInstance generateHypothesis(GeneratedFaultCandidate hypothesis) {
        return new RcDelayGenerator().generateForFaultVerification(seed, hypothesis.getFault().getType());
    }

    public String getCorrectCatalogId(GeneratedBoardInstance instance, String componentId) {
        if ("C1".equals(componentId)) return CapacitorReplacementCatalog.CORRECT;
        return super.getCorrectCatalogId(instance, componentId);
    }

    public GeneratedDiagnosticProgram getObservationProgram() {
        GeneratedDiagnosticPlan plan = getDiagnosticPlan();
        GeneratedDiagnosticProgram.Builder program = GeneratedDiagnosticProgram.builder(plan);
        program.profilePower("BOARD_POWER_OFF_INITIAL", BoardPowerState.UNPOWERED)
            .waitSample("RC_RESIDUAL_SAMPLE", .120)
            .measure(GeneratedDiagnosticProgram.Kind.DC_VOLTAGE, "RC_RESIDUAL_SAMPLE", "J2.1", "J2.2")
            .profilePower("RC_POWER_ON", BoardPowerState.POWERED)
            .waitSample("RC_EARLY_SAMPLE", .100)
            .measure(GeneratedDiagnosticProgram.Kind.DC_VOLTAGE, "RC_EARLY_SAMPLE", "J2.1", "J2.2")
            .waitSample("RC_LATE_SAMPLE", .700)
            .measure(GeneratedDiagnosticProgram.Kind.DC_VOLTAGE, "RC_LATE_SAMPLE", "J2.1", "J2.2")
            .power("BOARD_POWER_OFF_FINAL", BoardPowerState.UNPOWERED)
            .waitSample("RC_POWER_OFF_SETTLE", .800).settle()
            .measure(GeneratedDiagnosticProgram.Kind.RESISTANCE, "OHM_C1+_C1-", "C1.+", "C1.-");
        return program.build();
    }
}
