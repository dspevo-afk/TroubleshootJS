package com.lushprojects.circuitjs1.client;

/**
 * Fixed E06 pilot: isolated cycle-mean delivery before the external output L/C.
 * This is not a switching waveform or a regulated 12 V source. The initial
 * candidate omits weighted rectifier/freewheel drops; comparison must measure
 * that approximation. External storage, freewheel and feedback remain real.
 */
final class E06AveragedConverterElm extends CircuitElm {
    // Current-only codec. Increment when this fixed model's interpretation changes.
    static final int DUMP_TYPE = 458;
    static final String DUMP_VERSION = "1", DUMP_KIND = "e06-averaged";
    private static final double MAX_STEP_SECONDS = .0004;
    private static final double MAX_NEWTON_DROP_STEP_VOLTS = .5;
    private static final double RESTART_TARGET_STEP_VOLTS = .25;
    private static final double CURRENT_TOLERANCE_AMPS = 1e-8;
    private static final double VOLTAGE_TOLERANCE = 1e-6;
    private static final double MIN_COMMUTATION_VOLTS = -2;
    private final E06ConverterContract contract;
    private final Point[] posts = new Point[6];

    // Committed control is prepared for the next solve. No Newton trial commits it.
    private boolean active, stepActive;
    private double activeSeconds, duty, stepActiveSeconds, stepDuty;
    private double integral, stepIntegral, acceptedIntegral;
    private long cycleIndex, stepCycleIndex;
    private boolean acceptedActive;
    private double acceptedActiveSeconds, acceptedDuty;
    private long acceptedCycleIndex;

    // Retain the actual last Norton tangent, not the next cycle's control law.
    private double evaluationInput, evaluationOutput, driveAtEvaluation;
    private double driveInputGain, driveOutputGain;
    private double inputAtEvaluation, inputInputGain, inputOutputGain;
    private double lastTarget = Double.NaN, lastDrop = Double.NaN, lastInput = Double.NaN;
    private double acceptedInputCurrent, acceptedOutputCurrent;
    private double acceptedEnableCurrent, acceptedFeedbackCurrent, acceptedDriveCurrent;
    private double acceptedInputVolts, acceptedOutputVolts, acceptedEnableVolts, acceptedFeedbackVolts;
    private boolean tangentReady, receiptReady;
    // Only the immediately preceding Newton stamp in this solve can certify
    // its input constitutive residual. Accepted predictors are separate.
    private int inputStampSubIteration = -1;
    private double inputStampTime = Double.NaN, inputStampTimeStep = Double.NaN;

    // The model prepares a predictor; the executor must confirm global acceptance.
    // Trial caches remain scratch; a rejected trial never becomes the next seed.
    private double predictorTarget, predictorDrop, predictorInput;
    private double predictorTime, predictorTimeStep;
    private boolean predictorReady;
    // Completed model callbacks remain provisional until the executor commits global acceptance.
    private boolean pendingAcceptedReady;
    private double pendingInputVolts, pendingOutputVolts, pendingEnableVolts, pendingFeedbackVolts;
    private double pendingInputCurrent, pendingOutputCurrent, pendingEnableCurrent, pendingFeedbackCurrent, pendingDriveCurrent;
    private boolean pendingAcceptedActive;
    private double pendingAcceptedActiveSeconds, pendingAcceptedDuty, pendingAcceptedIntegral;
    private long pendingAcceptedCycleIndex;
    private boolean pendingActive;
    private double pendingActiveSeconds, pendingDuty, pendingIntegral;
    private long pendingCycleIndex;
    private double pendingPredictorTarget, pendingPredictorDrop, pendingPredictorInput;
    private double pendingPredictorTime, pendingPredictorTimeStep;
    private boolean pendingPredictorReady;

    E06AveragedConverterElm(int x, int y, E06ConverterContract contract) {
        super(x, y);
        if (contract == null) throw new IllegalArgumentException("Missing E06 converter contract");
        this.contract = contract;
        x2 = x + 160;
        setPoints();
    }

    E06AveragedConverterElm(int x, int y, int x2, int y2, int flags, StringTokenizer tokens) {
        super(x, y, x2, y2, flags);
        requireCurrentDump(tokens, DUMP_VERSION, DUMP_KIND);
        contract = new E06ConverterContract();
        setPoints();
    }

