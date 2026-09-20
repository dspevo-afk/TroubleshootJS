package com.lushprojects.circuitjs1.client;

import java.util.HashMap;
import java.util.Vector;

import com.google.gwt.canvas.client.Canvas;
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
    private static final int SCOPE_TRACE_WIDTH = 198;
    private static final int SCOPE_TRACE_HEIGHT = 60;
    static final double DIODE_MINIMUM_CURRENT = .00001;
    static final double DIODE_COMPLIANCE_THRESHOLD = 2.95;

    private final CirSim sim;
    private final CircuitMeasurementBoundary measurementAdapter;
    private final HashMap<String, Button> modeButtons = new HashMap<String, Button>();
    private final HashMap<String, ClickHandler> modeHandlers =
        new HashMap<String, ClickHandler>();
    private final Grid modeGrid;
    private final VerticalPanel meterPanel;
    private final Label readingLabel;
    private final Label continuityLabel;
    private final Canvas scopeTraceCanvas;
    private final Grid scopeControls;
    private final Button scopeTimeButton;
    private final Button scopeVoltageButton;
    private final Button scopeTriggerButton;
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

        meterPanel = new VerticalPanel();
        meterPanel.getElement().setAttribute("data-mode", "NONE");
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
        scopeTraceCanvas = Canvas.createIfSupported();
        if (scopeTraceCanvas != null) {
            scopeTraceCanvas.setCoordinateSpaceWidth(SCOPE_TRACE_WIDTH);
            scopeTraceCanvas.setCoordinateSpaceHeight(SCOPE_TRACE_HEIGHT);
            scopeTraceCanvas.setPixelSize(SCOPE_TRACE_WIDTH, SCOPE_TRACE_HEIGHT);
            scopeTraceCanvas.setStyleName("tsj-scope-trace");
            scopeTraceCanvas.getElement().setAttribute("role", "img");
            scopeTraceCanvas.getElement().setAttribute("aria-label", "Oscilloscope: probes required");
            scopeTraceCanvas.setVisible(false);
            meterPanel.add(scopeTraceCanvas);
        }
        continuityLabel = new Label("BEEP");
        continuityLabel.setStyleName("tsj-continuity-indicator");
        continuityLabel.setVisible(false);
        meterPanel.add(continuityLabel);
        scopeControls = new Grid(1, 3);
        scopeControls.setStyleName("tsj-scope-controls");
        scopeTimeButton = new Button("TIME");
        scopeVoltageButton = new Button("VOLTS");
        scopeTriggerButton = new Button("TRIGGER");
        scopeTimeButton.getElement().setAttribute("aria-label", "Cycle oscilloscope time per division");
        scopeVoltageButton.getElement().setAttribute("aria-label", "Cycle oscilloscope volts per division");
        scopeTriggerButton.getElement().setAttribute("aria-label", "Cycle oscilloscope trigger edge");
        scopeControls.setWidget(0, 0, scopeTimeButton);
        scopeControls.setWidget(0, 1, scopeVoltageButton);
        scopeControls.setWidget(0, 2, scopeTriggerButton);
        scopeControls.setVisible(false);
        meterPanel.add(scopeControls);
        scopeTimeButton.addClickHandler(new ClickHandler() {
            public void onClick(ClickEvent event) { cycleScopeControl(0); }
        });
        scopeVoltageButton.addClickHandler(new ClickHandler() {
            public void onClick(ClickEvent event) { cycleScopeControl(1); }
        });
        scopeTriggerButton.addClickHandler(new ClickHandler() {
            public void onClick(ClickEvent event) { cycleScopeControl(2); }
        });
        panel.add(meterPanel);
        continuityFeedback = new BrowserContinuityFeedback();
    }

    boolean isHandlingPointerInput() {
        return isCurrentPlayerInteractionAllowed() &&
            activeStrategy.getProbeRequirements().requiresTwoProbes();
    }

    void setInteractionEnabled(boolean enabled) {
        interactionEnabled = enabled;
        for (Button button : modeButtons.values())
            button.setEnabled(enabled);
        scopeTimeButton.setEnabled(enabled);
        scopeVoltageButton.setEnabled(enabled);
        scopeTriggerButton.setEnabled(enabled);
        /*
         * A pending verification or temporary analysis only suspends player
         * input.  Keep the selected mode and probes so that settlement can
         * resume the same meter without triggering another measurement from
         * this UI refresh.  Completion is terminal and may clear the mode as
         * the previous lifecycle did.
         */
        GeneratedChallengeController challenge = sim.getGeneratedChallengeController();
        if (!enabled && challenge != null && challenge.isCompleted())
            exitInstrumentModeForDeveloperVerification();
    }

    private boolean isCurrentPlayerInteractionAllowed() {
        /* Button enabled state is a rendering cache; always consult the live
         * simulator predicate at the entry boundary. */
        return sim.isChallengeInteractionEnabled();
    }

    boolean isInteractionEnabledForDeveloperVerification() {
        return interactionEnabled;
    }

    boolean isModeButtonEnabledForDeveloperVerification(String id) {
        Button button = id == null ? null : modeButtons.get(id);
        return button != null && button.isEnabled();
    }

    ClickHandler getPlayerModeHandlerForDeveloperVerification(String id) {
        return id == null ? null : modeHandlers.get(id);
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
        /* The verifier may have selected a different strategy after capture.
         * Let that live strategy release external resources (notably a scope
         * subscription) before restoring the saved presentation/state. */
        if (activeStrategy != null && activeStrategy != saved.activeStrategy)
            activeStrategy.deactivate(this);
        activeStrategy = saved.activeStrategy;
        meterPanel.getElement().setAttribute("data-mode", activeStrategy.getId());
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
        scopeTimeButton.setEnabled(interactionEnabled);
        scopeVoltageButton.setEnabled(interactionEnabled);
        scopeTriggerButton.setEnabled(interactionEnabled);
        scopeControls.setVisible(activeStrategy instanceof OscilloscopeInstrumentMode);
        setScopePresentationVisible(activeStrategy instanceof OscilloscopeInstrumentMode);
        if (activeStrategy instanceof OscilloscopeInstrumentMode) {
            /* A verifier can temporarily select another strategy, whose
             * deactivation retires the old scope subscription. Rebind through
             * the normal strategy seam instead of restoring a stale cursor. */
            activeStrategy.refresh(this);
            updateReading();
        }
    }

    void handlePointerInput(int button, int screenX, int screenY) {
        if (!isCurrentPlayerInteractionAllowed())
            return;
        handlePointerInput(button, sim.findPostTarget(screenX, screenY));
    }

    void handlePointerInput(int button, ProbeTarget target) {
        if (!isCurrentPlayerInteractionAllowed())
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

    /** Refresh readings only when a physical target disappears (tray, face or copper revision). */
    void onPhysicalProjectionChanged() {
        boolean redCleared = redProbe != null && !redProbe.isValid();
        boolean blackCleared = blackProbe != null && !blackProbe.isValid();
        if (!redCleared && !blackCleared)
            return;
        if (redCleared)
            redProbe = null;
        if (blackCleared)
            blackProbe = null;
        activeStrategy.refresh(this);
        updateReading();
        setContinuityFeedbackForStrategy(false);
        // Display/feedback widgets update immediately. The renderer or page
        // handler owns the next paint; do not schedule one from this callback,
        // which may run while a renderer repaint is already in progress.
    }

    void onSimulationStepComplete(boolean didAnalyze) {
        if (sim.activeMeasurementOverlay || !sim.isChallengeInteractionEnabled())
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
        activeStrategy.draw(this, graphics);
        drawProbe(graphics, redProbe, Color.red);
        drawProbe(graphics, blackProbe, Color.black);
    }

    /** The bench dial uses the same live guard, strategy and cleanup as native buttons. */
    boolean selectPlayerMode(String id) {
        if (!isCurrentPlayerInteractionAllowed() || id == null ||
                !("NONE".equals(id) || modeButtons.containsKey(id))) return false;
        setActiveMode(id, true);
        updateReading();
        return true;
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
        meterPanel.getElement().setAttribute("data-mode", activeStrategy.getId());
        scopeControls.setVisible(activeStrategy instanceof OscilloscopeInstrumentMode);
        setScopePresentationVisible(activeStrategy instanceof OscilloscopeInstrumentMode);
        if (changed)
            activeStrategy.activate(this);
        for (String id : modeButtons.keySet())
            modeButtons.get(id).setStyleName("chsel", activeStrategy.getId().equals(id));
        if (refresh)
            activeStrategy.refresh(this);
        setContinuityFeedbackForStrategy(activeStrategy.getState().isContinuityDetected());
        sim.repaint();
    }

    private void setScopePresentationVisible(boolean visible) {
        if (scopeTraceCanvas == null)
            return;
        scopeTraceCanvas.setVisible(visible);
        if (!visible)
            clearScopeTrace();
    }

    private void clearScopeTrace() {
        if (scopeTraceCanvas == null)
            return;
        Graphics graphics = new Graphics(scopeTraceCanvas.getContext2d());
        graphics.setColor("#0e171b");
        graphics.fillRect(0, 0, SCOPE_TRACE_WIDTH, SCOPE_TRACE_HEIGHT);
        scopeTraceCanvas.getElement().setAttribute("aria-label", "Oscilloscope: inactive");
    }

    private static boolean finite(double value) {
        return !Double.isNaN(value) && !Double.isInfinite(value);
    }

    private static int clamp(int value, int minimum, int maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    private void addVisibleModeButton(final InstrumentModeStrategy strategy) {
        if (strategy == null || !strategy.isPlayerVisible() ||
                modeButtons.containsKey(strategy.getId()))
            return;
        final Button button = new Button(strategy.getLabel());
        button.addStyleName("chbut");
        button.getElement().setAttribute("data-instrument-mode", strategy.getId());
        int index = modeButtons.size();
        modeButtons.put(strategy.getId(), button);
        int rows = Math.max(1, (modeButtons.size() + 1) / 2);
        modeGrid.resize(rows, 2);
        modeGrid.setWidget(index / 2, index % 2, button);
        button.setEnabled(interactionEnabled);
        ClickHandler modeHandler = new ClickHandler() {
            public void onClick(ClickEvent event) {
                if (!isCurrentPlayerInteractionAllowed())
                    return;
                toggleMode(strategy.getId());
                updateReading();
            }
        };
        modeHandlers.put(strategy.getId(), modeHandler);
        button.addClickHandler(modeHandler);
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

    VoltageMeasurementResult measureDcVoltageResultForStrategy(ProbeTarget red,
            ProbeTarget black) {
        return measurementAdapter.measureDcVoltageResult(red, black);
    }

    VoltageMeasurementResult measureAcVoltageForStrategy(ProbeTarget red,
            ProbeTarget black) {
        return measurementAdapter.measureAcVoltage(red, black);
    }

    MeasurementReferencePolicy.Result assessDifferentialReferenceForStrategy(
            ProbeTarget red, ProbeTarget black) {
        return measurementAdapter.assessDifferentialReference(red, black);
    }

    SolverTimeObservationService.Subscription observeDifferentialVoltageForStrategy(
            ProbeTarget red, ProbeTarget black) {
        return measurementAdapter.observeDifferentialVoltage(red, black);
    }

    void stopObservingDifferentialVoltageForStrategy(
            SolverTimeObservationService.Subscription samples) {
        measurementAdapter.stopObservingDifferentialVoltage(samples);
    }

    void configureScopeControlsForStrategy(String time, String voltage, String trigger) {
        scopeTimeButton.setText(time == null ? "TIME" : time);
        scopeVoltageButton.setText(voltage == null ? "VOLTS" : voltage);
        scopeTriggerButton.setText(trigger == null ? "TRIGGER" : trigger);
    }

    /**
     * Paints a bounded, read-only projection of the active scope subscription
     * into the native meter screen. It never advances or reads CircuitJS on
     * its own; callers have already obtained accepted solver-time samples.
     */
    void renderScopeTraceForStrategy(SolverTimeSample[] samples, double viewStart,
            double captureSeconds, double voltsPerDivision, String legend,
            String status, boolean drawWaveform) {
        if (scopeTraceCanvas == null)
            return;
        Graphics graphics = new Graphics(scopeTraceCanvas.getContext2d());
        graphics.setColor("#0e171b");
        graphics.fillRect(0, 0, SCOPE_TRACE_WIDTH, SCOPE_TRACE_HEIGHT);
        graphics.setColor("#34515a");
        graphics.setLineWidth(1);
        final int graphTop = 11;
        final int graphHeight = 37;
        for (int i = 0; i <= 10; i++) {
            int x = i * (SCOPE_TRACE_WIDTH - 1) / 10;
            graphics.drawLine(x, graphTop, x, graphTop + graphHeight);
        }
        for (int i = 0; i <= 8; i++) {
            int y = graphTop + i * graphHeight / 8;
            graphics.drawLine(0, y, SCOPE_TRACE_WIDTH - 1, y);
        }
        graphics.setFont(new Font("monospace", Font.BOLD, 7));
        graphics.setColor("#d5f7cc");
        graphics.drawString(legend == null ? "SCOPE" : legend, 3, 8);
        graphics.drawString(status == null ? "WINDOW" : status, 3, SCOPE_TRACE_HEIGHT - 3);
        if (drawWaveform && samples != null && samples.length > 0 &&
                finite(viewStart) && finite(captureSeconds) && captureSeconds > 0 &&
                finite(voltsPerDivision) && voltsPerDivision > 0) {
            double pixelsPerVolt = (graphHeight / 8.0) / voltsPerDivision;
            int priorX = Integer.MIN_VALUE;
            int priorY = 0;
            graphics.setColor("#91ef7c");
            graphics.setLineWidth(1.25);
            for (int i = 0; i < samples.length; i++) {
                SolverTimeSample sample = samples[i];
                if (sample == null || !finite(sample.getTime()) || !finite(sample.getValue()) ||
                        sample.getTime() < viewStart || sample.getTime() > viewStart + captureSeconds) {
                    priorX = Integer.MIN_VALUE;
                    continue;
                }
                int x = clamp((int)Math.round((sample.getTime() - viewStart) *
                    (SCOPE_TRACE_WIDTH - 1) / captureSeconds), 0, SCOPE_TRACE_WIDTH - 1);
                int y = clamp((int)Math.round(graphTop + graphHeight * .5 -
                    sample.getValue() * pixelsPerVolt), graphTop, graphTop + graphHeight);
                if (priorX != Integer.MIN_VALUE)
                    graphics.drawLine(priorX, priorY, x, y);
                priorX = x;
                priorY = y;
            }
        }
        scopeTraceCanvas.getElement().setAttribute("aria-label", "Oscilloscope: " +
            (status == null ? "WINDOW" : status) + ". " +
            (legend == null ? "" : legend));
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
        if (!sim.isChallengeInteractionEnabled())
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

    private void cycleScopeControl(int control) {
        if (!isCurrentPlayerInteractionAllowed() || !(activeStrategy instanceof OscilloscopeInstrumentMode))
            return;
        OscilloscopeInstrumentMode scope = (OscilloscopeInstrumentMode) activeStrategy;
        if (control == 0)
            scope.cycleTimeScale(this);
        else if (control == 1)
            scope.cycleVoltageScale(this);
        else
            scope.cycleTrigger(this);
    }

    private InstrumentModeState getModeState(String id) {
        return modeRegistry.get(id).getState();
    }

    private void drawProbe(Graphics graphics, ProbeTarget target, Color color) {
        if (target == null || !target.isValid())
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
