package com.lushprojects.circuitjs1.client;

/**
 * Shared finite four-terminal regulator branch.  The branch is a bounded
 * Norton equivalent, not an ideal output source: its target voltage is derived
 * from the solved input and enable terminals, and its finite regulation branch
 * plus declared current limit give the load response.
 */
abstract class AbstractRailRegulatorElm extends CircuitElm {
    static final int INPUT_POST = 0;
    static final int OUTPUT_POST = 1;
    static final int RETURN_POST = 2;
    static final int ENABLE_POST = 3;

    private static final double INPUT_LEAK_OHMS = 1000000.0;
    private static final double ENABLE_LEAK_OHMS = 1000000.0;
    // The limiter's flat branch is kept just barely conductive so that an
    // otherwise unconnected output remains numerically bounded.  Its current
    // contribution is far below the declared current-limit tolerance.
    private static final double CURRENT_LIMIT_LEAKAGE_SIEMENS = 1e-9;
    // A powered-off/reverse-driven output is a finite 1 Mohm internal bleed,
    // not an almost-open 1 Gohm matrix pivot.  The stronger finite branch is
    // still passive (at most 24 uA over the declared absolute envelope), but
    // keeps Parts Tray remove/install topology changes well-conditioned.
    private static final double REVERSE_LEAKAGE_SIEMENS = 1e-6;
    private static final double CURRENT_LIMIT_TOLERANCE_AMPS = 1e-9;
    private static final double UNSUPPORTED_VOLTAGE_TOLERANCE = 1e-6;
    private static final double BACKFEED_CURRENT_TOLERANCE_FRACTION = .01;
    // Bound Newton's trial across the current-limit knee.  The solved output
    // is never clipped; only the tangent point is bounded, as in CircuitJS's
    // diode and limited-supply models.
    private static final double MAX_NEWTON_DROP_STEP_VOLTS = .5;
    // A source/enable update can move the target by a few millivolts while
    // Newton is solving the coupled input/output network.  Restarting the
    // output tangent for every such update makes the target and input-current
    // branches chase one another.  A large target transition still starts
    // from the safe regulation tangent.
    private static final double TARGET_RESTART_STEP_VOLTS = .25;
    private static final double CHANGE_TOLERANCE = 1e-4;

    private final RailRegulationContract contract;
    private final Point[] posts = new Point[4];
    private double lastTarget = Double.NaN;
    private double lastInputCurrent = Double.NaN;
    private double lastEnableFraction = Double.NaN;
    private double lastOutputDrop = Double.NaN;

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
        double enableFraction = enableFraction(enableVoltage);
        double target = targetVoltage(inputVoltage, enableFraction);
        boolean outputActive = target > 0.0;
        double requestedDrop = target - outputVoltage;
        double evaluationDrop = limitNewtonDrop(requestedDrop, target,
                outputActive);
        double evaluationOutput = target - evaluationDrop;
        // A graph rebuild can expose a stale node-voltage trial before this
        // element has stamped its first corrective tangent.  Validate the
        // bounded Newton evaluation point, not that unaccepted trial.  A real
        // unsupported source still advances to the boundary and is rejected.
        validateOperatingPoint(inputVoltage, evaluationOutput, enableVoltage,
                changed(evaluationDrop, requestedDrop));
        double outputCurrent = outputCurrentForDrop(evaluationDrop,
                outputActive);
        double outputConductance = outputConductanceForDrop(evaluationDrop,
                outputActive);
        double targetInputSlope = targetInputSlope(inputVoltage, enableFraction);
        double targetEnableSlope = targetEnableSlope(inputVoltage, enableVoltage);
        double outputInputGain = outputActive ?
                -outputConductance * targetInputSlope : 0.0;
        double outputEnableGain = outputActive ?
                -outputConductance * targetEnableSlope : 0.0;

        // Linearize the bounded delivered-current characteristic around the
        // evaluation point.  Current leaving OUTPUT is the negative of the
        // delivered load current, so the Norton offset is -I - g*Veval;
        // target dependence on INPUT and ENABLE is stamped as controlled
        // current so the source/rail loop is solved by the same Newton step.
        sim.stampConductance(nodes[OUTPUT_POST], nodes[RETURN_POST], outputConductance);
        sim.stampVCCurrentSource(nodes[OUTPUT_POST], nodes[RETURN_POST],
                nodes[INPUT_POST], nodes[RETURN_POST], outputInputGain);
        sim.stampVCCurrentSource(nodes[OUTPUT_POST], nodes[RETURN_POST],
                nodes[ENABLE_POST], nodes[RETURN_POST], outputEnableGain);
        sim.stampCurrentSource(nodes[OUTPUT_POST], nodes[RETURN_POST],
                -outputCurrent - outputConductance * evaluationOutput -
                outputInputGain * inputVoltage -
                outputEnableGain * enableVoltage);

