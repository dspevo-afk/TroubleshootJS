package com.lushprojects.circuitjs1.client;

import java.util.Vector;

/** Independent small fixtures for the advisory PCB vacancy target helper. */
public final class PcbVacancyTargetsContractTest {
    private static int assertions;

    private PcbVacancyTargetsContractTest() { }

    public static void main(String[] args) {
        knownLargestPocketAndPurity();
        strictEdgeTouchAndTightFit();
        negativeCoordinatesAndTranslation();
        canonicalTies();
        invalidBoundsAndBudget();
        nullInputsReject();
        System.out.println("PASS: PCB vacancy targets contracts assertions=" + assertions);
    }

    private static void knownLargestPocketAndPurity() {
        Rectangle allowed = new Rectangle(0, 0, 400, 320);
        Vector<Rectangle> obstacles = new Vector<Rectangle>();
        obstacles.add(new Rectangle(0, 0, 400, 40));
        obstacles.add(new Rectangle(0, 200, 400, 120));
        obstacles.add(new Rectangle(0, 40, 80, 160));
        obstacles.add(new Rectangle(280, 40, 120, 160));
        Rectangle envelope = new Rectangle(500, 600, 80, 40);
        Point source = new Point(510, 607);

        int[] allowedBefore = values(allowed);
        int[][] obstaclesBefore = values(obstacles);
        int[] envelopeBefore = values(envelope);
        Point sourceBefore = new Point(source);
        Vector<Point> actual = PcbVacancyTargets.targets(allowed, obstacles,
                envelope, source);
        requirePoints(actual, new int[][] {
                { 150, 107 }, { 90, 47 }, { 210, 47 }, { 90, 167 }, { 210, 167 }
        }, "largest 5-by-4 pocket center and corners with local envelope offset");
        require(equal(allowedBefore, values(allowed)), "allowed rectangle remains unchanged");
        require(equal(obstaclesBefore, values(obstacles)), "obstacles and order remain unchanged");
        require(equal(envelopeBefore, values(envelope)), "source envelope remains unchanged");
        require(source.equals(sourceBefore), "source origin remains unchanged");

        Vector<Rectangle> reversed = new Vector<Rectangle>();
        for (int i = obstacles.size() - 1; i >= 0; i--) {
            reversed.add(new Rectangle(obstacles.get(i)));
        }
        Vector<Point> reordered = PcbVacancyTargets.targets(new Rectangle(allowed),
                reversed, new Rectangle(envelope), new Point(source));
        requirePoints(reordered, new int[][] {
                { 150, 107 }, { 90, 47 }, { 210, 47 }, { 90, 167 }, { 210, 167 }
        }, "obstacle order does not change canonical targets");
    }

    private static void strictEdgeTouchAndTightFit() {
        Vector<Rectangle> oneBlockedCell = new Vector<Rectangle>();
        oneBlockedCell.add(new Rectangle(40, 0, 40, 40));
        Vector<Point> edge = PcbVacancyTargets.targets(new Rectangle(0, 0, 80, 40),
                oneBlockedCell, new Rectangle(300, 300, 40, 40),
                new Point(300, 300));
        requirePoints(edge, new int[][] { { 0, 0 } },
                "obstacle sharing an edge leaves the adjacent cell available");

        Vector<Point> tight = PcbVacancyTargets.targets(new Rectangle(-40, 80, 80, 40),
                new Vector<Rectangle>(), new Rectangle(100, 100, 80, 40),
                new Point(107, 111));
        requirePoints(tight, new int[][] { { -33, 91 } },
                "a pocket exactly matching the envelope yields one unique target");
    }

    private static void negativeCoordinatesAndTranslation() {
        Rectangle allowed = new Rectangle(-83, -43, 200, 160);
        Vector<Rectangle> obstacles = new Vector<Rectangle>();
        // Only three units overlap the first allowed cell, but that whole cell
        // is blocked. The right edge ends at the next cell boundary.
        obstacles.add(new Rectangle(-100, -43, 20, 40));
        Rectangle envelope = new Rectangle(-500, -600, 40, 40);
        Point source = new Point(-490, -590);
        // The right 4-by-4 pocket has 16 cells, larger than the lower 5-by-3
        // pocket's 15 cells, so the partial blocker leaves the first as best.
        int[][] expected = new int[][] {
                { 27, 27 }, { -33, -33 }, { 87, -33 }, { -33, 87 }, { 87, 87 }
        };
        Vector<Point> original = PcbVacancyTargets.targets(allowed, obstacles,
                envelope, source);
        requirePoints(original, expected,
                "negative clipped obstacle blocks one whole cell and preserves local offset");

        int dx = 17;
        int dy = -23;
        Vector<Rectangle> shiftedObstacles = new Vector<Rectangle>();
        for (int i = 0; i < obstacles.size(); i++) {
            shiftedObstacles.add(translate(obstacles.get(i), dx, dy));
        }
        Vector<Point> shifted = PcbVacancyTargets.targets(translate(allowed, dx, dy),
                shiftedObstacles, translate(envelope, dx, dy),
                new Point(source.x + dx, source.y + dy));
        int[][] translatedExpected = new int[expected.length][2];
        for (int i = 0; i < expected.length; i++) {
            translatedExpected[i][0] = expected[i][0] + dx;
            translatedExpected[i][1] = expected[i][1] + dy;
        }
        requirePoints(shifted, translatedExpected,
                "arbitrary common translation translates all targets exactly");
    }

