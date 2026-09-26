package com.lushprojects.circuitjs1.client;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.Vector;

/** Native solver-backed Q30 hypothesis, physical service, and customer retest contract. */
public final class Q30ServiceFlowContractTest {
    private static int assertions;

    private static final String[][] EXPECTED = {
        { "DREV_OPEN", "DIODE_OPEN", "DREV", "3" },
        { "REN_OPEN", "RESISTOR_OPEN", "REN", "3" },
        { "SENSOR_A_OPEN", "RESISTOR_OPEN", "RSA", "1" },
        { "DRIVE_A_OPEN", "RESISTOR_OPEN", "RDA", "1" },
        { "RELAY_B_COIL_OPEN", "RELAY_COIL_OPEN", "KB", "2" }
    };
    private static final int[] INPUTS = { 0, 1, 2, 3 };
    private static final boolean[][] EXPECTED_OUTPUTS = {
        { false, false }, { true, false }, { false, true }, { true, true }
    };
    private static final String[] INPUT_OPERATIONS = {
        Rb30Behavior.SENSORS_LOW, Rb30Behavior.SENSORS_A_ONLY,
        Rb30Behavior.SENSORS_B_ONLY, Rb30Behavior.SENSORS_HIGH
    };

    private Q30ServiceFlowContractTest() { }

    public static void main(String[] args) {
        long started = System.nanoTime();
        long[] seeds = { 0L, 37L };
        int selectedHypothesis = -1;
        long selectedSeed = 0L;
        String selectedFault = null;
        if (args.length != 0) {
            if (args.length != 4 || !"--seed".equals(args[0]) ||
                    !"--fault".equals(args[2]))
                throw new IllegalArgumentException(
                    "Usage: [--seed 0|37 --fault DREV_OPEN|REN_OPEN|SENSOR_A_OPEN|DRIVE_A_OPEN|RELAY_B_COIL_OPEN]");
            if ("0".equals(args[1])) selectedSeed = 0L;
            else if ("37".equals(args[1])) selectedSeed = 37L;
            else throw new IllegalArgumentException("Unsupported Q30 service seed: " + args[1]);
            selectedFault = args[3];
            selectedHypothesis = hypothesisIndex(selectedFault);
            seeds = new long[] { selectedSeed };
        }
        verifyRelayReadingBoundary();
        CirSim constructionSim = new CirSim();
        configureSimulator(constructionSim);
        for (long seed : seeds)
            verifySeed(seed, constructionSim, selectedHypothesis);
        System.out.println("Q30_CASE_TIME operation=route-replay-and-service elapsedMillis=" +
            ((System.nanoTime() - started) / 1000000L));
        if (selectedHypothesis < 0) {
            System.out.println("PASS: Q30 service flow contracts " + assertions +
                " assertions seeds=" + seeds.length + " hypotheses=" + EXPECTED.length +
                " cases=" + (seeds.length * EXPECTED.length));
        } else {
            System.out.println("PASS: Q30 service flow contracts " + assertions +
                " assertions seed=" + selectedSeed + " fault=" + selectedFault);
        }
    }

    private static int hypothesisIndex(String faultId) {
        for (int index = 0; index < EXPECTED.length; index++)
            if (EXPECTED[index][0].equals(faultId)) return index;
        throw new IllegalArgumentException("Unsupported Q30 fault: " + faultId);
    }

