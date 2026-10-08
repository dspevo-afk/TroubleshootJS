package com.lushprojects.circuitjs1.client;

import java.io.PrintStream;
import java.util.HashSet;
import java.util.TreeMap;
import java.util.Vector;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Actual serviced owner pilot; geometry, diagnostics, faults and player admission are unqualified. */
public final class Rb56PhysicalOwnerContractTest {
    private static final double DT = .00005;
    private static int assertions;
    public static void main(String[] args) {
        PrintStream original = System.err;
        RoutineConvergenceStream routine = new RoutineConvergenceStream(original);
        try {
            System.setErr(routine);
            run(Rb56Plan.configured(17, 1, Rb56Plan.ReferenceArrangement.SHARED_DIRECT, true, false, 1, 0, false), 40, false);
            run(Rb56Plan.reference(77), 55, true);
            run(Rb56Plan.configured(42, 2, Rb56Plan.ReferenceArrangement.SHARED_HYSTERETIC, true, true, 3, 3, true), 60, false);
            System.out.println("PASS: RB56 physical-owner contracts assertions=" + assertions + " cases=3 scope=DEVELOPER_PHYSICAL_OWNER_ONLY");
        } finally { System.setErr(original); routine.report(); }
    }

    private static void run(Rb56Plan plan, int packages, boolean service) {
        CirSim prior = CircuitElm.sim, priorSingleton = CirSim.theSim;
        String priorDiode = DiodeElm.lastModelName, priorZener = ZenerElm.lastZenerModelName, priorTransistor = TransistorElm.lastModelName;
        int priorFlags = MosfetElm.globalFlags; double priorBeta = MosfetElm.lastBeta;
        TestCirSim sim = new TestCirSim(); Vector<CircuitElm> original = sim.elmList;
        Rb56PhysicalOwnerConstruction.Result fixture = null;
        Throwable failure = null; String phase = "CONSTRUCTION"; long start = System.nanoTime();
        CircuitElm.sim = sim;
        try {
            fixture = Rb56PhysicalOwnerConstruction.build(plan, new UnqualifiedBehavior());
            sim.generatedBoardInstance = fixture.owner; sim.elmList = fixture.owner.getSimulationElements();
            sim.boardModificationController = new BoardModificationController(sim, fixture.owner);
            sim.getBoardPowerController().attach(fixture.owner.getExternalPowerBindings());
            fixture.runtime.installRegisteredCapabilities(sim, fixture.owner, sim.boardModificationController, 0);
            sim.a01MeasurementRunning = true;
            structural(fixture, packages);
            phase = "POWERED_LOW"; advance(sim, fixture, .4);
            check(voltage(fixture.owner, "COUT.+", "COUT.-") > 9 && voltage(fixture.owner, "U1.OUTPUT", "U1.RETURN") > 4.5,
                "actual serviced converter/regulator graph energizes both control rails");
            report(sim, fixture, phase);
            for (String channel : plan.channels()) fixture.tail.sensorSources.get(channel).configure(5, .25);
            sim.needAnalyze(); phase = "POWERED_HIGH"; advance(sim, fixture, .03);
            for (String channel : plan.channels()) {
                ServiceRelayElm relay = ((PhysicalRelayPart)fixture.runtime.getInstalledPart("K" + channel)).getElement();
                check(Math.abs(relay.coilCurrent) > RelayOutputBehavior.DISCHARGED_AMPS,
                    "actual installed relay winding contains current before supply isolation");
            }
            report(sim, fixture, phase);
            PhysicalSlotMutationProvider module = fixture.runtime.getMutationProvider("UAC"), relay = fixture.runtime.getMutationProvider("KA");
            sim.getBoardPowerController().setState(BoardPowerState.UNPOWERED);
            fixture.runtime.onBoardPowerStateChanged(BoardPowerState.UNPOWERED); sim.needAnalyze();
            check(!module.isAvailable(WorkbenchOperation.forComponent(WorkbenchOperation.REMOVE, "UAC"), null) &&
                !relay.isAvailable(WorkbenchOperation.forComponent(WorkbenchOperation.REMOVE, "KA"), null),
                "OFF transition cannot authorize module or relay service with a stale observation");
            phase = "CHARGED_OFF"; advance(sim, fixture, DT);
            check(Math.abs(((PhysicalCapacitorPart)fixture.runtime.getInstalledPart("CBULK")).getElement().getVoltageDiff()) > .25,
                "OFF retains the actual bulk charge");
            check(!module.isAvailable(WorkbenchOperation.forComponent(WorkbenchOperation.REMOVE, "UAC"), null) &&
                !relay.isAvailable(WorkbenchOperation.forComponent(WorkbenchOperation.REMOVE, "KA"), null),
                "fresh but charged OFF observation still denies installed module/relay service");
            report(sim, fixture, phase);
            phase = "DISCHARGED_OFF"; advance(sim, fixture, 3);
            check(fixture.owner.getExternalPowerBindings().areAllDisconnected(), "every actual source is isolated");
            check(new Rb56Generator.RelayGuard().isDischarged(fixture.owner, "KA"),
                "fresh current installed capacitor, L/Rw and relay stores permit relay service");
            check(module.isAvailable(WorkbenchOperation.forComponent(WorkbenchOperation.REMOVE, "UAC"), null) &&
                relay.isAvailable(WorkbenchOperation.forComponent(WorkbenchOperation.REMOVE, "KA"), null),
                "module/relay service becomes available after real accepted discharge without reset");
            check(!new Rb56Generator.RelayGuard().isDischarged(fixture.owner, "RPRIMARY"),
                "relay guard rejects a non-relay occupied slot");
            report(sim, fixture, phase);
            if (service) {
                fixture.runtime.getSessionHistory().start();
                for (String id : new String[] {"UAC", "CBULK", "COUT", "CBIAS", "CFB", "L1", "DZ1", "UFB", "F1", "CIN", "C5", "U2A", "KA"}) {
                    phase = "SERVICE_" + id; roundTrip(sim, fixture, id);
                }
                check(fixture.runtime.getSessionHistory().size() == 65,
                    "only five successful actions per part enter the existing session vocabulary; rollback does not");
                report(sim, fixture, "SERVICE_COMPLETE");
            }
            GeneratedRuntimeInvariant.verify(fixture.owner, sim.boardModificationController, sim.elmList);
        } catch (RuntimeException error) { failure = error; throw error;
        } catch (Error error) { failure = error; throw error;
        } finally {
            if (failure != null) System.out.println("RB56_PHYSICAL_FAILURE {\"packages\":" + packages + ",\"phase\":\"" + phase +
                "\",\"acceptedTime\":" + number(sim.t) + ",\"subIterations\":" + sim.subIterations + "}");
            Throwable cleanup = null; boolean disposed = true;
            try {
                PhysicalMutationScope.clearFailureHookForDeveloperVerification(); sim.a01MeasurementRunning = false;
                sim.elmList = original; sim.generatedBoardInstance = null; sim.boardModificationController = null;
                try { sim.solverExecutor.retire(); sim.solverExecutor.invalidate(); }
                catch (RuntimeException error) { cleanup = error; } catch (Error error) { cleanup = error; }
                sim.circuitMatrix = null; sim.getBoardPowerController().detach();
                if (fixture != null) for (CircuitElm element : fixture.owner.getSimulationElements()) try { element.delete(); }
                catch (RuntimeException error) { disposed = false; if (cleanup == null) cleanup = error; else cleanup.addSuppressed(error); }
                catch (Error error) { disposed = false; if (cleanup == null) cleanup = error; else cleanup.addSuppressed(error); }
            } finally {
                DiodeElm.lastModelName = priorDiode; ZenerElm.lastZenerModelName = priorZener; TransistorElm.lastModelName = priorTransistor;
                MosfetElm.globalFlags = priorFlags; MosfetElm.lastBeta = priorBeta;
                CircuitElm.sim = prior; CirSim.theSim = priorSingleton;
            }
            boolean clean = cleanup == null && disposed && original.isEmpty() && sim.elmList == original &&
                sim.generatedBoardInstance == null && sim.boardModificationController == null && sim.circuitMatrix == null &&
                !sim.solverExecutor.isUnavailable() && CircuitElm.sim == prior && CirSim.theSim == priorSingleton;
            System.out.println("RB56_PHYSICAL_CLEANUP {\"packages\":" + packages + ",\"phase\":\"" + phase +
                "\",\"elapsedSeconds\":" + number((System.nanoTime() - start) / 1e9) + ",\"candidateDisposed\":" + disposed +
                ",\"rawGraphRestored\":" + (sim.elmList == original && original.isEmpty()) + ",\"clean\":" + clean + "}");
            if (failure != null) { if (cleanup != null) failure.addSuppressed(cleanup); if (!clean) failure.addSuppressed(new AssertionError("Physical owner cleanup failed")); }
            else if (cleanup != null || !clean) throw new AssertionError("Physical owner cleanup failed", cleanup);
        }
    }

