package com.lushprojects.circuitjs1.client;

import java.util.Vector;

/**
 * CircuitJS-backed implementation of one generated fault.  The effect owns
 * only private simulation infrastructure and never owns board identity.
 */
interface GeneratedFaultEffect {
    void setApplied(boolean applied);
    boolean isApplied();
    CircuitMeasurementEndpoint getPublicTerminal(CircuitElm backingElement, int terminal);
    Vector<CircuitElm> getPrivateSimulationElements();
    CircuitElm getValueMutationTarget();
}

class SwitchOpenFaultEffect implements GeneratedFaultEffect {
    private final SwitchElm switchElement;

    SwitchOpenFaultEffect(SwitchElm switchElement) {
        if (switchElement == null)
            throw new IllegalArgumentException("Missing fault switch");
        this.switchElement = switchElement;
    }

    public void setApplied(boolean applied) {
        boolean open = switchElement.position == 1;
        if (open != applied)
            switchElement.toggle();
    }

    public boolean isApplied() { return switchElement.position == 1; }

    public CircuitMeasurementEndpoint getPublicTerminal(CircuitElm backingElement, int terminal) {
        if (backingElement == null || terminal < 0 || terminal > 1)
            throw new IllegalArgumentException("Invalid faulted component terminal");
        if (terminal == 0)
            return new CircuitPostMeasurementEndpoint(backingElement, 0);
        return new CircuitPostMeasurementEndpoint(switchElement, 1);
    }

    public Vector<CircuitElm> getPrivateSimulationElements() {
        Vector<CircuitElm> result = new Vector<CircuitElm>();
        result.add(switchElement);
        return result;
    }

    public CircuitElm getValueMutationTarget() { return null; }
}

/** A lead-open capacitor fault exposes the board-side positive lead, not a fake meter value. */
class CapacitorPositiveLeadOpenFaultEffect implements GeneratedFaultEffect {
    private final SwitchElm switchElement;

    CapacitorPositiveLeadOpenFaultEffect(SwitchElm switchElement) {
        if (switchElement == null)
            throw new IllegalArgumentException("Missing capacitor open switch");
        this.switchElement = switchElement;
    }

    public void setApplied(boolean applied) {
        boolean open = switchElement.position == 1;
        if (open != applied)
            switchElement.toggle();
    }

    public boolean isApplied() { return switchElement.position == 1; }

    public CircuitMeasurementEndpoint getPublicTerminal(CircuitElm backingElement, int terminal) {
        if (backingElement == null || terminal < 0 || terminal > 1)
            throw new IllegalArgumentException("Invalid open capacitor terminal");
        return terminal == 0 ? new CircuitPostMeasurementEndpoint(switchElement, 0) :
            new CircuitPostMeasurementEndpoint(backingElement, 1);
    }

    public Vector<CircuitElm> getPrivateSimulationElements() {
        Vector<CircuitElm> result = new Vector<CircuitElm>();
        result.add(switchElement);
        return result;
    }

    public CircuitElm getValueMutationTarget() { return null; }
}

/**
 * A shorted capacitor keeps its board-facing positive lead distinct from the
 * capacitor body.  The private bypass is therefore part of the original
 * physical part, rather than a board-level shortcut that a later replacement
 * could accidentally inherit.
 */
class CapacitorShortFaultEffect implements GeneratedFaultEffect {
    private static final double OPEN_SHUNT_RESISTANCE_OHMS = 1e12;
    private static final double SHORT_SHUNT_RESISTANCE_OHMS = .1;
    private final ResistorElm bypassResistor;
    private final SwitchElm positiveLeadSwitch;
    private boolean applied;

    CapacitorShortFaultEffect(ResistorElm bypassResistor, SwitchElm positiveLeadSwitch) {
        if (bypassResistor == null || positiveLeadSwitch == null)
            throw new IllegalArgumentException("Missing capacitor short infrastructure");
        this.bypassResistor = bypassResistor;
        this.positiveLeadSwitch = positiveLeadSwitch;
        setApplied(false);
    }

