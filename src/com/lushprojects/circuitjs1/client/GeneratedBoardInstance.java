package com.lushprojects.circuitjs1.client;

import java.util.Vector;

class GeneratedBoardInstance {
    private final TroubleshootBoard board;
    private final Vector<CircuitElm> simulationElements;
    private final long seed;
    private final String topologyVariant;
    private final VoltageElm externalPowerSource;
    private final ResistorElm resistor;
    private final LEDElm led;
    private final double supplyVoltage;
    private final double resistorValue;

    GeneratedBoardInstance(TroubleshootBoard board, Vector<CircuitElm> simulationElements,
            long seed, String topologyVariant, VoltageElm externalPowerSource,
            ResistorElm resistor, LEDElm led, double supplyVoltage, double resistorValue) {
        this.board = board;
        this.simulationElements = new Vector<CircuitElm>(simulationElements);
        this.seed = seed;
        this.topologyVariant = topologyVariant;
        this.externalPowerSource = externalPowerSource;
        this.resistor = resistor;
        this.led = led;
        this.supplyVoltage = supplyVoltage;
        this.resistorValue = resistorValue;
    }

    TroubleshootBoard getBoard() {
        return board;
    }

    Vector<CircuitElm> getSimulationElements() {
        return new Vector<CircuitElm>(simulationElements);
    }

    BoardSimulationBindings getSimulationBindings() {
        return board.getSimulationBindings();
    }

    long getSeed() {
        return seed;
    }

    String getTopologyVariant() {
        return topologyVariant;
    }

    VoltageElm getExternalPowerSource(String powerInputId) {
        if (!"VIN_INPUT".equals(powerInputId))
            throw new IllegalArgumentException("Unknown generated board power input: " + powerInputId);
        return externalPowerSource;
    }

    ResistorElm getResistor() {
        return resistor;
    }

    LEDElm getLed() {
        return led;
    }

    double getSupplyVoltage() {
        return supplyVoltage;
    }

    double getResistorValue() {
        return resistorValue;
    }
}
