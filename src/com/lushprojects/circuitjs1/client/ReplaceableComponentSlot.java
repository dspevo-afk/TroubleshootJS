package com.lushprojects.circuitjs1.client;

class ReplaceableComponentSlot {
    private final String componentId;
    private final ResistorNameplate intendedNameplate;
    private final WireElm firstAttachment;
    private final WireElm secondAttachment;
    private final PhysicalBoardSlot physicalSlot;

    ReplaceableComponentSlot(String componentId, ResistorNameplate intendedNameplate,
            PhysicalResistorPart installedPart, WireElm firstAttachment, WireElm secondAttachment,
            PhysicalBoardSlot physicalSlot) {
        if (componentId == null || componentId.length() == 0 || intendedNameplate == null ||
                installedPart == null || firstAttachment == null || secondAttachment == null ||
                physicalSlot == null)
            throw new IllegalArgumentException("Invalid replaceable component slot");
        this.componentId = componentId;
        this.intendedNameplate = intendedNameplate;
        this.firstAttachment = firstAttachment;
        this.secondAttachment = secondAttachment;
        this.physicalSlot = physicalSlot;
        attach(installedPart);
        physicalSlot.install(installedPart);
    }

    String getComponentId() { return componentId; }
    ResistorNameplate getIntendedNameplate() { return intendedNameplate; }
    PhysicalBoardSlot getPhysicalSlot() { return physicalSlot; }
    PhysicalResistorPart getInstalledPart() {
        return (PhysicalResistorPart) physicalSlot.getInstalledPart();
    }
    boolean isEmpty() { return !physicalSlot.isOccupied(); }
    void clear() { physicalSlot.remove(); }
    void install(PhysicalResistorPart part) {
        if (part == null) throw new IllegalArgumentException("Missing resistor part");
        attach(part);
        physicalSlot.install(part);
    }

    void installForMutation(PhysicalResistorPart part, ResistorMutationScope scope) {
        if (part == null || scope == null)
            throw new IllegalArgumentException("Missing resistor mutation install context");
        if (!scope.owns(this))
            throw new IllegalStateException("Resistor mutation scope does not own slot");
        attach(part);
        scope.afterAttachmentWrite();
        physicalSlot.install(part);
        scope.afterSlotMountWrite();
    }

    PhysicalPart<?> clearForMutation(ResistorMutationScope scope) {
        if (scope == null || !scope.owns(this))
            throw new IllegalStateException("Resistor mutation scope does not own slot");
        PhysicalPart<?> removed = physicalSlot.remove();
        scope.afterSlotClearWrite();
        return removed;
    }

    AttachmentState captureAttachmentState() {
        return new AttachmentState(firstAttachment.x, firstAttachment.y,
            firstAttachment.x2, firstAttachment.y2, secondAttachment.x,
            secondAttachment.y, secondAttachment.x2, secondAttachment.y2);
    }

    void restoreAttachmentState(AttachmentState state) {
        if (state == null)
            throw new IllegalArgumentException("Missing resistor attachment state");
        firstAttachment.x = state.firstX;
        firstAttachment.y = state.firstY;
        firstAttachment.x2 = state.firstX2;
        firstAttachment.y2 = state.firstY2;
        firstAttachment.setPoints();
        secondAttachment.x = state.secondX;
        secondAttachment.y = state.secondY;
        secondAttachment.x2 = state.secondX2;
        secondAttachment.y2 = state.secondY2;
        secondAttachment.setPoints();
    }

    static final class AttachmentState {
        private final int firstX;
        private final int firstY;
        private final int firstX2;
        private final int firstY2;
        private final int secondX;
        private final int secondY;
        private final int secondX2;
        private final int secondY2;

        private AttachmentState(int firstX, int firstY, int firstX2, int firstY2,
                int secondX, int secondY, int secondX2, int secondY2) {
            this.firstX = firstX;
            this.firstY = firstY;
            this.firstX2 = firstX2;
            this.firstY2 = firstY2;
            this.secondX = secondX;
            this.secondY = secondY;
            this.secondX2 = secondX2;
            this.secondY2 = secondY2;
        }
    }

    private void attach(PhysicalResistorPart part) {
        moveAttachmentEnd(firstAttachment, part.getPublicTerminal(0), false);
        moveAttachmentEnd(secondAttachment, part.getPublicTerminal(1), true);
    }

    private void moveAttachmentEnd(WireElm attachment, CircuitMeasurementEndpoint terminal,
            boolean moveFirstEnd) {
        if (!(terminal instanceof CircuitPostMeasurementEndpoint))
            throw new IllegalStateException("Unsupported resistor part terminal");
        CircuitPostMeasurementEndpoint post = (CircuitPostMeasurementEndpoint) terminal;
        Point point = post.getElement().getPost(post.getPostIndex());
        if (moveFirstEnd) { attachment.x = point.x; attachment.y = point.y; }
        else { attachment.x2 = point.x; attachment.y2 = point.y; }
        attachment.setPoints();
    }
}