    static void requireCurrentDump(StringTokenizer tokens, String version, String kind) {
        if (tokens == null || !tokens.hasMoreTokens() || !version.equals(tokens.nextToken()) ||
                !tokens.hasMoreTokens() || !kind.equals(tokens.nextToken()) || tokens.hasMoreTokens())
            throw new IllegalArgumentException("Unsupported or malformed E06 model dump");
    }

    int getDumpType() { return DUMP_TYPE; }
    E06ConverterContract getContract() { return contract; }
    int getPostCount() { return 6; }
    Point getPost(int n) {
        if (n < 0 || n >= posts.length) throw new IllegalArgumentException("Invalid E06 converter post");
        return posts[n];
    }
    void setPoints() {
        super.setPoints();
        posts[E06ConverterContract.IN_PLUS] = point1;
        posts[E06ConverterContract.IN_MINUS] = new Point(x, y + 96);
        posts[E06ConverterContract.PRE_L_PLUS] = point2;
        posts[E06ConverterContract.OUT_MINUS] = new Point(x2, y2 + 96);
        posts[E06ConverterContract.ENABLE] = new Point(x + 48, y - 48);
        posts[E06ConverterContract.FEEDBACK] = new Point(x + 96, y - 48);
    }
    // U06 reload restarts control; no accepted/trial solver state is serialized.
    String dump() { return super.dump() + " " + DUMP_VERSION + " " + DUMP_KIND; }
    boolean nonLinear() { return true; }
    boolean hasGroundConnection(int n) { return false; }
    boolean getConnection(int first, int second) {
        boolean firstSecondary = first == E06ConverterContract.PRE_L_PLUS || first == E06ConverterContract.OUT_MINUS;
        boolean secondSecondary = second == E06ConverterContract.PRE_L_PLUS || second == E06ConverterContract.OUT_MINUS;
        return firstSecondary == secondSecondary;
    }

    void stamp() {
        // Analysis, graph changes and adaptive restamps all start conservatively.
        predictorReady = false;
        inputStampSubIteration = -1;
        for (int node : nodes) sim.stampNonLinear(node);
        sim.stampResistor(nodes[0], nodes[1], E06ConverterContract.INPUT_LEAK_OHMS);
        sim.stampResistor(nodes[4], nodes[1], E06ConverterContract.CONTROL_LEAK_OHMS);
        sim.stampResistor(nodes[5], nodes[1], E06ConverterContract.CONTROL_LEAK_OHMS);
        // Always passive, including disabled and externally charged output states.
        sim.stampResistor(nodes[2], nodes[3], E06ConverterContract.OUTPUT_LEAK_OHMS);
    }

    void startIteration() {
        clearPendingAcceptedState();
        requireStep();
        inputStampSubIteration = -1;
        stepActive = active;
        stepDuty = duty;
        stepIntegral = integral;
        stepActiveSeconds = activeSeconds;
        stepCycleIndex = cycleIndex;
        if (canUseAcceptedNewtonPredictor()) {
            lastTarget = predictorTarget;
            lastDrop = predictorDrop;
            lastInput = predictorInput;
        } else {
            lastTarget = lastDrop = lastInput = Double.NaN;
        }
    }

    boolean canUseAcceptedNewtonPredictor() {
        return predictorReady && receiptReady && sim.solverExecutor.hasAcceptedStepAt(predictorTime) &&
            active && active == acceptedActive &&
            duty == acceptedDuty && sim.t == predictorTime && sim.timeStep == predictorTimeStep &&
            getInputVoltage() == acceptedInputVolts && getOutputVoltage() == acceptedOutputVolts &&
            getEnableVoltage() == acceptedEnableVolts && getFeedbackVoltage() == acceptedFeedbackVolts &&
            E06ConverterContract.finite(predictorTarget) && E06ConverterContract.finite(predictorDrop) &&
            E06ConverterContract.finite(predictorInput);
    }

