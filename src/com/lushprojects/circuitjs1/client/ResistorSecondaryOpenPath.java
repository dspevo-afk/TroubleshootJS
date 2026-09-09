package com.lushprojects.circuitjs1.client;

/**
 * Owns the secondary failure boundary for one physical resistor.  This is
 * deliberately separate from GeneratedFaultBinding: a replacement may fail
 * without changing the generated customer fault.
 */
class ResistorSecondaryOpenPath {
    /** Construction owners register the backing before later configuration can fail. */
    interface AllocationObserver {
        void allocated(CircuitElm element);
    }

    private final SwitchElm switchElement;

    private ResistorSecondaryOpenPath(CircuitPostMeasurementEndpoint upstream,
            AllocationObserver observer) {
        if (upstream == null || upstream.getElement() == null)
            throw new IllegalArgumentException("Missing resistor open-path upstream endpoint");
        Point point = upstream.getElement().getPost(upstream.getPostIndex());
        if (point == null)
            throw new IllegalArgumentException("Missing resistor open-path upstream point");
        switchElement = new SwitchElm(point.x, point.y);
        if (observer != null)
            observer.allocated(switchElement);
        switchElement.drag(point.x + 32, point.y);
    }

    static ResistorSecondaryOpenPath create(CircuitPostMeasurementEndpoint upstream) {
        return new ResistorSecondaryOpenPath(upstream, null);
    }

    /** Scoped construction overload; the observer may fail after registering ownership. */
    static ResistorSecondaryOpenPath create(CircuitPostMeasurementEndpoint upstream,
            AllocationObserver observer) {
        if (observer == null)
            throw new IllegalArgumentException("Missing secondary-path allocation observer");
        return new ResistorSecondaryOpenPath(upstream, observer);
    }

    CircuitPostMeasurementEndpoint getPublicTerminal() {
        return new CircuitPostMeasurementEndpoint(switchElement, 1);
    }

    CircuitElm getSimulationElement() { return switchElement; }

    boolean isOpen() { return switchElement.position == 1; }

    void open() {
        if (!isOpen())
            switchElement.toggle();
    }

    void resetForBoardReset() {
        if (isOpen())
            switchElement.toggle();
    }
}
