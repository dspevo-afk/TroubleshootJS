package com.lushprojects.circuitjs1.client;

import java.util.Vector;

/** Developer-only proof for the board/layout half of Task 43R-2. */
final class PcbR2DeveloperVerifier {
    private static final int EXPECTED_GEOMETRY_CONTRACT_VERSION = 3;
    private static final long[] REPRESENTATIVE_SEEDS = { 0, 2, 3 };

    private PcbR2DeveloperVerifier() { }

    static void verify() {
        verifyGeometryContractVersion();
        PcbProductionEscapeDeveloperVerifier.verify();
        verifyPositiveCanaries();
        verifyNegativeCanaries();
        verifyGeneratedLayouts();
    }

    private static void verifyGeometryContractVersion() {
        require(PcbGeometryContractVersion.CURRENT == EXPECTED_GEOMETRY_CONTRACT_VERSION &&
                PcbGeometryContractVersion.CURRENT_VALUE ==
                    EXPECTED_GEOMETRY_CONTRACT_VERSION,
            "R-2 geometry contract version is not 3");
        Vector<PhysicalPackage> packages =
            StandardPcbFootprintProviders.createRegistry().getRegisteredPackages();
        for (PhysicalPackage physicalPackage : packages) {
            if (physicalPackage.isDeveloperGeneric())
                continue;
            require(!physicalPackage.getGeometry().isDeveloperGeneric() &&
                    physicalPackage.getGeometryContractVersionValue() ==
                        EXPECTED_GEOMETRY_CONTRACT_VERSION &&
                    physicalPackage.acceptsGeometry(physicalPackage.getGeometry()),
                "Production package is not backed by current geometry: " +
                    physicalPackage.getId());
        }
    }

    private static void verifyPositiveCanaries() {
        verifyTwoPadNet();
        verifyThreePadBranchedNet();
        verifySameNetTeeAndCollinearReuse();
        verifySameNetPerpendicularTouch();
        verifySameNetPadContact();
        verifyTransitivePackageInternalConnectivity();
        verifyCompactionAndRealizationPropagation();
        verifyFullWidthCopperAtEdge();
    }

    private static void verifyTwoPadNet() {
        Fixture fixture = buildTwoPadFixture(false, 0);
        fixture.layout.validateGeometry(fixture.board);
        require(fixture.layout.getTraces().size() == 1 &&
                "R1.1".equals(fixture.layout.getTraces().get(0).getStartPadId()) &&
                "R1.2".equals(fixture.layout.getTraces().get(0).getEndPadId()),
            "R-2 valid two-pad net canary did not retain its exact pad endpoints");
        PcbComponentPlacement placement = fixture.layout.getComponent("R1");
        require(placement != null && placement.getPhysicalPackage() ==
                PhysicalPackages.AXIAL_RESISTOR &&
                !placement.getPhysicalPackage().isDeveloperGeneric() &&
                placement.getPhysicalGeometry() ==
                    PhysicalPackages.AXIAL_RESISTOR.getGeometry(),
            "R-2 two-pad canary is not a package-backed production footprint");
    }

    private static void verifyThreePadBranchedNet() {
        Fixture fixture = buildThreePadBranchFixture();
        fixture.layout.validateGeometry(fixture.board);
        require(fixture.layout.getTraces().size() == 2 &&
                fixture.layout.getTraces().get(0).getNetId().equals("BRANCH_NET") &&
                fixture.layout.getTraces().get(1).getNetId().equals("BRANCH_NET"),
            "R-2 valid three-pad branched net canary did not retain both branches");
    }

    private static void verifySameNetTeeAndCollinearReuse() {
        Fixture fixture = buildThreePadBranchFixture();
        fixture.layout.validateGeometry(fixture.board);
        require(fixture.layout.getSameNetReuseLength() > 0,
            "R-2 same-net tee canary did not reuse a collinear trunk");
        PcbTraceGeometry first = fixture.layout.getTraces().get(0);
        PcbTraceGeometry second = fixture.layout.getTraces().get(1);
        require(first.getXPoints()[0] == second.getXPoints()[0] &&
                first.getYPoints()[0] == second.getYPoints()[0] &&
                first.getXPoints()[1] == second.getXPoints()[1] &&
                first.getYPoints()[1] == second.getYPoints()[1],
            "R-2 same-net tee canary did not retain a shared trunk");
    }

    private static void verifySameNetPerpendicularTouch() {
        Fixture fixture = buildPerpendicularFixture(false);
        fixture.layout.validateGeometry(fixture.board);
        require(fixture.layout.getTraces().size() == 2 &&
                fixture.layout.getTraces().get(0).getNetId().equals("CROSS_NET") &&
                fixture.layout.getTraces().get(1).getNetId().equals("CROSS_NET"),
            "R-2 same-net perpendicular-touch canary lost its traces");
    }

    private static void verifySameNetPadContact() {
        Fixture fixture = buildPadContactFixture(false);
        fixture.layout.validateGeometry(fixture.board);
        require(fixture.layout.getTraces().size() == 1 &&
                "TOUCH_NET".equals(fixture.layout.getTraces().get(0).getNetId()),
            "R-2 same-net pad-contact canary lost its physical connection");
    }

    private static void verifyTransitivePackageInternalConnectivity() {
        Fixture fixture = buildTransitiveFixture();
        fixture.layout.validateGeometry(fixture.board);
        require(fixture.layout.getTraces().size() == 1 &&
                fixture.layout.getTraces().get(0).getEndPadId().equals("U1.2") &&
                fixture.board.getNet("TRANSITIVE_NET").getPadIds().size() == 3,
            "R-2 transitive package canary routed a redundant third-pad branch");
        PcbComponentPlacement placement = fixture.layout.getComponent("U1");
        require(placement.getPhysicalPackage().isInternallyConnected("1", "2") &&
                placement.getPhysicalPackage().isInternallyConnected("2", "3") &&
                !placement.getPhysicalPackage().isInternallyConnected("1", "3") &&
                placement.getGeometryContractVersionValue() ==
                    EXPECTED_GEOMETRY_CONTRACT_VERSION,
            "R-2 transitive package declaration was not exercised");
    }

