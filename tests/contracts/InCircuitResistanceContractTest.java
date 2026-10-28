package com.lushprojects.circuitjs1.client;

import java.util.Vector;

/** Literal electrical oracles for the real meter transaction, separate from RB56 admission. */
public final class InCircuitResistanceContractTest {
    private static int assertions;
    private static final InstrumentMode OHM = new InstrumentMode("RESISTANCE");

    public static void main(String[] args) {
        CirSim prior = CircuitElm.sim, singleton = CirSim.theSim;
        String diodeModel = DiodeElm.lastModelName;
        MeterSim sim = new MeterSim();
        GeneratedBoardInstance owner = null;
        Vector<CircuitElm> fixture = new Vector<CircuitElm>();
        Throwable failure = null;
        CircuitElm.sim = sim;
        try {
            IllegalStateException forced = new IllegalStateException("forced solver stamp failure");
            check(sim.reportCircuitFailure(forced) == forced &&
                    "CircuitJS solver failed: forced solver stamp failure".equals(sim.stopMessage) &&
                    !sim.simRunning,
                "solver failure retains its actual cause and stops simulation");
            sim.stopMessage = null;
            owner = new LedIndicatorGenerator().generate(3);
            sim.generatedBoardInstance = owner;
            sim.elmList = new Vector<CircuitElm>(owner.getSimulationElements());
            sim.boardModificationController = new BoardModificationController(sim, owner);
            sim.getBoardPowerController().attach(owner.getExternalPowerBindings());
            owner.getPhysicalBoardRuntime().installRegisteredCapabilities(sim, owner, sim.boardModificationController, 0);
            sim.getBoardPowerController().setState(BoardPowerState.UNPOWERED);
            owner.getPhysicalBoardRuntime().onBoardPowerStateChanged(BoardPowerState.UNPOWERED);
            sim.a01MeasurementRunning = true;

            verifyMemorylessStaticDcAcquisition(sim, owner, fixture);

            // These literal islands are test-only solver fixtures. They do not claim
            // RB56 target eligibility, package population or a physical player board.
            ResistorElm one = resistor(100000, 100000, 100080, 100000, 1000);
            ResistorElm ten = resistor(100000, 100000, 100080, 100000, 10000);
            add(sim, fixture, one); add(sim, fixture, ten);
            CircuitPostMeasurementEndpoint a = endpoint(one, 0), b = endpoint(one, 1);
            settle(sim, owner);
            numeric(sim, a, b, 10000.0 / 11, .01, "1k parallel 10k is 909.09 ohms, not either nameplate");
            numeric(sim, b, a, 10000.0 / 11, .01, "linear parallel reading is polarity independent");
            sim.elmList.remove(ten); settle(sim, owner);
            numeric(sim, a, b, 1000, .01, "removing the parallel path exposes the actual 1k branch");
            sim.elmList.add(ten); one.setResistance(2000); settle(sim, owner);
            numeric(sim, a, b, 10000.0 / 6, .01, "changing a connected branch changes equivalent resistance");
            int readers = sim.getResistanceResultReaderCallsForDeveloperVerification();
            check(Double.isNaN(sim.measureResistance(null, b, OHM)) &&
                    sim.getResistanceResultReaderCallsForDeveloperVerification() == readers,
                "an early invalid-target refusal does not invoke the actual result reader");

            WireElm shorted = new WireElm(100000, 100160);
            shorted.setPosition(100000, 100160, 100080, 100160);
            add(sim, fixture, shorted); settle(sim, owner);
            numeric(sim, endpoint(shorted, 0), endpoint(shorted, 1), 0, .001, "real same-net conductor reads zero");

            WireElm openA = new WireElm(100000, 100320), openB = new WireElm(100160, 100320);
            openA.setPosition(100000, 100320, 100032, 100320);
            openB.setPosition(100160, 100320, 100192, 100320);
            add(sim, fixture, openA); add(sim, fixture, openB); settle(sim, owner);
            double open = measure(sim, endpoint(openA, 0), endpoint(openB, 0));
            check(open > ResistanceInstrumentMode.MAX_RESISTANCE, "disconnected branches exceed the real meter range");

            DiodeElm diode = new DiodeElm(100000, 100480);
            diode.setPosition(100000, 100480, 100080, 100480);
            diode.modelName = "default"; diode.setup();
            check(diode.model.saturationCurrent == 1.7143528192808883e-7 &&
                diode.model.emissionCoefficient == 2 && diode.model.seriesResistance == 0 &&
                diode.model.breakdownVoltage == 0, "literal diode oracle pins its declared model");
            add(sim, fixture, diode); settle(sim, owner);
            double forward = measure(sim, endpoint(diode, 0), endpoint(diode, 1));
            double reverse = measure(sim, endpoint(diode, 1), endpoint(diode, 0));
            double expectedForward = diodeResistance(true), expectedReverse = diodeResistance(false);
            System.out.println("IN_CIRCUIT_DIODE forwardOhms=" + forward + " expectedForward=" + expectedForward +
                " reverseOhms=" + reverse + " expectedReverse=" + expectedReverse);
            check(PowerDomainContract.finite(forward) && Math.abs(forward / expectedForward - 1) < .001,
                "forward apparent resistance follows the independent Shockley/load-line oracle");
            check(PowerDomainContract.finite(reverse) && Math.abs(reverse / expectedReverse - 1) < .001 &&
                reverse < ResistanceInstrumentMode.MAX_RESISTANCE,
                "declared reverse leakage is measurable below 10M; do not force every reverse diode to OL");

            // Exercise production removal and reconstruction even when result collection fails.
            sim.troubleshootTask43PVerification = true; sim.developerVerifierRunning = true;
            for (CirSim.Task43PMeasurementFailureStage stage : new CirSim.Task43PMeasurementFailureStage[] {
                    CirSim.Task43PMeasurementFailureStage.READER,
                    CirSim.Task43PMeasurementFailureStage.AFTER_STIMULUS_REMOVE }) {
                Vector<CircuitElm> graph = new Vector<CircuitElm>(sim.elmList);
                sim.armTask43PMeasurementFailureForDeveloperVerification(stage);
                boolean rejected = false;
                try { sim.measureResistance(a, b, OHM); }
                catch (CirSim.Task43PInjectedMeasurementFailure expected) { rejected = true; }
                check(rejected, "actual declared measurement failure is surfaced: " + stage);
                restored(sim, graph);
                sim.clearTask43PMeasurementFailureForDeveloperVerification();
            }
            sim.troubleshootTask43PVerification = false; sim.developerVerifierRunning = false;
            numeric(sim, a, b, 10000.0 / 6, .01, "a new real measurement succeeds after both cleanup failures");
            verifyTransactionIdentity(sim, owner, a, b);
            verifyMeterChargedCapacitor(sim, owner, fixture);
            check(sim.getBoardPowerController().isElectricallyUnpowered(), "measurement and error cleanup never repower the board");
            verifyGeneratedFailurePrivacy(sim, owner);
        } catch (RuntimeException error) { failure = error; throw error;
        } catch (Error error) { failure = error; throw error;
        } finally {
            sim.a01MeasurementRunning = false;
            boolean overlayClear = !sim.activeMeasurementOverlay;
            sim.generatedBoardInstance = null; sim.boardModificationController = null;
            sim.solverExecutor.retire(); sim.solverExecutor.invalidate(); sim.circuitMatrix = null;
            sim.getBoardPowerController().detach();
            for (CircuitElm element : fixture) element.delete();
            if (owner != null) for (CircuitElm element : owner.getSimulationElements()) element.delete();
            sim.elmList.clear();
            DiodeElm.lastModelName = diodeModel; CircuitElm.sim = prior; CirSim.theSim = singleton;
            if (!overlayClear && failure == null) throw new AssertionError("Meter overlay survived fixture cleanup");
        }
        System.out.println("PASS: in-circuit resistance contracts assertions=" + assertions +
            " scope=LITERAL_SOLVER_TRANSACTION_NOT_RB56_ADMISSION");
    }
    private static ResistorElm resistor(int x, int y, int x2, int y2, double ohms) {
        ResistorElm result = new ResistorElm(x, y);
        result.setPosition(x, y, x2, y2); result.setResistance(ohms); return result;
    }

