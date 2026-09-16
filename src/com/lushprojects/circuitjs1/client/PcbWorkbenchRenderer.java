package com.lushprojects.circuitjs1.client;

import java.util.HashMap;
import java.util.Vector;

/** Common PCB canvas, transform, selection, and provider orchestration. */
class PcbWorkbenchRenderer implements PhysicalProbeProjection, CopperProbeProjection {
    private static final int DRILL_RADIUS = 5;
    private static final int PARTS_PER_TRAY_PAGE = 3;

    private final GeneratedBoardInstance instance;
    private final BoardModificationController modifications;
    private final PcbBoardLayout layout;
    private final PhysicalPartRenderRegistry renderRegistry;
    private PcbBoardSide viewingFace = PcbBoardSide.TOP;
    private PcbViewport viewport;
    private U01ViewportFixture viewportFixture;
    private PcbViewport.Transform projection;
    private PcbViewport.Transform trayProjection;
    private Rectangle trayArea = new Rectangle(0, 0, 1, 1);
    private boolean workbenchViewInitialized;
    private boolean ambiguousTarget;

    PcbBoardSide getViewingFace() { return viewingFace; }
    void setViewingFace(PcbBoardSide face) {
        if (face == null) throw new IllegalArgumentException("Missing viewing face");
        if (viewingFace != face) {
            viewingFace = face;
            viewport.setFace(face);
            updateProjection();
        }
    }
    public boolean canProbePad(String padId) { return PcbCopperAccess.canProbe(layout.getPad(padId), viewingFace); }
    boolean canProbeInstalledContext(PhysicalPartRenderContext context) {
        return context != null && context.getPlacement() != null &&
            context.getPlacement().getMountingSide() == viewingFace;
    }
    private Rectangle canvasArea = new Rectangle(0, 0, 1, 1);
    private String selectedComponentId;
    private String selectedPartId;
    private int trayPage;
    private Object looseProjectionToken = new Object();
    private boolean looseProjectionInitialized;
    private int observedLooseProjectionPage;
    private Vector<PhysicalPart<?>> observedLooseProjection =
        new Vector<PhysicalPart<?>>();
    private LooseProjectionTransitionListener looseProjectionListener;
    private final HashMap<String, InstalledProjectionObservation> installedObservations =
        new HashMap<String, InstalledProjectionObservation>();

    interface LooseProjectionTransitionListener {
        void onLooseProjectionTransition();
    }

    /** Renderer-local identity for one observed installed projection epoch. */
    private static final class InstalledProjectionObservation {
        private final PhysicalPart<?> part;
        private final PhysicalBoardSlot slot;
        private final boolean partInstalled;
        private final ComponentPhysicalState state;
        private final String connectionSignature;
        private final Object token = new Object();

        InstalledProjectionObservation(PhysicalPart<?> part, PhysicalBoardSlot slot,
                boolean partInstalled, ComponentPhysicalState state, String connectionSignature) {
            this.part = part;
            this.slot = slot;
            this.partInstalled = partInstalled;
            this.state = state;
            this.connectionSignature = connectionSignature;
        }
    }

    PcbWorkbenchRenderer(GeneratedBoardInstance instance,
            BoardModificationController modifications, PcbBoardLayout layout) {
        this(instance, modifications, layout, StandardPhysicalPartRenderProviders.createRegistry());
    }

    PcbWorkbenchRenderer(GeneratedBoardInstance instance,
            BoardModificationController modifications, PcbBoardLayout layout,
            PhysicalPartRenderRegistry renderRegistry) {
        if (instance == null || modifications == null || layout == null || renderRegistry == null)
            throw new IllegalArgumentException("Missing PCB workbench renderer dependency");
        // The candidate already owns real parts. Reject an unusable provider
        // before its workbench can be attached or its generation published.
        for (PhysicalPart<?> part : instance.getPhysicalBoardRuntime().getPhysicalParts())
            renderRegistry.requireRenderer(part.getPackage(), part);
        this.instance = instance;
        this.modifications = modifications;
        this.layout = layout;
        this.viewport = new PcbViewport(layout.getBoardOutline());
        this.viewport.setWorkbenchBounds(getWorkbenchBounds());
        this.projection = viewport.current();
        this.trayProjection = new PcbViewport.Transform(1, 0, 0, 0, false);
        this.renderRegistry = renderRegistry;
    }

    void setLooseProjectionTransitionListener(LooseProjectionTransitionListener listener) {
        looseProjectionListener = listener;
    }

    void draw(Graphics graphics, Rectangle area) {
        updateTransform(area);
        WorkbenchVisualTheme.wood(graphics, area, trayProjection);
        graphics.context.save();
        Rectangle boardArea = viewport.getArea();
        graphics.clipRect(boardArea.x, boardArea.y, boardArea.width, boardArea.height);
        if (viewportFixture == null) drawBoard(graphics); else viewportFixture.draw(graphics);
        drawTray(graphics);
        graphics.restore();
        if (viewport.isInspecting()) {
            graphics.setColor(WorkbenchVisualTheme.BODY); graphics.fillRect(0, 0, boardArea.width, 24);
            graphics.setColor(WorkbenchVisualTheme.SILK);
            graphics.setFont(new Font(WorkbenchVisualTheme.FONT, 0, 12));
            graphics.drawString("Inspection loupe " + Math.round(viewport.getMagnification() * 10) / 10.0 +
                "x - release Space to restore view", 12, 17);
        }
        if (CirSim.theSim != null && CirSim.theSim.isGeometryVerificationEnabled())
            publishDeveloperGeometry();
    }

