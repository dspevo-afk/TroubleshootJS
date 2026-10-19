package com.lushprojects.circuitjs1.client;

import java.util.Vector;

/** Literal copy ownership canaries; these are not electrical admission fixtures. */
public final class PcbPostSelectionEdgeTrimCopyContractTest {
    private static int assertions;
    public static void main(String[] args) {
        PcbBoardLayout original=new PcbBoardLayout(1000,1000,
            new Rectangle(100,100,500,400),new Rectangle(700,700,100,100));
        original.addTrace(new PcbTraceGeometry("N",new int[]{200,400},new int[]{200,200}));
        original.addSilkscreenLabel(new PcbSilkscreenLabel("L","label",
            new Rectangle(200,250,40,10),10,false,null));
        original.setRoutingStatistics(11,12,13);
        original.setGenerationStatistics(2,3,4,5L,6L,7L);
        String before=original.geometryFingerprint();
        PcbBoardLayout candidate=original.copyForCompaction();
        check(before.equals(candidate.geometryFingerprint()),"fresh copy retains all geometry");
        check(candidate.getRoutingExpansions()==11 && candidate.getRawRoutingSegments()==12 &&
            candidate.getRoutingCongestionRejections()==13,"literal routing receipts retained");
        check(candidate.getGenerationPlacementAttempts()==2 && candidate.getGenerationRoutingAttempts()==3 &&
            candidate.getGenerationRoutingExpansions()==4 && candidate.getGenerationRoutingMillis()==5L,
            "literal generation receipts retained");
        check(candidate.matchesGenerationSeeds(6L,7L) && !candidate.matchesGenerationSeeds(6L,8L),
            "literal seed identity retained without accepting a different routing seed");
        try {candidate.compactToContent(-1,100,30);throw new AssertionError("negative crop admitted");}
        catch(IllegalArgumentException expected){assertions++;}
        check(before.equals(candidate.geometryFingerprint()),"rejected coordinate leaves copy intact");
        check(before.equals(original.geometryFingerprint()),"rejected candidate leaves original intact");
        candidate.compactToContent(100,100,30);
        check(!before.equals(candidate.geometryFingerprint()),"candidate crop actually changes geometry");
        check(before.equals(original.geometryFingerprint()),"successful candidate crop cannot mutate original");
        candidate.replaceTraces(new Vector<PcbTraceGeometry>());
        check(candidate.getTraces().isEmpty() && original.getTraces().size()==1,
            "mutable copper collections have separate ownership");
        check(original.getSilkscreenLabels().size()==1 &&
            original.getSilkscreenLabels().get(0).getBounds().equals(new Rectangle(200,250,40,10)),
            "original silk coordinates retain literal values");
        original.seal();
        PcbBoardLayout frozenCopy=original.copySealed();
        check(before.equals(frozenCopy.geometryFingerprint()),"existing frozen copy retains exact geometry");
        try {original.copyForCompaction();throw new AssertionError("sealed owner admitted for mutation copy");}
        catch(IllegalStateException expected){assertions++;}
        try {frozenCopy.replaceTraces(new Vector<PcbTraceGeometry>());throw new AssertionError("frozen copy mutable");}
        catch(IllegalStateException expected){assertions++;}
        System.out.println("PASS: PCB post-selection edge trim copy contracts assertions="+assertions);
    }
    private static void check(boolean condition,String why) {
        assertions++;
        if(!condition)throw new AssertionError(why);
    }
}
