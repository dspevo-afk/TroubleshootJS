package com.lushprojects.circuitjs1.client;

import java.util.HashMap;
import java.util.Vector;

/** Actual CircuitJS warm-storage and current-limit release contracts for E02. */
public final class E02BoundedTangentContractTest {
    private static final double INPUT_SOURCE_VOLTS = 12.0;
    private static final double INPUT_SOURCE_OHMS = .1;
    private static final double ENABLE_SOURCE_VOLTS = 2.5;
    private static final double ENABLE_SOURCE_OHMS = 1.0;
    private static final double OUTPUT_CAPACITANCE = 1e-6;
    private static final double WARM_SOURCE_VOLTS = 8.0;
    private static final double WARM_SOURCE_OHMS = 10.0;
    private static final double WARM_LOAD_OHMS = 100.0;
    private static final double LIMIT_LOAD_OHMS = 5.0;
    private static final int PRECONDITION_STEPS = 8;
    private static final int RELEASE_STEPS = 16;
    private static final int MAX_ACCEPTED_ATTEMPTS = 64;
    private static final long MAX_CASE_MILLIS = 90000;
    private static final long MAX_UNIT_MILLIS = 5000;
    private static final double CURRENT_LIMIT_TOLERANCE = 1e-7;
    private static final double OUTPUT_KCL_TOLERANCE = 2e-5;
    private static int assertions;
    private static int completedCases;

    private E02BoundedTangentContractTest() { }

    public static void main(String[] args) {
        CirSim savedSim = CircuitElm.sim;
        CirSim savedSingleton = CirSim.theSim;
        Point savedPs1 = CircuitElm.ps1;
        Point savedPs2 = CircuitElm.ps2;
        HashMap<String, String> savedLocalization = CirSim.localizationMap;
        try {
            runMatrix(false, RailRegulationContract.linear5V(), "linear5V");
            runMatrix(true, RailRegulationContract.averagedSwitching3V3(),
                    "averaged3V3");
            check(CircuitElm.sim == savedSim && CirSim.theSim == savedSingleton &&
                    CircuitElm.ps1 == savedPs1 && CircuitElm.ps2 == savedPs2 &&
                    CirSim.localizationMap == savedLocalization,
                    "suite restores the exact native solver globals");
            check(completedCases == 8,
                    "suite completed the full eight-case regulator/timestep census");
            System.out.println("PASS: E02 bounded tangent contracts assertions=" + assertions +
                    " cases=" + completedCases +
                    " scope=ACTUAL_SOLVER_WARM_STORAGE_AND_LIMIT_RELEASE");
        } finally {
            CircuitElm.sim = savedSim;
            CirSim.theSim = savedSingleton;
            CircuitElm.ps1 = savedPs1;
            CircuitElm.ps2 = savedPs2;
            CirSim.localizationMap = savedLocalization;
        }
    }

    private static void runMatrix(final boolean averaged,
            final RailRegulationContract contract, final String variant) {
        verifyWarmStorageReconnect(averaged, contract, variant, 1e-4);
        verifyCurrentLimitRelease(averaged, contract, variant, 1e-4);
        verifyWarmStorageReconnect(averaged, contract, variant, 5e-5);
        verifyCurrentLimitRelease(averaged, contract, variant, 5e-5);
    }

