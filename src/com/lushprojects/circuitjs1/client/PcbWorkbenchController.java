package com.lushprojects.circuitjs1.client;

import java.util.HashMap;
import java.util.Vector;

import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.event.dom.client.ChangeEvent;
import com.google.gwt.event.dom.client.ChangeHandler;
import com.google.gwt.user.client.ui.Button;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.ListBox;
import com.google.gwt.user.client.ui.VerticalPanel;
import com.google.gwt.user.client.ui.PopupPanel;
import com.google.gwt.event.logical.shared.CloseEvent;
import com.google.gwt.event.logical.shared.CloseHandler;
import com.google.gwt.core.client.JavaScriptObject;
import com.google.gwt.dom.client.NativeEvent;
import com.google.gwt.user.client.Window;

class PcbWorkbenchController implements WorkbenchCapabilityContext {
    private final CirSim sim;
    private final GeneratedBoardInstance instance;
    private final BoardModificationController modifications;
    private final PcbWorkbenchRenderer renderer;
    private final WorkbenchRenderHost renderHost;
    private final VerticalPanel panel = new VerticalPanel();
    private final VerticalPanel ticketPanel = new VerticalPanel();
    private final VerticalPanel partsPanel = new VerticalPanel();
    private final Label feedback = new Label();
    private PopupPanel componentMenu;
    private Object componentMenuLease;
    private PhysicalPart<?> draggedPart;
    private String draggedFromComponent;
    private int dragStartX, dragStartY, dragX, dragY;
    private boolean partDragging;
    private PopupPanel interactionNotice;
    private com.google.gwt.user.client.Timer noticeTimer;
    private BenchPowerPanel benchPowerPanel;
    private final VerticalPanel viewPanel = new VerticalPanel();
    private final Label viewFeedback = new Label();
    private JavaScriptObject viewListeners;
    private JavaScriptObject viewFrame;
    private int viewFrameRequests, viewFramesPresented, viewFramesSkipped;
    private double lastViewRequestTime, maximumViewLatencyMs, maximumViewDrawMs;
    private boolean panning;
    private boolean tabViewHeld;
    private int panX, panY;
    private final boolean quickPlay;
    private VerticalPanel sidebar;
    private boolean attachedToSidebar;
    private final Object benchInstrumentOwner = new Object();
    private String finishFeedbackText = "";
    private String customerRetestFeedbackText = "";
    /*
     * Keep references to the handlers that the player-facing widgets really
     * receive.  The developer lifecycle verifier uses these references to
     * replay a retained callback after its owner has been replaced; no test
     * only callback is synthesized.
     */
    private final HashMap<String, ClickHandler> semanticOperationHandlers =
        new HashMap<String, ClickHandler>();
    private ClickHandler lastSemanticOperationHandler;
    private ClickHandler lastCustomerRetestHandler;
    private ClickHandler lastFinishHandler;
    private ClickHandler lastPhysicalActionHandler;

    private boolean isCurrentOwner() {
        return sim.getGeneratedBoardInstance() == instance &&
            sim.pcbWorkbenchController == this;
    }

    private boolean isCurrentOwner(GeneratedChallengeController challenge) {
        return isCurrentOwner() && sim.getGeneratedChallengeController() == challenge;
    }

    private boolean isCurrentPhysicalActionable() {
        return isCurrentOwner() && sim.isChallengeInteractionEnabled();
    }

    private boolean isCurrentSemanticActionable(GeneratedChallengeController challenge) {
        return isCurrentOwner(challenge) && sim.isGeneratedSemanticInteractionEnabled();
    }

    ClickHandler getSemanticOperationHandlerForDeveloperVerification(String stableId) {
        return stableId == null ? null : semanticOperationHandlers.get(stableId);
    }

    boolean isSemanticOperationControlEnabledForDeveloperVerification(String stableId) {
        GeneratedBoardOperation operation = instance.getOperationCatalog().find(stableId);
        if (!isCurrentOwner() || operation == null) return false;
        int matches = 0;
        boolean enabled = false;
        for (int i = 0; i < ticketPanel.getWidgetCount(); i++) {
            if (!(ticketPanel.getWidget(i) instanceof Button)) continue;
            Button button = (Button) ticketPanel.getWidget(i);
            if (operation.getPlayerLabel().equals(button.getText())) {
                matches++;
                enabled = button.isEnabled();
            }
        }
        return matches == 1 && enabled;
    }

    ClickHandler getLastSemanticOperationHandlerForDeveloperVerification() {
        return lastSemanticOperationHandler;
    }

    ClickHandler getCustomerRetestHandlerForDeveloperVerification() {
        return lastCustomerRetestHandler;
    }

    ClickHandler getFinishHandlerForDeveloperVerification() {
        return lastFinishHandler;
    }

    ClickHandler getPhysicalActionHandlerForDeveloperVerification() {
        return lastPhysicalActionHandler;
    }

    PcbWorkbenchController(CirSim sim, GeneratedBoardInstance instance,
            BoardModificationController modifications, PcbBoardLayout layout,
            VerticalPanel sidebar, boolean quickPlay) {
        this(sim, instance, modifications, layout, sidebar, quickPlay, true);
    }

    PcbWorkbenchController(CirSim sim, GeneratedBoardInstance instance,
            BoardModificationController modifications, PcbBoardLayout layout,
            VerticalPanel sidebar, boolean quickPlay, boolean attachToSidebar) {
        this.sim = sim;
        this.instance = instance;
        this.modifications = modifications;
        this.quickPlay = quickPlay;
        renderer = new PcbWorkbenchRenderer(instance, modifications, layout);
        renderHost = new WorkbenchRenderHost(sim, instance, modifications, renderer);
        final CirSim simulation = sim;
        renderer.setLooseProjectionTransitionListener(
            new PcbWorkbenchRenderer.LooseProjectionTransitionListener() {
                public void onLooseProjectionTransition() {
                    if (!isCurrentOwner())
                        return;
                    if (simulation.instrumentController != null)
                        simulation.instrumentController.onPhysicalProjectionChanged();
                }
            });
        ticketPanel.setStyleName("tsj-component-panel");
        ticketPanel.getElement().setAttribute("aria-label", "Service ticket");
        ticketPanel.setVisible(false);
        panel.setStyleName("tsj-component-panel");
        panel.getElement().setAttribute("aria-label", "Component");
        panel.setVisible(false);
        partsPanel.setStyleName("tsj-component-panel");
        partsPanel.getElement().setAttribute("aria-label", "Parts Tray");
        viewPanel.setStyleName("tsj-component-panel");
        viewPanel.getElement().setAttribute("aria-label", "Board view");
        viewFeedback.getElement().setAttribute("role", "status");
        rebuildViewPanel();
        if (attachToSidebar)
            attachToSidebar(sidebar);
    }

