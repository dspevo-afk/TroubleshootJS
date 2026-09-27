package com.lushprojects.circuitjs1.client;

import java.util.Vector;

/** Focused provider-policy and deterministic medium-generator receipt checks. */
public final class MediumBoardPhysicalPolicyContractTest {
    private static int assertions;

    private MediumBoardPhysicalPolicyContractTest() { }

    public static void main(String[] args) {
        Rb30Plan firstPlan = Rb30Plan.resolve(0L);
        Rb30Plan secondPlan = Rb30Plan.resolve(0L);
        check(firstPlan.canonical().equals(secondPlan.canonical()),
            "Q30 plan canonical replay");
        check(firstPlan.board().getPlacementConstraints().getPhysicalPolicyIdentity().equals(
            MediumBoardPhysicalPolicy.identity()), "Q30 selects medium policy");

        MediumBoardPhysicalPolicy.Result first = new SeededPcbLayoutGenerator()
            .generateWithPolicyResult(firstPlan.board(), firstPlan.layoutSeed,
                firstPlan.routingSeed, NOOP);
        MediumBoardPhysicalPolicy.Result second = new SeededPcbLayoutGenerator()
            .generateWithPolicyResult(secondPlan.board(), secondPlan.layoutSeed,
                secondPlan.routingSeed, NOOP);
        check(first.getStatistics().toCanonical().equals(second.getStatistics().toCanonical()),
            "medium policy work receipt is deterministic");
        check(first.getStatistics().placementCandidates ==
            MediumBoardPhysicalPolicy.PLACEMENT_CANDIDATES,
            "medium policy uses its bounded placement breadth");
        check(first.getStatistics().placementEvaluations > 0,
            "medium receipt exposes bounded planner evaluations");
        check(first.getStatistics().routeAttempts <=
            MediumBoardPhysicalPolicy.ROUTING_CANDIDATES * 2,
            "medium policy routes only its bounded subset");
        check(first.getStatistics().placementScores.size() ==
            first.getStatistics().placementCandidates -
            first.getStatistics().placementRejections,
            "medium receipt exposes every ranked legal placement score");
        check(first.getStatistics().rankedPlacementAttempts.size() ==
            first.getStatistics().placementScores.size(),
            "ranked placement identity and score vectors stay aligned");
        check(first.getStatistics().placementScores.size() > 1 &&
            !first.getStatistics().placementScores.get(0).equals(
                first.getStatistics().placementScores.get(
                    first.getStatistics().placementScores.size() - 1)),
            "medium rank has meaningful candidate score variation");
        String receipt = first.toCanonical();
        check(receipt.indexOf("electrical=") >= 0,
            "medium receipt exposes electrical score component");
        check(receipt.indexOf("region=") >= 0,
            "medium receipt exposes region score component");
        check(receipt.indexOf("connector=") >= 0,
            "medium receipt exposes connector score component");
        check(receipt.indexOf("corridor=") >= 0,
            "medium receipt exposes corridor score component");
        check(receipt.indexOf("selectedScore=") >= 0,
            "medium receipt exposes selected placement score");
        check(receipt.indexOf("placementEvaluations=") >= 0,
            "medium receipt canonical includes planner evaluations");
        check(first.toCanonical().equals(second.toCanonical()),
            "medium policy result identity is deterministic");
        if (first.accepted()) {
            check(first.getStatistics().selectedPlacementScore >= 0,
                "accepted medium result has a selected placement score");
            check(first.getStatistics().selectedRouteQualityMilli >= 0,
                "accepted medium result has a measured route quality");
            if (first.getStatistics().oneFaceSuccesses > 0)
                check(first.getStatistics().twoLayerAttempts == 1,
                    "successful one-face route receives one bounded fuller probe");
            check(first.getLayout().geometryFingerprint().equals(
                second.getLayout().geometryFingerprint()),
                "accepted medium geometry is deterministic");
            first.getLayout().validateGeometry(firstPlan.board());
            check(first.getLayout().getComponents().size() ==
                firstPlan.board().getComponentIds().size(),
                "accepted medium layout preserves component correspondence");
            check(first.getLayout().getPads().size() ==
                firstPlan.board().getPadIds().size(),
                "accepted medium layout preserves pad correspondence");
        }
        check(!MediumBoardPhysicalPolicy.selected(new PcbPlacementConstraints(
            new Vector<PcbPlacementConstraints.Part>(),
            new Vector<PcbPlacementConstraints.Barrier>(), PcbCopperLayer.BOTTOM,
            MediumBoardPhysicalPolicy.ID, MediumBoardPhysicalPolicy.VERSION + 1)),
            "unknown medium policy version is rejected");
        check(new PcbPlacementConstraints(new Vector<PcbPlacementConstraints.Part>(),
            new Vector<PcbPlacementConstraints.Barrier>()).usesDefaultPhysicalPolicy(),
            "legacy placement constraints retain the default one-face identity");
        PcbPlacementConstraints mediumTop = new PcbPlacementConstraints(
            new Vector<PcbPlacementConstraints.Part>(),
            new Vector<PcbPlacementConstraints.Barrier>(), PcbCopperLayer.TOP,
            MediumBoardPhysicalPolicy.ID, MediumBoardPhysicalPolicy.VERSION);
        PcbPlacementConstraints mediumBottom =
            ProceduralPcbLayout.toBottomRoutingConstraints(mediumTop);
        check(mediumBottom.routingLayer == PcbCopperLayer.BOTTOM &&
            mediumBottom.getPhysicalPolicyIdentity().equals(
                MediumBoardPhysicalPolicy.identity()),
            "procedural bottom-face adapter preserves medium policy identity");
        checkThrows(new Runnable() {
            public void run() { MediumBoardPhysicalPolicy.rank(null); }
        }, "missing medium candidates are rejected");
        check(MediumBoardPhysicalPolicy.materiallyBetterTwoLayer(10000.0, 20000.0),
            "materially better fuller route is selected");
        check(!MediumBoardPhysicalPolicy.materiallyBetterTwoLayer(19000.0, 20000.0),
            "marginal fuller route is not selected");
        // Freeze the original 33-package route fixture so the escape/compaction
        // regression remains independent of later Q30 package additions.
        Rb30Plan edgeTrimPlan = Rb30Plan.resolve(83L);
        TroubleshootBoard edgeTrimBoard = originalSeed83Board(edgeTrimPlan);
        MediumBoardPhysicalPolicy.Result edgeTrim = new SeededPcbLayoutGenerator()
            .generateWithPolicyResult(edgeTrimBoard, edgeTrimPlan.layoutSeed,
                edgeTrimPlan.routingSeed, NOOP);
        String edgeTrimReceipt = edgeTrim.toCanonical();
        check(edgeTrimBoard.getComponentIds().size() == 33 &&
            edgeTrimBoard.getPadIds().size() == 82,
            "seed 83 regression uses the frozen original 33-package declaration");
        check(edgeTrimBoard.getComponent("RPIN_A") == null &&
            edgeTrimBoard.getComponent("RPIN_B") == null,
            "seed 83 historical route fixture has no later pull-down packages");
        check(edgeTrim.accepted(),
            "seed 83 retains the routed candidate after compacting");
        check(edgeTrimReceipt.indexOf("P07_FULLER_TWO_LAYER@1=SUCCESS") >= 0 ||
            edgeTrimReceipt.indexOf("P07_FULLER_TWO_LAYER@2=SUCCESS") >= 0,
            "seed 83 fuller route keeps a previously valid escape channel");
        check(edgeTrimReceipt.indexOf("DISCONNECTED_ESCAPE_CHANNEL") < 0,
            "seed 83 compacting does not reintroduce a disconnected escape channel");
        System.out.println("PASS: medium physical policy contracts assertions=" + assertions +
            " receipt=" + first.toCanonical());
    }

