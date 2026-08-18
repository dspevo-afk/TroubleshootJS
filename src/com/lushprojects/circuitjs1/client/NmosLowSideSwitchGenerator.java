package com.lushprojects.circuitjs1.client;

import java.util.Vector;

/**
 * Deterministic NMOS low-side family.  CircuitJS NMosfetElm is the electrical
 * source of truth; board IDs and physical terminal identities are adapters.
 * At this binding boundary the legacy MosfetElm mapping is preserved:
 * post 0 = G, post 1 = S, post 2 = D, with the default body diode enabled and
 * no body terminal exposed (getPostCount() == 3).
 */
final class NmosLowSideSwitchGenerator {
    static final String FAMILY_ID = "NMOS_LOW_SIDE_SWITCH";
    static final String TOPOLOGY_VARIANT = "NMOS_LOW_SIDE";

    GeneratedBoardInstance generate(long seed) { return generateInternal(seed, null); }

    GeneratedBoardInstance generateForFaultVerification(long seed, GeneratedFaultType type) {
        if (type != GeneratedFaultType.NMOS_DS_OPEN && type != GeneratedFaultType.NMOS_DS_SHORT &&
                type != GeneratedFaultType.NMOS_GATE_OPEN)
            throw new IllegalArgumentException("Unsupported NMOS verification fault: " + type);
        return generateInternal(seed, type);
    }

    private GeneratedBoardInstance generateInternal(long seed, GeneratedFaultType forcedType) {
        NmosValues values = valuesFor(seed);
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
        WireElm loadResistorLead2 = new WireElm(384, 176);
        loadResistorLead2.drag(480, 176);
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
        WireElm gateDriveTrace = new WireElm(272, 96);
        gateDriveTrace.drag(336, 96);
        WireElm gateDriveLink = new WireElm(336, 96);
        gateDriveLink.drag(496, 96);
        WireElm gateNodeTrace = new WireElm(496, 96);
        gateNodeTrace.drag(544, 288);
        ResistorElm gatePullDown = new ResistorElm(496, 96);
        gatePullDown.drag(496, 176);
        gatePullDown.setResistance(values.pullDownResistanceOhms);

        NMosfetElm mosfet = new NMosfetElm(592, 288);
        mosfet.drag(672, 288);
        mosfet.vt = values.thresholdVoltage;
        mosfet.beta = values.beta;
        Point gate = mosfet.getPost(0);
        Point source = mosfet.getPost(1);
        Point drain = mosfet.getPost(2);
        // Actual post order is G/S/D; physical package order is G/D/S.
        SwitchElm gateFaultSwitch = new SwitchElm(gate.x, gate.y);
        gateFaultSwitch.drag(gate.x + 32, gate.y);
        SwitchElm drainFaultSwitch = new SwitchElm(drain.x, drain.y);
        drainFaultSwitch.drag(drain.x + 32, drain.y);
        WireElm gateAttachment = new WireElm(544, 288);
        gateAttachment.drag(gate.x, gate.y);
        WireElm drainTrace = new WireElm(led.getPost(1).x, led.getPost(1).y);
        drainTrace.drag(drain.x - 48, drain.y);
        WireElm drainAttachment = new WireElm(drain.x - 48, drain.y);
        drainAttachment.drag(drain.x, drain.y);
        WireElm sourceTrace = new WireElm(704, 416);
        sourceTrace.drag(source.x - 48, source.y);
        WireElm sourceAttachment = new WireElm(source.x - 48, source.y);
        sourceAttachment.drag(source.x, source.y);
        ResistorElm dsFaultShunt = new ResistorElm(drain.x, drain.y);
        dsFaultShunt.drag(drain.x + 32, drain.y);
        SwitchElm dsFaultBoardPath = new SwitchElm(drain.x + 32, drain.y);
        dsFaultBoardPath.drag(source.x, source.y);

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
            loadResistor, loadResistorLead2, loadNodeTrace, led, controlSupply,
            controlIsolation, controlInputTrace, controlCommand, gateDriveTrace,
            gateDriveLink, gateNodeTrace, gatePullDown, mosfet, gateFaultSwitch,
            drainTrace, sourceTrace, dsFaultShunt, ground, loadReturn, controlReturn,
            pullDownReturn);

