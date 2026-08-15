package com.lushprojects.circuitjs1.client;

import java.util.HashMap;
import java.util.Collections;
import java.util.Comparator;
import java.util.Vector;

class PcbBoardLayout {
    private final int width;
    private final int height;
    private final Rectangle boardOutline;
    private final Rectangle partsTray;
    private final HashMap<String, PcbPadPlacement> pads =
        new HashMap<String, PcbPadPlacement>();
    private final HashMap<String, PcbComponentPlacement> components =
        new HashMap<String, PcbComponentPlacement>();
    private final Vector<PcbTraceGeometry> traces = new Vector<PcbTraceGeometry>();

    PcbBoardLayout(int width, int height, Rectangle boardOutline, Rectangle partsTray) {
        this.width = width;
        this.height = height;
        this.boardOutline = boardOutline;
        this.partsTray = partsTray;
    }

    void addPad(PcbPadPlacement pad) {
        if (pads.put(pad.getPadId(), pad) != null)
            throw new IllegalArgumentException("Duplicate PCB pad placement: " + pad.getPadId());
    }

    void addComponent(PcbComponentPlacement component) {
        if (components.put(component.getComponentId(), component) != null)
            throw new IllegalArgumentException("Duplicate PCB component placement: " +
                component.getComponentId());
    }

    void addTrace(PcbTraceGeometry trace) { traces.add(trace); }

    void validateAgainst(TroubleshootBoard board) {
        for (String padId : pads.keySet()) {
            BoardPad pad = board.getPad(padId);
            if (pad == null)
                throw new IllegalStateException("PCB layout references unknown pad: " + padId);
        }
        for (String componentId : components.keySet()) {
            if (board.getComponent(componentId) == null)
                throw new IllegalStateException("PCB layout references unknown component: " + componentId);
        }
        for (PcbTraceGeometry trace : traces) {
            if (board.getNet(trace.getNetId()) == null)
                throw new IllegalStateException("PCB layout references unknown net: " + trace.getNetId());
        }
        for (String padId : board.getPadIds()) {
            if (!pads.containsKey(padId))
                throw new IllegalStateException("PCB layout is missing pad: " + padId);
        }
    }

    void validateGeometry(TroubleshootBoard board) {
        validateAgainst(board);
        for (String componentId : board.getComponentIds()) {
            if (!components.containsKey(componentId))
                throw new IllegalStateException("PCB layout is missing component: " + componentId);
        }
        if (boardOutline.width <= 0 || boardOutline.height <= 0 || partsTray.width <= 0 ||
                partsTray.height <= 0)
            throw new IllegalStateException("PCB layout has an invalid board or parts tray");

        Vector<PcbComponentPlacement> componentList = getComponents();
        for (int first = 0; first < componentList.size(); first++) {
            PcbComponentPlacement firstPlacement = componentList.get(first);
            requireInside(firstPlacement.getBounds(), boardOutline,
                "component " + firstPlacement.getComponentId());
            requireInside(firstPlacement.getKeepOut(), boardOutline,
                "component keep-out " + firstPlacement.getComponentId());
            for (int second = first + 1; second < componentList.size(); second++) {
                if (firstPlacement.getBounds().intersects(componentList.get(second).getBounds()))
                    throw new IllegalStateException("PCB component bodies overlap: " +
                        firstPlacement.getComponentId() + " and " +
                        componentList.get(second).getComponentId());
            }
        }

        Vector<PcbPadPlacement> padList = getPads();
        for (PcbPadPlacement pad : padList) {
            requireInside(new Rectangle(pad.getX(), pad.getY(), 0, 0), boardOutline,
                "pad " + pad.getPadId());
            for (PcbPadPlacement other : padList) {
                if (pad == other)
                    continue;
                int dx = pad.getX() - other.getX();
                int dy = pad.getY() - other.getY();
                if (dx * dx + dy * dy < 26 * 26)
                    throw new IllegalStateException("PCB pads overlap: " + pad.getPadId() +
                        " and " + other.getPadId());
            }
        }

        HashMap<String, Boolean> representedNets = new HashMap<String, Boolean>();
        for (PcbTraceGeometry trace : traces) {
            if (trace.getStartPadId() == null || trace.getEndPadId() == null)
                throw new IllegalStateException("PCB trace has no pad endpoints: " +
                    trace.getNetId());
            BoardPad startPad = board.getPad(trace.getStartPadId());
            BoardPad endPad = board.getPad(trace.getEndPadId());
            if (startPad == null || endPad == null ||
                    !trace.getNetId().equals(startPad.getNetId()) ||
                    !trace.getNetId().equals(endPad.getNetId()))
                throw new IllegalStateException("PCB trace endpoints do not match net: " +
                    trace.getNetId());
            PcbPadPlacement start = pads.get(trace.getStartPadId());
            PcbPadPlacement end = pads.get(trace.getEndPadId());
            int[] xPoints = trace.getXPoints();
            int[] yPoints = trace.getYPoints();
            if (xPoints[0] != start.getX() || yPoints[0] != start.getY() ||
                    xPoints[xPoints.length - 1] != end.getX() ||
                    yPoints[yPoints.length - 1] != end.getY())
                throw new IllegalStateException("PCB trace does not land on its pads: " +
                    trace.getNetId());
            representedNets.put(trace.getNetId(), Boolean.TRUE);
            for (int index = 0; index < xPoints.length; index++) {
                if (!inside(boardOutline, xPoints[index], yPoints[index]))
                    throw new IllegalStateException("PCB trace leaves board: " + trace.getNetId());
                if (index > 0 && xPoints[index] != xPoints[index - 1] &&
                        yPoints[index] != yPoints[index - 1])
                    throw new IllegalStateException("PCB trace is not Manhattan routed: " +
                        trace.getNetId());
            }
            validateTraceKeepOuts(board, trace, startPad, endPad);
        }
        for (String netId : board.getNetIds()) {
            if (!representedNets.containsKey(netId))
                throw new IllegalStateException("PCB net has no copper trace: " + netId);
        }
        for (int first = 0; first < traces.size(); first++) {
            for (int second = first + 1; second < traces.size(); second++) {
                if (tracesCross(traces.get(first), traces.get(second)))
                    throw new IllegalStateException("Unrelated PCB traces cross: " +
                        traces.get(first).getNetId() + " and " + traces.get(second).getNetId());
            }
        }
    }

