package com.lushprojects.circuitjs1.client;

/** Family-owned three-terminal board slot with explicit B/C/E attachment order. */
final class NpnComponentSlot implements PhysicalMutationSlot {
    private final String componentId;
    private final NpnSpecification intendedSpecification;
    private final WireElm[] attachments;
    private final PhysicalBoardSlot physicalSlot;
    private final PhysicalMutationSlot.AttachmentState emptySlotAttachmentState;

    NpnComponentSlot(String componentId, NpnSpecification intendedSpecification,
            PhysicalNpnPart installedPart, WireElm baseAttachment,
            WireElm collectorAttachment, WireElm emitterAttachment,
            PhysicalBoardSlot physicalSlot) {
        if (componentId == null || componentId.length() == 0 || intendedSpecification == null ||
                installedPart == null || baseAttachment == null || collectorAttachment == null ||
                emitterAttachment == null || physicalSlot == null)
            throw new IllegalArgumentException("Invalid NPN component slot");
        this.componentId = componentId;
        this.intendedSpecification = intendedSpecification;
        this.attachments = new WireElm[] { baseAttachment, collectorAttachment, emitterAttachment };
        this.physicalSlot = physicalSlot;
        attach(installedPart);
        physicalSlot.install(installedPart);
        this.emptySlotAttachmentState = captureAttachmentState();
    }

    public String getComponentId() { return componentId; }
    NpnSpecification getIntendedSpecification() { return intendedSpecification; }
    public PhysicalBoardSlot getPhysicalSlot() { return physicalSlot; }
    public PhysicalNpnPart getInstalledPart() { return (PhysicalNpnPart) physicalSlot.getInstalledPart(); }
    public boolean isEmpty() { return !physicalSlot.isOccupied(); }
    public boolean acceptsPart(PhysicalPart<?> part) {
        return part instanceof PhysicalNpnPart;
    }
    public CircuitMeasurementEndpoint getExpectedEndpoint(PhysicalPart<?> part, BoardPad pad) {
        if (!acceptsPart(part) || pad == null || !componentId.equals(pad.getComponentId()))
            throw new IllegalArgumentException("Foreign NPN terminal mapping");
        return ((PhysicalNpnPart) part).getTerminalForBoardPad(pad.getId());
    }
    void clear() { physicalSlot.remove(); }

    void install(PhysicalNpnPart part) {
        if (part == null)
            throw new IllegalArgumentException("Missing NPN part");
        attach(part);
        physicalSlot.install(part);
    }

    public void installForMutation(PhysicalPart<?> candidate, PhysicalMutationScope scope) {
        if (!(candidate instanceof PhysicalNpnPart) || scope == null)
            throw new IllegalArgumentException("Missing NPN mutation install context");
        if (!scope.owns(this))
            throw new IllegalStateException("Physical mutation scope does not own NPN slot");
        attach((PhysicalNpnPart) candidate);
        scope.afterAttachmentWrite();
        physicalSlot.install((PhysicalNpnPart) candidate);
        scope.afterSlotMountWrite();
    }

    public PhysicalPart<?> clearForMutation(PhysicalMutationScope scope) {
        if (scope == null || !scope.owns(this))
            throw new IllegalStateException("Physical mutation scope does not own NPN slot");
        PhysicalPart<?> removed = physicalSlot.remove();
        scope.afterSlotClearWrite();
        return removed;
    }

    public PhysicalMutationSlot.AttachmentState captureAttachmentState() {
        return new NpnAttachmentState(attachments);
    }

    public void restoreAttachmentState(PhysicalMutationSlot.AttachmentState captured) {
        if (!(captured instanceof NpnAttachmentState))
            throw new IllegalArgumentException("Missing NPN attachment state");
        NpnAttachmentState state = (NpnAttachmentState) captured;
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
            throw new IllegalStateException("Physical mutation scope does not own NPN slot");
        restoreAttachmentState(emptySlotAttachmentState);
    }

    private static final class NpnAttachmentState implements PhysicalMutationSlot.AttachmentState {
        private final int[] coordinates = new int[12];

        private NpnAttachmentState(WireElm[] attachments) {
            for (int index = 0; index < attachments.length; index++) {
                coordinates[index * 4] = attachments[index].x;
                coordinates[index * 4 + 1] = attachments[index].y;
                coordinates[index * 4 + 2] = attachments[index].x2;
                coordinates[index * 4 + 3] = attachments[index].y2;
            }
        }
    }

    private void attach(PhysicalNpnPart part) {
        for (int index = 0; index < attachments.length; index++)
            moveAttachmentEnd(attachments[index], part.getPublicTerminal(index), false);
    }

    private void moveAttachmentEnd(WireElm attachment, CircuitMeasurementEndpoint endpoint,
            boolean moveFirstEnd) {
        if (!(endpoint instanceof CircuitPostMeasurementEndpoint))
            throw new IllegalStateException("Unsupported NPN terminal endpoint");
        CircuitPostMeasurementEndpoint post = (CircuitPostMeasurementEndpoint) endpoint;
        Point point = post.getElement().getPost(post.getPostIndex());
        if (moveFirstEnd) {
            attachment.x = point.x;
            attachment.y = point.y;
        } else {
            attachment.x2 = point.x;
            attachment.y2 = point.y;
        }
        attachment.setPoints();
    }
}
