package com.lushprojects.circuitjs1.client;

/** Synthetic information fixtures are mathematical falsifiers, never solver or human trial evidence. */
public final class U05DifficultyContractTest {
    private static int assertions;
    public static void main(String[] args) {
        GeneratedDiagnosticExecutionTrace.Builder trace = GeneratedDiagnosticExecutionTrace.builder();
        trace.recordCompletedSemanticAction(); trace.recordCompletedSemanticAction();
        trace.recordRepairAction(WorkbenchOperation.REMOVE); trace.recordRepairAction(WorkbenchOperation.REMOVE);
        GeneratedDiagnosticRepairSemantics semantics = GeneratedDiagnosticRepairSemantics.forServiceability(
            new GeneratedFaultServiceability(GeneratedFaultLocus.componentInternal("R1"), new String[0],
                new String[0], new String[] {WorkbenchOperation.CATALOG_INSTALL}, GeneratedBoardOperationIds.CUSTOMER_RETEST));
        GeneratedDiagnosticExecutionTrace frozen = trace.freeze(semantics);
        check(frozen.getCompletedSemanticActions()==4 && frozen.getExecutedRepairActionIds().size()==1,
            "ordered semantic executions do not deduplicate repeat actions");
        trace.recordCompletedSemanticAction();
        check(frozen.getCompletedSemanticActions()==4, "published action witness is immutable");
        boolean[][][] twoBits = observations(new int[][] {{0,0,1,1}, {0,1,0,1}});
        DiagnosticReduction binary = new DiagnosticReduction(twoBits, new int[] {0,1,2,3});
        check(binary.readings == 2 && binary.bestSingleRemaining == 2, "two independent binary readings distinguish four repairs");
        DiagnosticReduction direct = new DiagnosticReduction(observations(new int[][] {{0,1,2,3}}), new int[] {0,1,2,3});
        check(direct.readings == 1 && direct.bestSingleRemaining == 1, "one-probe solution is measured honestly");
        check(DifficultyAssessment.classify(16, 4, 4, direct.readings, 13, 4, 6, 6, 4, true) == DifficultyProfile.EASY,
            "one-probe diagnostic solution cannot earn MEDIUM from other large counters");
        DiagnosticReduction sameRepair = new DiagnosticReduction(observations(new int[][] {{0,0,0,0}}), new int[] {0,0,0,0});
        check(sameRepair.readings == 0, "equivalent repairs need no forced human route");
        boolean rejected = false;
        try { new DiagnosticReduction(observations(new int[][] {{0,0,0}, {0,0,0}}), new int[] {0,1,2}); }
        catch (IllegalArgumentException expected) { rejected = true; }
        check(rejected, "unresolved legal repairs reject");
        rejected = false;
        try { new DiagnosticReduction(new boolean[][][] {{{true,false},{true,true}}}, new int[] {0,1}); }
        catch (IllegalArgumentException expected) { rejected = true; }
        check(rejected, "asymmetric comparison rejects");
        for (int parts : new int[] {3, 5, 16, 20}) {
            check(DifficultyAssessment.classify(parts, 1, 1, 0, 4, 2, 2, 1, 0, false) == DifficultyProfile.EASY,
                "adding inert part count cannot raise difficulty");
            check(DifficultyAssessment.classify(parts, 2, 3, 2, 8, 2, 4, 2, 0, true) == DifficultyProfile.MEDIUM,
                "parallel-path interaction independent of package count");
        }
        check(!DifficultyProfile.HARD.isAvailable() && !DifficultyProfile.PSYCHOTIC.isAvailable(), "advanced bands remain unavailable");
        check(DifficultyAssessment.classify(16, 2, 3, 2, 100, 3, 100, 100, 0, false) == DifficultyProfile.EASY,
            "large proof-route and target-list counts cannot fabricate interaction");
        check(DifficultyAssessment.classify(16, 2, 3, 2, 1, 3, 4, 1, 0, true) == DifficultyProfile.MEDIUM,
            "proof-route category depth does not set the label");
        for (int badModes : new int[] {0, -1}) {
            rejected = false;
            try { DifficultyAssessment.classify(16, 2, 3, 1, 8, badModes, 4, 2, 0, false); }
            catch (IllegalArgumentException expected) { rejected = true; }
            check(rejected, "missing instrument cannot earn profile");
        }
        PlayerLaunchRequest easy = new PlayerLaunchRequest("RB15_CONTROL", "9007199254740993", "EASY");
        PlayerLaunchRequest medium = new PlayerLaunchRequest("RB15_CONTROL", "9007199254740993", "MEDIUM");
        check(easy.seed == medium.seed && easy.generation().getDescriptor().toCanonical().equals(medium.generation().getDescriptor().toCanonical()),
            "profile changes admission only, electrical generation inputs remain exact");
        check(!easy.generation().canonical().equals(medium.generation().canonical()), "requested profile participates in proof dependencies");
        System.out.println("PASS: U05 difficulty contracts assertions=" + assertions);
    }
    private static boolean[][][] observations(int[][] values) {
        boolean[][][] result = new boolean[values.length][values[0].length][values[0].length];
        for (int m=0;m<values.length;m++) for (int a=0;a<values[m].length;a++) for(int b=0;b<values[m].length;b++)
            result[m][a][b]=values[m][a]==values[m][b];
        return result;
    }
    private static void check(boolean ok, String text) { assertions++; if(!ok)throw new AssertionError(text); }
}
