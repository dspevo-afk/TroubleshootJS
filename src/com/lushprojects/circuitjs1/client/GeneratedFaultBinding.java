package com.lushprojects.circuitjs1.client;

class GeneratedFaultBinding {
    private final GeneratedFault fault;
    private final SwitchElm isolationElement;

    GeneratedFaultBinding(GeneratedFault fault, SwitchElm isolationElement) {
        if (fault == null || isolationElement == null)
            throw new IllegalArgumentException("Missing generated fault binding");
        this.fault = fault;
        this.isolationElement = isolationElement;
    }

    GeneratedFault getFault() { return fault; }
    SwitchElm getIsolationElement() { return isolationElement; }

    void setApplied(boolean applied) {
        boolean open = isolationElement.position == 1;
        if (open != applied)
            isolationElement.toggle();
    }

    boolean isApplied() { return isolationElement.position == 1; }
}
