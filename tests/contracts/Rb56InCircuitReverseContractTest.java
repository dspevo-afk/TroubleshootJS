package com.lushprojects.circuitjs1.client;

import java.util.Vector;

/** Native reproduction of the seed -1004 in-circuit resistance polarity path. */
public final class Rb56InCircuitReverseContractTest {
    private static final long SEED = -1004L;
    private static final double MAX_TIME_STEP = .0004;
    private static final double RUN_CADENCE_SECONDS = .25;
    private static final double POWERED_SECONDS = 10.829705;
    private static final double DISCHARGE_SECONDS = 3.0;
    private static final InstrumentMode OHM = new InstrumentMode("RESISTANCE");
    private static int assertions;

    public static void main(String[] args) throws Exception {
        CirSim prior = CircuitElm.sim, singleton = CirSim.theSim;
        String diodeModel = DiodeElm.lastModelName, zenerModel = ZenerElm.lastZenerModelName;
        String transistorModel = TransistorElm.lastModelName;
        int mosfetFlags = MosfetElm.globalFlags;
        double mosfetBeta = MosfetElm.lastBeta;
        NativeCadenceCirSim sim = new NativeCadenceCirSim();
        Vector<CircuitElm> original = sim.elmList;
        Rb56PhysicalOwnerConstruction.Result fixture = null;
        Throwable failure = null;
        String phase = "CONSTRUCTION";
        CircuitElm.sim = sim;
        try {
            Rb56Plan plan = Rb56Plan.resolve(SEED);
            check(plan.seed == SEED, "the exact public seed resolves without remapping");
            fixture = Rb56PhysicalOwnerConstruction.build(plan, new UnqualifiedBehavior());
            check(fixture.plan == plan,
                "the replay retains its exact resolved -1004 RB56 electrical plan");

            sim.generatedBoardInstance = fixture.owner;
            sim.elmList = fixture.owner.getSimulationElements();
            sim.boardModificationController = new BoardModificationController(sim, fixture.owner);
            sim.getBoardPowerController().attach(fixture.owner.getExternalPowerBindings());
            fixture.runtime.installRegisteredCapabilities(sim, fixture.owner,
                sim.boardModificationController, 0);
            sim.a01MeasurementRunning = true;
            sim.analyzeCircuit();
            sim.analyzeFlag = false;
            check(sim.getBoardPowerController().getState() == BoardPowerState.POWERED,
                "the actual external RB56 sources start connected");

            phase = "POWERED_STEADY_STATE";
            advanceFor(sim, fixture, POWERED_SECONDS);
            PhysicalResistorPart lowReference =
                (PhysicalResistorPart)fixture.runtime.getInstalledPart("RREF_L");
            check(lowReference != null && lowReference.isInstalled(),
                "seed -1004 retains the installed low-reference resistor");
            CircuitPostMeasurementEndpoint lowLead1 = boardPadEndpoint(fixture.owner,
                lowReference, 0);
            CircuitPostMeasurementEndpoint lowLead2 = boardPadEndpoint(fixture.owner,
                lowReference, 1);

            Vector<CircuitElm> liveGraph = new Vector<CircuitElm>(sim.elmList);
            double onTime = sim.t;
            ActiveMeasurementReadiness poweredReadiness =
                sim.getActiveMeasurementReadiness(lowLead1, lowLead2, OHM);
            double poweredReading = sim.measureResistance(lowLead1, lowLead2, OHM);
            check(poweredReadiness == ActiveMeasurementReadiness.POWER_OFF &&
                Double.isNaN(poweredReading) && sim.t == onTime && sim.elmList.equals(liveGraph),
                "in-circuit OHM refuses while the actual sources remain ON without injecting a meter source");

            phase = "POWER_OFF_DISCHARGE";
            sim.getBoardPowerController().setState(BoardPowerState.UNPOWERED);
            fixture.runtime.onBoardPowerStateChanged(BoardPowerState.UNPOWERED);
            sim.needAnalyze();
            sim.analyzeCircuit();
            sim.analyzeFlag = false;
            advanceFor(sim, fixture, .01);
            ActiveMeasurementReadiness initialOffReadiness =
                sim.getActiveMeasurementReadiness(lowLead1, lowLead2, OHM);
            double initialOffReading = sim.measureResistance(lowLead1, lowLead2, OHM);
            check(initialOffReadiness == ActiveMeasurementReadiness.DISCHARGE &&
                Double.isNaN(initialOffReading),
                "switching OFF exposes the actual residual-energy DISCHARGE guard");

            advanceFor(sim, fixture, DISCHARGE_SECONDS);
            ActiveMeasurementReadiness settledReadiness =
                sim.getActiveMeasurementReadiness(lowLead1, lowLead2, OHM);
            check(settledReadiness == ActiveMeasurementReadiness.READY,
                "the real powered-off graph settles before the first resistance read");

            phase = "FORWARD_RESISTANCE";
            double beforeForward = sim.t;
            Vector<CircuitElm> beforeForwardGraph = new Vector<CircuitElm>(sim.elmList);
            double forwardOhms = sim.measureResistance(lowLead1, lowLead2, OHM);
            boolean forwardCleanup = cleanupState(sim, fixture, beforeForwardGraph);
            check(PowerDomainContract.finite(forwardOhms) && forwardOhms > 0,
                "the actual installed RREF_L network returns a finite positive forward resistance");
            check(sim.t > beforeForward && forwardCleanup &&
                sim.getBoardPowerController().isElectricallyUnpowered(),
                "forward read advances CircuitJS then restores its graph and leaves the board OFF");
            GeneratedRuntimeInvariant.verify(fixture.owner,
                sim.boardModificationController, sim.elmList);
            System.out.println("RB56_REVERSE_PROBE_STEP {\"seed\":\"" + Long.toString(SEED) +
                "\",\"phase\":\"FORWARD_RESISTANCE\",\"pads\":\"RREF_L.1,RREF_L.2\"" +
                ",\"readiness\":\"" + settledReadiness.name() + "\",\"ohms\":" + number(forwardOhms) +
                ",\"timeBefore\":" + number(beforeForward) + ",\"timeAfter\":" + number(sim.t) +
                ",\"timeStep\":" + number(sim.timeStep) + ",\"cleanup\":" + forwardCleanup + "}");

            phase = "SAME_POST_INTERMEDIATE";
            double beforeSamePost = sim.t;
            Vector<CircuitElm> beforeSamePostGraph = new Vector<CircuitElm>(sim.elmList);
            ActiveMeasurementReadiness samePostReadiness =
                sim.getActiveMeasurementReadiness(lowLead2, lowLead2, OHM);
            double samePostOhms = sim.measureResistance(lowLead2, lowLead2, OHM);
            boolean samePostCleanup = cleanupState(sim, fixture, beforeSamePostGraph);
            check(samePostReadiness == ActiveMeasurementReadiness.READY &&
                PowerDomainContract.finite(samePostOhms) && Math.abs(samePostOhms) <= .001,
                "the intermediate red/black coincidence on RREF_L.2 is a real zero-ohm same-post read");
            check(sim.t > beforeSamePost && samePostCleanup &&
                sim.getBoardPowerController().isElectricallyUnpowered(),
                "same-post read cleans up its real temporary source before polarity reversal");
            System.out.println("RB56_REVERSE_PROBE_STEP {\"seed\":\"" + Long.toString(SEED) +
                "\",\"phase\":\"SAME_POST_INTERMEDIATE\",\"pads\":\"RREF_L.2,RREF_L.2\"" +
                ",\"readiness\":\"" + samePostReadiness.name() + "\",\"ohms\":" + number(samePostOhms) +
                ",\"timeBefore\":" + number(beforeSamePost) + ",\"timeAfter\":" +
                number(sim.t) + ",\"timeStep\":" + number(sim.timeStep) +
                ",\"cleanup\":" + samePostCleanup + "}");

            phase = "REVERSED_RESISTANCE";
            double beforeReverse = sim.t;
            Vector<CircuitElm> beforeReverseGraph = new Vector<CircuitElm>(sim.elmList);
            double reverseOhms = Double.NaN;
            Throwable reverseFailure = null;
            try {
                // Same two real board posts, swapped to reproduce the UI red/black reversal.
                reverseOhms = sim.measureResistance(lowLead2, lowLead1, OHM);
            } catch (RuntimeException error) {
                reverseFailure = error;
            } catch (Error error) {
                reverseFailure = error;
            }
            boolean reverseCleanup = cleanupState(sim, fixture, beforeReverseGraph);
            ActiveMeasurementReadiness reverseReadiness =
                sim.getActiveMeasurementReadiness(lowLead2, lowLead1, OHM);
            System.out.println("RB56_REVERSE_PROBE_STEP {\"seed\":\"" + Long.toString(SEED) +
                "\",\"phase\":\"REVERSED_RESISTANCE\",\"pads\":\"RREF_L.2,RREF_L.1\"" +
                ",\"readiness\":\"" + reverseReadiness.name() + "\",\"ohms\":" + number(reverseOhms) +
                ",\"failureType\":\"" + quote(reverseFailure == null ? null :
                    reverseFailure.getClass().getName()) + "\",\"failureMessage\":\"" +
                    quote(reverseFailure == null ? null : reverseFailure.getMessage()) +
                "\",\"timeBefore\":" + number(beforeReverse) + ",\"timeAfter\":" +
                number(sim.t) + ",\"timeStep\":" + number(sim.timeStep) +
                ",\"subIteration\":" + sim.subIterations + ",\"cleanup\":" + reverseCleanup + ",\"boardOff\":" +
                sim.getBoardPowerController().isElectricallyUnpowered() + "}");
            check(reverseFailure == null,
                "reversing the real in-circuit OHM probes must not trigger an E02 solver failure: " +
                    describe(reverseFailure));
            check(PowerDomainContract.finite(reverseOhms) && reverseOhms > 0,
                "the reversed RREF_L board measurement returns a finite positive reading");
            check(sim.t > beforeReverse && reverseCleanup &&
                sim.getBoardPowerController().isElectricallyUnpowered() &&
                reverseReadiness == ActiveMeasurementReadiness.READY,
                "reversed read restores solver/source state, keeps the board OFF and unlocks measurements");
            GeneratedRuntimeInvariant.verify(fixture.owner,
                sim.boardModificationController, sim.elmList);
        } catch (RuntimeException error) {
            failure = error;
            throw error;
        } catch (Error error) {
            failure = error;
            throw error;
        } finally {
            Throwable cleanupFailure = null;
            try {
                sim.a01MeasurementRunning = false;
                sim.generatedBoardInstance = null;
                sim.boardModificationController = null;
                try {
                    sim.solverExecutor.retire();
                    sim.solverExecutor.invalidate();
                } catch (Throwable error) { cleanupFailure = error; }
                sim.circuitMatrix = null;
                sim.getBoardPowerController().detach();
                if (fixture != null) for (CircuitElm element : fixture.owner.getSimulationElements()) {
                    try { element.delete(); }
                    catch (Throwable error) {
                        if (cleanupFailure == null) cleanupFailure = error;
                        else cleanupFailure.addSuppressed(error);
                    }
                }
                sim.elmList = original;
            } finally {
                DiodeElm.lastModelName = diodeModel;
                ZenerElm.lastZenerModelName = zenerModel;
                TransistorElm.lastModelName = transistorModel;
                MosfetElm.globalFlags = mosfetFlags;
                MosfetElm.lastBeta = mosfetBeta;
                CircuitElm.sim = prior;
                CirSim.theSim = singleton;
            }
            boolean clean = cleanupFailure == null && !sim.activeMeasurementOverlay &&
                sim.generatedBoardInstance == null && sim.boardModificationController == null &&
                sim.elmList == original && original.isEmpty() && sim.circuitMatrix == null &&
                !sim.solverExecutor.isUnavailable() && CircuitElm.sim == prior && CirSim.theSim == singleton;
            System.out.println("RB56_REVERSE_PROBE_CLEANUP {\"phase\":\"" + phase +
                "\",\"clean\":" + clean + "}");
            if (failure != null) {
                if (cleanupFailure != null) failure.addSuppressed(cleanupFailure);
                if (!clean) failure.addSuppressed(new AssertionError("reverse-probe fixture cleanup failed"));
            } else if (!clean) throw new AssertionError("reverse-probe fixture cleanup failed", cleanupFailure);
        }
        System.out.println("PASS: RB56 in-circuit reverse resistance assertions=" + assertions +
            " seed=" + Long.toString(SEED) + " cadenceSeconds=" + RUN_CADENCE_SECONDS +
            " scope=DEVELOPER_NATIVE_REPRODUCTION_ONLY");
    }

