package com.lushprojects.circuitjs1.client;

import java.util.Random;
import java.util.Vector;

/** Developer proof for the package-owned physical geometry contract. */
final class Task43DeveloperVerifier {
    private Task43DeveloperVerifier() { }

    static void verify(CirSim sim) {
        if (sim == null || sim.getGeneratedBoardInstance() == null)
            throw new IllegalStateException("Task 43 requires a generated board");
        PcbFootprintRegistry footprints = StandardPcbFootprintProviders.createRegistry();
        PhysicalPartRenderRegistry renderers = StandardPhysicalPartRenderProviders.createRegistry();
        Vector<PhysicalPackage> registered = footprints.getRegisteredPackages();
        Vector<PhysicalPackage> rendered = renderers.getRegisteredPackages();
        require(registered.size() == rendered.size(),
            "Footprint/render package registries have different sizes");
        for (int index = 0; index < registered.size(); index++)
            require(registered.get(index).getId().equals(rendered.get(index).getId()),
                "Footprint/render package registration diverged: " + registered.get(index).getId());

        for (PhysicalPackage physicalPackage : registered)
            verifyPackage(physicalPackage, footprints, renderers);
        verifyBadGeometryCanary(registered.get(0).getGeometry());
        sim.setCircuitTitle("Task 43 physical geometry verification passed");
    }

    private static void verifyPackage(PhysicalPackage physicalPackage,
            PcbFootprintRegistry footprints, PhysicalPartRenderRegistry renderers) {
        PhysicalPackageGeometry geometry = physicalPackage.getGeometry();
        Vector<String> packageTerminals = physicalPackage.getTerminalIds();
        require(geometry.getWidth() > 0 && geometry.getHeight() > 0,
            "Package has non-positive dimensions: " + physicalPackage.getId());
        require(packageTerminals.equals(geometry.getTerminalIds()),
            "Package terminal IDs are not stable in geometry: " + physicalPackage.getId());
        require(renderers.hasProvider(physicalPackage),
            "Package has no render provider: " + physicalPackage.getId());

        Rectangle body = geometry.getBodyBounds();
        Rectangle keepOut = geometry.getBodyKeepOut();
        Rectangle courtyard = geometry.getRoutingCourtyard();
        Rectangle selection = geometry.getSelectionEnvelope();
        Rectangle drag = geometry.getDragEnvelope();
        require(contains(keepOut, body) && contains(courtyard, keepOut) &&
                contains(selection, body) && contains(drag, selection),
            "Package envelope containment failed: " + physicalPackage.getId());
        for (PhysicalPackageGeometry.Terminal terminal : geometry.getTerminals()) {
            Rectangle pad = terminal.getPadBounds();
            Rectangle probe = terminal.getProbeBounds();
            Rectangle lead = terminal.getLead().getBounds();
            Point padPoint = terminal.getPadCenter();
            Point probePoint = terminal.getProbeCenter();
            Point leadPadPoint = terminal.getLead().getPadPoint();
            Point leadBodyPoint = terminal.getLead().getBodyPoint();
            require(pad.width > 0 && pad.height > 0 && probe.width > 0 &&
                    probe.height > 0 && lead.width > 0 && lead.height > 0 &&
                    contains(pad, padPoint) && contains(probe, padPoint) &&
                    contains(probe, probePoint) && contains(lead, leadPadPoint) &&
                    contains(lead, leadBodyPoint) && leadPadPoint.equals(padPoint) &&
                    contains(body, leadBodyPoint) && contains(courtyard, pad) &&
                    contains(courtyard, lead) && contains(selection, probe) &&
                    contains(selection, lead),
                "Terminal geometry invariant failed: " + physicalPackage.getId() + "/" +
                    terminal.getTerminalId());
            require(Math.abs(probePoint.x - padPoint.x) +
                    Math.abs(probePoint.y - padPoint.y) <= 80,
                "Terminal probe is too far from pad: " + physicalPackage.getId() + "/" +
                    terminal.getTerminalId());
        }

        BoardComponent component = new BoardComponent("TASK43_" + physicalPackage.getId(),
            "TASK43", physicalPackage);
        for (String terminal : packageTerminals)
            component.addPadId(component.getId() + "." + terminal);
        Rectangle outline = new Rectangle(0, 0, 1600, 1200);
        PcbFootprint first = footprints.create(component, 240, 180, new Random(43), outline);
        PcbFootprint second = footprints.create(component, 240, 180, new Random(43), outline);
        require(first.geometryFingerprint().equals(second.geometryFingerprint()),
            "Same package and placement did not resolve identically: " + physicalPackage.getId());
        require(first.getPlacement().getPhysicalGeometry() != null &&
                physicalPackage.acceptsGeometry(
                    first.getPlacement().getPhysicalGeometry()),
            "Footprint lost the authoritative package geometry: " + physicalPackage.getId());
        verifyPlacedGeometry(first, physicalPackage, component);

        PcbFootprint translated = first.translated(640, 510);
        require(translated.getPlacement().getPhysicalGeometry() != null &&
                physicalPackage.acceptsGeometry(
                    translated.getPlacement().getPhysicalGeometry()),
            "Translated footprint lost package geometry: " + physicalPackage.getId());
        verifyPlacedGeometry(translated, physicalPackage, component);
        for (int index = 0; index < packageTerminals.size(); index++) {
            PcbPadPlacement original = first.getPad(component.getPadIds().get(index));
            PcbPadPlacement moved = translated.getPad(component.getPadIds().get(index));
            require(moved.getX() - original.getX() == 400 &&
                    moved.getY() - original.getY() == 330,
                "Translated pad did not preserve package-local coordinates: " +
                    physicalPackage.getId());
        }
    }

