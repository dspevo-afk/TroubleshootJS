package com.lushprojects.circuitjs1.client;

import java.util.Vector;

/** Actual CircuitJS timestep contracts for E02 enable-knee convergence. */
public final class E02RegulatorKneeContractTest {
    private static final double DT = .00005;
    private static final int MAX_ACCEPTED_ATTEMPTS = 64;
    private static final long MAX_CASE_MILLIS = 90000;
    private static final long MAX_UNIT_MILLIS = 5000;
    private static final double LOAD_OHMS = 30.0;
    private static int assertions;
    private static int completedCases;

    public static void main(String[] args) {
        verifyUpperKnee(false, RailRegulationContract.linear5V());
        verifyUpperKnee(true, RailRegulationContract.averagedSwitching5V());
        verifyLowKnee(false, RailRegulationContract.linear5V());
        verifyLowKnee(true, RailRegulationContract.averagedSwitching5V());
        verifyColdStartRamp(false, RailRegulationContract.linear5V());
        verifyColdStartRamp(true, RailRegulationContract.averagedSwitching5V());
        verifyFiniteUnacceptedTrial(false, RailRegulationContract.linear5V());
        verifyFiniteUnacceptedTrial(true, RailRegulationContract.averagedSwitching5V());
        verifyPersistentInputRejection(false, RailRegulationContract.linear5V());
        verifyPersistentInputRejection(true, RailRegulationContract.averagedSwitching5V());
        verifyNonfiniteInputRejection(false, RailRegulationContract.linear5V());
        verifyNonfiniteInputRejection(true, RailRegulationContract.averagedSwitching5V());
        System.out.println("PASS: E02 regulator knee contracts cases=" +
                completedCases + " assertions=" + assertions);
    }

    private static void verifyUpperKnee(final boolean averaged,
            final RailRegulationContract contract) {
        runCase(contract.getVariantId() + "_upper_knee_rise_fall", new CaseWork() {
            public void run(CaseState c) {
                final Board b = board(c, averaged, contract, 12.0, 1.998,
                        10.0, 10.0, 100e-6, 20e-6);
                c.analyze();
                c.advance(4);
                double lowEnable = terminal(b.regulator,
                        AbstractRailRegulatorElm.ENABLE_POST);
                double lowTarget = expectedTarget(contract, terminal(b.regulator,
                        AbstractRailRegulatorElm.INPUT_POST), lowEnable);
                check(lowEnable < contract.getEnableHighVolts(),
                        "initial upper-knee point is below EN high");
                near(lowTarget, 5.0 * (1.998 - .8) / 1.2, .001,
                        "source 1.998 V produces the independent below-knee target");
                checkRegulatedEquilibrium(b, contract, lowTarget, "upper-knee low side");

                b.enableSource.maxVoltage = 2.002;
                c.analyze();
                boolean crossedUp = false;
                for (int i = 0; i < 16; i++) {
                    c.advance(1);
                    if (terminal(b.regulator, AbstractRailRegulatorElm.ENABLE_POST) >=
                            contract.getEnableHighVolts()) crossedUp = true;
                }
                double highEnable = terminal(b.regulator,
                        AbstractRailRegulatorElm.ENABLE_POST);
                double highTarget = expectedTarget(contract, terminal(b.regulator,
                        AbstractRailRegulatorElm.INPUT_POST), highEnable);
                check(crossedUp && highEnable > contract.getEnableHighVolts(),
                        "actual finite-source RC rises across EN high");
                near(highTarget, 5.0, .001,
                        "source 2.002 V produces the independent upper target");
                checkRegulatedEquilibrium(b, contract, highTarget, "upper-knee high side");

                b.enableSource.maxVoltage = 1.998;
                c.analyze();
                boolean crossedDown = false;
                for (int i = 0; i < 16; i++) {
                    c.advance(1);
                    if (terminal(b.regulator, AbstractRailRegulatorElm.ENABLE_POST) <
                            contract.getEnableHighVolts()) crossedDown = true;
                }
                double fallenEnable = terminal(b.regulator,
                        AbstractRailRegulatorElm.ENABLE_POST);
                double fallenTarget = expectedTarget(contract, terminal(b.regulator,
                        AbstractRailRegulatorElm.INPUT_POST), fallenEnable);
                check(crossedDown && fallenEnable < contract.getEnableHighVolts(),
                        "actual finite-source RC falls across EN high");
                near(fallenTarget, 5.0 * (1.998 - .8) / 1.2, .001,
                        "falling source returns to the independent below-knee target");
                checkRegulatedEquilibrium(b, contract, fallenTarget,
                        "upper-knee falling side");
                c.targets("{\"lowSourceV\":1.998,\"highSourceV\":2.002," +
                        "\"expectedBelowV\":4.991666666666666,\"expectedAboveV\":5.0}");
                c.observed("{\"lowEnableV\":" + number(lowEnable) +
                        ",\"highEnableV\":" + number(highEnable) +
                        ",\"highTargetV\":" + number(highTarget) +
                        ",\"fallEnableV\":" + number(fallenEnable) +
                        ",\"fallTargetV\":" + number(fallenTarget) +
                        ",\"outputV\":" + number(b.regulator.getOutputVoltage()) +
                        ",\"loadA\":" + number(loadCurrent(b)) + "}");
            }
        });
    }

