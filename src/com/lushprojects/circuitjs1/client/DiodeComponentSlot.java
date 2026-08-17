package com.lushprojects.circuitjs1.client;

class DiodeComponentSlot {
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

    String getComponentId() { return componentId; }
    DiodeNameplate getIntendedNameplate() { return intendedNameplate; }
    PhysicalBoardSlot getPhysicalSlot() { return physicalSlot; }
    PhysicalDiodePart getInstalledPart() { return (PhysicalDiodePart) physicalSlot.getInstalledPart(); }
    boolean isEmpty() { return !physicalSlot.isOccupied(); }
    void clear() { physicalSlot.remove(); }
    void install(PhysicalDiodePart part) {
        if (part == null) throw new IllegalArgumentException("Missing diode part");
        moveAttachmentEnd(anodePadAttachment, part.getTerminalForBoardPad(componentId + ".A"), false);
        moveAttachmentEnd(cathodePadAttachment, part.getTerminalForBoardPad(componentId + ".K"), true);
        physicalSlot.install(part);
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
