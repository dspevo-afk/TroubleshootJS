package com.lushprojects.circuitjs1.client;

import java.util.Vector;

/** Independent contracts for aligning existing vacancy origins to a global grid. */
public final class PcbVacancyOriginGridContractTest {
    private static int assertions;

    private PcbVacancyOriginGridContractTest() { }

    public static void main(String[] args) {
        literalCounterexampleAndGridOneCompatibility();
        negativeCoordinatesAndGlobalGridTranslations();
        tightFitsAndCanonicalDeduplication();
        rawOriginSetValidationAndPurity();
        independentlyEnumeratedOpenAndBandPockets();
        integerLimitsInvalidInputsAndLegacyRasterCap();
        System.out.println("PASS: PCB vacancy origin grid contracts: " +
                assertions + " assertions");
    }

    private static void literalCounterexampleAndGridOneCompatibility() {
        Rectangle allowed = new Rectangle(0, 0, 200, 240);
        Vector<Rectangle> obstacles = new Vector<Rectangle>();
        obstacles.add(new Rectangle(0, 0, 200, 80));
        Rectangle envelope = new Rectangle(93, 97, 83, 49);
        Point source = new Point(100, 100);

        int[] allowedBefore = values(allowed);
        int[][] obstaclesBefore = values(obstacles);
        int[] envelopeBefore = values(envelope);
        Point sourceBefore = new Point(source);

        Vector<Point> legacy = PcbVacancyTargets.targets(allowed, obstacles,
                envelope, source);
        requirePoints(legacy, new int[][] {
                { 65, 138 }, { 7, 83 }, { 124, 83 }, { 7, 194 }, { 124, 194 }
        }, "literal free pocket yields the existing center and four corners");

        int roundedLegacyTopY = (int) (Math.round(legacy.get(1).y / 10.0) * 10L);
        int legacyEnvelopeTop = roundedLegacyTopY + envelope.y - source.y;
        require(legacy.get(1).y == 83 && roundedLegacyTopY == 80 &&
                legacyEnvelopeTop == 77 && legacyEnvelopeTop < 80,
                "the old top-left origin rounds across the blocked top-band boundary");

        Vector<Point> gridOne = PcbVacancyTargets.targetsOnOriginGrid(
                allowed, obstacles, envelope, source, 1);
        requirePoints(gridOne, new int[][] {
                { 65, 138 }, { 7, 83 }, { 124, 83 }, { 7, 194 }, { 124, 194 }
        }, "grid one preserves the legacy origins exactly");

        Vector<Point> aligned = PcbVacancyTargets.targetsOnOriginGrid(
                allowed, obstacles, envelope, source, 10);
        requirePoints(aligned, new int[][] {
                { 70, 140 }, { 10, 90 }, { 120, 90 }, { 10, 190 }, { 120, 190 }
        }, "grid ten uses the literal center and four aligned pocket corners");
        requireEnvelopeFits(aligned, -7, -3, 83, 49, allowed, obstacles,
                new Rectangle(0, 80, 200, 160),
                "each aligned envelope stays inside the known free pocket");
        require(equal(allowedBefore, values(allowed)),
                "grid alignment preserves the allowed rectangle");
        require(equal(obstaclesBefore, values(obstacles)),
                "grid alignment preserves obstacle values and order");
        require(equal(envelopeBefore, values(envelope)),
                "grid alignment preserves the source envelope");
        require(source.equals(sourceBefore),
                "grid alignment preserves the source origin");
        aligned.get(0).x = -1;
        require(equal(allowedBefore, values(allowed)) &&
                equal(envelopeBefore, values(envelope)) &&
                source.equals(sourceBefore),
                "mutating an output point cannot mutate inputs");
    }

