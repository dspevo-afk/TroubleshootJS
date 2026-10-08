package com.lushprojects.circuitjs1.client;

import java.util.Vector;

/** Focused provider-policy and deterministic medium-generator receipt checks. */
public final class MediumBoardPhysicalPolicyContractTest {
    private static int assertions;

    private static final String SEED_8_PLAN =
        "rb30-plan@4;seed=8;topology=RB30_CH2_SHARED_DIRECT_A_NMOS_B_BJT;" +
        "support=C12:false,S5:false,S12:false,filters:0,outputIndicators:1,bleeder5:false;" +
        "layout=1789953187444160285;routing=-8500271482997274880;" +
        "physicalPolicy=MEDIUM_BOARD@1;fault=SENSOR_A_OPEN;packages=32;main=12V;" +
        "regulator=5V-E02;coil=5V-E03;load=isolated-12V-180ohm-per-channel;channels=2;" +
        "sensors=2-E04-decisions;sensorPullDowns=RPIN_A:1000ohm(A_RAW,CTRL_RETURN)," +
        "RPIN_B:1000ohm(B_RAW,CTRL_RETURN);loadReference=isolated";
    private static final String SEED_4_PLAN =
        "rb30-plan@4;seed=4;topology=RB30_CH2_SHARED_DIRECT_A_BJT_B_NMOS;" +
        "support=C12:true,S5:false,S12:false,filters:1,outputIndicators:2,bleeder5:false;" +
        "layout=-7967780441668740599;routing=4321422998255155060;" +
        "physicalPolicy=MEDIUM_BOARD@1;fault=DREV_OPEN;packages=34;main=12V;" +
        "regulator=5V-E02;coil=5V-E03;load=isolated-12V-180ohm-per-channel;channels=2;" +
        "sensors=2-E04-decisions;sensorPullDowns=RPIN_A:1000ohm(A_RAW,CTRL_RETURN)," +
        "RPIN_B:1000ohm(B_RAW,CTRL_RETURN);loadReference=isolated";
    private static final String SEED_10000_PLAN =
        "rb30-plan@4;seed=10000;topology=RB30_CH2_SHARED_HYSTERETIC_A_NMOS_B_NMOS;" +
        "support=C12:false,S5:false,S12:false,filters:1,outputIndicators:1,bleeder5:true;" +
        "layout=-5582834733033270822;routing=-7232503086477951915;" +
        "physicalPolicy=MEDIUM_BOARD@1;fault=RELAY_B_COIL_OPEN;packages=36;main=12V;" +
        "regulator=5V-E02;coil=5V-E03;load=isolated-12V-180ohm-per-channel;channels=2;" +
        "sensors=2-E04-decisions;sensorPullDowns=RPIN_A:1000ohm(A_RAW,CTRL_RETURN)," +
        "RPIN_B:1000ohm(B_RAW,CTRL_RETURN);loadReference=isolated";

    private MediumBoardPhysicalPolicyContractTest() { }

    public static void main(String[] args) {
        frozenSeed10Routing();
        frozenRepresentativeRootRouting();
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
        Rb30Plan edgeTrimPlan = Rb30Plan.reference(83L);
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
        System.out.println("Q30_SEED83_ROUTING " + edgeTrimReceipt);
        // Corrected package escapes can change candidate ranking. Preserve the
        // same board and require a real fuller route, independent of its index.
        check(edgeTrim.getStatistics().twoLayerSuccesses > 0,
            "seed 83 fuller route keeps a valid escape channel: " + edgeTrimReceipt);
        edgeTrim.getLayout().validateRoutingGeometry(edgeTrimBoard);
        new PcbTwoLayerRules(edgeTrimBoard, edgeTrim.getLayout()).validate(edgeTrim.getLayout());
        check(edgeTrimReceipt.indexOf("DISCONNECTED_ESCAPE_CHANNEL") < 0,
            "seed 83 compacting does not reintroduce a disconnected escape channel");
        System.out.println("PASS: medium physical policy contracts assertions=" + assertions +
            " receipt=" + first.toCanonical());
    }

    /** Exact predeclared service-cohort plan; retries cannot satisfy this witness. */
    private static void frozenSeed10Routing() {
        Rb30Plan plan = Rb30Plan.resolve(10L);
        check(plan.channelCount == 2 && plan.physicalPackageCount() == 36 &&
            plan.referenceArrangement == Rb30Plan.ReferenceArrangement.SEPARATE_DIRECT &&
            plan.driverABjt && !plan.driverBBjt &&
            plan.layoutSeed == -5437028946546985473L &&
            plan.routingSeed == 6161596101315970262L,
            "seed 10 retains the frozen 36-package two-channel plan and random concerns");
        TroubleshootBoard board = plan.board();
        check(board.getComponentIds().size() == 36,
            "seed 10 routes the original complete board without retry substitution");
        MediumBoardPhysicalPolicy.Result first = new SeededPcbLayoutGenerator()
            .generateWithPolicyResult(board, plan.layoutSeed, plan.routingSeed, NOOP);
        System.out.println("Q30_SEED10_ROUTING " + first.toCanonical());
        check(first.accepted() && MediumBoardPhysicalPolicy.P07_FULLER_TWO_LAYER.equals(
            first.getStatistics().selectedRoutePolicy),
            "seed 10 frozen service plan has a normal-eligible P07 physical realization");
        check(first.getStatistics().placementCandidates == 6 &&
            first.getStatistics().routeAttempts <= 6,
            "seed 10 retains the existing bounded placement and route candidate breadth");
        first.getLayout().validateGeometry(board);
        new PcbTwoLayerRules(board, first.getLayout()).validate(first.getLayout());
        check(first.getLayout().getComponents().size() == 36 &&
            first.getLayout().getPads().size() == board.getPadIds().size(),
            "seed 10 preserves every original package and electrical endpoint");
        MediumBoardPhysicalPolicy.Result replay = new SeededPcbLayoutGenerator()
            .generateWithPolicyResult(plan.board(), plan.layoutSeed, plan.routingSeed, NOOP);
        check(first.toCanonical().equals(replay.toCanonical()) &&
            first.getLayout().geometryFingerprint().equals(replay.getLayout().geometryFingerprint()),
            "seed 10 replays exact geometry and bounded work without changing plan identity");
    }

