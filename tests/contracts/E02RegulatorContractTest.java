package com.lushprojects.circuitjs1.client;

import java.util.Vector;

/** Independent E02 data-contract and model-boundary expectations. */
public final class E02RegulatorContractTest {
    // 1e-9 S limiter leakage over the <=24 V input envelope contributes at
    // most 2.4e-8 A; leave a small additional solver-rounding margin.
    private static final double CURRENT_LIMIT_SOLVER_TOLERANCE_AMPS = 1e-7;
    private static int assertions;
    private static final StringBuilder observed = new StringBuilder();

    public static void main(String[] args) {
        observed.setLength(0);
        final RailRegulationContract linear12 = RailRegulationContract.linear12V();
        final RailRegulationContract linear5 = RailRegulationContract.linear5V();
        final RailRegulationContract linear33 = RailRegulationContract.linear3V3();
        final RailRegulationContract averaged12 =
                RailRegulationContract.averagedSwitching12V();
        final RailRegulationContract averaged5 = RailRegulationContract.averagedSwitching5V();
        final RailRegulationContract averaged33 = RailRegulationContract.averagedSwitching3V3();
        check(linear12.getNominalOutputVolts() == 12.0);
        check(linear5.getNominalOutputVolts() == 5.0);
        check(linear33.getNominalOutputVolts() == 3.3);
        check(averaged12.getNominalOutputVolts() == 12.0);
        check(averaged5.getNominalOutputVolts() == 5.0);
        check(averaged33.getNominalOutputVolts() == 3.3);
        check(linear5.isLinear() && !linear5.isAveragedSwitching());
        check(averaged5.isAveragedSwitching() && !averaged5.isLinear());
        check(linear5.getTerminalIds().size() == 4);
        check(linear5.getInputTerminalId().equals("INPUT"));
        check(linear5.getOutputTerminalId().equals("OUTPUT"));
        check(linear5.getReturnTerminalId().equals("RETURN"));
        check(linear5.getEnableTerminalId().equals("ENABLE"));
        check(!averaged5.supportsSwitchingWaveform());
        check(!averaged5.supportsFrequencyMeasurement());
        check(!averaged5.hasSwitchingWaveform());
        check(linear5.getMinimumInputVolts() >=
                linear5.getNominalOutputVolts() + linear5.getDropoutVolts());
        check(averaged5.getEfficiency() < 1.0);
        check(linear5.getVersion() == 2 && averaged5.getVersion() == 2);
        check(linear5.getUsableRegulatedCurrentFraction() > 0.0 &&
                linear5.getUsableRegulatedCurrentFraction() < 1.0);
        check(linear5.getUsableRegulatedCurrentAmps() <
                linear5.getMaximumOutputCurrentAmps());
        check(linear5.getRegulatedVoltageToleranceVolts() > 0.0 &&
                linear5.getMinimumRegulatedOutputVolts() > 0.0);
        check(averaged5.getUsableRegulatedCurrentFraction() ==
                linear5.getUsableRegulatedCurrentFraction());
        verifyPhysicalPackage();

        reject(new Runnable() { public void run() {
            new RailRegulationContract("bad", "INPUT", "INPUT", "RETURN", "ENABLE",
                    5, 5.8, 24, .8, .2, .1, .8, 2, .001, 1, false, false, .90, .25);
        }});
        reject(new Runnable() { public void run() {
            new RailRegulationContract("bad", "INPUT", "OUTPUT", "RETURN", "ENABLE",
                    5, 5.8, 24, .8, .2, .1, 2, .8, .001, 1, false, false, .90, .25);
        }});
        reject(new Runnable() { public void run() {
            new RailRegulationContract("bad", "INPUT", "OUTPUT", "RETURN", "ENABLE",
                    5, 5.8, 24, .8, .2, .1, .8, 2, .001, 1, true, true, .90, .25);
        }});
        reject(new Runnable() { public void run() {
            new RailRegulationContract("bad", "INPUT", "OUTPUT", "RETURN", "ENABLE",
                    5, 5.8, 5.5, .8, .2, .1, .8, 2, .001, 1, false, false, .90, .25);
        }});
        reject(new Runnable() { public void run() {
            new RailRegulationContract("bad", "INPUT", "OUTPUT", "RETURN", "ENABLE",
                    5, 5.8, 24, .8, .2, .1, .8, 2, .001, 1.01, false, false, .90, .25);
        }});
        reject(new Runnable() { public void run() {
            new RailRegulationContract("bad", "INPUT", "OUTPUT", "RETURN", "ENABLE",
                    5, 5.8, 24, .8, .2, .1, .8, 2, .001, 1, false, false,
                    1.01, .25);
        }});
        reject(new Runnable() { public void run() {
            new RailRegulationContract("bad", "INPUT", "OUTPUT", "RETURN", "ENABLE",
                    5, 5.8, 24, .8, .2, .1, .8, 2, .001, 1, false, false,
                    1.0, .25);
        }});
        reject(new Runnable() { public void run() {
            new RailRegulationContract("bad", "INPUT", "OUTPUT", "RETURN", "ENABLE",
                    5, 5.8, 24, .8, .2, .1, .8, 2, .001, 1, false, false,
                    .90, 5.0);
        }});
        reject(new Runnable() { public void run() {
            new RailRegulationContract("impossible-regulation", "INPUT", "OUTPUT",
                    "RETURN", "ENABLE", 5, 5.8, 24, .8, .2, .1,
                    .8, 2, .001, 1, false, false, .90, .001);
        }});

        reject(new Runnable() { public void run() {
            new LinearRegulatorElm(0, 0, averaged5);
        }});
        reject(new Runnable() { public void run() {
            new AveragedSwitchingRegulatorElm(0, 0, linear5);
        }});
        check(new LinearRegulatorElm(0, 0).getContract().isLinear());
        check(new AveragedSwitchingRegulatorElm(0, 0).getContract().isAveragedSwitching());
        check(new LinearRegulatorElm(0, 0).getPostCount() == 4);
        check(new AveragedSwitchingRegulatorElm(0, 0).getPostCount() == 4);
        verifyDumpRoundTrip();
        verifyUnsupportedOperatingRegions();
        verifyReturnReference();
        verifyRealSolverVariants();
        verifyTimestepSensitivity();
        System.out.println("E02_NATIVE_REPORT {\"schema\":\"e02-native-regulator-v2\",\"cases\":[" +
                observed + "]}");
        System.out.println("PASS: E02 regulator contracts " + assertions + " assertions");
    }