    void attachToSidebar(VerticalPanel targetSidebar) {
        if (attachedToSidebar) {
            if (sidebar != targetSidebar)
                throw new IllegalStateException("Workbench is attached to another sidebar");
            return;
        }
        if (targetSidebar == null)
            throw new IllegalArgumentException("Missing workbench sidebar");
        sidebar = targetSidebar;
        if (benchPowerPanel == null) benchPowerPanel = new BenchPowerPanel(sim, instance);
        sidebar.add(benchPowerPanel);
        benchPowerPanel.start();
        sidebar.add(viewPanel);
        sidebar.add(ticketPanel);
        sidebar.add(panel);
        sidebar.add(partsPanel);
        attachedToSidebar = true;
        viewListeners = installViewListeners(this, sim.cv.getElement());
        sim.registerAttachedPcbWorkbenchForDeveloperVerification(this);
    }

    void detachFromSidebar() {
        suspendBenchInstrument(benchInstrumentOwner);
        closeComponentMenu();
        closeInteractionNotice();
        cancelViewFrame(viewFrame); viewFrame = null;
        tabView(false);
        cancelViewGesture();
        removeViewListeners(viewListeners); viewListeners = null;
        if (!attachedToSidebar)
            return;
        sidebar.remove(ticketPanel);
        sidebar.remove(panel);
        sidebar.remove(partsPanel);
        sidebar.remove(viewPanel);
        benchPowerPanel.stop(); sidebar.remove(benchPowerPanel);
        attachedToSidebar = false;
        sim.unregisterAttachedPcbWorkbenchForDeveloperVerification(this);
        sidebar = null;
    }

    void disposeForDeveloperVerification() {
        detachFromSidebar();
        renderHost.detach();
        ticketPanel.clear();
        panel.clear();
        partsPanel.clear();
    }

    boolean isAttachedToSidebarForDeveloperVerification() { return attachedToSidebar; }

    void draw(Graphics graphics, Rectangle area) {
        if (isCurrentOwner()) {
            if (sim.dialogIsShowing() || !sim.isChallengeInteractionEnabled()) {
                tabView(false); cancelViewGesture();
            }
            renderHost.draw(graphics, area);
            presentBenchInstrument();
            drawPartDrag(graphics);
        }
    }

    /** Read-only presentation seam: no circuit, solution, or controller references cross it. */
    private void presentBenchInstrument() {
        if (!attachedToSidebar) return;
        if (!renderHost.isProduction()) { suspendBenchInstrument(benchInstrumentOwner); return; }
        PcbViewport.Transform camera = renderer.getViewport().permanent();
        Rectangle home = renderer.getMeterHome(), area = renderer.getViewport().getArea();
        projectBenchInstrument(benchInstrumentOwner, sim.cv.getElement(), camera.scale, camera.x, camera.y,
            area.x, area.y, area.width, area.height, home.x, home.y, home.width, home.height);
    }
    private static native void projectBenchInstrument(Object owner, com.google.gwt.dom.client.Element canvas,
            double scale, double x, double y, int ax, int ay, int aw, int ah,
            int hx, int hy, int hw, int hh) /*-{
        if ($wnd.tsjBenchInstruments) $wnd.tsjBenchInstruments.project(owner, canvas, {
            scale: scale, x: x, y: y, area: {x: ax, y: ay, width: aw, height: ah},
            home: {x: hx, y: hy, width: hw, height: hh}
        });
    }-*/;
    private static native void suspendBenchInstrument(Object owner) /*-{
        if ($wnd.tsjBenchInstruments) $wnd.tsjBenchInstruments.suspend(owner);
    }-*/;

    ProbeTarget findProbeTarget(int x, int y) {
        ProbeTarget target = !isCurrentOwner() ? null : renderer.hasViewportFixture() ?
            renderer.findProbeTarget(sim, x, y) : renderHost.resolve(renderHost.hit(x, y, true));
        if (renderer.wasTargetAmbiguous()) viewFeedback.setText("Several terminals overlap. Zoom in to choose one.");
        return target;
    }