    private static void verifyCompactionAndRealizationPropagation() {
        Fixture first = buildTwoPadFixture(false, 0);
        Fixture second = buildTwoPadFixture(false, 0);
        first.layout.validateGeometry(first.board);
        second.layout.validateGeometry(second.board);
        require(first.layout.geometryFingerprint().equals(second.layout.geometryFingerprint()),
            "R-2 deterministic layout fingerprint canary diverged");

        PcbComponentPlacement before = first.layout.getComponent("R1");
        PhysicalGeometryRealization realization = before.getGeometryRealization();
        String beforeLocalIdentity = before.geometryFingerprint();
        first.layout.compactToContent(70, 70, 26);
        first.layout.positionPartsTrayDisjointFromBoard();
        first.layout.validateGeometry(first.board);
        PcbComponentPlacement after = first.layout.getComponent("R1");
        require(after.getGeometryRealization() == realization &&
                after.getPhysicalPackage() == before.getPhysicalPackage() &&
                after.getPhysicalGeometry() == before.getPhysicalGeometry() &&
                after.getGeometryVariantKey().equals(before.getGeometryVariantKey()) &&
                after.getGeometryTransformKey().equals(before.getGeometryTransformKey()) &&
                after.getGeometryContractVersionValue() ==
                    EXPECTED_GEOMETRY_CONTRACT_VERSION &&
                beforeLocalIdentity.indexOf("package=AXIAL_RESISTOR") >= 0 &&
                after.geometryFingerprint().indexOf("package=AXIAL_RESISTOR") >= 0,
            "R-2 compaction did not propagate the exact package realization");

        second.layout.compactToContent(70, 70, 26);
        second.layout.positionPartsTrayDisjointFromBoard();
        second.layout.validateGeometry(second.board);
        require(first.layout.geometryFingerprint().equals(second.layout.geometryFingerprint()),
            "R-2 compacted layout fingerprint is not deterministic");
        Rectangle content = first.layout.getOccupiedContentBounds();
        Rectangle outline = first.layout.getBoardOutline();
        require(content.x == outline.x + 26 && content.y == outline.y + 26 &&
                content.x + content.width == outline.x + outline.width - 26 &&
                content.y + content.height == outline.y + outline.height - 26,
            "R-2 compaction did not include every approved geometry surface");
    }

    private static void verifyFullWidthCopperAtEdge() {
        Fixture fixture = buildTwoPadFixture(true, 0);
        fixture.layout.validateGeometry(fixture.board);
        Rectangle content = fixture.layout.getOccupiedContentBounds();
        Rectangle outline = fixture.layout.getBoardOutline();
        PcbTraceGeometry trace = fixture.layout.getTraces().get(0);
        require(trace.getYPoints()[2] == outline.y + 4 &&
                trace.getXPoints()[2] == outline.x + 4 &&
                content.x == outline.x && content.y == outline.y,
            "R-2 full-width copper edge canary did not exercise the stroke boundary");
    }

    private static void verifyNegativeCanaries() {
        expectFailure("orphan third pad", buildOrphanThirdPadFixture(),
            "PCB net is electrically disconnected");
        expectFailure("disconnected same-net islands", buildDisconnectedIslandsFixture(),
            "PCB net is electrically disconnected");
        expectFailure("wrong-net trace", buildWrongNetFixture(),
            "PCB trace endpoints do not match net");
        expectFailure("unknown pad", buildUnknownPadFixture(),
            "PCB layout references unknown pad");
        expectFailure("endpoint not at pad center", buildTwoPadFixture(false, 1),
            "PCB trace does not land on its pads");
        expectFailure("unrelated-net crossing/contact", buildPerpendicularFixture(true),
            "Unrelated PCB traces share copper");
        expectFailure("unrelated-net pad contact", buildPadContactFixture(true),
            "Unrelated PCB pads share copper");
        expectFailure("insufficient clearance", buildNearClearanceFixture(),
            "PCB traces violate copper clearance");

        expectFailure("body outside", buildSurfaceOutsideFixture("BODY"),
            "PCB component R1 leaves board outline");
        expectFailure("keep-out outside", buildSurfaceOutsideFixture("KEEP_OUT"),
            "PCB component keep-out R1 leaves board outline");
        expectFailure("courtyard outside", buildSurfaceOutsideFixture("COURTYARD"),
            "PCB component routing courtyard R1 leaves board outline");
        expectFailure("selection outside", buildSurfaceOutsideFixture("SELECTION"),
            "PCB component selection envelope R1 leaves board outline");
        expectFailure("drag outside", buildSurfaceOutsideFixture("DRAG"),
            "PCB component drag envelope R1 leaves board outline");
        expectFailure("pad outside", buildPadSurfaceMismatchFixture(false),
            "PCB component pad R1/0 leaves board outline");
        expectFailure("probe outside", buildPadSurfaceMismatchFixture(true),
            "PCB component board-pad probe R1/0 leaves board outline");
        expectFailure("lifted lead outside", buildLeadSurfaceOutsideFixture(true, false),
            "PCB component lifted lead R1/0 leaves board outline");
        expectFailure("lead-probe outside", buildLeadSurfaceOutsideFixture(false, false),
            "PCB component connected lead probe R1/0 leaves board outline");
        expectFailure("lifted lead-probe outside", buildLeadSurfaceOutsideFixture(false, true),
            "PCB component lifted lead probe R1/0 leaves board outline");
        expectFailure("silkscreen outside", buildSilkscreenOutsideFixture(),
            "PCB silkscreen label outside-silkscreen leaves board outline");
        expectFailure("full trace width outside", buildTwoPadFixture(true, 0, true),
            "PCB trace leaves board");

        verifyForeignAndPackageLessProductionGeometry();
        verifyPackageIdentityRejectedByBoardValidation();
        verifyDeterministicIdentityMismatch();
    }

    private static void verifyGeneratedLayouts() {
        Vector<String> failures = new Vector<String>();
        for (long seed : REPRESENTATIVE_SEEDS) {
            recordGeneratedLayoutResult(failures, "LED_INDICATOR", seed);
            recordGeneratedLayoutResult(failures, "DIODE_PROTECTED_INDICATOR", seed);
            recordGeneratedLayoutResult(failures, "PARALLEL_DUAL_INDICATOR", seed);
        }
        if (!failures.isEmpty())
            throw new IllegalStateException("R-2 generated strict-layout evidence: " +
                joinFailures(failures));
    }

    private static void recordGeneratedLayoutResult(Vector<String> failures, String familyId,
            long seed) {
        try {
            GeneratedBoardInstance instance = generateGeneratedFamily(familyId, seed);
            verifyGeneratedLayout(familyId, instance, seed);
        } catch (RuntimeException failure) {
            failures.add(familyId + "/" + seed + ": " + failure.getMessage());
        }
    }