        GeneratedComponentBindings componentBindings = new GeneratedComponentBindings(board);
        componentBindings.bindComponent("RLOAD", loadResistor);
        componentBindings.bindComponent("RPD", gatePullDown);
        componentBindings.bindComponent("LED1", led);
        componentBindings.bindComponent("Q1", mosfet);
        GeneratedComponentOperationalStates operationalStates =
            new GeneratedComponentOperationalStates();
        operationalStates.bindLed("LED1", led);

        Vector<GeneratedFaultCandidate> candidates = new Vector<GeneratedFaultCandidate>();
        candidates.add(GeneratedFaultEngine.nmosDsOpen("NMOS_Q1_DS_OPEN", FAMILY_ID, seed,
            "Q1", drainFaultSwitch));
        candidates.add(GeneratedFaultEngine.nmosDsShort("NMOS_Q1_DS_SHORT", FAMILY_ID, seed,
            "Q1", dsFaultShunt, dsFaultBoardPath));
        candidates.add(GeneratedFaultEngine.nmosGateOpen("NMOS_Q1_GATE_OPEN", FAMILY_ID, seed,
            "Q1", gateFaultSwitch));
        GeneratedFaultBinding selectedBinding = null;
        GeneratedFaultCandidate selected = forcedType == null ?
            GeneratedFaultEngine.select(seed, candidates) :
            GeneratedFaultEngine.select(forcedType, candidates);
        GeneratedFaultEngine.clearAll(candidates);
        for (GeneratedFaultCandidate candidate : candidates)
            for (CircuitElm privateElement : candidate.getPrivateSimulationElements())
                if (!elements.contains(privateElement)) elements.add(privateElement);
        selectedBinding = selected.getBinding();
        GeneratedFault fault = selected.getFault();

        BoardSimulationBindings bindings = board.getSimulationBindings();
        bindings.bindPad("J1.1", new CircuitPostMeasurementEndpoint(loadInputTrace, 0));
        bindings.bindPad("J1.2", new CircuitPostMeasurementEndpoint(ground, 0));
        bindings.bindPad("RLOAD.1", new CircuitPostMeasurementEndpoint(loadInputTrace, 1));
        bindings.bindPad("RLOAD.2", new CircuitPostMeasurementEndpoint(loadNodeTrace, 0));
        bindings.bindPad("LED1.A", new CircuitPostMeasurementEndpoint(loadNodeTrace, 1));
        bindings.bindPad("LED1.K", new CircuitPostMeasurementEndpoint(drainTrace, 0));
        bindings.bindPad("J2.1", new CircuitPostMeasurementEndpoint(controlInputTrace, 1));
        bindings.bindPad("J2.2", new CircuitPostMeasurementEndpoint(ground, 0));
        bindings.bindPad("TP1.1", new CircuitPostMeasurementEndpoint(gateDriveTrace, 0));
        bindings.bindPad("TP1.2", new CircuitPostMeasurementEndpoint(gateDriveTrace, 1));
        bindings.bindPad("TP2.1", new CircuitPostMeasurementEndpoint(controlInputTrace, 0));
        bindings.bindPad("TP2.2", new CircuitPostMeasurementEndpoint(controlInputTrace, 1));
        bindings.bindPad("RPD.1", new CircuitPostMeasurementEndpoint(gateNodeTrace, 0));
        bindings.bindPad("RPD.2", new CircuitPostMeasurementEndpoint(ground, 0));
        bindings.bindPad("Q1.G", new CircuitPostMeasurementEndpoint(gateNodeTrace, 1));
        bindings.bindPad("Q1.D", new CircuitPostMeasurementEndpoint(drainTrace, 1));
        bindings.bindPad("Q1.S", new CircuitPostMeasurementEndpoint(sourceTrace, 1));