    private static void structural(Rb56PhysicalOwnerConstruction.Result f, int packages) {
        check(f.plan.physicalPackageCount() == packages && f.runtime.getSlots().size() == packages,
            "declared representative has exactly its actual mounted package population");
        check(f.owner.isDeveloperOnlyFaultRoute() && f.owner.getPcbLayout() == null && f.owner.getFaultCandidates().isEmpty(),
            "owner remains an explicit geometry/diagnostic/fault-unqualified developer fixture");
        HashSet<String> capabilityIds = new HashSet<String>();
        for (PhysicalBoardRuntimeCapability capability : f.runtime.getCapabilities())
            check(capabilityIds.add(capability.getCapabilityId()), "distinct capability owner identity");
        int grounds = 0; for (CircuitElm element : f.owner.getSimulationElements()) if (element instanceof GroundElm) grounds++;
        check(grounds == 1, "one actual numerical reference does not bond the isolated domains");
        for (Rb56Plan.Part declared : f.plan.parts()) {
            PhysicalPart<?> part = f.runtime.getInstalledPart(declared.id);
            check(part != null && part.isInstalled() && part.getBoardSlot() == f.runtime.getSlot(declared.id) &&
                part.getPackage().isEquivalentTo(declared.physicalPackage), "exact mounted slot/package " + declared.id);
            check(f.runtime.getWorkbenchPartsProvider(declared.id) != null && f.runtime.getMutationProvider(declared.id) != null,
                "installed actual service and inventory owner " + declared.id);
            for (CircuitElm element : part.getElectricalBacking().getCircuitElements())
                check(f.owner.getSimulationElements().contains(element), "physical backing belongs to canonical graph " + declared.id);
        }
        PhysicalConverterPart module = (PhysicalConverterPart)f.runtime.getInstalledPart("UAC");
        String[] ids = {"IN+", "IN-", "PRE_L+", "OUT-", "EN", "FB", "BIAS"};
        for (int i = 0; i < 7; i++) endpoint(module.getTerminal(i).getEndpoint(), i == 6 ? module.getModule().bias : module.getModule().converter,
            i == 6 ? 2 : i, "independent seven-terminal module map " + ids[i]);
        PhysicalCapacitorPart output = (PhysicalCapacitorPart)f.runtime.getInstalledPart("COUT");
        check(output.getElement() == f.power.stage.outputCap && output.getEsrElement() == f.power.stage.capacitorEsr,
            "output capacitor retains actual C and ESR objects");
        endpoint(output.getTerminal(0).getEndpoint(), f.power.stage.capacitorEsr, 0, "COUT external plus is actual ESR post0");
        endpoint(output.getTerminal(1).getEndpoint(), f.power.stage.outputCap, 1, "COUT external minus is actual capacitor post1");
        PhysicalServicePart winding = (PhysicalServicePart)f.runtime.getInstalledPart("L1");
        check(winding.primary() == f.power.stage.outputInductor && winding.secondary() == f.power.stage.windingResistance,
            "output inductor retains actual winding and series-resistance objects");
        endpoint(winding.getTerminal(0).getEndpoint(), f.power.stage.outputInductor, 0, "L1 external first is actual winding post0");
        endpoint(winding.getTerminal(1).getEndpoint(), f.power.stage.windingResistance, 1, "L1 external second is actual Rw post1");
        PhysicalServicePart opto = (PhysicalServicePart)f.runtime.getInstalledPart("UFB");
        for (int i = 0; i < 4; i++) endpoint(opto.getTerminal(i).getEndpoint(), f.power.stage.opto, i, "actual optocoupler post " + i);
        check(f.runtime.getInstalledPart("F1").getSpecification() == FuseSpecification.E05_AC,
            "fuse adopts the exact resistance/thermal recipe rather than generic metadata");
        ResistorNameplate primary = (ResistorNameplate)f.runtime.getInstalledPart("RPRIMARY").getSpecification();
        check(primary.getNominalResistanceOhms() == 4700 && primary.getRatedWattage() == 10, "primary bleed has truthful 4.7k/10W specification");
        for (String id : new String[] {"CBIAS", "CFB", "C5"})
            check("CAP_Q60_1UF_25V_CERAMIC_BE0".equals(f.runtime.getInstalledPart(id).getSpecification().getSpecificationId()), "shared ceramic catalog recipe " + id);
        check(f.domains.getReferences().size() == 4 && f.domains.getReferences().get("AC_RETURN") != f.domains.getReferences().get("HV_RETURN"),
            "AC and rectified returns remain separate primary references");
        check(MeasurementReferencePolicy.check(f.domains, MeasurementReferencePolicy.Mode.DIFFERENTIAL, "HV_POS", "RAIL12", null).getDecision() ==
            MeasurementReferencePolicy.Decision.REJECTED, "primary-to-secondary differential is refused");
        check(MeasurementReferencePolicy.check(f.domains, MeasurementReferencePolicy.Mode.DIFFERENTIAL, "AC_LINE", "HV_POS", null).getDecision() ==
            MeasurementReferencePolicy.Decision.UNPROVEN, "separate primary references are not silently joined");
        PowerDomainContract.Source source = f.domains.getSources().get("MAINAC");
        check(source.getSeriesResistanceOhms().getValue() == 22 && source.getVoltageEnvelope().getMaximum() == 120 * Math.sqrt(2),
            "source declaration uses actual sine peak and finite series resistance");
        for (String channel : f.plan.channels()) check(f.runtime.getMutationProvider("K" + channel).getMetadata().getId().equals("RB56_RELAY_K" + channel),
            "both installed relay strategies retain distinct runtime identities");
    }