    private static GeneratedBoardInstance generateGeneratedFamily(String familyId, long seed) {
        if ("LED_INDICATOR".equals(familyId))
            return new LedIndicatorGenerator().generate(seed);
        if ("DIODE_PROTECTED_INDICATOR".equals(familyId))
            return new DiodeProtectedIndicatorGenerator().generate(seed);
        if ("PARALLEL_DUAL_INDICATOR".equals(familyId))
            return new ParallelDualIndicatorGenerator().generate(seed);
        throw new IllegalArgumentException("Unsupported R-2 generated family: " + familyId);
    }

    private static String joinFailures(Vector<String> failures) {
        StringBuilder result = new StringBuilder();
        for (String failure : failures) {
            if (result.length() > 0)
                result.append("; ");
            result.append(failure);
        }
        return result.toString();
    }

    private static void verifyGeneratedLayout(String familyId, GeneratedBoardInstance instance,
            long seed) {
        require(instance != null && instance.getPcbLayout() != null &&
                familyId.equals(instance.getCircuitFamilyId()) && instance.getSeed() == seed,
            "R-2 generated layout identity is incomplete: " + familyId + "/" + seed);
        PcbBoardLayout layout = instance.getPcbLayout();
        layout.validateGeometry(instance.getBoard());
        String fingerprint = layout.geometryFingerprint();
        GeneratedBoardInstance repeat;
        repeat = generateGeneratedFamily(familyId, seed);
        require(fingerprint.equals(repeat.getPcbLayout().geometryFingerprint()),
            "R-2 generated layout fingerprint is not deterministic: " + familyId + "/" + seed);
        for (String componentId : instance.getBoard().getComponentIds()) {
            PcbComponentPlacement placement = layout.getComponent(componentId);
            require(placement != null && placement.getPhysicalPackage() != null &&
                    placement.getPhysicalGeometry() != null &&
                    !placement.getPhysicalPackage().isDeveloperGeneric() &&
                    placement.getGeometryContractVersionValue() ==
                        EXPECTED_GEOMETRY_CONTRACT_VERSION &&
                    placement.getPhysicalPackage().acceptsGeometry(
                        placement.getPhysicalGeometry()),
                "R-2 generated component is not package-backed: " + familyId + "/" +
                    seed + "/" + componentId);
        }
    }

    private static Fixture buildTwoPadFixture(boolean edge, int endpointDelta) {
        return buildTwoPadFixture(edge, endpointDelta, false);
    }

    private static Fixture buildTwoPadFixture(boolean edge, int endpointDelta,
            boolean fullWidthOutside) {
        TroubleshootBoard board = new TroubleshootBoard("TASK43_R2_TWO_PAD");
        board.addNet(new BoardNet("TWO_PAD_NET"));
        BoardComponent component = new BoardComponent("R1", "RESISTOR",
            PhysicalPackages.AXIAL_RESISTOR);
        board.addComponent(component);
        board.addPad(new BoardPad("R1.1", "R1", "1", "TWO_PAD_NET"));
        board.addPad(new BoardPad("R1.2", "R1", "2", "TWO_PAD_NET"));
        board.validate();

        Rectangle outline = edge ? new Rectangle(fullWidthOutside ? 250 : 246, 206,
            fullWidthOutside ? 290 : 294, 170) :
            new Rectangle(210, 140, 380, 220);
        Rectangle tray = edge ? new Rectangle(570, 200, 200, 160) :
            new Rectangle(620, 140, 150, 220);
        PcbBoardLayout layout = new PcbBoardLayout(800, 500, outline, tray);
        PhysicalPackageGeometry geometry = PhysicalPackages.AXIAL_RESISTOR.getGeometry();
        PcbFootprint footprint = PcbFootprint.fromPhysicalPackage(component, 280, 220,
            geometry);
        layout.addComponent(footprint.getPlacement());
        for (PcbPadPlacement pad : footprint.getPads())
            layout.addPad(pad);
        int y = edge ? 210 : 210;
        int leftX = edge ? 250 : 250;
        addTrace(layout, "TWO_PAD_NET", "R1.1", "R1.2", 310, 250, leftX, 250,
            leftX, y, edge ? 530 : 530, y, edge ? 530 : 530, 250,
            470 + endpointDelta, 250);
        if (edge) {
            addLabel(layout, "board-title", "R2 EDGE", 270, 340, 80, 14, null);
            addLabel(layout, "component:R1", "R1", 365, 340, 24, 14, null);
        } else {
            addLabel(layout, "board-title", "R2 TWO PAD", 220, 155, 100, 14, null);
            addLabel(layout, "component:R1", "R1", 365, 155, 24, 14, null);
        }
        return new Fixture(board, layout);
    }

    private static Fixture buildThreePadBranchFixture() {
        TroubleshootBoard board = new TroubleshootBoard("TASK43_R2_BRANCH");
        board.addNet(new BoardNet("BRANCH_NET"));
        PhysicalPackage physicalPackage = singlePadPackage("TASK43_R2_SINGLE_PAD_BRANCH");
        addSinglePadBoardComponent(board, "A", physicalPackage, "BRANCH_NET");
        addSinglePadBoardComponent(board, "B", physicalPackage, "BRANCH_NET");
        addSinglePadBoardComponent(board, "C", physicalPackage, "BRANCH_NET");
        board.validate();
        PcbBoardLayout layout = new PcbBoardLayout(1000, 700,
            new Rectangle(150, 80, 650, 500), new Rectangle(830, 100, 150, 220));
        addSinglePadFootprint(layout, board.getComponent("A"), 260, 250);
        addSinglePadFootprint(layout, board.getComponent("B"), 560, 150);
        addSinglePadFootprint(layout, board.getComponent("C"), 560, 350);
        addTrace(layout, "BRANCH_NET", "A.1", "B.1", 290, 300, 240, 300, 240, 200,
            590, 200);
        addTrace(layout, "BRANCH_NET", "A.1", "C.1", 290, 300, 240, 300, 240, 400,
            590, 400);
        addLabel(layout, "board-title", "R2 BRANCH", 170, 95, 100, 14, null);
        addLabel(layout, "component:A", "A", 310, 240, 18, 14, null);
        addLabel(layout, "component:B", "B", 600, 130, 18, 14, null);
        addLabel(layout, "component:C", "C", 600, 450, 18, 14, null);
        return new Fixture(board, layout);
    }

