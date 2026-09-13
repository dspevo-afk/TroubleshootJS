package com.lushprojects.circuitjs1.client;

import java.util.Vector;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.event.dom.client.ChangeEvent;
import com.google.gwt.event.dom.client.ChangeHandler;
import com.google.gwt.user.client.Timer;
import com.google.gwt.user.client.ui.Button;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.ListBox;
import com.google.gwt.user.client.ui.VerticalPanel;

/** Owner-bound public controls and actual terminal/current observations. */
final class BenchPowerPanel extends VerticalPanel {
    private final CirSim sim;
    private final GeneratedBoardInstance owner;
    private final Vector<Label> readings = new Vector<Label>();
    private final Vector<Button> switches = new Vector<Button>();
    private final Vector<ListBox> limits = new Vector<ListBox>();
    private final Vector<String> inputs = new Vector<String>();
    private final Timer timer = new Timer() { public void run() { refreshReadings(); } };
    private static final int[] MILLIAMPS = {1, 10, 25, 100, 250, 500};

    BenchPowerPanel(final CirSim sim, final GeneratedBoardInstance owner) {
        this.sim = sim; this.owner = owner;
        setStyleName("tsj-component-panel");
        add(new Label("BENCH POWER"));
        for (final String id : owner.getBoard().getPowerInputIds()) {
            final ExternalPowerSimulationBinding binding = owner.getExternalPowerBindings().getBinding(id);
            if (binding.getLimitedSupply() == null) continue;
            inputs.add(id);
            final Label reading = new Label(); readings.add(reading); add(reading);
            final Button toggle = new Button(); switches.add(toggle); add(toggle);
            toggle.addClickHandler(new ClickHandler() { public void onClick(ClickEvent event) {
                if (actionable()) sim.changeBenchSource(owner, id, !binding.isConnected(), null);
                refreshReadings();
            }});
            final ListBox limit = new ListBox(); limits.add(limit); add(limit);
            for (int ma : MILLIAMPS) limit.addItem("Limit: " + ma + " mA", Integer.toString(ma));
            limit.getElement().setAttribute("aria-label", label(id) + " current limit");
            limit.addChangeHandler(new ChangeHandler() { public void onChange(ChangeEvent event) {
                if (actionable()) sim.changeBenchSource(owner, id, null,
                    Double.parseDouble(limit.getSelectedValue()) / 1000);
                refreshReadings();
            }});
        }
        add(new Label("Each output disconnects independently. Fixed nominal voltage; reverse blocking has small leakage."));
        setVisible(!inputs.isEmpty());
    }
    private String label(String id) {
        PowerInputNameplate name = owner.getPhysicalSpecifications().getPowerInputNameplate(id);
        return "Supply " + (owner.getBoard().getPowerInputIds().indexOf(id) + 1) +
            (name == null ? "" : " (" + name.getDisplayLabel() + ")");
    }
    private boolean actionable() {
        return sim.getGeneratedBoardInstance() == owner && sim.isChallengeInteractionEnabled() &&
            !sim.activeMeasurementOverlay;
    }
    void start() { refreshReadings(); timer.scheduleRepeating(250); }
    void stop() { timer.cancel(); }
    void refreshReadings() {
        if (sim.getGeneratedBoardInstance() != owner) { stop(); return; }
        for (int i = 0; i < inputs.size(); i++) {
            String id = inputs.get(i);
            ExternalPowerSimulationBinding binding = owner.getExternalPowerBindings().getBinding(id);
            LimitedDcSupplyElm source = binding.getLimitedSupply();
            boolean connected = binding.isConnected();
            String value = !connected ? "disconnected" : !sim.isGeneratedRuntimeSettled() ? "settling" :
                CircuitElm.getVoltageText(source.getOutputVoltage()) + ", " + CircuitElm.getCurrentText(source.getCurrent()) +
                (source.isLimiting() ? " (current limit)" : "");
            readings.get(i).setText(label(id) + ": " + value);
            switches.get(i).setText((connected ? "Disconnect " : "Connect ") + label(id));
            switches.get(i).setEnabled(actionable()); limits.get(i).setEnabled(actionable());
            for (int j = 0; j < MILLIAMPS.length; j++)
                if (Math.abs(source.getLimitAmps() * 1000 - MILLIAMPS[j]) < .001) limits.get(i).setSelectedIndex(j);
        }
    }
}