    void doStep() {
        double vin = getInputVoltage(), output = getOutputVoltage();
        if (!E06ConverterContract.finite(vin) || !E06ConverterContract.finite(output))
            throw new IllegalArgumentException("Nonfinite E06 converter trial");
        // Preserve the tangent that produced these solved nodes before this
        // trial replaces it. A new timestep/control snapshot or graph restamp
        // invalidates this history even when an accepted predictor is reusable.
        boolean previousInputStampReady = tangentReady && sim.subIterations > 0 &&
            inputStampSubIteration == sim.subIterations - 1 &&
            inputStampTime == sim.t && inputStampTimeStep == sim.timeStep;
        double previousEvaluationInput = evaluationInput;
        double previousEvaluationOutput = evaluationOutput;
        double previousInputAtEvaluation = inputAtEvaluation;
        double previousInputInputGain = inputInputGain;
        double previousInputOutputGain = inputOutputGain;
        double gain = stepActive && vin > 0 ? stepDuty * E06ConverterContract.SECONDARY_OVER_PRIMARY_TURNS : 0;
        double target = gain * vin;
        boolean driving = gain > 0;
        double requestedDrop = target - output;
        double drop = driving ? limitNewtonDrop(requestedDrop, target) : requestedDrop;
        evaluationInput = vin;
        evaluationOutput = target - drop;
        double resistance = E06ConverterContract.OUTPUT_RESISTANCE_OHMS;
        double knee = resistance * E06ConverterContract.MAX_OUTPUT_CURRENT_AMPS;
        driveAtEvaluation = driving ? Math.max(0, Math.min(E06ConverterContract.MAX_OUTPUT_CURRENT_AMPS, drop / resistance)) : 0;
        double conductance = driving && drop >= 0 && drop <= knee ? 1 / resistance : 0;
        driveInputGain = conductance * gain;
        driveOutputGain = -conductance;
        sim.stampConductance(nodes[2], nodes[3], conductance);
        sim.stampVCCurrentSource(nodes[2], nodes[3], nodes[0], nodes[1], -driveInputGain);
        sim.stampCurrentSource(nodes[2], nodes[3], -driveAtEvaluation +
            driveInputGain * vin + driveOutputGain * evaluationOutput);

        // Actual signed PRE_L drive power includes charging all external storage.
        // R loss is explicit once. Efficiency describes only the remaining loss
        // on positive drive power; negative port power is absorbed, never regenerated.
        double portPower = evaluationOutput * driveAtEvaluation;
        double positivePower = Math.max(0, portPower);
        double conversionPower = positivePower / E06ConverterContract.EFFICIENCY +
            resistance * driveAtEvaluation * driveAtEvaluation;
        double powerInputGain = (portPower > 0 ? evaluationOutput * driveInputGain / E06ConverterContract.EFFICIENCY : 0) +
            2 * resistance * driveAtEvaluation * driveInputGain;
        double powerOutputGain = (portPower > 0 ? (driveAtEvaluation + evaluationOutput * driveOutputGain) /
            E06ConverterContract.EFFICIENCY : 0) + 2 * resistance * driveAtEvaluation * driveOutputGain;
        double quiescent = E06ConverterContract.quiescentFor(vin);
        double quiescentGain = vin > 0 && vin < E06ConverterContract.BIAS_VOLTS ?
            E06ConverterContract.QUIESCENT_AMPS / E06ConverterContract.BIAS_VOLTS : 0;
        inputAtEvaluation = quiescent;
        inputInputGain = quiescentGain;
        inputOutputGain = 0;
        if (vin > 0 && driving) {
            inputAtEvaluation += conversionPower / vin;
            inputInputGain += powerInputGain / vin - conversionPower / (vin * vin);
            inputOutputGain = powerOutputGain / vin;
        }
        sim.stampConductance(nodes[0], nodes[1], inputInputGain);
        sim.stampVCCurrentSource(nodes[0], nodes[1], nodes[2], nodes[3], inputOutputGain);
        sim.stampCurrentSource(nodes[0], nodes[1], inputAtEvaluation -
            inputInputGain * vin - inputOutputGain * evaluationOutput);
        tangentReady = true;
        inputStampSubIteration = sim.subIterations;
        inputStampTime = sim.t;
        inputStampTimeStep = sim.timeStep;
        // Keep receiptReady bound to the last globally accepted receipt. Trial
        // currents are computed by the private trial helpers until acceptance.
        double physicalDrive = driving ? Math.max(0,
            Math.min(E06ConverterContract.MAX_OUTPUT_CURRENT_AMPS, requestedDrop / resistance)) : 0;
        double trialDrive = trialDriveCurrent();
        boolean targetChanged = changed(lastTarget, target, VOLTAGE_TOLERANCE);
        // The output-drop voltage only affects this Norton tangent in the
        // unsaturated current region. In the off and current-clamped regions
        // the stamped output current has zero drop derivative; requiring the
        // otherwise irrelevant voltage to settle can prevent global Newton
        // convergence while the physical current residual is already exact.
        boolean dropChanged = conductance > 0 && changed(lastDrop, drop, VOLTAGE_TOLERANCE);
        double previousInputPrediction = previousInputAtEvaluation +
            previousInputInputGain * (vin - previousEvaluationInput) +
            previousInputOutputGain * (output - previousEvaluationOutput);
        double physicalInput = quiescent;
        if (vin > 0 && driving)
            physicalInput += (Math.max(0, output * physicalDrive) / E06ConverterContract.EFFICIENCY +
                resistance * physicalDrive * physicalDrive) / vin;
        // Current movement is not a KCL error. Compare the physical law at
        // the raw solved nodes with the prior Norton stamp that produced them.
        // The fixed input leak is separately stamped and exactly linear.
        boolean inputResidualBad = !previousInputStampReady ||
            !E06ConverterContract.finite(previousInputPrediction) ||
            !E06ConverterContract.finite(physicalInput) ||
            changed(previousInputPrediction, physicalInput, 1e-9);
        // Clipping within the all-off branch changes neither port stamp:
        // output drive is zero and input draw is quiescent only. Saturated
        // current and conducting-side knees retain the original veto.
        boolean boundedTrial = Math.abs(drop - requestedDrop) > VOLTAGE_TOLERANCE &&
            (conductance > 0 || driveAtEvaluation != 0 || physicalDrive != 0);
        double driveResidual = Math.abs(trialDrive - physicalDrive);
        // At either limiter knee the accepted tangent must also represent the
        // bounded characteristic; a voltage-only test could accept a small
        // current overshoot on the neighboring regulation tangent.
        if (targetChanged || dropChanged || inputResidualBad || boundedTrial ||
                driveResidual > 1e-9)
            sim.converged = false;
        lastTarget = target;
        lastDrop = drop;
        lastInput = inputAtEvaluation;
    }

