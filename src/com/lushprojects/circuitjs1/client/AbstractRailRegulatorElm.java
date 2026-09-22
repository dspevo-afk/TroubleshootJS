package com.lushprojects.circuitjs1.client;

/**
 * Shared finite four-terminal regulator branch.  The branch is a bounded
 * Norton equivalent, not an ideal output source: its target voltage is derived
 * from the solved input and enable terminals, and its finite resistance gives
 * the declared load/current response.
 */
abstract class AbstractRailRegulatorElm extends CircuitElm {
    static final int INPUT_POST = 0;
    static final int OUTPUT_POST = 1;
    static final int RETURN_POST = 2;
    static final int ENABLE_POST = 3;

    private static final double INPUT_LEAK_OHMS = 1000000.0;
    private static final double ENABLE_LEAK_OHMS = 1000000.0;
    private static final double UNSUPPORTED_VOLTAGE_TOLERANCE = 1e-6;
    private static final double BACKFEED_CURRENT_TOLERANCE_FRACTION = .01;
    // Keep the bounded Norton update within CircuitJS's native diagnostic
    // iteration budget while retaining millivolt-scale rail accuracy.
    private static final double CHANGE_TOLERANCE = 1e-4;

    private final RailRegulationContract contract;
    private final Point[] posts = new Point[4];
    private double lastTarget = Double.NaN;
    private double lastInputCurrent = Double.NaN;
    private double lastEnableFraction = Double.NaN;

    AbstractRailRegulatorElm(int x, int y, RailRegulationContract contract) {
        super(x, y);
        if (contract == null) throw new IllegalArgumentException("Missing rail contract");
        this.contract = contract;
    }

    AbstractRailRegulatorElm(int x, int y, int x2, int y2, int flags,
            RailRegulationContract contract) {
        super(x, y, x2, y2, flags);
        if (contract == null) throw new IllegalArgumentException("Missing rail contract");
        this.contract = contract;
    }

    final RailRegulationContract getContract() { return contract; }

    int getPostCount() { return 4; }

    Point getPost(int n) {
        if (n < 0 || n >= posts.length) throw new IllegalArgumentException("Invalid rail post");
        return posts[n];
    }

    void setPoints() {
        super.setPoints();
        int mx = (x + x2) / 2;
        int my = (y + y2) / 2;
        posts[INPUT_POST] = point1;
        posts[OUTPUT_POST] = point2;
        posts[RETURN_POST] = new Point(mx, my + 32);
        posts[ENABLE_POST] = new Point(mx, my - 32);
        setBbox(Math.min(x, x2) - 24, Math.min(y, y2) - 40,
                Math.max(x, x2) + 24, Math.max(y, y2) + 40);
    }

    void draw(Graphics g) {
        setBbox(Math.min(x, x2) - 24, Math.min(y, y2) - 40,
                Math.max(x, x2) + 24, Math.max(y, y2) + 40);
        if (posts[INPUT_POST] != null) {
            setVoltageColor(g, volts[INPUT_POST]);
            drawThickLine(g, posts[INPUT_POST], posts[OUTPUT_POST]);
            setVoltageColor(g, volts[RETURN_POST]);
            drawThickLine(g, posts[RETURN_POST], new Point((x + x2) / 2, (y + y2) / 2));
            setVoltageColor(g, volts[ENABLE_POST]);
            drawThickLine(g, posts[ENABLE_POST], new Point((x + x2) / 2, (y + y2) / 2));
            drawPosts(g);
        }
    }

    /** The model is deliberately nonlinear because input/enable target changes per solve. */
    boolean nonLinear() { return true; }

    void stamp() {
        validateContractForModel();
        sim.stampNonLinear(nodes[INPUT_POST]);
        sim.stampNonLinear(nodes[OUTPUT_POST]);
        sim.stampNonLinear(nodes[RETURN_POST]);
        sim.stampNonLinear(nodes[ENABLE_POST]);
        // These leaks make disconnected input/enable terminals finite without
        // creating an implicit return or an ideal source.
        sim.stampResistor(nodes[INPUT_POST], nodes[RETURN_POST], INPUT_LEAK_OHMS);
        sim.stampResistor(nodes[ENABLE_POST], nodes[RETURN_POST], ENABLE_LEAK_OHMS);
    }

