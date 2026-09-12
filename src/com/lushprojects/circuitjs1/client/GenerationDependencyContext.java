package com.lushprojects.circuitjs1.client;

import java.util.Collections;
import java.util.Comparator;
import java.util.Map;
import java.util.Vector;

/**
 * Immutable, internal dependency envelope for a generated-board proof.
 *
 * <p>The envelope is deliberately a value object.  Capture reads the live
 * CircuitJS graph, generation metadata, physical catalog, and the current PCB
 * realization, then retains only one bounded canonical string and its hash.
 * It is therefore safe to compare a candidate-ready snapshot with the
 * snapshot taken immediately before publication without retaining a simulator
 * or any mutable model object.</p>
 *
 * <p>This class is package-private because its canonical value contains
 * developer-only generation and fault inputs.  It is not a player-facing
 * answer or UI model.</p>
 */
final class GenerationDependencyContext {
    /** Current interpretation epoch.  Caches are valid only in this runtime. */
    static final String INTERPRETATION_EPOCH = "tsj-a10-generation-dependencies-v3";
    static final String CIRCUIT_DUMP_EPOCH =
        "circuitjs-source-load-model-inputs-no-transient-dump-v2";
    static final String POWER_REFERENCE_STORAGE_SEAM = "power-domain-contract-v1";
    private static final String POWER_DYNAMIC_INPUT_POLICY =
        "excluded:SolverExecutionBoundary.Observation,rail-voltage-samples," +
        "PowerOperatingAssessment,StoredEnergyMeasurementReadinessCapability.voltageDiff," +
        "GeneratedExternalPowerBindings.connectionRevision";
    private static final String POWER_UNAVAILABLE_CONSUMED_INPUTS = "none";
    private static final String POWER_LEAF_CONTRACT_POLICY =
        "none:BoardPowerController-and-GeneratedExternalPowerBindings-only";

    private static final int MAX_CANONICAL_LENGTH = 1024 * 1024;
    private static final int MAX_MANIFEST_LENGTH = 64 * 1024;
    private static final int MAX_FIELD_LENGTH = 256 * 1024;

    private final String canonical;
    private final int hash;

    private GenerationDependencyContext(String canonical) {
        this.canonical = canonical;
        hash = canonical.hashCode();
    }

    /**
     * Captures all dependencies available at the generated-board ownership
     * boundary.  Missing required data fails closed; it is never represented
     * as an empty fingerprint.
     */
    static GenerationDependencyContext capture(CirSim sim,
            GeneratedBoardInstance owner, String requestManifest, String realizationManifest) {
        require(sim != null, "Missing simulator for generation dependency capture");
        require(owner != null, "Missing generated board for generation dependency capture");
        require(requestManifest != null && requestManifest.length() > 0,
            "Missing generation request manifest");
        if (requestManifest.length() > MAX_MANIFEST_LENGTH)
            throw new IllegalArgumentException("Generation request manifest exceeds its bound");
        require(realizationManifest != null && realizationManifest.length() > 0,
            "Missing generation realization manifest");
        // RealizationManifest already permits a 256 KiB encoding. Keep its
        // complete evidence separate from the much smaller request descriptor.
        if (realizationManifest.length() > MAX_FIELD_LENGTH)
            throw new IllegalArgumentException("Generation realization manifest exceeds its bound");

        TroubleshootBoard board = owner.getBoard();
        GeneratedComponentBindings componentBindings = owner.getComponentBindings();
        GeneratedExternalPowerBindings powerBindings = owner.getExternalPowerBindings();
        GeneratedComponentConnectionBindings connectionBindings = owner.getConnectionBindings();
        BoardPhysicalSpecifications physicalSpecifications = owner.getPhysicalSpecifications();
        PhysicalBoardRuntime physicalRuntime = owner.getPhysicalBoardRuntime();
        PcbBoardLayout layout = owner.getPcbLayout();
        PcbConductorGraph pristineConductors = owner.getPristineConductorGraph();
        PcbConductorGraph.Snapshot currentConductors = owner.getCurrentConductorSnapshot();
        GeneratedDiagnosticProvider provider = owner.getDiagnosticProvider();
        GeneratedDiagnosticSolvabilityContract solvability =
            owner.getDiagnosticSolvabilityContract();

        require(board != null, "Generated board has no logical board");
        require(componentBindings != null, "Generated board has no component bindings");
        require(powerBindings != null, "Generated board has no power bindings");
        require(connectionBindings != null, "Generated board has no connection bindings");
        require(physicalSpecifications != null, "Generated board has no physical specifications");
        require(physicalRuntime != null, "Generated board has no physical runtime");
        require(layout != null, "Generated board has no PCB layout");
        require(pristineConductors != null, "Generated board has no pristine conductor graph");
        require(currentConductors != null, "Generated board has no current conductor snapshot");
        require(provider != null, "Generated board has no diagnostic provider");
        require(solvability != null, "Generated board has no diagnostic solvability contract");
        require(sim.elmList != null, "Simulator has no owned element list");
        PowerDomainContract powerContract = capturePowerContract(physicalRuntime);

        GeneratedDiagnosticPlan plan = provider.getDiagnosticPlan();
        GeneratedDiagnosticProgram program = provider.getObservationProgram();
        require(plan != null, "Diagnostic provider has no plan");
        require(program != null, "Diagnostic provider has no observation program");

        Vector<GeneratedFaultCandidate> candidates = owner.getFaultCandidates();
        require(candidates != null && !candidates.isEmpty(),
            "Generated board has no hypothesis population");
        GeneratedFaultBinding selectedBinding = owner.getFaultBinding();
        require(selectedBinding != null, "Generated board has no selected fault binding");
        require(selectedBinding.getFault() != null, "Selected fault binding has no fault");
        require(selectedBinding.getServiceability() != null,
            "Selected fault binding has no serviceability contract");

        StringBuilder out = new StringBuilder();
        frame(out, INTERPRETATION_EPOCH);
        appendField(out, "request.manifest", requestManifest);
        appendField(out, "realization.manifest", realizationManifest);
        appendField(out, "epoch.circuit-dump", CIRCUIT_DUMP_EPOCH);
        appendField(out, "epoch.geometry-contract", Integer.toString(PcbGeometryContractVersion.CURRENT));
        appendField(out, "epoch.layout-generator", Integer.toString(SeededPcbLayoutGenerator.CURRENT_VERSION));
        appendField(out, "epoch.diagnostic-program", Integer.toString(GeneratedDiagnosticProgram.VERSION));
        appendField(out, "epoch.diagnostic-solvability",
            Integer.toString(GeneratedDiagnosticSolvabilityContract.VERSION));
        appendField(out, "epoch.power-reference-storage", POWER_REFERENCE_STORAGE_SEAM);

        appendField(out, "board.id", board.getId());
        appendField(out, "board.family", owner.getCircuitFamilyId());
        appendField(out, "board.topology", owner.getTopologyVariantId());
        appendField(out, "board.seed", Long.toString(owner.getSeed()));
        appendField(out, "board.description", owner.getDescription());
        appendField(out, "board.developer-only", Boolean.toString(owner.isDeveloperOnlyFaultRoute()));

        appendBoard(out, board, physicalSpecifications);
        appendBoardSimulationBindings(out, board);
        appendSimulationSettings(out, sim);
        appendElementBatch(out, "simulation.elements", sim.elmList);
        appendComponentBindings(out, board, componentBindings, connectionBindings);
        appendPowerBindings(out, board, powerBindings, powerContract);
        appendConnectionBindings(out, connectionBindings);
        appendDiagnosticDependencies(out, owner, provider, plan, program, solvability,
            candidates, selectedBinding);
        appendScenarioDependencies(out, owner, sim.getGeneratedChallengeController());
        appendTemporalDependency(out, owner);
        appendPhysicalDependencies(out, board, physicalSpecifications, physicalRuntime, layout);
        appendLayoutDependencies(out, board, layout, pristineConductors, currentConductors);

        if (out.length() > MAX_CANONICAL_LENGTH)
            throw new IllegalStateException("Generation dependency context exceeds its bound");
        return new GenerationDependencyContext(out.toString());
    }

