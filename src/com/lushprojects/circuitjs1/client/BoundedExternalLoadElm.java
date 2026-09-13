package com.lushprojects.circuitjs1.client;

/** Passive 0-24 V external load with an explicit 6 W sacrificial stress limit. */
final class BoundedExternalLoadElm extends ResistorElm {
    private boolean failed;
    BoundedExternalLoadElm(int x, int y, double ohms) {
        super(x, y);
        configure(ohms);
    }
    BoundedExternalLoadElm(int x, int y, int x2, int y2, int flags, StringTokenizer st) {
        super(x, y, x2, y2, flags, st);
        String state = st.nextToken();
        if (!"true".equals(state) && !"false".equals(state))
            throw new IllegalArgumentException("Invalid external load state");
        failed = "true".equals(state);
        if (failed) {
            if (getResistance() != 1e9) throw new IllegalArgumentException("Invalid failed load resistance");
        } else configure(getResistance());
    }
    int getDumpType() { return 453; }
    String dump() { return super.dump() + " " + failed; }
    void configure(double ohms) {
        if (failed) throw new IllegalStateException("Failed external load requires replacement");
        if (!LowVoltageSourceModel.finite(ohms) || ohms < 1 || ohms > 100000)
            throw new IllegalArgumentException("Unsupported external load: 1-100000 ohms required");
        setResistance(ohms);
    }
    boolean nonLinear() { return true; }
    void stamp() { sim.stampNonLinear(nodes[0]); sim.stampNonLinear(nodes[1]); }
    void doStep() { sim.stampResistor(nodes[0], nodes[1], getResistance()); }
    void stepFinished() {
        if (sim.dcAnalysisFlag || failed) return;
        if (Math.abs(volts[0] - volts[1]) > 24.001 || getPower() > 6) {
            failed = true;
            setResistance(1e9);
        }
    }
    boolean hasFailed() { return failed; }
}
