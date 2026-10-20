package com.lushprojects.circuitjs1.client;

import java.util.Vector;

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
        referenceLeadClearanceFixtures();
        emptyChipFootprintsUseOnlyTheDeclaredBody();
        electrolyticMarkingsStayInsideTheCanFace();
        ceramicMarkingsClearTheContactPads();
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

    private static void referenceLeadClearanceFixtures() {
        PhysicalPackage pkg = PhysicalPackages.RAISED_FACTORY_LINK;
        Rectangle localBody = new Rectangle(70,18,80,34);
        Rectangle[] localLeads = {new Rectangle(27,27,51,6), new Rectangle(142,27,51,6)};
        for (PcbRotation rotation : PcbRotation.values())
            for (PcbBoardSide side : PcbBoardSide.values()) {
                PcbPackagePose pose = new PcbPackagePose(1000,2000,rotation,side);
                check(pkg.supportsPose(pose),"raised-link fixture declares this rotation and side");
                PcbComponentPlacement placement = PcbComponentPlacement.fromPhysicalGeometry(
                    "R1",pose,pkg,pkg.getGeometry());
                Rectangle body = P01PhysicalPoseChecks.expectedRectangle(localBody,220,70,
                    1000,2000,rotation,side);
                rect(placement.getBodyBounds(),body.x,body.y,body.width,body.height,
                    "reference fixture uses independently posed literal body geometry");
                for (int lead = 0; lead < localLeads.length; lead++) {
                    Rectangle expected = P01PhysicalPoseChecks.expectedRectangle(localLeads[lead],
                        220,70,1000,2000,rotation,side);
                    Rectangle actual = placement.getLeadBounds(lead);
                    rect(actual,expected.x,expected.y,expected.width,expected.height,
                        "reference fixture lead matches independent literal pose");
                }
                Vector<Rectangle> candidates = referenceCandidates(placement);
                int[][] expected = expectedReferenceCandidates(
                    rotation == PcbRotation.DEG_90 || rotation == PcbRotation.DEG_270);
                check(candidates.size() == expected.length,
                    "reference candidate count follows body orientation and bounded rows");
                for (int index = 0; index < expected.length; index++)
                    rect(candidates.get(index),expected[index][0],expected[index][1],18,18,
                        "reference candidates retain literal canonical order");
            }
        verifyLeadEdge(PcbRotation.DEG_0,PcbBoardSide.TOP,0,localLeads[0],
            new Rectangle(1045,2010,18,18),new Rectangle(1045,2009,18,18));
        verifyLeadEdge(PcbRotation.DEG_0,PcbBoardSide.TOP,1,localLeads[1],
            new Rectangle(1152,2010,18,18),new Rectangle(1152,2009,18,18));
        verifyLeadEdge(PcbRotation.DEG_90,PcbBoardSide.TOP,0,localLeads[0],
            new Rectangle(1020,2045,18,18),new Rectangle(1019,2045,18,18));
        verifyLeadEdge(PcbRotation.DEG_90,PcbBoardSide.BOTTOM,0,localLeads[0],
            new Rectangle(1020,2151,18,18),new Rectangle(1019,2151,18,18));
        assertLegacyReferenceCandidates(referenceFixture(PcbRotation.DEG_0,PcbBoardSide.TOP).placement,
            new int[][] {{1101,1992},{1101,2060},{1044,2026},{1158,2026}});
        assertLegacyReferenceCandidates(referenceFixture(PcbRotation.DEG_90,PcbBoardSide.TOP).placement,
            new int[][] {{1026,2044},{1026,2158},{992,2101},{1060,2101}});
        referenceSilkscreenPlanningFixtures();
    }

    private static void assertLegacyReferenceCandidates(PcbComponentPlacement placement,
            int[][] expected) {
        try {
            Vector<Rectangle> actual = (Vector<Rectangle>)generatorMethod("getReferenceCandidates",
                PcbComponentPlacement.class,int.class,int.class,boolean.class).invoke(
                    new SeededPcbLayoutGenerator(),placement,18,18,false);
            check(actual.size() == expected.length,"legacy reference candidate count is preserved");
            for (int index = 0; index < expected.length; index++)
                rect(actual.get(index),expected[index][0],expected[index][1],18,18,
                    "legacy four-candidate order remains literal");
        } catch (java.lang.ReflectiveOperationException error) {
            throw new AssertionError("legacy reference candidate reflection failed",error);
        }
    }

    private static void referenceSilkscreenPlanningFixtures() {
        ReferenceFixture actual = referenceFixture(PcbRotation.DEG_90,PcbBoardSide.TOP);
        String originalFingerprint = actual.layout.geometryFingerprint();
        check(actual.layout.getSilkscreenLabels().isEmpty(),"wrapper starts with no silkscreen ink");
        ReferenceFixture planned = new ReferenceFixture(actual.board,
            actual.layout.copyForCompaction(),actual.placement,actual.outline);
        Throwable failure = invokeSilkscreen("placeInitialSilkscreen",planned);
        check(failure == null,"legacy label planning succeeds on the physical-only copy");
        check(actual.layout.getSilkscreenLabels().isEmpty() &&
            originalFingerprint.equals(actual.layout.geometryFingerprint()),
            "planning copy leaves the original layout byte-for-byte unchanged");
        Vector<PcbSilkscreenLabel> oldLabels = planned.layout.getSilkscreenLabels();
        check(oldLabels.size() == 2 && planned.layout.getSilkscreenLabel("net:R1.1") == null,
            "fixture plans only its board title and one component reference");
        PcbSilkscreenLabel oldTitle = planned.layout.getSilkscreenLabel("board-title");
        PcbSilkscreenLabel oldReference = planned.layout.getSilkscreenLabel("component:R1");
        rect(oldTitle.getBounds(),1005,1978,153,18,"old title occupies its literal family slot");
        Rectangle oldBounds = oldReference.getBounds();
        rect(oldBounds,1026,2044,18,18,"legacy vertical reference position is literal");
        Rectangle independentLead = new Rectangle(1037,2027,6,51);
        Rectangle independentBody = new Rectangle(1018,2070,34,80);
        check(oldBounds.intersects(independentLead) && !oldBounds.intersects(independentBody),
            "old reference overlaps the independently declared lead and clears the body");
        for (PcbPadPlacement pad : planned.layout.getPads())
            check(!oldBounds.intersects(pad.getPadBounds()),"old reference clears each physical pad");
        rect(planned.layout.getOccupiedContentBounds(),1005,1978,153,237,
            "planned ink preserves the independently unioned old content envelope");
        String plannedFingerprint = planned.layout.geometryFingerprint();

        failure = invokeSilkscreen("placeSilkscreen",actual);
        check(failure == null,"wrapper reflows the lead-colliding reference");
        PcbSilkscreenLabel moved = actual.layout.getSilkscreenLabel("component:R1");
        rect(moved.getBounds(),1016,2044,18,18,"nearest clear reference nudge is literal");
        check(!moved.getBounds().intersects(independentLead) &&
            !moved.getBounds().intersects(independentBody),
            "reflowed reference clears independent lead and body geometry");
        for (PcbPadPlacement pad : actual.layout.getPads())
            check(!moved.getBounds().intersects(pad.getPadBounds()),"reflowed reference clears each pad");
        rect(actual.layout.getOccupiedContentBounds(),1005,1978,153,237,
            "reflow preserves the exact planned occupied envelope");
        check(plannedFingerprint.equals(planned.layout.geometryFingerprint()),
            "final placement leaves the planning copy and its legacy label unchanged");
        check(actual.layout.getSilkscreenLabels().size() == oldLabels.size() &&
            actual.layout.getSilkscreenLabel("net:R1.1") == null,
            "wrapper adds each planned fixture label exactly once");
        for (PcbSilkscreenLabel before : oldLabels) {
            PcbSilkscreenLabel after = actual.layout.getSilkscreenLabel(before.getId());
            Rectangle oldRect = before.getBounds(), newRect = after.getBounds();
            check(after != null && before.getId().equals(after.getId()) &&
                before.getText().equals(after.getText()) && oldRect.width == newRect.width &&
                oldRect.height == newRect.height && before.getFontSize() == after.getFontSize() &&
                before.isBold() == after.isBold() &&
                (before.getTargetPadId() == null ? after.getTargetPadId() == null :
                    before.getTargetPadId().equals(after.getTargetPadId())),
                "reflow preserves label identity, text, size, font, style and target");
            if ("board-title".equals(before.getId()))
                rect(newRect,oldRect.x,oldRect.y,oldRect.width,oldRect.height,
                    "board title bounds remain unchanged");
        }
        check(withoutSilkscreenRecords(actual.layout.geometryFingerprint(),
            actual.layout.getSilkscreenLabels()).equals(originalFingerprint),
            "removing the exact unique label records preserves every other geometry byte");
        rejectSilkscreenWrapper(actual,"populated wrapper rejects a second placement");
        ReferenceFixture sealed = referenceFixture(PcbRotation.DEG_90,PcbBoardSide.TOP);
        sealed.layout.seal();
        rejectSilkscreenWrapper(sealed,"sealed empty layout rejects wrapper placement");
    }

    private static Throwable invokeSilkscreen(String name, ReferenceFixture fixture) {
        try {
            generatorMethod(name,PcbBoardLayout.class,TroubleshootBoard.class,Rectangle.class)
                .invoke(new SeededPcbLayoutGenerator(),fixture.layout,fixture.board,fixture.outline);
            return null;
        } catch (java.lang.reflect.InvocationTargetException error) {
            return error.getCause();
        } catch (java.lang.ReflectiveOperationException error) {
            throw new AssertionError("silkscreen wrapper reflection failed: " + name,error);
        }
    }

    private static void rejectSilkscreenWrapper(ReferenceFixture fixture, String message) {
        String before = fixture.layout.geometryFingerprint();
        Throwable cause = invokeSilkscreen("placeSilkscreen",fixture);
        check(cause instanceof IllegalStateException,message + " with IllegalStateException");
        check(before.equals(fixture.layout.geometryFingerprint()),
            "rejected wrapper call leaves the complete layout fingerprint unchanged");
    }

    private static String withoutSilkscreenRecords(String fingerprint,
            Vector<PcbSilkscreenLabel> labels) {
        String result = fingerprint;
        for (PcbSilkscreenLabel label : labels) {
            Rectangle bounds = label.getBounds();
            String record = "L:" + label.getId() + "@" + label.getText() + ":" +
                bounds.x + "," + bounds.y + "," + bounds.width + "," + bounds.height + ";";
            int start = result.indexOf(record);
            check(start >= 0 && result.indexOf(record,start + record.length()) < 0,
                "each label has one exact, uniquely framed geometry record");
            result = result.substring(0,start) + result.substring(start + record.length());
        }
        return result;

    private static int[][] expectedReferenceCandidates(boolean vertical) {
        if (vertical) return new int[][] {{1026,2044},{1026,2158},{992,2101},{1060,2101}};
        return new int[][] {{1101,1992},{1091,1992},{1111,1992},{1081,1992},
            {1121,1992},{1071,1992},{1131,1992},{1101,2060},{1091,2060},
            {1111,2060},{1081,2060},{1121,2060},{1071,2060},{1131,2060},
            {1044,2026},{1158,2026}};
    }

    private static Vector<Rectangle> referenceCandidates(PcbComponentPlacement placement) {
        try {
            return (Vector<Rectangle>)generatorMethod("getReferenceCandidates",
                PcbComponentPlacement.class,int.class,int.class).invoke(new SeededPcbLayoutGenerator(),
                    placement,18,18);
        } catch (java.lang.ReflectiveOperationException error) {
            throw new AssertionError("reference candidate reflection failed",error);
        }
    }

    private static void verifyLeadEdge(PcbRotation rotation, PcbBoardSide side, int leadIndex,
            Rectangle localLead, Rectangle overlap, Rectangle edge) {
        ReferenceFixture fixture = referenceFixture(rotation,side);
        Rectangle expectedLead = P01PhysicalPoseChecks.expectedRectangle(localLead,220,70,
            1000,2000,rotation,side);
        rect(fixture.placement.getLeadBounds(leadIndex),expectedLead.x,expectedLead.y,
            expectedLead.width,expectedLead.height,"selected lead matches its independent literal pose");
        check(overlap.intersects(expectedLead),"overlap fixture crosses the placed lead");
        check(!overlap.intersects(fixture.placement.getBodyBounds()),
            "lead overlap fixture clears the component body");
        for (PcbPadPlacement pad : fixture.layout.getPads())
            check(!overlap.intersects(pad.getPadBounds()),"lead overlap fixture clears every pad");
        check(!edge.intersects(expectedLead),"edge fixture only touches the lead boundary");
        check(!referenceLabelPositionFree(fixture,overlap),
            "reference chooser rejects ink over a placed lead");
        for (String id : new String[] {"board-title","component:R1","net:R1.1"})
            rejectReferenceLabel(referenceFixture(rotation,side),overlap,id);
        check(referenceLabelPositionFree(fixture,edge),
            "reference chooser accepts ink exactly at the lead edge");
    }

    private static ReferenceFixture referenceFixture(PcbRotation rotation, PcbBoardSide side) {
        PhysicalPackage pkg = PhysicalPackages.RAISED_FACTORY_LINK;
        PcbPackagePose pose = new PcbPackagePose(1000,2000,rotation,side);
        check(pkg.supportsPose(pose),"clearance fixture pose is declared by the package");
        PcbComponentPlacement placement = PcbComponentPlacement.fromPhysicalGeometry("R1",
            pose,pkg,pkg.getGeometry());
        Rectangle outline = new Rectangle(900,1900,500,600);
        PcbBoardLayout layout = new PcbBoardLayout(2400,3000,outline,
            new Rectangle(1800,2600,300,200));
        layout.addComponent(placement);
        TroubleshootBoard board = new TroubleshootBoard("lead-reference-fixture");
        board.addComponent(new BoardComponent("R1","R1",pkg));
        Vector<String> terminals = pkg.getTerminalIds();
        for (int index = 0; index < terminals.size(); index++) {
            String terminal = terminals.get(index), net = "N" + index, id = "R1." + terminal;
            board.addNet(new BoardNet(net));
            board.addPad(new BoardPad(id,"R1",terminal,net));
            Point center = placement.getPadPoint(index);
            layout.addPad(new PcbPadPlacement(id,center.x,center.y,0,0,0,
                placement.getPadBounds(index),placement.getBoardPadProbeBounds(index),
                PcbTerminalAttachment.PLATED_THROUGH_HOLE,side));
        }
        return new ReferenceFixture(board,layout,placement,outline);
    }

    private static boolean referenceLabelPositionFree(ReferenceFixture fixture,
            Rectangle bounds) {
        try {
            return ((Boolean)generatorMethod("isLabelPositionFree",PcbBoardLayout.class,
                TroubleshootBoard.class,Rectangle.class,Rectangle.class).invoke(
                    new SeededPcbLayoutGenerator(),fixture.layout,fixture.board,fixture.outline,
                    bounds)).booleanValue();
        } catch (java.lang.ReflectiveOperationException error) {
            throw new AssertionError("reference clearance reflection failed",error);
        }
    }

    private static void rejectReferenceLabel(ReferenceFixture fixture, Rectangle bounds,
            String id) {
        try {
            generatorMethod("addLabel",PcbBoardLayout.class,TroubleshootBoard.class,
                Rectangle.class,String.class,String.class,Rectangle.class,int.class,
                boolean.class,String.class).invoke(new SeededPcbLayoutGenerator(),fixture.layout,
                    fixture.board,fixture.outline,id,
                    "board-title".equals(id) ? "FIXTURE" : "net:R1.1".equals(id) ? "GND" : "R1",
                    bounds,"net:R1.1".equals(id) ? 12 : 14,
                    !"net:R1.1".equals(id),"net:R1.1".equals(id) ? "R1.1" : null);
            throw new AssertionError("lead-overlap reference label was accepted");
        } catch (java.lang.reflect.InvocationTargetException error) {
            Throwable cause = error.getCause();
            check(cause instanceof IllegalStateException,
                "reflected rejection preserves IllegalStateException as its cause");
            check(cause instanceof IllegalStateException &&
                ("PCB silkscreen label position became occupied: " + id).equals(
                    cause.getMessage()),"lead-overlap rejection has its precise error for " + id);
            check(fixture.layout.getSilkscreenLabel(id) == null,
                "rejected label leaves the layout unchanged: " + id);
        } catch (java.lang.ReflectiveOperationException error) {
            throw new AssertionError("reference rejection reflection failed",error);
        }
    }

    private static java.lang.reflect.Method generatorMethod(String name, Class<?>... parameters) {
        try {
            java.lang.reflect.Method method = SeededPcbLayoutGenerator.class.getDeclaredMethod(
                name,parameters);
            method.setAccessible(true);
            return method;
        } catch (NoSuchMethodException error) {
            throw new AssertionError("missing private reference-label method: " + name,error);
        }
    }

    private static final class ReferenceFixture {
        final TroubleshootBoard board;
        final PcbBoardLayout layout;
        final PcbComponentPlacement placement;
        final Rectangle outline;
        ReferenceFixture(TroubleshootBoard board, PcbBoardLayout layout,
                PcbComponentPlacement placement, Rectangle outline) {
            this.board = board; this.layout = layout; this.placement = placement; this.outline = outline;
        }
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

    private static void electrolyticMarkingsStayInsideTheCanFace() {
        rect(WorkbenchVisualTheme.electrolyticMarkingBounds(new Rectangle(100,200,80,80)),
            127,230,26,20,"literal round can uses a centered narrow text band");
        rect(WorkbenchVisualTheme.electrolyticMarkingBounds(new Rectangle(100,200,81,79)),
            127,230,27,19,"odd projected dimensions retain the original exact center");
        rect(WorkbenchVisualTheme.electrolyticMarkingBounds(new Rectangle(-30,-80,40,40)),
            -16,-65,12,10,"panned negative screen coordinates retain the same inset");
        check(WorkbenchVisualTheme.electrolyticMarkingBounds(null) == null &&
                WorkbenchVisualTheme.electrolyticMarkingBounds(new Rectangle(0,0,0,40)) == null &&
                WorkbenchVisualTheme.electrolyticMarkingBounds(new Rectangle(0,0,35,35)) == null,
            "missing, empty and undersized bands omit ink rather than crop a marking");
        for (int width = 1; width <= 256; width++) {
            int[] heights = {width, width+1, Math.max(1,width-1), width*2, Math.max(1,width/2)};
            for (int height : heights) {
                Rectangle body = new Rectangle(-31,47,width,height);
                Rectangle marking = WorkbenchVisualTheme.electrolyticMarkingBounds(body);
                check(body.x == -31 && body.y == 47 && body.width == width && body.height == height,
                    "text fitting never changes the caller's physical body rectangle");
                if (marking == null) continue;
                check(marking.width >= 12 && marking.height >= 9 &&
                        marking.x >= body.x && marking.y >= body.y &&
                        marking.x + marking.width <= body.x + body.width &&
                        marking.y + marking.height <= body.y + body.height,
                    "returned band preserves the existing full-text fitter minimum and body containment");
                check(marking.x * 2 + marking.width == body.x * 2 + body.width &&
                        marking.y * 2 + marking.height == body.y * 2 + body.height,
                    "symmetric integer insets retain the exact projected center");
                // Independent ellipse equation, using the renderer's unchanged drawn rim.
                int rim = Math.max(1, Math.min(body.width, body.height)/10);
                double radiusX = (body.width - rim*2)/2.0;
                double radiusY = (body.height - rim*2)/2.0;
                int diskInset = Math.max(2, Math.min(body.width, body.height)/4);
                double diskRadiusX = (body.width - diskInset*2)/2.0;
                double diskRadiusY = (body.height - diskInset*2)/2.0;
                for (int x : new int[] {marking.x,marking.x+marking.width})
                    for (int y : new int[] {marking.y,marking.y+marking.height}) {
                        double dx = (x-body.x-body.width/2.0)/radiusX;
                        double dy = (y-body.y-body.height/2.0)/radiusY;
                        check(dx*dx+dy*dy < 1.0,
                            "every text-band corner lies strictly inside the can face, clear of the rim");
                        double diskX = (x-body.x-body.width/2.0)/diskRadiusX;
                        double diskY = (y-body.y-body.height/2.0)/diskRadiusY;
                        check(diskX*diskX+diskY*diskY < 1.0,
                            "the complete text band also fits within the existing central metal disk");
                    }
            }
        }
    }

    private static void ceramicMarkingsClearTheContactPads() {
        PhysicalPackageGeometry ceramic = PhysicalPackages.RADIAL_CERAMIC_CAPACITOR.getGeometry();
        rect(ceramic.getBodyBounds(),15,8,50,53,"literal ceramic physical body remains unchanged");
        rect(ceramic.getTerminal(0).getPadBounds(),7,17,26,26,"literal first ceramic land");
        rect(ceramic.getTerminal(1).getPadBounds(),47,17,26,26,"literal second ceramic land");
        rect(WorkbenchVisualTheme.ceramicMarkingBounds(new Rectangle(15,8,50,52),
            new Rectangle(7,17,26,26),new Rectangle(47,17,26,26)),
            28,44,24,10,"complete marking uses the wider clear lower disc area");
        rect(WorkbenchVisualTheme.ceramicMarkingBounds(new Rectangle(30,16,100,104),
            new Rectangle(14,34,52,52),new Rectangle(94,34,52,52)),
            55,87,50,22,"doubled screen projection retains contact clearance");
        rect(WorkbenchVisualTheme.ceramicMarkingBounds(new Rectangle(0,0,104,100),
            new Rectangle(34,14,52,52),new Rectangle(34,94,52,52)),
            26,67,52,23,"vertical contact arrangement retains a horizontal clear band");
        check(WorkbenchVisualTheme.ceramicMarkingBounds(null,new Rectangle(1,1,2,2),
                new Rectangle(4,1,2,2)) == null &&
            WorkbenchVisualTheme.ceramicMarkingBounds(new Rectangle(0,0,50,52),null,
                new Rectangle(4,1,2,2)) == null &&
            WorkbenchVisualTheme.ceramicMarkingBounds(new Rectangle(0,0,8,8),
                new Rectangle(1,1,2,2),new Rectangle(4,1,2,2)) == null &&
            WorkbenchVisualTheme.ceramicMarkingBounds(new Rectangle(0,0,50,52),
                new Rectangle(0,0,50,52),new Rectangle(0,0,50,52)) == null,
            "missing, tiny or fully blocked ceramic faces omit the complete marking");
        double[] scales = {.25,.5,1,1.25,1.5,2,3,4,7.5};
        int painted = 0;
        // These synthetic cardinal rectangles exercise ink fitting, not new allowed package poses.
        for (PcbRotation rotation : PcbRotation.values())
            for (PcbBoardSide side : PcbBoardSide.values())
                for (double scale : scales) {
                    PcbPackagePose pose = new PcbPackagePose(-170,230,rotation,side);
                    PcbViewport.Transform view = new PcbViewport.Transform(scale,-21,17,0,false);
                    Rectangle projected = view.project(pose.toBoardRectangle(ceramic.getBodyBounds(),90,90));
                    Rectangle body = new Rectangle(projected.x,projected.y,
                        projected.width/2*2,projected.height/2*2);
                    Rectangle a = view.project(pose.toBoardRectangle(ceramic.getTerminal(0).getPadBounds(),90,90));
                    Rectangle b = view.project(pose.toBoardRectangle(ceramic.getTerminal(1).getPadBounds(),90,90));
                    Rectangle originalBody = new Rectangle(body), originalA = new Rectangle(a), originalB = new Rectangle(b);
                    Rectangle marking = WorkbenchVisualTheme.ceramicMarkingBounds(body,a,b);
                    rect(body,originalBody.x,originalBody.y,originalBody.width,originalBody.height,"body input is immutable");
                    rect(a,originalA.x,originalA.y,originalA.width,originalA.height,"first pad input is immutable");
                    rect(b,originalB.x,originalB.y,originalB.width,originalB.height,"second pad input is immutable");
                    Rectangle reversed = WorkbenchVisualTheme.ceramicMarkingBounds(body,b,a);
                    if (marking == null) {
                        check(reversed == null,"omission is independent of terminal enumeration");
                        continue;
                    }
                    painted++;
                    rect(reversed,marking.x,marking.y,marking.width,marking.height,"terminal order cannot move printed ink");
                    check(marking.width >= 12 && marking.height >= 9,"unchanged full-string fitter minimum");
                    for (Rectangle pad : new Rectangle[] {a,b})
                        check(marking.x+marking.width <= pad.x-1 || marking.x >= pad.x+pad.width+1 ||
                            marking.y+marking.height <= pad.y-1 || marking.y >= pad.y+pad.height+1,
                            "every chosen text band has an independent one-pixel pad exclusion");
                    for (int x : new int[] {marking.x,marking.x+marking.width})
                        for (int y : new int[] {marking.y,marking.y+marking.height}) {
                            double dx = (x-(body.x+body.width/2.0))/((body.width-2)/2.0);
                            double dy = (y-(body.y+(body.height-1)/2.0))/((body.height-3)/2.0);
                            check(dx*dx+dy*dy < 1.0,"every corner stays strictly inside the already painted inner ellipse");
                        }
                }
        check(painted >= 32,"representative fitted arrangements retain real printable bands");
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
