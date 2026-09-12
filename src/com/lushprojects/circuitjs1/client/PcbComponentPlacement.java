package com.lushprojects.circuitjs1.client;

import java.util.Vector;

/** Immutable board placement of a package-backed physical component. */
class PcbComponentPlacement {
    private final String componentId;
    private final int x;
    private final int y;
    private final int width;
    private final int height;
    private final PcbPackagePose pose;
    private final Rectangle keepOut;
    private final Rectangle routingCourtyard;
    private final PhysicalPackage physicalPackage;
    private final PhysicalPackageGeometry physicalGeometry;
    private final String geometryVariantKey;
    private final String geometryTransformKey;
    private final PcbGeometryContractVersion geometryContractVersion;
    private final PhysicalGeometryRealization geometryRealization;

    PcbComponentPlacement(String componentId, int x, int y, int width, int height,
            Rectangle keepOut, Rectangle routingCourtyard, PhysicalPackage physicalPackage,
            PhysicalPackageGeometry physicalGeometry) {
        this(componentId, PcbPackagePose.top(x, y), width, height, keepOut, routingCourtyard,
            physicalPackage, physicalGeometry, null);
    }

    private PcbComponentPlacement(String componentId, PcbPackagePose pose, int width, int height,
            Rectangle keepOut, Rectangle routingCourtyard, PhysicalPackage physicalPackage,
            PhysicalPackageGeometry physicalGeometry,
            PhysicalGeometryRealization suppliedGeometryRealization) {
        int x = pose == null ? 0 : pose.getX();
        int y = pose == null ? 0 : pose.getY();
        if (componentId == null || componentId.trim().length() == 0 || keepOut == null ||
                keepOut.width <= 0 || keepOut.height <= 0 || routingCourtyard == null ||
                routingCourtyard.width <= 0 || routingCourtyard.height <= 0 || width <= 0 ||
                height <= 0)
            throw new IllegalArgumentException("Invalid PCB component placement: " + componentId);
        if (physicalPackage == null || physicalGeometry == null || pose == null)
            throw new IllegalArgumentException("Package-backed placement required: " + componentId);
        if (!physicalPackage.supportsPose(pose))
            throw new IllegalArgumentException("Package does not declare requested PCB pose: " +
                componentId + " / " + pose.fingerprint());
        if (!physicalPackage.acceptsGeometry(physicalGeometry))
            throw new IllegalArgumentException("Foreign or undeclared package geometry: " +
                componentId);
        if (!physicalPackage.getGeometryContractVersion().equals(
                physicalGeometry.getGeometryContractVersion()))
            throw new IllegalArgumentException("Package geometry contract version mismatch: " +
                componentId);
        PhysicalPackageGeometry.Placement placed = physicalGeometry.placedAt(pose);
        if (width != pose.getPlacedWidth(physicalGeometry.getWidth(), physicalGeometry.getHeight()) ||
                height != pose.getPlacedHeight(physicalGeometry.getWidth(),
                    physicalGeometry.getHeight()) ||
                !placed.getBodyKeepOut().equals(keepOut) ||
                !placed.getRoutingCourtyard().equals(routingCourtyard))
            throw new IllegalArgumentException("Placement diverges from package geometry: " +
                componentId);
        String variantKey = physicalPackage.getGeometryVariantKey(physicalGeometry);
        String transformKey = physicalPackage.getGeometryVariantTransformKey(physicalGeometry);
        if (variantKey == null || transformKey == null)
            throw new IllegalArgumentException("Package geometry has no canonical variant: " +
                componentId);

        this.componentId = componentId;
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        this.pose = pose;
        this.keepOut = new Rectangle(keepOut);
        this.routingCourtyard = new Rectangle(routingCourtyard);
        this.physicalPackage = physicalPackage;
        this.physicalGeometry = physicalGeometry;
        this.geometryVariantKey = variantKey;
        this.geometryTransformKey = transformKey;
        this.geometryContractVersion = physicalPackage.getGeometryContractVersion();
        if (suppliedGeometryRealization == null)
            this.geometryRealization = new PhysicalGeometryRealization(physicalPackage,
                physicalGeometry, geometryVariantKey, geometryTransformKey,
                geometryContractVersion);
        else {
            validateGeometryRealization(suppliedGeometryRealization, physicalPackage,
                physicalGeometry, geometryVariantKey, geometryTransformKey,
                geometryContractVersion, componentId);
            this.geometryRealization = suppliedGeometryRealization;
        }
    }

