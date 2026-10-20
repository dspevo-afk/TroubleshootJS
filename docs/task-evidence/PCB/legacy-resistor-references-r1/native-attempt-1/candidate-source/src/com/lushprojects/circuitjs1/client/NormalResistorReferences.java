package com.lushprojects.circuitjs1.client;

import java.util.HashSet;
import java.util.Set;

/** Explicit recipe policy for public marks; graph IDs and existing legend capacity stay authoritative. */
final class NormalResistorReferences {
    private NormalResistorReferences() { }

    static BoardComponent create(TroubleshootBoard board, String id, String type,
            PhysicalPackage physicalPackage) {
        if (board == null || id == null || id.trim().length() == 0 ||
                type == null || type.trim().length() == 0 || physicalPackage == null)
            throw new IllegalArgumentException("Missing normal resistor declaration");
        Set<String> used = new HashSet<String>();
        for (String componentId : board.getComponentIds())
            used.add(board.getComponent(componentId).getDisplayName());
        int ordinal = 1;
        while (used.contains("R" + ordinal)) ordinal++;
        return new BoardComponent(id, type, physicalPackage, "R" + ordinal, id.length());
    }
}
