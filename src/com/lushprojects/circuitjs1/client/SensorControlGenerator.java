package com.lushprojects.circuitjs1.client;

import java.util.Collections;
import java.util.Vector;

/**
 * Deterministic, player-operable E04 sensor/control leaf.  E04 owns the live
 * rail, sensor source, reference divider, decision and output graph; this
 * generator contributes only the board adapters, serviceable passives and
 * physical identity around those model-owned endpoints.
 */
final class SensorControlGenerator {
    static final String FAMILY_ID = "SENSOR_CONTROL";
    static final String DIRECT_VARIANT = "DIRECT_THRESHOLD_LINEAR_5V";
    static final String HYSTERETIC_VARIANT = "HYSTERETIC_REGENERATIVE_AVERAGED_5V";

    GeneratedBoardInstance generate(long seed) {
        return generateInternal(seed, null);
    }

    GeneratedBoardInstance generateForFaultVerification(long seed, GeneratedFaultType type) {
        if (type != GeneratedFaultType.RESISTOR_OPEN)
            throw new IllegalArgumentException("Unsupported E04 verification fault: " + type);
        return generateInternal(seed, "TYPE:" + type.name());
    }

    GeneratedBoardInstance generateForHypothesis(long seed, String hypothesisKey) {
        if (hypothesisKey == null || hypothesisKey.length() == 0)
            throw new IllegalArgumentException("Missing E04 hypothesis key");
        return generateInternal(seed, hypothesisKey);
    }

