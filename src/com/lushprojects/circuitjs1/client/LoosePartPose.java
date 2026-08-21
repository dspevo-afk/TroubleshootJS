package com.lushprojects.circuitjs1.client;

import java.util.Vector;

/**
 * Immutable presentation projection for one loose physical part.
 *
 * The source realization and package geometry remain the physical identity.
 * This value only projects that package-local geometry into one current tray
 * cell; it never changes terminal order, package identity, or electrical
 * endpoints.
 */
final class LoosePartPose {
    enum QuarterTurn {
        NONE,
        CLOCKWISE
    }

    private static final int TRAY_HORIZONTAL_MARGIN = 18;
    private static final int TRAY_ROW_START = 70;
    private static final int TRAY_ROW_PITCH = 48;
    private static final int TRAY_CELL_HEIGHT = 48;
    private static final int TRAY_PAGE_ROWS = 3;

    private final PhysicalGeometryRealization sourceRealization;
    private final PhysicalPackageGeometry sourceGeometry;
    private final PhysicalPartOrientation orientation;
    private final boolean polarityMirrored;
    private final QuarterTurn quarterTurn;
    private final double scale;
    private final Point translation;
    private final Rectangle trayCell;
    private final Rectangle orientedSelectionEnvelope;

    private LoosePartPose(PhysicalGeometryRealization sourceRealization,
            PhysicalPackageGeometry sourceGeometry, PhysicalPartOrientation orientation,
            boolean polarityMirrored, QuarterTurn quarterTurn, double scale,
            Point translation, Rectangle trayCell, Rectangle orientedSelectionEnvelope) {
        this.sourceRealization = sourceRealization;
        this.sourceGeometry = sourceGeometry;
        this.orientation = orientation;
        this.polarityMirrored = polarityMirrored;
        this.quarterTurn = quarterTurn;
        this.scale = scale;
        this.translation = new Point(translation);
        this.trayCell = new Rectangle(trayCell);
        this.orientedSelectionEnvelope = new Rectangle(orientedSelectionEnvelope);
    }

    static LoosePartPose forPart(PhysicalPackage physicalPackage, PhysicalPart<?> part,
            Rectangle tray, int trayIndex) {
        if (physicalPackage == null || tray == null || tray.width <= 0 || tray.height <= 0)
            throw new IllegalArgumentException("Invalid loose-part pose input");
        if (trayIndex < 0 || trayIndex >= TRAY_PAGE_ROWS)
            throw new IllegalArgumentException("Loose part tray row is outside the current page");

        PhysicalGeometryRealization realization = part == null ? null :
            part.getGeometryRealization();
        PhysicalPackageGeometry sourceGeometry;
        if (realization != null) {
            if (!physicalPackage.isEquivalentTo(realization.getPhysicalPackage()) ||
                    !physicalPackage.acceptsGeometry(realization.getPhysicalGeometry()))
                throw new IllegalStateException("Loose part realization does not fit its package");
            sourceGeometry = realization.getPhysicalGeometry();
        } else {
            PhysicalPackage.GeometryVariant defaultVariant = physicalPackage.getGeometryVariant(
                physicalPackage.getDefaultLooseGeometryVariantKey());
            if (defaultVariant == null || defaultVariant.getGeometry() !=
                    physicalPackage.getDefaultLooseGeometry() ||
                    !physicalPackage.acceptsGeometry(defaultVariant.getGeometry()))
                throw new IllegalStateException("Package has no canonical loose geometry: " +
                    physicalPackage.getId());
            sourceGeometry = defaultVariant.getGeometry();
        }

        PhysicalPartOrientation orientation = part == null ?
            PhysicalPartOrientation.NON_POLARIZED : part.getOrientation();
        if (orientation == null)
            throw new IllegalStateException("Loose part has no physical orientation");
        boolean polarityMirrored = orientation == PhysicalPartOrientation.REVERSED;
        if (polarityMirrored && physicalPackage.isConnector() && realization != null &&
                isConnectorMirror(realization.getGeometryTransformKey()))
            polarityMirrored = false;

        QuarterTurn quarterTurn = sourceGeometry.isDeveloperGeneric() ||
            physicalPackage.isConnector() ? QuarterTurn.CLOCKWISE : QuarterTurn.NONE;
        Rectangle orientedSelection = projectRectangle(sourceGeometry.getSelectionEnvelope(),
            sourceGeometry, polarityMirrored, quarterTurn, 1.0, new Point(0, 0));
        if (orientedSelection.width <= 0 || orientedSelection.height <= 0)
            throw new IllegalStateException("Loose package has no positive selection envelope");

        int cellWidth = tray.width - TRAY_HORIZONTAL_MARGIN * 2;
        if (cellWidth <= 0 || TRAY_CELL_HEIGHT <= 0)
            throw new IllegalStateException("Parts tray cannot provide a positive loose cell");
        int centerX = tray.x + tray.width / 2;
        int centerY = tray.y + TRAY_ROW_START + trayIndex * TRAY_ROW_PITCH;
        Rectangle cell = new Rectangle(tray.x + TRAY_HORIZONTAL_MARGIN,
            centerY - TRAY_CELL_HEIGHT / 2, cellWidth, TRAY_CELL_HEIGHT);

        double scale = Math.min(1.0, Math.min(cell.width / (double) orientedSelection.width,
            cell.height / (double) orientedSelection.height));
        if (!(scale > 0.0) || Double.isNaN(scale) || Double.isInfinite(scale))
            throw new IllegalStateException("Parts tray cannot provide a positive loose scale");

        // Axis-aligned projection rounds outward.  Tighten the scale if that
        // raster envelope would otherwise consume one extra cell pixel.
        for (int attempt = 0; attempt < 4; attempt++) {
            Rectangle scaled = projectRectangle(sourceGeometry.getSelectionEnvelope(),
                sourceGeometry, polarityMirrored, quarterTurn, scale, new Point(0, 0));
            if (scaled.width <= cell.width && scaled.height <= cell.height)
                break;
            scale *= Math.min(cell.width / (double) scaled.width,
                cell.height / (double) scaled.height);
            if (!(scale > 0.0) || Double.isNaN(scale) || Double.isInfinite(scale))
                throw new IllegalStateException("Loose package scale became non-positive");
        }

        Rectangle scaledSelection = projectRectangle(sourceGeometry.getSelectionEnvelope(),
            sourceGeometry, polarityMirrored, quarterTurn, scale, new Point(0, 0));
        if (scaledSelection.width > cell.width || scaledSelection.height > cell.height)
            throw new IllegalStateException("Loose package does not fit its tray cell");

        int translationX = centeredTranslation(cell.x, cell.width, scaledSelection.x,
            scaledSelection.width);
        int translationY = centeredTranslation(cell.y, cell.height, scaledSelection.y,
            scaledSelection.height);
        Point translation = new Point(translationX, translationY);
        Rectangle projected = projectRectangle(sourceGeometry.getSelectionEnvelope(),
            sourceGeometry, polarityMirrored, quarterTurn, scale, translation);
        if (!contains(cell, projected))
            throw new IllegalStateException("Loose package escaped its tray cell");

        return new LoosePartPose(realization, sourceGeometry, orientation, polarityMirrored,
            quarterTurn, scale, translation, cell, orientedSelection);
    }

