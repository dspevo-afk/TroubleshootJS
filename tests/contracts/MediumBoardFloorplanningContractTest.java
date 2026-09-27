package com.lushprojects.circuitjs1.client;

import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.Vector;

/**
 * Focused contract for the generic medium inventory placement path.
 *
 * <p>The locality receipt is computed from board nets and placed pad
 * coordinates.  It deliberately does not call the planner's topology graph
 * or scoring helpers, so a regression to arbitrary region-row packing is
 * visible through the electrical result.</p>
 */
public final class MediumBoardFloorplanningContractTest {
    private static final long Q30_NET_MST_LIMIT = 28000L;
    private static final long CONNECTOR_NEAREST_PAD_LIMIT = 300L;
    // fac582 seed 0, Concern.PLACEMENT revision 1, semantic key "board".
    private static final long HISTORICAL_P1_LAYOUT_SEED = -5811592868239938363L;
    private static final String HISTORICAL_P1_BOARD_ID = "RB30_CONTROL_BOARD";
    private static final String HISTORICAL_P1_DRIVER_TYPE = "BJT";
    private static final String HISTORICAL_P1_COMMIT =
        "fac582c1150273c01d09bc704dd796c1bf49a135";
    private static final String HISTORICAL_P1_PLAN_BLOB =
        "37191a3d291b6eb1fc438187a3dec27759a411e1";
    private static final String HISTORICAL_P1_FIXTURE_ID =
        "rb30-p1-physical-seed0@1;board=RB30_CONTROL_BOARD;" +
        "topology=RB30_SEPARATE_DIRECT_A_BJT_B_BJT;" +
        "layout=-5811592868239938363;packages=33;pads=82;" +
        "status=RLED+LED1;inputPullDowns=none";
    private static int assertions;

    private MediumBoardFloorplanningContractTest() { }

    public static void main(String[] args) {
        checkGenericOpposingEdgeAnchors();
        TroubleshootBoard historicalBoard = historicalP1Board();
        checkHistoricalFixtureIdentity(historicalBoard);
        printPlacementLocalityCensus(historicalBoard, HISTORICAL_P1_LAYOUT_SEED,
            HISTORICAL_P1_FIXTURE_ID);
        PcbPlacementPlanner.Plan historicalPlacement = place(historicalBoard,
            HISTORICAL_P1_LAYOUT_SEED, 0);
        PcbPlacementPlanner.Plan historicalReplay = place(historicalBoard,
            HISTORICAL_P1_LAYOUT_SEED, 0);
        check(historicalPlacement.materialize().geometryFingerprint().equals(
            historicalReplay.materialize().geometryFingerprint()),
            "historical P1 candidate 0 replays exact geometry");
        check(historicalPlacement.outline.equals(historicalReplay.outline),
            "historical P1 candidate 0 replays exact outline");
        checkGeometryAndAccess(historicalBoard, historicalPlacement, 33, 82,
            HISTORICAL_P1_FIXTURE_ID);
        checkConnectorLocality(historicalBoard, historicalPlacement);
        checkJloadOutputRelationship(historicalBoard, HISTORICAL_P1_LAYOUT_SEED,
            HISTORICAL_P1_FIXTURE_ID);
        Locality historicalLocality = measureElectricalLocality(historicalBoard,
            historicalPlacement);
        check(historicalLocality.totalMst <= Q30_NET_MST_LIMIT,
            "historical P1 electrical net locality stays below the fixed MST bound: " +
                historicalLocality.totalMst);

        checkCurrentRecipes();

        System.out.println("PASS: medium board floorplanning contracts assertions=" +
            assertions + " historicalFixture=" + HISTORICAL_P1_FIXTURE_ID +
            " historicalNetMst=" + historicalLocality.totalMst +
            " historicalNetEdges=" + historicalLocality.edgeCount +
            " currentRecipes=33/35/37");
    }