    private static void verifyDumpRoundTrip() {
        final LinearRegulatorElm linear = new LinearRegulatorElm(64, 64);
        linear.x2 = 192;
        linear.y2 = 64;
        linear.setPoints();
        String linearDump = linear.dump();
        CircuitElm restoredLinear = restoreDump(linearDump);
        check(restoredLinear instanceof LinearRegulatorElm,
                "454 dump restores a linear regulator");
        check(linearDump.equals(restoredLinear.dump()),
                "454 dump retains the complete rail contract");

        final RailRegulationContract explicitEnvelope =
                new RailRegulationContract("linear-custom-envelope", "INPUT",
                        "OUTPUT", "RETURN", "ENABLE", 5.0, 5.8, 24.0,
                        .8, .20, .1, .8, 2.0, .001, 1.0, false, false,
                        .72, .125);
        final LinearRegulatorElm explicitLinear =
                new LinearRegulatorElm(80, 64, explicitEnvelope);
        explicitLinear.x2 = 208;
        explicitLinear.y2 = 64;
        explicitLinear.setPoints();
        CircuitElm restoredExplicit = restoreDump(explicitLinear.dump());
        RailRegulationContract restoredExplicitContract =
                ((LinearRegulatorElm) restoredExplicit).getContract();
        check(restoredExplicitContract.getUsableRegulatedCurrentFraction() == .72 &&
                restoredExplicitContract.getRegulatedVoltageToleranceVolts() == .125,
                "454 dump/parser retains explicit usable regulation envelope");

        final AveragedSwitchingRegulatorElm averaged =
                new AveragedSwitchingRegulatorElm(96, 96);
        averaged.x2 = 224;
        averaged.y2 = 96;
        averaged.setPoints();
        String averagedDump = averaged.dump();
        CircuitElm restoredAveraged = restoreDump(averagedDump);
        check(restoredAveraged instanceof AveragedSwitchingRegulatorElm,
                "455 dump restores an averaged regulator");
        check(averagedDump.equals(restoredAveraged.dump()),
                "455 dump retains the complete rail contract");

        final E02FiniteSourceElm source = new E02FiniteSourceElm(128, 128,
                3.75, 12.5);
        source.x2 = 256;
        source.y2 = 128;
        source.setPoints();
        String sourceDump = source.dump();
        CircuitElm restoredSource = restoreDump(sourceDump);
        check(restoredSource instanceof E02FiniteSourceElm,
                "456 dump restores an E02 finite source");
        check(sourceDump.equals(restoredSource.dump()),
                "456 dump retains source voltage and resistance");
        E02FiniteSourceElm loadedSource = (E02FiniteSourceElm) restoredSource;
        check(loadedSource.getSourceVoltage() == 3.75 &&
                loadedSource.getResistance() == 12.5,
                "456 load restores source model parameters");
    }

    private static CircuitElm restoreDump(String dump) {
        StringTokenizer st = new StringTokenizer(dump);
        int type = Integer.parseInt(st.nextToken());
        int x1 = Integer.parseInt(st.nextToken());
        int y1 = Integer.parseInt(st.nextToken());
        int x2 = Integer.parseInt(st.nextToken());
        int y2 = Integer.parseInt(st.nextToken());
        int flags = Integer.parseInt(st.nextToken());
        CircuitElm restored = CirSim.createCe(type, x1, y1, x2, y2, flags, st);
        check(restored != null, "E02 dump type is registered");
        restored.setPoints();
        return restored;
    }

    private static void verifyUnsupportedOperatingRegions() {
        reject(new Runnable() { public void run() {
            new E02FiniteSourceElm(0, 0, -1.0, 1.0);
        }});
        final E02FiniteSourceElm source = new E02FiniteSourceElm(0, 0, 1.0, 1.0);
        reject(new Runnable() { public void run() {
            source.setVoltage(-1.0);
        }});

        final RailRegulationContract contract = RailRegulationContract.linear5V();
        Harness passiveHarness = new Harness(false, contract);
        try {
            passiveHarness.fixture.input.setVoltage(0.0);
            passiveHarness.fixture.enable.setVoltage(0.0);
            E02FiniteSourceElm passiveProbe = connectOutputSource(passiveHarness,
                    1.0, ResistanceMeasurementStimulus.INTERNAL_RESISTANCE);
            passiveHarness.analyzeAndSettle();
            double passiveOutput = passiveHarness.fixture.regulator.getOutputVoltage();
            double passiveRegulatorCurrent =
                passiveHarness.fixture.regulator.getOutputCurrent();
            double passiveProbeCurrent = passiveProbe.getCurrent();
            check(passiveOutput > .1 && passiveOutput <
                        contract.getNominalOutputVolts() &&
                    Math.abs(passiveRegulatorCurrent) < 2e-6 &&
                    Math.abs(passiveProbeCurrent) < .002,
                "powered-off output admits a bounded passive meter stimulus without driving it: " +
                    "output=" + passiveOutput + " regulatorCurrent=" +
                    passiveRegulatorCurrent + " probeCurrent=" + passiveProbeCurrent);
            check(passiveHarness.fixture.regulator.getInputPowerWatts() >= -1e-9 &&
                    passiveHarness.fixture.regulator.getPowerLossWatts() >= -1e-9,
                "passive output backfeed creates no regulator input energy");
        } finally {
            passiveHarness.close();
        }

        Harness topologyHarness = new Harness(false, contract);
        try {
            topologyHarness.fixture.input.setVoltage(0.0);
            topologyHarness.fixture.enable.setVoltage(0.0);
            topologyHarness.analyzeAndSettle();
            AbstractRailRegulatorElm regulator = topologyHarness.fixture.regulator;
            regulator.volts[AbstractRailRegulatorElm.OUTPUT_POST] =
                    regulator.volts[AbstractRailRegulatorElm.RETURN_POST] - 1e12;
            // A physical graph rebuild can expose one stale node-voltage trial
            // before nonlinear elements restamp.  It must be bounded as a
            // passive Newton trial, not converted into an unpowered 200 mA
            // source or mistaken for an accepted operating point.
            regulator.doStep();
            topologyHarness.analyzeAndSettle();
            check(Math.abs(regulator.getOutputVoltage()) < .01 &&
                    Math.abs(regulator.getOutputCurrent()) < 1e-6,
                "powered-off regulator recovers from a negative stale topology trial");
            regulator.volts[AbstractRailRegulatorElm.OUTPUT_POST] =
                    regulator.volts[AbstractRailRegulatorElm.RETURN_POST] + 1e12;
            regulator.doStep();
            topologyHarness.analyzeAndSettle();
            check(Math.abs(regulator.getOutputVoltage()) < .01 &&
                    Math.abs(regulator.getOutputCurrent()) < 1e-6,
                "powered-off regulator recovers from a positive stale topology trial");
        } finally {
            topologyHarness.close();
        }

        Harness harness = new Harness(false, contract);
        boolean backfeedRejected = false;
        try {
            connectOutputSource(harness,
                contract.getMaximumInputVolts() + 6.0, .1);
            harness.sim.analyzeCircuit();
            harness.sim.solverExecutor.advanceSteps(8);
        } catch (IllegalArgumentException expected) {
            backfeedRejected = true;
        } finally {
            harness.close();
        }
        check(backfeedRejected,
                "output backfeed above the declared absolute device envelope is rejected");
    }

