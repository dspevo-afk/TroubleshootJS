package com.lushprojects.circuitjs1.client;

/** Production diagnostic recipe for both timer variants (free-running and triggered). */
final class Timer555DiagnosticProvider extends LeafDiagnosticProvider {
    private final boolean freeRunning;
    private final String target;

    Timer555DiagnosticProvider(long seed) {
        super(seed);
        freeRunning = Timer555Generator.isFreeRunning(seed);
        target = "RA";
    }

    public String getProviderId() { return "timer-555-diagnostic@1"; }

    public GeneratedDiagnosticPlan getDiagnosticPlan() {
        String[] transitions = freeRunning ?
            new String[] { "BOARD_POWER_OFF_INITIAL", "TIMER_POWER_ON", "TIMER_EARLY_SAMPLE",
                "TIMER_LATE_SAMPLE", "BOARD_POWER_OFF_FINAL" } :
            new String[] { "BOARD_POWER_OFF_INITIAL", "TIMER_POWER_ON",
                GeneratedBoardOperationIds.START_BUTTON_PRESS, "TIMER_EARLY_SAMPLE",
                "TIMER_LATE_SAMPLE", "BOARD_POWER_OFF_FINAL" };
        String[] operations = freeRunning ?
            new String[] { GeneratedBoardOperationIds.CUSTOMER_RETEST } :
            new String[] { GeneratedBoardOperationIds.START_BUTTON_PRESS,
                GeneratedBoardOperationIds.CUSTOMER_RETEST };
        return new GeneratedDiagnosticPlan(
            freeRunning ? "TIMER_FLASH_RATE_CHECK" : "TIMER_PULSE_WIDTH_CHECK", "J1.2",
            new String[] { "J1.1", "U1.OUT", "U1.THRES", "C1.+", "C1.-", target + ".1", target + ".2" },
            new String[] { "DC_VOLTAGE", "RESISTANCE" },
            transitions,
            new String[] { WorkbenchOperation.REMOVE, WorkbenchOperation.LIFT_LEAD },
            new String[] { WorkbenchOperation.CATALOG_INSTALL },
            new String[] { WorkbenchOperation.RECONNECT_LEAD },
            operations,
            new String[] { "TIMER_DISCHARGE_SAMPLE", "TIMER_EARLY_SAMPLE", "TIMER_LATE_SAMPLE",
                "TIMER_POWER_OFF_SETTLE" },
            new String[] { "VCC", "TIMING", "GND" }, 6, false, false, "NONE");
    }

    public GeneratedBoardInstance generateHypothesis(GeneratedFaultCandidate hypothesis) {
        return new Timer555Generator().generateForFaultVerification(seed,
            hypothesis.getFault().getType());
    }

    public String getCorrectCatalogId(GeneratedBoardInstance instance, String componentId) {
        if ("C1".equals(componentId)) return CapacitorReplacementCatalog.CORRECT;
        if ("LED1".equals(componentId)) return LedReplacementCatalog.CORRECT;
        return super.getCorrectCatalogId(instance, componentId);
    }

    public GeneratedDiagnosticProgram getObservationProgram() {
        GeneratedDiagnosticPlan plan = getDiagnosticPlan();
        GeneratedDiagnosticProgram.Builder program = GeneratedDiagnosticProgram.builder(plan);
        program.profilePower("BOARD_POWER_OFF_INITIAL", BoardPowerState.UNPOWERED)
            .waitSample("TIMER_DISCHARGE_SAMPLE", .750)
            .profilePower("TIMER_POWER_ON", BoardPowerState.POWERED);
        if (freeRunning) program.waitSample("TIMER_EARLY_SAMPLE", .300);
        else program.waitSample("TIMER_EARLY_SAMPLE", .250)
            .input(GeneratedBoardOperationIds.START_BUTTON_PRESS);
        program.measure(GeneratedDiagnosticProgram.Kind.DC_VOLTAGE, "TIMER_EARLY_OUT", "U1.OUT", "J1.2")
            .measure(GeneratedDiagnosticProgram.Kind.DC_VOLTAGE, "TIMER_EARLY_THRES", "U1.THRES", "J1.2")
            .waitSample("TIMER_LATE_SAMPLE", .750)
            .measure(GeneratedDiagnosticProgram.Kind.DC_VOLTAGE, "TIMER_LATE_OUT", "U1.OUT", "J1.2")
            .measure(GeneratedDiagnosticProgram.Kind.DC_VOLTAGE, "TIMER_LATE_THRES", "U1.THRES", "J1.2")
            .measure(GeneratedDiagnosticProgram.Kind.DC_VOLTAGE, "TIMER_SUPPLY", "J1.1", "J1.2")
            .power("BOARD_POWER_OFF_FINAL", BoardPowerState.UNPOWERED)
            .waitSample("TIMER_POWER_OFF_SETTLE", .750).settle()
            .measure(GeneratedDiagnosticProgram.Kind.RESISTANCE, "OHM_C1+_C1-", "C1.+", "C1.-")
            // Resistance alone separates RA open from RA off-value. A continuity
            // step is omitted: on the pulse board RA feeds C1 directly, and the
            // ohmmeter's test current leaves C1 above the meter's residual limit.
            .measure(GeneratedDiagnosticProgram.Kind.RESISTANCE, "OHM_" + target + ".1_" + target + ".2",
                target + ".1", target + ".2");
        return program.build();
    }
}
