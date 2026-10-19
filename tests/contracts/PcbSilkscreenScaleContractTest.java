package com.lushprojects.circuitjs1.client;

/** Literal physical-silkscreen projection and ink bounds contract. */
public final class PcbSilkscreenScaleContractTest {
    private static int assertions;

    private PcbSilkscreenScaleContractTest() { }

    public static void main(String[] args) {
        overviewKeepsFractionalPhysicalScale();
        normalAndInspectionScalesProjectInk();
        canvasFrameTransformsTheObstacleOnce();
        boardClipBoundsTheObstacle();
        physicalLabelIdentityAndFacePolicyStayStable();
        System.out.println("PASS: PCB silkscreen scale contracts: " + assertions + " assertions");
    }

    private static void overviewKeepsFractionalPhysicalScale() {
        PcbViewport.Transform view = new PcbViewport.Transform(.0375, 18.25, 8.5, 0, false);
        PcbWorkbenchRenderer.SilkscreenProjection silk =
            PcbWorkbenchRenderer.projectSilkscreen(view, 100, 200, 14,
                -2.5, 17.25, 10.5, 3.25);
        near(silk.baselineX, 22.0, "overview keeps the projected baseline X");
        near(silk.baselineY, 16.0, "overview keeps the projected baseline Y");
        near(silk.projectedFontSize, .525, "overview font remains below one screen pixel");
        rect(silk.inkBounds, 21, 14, 3, 4,
            "overview ink uses fractional scale and conservative raster padding");
    }

    private static void normalAndInspectionScalesProjectInk() {
        PcbWorkbenchRenderer.SilkscreenProjection normal =
            PcbWorkbenchRenderer.projectSilkscreen(
                new PcbViewport.Transform(1.25, 24, -7, 0, false),
                100, 200, 14, -2.5, 17.25, 10.5, 3.25);
        near(normal.baselineX, 149, "normal view baseline X");
        near(normal.baselineY, 243, "normal view baseline Y");
        near(normal.projectedFontSize, 17.5, "normal view scales the original logical font");
        rect(normal.inkBounds, 151, 228, 21, 21,
            "normal view includes negative left bearing and descender");

        PcbWorkbenchRenderer.SilkscreenProjection inspection =
            PcbWorkbenchRenderer.projectSilkscreen(
                new PcbViewport.Transform(6.75, -340, 18, 0, false),
                100, 200, 14, -2.5, 17.25, 10.5, 3.25);
        near(inspection.baselineX, 335, "inspection baseline X");
        near(inspection.baselineY, 1368, "inspection baseline Y");
        near(inspection.projectedFontSize, 94.5, "inspection preserves proportional font scale");
        rect(inspection.inkBounds, 350, 1296, 103, 95,
            "inspection ink covers the full projected ascent and descent");
    }

    private static void canvasFrameTransformsTheObstacleOnce() {
        PcbWorkbenchRenderer.SilkscreenProjection silk =
            PcbWorkbenchRenderer.projectSilkscreen(
                new PcbViewport.Transform(.0375, 18.25, 8.5, 0, false),
                100, 200, 14, -2.5, 17.25, 10.5, 3.25);
        double[] metrics = {0, 0, 0, 0, 0, 2, 0, 0, 3, 11, -5, 1, 1};
        String[] state = {"14px sans-serif", "#ffffff", "start", "alphabetic"};
        Graphics.MeasuredText frame = new Graphics.MeasuredText("provider:frame", "OUT",
            0, 0, 0, 0, 14, metrics, state);
        rect(frame.transformRect(silk.inkBounds), 53, 37, 6, 12,
            "silkscreen obstacle is transformed once into the provider frame");
    }

    private static void boardClipBoundsTheObstacle() {
        Rectangle clipped = PcbWorkbenchRenderer.clipSilkscreenInk(
            new Rectangle(9, 8, 5, 7), new Rectangle(10, 10, 4, 4));
        rect(clipped, 10, 10, 4, 4, "ink obstacle is clipped to the physical board");
        check(PcbWorkbenchRenderer.clipSilkscreenInk(new Rectangle(1, 1, 2, 2),
            new Rectangle(3, 1, 4, 4)) == null,
            "ink touching but outside the board reserves no area");
        check(PcbWorkbenchRenderer.clipSilkscreenInk(new Rectangle(1, 1, 0, 2),
            new Rectangle(0, 0, 4, 4)) == null,
            "empty measured ink reserves no area");
    }

    private static void physicalLabelIdentityAndFacePolicyStayStable() {
        PcbSilkscreenLabel label = new PcbSilkscreenLabel("component:R1", "R1",
            new Rectangle(20, 30, 18, 10), 14, true, "pad:R1.1");
        check(label.getId().equals("component:R1"), "physical label ID remains stable");
        check(label.getText().equals("R1"), "physical label text remains public text");
        check(label.getTargetPadId().equals("pad:R1.1"), "target pad identity remains stable");
        check(label.getBaselineX() == 20 && label.getBaselineY() == 37,
            "stored physical baseline remains unchanged");
        check(label.getFontSize() == 14 && label.isBold(),
            "stored logical font and style remain unchanged");
        check(PcbWorkbenchRenderer.shouldDrawPhysicalSilkscreen(PcbBoardSide.TOP),
            "top face displays physical silkscreen");
        check(!PcbWorkbenchRenderer.shouldDrawPhysicalSilkscreen(PcbBoardSide.BOTTOM),
            "bottom face does not display top-face physical silkscreen");
    }

    private static void rect(Rectangle actual, int x, int y, int width, int height,
            String message) {
        check(actual != null && actual.x == x && actual.y == y &&
            actual.width == width && actual.height == height,
            message + " (expected " + x + "," + y + "," + width + "," + height +
            "; got " + (actual == null ? "null" : actual.x + "," + actual.y + "," +
                actual.width + "," + actual.height) + ")");
    }

    private static void near(double actual, double expected, String message) {
        check(Math.abs(actual - expected) < 0.000001, message +
            " (expected " + expected + "; got " + actual + ")");
    }

    private static void check(boolean condition, String message) {
        if (!condition)
            throw new AssertionError(message);
        assertions++;
    }
}
