package com.lushprojects.circuitjs1.client;

import java.util.Vector;

class ExternalPowerSimulationBinding {
    private final Vector<CircuitElm> backingElements;
    private final ExternalPowerControl control;
    private long connectionRevision;

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
	boolean changed = control.isConnected() != connected;
	control.setConnected(connected);
	if (changed) connectionRevision++;
    }

    long getConnectionRevision() { return connectionRevision; }

    boolean isConnected() {
	return control != null && control.isConnected();
    }

    LimitedDcSupplyElm getLimitedSupply() {
        LimitedDcSupplyElm found = null;
        for (CircuitElm element : backingElements) {
            if (!(element instanceof LimitedDcSupplyElm)) continue;
            if (found != null) throw new IllegalStateException("Ambiguous bench supply owner");
            found = (LimitedDcSupplyElm)element;
        }
        return found;
    }

    void setCurrentLimit(double amps) {
        LimitedDcSupplyElm supply = getLimitedSupply();
        if (supply == null) throw new IllegalStateException("Input has no adjustable current limit");
        supply.configure(supply.maxVoltage, amps);
        connectionRevision++;
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
