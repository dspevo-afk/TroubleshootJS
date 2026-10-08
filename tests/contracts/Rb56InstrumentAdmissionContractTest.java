package com.lushprojects.circuitjs1.client;

import java.lang.reflect.Field;
import java.util.Vector;

/** Actual instrument boundary only; no player geometry, fault or diagnostic admission is certified. */
public final class Rb56InstrumentAdmissionContractTest {
    private static final double DT = .00005;
    private static int assertions, activeOrientations, passiveOrientations;
    public static void main(String[] args) throws Exception {
        CirSim prior = CircuitElm.sim, singleton = CirSim.theSim;
        String diode = DiodeElm.lastModelName, zener = ZenerElm.lastZenerModelName, transistor = TransistorElm.lastModelName;
        int flags = MosfetElm.globalFlags; double beta = MosfetElm.lastBeta;
        NativeMeterCirSim sim = new NativeMeterCirSim(); Vector<CircuitElm> original = sim.elmList;
        Rb56PhysicalOwnerConstruction.Result f = null; Throwable failure = null;
        String phase = "CONSTRUCTION"; CircuitElm.sim = sim;
        try {
            f = Rb56PhysicalOwnerConstruction.build(Rb56Plan.reference(77), new UnqualifiedBehavior());
            check(f.runtime.getCapability(Rb56InstrumentAdmissionCapability.ID) instanceof Rb56InstrumentAdmissionCapability,
                "actual helper registers instrument policy before owner publication");
            sim.generatedBoardInstance = f.owner; sim.elmList = f.owner.getSimulationElements();
            sim.boardModificationController = new BoardModificationController(sim, f.owner);
            sim.getBoardPowerController().attach(f.owner.getExternalPowerBindings());
            f.runtime.installRegisteredCapabilities(sim, f.owner, sim.boardModificationController, 0); sim.a01MeasurementRunning = true;
            phase = "POWERED"; advance(sim, f, .4);
            PhysicalCapacitorPart cap = (PhysicalCapacitorPart)f.runtime.getInstalledPart("CBULK");
            check(Math.abs(cap.getElement().getVoltageDiff()) > .25, "actual powered graph charges bulk storage");
            sim.getBoardPowerController().setState(BoardPowerState.UNPOWERED);
            f.runtime.onBoardPowerStateChanged(BoardPowerState.UNPOWERED); sim.needAnalyze();
            phase = "CHARGED_OFF"; advance(sim, f, DT);
            CircuitPostMeasurementEndpoint cr = terminal(cap, 0), cb = terminal(cap, 1);
            check(f.runtime.getActiveMeasurementReadiness(cr, cb, BoardPowerState.UNPOWERED, true) == ActiveMeasurementReadiness.DISCHARGE,
                "service-energy API retains fresh solver-observed DISCHARGE independently of injection admission");
            Quiet charged = new Quiet(sim, f.owner);
            check(sim.getActiveMeasurementReadiness(cr, cb) == ActiveMeasurementReadiness.DISCHARGE && Double.isNaN(sim.measureResistance(cr, cb)) &&
                sim.measureDiode(cr, cb) == null, "charged actual capacitor cannot receive meter injection"); charged.verify(sim, f.owner);
            PhysicalSlotMutationProvider service = f.runtime.getMutationProvider("UAC");
            check(!service.isAvailable(WorkbenchOperation.forComponent(WorkbenchOperation.REMOVE, "UAC"), null),
                "actual charged OFF module remains unavailable for service");
            phase = "DISCHARGED_OFF"; advance(sim, f, 3);
            PhysicalConverterPart module = (PhysicalConverterPart)f.runtime.getInstalledPart("UAC");
            CircuitPostMeasurementEndpoint input = terminal(module, 0), returned = terminal(module, 1);
            check(f.runtime.getActiveMeasurementReadiness(input, returned, BoardPowerState.UNPOWERED, true).isReady() &&
                sim.getActiveMeasurementReadiness(input, returned) == ActiveMeasurementReadiness.UNSUPPORTED,
                "discharged service readiness does not inherit opaque-module instrument refusal");
            check(service.isAvailable(WorkbenchOperation.forComponent(WorkbenchOperation.REMOVE, "UAC"), null) &&
                service.removeInstalledPart(), "real discharged converter removal has no instrument-policy deadlock");
            advance(sim, f, DT); check(!module.isInstalled(), "the exact original module is loose in its inventory");
            phase = "CONVERTER_ACTIVE_PAIRS";
            for (int first = 0; first < 7; first++) for (int second = first + 1; second < 7; second++) {
                denyActive(sim, f.owner, terminal(module, first), terminal(module, second));
                denyActive(sim, f.owner, terminal(module, second), terminal(module, first));
            }
            phase = "CONVERTER_PASSIVE_ISOLATION";
            // Independent model terminal map: five primary pins and two secondary pins.
            for (int primary : new int[] {0, 1, 4, 5, 6}) for (int secondary : new int[] {2, 3}) {
                denyPassive(sim, f.owner, terminal(module, primary), terminal(module, secondary));
                denyPassive(sim, f.owner, terminal(module, secondary), terminal(module, primary));
            }
            phase = "LOOSE_RESISTOR";
            PhysicalResistorPart resistor = (PhysicalResistorPart)f.runtime.getInstalledPart("RPRIMARY");
            check(f.runtime.getMutationProvider("RPRIMARY").removeInstalledPart(), "remove actual discharged 4.7k resistor"); advance(sim, f, DT);
            for (int polarity = 0; polarity < 2; polarity++) {
                CircuitPostMeasurementEndpoint red = terminal(resistor, polarity), black = terminal(resistor, 1 - polarity);
                check(sim.getActiveMeasurementReadiness(red, black) == ActiveMeasurementReadiness.READY, "isolated loose resistor is supported");
                Vector<CircuitElm> graph = new Vector<CircuitElm>(sim.elmList); double before = sim.t;
                double ohms = sim.measureResistance(red, black);
                check(PowerDomainContract.finite(ohms) && Math.abs(ohms - 4700) < 1 &&
                    Math.abs(Math.abs(sim.getLastResistanceTestCurrentForDeveloperVerification()) - 1.0 / 5700) < 2e-8,
                    "actual 4.7k and independent 1V/(1k+4.7k) current oracle in each polarity");
                check(sim.t > before && sim.elmList.equals(graph) && !sim.activeMeasurementOverlay &&
                    sim.isActiveMeasurementSolverRestoredForDeveloperVerification() && sim.hasElectricallyNeutralResistanceReferenceForDeveloperVerification(),
                    "real meter source/reference and solver indexes restore after accepted solve");
                GeneratedRuntimeInvariant.verify(f.owner, sim.boardModificationController, sim.elmList); advance(sim, f, DT);
            }
            check(activeOrientations == 42 && passiveOrientations == 20, "exact complete independent converter pin-pair census");
        } catch (RuntimeException error) { failure = error; throw error;
        } catch (Error error) { failure = error; throw error;
        } catch (Exception error) { failure = error; throw error;
        } finally {
            Throwable cleanup = null; boolean disposed = true, overlayClear = !sim.activeMeasurementOverlay;
            try {
                sim.a01MeasurementRunning = false; sim.elmList = original; sim.generatedBoardInstance = null; sim.boardModificationController = null;
                try { sim.solverExecutor.retire(); sim.solverExecutor.invalidate(); } catch (Throwable error) { cleanup = error; }
                sim.circuitMatrix = null; sim.getBoardPowerController().detach();
                if (f != null) for (CircuitElm element : f.owner.getSimulationElements()) try { element.delete(); }
                catch (Throwable error) { disposed = false; if (cleanup == null) cleanup = error; else cleanup.addSuppressed(error); }
            } finally {
                DiodeElm.lastModelName = diode; ZenerElm.lastZenerModelName = zener; TransistorElm.lastModelName = transistor;
                MosfetElm.globalFlags = flags; MosfetElm.lastBeta = beta; CircuitElm.sim = prior; CirSim.theSim = singleton;
            }
            boolean clean = cleanup == null && disposed && overlayClear && original.isEmpty() && sim.elmList == original &&
                sim.generatedBoardInstance == null && sim.boardModificationController == null && sim.circuitMatrix == null &&
                !sim.solverExecutor.isUnavailable() && CircuitElm.sim == prior && CirSim.theSim == singleton;
            System.out.println("RB56_INSTRUMENT_CLEANUP {\"phase\":\"" + phase + "\",\"candidateDisposed\":" + disposed + ",\"clean\":" + clean + "}");
            if (failure != null) { if (cleanup != null) failure.addSuppressed(cleanup); if (!clean) failure.addSuppressed(new AssertionError("Instrument fixture cleanup failed")); }
            else if (!clean) throw new AssertionError("Instrument fixture cleanup failed", cleanup);
        }
        System.out.println("PASS: RB56 instrument admission contracts assertions=" + assertions +
            " activePairs=21 activeOrientations=42 passivePairs=10 passiveOrientations=20 realOhm=2 scope=DEVELOPER_BOUNDARY_ONLY");
    }
    private static void denyActive(NativeMeterCirSim sim, GeneratedBoardInstance owner,
            CircuitPostMeasurementEndpoint red, CircuitPostMeasurementEndpoint black) throws Exception {
        Quiet before = new Quiet(sim, owner);
        check(sim.getActiveMeasurementReadiness(red, black) == ActiveMeasurementReadiness.UNSUPPORTED, "opaque module pair explicitly unsupported");
        check(Double.isNaN(sim.measureResistance(red, black)) && sim.measureDiode(red, black) == null, "both actual active entry points refuse injection");
        before.verify(sim, owner); activeOrientations++;
    }
    private static void denyPassive(NativeMeterCirSim sim, GeneratedBoardInstance owner,
            CircuitPostMeasurementEndpoint red, CircuitPostMeasurementEndpoint black) throws Exception {
        Quiet before = new Quiet(sim, owner); MeasurementReferencePolicy.Result reference = sim.assessMeasurementReference(red, black);
        check(reference.getDecision() == MeasurementReferencePolicy.Decision.REJECTED && "ISOLATION_BOUNDARY".equals(reference.getReason()),
            "loose converter cross-domain reference is rejected rather than NOT_APPLICABLE");
        check(sim.measureDcVoltageResult(red, black).getStatus() == VoltageMeasurementResult.Status.REFERENCE_REJECTED &&
            sim.measureAcVoltage(red, black).getStatus() == VoltageMeasurementResult.Status.REFERENCE_REJECTED &&
            sim.observeDifferentialVoltage(red, black) == null, "DC/AC/scope refuse before burden or observation subscription");
        before.verify(sim, owner); passiveOrientations++;
    }
    private static CircuitPostMeasurementEndpoint terminal(PhysicalPart<?> part, int index) {
        return (CircuitPostMeasurementEndpoint)part.getTerminal(index).getEndpoint();
    }
    private static void advance(NativeMeterCirSim sim, Rb56PhysicalOwnerConstruction.Result f, double seconds) {
        if (sim.circuitMatrix == null || sim.analyzeFlag) { sim.solverExecutor.analyze(); sim.analyzeFlag = false; }
        for (int i = 0, n = (int)Math.round(seconds / DT); i < n; i++) sim.solverExecutor.advanceSteps(1);
        f.runtime.observeSimulationTime(sim.t);
        check(sim.stopMessage == null && sim.solverExecutor.isCurrent(sim.solverExecutor.observation(f.owner), f.owner), "actual accepted current-owner sample");
    }
    private static int subscriptions(NativeMeterCirSim sim) throws Exception {
        Field field = SolverTimeObservationService.class.getDeclaredField("subscriptions"); field.setAccessible(true);
        return ((Vector<?>)field.get(sim.solverTimeObservations)).size();
    }
    private static final class Quiet {
        final double time; final Vector<CircuitElm> graph; final Object observation; final int subscriptions;
        Quiet(NativeMeterCirSim sim, GeneratedBoardInstance owner) throws Exception {
            time = sim.t; graph = new Vector<CircuitElm>(sim.elmList); observation = sim.solverExecutor.observation(owner); subscriptions = subscriptions(sim);
        }
        void verify(NativeMeterCirSim sim, GeneratedBoardInstance owner) throws Exception {
            check(sim.t == time && sim.elmList.equals(graph) && !sim.activeMeasurementOverlay && sim.stopMessage == null &&
                sim.solverExecutor.observation(owner) == observation && subscriptions(sim) == subscriptions,
                "denial does not advance solver, append stimulus, open overlay, replace receipt or leak scope subscription");
        }
    }
    private static void check(boolean condition, String message) { assertions++; if (!condition) throw new AssertionError(message); }
    private static final class UnqualifiedBehavior implements GeneratedChallengeBehaviorContract {
        public void verifyHealthy(GeneratedBoardInstance owner, BoardPowerState state) { throw new UnsupportedOperationException("Instrument fixture is not customer qualification"); }
        public void verifyFaulted(GeneratedBoardInstance owner, BoardModificationController changes, BoardPowerState state) { throw new UnsupportedOperationException("No fault selected"); }
        public GeneratedRepairStatus getRepairStatus(GeneratedBoardInstance owner, BoardModificationController changes, BoardPowerState state, boolean overlay) { return GeneratedRepairStatus.STILL_FAULTED_OR_NONFUNCTIONAL; }
        public boolean isFunctionallyRepaired(GeneratedBoardInstance owner, BoardModificationController changes, BoardPowerState state, boolean overlay) { return false; }
    }
    /** Adapt only widgetless native scheduling; keep the production stimulus/reader/restoration transaction. */
    private static final class NativeMeterCirSim extends CirSim {
        NativeMeterCirSim() { gridSize = 16; gridMask = ~15; gridRound = 7; elmList = new Vector<CircuitElm>(); adjustables = new Vector<Adjustable>();
            undoStack = new Vector<String>(); redoStack = new Vector<String>(); maxTimeStep = minTimeStep = timeStep = DT; adjustTimeStep = false; }
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
