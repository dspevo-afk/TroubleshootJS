package com.lushprojects.circuitjs1.client;

/** CircuitJS MNA source plus nonlinear series compliance. Posts: return, output. */
final class LimitedDcSupplyElm extends DCVoltageElm {
    private double limitAmps = LowVoltageSourceModel.DEFAULT_LIMIT_AMPS;
    private double lastDrop;

    LimitedDcSupplyElm(int x, int y) { super(x, y); }
    LimitedDcSupplyElm(int x, int y, int x2, int y2, int flags, StringTokenizer st) {
        super(x, y, x2, y2, flags, st);
        limitAmps = Double.parseDouble(st.nextToken());
        validateSettings();
    }
    int getDumpType() { return 450; }
    Class getDumpClass() { return LimitedDcSupplyElm.class; }
    String dump() { return super.dump() + " " + limitAmps; }
    int getInternalNodeCount() { return 1; }
    boolean nonLinear() { return true; }
    double getLimitAmps() { return limitAmps; }
    double getOutputVoltage() { return volts[1] - volts[0]; }
    boolean isLimiting() { return volts[2] - volts[1] > limitAmps * LowVoltageSourceModel.OUTPUT_OHMS; }
    void configure(double voltage, double limit) {
        LowVoltageSourceModel.validate(voltage, limit);
        maxVoltage = voltage;
        limitAmps = limit;
    }
    private void validateSettings() {
        LowVoltageSourceModel.validate(maxVoltage, limitAmps);
        if (waveform != WF_DC || bias != 0)
            throw new IllegalArgumentException("Unsupported bench waveform");
    }
    void stamp() {
        validateSettings();
        sim.stampVoltageSource(nodes[0], nodes[2], voltSource, maxVoltage);
        sim.stampNonLinear(nodes[2]);
        sim.stampNonLinear(nodes[1]);
    }
    void doStep() {
        double drop = volts[2] - volts[1];
        double lower = LowVoltageSourceModel.REVERSE_KNEE_VOLTS;
        double upper = limitAmps * LowVoltageSourceModel.OUTPUT_OHMS;
        // A Newton tangent on either flat branch can jump over the entire
        // voltage-regulating region. Visit that region before crossing to the
        // opposite branch (notably when two supplies backfeed one another).
        if ((lastDrop < lower && drop > upper) || (lastDrop > upper && drop < lower)) {
            drop = (lower + upper) / 2;
            sim.converged = false;
        }
        // Bound Newton moves across the two compliance knees, not solved readings.
        if (Math.abs(drop - lastDrop) > .5) {
            drop = lastDrop + (drop > lastDrop ? .5 : -.5);
            sim.converged = false;
        }
        if (Math.abs(drop - lastDrop) > 1e-7) sim.converged = false;
        lastDrop = drop;
        double g = LowVoltageSourceModel.conductance(drop, limitAmps);
        double offset = LowVoltageSourceModel.current(drop, limitAmps) - g * drop;
        sim.stampConductance(nodes[2], nodes[1], g);
        sim.stampCurrentSource(nodes[2], nodes[1], offset);
    }
    void reset() { super.reset(); lastDrop = 0; }
    public EditInfo getEditInfo(int n) { return null; }
}
