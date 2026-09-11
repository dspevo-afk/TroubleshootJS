package com.lushprojects.circuitjs1.client;

class DiodeComponentSlot implements PhysicalMutationSlot {
    private final String componentId;
    private final DiodeNameplate intendedNameplate;
    private final WireElm anodePadAttachment;
    private final WireElm cathodePadAttachment;
    private final PhysicalBoardSlot physicalSlot;

    DiodeComponentSlot(String componentId, DiodeNameplate intendedNameplate,
            PhysicalDiodePart installedPart, WireElm anodePadAttachment,
            WireElm cathodePadAttachment, PhysicalBoardSlot physicalSlot) {
        if (componentId == null || intendedNameplate == null || installedPart == null ||
                anodePadAttachment == null || cathodePadAttachment == null || physicalSlot == null)
            throw new IllegalArgumentException("Invalid diode slot");
        this.componentId = componentId;
        this.intendedNameplate = intendedNameplate;
        this.anodePadAttachment = anodePadAttachment;
        this.cathodePadAttachment = cathodePadAttachment;
        this.physicalSlot = physicalSlot;
        install(installedPart);
    }

    public String getComponentId() { return componentId; }
    DiodeNameplate getIntendedNameplate() { return intendedNameplate; }
    public PhysicalBoardSlot getPhysicalSlot() { return physicalSlot; }
    public PhysicalDiodePart getInstalledPart() { return (PhysicalDiodePart) physicalSlot.getInstalledPart(); }
    public boolean isEmpty() { return !physicalSlot.isOccupied(); }
    public boolean acceptsPart(PhysicalPart<?> part) {
        return part instanceof PhysicalDiodePart;
    }
    public CircuitMeasurementEndpoint getExpectedEndpoint(PhysicalPart<?> part, BoardPad pad) {
        if (!acceptsPart(part) || pad == null || !componentId.equals(pad.getComponentId()))
            throw new IllegalArgumentException("Foreign diode terminal mapping");
        return ((PhysicalDiodePart) part).getTerminalForBoardPad(pad.getId());
    }
    void clear() { physicalSlot.remove(); }
    void install(PhysicalDiodePart part) {
        if (part == null) throw new IllegalArgumentException("Missing diode part");
        moveAttachmentEnd(anodePadAttachment, part.getTerminalForBoardPad(componentId + ".A"), false);
        moveAttachmentEnd(cathodePadAttachment, part.getTerminalForBoardPad(componentId + ".K"), true);
        physicalSlot.install(part);
    }

    public void installForMutation(PhysicalPart<?> candidate, PhysicalMutationScope scope) {
        if (!(candidate instanceof PhysicalDiodePart) || scope == null)
            throw new IllegalArgumentException("Missing diode mutation install context");
        if (!scope.owns(this))
            throw new IllegalStateException("Physical mutation scope does not own diode slot");
        PhysicalDiodePart part = (PhysicalDiodePart) candidate;
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
            throw new IllegalStateException("Physical mutation scope does not own diode slot");
        PhysicalPart<?> removed = physicalSlot.remove();
        scope.afterSlotClearWrite();
        return removed;
    }

    public PhysicalMutationSlot.AttachmentState captureAttachmentState() {
        return new DiodeAttachmentState(anodePadAttachment.x, anodePadAttachment.y,
            anodePadAttachment.x2, anodePadAttachment.y2, cathodePadAttachment.x,
            cathodePadAttachment.y, cathodePadAttachment.x2, cathodePadAttachment.y2);
    }

    public void restoreAttachmentState(PhysicalMutationSlot.AttachmentState captured) {
        if (!(captured instanceof DiodeAttachmentState))
            throw new IllegalArgumentException("Missing diode attachment state");
        DiodeAttachmentState state = (DiodeAttachmentState) captured;
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

    private static final class DiodeAttachmentState implements PhysicalMutationSlot.AttachmentState {
        private final int anodeX;
        private final int anodeY;
        private final int anodeX2;
        private final int anodeY2;
        private final int cathodeX;
        private final int cathodeY;
        private final int cathodeX2;
        private final int cathodeY2;

        private DiodeAttachmentState(int anodeX, int anodeY, int anodeX2, int anodeY2,
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
        CircuitPostMeasurementEndpoint post = (CircuitPostMeasurementEndpoint) endpoint;
        Point point = post.getElement().getPost(post.getPostIndex());
        if (moveFirstEnd) { attachment.x = point.x; attachment.y = point.y; }
        else { attachment.x2 = point.x; attachment.y2 = point.y; }
        attachment.setPoints();
    }
}
