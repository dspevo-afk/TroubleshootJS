package com.lushprojects.circuitjs1.client;

/** Primary powered-driver abstraction; state advances only after an accepted solve. */
final class E06PwmControllerElm extends CircuitElm {
    static final int INPUT = 0, RETURN = 1, ENABLE = 2, FEEDBACK = 3;
    static final int HIGH_GATE = 4, HIGH_SOURCE = 5, LOW_GATE = 6, LOW_SOURCE = 7;
    private final E06ConverterContract contract;
    private final Point[] posts = new Point[8];
    private final double[] acceptedCurrents = new double[8];
    private boolean active, acceptedActive, acceptedPulse, appliedPulse, quiescentTangentReady;
    private double duty, activeSeconds, acceptedDuty, acceptedActiveSeconds, acceptedPhase, appliedDrive;
    private double appliedQuiescentGain, appliedQuiescentOffset;
    private double integral, acceptedIntegral;
    private long cycle, acceptedCycle;

    E06PwmControllerElm(int x, int y) { this(x, y, new E06ConverterContract()); }
    E06PwmControllerElm(int x, int y, E06ConverterContract contract) {
        super(x, y);
        if (contract == null) throw new IllegalArgumentException("Missing E06 control contract");
        this.contract = contract;
        x2 = x + 160; y2 = y + 160; setPoints();
    }