    private GeneratedBoardInstance generateInternal(long seed, String forcedSelection) {
        boolean averagedRail = odd(seed);
        RailRegulationContract rail = averagedRail ?
            RailRegulationContract.averagedSwitching5V() :
            RailRegulationContract.linear5V();
        E04SensorControlModel.Variant controlVariant = averagedRail ?
            E04SensorControlModel.Variant.HYSTERETIC_REGENERATIVE :
            E04SensorControlModel.Variant.DIRECT_THRESHOLD;
        String topology = averagedRail ? HYSTERETIC_VARIANT : DIRECT_VARIANT;
        E04SensorControlModel model = E04SensorControlModel.fromE02Rail(
            rail, controlVariant, configurationFor(rail, controlVariant));
        E04SensorControlModel.SensorControlBindings modelEndpoints =
            model.getSensorControlBindings();
        model.prepareForPhysicalBoard();
        TroubleshootBoard board = createBoard(model.hasRegenerativeFeedback());
        BoardPhysicalSpecifications specifications = createSpecifications(rail,
            model.hasRegenerativeFeedback());
        Vector<CircuitElm> elements = model.getSimulationElements();
        model.validateElementOwnership(elements, board);

        PassiveBuild rbias = createModelPassive("RBIAS",
            model.getBoardOwnedPassive("RBIAS"),
            modelEndpoints.getExternalSensorSourceEndpoint(),
            model.getPhysicalSensorBoardEndpoint());
        PassiveBuild rref = createModelPassive("RREF",
            model.getBoardOwnedPassive("RREF"),
            modelEndpoints.getRegulatorOutputBoardEndpoint(),
            model.getPhysicalReferenceBoardEndpoint());
        PassiveBuild rfb = createModelPassive("RFB",
            model.getBoardOwnedPassive("RFB"),
            model.getPhysicalOutputBoardEndpoint(),
            modelEndpoints.getLoadedOutputEndpoint());
        PassiveBuild rrefLow = createModelPassive("RREF_LOW",
            model.getBoardSupportPassive("RREF_LOW"),
            model.getPhysicalReferenceBoardEndpoint(),
            model.getPhysicalReturnBoardEndpoint());
        PassiveBuild rfbHysteresis = model.hasRegenerativeFeedback() ?
            createModelPassive("RFB_HYST", model.getBoardSupportPassive("RFB_HYST"),
                model.getPhysicalOutputBoardEndpoint(),
                model.getPhysicalSensorBoardEndpoint()) : null;
        PassiveBuild[] passives = { rbias, rref, rfb };
        for (PassiveBuild passive : passives) {
            elements.add(passive.firstLead);
            elements.add(passive.secondLead);
        }
        elements.add(rrefLow.firstLead);
        elements.add(rrefLow.secondLead);
        if (rfbHysteresis != null) {
            elements.add(rfbHysteresis.firstLead);
            elements.add(rfbHysteresis.secondLead);
        }

        Vector<GeneratedFaultCandidate> candidates =
            new Vector<GeneratedFaultCandidate>();
        candidates.add(GeneratedFaultEngine.resistorOpen("SENSOR_RBIAS_OPEN",
            FAMILY_ID, seed, "RBIAS", rbias.faultIsolation));
        candidates.add(GeneratedFaultEngine.resistorOpen("SENSOR_RREF_OPEN",
            FAMILY_ID, seed, "RREF", rref.faultIsolation));
        candidates.add(GeneratedFaultEngine.resistorOpen("SENSOR_RFB_OPEN",
            FAMILY_ID, seed, "RFB", rfb.faultIsolation));
        GeneratedFaultCandidate selected = select(candidates, seed, forcedSelection);
        GeneratedFaultEngine.clearAll(candidates);
        for (GeneratedFaultCandidate candidate : candidates)
            for (CircuitElm privateElement : candidate.getPrivateSimulationElements())
                if (!elements.contains(privateElement)) elements.add(privateElement);

        GeneratedFaultBinding selectedBinding = selected.getBinding();
        GeneratedFault fault = selected.getFault();
        GeneratedComponentBindings componentBindings = new GeneratedComponentBindings(board);
        componentBindings.bindComponent("U1", model.getSelectedE02Regulator());
        componentBindings.bindComponent("RBIAS", rbias.resistor);
        componentBindings.bindAuxiliaryComponentElement("RBIAS",
            rbias.openPath.getSimulationElement());
        componentBindings.bindComponent("RREF", rref.resistor);
        componentBindings.bindAuxiliaryComponentElement("RREF",
            rref.openPath.getSimulationElement());
        componentBindings.bindComponent("RFB", rfb.resistor);
        componentBindings.bindAuxiliaryComponentElement("RFB",
            rfb.openPath.getSimulationElement());
        componentBindings.bindComponent("U2", model.getDecisionElement());
        componentBindings.bindComponent("RREF_LOW", rrefLow.resistor);
        if (rrefLow.openPath != null)
            componentBindings.bindAuxiliaryComponentElement("RREF_LOW",
                rrefLow.openPath.getSimulationElement());
        if (rfbHysteresis != null) {
            componentBindings.bindComponent("RFB_HYST", rfbHysteresis.resistor);
            if (rfbHysteresis.openPath != null)
                componentBindings.bindAuxiliaryComponentElement("RFB_HYST",
                    rfbHysteresis.openPath.getSimulationElement());
        }
        GeneratedComponentOperationalStates operationalStates =
            new GeneratedComponentOperationalStates();

        BoardSimulationBindings bindings = board.getSimulationBindings();
        bindModelPads(board, bindings, model, modelEndpoints);
        bindPassivePads(bindings, rbias, "RBIAS");
        bindPassivePads(bindings, rref, "RREF");
        bindPassivePads(bindings, rfb, "RFB");
        bindPassivePads(bindings, rrefLow, "RREF_LOW");
        if (rfbHysteresis != null)
            bindPassivePads(bindings, rfbHysteresis, "RFB_HYST");
        GeneratedComponentConnectionBindings connectionBindings =
            new GeneratedComponentConnectionBindings(board);
        bindPassiveConnections(connectionBindings, bindings, rbias, "RBIAS");
        bindPassiveConnections(connectionBindings, bindings, rref, "RREF");
        bindPassiveConnections(connectionBindings, bindings, rfb, "RFB");
        bindPassiveConnections(connectionBindings, bindings, rrefLow, "RREF_LOW");
        if (rfbHysteresis != null)
            bindPassiveConnections(connectionBindings, bindings, rfbHysteresis, "RFB_HYST");
        bindDecisionConnections(connectionBindings, bindings, model, elements);

        PhysicalBoardRuntime runtime = new PhysicalBoardRuntime(board);
        PhysicalBoardSlot j1Slot = runtime.createSlot("J1");
        PhysicalBoardSlot j2Slot = runtime.createSlot("J2");
        PhysicalBoardSlot j3Slot = runtime.createSlot("J3");
        PhysicalBoardSlot u1Slot = runtime.createSlot("U1");
        PhysicalBoardSlot u2Slot = runtime.createSlot("U2");
        installFixedFoundation(runtime, j1Slot, "J1", specifications, bindings,
            modelEndpoints.getRawInputEndpoint().getElement());
        installFixedFoundation(runtime, j2Slot, "J2", specifications, bindings,
            modelEndpoints.getExternalSensorSourceEndpoint().getElement());
        installFixedFoundation(runtime, j3Slot, "J3", specifications, bindings,
            modelEndpoints.getLoadedOutputEndpoint().getElement());
        installFixedFoundation(runtime, u1Slot, "U1", specifications, bindings,
            model.getSelectedE02Regulator());
        installDecisionControl(runtime, u2Slot, specifications, model,
            connectionBindings);
        installResistor(runtime, componentBindings, connectionBindings, specifications,
            rbias, selectedBinding, fault, "RBIAS");
        installResistor(runtime, componentBindings, connectionBindings, specifications,
            rref, selectedBinding, fault, "RREF");
        installResistor(runtime, componentBindings, connectionBindings, specifications,
            rfb, selectedBinding, fault, "RFB");
        installResistor(runtime, componentBindings, connectionBindings, specifications,
            rrefLow, selectedBinding, fault, "RREF_LOW");
        if (rfbHysteresis != null)
            installResistor(runtime, componentBindings, connectionBindings, specifications,
                rfbHysteresis, selectedBinding, fault, "RFB_HYST");

        GeneratedExternalPowerBindings powerBindings =
            new GeneratedExternalPowerBindings(board);
        Vector<CircuitElm> regulatorSources = new Vector<CircuitElm>();
        for (CircuitElm element : model.getSimulationElements())
            if (element instanceof E02FiniteSourceElm) regulatorSources.add(element);
        if (regulatorSources.size() != 2)
            throw new IllegalStateException("E04 model did not expose both E02 finite sources");
        powerBindings.bindPowerInput("CONTROL_RAIL_INPUT",
            new ExternalPowerSimulationBinding(regulatorSources,
                new SensorControlPowerControl(model)));

        SensorControlFamilyState familyState = new SensorControlFamilyState(model);
        GeneratedChallengeBehaviorContract behaviorContract =
            new GeneratedChallengeBehaviorAdapter(
                new SensorControlGeneratedBoardValidator(),
                new SensorControlFaultValidator(), new SensorControlRepairValidator());
        GeneratedScenarioCatalog<GeneratedObservedBehavior> scenarios =
            GeneratedScenarioLibrary.sensorControl();
        String description = "Generated sensor/control board, seed " + seed;
        GeneratedChallengeDefinition challenge = new GeneratedChallengeDefinition(
            "SENSOR_CONTROL_OUTPUT_NOT_TRACKING", FAMILY_ID, topology, seed,
            scenarios, "Repair verified. The sensor-controlled output follows the input.",
            fault, selectedBinding, behaviorContract);
        PcbBoardLayout layout = SensorControlPcbLayoutFactory.create(board, seed);
        GeneratedBoardInstance instance = new GeneratedBoardInstance(board, elements, seed,
            FAMILY_ID, topology, description, componentBindings, powerBindings,
            connectionBindings, behaviorContract, layout, specifications, selectedBinding,
            operationalStates, challenge, familyState, runtime, null, false, candidates, null,
            new SensorControlDiagnosticProvider(seed));
        validateFinalElementOwnership(instance, model);
        SensorControlGeneratedBoardValidator.requireStaticPhysicalOwners(instance);
        return instance;
    }

