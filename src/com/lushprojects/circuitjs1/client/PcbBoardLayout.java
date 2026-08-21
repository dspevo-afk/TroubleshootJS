package com.lushprojects.circuitjs1.client;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Collections;
import java.util.Comparator;
import java.util.Vector;

class PcbBoardLayout {
    private static final class TraceSegment {
        final int node;
        final int traceIndex;
        final int segmentIndex;
        final String netId;
        final Rectangle stroke;

        TraceSegment(int node, int traceIndex, int segmentIndex, String netId, Rectangle stroke) {
            this.node = node;
            this.traceIndex = traceIndex;
            this.segmentIndex = segmentIndex;
            this.netId = netId;
            this.stroke = stroke;
        }
    }

    private static final class TraceNodeDisjointSet {
        private final int[] parent;
        private final byte[] rank;

        TraceNodeDisjointSet(int size) {
            if (size <= 0)
                throw new IllegalStateException("PCB connectivity graph has invalid size: " + size);
            parent = new int[size];
            rank = new byte[size];
            for (int index = 0; index < size; index++)
                parent[index] = index;
        }

        int find(int node) {
            if (node < 0 || node >= parent.length)
                throw new IllegalStateException("PCB connectivity node out of range: " + node);
            int current = node;
            while (parent[current] != current)
                current = parent[current];
            while (parent[node] != node) {
                int next = parent[node];
                parent[node] = current;
                node = next;
            }
            return current;
        }

        void union(int first, int second) {
            int firstRoot = find(first);
            int secondRoot = find(second);
            if (firstRoot == secondRoot)
                return;
            if (rank[firstRoot] > rank[secondRoot]) {
                parent[secondRoot] = firstRoot;
            } else if (rank[firstRoot] < rank[secondRoot]) {
                parent[firstRoot] = secondRoot;
            } else {
                parent[secondRoot] = firstRoot;
                rank[firstRoot]++;
            }
        }

        boolean connected(int first, int second) {
            return find(first) == find(second);
        }
    }

    private final int width;
    private final int height;
    private Rectangle boardOutline;
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
        if (!inside(new Rectangle(0, 0, width, height), boardOutline.x, boardOutline.y) ||
                !inside(new Rectangle(0, 0, width, height),
                    checkedAdd(boardOutline.x, boardOutline.width),
                    checkedAdd(boardOutline.y, boardOutline.height)))
            throw new IllegalStateException("PCB board outline leaves workbench canvas");
        if (!inside(new Rectangle(0, 0, width, height), partsTray.x, partsTray.y) ||
                !inside(new Rectangle(0, 0, width, height),
                    checkedAdd(partsTray.x, partsTray.width),
                    checkedAdd(partsTray.y, partsTray.height)))
            throw new IllegalStateException("PCB parts tray leaves workbench canvas");
        if (partsTray.intersects(boardOutline))
            throw new IllegalStateException("PCB parts tray intersects board outline");

        Vector<PcbComponentPlacement> componentList = getComponents();
        for (int first = 0; first < componentList.size(); first++) {
            PcbComponentPlacement firstPlacement = componentList.get(first);
            BoardComponent boardComponent = board.getComponent(firstPlacement.getComponentId());
            validatePackageGeometry(board, boardComponent, firstPlacement);
            validateComponentSurfaces(firstPlacement, boardComponent);
            for (int second = first + 1; second < componentList.size(); second++) {
                if (firstPlacement.getBodyBounds().intersects(
                        componentList.get(second).getBodyBounds()))
                    throw new IllegalStateException("PCB component bodies overlap: " +
                        firstPlacement.getComponentId() + " and " +
                        componentList.get(second).getComponentId());
                if (firstPlacement.getRoutingCourtyard().intersects(
                        componentList.get(second).getRoutingCourtyard()))
                    throw new IllegalStateException("PCB component routing courtyards overlap: " +
                        firstPlacement.getComponentId() + " and " +
                        componentList.get(second).getComponentId());
            }
        }