    private void drawBoard(Graphics graphics) {
        Rectangle outline = screenRectForProvider(layout.getBoardOutline());
        graphics.setColor(WorkbenchVisualTheme.SHADOW);
        graphics.fillRect(outline.x + 3, outline.y + 5, outline.width, outline.height);
        graphics.setColor(WorkbenchVisualTheme.BOARD);
        graphics.fillRect(outline.x, outline.y, outline.width, outline.height);
        graphics.setColor(WorkbenchVisualTheme.BOARD_EDGE);
        graphics.setLineWidth(1);
        graphics.drawRect(outline.x, outline.y, outline.width, outline.height);
        graphics.fillRect(outline.x + 1, outline.y + outline.height - 3,
            Math.max(0, outline.width - 2), 2);
        graphics.setColor(WorkbenchVisualTheme.BOARD_BEVEL);
        graphics.drawLine(outline.x + 1, outline.y + 1, outline.x + outline.width - 1, outline.y + 1);
        graphics.drawLine(outline.x + 1, outline.y + 1, outline.x + 1, outline.y + outline.height - 2);
        int traceWidth = Math.max(1, scaleInt(PcbTraceRules.TRACE_WIDTH));
        PcbConductorGraph.Snapshot copper = instance.getCurrentConductorSnapshot();
        for (PcbConductorGraph.Surface surface : copper.getGraph().getSurfaces()) {
            if (surface.edgeId == null || !copper.hasEdge(surface.edgeId) || !surface.canProbe(viewingFace)) continue;
            PcbConductorGraph.Edge edge = copper.getGraph().getEdges().get(surface.edgeId);
            PcbConductorGraph.Junction a = copper.getGraph().getJunctions().get(edge.first);
            PcbConductorGraph.Junction b = copper.getGraph().getJunctions().get(edge.second);
            Point first = screenPointForProvider(new Point(a.x,a.y));
            Point second = screenPointForProvider(new Point(b.x,b.y));
            // Both material strokes stay centered on the exact current conductor.
            // The highlight is narrower; it adds no copper or probe surface.
            graphics.setColor(WorkbenchVisualTheme.COPPER);
            graphics.setLineWidth(traceWidth);
            graphics.drawLine(first.x,first.y,second.x,second.y);
            if (traceWidth >= 3) {
                graphics.setColor(WorkbenchVisualTheme.COPPER_LIGHT);
                graphics.setLineWidth(Math.max(1, traceWidth / 3));
                graphics.drawLine(first.x,first.y,second.x,second.y);
            }
        }
        graphics.setLineWidth(1);
        for (PcbPadPlacement pad : layout.getPads())
            drawPad(graphics, pad);
        for (PcbBoardHole hole : layout.getHoles()) drawHole(graphics,hole);
        for (PcbComponentPlacement component : layout.getComponents())
            drawComponent(graphics, component);
        drawSilkscreenLabels(graphics);
    }

    private void drawHole(Graphics graphics, PcbBoardHole hole) {
        Point point = screenPointForProvider(new Point(hole.x,hole.y));
        int land = Math.max(1,scaleInt(hole.landRadius));
        if (hole.kind != PcbBoardHole.Kind.NON_PLATED && hole.exposure == PcbCopperAccess.Exposure.EXPOSED) {
            WorkbenchVisualTheme.platedLand(graphics, point.x, point.y, land);
        }
        int drill = Math.max(1,scaleInt(hole.drillRadius));
        graphics.setColor(WorkbenchVisualTheme.DRILL);
        graphics.fillOval(point.x-drill,point.y-drill,drill*2,drill*2);
    }

    private void drawPad(Graphics graphics, PcbPadPlacement pad) {
        if (!PcbCopperAccess.hasCopper(pad,PcbCopperLayer.forFace(viewingFace))) return;
        Point point = getPadPoint(pad.getPadId());
        Rectangle bounds = screenRectForProvider(pad.getPadBounds());
        if (pad.getExposure() == PcbCopperAccess.Exposure.EXPOSED) {
            if (pad.getAttachment() == PcbTerminalAttachment.SURFACE_PAD) {
                graphics.setColor(WorkbenchVisualTheme.PLATING);
                graphics.fillRect(bounds.x,bounds.y,bounds.width,bounds.height);
                graphics.setColor(WorkbenchVisualTheme.PLATING_LIGHT);
                graphics.fillRect(bounds.x + 1, bounds.y + 1,
                    Math.max(0, bounds.width - 2), Math.max(0, bounds.height - 2));
            } else {
                int radius = Math.max(1,Math.min(bounds.width,bounds.height)/2);
                WorkbenchVisualTheme.platedLand(graphics, point.x, point.y, radius);
            }
        }
        if (pad.getAttachment() == PcbTerminalAttachment.PLATED_THROUGH_HOLE) {
            int drill=Math.max(1,scaleInt(DRILL_RADIUS));
            graphics.setColor(WorkbenchVisualTheme.DRILL);
            graphics.fillOval(point.x-drill,point.y-drill,drill*2,drill*2);
        }
    }

    private void drawComponent(Graphics graphics, PcbComponentPlacement placement) {
        BoardComponent component = instance.getBoard().getComponent(placement.getComponentId());
        if (component == null)
            return;
        PhysicalPart<?> part = instance.getPhysicalBoardRuntime().getInstalledPart(
            placement.getComponentId());
        PhysicalPartRenderContext context = new PhysicalPartRenderContext(this, placement, part,
            component.getPhysicalPackage(), 0, false);
        drawComponent(graphics, placement, component, part, context,
            placement.getComponentId().equals(selectedComponentId));
    }

    private PhysicalPartRenderGeometry drawComponent(Graphics graphics,
            PcbComponentPlacement placement, BoardComponent component, PhysicalPart<?> part,
            PhysicalPartRenderContext context, boolean selected) {
        if (!context.isInstalledPartMounted())
            return null;
        PhysicalPartRenderer renderer = requireRenderer(component.getPhysicalPackage(), part);
        PhysicalPartRenderGeometry geometry = renderer.getInstalledGeometry(context);
        if (placement.getMountingSide() != viewingFace) return geometry;
        if (selected)
            drawSelection(graphics, geometry);
        renderer.drawInstalled(graphics, context, geometry, selected);
        return geometry;
    }

    private void drawSelection(Graphics graphics, PhysicalPartRenderGeometry geometry) {
        Rectangle bounds = geometry.getSelectionBounds();
        graphics.setColor(WorkbenchVisualTheme.SELECTION);
        graphics.setLineWidth(2);
        graphics.drawRect(bounds.x - 4, bounds.y - 4, bounds.width + 8, bounds.height + 8);
        graphics.setLineWidth(1);
    }