    private void validateTraceKeepOuts(TroubleshootBoard board, PcbTraceGeometry trace,
            BoardPad startPad, BoardPad endPad) {
        for (PcbComponentPlacement component : components.values()) {
            if (component.getComponentId().equals(startPad.getComponentId()) ||
                    component.getComponentId().equals(endPad.getComponentId()))
                continue;
            Rectangle keepOut = component.getKeepOut();
            int[] xPoints = trace.getXPoints();
            int[] yPoints = trace.getYPoints();
            for (int index = 1; index < xPoints.length; index++) {
                if (segmentIntersects(keepOut, xPoints[index - 1], yPoints[index - 1],
                        xPoints[index], yPoints[index]))
                    throw new IllegalStateException("PCB trace passes through component keep-out: " +
                        trace.getNetId() + " / " + component.getComponentId());
            }
        }
    }

    private static void requireInside(Rectangle rectangle, Rectangle outer, String description) {
        if (!inside(outer, rectangle.x, rectangle.y) ||
                !inside(outer, rectangle.x + rectangle.width, rectangle.y + rectangle.height))
            throw new IllegalStateException("PCB " + description + " leaves board outline");
    }

    private static boolean inside(Rectangle rectangle, int x, int y) {
        return x >= rectangle.x && y >= rectangle.y &&
            x <= rectangle.x + rectangle.width && y <= rectangle.y + rectangle.height;
    }

    private static boolean segmentIntersects(Rectangle rectangle, int x1, int y1,
            int x2, int y2) {
        int left = Math.min(x1, x2);
        int right = Math.max(x1, x2);
        int top = Math.min(y1, y2);
        int bottom = Math.max(y1, y2);
        return right >= rectangle.x && left <= rectangle.x + rectangle.width &&
            bottom >= rectangle.y && top <= rectangle.y + rectangle.height;
    }

    private static boolean tracesCross(PcbTraceGeometry first, PcbTraceGeometry second) {
        int[] firstX = first.getXPoints();
        int[] firstY = first.getYPoints();
        int[] secondX = second.getXPoints();
        int[] secondY = second.getYPoints();
        for (int firstIndex = 1; firstIndex < firstX.length; firstIndex++) {
            for (int secondIndex = 1; secondIndex < secondX.length; secondIndex++) {
                if (segmentsIntersect(firstX[firstIndex - 1], firstY[firstIndex - 1],
                        firstX[firstIndex], firstY[firstIndex], secondX[secondIndex - 1],
                        secondY[secondIndex - 1], secondX[secondIndex], secondY[secondIndex]))
                    return true;
            }
        }
        return false;
    }

    private static boolean segmentsIntersect(int ax1, int ay1, int ax2, int ay2,
            int bx1, int by1, int bx2, int by2) {
        int aLeft = Math.min(ax1, ax2);
        int aRight = Math.max(ax1, ax2);
        int aTop = Math.min(ay1, ay2);
        int aBottom = Math.max(ay1, ay2);
        int bLeft = Math.min(bx1, bx2);
        int bRight = Math.max(bx1, bx2);
        int bTop = Math.min(by1, by2);
        int bBottom = Math.max(by1, by2);
        if (aLeft > bRight || bLeft > aRight || aTop > bBottom || bTop > aBottom)
            return false;
        boolean aHorizontal = ay1 == ay2;
        boolean bHorizontal = by1 == by2;
        if (aHorizontal == bHorizontal)
            return true;
        return aHorizontal ? bx1 >= aLeft && bx1 <= aRight && ay1 >= bTop && ay1 <= bBottom :
            ax1 >= bLeft && ax1 <= bRight && by1 >= aTop && by1 <= aBottom;
    }