    /** Returns the complete internal canonical value, including hidden inputs. */
    String canonical() {
        return canonical;
    }

    private static void appendScenarioDependencies(StringBuilder out,
            GeneratedBoardInstance owner, GeneratedChallengeController controller) {
        // This epoch covers the current library's predicate, selection and
        // presentation code. Change it when those interpretation rules change;
        // runtime object identities cannot identify reproducible fresh recipes.
        appendField(out, "epoch.scenario-execution", "generated-scenario-execution-v1");
        require(controller != null && controller.getScenario() != null,
            "Generation dependencies require the prepared scenario");
        Vector<String> records = new Vector<String>();
        Vector<String> ids = new Vector<String>();
        for (GeneratedScenario<GeneratedObservedBehavior> scenario :
                owner.getChallengeDefinition().getScenarioCatalog().getCandidates()) {
            require(!ids.contains(scenario.getScenarioId()), "Duplicate scenario identity");
            ids.add(scenario.getScenarioId());
            records.add(scenarioIdentity(scenario));
        }
        appendStringList(out, "scenario.catalog", records, true);
        appendField(out, "scenario.selected", scenarioIdentity(controller.getScenario()));
    }

    static String scenarioIdentity(GeneratedScenario<GeneratedObservedBehavior> scenario) {
        require(scenario != null && scenario.getObservedBehavior() != null,
            "Incomplete scenario dependency");
        StringBuilder out = new StringBuilder();
        appendField(out, "scenario.id", scenario.getScenarioId());
        appendField(out, "complaint.id", scenario.getComplaintId());
        appendField(out, "complaint.text", scenario.getComplaintText());
        appendField(out, "observed.behavior", scenario.getObservedBehavior().name());
        appendField(out, "presentation", Boolean.toString(scenario.hasPresentation()));
        return out.toString();
    }

    @Override
    public boolean equals(Object other) {
        return this == other || (other instanceof GenerationDependencyContext &&
            canonical.equals(((GenerationDependencyContext) other).canonical));
    }

    @Override
    public int hashCode() {
        return hash;
    }

    private static void appendBoard(StringBuilder out, TroubleshootBoard board,
            BoardPhysicalSpecifications physicalSpecifications) {
        Vector<String> componentIds = sorted(board.getComponentIds());
        Vector<String> padIds = sorted(board.getPadIds());
        Vector<String> netIds = sorted(board.getNetIds());
        Vector<String> powerInputIds = sorted(board.getPowerInputIds());

        appendStringList(out, "board.components", componentIds, false);
        for (String componentId : componentIds) {
            BoardComponent component = board.getComponent(componentId);
            require(component != null, "Missing board component: " + componentId);
            appendField(out, "component." + componentId + ".type", component.getType());
            appendField(out, "component." + componentId + ".display", component.getDisplayName());
            appendStringList(out, "component." + componentId + ".pads",
                sorted(component.getPadIds()), false);
        }

        appendStringList(out, "board.pads", padIds, false);
        for (String padId : padIds) {
            BoardPad pad = board.getPad(padId);
            require(pad != null, "Missing board pad: " + padId);
            appendField(out, "pad." + padId + ".component", pad.getComponentId());
            appendField(out, "pad." + padId + ".terminal", pad.getTerminalId());
            appendField(out, "pad." + padId + ".net", pad.getNetId());
        }

        appendStringList(out, "board.nets", netIds, false);
        for (String netId : netIds) {
            BoardNet net = board.getNet(netId);
            require(net != null, "Missing board net: " + netId);
            appendStringList(out, "net." + netId + ".pads", sorted(net.getPadIds()), false);
        }

        appendStringList(out, "board.power-inputs", powerInputIds, false);
        for (String powerInputId : powerInputIds) {
            ExternalBoardPowerInput input = board.getPowerInput(powerInputId);
            require(input != null, "Missing board power input: " + powerInputId);
            appendField(out, "power." + powerInputId + ".positive-pad", input.getPositivePadId());
            appendField(out, "power." + powerInputId + ".return-pad", input.getReturnPadId());
            appendField(out, "power." + powerInputId + ".positive-net", input.getPositiveNetId());
            appendField(out, "power." + powerInputId + ".return-net", input.getReturnNetId());
            PowerInputNameplate nameplate =
                physicalSpecifications.getPowerInputNameplate(powerInputId);
            require(nameplate != null, "Missing power input nameplate: " + powerInputId);
            appendField(out, "power." + powerInputId + ".nominal-voltage",
                doubleBits(nameplate.getNominalVoltage()));
            appendField(out, "power." + powerInputId + ".display", nameplate.getDisplayLabel());
        }
    }

    private static void appendBoardSimulationBindings(StringBuilder out,
            TroubleshootBoard board) {
        Vector<String> padIds = sorted(board.getPadIds());
        appendStringList(out, "board.simulation-pads", padIds, false);
        for (String padId : padIds) {
            CircuitMeasurementEndpoint endpoint = board.getSimulationBindings().getEndpoint(padId);
            require(endpoint != null, "Missing board simulation endpoint: " + padId);
            appendField(out, "board.pad." + padId + ".simulation-endpoint",
                endpointFingerprint(endpoint));
        }
    }