    void drawDraggedPart(Graphics graphics,PhysicalPart<?> part,String componentId,int dx,int dy) {
        PcbComponentPlacement placement=componentId==null?null:layout.getComponent(componentId);
        int trayIndex=0;
        if(componentId==null) {
            Vector<PhysicalPart<?>> visible=getVisibleLoosePhysicalParts();
            trayIndex=visible.indexOf(part); if(trayIndex<0)return;
        }
        PhysicalPartRenderContext context=new PhysicalPartRenderContext(this,placement,part,part.getPackage(),trayIndex,componentId==null);
        PhysicalPartRenderer painter=requireRenderer(part.getPackage(),part);
        graphics.context.save(); graphics.context.translate(dx,dy); graphics.context.setGlobalAlpha(.78);
        try {
            if(componentId==null)painter.drawLoose(graphics,context,painter.getLooseGeometry(context),true);
            else painter.drawInstalled(graphics,context,painter.getInstalledGeometry(context),true);
        } finally { graphics.context.restore(); }
    }

    private void drawSilkscreenLabels(Graphics graphics) {
        if (viewingFace != PcbBoardSide.TOP) return; // Current labels declare the top face only.
        for (PcbSilkscreenLabel label : layout.getSilkscreenLabels()) {
            graphics.setFont(new Font(WorkbenchVisualTheme.FONT, label.isBold() ? Font.BOLD : 0,
                Math.max(10, scaleInt(label.getFontSize()))));
            graphics.setColor(label.getId().startsWith("net:") ?
                WorkbenchVisualTheme.SILK : WorkbenchVisualTheme.SILK_SECONDARY);
            String text = getPowerInputLabel(label.getTargetPadId(), label.getText());
            Rectangle bounds = screenRectForProvider(label.getBounds());
            graphics.drawString(text, bounds.x, screenY(label.getBaselineY()));
        }
    }

    private void drawTray(Graphics graphics) {
        Rectangle tray = trayArea;
        if (!tray.intersects(viewport.getArea())) return;
        WorkbenchVisualTheme.metalTray(graphics, tray, trayProjection.scale);
        Rectangle source = layout.getPartsTray();
        graphics.setFont(new Font(WorkbenchVisualTheme.FONT, Font.BOLD, Math.max(7, scaleWorkbenchIntForProvider(11))));
        graphics.setColor(WorkbenchVisualTheme.TEXT);
        graphics.drawString("PARTS TRAY", screenWorkbenchXForProvider(source.x + 18),
            screenWorkbenchYForProvider(source.y + 28));
        Vector<PhysicalPart<?>> parts = getVisibleLoosePhysicalParts();
        for (int index = 0; index < parts.size(); index++) {
            PhysicalPart<?> part = parts.get(index);
            PhysicalPartRenderContext context = new PhysicalPartRenderContext(this, null, part,
                part.getPackage(), index, true);
            PhysicalPartRenderer renderer = requireRenderer(part.getPackage(), part);
            PhysicalPartRenderGeometry geometry = renderer.getLooseGeometry(context);
            if (part.getId().equals(selectedPartId)) drawSelection(graphics, geometry);
            renderer.drawLoose(graphics, context, geometry, part.getId().equals(selectedPartId));
        }
        if (getTrayPageCount() > 1) {
            graphics.setFont(new Font(WorkbenchVisualTheme.FONT, 0, Math.max(7, scaleWorkbenchIntForProvider(11))));
            graphics.setColor(WorkbenchVisualTheme.MUTED_TEXT);
            graphics.drawString("Page " + (trayPage + 1) + " of " + getTrayPageCount(),
                screenWorkbenchXForProvider(source.x + 18), screenWorkbenchYForProvider(source.y + source.height - 18));
        }
    }

    ProbeTarget findProbeTarget(CirSim sim, int screenX, int screenY) {
        ambiguousTarget = false;
        if (viewportFixture != null) return viewport.contains(screenX, screenY) ? viewportFixture.target(screenX, screenY) : null;
        if (!viewport.contains(screenX, screenY) && !trayArea.contains(screenX, screenY)) return null;
        if (viewport.contains(screenX, screenY)) {
        for (PcbBoardHole hole : layout.getHoles())
            if (PcbCopperAccess.blocksProbeAt(hole,screenRectForProvider(hole.getBounds()),screenX,screenY)) return null;
        // Board pads own the board-side probe envelope.  Resolve them before
        // any component-side or tray target so an overlap at a pad can never
        // be stolen by an installed-part renderer.
        String candidatePad = null;
        for (PcbPadPlacement pad : layout.getPads()) {
            if (!canProbePad(pad.getPadId())) continue;
            Rectangle probeBounds = screenRectForProvider(pad.getProbeBounds());
            if (probeBounds.contains(screenX, screenY) && !isOccluded(screenX, screenY,
                    instance.getBoard().getPad(pad.getPadId()).getComponentId())) {
                if (candidatePad != null) { ambiguousTarget = true; return null; }
                candidatePad = pad.getPadId();
            }
        }
        if (candidatePad != null) return new BoardPadProbeTarget(sim, instance, candidatePad, this);
        }
        if (trayArea.contains(screenX, screenY)) {
        Vector<PhysicalPart<?>> looseParts = getVisibleLoosePhysicalParts();
        for (int index = 0; index < looseParts.size(); index++) {
            PhysicalPart<?> part = looseParts.get(index);
            PhysicalPartRenderContext context = new PhysicalPartRenderContext(this, null, part,
                part.getPackage(), index, true);
            PhysicalPartRenderer renderer = requireRenderer(part.getPackage(), part);
            PhysicalPartRenderGeometry geometry = renderer.getLooseGeometry(context);
            for (PhysicalPartRenderTerminal terminal : geometry.getTerminals()) {
                if (terminal.containsProbe(screenX, screenY))
                    return renderer.createLooseProbeTarget(sim, context,
                        terminal.getTerminalIndex());
            }
        }
        return null;
        }
        for (PcbComponentPlacement placement : layout.getComponents()) {
            BoardComponent component = instance.getBoard().getComponent(placement.getComponentId());
            if (component == null)
                continue;
            Vector<GeneratedComponentConnectionBinding> bindings = instance.getConnectionBindings()
                .getForComponentOrEmpty(placement.getComponentId());
            if (bindings.isEmpty() || modifications.getComponentState(placement.getComponentId()) ==
                    ComponentPhysicalState.INSTALLED)
                continue;
            PhysicalPart<?> part = instance.getPhysicalBoardRuntime().getInstalledPart(
                placement.getComponentId());
            if (part == null)
                continue;
            PhysicalPartRenderContext context = new PhysicalPartRenderContext(this, placement, part,
                component.getPhysicalPackage(), 0, false);
            ProbeTarget target = findInstalledProbeTarget(sim, placement, component, part,
                context, screenX, screenY);
            if (target != null)
                return target;
        }
        return findCopperProbeTarget(sim,screenX,screenY);
    }

