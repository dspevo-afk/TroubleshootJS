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
        verifyFinalGhostRejection();
        verifyRegulatorReplacementRetainsPhysicalMapping();
        verifyServicedPhysicalOwnersRemainTruthful();
        verifyEachFaultHypothesisChangesLiveGraph(sim);
        verifyPhysicalIntegrationAndScenarioSymptom(sim);
        System.out.println("PASS: SensorControl family contracts " + assertions +
            " assertions");
    }

    private static void verifyFinalGhostRejection() {
        GeneratedBoardInstance instance = QuickPlayFamilyRegistry.generate(
            QuickPlayFamilyRegistry.SENSOR_CONTROL, 0L);
        E04SensorControlModel model =
            ((SensorControlFamilyState) instance.getFamilyState()).getModel();
        SensorControlGenerator.validateFinalElementOwnership(instance, model);
        check(true, "complete post-service E04 solver graph is physically accounted for");
        ResistorElm ghost = new ResistorElm(2048, 2048);
        ghost.drag(2080, 2048);
        instance.registerRuntimeSimulationElement(ghost);
        boolean rejected = false;
        try {
            SensorControlGenerator.validateFinalElementOwnership(instance, model);
        } catch (IllegalStateException expected) {
            rejected = expected.getMessage() != null &&
                expected.getMessage().contains("unexplained final solver element");
        } finally {
            ghost.delete();
        }
        check(rejected, "unclaimed material element added after service completion fails closed");
    }

    /** A catalog U1 owns the live posts; the model's original selection is only the contract oracle. */
    private static void verifyRegulatorReplacementRetainsPhysicalMapping() {
        CirSim sim = new NativeServiceCirSim();
        sim.gridSize = 16;
        sim.gridMask = ~15;
        sim.gridRound = 7;
        sim.maxTimeStep = 1e-4;
        sim.minTimeStep = 1e-7;
        sim.adjustTimeStep = true;
        CircuitElm.sim = sim;
        GeneratedBoardInstance instance = QuickPlayFamilyRegistry.generate(
            QuickPlayFamilyRegistry.SENSOR_CONTROL, 0L);
        sim.elmList = new Vector<CircuitElm>(instance.getSimulationElements());
        sim.adjustables = new Vector<Adjustable>();
        sim.undoStack = new Vector<String>();
        sim.redoStack = new Vector<String>();
        sim.generatedBoardInstance = instance;
        sim.boardModificationController = new BoardModificationController(sim, instance);
        PhysicalBoardRuntime runtime = instance.getPhysicalBoardRuntime();
        runtime.installRegisteredCapabilities(sim, instance,
            sim.boardModificationController, 0);
        sim.boardPowerController.attach(instance.getExternalPowerBindings());
        sim.boardPowerController.setState(BoardPowerState.UNPOWERED);
        runtime.onBoardPowerStateChanged(BoardPowerState.UNPOWERED);

        ReplaceableRegulatorBoardCapability capability =
            ReplaceableRegulatorBoardCapability.require(instance);
        RegulatorSlotController controller = capability.getController();
        PhysicalRegulatorPart original = (PhysicalRegulatorPart)
            runtime.getInstalledPart("U1");
        check(controller != null && original != null && sim.isGeneratedRuntimeSettled(),
            "E04 native service fixture installs the real unpowered U1 mutation owner");
        check(controller.removeInstalledPart(),
            "E04 real U1 owner removes the original regulator");
        GeneratedBoardVerifier.verify(instance, BoardPowerState.UNPOWERED,
            sim.boardModificationController, sim.elmList, false);
        new SensorControlGeneratedBoardValidator().verify(instance,
            BoardPowerState.UNPOWERED);
        acknowledgeNativeServiceMutation(sim);

        String catalogId = capability.getCatalog().getEntries().firstElement().getId();
        check(controller.installNewFromCatalog(catalogId),
            "E04 real U1 owner installs a compatible catalog regulator");
        PhysicalRegulatorPart replacement = (PhysicalRegulatorPart)
            runtime.getInstalledPart("U1");
        check(replacement != null && replacement != original &&
                replacement.getElement() != original.getElement() &&
                instance.getComponentBindings().getSingleElement("U1") ==
                    replacement.getElement(),
            "E04 catalog replacement becomes the authoritative live U1 element");
        new SensorControlGeneratedBoardValidator().verify(instance,
            BoardPowerState.UNPOWERED);
        String[] terminals = { RailRegulationContract.INPUT_TERMINAL,
            RailRegulationContract.OUTPUT_TERMINAL,
            RailRegulationContract.RETURN_TERMINAL,
            RailRegulationContract.ENABLE_TERMINAL };
        int[] posts = { AbstractRailRegulatorElm.INPUT_POST,
            AbstractRailRegulatorElm.OUTPUT_POST,
            AbstractRailRegulatorElm.RETURN_POST,
            AbstractRailRegulatorElm.ENABLE_POST };
        for (int index = 0; index < terminals.length; index++) {
            CircuitMeasurementEndpoint endpoint = instance.getConnectionBindings()
                .get("U1", "U1." + terminals[index]).getComponentEndpoint();
            check(endpoint instanceof CircuitPostMeasurementEndpoint &&
                    ((CircuitPostMeasurementEndpoint) endpoint).getElement() ==
                        replacement.getElement() &&
                    ((CircuitPostMeasurementEndpoint) endpoint).getPostIndex() == posts[index],
                "E04 replacement U1 owns its live " + terminals[index] + " post");
        }
    }

    /** Catalog service must move E04 semantic ownership to each installed backing. */
    private static void verifyServicedPhysicalOwnersRemainTruthful() {
        verifyServicedPhysicalOwners(0L,
            new String[] { "RBIAS", "RREF", "RFB" });
        verifyServicedPhysicalOwners(1L,
            new String[] { "RREF_LOW", "RFB_HYST" });
    }

    private static void verifyServicedPhysicalOwners(long seed,
            String[] resistorIds) {
        NativeServiceContext context = new NativeServiceContext(seed);
        for (String componentId : resistorIds) {
            PhysicalPart<?> original = context.runtime.getInstalledPart(componentId);
            PhysicalSlotMutationProvider provider =
                context.runtime.getMutationProvider(componentId);
            check(original instanceof PhysicalResistorPart &&
                    provider instanceof ResistorSlotController,
                "E04 " + componentId + " exposes its real resistor service owner");
            ResistorSlotController controller = (ResistorSlotController) provider;
            GeneratedComponentConnectionBinding first = context.instance
                .getConnectionBindings().get(componentId, componentId + ".1");
            GeneratedComponentConnectionBinding second = context.instance
                .getConnectionBindings().get(componentId, componentId + ".2");
            CircuitMeasurementEndpoint firstBoard = first.getBoardEndpoint();
            CircuitMeasurementEndpoint secondBoard = second.getBoardEndpoint();

            check(controller.removeInstalledPart(),
                "E04 " + componentId + " removes through the production owner");
            requireNativeServiceState(context,
                "E04 " + componentId + " empty physical slot");
            acknowledgeNativeServiceMutation(context.sim);

            check(controller.installNewFromCatalog(
                    correctResistorCatalogId(componentId)),
                "E04 " + componentId + " installs its compatible catalog value");
            PhysicalPart<?> installed = context.runtime.getInstalledPart(componentId);
            check(installed instanceof PhysicalResistorPart && installed != original,
                "E04 " + componentId + " catalog install has a distinct physical identity");
            PhysicalResistorPart replacement = (PhysicalResistorPart) installed;
            Vector<CircuitElm> auxiliary = context.instance.getComponentBindings()
                .getAuxiliaryElements(componentId);
            check(context.instance.getComponentBindings().getSingleElement(componentId) ==
                    replacement.getElement() &&
                    replacement.getSecondaryOpenPath() != null &&
                    auxiliary.size() == 1 && auxiliary.firstElement() ==
                        replacement.getSecondaryOpenPath().getSimulationElement(),
                "E04 " + componentId + " bindings follow replacement backing and open path");
            check(GeneratedComponentConnectionBindings.sameEndpoint(
                        first.getBoardEndpoint(), firstBoard) &&
                    GeneratedComponentConnectionBindings.sameEndpoint(
                        second.getBoardEndpoint(), secondBoard) &&
                    GeneratedComponentConnectionBindings.sameEndpoint(
                        first.getComponentEndpoint(), replacement.getPublicTerminal(0)) &&
                    GeneratedComponentConnectionBindings.sameEndpoint(
                        second.getComponentEndpoint(), replacement.getPublicTerminal(1)),
                "E04 " + componentId +
                " keeps board copper while retargeting both replacement terminals");
            requireNativeServiceState(context,
                "E04 " + componentId + " installed catalog owner");
            acknowledgeNativeServiceMutation(context.sim);
        }

        E04DecisionControlBoardCapability decisionCapability =
            (E04DecisionControlBoardCapability) context.runtime.getCapability(
                E04DecisionControlBoardCapability.ID);
        E04DecisionControlSlotController decisionController =
            decisionCapability.getController();
        PhysicalPart<?> originalDecision = context.runtime.getInstalledPart("U2");
        check(originalDecision instanceof E04DecisionControlPart &&
                decisionController != null,
            "E04 seed " + seed + " exposes its real U2 service owner");
        check(decisionController.removeInstalledPart(),
            "E04 seed " + seed + " removes U2 through the production owner");
        requireNativeServiceState(context, "E04 seed " + seed + " empty U2 slot");
        acknowledgeNativeServiceMutation(context.sim);
        check(decisionController.installNewFromCatalog(
                E04DecisionControlBoardCapability.CATALOG_ID),
            "E04 seed " + seed + " installs a compatible catalog U2");
        PhysicalPart<?> installedDecision = context.runtime.getInstalledPart("U2");
        check(installedDecision instanceof E04DecisionControlPart &&
                installedDecision != originalDecision &&
                context.state.getModel().getActiveDecisionElement() ==
                    ((E04DecisionControlPart) installedDecision).getElement(),
            "E04 seed " + seed + " customer state follows the installed U2 backing");
        requireNativeServiceState(context, "E04 seed " + seed + " installed U2 owner");
        acknowledgeNativeServiceMutation(context.sim);
    }

    private static void requireNativeServiceState(NativeServiceContext context,
            String message) {
        GeneratedRuntimeInvariant.verify(context.instance, context.modifications,
            context.sim.elmList);
        GeneratedBoardVerifier.verify(context.instance, BoardPowerState.UNPOWERED,
            context.modifications, context.sim.elmList, false);
        new SensorControlGeneratedBoardValidator().verify(context.instance,
            BoardPowerState.UNPOWERED);
        check(true, message + " passes generic and E04 dynamic ownership validation");
    }

    private static String correctResistorCatalogId(String componentId) {
        if ("RFB".equals(componentId)) return "R_CATALOG_47";
        if ("RFB_HYST".equals(componentId)) return "R_CATALOG_22000";
        return "R_CATALOG_10000";
    }

    /** Native contracts explicitly consume the UI-loop work queued by a proven synchronous mutation. */
    private static void acknowledgeNativeServiceMutation(CirSim sim) {
        check(sim.generatedBoardVerificationPending && sim.analyzeFlag,
            "E04 service mutation queues production graph verification");
        sim.generatedBoardVerificationPending = false;
        sim.generatedBoardVerificationAnalyzed = false;
        sim.analyzeFlag = false;
        sim.dcAnalysisFlag = false;
    }

    private static final class NativeServiceContext {
        final NativeServiceCirSim sim;
        final GeneratedBoardInstance instance;
        final PhysicalBoardRuntime runtime;
        final BoardModificationController modifications;
        final SensorControlFamilyState state;

        NativeServiceContext(long seed) {
            sim = new NativeServiceCirSim();
            sim.gridSize = 16;
            sim.gridMask = ~15;
            sim.gridRound = 7;
            sim.maxTimeStep = 1e-4;
            sim.minTimeStep = 1e-7;
            sim.adjustTimeStep = true;
            CircuitElm.sim = sim;
            instance = QuickPlayFamilyRegistry.generate(
                QuickPlayFamilyRegistry.SENSOR_CONTROL, seed);
            sim.elmList = new Vector<CircuitElm>(instance.getSimulationElements());
            sim.adjustables = new Vector<Adjustable>();
            sim.undoStack = new Vector<String>();
            sim.redoStack = new Vector<String>();
            sim.generatedBoardInstance = instance;
            modifications = new BoardModificationController(sim, instance);
            sim.boardModificationController = modifications;
            runtime = instance.getPhysicalBoardRuntime();
            runtime.installRegisteredCapabilities(sim, instance, modifications, 0);
            sim.boardPowerController.attach(instance.getExternalPowerBindings());
            sim.boardPowerController.setState(BoardPowerState.UNPOWERED);
            runtime.onBoardPowerStateChanged(BoardPowerState.UNPOWERED);
            state = (SensorControlFamilyState) instance.getFamilyState();
        }
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
            verifyDecisionAndSupportPhysicalMapping(instance, state);
            E04SensorControlModel ownershipModel = state.getModel();
            ownershipModel.validateElementOwnership(
                ownershipModel.getSimulationElements(), instance.getBoard());
            SensorControlGenerator.validateFinalElementOwnership(instance, ownershipModel);
            emitFinalOwnership(instance, ownershipModel, seed);
            StringBuilder ownershipReceipt = new StringBuilder("E04_OWNERSHIP seed=")
                .append(seed).append(" variant=").append(instance.getTopologyVariantId());
            for (E04SensorControlModel.ElementOwnership row :
                    ownershipModel.getElementOwnership())
                ownershipReceipt.append(" | ").append(row.getElement().getClass().getSimpleName())
                    .append(':').append(row.getKind()).append(':')
                    .append(row.getOwnerId());
            System.out.println(ownershipReceipt.toString());
            check(true, "every E04 material element names a physical or explicit external owner");
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

    private static void emitFinalOwnership(GeneratedBoardInstance instance,
            E04SensorControlModel model, long seed) {
        StringBuilder receipt = new StringBuilder("E04_FINAL_OWNERSHIP seed=")
            .append(seed).append(" variant=").append(instance.getTopologyVariantId());
        int index = 0;
        for (CircuitElm element : instance.getSimulationElements()) {
            String owner = null;
            E04SensorControlModel.ElementOwnership row = model.getElementOwnership(element);
            if (row != null) owner = row.getKind() + ":" + row.getOwnerId();
            if (owner == null)
                for (GeneratedComponentConnectionBinding connection :
                        instance.getConnectionBindings().getAll())
                    if (connection.getConnectionElement() == element)
                        owner = "PAD_CONNECTION:" + connection.getPadId();
            if (owner == null)
                for (String id : instance.getBoard().getComponentIds())
                    if (instance.getComponentBindings().hasComponentBinding(id) &&
                            instance.getComponentBindings().isElementBoundToComponent(id, element))
                        owner = "PHYSICAL_COMPONENT:" + id;
            if (owner == null)
                for (GeneratedFaultCandidate candidate : instance.getFaultCandidates())
                    if (candidate.getPrivateSimulationElements().contains(element))
                        owner = "FAULT_HELPER:" + candidate.getFault().getTargetComponentId();
            check(owner != null, "every final solver element has a declared owner");
            receipt.append(" | ").append(index++).append(':')
                .append(element.getClass().getSimpleName()).append(':').append(owner);
        }
        System.out.println(receipt.toString());
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
                    state.getModel().getPhysicalSensorBoardEndpoint().getElement() &&
                endpoint(healthy, "RBIAS.2").getElement() !=
                    endpoints.getExternalSensorSourceEndpoint().getElement(),
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

    /** E04's decision and supporting resistors must be physical solver owners. */
    private static void verifyDecisionAndSupportPhysicalMapping(
            GeneratedBoardInstance instance, SensorControlFamilyState state) {
        E04SensorControlModel model = state.getModel();
        BoardComponent u2 = instance.getBoard().getComponent("U2");
        BoardComponent rrefLow = instance.getBoard().getComponent("RREF_LOW");
        boolean hysteretic = model.hasRegenerativeFeedback();
        BoardComponent rfbHyst = instance.getBoard().getComponent("RFB_HYST");
        PhysicalPart<?> u2Part = instance.getPhysicalBoardRuntime()
            .getInstalledPart("U2");
        check(u2 != null && rrefLow != null &&
                (!hysteretic || rfbHyst != null) &&
                u2.getPhysicalPackage() == PhysicalPackages.E04_DECISION_CONTROL_5 &&
                u2Part instanceof E04DecisionControlPart &&
                u2Part.getTerminalCount() == 5 &&
                instance.getPhysicalBoardRuntime().getWorkbenchPartsProvider("U2") != null,
            "E04 publishes a serviceable five-terminal physical decision owner");
        check(instance.getPhysicalSpecifications().getSpecification("U2") != null &&
                instance.getPhysicalSpecifications().getSpecification("RREF_LOW") != null &&
                (!hysteretic || instance.getPhysicalSpecifications()
                    .getSpecification("RFB_HYST") != null),
            "E04 physical specifications include the mapped control and support passives");
        check(instance.getComponentBindings().getSingleElement("U2") ==
                model.getDecisionElement(),
            "U2 component identity is the model's live decision element");
        boolean implicitGround = false;
        for (int terminal = 0; terminal < model.getDecisionElement().getPostCount(); terminal++)
            implicitGround |= model.getDecisionElement().hasGroundConnection(terminal);
        check(!implicitGround,
            "U2 RETURN is a mapped terminal rather than a hidden global-ground path");

        String[] pads = { "U2.SENSOR", "U2.REFERENCE", "U2.RAIL",
            "U2.OUTPUT", "U2.RETURN" };
        CircuitPostMeasurementEndpoint[] expected = {
            new CircuitPostMeasurementEndpoint(model.getDecisionElement(), 0),
            new CircuitPostMeasurementEndpoint(model.getDecisionElement(), 1),
            new CircuitPostMeasurementEndpoint(model.getDecisionElement(), 2),
            new CircuitPostMeasurementEndpoint(model.getDecisionElement(), 3),
            new CircuitPostMeasurementEndpoint(model.getDecisionElement(), 4) };
        for (int index = 0; index < pads.length; index++) {
            GeneratedComponentConnectionBinding binding = connection(instance, pads[index]);
            CircuitPostMeasurementEndpoint componentEndpoint =
                (CircuitPostMeasurementEndpoint) binding.getComponentEndpoint();
            check(sameEndpoint(binding.getBoardEndpoint(),
                    instance.getSimulationBindings().getEndpoint(pads[index])) &&
                    componentEndpoint.getElement() == model.getDecisionElement() &&
                    componentEndpoint.getPostIndex() == index &&
                    sameEndpoint(componentEndpoint, expected[index]) &&
                    binding.getConnectionElement() instanceof WireElm,
                "E04 U2 pad " + pads[index] + " has a real detachable solver lead");
        }
        check(sameEndpoint(connection(instance, "U2.SENSOR").getBoardEndpoint(),
                    model.getPhysicalSensorBoardEndpoint()) &&
                sameEndpoint(connection(instance, "U2.REFERENCE").getBoardEndpoint(),
                    model.getPhysicalReferenceBoardEndpoint()) &&
                sameEndpoint(connection(instance, "U2.OUTPUT").getBoardEndpoint(),
                    model.getPhysicalOutputBoardEndpoint()) &&
                sameEndpoint(connection(instance, "U2.RETURN").getBoardEndpoint(),
                    model.getPhysicalReturnBoardEndpoint()) &&
                sameEndpoint(connection(instance, "U2.RAIL").getBoardEndpoint(),
                    model.getSensorControlBindings().getRegulatorOutputBoardEndpoint()),
            "E04 U2 board endpoints remain mapped to persistent copper anchors");
        check(sameEndpoint(endpoint(instance, "J3.2"),
                    model.getPhysicalReturnBoardEndpoint()) &&
                !sameEndpoint(endpoint(instance, "J3.2"),
                    model.getSensorControlBindings().getReturnEndpoint()),
            "E04 J3 return stays on board copper rather than U2's detachable RETURN pin");

        check(sameEndpoint(connection(instance, "RREF_LOW.1").getComponentEndpoint(),
                    model.getBoardSupportPassive("RREF_LOW").getFirstEndpoint()) &&
                sameEndpoint(connection(instance, "RREF_LOW.2").getComponentEndpoint(),
                    model.getBoardSupportPassive("RREF_LOW").getSecondEndpoint()) &&
                "CONTROL_REFERENCE".equals(rrefLowPadNet(instance, "1")) &&
                "CONTROL_RETURN".equals(rrefLowPadNet(instance, "2")),
            "E04 RREF_LOW is a real reference-return resistor on board pads");
        if (hysteretic) {
            check(sameEndpoint(connection(instance, "RFB_HYST.1").getComponentEndpoint(),
                        model.getBoardSupportPassive("RFB_HYST").getFirstEndpoint()) &&
                    sameEndpoint(connection(instance, "RFB_HYST.2").getComponentEndpoint(),
                        model.getBoardSupportPassive("RFB_HYST").getSecondEndpoint()) &&
                    "CONTROL_OUTPUT".equals(instance.getBoard().getPad("RFB_HYST.1").getNetId()) &&
                    "CONDITIONED_SENSOR".equals(instance.getBoard().getPad("RFB_HYST.2").getNetId()),
                "E04 hysteretic variant maps its feedback resistor to solver-backed pads");
        } else {
            check(rfbHyst == null && model.getBoardSupportPassive("RFB_HYST") == null,
                "E04 direct variant does not publish a ghost hysteresis component");
        }
    }

    private static String rrefLowPadNet(GeneratedBoardInstance instance, String terminal) {
        return instance.getBoard().getPad("RREF_LOW." + terminal).getNetId();
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

    /** Keeps the native service fixture on the production mutation path without browser repaint UI. */
    private static final class NativeServiceCirSim extends CirSim {
        @Override void needAnalyze() {
            if (elmList != null && CircuitElm.sim == this) solverExecutor.invalidate();
            analyzeFlag = true;
        }

        @Override void refreshBoardModificationControls() { }
    }
}
