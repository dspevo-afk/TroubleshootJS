package com.lushprojects.circuitjs1.client;

import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.dom.client.NativeEvent;
import com.google.gwt.user.client.ui.Button;
import com.google.gwt.user.client.ui.Grid;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.VerticalPanel;

class InstrumentController {
    private static final int MODE_NONE = 0;
    private static final int MODE_DC_VOLTAGE = 1;
    private static final int MODE_RESISTANCE = 2;
    private static final int MODE_CONTINUITY = 3;
    private static final int MODE_DIODE = 4;
    private static final double MAX_RESISTANCE = 10000000;
    static final double CONTINUITY_THRESHOLD_OHMS = 50;
    static final double DIODE_MINIMUM_CURRENT = .00001;
    static final double DIODE_COMPLIANCE_THRESHOLD = 2.95;
    private static final int PROBE_MARKER_RADIUS = 5;

    private final CirSim sim;
    private final CircuitMeasurementAdapter measurementAdapter;
    private final Button dcVoltageButton;
    private final Button resistanceButton;
    private final Button continuityButton;
    private final Button diodeButton;
    private final Label readingLabel;
    private final Label continuityLabel;
    private final ContinuityFeedback continuityFeedback;
    private boolean interactionEnabled = true;
    private int activeMode = MODE_NONE;
    private ProbeTarget redProbe;
    private ProbeTarget blackProbe;
    private boolean dcVoltageRefreshPending;
    private double latestDcVoltage = Double.NaN;
    private int dcVoltageMeasurementCount;
    private boolean resistanceRefreshPending;
    private double latestResistanceReading = Double.NaN;
    private int resistanceMeasurementCount;
    private boolean continuityDetected;
    private boolean diodeRefreshPending;
    private double latestDiodeVoltage = Double.NaN;
    private double latestDiodeCurrent = Double.NaN;
    private int diodeMeasurementCount;

    InstrumentController(final CirSim sim, VerticalPanel panel) {
        this.sim = sim;
        measurementAdapter = new CircuitMeasurementAdapter(sim);
        VerticalPanel meterPanel = new VerticalPanel();
        meterPanel.setStyleName("tsj-meter-panel");
        Label meterTitle = new Label("MULTIMETER");
        meterTitle.setStyleName("tsj-section-title");
        meterPanel.add(meterTitle);
        Grid modeGrid = new Grid(2, 2);
        modeGrid.setStyleName("tsj-meter-modes");
        dcVoltageButton = new Button("DC V");
        dcVoltageButton.addStyleName("chbut");
        readingLabel = new Label("--- V");
        readingLabel.setStyleName("tsj-meter-display");
        modeGrid.setWidget(0, 0, dcVoltageButton);
        resistanceButton = new Button("OHM");
        resistanceButton.addStyleName("chbut");
        modeGrid.setWidget(0, 1, resistanceButton);
        continuityButton = new Button("CONT");
        continuityButton.addStyleName("chbut");
        modeGrid.setWidget(1, 0, continuityButton);
        diodeButton = new Button("DIODE");
        diodeButton.addStyleName("chbut");
        modeGrid.setWidget(1, 1, diodeButton);
        continuityLabel = new Label("BEEP");
        continuityLabel.setStyleName("tsj-continuity-indicator");
        continuityLabel.setVisible(false);
        meterPanel.add(modeGrid);
        meterPanel.add(readingLabel);
        meterPanel.add(continuityLabel);
        panel.add(meterPanel);
        continuityFeedback = new BrowserContinuityFeedback();

        dcVoltageButton.addClickHandler(new ClickHandler() {
            public void onClick(ClickEvent event) {
                setActiveMode(activeMode == MODE_DC_VOLTAGE ? MODE_NONE : MODE_DC_VOLTAGE);
                updateReading();
            }
        });

        resistanceButton.addClickHandler(new ClickHandler() {
            public void onClick(ClickEvent event) {
                setActiveMode(activeMode == MODE_RESISTANCE ? MODE_NONE : MODE_RESISTANCE);
                updateReading();
            }
        });

        continuityButton.addClickHandler(new ClickHandler() {
            public void onClick(ClickEvent event) {
                continuityFeedback.prepare();
                setActiveMode(activeMode == MODE_CONTINUITY ? MODE_NONE : MODE_CONTINUITY);
                updateReading();
            }
        });

        diodeButton.addClickHandler(new ClickHandler() {
            public void onClick(ClickEvent event) {
                setActiveMode(activeMode == MODE_DIODE ? MODE_NONE : MODE_DIODE);
                updateReading();
            }
        });
    }