    private static void verifyWarmStorageReconnect(final boolean averaged,
            final RailRegulationContract contract, final String variant,
            final double dt) {
        final String id = variant + "_warm_storage_dt_" + dt;
        runCase(id, new CaseWork() {
            public void run(CaseState c) {
                Fixture f = new Fixture(c, averaged, contract, dt, 0.0,
                        WARM_LOAD_OHMS, true);
                c.analyze();
                check(f.outputCap.capacitance == OUTPUT_CAPACITANCE &&
                        !f.outputCap.isTrapezoidal(),
                        id + " uses the actual 1-uF backward-Euler output capacitor");

                for (int i = 0; i < PRECONDITION_STEPS; i++) {
                    c.advanceOne();
                    if (i == 0)
                        check(f.regulator.getEnableVoltage() <= contract.getEnableLowVolts(),
                                id + " holds the real finite enable source below its low knee");
                    checkOperatingBounds(f, id + " precharge " + i);
                }
                double chargedVoltage = capacitorVoltage(f.outputCap);
                double parallelLoad = WARM_LOAD_OHMS;
                double dividerExpectation = WARM_SOURCE_VOLTS * parallelLoad /
                        (WARM_SOURCE_OHMS + parallelLoad);
                near(chargedVoltage, dividerExpectation, .02,
                        id + " actual 8-V/10-ohm precharge reaches the independent 100-ohm divider");
                check(chargedVoltage > contract.getNominalOutputVolts() + .5 &&
                        chargedVoltage < contract.getMaximumInputVolts(),
                        id + " stores a real, bounded output overcharge before enabling E02");
                near(f.warmSeries.getCurrent(),
                        (WARM_SOURCE_VOLTS - chargedVoltage) / WARM_SOURCE_OHMS,
                        1e-5, id + " actual precharge resistor current follows its terminal drop");
                check(f.warmIsolation.position == 0 && f.warmSeries.getCurrent() > 0.0,
                        id + " precharge source was physically connected during charging");
                double storedHistory = f.outputCap.voltdiff;

                f.warmIsolation.position = 1;
                f.enableSource.maxVoltage = ENABLE_SOURCE_VOLTS;
                c.analyze();
                check(f.warmIsolation.position == 1,
                        id + " opens the temporary source with the actual series switch");
                near(f.outputCap.voltdiff, storedHistory, 1e-12,
                        id + " solver reanalysis preserves capacitor history without reset");

                double priorCapVoltage = capacitorVoltage(f.outputCap);
                double firstOutput = Double.NaN;
                double firstOutputCurrent = Double.NaN;
                int firstForwardStep = -1;
                double maximumKclResidual = 0.0;
                boolean sawForwardRegulation = false;
                for (int i = 0; i < RELEASE_STEPS; i++) {
                    double priorTime = c.sim.t;
                    c.advanceOne();
                    double nextCapVoltage = capacitorVoltage(f.outputCap);
                    double stepSeconds = c.sim.t - priorTime;
                    if (i == 0)
                        check(f.warmIsolation.getCurrent() == 0.0,
                                id + " accepted enabled step confirms zero current in the open source switch");
                    if (i == 0)
                        check(f.regulator.getEnableVoltage() > contract.getEnableHighVolts(),
                                id + " finite enable source now drives above the actual high knee");
                    checkOutputKcl(f, WARM_LOAD_OHMS, priorCapVoltage,
                            nextCapVoltage, stepSeconds, id + " enable transient step " + i);
                    maximumKclResidual = Math.max(maximumKclResidual,
                            Math.abs(outputKclResidual(f, WARM_LOAD_OHMS,
                                    priorCapVoltage, nextCapVoltage, stepSeconds)));
                    checkOperatingBounds(f, id + " enable transient step " + i);
                    if (i == 0) {
                        firstOutput = nextCapVoltage;
                        firstOutputCurrent = f.regulator.getOutputCurrent();
                    }
                    if (i > 0 && firstForwardStep < 0 &&
                            f.regulator.getOutputCurrent() > 1e-5)
                        firstForwardStep = i;
                    if (f.regulator.getOutputCurrent() > 1e-5)
                        sawForwardRegulation = true;
                    priorCapVoltage = nextCapVoltage;
                }

                if (averaged && Math.abs(dt - 1e-4) < 1e-12) {
                    double expectedPassiveFirst = chargedVoltage /
                            (1.0 + dt / (OUTPUT_CAPACITANCE * WARM_LOAD_OHMS));
                    near(firstOutput, expectedPassiveFirst, .002,
                            id + " first overcharged step follows the independent passive RC discharge");
                    check(firstOutputCurrent < 0.0 &&
                            firstOutput > contract.getNominalOutputVolts(),
                            id + " first 100-us average-model point remains above target on passive reverse leakage");
                    check(firstForwardStep > 0,
                            id + " subsequent accepted steps return the output branch to forward regulation");
                }
                double expectedFinal = expectedRegulatedOutput(f, WARM_LOAD_OHMS);
                near(f.regulator.getOutputVoltage(), expectedFinal, .025,
                        id + " actual capacitor recovers to the independent finite-resistance rail");
                check(sawForwardRegulation,
                        id + " accepted transient includes a real forward regulator branch current");
                check(c.sim.a01AcceptedStepCount == c.attempts &&
                        c.attempts == PRECONDITION_STEPS + RELEASE_STEPS,
                        id + " records every precharge and recovery call as an accepted CircuitJS step");
                c.targets("{\"model\":\"" + variant + "\",\"dtSeconds\":" + number(dt) +
                        ",\"prechargeV\":8.0,\"prechargeOhms\":10.0," +
                        "\"loadOhms\":100.0,\"enableV\":2.5,\"capacitanceF\":1.0E-6}");
                c.observed("{\"prechargedV\":" + number(chargedVoltage) +
                        ",\"firstEnabledV\":" + number(firstOutput) +
                        ",\"firstEnabledCurrentA\":" + number(firstOutputCurrent) +
                        ",\"firstForwardStep\":" + firstForwardStep +
                        ",\"finalV\":" + number(f.regulator.getOutputVoltage()) +
                        ",\"finalCurrentA\":" + number(f.regulator.getOutputCurrent()) +
                        ",\"maximumOutputKclResidualA\":" + number(maximumKclResidual) +
                        ",\"prechargeSwitchOpen\":" + (f.warmIsolation.position == 1) + "}");
            }
        });
    }

