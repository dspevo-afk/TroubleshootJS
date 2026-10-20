package com.lushprojects.circuitjs1.client;

/** Task-only observations of the existing policy contract's two replay pairs. */
public final class FreshRb30GapDiagnostic {
    public static void main(String[] args) {
        if (args.length != 2 || !"--seed".equals(args[0]) || !"10".equals(args[1]))
            throw new IllegalArgumentException("Expected exact witness seed 10");
        observe(10L);
        observe(0L);
        System.out.println("PASS: fresh RB30 gap diagnostic clock witness roots=10,0");
    }

    private static void observe(long seed) {
        Rb30Plan plan = Rb30Plan.resolve(seed);
        for (int repeat = 0; repeat < 2; repeat++) {
            TroubleshootBoard board = plan.board();
            MediumBoardPhysicalPolicy.Result result = new SeededPcbLayoutGenerator()
                .generateWithPolicyResult(board, plan.layoutSeed, plan.routingSeed);
            if (!result.accepted() || result.getLayout() == null)
                throw new AssertionError("Witness physical route did not admit");
            result.getLayout().validateGeometry(board);
            new PcbTwoLayerRules(board, result.getLayout()).validate(result.getLayout());
            System.out.println("PCB_CLOCK_WITNESS " + seed + " " + repeat + " " +
                PlayerSessionFingerprint.of(result.getLayout().geometryFingerprint()) +
                " " + result.toCanonical());
        }
    }
}
