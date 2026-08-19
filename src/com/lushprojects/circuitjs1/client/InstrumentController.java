package com.lushprojects.circuitjs1.client;

import java.util.HashMap;
import java.util.Vector;

import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.dom.client.NativeEvent;
import com.google.gwt.user.client.ui.Button;
import com.google.gwt.user.client.ui.Grid;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.VerticalPanel;

/** Common probe/UI lifecycle; all mode behavior is delegated to providers. */
class InstrumentController {
    private static final int PROBE_MARKER_RADIUS = 5;
    static final double DIODE_MINIMUM_CURRENT = .00001;
    static final double DIODE_COMPLIANCE_THRESHOLD = 2.95;

    private final CirSim sim;
    private final CircuitMeasurementBoundary measurementAdapter;
    private final HashMap<String, Button> modeButtons = new HashMap<String, Button>();
    private final Grid modeGrid;
    private final Label readingLabel;
    private final Label continuityLabel;
    private final ContinuityFeedback continuityFeedback;
    private final InstrumentModeRegistry modeRegistry;
    private boolean interactionEnabled = true;
    private InstrumentModeStrategy activeStrategy;
    private ProbeTarget redProbe;
    private ProbeTarget blackProbe;
    private int dcVoltagePlaceholderDisplayCount;
    private int dcVoltageDisplayChangeCount;

    static final class DeveloperState {
        final InstrumentModeStrategy activeStrategy;
        final ProbeTarget redProbe;
        final ProbeTarget blackProbe;
        final boolean interactionEnabled;
        final String readingText;
        final boolean continuityVisible;
        final boolean continuityRequested;
        final int continuityPrepareCount;
        final int continuityStartCount;
        final int continuityStopCount;
        final int dcVoltagePlaceholderDisplayCount;
        final int dcVoltageDisplayChangeCount;
        final Vector<ModeState> modeStates;

        DeveloperState(InstrumentModeStrategy activeStrategy, ProbeTarget redProbe,
                ProbeTarget blackProbe, boolean interactionEnabled, String readingText,
                boolean continuityVisible, boolean continuityRequested,
                int continuityPrepareCount, int continuityStartCount, int continuityStopCount,
                int dcVoltagePlaceholderDisplayCount, int dcVoltageDisplayChangeCount,
                Vector<ModeState> modeStates) {
            this.activeStrategy = activeStrategy;
            this.redProbe = redProbe;
            this.blackProbe = blackProbe;
            this.interactionEnabled = interactionEnabled;
            this.readingText = readingText;
            this.continuityVisible = continuityVisible;
            this.continuityRequested = continuityRequested;
            this.continuityPrepareCount = continuityPrepareCount;
            this.continuityStartCount = continuityStartCount;
            this.continuityStopCount = continuityStopCount;
            this.dcVoltagePlaceholderDisplayCount = dcVoltagePlaceholderDisplayCount;
            this.dcVoltageDisplayChangeCount = dcVoltageDisplayChangeCount;
            this.modeStates = modeStates;
        }
    }

    static final class ModeState {
        final String id;
        final String displayText;
        final double primaryValue;
        final double secondaryValue;
        final int measurementCount;
        final boolean continuityDetected;
        final boolean refreshPending;

        ModeState(String id, InstrumentModeState state) {
            this.id = id;
            displayText = state.getDisplayText();
            primaryValue = state.getPrimaryValue();
            secondaryValue = state.getSecondaryValue();
            measurementCount = state.getMeasurementCount();
            continuityDetected = state.isContinuityDetected();
            refreshPending = state.isRefreshPending();
        }
    }

