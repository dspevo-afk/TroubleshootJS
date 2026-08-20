package com.lushprojects.circuitjs1.client;

import java.util.Vector;

class PcbComponentPlacement {
    private final String componentId;
    private final int x;
    private final int y;
    private final int width;
    private final int height;
    private final Rectangle keepOut;
    private final Rectangle routingCourtyard;
    private final PhysicalPackage physicalPackage;
    private final PhysicalPackageGeometry physicalGeometry;

    PcbComponentPlacement(String componentId, int x, int y, int width, int height) {
        this(componentId, x, y, width, height, new Rectangle(x, y, width, height),
            new Rectangle(x, y, width, height));
    }

    PcbComponentPlacement(String componentId, int x, int y, int width, int height,
            Rectangle keepOut) {
        this(componentId, x, y, width, height, keepOut, keepOut);
    }

    PcbComponentPlacement(String componentId, int x, int y, int width, int height,
            Rectangle keepOut, Rectangle routingCourtyard) {
        this(componentId, x, y, width, height, keepOut, routingCourtyard, null);
    }

    PcbComponentPlacement(String componentId, int x, int y, int width, int height,
            Rectangle keepOut, Rectangle routingCourtyard,
            PhysicalPackageGeometry physicalGeometry) {
        this(componentId, x, y, width, height, keepOut, routingCourtyard, null,
            physicalGeometry);
    }

    PcbComponentPlacement(String componentId, int x, int y, int width, int height,
            Rectangle keepOut, Rectangle routingCourtyard, PhysicalPackage physicalPackage,
            PhysicalPackageGeometry physicalGeometry) {
        if (keepOut == null || keepOut.width <= 0 || keepOut.height <= 0 ||
                routingCourtyard == null || routingCourtyard.width <= 0 ||
                routingCourtyard.height <= 0 || width <= 0 || height <= 0)
            throw new IllegalArgumentException("Invalid PCB component keep-out: " + componentId);
        if (physicalPackage != null) {
            if (physicalGeometry == null || !physicalPackage.acceptsGeometry(physicalGeometry))
                throw new IllegalArgumentException("Foreign package geometry: " + componentId);
            PhysicalPackageGeometry.Placement placed = physicalGeometry.placedAt(x, y);
            if (width != physicalGeometry.getWidth() || height != physicalGeometry.getHeight() ||
                    !placed.getBodyKeepOut().equals(keepOut) ||
                    !placed.getRoutingCourtyard().equals(routingCourtyard))
                throw new IllegalArgumentException("Placement diverges from package geometry: " +
                    componentId);
        }
        this.componentId = componentId;
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        this.keepOut = new Rectangle(keepOut);
        this.routingCourtyard = new Rectangle(routingCourtyard);
        this.physicalPackage = physicalPackage;
        this.physicalGeometry = physicalGeometry;
    }

    static PcbComponentPlacement fromPhysicalGeometry(String componentId, int x, int y,
            PhysicalPackageGeometry geometry) {
        return fromPhysicalGeometry(componentId, x, y, null, geometry);
    }

    static PcbComponentPlacement fromPhysicalGeometry(String componentId, int x, int y,
            PhysicalPackage physicalPackage, PhysicalPackageGeometry geometry) {
        if (geometry == null)
            throw new IllegalArgumentException("Missing package geometry: " + componentId);
        PhysicalPackageGeometry.Placement placed = geometry.placedAt(x, y);
        return new PcbComponentPlacement(componentId, x, y, geometry.getWidth(),
            geometry.getHeight(), placed.getBodyKeepOut(), placed.getRoutingCourtyard(),
            physicalPackage, geometry);
    }

    String getComponentId() { return componentId; }
    int getX() { return x; }
    int getY() { return y; }
    int getWidth() { return width; }
    int getHeight() { return height; }
    Rectangle getBounds() { return new Rectangle(x, y, width, height); }
    Rectangle getBodyBounds() {
        return physicalGeometry == null ? getBounds() : placedGeometry().getBodyBounds();
    }
    Rectangle getKeepOut() { return new Rectangle(keepOut); }
    Rectangle getBodyKeepOut() { return getKeepOut(); }
    Rectangle getRoutingCourtyard() { return new Rectangle(routingCourtyard); }
    Rectangle getSelectionEnvelope() {
        return physicalGeometry == null ? getBounds() : placedGeometry().getSelectionEnvelope();
    }
    Rectangle getDragEnvelope() {
        return physicalGeometry == null ? getBounds() : placedGeometry().getDragEnvelope();
    }
    Rectangle getLeadBounds(int index) {
        return physicalGeometry == null ? null : placedGeometry().getLeadBounds(index);
    }
    Point getLeadBodyPoint(int index) {
        return physicalGeometry == null ? null : placedGeometry().getLeadBodyPoint(index);
    }
    Point getProbePoint(int index) {
        return physicalGeometry == null ? null : placedGeometry().getProbePoint(index);
    }
    Rectangle getPadBounds(int index) {
        return physicalGeometry == null ? null : placedGeometry().getPadBounds(index);
    }
    Rectangle getProbeBounds(int index) {
        return physicalGeometry == null ? null : placedGeometry().getProbeBounds(index);
    }
    Point getPadPoint(int index) {
        return physicalGeometry == null ? null : placedGeometry().getPadPoint(index);
    }
    PhysicalPackage getPhysicalPackage() { return physicalPackage; }
    PhysicalPackageGeometry getPhysicalGeometry() { return physicalGeometry; }

