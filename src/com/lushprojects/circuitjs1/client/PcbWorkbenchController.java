package com.lushprojects.circuitjs1.client;

import java.util.Vector;

import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.user.client.ui.Button;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.VerticalPanel;

class PcbWorkbenchController {
    private final CirSim sim;
    private final GeneratedBoardInstance instance;
    private final BoardModificationController modifications;
    private final PcbWorkbenchRenderer renderer;
    private final VerticalPanel panel = new VerticalPanel();
    private final VerticalPanel ticketPanel = new VerticalPanel();
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
    }

    void draw(Graphics graphics, Rectangle area) { renderer.draw(graphics, area); }

    ProbeTarget findProbeTarget(int x, int y) { return renderer.findProbeTarget(sim, x, y); }

    boolean selectComponentAt(int x, int y) {
        if (!sim.isChallengeInteractionEnabled())
            return false;
        String componentId = renderer.findComponentId(x, y);
        renderer.setSelectedComponentId(componentId);
        rebuildPanel();
        sim.repaint();
        return componentId != null;
    }

    void refresh() {
        rebuildTicket();
        rebuildPanel();
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
        ResistorNameplate nameplate = instance.getPhysicalSpecifications()
            .getResistorNameplate(componentId);
        if (nameplate != null)
            panel.add(new Label("Value: " + nameplate.getDisplayValue()));
        Vector<GeneratedComponentConnectionBinding> bindings =
            instance.getConnectionBindings().getForComponentOrEmpty(componentId);
        if (!bindings.isEmpty())
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
            public void execute() { modifications.removeComponent(componentId); }
        });
    }

    private void addRestoreAction(final String componentId, boolean disabled) {
        addAction("Restore component", disabled, new ComponentAction() {
            public void execute() { modifications.restoreComponent(componentId); }
        });
    }

    private void addAction(String text, boolean disabled, final ComponentAction action) {
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
                rebuildPanel();
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