    PhysicalGeometryRealization getSourceRealization() { return sourceRealization; }
    PhysicalPackageGeometry getSourceGeometry() { return sourceGeometry; }
    PhysicalPartOrientation getOrientation() { return orientation; }
    boolean isPolarityMirrored() { return polarityMirrored; }
    QuarterTurn getQuarterTurn() { return quarterTurn; }
    double getScale() { return scale; }
    Point getTranslation() { return new Point(translation); }
    Rectangle getTrayCell() { return new Rectangle(trayCell); }
    Rectangle getOrientedSelectionEnvelope() { return new Rectangle(orientedSelectionEnvelope); }

    Point getTerminalPoint(int terminal) {
        return transformPoint(sourceGeometry.getTerminal(terminal).getPadCenter());
    }

    Rectangle getBodyBounds() { return transformRectangle(sourceGeometry.getBodyBounds()); }
    Rectangle getSelectionEnvelope() {
        return transformRectangle(sourceGeometry.getSelectionEnvelope());
    }
    Rectangle getDragEnvelope() { return transformRectangle(sourceGeometry.getDragEnvelope()); }

    Rectangle getProbeBounds(int terminal) {
        return transformRectangle(getSourceTerminal(terminal).getBoardPadProbeBounds());
    }

    Rectangle getPadBounds(int terminal) {
        return transformRectangle(getSourceTerminal(terminal).getPadBounds());
    }

    Point getComponentLeadPoint(int terminal) {
        return transformPoint(getSourceTerminal(terminal).getComponentLeadProbeCenter(false));
    }

    Rectangle getComponentLeadProbeBounds(int terminal) {
        return transformRectangle(getSourceTerminal(terminal)
            .getComponentLeadProbeBounds(false));
    }

    Point getLeadBodyPoint(int terminal) {
        return transformPoint(getSourceTerminal(terminal).getConnectedLead().getBodyPoint());
    }

    Point getLeadEndPoint(int terminal) {
        return transformPoint(getSourceTerminal(terminal).getConnectedLead().getEndPoint());
    }

    Rectangle getLeadBounds(int terminal) {
        return transformRectangle(getSourceTerminal(terminal).getConnectedLead().getBounds());
    }

    Vector<Rectangle> getProbeSurfaces(int terminal) {
        PhysicalPackageGeometry.Terminal source = getSourceTerminal(terminal);
        Vector<Rectangle> result = new Vector<Rectangle>();
        result.add(transformRectangle(source.getBoardPadProbeBounds()));
        result.add(transformRectangle(source.getComponentLeadProbeBounds(false)));
        return result;
    }