    /** Safe translation with package-backed identity preservation. */
    PcbComponentPlacement translatedTo(int x, int y) {
        PcbPackagePose translatedPose = pose.translatedTo(x, y);
        PhysicalPackageGeometry.Placement placed = physicalGeometry.placedAt(translatedPose);
        return new PcbComponentPlacement(componentId, translatedPose, width, height,
            placed.getBodyKeepOut(), placed.getRoutingCourtyard(), physicalPackage,
            physicalGeometry, geometryRealization);
    }

    /** Delta translation that rejects overflow while preserving geometry realization. */
    PcbComponentPlacement translatedBy(int dx, int dy) {
        return translatedTo(checkedAdd(this.x, dx), checkedAdd(this.y, dy));
    }

    static PcbComponentPlacement fromPhysicalGeometry(String componentId, int x, int y,
            PhysicalPackage physicalPackage, PhysicalPackageGeometry geometry) {
        if (physicalPackage == null)
            throw new IllegalArgumentException("Package-backed placement required: " + componentId);
        if (geometry == null)
            throw new IllegalArgumentException("Missing package geometry: " + componentId);
        return fromPhysicalGeometry(componentId, PcbPackagePose.top(x, y), physicalPackage,
            geometry);
    }

    static PcbComponentPlacement fromPhysicalGeometry(String componentId, PcbPackagePose pose,
            PhysicalPackage physicalPackage, PhysicalPackageGeometry geometry) {
        if (physicalPackage == null || pose == null)
            throw new IllegalArgumentException("Package-backed placement required: " + componentId);
        if (geometry == null)
            throw new IllegalArgumentException("Missing package geometry: " + componentId);
        PhysicalPackageGeometry.Placement placed = geometry.placedAt(pose);
        return new PcbComponentPlacement(componentId, pose,
            pose.getPlacedWidth(geometry.getWidth(), geometry.getHeight()),
            pose.getPlacedHeight(geometry.getWidth(), geometry.getHeight()),
            placed.getBodyKeepOut(), placed.getRoutingCourtyard(), physicalPackage, geometry, null);
    }

    String getComponentId() { return componentId; }
    int getX() { return x; }
    int getY() { return y; }
    int getWidth() { return width; }
    int getHeight() { return height; }
    PcbPackagePose getPose() { return pose; }
    PcbRotation getRotation() { return pose.getRotation(); }
    PcbBoardSide getMountingSide() { return pose.getMountingSide(); }
    Rectangle getBounds() { return new Rectangle(x, y, width, height); }
    Rectangle getBodyBounds() { return placedGeometry().getBodyBounds(); }
    Rectangle getKeepOut() { return new Rectangle(keepOut); }
    Rectangle getRoutingCourtyard() { return new Rectangle(routingCourtyard); }
    Rectangle getSelectionEnvelope() { return placedGeometry().getSelectionEnvelope(); }
    Rectangle getDragEnvelope() { return placedGeometry().getDragEnvelope(); }

    Rectangle getLeadBounds(int index) { return placedGeometry().getLeadBounds(index); }
    Rectangle getLeadBounds(int index, boolean lifted) {
        return placedGeometry().getLeadBounds(index, lifted);
    }
    Point getLeadBodyPoint(int index) { return placedGeometry().getLeadBodyPoint(index); }
    Point getLeadBodyPoint(int index, boolean lifted) {
        return placedGeometry().getLeadBodyPoint(index, lifted);
    }
    Point getLeadEndPoint(int index) { return placedGeometry().getLeadEndPoint(index); }
    Point getLeadEndPoint(int index, boolean lifted) {
        return placedGeometry().getLeadEndPoint(index, lifted);
    }
    Point getConnectedBoardEndPoint(int index) {
        return placedGeometry().getConnectedBoardEndPoint(index);
    }
    Point getLiftedFreeEndPoint(int index) {
        return placedGeometry().getLiftedFreeEndPoint(index);
    }