    /** Ordinary placement demands may anchor both sides of one semantic region. */
    private static void checkGenericOpposingEdgeAnchors() {
        TroubleshootBoard board = genericTwentyPartBoard();
        PcbPlacementConstraints constraints = board.getPlacementConstraints();
        check(board.getComponentIds().size() == 20,
            "generic fixture exercises the medium inventory path");
        PcbPlacementConstraints.Part input = constraints.get("JIN");
        PcbPlacementConstraints.Part output = constraints.get("JOUT");
        check(input.anchor == PcbPlacementConstraints.Anchor.EDGE &&
            output.anchor == PcbPlacementConstraints.Anchor.EDGE &&
            input.regionId.equals(output.regionId) &&
            input.domainId.equals(output.domainId),
            "ordinary constraints put both edge connectors in one region");

        int opposing = 0;
        for (int candidate = 0; candidate < PcbPlacementPlanner.OUTLINE_CANDIDATES;
                candidate++) {
            PcbPlacementPlanner.Plan placed = new PcbPlacementPlanner(
                StandardPcbFootprintProviders.createRegistry()).plan(
                    board, constraints, 0L, candidate);
            PcbPlacementPlanner.Plan replay = new PcbPlacementPlanner(
                StandardPcbFootprintProviders.createRegistry()).plan(
                    board, constraints, 0L, candidate);
            check(placed.footprints.size() == 20 && placed.evaluations > 0,
                "all generic medium packages receive scored placements: " + candidate);
            check(placed.outline.equals(replay.outline) &&
                placed.materialize().geometryFingerprint().equals(
                    replay.materialize().geometryFingerprint()),
                "generic medium candidate replays exact geometry: " + candidate);
            PcbFootprint leftOrRightInput = null, leftOrRightOutput = null;
            for (PcbFootprint footprint : placed.footprints) {
                String id = footprint.getPlacement().getComponentId();
                if ("JIN".equals(id)) leftOrRightInput = footprint;
                if ("JOUT".equals(id)) leftOrRightOutput = footprint;
                check(inside(placed.outline,
                    footprint.getPlacement().getRoutingCourtyard()),
                    "generic medium geometry stays finite and inside outline: " + id);
            }
            check(leftOrRightInput != null && leftOrRightOutput != null,
                "both generic edge connectors are placed: " + candidate);
            long midpoint = (long) placed.outline.x + placed.outline.width / 2;
            long inputX = (long) leftOrRightInput.getPlacement().getX() +
                leftOrRightInput.getPlacement().getWidth() / 2;
            long outputX = (long) leftOrRightOutput.getPlacement().getX() +
                leftOrRightOutput.getPlacement().getWidth() / 2;
            boolean opposite = (inputX < midpoint && outputX > midpoint) ||
                (inputX > midpoint && outputX < midpoint);
            if (opposite) opposing++;
            if (candidate == 0) check(inputX > midpoint && outputX < midpoint,
                "seed 0 candidate 0 resolves same-region JIN right and JOUT left");
        }
        check(opposing > 0,
            "six deterministic generic candidates include opposing same-region anchors");
    }

    private static TroubleshootBoard genericTwentyPartBoard() {
        TroubleshootBoard board = new TroubleshootBoard("MEDIUM_GENERIC_EDGE_REGION");
        board.addNet(new BoardNet("SUPPLY", BoardNet.RoutingRole.SUPPLY));
        board.addNet(new BoardNet("RETURN", BoardNet.RoutingRole.RETURN));
        for (int channel = 1; channel <= 8; channel++)
            board.addNet(new BoardNet("S" + channel));
        for (String connector : new String[] { "JIN", "JOUT" }) {
            board.addComponent(new BoardComponent(connector, "CONNECTOR",
                PhysicalPackages.THROUGH_HOLE_CONNECTOR_2));
            board.addPad(new BoardPad(connector + ".1", connector, "1", "SUPPLY"));
            board.addPad(new BoardPad(connector + ".2", connector, "2", "RETURN"));
        }
        for (int channel = 1; channel <= 8; channel++) {
            String resistor = "R" + channel, capacitor = "C" + channel;
            String signal = "S" + channel;
            board.addComponent(new BoardComponent(resistor, "RESISTOR",
                PhysicalPackages.AXIAL_RESISTOR));
            board.addPad(new BoardPad(resistor + ".1", resistor, "1", "SUPPLY"));
            board.addPad(new BoardPad(resistor + ".2", resistor, "2", signal));
            board.addComponent(new BoardComponent(capacitor, "CAPACITOR",
                PhysicalPackages.RADIAL_CERAMIC_CAPACITOR));
            board.addPad(new BoardPad(capacitor + ".1", capacitor, "1", signal));
            board.addPad(new BoardPad(capacitor + ".2", capacitor, "2", "RETURN"));
        }
        for (int channel = 1; channel <= 2; channel++) {
            String diode = "D" + channel;
            board.addComponent(new BoardComponent(diode, "DIODE",
                PhysicalPackages.AXIAL_DIODE));
            board.addPad(new BoardPad(diode + ".A", diode, "A", "RETURN"));
            board.addPad(new BoardPad(diode + ".K", diode, "K", "S" + channel));
        }
        board.validate();
        return board;
    }

