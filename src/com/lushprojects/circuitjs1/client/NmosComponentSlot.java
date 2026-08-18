package com.lushprojects.circuitjs1.client;

/** Family-owned three-terminal slot with explicit physical G/D/S attachment order. */
final class NmosComponentSlot {
    private final String componentId;
    private final NmosSpecification intendedSpecification;
    private final WireElm[] attachments;
    private final PhysicalBoardSlot physicalSlot;

    NmosComponentSlot(String componentId, NmosSpecification intendedSpecification,
            PhysicalNmosPart installedPart, WireElm gateAttachment,
            WireElm drainAttachment, WireElm sourceAttachment, PhysicalBoardSlot physicalSlot) {
        if (componentId == null || componentId.length() == 0 || intendedSpecification == null ||
                installedPart == null || gateAttachment == null || drainAttachment == null ||
                sourceAttachment == null || physicalSlot == null)
            throw new IllegalArgumentException("Invalid NMOS component slot");
        this.componentId = componentId;
        this.intendedSpecification = intendedSpecification;
        this.attachments = new WireElm[] { gateAttachment, drainAttachment, sourceAttachment };
        this.physicalSlot = physicalSlot;
        attach(installedPart);
        physicalSlot.install(installedPart);
    }

    String getComponentId() { return componentId; }
    NmosSpecification getIntendedSpecification() { return intendedSpecification; }
    PhysicalBoardSlot getPhysicalSlot() { return physicalSlot; }
    PhysicalNmosPart getInstalledPart() { return (PhysicalNmosPart) physicalSlot.getInstalledPart(); }
    boolean isEmpty() { return !physicalSlot.isOccupied(); }
    void clear() { physicalSlot.remove(); }

    void install(PhysicalNmosPart part) {
        if (part == null)
            throw new IllegalArgumentException("Missing NMOS part");
        attach(part);
        physicalSlot.install(part);
    }

    private void attach(PhysicalNmosPart part) {
        for (int index = 0; index < attachments.length; index++)
            moveAttachmentEnd(attachments[index], part.getPublicTerminal(index));
    }

    private void moveAttachmentEnd(WireElm attachment, CircuitMeasurementEndpoint endpoint) {
        if (!(endpoint instanceof CircuitPostMeasurementEndpoint))
            throw new IllegalStateException("Unsupported NMOS terminal endpoint");
        CircuitPostMeasurementEndpoint post = (CircuitPostMeasurementEndpoint) endpoint;
        Point point = post.getElement().getPost(post.getPostIndex());
        attachment.x2 = point.x;
        attachment.y2 = point.y;
        attachment.setPoints();
    }
}
