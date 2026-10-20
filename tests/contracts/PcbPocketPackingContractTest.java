package com.lushprojects.circuitjs1.client;

/** Predeclared representative/holdout comparison against the published10 path. */
public final class PcbPocketPackingContractTest {
    public static void main(String[] args) {
        measure(0,6,"pocket packing contracts roots=6");
    }
    static void verifyHoldouts() {
        measure(6,10,"pocket packing holdout contracts roots=4");
    }
    private static void measure(int first,int end,String label) {
        long[] roots={-1017L,-1004L,-1022L,0L,17L,42L,1L,7L,5L,14L};
        long[] baselineAreas={4529785,4466664,6943752,4358809,5814368,6103992,4615974,6587808,9445744,8585034};
        long[] saved11Areas={4529785,4466664,6943752,4358809,5814368,6036952,4615974,6587808,9445744,8585034};
        String[] baselineHashes={
            "8e31efab1c7d21c7490117686d9c830ca89abd7fcd0f4e702a535b5c2b6bf8f2",
            "418d3d3606f0bb96f800efe4f064de9e9485dfefe9cd58d164296626943003c2",
            "54e60afe24bedfbeeb472aaca800a3ed264983d83b7a1ea70d668c3bdd347fe3",
            "293b1b1d72df8fce71b5f0d490d4fe7e42dd58c2de0569dc236da11c7e0a2374",
            "eabf5849e40d8a7f5323e48106ca248d2147a884188ef86fcc13a4d65ddf2dcd",
            "b7d7c6b05a9140bad55bee75cfb05844c46f43a9cc4110fc7949a60a68f9555f",
            "cc618345b154fb6140d835c71dabd897acd8671f0a5288263170b3d2deff606d",
            "7611f8a81e84e6ce20b173c5ef450b36a45834e4dc7132b6281a3cea997769d7",
            "f9ce1319244193e25f28a6e5a36f554512dff21a9e6c5eca267bc0190b783165",
            "4b68db53dfaeb5d77491e3d3f94fa8d9eca8473752f1b7a2ddc2cceb7332b9e2"};
        String[] saved11Hashes={
            "8e31efab1c7d21c7490117686d9c830ca89abd7fcd0f4e702a535b5c2b6bf8f2",
            "418d3d3606f0bb96f800efe4f064de9e9485dfefe9cd58d164296626943003c2",
            "54e60afe24bedfbeeb472aaca800a3ed264983d83b7a1ea70d668c3bdd347fe3",
            "293b1b1d72df8fce71b5f0d490d4fe7e42dd58c2de0569dc236da11c7e0a2374",
            "eabf5849e40d8a7f5323e48106ca248d2147a884188ef86fcc13a4d65ddf2dcd",
            "a4005331416d3c16399feaf7ff0f545d7b33a64ac48728eb72e1cb314b77d0fb",
            "cc618345b154fb6140d835c71dabd897acd8671f0a5288263170b3d2deff606d",
            "7611f8a81e84e6ce20b173c5ef450b36a45834e4dc7132b6281a3cea997769d7",
            "f9ce1319244193e25f28a6e5a36f554512dff21a9e6c5eca267bc0190b783165",
            "4b68db53dfaeb5d77491e3d3f94fa8d9eca8473752f1b7a2ddc2cceb7332b9e2"};
        int checks=0;
        for(int rootIndex=first;rootIndex<end;rootIndex++) {
            long seed=roots[rootIndex];
            long previousArea=0;
            for(int version:new int[]{10,11}) {
                Rb56Plan plan=Rb56Plan.resolve(seed,version);
                TroubleshootBoard board=plan.board();
                MediumBoardPhysicalPolicy.Result result=new SeededPcbLayoutGenerator()
                    .generateWithPolicyResult(board,plan.layoutSeed,plan.routingSeed);
                System.out.println("PCB_POCKET_ROUTE seed="+seed+" version="+version+" "+result.toCanonical());
                if(!result.accepted())throw new AssertionError("pocket route rejected: "+seed+"/"+version);
                PcbBoardLayout layout=result.getLayout();
                layout.validateGeometry(board);layout.validateRoutingGeometry(board);layout.validateRouteQuality();
                new PcbTwoLayerRules(board,layout).validate(layout);
                MediumBoardNormalAdmission.fromAcceptedRb56Route(board,result,plan.layoutSeed,plan.routingSeed)
                    .requireConstruction(board,layout);
                PcbFunctionalGroupingContractTest.verifySavedUniformTraceWidths(board,layout);
                if(layout.getComponents().size()!=board.getComponentIds().size() ||
                    layout.getPads().size()!=board.getPadIds().size())throw new AssertionError("pocket inventory changed");
                Rectangle outline=layout.getBoardOutline();
                long area=(long)outline.width*outline.height;
                long copperLength=0;
                int segments=0,vias=0;
                for(PcbTraceGeometry trace:layout.getTraces()) {
                    int[] x=trace.getXPoints(),y=trace.getYPoints();
                    for(int i=1;i<x.length;i++) {
                        copperLength+=Math.abs((long)x[i]-x[i-1])+Math.abs((long)y[i]-y[i-1]);
                        segments++;
                    }
                }
                for(PcbBoardHole hole:layout.getHoles())if(hole.kind==PcbBoardHole.Kind.VIA)vias++;
                System.out.println("PCB_POCKET_CASE seed="+seed+" version="+version+" area="+area+
                    " width="+outline.width+" height="+outline.height+
                    " copperLength="+copperLength+" segments="+segments+" vias="+vias+
                    " parts="+layout.getComponents().size()+" repeated="+
                    SeededPcbLayoutGenerator.hasRepeatedControlRegions(board.getPlacementConstraints())+
                    " geometry="+PlayerSessionFingerprint.of(layout.geometryFingerprint()));
                PcbFunctionalGroupingContractTest.reportWhitespace(board,layout,seed,version);
                if(version==10) {
                    previousArea=area;
                    if(area!=baselineAreas[rootIndex] || !baselineHashes[rootIndex].equals(
                            PcbCompactnessContractTest.physicalGeometrySha256(layout)))
                        throw new AssertionError("published10 physical pocket baseline changed (reference x/y and epoch excluded): "+seed);
                }
                else {
                    if(area>previousArea)throw new AssertionError("pocket area regression: "+seed+" "+area+">"+previousArea);
                    if(area!=saved11Areas[rootIndex] || !saved11Hashes[rootIndex].equals(
                            PcbCompactnessContractTest.physicalGeometrySha256(layout)))
                        throw new AssertionError("published11 physical baseline changed (reference x/y and epoch excluded): "+seed);
                }
                checks++;
            }
        }
        System.out.println("PASS: "+label+" cases="+checks);
    }
}
