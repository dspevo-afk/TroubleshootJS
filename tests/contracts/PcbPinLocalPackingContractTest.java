package com.lushprojects.circuitjs1.client;

import java.util.Vector;

/** Published9 bounds and actual paired-pin spans; no manufacturing claim. */
public final class PcbPinLocalPackingContractTest {
    public static void main(String[] args) {
        long[] seeds={-1017L,-1004L,-1022L,0L,17L,42L};
        long[] areas={4529785,4466664,6943752,4358809,5814368,6211431};
        long[] baselineSpans={19610,24320,28140,22890,25220,29920};
        long[] accepted10Spans={19610,24320,28140,22890,25220,20480};
        long totalSpan=0;
        for(int i=0;i<seeds.length;i++) {
            Rb56Plan plan=Rb56Plan.resolve(seeds[i],Rb56Placement.CURRENT_VERSION);
            TroubleshootBoard board=plan.board();
            MediumBoardPhysicalPolicy.Result result=new SeededPcbLayoutGenerator()
                .generateWithPolicyResult(board,plan.layoutSeed,plan.routingSeed);
            System.out.println("PCB_PIN_LOCAL_ROUTE seed="+seeds[i]+" "+result.toCanonical());
            if(!result.accepted())throw new AssertionError("lost local fixture route: "+seeds[i]);
            PcbBoardLayout layout=result.getLayout();
            layout.validateGeometry(board);layout.validateRoutingGeometry(board);layout.validateRouteQuality();
            new PcbTwoLayerRules(board,layout).validate(layout);
            MediumBoardNormalAdmission.fromAcceptedRb56Route(board,result,plan.layoutSeed,plan.routingSeed)
                .requireConstruction(board,layout);
            Rectangle outline=layout.getBoardOutline();
            long area=(long)outline.width*outline.height,span=0,copper=0;int pairs=0;
            for(PcbTraceGeometry trace:layout.getTraces()) {
                int[] xs=trace.getXPoints(),ys=trace.getYPoints();
                for(int point=1;point<xs.length;point++)copper+=Math.abs((long)xs[point]-xs[point-1])+
                    Math.abs((long)ys[point]-ys[point-1]);
            }
            Vector<PcbFootprint> footprints=new Vector<PcbFootprint>();
            for(PcbComponentPlacement part:layout.getComponents())
                footprints.add(PcbFootprint.fromPhysicalPackage(board.getComponent(part.getComponentId()),part.getPose(),
                    part.getPhysicalGeometry()));
            for(PcbFootprint part:footprints) {
                PcbComponentPlacement placement=part.getPlacement();
                PcbSupportPairPlacement.Pair pair=PcbSupportPairPlacement.find(board,board.getPlacementConstraints(),part,
                    footprints,new Point(placement.getX(),placement.getY()));
                if(pair!=null) {
                    long distance=pair.endpointSpan(part);span+=distance;pairs++;
                    System.out.println("PCB_PIN_LOCAL_PAIR seed="+seeds[i]+" source="+placement.getComponentId()+
                        " owner="+pair.ownerId+" endpoints="+pair.firstAnchor.getPadId()+","+pair.secondAnchor.getPadId()+
                        " span="+distance);
                }
            }
            System.out.println("PCB_PIN_LOCAL_CASE seed="+seeds[i]+" area="+area+" baseline="+areas[i]+
                " pairs="+pairs+" span="+span+" copper="+copper+" geometry="+PlayerSessionFingerprint.of(layout.geometryFingerprint()));
            if(area>areas[i])throw new AssertionError("local area regression: "+seeds[i]);
            if(i==5 && span>baselineSpans[i]*3/4)throw new AssertionError("paired-pin span gain below declared quarter");
            if(Rb56Placement.CURRENT_VERSION>=11 && span>accepted10Spans[i])
                throw new AssertionError("paired-pin span regresses against accepted10: "+seeds[i]);
            totalSpan+=span;
        }
        if(totalSpan>=150100)throw new AssertionError("paired-pin aggregate does not improve the frozen9 baseline");
        System.out.println("PASS: pin-local packing contracts roots=6");
    }
}