    private static E02FiniteSourceElm connectOutputSource(Harness harness,
            double voltage, double resistance) {
        E02FiniteSourceElm source = new E02FiniteSourceElm(640, 192,
                voltage, resistance);
        source.drag(640, 64);
        harness.fixture.elements.add(source);
        WireElm output = new WireElm(source.getPost(1).x, source.getPost(1).y);
        output.setPosition(source.getPost(1).x, source.getPost(1).y,
                harness.fixture.regulator.getPost(
                        AbstractRailRegulatorElm.OUTPUT_POST).x,
                harness.fixture.regulator.getPost(
                        AbstractRailRegulatorElm.OUTPUT_POST).y);
        harness.fixture.elements.add(output);
        WireElm returned = new WireElm(source.getPost(0).x, source.getPost(0).y);
        returned.setPosition(source.getPost(0).x, source.getPost(0).y,
                harness.fixture.regulator.getPost(
                        AbstractRailRegulatorElm.RETURN_POST).x,
                harness.fixture.regulator.getPost(
                        AbstractRailRegulatorElm.RETURN_POST).y);
        harness.fixture.elements.add(returned);
        return source;
    }

    private static void verifyPhysicalPackage() {
        PhysicalPackage physical = PhysicalPackages.TO220_REGULATOR_4;
        check(!physical.isDeveloperGeneric(), "regulator package is not generic geometry");
        check("TO220_REGULATOR_4".equals(physical.getId()),
                "regulator package has a stable physical identity");
        check(physical.getTerminalIds().size() == 4 &&
                physical.getTerminalIds().get(0).equals("INPUT") &&
                physical.getTerminalIds().get(1).equals("OUTPUT") &&
                physical.getTerminalIds().get(2).equals("RETURN") &&
                physical.getTerminalIds().get(3).equals("ENABLE"),
                "regulator package exposes the four declared rail terminals");
        check(physical.getGeometryVariants().size() == 1 &&
                "DEFAULT".equals(physical.getDefaultLooseGeometryVariantKey()),
                "regulator package geometry is deterministic");
        Vector<PhysicalPackageGeometry.Terminal> terminals = physical.getGeometry().getTerminals();
        check(terminals.size() == 4, "regulator package has four physical terminal geometries");
        for (int index = 0; index < terminals.size(); index++) {
            PhysicalPackageGeometry.Terminal terminal = terminals.get(index);
            check(terminal.getPadCenter().equals(new Point(50 + index * 40, 150)),
                    "regulator pad geometry is deterministic");
            check(terminal.getAttachment() == PcbTerminalAttachment.PLATED_THROUGH_HOLE,
                    "regulator pads are plated through holes");
            check(terminal.getEscapeDx() == 0 && terminal.getEscapeDy() == 1 &&
                    terminal.getEscapeLength() == 50,
                    "regulator pads have declared outward escapes");
        }
        check(!physical.isInternallyConnected("INPUT", "RETURN") &&
                !physical.isInternallyConnected("OUTPUT", "RETURN"),
                "regulator package does not hide rail joins");
        verifyPhysicalMapping(false);
        verifyPhysicalMapping(true);
    }

    private static void verifyPhysicalMapping(boolean averaged) {
        RailRegulationContract contract = averaged ?
            RailRegulationContract.averagedSwitching5V() : RailRegulationContract.linear5V();
        final Harness harness = new Harness(averaged, contract);
        try {
            TroubleshootBoard board = regulatorBoard("U1");
            RegulatorPhysicalMapping mapping = RegulatorPhysicalMapping.bind(
                board, "U1", harness.fixture.regulator);
            String[] terminals = { "INPUT", "OUTPUT", "RETURN", "ENABLE" };
            int[] posts = { AbstractRailRegulatorElm.INPUT_POST,
                AbstractRailRegulatorElm.OUTPUT_POST,
                AbstractRailRegulatorElm.RETURN_POST,
                AbstractRailRegulatorElm.ENABLE_POST };
            check(mapping.getTerminalCount() == terminals.length,
                    "physical regulator mapping covers every rail terminal");
            for (int index = 0; index < terminals.length; index++) {
                String terminal = terminals[index];
                String padId = "U1." + terminal;
                CircuitPostMeasurementEndpoint endpoint = mapping.getEndpoint(terminal);
                check(padId.equals(mapping.getPadId(terminal)) &&
                        mapping.getPostIndex(terminal).intValue() == posts[index],
                        "regulator terminal maps to its declared CircuitJS post");
                check(endpoint == board.getSimulationBindings().getEndpoint(padId) &&
                        endpoint.getElement() == harness.fixture.regulator &&
                        endpoint.getPostIndex() == posts[index] &&
                        endpoint.getElement().getPost(endpoint.getPostIndex()) != null,
                        "regulator terminal maps to the live board simulation endpoint");
            }
            mapping.verifyCurrentBindings();

            final TroubleshootBoard wrongPost = regulatorBoard("U1");
            wrongPost.getSimulationBindings().bindPad("U1.INPUT",
                new CircuitPostMeasurementEndpoint(harness.fixture.regulator,
                    AbstractRailRegulatorElm.OUTPUT_POST));
            rejectMapping(new Runnable() { public void run() {
                RegulatorPhysicalMapping.bind(wrongPost, "U1", harness.fixture.regulator);
            }});

            final TroubleshootBoard wrongKind = regulatorBoard("U1");
            wrongKind.getSimulationBindings().bindPad("U1.INPUT",
                new CircuitMeasurementEndpoint() { });
            rejectMapping(new Runnable() { public void run() {
                RegulatorPhysicalMapping.bind(wrongKind, "U1", harness.fixture.regulator);
            }});
        } finally {
            harness.close();
        }
    }