    private static void appendSimulationSettings(StringBuilder out, CirSim sim) {
        appendField(out, "sim.time-step", doubleBits(sim.timeStep));
        appendField(out, "sim.max-time-step", doubleBits(sim.maxTimeStep));
        appendField(out, "policy.residual-voltage-threshold",
            doubleBits(ActiveMeasurementReadiness.RESIDUAL_VOLTAGE_THRESHOLD_VOLTS));
        appendField(out, "sim.min-time-step", doubleBits(sim.minTimeStep));
        appendField(out, "sim.adjust-time-step", Boolean.toString(sim.adjustTimeStep));
        appendField(out, "sim.dc-analysis", Boolean.toString(sim.dcAnalysisFlag));
        appendField(out, "sim.circuit-nonlinear", Boolean.toString(sim.circuitNonLinear));
        appendField(out, "sim.voltage-source-count", Integer.toString(sim.voltageSourceCount));
        appendField(out, "sim.matrix-size", Integer.toString(sim.circuitMatrixSize));
        appendField(out, "sim.matrix-full-size", Integer.toString(sim.circuitMatrixFullSize));
        appendField(out, "sim.voltage-range", doubleBits(CircuitElm.voltageRange));
        appendField(out, "sim.show-resistance", Boolean.toString(sim.showResistanceInVoltageSources));
        appendCheckbox(out, "sim.option.dots", sim.dotsCheckItem);
        appendCheckbox(out, "sim.option.volts", sim.voltsCheckItem);
        appendCheckbox(out, "sim.option.power", sim.powerCheckItem);
        appendCheckbox(out, "sim.option.small-grid", sim.smallGridCheckItem);
        appendCheckbox(out, "sim.option.cross-hair", sim.crossHairCheckItem);
        appendCheckbox(out, "sim.option.show-values", sim.showValuesCheckItem);
        appendCheckbox(out, "sim.option.conductance", sim.conductanceCheckItem);
        appendCheckbox(out, "sim.option.euro-resistor", sim.euroResistorCheckItem);
        appendCheckbox(out, "sim.option.euro-gates", sim.euroGatesCheckItem);
        appendCheckbox(out, "sim.option.printable", sim.printableCheckItem);
        appendCheckbox(out, "sim.option.convention", sim.conventionCheckItem);
        appendCheckbox(out, "sim.option.no-edit", sim.noEditCheckItem);
        appendField(out, "sim.board-power-state",
            sim.boardPowerController == null || sim.boardPowerController.getState() == null ?
                null : sim.boardPowerController.getState().name());
        if (sim.boardPowerController != null)
            appendSourceStates(out, "sim.board-power-sources", sim.boardPowerController.getSourceStates());
    }

    private static void appendCheckbox(StringBuilder out, String key,
            CheckboxMenuItem value) {
        appendField(out, key, value == null ? null : Boolean.toString(value.getState()));
    }

    private static void appendSourceStates(StringBuilder out, String key,
            Map<String, PowerOperatingAssessment.SourceState> states) {
        Vector<String> ids = new Vector<String>();
        if (states != null)
            ids.addAll(states.keySet());
        Collections.sort(ids);
        appendStringList(out, key + ".ids", ids, false);
        for (String id : ids) {
            PowerOperatingAssessment.SourceState state = states.get(id);
            appendField(out, key + "." + id + ".connection",
                state == null || state.getConnection() == null ? null : state.getConnection().name());
            appendField(out, key + "." + id + ".drive",
                state == null || state.getDriveState() == null ? null : state.getDriveState().name());
        }
    }

    private static void appendComponentBindings(StringBuilder out, TroubleshootBoard board,
            GeneratedComponentBindings bindings, GeneratedComponentConnectionBindings connections) {
        Vector<String> componentIds = sorted(board.getComponentIds());
        for (String componentId : componentIds) {
            if (bindings.hasComponentBinding(componentId)) {
                appendField(out, "component." + componentId + ".simulation-owner", "component");
                appendElementBatch(out, "component." + componentId + ".elements",
                    bindings.getElements(componentId));
                appendElementBatch(out, "component." + componentId + ".auxiliary",
                    bindings.getAuxiliaryElements(componentId));
                continue;
            }

            // Connector and other board-only parts do not own a primary
            // CircuitJS element.  Require their actual board/power/connection
            // seam rather than treating an absent component binding as empty.
            String owner = nonComponentBindingOwner(board, componentId, connections);
            appendField(out, "component." + componentId + ".simulation-owner", owner);
        }
    }

    private static String nonComponentBindingOwner(TroubleshootBoard board,
            String componentId, GeneratedComponentConnectionBindings connections) {
        if (hasPowerInputComponent(board, componentId))
            return "external-power";
        if (!connections.getForComponentOrEmpty(componentId).isEmpty())
            return "generated-connection";
        BoardComponent component = board.getComponent(componentId);
        require(component != null, "Missing non-component board component: " + componentId);
        Vector<String> padIds = component.getPadIds();
        require(!padIds.isEmpty(), "Unmapped board-only component has no pads: " + componentId);
        for (String padId : padIds)
            require(board.getSimulationBindings().getEndpoint(padId) != null,
                "Unmapped board-only component has no simulation endpoint: " + componentId);
        return "board-simulation";
    }

    private static boolean hasPowerInputComponent(TroubleshootBoard board, String componentId) {
        for (String powerId : board.getPowerInputIds()) {
            ExternalBoardPowerInput input = board.getPowerInput(powerId);
            require(input != null, "Missing external power declaration: " + powerId);
            BoardPad positive = board.getPad(input.getPositivePadId());
            BoardPad returned = board.getPad(input.getReturnPadId());
            require(positive != null && returned != null,
                "External power declaration has no board pads: " + powerId);
            if (componentId.equals(positive.getComponentId()) ||
                    componentId.equals(returned.getComponentId()))
                return true;
        }
        return false;
    }