    private static final SeededPcbLayoutGenerator.AttemptObserver NOOP =
        new SeededPcbLayoutGenerator.AttemptObserver() {
            public void check(int attempt) { }
        };

    /**
     * Frozen route-regression declaration from accepted source
     * 16a922b2b757dfcd1cbc3a0230ab2b97ac2fbb50: Rb30Plan.resolve(83)'s
     * original 33 packages, before RPIN_A/RPIN_B were introduced. The current
     * plan supplies the unchanged seed-83 topology and placement/routing draws;
     * this fixture intentionally does not expose a production historical-plan API.
     */
    private static TroubleshootBoard originalSeed83Board(Rb30Plan plan) {
        if (plan == null || plan.seed != 83L)
            throw new IllegalArgumentException("Frozen route fixture is specific to seed 83");
        TroubleshootBoard board = new TroubleshootBoard(Rb30Plan.FAMILY_ID + "_BOARD");
        for (String id : new String[] {
                "RAW12", "FUSED12", "RAIL12", "EN5", "RAIL5", "LOAD12"
            }) fixtureNet(board, id, BoardNet.RoutingRole.SUPPLY);
        for (String id : new String[] { "CTRL_RETURN", "LOAD_RETURN" })
            fixtureNet(board, id, BoardNet.RoutingRole.RETURN);
        for (String id : new String[] { "A_RAW", "A_SENSE", "A_CMD", "A_DRIVE",
                "B_RAW", "B_SENSE", "B_CMD", "B_DRIVE" })
            fixtureNet(board, id, BoardNet.RoutingRole.CONTROL);
        if (plan.sharedHystereticReference)
            fixtureNet(board, "REF_SHARED", BoardNet.RoutingRole.CONTROL);
        else {
            fixtureNet(board, "A_REF", BoardNet.RoutingRole.CONTROL);
            fixtureNet(board, "B_REF", BoardNet.RoutingRole.CONTROL);
        }
        for (String id : new String[] { "A_COIL_LOW", "B_COIL_LOW", "OUT_A", "OUT_B" })
            fixtureNet(board, id, BoardNet.RoutingRole.HIGH_CURRENT);
        for (String id : new String[] { "NC_A", "NC_B", "LED_FEED" })
            fixtureNet(board, id, BoardNet.RoutingRole.SIGNAL);

        fixturePart(board, "J1", "CONNECTOR", PhysicalPackages.THROUGH_HOLE_CONNECTOR_2,
            "RAW12", "CTRL_RETURN");
        fixturePart(board, "F1", "FUSE", PhysicalPackages.AXIAL_FUSE,
            "RAW12", "FUSED12");
        fixturePart(board, "DREV", "DIODE", PhysicalPackages.AXIAL_DIODE,
            "FUSED12", "RAIL12");
        fixturePart(board, "C12", "CAPACITOR", PhysicalPackages.RADIAL_CERAMIC_CAPACITOR,
            "FUSED12", "CTRL_RETURN");
        fixturePart(board, "U1", "REGULATOR", PhysicalPackages.TO220_REGULATOR_4,
            "RAIL12", "RAIL5", "CTRL_RETURN", "EN5");
        fixturePart(board, "CIN", "CAPACITOR", PhysicalPackages.RADIAL_ELECTROLYTIC_CAPACITOR,
            "RAIL12", "CTRL_RETURN");
        fixturePart(board, "C5", "CAPACITOR", PhysicalPackages.RADIAL_CERAMIC_CAPACITOR,
            "RAIL5", "CTRL_RETURN");
        fixturePart(board, "REN", "RESISTOR", PhysicalPackages.AXIAL_RESISTOR,
            "RAIL12", "EN5");
        fixtureSensor(board, "A", plan.sharedHystereticReference ? "REF_SHARED" : "A_REF");
        fixtureSensor(board, "B", plan.sharedHystereticReference ? "REF_SHARED" : "B_REF");
        if (plan.sharedHystereticReference) {
            fixturePart(board, "RREF_H", "RESISTOR", PhysicalPackages.AXIAL_RESISTOR,
                "RAIL5", "REF_SHARED");
            fixturePart(board, "RREF_L", "RESISTOR", PhysicalPackages.AXIAL_RESISTOR,
                "REF_SHARED", "CTRL_RETURN");
            fixturePart(board, "RFB_A", "RESISTOR", PhysicalPackages.AXIAL_RESISTOR,
                "A_CMD", "A_SENSE");
            fixturePart(board, "RFB_B", "RESISTOR", PhysicalPackages.AXIAL_RESISTOR,
                "B_CMD", "B_SENSE");
        } else {
            for (String channel : new String[] { "A", "B" }) {
                fixturePart(board, "RREF_H" + channel, "RESISTOR",
                    PhysicalPackages.AXIAL_RESISTOR, "RAIL5", channel + "_REF");
                fixturePart(board, "RREF_L" + channel, "RESISTOR",
                    PhysicalPackages.AXIAL_RESISTOR,
                    channel + "_REF", "CTRL_RETURN");
            }
        }
        fixtureOutput(board, "A", plan.driverA());
        fixtureOutput(board, "B", plan.driverB());
        fixturePart(board, "RLED", "RESISTOR", PhysicalPackages.AXIAL_RESISTOR,
            "RAIL5", "LED_FEED");
        fixturePart(board, "LED1", "LED", PhysicalPackages.THROUGH_HOLE_LED,
            "LED_FEED", "CTRL_RETURN");
        fixturePart(board, "JSA", "CONNECTOR", PhysicalPackages.THROUGH_HOLE_CONNECTOR_2,
            "A_RAW", "CTRL_RETURN");
        fixturePart(board, "JSB", "CONNECTOR", PhysicalPackages.THROUGH_HOLE_CONNECTOR_2,
            "B_RAW", "CTRL_RETURN");
        fixturePart(board, "JLOAD", "CONNECTOR", PhysicalPackages.THROUGH_HOLE_CONNECTOR_2,
            "LOAD12", "LOAD_RETURN");
        fixturePart(board, "JOA", "OUTPUT_HEADER",
            PhysicalPackages.THROUGH_HOLE_OUTPUT_HEADER_2, "OUT_A", "LOAD_RETURN");
        fixturePart(board, "JOB", "OUTPUT_HEADER",
            PhysicalPackages.THROUGH_HOLE_OUTPUT_HEADER_2, "OUT_B", "LOAD_RETURN");

        board.addPowerInput(new ExternalBoardPowerInput("MAIN12", "J1.1",
            "J1.2", "RAW12", "CTRL_RETURN"));
        board.addPowerInput(new ExternalBoardPowerInput("SENSOR_A", "JSA.1",
            "JSA.2", "A_RAW", "CTRL_RETURN"));
        board.addPowerInput(new ExternalBoardPowerInput("SENSOR_B", "JSB.1",
            "JSB.2", "B_RAW", "CTRL_RETURN"));
        board.addPowerInput(new ExternalBoardPowerInput("LOAD12", "JLOAD.1",
            "JLOAD.2", "LOAD12", "LOAD_RETURN"));
        board.setPlacementConstraints(originalSeed83Placement(board));
        board.validate();
        if (board.getComponentIds().size() != 33 || board.getPadIds().size() != 82)
            throw new IllegalStateException("Frozen seed-83 Q30 fixture inventory changed");
        return board;
    }