    private static TroubleshootBoard regulatorBoard(String componentId) {
        TroubleshootBoard board = new TroubleshootBoard("E02_REGULATOR_MAPPING");
        board.addNet(new BoardNet("INPUT_NET"));
        board.addNet(new BoardNet("OUTPUT_NET"));
        board.addNet(new BoardNet("RETURN_NET"));
        board.addNet(new BoardNet("ENABLE_NET"));
        board.addComponent(new BoardComponent(componentId, "REGULATOR",
                PhysicalPackages.TO220_REGULATOR_4));
        board.addPad(new BoardPad(componentId + ".INPUT", componentId, "INPUT", "INPUT_NET"));
        board.addPad(new BoardPad(componentId + ".OUTPUT", componentId, "OUTPUT", "OUTPUT_NET"));
        board.addPad(new BoardPad(componentId + ".RETURN", componentId, "RETURN", "RETURN_NET"));
        board.addPad(new BoardPad(componentId + ".ENABLE", componentId, "ENABLE", "ENABLE_NET"));
        board.validate();
        return board;
    }

    private static void verifyRealSolverVariants() {
        verifyRealSolverVariant(false, RailRegulationContract.linear12V());
        verifyRealSolverVariant(false, RailRegulationContract.linear5V());
        verifyRealSolverVariant(false, RailRegulationContract.linear3V3());
        verifyRealSolverVariant(true, RailRegulationContract.averagedSwitching12V());
        verifyRealSolverVariant(true, RailRegulationContract.averagedSwitching5V());
        verifyRealSolverVariant(true, RailRegulationContract.averagedSwitching3V3());
    }

    private static void verifyReturnReference() {
        verifyReturnReference(false, RailRegulationContract.linear12V());
        verifyReturnReference(false, RailRegulationContract.linear5V());
        verifyReturnReference(false, RailRegulationContract.linear3V3());
        verifyReturnReference(true, RailRegulationContract.averagedSwitching12V());
        verifyReturnReference(true, RailRegulationContract.averagedSwitching5V());
        verifyReturnReference(true, RailRegulationContract.averagedSwitching3V3());
    }

    private static void verifyReturnReference(boolean averaged,
            RailRegulationContract contract) {
        double groundedInput;
        double groundedOutput;
        double groundedEnable;
        Harness grounded = new Harness(averaged, contract);
        try {
            groundedInput = grounded.fixture.regulator.getInputVoltage();
            groundedOutput = grounded.fixture.regulator.getOutputVoltage();
            groundedEnable = grounded.fixture.regulator.getEnableVoltage();
        } finally {
            grounded.close();
        }

        Harness shifted = new Harness(averaged, contract,
                1e-4, 1.25);
        try {
            near(shifted.fixture.regulator.getInputVoltage(), groundedInput,
                    .002, contract.getVariantId() +
                    " input voltage follows its RETURN reference");
            near(shifted.fixture.regulator.getOutputVoltage(), groundedOutput,
                    .002, contract.getVariantId() +
                    " output voltage follows its RETURN reference");
            near(shifted.fixture.regulator.getEnableVoltage(), groundedEnable,
                    .002, contract.getVariantId() +
                    " enable voltage follows its RETURN reference");
        } finally {
            shifted.close();
        }
    }

    private static void verifyTimestepSensitivity() {
        verifyTimestepSensitivity(false, RailRegulationContract.linear12V());
        verifyTimestepSensitivity(false, RailRegulationContract.linear5V());
        verifyTimestepSensitivity(false, RailRegulationContract.linear3V3());
        verifyTimestepSensitivity(true,
                RailRegulationContract.averagedSwitching12V());
        verifyTimestepSensitivity(true,
                RailRegulationContract.averagedSwitching5V());
        verifyTimestepSensitivity(true,
                RailRegulationContract.averagedSwitching3V3());
    }

    private static void verifyTimestepSensitivity(boolean averaged,
            RailRegulationContract contract) {
        TransientReading coarse = solveLoadTransient(averaged, contract, 1e-4);
        TransientReading fine = solveLoadTransient(averaged, contract, 2.5e-5);
        check(coarse.outputVolts < coarse.startingVolts - .05 &&
                fine.outputVolts < fine.startingVolts - .05 &&
                coarse.outputVolts > coarse.limitEquilibriumVolts + .05 &&
                fine.outputVolts > fine.limitEquilibriumVolts + .05,
                contract.getVariantId() +
                " timestep proof samples a live capacitive load-step transient: coarse=" +
                coarse.outputVolts + " fine=" + fine.outputVolts);
        check(coarse.outputAmps > 0.0 && fine.outputAmps > 0.0 &&
                coarse.outputAmps <= contract.getMaximumOutputCurrentAmps() +
                    CURRENT_LIMIT_SOLVER_TOLERANCE_AMPS &&
                fine.outputAmps <= contract.getMaximumOutputCurrentAmps() +
                    CURRENT_LIMIT_SOLVER_TOLERANCE_AMPS,
                contract.getVariantId() +
                " transient charge current remains bounded by the solver limit");
        near(fine.outputVolts, coarse.outputVolts, .03,
                contract.getVariantId() +
                " capacitive startup is stable across fourfold timestep change");
        near(coarse.elapsedSeconds, .002, 1e-7,
                contract.getVariantId() + " coarse transient uses equal physical time");
        near(fine.elapsedSeconds, .002, 1e-7,
                contract.getVariantId() + " fine transient uses equal physical time");
    }

