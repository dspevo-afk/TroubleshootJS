package com.lushprojects.circuitjs1.client;

/** The same available voltage/resistance observations separate every relay hypothesis. */
final class RelayOutputDiagnosticProvider extends LeafDiagnosticProvider {
    RelayOutputDiagnosticProvider(long seed) { super(seed); }
    public String getProviderId() { return "isolated-relay-diagnostic@1"; }
    public GeneratedDiagnosticPlan getDiagnosticPlan() {
        return new GeneratedDiagnosticPlan("RELAY_COIL_CONTACT_DRIVER", "J1.2",
            new String[] { "J1.1", "J2.1", "K1.A1", "K1.A2", "K1.COM", "K1.NC", "K1.NO",
                "J4.1", "J4.2", "RDRIVE.1", "RDRIVE.2" },
            new String[] { "DC_VOLTAGE", "RESISTANCE", "CONTINUITY" },
            new String[] { "CONTROL_INPUT_HIGH", "CONTROL_INPUT_LOW", "BOARD_POWER_OFF", "BOARD_POWER_ON" },
            new String[] { WorkbenchOperation.REMOVE }, new String[] { WorkbenchOperation.CATALOG_INSTALL },
            new String[] { WorkbenchOperation.INSTALL },
            new String[] { "CONTROL_INPUT_HIGH", "CONTROL_INPUT_LOW", "CUSTOMER_RETEST" },
            new String[] { "COIL_DISCHARGE" }, new String[] { "CTRL_SUPPLY", "CONTACT_SUPPLY" }, 6, false, false, "NONE");
    }
    public GeneratedBoardInstance generateHypothesis(GeneratedFaultCandidate hypothesis) {
        return new RelayOutputGenerator().generateForFaultVerification(seed, hypothesis.getFault().getType());
    }
    public String getCorrectCatalogId(GeneratedBoardInstance owner, String componentId) {
        return "K1".equals(componentId) ? ReplaceableRelayCapability.CORRECT : super.getCorrectCatalogId(owner, componentId);
    }
    public GeneratedDiagnosticProgram getObservationProgram() {
        GeneratedDiagnosticProgram.Builder program = GeneratedDiagnosticProgram.builder(getDiagnosticPlan());
        program.input(GeneratedBoardOperationIds.CONTROL_INPUT_HIGH);
        program.measure(GeneratedDiagnosticProgram.Kind.DC_VOLTAGE, "HIGH_COIL", "K1.A1", "K1.A2");
        program.measure(GeneratedDiagnosticProgram.Kind.DC_VOLTAGE, "HIGH_LOAD", "J4.1", "J4.2");
        program.measure(GeneratedDiagnosticProgram.Kind.DC_VOLTAGE, "HIGH_DRIVE", "RDRIVE.2", "J1.2");
        program.input(GeneratedBoardOperationIds.CONTROL_INPUT_LOW);
        program.measure(GeneratedDiagnosticProgram.Kind.DC_VOLTAGE, "LOW_COIL", "K1.A1", "K1.A2");
        program.measure(GeneratedDiagnosticProgram.Kind.DC_VOLTAGE, "LOW_LOAD", "J4.1", "J4.2");
        program.power("BOARD_POWER_OFF", BoardPowerState.UNPOWERED).waitSample("COIL_DISCHARGE", .025).settle();
        passivePair(program, "K1.A1", "K1.A2"); passivePair(program, "RDRIVE.1", "RDRIVE.2");
        return program.build();
    }
}
