package com.lushprojects.circuitjs1.client;

import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;
import java.util.Vector;

/** One predeclared Q30 corpus row through actual normal physical admission. */
public final class Q30NormalCorpusContractTest {
    private static final int EXPECTED_HYPOTHESES = 5;
    private static final String[][] FIXED_EXPECTED_FAULTS = {
        { "DREV_OPEN", "DIODE_OPEN", "DREV" },
        { "REN_OPEN", "RESISTOR_OPEN", "REN" },
        { "SENSOR_A_OPEN", "RESISTOR_OPEN", "RSA" },
        { "DRIVE_A_OPEN", "RESISTOR_OPEN", "RDA" }
    };

    private static int assertions;

    private Q30NormalCorpusContractTest() { }

    public static void main(String[] args) {
        long seed = parseSeed(args);
        CirSim sim = new CirSim();
        sim.gridSize = 16;
        sim.gridMask = ~15;
        sim.gridRound = 7;
        CircuitElm.sim = sim;

        Row row = new Row(seed);
        Rb30Generator.Candidate coldCandidate = null;
        Rb30Generator.Candidate warmCandidate = null;
        GeneratedBoardInstance coldOwner = null;
        GeneratedBoardInstance warmOwner = null;
        Vector<GeneratedBoardInstance> hypothesisOwners =
            new Vector<GeneratedBoardInstance>();
        Vector<Rb30Generator.Candidate> unassembledCandidates =
            new Vector<Rb30Generator.Candidate>();
        long totalStarted = System.nanoTime();
        Throwable failure = null;
        try {
            Rb30Plan plan = Rb30Plan.resolve(seed);
            row.topologyAxis = plan.topologyAxis();
            row.support = "C12:" + plan.hasEntryCapacitor + ",S5:" +
                plan.hasFiveVoltIndicator + ",S12:" +
                plan.hasTwelveVoltIndicator + ",F:" +
                plan.sensorInputFilterMask + ",O:" +
                plan.outputIndicatorMask + ",B5:" + plan.hasFiveVoltBleeder;
            row.partCount = plan.physicalPackageCount();

            long started = System.nanoTime();
            coldCandidate = new Rb30Generator().construct(plan);
            row.coldConstructionNanos = System.nanoTime() - started;
            unassembledCandidates.add(coldCandidate);
            check(coldCandidate.plan.canonical().equals(plan.canonical()),
                "cold construction changed its frozen plan");

            row.coldRoute = route(coldCandidate);
            row.coldRouteNanos = row.coldRoute.totalNanos;
            if (row.coldRoute.result == null)
                throw new AssertionError("medium route returned no complete receipt");

            started = System.nanoTime();
            warmCandidate = new Rb30Generator().construct(Rb30Plan.resolve(seed));
            row.warmConstructionNanos = System.nanoTime() - started;
            unassembledCandidates.add(warmCandidate);
            check(warmCandidate.plan.canonical().equals(plan.canonical()),
                "warm construction changed its frozen plan");

            row.warmRoute = route(warmCandidate);
            row.warmRouteNanos = row.warmRoute.totalNanos;
            if (row.warmRoute.result == null)
                throw new AssertionError("exact replay route returned no complete receipt");
            check(row.coldRoute.result.toCanonical().equals(
                    row.warmRoute.result.toCanonical()),
                "exact route replay changed its complete medium-policy receipt");
            check(row.coldRoute.placementAdvances == row.warmRoute.placementAdvances &&
                    row.coldRoute.routingAdvances == row.warmRoute.routingAdvances,
                "exact route replay changed phase advance counts");
            row.routeOutcome = row.coldRoute.result.accepted() ? "SUCCESS" : "REJECTED";

            if (!row.coldRoute.result.accepted()) {
                check(!row.coldRoute.result.getFailure().isEmpty() &&
                        "REJECTED".equals(row.coldRoute.result.getStatistics().outcome),
                    "route rejection lacks the complete expected rejection receipt");
                check(!row.warmRoute.result.accepted() &&
                        !row.warmRoute.result.getFailure().isEmpty(),
                    "replayed route did not retain the same rejection outcome");
                row.outcome = "REJECTED";
                row.normalAdmissionOutcome = "REJECTED_ROUTE";
                row.contractValidated = true;
            } else {
                check(row.warmRoute.result.accepted(),
                    "exact route replay changed accepted/rejected outcome");
                PcbBoardLayout coldLayout = row.coldRoute.result.getLayout();
                PcbBoardLayout warmLayout = row.warmRoute.result.getLayout();
                check(coldLayout != null && warmLayout != null,
                    "accepted route omitted its physical layout");
                String coldGeometry = coldLayout.geometryFingerprint();
                String warmGeometry = warmLayout.geometryFingerprint();
                check(coldGeometry.equals(warmGeometry),
                    "exact route replay changed routed physical layout geometry");
                row.layoutFingerprintHash = Integer.toHexString(coldGeometry.hashCode());

                String selectedRoute =
                    row.coldRoute.result.getStatistics().selectedRoutePolicy;
                if (MediumBoardPhysicalPolicy.P05_ONE_FACE.equals(selectedRoute)) {
                    check(MediumBoardPhysicalPolicy.P05_ONE_FACE.equals(
                            row.warmRoute.result.getStatistics().selectedRoutePolicy),
                        "exact route replay changed the selected P05 policy");
                    boolean rejectedByNormalConstruction = false;
                    Rb30Generator normalGenerator = new Rb30Generator();
                    try {
                        normalGenerator.constructFromAcceptedRoute(plan, row.coldRoute.result);
                    } catch (GenerationJob.Rejected expected) {
                        rejectedByNormalConstruction = true;
                    }
                    check(rejectedByNormalConstruction,
                        "one-face normal construction did not emit a retryable rejection");
                    // A fresh construction on this generator also proves the
                    // rejection happened before its one-use live allocation.
                    unassembledCandidates.add(normalGenerator.construct(plan));
                    row.outcome = "REJECTED";
                    row.normalAdmissionOutcome = "REJECTED_POLICY";
                    row.normalAdmissionReason = "P05_ONLY_NORMAL_ADMISSION_REQUIRES_P07";
                    row.contractValidated = true;
                } else {
                    check(MediumBoardPhysicalPolicy.P07_FULLER_TWO_LAYER.equals(selectedRoute),
                        "accepted medium result has no supported normal route policy");
                    row.outcome = "ACCEPTED";
                    row.normalAdmissionOutcome = "ACCEPTED";

                    GeneratedPhysicalAdmission coldAdmission =
                        MediumBoardNormalAdmission.fromAcceptedRoute(coldCandidate.board(),
                            row.coldRoute.result, plan.layoutSeed, plan.routingSeed);
                    GeneratedPhysicalAdmission warmAdmission =
                        MediumBoardNormalAdmission.fromAcceptedRoute(warmCandidate.board(),
                            row.warmRoute.result, plan.layoutSeed, plan.routingSeed);
                    coldAdmission.requireConstruction(coldCandidate.board(), coldLayout);
                    warmAdmission.requireConstruction(warmCandidate.board(), warmLayout);

                    Rb30Generator generator = new Rb30Generator();
                    started = System.nanoTime();
                    coldOwner = generator.assembleNormal(coldCandidate, coldLayout,
                        coldAdmission);
                    row.coldAdmissionAssemblyNanos = System.nanoTime() - started;
                    started = System.nanoTime();
                    warmOwner = generator.assembleNormal(warmCandidate, warmLayout,
                        warmAdmission);
                    row.warmAdmissionAssemblyNanos = System.nanoTime() - started;
                    unassembledCandidates.remove(coldCandidate);
                    unassembledCandidates.remove(warmCandidate);

                    started = System.nanoTime();
                    String coldPhysical = PhysicalBoardFingerprint.of(coldOwner);
                    String assembledGeometry = coldOwner.getPcbLayout().geometryFingerprint();
                    check(coldPhysical.equals(PhysicalBoardFingerprint.of(warmOwner)) &&
                            assembledGeometry.equals(warmOwner.getPcbLayout().geometryFingerprint()),
                        "independently routed copies changed physical geometry or copper");
                    row.physicalFingerprintHash = Integer.toHexString(coldPhysical.hashCode());
                    row.layoutFingerprintHash = Integer.toHexString(assembledGeometry.hashCode());

                    verifyNormalOwner(coldOwner, coldAdmission, plan);
                    verifyNormalOwner(warmOwner, warmAdmission, plan);
                    verifyDisjointSimulationGraphs(coldOwner, warmOwner);

                    GeneratedDiagnosticProvider provider = coldOwner.getDiagnosticProvider();
                    Vector<GeneratedFaultCandidate> population = coldOwner.getFaultCandidates();
                    verifyPopulation(population, plan);
                    String expectedPlan = GeneratedDiagnosticProgram.describePlan(
                        provider.getDiagnosticPlan());
                    String expectedProgram = provider.getObservationProgram().canonical();
                    check(countMeasurements(provider.getObservationProgram()) ==
                            expectedObservationCount(plan),
                        "normal observation program is not the active-channel measurement contract");
                    row.hypothesisCount = population.size();
                    row.observationCount = countMeasurements(provider.getObservationProgram());

                    for (GeneratedFaultCandidate hypothesis : population) {
                        CircuitElm.sim = sim;
                        GeneratedBoardInstance replay = provider.generateHypothesis(hypothesis);
                        hypothesisOwners.add(replay);
                        verifyReplay(replay, hypothesis, coldOwner, coldAdmission,
                            coldPhysical, assembledGeometry, expectedPlan, expectedProgram, plan);
                    }
                    verifyAllGraphsDisjoint(coldOwner, warmOwner, hypothesisOwners);
                    row.normalStructuralReplayNanos = System.nanoTime() - started;
                    row.contractValidated = true;
                }
            }
        } catch (Throwable problem) {
            failure = problem;
            row.failure = boundedFailure(problem);
        } finally {
            long cleanupStarted = System.nanoTime();
            Throwable cleanupFailure = disposeAll(coldOwner, warmOwner,
                hypothesisOwners, unassembledCandidates);
            row.cleanupNanos = System.nanoTime() - cleanupStarted;
            row.totalNanos = System.nanoTime() - totalStarted;
            row.cleanup = cleanupFailure == null;
            if (cleanupFailure != null) {
                row.failure = appendFailure(row.failure,
                    "cleanup: " + boundedFailure(cleanupFailure));
                if (failure == null) failure = cleanupFailure;
                else failure.addSuppressed(cleanupFailure);
            }
        }

        if (row.coldRoute != null && row.warmRoute != null) {
            System.out.println("Q30_CORPUS_JSON:" + row.toJson());
        }
        if (failure != null)
            throw new AssertionError("Q30 normal corpus contract failed for seed " +
                Long.toString(seed) + ": " + boundedFailure(failure));
        if (!row.cleanup || !row.contractValidated)
            throw new AssertionError("Q30 normal corpus row is incomplete for seed " +
                Long.toString(seed));
        System.out.println("PASS: Q30 normal corpus contracts outcome=" + row.outcome +
            " routeOutcome=" + row.routeOutcome + " normalAdmission=" +
            row.normalAdmissionOutcome + " cleanup=true assertions=" + assertions +
            " seed=" + Long.toString(seed));
    }

