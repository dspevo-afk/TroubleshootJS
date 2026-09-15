package com.lushprojects.circuitjs1.client;

/** Family-owned attachment seam for one polarized capacitor board location. */
final class CapacitorComponentSlot implements PhysicalMutationSlot {
    private final String componentId;
    private final CapacitorSpecification intendedSpecification;
    private final WireElm positiveAttachment;
    private final WireElm negativeAttachment;
    private final PhysicalBoardSlot physicalSlot;
    private final PhysicalMutationSlot.AttachmentState emptySlotAttachmentState;

    CapacitorComponentSlot(String componentId, CapacitorSpecification intendedSpecification,
            PhysicalCapacitorPart installedPart, WireElm positiveAttachment,
            WireElm negativeAttachment, PhysicalBoardSlot physicalSlot) {
        if (componentId == null || componentId.length() == 0 || intendedSpecification == null ||
                installedPart == null || positiveAttachment == null || negativeAttachment == null ||
                physicalSlot == null)
            throw new IllegalArgumentException("Invalid capacitor component slot");
        this.componentId = componentId;
        this.intendedSpecification = intendedSpecification;
        this.positiveAttachment = positiveAttachment;
        this.negativeAttachment = negativeAttachment;
        this.physicalSlot = physicalSlot;
        install(installedPart);
        this.emptySlotAttachmentState = captureAttachmentState();
    }

    public String getComponentId() { return componentId; }
    CapacitorSpecification getIntendedSpecification() { return intendedSpecification; }
    public PhysicalBoardSlot getPhysicalSlot() { return physicalSlot; }
    public PhysicalCapacitorPart getInstalledPart() {
        return (PhysicalCapacitorPart) physicalSlot.getInstalledPart();
    }
    public boolean isEmpty() { return !physicalSlot.isOccupied(); }
    public boolean acceptsPart(PhysicalPart<?> part) {
        return part instanceof PhysicalCapacitorPart;
    }
    public CircuitMeasurementEndpoint getExpectedEndpoint(PhysicalPart<?> part, BoardPad pad) {
        if (!acceptsPart(part) || pad == null || !componentId.equals(pad.getComponentId()))
            throw new IllegalArgumentException("Foreign capacitor terminal mapping");
        return ((PhysicalCapacitorPart) part).getTerminalForBoardPad(pad.getId());
    }
    void clear() { physicalSlot.remove(); }

    void install(PhysicalCapacitorPart part) {
        if (part == null) throw new IllegalArgumentException("Missing capacitor part");
        moveAttachmentEnd(positiveAttachment, part.getTerminalForBoardPad(terminalPad(0)),
            false);
        moveAttachmentEnd(negativeAttachment, part.getTerminalForBoardPad(terminalPad(1)),
            true);
        physicalSlot.install(part);
    }

    public void installForMutation(PhysicalPart<?> candidate, PhysicalMutationScope scope) {
        if (!(candidate instanceof PhysicalCapacitorPart) || scope == null)
            throw new IllegalArgumentException("Missing capacitor mutation install context");
        if (!scope.owns(this))
            throw new IllegalStateException("Physical mutation scope does not own capacitor slot");
        PhysicalCapacitorPart part = (PhysicalCapacitorPart) candidate;
        moveAttachmentEnd(positiveAttachment,
            part.getTerminalForBoardPad(terminalPad(0)), false);
        moveAttachmentEnd(negativeAttachment,
            part.getTerminalForBoardPad(terminalPad(1)), true);
        scope.afterAttachmentWrite();
        physicalSlot.install(part);
        scope.afterSlotMountWrite();
    }

    public PhysicalPart<?> clearForMutation(PhysicalMutationScope scope) {
        if (scope == null || !scope.owns(this))
            throw new IllegalStateException("Physical mutation scope does not own capacitor slot");
        PhysicalPart<?> removed = physicalSlot.remove();
        scope.afterSlotClearWrite();
        return removed;
    }

    private String terminalPad(int terminal) {
        return componentId + "." + physicalSlot.getPhysicalPackage().getTerminalIds().get(terminal);
    }

    public PhysicalMutationSlot.AttachmentState captureAttachmentState() {
        return new CapacitorAttachmentState(positiveAttachment.x, positiveAttachment.y,
            positiveAttachment.x2, positiveAttachment.y2, negativeAttachment.x,
            negativeAttachment.y, negativeAttachment.x2, negativeAttachment.y2);
    }

    public void restoreAttachmentState(PhysicalMutationSlot.AttachmentState captured) {
        if (!(captured instanceof CapacitorAttachmentState))
            throw new IllegalArgumentException("Missing capacitor attachment state");
        CapacitorAttachmentState state = (CapacitorAttachmentState) captured;
        positiveAttachment.x = state.positiveX;
        positiveAttachment.y = state.positiveY;
        positiveAttachment.x2 = state.positiveX2;
        positiveAttachment.y2 = state.positiveY2;
        positiveAttachment.setPoints();
        negativeAttachment.x = state.negativeX;
        negativeAttachment.y = state.negativeY;
        negativeAttachment.x2 = state.negativeX2;
        negativeAttachment.y2 = state.negativeY2;
        negativeAttachment.setPoints();
    }

    public void restoreEmptySlotAttachmentState(PhysicalMutationScope scope) {
        if (scope == null || !scope.owns(this))
            throw new IllegalStateException("Physical mutation scope does not own capacitor slot");
        restoreAttachmentState(emptySlotAttachmentState);
    }

    private static final class CapacitorAttachmentState implements PhysicalMutationSlot.AttachmentState {
        private final int positiveX;
        private final int positiveY;
        private final int positiveX2;
        private final int positiveY2;
        private final int negativeX;
        private final int negativeY;
        private final int negativeX2;
        private final int negativeY2;

        private CapacitorAttachmentState(int positiveX, int positiveY, int positiveX2,
                int positiveY2, int negativeX, int negativeY, int negativeX2,
                int negativeY2) {
            this.positiveX = positiveX;
            this.positiveY = positiveY;
            this.positiveX2 = positiveX2;
            this.positiveY2 = positiveY2;
            this.negativeX = negativeX;
            this.negativeY = negativeY;
            this.negativeX2 = negativeX2;
            this.negativeY2 = negativeY2;
        }
    }

    private void moveAttachmentEnd(WireElm attachment, CircuitMeasurementEndpoint endpoint,
            boolean moveFirstEnd) {
        if (!(endpoint instanceof CircuitPostMeasurementEndpoint))
            throw new IllegalStateException("Unsupported capacitor terminal endpoint");
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
