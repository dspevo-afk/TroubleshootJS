package com.lushprojects.circuitjs1.client;

import java.util.HashSet;
import java.util.Vector;

/** Actual fault values and serviced originals; no electrical symptom or family admission claim. */
public final class Rb56FaultPopulationContractTest {
    private static final double DT = .00005;
    private static final String[] RESISTOR_IDS = {"RENAC_HIGH_RESISTANCE", "REN_HIGH_RESISTANCE",
        "SENSOR_A_HIGH_RESISTANCE", "DRIVE_A_HIGH_RESISTANCE"};
    private static final String[] RESISTOR_OWNERS = {"RENAC", "REN", "RSA", "RDA"};
    private static final double[] HEALTHY_OHMS = {10000, 10000, 10000, 1000, 125};
    private static int assertions, acceptedSteps;

    public static void main(String[] args) {
        Rb56Plan[] plans = {
            Rb56Plan.configured(17, 1, Rb56Plan.ReferenceArrangement.SHARED_DIRECT, true, false, 1, 0, false),
            Rb56Plan.reference(77),
            Rb56Plan.configured(42, 2, Rb56Plan.ReferenceArrangement.SHARED_HYSTERETIC, true, true, 3, 3, true)
        };
        int[] packages = {40, 55, 60};
        for (int p = 0; p < plans.length; p++) {
            run(plans[p], packages[p], -1);
            for (int fault = 0; fault < 5; fault++) run(plans[p], packages[p], fault);
        }
        System.out.println("PASS: RB56 fault-population contracts assertions=" + assertions +
            " healthyCases=3 selectedServiceCases=15 acceptedSteps=" + acceptedSteps +
            " scope=DEVELOPER_FAULT_VALUE_AND_SERVICE_OWNERSHIP_ONLY");
    }