    private static void appendPowerBindings(StringBuilder out, TroubleshootBoard board,
            GeneratedExternalPowerBindings bindings, PowerDomainContract powerContract) {
        Vector<String> powerIds = sorted(board.getPowerInputIds());
        require(!powerIds.isEmpty(), "Generated board has no external power input");
        require(bindings.hasControlsForAllInputs(),
            "Generated board power inputs are not fully controllable");
        if (powerContract == null) {
            // Current leaf generators intentionally do not install the typed
            // power-domain assessment capability.  Their proof path consumes
            // BoardPowerController and the concrete external bindings only;
            // the board declaration/nameplate and binding records above are
            // the complete available static power truth for that path.
            appendField(out, "power.contract.version", "NONE");
            appendField(out, "power.contract.design", POWER_LEAF_CONTRACT_POLICY);
            appendField(out, "power.contract.canonical", "NONE");
        } else {
            appendField(out, "power.contract.version", Integer.toString(PowerDomainContract.VERSION));
            appendField(out, "power.contract.design", powerContract.getDesignId());
            appendField(out, "power.contract.canonical", powerContract.toCanonical());
        }
        appendField(out, "power.contract.dynamic-input-policy", POWER_DYNAMIC_INPUT_POLICY);
        appendField(out, "power.contract.unavailable-consumed-inputs",
            POWER_UNAVAILABLE_CONSUMED_INPUTS);
        appendField(out, "power.control-signature", stablePowerControlSignature(board, bindings));
        appendSourceStates(out, "power.source-states", bindings.getSourceStates());
        for (String powerId : powerIds) {
            ExternalPowerSimulationBinding binding = bindings.getBinding(powerId);
            require(binding != null, "Missing external power binding: " + powerId);
            appendField(out, "power." + powerId + ".connected",
                Boolean.toString(binding.isConnected()));
            appendElementBatch(out, "power." + powerId + ".backing",
                binding.getBackingElements());
        }
    }

    private static String stablePowerControlSignature(TroubleshootBoard board,
            GeneratedExternalPowerBindings bindings) {
        StringBuilder out = new StringBuilder();
        for (String powerId : sorted(board.getPowerInputIds())) {
            ExternalPowerSimulationBinding binding = bindings.getBinding(powerId);
            require(binding != null, "Missing external power binding: " + powerId);
            appendLocal(out, powerId);
            appendLocal(out, !binding.hasControl() ? "UNKNOWN" :
                binding.isConnected() ? "CONNECTED" : "ISOLATED");
        }
        return out.toString();
    }

    /**
     * Resolves the installed typed owner seam when the current runtime path
     * actually installs one.  Leaf generators currently use the board-power
     * controller and concrete source bindings without a typed reference/rail/
     * storage assessment capability, so a missing capability is represented
     * explicitly as NONE rather than as an empty or synthetic contract.
     */
    private static PowerDomainContract capturePowerContract(PhysicalBoardRuntime runtime) {
        PhysicalBoardRuntimeCapability capability = runtime.getCapability(
            PowerDomainRuntimeCapability.CAPABILITY_ID);
        if (capability == null)
            return null;
        if (!(capability instanceof PowerDomainRuntimeCapability))
            throw new IllegalStateException("Power-domain capability has no typed contract seam");
        PowerDomainContract contract = ((PowerDomainRuntimeCapability) capability).getContract();
        require(contract != null, "Power-domain capability has no contract");
        return contract;
    }

    private static void appendConnectionBindings(StringBuilder out,
            GeneratedComponentConnectionBindings bindings) {
        Vector<GeneratedComponentConnectionBinding> values = bindings.getAll();
        Vector<String> records = new Vector<String>();
        for (GeneratedComponentConnectionBinding value : values) {
            require(value != null, "Missing generated connection binding");
            String record = value.getComponentId() + "|" + value.getPadId() + "|" +
                endpointFingerprint(value.getBoardEndpoint()) + "|" +
                endpointFingerprint(value.getComponentEndpoint()) + "|" +
                elementDump(value.getConnectionElement());
            records.add(record);
        }
        appendStringList(out, "connections", records, true);
    }

    private static void appendDiagnosticDependencies(StringBuilder out,
            GeneratedBoardInstance owner, GeneratedDiagnosticProvider provider,
            GeneratedDiagnosticPlan plan, GeneratedDiagnosticProgram program,
            GeneratedDiagnosticSolvabilityContract solvability,
            Vector<GeneratedFaultCandidate> candidates,
            GeneratedFaultBinding selectedBinding) {
        appendField(out, "diagnostic.provider", provider.getProviderId());
        appendPlan(out, "diagnostic.plan", plan);
        appendField(out, "diagnostic.program", program.canonical());
        appendField(out, "diagnostic.contract.route", solvability.getRouteId());
        appendField(out, "diagnostic.contract.family", solvability.getFamilyId());
        appendField(out, "diagnostic.contract.topology", solvability.getTopologyVariantId());
        appendField(out, "diagnostic.contract.seed", Long.toString(solvability.getSeed()));
        appendField(out, "diagnostic.contract.admitted-candidates",
            Integer.toString(solvability.getAdmittedCandidateCount()));
        appendField(out, "diagnostic.contract.admitted-owners",
            Integer.toString(solvability.getAdmittedPhysicalOwnerCount()));
        appendField(out, "diagnostic.contract.owner-diversity",
            solvability.getOwnerDiversity() == null ? null : solvability.getOwnerDiversity().name());
        appendStringList(out, "diagnostic.contract.hypotheses",
            sorted(solvability.getHypothesisKeys()), false);
        Vector<GeneratedDiagnosticPlan> plans = solvability.getPlans();
        Vector<String> planIdentities = new Vector<String>();
        for (GeneratedDiagnosticPlan value : plans)
            planIdentities.add(GeneratedDiagnosticProgram.describePlan(value));
        appendStringList(out, "diagnostic.contract.plans", planIdentities, true);

        Vector<String> candidateRecords = new Vector<String>();
        for (GeneratedFaultCandidate candidate : candidates)
            candidateRecords.add(candidateFingerprint(candidate));
        appendStringList(out, "diagnostic.hypothesis-population", candidateRecords, true);
        appendField(out, "diagnostic.selected-fault", faultFingerprint(selectedBinding.getFault()));
        appendField(out, "diagnostic.selected-fault-applied",
            Boolean.toString(selectedBinding.isApplied()));
        appendField(out, "diagnostic.selected-fault-effect",
            selectedBinding.getEffect() == null ? null : "present");
        appendField(out, "diagnostic.selected-fault-private-elements",
            elementBatchIdentity(selectedBinding.getPrivateSimulationElements()));

        GeneratedChallengeDefinition challenge = owner.getChallengeDefinition();
        if (challenge == null) {
            appendField(out, "diagnostic.challenge", null);
        } else {
            appendField(out, "diagnostic.challenge.id", challenge.getId());
            appendField(out, "diagnostic.challenge.family", challenge.getCircuitFamilyId());
            appendField(out, "diagnostic.challenge.topology", challenge.getTopologyVariantId());
            appendField(out, "diagnostic.challenge.seed", Long.toString(challenge.getSelectionSeed()));
            appendField(out, "diagnostic.challenge.completion", challenge.getCompletionText());
            appendField(out, "diagnostic.challenge.fault", faultFingerprint(challenge.getFault()));
        }

        GeneratedFaultServiceability serviceability = selectedBinding.getServiceability();
        appendServiceability(out, "diagnostic.selected-serviceability", serviceability);
        appendOperationCatalog(out, owner.getOperationCatalog());
        GeneratedCustomerRetestProfile retest = owner.getCustomerRetestProfile();
        if (retest == null)
            appendField(out, "diagnostic.retest", null);
        else {
            appendField(out, "diagnostic.retest.id", retest.getStableId());
            appendField(out, "diagnostic.retest.instruction", retest.getPlayerInstruction());
            appendField(out, "diagnostic.retest.power", retest.getRequiredPowerTransition());
            appendField(out, "diagnostic.retest.input", retest.getRequiredInputTransition());
            appendField(out, "diagnostic.retest.output", retest.getObservableOutput());
            appendField(out, "diagnostic.retest.timing", retest.getTimingAndRepetition());
            appendField(out, "diagnostic.retest.unaffected", retest.getUnaffectedFunctions());
        }
        GeneratedTemporalBehavior temporal = owner.getTemporalBehavior();
        appendField(out, "diagnostic.temporal-observed",
            temporal == null || temporal.getObservedBehavior() == null ? null :
                temporal.getObservedBehavior().name());
    }

