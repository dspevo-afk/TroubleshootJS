package com.lushprojects.circuitjs1.client;

import java.lang.reflect.Method;
import java.util.Random;
import java.util.Vector;

/** Independent native contract for the bounded passive top-face pose allowance. */
public final class PcbPassivePoseFoundationContractTest {
    private static int assertions;
    private static final int BOARD_X = 100;
    private static final int BOARD_Y = 200;
    private static final PcbRotation[] ROTATIONS = {
        PcbRotation.DEG_0, PcbRotation.DEG_90, PcbRotation.DEG_180,
        PcbRotation.DEG_270
    };

    /* x/y affine matrices and width/height offsets, authored independently of
       PcbRotation and PcbPackagePose. Rows are 0, 90, 180 and 270 degrees. */
    private static final int[][] EXPECTED_TRANSFORMS = {
        { 1, 0, 0, 1, 0, 0, 0, 0 },
        { 0,-1, 1, 0, 0, 1, 0, 0 },
        {-1, 0, 0,-1, 1, 0, 0, 1 },
        { 0, 1,-1, 0, 0, 0, 1, 0 }
    };

    private static final class TerminalCase {
        final String id;
        final int[] pad;
        final int[] connectedBody;
        final int[] liftedEnd;
        final int escapeX;
        final int escapeY;
        final int escapeLength;

        TerminalCase(String id, int px, int py, int bx, int by, int lx, int ly,
                int dx, int dy, int length) {
            this.id = id;
            pad = new int[] { px, py };
            connectedBody = new int[] { bx, by };
            liftedEnd = new int[] { lx, ly };
            escapeX = dx;
            escapeY = dy;
            escapeLength = length;
        }
    }

    private static final class VariantCase {
        final String key;
        final int width;
        final int height;
        final int[] body;
        final int[] keepOut;
        final int[] courtyard;
        final int[] selection;
        final int[] drag;
        final TerminalCase[] terminals;

        VariantCase(String key, int width, int height, int[] body, int[] keepOut,
                int[] courtyard, int[] selection, int[] drag, TerminalCase[] terminals) {
            this.key = key;
            this.width = width;
            this.height = height;
            this.body = body;
            this.keepOut = keepOut;
            this.courtyard = courtyard;
            this.selection = selection;
            this.drag = drag;
            this.terminals = terminals;
        }
    }

    private static final class PackageCase {
        final PhysicalPackage physicalPackage;
        final String componentId;
        final String[] netIds;
        final VariantCase variant;

        PackageCase(PhysicalPackage physicalPackage, String componentId, String[] netIds,
                VariantCase variant) {
            this.physicalPackage = physicalPackage;
            this.componentId = componentId;
            this.netIds = netIds;
            this.variant = variant;
        }
    }

    private static final class BoardFixture {
        final TroubleshootBoard board;
        final BoardComponent component;

        BoardFixture(TroubleshootBoard board, BoardComponent component) {
            this.board = board;
            this.component = component;
        }
    }

    private interface Attempt { void run(); }

    public static void main(String[] args) throws Exception {
        CirSim prior = CircuitElm.sim, singleton = CirSim.theSim;
        try {
            // Stock capacitor allocation uses CircuitElm.drag's real grid owner.
            CirSim sim = new CirSim();
            sim.gridSize = 16; sim.gridMask = ~15; sim.gridRound = 7;
            CircuitElm.sim = sim;
            runFoundation();
        } finally {
            CircuitElm.sim = prior;
            CirSim.theSim = singleton;
        }
        check(CircuitElm.sim == prior && CirSim.theSim == singleton,
            "native fixture restores prior simulator owners");
        System.out.println("PASS: PCB passive pose foundation contracts assertions=" +
            assertions + " packages=4 variants=7 poses=28");
    }

    private static void runFoundation() throws Exception {
        PackageCase[] cases = expectedCases();
        packageDeclarations(cases);
        for (PackageCase item : cases)
            variantAndPose(item);
        standardProviderTopZero(cases);
        unchangedVariantDraws(cases);
        stockOwnersFitSlots(cases);
        unsupportedAndForeignCases(cases);
        dependencyIdentityBoundary(cases);
        literalTurnedSpotChecks(cases);
    }

    private static PackageCase[] expectedCases() {
        return new PackageCase[] {
            axialResistor(220, "SPAN_220"),
            axialResistor(240, "SPAN_240"),
            axialResistor(260, "SPAN_260"),
            axialDiode(230, "SPAN_230"),
            axialDiode(250, "SPAN_250"),
            electrolytic(),
            ceramic()
        };
    }

    private static PackageCase axialResistor(int span, String key) {
        VariantCase variant = new VariantCase(key, span, 70,
            values(70, 18, span - 140, 34), values(70, 18, span - 140, 34),
            values(12, 5, span - 24, 60), values(11, 11, span - 22, 45),
            values(5, 5, span - 10, 57), new TerminalCase[] {
                new TerminalCase("1", 30, 30, 75, 30, 82, 30, -1, 0, 50),
                new TerminalCase("2", span - 30, 30, span - 75, 30,
                    span - 82, 30, 1, 0, 50)
            });
        return new PackageCase(PhysicalPackages.AXIAL_RESISTOR, "R1",
            new String[] { "N_R1_1", "N_R1_2" }, variant);
    }

    private static PackageCase axialDiode(int span, String key) {
        VariantCase variant = new VariantCase(key, span, 70,
            values(72, 19, span - 144, 32), values(72, 19, span - 144, 32),
            values(12, 5, span - 24, 60), values(11, 11, span - 22, 44),
            values(5, 5, span - 10, 56), new TerminalCase[] {
                new TerminalCase("A", 30, 30, 72, 30, 78, 30, -1, 0, 50),
                new TerminalCase("K", span - 30, 30, span - 72, 30,
                    span - 78, 30, 1, 0, 50)
            });
        return new PackageCase(PhysicalPackages.AXIAL_DIODE, "D1",
            new String[] { "N_D1_A", "N_D1_K" }, variant);
    }

