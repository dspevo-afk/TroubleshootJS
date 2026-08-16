package com.lushprojects.circuitjs1.client;

import java.util.Random;
import java.util.Vector;

/** Generates the first real shared-node parallel TroubleshootJS family. */
class ParallelDualIndicatorGenerator {
    static final String DUAL_PARALLEL_BRANCHES_VARIANT = "DUAL_PARALLEL_BRANCHES";
    private static final String FAMILY_ID = "PARALLEL_DUAL_INDICATOR";
    private static final SeededPcbLayoutGenerator PCB_LAYOUT_GENERATOR =
        new SeededPcbLayoutGenerator();
    private static final double[] SUPPLY_VOLTAGES = { 5, 9, 12 };
    private static final double[] R1_VALUES = { 330, 680, 1000 };
    private static final double[] R2_VALUES = { 680, 1500, 2200 };

    GeneratedBoardInstance generate(long seed) {
        Random random = new Random(seed);
        int valueIndex = random.nextInt(SUPPLY_VOLTAGES.length);
        double supplyVoltage = SUPPLY_VOLTAGES[valueIndex];
        double r1Value = R1_VALUES[valueIndex];
        double r2Value = R2_VALUES[valueIndex];

        TroubleshootBoard board = createBoard();
        BoardPhysicalSpecifications physicalSpecifications = new BoardPhysicalSpecifications();
        physicalSpecifications.addResistorNameplate(new ResistorNameplate("R1", r1Value, 5));
        physicalSpecifications.addResistorNameplate(new ResistorNameplate("R2", r2Value, 5));
        physicalSpecifications.addPowerInputNameplate(new PowerInputNameplate("VIN_INPUT",
            supplyVoltage));
        LedNameplate led1Nameplate = new LedNameplate("LED1", "Generic red LED", "default-led",
            1, 0, 0);
        LedNameplate led2Nameplate = new LedNameplate("LED2", "Generic red LED", "default-led",
            1, 0, 0);
        physicalSpecifications.addLedNameplate(led1Nameplate);
        physicalSpecifications.addLedNameplate(led2Nameplate);

        DCVoltageElm supply = new DCVoltageElm(128, 528);
        supply.drag(128, 128);
        supply.maxVoltage = supplyVoltage;
        SwitchElm isolationSwitch = new SwitchElm(128, 128);
        isolationSwitch.drag(224, 128);
        SwitchElm connectorFaultSwitch = new SwitchElm(224, 128);
        connectorFaultSwitch.drag(256, 128);
        WireElm vinTrace = new WireElm(256, 128);
        vinTrace.drag(320, 128);

        WireElm r1Lead1Link = new WireElm(320, 128);
        r1Lead1Link.drag(384, 208);
        ResistorElm r1 = new ResistorElm(384, 208);
        r1.drag(448, 208);
        r1.setResistance(r1Value);
        SwitchElm r1FaultIsolation = new SwitchElm(448, 208);
        r1FaultIsolation.drag(480, 208);
        WireElm r1Lead2Link = new WireElm(480, 208);
        r1Lead2Link.drag(576, 208);
        WireElm branch1Trace = new WireElm(576, 208);
        branch1Trace.drag(608, 208);
        WireElm branch1AnodeLink = new WireElm(608, 208);
        branch1AnodeLink.drag(640, 208);
        LEDElm led1 = createLed(640, 208, 720, 208, led1Nameplate);
        WireElm led1CathodeLink = new WireElm(608, 448);
        led1CathodeLink.drag(720, 208);

        WireElm r2Lead1Link = new WireElm(320, 128);
        r2Lead1Link.drag(384, 368);
        ResistorElm r2 = new ResistorElm(384, 368);
        r2.drag(448, 368);
        r2.setResistance(r2Value);
        WireElm r2Lead2Link = new WireElm(448, 368);
        r2Lead2Link.drag(576, 368);
        WireElm branch2Trace = new WireElm(576, 368);
        branch2Trace.drag(608, 368);
        WireElm branch2AnodeLink = new WireElm(608, 368);
        branch2AnodeLink.drag(640, 368);
        LEDElm led2 = createLed(640, 368, 720, 368, led2Nameplate);
        WireElm led2CathodeLink = new WireElm(608, 448);
        led2CathodeLink.drag(720, 368);

        WireElm groundTrace = new WireElm(608, 448);
        groundTrace.drag(800, 448);
        GroundElm ground = new GroundElm(800, 448);
        ground.drag(800, 480);
        WireElm supplyReturn = new WireElm(128, 528);
        supplyReturn.drag(800, 448);

        Vector<CircuitElm> elements = new Vector<CircuitElm>();
        elements.add(supply);
        elements.add(isolationSwitch);
        elements.add(connectorFaultSwitch);
        elements.add(vinTrace);
        elements.add(r1Lead1Link);
        elements.add(r1);
        elements.add(r1Lead2Link);
        elements.add(branch1Trace);
        elements.add(branch1AnodeLink);
        elements.add(led1);
        elements.add(led1CathodeLink);
        elements.add(r2Lead1Link);
        elements.add(r2);
        elements.add(r2Lead2Link);
        elements.add(branch2Trace);
        elements.add(branch2AnodeLink);
        elements.add(led2);
        elements.add(led2CathodeLink);
        elements.add(groundTrace);
        elements.add(ground);
        elements.add(supplyReturn);

        GeneratedComponentBindings componentBindings = new GeneratedComponentBindings(board);
        componentBindings.bindComponent("R1", r1);
        componentBindings.bindComponent("LED1", led1);
        componentBindings.bindComponent("R2", r2);
        componentBindings.bindComponent("LED2", led2);
        GeneratedComponentOperationalStates operationalStates =
            new GeneratedComponentOperationalStates();
        operationalStates.bindLed("LED1", led1);
        operationalStates.bindLed("LED2", led2);

        Vector<GeneratedFaultCandidate> faultCandidates =
            new Vector<GeneratedFaultCandidate>();
        faultCandidates.add(GeneratedFaultEngine.resistorOpen("PARALLEL_R1_OPEN", FAMILY_ID,
            seed, "R1", r1FaultIsolation));
        faultCandidates.add(GeneratedFaultEngine.resistorIncorrectValue(
            "PARALLEL_R1_INCORRECT_VALUE", FAMILY_ID, seed, "R1", r1, r1Value,
            r1Value * 100));
        faultCandidates.add(GeneratedFaultEngine.connectorOpenPath("PARALLEL_J1_OPEN_PATH",
            FAMILY_ID, seed, "J1", connectorFaultSwitch, false));
        GeneratedFaultEngine.clearAll(faultCandidates);
        GeneratedFaultCandidate selectedFault = GeneratedFaultEngine.select(seed, faultCandidates);
        for (GeneratedFaultCandidate candidate : faultCandidates)
            for (CircuitElm privateElement : candidate.getPrivateSimulationElements())
                if (!elements.contains(privateElement))
                    elements.add(privateElement);
        GeneratedFault fault = selectedFault.getFault();
        GeneratedFaultBinding faultBinding = selectedFault.getBinding();
        GeneratedFaultBinding resistorFaultBinding = "R1".equals(
            fault.getTargetComponentId()) ? faultBinding : null;
        ResistorReplacementInventory resistorInventory = new ResistorReplacementInventory();
        ResistorReplacementCatalog resistorCatalog = new ResistorReplacementCatalog();
        CircuitPostMeasurementEndpoint resistorOpenPathUpstream = null;
        if (resistorFaultBinding != null && fault.getType() == GeneratedFaultType.RESISTOR_OPEN)
            resistorOpenPathUpstream = (CircuitPostMeasurementEndpoint)
                resistorFaultBinding.getPublicTerminal(r1, 1);
        if (resistorOpenPathUpstream == null)
            resistorOpenPathUpstream = new CircuitPostMeasurementEndpoint(r1FaultIsolation, 1);
        ResistorSecondaryOpenPath originalOpenPath = ResistorSecondaryOpenPath.create(
            resistorOpenPathUpstream);
        elements.add(originalOpenPath.getSimulationElement());
        PhysicalResistorPart originalR1 = new PhysicalResistorPart("R1_ORIGINAL",
            new ResistorNameplate("R1_ORIGINAL", r1Value, 5), r1, resistorFaultBinding,
            originalOpenPath, ResistorPartLocation.INSTALLED);
        componentBindings.bindAuxiliaryComponentElement("R1",
            originalOpenPath.getSimulationElement());
        resistorInventory.add(originalR1);
        ReplaceableComponentSlot r1Slot = new ReplaceableComponentSlot("R1",
            physicalSpecifications.getResistorNameplate("R1"), originalR1, r1Lead1Link,
            r1Lead2Link);

        GeneratedChallengeBehaviorContract behaviorContract =
            new GeneratedChallengeBehaviorAdapter(
                new ParallelDualIndicatorGeneratedBoardValidator(),
                new ParallelDualIndicatorFaultValidator(),
                new ParallelDualIndicatorRepairValidator());
        GeneratedScenarioCatalog<GeneratedObservedBehavior> scenarios =
            GeneratedScenarioLibrary.parallelIndicators();

        GeneratedExternalPowerBindings powerBindings = new GeneratedExternalPowerBindings(board);
        Vector<CircuitElm> powerElements = new Vector<CircuitElm>();
        powerElements.add(supply);
        powerElements.add(isolationSwitch);
        powerBindings.bindPowerInput("VIN_INPUT", new ExternalPowerSimulationBinding(powerElements,
            new SwitchExternalPowerControl(isolationSwitch)));

        GeneratedComponentConnectionBindings connectionBindings =
            new GeneratedComponentConnectionBindings(board);
        BoardSimulationBindings bindings = board.getSimulationBindings();
        bindings.bindPad("J1.1", new CircuitPostMeasurementEndpoint(connectorFaultSwitch, 1));
        bindings.bindPad("J1.2", new CircuitPostMeasurementEndpoint(groundTrace, 1));
        bindings.bindPad("R1.1", new CircuitPostMeasurementEndpoint(vinTrace, 1));
        bindings.bindPad("R1.2", new CircuitPostMeasurementEndpoint(branch1Trace, 0));
        bindings.bindPad("LED1.A", new CircuitPostMeasurementEndpoint(branch1Trace, 1));
        bindings.bindPad("LED1.K", new CircuitPostMeasurementEndpoint(groundTrace, 0));
        bindings.bindPad("R2.1", new CircuitPostMeasurementEndpoint(vinTrace, 1));
        bindings.bindPad("R2.2", new CircuitPostMeasurementEndpoint(branch2Trace, 0));
        bindings.bindPad("LED2.A", new CircuitPostMeasurementEndpoint(branch2Trace, 1));
        bindings.bindPad("LED2.K", new CircuitPostMeasurementEndpoint(groundTrace, 0));

        connectionBindings.bind("R1", "R1.1", bindings.getEndpoint("R1.1"),
            new CircuitPostMeasurementEndpoint(r1, 0), r1Lead1Link);
        connectionBindings.bind("R1", "R1.2", bindings.getEndpoint("R1.2"),
            originalR1.getPublicTerminal(1), r1Lead2Link);
        connectionBindings.bind("LED1", "LED1.A", bindings.getEndpoint("LED1.A"),
            new CircuitPostMeasurementEndpoint(led1, 0), branch1AnodeLink);
        connectionBindings.bind("LED1", "LED1.K", bindings.getEndpoint("LED1.K"),
            new CircuitPostMeasurementEndpoint(led1, 1), led1CathodeLink);
        connectionBindings.bind("R2", "R2.1", bindings.getEndpoint("R2.1"),
            new CircuitPostMeasurementEndpoint(r2, 0), r2Lead1Link);
        connectionBindings.bind("R2", "R2.2", bindings.getEndpoint("R2.2"),
            new CircuitPostMeasurementEndpoint(r2, 1), r2Lead2Link);
        connectionBindings.bind("LED2", "LED2.A", bindings.getEndpoint("LED2.A"),
            new CircuitPostMeasurementEndpoint(led2, 0), branch2AnodeLink);
        connectionBindings.bind("LED2", "LED2.K", bindings.getEndpoint("LED2.K"),
            new CircuitPostMeasurementEndpoint(led2, 1), led2CathodeLink);

        return new GeneratedBoardInstance(board, elements, seed, FAMILY_ID,
            DUAL_PARALLEL_BRANCHES_VARIANT,
            "Generated dual parallel indicator board, seed " + seed,
            componentBindings, powerBindings, connectionBindings,
            behaviorContract,
            PCB_LAYOUT_GENERATOR.generate(board, seed), physicalSpecifications, faultBinding,
            operationalStates, new GeneratedChallengeDefinition("PARALLEL_ONE_DARK", FAMILY_ID,
                DUAL_PARALLEL_BRANCHES_VARIANT, seed, scenarios,
                "Repair verified. Both indicators operating normally.", fault, faultBinding,
                behaviorContract),
            new ParallelDualIndicatorFamilyState(r1Slot, resistorInventory, resistorCatalog));
    }

