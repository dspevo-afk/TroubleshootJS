package com.lushprojects.circuitjs1.client;

import java.util.Vector;

class GeneratedFaultCandidate {
    private final GeneratedFaultBinding binding;
    private final boolean compatible;

    GeneratedFaultCandidate(GeneratedFaultBinding binding, boolean compatible) {
        if (binding == null)
            throw new IllegalArgumentException("Missing generated fault candidate");
        this.binding = binding;
        this.compatible = compatible;
    }

    GeneratedFault getFault() { return binding.getFault(); }
    GeneratedFaultBinding getBinding() { return binding; }
    boolean isCompatible() { return compatible; }
    GeneratedFaultServiceability getServiceability() { return binding.getServiceability(); }
    boolean isServiceable() {
        return getServiceability() != null && getServiceability().isAdmissible();
    }
    boolean isAdmitted() { return compatible && isServiceable(); }
    Vector<CircuitElm> getPrivateSimulationElements() {
        return binding.getPrivateSimulationElements();
    }
}
