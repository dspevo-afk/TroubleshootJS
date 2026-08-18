package com.lushprojects.circuitjs1.client;

/** Family-owned three-terminal board slot with explicit B/C/E attachment order. */
final class NpnComponentSlot {
    private final String componentId;
    private final NpnSpecification intendedSpecification;
    private final WireElm[] attachments;
    private final PhysicalBoardSlot physicalSlot;

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
    }

    String getComponentId() { return componentId; }
    NpnSpecification getIntendedSpecification() { return intendedSpecification; }
    PhysicalBoardSlot getPhysicalSlot() { return physicalSlot; }
    PhysicalNpnPart getInstalledPart() { return (PhysicalNpnPart) physicalSlot.getInstalledPart(); }
    boolean isEmpty() { return !physicalSlot.isOccupied(); }
    void clear() { physicalSlot.remove(); }

    void install(PhysicalNpnPart part) {
        if (part == null)
            throw new IllegalArgumentException("Missing NPN part");
        attach(part);
        physicalSlot.install(part);
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