    private static void run(Rb56Plan plan, int packages, int selectedIndex) {
        CirSim prior = CircuitElm.sim, priorSingleton = CirSim.theSim;
        String priorDiode = DiodeElm.lastModelName, priorZener = ZenerElm.lastZenerModelName,
            priorTransistor = TransistorElm.lastModelName;
        int priorFlags = MosfetElm.globalFlags; double priorBeta = MosfetElm.lastBeta;
        TestCirSim sim = new TestCirSim(); Vector<CircuitElm> rawGraph = sim.elmList;
        Rb56Generator.Candidate candidate = null; GeneratedBoardInstance owner = null;
        Throwable failure = null; String phase = "CONSTRUCTION";
        String selectedId = selectedIndex < 0 ? "HEALTHY" : id(plan, selectedIndex);
        long started = System.nanoTime(); CircuitElm.sim = sim;
        try {
            candidate = selectedIndex < 0 ? Rb56Generator.construct(plan) :
                Rb56Generator.construct(plan, selectedId);
            population(candidate, packages);
            check((selectedIndex < 0 && candidate.selectedFault == null) ||
                (selectedIndex >= 0 && candidate.selectedFault == candidate.faultCandidates.get(selectedIndex).getBinding()),
                "selection retains the exact record in the complete population");
            values(candidate, -1);
            for (int i = 0; i < 5; i++) {
                GeneratedFaultBinding binding = candidate.faultCandidates.get(i).getBinding();
                binding.setApplied(true); values(candidate, i);
                check(binding.isApplied(), "actual value effect enters applied state");
                binding.setApplied(false); values(candidate, -1);
                check(!binding.isApplied(), "actual value effect restores its healthy state");
            }
            if (selectedIndex < 0) {
                check(candidate.selectedFault == null, "testing effects does not select a healthy candidate");
                return;
            }

            owner = developerOwner(candidate);
            sim.generatedBoardInstance = owner; sim.elmList = owner.getSimulationElements();
            sim.boardModificationController = new BoardModificationController(sim, owner);
            sim.getBoardPowerController().attach(owner.getExternalPowerBindings());
            sim.getBoardPowerController().setState(BoardPowerState.UNPOWERED);
            candidate.runtime.installRegisteredCapabilities(sim, owner, sim.boardModificationController, 0);
            candidate.runtime.onBoardPowerStateChanged(BoardPowerState.UNPOWERED);
            sim.a01MeasurementRunning = true; sim.needAnalyze();
            phase = "FRESH_OFF"; advance(sim, owner);
            check(owner.getExternalPowerBindings().areAllDisconnected(), "actual poles isolate every source before service");
            check(owner.getFaultBinding() == candidate.selectedFault && owner.getFaultCandidates().size() == 5,
                "developer owner retains the selected exact binding and all five records");
            PhysicalPart<?> original = candidate.runtime.getInstalledPart(target(plan, selectedIndex));
            check(original instanceof GeneratedFaultOwningPart && original.isOriginal() &&
                ((GeneratedFaultOwningPart)original).ownsGeneratedFault(candidate.selectedFault),
                "exact original physical part owns the selected binding after completion");
            GeneratedFaultBinding alias = new GeneratedFaultBinding(candidate.selectedFault.getFault(),
                candidate.selectedFault.getEffect());
            check(!((GeneratedFaultOwningPart)original).ownsGeneratedFault(alias),
                "a same-effect wrapper cannot substitute for the exact binding");
            int owningParts = 0;
            for (PhysicalPart<?> part : candidate.runtime.getPhysicalParts())
                if (part instanceof GeneratedFaultOwningPart &&
                    ((GeneratedFaultOwningPart)part).ownsGeneratedFault(candidate.selectedFault)) owningParts++;
            check(owningParts == 1, "selected binding has exactly one original physical owner");
            if (original instanceof PhysicalResistorPart) {
                PhysicalResistorPart resistor = (PhysicalResistorPart)original;
                check(resistor.getFaultBinding() == candidate.selectedFault && resistor.getSecondaryOpenPath() != null &&
                    !resistor.getSecondaryOpenPath().isOpen(), "selected resistor keeps exact fault and ordinary closed stress boundary");
                check(resistor.getSpecification().getNominalResistanceOhms() == HEALTHY_OHMS[selectedIndex] &&
                    resistor.getNameplate().getNominalResistanceOhms() == HEALTHY_OHMS[selectedIndex],
                    "original specification and markings retain the independent healthy value");
            }
            String componentId = target(plan, selectedIndex);
            Vector<GeneratedComponentConnectionBinding> connections = owner.getConnectionBindings().getForComponent(componentId);
            Vector<CircuitMeasurementEndpoint> copper = new Vector<CircuitMeasurementEndpoint>();
            Vector<CircuitElm> leads = new Vector<CircuitElm>();
            for (GeneratedComponentConnectionBinding connection : connections) {
                copper.add(connection.getBoardEndpoint()); leads.add(connection.getConnectionElement());
            }
            currentBindings(owner, componentId, original, copper, leads);
            GeneratedRuntimeInvariant.verify(owner, sim.boardModificationController, sim.elmList);

            phase = "APPLIED_OFF"; candidate.selectedFault.setApplied(true); sim.needAnalyze(); advance(sim, owner);
            check(original.isFaulted() && candidate.selectedFault.isApplied() && originalValue(original) == 1e9,
                "selected physical original realizes the actual failed value while OFF");
            PhysicalSlotMutationProvider provider = candidate.runtime.getMutationProvider(componentId);
            check(provider != null && provider.isAvailable(WorkbenchOperation.forComponent(WorkbenchOperation.REMOVE, componentId), null),
                "fresh actual zero-store observation permits existing service controller");
            phase = "REMOVE_ORIGINAL"; check(provider.removeInstalledPart(), "real controller removes the faulted original");
            check(!original.isInstalled() && candidate.runtime.getInstalledPart(componentId) == null,
                "removed original becomes a real loose part and the slot is empty");
            for (CircuitElm lead : leads) check(!sim.elmList.contains(lead), "original board attachments detach from active graph");
            for (CircuitElm element : original.getElectricalBacking().getCircuitElements())
                check(sim.elmList.contains(element) && owner.getSimulationElements().contains(element), "loose original backing remains active and canonical");
            advance(sim, owner);

            WorkbenchPartsProvider inventory = candidate.runtime.getWorkbenchPartsProvider(componentId);
            String catalogId = selectedIndex == 4 ? ReplaceableRelayCapability.COIL_5V :
                "R_CATALOG_" + (long)HEALTHY_OHMS[selectedIndex];
            boolean advertised = false;
            for (WorkbenchCatalogEntry entry : inventory.getCatalogEntries())
                if (catalogId.equals(entry.getId())) advertised = true;
            check(advertised, "existing catalog advertises the correct independent healthy value");
            phase = "ACQUIRE_REPLACEMENT";
            PhysicalPart<?> replacement = ((CatalogAcquisitionProvider)provider).acquireFromCatalog(catalogId);
            check(replacement != original && !replacement.isOriginal() && !replacement.isFaulted() &&
                replacement instanceof GeneratedFaultOwningPart &&
                !((GeneratedFaultOwningPart)replacement).ownsGeneratedFault(candidate.selectedFault),
                "real catalog successor has fresh identity and no inherited generated fault");
            check(originalValue(replacement) == HEALTHY_OHMS[selectedIndex], "actual catalog successor restores the healthy resistance");
            for (CircuitElm element : replacement.getElectricalBacking().getCircuitElements()) {
                check(!original.getElectricalBacking().getCircuitElements().contains(element), "catalog allocates independent backing");
                check(sim.elmList.contains(element) && owner.getSimulationElements().contains(element), "catalog registers complete active and canonical backing");
            }
            advance(sim, owner);
            phase = "INSTALL_REPLACEMENT"; check(provider.install(replacement.getId()), "real controller installs correct catalog successor");
            advance(sim, owner); currentBindings(owner, componentId, replacement, copper, leads);
            check(candidate.runtime.getInstalledPart(componentId) == replacement && original.isFaulted() &&
                !original.isInstalled() && originalValue(original) == 1e9 && candidate.selectedFault.isApplied(),
                "repair changes current backing while retaining the failed loose original");
            check(inventory.getPart(original.getId()) == original && inventory.getLooseParts().contains(original) &&
                ((GeneratedFaultOwningPart)original).ownsGeneratedFault(candidate.selectedFault),
                "original keeper remains in actual inventory with persistent exact fault ownership");
            check(!replacement.isFaulted() && originalValue(replacement) == HEALTHY_OHMS[selectedIndex],
                "current installed successor stays unfaulted and correct");

            phase = "RESET_WITH_LOOSE_ORIGINAL";
            if (original instanceof PhysicalResistorPart) {
                PhysicalResistorPart resistor = (PhysicalResistorPart)original;
                resistor.getSecondaryOpenPath().open();
                check(resistor.getSecondaryOpenPath().isOpen(), "existing loose-original secondary boundary actually opens");
                check(ReplaceableResistorBoardCapability.find(candidate.runtime, componentId).getStressDamageSystem()
                    .getState(original.getId()).getPart() == original, "stress state retains exact loose original");
            }
            candidate.runtime.resetForBoardReset(); sim.needAnalyze(); advance(sim, owner);
            if (original instanceof PhysicalResistorPart) {
                PhysicalResistorPart resistor = (PhysicalResistorPart)original;
                ResistorStressState state = ReplaceableResistorBoardCapability.find(candidate.runtime, componentId)
                    .getStressDamageSystem().getState(original.getId());
                check(!resistor.getSecondaryOpenPath().isOpen() && !state.isFailed() && state.getAccumulatedDamage() == 0,
                    "actual runtime reset restores the retained original's secondary boundary and stress state");
            }
            check(candidate.runtime.getInstalledPart(componentId) == replacement && !replacement.isFaulted() &&
                original.isFaulted() && originalValue(original) == 1e9 && candidate.selectedFault.isApplied(),
                "stress reset preserves repair and the distinct original generated fault");
            currentBindings(owner, componentId, replacement, copper, leads);
            GeneratedRuntimeInvariant.verify(owner, sim.boardModificationController, sim.elmList);
            System.out.println("RB56_FAULT_CASE {\"packages\":" + packages + ",\"fault\":\"" + selectedId +
                "\",\"acceptedTime\":" + number(sim.t) + ",\"originalOhms\":" + number(originalValue(original)) +
                ",\"replacementOhms\":" + number(originalValue(replacement)) + ",\"keeperFaultApplied\":true,\"resetCompleted\":true}");
        } catch (RuntimeException error) { failure = error; throw error;
        } catch (Error error) { failure = error; throw error;
        } finally {
            if (failure != null) System.out.println("RB56_FAULT_FAILURE {\"packages\":" + packages +
                ",\"fault\":\"" + selectedId + "\",\"phase\":\"" + phase + "\",\"acceptedTime\":" + number(sim.t) + "}");
            Throwable cleanup = null; boolean disposed = true;
            try {
                sim.a01MeasurementRunning = false; sim.elmList = rawGraph;
                sim.generatedBoardInstance = null; sim.boardModificationController = null;
                try { sim.solverExecutor.retire(); sim.solverExecutor.invalidate(); }
                catch (RuntimeException error) { cleanup = error; } catch (Error error) { cleanup = error; }
                sim.circuitMatrix = null; sim.getBoardPowerController().detach();
                Vector<CircuitElm> owned = owner != null ? owner.getSimulationElements() :
                    candidate != null ? new Vector<CircuitElm>(candidate.elements()) : new Vector<CircuitElm>();
                for (CircuitElm element : owned) try { element.delete(); }
                catch (RuntimeException error) { disposed = false; if (cleanup == null) cleanup = error; else cleanup.addSuppressed(error); }
                catch (Error error) { disposed = false; if (cleanup == null) cleanup = error; else cleanup.addSuppressed(error); }
            } finally {
                DiodeElm.lastModelName = priorDiode; ZenerElm.lastZenerModelName = priorZener;
                TransistorElm.lastModelName = priorTransistor; MosfetElm.globalFlags = priorFlags; MosfetElm.lastBeta = priorBeta;
                CircuitElm.sim = prior; CirSim.theSim = priorSingleton;
            }
            boolean clean = cleanup == null && disposed && sim.elmList == rawGraph && rawGraph.isEmpty() &&
                sim.generatedBoardInstance == null && sim.boardModificationController == null && sim.circuitMatrix == null &&
                !sim.solverExecutor.isUnavailable() && CircuitElm.sim == prior && CirSim.theSim == priorSingleton;
            System.out.println("RB56_FAULT_CLEANUP {\"packages\":" + packages + ",\"fault\":\"" + selectedId +
                "\",\"elapsedSeconds\":" + number((System.nanoTime() - started) / 1e9) +
                ",\"candidateDisposed\":" + disposed + ",\"rawGraphRestored\":" + (sim.elmList == rawGraph && rawGraph.isEmpty()) +
                ",\"clean\":" + clean + "}");
            if (failure != null) {
                if (cleanup != null) failure.addSuppressed(cleanup);
                if (!clean) failure.addSuppressed(new AssertionError("Fault fixture cleanup failed"));
            } else if (cleanup != null || !clean) throw new AssertionError("Fault fixture cleanup failed", cleanup);
        }
    }

