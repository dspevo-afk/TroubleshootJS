package com.lushprojects.circuitjs1.client;

import java.util.Vector;

/** Public physical navigation group; contains no diagnostic or fault state. */
final class PcbLayoutRegion {
    final String id, label;
    private final Vector<String> components;
    PcbLayoutRegion(String id, String label, Vector<String> components) {
        if (id == null || id.length() == 0 || label == null || label.length() == 0 ||
                components == null || components.isEmpty()) throw new IllegalArgumentException("Empty physical region");
        this.id = id; this.label = label; this.components = new Vector<String>(components);
        java.util.Collections.sort(this.components);
    }
    Vector<String> getComponentIds() { return new Vector<String>(components); }
    Rectangle bounds(PcbBoardLayout layout) {
        Rectangle bounds = null;
        for (String id : components) {
            PcbComponentPlacement part = layout.getComponent(id);
            if (part == null) throw new IllegalStateException("Physical region has an unknown component");
            Rectangle next = part.getRoutingCourtyard();
            if (bounds == null) bounds = next;
            else {
                int right = Math.max(bounds.x + bounds.width, next.x + next.width);
                int bottom = Math.max(bounds.y + bounds.height, next.y + next.height);
                bounds.x = Math.min(bounds.x, next.x); bounds.y = Math.min(bounds.y, next.y);
                bounds.width = right - bounds.x; bounds.height = bottom - bounds.y;
            }
        }
        return bounds;
    }
}