    private static long parseSeed(String[] args) {
        if (args == null || args.length != 2 || !"--seed".equals(args[0]))
            throw new IllegalArgumentException(
                "Usage: --seed <canonical signed-long>");
        final long seed;
        try {
            seed = Long.parseLong(args[1]);
        } catch (NumberFormatException invalid) {
            throw new IllegalArgumentException("Q30 corpus seed is not a signed long");
        }
        if (!Long.toString(seed).equals(args[1]))
            throw new IllegalArgumentException("Q30 corpus seed is not canonical");
        return seed;
    }

    /** The first six deterministic advance units are placement; later units route. */
    private static RouteRun route(Rb30Generator.Candidate candidate) {
        SeededPcbLayoutGenerator generator = new SeededPcbLayoutGenerator();
        SeededPcbLayoutGenerator.Session session = generator.begin(candidate.board(),
            candidate.plan.layoutSeed, candidate.plan.routingSeed);
        RouteRun result = new RouteRun();
        long started = System.nanoTime();
        int advances = 0;
        while (true) {
            long unitStarted = System.nanoTime();
            boolean complete;
            try {
                complete = session.advance();
            } catch (PcbRoutingRejectedException expectedRouteRejection) {
                accountAdvance(result, System.nanoTime() - unitStarted, advances);
                advances++;
                result.rejection = expectedRouteRejection.getMessage();
                result.result = session.mediumResult();
                break;
            }
            accountAdvance(result, System.nanoTime() - unitStarted, advances);
            advances++;
            if (complete) {
                result.result = session.mediumResult();
                break;
            }
            if (advances > maxRouteAdvances(candidate.plan))
                throw new AssertionError("medium routing left insufficient work for the full production proof");
        }
        result.totalNanos = System.nanoTime() - started;
        result.totalAdvances = advances;
        check(advances <= maxRouteAdvances(candidate.plan),
            "completed route left insufficient work for the full production proof");
        check(result.result != null,
            "medium route session completed without its full result object");
        check(result.placementAdvances == MediumBoardPhysicalPolicy.PLACEMENT_CANDIDATES,
            "medium route placement phase did not consume its declared candidate count");
        MediumBoardPhysicalPolicy.Statistics stats = result.result.getStatistics();
        check(stats.placementCandidates == MediumBoardPhysicalPolicy.PLACEMENT_CANDIDATES,
            "medium route receipt changed its placement candidate count");
        check(stats.oneFaceAttempts <= MediumBoardPhysicalPolicy.ROUTING_CANDIDATES &&
                stats.twoLayerAttempts <= MediumBoardPhysicalPolicy.ROUTING_CANDIDATES &&
                stats.routeAttempts == stats.oneFaceAttempts + stats.twoLayerAttempts,
            "medium route exceeded or misreported the existing route budget");
        check(result.result.accepted() == "SUCCESS".equals(stats.outcome),
            "medium route outcome and full statistics disagree");
        check(result.result.accepted() || "REJECTED".equals(stats.outcome),
            "medium route has an unknown result state");
        return result;
    }

