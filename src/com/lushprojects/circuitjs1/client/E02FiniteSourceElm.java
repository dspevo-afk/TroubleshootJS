package com.lushprojects.circuitjs1.client;

/**
 * Finite Thevenin source used only by the E02 developer fixture.  Keeping the
 * source as a resistor plus a current source makes the fixture's supply and
 * enable rails solver-visible without introducing an ideal source path into
 * the regulator model.
 */
final class E02FiniteSourceElm extends CircuitElm {
    private static final double MAX_SOURCE_VOLTS = 24.0;
    private final double resistance;
    private double sourceVoltage;

    E02FiniteSourceElm(int x, int y, double voltage, double resistance) {
        super(x, y);
        validateVoltage(voltage);
        validateResistance(resistance);
        sourceVoltage = voltage;
        this.resistance = resistance;
    }

    E02FiniteSourceElm(int x, int y, int x2, int y2, int flags,
            StringTokenizer st) {
        super(x, y, x2, y2, flags);
        sourceVoltage = nextDouble(st, "source voltage");
        resistance = nextDouble(st, "source resistance");
        validateVoltage(sourceVoltage);
        validateResistance(resistance);
        if (st.hasMoreTokens())
            throw new IllegalArgumentException("Unexpected E02 source dump payload");
    }

    int getDumpType() { return 456; }

    void setVoltage(double voltage) {
        validateVoltage(voltage);
        sourceVoltage = voltage;
    }

    double getSourceVoltage() { return sourceVoltage; }
    double getResistance() { return resistance; }

    String dump() { return super.dump() + " " + sourceVoltage + " " + resistance; }

    void setPoints() {
        super.setPoints();
        calcLeads(32);
    }

    void draw(Graphics g) {
        draw2Leads(g);
        setVoltageColor(g, (volts[0] + volts[1]) * .5);
        drawThickCircle(g, (point1.x + point2.x) / 2,
                (point1.y + point2.y) / 2, 12);
        drawPosts(g);
    }

    void stamp() {
        double conductance = 1.0 / resistance;
        sim.stampResistor(nodes[0], nodes[1], resistance);
        // Current from post 0 to post 1 creates a positive voltage at post 1
        // relative to post 0, matching a DCVoltageElm's orientation.
        sim.stampCurrentSource(nodes[0], nodes[1], sourceVoltage * conductance);
    }

    void calculateCurrent() {
        current = (volts[0] - volts[1] + sourceVoltage) / resistance;
    }

    double getVoltageDiff() { return volts[1] - volts[0]; }

    private static boolean finite(double value) {
        return !Double.isNaN(value) && !Double.isInfinite(value);
    }

    private static void validateVoltage(double voltage) {
        if (!finite(voltage) || voltage < 0.0 || voltage > MAX_SOURCE_VOLTS)
            throw new IllegalArgumentException("E02 source voltage must be 0-24 V");
    }

    private static void validateResistance(double resistance) {
        if (!finite(resistance) || resistance <= 0.0)
            throw new IllegalArgumentException("Invalid E02 finite source resistance");
    }

    private static double nextDouble(StringTokenizer st, String name) {
        if (st == null || !st.hasMoreTokens())
            throw new IllegalArgumentException("Missing E02 " + name);
        try {
            return Double.parseDouble(st.nextToken());
        } catch (NumberFormatException failure) {
            throw new IllegalArgumentException("Invalid E02 " + name);
        }
    }
}