    private static void population(Rb56Generator.Candidate candidate, int packages) {
        check(candidate.plan.physicalPackageCount() == packages && candidate.runtime.getSlots().size() == packages,
            "representative mounts its exact 40/55/60 declared inventory");
        check(candidate.faultCandidates.size() == 5 &&
            GeneratedFaultServiceabilityAdmission.getAdmittedCandidateCount(candidate.faultCandidates) == 5,
            "complete five records survive without filtering");
        HashSet<String> keys = new HashSet<String>(), targets = new HashSet<String>();
        for (int i = 0; i < 5; i++) {
            GeneratedFaultCandidate record = candidate.faultCandidates.get(i);
            GeneratedFault fault = record.getFault();
            check(id(candidate.plan, i).equals(fault.getId()) && target(candidate.plan, i).equals(fault.getTargetComponentId()),
                "descriptor order and actual target agree with independent population");
            check(fault.getType() == (i < 4 ? GeneratedFaultType.RESISTOR_INCORRECT_VALUE : GeneratedFaultType.RELAY_COIL_OPEN),
                "high-value resistor is not mislabeled as an ideal open");
            check(Rb56Plan.FAMILY_ID.equals(fault.getCircuitFamilyId()) && fault.getSelectionSeed() == candidate.plan.seed &&
                !record.getBinding().isApplied(), "record preserves exact family/seed and starts unapplied");
            check(keys.add(fault.getHypothesisKey()) && targets.add(fault.getTargetComponentId()), "all five semantic keys and owners are distinct");
            check(Rb56Generator.faultIdForHypothesis(candidate.plan, fault.getHypothesisKey()).equals(fault.getId()),
                "exact value-bearing hypothesis maps back to its descriptor");
            if (i < 4) {
                check(fault.getHealthyValue() == HEALTHY_OHMS[i] && fault.getEffectiveValue() == 1e9,
                    "resistor metadata carries independent healthy and actual high values");
                String changedValueKey = new GeneratedFault(fault.getId(), fault.getType(), fault.getTargetComponentId(),
                    fault.getCircuitFamilyId(), fault.getSelectionSeed(), HEALTHY_OHMS[i], 2e9).getHypothesisKey();
                String changedHealthyKey = new GeneratedFault(fault.getId(), fault.getType(), fault.getTargetComponentId(),
                    fault.getCircuitFamilyId(), fault.getSelectionSeed(), HEALTHY_OHMS[i] * 2, 1e9).getHypothesisKey();
                rejectHypothesis(candidate.plan, changedValueKey);
                rejectHypothesis(candidate.plan, changedHealthyKey);
            }
            check(record.getBinding().getEffect().getValueMutationTarget() ==
                candidate.assembly.components.getSingleElement(target(candidate.plan, i)), "effect uses actual declared backing");
            check(record.getPrivateSimulationElements().isEmpty(), "value faults add no private fault graph or switch");
        }
        String selected = Rb56Generator.selectedFaultId(candidate.plan), canonical = candidate.plan.canonical();
        boolean inPopulation = false;
        for (int i = 0; i < 5; i++) if (id(candidate.plan, i).equals(selected)) inPopulation = true;
        check(inPopulation, "named selection chooses one of all five declared records");
        NamedRandomStreams other = new NamedRandomStreams(NamedRandomStreams.DERIVATION_VERSION,
            candidate.plan.seed, Rb56Plan.FAMILY_ID, Rb56Plan.PLAN_VERSION);
        other.openDevice(NamedRandomStreams.Concern.SUPPORT, 1, "unrelated-contract-canary").nextInt(100);
        check(selected.equals(Rb56Generator.selectedFaultId(candidate.plan)) && canonical.equals(candidate.plan.canonical()),
            "independent named selection remains repeatable without changing the plan");
    }