    private static void checkHistoricalFixtureIdentity(TroubleshootBoard board) {
        check(HISTORICAL_P1_BOARD_ID.equals(board.getId()),
            "historical P1 fixture retains its board identity");
        check(board.getComponentIds().size() == 33,
            "historical P1 fixture has exactly 33 physical packages");
        check(board.getPadIds().size() == 82,
            "historical P1 fixture has exactly 82 board pads");
        check(board.getComponent("RLED") != null &&
            board.getComponent("LED1") != null,
            "historical P1 fixture includes its status indicator");
        check(board.getComponent("RPIN_A") == null &&
            board.getComponent("RPIN_B") == null,
            "historical P1 fixture has no current sensor pull-down packages");
        check(board.getComponent("CFLT_A") == null &&
            board.getComponent("CFLT_B") == null,
            "historical P1 fixture has no current filtered-input packages");
        check(board.getNet("REF_SHARED") == null &&
            "A_REF".equals(board.getPad("U2A.REFERENCE").getNetId()) &&
            "B_REF".equals(board.getPad("U2B.REFERENCE").getNetId()),
            "historical P1 fixture retains separate A_REF and B_REF nets");
        check(HISTORICAL_P1_DRIVER_TYPE.equals(
                board.getComponent("QA").getType()) &&
            HISTORICAL_P1_DRIVER_TYPE.equals(board.getComponent("QB").getType()) &&
            board.getComponent("QA").getPhysicalPackage() == PhysicalPackages.TO92_NPN &&
            board.getComponent("QB").getPhysicalPackage() == PhysicalPackages.TO92_NPN,
            "historical P1 fixture retains BJT NPN drivers in TO92_NPN packages");
        check(board.getNet("LED_FEED") != null,
            "historical P1 fixture retains the status net");
        check(board.getPowerInputIds().size() == 4,
            "historical P1 fixture retains four external power inputs");
        System.out.println("FLOORPLAN_FIXTURE identity=" + HISTORICAL_P1_FIXTURE_ID +
            " sourceCommit=" + HISTORICAL_P1_COMMIT +
            " sourcePlanBlob=" + HISTORICAL_P1_PLAN_BLOB +
            " driverType=" + HISTORICAL_P1_DRIVER_TYPE +
            " driverPackage=TO92_NPN");
    }

    private static void checkCurrentRecipes() {
        // These are the frozen representative rows used by Q30PlanContractTest:
        // one current recipe of each physical size, independent of route results.
        long[] seeds = { 0L, 48L, 56L };
        Rb30Plan.SupportVariant[] expected = {
            Rb30Plan.SupportVariant.COMPACT_33,
            Rb30Plan.SupportVariant.STANDARD_35,
            Rb30Plan.SupportVariant.FILTERED_37
        };
        for (int index = 0; index < seeds.length; index++) {
            Rb30Plan plan = Rb30Plan.resolve(seeds[index]);
            check(plan.supportVariant == expected[index],
                "current Q30 recipe representative retains its support variant: " +
                    seeds[index]);
            TroubleshootBoard board = plan.board();
            printPlacementLocalityCensus(board, plan.layoutSeed,
                "current-" + expected[index].name() + "-seed=" + seeds[index]);
            for (int candidate = 0; candidate < PcbPlacementPlanner.OUTLINE_CANDIDATES;
                    candidate++) {
                PcbPlacementPlanner.Plan placed = place(board, plan.layoutSeed, candidate);
                PcbPlacementPlanner.Plan replay = place(board, plan.layoutSeed, candidate);
                check(placed.materialize().geometryFingerprint().equals(
                    replay.materialize().geometryFingerprint()),
                    "current Q30 recipe candidate replays exact geometry: " +
                        expected[index] + "/" + candidate);
                check(placed.outline.equals(replay.outline),
                    "current Q30 recipe candidate replays exact outline: " +
                        expected[index] + "/" + candidate);
                checkGeometryAndAccess(board, placed,
                    plan.physicalPackageCount(),
                    82 + (plan.hasStatusIndicator() ? 4 : 0) +
                        (plan.hasSensorInputFilters() ? 4 : 0),
                    "current-" + expected[index].name() +
                        "-seed=" + seeds[index] + "/" + candidate);
            }
            checkJloadOutputRelationship(board, plan.layoutSeed,
                "current-" + expected[index].name() + "-seed=" + seeds[index]);

            if (seeds[index] == 0L) {
                Rb30Plan otherPlan = Rb30Plan.resolve(1L);
                check(otherPlan.supportVariant == Rb30Plan.SupportVariant.COMPACT_33,
                    "seed 1 retains the compact current Q30 recipe");
                PcbPlacementPlanner.Plan first = place(board, plan.layoutSeed, 0);
                PcbPlacementPlanner.Plan other = place(board, otherPlan.layoutSeed, 0);
                check(!placementSignature(first).equals(placementSignature(other)),
                    "different current placement seeds produce real variation");
            }
        }
    }

