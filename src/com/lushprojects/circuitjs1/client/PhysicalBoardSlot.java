package com.lushprojects.circuitjs1.client;

import java.util.Vector;

/** Stable board location owning pads/nets and the installed-part association. */
final class PhysicalBoardSlot {
    private final String id;
    private final String componentId;
    private final PhysicalPackage physicalPackage;
    private final Vector<String> padIds;
    private final Vector<String> terminalIds;
    private final Vector<String> netIds;
    private PhysicalBoardRuntime runtime;
    private PhysicalPart<?> installedPart;

    PhysicalBoardSlot(TroubleshootBoard board, String componentId) {
        if (board == null || componentId == null || componentId.length() == 0)
            throw new IllegalArgumentException("Invalid physical board slot");
        BoardComponent component = board.getComponent(componentId);
        if (component == null || component.getPhysicalPackage() == null)
            throw new IllegalArgumentException("Unknown typed board component: " + componentId);
        id = board.getId() + ".SLOT." + componentId;
        this.componentId = componentId;
        physicalPackage = component.getPhysicalPackage();
        padIds = component.getPadIds();
        terminalIds = new Vector<String>();
        netIds = new Vector<String>();
        for (String padId : padIds) {
            BoardPad pad = board.getPad(padId);
            if (pad == null)
                throw new IllegalArgumentException("Slot references unknown pad: " + padId);
            netIds.add(pad.getNetId());
            terminalIds.add(pad.getTerminalId());
        }
        if (padIds.size() != physicalPackage.getTerminalCount())
            throw new IllegalArgumentException("Slot/package terminal count mismatch: " + componentId);
        for (int index = 0; index < terminalIds.size(); index++) {
            String terminalId = terminalIds.get(index);
            for (int previous = 0; previous < index; previous++)
                if (terminalId.equals(terminalIds.get(previous)))
                    throw new IllegalArgumentException("Duplicate slot terminal: " + componentId);
            boolean declared = false;
            for (String declaredTerminal : physicalPackage.getTerminalIds())
                if (terminalId.equals(declaredTerminal)) {
                    declared = true;
                    break;
                }
            if (!declared)
                throw new IllegalArgumentException("Undeclared package terminal on slot: " +
                    componentId + "." + terminalId);
        }
    }

    String getId() { return id; }
    String getComponentId() { return componentId; }
    PhysicalPackage getPhysicalPackage() { return physicalPackage; }
    Vector<String> getPadIds() { return new Vector<String>(padIds); }
    Vector<String> getNetIds() { return new Vector<String>(netIds); }
    PhysicalPart<?> getInstalledPart() { return installedPart; }
    boolean isOccupied() { return installedPart != null; }
    PhysicalPartMountState getMountState() {
        return installedPart == null ? null : installedPart.getMountState();
    }

    void setRuntime(PhysicalBoardRuntime runtime) {
        if (runtime == null || (this.runtime != null && this.runtime != runtime))
            throw new IllegalStateException("Physical slot runtime cannot be changed");
        this.runtime = runtime;
    }

    void install(PhysicalPart<?> part) {
        if (part == null || installedPart != null)
            throw new IllegalStateException("Physical slot is already occupied");
        if (part.getPackage() == null || !physicalPackage.isEquivalentTo(part.getPackage()) ||
                part.getTerminalCount() != padIds.size())
            throw new IllegalArgumentException("Physical part does not fit board slot: " + componentId);
        if (part.getMountState().isInstalled())
            throw new IllegalStateException("Physical part is already installed in a slot");
        for (String terminalId : terminalIds)
            findTerminal(part, terminalId);
        if (runtime != null)
            runtime.validatePartIdentity(part);
        part.getMountState().mount(this);
        installedPart = part;
        if (runtime != null)
            runtime.registerPart(part);
    }

    PhysicalPart<?> remove() {
        if (installedPart == null)
            return null;
        PhysicalPart<?> removed = installedPart;
        removed.getMountState().unmount(this);
        installedPart = null;
        return removed;
    }

    private PhysicalPartTerminal findTerminal(PhysicalPart<?> part, String terminalId) {
        for (PhysicalPartTerminal terminal : part.getTerminals())
            if (terminalId.equals(terminal.getTerminalName()))
                return terminal;
        throw new IllegalArgumentException("Part terminal does not match board pad: " + terminalId);
    }
}