    /**
     * The model census alone precedes detachable leads and service completion.
     * Recheck the *published* graph: every added element must be either a
     * physical pad connection, a bound physical part/helper, or a declared
     * fault helper. An unclaimed solver element cannot become player-visible.
     */
    static void validateFinalElementOwnership(GeneratedBoardInstance instance,
            E04SensorControlModel model) {
        Vector<CircuitElm> modelElements = model.getSimulationElements();
        model.validateElementOwnership(modelElements, instance.getBoard());
        Vector<CircuitElm> published = instance.getSimulationElements();
        Vector<CircuitElm> accounted = new Vector<CircuitElm>();
        for (CircuitElm element : modelElements) {
            if (!published.contains(element))
                throw new IllegalStateException("E04 model element missing from published graph");
            accounted.add(element);
        }
        for (GeneratedComponentConnectionBinding binding :
                instance.getConnectionBindings().getAll()) {
            CircuitElm lead = binding.getConnectionElement();
            if (!(lead instanceof WireElm) || !published.contains(lead) ||
                    instance.getBoard().getPad(binding.getPadId()) == null)
                throw new IllegalStateException("E04 physical lead has no pad/solver owner: " +
                    binding.getPadId());
            if (!accounted.contains(lead)) accounted.add(lead);
        }
        for (String componentId : instance.getBoard().getComponentIds()) {
            if (!instance.getComponentBindings().hasComponentBinding(componentId))
                continue;
            Vector<CircuitElm> parts = instance.getComponentBindings().getElements(componentId);
            parts.addAll(instance.getComponentBindings().getAuxiliaryElements(componentId));
            for (CircuitElm element : parts) {
                if (!published.contains(element))
                    throw new IllegalStateException("E04 physical backing missing: " + componentId);
                if (!accounted.contains(element)) accounted.add(element);
            }
        }
        for (GeneratedFaultCandidate candidate : instance.getFaultCandidates())
            for (CircuitElm element : candidate.getPrivateSimulationElements()) {
                if (!published.contains(element))
                    throw new IllegalStateException("E04 declared fault helper missing");
                if (!accounted.contains(element)) accounted.add(element);
            }
        for (CircuitElm element : published)
            if (!accounted.contains(element))
                throw new IllegalStateException("E04 unexplained final solver element: " +
                    element.getClass().getName());
    }

