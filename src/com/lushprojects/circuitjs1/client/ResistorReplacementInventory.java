package com.lushprojects.circuitjs1.client;

import java.util.HashMap;
import java.util.Vector;

class ResistorReplacementInventory {
    private final HashMap<String, PhysicalResistorPart> parts =
        new HashMap<String, PhysicalResistorPart>();
    private final Vector<String> order = new Vector<String>();

    void add(PhysicalResistorPart part) {
        if (parts.put(part.getId(), part) != null)
            throw new IllegalArgumentException("Duplicate resistor part: " + part.getId());
        order.add(part.getId());
    }

    PhysicalResistorPart get(String partId) {
        PhysicalResistorPart part = parts.get(partId);
        if (part == null)
            throw new IllegalArgumentException("Unknown resistor part: " + partId);
        return part;
    }

    Vector<PhysicalResistorPart> getAll() {
        Vector<PhysicalResistorPart> result = new Vector<PhysicalResistorPart>();
        for (String partId : order)
            result.add(parts.get(partId));
        return result;
    }

    Vector<PhysicalResistorPart> getLooseParts() {
        Vector<PhysicalResistorPart> result = new Vector<PhysicalResistorPart>();
        for (PhysicalResistorPart part : getAll())
            if (part.getLocation() == ResistorPartLocation.LOOSE)
                result.add(part);
        return result;
    }
}