    private static void verifyTransactionIdentity(MeterSim sim, GeneratedBoardInstance owner,
            CircuitPostMeasurementEndpoint a, CircuitPostMeasurementEndpoint b) {
        LiteralProbe red = new LiteralProbe(sim, a), black = new LiteralProbe(sim, b);
        ActiveMeasurementSession session = new ActiveMeasurementSession(sim, red, black);
        check(session.hasValidTargets(), "fresh transaction binds its exact owner, graph and targets");
        Vector<CircuitElm> graph = sim.elmList;
        sim.elmList = new Vector<CircuitElm>(graph);
        check(!session.hasValidTargets(), "an equal successor graph cannot inherit the transaction");
        sim.elmList = graph;
        sim.generatedBoardInstance = null;
        check(!session.hasValidTargets(), "a changed owner cannot inherit a completed reading");
        sim.generatedBoardInstance = owner;
        red.endpoint = b;
        check(!session.hasValidTargets(), "retargeting the same probe wrapper invalidates its transaction");
        red.endpoint = a;
        red.valid = false;
        check(!session.hasValidTargets(), "an invalid physical target cannot retain a reading");
        red.valid = true;
        CircuitElm element = graph.remove(graph.size() - 1);
        check(!session.hasValidTargets(), "changing the contents of the same graph is detected");
        graph.add(element);
        String source = owner.getBoard().getPowerInputIds().get(0);
        ExternalPowerSimulationBinding binding = owner.getExternalPowerBindings().getBinding(source);
        double limit = binding.getLimitedSupply().getLimitAmps();
        binding.setCurrentLimit(limit / 2);
        check(!session.hasValidTargets(), "source command revision invalidates a completed reading");
        binding.setCurrentLimit(limit);
        check(!session.hasValidTargets(), "restoring a source value cannot resurrect the old command revision");
        session.close();
        check(!session.hasValidTargets(), "closed transaction cannot be reused");
        ActiveMeasurementSession fresh = new ActiveMeasurementSession(sim, red, black);
        check(fresh.hasValidTargets(), "a newly admitted transaction binds the current source controls");
        fresh.close();
        settle(sim, owner);
    }