    private GeneratedFaultCandidate select(Vector<GeneratedFaultCandidate> candidates,
            long seed, String forcedSelection) {
        if (forcedSelection == null)
            return GeneratedFaultEngine.select(seed, candidates);
        if (forcedSelection.indexOf("TYPE:") == 0)
            return GeneratedFaultEngine.select(GeneratedFaultType.valueOf(
                forcedSelection.substring("TYPE:".length())), candidates);
        return GeneratedFaultEngine.selectHypothesis(forcedSelection, candidates);
    }

    private void bindModelPads(TroubleshootBoard board, BoardSimulationBindings bindings,
            E04SensorControlModel model,
            E04SensorControlModel.SensorControlBindings endpoints) {
        if (model.getSelectedE02Regulator() == null ||
                endpoints.getRawInputEndpoint() == null ||
                endpoints.getRawReturnEndpoint() == null ||
                endpoints.getRegulatorInputBoardEndpoint() == null ||
                endpoints.getRegulatorOutputBoardEndpoint() == null ||
                endpoints.getRegulatorReturnBoardEndpoint() == null ||
                endpoints.getRegulatorEnableBoardEndpoint() == null)
            throw new IllegalStateException("E04 physical board requires a selected E02 regulator");
        // J1 is the raw external supply boundary.  Its binding controls the
        // existing finite E02 input/enable sources; it is not the regulated
        // output and does not create a second ideal rail.
        bindings.bindPad("J1.1", endpoints.getRawInputEndpoint());
        bindings.bindPad("J1.2", endpoints.getRawReturnEndpoint());
        bindings.bindPad("J2.1", endpoints.getExternalSensorSourceEndpoint());
        bindings.bindPad("J2.2", endpoints.getExternalSensorReturnEndpoint());
        bindings.bindPad("J3.1", endpoints.getLoadedOutputEndpoint());
        // J3 is an external customer connector, so its return must remain on
        // persistent board copper when U2's independently serviceable RETURN
        // lead is lifted.  The semantic return endpoint belongs to the U2
        // package and is deliberately detachable.
        bindings.bindPad("J3.2", model.getPhysicalReturnBoardEndpoint());
        bindings.bindPad("U1.INPUT", endpoints.getRegulatorInputBoardEndpoint());
        bindings.bindPad("U1.OUTPUT", endpoints.getRegulatorOutputBoardEndpoint());
        bindings.bindPad("U1.RETURN", endpoints.getRegulatorReturnBoardEndpoint());
        bindings.bindPad("U1.ENABLE", endpoints.getRegulatorEnableBoardEndpoint());
        bindings.bindPad("U2.SENSOR", model.getPhysicalSensorBoardEndpoint());
        bindings.bindPad("U2.REFERENCE", model.getPhysicalReferenceBoardEndpoint());
        bindings.bindPad("U2.RAIL", endpoints.getRegulatorOutputBoardEndpoint());
        bindings.bindPad("U2.OUTPUT", model.getPhysicalOutputBoardEndpoint());
        bindings.bindPad("U2.RETURN", model.getPhysicalReturnBoardEndpoint());
        RegulatorPhysicalMapping.mapComponentTerminals(board, "U1",
            model.getSelectedE02Regulator());
    }

    private void bindDecisionConnections(GeneratedComponentConnectionBindings connections,
            BoardSimulationBindings bindings, E04SensorControlModel model,
            Vector<CircuitElm> elements) {
        E04SensorControlModel.DecisionElement decision = model.getDecisionElement();
        bindDecisionConnection(connections, bindings, "U2.SENSOR", decision, 0,
            model.getPhysicalSensorBoardEndpoint(), elements);
        bindDecisionConnection(connections, bindings, "U2.REFERENCE", decision, 1,
            model.getPhysicalReferenceBoardEndpoint(), elements);
        bindDecisionConnection(connections, bindings, "U2.RAIL", decision, 2,
            model.getSensorControlBindings().getRegulatorOutputBoardEndpoint(), elements);
        bindDecisionConnection(connections, bindings, "U2.OUTPUT", decision, 3,
            model.getPhysicalOutputBoardEndpoint(), elements);
        bindDecisionConnection(connections, bindings, "U2.RETURN", decision, 4,
            model.getPhysicalReturnBoardEndpoint(), elements);
    }

    private void bindDecisionConnection(GeneratedComponentConnectionBindings connections,
            BoardSimulationBindings bindings, String padId,
            E04SensorControlModel.DecisionElement decision, int postIndex,
            CircuitPostMeasurementEndpoint boardEndpoint,
            Vector<CircuitElm> elements) {
        CircuitPostMeasurementEndpoint componentEndpoint =
            new CircuitPostMeasurementEndpoint(decision, postIndex);
        WireElm lead = link(boardEndpoint, componentEndpoint);
        elements.add(lead);
        connections.bind("U2", padId, bindings.getEndpoint(padId),
            componentEndpoint, lead);
    }

