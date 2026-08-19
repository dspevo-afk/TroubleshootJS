package com.lushprojects.circuitjs1.client;

import java.util.Vector;

/**
 * Bounded, seeded NPN low-side switch family.  CircuitJS elements are the
 * only electrical model; board objects and physical identities wrap them.
 */
final class NpnLowSideSwitchGenerator {
    static final String FAMILY_ID = "NPN_LOW_SIDE_SWITCH";
    static final String TOPOLOGY_VARIANT = "NPN_LOW_SIDE";
    private static final double LED_FORWARD_VOLTAGE = 2.1;
    GeneratedBoardInstance generate(long seed) {
        return generateInternal(seed, null);
    }

    GeneratedBoardInstance generateForFaultVerification(long seed, GeneratedFaultType type) {
        if (type != GeneratedFaultType.TRANSISTOR_CE_OPEN &&
                type != GeneratedFaultType.TRANSISTOR_CE_SHORT &&
                type != GeneratedFaultType.BASE_RESISTOR_OPEN &&
                type != GeneratedFaultType.LOAD_PATH_OPEN)
            throw new IllegalArgumentException("Unsupported NPN verification fault: " + type);
        return generateInternal(seed, type);
    }

    private GeneratedBoardInstance generateInternal(long seed, GeneratedFaultType forcedType) {
        NpnValues values = valuesFor(seed);
        TroubleshootBoard board = createBoard();
        BoardPhysicalSpecifications specifications = createSpecifications(values);

        DCVoltageElm loadSupply = new DCVoltageElm(112, 416);
        loadSupply.drag(112, 176);
        loadSupply.maxVoltage = values.loadSupplyVoltage;
        SwitchElm loadIsolation = new SwitchElm(112, 176);
        loadIsolation.drag(192, 176);
        WireElm loadInputTrace = new WireElm(192, 176);
        loadInputTrace.drag(240, 176);
        WireElm loadResistorLead1 = new WireElm(240, 176);
        loadResistorLead1.drag(304, 176);
        ResistorElm loadResistor = new ResistorElm(304, 176);
        loadResistor.drag(384, 176);
        loadResistor.setResistance(values.loadResistanceOhms);
        SwitchElm loadFaultSwitch = new SwitchElm(384, 176);
        loadFaultSwitch.drag(416, 176);
        WireElm loadNodeTrace = new WireElm(480, 176);
        loadNodeTrace.drag(512, 176);
        LEDElm led = new LEDElm(512, 176);
        led.drag(512, 256);
        led.modelName = "default-led";
        led.setup();
        led.colorR = 1;
        led.colorG = 0;
        led.colorB = 0;

        DCVoltageElm controlSupply = new DCVoltageElm(112, 496);
        controlSupply.drag(112, 96);
        controlSupply.maxVoltage = values.controlSupplyVoltage;
        SwitchElm controlIsolation = new SwitchElm(112, 96);
        controlIsolation.drag(192, 96);
        WireElm controlInputTrace = new WireElm(192, 96);
        controlInputTrace.drag(240, 96);
        SwitchElm controlCommand = new SwitchElm(240, 96);
        controlCommand.drag(272, 96);
        WireElm controlDriveTrace = new WireElm(272, 96);
        controlDriveTrace.drag(288, 96);
        WireElm baseResistorLead1 = new WireElm(288, 96);
        baseResistorLead1.drag(304, 96);
        ResistorElm baseResistor = new ResistorElm(304, 96);
        baseResistor.drag(384, 96);
        baseResistor.setResistance(values.baseResistanceOhms);
        SwitchElm baseFaultSwitch = new SwitchElm(384, 96);
        baseFaultSwitch.drag(416, 96);
        WireElm baseTrace = new WireElm(496, 96);
        baseTrace.drag(544, 96);
        ResistorElm basePullDown = new ResistorElm(496, 96);
        basePullDown.drag(496, 176);
        basePullDown.setResistance(values.pullDownResistanceOhms);
        NTransistorElm transistor = new NTransistorElm(592, 288);
        transistor.drag(672, 288);
        transistor.setBeta(values.beta);

        Point collector = transistor.getPost(1);
        Point emitter = transistor.getPost(2);
        Point base = transistor.getPost(0);
        WireElm baseNodeTrace = new WireElm(496, 96);
        baseNodeTrace.drag(544, 288);
        SwitchElm transistorCollectorFaultSwitch = new SwitchElm(collector.x, collector.y);
        transistorCollectorFaultSwitch.drag(collector.x + 32, collector.y);
        ResistorElm transistorCeFaultShunt = new ResistorElm(collector.x, collector.y);
        transistorCeFaultShunt.drag(emitter.x, emitter.y);
        WireElm collectorTrace = new WireElm(led.getPost(1).x, led.getPost(1).y);
        collectorTrace.drag(collector.x - 48, collector.y);
        WireElm transistorCollectorAttachment = new WireElm(collector.x - 48, collector.y);
        transistorCollectorAttachment.drag(collector.x, collector.y);
        WireElm emitterNodeTrace = new WireElm(704, 416);
        emitterNodeTrace.drag(emitter.x - 48, emitter.y);
        WireElm transistorEmitterAttachment = new WireElm(emitter.x - 48, emitter.y);
        transistorEmitterAttachment.drag(emitter.x, emitter.y);
        WireElm transistorBaseAttachment = new WireElm(544, 288);
        transistorBaseAttachment.drag(base.x, base.y);

        GroundElm ground = new GroundElm(704, 416);
        ground.drag(704, 448);
        WireElm loadReturn = new WireElm(112, 416);
        loadReturn.drag(704, 416);
        WireElm controlReturn = new WireElm(112, 496);
        controlReturn.drag(112, 416);
        WireElm pullDownReturn = new WireElm(496, 176);
        pullDownReturn.drag(704, 416);

        Vector<CircuitElm> elements = new Vector<CircuitElm>();
        add(elements, loadSupply, loadIsolation, loadInputTrace, loadResistorLead1,
            loadResistor, loadFaultSwitch, loadNodeTrace, led, controlSupply,
            controlIsolation, controlInputTrace, controlCommand, controlDriveTrace,
            baseResistorLead1, baseResistor, baseFaultSwitch, baseTrace, basePullDown,
            baseNodeTrace, transistor, transistorCollectorFaultSwitch,
            transistorCeFaultShunt, collectorTrace, transistorCollectorAttachment,
            emitterNodeTrace, transistorEmitterAttachment, transistorBaseAttachment,
            ground, loadReturn, controlReturn, pullDownReturn);

        GeneratedComponentBindings componentBindings = new GeneratedComponentBindings(board);
        componentBindings.bindComponent("RLOAD", loadResistor);
        componentBindings.bindComponent("RB", baseResistor);
        componentBindings.bindComponent("RPD", basePullDown);
        componentBindings.bindComponent("LED1", led);
        componentBindings.bindComponent("Q1", transistor);
        GeneratedComponentOperationalStates operationalStates =
            new GeneratedComponentOperationalStates();
        operationalStates.bindLed("LED1", led);

        Vector<GeneratedFaultCandidate> candidates = new Vector<GeneratedFaultCandidate>();
        candidates.add(GeneratedFaultEngine.transistorCollectorOpen("NPN_Q1_CE_OPEN",
            FAMILY_ID, seed, "Q1", transistorCollectorFaultSwitch));
        candidates.add(GeneratedFaultEngine.transistorCeShort("NPN_Q1_CE_SHORT", FAMILY_ID,
            seed, "Q1", transistorCeFaultShunt));
        candidates.add(GeneratedFaultEngine.baseResistorOpen("NPN_RB_OPEN", FAMILY_ID, seed,
            "RB", baseFaultSwitch));
        candidates.add(GeneratedFaultEngine.loadPathOpen("NPN_LOAD_PATH_OPEN", FAMILY_ID, seed,
            "RLOAD", loadFaultSwitch));
        GeneratedFaultEngine.clearAll(candidates);
        GeneratedFaultCandidate selected = forcedType == null ?
            GeneratedFaultEngine.select(seed, candidates) :
            GeneratedFaultEngine.selectForDeveloperVerification(forcedType, candidates);
        for (GeneratedFaultCandidate candidateFault : candidates)
            for (CircuitElm privateElement : candidateFault.getPrivateSimulationElements())
                if (!elements.contains(privateElement))
                    elements.add(privateElement);

        GeneratedFault fault = selected.getFault();
        GeneratedFaultBinding faultBinding = selected.getBinding();
        GeneratedFaultBinding loadFaultBinding = targetBinding(fault, faultBinding, "RLOAD");
        GeneratedFaultBinding baseFaultBinding = targetBinding(fault, faultBinding, "RB");
        GeneratedFaultBinding transistorFaultBinding = targetBinding(fault, faultBinding, "Q1");
        ResistorSecondaryOpenPath loadSecondaryPath = ResistorSecondaryOpenPath.create(
            secondaryUpstream(loadFaultBinding, loadResistor, loadFaultSwitch));
        ResistorSecondaryOpenPath baseSecondaryPath = ResistorSecondaryOpenPath.create(
            secondaryUpstream(baseFaultBinding, baseResistor, baseFaultSwitch));
        elements.add(loadSecondaryPath.getSimulationElement());
        elements.add(baseSecondaryPath.getSimulationElement());
        componentBindings.bindAuxiliaryComponentElement("RLOAD",
            loadSecondaryPath.getSimulationElement());
        componentBindings.bindAuxiliaryComponentElement("RB",
            baseSecondaryPath.getSimulationElement());

        BoardSimulationBindings bindings = board.getSimulationBindings();
        bindings.bindPad("J1.1", new CircuitPostMeasurementEndpoint(loadInputTrace, 0));
        bindings.bindPad("J1.2", new CircuitPostMeasurementEndpoint(ground, 0));
        bindings.bindPad("RLOAD.1", new CircuitPostMeasurementEndpoint(loadInputTrace, 1));
        bindings.bindPad("RLOAD.2", new CircuitPostMeasurementEndpoint(loadNodeTrace, 0));
        bindings.bindPad("LED1.A", new CircuitPostMeasurementEndpoint(loadNodeTrace, 1));
        bindings.bindPad("LED1.K", new CircuitPostMeasurementEndpoint(collectorTrace, 0));
        bindings.bindPad("J2.1", new CircuitPostMeasurementEndpoint(controlDriveTrace, 1));
        bindings.bindPad("J2.2", new CircuitPostMeasurementEndpoint(ground, 0));
        bindings.bindPad("RB.1", new CircuitPostMeasurementEndpoint(controlDriveTrace, 1));
        bindings.bindPad("RB.2", new CircuitPostMeasurementEndpoint(baseTrace, 1));
        bindings.bindPad("RPD.1", new CircuitPostMeasurementEndpoint(baseTrace, 1));
        bindings.bindPad("RPD.2", new CircuitPostMeasurementEndpoint(ground, 0));
        bindings.bindPad("Q1.B", new CircuitPostMeasurementEndpoint(baseNodeTrace, 1));
        bindings.bindPad("Q1.C", new CircuitPostMeasurementEndpoint(collectorTrace, 1));
        bindings.bindPad("Q1.E", new CircuitPostMeasurementEndpoint(emitterNodeTrace, 1));

        GeneratedComponentConnectionBindings connectionBindings =
            new GeneratedComponentConnectionBindings(board);
        Point loadSecondaryPublic = loadSecondaryPath.getPublicTerminal().getElement().getPost(1);
        WireElm loadResistorLead2 = new WireElm(loadSecondaryPublic.x, loadSecondaryPublic.y);
        loadResistorLead2.drag(loadNodeTrace.getPost(0).x, loadNodeTrace.getPost(0).y);
        connectionBindings.bind("RLOAD", "RLOAD.1", bindings.getEndpoint("RLOAD.1"),
            new CircuitPostMeasurementEndpoint(loadResistor, 0), loadResistorLead1);
        connectionBindings.bind("RLOAD", "RLOAD.2", bindings.getEndpoint("RLOAD.2"),
            new CircuitPostMeasurementEndpoint(loadSecondaryPath.getSimulationElement(), 1),
            loadResistorLead2);
        connectionBindings.bind("RB", "RB.1", bindings.getEndpoint("RB.1"),
            new CircuitPostMeasurementEndpoint(baseResistor, 0), baseResistorLead1);
        Point baseSecondaryPublic = baseSecondaryPath.getPublicTerminal().getElement().getPost(1);
        WireElm baseResistorLead2 = new WireElm(baseSecondaryPublic.x, baseSecondaryPublic.y);
        baseResistorLead2.drag(baseTrace.getPost(1).x, baseTrace.getPost(1).y);
        connectionBindings.bind("RB", "RB.2", bindings.getEndpoint("RB.2"),
            new CircuitPostMeasurementEndpoint(baseSecondaryPath.getSimulationElement(), 1),
            baseResistorLead2);
        connectionBindings.bind("Q1", "Q1.B", bindings.getEndpoint("Q1.B"),
            transistorFaultBinding == null ? new CircuitPostMeasurementEndpoint(transistor, 0) :
                transistorFaultBinding.getPublicTerminal(transistor, 0),
            transistorBaseAttachment);
        connectionBindings.bind("Q1", "Q1.C", bindings.getEndpoint("Q1.C"),
            transistorFaultBinding == null ? new CircuitPostMeasurementEndpoint(transistor, 1) :
                transistorFaultBinding.getPublicTerminal(transistor, 1),
            transistorCollectorAttachment);
        connectionBindings.bind("Q1", "Q1.E", bindings.getEndpoint("Q1.E"),
            transistorFaultBinding == null ? new CircuitPostMeasurementEndpoint(transistor, 2) :
                transistorFaultBinding.getPublicTerminal(transistor, 2),
            transistorEmitterAttachment);
        elements.add(loadResistorLead2);
        elements.add(baseResistorLead2);

        PhysicalBoardRuntime runtime = new PhysicalBoardRuntime(board);
        PhysicalBoardSlot j1Slot = runtime.createSlot("J1");
        PhysicalBoardSlot j2Slot = runtime.createSlot("J2");
        PhysicalBoardSlot loadSlot = runtime.createSlot("RLOAD");
        PhysicalBoardSlot baseSlot = runtime.createSlot("RB");
        PhysicalBoardSlot pullDownSlot = runtime.createSlot("RPD");
        PhysicalBoardSlot ledSlot = runtime.createSlot("LED1");
        PhysicalBoardSlot transistorSlot = runtime.createSlot("Q1");

        ResistorReplacementCatalog loadCatalog = new ResistorReplacementCatalog();
        ResistorReplacementCatalog baseCatalog = new ResistorReplacementCatalog();
        PhysicalPartInventory<PhysicalResistorPart> loadInventory =
            new PhysicalPartInventory<PhysicalResistorPart>(runtime, "RLOAD_REPLACEMENTS",
                PhysicalResistorPart.class);
        PhysicalPartInventory<PhysicalResistorPart> baseInventory =
            new PhysicalPartInventory<PhysicalResistorPart>(runtime, "RB_REPLACEMENTS",
                PhysicalResistorPart.class);
        PhysicalPartInventory<PhysicalNpnPart> transistorInventory =
            new PhysicalPartInventory<PhysicalNpnPart>(runtime, "Q1_REPLACEMENTS",
                PhysicalNpnPart.class);
        ResistorNameplate loadSpec = StandardPhysicalDefinitionProviders.RESISTOR.require(
            specifications, "RLOAD");
        ResistorNameplate baseSpec = StandardPhysicalDefinitionProviders.RESISTOR.require(
            specifications, "RB");
        PhysicalResistorPart originalLoad = new PhysicalResistorPart("RLOAD_ORIGINAL",
            loadSpec, loadSpec, new PhysicalNameplate("RLOAD_ORIGINAL", "Physical resistor markings",
                "Markings", "Color bands"), loadResistor, loadFaultBinding, loadSecondaryPath,
            ResistorPartLocation.INSTALLED, new PhysicalPartProvenance(
                PhysicalPartProvenance.GENERATED_ORIGINAL, "RLOAD"));
        PhysicalResistorPart originalBase = new PhysicalResistorPart("RB_ORIGINAL", baseSpec,
            baseSpec, new PhysicalNameplate("RB_ORIGINAL", "Physical resistor markings",
                "Markings", "Color bands"), baseResistor, baseFaultBinding, baseSecondaryPath,
            ResistorPartLocation.INSTALLED, new PhysicalPartProvenance(
                PhysicalPartProvenance.GENERATED_ORIGINAL, "RB"));
        loadInventory.add(originalLoad);
        baseInventory.add(originalBase);
        ReplaceableComponentSlot loadComponentSlot = new ReplaceableComponentSlot("RLOAD",
            loadSpec, originalLoad, loadResistorLead1, loadResistorLead2, loadSlot);
        ReplaceableComponentSlot baseComponentSlot = new ReplaceableComponentSlot("RB", baseSpec,
            originalBase, baseResistorLead1, baseResistorLead2, baseSlot);
        runtime.registerCapability(new ReplaceableResistorBoardCapability("REPLACEABLE_RESISTOR_RLOAD",
            loadComponentSlot, loadInventory, loadCatalog));
        runtime.registerCapability(new ReplaceableResistorBoardCapability("REPLACEABLE_RESISTOR_RB",
            baseComponentSlot, baseInventory, baseCatalog));

        PhysicalResistorPart pullDownPart = new PhysicalResistorPart("RPD_ORIGINAL",
            StandardPhysicalDefinitionProviders.RESISTOR.require(specifications, "RPD"),
            StandardPhysicalDefinitionProviders.RESISTOR.require(specifications, "RPD"),
            basePullDown, null, null, ResistorPartLocation.INSTALLED,
            new PhysicalPartProvenance(PhysicalPartProvenance.FIXED_GENERATED, "RPD"));
        pullDownSlot.install(pullDownPart);
        LedNameplate ledSpec = StandardPhysicalDefinitionProviders.LED.require(specifications,
            "LED1");
        PhysicalLedPart ledPart = new PhysicalLedPart("LED1_ORIGINAL", ledSpec, ledSpec,
            new PhysicalNameplate("LED1_ORIGINAL", ledSpec.getDisplayName()), led, false,
            LedPartLocation.INSTALLED, new PhysicalPartProvenance(
                PhysicalPartProvenance.FIXED_GENERATED, "LED1"));
        // Fixed parts still have stable physical identities; their attachments are not
        // detachable component graph edges in this bounded family.
        ledSlot.install(ledPart);

        NpnSpecification transistorSpec = StandardPhysicalDefinitionProviders.NPN.require(
            specifications, "Q1");
        PhysicalNameplate originalTransistorNameplate = new PhysicalNameplate("Q1_ORIGINAL",
            "Generic NPN transistor", "Part", "Generic NPN transistor");
        PhysicalNpnPart originalTransistor = new PhysicalNpnPart("Q1_ORIGINAL", transistorSpec,
            originalTransistorNameplate, transistor, transistorFaultBinding,
            NpnPartLocation.INSTALLED, new PhysicalPartProvenance(
                PhysicalPartProvenance.GENERATED_ORIGINAL, "Q1"));
        transistorInventory.add(originalTransistor);
        NpnComponentSlot transistorComponentSlot = new NpnComponentSlot("Q1", transistorSpec,
            originalTransistor, transistorBaseAttachment, transistorCollectorAttachment,
            transistorEmitterAttachment, transistorSlot);
        NpnReplacementCatalog transistorCatalog = new NpnReplacementCatalog();
        runtime.registerCapability(new ReplaceableNpnBoardCapability(transistorComponentSlot,
            transistorInventory, transistorCatalog));

        j1Slot.install(PhysicalFoundationPartFactory.fromBoardBindings("J1",
            (BasicPhysicalSpecification) specifications.getSpecification("J1"),
            specifications.getNameplate("J1"), PhysicalPackages.THROUGH_HOLE_CONNECTOR_2,
            bindings, loadIsolation, new PhysicalPartProvenance(
                PhysicalPartProvenance.FIXED_GENERATED, "J1")));
        j2Slot.install(PhysicalFoundationPartFactory.fromBoardBindings("J2",
            (BasicPhysicalSpecification) specifications.getSpecification("J2"),
            specifications.getNameplate("J2"), PhysicalPackages.THROUGH_HOLE_CONNECTOR_2,
            bindings, controlCommand, new PhysicalPartProvenance(
                PhysicalPartProvenance.FIXED_GENERATED, "J2")));

        GeneratedExternalPowerBindings powerBindings = new GeneratedExternalPowerBindings(board);
        Vector<CircuitElm> loadPowerElements = new Vector<CircuitElm>();
        loadPowerElements.add(loadSupply); loadPowerElements.add(loadIsolation);
        powerBindings.bindPowerInput("LOAD_VIN_INPUT", new ExternalPowerSimulationBinding(
            loadPowerElements, new SwitchExternalPowerControl(loadIsolation)));
        Vector<CircuitElm> controlPowerElements = new Vector<CircuitElm>();
        controlPowerElements.add(controlSupply); controlPowerElements.add(controlIsolation);
        powerBindings.bindPowerInput("CONTROL_VIN_INPUT", new ExternalPowerSimulationBinding(
            controlPowerElements, new SwitchExternalPowerControl(controlIsolation)));

        NpnLowSideSwitchFamilyState familyState = new NpnLowSideSwitchFamilyState(controlCommand);
        GeneratedChallengeBehaviorContract behaviorContract = new GeneratedChallengeBehaviorAdapter(
            new NpnLowSideSwitchGeneratedBoardValidator(), new NpnLowSideSwitchFaultValidator(),
            new NpnLowSideSwitchRepairValidator());
        GeneratedScenarioCatalog<GeneratedObservedBehavior> scenarios =
            GeneratedScenarioLibrary.npnLowSideSwitch();
        PcbBoardLayout layout = NpnLowSideSwitchPcbLayoutFactory.create(board, specifications,
            seed);
        GeneratedChallengeDefinition challenge = new GeneratedChallengeDefinition(
            "NPN_LOW_SIDE_SWITCH_CHALLENGE", FAMILY_ID, TOPOLOGY_VARIANT, seed, scenarios,
            "Repair verified. The controlled load switches normally.", fault, faultBinding,
            behaviorContract);
        return new GeneratedBoardInstance(board, elements, seed, FAMILY_ID, TOPOLOGY_VARIANT,
            "Generated NPN low-side switch, seed " + seed, componentBindings, powerBindings,
            connectionBindings, behaviorContract, layout, specifications, faultBinding,
            operationalStates, challenge, familyState, runtime, null, forcedType != null,
            candidates);
    }