    /** Static nonlinear DC networks use eight real steps and match a full-window oracle. */
    private static void verifyMemorylessStaticDcAcquisition(MeterSim sim,
            GeneratedBoardInstance owner, Vector<CircuitElm> fixture) {
        Vector<CircuitElm> created = new Vector<CircuitElm>();
        Vector<CircuitElm> originalGraph = new Vector<CircuitElm>(sim.elmList);
        double originalTimeStep = sim.timeStep;
        double originalMaximumTimeStep = sim.maxTimeStep;
        double originalMinimumTimeStep = sim.minTimeStep;
        boolean originalAdjustTimeStep = sim.adjustTimeStep;
        int originalUiSliceSteps = sim.uiSliceSteps;

        LEDElm led = new LEDElm(103000, 100000);
        led.setPosition(103000, 100000, 103080, 100000);
        led.modelName = "default-led";
        led.setup();
        add(sim, fixture, led); created.add(led);
        check("default-led".equals(led.modelName) && led.model.saturationCurrent == 93.2e-12 &&
                led.model.seriesResistance == .042 && led.model.emissionCoefficient == 3.73 &&
                led.model.breakdownVoltage == 0,
            "static nonlinear fixture pins the independent default LED model parameters");
        CircuitPostMeasurementEndpoint red = endpoint(led, 0), black = endpoint(led, 1);
        double expected = defaultLedResistance();
        try {
            for (double step : new double[] { .000005, .0001 }) {
                sim.timeStep = sim.maxTimeStep = sim.minTimeStep = step;
                sim.adjustTimeStep = false;
                settle(sim, owner);
                for (int speed : new int[] {1, 100}) {
                    sim.uiSliceSteps = speed;
                    Vector<CircuitElm> graph = new Vector<CircuitElm>(sim.elmList);
                    int uiCalls = sim.uiCalls;
                    int readers = sim.getResistanceResultReaderCallsForDeveloperVerification();
                    double started = sim.t;
                    double actual = sim.measureResistance(red, black, OHM);
                    double elapsed = sim.t - started;
                    check(PowerDomainContract.finite(actual) && Math.abs(actual - expected) < expected * .01,
                        "real default LED resistance matches the independent Shockley/load-line oracle");
                    check(sim.getResistanceResultReaderCallsForDeveloperVerification() == readers + 1,
                        "static nonlinear acquisition reads only the actual solver result once");
                    check(Math.abs(elapsed - 9 * step) < Math.max(1e-12, step * 1e-8),
                        "verified static DC acquisition takes eight accepted steps plus one cleanup step");
                    check(sim.uiCalls == uiCalls,
                        "static DC acquisition is independent of the UI solver slice size");
                    restored(sim, graph);
                    System.out.println("IN_CIRCUIT_STATIC_LED step=" + step + " uiSteps=" + speed +
                        " ohms=" + actual + " simulatedSeconds=" + elapsed);
                }
            }

            // An electrically negligible but unreviewed subclass must keep the
            // full 10ms window; its Java type could hide time or stored state.
            OpaqueResistorElm unknown = new OpaqueResistorElm(led.getPost(0).x,
                led.getPost(0).y, led.getPost(1).x, led.getPost(1).y, 1e15);
            add(sim, fixture, unknown); created.add(unknown);
            sim.timeStep = sim.maxTimeStep = sim.minTimeStep = .00005;
            settle(sim, owner);
            Vector<CircuitElm> graph = new Vector<CircuitElm>(sim.elmList);
            double started = sim.t;
            double fullWindow = sim.measureResistance(red, black, OHM);
            double elapsed = sim.t - started;
            check(PowerDomainContract.finite(fullWindow) && Math.abs(fullWindow - expected) < expected * .01,
                "unknown-subclass fallback still returns the actual nonlinear network reading");
            check(Math.abs(elapsed - (TemporaryMeasurementSettlement.ACTIVE_SECONDS + sim.timeStep)) < 1e-10,
                "an unreviewed element subclass selects the unchanged full acquisition window");
            restored(sim, graph);
            System.out.println("IN_CIRCUIT_STATIC_UNKNOWN ohms=" + fullWindow + " simulatedSeconds=" + elapsed);

            sim.elmList.remove(unknown); fixture.remove(unknown); unknown.delete();
            created.remove(unknown);
            sim.solverExecutor.invalidate();

            // Exact DCVoltageElm is allowed only in its constant, zero-bias form.
            DCVoltageElm varyingSource = new DCVoltageElm(104000, 100000);
            varyingSource.setPosition(104000, 100000, 104080, 100000);
            varyingSource.maxVoltage = 1;
            varyingSource.waveform = VoltageElm.WF_SQUARE;
            varyingSource.bias = 0;
            varyingSource.reset();
            ResistorElm varyingLoad = resistor(104000, 100000, 104080, 100000, 1000);
            GroundElm varyingGround = new GroundElm(104000, 100000);
            varyingGround.setPosition(104000, 100000, 104000, 100032);
            add(sim, fixture, varyingSource); created.add(varyingSource);
            add(sim, fixture, varyingLoad); created.add(varyingLoad);
            add(sim, fixture, varyingGround); created.add(varyingGround);
            settle(sim, owner);
            graph = new Vector<CircuitElm>(sim.elmList);
            started = sim.t;
            double waveformFallback = sim.measureResistance(red, black, OHM);
            elapsed = sim.t - started;
            check(PowerDomainContract.finite(waveformFallback) &&
                    Math.abs(waveformFallback - expected) < expected * .01,
                "time-varying disconnected source does not change the literal LED result");
            check(Math.abs(elapsed - (TemporaryMeasurementSettlement.ACTIVE_SECONDS + sim.timeStep)) < 1e-10,
                "a non-DC source waveform forces the full acquisition window");
            restored(sim, graph);

            varyingSource.waveform = VoltageElm.WF_DC;
            varyingSource.bias = .25;
            varyingSource.reset();
            settle(sim, owner);
            graph = new Vector<CircuitElm>(sim.elmList);
            started = sim.t;
            double biasedFallback = sim.measureResistance(red, black, OHM);
            elapsed = sim.t - started;
            check(PowerDomainContract.finite(biasedFallback) &&
                    Math.abs(biasedFallback - expected) < expected * .01,
                "biased disconnected source does not change the literal LED result");
            check(Math.abs(elapsed - (TemporaryMeasurementSettlement.ACTIVE_SECONDS + sim.timeStep)) < 1e-10,
                "a DC source with nonzero bias forces the full acquisition window");
            restored(sim, graph);
        } finally {
            for (CircuitElm element : created) {
                sim.elmList.remove(element);
                fixture.remove(element);
                element.delete();
            }
            sim.timeStep = originalTimeStep;
            sim.maxTimeStep = originalMaximumTimeStep;
            sim.minTimeStep = originalMinimumTimeStep;
            sim.adjustTimeStep = originalAdjustTimeStep;
            sim.uiSliceSteps = originalUiSliceSteps;
            check(sim.elmList.equals(originalGraph), "static acquisition fixtures restore the original graph");
        }
    }