    /**
     * Literal TEST-LOCAL copy of the accepted P1 physical graph.  The source
     * is fac582c1150273c01d09bc704dd796c1bf49a135's Rb30Plan.java; this
     * fixture must remain independent of the current support variants.
     */
    private static TroubleshootBoard historicalP1Board() {
        TroubleshootBoard board = new TroubleshootBoard(HISTORICAL_P1_BOARD_ID);
        for (String id : new String[] {
                "RAW12", "FUSED12", "RAIL12", "EN5", "RAIL5", "LOAD12"
            }) historicalNet(board, id, BoardNet.RoutingRole.SUPPLY);
        for (String id : new String[] { "CTRL_RETURN", "LOAD_RETURN" })
            historicalNet(board, id, BoardNet.RoutingRole.RETURN);
        for (String id : new String[] { "A_RAW", "A_SENSE",
                "A_CMD", "A_DRIVE", "B_RAW", "B_SENSE",
                "B_CMD", "B_DRIVE" })
            historicalNet(board, id, BoardNet.RoutingRole.CONTROL);
        historicalNet(board, "A_REF", BoardNet.RoutingRole.CONTROL);
        historicalNet(board, "B_REF", BoardNet.RoutingRole.CONTROL);
        for (String id : new String[] { "A_COIL_LOW", "B_COIL_LOW",
                "OUT_A", "OUT_B" })
            historicalNet(board, id, BoardNet.RoutingRole.HIGH_CURRENT);
        for (String id : new String[] { "NC_A", "NC_B", "LED_FEED" })
            historicalNet(board, id, BoardNet.RoutingRole.SIGNAL);

        historicalPart(board, "J1", "CONNECTOR",
            PhysicalPackages.THROUGH_HOLE_CONNECTOR_2,
            "RAW12", "CTRL_RETURN");
        historicalPart(board, "F1", "FUSE", PhysicalPackages.AXIAL_FUSE,
            "RAW12", "FUSED12");
        historicalPart(board, "DREV", "DIODE", PhysicalPackages.AXIAL_DIODE,
            "FUSED12", "RAIL12");
        historicalPart(board, "C12", "CAPACITOR",
            PhysicalPackages.RADIAL_CERAMIC_CAPACITOR,
            "FUSED12", "CTRL_RETURN");

        historicalPart(board, "U1", "REGULATOR", PhysicalPackages.TO220_REGULATOR_4,
            "RAIL12", "RAIL5", "CTRL_RETURN", "EN5");
        historicalPart(board, "CIN", "CAPACITOR",
            PhysicalPackages.RADIAL_ELECTROLYTIC_CAPACITOR,
            "RAIL12", "CTRL_RETURN");
        historicalPart(board, "C5", "CAPACITOR",
            PhysicalPackages.RADIAL_CERAMIC_CAPACITOR,
            "RAIL5", "CTRL_RETURN");
        historicalPart(board, "REN", "RESISTOR", PhysicalPackages.AXIAL_RESISTOR,
            "RAIL12", "EN5");

        historicalSensor(board, "A", "A_REF");
        historicalSensor(board, "B", "B_REF");
        for (String channel : new String[] { "A", "B" }) {
            historicalPart(board, "RREF_H" + channel, "RESISTOR",
                PhysicalPackages.AXIAL_RESISTOR,
                "RAIL5", channel + "_REF");
            historicalPart(board, "RREF_L" + channel, "RESISTOR",
                PhysicalPackages.AXIAL_RESISTOR,
                channel + "_REF", "CTRL_RETURN");
        }

        historicalOutput(board, "A");
        historicalOutput(board, "B");
        historicalPart(board, "RLED", "RESISTOR", PhysicalPackages.AXIAL_RESISTOR,
            "RAIL5", "LED_FEED");
        historicalPart(board, "LED1", "LED", PhysicalPackages.THROUGH_HOLE_LED,
            "LED_FEED", "CTRL_RETURN");

        historicalPart(board, "JSA", "CONNECTOR",
            PhysicalPackages.THROUGH_HOLE_CONNECTOR_2,
            "A_RAW", "CTRL_RETURN");
        historicalPart(board, "JSB", "CONNECTOR",
            PhysicalPackages.THROUGH_HOLE_CONNECTOR_2,
            "B_RAW", "CTRL_RETURN");
        historicalPart(board, "JLOAD", "CONNECTOR",
            PhysicalPackages.THROUGH_HOLE_CONNECTOR_2,
            "LOAD12", "LOAD_RETURN");
        historicalPart(board, "JOA", "OUTPUT_HEADER",
            PhysicalPackages.THROUGH_HOLE_OUTPUT_HEADER_2,
            "OUT_A", "LOAD_RETURN");
        historicalPart(board, "JOB", "OUTPUT_HEADER",
            PhysicalPackages.THROUGH_HOLE_OUTPUT_HEADER_2,
            "OUT_B", "LOAD_RETURN");

        board.addPowerInput(new ExternalBoardPowerInput("MAIN12", "J1.1",
            "J1.2", "RAW12", "CTRL_RETURN"));
        board.addPowerInput(new ExternalBoardPowerInput("SENSOR_A", "JSA.1",
            "JSA.2", "A_RAW", "CTRL_RETURN"));
        board.addPowerInput(new ExternalBoardPowerInput("SENSOR_B", "JSB.1",
            "JSB.2", "B_RAW", "CTRL_RETURN"));
        board.addPowerInput(new ExternalBoardPowerInput("LOAD12", "JLOAD.1",
            "JLOAD.2", "LOAD12", "LOAD_RETURN"));
        board.setPlacementConstraints(historicalPlacement(board));
        board.validate();
        return board;
    }