    private void bindPassivePads(BoardSimulationBindings bindings, PassiveBuild passive,
            String componentId) {
        bindings.bindPad(componentId + ".1", passive.firstEndpoint);
        bindings.bindPad(componentId + ".2", passive.secondEndpoint);
    }

    private void bindPassiveConnections(GeneratedComponentConnectionBindings connections,
            BoardSimulationBindings bindings, PassiveBuild passive, String componentId) {
        connections.bind(componentId, componentId + ".1", bindings.getEndpoint(componentId + ".1"),
            new CircuitPostMeasurementEndpoint(passive.resistor, 0), passive.firstLead);
        connections.bind(componentId, componentId + ".2", bindings.getEndpoint(componentId + ".2"),
            passive.getSecondComponentEndpoint(), passive.secondLead);
    }

    private void installFixedFoundation(PhysicalBoardRuntime runtime, PhysicalBoardSlot slot,
            String componentId, BoardPhysicalSpecifications specifications,
            BoardSimulationBindings bindings, CircuitElm backingElement) {
        slot.install(PhysicalFoundationPartFactory.fromBoardBindings(componentId,
            (BasicPhysicalSpecification) specifications.getSpecification(componentId),
            specifications.getNameplate(componentId), specifications.getPackage(componentId),
            bindings, backingElement, new PhysicalPartProvenance(
                PhysicalPartProvenance.FIXED_GENERATED, componentId)));
    }

    private void installDecisionControl(PhysicalBoardRuntime runtime,
            PhysicalBoardSlot slot, BoardPhysicalSpecifications specifications,
            E04SensorControlModel model,
            GeneratedComponentConnectionBindings connections) {
        PhysicalSpecification specification = specifications.getSpecification("U2");
        PhysicalNameplate nameplate = specifications.getNameplate("U2");
        E04DecisionControlPart original = new E04DecisionControlPart("U2_ORIGINAL",
            specification, nameplate, model.getDecisionElement(),
            new PhysicalPartProvenance(PhysicalPartProvenance.GENERATED_ORIGINAL, "U2"));
        PhysicalPartInventory<E04DecisionControlPart> inventory =
            new PhysicalPartInventory<E04DecisionControlPart>(runtime,
                "U2_REPLACEMENTS", E04DecisionControlPart.class);
        inventory.add(original);
        String[] padIds = { "U2.SENSOR", "U2.REFERENCE", "U2.RAIL",
            "U2.OUTPUT", "U2.RETURN" };
        WireElm[] attachments = new WireElm[padIds.length];
        for (int index = 0; index < padIds.length; index++) {
            CircuitElm element = connections.get("U2", padIds[index])
                .getConnectionElement();
            if (!(element instanceof WireElm))
                throw new IllegalStateException(
                    "E04 U2 terminal is not a detachable solver wire: " +
                    padIds[index]);
            attachments[index] = (WireElm) element;
        }
        E04DecisionControlSlot decisionSlot = new E04DecisionControlSlot("U2",
            specification, original, attachments, slot);
        runtime.registerCapability(new E04DecisionControlBoardCapability(
            decisionSlot, inventory, original, model));
    }

    private void installResistor(PhysicalBoardRuntime runtime,
            GeneratedComponentBindings componentBindings,
            GeneratedComponentConnectionBindings connectionBindings,
            BoardPhysicalSpecifications specifications, PassiveBuild passive,
            GeneratedFaultBinding selectedBinding, GeneratedFault fault, String componentId) {
        PhysicalBoardSlot slot = runtime.createSlot(componentId);
        ResistorNameplate specification = StandardPhysicalDefinitionProviders.RESISTOR
            .require(specifications, componentId);
        GeneratedFaultBinding physicalFault = componentId.equals(fault.getTargetComponentId()) ?
            selectedBinding : null;
        PhysicalResistorPart original = new PhysicalResistorPart(componentId + "_ORIGINAL",
            specification, new ResistorNameplate(componentId + "_ORIGINAL",
                specification.getNominalResistanceOhms(), specification.getTolerancePercent()),
            specifications.getNameplate(componentId), passive.resistor, physicalFault,
            passive.openPath, ResistorPartLocation.INSTALLED,
            new PhysicalPartProvenance(PhysicalPartProvenance.GENERATED_ORIGINAL, componentId));
        PhysicalPartInventory<PhysicalResistorPart> inventory =
            new PhysicalPartInventory<PhysicalResistorPart>(runtime,
                componentId + "_REPLACEMENTS", PhysicalResistorPart.class);
        inventory.add(original);
        ReplaceableComponentSlot replaceable = new ReplaceableComponentSlot(componentId,
            specification, original, passive.firstLead, passive.secondLead, slot);
        runtime.registerCapability(new ReplaceableResistorBoardCapability(
            "SENSOR_REPLACEABLE_" + componentId, replaceable, inventory,
            new ResistorReplacementCatalog(), componentId + " service resistor"));
    }