    /** Independent Shockley law and meter burden for the pinned default LED model. */
    private static double defaultLedResistance() {
        final double saturationCurrent = 93.2e-12;
        final double seriesResistance = .042;
        final double emissionCoefficient = 3.73;
        final double thermalVoltage = .025865;
        final double internalResistance = ResistanceMeasurementStimulus.INTERNAL_RESISTANCE;
        double low = 0, high = .001;
        for (int iteration = 0; iteration < 100; iteration++) {
            double current = (low + high) / 2;
            double junctionVoltage = current * seriesResistance +
                emissionCoefficient * thermalVoltage * Math.log(1 + current / saturationCurrent);
            if (junctionVoltage + current * internalResistance >
                    ResistanceMeasurementStimulus.TEST_VOLTAGE)
                high = current;
            else
                low = current;
        }
        double current = (low + high) / 2;
        return 1 / current - internalResistance;
    }

    /** Real RC energy, not a forced readiness return or a replacement solver. */
    private static void verifyMeterChargedCapacitor(MeterSim sim, GeneratedBoardInstance owner,
            Vector<CircuitElm> fixture) {
        ResistorElm load = resistor(100000, 100640, 100080, 100640, 1000);
        CapacitorElm capacitor = new CapacitorElm(100000, 100640);
        capacitor.setPosition(100000, 100640, 100080, 100640);
        capacitor.capacitance = 1e-6;
        capacitor.initialVoltage = 0;
        capacitor.reset();
        add(sim, fixture, load); add(sim, fixture, capacitor);
        LiteralEnergyReadiness energy = new LiteralEnergyReadiness(capacitor);
        owner.getPhysicalBoardRuntime().registerCapability(energy);

        // The capacitor itself must retain the finite window. Remove the
        // unrelated generic DiodeElm from the earlier fixture so it cannot be
        // the reason this active transaction falls back.
        Vector<CircuitElm> excludedDiodes = new Vector<CircuitElm>();
        for (CircuitElm element : fixture) {
            if (element.getClass() == DiodeElm.class && sim.elmList.remove(element))
                excludedDiodes.add(element);
        }
        settle(sim, owner);
        LiteralProbe red = new LiteralProbe(sim, endpoint(load, 0));
        LiteralProbe black = new LiteralProbe(sim, endpoint(load, 1));
        CircuitMeasurementAdapter adapter = new CircuitMeasurementAdapter(sim);
        check(adapter.getActiveMeasurementReadiness(red, black, OHM) == ActiveMeasurementReadiness.READY,
            "discharged literal RC graph admits the first real injection");
        Vector<CircuitElm> graph = new Vector<CircuitElm>(sim.elmList);
        int readers = sim.getResistanceResultReaderCallsForDeveloperVerification();
        double started = sim.t;
        double reading = adapter.measureResistance(red, black, OHM);
        double elapsed = sim.t - started;
        check(PowerDomainContract.finite(reading) && reading >= 0 &&
                sim.getResistanceResultReaderCallsForDeveloperVerification() == readers + 1,
            "completed real RC sample survives its own post-transaction residual energy");
        check(Math.abs(elapsed - (TemporaryMeasurementSettlement.ACTIVE_SECONDS + sim.timeStep)) < 1e-10,
            "a real capacitor alone keeps active acquisition on the unchanged 10ms window");
        restored(sim, graph);
        for (CircuitElm element : excludedDiodes) sim.elmList.add(element);
        if (!excludedDiodes.isEmpty()) {
            sim.solverExecutor.invalidate(); sim.analyzeCircuit(); sim.analyzeFlag = false;
        }
        // The first transaction above proves the capacitor-only fallback and
        // restores that exact graph. Subsequent transactions own the graph
        // after the unrelated diode has deliberately been put back.
        graph = new Vector<CircuitElm>(sim.elmList);
        check(Math.abs(capacitor.getVoltageDiff()) > .25 &&
                adapter.getActiveMeasurementReadiness(red, black, OHM) == ActiveMeasurementReadiness.DISCHARGE,
            "the actual meter source charges the capacitor beyond the unchanged residual threshold");
        readers = sim.getResistanceResultReaderCallsForDeveloperVerification();
        check(Double.isNaN(adapter.measureResistance(red, black, OHM)) &&
                sim.getResistanceResultReaderCallsForDeveloperVerification() == readers,
            "remaining real energy blocks the next injection before its reader runs");
        sim.solverExecutor.advanceFor(.01);
        owner.getPhysicalBoardRuntime().observeSimulationTime(sim.t);
        check(Math.abs(capacitor.getVoltageDiff()) < .001 &&
                adapter.getActiveMeasurementReadiness(red, black, OHM) == ActiveMeasurementReadiness.READY,
            "explicit natural solver advance dissipates energy and restores admission");
        check(PowerDomainContract.finite(adapter.measureResistance(red, black, OHM)) &&
                sim.getResistanceResultReaderCallsForDeveloperVerification() == readers + 1,
            "a new real injection succeeds after natural recovery");
        restored(sim, graph);
        System.out.println("IN_CIRCUIT_RC heldOhms=" + reading + " residualVolts=" + capacitor.getVoltageDiff());

        double expected = Double.NaN;
        for (int speed : new int[] {1, 100}) {
            sim.uiSliceSteps = speed;
            for (int repeat = 0; repeat < 2; repeat++) {
                capacitor.reset();
                settle(sim, owner);
                int priorUiCalls = sim.uiCalls;
                double repeatStarted = sim.t;
                double actual = adapter.measureResistance(red, black, OHM);
                double repeatElapsed = sim.t - repeatStarted;
                check(PowerDomainContract.finite(actual) && Math.abs(actual - 1000) < .01,
                    "RC reading matches its literal DC load after the finite acquisition window");
                if (Double.isNaN(expected)) expected = actual;
                check(Double.doubleToLongBits(actual) == Double.doubleToLongBits(expected),
                    "the same discharged RC graph repeats exactly at both UI solver speeds");
                check(sim.uiCalls == priorUiCalls &&
                        Math.abs(repeatElapsed - (TemporaryMeasurementSettlement.ACTIVE_SECONDS + sim.timeStep)) < 1e-10,
                    "acquisition and cleanup use declared solver time rather than the UI slice");
                restored(sim, graph);
                System.out.println("IN_CIRCUIT_RC_REPEAT uiSteps=" + speed + " repeat=" + repeat +
                    " ohms=" + actual + " simulatedSeconds=" + repeatElapsed);
            }
        }

        verifyCompletedModeWhileDischarging(sim, owner, adapter, red, black, capacitor, load,
            "CONTINUITY");
        verifyCompletedModeWhileDischarging(sim, owner, adapter, red, black, capacitor, load,
            "DIODE");
        capacitor.reset();
        settle(sim, owner);
        verifyDiodeOlWhileDischarging(sim, owner, fixture);
    }

