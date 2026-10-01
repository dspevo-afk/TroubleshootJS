package com.lushprojects.circuitjs1.client;

/** Public-probe diagnostics and exact seeded replay for the Q30 family. */
final class Rb30DiagnosticProvider implements GeneratedDiagnosticProvider,
        GeneratedDiagnosticServicePreparation.Provider {
    private static final String TEMPLATE = "RB30_CHANNEL_INPUT_SWEEP_V1";
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
        return admission == null ? "rb30-control-diagnostic@2" : "rb30-control-diagnostic@4";
    }

    public GeneratedDiagnosticServicePreparation.Policy getServicePreparationPolicy() {
        return admission == null ? null : NORMAL_SERVICE_PREPARATION;
    }

    public GeneratedDiagnosticPlan getDiagnosticPlan() {
        String[] channels = plan.channels();
        String[] conditions = conditions();
        String[] samples = samples();
        String[] terminals = observationTerminals(channels);
        String[] initialSteps = new String[conditions.length];
        String[] retest = new String[conditions.length + 1];
        String[] conditionIds = new String[conditions.length];
        String[] hypotheses = hypothesisRoles(channels);
        for (int index = 0; index < conditions.length; index++) {
            initialSteps[index] = conditions[index];
            retest[index] = conditions[index];
            conditionIds[index] = samples[index];
        }
        retest[conditions.length] = GeneratedBoardOperationIds.CUSTOMER_RETEST;
        return new GeneratedDiagnosticPlan(TEMPLATE, "J1.2",
            terminals,
            new String[] { "DC_VOLTAGE" },
            normalDeclaration(initialSteps, "BOARD_POWER_OFF_SERVICE"),
            new String[] { WorkbenchOperation.REMOVE, WorkbenchOperation.LIFT_LEAD },
            new String[] { WorkbenchOperation.CATALOG_INSTALL },
            new String[] { WorkbenchOperation.LIFT_LEAD,
                WorkbenchOperation.RECONNECT_LEAD, WorkbenchOperation.REMOVE,
                WorkbenchOperation.CATALOG_INSTALL },
            retest, conditionIds, hypotheses,
            8, false, true, "PHYSICAL_COMPONENT_REPLACEMENT");
    }

    private String[] conditions() {
        return plan.channelCount == 1 ?
            new String[] { Rb30Behavior.SENSORS_LOW, Rb30Behavior.SENSORS_HIGH } :
            new String[] { Rb30Behavior.SENSORS_LOW, Rb30Behavior.SENSORS_A_ONLY,
                Rb30Behavior.SENSORS_B_ONLY, Rb30Behavior.SENSORS_HIGH };
    }

    private String[] samples() {
        return plan.channelCount == 1 ?
            new String[] { "LOW_INPUT_SETTLED", "HIGH_INPUT_SETTLED" } :
            new String[] { "LOW_INPUT_SETTLED", "A_ONLY_INPUT_SETTLED",
                "B_ONLY_INPUT_SETTLED", "HIGH_INPUT_SETTLED" };
    }

    private String[] observationTerminals(String[] channels) {
        VectorBuilder terminals = new VectorBuilder();
        terminals.add("J1.1");
        terminals.add("DREV.K");
        terminals.add("REN.2");
        terminals.add("U1.OUTPUT");
        terminals.add("RSA.2");
        terminals.add("RDA.1");
        terminals.add("RDA.2");
        String relay = channels.length == 1 ? "KA" : "KB";
        terminals.add(relay + ".A2");
        for (String channel : channels) {
            terminals.add("JO" + channel + ".1");
            terminals.add("JO" + channel + ".2");
        }
        return terminals.toArray();
    }

    private String[] hypothesisRoles(String[] channels) {
        if (channels.length == 1)
            return new String[] { "MAIN_12V", "REGULATED_5V", "SENSOR_A",
                "ENABLE_PATH", "SENSOR_A_PATH", "DRIVE_A_PATH",
                "RELAY_A_COIL", "OUTPUT_A_LOAD" };
        return new String[] { "MAIN_12V", "REGULATED_5V", "SENSOR_A",
            "SENSOR_B", "ENABLE_PATH", "SENSOR_A_PATH", "DRIVE_A_PATH",
            "RELAY_B_COIL", "OUTPUT_A_LOAD", "OUTPUT_B_LOAD" };
    }

    private static final class VectorBuilder {
        private final java.util.Vector<String> values =
            new java.util.Vector<String>();
        void add(String value) { values.add(value); }
        String[] toArray() {
            return values.toArray(new String[values.size()]);
        }
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
        String[] conditions = conditions();
        String[] samples = samples();
        for (int index = 0; index < conditions.length; index++) {
            program.input(conditions[index]).waitSample(samples[index], 0);
            measure(program, conditions[index] + "_DREV_K", "DREV.K", "J1.2");
            measure(program, conditions[index] + "_REN_2", "REN.2", "J1.2");
            measure(program, conditions[index] + "_RAIL5", "U1.OUTPUT", "J1.2");
            measure(program, conditions[index] + "_RSA_2", "RSA.2", "J1.2");
            measure(program, conditions[index] + "_RDA_1", "RDA.1", "J1.2");
            measure(program, conditions[index] + "_RDA_2", "RDA.2", "J1.2");
            String relay = plan.channelCount == 1 ? "KA" : "KB";
            measure(program, conditions[index] + "_K" +
                (plan.channelCount == 1 ? "A" : "B") + "_A2",
                relay + ".A2", "J1.2");
            for (String channel : plan.channels())
                measure(program, conditions[index] + "_OUTPUT_" + channel,
                    "JO" + channel + ".1", "JO" + channel + ".2");
        }
        // Two channels retain 9*4+1=37 observations. One channel uses
        // 8*2+1=17 with no absent-channel readings.
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
        String relayId = plan.channelCount == 1 ? "KA" : "KB";
        if (relayId.equals(componentId))
            for (PhysicalBoardRuntimeCapability capability :
                    instance.getPhysicalBoardRuntime().getCapabilities())
                if (capability instanceof Rb30RelayService && relayId.equals(
                        ((Rb30RelayService) capability).getComponentId()))
                    return ((Rb30RelayService) capability).getFiveVoltCatalogId();
        throw new IllegalArgumentException("No correct Q30 replacement for " +
            String.valueOf(componentId));
    }

}