    private static void verifyLowKnee(final boolean averaged,
            final RailRegulationContract contract) {
        runCase(contract.getVariantId() + "_low_knee_off_on", new CaseWork() {
            public void run(CaseState c) {
                final Board b = board(c, averaged, contract, 12.0, .798,
                        10.0, 10.0, 100e-6, 1e-6);
                c.analyze();
                c.advance(2);
                check(terminal(b.regulator, AbstractRailRegulatorElm.ENABLE_POST) <
                        contract.getEnableLowVolts(), "initial EN is below low knee");
                check(Math.abs(b.regulator.getOutputVoltage()) < .001,
                        "initial disabled output is solver-observed near zero");

                b.enableSource.maxVoltage = .802;
                c.analyze();
                c.advance(1);
                double enableOn = terminal(b.regulator,
                        AbstractRailRegulatorElm.ENABLE_POST);
                double inputOn = terminal(b.regulator,
                        AbstractRailRegulatorElm.INPUT_POST);
                double targetOn = expectedTarget(contract, inputOn, enableOn);
                double outputOn = b.regulator.getOutputVoltage();
                check(enableOn > contract.getEnableLowVolts() &&
                        enableOn < contract.getEnableHighVolts(),
                        "finite-source RC moves the solved EN terminal just above low knee");
                check(targetOn > 0 && targetOn < .02,
                        "independent low-knee target is small and positive");
                checkRegulatedEquilibrium(b, contract, targetOn, "low-knee enabled");

                b.enableSource.maxVoltage = .798;
                c.analyze();
                c.advance(5);
                double enableOff = terminal(b.regulator,
                        AbstractRailRegulatorElm.ENABLE_POST);
                double targetOff = expectedTarget(contract,
                        terminal(b.regulator, AbstractRailRegulatorElm.INPUT_POST), enableOff);
                check(enableOff < contract.getEnableLowVolts() && targetOff == 0.0,
                        "finite-source RC returns through low knee to the off state");
                check(Math.abs(b.regulator.getOutputVoltage()) < .001,
                        "actual 30-ohm load and output capacitor discharge the low-knee output");
                c.targets("{\"sourceOffV\":0.798,\"sourceOnV\":0.802}");
                c.observed("{\"enableOnV\":" + number(enableOn) +
                        ",\"targetOnV\":" + number(targetOn) +
                        ",\"outputOnV\":" + number(outputOn) +
                        ",\"enableOffV\":" + number(enableOff) +
                        ",\"targetOffV\":" + number(targetOff) + "}");
            }
        });
    }

