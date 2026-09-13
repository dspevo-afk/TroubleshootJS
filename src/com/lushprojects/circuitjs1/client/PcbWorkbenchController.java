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
import com.google.gwt.core.client.JavaScriptObject;
import com.google.gwt.dom.client.NativeEvent;
import com.google.gwt.user.client.Window;

class PcbWorkbenchController implements WorkbenchCapabilityContext {
    private final CirSim sim;
    private final GeneratedBoardInstance instance;
    private final BoardModificationController modifications;
    private final PcbWorkbenchRenderer renderer;
    private final VerticalPanel panel = new VerticalPanel();
    private final VerticalPanel ticketPanel = new VerticalPanel();
    private final VerticalPanel partsPanel = new VerticalPanel();
    private final Label feedback = new Label();
    private BenchPowerPanel benchPowerPanel;
    private final VerticalPanel viewPanel = new VerticalPanel();
    private final Label viewFeedback = new Label();
    private JavaScriptObject viewListeners;
    private boolean panning;
    private int panX, panY;
    private final boolean quickPlay;
    private VerticalPanel sidebar;
    private boolean attachedToSidebar;
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
        final CirSim simulation = sim;
        renderer.setLooseProjectionTransitionListener(
            new PcbWorkbenchRenderer.LooseProjectionTransitionListener() {
                public void onLooseProjectionTransition() {
                    if (!isCurrentOwner())
                        return;
                    if (simulation.instrumentController != null)
                        simulation.instrumentController.onLooseProjectionChanged();
                }
            });
        ticketPanel.setStyleName("tsj-component-panel");
        ticketPanel.setVisible(false);
        panel.setStyleName("tsj-component-panel");
        panel.setVisible(false);
        partsPanel.setStyleName("tsj-component-panel");
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
        ticketPanel.clear();
        panel.clear();
        partsPanel.clear();
    }

    boolean isAttachedToSidebarForDeveloperVerification() { return attachedToSidebar; }

    void draw(Graphics graphics, Rectangle area) {
        if (isCurrentOwner()) {
            if (sim.dialogIsShowing() || !sim.isChallengeInteractionEnabled()) cancelViewGesture();
            renderer.draw(graphics, area);
        }
    }

    ProbeTarget findProbeTarget(int x, int y) {
        ProbeTarget target = isCurrentOwner() ? renderer.findProbeTarget(sim, x, y) : null;
        if (renderer.wasTargetAmbiguous()) viewFeedback.setText("Several terminals overlap. Zoom in to choose one.");
        return target;
    }

    void cancelViewGesture() {
        panning = false; renderer.getViewport().dismiss(); renderer.updateProjection();
    }
    void pointerMove(int x, int y) {
        if (!isCurrentOwner()) return;
        if (panning) { renderer.getViewport().pan(x - panX, y - panY); panX = x; panY = y; }
        renderer.getViewport().moveCursor(x, y); renderer.updateProjection(); sim.repaint();
    }
    boolean beginPan(int button, boolean shift, int x, int y) {
        if (!isCurrentPhysicalActionable() || !renderer.getViewport().contains(x, y) ||
                renderer.getViewport().isInspecting()) return false;
        if (button != NativeEvent.BUTTON_MIDDLE && !(button == NativeEvent.BUTTON_LEFT && shift)) return false;
        panning = true; panX = x; panY = y; return true;
    }
    void endPan() { panning = false; }
    void wheel(int delta, int x, int y) {
        if (!isCurrentPhysicalActionable() || sim.dialogIsShowing()) return;
        renderer.getViewport().zoom(Math.pow(1.12, Math.max(-3, Math.min(3, -delta))), x, y);
        renderer.updateProjection(); sim.repaint();
    }
    boolean space(boolean down, int x, int y) {
        if (!down) { cancelViewGesture(); sim.repaint(); return true; }
        if (!isCurrentPhysicalActionable() || sim.dialogIsShowing()) return false;
        boolean handled = renderer.getViewport().inspect(x, y);
        renderer.updateProjection(); sim.repaint(); return handled;
    }
    private void viewChanged() { renderer.updateProjection(); sim.repaint(); }
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
        controls.add(viewButton("Fit board", new Runnable() { public void run() { renderer.getViewport().fitBoard(); }}));
        controls.add(viewButton("Fit selection", new Runnable() { public void run() {
            PcbComponentPlacement selected = renderer.getLayoutForProvider().getComponent(renderer.getSelectedComponentId());
            if (selected != null) renderer.getViewport().fit(selected.getRoutingCourtyard());
            else viewFeedback.setText("Select a component first.");
        }}));
        viewPanel.add(controls);
        VerticalPanel zoom = new VerticalPanel();
        zoom.add(viewButton("Zoom +", new Runnable() { public void run() { zoomFromButton(1.5); }}));
        zoom.add(viewButton("Zoom -", new Runnable() { public void run() { zoomFromButton(1 / 1.5); }}));
        zoom.add(viewButton(renderer.getViewingFace() == PcbBoardSide.TOP ? "View bottom copper" : "View top copper",
            new Runnable() { public void run() {
                renderer.setViewingFace(renderer.getViewingFace().opposite()); rebuildViewPanel();
            }}));
        viewPanel.add(zoom);
        viewPanel.add(new Label("Wheel: zoom. Shift-drag or middle-drag: pan. Hold Space: inspection loupe."));
        viewPanel.add(new Label(renderer.getViewingFace() == PcbBoardSide.TOP ? "Top side / top copper" : "Bottom side / bottom copper"));
        final Vector<PcbLayoutRegion> regions = renderer.getLayoutForProvider().getRegions();
        if (!regions.isEmpty()) {
            final ListBox regionChoice = new ListBox();
            regionChoice.getElement().setAttribute("aria-label", "Inspect functional region");
            regionChoice.addItem("Inspect functional region");
            for (PcbLayoutRegion region : regions) regionChoice.addItem(region.label);
            regionChoice.addChangeHandler(new ChangeHandler() { public void onChange(ChangeEvent event) {
                int index = regionChoice.getSelectedIndex() - 1;
                if (!isCurrentOwner() || index < 0) return;
                cancelViewGesture(); renderer.getViewport().fit(regions.get(index).bounds(renderer.getLayoutForProvider()));
                viewChanged();
            }});
            viewPanel.add(regionChoice);
        }
        final ListBox components = new ListBox();
        components.getElement().setAttribute("aria-label", "Inspect component");
        components.addItem("Inspect component", "");
        Vector<String> ids = instance.getBoard().getComponentIds(); java.util.Collections.sort(ids);
        for (String id : ids) components.addItem(instance.getBoard().getComponent(id).getDisplayName(), id);
        components.addChangeHandler(new ChangeHandler() { public void onChange(ChangeEvent event) {
            if (!isCurrentOwner()) return;
            String id = components.getValue(components.getSelectedIndex());
            PcbComponentPlacement part = renderer.getLayoutForProvider().getComponent(id);
            if (part == null) return;
            cancelViewGesture(); renderer.setViewingFace(part.getMountingSide());
            renderer.setSelectedComponentId(id); renderer.getViewport().fit(part.getRoutingCourtyard());
            rebuildPanel(); rebuildViewPanel(); viewChanged();
        }});
        viewPanel.add(components);
        final ListBox pads = new ListBox();
        pads.getElement().setAttribute("aria-label", "Inspect terminal");
        pads.addItem("Inspect terminal", "");
        for (String id : ids) {
            BoardComponent part = instance.getBoard().getComponent(id);
            for (String padId : part.getPadIds()) if (renderer.canProbePad(padId))
                pads.addItem(part.getDisplayName() + " terminal " + instance.getBoard().getPad(padId).getTerminalId(), padId);
        }
        pads.addChangeHandler(new ChangeHandler() { public void onChange(ChangeEvent event) {
            if (!isCurrentOwner()) return;
            PcbPadPlacement pad = renderer.getLayoutForProvider().getPad(pads.getValue(pads.getSelectedIndex()));
            if (pad == null) return;
            cancelViewGesture(); renderer.getViewport().fit(new Rectangle(pad.getX() - 80, pad.getY() - 80, 160, 160));
            viewChanged();
        }});
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
            viewFeedback.setText("That terminal is outside this view or covered. Inspect it first."); return;
        }
        sim.instrumentController.handlePointerInput(button, target); sim.repaint();
    }
    private static native JavaScriptObject installViewListeners(PcbWorkbenchController owner,
            com.google.gwt.dom.client.Element canvas) /*-{
        var cancel = $entry(function(e) {
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
        String partId = renderer.findPartId(x, y);
        if (partId != null) {
            renderer.setSelectedPartId(partId);
            renderer.setSelectedComponentId(null);
            rebuildPanel();
            rebuildPartsPanel();
            sim.repaint();
            return true;
        }
        String componentId = renderer.findComponentId(x, y);
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
        addActions(componentId, bindings, powered || preparationDisabled);
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
        for (WorkbenchPartsProvider provider : providers)
            powerWarningAdded = addCatalog(provider, powered, powerWarningAdded);

        partsPanel.add(styledLabel("Parts Tray", "tsj-component-title"));
        Vector<PhysicalPart<?>> looseParts = getLooseParts(providers);
        if (looseParts.isEmpty())
            partsPanel.add(new Label("No removed parts."));
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
            addSelectedPartControls(selectedProvider, selectedPartId);
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

    private void addLoosePartButton(PhysicalPart<?> part) {
        WorkbenchPartsProvider provider = instance.getPhysicalBoardRuntime()
            .getWorkbenchPartsProviderForPart(part.getId());
        if (provider == null)
            throw new IllegalStateException("Loose part has no workbench provider: " + part.getId());
        Button select = new Button(provider.getPartLabel(part));
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

    private void addSelectedPartControls(final WorkbenchPartsProvider provider,
            final String selectedPartId) {
        final PhysicalPart<?> part = provider.getPart(selectedPartId);
        final String componentId = provider.getComponentId();
        partsPanel.add(new Label("Selected: " + provider.getPartLabel(part)));
        partsPanel.add(new Label("State: Loose"));
        final WorkbenchOperation installOperation = WorkbenchOperation.forPartAtSlot(
            WorkbenchOperation.INSTALL, part, componentId);
        Button install = new Button(operationLabel(part, installOperation,
            "Install as " + playerComponentName(componentId)));
        install.setStyleName("tsj-action-button");
        install.setEnabled(getCapability(part, installOperation) != null &&
            isOperationAvailable(part, installOperation));
        ClickHandler installHandler = new ClickHandler() {
            public void onClick(ClickEvent event) {
                if (!isCurrentPhysicalActionable())
                    return;
                try {
                    if (dispatchOperation(part, installOperation))
                        renderer.setSelectedPartId(null);
                } catch (BoardModificationRejectedException exception) {
                    feedback.setText("Turn board power off before modifying components.");
                }
                if (!isCurrentOwner())
                    return;
                refresh();
                sim.repaint();
            }
        };
        lastPhysicalActionHandler = installHandler;
        install.addClickHandler(installHandler);
        partsPanel.add(install);

        final WorkbenchOperation inspectOperation =
            WorkbenchOperation.forPart(WorkbenchOperation.INSPECT_LOOSE, part);
        final WorkbenchCapabilityStrategy inspectCapability = getCapability(part,
            inspectOperation);
        if (inspectCapability != null) {
            Button inspect = new Button(inspectCapability.getOperationLabel(inspectOperation));
            inspect.setStyleName("tsj-action-button");
            inspect.setEnabled(isOperationAvailable(part, inspectOperation));
            ClickHandler inspectHandler = new ClickHandler() {
                public void onClick(ClickEvent event) {
                    if (!isCurrentPhysicalActionable())
                        return;
                    if (dispatchOperation(part, inspectOperation))
                        feedback.setText("Inspection: " + provider.getPartLabel(part));
                    if (!isCurrentOwner())
                        return;
                    refresh();
                    sim.repaint();
                }
            };
            inspect.addClickHandler(inspectHandler);
            partsPanel.add(inspect);
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
        if (quickPlay) {
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
        if (retestOperation != null) {
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

    private void addActions(final String componentId,
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
                addAction(operationLabel(part, operation, "Lift lead " + pad.getTerminalId()),
                    disabled || !isOperationAvailable(part, operation), new ComponentAction() {
                    public void execute() {
                        dispatchOperation(part, operation);
                    }
                });
            }
            addRemoveAction(componentId, part, disabled);
        } else if (state == ComponentPhysicalState.LEAD_LIFTED) {
            for (final GeneratedComponentConnectionBinding binding : bindings) {
                final BoardPad pad = instance.getBoard().getPad(binding.getPadId());
                if (modifications.isLeadConnected(componentId, binding.getPadId())) {
                    final WorkbenchOperation operation = part == null ?
                        WorkbenchOperation.forComponentLead(WorkbenchOperation.LIFT_LEAD,
                            componentId, binding.getPadId()) :
                        WorkbenchOperation.forPartLead(WorkbenchOperation.LIFT_LEAD, part,
                            componentId, binding.getPadId());
                    addAction(operationLabel(part, operation, "Lift lead " + pad.getTerminalId()),
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
                    addAction(operationLabel(part, operation,
                            "Reconnect lead " + pad.getTerminalId()),
                        disabled || !isOperationAvailable(part, operation),
                        new ComponentAction() {
                            public void execute() {
                                dispatchOperation(part, operation);
                            }
                        });
                }
            }
            addRemoveAction(componentId, part, disabled);
            addRestoreAction(componentId, part, disabled);
        } else {
            addRestoreAction(componentId, part, disabled);
        }
    }

    private void addRemoveAction(final String componentId, final PhysicalPart part,
            boolean disabled) {
        final WorkbenchOperation operation = part == null ?
            WorkbenchOperation.forComponent(WorkbenchOperation.REMOVE, componentId) :
            WorkbenchOperation.forPart(WorkbenchOperation.REMOVE, part);
        addAction(operationLabel(part, operation, "Remove component"),
            disabled || !isOperationAvailable(part, operation),
            new ComponentAction() {
            public void execute() {
                dispatchOperation(part, operation);
            }
        });
    }

    private void addRestoreAction(final String componentId, final PhysicalPart part,
            boolean disabled) {
        final WorkbenchOperation operation = part == null ?
            WorkbenchOperation.forComponent(WorkbenchOperation.RESTORE, componentId) :
            WorkbenchOperation.forPart(WorkbenchOperation.RESTORE, part);
        addAction(operationLabel(part, operation, "Restore component"),
            disabled || !isOperationAvailable(part, operation),
            new ComponentAction() {
            public void execute() {
                dispatchOperation(part, operation);
            }
        });
    }

    private void addAction(final String text, boolean disabled, final ComponentAction action) {
        Button button = new Button(text);
        button.setStyleName("tsj-action-button");
        button.setEnabled(!disabled);
        ClickHandler actionHandler = new ClickHandler() {
            public void onClick(ClickEvent event) {
                if (!isCurrentPhysicalActionable())
                    return;
                try {
                    action.execute();
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
        panel.add(button);
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

    private interface ComponentAction { void execute(); }
}