    private static void negativeCoordinatesAndGlobalGridTranslations() {
        Rectangle allowed = new Rectangle(-100, -100, 200, 200);
        Rectangle envelope = new Rectangle(0, 0, 30, 30);
        Point source = new Point(0, 0);
        Vector<Rectangle> obstacles = new Vector<Rectangle>();

        Vector<Point> original = PcbVacancyTargets.targetsOnOriginGrid(
                allowed, obstacles, envelope, source, 10);
        requirePoints(original, new int[][] {
                { -10, -10 }, { -100, -100 }, { 70, -100 },
                { -100, 70 }, { 70, 70 }
        }, "negative half-grid center ties round toward positive coordinates");

        int dx = 20;
        int dy = -30;
        Vector<Point> translated = PcbVacancyTargets.targetsOnOriginGrid(
                translate(allowed, dx, dy), new Vector<Rectangle>(),
                translate(envelope, dx, dy),
                new Point(source.x + dx, source.y + dy), 10);
        requirePoints(translated, new int[][] {
                { 10, -40 }, { -80, -130 }, { 90, -130 },
                { -80, 40 }, { 90, 40 }
        }, "translation by origin-grid multiples preserves the translated result");

        Vector<Point> oneUnitShift = PcbVacancyTargets.targetsOnOriginGrid(
                translate(allowed, 1, 1), new Vector<Rectangle>(),
                translate(envelope, 1, 1),
                new Point(source.x + 1, source.y + 1), 10);
        requirePoints(oneUnitShift, new int[][] {
                { -10, -10 }, { -90, -90 }, { 70, -90 },
                { -90, 70 }, { 70, 70 }
        }, "a non-grid translation reprojects onto global multiples of ten");
        for (int i = 0; i < oneUnitShift.size(); i++) {
            require(oneUnitShift.get(i).x % 10 == 0 &&
                    oneUnitShift.get(i).y % 10 == 0,
                    "every translated output remains on the global grid");
        }
    }

    private static void tightFitsAndCanonicalDeduplication() {
        Vector<Point> exact = PcbVacancyTargets.targetsOnOriginGrid(
                new Rectangle(0, 0, 120, 80), new Vector<Rectangle>(),
                new Rectangle(100, 100, 120, 80), new Point(100, 100), 10);
        requirePoints(exact, new int[][] { { 0, 0 } },
                "an exact pocket fit deduplicates all five legacy slots");

        Vector<Point> oneAxisTight = PcbVacancyTargets.targetsOnOriginGrid(
                new Rectangle(0, 0, 120, 120), new Vector<Rectangle>(),
                new Rectangle(100, 100, 120, 40), new Point(100, 100), 10);
        requirePoints(oneAxisTight, new int[][] {
                { 0, 40 }, { 0, 0 }, { 0, 80 }
        }, "single-width bounds deduplicate in center then TL/TR/BL/BR order");

        Rectangle narrowEnvelope = new Rectangle(99, 0, 116, 80);
        Point narrowSource = new Point(100, 0);
        Vector<Point> rawNarrow = PcbVacancyTargets.targets(
                new Rectangle(0, 0, 120, 80), new Vector<Rectangle>(),
                narrowEnvelope, narrowSource);
        requirePoints(rawNarrow, new int[][] {
                { 3, 0 }, { 1, 0 }, { 5, 0 }
        }, "the narrow fixture has only the independently calculated origins 1 through 5");
        requireEmpty(PcbVacancyTargets.targetsOnOriginGrid(
                new Rectangle(0, 0, 120, 80), new Vector<Rectangle>(),
                narrowEnvelope, narrowSource, 10),
                "a range with no global grid origin returns no candidates");
    }