    private static void verifyColdStartRamp(final boolean averaged,
            final RailRegulationContract contract) {
        runCase(contract.getVariantId() + "_cold_start_enable_rc", new CaseWork() {
            public void run(CaseState c) {
                final Board b = board(c, averaged, contract, 12.0, 0.0,
                        10.0, 10.0, 100e-6, 100e-6);
                c.analyze();
                c.advance(2);
                check(terminal(b.regulator, AbstractRailRegulatorElm.ENABLE_POST) <
                        contract.getEnableLowVolts(), "cold start begins disabled");
                check(Math.abs(b.regulator.getOutputVoltage()) < .001,
                        "cold-start output begins discharged");

                b.enableSource.maxVoltage = 5.0;
                c.analyze();
                int firstUpperStep = -1;
                double firstUpperEnable = Double.NaN;
                int remainingSteps = MAX_ACCEPTED_ATTEMPTS - c.attempts;
                for (int i = 0; i < remainingSteps; i++) {
                    c.advance(1);
                    double enable = terminal(b.regulator,
                            AbstractRailRegulatorElm.ENABLE_POST);
                    if (firstUpperStep < 0 && enable >= contract.getEnableHighVolts()) {
                        firstUpperStep = c.attempts;
                        firstUpperEnable = enable;
                    }
                }
                check(firstUpperStep > 0 && firstUpperStep < MAX_ACCEPTED_ATTEMPTS,
                        "actual 100-uF enable RC accepts a step crossing EN high");
                check(firstUpperEnable - contract.getEnableHighVolts() < .5,
                        "adjacent accepted RC samples bound the EN knee transition");
                double input = terminal(b.regulator, AbstractRailRegulatorElm.INPUT_POST);
                double enable = terminal(b.regulator, AbstractRailRegulatorElm.ENABLE_POST);
                double target = expectedTarget(contract, input, enable);
                check(target >= contract.getMinimumRegulatedOutputVolts(),
                        "cold-start final solver terminal state is powered and enabled");
                checkRegulatedEquilibrium(b, contract, target, "cold-start ramp steady load");
                check(c.attempts == MAX_ACCEPTED_ATTEMPTS &&
                        c.sim.a01AcceptedStepCount == MAX_ACCEPTED_ATTEMPTS,
                        "cold-start ramp completes exactly 64 actual accepted steps");
                check(c.sim.a01AcceptedStepCount == c.attempts &&
                        Math.abs(c.sim.t - c.attempts * DT) < 1e-10,
                        "cold-start ramp accepted exactly its bounded 50-us steps");
                c.targets("{\"vinSourceV\":12.0,\"enableSourceStartV\":0.0," +
                        "\"enableSourceEndV\":5.0,\"dtSeconds\":0.00005," +
                        "\"maximumAcceptedSteps\":64}");
                c.observed("{\"firstUpperStep\":" + firstUpperStep +
                        ",\"firstUpperEnableV\":" + number(firstUpperEnable) +
                        ",\"finalEnableV\":" + number(enable) +
                        ",\"finalInputV\":" + number(input) +
                        ",\"targetV\":" + number(target) +
                        ",\"outputV\":" + number(b.regulator.getOutputVoltage()) +
                        ",\"loadA\":" + number(loadCurrent(b)) + "}");
            }
        });
    }

    /** A finite 24.4-V Thevenin source has a real 20-ohm series resistor.
     * Newton can visit its >24-V open-load trial, but the active loaded solution
     * is inside the accepted E02 input envelope. */
    private static void verifyFiniteUnacceptedTrial(final boolean averaged,
            final RailRegulationContract contract) {
        runCase(contract.getVariantId() + "_finite_unaccepted_input_trial", new CaseWork() {
            public void run(CaseState c) {
                Board b = board(c, averaged, contract, 24.4, 5.0,
                        20.0, 10.0, 0.0, 0.0);
                c.analyze();
                c.advance(4);
                double input = terminal(b.regulator,
                        AbstractRailRegulatorElm.INPUT_POST);
                double enable = terminal(b.regulator,
                        AbstractRailRegulatorElm.ENABLE_POST);
                double target = expectedTarget(contract, input, enable);
                check(b.inputSource.maxVoltage > contract.getMaximumInputVolts(),
                        "positive canary source exceeds the rail terminal envelope");
                check(b.inputSeries.resistance == 20.0,
                        "positive canary uses an actual finite 20-ohm source resistor");
                check(input <= contract.getMaximumInputVolts() && input >
                        contract.getMinimumInputVolts(),
                        "active finite-source solution converges inside the input envelope");
                check(c.maxSubIterations >= 2,
                        "positive canary traverses multiple actual Newton iterations");
                near(target, 5.0, .01,
                        "positive canary target is derived from solved VIN and EN");
                checkRegulatedEquilibrium(b, contract, target,
                        "finite unaccepted input trial");
                c.targets("{\"sourceV\":24.4,\"sourceResistanceOhms\":20.0," +
                        "\"acceptedInputMaximumV\":24.0}");
                c.observed("{\"inputV\":" + number(input) +
                        ",\"enableV\":" + number(enable) +
                        ",\"newtonSubIterations\":" + c.maxSubIterations +
                        ",\"targetV\":" + number(target) +
                        ",\"outputV\":" + number(b.regulator.getOutputVoltage()) +
                        ",\"loadA\":" + number(loadCurrent(b)) + "}");
            }
        });
    }

