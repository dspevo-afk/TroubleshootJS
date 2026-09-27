package com.lushprojects.circuitjs1.client;

/** Focused contract for the versioned normal-medium physical eligibility seam. */
public final class MediumBoardNormalAdmissionContractTest {
    private static int assertions;

    public static void main(String[] args) {
        CirSim sim = new CirSim();
        sim.gridSize = 16;
        sim.gridMask = ~15;
        sim.gridRound = 7;
        CircuitElm.sim = sim;

        // Epoch3 seed13 is a measured accepted route; seed0 remains in the
        // full corpus as a routing outcome, not an assumed positive fixture.
        final Rb30Plan plan = Rb30Plan.resolve(13L);
        Rb30Generator.Candidate candidate = new Rb30Generator().construct(plan);
        try {
            TroubleshootBoard board = candidate.board();
            MediumBoardPhysicalPolicy.Result routed =
                new SeededPcbLayoutGenerator().generateWithPolicyResult(
                    board, plan.layoutSeed, plan.routingSeed);
            require(routed.accepted(), "normal-medium canary has an accepted routed candidate");
            require(MediumBoardPhysicalPolicy.P07_FULLER_TWO_LAYER.equals(
                routed.getStatistics().selectedRoutePolicy),
                "normal-medium canary selects the bounded P07 route");

            GeneratedPhysicalAdmission admission = MediumBoardNormalAdmission.fromAcceptedRoute(
                board, routed, plan.layoutSeed, plan.routingSeed);
            require(MediumBoardNormalAdmission.IDENTITY.equals(admission.identity()),
                "physical admission has its own versioned identity");
            require(!admission.identity().equals(MediumBoardPhysicalPolicy.identity()),
                "routing-policy identity alone is not normal admission");
            require(admission.canonical().contains(MediumBoardPhysicalPolicy.identity()),
                "admission binds the unchanged routing policy");
            String canonical = admission.canonical();
            require(canonical.equals(admission.canonical()), "admission token is immutable");
            admission.requireConstruction(board, routed.getLayout());

            PcbBoardLayout sealed = routed.getLayout();
            sealed.seal();
            admission.requireConstruction(board, sealed.copySealed());

            final TroubleshootBoard routedBoard = board;
            final MediumBoardPhysicalPolicy.Result accepted = routed;
            reject(new Runnable() {
                public void run() {
                    MediumBoardNormalAdmission.fromAcceptedRoute(routedBoard, accepted,
                        plan.layoutSeed ^ 1L, plan.routingSeed);
                }
            }, "placement-seed substitution is rejected");
            reject(new Runnable() {
                public void run() {
                    MediumBoardNormalAdmission.fromAcceptedRoute(routedBoard,
                        new MediumBoardPhysicalPolicy.Result(accepted.getLayout(),
                            accepted.getStatistics(), "forged failure"),
                        plan.layoutSeed, plan.routingSeed);
                }
            }, "failed policy result is rejected");
            reject(new Runnable() {
                public void run() {
                    PcbTwoLayerRules.requireDeveloperAdmission(accepted.getLayout(), false);
                }
            }, "P09 developer-only two-layer guard remains unchanged");
        } finally {
            for (CircuitElm element : candidate.elements()) element.delete();
        }

        System.out.println("PASS: medium normal admission contracts " + assertions +
            " assertions");
    }

    private static void reject(Runnable action, String why) {
        boolean rejected = false;
        try {
            action.run();
        } catch (RuntimeException expected) {
            rejected = true;
        }
        require(rejected, why);
    }

    private static void require(boolean condition, String why) {
        assertions++;
        if (!condition) throw new AssertionError(why);
    }
}
