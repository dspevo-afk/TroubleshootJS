package com.lushprojects.circuitjs1.client;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.Vector;

/** Native solver-backed Q30 hypothesis, physical service, and customer retest contract. */
public final class Q30ServiceFlowContractTest {
    private static int assertions;

    private static final String[][] COMMON_EXPECTED = {
        { "DREV_OPEN", "DIODE_OPEN", "DREV", "3" },
        { "REN_OPEN", "RESISTOR_OPEN", "REN", "3" },
        { "SENSOR_A_OPEN", "RESISTOR_OPEN", "RSA", "1" },
        { "DRIVE_A_OPEN", "RESISTOR_OPEN", "RDA", "1" }
    };

    private Q30ServiceFlowContractTest() { }

    public static void main(String[] args) {
        long started = System.nanoTime();
        if (args.length == 2 && "--describe-plan".equals(args[0])) {
            long describedSeed = parseSeed(args[1]);
            System.out.println(describePlan(Rb30Plan.resolve(describedSeed)));
            return;
        }
        // Explicit two-channel regression fixture; the maintained runner
        // exercises the procedural one/two-channel plans independently.
        long[] seeds = { 7L, 3L };
        int selectedHypothesis = -1;
        long selectedSeed = 0L;
        String selectedFault = null;
        boolean procedural = false;
        if (args.length != 0) {
            if (args.length != 4 || !"--seed".equals(args[0]) ||
                    !"--fault".equals(args[2]))
                throw new IllegalArgumentException(
                    "Usage: [--describe-plan <canonical-signed-long>] or " +
                    "[--seed <canonical-signed-long> --fault DREV_OPEN|REN_OPEN|SENSOR_A_OPEN|DRIVE_A_OPEN|RELAY_A_COIL_OPEN|RELAY_B_COIL_OPEN]");
            selectedSeed = parseSeed(args[1]);
            selectedFault = args[3];
            selectedHypothesis = hypothesisIndex(selectedFault,
                Rb30Plan.resolve(selectedSeed));
            seeds = new long[] { selectedSeed };
            procedural = true;
        }
        verifyRelayReadingBoundary();
        CirSim constructionSim = new CirSim();
        configureSimulator(constructionSim);
        for (long seed : seeds) {
            // No-argument runs retain an explicitly named legacy two-channel
            // regression fixture. Maintained --seed runs always resolve the
            // current procedural one/two-channel plan above.
            Rb30Plan plan = procedural ? Rb30Plan.resolve(seed) :
                Rb30Plan.withSupport(seed, Rb30Plan.SupportVariant.STANDARD_35);
            verifySeed(plan, constructionSim, selectedHypothesis);
        }
        System.out.println("Q30_CASE_TIME operation=route-replay-and-service elapsedMillis=" +
            ((System.nanoTime() - started) / 1000000L));
        if (selectedHypothesis < 0) {
            System.out.println("PASS: Q30 service flow contracts " + assertions +
                " assertions seeds=" + seeds.length + " hypotheses=5" +
                " cases=" + (seeds.length * 5));
        } else {
            System.out.println("PASS: Q30 service flow contracts " + assertions +
                " assertions seed=" + selectedSeed + " fault=" + selectedFault);
        }
    }

    private static int hypothesisIndex(String faultId, Rb30Plan plan) {
        String[][] expected = expectedFaults(plan);
        for (int index = 0; index < expected.length; index++)
            if (expected[index][0].equals(faultId)) return index;
        throw new IllegalArgumentException("Unsupported Q30 fault: " + faultId);
    }

    private static String[][] expectedFaults(Rb30Plan plan) {
        String relay = plan.channelCount == 1 ? "KA" : "KB";
        String relayFault = plan.channelCount == 1 ?
            "RELAY_A_COIL_OPEN" : "RELAY_B_COIL_OPEN";
        String[][] result = new String[5][4];
        for (int index = 0; index < COMMON_EXPECTED.length; index++)
            result[index] = COMMON_EXPECTED[index].clone();
        int allChannels = (1 << plan.channelCount) - 1;
        result[0][3] = Integer.toString(allChannels);
        result[1][3] = Integer.toString(allChannels);
        result[4] = new String[] { relayFault, "RELAY_COIL_OPEN", relay,
            Integer.toString(1 << (plan.channelCount - 1)) };
        return result;
    }