    private static PackageCase electrolytic() {
        VariantCase variant = new VariantCase("DEFAULT", 120, 120,
            values(20, 8, 70, 64), values(12, 4, 96, 84), values(5, 4, 110, 110),
            values(11, -4, 88, 80), values(5, -10, 109, 104), new TerminalCase[] {
                new TerminalCase("+", 30, 30, 48, 8, 48, 4, 0, -1, 38),
                new TerminalCase("-", 80, 30, 62, 8, 62, 4, 0, -1, 38)
            });
        return new PackageCase(PhysicalPackages.RADIAL_ELECTROLYTIC_CAPACITOR,
            "C1", new String[] { "N_C1_PLUS", "N_C1_MINUS" }, variant);
    }

    private static PackageCase ceramic() {
        VariantCase variant = new VariantCase("DEFAULT", 90, 90,
            values(15, 8, 50, 53), values(10, 4, 70, 65), values(5, 0, 80, 90),
            values(1, -2, 78, 67), values(-5, -8, 91, 83), new TerminalCase[] {
                new TerminalCase("1", 20, 30, 28, 10, 28, 6, 0, -1, 35),
                new TerminalCase("2", 60, 30, 52, 10, 52, 6, 0, -1, 35)
            });
        return new PackageCase(PhysicalPackages.RADIAL_CERAMIC_CAPACITOR,
            "C2", new String[] { "N_C2_1", "N_C2_2" }, variant);
    }

    private static void packageDeclarations(PackageCase[] cases) {
        PhysicalPackage[] packages = {
            PhysicalPackages.AXIAL_RESISTOR,
            PhysicalPackages.AXIAL_DIODE,
            PhysicalPackages.RADIAL_ELECTROLYTIC_CAPACITOR,
            PhysicalPackages.RADIAL_CERAMIC_CAPACITOR
        };
        String[][] variantKeys = {
            { "SPAN_220", "SPAN_240", "SPAN_260" },
            { "SPAN_230", "SPAN_250" },
            { "DEFAULT" },
            { "DEFAULT" }
        };
        String[][] terminalIds = {
            { "1", "2" }, { "A", "K" }, { "+", "-" }, { "1", "2" }
        };
        String[] defaults = { "SPAN_220", "SPAN_230", "DEFAULT", "DEFAULT" };
        PhysicalPackage.GeometryVariantSelection[] selection = {
            PhysicalPackage.GeometryVariantSelection.SEEDED_CATALOG,
            PhysicalPackage.GeometryVariantSelection.SEEDED_CATALOG,
            PhysicalPackage.GeometryVariantSelection.FIXED_DEFAULT,
            PhysicalPackage.GeometryVariantSelection.FIXED_DEFAULT
        };
        for (int i = 0; i < packages.length; i++) {
            PhysicalPackage physical = packages[i];
            check(physical.getId().equals(new String[] { "AXIAL_RESISTOR",
                    "AXIAL_DIODE", "RADIAL_ELECTROLYTIC_CAPACITOR",
                    "RADIAL_CERAMIC_CAPACITOR" }[i]), "canonical package ID " + i);
            check(physical.getAllowedRotations().equals(rotations()),
                "only selected passive packages declare all top cardinal rotations " + physical.getId());
            check(physical.getAllowedMountingSides().equals(topOnly()),
                "passive package stays top-face only " + physical.getId());
            check(physical.getTerminalIds().equals(strings(terminalIds[i])),
                "ordered terminal IDs stay fixed " + physical.getId());
            check(physical.getGeometryVariantSelection() == selection[i] &&
                    physical.getDefaultLooseGeometryVariantKey().equals(defaults[i]),
                "variant selection/default stays fixed " + physical.getId());
            check(physical.getGeometryVariants().size() == variantKeys[i].length,
                "finite variant count stays fixed " + physical.getId());
            for (int variant = 0; variant < variantKeys[i].length; variant++)
                check(physical.getGeometryVariants().get(variant).getKey().equals(
                    variantKeys[i][variant]), "canonical variant order " + physical.getId() +
                    " index=" + variant);
            check(!physical.isInternallyConnected(terminalIds[i][0], terminalIds[i][1]),
                "passive terminals remain electrically distinct " + physical.getId());
        }
        check(cases.length == 7, "independent fixture covers all seven current geometries");
    }

    private static void variantAndPose(PackageCase item) {
        PhysicalPackage physical = item.physicalPackage;
        VariantCase expected = item.variant;
        PhysicalPackage.GeometryVariant variant = physical.getGeometryVariant(expected.key);
        check(variant != null && "IDENTITY".equals(variant.getTransformKey()),
            "declared catalog key and identity transform " + physical.getId() + "/" + expected.key);
        PhysicalPackageGeometry geometry = variant.getGeometry();
        localGeometry(physical, geometry, expected);
        for (PcbRotation rotation : ROTATIONS)
            placedGeometry(item, geometry, rotation);
    }