    void cancelViewGesture() {
        cancelPartDrag();
        panning = false; renderer.getViewport().dismiss(); renderer.updateProjection();
    }
    /** Camera paints coalesce at display cadence without advancing the electrical simulation. */
    void requestViewFrame() {
        if (!isCurrentOwner() || !attachedToSidebar) return;
        viewFrameRequests++;
        lastViewRequestTime = viewClock();
        if (viewFrame == null) viewFrame = scheduleViewFrame(this);
    }
    private void presentViewFrame(JavaScriptObject token) {
        if (token != viewFrame) return;
        viewFrame = null;
        if (!isCurrentOwner() || !attachedToSidebar || sim.dialogIsShowing() ||
                !sim.isChallengeInteractionEnabled() || sim.activeMeasurementOverlay ||
                sim.analyzeFlag || sim.dcAnalysisFlag) {
            viewFramesSkipped++;
            return;
        }
        double started = viewClock();
        sim.backcontext.setTransform(1, 0, 0, 1, 0, 0);
        Graphics graphics = new Graphics(sim.backcontext);
        renderHost.draw(graphics, sim.circuitArea);
        presentBenchInstrument();
        drawPartDrag(graphics);
        sim.instrumentController.draw(graphics);
        sim.cvcontext.drawImage(sim.backcontext.getCanvas(), 0.0, 0.0);
        viewFramesPresented++;
        maximumViewLatencyMs = Math.max(maximumViewLatencyMs, started - lastViewRequestTime);
        maximumViewDrawMs = Math.max(maximumViewDrawMs, viewClock() - started);
    }
    private static native double viewClock() /*-{ return $wnd.performance.now(); }-*/;
    private static native JavaScriptObject scheduleViewFrame(PcbWorkbenchController owner) /*-{
        var token = { id: 0 };
        token.id = $wnd.requestAnimationFrame($entry(function() {
            owner.@com.lushprojects.circuitjs1.client.PcbWorkbenchController::presentViewFrame(Lcom/google/gwt/core/client/JavaScriptObject;)(token);
        }));
        return token;
    }-*/;
    private static native void cancelViewFrame(JavaScriptObject token) /*-{
        if (token) $wnd.cancelAnimationFrame(token.id);
    }-*/;
    String viewFrameEvidenceForDeveloperVerification() {
        return "{\"requests\":" + viewFrameRequests + ",\"presented\":" + viewFramesPresented +
            ",\"skipped\":" + viewFramesSkipped + ",\"pending\":" + (viewFrame != null) +
            ",\"maxLatencyMs\":" + maximumViewLatencyMs + ",\"maxDrawMs\":" + maximumViewDrawMs + "}";
    }
    int viewFramesPresentedForDeveloperVerification() { return viewFramesPresented; }
    boolean hasPendingViewFrameForDeveloperVerification() { return viewFrame != null; }
    void pointerMove(int x, int y) {
        if (!isCurrentOwner()) return;
        if (draggedPart != null) {
            dragX=x; dragY=y;
            if (Math.abs(x-dragStartX)+Math.abs(y-dragStartY)>8) partDragging=true;
        }
        if (panning) { renderer.getViewport().pan(x - panX, y - panY); panX = x; panY = y; }
        renderer.getViewport().moveCursor(x, y);
        if (panning || draggedPart != null || renderer.getViewport().isInspecting()) {
            renderer.updateProjection(); requestViewFrame();
        }
    }
    boolean beginPan(int button, boolean shift, int x, int y) {
        if (!isCurrentPhysicalActionable() || !renderer.getViewport().contains(x, y) ||
                renderer.getViewport().isInspecting()) return false;
        if (button != NativeEvent.BUTTON_MIDDLE && !(button == NativeEvent.BUTTON_LEFT && shift)) return false;
        panning = true; panX = x; panY = y; return true;
    }
    void endPan() { panning = false; }
    void beginPartDrag(int x,int y) {
        cancelPartDrag();
        if (!isCurrentPhysicalActionable() || !renderHost.isProduction() || !selectComponentAt(x,y)) return;
        draggedFromComponent=renderer.getSelectedComponentId();
        draggedPart=draggedFromComponent==null?instance.getPhysicalBoardRuntime().getPart(renderer.getSelectedPartId()):
            instance.getPhysicalBoardRuntime().getInstalledPart(draggedFromComponent);
        dragStartX=dragX=x; dragStartY=dragY=y;
    }
    void cancelPartDrag() { draggedPart=null; draggedFromComponent=null; partDragging=false; }
    private void drawPartDrag(Graphics graphics) {
        if (partDragging && draggedPart!=null && renderHost.isProduction())
            renderer.drawDraggedPart(graphics,draggedPart,draggedFromComponent,dragX-dragStartX,dragY-dragStartY);
    }
    void releasePartDrag(int x,int y) {
        PhysicalPart<?> part=draggedPart; String from=draggedFromComponent; boolean moved=partDragging;
        cancelPartDrag();
        if (!moved || part==null || !isCurrentPhysicalActionable()) return;
        WorkbenchRenderHit drop=renderHost.dropHit(x,y);
        String message="Drop the part on the tray or a compatible board footprint.";
        try {
            if (!sim.getBoardPowerController().isElectricallyUnpowered()) message="Switch off board power before moving parts.";
            else if (instance.getPhysicalBoardRuntime().getPart(part.getId())!=part) message="That part is no longer on this workbench.";
            else if (renderHost.accepts(drop) && from!=null && drop.kind==WorkbenchRenderHit.Kind.TRAY &&
                    instance.getPhysicalBoardRuntime().getInstalledPart(from)==part) {
                WorkbenchOperation remove=WorkbenchOperation.forPart(WorkbenchOperation.REMOVE,part);
                if(isOperationAvailable(part,remove) && dispatchOperation(part,remove)) {
                    renderer.setSelectedComponentId(null); renderer.setSelectedPartId(part.getId());
                    message="Part moved to tray.";
                } else message="This part cannot be removed in its current state.";
            } else if(renderHost.accepts(drop) && from==null && !part.isInstalled() && drop.kind==WorkbenchRenderHit.Kind.SLOT) {
                WorkbenchOperation install=WorkbenchOperation.forPartAtSlot(WorkbenchOperation.INSTALL,part,drop.id);
                if(isOperationAvailable(part,install) && dispatchOperation(part,install)) {
                    renderer.setSelectedPartId(null); renderer.setSelectedComponentId(drop.id);
                    message="Part installed.";
                } else message="That footprint is occupied or incompatible with this part.";
            }
        } catch(BoardModificationRejectedException failure) { message="Switch off board power before moving parts."; }
        if (!isCurrentOwner()) return;
        refresh(); sim.repaint(); showInteractionNotice(message);
    }
    private void closeInteractionNotice() {
        if(noticeTimer!=null)noticeTimer.cancel(); noticeTimer=null;
        if(interactionNotice!=null)interactionNotice.hide(); interactionNotice=null;
    }
    private void showInteractionNotice(String message) {
        closeInteractionNotice();
        final PopupPanel notice=new PopupPanel(); interactionNotice=notice;
        notice.setStyleName("tsj-interaction-notice"); notice.getElement().setAttribute("role","status");
        notice.getElement().setAttribute("aria-live","polite"); notice.add(new Label(message));
        notice.show(); notice.setPopupPosition(Math.max(8,(Window.getClientWidth()-notice.getOffsetWidth())/2),sim.cv.getAbsoluteTop()+12);
        noticeTimer=new com.google.gwt.user.client.Timer() { public void run() { if(interactionNotice==notice)closeInteractionNotice(); } };
        noticeTimer.schedule(3500);
    }
    void wheel(int delta, int x, int y) {
        if (!isCurrentPhysicalActionable() || sim.dialogIsShowing()) return;
        renderer.getViewport().zoom(Math.pow(1.12, Math.max(-3, Math.min(3, -delta))), x, y);
        renderer.updateProjection(); requestViewFrame();
    }
    boolean space(boolean down, int x, int y) {
        if (!down) { cancelViewGesture(); requestViewFrame(); return true; }
        if (!isCurrentPhysicalActionable() || sim.dialogIsShowing()) return false;
        boolean handled = renderer.getViewport().inspect(x, y);
        renderer.updateProjection(); requestViewFrame(); return handled;
    }
    boolean tabView(boolean down) {
        if (!down) {
            if (!tabViewHeld) return false;
            tabViewHeld = false;
            if (isCurrentOwner() && attachedToSidebar) {
                renderer.setViewingFace(PcbBoardSide.TOP);
                rebuildViewPanel(); viewChanged();
            }
            return true;
        }
        if (tabViewHeld) return true;
        if (!isCurrentPhysicalActionable() || !attachedToSidebar || sim.dialogIsShowing()) return false;
        tabViewHeld = true;
        renderer.setViewingFace(PcbBoardSide.BOTTOM);
        rebuildViewPanel(); viewChanged();
        return true;
    }
    private void viewChanged() {
        renderer.updateProjection();
        sim.instrumentController.onPhysicalProjectionChanged();
        requestViewFrame();
    }
    void auditViewEvent(NativeEvent event) {
        if (!sim.troubleshootU01Verification) return;
        if ("mousemove".equals(event.getType()) && !panning && !renderer.getViewport().isInspecting()) return;
        PcbViewport.Transform permanent=renderer.getViewport().permanent(), current=renderer.getViewport().current();
        publishViewEvent(event, permanent.scale, permanent.x, permanent.y, current.scale, current.x, current.y,
            current.flipped, renderer.getViewport().isInspecting());
    }
    /** Developer-only observation of the actual native input path; never drives the controller. */
    private static native void publishViewEvent(NativeEvent event, double scale, double x, double y,
            double currentScale, double currentX, double currentY, boolean flipped, boolean inspecting) /*-{
        var root=$doc.documentElement;
        var events=JSON.parse(root.getAttribute('data-tsj-u01-input-events') || '[]');
        events.push({type:event.type,key:event.key || '',trusted:event.isTrusted===true,
            clientX:event.clientX,clientY:event.clientY,permanent:[scale,x,y],
            current:[currentScale,currentX,currentY],flipped:flipped,loupe:inspecting});
        if(events.length>128) events.shift();
        root.setAttribute('data-tsj-u01-input-events',JSON.stringify(events));
    }-*/;
    private Button viewButton(String text, final Runnable action) {
        Button button = new Button(text);
        button.addClickHandler(new ClickHandler() { public void onClick(ClickEvent event) {
            if (!isCurrentOwner()) return;
            cancelViewGesture(); action.run(); viewChanged();
        }});
        return button;
    }
    private void rebuildViewPanel() {
        viewPanel.clear();
        viewPanel.add(new Label("BOARD VIEW"));
        VerticalPanel controls = new VerticalPanel();
        controls.add(viewButton("Fit bench", new Runnable() { public void run() { renderer.fitWorkbench(); }}));
        viewPanel.add(controls);
        VerticalPanel zoom = new VerticalPanel();
        zoom.add(viewButton("Zoom +", new Runnable() { public void run() { zoomFromButton(1.5); }}));
        zoom.add(viewButton("Zoom -", new Runnable() { public void run() { zoomFromButton(1 / 1.5); }}));
        zoom.add(viewButton(renderer.getViewingFace() == PcbBoardSide.TOP ? "View bottom copper" : "View top copper",
            new Runnable() { public void run() {
                renderer.setViewingFace(renderer.getViewingFace().opposite()); rebuildViewPanel();
            }}));
        viewPanel.add(zoom);
        viewPanel.add(new Label("Wheel: zoom. Shift-drag or middle-drag: pan. Hold Space: inspection loupe. Hold Tab on the board: bottom view; release: top."));
        viewPanel.add(new Label(renderer.getViewingFace() == PcbBoardSide.TOP ? "Top side / top copper" : "Bottom side / bottom copper"));
        final ListBox components = new ListBox();
        components.getElement().setAttribute("aria-label", "Select component");
        components.addItem("Select component", "");
        Vector<String> ids = instance.getBoard().getComponentIds(); java.util.Collections.sort(ids);
        for (String id : ids) components.addItem(instance.getBoard().getComponent(id).getDisplayName(), id);
        components.addChangeHandler(new ChangeHandler() { public void onChange(ChangeEvent event) {
            if (!isCurrentOwner()) return;
            String id = components.getValue(components.getSelectedIndex());
            PcbComponentPlacement part = renderer.getLayoutForProvider().getComponent(id);
            if (part == null) return;
            renderer.setSelectedComponentId(id);
            rebuildPanel(); viewChanged();
        }});
        viewPanel.add(components);
        final ListBox pads = new ListBox();
        pads.getElement().setAttribute("aria-label", "Probe terminal");
        pads.addItem("Probe terminal", "");
        for (String id : ids) {
            BoardComponent part = instance.getBoard().getComponent(id);
            for (String padId : part.getPadIds()) if (renderer.canProbePad(padId))
                pads.addItem(part.getDisplayName() + " terminal " + instance.getBoard().getPad(padId).getTerminalId(), padId);
        }
        viewPanel.add(pads);
        VerticalPanel probes = new VerticalPanel();
        probes.add(viewButton("Place red probe", new Runnable() { public void run() { probeSelected(pads, NativeEvent.BUTTON_LEFT); }}));
        probes.add(viewButton("Place black probe", new Runnable() { public void run() { probeSelected(pads, NativeEvent.BUTTON_RIGHT); }}));
        viewPanel.add(probes); viewPanel.add(viewFeedback);
    }
    private void zoomFromButton(double factor) {
        Rectangle area = renderer.getViewport().getArea();
        renderer.getViewport().zoom(factor, area.x + area.width / 2, area.y + area.height / 2);
    }
    private void probeSelected(ListBox pads, int button) {
        if (!isCurrentPhysicalActionable() || !sim.instrumentController.isHandlingPointerInput()) {
            viewFeedback.setText("Select a meter mode first."); return;
        }
        renderer.updateProjection();
        String id = pads.getValue(pads.getSelectedIndex()); Point point = renderer.getPadPoint(id);
        ProbeTarget target = point == null ? null : findProbeTarget(point.x, point.y);
        if (!(target instanceof BoardPadProbeTarget) || !id.equals(((BoardPadProbeTarget)target).getPadId())) {
            viewFeedback.setText("That terminal is outside this view or covered. Pan or zoom to it."); return;
        }
        sim.instrumentController.handlePointerInput(button, target); sim.repaint();
    }
    private static native JavaScriptObject installViewListeners(PcbWorkbenchController owner,
            com.google.gwt.dom.client.Element canvas) /*-{
        var cancel = $entry(function(e) {
            owner.@com.lushprojects.circuitjs1.client.PcbWorkbenchController::tabView(Z)(false);
            owner.@com.lushprojects.circuitjs1.client.PcbWorkbenchController::cancelViewGesture()();
            if(e) owner.@com.lushprojects.circuitjs1.client.PcbWorkbenchController::auditViewEvent(Lcom/google/gwt/dom/client/NativeEvent;)(e);
        });
        var focus = $entry(function(e) { if (e.target !== canvas) cancel(); });
        $wnd.addEventListener('blur', cancel); $doc.addEventListener('visibilitychange', cancel);
        $doc.addEventListener('pointercancel', cancel); $doc.addEventListener('focusin', focus);
        return { cancel: cancel, focus: focus };
    }-*/;
    private static native void removeViewListeners(JavaScriptObject listeners) /*-{
        if (!listeners) return;
        $wnd.removeEventListener('blur', listeners.cancel); $doc.removeEventListener('visibilitychange', listeners.cancel);
        $doc.removeEventListener('pointercancel', listeners.cancel); $doc.removeEventListener('focusin', listeners.focus);
    }-*/;

