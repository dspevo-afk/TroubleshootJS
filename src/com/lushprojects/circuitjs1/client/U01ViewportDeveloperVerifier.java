package com.lushprojects.circuitjs1.client;

/** Compiled viewport/input contract; real pointer trials are recorded separately. */
final class U01ViewportDeveloperVerifier {
    static void verify(CirSim sim, int fixtureCount, boolean forced) {
        int checks = U01ViewportChecks.verify();
        PcbWorkbenchController controller = sim.pcbWorkbenchController;
        PcbWorkbenchRenderer renderer = controller.getRenderer();
        renderer.draw(new Graphics(sim.backcontext), sim.circuitArea);
        PcbViewport view = renderer.getViewport();
        String copper = sim.getGeneratedBoardInstance().getPristineConductorGraph().toCanonical();
        PcbViewport.Transform saved = view.permanent();
        for (int cause = 0; cause < 4; cause++) {
            controller.space(true, 100, 100); controller.pointerMove(130, 120); controller.wheel(-1, 130, 120);
            controller.cancelViewGesture();
            PcbViewport.Transform now = view.current();
            if (view.isInspecting() || saved.scale != now.scale || saved.x != now.x || saved.y != now.y)
                throw new IllegalStateException("U01 controller cancellation changed camera");
            checks++;
        }
        int targets = 0;
        for (String id : sim.getGeneratedBoardInstance().getBoard().getPadIds()) {
            PcbPadPlacement pad = sim.getGeneratedBoardInstance().getPcbLayout().getPad(id);
            view.fit(new Rectangle(pad.getX() - 80, pad.getY() - 80, 160, 160)); renderer.updateProjection();
            Point point = renderer.getPadPoint(id); ProbeTarget target = renderer.findProbeTarget(sim, point.x, point.y);
            if (!(target instanceof BoardPadProbeTarget) || !id.equals(((BoardPadProbeTarget)target).getPadId()) ||
                    target.getMeasurementEndpoint() != sim.getGeneratedBoardInstance().getSimulationBindings().getEndpoint(id) ||
                    !point.equals(target.getMarkerPoint())) throw new IllegalStateException("U01 live pad/marker correspondence: " + id);
            targets++;
        }
        renderer.setViewingFace(PcbBoardSide.BOTTOM); renderer.setViewingFace(PcbBoardSide.TOP);
        if (!copper.equals(sim.getGeneratedBoardInstance().getPristineConductorGraph().toCanonical()))
            throw new IllegalStateException("U01 view changed copper identity");
        view.fitBoard(); renderer.updateProjection();
        if (forced) throw new IllegalStateException("U01 forced-negative");
        if (fixtureCount > 0) {
            renderer.showViewportFixture(sim, fixtureCount);
            renderer.draw(new Graphics(sim.backcontext), sim.circuitArea);
        }
        publish(checks, targets);
    }
    private static native void publish(int assertions, int targets) /*-{
        $doc.documentElement.setAttribute('data-tsj-u01-report', JSON.stringify({status:'PASS',schema:1,
            assertions:assertions,liveTargets:targets,identityUnchanged:true}));
    }-*/;
    private U01ViewportDeveloperVerifier() { }
}
