package com.lushprojects.circuitjs1.client;

class CircuitPostProbeTarget implements ProbeTarget {
    private final CirSim sim;
    private final CircuitElm element;
    private final int postIndex;

    CircuitPostProbeTarget(CirSim sim, CircuitElm element, int postIndex) {
        this.sim = sim;
        this.element = element;
        this.postIndex = postIndex;
    }

    CircuitElm getElement() {
        return element;
    }

    int getPostIndex() {
        return postIndex;
    }

    public boolean isValid() {
        return postIndex >= 0 && postIndex < element.getPostCount() &&
            sim.containsElement(element);
    }

    public boolean isSameTarget(ProbeTarget other) {
        if (!(other instanceof CircuitPostProbeTarget))
            return false;
        CircuitPostProbeTarget circuitPost = (CircuitPostProbeTarget) other;
        return sim == circuitPost.sim && element == circuitPost.element &&
            postIndex == circuitPost.postIndex;
    }

    public Point getMarkerPoint() {
        return element.getPost(postIndex);
    }

    public CircuitMeasurementEndpoint getMeasurementEndpoint() {
        return new CircuitPostMeasurementEndpoint(element, postIndex);
    }
}