    private static void roundTrip(TestCirSim sim, Rb56PhysicalOwnerConstruction.Result f, String id) {
        PhysicalSlotMutationProvider provider = f.runtime.getMutationProvider(id);
        PhysicalMutationSlot slot = ((PhysicalSlotMutationProvider.Scoped)provider).getMutationSlot();
        PhysicalPart<?> original = slot.getPhysicalSlot().getInstalledPart();
        Vector<CircuitMeasurementEndpoint> copper = new Vector<CircuitMeasurementEndpoint>();
        Vector<CircuitElm> leads = new Vector<CircuitElm>();
        for (GeneratedComponentConnectionBinding binding : f.owner.getConnectionBindings().getForComponent(id)) {
            copper.add(binding.getBoardEndpoint()); leads.add(binding.getConnectionElement());
        }
        check(provider.isAvailable(WorkbenchOperation.forComponent(WorkbenchOperation.REMOVE, id), null), "fresh measured service availability " + id);
        check(provider.removeInstalledPart(), "detach installed package " + id);
        for (CircuitElm lead : leads) check(!sim.elmList.contains(lead), "only declared board leads detach " + id);
        for (CircuitElm element : original.getElectricalBacking().getCircuitElements()) check(sim.elmList.contains(element), "original loose bundle retained " + id);
        advance(sim, f, DT);
        WorkbenchPartsProvider inventory = f.runtime.getWorkbenchPartsProvider(id);
        PhysicalPart<?> candidate = ((CatalogAcquisitionProvider)provider).acquireFromCatalog(inventory.getCatalogEntries().get(0).getId());
        check(candidate != original && candidate.getSpecification().getSpecificationId().equals(original.getSpecification().getSpecificationId()),
            "catalog creates a fresh identity for the declared recipe " + id);
        for (CircuitElm element : candidate.getElectricalBacking().getCircuitElements()) {
            check(!original.getElectricalBacking().getCircuitElements().contains(element), "catalog backing does not reuse original element " + id);
            check(sim.elmList.contains(element) && f.owner.getSimulationElements().contains(element), "complete acquired bundle enters active/canonical graph " + id);
        }
        if ("F1".equals(id)) {
            PhysicalServicePart fuse = (PhysicalServicePart)candidate;
            check(fuse.getSpecification() == FuseSpecification.E05_AC &&
                ((ProtectionFuseElm)fuse.primary()).resistance == .1 &&
                ((ProtectionFuseElm)fuse.primary()).i2t == .1 && !((ProtectionFuseElm)fuse.primary()).blown &&
                inventory.getCatalogEntries().get(0).getId().equals(FuseSpecification.E05_AC.getSpecificationId() + "_STANDARD"),
                "catalog fuse retains its actual thermal recipe with a fresh unblown physical owner");
        }
        advance(sim, f, DT);
        Vector<CircuitElm> before = new Vector<CircuitElm>(sim.elmList); int history = f.runtime.getSessionHistory().size();
        PhysicalMutationScope.setFailureHookForDeveloperVerification(new PhysicalMutationScope.FailureHook() {
            public void afterStage(PhysicalMutationScope.FailureStage stage) { if (stage == PhysicalMutationScope.FailureStage.AFTER_GRAPH_RESTORE) throw new InjectedFailure(); }
        });
        try { provider.install(candidate.getId()); throw new AssertionError("Expected real install rollback hook " + id); }
        catch (InjectedFailure expected) { }
        finally { PhysicalMutationScope.clearFailureHookForDeveloperVerification(); }
        check(slot.isEmpty() && !candidate.isInstalled() && before.equals(sim.elmList) && f.runtime.getSessionHistory().size() == history,
            "compensated install restores empty slot, exact graph, identity and history " + id);
        check(f.runtime.getLastMutationReceipt().isCompensated() && !f.runtime.isMutationInProgress() && !f.runtime.isMutationQuarantined(),
            "rollback releases its exact owner " + id);
        advance(sim, f, DT); check(provider.install(candidate.getId()), "install actual catalog bundle " + id); advance(sim, f, DT);
        currentBinding(f, id, candidate, copper);
        check(new Rb56Generator.RelayGuard().isDischarged(f.owner, "KA"),
            "guard resolves current installed stores after catalog replacement " + id);
        check(provider.removeInstalledPart(), "remove actual catalog bundle " + id); advance(sim, f, DT);
        check(provider.install(original.getId()), "restore actual original bundle " + id); advance(sim, f, DT);
        currentBinding(f, id, original, copper);
        GeneratedRuntimeInvariant.verify(f.owner, sim.boardModificationController, sim.elmList);
    }