    void stepFinished() {
        clearPendingAcceptedState();
        // Prepare the electrical receipt locally. The executor may still reject this
        // globally converged step in a later model callback, accepted event or boundary.
        double inputVolts = getInputVoltage();
        double outputVolts = getOutputVoltage();
        double enableVolts = getEnableVoltage();
        double feedbackVolts = getFeedbackVoltage();
        double driveCurrent = trialDriveCurrent();
        double inputCurrent = trialInputCurrent();
        double outputCurrent = driveCurrent - outputVolts / E06ConverterContract.OUTPUT_LEAK_OHMS;
        double enableCurrent = enableVolts / E06ConverterContract.CONTROL_LEAK_OHMS;
        double feedbackCurrent = feedbackVolts / E06ConverterContract.CONTROL_LEAK_OHMS;
        requireAcceptedEnvelope(inputVolts, outputVolts, enableVolts, feedbackVolts,
            driveCurrent, inputCurrent, outputCurrent, enableCurrent, feedbackCurrent);

        // Preserve the successful control calculation order; only its publication is deferred.
        boolean nextActive = E06ConverterContract.nextActive(active, inputVolts, enableVolts);
        double nextActiveSeconds = nextActive ? (active ? activeSeconds : 0) + sim.timeStep : 0;
        double nextIntegral = E06ConverterContract.nextIntegral(integral, inputVolts, feedbackVolts,
            nextActiveSeconds, sim.timeStep, nextActive);
        long nextCycleIndex = E06ConverterContract.cycleIndex(sim.t);
        double nextDuty = duty;
        if (!nextActive) nextDuty = 0;
        else if (nextCycleIndex != cycleIndex) nextDuty = E06ConverterContract.dutyFor(
            inputVolts, feedbackVolts, nextActiveSeconds, nextIntegral);

        pendingInputVolts = inputVolts;
        pendingOutputVolts = outputVolts;
        pendingEnableVolts = enableVolts;
        pendingFeedbackVolts = feedbackVolts;
        pendingInputCurrent = inputCurrent;
        pendingOutputCurrent = outputCurrent;
        pendingEnableCurrent = enableCurrent;
        pendingFeedbackCurrent = feedbackCurrent;
        pendingDriveCurrent = driveCurrent;
        pendingAcceptedActive = stepActive;
        pendingAcceptedDuty = stepDuty;
        pendingAcceptedIntegral = stepIntegral;
        pendingAcceptedActiveSeconds = stepActiveSeconds;
        pendingAcceptedCycleIndex = stepCycleIndex;
        pendingActive = nextActive;
        pendingActiveSeconds = nextActiveSeconds;
        pendingIntegral = nextIntegral;
        pendingDuty = nextDuty;
        pendingCycleIndex = nextCycleIndex;
        pendingPredictorTarget = lastTarget;
        pendingPredictorDrop = lastDrop;
        pendingPredictorInput = lastInput;
        pendingPredictorTime = sim.t;
        pendingPredictorTimeStep = sim.timeStep;
        pendingPredictorReady = true;
        pendingAcceptedReady = true;
    }