    /** Independent channel-count oracle for the accepted diagnostic program. */
    private static int expectedObservationCount(Rb30Plan plan) {
        int channels = plan.channelCount;
        return (7 + channels) * (1 << channels) + 1;
    }

    /** Reserve the full active proof before allowing physical-route slices. */
    private static int expectedProofUnits(Rb30Plan plan) {
        int channels = plan.channelCount;
        int conditions = 1 << channels;
        int workUnits = conditions + 1;
        int programSteps = (9 + channels) * conditions + 3;
        return EXPECTED_HYPOTHESES * (11 + programSteps +
            3 * (workUnits - 1) + (workUnits - 1) + 4);
    }

    private static int maxRouteAdvances(Rb30Plan plan) {
        int workUnits = (1 << plan.channelCount) + 1;
        int otherUnits = 4 + 2 * workUnits;
        return GenerationCoordinator.MAX_JOB_STEPS -
            expectedProofUnits(plan) - otherUnits;
    }

    private static void accountAdvance(RouteRun run, long elapsedNanos,
            int priorAdvanceCount) {
        if (priorAdvanceCount < MediumBoardPhysicalPolicy.PLACEMENT_CANDIDATES) {
            run.placementAdvanceNanos += elapsedNanos;
            run.placementAdvances++;
        } else {
            run.routingAdvanceNanos += elapsedNanos;
            run.routingAdvances++;
        }
    }