    public void setApplied(boolean applied) {
        bypassResistor.setResistance(applied ? SHORT_SHUNT_RESISTANCE_OHMS :
            OPEN_SHUNT_RESISTANCE_OHMS);
        this.applied = applied;
    }

    public boolean isApplied() { return applied; }

    public CircuitMeasurementEndpoint getPublicTerminal(CircuitElm backingElement, int terminal) {
        if (backingElement == null || terminal < 0 || terminal > 1)
            throw new IllegalArgumentException("Invalid shorted capacitor terminal");
        return terminal == 0 ? new CircuitPostMeasurementEndpoint(positiveLeadSwitch, 0) :
            new CircuitPostMeasurementEndpoint(backingElement, 1);
    }

    public Vector<CircuitElm> getPrivateSimulationElements() {
        Vector<CircuitElm> result = new Vector<CircuitElm>();
        result.add(bypassResistor);
        result.add(positiveLeadSwitch);
        return result;
    }

    public CircuitElm getValueMutationTarget() { return null; }
}

class ResistorIncorrectValueFaultEffect implements GeneratedFaultEffect {
    private final ResistorElm resistor;
    private final double healthyValue;
    private final double effectiveValue;
    private boolean applied;

    ResistorIncorrectValueFaultEffect(ResistorElm resistor, double healthyValue,
            double effectiveValue) {
        if (resistor == null || healthyValue <= 0 || effectiveValue <= 0 ||
                Double.isNaN(healthyValue) || Double.isInfinite(healthyValue) ||
                Double.isNaN(effectiveValue) || Double.isInfinite(effectiveValue) ||
                Math.abs(effectiveValue - healthyValue) <= Math.max(1e-9,
                    Math.abs(healthyValue) * 1e-9))
            throw new IllegalArgumentException("Invalid resistor fault value");
        this.resistor = resistor;
        this.healthyValue = healthyValue;
        this.effectiveValue = effectiveValue;
    }

    public void setApplied(boolean applied) {
        if (this.applied == applied)
            return;
        resistor.setResistance(applied ? effectiveValue : healthyValue);
        this.applied = applied;
    }

    public boolean isApplied() { return applied; }

    public CircuitMeasurementEndpoint getPublicTerminal(CircuitElm backingElement, int terminal) {
        if (backingElement != resistor || terminal < 0 || terminal > 1)
            throw new IllegalArgumentException("Resistor fault backing mismatch");
        return new CircuitPostMeasurementEndpoint(resistor, terminal);
    }

    public Vector<CircuitElm> getPrivateSimulationElements() {
        return new Vector<CircuitElm>();
    }

    public CircuitElm getValueMutationTarget() { return resistor; }
}

class SwitchParallelShortFaultEffect implements GeneratedFaultEffect {
    private final SwitchElm bypassSwitch;

    SwitchParallelShortFaultEffect(SwitchElm bypassSwitch) {
        if (bypassSwitch == null)
            throw new IllegalArgumentException("Missing short fault bypass");
        this.bypassSwitch = bypassSwitch;
        setApplied(false);
    }

    public void setApplied(boolean applied) {
        boolean closed = bypassSwitch.position == 0;
        if (closed != applied)
            bypassSwitch.toggle();
    }

    public boolean isApplied() { return bypassSwitch.position == 0; }

    public CircuitMeasurementEndpoint getPublicTerminal(CircuitElm backingElement, int terminal) {
        if (backingElement == null || terminal < 0 || terminal > 1)
            throw new IllegalArgumentException("Invalid shorted component terminal");
        return new CircuitPostMeasurementEndpoint(backingElement, terminal);
    }

    public Vector<CircuitElm> getPrivateSimulationElements() {
        Vector<CircuitElm> result = new Vector<CircuitElm>();
        result.add(bypassSwitch);
        return result;
    }

    public CircuitElm getValueMutationTarget() { return null; }
}