    private static void independentlyEnumeratedOpenAndBandPockets() {
        Rectangle openAllowed = new Rectangle(0, 0, 120, 80);
        Vector<Rectangle> noObstacles = new Vector<Rectangle>();
        Rectangle openEnvelope = new Rectangle(3, -7, 80, 40);
        Point openSource = new Point(0, 0);
        Vector<Point> openOrigins = enumerateLegalOrigins(openAllowed, noObstacles,
                openEnvelope, openSource, 10, -10, 50, -10, 60);
        require(openOrigins.size() == 16,
                "bounded open-pocket enumeration finds sixteen legal grid origins");
        Vector<Point> openActual = PcbVacancyTargets.targetsOnOriginGrid(
                openAllowed, noObstacles, openEnvelope, openSource, 10);
        int[][] openExpected = new int[][] {
                { 20, 30 }, { 0, 10 }, { 30, 10 }, { 0, 40 }, { 30, 40 }
        };
        requirePoints(openActual, openExpected,
                "open-pocket output has the literal canonical center and corner order");
        requireExpectedAndActualEnumerated(openOrigins, openActual, openExpected,
                "all open-pocket targets are in the independent legal-origin enumeration");

        Rectangle bandAllowed = new Rectangle(0, 0, 160, 120);
        Vector<Rectangle> bandObstacles = new Vector<Rectangle>();
        bandObstacles.add(new Rectangle(0, 0, 160, 40));
        Rectangle bandEnvelope = new Rectangle(2, -3, 60, 40);
        Point bandSource = new Point(0, 0);
        Vector<Point> bandOrigins = enumerateLegalOrigins(bandAllowed, bandObstacles,
                bandEnvelope, bandSource, 10, -20, 120, -20, 130);
        require(bandOrigins.size() == 40,
                "bounded known-band enumeration finds forty legal grid origins");
        Vector<Point> bandActual = PcbVacancyTargets.targetsOnOriginGrid(
                bandAllowed, bandObstacles, bandEnvelope, bandSource, 10);
        int[][] bandExpected = new int[][] {
                { 50, 60 }, { 0, 50 }, { 90, 50 }, { 0, 80 }, { 90, 80 }
        };
        requirePoints(bandActual, bandExpected,
                "known-band output keeps its literal center and TL/TR/BL/BR order");
        requireExpectedAndActualEnumerated(bandOrigins, bandActual, bandExpected,
                "all known-band targets fit the enumerated free region");
    }

    private static void rawOriginSetValidationAndPurity() {
        Point center = new Point(0, 0);
        Point diagonal = new Point(10, 10);
        Vector<Point> raw = new Vector<Point>();
        raw.add(center);
        raw.add(diagonal);

        Vector<Point> centerOutsideBounds = new Vector<Point>();
        centerOutsideBounds.add(new Point(2, 2));
        centerOutsideBounds.add(new Point(2, 0));
        centerOutsideBounds.add(new Point(12, 0));
        centerOutsideBounds.add(new Point(2, 10));
        centerOutsideBounds.add(new Point(12, 10));
        requirePoints(PcbVacancyTargets.originsOnGrid(centerOutsideBounds, 10),
                new int[][] { { 10, 0 }, { 10, 10 } },
                "rounded center clamps to the nearest aligned bound");
        Vector<Point> aligned = PcbVacancyTargets.originsOnGrid(raw, 5);
        requirePoints(aligned, new int[][] {
                { 0, 0 }, { 10, 0 }
        }, "raw set produces canonical outputs without exceeding its two-slot count");
        require(raw.size() == 2 && raw.get(0) == center && raw.get(1) == diagonal,
                "raw alignment preserves vector length, order and point identities");
        require(center.equals(new Point(0, 0)) && diagonal.equals(new Point(10, 10)),
                "raw alignment preserves input coordinates");
        require(aligned.get(0) != center && aligned.get(1) != diagonal,
                "aligned points do not alias raw input points");
        aligned.get(0).x = 99;
        require(center.x == 0 && center.y == 0 && diagonal.x == 10 &&
                diagonal.y == 10,
                "mutating aligned output cannot mutate raw points");

        requireEmpty(PcbVacancyTargets.originsOnGrid(new Vector<Point>(), 10),
                "an empty raw set remains empty");

        rejectRaw(null, 10, "null raw vector is rejected");
        Vector<Point> nullPoint = new Vector<Point>();
        nullPoint.add(new Point(0, 0));
        nullPoint.add(null);
        rejectRaw(nullPoint, 10, "null raw point is rejected");
        Vector<Point> tooMany = new Vector<Point>();
        for (int i = 0; i < 6; i++) {
            tooMany.add(new Point(i * 10, 0));
        }
        rejectRaw(tooMany, 10, "raw vector above five origins is rejected");
        rejectRaw(new Vector<Point>(), 0, "zero raw origin grid is rejected");
        rejectRaw(new Vector<Point>(), -10, "negative raw origin grid is rejected");
    }