        Vector<PcbPadPlacement> padList = getPads();
        for (PcbPadPlacement pad : padList) {
            requireInside(pad.getPadBounds(), boardOutline, "pad " + pad.getPadId());
            requireInside(pad.getProbeBounds(), boardOutline,
                "pad probe envelope " + pad.getPadId());
            for (PcbPadPlacement other : padList) {
                if (pad == other)
                    continue;
                if (pad.getPadBounds().intersects(other.getPadBounds()))
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
                if (index > 0 && xPoints[index] != xPoints[index - 1] &&
                        yPoints[index] != yPoints[index - 1])
                    throw new IllegalStateException("PCB trace is not Manhattan routed: " +
                        trace.getNetId() + " segment=" + xPoints[index - 1] + "," +
                        yPoints[index - 1] + " -> " + xPoints[index] + "," + yPoints[index]);
                if (index == 0)
                    continue;
                Rectangle stroke = getTraceSegmentBounds(xPoints[index - 1], yPoints[index - 1],
                    xPoints[index], yPoints[index]);
                if (!rectangleInside(boardOutline, stroke))
                    throw new IllegalStateException("PCB trace leaves board: " +
                        trace.getNetId() + " segment=" + xPoints[index - 1] + "," +
                        yPoints[index - 1] + " -> " + xPoints[index] + "," +
                        yPoints[index]);
            }
            validateTraceCourtyards(board, trace, startPad, endPad);
        }
        validatePhysicalConnectivity(board);
        for (String netId : board.getNetIds()) {
            if (!representedNets.containsKey(netId))
                throw new IllegalStateException("PCB net has no copper trace: " + netId);
        }
        validateTraceClearance();
        validateSilkscreen(board);
        validateRouteQuality();
    }

    /** Places workbench chrome outside the board while preserving both sizes. */
    void positionPartsTrayDisjointFromBoard() {
        int gap = 24;
        Rectangle[] candidates = new Rectangle[] {
            new Rectangle(checkedAdd(checkedAdd(boardOutline.x, boardOutline.width), gap),
                boardOutline.y, partsTray.width, partsTray.height),
            new Rectangle(checkedSubtract(boardOutline.x,
                checkedAdd(partsTray.width, gap)), boardOutline.y,
                partsTray.width, partsTray.height),
            new Rectangle(boardOutline.x,
                checkedAdd(checkedAdd(boardOutline.y, boardOutline.height), gap),
                partsTray.width, partsTray.height),
            new Rectangle(boardOutline.x,
                checkedSubtract(boardOutline.y, checkedAdd(partsTray.height, gap)),
                partsTray.width, partsTray.height)
        };
        Rectangle canvas = new Rectangle(0, 0, width, height);
        for (Rectangle candidate : candidates) {
            if (inside(canvas, candidate.x, candidate.y) &&
                    inside(canvas, checkedAdd(candidate.x, candidate.width),
                        checkedAdd(candidate.y, candidate.height)) &&
                    !candidate.intersects(boardOutline)) {
                partsTray.setBounds(candidate.x, candidate.y, candidate.width, candidate.height);
                return;
            }
        }
        throw new IllegalStateException("Unable to place parts tray outside board outline");
    }

    private void validateTraceCourtyards(TroubleshootBoard board, PcbTraceGeometry trace,
            BoardPad startPad, BoardPad endPad) {
        for (PcbComponentPlacement component : components.values()) {
            Rectangle keepOut = component.getRoutingCourtyard();
            PcbPadPlacement traceStartPlacement = pads.get(trace.getStartPadId());
            PcbPadPlacement traceEndPlacement = pads.get(trace.getEndPadId());
            int[] xPoints = trace.getXPoints();
            int[] yPoints = trace.getYPoints();
            for (int index = 1; index < xPoints.length; index++) {
                if (keepOut.intersects(getTraceSegmentBounds(xPoints[index - 1],
                        yPoints[index - 1], xPoints[index], yPoints[index])) &&
                        !isLegalEndpointEscape(component, trace, startPad, endPad,
                            xPoints[index - 1], yPoints[index - 1], xPoints[index],
                            yPoints[index]))
                throw new IllegalStateException("PCB trace passes through component routing courtyard: " +
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
        PcbPadPlacement startPlacement = pads.get(trace.getStartPadId());
        PcbPadPlacement endPlacement = pads.get(trace.getEndPadId());
        boolean startEscape = component.getComponentId().equals(startPad.getComponentId()) &&
            (touches(x1, y1, startPlacement) || touches(x2, y2, startPlacement) ||
                startPlacement.isInEscapeCorridor(x1, y1) ||
                startPlacement.isInEscapeCorridor(x2, y2));
        boolean endEscape = component.getComponentId().equals(endPad.getComponentId()) &&
            (touches(x1, y1, endPlacement) || touches(x2, y2, endPlacement) ||
                endPlacement.isInEscapeCorridor(x1, y1) ||
                endPlacement.isInEscapeCorridor(x2, y2));
        if (startEscape)
            escapePad = startPlacement;
        else if (endEscape)
            escapePad = endPlacement;
        if (escapePad == null)
            return false;
        Rectangle keepOut = traceCollisionEnvelope(component.getRoutingCourtyard());
        int left = Math.min(x1, x2);
        int right = Math.max(x1, x2);
        int top = Math.min(y1, y2);
        int bottom = Math.max(y1, y2);
        if (y1 == y2) {
            int overlapLeft = Math.max(left, keepOut.x);
            int overlapRight = Math.min(right, checkedAdd(keepOut.x, keepOut.width));
            return overlapLeft <= overlapRight &&
                escapePad.isInEscapeCorridor(overlapLeft, y1) &&
                escapePad.isInEscapeCorridor(overlapRight, y1);
        }
        if (x1 == x2) {
            int overlapTop = Math.max(top, keepOut.y);
            int overlapBottom = Math.min(bottom, checkedAdd(keepOut.y, keepOut.height));
            return overlapTop <= overlapBottom &&
                escapePad.isInEscapeCorridor(x1, overlapTop) &&
                escapePad.isInEscapeCorridor(x1, overlapBottom);
        }
        return false;
    }

    private void validatePackageGeometry(TroubleshootBoard board, BoardComponent component,
            PcbComponentPlacement placement) {
        if (component == null || component.getPhysicalPackage() == null ||
                placement.getPhysicalPackage() == null ||
                component.getPhysicalPackage() != placement.getPhysicalPackage())
            throw new IllegalStateException("PCB component package definition diverged: " +
                (component == null ? "null" : component.getId()));
        PhysicalPackageGeometry geometry = placement.getPhysicalGeometry();
        if (geometry == null || !component.getPhysicalPackage().acceptsGeometry(geometry))
            throw new IllegalStateException("PCB component does not retain package geometry: " +
                component.getId());
        Vector<String> padIds = component.getPadIds();
        if (padIds.size() != geometry.getTerminals().size())
            throw new IllegalStateException("PCB component terminal count diverged from package: " +
                component.getId());
        PhysicalPackageGeometry.Placement placed = geometry.placedAt(placement.getX(),
            placement.getY());
        if (!placed.getBodyBounds().equals(placement.getBodyBounds()) ||
                !placed.getBodyKeepOut().equals(placement.getKeepOut()) ||
                !placed.getRoutingCourtyard().equals(placement.getRoutingCourtyard()) ||
                !placed.getSelectionEnvelope().equals(placement.getSelectionEnvelope()) ||
                !placed.getDragEnvelope().equals(placement.getDragEnvelope()) ||
                placement.getWidth() != geometry.getWidth() ||
                placement.getHeight() != geometry.getHeight())
            throw new IllegalStateException("PCB component placement diverged from package geometry: " +
                component.getId());
        for (int index = 0; index < padIds.size(); index++) {
            PcbPadPlacement pad = pads.get(padIds.get(index));
            BoardPad boardPad = board.getPad(padIds.get(index));
            PhysicalPackageGeometry.Terminal terminal = geometry.getTerminal(index);
            if (terminal == null || boardPad == null ||
                    !terminal.getTerminalId().equals(boardPad.getTerminalId()) || pad == null ||
                    pad.getX() != placed.getPadPoint(index).x ||
                    pad.getY() != placed.getPadPoint(index).y ||
                    pad.getEscapeDx() != terminal.getEscapeDx() ||
                    pad.getEscapeDy() != terminal.getEscapeDy() ||
                    pad.getEscapeLength() != terminal.getEscapeLength() ||
                    !pad.getPadBounds().equals(placed.getPadBounds(index)) ||
                !pad.getProbeBounds().equals(placed.getProbeBounds(index)))
                throw new IllegalStateException("PCB pad diverged from package geometry: " +
                padIds.get(index));
        }
    }

    private void validateComponentSurfaces(PcbComponentPlacement placement, BoardComponent component) {
        requireInside(placement.getBodyBounds(), boardOutline,
            "component " + placement.getComponentId());
        requireInside(placement.getKeepOut(), boardOutline,
            "component keep-out " + placement.getComponentId());
        requireInside(placement.getRoutingCourtyard(), boardOutline,
            "component routing courtyard " + placement.getComponentId());
        requireInside(placement.getSelectionEnvelope(), boardOutline,
            "component selection envelope " + placement.getComponentId());
        requireInside(placement.getDragEnvelope(), boardOutline,
            "component drag envelope " + placement.getComponentId());
        for (int terminal = 0; terminal < component.getPadIds().size(); terminal++) {
            requireInside(placement.getPadBounds(terminal), boardOutline,
                "component pad " + placement.getComponentId() + "/" + terminal);
            requireInside(placement.getProbeBounds(terminal), boardOutline,
                "component board-pad probe " + placement.getComponentId() + "/" + terminal);
            requireInside(placement.getLeadBounds(terminal), boardOutline,
                "component lead " + placement.getComponentId() + "/" + terminal);
            requireInside(placement.getLeadBounds(terminal, true), boardOutline,
                "component lifted lead " + placement.getComponentId() + "/" + terminal);
            requireInside(placement.getComponentLeadProbeBounds(terminal), boardOutline,
                "component connected lead probe " + placement.getComponentId() + "/" + terminal);
            requireInside(placement.getComponentLeadProbeBounds(terminal, true), boardOutline,
                "component lifted lead probe " + placement.getComponentId() + "/" + terminal);
        }
    }

    private static Rectangle getTraceSegmentBounds(int firstX, int firstY, int secondX, int secondY) {
        if (firstX == secondX && firstY == secondY)
            throw new IllegalStateException("PCB trace segment has zero length: " +
                firstX + "," + firstY);
        int half = PcbTraceRules.TRACE_WIDTH / 2;
        if (firstX == secondX) {
            int x = checkedSubtract(firstX, half);
            int width = checkedInt(PcbTraceRules.TRACE_WIDTH);
            int top = Math.min(firstY, secondY);
            long height = checkedSubtract(Math.max(firstY, secondY), top);
            height = checkedAdd(height, PcbTraceRules.TRACE_WIDTH);
            return new Rectangle(x, checkedSubtract(top, half), width, checkedInt(height));
        }
        if (firstY == secondY) {
            int y = checkedSubtract(firstY, half);
            int height = checkedInt(PcbTraceRules.TRACE_WIDTH);
            int left = Math.min(firstX, secondX);
            long width = checkedSubtract(Math.max(firstX, secondX), left);
            width = checkedAdd(width, PcbTraceRules.TRACE_WIDTH);
            return new Rectangle(checkedSubtract(left, half), y, checkedInt(width), height);
        }
        throw new IllegalStateException("PCB trace segment is not Manhattan routed: " +
            firstX + "," + firstY + " -> " + secondX + "," + secondY);
    }

    private static Rectangle traceCollisionEnvelope(Rectangle rectangle) {
        int half = PcbTraceRules.TRACE_WIDTH / 2;
        int extension = PcbTraceRules.TRACE_WIDTH - half;
        return new Rectangle(checkedSubtract(rectangle.x, extension),
            checkedSubtract(rectangle.y, extension),
            checkedAdd(rectangle.width, PcbTraceRules.TRACE_WIDTH),
            checkedAdd(rectangle.height, PcbTraceRules.TRACE_WIDTH));
    }

    private static boolean rectangleInside(Rectangle outer, Rectangle inner) {
        return inside(outer, inner.x, inner.y) &&
            inside(outer, checkedAdd(inner.x, inner.width), inner.y) &&
            inside(outer, inner.x, checkedAdd(inner.y, inner.height)) &&
            inside(outer, checkedAdd(inner.x, inner.width), checkedAdd(inner.y, inner.height));
    }

    private static boolean tracesPhysicallyTouch(Rectangle first, Rectangle second) {
        return rectanglesTouch(first, second);
    }

    private static boolean rectanglesTouch(Rectangle first, Rectangle second) {
        long firstRight = checkedAdd(first.x, first.width);
        long firstBottom = checkedAdd(first.y, first.height);
        long secondRight = checkedAdd(second.x, second.width);
        long secondBottom = checkedAdd(second.y, second.height);
        return firstRight >= second.x && secondRight >= first.x &&
            firstBottom >= second.y && secondBottom >= first.y;
    }

    private void validatePhysicalConnectivity(TroubleshootBoard board) {
        Vector<PcbTraceGeometry> traceList = getTraces();
        HashMap<String, Integer> padNodes = new HashMap<String, Integer>();
        for (String padId : pads.keySet())
            padNodes.put(padId, Integer.valueOf(padNodes.size()));

        int segmentNodeOffset = padNodes.size();
        int[] segmentBase = new int[traceList.size()];
        Vector<TraceSegment> segmentLookup = new Vector<TraceSegment>();
        for (int traceIndex = 0; traceIndex < traceList.size(); traceIndex++) {
            PcbTraceGeometry trace = traceList.get(traceIndex);
            int[] xPoints = trace.getXPoints();
            int[] yPoints = trace.getYPoints();
            if (xPoints.length < 2 || yPoints.length != xPoints.length)
                throw new IllegalStateException("PCB trace has invalid segment list: " +
                    trace.getNetId());
            segmentBase[traceIndex] = segmentLookup.size();
            for (int segmentIndex = 0; segmentIndex < xPoints.length - 1; segmentIndex++) {
                Rectangle segment = getTraceSegmentBounds(
                    xPoints[segmentIndex], yPoints[segmentIndex], xPoints[segmentIndex + 1],
                    yPoints[segmentIndex + 1]);
                segmentLookup.add(new TraceSegment(segmentNodeId(traceIndex, segmentIndex,
                    segmentBase, segmentNodeOffset), traceIndex, segmentIndex,
                    trace.getNetId(), segment));
            }
        }

        TraceNodeDisjointSet connectivity = new TraceNodeDisjointSet(
            checkedAdd(segmentNodeOffset, segmentLookup.size()));

        for (int traceIndex = 0; traceIndex < traceList.size(); traceIndex++) {
            PcbTraceGeometry trace = traceList.get(traceIndex);
            int[] xPoints = trace.getXPoints();
            int[] yPoints = trace.getYPoints();
            int segmentCount = xPoints.length - 1;
            Integer startNode = padNodes.get(trace.getStartPadId());
            Integer endNode = padNodes.get(trace.getEndPadId());
            if (startNode == null || endNode == null)
                throw new IllegalStateException("PCB trace endpoints do not reference board pads: " +
                    trace.getNetId());
            TraceSegment firstSegment = segmentLookup.get(segmentBase[traceIndex]);
            TraceSegment lastSegment = segmentLookup.get(checkedAdd(segmentBase[traceIndex],
                segmentCount - 1));
            connectivity.union(startNode.intValue(), firstSegment.node);
            connectivity.union(endNode.intValue(), lastSegment.node);
            for (int segmentIndex = 1; segmentIndex < segmentCount; segmentIndex++) {
                TraceSegment previous = segmentLookup.get(checkedAdd(segmentBase[traceIndex],
                    segmentIndex - 1));
                TraceSegment current = segmentLookup.get(checkedAdd(segmentBase[traceIndex],
                    segmentIndex));
                connectivity.union(previous.node, current.node);
            }
            String netId = trace.getNetId();
            for (int segmentIndex = 0; segmentIndex < segmentCount; segmentIndex++) {
                TraceSegment segment = segmentLookup.get(checkedAdd(segmentBase[traceIndex],
                    segmentIndex));
                Rectangle segmentBounds = segment.stroke;
                for (String padId : pads.keySet()) {
                    PcbPadPlacement pad = pads.get(padId);
                    BoardPad boardPad = board.getPad(padId);
                    if (!rectanglesTouch(segmentBounds, pad.getPadBounds()))
                        continue;
                    if (boardPad == null)
                        throw new IllegalStateException(
                            "PCB layout references unknown pad during connectivity check: " +
                            padId);
                    Integer padNode = padNodes.get(padId);
                    if (padNode == null)
                        throw new IllegalStateException(
                            "PCB pad is missing from connectivity graph: " + padId);
                    if (netId.equals(boardPad.getNetId()))
                        connectivity.union(segment.node, padNode.intValue());
                    else
                        throw new IllegalStateException(
                            "Unrelated PCB trace and pad share copper: " + netId + " / " +
                            boardPad.getNetId() + " segment=" +
                            xPoints[segmentIndex] + "," + yPoints[segmentIndex] + " -> " +
                            xPoints[segmentIndex + 1] + "," + yPoints[segmentIndex + 1] +
                            " pad=" + padId);
                }
            }
        }

        for (int first = 0; first < segmentLookup.size(); first++) {
            TraceSegment firstSegment = segmentLookup.get(first);
            PcbTraceGeometry firstTrace = traceList.get(firstSegment.traceIndex);
            for (int second = first + 1; second < segmentLookup.size(); second++) {
                TraceSegment secondSegment = segmentLookup.get(second);
                if (!rectanglesTouch(firstSegment.stroke, secondSegment.stroke))
                    continue;
                PcbTraceGeometry secondTrace = traceList.get(secondSegment.traceIndex);
                if (!firstTrace.getNetId().equals(secondTrace.getNetId()))
                    throw new IllegalStateException(
                        "Unrelated PCB traces share copper: " + firstTrace.getNetId() + " and " +
                        secondTrace.getNetId() + " " +
                        "segmentDescription=" + crossingSegmentDescription(firstTrace, secondTrace));
                connectivity.union(firstSegment.node, secondSegment.node);
            }
        }

        Vector<String> padIds = new Vector<String>(pads.keySet());
        for (int first = 0; first < padIds.size(); first++) {
            String firstPadId = padIds.get(first);
            PcbPadPlacement firstPad = pads.get(firstPadId);
            BoardPad firstBoardPad = board.getPad(firstPadId);
            if (firstBoardPad == null)
                throw new IllegalStateException(
                    "PCB layout references unknown pad during pad contact check: " +
                    firstPadId);
            for (int second = first + 1; second < padIds.size(); second++) {
                String secondPadId = padIds.get(second);
                PcbPadPlacement secondPad = pads.get(secondPadId);
                if (!rectanglesTouch(firstPad.getPadBounds(), secondPad.getPadBounds()))
                    continue;
                BoardPad secondBoardPad = board.getPad(secondPadId);
                if (secondBoardPad == null)
                    throw new IllegalStateException(
                        "PCB layout references unknown pad during pad contact check: " +
                        secondPadId);
                Integer firstNode = padNodes.get(firstPadId);
                Integer secondNode = padNodes.get(secondPadId);
                if (!firstBoardPad.getNetId().equals(secondBoardPad.getNetId()))
                    throw new IllegalStateException("Unrelated PCB pads share copper: " +
                        firstBoardPad.getNetId() + " / " + secondBoardPad.getNetId() +
                        " pads=" + firstPadId + " / " + secondPadId);
                connectivity.union(firstNode.intValue(), secondNode.intValue());
            }
        }

        for (String componentId : board.getComponentIds()) {
            BoardComponent boardComponent = board.getComponent(componentId);
            PcbComponentPlacement placement = this.components.get(componentId);
            if (placement == null || boardComponent == null)
                throw new IllegalStateException("PCB component mismatch during connectivity check: " +
                    componentId);
            validateInternalConnectivityForComponent(board, boardComponent, placement,
                connectivity, padNodes);
        }
        for (String netId : board.getNetIds()) {
            Vector<String> netPadIds = board.getNet(netId).getPadIds();
            if (netPadIds.size() <= 1)
                continue;
            Integer representative = padNodes.get(netPadIds.get(0));
            if (representative == null)
                throw new IllegalStateException("PCB net references unknown representative pad: " +
                    netId + " pad=" + netPadIds.get(0));
            int root = connectivity.find(representative.intValue());
            for (int index = 1; index < netPadIds.size(); index++) {
                Integer node = padNodes.get(netPadIds.get(index));
                if (node == null || !connectivity.connected(root, node.intValue()))
                    throw new IllegalStateException("PCB net is electrically disconnected: " +
                        netId + " pad=" + netPadIds.get(0) + " / " + netPadIds.get(index));
            }
        }
    }

    private int segmentNodeId(int traceIndex, int segmentIndex, int[] segmentBase,
            int segmentNodeOffset) {
        if (traceIndex < 0 || traceIndex >= segmentBase.length || segmentIndex < 0)
            throw new IllegalStateException("PCB trace segment index is invalid: " +
                traceIndex + "/" + segmentIndex);
        int result = checkedAdd(segmentNodeOffset,
            checkedAdd(segmentBase[traceIndex], segmentIndex));
        if (result < segmentNodeOffset)
            throw new IllegalStateException("PCB trace segment index is out of bounds: " +
                traceIndex + "/" + segmentIndex);
        return result;
    }

    private void validateInternalConnectivityForComponent(TroubleshootBoard board,
            BoardComponent component, PcbComponentPlacement placement,
            TraceNodeDisjointSet components, HashMap<String, Integer> padNodes) {
        PhysicalPackage componentPackage = placement.getPhysicalPackage();
        if (componentPackage == null)
            throw new IllegalStateException("PCB component missing physical package: " +
                placement.getComponentId());
        Vector<String> terminalIds = componentPackage.getTerminalIds();
        Vector<String> componentPadIds = component.getPadIds();
        if (terminalIds.size() != componentPadIds.size())
            throw new IllegalStateException("PCB component terminal count mismatch: " +
                placement.getComponentId());
        HashMap<String, String> terminalToPad = new HashMap<String, String>();
        for (int terminalIndex = 0; terminalIndex < terminalIds.size(); terminalIndex++) {
            String terminalId = terminalIds.get(terminalIndex);
            String padId = componentPadIds.get(terminalIndex);
            BoardPad pad = board.getPad(padId);
            if (pad == null)
                throw new IllegalStateException("PCB component references unknown pad: " +
                    placement.getComponentId() + " / " + padId);
            if (!terminalId.equals(pad.getTerminalId()))
                throw new IllegalStateException(
                    "PCB component terminal order diverged from package geometry: " +
                    placement.getComponentId() + " terminal=" + terminalId + " pad=" + padId);
            terminalToPad.put(terminalId, padId);
        }
        HashSet<String> visited = new HashSet<String>();
        Vector<String> queue = new Vector<String>();
        for (String terminalId : terminalIds) {
            if (visited.contains(terminalId))
                continue;
            String rootPadId = terminalToPad.get(terminalId);
            if (rootPadId == null)
                throw new IllegalStateException(
                    "PCB component has unplaced terminal: " + placement.getComponentId() +
                        " / " + terminalId);
            BoardPad rootPad = board.getPad(rootPadId);
            if (rootPad == null)
                throw new IllegalStateException("PCB component terminal has unknown pad: " +
                    placement.getComponentId() + " / " + terminalId);
            visited.add(terminalId);
            queue.clear();
            queue.add(terminalId);
            String groupNet = rootPad.getNetId();
            Vector<String> groupTerminals = new Vector<String>();
            for (int headIndex = 0; headIndex < queue.size(); headIndex++) {
                String current = queue.get(headIndex);
                String currentPadId = terminalToPad.get(current);
                if (currentPadId == null)
                    throw new IllegalStateException(
                        "PCB component terminal has unplaced terminal: " +
                        placement.getComponentId() + " / " + current);
                BoardPad pad = board.getPad(currentPadId);
                if (pad == null)
                    throw new IllegalStateException("PCB component terminal has unknown pad: " +
                        placement.getComponentId() + " / " + current);
                if (!groupNet.equals(pad.getNetId()))
                    throw new IllegalStateException(
                        "PCB component internal connectivity spans nets: " +
                        placement.getComponentId() + " terminal=" + current + " net=" +
                        pad.getNetId());
                groupTerminals.add(current);
                for (int terminalIndex = 0; terminalIndex < terminalIds.size(); terminalIndex++) {
                    String other = terminalIds.get(terminalIndex);
                    if (visited.contains(other) || !componentPackage.isInternallyConnected(current,
                            other))
                        continue;
                    visited.add(other);
                    queue.add(other);
                }
            }
            for (int first = 0; first < groupTerminals.size(); first++) {
                String firstTerminal = groupTerminals.get(first);
                String firstPadId = terminalToPad.get(firstTerminal);
                Integer firstNode = padNodes.get(firstPadId);
                if (firstNode == null)
                    throw new IllegalStateException("PCB component internal pad missing from layout: " +
                        placement.getComponentId() + " / " + firstPadId);
                for (int second = first + 1; second < groupTerminals.size(); second++) {
                    String secondTerminal = groupTerminals.get(second);
                    String secondPadId = terminalToPad.get(secondTerminal);
                    Integer secondNode = padNodes.get(secondPadId);
                    if (secondNode == null)
                        throw new IllegalStateException("PCB component internal pad missing from " +
                            "layout: " + placement.getComponentId() + " / " + secondPadId);
                    components.union(firstNode.intValue(), secondNode.intValue());
                }
            }
        }
    }

    private boolean touches(int x, int y, PcbPadPlacement pad) {
        return pad != null && x == pad.getX() && y == pad.getY();
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
                if (bounds.intersects(component.getBodyBounds()))
                    throw new IllegalStateException("Silkscreen label overlaps component: " +
                        label.getId() + " / " + component.getComponentId());
            }
            for (PcbPadPlacement pad : pads.values()) {
                if (bounds.intersects(pad.getPadBounds()))
                    throw new IllegalStateException("Silkscreen label overlaps pad: " +
                        label.getId() + " / " + pad.getPadId());
            }
            for (PcbTraceGeometry trace : traces) {
                int[] xPoints = trace.getXPoints();
                int[] yPoints = trace.getYPoints();
                for (int index = 1; index < xPoints.length; index++) {
                    if (bounds.intersects(getTraceSegmentBounds(xPoints[index - 1],
                            yPoints[index - 1], xPoints[index], yPoints[index])))
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
            for (int index = 1; index < xPoints.length; index++) {
                if (xPoints[index] == xPoints[index - 1] &&
                        yPoints[index] == yPoints[index - 1])
                    throw new IllegalStateException("PCB trace has consecutive duplicate points: " +
                        trace.getNetId());
            }
            for (int first = 1; first < xPoints.length; first++) {
                for (int second = first + 1; second < xPoints.length; second++) {
                    if (!segmentsIntersect(xPoints[first - 1], yPoints[first - 1],
                            xPoints[first], yPoints[first], xPoints[second - 1],
                            yPoints[second - 1], xPoints[second], yPoints[second]))
                        continue;
                    if (second == first + 1 && adjacentSegmentsShareOnlyEndpoint(
                            xPoints[first - 1], yPoints[first - 1], xPoints[first],
                            yPoints[first], xPoints[second - 1], yPoints[second - 1],
                            xPoints[second], yPoints[second]))
                        continue;
                    if (collinearOverlap(xPoints[first - 1], yPoints[first - 1],
                            xPoints[first], yPoints[first], xPoints[second - 1],
                            yPoints[second - 1], xPoints[second], yPoints[second]) > 0)
                        throw new IllegalStateException("PCB trace has repeated or overlapping segments: " +
                            trace.getNetId() + " pads=" + trace.getStartPadId() + "->" +
                            trace.getEndPadId() + " points=" + pointsDescription(xPoints, yPoints));
                    throw new IllegalStateException("PCB trace self-intersects: " +
                        trace.getNetId());
                }
            }
        }
    }

    private static boolean adjacentSegmentsShareOnlyEndpoint(int ax1, int ay1, int ax2,
            int ay2, int bx1, int by1, int bx2, int by2) {
        if (ax2 != bx1 || ay2 != by1)
            return false;
        if (ay1 == ay2 && by1 == by2)
            return collinearOverlap(ax1, ay1, ax2, ay2, bx1, by1, bx2, by2) == 0;
        if (ax1 == ax2 && bx1 == bx2)
            return collinearOverlap(ax1, ay1, ax2, ay2, bx1, by1, bx2, by2) == 0;
        return true;
    }

    private static String pointsDescription(int[] x, int[] y) {
        String result = "";
        for (int index = 0; index < x.length; index++) {
            if (index > 0) result += ";";
            result += x[index] + "," + y[index];
        }
        return result;
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
                    Math.abs(first.getY() - other.getY())) * .40;
            }
        }
        Rectangle occupied = getOccupiedContentBounds();
        long boardArea = (long) boardOutline.width * boardOutline.height;
        long occupiedArea = (long) occupied.width * occupied.height;
        score += boardArea * .012;
        score += Math.max(0, boardArea - occupiedArea) * .025;
        score += getRoutingCourtyardArea() / (double) Math.max(1, boardArea) * 120;
        score += getLargestEdgeMargin() * .25;
        Vector<PcbComponentPlacement> componentList = getComponents();
        for (int first = 0; first < componentList.size(); first++) {
            for (int second = first + 1; second < componentList.size(); second++) {
                int gap = rectangleGap(componentList.get(first).getRoutingCourtyard(),
                    componentList.get(second).getRoutingCourtyard());
                score += Math.max(0, gap - 55) * .08;
            }
        }
        score -= getSameNetReuseLength() * .35;
        return score;
    }

    /**
     * Translates the generated geometry into a compact, stable canvas location.
     * The parts tray is deliberately excluded: it is workbench chrome, not PCB
     * content.  This is a simulator readability rule, not a manufacturing rule.
     */
    void compactToContent(int boardX, int boardY, int edgeMargin) {
        Rectangle content = getOccupiedContentBounds();
        int dx = checkedSubtract(checkedAdd(boardX, edgeMargin), content.x);
        int dy = checkedSubtract(checkedAdd(boardY, edgeMargin), content.y);
        HashMap<String, PcbComponentPlacement> translatedComponents =
            new HashMap<String, PcbComponentPlacement>();
        for (String componentId : components.keySet()) {
            PcbComponentPlacement placement = components.get(componentId);
            translatedComponents.put(componentId, placement.translatedBy(dx, dy));
        }
        components.clear();
        components.putAll(translatedComponents);

        HashMap<String, PcbPadPlacement> translatedPads =
            new HashMap<String, PcbPadPlacement>();
        for (String padId : pads.keySet()) {
            PcbPadPlacement pad = pads.get(padId);
            translatedPads.put(padId, new PcbPadPlacement(padId, checkedAdd(pad.getX(), dx),
                checkedAdd(pad.getY(), dy), pad.getEscapeDx(), pad.getEscapeDy(),
                pad.getEscapeLength(), translate(pad.getPadBounds(), dx, dy),
                translate(pad.getProbeBounds(), dx, dy)));
        }
        pads.clear();
        pads.putAll(translatedPads);

        Vector<PcbTraceGeometry> translatedTraces = new Vector<PcbTraceGeometry>();
        for (PcbTraceGeometry trace : traces) {
            int[] xPoints = trace.getXPoints();
            int[] yPoints = trace.getYPoints();
            int[] translatedX = new int[xPoints.length];
            int[] translatedY = new int[yPoints.length];
            for (int index = 0; index < xPoints.length; index++) {
                translatedX[index] = checkedAdd(xPoints[index], dx);
                translatedY[index] = checkedAdd(yPoints[index], dy);
            }
            translatedTraces.add(new PcbTraceGeometry(trace.getNetId(), trace.getStartPadId(),
                trace.getEndPadId(), translatedX, translatedY));
        }
        traces.clear();
        traces.addAll(translatedTraces);

        HashMap<String, PcbSilkscreenLabel> translatedLabels =
            new HashMap<String, PcbSilkscreenLabel>();
        for (String labelId : silkscreenLabels.keySet()) {
            PcbSilkscreenLabel label = silkscreenLabels.get(labelId);
            translatedLabels.put(labelId, new PcbSilkscreenLabel(labelId, label.getText(),
                translate(label.getBounds(), dx, dy), label.getFontSize(), label.isBold(),
                label.getTargetPadId()));
        }
        silkscreenLabels.clear();
        silkscreenLabels.putAll(translatedLabels);
        int compactedWidth = checkedAdd(content.width, checkedMultiply(edgeMargin, 2));
        int compactedHeight = checkedAdd(content.height, checkedMultiply(edgeMargin, 2));
        boardOutline = new Rectangle(boardX, boardY, compactedWidth, compactedHeight);
    }

    Rectangle getOccupiedContentBounds() {
        Rectangle result = null;
        for (PcbComponentPlacement component : components.values()) {
            result = union(result, component.getBodyBounds());
            result = union(result, component.getKeepOut());
            result = union(result, component.getRoutingCourtyard());
            result = union(result, component.getSelectionEnvelope());
            result = union(result, component.getDragEnvelope());
            PhysicalPackageGeometry geometry = component.getPhysicalGeometry();
            if (geometry != null) {
                for (int index = 0; index < geometry.getTerminals().size(); index++) {
                    result = union(result, component.getPadBounds(index));
                    result = union(result, component.getProbeBounds(index));
                    result = union(result, component.getLeadBounds(index));
                    result = union(result, component.getLeadBounds(index, true));
                    result = union(result, component.getComponentLeadProbeBounds(index));
                    result = union(result, component.getComponentLeadProbeBounds(index, true));
                }
            }
        }
        for (PcbPadPlacement pad : pads.values())
            result = union(result, pad.getPadBounds());
        for (PcbPadPlacement pad : pads.values())
            result = union(result, pad.getProbeBounds());
        for (PcbTraceGeometry trace : traces) {
            int[] xPoints = trace.getXPoints();
            int[] yPoints = trace.getYPoints();
            for (int index = 1; index < xPoints.length; index++)
                result = union(result,
                    getTraceSegmentBounds(xPoints[index - 1], yPoints[index - 1],
                        xPoints[index], yPoints[index]));
        }
        for (PcbSilkscreenLabel label : silkscreenLabels.values())
            result = union(result, label.getBounds());
        if (result == null)
            throw new IllegalStateException("PCB layout has no occupied geometry");
        return result;
    }

    double getBoardUtilization() {
        Rectangle content = getOccupiedContentBounds();
        return (content.width * (double) content.height) /
            Math.max(1, boardOutline.width * (double) boardOutline.height);
    }

    double getCompactnessMetric() {
        return Math.max(0, Math.min(1, getBoardUtilization()));
    }

    int getLargestEdgeMargin() {
        Rectangle content = getOccupiedContentBounds();
        int boardRight = checkedAdd(boardOutline.x, boardOutline.width);
        int boardBottom = checkedAdd(boardOutline.y, boardOutline.height);
        int contentRight = checkedAdd(content.x, content.width);
        int contentBottom = checkedAdd(content.y, content.height);
        return Math.max(Math.max(checkedSubtract(content.x, boardOutline.x),
                checkedSubtract(content.y, boardOutline.y)),
            Math.max(checkedSubtract(boardRight, contentRight),
                checkedSubtract(boardBottom, contentBottom)));
    }

    long getRoutingCourtyardArea() {
        long area = 0;
        for (PcbComponentPlacement component : components.values()) {
            Rectangle courtyard = component.getRoutingCourtyard();
            area += (long) courtyard.width * courtyard.height;
        }
        return area;
    }

    int getSameNetReuseLength() {
        int reused = 0;
        for (int first = 0; first < traces.size(); first++) {
            for (int second = first + 1; second < traces.size(); second++) {
                if (traces.get(first).getNetId().equals(traces.get(second).getNetId()))
                    reused += sharedCenterlineLength(traces.get(first), traces.get(second));
            }
        }
        return reused;
    }

    private static int sharedCenterlineLength(PcbTraceGeometry first, PcbTraceGeometry second) {
        int shared = 0;
        int[] firstX = first.getXPoints();
        int[] firstY = first.getYPoints();
        int[] secondX = second.getXPoints();
        int[] secondY = second.getYPoints();
        for (int firstIndex = 1; firstIndex < firstX.length; firstIndex++) {
            for (int secondIndex = 1; secondIndex < secondX.length; secondIndex++)
                shared += collinearOverlap(firstX[firstIndex - 1], firstY[firstIndex - 1],
                    firstX[firstIndex], firstY[firstIndex], secondX[secondIndex - 1],
                    secondY[secondIndex - 1], secondX[secondIndex], secondY[secondIndex]);
        }
        return shared;
    }

    private static int collinearOverlap(int ax1, int ay1, int ax2, int ay2,
            int bx1, int by1, int bx2, int by2) {
        if (ay1 == ay2 && by1 == by2 && ay1 == by1)
            return Math.max(0, Math.min(Math.max(ax1, ax2), Math.max(bx1, bx2)) -
                Math.max(Math.min(ax1, ax2), Math.min(bx1, bx2)));
        if (ax1 == ax2 && bx1 == bx2 && ax1 == bx1)
            return Math.max(0, Math.min(Math.max(ay1, ay2), Math.max(by1, by2)) -
                Math.max(Math.min(ay1, ay2), Math.min(by1, by2)));
        return 0;
    }

    private static int rectangleGap(Rectangle first, Rectangle second) {
        long firstRight = checkedAdd(first.x, first.width);
        long secondRight = checkedAdd(second.x, second.width);
        long firstBottom = checkedAdd(first.y, first.height);
        long secondBottom = checkedAdd(second.y, second.height);
        int dx = firstRight < second.x ? checkedSubtract(second.x, firstRight) :
            secondRight < first.x ? checkedSubtract(first.x, secondRight) : 0;
        int dy = firstBottom < second.y ? checkedSubtract(second.y, firstBottom) :
            secondBottom < first.y ? checkedSubtract(first.y, secondBottom) : 0;
        return Math.max(dx, dy);
    }

    private static Rectangle union(Rectangle first, Rectangle second) {
        if (first == null)
            return new Rectangle(second);
        int left = Math.min(first.x, second.x);
        int top = Math.min(first.y, second.y);
        long firstRight = (long) first.x + first.width;
        long secondRight = (long) second.x + second.width;
        long firstBottom = (long) first.y + first.height;
        long secondBottom = (long) second.y + second.height;
        long right = Math.max(firstRight, secondRight);
        long bottom = Math.max(firstBottom, secondBottom);
        return new Rectangle(left, top, checkedInt(right - left), checkedInt(bottom - top));
    }

    private static Rectangle translate(Rectangle rectangle, int dx, int dy) {
        return new Rectangle(checkedAdd(rectangle.x, dx), checkedAdd(rectangle.y, dy),
            rectangle.width,
            rectangle.height);
    }

    private static void requireInside(Rectangle rectangle, Rectangle outer, String description) {
        if (!rectangleInside(outer, rectangle))
            throw new IllegalStateException("PCB " + description + " leaves board outline");
    }

    private static boolean inside(Rectangle rectangle, int x, int y) {
        long right = checkedAdd(rectangle.x, rectangle.width);
        long bottom = checkedAdd(rectangle.y, rectangle.height);
        return x >= rectangle.x && y >= rectangle.y &&
            x <= right && y <= bottom;
    }

    private static int checkedAdd(int first, int second) {
        return checkedInt((long) first + second);
    }

    private static int checkedAdd(long first, int second) {
        return checkedInt(first + second);
    }

    private static int checkedSubtract(long first, int second) {
        return checkedInt(first - second);
    }

    private static int checkedSubtract(int first, long second) {
        return checkedInt((long) first - second);
    }

    private static int checkedSubtract(int first, int second) {
        return checkedInt((long) first - second);
    }

    private static int checkedMultiply(int first, int second) {
        return checkedInt((long) first * second);
    }

    private static int checkedInt(long value) {
        if (value < Integer.MIN_VALUE || value > Integer.MAX_VALUE)
            throw new IllegalStateException("PCB layout coordinate overflow: " + value);
        return (int) value;
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

    private static String crossingSegmentDescription(PcbTraceGeometry first,
            PcbTraceGeometry second) {
        int[] firstX = first.getXPoints();
        int[] firstY = first.getYPoints();
        int[] secondX = second.getXPoints();
        int[] secondY = second.getYPoints();
        for (int firstIndex = 1; firstIndex < firstX.length; firstIndex++) {
            for (int secondIndex = 1; secondIndex < secondX.length; secondIndex++) {
                if (segmentsIntersect(firstX[firstIndex - 1], firstY[firstIndex - 1],
                        firstX[firstIndex], firstY[firstIndex], secondX[secondIndex - 1],
                        secondY[secondIndex - 1], secondX[secondIndex], secondY[secondIndex]))
                    return "firstSegment=" + firstX[firstIndex - 1] + "," +
                        firstY[firstIndex - 1] + " -> " + firstX[firstIndex] + "," +
                        firstY[firstIndex] + " secondSegment=" + secondX[secondIndex - 1] +
                        "," + secondY[secondIndex - 1] + " -> " + secondX[secondIndex] +
                        "," + secondY[secondIndex];
            }
        }
        return "segments=unknown";
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
        if (aHorizontal && bHorizontal)
            return ay1 == by1;
        if (!aHorizontal && !bHorizontal)
            return ax1 == bx1;
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
            result.append("C:").append(placement.geometryFingerprint()).append(';');
        }
        Vector<String> padIds = new Vector<String>(pads.keySet());
        Collections.sort(padIds);
        for (String id : padIds) {
            PcbPadPlacement pad = pads.get(id);
            result.append("P:").append(pad.geometryFingerprint()).append(';');
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
            result.append(placement.geometryFingerprint()).append(';');
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