    /** A real pulse source holds 12 V for the first accepted step, then holds
     * 25 V for half a second through 10 ohms. The second state is a persistent
     * globally solvable out-of-envelope input; rejection must precede time,
     * accepted-step and observation publication. */
    private static void verifyPersistentInputRejection(final boolean averaged,
            final RailRegulationContract contract) {
        runCase(contract.getVariantId() + "_persistent_vin25_rejected", new CaseWork() {
            public void run(CaseState c) {
                Board b = board(c, averaged, contract, 12.0, 0.0,
                        10.0, 10.0, 0.0, 0.0);
                VoltageElm pulse = new VoltageElm(64, 64, VoltageElm.WF_PULSE);
                pulse.maxVoltage = 13.0;
                pulse.bias = 12.0;
                pulse.frequency = 1.0;
                pulse.dutyCycle = .5;
                pulse.phaseShift = 2.0 * Math.PI - 2.0 * Math.PI * 25e-6;
                pulse.setPosition(64, 64, 64, 128);
                c.replaceInputSource(b, pulse);
                c.analyze();
                SolverTimeObservationService.Subscription sample = c.sim.solverTimeObservations
                        .subscribe(new CircuitPostMeasurementEndpoint(b.regulator,
                                AbstractRailRegulatorElm.OUTPUT_POST),
                                new CircuitPostMeasurementEndpoint(b.regulator,
                                        AbstractRailRegulatorElm.RETURN_POST), 8, true);
                c.subscription = sample;
                c.advance(1);
                check(sample.getSampleCount() == 1,
                        "first valid solver operation publishes one actual output sample");
                double validTime = c.sim.t;
                long validAccepted = c.sim.a01AcceptedStepCount;
                int validTimeStepCount = c.sim.timeStepCount;
                double sourceNow = pulse.getVoltage();
                near(sourceNow, 25.0, 1e-9,
                        "source holds the out-of-envelope voltage after the valid sample");
                Throwable rejection = c.expectRuntimeRejection();
                check(rejection != null, "persistent unsupported VIN is rejected");
                double inputAtRejection = terminal(b.regulator,
                        AbstractRailRegulatorElm.INPUT_POST);
                check(inputAtRejection > contract.getMaximumInputVolts(),
                        "CircuitJS solved the persistent actual VIN above 24 V");
                check(rejection instanceof IllegalArgumentException &&
                        rejection.getMessage() != null &&
                        rejection.getMessage().indexOf(
                                "Rail input exceeds declared absolute envelope:") >= 0,
                        "unsupported VIN is rejected by accepted-state envelope validation");
                check(c.sim.converged && c.sim.subIterations > 0,
                        "unsupported VIN was rejected after a globally converged Newton iteration");
                check(c.sim.t == validTime &&
                        c.sim.a01AcceptedStepCount == validAccepted &&
                        c.sim.timeStepCount == validTimeStepCount,
                        "rejected converged input changes neither time nor accepted step count");
                check(sample.getSampleCount() == 0,
                        "failed operation retires the previous valid measurement sample");
                c.targets("{\"validSourceV\":12.0,\"rejectedSourceV\":25.0," +
                        "\"sourceResistanceOhms\":10.0}");
                c.observed("{\"validTimeS\":" + number(validTime) +
                        ",\"rejectedInputV\":" + number(inputAtRejection) +
                        ",\"subIterations\":" + c.sim.subIterations +
                        ",\"acceptedStepsBeforeAfter\":" + validAccepted +
                        ",\"samplesAfterRejection\":" + sample.getSampleCount() + "}");
            }
        });
    }

