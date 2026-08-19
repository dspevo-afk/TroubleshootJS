package com.lushprojects.circuitjs1.client;

import java.util.Vector;

import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.event.dom.client.ChangeEvent;
import com.google.gwt.event.dom.client.ChangeHandler;
import com.google.gwt.user.client.ui.Button;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.ListBox;
import com.google.gwt.user.client.ui.VerticalPanel;
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
    private final boolean quickPlay;
    private String finishFeedbackText = "";
    private String customerRetestFeedbackText = "";

    PcbWorkbenchController(CirSim sim, GeneratedBoardInstance instance,
            BoardModificationController modifications, PcbBoardLayout layout,
            VerticalPanel sidebar, boolean quickPlay) {
        this.sim = sim;
        this.instance = instance;
        this.modifications = modifications;
        this.quickPlay = quickPlay;
        renderer = new PcbWorkbenchRenderer(instance, modifications, layout);
        ticketPanel.setStyleName("tsj-component-panel");
        ticketPanel.setVisible(false);
        sidebar.add(ticketPanel);
        panel.setStyleName("tsj-component-panel");
        panel.setVisible(false);
        sidebar.add(panel);
        partsPanel.setStyleName("tsj-component-panel");
        sidebar.add(partsPanel);
    }

    void draw(Graphics graphics, Rectangle area) { renderer.draw(graphics, area); }

    ProbeTarget findProbeTarget(int x, int y) { return renderer.findProbeTarget(sim, x, y); }

    boolean selectComponentAt(int x, int y) {
        if (!sim.isChallengeInteractionEnabled())
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
        rebuildTicket();
        rebuildPanel();
        rebuildPartsPanel();
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
        WorkbenchCapabilityStrategy capability = getCapability(part, operation);
        return capability != null && capability.isAvailable(operation, this);
    }

    private boolean dispatchOperation(PhysicalPart part, WorkbenchOperation operation) {
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
        panel.add(styledLabel(component.getId(), "tsj-component-title"));
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
            panel.add(new Label("State: " + componentId + " slot empty"));
        else if (!bindings.isEmpty())
            panel.add(new Label("State: " + formatState(modifications.getComponentState(componentId))));
        else if (nameplate != null && nameplate.hasWorkbenchDetail() && partsProvider == null)
            panel.add(new Label("State: Installed"));
        for (String padId : component.getPadIds()) {
            BoardPad pad = instance.getBoard().getPad(padId);
            panel.add(new Label("Lead " + pad.getTerminalId() + ": " + pad.getId()));
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
                updateCatalogControls(provider, componentId, catalog, installNew);
            }
        });
        installNew.addClickHandler(new ClickHandler() {
            public void onClick(ClickEvent event) {
                try {
                    if (dispatchOperation(null, WorkbenchOperation.forCatalog(componentId,
                            catalog.getValue(catalog.getSelectedIndex()))))
                        renderer.setSelectedPartId(null);
                } catch (BoardModificationRejectedException exception) {
                    feedback.setText("Turn board power off.");
                }
                refresh();
                sim.repaint();
            }
        });
        partsPanel.add(installNew);
        if (powered && !powerWarningAdded) {
            partsPanel.add(new Label("Turn board power off."));
            powerWarningAdded = true;
        }
        PhysicalBoardSlot slot = instance.getPhysicalBoardRuntime().getSlot(componentId);
        if (slot != null && slot.isOccupied() && (!powered ||
                provider.showOccupiedMessageWhenPowered()))
            partsPanel.add(new Label("Remove " + componentId +
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
        select.setEnabled(sim.isChallengeInteractionEnabled());
        final String partId = part.getId();
        select.addClickHandler(new ClickHandler() {
            public void onClick(ClickEvent event) {
                renderer.setSelectedPartId(partId);
                renderer.setSelectedComponentId(null);
                rebuildPanel();
                rebuildPartsPanel();
                sim.repaint();
            }
        });
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
            "Install as " + componentId));
        install.setStyleName("tsj-action-button");
        install.setEnabled(getCapability(part, installOperation) != null &&
            isOperationAvailable(part, installOperation));
        install.addClickHandler(new ClickHandler() {
            public void onClick(ClickEvent event) {
                try {
                    if (dispatchOperation(part, installOperation))
                        renderer.setSelectedPartId(null);
                } catch (BoardModificationRejectedException exception) {
                    feedback.setText("Turn board power off before modifying components.");
                }
                refresh();
                sim.repaint();
            }
        });
        partsPanel.add(install);

        final WorkbenchOperation inspectOperation =
            WorkbenchOperation.forPart(WorkbenchOperation.INSPECT_LOOSE, part);
        final WorkbenchCapabilityStrategy inspectCapability = getCapability(part,
            inspectOperation);
        if (inspectCapability != null) {
            Button inspect = new Button(inspectCapability.getOperationLabel(inspectOperation));
            inspect.setStyleName("tsj-action-button");
            inspect.setEnabled(isOperationAvailable(part, inspectOperation));
            inspect.addClickHandler(new ClickHandler() {
                public void onClick(ClickEvent event) {
                    if (dispatchOperation(part, inspectOperation))
                        feedback.setText("Inspection: " + provider.getPartLabel(part));
                    refresh();
                    sim.repaint();
                }
            });
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
        previous.setEnabled(renderer.getTrayPage() > 0);
        previous.addClickHandler(new ClickHandler() {
            public void onClick(ClickEvent event) {
                renderer.setTrayPage(renderer.getTrayPage() - 1); refresh(); sim.repaint();
            }
        });
        Button next = new Button("Next");
        next.setEnabled(renderer.getTrayPage() + 1 < renderer.getTrayPageCount());
        next.addClickHandler(new ClickHandler() {
            public void onClick(ClickEvent event) {
                renderer.setTrayPage(renderer.getTrayPage() + 1); refresh(); sim.repaint();
            }
        });
        partsPanel.add(previous);
        partsPanel.add(next);
    }

    private void rebuildTicket() {
        ticketPanel.clear();
        GeneratedChallengeController challenge = sim.getGeneratedChallengeController();
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
            finish.setEnabled(challenge.isReady() && !challenge.isCompleted() &&
                challenge.getCustomerRetestResult() != null &&
                challenge.getCustomerRetestResult().isPassed());
            finish.addClickHandler(new ClickHandler() {
                public void onClick(ClickEvent event) {
                    if (sim.finishQuickPlayJob()) {
                        finishFeedbackText = "";
                        Window.Location.reload();
                    } else {
                        finishFeedbackText =
                            "Functional check failed. Continue troubleshooting.";
                        rebuildTicket();
                    }
                }
            });
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
            command.setEnabled(challenge.isReady());
            final String operationId = operation.getStableId();
            command.addClickHandler(new ClickHandler() {
                public void onClick(ClickEvent event) {
                    if (sim.invokeGeneratedPlayerOperation(operationId)) {
                        customerRetestFeedbackText = "";
                        refresh();
                    }
                    sim.repaint();
                }
            });
            ticketPanel.add(command);
        }
        final GeneratedBoardOperation retestOperation = instance.getOperationCatalog().find(
            GeneratedBoardOperationIds.CUSTOMER_RETEST);
        if (retestOperation != null) {
            Button retest = new Button(retestOperation.getPlayerLabel());
            retest.setStyleName("tsj-action-button");
            retest.setEnabled(challenge.isReady() && !challenge.isCompleted());
            retest.addClickHandler(new ClickHandler() {
                public void onClick(ClickEvent event) {
                    GeneratedCustomerRetestResult result = sim.performCustomerRetest();
                    customerRetestFeedbackText = result.getPlayerMessage();
                    refresh();
                    sim.repaint();
                }
            });
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
        button.addClickHandler(new ClickHandler() {
            public void onClick(ClickEvent event) {
                try {
                    action.execute();
                    feedback.setText("");
                } catch (BoardModificationRejectedException exception) {
                    feedback.setText("Turn board power off before modifying components.");
                }
                refresh();
                sim.repaint();
            }
        });
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
        WorkbenchCapabilityStrategy capability = getCapability(operation == null ? null :
            operation.getPart(), operation);
        return capability != null && capability.isAvailable(operation, this);
    }

    public boolean dispatch(WorkbenchOperation operation) {
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

    private interface ComponentAction { void execute(); }
}
