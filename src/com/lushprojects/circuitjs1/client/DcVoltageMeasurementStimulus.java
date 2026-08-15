package com.lushprojects.circuitjs1.client;

class DcVoltageMeasurementStimulus implements ActiveMeasurementStimulus {
    static final double INPUT_RESISTANCE = 10000000;

    private final CircuitPostMeasurementEndpoint red;
    private final CircuitPostMeasurementEndpoint black;
    private final ResistorElm inputResistor;

    DcVoltageMeasurementStimulus(CircuitPostMeasurementEndpoint red,
            CircuitPostMeasurementEndpoint black) {
        this.red = red;
        this.black = black;
        Point redPoint = red.getElement().getPost(red.getPostIndex());
        Point blackPoint = black.getElement().getPost(black.getPostIndex());
        inputResistor = new ResistorElm(redPoint.x, redPoint.y);
        inputResistor.drag(blackPoint.x, blackPoint.y);
        inputResistor.setResistance(INPUT_RESISTANCE);
    }

    public void install(CirSim sim) {
        sim.elmList.add(inputResistor);
    }

    public void remove(CirSim sim) {
        sim.elmList.remove(inputResistor);
    }

    public CircuitElm[] getTemporaryElements() {
        return new CircuitElm[] { inputResistor };
    }

    double getVoltage() {
        return red.getElement().getPostVoltage(red.getPostIndex()) -
            black.getElement().getPostVoltage(black.getPostIndex());
    }
}