    private GeneratedFaultBinding targetBinding(GeneratedFault fault, GeneratedFaultBinding binding,
            String componentId) {
        return fault.getTargetComponentId().equals(componentId) ? binding : null;
    }

    private CircuitPostMeasurementEndpoint secondaryUpstream(GeneratedFaultBinding binding,
            ResistorElm resistor, SwitchElm switchElement) {
        if (binding != null)
            return (CircuitPostMeasurementEndpoint) binding.getPublicTerminal(resistor, 1);
        return new CircuitPostMeasurementEndpoint(switchElement, 1);
    }

    private void add(Vector<CircuitElm> elements, CircuitElm... values) {
        for (CircuitElm value : values) elements.add(value);
    }

    private BoardPhysicalSpecifications createSpecifications(NpnValues values) {
        BoardPhysicalSpecifications specifications = new BoardPhysicalSpecifications();
        specifications.addPhysicalDefinition("J1", new BasicPhysicalSpecification("J1_CONNECTOR"),
            new PhysicalNameplate("J1", "Load supply connector"),
            PhysicalPackages.THROUGH_HOLE_CONNECTOR_2);
        specifications.addPhysicalDefinition("J2", new BasicPhysicalSpecification("J2_CONNECTOR"),
            new PhysicalNameplate("J2", "Control input connector"),
            PhysicalPackages.THROUGH_HOLE_CONNECTOR_2);
        StandardPhysicalDefinitionProviders.RESISTOR.add(specifications,
            new ResistorNameplate("RLOAD", values.loadResistanceOhms, 5, .5));
        StandardPhysicalDefinitionProviders.RESISTOR.add(specifications,
            new ResistorNameplate("RB", values.baseResistanceOhms, 5, .25));
        StandardPhysicalDefinitionProviders.RESISTOR.add(specifications,
            new ResistorNameplate("RPD", values.pullDownResistanceOhms, 5, .25));
        StandardPhysicalDefinitionProviders.LED.add(specifications,
            new LedNameplate("LED1", "Generic red LED", "default-led", 1, 0, 0));
        StandardPhysicalDefinitionProviders.NPN.add(specifications,
            new NpnSpecification("Q1", values.beta));
        specifications.addPowerInputNameplate(new PowerInputNameplate("LOAD_VIN_INPUT",
            values.loadSupplyVoltage));
        specifications.addPowerInputNameplate(new PowerInputNameplate("CONTROL_VIN_INPUT",
            values.controlSupplyVoltage));
        return specifications;
    }

