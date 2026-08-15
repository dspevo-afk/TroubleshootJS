package com.lushprojects.circuitjs1.client;

class ResistanceMeasurementStimulus {
    static final double TEST_VOLTAGE = 1;
    static final double INTERNAL_RESISTANCE = 1000;

    private final DCVoltageElm source;
    private final ResistorElm internalResistor;

    ResistanceMeasurementStimulus(CircuitPostMeasurementEndpoint red,
            CircuitPostMeasurementEndpoint black) {
        Point redPoint = red.getElement().getPost(red.getPostIndex());
        Point blackPoint = black.getElement().getPost(black.getPostIndex());
        int midpointX = redPoint.x;
        int midpointY = redPoint.y + 64;
        source = new DCVoltageElm(redPoint.x, redPoint.y);
        source.drag(midpointX, midpointY);
        source.maxVoltage = TEST_VOLTAGE;
        internalResistor = new ResistorElm(midpointX, midpointY);
        internalResistor.drag(blackPoint.x, blackPoint.y);
        internalResistor.setResistance(INTERNAL_RESISTANCE);
    }

    void install(CirSim sim) {
        sim.elmList.add(source);
        sim.elmList.add(internalResistor);
    }

    void remove(CirSim sim) {
        sim.elmList.remove(source);
        sim.elmList.remove(internalResistor);
    }

    double getTestCurrent() {
        return source.getCurrent();
    }
}