    void doStep() {
        validateContractForModel();
        double inputVoltage = getInputVoltage();
        double outputVoltage = getOutputVoltage();
        double enableVoltage = getEnableVoltage();
        validateOperatingPoint(inputVoltage, outputVoltage, enableVoltage);
        double enableFraction = enableFraction(enableVoltage);
        double target = targetVoltage(inputVoltage, enableFraction);
        double outputResistance = effectiveOutputResistance();
        double outputConductance = 1.0 / outputResistance;
        double outputCurrent = (target - outputVoltage) * outputConductance;
        double inputCurrent = inputCurrent(inputVoltage, outputVoltage,
                outputCurrent, enableFraction);

        // Norton form: g*Vout - target*g is current leaving the output.
        // The target is bounded by the solved input and dropout, so input loss
        // or a disabled source cannot leave an independent ideal output rail.
        sim.stampConductance(nodes[OUTPUT_POST], nodes[RETURN_POST], outputConductance);
        sim.stampCurrentSource(nodes[OUTPUT_POST], nodes[RETURN_POST],
                -target * outputConductance);
        sim.stampCurrentSource(nodes[INPUT_POST], nodes[RETURN_POST], inputCurrent);

        if (changed(lastTarget, target) || changed(lastInputCurrent, inputCurrent) ||
                changed(lastEnableFraction, enableFraction))
            sim.converged = false;
        lastTarget = target;
        lastInputCurrent = inputCurrent;
        lastEnableFraction = enableFraction;
        current = getInputCurrent();
    }

    private double targetVoltage(double inputVoltage, double enableFraction) {
        if (enableFraction <= 0.0 || inputVoltage <= 0.0) return 0.0;
        double available = inputVoltage - contract.getDropoutVolts();
        if (available <= 0.0) return 0.0;
        return Math.min(contract.getNominalOutputVolts(), available) * enableFraction;
    }

    private double inputCurrent(double inputVoltage, double outputVoltage,
            double outputCurrent, double enableFraction) {
        if (enableFraction <= 0.0 || inputVoltage <= 0.0) return 0.0;
        return modelInputCurrent(inputVoltage, outputVoltage,
                Math.max(0.0, outputCurrent), enableFraction);
    }

    /** Implementations differ only in their bounded input-power accounting. */
    abstract double modelInputCurrent(double inputVoltage, double outputVoltage,
            double deliveredOutputCurrent, double enableFraction);

    final double effectiveOutputResistance() {
        // A finite series term derived from the current limit prevents a short
        // from demanding an unbounded current while retaining useful light-load
        // regulation.  It is present in both variants and is solver-visible.
        return contract.getOutputResistanceOhms() +
                contract.getNominalOutputVolts() /
                contract.getMaximumOutputCurrentAmps();
    }

    final double getInputVoltage() { return volts[INPUT_POST] - volts[RETURN_POST]; }
    final double getOutputVoltage() { return volts[OUTPUT_POST] - volts[RETURN_POST]; }
    final double getEnableVoltage() { return volts[ENABLE_POST] - volts[RETURN_POST]; }

    final double getTargetVoltage() {
        return targetVoltage(getInputVoltage(), enableFraction(getEnableVoltage()));
    }

    final double getInputCurrent() {
        return inputCurrent(getInputVoltage(), getOutputVoltage(),
                getOutputCurrent(), enableFraction(getEnableVoltage())) +
                getInputLeakCurrent();
    }

    final double getOutputCurrent() {
        return (getTargetVoltage() - getOutputVoltage()) / effectiveOutputResistance();
    }

    final double getInputLeakCurrent() {
        return getInputVoltage() / INPUT_LEAK_OHMS;
    }
    final double getEnableLeakCurrent() {
        return getEnableVoltage() / ENABLE_LEAK_OHMS;
    }
    final double getEnablePowerWatts() {
        return getEnableVoltage() * getEnableLeakCurrent();
    }
    final double getInputPowerWatts() { return getInputVoltage() * getInputCurrent(); }
    final double getOutputPowerWatts() {
        double outputCurrent = getOutputCurrent();
        if (outputCurrent < -backfeedCurrentTolerance())
            throw new IllegalStateException("Unsupported rail output backfeed");
        return getOutputVoltage() * outputCurrent;
    }
    final double getPowerLossWatts() {
        return getInputPowerWatts() + getEnablePowerWatts() - getOutputPowerWatts();
    }

    final boolean isEnabled() { return enableFraction(getEnableVoltage()) > .5; }
    final boolean isInDropout() {
        return isEnabled() && getInputVoltage() > 0.0 &&
                getInputVoltage() < contract.getNominalOutputVolts() +
                contract.getDropoutVolts();
    }
    final boolean isCurrentLimited() {
        return getOutputCurrent() >= .95 * contract.getMaximumOutputCurrentAmps();
    }