    private static TransientReading solveLoadTransient(boolean averaged,
            RailRegulationContract contract, double maxTimeStep) {
        Harness harness = new Harness(averaged, contract, maxTimeStep);
        try {
            Point output = harness.fixture.regulator.getPost(
                    AbstractRailRegulatorElm.OUTPUT_POST);
            Point returned = harness.fixture.regulator.getPost(
                    AbstractRailRegulatorElm.RETURN_POST);
            CapacitorElm capacitor = new CapacitorElm(output.x, output.y);
            capacitor.setPosition(output.x, output.y, returned.x, returned.y);
            capacitor.setCapacitance(.001);
            capacitor.initialVoltage = harness.fixture.regulator.getOutputVoltage();
            capacitor.flags |= CapacitorElm.FLAG_BACK_EULER;
            capacitor.reset();
            harness.fixture.elements.add(capacitor);
            harness.fixture.load.setResistance(1000.0);
            harness.sim.minTimeStep = maxTimeStep;
            harness.sim.adjustTimeStep = false;
            harness.sim.analyzeCircuit();
            harness.sim.solverExecutor.advanceFor(.001);

            double startingVolts = harness.fixture.regulator.getOutputVoltage();
            double overloadResistance = 10.0;
            harness.fixture.load.setResistance(overloadResistance);
            harness.sim.analyzeCircuit();
            double startedAt = harness.sim.t;
            harness.sim.solverExecutor.advanceFor(.002);
            TransientReading reading = new TransientReading();
            reading.elapsedSeconds = harness.sim.t - startedAt;
            reading.startingVolts = startingVolts;
            reading.limitEquilibriumVolts =
                    contract.getMaximumOutputCurrentAmps() * overloadResistance;
            reading.outputVolts = harness.fixture.regulator.getOutputVoltage();
            reading.outputAmps = harness.fixture.regulator.getOutputCurrent();
            return reading;
        } finally {
            harness.close();
        }
    }

    private static void verifyRealSolverVariant(boolean averaged,
            RailRegulationContract contract) {
        Harness harness = new Harness(averaged, contract);
        try {
            AbstractRailRegulatorElm regulator = harness.fixture.regulator;
            ResistorElm load = harness.fixture.load;
            check(finite(regulator.getInputVoltage()) &&
                    finite(regulator.getOutputVoltage()),
                    "real solver produced finite nominal rail values");
            double settledOutput = regulator.getOutputVoltage();
            double settledCurrent = regulator.getOutputCurrent();
            harness.sim.solverExecutor.advanceSteps(8);
            near(regulator.getOutputVoltage(), settledOutput, .002,
                    contract.getVariantId() + " nominal output settles");
            check(regulator.getOutputVoltage() >
                    contract.getMinimumRegulatedOutputVolts(),
                    "real solver follows the declared nominal output");
            check(regulator.getOutputCurrent() > 0.0 &&
                    regulator.getOutputCurrent() <
                    contract.getMaximumOutputCurrentAmps(),
                    "real solver light load remains below current limit");
            verifyIndependentPower(harness, contract, "light load");
            check(regulator.getInputLeakCurrent() > 0.0 &&
                    regulator.getEnableLeakCurrent() > 0.0,
                    "input and enable leakage remain explicit solver branches");

            SweepReadings sweep = verifyIntermediateSweeps(harness, contract);

            load.setResistance(.1);
            harness.analyzeAndSettle();
            double overloadOutput = regulator.getOutputVoltage();
            double overloadCurrent = regulator.getOutputCurrent();
            check(regulator.getOutputVoltage() < .5 &&
                    overloadCurrent > 0.0 && overloadCurrent <=
                    contract.getMaximumOutputCurrentAmps() + CURRENT_LIMIT_SOLVER_TOLERANCE_AMPS,
                    "real solver reflects a finite short/overload limit");
            check(finite(overloadOutput) && overloadOutput >= -.01 &&
                    overloadOutput < settledOutput - .1,
                    contract.getVariantId() +
                    " overload is a distinct bounded diagnostic state");
            verifyIndependentPower(harness, contract, "hard overload");
            appendObserved(contract, settledOutput, settledCurrent,
                    sweep, overloadOutput, overloadCurrent);

            load.setResistance(1000.0);
            harness.fixture.enable.setVoltage(0.0);
            harness.analyzeAndSettle();
            double disabledOutput = regulator.getOutputVoltage();
            check(regulator.getOutputVoltage() < .1 &&
                    regulator.getInputCurrent() > 0.0 &&
                    regulator.getInputCurrent() < 5e-5,
                    "real solver reports bounded disabled-input leakage");
            check(finite(disabledOutput) && disabledOutput >= -.01 &&
                    disabledOutput < settledOutput - .1,
                    contract.getVariantId() +
                    " disabled state is distinct from enabled healthy output");
            verifyIndependentPower(harness, contract, "disabled");

            harness.fixture.enable.setVoltage(5.0);
            double dropoutInput = contract.getNominalOutputVolts() +
                    contract.getDropoutVolts() * .5;
            harness.fixture.input.setVoltage(dropoutInput);
            harness.analyzeAndSettle();
            check(regulator.isInDropout() && regulator.getOutputVoltage() <
                    contract.getNominalOutputVolts() - .1,
                    "real solver reflects input dropout");
            verifyIndependentPower(harness, contract, "dropout");

            harness.fixture.input.setVoltage(harness.fixture.nominalInputVoltage);
            harness.fixture.input.setVoltage(0.0);
            harness.analyzeAndSettle();
            double inputLossOutput = regulator.getOutputVoltage();
            check(regulator.getInputVoltage() < .1 &&
                    regulator.getOutputVoltage() < .1,
                    "real solver reflects input loss without an ideal output source");
            check(finite(inputLossOutput) && inputLossOutput >= -.01 &&
                    inputLossOutput < settledOutput - .1,
                    contract.getVariantId() +
                    " input loss is a distinct bounded diagnostic state");
            verifyIndependentPower(harness, contract, "source loss");
            harness.fixture.input.setVoltage(harness.fixture.nominalInputVoltage);
            harness.analyzeAndSettle();
            double restoredOutput = regulator.getOutputVoltage();
            check(regulator.getOutputVoltage() >
                    contract.getMinimumRegulatedOutputVolts(),
                    "real solver recovers after input restoration");
            check(restoredOutput > disabledOutput + .1 &&
                    restoredOutput > inputLossOutput + .1,
                    contract.getVariantId() +
                    " restoration returns to the healthy diagnostic state");
            verifyIndependentPower(harness, contract, "restored input");
        } finally {
            harness.close();
        }
    }

