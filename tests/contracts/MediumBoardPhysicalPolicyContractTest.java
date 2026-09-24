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
        Rb30Plan edgeTrimPlan = Rb30Plan.resolve(83L);
        MediumBoardPhysicalPolicy.Result edgeTrim = new SeededPcbLayoutGenerator()
            .generateWithPolicyResult(edgeTrimPlan.board(), edgeTrimPlan.layoutSeed,
                edgeTrimPlan.routingSeed, NOOP);
        String edgeTrimReceipt = edgeTrim.toCanonical();
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
