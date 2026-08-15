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

        DCVoltageElm supply = new DCVoltageElm(160, 320);
        supply.drag(160, 160);
        supply.maxVoltage = supplyVoltage;

        ResistorElm resistor = new ResistorElm(160, 160);
        resistor.drag(320, 160);
        resistor.setResistance(resistorValue);

        LEDElm led = new LEDElm(320, 160);
        led.drag(480, 160);

        GroundElm ground = new GroundElm(480, 160);
        ground.drag(480, 192);

        WireElm supplyReturn = new WireElm(160, 320);
        supplyReturn.drag(480, 320);
        WireElm groundReturn = new WireElm(480, 320);
        groundReturn.drag(480, 160);

        Vector<CircuitElm> elements = new Vector<CircuitElm>();
        elements.add(supply);
        elements.add(resistor);
        elements.add(led);
        elements.add(ground);
        elements.add(supplyReturn);
        elements.add(groundReturn);

        BoardSimulationBindings bindings = board.getSimulationBindings();
        bindings.bindPad("J1.1", new CircuitPostMeasurementEndpoint(supply, 1));
        bindings.bindPad("R1.1", new CircuitPostMeasurementEndpoint(resistor, 0));
        bindings.bindPad("R1.2", new CircuitPostMeasurementEndpoint(resistor, 1));
        bindings.bindPad("LED1.A", new CircuitPostMeasurementEndpoint(led, 0));
        bindings.bindPad("LED1.K", new CircuitPostMeasurementEndpoint(led, 1));
        bindings.bindPad("J1.2", new CircuitPostMeasurementEndpoint(ground, 0));

        return new GeneratedBoardInstance(board, elements, seed, DIRECT_SERIES_VARIANT,
            supply, resistor, led, supplyVoltage, resistorValue);
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
