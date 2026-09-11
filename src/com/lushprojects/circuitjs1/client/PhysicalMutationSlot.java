package com.lushprojects.circuitjs1.client;

/**
 * Narrow slot boundary used by the bounded physical mutation transaction.
 *
 * <p>The transaction knows how to preserve graph, binding and inventory
 * ownership.  A slot remains responsible for the physical details of moving
 * its own part and its own lead attachments.  In particular, diode polarity
 * and resistor terminal selection do not belong in the transaction.</p>
 */
interface PhysicalMutationSlot {
    String getComponentId();
    PhysicalBoardSlot getPhysicalSlot();
    PhysicalPart<?> getInstalledPart();
    boolean isEmpty();
    CircuitMeasurementEndpoint getExpectedEndpoint(PhysicalPart<?> part, BoardPad pad);
    boolean acceptsPart(PhysicalPart<?> part);

    AttachmentState captureAttachmentState();
    void restoreAttachmentState(AttachmentState state);

    void installForMutation(PhysicalPart<?> part, PhysicalMutationScope scope);
    PhysicalPart<?> clearForMutation(PhysicalMutationScope scope);

    /** Marker owned by the concrete slot; the scope never inspects its type. */
    interface AttachmentState { }
}
