package com.lushprojects.circuitjs1.client;

/** Public-probe diagnostics and exact seeded replay for the Q30 pilot. */
final class Rb30DiagnosticProvider implements GeneratedDiagnosticProvider,
        GeneratedDiagnosticServicePreparation.Provider {
    private static final String TEMPLATE = "RB30_TWO_CHANNEL_INPUT_SWEEP";
    private static final GeneratedDiagnosticServicePreparation.Policy NORMAL_SERVICE_PREPARATION =
        new GeneratedDiagnosticServicePreparation.Policy(5, .050);
    private final Rb30Plan plan;
    private final PcbBoardLayout layout;
    private final GeneratedPhysicalAdmission admission;

    Rb30DiagnosticProvider(Rb30Plan plan, PcbBoardLayout layout) {
        this(plan, layout, null);
    }

    Rb30DiagnosticProvider(Rb30Plan plan, PcbBoardLayout layout,
            GeneratedPhysicalAdmission admission) {
        if (plan == null || layout == null)
            throw new IllegalArgumentException("Missing Q30 diagnostic generation context");
        this.plan = plan;
        this.layout = layout;
        this.admission = admission;
    }

    public String getProviderId() {
        return admission == null ? "rb30-control-diagnostic@1" : "rb30-control-diagnostic@3";
    }

    public GeneratedDiagnosticServicePreparation.Policy getServicePreparationPolicy() {
        return admission == null ? null : NORMAL_SERVICE_PREPARATION;
    }

    public GeneratedDiagnosticPlan getDiagnosticPlan() {
        return new GeneratedDiagnosticPlan(TEMPLATE, "J1.2",
            new String[] { "J1.1", "DREV.K", "REN.2", "U1.OUTPUT", "RSA.2",
                "RDA.1", "RDA.2", "KB.A2", "JOA.1", "JOA.2", "JOB.1",
                "JOB.2" },
            new String[] { "DC_VOLTAGE" },
            normalDeclaration(new String[] { Rb30Behavior.SENSORS_LOW,
                Rb30Behavior.SENSORS_A_ONLY, Rb30Behavior.SENSORS_B_ONLY,
                Rb30Behavior.SENSORS_HIGH }, "BOARD_POWER_OFF_SERVICE"),
            new String[] { WorkbenchOperation.REMOVE, WorkbenchOperation.LIFT_LEAD },
            new String[] { WorkbenchOperation.CATALOG_INSTALL },
            new String[] { WorkbenchOperation.LIFT_LEAD,
                WorkbenchOperation.RECONNECT_LEAD, WorkbenchOperation.REMOVE,
                WorkbenchOperation.CATALOG_INSTALL },
            new String[] { Rb30Behavior.SENSORS_LOW,
                Rb30Behavior.SENSORS_A_ONLY, Rb30Behavior.SENSORS_B_ONLY,
                Rb30Behavior.SENSORS_HIGH,
                GeneratedBoardOperationIds.CUSTOMER_RETEST },
            new String[] { "LOW_INPUT_SETTLED", "A_ONLY_INPUT_SETTLED",
                "B_ONLY_INPUT_SETTLED", "HIGH_INPUT_SETTLED" },
            new String[] { "MAIN_12V", "REGULATED_5V", "SENSOR_A",
                "SENSOR_B", "ENABLE_PATH", "SENSOR_A_PATH", "DRIVE_A_PATH",
                "RELAY_B_COIL", "OUTPUT_A_LOAD", "OUTPUT_B_LOAD" },
            8, false, true, "PHYSICAL_COMPONENT_REPLACEMENT");
    }

    private String[] normalDeclaration(String[] existing, String serviceId) {
        if (admission == null) return existing;
        String[] extended = new String[existing.length + 1];
        for (int i = 0; i < existing.length; i++) extended[i] = existing[i];
        extended[existing.length] = serviceId;
        return extended;
    }

    public GeneratedDiagnosticProgram getObservationProgram() {
        GeneratedDiagnosticPlan diagnosticPlan = getDiagnosticPlan();
        GeneratedDiagnosticProgram.Builder program =
            GeneratedDiagnosticProgram.builder(diagnosticPlan);
        String[] conditions = { Rb30Behavior.SENSORS_LOW,
            Rb30Behavior.SENSORS_A_ONLY, Rb30Behavior.SENSORS_B_ONLY,
            Rb30Behavior.SENSORS_HIGH };
        String[] samples = { "LOW_INPUT_SETTLED", "A_ONLY_INPUT_SETTLED",
            "B_ONLY_INPUT_SETTLED", "HIGH_INPUT_SETTLED" };
        for (int index = 0; index < conditions.length; index++) {
            program.input(conditions[index]).waitSample(samples[index], 0);
            measure(program, conditions[index] + "_DREV_K", "DREV.K", "J1.2");
            measure(program, conditions[index] + "_REN_2", "REN.2", "J1.2");
            measure(program, conditions[index] + "_RAIL5", "U1.OUTPUT", "J1.2");
            measure(program, conditions[index] + "_RSA_2", "RSA.2", "J1.2");
            measure(program, conditions[index] + "_RDA_1", "RDA.1", "J1.2");
            measure(program, conditions[index] + "_RDA_2", "RDA.2", "J1.2");
            measure(program, conditions[index] + "_KB_A2", "KB.A2", "J1.2");
            measure(program, conditions[index] + "_OUTPUT_A", "JOA.1", "JOA.2");
            measure(program, conditions[index] + "_OUTPUT_B", "JOB.1", "JOB.2");
        }
        // A final independent supply measurement makes the receipt 37 samples:
        // nine observations in each functional condition plus the main input.
        measure(program, "MAIN_12V", "J1.1", "J1.2");
        if (admission != null) {
            // The proof session executes provider-owned, bounded readiness
            // work after power-off. It samples actual REMOVE availability;
            // elapsed time alone never authorizes a mutation.
            program.power("BOARD_POWER_OFF_SERVICE", BoardPowerState.UNPOWERED);
            program.settle();
        }
        return program.build();
    }

    private void measure(GeneratedDiagnosticProgram.Builder program, String id,
            String red, String black) {
        program.measure(GeneratedDiagnosticProgram.Kind.DC_VOLTAGE, id, red, black);
    }

    public GeneratedBoardInstance generateHypothesis(GeneratedFaultCandidate hypothesis) {
        if (hypothesis == null)
            throw new IllegalArgumentException("Missing Q30 diagnostic hypothesis");
        String expectedFaultId = Rb30Generator.faultIdForHypothesis(plan,
            hypothesis.getHypothesisKey());
        if (
                !Rb30Plan.FAMILY_ID.equals(hypothesis.getFault().getCircuitFamilyId()) ||
                hypothesis.getFault().getSelectionSeed() != plan.seed ||
                !hypothesis.isAdmitted() ||
                !expectedFaultId.equals(hypothesis.getFault().getId()))
            throw new IllegalArgumentException("Foreign Q30 diagnostic hypothesis");
        Rb30Generator.Candidate candidate = new Rb30Generator().construct(plan,
            hypothesis.getFault().getId());
        return admission == null ? new Rb30Generator().assemble(candidate, layout.copySealed()) :
            new Rb30Generator().assembleNormal(candidate, layout.copySealed(), admission);
    }

    public String getCorrectCatalogId(GeneratedBoardInstance instance,
            String componentId) {
        if (instance == null || !Rb30Plan.FAMILY_ID.equals(instance.getCircuitFamilyId()) ||
                instance.getSeed() != plan.seed ||
                !plan.topology().equals(instance.getTopologyVariantId()) ||
                instance.getDiagnosticProvider() != this)
            throw new IllegalArgumentException("Foreign Q30 catalog query");
        if ("DREV".equals(componentId)) return DiodeReplacementCatalog.CORRECT;
        if ("REN".equals(componentId) || "RSA".equals(componentId))
            return "R_CATALOG_10000";
        if ("RDA".equals(componentId)) return "R_CATALOG_1000";
        if ("KB".equals(componentId))
            for (PhysicalBoardRuntimeCapability capability :
                    instance.getPhysicalBoardRuntime().getCapabilities())
                if (capability instanceof Rb30RelayService && "KB".equals(
                        ((Rb30RelayService) capability).getComponentId()))
                    return ((Rb30RelayService) capability).getFiveVoltCatalogId();
        throw new IllegalArgumentException("No correct Q30 replacement for " +
            String.valueOf(componentId));
    }

}