    InstrumentController(final CirSim sim, VerticalPanel panel) {
        this.sim = sim;
        measurementAdapter = new CircuitMeasurementAdapter(sim);
        modeRegistry = StandardInstrumentModeProviders.createRegistry();
        activeStrategy = modeRegistry.get("NONE");

        VerticalPanel meterPanel = new VerticalPanel();
        meterPanel.setStyleName("tsj-meter-panel");
        Label meterTitle = new Label("MULTIMETER");
        meterTitle.setStyleName("tsj-section-title");
        meterPanel.add(meterTitle);

        modeGrid = new Grid(1, 2);
        modeGrid.setStyleName("tsj-meter-modes");
        Vector<InstrumentModeStrategy> visibleModes = modeRegistry.getPlayerVisibleModes();
        for (int i = 0; i < visibleModes.size(); i++)
            addVisibleModeButton(visibleModes.elementAt(i));
        meterPanel.add(modeGrid);

        readingLabel = new Label(activeStrategy.getInitialDisplay());
        readingLabel.setStyleName("tsj-meter-display");
        meterPanel.add(readingLabel);
        continuityLabel = new Label("BEEP");
        continuityLabel.setStyleName("tsj-continuity-indicator");
        continuityLabel.setVisible(false);
        meterPanel.add(continuityLabel);
        panel.add(meterPanel);
        continuityFeedback = new BrowserContinuityFeedback();
    }

    boolean isHandlingPointerInput() {
        return interactionEnabled && activeStrategy.getProbeRequirements().requiresTwoProbes();
    }

    void setInteractionEnabled(boolean enabled) {
        interactionEnabled = enabled;
        for (Button button : modeButtons.values())
            button.setEnabled(enabled);
        if (!enabled)
            exitInstrumentModeForDeveloperVerification();
    }

    DeveloperState captureForDeveloperVerification() {
        Vector<ModeState> states = new Vector<ModeState>();
        for (InstrumentModeStrategy strategy : modeRegistry.getAll())
            states.add(new ModeState(strategy.getId(), strategy.getState()));
        return new DeveloperState(activeStrategy, redProbe, blackProbe, interactionEnabled,
            readingLabel.getText(), continuityLabel.isVisible(),
            continuityFeedback.isRequestedActive(),
            continuityFeedback.getPrepareCount(), continuityFeedback.getStartCount(),
            continuityFeedback.getStopCount(), dcVoltagePlaceholderDisplayCount,
            dcVoltageDisplayChangeCount, states);
    }

    void restoreForDeveloperVerification(DeveloperState saved) {
        if (saved == null)
            throw new IllegalArgumentException("Missing instrument state snapshot");
        activeStrategy = saved.activeStrategy;
        redProbe = saved.redProbe;
        blackProbe = saved.blackProbe;
        interactionEnabled = saved.interactionEnabled;
        dcVoltagePlaceholderDisplayCount = saved.dcVoltagePlaceholderDisplayCount;
        dcVoltageDisplayChangeCount = saved.dcVoltageDisplayChangeCount;
        for (ModeState savedState : saved.modeStates) {
            InstrumentModeState state = modeRegistry.get(savedState.id).getState();
            state.setDisplayText(savedState.displayText);
            state.setPrimaryValue(savedState.primaryValue);
            state.setSecondaryValue(savedState.secondaryValue);
            state.setMeasurementCountForDeveloperVerification(savedState.measurementCount);
            state.setContinuityDetected(savedState.continuityDetected);
            state.setRefreshPending(savedState.refreshPending);
        }
        readingLabel.setText(saved.readingText);
        continuityLabel.setVisible(saved.continuityVisible);
        continuityFeedback.setActive(saved.continuityRequested);
        if (!(continuityFeedback instanceof BrowserContinuityFeedback))
            throw new IllegalStateException("Task 41 cannot restore continuity feedback counters");
        ((BrowserContinuityFeedback) continuityFeedback).restoreCountersForDeveloperVerification(
            saved.continuityPrepareCount, saved.continuityStartCount, saved.continuityStopCount);
        for (String id : modeButtons.keySet())
            modeButtons.get(id).setStyleName("chsel", activeStrategy.getId().equals(id));
        for (Button button : modeButtons.values())
            button.setEnabled(interactionEnabled);
    }

    void handlePointerInput(int button, int screenX, int screenY) {
        handlePointerInput(button, sim.findPostTarget(screenX, screenY));
    }