    private static void verifyNormalOwner(GeneratedBoardInstance owner,
            GeneratedPhysicalAdmission admission, Rb30Plan plan) {
        check(owner != null && !owner.isDeveloperOnlyFaultRoute(),
            "normal route assembled a developer-only owner");
        check(owner.getSeed() == plan.seed &&
                Rb30Plan.FAMILY_ID.equals(owner.getCircuitFamilyId()) &&
                plan.topology().equals(owner.getTopologyVariantId()),
            "normal owner identity differs from its seeded plan");
        check(owner.getPhysicalAdmission() == admission,
            "normal owner lost its accepted-route admission token");
        owner.requireNormalPhysicalAdmission();
        admission.requireNormal(owner);
        GeneratedDiagnosticSolvabilityAdmission.validateStructural(owner);
        check(GeneratedDiagnosticProofService.requiredWorkUnits(owner, true) ==
                expectedProofUnits(plan),
            "normal proof work differs from the independently reserved complete population");

        TroubleshootBoard board = owner.getBoard();
        board.validate();
        check(board.getComponentIds().size() == plan.physicalPackageCount() &&
                owner.getPhysicalSpecifications().getPhysicalComponentIds().size() ==
                    plan.physicalPackageCount() &&
                owner.getPhysicalBoardRuntime().getSlots().size() ==
                    plan.physicalPackageCount(),
            "normal physical package, specification and runtime slot census differ");
        check((board.getComponent("RLED") != null) == plan.hasFiveVoltIndicator &&
                (board.getComponent("LED1") != null) == plan.hasFiveVoltIndicator &&
                (board.getComponent("RLED12") != null) == plan.hasTwelveVoltIndicator &&
                (board.getComponent("LED12") != null) == plan.hasTwelveVoltIndicator &&
                (board.getComponent("C12") != null) == plan.hasEntryCapacitor &&
                (board.getComponent("RBLEED5") != null) == plan.hasFiveVoltBleeder,
            "normal assembly did not realize the planned rail-support functions");
        for (String channel : plan.channels())
            check((board.getComponent("CFLT_" + channel) != null) ==
                    plan.hasSensorInputFilter(channel) &&
                (board.getComponent("LEDOUT_" + channel) != null) ==
                    plan.hasOutputIndicator(channel),
                "normal assembly did not realize channel support " + channel);
        for (String componentId : board.getComponentIds()) {
            BoardComponent component = board.getComponent(componentId);
            check(component != null && owner.getPhysicalSpecifications()
                    .getPhysicalDefinition(componentId) != null &&
                    owner.getPhysicalSpecifications().getPackage(componentId)
                        .isEquivalentTo(component.getPhysicalPackage()) &&
                    owner.getPhysicalBoardRuntime().getSlot(componentId) != null,
                "normal component is missing package, physical specification or slot: " +
                    componentId);
        }
        owner.getPhysicalBoardRuntime().validate();
        owner.getOperationalStates().requireOwnedBy(owner.getSimulationElements());

        Vector<String> padIds = board.getPadIds();
        GeneratedBoardEndpointOracle oracle = owner.getDeveloperBoardEndpointOracle();
        check(oracle.getPadIds().size() == padIds.size(),
            "generated endpoint capture does not cover every board pad");
        for (String padId : padIds) {
            CircuitMeasurementEndpoint live = owner.getSimulationBindings().getEndpoint(padId);
            check(live != null && live == oracle.getEndpoint(padId),
                "normal pad endpoint is absent or differs from the captured composition: " +
                    padId);
        }
        check(owner.getExternalPowerBindings().hasControlsForAllInputs() &&
                board.getPowerInputIds().size() == 2 + plan.channelCount,
            "normal composition lost a controllable external source");
        check(!owner.getFaultBinding().isApplied(),
            "normal generation applied a hidden fault before diagnostic service");
        Set<CircuitElm> ownerElements = java.util.Collections.newSetFromMap(
            new IdentityHashMap<CircuitElm, Boolean>());
        ownerElements.addAll(owner.getSimulationElements());
        for (GeneratedFaultCandidate hypothesis : owner.getFaultCandidates()) {
            check(!hypothesis.getBinding().isApplied(),
                "normal generation left a hypothesis effect applied");
            for (CircuitElm privateElement : hypothesis.getPrivateSimulationElements())
                check(ownerElements.contains(privateElement),
                    "hypothesis-owned electrical state escaped its generated graph");
            String faultOwner = hypothesis.getFault().getTargetComponentId();
            PhysicalBoardSlot faultSlot = owner.getPhysicalBoardRuntime().getSlot(faultOwner);
            check(faultSlot != null && faultSlot.getInstalledPart() != null,
                "diagnostic fault owner has no installed physical package: " + faultOwner);
        }

        GeneratedDiagnosticProvider provider = owner.getDiagnosticProvider();
        GeneratedDiagnosticPlan diagnosticPlan = provider.getDiagnosticPlan();
        GeneratedDiagnosticProgram program = provider.getObservationProgram();
        GeneratedDiagnosticSolvabilityContract contract =
            owner.getDiagnosticSolvabilityContract();
        check(provider.getProviderId() != null && provider.getProviderId().length() != 0 &&
                !contract.isDeveloperFixture() && contract.getPlans().size() == 1,
            "normal diagnostic composition became missing or developer-only");
        program.validatePlan(diagnosticPlan);
        for (String target : diagnosticPlan.getProbeTargetIds())
            check(board.getPad(target) != null &&
                    owner.getSimulationBindings().getEndpoint(target) != null,
                "diagnostic probe is not a composed, electrically bound board pad: " + target);
        check(diagnosticPlan.getReferenceTargetId() != null &&
                board.getPad(diagnosticPlan.getReferenceTargetId()) != null &&
                owner.getSimulationBindings().getEndpoint(
                    diagnosticPlan.getReferenceTargetId()) != null,
            "diagnostic reference is not a composed board pad");
        check(countMeasurements(program) == expectedObservationCount(plan),
            "normal diagnostic program differs from the active-channel measurement population");
        for (GeneratedDiagnosticProgram.Step step : program.getSteps()) {
            if (step.kind == GeneratedDiagnosticProgram.Kind.DC_VOLTAGE ||
                    step.kind == GeneratedDiagnosticProgram.Kind.RESISTANCE ||
                    step.kind == GeneratedDiagnosticProgram.Kind.CONTINUITY ||
                    step.kind == GeneratedDiagnosticProgram.Kind.DIODE) {
                check((diagnosticPlan.getReferenceTargetId().equals(step.red) ||
                        diagnosticPlan.getProbeTargetIds().contains(step.red)) &&
                        (diagnosticPlan.getReferenceTargetId().equals(step.black) ||
                        diagnosticPlan.getProbeTargetIds().contains(step.black)),
                    "diagnostic measurement uses an undeclared board probe");
            }
        }
    }