    /**
     * Every declared rail variant gets the same real-solver intermediate
     * points.  Mutations are followed by a fresh CircuitJS analysis; no
     * expected reading is synthesized outside the solver.
     */
    private static SweepReadings verifyIntermediateSweeps(Harness harness,
            RailRegulationContract contract) {
        AbstractRailRegulatorElm regulator = harness.fixture.regulator;
        SweepReadings readings = new SweepReadings();
        readings.loadEnvelope = new LoadReading[4];
        readings.preLimitEnvelope = new LoadReading[2];
        harness.fixture.input.setVoltage(harness.fixture.nominalInputVoltage);
        harness.fixture.load.setResistance(1000.0);

        harness.fixture.enable.setVoltage(contract.getEnableLowVolts());
        harness.analyzeAndSettle();
        double enableLowOutput = regulator.getOutputVoltage();
        verifyIndependentPower(harness, contract, "enable low");
        harness.fixture.enable.setVoltage((contract.getEnableLowVolts() +
                contract.getEnableHighVolts()) * .5);
        harness.analyzeAndSettle();
        double enableMidOutput = regulator.getOutputVoltage();
        verifyIndependentPower(harness, contract, "enable mid");
        harness.fixture.enable.setVoltage(contract.getEnableHighVolts());
        harness.analyzeAndSettle();
        double enableHighOutput = regulator.getOutputVoltage();
        verifyIndependentPower(harness, contract, "enable high");
        check(finite(enableLowOutput) && finite(enableMidOutput) &&
                finite(enableHighOutput), contract.getVariantId() +
                " enable sweep is finite");
        check(enableLowOutput < .1 &&
                enableMidOutput > enableLowOutput + .05 &&
                enableHighOutput > enableMidOutput + .05 &&
                enableHighOutput <= contract.getNominalOutputVolts() * 1.01,
                contract.getVariantId() +
                " enable low/mid/high is monotonic and bounded");

        harness.fixture.enable.setVoltage(5.0);
        double dropoutInput = contract.getNominalOutputVolts() +
                contract.getDropoutVolts() * .5;
        harness.fixture.input.setVoltage(dropoutInput);
        harness.analyzeAndSettle();
        double dropoutOutput = regulator.getOutputVoltage();
        verifyIndependentPower(harness, contract, "sweep dropout");
        boolean dropout = regulator.isInDropout();
        double headroomInput = contract.getMinimumInputVolts() + .5;
        harness.fixture.input.setVoltage(headroomInput);
        harness.analyzeAndSettle();
        double headroomOutput = regulator.getOutputVoltage();
        verifyIndependentPower(harness, contract, "sweep headroom");
        check(dropout && finite(dropoutOutput) && dropoutOutput >= -.01 &&
                dropoutOutput < contract.getNominalOutputVolts() - .1,
                contract.getVariantId() +
                " input dropout is observable and bounded");
        check(finite(headroomOutput) && headroomOutput > dropoutOutput + .05 &&
                headroomOutput <= contract.getNominalOutputVolts() * 1.01,
                contract.getVariantId() +
                " input headroom recovers monotonically");

        harness.fixture.input.setVoltage(harness.fixture.nominalInputVoltage);
        double[] fractions = { .25, .50, .75, .90 };
        double previousCurrent = -1.0;
        double previousOutput = Double.POSITIVE_INFINITY;
        for (int index = 0; index < fractions.length; index++) {
            double fraction = fractions[index];
            double demandFraction = fraction *
                    contract.getUsableRegulatedCurrentFraction();
            double resistance = contract.getNominalOutputVolts() /
                    (contract.getMaximumOutputCurrentAmps() * demandFraction) -
                    contract.getOutputResistanceOhms();
            harness.fixture.load.setResistance(resistance);
            harness.analyzeAndSettle();
            LoadReading reading = new LoadReading();
            reading.fraction = fraction;
            reading.demandFraction = demandFraction;
            reading.resistance = resistance;
            reading.output = regulator.getOutputVoltage();
            reading.current = regulator.getOutputCurrent();
            readings.loadEnvelope[index] = reading;
            check(finite(reading.output) && finite(reading.current),
                    contract.getVariantId() + " load envelope point is finite");
            check(reading.current > previousCurrent &&
                    reading.current <= contract.getMaximumOutputCurrentAmps() +
                        CURRENT_LIMIT_SOLVER_TOLERANCE_AMPS &&
                    reading.output <= previousOutput &&
                    reading.output >= contract.getMinimumRegulatedOutputVolts(),
                    contract.getVariantId() +
                    " 25/50/75/90% loads regulate before the limit");
            check(!regulator.isCurrentLimited(), contract.getVariantId() +
                    " remains out of current limit through 90% load");
            verifyIndependentPower(harness, contract,
                    "usable-envelope load " + fraction);
            previousCurrent = reading.current;
            previousOutput = reading.output;
        }

        double[] preLimitFractions = { .95, .99 };
        for (int index = 0; index < preLimitFractions.length; index++) {
            double demandFraction = preLimitFractions[index];
            double resistance = contract.getNominalOutputVolts() /
                    (contract.getMaximumOutputCurrentAmps() * demandFraction) -
                    contract.getOutputResistanceOhms();
            harness.fixture.load.setResistance(resistance);
            harness.analyzeAndSettle();
            LoadReading reading = new LoadReading();
            reading.fraction = demandFraction;
            reading.demandFraction = demandFraction;
            reading.resistance = resistance;
            reading.output = regulator.getOutputVoltage();
            reading.current = regulator.getOutputCurrent();
            readings.preLimitEnvelope[index] = reading;
            check(finite(reading.output) && finite(reading.current) &&
                    reading.current > previousCurrent &&
                    reading.current < contract.getMaximumOutputCurrentAmps() &&
                    reading.output <= previousOutput &&
                    reading.output >= contract.getMinimumRegulatedOutputVolts(),
                    contract.getVariantId() +
                    " 95/99% hard-limit demand remains finite and regulated");
            check(!regulator.isCurrentLimited(), contract.getVariantId() +
                    " remains out of current limit below the declared knee");
            verifyIndependentPower(harness, contract,
                    "pre-limit load " + demandFraction);
            previousCurrent = reading.current;
            previousOutput = reading.output;
        }

        double onsetResistance = contract.getNominalOutputVolts() /
                contract.getMaximumOutputCurrentAmps() -
                contract.getOutputResistanceOhms();
        harness.fixture.load.setResistance(onsetResistance);
        harness.analyzeAndSettle();
        readings.limitOnsetResistance = onsetResistance;
        readings.limitOnsetOutput = regulator.getOutputVoltage();
        readings.limitOnsetCurrent = regulator.getOutputCurrent();
        check(finite(readings.limitOnsetOutput) &&
                finite(readings.limitOnsetCurrent) &&
                readings.limitOnsetCurrent >=
                contract.getMaximumOutputCurrentAmps() - 1e-9 &&
                readings.limitOnsetCurrent <=
                contract.getMaximumOutputCurrentAmps() + CURRENT_LIMIT_SOLVER_TOLERANCE_AMPS &&
                regulator.isCurrentLimited(), contract.getVariantId() +
                " current limiting begins at the declared threshold");
        verifyIndependentPower(harness, contract, "current-limit onset");
        readings.loadBelowResistance = readings.loadEnvelope[2].resistance;
        readings.loadBelowCurrent = readings.loadEnvelope[2].current;
        readings.loadBelowOutput = readings.loadEnvelope[2].output;
        readings.loadNearResistance = onsetResistance;
        readings.loadNearCurrent = readings.limitOnsetCurrent;
        readings.loadNearOutput = readings.limitOnsetOutput;

        harness.fixture.load.setResistance(1000.0);
        harness.fixture.input.setVoltage(harness.fixture.nominalInputVoltage);
        harness.fixture.enable.setVoltage(5.0);
        harness.analyzeAndSettle();
        verifyIndependentPower(harness, contract, "post-sweep light load");
        return readings;
    }