    private static void appendTemporalDependency(StringBuilder out,
            GeneratedBoardInstance owner) {
        GeneratedTemporalBehavior temporal = owner.getTemporalBehavior();
        if (temporal == null) {
            appendField(out, "temporal.contract", "NONE");
            return;
        }
        GeneratedTemporalDependency dependency = temporal.getDependency(owner);
        require(dependency != null, "Temporal behavior returned no dependency contract");
        appendField(out, "temporal.contract.id", dependency.getBehaviorId());
        appendField(out, "temporal.contract.version",
            Integer.toString(dependency.getBehaviorVersion()));
        appendField(out, "temporal.contract.initial-state",
            dependency.getInitialStateContract());
        appendField(out, "temporal.contract.canonical", dependency.canonical());
    }

    private static void appendPlan(StringBuilder out, String prefix,
            GeneratedDiagnosticPlan plan) {
        appendField(out, prefix + ".template", plan.getTemplateId());
        appendField(out, prefix + ".reference", plan.getReferenceTargetId());
        appendStringList(out, prefix + ".probes", plan.getProbeTargetIds(), false);
        appendStringList(out, prefix + ".meters", plan.getMeterModeIds(), false);
        appendStringList(out, prefix + ".power-transitions", plan.getInputPowerTransitions(), false);
        appendStringList(out, prefix + ".isolation", plan.getIsolationActionIds(), false);
        appendStringList(out, prefix + ".repair", plan.getRepairActionIds(), false);
        appendStringList(out, prefix + ".workflow", plan.getWorkflowActionIds(), false);
        appendStringList(out, prefix + ".player-operations", plan.getPlayerOperationIds(), false);
        appendStringList(out, prefix + ".temporal", plan.getTemporalWaitSampleIds(), false);
        appendStringList(out, prefix + ".rails", plan.getRailDomainIds(), false);
        appendField(out, prefix + ".depth", Integer.toString(plan.getDepth()));
        appendField(out, prefix + ".parallel-path", Boolean.toString(plan.hasParallelPathAmbiguity()));
        appendField(out, prefix + ".unaffected-retest",
            Boolean.toString(plan.hasUnaffectedFunctionRetestObservation()));
        appendField(out, prefix + ".equivalent-repair", plan.getEquivalentRepairClass());
    }

    private static void appendServiceability(StringBuilder out, String prefix,
            GeneratedFaultServiceability serviceability) {
        if (serviceability == null) {
            appendField(out, prefix, null);
            return;
        }
        GeneratedFaultLocus locus = serviceability.getLocus();
        appendField(out, prefix + ".locus.type", locus == null ? null : locus.getType().name());
        appendField(out, prefix + ".locus.component", locus == null ? null : locus.getComponentId());
        appendField(out, prefix + ".locus.terminal", locus == null ? null : locus.getTerminalId());
        appendField(out, prefix + ".locus.path", locus == null ? null : locus.getPathId());
        appendStringList(out, prefix + ".observe", serviceability.getObservationActionIds(), false);
        appendStringList(out, prefix + ".isolate", serviceability.getIsolationActionIds(), false);
        appendStringList(out, prefix + ".repair", serviceability.getRepairActionIds(), false);
        appendStringList(out, prefix + ".workflow", serviceability.getWorkflowActionIds(), false);
        appendField(out, prefix + ".retest", serviceability.getCustomerRetestOperationId());
        appendField(out, prefix + ".admissible", Boolean.toString(serviceability.isAdmissible()));
    }

    private static void appendOperationCatalog(StringBuilder out,
            GeneratedBoardOperationCatalog catalog) {
        require(catalog != null, "Generated board has no operation catalog");
        Vector<GeneratedBoardOperation> operations = catalog.getAll();
        Vector<String> records = new Vector<String>();
        for (GeneratedBoardOperation operation : operations) {
            require(operation != null, "Missing generated board operation");
            records.add(operation.getStableId() + "|" + operation.getPlayerLabel());
        }
        appendStringList(out, "diagnostic.operation-catalog", records, true);
    }

    private static String candidateFingerprint(GeneratedFaultCandidate candidate) {
        require(candidate != null, "Missing generated fault candidate");
        GeneratedFault fault = candidate.getFault();
        GeneratedFaultServiceability serviceability = candidate.getServiceability();
        StringBuilder value = new StringBuilder();
        appendLocal(value, candidate.getHypothesisKey());
        appendLocal(value, faultFingerprint(fault));
        appendLocal(value, Boolean.toString(candidate.isCompatible()));
        appendLocal(value, Boolean.toString(candidate.isServiceable()));
        appendLocal(value, Boolean.toString(candidate.isAdmitted()));
        appendLocal(value, serviceability == null ? null : serviceability.getCustomerRetestOperationId());
        appendLocal(value, serviceability == null ? null :
            Boolean.toString(serviceability.isAdmissible()));
        return value.toString();
    }

    private static String faultFingerprint(GeneratedFault fault) {
        require(fault != null, "Missing generated fault");
        StringBuilder value = new StringBuilder();
        appendLocal(value, fault.getId());
        appendLocal(value, fault.getType().name());
        appendLocal(value, fault.getTargetComponentId());
        appendLocal(value, fault.getCircuitFamilyId());
        appendLocal(value, Long.toString(fault.getSelectionSeed()));
        appendLocal(value, doubleBits(fault.getHealthyValue()));
        appendLocal(value, doubleBits(fault.getEffectiveValue()));
        appendLocal(value, fault.getHypothesisKey());
        return value.toString();
    }