    int getPostCount() { return 8; }
    Point getPost(int n) { return posts[n]; }
    void setPoints() {
        super.setPoints();
        posts[INPUT] = new Point(x, y); posts[RETURN] = new Point(x, y + 160);
        posts[ENABLE] = new Point(x + 64, y + 160); posts[FEEDBACK] = new Point(x + 96, y + 160);
        posts[HIGH_GATE] = new Point(x + 160, y); posts[HIGH_SOURCE] = new Point(x + 160, y + 32);
        posts[LOW_GATE] = new Point(x + 160, y + 96); posts[LOW_SOURCE] = new Point(x + 160, y + 128);
    }
    boolean nonLinear() { return true; }
    boolean getConnection(int a, int b) { return a < 4 && b < 4 || a >= 4 && b >= 4 && a / 2 == b / 2; }
    void stamp() {
        if (!E06ConverterContract.finite(sim.timeStep) || sim.timeStep <= 0 ||
                sim.timeStep > E06ConverterContract.MAX_DETAILED_STEP_SECONDS)
            throw new IllegalArgumentException("E06 detailed candidate requires accepted dt at most 5 us");
        quiescentTangentReady = false;
        sim.stampNonLinear(nodes[INPUT]); sim.stampNonLinear(nodes[RETURN]);
        sim.stampResistor(nodes[INPUT], nodes[RETURN], E06ConverterContract.INPUT_LEAK_OHMS);
        sim.stampResistor(nodes[ENABLE], nodes[RETURN], E06ConverterContract.CONTROL_LEAK_OHMS);
        sim.stampResistor(nodes[FEEDBACK], nodes[RETURN], E06ConverterContract.CONTROL_LEAK_OHMS);
        sim.stampResistor(nodes[HIGH_GATE], nodes[HIGH_SOURCE], E06ConverterContract.GATE_RESISTANCE_OHMS);
        sim.stampResistor(nodes[LOW_GATE], nodes[LOW_SOURCE], E06ConverterContract.GATE_RESISTANCE_OHMS);
        for (int n = HIGH_GATE; n <= LOW_SOURCE; n++) sim.stampRightSide(nodes[n]);
    }
    void doStep() {
        double input = volts[INPUT] - volts[RETURN];
        double q = E06ConverterContract.quiescentFor(input);
        double gain = input > 0 && input < E06ConverterContract.BIAS_VOLTS ?
            E06ConverterContract.QUIESCENT_AMPS / E06ConverterContract.BIAS_VOLTS : 0;
        double offset = q - gain * input;
        if (!quiescentTangentReady || changed(appliedQuiescentGain, gain) || changed(appliedQuiescentOffset, offset))
            sim.converged = false;
        appliedQuiescentGain = gain; appliedQuiescentOffset = offset; quiescentTangentReady = true;
        sim.stampConductance(nodes[INPUT], nodes[RETURN], gain);
        sim.stampCurrentSource(nodes[INPUT], nodes[RETURN], offset);
        // The prepared state and solver time are constant throughout Newton retries.
        appliedPulse = active && E06ConverterContract.cyclePhaseSeconds(sim.t) <
            duty * E06ConverterContract.PERIOD_SECONDS;
        appliedDrive = appliedPulse ? E06ConverterContract.BIAS_VOLTS : 0;
        double sourceCurrent = appliedDrive / E06ConverterContract.GATE_RESISTANCE_OHMS;
        sim.stampCurrentSource(nodes[HIGH_SOURCE], nodes[HIGH_GATE], sourceCurrent);
        sim.stampCurrentSource(nodes[LOW_SOURCE], nodes[LOW_GATE], sourceCurrent);
    }
    void stepFinished() {
        // Capture the realized solve before committing control for the following step.
        acceptedActive = active; acceptedPulse = appliedPulse; acceptedDuty = duty; acceptedActiveSeconds = activeSeconds;
        acceptedIntegral = integral;
        acceptedCycle = E06ConverterContract.cycleIndex(sim.t - sim.timeStep);
        acceptedPhase = E06ConverterContract.cyclePhaseSeconds(sim.t - sim.timeStep);
        double input = volts[INPUT] - volts[RETURN];
        double solvedQuiescent = appliedQuiescentGain * input + appliedQuiescentOffset;
        if (!E06ConverterContract.finite(solvedQuiescent) || solvedQuiescent < -1e-9 ||
                Math.abs(solvedQuiescent - E06ConverterContract.quiescentFor(input)) > 1e-9 ||
                input * solvedQuiescent < -1e-9)
            throw new IllegalStateException("E06 accepted quiescent branch is inconsistent or nonpassive");
        double in = solvedQuiescent + input / E06ConverterContract.INPUT_LEAK_OHMS;
        double enable = (volts[ENABLE] - volts[RETURN]) / E06ConverterContract.CONTROL_LEAK_OHMS;
        double feedback = (volts[FEEDBACK] - volts[RETURN]) / E06ConverterContract.CONTROL_LEAK_OHMS;
        acceptedCurrents[INPUT] = -in; acceptedCurrents[RETURN] = in + enable + feedback;
        acceptedCurrents[ENABLE] = -enable; acceptedCurrents[FEEDBACK] = -feedback;
        acceptedCurrents[HIGH_GATE] = (appliedDrive - volts[HIGH_GATE] + volts[HIGH_SOURCE]) /
            E06ConverterContract.GATE_RESISTANCE_OHMS;
        acceptedCurrents[HIGH_SOURCE] = -acceptedCurrents[HIGH_GATE];
        acceptedCurrents[LOW_GATE] = (appliedDrive - volts[LOW_GATE] + volts[LOW_SOURCE]) /
            E06ConverterContract.GATE_RESISTANCE_OHMS;
        acceptedCurrents[LOW_SOURCE] = -acceptedCurrents[LOW_GATE]; current = in;
        boolean next = E06ConverterContract.nextActive(active, input, volts[ENABLE] - volts[RETURN]);
        activeSeconds = next ? (active ? activeSeconds : 0) + sim.timeStep : 0;
        integral = E06ConverterContract.nextIntegral(integral, input, volts[FEEDBACK] - volts[RETURN],
            activeSeconds, sim.timeStep, next);
        long nextCycle = E06ConverterContract.cycleIndex(sim.t);
        if (!next) duty = 0;
        else if (nextCycle != cycle)
            duty = E06ConverterContract.dutyFor(input, volts[FEEDBACK] - volts[RETURN], activeSeconds, integral);
        active = next; cycle = nextCycle;
    }
    void reset() {
        super.reset(); active = acceptedActive = acceptedPulse = appliedPulse = quiescentTangentReady = false;
        duty = activeSeconds = acceptedDuty = acceptedActiveSeconds = acceptedPhase = appliedDrive = current = 0;
        appliedQuiescentGain = appliedQuiescentOffset = 0;
        cycle = acceptedCycle = 0; integral = acceptedIntegral = 0;
        for (int n = 0; n < acceptedCurrents.length; n++) acceptedCurrents[n] = 0;
    }
    double getCurrentIntoNode(int n) { return acceptedCurrents[n]; }
    double getInputCurrent() { return -acceptedCurrents[INPUT]; }
    double getDuty() { return acceptedDuty; }
    double getIntegral() { return acceptedIntegral; }
    boolean isActive() { return acceptedActive; }
    boolean isPulseOn() { return acceptedPulse; }
    double getActiveSeconds() { return acceptedActiveSeconds; }
    long getCycleIndex() { return acceptedCycle; }
    double getPhaseSeconds() { return acceptedPhase; }
    E06ConverterContract getContract() { return contract; }
    private static boolean changed(double before, double after) { return Math.abs(before - after) > 1e-12; }