    boolean isHandlingPointerInput() {
        return interactionEnabled && activeMode != MODE_NONE;
    }

    void setInteractionEnabled(boolean enabled) {
        interactionEnabled = enabled;
        dcVoltageButton.setEnabled(enabled);
        resistanceButton.setEnabled(enabled);
        continuityButton.setEnabled(enabled);
        diodeButton.setEnabled(enabled);
        if (!enabled)
            exitInstrumentModeForDeveloperVerification();
    }

    void handlePointerInput(int button, int screenX, int screenY) {
        handlePointerInput(button, sim.findPostTarget(screenX, screenY));
    }

    void handlePointerInput(int button, ProbeTarget target) {
        if (!interactionEnabled)
            return;
        boolean changed = false;
        if (target != null) {
            if (button == NativeEvent.BUTTON_LEFT)
                changed = redProbe == null || !redProbe.isSameTarget(target);
            else if (button == NativeEvent.BUTTON_RIGHT)
                changed = blackProbe == null || !blackProbe.isSameTarget(target);
            if (activeMode == MODE_CONTINUITY && changed)
                continuityFeedback.prepare();
            if (button == NativeEvent.BUTTON_LEFT)
                redProbe = target;
            else if (button == NativeEvent.BUTTON_RIGHT)
                blackProbe = target;
        }
        if (changed) {
            requestDcVoltageRefresh();
            requestResistanceRefresh();
            requestDiodeRefresh();
            updateReading();
        }
        sim.repaint();
    }

    void clearTargets() {
        redProbe = null;
        blackProbe = null;
        requestDcVoltageRefresh();
        requestResistanceRefresh();
        requestDiodeRefresh();
        updateReading();
    }

    void refreshActiveMeasurement() {
        requestDcVoltageRefresh();
        requestResistanceRefresh();
        requestDiodeRefresh();
    }

    void onCircuitTopologyChanged() {
        validateTargets();
        requestDcVoltageRefresh();
        requestResistanceRefresh();
        requestDiodeRefresh();
    }

    void onSimulationStepComplete(boolean didAnalyze) {
        if (sim.activeMeasurementOverlay)
            return;
        if (didAnalyze && activeMode == MODE_DC_VOLTAGE && dcVoltageRefreshPending) {
            dcVoltageRefreshPending = false;
            updateReading();
        }
        if (didAnalyze && isDiodeMode() && diodeRefreshPending) {
            validateTargets();
            if (redProbe == null || blackProbe == null) {
                diodeRefreshPending = false;
                latestDiodeVoltage = Double.NaN;
                latestDiodeCurrent = Double.NaN;
                readingLabel.setText("--- V");
            } else {
                diodeRefreshPending = false;
                updateDiodeReading();
            }
        }
        if (!didAnalyze || !isActiveResistanceMode() || !resistanceRefreshPending)
            return;
        validateTargets();
        if (redProbe == null || blackProbe == null) {
            resistanceRefreshPending = false;
            latestResistanceReading = Double.NaN;
            readingLabel.setText("--- Ohm");
            return;
        }
        resistanceRefreshPending = false;
        updateResistanceReading();
    }

    void setResistanceProbesForDeveloperVerification(ProbeTarget red, ProbeTarget black) {
        setActiveMode(MODE_RESISTANCE);
        redProbe = red;
        blackProbe = black;
        requestResistanceRefresh();
        updateReading();
    }

    String getReadingForDeveloperVerification() {
        return readingLabel.getText();
    }

    double getLatestResistanceReadingForDeveloperVerification() {
        return latestResistanceReading;
    }

    double getDcVoltageDifferenceForDeveloperVerification(ProbeTarget red, ProbeTarget black) {
        return measurementAdapter.measureDcVoltage(red, black);
    }

