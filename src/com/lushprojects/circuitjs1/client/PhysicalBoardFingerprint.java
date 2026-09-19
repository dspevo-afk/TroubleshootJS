package com.lushprojects.circuitjs1.client;

import java.util.Collections;
import java.util.Comparator;
import java.util.Vector;

/** Canonical physical realization, without seed, fault, values or board translation. */
final class PhysicalBoardFingerprint {
    private PhysicalBoardFingerprint() { }

    static String of(GeneratedBoardInstance board) {
        return encode(board, true);
    }

    /** Ignore outline-only changes when deciding whether New Board is actually new. */
    static String novelty(GeneratedBoardInstance board) {
        return encode(board, false);
    }

    private static String encode(GeneratedBoardInstance board, boolean includeOutline) {
        if (board == null || board.getPcbLayout() == null)
            throw new IllegalArgumentException("Missing generated physical board");
        TroubleshootBoard logical = board.getBoard();
        PcbBoardLayout layout = board.getPcbLayout();
        Rectangle outline = layout.getBoardOutline();
        int originX = outline.x, originY = outline.y;
        if (!includeOutline) {
            originX = Integer.MAX_VALUE;
            originY = Integer.MAX_VALUE;
            for (PcbComponentPlacement placement : layout.getComponents()) {
                originX = Math.min(originX, placement.getX());
                originY = Math.min(originY, placement.getY());
            }
            if (originX == Integer.MAX_VALUE || originY == Integer.MAX_VALUE)
                throw new IllegalStateException("Physical board has no components");
        }
        StringBuilder out = new StringBuilder("physical-board@1;");
        if (includeOutline) {
            field(out, board.getCircuitFamilyId());
            field(out, board.getTopologyVariantId());
        }
        if (includeOutline) out.append(outline.width).append(',').append(outline.height).append(';');

        Vector<String> components = logical.getComponentIds();
        Collections.sort(components);
        Vector<String> componentRecords = new Vector<String>();
        for (String id : components) {
            BoardComponent component = logical.getComponent(id);
            PcbComponentPlacement placement = layout.getComponent(id);
            if (component == null || placement == null)
                throw new IllegalStateException("Missing physical component " + id);
            StringBuilder record = new StringBuilder();
            if (includeOutline) field(record, id);
            field(record, component.getType());
            field(record, component.getPhysicalPackage().getId());
            field(record, placement.getGeometryRealization().getVariantKey());
            field(record, placement.getRotation().name());
            field(record, placement.getMountingSide().name());
            record.append(placement.getX() - originX).append(',')
                .append(placement.getY() - originY).append(';');
            componentRecords.add(record.toString());
        }
        Collections.sort(componentRecords);
        for (String record : componentRecords) field(out, record);

        Vector<String> pads = logical.getPadIds();
        Collections.sort(pads);
        Vector<String> padRecords = new Vector<String>();
        for (String id : pads) {
            BoardPad pad = logical.getPad(id);
            PcbPadPlacement placement = layout.getPad(id);
            if (pad == null || placement == null)
                throw new IllegalStateException("Missing physical pad " + id);
            StringBuilder record = new StringBuilder();
            if (includeOutline) { field(record, id); field(record, pad.getNetId()); }
            record.append(placement.getX() - originX).append(',')
                .append(placement.getY() - originY).append(';');
            Rectangle land = placement.getPadBounds();
            record.append(land.width).append(',').append(land.height).append(';');
            padRecords.add(record.toString());
        }
        Collections.sort(padRecords);
        for (String record : padRecords) field(out, record);

        Vector<String> holes = new Vector<String>();
        for (PcbBoardHole hole : layout.getHoles()) {
            holes.add((includeOutline ? hole.id + ':' + hole.netId + ':' : "") + hole.kind + ':' +
                (hole.x - originX) + ',' + (hole.y - originY) + ':' +
                hole.drillRadius + ',' + hole.landRadius + ':' + hole.exposure);
        }
        Collections.sort(holes);
        for (String hole : holes) field(out, hole);

        if (includeOutline) exactRoutes(out, layout, originX, originY);
        else mergedCopper(out, layout, originX, originY);
        return out.toString();
    }

