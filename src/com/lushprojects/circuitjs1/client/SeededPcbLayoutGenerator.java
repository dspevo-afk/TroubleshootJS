package com.lushprojects.circuitjs1.client;

import java.util.Collections;
import java.util.Comparator;
import java.util.PriorityQueue;
import java.util.Random;
import java.util.Vector;

/**
 * Generates only physical PCB geometry.  Stable board components, pads, and
 * nets are supplied by the logical board and are never invented here.
 */
class SeededPcbLayoutGenerator {
    private static final int CANVAS_WIDTH = 1040;
    private static final int CANVAS_HEIGHT = 520;
    private static final int GRID = 10;
    private static final int MAX_ATTEMPTS = 80;
    private static final int TARGET_VIABLE_CANDIDATES = 5;
    private static final Rectangle WORKING_OUTLINE = new Rectangle(70, 50, 720, 400);
    private static final int FINAL_BOARD_X = 40;
    private static final int FINAL_BOARD_Y = 40;
    private static final int FINAL_EDGE_MARGIN = 26;

    PcbBoardLayout generate(TroubleshootBoard board, long seed) {
        if (board == null)
            throw new IllegalArgumentException("Missing logical board for PCB generation");
        RuntimeException lastFailure = null;
        PcbBoardLayout bestLayout = null;
        double bestScore = Double.POSITIVE_INFINITY;
        int viableCandidates = 0;
        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            try {
                int variationMode = (int) ((seed % 4 + 4) % 4);
                PcbBoardLayout candidate = generateAttempt(board, attemptRandom(seed, attempt),
                    variationMode);
                double score = candidate.getRouteQualityScore(board);
                if (bestLayout == null || score < bestScore) {
                    bestLayout = candidate;
                    bestScore = score;
                }
                viableCandidates++;
                if (viableCandidates >= TARGET_VIABLE_CANDIDATES)
                    break;
            } catch (RuntimeException failure) {
                lastFailure = failure;
            }
        }
        if (bestLayout != null)
            return bestLayout;
        String message = "Unable to generate a routed PCB after " + MAX_ATTEMPTS +
            " deterministic attempts for seed " + seed + ": " +
            (lastFailure == null ? "unknown failure" : lastFailure.getMessage());
        throw new IllegalStateException(message);
    }

    private PcbBoardLayout generateAttempt(TroubleshootBoard board, Random random,
            int variationMode) {
        Rectangle outline = new Rectangle(WORKING_OUTLINE);
        PcbBoardLayout layout = new PcbBoardLayout(CANVAS_WIDTH, CANVAS_HEIGHT, outline,
            new Rectangle(850, 125, 150, 255));

        TopologyPlacementGraph topology = new TopologyPlacementGraph(board);
        Vector<Footprint> placed = placeTopology(board, topology, outline, random, variationMode);
        for (Footprint footprint : placed) {
            layout.addComponent(footprint.placement);
            for (PcbPadPlacement pad : footprint.pads)
                layout.addPad(pad);
        }
        routeNets(layout, board, outline);
        placeSilkscreen(layout, board, outline);
        layout.validateGeometry(board);
        layout.compactToContent(FINAL_BOARD_X + variationMode * 10,
            FINAL_BOARD_Y + (variationMode % 2) * 10, FINAL_EDGE_MARGIN);
        layout.validateGeometry(board);
        return layout;
    }

    private Vector<Footprint> placeTopology(TroubleshootBoard board,
            TopologyPlacementGraph topology, Rectangle outline, Random random,
            int variationMode) {
        Vector<Footprint> placed = new Vector<Footprint>();
        Footprint connector = placeConnector(board, outline, random, placed, variationMode);
        placed.add(connector);

        Vector<String> componentIds = board.getComponentIds();
        Collections.sort(componentIds);
        Vector<String> remaining = new Vector<String>();
        for (String componentId : componentIds)
            if (!"J1".equals(componentId))
                remaining.add(componentId);
        while (!remaining.isEmpty()) {
            String componentId = chooseNextComponent(remaining, placed, topology);
            Footprint footprint = placeTopologyComponent(board.getComponent(componentId),
                topology, outline, random, placed, variationMode);
            placed.add(footprint);
            remaining.remove(componentId);
        }
        return placed;
    }

    private String chooseNextComponent(Vector<String> remaining, Vector<Footprint> placed,
            TopologyPlacementGraph topology) {
        String bestId = remaining.get(0);
        double bestScore = Double.NEGATIVE_INFINITY;
        for (String componentId : remaining) {
            double score = 0;
            for (TopologyPlacementGraph.PadLink link : topology.getLinksFor(componentId)) {
                if (findFootprint(placed, link.getOtherComponentId()) != null)
                    score += link.getWeight();
            }
            if (score > bestScore || (score == bestScore && componentId.compareTo(bestId) < 0)) {
                bestId = componentId;
                bestScore = score;
            }
        }
        return bestId;
    }

    private Footprint placeConnector(TroubleshootBoard board, Rectangle outline, Random random,
            Vector<Footprint> placed, int variationMode) {
        BoardComponent connector = board.getComponent("J1");
        if (connector == null || !"CONNECTOR".equals(connector.getType()))
            throw new IllegalStateException("Simple PCB generator requires connector J1");
        for (int attempt = 0; attempt < 80; attempt++) {
            // Keep the pad escape directed into the working area.  Seeded
            // variation is applied to the topology cluster, not by making the
            // connector's routing corridor point at the canvas edge.
            boolean rightEdge = true;
            int x = rightEdge ? outline.x + outline.width - 120 : outline.x + 20;
            int y = align(outline.y + 80 + random.nextInt(outline.height - 230));
            Footprint candidate = createFootprint(connector, x, y, random, outline);
            if (fits(candidate, outline, placed))
                return candidate;
        }
        throw new IllegalStateException("Unable to place board connector");
    }

    private Footprint placeTopologyComponent(BoardComponent component,
            TopologyPlacementGraph topology, Rectangle outline, Random random,
            Vector<Footprint> placed, int variationMode) {
        Footprint prototype = createFootprint(component, 0, 0,
            new Random(random.nextLong()), outline);
        int targetX = outline.x + outline.width / 2 - prototype.placement.getWidth() / 2;
        int targetY = outline.y + outline.height / 2 - prototype.placement.getHeight() / 2;
        double targetWeight = 0;
        for (TopologyPlacementGraph.PadLink link : topology.getLinksFor(component.getId())) {
            Footprint other = findFootprint(placed, link.getOtherComponentId());
            if (other == null)
                continue;
            PcbPadPlacement sourcePad = prototype.getPad(link.getPadId());
            PcbPadPlacement otherPad = other.getPad(link.getOtherPadId());
            targetX += (int) Math.round((otherPad.getX() - sourcePad.getX()) * link.getWeight());
            targetY += (int) Math.round((otherPad.getY() - sourcePad.getY()) * link.getWeight());
            targetWeight += link.getWeight();
        }
        if (targetWeight > 0) {
            targetX = (int) Math.round((targetX -
                (outline.x + outline.width / 2 - prototype.placement.getWidth() / 2)) /
                targetWeight + (outline.x + outline.width / 2 - prototype.placement.getWidth() / 2));
            targetY = (int) Math.round((targetY -
                (outline.y + outline.height / 2 - prototype.placement.getHeight() / 2)) /
                targetWeight + (outline.y + outline.height / 2 - prototype.placement.getHeight() / 2));
        }
        targetX += random.nextInt(31) - 15;
        targetY += random.nextInt(31) - 15;

        int[][] offsets = new int[][] {
            { 0, 0 }, { -40, 0 }, { 40, 0 }, { 0, -50 }, { 0, 50 },
            { -80, 0 }, { 80, 0 }, { -40, -50 }, { 40, -50 }, { -40, 50 },
            { 40, 50 }, { -120, 0 }, { 120, 0 }, { 0, -100 }, { 0, 100 },
            { -80, -50 }, { 80, -50 }, { -80, 50 }, { 80, 50 }, { -160, 0 },
            { 160, 0 }, { -120, -50 }, { 120, -50 }, { -120, 50 }, { 120, 50 }
        };
        Footprint best = null;
        double bestScore = Double.POSITIVE_INFINITY;
        int firstOffset = random.nextInt(offsets.length);
        for (int index = 0; index < offsets.length; index++) {
            int[] offset = offsets[(firstOffset + index) % offsets.length];
            Footprint candidate = translated(prototype,
                align(targetX + offset[0]), align(targetY + offset[1]));
            if (!fits(candidate, outline, placed))
                continue;
            double score = placementScore(candidate, topology, placed, targetX, targetY,
                component.getId(), variationMode) +
                index * .05;
            if (best == null || score < bestScore) {
                best = candidate;
                bestScore = score;
            }
        }
        if (best != null)
            return best;

        for (int y = outline.y + 20; y < outline.y + outline.height - 100; y += GRID) {
            for (int x = outline.x + 20; x < outline.x + outline.width - 260; x += GRID) {
                Footprint candidate = translated(prototype, x, y);
                if (!fits(candidate, outline, placed))
                    continue;
                double score = placementScore(candidate, topology, placed, targetX, targetY,
                    component.getId(), variationMode);
                if (best == null || score < bestScore) {
                    best = candidate;
                    bestScore = score;
                }
            }
        }
        if (best == null)
            throw new IllegalStateException("Unable to place board component: " + component.getId());
        return best;
    }

    private boolean fits(Footprint candidate, Rectangle outline, Vector<Footprint> placed) {
        Rectangle courtyard = candidate.placement.getRoutingCourtyard();
        if (courtyard.x < outline.x + 10 || courtyard.y < outline.y + 10 ||
                courtyard.x + courtyard.width > outline.x + outline.width - 10 ||
                courtyard.y + courtyard.height > outline.y + outline.height - 10)
            return false;
        Rectangle padded = new Rectangle(courtyard.x - 8, courtyard.y - 8,
            courtyard.width + 16, courtyard.height + 16);
        for (Footprint other : placed) {
            Rectangle otherCourtyard = other.placement.getRoutingCourtyard();
            if (padded.intersects(new Rectangle(otherCourtyard.x - 8, otherCourtyard.y - 8,
                    otherCourtyard.width + 16, otherCourtyard.height + 16)))
                return false;
        }
        for (PcbPadPlacement pad : candidate.pads) {
            for (Footprint other : placed) {
                for (PcbPadPlacement otherPad : other.pads) {
                    int dx = pad.getX() - otherPad.getX();
                    int dy = pad.getY() - otherPad.getY();
                    if (dx * dx + dy * dy < 26 * 26)
                        return false;
                }
            }
        }
        return true;
    }

    private double placementScore(Footprint candidate, TopologyPlacementGraph topology,
            Vector<Footprint> placed, int targetX, int targetY, String componentId,
            int variationMode) {
        double score = Math.abs(candidate.placement.getX() - targetX) * .15 +
            Math.abs(candidate.placement.getY() - targetY) * .15;
        int componentSign = (componentId.hashCode() & 1) == 0 ? -1 : 1;
        if (variationMode == 0)
            score += Math.abs(candidate.placement.getX() - targetX - componentSign * 40) * .35;
        else if (variationMode == 1)
            score += Math.abs(candidate.placement.getY() - targetY - componentSign * 50) * .35;
        else if (variationMode == 2)
            score += Math.abs((candidate.placement.getX() - targetX) - componentSign * 40) * .20 +
                Math.abs((candidate.placement.getY() - targetY) + componentSign * 50) * .20;
        else
            score += Math.abs(candidate.placement.getX() - targetX + componentSign * 80) * .30;
        for (TopologyPlacementGraph.PadLink link : topology.getLinksFor(
                candidate.placement.getComponentId())) {
            Footprint other = findFootprint(placed, link.getOtherComponentId());
            if (other == null)
                continue;
            PcbPadPlacement sourcePad = candidate.getPad(link.getPadId());
            PcbPadPlacement otherPad = other.getPad(link.getOtherPadId());
            score += (Math.abs(sourcePad.getX() - otherPad.getX()) +
                Math.abs(sourcePad.getY() - otherPad.getY())) * link.getWeight();
        }
        return score;
    }

    private Footprint findFootprint(Vector<Footprint> footprints, String componentId) {
        for (Footprint footprint : footprints)
            if (footprint.placement.getComponentId().equals(componentId))
                return footprint;
        return null;
    }

    private Footprint translated(Footprint source, int x, int y) {
        int dx = x - source.placement.getX();
        int dy = y - source.placement.getY();
        PcbComponentPlacement placement = source.placement;
        PcbComponentPlacement translatedPlacement = new PcbComponentPlacement(
            placement.getComponentId(), x, y, placement.getWidth(), placement.getHeight(),
            translate(placement.getKeepOut(), dx, dy),
            translate(placement.getRoutingCourtyard(), dx, dy));
        Vector<PcbPadPlacement> translatedPads = new Vector<PcbPadPlacement>();
        for (PcbPadPlacement pad : source.pads)
            translatedPads.add(new PcbPadPlacement(pad.getPadId(), pad.getX() + dx,
                pad.getY() + dy, pad.getEscapeDx(), pad.getEscapeDy(), pad.getEscapeLength()));
        return new Footprint(translatedPlacement, translatedPads);
    }

    private static Rectangle translate(Rectangle rectangle, int dx, int dy) {
        return new Rectangle(rectangle.x + dx, rectangle.y + dy, rectangle.width,
            rectangle.height);
    }

    private Footprint createFootprint(BoardComponent component, int x, int y, Random random,
            Rectangle outline) {
        Vector<String> padIds = component.getPadIds();
        if (padIds.size() != 2)
            throw new IllegalStateException("Simple PCB footprint requires two pads: " +
                component.getId());
        Vector<PcbPadPlacement> pads = new Vector<PcbPadPlacement>();
        PcbComponentPlacement placement;
        String type = component.getType();
        if ("CONNECTOR".equals(type)) {
            boolean leftEdge = x < outline.x + outline.width / 2;
            int padX = leftEdge ? x + 90 : x + 10;
            int escapeDx = leftEdge ? 1 : -1;
            pads.add(new PcbPadPlacement(padIds.get(0), padX, y + 40, escapeDx, 0, 30));
            pads.add(new PcbPadPlacement(padIds.get(1), padX, y + 100, escapeDx, 0, 30));
            placement = new PcbComponentPlacement(component.getId(), x, y, 100, 130,
                new Rectangle(x, y, 100, 130), new Rectangle(x - 6, y - 6, 112, 142));
        } else if ("RESISTOR".equals(type)) {
            int span = 220 + random.nextInt(3) * 20;
            pads.add(new PcbPadPlacement(padIds.get(0), x + 30, y + 30, -1, 0, 50));
            pads.add(new PcbPadPlacement(padIds.get(1), x + span - 30, y + 30, 1, 0, 50));
            placement = new PcbComponentPlacement(component.getId(), x, y, span, 70,
                new Rectangle(x + 70, y + 18, span - 140, 34),
                new Rectangle(x + 12, y + 5, span - 24, 60));
        } else if ("DIODE".equals(type)) {
            int span = 230 + random.nextInt(2) * 20;
            pads.add(new PcbPadPlacement(padIds.get(0), x + 30, y + 30, -1, 0, 50));
            pads.add(new PcbPadPlacement(padIds.get(1), x + span - 30, y + 30, 1, 0, 50));
            placement = new PcbComponentPlacement(component.getId(), x, y, span, 70,
                new Rectangle(x + 72, y + 19, span - 144, 32),
                new Rectangle(x + 12, y + 5, span - 24, 60));
        } else if ("LED".equals(type)) {
            // The LED body sits above its two leads.  Both pads therefore
            // escape straight down, through the short lead corridor, rather
            // than routing sideways through the circular body keep-out.
            pads.add(new PcbPadPlacement(padIds.get(0), x + 20, y + 70, 0, 1, 35));
            pads.add(new PcbPadPlacement(padIds.get(1), x + 60, y + 70, 0, 1, 35));
            placement = new PcbComponentPlacement(component.getId(), x, y, 90, 100,
                new Rectangle(x + 15, y + 12, 60, 60),
                new Rectangle(x + 6, y + 4, 78, 101));
        } else {
            throw new IllegalStateException("Unsupported PCB footprint type: " + type);
        }
        return new Footprint(placement, pads);
    }

    private void placeSilkscreen(PcbBoardLayout layout, TroubleshootBoard board,
            Rectangle outline) {
        String title;
        if (board.getId().equals("DIODE_PROTECTED_INDICATOR"))
            title = "TSJ DIODE INDICATOR";
        else if (board.getId().equals("PARALLEL_DUAL_INDICATOR"))
            title = "TSJ PARALLEL INDICATORS";
        else
            title = "TSJ LED INDICATOR";
        int componentLeft = outline.x + outline.width;
        int componentTop = outline.y + outline.height;
        for (PcbComponentPlacement component : layout.getComponents()) {
            componentLeft = Math.min(componentLeft, component.getRoutingCourtyard().x);
            componentTop = Math.min(componentTop, component.getRoutingCourtyard().y);
        }
        int titleY = Math.max(outline.y + 15, componentTop - 34);
        addLabel(layout, board, outline, "board-title", title,
            new Rectangle(componentLeft, titleY, textWidth(title, 14), 18),
            14, true, null);

        Vector<String> componentIds = board.getComponentIds();
        Collections.sort(componentIds);
        for (String componentId : componentIds) {
            PcbComponentPlacement placement = layout.getComponent(componentId);
            String text = componentId;
            int width = textWidth(text, 14);
            Vector<Rectangle> candidates = getReferenceCandidates(placement, width, 18);
            Rectangle selected = chooseLabelPosition(layout, board, outline, candidates);
            addLabel(layout, board, outline, "component:" + componentId, text, selected,
                14, true, null);
        }

        PcbComponentPlacement connector = layout.getComponent("J1");
        if (connector == null)
            throw new IllegalStateException("Missing connector for silkscreen labels");
        boolean leftEdge = connector.getX() < outline.x + outline.width / 2;
        placeNetLabel(layout, board, outline, "J1.1", "+V", leftEdge);
        placeNetLabel(layout, board, outline, "J1.2", "GND", leftEdge);
    }

    private void placeNetLabel(PcbBoardLayout layout, TroubleshootBoard board, Rectangle outline,
            String padId, String text, boolean leftEdge) {
        PcbPadPlacement pad = layout.getPad(padId);
        if (pad == null)
            throw new IllegalStateException("Missing connector pad for silkscreen label: " + padId);
        int width = textWidth("+V".equals(text) ? "+12V" : text, 12);
        Vector<Rectangle> candidates = new Vector<Rectangle>();
        int sideX = leftEdge ? pad.getX() + 22 : pad.getX() - width - 22;
        candidates.add(new Rectangle(sideX, pad.getY() - 23, width, 16));
        candidates.add(new Rectangle(sideX, pad.getY() + 8, width, 16));
        int outerX = leftEdge ? pad.getX() - width - 22 : pad.getX() + 22;
        candidates.add(new Rectangle(outerX, pad.getY() - 23, width, 16));
        candidates.add(new Rectangle(outerX, pad.getY() + 8, width, 16));
        Rectangle selected = chooseLabelPosition(layout, board, outline, candidates);
        addLabel(layout, board, outline, "net:" + padId, text, selected, 12, false, padId);
    }

    private Vector<Rectangle> getReferenceCandidates(PcbComponentPlacement placement, int width,
            int height) {
        Rectangle bounds = placement.getBounds();
        int centerX = bounds.x + bounds.width / 2;
        int centerY = bounds.y + bounds.height / 2;
        Vector<Rectangle> candidates = new Vector<Rectangle>();
        candidates.add(new Rectangle(centerX - width / 2, bounds.y - height - 8, width, height));
        candidates.add(new Rectangle(centerX - width / 2, bounds.y + bounds.height + 8,
            width, height));
        candidates.add(new Rectangle(bounds.x - width - 8, centerY - height / 2, width, height));
        candidates.add(new Rectangle(bounds.x + bounds.width + 8, centerY - height / 2,
            width, height));
        return candidates;
    }

    private Rectangle chooseLabelPosition(PcbBoardLayout layout, TroubleshootBoard board,
            Rectangle outline, Vector<Rectangle> candidates) {
        for (Rectangle candidate : candidates) {
            if (isLabelPositionFree(layout, board, outline, candidate))
                return candidate;
        }
        throw new IllegalStateException("Unable to place collision-free PCB silkscreen label");
    }

    private boolean isLabelPositionFree(PcbBoardLayout layout, TroubleshootBoard board,
            Rectangle outline, Rectangle candidate) {
        if (candidate.x < outline.x || candidate.y < outline.y ||
                candidate.x + candidate.width > outline.x + outline.width ||
                candidate.y + candidate.height > outline.y + outline.height)
            return false;
        for (PcbComponentPlacement component : layout.getComponents()) {
            if (candidate.intersects(component.getBounds()))
                return false;
        }
        for (PcbPadPlacement pad : layout.getPads()) {
            if (candidate.intersects(new Rectangle(pad.getX() - 16, pad.getY() - 16, 32, 32)))
                return false;
        }
        for (PcbTraceGeometry trace : layout.getTraces()) {
            int[] xPoints = trace.getXPoints();
            int[] yPoints = trace.getYPoints();
            for (int index = 1; index < xPoints.length; index++) {
                if (segmentIntersects(candidate, xPoints[index - 1], yPoints[index - 1],
                        xPoints[index], yPoints[index]))
                    return false;
            }
        }
        for (PcbSilkscreenLabel label : layout.getSilkscreenLabels()) {
            if (candidate.intersects(label.getBounds()))
                return false;
        }
        return true;
    }

    private void addLabel(PcbBoardLayout layout, TroubleshootBoard board, Rectangle outline,
            String id, String text, Rectangle bounds, int fontSize, boolean bold,
            String targetPadId) {
        if (!isLabelPositionFree(layout, board, outline, bounds))
            throw new IllegalStateException("PCB silkscreen label position became occupied: " + id);
        layout.addSilkscreenLabel(new PcbSilkscreenLabel(id, text, bounds, fontSize, bold,
            targetPadId));
    }

    private static int textWidth(String text, int fontSize) {
        return Math.max(18, text.length() * (fontSize <= 12 ? 8 : 9));
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

    private void routeNets(PcbBoardLayout layout, TroubleshootBoard board, Rectangle outline) {
        Vector<String> netIds = board.getNetIds();
        Collections.sort(netIds);
        Router router = new Router(layout, board, outline);
        for (String netId : netIds) {
            Vector<String> padIds = board.getNet(netId).getPadIds();
            Collections.sort(padIds);
            if (padIds.size() < 2)
                throw new IllegalStateException("Cannot route net with fewer than two pads: " +
                    netId);
            for (int index = 1; index < padIds.size(); index++)
                layout.addTrace(router.route(netId, padIds.get(0), padIds.get(index)));
        }
    }

    private static Random attemptRandom(long seed, int attempt) {
        return new Random(seed ^ (0x9E3779B97F4A7C15L * (attempt + 1)));
    }

    private static int align(int value) { return (value / GRID) * GRID; }

    private static class Footprint {
        private final PcbComponentPlacement placement;
        private final Vector<PcbPadPlacement> pads;

        Footprint(PcbComponentPlacement placement, Vector<PcbPadPlacement> pads) {
            this.placement = placement;
            this.pads = pads;
        }

        PcbPadPlacement getPad(String padId) {
            for (PcbPadPlacement pad : pads)
                if (pad.getPadId().equals(padId))
                    return pad;
            throw new IllegalStateException("Footprint is missing pad: " + padId);
        }
    }

    private static class Router {
        private final PcbBoardLayout layout;
        private final TroubleshootBoard board;
        private final Rectangle outline;
        private final int minX;
        private final int minY;
        private final int gridWidth;
        private final int gridHeight;
        private final String[][] occupiedNet;
        private final String[][] clearanceNet;

        Router(PcbBoardLayout layout, TroubleshootBoard board, Rectangle outline) {
            this.layout = layout;
            this.board = board;
            this.outline = outline;
            minX = outline.x + GRID;
            minY = outline.y + GRID;
            gridWidth = (outline.width - 2 * GRID) / GRID + 1;
            gridHeight = (outline.height - 2 * GRID) / GRID + 1;
            occupiedNet = new String[gridWidth][gridHeight];
            clearanceNet = new String[gridWidth][gridHeight];
        }

        PcbTraceGeometry route(String netId, String startPadId, String endPadId) {
            PcbPadPlacement startPad = layout.getPad(startPadId);
            PcbPadPlacement endPad = layout.getPad(endPadId);
            int startX = gridX(startPad.getX());
            int startY = gridY(startPad.getY());
            int endX = gridX(endPad.getX());
            int endY = gridY(endPad.getY());
            if (startX < 0 || startY < 0 || endX < 0 || endY < 0)
                throw new IllegalStateException("Pad is not aligned to PCB routing grid: " +
                    netId);

            final int noneDirection = 4;
            final int[] directionX = { 0, 1, 0, -1 };
            final int[] directionY = { -1, 0, 1, 0 };
            double[][][] bestCost = new double[gridWidth][gridHeight][5];
            int[][][] previousX = new int[gridWidth][gridHeight][5];
            int[][][] previousY = new int[gridWidth][gridHeight][5];
            int[][][] previousDirection = new int[gridWidth][gridHeight][5];
            for (int x = 0; x < gridWidth; x++) {
                for (int y = 0; y < gridHeight; y++) {
                    for (int direction = 0; direction < 5; direction++) {
                        bestCost[x][y][direction] = Double.POSITIVE_INFINITY;
                        previousX[x][y][direction] = -1;
                        previousY[x][y][direction] = -1;
                        previousDirection[x][y][direction] = -1;
                    }
                }
            }
            PriorityQueue<SearchNode> open = new PriorityQueue<SearchNode>(128,
                new Comparator<SearchNode>() {
                    public int compare(SearchNode first, SearchNode second) {
                        return first.compareTo(second);
                    }
                });
            int sequence = 0;
            bestCost[startX][startY][noneDirection] = 0;
            open.add(new SearchNode(startX, startY, noneDirection, 0,
                manhattan(startX, startY, endX, endY) * GRID, sequence++));
            SearchNode goal = null;
            while (!open.isEmpty()) {
                SearchNode current = open.poll();
                if (current.cost != bestCost[current.x][current.y][current.direction])
                    continue;
                if (current.x == endX && current.y == endY) {
                    goal = current;
                    break;
                }
                for (int direction = 0; direction < 4; direction++) {
                    int nextX = current.x + directionX[direction];
                    int nextY = current.y + directionY[direction];
                    if (nextX < 0 || nextY < 0 || nextX >= gridWidth || nextY >= gridHeight ||
                            !isLegalMove(current, nextX, nextY, direction, startX, startY,
                                endX, endY, startPad, endPad) ||
                            !canTraverse(current.x, current.y, nextX, nextY, startPad, endPad) ||
                            !canOccupy(nextX, nextY, netId, startPad, endPad))
                        continue;
                    double stepCost = GRID;
                    if (netId.equals(occupiedNet[nextX][nextY]))
                        stepCost = 2;
                    else if (netId.equals(clearanceNet[nextX][nextY]))
                        stepCost = 7;
                    double cost = current.cost + stepCost;
                    if (current.direction != noneDirection && current.direction != direction)
                        cost += 35;
                    if (cost >= bestCost[nextX][nextY][direction])
                        continue;
                    bestCost[nextX][nextY][direction] = cost;
                    previousX[nextX][nextY][direction] = current.x;
                    previousY[nextX][nextY][direction] = current.y;
                    previousDirection[nextX][nextY][direction] = current.direction;
                    open.add(new SearchNode(nextX, nextY, direction, cost,
                        manhattan(nextX, nextY, endX, endY) * GRID, sequence++));
                }
            }
            if (goal == null)
                throw new IllegalStateException("Unable to route net " + netId + " from " +
                    startPadId + " to " + endPadId);

            Vector<Point> points = new Vector<Point>();
            int currentX = goal.x;
            int currentY = goal.y;
            int currentDirection = goal.direction;
            while (currentX >= 0 && currentY >= 0) {
                points.add(new Point(minX + currentX * GRID, minY + currentY * GRID));
                int priorX = previousX[currentX][currentY][currentDirection];
                int priorY = previousY[currentX][currentY][currentDirection];
                int priorDirection = previousDirection[currentX][currentY][currentDirection];
                if (priorX < 0 || priorY < 0)
                    break;
                currentX = priorX;
                currentY = priorY;
                currentDirection = priorDirection;
            }
            Collections.reverse(points);
            markCopper(points, netId);
            // Keep the grid path's boundary points.  A long collinear shortcut can
            // cut across a component keep-out even when every A* cell was legal.
            // The renderer naturally draws the same straight copper, while the
            // retained points preserve the router's clearance decisions exactly.
            Vector<Point> routedPoints = points;
            int[] xPoints = new int[routedPoints.size()];
            int[] yPoints = new int[routedPoints.size()];
            for (int index = 0; index < routedPoints.size(); index++) {
                xPoints[index] = routedPoints.get(index).x;
                yPoints[index] = routedPoints.get(index).y;
            }
            return new PcbTraceGeometry(netId, startPadId, endPadId, xPoints, yPoints);
        }

        private boolean isLegalMove(SearchNode current, int nextX, int nextY, int direction,
                int startX, int startY, int endX, int endY, PcbPadPlacement startPad,
                PcbPadPlacement endPad) {
            if (current.x == startX && current.y == startY && current.direction == 4 &&
                    startPad.getEscapeLength() > 0 &&
                    (directionX(direction) != startPad.getEscapeDx() ||
                    directionY(direction) != startPad.getEscapeDy()))
                return false;
            if (nextX == endX && nextY == endY && endPad.getEscapeLength() > 0 &&
                    (directionX(direction) != -endPad.getEscapeDx() ||
                    directionY(direction) != -endPad.getEscapeDy()))
                return false;
            return true;
        }

        private boolean canOccupy(int x, int y, String netId, PcbPadPlacement startPad,
                PcbPadPlacement endPad) {
            int physicalX = minX + x * GRID;
            int physicalY = minY + y * GRID;
            if ((occupiedNet[x][y] != null && !netId.equals(occupiedNet[x][y])) ||
                    (clearanceNet[x][y] != null && !netId.equals(clearanceNet[x][y])) ||
                    isPadAtOtherNet(physicalX, physicalY, startPad.getPadId(),
                        endPad.getPadId()))
                return false;
            if ((physicalX == startPad.getX() && physicalY == startPad.getY()) ||
                    (physicalX == endPad.getX() && physicalY == endPad.getY()))
                return true;
            for (PcbComponentPlacement component : layout.getComponents()) {
                if (!containsInclusive(component.getRoutingCourtyard(), physicalX, physicalY))
                    continue;
                boolean startEscape = component.getComponentId().equals(
                    board.getPad(startPad.getPadId()).getComponentId()) &&
                    startPad.isInEscapeCorridor(physicalX, physicalY);
                boolean endEscape = component.getComponentId().equals(
                    board.getPad(endPad.getPadId()).getComponentId()) &&
                    endPad.isInEscapeCorridor(physicalX, physicalY);
                if (!startEscape && !endEscape)
                    return false;
            }
            return true;
        }

        private boolean canTraverse(int fromX, int fromY, int toX, int toY,
                PcbPadPlacement startPad, PcbPadPlacement endPad) {
            int startPhysicalX = minX + fromX * GRID;
            int startPhysicalY = minY + fromY * GRID;
            int endPhysicalX = minX + toX * GRID;
            int endPhysicalY = minY + toY * GRID;
            String startComponentId = board.getPad(startPad.getPadId()).getComponentId();
            String endComponentId = board.getPad(endPad.getPadId()).getComponentId();
            for (PcbComponentPlacement component : layout.getComponents()) {
                if (!segmentTouchesKeepOut(component.getRoutingCourtyard(), startPhysicalX,
                        startPhysicalY, endPhysicalX, endPhysicalY))
                    continue;
                boolean startEscape = component.getComponentId().equals(startComponentId) &&
                    courtyardIntersectionIsEscape(component.getRoutingCourtyard(), startPad,
                        startPhysicalX, startPhysicalY, endPhysicalX, endPhysicalY);
                boolean endEscape = component.getComponentId().equals(endComponentId) &&
                    courtyardIntersectionIsEscape(component.getRoutingCourtyard(), endPad,
                        startPhysicalX, startPhysicalY, endPhysicalX, endPhysicalY);
                if (!startEscape && !endEscape)
                    return false;
            }
            return true;
        }

        private boolean courtyardIntersectionIsEscape(Rectangle courtyard,
                PcbPadPlacement pad, int x1, int y1, int x2, int y2) {
            if (y1 == y2) {
                int left = Math.max(Math.min(x1, x2), courtyard.x);
                int right = Math.min(Math.max(x1, x2), courtyard.x + courtyard.width);
                return left <= right && pad.isInEscapeCorridor(left, y1) &&
                    pad.isInEscapeCorridor(right, y1);
            }
            if (x1 == x2) {
                int top = Math.max(Math.min(y1, y2), courtyard.y);
                int bottom = Math.min(Math.max(y1, y2), courtyard.y + courtyard.height);
                return top <= bottom && pad.isInEscapeCorridor(x1, top) &&
                    pad.isInEscapeCorridor(x1, bottom);
            }
            return false;
        }

        private void markCopper(Vector<Point> points, String netId) {
            for (Point point : points) {
                int centerX = gridX(point.x);
                int centerY = gridY(point.y);
                occupiedNet[centerX][centerY] = netId;
                for (int x = Math.max(0, centerX - PcbTraceRules.ROUTING_GRID_CLEARANCE_CELLS);
                        x <= Math.min(gridWidth - 1,
                            centerX + PcbTraceRules.ROUTING_GRID_CLEARANCE_CELLS); x++) {
                    for (int y = Math.max(0, centerY - PcbTraceRules.ROUTING_GRID_CLEARANCE_CELLS);
                            y <= Math.min(gridHeight - 1,
                                centerY + PcbTraceRules.ROUTING_GRID_CLEARANCE_CELLS); y++)
                        if (clearanceNet[x][y] == null)
                            clearanceNet[x][y] = netId;
                }
            }
        }

        private boolean isPadAtOtherNet(int x, int y, String startPadId, String endPadId) {
            for (PcbPadPlacement pad : layout.getPads()) {
                if (pad.getPadId().equals(startPadId) || pad.getPadId().equals(endPadId))
                    continue;
                if (pad.getX() == x && pad.getY() == y)
                    return true;
            }
            return false;
        }

        private int directionX(int direction) {
            return direction == 1 ? 1 : direction == 3 ? -1 : 0;
        }

        private int directionY(int direction) {
            return direction == 0 ? -1 : direction == 2 ? 1 : 0;
        }

        private int manhattan(int x, int y, int otherX, int otherY) {
            return Math.abs(x - otherX) + Math.abs(y - otherY);
        }

        private int gridX(int x) { return (x - minX) % GRID == 0 ? (x - minX) / GRID : -1; }
        private int gridY(int y) { return (y - minY) % GRID == 0 ? (y - minY) / GRID : -1; }

        private static boolean containsInclusive(Rectangle rectangle, int x, int y) {
            return x >= rectangle.x && y >= rectangle.y &&
                x <= rectangle.x + rectangle.width && y <= rectangle.y + rectangle.height;
        }

        private static boolean segmentTouchesKeepOut(Rectangle rectangle, int firstX, int firstY,
                int secondX, int secondY) {
            if (firstX == secondX)
                return firstX >= rectangle.x && firstX <= rectangle.x + rectangle.width &&
                    Math.max(Math.min(firstY, secondY), rectangle.y) <=
                    Math.min(Math.max(firstY, secondY), rectangle.y + rectangle.height);
            if (firstY == secondY)
                return firstY >= rectangle.y && firstY <= rectangle.y + rectangle.height &&
                    Math.max(Math.min(firstX, secondX), rectangle.x) <=
                    Math.min(Math.max(firstX, secondX), rectangle.x + rectangle.width);
            return false;
        }

        private static class SearchNode {
            private final int x;
            private final int y;
            private final int direction;
            private final double cost;
            private final double heuristic;
            private final int sequence;

            SearchNode(int x, int y, int direction, double cost, double heuristic,
                    int sequence) {
                this.x = x;
                this.y = y;
                this.direction = direction;
                this.cost = cost;
                this.heuristic = heuristic;
                this.sequence = sequence;
            }

            int compareTo(SearchNode other) {
                double total = cost + heuristic;
                double otherTotal = other.cost + other.heuristic;
                if (total < otherTotal)
                    return -1;
                if (total > otherTotal)
                    return 1;
                if (heuristic < other.heuristic)
                    return -1;
                if (heuristic > other.heuristic)
                    return 1;
                if (y != other.y)
                    return y - other.y;
                if (x != other.x)
                    return x - other.x;
                if (direction != other.direction)
                    return direction - other.direction;
                return sequence - other.sequence;
            }
        }
    }
}
