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
        // Literals re-pinned 2026-11-12 to generator layout 26 after the intentional
        // dependency-v26 correction (34ff05d0); all ten areas stayed equal or shrank.
        // Holdouts 7 and 14 re-pinned 2026-11-13 for dependency v29 (guided P07 streams keep
        // their first accepted route); both routed with FIRST_SUCCESS_GUIDED, areas unchanged.
        long[] roots={-1017L,-1004L,-1022L,0L,17L,42L,1L,7L,5L,14L};
        long[] baselineAreas={4529785,4466664,6319512,4358809,5814368,5941269,4615974,5713720,6090714,6102705};
        long[] saved11Areas={4529785,4466664,6310794,4358809,5814368,5924664,4615974,5713720,6037578,6102705};
        String[] baselineHashes={
            "c42e71efc9f75f567c31aca1c6e4e5ce18af847e0c531fa6df3efb52bb2001fa",
            "7e75e4689fdb167de969c24b6d6249a9f715139df8f3fc9807955d201e9e78cb",
            "da34b4687b0afff9565b0173e6b95cdbb600e1f752ce025195c48d4106e5a90a",
            "9bd92e815e2b5d883f8b3fd6a16fe00d603279eb19a8b7cf971ab7378daffc94",
            "fe35af4d26052967f32a3582ae4c4e52ef6d459b3632ea4f02c7c5ed390ee427",
            "b6b31c6e80ce215322e00e7e07d221a68ecdf446e97c7219da821e3bc18f2d37",
            "78c2850ac7d0c692c4148c0b3e791c227a25ae7adb546552d243e13b62da36fa",
            "de3f078aa2d9b15c05551c6a5eaf1b50d9002f147a1006eb356ed99b5c64aa19",
            "4f7c08a1d13934a3f22c447584eb63b803b95f6976b2d0bb1c6defc696a15bd4",
            "719e8ed5e471e8ea7deff33d1c827c8fafd4be5c98dd1173612b41d775c62454"};
        String[] saved11Hashes={
            "c42e71efc9f75f567c31aca1c6e4e5ce18af847e0c531fa6df3efb52bb2001fa",
            "7e75e4689fdb167de969c24b6d6249a9f715139df8f3fc9807955d201e9e78cb",
            "89080e8b9237a947b0f25dfa73dd15b40399376cb1241acb0a92e9d23db4e013",
            "9bd92e815e2b5d883f8b3fd6a16fe00d603279eb19a8b7cf971ab7378daffc94",
            "fe35af4d26052967f32a3582ae4c4e52ef6d459b3632ea4f02c7c5ed390ee427",
            "d96de93c321a6673d4a49a4b0847393040c8367a274358c4a8e184a6c1e2440d",
            "78c2850ac7d0c692c4148c0b3e791c227a25ae7adb546552d243e13b62da36fa",
            "de3f078aa2d9b15c05551c6a5eaf1b50d9002f147a1006eb356ed99b5c64aa19",
            "4172c0f67bb5f14dc83a0afaa82933417e4fca4e6ff5156f2bee8c1483ee7c48",
            "719e8ed5e471e8ea7deff33d1c827c8fafd4be5c98dd1173612b41d775c62454"};
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
