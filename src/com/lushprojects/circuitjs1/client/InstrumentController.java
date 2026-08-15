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
    private static final int MODE_RESISTANCE = 2;
    private static final double MAX_RESISTANCE = 10000000;
    private static final int PROBE_MARKER_RADIUS = 5;

    private final CirSim sim;
    private final CircuitMeasurementAdapter measurementAdapter;
    private final Button dcVoltageButton;
    private final Button resistanceButton;
    private final Label readingLabel;
    private int activeMode = MODE_NONE;
    private ProbeTarget redProbe;
    private ProbeTarget blackProbe;
    private boolean resistanceReadingDirty;
    private double latestResistanceReading = Double.NaN;

    InstrumentController(final CirSim sim, VerticalPanel panel) {
        this.sim = sim;
        measurementAdapter = new CircuitMeasurementAdapter(sim);
        dcVoltageButton = new Button("DC V");
        dcVoltageButton.addStyleName("chbut");
        readingLabel = new Label("--- V");
        panel.add(dcVoltageButton);
        resistanceButton = new Button("OHM");
        resistanceButton.addStyleName("chbut");
        panel.add(resistanceButton);
        panel.add(readingLabel);

        dcVoltageButton.addClickHandler(new ClickHandler() {
            public void onClick(ClickEvent event) {
                setActiveMode(activeMode == MODE_DC_VOLTAGE ? MODE_NONE : MODE_DC_VOLTAGE);
            }
        });

        resistanceButton.addClickHandler(new ClickHandler() {
            public void onClick(ClickEvent event) {
                setActiveMode(activeMode == MODE_RESISTANCE ? MODE_NONE : MODE_RESISTANCE);
                updateReading();
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
        resistanceReadingDirty = true;
        updateReading();
        sim.repaint();
    }

    void clearTargets() {
        redProbe = null;
        blackProbe = null;
    resistanceReadingDirty = true;
        updateReading();
    }

    void refreshActiveMeasurement() {
        resistanceReadingDirty = true;
        updateReading();
    }

    void invalidateActiveMeasurement() {
        resistanceReadingDirty = true;
        if (activeMode == MODE_RESISTANCE) {
            latestResistanceReading = Double.NaN;
            readingLabel.setText("--- Ohm");
        }
    }

    void setResistanceProbesForDeveloperVerification(ProbeTarget red, ProbeTarget black) {
        setActiveMode(MODE_RESISTANCE);
        redProbe = red;
        blackProbe = black;
        resistanceReadingDirty = true;
        updateReading();
    }

    String getReadingForDeveloperVerification() {
        return readingLabel.getText();
    }

    double getLatestResistanceReadingForDeveloperVerification() {
        return latestResistanceReading;
    }

    void draw(Graphics graphics) {
        validateTargets();
        drawProbe(graphics, redProbe, Color.red);
        drawProbe(graphics, blackProbe, Color.black);
        if (activeMode == MODE_DC_VOLTAGE)
            updateReading();
    }

    private void setActiveMode(int mode) {
        activeMode = mode;
        resistanceReadingDirty = true;
        dcVoltageButton.setStyleName("chsel", activeMode == MODE_DC_VOLTAGE);
    resistanceButton.setStyleName("chsel", activeMode == MODE_RESISTANCE);
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
            latestResistanceReading = Double.NaN;
            readingLabel.setText("--- V");
            return;
        }
        if (activeMode == MODE_RESISTANCE) {
            if (resistanceReadingDirty)
                updateResistanceReading();
            return;
        }
        double voltage = measurementAdapter.getDcVoltageDifference(redProbe, blackProbe);
        if (Double.isNaN(voltage)) {
            readingLabel.setText("--- V");
            return;
        }
        readingLabel.setText(CircuitElm.getVoltageText(voltage));
    }

    private void updateResistanceReading() {
        resistanceReadingDirty = false;
        if (!measurementAdapter.isActiveMeasurementAllowed(redProbe, blackProbe)) {
            latestResistanceReading = Double.NaN;
            readingLabel.setText("POWER OFF");
            return;
        }
        double resistance = measurementAdapter.measureResistance(redProbe, blackProbe);
        latestResistanceReading = resistance;
        if (Double.isNaN(resistance) || Double.isInfinite(resistance) ||
            resistance > MAX_RESISTANCE) {
            readingLabel.setText("OL");
            return;
        }
        readingLabel.setText(CircuitElm.getUnitText(resistance, "Ohm"));
    }

    private void validateTargets() {
        if (redProbe != null && !redProbe.isValid())
            redProbe = null;
        if (blackProbe != null && !blackProbe.isValid())
            blackProbe = null;
    }
}