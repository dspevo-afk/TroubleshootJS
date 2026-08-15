package com.lushprojects.circuitjs1.client;

import java.util.Vector;

import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.user.client.ui.Button;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.ListBox;
import com.google.gwt.user.client.ui.VerticalPanel;

class PcbWorkbenchController {
    private final CirSim sim;
    private final GeneratedBoardInstance instance;
    private final BoardModificationController modifications;
    private final PcbWorkbenchRenderer renderer;
    private final VerticalPanel panel = new VerticalPanel();
    private final VerticalPanel ticketPanel = new VerticalPanel();
    private final VerticalPanel partsPanel = new VerticalPanel();
    private final Label feedback = new Label();

    PcbWorkbenchController(CirSim sim, GeneratedBoardInstance instance,
            BoardModificationController modifications, PcbBoardLayout layout,
            VerticalPanel sidebar) {
        this.sim = sim;
        this.instance = instance;
        this.modifications = modifications;
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

    PcbWorkbenchRenderer getRenderer() { return renderer; }

    private void rebuildPanel() {
        panel.clear();
        String componentId = renderer.getSelectedComponentId();
        panel.setVisible(componentId != null);
        if (componentId == null)
            return;
        BoardComponent component = instance.getBoard().getComponent(componentId);
        panel.add(styledLabel(component.getId(), "tsj-component-title"));
        panel.add(new Label("Type: " + component.getType().toLowerCase()));
        ResistorNameplate nameplate = "R1".equals(componentId) && !LedIndicatorFamilyState.require(instance).getR1Slot().isEmpty() ?
            LedIndicatorFamilyState.require(instance).getR1Slot().getInstalledPart().getNameplate() : instance.getPhysicalSpecifications()
                .getResistorNameplate(componentId);
        if (nameplate != null)
            panel.add(new Label("Value: " + nameplate.getDisplayValue()));
        Vector<GeneratedComponentConnectionBinding> bindings =
            instance.getConnectionBindings().getForComponentOrEmpty(componentId);
        if ("R1".equals(componentId) && LedIndicatorFamilyState.require(instance).getR1Slot().isEmpty())
            panel.add(new Label("State: R1 slot empty"));
        else if (!bindings.isEmpty())
            panel.add(new Label("State: " + formatState(modifications.getComponentState(componentId))));
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
        partsPanel.add(styledLabel("Replacement Catalog", "tsj-component-title"));
        final ListBox catalog = new ListBox();
        for (ResistorCatalogEntry entry : LedIndicatorFamilyState.require(instance).getResistorCatalog().getEntries())
            catalog.addItem(entry.getNameplate().getDisplayValue(), entry.getId());
        boolean canInstallNew = sim.isChallengeInteractionEnabled() &&
            sim.getBoardPowerController().isElectricallyUnpowered() && LedIndicatorFamilyState.require(instance).getR1Slot().isEmpty();
        catalog.setEnabled(canInstallNew);
        partsPanel.add(catalog);
        Button installNew = new Button("Install new resistor");
        installNew.setStyleName("tsj-action-button");
        installNew.setEnabled(canInstallNew);
        installNew.addClickHandler(new ClickHandler() {
            public void onClick(ClickEvent event) {
                try {
                    sim.getResistorSlotController().installNewFromCatalog(
                        catalog.getValue(catalog.getSelectedIndex()));
                    renderer.setSelectedPartId(null);
                } catch (BoardModificationRejectedException exception) {
                    feedback.setText("Turn board power off.");
                }
                refresh();
                sim.repaint();
            }
        });
        partsPanel.add(installNew);
        if (!sim.getBoardPowerController().isElectricallyUnpowered())
            partsPanel.add(new Label("Turn board power off."));
        else if (!LedIndicatorFamilyState.require(instance).getR1Slot().isEmpty())
            partsPanel.add(new Label("Remove R1 before installing a replacement."));
        partsPanel.add(styledLabel("Parts Tray", "tsj-component-title"));
        Vector<PhysicalResistorPart> loose = LedIndicatorFamilyState.require(instance).getResistorInventory().getLooseParts();
        if (loose.isEmpty())
            partsPanel.add(new Label("No removed parts."));
        int start = renderer.getTrayPage() * 3;
        for (int index = start; index < loose.size() && index < start + 3; index++) {
            PhysicalResistorPart part = loose.get(index);
            Button select = new Button(part.getNameplate().getDisplayValue());
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
        if (renderer.getTrayPageCount() > 1) {
            partsPanel.add(new Label("Page " + (renderer.getTrayPage() + 1) + " of " +
                renderer.getTrayPageCount()));
            Button previous = new Button("Previous");
            previous.setEnabled(renderer.getTrayPage() > 0);
            previous.addClickHandler(new ClickHandler() {
                public void onClick(ClickEvent event) { renderer.setTrayPage(renderer.getTrayPage() - 1); refresh(); sim.repaint(); }
            });
            Button next = new Button("Next");
            next.setEnabled(renderer.getTrayPage() + 1 < renderer.getTrayPageCount());
            next.addClickHandler(new ClickHandler() {
                public void onClick(ClickEvent event) { renderer.setTrayPage(renderer.getTrayPage() + 1); refresh(); sim.repaint(); }
            });
            partsPanel.add(previous);
            partsPanel.add(next);
        }
        final String selectedPartId = renderer.getSelectedPartId();
        if (selectedPartId == null)
            return;
        PhysicalResistorPart part = LedIndicatorFamilyState.require(instance).getResistorInventory().get(selectedPartId);
        partsPanel.add(new Label("Selected: " + part.getNameplate().getDisplayValue()));
        partsPanel.add(new Label("State: Loose"));
        Button install = new Button("Install as R1");
        install.setStyleName("tsj-action-button");
        install.setEnabled(sim.isChallengeInteractionEnabled() &&
            sim.getBoardPowerController().isElectricallyUnpowered() && LedIndicatorFamilyState.require(instance).getR1Slot().isEmpty());
        install.addClickHandler(new ClickHandler() {
            public void onClick(ClickEvent event) {
                try {
                    if (sim.getResistorSlotController().install(selectedPartId))
                        renderer.setSelectedPartId(null);
                } catch (BoardModificationRejectedException exception) {
                    feedback.setText("Turn board power off before modifying components.");
                }
                refresh();
                sim.repaint();
            }
        });
        partsPanel.add(install);
    }

    private void rebuildTicket() {
        ticketPanel.clear();
        GeneratedChallengeController challenge = sim.getGeneratedChallengeController();
        ticketPanel.setVisible(challenge != null);
        if (challenge == null)
            return;
        ticketPanel.add(styledLabel("Service Ticket", "tsj-component-title"));
        ticketPanel.add(new Label(challenge.isReady() ? challenge.getComplaintText() :
            "Preparing challenge..."));
    }

    private void addActions(final String componentId,
            Vector<GeneratedComponentConnectionBinding> bindings, boolean disabled) {
        if ("R1".equals(componentId) && LedIndicatorFamilyState.require(instance).getR1Slot().isEmpty())
            return;
        ComponentPhysicalState state = modifications.getComponentState(componentId);
        if (state == ComponentPhysicalState.INSTALLED) {
            for (final GeneratedComponentConnectionBinding binding : bindings) {
                BoardPad pad = instance.getBoard().getPad(binding.getPadId());
                addAction("Lift lead " + pad.getTerminalId(), disabled, new ComponentAction() {
                    public void execute() {
                        modifications.liftLead(componentId, binding.getPadId());
                    }
                });
            }
            addRemoveAction(componentId, disabled);
        } else if (state == ComponentPhysicalState.LEAD_LIFTED) {
            for (final GeneratedComponentConnectionBinding binding : bindings) {
                final BoardPad pad = instance.getBoard().getPad(binding.getPadId());
                if (modifications.isLeadConnected(componentId, binding.getPadId())) {
                    addAction("Lift lead " + pad.getTerminalId(), disabled, new ComponentAction() {
                        public void execute() {
                            modifications.liftLead(componentId, binding.getPadId());
                        }
                    });
                } else {
                    addAction("Reconnect lead " + pad.getTerminalId(), disabled,
                        new ComponentAction() {
                            public void execute() {
                                modifications.reconnectLead(componentId, binding.getPadId());
                            }
                        });
                }
            }
            addRemoveAction(componentId, disabled);
            addRestoreAction(componentId, disabled);
        } else {
            addRestoreAction(componentId, disabled);
        }
    }

    private void addRemoveAction(final String componentId, boolean disabled) {
        addAction("Remove component", disabled, new ComponentAction() {
            public void execute() {
                if ("R1".equals(componentId))
                    sim.getResistorSlotController().removeInstalledPart();
                else
                    modifications.removeComponent(componentId);
            }
        });
    }

    private void addRestoreAction(final String componentId, boolean disabled) {
        addAction("Restore component", disabled, new ComponentAction() {
            public void execute() {
                if (!"R1".equals(componentId))
                    modifications.restoreComponent(componentId);
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

    private interface ComponentAction { void execute(); }
}
