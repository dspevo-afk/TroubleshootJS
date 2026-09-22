package com.lushprojects.circuitjs1.client;

import java.util.Vector;

/** Native contract for the playable E04 leaf family. */
public final class SensorControlFamilyContractTest {
    private static int assertions;

    private SensorControlFamilyContractTest() { }

    public static void main(String[] args) {
        CirSim sim = new CirSim();
        sim.gridSize = 16;
        sim.gridMask = ~15;
        sim.gridRound = 7;
        sim.maxTimeStep = 1e-4;
        sim.minTimeStep = 1e-7;
        sim.adjustTimeStep = true;
        CircuitElm.sim = sim;
        verifyVariantsAndPlayerOperations(sim);
        verifyEachFaultHypothesisChangesLiveGraph(sim);
        verifyPhysicalIntegrationAndScenarioSymptom(sim);
        System.out.println("PASS: SensorControl family contracts " + assertions +
            " assertions");
    }

    private static void verifyVariantsAndPlayerOperations(CirSim sim) {
        String priorVariant = null;
        for (long seed : new long[] { 0L, 1L }) {
            GeneratedBoardInstance instance = QuickPlayFamilyRegistry.generate(
                QuickPlayFamilyRegistry.SENSOR_CONTROL, seed);
            GeneratedDiagnosticSolvabilityAdmission.validateStructural(instance);
            installSolver(sim, instance);
            sim.analyzeCircuit();
            sim.solverExecutor.advanceSteps(8);
            SensorControlFamilyState state = (SensorControlFamilyState) instance.getFamilyState();
            check(instance.getFaultCandidates().size() == 3 &&
                    allAdmittedPhysicalLoci(instance),
                "E04 admits only the three common causal physical resistor loci");
            GeneratedDiagnosticPlan diagnosticPlan = instance.getDiagnosticProvider()
                .getDiagnosticPlan();
            check(diagnosticPlan.getInputPowerTransitions().contains(
                    GeneratedBoardOperationIds.SENSOR_CONDITION_LOW) &&
                    diagnosticPlan.getInputPowerTransitions().contains(
                    GeneratedBoardOperationIds.SENSOR_CONDITION_MID) &&
                    diagnosticPlan.getInputPowerTransitions().contains(
                    GeneratedBoardOperationIds.SENSOR_CONDITION_HIGH),
                "E04 declares every player-operated sensor condition as a diagnostic transition");
            check(instance.getFaultServiceability() != null &&
                    instance.getFaultServiceability().isAdmissible(),
                "selected E04 locus has public serviceability metadata");
            check(instance.getDiagnosticProvider() != null &&
                    !instance.getDiagnosticProvider().getDiagnosticPlan()
                        .getProbeTargetIds().isEmpty(),
                "E04 diagnostic provider exposes legal public probe targets");
            int samplesPerHypothesis = observationSampleCount(instance.getDiagnosticProvider()
                .getObservationProgram());
            check(samplesPerHypothesis == 36 &&
                    samplesPerHypothesis * instance.getFaultCandidates().size() == 108,
                "E04 retains all ten public probe targets across three sensor conditions and " +
                "all three passive checks in its complete diagnostic population");
            check(state.getModel().hasE02Regulator() &&
                    state.getModel().getE02RegulatorVariantId() != null,
                "E04 live graph retains its selected E02 rail identity");
            check(priorVariant == null || !priorVariant.equals(instance.getTopologyVariantId()),
                "seeded E04 construction reaches both direct and hysteretic variants");
            priorVariant = instance.getTopologyVariantId();
            instance.invokeOperation(GeneratedBoardOperationIds.SENSOR_CONDITION_LOW, sim);
            check(state.getModel().getControlState() == E04SensorControlModel.ControlState.LOW,
                "normal-player LOW operation changes the live E04 decision");
            instance.invokeOperation(GeneratedBoardOperationIds.SENSOR_CONDITION_MID, sim);
            check(state.getModel().getSensorCondition() ==
                    E04SensorControlModel.SensorCondition.SENSOR_MID,
                "normal-player MID operation changes the live sensor source");
            instance.invokeOperation(GeneratedBoardOperationIds.SENSOR_CONDITION_HIGH, sim);
            check(state.getModel().getControlState() == E04SensorControlModel.ControlState.HIGH &&
                    state.getModel().isOutputHigh(),
                "normal-player HIGH operation drives the loaded solver output");
            check(instance.getOperationCatalog().find(GeneratedBoardOperationIds.SENSOR_CONDITION_LOW) != null &&
                    instance.getOperationCatalog().find(GeneratedBoardOperationIds.SENSOR_CONDITION_MID) != null &&
                    instance.getOperationCatalog().find(GeneratedBoardOperationIds.SENSOR_CONDITION_HIGH) != null,
                "E04 operation catalog contains only semantic sensor controls");
        }
    }