        GeneratedComponentConnectionBindings connectionBindings =
            new GeneratedComponentConnectionBindings(board);
        connectionBindings.bind("Q1", "Q1.G", bindings.getEndpoint("Q1.G"),
            selectedBinding.getPublicTerminal(mosfet, 0), gateAttachment);
        connectionBindings.bind("Q1", "Q1.D", bindings.getEndpoint("Q1.D"),
            selectedBinding.getPublicTerminal(mosfet, 2), drainAttachment);
        connectionBindings.bind("Q1", "Q1.S", bindings.getEndpoint("Q1.S"),
            selectedBinding.getPublicTerminal(mosfet, 1), sourceAttachment);
        elements.add(gateAttachment);
        elements.add(drainAttachment);
        elements.add(sourceAttachment);

        PhysicalBoardRuntime runtime = new PhysicalBoardRuntime(board);
        PhysicalBoardSlot j1Slot = runtime.createSlot("J1");
        PhysicalBoardSlot j2Slot = runtime.createSlot("J2");
        PhysicalBoardSlot tp1Slot = runtime.createSlot("TP1");
        PhysicalBoardSlot tp2Slot = runtime.createSlot("TP2");
        PhysicalBoardSlot loadSlot = runtime.createSlot("RLOAD");
        PhysicalBoardSlot pullDownSlot = runtime.createSlot("RPD");
        PhysicalBoardSlot ledSlot = runtime.createSlot("LED1");
        PhysicalBoardSlot qSlot = runtime.createSlot("Q1");

        ResistorNameplate loadSpec = StandardPhysicalDefinitionProviders.RESISTOR.require(
            specifications, "RLOAD");
        ResistorNameplate pullDownSpec = StandardPhysicalDefinitionProviders.RESISTOR.require(
            specifications, "RPD");
        PhysicalResistorPart loadPart = new PhysicalResistorPart("RLOAD_ORIGINAL", loadSpec,
            loadSpec, new PhysicalNameplate("RLOAD_ORIGINAL", "Physical resistor markings",
            "Markings", "Color bands"), loadResistor, null, null, ResistorPartLocation.INSTALLED,
            new PhysicalPartProvenance(PhysicalPartProvenance.FIXED_GENERATED, "RLOAD"));
        loadSlot.install(loadPart);
        PhysicalResistorPart pullDownPart = new PhysicalResistorPart("RPD_ORIGINAL", pullDownSpec,
            pullDownSpec, new PhysicalNameplate("RPD_ORIGINAL", "Physical resistor markings",
            "Markings", "Color bands"), gatePullDown, null, null, ResistorPartLocation.INSTALLED,
            new PhysicalPartProvenance(PhysicalPartProvenance.FIXED_GENERATED, "RPD"));
        pullDownSlot.install(pullDownPart);
        LedNameplate ledSpec = StandardPhysicalDefinitionProviders.LED.require(specifications,
            "LED1");
        ledSlot.install(new PhysicalLedPart("LED1_ORIGINAL", ledSpec, ledSpec, led,
            false, LedPartLocation.INSTALLED, new PhysicalPartProvenance(
                PhysicalPartProvenance.FIXED_GENERATED, "LED1")));

        NmosSpecification mosfetSpec = StandardPhysicalDefinitionProviders.NMOS.require(
            specifications, "Q1");
        PhysicalNmosPart originalMosfet = new PhysicalNmosPart("Q1_ORIGINAL", mosfetSpec,
            new PhysicalNameplate("Q1_ORIGINAL", "Generic N-channel MOSFET", "Part",
                "Generic N-channel MOSFET"), mosfet, selectedBinding, NmosPartLocation.INSTALLED,
            new PhysicalPartProvenance(PhysicalPartProvenance.GENERATED_ORIGINAL, "Q1"));
        PhysicalPartInventory<PhysicalNmosPart> mosfetInventory =
            new PhysicalPartInventory<PhysicalNmosPart>(runtime, "Q1_REPLACEMENTS",
                PhysicalNmosPart.class);
        mosfetInventory.add(originalMosfet);
        NmosComponentSlot mosfetComponentSlot = new NmosComponentSlot("Q1", mosfetSpec,
            originalMosfet, gateAttachment, drainAttachment, sourceAttachment, qSlot);
        runtime.registerCapability(new ReplaceableNmosBoardCapability(mosfetComponentSlot,
            mosfetInventory, new NmosReplacementCatalog()));

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
        tp1Slot.install(PhysicalFoundationPartFactory.fromBoardBindings("TP1",
            (BasicPhysicalSpecification) specifications.getSpecification("TP1"),
            specifications.getNameplate("TP1"), PhysicalPackages.THROUGH_HOLE_OUTPUT_HEADER_2,
            bindings, gateDriveTrace, new PhysicalPartProvenance(
                PhysicalPartProvenance.FIXED_GENERATED, "TP1")));
        tp2Slot.install(PhysicalFoundationPartFactory.fromBoardBindings("TP2",
            (BasicPhysicalSpecification) specifications.getSpecification("TP2"),
            specifications.getNameplate("TP2"), PhysicalPackages.THROUGH_HOLE_OUTPUT_HEADER_2,
            bindings, controlInputTrace, new PhysicalPartProvenance(
                PhysicalPartProvenance.FIXED_GENERATED, "TP2")));