    /** A completed CONT/DIODE sample remains displayable, while each refresh still preflights. */
    private static void verifyCompletedModeWhileDischarging(MeterSim sim, GeneratedBoardInstance owner,
            CircuitMeasurementAdapter adapter, LiteralProbe red, LiteralProbe black,
            CapacitorElm capacitor, ResistorElm load, String modeId) {
        InstrumentMode mode = new InstrumentMode(modeId);
        capacitor.reset();
        settle(sim, owner);
        check(adapter.getActiveMeasurementReadiness(red, black, mode) == ActiveMeasurementReadiness.READY,
            modeId + " real RC fixture starts with injection admitted");
        Vector<CircuitElm> graph = new Vector<CircuitElm>(sim.elmList);
        double started = sim.t;
        int readers = sim.getResistanceResultReaderCallsForDeveloperVerification();
        boolean completed;
        if ("CONTINUITY".equals(modeId)) {
            double ohms = adapter.measureResistance(red, black, mode);
            completed = PowerDomainContract.finite(ohms) && ohms >= 0;
            check(completed && sim.getResistanceResultReaderCallsForDeveloperVerification() == readers + 1,
                "CONTINUITY completed a real finite RC resistance sample");
        } else {
            DiodeMeasurementResult result = adapter.measureDiode(red, black, mode);
            completed = result != null && PowerDomainContract.finite(result.voltage) &&
                PowerDomainContract.finite(result.current) && result.current >= .00001 && result.voltage < 2.95;
            check(completed, "DIODE completed a real finite RC injection sample");
        }
        double elapsed = sim.t - started;
        ActiveMeasurementReadiness afterSample = adapter.getActiveMeasurementReadiness(red, black, mode);
        check(completed && Math.abs(capacitor.getVoltageDiff()) > .25 &&
                afterSample == ActiveMeasurementReadiness.DISCHARGE &&
                InstrumentController.canRetainCompletedMeasurementAtReadiness(modeId, completed, afterSample),
            modeId + " captured sample remains retainable after its injection leaves residual RC energy");
        check(Math.abs(elapsed - (TemporaryMeasurementSettlement.ACTIVE_SECONDS + sim.timeStep)) < 1e-10,
            modeId + " sample uses the bounded acquisition and cleanup solver window");
        restored(sim, graph);

        double blockedAt = sim.t;
        double diodeVoltageBeforeBlocked = sim.getLastDiodeMeasurementVoltageForDeveloperVerification();
        double diodeCurrentBeforeBlocked = sim.getLastDiodeMeasurementCurrentForDeveloperVerification();
        if ("CONTINUITY".equals(modeId)) {
            check(Double.isNaN(adapter.measureResistance(red, black, mode)) &&
                    sim.getResistanceResultReaderCallsForDeveloperVerification() == readers + 1,
                "CONTINUITY refresh is refused before the reader while the RC remains charged");
        } else {
            check(adapter.measureDiode(red, black, mode) == null &&
                    Double.doubleToLongBits(sim.getLastDiodeMeasurementVoltageForDeveloperVerification()) ==
                        Double.doubleToLongBits(diodeVoltageBeforeBlocked) &&
                    Double.doubleToLongBits(sim.getLastDiodeMeasurementCurrentForDeveloperVerification()) ==
                        Double.doubleToLongBits(diodeCurrentBeforeBlocked),
                "DIODE refresh is refused while the RC remains charged");
        }
        check(sim.t == blockedAt && adapter.getActiveMeasurementReadiness(red, black, mode) ==
                ActiveMeasurementReadiness.DISCHARGE &&
                !InstrumentController.canRetainCompletedMeasurementAtReadiness("DC_VOLTAGE", true,
                    ActiveMeasurementReadiness.DISCHARGE) &&
                !InstrumentController.canRetainCompletedMeasurementAtReadiness(modeId, false,
                    ActiveMeasurementReadiness.DISCHARGE) &&
                !InstrumentController.canRetainCompletedMeasurementAtReadiness(modeId, true,
                    ActiveMeasurementReadiness.POWER_OFF) &&
                !InstrumentController.canRetainCompletedMeasurementAtReadiness(modeId, true,
                    ActiveMeasurementReadiness.WAITING),
            modeId + " blocked refresh consumes no solver time and retention excludes wrong-mode, incomplete, powered, or unsettled results");

        sim.solverExecutor.advanceFor(.01);
        owner.getPhysicalBoardRuntime().observeSimulationTime(sim.t);
        check(Math.abs(capacitor.getVoltageDiff()) < .001 &&
                adapter.getActiveMeasurementReadiness(red, black, mode) == ActiveMeasurementReadiness.READY,
            modeId + " natural RC cooldown restores refresh admission");
        started = sim.t;
        readers = sim.getResistanceResultReaderCallsForDeveloperVerification();
        if ("CONTINUITY".equals(modeId)) {
            double refreshed = adapter.measureResistance(red, black, mode);
            check(PowerDomainContract.finite(refreshed) && refreshed >= 0 &&
                    sim.getResistanceResultReaderCallsForDeveloperVerification() == readers + 1,
                "CONTINUITY refresh is accepted after natural RC cooldown");
        } else {
            DiodeMeasurementResult refreshed = adapter.measureDiode(red, black, mode);
            check(refreshed != null && PowerDomainContract.finite(refreshed.voltage) &&
                    PowerDomainContract.finite(refreshed.current) && refreshed.current >= .00001 &&
                    refreshed.voltage < 2.95,
                "DIODE refresh is accepted after natural RC cooldown");
        }
        check(sim.t > started, modeId + " cooldown refresh performs a fresh solver-time injection");
        restored(sim, graph);
        System.out.println("IN_CIRCUIT_RC_HELD mode=" + modeId + " postSample=" + afterSample +
            " simulatedSeconds=" + elapsed);
    }