    private ProbeTarget findCopperProbeTarget(CirSim sim,int x,int y) {
        if(!viewport.contains(x,y) || !instance.isDeveloperOnlyFaultRoute() || isOccluded(x,y,null)) return null;
        for(PcbBoardHole hole:layout.getHoles()) {
            Rectangle drill=screenRectForProvider(new Rectangle(hole.x-hole.drillRadius,hole.y-hole.drillRadius,
                2*hole.drillRadius,2*hole.drillRadius));
            if(drill.contains(x,y)) return null;
        }
        PcbConductorGraph.Snapshot copper=instance.getCurrentConductorSnapshot();
        PcbConductorGraph.Surface selected=null;
        for(PcbConductorGraph.Surface surface:copper.getGraph().getSurfaces()) {
            if(!canProbeCopper(surface.id) || !screenRectForProvider(surface.getBounds()).contains(x,y)) continue;
            if(selected!=null && !copper.connected(selected.junctionId,surface.junctionId)) {
                ambiguousTarget=true; return null;
            }
            if(selected==null || surface.edgeId==null && selected.edgeId!=null) selected=surface;
        }
        if(selected==null) return null;
        ProbeTarget target=new BoardCopperProbeTarget(sim,instance,selected.id,this);
        return target.isValid()?target:null;
    }
    public boolean canProbeCopper(String id) {
        if(!instance.isDeveloperOnlyFaultRoute()) return false;
        PcbConductorGraph.Snapshot copper=instance.getCurrentConductorSnapshot();
        PcbConductorGraph.Surface surface=PcbCopperProbeAccess.surface(copper,id);
        if(!PcbCopperProbeAccess.available(copper,surface,viewingFace)) return false;
        Point point=screenPointForProvider(PcbCopperProbeAccess.marker(surface));
        return !isOccluded(point.x,point.y,null);
    }
    public Point getCopperPoint(String id) {
        if(!canProbeCopper(id)) return null;
        return screenPointForProvider(PcbCopperProbeAccess.marker(
            PcbCopperProbeAccess.surface(instance.getCurrentConductorSnapshot(),id)));
    }

    String findComponentId(int screenX, int screenY) {
        if (viewportFixture != null) return null;
        if (!viewport.contains(screenX, screenY)) return null;
        for (PcbComponentPlacement placement : layout.getComponents()) {
            if (isReplaceableSlotEmpty(placement.getComponentId()))
                continue;
            BoardComponent component = instance.getBoard().getComponent(placement.getComponentId());
            if (component == null)
                continue;
            PhysicalPart<?> part = instance.getPhysicalBoardRuntime().getInstalledPart(
                placement.getComponentId());
            if (part == null || !part.isInstalled())
                continue;
            PhysicalPartRenderContext context = new PhysicalPartRenderContext(this, placement, part,
                component.getPhysicalPackage(), 0, false);
            if (findComponentIdForPlacement(placement, component, part, context, screenX, screenY))
                return placement.getComponentId();
        }
        return null;
    }

    String findPartId(int screenX, int screenY) {
        if (!trayArea.contains(screenX, screenY)) return null;
        Vector<PhysicalPart<?>> parts = getVisibleLoosePhysicalParts();
        for (int index = 0; index < parts.size(); index++) {
            PhysicalPart<?> part = parts.get(index);
            PhysicalPartRenderContext context = new PhysicalPartRenderContext(this, null, part,
                part.getPackage(), index, true);
            PhysicalPartRenderer renderer = requireRenderer(part.getPackage(), part);
            if (renderer.getLooseGeometry(context).contains(screenX, screenY))
                return part.getId();
        }
        return null;
    }

    public Point getPadPoint(String padId) {
        PcbPadPlacement pad = layout.getPad(padId);
        return pad == null ? null : screenPointForProvider(new Point(pad.getX(),pad.getY()));
    }

    public Point getComponentLeadPoint(String componentId, String padId) {
        PcbComponentPlacement placement = layout.getComponent(componentId);
        BoardComponent component = instance.getBoard().getComponent(componentId);
        if (placement == null || component == null || placement.getMountingSide() != viewingFace)
            return null;
        PhysicalPart<?> part = instance.getPhysicalBoardRuntime().getInstalledPart(componentId);
        if (part == null || !part.isInstalled())
            return null;
        PhysicalPartRenderContext context = new PhysicalPartRenderContext(this, placement, part,
            component.getPhysicalPackage(), 0, false);
        if (!context.isInstalledPartMounted())
            return null;
        PhysicalPartRenderer renderer = requireRenderer(component.getPhysicalPackage(), part);
        PhysicalPartRenderGeometry geometry = renderer.getInstalledGeometry(context);
        for (PhysicalPartRenderTerminal terminal : geometry.getTerminals()) {
            if (!padId.equals(terminal.getBoardPadId()))
                continue;
            int terminalIndex = terminal.getTerminalIndex();
            if (!context.isDeveloperCanary() && context.isLeadConnected(terminalIndex))
                return null;
            return terminal.getComponentLeadPoint();
        }
        return null;
    }

    public boolean hasPad(String padId) { return layout.getPad(padId) != null; }