    private static void values(Rb56Generator.Candidate candidate, int applied) {
        for (int i = 0; i < 5; i++) {
            CircuitElm actual = candidate.assembly.components.getSingleElement(target(candidate.plan, i));
            double measured = i < 4 ? ((ResistorElm)actual).getResistance() : ((ServiceRelayElm)actual).coilR;
            check(measured == (i == applied ? 1e9 : HEALTHY_OHMS[i]), "only intended actual resistor/coil value changes");
        }
    }

    private static void rejectHypothesis(Rb56Plan plan, String key) {
        boolean rejected = false;
        try { Rb56Generator.faultIdForHypothesis(plan, key); }
        catch (IllegalArgumentException expected) { rejected = true; }
        check(rejected, "a different healthy or effective value cannot alias the same hypothesis");
    }

    private static GeneratedBoardInstance developerOwner(Rb56Generator.Candidate candidate) {
        RelayOutputGenerator.Assembly a = candidate.assembly;
        return new GeneratedBoardInstance(a.board, a.elements, candidate.plan.seed, Rb56Plan.FAMILY_ID,
            candidate.plan.topology(), "RB56 fault service contract fixture", a.components, a.power, a.connections,
            new UnqualifiedBehavior(), null, a.specifications, candidate.selectedFault,
            new GeneratedComponentOperationalStates(), null, null, candidate.runtime, null, true, candidate.faultCandidates,
            GeneratedDiagnosticSolvabilityContract.forDeveloperFixture(Rb56Plan.FAMILY_ID,
                candidate.plan.topology(), candidate.plan.seed, candidate.faultCandidates));
    }

