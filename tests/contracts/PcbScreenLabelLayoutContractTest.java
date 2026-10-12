package com.lushprojects.circuitjs1.client;

import java.util.Vector;

/** Literal screen-space geometry contract; this does not model browser font metrics. */
public final class PcbScreenLabelLayoutContractTest {
    private static int assertions;

    private PcbScreenLabelLayoutContractTest() { }

    public static void main(String[] args) {
        checkCollisionFreeView("fit", 480, 320, 1, 10);
        checkCollisionFreeView("small", 300, 210, 1, 12);
        checkCollisionFreeView("zoom", 640, 420, 2, 20);
        affinePlacementCoordinatesRoundTrip();
        keepsFullTextWhenNoSlotExists();
        keepsOffscreenOwnerAtNaturalClip();
        System.out.println("PASS: PcbScreenLabelLayoutContractTest: " + assertions + " assertions");
    }

    private static void checkCollisionFreeView(String name, int width, int height,
            int metricScale, int fontSize) {
        Rectangle view = new Rectangle(0, 0, width, height);
        int cx = width / 2, cy = height / 2;
        Vector<PcbScreenLabelLayout.Label> labels = new Vector<PcbScreenLabelLayout.Label>();
        // Each source box is literal screen-space ink geometry, not a browser font claim.
        labels.add(label("ref:Q1", "Q1", PcbScreenLabelLayout.Kind.REFERENCE,
            cx, cy, cx, cy, 18 * metricScale, 8 * metricScale, 2 * metricScale, fontSize));
        labels.add(label("ref:U2", "U2", PcbScreenLabelLayout.Kind.REFERENCE,
            cx, cy, cx, cy, 20 * metricScale, 8 * metricScale, 2 * metricScale, fontSize));
        labels.add(label("net:OUTPUT", "OUTPUT", PcbScreenLabelLayout.Kind.NET,
            cx + metricScale, cy, cx, cy, 48 * metricScale, 8 * metricScale,
            2 * metricScale, fontSize));
        labels.add(label("provider:SENSOR", "SENSOR/REFERENCE",
            PcbScreenLabelLayout.Kind.PROVIDER, cx, cy + metricScale, cx, cy,
            92 * metricScale, 8 * metricScale, 2 * metricScale, fontSize));
        int pinnedWidth = 38 * metricScale;
        int pinnedX = width - pinnedWidth - 6;
        int pinnedY = height - 12;
        labels.add(label("net:pinned", "PINNED", PcbScreenLabelLayout.Kind.NET,
            pinnedX, pinnedY, pinnedX, pinnedY, pinnedWidth, 8 * metricScale,
            2 * metricScale, fontSize));

        Vector<Rectangle> obstacles = new Vector<Rectangle>();
        obstacles.add(new Rectangle(cx - 13 * metricScale, cy - 12 * metricScale,
            26 * metricScale, 25 * metricScale));
        Rectangle board = new Rectangle(24, 24, width - 48, height - 48);
        PcbScreenLabelLayout.Result first = PcbScreenLabelLayout.place(
            labels, obstacles, board, view);
        PcbScreenLabelLayout.Result replay = PcbScreenLabelLayout.place(
            labels, obstacles, board, view);

        check(first.placements.size() == labels.size(), name + " retains each label");
        check(first.allPlaced(), name + " fits without a placement failure");
        check(first.placements.size() == replay.placements.size(), name + " replay count");
        for (int i = 0; i < first.placements.size(); i++) {
            PcbScreenLabelLayout.Placement p = first.placements.elementAt(i);
            PcbScreenLabelLayout.Placement q = find(replay, p.label.text.id);
            check(q != null, name + " replay retains " + p.label.text.id);
            check(p.label.text.text.equals(q.label.text.text), name + " retains full text");
            check(p.screenX == q.screenX && p.screenY == q.screenY,
                name + " deterministic placement for " + p.label.text.id);
            check(inside(view, p.ink), name + " keeps ink in view for " + p.label.text.id);
            for (Rectangle obstacle : obstacles)
                check(!intersects(expand(p.ink, PcbScreenLabelLayout.GAP), obstacle),
                    name + " clears obstacle for " + p.label.text.id);
            for (int j = i + 1; j < first.placements.size(); j++)
                check(!intersects(expand(p.ink, PcbScreenLabelLayout.GAP),
                    first.placements.elementAt(j).ink),
                    name + " separates " + p.label.text.id + " and " +
                        first.placements.elementAt(j).label.text.id);
        }
        PcbScreenLabelLayout.Placement pinned = find(first, "net:pinned");
        check(pinned != null, name + " pinned fixture lookup");
        check(pinned.screenX == pinnedX && pinned.screenY == pinnedY,
            name + " leaves clear pinned text at its original baseline");
    }

    private static void keepsFullTextWhenNoSlotExists() {
        Rectangle view = new Rectangle(0, 0, 24, 18);
        Vector<PcbScreenLabelLayout.Label> labels = new Vector<PcbScreenLabelLayout.Label>();
        labels.add(label("provider:impossible", "FULL TERMINAL NAME",
            PcbScreenLabelLayout.Kind.PROVIDER, 5, 10, 2, 10, 52, 8, 2, 10));
        PcbScreenLabelLayout.Result result = PcbScreenLabelLayout.place(labels,
            new Vector<Rectangle>(), new Rectangle(0, 0, 24, 18), view);
        check(result.placements.size() == 1, "impossible case keeps a placement");
        check(!result.allPlaced() && result.failures.contains("provider:impossible"),
            "impossible case records its unresolved placement");
        PcbScreenLabelLayout.Placement retained = result.placements.elementAt(0);
        check(retained.label.text.text.equals("FULL TERMINAL NAME"),
            "impossible case retains the entire original text");
        check(retained.userX == 2 && retained.userY == 10,
            "impossible case retains the original baseline");
    }