    /** A finite over-compliance diode sample remains OL while its real RC load discharges. */
    private static void verifyDiodeOlWhileDischarging(MeterSim sim, GeneratedBoardInstance owner,
            Vector<CircuitElm> fixture) {
        ResistorElm load = resistor(102000, 100960, 102080, 100960, 100000);
        CapacitorElm capacitor = new CapacitorElm(102000, 101040);
        capacitor.setPosition(102000, 100960, 102080, 100960);
        capacitor.capacitance = 1e-6;
        capacitor.initialVoltage = 0;
        capacitor.reset();
        add(sim, fixture, load);
        add(sim, fixture, capacitor);
        owner.getPhysicalBoardRuntime().registerCapability(new LiteralEnergyReadiness(capacitor,
            "LITERAL_RC_OPEN_DIODE_ENERGY_CONTRACT"));
        settle(sim, owner);

        LiteralProbe red = new LiteralProbe(sim, endpoint(load, 0));
        LiteralProbe black = new LiteralProbe(sim, endpoint(load, 1));
        InstrumentMode diode = new InstrumentMode("DIODE");
        CircuitMeasurementAdapter adapter = new CircuitMeasurementAdapter(sim);
        check(adapter.getActiveMeasurementReadiness(red, black, diode) == ActiveMeasurementReadiness.READY,
            "high-resistance diode RC fixture starts admitted");
        Vector<CircuitElm> graph = new Vector<CircuitElm>(sim.elmList);
        double started = sim.t;
        DiodeMeasurementResult result = adapter.measureDiode(red, black, diode);
        double elapsed = sim.t - started;
        double expectedCurrent = 3.0 / (1000.0 + 100000.0);
        double expectedVoltage = expectedCurrent * 100000.0;
        check(result != null && PowerDomainContract.finite(result.voltage) &&
                PowerDomainContract.finite(result.current) &&
                Math.abs(result.voltage - expectedVoltage) < .01 &&
                Math.abs(result.current - expectedCurrent) < expectedCurrent * .01,
            "real 100k RC load matches the independent 3V/1k load-line oracle");
        check(DiodeTestInstrumentMode.isCompletedFiniteResult(result) &&
                !DiodeTestInstrumentMode.isUsableResult(result) &&
                Double.isNaN(DiodeTestInstrumentMode.primaryValueForResult(result)) &&
                result.voltage >= 2.95 && result.current >= .00001,
            "finite over-compliance sample stays OL without a finite diode-voltage primary");
        check(Math.abs(capacitor.getVoltageDiff()) > .25 &&
                adapter.getActiveMeasurementReadiness(red, black, diode) == ActiveMeasurementReadiness.DISCHARGE &&
                InstrumentController.canRetainCompletedMeasurementAtReadiness("DIODE", true,
                    ActiveMeasurementReadiness.DISCHARGE),
            "completed nonconducting diode sample remains retainable after its own injection leaves charge");
        check(Math.abs(elapsed - (TemporaryMeasurementSettlement.ACTIVE_SECONDS + sim.timeStep)) < 1e-10,
            "nonconducting diode acquisition stays inside the declared solver-time window");
        restored(sim, graph);

        double blockedAt = sim.t;
        double lastVoltage = sim.getLastDiodeMeasurementVoltageForDeveloperVerification();
        double lastCurrent = sim.getLastDiodeMeasurementCurrentForDeveloperVerification();
        check(adapter.measureDiode(red, black, diode) == null && sim.t == blockedAt &&
                Double.doubleToLongBits(sim.getLastDiodeMeasurementVoltageForDeveloperVerification()) ==
                    Double.doubleToLongBits(lastVoltage) &&
                Double.doubleToLongBits(sim.getLastDiodeMeasurementCurrentForDeveloperVerification()) ==
                    Double.doubleToLongBits(lastCurrent),
            "charged OL sample blocks a repeated diode injection without time or result-reader change");

        sim.solverExecutor.advanceFor(.5);
        owner.getPhysicalBoardRuntime().observeSimulationTime(sim.t);
        check(Math.abs(capacitor.getVoltageDiff()) < .25 &&
                adapter.getActiveMeasurementReadiness(red, black, diode) == ActiveMeasurementReadiness.READY,
            "bounded natural 100k-by-1uF cooldown restores diode admission");
        started = sim.t;
        DiodeMeasurementResult refreshed = adapter.measureDiode(red, black, diode);
        check(refreshed != null && DiodeTestInstrumentMode.isCompletedFiniteResult(refreshed) &&
                !DiodeTestInstrumentMode.isUsableResult(refreshed) &&
                Double.isNaN(DiodeTestInstrumentMode.primaryValueForResult(refreshed)) && sim.t > started,
            "fresh nonconducting diode sample is accepted after cooldown and remains OL-classified");
        restored(sim, graph);
        System.out.println("IN_CIRCUIT_RC_DIODE_OL voltage=" + result.voltage + " current=" +
            result.current + " residualVolts=" + capacitor.getVoltageDiff());
    }