    Point transformPoint(Point source) {
        if (source == null)
            throw new IllegalArgumentException("Cannot project a null loose point");
        double[] oriented = orient(source.x, source.y, sourceGeometry, polarityMirrored,
            quarterTurn);
        return new Point(round(oriented[0] * scale + translation.x),
            round(oriented[1] * scale + translation.y));
    }

    Rectangle transformRectangle(Rectangle source) {
        return projectRectangle(source, sourceGeometry, polarityMirrored, quarterTurn, scale,
            translation);
    }

    private PhysicalPackageGeometry.Terminal getSourceTerminal(int terminal) {
        PhysicalPackageGeometry.Terminal result = sourceGeometry.getTerminal(terminal);
        if (result == null)
            throw new IllegalArgumentException("Unknown loose package terminal: " + terminal);
        return result;
    }

    private static boolean isConnectorMirror(String transformKey) {
        return "MIRROR_X".equals(transformKey) || "DEVELOPER_MIRROR_X".equals(transformKey);
    }

    private static int centeredTranslation(int cellStart, int cellSize, int projectedStart,
            int projectedSize) {
        int centered = round(cellStart + (cellSize - projectedSize) / 2.0 - projectedStart);
        int minimum = cellStart - projectedStart;
        int maximum = cellStart + cellSize - projectedSize - projectedStart;
        return Math.max(minimum, Math.min(maximum, centered));
    }

    private static boolean contains(Rectangle outer, Rectangle inner) {
        return inner.x >= outer.x && inner.y >= outer.y &&
            (long) inner.x + inner.width <= (long) outer.x + outer.width &&
            (long) inner.y + inner.height <= (long) outer.y + outer.height;
    }

    private static Rectangle projectRectangle(Rectangle source, PhysicalPackageGeometry geometry,
            boolean mirror, QuarterTurn turn, double scale, Point translation) {
        if (source == null || geometry == null || translation == null)
            throw new IllegalArgumentException("Invalid loose rectangle projection");
        double[][] corners = new double[][] {
            orient(source.x, source.y, geometry, mirror, turn),
            orient(source.x + source.width, source.y, geometry, mirror, turn),
            orient(source.x, source.y + source.height, geometry, mirror, turn),
            orient(source.x + source.width, source.y + source.height, geometry, mirror, turn)
        };
        double left = Double.POSITIVE_INFINITY;
        double top = Double.POSITIVE_INFINITY;
        double right = Double.NEGATIVE_INFINITY;
        double bottom = Double.NEGATIVE_INFINITY;
        for (double[] corner : corners) {
            left = Math.min(left, corner[0] * scale + translation.x);
            top = Math.min(top, corner[1] * scale + translation.y);
            right = Math.max(right, corner[0] * scale + translation.x);
            bottom = Math.max(bottom, corner[1] * scale + translation.y);
        }
        int projectedLeft = floor(left);
        int projectedTop = floor(top);
        int projectedRight = ceil(right);
        int projectedBottom = ceil(bottom);
        if (projectedRight <= projectedLeft)
            projectedRight = checkedAdd(projectedLeft, 1);
        if (projectedBottom <= projectedTop)
            projectedBottom = checkedAdd(projectedTop, 1);
        return new Rectangle(projectedLeft, projectedTop,
            checkedSubtract(projectedRight, projectedLeft),
            checkedSubtract(projectedBottom, projectedTop));
    }

    private static double[] orient(int x, int y, PhysicalPackageGeometry geometry,
            boolean mirror, QuarterTurn turn) {
        double orientedX = mirror ? geometry.getWidth() - x : x;
        double orientedY = y;
        if (turn == QuarterTurn.CLOCKWISE) {
            double rotatedX = geometry.getHeight() - orientedY;
            double rotatedY = orientedX;
            orientedX = rotatedX;
            orientedY = rotatedY;
        }
        return new double[] { orientedX, orientedY };
    }

    private static int round(double value) {
        if (value < Integer.MIN_VALUE || value > Integer.MAX_VALUE ||
                Double.isNaN(value) || Double.isInfinite(value))
            throw new IllegalStateException("Loose pose coordinate overflow: " + value);
        return (int) Math.round(value);
    }

    private static int floor(double value) {
        return round(Math.floor(value));
    }

    private static int ceil(double value) {
        return round(Math.ceil(value));
    }

    private static int checkedAdd(int first, int second) {
        long value = (long) first + second;
        if (value < Integer.MIN_VALUE || value > Integer.MAX_VALUE)
            throw new IllegalStateException("Loose pose coordinate overflow: " + value);
        return (int) value;
    }

    private static int checkedSubtract(int first, int second) {
        long value = (long) first - second;
        if (value < Integer.MIN_VALUE || value > Integer.MAX_VALUE)
            throw new IllegalStateException("Loose pose coordinate overflow: " + value);
        return (int) value;
    }
}