    private static void verifyNonfiniteInputRejection(final boolean averaged,
            final RailRegulationContract contract) {
        runCase(contract.getVariantId() + "_nonfinite_input_fail_fast", new CaseWork() {
            public void run(CaseState c) {
                Board b = board(c, averaged, contract, 12.0, 5.0,
                        10.0, 10.0, 0.0, 0.0);
                b.inputSource.maxVoltage = Double.NaN;
                double before = c.sim.t;
                Throwable rejection = null;
                try {
                    c.analyze();
                    c.advance(1);
                } catch (RuntimeException expected) {
                    rejection = expected;
                }
                check(rejection != null,
                        "nonfinite actual source/input fails in the real executor");
                check(c.sim.t == before && c.sim.a01AcceptedStepCount == 0,
                        "nonfinite input fails before any accepted simulation time");
                c.targets("{\"sourceV\":null,\"acceptedStepsMaximum\":0}");
                c.observed("{\"rejectionType\":\"" +
                        rejection.getClass().getSimpleName() + "\",\"timeS\":" +
                        number(c.sim.t) + ",\"acceptedSteps\":" +
                        c.sim.a01AcceptedStepCount + "}");
            }
        });
    }

    private static Board board(CaseState c, boolean averaged,
            RailRegulationContract contract, double inputSourceVolts,
            double enableSourceVolts, double inputResistance,
            double enableResistance, double inputCapacitance,
            double enableCapacitance) {
        DCVoltageElm input = new DCVoltageElm(64, 64);
        input.maxVoltage = inputSourceVolts;
        input.setPosition(64, 64, 64, 128);
        c.add(input);

        ResistorElm inputSeries = new ResistorElm(64, 128);
        inputSeries.setPosition(64, 128, 256, 128);
        inputSeries.setResistance(inputResistance);
        c.add(inputSeries);

        AbstractRailRegulatorElm regulator = averaged ?
                new AveragedSwitchingRegulatorElm(256, 128, contract) :
                new LinearRegulatorElm(256, 128, contract);
        regulator.setPosition(256, 128, 416, 128);
        c.add(regulator);

        DCVoltageElm enable = new DCVoltageElm(512, 0);
        enable.maxVoltage = enableSourceVolts;
        enable.setPosition(512, 0, 512, 64);
        c.add(enable);

        ResistorElm enableSeries = new ResistorElm(512, 64);
        enableSeries.setPosition(512, 64,
                regulator.getPost(AbstractRailRegulatorElm.ENABLE_POST).x,
                regulator.getPost(AbstractRailRegulatorElm.ENABLE_POST).y);
        enableSeries.setResistance(enableResistance);
        c.add(enableSeries);

        ResistorElm load = new ResistorElm(416, 128);
        load.setPosition(416, 128, 480, 128);
        load.setResistance(LOAD_OHMS);
        c.add(load);
        c.wire(load.getPost(1), regulator.getPost(AbstractRailRegulatorElm.RETURN_POST));

        CapacitorElm outputCap = capacitor(c, regulator.getPost(
                AbstractRailRegulatorElm.OUTPUT_POST), regulator.getPost(
                AbstractRailRegulatorElm.RETURN_POST), 1e-6, 0.0);
        CapacitorElm inputCap = inputCapacitance > 0 ? capacitor(c,
                regulator.getPost(AbstractRailRegulatorElm.INPUT_POST),
                regulator.getPost(AbstractRailRegulatorElm.RETURN_POST),
                inputCapacitance, inputSourceVolts) : null;
        CapacitorElm enableCap = enableCapacitance > 0 ? capacitor(c,
                regulator.getPost(AbstractRailRegulatorElm.ENABLE_POST),
                regulator.getPost(AbstractRailRegulatorElm.RETURN_POST),
                enableCapacitance, enableSourceVolts) : null;

        c.add(ground(c, input.getPost(0)));
        c.add(ground(c, enable.getPost(0)));
        c.add(ground(c, regulator.getPost(AbstractRailRegulatorElm.RETURN_POST)));
        return new Board(input, inputSeries, enable, enableSeries, regulator,
                load, inputCap, enableCap, outputCap);
    }