    private static void localGeometry(PhysicalPackage physical,
            PhysicalPackageGeometry geometry, VariantCase expected) {
        check(geometry.getWidth() == expected.width && geometry.getHeight() == expected.height,
            "local dimensions " + physical.getId() + "/" + expected.key);
        check(geometry.getGeometryContractVersionValue() == 3,
            "existing geometry contract version " + physical.getId() + "/" + expected.key);
        check(geometry.getNominalBounds().equals(new Rectangle(0, 0, expected.width,
                expected.height)),
            "nominal package bounds " + physical.getId() + "/" + expected.key);
        check(geometry.getBodyBounds().equals(bounds(expected.body)),
            "body bounds " + physical.getId() + "/" + expected.key);
        check(geometry.getBodyKeepOut().equals(bounds(expected.keepOut)),
            "body keep-out " + physical.getId() + "/" + expected.key);
        check(geometry.getRoutingCourtyard().equals(bounds(expected.courtyard)),
            "routing courtyard/access region " + physical.getId() + "/" + expected.key);
        check(geometry.getSelectionEnvelope().equals(bounds(expected.selection)),
            "selection envelope " + physical.getId() + "/" + expected.key);
        check(geometry.getDragEnvelope().equals(bounds(expected.drag)),
            "drag envelope " + physical.getId() + "/" + expected.key);
        check(geometry.getTerminalIds().equals(terminalStrings(expected.terminals)),
            "stable ordered package terminal IDs " + physical.getId() + "/" + expected.key);
        for (int i = 0; i < expected.terminals.length; i++) {
            TerminalCase terminal = expected.terminals[i];
            PhysicalPackageGeometry.Terminal actual = geometry.getTerminal(i);
            check(actual.getTerminalId().equals(terminal.id), "terminal identity/order " +
                physical.getId() + "/" + expected.key + "/" + terminal.id);
            check(actual.getPadCenter().equals(point(terminal.pad)), "pad center " + terminal.id);
            check(actual.getPadBounds().equals(centered(terminal.pad, 26, 26)),
                "pad bounds " + terminal.id);
            check(actual.getBoardPadProbeCenter().equals(point(terminal.pad)) &&
                    actual.getBoardPadProbeBounds().equals(centered(terminal.pad, 30, 30)),
                "board-pad probe surface " + terminal.id);
            check(actual.getConnectedLead().getEndPoint().equals(point(terminal.pad)) &&
                    actual.getConnectedLead().getBodyPoint().equals(point(terminal.connectedBody)) &&
                    actual.getConnectedLead().getBounds().equals(
                        leadBounds(terminal.pad, terminal.connectedBody)),
                "connected lead geometry " + terminal.id);
            check(actual.getConnectedLead().getComponentProbeCenter().equals(
                    point(terminal.connectedBody)) && actual.getConnectedLead()
                    .getComponentProbeBounds().equals(centered(terminal.connectedBody, 8, 8)),
                "connected lead probe " + terminal.id);
            check(actual.getLiftedLead().getEndPoint().equals(point(terminal.liftedEnd)) &&
                    actual.getLiftedLead().getBodyPoint().equals(point(terminal.connectedBody)) &&
                    actual.getLiftedLead().getBounds().equals(
                        leadBounds(terminal.liftedEnd, terminal.connectedBody)),
                "lifted lead geometry " + terminal.id);
            check(actual.getLiftedLead().getComponentProbeCenter().equals(
                    point(terminal.liftedEnd)) && actual.getLiftedLead()
                    .getComponentProbeBounds().equals(centered(terminal.liftedEnd, 8, 8)),
                "lifted lead probe " + terminal.id);
            check(actual.getEscapeDx() == terminal.escapeX &&
                    actual.getEscapeDy() == terminal.escapeY &&
                    actual.getEscapeLength() == terminal.escapeLength &&
                    actual.getAttachment() == PcbTerminalAttachment.PLATED_THROUGH_HOLE,
                "literal copper escape/access vector " + terminal.id);
        }
    }

    private static void placedGeometry(PackageCase item, PhysicalPackageGeometry geometry,
            PcbRotation rotation) {
        BoardFixture fixture = board(item);
        PcbPackagePose pose = new PcbPackagePose(BOARD_X, BOARD_Y, rotation, PcbBoardSide.TOP);
        PcbFootprint footprint = PcbFootprint.fromPhysicalPackage(fixture.component, pose, geometry);
        PcbComponentPlacement placement = footprint.getPlacement();
        int turns = rotation.ordinal();
        int expectedWidth = (turns & 1) == 0 ? geometry.getWidth() : geometry.getHeight();
        int expectedHeight = (turns & 1) == 0 ? geometry.getHeight() : geometry.getWidth();
        check(placement.getPhysicalPackage() == item.physicalPackage &&
                placement.getPhysicalGeometry() == geometry && placement.getRotation() == rotation &&
                placement.getMountingSide() == PcbBoardSide.TOP,
            "canonical package/geometry and requested pose " + item.physicalPackage.getId() +
                "/" + item.variant.key + "/" + rotation);
        check(placement.getBounds().equals(new Rectangle(BOARD_X, BOARD_Y, expectedWidth,
                expectedHeight)),
            "placed dimensions " + item.physicalPackage.getId() + "/" + item.variant.key +
                "/" + rotation);
        check(placement.getBodyBounds().equals(projectRect(item.variant.body, geometry,
                turns, BOARD_X, BOARD_Y)) && placement.getKeepOut().equals(projectRect(
                item.variant.keepOut, geometry, turns, BOARD_X, BOARD_Y)) &&
                placement.getRoutingCourtyard().equals(projectRect(item.variant.courtyard,
                    geometry, turns, BOARD_X, BOARD_Y)),
            "placed body, keep-out and courtyard " + item.physicalPackage.getId() + "/" +
                item.variant.key + "/" + rotation);
        check(placement.getSelectionEnvelope().equals(projectRect(item.variant.selection,
                geometry, turns, BOARD_X, BOARD_Y)) && placement.getDragEnvelope().equals(
                projectRect(item.variant.drag, geometry, turns, BOARD_X, BOARD_Y)),
            "placed selection and drag access " + item.physicalPackage.getId() + "/" +
                item.variant.key + "/" + rotation);
        check(footprint.getPads().size() == item.variant.terminals.length,
            "pad count " + item.physicalPackage.getId() + "/" + item.variant.key);
        for (int i = 0; i < item.variant.terminals.length; i++)
            placedTerminal(item, geometry, turns, fixture, footprint, i);

        PhysicalBoardSlot slot = new PhysicalBoardSlot(fixture.board, item.componentId);
        check(slot.getPadIds().equals(expectedPadIds(item)) &&
                slot.getTerminalIds().equals(terminalStrings(item.variant.terminals)) &&
                slot.getNetIds().equals(strings(item.netIds)),
            "physical slot keeps pad, terminal and net IDs " + item.physicalPackage.getId() +
                "/" + item.variant.key + "/" + rotation);
        slot.bindGeometryRealization(placement);
        PhysicalPart<?> stock = stockPart(item);
        slot.install(stock);
        check(slot.getInstalledPart() == stock && stock.isInstalled() &&
                stock.getPackage() == item.physicalPackage &&
                stock.getGeometryRealization() != null &&
                stock.getGeometryRealization().getGeometry() == geometry,
            "catalog stock admitted and bound to its physical slot " +
                item.physicalPackage.getId() + "/" + item.variant.key + "/" + rotation);
    }

