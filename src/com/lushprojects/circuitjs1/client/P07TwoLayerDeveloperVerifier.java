package com.lushprojects.circuitjs1.client;

import java.util.Vector;

/** Compiled CircuitJS, production hit resolution, face access and exact owner restoration. */
final class P07TwoLayerDeveloperVerifier {
    private static int checks;
    private static void require(boolean ok,String why) {
        checks++; if(!ok) throw new IllegalStateException("P07 "+why);
    }
    static void verify(CirSim sim,boolean forced,boolean keepBench) {
        if(!sim.troubleshootDebug || !sim.developerVerifierRunning)
            throw new IllegalStateException("P07 requires explicit developer verification");
        checks=0;
        Task41SimulationSnapshot saved=Task41SimulationSnapshot.capture(sim);
        boolean retained=false,restored=false; int expansions=0;
        double overCurrent=0,underCurrent=0;
        try {
            P07TwoLayerPrototype prototype=new P07TwoLayerPrototype();
            GeneratedBoardInstance owner=prototype.instance;
            expansions=prototype.routing.expansions;
            saved.beginProof(sim);
            sim.elmList=new Vector<CircuitElm>(); sim.adjustables=new Vector<Adjustable>();
            sim.undoStack=new Vector<String>(); sim.redoStack=new Vector<String>();
            sim.installGeneratedBoardForDeveloperVerification(owner);
            sim.setSimRunning(true); settle(sim);
            PcbWorkbenchController controller=sim.pcbWorkbenchController;
            require(controller!=null,"production workbench installed");
            PcbWorkbenchRenderer renderer=controller.getRenderer();
            PcbConductorGraph.Snapshot copper=owner.getCurrentConductorSnapshot();
            String identity=copper.getGraph().toCanonical();
            sim.setBoardPowerState(BoardPowerState.POWERED); settle(sim);
            prototype.verifyReadings(owner,BoardPowerState.POWERED,true);
            overCurrent=Math.abs(prototype.load.getCurrent()); underCurrent=Math.abs(prototype.underLoad.getCurrent());
            require(PcbConductorProjection.audit(owner,sim.elmList).pads==5,"all five pads match actual solver islands");
            require(copper.padsConnected("L.1","R.1") && copper.padsConnected("A.1","B.1") &&
                !copper.padsConnected("L.1","A.1"),"opposite-face crossing stays electrically isolated");
            PcbConductorGraph.Surface underside=null;
            for(PcbConductorGraph.Surface surface:copper.getGraph().getSurfaces())
                if(surface.edgeId!=null && surface.layer==PcbCopperLayer.BOTTOM) { underside=surface; break; }
            require(underside!=null,"required route has actual underside copper");
            renderer.setViewingFace(PcbBoardSide.TOP); settle(sim);
            require(!renderer.canProbeCopper(underside.id),"top view cannot target underside-only copper");
            require(!new BoardCopperProbeTarget(sim,owner,underside.id,renderer).isValid(),"forged hidden-layer target rejects");
            renderer.setViewingFace(PcbBoardSide.BOTTOM); settle(sim);
            Point point=renderer.getCopperPoint(underside.id);
            ProbeTarget bottom=point==null?null:controller.findProbeTarget(point.x,point.y);
            require(bottom instanceof BoardCopperProbeTarget && bottom.isValid(),"explicit flip reaches real underside route through production hit resolution");
            String pad=PcbCopperProbeAccess.connectedPad(copper,underside.id);
            require(bottom.getMeasurementEndpoint()==owner.getSimulationBindings().getEndpoint(pad),"copper resolves actual island endpoint, not a net label");
            renderer.setViewingFace(PcbBoardSide.TOP); settle(sim);
            require(!bottom.isValid() && bottom.getMeasurementEndpoint()==null,"hidden-side captured target invalidates");
            for(PcbBoardSide face:PcbBoardSide.values()) {
                renderer.setViewingFace(face); settle(sim);
                for(String id:owner.getBoard().getPadIds()) {
                    Point p=renderer.getPadPoint(id); ProbeTarget target=controller.findProbeTarget(p.x,p.y);
                    require(target instanceof BoardPadProbeTarget && target.getMeasurementEndpoint()==
                        owner.getSimulationBindings().getEndpoint(id),"flip preserves exact plated terminal "+id);
                }
                for(PcbBoardHole hole:owner.getPcbLayout().getHoles()) {
                    String id="hole/"+PcbConductorGraph.faceKey(hole.id,PcbCopperLayer.forFace(face));
                    Point p=renderer.getCopperPoint(id); ProbeTarget target=p==null?null:controller.findProbeTarget(p.x,p.y);
                    require(target instanceof BoardCopperProbeTarget && target.isValid(),"plated via land reachable on "+face);
                    String connected=PcbCopperProbeAccess.connectedPad(copper,id);
                    require(target.getMeasurementEndpoint()==owner.getSimulationBindings().getEndpoint(connected),"via joins only its physical island");
                }
            }
            int rejectedCuts=0;
            for(PcbConductorGraph.Edge edge:copper.getGraph().getEdges().values()) {
                if(edge.kind!=PcbConductorGraph.Kind.TRACE) continue;
                PcbConductorGraph.Snapshot cut=copper.withCut(edge.id,true);
                if(cut.padsConnected("A.1","B.1") && cut.padsConnected("L.1","R.1")) continue;
                boolean rejected=false;
                try { PcbConductorProjection.audit(owner,sim.elmList,cut); }
                catch(IllegalStateException expected) { rejected=true; }
                require(rejected,"metadata-only cut cannot impersonate a real solver repair"); rejectedCuts++;
            }
            require(rejectedCuts>0,"real solver falsifies at least one disconnected copper snapshot");
            require(copper==owner.getCurrentConductorSnapshot() && identity.equals(copper.getGraph().toCanonical()),
                "inspection and negative cuts leave actual copper unchanged");
            boolean rejected=false;
            try { PcbTwoLayerRules.requireDeveloperAdmission(owner.getPcbLayout(),false); }
            catch(IllegalArgumentException expected) { rejected=true; }
            require(rejected,"two-layer prototype cannot silently enter normal generation");
            rejected=false;
            try { GeneratedDiagnosticSolvabilityAdmission.validate(owner); }
            catch(IllegalArgumentException expected) { rejected=true; }
            require(rejected,"developer bench is not customer diagnostic admission");
            sim.setBoardPowerState(BoardPowerState.UNPOWERED); settle(sim);
            prototype.verifyReadings(owner,BoardPowerState.UNPOWERED,true);
            require(sim.getBoardPowerController().isElectricallyUnpowered(),"both real supplies isolate");
            require(!forced,"forced-negative");
            if(keepBench) {
                sim.setBoardPowerState(BoardPowerState.POWERED); settle(sim);
                controller.attachToSidebar(sim.verticalPanel);
                sim.refreshGeneratedUiForDeveloperVerification(); sim.updateCircuit();
                publishTargets(sim,owner,renderer,underside.id);
                retained=true;
            }
        } finally {
            if(!retained) { saved.restore(sim); saved.assertRestored(sim); restored=true; }
            publishCleanup(restored,retained);
        }
        publish("{\"schema\":1,\"status\":\"PASS\",\"runtimeAssertions\":"+checks+
            ",\"vias\":2,\"expansions\":"+expansions+",\"overAmps\":"+overCurrent+
            ",\"underAmps\":"+underCurrent+",\"ownerRestored\":"+restored+
            ",\"prototypeRetained\":"+retained+",\"normalAdoption\":false}");
    }
    private static void settle(CirSim sim) {
        for(int i=0;i<20;i++) {
            sim.updateCircuit();
            if(sim.stopMessage!=null) throw new IllegalStateException("P07 solver stopped: "+sim.stopMessage);
            if(sim.isGeneratedRuntimeSettled()) return;
        }
        throw new IllegalStateException("P07 bounded solver settlement exhausted");
    }
    private static void publishTargets(CirSim sim,GeneratedBoardInstance owner,PcbWorkbenchRenderer renderer,String underside) {
        StringBuilder json=new StringBuilder("[");
        for(PcbBoardSide face:PcbBoardSide.values()) {
            renderer.setViewingFace(face); settle(sim);
            for(PcbConductorGraph.Surface s:owner.getCurrentConductorSnapshot().getGraph().getSurfaces()) {
                if(s.padId!=null || !s.canProbe(face) || s.edgeId!=null && !s.id.equals(underside)) continue;
                Point p=renderer.getCopperPoint(s.id); require(p!=null,"published physical copper target is visible");
                String pad=PcbCopperProbeAccess.connectedPad(owner.getCurrentConductorSnapshot(),s.id);
                target(json,s.edgeId==null?"via":"trace",face,p,P07TwoLayerPrototype.voltage(owner,pad));
            }
            target(json,"ground",face,renderer.getPadPoint("G.1"),0);
        }
        renderer.setViewingFace(PcbBoardSide.TOP); settle(sim);
        json.append("]"); publishPoints(json.toString());
    }
    private static void target(StringBuilder json,String kind,PcbBoardSide face,Point p,double volts) {
        if(json.length()>1) json.append(',');
        json.append("{\"kind\":\"").append(kind).append("\",\"face\":\"").append(face)
            .append("\",\"x\":").append(p.x).append(",\"y\":").append(p.y)
            .append(",\"volts\":").append(volts).append('}');
    }
    private static native void publishPoints(String report) /*-{
        $doc.documentElement.setAttribute("data-tsj-p07-targets",report);
    }-*/;
    private static native void publishCleanup(boolean restored,boolean retained) /*-{
        $doc.documentElement.setAttribute("data-tsj-p07-cleanup",JSON.stringify({ownerRestored:restored,prototypeRetained:retained}));
    }-*/;
    private static native void publish(String report) /*-{
        $doc.documentElement.setAttribute("data-tsj-p07-report",report);
    }-*/;
    private P07TwoLayerDeveloperVerifier() { }
}