    private static void verifyPlacedGeometry(PcbFootprint footprint,
            PhysicalPackage physicalPackage, BoardComponent component) {
        PcbComponentPlacement placement = footprint.getPlacement();
        PhysicalPackageGeometry geometry = placement.getPhysicalGeometry();
        require(physicalPackage.acceptsGeometry(geometry),
            "Placed geometry is not a declared package variant: " + physicalPackage.getId());
        PhysicalPackageGeometry.Placement placed = geometry.placedAt(
            placement.getX(), placement.getY());
        require(placement.getWidth() == geometry.getWidth() &&
                placement.getHeight() == geometry.getHeight() &&
                placement.getKeepOut().equals(placed.getBodyKeepOut()) &&
                placement.getRoutingCourtyard().equals(placed.getRoutingCourtyard()),
            "Placed envelope diverged from package geometry: " + physicalPackage.getId());
        for (int index = 0; index < component.getPadIds().size(); index++) {
            PcbPadPlacement pad = footprint.getPad(component.getPadIds().get(index));
            PhysicalPackageGeometry.Terminal terminal = geometry.getTerminal(index);
            require(pad.getX() == placed.getPadPoint(index).x &&
                    pad.getY() == placed.getPadPoint(index).y &&
                    pad.getEscapeDx() == terminal.getEscapeDx() &&
                    pad.getEscapeDy() == terminal.getEscapeDy() &&
                    pad.getEscapeLength() == terminal.getEscapeLength() &&
                    pad.getPadBounds().equals(placed.getPadBounds(index)) &&
                    pad.getProbeBounds().equals(placed.getProbeBounds(index)),
                "Pad geometry diverged from package terminal: " + physicalPackage.getId());
        }
    }

    private static void verifyBadGeometryCanary(PhysicalPackageGeometry source) {
        boolean rejected = false;
        try {
            new PhysicalPackageGeometry(source.getWidth(), source.getHeight(),
                source.getTerminals(), source.getBodyBounds(), source.getBodyKeepOut(),
                new Rectangle(source.getRoutingCourtyard().x + 500,
                    source.getRoutingCourtyard().y, source.getRoutingCourtyard().width,
                    source.getRoutingCourtyard().height), source.getSelectionEnvelope(),
                source.getDragEnvelope());
        } catch (IllegalArgumentException expected) {
            rejected = true;
        }
        require(rejected, "BAD geometry canary was not rejected");
    }

    private static boolean contains(Rectangle outer, Rectangle inner) {
        return inner.x >= outer.x && inner.y >= outer.y &&
            inner.x + inner.width <= outer.x + outer.width &&
            inner.y + inner.height <= outer.y + outer.height;
    }

    private static boolean contains(Rectangle outer, Point point) {
        return point.x >= outer.x && point.y >= outer.y &&
            point.x <= outer.x + outer.width && point.y <= outer.y + outer.height;
    }

    private static void require(boolean condition, String message) {
        if (!condition)
            throw new IllegalStateException(message);
    }
}