    double getLatestDcVoltageForDeveloperVerification() { return latestDcVoltage; }
    int getDcVoltageMeasurementCountForDeveloperVerification() { return dcVoltageMeasurementCount; }

    int getResistanceMeasurementCountForDeveloperVerification() {
        return resistanceMeasurementCount;
    }

    void setContinuityProbesForDeveloperVerification(ProbeTarget red, ProbeTarget black) {
        setActiveMode(MODE_CONTINUITY);
        redProbe = red;
        blackProbe = black;
        requestResistanceRefresh();
        updateReading();
    }

    void setDiodeProbesForDeveloperVerification(ProbeTarget red, ProbeTarget black) {
        setActiveMode(MODE_DIODE);
        redProbe = red;
        blackProbe = black;
        requestDiodeRefresh();
        updateReading();
    }

    double getLatestDiodeVoltageForDeveloperVerification() { return latestDiodeVoltage; }
    double getLatestDiodeCurrentForDeveloperVerification() { return latestDiodeCurrent; }
    int getDiodeMeasurementCountForDeveloperVerification() { return diodeMeasurementCount; }

    boolean isContinuityDetectedForDeveloperVerification() {
        return continuityDetected;
    }

    boolean isContinuityIndicatorVisibleForDeveloperVerification() {
        return continuityLabel.isVisible();
    }

    boolean isContinuityFeedbackRequestedForDeveloperVerification() {
        return continuityFeedback.isRequestedActive();
    }

    int getContinuityFeedbackStartCountForDeveloperVerification() {
        return continuityFeedback.getStartCount();
    }

    int getContinuityFeedbackPrepareCountForDeveloperVerification() {
        return continuityFeedback.getPrepareCount();
    }

    int getContinuityFeedbackStopCountForDeveloperVerification() {
        return continuityFeedback.getStopCount();
    }

    void exitInstrumentModeForDeveloperVerification() {
        setActiveMode(MODE_NONE);
        updateReading();
    }

    void draw(Graphics graphics) {
        drawProbe(graphics, redProbe, Color.red);
        drawProbe(graphics, blackProbe, Color.black);
    }

    private void setActiveMode(int mode) {
        if (activeMode == MODE_CONTINUITY && mode != MODE_CONTINUITY)
            setContinuityDetected(false);
        activeMode = mode;
        requestDcVoltageRefresh();
        requestResistanceRefresh();
        requestDiodeRefresh();
        dcVoltageButton.setStyleName("chsel", activeMode == MODE_DC_VOLTAGE);
        resistanceButton.setStyleName("chsel", activeMode == MODE_RESISTANCE);
        continuityButton.setStyleName("chsel", activeMode == MODE_CONTINUITY);
        diodeButton.setStyleName("chsel", activeMode == MODE_DIODE);
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
        if (activeMode == MODE_NONE) {
            readingLabel.setText("--- V");
            return;
        }
        if (redProbe == null || blackProbe == null) {
            latestDcVoltage = Double.NaN;
            latestResistanceReading = Double.NaN;
            latestDiodeVoltage = Double.NaN;
            latestDiodeCurrent = Double.NaN;
            readingLabel.setText(isActiveResistanceMode() ? "--- Ohm" : "--- V");
            return;
        }
        if (isDiodeMode()) {
            if (diodeRefreshPending) {
                diodeRefreshPending = false;
                updateDiodeReading();
            }
            return;
        }
        if (isActiveResistanceMode()) {
            if (resistanceRefreshPending) {
                resistanceRefreshPending = false;
                updateResistanceReading();
            }
            return;
        }
        if (dcVoltageRefreshPending) {
            dcVoltageRefreshPending = false;
            latestDcVoltage = measurementAdapter.measureDcVoltage(redProbe, blackProbe);
            dcVoltageMeasurementCount++;
            validateTargets();
        }
        double voltage = latestDcVoltage;
        if (Double.isNaN(voltage)) {
            readingLabel.setText("--- V");
            return;
        }
        readingLabel.setText(CircuitElm.getVoltageText(voltage));
    }

