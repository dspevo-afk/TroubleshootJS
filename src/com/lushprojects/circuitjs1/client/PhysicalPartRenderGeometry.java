package com.lushprojects.circuitjs1.client;

import java.util.Vector;

/** Complete provider-owned geometry used for drawing, selection, and probing. */
final class PhysicalPartRenderGeometry {
    private final Vector<PhysicalPartRenderTerminal> terminals;
    private final Vector<PhysicalPartRenderHitRegion> hitRegions;
    private final Rectangle selectionBounds;

    PhysicalPartRenderGeometry(Vector<PhysicalPartRenderTerminal> terminals,
            Vector<PhysicalPartRenderHitRegion> hitRegions, Rectangle selectionBounds) {
        if (terminals == null || hitRegions == null || selectionBounds == null)
            throw new IllegalArgumentException("Incomplete physical render geometry");
        this.terminals = new Vector<PhysicalPartRenderTerminal>(terminals);
        this.hitRegions = new Vector<PhysicalPartRenderHitRegion>(hitRegions);
        this.selectionBounds = new Rectangle(selectionBounds);
        for (int index = 0; index < this.terminals.size(); index++) {
            PhysicalPartRenderTerminal terminal = this.terminals.get(index);
            if (terminal == null || terminal.getTerminalIndex() != index)
                throw new IllegalArgumentException("Physical render terminals are not ordered");
        }
    }

    Vector<PhysicalPartRenderTerminal> getTerminals() {
        return new Vector<PhysicalPartRenderTerminal>(terminals);
    }

    Vector<PhysicalPartRenderHitRegion> getHitRegions() {
        return new Vector<PhysicalPartRenderHitRegion>(hitRegions);
    }

    Rectangle getSelectionBounds() { return new Rectangle(selectionBounds); }

    Point getTerminalPoint(int terminal) {
        if (terminal < 0 || terminal >= terminals.size())
            return null;
        return terminals.get(terminal).getPoint();
    }

    boolean contains(int x, int y) {
        for (PhysicalPartRenderHitRegion region : hitRegions)
            if (region.contains(x, y))
                return true;
        return false;
    }
}