    private static void appendPhysicalDependencies(StringBuilder out, TroubleshootBoard board,
            BoardPhysicalSpecifications physicalSpecifications, PhysicalBoardRuntime runtime,
            PcbBoardLayout layout) {
        Vector<String> componentIds = sorted(physicalSpecifications.getPhysicalComponentIds());
        for (String boardComponentId : board.getComponentIds())
            require(componentIds.contains(boardComponentId),
                "Missing physical definition: " + boardComponentId);
        appendStringList(out, "physical.components", componentIds, false);
        for (String componentId : componentIds) {
            PhysicalSpecification specification = physicalSpecifications.getSpecification(componentId);
            PhysicalNameplate nameplate = physicalSpecifications.getNameplate(componentId);
            PhysicalPackage physicalPackage = physicalSpecifications.getPackage(componentId);
            require(specification != null, "Missing physical specification: " + componentId);
            require(nameplate != null, "Missing physical nameplate: " + componentId);
            require(physicalPackage != null, "Missing physical package: " + componentId);
            appendField(out, "physical." + componentId + ".specification",
                specification.getSpecificationId());
            appendRatings(out, "physical." + componentId + ".ratings", specification.getRatings());
            appendNameplate(out, "physical." + componentId + ".nameplate", nameplate);
            appendPackage(out, "physical." + componentId + ".package", physicalPackage);
            PcbComponentPlacement placement = layout.getComponent(componentId);
            require(placement != null, "Missing physical PCB placement: " + componentId);
            appendField(out, "physical." + componentId + ".placement",
                placement.geometryFingerprint());
            appendField(out, "physical." + componentId + ".pose",
                placement.getPose().fingerprint());
        }
        appendRuntime(out, board, runtime);
    }

    private static void appendRatings(StringBuilder out, String key,
            Vector<PhysicalRating> ratings) {
        require(ratings != null, "Physical specification has no ratings vector");
        Vector<PhysicalRating> ordered = new Vector<PhysicalRating>(ratings);
        Collections.sort(ordered, new Comparator<PhysicalRating>() {
            public int compare(PhysicalRating first, PhysicalRating second) {
                return first.getId().compareTo(second.getId());
            }
        });
        Vector<String> values = new Vector<String>();
        for (PhysicalRating rating : ordered) {
            require(rating != null, "Missing physical rating");
            StringBuilder value = new StringBuilder();
            appendLocal(value, rating.getId());
            if (rating instanceof PowerRating)
                appendLocal(value, doubleBits(((PowerRating) rating).getWatts()));
            else if (rating instanceof VoltageRating)
                appendLocal(value, doubleBits(((VoltageRating) rating).getVolts()));
            else
                appendLocal(value, "untyped-rating-value-unavailable");
            values.add(value.toString());
        }
        appendStringList(out, key, values, false);
    }

    private static void appendNameplate(StringBuilder out, String key,
            PhysicalNameplate nameplate) {
        appendField(out, key + ".id", nameplate.getId());
        appendField(out, key + ".display", nameplate.getDisplayName());
        appendField(out, key + ".detail-label", nameplate.hasWorkbenchDetail() ?
            nameplate.getWorkbenchDetailLabel() : null);
        appendField(out, key + ".detail-value", nameplate.hasWorkbenchDetail() ?
            nameplate.getWorkbenchDetailValue() : null);
    }

    private static void appendPackage(StringBuilder out, String key,
            PhysicalPackage physicalPackage) {
        appendField(out, key + ".id", physicalPackage.getId());
        appendField(out, key + ".connector", Boolean.toString(physicalPackage.isConnector()));
        appendField(out, key + ".developer-generic",
            Boolean.toString(physicalPackage.isDeveloperGeneric()));
        appendField(out, key + ".geometry-version",
            Integer.toString(physicalPackage.getGeometryContractVersionValue()));
        appendField(out, key + ".default-variant",
            physicalPackage.getDefaultLooseGeometryVariantKey());
        appendField(out, key + ".variant-selection",
            physicalPackage.getGeometryVariantSelection().name());
        appendStringList(out, key + ".terminals", physicalPackage.getTerminalIds(), false);
        Vector<String> rotations = new Vector<String>();
        for (PcbRotation rotation : physicalPackage.getAllowedRotations())
            rotations.add(rotation.name());
        appendStringList(out, key + ".allowed-rotations", rotations, true);
        Vector<String> sides = new Vector<String>();
        for (PcbBoardSide side : physicalPackage.getAllowedMountingSides())
            sides.add(side.name());
        appendStringList(out, key + ".allowed-sides", sides, true);
        Vector<String> connections = new Vector<String>();
        Vector<String> terminals = physicalPackage.getTerminalIds();
        for (int first = 0; first < terminals.size(); first++)
            for (int second = first + 1; second < terminals.size(); second++)
                if (physicalPackage.isInternallyConnected(terminals.get(first), terminals.get(second)))
                    connections.add(terminals.get(first) + "=" + terminals.get(second));
        appendStringList(out, key + ".internal-connections", connections, true);
        PhysicalPackageGeometry geometry = physicalPackage.getGeometry();
        require(geometry != null, "Physical package has no canonical geometry: " + physicalPackage.getId());
        appendField(out, key + ".geometry-size", geometry.getWidth() + "x" + geometry.getHeight());
        appendField(out, key + ".geometry-version-value",
            Integer.toString(geometry.getGeometryContractVersionValue()));
        appendField(out, key + ".geometry-developer-generic",
            Boolean.toString(geometry.isDeveloperGeneric()));
        Vector<String> variants = new Vector<String>();
        for (PhysicalPackage.GeometryVariant variant : physicalPackage.getGeometryVariants()) {
            require(variant != null, "Missing physical package geometry variant");
            PhysicalPackageGeometry variantGeometry = variant.getGeometry();
            require(variantGeometry != null, "Missing package variant geometry");
            variants.add(variant.getKey() + "|" + variant.getTransformKey() + "|" +
                variantGeometry.getWidth() + "x" + variantGeometry.getHeight() + "|" +
                variantGeometry.getGeometryContractVersionValue());
        }
        appendStringList(out, key + ".variants", variants, true);
    }