    private static void historicalSensor(TroubleshootBoard board, String channel,
            String referenceNet) {
        historicalPart(board, "U2" + channel, "SENSOR_CONTROL",
            PhysicalPackages.E04_DECISION_CONTROL_5,
            channel + "_SENSE", referenceNet, "RAIL5",
            channel + "_CMD", "CTRL_RETURN");
        historicalPart(board, "RS" + channel, "RESISTOR",
            PhysicalPackages.AXIAL_RESISTOR,
            channel + "_RAW", channel + "_SENSE");
    }

    private static void historicalOutput(TroubleshootBoard board, String channel) {
        historicalPart(board, "RD" + channel, "RESISTOR",
            PhysicalPackages.AXIAL_RESISTOR,
            channel + "_CMD", channel + "_DRIVE");
        historicalPart(board, "RPD" + channel, "RESISTOR",
            PhysicalPackages.AXIAL_RESISTOR,
            channel + "_DRIVE", "CTRL_RETURN");
        historicalPart(board, "Q" + channel, HISTORICAL_P1_DRIVER_TYPE,
            PhysicalPackages.TO92_NPN,
            channel + "_DRIVE", channel + "_COIL_LOW", "CTRL_RETURN");
        historicalPart(board, "D" + channel, "DIODE", PhysicalPackages.AXIAL_DIODE,
            channel + "_COIL_LOW", "RAIL5");
        historicalPart(board, "K" + channel, "RELAY", PhysicalPackages.RELAY_SPDT,
            "RAIL5", channel + "_COIL_LOW", "LOAD12",
            "NC_" + channel, "OUT_" + channel);
    }

    private static void historicalNet(TroubleshootBoard board, String id,
            BoardNet.RoutingRole role) {
        board.addNet(new BoardNet(id, role));
    }

    private static void historicalPart(TroubleshootBoard board, String id,
            String type, PhysicalPackage physicalPackage, String... nets) {
        Vector<String> terminals = physicalPackage.getTerminalIds();
        if (terminals.size() != nets.length)
            throw new IllegalArgumentException("historical Q30 terminal count: " + id);
        board.addComponent(new BoardComponent(id, type, physicalPackage));
        for (int index = 0; index < nets.length; index++)
            board.addPad(new BoardPad(id + "." + terminals.get(index), id,
                terminals.get(index), nets[index]));
    }

    private static PcbPlacementConstraints historicalPlacement(
            TroubleshootBoard board) {
        Vector<PcbPlacementConstraints.Part> parts =
            new Vector<PcbPlacementConstraints.Part>();
        for (String id : board.getComponentIds()) {
            String region = id.equals("J1") || id.equals("F1") ||
                id.equals("DREV") || id.equals("C12") ? "entry" :
                id.equals("U1") || id.equals("CIN") || id.equals("C5") ||
                id.equals("REN") ? "regulation" :
                id.equals("JSA") || id.equals("U2A") || id.equals("RSA") ||
                id.equals("RREFA") || id.equals("RFB_A") ||
                id.equals("RREF_HA") || id.equals("RREF_LA") ? "sensor-a" :
                id.equals("JSB") || id.equals("U2B") || id.equals("RSB") ||
                id.equals("RREFB") || id.equals("RFB_B") ||
                id.equals("RREF_HB") || id.equals("RREF_LB") ? "sensor-b" :
                id.equals("RREF_H") || id.equals("RREF_L") ?
                "sensor-reference" :
                id.equals("RLED") || id.equals("LED1") ? "status" :
                id.endsWith("A") || id.equals("JOA") ? "output-a" :
                id.endsWith("B") || id.equals("JOB") ? "output-b" :
                "output-common";
            String label = region.equals("entry") ? "12 V entry" :
                region.equals("regulation") ? "5 V regulation" :
                region.equals("sensor-a") ? "Sensor A" :
                region.equals("sensor-b") ? "Sensor B" :
                region.equals("sensor-reference") ? "Sensor reference" :
                region.equals("status") ? "Status" :
                region.equals("output-a") ? "Output A" :
                region.equals("output-b") ? "Output B" :
                "Load supply";
            PcbPlacementConstraints.Anchor anchor =
                id.equals("J1") || id.equals("JSA") || id.equals("JSB") ?
                    PcbPlacementConstraints.Anchor.LEFT :
                id.equals("JLOAD") ? PcbPlacementConstraints.Anchor.RIGHT :
                    PcbPlacementConstraints.Anchor.NONE;
            parts.add(new PcbPlacementConstraints.Part(id, region, label,
                "low-voltage-board", anchor, 20));
        }
        return new PcbPlacementConstraints(parts,
            new Vector<PcbPlacementConstraints.Barrier>(), PcbCopperLayer.BOTTOM,
            MediumBoardPhysicalPolicy.ID, MediumBoardPhysicalPolicy.VERSION);
    }

