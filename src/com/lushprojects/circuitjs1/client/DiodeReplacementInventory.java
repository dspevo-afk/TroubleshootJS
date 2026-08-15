package com.lushprojects.circuitjs1.client;

import java.util.HashMap;
import java.util.Vector;

class DiodeReplacementInventory {
    private final Vector<PhysicalDiodePart> ordered = new Vector<PhysicalDiodePart>();
    private final HashMap<String, PhysicalDiodePart> parts = new HashMap<String, PhysicalDiodePart>();

    void add(PhysicalDiodePart part) {
        if (part == null || parts.containsKey(part.getId()))
            throw new IllegalArgumentException("Invalid or duplicate diode part");
        ordered.add(part);
        parts.put(part.getId(), part);
    }

    PhysicalDiodePart get(String id) {
        PhysicalDiodePart part = parts.get(id);
        if (part == null)
            throw new IllegalArgumentException("Unknown diode part: " + id);
        return part;
    }

    Vector<PhysicalDiodePart> getAll() { return new Vector<PhysicalDiodePart>(ordered); }
    Vector<PhysicalDiodePart> getLooseParts() {
        Vector<PhysicalDiodePart> result = new Vector<PhysicalDiodePart>();
        for (PhysicalDiodePart part : ordered)
            if (part.getLocation() == DiodePartLocation.LOOSE)
                result.add(part);
        return result;
    }
}