    private void updateResistanceReading() {
        if (!measurementAdapter.isActiveMeasurementAllowed(redProbe, blackProbe)) {
            latestResistanceReading = Double.NaN;
            setContinuityDetected(false);
            readingLabel.setText("POWER OFF");
            return;
        }
        double resistance = measurementAdapter.measureResistance(redProbe, blackProbe);
        resistanceMeasurementCount++;
        validateTargets();
        if (!measurementAdapter.isActiveMeasurementAllowed(redProbe, blackProbe)) {
            latestResistanceReading = Double.NaN;
            setContinuityDetected(false);
            readingLabel.setText(redProbe == null || blackProbe == null ? "--- Ohm" : "POWER OFF");
            return;
        }
        latestResistanceReading = resistance;
        if (Double.isNaN(resistance) || Double.isInfinite(resistance) ||
            resistance > MAX_RESISTANCE) {
            setContinuityDetected(false);
            readingLabel.setText("OL");
            return;
        }
        readingLabel.setText(CircuitElm.getUnitText(resistance, "Ohm"));
        setContinuityDetected(activeMode == MODE_CONTINUITY &&
            resistance <= CONTINUITY_THRESHOLD_OHMS);
    }

    void setDcVoltageProbesForDeveloperVerification(ProbeTarget red, ProbeTarget black) {
        setActiveMode(MODE_DC_VOLTAGE);
        redProbe = red;
        blackProbe = black;
        updateReading();
    }

    private void updateDiodeReading() {
        if (!measurementAdapter.isActiveMeasurementAllowed(redProbe, blackProbe)) {
            latestDiodeVoltage = Double.NaN;
            latestDiodeCurrent = Double.NaN;
            readingLabel.setText("POWER OFF");
            return;
        }
        DiodeMeasurementResult result = measurementAdapter.measureDiode(redProbe, blackProbe);
        diodeMeasurementCount++;
        validateTargets();
        if (!measurementAdapter.isActiveMeasurementAllowed(redProbe, blackProbe)) {
            latestDiodeVoltage = Double.NaN;
            latestDiodeCurrent = Double.NaN;
            readingLabel.setText(redProbe == null || blackProbe == null ? "--- V" : "POWER OFF");
            return;
        }
        if (result == null || Double.isNaN(result.voltage) || Double.isInfinite(result.voltage) ||
                Double.isNaN(result.current) || Double.isInfinite(result.current) ||
                result.current < DIODE_MINIMUM_CURRENT ||
                result.voltage >= DIODE_COMPLIANCE_THRESHOLD) {
            latestDiodeVoltage = Double.NaN;
            latestDiodeCurrent = result == null ? Double.NaN : result.current;
            readingLabel.setText("OL");
            return;
        }
        latestDiodeVoltage = result.voltage;
        latestDiodeCurrent = result.current;
        readingLabel.setText(CircuitElm.getVoltageText(result.voltage));
    }

    private void validateTargets() {
        if (redProbe != null && !redProbe.isValid())
            redProbe = null;
        if (blackProbe != null && !blackProbe.isValid())
            blackProbe = null;
        if (redProbe == null || blackProbe == null)
            setContinuityDetected(false);
    }

    private void requestResistanceRefresh() {
        latestResistanceReading = Double.NaN;
        if (!isActiveResistanceMode())
            return;
        resistanceRefreshPending = true;
        setContinuityDetected(false);
        readingLabel.setText("--- Ohm");
    }

    private void requestDcVoltageRefresh() {
        latestDcVoltage = Double.NaN;
        if (activeMode != MODE_DC_VOLTAGE)
            return;
        dcVoltageRefreshPending = true;
        readingLabel.setText("--- V");
    }

    private void requestDiodeRefresh() {
        latestDiodeVoltage = Double.NaN;
        latestDiodeCurrent = Double.NaN;
        if (!isDiodeMode())
            return;
        diodeRefreshPending = true;
        readingLabel.setText("--- V");
    }

    private boolean isActiveResistanceMode() {
        return activeMode == MODE_RESISTANCE || activeMode == MODE_CONTINUITY;
    }

    private boolean isDiodeMode() { return activeMode == MODE_DIODE; }

    private void setContinuityDetected(boolean detected) {
        continuityDetected = detected;
        continuityLabel.setVisible(detected);
        continuityFeedback.setActive(detected);
    }
}