    private static void currentBinding(Rb56PhysicalOwnerConstruction.Result f, String id, PhysicalPart<?> part,
            Vector<CircuitMeasurementEndpoint> copper) {
        Vector<CircuitElm> actual = part.getElectricalBacking().getCircuitElements();
        check(f.owner.getComponentBindings().getSingleElement(id) == actual.get(0), "replacement uses actual current primary " + id);
        Vector<CircuitElm> auxiliary = new Vector<CircuitElm>(actual); auxiliary.remove(0);
        check(f.owner.getComponentBindings().getAuxiliaryElements(id).equals(auxiliary), "replacement binds the complete current auxiliary vector " + id);
        Vector<GeneratedComponentConnectionBinding> bindings = f.owner.getConnectionBindings().getForComponent(id);
        for (int i = 0; i < bindings.size(); i++) {
            GeneratedComponentConnectionBinding binding = bindings.get(i);
            CircuitMeasurementEndpoint expected = null;
            String name = f.owner.getBoard().getPad(binding.getPadId()).getTerminalId();
            for (PhysicalPartTerminal terminal : part.getTerminals()) if (name.equals(terminal.getTerminalName())) expected = terminal.getEndpoint();
            check(binding.getBoardEndpoint() == copper.get(i) && GeneratedComponentConnectionBindings.sameEndpoint(binding.getComponentEndpoint(), expected),
                "persistent board copper resolves actual replacement terminal " + binding.getPadId());
        }
    }

