package com.lushprojects.circuitjs1.client;

/** Family-owned three-terminal slot with explicit physical G/D/S attachment order. */
final class NmosComponentSlot implements PhysicalMutationSlot {
    private final String componentId;
    private final NmosSpecification intendedSpecification;
    private final WireElm[] attachments;
    private final PhysicalBoardSlot physicalSlot;
    private final PhysicalMutationSlot.AttachmentState emptySlotAttachmentState;

    NmosComponentSlot(String componentId, NmosSpecification intendedSpecification,
            PhysicalNmosPart installedPart, WireElm gateAttachment,
            WireElm drainAttachment, WireElm sourceAttachment, PhysicalBoardSlot physicalSlot) {
        if (componentId == null || componentId.length() == 0 || intendedSpecification == null ||
                installedPart == null || gateAttachment == null || drainAttachment == null ||
                sourceAttachment == null || physicalSlot == null)
            throw new IllegalArgumentException("Invalid NMOS component slot");
        this.componentId = componentId;
        this.intendedSpecification = intendedSpecification;
        this.attachments = new WireElm[] { gateAttachment, drainAttachment, sourceAttachment };
        this.physicalSlot = physicalSlot;
        attach(installedPart);
        physicalSlot.install(installedPart);
        this.emptySlotAttachmentState = captureAttachmentState();
    }

    public String getComponentId() { return componentId; }
    NmosSpecification getIntendedSpecification() { return intendedSpecification; }
    public PhysicalBoardSlot getPhysicalSlot() { return physicalSlot; }
    public PhysicalNmosPart getInstalledPart() { return (PhysicalNmosPart) physicalSlot.getInstalledPart(); }
    public boolean isEmpty() { return !physicalSlot.isOccupied(); }
    public boolean acceptsPart(PhysicalPart<?> part) {
        return part instanceof PhysicalNmosPart;
    }
    public CircuitMeasurementEndpoint getExpectedEndpoint(PhysicalPart<?> part, BoardPad pad) {
        if (!acceptsPart(part) || pad == null || !componentId.equals(pad.getComponentId()))
            throw new IllegalArgumentException("Foreign NMOS terminal mapping");
        return ((PhysicalNmosPart) part).getTerminalForBoardPad(pad.getId());
    }
    void clear() { physicalSlot.remove(); }

    void install(PhysicalNmosPart part) {
        if (part == null)
            throw new IllegalArgumentException("Missing NMOS part");
        attach(part);
        physicalSlot.install(part);
    }

    public void installForMutation(PhysicalPart<?> candidate, PhysicalMutationScope scope) {
        if (!(candidate instanceof PhysicalNmosPart) || scope == null)
            throw new IllegalArgumentException("Missing NMOS mutation install context");
        if (!scope.owns(this))
            throw new IllegalStateException("Physical mutation scope does not own NMOS slot");
        attach((PhysicalNmosPart) candidate);
        scope.afterAttachmentWrite();
        physicalSlot.install((PhysicalNmosPart) candidate);
        scope.afterSlotMountWrite();
    }

    public PhysicalPart<?> clearForMutation(PhysicalMutationScope scope) {
        if (scope == null || !scope.owns(this))
            throw new IllegalStateException("Physical mutation scope does not own NMOS slot");
        PhysicalPart<?> removed = physicalSlot.remove();
        scope.afterSlotClearWrite();
        return removed;
    }

    public PhysicalMutationSlot.AttachmentState captureAttachmentState() {
        return new NmosAttachmentState(attachments);
    }

    public void restoreAttachmentState(PhysicalMutationSlot.AttachmentState captured) {
        if (!(captured instanceof NmosAttachmentState))
            throw new IllegalArgumentException("Missing NMOS attachment state");
        NmosAttachmentState state = (NmosAttachmentState) captured;
        for (int index = 0; index < attachments.length; index++) {
            attachments[index].x = state.coordinates[index * 4];
            attachments[index].y = state.coordinates[index * 4 + 1];
            attachments[index].x2 = state.coordinates[index * 4 + 2];
            attachments[index].y2 = state.coordinates[index * 4 + 3];
            attachments[index].setPoints();
        }
    }

    public void restoreEmptySlotAttachmentState(PhysicalMutationScope scope) {
        if (scope == null || !scope.owns(this))
            throw new IllegalStateException("Physical mutation scope does not own NMOS slot");
        restoreAttachmentState(emptySlotAttachmentState);
    }

    private static final class NmosAttachmentState implements PhysicalMutationSlot.AttachmentState {
        private final int[] coordinates = new int[12];

        private NmosAttachmentState(WireElm[] attachments) {
            for (int index = 0; index < attachments.length; index++) {
                coordinates[index * 4] = attachments[index].x;
                coordinates[index * 4 + 1] = attachments[index].y;
                coordinates[index * 4 + 2] = attachments[index].x2;
                coordinates[index * 4 + 3] = attachments[index].y2;
            }
        }
    }

    private void attach(PhysicalNmosPart part) {
        for (int index = 0; index < attachments.length; index++)
            moveAttachmentEnd(attachments[index], part.getPublicTerminal(index));
    }

    private void moveAttachmentEnd(WireElm attachment, CircuitMeasurementEndpoint endpoint) {
        if (!(endpoint instanceof CircuitPostMeasurementEndpoint))
            throw new IllegalStateException("Unsupported NMOS terminal endpoint");
        CircuitPostMeasurementEndpoint post = (CircuitPostMeasurementEndpoint) endpoint;
        Point point = post.getElement().getPost(post.getPostIndex());
        attachment.x2 = point.x;
        attachment.y2 = point.y;
        attachment.setPoints();
    }
}