    private static void verifyRelayReadingBoundary() {
        check(Rb30RelayService.safeReadings(new double[5], 0), "zero-energy relay readings admitted");
        check(!Rb30RelayService.safeReadings(null, 0) &&
            !Rb30RelayService.safeReadings(new double[4], 0), "incomplete relay pin census rejected");
        for (int pin = 0; pin < 5; pin++)
            for (double unsafe : new double[] { .05, -.05, 12, -12, Double.NaN, Double.POSITIVE_INFINITY }) {
                double[] readings = new double[5];
                readings[pin] = unsafe;
                check(!Rb30RelayService.safeReadings(readings, 0), "unsafe target pin " + pin + " rejected");
            }
        for (double unsafe : new double[] { 1e-6, -1e-6, .04, Double.NaN, Double.POSITIVE_INFINITY })
            check(!Rb30RelayService.safeReadings(new double[5], unsafe), "energized/nonfinite relay coil rejected");
        check(Rb30RelayService.safeReadings(new double[] { .049, -.049, .049, -.049, .049 }, .999e-6),
            "bounded discharged readings admitted below both strict limits");
    }

    private static void verifySeed(long seed, CirSim constructionSim,
            int selectedHypothesis) {
        // CircuitElm constructors call drag(), which uses the active solver's grid.
        CircuitElm.sim = constructionSim;
        Rb30Plan plan = Rb30Plan.resolve(seed);
        check(plan.canonical().equals(Rb30Plan.resolve(seed).canonical()),
            "Q30 plan replays exact current identity for seed " + seed);
        CircuitElm.sim = constructionSim;
        Rb30Generator.Candidate oracle = new Rb30Generator().construct(plan);
        verifyPopulation(oracle.faultCandidates, null, seed);
        String firstKey = hypothesisKey(oracle.faultCandidates, EXPECTED[0][0]);

        CircuitElm.sim = constructionSim;
        GeneratedBoardInstance first = new Rb30Generator().generateForHypothesis(seed, firstKey);
        CircuitElm.sim = constructionSim;
        GeneratedBoardInstance replay = new Rb30Generator().generateForHypothesis(seed, firstKey);
        check(first.getSeed() == seed && replay.getSeed() == seed,
            "Q30 signed-long seed survives hypothesis construction and replay");
        check(first.getFaultBinding().getFault().getId().equals(EXPECTED[0][0]) &&
                replay.getFaultBinding().getFault().getId().equals(EXPECTED[0][0]),
            "exact semantic hypothesis key selects the requested fault");
        String physicalFingerprint = PhysicalBoardFingerprint.of(first);
        String layoutFingerprint = first.getPcbLayout().geometryFingerprint();
        check(physicalFingerprint.equals(PhysicalBoardFingerprint.of(replay)) &&
                layoutFingerprint.equals(replay.getPcbLayout().geometryFingerprint()),
            "Q30 first routed realization has exact physical replay for seed " + seed);
        verifyPopulation(first.getFaultCandidates(), first.getFaultBinding(), seed);

        boolean rejectedUnknownId = false;
        CircuitElm.sim = constructionSim;
        try {
            new Rb30Generator().construct(plan, "FOREIGN_Q30_FAULT");
        } catch (IllegalArgumentException expected) {
            rejectedUnknownId = true;
        }
        check(rejectedUnknownId, "foreign forced fault ID rejects before assembly");
        boolean rejectedUnknownKey = false;
        CircuitElm.sim = constructionSim;
        try {
            new Rb30Generator().generateForHypothesis(seed, "FOREIGN_Q30_HYPOTHESIS");
        } catch (IllegalArgumentException expected) {
            rejectedUnknownKey = true;
        }
        check(rejectedUnknownKey, "foreign hypothesis key rejects without producing an owner");

        for (int index = 0; index < EXPECTED.length; index++) {
            if (selectedHypothesis >= 0 && index != selectedHypothesis) continue;
            NativeServiceCirSim sim = new NativeServiceCirSim();
            configureSimulator(sim);
            CircuitElm.sim = sim;
            Rb30Generator.Candidate candidate = new Rb30Generator().construct(
                plan, EXPECTED[index][0]);
            verifyPopulation(candidate.faultCandidates, candidate.selectedFault, seed);
            GeneratedBoardInstance owner = new Rb30Generator().assemble(candidate,
                first.getPcbLayout().copySealed());
            check(owner.getFaultBinding().getFault().getId().equals(EXPECTED[index][0]) &&
                    owner.getFaultBinding().getFault().getTargetComponentId()
                        .equals(EXPECTED[index][2]),
                "selected Q30 hypothesis retains its independent physical locus");
            check(layoutFingerprint.equals(owner.getPcbLayout().geometryFingerprint()) &&
                    physicalFingerprint.equals(PhysicalBoardFingerprint.of(owner)),
                "all five selected hypotheses share the exact seed layout");
            verifyServiceFlow(seed, index, owner, physicalFingerprint, layoutFingerprint, sim);
            CircuitElm.sim = constructionSim;
        }
    }