    /**
     * Checks the regulator against equations and solved source currents that
     * do not call its power-reporting helpers.  This makes the proof reject a
     * self-consistent but energy-creating implementation.
     */
    private static void verifyIndependentPower(Harness harness,
            RailRegulationContract contract, String state) {
        AbstractRailRegulatorElm regulator = harness.fixture.regulator;
        double inputVolts = regulator.getInputVoltage();
        double outputVolts = regulator.getOutputVoltage();
        double enableVolts = regulator.getEnableVoltage();
        double outputAmps = Math.max(0.0, harness.fixture.load.getCurrent());
        near(regulator.getOutputCurrent(), outputAmps, 2e-7,
                contract.getVariantId() + " " + state +
                " solved output-load KCL");
        double enableFraction;
        if (enableVolts <= contract.getEnableLowVolts()) {
            enableFraction = 0.0;
        } else if (enableVolts >= contract.getEnableHighVolts()) {
            enableFraction = 1.0;
        } else {
            enableFraction = (enableVolts - contract.getEnableLowVolts()) /
                    (contract.getEnableHighVolts() - contract.getEnableLowVolts());
        }

        double expectedInputAmps = inputVolts / 1000000.0;
        if (enableFraction > 0.0 && inputVolts > 0.0) {
            if (contract.isAveragedSwitching()) {
                expectedInputAmps += Math.max(0.0, outputVolts) * outputAmps /
                        (inputVolts * contract.getEfficiency());
            } else {
                expectedInputAmps += outputAmps;
            }
            expectedInputAmps += contract.getQuiescentCurrentAmps() *
                    enableFraction;
        }
        near(regulator.getInputCurrent(), expectedInputAmps, 2e-8,
                contract.getVariantId() + " " + state +
                " input current follows the independent variant equation");

        double sourceAmps = harness.fixture.input.getCurrent();
        double enableSourceAmps = harness.fixture.enable.getCurrent();
        near(sourceAmps, regulator.getInputCurrent(), 2e-7,
                contract.getVariantId() + " " + state +
                " solved input-source KCL");
        near(enableSourceAmps, regulator.getEnableLeakCurrent(), 2e-7,
                contract.getVariantId() + " " + state +
                " solved enable-source KCL");

        double inputTerminalWatts = harness.fixture.input.getVoltageDiff() *
                sourceAmps;
        double enableTerminalWatts = harness.fixture.enable.getVoltageDiff() *
                enableSourceAmps;
        near(inputTerminalWatts, regulator.getInputPowerWatts(), 3e-6,
                contract.getVariantId() + " " + state +
                " finite input source delivers measured terminal power");
        near(enableTerminalWatts, regulator.getEnablePowerWatts(), 3e-6,
                contract.getVariantId() + " " + state +
                " finite enable source delivers measured terminal power");

        double inputIdealWatts = harness.fixture.input.getSourceVoltage() * sourceAmps;
        double inputSourceLoss = sourceAmps * sourceAmps *
                harness.fixture.input.getResistance();
        near(inputIdealWatts - inputSourceLoss, inputTerminalWatts, 3e-6,
                contract.getVariantId() + " " + state +
                " finite input-source loss closes independently");
        double enableIdealWatts = harness.fixture.enable.getSourceVoltage() *
                enableSourceAmps;
        double enableSourceLoss = enableSourceAmps * enableSourceAmps *
                harness.fixture.enable.getResistance();
        near(enableIdealWatts - enableSourceLoss, enableTerminalWatts, 3e-6,
                contract.getVariantId() + " " + state +
                " finite enable-source loss closes independently");

        double suppliedWatts = inputTerminalWatts + enableTerminalWatts;
        double outputWatts = outputVolts * outputAmps;
        check(finite(suppliedWatts) && finite(outputWatts) &&
                suppliedWatts + 1e-8 >= outputWatts &&
                regulator.getPowerLossWatts() >= -1e-8,
                contract.getVariantId() + " " + state +
                " has no energy creation");
        if (contract.isAveragedSwitching() && enableFraction > 0.0 &&
                inputVolts > 0.0 && outputWatts > 0.0) {
            double conversionInputWatts = inputVolts *
                    (expectedInputAmps - inputVolts / 1000000.0 -
                    contract.getQuiescentCurrentAmps() * enableFraction);
            near(conversionInputWatts * contract.getEfficiency(), outputWatts,
                    3e-6, contract.getVariantId() + " " + state +
                    " obeys declared averaged efficiency");
        }
    }