        InputLinearization input = inputLinearization(inputVoltage,
                evaluationOutput, outputCurrent, enableFraction,
                targetInputSlope, targetEnableSlope, outputConductance,
                outputActive);
        sim.stampConductance(nodes[INPUT_POST], nodes[RETURN_POST], input.inputGain);
        sim.stampVCCurrentSource(nodes[INPUT_POST], nodes[RETURN_POST],
                nodes[OUTPUT_POST], nodes[RETURN_POST], input.outputGain);
        sim.stampVCCurrentSource(nodes[INPUT_POST], nodes[RETURN_POST],
                nodes[ENABLE_POST], nodes[RETURN_POST], input.enableGain);
        sim.stampCurrentSource(nodes[INPUT_POST], nodes[RETURN_POST],
                input.current - input.inputGain * inputVoltage -
                input.outputGain * evaluationOutput -
                input.enableGain * enableVoltage);

        boolean targetChanged = changed(lastTarget, target);
        boolean inputChanged = changed(lastInputCurrent, input.current);
        boolean enableChanged = changed(lastEnableFraction, enableFraction);
        boolean outputChanged = changed(lastOutputDrop, evaluationDrop);
        if (targetChanged || inputChanged || enableChanged || outputChanged)
            sim.converged = false;
        lastTarget = target;
        lastInputCurrent = input.current;
        lastEnableFraction = enableFraction;
        lastOutputDrop = evaluationDrop;
        current = getInputCurrent();
    }

    private double targetVoltage(double inputVoltage, double enableFraction) {
        if (enableFraction <= 0.0 || inputVoltage <= 0.0) return 0.0;
        double available = inputVoltage - contract.getDropoutVolts();
        if (available <= 0.0) return 0.0;
        return Math.min(contract.getNominalOutputVolts(), available) * enableFraction;
    }

    private double inputCurrent(double inputVoltage, double outputVoltage,
            double outputCurrent, double enableFraction, boolean outputActive) {
        if (enableFraction <= 0.0 || inputVoltage <= 0.0) return 0.0;
        return modelInputCurrent(inputVoltage, outputVoltage,
                outputActive ? Math.max(0.0, outputCurrent) : 0.0,
                enableFraction);
    }

    /** Derivative of the target with respect to the solved input voltage. */
    private double targetInputSlope(double inputVoltage, double enableFraction) {
        if (enableFraction <= 0.0 || inputVoltage <= 0.0) return 0.0;
        double available = inputVoltage - contract.getDropoutVolts();
        if (available <= 0.0 || available >= contract.getNominalOutputVolts())
            return 0.0;
        return enableFraction;
    }

    /** Derivative of the target with respect to the solved enable voltage. */
    private double targetEnableSlope(double inputVoltage, double enableVoltage) {
        if (inputVoltage <= 0.0) return 0.0;
        double available = inputVoltage - contract.getDropoutVolts();
        if (available <= 0.0) return 0.0;
        double base = Math.min(contract.getNominalOutputVolts(), available);
        return base * enableFractionSlope(enableVoltage);
    }

    private double enableFractionSlope(double enableVoltage) {
        if (enableVoltage <= contract.getEnableLowVolts() ||
                enableVoltage >= contract.getEnableHighVolts()) return 0.0;
        return 1.0 / (contract.getEnableHighVolts() -
                contract.getEnableLowVolts());
    }

    /**
     * Linearized input draw for the current solver trial.  The returned
     * coefficients describe current from INPUT to RETURN as
     * current + inputGain*Vin + outputGain*Vout + enableGain*Ven.
     */
    private InputLinearization inputLinearization(double inputVoltage,
            double outputVoltage, double outputCurrent, double enableFraction,
            double targetInputSlope, double targetEnableSlope,
            double outputConductance, boolean outputActive) {
        if (enableFraction <= 0.0 || inputVoltage <= 0.0)
            return new InputLinearization(0.0, 0.0, 0.0, 0.0);
        double enableSlope = enableFractionSlope(getEnableVoltage());
        double quiescent = contract.getQuiescentCurrentAmps();
        if (!outputActive)
            return new InputLinearization(quiescent * enableFraction,
                    0.0, 0.0, quiescent * enableSlope);
        double loadInputGain = outputConductance * targetInputSlope;
        double loadOutputGain = -outputConductance;
        double loadEnableGain = outputConductance * targetEnableSlope;
        double current;
        double inputGain;
        double outputGain;
        double enableGain;
        if (!contract.isAveragedSwitching()) {
            current = outputCurrent + quiescent * enableFraction;
            inputGain = loadInputGain;
            outputGain = loadOutputGain;
            enableGain = loadEnableGain + quiescent * enableSlope;
        } else {
            double efficiency = contract.getEfficiency();
            double conversion = inputVoltage > 0.0 ?
                    Math.max(0.0, outputVoltage) / (inputVoltage * efficiency) : 0.0;
            current = conversion * outputCurrent + quiescent * enableFraction;
            inputGain = conversion * loadInputGain;
            if (inputVoltage > 0.0 && outputVoltage > 0.0)
                inputGain -= outputVoltage * outputCurrent /
                        (inputVoltage * inputVoltage * efficiency);
            outputGain = (inputVoltage > 0.0 && outputVoltage > 0.0 ?
                    outputCurrent / (inputVoltage * efficiency) : 0.0) +
                    conversion * loadOutputGain;
            enableGain = conversion * loadEnableGain + quiescent * enableSlope;
        }
        return new InputLinearization(current, inputGain, outputGain, enableGain);
    }

    private static final class InputLinearization {
        final double current;
        final double inputGain;
        final double outputGain;
        final double enableGain;

        InputLinearization(double current, double inputGain,
                double outputGain, double enableGain) {
            this.current = current;
            this.inputGain = inputGain;
            this.outputGain = outputGain;
            this.enableGain = enableGain;
        }
    }

    /** Implementations differ only in their bounded input-power accounting. */
    abstract double modelInputCurrent(double inputVoltage, double outputVoltage,
            double deliveredOutputCurrent, double enableFraction);

    final double effectiveOutputResistance() {
        // This is the declared in-regulation resistance.  Current limiting is
        // a separate solver-visible characteristic, not an artificial series
        // resistance that causes ordinary 25/50/75/90% loads to droop.
        return contract.getOutputResistanceOhms();
    }

    final double getInputVoltage() { return volts[INPUT_POST] - volts[RETURN_POST]; }
    final double getOutputVoltage() { return volts[OUTPUT_POST] - volts[RETURN_POST]; }
    final double getEnableVoltage() { return volts[ENABLE_POST] - volts[RETURN_POST]; }

    final double getTargetVoltage() {
        return targetVoltage(getInputVoltage(), enableFraction(getEnableVoltage()));
    }

    final double getInputCurrent() {
        double inputVoltage = getInputVoltage();
        double outputVoltage = getOutputVoltage();
        double enableFraction = enableFraction(getEnableVoltage());
        double target = targetVoltage(inputVoltage, enableFraction);
        boolean outputActive = target > 0.0;
        return inputCurrent(inputVoltage, outputVoltage,
                outputCurrentForDrop(target - outputVoltage, outputActive),
                enableFraction, outputActive) +
                getInputLeakCurrent();
    }

    final double getOutputCurrent() {
        double target = getTargetVoltage();
        return outputCurrentForDrop(target - getOutputVoltage(), target > 0.0);
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
        return getTargetVoltage() > 0.0 &&
                getOutputCurrent() >= contract.getMaximumOutputCurrentAmps() -
                CURRENT_LIMIT_TOLERANCE_AMPS;
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
        lastOutputDrop = Double.NaN;
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
                contract.isAveragedSwitching() + " " +
                contract.getUsableRegulatedCurrentFraction() + " " +
                contract.getRegulatedVoltageToleranceVolts();
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
        double usableCurrentFraction = nextDumpDouble(st,
                "usableRegulatedCurrentFraction");
        double voltageTolerance = nextDumpDouble(st,
                "regulatedVoltageToleranceVolts");
        if (st.hasMoreTokens())
            throw new IllegalArgumentException("Unexpected E02 rail dump payload");
        return new RailRegulationContract(variantId, inputId, outputId,
                returnId, enableId, nominal, minimumInput, maximumInput,
                dropout, maximumCurrent, outputResistance, enableLow,
                enableHigh, quiescent, efficiency, averaged, false,
                usableCurrentFraction, voltageTolerance);
    }

    private void validateOperatingPoint(double inputVoltage, double outputVoltage,
            double enableVoltage, boolean boundedTrial) {
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
                !changed(lastTarget, target) && !boundedTrial)
            throw new IllegalArgumentException("Negative E02 rail output is unsupported");
        // With the source or enable absent, an isolated regulator output is a
        // high-impedance reverse-leakage node.  Passive instruments and board
        // topology changes may therefore move it outside the *regulated*
        // band without the regulator sourcing energy.  The conservative
        // absolute terminal ceiling is the contract's declared maximum input
        // voltage; above that common device envelope the operating point is
        // rejected.  Reverse current remains independently leakage-bounded.
        if (outputVoltage > contract.getMaximumInputVolts() +
                UNSUPPORTED_VOLTAGE_TOLERANCE &&
                !changed(lastTarget, target) &&
                !releasingCurrentLimit(target) && !boundedTrial)
            throw new IllegalArgumentException(
                "Unsupported E02 rail output backfeed voltage: output=" +
                outputVoltage + " target=" + target + " ceiling=" +
                contract.getMaximumInputVolts());
        if (inputVoltage > contract.getMaximumInputVolts() +
                UNSUPPORTED_VOLTAGE_TOLERANCE)
            throw new IllegalArgumentException("Rail input exceeds declared envelope");
        double outputCurrent = outputCurrentForDrop(target - outputVoltage,
                target > 0.0);
        // A source/enable mutation can leave the previous rail voltage in the
        // first Newton trial.  Allow that one bounded transition, but reject
        // a persistent reverse output current once the target is unchanged.
        if (outputCurrent < -backfeedCurrentTolerance() &&
                !changed(lastTarget, target) && !boundedTrial)
            throw new IllegalArgumentException("Unsupported rail output backfeed: output=" +
                    outputVoltage + " target=" + target + " current=" + outputCurrent);
        if (outputCurrent > contract.getMaximumOutputCurrentAmps() +
                UNSUPPORTED_VOLTAGE_TOLERANCE && !boundedTrial)
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

    private boolean releasingCurrentLimit(double target) {
        if (target <= 0.0 || !finite(lastOutputDrop)) return false;
        return lastOutputDrop >= contract.getMaximumOutputCurrentAmps() *
                contract.getOutputResistanceOhms();
    }

    /**
     * Delivered load current as a function of target-to-output drop.
     *
     * <p>Inside the declared output resistance the branch regulates near its
     * target.  Once the drop reaches the knee implied by that resistance and
     * the declared limit, the branch becomes a bounded current source.  The
     * tiny leakage on the flat branch prevents an isolated-node singularity
     * without creating meaningful current or power.</p>
     */
    private double outputCurrentForDrop(double drop, boolean outputActive) {
        if (!outputActive) return drop * REVERSE_LEAKAGE_SIEMENS;
        double resistance = contract.getOutputResistanceOhms();
        double knee = contract.getMaximumOutputCurrentAmps() * resistance;
        if (drop <= 0.0) return drop * REVERSE_LEAKAGE_SIEMENS;
        if (drop <= knee) return drop / resistance;
        return contract.getMaximumOutputCurrentAmps() +
                (drop - knee) * CURRENT_LIMIT_LEAKAGE_SIEMENS;
    }

    private double outputConductanceForDrop(double drop, boolean outputActive) {
        if (!outputActive) return REVERSE_LEAKAGE_SIEMENS;
        double resistance = contract.getOutputResistanceOhms();
        double knee = contract.getMaximumOutputCurrentAmps() * resistance;
        // Match the reverse-leakage characteristic exactly.  Using the
        // forward 0.1-ohm tangent for a small negative drop clamped an
        // externally measured, powered-off output near RETURN even though
        // the reported branch current was only leakage.
        if (drop < 0.0) return REVERSE_LEAKAGE_SIEMENS;
        if (drop <= knee) return 1.0 / resistance;
        return CURRENT_LIMIT_LEAKAGE_SIEMENS;
    }

    /**
     * Limit only the Newton tangent point, never the solved reading.  Target
     * changes start at the regulation branch; load mutations can cross the
     * current-limit knee in either direction and are allowed to settle through
     * it on subsequent iterations.
     */
    private double limitNewtonDrop(double requestedDrop, double target,
            boolean outputActive) {
        if (!finite(requestedDrop)) return requestedDrop;
        double knee = contract.getMaximumOutputCurrentAmps() *
                contract.getOutputResistanceOhms();
        if (Double.isNaN(lastOutputDrop) || Double.isNaN(lastTarget) ||
                Math.abs(lastTarget - target) > TARGET_RESTART_STEP_VOLTS)
            return 0.0;

        double drop = requestedDrop;
        double previous = lastOutputDrop;
        if (outputActive && previous <= knee && drop > knee) {
            // Enter the flat current-limit branch only after the regulation
            // tangent has reached its knee.
            drop = previous < knee ? knee : knee +
                    Math.min(MAX_NEWTON_DROP_STEP_VOLTS, drop - knee);
        } else if (outputActive && previous > knee && drop <= knee) {
            // Leave the flat branch at the continuous knee, avoiding a large
            // reverse Newton jump when a load is removed.
            drop = knee;
        }
        // Also bound reverse/off-state moves.  Physical remove/install graph
        // rebuilds may initially present stale node voltages many orders of
        // magnitude outside the accepted solution; visiting finite tangents
        // lets the passive bleed restore the node before envelope validation.
        if ((!outputActive || drop < 0.0 || previous < 0.0) &&
                Math.abs(drop - previous) > MAX_NEWTON_DROP_STEP_VOLTS)
            drop = previous + (drop > previous ? MAX_NEWTON_DROP_STEP_VOLTS :
                    -MAX_NEWTON_DROP_STEP_VOLTS);
        return drop;
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