    private static void verifyPopulation(Vector<GeneratedFaultCandidate> candidates,
            GeneratedFaultBinding selected, long seed) {
        check(candidates != null && candidates.size() == EXPECTED.length,
            "Q30 diagnostic population retains exactly five candidates for seed " + seed);
        Map<String, GeneratedFaultCandidate> byId = new HashMap<String, GeneratedFaultCandidate>();
        Set<String> keys = new HashSet<String>();
        for (GeneratedFaultCandidate candidate : candidates) {
            String id = candidate.getFault().getId();
            check(byId.put(id, candidate) == null,
                "Q30 fault identity is unique: " + id);
            check(keys.add(candidate.getHypothesisKey()),
                "Q30 semantic hypothesis key is unique: " + id);
        }
        int selectedCount = 0;
        for (String[] expected : EXPECTED) {
            GeneratedFaultCandidate candidate = byId.get(expected[0]);
            check(candidate != null, "complete Q30 population contains " + expected[0]);
            check(candidate.getFault().getType() == GeneratedFaultType.valueOf(expected[1]) &&
                    candidate.getFault().getTargetComponentId().equals(expected[2]) &&
                    candidate.isAdmitted(),
                "independent Q30 oracle matches admitted " + expected[0] + " locus/type");
            if (selected != null && candidate.getBinding() == selected)
                selectedCount++;
        }
        check(selected == null || selectedCount == 1,
            "selected binding is the unique member of the complete Q30 population");
    }

    private static String hypothesisKey(Vector<GeneratedFaultCandidate> candidates, String id) {
        for (GeneratedFaultCandidate candidate : candidates)
            if (id.equals(candidate.getFault().getId()))
                return candidate.getHypothesisKey();
        throw new AssertionError("Missing Q30 hypothesis key " + id);
    }