    private static CircuitPostMeasurementEndpoint boardPadEndpoint(GeneratedBoardInstance owner,
            PhysicalPart<?> part, int index) {
        PhysicalPartTerminal terminal = part.getTerminal(index);
        String padId = part.getId() + "." + terminal.getTerminalName();
        CircuitMeasurementEndpoint endpoint = owner.getSimulationBindings().getEndpoint(padId);
        GeneratedComponentConnectionBinding binding = owner.getConnectionBindings().get(part.getId(), padId);
        check(endpoint instanceof CircuitPostMeasurementEndpoint && binding != null &&
            GeneratedComponentConnectionBindings.sameEndpoint(
                (CircuitPostMeasurementEndpoint)endpoint, binding.getBoardEndpoint()) &&
            GeneratedComponentConnectionBindings.sameEndpoint(
                (CircuitPostMeasurementEndpoint)binding.getComponentEndpoint(),
                (CircuitPostMeasurementEndpoint)terminal.getEndpoint()),
            "installed board pad resolves through its real lead and physical terminal: " + padId);
        return (CircuitPostMeasurementEndpoint)endpoint;
    }

    private static void advanceFor(NativeCadenceCirSim sim,
            Rb56PhysicalOwnerConstruction.Result fixture, double duration) {
        double target = sim.t + duration;
        int slices = 0;
        while (sim.t + 1e-10 < target && slices++ < 128) {
            double slice = Math.min(RUN_CADENCE_SECONDS, target - sim.t);
            double before = sim.t;
            sim.solverExecutor.advanceFor(slice);
            fixture.runtime.observeSimulationTime(sim.t);
            check(sim.t > before && sim.stopMessage == null &&
                sim.solverExecutor.isCurrent(sim.solverExecutor.observation(fixture.owner), fixture.owner),
                "bounded physical-time advance retains the current CircuitJS owner");
        }
        check(slices <= 128 && Math.abs(sim.t - target) <= MAX_TIME_STEP + 1e-9,
            "bounded advance reaches its requested physical-time target");
    }