    private static Fixture buildPerpendicularFixture(boolean differentNets) {
        TroubleshootBoard board = new TroubleshootBoard("TASK43_R2_PERPENDICULAR");
        String firstNet = differentNets ? "CROSS_NET_A" : "CROSS_NET";
        String secondNet = differentNets ? "CROSS_NET_B" : firstNet;
        board.addNet(new BoardNet(firstNet));
        if (differentNets)
            board.addNet(new BoardNet(secondNet));
        PhysicalPackage physicalPackage = singlePadPackage("TASK43_R2_SINGLE_PAD_CROSS");
        addSinglePadBoardComponent(board, "A", physicalPackage, firstNet);
        addSinglePadBoardComponent(board, "B", physicalPackage, firstNet);
        addSinglePadBoardComponent(board, "C", physicalPackage, secondNet);
        addSinglePadBoardComponent(board, "D", physicalPackage, secondNet);
        board.validate();
        PcbBoardLayout layout = new PcbBoardLayout(1000, 700,
            new Rectangle(150, 70, 650, 560), new Rectangle(830, 100, 150, 220));
        addSinglePadFootprint(layout, board.getComponent("A"), 220, 300);
        addSinglePadFootprint(layout, board.getComponent("B"), 620, 300);
        addSinglePadFootprint(layout, board.getComponent("C"), 420, 100);
        addSinglePadFootprint(layout, board.getComponent("D"), 420, 500);
        addTrace(layout, firstNet, "A.1", "B.1", 250, 350, 200, 350, 200, 420,
            600, 420, 600, 350, 650, 350);
        addTrace(layout, secondNet, "C.1", "D.1", 450, 150, 400, 150, 400, 420,
            400, 550, 450, 550);
        addLabel(layout, "board-title", "R2 CROSS", 170, 85, 90, 14, null);
        addLabel(layout, "component:A", "A", 290, 300, 18, 14, null);
        addLabel(layout, "component:B", "B", 690, 300, 18, 14, null);
        addLabel(layout, "component:C", "C", 500, 90, 18, 14, null);
        addLabel(layout, "component:D", "D", 500, 600, 18, 14, null);
        return new Fixture(board, layout);
    }

    private static Fixture buildTransitiveFixture() {
        TroubleshootBoard board = new TroubleshootBoard("TASK43_R2_TRANSITIVE");
        board.addNet(new BoardNet("TRANSITIVE_NET"));
        PhysicalPackage physicalPackage = transitivePackage();
        BoardComponent component = new BoardComponent("U1", "TRANSITIVE", physicalPackage);
        board.addComponent(component);
        board.addPad(new BoardPad("U1.1", "U1", "1", "TRANSITIVE_NET"));
        board.addPad(new BoardPad("U1.2", "U1", "2", "TRANSITIVE_NET"));
        board.addPad(new BoardPad("U1.3", "U1", "3", "TRANSITIVE_NET"));
        board.validate();
        PcbBoardLayout layout = new PcbBoardLayout(800, 560,
            new Rectangle(180, 80, 500, 400), new Rectangle(700, 100, 80, 200));
        PcbFootprint footprint = PcbFootprint.fromPhysicalPackage(component, 300, 150,
            physicalPackage.getGeometry());
        layout.addComponent(footprint.getPlacement());
        for (PcbPadPlacement pad : footprint.getPads())
            layout.addPad(pad);
        addTrace(layout, "TRANSITIVE_NET", "U1.1", "U1.2", 330, 190, 260, 190, 260, 260,
            330, 260);
        addLabel(layout, "board-title", "R2 TRANSITIVE", 200, 95, 120, 14, null);
        addLabel(layout, "component:U1", "U1", 500, 100, 24, 14, null);
        return new Fixture(board, layout);
    }

    private static Fixture buildPadContactFixture(boolean differentNets) {
        TroubleshootBoard board = new TroubleshootBoard(
            "TASK43_R2_PAD_CONTACT_" + (differentNets ? "CROSS" : "SAME"));
        String firstNet = differentNets ? "CONTACT_NET_A" : "TOUCH_NET";
        String secondNet = differentNets ? "CONTACT_NET_B" : firstNet;
        board.addNet(new BoardNet(firstNet));
        if (differentNets)
            board.addNet(new BoardNet(secondNet));
        PhysicalPackage physicalPackage = touchingPadPackage(
            "TASK43_R2_TOUCHING_PADS_" + (differentNets ? "CROSS" : "SAME"));
        BoardComponent component = new BoardComponent("U1", "TOUCHING_PADS", physicalPackage);
        board.addComponent(component);
        board.addPad(new BoardPad("U1.1", "U1", "1", firstNet));
        board.addPad(new BoardPad("U1.2", "U1", "2", secondNet));
        board.addPad(new BoardPad("U1.3", "U1", "3", differentNets ? secondNet : firstNet));
        board.validate();

        PcbBoardLayout layout = new PcbBoardLayout(500, 320,
            new Rectangle(0, 0, 280, 250), new Rectangle(300, 0, 160, 250));
        PcbFootprint footprint = PcbFootprint.fromPhysicalPackage(component, 0, 0,
            physicalPackage.getGeometry());
        layout.addComponent(footprint.getPlacement());
        for (PcbPadPlacement pad : footprint.getPads())
            layout.addPad(pad);
        if (!differentNets)
            addTrace(layout, firstNet, "U1.1", "U1.3", 30, 40, 4, 40, 4, 220,
                236, 220, 236, 160, 210, 160);
        addLabel(layout, "board-title", "R2 PAD CONTACT", 10, 195, 110, 14, null);
        addLabel(layout, "component:U1", "U1", 150, 195, 24, 14, null);
        return new Fixture(board, layout);
    }

    private static Fixture buildOrphanThirdPadFixture() {
        Fixture source = buildThreePadBranchFixture();
        PcbBoardLayout layout = new PcbBoardLayout(1000, 700,
            new Rectangle(150, 80, 650, 500), new Rectangle(830, 100, 150, 220));
        copySinglePadComponents(layout, source.board, 260, 250, 560, 150, 560, 350,
            560, 350);
        addTrace(layout, "BRANCH_NET", "A.1", "B.1", 290, 300, 240, 300, 240, 200,
            590, 200);
        addLabel(layout, "board-title", "R2 ORPHAN", 170, 95, 100, 14, null);
        addLabel(layout, "component:A", "A", 310, 240, 18, 14, null);
        addLabel(layout, "component:B", "B", 600, 130, 18, 14, null);
        addLabel(layout, "component:C", "C", 600, 450, 18, 14, null);
        return new Fixture(source.board, layout);
    }

