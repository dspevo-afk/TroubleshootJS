package com.lushprojects.circuitjs1.client;

import java.util.Collections;
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

    PcbBoardLayout generate(TroubleshootBoard board, long seed) {
        if (board == null)
            throw new IllegalArgumentException("Missing logical board for PCB generation");
        RuntimeException lastFailure = null;
        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            try {
                return generateAttempt(board, attemptRandom(seed, attempt));
            } catch (RuntimeException failure) {
                lastFailure = failure;
            }
        }
        String message = "Unable to generate a routed PCB after " + MAX_ATTEMPTS +
            " deterministic attempts for seed " + seed + ": " +
            (lastFailure == null ? "unknown failure" : lastFailure.getMessage());
        throw new IllegalStateException(message);
    }

    private PcbBoardLayout generateAttempt(TroubleshootBoard board, Random random) {
        int boardX = 40 + random.nextInt(3) * 10;
        int boardY = 40 + random.nextInt(3) * 10;
        int boardWidth = 700 + random.nextInt(7) * 10;
        int boardHeight = 350 + random.nextInt(6) * 10;
        Rectangle outline = new Rectangle(boardX, boardY, boardWidth, boardHeight);
        PcbBoardLayout layout = new PcbBoardLayout(CANVAS_WIDTH, CANVAS_HEIGHT, outline,
            new Rectangle(850, 125, 150, 255));

        Vector<Footprint> placed = new Vector<Footprint>();
        Footprint connector = placeConnector(board, outline, random, placed);
        placed.add(connector);

        Vector<String> componentIds = board.getComponentIds();
        Collections.sort(componentIds);
        for (String componentId : componentIds) {
            if ("J1".equals(componentId))
                continue;
            Footprint footprint = placeComponent(board.getComponent(componentId), outline,
                random, placed);
            placed.add(footprint);
        }

        for (Footprint footprint : placed) {
            layout.addComponent(footprint.placement);
            for (PcbPadPlacement pad : footprint.pads)
                layout.addPad(pad);
        }
        routeNets(layout, board, outline);
        layout.validateGeometry(board);
        return layout;
    }

    private Footprint placeConnector(TroubleshootBoard board, Rectangle outline, Random random,
            Vector<Footprint> placed) {
        BoardComponent connector = board.getComponent("J1");
        if (connector == null || !"CONNECTOR".equals(connector.getType()))
            throw new IllegalStateException("Simple PCB generator requires connector J1");
        for (int attempt = 0; attempt < 80; attempt++) {
            boolean rightEdge = random.nextBoolean();
            int x = rightEdge ? outline.x + outline.width - 120 : outline.x + 20;
            int y = align(outline.y + 80 + random.nextInt(outline.height - 230));
            Footprint candidate = createFootprint(connector, x, y, random);
            if (fits(candidate, outline, placed))
                return candidate;
        }
        throw new IllegalStateException("Unable to place board connector");
    }

    private Footprint placeComponent(BoardComponent component, Rectangle outline, Random random,
            Vector<Footprint> placed) {
        for (int attempt = 0; attempt < 100; attempt++) {
            int x = align(outline.x + 20 + random.nextInt(outline.width - 300));
            int y = align(outline.y + 20 + random.nextInt(outline.height - 150));
            Footprint candidate = createFootprint(component, x, y, random);
            if (fits(candidate, outline, placed))
                return candidate;
        }
        throw new IllegalStateException("Unable to place board component: " + component.getId());
    }

    private boolean fits(Footprint candidate, Rectangle outline, Vector<Footprint> placed) {
        Rectangle bounds = candidate.placement.getBounds();
        if (bounds.x < outline.x + 10 || bounds.y < outline.y + 10 ||
                bounds.x + bounds.width > outline.x + outline.width - 10 ||
                bounds.y + bounds.height > outline.y + outline.height - 10)
            return false;
        Rectangle padded = new Rectangle(bounds.x - 15, bounds.y - 15,
            bounds.width + 30, bounds.height + 30);
        for (Footprint other : placed) {
            if (padded.intersects(new Rectangle(other.placement.getX() - 15,
                    other.placement.getY() - 15, other.placement.getWidth() + 30,
                    other.placement.getHeight() + 30)))
                return false;
        }
        for (PcbPadPlacement pad : candidate.pads) {
            for (Footprint other : placed) {
                for (PcbPadPlacement otherPad : other.pads) {
                    int dx = pad.getX() - otherPad.getX();
                    int dy = pad.getY() - otherPad.getY();
                    if (dx * dx + dy * dy < 36 * 36)
                        return false;
                }
            }
        }
        return true;
    }

    private Footprint createFootprint(BoardComponent component, int x, int y, Random random) {
        Vector<String> padIds = component.getPadIds();
        if (padIds.size() != 2)
            throw new IllegalStateException("Simple PCB footprint requires two pads: " +
                component.getId());
        Vector<PcbPadPlacement> pads = new Vector<PcbPadPlacement>();
        PcbComponentPlacement placement;
        String type = component.getType();
        if ("CONNECTOR".equals(type)) {
            pads.add(new PcbPadPlacement(padIds.get(0), x + 50, y + 40));
            pads.add(new PcbPadPlacement(padIds.get(1), x + 50, y + 100));
            placement = new PcbComponentPlacement(component.getId(), x, y, 100, 130);
        } else if ("RESISTOR".equals(type)) {
            int span = 220 + random.nextInt(3) * 20;
            pads.add(new PcbPadPlacement(padIds.get(0), x + 30, y + 30));
            pads.add(new PcbPadPlacement(padIds.get(1), x + span - 30, y + 30));
            placement = new PcbComponentPlacement(component.getId(), x, y, span, 70,
                new Rectangle(x + 70, y + 18, span - 140, 34));
        } else if ("DIODE".equals(type)) {
            int span = 230 + random.nextInt(2) * 20;
            pads.add(new PcbPadPlacement(padIds.get(0), x + 30, y + 30));
            pads.add(new PcbPadPlacement(padIds.get(1), x + span - 30, y + 30));
            placement = new PcbComponentPlacement(component.getId(), x, y, span, 70,
                new Rectangle(x + 72, y + 19, span - 144, 32));
        } else if ("LED".equals(type)) {
            pads.add(new PcbPadPlacement(padIds.get(0), x + 20, y + 70));
            pads.add(new PcbPadPlacement(padIds.get(1), x + 60, y + 70));
            placement = new PcbComponentPlacement(component.getId(), x, y, 90, 100,
                new Rectangle(x + 15, y + 12, 60, 60));
        } else {
            throw new IllegalStateException("Unsupported PCB footprint type: " + type);
        }
        return new Footprint(placement, pads);
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
    }

    private static class Router {
        private final PcbBoardLayout layout;
        private final TroubleshootBoard board;
        private final Rectangle outline;
        private final int minX;
        private final int minY;
        private final int gridWidth;
        private final int gridHeight;
        private final boolean[][] occupied;

        Router(PcbBoardLayout layout, TroubleshootBoard board, Rectangle outline) {
            this.layout = layout;
            this.board = board;
            this.outline = outline;
            minX = outline.x + GRID;
            minY = outline.y + GRID;
            gridWidth = (outline.width - 2 * GRID) / GRID + 1;
            gridHeight = (outline.height - 2 * GRID) / GRID + 1;
            occupied = new boolean[gridWidth][gridHeight];
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

            boolean[][] visited = new boolean[gridWidth][gridHeight];
            int[][] previous = new int[gridWidth][gridHeight];
            for (int x = 0; x < gridWidth; x++)
                for (int y = 0; y < gridHeight; y++)
                    previous[x][y] = -1;
            Vector<Integer> queue = new Vector<Integer>();
            queue.add(Integer.valueOf(encode(startX, startY)));
            visited[startX][startY] = true;
            int head = 0;
            int[] directions = { 0, -1, 1, 0, 0, 1, -1, 0 };
            while (head < queue.size()) {
                int current = queue.get(head++).intValue();
                int currentX = current % gridWidth;
                int currentY = current / gridWidth;
                if (currentX == endX && currentY == endY)
                    break;
                for (int direction = 0; direction < directions.length; direction += 2) {
                    int nextX = currentX + directions[direction];
                    int nextY = currentY + directions[direction + 1];
                    if (nextX < 0 || nextY < 0 || nextX >= gridWidth || nextY >= gridHeight ||
                            visited[nextX][nextY] || !canOccupy(nextX, nextY, startPadId,
                            endPadId))
                        continue;
                    visited[nextX][nextY] = true;
                    previous[nextX][nextY] = current;
                    queue.add(Integer.valueOf(encode(nextX, nextY)));
                }
            }
            if (!visited[endX][endY])
                throw new IllegalStateException("Unable to route net " + netId + " from " +
                    startPadId + " to " + endPadId);

            Vector<Point> points = new Vector<Point>();
            int current = encode(endX, endY);
            while (current >= 0) {
                points.add(new Point(minX + (current % gridWidth) * GRID,
                    minY + (current / gridWidth) * GRID));
                if (current == encode(startX, startY))
                    break;
                current = previous[current % gridWidth][current / gridWidth];
            }
            Collections.reverse(points);
            for (Point point : points)
                occupied[gridX(point.x)][gridY(point.y)] = true;
            Vector<Point> simplified = simplify(points);
            int[] xPoints = new int[simplified.size()];
            int[] yPoints = new int[simplified.size()];
            for (int index = 0; index < simplified.size(); index++) {
                xPoints[index] = simplified.get(index).x;
                yPoints[index] = simplified.get(index).y;
            }
            return new PcbTraceGeometry(netId, startPadId, endPadId, xPoints, yPoints);
        }

        private boolean canOccupy(int x, int y, String startPadId, String endPadId) {
            int physicalX = minX + x * GRID;
            int physicalY = minY + y * GRID;
            if ((physicalX == layout.getPad(startPadId).getX() &&
                    physicalY == layout.getPad(startPadId).getY()) ||
                    (physicalX == layout.getPad(endPadId).getX() &&
                    physicalY == layout.getPad(endPadId).getY()))
                return true;
            if (occupied[x][y] || isPadAtOtherNet(physicalX, physicalY, startPadId, endPadId))
                return false;
            for (PcbComponentPlacement component : layout.getComponents()) {
                if (component.getComponentId().equals(board.getPad(startPadId).getComponentId()) ||
                        component.getComponentId().equals(board.getPad(endPadId).getComponentId()))
                    continue;
                if (containsInclusive(component.getKeepOut(), physicalX, physicalY))
                    return false;
            }
            return true;
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

        private int gridX(int x) { return (x - minX) % GRID == 0 ? (x - minX) / GRID : -1; }
        private int gridY(int y) { return (y - minY) % GRID == 0 ? (y - minY) / GRID : -1; }
        private int encode(int x, int y) { return y * gridWidth + x; }

        private static boolean containsInclusive(Rectangle rectangle, int x, int y) {
            return x >= rectangle.x && y >= rectangle.y &&
                x <= rectangle.x + rectangle.width && y <= rectangle.y + rectangle.height;
        }

        private Vector<Point> simplify(Vector<Point> points) {
            Vector<Point> simplified = new Vector<Point>();
            simplified.add(points.get(0));
            int previousDx = 0;
            int previousDy = 0;
            for (int index = 1; index < points.size(); index++) {
                int dx = points.get(index).x - points.get(index - 1).x;
                int dy = points.get(index).y - points.get(index - 1).y;
                if (index > 1 && (dx != previousDx || dy != previousDy))
                    simplified.add(points.get(index - 1));
                previousDx = dx;
                previousDy = dy;
            }
            if (!simplified.lastElement().equals(points.lastElement()))
                simplified.add(points.lastElement());
            return simplified;
        }
    }
}