    private static void advance(TestCirSim sim, Rb56PhysicalOwnerConstruction.Result f, double seconds) {
        if (sim.circuitMatrix == null || sim.analyzeFlag) {
            for (CircuitElm element : sim.elmList) {
                check(element.boundingBox != null, "constructed drawing bounds exist");
                for (int p = 0; p < element.getPostCount(); p++) check(element.getPost(p) != null, "constructed solver post exists");
            }
            sim.solverExecutor.analyze(); sim.analyzeFlag = false;
        }
        int count = (int)Math.round(seconds / DT);
        for (int n = 0; n < count; n++) sim.solverExecutor.advanceSteps(1);
        f.runtime.observeSimulationTime(sim.t);
        check(sim.solverExecutor.isCurrent(sim.solverExecutor.observation(f.owner), f.owner), "fresh accepted owner receipt");
    }

    private static void report(TestCirSim sim, Rb56PhysicalOwnerConstruction.Result f, String phase) {
        double capEnergy = 0, windingEnergy = 0, coilEnergy = 0, maxCap = 0, maxWinding = 0, maxCoil = 0;
        for (PhysicalPart<?> part : f.runtime.getPhysicalParts()) if (part.isInstalled()) {
            if (part instanceof PhysicalCapacitorPart) { CapacitorElm c = ((PhysicalCapacitorPart)part).getElement(); double v = c.getVoltageDiff();
                capEnergy += .5 * c.capacitance * v * v; maxCap = Math.max(maxCap, Math.abs(v)); }
            else if (part instanceof PhysicalServicePart && part.getSpecification() instanceof InductorSpecification) {
                InductorElm l = (InductorElm)((PhysicalServicePart)part).primary(); windingEnergy += .5 * l.inductance * l.getCurrent() * l.getCurrent();
                maxWinding = Math.max(maxWinding, Math.abs(l.getCurrent())); }
            else if (part instanceof PhysicalRelayPart) { ServiceRelayElm relay = ((PhysicalRelayPart)part).getElement();
                coilEnergy += .5 * relay.inductance * relay.coilCurrent * relay.coilCurrent; maxCoil = Math.max(maxCoil, Math.abs(relay.coilCurrent)); }
        }
        System.out.println("RB56_PHYSICAL_PHASE {\"packages\":" + f.plan.physicalPackageCount() + ",\"phase\":\"" + phase +
            "\",\"acceptedTime\":" + number(sim.t) + ",\"rail12V\":" + number(voltage(f.owner, "COUT.+", "COUT.-")) +
            ",\"rail5V\":" + number(voltage(f.owner, "U1.OUTPUT", "U1.RETURN")) + ",\"installedCapJ\":" + number(capEnergy) +
            ",\"installedWindingJ\":" + number(windingEnergy) + ",\"installedCoilJ\":" + number(coilEnergy) +
            ",\"maxInstalledCapV\":" + number(maxCap) + ",\"maxInstalledWindingA\":" + number(maxWinding) +
            ",\"maxInstalledCoilA\":" + number(maxCoil) + ",\"activeElements\":" + sim.elmList.size() +
            ",\"canonicalElements\":" + f.owner.getSimulationElements().size() + ",\"normalAcceptance\":false}");
    }
    private static double voltage(GeneratedBoardInstance owner, String first, String second) {
        CircuitPostMeasurementEndpoint a = (CircuitPostMeasurementEndpoint)owner.getSimulationBindings().getEndpoint(first);
        CircuitPostMeasurementEndpoint b = (CircuitPostMeasurementEndpoint)owner.getSimulationBindings().getEndpoint(second);
        return a.getElement().getPostVoltage(a.getPostIndex()) - b.getElement().getPostVoltage(b.getPostIndex());
    }
    private static void endpoint(CircuitMeasurementEndpoint endpoint, CircuitElm element, int post, String message) {
        check(endpoint instanceof CircuitPostMeasurementEndpoint && ((CircuitPostMeasurementEndpoint)endpoint).getElement() == element &&
            ((CircuitPostMeasurementEndpoint)endpoint).getPostIndex() == post, message);
    }
    private static String number(double value) { return PowerDomainContract.finite(value) ? Double.toString(value) : "null"; }
    private static void check(boolean value, String message) { assertions++; if (!value) throw new AssertionError(message); }
    private static final class InjectedFailure extends RuntimeException { }
    /** No customer predicate is supplied or certified by this construction pilot. */
    private static final class UnqualifiedBehavior implements GeneratedChallengeBehaviorContract {
        public void verifyHealthy(GeneratedBoardInstance owner, BoardPowerState state) { throw new UnsupportedOperationException("Customer behavior not qualified by physical-owner pilot"); }
        public void verifyFaulted(GeneratedBoardInstance owner, BoardModificationController changes, BoardPowerState state) { throw new UnsupportedOperationException("No fault selected"); }
        public GeneratedRepairStatus getRepairStatus(GeneratedBoardInstance owner, BoardModificationController changes, BoardPowerState state, boolean overlay) { return GeneratedRepairStatus.STILL_FAULTED_OR_NONFUNCTIONAL; }
        public boolean isFunctionallyRepaired(GeneratedBoardInstance owner, BoardModificationController changes, BoardPowerState state, boolean overlay) { return false; }
    }
    private static final class RoutineConvergenceStream extends PrintStream {
        final PrintStream original; final TreeMap<String,Long> grouped = new TreeMap<String,Long>(); long count;
        final Pattern pattern = Pattern.compile("^converged after ([0-9]+) iterations, timeStep = ([0-9.E+-]+)$");
        RoutineConvergenceStream(PrintStream original) { super(original, true); this.original = original; }
        @Override public synchronized void println(String line) { Matcher m = line == null ? null : pattern.matcher(line);
            if (m != null && m.matches()) try { int iterations = Integer.parseInt(m.group(1)); double dt = Double.parseDouble(m.group(2));
                if (iterations >= 1 && iterations <= 5000 && dt == DT) { count++; Long n = grouped.get(line); grouped.put(line, n == null ? 1 : n + 1); return; }
            } catch (NumberFormatException unknown) { /* Preserve unknown messages. */ } original.println(line); }
        void report() { original.println("RB56_PHYSICAL_ROUTINE {\"droppedRawRoutineLineCount\":" + count + ",\"groups\":\"" + grouped.toString() + "\"}"); }
    }
    private static final class TestCirSim extends CirSim {
        TestCirSim() { gridSize = 16; gridMask = ~15; gridRound = 7; elmList = new Vector<CircuitElm>(); adjustables = new Vector<Adjustable>();
            undoStack = new Vector<String>(); redoStack = new Vector<String>(); maxTimeStep = minTimeStep = timeStep = DT; adjustTimeStep = false; }
        @Override void needAnalyze() { if (elmList != null && CircuitElm.sim == this) solverExecutor.invalidate(); analyzeFlag = true; }
        @Override void stop(String message, CircuitElm element) { stopMessage = message; circuitMatrix = null; stopElm = element; setSimRunning(false); analyzeFlag = false; }
        @Override public void setSimRunning(boolean running) { simRunning = running; }
        @Override void repaint() { }
        @Override void requestGeneratedBoardVerification() { }
        @Override void refreshBoardModificationControls() { }
        @Override void refreshChallengeInteractionState() { }
    }
}

