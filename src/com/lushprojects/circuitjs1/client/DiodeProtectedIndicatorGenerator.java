package com.lushprojects.circuitjs1.client;

import java.util.Random;
import java.util.Vector;

class DiodeProtectedIndicatorGenerator {
    static final String FAMILY_ID = "DIODE_PROTECTED_INDICATOR";
    static final String DIRECT_SERIES_VARIANT = "DIRECT_SERIES_DIODE";
    private static final double[] SUPPLY_VOLTAGES = { 5, 9, 12 };
    private static final double[] RESISTOR_VALUES = { 330, 680, 1000 };

    GeneratedBoardInstance generate(long seed) {
        Random random = new Random(seed);
        int valueIndex = random.nextInt(SUPPLY_VOLTAGES.length);
        double supplyVoltage = SUPPLY_VOLTAGES[valueIndex];
        double resistorValue = RESISTOR_VALUES[valueIndex];
        TroubleshootBoard board = createBoard();
        BoardPhysicalSpecifications specs = new BoardPhysicalSpecifications();
        specs.addPowerInputNameplate(new PowerInputNameplate("VIN_INPUT", supplyVoltage));
        specs.addDiodeNameplate(new DiodeNameplate("D1", "Generic silicon diode", "default"));
        specs.addResistorNameplate(new ResistorNameplate("R1", resistorValue, 5));

        DCVoltageElm supply = new DCVoltageElm(160, 320); supply.drag(160, 160);
        supply.maxVoltage = supplyVoltage;
        SwitchElm powerSwitch = new SwitchElm(160, 160); powerSwitch.drag(240, 160);
        WireElm vinTrace = new WireElm(240, 160); vinTrace.drag(320, 160);
        WireElm d1AnodeLink = new WireElm(320, 160); d1AnodeLink.drag(400, 240);
        DiodeElm diode = createDefaultDiode(400, 240, 480, 240);
        SwitchElm faultSwitch = new SwitchElm(480, 240); faultSwitch.drag(512, 240);
        WireElm d1CathodeLink = new WireElm(512, 240); d1CathodeLink.drag(560, 160);
        WireElm diodeOutTrace = new WireElm(560, 160); diodeOutTrace.drag(608, 160);
        ResistorElm resistor = new ResistorElm(608, 160); resistor.drag(688, 160);
        resistor.setResistance(resistorValue);
        WireElm ledNodeTrace = new WireElm(688, 160); ledNodeTrace.drag(720, 160);
        LEDElm led = new LEDElm(720, 160); led.drag(800, 160);
        GroundElm ground = new GroundElm(800, 160); ground.drag(800, 192);
        WireElm supplyReturn = new WireElm(160, 320); supplyReturn.drag(800, 320);
        WireElm groundReturn = new WireElm(800, 320); groundReturn.drag(800, 160);

        Vector<CircuitElm> elements = new Vector<CircuitElm>();
        elements.add(supply); elements.add(powerSwitch); elements.add(vinTrace);
        elements.add(d1AnodeLink); elements.add(diode); elements.add(faultSwitch);
        elements.add(d1CathodeLink); elements.add(diodeOutTrace); elements.add(resistor);
        elements.add(ledNodeTrace); elements.add(led); elements.add(ground);
        elements.add(supplyReturn); elements.add(groundReturn);

        GeneratedComponentBindings components = new GeneratedComponentBindings(board);
        components.bindComponent("D1", diode);
        components.bindComponent("R1", resistor);
        components.bindComponent("LED1", led);
        GeneratedComponentOperationalStates operational = new GeneratedComponentOperationalStates();
        operational.bindLed("LED1", led);
        GeneratedFault fault = new GeneratedFault("DIODE_D1_OPEN", GeneratedFaultType.COMPONENT_OPEN,
            "D1", FAMILY_ID, seed);
        GeneratedFaultBinding faultBinding = new GeneratedFaultBinding(fault, faultSwitch);
        PhysicalDiodePart original = new PhysicalDiodePart("D1_ORIGINAL",
            new DiodeNameplate("D1_ORIGINAL", "Generic silicon diode", "default"), diode,
            faultBinding, false, DiodePartLocation.INSTALLED);
        DiodeReplacementInventory inventory = new DiodeReplacementInventory();
        inventory.add(original);
        DiodeReplacementCatalog catalog = new DiodeReplacementCatalog();
        DiodeComponentSlot slot = new DiodeComponentSlot("D1", specs.getDiodeNameplate("D1"),
            original, d1AnodeLink, d1CathodeLink);

        GeneratedChallengeCatalog challenges = new GeneratedChallengeCatalog();
        challenges.addCandidate(new GeneratedChallengeDefinition("DIODE_INDICATOR_NO_LIGHT",
            FAMILY_ID, DIRECT_SERIES_VARIANT, seed, "INDICATOR_DOES_NOT_LIGHT",
            "Indicator does not light.", fault, faultBinding,
            new DiodeProtectedIndicatorFaultValidator(),
            new DiodeProtectedIndicatorRepairValidator()));
        GeneratedExternalPowerBindings power = new GeneratedExternalPowerBindings(board);
        Vector<CircuitElm> powerElements = new Vector<CircuitElm>();
        powerElements.add(supply); powerElements.add(powerSwitch);
        power.bindPowerInput("VIN_INPUT", new ExternalPowerSimulationBinding(powerElements,
            new SwitchExternalPowerControl(powerSwitch)));

        BoardSimulationBindings bindings = board.getSimulationBindings();
        bindings.bindPad("J1.1", new CircuitPostMeasurementEndpoint(powerSwitch, 1));
        bindings.bindPad("J1.2", new CircuitPostMeasurementEndpoint(ground, 0));
        bindings.bindPad("D1.A", new CircuitPostMeasurementEndpoint(vinTrace, 1));
        bindings.bindPad("D1.K", new CircuitPostMeasurementEndpoint(diodeOutTrace, 0));
        bindings.bindPad("R1.1", new CircuitPostMeasurementEndpoint(resistor, 0));
        bindings.bindPad("R1.2", new CircuitPostMeasurementEndpoint(resistor, 1));
        bindings.bindPad("LED1.A", new CircuitPostMeasurementEndpoint(led, 0));
        bindings.bindPad("LED1.K", new CircuitPostMeasurementEndpoint(led, 1));
        GeneratedComponentConnectionBindings connections = new GeneratedComponentConnectionBindings(board);
        connections.bind("D1", "D1.A", bindings.getEndpoint("D1.A"),
            original.getTerminalForBoardPad("D1.A"), d1AnodeLink);
        connections.bind("D1", "D1.K", bindings.getEndpoint("D1.K"),
            original.getTerminalForBoardPad("D1.K"), d1CathodeLink);

        String description = "Generated diode-protected indicator, seed " + seed + ", " +
            supplyVoltage + " V, " + resistorValue + " ohm";
        return new GeneratedBoardInstance(board, elements, seed, FAMILY_ID,
            DIRECT_SERIES_VARIANT, description, components, power, connections,
            new DiodeProtectedIndicatorGeneratedBoardValidator(),
            DiodeProtectedIndicatorPcbLayout.create(board), specs, faultBinding, operational,
            challenges.select(seed), new DiodeProtectedIndicatorFamilyState(slot, inventory, catalog));
    }