    private static void placedTerminal(PackageCase item, PhysicalPackageGeometry geometry,
            int turns, BoardFixture fixture, PcbFootprint footprint, int index) {
        TerminalCase expected = item.variant.terminals[index];
        String padId = item.componentId + "." + expected.id;
        PcbPadPlacement pad = footprint.getPad(padId);
        Point padPoint = projectPoint(expected.pad, geometry, turns, BOARD_X, BOARD_Y);
        int[] escape = projectDirection(expected.escapeX, expected.escapeY, turns);
        check(pad.getPadId().equals(padId) && pad.getX() == padPoint.x &&
                pad.getY() == padPoint.y && pad.getMountingSide() == PcbBoardSide.TOP,
            "placed stable pad ID/center " + padId);
        check(pad.getPadBounds().equals(projectRect(centered(expected.pad, 26, 26),
                geometry, turns, BOARD_X, BOARD_Y)) && pad.getProbeBounds().equals(
                projectRect(centered(expected.pad, 30, 30), geometry, turns, BOARD_X, BOARD_Y)),
            "placed pad/probe bounds " + padId);
        check(pad.getEscapeDx() == escape[0] && pad.getEscapeDy() == escape[1] &&
                pad.getEscapeLength() == expected.escapeLength &&
                pad.isInEscapeCorridor(padPoint.x + escape[0] * expected.escapeLength,
                    padPoint.y + escape[1] * expected.escapeLength),
            "placed access vector reaches its declared end " + padId);
        check(footprint.getPlacement().getBoardPadProbeCenter(index).equals(padPoint) &&
                footprint.getPlacement().getBoardPadProbeBounds(index).equals(projectRect(
                    centered(expected.pad, 30, 30), geometry, turns, BOARD_X, BOARD_Y)) &&
                placementPoint(footprint, index, false, false).equals(padPoint) &&
                placementPoint(footprint, index, true, false).equals(projectPoint(
                    expected.connectedBody, geometry, turns, BOARD_X, BOARD_Y)) &&
                placementRect(footprint, index, false).equals(projectRect(
                    leadBounds(expected.pad, expected.connectedBody), geometry, turns,
                    BOARD_X, BOARD_Y)), "connected lead endpoints/body/bounds " + padId);
        check(placementPoint(footprint, index, false, true).equals(projectPoint(
                    expected.liftedEnd, geometry, turns, BOARD_X, BOARD_Y)) &&
                placementPoint(footprint, index, true, true).equals(projectPoint(
                    expected.connectedBody, geometry, turns, BOARD_X, BOARD_Y)) &&
                placementRect(footprint, index, true).equals(projectRect(
                    leadBounds(expected.liftedEnd, expected.connectedBody), geometry,
                    turns, BOARD_X, BOARD_Y)), "lifted lead endpoints/body/bounds " + padId);
        check(placementProbe(footprint, index, false).equals(projectPoint(
                    expected.connectedBody, geometry, turns, BOARD_X, BOARD_Y)) &&
                placementProbe(footprint, index, true).equals(projectPoint(
                    expected.liftedEnd, geometry, turns, BOARD_X, BOARD_Y)),
            "connected and lifted component probe centers " + padId);
        check(placementProbeBounds(footprint, index, false).equals(projectRect(
                    centered(expected.connectedBody, 8, 8), geometry, turns, BOARD_X, BOARD_Y)) &&
                placementProbeBounds(footprint, index, true).equals(projectRect(
                    centered(expected.liftedEnd, 8, 8), geometry, turns, BOARD_X, BOARD_Y)),
            "connected and lifted component probe bounds " + padId);
        check(fixture.board.getPad(padId).getTerminalId().equals(expected.id) &&
                fixture.board.getPad(padId).getNetId().equals(item.netIds[index]) &&
                fixture.board.getNet(item.netIds[index]).getPadIds().equals(
                    strings(new String[] { padId })),
            "board pad preserves terminal and net IDs " + padId);
    }

    private static Point placementPoint(PcbFootprint footprint, int index,
            boolean body, boolean lifted) {
        PcbComponentPlacement placement = footprint.getPlacement();
        if (body)
            return placement.getLeadBodyPoint(index, lifted);
        return placement.getLeadEndPoint(index, lifted);
    }

