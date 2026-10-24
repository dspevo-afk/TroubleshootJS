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
            owner = new LedIndicatorGenerator().generate(3);
            sim.generatedBoardInstance = owner;
            sim.elmList = new Vector<CircuitElm>(owner.getSimulationElements());
            sim.boardModificationController = new BoardModificationController(sim, owner);
            sim.getBoardPowerController().attach(owner.getExternalPowerBindings());
            owner.getPhysicalBoardRuntime().installRegisteredCapabilities(sim, owner, sim.boardModificationController, 0);
            sim.getBoardPowerController().setState(BoardPowerState.UNPOWERED);
            owner.getPhysicalBoardRuntime().onBoardPowerStateChanged(BoardPowerState.UNPOWERED);
            sim.a01MeasurementRunning = true;

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
        settle(sim, owner);
        LiteralProbe red = new LiteralProbe(sim, endpoint(load, 0));
        LiteralProbe black = new LiteralProbe(sim, endpoint(load, 1));
        CircuitMeasurementAdapter adapter = new CircuitMeasurementAdapter(sim);
        check(adapter.getActiveMeasurementReadiness(red, black, OHM) == ActiveMeasurementReadiness.READY,
            "discharged literal RC graph admits the first real injection");
        Vector<CircuitElm> graph = new Vector<CircuitElm>(sim.elmList);
        int readers = sim.getResistanceResultReaderCallsForDeveloperVerification();
        double reading = adapter.measureResistance(red, black, OHM);
        check(PowerDomainContract.finite(reading) && reading >= 0 &&
                sim.getResistanceResultReaderCallsForDeveloperVerification() == readers + 1,
            "completed real RC sample survives its own post-transaction residual energy");
        restored(sim, graph);
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
        LiteralEnergyReadiness(CapacitorElm capacitor) { this.capacitor = capacitor; }
        public String getCapabilityId() { return "LITERAL_RC_ENERGY_CONTRACT"; }
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
    private static final class MeterSim extends CirSim {
        MeterSim() {
            gridSize = 16; gridMask = ~15; gridRound = 7; elmList = new Vector<CircuitElm>();
            adjustables = new Vector<Adjustable>(); undoStack = new Vector<String>(); redoStack = new Vector<String>();
            maxTimeStep = minTimeStep = timeStep = .00005; adjustTimeStep = false;
        }
        @Override void runCircuit(boolean didAnalyze) { solverExecutor.advanceSteps(8); }
        @Override void needAnalyze() { if (elmList != null && CircuitElm.sim == this) solverExecutor.invalidate(); analyzeFlag = true; }
        @Override void stop(String message, CircuitElm element) { stopMessage = message; circuitMatrix = null; stopElm = element; setSimRunning(false); analyzeFlag = false; }
        @Override public void setSimRunning(boolean running) { simRunning = running; }
        @Override void repaint() { }
        @Override void requestGeneratedBoardVerification() { }
        @Override void refreshBoardModificationControls() { }
        @Override void refreshChallengeInteractionState() { }
    }
}
