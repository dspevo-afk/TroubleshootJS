package com.lushprojects.circuitjs1.client;

import java.util.Vector;

/** A physical part instance whose board association is owned by a slot. */
interface PhysicalPart<S extends PhysicalSpecification> {
    String getId();
    S getSpecification();
    PhysicalNameplate getPlayerVisibleNameplate();
    PhysicalPartRenderMetadata getRenderMetadata();
    PhysicalPartOrientation getOrientation();
    PhysicalPackage getPackage();
    int getTerminalCount();
    PhysicalPartTerminal getTerminal(int terminal);
    Vector<PhysicalPartTerminal> getTerminals();
    PhysicalPartElectricalBacking getElectricalBacking();
    PhysicalPartMountState getMountState();
    PhysicalBoardSlot getBoardSlot();
    PhysicalPartProvenance getProvenance();
    PhysicalFailureState getFailureState();
    Vector<PhysicalPartCapability> getCapabilities();
    Vector<PhysicalPartCapability> getIntrinsicCapabilities();
    boolean isInstalled();
    boolean isOriginal();
    boolean isFaulted();
}
