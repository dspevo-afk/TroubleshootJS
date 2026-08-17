package com.lushprojects.circuitjs1.client;

import java.util.Vector;

/** Provider-produced PCB geometry for one logical board component. */
final class PcbFootprint {
    private final PcbComponentPlacement placement;
    private final Vector<PcbPadPlacement> pads;

    PcbFootprint(PcbComponentPlacement placement, Vector<PcbPadPlacement> pads) {
        if (placement == null || pads == null || pads.size() == 0)
            throw new IllegalArgumentException("Invalid PCB footprint");
        for (int index = 0; index < pads.size(); index++) {
            PcbPadPlacement pad = pads.get(index);
            if (pad == null || pad.getPadId() == null || pad.getPadId().length() == 0)
                throw new IllegalArgumentException("Invalid PCB footprint pad");
            if (getPadIfPresent(pads, index, pad.getPadId()) != null)
                throw new IllegalArgumentException("Duplicate PCB footprint pad: " +
                    pad.getPadId());
        }
        this.placement = placement;
        this.pads = new Vector<PcbPadPlacement>(pads);
    }

    PcbComponentPlacement getPlacement() { return placement; }
    Vector<PcbPadPlacement> getPads() { return new Vector<PcbPadPlacement>(pads); }

    PcbPadPlacement getPad(String padId) {
        for (PcbPadPlacement pad : pads)
            if (pad.getPadId().equals(padId))
                return pad;
        throw new IllegalStateException("PCB footprint is missing pad: " + padId);
    }

    String geometryFingerprint() {
        StringBuilder result = new StringBuilder();
        result.append(placement.getComponentId()).append('@').append(placement.getX()).append(',')
            .append(placement.getY()).append(',').append(placement.getWidth()).append(',')
            .append(placement.getHeight()).append('|');
        for (PcbPadPlacement pad : pads)
            result.append(pad.getPadId()).append('@').append(pad.getX()).append(',')
                .append(pad.getY()).append(':').append(pad.getEscapeDx()).append(',')
                .append(pad.getEscapeDy()).append(',').append(pad.getEscapeLength()).append(';');
        return result.toString();
    }

    PcbFootprint translated(int x, int y) {
        int dx = x - placement.getX();
        int dy = y - placement.getY();
        PcbComponentPlacement translatedPlacement = new PcbComponentPlacement(
            placement.getComponentId(), x, y, placement.getWidth(), placement.getHeight(),
            new Rectangle(placement.getKeepOut().x + dx, placement.getKeepOut().y + dy,
                placement.getKeepOut().width, placement.getKeepOut().height),
            new Rectangle(placement.getRoutingCourtyard().x + dx,
                placement.getRoutingCourtyard().y + dy,
                placement.getRoutingCourtyard().width, placement.getRoutingCourtyard().height));
        Vector<PcbPadPlacement> translatedPads = new Vector<PcbPadPlacement>();
        for (PcbPadPlacement pad : pads)
            translatedPads.add(new PcbPadPlacement(pad.getPadId(), pad.getX() + dx,
                pad.getY() + dy, pad.getEscapeDx(), pad.getEscapeDy(), pad.getEscapeLength()));
        return new PcbFootprint(translatedPlacement, translatedPads);
    }

    private static PcbPadPlacement getPadIfPresent(Vector<PcbPadPlacement> values, int end,
            String padId) {
        for (int index = 0; index < end; index++) {
            PcbPadPlacement value = values.get(index);
            if (padId.equals(value.getPadId()))
                return value;
        }
        return null;
    }
}