/** Explicit developer construction slice; geometry, faults and customer acceptance remain unqualified. */
final class Rb56PhysicalOwnerConstruction {
    static final class Result {
        final Rb56Plan plan;
        final RelayOutputGenerator.Assembly assembly;
        final Rb56PowerStageAssembly power;
        final Rb56ControlTail.Result tail;
        final PowerDomainContract domains;
        final PhysicalBoardRuntime runtime;
        final GeneratedBoardInstance owner;
        private Result(Rb56Plan plan, RelayOutputGenerator.Assembly assembly,
                Rb56PowerStageAssembly power, Rb56ControlTail.Result tail,
                PowerDomainContract domains, PhysicalBoardRuntime runtime, GeneratedBoardInstance owner) {
            this.plan = plan; this.assembly = assembly; this.power = power; this.tail = tail;
            this.domains = domains; this.runtime = runtime; this.owner = owner;
        }
    }
    private Rb56PhysicalOwnerConstruction() { }

    /** The caller supplies its behavior contract; this helper invents no diagnostic or customer success. */
    static Result build(Rb56Plan plan, GeneratedChallengeBehaviorContract behavior) {
        if (plan == null || behavior == null) throw new IllegalArgumentException("Missing RB56 construction declaration");
        Rb56Generator.Candidate candidate = Rb56Generator.construct(plan);
        RelayOutputGenerator.Assembly a = candidate.assembly;
        try {
            Vector<GeneratedFaultCandidate> noFaults = new Vector<GeneratedFaultCandidate>();
            GeneratedBoardInstance owner = new GeneratedBoardInstance(a.board, a.elements,
                plan.seed, Rb56Plan.FAMILY_ID, plan.topology(), "RB56 physical ownership developer fixture",
                a.components, a.power, a.connections, behavior, null, a.specifications, null,
                new GeneratedComponentOperationalStates(), null, null, candidate.runtime, null, true, noFaults,
                GeneratedDiagnosticSolvabilityContract.forDeveloperFixture(Rb56Plan.FAMILY_ID,
                    plan.topology(), plan.seed, noFaults));
            return new Result(plan, a, candidate.power, candidate.tail, candidate.domains, candidate.runtime, owner);
        } catch (RuntimeException failure) {
            Rb56Generator.disposeUnpublished(a, failure);
            throw failure;
        } catch (Error failure) {
            Rb56Generator.disposeUnpublished(a, failure);
            throw failure;
        }
    }
}