    double getVoltageDiff() { return getOutputVoltage(); }
    double getCurrent() { return getInputCurrent(); }
    double getPower() { return getPowerLossWatts(); }

    double getCurrentIntoNode(int n) {
        double input = getInputCurrent();
        double output = getOutputCurrent();
        if (n == INPUT_POST) return -input;
        if (n == OUTPUT_POST) return output;
        if (n == RETURN_POST) return input - output + getEnableLeakCurrent();
        if (n == ENABLE_POST) return -getEnableLeakCurrent();
        return 0.0;
    }

    void calculateCurrent() {
        current = getInputCurrent();
    }

    boolean getConnection(int n1, int n2) { return false; }
    boolean hasGroundConnection(int n) { return false; }

    void reset() {
        super.reset();
        lastTarget = Double.NaN;
        lastInputCurrent = Double.NaN;
        lastEnableFraction = Double.NaN;
    }

    void getInfo(String arr[]) {
        arr[0] = contract.getVariantId();
        arr[1] = "Vin = " + getVoltageText(getInputVoltage());
        arr[2] = "Vout = " + getVoltageText(getOutputVoltage());
        arr[3] = "Iin = " + getCurrentText(getInputCurrent());
        arr[4] = "P loss = " + getUnitText(getPowerLossWatts(), "W");
    }

    String dump() {
        return super.dump() + " " + contract.getVersion() + " " +
                dumpToken(contract.getVariantId(), "variantId") + " " +
                dumpToken(contract.getInputTerminalId(), "inputTerminalId") + " " +
                dumpToken(contract.getOutputTerminalId(), "outputTerminalId") + " " +
                dumpToken(contract.getReturnTerminalId(), "returnTerminalId") + " " +
                dumpToken(contract.getEnableTerminalId(), "enableTerminalId") + " " +
                contract.getNominalOutputVolts() + " " +
                contract.getMinimumInputVolts() + " " +
                contract.getMaximumInputVolts() + " " +
                contract.getDropoutVolts() + " " +
                contract.getMaximumOutputCurrentAmps() + " " +
                contract.getOutputResistanceOhms() + " " +
                contract.getEnableLowVolts() + " " +
                contract.getEnableHighVolts() + " " +
                contract.getQuiescentCurrentAmps() + " " +
                contract.getEfficiency() + " " +
                contract.isAveragedSwitching();
    }

    /** Read only the current, bounded E02 contract payload. */
    static RailRegulationContract readDumpContract(StringTokenizer st,
            boolean expectedAveraged) {
        int version = nextDumpInt(st, "contract version");
        if (version != RailRegulationContract.VERSION)
            throw new IllegalArgumentException("Unsupported E02 rail contract version");
        String variantId = nextDumpToken(st, "variantId");
        String inputId = nextDumpToken(st, "inputTerminalId");
        String outputId = nextDumpToken(st, "outputTerminalId");
        String returnId = nextDumpToken(st, "returnTerminalId");
        String enableId = nextDumpToken(st, "enableTerminalId");
        double nominal = nextDumpDouble(st, "nominalOutputVolts");
        double minimumInput = nextDumpDouble(st, "minimumInputVolts");
        double maximumInput = nextDumpDouble(st, "maximumInputVolts");
        double dropout = nextDumpDouble(st, "dropoutVolts");
        double maximumCurrent = nextDumpDouble(st, "maximumOutputCurrentAmps");
        double outputResistance = nextDumpDouble(st, "outputResistanceOhms");
        double enableLow = nextDumpDouble(st, "enableLowVolts");
        double enableHigh = nextDumpDouble(st, "enableHighVolts");
        double quiescent = nextDumpDouble(st, "quiescentCurrentAmps");
        double efficiency = nextDumpDouble(st, "efficiency");
        String averagedText = nextDumpToken(st, "averagedSwitching");
        if (!("true".equals(averagedText) || "false".equals(averagedText)))
            throw new IllegalArgumentException("Invalid E02 averaged-switching flag");
        boolean averaged = "true".equals(averagedText);
        if (averaged != expectedAveraged)
            throw new IllegalArgumentException("E02 dump variant does not match element type");
        if (st.hasMoreTokens())
            throw new IllegalArgumentException("Unexpected E02 rail dump payload");
        return new RailRegulationContract(variantId, inputId, outputId,
                returnId, enableId, nominal, minimumInput, maximumInput,
                dropout, maximumCurrent, outputResistance, enableLow,
                enableHigh, quiescent, efficiency, averaged);
    }

