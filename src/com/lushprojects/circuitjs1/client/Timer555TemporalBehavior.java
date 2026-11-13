package com.lushprojects.circuitjs1.client;

import java.util.HashMap;
import java.util.Vector;

/**
 * Family-owned CircuitJS transient test for the E07 timer board. The 555 OUT
 * pin is read from the solved graph at fixed solver-time slices; the period
 * (free-running variant) or pulse width (triggered variant) is measured from
 * those solved edges and compared with the healthy board's own measurement.
 * Nothing here is computed from component values, and an edge rate the slice
 * cannot resolve is reported as unsupported rather than as a frequency.
 */
final class Timer555TemporalBehavior implements GeneratedTemporalBehavior,
        GeneratedLiveTemporalSimulation, PhysicalBoardRuntimeCapability {
    static final String CAPABILITY_ID = "TIMER_555_TEMPORAL";
    static final int BEHAVIOR_VERSION = 1;

    enum Mode { FREE_RUNNING, TRIGGERED }

    /** Solver-time slice between OUT samples. */
    static final double SAMPLE_SLICE_SECONDS = .002;
    /** Edges closer than this cannot be resolved by the slice and are not measured. */
    static final double MIN_RESOLVED_INTERVAL_SECONDS = .010;
    /** Healthy references must be comfortably slower than the resolution floor. */
    static final double MIN_HEALTHY_INTERVAL_SECONDS = .050;
    static final double OFF_SETTLE_SECONDS = .750;
    static final double WINDOW_SECONDS = .750;
    /** Free-running: ignore the longer first cycle that starts from a discharged capacitor. */
    static final double STARTUP_BLANK_SECONDS = .500;
    /** Triggered: idle time before the press, then press duration. */
    static final double TRIGGER_AT_SECONDS = .250;
    static final double PRESS_SECONDS = .020;
    static final double TIMING_TOLERANCE = .12;
    static final double TOO_FAST_FRACTION = .60;
    private static final double LIVE_SOLVER_ADVANCE_SECONDS = .010;
    /**
     * Solver recipe. The shortest timed interval is the 10 ms resolution floor,
     * so a 100 us maximum step quantizes timing to 1% at worst and 0.02% for the
     * healthy 0.5 s intervals, while keeping browser proof work bounded.
     */
    static final double SOLVER_MAX_STEP_SECONDS = 1e-4;
    static final double SOLVER_MIN_STEP_SECONDS = 50e-12;
    private static final int PHASE_COUNT = 4;

    private final Mode mode;
    private final CircuitPostMeasurementEndpoint output;
    private final CircuitPostMeasurementEndpoint ground;
    private final String outputEndpointId;
    private final String groundEndpointId;
    private final double nominalSupply;
    private final SwitchElm startButton;

    private GeneratedObservedBehavior observedBehavior;
    private boolean healthyReferenceCaptured;
    private double healthyTimingSeconds;
    private double measuredTimingSeconds = Double.NaN;

    Timer555TemporalBehavior(Mode mode, CircuitPostMeasurementEndpoint output,
            CircuitPostMeasurementEndpoint ground, String outputEndpointId,
            String groundEndpointId, double nominalSupply, SwitchElm startButton) {
        if (mode == null || output == null || ground == null || outputEndpointId == null ||
                outputEndpointId.length() == 0 || groundEndpointId == null ||
                groundEndpointId.length() == 0 || !(nominalSupply > 0) ||
                Double.isInfinite(nominalSupply) ||
                (mode == Mode.TRIGGERED) != (startButton != null))
            throw new IllegalArgumentException("Invalid timer temporal behavior");
        this.mode = mode;
        this.output = output;
        this.ground = ground;
        this.outputEndpointId = outputEndpointId;
        this.groundEndpointId = groundEndpointId;
        this.nominalSupply = nominalSupply;
        this.startButton = startButton;
    }

    Mode getMode() { return mode; }

    public String getCapabilityId() { return CAPABILITY_ID; }

    public double getLiveSolverAdvanceSeconds() { return LIVE_SOLVER_ADVANCE_SECONDS; }

    public int getProfileWorkUnits() { return PHASE_COUNT; }

    public void requireOwnedBy(GeneratedBoardInstance instance) {
        requireElementOwner(instance, output.getElement(), output.getPostIndex());
        requireElementOwner(instance, ground.getElement(), ground.getPostIndex());
        if (startButton != null) requireElementOwner(instance, startButton, 0);
    }

    private static void requireElementOwner(GeneratedBoardInstance instance, CircuitElm element,
            int post) {
        if (instance == null || !instance.getSimulationElements().contains(element) ||
                post < 0 || post >= element.getPostCount())
            throw new IllegalArgumentException("Fresh temporal state captures a foreign endpoint");
    }

    public GeneratedTemporalDependency getDependency(GeneratedBoardInstance instance) {
        requireOwnedBy(instance);
        requireExactEndpointBinding(instance, outputEndpointId, output);
        requireExactEndpointBinding(instance, groundEndpointId, ground);
        if (!healthyReferenceCaptured)
            throw new IllegalStateException("Timer healthy reference is unavailable");
        HashMap<String, String> parameters = new HashMap<String, String>();
        parameters.put("mode", mode.name());
        parameters.put("supply-volts", Double.toString(nominalSupply));
        parameters.put("sample-slice-seconds", Double.toString(SAMPLE_SLICE_SECONDS));
        parameters.put("solver-maximum-step-seconds", Double.toString(SOLVER_MAX_STEP_SECONDS));
        parameters.put("solver-minimum-step-seconds", Double.toString(SOLVER_MIN_STEP_SECONDS));
        parameters.put("min-resolved-interval-seconds", Double.toString(MIN_RESOLVED_INTERVAL_SECONDS));
        parameters.put("min-healthy-interval-seconds", Double.toString(MIN_HEALTHY_INTERVAL_SECONDS));
        parameters.put("off-settle-seconds", Double.toString(OFF_SETTLE_SECONDS));
        parameters.put("window-seconds", Double.toString(WINDOW_SECONDS));
        parameters.put("windows", Integer.toString(PHASE_COUNT - 1));
        parameters.put("startup-blank-seconds", Double.toString(STARTUP_BLANK_SECONDS));
        parameters.put("trigger-at-seconds", Double.toString(TRIGGER_AT_SECONDS));
        parameters.put("press-seconds", Double.toString(PRESS_SECONDS));
        parameters.put("timing-tolerance", Double.toString(TIMING_TOLERANCE));
        parameters.put("too-fast-fraction", Double.toString(TOO_FAST_FRACTION));
        parameters.put("live-advance-seconds", Double.toString(LIVE_SOLVER_ADVANCE_SECONDS));
        parameters.put("healthy-timing-seconds", Double.toString(healthyTimingSeconds));
        return new GeneratedTemporalDependency(CAPABILITY_ID, BEHAVIOR_VERSION,
            GeneratedTemporalDependency.FRESH_GENERATED_OWNER_COLD_V1,
            outputEndpointId, groundEndpointId, parameters);
    }

    private static void requireExactEndpointBinding(GeneratedBoardInstance instance,
            String endpointId, CircuitPostMeasurementEndpoint expected) {
        BoardSimulationBindings bindings = instance.getSimulationBindings();
        CircuitMeasurementEndpoint actual = bindings == null ? null : bindings.getEndpoint(endpointId);
        if (!(actual instanceof CircuitPostMeasurementEndpoint))
            throw new IllegalStateException("Missing temporal endpoint binding: " + endpointId);
        CircuitPostMeasurementEndpoint bound = (CircuitPostMeasurementEndpoint) actual;
        if (bound.getElement() != expected.getElement() ||
                bound.getPostIndex() != expected.getPostIndex())
            throw new IllegalStateException("Temporal endpoint binding changed: " + endpointId);
    }

    public GeneratedWork<GeneratedRepairStatus> beginProfile(CirSim sim,
            GeneratedBoardInstance instance, Profile profile) {
        if (sim == null || instance == null || profile == null)
            throw new IllegalArgumentException("Missing timer temporal profile context");
        if (sim.getGeneratedBoardInstance() != instance || instance.getTemporalBehavior() != this)
            throw new IllegalStateException("Timer temporal profile has no current owner");
        if (profile == Profile.REPAIR && (sim.activeMeasurementOverlay ||
                sim.getBoardPowerController().getState() != BoardPowerState.POWERED ||
                sim.getBoardModificationController() == null ||
                !sim.getBoardModificationController().isFullyRestored()))
            return GeneratedWork.value(GeneratedRepairStatus.STILL_FAULTED_OR_NONFUNCTIONAL);
        return new TimerProfileWork(sim, instance, profile);
    }

    public void prepareHealthyProfile(CirSim sim, GeneratedBoardInstance instance) {
        GeneratedWork.complete(beginProfile(sim, instance, Profile.HEALTHY));
    }

    public void prepareFaultedProfile(CirSim sim, GeneratedBoardInstance instance) {
        GeneratedWork.complete(beginProfile(sim, instance, Profile.FAULTED));
    }

    public void verifyFaultedProfile(CirSim sim, GeneratedBoardInstance instance,
            BoardModificationController modifications, BoardPowerState powerState) {
        if (!isSymptom(observedBehavior))
            throw new IllegalStateException("Timer fault did not produce a meaningful timing symptom");
    }

    static boolean isSymptom(GeneratedObservedBehavior behavior) {
        return behavior == GeneratedObservedBehavior.TIMER_STUCK_ON ||
            behavior == GeneratedObservedBehavior.TIMER_STUCK_OFF ||
            behavior == GeneratedObservedBehavior.TIMER_TOO_SLOW ||
            behavior == GeneratedObservedBehavior.TIMER_TOO_FAST;
    }

    public GeneratedRepairStatus getRepairStatus(CirSim sim, GeneratedBoardInstance instance,
            BoardModificationController modifications, BoardPowerState powerState,
            boolean activeMeasurementOverlay) {
        if (activeMeasurementOverlay || powerState != BoardPowerState.POWERED ||
                modifications == null || modifications != sim.getBoardModificationController() ||
                !modifications.isFullyRestored())
            return GeneratedRepairStatus.STILL_FAULTED_OR_NONFUNCTIONAL;
        return GeneratedWork.complete(beginProfile(sim, instance, Profile.REPAIR));
    }

    public GeneratedObservedBehavior getObservedBehavior() { return observedBehavior; }
    double getHealthyTimingSecondsForDeveloperVerification() { return healthyTimingSeconds; }
    /** Period (free-running) or pulse width (triggered) of the last profile; NaN when unmeasured. */
    double getMeasuredTimingSecondsForDeveloperVerification() { return measuredTimingSeconds; }

    /**
     * Momentary press of the off-board START button: the contact is really
     * closed for PRESS_SECONDS of solver time and then released.
     */
    void pressStartButton(CirSim sim) {
        if (startButton == null)
            throw new IllegalStateException("This timer board has no START button");
        if (startButton.position == 0)
            throw new IllegalStateException("START button is already closed");
        startButton.toggle();
        try {
            sim.needAnalyze();
            sim.advanceGeneratedTemporalProfile(PRESS_SECONDS);
        } finally {
            if (startButton.position == 0) startButton.toggle();
            sim.needAnalyze();
            sim.analyzeCircuit();
        }
    }

    boolean isStartButtonReleased() { return startButton == null || startButton.position != 0; }

    GeneratedWork<GeneratedCustomerRetestResult> beginCustomerRetest(final CirSim sim,
            final GeneratedBoardInstance instance) {
        if (sim == null || instance == null || sim.activeMeasurementOverlay ||
                sim.getBoardModificationController() == null ||
                !sim.getBoardModificationController().isFullyRestored())
            return GeneratedWork.value(GeneratedCustomerRetestSupport.failure());
        if (sim.getBoardPowerController().getState() != BoardPowerState.POWERED)
            return GeneratedWork.value(GeneratedCustomerRetestSupport.powerRequiredFailure());
        final BoardModificationController modifications = sim.getBoardModificationController();
        final boolean physicalState = modifications.isFullyRestored();
        final GeneratedWork<GeneratedRepairStatus> profile =
            beginProfile(sim, instance, Profile.REPAIR);
        return new GeneratedWork<GeneratedCustomerRetestResult>() {
            private boolean complete;
            private boolean cancelled;
            private boolean readyToFinish;
            private GeneratedCustomerRetestResult result;

            boolean step() {
                if (cancelled) throw new IllegalStateException("Timer customer retest was cancelled");
                if (complete || readyToFinish) return false;
                if (profile.step()) return true;
                readyToFinish = true;
                return false;
            }

            GeneratedCustomerRetestResult finish() {
                if (complete) return result;
                if (cancelled || !readyToFinish)
                    throw new IllegalStateException("Timer customer retest is incomplete");
                GeneratedRepairStatus status = profile.finish();
                if (sim.getGeneratedBoardInstance() != instance ||
                        sim.getBoardModificationController() != modifications ||
                        modifications.isFullyRestored() != physicalState)
                    throw new IllegalStateException("Customer retest changed physical board state");
                result = status == GeneratedRepairStatus.CORRECTLY_RESTORED ?
                    GeneratedCustomerRetestSupport.success() :
                    GeneratedCustomerRetestSupport.failure();
                complete = true;
                return result;
            }

            void cancel() {
                if (cancelled || complete) return;
                profile.cancel();
                cancelled = true;
            }

            int getWorkUnits() { return getProfileWorkUnits(); }
        };
    }

    /** Solved OUT edges collected over one profile, relative to the power-on instant. */
    private final class EdgeRecord implements GeneratedTemporalSampler {
        final Vector<Double> rises = new Vector<Double>();
        final Vector<Double> falls = new Vector<Double>();
        double powerOnTime = Double.NaN;
        double triggerTime = Double.NaN;
        boolean started;
        boolean high;
        boolean highBeforeTrigger;
        double minimumInterval = Double.POSITIVE_INFINITY;
        double lastEdge = Double.NaN;

        public void sample(double solverTime) {
            double v = output.getElement().getPostVoltage(output.getPostIndex()) -
                ground.getElement().getPostVoltage(ground.getPostIndex());
            if (Double.isNaN(v) || Double.isInfinite(v))
                throw new IllegalStateException("Timer profile produced a non-finite OUT sample");
            double at = solverTime - powerOnTime;
            boolean now = v > nominalSupply / 2;
            if (!started) {
                started = true;
                high = now;
                if (now) rises.add(at);
            } else if (now != high) {
                if (now) rises.add(at); else falls.add(at);
                if (!Double.isNaN(lastEdge)) minimumInterval = Math.min(minimumInterval, at - lastEdge);
                lastEdge = at;
                high = now;
            }
            if (now && Double.isNaN(triggerTime)) highBeforeTrigger = true;
        }
    }

    private Classification classify(EdgeRecord record, Profile profile) {
        if (record.minimumInterval < MIN_RESOLVED_INTERVAL_SECONDS)
            return new Classification(GeneratedObservedBehavior.TIMER_TOO_FAST, Double.NaN);
        return mode == Mode.FREE_RUNNING ? classifyFreeRunning(record, profile) :
            classifyTriggered(record, profile);
    }

    private Classification classifyFreeRunning(EdgeRecord record, Profile profile) {
        Vector<Double> settledRises = new Vector<Double>();
        int settledEdges = 0;
        for (Double t : record.rises) if (t >= STARTUP_BLANK_SECONDS) settledRises.add(t);
        for (Double t : record.rises) if (t >= STARTUP_BLANK_SECONDS) settledEdges++;
        for (Double t : record.falls) if (t >= STARTUP_BLANK_SECONDS) settledEdges++;
        if (settledEdges == 0)
            return new Classification(record.high ? GeneratedObservedBehavior.TIMER_STUCK_ON :
                GeneratedObservedBehavior.TIMER_STUCK_OFF, Double.NaN);
        if (settledRises.size() < 2)
            return new Classification(GeneratedObservedBehavior.TIMER_TOO_SLOW, Double.NaN);
        double period = (settledRises.lastElement() - settledRises.firstElement()) /
            (settledRises.size() - 1);
        return compare(period, profile);
    }

    private Classification classifyTriggered(EdgeRecord record, Profile profile) {
        if (record.highBeforeTrigger)
            return new Classification(GeneratedObservedBehavior.TIMER_STUCK_ON, Double.NaN);
        double rise = Double.NaN, fall = Double.NaN;
        for (Double t : record.rises) if (t >= TRIGGER_AT_SECONDS) { rise = t; break; }
        if (Double.isNaN(rise))
            return new Classification(GeneratedObservedBehavior.TIMER_STUCK_OFF, Double.NaN);
        for (Double t : record.falls) if (t > rise) { fall = t; break; }
        if (Double.isNaN(fall))
            return new Classification(GeneratedObservedBehavior.TIMER_STUCK_ON, Double.NaN);
        return compare(fall - rise, profile);
    }

    private Classification compare(double timing, Profile profile) {
        if (profile == Profile.HEALTHY) {
            if (!(timing >= MIN_HEALTHY_INTERVAL_SECONDS))
                throw new IllegalStateException("Healthy timer is too fast to observe");
            return new Classification(GeneratedObservedBehavior.TIMER_HEALTHY, timing);
        }
        if (!healthyReferenceCaptured)
            throw new IllegalStateException("Timer healthy reference is unavailable");
        double ratio = timing / healthyTimingSeconds;
        if (Math.abs(ratio - 1) <= TIMING_TOLERANCE)
            return new Classification(GeneratedObservedBehavior.TIMER_HEALTHY, timing);
        return new Classification(ratio > 1 ? GeneratedObservedBehavior.TIMER_TOO_SLOW :
            GeneratedObservedBehavior.TIMER_TOO_FAST, timing);
    }

    private static final class Classification {
        final GeneratedObservedBehavior behavior;
        final double timing;
        Classification(GeneratedObservedBehavior behavior, double timing) {
            this.behavior = behavior; this.timing = timing;
        }
    }

    /**
     * Four-phase owner cursor: isolate and discharge, power on and observe,
     * then two more observation windows. Each step holds one bounded solver
     * advance; samples are published only from finish().
     */
    private final class TimerProfileWork extends GeneratedWork<GeneratedRepairStatus> {
        private final CirSim ownerSim;
        private final GeneratedBoardInstance ownerInstance;
        private final Object ownerGraph;
        private final Vector<CircuitElm> ownerGraphElements;
        private final PhysicalMutationReceipt ownerMutationReceipt;
        private final GeneratedChallengeController ownerChallenge;
        private final GeneratedBoardFamilyState ownerFamilyState;
        private final GeneratedDiagnosticProvider ownerProvider;
        private final GeneratedFaultBinding ownerFaultBinding;
        private final GeneratedChallengeDefinition ownerDefinition;
        private final TroubleshootBoard ownerBoard;
        private final BoardSimulationBindings ownerSimulationBindings;
        private final PhysicalBoardRuntime ownerRuntime;
        private final BoardPowerController ownerPowerController;
        private final GeneratedExternalPowerBindings ownerPowerBindings;
        private final BoardModificationController ownerModifications;
        private final BoardPowerState priorPowerState;
        private final GeneratedExternalPowerBindings.SavedControls priorControls;
        private final boolean priorPhysicalState;
        private final Profile profile;
        private final EdgeRecord record = new EdgeRecord();

        private GeneratedExternalPowerBindings.ControlObservation expectedControls;
        private GeneratedExternalPowerBindings.SavedControls expectedSavedControls;
        private BoardPowerState expectedPowerState;
        private int phase;
        private boolean complete;
        private boolean cancelled;
        private Classification local;
        private GeneratedRepairStatus result;

        TimerProfileWork(CirSim sim, GeneratedBoardInstance instance, Profile profile) {
            if (CircuitElm.sim != sim || sim.getGeneratedBoardInstance() != instance ||
                    sim.elmList == null || instance.getTemporalBehavior() != Timer555TemporalBehavior.this)
                throw new IllegalStateException("Timer temporal profile has no current owner");
            requireOwnedBy(instance);
            ownerSim = sim;
            ownerInstance = instance;
            ownerGraph = sim.elmList;
            ownerGraphElements = new Vector<CircuitElm>(sim.elmList);
            ownerChallenge = sim.getGeneratedChallengeController();
            ownerFamilyState = instance.getFamilyState();
            ownerProvider = instance.getDiagnosticProvider();
            ownerFaultBinding = instance.getFaultBinding();
            ownerDefinition = instance.getChallengeDefinition();
            ownerBoard = instance.getBoard();
            ownerSimulationBindings = instance.getSimulationBindings();
            ownerRuntime = instance.getPhysicalBoardRuntime();
            ownerMutationReceipt = ownerRuntime.getLastMutationReceipt();
            ownerPowerController = sim.getBoardPowerController();
            ownerPowerBindings = instance.getExternalPowerBindings();
            ownerModifications = sim.getBoardModificationController();
            if (ownerPowerBindings == null || ownerPowerController == null ||
                    ownerPowerController.getBindingsForDeveloperVerification() != ownerPowerBindings ||
                    ownerModifications == null)
                throw new IllegalStateException("Timer temporal profile has incomplete owner state");
            priorPowerState = ownerPowerController.getState();
            if (priorPowerState == null)
                throw new IllegalStateException("Timer temporal profile has no power state");
            if (!isStartButtonReleased())
                throw new IllegalStateException("Timer profile cannot start with START held");
            priorControls = ownerPowerBindings.saveControls();
            expectedPowerState = priorPowerState;
            rememberCurrentControls();
            priorPhysicalState = ownerModifications.isFullyRestored();
            this.profile = profile;
            if (profile == Profile.HEALTHY) {
                // Declared CircuitJS recipe for the timer graph, applied before the
                // first healthy sample; later profiles and live play inherit it.
                sim.timeStep = sim.maxTimeStep = SOLVER_MAX_STEP_SECONDS;
                sim.minTimeStep = SOLVER_MIN_STEP_SECONDS;
                sim.adjustTimeStep = true;
            }
            requireCurrent("begin");
        }

        boolean step() {
            if (cancelled) throw new IllegalStateException("Timer temporal profile was cancelled");
            if (complete || phase >= PHASE_COUNT) return false;
            requireCurrent("phase " + phase + " before");
            if (phase == 0) {
                ownerSim.setBoardPowerStateForGeneratedTemporalProfile(BoardPowerState.UNPOWERED);
                expectedPowerState = BoardPowerState.UNPOWERED;
                rememberCurrentControls();
                ownerSim.advanceGeneratedTemporalProfile(OFF_SETTLE_SECONDS);
            } else if (phase == 1) {
                ownerSim.setBoardPowerStateForGeneratedTemporalProfile(BoardPowerState.POWERED);
                expectedPowerState = BoardPowerState.POWERED;
                rememberCurrentControls();
                record.powerOnTime = ownerSim.t;
                if (mode == Mode.FREE_RUNNING) {
                    ownerSim.advanceGeneratedTemporalProfileSampled(WINDOW_SECONDS,
                        SAMPLE_SLICE_SECONDS, record);
                } else {
                    ownerSim.advanceGeneratedTemporalProfileSampled(TRIGGER_AT_SECONDS,
                        SAMPLE_SLICE_SECONDS, record);
                    record.triggerTime = ownerSim.t - record.powerOnTime;
                    // Like profile power transitions, each sampled advance re-analyzes
                    // the graph first, so the contact change needs no queued analysis.
                    startButton.toggle();
                    try {
                        ownerSim.advanceGeneratedTemporalProfileSampled(PRESS_SECONDS,
                            SAMPLE_SLICE_SECONDS, record);
                    } finally {
                        if (startButton.position == 0) startButton.toggle();
                    }
                    ownerSim.advanceGeneratedTemporalProfileSampled(
                        WINDOW_SECONDS - TRIGGER_AT_SECONDS - PRESS_SECONDS,
                        SAMPLE_SLICE_SECONDS, record);
                }
            } else {
                ownerSim.advanceGeneratedTemporalProfileSampled(WINDOW_SECONDS,
                    SAMPLE_SLICE_SECONDS, record);
                if (phase == PHASE_COUNT - 1) {
                    local = classify(record, profile);
                    if (profile == Profile.HEALTHY &&
                            local.behavior != GeneratedObservedBehavior.TIMER_HEALTHY)
                        throw new IllegalStateException("Healthy timer graph did not produce " +
                            "observable timing: " + local.behavior);
                }
            }
            phase++;
            requireCurrent("phase " + (phase - 1) + " after");
            return phase < PHASE_COUNT;
        }

        GeneratedRepairStatus finish() {
            if (complete) return result;
            if (cancelled || phase < PHASE_COUNT || local == null)
                throw new IllegalStateException("Timer temporal profile is incomplete");
            requireCurrent("finish");
            observedBehavior = local.behavior;
            measuredTimingSeconds = local.timing;
            if (profile == Profile.HEALTHY) {
                healthyTimingSeconds = local.timing;
                healthyReferenceCaptured = true;
                result = GeneratedRepairStatus.CORRECTLY_RESTORED;
            } else if (profile == Profile.FAULTED) {
                result = GeneratedRepairStatus.STILL_FAULTED_OR_NONFUNCTIONAL;
            } else {
                result = local.behavior == GeneratedObservedBehavior.TIMER_HEALTHY ?
                    GeneratedRepairStatus.CORRECTLY_RESTORED :
                    GeneratedRepairStatus.STILL_FAULTED_OR_NONFUNCTIONAL;
            }
            complete = true;
            return result;
        }

        void cancel() {
            if (cancelled || complete) return;
            if (canRestorePriorControls()) {
                ownerPowerController.restoreForDeveloperVerification(ownerPowerBindings,
                    priorPowerState, priorControls);
                if (isCurrentOwnerIdentity()) ownerRuntime.onBoardPowerStateChanged(priorPowerState);
            }
            cancelled = true;
        }

        int getWorkUnits() { return PHASE_COUNT; }

        private void rememberCurrentControls() {
            expectedControls = ownerPowerBindings.observeControls();
            expectedSavedControls = ownerPowerBindings.saveControls();
        }

        private void requireCurrent(String stage) {
            if (!isCurrentOwnerIdentity() || ownerPowerController.getState() != expectedPowerState ||
                    ownerModifications.isFullyRestored() != priorPhysicalState ||
                    !controlsAreCurrent() || !isStartButtonReleased())
                throw new IllegalStateException("Timer temporal profile lost its owner at " + stage);
            if (ownerSim.maxTimeStep != SOLVER_MAX_STEP_SECONDS ||
                    ownerSim.minTimeStep != SOLVER_MIN_STEP_SECONDS || !ownerSim.adjustTimeStep)
                throw new IllegalStateException("Timer profile lost its solver recipe at " + stage);
            requireOwnedBy(ownerInstance);
        }

        private boolean controlsAreCurrent() {
            try {
                return expectedControls != null && expectedControls.isCurrent() &&
                    expectedSavedControls != null && expectedSavedControls.matches();
            } catch (Throwable ignored) {
                return false;
            }
        }

        private boolean canRestorePriorControls() {
            try {
                return isCurrentOwnerIdentity() && ownerPowerController.getState() == expectedPowerState &&
                    ownerModifications.isFullyRestored() == priorPhysicalState && controlsAreCurrent();
            } catch (Throwable ignored) {
                return false;
            }
        }

        private boolean isCurrentOwnerIdentity() {
            if (ownerSim.getGeneratedBoardInstance() != ownerInstance || ownerSim.elmList != ownerGraph ||
                    CircuitElm.sim != ownerSim || ownerSim.elmList.size() != ownerGraphElements.size())
                return false;
            for (int i = 0; i < ownerGraphElements.size(); i++)
                if (ownerSim.elmList.get(i) != ownerGraphElements.get(i)) return false;
            return ownerSim.getGeneratedChallengeController() == ownerChallenge &&
                ownerSim.getBoardModificationController() == ownerModifications &&
                ownerSim.getBoardPowerController() == ownerPowerController &&
                ownerPowerController.getBindingsForDeveloperVerification() == ownerPowerBindings &&
                ownerInstance.getBoard() == ownerBoard &&
                ownerInstance.getSimulationBindings() == ownerSimulationBindings &&
                ownerInstance.getExternalPowerBindings() == ownerPowerBindings &&
                ownerInstance.getPhysicalBoardRuntime() == ownerRuntime &&
                ownerRuntime.getLastMutationReceipt() == ownerMutationReceipt &&
                ownerInstance.getFamilyState() == ownerFamilyState &&
                ownerInstance.getDiagnosticProvider() == ownerProvider &&
                ownerInstance.getTemporalBehavior() == Timer555TemporalBehavior.this &&
                ownerInstance.getFaultBinding() == ownerFaultBinding &&
                ownerInstance.getChallengeDefinition() == ownerDefinition;
        }
    }
}
