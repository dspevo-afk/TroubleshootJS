package com.lushprojects.circuitjs1.client;

import java.util.Vector;

/** Independent contracts for choosing among existing vacancy origins. */
public final class PcbNearestVacancyPreferenceContractTest {
    private static int assertions;

    private PcbNearestVacancyPreferenceContractTest() { }

    public static void main(String[] args) {
        knownCoordinates();
        overflowSafeDistances();
        boundedInputs();
        System.out.println("PASS: PCB nearest vacancy preference contracts assertions=" + assertions);
    }

    private static void knownCoordinates() {
        Point center = new Point(0, 0);
        Point corner = new Point(90, 90);
        Point reference = new Point(88, 91);
        Vector<Point> origins = new Vector<Point>();
        origins.add(center);
        origins.add(corner);
        Point selected = PcbVacancyTargets.nearest(origins, reference);
        check(selected.equals(new Point(90, 90)), "near corner beats center by Manhattan distance");
        check(selected != corner, "selected origin is returned as a copy");
        check(origins.size() == 2 && origins.get(0) == center && origins.get(1) == corner,
            "selection preserves vector order and point identities");
        check(reference.x == 88 && reference.y == 91 && center.equals(new Point(0, 0)) &&
            corner.equals(new Point(90, 90)), "selection preserves all input coordinates");
        selected.x = -7;
        selected.y = -8;
        check(corner.equals(new Point(90, 90)), "mutating returned copy cannot mutate its source point");

        Vector<Point> centerOrigins = points(new Point(0, 0), new Point(20, 20), new Point(40, 40));
        check(PcbVacancyTargets.nearest(centerOrigins, new Point(19, 22)).equals(new Point(20, 20)),
            "center is selected when it is closest");

        Point earlier = new Point(0, 2);
        Point later = new Point(2, 0);
        Point tied = PcbVacancyTargets.nearest(points(earlier, later), new Point(1, 1));
        check(tied.equals(new Point(0, 2)), "equal distances retain earlier canonical origin");
        check(tied != earlier && tied != later, "tie result aliases neither input point");

        check(PcbVacancyTargets.nearest(points(new Point(-20, -30), new Point(-5, -2)),
            new Point(-6, -3)).equals(new Point(-5, -2)), "negative board coordinates use Manhattan distance");
        check(PcbVacancyTargets.nearest(points(new Point(-10, -10), new Point(-5, -5),
            new Point(1, 0), new Point(5, 5), new Point(10, 10)), new Point(0, 0))
            .equals(new Point(1, 0)), "all five declared origins are accepted and ranked");
    }

    private static void overflowSafeDistances() {
        check(PcbVacancyTargets.nearest(points(new Point(Integer.MAX_VALUE, Integer.MAX_VALUE),
            new Point(1, 1)), new Point(0, 0)).equals(new Point(1, 1)),
            "large positive coordinate distance cannot wrap below a nearby origin");
        check(PcbVacancyTargets.nearest(points(new Point(Integer.MIN_VALUE, Integer.MAX_VALUE),
            new Point(-1, 0)), new Point(0, 0)).equals(new Point(-1, 0)),
            "mixed signed integer extremes cannot wrap below a nearby negative origin");
    }

    private static void boundedInputs() {
        reject(null, new Point(0, 0), "null origin vector is rejected");
        reject(new Vector<Point>(), new Point(0, 0), "empty origin vector is rejected");
        reject(points(new Point(0, 0), new Point(1, 0), new Point(2, 0),
            new Point(3, 0), new Point(4, 0), new Point(5, 0)), new Point(0, 0),
            "more than five origins are rejected");
        Vector<Point> nullEntry = new Vector<Point>();
        nullEntry.add(new Point(0, 0));
        nullEntry.add(null);
        reject(nullEntry, new Point(0, 0), "null origin entry is rejected");
        reject(points(new Point(0, 0)), null, "null reference point is rejected");
    }

    private static Vector<Point> points(Point... values) {
        Vector<Point> result = new Vector<Point>();
        for (Point value : values) result.add(value);
        return result;
    }

    private static void reject(Vector<Point> origins, Point reference, String label) {
        boolean rejected = false;
        try {
            PcbVacancyTargets.nearest(origins, reference);
        } catch (IllegalArgumentException expected) {
            rejected = true;
        }
        check(rejected, label);
    }

    private static void check(boolean condition, String message) {
        assertions++;
        if (!condition) throw new AssertionError(message);
    }
}