    private static void verifyGeneratedFailurePrivacy(MeterSim sim, GeneratedBoardInstance owner) {
        IllegalStateException hidden = new IllegalStateException("Source fault did not lower device output");
        check(sim.reportCircuitFailure(hidden) == hidden &&
                "Runtime recovery failed: reload board".equals(sim.stopMessage) &&
                !sim.stopMessage.contains("Source fault") && !sim.simRunning &&
                sim.failedGeneratedRuntimeOwner == owner &&
                sim.getBoardPowerController().isElectricallyUnpowered(),
            "generated-board solver failure retains its cause while player recovery hides fault detail and isolates power");

        sim.troubleshootDebug = true;
        IllegalStateException visible = new IllegalStateException("Source fault did not lower device output");
        check(sim.reportCircuitFailure(visible) == visible &&
                "CircuitJS solver failed: Source fault did not lower device output".equals(sim.stopMessage) &&
                !sim.simRunning && sim.failedGeneratedRuntimeOwner == owner &&
                sim.getBoardPowerController().isElectricallyUnpowered(),
            "explicit developer debug retains actual generated-board failure detail and cause");
    }

    private static final class LiteralProbe implements ProbeTarget {
        private final MeterSim sim;
        CircuitPostMeasurementEndpoint endpoint;
        boolean valid = true;
        LiteralProbe(MeterSim sim, CircuitPostMeasurementEndpoint endpoint) {
            this.sim = sim; this.endpoint = endpoint;
        }
        public boolean isValid() { return valid && sim.containsElement(endpoint.getElement()); }
        public boolean isSameTarget(ProbeTarget other) { return other == this; }
        public Point getMarkerPoint() { return null; }
        public CircuitMeasurementEndpoint getMeasurementEndpoint() { return endpoint; }
    }