    /** Called only after solver events and the global acceptance boundary succeed. */
    void solverStepAccepted() {
        if (!pendingAcceptedReady) return;
        acceptedInputVolts = pendingInputVolts;
        acceptedOutputVolts = pendingOutputVolts;
        acceptedEnableVolts = pendingEnableVolts;
        acceptedFeedbackVolts = pendingFeedbackVolts;
        acceptedInputCurrent = pendingInputCurrent;
        acceptedOutputCurrent = pendingOutputCurrent;
        acceptedEnableCurrent = pendingEnableCurrent;
        acceptedFeedbackCurrent = pendingFeedbackCurrent;
        acceptedDriveCurrent = pendingDriveCurrent;
        acceptedActive = pendingAcceptedActive;
        acceptedDuty = pendingAcceptedDuty;
        acceptedIntegral = pendingAcceptedIntegral;
        acceptedActiveSeconds = pendingAcceptedActiveSeconds;
        acceptedCycleIndex = pendingAcceptedCycleIndex;
        receiptReady = true;
        active = pendingActive;
        activeSeconds = pendingActiveSeconds;
        integral = pendingIntegral;
        duty = pendingDuty;
        cycleIndex = pendingCycleIndex;
        predictorTarget = pendingPredictorTarget;
        predictorDrop = pendingPredictorDrop;
        predictorInput = pendingPredictorInput;
        predictorTime = pendingPredictorTime;
        predictorTimeStep = pendingPredictorTimeStep;
        predictorReady = pendingPredictorReady;
        clearPendingAcceptedState();
    }

    /** Immutable view of committed E06 memory for a Developer owner-boundary canary. */
    static final class AcceptedStateSnapshot {
        private final E06AveragedConverterElm owner;
        private final boolean receiptReady, acceptedActive, active, predictorReady;
        private final double acceptedInputCurrent, acceptedOutputCurrent;
        private final double acceptedEnableCurrent, acceptedFeedbackCurrent, acceptedDriveCurrent;
        private final double acceptedInputVolts, acceptedOutputVolts;
        private final double acceptedEnableVolts, acceptedFeedbackVolts;
        private final double acceptedActiveSeconds, acceptedDuty, acceptedIntegral;
        private final long acceptedCycleIndex;
        private final double activeSeconds, duty, integral;
        private final long cycleIndex;
        private final double predictorTarget, predictorDrop, predictorInput;
        private final double predictorTime, predictorTimeStep;