    private static Fixture buildDisconnectedIslandsFixture() {
        TroubleshootBoard board = new TroubleshootBoard("TASK43_R2_ISLANDS");
        board.addNet(new BoardNet("ISLAND_NET"));
        PhysicalPackage physicalPackage = singlePadPackage("TASK43_R2_SINGLE_PAD_ISLANDS");
        addSinglePadBoardComponent(board, "A", physicalPackage, "ISLAND_NET");
        addSinglePadBoardComponent(board, "B", physicalPackage, "ISLAND_NET");
        addSinglePadBoardComponent(board, "C", physicalPackage, "ISLAND_NET");
        addSinglePadBoardComponent(board, "D", physicalPackage, "ISLAND_NET");
        board.validate();
        PcbBoardLayout layout = new PcbBoardLayout(1000, 700,
            new Rectangle(150, 60, 650, 500), new Rectangle(830, 100, 150, 220));
        copySinglePadComponents(layout, board, 260, 100, 560, 100, 260, 350, 560, 350);
        addTrace(layout, "ISLAND_NET", "A.1", "B.1", 290, 150, 240, 150, 240, 100,
            550, 100, 550, 150, 590, 150);
        addTrace(layout, "ISLAND_NET", "C.1", "D.1", 290, 400, 240, 400, 240, 350,
            550, 350, 550, 400, 590, 400);
        addLabel(layout, "board-title", "R2 ISLANDS", 170, 75, 100, 14, null);
        addLabel(layout, "component:A", "A", 310, 90, 18, 14, null);
        addLabel(layout, "component:B", "B", 600, 90, 18, 14, null);
        addLabel(layout, "component:C", "C", 310, 340, 18, 14, null);
        addLabel(layout, "component:D", "D", 600, 340, 18, 14, null);
        return new Fixture(board, layout);
    }

    private static Fixture buildWrongNetFixture() {
        TroubleshootBoard board = new TroubleshootBoard("TASK43_R2_WRONG_NET");
        board.addNet(new BoardNet("NET_A"));
        board.addNet(new BoardNet("NET_B"));
        PhysicalPackage physicalPackage = singlePadPackage("TASK43_R2_SINGLE_PAD_WRONG_NET");
        addSinglePadBoardComponent(board, "A", physicalPackage, "NET_A");
        addSinglePadBoardComponent(board, "B", physicalPackage, "NET_B");
        board.validate();
        PcbBoardLayout layout = new PcbBoardLayout(800, 500,
            new Rectangle(150, 80, 500, 300), new Rectangle(680, 100, 80, 160));
        addSinglePadFootprint(layout, board.getComponent("A"), 240, 180);
        addSinglePadFootprint(layout, board.getComponent("B"), 500, 180);
        addTrace(layout, "NET_A", "A.1", "B.1", 270, 230, 220, 230, 530, 230);
        addLabel(layout, "board-title", "R2 WRONG NET", 170, 95, 110, 14, null);
        addLabel(layout, "component:A", "A", 300, 170, 18, 14, null);
        addLabel(layout, "component:B", "B", 560, 170, 18, 14, null);
        return new Fixture(board, layout);
    }

    private static Fixture buildUnknownPadFixture() {
        Fixture fixture = buildTwoPadFixture(false, 0);
        PcbPadPlacement known = fixture.layout.getPad("R1.1");
        fixture.layout.addPad(new PcbPadPlacement("R1.UNKNOWN", known.getX(), known.getY(),
            known.getEscapeDx(), known.getEscapeDy(), known.getEscapeLength(),
            known.getPadBounds(), known.getProbeBounds()));
        return fixture;
    }

    private static Fixture buildNearClearanceFixture() {
        TroubleshootBoard board = new TroubleshootBoard("TASK43_R2_CLEARANCE");
        board.addNet(new BoardNet("CLEAR_NET_A"));
        board.addNet(new BoardNet("CLEAR_NET_B"));
        PhysicalPackage physicalPackage = singlePadPackage("TASK43_R2_SINGLE_PAD_CLEARANCE");
        addSinglePadBoardComponent(board, "A", physicalPackage, "CLEAR_NET_A");
        addSinglePadBoardComponent(board, "B", physicalPackage, "CLEAR_NET_A");
        addSinglePadBoardComponent(board, "C", physicalPackage, "CLEAR_NET_B");
        addSinglePadBoardComponent(board, "D", physicalPackage, "CLEAR_NET_B");
        board.validate();
        PcbBoardLayout layout = new PcbBoardLayout(1100, 800,
            new Rectangle(140, 120, 760, 560), new Rectangle(920, 150, 150, 220));
        copySinglePadComponents(layout, board, 200, 200, 700, 200, 200, 500, 700, 500);
        addTrace(layout, "CLEAR_NET_A", "A.1", "B.1", 230, 250, 180, 250, 180, 300,
            600, 300, 600, 250, 730, 250);
        addTrace(layout, "CLEAR_NET_B", "C.1", "D.1", 230, 550, 180, 550, 180, 310,
            600, 310, 600, 550, 730, 550);
        addLabel(layout, "board-title", "R2 CLEARANCE", 160, 135, 110, 14, null);
        addLabel(layout, "component:A", "A", 250, 190, 18, 14, null);
        addLabel(layout, "component:B", "B", 750, 190, 18, 14, null);
        addLabel(layout, "component:C", "C", 250, 480, 18, 14, null);
        addLabel(layout, "component:D", "D", 750, 480, 18, 14, null);
        return new Fixture(board, layout);
    }

    private static Fixture buildSurfaceOutsideFixture(String surface) {
        TroubleshootBoard board = new TroubleshootBoard("TASK43_R2_SURFACE_" + surface);
        board.addNet(new BoardNet("SURFACE_NET"));
        PhysicalPackage physicalPackage = singlePadPackage("TASK43_R2_SINGLE_PAD_" + surface);
        addSinglePadBoardComponent(board, "R1", physicalPackage, "SURFACE_NET");
        board.validate();
        int right = "BODY".equals(surface) ? 400 : "KEEP_OUT".equals(surface) ? 418 :
            "COURTYARD".equals(surface) ? 425 : "SELECTION".equals(surface) ? 433 : 438;
        PcbBoardLayout layout = new PcbBoardLayout(700, 500,
            new Rectangle(300, 250, right - 300, 100), new Rectangle(500, 250, 150, 100));
        addSinglePadFootprint(layout, board.getComponent("R1"), 300, 250);
        return new Fixture(board, layout);
    }