    private DiodeElm createDefaultDiode(int x, int y, int x2, int y2) {
        DiodeElm diode = new DiodeElm(x, y);
        diode.drag(x2, y2);
        diode.modelName = "default";
        diode.setup();
        return diode;
    }

    private TroubleshootBoard createBoard() {
        TroubleshootBoard board = new TroubleshootBoard(FAMILY_ID);
        board.addNet(new BoardNet("VIN"));
        board.addNet(new BoardNet("DIODE_OUT"));
        board.addNet(new BoardNet("LED_NODE"));
        board.addNet(new BoardNet("GND"));
        board.addComponent(new BoardComponent("J1", "CONNECTOR"));
        board.addComponent(new BoardComponent("D1", "DIODE"));
        board.addComponent(new BoardComponent("R1", "RESISTOR"));
        board.addComponent(new BoardComponent("LED1", "LED"));
        board.addPad(new BoardPad("J1.1", "J1", "1", "VIN"));
        board.addPad(new BoardPad("J1.2", "J1", "2", "GND"));
        board.addPad(new BoardPad("D1.A", "D1", "A", "VIN"));
        board.addPad(new BoardPad("D1.K", "D1", "K", "DIODE_OUT"));
        board.addPad(new BoardPad("R1.1", "R1", "1", "DIODE_OUT"));
        board.addPad(new BoardPad("R1.2", "R1", "2", "LED_NODE"));
        board.addPad(new BoardPad("LED1.A", "LED1", "A", "LED_NODE"));
        board.addPad(new BoardPad("LED1.K", "LED1", "K", "GND"));
        board.addPowerInput(new ExternalBoardPowerInput("VIN_INPUT", "J1.1", "J1.2",
            "VIN", "GND"));
        board.validate();
        return board;
    }
}