    /** Shared finite linear bias supply. Signed reverse output is absorbed, never backfed. */
    static final class BiasElm extends CircuitElm {
        static final int INPUT = 0, RETURN = 1, BIAS = 2;
        private final Point[] posts = new Point[3];
        private boolean tangentReady;
        private double appliedTargetGain, appliedTargetOffset, appliedInputGain, appliedOutputGain, appliedInputOffset;
        private double acceptedInput, acceptedOutput;
        BiasElm(int x, int y) { super(x, y); x2 = x + 128; y2 = y + 64; setPoints(); }
        int getPostCount() { return 3; }
        Point getPost(int n) { return posts[n]; }
        void setPoints() {
            super.setPoints(); posts[INPUT] = new Point(x, y); posts[RETURN] = new Point(x, y + 64);
            posts[BIAS] = new Point(x + 128, y);
        }
        boolean nonLinear() { return true; }
        boolean getConnection(int a, int b) { return true; }
        void stamp() { tangentReady = false; for (int n = 0; n < 3; n++) sim.stampNonLinear(nodes[n]); }
        void doStep() {
            double input = volts[INPUT] - volts[RETURN], output = volts[BIAS] - volts[RETURN];
            double target = E06ConverterContract.clamp(input, 0, E06ConverterContract.BIAS_VOLTS);
            double slope = input > 0 && input < E06ConverterContract.BIAS_VOLTS ? 1 : 0;
            double g = 1 / E06ConverterContract.BIAS_RESISTANCE_OHMS;
            double delivered = (target - output) * g;
            double targetGain = g * slope, targetOffset = g * (target - slope * input);
            double inputGain = delivered > 0 ? g * slope : 0, outputGain = delivered > 0 ? -g : 0;
            double inputOffset = Math.max(0, delivered) - inputGain * input - outputGain * output;
            if (!tangentReady || changed(appliedTargetGain, targetGain) || changed(appliedTargetOffset, targetOffset) ||
                    changed(appliedInputGain, inputGain) || changed(appliedOutputGain, outputGain) ||
                    changed(appliedInputOffset, inputOffset)) sim.converged = false;
            appliedTargetGain = targetGain; appliedTargetOffset = targetOffset;
            appliedInputGain = inputGain; appliedOutputGain = outputGain; appliedInputOffset = inputOffset;
            tangentReady = true;
            sim.stampConductance(nodes[BIAS], nodes[RETURN], g);
            sim.stampVCCurrentSource(nodes[BIAS], nodes[RETURN], nodes[INPUT], nodes[RETURN], -targetGain);
            sim.stampCurrentSource(nodes[BIAS], nodes[RETURN], -targetOffset);
            // Linear regulator accounting: actual positive delivered current is drawn from INPUT.
            sim.stampConductance(nodes[INPUT], nodes[RETURN], inputGain);
            sim.stampVCCurrentSource(nodes[INPUT], nodes[RETURN], nodes[BIAS], nodes[RETURN], outputGain);
            sim.stampCurrentSource(nodes[INPUT], nodes[RETURN], inputOffset);
        }
        void stepFinished() {
            double input = volts[INPUT] - volts[RETURN], output = volts[BIAS] - volts[RETURN];
            acceptedOutput = appliedTargetGain * input + appliedTargetOffset -
                output / E06ConverterContract.BIAS_RESISTANCE_OHMS;
            acceptedInput = appliedInputGain * input + appliedOutputGain * output + appliedInputOffset;
            double expectedOutput = (E06ConverterContract.clamp(input, 0, E06ConverterContract.BIAS_VOLTS) - output) /
                E06ConverterContract.BIAS_RESISTANCE_OHMS;
            if (!E06ConverterContract.finite(acceptedInput) || !E06ConverterContract.finite(acceptedOutput) ||
                    acceptedInput < -1e-9 || Math.abs(acceptedOutput - expectedOutput) > 1e-9 ||
                    Math.abs(acceptedInput - Math.max(0, expectedOutput)) > 1e-9 ||
                    input * acceptedInput - output * acceptedOutput < -1e-9)
                throw new IllegalStateException("E06 accepted bias branches are inconsistent or nonpassive");
            current = acceptedInput;
        }
        void reset() {
            super.reset(); tangentReady = false;
            appliedTargetGain = appliedTargetOffset = appliedInputGain = appliedOutputGain = appliedInputOffset = 0;
            acceptedInput = acceptedOutput = current = 0;
        }
        double getInputCurrent() { return acceptedInput; }
        double getOutputCurrent() { return acceptedOutput; }
        double getCurrentIntoNode(int n) {
            return n == INPUT ? -acceptedInput : n == BIAS ? acceptedOutput : acceptedInput - acceptedOutput;
        }
    }
}