    private PassiveBuild createModelPassive(String id,
            E04SensorControlModel.PassiveBinding binding,
            CircuitPostMeasurementEndpoint firstEndpoint,
            CircuitPostMeasurementEndpoint secondEndpoint) {
        if (binding == null || binding.getElement() == null)
            throw new IllegalStateException("Missing E04 model-owned passive: " + id);
        WireElm firstLead = link(firstEndpoint,
            new CircuitPostMeasurementEndpoint(binding.getElement(), 0));
        CircuitPostMeasurementEndpoint componentSecondEndpoint = binding.getOpenPath() == null ?
            new CircuitPostMeasurementEndpoint(binding.getElement(), 1) :
            binding.getOpenPath().getPublicTerminal();
        WireElm secondLead = link(componentSecondEndpoint, secondEndpoint);
        return new PassiveBuild(id, binding.getElement(),
            binding.getFaultIsolation(), binding.getOpenPath(), firstEndpoint,
            secondEndpoint, componentSecondEndpoint, firstLead, secondLead);
    }

    private WireElm link(CircuitPostMeasurementEndpoint first,
            CircuitPostMeasurementEndpoint second) {
        Point firstPoint = first.getElement().getPost(first.getPostIndex());
        Point secondPoint = second.getElement().getPost(second.getPostIndex());
        WireElm wire = new WireElm(firstPoint.x, firstPoint.y);
        wire.drag(secondPoint.x, secondPoint.y);
        return wire;
    }

    private TroubleshootBoard createBoard(boolean hasFeedback) {
        TroubleshootBoard board = new TroubleshootBoard(FAMILY_ID);
        board.addNet(new BoardNet("CONTROL_RAIL"));
        board.addNet(new BoardNet("CONTROL_RETURN"));
        board.addNet(new BoardNet("RAW_INPUT"));
        board.addNet(new BoardNet("CONTROL_ENABLE"));
        board.addNet(new BoardNet("SENSOR_SOURCE"));
        board.addNet(new BoardNet("CONDITIONED_SENSOR"));
        board.addNet(new BoardNet("CONTROL_REFERENCE"));
        board.addNet(new BoardNet("CONTROL_OUTPUT"));
        board.addNet(new BoardNet("CONTROL_OUTPUT_LOAD"));
        board.addComponent(new BoardComponent("J1", "POWER_CONNECTOR",
            PhysicalPackages.THROUGH_HOLE_CONNECTOR_2));
        board.addComponent(new BoardComponent("J2", "SENSOR_CONNECTOR",
            PhysicalPackages.THROUGH_HOLE_CONNECTOR_2));
        board.addComponent(new BoardComponent("J3", "OUTPUT_HEADER",
            PhysicalPackages.THROUGH_HOLE_OUTPUT_HEADER_2));
        board.addComponent(new BoardComponent("U1", "RAIL_REGULATOR",
            PhysicalPackages.TO220_REGULATOR_4));
        board.addComponent(new BoardComponent("U2", "SENSOR_CONTROL_DECISION",
            PhysicalPackages.E04_DECISION_CONTROL_5));
        board.addComponent(new BoardComponent("RBIAS", "SENSOR_SOURCE_RESISTOR",
            PhysicalPackages.AXIAL_RESISTOR));
        board.addComponent(new BoardComponent("RREF", "REFERENCE_HIGH_RESISTOR",
            PhysicalPackages.AXIAL_RESISTOR));
        board.addComponent(new BoardComponent("RFB", "OUTPUT_SERIES_RESISTOR",
            PhysicalPackages.AXIAL_RESISTOR));
        board.addComponent(new BoardComponent("RREF_LOW", "REFERENCE_RETURN_RESISTOR",
            PhysicalPackages.AXIAL_RESISTOR));
        if (hasFeedback)
            board.addComponent(new BoardComponent("RFB_HYST", "HYSTERESIS_FEEDBACK_RESISTOR",
                PhysicalPackages.AXIAL_RESISTOR));
        addPad(board, "J1.1", "J1", "1", "RAW_INPUT");
        addPad(board, "J1.2", "J1", "2", "CONTROL_RETURN");
        addPad(board, "J2.1", "J2", "1", "SENSOR_SOURCE");
        addPad(board, "J2.2", "J2", "2", "CONTROL_RETURN");
        addPad(board, "J3.1", "J3", "1", "CONTROL_OUTPUT_LOAD");
        addPad(board, "J3.2", "J3", "2", "CONTROL_RETURN");
        addPad(board, "U1.INPUT", "U1", "INPUT", "RAW_INPUT");
        addPad(board, "U1.OUTPUT", "U1", "OUTPUT", "CONTROL_RAIL");
        addPad(board, "U1.RETURN", "U1", "RETURN", "CONTROL_RETURN");
        addPad(board, "U1.ENABLE", "U1", "ENABLE", "CONTROL_ENABLE");
        addPad(board, "U2.SENSOR", "U2", "SENSOR", "CONDITIONED_SENSOR");
        addPad(board, "U2.REFERENCE", "U2", "REFERENCE", "CONTROL_REFERENCE");
        addPad(board, "U2.RAIL", "U2", "RAIL", "CONTROL_RAIL");
        addPad(board, "U2.OUTPUT", "U2", "OUTPUT", "CONTROL_OUTPUT");
        addPad(board, "U2.RETURN", "U2", "RETURN", "CONTROL_RETURN");
        addPad(board, "RBIAS.1", "RBIAS", "1", "SENSOR_SOURCE");
        addPad(board, "RBIAS.2", "RBIAS", "2", "CONDITIONED_SENSOR");
        addPad(board, "RREF.1", "RREF", "1", "CONTROL_RAIL");
        addPad(board, "RREF.2", "RREF", "2", "CONTROL_REFERENCE");
        addPad(board, "RFB.1", "RFB", "1", "CONTROL_OUTPUT");
        addPad(board, "RFB.2", "RFB", "2", "CONTROL_OUTPUT_LOAD");
        addPad(board, "RREF_LOW.1", "RREF_LOW", "1", "CONTROL_REFERENCE");
        addPad(board, "RREF_LOW.2", "RREF_LOW", "2", "CONTROL_RETURN");
        if (hasFeedback) {
            addPad(board, "RFB_HYST.1", "RFB_HYST", "1", "CONTROL_OUTPUT");
            addPad(board, "RFB_HYST.2", "RFB_HYST", "2", "CONDITIONED_SENSOR");
        }
        board.addPowerInput(new ExternalBoardPowerInput("CONTROL_RAIL_INPUT", "J1.1",
            "J1.2", "RAW_INPUT", "CONTROL_RETURN"));
        installPhysicalDemand(board);
        board.validate();
        return board;
    }

