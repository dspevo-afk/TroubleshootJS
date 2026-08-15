package com.lushprojects.circuitjs1.client;

import java.util.HashMap;
import java.util.Vector;

class LedReplacementInventory {
    private final Vector<PhysicalLedPart> ordered = new Vector<PhysicalLedPart>();
    private final HashMap<String, PhysicalLedPart> parts = new HashMap<String, PhysicalLedPart>();

    void add(PhysicalLedPart part) {
        if (part == null || parts.containsKey(part.getId()))
            throw new IllegalArgumentException("Invalid or duplicate LED part");
        ordered.add(part);
        parts.put(part.getId(), part);
    }

    PhysicalLedPart get(String id) {
        PhysicalLedPart part = parts.get(id);
        if (part == null)
            throw new IllegalArgumentException("Unknown LED part: " + id);
        return part;
    }

    boolean contains(String id) { return parts.containsKey(id); }
    Vector<PhysicalLedPart> getAll() { return new Vector<PhysicalLedPart>(ordered); }
    Vector<PhysicalLedPart> getLooseParts() {
        Vector<PhysicalLedPart> result = new Vector<PhysicalLedPart>();
        for (PhysicalLedPart part : ordered)
            if (part.getLocation() == LedPartLocation.LOOSE)
                result.add(part);
        return result;
    }
}
