package com.lushprojects.circuitjs1.client;

import java.util.Vector;

/** Real CircuitJS solver fixture for the bounded E02 regulator variants. */
final class E02RegulatorDeveloperVerifier {
    // The 1e-9 S flat-branch leakage contributes <2.4e-8 A over the declared
    // input envelope; this 1e-7 A bound also covers solver rounding.
    private static final double CURRENT_LIMIT_SOLVER_TOLERANCE_AMPS = 1e-7;
    private static int assertions;

    private E02RegulatorDeveloperVerifier() { }

    /**
     * Runs only from an explicit developer route.  The returned receipt is
     * intentionally independent of normal player construction and contains no
     * answer-key or fault metadata.
     */
    static String verify(CirSim sim, boolean forced) {
        if (sim == null || !sim.troubleshootDebug || !sim.developerVerifierRunning)
            throw new IllegalStateException("E02 requires an explicit developer route");
        if (forced) throw new AssertionError("e02-explicit-failure-canary");
        GeneratedBoardInstance player = sim.getGeneratedBoardInstance();
        assertions = 0;
        long started = System.currentTimeMillis();
        StringBuilder cases = new StringBuilder();
        runVariant(sim, player, false, RailRegulationContract.linear12V(), cases);
        runVariant(sim, player, false, RailRegulationContract.linear5V(), cases);
        runVariant(sim, player, false, RailRegulationContract.linear3V3(), cases);
        runVariant(sim, player, true,
                RailRegulationContract.averagedSwitching12V(), cases);
        runVariant(sim, player, true, RailRegulationContract.averagedSwitching5V(), cases);
        runVariant(sim, player, true,
                RailRegulationContract.averagedSwitching3V3(), cases);
        require(sim.getGeneratedBoardInstance() == player,
                "private regulator proof restores exact player owner");
        return "{\"schema\":\"e02-regulator-proof-v2\",\"status\":\"PASS\",\"assertions\":" +
                assertions + ",\"cases\":[" + cases + "],\"elapsedMs\":" +
                (System.currentTimeMillis() - started) + "}";
    }