    /**
     * E04 keeps the component bodies on the top side but routes its generated
     * copper on the bottom side.  This is an owned board decision, not a
     * relaxation of the generic router: the five U2 escape channels remain
     * real through-hole pad geometry while the bottom plane can pass beneath
     * the mapped control package and its serviceable support passives.
     */
    private void installPhysicalDemand(TroubleshootBoard board) {
        Vector<String> componentIds = board.getComponentIds();
        Collections.sort(componentIds);
        Vector<PcbPlacementConstraints.Part> parts =
            new Vector<PcbPlacementConstraints.Part>();
        for (String componentId : componentIds) {
            boolean connector = board.getComponent(componentId).getPhysicalPackage().isConnector();
            parts.add(new PcbPlacementConstraints.Part(componentId, "circuit", "Circuit",
                "board", connector ? PcbPlacementConstraints.Anchor.EDGE :
                    PcbPlacementConstraints.Anchor.NONE, 20));
        }
        board.setPlacementConstraints(new PcbPlacementConstraints(parts,
            new Vector<PcbPlacementConstraints.Barrier>(), PcbCopperLayer.BOTTOM));
    }

    private BoardPhysicalSpecifications createSpecifications(RailRegulationContract rail,
            boolean hasFeedback) {
        BoardPhysicalSpecifications specifications = new BoardPhysicalSpecifications();
        specifications.addPhysicalDefinition("J1", new BasicPhysicalSpecification("J1_CONNECTOR"),
            new PhysicalNameplate("J1", "Control rail connector"),
            PhysicalPackages.THROUGH_HOLE_CONNECTOR_2);
        specifications.addPhysicalDefinition("J2", new BasicPhysicalSpecification("J2_CONNECTOR"),
            new PhysicalNameplate("J2", "Sensor input connector"),
            PhysicalPackages.THROUGH_HOLE_CONNECTOR_2);
        specifications.addPhysicalDefinition("J3", new BasicPhysicalSpecification("J3_OUTPUT"),
            new PhysicalNameplate("J3", "Conditioned output header"),
            PhysicalPackages.THROUGH_HOLE_OUTPUT_HEADER_2);
        specifications.addPhysicalDefinition("U1", new BasicPhysicalSpecification("U1_REGULATOR"),
            new PhysicalNameplate("U1", "5 V control rail regulator"),
            PhysicalPackages.TO220_REGULATOR_4);
        specifications.addPhysicalDefinition("U2", new BasicPhysicalSpecification("U2_DECISION"),
            new PhysicalNameplate("U2", "Sensor threshold decision controller"),
            PhysicalPackages.E04_DECISION_CONTROL_5);
        StandardPhysicalDefinitionProviders.RESISTOR.add(specifications,
            new ResistorNameplate("RBIAS", 10000.0, 5, .25));
        StandardPhysicalDefinitionProviders.RESISTOR.add(specifications,
            new ResistorNameplate("RREF", 10000.0, 5, .25));
        StandardPhysicalDefinitionProviders.RESISTOR.add(specifications,
            new ResistorNameplate("RFB", 47.0, 5, .25));
        StandardPhysicalDefinitionProviders.RESISTOR.add(specifications,
            new ResistorNameplate("RREF_LOW", 10000.0, 5, .25));
        if (hasFeedback)
            StandardPhysicalDefinitionProviders.RESISTOR.add(specifications,
                new ResistorNameplate("RFB_HYST", 22000.0, 5, .25));
        specifications.addPowerInputNameplate(new PowerInputNameplate(
            "CONTROL_RAIL_INPUT", rail.getNominalOutputVolts() + 2.0));
        return specifications;
    }