    private static final class LiteralEnergyReadiness implements ActiveMeasurementReadinessCapability {
        private final CapacitorElm capacitor;
        private final String capabilityId;
        LiteralEnergyReadiness(CapacitorElm capacitor) {
            this(capacitor, "LITERAL_RC_ENERGY_CONTRACT");
        }
        LiteralEnergyReadiness(CapacitorElm capacitor, String capabilityId) {
            this.capacitor = capacitor;
            this.capabilityId = capabilityId;
        }
        public String getCapabilityId() { return capabilityId; }
        public ActiveMeasurementReadiness getActiveMeasurementReadiness(CircuitPostMeasurementEndpoint red,
                CircuitPostMeasurementEndpoint black, BoardPowerState power, boolean allOff) {
            if (power != BoardPowerState.UNPOWERED || !allOff) return ActiveMeasurementReadiness.POWER_OFF;
            return Math.abs(capacitor.getVoltageDiff()) > .25 ?
                ActiveMeasurementReadiness.DISCHARGE : ActiveMeasurementReadiness.READY;
        }
    }
    /** Independent load-line bisection with literal model and meter specifications. */
    private static double diodeResistance(boolean forward) {
        double low = 0, high = 1;
        for (int iteration = 0; iteration < 80; iteration++) {
            double volts = (low + high) / 2;
            double current = 1.7143528192808883e-7 * (forward ?
                Math.exp(volts / (2 * .025865)) - 1 : 1 - Math.exp(-volts / (2 * .025865)));
            if (volts + current * 1000 > 1) high = volts; else low = volts;
        }
        double volts = (low + high) / 2;
        return volts * 1000 / (1 - volts);
    }
    private static void add(MeterSim sim, Vector<CircuitElm> fixture, CircuitElm element) {
        fixture.add(element); sim.elmList.add(element);
    }
    private static CircuitPostMeasurementEndpoint endpoint(CircuitElm element, int post) {
        return new CircuitPostMeasurementEndpoint(element, post);
    }
    private static void settle(MeterSim sim, GeneratedBoardInstance owner) {
        sim.solverExecutor.invalidate(); sim.analyzeCircuit(); sim.analyzeFlag = false;
        sim.solverExecutor.advanceSteps(8);
        owner.getPhysicalBoardRuntime().observeSimulationTime(sim.t);
        check(sim.stopMessage == null && sim.isGeneratedRuntimeSettled(), "literal graph settles through the actual solver");
    }
    private static double measure(MeterSim sim, CircuitPostMeasurementEndpoint red, CircuitPostMeasurementEndpoint black) {
        Vector<CircuitElm> graph = new Vector<CircuitElm>(sim.elmList);
        int readers = sim.getResistanceResultReaderCallsForDeveloperVerification();
        double value = sim.measureResistance(red, black, OHM);
        check(sim.getResistanceResultReaderCallsForDeveloperVerification() == readers + 1,
            "a real numeric or over-range transaction invokes the result reader exactly once");
        restored(sim, graph); return value;
    }
    private static void numeric(MeterSim sim, CircuitPostMeasurementEndpoint red, CircuitPostMeasurementEndpoint black,
            double expected, double tolerance, String reason) {
        double value = measure(sim, red, black);
        check(PowerDomainContract.finite(value) && Math.abs(value - expected) <= tolerance,
            reason + ": expected=" + expected + " actual=" + value);
    }
    private static void restored(MeterSim sim, Vector<CircuitElm> graph) {
        check(sim.elmList.equals(graph) && !sim.activeMeasurementOverlay && sim.stopMessage == null &&
            sim.isActiveMeasurementSolverRestoredForDeveloperVerification(), "actual source/reference elements and solver indexes restored");
    }
    private static void check(boolean result, String reason) { assertions++; if (!result) throw new AssertionError(reason); }
    private static final class OpaqueResistorElm extends ResistorElm {
        OpaqueResistorElm(int x, int y, int x2, int y2, double resistance) {
            super(x, y);
            setPosition(x, y, x2, y2);
            setResistance(resistance);
        }
    }
    private static final class MeterSim extends CirSim {
        int uiSliceSteps = 8, uiCalls;
        MeterSim() {
            gridSize = 16; gridMask = ~15; gridRound = 7; elmList = new Vector<CircuitElm>();
            adjustables = new Vector<Adjustable>(); undoStack = new Vector<String>(); redoStack = new Vector<String>();
            maxTimeStep = minTimeStep = timeStep = .00005; adjustTimeStep = false;
        }
        @Override void runCircuit(boolean didAnalyze) { uiCalls++; solverExecutor.advanceSteps(uiSliceSteps); }
        @Override void needAnalyze() { if (elmList != null && CircuitElm.sim == this) solverExecutor.invalidate(); analyzeFlag = true; }
        @Override void stop(String message, CircuitElm element) { stopMessage = message; circuitMatrix = null; stopElm = element; setSimRunning(false); analyzeFlag = false; }
        @Override public void setSimRunning(boolean running) { simRunning = running; }
        @Override void repaint() { }
        @Override void requestGeneratedBoardVerification() { }
        @Override void refreshBoardModificationControls() { }
        @Override void refreshChallengeInteractionState() { }
    }
}