    private static Fixture buildPadSurfaceMismatchFixture(boolean probe) {
        TroubleshootBoard board = new TroubleshootBoard("TASK43_R2_" +
            (probe ? "PROBE" : "PAD"));
        board.addNet(new BoardNet("PAD_NET"));
        PhysicalPackage physicalPackage = singlePadPackage("TASK43_R2_SINGLE_PAD_MISMATCH");
        addSinglePadBoardComponent(board, "R1", physicalPackage, "PAD_NET");
        board.validate();
        PcbBoardLayout layout = new PcbBoardLayout(700, 500,
            new Rectangle(300, 200, 300, 220), new Rectangle(500, 80, 150, 100));
        PcbFootprint footprint = PcbFootprint.fromPhysicalPackage(board.getComponent("R1"),
            300, 250, physicalPackage.getGeometry());
        layout.addComponent(new SurfaceOverridePlacement(footprint.getPlacement(), false, false,
            false, !probe, probe));
        PcbPadPlacement expected = footprint.getPad("R1.1");
        layout.addPad(expected);
        return new Fixture(board, layout);
    }

    private static Fixture buildLeadSurfaceOutsideFixture(boolean lifted, boolean liftedProbe) {
        TroubleshootBoard board = new TroubleshootBoard("TASK43_R2_LEAD_SURFACE");
        board.addNet(new BoardNet("LEAD_NET"));
        PhysicalPackage physicalPackage = singlePadPackage("TASK43_R2_SINGLE_PAD_LEAD");
        addSinglePadBoardComponent(board, "R1", physicalPackage, "LEAD_NET");
        addSinglePadBoardComponent(board, "R2", physicalPackage, "LEAD_NET");
        board.validate();
        PcbBoardLayout layout = new PcbBoardLayout(700, 500,
            new Rectangle(250, 200, 300, 220), new Rectangle(570, 80, 100, 100));
        PcbFootprint footprint = PcbFootprint.fromPhysicalPackage(board.getComponent("R1"),
            270, 200, physicalPackage.getGeometry());
        PcbComponentPlacement placement = new SurfaceOverridePlacement(footprint.getPlacement(),
            lifted, !lifted && !liftedProbe, liftedProbe);
        layout.addComponent(placement);
        for (PcbPadPlacement pad : footprint.getPads())
            layout.addPad(pad);
        PcbFootprint secondFootprint = PcbFootprint.fromPhysicalPackage(board.getComponent("R2"),
            410, 320, physicalPackage.getGeometry());
        layout.addComponent(secondFootprint.getPlacement());
        for (PcbPadPlacement pad : secondFootprint.getPads())
            layout.addPad(pad);
        addTrace(layout, "LEAD_NET", "R1.1", "R2.1", 300, 250, 270, 250, 270, 310,
            390, 310, 390, 370, 440, 370);
        addLabel(layout, "board-title", "R2 LEAD", 255, 205, 70, 14, null);
        addLabel(layout, "component:R1", "R1", 255, 360, 24, 14, null);
        addLabel(layout, "component:R2", "R2", 480, 330, 24, 14, null);
        return new Fixture(board, layout);
    }

    private static Fixture buildSilkscreenOutsideFixture() {
        Fixture fixture = buildTwoPadFixture(false, 0);
        Rectangle outline = fixture.layout.getBoardOutline();
        addLabel(fixture.layout, "outside-silkscreen", "OUTSIDE", outline.x - 5,
            outline.y + 20, 50, 14, null);
        return fixture;
    }

    private static void verifyForeignAndPackageLessProductionGeometry() {
        PhysicalPackage physicalPackage = PhysicalPackages.AXIAL_RESISTOR;
        PhysicalPackageGeometry source = physicalPackage.getGeometry();
        PhysicalPackageGeometry foreign = cloneGeometry(source,
            new PcbGeometryContractVersion(EXPECTED_GEOMETRY_CONTRACT_VERSION));
        require(source.isEquivalentTo(foreign) && !physicalPackage.acceptsGeometry(foreign),
            "R-2 foreign geometry identity precondition failed");
        boolean foreignRejected = false;
        try {
            PcbComponentPlacement.fromPhysicalGeometry("R2_FOREIGN", 280, 220,
                physicalPackage, foreign);
        } catch (IllegalArgumentException expected) {
            foreignRejected = messageContains(expected, "Foreign or undeclared package geometry");
        }
        require(foreignRejected, "R-2 foreign production geometry was not rejected");

        boolean packageLessRejected = false;
        try {
            PcbComponentPlacement.fromPhysicalGeometry("R2_PACKAGELESS", 280, 220, source);
        } catch (IllegalArgumentException expected) {
            packageLessRejected = messageContains(expected, "Only marked developer geometry may be projected");
        }
        require(packageLessRejected, "R-2 package-less production geometry was not rejected");
    }

    private static void verifyPackageIdentityRejectedByBoardValidation() {
        Fixture fixture = buildForeignPackageValidationFixture();
        expectFailure("foreign package object in board validation", fixture,
            "PCB component package definition diverged: A");
    }

    private static Fixture buildForeignPackageValidationFixture() {
        TroubleshootBoard board = new TroubleshootBoard("TASK43_R2_FOREIGN_PACKAGE");
        board.addNet(new BoardNet("FOREIGN_NET"));
        PhysicalPackage canonical = singlePadPackage("TASK43_R2_FOREIGN_CANONICAL");
        PhysicalPackage foreign = new PhysicalPackage(canonical.getId(),
            canonical.getTerminalIds(), new Vector<String>(), canonical.isConnector(),
            cloneGeometry(canonical.getGeometry(),
                new PcbGeometryContractVersion(EXPECTED_GEOMETRY_CONTRACT_VERSION)));
        require(canonical.isEquivalentTo(foreign) && canonical != foreign,
            "R-2 foreign package object precondition failed");
        addSinglePadBoardComponent(board, "A", canonical, "FOREIGN_NET");
        addSinglePadBoardComponent(board, "B", canonical, "FOREIGN_NET");
        board.validate();

        PcbBoardLayout layout = new PcbBoardLayout(700, 400,
            new Rectangle(20, 20, 500, 300), new Rectangle(550, 20, 120, 300));
        PcbFootprint first = PcbFootprint.fromPhysicalPackage(board.getComponent("A"),
            80, 100, canonical.getGeometry());
        PcbComponentPlacement foreignPlacement = PcbComponentPlacement.fromPhysicalGeometry(
            "A", 80, 100, foreign, foreign.getGeometry());
        layout.addComponent(foreignPlacement);
        for (PcbPadPlacement pad : first.getPads())
            layout.addPad(pad);
        addSinglePadFootprint(layout, board.getComponent("B"), 300, 100);
        return new Fixture(board, layout);
    }

