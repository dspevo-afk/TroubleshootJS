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
            "0f557eee2670d3ea4d963dc4cd4797d908b0af33e405db1f86e4528dfb0c1cba",
            "d349957427274ae74726d07034409619d3a43e8e571b756622100dd2d7d2438d",
            "1216897c60853a35008c3d2fd62965d6489ad997c2e3a62cb578b081dedd5be9",
            "5d93ab0115d8721826d93f2220ec10707dfe30836b6e31d56f7b068d249b7836",
            "2e5a36d464418c7411b68055d408d17867012eaec011c8e21e5d5f4da7caee32",
            "5b4de9612e851257a5d5cb3027fddca8ca8b1e254bfcb3ce75fcab8c2ec2a091",
            "e512e825f8c14bf78bb8a13eb4d8cc38f6270f8ed50757cb57ad6b94bb2b33f9",
            "fed3351826ce0f08014e484b01146a68a6cae799933331ae4f1c44a92815f718",
            "ec8b179776a80fb23a5f2dce032a9ec429dccd1aafd0fe33e4ac47814f7f37b3",
            "ab268c10c0f7b8d8be74f76f71c197b2cdf410c7dd044a943986604130f386cf"};
        String[] saved11Hashes={
            "0f557eee2670d3ea4d963dc4cd4797d908b0af33e405db1f86e4528dfb0c1cba",
            "d349957427274ae74726d07034409619d3a43e8e571b756622100dd2d7d2438d",
            "1216897c60853a35008c3d2fd62965d6489ad997c2e3a62cb578b081dedd5be9",
            "5d93ab0115d8721826d93f2220ec10707dfe30836b6e31d56f7b068d249b7836",
            "2e5a36d464418c7411b68055d408d17867012eaec011c8e21e5d5f4da7caee32",
            "df2494c31141ac8c6ccb5065454ba70e2190d29d9690a195f518c1c268c5fb01",
            "e512e825f8c14bf78bb8a13eb4d8cc38f6270f8ed50757cb57ad6b94bb2b33f9",
            "fed3351826ce0f08014e484b01146a68a6cae799933331ae4f1c44a92815f718",
            "ec8b179776a80fb23a5f2dce032a9ec429dccd1aafd0fe33e4ac47814f7f37b3",
            "ab268c10c0f7b8d8be74f76f71c197b2cdf410c7dd044a943986604130f386cf"};
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
                            PcbCompactnessContractTest.legacyGeometrySha256(layout)))
                        throw new AssertionError("published10 pocket baseline changed: "+seed);
                }
                else {
                    if(area>previousArea)throw new AssertionError("pocket area regression: "+seed+" "+area+">"+previousArea);
                    if(area!=saved11Areas[rootIndex] || !saved11Hashes[rootIndex].equals(
                            PcbCompactnessContractTest.legacyGeometrySha256(layout)))
                        throw new AssertionError("published11 full geometry baseline changed: "+seed);
                }
                checks++;
            }
        }
        System.out.println("PASS: "+label+" cases="+checks);
    }
}