    void handlePointerInput(int button, ProbeTarget target) {
        if (!interactionEnabled)
            return;
        boolean changed = false;
        if (target != null) {
            if (button == NativeEvent.BUTTON_LEFT) {
                changed = redProbe == null || !redProbe.isSameTarget(target);
                redProbe = target;
            } else if (button == NativeEvent.BUTTON_RIGHT) {
                changed = blackProbe == null || !blackProbe.isSameTarget(target);
                blackProbe = target;
            }
        }
        if (changed) {
            activeStrategy.onProbeChanged(this);
            activeStrategy.refresh(this);
            updateReading();
        }
        sim.repaint();
    }

    void clearTargets() {
        redProbe = null;
        blackProbe = null;
        activeStrategy.refresh(this);
        updateReading();
    }

    void refreshActiveMeasurement() {
        activeStrategy.refresh(this);
    }

    void onCircuitTopologyChanged() {
        validateTargetsForStrategy();
        activeStrategy.refresh(this);
    }

    void onSimulationStepComplete(boolean didAnalyze) {
        if (sim.activeMeasurementOverlay)
            return;
        activeStrategy.onSimulationStepComplete(this, didAnalyze);
    }

    void setResistanceProbesForDeveloperVerification(ProbeTarget red, ProbeTarget black) {
        setActiveMode("RESISTANCE", true);
        redProbe = red;
        blackProbe = black;
        activeStrategy.refresh(this);
        updateReading();
    }

    void activateResistanceModeForDeveloperVerification() {
        setActiveMode("RESISTANCE", true);
        updateReading();
    }

    void activateDcVoltageModeForDeveloperVerification() {
        setActiveMode("DC_VOLTAGE", true);
        updateReading();
    }

    String getReadingForDeveloperVerification() {
        return readingLabel.getText();
    }

    double getLatestResistanceReadingForDeveloperVerification() {
        return getModeState("RESISTANCE").getPrimaryValue();
    }

    double getDcVoltageDifferenceForDeveloperVerification(ProbeTarget red, ProbeTarget black) {
        return measurementAdapter.measureDcVoltage(red, black);
    }

    double getLatestDcVoltageForDeveloperVerification() {
        return getModeState("DC_VOLTAGE").getPrimaryValue();
    }

    int getDcVoltageMeasurementCountForDeveloperVerification() {
        return getModeState("DC_VOLTAGE").getMeasurementCount();
    }

    int getDcVoltagePlaceholderDisplayCountForDeveloperVerification() {
        return dcVoltagePlaceholderDisplayCount;
    }

    int getDcVoltageDisplayChangeCountForDeveloperVerification() {
        return dcVoltageDisplayChangeCount;
    }

    boolean isResistanceRefreshPendingForDeveloperVerification() {
        return getModeState("RESISTANCE").isRefreshPending();
    }

    int getActiveModeForDeveloperVerification() {
        return activeStrategy.getDeveloperCode();
    }

    int getResistanceMeasurementCountForDeveloperVerification() {
        return getModeState("RESISTANCE").getMeasurementCount();
    }

    void setContinuityProbesForDeveloperVerification(ProbeTarget red, ProbeTarget black) {
        setActiveMode("CONTINUITY", true);
        redProbe = red;
        blackProbe = black;
        activeStrategy.refresh(this);
        updateReading();
    }

    void setDiodeProbesForDeveloperVerification(ProbeTarget red, ProbeTarget black) {
        setActiveMode("DIODE", true);
        redProbe = red;
        blackProbe = black;
        activeStrategy.refresh(this);
        updateReading();
    }

    void setDcVoltageProbesForDeveloperVerification(ProbeTarget red, ProbeTarget black) {
        setActiveMode("DC_VOLTAGE", true);
        clearTargets();
        handlePointerInput(NativeEvent.BUTTON_LEFT, red);
        handlePointerInput(NativeEvent.BUTTON_RIGHT, black);
    }