    private static PcbPlacementPlanner.Plan place(TroubleshootBoard board, long seed,
            int candidate) {
        return new PcbPlacementPlanner(StandardPcbFootprintProviders.createRegistry())
            .plan(board, board.getPlacementConstraints(), seed, candidate);
    }

    private static void checkGeometryAndAccess(TroubleshootBoard board,
            PcbPlacementPlanner.Plan plan, int expectedComponents, int expectedPads,
            String fixture) {
        check(board.getComponentIds().size() == expectedComponents,
            fixture + " has its declared physical package count");
        check(board.getPadIds().size() == expectedPads,
            fixture + " has its declared board pad count");
        check(plan.footprints.size() == expectedComponents,
            fixture + " placement has one footprint per package");

        TreeMap<String, PcbFootprint> byComponent = new TreeMap<String, PcbFootprint>();
        TreeSet<String> padIds = new TreeSet<String>();
        Vector<Rectangle> courtyards = new Vector<Rectangle>();
        for (PcbFootprint footprint : plan.footprints) {
            PcbComponentPlacement placement = footprint.getPlacement();
            String componentId = placement.getComponentId();
            check(byComponent.put(componentId, footprint) == null,
                fixture + " placement component IDs are unique: " + componentId);
            BoardComponent component = board.getComponent(componentId);
            check(component != null,
                fixture + " placement component exists on board: " + componentId);
            check(placement.getPhysicalPackage() == component.getPhysicalPackage(),
                fixture + " placement preserves package identity: " + componentId);
            Rectangle courtyard = placement.getRoutingCourtyard();
            check(inside(plan.outline, courtyard),
                fixture + " routing courtyard is inside outline: " + componentId);
            courtyards.add(courtyard);
            for (PcbPadPlacement pad : footprint.getPads()) {
                check(padIds.add(pad.getPadId()),
                    fixture + " placement pad IDs are unique: " + pad.getPadId());
                check(contains(pad.getPadBounds(), pad.getX(), pad.getY()),
                    fixture + " pad center is inside its pad bounds: " + pad.getPadId());
                int escapeX = pad.getX() + pad.getEscapeDx() * pad.getEscapeLength();
                int escapeY = pad.getY() + pad.getEscapeDy() * pad.getEscapeLength();
                check(inside(plan.outline, escapeX, escapeY),
                    fixture + " pad escape endpoint is inside outline: " + pad.getPadId());
                for (PcbFootprint other : plan.footprints) {
                    if (other == footprint) continue;
                    check(!contains(other.getPlacement().getRoutingCourtyard(),
                        escapeX, escapeY),
                        fixture + " pad escape endpoint remains accessible: " +
                            pad.getPadId());
                }
            }
        }
        check(new TreeSet<String>(board.getComponentIds()).equals(
            new TreeSet<String>(byComponent.keySet())),
            fixture + " placement component IDs match board IDs");
        check(new TreeSet<String>(board.getPadIds()).equals(padIds),
            fixture + " placement pad IDs match board IDs");
        for (int first = 0; first < courtyards.size(); first++) {
            for (int second = first + 1; second < courtyards.size(); second++) {
                check(!overlaps(courtyards.get(first), courtyards.get(second)),
                    fixture + " routing courtyards do not overlap");
            }
        }
    }

    private static void printPlacementLocalityCensus(TroubleshootBoard board, long seed,
            String fixture) {
        Vector<MediumBoardPhysicalPolicy.PlacementCandidate> candidates =
            new Vector<MediumBoardPhysicalPolicy.PlacementCandidate>();
        for (int candidate = 0; candidate < PcbPlacementPlanner.OUTLINE_CANDIDATES;
                candidate++) {
            PcbPlacementPlanner.Plan placed = place(board, seed, candidate);
            TreeMap<String, PcbFootprint> footprints = new TreeMap<String, PcbFootprint>();
            for (PcbFootprint footprint : placed.footprints)
                footprints.put(footprint.getPlacement().getComponentId(), footprint);
            StringBuilder distances = new StringBuilder();
            for (String connector : new String[] { "J1", "JSA", "JSB", "JOA", "JOB", "JLOAD" }) {
                if (distances.length() > 0) distances.append(',');
                distances.append(connector).append(':').append(
                    nearestSameNetPadDistance(board, footprints, connector));
            }
            System.out.println("FLOORPLAN_LOCALITY fixture=" + fixture +
                " candidate=" + candidate + " placementSeed=" + seed +
                " components=" + board.getComponentIds().size() +
                " mst=" + measureElectricalLocality(board, placed).totalMst +
                " nearest=" + distances);
            candidates.add(new MediumBoardPhysicalPolicy.PlacementCandidate(board, candidate, placed));
        }
        Vector<MediumBoardPhysicalPolicy.PlacementCandidate> ranked =
            MediumBoardPhysicalPolicy.rank(candidates);
        for (int index = 0; index < ranked.size(); index++)
            System.out.println("FLOORPLAN_RANK fixture=" + fixture + " rank=" + index +
                " routedSubset=" +
                (index < MediumBoardPhysicalPolicy.ROUTING_CANDIDATES) + " " +
                ranked.get(index).toCanonical());
    }