    private static CapacitorElm capacitor(CaseState c, Point positive,
            Point returned, double capacitance, double initialVoltage) {
        CapacitorElm capacitor = new CapacitorElm(positive.x, positive.y);
        capacitor.setPosition(positive.x, positive.y, returned.x, returned.y);
        capacitor.setCapacitance(capacitance);
        capacitor.initialVoltage = initialVoltage;
        capacitor.flags |= CapacitorElm.FLAG_BACK_EULER;
        capacitor.reset();
        c.add(capacitor);
        return capacitor;
    }

    private static GroundElm ground(CaseState c, Point point) {
        GroundElm ground = new GroundElm(point.x, point.y);
        ground.setPosition(point.x, point.y, point.x, point.y + 32);
        return ground;
    }

    private static void checkRegulatedEquilibrium(Board b,
            RailRegulationContract contract, double target, String description) {
        double expectedOutput = target /
                (1.0 + contract.getOutputResistanceOhms() / LOAD_OHMS);
        double expectedCurrent = expectedOutput / LOAD_OHMS;
        double output = b.regulator.getOutputVoltage();
        double measuredLoadCurrent = loadCurrent(b);
        near(output, expectedOutput, .002,
                description + " independent finite-output-resistance voltage");
        near(measuredLoadCurrent, expectedCurrent, .0002,
                description + " actual 30-ohm load current");
        near(b.regulator.getOutputCurrent(), measuredLoadCurrent, .0002,
                description + " solved regulator/load KCL");
        check(expectedCurrent > 0 && expectedCurrent <
                contract.getMaximumOutputCurrentAmps(),
                description + " independent load current stays below rating");
        check(target - expectedOutput < .020,
                description + " independent drop stays below the 20-mV knee");
        check(b.inputSeries.resistance > 0 && b.enableSeries.resistance > 0,
                description + " source impedances are actual resistor elements");
    }

    private static double expectedTarget(RailRegulationContract contract,
            double inputVoltage, double enableVoltage) {
        double enableFraction;
        if (enableVoltage <= contract.getEnableLowVolts()) {
            enableFraction = 0.0;
        } else if (enableVoltage >= contract.getEnableHighVolts()) {
            enableFraction = 1.0;
        } else {
            enableFraction = (enableVoltage - contract.getEnableLowVolts()) /
                    (contract.getEnableHighVolts() - contract.getEnableLowVolts());
        }
        double available = inputVoltage - contract.getDropoutVolts();
        double base = Math.max(0.0, Math.min(contract.getNominalOutputVolts(),
                available));
        return base * enableFraction;
    }

    private static double terminal(AbstractRailRegulatorElm regulator, int post) {
        return regulator.getPostVoltage(post) - regulator.getPostVoltage(
                AbstractRailRegulatorElm.RETURN_POST);
    }

    private static double loadCurrent(Board b) {
        return b.load.getVoltageDiff() / LOAD_OHMS;
    }

    private static void runCase(String id, CaseWork work) {
        CaseState c = new CaseState(id);
        Throwable failure = null;
        try {
            work.run(c);
        } catch (Throwable error) {
            failure = error;
        }
        c.operationElapsedMillis = millisSince(c.startedAt);
        boolean clean = c.cleanup();
        boolean passed = failure == null && clean;
        System.out.println(c.report(passed ? "PASS" : "FAIL", clean));
        if (failure != null)
            throw new AssertionError("E02 knee case failed: " + id, failure);
        if (!clean)
            throw new AssertionError("E02 knee case cleanup failed: " + id);
        completedCases++;
    }

    private static void check(boolean passed, String message) {
        assertions++;
        if (!passed) throw new AssertionError("E02 regulator knee: " + message);
    }