        private AcceptedStateSnapshot(E06AveragedConverterElm owner) {
            this.owner = owner;
            receiptReady = owner.receiptReady;
            acceptedInputCurrent = owner.acceptedInputCurrent;
            acceptedOutputCurrent = owner.acceptedOutputCurrent;
            acceptedEnableCurrent = owner.acceptedEnableCurrent;
            acceptedFeedbackCurrent = owner.acceptedFeedbackCurrent;
            acceptedDriveCurrent = owner.acceptedDriveCurrent;
            acceptedInputVolts = owner.acceptedInputVolts;
            acceptedOutputVolts = owner.acceptedOutputVolts;
            acceptedEnableVolts = owner.acceptedEnableVolts;
            acceptedFeedbackVolts = owner.acceptedFeedbackVolts;
            acceptedActive = owner.acceptedActive;
            acceptedActiveSeconds = owner.acceptedActiveSeconds;
            acceptedDuty = owner.acceptedDuty;
            acceptedIntegral = owner.acceptedIntegral;
            acceptedCycleIndex = owner.acceptedCycleIndex;
            active = owner.active;
            activeSeconds = owner.activeSeconds;
            duty = owner.duty;
            integral = owner.integral;
            cycleIndex = owner.cycleIndex;
            predictorReady = owner.predictorReady;
            predictorTarget = owner.predictorTarget;
            predictorDrop = owner.predictorDrop;
            predictorInput = owner.predictorInput;
            predictorTime = owner.predictorTime;
            predictorTimeStep = owner.predictorTimeStep;
        }
    }

    AcceptedStateSnapshot captureAcceptedStateForDeveloperVerification() {
        return new AcceptedStateSnapshot(this);
    }

    boolean matchesAcceptedStateForDeveloperVerification(AcceptedStateSnapshot snapshot) {
        return snapshot != null && snapshot.owner == this &&
            receiptReady == snapshot.receiptReady &&
            sameExact(acceptedInputCurrent, snapshot.acceptedInputCurrent) &&
            sameExact(acceptedOutputCurrent, snapshot.acceptedOutputCurrent) &&
            sameExact(acceptedEnableCurrent, snapshot.acceptedEnableCurrent) &&
            sameExact(acceptedFeedbackCurrent, snapshot.acceptedFeedbackCurrent) &&
            sameExact(acceptedDriveCurrent, snapshot.acceptedDriveCurrent) &&
            sameExact(acceptedInputVolts, snapshot.acceptedInputVolts) &&
            sameExact(acceptedOutputVolts, snapshot.acceptedOutputVolts) &&
            sameExact(acceptedEnableVolts, snapshot.acceptedEnableVolts) &&
            sameExact(acceptedFeedbackVolts, snapshot.acceptedFeedbackVolts) &&
            acceptedActive == snapshot.acceptedActive &&
            sameExact(acceptedActiveSeconds, snapshot.acceptedActiveSeconds) &&
            sameExact(acceptedDuty, snapshot.acceptedDuty) &&
            sameExact(acceptedIntegral, snapshot.acceptedIntegral) &&
            acceptedCycleIndex == snapshot.acceptedCycleIndex &&
            active == snapshot.active &&
            sameExact(activeSeconds, snapshot.activeSeconds) &&
            sameExact(duty, snapshot.duty) &&
            sameExact(integral, snapshot.integral) &&
            cycleIndex == snapshot.cycleIndex &&
            predictorReady == snapshot.predictorReady &&
            sameExact(predictorTarget, snapshot.predictorTarget) &&
            sameExact(predictorDrop, snapshot.predictorDrop) &&
            sameExact(predictorInput, snapshot.predictorInput) &&
            sameExact(predictorTime, snapshot.predictorTime) &&
            sameExact(predictorTimeStep, snapshot.predictorTimeStep);
    }

    boolean hasPendingAcceptedStateForDeveloperVerification() {
        return pendingAcceptedReady || pendingPredictorReady;
    }

    /** Reports stored historical predictor data; the live executor reuse permit is checked separately. */
    boolean hasAcceptedPredictorForDeveloperVerification() {
        return receiptReady && predictorReady &&
            E06ConverterContract.finite(predictorTarget) &&
            E06ConverterContract.finite(predictorDrop) &&
            E06ConverterContract.finite(predictorInput) &&
            E06ConverterContract.finite(predictorTime) &&
            E06ConverterContract.finite(predictorTimeStep) &&
            predictorTimeStep > 0 && predictorTimeStep <= MAX_STEP_SECONDS;
    }