    private static void verifyDeterministicIdentityMismatch() {
        Fixture first = buildTwoPadFixture(false, 0);
        Fixture second = buildTwoPadFixture(false, 0);
        require(first.layout.geometryFingerprint().equals(second.layout.geometryFingerprint()),
            "R-2 identity baseline is not deterministic");
        PcbPadPlacement firstPad = first.layout.getPad("R1.1");
        PcbPadPlacement secondPad = second.layout.getPad("R1.1");
        require(firstPad.geometryFingerprint().equals(secondPad.geometryFingerprint()),
            "R-2 pad identity baseline is not deterministic");
        boolean rejected = false;
        try {
            PcbComponentPlacement.fromPhysicalGeometry("R2_IDENTITY_MISMATCH", 280, 220,
                PhysicalPackages.AXIAL_RESISTOR, cloneGeometry(
                    PhysicalPackages.AXIAL_RESISTOR.getGeometry(),
                    new PcbGeometryContractVersion(EXPECTED_GEOMETRY_CONTRACT_VERSION)));
        } catch (IllegalArgumentException expected) {
            rejected = messageContains(expected, "Foreign or undeclared package geometry");
        }
        require(rejected, "R-2 deterministic identity mismatch was not rejected");
    }

    private static void expectFailure(String name, Fixture fixture, String expectedMessage) {
        boolean rejected = false;
        try {
            fixture.layout.validateGeometry(fixture.board);
        } catch (IllegalStateException expected) {
            rejected = messageContains(expected, expectedMessage);
            if (!rejected)
                throw new IllegalStateException("R-2 " + name + " canary failed for the wrong " +
                    "reason: " + expected.getMessage());
        }
        require(rejected, "R-2 " + name + " canary was accepted");
    }

    private static boolean messageContains(Throwable failure, String expected) {
        return failure != null && failure.getMessage() != null &&
            failure.getMessage().indexOf(expected) >= 0;
    }

    private static void addSinglePadBoardComponent(TroubleshootBoard board, String id,
            PhysicalPackage physicalPackage, String netId) {
        BoardComponent component = new BoardComponent(id, "TEST_PAD", physicalPackage);
        board.addComponent(component);
        board.addPad(new BoardPad(id + ".1", id, "1", netId));
    }

    private static void addSinglePadFootprint(PcbBoardLayout layout, BoardComponent component,
            int x, int y) {
        PcbFootprint footprint = PcbFootprint.fromPhysicalPackage(component, x, y,
            component.getPhysicalPackage().getGeometry());
        layout.addComponent(footprint.getPlacement());
        for (PcbPadPlacement pad : footprint.getPads())
            layout.addPad(pad);
    }

    private static void copySinglePadComponents(PcbBoardLayout layout, TroubleshootBoard board,
            int aX, int aY, int bX, int bY, int cX, int cY, int dX, int dY) {
        addSinglePadFootprint(layout, board.getComponent("A"), aX, aY);
        addSinglePadFootprint(layout, board.getComponent("B"), bX, bY);
        if (board.getComponent("C") != null)
            addSinglePadFootprint(layout, board.getComponent("C"), cX, cY);
        if (board.getComponent("D") != null)
            addSinglePadFootprint(layout, board.getComponent("D"), dX, dY);
    }

    private static void addTrace(PcbBoardLayout layout, String netId, String startPadId,
            String endPadId, int... points) {
        require(points.length >= 4 && points.length % 2 == 0,
            "R-2 verifier trace canary has invalid point count");
        int count = points.length / 2;
        int[] x = new int[count];
        int[] y = new int[count];
        for (int index = 0; index < count; index++) {
            x[index] = points[index * 2];
            y[index] = points[index * 2 + 1];
        }
        layout.addTrace(new PcbTraceGeometry(netId, startPadId, endPadId, x, y));
    }

    private static void addLabel(PcbBoardLayout layout, String id, String text, int x, int y,
            int width, int height, String targetPadId) {
        layout.addSilkscreenLabel(new PcbSilkscreenLabel(id, text,
            new Rectangle(x, y, width, height), 12, false, targetPadId));
    }

    private static PhysicalPackage singlePadPackage(String id) {
        Vector<String> terminals = vector("1");
        Vector<PhysicalPackageGeometry.Terminal> geometryTerminals =
            new Vector<PhysicalPackageGeometry.Terminal>();
        Point pad = new Point(30, 50);
        Point bodyPoint = new Point(90, 50);
        Point liftedPoint = new Point(82, 50);
        geometryTerminals.add(new PhysicalPackageGeometry.Terminal("1", pad,
            centered(pad, 12), pad, centered(pad, 18),
            lead(pad, bodyPoint, bodyPoint), lead(liftedPoint, bodyPoint, liftedPoint),
            -1, 0, 50));
        PhysicalPackageGeometry geometry = new PhysicalPackageGeometry(140, 100,
            geometryTerminals, new Rectangle(70, 25, 45, 50),
            new Rectangle(65, 20, 55, 60), new Rectangle(10, 10, 120, 80),
            new Rectangle(4, 4, 132, 92), new Rectangle(0, 0, 140, 100),
            new PcbGeometryContractVersion(EXPECTED_GEOMETRY_CONTRACT_VERSION));
        return new PhysicalPackage(id, terminals, new Vector<String>(), false, geometry);
    }

    private static PhysicalPackage touchingPadPackage(String id) {
        Vector<String> terminals = vector("1", "2", "3");
        Vector<PhysicalPackageGeometry.Terminal> geometryTerminals =
            new Vector<PhysicalPackageGeometry.Terminal>();
        Point[] pads = { new Point(30, 40), new Point(30, 52), new Point(210, 160) };
        Point[] bodies = { new Point(100, 20), new Point(100, 80), new Point(100, 140) };
        Point[] lifted = { new Point(30, 10), new Point(30, 90), new Point(100, 180) };
        int[] escapeDx = { -1, 0, 1 };
        int[] escapeLengths = { 26, 0, 26 };
        for (int index = 0; index < terminals.size(); index++) {
            Rectangle padBounds = centered(pads[index], 12);
            geometryTerminals.add(new PhysicalPackageGeometry.Terminal(terminals.get(index),
                pads[index], padBounds, pads[index], new Rectangle(padBounds),
                lead(pads[index], bodies[index], bodies[index]),
                lead(lifted[index], bodies[index], lifted[index]), escapeDx[index], 0,
                escapeLengths[index]));
        }
        PhysicalPackageGeometry geometry = new PhysicalPackageGeometry(240, 210,
            geometryTerminals, new Rectangle(80, 10, 80, 140),
            new Rectangle(70, 5, 100, 150), new Rectangle(10, 0, 220, 190),
            new Rectangle(4, 0, 232, 200), new Rectangle(0, 0, 240, 210),
            new PcbGeometryContractVersion(EXPECTED_GEOMETRY_CONTRACT_VERSION));
        return new PhysicalPackage(id, terminals, new Vector<String>(), false, geometry);
    }

