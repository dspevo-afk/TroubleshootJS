package com.lushprojects.circuitjs1.client;

import java.util.Vector;

/** Complete provider-owned geometry used for drawing, selection, and probing. */
final class PhysicalPartRenderGeometry {
    private final Vector<PhysicalPartRenderTerminal> terminals;
    private final Vector<PhysicalPartRenderHitRegion> hitRegions;
    private final Rectangle selectionBounds;
    private final Rectangle bodyBounds;
    private final Vector<Rectangle> leadBounds;
    private final Rectangle dragBounds;

    PhysicalPartRenderGeometry(Vector<PhysicalPartRenderTerminal> terminals,
            Vector<PhysicalPartRenderHitRegion> hitRegions, Rectangle selectionBounds,
            Rectangle bodyBounds, Vector<Rectangle> leadBounds, Rectangle dragBounds) {
        if (terminals == null || hitRegions == null || selectionBounds == null ||
                hitRegions.size() == 0 || selectionBounds.width <= 0 ||
                selectionBounds.height <= 0)
            throw new IllegalArgumentException("Incomplete physical render geometry");
        this.terminals = new Vector<PhysicalPartRenderTerminal>(terminals);
        this.hitRegions = new Vector<PhysicalPartRenderHitRegion>(hitRegions);
        this.selectionBounds = new Rectangle(selectionBounds);
        if (bodyBounds == null || bodyBounds.width <= 0 || bodyBounds.height <= 0 ||
                dragBounds == null || dragBounds.width <= 0 || dragBounds.height <= 0 ||
                leadBounds == null)
            throw new IllegalArgumentException("Incomplete physical render envelope");
        this.bodyBounds = new Rectangle(bodyBounds);
        this.leadBounds = new Vector<Rectangle>();
        for (Rectangle lead : leadBounds) {
            if (lead == null || lead.width <= 0 || lead.height <= 0)
                throw new IllegalArgumentException("Invalid physical render lead bounds");
            this.leadBounds.add(new Rectangle(lead));
        }
        this.dragBounds = new Rectangle(dragBounds);
        if (this.leadBounds.size() != this.terminals.size())
            throw new IllegalArgumentException("Physical render lead order is incomplete");
        for (PhysicalPartRenderHitRegion region : this.hitRegions)
            if (region == null)
                throw new IllegalArgumentException("Invalid physical render hit region");
        for (int index = 0; index < this.terminals.size(); index++) {
            PhysicalPartRenderTerminal terminal = this.terminals.get(index);
            if (terminal == null || terminal.getTerminalIndex() != index)
                throw new IllegalArgumentException("Physical render terminals are not ordered");
            if (!contains(this.selectionBounds, terminal.getProbeBounds()) ||
                    !contains(this.selectionBounds, terminal.getPadBounds()) ||
                    !contains(this.selectionBounds, terminal.getLeadBounds()) ||
                    !contains(this.selectionBounds, terminal.getComponentLeadProbeBounds()) ||
                    (terminal.getBoardPadProbeBounds() != null &&
                        !contains(this.selectionBounds, terminal.getBoardPadProbeBounds())) ||
                    !contains(this.dragBounds, terminal.getProbeBounds()) ||
                    !contains(this.dragBounds, terminal.getPadBounds()) ||
                    !contains(this.dragBounds, terminal.getLeadBounds()) ||
                    !contains(this.dragBounds, terminal.getComponentLeadProbeBounds()) ||
                    (terminal.getBoardPadProbeBounds() != null &&
                        !contains(this.dragBounds, terminal.getBoardPadProbeBounds())))
                throw new IllegalArgumentException("Physical render terminal escapes its envelope");
        }
        if (!contains(this.selectionBounds, this.bodyBounds) ||
                !contains(this.dragBounds, this.selectionBounds))
            throw new IllegalArgumentException("Physical render envelope containment failed");
    }

    Vector<PhysicalPartRenderTerminal> getTerminals() {
        return new Vector<PhysicalPartRenderTerminal>(terminals);
    }

    Vector<PhysicalPartRenderHitRegion> getHitRegions() {
        return new Vector<PhysicalPartRenderHitRegion>(hitRegions);
    }

    Rectangle getSelectionBounds() { return new Rectangle(selectionBounds); }
    Rectangle getBodyBounds() { return new Rectangle(bodyBounds); }
    Vector<Rectangle> getLeadBounds() {
        Vector<Rectangle> result = new Vector<Rectangle>();
        for (Rectangle lead : leadBounds)
            result.add(new Rectangle(lead));
        return result;
    }
    Rectangle getDragBounds() { return new Rectangle(dragBounds); }

    Point getTerminalPoint(int terminal) {
        if (terminal < 0 || terminal >= terminals.size())
            return null;
        return terminals.get(terminal).getPoint();
    }

    PhysicalPartRenderTerminal getTerminal(int terminal) {
        if (terminal < 0 || terminal >= terminals.size())
            return null;
        return terminals.get(terminal);
    }

    boolean contains(int x, int y) {
        for (PhysicalPartRenderHitRegion region : hitRegions)
            if (region.contains(x, y))
                return true;
        return false;
    }

    private static boolean contains(Rectangle outer, Rectangle inner) {
        // Screen-space rounding can shrink a translated outer edge by one
        // pixel.  Keep the invariant strict at package coordinates while
        // tolerating that single-pixel rasterization artifact here.
        return inner.x >= outer.x - 1 && inner.y >= outer.y - 1 &&
            (long) inner.x + inner.width <= (long) outer.x + outer.width + 1 &&
            (long) inner.y + inner.height <= (long) outer.y + outer.height + 1;
    }
}