    private static void affinePlacementCoordinatesRoundTrip() {
        double[] ink = {12, 0, 12, 5, 1, 2, 0, 0, 2, 10, 8, 1, 0};
        String[] state = {"10px sans-serif", "#000000", "start", "alphabetic"};
        Graphics.MeasuredText text = new Graphics.MeasuredText("provider:affine",
            "K", 50, 40, 50, 40, 10, ink, state);
        Vector<PcbScreenLabelLayout.Label> labels = new Vector<PcbScreenLabelLayout.Label>();
        labels.add(new PcbScreenLabelLayout.Label(text, PcbScreenLabelLayout.Kind.PROVIDER));
        Rectangle view = new Rectangle(10, 8, 400, 300);
        Vector<Rectangle> obstacles = new Vector<Rectangle>();
        obstacles.add(new Rectangle(109, 77, 26, 14));
        PcbScreenLabelLayout.Result result = PcbScreenLabelLayout.place(labels,
            obstacles, new Rectangle(10, 8, 400, 300), view);
        check(result.allPlaced() && result.placements.size() == 1,
            "affine fixture relocates one label without loss");
        PcbScreenLabelLayout.Placement placement = result.placements.elementAt(0);
        check(placement.leaderFrom != null && placement.leaderTo != null,
            "affine relocation keeps a leader");
        check(projectX(text, placement.userX, placement.userY) == placement.screenX &&
            projectY(text, placement.userX, placement.userY) == placement.screenY,
            "affine user baseline maps back to the selected screen baseline");
        Point from = placement.label.userPoint(placement.leaderFrom.x,
            placement.leaderFrom.y);
        Point to = placement.label.userPoint(placement.leaderTo.x,
            placement.leaderTo.y);
        check(from != null && projectX(text, from.x, from.y) == placement.leaderFrom.x &&
            projectY(text, from.x, from.y) == placement.leaderFrom.y,
            "affine leader origin maps back to its selected screen point");
        check(to != null && projectX(text, to.x, to.y) == placement.leaderTo.x &&
            projectY(text, to.x, to.y) == placement.leaderTo.y,
            "affine leader end maps back to its selected screen point");
    }

    private static void keepsOffscreenOwnerAtNaturalClip() {
        Rectangle view = new Rectangle(0, 0, 120, 64);
        Vector<PcbScreenLabelLayout.Label> labels = new Vector<PcbScreenLabelLayout.Label>();
        labels.add(label("provider:offscreen", "OFFSCREEN TERMINAL",
            PcbScreenLabelLayout.Kind.PROVIDER, -20, 30, -8, 32, 72, 8, 2, 10));
        PcbScreenLabelLayout.Result result = PcbScreenLabelLayout.place(labels,
            new Vector<Rectangle>(), new Rectangle(0, 0, 120, 64), view);
        check(result.placements.size() == 1, "offscreen owner retains a placement");
        check(result.allPlaced(), "offscreen owner is not a viewport placement failure");
        PcbScreenLabelLayout.Placement retained = result.placements.elementAt(0);
        check(retained.label.text.text.equals("OFFSCREEN TERMINAL"),
            "offscreen owner retains full text");
        check(retained.screenX == -8 && retained.screenY == 32,
            "offscreen owner remains at its natural clipped baseline");
        check(retained.leaderFrom == null && retained.leaderTo == null,
            "offscreen owner does not acquire a viewport-gutter leader");
    }

    private static PcbScreenLabelLayout.Label label(String id, String text,
            PcbScreenLabelLayout.Kind kind, int ownerX, int ownerY, int x, int y,
            double width, double ascent, double descent, int fontSize) {
        double[] ink = {width, 0, width, ascent, descent,
            1, 0, 0, 1, 0, 0, 1, 0};
        String[] state = {fontSize + "px sans-serif", "#000000", "start", "alphabetic"};
        return new PcbScreenLabelLayout.Label(new Graphics.MeasuredText(id, text,
            ownerX, ownerY, x, y, fontSize, ink, state), kind);
    }

    private static PcbScreenLabelLayout.Placement find(
            PcbScreenLabelLayout.Result result, String id) {
        for (PcbScreenLabelLayout.Placement p : result.placements)
            if (p.label.text.id.equals(id)) return p;
        return null;
    }

    private static boolean inside(Rectangle a, Rectangle b) {
        return b.x >= a.x && b.y >= a.y && b.x + b.width <= a.x + a.width &&
            b.y + b.height <= a.y + a.height;
    }

    private static boolean intersects(Rectangle a, Rectangle b) {
        return a.x < b.x + b.width && b.x < a.x + a.width &&
            a.y < b.y + b.height && b.y < a.y + a.height;
    }

    private static Rectangle expand(Rectangle r, int amount) {
        return new Rectangle(r.x - amount, r.y - amount,
            r.width + amount * 2, r.height + amount * 2);
    }

    private static int projectX(Graphics.MeasuredText text, int x, int y) {
        return (int)Math.round(text.a * x + text.c * y + text.e);
    }

    private static int projectY(Graphics.MeasuredText text, int x, int y) {
        return (int)Math.round(text.b * x + text.d * y + text.f);
    }

    private static void check(boolean condition, String message) {
        assertions++;
        if (!condition) throw new IllegalStateException(message);
    }
}
