package com.lushprojects.circuitjs1.client;

class ResistanceMeasurementStimulus implements ActiveMeasurementStimulus {
    static final double TEST_VOLTAGE = 1;
    static final double INTERNAL_RESISTANCE = 1000;

    private final DCVoltageElm source;
    private final ResistorElm internalResistor;

    ResistanceMeasurementStimulus(CirSim sim, CircuitPostMeasurementEndpoint red,
            CircuitPostMeasurementEndpoint black) {
        Point redPoint = red.getElement().getPost(red.getPostIndex());
        Point blackPoint = black.getElement().getPost(black.getPostIndex());
        Point midpoint = findUnusedPoint(sim, redPoint, blackPoint);
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

    double getTestCurrent() {
        return source.getCurrent();
    }

    CircuitElm getSource() {
        return source;
    }

    CircuitElm getInternalResistor() {
        return internalResistor;
    }

    public CircuitElm[] getTemporaryElements() {
        return new CircuitElm[] { source, internalResistor };
    }

    static Point findUnusedPoint(CirSim sim, Point redPoint, Point blackPoint) {
        int maximumX = Math.max(redPoint.x, blackPoint.x);
        int maximumY = Math.max(redPoint.y, blackPoint.y);
        for (CircuitElm element : sim.elmList) {
            for (int postIndex = 0; postIndex < element.getPostCount(); postIndex++) {
                Point post = element.getPost(postIndex);
                maximumX = Math.max(maximumX, post.x);
                maximumY = Math.max(maximumY, post.y);
            }
        }
        for (int offset = 64; ; offset += 64) {
            Point candidate = new Point(maximumX + offset, maximumY + offset);
            if (!samePoint(candidate, redPoint) && !samePoint(candidate, blackPoint) &&
                    !isOccupied(sim, candidate))
                return candidate;
        }
    }

    private static boolean isOccupied(CirSim sim, Point point) {
        for (CircuitElm element : sim.elmList) {
            for (int postIndex = 0; postIndex < element.getPostCount(); postIndex++) {
                if (samePoint(point, element.getPost(postIndex)))
                    return true;
            }
        }
        return false;
    }

    private static boolean samePoint(Point first, Point second) {
        return first.x == second.x && first.y == second.y;
    }
}