    private static void verifyServiceFlow(long seed, int hypothesisIndex,
            GeneratedBoardInstance owner, String expectedPhysical, String expectedLayout,
            final NativeServiceCirSim sim) {
        sim.gridSize = 16;
        sim.gridMask = ~15;
        sim.gridRound = 7;
        configureSimulator(sim);
        CircuitElm.sim = sim;
        sim.elmList = new Vector<CircuitElm>(owner.getSimulationElements());
        sim.adjustables = new Vector<Adjustable>();
        sim.undoStack = new Vector<String>();
        sim.redoStack = new Vector<String>();
        sim.generatedBoardInstance = owner;
        BoardModificationController modifications =
            new BoardModificationController(sim, owner);
        sim.boardModificationController = modifications;
        PhysicalBoardRuntime runtime = owner.getPhysicalBoardRuntime();
        runtime.installRegisteredCapabilities(sim, owner, modifications, 0);
        sim.boardPowerController.attach(owner.getExternalPowerBindings());
        runtime.onBoardPowerStateChanged(BoardPowerState.POWERED);

        GeneratedDiagnosticSolvabilityAdmission.validateStructural(owner);
        verifyDiagnosticOwnership(owner, hypothesisIndex);
        verifyD01NormalGate(sim, owner);

        Rb30Behavior behavior = (Rb30Behavior) owner.getFamilyState();
        // Native execution has no GWT instrument widgets or UI verification
        // callbacks. Qualify the real profiles below, then expose only that
        // readiness signal while retaining actual retest invalidation state.
        sim.generatedChallengeController = new GeneratedChallengeController(sim, owner) {
            @Override boolean allowsWorkbenchInteraction() {
                return sim.nativeProfilesReady;
            }
        };
        owner.getFaultBinding().setApplied(false);
        settleElectrical(sim);
        behavior.prepareHealthyProfile(sim, owner);
        behavior.verifyHealthy(owner, BoardPowerState.POWERED);
        check(behavior.getInputs() == 0 && behavior.healthy(owner, 0) &&
                Math.abs(Rb30Behavior.voltage(owner, "JOA.1", "JOA.2")) <= .05 &&
                Math.abs(Rb30Behavior.voltage(owner, "JOB.1", "JOB.2")) <= .05,
            "healthy four-condition profile finishes at real LOW inputs before fault application");
        owner.getFaultBinding().setApplied(true);
        behavior.prepareFaultedProfile(sim, owner);
        behavior.verifyFaultedProfile(sim, owner, modifications, BoardPowerState.POWERED);
        check(behavior.getInputs() == 3 && !behavior.healthy(owner, 3) &&
                behavior.getObservedBehavior() == GeneratedObservedBehavior.RELAY_LOAD_NOT_SWITCHING,
            "actual fault preparation observes a symptom after LOW-before-fault then HIGH");
        int symptomMask = observeFaultMask(owner, sim);
        int expectedMask = Integer.parseInt(EXPECTED[hypothesisIndex][3]);
        check(symptomMask == expectedMask,
            "CircuitJS fault symptom mask matches independent locus oracle for " +
                EXPECTED[hypothesisIndex][0] + " got=" + symptomMask);
        sim.nativeProfilesReady = true;
        double beforeStatusTime = sim.t;
        int beforeStatusInputs = behavior.getInputs();
        behavior.getRepairStatus(sim, owner, modifications, BoardPowerState.POWERED, false);
        check(sim.t == beforeStatusTime && behavior.getInputs() == beforeStatusInputs,
            "live repair-status observation cannot drive inputs or advance solver time");
        check(!customerRetest(owner, sim),
            "unrepaired Q30 owner fails actual four-input customer retest");

        String component = EXPECTED[hypothesisIndex][2];
        PhysicalPart<?> original = runtime.getInstalledPart(component);
        PhysicalSlotMutationProvider service = runtime.getMutationProvider(component);
        WorkbenchPartsProvider catalog = runtime.getWorkbenchPartsProvider(component);
        String correctCatalogId = owner.getDiagnosticProvider()
            .getCorrectCatalogId(owner, component);
        check(original != null && service != null && catalog != null &&
                containsCatalog(catalog.getCatalogEntries(), correctCatalogId),
            "selected Q30 physical owner has a matching production replacement catalog");
        Map<String, CircuitMeasurementEndpoint> pads = capturePadEndpoints(owner);

        setPower(sim, owner, BoardPowerState.UNPOWERED);
        check(service.removeInstalledPart(), "production owner removes faulted part " + component);
        settleMutation(sim, owner, modifications, runtime);
        check(runtime.getInstalledPart(component) == null,
            "physical removal leaves the selected slot empty");
        check(service.install(original.getId()),
            "production owner reinstalls the exact faulted original part");
        settleMutation(sim, owner, modifications, runtime);
        check(runtime.getInstalledPart(component) == original && owner.getFaultBinding().isApplied(),
            "reinstall preserves original part identity and its injected fault");
        setPower(sim, owner, BoardPowerState.POWERED);
        check(!customerRetest(owner, sim),
            "reinstalling the original component does not repair the Q30 symptom");

        setPower(sim, owner, BoardPowerState.UNPOWERED);
        check(service.removeInstalledPart(), "faulted original can be removed for replacement");
        settleMutation(sim, owner, modifications, runtime);
        check(service.installNewFromCatalog(correctCatalogId),
            "production owner installs the diagnostic provider's correct catalog part");
        settleMutation(sim, owner, modifications, runtime);
        PhysicalPart<?> replacement = runtime.getInstalledPart(component);
        check(replacement != null && replacement != original,
            "catalog repair creates a distinct physical identity");
        verifyPadEndpoints(owner, pads);
        owner.getConnectionBindings().validateAgainst(owner.getBoard(),
            owner.getSimulationElements(), owner.getComponentBindings(),
            owner.getExternalPowerBindings(), owner.getFaultBinding());
        check(expectedLayout.equals(owner.getPcbLayout().geometryFingerprint()) &&
                expectedPhysical.equals(PhysicalBoardFingerprint.of(owner)),
            "repair preserves exact board/copper geometry and physical fingerprint");

        setPower(sim, owner, BoardPowerState.POWERED);
        check(customerRetest(owner, sim),
            "catalog replacement passes actual four-input customer retest");
        check(repairedTruthTable(owner, sim),
            "independent solver voltages satisfy all four expected input/output states");
        System.out.println("PASS: Q30 service flow seed=" + seed + " fault=" +
            EXPECTED[hypothesisIndex][0] + " target=" + component +
            " fingerprintHash=" + Integer.toHexString(expectedPhysical.hashCode()));
    }

