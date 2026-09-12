package com.lushprojects.circuitjs1.client;

import java.util.HashMap;
import java.util.Vector;

/** Explicit developer surface fixture. Endpoints observe the active CircuitJS owner; no model is invented. */
final class U01ViewportFixture {
    private final CirSim sim;
    private final PcbWorkbenchRenderer renderer;
    private final Vector<PhysicalPartRenderContext> contexts = new Vector<PhysicalPartRenderContext>();
    private final PhysicalPartRenderer provider;
    final Rectangle outline;
    private int frames, inputs;
    private long frameMillis, inputMillis;
    U01ViewportFixture(CirSim sim, PcbWorkbenchRenderer renderer, int count) {
        if (count != 15 && count != 30 && count != 56 && count != 100)
            throw new IllegalArgumentException("Unsupported viewport fixture size");
        this.sim = sim; this.renderer = renderer;
        int columns = (int)Math.ceil(Math.sqrt(count * 1.5));
        outline = new Rectangle(20, 20, columns * 340 + 80, ((count + columns - 1) / columns) * 210 + 80);
        provider = new P01PhysicalPoseDeveloperVerifier.SurfaceRenderer(
            StandardPhysicalPartRenderProviders.createMultiTerminalRenderer());
        Vector<CircuitElm> backing = new Vector<CircuitElm>();
        for (CircuitElm element : sim.elmList) if (element.getPostCount() > 0 && backing.size() < 3) backing.add(element);
        if (backing.size() != 3) throw new IllegalStateException("Missing live fixture endpoints");
        for (int index = 0; index < count; index++) {
            PhysicalPackage physical = index % 3 == 0 ? PhysicalPackages.DEV_SMD_0805 :
                index % 3 == 1 ? PhysicalPackages.DEV_SMD_SOT23 : PhysicalPackages.AXIAL_RESISTOR;
            String id = "U" + (index + 1);
            TroubleshootBoard board = new TroubleshootBoard("U01_SURFACE_" + index);
            BoardComponent component = new BoardComponent(id, "FIXTURE", physical); board.addComponent(component);
            Vector<PhysicalPartTerminal> terminals = new Vector<PhysicalPartTerminal>();
            for (int i = 0; i < physical.getTerminalCount(); i++) {
                String terminal = physical.getTerminalIds().get(i);
                board.addNet(new BoardNet("N" + i)); board.addPad(new BoardPad(id + "." + terminal, id, terminal, "N" + i));
                terminals.add(new PhysicalPartTerminal(id, terminal, new CircuitPostMeasurementEndpoint(backing.get(i), 0)));
            }
            PhysicalBoardRuntime runtime = new PhysicalBoardRuntime(board);
            FixedPhysicalPart<BasicPhysicalSpecification> part = new FixedPhysicalPart<BasicPhysicalSpecification>(id,
                new BasicPhysicalSpecification("U01_CANARY"), new PhysicalNameplate(id, "Developer surface"),
                physical, terminals, backing, new PhysicalPartProvenance(PhysicalPartProvenance.DEVELOPER_CANARY, id));
            runtime.createSlot(id).install(part);
            PcbBoardSide face = index % 4 == 3 && physical != PhysicalPackages.AXIAL_RESISTOR ? PcbBoardSide.BOTTOM : PcbBoardSide.TOP;
            PcbFootprint footprint = PcbFootprint.fromPhysicalPackage(component,
                new PcbPackagePose(100 + index % columns * 340, 100 + index / columns * 210, PcbRotation.DEG_0, face), physical.getGeometry());
            HashMap<String,Point> points = new HashMap<String,Point>();
            for (PcbPadPlacement pad : footprint.getPads()) points.put(pad.getPadId(), new Point(pad.getX(), pad.getY()));
            contexts.add(new PhysicalPartRenderContext(renderer, footprint.getPlacement(), part, physical, 0, false, board, points));
        }
    }
    void draw(Graphics graphics) {
        long start = System.currentTimeMillis();
        Rectangle bounds = renderer.screenRectForProvider(outline);
        graphics.setColor("#0d5b3d"); graphics.fillRect(bounds.x, bounds.y, bounds.width, bounds.height);
        for (PhysicalPartRenderContext context : contexts) {
            if (!renderer.canProbeInstalledContext(context)) continue;
            PhysicalPartRenderGeometry geometry = provider.getInstalledGeometry(context);
            provider.drawInstalled(graphics, context, geometry, false);
            Rectangle body = geometry.getBodyBounds();
            graphics.setColor("#ffffff"); graphics.setFont(new Font("sans-serif", 0, Math.max(10, context.scale(14))));
            graphics.drawString(context.getComponentId(), body.x, body.y - 6);
        }
        frames++; frameMillis += System.currentTimeMillis() - start;
        publish();
    }
    ProbeTarget target(int x, int y) {
        long start = System.currentTimeMillis(); ProbeTarget result = null;
        for (PhysicalPartRenderContext context : contexts) {
            if (!renderer.canProbeInstalledContext(context)) continue;
            PhysicalPartRenderGeometry geometry = provider.getInstalledGeometry(context);
            for (PhysicalPartRenderTerminal terminal : geometry.getTerminals()) if (terminal.containsProbe(x, y)) {
                if (result != null) return null;
                result = new PhysicalPartRenderCanaryProbeTarget(sim, context, terminal.getTerminalIndex());
            }
        }
        inputs++; inputMillis += System.currentTimeMillis() - start; publish(); return result;
    }
    private void publish() {
        setMetrics(contexts.size(), frames, frameMillis, inputs, inputMillis, renderer.getViewport().isInspecting());
    }
    private static native void setMetrics(int count, int frames, double frameMs, int inputs, double inputMs, boolean loupe) /*-{
        $doc.documentElement.setAttribute('data-tsj-u01-fixture', JSON.stringify({count:count,frames:frames,
            frameMs:frameMs,inputs:inputs,inputMs:inputMs,loupe:loupe,scope:'structural surface rendering; live endpoint observations'}));
    }-*/;
}
