package com.lushprojects.circuitjs1.client;

import java.util.Collections;
import java.util.Comparator;
import java.util.Map;
import java.util.TreeMap;
import java.util.Vector;

/** Bounded drawing-space metrics for comparing complete routed PCB candidates. */
final class PcbPackingMetrics {
    static final int GRID_STEP = 10;
    static final int MAX_GRID_CELLS = 250000;

    final long area;
    final long copper;
    final int vias;
    final int protectedVoid;

    PcbPackingMetrics(long area, long copper, int vias, int protectedVoid) {
        if (area <= 0L || copper < 0L || vias < 0 || protectedVoid < 0)
            throw new IllegalArgumentException("PCB packing metrics require positive area and nonnegative values");
        this.area = area;
        this.copper = copper;
        this.vias = vias;
        this.protectedVoid = protectedVoid;
    }

    /** Rejects ties and any candidate that regresses one of the protected metrics. */
    static boolean strictlyImproves(PcbPackingMetrics baseline,
            PcbPackingMetrics candidate) {
        if (baseline == null || candidate == null)
            throw new IllegalArgumentException("Missing PCB packing metrics");
        return candidate.area < baseline.area && candidate.copper <= baseline.copper &&
            candidate.vias <= baseline.vias &&
            candidate.protectedVoid <= baseline.protectedVoid;
    }

    /**
     * Measures drawing occupancy only. Free sampled cells are not proof that a
     * package can be placed or that a PCB is manufacturable.
     */
    static PcbPackingMetrics measure(TroubleshootBoard board, PcbBoardLayout layout) {
        GenerationWorkScope.check();
        if (board == null || layout == null)
            throw new IllegalArgumentException("Missing PCB packing metric input");

        Rectangle outline = layout.getBoardOutline();
        if (outline.width <= 0 || outline.height <= 0)
            throw new IllegalArgumentException("Invalid PCB packing metric outline");
        int columns = outline.width / GRID_STEP;
        int rows = outline.height / GRID_STEP;
        if (columns <= 0 || rows <= 0)
            throw new Unavailable("GRID10_GRID_COUNT_UNSUPPORTED");
        long cells = (long) columns * rows;
        if (cells > MAX_GRID_CELLS)
            throw new Unavailable("GRID10_GRID_COUNT_UNSUPPORTED");

        boolean[] blocked = new boolean[(int) cells];
        PcbPlacementConstraints constraints = board.getPlacementConstraints();
        Vector<PcbComponentPlacement> components = layout.getComponents();
        Collections.sort(components, new Comparator<PcbComponentPlacement>() {
            public int compare(PcbComponentPlacement first, PcbComponentPlacement second) {
                return first.getComponentId().compareTo(second.getComponentId());
            }
        });
        for (PcbComponentPlacement placement : components) {
            BoardComponent component = board.getComponent(placement.getComponentId());
            if (component == null)
                throw new IllegalStateException("PCB metric layout references unknown component: " +
                    placement.getComponentId());
            Vector<String> padIds = component.getPadIds();
            Collections.sort(padIds);
            Vector<PcbPadPlacement> pads = new Vector<PcbPadPlacement>();
            for (String padId : padIds) {
                PcbPadPlacement pad = layout.getPad(padId);
                if (pad == null)
                    throw new IllegalStateException("PCB metric layout is missing pad: " + padId);
                pads.add(pad);
            }
            PcbFootprint footprint = new PcbFootprint(placement, pads);
            PcbPlacementConstraints.Part part = constraints.get(placement.getComponentId());
            if (part == null)
                throw new IllegalStateException("PCB metric layout is missing placement demand: " +
                    placement.getComponentId());
            mark(blocked, columns, rows, outline, PcbPlacementPlanner.envelope(footprint,
                part.accessMargin));
        }

        PcbTraceWidthProfile profile = constraints.getTraceWidthProfile();
        Vector<PcbTraceGeometry> traces = layout.getTraces();
        Collections.sort(traces, TRACE_ORDER);
        for (PcbTraceGeometry trace : traces) {
            int guard = PcbTraceRules.clearance(trace.getWidth(), profile.maximumWidth());
            int[] x = trace.getXPoints();
            int[] y = trace.getYPoints();
            for (int index = 1; index < x.length; index++) {
                long dx = Math.abs((long) x[index] - x[index - 1]);
                long dy = Math.abs((long) y[index] - y[index - 1]);
                Rectangle stroke = new Rectangle(
                    PcbCoordinateSystem.checkedInt((long) Math.min(x[index], x[index - 1]) - guard),
                    PcbCoordinateSystem.checkedInt((long) Math.min(y[index], y[index - 1]) - guard),
                    PcbCoordinateSystem.checkedInt(dx + 2L * guard),
                    PcbCoordinateSystem.checkedInt(dy + 2L * guard));
                mark(blocked, columns, rows, outline, stroke);
            }
        }

        int vias = 0;
        int holeGuard = PcbTraceRules.clearance(profile.maximumWidth(), profile.maximumWidth());
        Vector<PcbBoardHole> holes = layout.getHoles();
        Collections.sort(holes, new Comparator<PcbBoardHole>() {
            public int compare(PcbBoardHole first, PcbBoardHole second) {
                return first.id.compareTo(second.id);
            }
        });
        for (PcbBoardHole hole : holes) {
            if (hole.kind == PcbBoardHole.Kind.VIA) vias++;
            Rectangle bounds = hole.getBounds();
            mark(blocked, columns, rows, outline, new Rectangle(
                PcbCoordinateSystem.checkedInt((long) bounds.x - holeGuard),
                PcbCoordinateSystem.checkedInt((long) bounds.y - holeGuard),
                PcbCoordinateSystem.checkedInt((long) bounds.width + 2L * holeGuard),
                PcbCoordinateSystem.checkedInt((long) bounds.height + 2L * holeGuard)));
        }

        Vector<PcbSilkscreenLabel> labels = layout.getSilkscreenLabels();
        Collections.sort(labels, new Comparator<PcbSilkscreenLabel>() {
            public int compare(PcbSilkscreenLabel first, PcbSilkscreenLabel second) {
                return first.getId().compareTo(second.getId());
            }
        });
        for (PcbSilkscreenLabel label : labels)
            mark(blocked, columns, rows, outline, label.getBounds());

        markIsolation(blocked, columns, rows, outline, constraints, components);

        long copper = 0L;
        for (PcbTraceGeometry trace : traces) {
            GenerationWorkScope.check();
            int[] x = trace.getXPoints();
            int[] y = trace.getYPoints();
            for (int index = 1; index < x.length; index++) {
                long segment = Math.abs((long) x[index] - x[index - 1]) +
                    Math.abs((long) y[index] - y[index - 1]);
                if (Long.MAX_VALUE - copper < segment)
                    throw new IllegalStateException("PCB copper metric overflow");
                copper += segment;
                if ((index & 255) == 0) GenerationWorkScope.check();
            }
        }

        int emptyCells = largestEmptyRectangleCells(blocked, columns, rows);
        long protectedArea = (long) emptyCells * GRID_STEP * GRID_STEP;
        if (protectedArea > Integer.MAX_VALUE)
            throw new IllegalStateException("PCB protected-void metric overflow");
        return new PcbPackingMetrics((long) outline.width * outline.height, copper, vias,
            (int) protectedArea);
    }