    private TroubleshootBoard createBoard() {
        TroubleshootBoard board = new TroubleshootBoard(FAMILY_ID);
        board.addNet(new BoardNet("LOAD_SUPPLY"));
        board.addNet(new BoardNet("CONTROL_INPUT"));
        board.addNet(new BoardNet("BASE"));
        board.addNet(new BoardNet("LOAD_NODE"));
        board.addNet(new BoardNet("COLLECTOR"));
        board.addNet(new BoardNet("GND"));
        board.addComponent(new BoardComponent("J1", "CONNECTOR"));
        board.addComponent(new BoardComponent("J2", "CONNECTOR"));
        board.addComponent(new BoardComponent("RLOAD", "RESISTOR"));
        board.addComponent(new BoardComponent("RB", "RESISTOR"));
        board.addComponent(new BoardComponent("RPD", "RESISTOR"));
        board.addComponent(new BoardComponent("LED1", "LED"));
        board.addComponent(new BoardComponent("Q1", "NPN_TRANSISTOR"));
        addPad(board, "J1.1", "J1", "1", "LOAD_SUPPLY");
        addPad(board, "J1.2", "J1", "2", "GND");
        addPad(board, "J2.1", "J2", "1", "CONTROL_INPUT");
        addPad(board, "J2.2", "J2", "2", "GND");
        addPad(board, "RLOAD.1", "RLOAD", "1", "LOAD_SUPPLY");
        addPad(board, "RLOAD.2", "RLOAD", "2", "LOAD_NODE");
        addPad(board, "RB.1", "RB", "1", "CONTROL_INPUT");
        addPad(board, "RB.2", "RB", "2", "BASE");
        addPad(board, "RPD.1", "RPD", "1", "BASE");
        addPad(board, "RPD.2", "RPD", "2", "GND");
        addPad(board, "LED1.A", "LED1", "A", "LOAD_NODE");
        addPad(board, "LED1.K", "LED1", "K", "COLLECTOR");
        addPad(board, "Q1.B", "Q1", "B", "BASE");
        addPad(board, "Q1.C", "Q1", "C", "COLLECTOR");
        addPad(board, "Q1.E", "Q1", "E", "GND");
        board.addPowerInput(new ExternalBoardPowerInput("LOAD_VIN_INPUT", "J1.1", "J1.2",
            "LOAD_SUPPLY", "GND"));
        board.addPowerInput(new ExternalBoardPowerInput("CONTROL_VIN_INPUT", "J2.1", "J2.2",
            "CONTROL_INPUT", "GND"));
        board.validate();
        return board;
    }

    private void addPad(TroubleshootBoard board, String id, String componentId,
            String terminal, String net) {
        board.addPad(new BoardPad(id, componentId, terminal, net));
    }

    private NpnValues valuesFor(long seed) {
        long normalized = seed % 3;
        if (normalized < 0) normalized += 3;
        double supply = normalized == 0 ? 9 : normalized == 1 ? 12 : 5;
        return new NpnValues(supply, 5, 330, 1000, 100000, 100);
    }

    private static final class NpnValues {
        final double loadSupplyVoltage;
        final double controlSupplyVoltage;
        final double loadResistanceOhms;
        final double baseResistanceOhms;
        final double pullDownResistanceOhms;
        final double beta;

        NpnValues(double loadSupplyVoltage, double controlSupplyVoltage,
                double loadResistanceOhms, double baseResistanceOhms,
                double pullDownResistanceOhms, double beta) {
            this.loadSupplyVoltage = loadSupplyVoltage;
            this.controlSupplyVoltage = controlSupplyVoltage;
            this.loadResistanceOhms = loadResistanceOhms;
            this.baseResistanceOhms = baseResistanceOhms;
            this.pullDownResistanceOhms = pullDownResistanceOhms;
            this.beta = beta;
        }
    }
}