    private static void exactRoutes(StringBuilder out, PcbBoardLayout layout, int originX, int originY) {
        Vector<String> routes = new Vector<String>();
        for (PcbTraceGeometry trace : layout.getTraces()) {
            int[] x = trace.getXPoints(), y = trace.getYPoints();
            StringBuilder forward = new StringBuilder(), reverse = new StringBuilder();
            for (int i = 0; i < x.length; i++) {
                forward.append(x[i] - originX).append(',').append(y[i] - originY).append('/');
                int j = x.length - i - 1;
                reverse.append(x[j] - originX).append(',').append(y[j] - originY).append('/');
            }
            StringBuilder route = new StringBuilder();
            field(route, trace.getNetId());
            field(route, trace.getLayer().name());
            field(route, trace.getExposure().name());
            field(route, forward.toString().compareTo(reverse.toString()) <= 0 ?
                forward.toString() : reverse.toString());
            routes.add(route.toString());
        }
        Collections.sort(routes);
        for (String route : routes) field(out, route);
    }

    /** Compare the drawn copper union, independent of route tree segmentation. */
    static String copperUnion(PcbBoardLayout layout, int originX, int originY) {
        StringBuilder result = new StringBuilder();
        mergedCopper(result, layout, originX, originY);
        return result.toString();
    }

    private static void mergedCopper(StringBuilder out, PcbBoardLayout layout, int originX, int originY) {
        Vector<Span> spans = new Vector<Span>();
        for (PcbTraceGeometry trace : layout.getTraces()) {
            int[] x = trace.getXPoints(), y = trace.getYPoints();
            for (int i = 1; i < x.length; i++) {
                boolean horizontal = y[i] == y[i - 1];
                if ((horizontal && x[i] == x[i - 1]) || (!horizontal && x[i] != x[i - 1]))
                    throw new IllegalStateException("Non-Manhattan copper in physical fingerprint");
                int fixed = horizontal ? y[i] - originY : x[i] - originX;
                int a = horizontal ? x[i] - originX : y[i] - originY;
                int b = horizontal ? x[i - 1] - originX : y[i - 1] - originY;
                spans.add(new Span(trace.getLayer(), trace.getExposure(), horizontal,
                    fixed, Math.min(a, b), Math.max(a, b)));
            }
        }
        Collections.sort(spans, new Comparator<Span>() {
            public int compare(Span a, Span b) {
                int order = a.layer.ordinal() - b.layer.ordinal();
                if (order != 0) return order;
                order = a.exposure.ordinal() - b.exposure.ordinal();
                if (order != 0) return order;
                if (a.horizontal != b.horizontal) return a.horizontal ? -1 : 1;
                order = compareInt(a.fixed, b.fixed);
                return order != 0 ? order : compareInt(a.start, b.start);
            }
        });
        Span run = null;
        for (Span next : spans) {
            if (run != null && run.sameLine(next) && next.start <= run.end) {
                run.end = Math.max(run.end, next.end);
            } else {
                if (run != null) field(out, run.record());
                run = next;
            }
        }
        if (run != null) field(out, run.record());
    }

    private static int compareInt(int a, int b) { return a < b ? -1 : a == b ? 0 : 1; }

    private static final class Span {
        final PcbCopperLayer layer;
        final PcbCopperAccess.Exposure exposure;
        final boolean horizontal;
        final int fixed, start;
        int end;
        Span(PcbCopperLayer layer, PcbCopperAccess.Exposure exposure,
                boolean horizontal, int fixed, int start, int end) {
            this.layer = layer; this.exposure = exposure; this.horizontal = horizontal;
            this.fixed = fixed; this.start = start; this.end = end;
        }
        boolean sameLine(Span other) {
            return layer == other.layer && exposure == other.exposure &&
                horizontal == other.horizontal && fixed == other.fixed;
        }
        String record() {
            return layer.name() + '/' + exposure.name() + '/' + (horizontal ? 'H' : 'V') +
                '/' + fixed + '/' + start + '/' + end;
        }
    }

    private static void field(StringBuilder out, String value) {
        if (value == null) throw new IllegalStateException("Missing physical fingerprint field");
        out.append(value.length()).append(':').append(value).append(';');
    }
}