    private static void verifyDiagnosticOwnership(GeneratedBoardInstance owner,
            int selectedIndex) {
        GeneratedDiagnosticProvider provider = owner.getDiagnosticProvider();
        check(provider != null && provider.getObservationProgram() != null &&
                provider.getDiagnosticPlan() != null,
            "Q30 owns a production diagnostic plan and observation program");
        Set<String> populationKeys = new HashSet<String>();
        for (GeneratedFaultCandidate candidate : owner.getFaultCandidates())
            populationKeys.add(candidate.getHypothesisKey());
        Set<String> declaredKeys = new HashSet<String>(
            owner.getDiagnosticSolvabilityContract().getHypothesisKeys());
        check(populationKeys.equals(declaredKeys) && declaredKeys.size() == EXPECTED.length,
            "Q30 provider contract accounts for every hypothesis exactly once");
        for (String[] expected : EXPECTED) {
            String replacementId = provider.getCorrectCatalogId(owner, expected[2]);
            WorkbenchPartsProvider parts = owner.getPhysicalBoardRuntime()
                .getWorkbenchPartsProvider(expected[2]);
            check(parts != null && containsCatalog(parts.getCatalogEntries(), replacementId),
                "Q30 provider maps " + expected[0] + " to an owned legal replacement");
        }
        check(owner.getFaultBinding().getFault().getId().equals(EXPECTED[selectedIndex][0]),
            "diagnostic provider and live owner agree on the selected hypothesis");
    }

    private static void verifyD01NormalGate(CirSim sim, GeneratedBoardInstance owner) {
        GeneratedChallengeController controller = new GeneratedChallengeController(sim, owner);
        sim.generatedChallengeController = controller;
        boolean rejected = false;
        try {
            controller.beginDiagnosticAdmission();
        } catch (IllegalStateException expected) {
            rejected = expected.getMessage() != null &&
                expected.getMessage().contains("normal owner");
        } finally {
            sim.generatedChallengeController = null;
        }
        check(owner.isDeveloperOnlyFaultRoute() && rejected &&
                controller.getDiagnosticProofReceipt() == null,
            "D01 rejects developer-only Q30 before normal diagnostic admission");
    }