    private static void checkConnectorLocality(TroubleshootBoard board,
            PcbPlacementPlanner.Plan plan) {
        TreeMap<String, PcbFootprint> byComponent = new TreeMap<String, PcbFootprint>();
        for (PcbFootprint footprint : plan.footprints)
            byComponent.put(footprint.getPlacement().getComponentId(), footprint);
        for (String connectorId : new String[] { "J1", "JSA", "JSB", "JOA", "JOB" }) {
            long nearest = nearestSameNetPadDistance(board, byComponent, connectorId);
            check(nearest <= CONNECTOR_NEAREST_PAD_LIMIT,
                "connector has a nearby same-net pad: " + connectorId + " distance=" + nearest);
        }
    }

    private static long nearestSameNetPadDistance(TroubleshootBoard board,
            TreeMap<String, PcbFootprint> byComponent, String componentId) {
        PcbFootprint source = byComponent.get(componentId);
        long best = Long.MAX_VALUE;
        for (PcbPadPlacement pad : source.getPads()) {
            BoardPad boardPad = board.getPad(pad.getPadId());
            BoardNet net = board.getNet(boardPad.getNetId());
            for (String otherPadId : net.getPadIds()) {
                BoardPad otherBoardPad = board.getPad(otherPadId);
                if (componentId.equals(otherBoardPad.getComponentId())) continue;
                PcbPadPlacement other = byComponent.get(otherBoardPad.getComponentId())
                    .getPad(otherPadId);
                long distance = Math.abs((long) pad.getX() - other.getX()) +
                    Math.abs((long) pad.getY() - other.getY());
                if (distance < best) best = distance;
            }
        }
        return best;
    }

    private static JloadRelation checkJloadOutputRelationship(TroubleshootBoard board,
            long layoutSeed, String fixture) {
        TreeMap<String, PcbPlacementConstraints.Part> demands =
            new TreeMap<String, PcbPlacementConstraints.Part>();
        for (PcbPlacementConstraints.Part part : board.getPlacementConstraints().getParts())
            demands.put(part.componentId, part);
        boolean loadHasOutputNeighbor = false;
        BoardNet loadNet = board.getNet("LOAD12");
        for (String padId : loadNet.getPadIds()) {
            BoardPad pad = board.getPad(padId);
            if (!"JLOAD".equals(pad.getComponentId()) &&
                    isOutputRegion(demands.get(pad.getComponentId())))
                loadHasOutputNeighbor = true;
        }
        check(loadHasOutputNeighbor,
            fixture + " JLOAD LOAD12 net reaches an output region");

        JloadRelation result = new JloadRelation();
        for (int candidate = 0; candidate < PcbPlacementPlanner.OUTLINE_CANDIDATES;
                candidate++) {
            PcbPlacementPlanner.Plan placement = place(board, layoutSeed, candidate);
            TreeMap<String, PcbFootprint> byComponent =
                new TreeMap<String, PcbFootprint>();
            for (PcbFootprint footprint : placement.footprints)
                byComponent.put(footprint.getPlacement().getComponentId(), footprint);
            long nearestOutput = Long.MAX_VALUE, nearestOther = Long.MAX_VALUE;
            for (Map.Entry<String, PcbFootprint> entry : byComponent.entrySet()) {
                if ("JLOAD".equals(entry.getKey())) continue;
                long distance = padDistance(byComponent.get("JLOAD"), entry.getValue());
                if (isOutputRegion(demands.get(entry.getKey())))
                    nearestOutput = Math.min(nearestOutput, distance);
                else
                    nearestOther = Math.min(nearestOther, distance);
            }
            check(nearestOutput != Long.MAX_VALUE && nearestOther != Long.MAX_VALUE,
                fixture + " JLOAD has both output and non-output placement cohorts");
            result.outputNearestTotal += nearestOutput;
            result.otherNearestTotal += nearestOther;
        }
        check(result.outputNearestTotal < result.otherNearestTotal,
            fixture + " JLOAD stays closer to output regions across candidates: output=" +
                result.outputNearestTotal + " other=" + result.otherNearestTotal);
        return result;
    }

    private static boolean isOutputRegion(PcbPlacementConstraints.Part part) {
        return part != null && part.regionLabel.startsWith("Output ");
    }

    private static long padDistance(PcbFootprint first, PcbFootprint second) {
        long best = Long.MAX_VALUE;
        for (PcbPadPlacement a : first.getPads()) {
            for (PcbPadPlacement b : second.getPads()) {
                long distance = Math.abs((long) a.getX() - b.getX()) +
                    Math.abs((long) a.getY() - b.getY());
                if (distance < best) best = distance;
            }
        }
        return best;
    }