    private static int[] inputMasks(Rb30Plan plan) {
        int[] masks = new int[1 << plan.channelCount];
        for (int index = 0; index < masks.length; index++) masks[index] = index;
        return masks;
    }

    private static String[] inputOperations(Rb30Plan plan) {
        return plan.channelCount == 1 ? new String[] {
            Rb30Behavior.SENSORS_LOW, Rb30Behavior.SENSORS_HIGH } : new String[] {
            Rb30Behavior.SENSORS_LOW, Rb30Behavior.SENSORS_A_ONLY,
            Rb30Behavior.SENSORS_B_ONLY, Rb30Behavior.SENSORS_HIGH };
    }

    private static boolean outputOn(int inputMask, int channelIndex) {
        return (inputMask & (1 << channelIndex)) != 0;
    }

    private static long parseSeed(String text) {
        final long seed;
        try { seed = Long.parseLong(text); }
        catch (RuntimeException invalid) {
            throw new IllegalArgumentException("Invalid canonical Q30 seed", invalid);
        }
        if (!Long.toString(seed).equals(text))
            throw new IllegalArgumentException("Noncanonical Q30 seed: " + text);
        return seed;
    }

    /** Export seeded scale metadata without constructing CircuitJS elements. */
    private static String describePlan(Rb30Plan plan) {
        String relay = plan.channelCount == 1 ? "KA" : "KB";
        String relayFault = "RELAY_" + relay.substring(1) + "_COIL_OPEN";
        StringBuilder channels = new StringBuilder();
        for (String channel : plan.channels()) {
            if (channels.length() > 0) channels.append(',');
            channels.append('"').append(channel).append('"');
        }
        int states = 1 << plan.channelCount;
        int observations = (7 + plan.channelCount) * states + 1;
        int workUnits = states + 1;
        return "Q30_CASE_PLAN {\"schema\":1,\"seed\":\"" +
            Long.toString(plan.seed) + "\",\"canonical\":\"" +
            plan.canonical() + "\",\"planEpoch\":" + Rb30Plan.PLAN_VERSION +
            ",\"topology\":\"" + plan.topology() +
            "\",\"channelCount\":" + plan.channelCount +
            ",\"activeChannels\":[" + channels +
            "],\"packageCount\":" + plan.physicalPackageCount() +
            ",\"referenceArrangement\":\"" +
            plan.referenceArrangement.name() + "\",\"support\":{\"C12\":" +
            plan.hasEntryCapacitor + ",\"S5\":" + plan.hasFiveVoltIndicator +
            ",\"S12\":" + plan.hasTwelveVoltIndicator +
            ",\"filters\":" + plan.sensorInputFilterMask +
            ",\"outputIndicators\":" + plan.outputIndicatorMask +
            ",\"bleeder5\":" + plan.hasFiveVoltBleeder +
            "},\"faults\":[{\"id\":\"DREV_OPEN\",\"owner\":\"DREV\"}," +
            "{\"id\":\"REN_OPEN\",\"owner\":\"REN\"}," +
            "{\"id\":\"SENSOR_A_OPEN\",\"owner\":\"RSA\"}," +
            "{\"id\":\"DRIVE_A_OPEN\",\"owner\":\"RDA\"}," +
            "{\"id\":\"" + relayFault + "\",\"owner\":\"" + relay +
            "\"}],\"diagnosticProviderDeveloper\":\"rb30-control-diagnostic@2\"," +
            "\"diagnosticProviderNormal\":\"rb30-control-diagnostic@4\"," +
            "\"diagnosticTemplate\":\"RB30_CHANNEL_INPUT_SWEEP_V1\"," +
            "\"diagnosticSamplesPerHypothesis\":" + observations +
            ",\"temporalContract\":\"RB30_CHANNEL_FUNCTION@3\"," +
            "\"sampleSeconds\":0.03,\"profileWorkUnits\":" + workUnits +
            ",\"customerRetestWorkUnits\":" + workUnits +
            ",\"maxJobMillis\":90000,\"maxJobSteps\":640,\"activeOperationMillis\":5000}";
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

    private static void verifySeed(Rb30Plan plan, CirSim constructionSim,
            int selectedHypothesis) {
        long seed = plan.seed;
        String[][] expected = expectedFaults(plan);
        // CircuitElm constructors call drag(), which uses the active solver's grid.
        CircuitElm.sim = constructionSim;
        if (plan.supportVariant == null)
            check(plan.canonical().equals(Rb30Plan.resolve(seed).canonical()),
                "Q30 plan replays exact current identity for seed " + seed);
        else
            check(plan.channelCount == 2 && plan.supportVariant ==
                    Rb30Plan.SupportVariant.STANDARD_35,
                "Q30 legacy fixture explicitly selects the named two-channel shape");
        CircuitElm.sim = constructionSim;
        Rb30Generator.Candidate oracle = new Rb30Generator().construct(plan);
        verifyPopulation(oracle.faultCandidates, null, seed, expected);
        String firstKey = hypothesisKey(oracle.faultCandidates, expected[0][0]);

        CircuitElm.sim = constructionSim;
        GeneratedBoardInstance first = new Rb30Generator().generateForHypothesis(plan, firstKey);
        CircuitElm.sim = constructionSim;
        GeneratedBoardInstance replay = new Rb30Generator().generateForHypothesis(plan, firstKey);
        check(first.getSeed() == seed && replay.getSeed() == seed,
            "Q30 signed-long seed survives hypothesis construction and replay");
        check(first.getFaultBinding().getFault().getId().equals(expected[0][0]) &&
                replay.getFaultBinding().getFault().getId().equals(expected[0][0]),
            "exact semantic hypothesis key selects the requested fault");
        String physicalFingerprint = PhysicalBoardFingerprint.of(first);
        String layoutFingerprint = first.getPcbLayout().geometryFingerprint();
        check(physicalFingerprint.equals(PhysicalBoardFingerprint.of(replay)) &&
                layoutFingerprint.equals(replay.getPcbLayout().geometryFingerprint()),
            "Q30 first routed realization has exact physical replay for seed " + seed);
        verifyPopulation(first.getFaultCandidates(), first.getFaultBinding(), seed, expected);

        boolean rejectedUnknownId = false;
        CircuitElm.sim = constructionSim;
        try {
            new Rb30Generator().construct(plan, "FOREIGN_Q30_FAULT");
        } catch (IllegalArgumentException invalidFault) {
            rejectedUnknownId = true;
        }
        check(rejectedUnknownId, "foreign forced fault ID rejects before assembly");
        boolean rejectedUnknownKey = false;
        CircuitElm.sim = constructionSim;
        try {
            new Rb30Generator().generateForHypothesis(seed, "FOREIGN_Q30_HYPOTHESIS");
        } catch (IllegalArgumentException invalidKey) {
            rejectedUnknownKey = true;
        }
        check(rejectedUnknownKey, "foreign hypothesis key rejects without producing an owner");

        for (int index = 0; index < expected.length; index++) {
            if (selectedHypothesis >= 0 && index != selectedHypothesis) continue;
            NativeServiceCirSim sim = new NativeServiceCirSim();
            configureSimulator(sim);
            CircuitElm.sim = sim;
            Rb30Generator.Candidate candidate = new Rb30Generator().construct(
                plan, expected[index][0]);
            verifyPopulation(candidate.faultCandidates, candidate.selectedFault, seed, expected);
            GeneratedBoardInstance owner = new Rb30Generator().assemble(candidate,
                first.getPcbLayout().copySealed());
            check(owner.getFaultBinding().getFault().getId().equals(expected[index][0]) &&
                    owner.getFaultBinding().getFault().getTargetComponentId()
                        .equals(expected[index][2]),
                "selected Q30 hypothesis retains its independent physical locus");
            check(layoutFingerprint.equals(owner.getPcbLayout().geometryFingerprint()) &&
                    physicalFingerprint.equals(PhysicalBoardFingerprint.of(owner)),
                "all five selected hypotheses share the exact seed layout");
            verifyServiceFlow(plan, index, expected, owner, physicalFingerprint,
                layoutFingerprint, sim);
            CircuitElm.sim = constructionSim;
        }
    }

    private static void verifyPopulation(Vector<GeneratedFaultCandidate> candidates,
            GeneratedFaultBinding selected, long seed, String[][] expected) {
        check(candidates != null && candidates.size() == expected.length,
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
        for (String[] fault : expected) {
            GeneratedFaultCandidate candidate = byId.get(fault[0]);
            check(candidate != null, "complete Q30 population contains " + fault[0]);
            check(candidate.getFault().getType() == GeneratedFaultType.valueOf(fault[1]) &&
                    candidate.getFault().getTargetComponentId().equals(fault[2]) &&
                    candidate.isAdmitted(),
                "independent Q30 oracle matches admitted " + fault[0] + " locus/type");
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

    private static void verifyServiceFlow(Rb30Plan plan, int hypothesisIndex,
            String[][] expected,
            GeneratedBoardInstance owner, String expectedPhysical, String expectedLayout,
            final NativeServiceCirSim sim) {
        long seed = plan.seed;
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
        verifyPlanSupport(owner, plan);

        GeneratedDiagnosticSolvabilityAdmission.validateStructural(owner);
        verifyDiagnosticOwnership(owner, hypothesisIndex, expected);
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
        check(behavior.getInputs() == 0 && behavior.healthy(owner, 0),
            "healthy channel-input profile finishes at real LOW inputs before fault application");
        verifyOutputSupport(owner, plan);
        sampleFused12(owner, "powered-before-service");
        owner.getFaultBinding().setApplied(true);
        behavior.prepareFaultedProfile(sim, owner);
        behavior.verifyFaultedProfile(sim, owner, modifications, BoardPowerState.POWERED);
        int highMask = (1 << plan.channelCount) - 1;
        check(behavior.getInputs() == highMask && !behavior.healthy(owner, highMask) &&
                behavior.getObservedBehavior() == GeneratedObservedBehavior.RELAY_LOAD_NOT_SWITCHING,
            "actual fault preparation observes a symptom after LOW-before-fault then HIGH");
        int symptomMask = observeFaultMask(owner, sim, plan);
        int expectedMask = Integer.parseInt(expected[hypothesisIndex][3]);
        check(symptomMask == expectedMask,
            "CircuitJS fault symptom mask matches independent locus oracle for " +
                expected[hypothesisIndex][0] + " got=" + symptomMask);
        sim.nativeProfilesReady = true;
        double beforeStatusTime = sim.t;
        int beforeStatusInputs = behavior.getInputs();
        behavior.getRepairStatus(sim, owner, modifications, BoardPowerState.POWERED, false);
        check(sim.t == beforeStatusTime && behavior.getInputs() == beforeStatusInputs,
            "live repair-status observation cannot drive inputs or advance solver time");
        check(!customerRetest(owner, sim),
            "unrepaired Q30 owner fails actual active-channel customer retest");

        String component = expected[hypothesisIndex][2];
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
            "catalog replacement passes actual active-channel customer retest");
        check(repairedTruthTable(owner, sim, plan),
            plan.channelCount == 2 && plan.referenceArrangement ==
                    Rb30Plan.ReferenceArrangement.SHARED_DIRECT ?
                "shared-direct A-only/B-only states keep the two outputs uncoupled" :
                "independent solver voltages satisfy each active input/output state");
        System.out.println("PASS: Q30 service flow seed=" + seed + " fault=" +
            expected[hypothesisIndex][0] + " target=" + component +
            " fingerprintHash=" + Integer.toHexString(expectedPhysical.hashCode()));
    }

    private static void verifyDiagnosticOwnership(GeneratedBoardInstance owner,
            int selectedIndex, String[][] expected) {
        GeneratedDiagnosticProvider provider = owner.getDiagnosticProvider();
        check(provider != null && provider.getObservationProgram() != null &&
                provider.getDiagnosticPlan() != null,
            "Q30 owns a production diagnostic plan and observation program");
        Set<String> populationKeys = new HashSet<String>();
        for (GeneratedFaultCandidate candidate : owner.getFaultCandidates())
            populationKeys.add(candidate.getHypothesisKey());
        Set<String> declaredKeys = new HashSet<String>(
            owner.getDiagnosticSolvabilityContract().getHypothesisKeys());
        check(populationKeys.equals(declaredKeys) && declaredKeys.size() == expected.length,
            "Q30 provider contract accounts for every hypothesis exactly once");
        for (String[] fault : expected) {
            String replacementId = provider.getCorrectCatalogId(owner, fault[2]);
            WorkbenchPartsProvider parts = owner.getPhysicalBoardRuntime()
                .getWorkbenchPartsProvider(fault[2]);
            check(parts != null && containsCatalog(parts.getCatalogEntries(), replacementId),
                "Q30 provider maps " + fault[0] + " to an owned legal replacement");
        }
        check(owner.getFaultBinding().getFault().getId().equals(expected[selectedIndex][0]),
            "diagnostic provider and live owner agree on the selected hypothesis");
    }

    private static void verifyPlanSupport(GeneratedBoardInstance owner, Rb30Plan plan) {
        PhysicalBoardRuntime runtime = owner.getPhysicalBoardRuntime();
        check((runtime.getInstalledPart("C12") != null) == plan.hasEntryCapacitor,
            "physical C12 presence matches this resolved plan, including absence");
        PhysicalBoardRuntimeCapability capability = runtime.getCapability(
            PowerDomainRuntimeCapability.CAPABILITY_ID);
        check(capability instanceof PowerDomainRuntimeCapability,
            "Q30 installs the current owner's power-domain observation capability");
        if (capability instanceof PowerDomainRuntimeCapability) {
            PowerDomainRuntimeCapability power =
                (PowerDomainRuntimeCapability) capability;
            CircuitPostMeasurementEndpoint positive =
                (CircuitPostMeasurementEndpoint) owner.getSimulationBindings()
                    .getEndpoint("J1.1");
            CircuitPostMeasurementEndpoint controlReturn =
                (CircuitPostMeasurementEndpoint) owner.getSimulationBindings()
                    .getEndpoint("J1.2");
            MeasurementReferencePolicy.Result installedOwnerReference =
                power.assessReference(MeasurementReferencePolicy.Mode.DIFFERENTIAL,
                    positive, controlReturn);
            check(installedOwnerReference.getDecision() ==
                    MeasurementReferencePolicy.Decision.ADMITTED,
                "Q30 power-domain capability is installed against the live owner and bindings");
            PowerDomainContract contract = power.getContract();
            PowerDomainContract.Rail fused12 = contract.getRails().get("FUSED12");
            check(fused12 != null && fused12.getStorageRequirement() ==
                    PowerDomainContract.StorageRequirement.OBSERVATION_REQUIRED,
                "FUSED12 retains its observation obligation with or without C12");
        }
    }

    private static void verifyOutputSupport(GeneratedBoardInstance owner, Rb30Plan plan) {
        double rail5 = Rb30Behavior.voltage(owner, "U1.OUTPUT", "U1.RETURN");
        double main12 = Rb30Behavior.voltage(owner, "J1.1", "J1.2");
        check(!Double.isNaN(rail5) && !Double.isInfinite(rail5) &&
                rail5 >= 4.75 && rail5 <= 5.25,
            "real regulated 5 V rail is in range at healthy LOW");
        check(matches(main12, true), "real main 12 V input rail is present");
        for (String channel : plan.channels()) {
            String led = "LEDOUT_" + channel;
            check((owner.getPhysicalBoardRuntime().getInstalledPart(led) != null) ==
                    plan.hasOutputIndicator(channel),
                "output indicator population follows the resolved channel support flags: " + channel);
            if (plan.hasOutputIndicator(channel))
                check(Math.abs(Rb30Behavior.voltage(owner, led + ".K", "JLOAD.2")) <= .05,
                    "output LED cathode returns through the real down-channel LOAD_RETURN: " + channel);
        }
    }

    private static void sampleFused12(GeneratedBoardInstance owner, String phase) {
        double value = Rb30Behavior.voltage(owner, "DREV.A", "J1.2");
        check(!Double.isNaN(value) && !Double.isInfinite(value),
            "real bound FUSED12 endpoint is finite at " + phase);
        System.out.println("Q30_FUSED12_SAMPLE seed=" + owner.getSeed() +
            " phase=" + phase + " c12=" +
            (owner.getPhysicalBoardRuntime().getInstalledPart("C12") != null) +
            " volts=" + value + " storage=OBSERVATION_REQUIRED");
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

    private static int observeFaultMask(GeneratedBoardInstance owner, CirSim sim,
            Rb30Plan plan) {
        int symptomMask = 0;
        String[] operations = inputOperations(plan);
        for (int condition = 0; condition < operations.length; condition++) {
            GeneratedCustomerRetestResult control = owner.invokeOperation(
                operations[condition], sim);
            check(control == null, "Q30 input control " + operations[condition] + " executes");
            int mask = inputMasks(plan)[condition];
            int channelIndex = 0;
            for (String channel : plan.channels()) {
                double value = Rb30Behavior.voltage(owner, "JO" + channel + ".1",
                    "JO" + channel + ".2");
                if (!matches(value, outputOn(mask, channelIndex)))
                    symptomMask |= 1 << channelIndex;
                channelIndex++;
            }
        }
        return symptomMask;
    }

    private static boolean repairedTruthTable(GeneratedBoardInstance owner, CirSim sim,
            Rb30Plan plan) {
        String[] operations = inputOperations(plan);
        int[] masks = inputMasks(plan);
        for (int condition = 0; condition < operations.length; condition++) {
            owner.invokeOperation(operations[condition], sim);
            int channelIndex = 0;
            for (String channel : plan.channels()) {
                if (!matches(Rb30Behavior.voltage(owner, "JO" + channel + ".1",
                        "JO" + channel + ".2"), outputOn(masks[condition], channelIndex)))
                    return false;
                channelIndex++;
            }
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
            if (state == BoardPowerState.UNPOWERED)
                sampleFused12(owner, "immediately-after-source-disconnect");
        }
        settleElectrical(sim);
        if (state == BoardPowerState.UNPOWERED) {
            sim.advanceGeneratedTemporalProfile(.100);
            sampleFused12(owner, "after-100ms-local-relay-window");
        }
        GeneratedRuntimeInvariant.verify(owner, sim.getBoardModificationController(), sim.elmList);
        GeneratedBoardVerifier.verify(owner, state, sim.getBoardModificationController(), sim.elmList, false);
        // Consume the UI-loop flags only after the real native solver and verifiers ran.
        sim.generatedBoardVerificationPending = false;
        sim.generatedBoardVerificationAnalyzed = false;
        sim.analyzeFlag = false;
        sim.dcAnalysisFlag = false;
        String relay = owner.getFaultLocus().getComponentId();
        if (state == BoardPowerState.UNPOWERED &&
                ("KA".equals(relay) || "KB".equals(relay))) {
            PhysicalPart<?> original = owner.getPhysicalBoardRuntime().getInstalledPart(relay);
            check(!Rb30RelayService.isDischarged(owner, relay), "real residual target voltage blocks relay service");
            boolean rejected = false;
            try { owner.getPhysicalBoardRuntime().getMutationProvider(relay).removeInstalledPart(); }
            catch (BoardModificationRejectedException expected) { rejected = true; }
            check(rejected && owner.getPhysicalBoardRuntime().getInstalledPart(relay) == original,
                "residual-energy rejection preserves installed relay identity");
            int steps = 0;
            while (!Rb30RelayService.isDischarged(owner, relay) && steps < 50) {
                sim.advanceGeneratedTemporalProfile(.100);
                steps++;
            }
            String[] terminals = { "A1", "A2", "COM", "NC", "NO" };
            for (int pin = 0; pin < terminals.length; pin++)
                check(Math.abs(Rb30Behavior.voltage(owner, relay + "." + terminals[pin], pin < 2 ? "J1.2" : "JLOAD.2")) < .05,
                    "independent local-return voltage boundary after discharge: " + terminals[pin]);
            check(Rb30RelayService.isDischarged(owner, relay), "target relay settles within five simulated seconds");
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

    static void configureSimulator(CirSim sim) {
        configureSimulator(sim, 5e-6);
    }

    static void configureSimulator(CirSim sim, double maxStep) {
        if (!Double.isFinite(maxStep) || maxStep <= 0)
            throw new IllegalArgumentException("Invalid Q30 solver maximum step");
        // Production circuitjs1.onModuleLoad() creates this shared English
        // fallback table before constructing CirSim. Native fixtures skip the
        // entry point, so initialize the same table rather than masking a real
        // solver stop with an NPE inside CirSim.LS().
        CirSim.localizationMap = new HashMap<String, String>();
        sim.gridSize = 16;
        sim.gridMask = ~15;
        sim.gridRound = 7;
        // Explicit Q30 qualification settings. The paired solver-sensitivity
        // contract varies only this maximum; adaptive stepping and its lower
        // bound remain fixed. Fresh CirSim's adaptive flag defaults false.
        sim.timeStep = maxStep;
        sim.maxTimeStep = maxStep;
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
    static class NativeServiceCirSim extends CirSim {
        boolean nativeProfilesReady;
        @Override void needAnalyze() {
            if (elmList != null && CircuitElm.sim == this) solverExecutor.invalidate();
            analyzeFlag = true;
        }

        @Override void refreshBoardModificationControls() { }
        @Override void repaint() { }
    }
}