    String geometryFingerprint() {
        StringBuilder result = new StringBuilder();
        result.append(width).append('x').append(height).append('|');
        result.append(boardOutline.x).append(',').append(boardOutline.y).append(',')
            .append(boardOutline.width).append(',').append(boardOutline.height).append('|');
        result.append(partsTray.x).append(',').append(partsTray.y).append(',')
            .append(partsTray.width).append(',').append(partsTray.height).append('|');
        Vector<String> componentIds = new Vector<String>(components.keySet());
        Collections.sort(componentIds);
        for (String id : componentIds) {
            PcbComponentPlacement placement = components.get(id);
            result.append("C:").append(id).append('@').append(placement.getX()).append(',')
                .append(placement.getY()).append(',').append(placement.getWidth()).append(',')
                .append(placement.getHeight()).append('!')
                .append(placement.getKeepOut().x).append(',').append(placement.getKeepOut().y)
                .append(',').append(placement.getKeepOut().width).append(',')
                .append(placement.getKeepOut().height).append(';');
        }
        Vector<String> padIds = new Vector<String>(pads.keySet());
        Collections.sort(padIds);
        for (String id : padIds) {
            PcbPadPlacement pad = pads.get(id);
            result.append("P:").append(id).append('@').append(pad.getX()).append(',')
                .append(pad.getY()).append(';');
        }
        Vector<PcbTraceGeometry> orderedTraces = new Vector<PcbTraceGeometry>(traces);
        Collections.sort(orderedTraces, new Comparator<PcbTraceGeometry>() {
            public int compare(PcbTraceGeometry first, PcbTraceGeometry second) {
                int result = first.getNetId().compareTo(second.getNetId());
                if (result != 0)
                    return result;
                result = String.valueOf(first.getStartPadId()).compareTo(
                    String.valueOf(second.getStartPadId()));
                if (result != 0)
                    return result;
                return String.valueOf(first.getEndPadId()).compareTo(
                    String.valueOf(second.getEndPadId()));
            }
        });
        for (PcbTraceGeometry trace : orderedTraces) {
            result.append("T:").append(trace.getNetId()).append(':')
                .append(trace.getStartPadId()).append('-').append(trace.getEndPadId()).append('@');
            int[] xPoints = trace.getXPoints();
            int[] yPoints = trace.getYPoints();
            for (int index = 0; index < xPoints.length; index++)
                result.append(xPoints[index]).append(',').append(yPoints[index]).append(';');
        }
        return result.toString();
    }

    String componentGeometryFingerprint() {
        StringBuilder result = new StringBuilder();
        Vector<String> componentIds = new Vector<String>(components.keySet());
        Collections.sort(componentIds);
        for (String id : componentIds) {
            PcbComponentPlacement placement = components.get(id);
            result.append(id).append('@').append(placement.getX()).append(',')
                .append(placement.getY()).append(',').append(placement.getWidth()).append(',')
                .append(placement.getHeight()).append('!').append(placement.getKeepOut().x)
                .append(',').append(placement.getKeepOut().y).append(',')
                .append(placement.getKeepOut().width).append(',')
                .append(placement.getKeepOut().height).append(';');
        }
        return result.toString();
    }

    String traceGeometryFingerprint() {
        StringBuilder result = new StringBuilder();
        Vector<PcbTraceGeometry> orderedTraces = new Vector<PcbTraceGeometry>(traces);
        Collections.sort(orderedTraces, new Comparator<PcbTraceGeometry>() {
            public int compare(PcbTraceGeometry first, PcbTraceGeometry second) {
                return first.getNetId().compareTo(second.getNetId());
            }
        });
        for (PcbTraceGeometry trace : orderedTraces) {
            result.append(trace.getNetId()).append(':');
            int[] xPoints = trace.getXPoints();
            int[] yPoints = trace.getYPoints();
            for (int index = 0; index < xPoints.length; index++)
                result.append(xPoints[index]).append(',').append(yPoints[index]).append(';');
        }
        return result.toString();
    }

    int getWidth() { return width; }
    int getHeight() { return height; }
    Rectangle getBoardOutline() { return boardOutline; }
    Rectangle getPartsTray() { return partsTray; }
    PcbPadPlacement getPad(String padId) { return pads.get(padId); }
    PcbComponentPlacement getComponent(String componentId) { return components.get(componentId); }
    Vector<PcbPadPlacement> getPads() { return new Vector<PcbPadPlacement>(pads.values()); }
    Vector<PcbComponentPlacement> getComponents() {
        return new Vector<PcbComponentPlacement>(components.values());
    }
    Vector<PcbTraceGeometry> getTraces() { return new Vector<PcbTraceGeometry>(traces); }
}