    /** Board-pad surface accessor. */
    Point getBoardPadProbeCenter(int index) {
        return placedGeometry().getBoardPadProbeCenter(index);
    }
    /** Board-pad surface accessor. */
    Rectangle getBoardPadProbeBounds(int index) {
        return placedGeometry().getBoardPadProbeBounds(index);
    }
    /** Connected or lifted component-lead surface accessor. */
    Point getComponentLeadProbeCenter(int index) {
        return placedGeometry().getComponentLeadProbeCenter(index);
    }
    /** Connected or lifted component-lead surface accessor. */
    Point getComponentLeadProbeCenter(int index, boolean lifted) {
        return placedGeometry().getComponentLeadProbeCenter(index, lifted);
    }
    /** Connected or lifted component-lead surface accessor. */
    Rectangle getComponentLeadProbeBounds(int index) {
        return placedGeometry().getComponentLeadProbeBounds(index);
    }
    /** Connected or lifted component-lead surface accessor. */
    Rectangle getComponentLeadProbeBounds(int index, boolean lifted) {
        return placedGeometry().getComponentLeadProbeBounds(index, lifted);
    }

    Rectangle getPadBounds(int index) { return placedGeometry().getPadBounds(index); }
    Point getPadPoint(int index) { return placedGeometry().getPadPoint(index); }

    PhysicalPackage getPhysicalPackage() { return physicalPackage; }
    PhysicalPackageGeometry getPhysicalGeometry() { return physicalGeometry; }
    String getGeometryVariantKey() { return geometryVariantKey; }
    String getGeometryTransformKey() { return geometryTransformKey; }
    PcbGeometryContractVersion getGeometryContractVersion() { return geometryContractVersion; }
    int getGeometryContractVersionValue() { return geometryContractVersion.getValue(); }
    PhysicalGeometryRealization getGeometryRealization() { return geometryRealization; }

    String geometryFingerprint() {
        StringBuilder result = new StringBuilder();
        result.append("component=").append(componentId).append('@').append(x).append(',')
            .append(y).append(',').append(width).append(',').append(height).append('|')
            .append("pose=").append(pose.fingerprint()).append('|');
        appendRectangle(result, "body", getBodyBounds());
        appendRectangle(result, "keepout", keepOut);
        appendRectangle(result, "courtyard", routingCourtyard);
        appendRectangle(result, "selection", getSelectionEnvelope());
        appendRectangle(result, "drag", getDragEnvelope());
        result.append("package=").append(physicalPackage.getId()).append('|')
            .append("connector=").append(physicalPackage.isConnector()).append('|')
            .append("variant=").append(geometryVariantKey).append('|')
            .append("transform=").append(geometryTransformKey).append('|')
            .append("version=").append(geometryContractVersion.getValue()).append('|')
            .append("looseVariant=").append(
                physicalPackage.getDefaultLooseGeometryVariantKey()).append('|')
            .append("developerGeneric=").append(physicalPackage.isDeveloperGeneric()).append('|');
        Vector<String> terminalIds = physicalPackage.getTerminalIds();
        for (int index = 0; index < terminalIds.size(); index++) {
            for (int second = index + 1; second < terminalIds.size(); second++)
                result.append("internal:").append(terminalIds.get(index)).append('=')
                    .append(terminalIds.get(second)).append('=')
                    .append(physicalPackage.isInternallyConnected(terminalIds.get(index),
                        terminalIds.get(second))).append(';');
        }
        appendGeometry(result, physicalGeometry);
        PhysicalPackageGeometry.Placement placed = placedGeometry();
        for (int index = 0; index < physicalGeometry.getTerminals().size(); index++) {
            PhysicalPackageGeometry.Terminal terminal = physicalGeometry.getTerminal(index);
            result.append("terminal[").append(index).append("]:")
                .append(terminal.getTerminalId()).append('|');
            appendPoint(result, placed.getPadPoint(index));
            appendRectangle(result, "pad", placed.getPadBounds(index));
            appendPoint(result, placed.getBoardPadProbeCenter(index));
            appendRectangle(result, "boardProbe", placed.getBoardPadProbeBounds(index));
            appendPoint(result, placed.getComponentLeadProbeCenter(index));
            appendRectangle(result, "connectedProbe",
                placed.getComponentLeadProbeBounds(index));
            appendPoint(result, placed.getComponentLeadProbeCenter(index, true));
            appendRectangle(result, "liftedProbe",
                placed.getComponentLeadProbeBounds(index, true));
            appendPoint(result, placed.getLeadEndPoint(index));
            appendPoint(result, placed.getLeadBodyPoint(index));
            appendRectangle(result, "connectedLead", placed.getLeadBounds(index));
            appendPoint(result, placed.getLeadEndPoint(index, true));
            appendPoint(result, placed.getLeadBodyPoint(index, true));
            appendRectangle(result, "liftedLead", placed.getLeadBounds(index, true));
            result.append("escape=").append(placed.getEscapeDx(index)).append(',')
                .append(placed.getEscapeDy(index)).append(',').append(placed.getEscapeLength(index))
                .append(" attachment=").append(terminal.getAttachment()).append(';');
        }
        return result.toString();
    }