    private static void runVariant(CirSim sim, GeneratedBoardInstance player,
            boolean averaged, RailRegulationContract contract, StringBuilder cases) {
        PrivateSolverContext proof = PrivateSolverContext.open(sim);
        try {
            Fixture fixture = new Fixture(averaged, contract);
            proof.install(fixture.elements);
            proof.analyze();
            proof.advanceSteps(5);

            double nominalInput = fixture.regulator.getInputVoltage();
            double nominalOutput = fixture.regulator.getOutputVoltage();
            double nominalCurrent = fixture.regulator.getOutputCurrent();
            double nominalLoss = fixture.regulator.getPowerLossWatts();
            require(finite(nominalInput) && finite(nominalOutput) &&
                    finite(nominalCurrent), "nominal solver values are finite");
            proof.advanceSteps(5);
            require(Math.abs(fixture.regulator.getOutputVoltage() - nominalOutput) <=
                    .002, "nominal solver output settles");
            require(nominalOutput > contract.getMinimumRegulatedOutputVolts(),
                    "light load follows the declared rail target");
            require(nominalCurrent > 0.0 && nominalCurrent <
                    contract.getMaximumOutputCurrentAmps(),
                    "light-load current is finite and below the limit");
            requireIndependentPower(fixture, contract, "light load");
            require(fixture.regulator.getInputLeakCurrent() > 0.0 &&
                    fixture.regulator.getEnableLeakCurrent() > 0.0,
                    "input and enable leakage remain explicit branches");

            SweepReadings sweep = runIntermediateSweeps(proof, fixture, contract);

            fixture.load.setResistance(.1);
            proof.analyze();
            proof.advanceSteps(5);
            double overloadOutput = fixture.regulator.getOutputVoltage();
            double overloadCurrent = fixture.regulator.getOutputCurrent();
            require(fixture.regulator.getOutputVoltage() < .5,
                    "short/overload collapses output voltage");
            require(finite(overloadOutput) && overloadOutput >= -.01 &&
                    overloadOutput < nominalOutput - .1,
                    "overload is a distinct bounded diagnostic state");
            require(overloadCurrent > 0.0 && overloadCurrent <=
                    contract.getMaximumOutputCurrentAmps() + CURRENT_LIMIT_SOLVER_TOLERANCE_AMPS,
                    "short/overload is bounded by the declared output current limit");
            require(fixture.regulator.isCurrentLimited(),
                    "short/overload reaches the current-limit region");
            requireIndependentPower(fixture, contract, "hard overload");

            fixture.load.setResistance(1000.0);
            fixture.enable.setVoltage(0.0);
            proof.analyze();
            proof.advanceSteps(5);
            double disabledOutput = fixture.regulator.getOutputVoltage();
            require(fixture.regulator.getOutputVoltage() < .1,
                    "disabled regulator does not hold an ideal output rail");
            require(finite(disabledOutput) && disabledOutput >= -.01 &&
                    disabledOutput < nominalOutput - .1,
                    "disabled regulator is a distinct bounded diagnostic state");
            require(fixture.regulator.getInputCurrent() > 0.0 &&
                    fixture.regulator.getInputCurrent() < 5e-5,
                    "disabled regulator reports only bounded input leakage");
            requireIndependentPower(fixture, contract, "disabled");

            fixture.enable.setVoltage(5.0);
            proof.analyze();
            proof.advanceSteps(5);
            double dropoutInput = contract.getNominalOutputVolts() +
                    contract.getDropoutVolts() * .5;
            fixture.input.setVoltage(dropoutInput);
            proof.analyze();
            proof.advanceSteps(5);
            require(fixture.regulator.isInDropout(),
                    "input headroom below nominal is reported as dropout");
            require(fixture.regulator.getOutputVoltage() <
                    contract.getNominalOutputVolts() - .1,
                    "dropout reduces the solved output");
            requireIndependentPower(fixture, contract, "dropout");

            fixture.input.setVoltage(fixture.nominalInputVoltage);
            fixture.input.setVoltage(0.0);
            proof.analyze();
            proof.advanceSteps(5);
            double inputLossOutput = fixture.regulator.getOutputVoltage();
            require(fixture.regulator.getInputVoltage() < .1,
                    "input loss removes the regulator input rail");
            require(fixture.regulator.getOutputVoltage() < .1,
                    "input loss removes the output rail through the live model");
            require(finite(inputLossOutput) && inputLossOutput >= -.01 &&
                    inputLossOutput < nominalOutput - .1,
                    "input loss is a distinct bounded diagnostic state");
            requireIndependentPower(fixture, contract, "source loss");
            fixture.input.setVoltage(fixture.nominalInputVoltage);
            proof.analyze();
            proof.advanceSteps(5);
            double restoredOutput = fixture.regulator.getOutputVoltage();
            require(fixture.regulator.getOutputVoltage() >
                    contract.getMinimumRegulatedOutputVolts(),
                    "restored input recovers the regulated output");
            require(restoredOutput > disabledOutput + .1 &&
                    restoredOutput > inputLossOutput + .1,
                    "restoration separates the healthy diagnostic state");
            requireIndependentPower(fixture, contract, "restored input");

            if (cases.length() > 0) cases.append(',');
            cases.append("{\"variant\":\"").append(contract.getVariantId())
                    .append("\",\"usableRegulatedCurrentFraction\":")
                    .append(contract.getUsableRegulatedCurrentFraction())
                    .append(",\"usableRegulatedCurrentAmps\":")
                    .append(contract.getUsableRegulatedCurrentAmps())
                    .append(",\"regulatedVoltageToleranceVolts\":")
                    .append(contract.getRegulatedVoltageToleranceVolts())
                    .append(",\"minimumRegulatedOutputVolts\":")
                    .append(contract.getMinimumRegulatedOutputVolts())
                    .append(",\"inputVolts\":").append(nominalInput)
                    .append(",\"outputVolts\":").append(nominalOutput)
                    .append(",\"outputAmps\":").append(nominalCurrent)
                    .append(",\"lossWatts\":").append(nominalLoss)
                    .append(",\"sweep\":{\"enableLowOutputVolts\":")
                    .append(sweep.enableLowOutput)
                    .append(",\"enableMidOutputVolts\":")
                    .append(sweep.enableMidOutput)
                    .append(",\"enableHighOutputVolts\":")
                    .append(sweep.enableHighOutput)
                    .append(",\"dropoutOutputVolts\":")
                    .append(sweep.dropoutOutput)
                    .append(",\"dropoutInputVolts\":")
                    .append(sweep.dropoutInput)
                    .append(",\"headroomOutputVolts\":")
                    .append(sweep.headroomOutput)
                    .append(",\"headroomInputVolts\":")
                    .append(sweep.headroomInput)
                    .append(",\"loadBelowResistanceOhms\":")
                    .append(sweep.loadBelowResistance)
                    .append(",\"loadBelowLimitAmps\":")
                    .append(sweep.loadBelowCurrent)
                    .append(",\"loadBelowOutputVolts\":")
                    .append(sweep.loadBelowOutput)
                    .append(",\"loadNearResistanceOhms\":")
                    .append(sweep.loadNearResistance)
                    .append(",\"loadNearLimitAmps\":")
                    .append(sweep.loadNearCurrent)
                    .append(",\"loadNearOutputVolts\":")
                    .append(sweep.loadNearOutput)
                    .append("},\"loadEnvelope\":[");
            for (int index = 0; index < sweep.loadEnvelope.length; index++) {
                if (index > 0) cases.append(',');
                LoadReading reading = sweep.loadEnvelope[index];
                cases.append("{\"fraction\":").append(reading.fraction)
                        .append(",\"demandFraction\":").append(reading.demandFraction)
                        .append(",\"resistanceOhms\":").append(reading.resistance)
                        .append(",\"outputVolts\":").append(reading.output)
                        .append(",\"outputAmps\":").append(reading.current)
                        .append('}');
            }
            cases.append("],\"preLimitEnvelope\":[");
            for (int index = 0; index < sweep.preLimitEnvelope.length; index++) {
                if (index > 0) cases.append(',');
                LoadReading reading = sweep.preLimitEnvelope[index];
                cases.append("{\"fraction\":").append(reading.fraction)
                        .append(",\"demandFraction\":").append(reading.demandFraction)
                        .append(",\"resistanceOhms\":").append(reading.resistance)
                        .append(",\"outputVolts\":").append(reading.output)
                        .append(",\"outputAmps\":").append(reading.current)
                        .append('}');
            }
            cases.append("],\"limitOnset\":{\"fraction\":1.0,\"demandFraction\":1.0,\"resistanceOhms\":")
                    .append(sweep.limitOnsetResistance)
                    .append(",\"outputVolts\":").append(sweep.limitOnsetOutput)
                    .append(",\"outputAmps\":").append(sweep.limitOnsetCurrent)
                    .append("},\"short\":{\"fraction\":1.0,\"demandFraction\":1.0,\"resistanceOhms\":0.1,\"outputVolts\":")
                    .append(overloadOutput)
                    .append(",\"outputAmps\":").append(overloadCurrent)
                    .append("},\"diagnostics\":{\"overloadOutputVolts\":")
                    .append(overloadOutput)
                    .append(",\"overloadOutputAmps\":")
                    .append(overloadCurrent)
                    .append(",\"disabledOutputVolts\":")
                    .append(disabledOutput)
                    .append(",\"inputLossOutputVolts\":")
                    .append(inputLossOutput)
                    .append(",\"restoredOutputVolts\":")
                    .append(restoredOutput).append("}}");
        } finally {
            proof.close();
        }
        require(sim.getGeneratedBoardInstance() == player,
                "variant proof restores the player owner");
    }