    private static void near(double actual, double expected, double tolerance,
            String message) {
        check(finite(actual) && Math.abs(actual - expected) <= tolerance,
                message + ": observed=" + actual + " expected=" + expected +
                " tolerance=" + tolerance);
    }

    private static boolean finite(double value) {
        return !Double.isNaN(value) && !Double.isInfinite(value);
    }

    private static String number(double value) {
        return finite(value) ? Double.toString(value) : "null";
    }

    private interface CaseWork { void run(CaseState c); }

    private static final class Board {
        final DCVoltageElm inputSource;
        final ResistorElm inputSeries;
        final DCVoltageElm enableSource;
        final ResistorElm enableSeries;
        final AbstractRailRegulatorElm regulator;
        final ResistorElm load;
        final CapacitorElm inputCap, enableCap, outputCap;
        Board(DCVoltageElm inputSource, ResistorElm inputSeries,
                DCVoltageElm enableSource, ResistorElm enableSeries,
                AbstractRailRegulatorElm regulator, ResistorElm load,
                CapacitorElm inputCap, CapacitorElm enableCap,
                CapacitorElm outputCap) {
            this.inputSource = inputSource;
            this.inputSeries = inputSeries;
            this.enableSource = enableSource;
            this.enableSeries = enableSeries;
            this.regulator = regulator;
            this.load = load;
            this.inputCap = inputCap;
            this.enableCap = enableCap;
            this.outputCap = outputCap;
        }
    }

    private static final class CaseState {
        final String id;
        final long startedAt = System.nanoTime();
        final CirSim previousSim = CircuitElm.sim;
        final CirSim previousSingleton = CirSim.theSim;
        final TestCirSim sim = new TestCirSim();
        final Vector<CircuitElm> original;
        final Vector<CircuitElm> graph = new Vector<CircuitElm>();
        final Vector<CircuitElm> owned = new Vector<CircuitElm>();
        String targetJson = "{}", observedJson = "{}";
        SolverTimeObservationService.Subscription subscription;
        int attempts, analyses, maxSubIterations;
        long maxStepMillis, maxAnalysisMillis, operationElapsedMillis,
                cleanupElapsedMillis;
        boolean cleanupComplete;

        CaseState(String id) {
            this.id = id;
            original = sim.elmList;
            sim.elmList = graph;
            CircuitElm.sim = sim;
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
            } finally {
                long elapsed = millisSince(started);
                maxAnalysisMillis = Math.max(maxAnalysisMillis, elapsed);
                analyses++;
                check(elapsed <= MAX_UNIT_MILLIS,
                        "analysis operation stays within the five-second unit budget");
            }
            checkWithinBudget();
        }

        void advance(int count) {
            check(count >= 1 && count <= MAX_ACCEPTED_ATTEMPTS,
                    "advance request is positively bounded");
            for (int i = 0; i < count; i++) {
                checkWithinBudget();
                check(attempts < MAX_ACCEPTED_ATTEMPTS,
                        "case stays within 64 accepted-step attempts");
                attempts++;
                long started = System.nanoTime();
                try {
                    sim.solverExecutor.advanceSteps(1);
                } finally {
                    long elapsed = millisSince(started);
                    maxStepMillis = Math.max(maxStepMillis, elapsed);
                    check(elapsed <= MAX_UNIT_MILLIS,
                            "accepted-step operation stays within the five-second unit budget");
                }
                maxSubIterations = Math.max(maxSubIterations, sim.subIterations);
                checkWithinBudget();
            }
        }

        Throwable expectRuntimeRejection() {
            try {
                advance(1);
                throw new AssertionError("expected the unsupported accepted state to be rejected");
            } catch (AssertionError error) {
                throw error;
            } catch (RuntimeException expected) {
                return expected;
            }
        }

        void replaceInputSource(Board b, VoltageElm source) {
            check(graph.remove(b.inputSource) && owned.remove(b.inputSource),
                    "replace only the source element in the task-owned graph");
            source.setPosition(64, 64, 64, 128);
            graph.add(source);
            owned.add(source);
            b.inputSource.delete();
        }

        void targets(String json) { targetJson = json; }
        void observed(String json) { observedJson = json; }

