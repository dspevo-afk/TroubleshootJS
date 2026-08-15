package com.lushprojects.circuitjs1.client;

class CircuitPostMeasurementEndpoint implements CircuitMeasurementEndpoint {
    private final CircuitElm element;
    private final int postIndex;

    CircuitPostMeasurementEndpoint(CircuitElm element, int postIndex) {
        this.element = element;
        this.postIndex = postIndex;
    }

    CircuitElm getElement() {
        return element;
    }

    int getPostIndex() {
        return postIndex;
    }
}