    private static void currentBindings(GeneratedBoardInstance owner, String id, PhysicalPart<?> part,
            Vector<CircuitMeasurementEndpoint> copper, Vector<CircuitElm> leads) {
        Vector<CircuitElm> backing = part.getElectricalBacking().getCircuitElements();
        check(owner.getComponentBindings().getSingleElement(id) == backing.get(0), "current primary is actual installed part");
        Vector<CircuitElm> auxiliary = new Vector<CircuitElm>(backing); auxiliary.remove(0);
        check(owner.getComponentBindings().getAuxiliaryElements(id).equals(auxiliary), "all current auxiliary backing is bound");
        Vector<GeneratedComponentConnectionBinding> bindings = owner.getConnectionBindings().getForComponent(id);
        check(bindings.size() == part.getTerminalCount(), "all physical terminals retain declared lead mappings");
        for (int i = 0; i < bindings.size(); i++) {
            GeneratedComponentConnectionBinding binding = bindings.get(i);
            String name = owner.getBoard().getPad(binding.getPadId()).getTerminalId();
            CircuitMeasurementEndpoint terminal = null;
            for (PhysicalPartTerminal declared : part.getTerminals()) if (name.equals(declared.getTerminalName())) terminal = declared.getEndpoint();
            check(binding.getBoardEndpoint() == copper.get(i) && binding.getConnectionElement() == leads.get(i) &&
                GeneratedComponentConnectionBindings.sameEndpoint(binding.getComponentEndpoint(), terminal),
                "stationary copper and actual current physical terminal are preserved");
            CircuitPostMeasurementEndpoint board = (CircuitPostMeasurementEndpoint)copper.get(i), component = (CircuitPostMeasurementEndpoint)terminal;
            Point boardPoint = board.getElement().getPost(board.getPostIndex()), componentPoint = component.getElement().getPost(component.getPostIndex());
            CircuitElm lead = leads.get(i);
            check(simContains(lead) && (lead.getPost(0).equals(boardPoint) && lead.getPost(1).equals(componentPoint) ||
                lead.getPost(1).equals(boardPoint) && lead.getPost(0).equals(componentPoint)), "actual declared lead joins its two owned endpoints");
        }
    }

