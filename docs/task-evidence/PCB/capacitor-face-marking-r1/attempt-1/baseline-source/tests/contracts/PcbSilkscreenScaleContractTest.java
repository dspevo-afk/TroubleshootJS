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
        emptyChipFootprintsUseOnlyTheDeclaredBody();
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

    private static void emptyChipFootprintsUseOnlyTheDeclaredBody() {
        PhysicalPackage[] packages = {PhysicalPackages.TO220_REGULATOR_4,
            PhysicalPackages.E04_DECISION_CONTROL_5, PhysicalPackages.OPTOCOUPLER_4,
            PhysicalPackages.ISOLATED_CONVERTER_7};
        Rectangle[] bodies = {new Rectangle(25,25,170,75), new Rectangle(25,25,210,75),
            new Rectangle(90,25,160,140), new Rectangle(90,25,240,270)};
        for (int i = 0; i < packages.length; i++) {
            PhysicalPackage pkg = packages[i];
            PhysicalPackageGeometry geometry = pkg.getGeometry();
            PhysicalBoardSlot slot = emptySlot(pkg, "U1");
            PcbComponentPlacement canonical = PcbComponentPlacement.fromPhysicalGeometry(
                "U1", 700, 900, pkg, geometry);
            check(PcbWorkbenchRenderer.emptyChipFootprint(canonical, slot, PcbBoardSide.TOP) == null,
                "an unbound slot does not invent assembly ink");
            slot.bindGeometryRealization(canonical);
            for (PcbRotation rotation : PcbRotation.values())
                for (PcbBoardSide mount : PcbBoardSide.values()) {
                    PcbPackagePose pose = new PcbPackagePose(700,900,rotation,mount);
                    if (!pkg.supportsPose(pose)) {
                        try {
                            PcbComponentPlacement.fromPhysicalGeometry("U1",pose,pkg,geometry);
                            throw new AssertionError("package-disallowed pose accepted");
                        } catch (IllegalArgumentException expected) {
                            check(true,"assembly fixture preserves package pose restrictions");
                        }
                        continue;
                    }
                    PcbComponentPlacement placement = PcbComponentPlacement.fromPhysicalGeometry(
                        "U1", pose, pkg, geometry);
                    Rectangle actual = PcbWorkbenchRenderer.emptyChipFootprint(
                        placement, slot, PcbBoardSide.TOP);
                    if (mount == PcbBoardSide.TOP) {
                        Rectangle expected = P01PhysicalPoseChecks.expectedRectangle(bodies[i],
                            geometry.getWidth(),geometry.getHeight(),700,900,rotation,mount);
                        rect(actual, expected.x, expected.y, expected.width, expected.height,
                            "empty chip ink uses independently posed literal body, never courtyard");
                        actual.x = -999;
                        rect(PcbWorkbenchRenderer.emptyChipFootprint(placement,slot,PcbBoardSide.TOP),
                            expected.x,expected.y,expected.width,expected.height,
                            "returned footprint cannot mutate the canonical placement");
                    } else check(actual == null, "top silk does not mark a bottom-mounted body");
                    check(PcbWorkbenchRenderer.emptyChipFootprint(placement,slot,PcbBoardSide.BOTTOM) == null,
                        "top-only assembly ink never appears on bottom copper");
                }
            check(PcbWorkbenchRenderer.emptyChipFootprint(null,slot,PcbBoardSide.TOP) == null &&
                PcbWorkbenchRenderer.emptyChipFootprint(canonical,null,PcbBoardSide.TOP) == null &&
                PcbWorkbenchRenderer.emptyChipFootprint(canonical,slot,null) == null,
                "missing placement, slot or face emits no footprint");
            PhysicalBoardSlot foreign = emptySlot(pkg,"U2");
            foreign.bindGeometryRealization(PcbComponentPlacement.fromPhysicalGeometry(
                "U2",700,900,pkg,geometry));
            check(PcbWorkbenchRenderer.emptyChipFootprint(canonical,foreign,PcbBoardSide.TOP) == null,
                "same package in another component slot does not certify this footprint");
        }
        PhysicalBoardSlot axial = emptySlot(PhysicalPackages.AXIAL_RESISTOR,"R1");
        PcbComponentPlacement resistor = PcbComponentPlacement.fromPhysicalGeometry("R1",700,900,
            PhysicalPackages.AXIAL_RESISTOR,PhysicalPackages.AXIAL_RESISTOR.getGeometry());
        axial.bindGeometryRealization(resistor);
        check(PcbWorkbenchRenderer.emptyChipFootprint(resistor,axial,PcbBoardSide.TOP) == null,
            "nonrectangular axial parts do not acquire a misleading rectangular silhouette");
        PhysicalBoardSlot wrongPackage = emptySlot(PhysicalPackages.OPTOCOUPLER_4,"U1");
        wrongPackage.bindGeometryRealization(PcbComponentPlacement.fromPhysicalGeometry("U1",700,900,
            PhysicalPackages.OPTOCOUPLER_4,PhysicalPackages.OPTOCOUPLER_4.getGeometry()));
        check(PcbWorkbenchRenderer.emptyChipFootprint(PcbComponentPlacement.fromPhysicalGeometry(
            "U1",700,900,PhysicalPackages.TO220_REGULATOR_4,
            PhysicalPackages.TO220_REGULATOR_4.getGeometry()),wrongPackage,PcbBoardSide.TOP) == null,
            "same component ID with a different bound package fails closed");
    }

    private static PhysicalBoardSlot emptySlot(PhysicalPackage pkg, String id) {
        TroubleshootBoard board = new TroubleshootBoard("assembly-ink-fixture");
        board.addComponent(new BoardComponent(id,"fixture",pkg));
        int index = 0;
        for (String terminal : pkg.getTerminalIds()) {
            String net = "N" + index++;
            board.addNet(new BoardNet(net));
            board.addPad(new BoardPad(id+"."+terminal,id,terminal,net));
        }
        return new PhysicalBoardSlot(board,id);
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