    private static void verifyCurrentLimitRelease(final boolean averaged,
            final RailRegulationContract contract, final String variant,
            final double dt) {
        final String id = variant + "_current_limit_release_dt_" + dt;
        runCase(id, new CaseWork() {
            public void run(CaseState c) {
                Fixture f = new Fixture(c, averaged, contract, dt,
                        ENABLE_SOURCE_VOLTS, LIMIT_LOAD_OHMS, false);
                c.analyze();
                for (int i = 0; i < PRECONDITION_STEPS; i++) {
                    c.advanceOne();
                    checkOperatingBounds(f, id + " overload settle " + i);
                }
                double limitedVoltage = capacitorVoltage(f.outputCap);
                near(limitedVoltage,
                        contract.getMaximumOutputCurrentAmps() * LIMIT_LOAD_OHMS,
                        .02, id + " actual 5-ohm load settles at the independent I-limit times R");
                check(f.regulator.isCurrentLimited(),
                        id + " actual loaded regulator reports its current-limited branch");
                near(f.regulator.getOutputCurrent(),
                        contract.getMaximumOutputCurrentAmps(), 2e-5,
                        id + " accepted overload current equals the declared branch limit");
                check(f.regulator.getOutputCurrent() <=
                        contract.getMaximumOutputCurrentAmps() + CURRENT_LIMIT_TOLERANCE,
                        id + " accepted overload stays within the unchanged strict current ceiling");

                double storedHistory = f.outputCap.voltdiff;
                f.load.setResistance(WARM_LOAD_OHMS);
                c.analyze();
                near(f.outputCap.voltdiff, storedHistory, 1e-12,
                        id + " load restamp and reanalysis preserve actual capacitor history");
                check(f.load.resistance == WARM_LOAD_OHMS,
                        id + " same physical load element is restamped from 5 to 100 ohms");

                double priorCapVoltage = capacitorVoltage(f.outputCap);
                double firstReleasedCurrent = Double.NaN;
                double maximumKclResidual = 0.0;
                int limitedAfterRelease = 0;
                for (int i = 0; i < RELEASE_STEPS; i++) {
                    double priorTime = c.sim.t;
                    c.advanceOne();
                    double nextCapVoltage = capacitorVoltage(f.outputCap);
                    double stepSeconds = c.sim.t - priorTime;
                    checkOutputKcl(f, WARM_LOAD_OHMS, priorCapVoltage,
                            nextCapVoltage, stepSeconds, id + " released step " + i);
                    maximumKclResidual = Math.max(maximumKclResidual,
                            Math.abs(outputKclResidual(f, WARM_LOAD_OHMS,
                                    priorCapVoltage, nextCapVoltage, stepSeconds)));
                    checkOperatingBounds(f, id + " released step " + i);
                    if (i == 0) {
                        firstReleasedCurrent = f.regulator.getOutputCurrent();
                        check(!f.regulator.isCurrentLimited() &&
                                firstReleasedCurrent < contract.getMaximumOutputCurrentAmps() - .02,
                                id + " first accepted release step exits the old current-limited load state");
                    }
                    if (f.regulator.isCurrentLimited()) limitedAfterRelease++;
                    priorCapVoltage = nextCapVoltage;
                }
                double expectedFinal = expectedRegulatedOutput(f, WARM_LOAD_OHMS);
                near(f.regulator.getOutputVoltage(), expectedFinal, .025,
                        id + " actual load release restores the independent regulated rail");
                check(limitedAfterRelease == 0,
                        id + " all accepted released-load steps remain below current limit");
                check(c.sim.a01AcceptedStepCount == c.attempts &&
                        c.attempts == PRECONDITION_STEPS + RELEASE_STEPS,
                        id + " records each overload and release call as an accepted solver step");
                c.targets("{\"model\":\"" + variant + "\",\"dtSeconds\":" + number(dt) +
                        ",\"initialLoadOhms\":5.0,\"releasedLoadOhms\":100.0," +
                        "\"limitA\":" + number(contract.getMaximumOutputCurrentAmps()) +
                        ",\"capacitanceF\":1.0E-6}");
                c.observed("{\"limitedCapV\":" + number(limitedVoltage) +
                        ",\"firstReleasedCurrentA\":" + number(firstReleasedCurrent) +
                        ",\"finalV\":" + number(f.regulator.getOutputVoltage()) +
                        ",\"finalCurrentA\":" + number(f.regulator.getOutputCurrent()) +
                        ",\"maximumOutputKclResidualA\":" + number(maximumKclResidual) +
                        ",\"limitedStepsAfterRelease\":" + limitedAfterRelease + "}");
            }
        });
    }

