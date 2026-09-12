package com.lushprojects.circuitjs1.client;

import java.util.HashMap;
import java.util.Vector;

/** Compiled production projection and face-access checks. Never enables player SMD or cut gameplay. */
final class P02ConductorDeveloperVerifier {
    private static int assertions;
    static String verify(CirSim sim, boolean forced) {
        assertions=0;
        require(sim != null && sim.pcbWorkbenchController != null && sim.backcontext != null,
            "requires the production workbench");
        int pure=P02ConductorChecks.verify();
        GeneratedBoardInstance instance=sim.getGeneratedBoardInstance();
        Vector<CircuitElm> original=new Vector<CircuitElm>(sim.elmList);
        PcbConductorGraph graph=instance.getPristineConductorGraph();
        PcbConductorGraph.Snapshot current=instance.getCurrentConductorSnapshot();
        String canonical=graph.toCanonical();
        PcbConductorProjection.Report projection=PcbConductorProjection.audit(instance,sim.elmList);
        require(projection.pads==instance.getBoard().getPadIds().size(),"all live board pads audited");
        int rejectedCuts=0;
        for (PcbConductorGraph.Edge edge : graph.getEdges().values()) {
            if (edge.kind != PcbConductorGraph.Kind.TRACE) continue;
            PcbConductorGraph.Snapshot cut=current.withCut(edge.id,true);
            boolean separates=false;
            for (String a : instance.getBoard().getPadIds()) for (String b : instance.getBoard().getPadIds())
                if (current.padsConnected(a,b) && !cut.padsConnected(a,b)) separates=true;
            if (!separates) continue;
            boolean rejected=false;
            try { PcbConductorProjection.audit(instance,sim.elmList,cut); }
            catch (IllegalStateException expected) { rejected=true; }
            require(rejected,"metadata-only cut rejected against unchanged real solver"); rejectedCuts++;
        }
        require(rejectedCuts>0,"at least one live solver projection falsifier executed");
        PhysicalPartRenderRegistry registry=StandardPhysicalPartRenderProviders.createRegistry();
        final PhysicalPartRenderer surface=new P01PhysicalPoseDeveloperVerifier.SurfaceRenderer(
            registry.getRenderer(PhysicalPackages.MULTI_TERMINAL,null));
        PhysicalPartRenderProvider provider=new PhysicalPartRenderProvider() {
            public PhysicalPartRenderer getRenderer(PhysicalPart<?> part) { return surface; }
        };
        registry.register(PhysicalPackages.DEV_SMD_0805,provider);
        registry.register(PhysicalPackages.DEV_SMD_SOT23,provider);
        PcbWorkbenchRenderer renderer=new PcbWorkbenchRenderer(instance,sim.getBoardModificationController(),
            instance.getPcbLayout(),registry);
        int poses=0;
        for (PhysicalPackage physical : new PhysicalPackage[]{PhysicalPackages.DEV_SMD_0805,PhysicalPackages.DEV_SMD_SOT23}) {
            TroubleshootBoard board=new TroubleshootBoard("P02_ACCESS_CANARY");
            BoardComponent component=new BoardComponent("U1",physical.getId(),physical); board.addComponent(component);
            Vector<CircuitElm> backing=new Vector<CircuitElm>();
            Vector<PhysicalPartTerminal> terminals=new Vector<PhysicalPartTerminal>();
            for (CircuitElm element : original)
                if (element.getPostCount()>0 && backing.size()<physical.getTerminalCount()) backing.add(element);
            require(backing.size()==physical.getTerminalCount(),"live canary endpoints available");
            for (int i=0; i<physical.getTerminalCount(); i++) {
                String id=physical.getTerminalIds().get(i);
                board.addNet(new BoardNet("N"+id)); board.addPad(new BoardPad("U1."+id,"U1",id,"N"+id));
                terminals.add(new PhysicalPartTerminal("U1",id,new CircuitPostMeasurementEndpoint(backing.get(i),0)));
            }
            board.validate();
            FixedPhysicalPart<BasicPhysicalSpecification> part=new FixedPhysicalPart<BasicPhysicalSpecification>(
                "U1",new BasicPhysicalSpecification("P02_CANARY"),new PhysicalNameplate("U1","P02 canary"),
                physical,terminals,backing,new PhysicalPartProvenance(PhysicalPartProvenance.DEVELOPER_CANARY,"U1"));
            PhysicalBoardRuntime runtime=new PhysicalBoardRuntime(board);
            runtime.createSlot("U1").install(part); runtime.validate();
            Rectangle outline=instance.getPcbLayout().getBoardOutline();
            for (PcbRotation rotation : physical.getAllowedRotations()) for (PcbBoardSide mount : physical.getAllowedMountingSides()) {
                PcbFootprint footprint=PcbFootprint.fromPhysicalPackage(component,
                    new PcbPackagePose(outline.x+100,outline.y+100,rotation,mount),physical.getGeometry());
                PcbComponentPlacement placement=footprint.getPlacement();
                HashMap<String,Point> points=new HashMap<String,Point>();
                for (PcbPadPlacement pad : footprint.getPads()) points.put(pad.getPadId(),new Point(pad.getX(),pad.getY()));
                renderer.setViewingFace(mount);
                PhysicalPartRenderCanaryResult visible=renderer.renderProviderCanaryForDeveloperVerification(sim,
                    new Graphics(sim.backcontext),board,part,placement,points);
                require(visible.wasBodyDrawn() && "U1".equals(visible.getHitComponentId()),"mounting face draws and selects actual body");
                for (int i=0; i<physical.getTerminalCount(); i++) {
                    ProbeTarget target=visible.getProbeTargets().get(i);
                    require(target!=null && target.isValid() && target.getMeasurementEndpoint()==part.getTerminal(i).getEndpoint(),
                        "visible surface resolves exact live endpoint");
                }
                Point tray=renderer.screenWorkbenchPointForProvider(new Point(900,100));
                renderer.setViewingFace(mount.opposite());
                PhysicalPartRenderCanaryResult hidden=renderer.renderProviderCanaryForDeveloperVerification(sim,
                    new Graphics(sim.backcontext),board,part,placement,points);
                require(!hidden.wasBodyDrawn() && hidden.getHitComponentId()==null,"opposite face cannot draw or select SMD body");
                require(tray.equals(renderer.screenWorkbenchPointForProvider(new Point(900,100))),"board flip does not move tray");
                for (int i=0; i<physical.getTerminalCount(); i++) {
                    require(hidden.getProbeTargets().get(i)==null,"projected underside cannot probe surface pad");
                    ProbeTarget stale=visible.getProbeTargets().get(i);
                    require(!stale.isValid() && stale.getMeasurementEndpoint()==null,"opposite-face target loses electrical access");
                }
                poses++;
            }
        }
        require(original.equals(sim.elmList) && current==instance.getCurrentConductorSnapshot(),"checks do not change live graph or current copper");
        require(canonical.equals(graph.toCanonical()),"render and probe checks preserve frozen conductor identity");
        require(!forced,"forced-negative");
        String report="{\"schema\":1,\"family\":"+
            quote(instance.getCircuitFamilyId())+
            ",\"status\":\"PASS\",\"pureAssertions\":"+pure+
            ",\"runtimeAssertions\":"+assertions+",\"smdPoses\":"+poses+",\"projectionPads\":"+projection.pads+
            ",\"sharedPostAliases\":"+projection.sharedPostAliases+",\"modeledComponentElements\":"+projection.modeledComponentElements+
            ",\"rejectedMetadataCuts\":"+rejectedCuts+",\"liveGraphUnchanged\":true}";
        publish(report); return report;
    }
    private static void require(boolean condition, String message) {
        assertions++; if (!condition) throw new IllegalStateException("P02 " + message);
    }
    private static native String quote(String value) /*-{
        return $wnd.JSON.stringify(value);
    }-*/;
    static native void publishReadiness(boolean ready, boolean settled, boolean proof) /*-{
        $doc.documentElement.setAttribute("data-tsj-p02-readiness",$wnd.JSON.stringify(
            {ready:ready,settled:settled,internalProof:proof}));
    }-*/;
    private static native void publish(String report) /*-{
        $doc.documentElement.removeAttribute("data-tsj-p02-readiness");
        $doc.documentElement.setAttribute("data-tsj-p02-report",report);
    }-*/;
    private P02ConductorDeveloperVerifier() { }
}