    private void addPad(TroubleshootBoard board, String id, String componentId,
            String terminal, String net) {
        board.addPad(new BoardPad(id, componentId, terminal, net));
    }

    private boolean odd(long seed) {
        long value = seed % 2L;
        if (value < 0L) value += 2L;
        return value == 1L;
    }

    /** Keep the two control variants on the same physical resistance seams. */
    private E04SensorControlModel.Configuration configurationFor(
            RailRegulationContract rail, E04SensorControlModel.Variant variant) {
        E04SensorControlModel.Configuration defaults =
            E04SensorControlModel.Configuration.defaults(
                E04SensorControlModel.adaptE02Rail(rail));
        // The generated RREF is the model-owned reference-high resistor, so
        // there is no hidden parallel reference branch to mask its open fault.
        double referenceHigh = defaults.referenceHighOhms;
        if (variant != E04SensorControlModel.Variant.HYSTERETIC_REGENERATIVE)
            return new E04SensorControlModel.Configuration(
                defaults.sensorSourceResistanceOhms, defaults.sensorLoadOhms,
                referenceHigh, defaults.referenceLowOhms,
                defaults.directThresholdOffsetVolts,
                defaults.risingThresholdOffsetVolts,
                defaults.fallingThresholdOffsetVolts,
                defaults.sensorLowVolts, defaults.sensorHighVolts);
        return new E04SensorControlModel.Configuration(
            defaults.sensorSourceResistanceOhms, defaults.sensorLoadOhms,
            referenceHigh, defaults.referenceLowOhms,
            defaults.directThresholdOffsetVolts, .10, -.10,
            defaults.sensorLowVolts, defaults.sensorHighVolts);
    }

    private static final class PassiveBuild {
        final String id;
        final ResistorElm resistor;
        final SwitchElm faultIsolation;
        final ResistorSecondaryOpenPath openPath;
        final CircuitPostMeasurementEndpoint firstEndpoint;
        final CircuitPostMeasurementEndpoint secondEndpoint;
        final CircuitPostMeasurementEndpoint secondComponentEndpoint;
        final WireElm firstLead;
        final WireElm secondLead;

        PassiveBuild(String id, ResistorElm resistor, SwitchElm faultIsolation,
                ResistorSecondaryOpenPath openPath,
                CircuitPostMeasurementEndpoint firstEndpoint,
                CircuitPostMeasurementEndpoint secondEndpoint,
                CircuitPostMeasurementEndpoint secondComponentEndpoint,
                WireElm firstLead, WireElm secondLead) {
            this.id = id;
            this.resistor = resistor;
            this.faultIsolation = faultIsolation;
            this.openPath = openPath;
            this.firstEndpoint = firstEndpoint;
            this.secondEndpoint = secondEndpoint;
            this.secondComponentEndpoint = secondComponentEndpoint;
            this.firstLead = firstLead;
            this.secondLead = secondLead;
        }

        CircuitPostMeasurementEndpoint getSecondComponentEndpoint() {
            return secondComponentEndpoint;
        }
    }

    private static final class SensorControlPowerControl implements ExternalPowerControl {
        private final E04SensorControlModel model;

        SensorControlPowerControl(E04SensorControlModel model) {
            this.model = model;
        }

        public void setConnected(boolean connected) {
            model.setRailPowered(connected);
        }

        public boolean isConnected() {
            return model.isPowered();
        }
    }
}