    double getLatestDiodeVoltageForDeveloperVerification() {
        return getModeState("DIODE").getPrimaryValue();
    }

    double getLatestDiodeCurrentForDeveloperVerification() {
        return getModeState("DIODE").getSecondaryValue();
    }

    int getDiodeMeasurementCountForDeveloperVerification() {
        return getModeState("DIODE").getMeasurementCount();
    }

    boolean isContinuityDetectedForDeveloperVerification() {
        return getModeState("CONTINUITY").isContinuityDetected();
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

    int getContinuityMeasurementCountForDeveloperVerification() {
        return getModeState("CONTINUITY").getMeasurementCount();
    }

    /** Deliberately perturbs only developer feedback so rollback tests prove counter restore. */
    void perturbContinuityFeedbackForDeveloperVerification() {
        boolean requested = continuityFeedback.isRequestedActive();
        continuityFeedback.prepare();
        continuityFeedback.setActive(!requested);
        continuityFeedback.setActive(requested);
        continuityLabel.setVisible(requested);
    }

    void exitInstrumentModeForDeveloperVerification() {
        setActiveMode("NONE", true);
        updateReading();
    }

    void registerDeveloperInstrumentModeForVerification(InstrumentModeStrategy strategy) {
        modeRegistry.registerDeveloperOnly(strategy);
    }

    /** Register a production provider and refresh the visible mode controls. */
    void registerInstrumentModeProvider(InstrumentModeProvider provider) {
        modeRegistry.registerProduction(provider);
        if (provider.isPlayerVisible())
            addVisibleModeButton(provider);
    }

    boolean isPlayerVisibleModeButtonRegisteredForDeveloperVerification(String id) {
        return id != null && modeButtons.containsKey(id);
    }

    void clickPlayerVisibleModeButtonForDeveloperVerification(String id) {
        Button button = id == null ? null : modeButtons.get(id);
        if (button == null)
            throw new IllegalArgumentException("No player-visible instrument mode button: " + id);
        button.click();
    }

    void activateDeveloperInstrumentModeForVerification(String id) {
        setActiveStrategy(modeRegistry.get(id), false);
    }

    void setDeveloperReadingForVerification(String text) {
        readingLabel.setText(text);
    }

    void draw(Graphics graphics) {
        drawProbe(graphics, redProbe, Color.red);
        drawProbe(graphics, blackProbe, Color.black);
    }

    private void toggleMode(String id) {
        if (activeStrategy.getId().equals(id))
            setActiveMode("NONE", true);
        else
            setActiveMode(id, true);
    }

    private void setActiveMode(String id, boolean refresh) {
        setActiveStrategy(modeRegistry.get(id), refresh);
    }

    private void setActiveStrategy(InstrumentModeStrategy strategy, boolean refresh) {
        if (strategy == null)
            throw new IllegalArgumentException("Missing instrument strategy");
        if (activeStrategy != null && !activeStrategy.getId().equals(strategy.getId()))
            activeStrategy.deactivate(this);
        boolean changed = activeStrategy != strategy;
        activeStrategy = strategy;
        if (changed)
            activeStrategy.activate(this);
        for (String id : modeButtons.keySet())
            modeButtons.get(id).setStyleName("chsel", activeStrategy.getId().equals(id));
        if (refresh)
            activeStrategy.refresh(this);
        setContinuityFeedbackForStrategy(activeStrategy.getState().isContinuityDetected());
        sim.repaint();
    }

    private void addVisibleModeButton(final InstrumentModeStrategy strategy) {
        if (strategy == null || !strategy.isPlayerVisible() ||
                modeButtons.containsKey(strategy.getId()))
            return;
        final Button button = new Button(strategy.getLabel());
        button.addStyleName("chbut");
        int index = modeButtons.size();
        modeButtons.put(strategy.getId(), button);
        int rows = Math.max(1, (modeButtons.size() + 1) / 2);
        modeGrid.resize(rows, 2);
        modeGrid.setWidget(index / 2, index % 2, button);
        button.setEnabled(interactionEnabled);
        button.addClickHandler(new ClickHandler() {
            public void onClick(ClickEvent event) {
                toggleMode(strategy.getId());
                updateReading();
            }
        });
    }

    private void updateReading() {
        validateTargetsForStrategy();
        activeStrategy.measure(this);
        activeStrategy.display(this);
        setContinuityFeedbackForStrategy(activeStrategy.getState().isContinuityDetected());
    }

    void updateReadingForStrategy() {
        updateReading();
    }

    void setInstrumentDisplayForStrategy(String text) {
        if (activeStrategy.getId().equals("DC_VOLTAGE")) {
            String prior = readingLabel.getText();
            if (!text.equals(prior)) {
                dcVoltageDisplayChangeCount++;
                if ("--- V".equals(text))
                    dcVoltagePlaceholderDisplayCount++;
            }
        }
        readingLabel.setText(text);
    }

    ProbeTarget getRedProbeForStrategy() {
        return redProbe;
    }

    ProbeTarget getBlackProbeForStrategy() {
        return blackProbe;
    }

    double measureDcVoltageForStrategy(ProbeTarget red, ProbeTarget black) {
        return measurementAdapter.measureDcVoltage(red, black);
    }

    boolean usesLiveDcVoltageForStrategy(ProbeTarget red, ProbeTarget black) {
        return measurementAdapter.usesLiveDcVoltage(red, black);
    }

    ActiveMeasurementReadiness getActiveMeasurementReadinessForStrategy(ProbeTarget red,
            ProbeTarget black) {
        return measurementAdapter.getActiveMeasurementReadiness(red, black);
    }

    double measureResistanceForStrategy(ProbeTarget red, ProbeTarget black) {
        return measurementAdapter.measureResistance(red, black);
    }

    DiodeMeasurementResult measureDiodeForStrategy(ProbeTarget red, ProbeTarget black) {
        return measurementAdapter.measureDiode(red, black);
    }

    boolean isMeasurementAllowedForStrategy(InstrumentModeStrategy strategy,
            ProbeTarget red, ProbeTarget black) {
        if (strategy == null || red == null || black == null || !red.isValid() || !black.isValid())
            return false;
        if (strategy.getPowerPolicy() == InstrumentPowerPolicy.UNPOWERED_ONLY)
            return measurementAdapter.isActiveMeasurementAllowed(red, black);
        if (strategy.getPowerPolicy() == InstrumentPowerPolicy.POWERED_ONLY)
            return !sim.getBoardPowerController().isElectricallyUnpowered();
        return true;
    }

    void validateTargetsForStrategy() {
        if (redProbe != null && !redProbe.isValid())
            redProbe = null;
        if (blackProbe != null && !blackProbe.isValid())
            blackProbe = null;
        if (redProbe == null || blackProbe == null) {
            activeStrategy.getState().setContinuityDetected(false);
            setContinuityFeedbackForStrategy(false);
        }
    }

    void prepareContinuityFeedbackForStrategy() {
        continuityFeedback.prepare();
    }

    void setContinuityFeedbackForStrategy(boolean active) {
        continuityLabel.setVisible(active);
        continuityFeedback.setActive(active);
    }

    void finishActiveMeasurementForStrategy() {
        sim.finishActiveMeasurementBeforeInstrumentExit();
    }

    private InstrumentModeState getModeState(String id) {
        return modeRegistry.get(id).getState();
    }

    private void drawProbe(Graphics graphics, ProbeTarget target, Color color) {
        if (target == null)
            return;
        Point point = target.getMarkerPoint();
        if (point == null)
            return;
        graphics.setColor(Color.white);
        graphics.fillOval(point.x - PROBE_MARKER_RADIUS, point.y - PROBE_MARKER_RADIUS,
            PROBE_MARKER_RADIUS * 2 + 1, PROBE_MARKER_RADIUS * 2 + 1);
        graphics.setColor(color);
        graphics.fillOval(point.x - 3, point.y - 3, 7, 7);
    }
}