    private static String placementSignature(PcbPlacementPlanner.Plan plan) {
        TreeMap<String, PcbFootprint> sorted = new TreeMap<String, PcbFootprint>();
        for (PcbFootprint footprint : plan.footprints)
            sorted.put(footprint.getPlacement().getComponentId(), footprint);
        StringBuilder result = new StringBuilder();
        result.append(plan.outline.x).append(',').append(plan.outline.y).append(',')
            .append(plan.outline.width).append(',').append(plan.outline.height).append('|');
        for (Map.Entry<String, PcbFootprint> entry : sorted.entrySet()) {
            PcbComponentPlacement placement = entry.getValue().getPlacement();
            result.append(entry.getKey()).append('@').append(placement.getX()).append(',')
                .append(placement.getY()).append(',').append(placement.getWidth()).append(',')
                .append(placement.getHeight()).append(';');
        }
        return result.toString();
    }

    private static Locality measureElectricalLocality(TroubleshootBoard board,
            PcbPlacementPlanner.Plan plan) {
        TreeMap<String, PcbFootprint> footprints = new TreeMap<String, PcbFootprint>();
        for (PcbFootprint footprint : plan.footprints)
            footprints.put(footprint.getPlacement().getComponentId(), footprint);
        Locality result = new Locality();
        Vector<String> netIds = board.getNetIds();
        Collections.sort(netIds);
        for (String netId : netIds) {
            BoardNet net = board.getNet(netId);
            Vector<String> components = new Vector<String>();
            for (String padId : net.getPadIds()) {
                String componentId = board.getPad(padId).getComponentId();
                if (!components.contains(componentId)) components.add(componentId);
            }
            if (components.size() < 2) continue;
            Collections.sort(components);
            boolean[] used = new boolean[components.size()];
            long[] best = new long[components.size()];
            for (int index = 0; index < best.length; index++) best[index] = Long.MAX_VALUE;
            best[0] = 0;
            for (int edge = 0; edge < components.size(); edge++) {
                int selected = -1;
                for (int index = 0; index < components.size(); index++) {
                    if (!used[index] && (selected < 0 || best[index] < best[selected]))
                        selected = index;
                }
                check(selected >= 0 && best[selected] != Long.MAX_VALUE,
                    "net has a measurable pad connection: " + netId);
                used[selected] = true;
                result.totalMst += best[selected];
                if (edge > 0) result.edgeCount++;
                for (int index = 0; index < components.size(); index++) {
                    if (used[index]) continue;
                    long distance = netDistance(board, net, footprints,
                        components.get(selected), components.get(index));
                    if (distance < best[index]) best[index] = distance;
                }
            }
        }
        return result;
    }

    private static long netDistance(TroubleshootBoard board, BoardNet net,
            TreeMap<String, PcbFootprint> footprints, String first, String second) {
        long best = Long.MAX_VALUE;
        for (String firstPadId : net.getPadIds()) {
            BoardPad firstPad = board.getPad(firstPadId);
            if (!first.equals(firstPad.getComponentId())) continue;
            PcbPadPlacement firstPlacement = footprints.get(first).getPad(firstPadId);
            for (String secondPadId : net.getPadIds()) {
                BoardPad secondPad = board.getPad(secondPadId);
                if (!second.equals(secondPad.getComponentId())) continue;
                PcbPadPlacement secondPlacement = footprints.get(second).getPad(secondPadId);
                long distance = Math.abs((long) firstPlacement.getX() - secondPlacement.getX()) +
                    Math.abs((long) firstPlacement.getY() - secondPlacement.getY());
                if (distance < best) best = distance;
            }
        }
        return best;
    }

    private static boolean inside(Rectangle outer, Rectangle inner) {
        return inner.x >= outer.x && inner.y >= outer.y &&
            (long) inner.x + inner.width <= (long) outer.x + outer.width &&
            (long) inner.y + inner.height <= (long) outer.y + outer.height;
    }

    private static boolean inside(Rectangle outer, int x, int y) {
        return x >= outer.x && y >= outer.y &&
            (long) x <= (long) outer.x + outer.width &&
            (long) y <= (long) outer.y + outer.height;
    }

    private static boolean contains(Rectangle outer, int x, int y) {
        return x >= outer.x && y >= outer.y &&
            (long) x <= (long) outer.x + outer.width &&
            (long) y <= (long) outer.y + outer.height;
    }

    private static boolean overlaps(Rectangle first, Rectangle second) {
        return first.x < (long) second.x + second.width &&
            second.x < (long) first.x + first.width &&
            first.y < (long) second.y + second.height &&
            second.y < (long) first.y + first.height;
    }

    private static void check(boolean condition, String message) {
        assertions++;
        if (!condition) throw new AssertionError(message);
    }

    private static final class Locality {
        long totalMst;
        int edgeCount;
    }

    private static final class JloadRelation {
        long outputNearestTotal;
        long otherNearestTotal;
    }
}