    private PhysicalPackageGeometry.Placement placedGeometry() {
        return physicalGeometry.placedAt(pose);
    }

    private static int checkedAdd(int first, int second) {
        long value = (long) first + second;
        return checkedInt(value);
    }

    private static int checkedInt(long value) {
        if (value < Integer.MIN_VALUE || value > Integer.MAX_VALUE)
            throw new IllegalArgumentException("PCB component placement overflow: " + value);
        return (int) value;
    }

    private static void validateGeometryRealization(
            PhysicalGeometryRealization realization, PhysicalPackage physicalPackage,
            PhysicalPackageGeometry physicalGeometry, String variantKey, String transformKey,
            PcbGeometryContractVersion geometryContractVersion, String componentId) {
        PhysicalPackage.GeometryVariant variant = physicalPackage.getGeometryVariant(
            realization.getVariantKey());
        if (!physicalPackage.acceptsGeometry(physicalGeometry) ||
                realization.getPhysicalPackage() != physicalPackage ||
                realization.getPhysicalGeometry() != physicalGeometry ||
                variant == null || variant.getGeometry() != physicalGeometry ||
                !variantKey.equals(realization.getVariantKey()) ||
                !transformKey.equals(realization.getTransformKey()) ||
                !transformKey.equals(variant.getTransformKey()) ||
                !geometryContractVersion.equals(realization.getGeometryContractVersion()) ||
                !geometryContractVersion.equals(physicalPackage.getGeometryContractVersion()) ||
                !geometryContractVersion.equals(physicalGeometry.getGeometryContractVersion()))
            throw new IllegalArgumentException("Mismatched physical geometry realization: " +
                componentId);
    }

    private static void appendGeometry(StringBuilder result, PhysicalPackageGeometry geometry) {
        result.append("geometry=").append(geometry.getWidth()).append('x')
            .append(geometry.getHeight()).append('|')
            .append("version=").append(geometry.getGeometryContractVersionValue()).append('|')
            .append("developerGeneric=").append(geometry.isDeveloperGeneric()).append('|');
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
            appendPoint(result, terminal.getBoardPadProbeCenter());
            appendRectangle(result, "boardProbe", terminal.getBoardPadProbeBounds());
            appendLead(result, "connected", terminal.getConnectedLead());
            appendLead(result, "lifted", terminal.getLiftedLead());
            result.append("escape=").append(terminal.getEscapeDx()).append(',')
                .append(terminal.getEscapeDy()).append(',').append(terminal.getEscapeLength())
                .append(" attachment=").append(terminal.getAttachment()).append(';');
        }
    }

    private static void appendLead(StringBuilder result, String name,
            PhysicalPackageGeometry.Lead lead) {
        result.append(name).append('=');
        appendPoint(result, lead.getEndPoint());
        appendPoint(result, lead.getBodyPoint());
        appendRectangle(result, name + "Bounds", lead.getBounds());
        appendPoint(result, lead.getComponentProbeCenter());
        appendRectangle(result, name + "Probe", lead.getComponentProbeBounds());
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