    /**
     * Intermediate points are intentionally solved by CircuitJS after every
     * source/load mutation.  They cover enable interpolation, input
     * headroom/dropout, four usable-envelope points, two hard-limit approach
     * points and limit onset.
     */
    private static SweepReadings runIntermediateSweeps(PrivateSolverContext proof,
            Fixture fixture, RailRegulationContract contract) {
        SweepReadings readings = new SweepReadings();
        fixture.input.setVoltage(fixture.nominalInputVoltage);
        fixture.load.setResistance(1000.0);

        fixture.enable.setVoltage(contract.getEnableLowVolts());
        settle(proof);
        readings.enableLowOutput = fixture.regulator.getOutputVoltage();
        requireIndependentPower(fixture, contract, "enable low");
        fixture.enable.setVoltage((contract.getEnableLowVolts() +
                contract.getEnableHighVolts()) * .5);
        settle(proof);
        readings.enableMidOutput = fixture.regulator.getOutputVoltage();
        requireIndependentPower(fixture, contract, "enable mid");
        fixture.enable.setVoltage(contract.getEnableHighVolts());
        settle(proof);
        readings.enableHighOutput = fixture.regulator.getOutputVoltage();
        requireIndependentPower(fixture, contract, "enable high");
        require(finite(readings.enableLowOutput) &&
                finite(readings.enableMidOutput) &&
                finite(readings.enableHighOutput),
                contract.getVariantId() + " enable sweep is finite");
        require(readings.enableLowOutput < .1 &&
                readings.enableMidOutput > readings.enableLowOutput + .05 &&
                readings.enableHighOutput > readings.enableMidOutput + .05 &&
                readings.enableHighOutput <=
                contract.getNominalOutputVolts() * 1.01,
                contract.getVariantId() + " enable low/mid/high is monotonic and bounded");

        fixture.enable.setVoltage(5.0);
        double dropoutInput = contract.getNominalOutputVolts() +
                contract.getDropoutVolts() * .5;
        readings.dropoutInput = dropoutInput;
        fixture.input.setVoltage(dropoutInput);
        settle(proof);
        readings.dropoutOutput = fixture.regulator.getOutputVoltage();
        requireIndependentPower(fixture, contract, "sweep dropout");
        boolean dropout = fixture.regulator.isInDropout();
        double headroomInput = contract.getMinimumInputVolts() + .5;
        readings.headroomInput = headroomInput;
        fixture.input.setVoltage(headroomInput);
        settle(proof);
        readings.headroomOutput = fixture.regulator.getOutputVoltage();
        requireIndependentPower(fixture, contract, "sweep headroom");
        require(dropout && finite(readings.dropoutOutput) &&
                readings.dropoutOutput >= -.01 && readings.dropoutOutput <
                contract.getNominalOutputVolts() - .1,
                contract.getVariantId() + " input dropout is observable and bounded");
        require(finite(readings.headroomOutput) &&
                readings.headroomOutput > readings.dropoutOutput + .05 &&
                readings.headroomOutput <= contract.getNominalOutputVolts() * 1.01,
                contract.getVariantId() + " input headroom recovers monotonically");

        fixture.input.setVoltage(fixture.nominalInputVoltage);
        readings.loadEnvelope = new LoadReading[4];
        readings.preLimitEnvelope = new LoadReading[2];
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
            fixture.load.setResistance(resistance);
            settle(proof);
            LoadReading reading = new LoadReading();
            reading.fraction = fraction;
            reading.demandFraction = demandFraction;
            reading.resistance = resistance;
            reading.output = fixture.regulator.getOutputVoltage();
            reading.current = fixture.regulator.getOutputCurrent();
            readings.loadEnvelope[index] = reading;
            require(finite(reading.output) && finite(reading.current),
                    contract.getVariantId() + " load envelope point is finite");
            require(reading.current > previousCurrent &&
                    reading.current <= contract.getMaximumOutputCurrentAmps() +
                        CURRENT_LIMIT_SOLVER_TOLERANCE_AMPS &&
                    reading.output <= previousOutput &&
                    reading.output >= contract.getMinimumRegulatedOutputVolts(),
                    contract.getVariantId() +
                    " 25/50/75/90% loads regulate before the limit");
            require(!fixture.regulator.isCurrentLimited(), contract.getVariantId() +
                    " remains out of current limit through 90% load");
            requireIndependentPower(fixture, contract,
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
            fixture.load.setResistance(resistance);
            settle(proof);
            LoadReading reading = new LoadReading();
            reading.fraction = demandFraction;
            reading.demandFraction = demandFraction;
            reading.resistance = resistance;
            reading.output = fixture.regulator.getOutputVoltage();
            reading.current = fixture.regulator.getOutputCurrent();
            readings.preLimitEnvelope[index] = reading;
            require(finite(reading.output) && finite(reading.current) &&
                    reading.current > previousCurrent &&
                    reading.current < contract.getMaximumOutputCurrentAmps() &&
                    reading.output <= previousOutput &&
                    reading.output >= contract.getMinimumRegulatedOutputVolts(),
                    contract.getVariantId() +
                    " 95/99% hard-limit demand remains finite and regulated");
            require(!fixture.regulator.isCurrentLimited(), contract.getVariantId() +
                    " remains out of current limit below the declared knee");
            requireIndependentPower(fixture, contract,
                    "pre-limit load " + demandFraction);
            previousCurrent = reading.current;
            previousOutput = reading.output;
        }

        double onsetResistance = contract.getNominalOutputVolts() /
                contract.getMaximumOutputCurrentAmps() -
                contract.getOutputResistanceOhms();
        fixture.load.setResistance(onsetResistance);
        settle(proof);
        readings.limitOnsetResistance = onsetResistance;
        readings.limitOnsetOutput = fixture.regulator.getOutputVoltage();
        readings.limitOnsetCurrent = fixture.regulator.getOutputCurrent();
        require(finite(readings.limitOnsetOutput) &&
                finite(readings.limitOnsetCurrent) &&
                readings.limitOnsetCurrent >=
                contract.getMaximumOutputCurrentAmps() - 1e-9 &&
                readings.limitOnsetCurrent <=
                contract.getMaximumOutputCurrentAmps() + CURRENT_LIMIT_SOLVER_TOLERANCE_AMPS &&
                fixture.regulator.isCurrentLimited(), contract.getVariantId() +
                " current limiting begins at the declared threshold");
        requireIndependentPower(fixture, contract, "current-limit onset");
        readings.loadBelowResistance = readings.loadEnvelope[2].resistance;
        readings.loadBelowCurrent = readings.loadEnvelope[2].current;
        readings.loadBelowOutput = readings.loadEnvelope[2].output;
        readings.loadNearResistance = onsetResistance;
        readings.loadNearCurrent = readings.limitOnsetCurrent;
        readings.loadNearOutput = readings.limitOnsetOutput;

        fixture.load.setResistance(1000.0);
        fixture.input.setVoltage(fixture.nominalInputVoltage);
        fixture.enable.setVoltage(5.0);
        settle(proof);
        requireIndependentPower(fixture, contract, "post-sweep light load");
        return readings;
    }

