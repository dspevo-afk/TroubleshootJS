package com.lushprojects.circuitjs1.client;

/** Predeclared non-tuning roots; compare real guarded routes, not visual scale. */
public final class PcbConnectedPackingHoldoutContractTest {
    public static void main(String[] args) {
        int checks=0;
        for(long seed:new long[]{1L,7L}) {
            long previousArea=0;
            for(int version:new int[]{9,10}) {
                Rb56Plan plan=Rb56Plan.resolve(seed,version);
                TroubleshootBoard board=plan.board();
                MediumBoardPhysicalPolicy.Result result=new SeededPcbLayoutGenerator()
                    .generateWithPolicyResult(board,plan.layoutSeed,plan.routingSeed);
                if(!result.accepted())throw new AssertionError("holdout route rejected: "+seed+"/"+version+" "+result.toCanonical());
                PcbBoardLayout layout=result.getLayout();
                layout.validateGeometry(board);layout.validateRoutingGeometry(board);layout.validateRouteQuality();
                new PcbTwoLayerRules(board,layout).validate(layout);
                MediumBoardNormalAdmission.fromAcceptedRb56Route(board,result,plan.layoutSeed,plan.routingSeed)
                    .requireConstruction(board,layout);
                if(layout.getComponents().size()!=board.getComponentIds().size() ||
                    layout.getPads().size()!=board.getPadIds().size())throw new AssertionError("holdout inventory changed");
                Rectangle outline=layout.getBoardOutline();
                long area=(long)outline.width*outline.height;
                if(version==9)previousArea=area;
                else if(area>previousArea)throw new AssertionError("holdout area regression: "+seed);
                System.out.println("PCB_CONNECTED_HOLDOUT seed="+seed+" version="+version+" area="+area+
                    " geometry="+PlayerSessionFingerprint.of(layout.geometryFingerprint()));
                checks++;
            }
        }
        System.out.println("PASS: connected packing holdouts roots=2 assertions="+checks);
    }
}