        private void checkWithinBudget() {
            check(attempts <= MAX_ACCEPTED_ATTEMPTS && workUnits() <= 640,
                    "case stays within 640 solver work units");
            check(millisSince(startedAt) <= MAX_CASE_MILLIS,
                    "case stays within the 90-second wall budget");
        }

        boolean cleanup() {
            long cleanupStarted = System.nanoTime();
            Throwable cleanupError = null;
            boolean disposed = true;
            try {
                if (subscription != null)
                    sim.solverTimeObservations.unsubscribe(subscription);
                sim.a01MeasurementRunning = false;
                sim.elmList = original;
                try {
                    sim.solverExecutor.retire();
                    sim.solverExecutor.invalidate();
                } catch (RuntimeException error) {
                    cleanupError = error;
                } catch (Error error) {
                    cleanupError = error;
                }
                sim.circuitMatrix = null;
                for (CircuitElm element : owned) {
                    try {
                        element.delete();
                    } catch (RuntimeException error) {
                        disposed = false;
                        if (cleanupError == null) cleanupError = error;
                        else cleanupError.addSuppressed(error);
                    } catch (Error error) {
                        disposed = false;
                        if (cleanupError == null) cleanupError = error;
                        else cleanupError.addSuppressed(error);
                    }
                }
                if (disposed) owned.clear();
            } finally {
                CircuitElm.sim = previousSim;
                CirSim.theSim = previousSingleton;
            }
            cleanupComplete = cleanupError == null && disposed &&
                    sim.elmList == original && original.isEmpty() &&
                    sim.circuitMatrix == null && !sim.solverExecutor.isUnavailable() &&
                    CircuitElm.sim == previousSim && CirSim.theSim == previousSingleton;
            if (cleanupError != null)
                observedJson = "{\"cleanupError\":\"" +
                        cleanupError.getClass().getSimpleName() + "\"}";
            if (!disposed && cleanupError == null)
                observedJson = "{\"cleanupError\":\"element-disposal\"}";
            cleanupElapsedMillis = millisSince(cleanupStarted);
            return cleanupComplete;
        }

        String report(String status, boolean clean) {
            return "E02_REGULATOR_KNEE {\"case\":\"" + id + "\",\"status\":\"" + status +
                    "\",\"targets\":" + targetJson + ",\"observed\":" + observedJson +
                    ",\"timing\":{\"elapsedMs\":" + millisSince(startedAt) +
                    ",\"operationElapsedMs\":" + operationElapsedMillis +
                    ",\"cleanupElapsedMs\":" + cleanupElapsedMillis +
                    ",\"analysisCalls\":" + analyses + ",\"acceptedAttempts\":" + attempts +
                    ",\"workUnits\":" + workUnits() + ",\"maxAnalysisMs\":" +
                    maxAnalysisMillis + ",\"maxStepMs\":" + maxStepMillis +
                    ",\"maxSubIterations\":" + maxSubIterations +
                    "},\"cleanup\":{\"clean\":" + clean +
                    ",\"graphRestored\":" + (sim.elmList == original && original.isEmpty()) +
                    ",\"candidateDisposed\":" + owned.isEmpty() +
                    ",\"matrixCleared\":" + (sim.circuitMatrix == null) +
                    ",\"singletonsRestored\":" + (CircuitElm.sim == previousSim &&
                            CirSim.theSim == previousSingleton) + "}}";
        }

        private int workUnits() { return 1 + analyses + attempts; }
    }

    private static long millisSince(long started) {
        return (System.nanoTime() - started) / 1000000L;
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
            maxTimeStep = minTimeStep = timeStep = DT;
            adjustTimeStep = false;
        }

        @Override void needAnalyze() {
            if (elmList != null && CircuitElm.sim == this) solverExecutor.invalidate();
            analyzeFlag = true;
        }
        @Override void stop(String message, CircuitElm element) {
            stopMessage = message;
            circuitMatrix = null;
            stopElm = element;
            setSimRunning(false);
            analyzeFlag = false;
        }
        @Override public void setSimRunning(boolean running) { simRunning = running; }
        @Override void repaint() { }
        @Override void refreshBoardModificationControls() { }
        @Override void refreshChallengeInteractionState() { }
    }
}