    private static void rejectRaw(final Vector<Point> rawOrigins,
            final int grid, String why) {
        reject(new Runnable() {
            public void run() {
                PcbVacancyTargets.originsOnGrid(rawOrigins, grid);
            }
        }, why);
    }
    private static void integerLimitsInvalidInputsAndLegacyRasterCap() {
        int maximum = Integer.MAX_VALUE;
        int minimum = Integer.MIN_VALUE;

        Vector<Point> nearMaximum = PcbVacancyTargets.targetsOnOriginGrid(
                new Rectangle(maximum - 80, 0, 80, 40), new Vector<Rectangle>(),
                new Rectangle(100, 0, 40, 40), new Point(100, 0), 10);
        requirePoints(nearMaximum, new int[][] {
                { 2147483590, 0 }, { 2147483570, 0 }, { 2147483600, 0 }
        }, "positive integer-boundary alignment remains representable");

        Vector<Point> nearMinimum = PcbVacancyTargets.targetsOnOriginGrid(
                new Rectangle(minimum, 0, 80, 40), new Vector<Rectangle>(),
                new Rectangle(100, 0, 40, 40), new Point(100, 0), 10);
        requirePoints(nearMinimum, new int[][] {
                { -2147483630, 0 }, { -2147483640, 0 }, { -2147483610, 0 }
        }, "negative integer-boundary alignment uses checked signed arithmetic");

        Vector<Point> atMaximum = PcbVacancyTargets.targets(
                new Rectangle(maximum - 40, 0, 40, 40), new Vector<Rectangle>(),
                new Rectangle(100, 0, 40, 40), new Point(140, 0));
        requirePoints(atMaximum, new int[][] { { maximum, 0 } },
                "legacy target at Integer.MAX_VALUE is representable before alignment");
        requireEmpty(PcbVacancyTargets.targetsOnOriginGrid(
                new Rectangle(maximum - 40, 0, 40, 40), new Vector<Rectangle>(),
                new Rectangle(100, 0, 40, 40), new Point(140, 0), 10),
                "grid rounding beyond Integer.MAX_VALUE returns no candidate");

        requireEmpty(PcbVacancyTargets.targetsOnOriginGrid(
                new Rectangle(minimum, 0, 40, 40), new Vector<Rectangle>(),
                new Rectangle(100, 0, 40, 40), new Point(100, 0), 10),
                "grid rounding below Integer.MIN_VALUE returns no candidate");

        Vector<Point> hugeGrid = PcbVacancyTargets.targetsOnOriginGrid(
                new Rectangle(-80, -80, 160, 160), new Vector<Rectangle>(),
                new Rectangle(100, 100, 40, 40), new Point(100, 100), maximum);
        requirePoints(hugeGrid, new int[][] { { 0, 0 } },
                "a positive grid up to Integer.MAX_VALUE is handled without overflow");

        requireEmpty(PcbVacancyTargets.targetsOnOriginGrid(
                new Rectangle(maximum - 39, 0, 80, 40), new Vector<Rectangle>(),
                new Rectangle(0, 0, 40, 40), new Point(0, 0), 10),
                "overflowing allowed bounds remain rejected by the legacy target owner");
        requireEmpty(PcbVacancyTargets.targetsOnOriginGrid(
                new Rectangle(0, 0, 80, 40), new Vector<Rectangle>(),
                new Rectangle(maximum - 39, 0, 40, 40),
                new Point(maximum - 39, 0), 10),
                "overflowing source envelope bounds remain rejected");
        Vector<Rectangle> overflowingObstacle = new Vector<Rectangle>();
        overflowingObstacle.add(new Rectangle(maximum - 39, 0, 40, 40));
        requireEmpty(PcbVacancyTargets.targetsOnOriginGrid(
                new Rectangle(0, 0, 80, 40), overflowingObstacle,
                new Rectangle(0, 0, 40, 40), new Point(0, 0), 10),
                "overflowing obstacle bounds remain rejected");

        Vector<Point> atLegacyCellCap = PcbVacancyTargets.targetsOnOriginGrid(
                new Rectangle(0, 0, 8000, 4000), new Vector<Rectangle>(),
                new Rectangle(100, 100, 40, 40), new Point(100, 100), 1);
        requirePoints(atLegacyCellCap, new int[][] {
                { 3980, 1980 }, { 0, 0 }, { 7960, 0 },
                { 0, 3960 }, { 7960, 3960 }
        }, "the legacy 20000-cell raster limit remains inclusive");
        requireEmpty(PcbVacancyTargets.targetsOnOriginGrid(
                new Rectangle(0, 0, 4000, 8040), new Vector<Rectangle>(),
                new Rectangle(100, 100, 40, 40), new Point(100, 100), 1),
                "the legacy raster above 20000 cells remains rejected");

        rejectGrid(new Rectangle(0, 0, 40, 40), new Vector<Rectangle>(),
                new Rectangle(0, 0, 40, 40), new Point(0, 0), 0,
                "wrapper rejects a zero origin grid");

        rejectGrid(new Rectangle(0, 0, 40, 40), new Vector<Rectangle>(),
                new Rectangle(0, 0, 40, 40), new Point(0, 0), -1,
                "negative origin grid is rejected");
        reject(new Runnable() {
            public void run() {
                PcbVacancyTargets.targetsOnOriginGrid(null, new Vector<Rectangle>(),
                        new Rectangle(0, 0, 40, 40), new Point(0, 0), 10);
            }
        }, "null allowed rectangle is rejected");
        reject(new Runnable() {
            public void run() {
                PcbVacancyTargets.targetsOnOriginGrid(new Rectangle(0, 0, 40, 40),
                        null, new Rectangle(0, 0, 40, 40), new Point(0, 0), 10);
            }
        }, "null obstacle vector is rejected");
        reject(new Runnable() {
            public void run() {
                PcbVacancyTargets.targetsOnOriginGrid(new Rectangle(0, 0, 40, 40),
                        new Vector<Rectangle>(), null, new Point(0, 0), 10);
            }
        }, "null source envelope is rejected");
        reject(new Runnable() {
            public void run() {
                PcbVacancyTargets.targetsOnOriginGrid(new Rectangle(0, 0, 40, 40),
                        new Vector<Rectangle>(), new Rectangle(0, 0, 40, 40), null, 10);
            }
        }, "null source origin is rejected");
        Vector<Rectangle> nullObstacle = new Vector<Rectangle>();
        nullObstacle.add(null);
        reject(new Runnable() {
            public void run() {
                PcbVacancyTargets.targetsOnOriginGrid(new Rectangle(0, 0, 40, 40),
                        nullObstacle, new Rectangle(0, 0, 40, 40), new Point(0, 0), 10);
            }
        }, "null obstacle entry is rejected");
    }