    public Point getLooseTerminalPoint(String partId, int terminal) {
        Vector<PhysicalPart<?>> parts = getVisibleLoosePhysicalParts();
        for (int index = 0; index < parts.size(); index++) {
            PhysicalPart<?> part = parts.get(index);
            if (!part.getId().equals(partId))
                continue;
            PhysicalPartRenderContext context = new PhysicalPartRenderContext(this, null, part,
                part.getPackage(), index, true);
            PhysicalPartRenderer renderer = requireRenderer(part.getPackage(), part);
            return renderer.getLooseGeometry(context).getTerminalPoint(terminal);
        }
        return null;
    }

    int getTrayPage() { return trayPage; }
    int getPartsPerTrayPage() { return PARTS_PER_TRAY_PAGE; }
    int getTrayPageCount() {
        int count = getLoosePartCount();
        return Math.max(1, (count + PARTS_PER_TRAY_PAGE - 1) / PARTS_PER_TRAY_PAGE);
    }
    void setTrayPage(int page) {
        Vector<PhysicalPart<?>> all = getAllLoosePhysicalParts();
        trayPage = clampTrayPageValue(page, all.size());
        synchronizeLooseProjection(all);
        clearSelectedPartIfHidden(all);
    }
    void clampTrayPage() {
        Vector<PhysicalPart<?>> all = getAllLoosePhysicalParts();
        trayPage = clampTrayPageValue(trayPage, all.size());
        synchronizeLooseProjection(all);
        clearSelectedPartIfHidden(all);
    }

    Vector<PhysicalPart<?>> getVisibleLoosePhysicalParts() {
        Vector<PhysicalPart<?>> all = getAllLoosePhysicalParts();
        trayPage = clampTrayPageValue(trayPage, all.size());
        Vector<PhysicalPart<?>> result = getVisibleLoosePhysicalParts(all);
        synchronizeLooseProjection(all, result);
        clearSelectedPartIfHidden(all);
        return result;
    }

    public Object captureLooseProjectionToken() { return looseProjectionToken; }

    public boolean isLooseProjectionTokenCurrent(Object token) {
        return token != null && token == looseProjectionToken;
    }

    /** Pure target-lifecycle predicate; it never clamps, clears, or advances projection state. */
    public boolean isLoosePartVisibleOnCurrentPage(String partId) {
        if (partId == null || trayPage < 0)
            return false;
        Vector<PhysicalPart<?>> all = getAllLoosePhysicalParts();
        int start = trayPage * PARTS_PER_TRAY_PAGE;
        if (start < 0 || start >= all.size())
            return false;
        int end = Math.min(all.size(), start + PARTS_PER_TRAY_PAGE);
        for (int index = start; index < end; index++)
            if (partId.equals(all.get(index).getId()))
                return true;
        return false;
    }

    private Vector<PhysicalPart<?>> getVisibleLoosePhysicalParts(Vector<PhysicalPart<?>> all) {
        Vector<PhysicalPart<?>> result = new Vector<PhysicalPart<?>>();
        int start = trayPage * PARTS_PER_TRAY_PAGE;
        for (int index = start; index < all.size() && index < start + PARTS_PER_TRAY_PAGE; index++)
            result.add(all.get(index));
        return result;
    }

    String getPowerInputLabelForDeveloperVerification() { return getPowerInputLabel(); }

    String getRenderedSilkscreenLabelTextForDeveloperVerification(String labelId) {
        PcbSilkscreenLabel label = layout.getSilkscreenLabel(labelId);
        return label == null ? null : getPowerInputLabel(label.getTargetPadId(), label.getText());
    }
    void setSelectedComponentId(String componentId) { selectedComponentId = componentId; }
    String getSelectedComponentId() {
        if (selectedComponentId != null)
            observeInstalledProjection(selectedComponentId);
        if (selectedComponentId != null && !isInstalledComponentMounted(selectedComponentId))
            selectedComponentId = null;
        return selectedComponentId;
    }
    void setSelectedPartId(String partId) {
        selectedPartId = partId;
        clearSelectedPartIfHidden(getAllLoosePhysicalParts());
    }
    String getSelectedPartId() {
        clearSelectedPartIfHidden(getAllLoosePhysicalParts());
        return selectedPartId;
    }

    PhysicalPartRenderRegistry getRenderRegistryForDeveloperVerification() { return renderRegistry; }

    PhysicalPartRenderGeometry getInstalledGeometryForDeveloperVerification(String componentId) {
        PcbComponentPlacement placement = layout.getComponent(componentId);
        BoardComponent component = instance.getBoard().getComponent(componentId);
        if (placement == null || component == null)
            return null;
        PhysicalPart<?> part = instance.getPhysicalBoardRuntime().getInstalledPart(componentId);
        if (part == null) return null;
        PhysicalPartRenderContext context = new PhysicalPartRenderContext(this, placement, part,
            component.getPhysicalPackage(), 0, false);
        return requireRenderer(component.getPhysicalPackage(), part).getInstalledGeometry(context);
    }

    boolean drawInstalledForDeveloperVerification(CirSim sim, Graphics graphics,
            String componentId) {
        if (sim == null || graphics == null || sim.circuitArea == null)
            throw new IllegalArgumentException("Incomplete installed render verification request");
        PcbComponentPlacement placement = layout.getComponent(componentId);
        BoardComponent component = instance.getBoard().getComponent(componentId);
        if (placement == null || component == null)
            return false;
        PhysicalPart<?> part = instance.getPhysicalBoardRuntime().getInstalledPart(componentId);
        updateTransform(sim.circuitArea);
        PhysicalPartRenderContext context = new PhysicalPartRenderContext(this, placement, part,
            component.getPhysicalPackage(), 0, false);
        drawComponent(graphics, placement, component, part, context, false);
        return context.wasBodyDrawn();
    }

