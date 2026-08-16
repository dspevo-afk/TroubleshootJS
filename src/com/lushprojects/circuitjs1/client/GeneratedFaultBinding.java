package com.lushprojects.circuitjs1.client;

import java.util.Vector;

class GeneratedFaultBinding {
    private final GeneratedFault fault;
    private final GeneratedFaultEffect effect;

    GeneratedFaultBinding(GeneratedFault fault, GeneratedFaultEffect effect) {
        if (fault == null || effect == null)
            throw new IllegalArgumentException("Missing generated fault binding");
        this.fault = fault;
        this.effect = effect;
    }

    GeneratedFault getFault() { return fault; }
    GeneratedFaultEffect getEffect() { return effect; }

    Vector<CircuitElm> getPrivateSimulationElements() {
        return effect.getPrivateSimulationElements();
    }

    CircuitMeasurementEndpoint getPublicTerminal(CircuitElm backingElement, int terminal) {
        return effect.getPublicTerminal(backingElement, terminal);
    }

    boolean isPublicTerminal(CircuitElm backingElement, CircuitPostMeasurementEndpoint endpoint,
            int terminal) {
        CircuitMeasurementEndpoint publicTerminal = getPublicTerminal(backingElement, terminal);
        if (!(publicTerminal instanceof CircuitPostMeasurementEndpoint))
            return false;
        CircuitPostMeasurementEndpoint expected = (CircuitPostMeasurementEndpoint) publicTerminal;
        return expected.getElement() == endpoint.getElement() &&
            expected.getPostIndex() == endpoint.getPostIndex();
    }

    void setApplied(boolean applied) {
        effect.setApplied(applied);
    }

    boolean isApplied() { return effect.isApplied(); }
}
