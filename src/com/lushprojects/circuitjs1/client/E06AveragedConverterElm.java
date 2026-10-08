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
        for (int node : nodes) sim.stampNonLinear(node);
        sim.stampResistor(nodes[0], nodes[1], E06ConverterContract.INPUT_LEAK_OHMS);
        sim.stampResistor(nodes[4], nodes[1], E06ConverterContract.CONTROL_LEAK_OHMS);
        sim.stampResistor(nodes[5], nodes[1], E06ConverterContract.CONTROL_LEAK_OHMS);
        // Always passive, including disabled and externally charged output states.
        sim.stampResistor(nodes[2], nodes[3], E06ConverterContract.OUTPUT_LEAK_OHMS);
    }

    void startIteration() {
        requireStep();
        stepActive = active;
        stepDuty = duty;
        stepIntegral = integral;
        stepActiveSeconds = activeSeconds;
        stepCycleIndex = cycleIndex;
        lastTarget = lastDrop = lastInput = Double.NaN;
    }

    void doStep() {
        double vin = getInputVoltage(), output = getOutputVoltage();
        if (!E06ConverterContract.finite(vin) || !E06ConverterContract.finite(output))
            throw new IllegalArgumentException("Nonfinite E06 converter trial");
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
        receiptReady = false;
        double physicalDrive = driving ? Math.max(0,
            Math.min(E06ConverterContract.MAX_OUTPUT_CURRENT_AMPS, requestedDrop / resistance)) : 0;
        // At either limiter knee the accepted tangent must also represent the
        // bounded characteristic; a voltage-only test could accept a small
        // current overshoot on the neighboring regulation tangent.
        if (changed(lastTarget, target, VOLTAGE_TOLERANCE) || changed(lastDrop, drop, VOLTAGE_TOLERANCE) ||
                changed(lastInput, inputAtEvaluation, 1e-9) || Math.abs(drop - requestedDrop) > VOLTAGE_TOLERANCE ||
                Math.abs(trialDriveCurrent() - physicalDrive) > 1e-9)
            sim.converged = false;
        lastTarget = target;
        lastDrop = drop;
        lastInput = inputAtEvaluation;
    }

    void stepFinished() {
        // Snapshot the realized stamped branches before latching future control.
        acceptedInputVolts = getInputVoltage();
        acceptedOutputVolts = getOutputVoltage();
        acceptedEnableVolts = getEnableVoltage();
        acceptedFeedbackVolts = getFeedbackVoltage();
        acceptedDriveCurrent = trialDriveCurrent();
        acceptedInputCurrent = trialInputCurrent();
        acceptedOutputCurrent = acceptedDriveCurrent - acceptedOutputVolts / E06ConverterContract.OUTPUT_LEAK_OHMS;
        acceptedEnableCurrent = acceptedEnableVolts / E06ConverterContract.CONTROL_LEAK_OHMS;
        acceptedFeedbackCurrent = acceptedFeedbackVolts / E06ConverterContract.CONTROL_LEAK_OHMS;
        acceptedActive = stepActive;
        acceptedDuty = stepDuty;
        acceptedIntegral = stepIntegral;
        acceptedActiveSeconds = stepActiveSeconds;
        acceptedCycleIndex = stepCycleIndex;
        receiptReady = true;
        requireAcceptedEnvelope();
        boolean nextActive = E06ConverterContract.nextActive(active, acceptedInputVolts, acceptedEnableVolts);
        activeSeconds = nextActive ? (active ? activeSeconds : 0) + sim.timeStep : 0;
        integral = E06ConverterContract.nextIntegral(integral, acceptedInputVolts, acceptedFeedbackVolts,
            activeSeconds, sim.timeStep, nextActive);
        active = nextActive;
        long nextCycle = E06ConverterContract.cycleIndex(sim.t);
        if (!active) duty = 0;
        else if (nextCycle != cycleIndex) duty = E06ConverterContract.dutyFor(
            acceptedInputVolts, acceptedFeedbackVolts, activeSeconds, integral);
        cycleIndex = nextCycle;
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
    double getCurrent() { return getInputCurrent(); }
    double getPower() { return getPowerLossWatts(); }
    double getCurrentIntoNode(int n) {
        double input = getInputCurrent(), output = getOutputCurrent();
        double enable = receiptReady ? acceptedEnableCurrent : getEnableVoltage() / E06ConverterContract.CONTROL_LEAK_OHMS;
        double feedback = receiptReady ? acceptedFeedbackCurrent : getFeedbackVoltage() / E06ConverterContract.CONTROL_LEAK_OHMS;
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
        active = stepActive = acceptedActive = false;
        activeSeconds = duty = stepActiveSeconds = stepDuty = acceptedActiveSeconds = acceptedDuty = 0;
        integral = stepIntegral = acceptedIntegral = 0;
        cycleIndex = stepCycleIndex = acceptedCycleIndex = 0;
        acceptedInputCurrent = acceptedOutputCurrent = acceptedEnableCurrent = acceptedFeedbackCurrent = acceptedDriveCurrent = 0;
        acceptedInputVolts = acceptedOutputVolts = acceptedEnableVolts = acceptedFeedbackVolts = 0;
        lastTarget = lastDrop = lastInput = Double.NaN;
        tangentReady = receiptReady = false;
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
    private void requireAcceptedEnvelope() {
        if (!E06ConverterContract.finite(acceptedInputVolts) || !E06ConverterContract.finite(acceptedOutputVolts) ||
                !E06ConverterContract.finite(acceptedEnableVolts) || !E06ConverterContract.finite(acceptedFeedbackVolts) ||
                !E06ConverterContract.finite(acceptedInputCurrent) || !E06ConverterContract.finite(acceptedOutputCurrent))
            throw new IllegalArgumentException("Nonfinite accepted E06 averaged port");
        if (acceptedInputVolts < -VOLTAGE_TOLERANCE || acceptedOutputVolts < MIN_COMMUTATION_VOLTS)
            throw new IllegalArgumentException("E06 averaged reverse input or fast reverse commutation is unsupported");
        if (acceptedDriveCurrent < -CURRENT_TOLERANCE_AMPS ||
                acceptedDriveCurrent > E06ConverterContract.MAX_OUTPUT_CURRENT_AMPS + CURRENT_TOLERANCE_AMPS ||
                acceptedInputCurrent < -CURRENT_TOLERANCE_AMPS || getPowerLossWatts() < -1e-7)
            throw new IllegalArgumentException("E06 averaged accepted flow exceeds forward-only passive-loss contract");
    }
    private static boolean changed(double before, double after, double tolerance) {
        return !E06ConverterContract.finite(before) || Math.abs(before - after) > tolerance * Math.max(1, Math.abs(after));
    }
}