    /** Exact representative Q30 roots from the epoch-4 qualification plan. */
    private static void frozenRepresentativeRootRouting() {
        frozenRepresentativeRootRouting(8L, 32, 1789953187444160285L,
            -8500271482997274880L, SEED_8_PLAN);
        frozenRepresentativeRootRouting(4L, 34, -7967780441668740599L,
            4321422998255155060L, SEED_4_PLAN);
        frozenRepresentativeRootRouting(10000L, 36, -5582834733033270822L,
            -7232503086477951915L, SEED_10000_PLAN);
    }

    /** One route only: the four-root corpus contract owns cold/warm replay. */
    private static void frozenRepresentativeRootRouting(long seed,
            int expectedPackages, long expectedLayoutSeed, long expectedRoutingSeed,
            String expectedCanonical) {
        Rb30Plan plan = Rb30Plan.resolve(seed);
        check(plan.seed == seed && plan.channelCount == 2 &&
            plan.physicalPackageCount() == expectedPackages &&
            plan.layoutSeed == expectedLayoutSeed &&
            plan.routingSeed == expectedRoutingSeed &&
            expectedCanonical.equals(plan.canonical()),
            "seed " + seed + " retains its exact epoch-4 acceptance-plan identity");

        TroubleshootBoard board = plan.board();
        Vector<String> expectedComponentIds = board.getComponentIds();
        Vector<String> expectedPadIds = board.getPadIds();
        check(expectedComponentIds.size() == expectedPackages,
            "seed " + seed + " materializes its declared physical package count");

        MediumBoardPhysicalPolicy.Result result = new SeededPcbLayoutGenerator()
            .generateWithPolicyResult(board, plan.layoutSeed, plan.routingSeed, NOOP);
        System.out.println("Q30_FROZEN_ROOT_ROUTING seed=" + seed + " " +
            result.toCanonical());
        check(result.accepted() && MediumBoardPhysicalPolicy.P07_FULLER_TWO_LAYER.equals(
            result.getStatistics().selectedRoutePolicy),
            "seed " + seed + " has an accepted normal-eligible P07 realization");

        MediumBoardPhysicalPolicy.Statistics stats = result.getStatistics();
        check(stats.placementCandidates == 6 &&
            stats.placementCandidates == MediumBoardPhysicalPolicy.PLACEMENT_CANDIDATES &&
            stats.routeAttempts <= 6 &&
            stats.oneFaceAttempts <= MediumBoardPhysicalPolicy.ROUTING_CANDIDATES &&
            stats.twoLayerAttempts <= MediumBoardPhysicalPolicy.ROUTING_CANDIDATES &&
            stats.routeAttempts == stats.oneFaceAttempts + stats.twoLayerAttempts,
            "seed " + seed + " stays within the existing six-placement/six-route bounds");
        check(stats.rankedPlacementAttempts.size() == stats.placementScores.size() &&
            stats.placementScores.size() ==
                stats.placementCandidates - stats.placementRejections,
            "seed " + seed + " retains aligned ranked original placement IDs and scores");
        boolean originalRankIds = true;
        boolean[] seenAttempts = new boolean[stats.placementCandidates];
        for (Integer rankedAttempt : stats.rankedPlacementAttempts) {
            if (rankedAttempt == null || rankedAttempt < 0 ||
                    rankedAttempt >= stats.placementCandidates || seenAttempts[rankedAttempt]) {
                originalRankIds = false;
                continue;
            }
            seenAttempts[rankedAttempt] = true;
        }
        check(originalRankIds,
            "seed " + seed + " ranking preserves unique original placement attempt IDs");

        PcbBoardLayout layout = result.getLayout();
        layout.validateGeometry(board);
        new PcbTwoLayerRules(board, layout).validate(layout);
        boolean packageMappingPreserved = layout.getComponents().size() ==
            expectedComponentIds.size();
        for (String componentId : expectedComponentIds) {
            BoardComponent source = board.getComponent(componentId);
            PcbComponentPlacement placed = layout.getComponent(componentId);
            if (source == null || placed == null ||
                    source.getPhysicalPackage() != placed.getPhysicalPackage())
                packageMappingPreserved = false;
        }
        boolean padMappingPreserved = layout.getPads().size() == expectedPadIds.size();
        for (String padId : expectedPadIds) {
            PcbPadPlacement placed = layout.getPad(padId);
            if (placed == null || !padId.equals(placed.getPadId()))
                padMappingPreserved = false;
        }
        check(packageMappingPreserved && padMappingPreserved,
            "seed " + seed + " preserves every original package and pad identity");
    }

    private static final SeededPcbLayoutGenerator.AttemptObserver NOOP =
        new SeededPcbLayoutGenerator.AttemptObserver() {
            public void check(int attempt) { }
        };

    /**
     * Frozen route-regression declaration from accepted source
     * 16a922b2b757dfcd1cbc3a0230ab2b97ac2fbb50: the seed-83 two-channel Q30
     * regression fixture's
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