    /** Largest all-false rectangle in row-major cell occupancy; returns cell count. */
    static int largestEmptyRectangleCells(boolean[] blocked, int columns, int rows) {
        if (blocked == null || columns <= 0 || rows <= 0 ||
                (long) columns * rows != blocked.length)
            throw new IllegalArgumentException("Invalid PCB occupancy grid");
        GenerationWorkScope.check();
        int[] height = new int[columns];
        int[] stack = new int[columns + 1];
        int bestArea = 0;
        for (int y = 0; y < rows; y++) {
            GenerationWorkScope.check();
            for (int x = 0; x < columns; x++) {
                height[x] = blocked[y * columns + x] ? 0 : height[x] + 1;
            }
            int top = -1;
            for (int x = 0; x <= columns; x++) {
                int current = x == columns ? 0 : height[x];
                while (top >= 0 && height[stack[top]] > current) {
                    int h = height[stack[top--]];
                    int left = top < 0 ? 0 : stack[top] + 1;
                    int width = x - left;
                    bestArea = Math.max(bestArea, h * width);
                }
                if (x < columns) stack[++top] = x;
            }
        }
        return bestArea;
    }

    private static final Comparator<PcbTraceGeometry> TRACE_ORDER =
        new Comparator<PcbTraceGeometry>() {
            public int compare(PcbTraceGeometry first, PcbTraceGeometry second) {
                GenerationWorkScope.check();
                int result = compareNullable(first.getSourceId(), second.getSourceId());
                if (result != 0) return result;
                result = compareNullable(first.getNetId(), second.getNetId());
                if (result != 0) return result;
                result = first.getLayer().compareTo(second.getLayer());
                if (result != 0) return result;
                result = first.getExposure().compareTo(second.getExposure());
                if (result != 0) return result;
                result = compareNullable(first.getStartPadId(), second.getStartPadId());
                if (result != 0) return result;
                result = compareNullable(first.getEndPadId(), second.getEndPadId());
                if (result != 0) return result;
                result = first.getWidth() == second.getWidth() ? 0 :
                    (first.getWidth() < second.getWidth() ? -1 : 1);
                if (result != 0) return result;
                result = compareCoordinates(first.getXPoints(), second.getXPoints());
                if (result != 0) return result;
                return compareCoordinates(first.getYPoints(), second.getYPoints());
            }
        };

