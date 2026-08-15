package com.lushprojects.circuitjs1.client;

import java.util.Random;
import java.util.Vector;

class LedIndicatorGenerator {
    static final String DIRECT_SERIES_VARIANT = "DIRECT_SERIES";
    private static final double LED_FORWARD_VOLTAGE = 2.1;
    private static final double TARGET_CURRENT = .010;
    private static final double[] SUPPLY_VOLTAGES = { 5, 9, 12 };
    private static final double[] RESISTOR_VALUES = { 330, 680, 1000 };

    GeneratedBoardInstance generate(long seed) {
        Random random = new Random(seed);
        int valueIndex = random.nextInt(SUPPLY_VOLTAGES.length);
        double supplyVoltage = SUPPLY_VOLTAGES[valueIndex];
        double resistorValue = RESISTOR_VALUES[valueIndex];
        TroubleshootBoard board = createBoard();
        BoardPhysicalSpecifications physicalSpecifications = new BoardPhysicalSpecifications();
        physicalSpecifications.addResistorNameplate(new ResistorNameplate("R1", resistorValue, 5));
        physicalSpecifications.addPowerInputNameplate(new PowerInputNameplate("VIN_INPUT",
            supplyVoltage));

        DCVoltageElm supply = new DCVoltageElm(160, 320);
        supply.drag(160, 160);
        supply.maxVoltage = supplyVoltage;

        SwitchElm isolationSwitch = new SwitchElm(160, 160);
        isolationSwitch.drag(240, 160);

        WireElm vinTrace = new WireElm(240, 160);
        vinTrace.drag(320, 160);
        WireElm r1Lead1Link = new WireElm(320, 160);
        r1Lead1Link.drag(400, 240);
        ResistorElm resistor = new ResistorElm(400, 240);
        resistor.drag(480, 240);
        resistor.setResistance(resistorValue);

        SwitchElm r1FaultIsolation = new SwitchElm(480, 240);
        r1FaultIsolation.drag(512, 240);

        WireElm r1Lead2Link = new WireElm(512, 240);
        r1Lead2Link.drag(560, 160);
        WireElm ledNodeTrace = new WireElm(560, 160);
        ledNodeTrace.drag(640, 160);
        LEDElm led = new LEDElm(640, 160);
        led.drag(800, 160);

        GroundElm ground = new GroundElm(800, 160);
        ground.drag(800, 192);

        WireElm supplyReturn = new WireElm(160, 320);
        supplyReturn.drag(800, 320);
        WireElm groundReturn = new WireElm(800, 320);
        groundReturn.drag(800, 160);

        Vector<CircuitElm> elements = new Vector<CircuitElm>();
        elements.add(supply);
        elements.add(isolationSwitch);
        elements.add(vinTrace);
        elements.add(r1Lead1Link);
        elements.add(resistor);
        elements.add(r1FaultIsolation);
        elements.add(r1Lead2Link);
        elements.add(ledNodeTrace);
        elements.add(led);
        elements.add(ground);
        elements.add(supplyReturn);
        elements.add(groundReturn);

        GeneratedComponentBindings componentBindings = new GeneratedComponentBindings(board);
        componentBindings.bindComponent("R1", resistor);
        componentBindings.bindComponent("LED1", led);
        GeneratedComponentOperationalStates operationalStates =
            new GeneratedComponentOperationalStates();
        operationalStates.bindLed("LED1", led);
        GeneratedFault fault = new GeneratedFault("LED_R1_OPEN", GeneratedFaultType.COMPONENT_OPEN,
            "R1", "LED_INDICATOR", seed);
        GeneratedFaultBinding faultBinding = new GeneratedFaultBinding(fault, r1FaultIsolation);
        GeneratedChallengeCatalog challengeCatalog = new GeneratedChallengeCatalog();
        challengeCatalog.addCandidate(new GeneratedChallengeDefinition("LED_INDICATOR_NO_LIGHT",
            "LED_INDICATOR", DIRECT_SERIES_VARIANT, seed, "INDICATOR_DOES_NOT_LIGHT",
            "Indicator does not light.", fault, faultBinding, new LedIndicatorFaultValidator()));
        GeneratedExternalPowerBindings powerBindings = new GeneratedExternalPowerBindings(board);
        Vector<CircuitElm> powerElements = new Vector<CircuitElm>();
        powerElements.add(supply);
        powerElements.add(isolationSwitch);
        powerBindings.bindPowerInput("VIN_INPUT", new ExternalPowerSimulationBinding(powerElements,
            new SwitchExternalPowerControl(isolationSwitch)));

        GeneratedComponentConnectionBindings connectionBindings =
            new GeneratedComponentConnectionBindings(board);

        BoardSimulationBindings bindings = board.getSimulationBindings();
        bindings.bindPad("J1.1", new CircuitPostMeasurementEndpoint(isolationSwitch, 1));
        bindings.bindPad("R1.1", new CircuitPostMeasurementEndpoint(vinTrace, 1));
        bindings.bindPad("R1.2", new CircuitPostMeasurementEndpoint(ledNodeTrace, 0));
        bindings.bindPad("LED1.A", new CircuitPostMeasurementEndpoint(led, 0));
        bindings.bindPad("LED1.K", new CircuitPostMeasurementEndpoint(led, 1));
        bindings.bindPad("J1.2", new CircuitPostMeasurementEndpoint(ground, 0));
        connectionBindings.bind("R1", "R1.1", bindings.getEndpoint("R1.1"),
            new CircuitPostMeasurementEndpoint(resistor, 0), r1Lead1Link);
        connectionBindings.bind("R1", "R1.2", bindings.getEndpoint("R1.2"),
            new CircuitPostMeasurementEndpoint(r1FaultIsolation, 1), r1Lead2Link);

        String description = "Generated LED indicator, seed " + seed + ", " + supplyVoltage +
            " V, " + resistorValue + " ohm";
        return new GeneratedBoardInstance(board, elements, seed, "LED_INDICATOR",
            DIRECT_SERIES_VARIANT, description, componentBindings, powerBindings,
            connectionBindings, new LedIndicatorGeneratedBoardValidator(),
            LedIndicatorPcbLayout.create(board), physicalSpecifications, faultBinding,
            operationalStates, challengeCatalog.select(seed));
    }

    private TroubleshootBoard createBoard() {
        TroubleshootBoard board = new TroubleshootBoard("LED_INDICATOR");
        board.addNet(new BoardNet("VIN"));
        board.addNet(new BoardNet("LED_NODE"));
        board.addNet(new BoardNet("GND"));

        board.addComponent(new BoardComponent("J1", "CONNECTOR"));
        board.addComponent(new BoardComponent("R1", "RESISTOR"));
        board.addComponent(new BoardComponent("LED1", "LED"));

        board.addPad(new BoardPad("J1.1", "J1", "1", "VIN"));
        board.addPad(new BoardPad("J1.2", "J1", "2", "GND"));
        board.addPad(new BoardPad("R1.1", "R1", "1", "VIN"));
        board.addPad(new BoardPad("R1.2", "R1", "2", "LED_NODE"));
        board.addPad(new BoardPad("LED1.A", "LED1", "A", "LED_NODE"));
        board.addPad(new BoardPad("LED1.K", "LED1", "K", "GND"));
        board.addPowerInput(new ExternalBoardPowerInput(
            "VIN_INPUT", "J1.1", "J1.2", "VIN", "GND"));
        board.validate();
        return board;
    }

    double getExpectedCurrent(double supplyVoltage, double resistorValue) {
        return (supplyVoltage - LED_FORWARD_VOLTAGE) / resistorValue;
    }
}