    private static Rectangle placementRect(PcbFootprint footprint, int index,
            boolean lifted) {
        return footprint.getPlacement().getLeadBounds(index, lifted);
    }

    private static Point placementProbe(PcbFootprint footprint, int index, boolean lifted) {
        return footprint.getPlacement().getComponentLeadProbeCenter(index, lifted);
    }

    private static Rectangle placementProbeBounds(PcbFootprint footprint, int index,
            boolean lifted) {
        return footprint.getPlacement().getComponentLeadProbeBounds(index, lifted);
    }

    private static void standardProviderTopZero(PackageCase[] cases) {
        PcbFootprintRegistry registry = StandardPcbFootprintProviders.createRegistry();
        PhysicalPackage[] seen = new PhysicalPackage[4];
        int seenCount = 0;
        for (PackageCase item : cases) {
            boolean alreadySeen = false;
            for (int i = 0; i < seenCount; i++)
                if (seen[i] == item.physicalPackage)
                    alreadySeen = true;
            if (alreadySeen)
                continue;
            seen[seenCount++] = item.physicalPackage;
            BoardFixture fixture = board(item);
            PcbFootprint footprint = registry.create(fixture.component, BOARD_X, BOARD_Y,
                new Random(451L), bounds(-100, -100, 2400, 1200));
            check(registry.getProvider(item.physicalPackage) != null &&
                    footprint.getPlacement().getPhysicalPackage() == item.physicalPackage &&
                    footprint.getPlacement().getRotation() == PcbRotation.DEG_0 &&
                    footprint.getPlacement().getMountingSide() == PcbBoardSide.TOP,
                "actual standard typed provider remains canonical TOP0 " +
                    item.physicalPackage.getId());
            check(footprint.getPlacement().getPhysicalGeometry() != null &&
                    item.physicalPackage.acceptsGeometry(
                        footprint.getPlacement().getPhysicalGeometry()) &&
                    item.physicalPackage.getGeometryVariantKey(
                        footprint.getPlacement().getPhysicalGeometry()) != null,
                "standard provider selects only a registered geometry variant " +
                    item.physicalPackage.getId());
        }
        check(seenCount == 4, "all four standard passive providers exercised");
    }

    private static void unchangedVariantDraws(PackageCase[] cases) {
        PhysicalPackage[] seen = new PhysicalPackage[4];
        int seenCount = 0;
        long[] seeds = { 0L, 1L, 42L, -1022L, 0x100000001L };
        for (PackageCase item : cases) {
            boolean alreadySeen = false;
            for (int i = 0; i < seenCount; i++)
                if (seen[i] == item.physicalPackage)
                    alreadySeen = true;
            if (alreadySeen)
                continue;
            seen[seenCount++] = item.physicalPackage;
            PhysicalPackage oldTopZero = topZeroClone(item.physicalPackage);
            for (long seed : seeds) {
                Random oldRandom = new Random(seed);
                Random expandedRandom = new Random(seed);
                PhysicalPackageGeometry oldGeometry = oldTopZero.geometryForPlacement(
                    oldRandom, 400, bounds(0, 0, 1800, 900));
                PhysicalPackageGeometry expandedGeometry =
                    item.physicalPackage.geometryForPlacement(expandedRandom, 400,
                        bounds(0, 0, 1800, 900));
                check(oldGeometry == expandedGeometry &&
                        oldRandom.nextLong() == expandedRandom.nextLong(),
                    "same-seed variant choice and random draw boundary " +
                        item.physicalPackage.getId() + " seed=" + seed);
            }
        }
    }

    private static void stockOwnersFitSlots(PackageCase[] cases) {
        for (PackageCase item : cases) {
            PhysicalPart<?> part = stockPart(item);
            check(part.getPackage() == item.physicalPackage,
                "real stock owner shares the canonical widened descriptor " +
                    item.physicalPackage.getId());
            check(part.getTerminalCount() == item.variant.terminals.length &&
                    terminalIds(part).equals(terminalStrings(item.variant.terminals)),
                "stock terminal identity/order matches package " +
                    item.physicalPackage.getId());
        }
        CapacitorReplacementCatalog electrolyticCatalog = new CapacitorReplacementCatalog();
        CapacitorReplacementCatalog ceramicCatalog = new CapacitorReplacementCatalog(
            PhysicalPackages.RADIAL_CERAMIC_CAPACITOR);
        check(electrolyticCatalog.getEntries().size() == 3 &&
                electrolyticCatalog.getEntries().get(0).getSpecification()
                    .getPhysicalPackage() == PhysicalPackages.RADIAL_ELECTROLYTIC_CAPACITOR,
            "electrolytic stock catalog keeps the canonical widened package");
        check(ceramicCatalog.getEntries().size() == 3 &&
                ceramicCatalog.getEntries().get(0).getSpecification()
                    .getPhysicalPackage() == PhysicalPackages.RADIAL_CERAMIC_CAPACITOR,
            "ceramic stock catalog keeps the canonical widened package");
    }

