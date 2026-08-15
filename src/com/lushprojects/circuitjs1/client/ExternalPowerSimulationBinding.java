package com.lushprojects.circuitjs1.client;

import java.util.Vector;

class ExternalPowerSimulationBinding {
    private final Vector<CircuitElm> backingElements;
    private final ExternalPowerControl control;

    ExternalPowerSimulationBinding(CircuitElm backingElement) {
	Vector<CircuitElm> elements = new Vector<CircuitElm>();
	elements.add(backingElement);
    backingElements = validateElements(elements);
    control = null;
    }

    ExternalPowerSimulationBinding(Vector<CircuitElm> backingElements) {
	this.backingElements = validateElements(backingElements);
    control = null;
    }

    ExternalPowerSimulationBinding(Vector<CircuitElm> backingElements,
        ExternalPowerControl control) {
    this.backingElements = validateElements(backingElements);
    if (control == null)
        throw new IllegalArgumentException("Missing external power control");
    this.control = control;
    }

    Vector<CircuitElm> getBackingElements() {
        return new Vector<CircuitElm>(backingElements);
    }

    boolean hasControl() {
	return control != null;
    }

    void setConnected(boolean connected) {
	if (control == null)
	    throw new IllegalStateException("External power input has no control");
	control.setConnected(connected);
    }

    boolean isConnected() {
	return control != null && control.isConnected();
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