    private LEDElm createLed(int x1, int y1, int x2, int y2, LedNameplate nameplate) {
        LEDElm led = new LEDElm(x1, y1);
        led.drag(x2, y2);
        led.modelName = nameplate.getModelName();
        led.setup();
        led.colorR = nameplate.getRed();
        led.colorG = nameplate.getGreen();
        led.colorB = nameplate.getBlue();
        return led;
    }

    private TroubleshootBoard createBoard() {
        TroubleshootBoard board = new TroubleshootBoard(FAMILY_ID);
        board.addNet(new BoardNet("VIN"));
        board.addNet(new BoardNet("BRANCH1_NODE"));
        board.addNet(new BoardNet("BRANCH2_NODE"));
        board.addNet(new BoardNet("GND"));
        board.addComponent(new BoardComponent("J1", "CONNECTOR"));
        board.addComponent(new BoardComponent("R1", "RESISTOR"));
        board.addComponent(new BoardComponent("LED1", "LED"));
        board.addComponent(new BoardComponent("R2", "RESISTOR"));
        board.addComponent(new BoardComponent("LED2", "LED"));
        board.addPad(new BoardPad("J1.1", "J1", "1", "VIN"));
        board.addPad(new BoardPad("J1.2", "J1", "2", "GND"));
        board.addPad(new BoardPad("R1.1", "R1", "1", "VIN"));
        board.addPad(new BoardPad("R1.2", "R1", "2", "BRANCH1_NODE"));
        board.addPad(new BoardPad("LED1.A", "LED1", "A", "BRANCH1_NODE"));
        board.addPad(new BoardPad("LED1.K", "LED1", "K", "GND"));
        board.addPad(new BoardPad("R2.1", "R2", "1", "VIN"));
        board.addPad(new BoardPad("R2.2", "R2", "2", "BRANCH2_NODE"));
        board.addPad(new BoardPad("LED2.A", "LED2", "A", "BRANCH2_NODE"));
        board.addPad(new BoardPad("LED2.K", "LED2", "K", "GND"));
        board.addPowerInput(new ExternalBoardPowerInput("VIN_INPUT", "J1.1", "J1.2",
            "VIN", "GND"));
        board.validate();
        return board;
    }
}