    private static void verifyPopulation(Vector<GeneratedFaultCandidate> candidates,
            Rb30Plan plan) {
        check(candidates.size() == EXPECTED_HYPOTHESES,
            "normal diagnostic population is not the complete five-hypothesis set");
        Map<String, GeneratedFaultCandidate> byId =
            new HashMap<String, GeneratedFaultCandidate>();
        Set<String> keys = new HashSet<String>();
        Set<String> owners = new HashSet<String>();
        for (GeneratedFaultCandidate candidate : candidates) {
            String id = candidate.getFault().getId();
            check(byId.put(id, candidate) == null &&
                    keys.add(candidate.getHypothesisKey()),
                "normal hypothesis identity or key is duplicated");
            check(candidate.isAdmitted() && candidate.isCompatible() &&
                    candidate.getFault().getCircuitFamilyId().equals(Rb30Plan.FAMILY_ID) &&
                    candidate.getFault().getSelectionSeed() == plan.seed,
                "normal hypothesis is not admitted for this exact family and seed");
            owners.add(candidate.getFault().getTargetComponentId());
        }
        Set<String> expectedOwners = new HashSet<String>();
        for (String[] expected : FIXED_EXPECTED_FAULTS) {
            GeneratedFaultCandidate actual = byId.get(expected[0]);
            check(actual != null && actual.getFault().getType() ==
                    GeneratedFaultType.valueOf(expected[1]) &&
                    expected[2].equals(actual.getFault().getTargetComponentId()),
                "normal hypothesis population changed the independent owner/type table");
            expectedOwners.add(expected[2]);
        }
        String relay = plan.channelCount == 1 ? "KA" : "KB";
        String relayFaultId = "RELAY_" + relay.substring(1) + "_COIL_OPEN";
        GeneratedFaultCandidate relayFault = byId.get(relayFaultId);
        check(relayFault != null &&
                relayFault.getFault().getType() == GeneratedFaultType.RELAY_COIL_OPEN &&
                relay.equals(relayFault.getFault().getTargetComponentId()),
            "normal hypothesis population changed its active relay owner");
        expectedOwners.add(relay);
        check(owners.equals(expectedOwners),
            "normal population lost an independent physical fault owner");
        check(byId.get(plan.selectedFault).getBinding() != null,
            "seed-selected fault is absent from the normal population");
    }