    private static void unsupportedAndForeignCases(PackageCase[] cases) {
        final PcbFootprintRegistry registry = StandardPcbFootprintProviders.createRegistry();
        for (PackageCase item : cases) {
            final PhysicalPackage physical = item.physicalPackage;
            final PhysicalPackage oldTopZero = topZeroClone(physical);
            check(!oldTopZero.isEquivalentTo(physical),
                "pre-capability TOP0 descriptor is not equivalent to widened canonical " +
                    physical.getId());
            final BoardFixture fixture = board(item);
            final PhysicalPackageGeometry geometry = physical.getGeometryVariant(
                item.variant.key).getGeometry();
            check(!physical.supportsPose(new PcbPackagePose(BOARD_X, BOARD_Y,
                    PcbRotation.DEG_0, PcbBoardSide.BOTTOM)),
                "bottom-side capability remains unsupported " + physical.getId());
            expectIllegal(new Attempt() { public void run() {
                PcbFootprint.fromPhysicalPackage(fixture.component,
                    new PcbPackagePose(BOARD_X, BOARD_Y, PcbRotation.DEG_0,
                        PcbBoardSide.BOTTOM), geometry);
            }}, "bottom pose rejects at footprint admission " + physical.getId());
            expectIllegal(new Attempt() { public void run() {
                registry.getProvider(oldTopZero);
            }}, "typed registry rejects same-ID old TOP0 descriptor " + physical.getId());
        }

        final PhysicalPackageGeometry foreignGeometry =
            PhysicalPackages.RADIAL_CERAMIC_CAPACITOR.getGeometry();
        check(!PhysicalPackages.AXIAL_RESISTOR.acceptsGeometry(foreignGeometry),
            "foreign same-terminal geometry remains outside finite catalog");
        final BoardFixture resistor = board(cases[0]);
        expectIllegal(new Attempt() { public void run() {
            PcbFootprint.fromPhysicalPackage(resistor.component,
                PcbPackagePose.top(BOARD_X, BOARD_Y), foreignGeometry);
        }}, "foreign geometry rejects before footprint realization");

        final PhysicalPackage foreignDescriptor = extraResistorVariant(
            PhysicalPackages.AXIAL_RESISTOR, foreignGeometry);
        expectIllegal(new Attempt() { public void run() {
            registry.getProvider(foreignDescriptor);
        }}, "typed registry rejects foreign descriptor despite matching package ID");

        PhysicalPackage[] untouched = {
            PhysicalPackages.AXIAL_FUSE,
            PhysicalPackages.THROUGH_HOLE_CONNECTOR_2,
            PhysicalPackages.THROUGH_HOLE_OUTPUT_HEADER_2,
            PhysicalPackages.E05_ISOLATION_TRANSFORMER_4,
            PhysicalPackages.ISOLATED_CONVERTER_7
        };
        for (PhysicalPackage physical : untouched) {
            check(physical.getAllowedRotations().equals(singleRotation()),
                "unselected package rotation capabilities stay unchanged " + physical.getId());
            check(!physical.supportsPose(new PcbPackagePose(BOARD_X, BOARD_Y,
                    PcbRotation.DEG_90, PcbBoardSide.TOP)),
                "unselected package still rejects 90 degrees " + physical.getId());
        }
    }

    private static void dependencyIdentityBoundary(PackageCase[] cases) throws Exception {
        PhysicalPackage[] seen = new PhysicalPackage[4];
        int seenCount = 0;
        for (PackageCase item : cases) {
            boolean alreadySeen = false;
            for (int i = 0; i < seenCount; i++)
                if (seen[i] == item.physicalPackage)
                    alreadySeen = true;
            if (alreadySeen)
                continue;
            seen[seenCount++] = item.physicalPackage;
            PhysicalPackage expanded = item.physicalPackage;
            PhysicalPackage oldTopZero = topZeroClone(expanded);
            String oldContext = appendPackage(oldTopZero);
            String newContext = appendPackage(expanded);
            check(!oldContext.equals(newContext) && !oldContext.contains("DEG_90") &&
                    newContext.contains("DEG_90"),
                "real GenerationDependencyContext binds the changed rotation declaration " +
                    expanded.getId());
            PhysicalPackage.GeometryVariant defaultVariant = expanded.getGeometryVariant(
                expanded.getDefaultLooseGeometryVariantKey());
            PhysicalGeometryRealization oldRealization = new PhysicalGeometryRealization(
                oldTopZero, defaultVariant.getGeometry(), defaultVariant.getKey(),
                defaultVariant.getTransformKey(), oldTopZero.getGeometryContractVersion());
            PhysicalGeometryRealization newRealization = new PhysicalGeometryRealization(
                expanded, defaultVariant.getGeometry(), defaultVariant.getKey(),
                defaultVariant.getTransformKey(), expanded.getGeometryContractVersion());
            check(oldRealization.fingerprint().equals(newRealization.fingerprint()),
                "package-local realized identity omits allowed-pose capabilities " +
                    expanded.getId());
            PcbPackagePose topZero = PcbPackagePose.top(BOARD_X, BOARD_Y);
            PcbComponentPlacement oldPlacement = PcbComponentPlacement.fromPhysicalGeometry(
                item.componentId, topZero, oldTopZero, defaultVariant.getGeometry());
            PcbComponentPlacement expandedPlacement =
                PcbComponentPlacement.fromPhysicalGeometry(item.componentId, topZero,
                    expanded, defaultVariant.getGeometry());
            check(oldPlacement.geometryFingerprint().equals(
                    expandedPlacement.geometryFingerprint()),
                "TOP0 physical source fingerprint omits allowed-pose capabilities " +
                    expanded.getId());
        }
    }

