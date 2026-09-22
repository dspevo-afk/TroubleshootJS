package com.lushprojects.circuitjs1.client;

/** Family-owned four-terminal slot with INPUT/OUTPUT/RETURN/ENABLE leads. */
final class RegulatorComponentSlot implements PhysicalMutationSlot {
    private final String componentId;
    private final RailRegulationContract intendedContract;
    private final WireElm[] attachments;
    private final PhysicalBoardSlot physicalSlot;
    private final PhysicalMutationSlot.AttachmentState emptySlotAttachmentState;

    RegulatorComponentSlot(String componentId, RailRegulationContract intendedContract,
            PhysicalRegulatorPart installedPart, WireElm inputAttachment,
            WireElm outputAttachment, WireElm returnAttachment,
            WireElm enableAttachment, PhysicalBoardSlot physicalSlot) {
        if (componentId == null || componentId.length() == 0 || intendedContract == null ||
                installedPart == null || inputAttachment == null || outputAttachment == null ||
                returnAttachment == null || enableAttachment == null || physicalSlot == null)
            throw new IllegalArgumentException("Invalid regulator component slot");
        if (installedPart.getContract() != intendedContract)
            throw new IllegalArgumentException("Regulator slot contract does not match original part");
        this.componentId = componentId;
        this.intendedContract = intendedContract;
        this.attachments = new WireElm[] {
            inputAttachment, outputAttachment, returnAttachment, enableAttachment };
        this.physicalSlot = physicalSlot;
        attach(installedPart);
        physicalSlot.install(installedPart);
        this.emptySlotAttachmentState = captureAttachmentState();
    }

    public String getComponentId() { return componentId; }
    RailRegulationContract getIntendedContract() { return intendedContract; }
    public PhysicalBoardSlot getPhysicalSlot() { return physicalSlot; }
    public PhysicalRegulatorPart getInstalledPart() {
        return (PhysicalRegulatorPart) physicalSlot.getInstalledPart();
    }
    public boolean isEmpty() { return !physicalSlot.isOccupied(); }

    public boolean acceptsPart(PhysicalPart<?> part) {
        return part instanceof PhysicalRegulatorPart &&
            ((PhysicalRegulatorPart) part).getContract() == intendedContract;
    }

    public CircuitMeasurementEndpoint getExpectedEndpoint(PhysicalPart<?> part, BoardPad pad) {
        if (!acceptsPart(part) || pad == null || !componentId.equals(pad.getComponentId()))
            throw new IllegalArgumentException("Foreign regulator terminal mapping");
        return ((PhysicalRegulatorPart) part).getTerminalForBoardPad(pad.getId());
    }

    void clear() { physicalSlot.remove(); }

    void install(PhysicalRegulatorPart part) {
        if (part == null || !acceptsPart(part))
            throw new IllegalArgumentException("Missing compatible regulator part");
        attach(part);
        physicalSlot.install(part);
    }

    public void installForMutation(PhysicalPart<?> candidate, PhysicalMutationScope scope) {
        if (!(candidate instanceof PhysicalRegulatorPart) || scope == null)
            throw new IllegalArgumentException("Missing regulator mutation install context");
        if (!scope.owns(this))
            throw new IllegalStateException("Physical mutation scope does not own regulator slot");
        if (!acceptsPart(candidate))
            throw new IllegalArgumentException("Regulator candidate uses another rail contract");
        attach((PhysicalRegulatorPart) candidate);
        scope.afterAttachmentWrite();
        physicalSlot.install((PhysicalRegulatorPart) candidate);
        scope.afterSlotMountWrite();
    }

    public PhysicalPart<?> clearForMutation(PhysicalMutationScope scope) {
        if (scope == null || !scope.owns(this))
            throw new IllegalStateException("Physical mutation scope does not own regulator slot");
        PhysicalPart<?> removed = physicalSlot.remove();
        scope.afterSlotClearWrite();
        return removed;
    }

    public PhysicalMutationSlot.AttachmentState captureAttachmentState() {
        return new RegulatorAttachmentState(attachments);
    }

    public void restoreAttachmentState(PhysicalMutationSlot.AttachmentState captured) {
        if (!(captured instanceof RegulatorAttachmentState))
            throw new IllegalArgumentException("Missing regulator attachment state");
        RegulatorAttachmentState state = (RegulatorAttachmentState) captured;
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
            throw new IllegalStateException("Physical mutation scope does not own regulator slot");
        restoreAttachmentState(emptySlotAttachmentState);
    }

    private void attach(PhysicalRegulatorPart part) {
        for (int index = 0; index < attachments.length; index++)
            moveAttachmentEnd(attachments[index], part.getPublicTerminal(index));
    }

    private void moveAttachmentEnd(WireElm attachment, CircuitMeasurementEndpoint endpoint) {
        if (!(endpoint instanceof CircuitPostMeasurementEndpoint))
            throw new IllegalStateException("Unsupported regulator terminal endpoint");
        CircuitPostMeasurementEndpoint post = (CircuitPostMeasurementEndpoint) endpoint;
        Point point = post.getElement().getPost(post.getPostIndex());
        if (point == null)
            throw new IllegalStateException("Regulator terminal has no CircuitJS post");
        attachment.x2 = point.x;
        attachment.y2 = point.y;
        attachment.setPoints();
    }

    private static final class RegulatorAttachmentState
            implements PhysicalMutationSlot.AttachmentState {
        private final int[] coordinates = new int[16];

        private RegulatorAttachmentState(WireElm[] attachments) {
            for (int index = 0; index < attachments.length; index++) {
                coordinates[index * 4] = attachments[index].x;
                coordinates[index * 4 + 1] = attachments[index].y;
                coordinates[index * 4 + 2] = attachments[index].x2;
                coordinates[index * 4 + 3] = attachments[index].y2;
            }
        }
    }
}