    private static void appendObserved(RailRegulationContract contract,
            double lightOutput, double lightCurrent, SweepReadings sweep,
            double overloadOutput, double overloadCurrent) {
        if (observed.length() > 0) observed.append(',');
        observed.append("{\"variant\":\"").append(contract.getVariantId())
                .append("\",\"usableRegulatedCurrentFraction\":")
                .append(contract.getUsableRegulatedCurrentFraction())
                .append(",\"usableRegulatedCurrentAmps\":")
                .append(contract.getUsableRegulatedCurrentAmps())
                .append(",\"regulatedVoltageToleranceVolts\":")
                .append(contract.getRegulatedVoltageToleranceVolts())
                .append(",\"minimumRegulatedOutputVolts\":")
                .append(contract.getMinimumRegulatedOutputVolts())
                .append(",\"light\":");
        appendPoint(observed, 0.0, 0.0, lightOutput, lightCurrent, 1000.0);
        observed.append(",\"loadEnvelope\":[");
        for (int i = 0; i < sweep.loadEnvelope.length; i++) {
            if (i > 0) observed.append(',');
            LoadReading reading = sweep.loadEnvelope[i];
            appendPoint(observed, reading.fraction, reading.demandFraction,
                    reading.output, reading.current, reading.resistance);
        }
        observed.append("],\"preLimitEnvelope\":[");
        for (int i = 0; i < sweep.preLimitEnvelope.length; i++) {
            if (i > 0) observed.append(',');
            LoadReading reading = sweep.preLimitEnvelope[i];
            appendPoint(observed, reading.fraction, reading.demandFraction,
                    reading.output, reading.current, reading.resistance);
        }
        observed.append("],\"limitOnset\":");
        appendPoint(observed, 1.0, 1.0, sweep.limitOnsetOutput,
                sweep.limitOnsetCurrent, sweep.limitOnsetResistance);
        observed.append(",\"short\":");
        appendPoint(observed, 1.0, 1.0, overloadOutput, overloadCurrent, .1);
        observed.append('}');
    }

    private static void appendPoint(StringBuilder out, double fraction,
            double output, double current, double resistance) {
        appendPoint(out, fraction, fraction, output, current, resistance);
    }

    private static void appendPoint(StringBuilder out, double fraction,
            double demandFraction, double output, double current,
            double resistance) {
        out.append("{\"fraction\":").append(fraction)
                .append(",\"demandFraction\":").append(demandFraction)
                .append(",\"resistanceOhms\":").append(resistance)
                .append(",\"outputVolts\":").append(output)
                .append(",\"outputAmps\":").append(current).append('}');
    }

    private static final class LoadReading {
        double fraction;
        double demandFraction;
        double resistance;
        double output;
        double current;
    }

    private static final class SweepReadings {
        LoadReading[] loadEnvelope;
        LoadReading[] preLimitEnvelope;
        double limitOnsetResistance;
        double limitOnsetOutput;
        double limitOnsetCurrent;
        double loadBelowResistance;
        double loadBelowCurrent;
        double loadBelowOutput;
        double loadNearResistance;
        double loadNearCurrent;
        double loadNearOutput;
    }

    private static final class TransientReading {
        double elapsedSeconds;
        double startingVolts;
        double limitEquilibriumVolts;
        double outputVolts;
        double outputAmps;
    }

    private static final class Harness {
        final CirSim sim;
        final E02RegulatorDeveloperVerifier.Fixture fixture;

        Harness(boolean averaged, RailRegulationContract contract) {
            this(averaged, contract, 1e-4, 0.0);
        }

        Harness(boolean averaged, RailRegulationContract contract,
                double maxTimeStep) {
            this(averaged, contract, maxTimeStep, 0.0);
        }

        Harness(boolean averaged, RailRegulationContract contract,
                double maxTimeStep, double returnReferenceVoltage) {
            sim = new CirSim();
            sim.gridSize = 16;
            sim.gridMask = ~15;
            sim.gridRound = 7;
            sim.maxTimeStep = maxTimeStep;
            sim.minTimeStep = 1e-7;
            sim.adjustTimeStep = true;
            sim.elmList = new Vector<CircuitElm>();
            sim.adjustables = new Vector<Adjustable>();
            CircuitElm.sim = sim;
            fixture = new E02RegulatorDeveloperVerifier.Fixture(averaged, contract,
                    returnReferenceVoltage);
            sim.elmList = fixture.elements;
            analyzeAndSettle();
        }

        void analyzeAndSettle() {
            sim.analyzeCircuit();
            sim.solverExecutor.advanceSteps(8);
        }

        void close() {
            for (CircuitElm element : fixture.elements) element.delete();
        }
    }

    private static void reject(Runnable operation) {
        try {
            operation.run();
            throw new AssertionError("E02 invalid contract/model accepted");
        } catch (IllegalArgumentException expected) {
            assertions++;
        }
    }

    private static void rejectMapping(Runnable operation) {
        try {
            operation.run();
            throw new AssertionError("E02 invalid physical mapping accepted");
        } catch (IllegalArgumentException expected) {
            assertions++;
        }
    }

    private static void check(boolean condition) {
        assertions++;
        if (!condition) throw new AssertionError("E02 contract " + assertions);
    }

    private static void check(boolean condition, String label) {
        assertions++;
        if (!condition) throw new AssertionError(label);
    }

    private static void near(double actual, double expected, double tolerance,
            String label) {
        check(finite(actual) && Math.abs(actual - expected) <= tolerance,
                label + ": " + actual + " expected " + expected);
    }

    private static boolean finite(double value) {
        return !Double.isNaN(value) && !Double.isInfinite(value);
    }
}