    private void validateOperatingPoint(double inputVoltage, double outputVoltage,
            double enableVoltage) {
        if (!finite(inputVoltage) || !finite(outputVoltage) || !finite(enableVoltage))
            throw new IllegalArgumentException("Nonfinite E02 rail operating point");
        double target = targetVoltage(inputVoltage, enableFraction(enableVoltage));
        // Source/enable mutations can leave the old solved voltages in the
        // first Newton trial.  Reject only a persistent unsupported region;
        // the changed-target trial is allowed to settle to its new bounded
        // operating point.
        if (inputVoltage < -UNSUPPORTED_VOLTAGE_TOLERANCE &&
                !changed(lastTarget, target))
            throw new IllegalArgumentException("Negative E02 rail source is unsupported");
        if (enableVoltage < -UNSUPPORTED_VOLTAGE_TOLERANCE &&
                !changed(lastTarget, target))
            throw new IllegalArgumentException("Negative E02 enable source is unsupported");
        if (outputVoltage < -UNSUPPORTED_VOLTAGE_TOLERANCE &&
                !changed(lastTarget, target))
            throw new IllegalArgumentException("Negative E02 rail output is unsupported");
        if (inputVoltage > contract.getMaximumInputVolts() +
                UNSUPPORTED_VOLTAGE_TOLERANCE)
            throw new IllegalArgumentException("Rail input exceeds declared envelope");
        double outputCurrent = (target - outputVoltage) / effectiveOutputResistance();
        // A source/enable mutation can leave the previous rail voltage in the
        // first Newton trial.  Allow that one bounded transition, but reject
        // a persistent reverse output current once the target is unchanged.
        if (outputCurrent < -backfeedCurrentTolerance() &&
                !changed(lastTarget, target))
            throw new IllegalArgumentException("Unsupported rail output backfeed: output=" +
                    outputVoltage + " target=" + target + " current=" + outputCurrent);
        if (outputCurrent > contract.getMaximumOutputCurrentAmps() +
                UNSUPPORTED_VOLTAGE_TOLERANCE)
            throw new IllegalArgumentException("Rail output exceeds declared current limit");
    }

    private static String nextDumpToken(StringTokenizer st, String name) {
        if (st == null || !st.hasMoreTokens())
            throw new IllegalArgumentException("Missing E02 rail " + name);
        return st.nextToken();
    }

    private static int nextDumpInt(StringTokenizer st, String name) {
        try {
            return Integer.parseInt(nextDumpToken(st, name));
        } catch (NumberFormatException failure) {
            throw new IllegalArgumentException("Invalid E02 rail " + name);
        }
    }

    private static double nextDumpDouble(StringTokenizer st, String name) {
        try {
            return Double.parseDouble(nextDumpToken(st, name));
        } catch (NumberFormatException failure) {
            throw new IllegalArgumentException("Invalid E02 rail " + name);
        }
    }

    private static String dumpToken(String value, String name) {
        if (value == null || value.length() == 0 ||
                value.indexOf(' ') >= 0 || value.indexOf('\t') >= 0 ||
                value.indexOf('\r') >= 0 || value.indexOf('\n') >= 0)
            throw new IllegalStateException("E02 rail " + name + " is not dump-safe");
        return value;
    }

    private static boolean finite(double value) {
        return !Double.isNaN(value) && !Double.isInfinite(value);
    }

    private double backfeedCurrentTolerance() {
        return Math.max(1e-6, contract.getMaximumOutputCurrentAmps() *
                BACKFEED_CURRENT_TOLERANCE_FRACTION);
    }

    private double enableFraction(double enableVoltage) {
        if (enableVoltage <= contract.getEnableLowVolts()) return 0.0;
        if (enableVoltage >= contract.getEnableHighVolts()) return 1.0;
        return (enableVoltage - contract.getEnableLowVolts()) /
                (contract.getEnableHighVolts() - contract.getEnableLowVolts());
    }

    private void validateContractForModel() {
        if (contract.getTerminalIds().size() != 4 ||
                contract.supportsSwitchingWaveform() ||
                contract.supportsFrequencyMeasurement())
            throw new IllegalArgumentException("Rail contract is not a bounded solver model contract");
    }

    private static boolean changed(double before, double after) {
        if (Double.isNaN(before)) return true;
        return Math.abs(before - after) > CHANGE_TOLERANCE *
                Math.max(1.0, Math.max(Math.abs(before), Math.abs(after)));
    }
}