    boolean selectComponentAt(int x, int y) {
        if (!isCurrentPhysicalActionable())
            return false;
        WorkbenchRenderHit hit = renderHost.hit(x, y, false);
        String partId = renderHost.selectedPart(hit);
        if (partId != null) {
            renderer.setSelectedPartId(partId);
            renderer.setSelectedComponentId(null);
            rebuildPanel();
            rebuildPartsPanel();
            sim.repaint();
            return true;
        }
        String componentId = renderHost.selectedComponent(hit);
        renderer.setSelectedPartId(null);
        renderer.setSelectedComponentId(componentId);
        rebuildPanel();
        sim.repaint();
        return componentId != null;
    }

    void refresh() {
        if (!isCurrentOwner())
            return;
        rebuildTicket();
        rebuildPanel();
        rebuildPartsPanel();
        renderHost.scene();
        if (benchPowerPanel != null) benchPowerPanel.refreshReadings();
    }

    void hide() { panel.setVisible(false); }

    String getPanelTextForDeveloperVerification() { return panel.getElement().getInnerText(); }

    String getPlayerFacingTextForDeveloperVerification() {
        return ticketPanel.getElement().getInnerText() + "\n" +
            panel.getElement().getInnerText() + "\n" +
            partsPanel.getElement().getInnerText();
    }

    PcbWorkbenchRenderer getRenderer() { return renderer; }
    WorkbenchRenderHost getRenderHostForDeveloperVerification() { return renderHost; }
    void replaceRendererForDeveloperVerification(WorkbenchRenderBackend backend) {
        if (!sim.troubleshootDebug || !isCurrentOwner()) throw new IllegalStateException("Renderer canary is developer-only");
        tabView(false);
        cancelViewGesture();
        if (backend == null) renderHost.restoreProduction(); else renderHost.replace(backend);
        sim.repaint();
    }