    private static void fixtureSensor(TroubleshootBoard board, String channel,
            String referenceNet) {
        fixturePart(board, "U2" + channel, "SENSOR_CONTROL",
            PhysicalPackages.E04_DECISION_CONTROL_5, channel + "_SENSE",
            referenceNet, "RAIL5", channel + "_CMD", "CTRL_RETURN");
        fixturePart(board, "RS" + channel, "RESISTOR", PhysicalPackages.AXIAL_RESISTOR,
            channel + "_RAW", channel + "_SENSE");
    }

    private static void fixtureOutput(TroubleshootBoard board, String channel,
            RelayDriverProvider driver) {
        fixturePart(board, "RD" + channel, "RESISTOR", PhysicalPackages.AXIAL_RESISTOR,
            channel + "_CMD", channel + "_DRIVE");
        fixturePart(board, "RPD" + channel, "RESISTOR", PhysicalPackages.AXIAL_RESISTOR,
            channel + "_DRIVE", "CTRL_RETURN");
        fixturePart(board, "Q" + channel, driver.getId(), driver.getPackage(),
            channel + "_DRIVE", channel + "_COIL_LOW", "CTRL_RETURN");
        fixturePart(board, "D" + channel, "DIODE", PhysicalPackages.AXIAL_DIODE,
            channel + "_COIL_LOW", "RAIL5");
        fixturePart(board, "K" + channel, "RELAY", PhysicalPackages.RELAY_SPDT,
            "RAIL5", channel + "_COIL_LOW", "LOAD12", "NC_" + channel,
            "OUT_" + channel);
    }

    private static PcbPlacementConstraints originalSeed83Placement(
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
                region.equals("output-b") ? "Output B" : "Load supply";
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

    private static void fixtureNet(TroubleshootBoard board, String id,
            BoardNet.RoutingRole role) {
        board.addNet(new BoardNet(id, role));
    }

    private static void fixturePart(TroubleshootBoard board, String id,
            String type, PhysicalPackage physicalPackage, String... nets) {
        Vector<String> terminals = physicalPackage.getTerminalIds();
        if (terminals.size() != nets.length)
            throw new IllegalArgumentException("Frozen Q30 terminal count: " + id);
        board.addComponent(new BoardComponent(id, type, physicalPackage));
        for (int index = 0; index < nets.length; index++)
            board.addPad(new BoardPad(id + "." + terminals.get(index), id,
                terminals.get(index), nets[index]));
    }

    private static void check(boolean condition, String message) {
        assertions++;
        if (!condition) throw new AssertionError(message);
    }

    private static void checkThrows(Runnable action, String message) {
        assertions++;
        try {
            action.run();
        } catch (IllegalArgumentException expected) {
            return;
        }
        throw new AssertionError(message);
    }
}