    private static void verifyReplay(GeneratedBoardInstance replay,
            GeneratedFaultCandidate hypothesis, GeneratedBoardInstance normalOwner,
            GeneratedPhysicalAdmission admission, String physicalFingerprint,
            String geometryFingerprint, String expectedPlan, String expectedProgram,
            Rb30Plan plan) {
        check(replay != null && !replay.isDeveloperOnlyFaultRoute(),
            "normal provider replay returned a developer-only graph");
        check(replay.getSeed() == plan.seed &&
                Rb30Plan.FAMILY_ID.equals(replay.getCircuitFamilyId()) &&
                replay.getTopologyVariantId().equals(normalOwner.getTopologyVariantId()),
            "normal provider replay changed its family, seed or topology");
        check(replay.getFaultBinding().getFault().getHypothesisKey()
                .equals(hypothesis.getHypothesisKey()) &&
                replay.getFaultBinding().getFault().getId()
                    .equals(hypothesis.getFault().getId()),
            "normal provider did not replay the exact requested hypothesis");
        check(replay.getPhysicalAdmission() == admission,
            "normal replay did not retain the accepted physical admission");
        replay.requireNormalPhysicalAdmission();
        admission.requireNormal(replay);
        check(physicalFingerprint.equals(PhysicalBoardFingerprint.of(replay)) &&
                geometryFingerprint.equals(replay.getPcbLayout().geometryFingerprint()),
            "normal provider replay changed the physical layout or copper");
        verifyNormalOwner(replay, admission, plan);
        String actualPlan = GeneratedDiagnosticProgram.describePlan(
            replay.getDiagnosticProvider().getDiagnosticPlan());
        String actualProgram = replay.getDiagnosticProvider()
            .getObservationProgram().canonical();
        check(expectedPlan.equals(actualPlan) && expectedProgram.equals(actualProgram),
            "normal provider replay changed its plan or active-channel observation program");
        verifyPopulation(replay.getFaultCandidates(), plan);
    }

    private static void verifyDisjointSimulationGraphs(GeneratedBoardInstance first,
            GeneratedBoardInstance second) {
        Set<CircuitElm> elements = java.util.Collections.newSetFromMap(
            new IdentityHashMap<CircuitElm, Boolean>());
        for (CircuitElm element : first.getSimulationElements())
            check(elements.add(element), "normal graph contains a duplicate element identity");
        for (CircuitElm element : second.getSimulationElements())
            check(elements.add(element), "exact route copy reused an electrical element identity");
    }

    private static void verifyAllGraphsDisjoint(GeneratedBoardInstance normalOwner,
            GeneratedBoardInstance warmOwner,
            Vector<GeneratedBoardInstance> hypothesisOwners) {
        Set<CircuitElm> elements = java.util.Collections.newSetFromMap(
            new IdentityHashMap<CircuitElm, Boolean>());
        addGraph(elements, normalOwner, "normal owner");
        addGraph(elements, warmOwner, "exact route copy");
        for (int index = 0; index < hypothesisOwners.size(); index++)
            addGraph(elements, hypothesisOwners.get(index), "hypothesis " + index);
    }