    private static int observeFaultMask(GeneratedBoardInstance owner, CirSim sim) {
        int symptomMask = 0;
        for (int condition = 0; condition < INPUTS.length; condition++) {
            GeneratedCustomerRetestResult control = owner.invokeOperation(
                INPUT_OPERATIONS[condition], sim);
            check(control == null, "Q30 input control " + INPUT_OPERATIONS[condition] + " executes");
            double a = Rb30Behavior.voltage(owner, "JOA.1", "JOA.2");
            double b = Rb30Behavior.voltage(owner, "JOB.1", "JOB.2");
            if (!matches(a, EXPECTED_OUTPUTS[condition][0])) symptomMask |= 1;
            if (!matches(b, EXPECTED_OUTPUTS[condition][1])) symptomMask |= 2;
        }
        return symptomMask;
    }

    private static boolean repairedTruthTable(GeneratedBoardInstance owner, CirSim sim) {
        for (int condition = 0; condition < INPUTS.length; condition++) {
            owner.invokeOperation(INPUT_OPERATIONS[condition], sim);
            if (!matches(Rb30Behavior.voltage(owner, "JOA.1", "JOA.2"),
                    EXPECTED_OUTPUTS[condition][0]) ||
                    !matches(Rb30Behavior.voltage(owner, "JOB.1", "JOB.2"),
                    EXPECTED_OUTPUTS[condition][1]))
                return false;
        }
        return true;
    }

    private static boolean matches(double volts, boolean on) {
        if (Double.isNaN(volts) || Double.isInfinite(volts)) return false;
        return on ? volts >= 10.8 && volts <= 12.6 : Math.abs(volts) <= .05;
    }

    private static boolean customerRetest(GeneratedBoardInstance owner, CirSim sim) {
        GeneratedCustomerRetestResult result = owner.invokeOperation(
            GeneratedBoardOperationIds.CUSTOMER_RETEST, sim);
        check(result != null, "production Q30 customer retest returns a result");
        return result != null && result.isPassed();
    }

    private static void setPower(CirSim sim, GeneratedBoardInstance owner,
            BoardPowerState state) {
        if (sim.boardPowerController.getState() != state) {
            sim.boardPowerController.setState(state);
            owner.getPhysicalBoardRuntime().onBoardPowerStateChanged(state);
        }
        settleElectrical(sim);
        if (state == BoardPowerState.UNPOWERED)
            sim.advanceGeneratedTemporalProfile(.100);
        GeneratedRuntimeInvariant.verify(owner, sim.getBoardModificationController(), sim.elmList);
        GeneratedBoardVerifier.verify(owner, state, sim.getBoardModificationController(), sim.elmList, false);
        // Consume the UI-loop flags only after the real native solver and verifiers ran.
        sim.generatedBoardVerificationPending = false;
        sim.generatedBoardVerificationAnalyzed = false;
        sim.analyzeFlag = false;
        sim.dcAnalysisFlag = false;
        if (state == BoardPowerState.UNPOWERED && "KB".equals(owner.getFaultLocus().getComponentId())) {
            PhysicalPart<?> original = owner.getPhysicalBoardRuntime().getInstalledPart("KB");
            check(!Rb30RelayService.isDischarged(owner, "KB"), "real residual target voltage blocks relay service");
            boolean rejected = false;
            try { owner.getPhysicalBoardRuntime().getMutationProvider("KB").removeInstalledPart(); }
            catch (BoardModificationRejectedException expected) { rejected = true; }
            check(rejected && owner.getPhysicalBoardRuntime().getInstalledPart("KB") == original,
                "residual-energy rejection preserves installed relay identity");
            int steps = 0;
            while (!Rb30RelayService.isDischarged(owner, "KB") && steps < 50) {
                sim.advanceGeneratedTemporalProfile(.100);
                steps++;
            }
            String[] terminals = { "A1", "A2", "COM", "NC", "NO" };
            for (int pin = 0; pin < terminals.length; pin++)
                check(Math.abs(Rb30Behavior.voltage(owner, "KB." + terminals[pin], pin < 2 ? "J1.2" : "JLOAD.2")) < .05,
                    "independent local-return voltage boundary after discharge: " + terminals[pin]);
            check(Rb30RelayService.isDischarged(owner, "KB"), "target relay settles within five simulated seconds");
            check(!RelayOutputBehavior.isDischarged(owner), "default E03 global guard still rejects unrelated stored energy");
            System.out.println("Q30_RESIDUAL_GUARD seed=" + owner.getSeed() + " simulatedSeconds=" + (.1 * (steps + 1)) +
                " upstreamVolts=" + Rb30Behavior.voltage(owner, "CIN.+", "CIN.-"));
        }
        check(state != BoardPowerState.UNPOWERED ||
                sim.boardPowerController.isElectricallyUnpowered(),
            "Q30 service power-off disconnects all external inputs");
    }