    /** Enumerates literal legal origins in a small declared scan window. */
    private static Vector<Point> enumerateLegalOrigins(Rectangle allowed,
            Vector<Rectangle> obstacles, Rectangle envelope, Point sourceOrigin,
            int grid, int minimumX, int maximumX, int minimumY, int maximumY) {
        Vector<Point> result = new Vector<Point>();
        long offsetX = (long) envelope.x - sourceOrigin.x;
        long offsetY = (long) envelope.y - sourceOrigin.y;
        for (int y = minimumY; y <= maximumY; y += grid) {
            for (int x = minimumX; x <= maximumX; x += grid) {
                long envelopeLeft = x + offsetX;
                long envelopeTop = y + offsetY;
                long envelopeRight = envelopeLeft + envelope.width;
                long envelopeBottom = envelopeTop + envelope.height;
                if (!inside(allowed, envelopeLeft, envelopeTop,
                        envelopeRight, envelopeBottom)) {
                    continue;
                }
                boolean overlaps = false;
                for (int i = 0; i < obstacles.size(); i++) {
                    Rectangle obstacle = obstacles.get(i);
                    long obstacleRight = (long) obstacle.x + obstacle.width;
                    long obstacleBottom = (long) obstacle.y + obstacle.height;
                    if (envelopeLeft < obstacleRight && envelopeRight > obstacle.x &&
                            envelopeTop < obstacleBottom && envelopeBottom > obstacle.y) {
                        overlaps = true;
                        break;
                    }
                }
                if (!overlaps) {
                    result.add(new Point(x, y));
                }
            }
        }
        return result;
    }