    /** Hand-computed asymmetric checks so a matrix-oracle mistake cannot self-confirm. */
    private static void literalTurnedSpotChecks(PackageCase[] cases) {
        literalTurnedPose(cases[0], PcbRotation.DEG_90, new int[] { 140, 230 },
            bounds(118, 270, 34, 80), bounds(105, 212, 60, 196));
        literalTurnedPose(cases[0], PcbRotation.DEG_180, new int[] { 290, 240 },
            bounds(170, 218, 80, 34), bounds(112, 205, 196, 60));
        literalTurnedPose(cases[0], PcbRotation.DEG_270, new int[] { 130, 390 },
            bounds(118, 270, 34, 80), bounds(105, 212, 60, 196));

        literalTurnedPose(cases[3], PcbRotation.DEG_90, new int[] { 140, 230 },
            bounds(119, 272, 32, 86), bounds(105, 212, 60, 206));
        literalTurnedPose(cases[3], PcbRotation.DEG_180, new int[] { 300, 240 },
            bounds(172, 219, 86, 32), bounds(112, 205, 206, 60));
        literalTurnedPose(cases[3], PcbRotation.DEG_270, new int[] { 130, 400 },
            bounds(119, 272, 32, 86), bounds(105, 212, 60, 206));

        literalTurnedPose(cases[5], PcbRotation.DEG_90, new int[] { 190, 230 },
            bounds(148, 220, 64, 70), bounds(106, 205, 110, 110));
        literalTurnedPose(cases[5], PcbRotation.DEG_180, new int[] { 190, 290 },
            bounds(130, 248, 70, 64), bounds(105, 206, 110, 110));
        literalTurnedPose(cases[5], PcbRotation.DEG_270, new int[] { 130, 290 },
            bounds(108, 230, 64, 70), bounds(104, 205, 110, 110));

        literalTurnedPose(cases[6], PcbRotation.DEG_90, new int[] { 160, 220 },
            bounds(129, 215, 53, 50), bounds(100, 205, 90, 80));
        literalTurnedPose(cases[6], PcbRotation.DEG_180, new int[] { 170, 260 },
            bounds(125, 229, 50, 53), bounds(105, 200, 80, 90));
        literalTurnedPose(cases[6], PcbRotation.DEG_270, new int[] { 130, 270 },
            bounds(108, 225, 53, 50), bounds(100, 205, 90, 80));
    }

    private static void literalTurnedPose(PackageCase item, PcbRotation rotation,
            int[] expectedPad, Rectangle expectedBody, Rectangle expectedCourtyard) {
        PhysicalPackageGeometry geometry = item.physicalPackage.getGeometryVariant(
            item.variant.key).getGeometry();
        BoardFixture fixture = board(item);
        PcbFootprint footprint = PcbFootprint.fromPhysicalPackage(fixture.component,
            new PcbPackagePose(BOARD_X, BOARD_Y, rotation, PcbBoardSide.TOP), geometry);
        PcbPadPlacement pad = footprint.getPad(item.componentId + "." +
            item.variant.terminals[0].id);
        PcbComponentPlacement placement = footprint.getPlacement();
        check(pad.getX() == expectedPad[0] && pad.getY() == expectedPad[1],
            "hand-computed turned first-pad center " + item.physicalPackage.getId() +
                "/" + item.variant.key + "/" + rotation);
        check(placement.getBodyBounds().equals(expectedBody) &&
                placement.getRoutingCourtyard().equals(expectedCourtyard),
            "hand-computed turned body/courtyard " + item.physicalPackage.getId() +
                "/" + item.variant.key + "/" + rotation);
    }

    private static String appendPackage(PhysicalPackage physicalPackage) throws Exception {
        Method method = GenerationDependencyContext.class.getDeclaredMethod("appendPackage",
            StringBuilder.class, String.class, PhysicalPackage.class);
        method.setAccessible(true);
        StringBuilder result = new StringBuilder();
        method.invoke(null, result, "passive.package", physicalPackage);
        return result.toString();
    }

    private static PhysicalPackage topZeroClone(PhysicalPackage physical) {
        return new PhysicalPackage(physical.getId(), physical.getTerminalIds(),
            new Vector<String>(), physical.isConnector(), physical.getGeometry(),
            physical.getGeometryVariants(), physical.getDefaultLooseGeometryVariantKey(),
            physical.getGeometryVariantSelection(), singleRotation(), topOnly());
    }

    private static PhysicalPackage extraResistorVariant(PhysicalPackage physical,
            PhysicalPackageGeometry extraGeometry) {
        Vector<PhysicalPackage.GeometryVariant> variants = physical.getGeometryVariants();
        variants.add(new PhysicalPackage.GeometryVariant("SPAN_280_CANARY", "IDENTITY",
            extraGeometry));
        return new PhysicalPackage(physical.getId(), physical.getTerminalIds(),
            new Vector<String>(), physical.isConnector(), physical.getGeometry(), variants,
            physical.getDefaultLooseGeometryVariantKey(), physical.getGeometryVariantSelection(),
            physical.getAllowedRotations(), physical.getAllowedMountingSides());
    }

    private static PhysicalPart<?> stockPart(PackageCase item) {
        if (item.physicalPackage == PhysicalPackages.AXIAL_RESISTOR) {
            ResistorCatalogEntry row = new ResistorReplacementCatalog().getEntries().get(0);
            return new PhysicalResistorPart(row.getId(), row.getNameplate(),
                new ResistorElm(0, 0), null, null, ResistorPartLocation.LOOSE);
        }
        if (item.physicalPackage == PhysicalPackages.AXIAL_DIODE) {
            DiodeCatalogEntry row = new DiodeReplacementCatalog().getEntries().get(0);
            return new PhysicalDiodePart(row.getId(), row.getNameplate(),
                new DiodeElm(0, 0), null, row.isReversedInstallation(),
                DiodePartLocation.LOOSE);
        }
        CapacitorReplacementCatalog catalog = item.physicalPackage ==
                PhysicalPackages.RADIAL_ELECTROLYTIC_CAPACITOR ?
            new CapacitorReplacementCatalog() :
            new CapacitorReplacementCatalog(item.physicalPackage);
        CapacitorCatalogEntry row = catalog.getEntries().get(0);
        CapacitorSpecification specification = row.getSpecification();
        CapacitorElm element = DynamicCapacitorBackingAllocator.create(
            new Vector<CircuitElm>(), specification);
        return new PhysicalCapacitorPart(row.getId(), specification,
            row.getPlayerVisibleNameplate(), element, null, CapacitorPartLocation.LOOSE,
            new PhysicalPartProvenance(PhysicalPartProvenance.CATALOG_ACQUIRED, row.getId()));
    }