    private static void addGraph(Set<CircuitElm> elements,
            GeneratedBoardInstance owner, String description) {
        for (CircuitElm element : owner.getSimulationElements())
            check(elements.add(element),
                "private circuit graph aliases another allocated graph: " + description);
    }

    private static int countMeasurements(GeneratedDiagnosticProgram program) {
        int result = 0;
        for (GeneratedDiagnosticProgram.Step step : program.getSteps())
            if (step.kind == GeneratedDiagnosticProgram.Kind.DC_VOLTAGE ||
                    step.kind == GeneratedDiagnosticProgram.Kind.RESISTANCE ||
                    step.kind == GeneratedDiagnosticProgram.Kind.CONTINUITY ||
                    step.kind == GeneratedDiagnosticProgram.Kind.DIODE)
                result++;
        return result;
    }

    private static Throwable disposeAll(GeneratedBoardInstance coldOwner,
            GeneratedBoardInstance warmOwner,
            Vector<GeneratedBoardInstance> hypothesisOwners,
            Vector<Rb30Generator.Candidate> unassembledCandidates) {
        Throwable failure = null;
        Vector<CircuitElm> elements = new Vector<CircuitElm>();
        Vector<GeneratedBoardInstance> owners =
            new Vector<GeneratedBoardInstance>();
        if (coldOwner != null) owners.add(coldOwner);
        if (warmOwner != null) owners.add(warmOwner);
        owners.addAll(hypothesisOwners);
        for (GeneratedBoardInstance owner : owners) {
            try {
                owner.getExternalPowerBindings().setConnected(false);
            } catch (Throwable problem) {
                failure = retain(failure, problem);
            }
            elements.addAll(owner.getSimulationElements());
        }
        for (Rb30Generator.Candidate candidate : unassembledCandidates) {
            try {
                candidate.assembly.power.setConnected(false);
            } catch (Throwable problem) {
                failure = retain(failure, problem);
            }
            elements.addAll(candidate.elements());
        }

        Set<CircuitElm> disposed = java.util.Collections.newSetFromMap(
            new IdentityHashMap<CircuitElm, Boolean>());
        for (CircuitElm element : elements) {
            if (element == null || !disposed.add(element)) continue;
            try {
                element.delete();
            } catch (Throwable problem) {
                failure = retain(failure, problem);
            }
        }
        return failure;
    }

    private static Throwable retain(Throwable first, Throwable next) {
        if (first == null) return next;
        if (next != null && first != next) first.addSuppressed(next);
        return first;
    }

    private static String boundedFailure(Throwable failure) {
        if (failure == null) return "unknown failure";
        String message = failure.getMessage();
        if (message == null || message.length() == 0)
            message = failure.getClass().getSimpleName();
        message = message.replace('\r', ' ').replace('\n', ' ').replace('\t', ' ');
        return message.length() <= 384 ? message : message.substring(0, 384);
    }

    private static String appendFailure(String first, String next) {
        if (first == null || first.length() == 0) return next;
        return first + "; " + next;
    }

    private static void check(boolean condition, String message) {
        assertions++;
        if (!condition) throw new AssertionError("Q30 corpus assertion " +
            assertions + ": " + message);
    }

    private static String quote(String value) {
        if (value == null) return "null";
        StringBuilder result = new StringBuilder("\"");
        for (int index = 0; index < value.length(); index++) {
            char ch = value.charAt(index);
            if (ch == '\"' || ch == '\\') result.append('\\').append(ch);
            else if (ch == '\b') result.append("\\b");
            else if (ch == '\f') result.append("\\f");
            else if (ch == '\n') result.append("\\n");
            else if (ch == '\r') result.append("\\r");
            else if (ch == '\t') result.append("\\t");
            else if (ch < 0x20) result.append('?');
            else result.append(ch);
        }
        return result.append('\"').toString();
    }

    private static String strings(Vector<String> values) {
        StringBuilder result = new StringBuilder("[");
        for (int index = 0; index < values.size(); index++) {
            if (index != 0) result.append(',');
            result.append(quote(values.get(index)));
        }
        return result.append(']').toString();
    }

    private static final class RouteRun {
        MediumBoardPhysicalPolicy.Result result;
        String rejection;
        long placementAdvanceNanos;
        long routingAdvanceNanos;
        long totalNanos;
        int placementAdvances;
        int routingAdvances;
        int totalAdvances;
    }

