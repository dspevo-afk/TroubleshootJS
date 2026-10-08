package com.lushprojects.circuitjs1.client;

import java.util.Vector;

/** Domain-local public probes and exact replay of RB56's complete fault population. */
final class Rb56DiagnosticProvider implements GeneratedDiagnosticProvider,
        GeneratedDiagnosticServicePreparation.Provider {
    private static final String TEMPLATE = "RB56_POWER_AND_HIGH_INPUT_SNAPSHOT_V1";
    private static final String SERVICE = "BOARD_POWER_OFF_SERVICE";
    private static final GeneratedDiagnosticServicePreparation.Policy PREPARATION =
        new GeneratedDiagnosticServicePreparation.Policy(30, .100);
    private final Rb56Plan plan;
    private final PcbBoardLayout layout;
    private final GeneratedPhysicalAdmission admission;

    Rb56DiagnosticProvider(Rb56Plan plan, PcbBoardLayout layout, GeneratedPhysicalAdmission admission) {
        if (plan == null || layout == null || admission == null)
            throw new IllegalArgumentException("RB56 diagnosis requires its exact physical admission");
        this.plan = plan; this.layout = layout; this.admission = admission;
    }
    public String getProviderId() { return "rb56-domain-diagnostic@1"; }
    public GeneratedDiagnosticServicePreparation.Policy getServicePreparationPolicy() { return PREPARATION; }

    public GeneratedDiagnosticPlan getDiagnosticPlan() {
        Vector<String> pads = new Vector<String>();
        for (String pad : new String[] {"CBULK.-", "CBULK.+", "RENAC.1", "RENAC.2", "UAC.OUT-",
                "COUT.+", "REN.2", "U1.OUTPUT", "RSA.2", "RDA.1", "RDA.2", relayId() + ".A2"}) pads.add(pad);
        for (String channel : plan.channels()) { pads.add("JO" + channel + ".1"); pads.add("JO" + channel + ".2"); }
        return new GeneratedDiagnosticPlan(TEMPLATE, "UAC.OUT-", pads.toArray(new String[pads.size()]),
            new String[] {"DC_VOLTAGE"}, new String[] {Rb30Behavior.SENSORS_HIGH, SERVICE},
            new String[] {WorkbenchOperation.REMOVE, WorkbenchOperation.LIFT_LEAD},
            new String[] {WorkbenchOperation.CATALOG_INSTALL},
            new String[] {WorkbenchOperation.LIFT_LEAD, WorkbenchOperation.RECONNECT_LEAD,
                WorkbenchOperation.REMOVE, WorkbenchOperation.CATALOG_INSTALL},
            new String[] {Rb30Behavior.SENSORS_HIGH, GeneratedBoardOperationIds.CUSTOMER_RETEST},
            new String[] {"HIGH_INPUT_SETTLED"},
            new String[] {"PRIMARY_DC", "ISOLATED_12V", "REGULATED_5V", "SENSOR_A_PATH",
                "DRIVE_A_PATH", "RELAY_COIL", "EXTERNAL_LOAD"},
            9, false, true, "PHYSICAL_COMPONENT_REPLACEMENT");
    }

    public GeneratedDiagnosticProgram getObservationProgram() {
        GeneratedDiagnosticProgram.Builder program = GeneratedDiagnosticProgram.builder(getDiagnosticPlan());
        // The same input and observations apply to every hypothesis. Healthy and
        // repaired behavior independently exercise every declared input combination.
        program.input(Rb30Behavior.SENSORS_HIGH).waitSample("HIGH_INPUT_SETTLED", 0);
        measure(program, "RECTIFIED_INPUT", "CBULK.+", "CBULK.-");
        measure(program, "PRIMARY_BIAS", "RENAC.1", "CBULK.-");
        measure(program, "PRIMARY_ENABLE", "RENAC.2", "CBULK.-");
        measure(program, "ISOLATED_12V", "COUT.+", "UAC.OUT-");
        measure(program, "REGULATOR_ENABLE", "REN.2", "UAC.OUT-");
        measure(program, "REGULATED_5V", "U1.OUTPUT", "UAC.OUT-");
        measure(program, "SENSOR_A_CONDITIONED", "RSA.2", "UAC.OUT-");
        measure(program, "DRIVER_A_COMMAND", "RDA.1", "UAC.OUT-");
        measure(program, "DRIVER_A_INPUT", "RDA.2", "UAC.OUT-");
        measure(program, "RELAY_COIL_RETURN", relayId() + ".A2", "UAC.OUT-");
        for (String channel : plan.channels()) measure(program, "OUTPUT_" + channel,
            "JO" + channel + ".1", "JO" + channel + ".2");
        program.power(SERVICE, BoardPowerState.UNPOWERED).settle();
        return program.build();
    }
    private static void measure(GeneratedDiagnosticProgram.Builder program, String id, String red, String black) {
        program.measure(GeneratedDiagnosticProgram.Kind.DC_VOLTAGE, id, red, black);
    }
    private String relayId() { return plan.channelCount == 1 ? "KA" : "KB"; }

    public GeneratedBoardInstance generateHypothesis(GeneratedFaultCandidate hypothesis) {
        if (hypothesis == null || !hypothesis.isAdmitted() ||
                !Rb56Plan.FAMILY_ID.equals(hypothesis.getFault().getCircuitFamilyId()) ||
                hypothesis.getFault().getSelectionSeed() != plan.seed)
            throw new IllegalArgumentException("Foreign RB56 diagnostic hypothesis");
        String faultId = Rb56Generator.faultIdForHypothesis(plan, hypothesis.getHypothesisKey());
        if (!faultId.equals(hypothesis.getFault().getId()))
            throw new IllegalArgumentException("RB56 hypothesis ID does not match its full identity");
        Rb56Generator.Candidate candidate = Rb56Generator.construct(plan, faultId);
        try { return Rb56Generator.assemble(candidate, layout.copySealed(), admission); }
        catch (RuntimeException failure) { Rb56Generator.disposeUnpublished(candidate.assembly, failure); throw failure; }
        catch (Error failure) { Rb56Generator.disposeUnpublished(candidate.assembly, failure); throw failure; }
    }

    public String getCorrectCatalogId(GeneratedBoardInstance instance, String componentId) {
        if (instance == null || !Rb56Plan.FAMILY_ID.equals(instance.getCircuitFamilyId()) ||
                instance.getSeed() != plan.seed || !plan.topology().equals(instance.getTopologyVariantId()) ||
                instance.getDiagnosticProvider() != this)
            throw new IllegalArgumentException("Foreign RB56 replacement query");
        if ("RENAC".equals(componentId) || "REN".equals(componentId) || "RSA".equals(componentId))
            return "R_CATALOG_10000";
        if ("RDA".equals(componentId)) return "R_CATALOG_1000";
        if (relayId().equals(componentId)) {
            PhysicalBoardRuntimeCapability capability = instance.getPhysicalBoardRuntime().getCapability("RB56_RELAY_" + componentId);
            if (capability instanceof ReplaceableRelayCapability &&
                    componentId.equals(((ReplaceableRelayCapability)capability).getComponentId()))
                return ReplaceableRelayCapability.COIL_5V;
        }
        throw new IllegalArgumentException("No declared RB56 fault replacement for " + componentId);
    }
}