    private static int compareNullable(String first, String second) {
        if (first == null) return second == null ? 0 : -1;
        if (second == null) return 1;
        return first.compareTo(second);
    }

    private static int compareCoordinates(int[] first, int[] second) {
        int count = Math.min(first.length, second.length);
        for (int index = 0; index < count; index++)
            if (first[index] != second[index]) return first[index] < second[index] ? -1 : 1;
        if (first.length == second.length) return 0;
        return first.length < second.length ? -1 : 1;
    }

    private static void markIsolation(boolean[] blocked, int columns, int rows,
            Rectangle outline, PcbPlacementConstraints constraints,
            Vector<PcbComponentPlacement> components) {
        TreeMap<String, Rectangle> domains = new TreeMap<String, Rectangle>();
        for (PcbComponentPlacement placement : components) {
            TreeMap<String, Rectangle> courtyards =
                constraints.domainCourtyards(placement);
            for (Map.Entry<String, Rectangle> entry : courtyards.entrySet()) {
                Rectangle prior = domains.get(entry.getKey());
                domains.put(entry.getKey(), prior == null ?
                    new Rectangle(entry.getValue()) : union(prior, entry.getValue()));
            }
        }
        Vector<PcbPlacementConstraints.Barrier> barriers = constraints.getBarriers();
        for (PcbPlacementConstraints.Barrier barrier : barriers) {
            Rectangle first = domains.get(barrier.firstDomain);
            Rectangle second = domains.get(barrier.secondDomain);
            if (first == null || second == null)
                throw new IllegalStateException("PCB isolation metric lacks a declared domain");
            long gap = barrier.clearance;
            Rectangle reserved;
            if ((long) first.x + first.width + gap <= second.x ||
                    (long) second.x + second.width + gap <= first.x) {
                long low = first.x < second.x ? (long) first.x + first.width :
                    (long) second.x + second.width;
                long high = first.x < second.x ? second.x : first.x;
                long middle = low + (high - low) / 2L;
                reserved = new Rectangle(PcbCoordinateSystem.checkedInt(
                    middle - (gap + 1L) / 2L), outline.y, barrier.clearance,
                    outline.height);
            } else {
                long low = first.y < second.y ? (long) first.y + first.height :
                    (long) second.y + second.height;
                long high = first.y < second.y ? second.y : first.y;
                long middle = low + (high - low) / 2L;
                reserved = new Rectangle(outline.x, PcbCoordinateSystem.checkedInt(
                    middle - (gap + 1L) / 2L), outline.width, barrier.clearance);
            }
            mark(blocked, columns, rows, outline, reserved);
        }
    }

    private static Rectangle union(Rectangle first, Rectangle second) {
        long left = Math.min((long) first.x, second.x);
        long top = Math.min((long) first.y, second.y);
        long right = Math.max((long) first.x + first.width,
            (long) second.x + second.width);
        long bottom = Math.max((long) first.y + first.height,
            (long) second.y + second.height);
        return new Rectangle(PcbCoordinateSystem.checkedInt(left),
            PcbCoordinateSystem.checkedInt(top), PcbCoordinateSystem.checkedInt(right - left),
            PcbCoordinateSystem.checkedInt(bottom - top));
    }

    /** Uses the frozen reportWhitespace rectangle-to-cell rounding rule. */
    private static void mark(boolean[] blocked, int columns, int rows,
            Rectangle outline, Rectangle rectangle) {
        if (rectangle == null || rectangle.width <= 0 || rectangle.height <= 0)
            throw new IllegalArgumentException("Invalid PCB occupancy mark");
        PcbCoordinateSystem.requireBoardRectangle(rectangle);
        GenerationWorkScope.check();
        long xStart = Math.max(0L, ((long) rectangle.x - outline.x) / GRID_STEP);
        long yStart = Math.max(0L, ((long) rectangle.y - outline.y) / GRID_STEP);
        long xEnd = Math.min((long) columns - 1L,
            ((long) rectangle.x + rectangle.width - outline.x + GRID_STEP - 1L) / GRID_STEP);
        long yEnd = Math.min((long) rows - 1L,
            ((long) rectangle.y + rectangle.height - outline.y + GRID_STEP - 1L) / GRID_STEP);
        if (xStart > xEnd || yStart > yEnd) return;
        int x0 = PcbCoordinateSystem.checkedInt(xStart);
        int x1 = PcbCoordinateSystem.checkedInt(xEnd);
        int y0 = PcbCoordinateSystem.checkedInt(yStart);
        int y1 = PcbCoordinateSystem.checkedInt(yEnd);
        for (int y = y0; y <= y1; y++) {
            GenerationWorkScope.check();
            int row = y * columns;
            for (int x = x0; x <= x1; x++) {
                blocked[row + x] = true;
                if (((x - x0 + 1) & 255) == 0) GenerationWorkScope.check();
            }
        }
    }

    static final class Unavailable extends RuntimeException {
        Unavailable(String reason) { super(reason); }
    }
}