    private static boolean cleanupState(NativeCadenceCirSim sim,
            Rb56PhysicalOwnerConstruction.Result fixture, Vector<CircuitElm> graph) {
        return sim.elmList.equals(graph) && !sim.activeMeasurementOverlay && sim.stopMessage == null &&
            sim.isActiveMeasurementSolverRestoredForDeveloperVerification() &&
            sim.getBoardPowerController().isElectricallyUnpowered() &&
            sim.solverExecutor.isCurrent(sim.solverExecutor.observation(fixture.owner), fixture.owner);
    }

    private static String number(double value) {
        return PowerDomainContract.finite(value) ? Double.toString(value) : "null";
    }

    private static String quote(String value) {
        if (value == null) return "";
        return value.replace("\\", "\\\\").replace("\"", "\\\"")
            .replace("\r", "\\r").replace("\n", "\\n");
    }

    private static String describe(Throwable error) {
        return error == null ? "none" : error.getClass().getName() + ": " + String.valueOf(error.getMessage());
    }

    private static void check(boolean condition, String message) {
        assertions++;
        if (!condition) throw new AssertionError(message);
    }

    private static final class UnqualifiedBehavior implements GeneratedChallengeBehaviorContract {
        public void verifyHealthy(GeneratedBoardInstance owner, BoardPowerState state) {
            throw new UnsupportedOperationException("Developer electrical fixture only");
        }
        public void verifyFaulted(GeneratedBoardInstance owner, BoardModificationController changes,
                BoardPowerState state) {
            throw new UnsupportedOperationException("Developer electrical fixture only");
        }
        public GeneratedRepairStatus getRepairStatus(GeneratedBoardInstance owner,
                BoardModificationController changes, BoardPowerState state, boolean overlay) {
            return GeneratedRepairStatus.STILL_FAULTED_OR_NONFUNCTIONAL;
        }
        public boolean isFunctionallyRepaired(GeneratedBoardInstance owner,
                BoardModificationController changes, BoardPowerState state, boolean overlay) {
            return false;
        }
    }

