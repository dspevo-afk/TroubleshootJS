package com.lushprojects.circuitjs1.client;

import java.util.Vector;
import java.util.Random;

/** Provider-produced PCB geometry for one logical board component. */
final class PcbFootprint {
    private final PcbComponentPlacement placement;
    private final Vector<PcbPadPlacement> pads;

    PcbFootprint(PcbComponentPlacement placement, Vector<PcbPadPlacement> pads) {
        if (placement == null || pads == null || pads.size() == 0)
            throw new IllegalArgumentException("Invalid PCB footprint");
        PhysicalPackage physicalPackage = placement.getPhysicalPackage();
        PhysicalPackageGeometry physicalGeometry = placement.getPhysicalGeometry();
        if (physicalPackage != null && (physicalGeometry == null ||
                !physicalPackage.acceptsGeometry(physicalGeometry)))
            throw new IllegalArgumentException("Footprint geometry is incompatible with package: " +
                physicalPackage.getId());
        if (physicalPackage != null && pads.size() != physicalGeometry.getTerminals().size())
            throw new IllegalArgumentException("Footprint terminal count does not match package: " +
                physicalPackage.getId());
        for (int index = 0; index < pads.size(); index++) {
            PcbPadPlacement pad = pads.get(index);
            if (pad == null || pad.getPadId() == null || pad.getPadId().length() == 0)
                throw new IllegalArgumentException("Invalid PCB footprint pad");
            if (getPadIfPresent(pads, index, pad.getPadId()) != null)
                throw new IllegalArgumentException("Duplicate PCB footprint pad: " +
                    pad.getPadId());
        }
        if (physicalPackage != null) {
            requireTerminalOrder(pads, physicalGeometry);
            requirePlacedTerminalGeometry(placement, pads, physicalGeometry);
        }
        this.placement = placement;
        this.pads = new Vector<PcbPadPlacement>(pads);
    }

    static PcbFootprint fromPhysicalPackage(BoardComponent component, int x, int y) {
        if (component == null || component.getPhysicalPackage() == null)
            throw new IllegalArgumentException("Missing package for PCB footprint");
        return fromPhysicalPackage(component, x, y,
            component.getPhysicalPackage().getGeometry());
    }

    static PcbFootprint fromPhysicalPackage(BoardComponent component, int x, int y,
            Random random, Rectangle outline) {
        if (component == null || component.getPhysicalPackage() == null)
            throw new IllegalArgumentException("Missing package for PCB footprint");
        return fromPhysicalPackage(component, x, y,
            component.getPhysicalPackage().geometryForPlacement(random, x, outline));
    }

    static PcbFootprint fromPhysicalPackage(BoardComponent component, int x, int y,
            PhysicalPackageGeometry geometry) {
        if (component == null || component.getPhysicalPackage() == null || geometry == null)
            throw new IllegalArgumentException("Missing package for PCB footprint");
        PhysicalPackage physicalPackage = component.getPhysicalPackage();
        if (!physicalPackage.acceptsGeometry(geometry))
            throw new IllegalArgumentException("Foreign PCB footprint geometry for package: " +
                physicalPackage.getId());
        Vector<String> ids = component.getPadIds();
        if (ids.size() != geometry.getTerminals().size())
            throw new IllegalStateException("Package footprint terminal count mismatch: " +
                component.getId());
        requireTerminalOrder(component, ids, geometry);
        PcbComponentPlacement placement = PcbComponentPlacement.fromPhysicalGeometry(
            component.getId(), x, y, physicalPackage, geometry);
        PhysicalPackageGeometry.Placement placed = geometry.placedAt(x, y);
        Vector<PcbPadPlacement> pads = new Vector<PcbPadPlacement>();
        for (int index = 0; index < ids.size(); index++) {
            PhysicalPackageGeometry.Terminal terminal = geometry.getTerminal(index);
            Point pad = placed.getPadPoint(index);
            pads.add(new PcbPadPlacement(ids.get(index), pad.x, pad.y, terminal.getEscapeDx(),
                terminal.getEscapeDy(), terminal.getEscapeLength(), placed.getPadBounds(index),
                placed.getProbeBounds(index)));
        }
        return new PcbFootprint(placement, pads);
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
            .append(placement.getHeight()).append('|')
            .append(placement.geometryFingerprint()).append('|');
        for (PcbPadPlacement pad : pads)
            result.append("pad=").append(pad.geometryFingerprint()).append(';');
        return result.toString();
    }

