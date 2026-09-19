package com.lushprojects.circuitjs1.client;

/** Matched-input routed compaction, not a comparison between different search winners. */
public final class DensePcbPackingContractTest {
    private static int checks;
    public static void main(String[] args) {
        CirSim sim=new CirSim();sim.gridSize=16;sim.gridMask=~15;sim.gridRound=7;CircuitElm.sim=sim;
        long[] seeds={0,1,2,3,17,42,101,-1,9007199254740993L,Long.MIN_VALUE,Long.MAX_VALUE};
        long areaSum=0;int reduced=0;
        for(long seed:seeds) {
            Rb15Plan intent=Rb15Plan.resolve(seed);TroubleshootBoard board=intent.board();
            PcbPlacementConstraints constraints=board.getPlacementConstraints();
            PcbPlacementPlanner.Plan plan=null;PcbBoardLayout routed=null;
            // Keep rejected physical attempts; compare the first legal identical input.
            for(int attempt=0;attempt<80;attempt++) {
                try {
                    plan=new PcbPlacementPlanner(StandardPcbFootprintProviders.createRegistry())
                        .plan(board,constraints,intent.layoutSeed,attempt);
                    routed=plan.materialize();
                    PcbNetRouter.route(routed,board,plan.outline,attempt,
                        new SeededPcbLayoutGenerator.AttemptObserver() {
                            public void check(int ignored) { GenerationWorkScope.check(); }
                        },true,intent.routingSeed);
                    routed.validateRoutingGeometry(board);
                    routed.validateRouteQuality();
                    break;
                } catch(PcbPlacementPlanner.Rejected rejected) {
                    routed=null;
                } catch(PcbNetRouter.Rejected rejected) {
                    routed=null;
                } catch(PcbBoardLayout.RouteQualityRejectedException rejected) {
                    routed=null;
                }
            }
            check(routed!=null,"No legal compaction input for "+seed);
            String beforeFingerprint=routed.geometryFingerprint();
            PcbPlacementCompactor.Routed packed=PcbPlacementCompactor.compactRouted(board,constraints,plan,routed);
            long before=(long)plan.outline.width*plan.outline.height;
            long after=(long)packed.plan.outline.width*packed.plan.outline.height;
            check(after<=before,"Routed compaction grew the same input");
            if(after<before)reduced++;
            check(beforeFingerprint.equals(routed.geometryFingerprint()),"Compaction changed original copper");
            check(packed.layout.geometryFingerprint().equals(PcbPlacementCompactor
                .compactRouted(board,constraints,plan,routed).layout.geometryFingerprint()),"Compaction is not deterministic");
            PcbPlacementPlanner.validate(board,constraints,packed.plan.outline,packed.plan.footprints);
            packed.layout.validateRoutingGeometry(board);
            packed.layout.validateRouteQuality();
            check(packed.layout.getComponents().size()==16 && packed.layout.getPads().size()==routed.getPads().size(),
                "Compaction discarded physical inventory");
            for(PcbComponentPlacement part:routed.getComponents()) {
                PcbComponentPlacement moved=packed.layout.getComponent(part.getComponentId());
                int dx=moved.getX()-part.getX(),dy=moved.getY()-part.getY();
                check(part.getGeometryRealization().isEquivalentTo(moved.getGeometryRealization()),
                    "Compaction changed a package or lead geometry");
                for(String id:board.getComponent(part.getComponentId()).getPadIds()) {
                    PcbPadPlacement a=routed.getPad(id),b=packed.layout.getPad(id);
                    check(b.getX()==a.getX()+dx && b.getY()==a.getY()+dy,"Pad did not follow rigid package translation");
                    check(a.getProbeBounds().width==b.getProbeBounds().width &&
                        a.getProbeBounds().height==b.getProbeBounds().height,"Compaction changed probe access dimensions");
                }
            }
            PcbBoardLayout published=new SeededPcbLayoutGenerator().generate(board,intent.layoutSeed,
                intent.routingSeed,SupportedEnvelope.current());
            published.validateGeometry(board);SupportedEnvelope.current().requireBounds(board,published);
            Rectangle r=published.getBoardOutline();areaSum+=(long)r.width*r.height;
            System.out.println("DENSE_MATCHED seed="+seed+" before="+before+" after="+after+
                " published="+r.width+"x"+r.height);
        }
        check(reduced>0,"No matched routed input was compacted");
        System.out.println("PASS: dense PCB packing seeds="+seeds.length+" assertions="+checks+
            " reduced="+reduced+" areaSum="+areaSum);
    }
    private static void check(boolean ok,String detail){checks++;if(!ok)throw new AssertionError(detail);}
}
