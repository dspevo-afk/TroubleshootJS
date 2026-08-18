package com.lushprojects.circuitjs1.client;

/** Family-owned attachment seam for one polarized capacitor board location. */
final class CapacitorComponentSlot {
    private final String componentId;
    private final CapacitorSpecification intendedSpecification;
    private final WireElm positiveAttachment;
    private final WireElm negativeAttachment;
    private final PhysicalBoardSlot physicalSlot;

    CapacitorComponentSlot(String componentId, CapacitorSpecification intendedSpecification,
            PhysicalCapacitorPart installedPart, WireElm positiveAttachment,
            WireElm negativeAttachment, PhysicalBoardSlot physicalSlot) {
        if (componentId == null || componentId.length() == 0 || intendedSpecification == null ||
                installedPart == null || positiveAttachment == null || negativeAttachment == null ||
                physicalSlot == null)
            throw new IllegalArgumentException("Invalid capacitor component slot");
        if (!intendedSpecification.isPolarized())
            throw new IllegalArgumentException("Replaceable capacitor slot requires polarity");
        this.componentId = componentId;
        this.intendedSpecification = intendedSpecification;
        this.positiveAttachment = positiveAttachment;
        this.negativeAttachment = negativeAttachment;
        this.physicalSlot = physicalSlot;
        install(installedPart);
    }

    String getComponentId() { return componentId; }
    CapacitorSpecification getIntendedSpecification() { return intendedSpecification; }
    PhysicalBoardSlot getPhysicalSlot() { return physicalSlot; }
    PhysicalCapacitorPart getInstalledPart() {
        return (PhysicalCapacitorPart) physicalSlot.getInstalledPart();
    }
    boolean isEmpty() { return !physicalSlot.isOccupied(); }
    void clear() { physicalSlot.remove(); }

    void install(PhysicalCapacitorPart part) {
        if (part == null || !part.getSpecification().isPolarized())
            throw new IllegalArgumentException("Invalid polarized capacitor installation");
        moveAttachmentEnd(positiveAttachment, part.getTerminalForBoardPad(componentId + ".+"),
            false);
        moveAttachmentEnd(negativeAttachment, part.getTerminalForBoardPad(componentId + ".-"),
            true);
        physicalSlot.install(part);
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
