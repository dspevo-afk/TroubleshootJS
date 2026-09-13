package com.lushprojects.circuitjs1.client;

/** The same available voltage/resistance observations separate every relay hypothesis. */
final class RelayOutputDiagnosticProvider extends LeafDiagnosticProvider {
    private final Rb15Plan plan;
    RelayOutputDiagnosticProvider(long seed) { this(seed, null); }
    RelayOutputDiagnosticProvider(long seed, Rb15Plan plan) { super(seed); this.plan=plan; }
    public String getProviderId() { return plan == null ? "isolated-relay-diagnostic@1" : "rb15-control-diagnostic@1"; }
    public GeneratedDiagnosticPlan getDiagnosticPlan() {
        return new GeneratedDiagnosticPlan("RELAY_COIL_CONTACT_DRIVER", "J1.2",
            new String[] { "J1.1", "J2.1", "K1.A1", "K1.A2", "K1.COM", "K1.NC", "K1.NO",
                "J4.1", "J4.2", "RDRIVE.1", "RDRIVE.2" },
            new String[] { "DC_VOLTAGE", "RESISTANCE", "CONTINUITY" },
            new String[] { "CONTROL_INPUT_HIGH", "CONTROL_INPUT_LOW", "BOARD_POWER_OFF", "BOARD_POWER_ON" },
            new String[] { WorkbenchOperation.REMOVE }, new String[] { WorkbenchOperation.CATALOG_INSTALL },
            new String[] { WorkbenchOperation.INSTALL },
            new String[] { "CONTROL_INPUT_HIGH", "CONTROL_INPUT_LOW", "CUSTOMER_RETEST" },
            new String[] { "COIL_DISCHARGE" }, plan == null ? new String[] { "CTRL_SUPPLY", "CONTACT_SUPPLY" } : new String[] { "CTRL_SUPPLY" }, 6, false, false, "NONE");
    }
    public GeneratedBoardInstance generateHypothesis(GeneratedFaultCandidate hypothesis) {
        if(hypothesis==null) throw new IllegalArgumentException("Missing relay hypothesis");
        if(plan!=null) {
            GeneratedFault fault=hypothesis.getFault(); GeneratedFaultType type=fault.getType();
            String target=type==GeneratedFaultType.BASE_RESISTOR_OPEN?"RDRIVE":"K1";
            if(!hypothesis.isAdmitted() || !Double.isNaN(fault.getHealthyValue()) || !Double.isNaN(fault.getEffectiveValue()) ||
                    !Rb15Plan.FAMILY_ID.equals(fault.getCircuitFamilyId()) || fault.getSelectionSeed()!=seed ||
                    !target.equals(fault.getTargetComponentId()) ||
                    !fault.getId().equals(Rb15Plan.FAMILY_ID+"_"+type.name()) ||
                    !(type==GeneratedFaultType.BASE_RESISTOR_OPEN || type==GeneratedFaultType.RELAY_COIL_OPEN || type==GeneratedFaultType.RELAY_CONTACT_OPEN))
                throw new IllegalArgumentException("Unsupported control-board hypothesis");
        }
        return new RelayOutputGenerator().generateResolved(seed, hypothesis.getFault().getType(), plan);
    }
    public String getCorrectCatalogId(GeneratedBoardInstance owner, String componentId) {
        return "K1".equals(componentId) ? (plan == null ? ReplaceableRelayCapability.COIL_5V : ReplaceableRelayCapability.COIL_12V) : super.getCorrectCatalogId(owner, componentId);
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