    private static double outputKclResidual(Fixture f, double loadOhms,
            double previousCapVoltage, double nextCapVoltage, double dt) {
        double capacitorCurrent = OUTPUT_CAPACITANCE *
                (nextCapVoltage - previousCapVoltage) / dt;
        double loadVoltage = f.load.getPostVoltage(0) - f.load.getPostVoltage(1);
        double loadCurrent = loadVoltage / loadOhms;
        return f.regulator.getOutputCurrent() - capacitorCurrent - loadCurrent;
    }

    private static void checkOutputKcl(Fixture f, double loadOhms,
            double previousCapVoltage, double nextCapVoltage, double dt,
            String label) {
        near(dt, f.sim.maxTimeStep, 1e-12,
                label + " uses the configured actual accepted timestep");
        double capacitorCurrent = OUTPUT_CAPACITANCE *
                (nextCapVoltage - previousCapVoltage) / dt;
        double loadVoltage = f.load.getPostVoltage(0) - f.load.getPostVoltage(1);
        double loadCurrent = loadVoltage / loadOhms;
        double regulatorCurrent = f.regulator.getOutputCurrent();
        double residual = regulatorCurrent - capacitorCurrent - loadCurrent;
        check(finite(capacitorCurrent) && finite(loadCurrent) &&
                Math.abs(residual) <= OUTPUT_KCL_TOLERANCE,
                label + " actual output-terminal KCL: Ireg=" + regulatorCurrent +
                    " C*dV/dt=" + capacitorCurrent + " Vload/R=" + loadCurrent +
                    " residual=" + residual);
    }

    private static void checkOperatingBounds(Fixture f, String label) {
        AbstractRailRegulatorElm r = f.regulator;
        RailRegulationContract contract = r.getContract();
        double input = r.getInputVoltage();
        double output = r.getOutputVoltage();
        double enable = r.getEnableVoltage();
        double inputCurrent = r.getInputCurrent();
        double outputCurrent = r.getOutputCurrent();
        double inputPower = r.getInputPowerWatts();
        double outputPower = r.getOutputPowerWatts();
        double loss = r.getPowerLossWatts();
        check(finite(input) && finite(output) && finite(enable) &&
                finite(inputCurrent) && finite(outputCurrent) && finite(inputPower) &&
                finite(outputPower) && finite(loss),
                label + " actual source and regulator readings stay finite");
        check(input >= contract.getMinimumInputVolts() &&
                input <= contract.getMaximumInputVolts() + 1e-6,
                label + " actual finite-source input remains inside the declared rail envelope: " + input);
        check(output >= -1e-6 &&
                output <= contract.getMaximumInputVolts() + 1e-6,
                label + " actual output remains inside the unchanged absolute voltage envelope: " + output);
        check(enable >= -1e-6 && enable <= contract.getMaximumInputVolts() + 1e-6,
                label + " actual enable terminal remains inside the absolute voltage envelope");
        check(inputCurrent >= 0.0 && inputPower >= 0.0 &&
                loss >= -1e-8,
                label + " E02 input and loss power remain nonregenerative");
        check(outputCurrent >= -2e-5 &&
                outputCurrent <= contract.getMaximumOutputCurrentAmps() +
                    CURRENT_LIMIT_TOLERANCE,
                label + " actual output current stays inside the strict forward limit and bounded passive reverse path: " +
                    outputCurrent);
        near(f.inputSeries.getCurrent(), inputCurrent, 2e-6,
                label + " actual input source resistor satisfies terminal KCL");
        near(f.enableSeries.getCurrent(), r.getEnableLeakCurrent(), 2e-8,
                label + " actual enable source resistor satisfies terminal KCL");
    }

