package com.lushprojects.circuitjs1.client;

import java.util.Collections;
import java.util.HashSet;
import java.util.Vector;

/** Frozen structural comparisons, not solver-backed 30/56/100-part player boards. */
public final class P07LayerCorpus {
    static P03StructuralFixtures.Fixture inventory(String id,boolean linked) throws Exception {
        P03StructuralFixtures.Fixture original=P03StructuralFixtures.read(id);
        if(!linked) return original;
        P03StructuralFixtures.Fixture result=new P03StructuralFixtures.Fixture(id+"_P07_FIXED_LINK");
        result.registry.register(PhysicalPackages.RAISED_FACTORY_LINK,new PcbFootprintProvider() {
            public PcbFootprint create(BoardComponent part,int x,int y,java.util.Random random,Rectangle outline) {
                return PcbFootprint.fromPhysicalPackage(part,x,y);
            }
        });
        result.regions.putAll(original.regions); result.domains.putAll(original.domains);
        for(String net:original.board.getNetIds()) result.board.addNet(new BoardNet(net));
        Vector<String> ids=original.board.getComponentIds(); Collections.sort(ids);
        HashSet<PhysicalPackage> registered=new HashSet<PhysicalPackage>(); boolean replaced=false;
        for(String idPart:ids) {
            PhysicalPackage physical=original.board.getComponent(idPart).getPhysicalPackage();
            if(!replaced && physical.getTerminalCount()==2 && !physical.isConnector()) {
                physical=PhysicalPackages.RAISED_FACTORY_LINK; replaced=true;
            } else if(registered.add(physical)) result.registry.register(physical,new PcbFootprintProvider() {
                public PcbFootprint create(BoardComponent part,int x,int y,java.util.Random random,Rectangle outline) {
                    return PcbFootprint.fromPhysicalPackage(part,x,y);
                }
            });
            result.board.addComponent(new BoardComponent(idPart,"STRUCTURAL",physical));
            for(String pad:original.board.getComponent(idPart).getPadIds()) {
                BoardPad old=original.board.getPad(pad);
                result.board.addPad(new BoardPad(pad,idPart,old.getTerminalId(),old.getNetId()));
            }
        }
        if(!replaced) throw new AssertionError("No two-terminal fixed-link candidate in "+id);
        result.board.validate(); return result;
    }
    public static void main(String[] args) throws Exception {
        int rows=0,successes=0;
        for(String family:new String[]{"RB30","RB56","RB100"})
            for(boolean linked:new boolean[]{false,true}) for(PcbCopperLayer primary:PcbCopperLayer.values()) {
                P03StructuralFixtures.Fixture fixture=inventory(family,linked);
                PcbPlacementConstraints original=P03PlacementContractTest.constraints(fixture);
                fixture.board.setPlacementConstraints(new PcbPlacementConstraints(original.getParts(),original.getBarriers(),primary));
                PcbPlacementPlanner planner=new PcbPlacementPlanner(fixture.registry);
                PcbPlacementPlanner.Plan plan=null;
                for(int candidate=0;candidate<PcbPlacementPlanner.OUTLINE_CANDIDATES;candidate++) {
                    try { plan=planner.plan(fixture.board,fixture.board.getPlacementConstraints(),3,candidate); break; }
                    catch(PcbPlacementPlanner.Rejected expected) { }
                }
                if(plan==null) throw new AssertionError("Frozen P07 placement unavailable: "+family);
                PcbBoardLayout placement=plan.materialize();
                String identity=P05RoutingCorpus.identity(fixture.board,placement);
                for(PcbLayerRoutingPrototype.Policy policy:PcbLayerRoutingPrototype.Policy.values()) {
                    long started=System.nanoTime();
                    PcbLayerRoutingPrototype.Result result=PcbLayerRoutingPrototype.route(fixture.board,placement,
                        policy,P06FactoryLinkFixtures.OBSERVER);
                    long elapsed=System.nanoTime()-started;
                    if(!identity.equals(P05RoutingCorpus.identity(fixture.board,placement))) throw new AssertionError("P07 input changed");
                    if(result.accepted() && !identity.equals(P05RoutingCorpus.identity(fixture.board,result.layout)))
                        throw new AssertionError("P07 changed packages, placement or netlist");
                    report(family,linked,primary,plan.candidate,identity,policy.toString(),result,placement,elapsed,fixture.board);
                    rows++; if(result.accepted()) successes++;
                }
                if(!linked) {
                    PcbBoardLayout control=placement.copyForRouting(); long started=System.nanoTime();
                    String outcome="SUCCESS";
                    try {
                        PcbNetRouter.route(control,fixture.board,control.getBoardOutline(),0,P06FactoryLinkFixtures.OBSERVER);
                        new PcbTwoLayerRules(fixture.board,control).validate(control);
                    } catch(PcbNetRouter.Rejected failure) { outcome=failure.reason.toString(); }
                    catch(PcbBoardLayout.RouteQualityRejectedException failure) { outcome="QUALITY_LIMIT"; }
                    catch(IllegalStateException failure) {
                        if(!failure.getMessage().startsWith("P07 copper crosses")) throw failure;
                        outcome="DOMAIN_BARRIER_REJECT";
                    }
                    PcbLayerRoutingPrototype.Result result=new PcbLayerRoutingPrototype.Result(
                        outcome.equals("SUCCESS")?control:null,outcome,control.getRoutingExpansions(),0,0);
                    report(family,false,primary,plan.candidate,identity,"P05_REFERENCE",result,placement,System.nanoTime()-started,fixture.board);
                    rows++; if(result.accepted()) successes++;
                }
            }
        if(rows!=54) throw new AssertionError("Incomplete frozen matrix: "+rows);
        System.out.println("PASS: P07 frozen layer corpus rows="+rows+" successes="+successes);
    }
    private static void report(String family,boolean linked,PcbCopperLayer primary,int candidate,String identity,
            String policy,PcbLayerRoutingPrototype.Result result,PcbBoardLayout placement,long nanos,TroubleshootBoard board) {
        PcbRouteMetrics metrics=result.accepted()?PcbRouteMetrics.measure(result.layout.getTraces()):null;
        int top=0,bottom=0;
        if(result.accepted()) {
            result.layout.validateRoutingGeometry(board);
            new PcbTwoLayerRules(board,result.layout).validate(result.layout);
            for(PcbTraceGeometry trace:result.layout.getTraces()) {
                if(trace.getLayer()==PcbCopperLayer.TOP) top++; else bottom++;
            }
        }
        Rectangle outline=placement.getBoardOutline();
        double scale=Math.min(1000.0/outline.width,700.0/outline.height),probe=Double.MAX_VALUE;
        for(PcbPadPlacement pad:placement.getPads()) {
            Rectangle r=pad.getProbeBounds(); probe=Math.min(probe,Math.min(r.width,r.height)*scale);
        }
        System.out.println("P07_CORPUS {\"board\":\""+family+"\",\"fixedLinkVariant\":"+linked+
            ",\"seed\":3,\"primary\":\""+primary+"\",\"candidate\":"+candidate+
            ",\"identity\":\""+identity+"\",\"policy\":\""+policy+"\",\"outcome\":\""+result.outcome+
            "\",\"parts\":"+placement.getComponents().size()+",\"expansions\":"+result.expansions+
            ",\"orderings\":"+result.orderings+",\"vias\":"+result.vias+",\"links\":"+result.links+
            ",\"length\":"+(metrics==null?"null":""+metrics.uniqueLength)+
            ",\"bends\":"+(metrics==null?"null":""+metrics.bends)+
            ",\"topRoutes\":"+top+",\"bottomRoutes\":"+bottom+
            ",\"area\":"+((long)outline.width*outline.height)+",\"elapsedMs\":"+(nanos/1000000.0)+
            ",\"fitProbePixels\":"+probe+",\"fitViaPixels\":"+(PcbTwoLayerRules.VIA_LAND*2*scale)+
            ",\"zoomFor12PixelPadTarget\":"+Math.max(1,12/probe)+",\"structuralOnly\":true}");
    }
}
