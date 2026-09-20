package com.lushprojects.circuitjs1.client;

/** Independent progress, relay transition and three-pin alignment contracts. */
public final class VisualWorkbenchContractTest {
    private static int assertions;
    private static void check(boolean value, String label) {
        assertions++; if (!value) throw new AssertionError(label);
    }
    public static void main(String[] args) {
        check(GenerationProgress.percent(null, 0, 0, false) == 0, "not started is zero");
        int prior = -1;
        for (GenerationJob.Stage stage : GenerationJob.Stage.values()) {
            int percent = GenerationProgress.percent(stage, 0, 100, false);
            check(percent >= prior && percent < 100, "stage-based progress is monotonic and unfinished"); prior = percent;
            check(GenerationProgress.label(stage).length() > 0, "each stage has a public label");
        }
        check(GenerationProgress.percent(GenerationJob.Stage.HYPOTHESES, 50, 100, false) == 58, "real half-completed hypothesis work");
        check(GenerationProgress.percent(GenerationJob.Stage.HYPOTHESES, 1000, 100, false) < 67, "work estimate cannot cross next stage");
        check(GenerationProgress.percent(GenerationJob.Stage.PUBLISH, 0, 0, true) == 100, "only completed publication reaches 100");
        WorkbenchRelayFeedback feedback = new WorkbenchRelayFeedback(); Object owner = new Object(), part = new Object();
        check(feedback.observe(owner, part, 0) == -1, "initial contact is silent");
        check(feedback.observe(owner, part, 1) == 1, "real pickup edge");
        check(feedback.observe(owner, part, 1) == -1, "no repeated pickup click");
        check(feedback.observe(owner, part, 2) == -1, "intermediate mechanics are not a contact edge");
        check(feedback.observe(owner, part, 0) == 0, "real release edge");
        check(feedback.observe(new Object(), part, 1) == -1, "new owner cannot replay old edge");
        feedback.clear(); check(feedback.observe(owner, part, 1) == -1, "private proof cannot manufacture sound");
        ForegroundGenerationClock clock = new ForegroundGenerationClock(1000, false);
        check(clock.elapsedMillis(1200) == 200, "foreground time consumes the existing budget");
        clock.setPaused(1300, true);
        check(clock.isPaused() && clock.elapsedMillis(100000) == 300, "hidden idle time consumes no foreground budget");
        clock.setPaused(101000, false);
        check(clock.elapsedMillis(101700) == 1000, "resuming does not reset prior work time");
        clock.setPaused(102000, true); clock.setPaused(102500, true);
        clock.setPaused(202000, false);
        check(clock.elapsedMillis(202100) == 1400, "repeated visibility events preserve cumulative accounting");
        boolean rejected=false;
        try { clock.nowMillis(202000); } catch (IllegalStateException expected) { rejected=true; }
        check(rejected, "a regressing wall clock is not an extended deadline");
        ForegroundGenerationClock initiallyHidden = new ForegroundGenerationClock(0, true);
        check(initiallyHidden.elapsedMillis(1000000)==0, "background launch waits before spending its budget");
        initiallyHidden.setPaused(1000001, false);
        check(initiallyHidden.elapsedMillis(1090001)==90000, "the full 90-second budget still expires");
        alignment(PhysicalPackages.TO92_NPN); alignment(PhysicalPackages.TO92_NMOS);
        drawer();
        System.out.println("PASS: visual workbench contracts assertions=" + assertions);
    }
    private static void drawer() {
        PartsTrayViewport tray = new PartsTrayViewport();
        for (int width : new int[] {320, 800, 1024, 1440}) {
            tray.resize(width, 700, 40); tray.set(false, 0);
            check(!tray.contains(10, 699) && !tray.visible(0), "closed drawer contributes no hit targets");
            check(tray.set(true, 0), "bottom hover opens drawer");
            check(tray.bounds().width == width && tray.bounds().y == 528, "full-width bottom drawer independent of board camera");
            check(tray.visible(0) && !tray.visible(39), "horizontal visible inventory window");
            check(tray.contains(10, 699) && !tray.contentContains(10, 699), "scrollbar is not a probe target");
            check(!tray.contains(10, 527), "board outside drawer is not intercepted");
            for (PhysicalPackage pkg : new PhysicalPackage[] {PhysicalPackages.AXIAL_RESISTOR,
                    PhysicalPackages.THROUGH_HOLE_CONNECTOR_2, PhysicalPackages.RELAY_SPDT,
                    PhysicalPackages.TO92_NMOS, PhysicalPackages.RADIAL_CERAMIC_CAPACITOR}) {
                LoosePartPose pose = LoosePartPose.forCell(pkg, null, tray.cell(0));
                Rectangle cell = tray.cell(0), body = pose.getSelectionEnvelope();
                check(body.x >= cell.x && body.y >= cell.y && body.x+body.width <= cell.x+cell.width &&
                    body.y+body.height <= cell.y+cell.height, "actual source package fits its horizontal cell");
                for (int i=0;i<pkg.getTerminalCount();i++) {
                    Point p = pose.getTerminalPoint(i);
                    check(p.x >= cell.x && p.x <= cell.x+cell.width && p.y >= cell.y && p.y <= cell.y+cell.height,
                        "real loose terminal stays in its cell");
                }
            }
            tray.set(true, Integer.MAX_VALUE);
            check(tray.scroll() == tray.contentWidth()-width && tray.visible(39) && !tray.visible(0), "scroll clamps and reaches the last item");
            tray.resize(width, 700, 1);
            check(tray.scroll() == 0 && tray.contentWidth() == width, "inventory shrink clears stale scroll offset");
            tray.set(false, -1000); check(tray.scroll()==0 && !tray.visible(0), "close revokes visible inventory");
        }
    }
    private static void alignment(PhysicalPackage pkg) {
        for (PhysicalPackage.GeometryVariant variant : pkg.getGeometryVariants()) {
            PhysicalPackageGeometry geometry = variant.getGeometry();
            Point a = geometry.getTerminal(0).getPadCenter();
            Point b = geometry.getTerminal(1).getPadCenter();
            Point c = geometry.getTerminal(2).getPadCenter();
            Rectangle body = geometry.getBodyBounds(), keepOut = geometry.getBodyKeepOut();
            check(a.y == b.y && b.y == c.y, "three holes are collinear");
            check(a.x + c.x == 2 * b.x, "middle hole is centered");
            check(2 * body.x + body.width == 2 * b.x, "body centers over the hole row");
            check(2 * keepOut.x + keepOut.width == 2 * b.x, "keepout centers over the hole row");
        }
    }
}
