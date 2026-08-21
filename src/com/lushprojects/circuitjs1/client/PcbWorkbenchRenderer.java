package com.lushprojects.circuitjs1.client;

import java.util.HashMap;
import java.util.Vector;

/** Common PCB canvas, transform, selection, and provider orchestration. */
class PcbWorkbenchRenderer {
    private static final int DRILL_RADIUS = 5;
    private static final int PARTS_PER_TRAY_PAGE = 3;

    private final GeneratedBoardInstance instance;
    private final BoardModificationController modifications;
    private final PcbBoardLayout layout;
    private final PhysicalPartRenderRegistry renderRegistry;
    private Rectangle canvasArea = new Rectangle(0, 0, 1, 1);
    private double scale = 1;
    private int offsetX;
    private int offsetY;
    private String selectedComponentId;
    private String selectedPartId;
    private int trayPage;
    private final HashMap<String, InstalledProjectionObservation> installedObservations =
        new HashMap<String, InstalledProjectionObservation>();

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
        this.instance = instance;
        this.modifications = modifications;
        this.layout = layout;
        this.renderRegistry = renderRegistry;
    }

    void draw(Graphics graphics, Rectangle area) {
        updateTransform(area);
        graphics.setColor("#e8ece9");
        graphics.fillRect(0, 0, area.width, area.height);
        graphics.setColor("#d3d9d4");
        for (int x = 0; x < area.width; x += 32)
            graphics.drawLine(x, 0, x, area.height);
        for (int y = 0; y < area.height; y += 32)
            graphics.drawLine(0, y, area.width, y);
        drawBoard(graphics);
        drawTray(graphics);
        if (CirSim.theSim != null && CirSim.theSim.isGeometryVerificationEnabled())
            publishDeveloperGeometry();
    }

    private void drawBoard(Graphics graphics) {
        Rectangle outline = screenRect(layout.getBoardOutline());
        graphics.setColor("#0d5b3d");
        graphics.fillRect(outline.x, outline.y, outline.width, outline.height);
        graphics.setColor("#b5dfc8");
        graphics.setLineWidth(2);
        graphics.drawRect(outline.x, outline.y, outline.width, outline.height);
        graphics.setColor("#b56c2f");
        graphics.setLineWidth(Math.max(5, scaleInt(PcbTraceRules.TRACE_WIDTH)));
        for (PcbTraceGeometry trace : layout.getTraces()) {
            int[] sourceX = trace.getXPoints();
            int[] sourceY = trace.getYPoints();
            for (int index = 1; index < sourceX.length; index++)
                graphics.drawLine(screenX(sourceX[index - 1]), screenY(sourceY[index - 1]),
                    screenX(sourceX[index]), screenY(sourceY[index]));
        }
        graphics.setLineWidth(1);
        for (PcbPadPlacement pad : layout.getPads())
            drawPad(graphics, pad);
        for (PcbComponentPlacement component : layout.getComponents())
            drawComponent(graphics, component);
        drawSilkscreenLabels(graphics);
    }

    private void drawPad(Graphics graphics, PcbPadPlacement pad) {
        Point point = getPadPoint(pad.getPadId());
        Rectangle padBounds = screenRect(pad.getPadBounds());
        int radius = Math.max(8, Math.min(padBounds.width, padBounds.height) / 2);
        int drill = Math.max(4, scaleInt(DRILL_RADIUS));
        graphics.setColor("#d79a43");
        graphics.fillOval(point.x - radius, point.y - radius, radius * 2, radius * 2);
        graphics.setColor("#26312e");
        graphics.fillOval(point.x - drill, point.y - drill, drill * 2, drill * 2);
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
        if (selected)
            drawSelection(graphics, geometry);
        renderer.drawInstalled(graphics, context, geometry, selected);
        return geometry;
    }

    private void drawSelection(Graphics graphics, PhysicalPartRenderGeometry geometry) {
        Rectangle bounds = geometry.getSelectionBounds();
        graphics.setColor("#f4d35e");
        graphics.setLineWidth(4);
        graphics.drawRect(bounds.x - 8, bounds.y - 8, bounds.width + 16, bounds.height + 16);
        graphics.setLineWidth(1);
    }

    private void drawSilkscreenLabels(Graphics graphics) {
        for (PcbSilkscreenLabel label : layout.getSilkscreenLabels()) {
            graphics.setFont(new Font("sans-serif", label.isBold() ? Font.BOLD : 0,
                Math.max(10, scaleInt(label.getFontSize()))));
            graphics.setColor(label.getId().startsWith("net:") ? "#f2f5e9" : "#d9f1e3");
            String text = getPowerInputLabel(label.getTargetPadId(), label.getText());
            graphics.drawString(text, screenX(label.getBaselineX()), screenY(label.getBaselineY()));
        }
    }

    private void drawTray(Graphics graphics) {
        Rectangle tray = screenRect(layout.getPartsTray());
        graphics.setColor("#c9ced0");
        graphics.fillRect(tray.x, tray.y, tray.width, tray.height);
        graphics.setColor("#778084");
        graphics.drawRect(tray.x, tray.y, tray.width, tray.height);
        graphics.setFont(new Font("sans-serif", Font.BOLD, Math.max(11, scaleInt(13))));
        graphics.setColor("#3d484c");
        graphics.drawString("PARTS TRAY", tray.x + scaleInt(20), tray.y + scaleInt(30));
        Vector<PhysicalPart<?>> parts = getVisibleLoosePhysicalParts();
        if (parts.isEmpty()) {
            graphics.setFont(new Font("sans-serif", 0, Math.max(11, scaleInt(12))));
            graphics.drawString("No removed parts", tray.x + scaleInt(20), tray.y + scaleInt(70));
        }
        for (int index = 0; index < parts.size(); index++) {
            PhysicalPart<?> part = parts.get(index);
            PhysicalPartRenderProvider provider = renderRegistry.getProvider(part.getPackage());
            if (provider == null)
                throw new IllegalStateException("No physical render provider for part package: " +
                    part.getPackage().getId());
            PhysicalPartRenderContext context = new PhysicalPartRenderContext(this, null, part,
                part.getPackage(), index, true);
            PhysicalPartRenderer renderer = provider.getRenderer(part);
            PhysicalPartRenderGeometry geometry = renderer.getLooseGeometry(context);
            renderer.drawLoose(graphics, context, geometry, part.getId().equals(selectedPartId));
        }
        if (getTrayPageCount() > 1) {
            graphics.setFont(new Font("sans-serif", 0, Math.max(10, scaleInt(11))));
            graphics.drawString("Page " + (trayPage + 1) + " of " + getTrayPageCount(),
                tray.x + scaleInt(20), tray.y + tray.height - scaleInt(15));
        }
    }

    ProbeTarget findProbeTarget(CirSim sim, int screenX, int screenY) {
        // Board pads own the board-side probe envelope.  Resolve them before
        // any component-side or tray target so an overlap at a pad can never
        // be stolen by an installed-part renderer.
        for (PcbPadPlacement pad : layout.getPads()) {
            Rectangle probeBounds = screenRect(pad.getProbeBounds());
            if (probeBounds.contains(screenX, screenY))
                return new BoardPadProbeTarget(sim, instance, pad.getPadId(), this);
        }
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
        return null;
    }

    String findComponentId(int screenX, int screenY) {
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

    Point getPadPoint(String padId) {
        PcbPadPlacement pad = layout.getPad(padId);
        return pad == null ? null : new Point(screenX(pad.getX()), screenY(pad.getY()));
    }

    Point getComponentLeadPoint(String componentId, String padId) {
        PcbComponentPlacement placement = layout.getComponent(componentId);
        BoardComponent component = instance.getBoard().getComponent(componentId);
        if (placement == null || component == null)
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

    boolean hasPad(String padId) { return layout.getPad(padId) != null; }

    Point getLooseTerminalPoint(String partId, int terminal) {
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
        trayPage = clampTrayPageValue(page);
        if (selectedPartId != null && getLoosePartMarkerPoint(selectedPartId) == null)
            selectedPartId = null;
    }
    void clampTrayPage() { trayPage = clampTrayPageValue(trayPage); }

    Vector<PhysicalPart<?>> getVisibleLoosePhysicalParts() {
        Vector<PhysicalPart<?>> all = getAllLoosePhysicalParts();
        clampTrayPage();
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
    void setSelectedPartId(String partId) { selectedPartId = partId; }
    String getSelectedPartId() { return selectedPartId; }

    PhysicalPartRenderRegistry getRenderRegistryForDeveloperVerification() { return renderRegistry; }

    PhysicalPartRenderGeometry getInstalledGeometryForDeveloperVerification(String componentId) {
        PcbComponentPlacement placement = layout.getComponent(componentId);
        BoardComponent component = instance.getBoard().getComponent(componentId);
        if (placement == null || component == null)
            return null;
        PhysicalPart<?> part = instance.getPhysicalBoardRuntime().getInstalledPart(componentId);
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
        return Math.max(0, Math.min(page, getTrayPageCount() - 1));
    }

    private PhysicalPartRenderer requireRenderer(PhysicalPackage physicalPackage,
            PhysicalPart<?> part) {
        PhysicalPartRenderProvider provider = renderRegistry.getProvider(physicalPackage);
        if (provider == null)
            throw new IllegalStateException("No physical render provider for package: " +
                (physicalPackage == null ? "null" : physicalPackage.getId()));
        PhysicalPartRenderer renderer = provider.getRenderer(part);
        if (renderer == null)
            throw new IllegalStateException("Physical render provider returned no renderer: " +
                physicalPackage.getId());
        return renderer;
    }

    private boolean findComponentIdForPlacement(PcbComponentPlacement placement,
            BoardComponent component, PhysicalPart<?> part, PhysicalPartRenderContext context,
            int screenX, int screenY) {
        return requireRenderer(component.getPhysicalPackage(), part)
            .getInstalledGeometry(context).contains(screenX, screenY);
    }

    private ProbeTarget findInstalledProbeTarget(CirSim sim, PcbComponentPlacement placement,
            BoardComponent component, PhysicalPart<?> part, PhysicalPartRenderContext context,
            int screenX, int screenY) {
        if (part == null)
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

    private void publishDeveloperGeometry() {
        StringBuilder json = new StringBuilder("{\"points\":{");
        boolean first = true;
        for (PcbComponentPlacement component : layout.getComponents()) {
            Rectangle bounds = screenRect(component);
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
    int screenYForProvider(int value) { return screenY(value); }
    int scaleIntForProvider(int value) { return scaleInt(value); }
    Point screenPointForProvider(Point value) {
        return value == null ? null : new Point(screenX(value.x), screenY(value.y));
    }
    Rectangle screenRectForProvider(Rectangle value) { return screenRect(value); }
    Rectangle getPadProbeBoundsForDeveloperVerification(String padId) {
        return getPadProbeBounds(padId);
    }

    Object captureInstalledTargetIdentity(String componentId, String padId) {
        InstalledProjectionObservation observation = observeInstalledProjection(componentId);
        return observation == null ? null : observation.token;
    }

    boolean isInstalledTargetIdentityCurrent(String componentId, String padId, Object token) {
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
        return new Rectangle(screenX(value.getX()), screenY(value.getY()),
            scaleInt(value.getWidth()), scaleInt(value.getHeight()));
    }

    Point getProviderCanaryPadPoint(String padId, HashMap<String, Point> padPoints) {
        Point logical = padPoints.get(padId);
        return logical == null ? null : new Point(screenX(logical.x), screenY(logical.y));
    }

    private void updateTransform(Rectangle area) {
        canvasArea = area;
        scale = Math.min((area.width - 30) / (double) layout.getWidth(),
            (area.height - 30) / (double) layout.getHeight());
        if (scale > 1.35)
            scale = 1.35;
        offsetX = (area.width - scaleInt(layout.getWidth())) / 2;
        offsetY = (area.height - scaleInt(layout.getHeight())) / 2;
    }

    private int screenX(int x) { return offsetX + scaleInt(x); }
    private int screenY(int y) { return offsetY + scaleInt(y); }
    private int scaleInt(int value) { return (int) Math.round(value * scale); }
    private Rectangle screenRect(Rectangle rectangle) {
        return new Rectangle(screenX(rectangle.x), screenY(rectangle.y),
            scaleInt(rectangle.width), scaleInt(rectangle.height));
    }

    private Rectangle screenRect(PcbComponentPlacement component) {
        return new Rectangle(screenX(component.getX()), screenY(component.getY()),
            scaleInt(component.getWidth()), scaleInt(component.getHeight()));
    }

    private Rectangle getPadProbeBounds(String padId) {
        PcbPadPlacement pad = layout.getPad(padId);
        return pad == null ? null : screenRect(pad.getProbeBounds());
    }
}
