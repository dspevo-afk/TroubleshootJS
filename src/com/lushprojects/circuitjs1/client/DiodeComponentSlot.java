package com.lushprojects.circuitjs1.client;

class DiodeComponentSlot {
    private final String componentId;
    private final DiodeNameplate intendedNameplate;
    private final WireElm anodePadAttachment;
    private final WireElm cathodePadAttachment;
    private PhysicalDiodePart installedPart;

    DiodeComponentSlot(String componentId, DiodeNameplate intendedNameplate,
            PhysicalDiodePart installedPart, WireElm anodePadAttachment,
            WireElm cathodePadAttachment) {
        if (componentId == null || intendedNameplate == null || installedPart == null ||
                anodePadAttachment == null || cathodePadAttachment == null)
            throw new IllegalArgumentException("Invalid diode slot");
        this.componentId = componentId;
        this.intendedNameplate = intendedNameplate;
        this.anodePadAttachment = anodePadAttachment;
        this.cathodePadAttachment = cathodePadAttachment;
        install(installedPart);
    }

    String getComponentId() { return componentId; }
    DiodeNameplate getIntendedNameplate() { return intendedNameplate; }
    PhysicalDiodePart getInstalledPart() { return installedPart; }
    boolean isEmpty() { return installedPart == null; }
    void clear() { installedPart = null; }
    void install(PhysicalDiodePart part) {
        if (part == null)
            throw new IllegalArgumentException("Missing diode part");
        moveAttachmentEnd(anodePadAttachment, part.getTerminalForBoardPad("D1.A"), false);
        moveAttachmentEnd(cathodePadAttachment, part.getTerminalForBoardPad("D1.K"), true);
        installedPart = part;
    }

    private void moveAttachmentEnd(WireElm attachment, CircuitMeasurementEndpoint terminal,
            boolean moveFirstEnd) {
        CircuitPostMeasurementEndpoint post = (CircuitPostMeasurementEndpoint) terminal;
        Point point = post.getElement().getPost(post.getPostIndex());
        if (moveFirstEnd) { attachment.x = point.x; attachment.y = point.y; }
        else { attachment.x2 = point.x; attachment.y2 = point.y; }
        attachment.setPoints();
    }
}
