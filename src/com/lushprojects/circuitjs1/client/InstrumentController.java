package com.lushprojects.circuitjs1.client;

import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.dom.client.NativeEvent;
import com.google.gwt.user.client.ui.Button;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.VerticalPanel;

class InstrumentController {
    private static final int MODE_NONE = 0;
    private static final int MODE_DC_VOLTAGE = 1;
    private static final int PROBE_MARKER_RADIUS = 5;

    private final CirSim sim;
    private final CircuitMeasurementAdapter measurementAdapter;
    private final Button dcVoltageButton;
    private final Label readingLabel;
    private int activeMode = MODE_NONE;
    private ProbeTarget redProbe;
    private ProbeTarget blackProbe;

    InstrumentController(final CirSim sim, VerticalPanel panel) {
        this.sim = sim;
        measurementAdapter = new CircuitMeasurementAdapter(sim);
        dcVoltageButton = new Button("DC V");
        dcVoltageButton.addStyleName("chbut");
        readingLabel = new Label("--- V");
        panel.add(dcVoltageButton);
        panel.add(readingLabel);

        dcVoltageButton.addClickHandler(new ClickHandler() {
            public void onClick(ClickEvent event) {
                setActiveMode(activeMode == MODE_DC_VOLTAGE ? MODE_NONE : MODE_DC_VOLTAGE);
            }
        });
    }

    boolean isHandlingPointerInput() {
        return activeMode != MODE_NONE;
    }

    void handlePointerInput(int button, int screenX, int screenY) {
        CircuitPostProbeTarget target = sim.findPostTarget(screenX, screenY);
        if (target != null) {
            if (button == NativeEvent.BUTTON_LEFT)
                redProbe = target;
            else if (button == NativeEvent.BUTTON_RIGHT)
                blackProbe = target;
        }
        updateReading();
        sim.repaint();
    }

    void clearTargets() {
        redProbe = null;
        blackProbe = null;
        updateReading();
    }

    void draw(Graphics graphics) {
        validateTargets();
        drawProbe(graphics, redProbe, Color.red);
        drawProbe(graphics, blackProbe, Color.black);
        updateReading();
    }

    private void setActiveMode(int mode) {
        activeMode = mode;
        dcVoltageButton.setStyleName("chsel", activeMode == MODE_DC_VOLTAGE);
        sim.repaint();
    }

    private void drawProbe(Graphics graphics, ProbeTarget target, Color color) {
        if (target == null)
            return;
        Point point = target.getMarkerPoint();
        graphics.setColor(Color.white);
        graphics.fillOval(point.x - PROBE_MARKER_RADIUS, point.y - PROBE_MARKER_RADIUS,
            PROBE_MARKER_RADIUS * 2 + 1, PROBE_MARKER_RADIUS * 2 + 1);
        graphics.setColor(color);
        graphics.fillOval(point.x - 3, point.y - 3, 7, 7);
    }

    private void updateReading() {
        validateTargets();
        if (redProbe == null || blackProbe == null) {
            readingLabel.setText("--- V");
            return;
        }
        readingLabel.setText(CircuitElm.getVoltageText(
            measurementAdapter.getDcVoltageDifference(redProbe, blackProbe)));
    }

    private void validateTargets() {
        if (redProbe != null && !redProbe.isValid())
            redProbe = null;
        if (blackProbe != null && !blackProbe.isValid())
            blackProbe = null;
    }
}