    private static void settleMutation(CirSim sim, GeneratedBoardInstance owner,
            BoardModificationController modifications, PhysicalBoardRuntime runtime) {
        check(sim.generatedBoardVerificationPending && sim.analyzeFlag,
            "physical Q30 mutation queues normal graph analysis/verification");
        settleElectrical(sim);
        GeneratedRuntimeInvariant.verify(owner, modifications, sim.elmList);
        GeneratedBoardVerifier.verify(owner, sim.boardPowerController.getState(),
            modifications, sim.elmList, false);
        owner.getConnectionBindings().validateAgainst(owner.getBoard(),
            owner.getSimulationElements(), owner.getComponentBindings(),
            owner.getExternalPowerBindings(), owner.getFaultBinding());
        check(!runtime.isMutationInProgress() && !runtime.isMutationQuarantined(),
            "Q30 scoped mutation commits without quarantine");
        // The native fixture consumes the UI-loop verification queued by the real mutation.
        sim.generatedBoardVerificationPending = false;
        sim.generatedBoardVerificationAnalyzed = false;
        sim.analyzeFlag = false;
        sim.dcAnalysisFlag = false;
    }

    private static void settleElectrical(CirSim sim) {
        sim.analyzeCircuit();
        sim.solverExecutor.advanceSteps(8);
    }

    private static void configureSimulator(CirSim sim) {
        sim.gridSize = 16;
        sim.gridMask = ~15;
        sim.gridRound = 7;
        // Explicit Q30 qualification settings, also selected by the compiled
        // developer entry. Fresh CirSim's adaptive flag otherwise defaults false.
        sim.maxTimeStep = 5e-6;
        sim.minTimeStep = 50e-12;
        sim.adjustTimeStep = true;
    }

    private static boolean containsCatalog(Vector<WorkbenchCatalogEntry> entries,
            String id) {
        if (id == null || id.length() == 0) return false;
        for (WorkbenchCatalogEntry entry : entries)
            if (id.equals(entry.getId())) return true;
        return false;
    }

    private static Map<String, CircuitMeasurementEndpoint> capturePadEndpoints(
            GeneratedBoardInstance owner) {
        Map<String, CircuitMeasurementEndpoint> result =
            new HashMap<String, CircuitMeasurementEndpoint>();
        for (String pad : owner.getBoard().getPadIds())
            result.put(pad, owner.getSimulationBindings().getEndpoint(pad));
        return result;
    }

    private static void verifyPadEndpoints(GeneratedBoardInstance owner,
            Map<String, CircuitMeasurementEndpoint> expected) {
        for (String pad : owner.getBoard().getPadIds())
            check(expected.get(pad) == owner.getSimulationBindings().getEndpoint(pad),
                "Q30 board endpoint identity survives physical service: " + pad);
    }

    private static void check(boolean condition, String message) {
        assertions++;
        if (!condition) throw new AssertionError(message);
    }

    /** Native fixture follows production solver invalidation without GWT repaint. */
    private static final class NativeServiceCirSim extends CirSim {
        boolean nativeProfilesReady;
        @Override void needAnalyze() {
            if (elmList != null && CircuitElm.sim == this) solverExecutor.invalidate();
            analyzeFlag = true;
        }

        @Override void refreshBoardModificationControls() { }
        @Override void repaint() { }
    }
}