    private WorkbenchCapabilityStrategy getCapability(PhysicalPart part,
            WorkbenchOperation operation) {
        return WorkbenchCapabilityDiscovery.find(part, operation,
            instance.getPhysicalBoardRuntime().getWorkbenchCapabilityRegistry());
    }

    private boolean isOperationAvailable(PhysicalPart part, WorkbenchOperation operation) {
        if (!isCurrentPhysicalActionable())
            return false;
        WorkbenchCapabilityStrategy capability = getCapability(part, operation);
        return capability != null && capability.isAvailable(operation, this);
    }

    private boolean dispatchOperation(PhysicalPart part, WorkbenchOperation operation) {
        if (!isCurrentPhysicalActionable())
            return false;
        WorkbenchCapabilityStrategy capability = getCapability(part, operation);
        return capability != null && capability.invoke(operation, this);
    }

    void closeComponentMenu() {
        componentMenuLease = null;
        if (componentMenu != null) componentMenu.hide();
        componentMenu = null;
    }
    boolean openComponentMenu(int x, int y) {
        if (!isCurrentPhysicalActionable() || sim.dialogIsShowing()) return false;
        WorkbenchRenderHit hit = renderHost.hit(x,y,false);
        String component = renderHost.selectedComponent(hit), partId = renderHost.selectedPart(hit);
        if (component == null && partId == null) return false;
        selectComponentAt(x,y);
        closeComponentMenu();
        final PopupPanel popup = new PopupPanel(true);
        componentMenu = popup; componentMenuLease = new Object();
        popup.setStyleName("tsj-component-context-menu");
        popup.getElement().setAttribute("role", "dialog");
        popup.getElement().setAttribute("aria-label", "Component actions");
        VerticalPanel actions = new VerticalPanel();
        if (component != null) {
            actions.add(styledLabel(playerComponentName(component),"tsj-component-title"));
            Vector<GeneratedComponentConnectionBinding> bindings = instance.getConnectionBindings().getForComponentOrEmpty(component);
            if (!sim.getBoardPowerController().isElectricallyUnpowered()) actions.add(new Label("Switch off board power to modify this part."));
            if (!bindings.isEmpty()) addActions(actions,component,bindings,!sim.getBoardPowerController().isElectricallyUnpowered());
        } else {
            WorkbenchPartsProvider provider = instance.getPhysicalBoardRuntime().getWorkbenchPartsProviderForPart(partId);
            if (provider != null) addSelectedPartControls(actions,provider,partId);
        }
        popup.add(actions);
        popup.addCloseHandler(new CloseHandler<PopupPanel>() { public void onClose(CloseEvent<PopupPanel> event) {
            if (componentMenu == popup) { componentMenuLease = null; componentMenu = null; }
            if (isCurrentOwner()) sim.cv.getElement().focus();
        }});
        popup.setPopupPosition(sim.cv.getAbsoluteLeft()+x,sim.cv.getAbsoluteTop()+y);
        popup.show();
        popup.setPopupPosition(Math.max(8,Math.min(sim.cv.getAbsoluteLeft()+x,Window.getClientWidth()-popup.getOffsetWidth()-8)),
            Math.max(8,Math.min(sim.cv.getAbsoluteTop()+y,Window.getClientHeight()-popup.getOffsetHeight()-8)));
        if (actions.getWidgetCount()>1 && actions.getWidget(1) instanceof Button) ((Button)actions.getWidget(1)).setFocus(true);
        return true;
    }

    private String operationLabel(PhysicalPart part, WorkbenchOperation operation,
            String fallback) {
        WorkbenchCapabilityStrategy capability = getCapability(part, operation);
        return capability == null ? fallback : capability.getOperationLabel(operation);
    }

    private void rebuildPanel() {
        panel.clear();
        String componentId = renderer.getSelectedComponentId();
        panel.setVisible(componentId != null);
        if (componentId == null)
            return;
        BoardComponent component = instance.getBoard().getComponent(componentId);
        panel.add(styledLabel(component.getDisplayName(), "tsj-component-title"));
        panel.add(new Label("Type: " + component.getType().toLowerCase()));
        PhysicalBoardRuntime runtime = instance.getPhysicalBoardRuntime();
        WorkbenchPartsProvider partsProvider = runtime.getWorkbenchPartsProvider(componentId);
        PhysicalPart<?> installedPart = runtime.getInstalledPart(componentId);
        PhysicalNameplate nameplate = installedPart == null ?
            (partsProvider == null ? instance.getPhysicalSpecifications().getNameplate(componentId) :
                null) : installedPart.getPlayerVisibleNameplate();
        if (nameplate != null && nameplate.hasWorkbenchDetail())
            panel.add(new Label(nameplate.getWorkbenchDetailLabel() + ": " +
                nameplate.getWorkbenchDetailValue()));
        Vector<GeneratedComponentConnectionBinding> bindings =
            instance.getConnectionBindings().getForComponentOrEmpty(componentId);
        if (isManagedSlotEmpty(componentId))
            panel.add(new Label("State: " + component.getDisplayName() + " slot empty"));
        else if (!bindings.isEmpty())
            panel.add(new Label("State: " + formatState(modifications.getComponentState(componentId))));
        else if (nameplate != null && nameplate.hasWorkbenchDetail() && partsProvider == null)
            panel.add(new Label("State: Installed"));
        for (String padId : component.getPadIds()) {
            BoardPad pad = instance.getBoard().getPad(padId);
            panel.add(new Label("Lead " + pad.getTerminalId() + ": " +
                component.getDisplayName() + "." + pad.getTerminalId()));
        }
        feedback.setText("");
        feedback.setStyleName("tsj-inline-feedback");
        panel.add(feedback);
        if (bindings.isEmpty())
            return;
        boolean powered = !sim.getBoardPowerController().isElectricallyUnpowered();
        boolean preparationDisabled = !sim.isChallengeInteractionEnabled();
        if (powered)
            feedback.setText("Turn board power off before modifying components.");
        addActions(panel, componentId, bindings, powered || preparationDisabled);
    }

    private void rebuildPartsPanel() {
        partsPanel.clear();
        renderer.clampTrayPage();
        PhysicalBoardRuntime runtime = instance.getPhysicalBoardRuntime();
        Vector<WorkbenchPartsProvider> providers = runtime.getWorkbenchPartsProviders();
        if (providers.isEmpty())
            return;
        boolean powered = !sim.getBoardPowerController().isElectricallyUnpowered();
        boolean powerWarningAdded = false;
        if (sim.playerSessionController == null)
            for (WorkbenchPartsProvider provider : providers)
                powerWarningAdded = addCatalog(provider, powered, powerWarningAdded);

        partsPanel.add(styledLabel("Parts Tray", "tsj-component-title"));
        Vector<PhysicalPart<?>> looseParts = getLooseParts(providers);
        if (looseParts.isEmpty())
            partsPanel.add(new Label("No loose parts."));
        int pageSize = renderer.getPartsPerTrayPage();
        int start = renderer.getTrayPage() * pageSize;
        int end = Math.min(looseParts.size(), start + pageSize);
        for (int index = start; index < end; index++)
            addLoosePartButton(looseParts.get(index));
        addPaginationControls();

        final String selectedPartId = renderer.getSelectedPartId();
        if (selectedPartId == null)
            return;
        WorkbenchPartsProvider selectedProvider =
            runtime.getWorkbenchPartsProviderForPart(selectedPartId);
        if (selectedProvider != null)
            addSelectedPartControls(partsPanel, selectedProvider, selectedPartId);
    }