    PhysicalPartRenderCanaryResult renderProviderCanaryForDeveloperVerification(CirSim sim,
            Graphics graphics, TroubleshootBoard canaryBoard, PhysicalPart<?> part,
            PcbComponentPlacement placement, HashMap<String, Point> padPoints) {
        if (sim == null || graphics == null || canaryBoard == null || part == null ||
                placement == null || padPoints == null || sim.circuitArea == null)
            throw new IllegalArgumentException("Incomplete physical render canary request");
        BoardComponent component = canaryBoard.getComponent(placement.getComponentId());
        if (component == null || !component.getPhysicalPackage().isEquivalentTo(part.getPackage()))
            throw new IllegalArgumentException("Render canary board/part mismatch");
        updateTransform(sim.circuitArea);
        PhysicalPartRenderContext context = new PhysicalPartRenderContext(this, placement, part,
            component.getPhysicalPackage(), 0, false, canaryBoard, padPoints);
        PhysicalPartRenderGeometry geometry = drawComponent(graphics, placement, component, part,
            context, true);
        Rectangle selection = geometry.getSelectionBounds();
        String hit = findComponentIdForPlacement(placement, component, part, context,
            selection.x + selection.width / 2, selection.y + selection.height / 2) ?
            placement.getComponentId() : null;
        Vector<ProbeTarget> probeTargets = new Vector<ProbeTarget>();
        for (PhysicalPartRenderTerminal terminal : geometry.getTerminals()) {
            Point point = terminal.getPoint();
            probeTargets.add(findInstalledProbeTarget(sim, placement, component, part, context,
                point.x, point.y));
        }
        return new PhysicalPartRenderCanaryResult(geometry, hit, probeTargets,
            context.wasBodyDrawn());
    }

    private Vector<PhysicalPart<?>> getAllLoosePhysicalParts() {
        Vector<PhysicalPart<?>> result = new Vector<PhysicalPart<?>>();
        for (WorkbenchPartsProvider provider : instance.getPhysicalBoardRuntime()
                .getWorkbenchPartsProviders())
            result.addAll(provider.getLooseParts());
        return result;
    }

    private int clampTrayPageValue(int page) {
        return clampTrayPageValue(page, getLoosePartCount());
    }

    private int clampTrayPageValue(int page, int loosePartCount) {
        int pageCount = Math.max(1, (loosePartCount + PARTS_PER_TRAY_PAGE - 1) /
            PARTS_PER_TRAY_PAGE);
        return Math.max(0, Math.min(page, pageCount - 1));
    }

    private void synchronizeLooseProjection(Vector<PhysicalPart<?>> all) {
        synchronizeLooseProjection(all, getVisibleLoosePhysicalParts(all));
    }

    private void synchronizeLooseProjection(Vector<PhysicalPart<?>> all,
            Vector<PhysicalPart<?>> visible) {
        if (all == null || visible == null)
            return;
        boolean changed = !looseProjectionInitialized ||
            observedLooseProjectionPage != trayPage ||
            !sameLooseProjection(observedLooseProjection, visible);
        if (!changed)
            return;
        observedLooseProjectionPage = trayPage;
        observedLooseProjection = new Vector<PhysicalPart<?>>(visible);
        if (!looseProjectionInitialized) {
            looseProjectionInitialized = true;
            return;
        }
        looseProjectionToken = new Object();
        if (looseProjectionListener != null)
            looseProjectionListener.onLooseProjectionTransition();
    }

    private boolean sameLooseProjection(Vector<PhysicalPart<?>> first,
            Vector<PhysicalPart<?>> second) {
        if (first == null || second == null || first.size() != second.size())
            return false;
        for (int index = 0; index < first.size(); index++)
            if (first.get(index) != second.get(index))
                return false;
        return true;
    }

    private PhysicalPartRenderer requireRenderer(PhysicalPackage physicalPackage,
            PhysicalPart<?> part) {
        return renderRegistry.requireRenderer(physicalPackage, part);
    }

    private boolean findComponentIdForPlacement(PcbComponentPlacement placement,
            BoardComponent component, PhysicalPart<?> part, PhysicalPartRenderContext context,
            int screenX, int screenY) {
        return placement.getMountingSide() == viewingFace && requireRenderer(component.getPhysicalPackage(), part)
            .getInstalledGeometry(context).contains(screenX, screenY);
    }

    private ProbeTarget findInstalledProbeTarget(CirSim sim, PcbComponentPlacement placement,
            BoardComponent component, PhysicalPart<?> part, PhysicalPartRenderContext context,
            int screenX, int screenY) {
        if (part == null || !canProbeInstalledContext(context))
            return null;
        if (!context.isDeveloperCanary() && !context.isInstalledPartMounted())
            return null;
        PhysicalPartRenderer renderer = requireRenderer(component.getPhysicalPackage(), part);
        PhysicalPartRenderGeometry geometry = renderer.getInstalledGeometry(context);
        for (PhysicalPartRenderTerminal terminal : geometry.getTerminals()) {
            int terminalIndex = terminal.getTerminalIndex();
            if (!context.isDeveloperCanary() && context.isLeadConnected(terminalIndex))
                continue;
            Rectangle boardPadProbeBounds = terminal.getBoardPadProbeBounds();
            boolean hit = context.isDeveloperCanary() ?
                terminal.containsProbe(screenX, screenY) :
                terminal.containsComponentProbe(screenX, screenY, boardPadProbeBounds);
            if (hit)
                return renderer.createInstalledProbeTarget(sim, context,
                    terminalIndex);
        }
        return null;
    }

    private String getPowerInputLabel() {
        Vector<String> powerInputIds = instance.getBoard().getPowerInputIds();
        if (powerInputIds.size() != 1)
            return "VIN";
        return getPowerInputLabel(powerInputIds.get(0));
    }

    private String getPowerInputLabel(String powerInputId) {
        PowerInputNameplate nameplate = instance.getPhysicalSpecifications()
            .getPowerInputNameplate(powerInputId);
        return nameplate == null ? "VIN" : nameplate.getDisplayLabel();
    }

    private String getPowerInputLabel(String padId, String fallback) {
        if (padId == null)
            return fallback;
        for (String powerInputId : instance.getBoard().getPowerInputIds()) {
            ExternalBoardPowerInput input = instance.getBoard().getPowerInput(powerInputId);
            if (input.getPositivePadId().equals(padId))
                return getPowerInputLabel(input.getId());
            if (input.getReturnPadId().equals(padId))
                return "GND";
        }
        return fallback;
    }

    private boolean isReplaceableSlotEmpty(String componentId) {
        PhysicalBoardRuntime runtime = instance.getPhysicalBoardRuntime();
        PhysicalBoardSlot slot = runtime.getSlot(componentId);
        return runtime.getMutationProvider(componentId) != null && slot != null &&
            !slot.isOccupied();
    }