    private static double expectedRegulatedOutput(Fixture f, double loadOhms) {
        double available = f.regulator.getInputVoltage() - f.regulator.getContract().getDropoutVolts();
        double target = Math.min(f.regulator.getContract().getNominalOutputVolts(), available);
        return target / (1.0 + f.regulator.getContract().getOutputResistanceOhms() /
                loadOhms);
    }

    private static double capacitorVoltage(CapacitorElm cap) {
        return cap.getPostVoltage(0) - cap.getPostVoltage(1);
    }

    private static void runCase(String id, CaseWork work) {
        CaseState c = new CaseState(id);
        Throwable operationFailure = null;
        try {
            work.run(c);
        } catch (Throwable problem) {
            operationFailure = problem;
        }
        c.operationElapsedMillis = millisSince(c.startedAt);
        if (c.operationElapsedMillis > MAX_CASE_MILLIS) {
            AssertionError overrun = new AssertionError(
                    "E02 bounded tangent operation exceeded the 90-second case budget");
            if (operationFailure == null) operationFailure = overrun;
            else operationFailure.addSuppressed(overrun);
        }
        Throwable cleanupFailure = null;
        boolean clean = false;
        try {
            clean = c.cleanup();
        } catch (Throwable problem) {
            cleanupFailure = problem;
        }
        if (cleanupFailure != null && operationFailure != null)
            operationFailure.addSuppressed(cleanupFailure);
        boolean passed = operationFailure == null && cleanupFailure == null && clean;
        Throwable reportFailure = operationFailure == null ? cleanupFailure : operationFailure;
        System.out.println(c.report(passed ? "PASS" : "FAIL", clean,
                reportFailure == null ? null : reportFailure.getMessage()));
        if (!passed) {
            if (operationFailure != null)
                throw new AssertionError("E02 bounded tangent case failed: " + id,
                        operationFailure);
            if (cleanupFailure != null)
                throw new AssertionError("E02 bounded tangent cleanup failed: " + id,
                        cleanupFailure);
            throw new AssertionError("E02 bounded tangent cleanup failed: " + id);
        }
        completedCases++;
    }

    private static void check(boolean passed, String message) {
        assertions++;
        if (!passed) throw new AssertionError("E02 bounded tangent: " + message);
    }

    private static void near(double actual, double expected, double tolerance,
            String message) {
        check(finite(actual) && finite(expected) &&
                Math.abs(actual - expected) <= tolerance,
                message + ": observed=" + actual + " expected=" + expected +
                    " tolerance=" + tolerance);
    }

    private static boolean finite(double value) {
        return !Double.isNaN(value) && !Double.isInfinite(value);
    }

    private static String number(double value) {
        return finite(value) ? Double.toString(value) : "null";
    }