    private boolean addCatalog(final WorkbenchPartsProvider provider, boolean powered,
            boolean powerWarningAdded) {
        final String componentId = provider.getComponentId();
        Vector<WorkbenchCatalogEntry> entries = provider.getCatalogEntries();
        if (entries.isEmpty())
            return powerWarningAdded;
        partsPanel.add(styledLabel(provider.getCatalogTitle(), "tsj-component-title"));
        final ListBox catalog = new ListBox();
        for (WorkbenchCatalogEntry entry : entries)
            catalog.addItem(entry.getDisplayName(), entry.getId());
        boolean anyCatalogAvailable = false;
        for (WorkbenchCatalogEntry entry : entries)
            anyCatalogAvailable = anyCatalogAvailable || isOperationAvailable(null,
                WorkbenchOperation.forCatalog(componentId, entry.getId()));
        catalog.setEnabled(anyCatalogAvailable);
        partsPanel.add(catalog);
        final Button installNew = new Button(provider.getInstallNewLabel());
        installNew.setStyleName("tsj-action-button");
        updateCatalogControls(provider, componentId, catalog, installNew);
        catalog.addChangeHandler(new ChangeHandler() {
            public void onChange(ChangeEvent event) {
                if (!isCurrentOwner())
                    return;
                updateCatalogControls(provider, componentId, catalog, installNew);
            }
        });
        ClickHandler installNewHandler = new ClickHandler() {
            public void onClick(ClickEvent event) {
                if (!isCurrentPhysicalActionable())
                    return;
                try {
                    if (dispatchOperation(null, WorkbenchOperation.forCatalog(componentId,
                            catalog.getValue(catalog.getSelectedIndex()))))
                        renderer.setSelectedPartId(null);
                } catch (BoardModificationRejectedException exception) {
                    feedback.setText("Turn board power off.");
                }
                if (!isCurrentOwner())
                    return;
                refresh();
                sim.repaint();
            }
        };
        lastPhysicalActionHandler = installNewHandler;
        installNew.addClickHandler(installNewHandler);
        partsPanel.add(installNew);
        if (powered && !powerWarningAdded) {
            partsPanel.add(new Label("Turn board power off."));
            powerWarningAdded = true;
        }
        PhysicalBoardSlot slot = instance.getPhysicalBoardRuntime().getSlot(componentId);
        if (slot != null && slot.isOccupied() && (!powered ||
                provider.showOccupiedMessageWhenPowered()))
            partsPanel.add(new Label("Remove " + playerComponentName(componentId) +
                " before installing a replacement."));
        return powerWarningAdded;
    }

    private void updateCatalogControls(WorkbenchPartsProvider provider, String componentId,
            ListBox catalog, Button installNew) {
        int selectedIndex = catalog.getSelectedIndex();
        if (selectedIndex < 0 || selectedIndex >= catalog.getItemCount()) {
            installNew.setText(provider.getInstallNewLabel());
            installNew.setEnabled(false);
            return;
        }
        WorkbenchOperation operation = WorkbenchOperation.forCatalog(componentId,
            catalog.getValue(selectedIndex));
        installNew.setText(operationLabel(null, operation, provider.getInstallNewLabel()));
        installNew.setEnabled(isOperationAvailable(null, operation));
    }

    private String playerPartLabel(WorkbenchPartsProvider provider, PhysicalPart<?> part) {
        if (sim.troubleshootDebug) return provider.getPartLabel(part);
        int index = instance.getPhysicalBoardRuntime().getPhysicalParts().indexOf(part);
        if (index < 0) throw new IllegalArgumentException("Part is outside the current inventory");
        PhysicalNameplate markings = part.getPlayerVisibleNameplate();
        return "Part " + (index + 1) + " - " + (markings.hasWorkbenchDetail() ?
            markings.getWorkbenchDetailValue() : markings.getDisplayName());
    }

    private void addLoosePartButton(PhysicalPart<?> part) {
        WorkbenchPartsProvider provider = instance.getPhysicalBoardRuntime()
            .getWorkbenchPartsProviderForPart(part.getId());
        if (provider == null)
            throw new IllegalStateException("Loose part has no workbench provider: " + part.getId());
        Button select = new Button(playerPartLabel(provider, part));
        select.setStyleName("tsj-action-button");
        select.setEnabled(isCurrentPhysicalActionable());
        final String partId = part.getId();
        ClickHandler selectHandler = new ClickHandler() {
            public void onClick(ClickEvent event) {
                if (!isCurrentPhysicalActionable())
                    return;
                renderer.setSelectedPartId(partId);
                renderer.setSelectedComponentId(null);
                rebuildPanel();
                rebuildPartsPanel();
                sim.repaint();
            }
        };
        select.addClickHandler(selectHandler);
        partsPanel.add(select);
    }

    private void addSelectedPartControls(final VerticalPanel targetPanel, final WorkbenchPartsProvider provider,
            final String selectedPartId) {
        final Object menuLease = componentMenuLease;
        final PhysicalPart<?> part = provider.getPart(selectedPartId);
        targetPanel.add(new Label("Selected: " + playerPartLabel(provider, part)));
        targetPanel.add(new Label("State: Loose"));
        PhysicalBoardRuntime runtime = instance.getPhysicalBoardRuntime();
        Vector<PhysicalSlotMutationProvider> targets =
            runtime.getCompatibleMutationProviders(part);
        if (targets.isEmpty()) {
            targetPanel.add(new Label("No compatible empty target."));
        } else {
            targetPanel.add(new Label("Compatible targets:"));
            for (final PhysicalSlotMutationProvider target : targets) {
                final String targetComponentId = target.getComponentId();
                final WorkbenchOperation installOperation = WorkbenchOperation.forPartAtSlot(
                    WorkbenchOperation.INSTALL, part, targetComponentId);
                final String targetLabel = playerTargetLabel(targetComponentId);
                Button install = new Button("Install as " + targetLabel);
                install.setStyleName("tsj-action-button");
                install.setEnabled(getCapability(part, installOperation) != null &&
                    isOperationAvailable(part, installOperation));
                ClickHandler installHandler = new ClickHandler() {
                    public void onClick(ClickEvent event) {
                        if (!isCurrentPhysicalActionable() || (targetPanel != partsPanel && menuLease != componentMenuLease))
                            return;
                        try {
                            if (dispatchOperation(part, installOperation))
                                renderer.setSelectedPartId(null);
                        } catch (BoardModificationRejectedException exception) {
                            feedback.setText("Turn board power off before modifying components.");
                        }
                        if (!isCurrentOwner())
                            return;
                        closeComponentMenu();
                        refresh();
                        sim.repaint();
                    }
                };
                lastPhysicalActionHandler = installHandler;
                install.addClickHandler(installHandler);
                targetPanel.add(install);
            }
        }

    }