    private static final class Row {
        final long seed;
        String topologyAxis;
        String support;
        String outcome;
        String routeOutcome;
        String normalAdmissionOutcome;
        String normalAdmissionReason;
        int partCount;
        int hypothesisCount;
        int observationCount;
        String physicalFingerprintHash;
        String layoutFingerprintHash;
        long coldConstructionNanos;
        long coldRouteNanos;
        long coldAdmissionAssemblyNanos = -1;
        long warmConstructionNanos;
        long warmRouteNanos;
        long warmAdmissionAssemblyNanos = -1;
        long normalStructuralReplayNanos = -1;
        long cleanupNanos;
        long totalNanos;
        boolean cleanup;
        boolean contractValidated;
        String failure;
        RouteRun coldRoute;
        RouteRun warmRoute;

        Row(long seed) { this.seed = seed; }

        String toJson() {
            StringBuilder result = new StringBuilder("{");
            field(result, "schema", "1", false);
            field(result, "planEpoch", Integer.toString(Rb30Plan.PLAN_VERSION), true);
            field(result, "seed", quote(Long.toString(seed)), true);
            field(result, "outcome", quote(outcome), true);
            field(result, "routeOutcome", quote(routeOutcome), true);
            field(result, "normalAdmissionOutcome",
                quote(normalAdmissionOutcome), true);
            field(result, "normalAdmissionReason",
                quote(normalAdmissionReason), true);
            field(result, "topologyAxis", quote(topologyAxis), true);
            field(result, "support", quote(support), true);
            field(result, "partCount", Integer.toString(partCount), true);
            field(result, "contractValidated", Boolean.toString(contractValidated), true);
            field(result, "cleanup", Boolean.toString(cleanup), true);
            field(result, "hypothesisCount", Integer.toString(hypothesisCount), true);
            field(result, "observationCount", Integer.toString(observationCount), true);
            field(result, "physicalFingerprintHash", quote(physicalFingerprintHash), true);
            field(result, "layoutFingerprintHash", quote(layoutFingerprintHash), true);
            field(result, "coldConstructionNanos", Long.toString(coldConstructionNanos), true);
            field(result, "coldAdmissionAssemblyNanos",
                nullableNanos(coldAdmissionAssemblyNanos), true);
            field(result, "coldGenerationNanos",
                nullableNanos(coldAdmissionAssemblyNanos < 0 ? -1 :
                    coldConstructionNanos + coldAdmissionAssemblyNanos), true);
            field(result, "coldRouteNanos", Long.toString(coldRouteNanos), true);
            field(result, "coldPlacementAdvanceNanos",
                Long.toString(coldRoute.placementAdvanceNanos), true);
            field(result, "coldRoutingAdvanceNanos",
                Long.toString(coldRoute.routingAdvanceNanos), true);
            field(result, "coldPlacementAdvances",
                Integer.toString(coldRoute.placementAdvances), true);
            field(result, "coldRoutingAdvances",
                Integer.toString(coldRoute.routingAdvances), true);
            field(result, "warmConstructionNanos", Long.toString(warmConstructionNanos), true);
            field(result, "warmAdmissionAssemblyNanos",
                nullableNanos(warmAdmissionAssemblyNanos), true);
            field(result, "warmGenerationNanos",
                nullableNanos(warmAdmissionAssemblyNanos < 0 ? -1 :
                    warmConstructionNanos + warmAdmissionAssemblyNanos), true);
            field(result, "warmRouteNanos", Long.toString(warmRouteNanos), true);
            field(result, "warmPlacementAdvanceNanos",
                Long.toString(warmRoute.placementAdvanceNanos), true);
            field(result, "warmRoutingAdvanceNanos",
                Long.toString(warmRoute.routingAdvanceNanos), true);
            field(result, "cleanupNanos", Long.toString(cleanupNanos), true);
            field(result, "normalStructuralReplayNanos",
                nullableNanos(normalStructuralReplayNanos), true);
            field(result, "totalNanos", Long.toString(totalNanos), true);
            field(result, "routeTimingMethod",
                quote("first six session advances=placement; remaining advances=routing"), true);
            field(result, "proofPhase", quote("NOT_RUN_BY_NORMAL_CORPUS_CONTRACT"), true);
            field(result, "coldRouteCanonical",
                quote(coldRoute.result.toCanonical()), true);
            field(result, "warmRouteCanonical",
                quote(warmRoute.result.toCanonical()), true);
            field(result, "coldRouteOutcomes",
                strings(coldRoute.result.getStatistics().routeOutcomes), true);
            field(result, "warmRouteOutcomes",
                strings(warmRoute.result.getStatistics().routeOutcomes), true);
            field(result, "routeRejection", quote(coldRoute.rejection), true);
            field(result, "validationFailure", quote(failure), true);
            return result.append('}').toString();
        }

        private static void field(StringBuilder result, String name, String value,
                boolean comma) {
            if (comma) result.append(',');
            result.append(quote(name)).append(':').append(value);
        }

        private static String nullableNanos(long value) {
            return value < 0 ? "null" : Long.toString(value);
        }
    }
}
