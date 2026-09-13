package com.lushprojects.circuitjs1.client;

class LedComponentSlot implements PhysicalMutationSlot {
    private final String componentId;
    private final LedNameplate intendedNameplate;
    private final WireElm anodePadAttachment;
    private final WireElm cathodePadAttachment;
    private final PhysicalBoardSlot physicalSlot;
    private final PhysicalMutationSlot.AttachmentState emptySlotAttachmentState;

    LedComponentSlot(String componentId, LedNameplate intendedNameplate, PhysicalLedPart installedPart,
            WireElm anodePadAttachment, WireElm cathodePadAttachment, PhysicalBoardSlot physicalSlot) {
        if (componentId == null || intendedNameplate == null || installedPart == null ||
                anodePadAttachment == null || cathodePadAttachment == null || physicalSlot == null)
            throw new IllegalArgumentException("Invalid LED slot");
        this.componentId = componentId;
        this.intendedNameplate = intendedNameplate;
        this.anodePadAttachment = anodePadAttachment;
        this.cathodePadAttachment = cathodePadAttachment;
        this.physicalSlot = physicalSlot;
        install(installedPart);
        this.emptySlotAttachmentState = captureAttachmentState();
    }

    public String getComponentId() { return componentId; }
    LedNameplate getIntendedNameplate() { return intendedNameplate; }
    public PhysicalBoardSlot getPhysicalSlot() { return physicalSlot; }
    public PhysicalLedPart getInstalledPart() { return (PhysicalLedPart) physicalSlot.getInstalledPart(); }
    public boolean isEmpty() { return !physicalSlot.isOccupied(); }
    public boolean acceptsPart(PhysicalPart<?> part) {
        return part instanceof PhysicalLedPart;
    }
    public CircuitMeasurementEndpoint getExpectedEndpoint(PhysicalPart<?> part, BoardPad pad) {
        if (!acceptsPart(part) || pad == null || !componentId.equals(pad.getComponentId()))
            throw new IllegalArgumentException("Foreign LED terminal mapping");
        return ((PhysicalLedPart) part).getTerminalForBoardPad(pad.getId());
    }
    void clear() { physicalSlot.remove(); }
    void install(PhysicalLedPart part) {
        if (part == null) throw new IllegalArgumentException("Missing LED part");
        moveAttachmentEnd(anodePadAttachment, part.getTerminalForBoardPad(componentId + ".A"), false);
        moveAttachmentEnd(cathodePadAttachment, part.getTerminalForBoardPad(componentId + ".K"), true);
        physicalSlot.install(part);
    }

    public void installForMutation(PhysicalPart<?> candidate, PhysicalMutationScope scope) {
        if (!(candidate instanceof PhysicalLedPart) || scope == null)
            throw new IllegalArgumentException("Missing LED mutation install context");
        if (!scope.owns(this))
            throw new IllegalStateException("Physical mutation scope does not own LED slot");
        PhysicalLedPart part = (PhysicalLedPart) candidate;
        moveAttachmentEnd(anodePadAttachment,
            part.getTerminalForBoardPad(componentId + ".A"), false);
        moveAttachmentEnd(cathodePadAttachment,
            part.getTerminalForBoardPad(componentId + ".K"), true);
        scope.afterAttachmentWrite();
        physicalSlot.install(part);
        scope.afterSlotMountWrite();
    }

    public PhysicalPart<?> clearForMutation(PhysicalMutationScope scope) {
        if (scope == null || !scope.owns(this))
            throw new IllegalStateException("Physical mutation scope does not own LED slot");
        PhysicalPart<?> removed = physicalSlot.remove();
        scope.afterSlotClearWrite();
        return removed;
    }

    public PhysicalMutationSlot.AttachmentState captureAttachmentState() {
        return new LedAttachmentState(anodePadAttachment.x, anodePadAttachment.y,
            anodePadAttachment.x2, anodePadAttachment.y2, cathodePadAttachment.x,
            cathodePadAttachment.y, cathodePadAttachment.x2, cathodePadAttachment.y2);
    }

    public void restoreAttachmentState(PhysicalMutationSlot.AttachmentState captured) {
        if (!(captured instanceof LedAttachmentState))
            throw new IllegalArgumentException("Missing LED attachment state");
        LedAttachmentState state = (LedAttachmentState) captured;
        anodePadAttachment.x = state.anodeX;
        anodePadAttachment.y = state.anodeY;
        anodePadAttachment.x2 = state.anodeX2;
        anodePadAttachment.y2 = state.anodeY2;
        anodePadAttachment.setPoints();
        cathodePadAttachment.x = state.cathodeX;
        cathodePadAttachment.y = state.cathodeY;
        cathodePadAttachment.x2 = state.cathodeX2;
        cathodePadAttachment.y2 = state.cathodeY2;
        cathodePadAttachment.setPoints();
    }

    public void restoreEmptySlotAttachmentState(PhysicalMutationScope scope) {
        if (scope == null || !scope.owns(this))
            throw new IllegalStateException("Physical mutation scope does not own LED slot");
        restoreAttachmentState(emptySlotAttachmentState);
    }

    private static final class LedAttachmentState implements PhysicalMutationSlot.AttachmentState {
        private final int anodeX;
        private final int anodeY;
        private final int anodeX2;
        private final int anodeY2;
        private final int cathodeX;
        private final int cathodeY;
        private final int cathodeX2;
        private final int cathodeY2;

        private LedAttachmentState(int anodeX, int anodeY, int anodeX2, int anodeY2,
                int cathodeX, int cathodeY, int cathodeX2, int cathodeY2) {
            this.anodeX = anodeX;
            this.anodeY = anodeY;
            this.anodeX2 = anodeX2;
            this.anodeY2 = anodeY2;
            this.cathodeX = cathodeX;
            this.cathodeY = cathodeY;
            this.cathodeX2 = cathodeX2;
            this.cathodeY2 = cathodeY2;
        }
    }

    private void moveAttachmentEnd(WireElm attachment, CircuitMeasurementEndpoint endpoint,
            boolean moveFirstEnd) {
        if (!(endpoint instanceof CircuitPostMeasurementEndpoint))
            throw new IllegalStateException("Unsupported LED terminal endpoint");
        CircuitPostMeasurementEndpoint post = (CircuitPostMeasurementEndpoint) endpoint;
        Point point = post.getElement().getPost(post.getPostIndex());
        if (moveFirstEnd) { attachment.x = point.x; attachment.y = point.y; }
        else { attachment.x2 = point.x; attachment.y2 = point.y; }
        attachment.setPoints();
    }
}
