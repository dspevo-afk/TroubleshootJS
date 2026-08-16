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
    private final HashMap<String, PcbSilkscreenLabel> silkscreenLabels =
        new HashMap<String, PcbSilkscreenLabel>();
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

    void addSilkscreenLabel(PcbSilkscreenLabel label) {
        if (silkscreenLabels.put(label.getId(), label) != null)
            throw new IllegalArgumentException("Duplicate PCB silkscreen label: " + label.getId());
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
                if (!traces.get(first).getNetId().equals(traces.get(second).getNetId()) &&
                        tracesCross(traces.get(first), traces.get(second)))
                    throw new IllegalStateException("Unrelated PCB traces cross: " +
                        traces.get(first).getNetId() + " and " + traces.get(second).getNetId());
            }
        }
        validateTraceClearance();
        validateSilkscreen(board);
        validateRouteQuality();
    }

    private void validateTraceKeepOuts(TroubleshootBoard board, PcbTraceGeometry trace,
            BoardPad startPad, BoardPad endPad) {
        for (PcbComponentPlacement component : components.values()) {
            Rectangle keepOut = component.getKeepOut();
            PcbPadPlacement traceStartPlacement = pads.get(trace.getStartPadId());
            PcbPadPlacement traceEndPlacement = pads.get(trace.getEndPadId());
            int[] xPoints = trace.getXPoints();
            int[] yPoints = trace.getYPoints();
            for (int index = 1; index < xPoints.length; index++) {
                    if (segmentIntersects(keepOut, xPoints[index - 1], yPoints[index - 1],
                        xPoints[index], yPoints[index]) &&
                        !isLegalEndpointEscape(component, trace, startPad, endPad,
                            xPoints[index - 1], yPoints[index - 1], xPoints[index],
                            yPoints[index]))
                    throw new IllegalStateException("PCB trace passes through component keep-out: " +
                        trace.getNetId() + " / " + component.getComponentId() + " segment " +
                        xPoints[index - 1] + "," + yPoints[index - 1] + " -> " +
                        xPoints[index] + "," + yPoints[index] + " keepOut=" + keepOut +
                        " startPad=" + trace.getStartPadId() + "@" + traceStartPlacement.getX() +
                        "," + traceStartPlacement.getY() + " endPad=" + trace.getEndPadId() +
                        "@" + traceEndPlacement.getX() + "," + traceEndPlacement.getY());
            }
        }
    }

    private boolean isLegalEndpointEscape(PcbComponentPlacement component,
            PcbTraceGeometry trace, BoardPad startPad, BoardPad endPad, int x1, int y1,
            int x2, int y2) {
        PcbPadPlacement escapePad = null;
        if (component.getComponentId().equals(startPad.getComponentId()))
            escapePad = pads.get(trace.getStartPadId());
        else if (component.getComponentId().equals(endPad.getComponentId()))
            escapePad = pads.get(trace.getEndPadId());
        if (escapePad == null)
            return false;
        Rectangle keepOut = component.getKeepOut();
        int left = Math.min(x1, x2);
        int right = Math.max(x1, x2);
        int top = Math.min(y1, y2);
        int bottom = Math.max(y1, y2);
        if (y1 == y2) {
            int overlapLeft = Math.max(left, keepOut.x);
            int overlapRight = Math.min(right, keepOut.x + keepOut.width);
            return overlapLeft <= overlapRight &&
                escapePad.isInEscapeCorridor(overlapLeft, y1) &&
                escapePad.isInEscapeCorridor(overlapRight, y1);
        }
        if (x1 == x2) {
            int overlapTop = Math.max(top, keepOut.y);
            int overlapBottom = Math.min(bottom, keepOut.y + keepOut.height);
            return overlapTop <= overlapBottom &&
                escapePad.isInEscapeCorridor(x1, overlapTop) &&
                escapePad.isInEscapeCorridor(x1, overlapBottom);
        }
        return false;
    }

    private void validateSilkscreen(TroubleshootBoard board) {
        for (String componentId : board.getComponentIds()) {
            if (!silkscreenLabels.containsKey("component:" + componentId))
                throw new IllegalStateException("PCB component has no reference label: " +
                    componentId);
        }
        for (PcbSilkscreenLabel label : silkscreenLabels.values()) {
            Rectangle bounds = label.getBounds();
            requireInside(bounds, boardOutline, "silkscreen label " + label.getId());
            if (label.getTargetPadId() != null && pads.get(label.getTargetPadId()) == null)
                throw new IllegalStateException("Silkscreen label references unknown pad: " +
                    label.getTargetPadId());
            for (PcbComponentPlacement component : components.values()) {
                if (bounds.intersects(component.getBounds()))
                    throw new IllegalStateException("Silkscreen label overlaps component: " +
                        label.getId() + " / " + component.getComponentId());
            }
            for (PcbPadPlacement pad : pads.values()) {
                Rectangle padBounds = new Rectangle(pad.getX() - 16, pad.getY() - 16, 32, 32);
                if (bounds.intersects(padBounds))
                    throw new IllegalStateException("Silkscreen label overlaps pad: " +
                        label.getId() + " / " + pad.getPadId());
            }
            for (PcbTraceGeometry trace : traces) {
                int[] xPoints = trace.getXPoints();
                int[] yPoints = trace.getYPoints();
                for (int index = 1; index < xPoints.length; index++) {
                    if (segmentIntersects(bounds, xPoints[index - 1], yPoints[index - 1],
                            xPoints[index], yPoints[index]))
                        throw new IllegalStateException("Silkscreen label overlaps copper: " +
                            label.getId() + " / " + trace.getNetId());
                }
            }
        }
        Vector<PcbSilkscreenLabel> labels = new Vector<PcbSilkscreenLabel>(
            silkscreenLabels.values());
        for (int first = 0; first < labels.size(); first++) {
            for (int second = first + 1; second < labels.size(); second++) {
                if (labels.get(first).getBounds().intersects(labels.get(second).getBounds()))
                    throw new IllegalStateException("PCB silkscreen labels overlap: " +
                        labels.get(first).getId() + " / " + labels.get(second).getId());
            }
        }
    }

    void validateRouteQuality() {
        for (PcbTraceGeometry trace : traces) {
            int length = getTraceLength(trace);
            int direct = getDirectManhattanDistance(trace);
            int bends = getTraceBendCount(trace);
            if (length <= 0 || direct <= 0)
                throw new IllegalStateException("PCB trace has invalid route length: " +
                    trace.getNetId());
            if (bends > 16)
                throw new IllegalStateException("PCB trace has excessive bends: " +
                    trace.getNetId());
            if (length > direct * 3)
                throw new IllegalStateException("PCB trace has excessive detour: " +
                    trace.getNetId());
            int[] xPoints = trace.getXPoints();
            int[] yPoints = trace.getYPoints();
            for (int first = 0; first < xPoints.length; first++) {
                for (int second = first + 1; second < xPoints.length; second++) {
                    if (xPoints[first] == xPoints[second] && yPoints[first] == yPoints[second])
                        throw new IllegalStateException("PCB trace backtracks: " +
                            trace.getNetId());
                }
            }
        }
    }

    void validateTraceClearance() {
        long minimumSquared = (long) PcbTraceRules.MIN_CENTERLINE_CLEARANCE *
            PcbTraceRules.MIN_CENTERLINE_CLEARANCE;
        for (int first = 0; first < traces.size(); first++) {
            for (int second = first + 1; second < traces.size(); second++) {
                PcbTraceGeometry firstTrace = traces.get(first);
                PcbTraceGeometry secondTrace = traces.get(second);
                if (firstTrace.getNetId().equals(secondTrace.getNetId()))
                    continue;
                int[] firstX = firstTrace.getXPoints();
                int[] firstY = firstTrace.getYPoints();
                int[] secondX = secondTrace.getXPoints();
                int[] secondY = secondTrace.getYPoints();
                for (int firstIndex = 1; firstIndex < firstX.length; firstIndex++) {
                    for (int secondIndex = 1; secondIndex < secondX.length; secondIndex++) {
                        long distanceSquared = segmentDistanceSquared(
                            firstX[firstIndex - 1], firstY[firstIndex - 1], firstX[firstIndex],
                            firstY[firstIndex], secondX[secondIndex - 1],
                            secondY[secondIndex - 1], secondX[secondIndex],
                            secondY[secondIndex]);
                        if (distanceSquared < minimumSquared)
                            throw new IllegalStateException("PCB traces violate copper clearance: " +
                                firstTrace.getNetId() + " / " + secondTrace.getNetId() +
                                " distanceSquared=" + distanceSquared + " minimumSquared=" +
                                minimumSquared + " firstSegment=" +
                                firstX[firstIndex - 1] + "," + firstY[firstIndex - 1] + " -> " +
                                firstX[firstIndex] + "," + firstY[firstIndex] +
                                " secondSegment=" + secondX[secondIndex - 1] + "," +
                                secondY[secondIndex - 1] + " -> " + secondX[secondIndex] + "," +
                                secondY[secondIndex]);
                    }
                }
            }
        }
    }

    private static long segmentDistanceSquared(int ax1, int ay1, int ax2, int ay2,
            int bx1, int by1, int bx2, int by2) {
        if (segmentsIntersect(ax1, ay1, ax2, ay2, bx1, by1, bx2, by2))
            return 0;
        long result = pointSegmentDistanceSquared(ax1, ay1, bx1, by1, bx2, by2);
        result = Math.min(result, pointSegmentDistanceSquared(ax2, ay2, bx1, by1, bx2, by2));
        result = Math.min(result, pointSegmentDistanceSquared(bx1, by1, ax1, ay1, ax2, ay2));
        return Math.min(result, pointSegmentDistanceSquared(bx2, by2, ax1, ay1, ax2, ay2));
    }

    private static long pointSegmentDistanceSquared(int px, int py, int x1, int y1,
            int x2, int y2) {
        int nearestX = px;
        int nearestY = py;
        if (x1 == x2) {
            nearestX = x1;
            nearestY = Math.max(Math.min(py, Math.max(y1, y2)), Math.min(y1, y2));
        } else if (y1 == y2) {
            nearestY = y1;
            nearestX = Math.max(Math.min(px, Math.max(x1, x2)), Math.min(x1, x2));
        }
        long dx = px - nearestX;
        long dy = py - nearestY;
        return dx * dx + dy * dy;
    }

    int getTraceLength(PcbTraceGeometry trace) {
        int length = 0;
        int[] xPoints = trace.getXPoints();
        int[] yPoints = trace.getYPoints();
        for (int index = 1; index < xPoints.length; index++)
            length += Math.abs(xPoints[index] - xPoints[index - 1]) +
                Math.abs(yPoints[index] - yPoints[index - 1]);
        return length;
    }

    int getDirectManhattanDistance(PcbTraceGeometry trace) {
        int[] xPoints = trace.getXPoints();
        int[] yPoints = trace.getYPoints();
        return Math.abs(xPoints[xPoints.length - 1] - xPoints[0]) +
            Math.abs(yPoints[yPoints.length - 1] - yPoints[0]);
    }

    int getTraceBendCount(PcbTraceGeometry trace) {
        int bends = 0;
        int[] xPoints = trace.getXPoints();
        int[] yPoints = trace.getYPoints();
        for (int index = 2; index < xPoints.length; index++) {
            int firstDx = xPoints[index - 1] - xPoints[index - 2];
            int firstDy = yPoints[index - 1] - yPoints[index - 2];
            int secondDx = xPoints[index] - xPoints[index - 1];
            int secondDy = yPoints[index] - yPoints[index - 1];
            if (firstDx != secondDx || firstDy != secondDy)
                bends++;
        }
        return bends;
    }

    double getTraceDetourRatio(PcbTraceGeometry trace) {
        return getTraceLength(trace) / (double) Math.max(1, getDirectManhattanDistance(trace));
    }

    double getRouteQualityScore(TroubleshootBoard board) {
        double score = 0;
        for (PcbTraceGeometry trace : traces)
            score += getTraceLength(trace) + getTraceBendCount(trace) * 35 +
                getTraceDetourRatio(trace) * 60;
        for (String netId : board.getNetIds()) {
            Vector<String> padIds = board.getNet(netId).getPadIds();
            if (padIds.size() < 2)
                continue;
            PcbPadPlacement first = pads.get(padIds.get(0));
            for (int index = 1; index < padIds.size(); index++) {
                PcbPadPlacement other = pads.get(padIds.get(index));
                score += (Math.abs(first.getX() - other.getX()) +
                    Math.abs(first.getY() - other.getY())) * .15;
            }
        }
        return score;
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
                .append(pad.getY()).append('!').append(pad.getEscapeDx()).append(',')
                .append(pad.getEscapeDy()).append(',').append(pad.getEscapeLength()).append(';');
        }
        Vector<String> labelIds = new Vector<String>(silkscreenLabels.keySet());
        Collections.sort(labelIds);
        for (String id : labelIds) {
            PcbSilkscreenLabel label = silkscreenLabels.get(id);
            Rectangle bounds = label.getBounds();
            result.append("L:").append(id).append('@').append(label.getText()).append(':')
                .append(bounds.x).append(',').append(bounds.y).append(',').append(bounds.width)
                .append(',').append(bounds.height).append(';');
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

    String silkscreenGeometryFingerprint() {
        StringBuilder result = new StringBuilder();
        Vector<String> labelIds = new Vector<String>(silkscreenLabels.keySet());
        Collections.sort(labelIds);
        for (String id : labelIds) {
            PcbSilkscreenLabel label = silkscreenLabels.get(id);
            Rectangle bounds = label.getBounds();
            result.append(id).append('@').append(bounds.x).append(',').append(bounds.y).append(',')
                .append(bounds.width).append(',').append(bounds.height).append(';');
        }
        return result.toString();
    }

    int getWidth() { return width; }
    int getHeight() { return height; }
    Rectangle getBoardOutline() { return boardOutline; }
    Rectangle getPartsTray() { return partsTray; }
    PcbPadPlacement getPad(String padId) { return pads.get(padId); }
    PcbComponentPlacement getComponent(String componentId) { return components.get(componentId); }
    PcbSilkscreenLabel getSilkscreenLabel(String labelId) { return silkscreenLabels.get(labelId); }
    Vector<PcbPadPlacement> getPads() { return new Vector<PcbPadPlacement>(pads.values()); }
    Vector<PcbComponentPlacement> getComponents() {
        return new Vector<PcbComponentPlacement>(components.values());
    }
    Vector<PcbSilkscreenLabel> getSilkscreenLabels() {
        return new Vector<PcbSilkscreenLabel>(silkscreenLabels.values());
    }
    Vector<PcbTraceGeometry> getTraces() { return new Vector<PcbTraceGeometry>(traces); }
}