    private static void appendRuntime(StringBuilder out, TroubleshootBoard board,
            PhysicalBoardRuntime runtime) {
        Vector<String> slotIds = sorted(runtime.getSlotOrder());
        appendStringList(out, "physical.runtime.slots", slotIds, false);
        for (String componentId : slotIds) {
            PhysicalBoardSlot slot = runtime.getSlot(componentId);
            require(slot != null, "Missing physical board slot: " + componentId);
            appendField(out, "physical.slot." + componentId + ".id", slot.getId());
            appendStringList(out, "physical.slot." + componentId + ".pads",
                sorted(slot.getPadIds()), false);
            appendStringList(out, "physical.slot." + componentId + ".terminals",
                sorted(slot.getTerminalIds()), false);
            appendStringList(out, "physical.slot." + componentId + ".nets",
                sorted(slot.getNetIds()), false);
            appendField(out, "physical.slot." + componentId + ".geometry",
                slot.getGeometryRealization() == null ? null :
                    slot.getGeometryRealization().fingerprint());
            appendField(out, "physical.slot." + componentId + ".installed",
                slot.getInstalledPart() == null ? null : slot.getInstalledPart().getId());
        }

        Vector<String> inventoryIds = sorted(runtime.getInventoryIds());
        appendStringList(out, "physical.runtime.inventories", inventoryIds, false);
        for (String inventoryId : inventoryIds) {
            appendStringList(out, "physical.inventory." + inventoryId + ".parts",
                sorted(runtime.getInventoryPartIds(inventoryId)), false);
            Integer nextSerial = runtime.getNextPartSerial(inventoryId);
            appendField(out, "physical.inventory." + inventoryId + ".next-serial",
                nextSerial == null ? null : nextSerial.toString());
        }

        Vector<String> partIds = sorted(runtime.getPartIds());
        appendStringList(out, "physical.runtime.parts", partIds, false);
        for (String partId : partIds) {
            PhysicalPart<?> part = runtime.getPart(partId);
            require(part != null, "Missing physical part: " + partId);
            PhysicalSpecification partSpecification = part.getSpecification();
            appendField(out, "physical.part." + partId + ".specification",
                partSpecification == null ? null : partSpecification.getSpecificationId());
            if (partSpecification == null)
                appendField(out, "physical.part." + partId + ".ratings", null);
            else
                appendRatings(out, "physical.part." + partId + ".ratings",
                    partSpecification.getRatings());
            appendField(out, "physical.part." + partId + ".orientation",
                part.getOrientation() == null ? null : part.getOrientation().name());
            appendField(out, "physical.part." + partId + ".package",
                part.getPackage() == null ? null : part.getPackage().getId());
            appendField(out, "physical.part." + partId + ".geometry",
                part.getGeometryRealization() == null ? null :
                    part.getGeometryRealization().fingerprint());
            appendField(out, "physical.part." + partId + ".slot",
                part.getBoardSlot() == null ? null : part.getBoardSlot().getId());
            appendField(out, "physical.part." + partId + ".inventory",
                runtime.getInventoryIdForPart(partId));
            appendField(out, "physical.part." + partId + ".installed",
                Boolean.toString(part.isInstalled()));
            appendField(out, "physical.part." + partId + ".original",
                Boolean.toString(part.isOriginal()));
            appendField(out, "physical.part." + partId + ".faulted",
                Boolean.toString(part.isFaulted()));
            PhysicalPartProvenance provenance = part.getProvenance();
            appendField(out, "physical.part." + partId + ".provenance-kind",
                provenance == null ? null : provenance.getKind());
            appendField(out, "physical.part." + partId + ".provenance-source",
                provenance == null ? null : provenance.getSourceId());
            PhysicalFailureState failure = part.getFailureState();
            appendField(out, "physical.part." + partId + ".failure-kind",
                failure == null ? null : failure.getKind());
            appendField(out, "physical.part." + partId + ".failure-active",
                failure == null ? null : Boolean.toString(failure.isFailed()));
            appendPartCapabilities(out, "physical.part." + partId + ".capabilities",
                part.getCapabilities());
            appendPartCapabilities(out, "physical.part." + partId + ".intrinsic-capabilities",
                part.getIntrinsicCapabilities());
            PhysicalNameplate partNameplate = part.getPlayerVisibleNameplate();
            if (partNameplate != null)
                appendNameplate(out, "physical.part." + partId + ".nameplate", partNameplate);
            else
                appendField(out, "physical.part." + partId + ".nameplate", null);
            Vector<String> terminals = new Vector<String>();
            for (PhysicalPartTerminal terminal : part.getTerminals()) {
                require(terminal != null, "Missing physical part terminal: " + partId);
                terminals.add(terminal.getId() + "|" + terminal.getTerminalName());
            }
            appendStringList(out, "physical.part." + partId + ".terminals", terminals, true);
        }

        Vector<String> capabilityIds = sorted(runtime.getCapabilityOrder());
        appendStringList(out, "physical.runtime.capabilities", capabilityIds, false);
        for (String capabilityId : capabilityIds) {
            PhysicalBoardRuntimeCapability capability = runtime.getCapability(capabilityId);
            require(capability != null, "Missing physical runtime capability: " + capabilityId);
            appendField(out, "physical.capability." + capabilityId, capability.getCapabilityId());
        }

        Vector<WorkbenchPartsProvider> providers = runtime.getWorkbenchPartsProviders();
        Vector<String> providerRecords = new Vector<String>();
        for (WorkbenchPartsProvider provider : providers) {
            require(provider != null, "Missing workbench parts provider");
            providerRecords.add(provider.getComponentId() + "|" + provider.getCatalogTitle() + "|" +
                provider.getInstallNewLabel() + "|" + provider.showOccupiedMessageWhenPowered());
        }
        appendStringList(out, "physical.runtime.workbench-providers", providerRecords, true);
        appendField(out, "physical.runtime.board", runtime.getBoard() == board ? board.getId() : null);
    }

    private static void appendPartCapabilities(StringBuilder out, String key,
            Vector<PhysicalPartCapability> capabilities) {
        require(capabilities != null, "Physical part has no capability vector");
        Vector<String> records = new Vector<String>();
        for (PhysicalPartCapability capability : capabilities) {
            require(capability != null, "Missing physical part capability");
            WorkbenchCapabilityMetadata metadata = capability.getMetadata();
            require(metadata != null, "Physical part capability has no metadata");
            records.add(metadata.getId() + "|" + metadata.getDisplayName() + "|" +
                metadata.getOperationId());
        }
        appendStringList(out, key, records, true);
    }

    private static void appendLayoutDependencies(StringBuilder out, TroubleshootBoard board,
            PcbBoardLayout layout, PcbConductorGraph pristine,
            PcbConductorGraph.Snapshot current) {
        appendField(out, "layout.algorithm-version",
            Integer.toString(layout.getLayoutAlgorithmVersion()));
        appendField(out, "layout.size", layout.getWidth() + "x" + layout.getHeight());
        appendField(out, "layout.geometry", layout.geometryFingerprint());
        appendField(out, "layout.components", layout.componentGeometryFingerprint());
        appendField(out, "layout.traces", layout.traceGeometryFingerprint());
        appendField(out, "layout.silkscreen", layout.silkscreenGeometryFingerprint());
        Vector<String> padRecords = new Vector<String>();
        for (PcbPadPlacement pad : layout.getPads()) {
            require(pad != null, "Missing PCB pad placement");
            padRecords.add(pad.geometryFingerprint());
        }
        appendStringList(out, "layout.access-pads", padRecords, true);
        appendField(out, "conductor.pristine", pristine.toCanonical());
        appendField(out, "conductor.current", PcbConductorState.encode(current));
        appendField(out, "layout.owner-board", board.getId());
    }

