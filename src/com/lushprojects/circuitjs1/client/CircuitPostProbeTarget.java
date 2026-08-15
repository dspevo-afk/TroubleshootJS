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

    public Point getMarkerPoint() {
        return element.getPost(postIndex);
    }

    public double getVoltage() {
        return element.getPostVoltage(postIndex);
    }
}