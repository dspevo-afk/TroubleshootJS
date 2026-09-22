package com.lushprojects.circuitjs1.client;

/** Production diagnostic recipe for the player-operated E04 leaf. */
final class SensorControlDiagnosticProvider extends LeafDiagnosticProvider {
    SensorControlDiagnosticProvider(long seed) { super(seed); }

    public String getProviderId() { return "sensor-control-diagnostic@1"; }

    public GeneratedDiagnosticPlan getDiagnosticPlan() {
        return new GeneratedDiagnosticPlan("SENSOR_CONTROL_CONDITION_SWEEP", "J1.2",
            new String[] { "J1.1", "J2.1", "J3.1", "J3.2", "RBIAS.1", "RBIAS.2",
                "RREF.1", "RREF.2", "RFB.1", "RFB.2" },
            new String[] { "DC_VOLTAGE", "RESISTANCE", "CONTINUITY" },
            new String[] { GeneratedBoardOperationIds.SENSOR_CONDITION_LOW,
                GeneratedBoardOperationIds.SENSOR_CONDITION_MID,
                GeneratedBoardOperationIds.SENSOR_CONDITION_HIGH,
                "BOARD_POWER_OFF", "BOARD_POWER_ON" },
            new String[] { WorkbenchOperation.REMOVE },
            new String[] { WorkbenchOperation.CATALOG_INSTALL },
            new String[] { WorkbenchOperation.RECONNECT_LEAD },
            new String[] { GeneratedBoardOperationIds.SENSOR_CONDITION_LOW,
                GeneratedBoardOperationIds.SENSOR_CONDITION_MID,
                GeneratedBoardOperationIds.SENSOR_CONDITION_HIGH,
                GeneratedBoardOperationIds.CUSTOMER_RETEST },
            new String[] { "SENSOR_LOW_SAMPLE", "SENSOR_MID_SAMPLE",
                "SENSOR_HIGH_SAMPLE" },
            new String[] { "CONTROL_RAIL", "SENSOR_SOURCE", "CONDITIONED_SENSOR",
                "CONTROL_REFERENCE", "CONTROL_OUTPUT", "CONTROL_OUTPUT_LOAD",
                "CONTROL_RETURN" },
            8, false, true, "RESISTOR_REPLACEMENT");
    }

    public GeneratedBoardInstance generateHypothesis(GeneratedFaultCandidate hypothesis) {
        if (hypothesis == null)
            throw new IllegalArgumentException("Missing E04 diagnostic hypothesis");
        return new SensorControlGenerator().generateForHypothesis(seed,
            hypothesis.getHypothesisKey());
    }

    public GeneratedDiagnosticProgram getObservationProgram() {
        GeneratedDiagnosticPlan plan = getDiagnosticPlan();
        GeneratedDiagnosticProgram.Builder program = GeneratedDiagnosticProgram.builder(plan);
        program.input(GeneratedBoardOperationIds.SENSOR_CONDITION_LOW);
        dcSweep(program, plan, "SENSOR_LOW");
        program.input(GeneratedBoardOperationIds.SENSOR_CONDITION_MID);
        dcSweep(program, plan, "SENSOR_MID");
        program.input(GeneratedBoardOperationIds.SENSOR_CONDITION_HIGH);
        dcSweep(program, plan, "SENSOR_HIGH");
        program.power("BOARD_POWER_OFF", BoardPowerState.UNPOWERED).settle();
        passivePair(program, "RBIAS.1", "RBIAS.2");
        passivePair(program, "RREF.1", "RREF.2");
        passivePair(program, "RFB.1", "RFB.2");
        return program.build();
    }
}