    private static boolean simContains(CircuitElm element) { return CircuitElm.sim.elmList.contains(element); }
    private static double originalValue(PhysicalPart<?> part) {
        return part instanceof PhysicalResistorPart ? ((PhysicalResistorPart)part).getElement().getResistance() :
            ((PhysicalRelayPart)part).getElement().coilR;
    }
    private static String id(Rb56Plan plan, int index) {
        return index < 4 ? RESISTOR_IDS[index] : "RELAY_" + (plan.channelCount == 1 ? "A" : "B") + "_COIL_OPEN";
    }
    private static String target(Rb56Plan plan, int index) {
        return index < 4 ? RESISTOR_OWNERS[index] : "K" + (plan.channelCount == 1 ? "A" : "B");
    }
    private static void advance(TestCirSim sim, GeneratedBoardInstance owner) {
        if (sim.circuitMatrix == null || sim.analyzeFlag) {
            for (CircuitElm element : sim.elmList) {
                check(element.boundingBox != null, "actual solver element has initialized bounds");
                for (int p = 0; p < element.getPostCount(); p++) check(element.getPost(p) != null, "actual solver post is initialized");
            }
            sim.solverExecutor.analyze(); sim.analyzeFlag = false;
        }
        sim.solverExecutor.advanceSteps(1); acceptedSteps++;
        owner.getPhysicalBoardRuntime().observeSimulationTime(sim.t);
        check(sim.solverExecutor.isCurrent(sim.solverExecutor.observation(owner), owner), "service uses fresh accepted exact-owner observation");
    }
    private static String number(double value) { return PowerDomainContract.finite(value) ? Double.toString(value) : "null"; }
    private static void check(boolean value, String message) { assertions++; if (!value) throw new AssertionError(message); }

    private static final class UnqualifiedBehavior implements GeneratedChallengeBehaviorContract {
        public void verifyHealthy(GeneratedBoardInstance owner, BoardPowerState state) { throw new UnsupportedOperationException("Electrical symptom not qualified by this contract"); }
        public void verifyFaulted(GeneratedBoardInstance owner, BoardModificationController changes, BoardPowerState state) { throw new UnsupportedOperationException("Electrical symptom not qualified by this contract"); }
        public GeneratedRepairStatus getRepairStatus(GeneratedBoardInstance owner, BoardModificationController changes, BoardPowerState state, boolean overlay) { return GeneratedRepairStatus.STILL_FAULTED_OR_NONFUNCTIONAL; }
        public boolean isFunctionallyRepaired(GeneratedBoardInstance owner, BoardModificationController changes, BoardPowerState state, boolean overlay) { return false; }
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