    private static boolean inside(Rectangle bounds, long left, long top,
            long right, long bottom) {
        return bounds.width > 0 && bounds.height > 0 &&
                left >= bounds.x && top >= bounds.y &&
                right <= (long) bounds.x + bounds.width &&
                bottom <= (long) bounds.y + bounds.height;
    }

    private static void requireExpectedAndActualEnumerated(Vector<Point> enumerated,
            Vector<Point> actual, int[][] expected, String why) {
        for (int i = 0; i < expected.length; i++) {
            require(contains(enumerated, expected[i][0], expected[i][1]),
                    why + " (literal expected point " + i + ")");
        }
        for (int i = 0; i < actual.size(); i++) {
            Point point = actual.get(i);
            require(contains(enumerated, point.x, point.y),
                    why + " (actual point " + i + ")");
        }
    }

    private static void requireEnvelopeFits(Vector<Point> origins, int offsetX,
            int offsetY, int width, int height, Rectangle allowed,
            Vector<Rectangle> obstacles, Rectangle knownPocket, String why) {
        for (int i = 0; i < origins.size(); i++) {
            Point origin = origins.get(i);
            long left = (long) origin.x + offsetX;
            long top = (long) origin.y + offsetY;
            long right = left + width;
            long bottom = top + height;
            require(inside(allowed, left, top, right, bottom) &&
                    inside(knownPocket, left, top, right, bottom),
                    why + " (point " + i + " is within the literal bounds)");
            for (int j = 0; j < obstacles.size(); j++) {
                Rectangle obstacle = obstacles.get(j);
                long obstacleRight = (long) obstacle.x + obstacle.width;
                long obstacleBottom = (long) obstacle.y + obstacle.height;
                require(!(left < obstacleRight && right > obstacle.x &&
                        top < obstacleBottom && bottom > obstacle.y),
                        why + " (point " + i + " avoids every blocked band)");
            }
        }
    }

    private static boolean contains(Vector<Point> points, int x, int y) {
        for (int i = 0; i < points.size(); i++) {
            if (points.get(i).x == x && points.get(i).y == y) {
                return true;
            }
        }
        return false;
    }

    private static void rejectGrid(final Rectangle allowed,
            final Vector<Rectangle> obstacles, final Rectangle envelope,
            final Point origin, final int grid, String why) {
        reject(new Runnable() {
            public void run() {
                PcbVacancyTargets.targetsOnOriginGrid(
                        allowed, obstacles, envelope, origin, grid);
            }
        }, why);
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
        require(actual != null && actual.size() == expected.length,
                why + " (count and maximum five slots)");
        require(actual.size() <= 5, why + " (at most five origins)");
        for (int i = 0; i < expected.length; i++) {
            require(actual.get(i).x == expected[i][0] &&
                    actual.get(i).y == expected[i][1],
                    why + " (point " + i + ": expected " + expected[i][0] + "," +
                    expected[i][1] + " got " + actual.get(i) + ")");
        }
    }

    private static void requireEmpty(Vector<Point> actual, String why) {
        require(actual != null && actual.isEmpty(), why + " returns no candidates");
    }

    private static void reject(Runnable action, String why) {
        boolean rejected = false;
        try {
            action.run();
        } catch (IllegalArgumentException expected) {
            rejected = true;
        }
        require(rejected, why + " throws IllegalArgumentException");
    }

    private static void require(boolean condition, String why) {
        assertions++;
        if (!condition) {
            throw new AssertionError(why);
        }
    }
}