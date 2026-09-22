package com.lushprojects.circuitjs1.client;

import java.util.Vector;

/** Independent E02 data-contract and model-boundary expectations. */
public final class E02RegulatorContractTest {
    private static int assertions;

    public static void main(String[] args) {
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
        verifyPhysicalPackage();

        reject(new Runnable() { public void run() {
            new RailRegulationContract("bad", "INPUT", "INPUT", "RETURN", "ENABLE",
                    5, 5.8, 24, .8, .2, .1, .8, 2, .001, 1, false);
        }});
        reject(new Runnable() { public void run() {
            new RailRegulationContract("bad", "INPUT", "OUTPUT", "RETURN", "ENABLE",
                    5, 5.8, 24, .8, .2, .1, 2, .8, .001, 1, false);
        }});
        reject(new Runnable() { public void run() {
            new RailRegulationContract("bad", "INPUT", "OUTPUT", "RETURN", "ENABLE",
                    5, 5.8, 24, .8, .2, .1, .8, 2, .001, 1, true, true);
        }});
        reject(new Runnable() { public void run() {
            new RailRegulationContract("bad", "INPUT", "OUTPUT", "RETURN", "ENABLE",
                    5, 5.8, 5.5, .8, .2, .1, .8, 2, .001, 1, false);
        }});
        reject(new Runnable() { public void run() {
            new RailRegulationContract("bad", "INPUT", "OUTPUT", "RETURN", "ENABLE",
                    5, 5.8, 24, .8, .2, .1, .8, 2, .001, 1.01, false);
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

        Harness harness = new Harness(false, RailRegulationContract.linear5V());
        boolean backfeedRejected = false;
        try {
            E02FiniteSourceElm backfeed = new E02FiniteSourceElm(640, 192,
                    7.0, .1);
            backfeed.drag(640, 64);
            harness.fixture.elements.add(backfeed);
            WireElm output = new WireElm(backfeed.getPost(1).x,
                    backfeed.getPost(1).y);
            output.setPosition(backfeed.getPost(1).x, backfeed.getPost(1).y,
                    harness.fixture.regulator.getPost(
                            AbstractRailRegulatorElm.OUTPUT_POST).x,
                    harness.fixture.regulator.getPost(
                            AbstractRailRegulatorElm.OUTPUT_POST).y);
            harness.fixture.elements.add(output);
            WireElm returned = new WireElm(backfeed.getPost(0).x,
                    backfeed.getPost(0).y);
            returned.setPosition(backfeed.getPost(0).x, backfeed.getPost(0).y,
                    harness.fixture.regulator.getPost(
                            AbstractRailRegulatorElm.RETURN_POST).x,
                    harness.fixture.regulator.getPost(
                            AbstractRailRegulatorElm.RETURN_POST).y);
            harness.fixture.elements.add(returned);
            harness.sim.analyzeCircuit();
            harness.sim.solverExecutor.advanceSteps(8);
        } catch (IllegalArgumentException expected) {
            backfeedRejected = true;
        } finally {
            harness.close();
        }
        check(backfeedRejected,
                "output backfeed is rejected instead of misreported as balanced");
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
        double coarse = solveNominalOutput(averaged, contract, 1e-4);
        double fine = solveNominalOutput(averaged, contract, 2.5e-5);
        near(fine, coarse, .002,
                contract.getVariantId() + " nominal output is timestep-stable");
    }

    private static double solveNominalOutput(boolean averaged,
            RailRegulationContract contract, double maxTimeStep) {
        Harness harness = new Harness(averaged, contract, maxTimeStep);
        try {
            return harness.fixture.regulator.getOutputVoltage();
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
            harness.sim.solverExecutor.advanceSteps(8);
            near(regulator.getOutputVoltage(), settledOutput, .002,
                    contract.getVariantId() + " nominal output settles");
            check(regulator.getOutputVoltage() >
                    contract.getNominalOutputVolts() * .90,
                    "real solver follows the declared nominal output");
            check(regulator.getOutputCurrent() > 0.0 &&
                    regulator.getOutputCurrent() <
                    contract.getMaximumOutputCurrentAmps(),
                    "real solver light load remains below current limit");
            check(regulator.getInputPowerWatts() > regulator.getOutputPowerWatts() &&
                    regulator.getPowerLossWatts() > 0.0,
                    "real solver accounts for positive regulator loss");
            check(regulator.getInputLeakCurrent() > 0.0 &&
                    regulator.getEnableLeakCurrent() > 0.0,
                    "input and enable leakage remain explicit solver branches");
            near(regulator.getPowerLossWatts(),
                    regulator.getInputPowerWatts() + regulator.getEnablePowerWatts() -
                    regulator.getOutputPowerWatts(), 1e-12,
                    "power loss includes input and enable leakage");

            verifyIntermediateSweeps(harness, contract);

            load.setResistance(.1);
            harness.analyzeAndSettle();
            double overloadOutput = regulator.getOutputVoltage();
            double overloadCurrent = regulator.getOutputCurrent();
            check(regulator.getOutputVoltage() < .5 &&
                    overloadCurrent > 0.0 && overloadCurrent <=
                    contract.getMaximumOutputCurrentAmps() * 1.001,
                    "real solver reflects a finite short/overload limit");
            check(finite(overloadOutput) && overloadOutput >= -.01 &&
                    overloadOutput < settledOutput - .1,
                    contract.getVariantId() +
                    " overload is a distinct bounded diagnostic state");

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

            harness.fixture.enable.setVoltage(5.0);
            double dropoutInput = contract.getNominalOutputVolts() +
                    contract.getDropoutVolts() * .5;
            harness.fixture.input.setVoltage(dropoutInput);
            harness.analyzeAndSettle();
            check(regulator.isInDropout() && regulator.getOutputVoltage() <
                    contract.getNominalOutputVolts() - .1,
                    "real solver reflects input dropout");

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
            harness.fixture.input.setVoltage(harness.fixture.nominalInputVoltage);
            harness.analyzeAndSettle();
            double restoredOutput = regulator.getOutputVoltage();
            check(regulator.getOutputVoltage() >
                    contract.getNominalOutputVolts() * .90,
                    "real solver recovers after input restoration");
            check(restoredOutput > disabledOutput + .1 &&
                    restoredOutput > inputLossOutput + .1,
                    contract.getVariantId() +
                    " restoration returns to the healthy diagnostic state");
        } finally {
            harness.close();
        }
    }

    /**
     * Every declared rail variant gets the same real-solver intermediate
     * points.  Mutations are followed by a fresh CircuitJS analysis; no
     * expected reading is synthesized outside the solver.
     */
    private static void verifyIntermediateSweeps(Harness harness,
            RailRegulationContract contract) {
        AbstractRailRegulatorElm regulator = harness.fixture.regulator;
        harness.fixture.input.setVoltage(harness.fixture.nominalInputVoltage);
        harness.fixture.load.setResistance(1000.0);

        harness.fixture.enable.setVoltage(contract.getEnableLowVolts());
        harness.analyzeAndSettle();
        double enableLowOutput = regulator.getOutputVoltage();
        harness.fixture.enable.setVoltage((contract.getEnableLowVolts() +
                contract.getEnableHighVolts()) * .5);
        harness.analyzeAndSettle();
        double enableMidOutput = regulator.getOutputVoltage();
        harness.fixture.enable.setVoltage(contract.getEnableHighVolts());
        harness.analyzeAndSettle();
        double enableHighOutput = regulator.getOutputVoltage();
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
        boolean dropout = regulator.isInDropout();
        double headroomInput = contract.getMinimumInputVolts() + .5;
        harness.fixture.input.setVoltage(headroomInput);
        harness.analyzeAndSettle();
        double headroomOutput = regulator.getOutputVoltage();
        check(dropout && finite(dropoutOutput) && dropoutOutput >= -.01 &&
                dropoutOutput < contract.getNominalOutputVolts() - .1,
                contract.getVariantId() +
                " input dropout is observable and bounded");
        check(finite(headroomOutput) && headroomOutput > dropoutOutput + .05 &&
                headroomOutput <= contract.getNominalOutputVolts() * 1.01,
                contract.getVariantId() +
                " input headroom recovers monotonically");

        harness.fixture.input.setVoltage(harness.fixture.nominalInputVoltage);
        double effectiveOutputResistance = contract.getOutputResistanceOhms() +
                contract.getNominalOutputVolts() /
                contract.getMaximumOutputCurrentAmps();
        double belowLimitResistance = contract.getNominalOutputVolts() /
                (contract.getMaximumOutputCurrentAmps() * .75) -
                effectiveOutputResistance;
        double nearLimitResistance = contract.getNominalOutputVolts() /
                (contract.getMaximumOutputCurrentAmps() * .95) -
                effectiveOutputResistance;
        harness.fixture.load.setResistance(belowLimitResistance);
        harness.analyzeAndSettle();
        double loadBelowCurrent = regulator.getOutputCurrent();
        double loadBelowOutput = regulator.getOutputVoltage();
        harness.fixture.load.setResistance(nearLimitResistance);
        harness.analyzeAndSettle();
        double loadNearCurrent = regulator.getOutputCurrent();
        double loadNearOutput = regulator.getOutputVoltage();
        check(finite(loadBelowCurrent) && finite(loadNearCurrent) &&
                finite(loadBelowOutput) && finite(loadNearOutput),
                contract.getVariantId() + " load sweep is finite");
        check(loadBelowCurrent > contract.getMaximumOutputCurrentAmps() * .50 &&
                loadBelowCurrent < loadNearCurrent &&
                loadNearCurrent <= contract.getMaximumOutputCurrentAmps() * 1.001 &&
                loadBelowOutput > loadNearOutput && loadNearOutput >= -.01,
                contract.getVariantId() +
                " load sweep is monotonic and current-bounded");

        harness.fixture.load.setResistance(1000.0);
        harness.fixture.input.setVoltage(harness.fixture.nominalInputVoltage);
        harness.fixture.enable.setVoltage(5.0);
        harness.analyzeAndSettle();
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