    private int getLoosePartCount() { return getAllLoosePhysicalParts().size(); }
    private Point getLoosePartMarkerPoint(String partId) { return getLooseTerminalPoint(partId, 0); }

    private void clearSelectedPartIfHidden(Vector<PhysicalPart<?>> all) {
        if (selectedPartId == null || all == null)
            return;
        int start = trayPage * PARTS_PER_TRAY_PAGE;
        int end = Math.min(all.size(), start + PARTS_PER_TRAY_PAGE);
        for (int index = start; index < end; index++)
            if (selectedPartId.equals(all.get(index).getId()))
                return;
        selectedPartId = null;
    }

    private void publishDeveloperGeometry() {
        StringBuilder json = new StringBuilder("{\"points\":{");
        boolean first = true;
        for (PcbComponentPlacement component : layout.getComponents()) {
            Rectangle bounds = screenRectForProvider(component);
            first = appendDeveloperPoint(json, first, "component:" + component.getComponentId(),
                bounds.x + bounds.width / 2, bounds.y + bounds.height / 2);
        }
        for (PcbPadPlacement pad : layout.getPads()) {
            Point point = getPadPoint(pad.getPadId());
            first = appendDeveloperPoint(json, first, "pad:" + pad.getPadId(), point.x, point.y);
        }
        Vector<PhysicalPart<?>> visibleParts = getVisibleLoosePhysicalParts();
        for (int index = 0; index < visibleParts.size(); index++) {
            PhysicalPart<?> part = visibleParts.get(index);
            PhysicalPartRenderContext context = new PhysicalPartRenderContext(this, null, part,
                part.getPackage(), index, true);
            PhysicalPartRenderGeometry geometry = requireRenderer(part.getPackage(), part)
                .getLooseGeometry(context);
            for (PhysicalPartRenderTerminal terminal : geometry.getTerminals()) {
                Point point = terminal.getPoint();
                first = appendDeveloperPoint(json, first, "loose:" + part.getId() + ":" +
                    terminal.getTerminalIndex(), point.x, point.y);
            }
        }
        json.append("}}");
        setDeveloperGeometry(json.toString());
    }

    private boolean appendDeveloperPoint(StringBuilder json, boolean first, String key, int x, int y) {
        if (!first)
            json.append(',');
        json.append('"').append(key).append("\":{\"x\":").append(x).append(",\"y\":")
            .append(y).append('}');
        return false;
    }

    private static native void setDeveloperGeometry(String json) /*-{
        $wnd.__tsjPcbGeometry = JSON.parse(json);
    }-*/;

    private PhysicalPart<?> getInstalledPhysicalPart(String componentId) {
        return instance.getPhysicalBoardRuntime().getInstalledPart(componentId);
    }

    PhysicalPart<?> getInstalledPhysicalPartForDeveloperVerification(String componentId) {
        return getInstalledPhysicalPart(componentId);
    }

    ProbeTarget createInstalledProbeTargetForProvider(CirSim sim,
            PhysicalPartRenderContext context, int terminal) {
        if (!canProbeInstalledContext(context)) return null;
        if (context.isDeveloperCanary())
            return new PhysicalPartRenderCanaryProbeTarget(sim, context, terminal);
        if (!context.isInstalledPartMounted() || context.isLeadConnected(terminal))
            return null;
        String padId = context.getBoardPadId(terminal);
        if (padId == null)
            return null;
        GeneratedComponentConnectionBinding binding = instance.getConnectionBindings()
            .get(context.getComponentId(), padId);
        if (binding == null)
            return null;
        PhysicalPart<?> part = context.getPart();
        return new ComponentLeadProbeTarget(sim, instance, context.getComponentId(), padId, this,
            part == null ? null : part.getId(), binding.getComponentEndpoint(),
            captureInstalledTargetIdentity(context.getComponentId(), padId));
    }

    GeneratedBoardInstance getInstanceForProvider() { return instance; }
    BoardModificationController getModificationsForProvider() { return modifications; }
    Rectangle getPartsTrayForProvider() { return layout.getPartsTray(); }
    PcbBoardLayout getLayoutForProvider() { return layout; }
    String getSelectedPartForProvider() { return selectedPartId; }
    Point getProviderTerminalPoint(PhysicalPartRenderContext context, int terminal) {
        return context.getProviderTerminalPoint(terminal);
    }
    int screenXForProvider(int value) { return screenX(value); }
    int screenWorkbenchXForProvider(int value) { return (int)Math.round(trayProjection.screenX(value)); }
    int screenWorkbenchYForProvider(int value) { return (int)Math.round(trayProjection.screenY(value)); }
    Point screenWorkbenchPointForProvider(Point value) {
        return trayProjection.project(value);
    }
    Rectangle screenWorkbenchRectForProvider(Rectangle value) { return trayProjection.project(value); }
    int screenYForProvider(int value) { return screenY(value); }
    int scaleIntForProvider(int value) { return scaleInt(value); }
    int scaleWorkbenchIntForProvider(int value) { return (int)Math.round(value * trayProjection.scale); }
    int scaleLengthForProvider(double value) {
        if (Double.isNaN(value) || Double.isInfinite(value) || value < 0.0)
            throw new IllegalArgumentException("Invalid provider length: " + value);
        return (int) Math.round(value * trayProjection.scale);
    }
    Point screenPointForProvider(Point value) {
        return projection.project(value);
    }
    Rectangle screenRectForProvider(Rectangle value) { return projection.project(value); }
    Rectangle getPadProbeBoundsForDeveloperVerification(String padId) {
        return getPadProbeBounds(padId);
    }

    public Object captureInstalledTargetIdentity(String componentId, String padId) {
        InstalledProjectionObservation observation = observeInstalledProjection(componentId);
        return observation == null ? null : observation.token;
    }

    public boolean isInstalledTargetIdentityCurrent(String componentId, String padId, Object token) {
        InstalledProjectionObservation observation = observeInstalledProjection(componentId);
        return token != null && observation != null && token == observation.token;
    }