        GeneratedExternalPowerBindings powerBindings = new GeneratedExternalPowerBindings(board);
        Vector<CircuitElm> loadPowerElements = new Vector<CircuitElm>();
        loadPowerElements.add(loadSupply); loadPowerElements.add(loadIsolation);
        powerBindings.bindPowerInput("LOAD_VIN_INPUT", new ExternalPowerSimulationBinding(
            loadPowerElements, new SwitchExternalPowerControl(loadIsolation)));
        Vector<CircuitElm> controlPowerElements = new Vector<CircuitElm>();
        controlPowerElements.add(controlSupply); controlPowerElements.add(controlIsolation);
        powerBindings.bindPowerInput("CONTROL_VIN_INPUT", new ExternalPowerSimulationBinding(
            controlPowerElements, new SwitchExternalPowerControl(controlIsolation)));

        NmosLowSideSwitchFamilyState familyState = new NmosLowSideSwitchFamilyState(controlCommand);
        GeneratedChallengeBehaviorContract behaviorContract = new GeneratedChallengeBehaviorAdapter(
            new NmosLowSideSwitchGeneratedBoardValidator(), new NmosLowSideSwitchFaultValidator(),
            new NmosLowSideSwitchRepairValidator());
        GeneratedChallengeDefinition challenge = new GeneratedChallengeDefinition(
            "NMOS_LOW_SIDE_SWITCH_CHALLENGE", FAMILY_ID, TOPOLOGY_VARIANT, seed,
            GeneratedScenarioLibrary.nmosLowSideSwitch(),
            "Repair verified. The controlled load switches normally.", fault, selectedBinding,
            behaviorContract);
        PcbBoardLayout layout = NmosLowSideSwitchPcbLayoutFactory.create(board, specifications,
            seed);
        return new GeneratedBoardInstance(board, elements, seed, FAMILY_ID, TOPOLOGY_VARIANT,
            "Generated NMOS low-side switch, seed " + seed, componentBindings, powerBindings,
            connectionBindings, behaviorContract, layout, specifications, selectedBinding,
            operationalStates, challenge, familyState, runtime);
    }

    private void add(Vector<CircuitElm> elements, CircuitElm... values) {
        for (CircuitElm value : values) elements.add(value);
    }

    private BoardPhysicalSpecifications createSpecifications(NmosValues values) {
        BoardPhysicalSpecifications specifications = new BoardPhysicalSpecifications();
        specifications.addPhysicalDefinition("J1", new BasicPhysicalSpecification("J1_CONNECTOR"),
            new PhysicalNameplate("J1", "Load supply connector"),
            PhysicalPackages.THROUGH_HOLE_CONNECTOR_2);
        specifications.addPhysicalDefinition("J2", new BasicPhysicalSpecification("J2_CONNECTOR"),
            new PhysicalNameplate("J2", "Control input connector"),
            PhysicalPackages.THROUGH_HOLE_CONNECTOR_2);
        specifications.addPhysicalDefinition("TP1", new BasicPhysicalSpecification("TP1_HEADER"),
            new PhysicalNameplate("TP1", "Gate drive test header"),
            PhysicalPackages.THROUGH_HOLE_OUTPUT_HEADER_2);
        specifications.addPhysicalDefinition("TP2", new BasicPhysicalSpecification("TP2_HEADER"),
            new PhysicalNameplate("TP2", "Control input test header"),
            PhysicalPackages.THROUGH_HOLE_OUTPUT_HEADER_2);
        StandardPhysicalDefinitionProviders.RESISTOR.add(specifications,
            new ResistorNameplate("RLOAD", values.loadResistanceOhms, 5, .5));
        StandardPhysicalDefinitionProviders.RESISTOR.add(specifications,
            new ResistorNameplate("RPD", values.pullDownResistanceOhms, 5, .25));
        StandardPhysicalDefinitionProviders.LED.add(specifications,
            new LedNameplate("LED1", "Generic red LED", "default-led", 1, 0, 0));
        StandardPhysicalDefinitionProviders.NMOS.add(specifications,
            new NmosSpecification("Q1", values.thresholdVoltage, values.beta));
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
        board.addNet(new BoardNet("GATE_DRIVE"));
        board.addNet(new BoardNet("GATE"));
        board.addNet(new BoardNet("LOAD_NODE"));
        board.addNet(new BoardNet("DRAIN"));
        board.addNet(new BoardNet("GND"));
        board.addComponent(new BoardComponent("J1", "CONNECTOR"));
        board.addComponent(new BoardComponent("J2", "CONNECTOR"));
        board.addComponent(new BoardComponent("TP1", "OUTPUT_HEADER",
            PhysicalPackages.THROUGH_HOLE_OUTPUT_HEADER_2));
        board.addComponent(new BoardComponent("TP2", "OUTPUT_HEADER",
            PhysicalPackages.THROUGH_HOLE_OUTPUT_HEADER_2));
        board.addComponent(new BoardComponent("RLOAD", "RESISTOR"));
        board.addComponent(new BoardComponent("RPD", "RESISTOR"));
        board.addComponent(new BoardComponent("LED1", "LED"));
        board.addComponent(new BoardComponent("Q1", "NMOS_TRANSISTOR"));
        addPad(board, "J1.1", "J1", "1", "LOAD_SUPPLY");
        addPad(board, "J1.2", "J1", "2", "GND");
        addPad(board, "J2.1", "J2", "1", "CONTROL_INPUT");
        addPad(board, "J2.2", "J2", "2", "GND");
        addPad(board, "TP1.1", "TP1", "1", "GATE_DRIVE");
        addPad(board, "TP1.2", "TP1", "2", "GATE_DRIVE");
        addPad(board, "TP2.1", "TP2", "1", "CONTROL_INPUT");
        addPad(board, "TP2.2", "TP2", "2", "CONTROL_INPUT");
        addPad(board, "RLOAD.1", "RLOAD", "1", "LOAD_SUPPLY");
        addPad(board, "RLOAD.2", "RLOAD", "2", "LOAD_NODE");
        addPad(board, "RPD.1", "RPD", "1", "GATE");
        addPad(board, "RPD.2", "RPD", "2", "GND");
        addPad(board, "LED1.A", "LED1", "A", "LOAD_NODE");
        addPad(board, "LED1.K", "LED1", "K", "DRAIN");
        addPad(board, "Q1.G", "Q1", "G", "GATE");
        addPad(board, "Q1.D", "Q1", "D", "DRAIN");
        addPad(board, "Q1.S", "Q1", "S", "GND");
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

    private NmosValues valuesFor(long seed) {
        long normalized = seed % 3;
        if (normalized < 0) normalized += 3;
        double supply = normalized == 0 ? 9 : normalized == 1 ? 12 : 5;
        return new NmosValues(supply, 5, 330, 100000, 1.5, 10);
    }

    private static final class NmosValues {
        final double loadSupplyVoltage;
        final double controlSupplyVoltage;
        final double loadResistanceOhms;
        final double pullDownResistanceOhms;
        final double thresholdVoltage;
        final double beta;

        NmosValues(double loadSupplyVoltage, double controlSupplyVoltage,
                double loadResistanceOhms, double pullDownResistanceOhms,
                double thresholdVoltage, double beta) {
            this.loadSupplyVoltage = loadSupplyVoltage;
            this.controlSupplyVoltage = controlSupplyVoltage;
            this.loadResistanceOhms = loadResistanceOhms;
            this.pullDownResistanceOhms = pullDownResistanceOhms;
            this.thresholdVoltage = thresholdVoltage;
            this.beta = beta;
        }
    }
}
