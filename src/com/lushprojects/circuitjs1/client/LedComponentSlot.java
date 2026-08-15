package com.lushprojects.circuitjs1.client;

class LedComponentSlot {
    private final String componentId;
    private final LedNameplate intendedNameplate;
    private final WireElm anodePadAttachment;
    private final WireElm cathodePadAttachment;
    private PhysicalLedPart installedPart;

    LedComponentSlot(String componentId, LedNameplate intendedNameplate, PhysicalLedPart installedPart,
            WireElm anodePadAttachment, WireElm cathodePadAttachment) {
        if (componentId == null || intendedNameplate == null || installedPart == null ||
                anodePadAttachment == null || cathodePadAttachment == null)
            throw new IllegalArgumentException("Invalid LED slot");
        this.componentId = componentId;
        this.intendedNameplate = intendedNameplate;
        this.anodePadAttachment = anodePadAttachment;
        this.cathodePadAttachment = cathodePadAttachment;
        install(installedPart);
    }

    String getComponentId() { return componentId; }
    LedNameplate getIntendedNameplate() { return intendedNameplate; }
    PhysicalLedPart getInstalledPart() { return installedPart; }
    boolean isEmpty() { return installedPart == null; }
    void clear() { installedPart = null; }
    void install(PhysicalLedPart part) {
        if (part == null) throw new IllegalArgumentException("Missing LED part");
        moveAttachmentEnd(anodePadAttachment, part.getTerminalForBoardPad("LED1.A"), false);
        moveAttachmentEnd(cathodePadAttachment, part.getTerminalForBoardPad("LED1.K"), true);
        installedPart = part;
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