    String geometryFingerprint() {
        StringBuilder result = new StringBuilder();
        result.append("component=").append(componentId).append('@').append(x).append(',')
            .append(y).append(',').append(width).append(',').append(height).append('|');
        appendRectangle(result, "body", getBodyBounds());
        appendRectangle(result, "keepout", keepOut);
        appendRectangle(result, "courtyard", routingCourtyard);
        appendRectangle(result, "selection", getSelectionEnvelope());
        appendRectangle(result, "drag", getDragEnvelope());
        if (physicalPackage == null)
            result.append("package=null|");
        else {
            result.append("package=").append(physicalPackage.getId()).append('|')
                .append("connector=").append(physicalPackage.isConnector()).append('|')
                .append("variant=").append(physicalPackage.getGeometryVariantOwner()).append('|');
            Vector<String> terminalIds = physicalPackage.getTerminalIds();
            for (int index = 0; index < terminalIds.size(); index++) {
                for (int second = index + 1; second < terminalIds.size(); second++)
                    result.append("internal:").append(terminalIds.get(index)).append('=')
                        .append(terminalIds.get(second)).append('=')
                        .append(physicalPackage.isInternallyConnected(terminalIds.get(index),
                            terminalIds.get(second))).append(';');
            }
        }
        appendGeometry(result, physicalGeometry);
        if (physicalGeometry != null) {
            PhysicalPackageGeometry.Placement placed = placedGeometry();
            for (int index = 0; index < physicalGeometry.getTerminals().size(); index++) {
                result.append("terminal[").append(index).append("]:");
                appendPoint(result, placed.getPadPoint(index));
                appendRectangle(result, "pad", placed.getPadBounds(index));
                appendPoint(result, placed.getProbePoint(index));
                appendRectangle(result, "probe", placed.getProbeBounds(index));
                appendPoint(result, placed.getLeadBodyPoint(index));
                appendRectangle(result, "lead", placed.getLeadBounds(index));
                PhysicalPackageGeometry.Terminal terminal = physicalGeometry.getTerminal(index);
                result.append("escape=").append(terminal.getEscapeDx()).append(',')
                    .append(terminal.getEscapeDy()).append(',').append(terminal.getEscapeLength())
                    .append(';');
            }
        }
        return result.toString();
    }

    private PhysicalPackageGeometry.Placement placedGeometry() {
        return physicalGeometry.placedAt(x, y);
    }

    private static void appendGeometry(StringBuilder result, PhysicalPackageGeometry geometry) {
        if (geometry == null) {
            result.append("geometry=null|");
            return;
        }
        result.append("geometry=").append(geometry.getWidth()).append('x')
            .append(geometry.getHeight()).append('|');
        appendRectangle(result, "localBody", geometry.getBodyBounds());
        appendRectangle(result, "localKeepout", geometry.getBodyKeepOut());
        appendRectangle(result, "localCourtyard", geometry.getRoutingCourtyard());
        appendRectangle(result, "localSelection", geometry.getSelectionEnvelope());
        appendRectangle(result, "localDrag", geometry.getDragEnvelope());
        Vector<PhysicalPackageGeometry.Terminal> terminals = geometry.getTerminals();
        for (int index = 0; index < terminals.size(); index++) {
            PhysicalPackageGeometry.Terminal terminal = terminals.get(index);
            result.append("localTerminal[").append(index).append("]=")
                .append(terminal.getTerminalId()).append('|');
            appendPoint(result, terminal.getPadCenter());
            appendRectangle(result, "pad", terminal.getPadBounds());
            appendPoint(result, terminal.getProbeCenter());
            appendRectangle(result, "probe", terminal.getProbeBounds());
            PhysicalPackageGeometry.Lead lead = terminal.getLead();
            appendPoint(result, lead.getPadPoint());
            appendPoint(result, lead.getBodyPoint());
            appendRectangle(result, "lead", lead.getBounds());
            result.append("escape=").append(terminal.getEscapeDx()).append(',')
                .append(terminal.getEscapeDy()).append(',').append(terminal.getEscapeLength())
                .append(';');
        }
    }

    private static void appendPoint(StringBuilder result, Point point) {
        result.append("point=").append(point == null ? "null" : point.x + "," + point.y)
            .append(';');
    }

    private static void appendRectangle(StringBuilder result, String name, Rectangle rectangle) {
        result.append(name).append('=');
        if (rectangle == null)
            result.append("null");
        else
            result.append(rectangle.x).append(',').append(rectangle.y).append(',')
                .append(rectangle.width).append(',').append(rectangle.height);
        result.append(';');
    }
}
