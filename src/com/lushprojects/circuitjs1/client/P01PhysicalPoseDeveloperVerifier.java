package com.lushprojects.circuitjs1.client;

import java.util.HashMap;
import java.util.Vector;

/** P01-only compiled probe/render canaries. No SMD gameplay or layer policy is implied. */
final class P01PhysicalPoseDeveloperVerifier {
    private static int assertions;

    static String verify(CirSim sim, boolean forced) {
        assertions = 0;
        if (sim == null || sim.pcbWorkbenchController == null || sim.backcontext == null)
            throw new IllegalStateException("P01 requires the production workbench");
        P01PhysicalPoseChecks pure = P01PhysicalPoseChecks.verify();
        Vector<CircuitElm> original = new Vector<CircuitElm>(sim.elmList);
        GeneratedBoardInstance instance = sim.getGeneratedBoardInstance();
        PhysicalPartRenderRegistry registry = StandardPhysicalPartRenderProviders.createRegistry();
        final PhysicalPartRenderer surface = new SurfaceRenderer(registry.getRenderer(PhysicalPackages.MULTI_TERMINAL, null));
        PhysicalPartRenderProvider provider = new PhysicalPartRenderProvider() {
            public PhysicalPartRenderer getRenderer(PhysicalPart<?> part) { return surface; }
        };
        registry.register(PhysicalPackages.DEV_SMD_0805, provider);
        registry.register(PhysicalPackages.DEV_SMD_SOT23, provider);
        PcbWorkbenchRenderer renderer = new PcbWorkbenchRenderer(instance, sim.getBoardModificationController(),
            instance.getPcbLayout(), registry);
        int rendered = 0;
        for (PhysicalPackage p : new PhysicalPackage[] {PhysicalPackages.DEV_SMD_0805, PhysicalPackages.DEV_SMD_SOT23}) {
            TroubleshootBoard board = new TroubleshootBoard("P01_CANARY");
            BoardComponent component = new BoardComponent("U1", p.getId(), p);
            board.addComponent(component);
            Vector<PhysicalPartTerminal> terminals = new Vector<PhysicalPartTerminal>();
            Vector<CircuitElm> backing = new Vector<CircuitElm>();
            // Read-only endpoints from the active solver: no test wires inserted into its graph.
            for (CircuitElm element : original) {
                if (element.getPostCount() > 0 && backing.size() < p.getTerminalCount()) backing.add(element);
            }
            require(backing.size() == p.getTerminalCount(), "distinct live endpoint fixtures");
            for (int i=0; i<p.getTerminalCount(); i++) {
                String id = p.getTerminalIds().get(i);
                board.addNet(new BoardNet("N"+i));
                board.addPad(new BoardPad("U1."+id,"U1",id,"N"+i));
                terminals.add(new PhysicalPartTerminal("U1",id,new CircuitPostMeasurementEndpoint(backing.get(i),0)));
            }
            board.validate();
            PhysicalBoardRuntime runtime = new PhysicalBoardRuntime(board);
            FixedPhysicalPart<BasicPhysicalSpecification> part = new FixedPhysicalPart<BasicPhysicalSpecification>(
                "U1", new BasicPhysicalSpecification("P01_CANARY"),new PhysicalNameplate("U1","P01 canary"),
                p,terminals,backing,new PhysicalPartProvenance(PhysicalPartProvenance.DEVELOPER_CANARY,"U1"));
            runtime.createSlot("U1").install(part);
            runtime.validate();
            Rectangle outline = instance.getPcbLayout().getBoardOutline();
            int x=outline.x+100, y=outline.y+100;
            PhysicalPackageGeometry g=p.getGeometry();
            for (PcbRotation rotation : p.getAllowedRotations()) {
                for (PcbBoardSide side : p.getAllowedMountingSides()) {
                    PcbPackagePose pose=new PcbPackagePose(x,y,rotation,side);
                    PcbFootprint footprint=PcbFootprint.fromPhysicalPackage(component,pose,g);
                    PcbComponentPlacement placement=footprint.getPlacement();
                    HashMap<String,Point> pads=new HashMap<String,Point>();
                    for (int i=0;i<p.getTerminalCount();i++) pads.put("U1."+p.getTerminalIds().get(i),placement.getPadPoint(i));
                    PhysicalPartRenderCanaryResult result=renderer.renderProviderCanaryForDeveloperVerification(sim,
                        new Graphics(sim.backcontext),board,part,placement,pads);
                    rendered++;
                    require(result.wasBodyDrawn() && "U1".equals(result.getHitComponentId()),"actual draw and component hit");
                    PhysicalPartRenderGeometry actual=result.getGeometry();
                    require(actual.getBodyBounds().equals(renderer.screenRectForProvider(
                        P01PhysicalPoseChecks.expectedRectangle(g.getBodyBounds(),g.getWidth(),g.getHeight(),x,y,rotation,side))),
                        "body uses independent pose projection");
                    for (int i=0;i<p.getTerminalCount();i++) {
                        PhysicalPackageGeometry.Terminal t=g.getTerminal(i);
                        Point expected=P01PhysicalPoseChecks.expectedPoint(t.getPadCenter(),g.getWidth(),g.getHeight(),x,y,rotation,side);
                        PhysicalPartRenderTerminal terminal=actual.getTerminal(i);
                        require(terminal.getPoint().equals(renderer.screenPointForProvider(expected)),"probe point follows pose");
                        require(terminal.getPadBounds().equals(renderer.screenRectForProvider(
                            P01PhysicalPoseChecks.expectedRectangle(t.getPadBounds(),g.getWidth(),g.getHeight(),x,y,rotation,side))),
                            "rendered surface pad follows pose");
                        require(terminal.getBoardPadProbeBounds().equals(renderer.screenRectForProvider(
                            P01PhysicalPoseChecks.expectedRectangle(t.getBoardPadProbeBounds(),g.getWidth(),g.getHeight(),x,y,rotation,side))),
                            "probe bounds follow pose");
                        require(terminal.getTerminalId().equals(t.getTerminalId()) &&
                            terminal.getBoardPadId().equals("U1."+t.getTerminalId()),"terminal identity never permutes");
                        ProbeTarget target=result.getProbeTargets().get(i);
                        require(target != null && target.isValid() && target.getMeasurementEndpoint()==part.getTerminal(i).getEndpoint(),
                            "real hit resolves the exact live CircuitJS endpoint");
                        require(target.getMarkerPoint().equals(renderer.screenPointForProvider(expected)),"marker follows hit geometry");
                        require(t.getAttachment()==PcbTerminalAttachment.SURFACE_PAD,"surface package has no barrel");
                    }
                }
            }
        }
        require(original.equals(sim.elmList),"probe canaries did not change the live graph");
        require(!forced,"forced-negative");
        String report="{\"schema\":1,\"status\":\"PASS\",\"pureAssertions\":"+pure.getAssertions()+
            ",\"declaredPoses\":"+pure.getPoses()+",\"renderedSmdPoses\":"+rendered+
            ",\"runtimeAssertions\":"+assertions+",\"liveGraphUnchanged\":true}";
        publish(report);
        return report;
    }