    private static String quote(String value) {
        if (value == null) return "null";
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    private interface CaseWork { void run(CaseState c); }

    private static final class Fixture {
        final TestCirSim sim;
        final AbstractRailRegulatorElm regulator;
        final DCVoltageElm inputSource;
        final ResistorElm inputSeries;
        final DCVoltageElm enableSource;
        final ResistorElm enableSeries;
        final ResistorElm load;
        final CapacitorElm outputCap;
        DCVoltageElm warmSource;
        ResistorElm warmSeries;
        SwitchElm warmIsolation;

        Fixture(CaseState c, boolean averaged, RailRegulationContract contract,
                double dt, double enableVoltage, double loadOhms,
                boolean withWarmSource) {
            sim = c.sim;
            inputSource = new DCVoltageElm(64, 64);
            inputSource.maxVoltage = INPUT_SOURCE_VOLTS;
            inputSource.setPosition(64, 64, 64, 128);
            c.add(inputSource);

            inputSeries = new ResistorElm(64, 128);
            inputSeries.setPosition(inputSource.getPost(1).x,
                    inputSource.getPost(1).y, 256, 160);
            inputSeries.setResistance(INPUT_SOURCE_OHMS);
            c.add(inputSeries);

            regulator = averaged ?
                    new AveragedSwitchingRegulatorElm(256, 160, contract) :
                    new LinearRegulatorElm(256, 160, contract);
            regulator.setPosition(256, 160, 416, 160);
            c.add(regulator);

            enableSource = new DCVoltageElm(560, 64);
            enableSource.maxVoltage = enableVoltage;
            enableSource.setPosition(560, 64, 560, 128);
            c.add(enableSource);

            enableSeries = new ResistorElm(560, 128);
            enableSeries.setPosition(enableSource.getPost(1).x,
                    enableSource.getPost(1).y,
                    regulator.getPost(AbstractRailRegulatorElm.ENABLE_POST).x,
                    regulator.getPost(AbstractRailRegulatorElm.ENABLE_POST).y);
            enableSeries.setResistance(ENABLE_SOURCE_OHMS);
            c.add(enableSeries);

            load = new ResistorElm(416, 160);
            load.setPosition(regulator.getPost(AbstractRailRegulatorElm.OUTPUT_POST).x,
                    regulator.getPost(AbstractRailRegulatorElm.OUTPUT_POST).y,
                    416, 224);
            load.setResistance(loadOhms);
            c.add(load);

            outputCap = new CapacitorElm(416, 160);
            outputCap.setPosition(
                    regulator.getPost(AbstractRailRegulatorElm.OUTPUT_POST).x,
                    regulator.getPost(AbstractRailRegulatorElm.OUTPUT_POST).y,
                    regulator.getPost(AbstractRailRegulatorElm.RETURN_POST).x,
                    regulator.getPost(AbstractRailRegulatorElm.RETURN_POST).y);
            outputCap.setCapacitance(OUTPUT_CAPACITANCE);
            outputCap.initialVoltage = 0.0;
            outputCap.flags |= CapacitorElm.FLAG_BACK_EULER;
            outputCap.reset();
            c.add(outputCap);

            addGround(c, inputSource.getPost(0));
            addGround(c, enableSource.getPost(0));
            addGround(c,
                    regulator.getPost(AbstractRailRegulatorElm.RETURN_POST));
            addGround(c, load.getPost(1));

            if (withWarmSource) addWarmSource(c);
            c.sim.maxTimeStep = dt;
            c.sim.minTimeStep = dt;
            c.sim.timeStep = dt;
            c.sim.adjustTimeStep = false;
        }

        private void addWarmSource(CaseState c) {
            warmSource = new DCVoltageElm(640, 160);
            warmSource.maxVoltage = WARM_SOURCE_VOLTS;
            warmSource.setPosition(640, 160, 576, 160);
            c.add(warmSource);
            addGround(c, warmSource.getPost(0));

            warmSeries = new ResistorElm(576, 160);
            warmSeries.setPosition(warmSource.getPost(1).x,
                    warmSource.getPost(1).y, 512, 160);
            warmSeries.setResistance(WARM_SOURCE_OHMS);
            c.add(warmSeries);

            warmIsolation = new SwitchElm(512, 160);
            warmIsolation.setPosition(512, 160, 448, 160);
            c.add(warmIsolation);
            c.wire(warmIsolation.getPost(1),
                    regulator.getPost(AbstractRailRegulatorElm.OUTPUT_POST));
        }
    }

    private static final class CaseState {
        final String id;
        final long startedAt = System.nanoTime();
        final CirSim previousSim = CircuitElm.sim;
        final CirSim previousSingleton = CirSim.theSim;
        final Point previousPs1 = CircuitElm.ps1;
        final Point previousPs2 = CircuitElm.ps2;
        final HashMap<String, String> previousLocalization = CirSim.localizationMap;
        final TestCirSim sim = new TestCirSim();
        final Vector<CircuitElm> original;
        final Vector<CircuitElm> originalElements;
        final Vector<CircuitElm> graph = new Vector<CircuitElm>();
        final Vector<CircuitElm> owned = new Vector<CircuitElm>();
        String targetJson = "{}", observedJson = "{}";
        int attempts, analyses, maxSubIterations;
        long maxStepMillis, maxAnalysisMillis, operationElapsedMillis,
                cleanupElapsedMillis;
        boolean cleanupComplete, ownedElementsDeleted;

        CaseState(String id) {
            this.id = id;
            original = sim.elmList;
            originalElements = new Vector<CircuitElm>(original);
            sim.elmList = graph;
            sim.adjustables = new Vector<Adjustable>();
            sim.undoStack = new Vector<String>();
            sim.redoStack = new Vector<String>();
            CircuitElm.sim = sim;
            CirSim.theSim = sim;
            sim.a01MeasurementRunning = true;
        }

        <T extends CircuitElm> T add(T element) {
            graph.add(element);
            owned.add(element);
            return element;
        }

        void wire(Point from, Point to) {
            WireElm wire = new WireElm(from.x, from.y);
            wire.setPosition(from.x, from.y, to.x, to.y);
            add(wire);
        }

        void analyze() {
            checkWithinBudget();
            long started = System.nanoTime();
            try {
                sim.solverExecutor.analyze();
            } catch (RuntimeException error) {
                recordAnalysis(started);
                throw error;
            } catch (Error error) {
                recordAnalysis(started);
                throw error;
            }
            long elapsed = recordAnalysis(started);
            check(elapsed <= MAX_UNIT_MILLIS,
                    id + " analysis call stays within the five-second unit budget");
            checkWithinBudget();
        }

        void advanceOne() {
            checkWithinBudget();
            check(attempts < MAX_ACCEPTED_ATTEMPTS,
                    id + " remains within 64 bounded accepted-step calls");
            attempts++;
            long beforeAccepted = sim.a01AcceptedStepCount;
            double beforeTime = sim.t;
            long started = System.nanoTime();
            try {
                sim.solverExecutor.advanceSteps(1);
            } catch (RuntimeException error) {
                recordStep(started);
                throw error;
            } catch (Error error) {
                recordStep(started);
                throw error;
            }
            long elapsed = recordStep(started);
            check(elapsed <= MAX_UNIT_MILLIS,
                    id + " advanceSteps(1) call stays within the five-second unit budget");
            check(sim.a01AcceptedStepCount == beforeAccepted + 1 &&
                    sim.solverExecutor.hasAcceptedStepAt(sim.t),
                    id + " executor publishes exactly one actual accepted solver step");
            near(sim.t - beforeTime, sim.maxTimeStep, 1e-12,
                    id + " one actual accepted call advances the fixed physical timestep");
            maxSubIterations = Math.max(maxSubIterations, sim.subIterations);
            checkWithinBudget();
        }

        private long recordAnalysis(long started) {
            long elapsed = millisSince(started);
            maxAnalysisMillis = Math.max(maxAnalysisMillis, elapsed);
            analyses++;
            return elapsed;
        }

        private long recordStep(long started) {
            long elapsed = millisSince(started);
            maxStepMillis = Math.max(maxStepMillis, elapsed);
            return elapsed;
        }

        void targets(String json) { targetJson = json; }
        void observed(String json) { observedJson = json; }

        private void checkWithinBudget() {
            check(attempts <= MAX_ACCEPTED_ATTEMPTS && workUnits() <= 640,
                    id + " stays within 64 calls and 640 actual solver work units");
            check(millisSince(startedAt) <= MAX_CASE_MILLIS,
                    id + " operation stays within the 90-second case budget");
        }

        boolean cleanup() {
            long cleanupStarted = System.nanoTime();
            Throwable failure = null;
            boolean disposed = true;
            CircuitElm.sim = sim;
            CirSim.theSim = sim;
            try {
                try {
                    sim.solverExecutor.retire();
                    sim.solverExecutor.invalidate();
                } catch (RuntimeException error) {
                    failure = retain(failure, error);
                } catch (Error error) {
                    failure = retain(failure, error);
                }
                sim.a01MeasurementRunning = false;
                sim.elmList = original;
                for (CircuitElm element : owned) {
                    try {
                        element.delete();
                    } catch (RuntimeException error) {
                        disposed = false;
                        failure = retain(failure, error);
                    } catch (Error error) {
                        disposed = false;
                        failure = retain(failure, error);
                    }
                }
                ownedElementsDeleted = disposed;
                owned.clear();
                graph.clear();
                sim.circuitMatrix = null;
                sim.wireInfoList = null;
            } finally {
                CircuitElm.sim = previousSim;
                CirSim.theSim = previousSingleton;
                CircuitElm.ps1 = previousPs1;
                CircuitElm.ps2 = previousPs2;
                CirSim.localizationMap = previousLocalization;
            }
            cleanupComplete = failure == null && disposed && graph.isEmpty() &&
                    owned.isEmpty() && sim.elmList == original &&
                    vectorEquals(original, originalElements) && sim.circuitMatrix == null &&
                    sim.wireInfoList == null && sim.adjustables != null &&
                    sim.adjustables.isEmpty() && sim.generatedBoardInstance == null &&
                    !sim.activeMeasurementOverlay && !sim.solverExecutor.isUnavailable() &&
                    CircuitElm.sim == previousSim && CirSim.theSim == previousSingleton &&
                    CircuitElm.ps1 == previousPs1 && CircuitElm.ps2 == previousPs2 &&
                    CirSim.localizationMap == previousLocalization;
            cleanupElapsedMillis = millisSince(cleanupStarted);
            if (failure != null) observedJson = "{\"cleanupError\":" +
                    quote(failure.getClass().getSimpleName()) + "}";
            return cleanupComplete;
        }

        String report(String status, boolean clean, String error) {
            return "E02_BOUNDED_TANGENT {\"case\":" + quote(id) +
                    ",\"status\":" + quote(status) + ",\"targets\":" + targetJson +
                    ",\"observed\":" + observedJson + ",\"error\":" + quote(error) +
                    ",\"timing\":{\"elapsedMs\":" + millisSince(startedAt) +
                    ",\"operationElapsedMs\":" + operationElapsedMillis +
                    ",\"cleanupElapsedMs\":" + cleanupElapsedMillis +
                    ",\"analysisCalls\":" + analyses + ",\"stepCalls\":" + attempts +
                    ",\"acceptedSteps\":" + sim.a01AcceptedStepCount +
                    ",\"workUnits\":" + workUnits() + ",\"maxAnalysisMs\":" +
                    maxAnalysisMillis + ",\"maxStepMs\":" + maxStepMillis +
                    ",\"maxSubIterations\":" + maxSubIterations +
                    "},\"cleanup\":{\"clean\":" + clean +
                    ",\"graphEmpty\":" + graph.isEmpty() +
                    ",\"ownedElementsDisposed\":" +
                        (ownedElementsDeleted && owned.isEmpty()) +
                    ",\"predecessorGraphRestored\":" +
                        (sim.elmList == original && vectorEquals(original, originalElements)) +
                    ",\"matrixCleared\":" + (sim.circuitMatrix == null) +
                    ",\"adjustablesEmpty\":" +
                        (sim.adjustables != null && sim.adjustables.isEmpty()) +
                    ",\"generatedBoardAbsent\":" +
                        (sim.generatedBoardInstance == null) +
                    ",\"measurementOverlayInactive\":" + !sim.activeMeasurementOverlay +
                    ",\"singletonsRestored\":" +
                        (CircuitElm.sim == previousSim && CirSim.theSim == previousSingleton) +
                    "}}";
        }

        private int workUnits() { return 1 + analyses + attempts; }

        private boolean vectorEquals(Vector<CircuitElm> first,
                Vector<CircuitElm> second) {
            if (first.size() != second.size()) return false;
            for (int i = 0; i < first.size(); i++)
                if (first.elementAt(i) != second.elementAt(i)) return false;
            return true;
        }

        private Throwable retain(Throwable first, Throwable next) {
            if (first == null) return next;
            first.addSuppressed(next);
            return first;
        }
    }

    private static long millisSince(long started) {
        long elapsed = System.nanoTime() - started;
        return elapsed / 1000000L + (elapsed % 1000000L == 0L ? 0L : 1L);
    }

    private static GroundElm addGround(CaseState c, Point point) {
        GroundElm ground = new GroundElm(point.x, point.y);
        ground.setPosition(point.x, point.y, point.x, point.y + 32);
        return c.add(ground);
    }

    private static final class TestCirSim extends CirSim {
        TestCirSim() {
            gridSize = 16;
            gridMask = ~15;
            gridRound = 7;
            elmList = new Vector<CircuitElm>();
            adjustables = new Vector<Adjustable>();
            undoStack = new Vector<String>();
            redoStack = new Vector<String>();
        }

        @Override void needAnalyze() {
            if (elmList != null && CircuitElm.sim == this)
                solverExecutor.invalidate();
            analyzeFlag = true;
        }

        @Override void stop(String message, CircuitElm element) {
            stopMessage = message;
            stopElm = element;
            circuitMatrix = null;
            setSimRunning(false);
            analyzeFlag = false;
        }

        @Override public void setSimRunning(boolean running) { simRunning = running; }
        @Override void repaint() { }
        @Override void refreshBoardModificationControls() { }
    }
}
