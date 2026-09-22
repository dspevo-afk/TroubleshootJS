package com.lushprojects.circuitjs1.client;

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
        TroubleshootBoard board = createBoard();
        BoardPhysicalSpecifications specifications = createSpecifications(rail);
        Vector<CircuitElm> elements = model.getSimulationElements();

        PassiveBuild rbias = createModelPassive("RBIAS",
            model.getBoardOwnedPassive("RBIAS"),
            modelEndpoints.getExternalSensorSourceEndpoint(),
            modelEndpoints.getConditionedSensorEndpoint());
        PassiveBuild rref = createModelPassive("RREF",
            model.getBoardOwnedPassive("RREF"),
            modelEndpoints.getRegulatorOutputBoardEndpoint(),
            modelEndpoints.getReferenceEndpoint());
        PassiveBuild rfb = createModelPassive("RFB",
            model.getBoardOwnedPassive("RFB"),
            modelEndpoints.getOutputDriveEndpoint(),
            modelEndpoints.getLoadedOutputEndpoint());
        PassiveBuild[] passives = { rbias, rref, rfb };
        for (PassiveBuild passive : passives) {
            elements.add(passive.firstLead);
            elements.add(passive.secondLead);
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
        GeneratedComponentOperationalStates operationalStates =
            new GeneratedComponentOperationalStates();

        BoardSimulationBindings bindings = board.getSimulationBindings();
        bindModelPads(board, bindings, model, modelEndpoints);
        bindPassivePads(bindings, rbias, "RBIAS");
        bindPassivePads(bindings, rref, "RREF");
        bindPassivePads(bindings, rfb, "RFB");
        GeneratedComponentConnectionBindings connectionBindings =
            new GeneratedComponentConnectionBindings(board);
        bindPassiveConnections(connectionBindings, bindings, rbias, "RBIAS");
        bindPassiveConnections(connectionBindings, bindings, rref, "RREF");
        bindPassiveConnections(connectionBindings, bindings, rfb, "RFB");

        PhysicalBoardRuntime runtime = new PhysicalBoardRuntime(board);
        PhysicalBoardSlot j1Slot = runtime.createSlot("J1");
        PhysicalBoardSlot j2Slot = runtime.createSlot("J2");
        PhysicalBoardSlot j3Slot = runtime.createSlot("J3");
        PhysicalBoardSlot u1Slot = runtime.createSlot("U1");
        installFixedFoundation(runtime, j1Slot, "J1", specifications, bindings,
            modelEndpoints.getRawInputEndpoint().getElement());
        installFixedFoundation(runtime, j2Slot, "J2", specifications, bindings,
            modelEndpoints.getExternalSensorSourceEndpoint().getElement());
        installFixedFoundation(runtime, j3Slot, "J3", specifications, bindings,
            modelEndpoints.getLoadedOutputEndpoint().getElement());
        installFixedFoundation(runtime, u1Slot, "U1", specifications, bindings,
            model.getSelectedE02Regulator());
        installResistor(runtime, componentBindings, connectionBindings, specifications,
            rbias, selectedBinding, fault, "RBIAS");
        installResistor(runtime, componentBindings, connectionBindings, specifications,
            rref, selectedBinding, fault, "RREF");
        installResistor(runtime, componentBindings, connectionBindings, specifications,
            rfb, selectedBinding, fault, "RFB");

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
        return instance;
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
        bindings.bindPad("J3.2", endpoints.getReturnEndpoint());
        bindings.bindPad("U1.INPUT", endpoints.getRegulatorInputBoardEndpoint());
        bindings.bindPad("U1.OUTPUT", endpoints.getRegulatorOutputBoardEndpoint());
        bindings.bindPad("U1.RETURN", endpoints.getRegulatorReturnBoardEndpoint());
        bindings.bindPad("U1.ENABLE", endpoints.getRegulatorEnableBoardEndpoint());
        RegulatorPhysicalMapping.mapComponentTerminals(board, "U1",
            model.getSelectedE02Regulator());
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
            passive.openPath.getPublicTerminal(), passive.secondLead);
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
        if (binding == null || binding.getElement() == null ||
                binding.getFaultIsolation() == null || binding.getOpenPath() == null)
            throw new IllegalStateException("Missing E04 model-owned passive: " + id);
        WireElm firstLead = link(firstEndpoint,
            new CircuitPostMeasurementEndpoint(binding.getElement(), 0));
        WireElm secondLead = link(binding.getOpenPath().getPublicTerminal(),
            secondEndpoint);
        return new PassiveBuild(id, binding.getElement(),
            binding.getFaultIsolation(), binding.getOpenPath(), firstEndpoint,
            secondEndpoint, firstLead, secondLead);
    }

    private WireElm link(CircuitPostMeasurementEndpoint first,
            CircuitPostMeasurementEndpoint second) {
        Point firstPoint = first.getElement().getPost(first.getPostIndex());
        Point secondPoint = second.getElement().getPost(second.getPostIndex());
        WireElm wire = new WireElm(firstPoint.x, firstPoint.y);
        wire.drag(secondPoint.x, secondPoint.y);
        return wire;
    }

    private TroubleshootBoard createBoard() {
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
        board.addComponent(new BoardComponent("RBIAS", "SENSOR_SOURCE_RESISTOR",
            PhysicalPackages.AXIAL_RESISTOR));
        board.addComponent(new BoardComponent("RREF", "REFERENCE_HIGH_RESISTOR",
            PhysicalPackages.AXIAL_RESISTOR));
        board.addComponent(new BoardComponent("RFB", "OUTPUT_SERIES_RESISTOR",
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
        addPad(board, "RBIAS.1", "RBIAS", "1", "SENSOR_SOURCE");
        addPad(board, "RBIAS.2", "RBIAS", "2", "CONDITIONED_SENSOR");
        addPad(board, "RREF.1", "RREF", "1", "CONTROL_RAIL");
        addPad(board, "RREF.2", "RREF", "2", "CONTROL_REFERENCE");
        addPad(board, "RFB.1", "RFB", "1", "CONTROL_OUTPUT");
        addPad(board, "RFB.2", "RFB", "2", "CONTROL_OUTPUT_LOAD");
        board.addPowerInput(new ExternalBoardPowerInput("CONTROL_RAIL_INPUT", "J1.1",
            "J1.2", "RAW_INPUT", "CONTROL_RETURN"));
        board.validate();
        return board;
    }

    private BoardPhysicalSpecifications createSpecifications(RailRegulationContract rail) {
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
        StandardPhysicalDefinitionProviders.RESISTOR.add(specifications,
            new ResistorNameplate("RBIAS", 10000.0, 5, .25));
        StandardPhysicalDefinitionProviders.RESISTOR.add(specifications,
            new ResistorNameplate("RREF", 10000.0, 5, .25));
        StandardPhysicalDefinitionProviders.RESISTOR.add(specifications,
            new ResistorNameplate("RFB", 47.0, 5, .25));
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
        final WireElm firstLead;
        final WireElm secondLead;

        PassiveBuild(String id, ResistorElm resistor, SwitchElm faultIsolation,
                ResistorSecondaryOpenPath openPath,
                CircuitPostMeasurementEndpoint firstEndpoint,
                CircuitPostMeasurementEndpoint secondEndpoint, WireElm firstLead,
                WireElm secondLead) {
            this.id = id;
            this.resistor = resistor;
            this.faultIsolation = faultIsolation;
            this.openPath = openPath;
            this.firstEndpoint = firstEndpoint;
            this.secondEndpoint = secondEndpoint;
            this.firstLead = firstLead;
            this.secondLead = secondLead;
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