    private Vector<PhysicalPart<?>> getLooseParts(Vector<WorkbenchPartsProvider> providers) {
        Vector<PhysicalPart<?>> result = new Vector<PhysicalPart<?>>();
        for (WorkbenchPartsProvider provider : providers)
            result.addAll(provider.getLooseParts());
        return result;
    }

    private void addPaginationControls() {
        if (renderer.getTrayPageCount() <= 1)
            return;
        partsPanel.add(new Label("Page " + (renderer.getTrayPage() + 1) + " of " +
            renderer.getTrayPageCount()));
        Button previous = new Button("Previous");
        previous.setEnabled(isCurrentPhysicalActionable() && renderer.getTrayPage() > 0);
        previous.addClickHandler(new ClickHandler() {
            public void onClick(ClickEvent event) {
                if (!isCurrentPhysicalActionable())
                    return;
                renderer.setTrayPage(renderer.getTrayPage() - 1); refresh(); sim.repaint();
            }
        });
        Button next = new Button("Next");
        next.setEnabled(isCurrentPhysicalActionable() &&
            renderer.getTrayPage() + 1 < renderer.getTrayPageCount());
        next.addClickHandler(new ClickHandler() {
            public void onClick(ClickEvent event) {
                if (!isCurrentPhysicalActionable())
                    return;
                renderer.setTrayPage(renderer.getTrayPage() + 1); refresh(); sim.repaint();
            }
        });
        partsPanel.add(previous);
        partsPanel.add(next);
    }

    private void rebuildTicket() {
        ticketPanel.clear();
        final GeneratedChallengeController challenge = sim.getGeneratedChallengeController();
        ticketPanel.setVisible(challenge != null);
        if (challenge == null)
            return;
        if (challenge.getCustomerRetestResult() == null)
            customerRetestFeedbackText = "";
        ticketPanel.add(styledLabel("Service Ticket", "tsj-component-title"));
        ticketPanel.add(new Label(challenge.isReady() ? challenge.getComplaintText() :
            "Preparing challenge..."));
        if (challenge.isReady())
            addCustomerOperationControls(challenge);
        if (quickPlay && sim.playerSessionController == null) {
            final Button finish = new Button("Finish Job");
            finish.setStyleName("tsj-action-button");
            finish.setEnabled(isCurrentSemanticActionable(challenge) && !challenge.isCompleted() &&
                challenge.getCustomerRetestResult() != null &&
                challenge.getCustomerRetestResult().isPassed());
            ClickHandler finishHandler = new ClickHandler() {
                public void onClick(ClickEvent event) {
                    if (!isCurrentSemanticActionable(challenge) || challenge.isCompleted())
                        return;
                    if (sim.finishQuickPlayJob()) {
                        finishFeedbackText = "";
                        Window.Location.reload();
                    } else {
                        finishFeedbackText =
                            "Functional check failed. Continue troubleshooting.";
                        rebuildTicket();
                    }
                }
            };
            lastFinishHandler = finishHandler;
            finish.addClickHandler(finishHandler);
            ticketPanel.add(finish);
            if (finishFeedbackText.length() != 0) {
                Label result = new Label(finishFeedbackText);
                result.setStyleName("tsj-inline-feedback");
                ticketPanel.add(result);
            }
        }
    }

    private void addCustomerOperationControls(final GeneratedChallengeController challenge) {
        GeneratedCustomerRetestProfile profile = challenge.getCustomerRetestProfile();
        ticketPanel.add(new Label("Customer retest: " + profile.getPlayerInstruction()));
        for (final GeneratedBoardOperation operation : instance.getOperationCatalog().getAll()) {
            if (GeneratedBoardOperationIds.CUSTOMER_RETEST.equals(operation.getStableId()))
                continue;
            Button command = new Button(operation.getPlayerLabel());
            command.setStyleName("tsj-action-button");
            command.setEnabled(isCurrentSemanticActionable(challenge));
            final String operationId = operation.getStableId();
            ClickHandler commandHandler = new ClickHandler() {
                public void onClick(ClickEvent event) {
                    if (!isCurrentSemanticActionable(challenge))
                        return;
                    if (sim.invokeGeneratedPlayerOperation(operationId)) {
                        customerRetestFeedbackText = "";
                        refresh();
                    }
                    if (isCurrentOwner())
                        sim.repaint();
                }
            };
            semanticOperationHandlers.put(operationId, commandHandler);
            lastSemanticOperationHandler = commandHandler;
            command.addClickHandler(commandHandler);
            ticketPanel.add(command);
        }
        final GeneratedBoardOperation retestOperation = instance.getOperationCatalog().find(
            GeneratedBoardOperationIds.CUSTOMER_RETEST);
        if (retestOperation != null && sim.playerSessionController == null) {
            Button retest = new Button(retestOperation.getPlayerLabel());
            retest.setStyleName("tsj-action-button");
            retest.setEnabled(isCurrentSemanticActionable(challenge) && !challenge.isCompleted());
            ClickHandler retestHandler = new ClickHandler() {
                public void onClick(ClickEvent event) {
                    if (!isCurrentSemanticActionable(challenge) || challenge.isCompleted())
                        return;
                    GeneratedCustomerRetestResult result = sim.performCustomerRetest();
                    customerRetestFeedbackText = result.getPlayerMessage();
                    if (!isCurrentOwner())
                        return;
                    refresh();
                    sim.repaint();
                }
            };
            lastCustomerRetestHandler = retestHandler;
            retest.addClickHandler(retestHandler);
            ticketPanel.add(retest);
        }
        if (customerRetestFeedbackText.length() != 0) {
            Label result = new Label(customerRetestFeedbackText);
            result.setStyleName("tsj-inline-feedback");
            ticketPanel.add(result);
        }
    }