    private static BoardFixture board(PackageCase item) {
        TroubleshootBoard board = new TroubleshootBoard("POSE_" + item.componentId);
        BoardNet first = new BoardNet(item.netIds[0]);
        BoardNet second = new BoardNet(item.netIds[1]);
        board.addNet(first);
        board.addNet(second);
        BoardComponent component = new BoardComponent(item.componentId,
            item.physicalPackage.getId(), item.physicalPackage, item.componentId);
        board.addComponent(component);
        for (int i = 0; i < item.variant.terminals.length; i++) {
            TerminalCase terminal = item.variant.terminals[i];
            board.addPad(new BoardPad(item.componentId + "." + terminal.id,
                item.componentId, terminal.id, item.netIds[i]));
        }
        return new BoardFixture(board, component);
    }

    private static Vector<String> terminalIds(PhysicalPart<?> part) {
        Vector<String> ids = new Vector<String>();
        for (PhysicalPartTerminal terminal : part.getTerminals())
            ids.add(terminal.getTerminalName());
        return ids;
    }

    private static Vector<String> expectedPadIds(PackageCase item) {
        Vector<String> result = new Vector<String>();
        for (TerminalCase terminal : item.variant.terminals)
            result.add(item.componentId + "." + terminal.id);
        return result;
    }

    private static Vector<String> terminalStrings(TerminalCase[] terminals) {
        Vector<String> result = new Vector<String>();
        for (TerminalCase terminal : terminals)
            result.add(terminal.id);
        return result;
    }

    private static Vector<String> strings(String[] values) {
        Vector<String> result = new Vector<String>();
        for (String value : values)
            result.add(value);
        return result;
    }

    private static Vector<PcbRotation> rotations() {
        Vector<PcbRotation> result = new Vector<PcbRotation>();
        for (PcbRotation rotation : ROTATIONS)
            result.add(rotation);
        return result;
    }

    private static Vector<PcbRotation> singleRotation() {
        Vector<PcbRotation> result = new Vector<PcbRotation>();
        result.add(PcbRotation.DEG_0);
        return result;
    }

    private static Vector<PcbBoardSide> topOnly() {
        Vector<PcbBoardSide> result = new Vector<PcbBoardSide>();
        result.add(PcbBoardSide.TOP);
        return result;
    }

    private static Point projectPoint(int[] value, PhysicalPackageGeometry geometry,
            int turns, int originX, int originY) {
        int[] transform = EXPECTED_TRANSFORMS[turns];
        int x = transform[0] * value[0] + transform[1] * value[1] +
            transform[4] * geometry.getWidth() + transform[5] * geometry.getHeight();
        int y = transform[2] * value[0] + transform[3] * value[1] +
            transform[6] * geometry.getWidth() + transform[7] * geometry.getHeight();
        return new Point(originX + x, originY + y);
    }

    private static int[] projectDirection(int dx, int dy, int turns) {
        int[] transform = EXPECTED_TRANSFORMS[turns];
        return new int[] { transform[0] * dx + transform[1] * dy,
            transform[2] * dx + transform[3] * dy };
    }

    private static Rectangle projectRect(int[] values, PhysicalPackageGeometry geometry,
            int turns, int originX, int originY) {
        return projectRect(bounds(values), geometry, turns, originX, originY);
    }

    /** Transform all four corners and take their envelope, independent of production helpers. */
    private static Rectangle projectRect(Rectangle value, PhysicalPackageGeometry geometry,
            int turns, int originX, int originY) {
        int[][] corners = {
            { value.x, value.y },
            { value.x + value.width, value.y },
            { value.x, value.y + value.height },
            { value.x + value.width, value.y + value.height }
        };
        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxY = Integer.MIN_VALUE;
        for (int[] corner : corners) {
            Point projected = projectPoint(corner, geometry, turns, 0, 0);
            minX = Math.min(minX, projected.x);
            minY = Math.min(minY, projected.y);
            maxX = Math.max(maxX, projected.x);
            maxY = Math.max(maxY, projected.y);
        }
        return bounds(originX + minX, originY + minY, maxX - minX, maxY - minY);
    }

    private static Rectangle centered(int[] point, int width, int height) {
        return bounds(point[0] - width / 2, point[1] - height / 2, width, height);
    }

    private static Rectangle leadBounds(int[] first, int[] second) {
        int left = Math.min(first[0], second[0]) - 3;
        int top = Math.min(first[1], second[1]) - 3;
        return bounds(left, top, Math.abs(first[0] - second[0]) + 6,
            Math.abs(first[1] - second[1]) + 6);
    }

    private static Rectangle bounds(int[] value) {
        return bounds(value[0], value[1], value[2], value[3]);
    }

    private static Rectangle bounds(int x, int y, int width, int height) {
        return new Rectangle(x, y, width, height);
    }

    private static int[] values(int x, int y, int width, int height) {
        return new int[] { x, y, width, height };
    }

    private static Point point(int[] value) { return new Point(value[0], value[1]); }

    private static void expectIllegal(Attempt attempt, String label) {
        try { attempt.run(); }
        catch (IllegalArgumentException expected) { check(true, label); return; }
        throw new AssertionError("Expected IllegalArgumentException: " + label);
    }

    private static void check(boolean condition, String message) {
        assertions++;
        if (!condition)
            throw new AssertionError(message);
    }
}