    private static void canonicalTies() {
        Vector<Rectangle> topPriorityObstacles = new Vector<Rectangle>();
        topPriorityObstacles.add(new Rectangle(0, 0, 120, 80));
        topPriorityObstacles.add(new Rectangle(80, 80, 120, 80));
        Vector<Point> topPriority = PcbVacancyTargets.targets(
                new Rectangle(0, 0, 200, 160), topPriorityObstacles,
                new Rectangle(500, 500, 40, 40), new Point(500, 500));
        requirePoints(topPriority, new int[][] {
                { 140, 20 }, { 120, 0 }, { 160, 0 }, { 120, 40 }, { 160, 40 }
        }, "equal areas prefer the higher pocket before the farther-left lower pocket");

        Vector<Rectangle> leftPriorityObstacles = new Vector<Rectangle>();
        leftPriorityObstacles.add(new Rectangle(80, 0, 40, 80));
        leftPriorityObstacles.add(new Rectangle(0, 80, 200, 80));
        // The two top 2-by-2 pockets are tied; the left pocket wins.
        Vector<Point> leftPriority = PcbVacancyTargets.targets(
                new Rectangle(0, 0, 200, 160), leftPriorityObstacles,
                new Rectangle(500, 500, 40, 40), new Point(500, 500));
        requirePoints(leftPriority, new int[][] {
                { 20, 20 }, { 0, 0 }, { 40, 0 }, { 0, 40 }, { 40, 40 }
        }, "equal top positions prefer the left pocket");

        Vector<Rectangle> widthTieObstacles = new Vector<Rectangle>();
        widthTieObstacles.add(new Rectangle(80, 80, 40, 40));
        Vector<Point> widthTie = PcbVacancyTargets.targets(
                new Rectangle(0, 0, 120, 120), widthTieObstacles,
                new Rectangle(500, 500, 40, 40), new Point(500, 500));
        requirePoints(widthTie, new int[][] {
                { 20, 40 }, { 0, 0 }, { 40, 0 }, { 0, 80 }, { 40, 80 }
        }, "equal top-left areas prefer the smaller width after top and left");
    }

    private static void invalidBoundsAndBudget() {
        requireEmpty(PcbVacancyTargets.targets(new Rectangle(0, 0, 39, 39),
                new Vector<Rectangle>(), new Rectangle(0, 0, 1, 1),
                new Point(0, 0)), "allowed region without a complete grid cell");

        Vector<Rectangle> fillsBoard = new Vector<Rectangle>();
        fillsBoard.add(new Rectangle(0, 0, 80, 80));
        requireEmpty(PcbVacancyTargets.targets(new Rectangle(0, 0, 80, 80),
                fillsBoard, new Rectangle(0, 0, 40, 40), new Point(0, 0)),
                "fully occupied region");

        Vector<Rectangle> blocksHalf = new Vector<Rectangle>();
        blocksHalf.add(new Rectangle(0, 0, 40, 40));
        requireEmpty(PcbVacancyTargets.targets(new Rectangle(0, 0, 80, 40),
                blocksHalf, new Rectangle(0, 0, 80, 40), new Point(0, 0)),
                "no pocket can contain a source envelope wider than the remaining space");

        requireEmpty(PcbVacancyTargets.targets(new Rectangle(0, 0, -40, 80),
                new Vector<Rectangle>(), new Rectangle(0, 0, 40, 40),
                new Point(0, 0)), "negative allowed width");
        requireEmpty(PcbVacancyTargets.targets(new Rectangle(0, 0, 80, 80),
                new Vector<Rectangle>(), new Rectangle(0, 0, 40, 0),
                new Point(0, 0)), "nonpositive source envelope height");

        Vector<Point> atLimit = PcbVacancyTargets.targets(new Rectangle(0, 0, 8000, 4000),
                new Vector<Rectangle>(), new Rectangle(100, 100, 40, 40),
                new Point(100, 100));
        requirePoints(atLimit, new int[][] {
                { 3980, 1980 }, { 0, 0 }, { 7960, 0 }, { 0, 3960 }, { 7960, 3960 }
        }, "the 20000-cell budget is inclusive");
        requireEmpty(PcbVacancyTargets.targets(new Rectangle(0, 0, 4000, 8040),
                new Vector<Rectangle>(), new Rectangle(100, 100, 40, 40),
                new Point(100, 100)), "grid above the 20000-cell budget");

        int maximum = Integer.MAX_VALUE;
        requireEmpty(PcbVacancyTargets.targets(
                new Rectangle(maximum - 39, 0, 80, 40), new Vector<Rectangle>(),
                new Rectangle(0, 0, 40, 40), new Point(0, 0)),
                "allowed right edge beyond int range");
        requireEmpty(PcbVacancyTargets.targets(new Rectangle(0, 0, 80, 40),
                new Vector<Rectangle>(), new Rectangle(maximum - 39, 0, 40, 40),
                new Point(maximum - 39, 0)), "source envelope right edge beyond int range");
        Vector<Rectangle> overflowingObstacle = new Vector<Rectangle>();
        overflowingObstacle.add(new Rectangle(maximum - 39, 0, 40, 40));
        requireEmpty(PcbVacancyTargets.targets(new Rectangle(0, 0, 80, 40),
                overflowingObstacle, new Rectangle(0, 0, 40, 40), new Point(0, 0)),
                "obstacle right edge beyond int range");

        Vector<Point> atIntBoundary = PcbVacancyTargets.targets(
                new Rectangle(maximum - 80, 0, 80, 40), new Vector<Rectangle>(),
                new Rectangle(0, 0, 40, 40), new Point(0, 0));
        requirePoints(atIntBoundary, new int[][] {
                { maximum - 60, 0 }, { maximum - 80, 0 }, { maximum - 40, 0 }
        }, "right edge exactly at Integer.MAX_VALUE remains representable");
    }