    private static boolean sameExact(double first, double second) {
        return Double.doubleToLongBits(first) == Double.doubleToLongBits(second);
    }

    private void clearPendingAcceptedState() {
        pendingAcceptedReady = false;
        pendingPredictorReady = false;
    }

    double getDuty() { return acceptedDuty; }
    double getIntegral() { return acceptedIntegral; }
    boolean isActive() { return acceptedActive; }
    double getActiveSeconds() { return acceptedActiveSeconds; }
    long getCycleIndex() { return acceptedCycleIndex; }
    double getInputVoltage() { return volts[0] - volts[1]; }
    double getOutputVoltage() { return volts[2] - volts[3]; }
    double getEnableVoltage() { return volts[4] - volts[1]; }
    double getFeedbackVoltage() { return volts[5] - volts[1]; }
    double getInputCurrent() { return receiptReady ? acceptedInputCurrent : trialInputCurrent(); }
    double getOutputCurrent() {
        return receiptReady ? acceptedOutputCurrent : trialDriveCurrent() - getOutputVoltage() / E06ConverterContract.OUTPUT_LEAK_OHMS;
    }
    double getInputPowerWatts() { return acceptedInputVolts * acceptedInputCurrent; }
    double getOutputPowerWatts() { return acceptedOutputVolts * acceptedOutputCurrent; }
    double getDeclaredResistanceLossWatts() {
        return E06ConverterContract.OUTPUT_RESISTANCE_OHMS * acceptedDriveCurrent * acceptedDriveCurrent;
    }
    double getDeclaredConversionLossWatts() {
        return Math.max(0, acceptedOutputVolts * acceptedDriveCurrent) * (1 / E06ConverterContract.EFFICIENCY - 1);
    }
    double getAbsorbedOutputPowerWatts() { return Math.max(0, -acceptedOutputVolts * acceptedDriveCurrent); }
    double getPowerLossWatts() {
        return getInputPowerWatts() + acceptedEnableVolts * acceptedEnableCurrent +
            acceptedFeedbackVolts * acceptedFeedbackCurrent - getOutputPowerWatts();
    }
    double getVoltageDiff() { return getOutputVoltage(); }
    // CircuitJS calls these physical-current accessors before the executor's
    // acceptance hook. When a validated step is staged, report that solved
    // vector so scopes and wire-current processing do not lag one accepted step.
    // After a rejected step, this raw-solver view can remain provisional until
    // the next startIteration/reset; semantic receipt getters stay committed.
    double getCurrent() {
        return pendingAcceptedReady ? pendingInputCurrent : getInputCurrent();
    }
    double getPower() { return getPowerLossWatts(); }
    double getCurrentIntoNode(int n) {
        double input = pendingAcceptedReady ? pendingInputCurrent : getInputCurrent();
        double output = pendingAcceptedReady ? pendingOutputCurrent : getOutputCurrent();
        double enable = pendingAcceptedReady ? pendingEnableCurrent : receiptReady ? acceptedEnableCurrent :
            getEnableVoltage() / E06ConverterContract.CONTROL_LEAK_OHMS;
        double feedback = pendingAcceptedReady ? pendingFeedbackCurrent : receiptReady ? acceptedFeedbackCurrent :
            getFeedbackVoltage() / E06ConverterContract.CONTROL_LEAK_OHMS;
        if (n == 0) return -input;
        if (n == 1) return input + enable + feedback;
        if (n == 2) return output;
        if (n == 3) return -output;
        if (n == 4) return -enable;
        if (n == 5) return -feedback;
        throw new IllegalArgumentException("Invalid E06 current post");
    }