    private void addActions(final VerticalPanel targetPanel, final String componentId,
            Vector<GeneratedComponentConnectionBinding> bindings, boolean disabled) {
        if (isManagedSlotEmpty(componentId))
            return;
        final PhysicalPart part = getInstalledPhysicalPart(componentId);
        if (part != null && !hasComponentCapability(part, componentId))
            return;
        ComponentPhysicalState state = modifications.getComponentState(componentId);
        if (state == ComponentPhysicalState.INSTALLED) {
            for (final GeneratedComponentConnectionBinding binding : bindings) {
                BoardPad pad = instance.getBoard().getPad(binding.getPadId());
                final WorkbenchOperation operation = part == null ?
                    WorkbenchOperation.forComponentLead(WorkbenchOperation.LIFT_LEAD, componentId,
                        binding.getPadId()) :
                    WorkbenchOperation.forPartLead(WorkbenchOperation.LIFT_LEAD, part,
                        componentId, binding.getPadId());
                addAction(targetPanel, operationLabel(part, operation, "Lift lead " + pad.getTerminalId()),
                    disabled || !isOperationAvailable(part, operation), new ComponentAction() {
                    public void execute() {
                        dispatchOperation(part, operation);
                    }
                });
            }
            addRemoveAction(targetPanel, componentId, part, disabled);
        } else if (state == ComponentPhysicalState.LEAD_LIFTED) {
            for (final GeneratedComponentConnectionBinding binding : bindings) {
                final BoardPad pad = instance.getBoard().getPad(binding.getPadId());
                if (modifications.isLeadConnected(componentId, binding.getPadId())) {
                    final WorkbenchOperation operation = part == null ?
                        WorkbenchOperation.forComponentLead(WorkbenchOperation.LIFT_LEAD,
                            componentId, binding.getPadId()) :
                        WorkbenchOperation.forPartLead(WorkbenchOperation.LIFT_LEAD, part,
                            componentId, binding.getPadId());
                    addAction(targetPanel, operationLabel(part, operation, "Lift lead " + pad.getTerminalId()),
                        disabled || !isOperationAvailable(part, operation), new ComponentAction() {
                        public void execute() {
                            dispatchOperation(part, operation);
                        }
                    });
                } else {
                    final WorkbenchOperation operation = part == null ?
                        WorkbenchOperation.forComponentLead(WorkbenchOperation.RECONNECT_LEAD,
                            componentId, binding.getPadId()) :
                        WorkbenchOperation.forPartLead(WorkbenchOperation.RECONNECT_LEAD, part,
                            componentId, binding.getPadId());
                    addAction(targetPanel, operationLabel(part, operation,
                            "Reconnect lead " + pad.getTerminalId()),
                        disabled || !isOperationAvailable(part, operation),
                        new ComponentAction() {
                            public void execute() {
                                dispatchOperation(part, operation);
                            }
                        });
                }
            }
            addRemoveAction(targetPanel, componentId, part, disabled);
            addRestoreAction(targetPanel, componentId, part, disabled);
        } else {
            addRestoreAction(targetPanel, componentId, part, disabled);
        }
    }

    private void addRemoveAction(final VerticalPanel targetPanel, final String componentId, final PhysicalPart part,
            boolean disabled) {
        final WorkbenchOperation operation = part == null ?
            WorkbenchOperation.forComponent(WorkbenchOperation.REMOVE, componentId) :
            WorkbenchOperation.forPart(WorkbenchOperation.REMOVE, part);
        addAction(targetPanel, operationLabel(part, operation, "Remove component"),
            disabled || !isOperationAvailable(part, operation),
            new ComponentAction() {
            public void execute() {
                dispatchOperation(part, operation);
            }
        });
    }

    private void addRestoreAction(final VerticalPanel targetPanel, final String componentId, final PhysicalPart part,
            boolean disabled) {
        final WorkbenchOperation operation = part == null ?
            WorkbenchOperation.forComponent(WorkbenchOperation.RESTORE, componentId) :
            WorkbenchOperation.forPart(WorkbenchOperation.RESTORE, part);
        addAction(targetPanel, operationLabel(part, operation, "Restore component"),
            disabled || !isOperationAvailable(part, operation),
            new ComponentAction() {
            public void execute() {
                dispatchOperation(part, operation);
            }
        });
    }

    private void addAction(final VerticalPanel targetPanel, final String text, boolean disabled, final ComponentAction action) {
        final Object menuLease = componentMenuLease;
        Button button = new Button(text);
        button.setStyleName("tsj-action-button");
        button.setEnabled(!disabled);
        ClickHandler actionHandler = new ClickHandler() {
            public void onClick(ClickEvent event) {
                if (!isCurrentPhysicalActionable() || (targetPanel != panel && menuLease != componentMenuLease))
                    return;
                try {
                    action.execute();
                    closeComponentMenu();
                    feedback.setText("");
                } catch (BoardModificationRejectedException exception) {
                    feedback.setText("Turn board power off before modifying components.");
                }
                if (!isCurrentOwner())
                    return;
                refresh();
                sim.repaint();
            }
        };
        lastPhysicalActionHandler = actionHandler;
        button.addClickHandler(actionHandler);
        targetPanel.add(button);
    }

    private Label styledLabel(String text, String style) {
        Label label = new Label(text);
        label.setStyleName(style);
        return label;
    }

    private String formatState(ComponentPhysicalState state) {
        if (state == ComponentPhysicalState.LEAD_LIFTED)
            return "Lead Lifted";
        if (state == ComponentPhysicalState.REMOVED)
            return "Removed";
        return "Installed";
    }

    PhysicalPart getInstalledPhysicalPart(String componentId) {
        return instance.getPhysicalBoardRuntime().getInstalledPart(componentId);
    }

    public boolean isAvailable(WorkbenchOperation operation) {
        if (!isCurrentPhysicalActionable())
            return false;
        WorkbenchCapabilityStrategy capability = getCapability(operation == null ? null :
            operation.getPart(), operation);
        return capability != null && capability.isAvailable(operation, this);
    }

    public boolean dispatch(WorkbenchOperation operation) {
        if (!isCurrentPhysicalActionable())
            return false;
        WorkbenchCapabilityStrategy capability = getCapability(operation == null ? null :
            operation.getPart(), operation);
        return capability != null && capability.isAvailable(operation, this) &&
            capability.invoke(operation, this);
    }

    private boolean hasComponentCapability(PhysicalPart part, String componentId) {
        WorkbenchOperation remove = part == null ?
            WorkbenchOperation.forComponent(WorkbenchOperation.REMOVE, componentId) :
            WorkbenchOperation.forPart(WorkbenchOperation.REMOVE, part);
        WorkbenchOperation lift = part == null ?
            WorkbenchOperation.forComponentLead(WorkbenchOperation.LIFT_LEAD, componentId,
                instance.getBoard().getComponent(componentId).getPadIds().firstElement()) :
            WorkbenchOperation.forPartLead(WorkbenchOperation.LIFT_LEAD, part, componentId,
                instance.getBoard().getComponent(componentId).getPadIds().firstElement());
        return getCapability(part, remove) != null || getCapability(part, lift) != null;
    }

    private boolean isManagedSlotEmpty(String componentId) {
        PhysicalSlotMutationProvider provider = instance.getPhysicalBoardRuntime()
            .getMutationProvider(componentId);
        PhysicalBoardSlot slot = instance.getPhysicalBoardRuntime().getSlot(componentId);
        return provider != null && slot != null && !slot.isOccupied();
    }

    private String playerComponentName(String componentId) {
        return instance.getBoard().getComponent(componentId).getDisplayName();
    }

    private String playerTargetLabel(String componentId) {
        String componentLabel = playerComponentName(componentId);
        for (PcbLayoutRegion region : renderer.getLayoutForProvider().getRegions())
            for (String member : region.getComponentIds())
                if (componentId.equals(member))
                    return region.label + " / " + componentLabel;
        return componentLabel;
    }

    private interface ComponentAction { void execute(); }
}