    /** Independent equations plus solved finite-source KCL/power balance. */
    private static void requireIndependentPower(Fixture fixture,
            RailRegulationContract contract, String state) {
        AbstractRailRegulatorElm regulator = fixture.regulator;
        double inputVolts = regulator.getInputVoltage();
        double outputVolts = regulator.getOutputVoltage();
        double enableVolts = regulator.getEnableVoltage();
        double outputAmps = Math.max(0.0, fixture.load.getCurrent());
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

        double sourceAmps = fixture.input.getCurrent();
        double enableSourceAmps = fixture.enable.getCurrent();
        near(sourceAmps, regulator.getInputCurrent(), 2e-7,
                contract.getVariantId() + " " + state +
                " solved input-source KCL");
        near(enableSourceAmps, regulator.getEnableLeakCurrent(), 2e-7,
                contract.getVariantId() + " " + state +
                " solved enable-source KCL");

        double inputTerminalWatts = fixture.input.getVoltageDiff() * sourceAmps;
        double enableTerminalWatts = fixture.enable.getVoltageDiff() *
                enableSourceAmps;
        near(inputTerminalWatts, regulator.getInputPowerWatts(), 3e-6,
                contract.getVariantId() + " " + state +
                " finite input source delivers measured terminal power");
        near(enableTerminalWatts, regulator.getEnablePowerWatts(), 3e-6,
                contract.getVariantId() + " " + state +
                " finite enable source delivers measured terminal power");

        double inputIdealWatts = fixture.input.getSourceVoltage() * sourceAmps;
        double inputSourceLoss = sourceAmps * sourceAmps *
                fixture.input.getResistance();
        near(inputIdealWatts - inputSourceLoss, inputTerminalWatts, 3e-6,
                contract.getVariantId() + " " + state +
                " finite input-source loss closes independently");
        double enableIdealWatts = fixture.enable.getSourceVoltage() *
                enableSourceAmps;
        double enableSourceLoss = enableSourceAmps * enableSourceAmps *
                fixture.enable.getResistance();
        near(enableIdealWatts - enableSourceLoss, enableTerminalWatts, 3e-6,
                contract.getVariantId() + " " + state +
                " finite enable-source loss closes independently");

        double suppliedWatts = inputTerminalWatts + enableTerminalWatts;
        double outputWatts = outputVolts * outputAmps;
        require(finite(suppliedWatts) && finite(outputWatts) &&
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

    private static void settle(PrivateSolverContext proof) {
        proof.analyze();
        proof.advanceSteps(5);
    }

    private static final class SweepReadings {
        double enableLowOutput;
        double enableMidOutput;
        double enableHighOutput;
        double dropoutInput;
        double dropoutOutput;
        double headroomInput;
        double headroomOutput;
        double loadBelowResistance;
        double loadBelowCurrent;
        double loadBelowOutput;
        double loadNearResistance;
        double loadNearCurrent;
        double loadNearOutput;
        LoadReading[] loadEnvelope;
        LoadReading[] preLimitEnvelope;
        double limitOnsetResistance;
        double limitOnsetOutput;
        double limitOnsetCurrent;
    }

    private static final class LoadReading {
        double fraction;
        double demandFraction;
        double resistance;
        double output;
        double current;
    }

    /** Package-visible fixture shared by the native contract canary. */
    static final class Fixture {
        final Vector<CircuitElm> elements = new Vector<CircuitElm>();
        final E02FiniteSourceElm input;
        final E02FiniteSourceElm enable;
        final AbstractRailRegulatorElm regulator;
        final ResistorElm load;
        final double nominalInputVoltage;

        Fixture(boolean averaged, RailRegulationContract contract) {
            this(averaged, contract, 0.0);
        }

        /**
         * The optional return-reference source is a native-test hook.  It
         * leaves the rail contract unchanged while allowing the contract test
         * to prove that readings are relative to RETURN, not solver ground.
         */
        Fixture(boolean averaged, RailRegulationContract contract,
                double returnReferenceVoltage) {
            nominalInputVoltage = Math.min(contract.getMaximumInputVolts() - .5,
                    contract.getNominalOutputVolts() + 7.0);
            input = new E02FiniteSourceElm(100, 192, nominalInputVoltage, .1);
            input.drag(100, 64);
            elements.add(input);

            regulator = averaged ? new AveragedSwitchingRegulatorElm(260, 64, contract) :
                    new LinearRegulatorElm(260, 64, contract);
            regulator.drag(420, 64);
            elements.add(regulator);

            enable = new E02FiniteSourceElm(300, 192, 5.0, 1.0);
            enable.drag(300, 32);
            elements.add(enable);

            load = new ResistorElm(520, 64);
            load.drag(520, 96);
            load.setResistance(1000.0);
            elements.add(load);

            connect(input.getPost(1), regulator.getPost(AbstractRailRegulatorElm.INPUT_POST));
            connect(enable.getPost(1), regulator.getPost(AbstractRailRegulatorElm.ENABLE_POST));
            connect(regulator.getPost(AbstractRailRegulatorElm.OUTPUT_POST), load.getPost(0));
            connect(load.getPost(1), regulator.getPost(AbstractRailRegulatorElm.RETURN_POST));
            connect(input.getPost(0), regulator.getPost(AbstractRailRegulatorElm.RETURN_POST));
            connect(enable.getPost(0), regulator.getPost(AbstractRailRegulatorElm.RETURN_POST));
            if (returnReferenceVoltage == 0.0) {
                ground(input.getPost(0));
            } else {
                E02FiniteSourceElm reference = new E02FiniteSourceElm(
                        100, 320, returnReferenceVoltage, .1);
                reference.drag(100, 256);
                elements.add(reference);
                connect(reference.getPost(1), input.getPost(0));
                ground(reference.getPost(0));
            }
        }

        private void connect(Point from, Point to) {
            WireElm wire = new WireElm(from.x, from.y);
            wire.setPosition(from.x, from.y, to.x, to.y);
            elements.add(wire);
        }

        private void ground(Point point) {
            GroundElm ground = new GroundElm(point.x, point.y);
            ground.setPosition(point.x, point.y, point.x, point.y + 32);
            elements.add(ground);
        }
    }

    private static boolean finite(double value) {
        return !Double.isNaN(value) && !Double.isInfinite(value);
    }

    private static void require(boolean condition, String message) {
        assertions++;
        if (!condition) throw new AssertionError("E02: " + message);
    }

    private static void near(double actual, double expected, double tolerance,
            String message) {
        require(finite(actual) && Math.abs(actual - expected) <= tolerance,
                message + ": " + actual + " expected " + expected);
    }
}