    /** Actual CircuitJS steps, bounded by physical time rather than a tiny fixed step count. */
    private static final class NativeCadenceCirSim extends CirSim {
        NativeCadenceCirSim() {
            gridSize = 16; gridMask = ~15; gridRound = 7;
            elmList = new Vector<CircuitElm>();
            adjustables = new Vector<Adjustable>();
            undoStack = new Vector<String>(); redoStack = new Vector<String>();
            maxTimeStep = timeStep = MAX_TIME_STEP;
            minTimeStep = 50e-12;
            adjustTimeStep = true;
        }
        @Override void runCircuit(boolean didAnalyze) {
            solverExecutor.advanceFor(RUN_CADENCE_SECONDS);
        }
        @Override void needAnalyze() {
            if (elmList != null && CircuitElm.sim == this) solverExecutor.invalidate();
            analyzeFlag = true;
        }
        @Override void stop(String message, CircuitElm element) {
            stopMessage = message; circuitMatrix = null; stopElm = element;
            setSimRunning(false); analyzeFlag = false;
        }
        @Override public void setSimRunning(boolean running) { simRunning = running; }
        @Override void repaint() { }
        @Override void requestGeneratedBoardVerification() { }
        @Override void refreshBoardModificationControls() { }
        @Override void refreshChallengeInteractionState() { }
    }
}