    private static PhysicalPackage transitivePackage() {
        Vector<String> terminals = vector("1", "2", "3");
        Vector<PhysicalPackageGeometry.Terminal> geometryTerminals =
            new Vector<PhysicalPackageGeometry.Terminal>();
        int[] yPoints = { 40, 110, 180 };
        for (int index = 0; index < yPoints.length; index++) {
            Point pad = new Point(30, yPoints[index]);
            Point bodyPoint = new Point(100, yPoints[index]);
            Point liftedPoint = new Point(88, yPoints[index]);
            geometryTerminals.add(new PhysicalPackageGeometry.Terminal(
                terminals.get(index), pad, centered(pad, 20), pad, centered(pad, 24),
                lead(pad, bodyPoint, bodyPoint), lead(liftedPoint, bodyPoint, liftedPoint),
                -1, 0, 70));
        }
        PhysicalPackageGeometry geometry = new PhysicalPackageGeometry(180, 240,
            geometryTerminals, new Rectangle(70, 25, 60, 190),
            new Rectangle(65, 15, 70, 210), new Rectangle(10, 5, 165, 230),
            new Rectangle(4, 0, 172, 240), new Rectangle(0, 0, 180, 240),
            new PcbGeometryContractVersion(EXPECTED_GEOMETRY_CONTRACT_VERSION));
        return new PhysicalPackage("TASK43_R2_TRANSITIVE_PACKAGE", terminals,
            vector("1=2", "2=3"), false, geometry);
    }

    private static PhysicalPackageGeometry cloneGeometry(PhysicalPackageGeometry source,
            PcbGeometryContractVersion version) {
        return new PhysicalPackageGeometry(source.getWidth(), source.getHeight(),
            source.getTerminals(), source.getBodyBounds(), source.getBodyKeepOut(),
            source.getRoutingCourtyard(), source.getSelectionEnvelope(),
            source.getDragEnvelope(), version);
    }

    private static PhysicalPackageGeometry.Lead lead(Point endPoint, Point bodyPoint,
            Point probeCenter) {
        int left = Math.min(endPoint.x, bodyPoint.x) - 3;
        int top = Math.min(endPoint.y, bodyPoint.y) - 3;
        int right = Math.max(endPoint.x, bodyPoint.x) + 3;
        int bottom = Math.max(endPoint.y, bodyPoint.y) + 3;
        return new PhysicalPackageGeometry.Lead(endPoint, bodyPoint,
            new Rectangle(left, top, right - left, bottom - top), probeCenter,
            centered(probeCenter, 8));
    }

    private static Rectangle centered(Point point, int size) {
        return new Rectangle(point.x - size / 2, point.y - size / 2, size, size);
    }

    private static Vector<String> vector(String... values) {
        Vector<String> result = new Vector<String>();
        for (String value : values)
            result.add(value);
        return result;
    }

    private static final class Fixture {
        final TroubleshootBoard board;
        final PcbBoardLayout layout;

        Fixture(TroubleshootBoard board, PcbBoardLayout layout) {
            this.board = board;
            this.layout = layout;
        }
    }

    /** Overrides only R-2 interaction surfaces not checked during package identity sync. */
    private static final class SurfaceOverridePlacement extends PcbComponentPlacement {
        private final boolean liftedLeadOutside;
        private final boolean connectedProbeOutside;
        private final boolean liftedProbeOutside;
        private final boolean padOutside;
        private final boolean probeOutside;

        SurfaceOverridePlacement(PcbComponentPlacement source, boolean liftedLeadOutside,
                boolean connectedProbeOutside, boolean liftedProbeOutside) {
            this(source, liftedLeadOutside, connectedProbeOutside, liftedProbeOutside, false,
                false);
        }

        SurfaceOverridePlacement(PcbComponentPlacement source, boolean liftedLeadOutside,
                boolean connectedProbeOutside, boolean liftedProbeOutside, boolean padOutside,
                boolean probeOutside) {
            super(source.getComponentId(), source.getX(), source.getY(), source.getWidth(),
                source.getHeight(), source.getKeepOut(), source.getRoutingCourtyard(),
                source.getPhysicalPackage(), source.getPhysicalGeometry());
            this.liftedLeadOutside = liftedLeadOutside;
            this.connectedProbeOutside = connectedProbeOutside;
            this.liftedProbeOutside = liftedProbeOutside;
            this.padOutside = padOutside;
            this.probeOutside = probeOutside;
        }

        public Rectangle getPadBounds(int index) {
            Rectangle source = super.getPadBounds(index);
            return padOutside ? shiftedOutside(source) : source;
        }

        public Rectangle getProbeBounds(int index) {
            Rectangle source = super.getProbeBounds(index);
            return probeOutside ? shiftedOutside(source) : source;
        }

        public Rectangle getLeadBounds(int index, boolean lifted) {
            Rectangle source = super.getLeadBounds(index, lifted);
            if (lifted && liftedLeadOutside)
                return new Rectangle(source.x - 500, source.y, source.width, source.height);
            return source;
        }

        public Rectangle getComponentLeadProbeBounds(int index) {
            Rectangle source = super.getComponentLeadProbeBounds(index);
            return connectedProbeOutside ? shiftedOutside(source) : source;
        }

        public Rectangle getComponentLeadProbeBounds(int index, boolean lifted) {
            Rectangle source = super.getComponentLeadProbeBounds(index, lifted);
            if ((!lifted && connectedProbeOutside) || (lifted && liftedProbeOutside))
                return shiftedOutside(source);
            return source;
        }

        private Rectangle shiftedOutside(Rectangle source) {
            return new Rectangle(source.x - 500, source.y, source.width, source.height);
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition)
            throw new IllegalStateException(message);
    }
}