    private static void verifyEachFaultHypothesisChangesLiveGraph(CirSim sim) {
        GeneratedBoardInstance baseline = QuickPlayFamilyRegistry.generate(
            QuickPlayFamilyRegistry.SENSOR_CONTROL, 0L);
        for (GeneratedFaultCandidate candidate : baseline.getFaultCandidates()) {
            GeneratedBoardInstance instance = new SensorControlGenerator()
                .generateForHypothesis(0L, candidate.getHypothesisKey());
            installSolver(sim, instance);
            sim.analyzeCircuit();
            sim.solverExecutor.advanceSteps(8);
            GeneratedFaultBinding binding = instance.getFaultBinding();
            binding.setApplied(true);
            sim.solverExecutor.invalidate();
            sim.analyzeFlag = true;
            sim.analyzeCircuit();
            sim.solverExecutor.advanceSteps(8);
            check(SensorControlGeneratedBoardValidator.resistorCurrent(instance,
                    binding.getFault().getTargetComponentId()) < .000001,
                "selected E04 open fault removes current in its live branch");
            binding.setApplied(false);
        }
    }

    private static void verifyPhysicalIntegrationAndScenarioSymptom(CirSim sim) {
        GeneratedBoardInstance healthy = QuickPlayFamilyRegistry.generate(
            QuickPlayFamilyRegistry.SENSOR_CONTROL, 0L);
        installSolver(sim, healthy);
        sim.analyzeCircuit();
        sim.solverExecutor.advanceSteps(8);
        SensorControlFamilyState state = (SensorControlFamilyState) healthy.getFamilyState();
        E04SensorControlModel.SensorControlBindings endpoints =
            state.getModel().getSensorControlBindings();
        new SensorControlGeneratedBoardValidator().verify(healthy, BoardPowerState.POWERED);
        check(true,
            "E04 full physical admission validates the installed solver-backed board");
        BoardComponent u1 = healthy.getBoard().getComponent("U1");
        PhysicalBoardSlot u1Slot = healthy.getPhysicalBoardRuntime().getSlot("U1");
        PhysicalPart<?> u1Part = u1Slot == null ? null : u1Slot.getInstalledPart();
        check(u1 != null && u1Slot != null && u1Part instanceof PhysicalRegulatorPart &&
                PhysicalPackages.TO220_REGULATOR_4.isEquivalentTo(u1.getPhysicalPackage()) &&
                PhysicalPackages.TO220_REGULATOR_4.isEquivalentTo(u1Part.getPackage()) &&
                healthy.getPhysicalSpecifications().getSpecification("U1") != null,
            "E04 has a serviceable physical U1 regulator part, slot and specification");
        check(sameEndpoint(connection(healthy, "U1.INPUT").getBoardEndpoint(),
                    endpoints.getRegulatorInputBoardEndpoint()) &&
                sameEndpoint(connection(healthy, "U1.OUTPUT").getBoardEndpoint(),
                    endpoints.getRegulatorOutputBoardEndpoint()) &&
                sameEndpoint(connection(healthy, "U1.RETURN").getBoardEndpoint(),
                    endpoints.getRegulatorReturnBoardEndpoint()) &&
                sameEndpoint(connection(healthy, "U1.ENABLE").getBoardEndpoint(),
                    endpoints.getRegulatorEnableBoardEndpoint()) &&
                sameEndpoint(connection(healthy, "U1.INPUT").getComponentEndpoint(),
                    endpoints.getRegulatorInputEndpoint()) &&
                sameEndpoint(connection(healthy, "U1.OUTPUT").getComponentEndpoint(),
                    endpoints.getRegulatorOutputEndpoint()) &&
                sameEndpoint(connection(healthy, "U1.RETURN").getComponentEndpoint(),
                    endpoints.getRegulatorReturnEndpoint()) &&
                sameEndpoint(connection(healthy, "U1.ENABLE").getComponentEndpoint(),
                    endpoints.getRegulatorEnableEndpoint()) &&
                endpoints.getRegulatorOutputEndpoint().getElement() ==
                    state.getModel().getSelectedE02Regulator(),
            "E04 U1 has distinct copper endpoints and bijective exact live regulator posts");
        check(sameEndpoint(endpoint(healthy, "J1.1"), endpoints.getRawInputEndpoint()) &&
                sameEndpoint(endpoint(healthy, "J1.2"), endpoints.getRawReturnEndpoint()) &&
                "RAW_INPUT".equals(healthy.getBoard().getPad("J1.1").getNetId()) &&
                "CONTROL_RETURN".equals(healthy.getBoard().getPad("J1.2").getNetId()) &&
                "CONTROL_RAIL".equals(healthy.getBoard().getPad("U1.OUTPUT").getNetId()),
            "E04 J1 is the raw finite-source boundary and U1.OUTPUT is the rail endpoint");
        check("SENSOR_SOURCE".equals(healthy.getBoard().getPad("J2.1").getNetId()) &&
                "SENSOR_SOURCE".equals(healthy.getBoard().getPad("RBIAS.1").getNetId()) &&
                "CONDITIONED_SENSOR".equals(healthy.getBoard().getPad("RBIAS.2").getNetId()) &&
                !healthy.getBoard().getPad("J2.1").getNetId().equals(
                    healthy.getBoard().getPad("RBIAS.2").getNetId()) &&
                endpoint(healthy, "J2.1").getElement() ==
                    endpoints.getExternalSensorSourceEndpoint().getElement() &&
                endpoint(healthy, "RBIAS.2").getElement() ==
                    endpoints.getConditionedSensorEndpoint().getElement(),
            "E04 external sensor source and conditioned sensor copper are distinct live nodes");
        check("CONTROL_OUTPUT".equals(healthy.getBoard().getPad("RFB.1").getNetId()) &&
                "CONTROL_OUTPUT_LOAD".equals(healthy.getBoard().getPad("RFB.2").getNetId()) &&
                "CONTROL_OUTPUT_LOAD".equals(healthy.getBoard().getPad("J3.1").getNetId()),
            "E04 output-resistance seam preserves the loaded output endpoint");
        check(sameEndpoint(connection(healthy, "RBIAS.1").getComponentEndpoint(),
                    state.getModel().getBoardOwnedPassive("RBIAS").getFirstEndpoint()) &&
                sameEndpoint(connection(healthy, "RBIAS.2").getComponentEndpoint(),
                    state.getModel().getBoardOwnedPassive("RBIAS").getSecondEndpoint()) &&
                sameEndpoint(connection(healthy, "RREF.1").getBoardEndpoint(),
                    endpoints.getRegulatorOutputBoardEndpoint()) &&
                sameEndpoint(connection(healthy, "RREF.1").getComponentEndpoint(),
                    state.getModel().getBoardOwnedPassive("RREF").getFirstEndpoint()) &&
                sameEndpoint(connection(healthy, "RREF.2").getComponentEndpoint(),
                    state.getModel().getBoardOwnedPassive("RREF").getSecondEndpoint()) &&
                sameEndpoint(connection(healthy, "RFB.1").getComponentEndpoint(),
                    state.getModel().getBoardOwnedPassive("RFB").getFirstEndpoint()) &&
                sameEndpoint(connection(healthy, "RFB.2").getComponentEndpoint(),
                    state.getModel().getBoardOwnedPassive("RFB").getSecondEndpoint()),
            "E04 replaceable resistor pads terminate at model-owned resistor seams");
        check(SensorControlGeneratedBoardValidator.isHealthyLow(healthy),
            "E04 generated healthy LOW behavior is solver-backed");
        state.applyCondition(sim, E04SensorControlModel.SensorCondition.SENSOR_HIGH);
        check(SensorControlGeneratedBoardValidator.isHealthyHigh(healthy),
            "E04 generated healthy HIGH behavior is solver-backed");
        state.applyCondition(sim, E04SensorControlModel.SensorCondition.SENSOR_LOW);

        SensorControlScenarioCompatibility scenario = new SensorControlScenarioCompatibility();
        check(!scenario.matches(healthy, null, BoardPowerState.POWERED,
                GeneratedObservedBehavior.SENSOR_CONTROL_OUTPUT_NOT_TRACKING),
            "E04 healthy live LOW/MID/HIGH sweep does not match the complaint");

        for (GeneratedFaultCandidate candidate : healthy.getFaultCandidates()) {
            String hypothesis = candidate.getHypothesisKey();
            String target = candidate.getFault().getTargetComponentId();
            GeneratedBoardInstance faulted = new SensorControlGenerator()
                .generateForHypothesis(0L, hypothesis);
            installSolver(sim, faulted);
            sim.analyzeCircuit();
            sim.solverExecutor.advanceSteps(8);
            GeneratedFaultBinding binding = faulted.getFaultBinding();
            binding.setApplied(true);
            sim.solverExecutor.invalidate();
            sim.analyzeFlag = true;
            sim.analyzeCircuit();
            sim.solverExecutor.advanceSteps(8);
            check(scenario.matches(faulted, null, BoardPowerState.POWERED,
                    GeneratedObservedBehavior.SENSOR_CONTROL_OUTPUT_NOT_TRACKING),
                "E04 " + target + " open creates a live complaint symptom");
            binding.setApplied(false);
            sim.solverExecutor.invalidate();
            sim.analyzeFlag = true;
            sim.analyzeCircuit();
            sim.solverExecutor.advanceSteps(8);
            check(SensorControlGeneratedBoardValidator.isHealthyLow(faulted),
                "E04 " + target + " repair restores live LOW behavior");
            SensorControlFamilyState repaired =
                (SensorControlFamilyState) faulted.getFamilyState();
            repaired.applyCondition(sim, E04SensorControlModel.SensorCondition.SENSOR_HIGH);
            check(SensorControlGeneratedBoardValidator.isHealthyHigh(faulted),
                "E04 " + target + " repair restores live HIGH retest behavior");
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
        assertions++;
    }

    private static void installSolver(CirSim sim, GeneratedBoardInstance instance) {
        sim.elmList = instance.getSimulationElements();
        sim.adjustables = new Vector<Adjustable>();
        CircuitElm.sim = sim;
    }

    private static CircuitPostMeasurementEndpoint endpoint(GeneratedBoardInstance instance,
            String padId) {
        CircuitMeasurementEndpoint endpoint = instance.getSimulationBindings().getEndpoint(padId);
        if (!(endpoint instanceof CircuitPostMeasurementEndpoint))
            throw new AssertionError("Missing live E04 endpoint: " + padId);
        return (CircuitPostMeasurementEndpoint) endpoint;
    }

    private static boolean sameEndpoint(CircuitMeasurementEndpoint first,
            CircuitMeasurementEndpoint second) {
        return GeneratedComponentConnectionBindings.sameEndpoint(first, second);
    }

    private static GeneratedComponentConnectionBinding connection(
            GeneratedBoardInstance instance, String padId) {
        BoardPad pad = instance.getBoard().getPad(padId);
        return instance.getConnectionBindings().get(pad.getComponentId(), padId);
    }

    private static boolean allAdmittedPhysicalLoci(GeneratedBoardInstance instance) {
        boolean rbias = false;
        boolean rref = false;
        boolean rfb = false;
        for (GeneratedFaultCandidate candidate : instance.getFaultCandidates()) {
            if (!candidate.isAdmitted()) return false;
            String component = candidate.getFault().getTargetComponentId();
            rbias |= "RBIAS".equals(component);
            rref |= "RREF".equals(component);
            rfb |= "RFB".equals(component);
        }
        return rbias && rref && rfb;
    }

    /** Counts emitted samples rather than steps because a diode step emits two ordered readings. */
    private static int observationSampleCount(GeneratedDiagnosticProgram program) {
        int samples = 0;
        for (GeneratedDiagnosticProgram.Step step : program.getSteps()) {
            if (step.kind == GeneratedDiagnosticProgram.Kind.DIODE) samples += 2;
            else if (step.kind == GeneratedDiagnosticProgram.Kind.DC_VOLTAGE ||
                    step.kind == GeneratedDiagnosticProgram.Kind.RESISTANCE ||
                    step.kind == GeneratedDiagnosticProgram.Kind.CONTINUITY) samples++;
        }
        return samples;
    }
}
