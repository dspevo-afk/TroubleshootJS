package com.lushprojects.circuitjs1.client;

import java.util.Vector;

/** Owns separable solder leads and, for connectors, independent cable contacts. */
final class ServiceComponentSlot implements PhysicalMutationSlot.Docking {
    private final PhysicalBoardSlot slot;
    private final WireElm[] leads;
    private final WireElm[] docking;
    private final AttachmentState empty;
    private final boolean connector;
    private final PhysicalServicePart original;
    private final CircuitPostMeasurementEndpoint[] harness;

    ServiceComponentSlot(PhysicalBoardSlot slot, PhysicalServicePart part,
            WireElm[] leads, WireElm[] docking, CircuitPostMeasurementEndpoint[] harness) {
        this.slot = slot; this.leads = copy(leads); this.docking = copy(docking);
        original = part;
        this.harness = harness == null ? new CircuitPostMeasurementEndpoint[0] :
            new CircuitPostMeasurementEndpoint[] {harness[0], harness[1]};
        if (this.harness.length != docking.length) throw new IllegalArgumentException("Missing cable endpoint ownership");
        connector = part.isConnector();
        attach(part); slot.install(part); empty = captureAttachmentState();
    }
    public String getComponentId() { return slot.getComponentId(); }
    private static WireElm[] copy(WireElm[] source) {
        WireElm[] copy = new WireElm[source.length];
        for (int i = 0; i < source.length; i++) copy[i] = source[i]; return copy;
    }
    public PhysicalBoardSlot getPhysicalSlot() { return slot; }
    public PhysicalServicePart getInstalledPart() { return (PhysicalServicePart)slot.getInstalledPart(); }
    public boolean isEmpty() { return !slot.isOccupied(); }
    public boolean acceptsPart(PhysicalPart<?> part) {
        return part instanceof PhysicalServicePart &&
            ((PhysicalServicePart)part).isConnector() == connector &&
            slot.getPhysicalPackage().isEquivalentTo(part.getPackage());
    }
    public CircuitMeasurementEndpoint getExpectedEndpoint(PhysicalPart<?> part, BoardPad pad) {
        if (!acceptsPart(part) || pad == null || !getComponentId().equals(pad.getComponentId()))
            throw new IllegalArgumentException("Foreign service terminal");
        for (PhysicalPartTerminal terminal : part.getTerminals())
            if (terminal.getTerminalName().equals(pad.getTerminalId())) return terminal.getEndpoint();
        throw new IllegalArgumentException("Unknown service terminal");
    }
    private void attach(PhysicalServicePart part) {
        for (int i = 0; i < leads.length; i++) {
            moveEnd(leads[i], (CircuitPostMeasurementEndpoint)part.getTerminal(i).getEndpoint());
            if (docking.length != 0) moveEnd(docking[i],
                new CircuitPostMeasurementEndpoint(i == 0 ? part.primary() : part.secondary(), 1));
        }
    }
    private static void moveEnd(WireElm wire, CircuitPostMeasurementEndpoint endpoint) {
        Point p = endpoint.getElement().getPost(endpoint.getPostIndex());
        wire.x2 = p.x; wire.y2 = p.y; wire.setPoints();
    }
    public void installForMutation(PhysicalPart<?> part, PhysicalMutationScope scope) {
        if (scope == null || !scope.owns(this) || !acceptsPart(part))
            throw new IllegalStateException("Invalid service installation owner");
        attach((PhysicalServicePart)part); scope.afterAttachmentWrite();
        slot.install(part); scope.afterSlotMountWrite();
    }
    public PhysicalPart<?> clearForMutation(PhysicalMutationScope scope) {
        if (scope == null || !scope.owns(this)) throw new IllegalStateException("Foreign service removal");
        PhysicalPart<?> result = slot.remove(); scope.afterSlotClearWrite(); return result;
    }
    public Vector<CircuitElm> getDockingAttachments() {
        Vector<CircuitElm> result = new Vector<CircuitElm>();
        for (WireElm wire : docking) result.add(wire); return result;
    }
    public void validateDockingAttachments() {
        PhysicalServicePart part = isEmpty() ? original : getInstalledPart();
        for (int i = 0; i < docking.length; i++) {
            Point external = harness[i].getElement().getPost(harness[i].getPostIndex());
            Point pin = (i == 0 ? part.primary() : part.secondary()).getPost(1);
            if (!docking[i].getPost(0).equals(external) || !docking[i].getPost(1).equals(pin))
                throw new IllegalStateException("Connector contact changed its electrical endpoint");
        }
    }
    public AttachmentState captureAttachmentState() {
        int[][] points = new int[leads.length + docking.length][4];
        for (int i = 0; i < points.length; i++) {
            WireElm w = i < leads.length ? leads[i] : docking[i - leads.length];
            points[i] = new int[] {w.x, w.y, w.x2, w.y2};
        }
        return new Saved(points);
    }
    public void restoreAttachmentState(AttachmentState state) {
        if (!(state instanceof Saved)) throw new IllegalArgumentException("Missing service attachment state");
        int[][] points = ((Saved)state).points;
        for (int i = 0; i < points.length; i++) {
            WireElm w = i < leads.length ? leads[i] : docking[i - leads.length];
            w.x = points[i][0]; w.y = points[i][1]; w.x2 = points[i][2]; w.y2 = points[i][3]; w.setPoints();
        }
    }
    public void restoreEmptySlotAttachmentState(PhysicalMutationScope scope) {
        if (scope == null || !scope.owns(this)) throw new IllegalStateException("Foreign service attachment restore");
        restoreAttachmentState(empty);
    }
    private static final class Saved implements AttachmentState {
        final int[][] points; Saved(int[][] points) { this.points = points; }
    }
}