    private static void appendElementBatch(StringBuilder out, String key,
            Vector<CircuitElm> elements) {
        appendStringList(out, key, captureElementRecords(elements), true);
    }

    private static String elementBatchIdentity(Vector<CircuitElm> elements) {
        Vector<String> records = captureElementRecords(elements);
        StringBuilder out = new StringBuilder();
        for (String record : records)
            appendLocal(out, record);
        return out.toString();
    }

    private static Vector<String> captureElementRecords(Vector<CircuitElm> elements) {
        require(elements != null, "Missing CircuitJS element vector");
        resetModelDumpFlags();
        try {
            Vector<String> records = new Vector<String>();
            for (CircuitElm element : elements) {
                require(element != null, "Missing CircuitJS element");
                String dump = stableElementDump(element);
                records.add(frameValue(element.dumpModel()) + frameValue(dump));
            }
            Collections.sort(records);
            return records;
        } finally {
            resetModelDumpFlags();
        }
    }

    private static String endpointFingerprint(CircuitMeasurementEndpoint endpoint) {
        require(endpoint != null, "Missing measurement endpoint");
        if (!(endpoint instanceof CircuitPostMeasurementEndpoint))
            throw new IllegalStateException("Unsupported measurement endpoint dependency");
        CircuitPostMeasurementEndpoint post = (CircuitPostMeasurementEndpoint) endpoint;
        require(post.getElement() != null, "Measurement endpoint has no element");
        return "post|" + frameValue(stableElementDump(post.getElement())) +
            frameValue(Integer.toString(post.getPostIndex()));
    }

    private static String elementDump(CircuitElm element) {
        return frameValue(stableElementDump(element));
    }

    /**
     * CircuitJS's historical dump is also an undo/save format.  A few model
     * dumps append live solver state so that an undo can restore it; those
     * values are not generation dependencies.  Keep the source/load/model
     * parameters and explicitly remove only the known transient positions.
     * Current generated boards use the supported stable types below.  An
     * unrecognised type fails closed instead of quietly admitting a dump whose
     * transient fields have not been audited.
     */
    private static String stableElementDump(CircuitElm element) {
        require(element != null, "Missing CircuitJS binding element");
        String dump = element.dump();
        require(dump != null, "CircuitJS binding element has no dump");
        Vector<String> tokens = dumpTokens(dump);
        int type = element.getDumpType();
        switch (type) {
        case 'c':
            // CapacitorElm: capacitance, live voltdiff, initialVoltage.
            return joinDumpTokens(tokens, 7);
        case 't':
            // TransistorElm: pnp, live VBE, live VCE, beta, model name.
            return joinDumpTokens(tokens, 7, 8);
        case 'd':
        case 'f':
        case 'g':
        case 'r':
        case 's':
        case 'v':
        case 'w':
        case 162:
            // Current generated boards: diode, MOSFET, ground, resistor,
            // switch, voltage source, wire, and LED.  Their dumps contain
            // only source/load/model inputs (the control switch position is
            // an intentional mutable input captured here).
            return joinDumpTokens(tokens);
        default:
            throw new IllegalStateException("No audited stable CircuitJS dump policy for type: " +
                type);
        }
    }

    private static Vector<String> dumpTokens(String dump) {
        Vector<String> tokens = new Vector<String>();
        com.lushprojects.circuitjs1.client.StringTokenizer tokenizer =
            new com.lushprojects.circuitjs1.client.StringTokenizer(dump);
        while (tokenizer.hasMoreTokens())
            tokens.add(tokenizer.nextToken());
        require(!tokens.isEmpty(), "CircuitJS element dump is empty");
        return tokens;
    }

    private static String joinDumpTokens(Vector<String> tokens, int... omitted) {
        for (int i = 0; i < omitted.length; i++)
            require(omitted[i] >= 0 && omitted[i] < tokens.size(),
                "CircuitJS dump is shorter than its audited policy");
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < tokens.size(); i++) {
            boolean skip = false;
            for (int j = 0; j < omitted.length; j++)
                if (i == omitted[j]) {
                    skip = true;
                    break;
                }
            if (skip)
                continue;
            if (out.length() > 0)
                out.append(' ');
            out.append(tokens.get(i));
        }
        return out.toString();
    }

    private static void resetModelDumpFlags() {
        CustomLogicModel.clearDumpedFlags();
        CustomCompositeModel.clearDumpedFlags();
        DiodeModel.clearDumpedFlags();
        TransistorModel.clearDumpedFlags();
    }

    private static String doubleBits(double value) {
        return Long.toHexString(Double.doubleToLongBits(value));
    }

    private static Vector<String> sorted(Vector<String> values) {
        require(values != null, "Missing stable value vector");
        Vector<String> result = new Vector<String>(values);
        Collections.sort(result);
        return result;
    }

    private static void appendStringList(StringBuilder out, String key,
            Vector<String> values, boolean sort) {
        require(values != null, "Missing value list: " + key);
        Vector<String> ordered = new Vector<String>(values);
        if (sort)
            Collections.sort(ordered);
        out.append('F');
        frame(out, key);
        frame(out, Integer.toString(ordered.size()));
        for (String value : ordered)
            frame(out, value);
        checkBound(out);
    }

    private static void appendField(StringBuilder out, String key, String value) {
        require(key != null && key.length() > 0, "Missing canonical field key");
        out.append('F');
        frame(out, key);
        frame(out, value);
        checkBound(out);
    }

    private static void appendLocal(StringBuilder out, String value) {
        frame(out, value);
    }

    private static String frameValue(String value) {
        StringBuilder out = new StringBuilder();
        frame(out, value);
        return out.toString();
    }

    private static void frame(StringBuilder out, String value) {
        if (value == null) {
            out.append("N;");
            return;
        }
        if (value.length() > MAX_FIELD_LENGTH)
            throw new IllegalStateException("Canonical dependency field exceeds its bound");
        out.append('V').append(value.length()).append(':').append(value).append(';');
    }

    private static void checkBound(StringBuilder out) {
        if (out.length() > MAX_CANONICAL_LENGTH)
            throw new IllegalStateException("Generation dependency context exceeds its bound");
    }

    private static void require(boolean condition, String message) {
        if (!condition)
            throw new IllegalStateException(message);
    }
}