    private static void require(boolean ok,String label) {
        assertions++;
        if (!ok) throw new IllegalStateException("P01 " + label);
    }

    private static native void publish(String report) /*-{
        $doc.documentElement.setAttribute("data-tsj-p01-report",report);
    }-*/;

    /** Geometry and hit testing use the maintained provider; only canary pad artwork is surface-specific. */
    private static final class SurfaceRenderer implements PhysicalPartRenderer {
        private final PhysicalPartRenderer delegate;
        SurfaceRenderer(PhysicalPartRenderer delegate) { this.delegate=delegate; }
        public PhysicalPartRenderGeometry getInstalledGeometry(PhysicalPartRenderContext c) { return delegate.getInstalledGeometry(c); }
        public PhysicalPartRenderGeometry getLooseGeometry(PhysicalPartRenderContext c) { return delegate.getLooseGeometry(c); }
        public ProbeTarget createInstalledProbeTarget(CirSim sim,PhysicalPartRenderContext c,int i) {
            return delegate.createInstalledProbeTarget(sim,c,i);
        }
        public ProbeTarget createLooseProbeTarget(CirSim sim,PhysicalPartRenderContext c,int i) {
            throw new UnsupportedOperationException("P01 canary is installed-only");
        }
        public void drawLoose(Graphics g,PhysicalPartRenderContext c,PhysicalPartRenderGeometry p,boolean selected) {
            throw new UnsupportedOperationException("P01 canary is installed-only");
        }
        public void drawInstalled(Graphics graphics,PhysicalPartRenderContext context,PhysicalPartRenderGeometry geometry,boolean selected) {
            Rectangle body=geometry.getBodyBounds();
            graphics.setColor("#485b69"); graphics.fillRect(body.x,body.y,body.width,body.height);
            graphics.setColor("#b8c8c2");
            for (PhysicalPartRenderTerminal terminal : geometry.getTerminals()) {
                Point a=terminal.getLeadEndPoint(), b=terminal.getLeadBodyPoint();
                graphics.drawLine(a.x,a.y,b.x,b.y);
                Rectangle pad=terminal.getPadBounds();
                graphics.fillRect(pad.x,pad.y,pad.width,pad.height);
            }
            context.markBodyDrawn();
        }
    }
}