    InstalledProjectionObservation observeInstalledProjection(String componentId) {
        if (componentId == null)
            return null;
        PhysicalBoardRuntime runtime = instance.getPhysicalBoardRuntime();
        PhysicalPart<?> part = runtime.getInstalledPart(componentId);
        PhysicalBoardSlot slot = runtime.getSlot(componentId);
        boolean partInstalled = part != null && part.isInstalled() && slot != null &&
            slot.getInstalledPart() == part && part.getBoardSlot() == slot;
        StringBuilder connectionSignature = new StringBuilder();
        Vector<GeneratedComponentConnectionBinding> bindings = instance.getConnectionBindings()
            .getForComponentOrEmpty(componentId);
        ComponentPhysicalState state = bindings.isEmpty() ? ComponentPhysicalState.INSTALLED :
            modifications.getComponentState(componentId);
        for (GeneratedComponentConnectionBinding binding : bindings)
            connectionSignature.append(binding.getPadId()).append('=')
                .append(modifications.isLeadConnected(componentId, binding.getPadId()))
                .append(';');
        InstalledProjectionObservation prior = installedObservations.get(componentId);
        if (componentId.equals(selectedComponentId) &&
                (!partInstalled || (prior != null && prior.part != part)))
            selectedComponentId = null;
        if (prior == null || prior.part != part || prior.slot != slot ||
                prior.partInstalled != partInstalled ||
                prior.state != state || !prior.connectionSignature.equals(connectionSignature.toString()))
            installedObservations.put(componentId, new InstalledProjectionObservation(part, slot,
                partInstalled, state, connectionSignature.toString()));
        return installedObservations.get(componentId);
    }

    private boolean isInstalledComponentMounted(String componentId) {
        if (componentId == null)
            return false;
        PhysicalBoardRuntime runtime = instance.getPhysicalBoardRuntime();
        PhysicalPart<?> part = runtime.getInstalledPart(componentId);
        PhysicalBoardSlot slot = runtime.getSlot(componentId);
        return part != null && part.isInstalled() && slot != null &&
            slot.getInstalledPart() == part && part.getBoardSlot() == slot;
    }
    Rectangle screenRectForProvider(PcbComponentPlacement value) {
        return screenRectForProvider(new Rectangle(value.getX(),value.getY(),value.getWidth(),value.getHeight()));
    }

    Point getProviderCanaryPadPoint(String padId, HashMap<String, Point> padPoints) {
        Point logical = padPoints.get(padId);
        return screenPointForProvider(logical);
    }

    private void updateTransform(Rectangle area) {
        canvasArea = area;
        viewport.resize(new Rectangle(0, 0, Math.max(1, area.width), Math.max(1, area.height)));
        if (!workbenchViewInitialized && viewportFixture == null) {
            workbenchViewInitialized = true;
            fitWorkbench();
        }
        updateProjection();
    }

    // Furniture uses unmirrored bench coordinates, not the PCB face transform.
    Rectangle getMeterHome() {
        Rectangle board = layout.getBoardOutline();
        return new Rectangle(board.x - 284, board.y + Math.max(0, (board.height - 454) / 2), 252, 454);
    }

    private Rectangle getWorkbenchBounds() {
        return layout.getBoardOutline().union(layout.getPartsTray()).union(getMeterHome());
    }

    void fitWorkbench() {
        viewport.fitWorkbench(getWorkbenchBounds());
        updateProjection();
    }

    private int screenX(int x) { return (int)Math.round(projection.screenX(x)); }
    private int screenY(int y) { return (int)Math.round(projection.screenY(y)); }
    private int scaleInt(int value) { return (int)Math.round(value * projection.scale); }
    PcbViewport getViewport() { return viewport; }
    boolean hasViewportFixture() { return viewportFixture != null; }
    void showViewportFixture(CirSim sim, int count) {
        viewport.dismiss();
        viewportFixture = new U01ViewportFixture(sim, this, count);
        viewport = new PcbViewport(viewportFixture.outline);
        viewport.setFace(viewingFace); updateTransform(canvasArea);
    }
    void updateProjection() {
        projection = viewport.current();
        // The tray shares the bench camera. Only the PCB flips to its other face.
        trayProjection = new PcbViewport.Transform(projection.scale, projection.x,
            projection.y, 0, false);
        trayArea = trayProjection.project(layout.getPartsTray());
    }
    boolean wasTargetAmbiguous() { return ambiguousTarget; }
    public boolean isBoardPointVisible(Point point) { return point != null && viewport.contains(point.x, point.y); }
    private boolean isOccluded(int sx, int sy, String padOwner) {
        for (PcbComponentPlacement placement : layout.getComponents())
            if (!placement.getComponentId().equals(padOwner) && placement.getMountingSide() == viewingFace && isInstalledComponentMounted(placement.getComponentId()) &&
                    screenRectForProvider(placement.getPhysicalGeometry().placedAt(placement.getPose()).getBodyBounds()).contains(sx, sy))
                return true;
        return false;
    }
    /** Admission follows the supported fit/side path; overview scale is not a probeability requirement. */
    boolean canInspectPad(CirSim sim, String id) {
        if (sim.circuitArea != null) updateTransform(sim.circuitArea);
        PcbViewport.State saved = viewport.capture(); PcbBoardSide savedFace = viewingFace;
        try {
            PcbPadPlacement pad = layout.getPad(id);
            if (pad == null) return false;
            if (!canProbePad(id)) setViewingFace(viewingFace.opposite());
            viewport.fit(new Rectangle(pad.getX()-80, pad.getY()-80, 160, 160)); updateProjection();
            Point point = getPadPoint(id); ProbeTarget target = findProbeTarget(sim, point.x, point.y);
            return target instanceof BoardPadProbeTarget && id.equals(((BoardPadProbeTarget)target).getPadId()) &&
                target.isValid() && target.getMeasurementEndpoint() == instance.getSimulationBindings().getEndpoint(id);
        } finally { viewingFace = savedFace; viewport.restore(saved); updateProjection(); }
    }

    private Rectangle getPadProbeBounds(String padId) {
        PcbPadPlacement pad = layout.getPad(padId);
        return pad == null || !canProbePad(padId) ? null : screenRectForProvider(pad.getProbeBounds());
    }
}
