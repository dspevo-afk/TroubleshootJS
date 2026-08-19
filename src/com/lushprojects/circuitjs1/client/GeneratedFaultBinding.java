package com.lushprojects.circuitjs1.client;

import java.util.Vector;

class GeneratedFaultBinding {
    private final GeneratedFault fault;
    private final GeneratedFaultEffect effect;
    private final GeneratedFaultServiceability serviceability;

    GeneratedFaultBinding(GeneratedFault fault, GeneratedFaultEffect effect) {
        this(fault, effect, GeneratedFaultServiceabilityCatalog.forFault(fault));
    }

    GeneratedFaultBinding(GeneratedFault fault, GeneratedFaultEffect effect,
            GeneratedFaultServiceability serviceability) {
        if (fault == null || effect == null)
            throw new IllegalArgumentException("Missing generated fault binding");
        this.fault = fault;
        this.effect = effect;
        this.serviceability = serviceability;
    }

    GeneratedFault getFault() { return fault; }
    GeneratedFaultEffect getEffect() { return effect; }
    GeneratedFaultServiceability getServiceability() { return serviceability; }
    GeneratedFaultLocus getFaultLocus() {
        return serviceability == null ? null : serviceability.getLocus();
    }

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
