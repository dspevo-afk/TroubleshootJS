package com.lushprojects.circuitjs1.client;

/** Mutable mount state changed only through board-slot operations. */
final class PhysicalPartMountState {
    private PhysicalBoardSlot slot;

    boolean isInstalled() { return slot != null; }
    PhysicalBoardSlot getSlot() { return slot; }

    void mount(PhysicalBoardSlot target) {
        if (target == null || slot != null)
            throw new IllegalStateException("Physical part is already mounted");
        slot = target;
    }

    void unmount(PhysicalBoardSlot source) {
        if (source == null || slot != source)
            throw new IllegalStateException("Physical part mount state is inconsistent");
        slot = null;
    }
}
