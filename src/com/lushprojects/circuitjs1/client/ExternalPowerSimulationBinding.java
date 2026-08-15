package com.lushprojects.circuitjs1.client;

import java.util.Vector;

class ExternalPowerSimulationBinding {
    private final Vector<CircuitElm> backingElements;

    ExternalPowerSimulationBinding(CircuitElm backingElement) {
	Vector<CircuitElm> elements = new Vector<CircuitElm>();
	elements.add(backingElement);
	backingElements = validateElements(elements);
    }

    ExternalPowerSimulationBinding(Vector<CircuitElm> backingElements) {
	this.backingElements = validateElements(backingElements);
    }

    Vector<CircuitElm> getBackingElements() {
        return new Vector<CircuitElm>(backingElements);
    }

    private static Vector<CircuitElm> validateElements(Vector<CircuitElm> elements) {
    if (elements == null || elements.isEmpty())
        throw new IllegalArgumentException("Missing external power simulation elements");
    for (CircuitElm element : elements) {
        if (element == null)
        throw new IllegalArgumentException("Missing external power simulation element");
    }
    return new Vector<CircuitElm>(elements);
    }
}