    PcbFootprint translated(int x, int y) {
        int dx = x - placement.getX();
        int dy = y - placement.getY();
        PcbComponentPlacement translatedPlacement = placement.translatedTo(x, y);
        Vector<PcbPadPlacement> translatedPads = new Vector<PcbPadPlacement>();
        for (PcbPadPlacement pad : pads)
            translatedPads.add(new PcbPadPlacement(pad.getPadId(), pad.getX() + dx,
                pad.getY() + dy, pad.getEscapeDx(), pad.getEscapeDy(), pad.getEscapeLength(),
                translate(pad.getPadBounds(), dx, dy), translate(pad.getProbeBounds(), dx, dy)));
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

    private static Rectangle translate(Rectangle rectangle, int dx, int dy) {
        return new Rectangle(rectangle.x + dx, rectangle.y + dy, rectangle.width,
            rectangle.height);
    }

    private static void requireTerminalOrder(BoardComponent component, Vector<String> padIds,
            PhysicalPackageGeometry geometry) {
        for (int index = 0; index < padIds.size(); index++) {
            String padId = padIds.get(index);
            int separator = padId == null ? -1 : padId.lastIndexOf('.');
            String terminalId = separator < 0 ? null : padId.substring(separator + 1);
            PhysicalPackageGeometry.Terminal terminal = geometry.getTerminal(index);
            if (terminal == null || terminalId == null ||
                    !terminal.getTerminalId().equals(terminalId))
                throw new IllegalStateException("Package footprint terminal order mismatch: " +
                    component.getId() + " pad=" + padId);
        }
    }

    private static void requireTerminalOrder(Vector<PcbPadPlacement> pads,
            PhysicalPackageGeometry geometry) {
        for (int index = 0; index < pads.size(); index++) {
            String padId = pads.get(index).getPadId();
            int separator = padId.lastIndexOf('.');
            String terminalId = separator < 0 ? null : padId.substring(separator + 1);
            PhysicalPackageGeometry.Terminal terminal = geometry.getTerminal(index);
            if (terminal == null || terminalId == null ||
                    !terminal.getTerminalId().equals(terminalId))
                throw new IllegalArgumentException("Footprint terminal order mismatch: " + padId);
        }
    }

    private static void requirePlacedTerminalGeometry(PcbComponentPlacement placement,
            Vector<PcbPadPlacement> pads, PhysicalPackageGeometry geometry) {
        PhysicalPackageGeometry.Placement placed = geometry.placedAt(placement.getX(),
            placement.getY());
        for (int index = 0; index < pads.size(); index++) {
            PcbPadPlacement pad = pads.get(index);
            PhysicalPackageGeometry.Terminal terminal = geometry.getTerminal(index);
            Point expectedPad = placed.getPadPoint(index);
            if (terminal == null || expectedPad == null || pad.getX() != expectedPad.x ||
                    pad.getY() != expectedPad.y || pad.getEscapeDx() != terminal.getEscapeDx() ||
                    pad.getEscapeDy() != terminal.getEscapeDy() ||
                    pad.getEscapeLength() != terminal.getEscapeLength() ||
                    !pad.getPadBounds().equals(placed.getPadBounds(index)) ||
                    !pad.getProbeBounds().equals(placed.getProbeBounds(index)))
                throw new IllegalArgumentException("Footprint pad diverges from package geometry: " +
                    pad.getPadId());
        }
    }
}
