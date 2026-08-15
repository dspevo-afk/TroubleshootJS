package com.lushprojects.circuitjs1.client;

class ReplaceableComponentSlot {
    private final String componentId;
    private final ResistorNameplate intendedNameplate;
    private final WireElm firstAttachment;
    private final WireElm secondAttachment;
    private PhysicalResistorPart installedPart;

    ReplaceableComponentSlot(String componentId, ResistorNameplate intendedNameplate,
            PhysicalResistorPart installedPart, WireElm firstAttachment, WireElm secondAttachment) {
        if (componentId == null || componentId.length() == 0 || intendedNameplate == null ||
                installedPart == null || firstAttachment == null || secondAttachment == null)
            throw new IllegalArgumentException("Invalid replaceable component slot");
        this.componentId = componentId;
        this.intendedNameplate = intendedNameplate;
        this.firstAttachment = firstAttachment;
        this.secondAttachment = secondAttachment;
        this.installedPart = installedPart;
        attach(installedPart);
    }

    String getComponentId() { return componentId; }
    ResistorNameplate getIntendedNameplate() { return intendedNameplate; }
    PhysicalResistorPart getInstalledPart() { return installedPart; }
    boolean isEmpty() { return installedPart == null; }
    void clear() { installedPart = null; }
    void install(PhysicalResistorPart part) {
        if (part == null)
            throw new IllegalArgumentException("Missing resistor part");
        attach(part);
        installedPart = part;
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