    void reset() {
        super.reset();
        clearPendingAcceptedState();
        active = stepActive = acceptedActive = false;
        activeSeconds = duty = stepActiveSeconds = stepDuty = acceptedActiveSeconds = acceptedDuty = 0;
        integral = stepIntegral = acceptedIntegral = 0;
        cycleIndex = stepCycleIndex = acceptedCycleIndex = 0;
        acceptedInputCurrent = acceptedOutputCurrent = acceptedEnableCurrent = acceptedFeedbackCurrent = acceptedDriveCurrent = 0;
        acceptedInputVolts = acceptedOutputVolts = acceptedEnableVolts = acceptedFeedbackVolts = 0;
        lastTarget = lastDrop = lastInput = Double.NaN;
        tangentReady = receiptReady = false;
        inputStampSubIteration = -1;
        inputStampTime = inputStampTimeStep = Double.NaN;
        predictorReady = false;
        predictorTarget = predictorDrop = predictorInput = Double.NaN;
        predictorTime = predictorTimeStep = Double.NaN;
    }
    private double trialDriveCurrent() {
        return tangentReady ? driveAtEvaluation + driveInputGain * (getInputVoltage() - evaluationInput) +
            driveOutputGain * (getOutputVoltage() - evaluationOutput) : 0;
    }
    private double trialInputCurrent() {
        double vin = getInputVoltage();
        return vin / E06ConverterContract.INPUT_LEAK_OHMS + (tangentReady ? inputAtEvaluation +
            inputInputGain * (vin - evaluationInput) + inputOutputGain * (getOutputVoltage() - evaluationOutput) : 0);
    }
    private double limitNewtonDrop(double requested, double target) {
        if (!E06ConverterContract.finite(lastDrop) || !E06ConverterContract.finite(lastTarget) ||
                Math.abs(target - lastTarget) > RESTART_TARGET_STEP_VOLTS) return 0;
        double knee = E06ConverterContract.OUTPUT_RESISTANCE_OHMS * E06ConverterContract.MAX_OUTPUT_CURRENT_AMPS;
        double drop = requested;
        if (lastDrop <= knee && drop > knee) drop = lastDrop < knee ? knee : knee + Math.min(MAX_NEWTON_DROP_STEP_VOLTS, drop - knee);
        else if (lastDrop > knee && drop <= knee) drop = knee;
        if (Math.abs(drop - lastDrop) > MAX_NEWTON_DROP_STEP_VOLTS)
            drop = lastDrop + (drop > lastDrop ? MAX_NEWTON_DROP_STEP_VOLTS : -MAX_NEWTON_DROP_STEP_VOLTS);
        return drop;
    }
    private void requireStep() {
        if (!E06ConverterContract.finite(sim.timeStep) || sim.timeStep <= 0 || sim.timeStep > MAX_STEP_SECONDS)
            throw new IllegalArgumentException("E06 averaged pilot requires an accepted timestep in (0, 400 us]");
    }
    private void requireAcceptedEnvelope(double inputVolts, double outputVolts,
            double enableVolts, double feedbackVolts, double driveCurrent,
            double inputCurrent, double outputCurrent, double enableCurrent,
            double feedbackCurrent) {
        if (!E06ConverterContract.finite(inputVolts) || !E06ConverterContract.finite(outputVolts) ||
                !E06ConverterContract.finite(enableVolts) || !E06ConverterContract.finite(feedbackVolts) ||
                !E06ConverterContract.finite(inputCurrent) || !E06ConverterContract.finite(outputCurrent))
            throw new IllegalArgumentException("Nonfinite accepted E06 averaged port");
        if (inputVolts < -VOLTAGE_TOLERANCE || outputVolts < MIN_COMMUTATION_VOLTS)
            throw new IllegalArgumentException("E06 averaged reverse input or fast reverse commutation is unsupported");
        double powerLoss = inputVolts * inputCurrent + enableVolts * enableCurrent +
            feedbackVolts * feedbackCurrent - outputVolts * outputCurrent;
        if (!E06ConverterContract.finite(powerLoss))
            throw new IllegalArgumentException("Nonfinite accepted E06 power flow");
        if (driveCurrent < -CURRENT_TOLERANCE_AMPS ||
                driveCurrent > E06ConverterContract.MAX_OUTPUT_CURRENT_AMPS + CURRENT_TOLERANCE_AMPS ||
                inputCurrent < -CURRENT_TOLERANCE_AMPS || powerLoss < -1e-7)
            throw new IllegalArgumentException("E06 averaged accepted flow exceeds forward-only passive-loss contract");
    }
    private static boolean changed(double before, double after, double tolerance) {
        return !E06ConverterContract.finite(before) || Math.abs(before - after) > tolerance * Math.max(1, Math.abs(after));
    }
}