    private static void nullInputsReject() {
        expectIllegalArgument(new Runnable() {
            public void run() {
                PcbVacancyTargets.targets(null, new Vector<Rectangle>(),
                        new Rectangle(0, 0, 40, 40), new Point(0, 0));
            }
        }, "null allowed rectangle");
        expectIllegalArgument(new Runnable() {
            public void run() {
                PcbVacancyTargets.targets(new Rectangle(0, 0, 40, 40), null,
                        new Rectangle(0, 0, 40, 40), new Point(0, 0));
            }
        }, "null obstacle vector");
        expectIllegalArgument(new Runnable() {
            public void run() {
                PcbVacancyTargets.targets(new Rectangle(0, 0, 40, 40),
                        new Vector<Rectangle>(), null, new Point(0, 0));
            }
        }, "null source envelope");
        expectIllegalArgument(new Runnable() {
            public void run() {
                PcbVacancyTargets.targets(new Rectangle(0, 0, 40, 40),
                        new Vector<Rectangle>(), new Rectangle(0, 0, 40, 40), null);
            }
        }, "null source origin");
        expectIllegalArgument(new Runnable() {
            public void run() {
                Vector<Rectangle> malformed = new Vector<Rectangle>();
                malformed.add(null);
                PcbVacancyTargets.targets(new Rectangle(0, 0, 40, 40), malformed,
                        new Rectangle(0, 0, 40, 40), new Point(0, 0));
            }
        }, "null obstacle entry");
    }

    private static Rectangle translate(Rectangle rectangle, int dx, int dy) {
        return new Rectangle(rectangle.x + dx, rectangle.y + dy,
                rectangle.width, rectangle.height);
    }

    private static int[][] values(Vector<Rectangle> rectangles) {
        int[][] result = new int[rectangles.size()][4];
        for (int i = 0; i < rectangles.size(); i++) {
            result[i] = values(rectangles.get(i));
        }
        return result;
    }

    private static int[] values(Rectangle rectangle) {
        return new int[] { rectangle.x, rectangle.y, rectangle.width, rectangle.height };
    }

    private static boolean equal(int[] left, int[] right) {
        if (left.length != right.length) {
            return false;
        }
        for (int i = 0; i < left.length; i++) {
            if (left[i] != right[i]) {
                return false;
            }
        }
        return true;
    }

    private static boolean equal(int[][] left, int[][] right) {
        if (left.length != right.length) {
            return false;
        }
        for (int i = 0; i < left.length; i++) {
            if (!equal(left[i], right[i])) {
                return false;
            }
        }
        return true;
    }

    private static void requirePoints(Vector<Point> actual, int[][] expected,
            String why) {
        require(actual.size() == expected.length, why + " (count)");
        for (int i = 0; i < expected.length; i++) {
            require(actual.get(i).x == expected[i][0] &&
                    actual.get(i).y == expected[i][1],
                    why + " (point " + i + ": expected " + expected[i][0] + "," +
                    expected[i][1] + " got " + actual.get(i) + ")");
        }
    }

    private static void requireEmpty(Vector<Point> actual, String why) {
        require(actual.isEmpty(), why + " returns no candidates");
    }

    private static void expectIllegalArgument(Runnable action, String why) {
        boolean thrown = false;
        try {
            action.run();
        } catch (IllegalArgumentException expected) {
            thrown = true;
        }
        require(thrown, why + " throws IllegalArgumentException");
    }

    private static void require(boolean condition, String why) {
        assertions++;
        if (!condition) {
            throw new AssertionError(why);
        }
    }
}
