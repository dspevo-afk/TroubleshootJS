package com.lushprojects.circuitjs1.client;

class DiodeTestStimulus implements ActiveMeasurementStimulus {
    static final double TEST_VOLTAGE = 3;
    static final double INTERNAL_RESISTANCE = 1000;

    private final CircuitPostMeasurementEndpoint red;
    private final CircuitPostMeasurementEndpoint black;
    private final DCVoltageElm source;
    private final ResistorElm internalResistor;

    DiodeTestStimulus(CirSim sim, CircuitPostMeasurementEndpoint red,
            CircuitPostMeasurementEndpoint black) {
        this.red = red;
        this.black = black;
        Point redPoint = red.getElement().getPost(red.getPostIndex());
        Point blackPoint = black.getElement().getPost(black.getPostIndex());
        Point midpoint = ResistanceMeasurementStimulus.findUnusedPoint(sim, redPoint, blackPoint);
        source = new DCVoltageElm(midpoint.x, midpoint.y);
        source.drag(redPoint.x, redPoint.y);
        source.maxVoltage = TEST_VOLTAGE;
        internalResistor = new ResistorElm(blackPoint.x, blackPoint.y);
        internalResistor.drag(midpoint.x, midpoint.y);
        internalResistor.setResistance(INTERNAL_RESISTANCE);
    }

    public void install(CirSim sim) {
        sim.elmList.add(source);
        sim.elmList.add(internalResistor);
    }

    public void remove(CirSim sim) {
        sim.elmList.remove(source);
        sim.elmList.remove(internalResistor);
    }

    public CircuitElm[] getTemporaryElements() {
        return new CircuitElm[] { source, internalResistor };
    }

    DiodeMeasurementResult getResult() {
        return new DiodeMeasurementResult(
            red.getElement().getPostVoltage(red.getPostIndex()) -
            black.getElement().getPostVoltage(black.getPostIndex()), Math.abs(source.getCurrent()));
    }
}