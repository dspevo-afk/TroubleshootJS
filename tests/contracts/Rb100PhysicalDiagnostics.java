package com.lushprojects.circuitjs1.client;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.UUID;
import java.util.Vector;

/** Current native observations and focused test-only placement diagnostics for RB100. */
public final class Rb100PhysicalDiagnostics {
    private static final long SEED = 9007199254740993L;
    private static final long PLACEMENT_DIAGNOSTIC_SEED = -6587958367974952701L;
    private static final long ROUTING_DIAGNOSTIC_ROUTING_SEED = 8445301674744508279L;
    private static final long CASE_LIMIT_NANOS = 90000000000L;
    private static final long UNIT_LIMIT_NANOS = 5000000000L;
    private static final int WORK_LIMIT = 640;
    private static final long CLEANUP_LIMIT_MILLIS = 120000L;
    private static final int EXPECTED_COMPONENTS = 100;
    private static final int MAX_PLACEMENTS = 80;
    private static final int MAX_ROUTE_ATTEMPTS = 800;
    private static final int MAX_ROUTE_EXPANSIONS = 160000000;
    private static final int MAX_ACCESS_CELLS = 700000;
    private static int assertions;

    private Rb100PhysicalDiagnostics() { }

    static void runPlacementDiagnosticContracts() {
        long suiteStarted = System.nanoTime();
        int beforeAssertions = assertions;
        require(SeededPcbLayoutGenerator.CURRENT_VERSION == 25 &&
                "tsj-generation-dependencies-v26".equals(
                    GenerationDependencyContext.INTERPRETATION_EPOCH) &&
                Rb100Plan.PLAN_VERSION == 1 && Rb100Plan.MODEL_VERSION == 4 &&
                Rb100Placement.INTENT_VERSION == 9,
            "placement diagnostics identify current layout25/dependency-v26/plan1/model4/intent9 inputs");
        PlacementDiagnosticCase[] cases = new PlacementDiagnosticCase[] {
            runStructuralPlacementDiagnostic(), runCorridorWitnessPlacementDiagnostic()
        };
        int failures = 0;
        for (PlacementDiagnosticCase result : cases) {
            System.out.println("PCB_PLACEMENT_DIAGNOSTIC " + result.toJson());
            if (!"PASS".equals(result.status)) failures++;
        }
        long suiteMillis = nanosToMillis(System.nanoTime() - suiteStarted);
        if (cases.length != 2) failures++;
        if (failures != 0) {
            System.out.println("PCB_PLACEMENT_DIAGNOSTIC_SUMMARY {\"status\":\"FAIL\",\"cases\":" +
                cases.length + ",\"expectedCases\":2,\"failures\":" + failures +
                ",\"assertions\":" + (assertions - beforeAssertions) +
                ",\"suiteMs\":" + suiteMillis +
                ",\"layoutVersion\":" + SeededPcbLayoutGenerator.CURRENT_VERSION +
                ",\"dependencyEpoch\":\"" + GenerationDependencyContext.INTERPRETATION_EPOCH +
                "\",\"planVersion\":" + Rb100Plan.PLAN_VERSION +
                ",\"modelVersion\":" + Rb100Plan.MODEL_VERSION +
                ",\"physicalIntentVersion\":" + Rb100Placement.INTENT_VERSION + "}");
            throw new AssertionError("PCB placement diagnostics failures=" + failures);
        }
        System.out.println("PASS: PCB placement diagnostics contracts {\"status\":\"PASS\",\"cases\":2," +
            "\"fixtures\":[\"MEDIUM_GENERIC_EDGE_REGION_20_PART\",\"MEDIUM_ISOLATION_CORRIDOR_WITNESS_20_PART\"]," +
            "\"layoutVersion\":" + SeededPcbLayoutGenerator.CURRENT_VERSION +
            ",\"dependencyEpoch\":\"" + GenerationDependencyContext.INTERPRETATION_EPOCH +
            "\",\"planVersion\":" + Rb100Plan.PLAN_VERSION +
            ",\"modelVersion\":" + Rb100Plan.MODEL_VERSION +
            ",\"physicalIntentVersion\":" + Rb100Placement.INTENT_VERSION +
            ",\"assertions\":" + (assertions - beforeAssertions) +
            ",\"suiteMs\":" + suiteMillis +
            ",\"scope\":\"CURRENT_PLANNER_DIAGNOSTIC_CONTROLS_ONLY\"}");
    }

    static void runP07PolicyDiagnosticContracts() {
        long suiteStarted = System.nanoTime();
        int beforeAssertions = assertions;
        final String runId = UUID.randomUUID().toString();
        int failures = 0;
        P07PolicyDiagnosticCase[] results = new P07PolicyDiagnosticCase[2];
        Rb100Plan.Variant[] variants = variants();
        for (int index = 0; index < variants.length; index++) {
            P07PolicyDiagnosticCase result = runP07PolicyDiagnosticCase(variants[index], runId);
            results[index] = result;
            System.out.println("RB100_P07_POLICY_DIAGNOSTIC " + result.toJson());
            if (!"PASS".equals(result.status)) failures++;
        }
        RoutingInputMetadata summaryMetadata = null;
        if (results[0] != null && results[1] != null &&
                results[0].inputMetadata != null && results[1].inputMetadata != null &&
                results[0].inputMetadata.sameAs(results[1].inputMetadata)) {
            summaryMetadata = results[0].inputMetadata;
        } else {
            failures++;
        }
        long suiteMillis = nanosToMillis(System.nanoTime() - suiteStarted);
        if (results.length != 2 || failures != 0) {
            System.out.println("RB100_P07_POLICY_DIAGNOSTIC_SUMMARY {\"status\":\"FAIL\",\"cases\":" +
                results.length + ",\"expectedCases\":2,\"failures\":" + failures +
                ",\"protocolVersion\":2,\"runId\":\"" + runId + "\",\"suiteMs\":" +
                suiteMillis + ",\"layoutVersion\":" + SeededPcbLayoutGenerator.CURRENT_VERSION +
                ",\"dependencyEpoch\":\"" +
                jsonFull(GenerationDependencyContext.INTERPRETATION_EPOCH) +
                "\",\"planVersion\":" + Rb100Plan.PLAN_VERSION +
                ",\"modelVersion\":" + Rb100Plan.MODEL_VERSION +
                ",\"physicalIntentVersion\":" + Rb100Placement.INTENT_VERSION +
                routingInputMetadataFields(summaryMetadata) +
                ",\"scope\":\"OBSERVATIONAL_PRIVATE_P07_POLICY_ONLY\"," +
                "\"layoutQualification\":false}");
            throw new AssertionError("RB100 P07 policy diagnostic failures=" + failures);
        }
        System.out.println("PASS: RB100 P07 policy diagnostic contracts {\"status\":\"PASS\",\"cases\":2," +
            "\"authoredVariants\":2,\"variants\":[\"SHARED_HYSTERETIC_LINEAR\"," +
            "\"SEPARATE_DIRECT_SWITCHING\"],\"protocolVersion\":2,\"runId\":\"" + runId +
            "\",\"seed\":\"" + SEED + "\",\"placementSeed\":\"" +
            PLACEMENT_DIAGNOSTIC_SEED + "\",\"routingSeed\":\"" +
            ROUTING_DIAGNOSTIC_ROUTING_SEED + "\",\"layoutVersion\":" +
            SeededPcbLayoutGenerator.CURRENT_VERSION + ",\"dependencyEpoch\":\"" +
            jsonFull(GenerationDependencyContext.INTERPRETATION_EPOCH) +
            "\",\"planVersion\":" + Rb100Plan.PLAN_VERSION +
            ",\"modelVersion\":" + Rb100Plan.MODEL_VERSION +
            ",\"physicalIntentVersion\":" + Rb100Placement.INTENT_VERSION +
            routingInputMetadataFields(summaryMetadata) +
            ",\"assertions\":" + (assertions - beforeAssertions) +
            ",\"suiteMs\":" + suiteMillis +
            ",\"scope\":\"OBSERVATIONAL_PRIVATE_P07_POLICY_ONLY\"," +
            "\"layoutQualification\":false}");
    }

    static void runP07BranchDiagnosticContracts() {
        long suiteStarted = System.nanoTime();
        int beforeAssertions = assertions;
        final String runId = UUID.randomUUID().toString();
        int failures = 0;
        BranchDiagnosticCase[] results = new BranchDiagnosticCase[2];
        Rb100Plan.Variant[] variants = variants();
        for (int index = 0; index < variants.length; index++) {
            BranchDiagnosticCase result = runAuthoredBranchDiagnostic(variants[index], runId);
            results[index] = result;
            System.out.println("RB100_P07_BRANCH_DIAGNOSTIC " + result.toJson());
            if (!"PASS".equals(result.status)) failures++;
        }
        RoutingInputMetadata summaryMetadata = null;
        if (results.length == 2 && results[0] != null && results[1] != null &&
                results[0].inputMetadata != null && results[1].inputMetadata != null &&
                results[0].inputMetadata.sameAs(results[1].inputMetadata)) {
            summaryMetadata = results[0].inputMetadata;
        } else {
            failures++;
        }
        long suiteMillis = nanosToMillis(System.nanoTime() - suiteStarted);
        if (results.length != 2 || failures != 0) {
            System.out.println("RB100_P07_BRANCH_DIAGNOSTIC_SUMMARY {\"status\":\"FAIL\",\"cases\":" +
                results.length + ",\"expectedCases\":2,\"failures\":" + failures +
                ",\"protocolVersion\":2,\"runId\":\"" + jsonFull(runId) +
                "\",\"layoutVersion\":" + SeededPcbLayoutGenerator.CURRENT_VERSION +
                ",\"dependencyEpoch\":\"" +
                jsonFull(GenerationDependencyContext.INTERPRETATION_EPOCH) +
                "\",\"planVersion\":" + Rb100Plan.PLAN_VERSION +
                ",\"modelVersion\":" + Rb100Plan.MODEL_VERSION +
                ",\"physicalIntentVersion\":" + Rb100Placement.INTENT_VERSION +
                routingInputMetadataFields(summaryMetadata) +
                ",\"scope\":\"OBSERVATIONAL_FIXED_P07_BRANCHES_ONLY\"," +
                "\"layoutQualification\":false}");
            throw new AssertionError("RB100 P07 branch diagnostic failures=" + failures);
        }
        String census = "[{\"variant\":\"" + jsonFull(results[0].subject) +
            "\",\"branchCount\":" + results[0].branches.size() +
            ",\"expansions\":" + results[0].routeResult.expansions +
            ",\"outcome\":\"" + jsonFull(results[0].routeResult.outcome) + "\"}," +
            "{\"variant\":\"" + jsonFull(results[1].subject) +
            "\",\"branchCount\":" + results[1].branches.size() +
            ",\"expansions\":" + results[1].routeResult.expansions +
            ",\"outcome\":\"" + jsonFull(results[1].routeResult.outcome) + "\"}]";
        System.out.println("PASS: RB100 P07 branch diagnostic contracts {\"status\":\"PASS\",\"cases\":2," +
            "\"authoredVariants\":2,\"variants\":[\"SHARED_HYSTERETIC_LINEAR\"," +
            "\"SEPARATE_DIRECT_SWITCHING\"],\"protocolVersion\":2,\"runId\":\"" +
            jsonFull(runId) + "\",\"seed\":\"" + SEED + "\",\"placementSeed\":\"" +
            PLACEMENT_DIAGNOSTIC_SEED + "\",\"routingSeed\":\"" +
            ROUTING_DIAGNOSTIC_ROUTING_SEED + "\",\"fixedPlacementAttempt\":4," +
            "\"layoutVersion\":" + SeededPcbLayoutGenerator.CURRENT_VERSION +
            ",\"dependencyEpoch\":\"" +
            jsonFull(GenerationDependencyContext.INTERPRETATION_EPOCH) + "\",\"planVersion\":" +
            Rb100Plan.PLAN_VERSION + ",\"modelVersion\":" + Rb100Plan.MODEL_VERSION +
            ",\"physicalIntentVersion\":" + Rb100Placement.INTENT_VERSION +
            routingInputMetadataFields(summaryMetadata) + ",\"variantCensus\":" + census +
            ",\"assertions\":" + (assertions - beforeAssertions) +
            ",\"suiteMs\":" + suiteMillis +
            ",\"scope\":\"OBSERVATIONAL_FIXED_P07_BRANCHES_ONLY\"," +
            "\"layoutQualification\":false}");
    }

    private static P07PolicyDiagnosticCase runP07PolicyDiagnosticCase(
            final Rb100Plan.Variant variant, String runId) {
        final long operationStarted = System.nanoTime();
        final Budget budget = new Budget(operationStarted);
        final P07PolicyDiagnosticCase result = new P07PolicyDiagnosticCase(
            variant.name(), SEED, runId);
        final NativeContext[] context = new NativeContext[1];
        final Rb100Plan[] plan = new Rb100Plan[1];
        final Rb100ElectricalPilot.Candidate[] candidate =
            new Rb100ElectricalPilot.Candidate[1];
        final BoardIdentity[] identity = new BoardIdentity[1];
        final SeededPcbLayoutGenerator[] generator = new SeededPcbLayoutGenerator[1];
        final SeededPcbLayoutGenerator.Session[] session =
            new SeededPcbLayoutGenerator.Session[1];
        final MediumBoardPhysicalPolicy.Result[] policyResult =
            new MediumBoardPhysicalPolicy.Result[1];
        final PcbRoutingRejectedException[] terminalRejection =
            new PcbRoutingRejectedException[1];
        final boolean[] terminal = new boolean[] { false };
        try {
            result.phase = "CURRENT_SOURCE_IDENTITY";
            unit(budget, "p07-source-identity", new UnitAction() {
                public void run() {
                    require(SeededPcbLayoutGenerator.CURRENT_VERSION == 25 &&
                        "tsj-generation-dependencies-v26".equals(
                            GenerationDependencyContext.INTERPRETATION_EPOCH) &&
                        Rb100Plan.PLAN_VERSION == 1 && Rb100Plan.MODEL_VERSION == 4 &&
                        Rb100Placement.INTENT_VERSION == 9,
                        "P07 observation identifies layout25/dependency-v26/plan1/model4/intent9");
                    require(MediumBoardPhysicalPolicy.PLACEMENT_CANDIDATES == 6 &&
                        MediumBoardPhysicalPolicy.ROUTING_CANDIDATES == 3 &&
                        PcbLayerRoutingPrototype.MAX_EXPANSIONS == 1000000 &&
                        PcbLayerRoutingPrototype.MAX_BRANCH_EXPANSIONS == 100000 &&
                        PcbLayerRoutingPrototype.MAX_CELLS == 250000 &&
                        PcbLayerRoutingPrototype.ORDERINGS == 3 &&
                        PcbLayerRoutingPrototype.Policy.FULLER_TWO_LAYER.vias == 48,
                        "P07 observation retains the existing placement, routing, branch, grid, ordering and via caps");
                    require(MAX_PLACEMENTS == 80 && MAX_ROUTE_ATTEMPTS == 800 &&
                        MAX_ROUTE_EXPANSIONS == 160000000 && MAX_ACCESS_CELLS == 700000 &&
                        CASE_LIMIT_NANOS == 90000000000L && WORK_LIMIT == 640 &&
                        UNIT_LIMIT_NANOS == 5000000000L && CLEANUP_LIMIT_MILLIS == 120000L,
                        "P07 observation retains existing aggregate and per-case resource ceilings");
                }
            });
            result.phase = "NATIVE_CONTEXT";
            unit(budget, "p07-native-context", new UnitAction() {
                public void run() {
                    context[0] = new NativeContext();
                    context[0].activate();
                }
            });
            result.phase = "CANDIDATE_CONSTRUCTION";
            unit(budget, "p07-authored-candidate-construction", new UnitAction() {
                public void run() {
                    plan[0] = Rb100Plan.create(SEED, variant);
                    candidate[0] = Rb100ElectricalPilot.construct(plan[0]);
                }
            });
            identity[0] = valueUnit(budget, "p07-authored-board-identity",
                new UnitValue<BoardIdentity>() {
                    public BoardIdentity run() {
                        result.components = candidate[0].board().getComponentIds().size();
                        result.pads = candidate[0].board().getPadIds().size();
                        result.nets = candidate[0].board().getNetIds().size();
                        require(result.components == EXPECTED_COMPONENTS && result.pads == 246 &&
                            (result.nets == 59 || result.nets == 60),
                            "P07 observation uses the actual authored 100-component, 246-pad, 59-or-60-net board");
                        return BoardIdentity.capture(candidate[0]);
                    }
                });

            result.phase = "PROCEDURAL_DECLARATION";
            unit(budget, "p07-procedural-layout-declaration", new UnitAction() {
                public void run() { ProceduralPcbLayout.declare(candidate[0].board()); }
            });
            unit(budget, "p07-post-declaration-input-identity", new UnitAction() {
                public void run() {
                    identity[0].requireUnchanged(candidate[0]);
                    result.inputMetadata = RoutingInputMetadata.capture(
                        candidate[0].board().getPlacementConstraints());
                    result.inputMetadata.requireCurrentP07Treatment();
                    require(MediumBoardPhysicalPolicy.selected(
                            candidate[0].board().getPlacementConstraints()),
                        "actual procedural board selects the existing MEDIUM_BOARD policy");
                }
            });
            final long[] namedSeeds = valueUnit(budget, "p07-named-layout-seeds",
                new UnitValue<long[]>() {
                    public long[] run() {
                        return new long[] {
                            ProceduralPcbLayout.seed(Rb100Plan.FAMILY_ID, SEED,
                                NamedRandomStreams.Concern.PLACEMENT),
                            ProceduralPcbLayout.seed(Rb100Plan.FAMILY_ID, SEED,
                                NamedRandomStreams.Concern.ROUTING)
                        };
                    }
                });
            result.placementSeed = namedSeeds[0];
            result.routingSeed = namedSeeds[1];
            unit(budget, "p07-assert-named-seeds", new UnitAction() {
                public void run() {
                    require(result.placementSeed == PLACEMENT_DIAGNOSTIC_SEED &&
                        result.routingSeed == ROUTING_DIAGNOSTIC_ROUTING_SEED,
                        "P07 session uses the exact frozen signed placement and routing seeds");
                }
            });

            final SeededPcbLayoutGenerator.AttemptObserver observer =
                new SeededPcbLayoutGenerator.AttemptObserver() {
                    public void check(int attempt) {
                        result.observerChecks++;
                        budget.checkpoint();
                    }
                };
            result.phase = "MEDIUM_SESSION_BEGIN";
            unit(budget, "p07-generator-and-session-begin", new UnitAction() {
                public void run() {
                    generator[0] = new SeededPcbLayoutGenerator(
                        StandardPcbFootprintProviders.createRegistry(), observer);
                    session[0] = generator[0].begin(candidate[0].board(),
                        result.placementSeed, result.routingSeed);
                }
            });

            result.phase = "MEDIUM_SESSION_ADVANCE";
            while (!terminal[0]) {
                result.advanceCalls++;
                try {
                    terminal[0] = valueUnit(budget,
                        "p07-session-advance-" + result.advanceCalls,
                        new UnitValue<Boolean>() {
                            public Boolean run() {
                                return Boolean.valueOf(session[0].advance());
                            }
                        }).booleanValue();
                    if (terminal[0]) result.sessionTerminalStatus = "ROUTED";
                } catch (PcbRoutingRejectedException expectedExhaustion) {
                    terminalRejection[0] = expectedExhaustion;
                    terminal[0] = true;
                    result.sessionTerminalStatus = "EXHAUSTED";
                }
            }
            result.phase = "TERMINAL_POLICY_RESULT_CAPTURE";
            valueUnit(budget, "p07-terminal-policy-result-capture", new UnitValue<Object>() {
                public Object run() {
                    require(result.advanceCalls > 0 && result.observerChecks > 0,
                        "P07 policy observation advances the real session under budget checkpoints");
                    policyResult[0] = session[0].mediumResult();
                    require(policyResult[0] != null && policyResult[0].getStatistics() != null,
                        "terminal medium session exposes its actual immutable result receipt");
                    result.policyResult = policyResult[0];
                    result.sessionRejection = terminalRejection[0];
                    if (policyResult[0].accepted()) {
                        require(terminalRejection[0] == null &&
                            "ROUTED".equals(result.sessionTerminalStatus) &&
                            "SUCCESS".equals(policyResult[0].getStatistics().outcome) &&
                            MediumBoardPhysicalPolicy.P07_FULLER_TWO_LAYER.equals(
                                policyResult[0].getStatistics().selectedRoutePolicy) &&
                            policyResult[0].getLayout() != null &&
                            session[0].result() == policyResult[0].getLayout(),
                            "completed medium session returns its selected routed layout and success receipt");
                    } else {
                        require(terminalRejection[0] != null &&
                            "EXHAUSTED".equals(result.sessionTerminalStatus) &&
                            terminalRejection[0].isExhausted() &&
                            terminalRejection[0].getSeed() == result.placementSeed &&
                            "REJECTED".equals(policyResult[0].getStatistics().outcome) &&
                            policyResult[0].getFailure() != null,
                            "expected exhausted rejection carries the installed terminal rejected-policy receipt");
                    }
                    verifyP07PolicyStatistics(policyResult[0]);
                    identity[0].requireUnchanged(candidate[0]);
                    require(CircuitElm.sim == context[0].sim &&
                        CirSim.theSim == context[0].sim && context[0].sim.elmList == context[0].rawGraph &&
                        context[0].rawGraph.isEmpty(),
                        "P07 geometry session leaves the authored CircuitJS elements uninstalled");
                    result.solverGraphIdentityStatus = "PASS";
                    result.boardIdentityStatus = "PASS";
                    result.captureLayoutOutput();
                    return null;
                }
            });
        } catch (RuntimeException failure) {
            result.failurePhase = result.phase;
            result.failure = failure;
        } catch (Error failure) {
            result.failurePhase = result.phase;
            result.failure = failure;
        }

        result.phase = "FINAL_REPORT_PREPARATION";
        try {
            unit(budget, "p07-final-report-preparation", new UnitAction() {
                public void run() { result.prepareFacts(); }
            });
        } catch (RuntimeException failure) {
            if (result.failurePhase == null) result.failurePhase = result.phase;
            result.failure = append(result.failure, failure);
        } catch (Error failure) {
            if (result.failurePhase == null) result.failurePhase = result.phase;
            result.failure = append(result.failure, failure);
        }
        result.operationMillis = nanosToMillis(System.nanoTime() - operationStarted);
        cleanupDiagnosticCase(result, context[0], candidate[0]);
        result.workUnits = budget.workUnits;
        result.maxUnitMillis = budget.maxUnitMillis();
        result.status = result.failure == null && result.cleanupPassed &&
            result.policyResult != null && result.inputMetadata != null &&
            result.boardIdentityStatus.equals("PASS") &&
            result.solverGraphIdentityStatus.equals("PASS") &&
            ("ROUTED".equals(result.sessionTerminalStatus) ||
                "EXHAUSTED".equals(result.sessionTerminalStatus)) &&
            result.operationMillis <= nanosToMillis(CASE_LIMIT_NANOS) &&
            result.workUnits <= WORK_LIMIT && result.maxUnitMillis <=
                nanosToMillis(UNIT_LIMIT_NANOS) ? "PASS" : "FAIL";
        return result;
    }

    private static void verifyP07PolicyStatistics(MediumBoardPhysicalPolicy.Result result) {
        MediumBoardPhysicalPolicy.Statistics statistics = result.getStatistics();
        require("MEDIUM_BOARD@1".equals(statistics.policyIdentity) &&
            statistics.placementCandidates == MediumBoardPhysicalPolicy.PLACEMENT_CANDIDATES &&
            statistics.placementCandidates <= MAX_PLACEMENTS &&
            statistics.placementRejections >= 0 && statistics.placementEvaluations > 0 &&
            statistics.placementRejections + statistics.rankedPlacementAttempts.size() ==
                statistics.placementCandidates &&
            statistics.rankedPlacementAttempts.size() == statistics.placementScores.size() &&
            statistics.rankedPlacementAttempts.size() <= statistics.placementCandidates,
            "medium policy receipt preserves actual ranked placement candidates and scores");
        require(statistics.oneFaceAttempts == 0 && statistics.oneFaceSuccesses == 0 &&
            statistics.twoLayerAttempts == statistics.routeAttempts &&
            statistics.twoLayerAttempts >= 1 &&
            statistics.twoLayerAttempts <= MediumBoardPhysicalPolicy.ROUTING_CANDIDATES &&
            statistics.routeAttempts <= MAX_ROUTE_ATTEMPTS &&
            statistics.twoLayerSuccesses <= statistics.twoLayerAttempts,
            "requiresTwoLayerRouting skips all P05 attempts and retains bounded P07 counts");
        verifyP07GridEligibility(statistics);
        int p05Outcomes = 0, p07Outcomes = 0, p07Successes = 0;
        Vector<Integer> attemptedP07 = new Vector<Integer>();
        for (String outcome : statistics.routeOutcomes) {
            if (outcome.startsWith(MediumBoardPhysicalPolicy.P05_ONE_FACE + "@")) p05Outcomes++;
            if (outcome.startsWith(MediumBoardPhysicalPolicy.P07_FULLER_TWO_LAYER + "@")) {
                int routeIndex = p07Outcomes++;
                String prefix = MediumBoardPhysicalPolicy.P07_FULLER_TWO_LAYER + "@";
                int attemptEnd = outcome.indexOf('=', prefix.length());
                require(attemptEnd > prefix.length(),
                    "each P07 outcome includes an exact placement-attempt receipt");
                int actualAttempt = Integer.parseInt(
                    outcome.substring(prefix.length(), attemptEnd));
                require(statistics.routeEligiblePlacementAttempts.contains(Integer.valueOf(actualAttempt)) &&
                    !attemptedP07.contains(Integer.valueOf(actualAttempt)),
                    "actual P07 router calls use unique eligible placements");
                if (p07Successes == 0)
                    require(routeIndex < statistics.routeEligiblePlacementAttempts.size() &&
                        actualAttempt == statistics.routeEligiblePlacementAttempts.get(routeIndex).intValue(),
                        "P07 calls before the first success follow eligible priority");
                attemptedP07.add(Integer.valueOf(actualAttempt));
                if ("SUCCESS".equals(outcome.substring(attemptEnd + 1))) p07Successes++;
            }
        }
        require(p05Outcomes == statistics.oneFaceAttempts && p05Outcomes == 0 &&
            p07Outcomes == statistics.twoLayerAttempts &&
            p07Successes == statistics.twoLayerSuccesses &&
            statistics.routeOutcomes.size() == statistics.routeAttempts &&
            statistics.routeOutcomes.size() <= MediumBoardPhysicalPolicy.ROUTING_CANDIDATES &&
            statistics.routingOrders >= 0 && statistics.routingExpansions >= 0 &&
            statistics.selectedPlacementAttempt >= -1 &&
            (statistics.selectedPlacementAttempt < 0 ||
                statistics.selectedPlacementAttempt < statistics.placementCandidates),
            "route outcomes and selected-attempt metadata agree with actual P05/P07 policy counts");
        PcbPackingProbeReceipt probe = statistics.packingProbe;
        int probeOrders = probe == null ? 0 : probe.orderings;
        int probeExpansions = probe == null ? 0 : probe.expansions;
        if (probe != null)
            require(MediumBoardPhysicalPolicy.P07_FULLER_TWO_LAYER.equals(probe.routePolicy) &&
                probe.attempts <= 1 && probe.orderings <= PcbLayerRoutingPrototype.ORDERINGS &&
                probe.expansions <= PcbLayerRoutingPrototype.MAX_EXPANSIONS,
                "optional connected-pocket probe is separately bounded on the actual P07 policy");
        require(statistics.routingOrders >= probeOrders &&
            statistics.routingExpansions >= probeExpansions &&
            statistics.routingExpansions <= MAX_ROUTE_EXPANSIONS &&
            statistics.routingOrders - probeOrders <=
                MediumBoardPhysicalPolicy.ROUTING_CANDIDATES * PcbLayerRoutingPrototype.ORDERINGS &&
            statistics.routingExpansions - probeExpansions <=
                MediumBoardPhysicalPolicy.ROUTING_CANDIDATES *
                    PcbLayerRoutingPrototype.MAX_EXPANSIONS,
            "main P07 routing work remains within three requests after subtracting the separate probe");
    }

    private static void verifyP07GridEligibility(MediumBoardPhysicalPolicy.Statistics statistics) {
        Vector<Integer> eligible = statistics.routeEligiblePlacementAttempts;
        Vector<String> skips = statistics.routeEligibilitySkips;
        Vector<Integer> ranked = statistics.rankedPlacementAttempts;
        Vector<Integer> seen = new Vector<Integer>();
        require(eligible.size() + skips.size() <= ranked.size() &&
            eligible.size() <= statistics.placementCandidates &&
            !eligible.isEmpty(),
            "P07 receipt separates nonempty eligible candidates from pre-route grid skips");
        for (Integer attempt : eligible) {
            require(attempt != null && ranked.contains(attempt) && !seen.contains(attempt),
                "each post-priority eligible attempt is a unique member of the ranked placement receipt");
            seen.add(attempt);
        }
        for (String skip : skips) {
            String[] fields = skip.split(";", -1);
            require(fields.length == 5 && fields[0].startsWith("attempt=") &&
                fields[1].startsWith("width=") && fields[2].startsWith("height=") &&
                fields[3].startsWith("cells=") && "reason=GRID_LIMIT".equals(fields[4]),
                "P07 grid skip uses the frozen exact field order and GRID_LIMIT reason");
            int attempt = Integer.parseInt(fields[0].substring("attempt=".length()));
            int width = Integer.parseInt(fields[1].substring("width=".length()));
            int height = Integer.parseInt(fields[2].substring("height=".length()));
            long cells = Long.parseLong(fields[3].substring("cells=".length()));
            require(attempt >= 0 && attempt < statistics.placementCandidates &&
                ranked.contains(Integer.valueOf(attempt)) &&
                !seen.contains(Integer.valueOf(attempt)) &&
                cells == (long) width * height &&
                !(width >= 1 && height >= 1 && cells <= PcbLayerRoutingPrototype.MAX_CELLS),
                "P07 grid skip is a unique ranked attempt whose reported grid fails the production admission bounds");
            seen.add(Integer.valueOf(attempt));
        }
        String canonical = statistics.toCanonical();
        require(canonical.contains(";routeEligiblePlacementAttempts=" + eligible) &&
            canonical.contains(";routeEligibilitySkips=" + skips),
            "canonical policy receipt preserves both copied P07 eligibility vectors");
    }

    private static BranchDiagnosticCase runAuthoredBranchDiagnostic(
            final Rb100Plan.Variant variant, String runId) {
        final long operationStarted = System.nanoTime();
        final Budget budget = new Budget(operationStarted);
        final BranchDiagnosticCase result = new BranchDiagnosticCase(variant.name(), SEED, runId);
        final NativeContext[] context = new NativeContext[1];
        final Rb100Plan[] plan = new Rb100Plan[1];
        final Rb100ElectricalPilot.Candidate[] candidate =
            new Rb100ElectricalPilot.Candidate[1];
        final BoardIdentity[] identity = new BoardIdentity[1];
        final PcbPlacementPlanner[] planner = new PcbPlacementPlanner[1];
        final PcbPlacementPlanner.Plan[] placement = new PcbPlacementPlanner.Plan[1];
        final PcbBoardLayout[] inputLayout = new PcbBoardLayout[1];
        final PcbLayerRoutingPrototype.Session[] session =
            new PcbLayerRoutingPrototype.Session[1];
        final PcbLayerRoutingPrototype.Result[] routeResult =
            new PcbLayerRoutingPrototype.Result[1];
        try {
            result.phase = "CURRENT_SOURCE_IDENTITY";
            unit(budget, "p07-branch-source-identity", new UnitAction() {
                public void run() {
                    require(SeededPcbLayoutGenerator.CURRENT_VERSION == 25 &&
                        "tsj-generation-dependencies-v26".equals(
                            GenerationDependencyContext.INTERPRETATION_EPOCH) &&
                        Rb100Plan.PLAN_VERSION == 1 && Rb100Plan.MODEL_VERSION == 4 &&
                        Rb100Placement.INTENT_VERSION == 9,
                        "fixed branch observation identifies layout25/dependency-v26/plan1/model4/intent9");
                    require(PcbLayerRoutingPrototype.MAX_EXPANSIONS == 1000000 &&
                        PcbLayerRoutingPrototype.MAX_BRANCH_EXPANSIONS == 100000 &&
                        PcbLayerRoutingPrototype.MAX_CELLS == 250000 &&
                        PcbLayerRoutingPrototype.ORDERINGS == 3 &&
                        PcbLayerRoutingPrototype.Policy.FULLER_TWO_LAYER.vias == 48,
                        "fixed branch observation keeps the existing P07 expansion, grid, ordering and via limits");
                }
            });
            result.phase = "NATIVE_CONTEXT";
            unit(budget, "p07-branch-native-context", new UnitAction() {
                public void run() {
                    context[0] = new NativeContext();
                    context[0].activate();
                }
            });
            result.phase = "CANDIDATE_CONSTRUCTION";
            unit(budget, "p07-branch-authored-candidate-construction", new UnitAction() {
                public void run() {
                    plan[0] = Rb100Plan.create(SEED, variant);
                    candidate[0] = Rb100ElectricalPilot.construct(plan[0]);
                }
            });
            identity[0] = valueUnit(budget, "p07-branch-authored-board-identity",
                new UnitValue<BoardIdentity>() {
                    public BoardIdentity run() {
                        result.components = candidate[0].board().getComponentIds().size();
                        result.pads = candidate[0].board().getPadIds().size();
                        result.nets = candidate[0].board().getNetIds().size();
                        require(result.components == EXPECTED_COMPONENTS &&
                            result.pads == 246 && (result.nets == 59 || result.nets == 60),
                            "branch observation uses the exact authored 100-component, 246-pad, 59-or-60-net candidate");
                        return BoardIdentity.capture(candidate[0]);
                    }
                });

            result.phase = "PROCEDURAL_DECLARATION";
            unit(budget, "p07-branch-procedural-declaration", new UnitAction() {
                public void run() { ProceduralPcbLayout.declare(candidate[0].board()); }
            });
            unit(budget, "p07-branch-post-declaration-input-identity", new UnitAction() {
                public void run() {
                    identity[0].requireUnchanged(candidate[0]);
                    result.inputMetadata = RoutingInputMetadata.capture(
                        candidate[0].board().getPlacementConstraints());
                    result.inputMetadata.requireCurrentP07Treatment();
                    require(MediumBoardPhysicalPolicy.selected(
                        candidate[0].board().getPlacementConstraints()),
                        "actual procedural board retains its current medium physical-policy interpretation");
                }
            });
            final long[] namedSeeds = valueUnit(budget, "p07-branch-named-seeds",
                new UnitValue<long[]>() {
                    public long[] run() {
                        return new long[] {
                            ProceduralPcbLayout.seed(Rb100Plan.FAMILY_ID, SEED,
                                NamedRandomStreams.Concern.PLACEMENT),
                            ProceduralPcbLayout.seed(Rb100Plan.FAMILY_ID, SEED,
                                NamedRandomStreams.Concern.ROUTING)
                        };
                    }
                });
            result.placementSeed = namedSeeds[0];
            result.routingSeed = namedSeeds[1];
            unit(budget, "p07-branch-assert-named-seeds", new UnitAction() {
                public void run() {
                    require(result.placementSeed == PLACEMENT_DIAGNOSTIC_SEED &&
                        result.routingSeed == ROUTING_DIAGNOSTIC_ROUTING_SEED,
                        "fixed branch observation preserves the exact root and named concern streams");
                }
            });

            result.phase = "FOOTPRINT_REGISTRY";
            unit(budget, "p07-branch-footprint-registry", new UnitAction() {
                public void run() {
                    planner[0] = new PcbPlacementPlanner(
                        StandardPcbFootprintProviders.createRegistry());
                }
            });
            result.phase = "FIXED_PLACEMENT_ATTEMPT_4";
            valueUnit(budget, "p07-branch-fixed-placement-attempt-4",
                new UnitValue<Object>() {
                    public Object run() {
                        placement[0] = planner[0].plan(candidate[0].board(),
                            candidate[0].board().getPlacementConstraints(),
                            result.placementSeed, 4);
                        return null;
                    }
                });
            result.phase = "FIXED_PLACEMENT_MATERIALIZATION";
            unit(budget, "p07-branch-fixed-placement-materialization", new UnitAction() {
                public void run() {
                    inputLayout[0] = placement[0].materialize();
                    result.placementFingerprint = inputLayout[0].geometryFingerprint();
                    require(result.placementFingerprint != null &&
                        result.placementFingerprint.length() > 0 &&
                        result.placementFingerprint.length() <= 1024 * 1024,
                        "fixed4 placement has a complete input fingerprint within the 1MiB receipt bound");
                    require(inputLayout[0].getTraces().isEmpty() &&
                        inputLayout[0].getHoles().isEmpty(),
                        "fixed4 materialization is the undrilled and unrouted actual placement");
                    require(PcbLayerRoutingPrototype.gridAdmission(
                            inputLayout[0].getBoardOutline()).eligible,
                        "fixed4 branch observation requires the actual preflight-eligible placement");
                    result.maximumBranchEvents = 3L * (result.pads - result.nets);
                    require(result.maximumBranchEvents > 0,
                        "authored board provides a positive three-pass branch event bound");
                }
            });
            unit(budget, "p07-branch-pre-session-board-identity", new UnitAction() {
                public void run() { identity[0].requireUnchanged(candidate[0]); }
            });

            final SeededPcbLayoutGenerator.AttemptObserver observer =
                new SeededPcbLayoutGenerator.AttemptObserver() {
                    public void check(int pass) {
                        result.observerChecks++;
                        if (pass < 0 || pass >= PcbLayerRoutingPrototype.ORDERINGS)
                            throw new AssertionError("P07 observer reported a pass outside the three-ordering bound");
                        budget.checkpoint();
                    }
                };
            final PcbLayerRoutingPrototype.Diagnostics diagnostics =
                new PcbLayerRoutingPrototype.Diagnostics() {
                    public void onTerminalBranch(PcbLayerRoutingPrototype.BranchSnapshot snapshot) {
                        budget.checkpoint();
                        if (snapshot == null)
                            throw new AssertionError("P07 emitted a null terminal branch snapshot");
                        if (result.branches.size() >= result.maximumBranchEvents)
                            throw new AssertionError("P07 branch snapshot stream exceeded its finite pad/net bound");
                        result.branches.add(snapshot);
                    }
                };
            result.phase = "FIXED_P07_SESSION_BEGIN";
            unit(budget, "p07-branch-session-begin", new UnitAction() {
                public void run() {
                    session[0] = PcbLayerRoutingPrototype.begin(candidate[0].board(),
                        inputLayout[0], PcbLayerRoutingPrototype.Policy.FULLER_TWO_LAYER,
                        observer, PcbLayerRoutingPrototype.MAX_EXPANSIONS,
                        PcbLayerRoutingPrototype.Policy.FULLER_TWO_LAYER.vias, diagnostics);
                }
            });

            result.phase = "FIXED_P07_SESSION_ADVANCE";
            boolean terminal = false;
            while (!terminal) {
                final int call = ++result.advanceCalls;
                terminal = valueUnit(budget, "p07-branch-session-advance-" + call,
                    new UnitValue<Boolean>() {
                        public Boolean run() {
                            // Keep one counted diagnostic unit on the production router quantum.
                            return Boolean.valueOf(session[0].advanceSlice());
                        }
                    }).booleanValue();
            }
            result.phase = "TERMINAL_P07_RESULT_CAPTURE";
            valueUnit(budget, "p07-branch-terminal-result-capture", new UnitValue<Object>() {
                public Object run() {
                    routeResult[0] = session[0].result();
                    result.routeResult = routeResult[0];
                    require(result.advanceCalls > 0 && result.observerChecks > 0,
                        "fixed P07 session advanced under actual per-unit budget checkpoints");
                    require(routeResult[0] != null && routeResult[0].outcome != null &&
                        routeResult[0].expansions >= 0 && routeResult[0].expansions <=
                            PcbLayerRoutingPrototype.MAX_EXPANSIONS &&
                        routeResult[0].orderings >= 0 && routeResult[0].orderings <=
                            PcbLayerRoutingPrototype.ORDERINGS && routeResult[0].vias >= 0 &&
                        routeResult[0].vias <= PcbLayerRoutingPrototype.Policy.FULLER_TWO_LAYER.vias &&
                        routeResult[0].links >= 0,
                        "actual fixed P07 result reports bounded outcome and counters");
                    require(routeResult[0].accepted() == (routeResult[0].layout != null),
                        "P07 accepted outcome agrees with presence of its actual layout");
                    require(routeResult[0].accepted() == "SUCCESS".equals(routeResult[0].outcome),
                        "P07 success outcome agrees with acceptance; rejected outcomes remain observations");
                    verifyBranchHistory(candidate[0].board(), inputLayout[0],
                        result.branches, result.maximumBranchEvents, routeResult[0]);
                    identity[0].requireUnchanged(candidate[0]);
                    require(result.placementFingerprint.equals(inputLayout[0].geometryFingerprint()) &&
                        inputLayout[0].getTraces().isEmpty() && inputLayout[0].getHoles().isEmpty(),
                        "P07 session preserves the exact fixed placement input and publishes no copper into it");
                    require(CircuitElm.sim == context[0].sim && CirSim.theSim == context[0].sim &&
                        context[0].sim.elmList == context[0].rawGraph && context[0].rawGraph.isEmpty(),
                        "P07 branch observation leaves the candidate uninstalled in the empty active solver graph");
                    result.boardIdentityStatus = "PASS";
                    result.solverGraphIdentityStatus = "PASS";
                    result.captureLayoutOutput();
                    return null;
                }
            });
        } catch (RuntimeException failure) {
            result.failurePhase = result.phase;
            result.failure = failure;
        } catch (Error failure) {
            result.failurePhase = result.phase;
            result.failure = failure;
        }

        result.phase = "FINAL_REPORT_PREPARATION";
        try {
            unit(budget, "p07-branch-final-report-preparation", new UnitAction() {
                public void run() { result.prepareFacts(); }
            });
        } catch (RuntimeException failure) {
            if (result.failurePhase == null) result.failurePhase = result.phase;
            result.failure = append(result.failure, failure);
        } catch (Error failure) {
            if (result.failurePhase == null) result.failurePhase = result.phase;
            result.failure = append(result.failure, failure);
        }
        result.operationMillis = nanosToMillis(System.nanoTime() - operationStarted);
        cleanupDiagnosticCase(result, context[0], candidate[0]);
        result.workUnits = budget.workUnits;
        result.maxUnitMillis = budget.maxUnitMillis();
        result.status = result.failure == null && result.cleanupPassed &&
            result.routeResult != null && result.inputMetadata != null &&
            "PASS".equals(result.boardIdentityStatus) &&
            "PASS".equals(result.solverGraphIdentityStatus) &&
            result.reportFactsJson != null && result.operationMillis <=
                nanosToMillis(CASE_LIMIT_NANOS) && result.workUnits <= WORK_LIMIT &&
            result.maxUnitMillis <= nanosToMillis(UNIT_LIMIT_NANOS) ? "PASS" : "FAIL";
        return result;
    }

    private static void verifyBranchHistory(TroubleshootBoard board, PcbBoardLayout placement,
            Vector<PcbLayerRoutingPrototype.BranchSnapshot> branches, long maximumEvents,
            PcbLayerRoutingPrototype.Result routeResult) {
        require(!branches.isEmpty() && branches.size() <= maximumEvents,
            "complete terminal branch census stays within three times (pads minus nets)");
        HashSet<String> uniqueBranches = new HashSet<String>();
        long totalExpanded = 0;
        int priorPass = -1;
        int priorPrefixPass = -1;
        int priorPrefixViaCount = -1;
        int expectedGridWidth = (placement.getBoardOutline().width - 2 * 10) / 10 + 1;
        int expectedGridHeight = (placement.getBoardOutline().height - 2 * 10) / 10 + 1;
        require(PcbLayerRoutingPrototype.GRID == 10 && expectedGridWidth > 0 &&
            expectedGridHeight > 0 && (long)expectedGridWidth * expectedGridHeight <=
                PcbLayerRoutingPrototype.MAX_CELLS,
            "branch grid census matches the frozen ten-unit P07 grid and existing cell ceiling");
        for (PcbLayerRoutingPrototype.BranchSnapshot branch : branches) {
            require(branch.pass >= 0 && branch.pass < PcbLayerRoutingPrototype.ORDERINGS &&
                branch.pass >= priorPass,
                "branch snapshots preserve actual pass order within the three-pass cap");
            priorPass = branch.pass;
            BoardNet net = board.getNet(branch.netId);
            BoardPad startPad = board.getPad(branch.startPadId);
            BoardPad rootPad = board.getPad(branch.rootPadId);
            PcbPadPlacement start = placement.getPad(branch.startPadId);
            PcbPadPlacement root = placement.getPad(branch.rootPadId);
            require(net != null && startPad != null && rootPad != null && start != null && root != null &&
                branch.netId.equals(startPad.getNetId()) && branch.netId.equals(rootPad.getNetId()) &&
                branch.startX == start.getX() && branch.startY == start.getY() &&
                branch.rootX == root.getX() && branch.rootY == root.getY(),
                "branch identity and endpoint coordinates name actual same-net board pads");
            int netPadCount = net.getPadIds().size();
            require(branch.prefixViaCount >= 0 && branch.prefixViaCount <=
                    PcbLayerRoutingPrototype.Policy.FULLER_TWO_LAYER.vias &&
                branch.reachedPadCount >= 1 && branch.reachedPadCount <= netPadCount,
                "prefix-via and reached-terminal snapshots stay within actual route limits and net membership");
            if (branch.pass != priorPrefixPass) {
                priorPrefixPass = branch.pass;
                priorPrefixViaCount = branch.prefixViaCount;
            } else {
                require(branch.prefixViaCount >= priorPrefixViaCount,
                    "actual published via prefix never decreases within an ordering pass");
                priorPrefixViaCount = branch.prefixViaCount;
            }
            require(uniqueBranches.add(branch.pass + "|" + branch.netId.length() + ":" +
                branch.netId + "|" + branch.startPadId.length() + ":" + branch.startPadId +
                "|" + branch.rootPadId.length() + ":" + branch.rootPadId),
                "each actual pass/net/start/root branch emits once");
            long cells = (long) branch.gridWidth * branch.gridHeight;
            require(branch.gridWidth == expectedGridWidth && branch.gridHeight == expectedGridHeight &&
                branch.goalsTop >= branch.reachedPadCount &&
                branch.goalsBottom >= branch.reachedPadCount &&
                branch.goalsTop <= cells && branch.goalsBottom <= cells,
                "fuller-policy goals include every reached PTH face within the fixed grid");
            require(branch.initialManhattanDistance >= 0 &&
                branch.initialManhattanDistance % 10 == 0 &&
                branch.minimumExploredManhattanDistance >= -1 &&
                (branch.minimumExploredManhattanDistance < 0 ||
                    branch.minimumExploredManhattanDistance % 10 == 0) &&
                (branch.branchExpanded == 0 ? branch.minimumExploredManhattanDistance == -1 :
                    branch.minimumExploredManhattanDistance >= 0 &&
                        branch.minimumExploredManhattanDistance <= branch.initialManhattanDistance),
                "Manhattan distance fields retain ten-unit route-grid semantics and zero-work sentinel");
            require(branch.branchExpanded >= 0 &&
                branch.branchExpanded <= PcbLayerRoutingPrototype.MAX_BRANCH_EXPANSIONS &&
                branch.expandedTop >= 0 && branch.expandedBottom >= 0 &&
                branch.expandedTop + branch.expandedBottom == branch.branchExpanded &&
                branch.totalExpanded >= totalExpanded &&
                branch.totalExpanded == totalExpanded + branch.branchExpanded &&
                branch.totalExpanded <= PcbLayerRoutingPrototype.MAX_EXPANSIONS &&
                branch.frontierCount >= 0 && branch.bestStateCount >= 0,
                "per-face branch work and cumulative expansion census conserve actual non-stale search states");
            String terminalReason = String.valueOf(branch.terminalReason);
            require("FOUND".equals(terminalReason) || "NO_PATH".equals(terminalReason) ||
                "TOTAL_SEARCH_LIMIT".equals(terminalReason) ||
                "BRANCH_SEARCH_LIMIT".equals(terminalReason),
                "branch snapshot records a real terminal search reason");
            totalExpanded += branch.branchExpanded;
        }
        require(totalExpanded == routeResult.expansions &&
            branches.lastElement().totalExpanded == routeResult.expansions,
            "complete terminal branch events account for the final P07 result expansion total");
        if (routeResult.accepted() || "QUALITY_LIMIT".equals(routeResult.outcome)) {
            int completedBranches = 0;
            for (PcbLayerRoutingPrototype.BranchSnapshot branch : branches)
                if (branch.pass == routeResult.orderings - 1) {
                    require("FOUND".equals(branch.terminalReason),
                        "a completed ordering contains only found search branches");
                    completedBranches++;
                }
            require(completedBranches == board.getPadIds().size() - board.getNetIds().size(),
                "a completed authored ordering covers every non-root net terminal");
        }
    }

    private static String routingInputMetadataFields(RoutingInputMetadata metadata) {
        if (metadata == null)
            return ",\"physicalPolicyId\":null,\"physicalPolicyVersion\":null," +
                "\"placementDensityVersion\":null,\"routingLayer\":null," +
                "\"traceWidthProfile\":null,\"placementPackingVersion\":null," +
                "\"requiresTwoLayerRouting\":null";
        return ",\"physicalPolicyId\":\"" + jsonFull(metadata.physicalPolicyId) +
            "\",\"physicalPolicyVersion\":" + metadata.physicalPolicyVersion +
            ",\"placementDensityVersion\":" + metadata.placementDensityVersion +
            ",\"routingLayer\":\"" + jsonFull(metadata.routingLayer) +
            "\",\"traceWidthProfile\":\"" + jsonFull(metadata.traceWidthProfile) +
            "\",\"placementPackingVersion\":" + metadata.placementPackingVersion +
            ",\"requiresTwoLayerRouting\":" + metadata.requiresTwoLayerRouting;
    }

    static void runCorridorOrientationContracts() {
        long started = System.nanoTime();
        int beforeAssertions = assertions;
        require(SeededPcbLayoutGenerator.CURRENT_VERSION == 25 &&
                "tsj-generation-dependencies-v26".equals(
                    GenerationDependencyContext.INTERPRETATION_EPOCH),
            "orientation contract identifies layout25/dependency-v26 interpretation");
        require(Rb100Plan.PLAN_VERSION == 1 && Rb100Plan.MODEL_VERSION == 4 &&
                Rb100Placement.INTENT_VERSION == 9,
            "orientation contract identifies current plan1/model4/physical-intent9");

        PcbPlacementConstraints.Barrier barrier =
            new PcbPlacementConstraints.Barrier("PRIMARY", "SECONDARY", 80);
        PcbPlacementConstraints constraints = corridorFixtureConstraints(barrier);
        PcbFootprint uac = corridorFixtureFootprint("UAC",
            PhysicalPackages.ISOLATED_CONVERTER_7, PcbRotation.DEG_0);
        PcbFootprint ufb = corridorFixtureFootprint("UFB",
            PhysicalPackages.OPTOCOUPLER_4, PcbRotation.DEG_180);
        Vector<PcbFootprint> authoredSelected = new Vector<PcbFootprint>();
        authoredSelected.add(uac);
        authoredSelected.add(ufb);

        TreeMap<String, Rectangle> uacCourtyards =
            constraints.domainCourtyards(uac.getPlacement());
        require(sameRectangle(uacCourtyards.get("PRIMARY"),
                new Rectangle(10, 10, 105, 310)) &&
            sameRectangle(uacCourtyards.get("SECONDARY"),
                new Rectangle(305, 10, 105, 310)),
            "actual selected UAC geometry retains the literal primary-left courtyard pair");
        int uacMask = PcbPlacementPlanner.separatingOrientationMask(
            new Rectangle(10, 10, 105, 310), new Rectangle(305, 10, 105, 310), 80);
        require(uacMask == PcbPlacementPlanner.CORRIDOR_FIRST_LEFT_SECOND,
            "literal UAC courtyards independently require primary-left-secondary");

        TreeMap<String, Rectangle> ufbCourtyards =
            constraints.domainCourtyards(ufb.getPlacement());
        require(sameRectangle(ufbCourtyards.get("PRIMARY"),
                new Rectangle(10, 10, 105, 180)) &&
            sameRectangle(ufbCourtyards.get("SECONDARY"),
                new Rectangle(225, 10, 105, 180)),
            "actual rotated UFB geometry maps its primary courtyard left of secondary");
        int ufbMask = PcbPlacementPlanner.separatingOrientationMask(
            ufbCourtyards.get("PRIMARY"), ufbCourtyards.get("SECONDARY"), 80);
        require(ufbMask == PcbPlacementPlanner.CORRIDOR_FIRST_LEFT_SECOND,
            "rotated UFB is compatible with the authored primary-left corridor");
        int authoredMask = PcbPlacementPlanner.supportedIsolationOrientationMask(
            constraints, barrier, authoredSelected);
        require(authoredMask == PcbPlacementPlanner.CORRIDOR_FIRST_LEFT_SECOND,
            "one shared requirement intersects all selected UAC/UFB isolation bodies");

        Rectangle prefixPrimary = new Rectangle(94, 2784, 594, 142);
        Rectangle prefixSecondary = new Rectangle(55, 75, 12331, 2591);
        int prefixMask = PcbPlacementPlanner.separatingOrientationMask(
            prefixPrimary, prefixSecondary, 80);
        require(prefixMask == PcbPlacementPlanner.CORRIDOR_SECOND_ABOVE_FIRST,
            "recorded prefix supports only secondary-above-primary, meaning primary-below-secondary");
        require((prefixMask & authoredMask) == 0,
            "production orientation mask refuses the recorded prefix despite its vertical gap");

        int exactClearance = PcbPlacementPlanner.separatingOrientationMask(
            new Rectangle(0, 0, 100, 100), new Rectangle(180, 0, 100, 100), 80);
        int oneShort = PcbPlacementPlanner.separatingOrientationMask(
            new Rectangle(0, 0, 100, 100), new Rectangle(179, 0, 100, 100), 80);
        require(exactClearance == PcbPlacementPlanner.CORRIDOR_FIRST_LEFT_SECOND,
            "X-lane separation passes at exactly the declared 80-unit clearance");
        require(oneShort == 0,
            "X-lane separation rejects at a 79-unit gap without another separating axis");

        PhysicalPackage rotatableConverter = rotatableIsolationFixturePackage(
            PhysicalPackages.ISOLATED_CONVERTER_7);
        PcbFootprint horizontal = corridorFixtureFootprint("MIXED_H",
            rotatableConverter, PcbRotation.DEG_0);
        PcbFootprint vertical = corridorFixtureFootprint("MIXED_V",
            rotatableConverter, PcbRotation.DEG_90);
        PcbPlacementConstraints incompatibleConstraints =
            corridorFixtureConstraints(barrier, "MIXED_H", "MIXED_V");
        TreeMap<String, Rectangle> horizontalCourtyards =
            incompatibleConstraints.domainCourtyards(horizontal.getPlacement());
        TreeMap<String, Rectangle> verticalCourtyards =
            incompatibleConstraints.domainCourtyards(vertical.getPlacement());
        int horizontalMask = PcbPlacementPlanner.separatingOrientationMask(
            horizontalCourtyards.get("PRIMARY"), horizontalCourtyards.get("SECONDARY"), 80);
        int verticalMask = PcbPlacementPlanner.separatingOrientationMask(
            verticalCourtyards.get("PRIMARY"), verticalCourtyards.get("SECONDARY"), 80);
        require(horizontalMask == PcbPlacementPlanner.CORRIDOR_FIRST_LEFT_SECOND &&
            verticalMask == PcbPlacementPlanner.CORRIDOR_FIRST_ABOVE_SECOND,
            "each individually legal mixed-body pose contributes its own fixed orientation");
        Vector<PcbFootprint> incompatibleSelected = new Vector<PcbFootprint>();
        incompatibleSelected.add(horizontal);
        incompatibleSelected.add(vertical);
        int emptyMask = PcbPlacementPlanner.supportedIsolationOrientationMask(
            incompatibleConstraints, barrier, incompatibleSelected);
        require(emptyMask == 0,
            "incompatible individually legal selected mixed poses have an empty common mask");
        boolean typedRejection = false;
        try {
            PcbPlacementPlanner.requireNonEmptyIsolationOrientationMask(emptyMask);
        } catch (PcbPlacementPlanner.Rejected rejected) {
            typedRejection = "MIXED_CORRIDOR_ORIENTATION".equals(rejected.reason);
        }
        require(typedRejection,
            "empty selected-body intersection maps to the typed mixed-corridor rejection");
        require(PcbPlacementPlanner.requireNonEmptyIsolationOrientationMask(authoredMask) ==
                authoredMask,
            "nonempty selected-body orientation masks pass unchanged");

        BoardComponent ordinaryComponent = new BoardComponent("ORDINARY", "LED",
            PhysicalPackages.THROUGH_HOLE_LED);
        for (String terminal : PhysicalPackages.THROUGH_HOLE_LED.getTerminalIds())
            ordinaryComponent.addPadId("ORDINARY." + terminal);
        PcbFootprint ordinary = PcbFootprint.fromPhysicalPackage(ordinaryComponent, 0, 0);
        Vector<PcbFootprint> ordinarySelected = new Vector<PcbFootprint>();
        ordinarySelected.add(ordinary);
        Vector<PcbPlacementConstraints.Part> ordinaryParts =
            new Vector<PcbPlacementConstraints.Part>();
        ordinaryParts.add(new PcbPlacementConstraints.Part("ORDINARY", "circuit", "Circuit",
            "PRIMARY", PcbPlacementConstraints.Anchor.NONE, 20));
        PcbPlacementConstraints ordinaryConstraints = new PcbPlacementConstraints(
            ordinaryParts, singletonBarrier(barrier));
        require(PcbPlacementPlanner.supportedIsolationOrientationMask(
                ordinaryConstraints, barrier, ordinarySelected) ==
                PcbPlacementPlanner.CORRIDOR_ALL_ORIENTATIONS,
            "ordinary nonmixed packages impose no new corridor orientation");

        Vector<PcbFootprint> noDomains = new Vector<PcbFootprint>();
        require(PcbPlacementPlanner.currentSeparatingOrientations(constraints, barrier,
                null, noDomains, -1, false) ==
                PcbPlacementPlanner.CORRIDOR_ALL_ORIENTATIONS,
            "incomplete current aggregates preserve the existing permissive missing-domain rule");
        require(PcbPlacementPlanner.currentSeparatingOrientations(constraints, barrier,
                null, noDomains, -1, true) == 0,
            "complete current aggregates reject when either barrier domain is missing");
        Vector<PcbFootprint> oneDomain = new Vector<PcbFootprint>();
        oneDomain.add(ordinary);
        require(PcbPlacementPlanner.currentSeparatingOrientations(ordinaryConstraints,
                barrier, null, oneDomain, -1, false) ==
                PcbPlacementPlanner.CORRIDOR_ALL_ORIENTATIONS &&
            PcbPlacementPlanner.currentSeparatingOrientations(ordinaryConstraints,
                barrier, null, oneDomain, -1, true) == 0,
            "single-domain prefix keeps incomplete/complete missing-domain semantics distinct");

        System.out.println("PASS: PCB corridor orientation contracts {\"status\":\"PASS\",\"cases\":5," +
            "\"layoutVersion\":" + SeededPcbLayoutGenerator.CURRENT_VERSION +
            ",\"dependencyEpoch\":\"" + GenerationDependencyContext.INTERPRETATION_EPOCH +
            "\",\"planVersion\":" + Rb100Plan.PLAN_VERSION +
            ",\"modelVersion\":" + Rb100Plan.MODEL_VERSION +
            ",\"physicalIntentVersion\":" + Rb100Placement.INTENT_VERSION +
            ",\"assertions\":" + (assertions - beforeAssertions) +
            ",\"elapsedMs\":" + nanosToMillis(System.nanoTime() - started) +
            ",\"scope\":\"PLANNER_ORIENTATION_GEOMETRY_ONLY\"}");
    }

    private static PcbPlacementConstraints corridorFixtureConstraints(
            PcbPlacementConstraints.Barrier barrier, String... componentIds) {
        Vector<PcbPlacementConstraints.Part> parts = new Vector<PcbPlacementConstraints.Part>();
        if (componentIds == null || componentIds.length == 0)
            componentIds = new String[] { "UAC", "UFB" };
        for (String componentId : componentIds) {
            String[] primary, secondary;
            if (componentId.equals("UFB")) {
                primary = new String[] { "C", "E" };
                secondary = new String[] { "A", "K" };
            } else if (componentId.equals("UAC") || componentId.startsWith("MIXED_")) {
                primary = new String[] { "IN+", "IN-", "EN", "FB", "BIAS" };
                secondary = new String[] { "PRE_L+", "OUT-" };
            } else {
                parts.add(new PcbPlacementConstraints.Part(componentId, "circuit", "Circuit",
                    "PRIMARY", PcbPlacementConstraints.Anchor.NONE, 20));
                continue;
            }
            Vector<PcbPlacementConstraints.TerminalDomain> declarations =
                new Vector<PcbPlacementConstraints.TerminalDomain>();
            for (String terminal : primary)
                declarations.add(new PcbPlacementConstraints.TerminalDomain(terminal, "PRIMARY"));
            for (String terminal : secondary)
                declarations.add(new PcbPlacementConstraints.TerminalDomain(terminal, "SECONDARY"));
            parts.add(new PcbPlacementConstraints.Part(componentId, "circuit", "Circuit",
                "PRIMARY", PcbPlacementConstraints.Anchor.NONE, 20, declarations));
        }
        return new PcbPlacementConstraints(parts, singletonBarrier(barrier));
    }

    private static Vector<PcbPlacementConstraints.Barrier> singletonBarrier(
            PcbPlacementConstraints.Barrier barrier) {
        Vector<PcbPlacementConstraints.Barrier> barriers =
            new Vector<PcbPlacementConstraints.Barrier>();
        barriers.add(barrier);
        return barriers;
    }

    private static PcbFootprint corridorFixtureFootprint(String componentId,
            PhysicalPackage physicalPackage, PcbRotation rotation) {
        BoardComponent component = new BoardComponent(componentId, "CORRIDOR_FIXTURE",
            physicalPackage);
        for (String terminal : physicalPackage.getTerminalIds())
            component.addPadId(componentId + "." + terminal);
        return PcbFootprint.fromPhysicalPackage(component,
            new PcbPackagePose(0, 0, rotation, PcbBoardSide.TOP),
            physicalPackage.getGeometry());
    }

    private static PhysicalPackage rotatableIsolationFixturePackage(PhysicalPackage source) {
        Vector<PhysicalPackage.GeometryVariant> variants =
            new Vector<PhysicalPackage.GeometryVariant>();
        variants.add(new PhysicalPackage.GeometryVariant("DEFAULT", "IDENTITY",
            source.getGeometry()));
        Vector<PcbRotation> rotations = new Vector<PcbRotation>();
        rotations.add(PcbRotation.DEG_0);
        rotations.add(PcbRotation.DEG_90);
        Vector<PcbBoardSide> sides = new Vector<PcbBoardSide>();
        sides.add(PcbBoardSide.TOP);
        return new PhysicalPackage("TEST_ROTATABLE_ISOLATION_FIXTURE",
            source.getTerminalIds(), new Vector<String>(), false, source.getGeometry(),
            variants, "DEFAULT", PhysicalPackage.GeometryVariantSelection.FIXED_DEFAULT,
            rotations, sides);
    }

    private static boolean sameRectangle(Rectangle actual, Rectangle expected) {
        return actual != null && expected != null && actual.x == expected.x &&
            actual.y == expected.y && actual.width == expected.width &&
            actual.height == expected.height;
    }

    private static PlacementDiagnosticCase runStructuralPlacementDiagnostic() {
        final long operationStarted = System.nanoTime();
        final Budget budget = new Budget(operationStarted);
        final PlacementDiagnosticCase result = new PlacementDiagnosticCase(
            "MEDIUM_GENERIC_EDGE_REGION_20_PART", "STRUCTURAL_CONTROL", 0L, 0);
        final NativeContext[] context = new NativeContext[1];
        final TroubleshootBoard[] board = new TroubleshootBoard[1];
        final PcbPlacementPlanner[] planner = new PcbPlacementPlanner[1];
        final PcbPlacementPlanner.Plan[] nullPlan = new PcbPlacementPlanner.Plan[1];
        final PcbPlacementPlanner.Plan[] observedPlan = new PcbPlacementPlanner.Plan[1];
        final PcbPlacementPlanner.Plan[] retryPlan = new PcbPlacementPlanner.Plan[1];
        final DiagnosticCounts[] counts = new DiagnosticCounts[1];
        final DiagnosticCounts[] abortCounts = new DiagnosticCounts[1];
        final DiagnosticCallbackAbort sentinel = new DiagnosticCallbackAbort();
        try {
            result.phase = "NATIVE_CONTEXT";
            unit(budget, "native-context", new UnitAction() {
                public void run() { context[0] = new NativeContext(); context[0].activate(); }
            });
            result.phase = "EXISTING_MEDIUM_STRUCTURAL_FIXTURE";
            board[0] = valueUnit(budget, "existing-medium-20-part-fixture",
                new UnitValue<TroubleshootBoard>() {
                    public TroubleshootBoard run() {
                        return MediumBoardFloorplanningContractTest.genericTwentyPartBoard();
                    }
                });
            unit(budget, "structural-candidate-counts", new UnitAction() {
                public void run() {
                    result.components = board[0].getComponentIds().size();
                    result.pads = board[0].getPadIds().size();
                    result.nets = board[0].getNetIds().size();
                    require(result.components == 20,
                        "positive instrumentation control is the existing generic 20-part medium fixture");
                }
            });
            unit(budget, "structural-footprint-registry", new UnitAction() {
                public void run() {
                    planner[0] = new PcbPlacementPlanner(
                        StandardPcbFootprintProviders.createRegistry());
                }
            });

            result.phase = "STRUCTURAL_NULL_SINK_CONTROL";
            nullPlan[0] = valueUnit(budget, "structural-null-sink-placement",
                new UnitValue<PcbPlacementPlanner.Plan>() {
                    public PcbPlacementPlanner.Plan run() {
                        return planner[0].plan(board[0], board[0].getPlacementConstraints(),
                            0L, 0, null);
                    }
                });
            result.nullOutcome = outcomeName(nullPlan[0], null);
            result.nullGeometry = valueUnit(budget, "structural-null-geometry-fingerprint",
                new UnitValue<String>() {
                    public String run() { return nullPlan[0].materialize().geometryFingerprint(); }
                });

            counts[0] = new DiagnosticCounts(budget, null);
            result.phase = "STRUCTURAL_OBSERVED_SINK_CONTROL";
            observedPlan[0] = valueUnit(budget, "structural-observed-sink-placement",
                new UnitValue<PcbPlacementPlanner.Plan>() {
                    public PcbPlacementPlanner.Plan run() {
                        return planner[0].plan(board[0], board[0].getPlacementConstraints(),
                            0L, 0, counts[0]);
                    }
                });
            result.observedOutcome = outcomeName(observedPlan[0], null);
            result.observedGeometry = valueUnit(budget,
                "structural-observed-geometry-fingerprint", new UnitValue<String>() {
                    public String run() { return observedPlan[0].materialize().geometryFingerprint(); }
                });

            abortCounts[0] = new DiagnosticCounts(budget, sentinel);
            result.phase = "CALLBACK_EXCEPTION_PROPAGATION";
            unit(budget, "sentinel-callback-abort", new UnitAction() {
                public void run() {
                    Throwable propagated = null;
                    try {
                        planner[0].plan(board[0], board[0].getPlacementConstraints(),
                            0L, 0, abortCounts[0]);
                    } catch (RuntimeException failure) {
                        propagated = failure;
                    }
                    result.callbackExceptionPropagated = propagated == sentinel;
                    require(result.callbackExceptionPropagated,
                        "selected medium placement propagates the exact sink callback exception");
                    require(abortCounts[0].fitCallbacks == 1,
                        "sentinel abort occurs at the first local fit callback");
                    require(abortCounts[0].snapshotCalls == 0,
                        "sentinel abort propagates before a placement-failure snapshot");
                }
            });
            result.phase = "NULL_SINK_RETRY_AFTER_CALLBACK_ABORT";
            retryPlan[0] = valueUnit(budget, "null-sink-retry-after-callback-abort",
                new UnitValue<PcbPlacementPlanner.Plan>() {
                    public PcbPlacementPlanner.Plan run() {
                        return planner[0].plan(board[0], board[0].getPlacementConstraints(),
                            0L, 0, null);
                    }
                });
            result.retryOutcome = outcomeName(retryPlan[0], null);
            result.retryGeometry = valueUnit(budget, "retry-geometry-fingerprint",
                new UnitValue<String>() {
                    public String run() { return retryPlan[0].materialize().geometryFingerprint(); }
                });
            result.phase = "CONTROL_PARITY_AND_REPORT";
            unit(budget, "structural-control-parity", new UnitAction() {
                public void run() {
                    require(nullPlan[0] != null && observedPlan[0] != null && retryPlan[0] != null,
                        "existing medium control placement succeeds with null, observed and post-abort null sinks");
                    require("ACCEPTED".equals(result.nullOutcome) &&
                        result.nullOutcome.equals(result.observedOutcome),
                        "positive control has the same accepted typed outcome with and without observation");
                    require(result.nullGeometry.equals(result.observedGeometry) &&
                        result.nullGeometry.equals(result.retryGeometry),
                        "null/observed and post-abort null placements have exact canonical pose parity");
                    require(result.nullGeometry.startsWith("layout=25|"),
                        "generic20 control has the current layout epoch prefix");
                    require("d0ca6b24/41787".equals(key("layout=20|" +
                            result.nullGeometry.substring("layout=25|".length()))),
                        "generic20 geometry retains its recorded key with only the epoch masked");
                    require(counts[0].snapshotCalls == 0 && counts[0].totalOutcomes() > 0,
                        "accepted positive control reports fit outcomes without a failure snapshot");
                    require(counts[0].totalOutcomes() == observedPlan[0].evaluations,
                        "positive control reports one classified outcome per planner evaluation");
                    require(counts[0].get(PcbPlacementPlanner.FitOutcome.ACCEPTED) > 0,
                        "accepted positive control reports at least one accepted fit");
                    require(result.callbackExceptionPropagated,
                        "callback abort propagation remains established before retry");
                }
            });
        } catch (RuntimeException failure) {
            result.failurePhase = result.phase;
            result.failure = failure;
        } catch (Error failure) {
            result.failurePhase = result.phase;
            result.failure = failure;
        }
        preparePlacementDiagnosticReport(result, budget, new UnitAction() {
            public void run() {
                if (counts[0] != null) {
                    result.fitCounts = counts[0].copyCounts();
                    result.firstFitCount = counts[0].totalOutcomes();
                }
                if (abortCounts[0] != null)
                    result.callbackAbortCallbacks = abortCounts[0].fitCallbacks;
                result.prepareFacts();
            }
        });
        result.operationMillis = nanosToMillis(System.nanoTime() - operationStarted);
        cleanupDiagnosticCase(result, context[0], null);
        result.workUnits = budget.workUnits;
        result.maxUnitMillis = budget.maxUnitMillis();
        result.status = result.failure == null && result.cleanupPassed && result.reportFactsJson != null &&
            result.operationMillis <= nanosToMillis(CASE_LIMIT_NANOS) &&
            result.workUnits <= WORK_LIMIT && result.maxUnitMillis <= nanosToMillis(UNIT_LIMIT_NANOS) ?
            "PASS" : "FAIL";
        return result;
    }

    private static PlacementDiagnosticCase runCorridorWitnessPlacementDiagnostic() {
        final long operationStarted = System.nanoTime();
        final Budget budget = new Budget(operationStarted);
        final PlacementDiagnosticCase result = new PlacementDiagnosticCase(
            "MEDIUM_ISOLATION_CORRIDOR_WITNESS_20_PART", "RIGHT_ANCHORED_PRIMARY", 0L, 0);
        final NativeContext[] context = new NativeContext[1];
        final TroubleshootBoard[] board = new TroubleshootBoard[1];
        final PlacementFixtureIdentity[] identity = new PlacementFixtureIdentity[1];
        final PcbPlacementPlanner[] planner = new PcbPlacementPlanner[1];
        final PcbPlacementPlanner.Plan[] nullPlan = new PcbPlacementPlanner.Plan[1];
        final PcbPlacementPlanner.Plan[] observedPlan = new PcbPlacementPlanner.Plan[1];
        final PcbPlacementPlanner.Rejected[] nullRejected =
            new PcbPlacementPlanner.Rejected[1];
        final PcbPlacementPlanner.Rejected[] observedRejected =
            new PcbPlacementPlanner.Rejected[1];
        final DiagnosticCounts[] counts = new DiagnosticCounts[1];
        final DiagnosticCounts[] abortCounts = new DiagnosticCounts[1];
        final DiagnosticCallbackAbort sentinel = new DiagnosticCallbackAbort();
        try {
            result.phase = "NATIVE_CONTEXT";
            unit(budget, "native-context", new UnitAction() {
                public void run() { context[0] = new NativeContext(); context[0].activate(); }
            });
            result.phase = "DETERMINISTIC_CORRIDOR_FIXTURE";
            board[0] = valueUnit(budget, "build-20-part-corridor-fixture",
                new UnitValue<TroubleshootBoard>() {
                    public TroubleshootBoard run() { return corridorWitnessBoard(); }
                });
            identity[0] = valueUnit(budget, "capture-corridor-fixture-identity",
                new UnitValue<PlacementFixtureIdentity>() {
                    public PlacementFixtureIdentity run() {
                        return PlacementFixtureIdentity.capture(board[0]);
                    }
                });
            unit(budget, "corridor-fixture-input-contract", new UnitAction() {
                public void run() {
                    result.components = board[0].getComponentIds().size();
                    result.pads = board[0].getPadIds().size();
                    result.nets = board[0].getNetIds().size();
                    require(result.components == 20 &&
                        board[0].getComponent("UAC") != null &&
                        board[0].getComponent("JRIGHT") != null,
                        "non-authored fixture is exactly the planner's 20-part medium threshold with the intended real packages");
                    PcbPlacementConstraints constraints = board[0].getPlacementConstraints();
                    String[] expectedIsolationTerminals = new String[] {
                        "IN+", "IN-", "PRE_L+", "OUT-", "EN", "FB", "BIAS"
                    };
                    require(PhysicalPackages.ISOLATED_CONVERTER_7.getTerminalIds().equals(
                            Arrays.asList(expectedIsolationTerminals)),
                        "fixture uses the selected converter's exact seven literal terminal IDs");
                    require("MEDIUM_BOARD".equals(constraints.getPhysicalPolicyId()) &&
                        constraints.getPhysicalPolicyVersion() == 1 &&
                        constraints.placementDensityVersion == 14 &&
                        constraints.requiresTwoLayerRouting &&
                        "ROLE_TRACE@1".equals(constraints.getTraceWidthProfile().getIdentity()) &&
                        constraints.placementPackingVersion == 4,
                        "fixture exercises the real current medium planner input identity");
                    PcbPlacementConstraints.Part right = constraints.get("JRIGHT");
                    PcbPlacementConstraints.Part isolation = constraints.get("UAC");
                    require(right != null && right.anchor == PcbPlacementConstraints.Anchor.RIGHT &&
                        isolation != null && isolation.anchor == PcbPlacementConstraints.Anchor.NONE &&
                        isolation.getTerminalDomains().size() == 7 &&
                        "SECONDARY".equals(constraints.get("R02").domainId) &&
                        "SECONDARY".equals(constraints.get("R03").domainId) &&
                        "SECONDARY".equals(constraints.domainForPad(board[0], "R02.1")) &&
                        "SECONDARY".equals(constraints.domainForPad(board[0], "UAC.OUT-")) &&
                        "SECONDARY".equals(constraints.domainForPad(board[0], "R03.1")) &&
                        constraints.getBarriers().size() == 1 &&
                        constraints.getBarriers().get(0).separates("PRIMARY", "SECONDARY") &&
                        constraints.getBarriers().get(0).clearance == 80,
                        "actual selected isolation package and fixed right connector declare the single 80-unit primary/secondary barrier");
                    String[] expectedDomains = new String[] {
                        "PRIMARY", "PRIMARY", "SECONDARY", "SECONDARY",
                        "PRIMARY", "PRIMARY", "PRIMARY"
                    };
                    for (int index = 0; index < expectedIsolationTerminals.length; index++) {
                        String terminal = expectedIsolationTerminals[index];
                        String resistor = corridorResistorId(index);
                        require(expectedDomains[index].equals(
                                constraints.domainForPad(board[0], "UAC." + terminal)) &&
                            expectedDomains[index].equals(
                                constraints.domainForPad(board[0], resistor + ".1")) &&
                            expectedDomains[index].equals(constraints.get(resistor).domainId),
                            "each of the seven converter links stays within its exact terminal-domain assignment: " + terminal);
                    }
                }
            });
            unit(budget, "corridor-footprint-registry", new UnitAction() {
                public void run() {
                    planner[0] = new PcbPlacementPlanner(
                        StandardPcbFootprintProviders.createRegistry());
                }
            });

            result.phase = "CORRIDOR_NULL_SINK_PLACEMENT";
            valueUnit(budget, "corridor-null-sink-planner", new UnitValue<Object>() {
                public Object run() {
                    try {
                        nullPlan[0] = planner[0].plan(board[0],
                            board[0].getPlacementConstraints(), 0L, 0, null);
                    } catch (PcbPlacementPlanner.Rejected rejected) {
                        nullRejected[0] = rejected;
                    }
                    return null;
                }
            });
            result.nullOutcome = outcomeName(nullPlan[0], nullRejected[0]);

            counts[0] = new DiagnosticCounts(budget, null);
            result.phase = "CORRIDOR_OBSERVED_SINK_PLACEMENT";
            valueUnit(budget, "corridor-observed-sink-planner", new UnitValue<Object>() {
                public Object run() {
                    try {
                        observedPlan[0] = planner[0].plan(board[0],
                            board[0].getPlacementConstraints(), 0L, 0, counts[0]);
                    } catch (PcbPlacementPlanner.Rejected rejected) {
                        observedRejected[0] = rejected;
                    }
                    return null;
                }
            });
            result.observedOutcome = outcomeName(observedPlan[0], observedRejected[0]);
            result.snapshot = counts[0].snapshot;
            result.phase = "INDEPENDENT_FAILURE_SNAPSHOT_AND_WITNESS_CHECKS";
            unit(budget, "corridor-parity-and-immutable-witness", new UnitAction() {
                public void run() {
                    require(nullPlan[0] == null && observedPlan[0] == null &&
                        nullRejected[0] != null && observedRejected[0] != null &&
                        "MEDIUM_COMPONENT_PACKING".equals(nullRejected[0].reason) &&
                        nullRejected[0].reason.equals(observedRejected[0].reason) &&
                        result.nullOutcome.equals(result.observedOutcome),
                        "null and observed sinks return the same concrete medium component rejection");
                    require(counts[0].snapshotCalls == 1 && result.snapshot != null &&
                        result.snapshot.getComponentId().equals("UAC") &&
                        result.snapshot.getPlacedCount() == 1,
                        "actual first failed medium item is UAC after only the right-anchored connector was placed");
                    verifyFailureSnapshot(board[0], result.snapshot,
                        counts[0].totalOutcomes());
                    verifyLiteralCorridorWitness(result.snapshot);
                    identity[0].requireUnchanged(board[0]);
                }
            });
            final String immutableWitnessBeforeAbort = result.snapshotJson();
            abortCounts[0] = new DiagnosticCounts(budget, sentinel);
            result.phase = "CALLBACK_ABORT_AND_NULL_SINK_RETRY";
            unit(budget, "sentinel-callback-abort-and-retry", new UnitAction() {
                public void run() {
                    Throwable propagated = null;
                    try {
                        planner[0].plan(board[0], board[0].getPlacementConstraints(),
                            0L, 0, abortCounts[0]);
                    } catch (RuntimeException failure) {
                        propagated = failure;
                    }
                    result.callbackExceptionPropagated = propagated == sentinel;
                    require(result.callbackExceptionPropagated &&
                        abortCounts[0].fitCallbacks == 1 && abortCounts[0].snapshotCalls == 0,
                        "real planner propagates the exact first callback exception before publishing a failure snapshot");
                    PcbPlacementPlanner.Rejected retryRejected = null;
                    try {
                        planner[0].plan(board[0], board[0].getPlacementConstraints(),
                            0L, 0, null);
                    } catch (PcbPlacementPlanner.Rejected rejected) {
                        retryRejected = rejected;
                    }
                    result.retryOutcome = outcomeName(null, retryRejected);
                    require(retryRejected != null && nullRejected[0].reason.equals(retryRejected.reason) &&
                        result.retryOutcome.equals(result.nullOutcome),
                        "null-sink retry after callback abort reaches the same deterministic typed rejection");
                    require(counts[0].snapshot == result.snapshot && counts[0].snapshotCalls == 1 &&
                        immutableWitnessBeforeAbort.equals(result.snapshotJson()),
                        "the retained immutable witness is unchanged by an aborted observation and later planner retry");
                    identity[0].requireUnchanged(board[0]);
                }
            });
        } catch (RuntimeException failure) {
            result.failurePhase = result.phase;
            result.failure = failure;
        } catch (Error failure) {
            result.failurePhase = result.phase;
            result.failure = failure;
        }
        preparePlacementDiagnosticReport(result, budget, new UnitAction() {
            public void run() {
                if (counts[0] != null) {
                    result.fitCounts = counts[0].copyCounts();
                    result.firstFitCount = counts[0].totalOutcomes();
                }
                if (abortCounts[0] != null)
                    result.callbackAbortCallbacks = abortCounts[0].fitCallbacks;
                result.prepareFacts();
            }
        });
        result.operationMillis = nanosToMillis(System.nanoTime() - operationStarted);
        cleanupDiagnosticCase(result, context[0], null);
        result.workUnits = budget.workUnits;
        result.maxUnitMillis = budget.maxUnitMillis();
        result.status = result.failure == null && result.cleanupPassed && result.reportFactsJson != null &&
            result.operationMillis <= nanosToMillis(CASE_LIMIT_NANOS) &&
            result.workUnits <= WORK_LIMIT &&
            result.maxUnitMillis <= nanosToMillis(UNIT_LIMIT_NANOS) ? "PASS" : "FAIL";
        return result;
    }

    private static TroubleshootBoard corridorWitnessBoard() {
        TroubleshootBoard board = new TroubleshootBoard("MEDIUM_ISOLATION_CORRIDOR_WITNESS_20_PART");
        String[] isolationTerminals = PhysicalPackages.ISOLATED_CONVERTER_7.getTerminalIds()
            .toArray(new String[0]);
        board.addComponent(new BoardComponent("UAC", "ISOLATED_CONVERTER",
            PhysicalPackages.ISOLATED_CONVERTER_7));
        board.addComponent(new BoardComponent("JRIGHT", "CONNECTOR",
            PhysicalPackages.THROUGH_HOLE_CONNECTOR_2));
        for (int index = 0; index < 18; index++)
            board.addComponent(new BoardComponent(corridorResistorId(index),
                "RESISTOR", PhysicalPackages.AXIAL_RESISTOR));

        TreeMap<String, String> linkedResistorDomains = new TreeMap<String, String>();
        for (int index = 0; index < isolationTerminals.length; index++) {
            String resistor = corridorResistorId(index);
            String domain = "PRE_L+".equals(isolationTerminals[index]) ||
                "OUT-".equals(isolationTerminals[index]) ? "SECONDARY" : "PRIMARY";
            linkedResistorDomains.put(resistor, domain);
            String sharedNet = "UAC_LINK_" + index;
            String otherNet = resistor + "_OTHER";
            board.addNet(new BoardNet(sharedNet));
            board.addNet(new BoardNet(otherNet));
            board.addPad(new BoardPad("UAC." + isolationTerminals[index], "UAC",
                isolationTerminals[index], sharedNet));
            board.addPad(new BoardPad(resistor + ".1", resistor, "1", sharedNet));
            board.addPad(new BoardPad(resistor + ".2", resistor, "2", otherNet));
        }
        for (int index = isolationTerminals.length; index < 18; index++) {
            String resistor = corridorResistorId(index);
            String firstNet = resistor + "_1", secondNet = resistor + "_2";
            board.addNet(new BoardNet(firstNet));
            board.addNet(new BoardNet(secondNet));
            board.addPad(new BoardPad(resistor + ".1", resistor, "1", firstNet));
            board.addPad(new BoardPad(resistor + ".2", resistor, "2", secondNet));
        }
        for (String terminal : PhysicalPackages.THROUGH_HOLE_CONNECTOR_2.getTerminalIds()) {
            String net = "JRIGHT_" + terminal;
            board.addNet(new BoardNet(net));
            board.addPad(new BoardPad("JRIGHT." + terminal, "JRIGHT", terminal, net));
        }

        Vector<PcbPlacementConstraints.Part> demands =
            new Vector<PcbPlacementConstraints.Part>();
        for (String componentId : board.getComponentIds()) {
            PcbPlacementConstraints.Anchor anchor = "JRIGHT".equals(componentId) ?
                PcbPlacementConstraints.Anchor.RIGHT : PcbPlacementConstraints.Anchor.NONE;
            Vector<PcbPlacementConstraints.TerminalDomain> terminalDomains =
                new Vector<PcbPlacementConstraints.TerminalDomain>();
            if ("UAC".equals(componentId)) {
                for (String terminal : isolationTerminals) {
                    String domain = "PRE_L+".equals(terminal) || "OUT-".equals(terminal) ?
                        "SECONDARY" : "PRIMARY";
                    terminalDomains.add(new PcbPlacementConstraints.TerminalDomain(
                        terminal, domain));
                }
            }
            String domain = linkedResistorDomains.containsKey(componentId) ?
                linkedResistorDomains.get(componentId) : "PRIMARY";
            demands.add(new PcbPlacementConstraints.Part(componentId, "fixture",
                "Circuit", domain, anchor, 20, terminalDomains));
        }
        Vector<PcbPlacementConstraints.Barrier> barriers =
            new Vector<PcbPlacementConstraints.Barrier>();
        barriers.add(new PcbPlacementConstraints.Barrier("PRIMARY", "SECONDARY", 80));
        board.setPlacementConstraints(new PcbPlacementConstraints(demands, barriers,
            PcbCopperLayer.BOTTOM, "MEDIUM_BOARD", 1, 14, true,
            PcbTraceWidthProfile.ROLE_WIDTHS, 4));
        board.validate();
        return board;
    }

    private static String corridorResistorId(int index) {
        return index < 10 ? "R0" + index : "R" + index;
    }

    private static void verifyLiteralCorridorWitness(
            PcbPlacementPlanner.PlacementFailureSnapshot snapshot) {
        PcbPlacementPlanner.CorridorWitnessSnapshot witness =
            snapshot.getFirstGlobalCorridorWitness();
        require(witness != null && "UAC".equals(witness.getTrialComponentId()) &&
            "ISOLATED_CONVERTER_7".equals(witness.getTrialGeometryId()) &&
            "PRIMARY".equals(witness.getBarrierFirstDomain()) &&
            "SECONDARY".equals(witness.getBarrierSecondDomain()) &&
            witness.getBarrierClearance() == 80 && "DEG_0".equals(witness.getTrialRotation()) &&
            "TOP".equals(witness.getTrialSide()) &&
            snapshot.getFirstRequiredOrientationPruneWitness() == null,
            "actual selected UAC footprint emits a global-corridor witness with no separating-axis prune");
        PcbPlacementPlanner.DomainBoundsSnapshot primary = witness.getTrialFirstCourtyard();
        PcbPlacementPlanner.DomainBoundsSnapshot secondary = witness.getTrialSecondCourtyard();
        require(primary != null && primary.isPresent() && secondary != null && secondary.isPresent() &&
            primary.getX() == witness.getTrialX() + 10 &&
            primary.getY() == witness.getTrialY() + 10 &&
            primary.getWidth() == 105 && primary.getHeight() == 310 &&
            secondary.getX() == witness.getTrialX() + 305 &&
            secondary.getY() == witness.getTrialY() + 10 &&
            secondary.getWidth() == 105 && secondary.getHeight() == 310,
            "literal converter package courtyards retain their authored x/y offsets and 105x310 extents");
        PcbPlacementPlanner.DomainBoundsSnapshot beforePrimary =
            witness.getPreTrialFirstDomainBounds();
        PcbPlacementPlanner.DomainBoundsSnapshot beforeSecondary =
            witness.getPreTrialSecondDomainBounds();
        require(beforePrimary != null && beforePrimary.isPresent() &&
            "JRIGHT".equals(beforePrimary.getMinXComponentId()) &&
            "JRIGHT".equals(beforePrimary.getMaxXComponentId()) &&
            "JRIGHT".equals(beforePrimary.getMinYComponentId()) &&
            "JRIGHT".equals(beforePrimary.getMaxYComponentId()) &&
            beforeSecondary != null && !beforeSecondary.isPresent(),
            "pre-trial corridor bounds are independently attributable to the only placed right connector");
        require(witness.getAppliedAllowedOrientationMask() ==
                PcbPlacementPlanner.CORRIDOR_FIRST_LEFT_SECOND &&
            witness.getUnconstrainedSeparatingOrientationMask() == 0,
            "fixed UAC pose requires primary-left-secondary and the right connector prefix admits no separating axis");

        PcbPlacementPlanner.DomainBoundsSnapshot afterPrimary =
            witness.getPostUnionFirstDomainBounds();
        PcbPlacementPlanner.DomainBoundsSnapshot afterSecondary =
            witness.getPostUnionSecondDomainBounds();
        long trialPrimaryX = (long)witness.getTrialX() + 10;
        long trialPrimaryY = (long)witness.getTrialY() + 10;
        long trialSecondaryX = (long)witness.getTrialX() + 305;
        long trialSecondaryY = (long)witness.getTrialY() + 10;
        long primaryLeft = Math.min((long)beforePrimary.getX(), trialPrimaryX);
        long primaryTop = Math.min((long)beforePrimary.getY(), trialPrimaryY);
        long primaryRight = Math.max((long)beforePrimary.getX() + beforePrimary.getWidth(),
            trialPrimaryX + 105);
        long primaryBottom = Math.max((long)beforePrimary.getY() + beforePrimary.getHeight(),
            trialPrimaryY + 310);
        long firstToSecondX = primaryRight + 80 - trialSecondaryX;
        long secondToFirstX = trialSecondaryX + 105 + 80 - primaryLeft;
        long firstToSecondY = primaryBottom + 80 - trialSecondaryY;
        long secondToFirstY = trialSecondaryY + 310 + 80 - primaryTop;
        require(afterPrimary != null && afterPrimary.isPresent() &&
            afterPrimary.getX() == primaryLeft && afterPrimary.getY() == primaryTop &&
            afterPrimary.getWidth() == primaryRight - primaryLeft &&
            afterPrimary.getHeight() == primaryBottom - primaryTop &&
            afterSecondary != null && afterSecondary.isPresent() &&
            afterSecondary.getX() == trialSecondaryX &&
            afterSecondary.getY() == trialSecondaryY &&
            afterSecondary.getWidth() == 105 && afterSecondary.getHeight() == 310,
            "post-union bounds independently equal the right connector plus literal UAC primary and the sole UAC secondary courtyard");
        require(firstToSecondX > 0 && secondToFirstX > 0 &&
            firstToSecondY > 0 && secondToFirstY > 0 &&
            witness.getFirstToSecondXMargin() == firstToSecondX &&
            witness.getSecondToFirstXMargin() == secondToFirstX &&
            witness.getFirstToSecondYMargin() == firstToSecondY &&
            witness.getSecondToFirstYMargin() == secondToFirstY,
            "all four literal-courtyard clearance margins are independently positive and equal the immutable global witness");
    }

    private static void preparePlacementDiagnosticReport(final PlacementDiagnosticCase result,
            Budget budget, final UnitAction prepare) {
        result.phase = "FINAL_REPORT_PREPARATION";
        try {
            unit(budget, "final-report-preparation", new UnitAction() {
                public void run() { prepare.run(); }
            });
        } catch (RuntimeException failure) {
            if (result.failurePhase == null) result.failurePhase = result.phase;
            result.failure = append(result.failure, failure);
        } catch (Error failure) {
            if (result.failurePhase == null) result.failurePhase = result.phase;
            result.failure = append(result.failure, failure);
        }
    }

    private static void cleanupDiagnosticCase(PlacementDiagnosticCase result,
            NativeContext context, Rb100ElectricalPilot.Candidate candidate) {
        if (context == null) {
            result.cleanupPassed = false;
            result.failure = append(result.failure,
                new AssertionError("native context was not created; owner cleanup was unavailable"));
            return;
        }
        long cleanupStarted = System.nanoTime();
        try {
            context.close(candidate);
        } catch (RuntimeException cleanupFailure) {
            if (result.failurePhase == null) result.failurePhase = "CLEANUP";
            result.cleanupFailure = cleanupFailure;
            result.failure = append(result.failure, cleanupFailure);
        } catch (Error cleanupFailure) {
            if (result.failurePhase == null) result.failurePhase = "CLEANUP";
            result.cleanupFailure = cleanupFailure;
            result.failure = append(result.failure, cleanupFailure);
        }
        result.cleanupMillis = nanosToMillis(System.nanoTime() - cleanupStarted);
        result.cleanupPassed = result.cleanupFailure == null &&
            result.cleanupMillis <= CLEANUP_LIMIT_MILLIS;
        if (!result.cleanupPassed && result.cleanupFailure == null) {
            if (result.failurePhase == null) result.failurePhase = "CLEANUP";
            result.failure = append(result.failure,
                new AssertionError("native owner cleanup exceeded the 120 second bound"));
        }
    }

    private static void verifyFailureSnapshot(TroubleshootBoard board,
            PcbPlacementPlanner.PlacementFailureSnapshot snapshot, long observedOutcomes) {
        require(snapshot.getComponentId() != null && board.getComponent(snapshot.getComponentId()) != null,
            "failure snapshot names an actual component in the deterministic planner fixture");
        PcbPlacementConstraints.Part demand = board.getPlacementConstraints().get(snapshot.getComponentId());
        require(demand != null && demand.domainId.equals(snapshot.getDomainId()) &&
            demand.regionId.equals(snapshot.getRegionId()),
            "failure snapshot preserves the selected component's declared domain and region");
        require(snapshot.getOutlineWidth() > 0 && snapshot.getOutlineHeight() > 0 &&
            snapshot.getGridMinOriginX() <= snapshot.getGridMaxOriginX() &&
            snapshot.getGridMinOriginY() <= snapshot.getGridMaxOriginY(),
            "failure snapshot contains finite outline and ordered search bounds");
        require(snapshot.getPlacedCount() >= 0 &&
            snapshot.getPlacedCount() < board.getComponentIds().size(),
            "failure snapshot records the already-placed count before the rejected component");
        long classified = snapshot.getOutlineRejectCount() +
            snapshot.getAccessEnvelopeOverlapCount() + snapshot.getInterDomainBarrierCount() +
            snapshot.getGlobalCorridorCount() + snapshot.getAcceptedCount();
        require(snapshot.getEvaluationCount() > 0 && classified == snapshot.getEvaluationCount() &&
            snapshot.getAcceptedCount() == 0,
            "failed component's first-fit cause counts exactly cover its evaluations with no accepted pose");
        require(snapshot.getPlanEvaluationCount() >= snapshot.getEvaluationCount(),
            "failure snapshot distinguishes cumulative planner evaluations from failed-item evaluations");
        require(snapshot.getPlanEvaluationCount() == observedOutcomes,
            "observed first-fit callbacks equal the cumulative planner evaluation count");
        PcbPlacementPlanner.CorridorWitnessSnapshot witness =
            snapshot.getFirstGlobalCorridorWitness();
        if (witness != null)
            verifyCorridorWitness(board, witness, false);
        PcbPlacementPlanner.CorridorWitnessSnapshot pruneWitness =
            snapshot.getFirstRequiredOrientationPruneWitness();
        if (pruneWitness != null)
            verifyCorridorWitness(board, pruneWitness, true);
    }

    private static void verifyCorridorWitness(TroubleshootBoard board,
            PcbPlacementPlanner.CorridorWitnessSnapshot witness,
            boolean requiredOrientationPrune) {
        require(board.getComponent(witness.getTrialComponentId()) != null,
            "global-corridor witness names an actual selected fixture trial");
        Vector<PcbPlacementConstraints.Barrier> barriers =
            board.getPlacementConstraints().getBarriers();
        boolean declaredBarrier = false;
        for (PcbPlacementConstraints.Barrier barrier : barriers)
            if (barrier.firstDomain.equals(witness.getBarrierFirstDomain()) &&
                    barrier.secondDomain.equals(witness.getBarrierSecondDomain()) &&
                    barrier.clearance == witness.getBarrierClearance())
                declaredBarrier = true;
        require(declaredBarrier,
            "witness barrier identifiers match the actual fixture declaration");

        PhysicalPackage physical = board.getComponent(witness.getTrialComponentId())
            .getPhysicalPackage();
        PcbRotation rotation = PcbRotation.valueOf(witness.getTrialRotation());
        PcbBoardSide side = PcbBoardSide.valueOf(witness.getTrialSide());
        PcbPackagePose pose = new PcbPackagePose(witness.getTrialX(), witness.getTrialY(),
            rotation, side);
        PhysicalPackage.GeometryVariant variant =
            physical.getGeometryVariant(witness.getTrialGeometryVariantKey());
        require(physical.getId().equals(witness.getTrialGeometryId()) && variant != null &&
            variant.getTransformKey().equals(witness.getTrialGeometryTransformKey()) &&
            physical.supportsPose(pose),
            "witness keeps an actual package variant, transform and supported pose");
        TreeMap<String, Rectangle> actualTrialCourtyards =
            board.getPlacementConstraints().domainCourtyards(
                PcbComponentPlacement.fromPhysicalGeometry(witness.getTrialComponentId(),
                    pose, physical, variant.getGeometry()));

        PcbPlacementPlanner.DomainBoundsSnapshot trialFirst = witness.getTrialFirstCourtyard();
        PcbPlacementPlanner.DomainBoundsSnapshot trialSecond = witness.getTrialSecondCourtyard();
        PcbPlacementPlanner.DomainBoundsSnapshot preFirst = witness.getPreTrialFirstDomainBounds();
        PcbPlacementPlanner.DomainBoundsSnapshot preSecond = witness.getPreTrialSecondDomainBounds();
        PcbPlacementPlanner.DomainBoundsSnapshot postFirst = witness.getPostUnionFirstDomainBounds();
        PcbPlacementPlanner.DomainBoundsSnapshot postSecond = witness.getPostUnionSecondDomainBounds();
        requireDomainBounds(trialFirst, witness.getBarrierFirstDomain(), board, true);
        requireDomainBounds(trialSecond, witness.getBarrierSecondDomain(), board, true);
        requireDomainBounds(preFirst, witness.getBarrierFirstDomain(), board, false);
        requireDomainBounds(preSecond, witness.getBarrierSecondDomain(), board, false);
        requireDomainBounds(postFirst, witness.getBarrierFirstDomain(), board, false);
        requireDomainBounds(postSecond, witness.getBarrierSecondDomain(), board, false);
        requireTrialCourtyardContributors(trialFirst, witness.getTrialComponentId());
        requireTrialCourtyardContributors(trialSecond, witness.getTrialComponentId());
        requireBoundsMatchRectangle(trialFirst,
            actualTrialCourtyards.get(witness.getBarrierFirstDomain()),
            "first actual trial courtyard");
        requireBoundsMatchRectangle(trialSecond,
            actualTrialCourtyards.get(witness.getBarrierSecondDomain()),
            "second actual trial courtyard");
        requireNoTrialContributor(preFirst, witness.getTrialComponentId());
        requireNoTrialContributor(preSecond, witness.getTrialComponentId());

        verifyUnionBounds(preFirst, trialFirst, postFirst, "first domain");
        verifyUnionBounds(preSecond, trialSecond, postSecond, "second domain");
        verifyUnionExtrema(preFirst, trialFirst, postFirst, "first domain");
        verifyUnionExtrema(preSecond, trialSecond, postSecond, "second domain");

        long firstToSecondX = (long)postFirst.getX() + postFirst.getWidth() +
            witness.getBarrierClearance() - postSecond.getX();
        long secondToFirstX = (long)postSecond.getX() + postSecond.getWidth() +
            witness.getBarrierClearance() - postFirst.getX();
        long firstToSecondY = (long)postFirst.getY() + postFirst.getHeight() +
            witness.getBarrierClearance() - postSecond.getY();
        long secondToFirstY = (long)postSecond.getY() + postSecond.getHeight() +
            witness.getBarrierClearance() - postFirst.getY();
        require(witness.getFirstToSecondXMargin() == firstToSecondX &&
            witness.getSecondToFirstXMargin() == secondToFirstX &&
            witness.getFirstToSecondYMargin() == firstToSecondY &&
            witness.getSecondToFirstYMargin() == secondToFirstY,
            "witness's four signed clearance margins match its post-union domain bounds");
        int allowed = witness.getAppliedAllowedOrientationMask();
        int unconstrained = witness.getUnconstrainedSeparatingOrientationMask();
        require((allowed & ~PcbPlacementPlanner.CORRIDOR_ALL_ORIENTATIONS) == 0 &&
            (unconstrained & ~PcbPlacementPlanner.CORRIDOR_ALL_ORIENTATIONS) == 0,
            "witness orientation masks contain only declared separating orientations");
        if (requiredOrientationPrune)
            require(unconstrained != 0 && (unconstrained & allowed) == 0 &&
                allowed == PcbPlacementPlanner.CORRIDOR_FIRST_LEFT_SECOND,
                "orientation-prune witness records a separable prefix excluded by applied allowed orientations");
        else
            require(unconstrained == 0,
                "first global-corridor witness records a prefix with no unconstrained separating orientation");
    }

    private static void requireDomainBounds(PcbPlacementPlanner.DomainBoundsSnapshot bounds,
            String expectedDomain, TroubleshootBoard board, boolean trialCourtyard) {
        require(bounds != null && expectedDomain.equals(bounds.getDomainId()),
            "corridor witness identifies bounds for " + expectedDomain);
        if (!bounds.isPresent()) {
            require(bounds.getX() == 0 && bounds.getY() == 0 && bounds.getWidth() == 0 &&
                bounds.getHeight() == 0 && bounds.getMinXComponentId() == null &&
                bounds.getMaxXComponentId() == null && bounds.getMinYComponentId() == null &&
                bounds.getMaxYComponentId() == null,
                "missing domain bounds use the immutable empty representation for " + expectedDomain);
            return;
        }
        require(bounds.getWidth() > 0 && bounds.getHeight() > 0,
            "corridor witness contains positive present bounds for " + expectedDomain);
        TreeSet<String> actualComponents = new TreeSet<String>(board.getComponentIds());
        require(actualComponents.contains(bounds.getMinXComponentId()) &&
            actualComponents.contains(bounds.getMaxXComponentId()) &&
            actualComponents.contains(bounds.getMinYComponentId()) &&
            actualComponents.contains(bounds.getMaxYComponentId()),
            "corridor bounds extrema refer to actual plan component IDs for " + expectedDomain);
        if (trialCourtyard)
            require(bounds.getMinXComponentId().equals(bounds.getMaxXComponentId()) &&
                bounds.getMinXComponentId().equals(bounds.getMinYComponentId()) &&
                bounds.getMinXComponentId().equals(bounds.getMaxYComponentId()),
                "trial courtyard extrema all belong to its one physical component");
    }

    private static void requireBoundsMatchRectangle(
            PcbPlacementPlanner.DomainBoundsSnapshot bounds, Rectangle expected, String label) {
        if (expected == null) {
            require(!bounds.isPresent(),
                label + " is absent when the selected package has no such domain");
            return;
        }
        require(bounds.isPresent() && bounds.getX() == expected.x &&
            bounds.getY() == expected.y && bounds.getWidth() == expected.width &&
            bounds.getHeight() == expected.height,
            label + " matches the selected package geometry at the recorded pose");
    }

    private static void requireTrialCourtyardContributors(
            PcbPlacementPlanner.DomainBoundsSnapshot bounds, String trialComponentId) {
        if (!bounds.isPresent()) return;
        require(trialComponentId.equals(bounds.getMinXComponentId()) &&
            trialComponentId.equals(bounds.getMaxXComponentId()) &&
            trialComponentId.equals(bounds.getMinYComponentId()) &&
            trialComponentId.equals(bounds.getMaxYComponentId()),
            "trial courtyard reports its actual component at every boundary");
    }

    private static void requireNoTrialContributor(
            PcbPlacementPlanner.DomainBoundsSnapshot bounds, String trialComponentId) {
        require(!trialComponentId.equals(bounds.getMinXComponentId()) &&
            !trialComponentId.equals(bounds.getMaxXComponentId()) &&
            !trialComponentId.equals(bounds.getMinYComponentId()) &&
            !trialComponentId.equals(bounds.getMaxYComponentId()),
            "pre-trial aggregate excludes the unplaced failed component");
    }

    private static void verifyUnionBounds(PcbPlacementPlanner.DomainBoundsSnapshot before,
            PcbPlacementPlanner.DomainBoundsSnapshot trial,
            PcbPlacementPlanner.DomainBoundsSnapshot after, String domain) {
        if (!before.isPresent() && !trial.isPresent()) {
            require(!after.isPresent(),
                "post-union remains absent when both inputs are absent for " + domain);
            return;
        }
        PcbPlacementPlanner.DomainBoundsSnapshot only = before.isPresent() ? before : trial;
        if (!before.isPresent() || !trial.isPresent()) {
            require(after.isPresent() && after.getX() == only.getX() &&
                after.getY() == only.getY() && after.getWidth() == only.getWidth() &&
                after.getHeight() == only.getHeight(),
                "post-union preserves the one present input for " + domain);
            return;
        }
        long minX = Math.min((long)before.getX(), trial.getX());
        long minY = Math.min((long)before.getY(), trial.getY());
        long maxX = Math.max((long)before.getX() + before.getWidth(),
            (long)trial.getX() + trial.getWidth());
        long maxY = Math.max((long)before.getY() + before.getHeight(),
            (long)trial.getY() + trial.getHeight());
        require(after.getX() == minX && after.getY() == minY &&
            after.getWidth() == maxX - minX && after.getHeight() == maxY - minY,
            "post-union bounds equal the pre-trial aggregate union trial courtyard for " + domain);
    }

    private static void verifyUnionExtrema(PcbPlacementPlanner.DomainBoundsSnapshot before,
            PcbPlacementPlanner.DomainBoundsSnapshot trial,
            PcbPlacementPlanner.DomainBoundsSnapshot after, String domain) {
        if (!before.isPresent() || !trial.isPresent()) {
            if (before.isPresent() || trial.isPresent()) {
                PcbPlacementPlanner.DomainBoundsSnapshot only = before.isPresent() ? before : trial;
                require(after.getMinXComponentId().equals(only.getMinXComponentId()) &&
                    after.getMaxXComponentId().equals(only.getMaxXComponentId()) &&
                    after.getMinYComponentId().equals(only.getMinYComponentId()) &&
                    after.getMaxYComponentId().equals(only.getMaxYComponentId()),
                    "post-union preserves the one present input's extrema for " + domain);
            }
            return;
        }
        verifyUnionExtremum(before.getX(), before.getMinXComponentId(),
            trial.getX(), trial.getMinXComponentId(), after.getX(), after.getMinXComponentId(),
            true, domain + " minX");
        verifyUnionExtremum((long)before.getX() + before.getWidth(),
            before.getMaxXComponentId(), (long)trial.getX() + trial.getWidth(),
            trial.getMaxXComponentId(), (long)after.getX() + after.getWidth(),
            after.getMaxXComponentId(), false, domain + " maxX");
        verifyUnionExtremum(before.getY(), before.getMinYComponentId(),
            trial.getY(), trial.getMinYComponentId(), after.getY(), after.getMinYComponentId(),
            true, domain + " minY");
        verifyUnionExtremum((long)before.getY() + before.getHeight(),
            before.getMaxYComponentId(), (long)trial.getY() + trial.getHeight(),
            trial.getMaxYComponentId(), (long)after.getY() + after.getHeight(),
            after.getMaxYComponentId(), false, domain + " maxY");
    }

    private static void verifyUnionExtremum(long beforeValue, String beforeComponent,
            long trialValue, String trialComponent, long afterValue, String afterComponent,
            boolean minimum, String label) {
        long expected = minimum ? Math.min(beforeValue, trialValue) :
            Math.max(beforeValue, trialValue);
        String expectedComponent;
        if (beforeValue == trialValue)
            expectedComponent = beforeComponent.equals(trialComponent) ? beforeComponent : null;
        else if (minimum ? trialValue < beforeValue : trialValue > beforeValue)
            expectedComponent = trialComponent;
        else expectedComponent = beforeComponent;
        boolean contributorMatches = expectedComponent == null ?
            afterComponent.equals(beforeComponent) || afterComponent.equals(trialComponent) :
            expectedComponent.equals(afterComponent);
        require(afterValue == expected && contributorMatches,
            "post-union extremum retains a valid source component for " + label);
    }

    private static String outcomeName(PcbPlacementPlanner.Plan plan,
            PcbPlacementPlanner.Rejected rejection) {
        if (plan != null) return "ACCEPTED";
        return rejection == null ? "NO_PLACEMENT_RESULT" : typedFailure(rejection);
    }

    private static String padRecord(BoardPad pad) {
        return pad.getComponentId() + "|" + pad.getTerminalId() + "|" + pad.getNetId();
    }

    private static boolean containsIdentity(Vector<CircuitElm> elements, CircuitElm target) {
        for (CircuitElm element : elements) if (element == target) return true;
        return false;
    }

    private static Rb100Plan.Variant[] variants() {
        return new Rb100Plan.Variant[] {
            Rb100Plan.Variant.SHARED_HYSTERETIC_LINEAR,
            Rb100Plan.Variant.SEPARATE_DIRECT_SWITCHING
        };
    }

    private static void require(boolean condition, String message) {
        assertions++;
        if (!condition) throw new AssertionError("RB100 physical diagnostics: " + message);
    }

    private static long nanosToMillis(long nanos) { return nanos / 1000000L; }

    private static String key(String canonical) {
        return Integer.toHexString(canonical.hashCode()) + "/" + canonical.length();
    }

    private static String json(String value) {
        if (value == null) return "";
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < value.length() && i < 400; i++) {
            char ch = value.charAt(i);
            if (ch == '"' || ch == '\\') out.append('\\').append(ch);
            else if (ch == '\n') out.append("\\n");
            else if (ch == '\r') out.append("\\r");
            else if (ch == '\t') out.append("\\t");
            else if (ch < 32) out.append('?');
            else out.append(ch);
        }
        if (value.length() > 400) out.append("...");
        return out.toString();
    }

    /** Complete JSON escaping for bounded routing decisions and geometry fingerprints. */
    private static String jsonFull(String value) {
        if (value == null) return "";
        final char[] hex = "0123456789abcdef".toCharArray();
        StringBuilder out = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            if (ch == '"' || ch == '\\') out.append('\\').append(ch);
            else if (ch == '\n') out.append("\\n");
            else if (ch == '\r') out.append("\\r");
            else if (ch == '\t') out.append("\\t");
            else if (ch < 32) {
                out.append("\\u").append(hex[(ch >>> 12) & 0xf])
                    .append(hex[(ch >>> 8) & 0xf]).append(hex[(ch >>> 4) & 0xf])
                    .append(hex[ch & 0xf]);
            } else out.append(ch);
        }
        return out.toString();
    }

    private static Throwable append(Throwable prior, Throwable next) {
        if (prior == null) return next;
        prior.addSuppressed(next);
        return prior;
    }

    private static String typedFailure(Throwable failure) {
        if (failure == null) return "none";
        if (failure instanceof BudgetExceeded)
            return "BUDGET_" + ((BudgetExceeded)failure).reason;
        if (failure instanceof PcbRoutingRejectedException) {
            PcbRoutingRejectedException rejected = (PcbRoutingRejectedException)failure;
            return "PCB_" + rejected.getKind() + (rejected.isExhausted() ? "_EXHAUSTED" : "_REJECTED");
        }
        if (failure instanceof PcbPlacementPlanner.Rejected)
            return "PLACEMENT_" + ((PcbPlacementPlanner.Rejected)failure).reason;
        if (failure instanceof PcbNetRouter.Rejected)
            return "ROUTER_" + ((PcbNetRouter.Rejected)failure).reason;
        if (failure instanceof SupportedEnvelope.Rejected)
            return "ENVELOPE_" + ((SupportedEnvelope.Rejected)failure).reason;
        return failure.getClass().getSimpleName();
    }

    private interface UnitAction { void run(); }
    private interface UnitValue<T> { T run(); }

    private static void unit(Budget budget, String name, UnitAction action) {
        budget.beginUnit(name);
        Throwable thrown = null;
        try { action.run(); }
        catch (RuntimeException failure) { thrown = failure; throw failure; }
        catch (Error failure) { thrown = failure; throw failure; }
        finally { budget.endUnit(thrown); }
    }

    private static <T> T valueUnit(Budget budget, String name, UnitValue<T> action) {
        budget.beginUnit(name);
        Throwable thrown = null;
        try { return action.run(); }
        catch (RuntimeException failure) { thrown = failure; throw failure; }
        catch (Error failure) { thrown = failure; throw failure; }
        finally { budget.endUnit(thrown); }
    }

    private static final class Budget {
        final long started;
        int workUnits;
        long maxUnitNanos;
        long unitStarted;
        String unitName;
        Budget(long started) { this.started = started; }
        void beginUnit(String name) {
            checkpoint();
            if (workUnits >= WORK_LIMIT) throw new BudgetExceeded("WORK_LIMIT", name);
            workUnits++;
            unitName = name;
            unitStarted = System.nanoTime();
        }
        void checkpoint() {
            long now = System.nanoTime();
            if (now - started > CASE_LIMIT_NANOS)
                throw new BudgetExceeded("CASE_DEADLINE", unitName);
            if (unitName != null && now - unitStarted > UNIT_LIMIT_NANOS)
                throw new BudgetExceeded("UNIT_DEADLINE", unitName);
        }
        void endUnit(Throwable primary) {
            long elapsed = System.nanoTime() - unitStarted;
            if (elapsed > maxUnitNanos) maxUnitNanos = elapsed;
            String ended = unitName;
            unitName = null;
            try {
                if (elapsed > UNIT_LIMIT_NANOS)
                    throw new BudgetExceeded("UNIT_DEADLINE", ended);
                if (System.nanoTime() - started > CASE_LIMIT_NANOS)
                    throw new BudgetExceeded("CASE_DEADLINE", ended);
            } catch (RuntimeException budgetFailure) {
                if (primary != null) primary.addSuppressed(budgetFailure);
                else throw budgetFailure;
            }
        }
        long maxUnitMillis() { return nanosToMillis(maxUnitNanos); }
    }

    private static final class BudgetExceeded extends RuntimeException {
        private static final long serialVersionUID = 1L;
        final String reason;
        BudgetExceeded(String reason, String unit) {
            super(reason + (unit == null ? "" : " in " + unit));
            this.reason = reason;
        }
    }

    private static class PlacementDiagnosticCase {
        final String fixture, subject;
        final long seed;
        final int candidate;
        String phase = "START", status = "FAIL";
        String nullOutcome = "NOT_RUN", observedOutcome = "NOT_RUN", retryOutcome = "NOT_RUN";
        String nullGeometry, observedGeometry, retryGeometry;
        String failurePhase, reportFactsJson;
        Throwable failure, cleanupFailure;
        PcbPlacementPlanner.PlacementFailureSnapshot snapshot;
        EnumMap<PcbPlacementPlanner.FitOutcome, Long> fitCounts =
            new EnumMap<PcbPlacementPlanner.FitOutcome, Long>(PcbPlacementPlanner.FitOutcome.class);
        long firstFitCount, operationMillis, cleanupMillis, maxUnitMillis;
        int workUnits, components, pads, nets, callbackAbortCallbacks;
        boolean cleanupPassed, callbackExceptionPropagated;
        PlacementDiagnosticCase(String fixture, String subject, long seed, int candidate) {
            this.fixture = fixture; this.subject = subject;
            this.seed = seed; this.candidate = candidate;
            for (PcbPlacementPlanner.FitOutcome outcome : PcbPlacementPlanner.FitOutcome.values())
                fitCounts.put(outcome, 0L);
        }
        String toJson() {
            return "{\"status\":\"" + status + "\",\"facts\":" +
                (reportFactsJson == null ? "null" : reportFactsJson) +
                ",\"phase\":\"" + json(phase) +
                "\",\"operationMs\":" + operationMillis + ",\"cleanupMs\":" + cleanupMillis +
                ",\"cleanupStatus\":\"" + (cleanupPassed ? "PASS" : "FAIL") +
                "\",\"workUnits\":" + workUnits + ",\"maxUnitMs\":" + maxUnitMillis +
                ",\"failurePhase\":\"" + json(failurePhase) +
                "\",\"failureType\":\"" + json(typedFailure(failure)) +
                "\",\"failure\":\"" + json(failure == null ? "" : failure.toString()) +
                "\",\"cleanupFailureType\":\"" + json(typedFailure(cleanupFailure)) +
                "\",\"cleanupFailure\":\"" +
                json(cleanupFailure == null ? "" : cleanupFailure.toString()) +
                "\",\"scope\":\"CURRENT_PLACEMENT_DIAGNOSTICS_ONLY\"}";
        }
        void prepareFacts() {
            reportFactsJson = "{\"fixture\":\"" + json(fixture) + "\",\"subject\":\"" +
                json(subject) + "\",\"seed\":\"" + seed + "\",\"candidate\":" + candidate +
                ",\"components\":" + components + ",\"pads\":" + pads + ",\"nets\":" + nets +
                ",\"nullOutcome\":\"" + json(nullOutcome) +
                "\",\"retryOutcome\":\"" + json(retryOutcome) +
                "\",\"layoutVersion\":" + SeededPcbLayoutGenerator.CURRENT_VERSION +
                ",\"dependencyEpoch\":\"" +
                json(GenerationDependencyContext.INTERPRETATION_EPOCH) +
                "\",\"planVersion\":" + Rb100Plan.PLAN_VERSION +
                ",\"modelVersion\":" + Rb100Plan.MODEL_VERSION +
                ",\"physicalIntentVersion\":" + Rb100Placement.INTENT_VERSION +
                ",\"observedOutcome\":\"" + json(observedOutcome) +
                "\",\"nullGeometryKey\":\"" + geometryKey(nullGeometry) +
                "\",\"observedGeometryKey\":\"" + geometryKey(observedGeometry) +
                "\",\"retryGeometryKey\":\"" + geometryKey(retryGeometry) +
                "\",\"firstFitOutcomeCount\":" + firstFitCount +
                ",\"firstFitOutcomes\":" + fitCountsJson() +
                ",\"failureSnapshot\":" + snapshotJson() +
                ",\"callbackExceptionPropagated\":" + callbackExceptionPropagated +
                ",\"callbackAbortCallbacks\":" + callbackAbortCallbacks +
                "}";
        }
        private String geometryKey(String geometry) {
            return geometry == null ? "NOT_AVAILABLE" : json(key(geometry));
        }
        private String fitCountsJson() {
            return "{\"outline\":" + count(PcbPlacementPlanner.FitOutcome.OUTLINE) +
                ",\"accessEnvelopeOverlap\":" +
                count(PcbPlacementPlanner.FitOutcome.ACCESS_ENVELOPE_OVERLAP) +
                ",\"interDomainBarrier\":" +
                count(PcbPlacementPlanner.FitOutcome.INTER_DOMAIN_BARRIER) +
                ",\"globalCorridor\":" +
                count(PcbPlacementPlanner.FitOutcome.GLOBAL_CORRIDOR) +
                ",\"accepted\":" + count(PcbPlacementPlanner.FitOutcome.ACCEPTED) + "}";
        }
        private long count(PcbPlacementPlanner.FitOutcome outcome) {
            Long value = fitCounts.get(outcome);
            return value == null ? 0 : value.longValue();
        }
        private String snapshotJson() {
            if (snapshot == null) return "null";
            return "{\"componentId\":\"" + json(snapshot.getComponentId()) +
                "\",\"domainId\":\"" + json(snapshot.getDomainId()) +
                "\",\"regionId\":\"" + json(snapshot.getRegionId()) +
                "\",\"outline\":{\"x\":" + snapshot.getOutlineX() +
                ",\"y\":" + snapshot.getOutlineY() +
                ",\"width\":" + snapshot.getOutlineWidth() +
                ",\"height\":" + snapshot.getOutlineHeight() + "},\"gridBounds\":{\"minOriginX\":" +
                snapshot.getGridMinOriginX() + ",\"maxOriginX\":" + snapshot.getGridMaxOriginX() +
                ",\"minOriginY\":" + snapshot.getGridMinOriginY() +
                ",\"maxOriginY\":" + snapshot.getGridMaxOriginY() +
                "},\"placedCount\":" + snapshot.getPlacedCount() +
                ",\"evaluationCount\":" + snapshot.getEvaluationCount() +
                ",\"planEvaluationCount\":" + snapshot.getPlanEvaluationCount() +
                ",\"firstFitCauseCounts\":{\"outline\":" + snapshot.getOutlineRejectCount() +
                ",\"accessEnvelopeOverlap\":" + snapshot.getAccessEnvelopeOverlapCount() +
                ",\"interDomainBarrier\":" + snapshot.getInterDomainBarrierCount() +
                ",\"globalCorridor\":" + snapshot.getGlobalCorridorCount() +
                ",\"accepted\":" + snapshot.getAcceptedCount() +
                "},\"firstGlobalCorridorWitness\":" +
                corridorWitnessJson(snapshot.getFirstGlobalCorridorWitness()) +
                ",\"firstRequiredOrientationPruneWitness\":" +
                corridorWitnessJson(snapshot.getFirstRequiredOrientationPruneWitness()) + "}";
        }
        private String corridorWitnessJson(
                PcbPlacementPlanner.CorridorWitnessSnapshot witness) {
            if (witness == null) return "null";
            return "{\"barrier\":{\"firstDomain\":\"" + json(witness.getBarrierFirstDomain()) +
                "\",\"secondDomain\":\"" + json(witness.getBarrierSecondDomain()) +
                "\",\"clearance\":" + witness.getBarrierClearance() +
                "},\"trial\":{\"componentId\":\"" + json(witness.getTrialComponentId()) +
                "\",\"geometryId\":\"" + json(witness.getTrialGeometryId()) +
                "\",\"geometryVariantKey\":\"" + json(witness.getTrialGeometryVariantKey()) +
                "\",\"geometryTransformKey\":\"" + json(witness.getTrialGeometryTransformKey()) +
                "\",\"x\":" + witness.getTrialX() + ",\"y\":" + witness.getTrialY() +
                ",\"rotation\":\"" + json(witness.getTrialRotation()) +
                "\",\"side\":\"" + json(witness.getTrialSide()) + "\"}," +
                "\"trialDomainCourtyards\":{\"first\":" +
                domainBoundsJson(witness.getTrialFirstCourtyard()) + ",\"second\":" +
                domainBoundsJson(witness.getTrialSecondCourtyard()) +
                "},\"preTrialAggregateBounds\":{\"first\":" +
                domainBoundsJson(witness.getPreTrialFirstDomainBounds()) + ",\"second\":" +
                domainBoundsJson(witness.getPreTrialSecondDomainBounds()) +
                "},\"postUnionAggregateBounds\":{\"first\":" +
                domainBoundsJson(witness.getPostUnionFirstDomainBounds()) + ",\"second\":" +
                domainBoundsJson(witness.getPostUnionSecondDomainBounds()) +
                "},\"signedMargins\":{\"meaning\":\"positive=clearance-shortfall; zero-or-negative=satisfies-orientation\"," +
                "\"firstToSecondXMargin\":" + witness.getFirstToSecondXMargin() +
                ",\"secondToFirstXMargin\":" + witness.getSecondToFirstXMargin() +
                ",\"firstToSecondYMargin\":" + witness.getFirstToSecondYMargin() +
                ",\"secondToFirstYMargin\":" + witness.getSecondToFirstYMargin() +
                "},\"orientationMasks\":{\"appliedAllowed\":" +
                witness.getAppliedAllowedOrientationMask() +
                ",\"appliedAllowedNames\":" + orientationMaskNamesJson(
                    witness.getAppliedAllowedOrientationMask()) +
                ",\"unconstrainedSeparating\":" +
                witness.getUnconstrainedSeparatingOrientationMask() +
                ",\"unconstrainedSeparatingNames\":" + orientationMaskNamesJson(
                    witness.getUnconstrainedSeparatingOrientationMask()) + "}}";
        }
        private String orientationMaskNamesJson(int mask) {
            StringBuilder names = new StringBuilder("[");
            String[] labels = new String[] { "firstLeftSecond", "secondLeftFirst",
                "firstAboveSecond", "secondAboveFirst" };
            int[] bits = new int[] { PcbPlacementPlanner.CORRIDOR_FIRST_LEFT_SECOND,
                PcbPlacementPlanner.CORRIDOR_SECOND_LEFT_FIRST,
                PcbPlacementPlanner.CORRIDOR_FIRST_ABOVE_SECOND,
                PcbPlacementPlanner.CORRIDOR_SECOND_ABOVE_FIRST };
            boolean comma = false;
            for (int index = 0; index < bits.length; index++)
                if ((mask & bits[index]) != 0) {
                    if (comma) names.append(',');
                    names.append('"').append(labels[index]).append('"');
                    comma = true;
                }
            return names.append(']').toString();
        }
        private String domainBoundsJson(PcbPlacementPlanner.DomainBoundsSnapshot bounds) {
            if (bounds == null) return "null";
            return "{\"domainId\":\"" + json(bounds.getDomainId()) +
                "\",\"present\":" + bounds.isPresent() +
                ",\"x\":" + bounds.getX() + ",\"y\":" + bounds.getY() +
                ",\"width\":" + bounds.getWidth() + ",\"height\":" + bounds.getHeight() +
                ",\"extremaComponents\":{\"minX\":\"" + json(bounds.getMinXComponentId()) +
                "\",\"maxX\":\"" + json(bounds.getMaxXComponentId()) +
                "\",\"minY\":\"" + json(bounds.getMinYComponentId()) +
                "\",\"maxY\":\"" + json(bounds.getMaxYComponentId()) + "\"}}";
        }
    }

    private static final class RoutingInputMetadata {
        final String physicalPolicyId, routingLayer, traceWidthProfile;
        final int physicalPolicyVersion, placementDensityVersion, placementPackingVersion;
        final boolean requiresTwoLayerRouting;

        private RoutingInputMetadata(PcbPlacementConstraints constraints) {
            physicalPolicyId = constraints.getPhysicalPolicyId();
            physicalPolicyVersion = constraints.getPhysicalPolicyVersion();
            placementDensityVersion = constraints.placementDensityVersion;
            routingLayer = constraints.routingLayer.name();
            traceWidthProfile = constraints.getTraceWidthProfile().getIdentity();
            placementPackingVersion = constraints.getPlacementPackingVersion();
            requiresTwoLayerRouting = constraints.requiresTwoLayerRouting;
        }

        static RoutingInputMetadata capture(PcbPlacementConstraints constraints) {
            if (constraints == null)
                throw new IllegalArgumentException("Missing actual placement constraints");
            return new RoutingInputMetadata(constraints);
        }

        void requireCurrentP07Treatment() {
            require("MEDIUM_BOARD".equals(physicalPolicyId) &&
                physicalPolicyVersion == 1 && placementDensityVersion == 14 &&
                "BOTTOM".equals(routingLayer) &&
                "ROLE_TRACE@1".equals(traceWidthProfile) &&
                placementPackingVersion == 4 && requiresTwoLayerRouting,
                "actual intent9 input selects MEDIUM_BOARD@1/P07 with ROLE_TRACE@1 at density14 and packing4");
        }

        boolean sameAs(RoutingInputMetadata other) {
            return other != null && physicalPolicyId.equals(other.physicalPolicyId) &&
                physicalPolicyVersion == other.physicalPolicyVersion &&
                placementDensityVersion == other.placementDensityVersion &&
                routingLayer.equals(other.routingLayer) &&
                traceWidthProfile.equals(other.traceWidthProfile) &&
                placementPackingVersion == other.placementPackingVersion &&
                requiresTwoLayerRouting == other.requiresTwoLayerRouting;
        }
    }

    private static final class P07PolicyDiagnosticCase extends PlacementDiagnosticCase {
        final String runId;
        long placementSeed, routingSeed;
        int advanceCalls, observerChecks;
        String sessionTerminalStatus = "NOT_REACHED";
        String boardIdentityStatus = "NOT_REACHED";
        String solverGraphIdentityStatus = "NOT_REACHED";
        RoutingInputMetadata inputMetadata;
        PcbRoutingRejectedException sessionRejection;
        MediumBoardPhysicalPolicy.Result policyResult;
        String layoutOutputJson = "null";

        P07PolicyDiagnosticCase(String variant, long seed, String runId) {
            super("AUTHORED_RB100", variant, seed, -1);
            this.runId = runId;
        }

        void captureLayoutOutput() {
            if (policyResult == null) {
                layoutOutputJson = "null";
                return;
            }
            PcbBoardLayout layout = policyResult.getLayout();
            if (layout == null) {
                layoutOutputJson = "{\"layoutReturned\":false,\"zeroOutput\":true,\"fingerprint\":null," +
                    "\"outline\":null,\"rawSegments\":0,\"topTraceCount\":0," +
                    "\"bottomTraceCount\":0,\"totalTraceCount\":0,\"holeCount\":0," +
                    "\"viaCount\":0,\"platedHoleCount\":0,\"nonPlatedHoleCount\":0}";
                return;
            }
            String fingerprint = layout.geometryFingerprint();
            require(fingerprint != null && fingerprint.length() > 0 &&
                fingerprint.length() <= 1024 * 1024,
                "selected P07 output has a complete canonical geometry fingerprint within1MiB");
            Rectangle outline = layout.getBoardOutline();
            Vector<PcbTraceGeometry> traces = layout.getTraces();
            int topTraces = 0, bottomTraces = 0;
            for (PcbTraceGeometry trace : traces) {
                if (trace.getLayer() == PcbCopperLayer.TOP) topTraces++;
                else if (trace.getLayer() == PcbCopperLayer.BOTTOM) bottomTraces++;
                else throw new AssertionError("P07 output contains an unknown copper face");
            }
            Vector<PcbBoardHole> holes = layout.getHoles();
            int vias = 0, plated = 0, nonPlated = 0;
            for (PcbBoardHole hole : holes) {
                if (hole.kind == PcbBoardHole.Kind.VIA) vias++;
                else if (hole.kind == PcbBoardHole.Kind.PLATED) plated++;
                else if (hole.kind == PcbBoardHole.Kind.NON_PLATED) nonPlated++;
                else throw new AssertionError("P07 output contains an unknown hole kind");
            }
            require(topTraces + bottomTraces == traces.size() &&
                holes.size() == layout.getHoleCount() &&
                vias <= PcbLayerRoutingPrototype.Policy.FULLER_TWO_LAYER.vias,
                "selected P07 output face and drill counts cover the actual geometry objects");
            layoutOutputJson = "{\"layoutReturned\":true,\"zeroOutput\":false,\"fingerprint\":\"" +
                jsonFull(fingerprint) + "\",\"fingerprintLength\":" + fingerprint.length() +
                ",\"fingerprintJavaHash\":\"" + Integer.toHexString(fingerprint.hashCode()) +
                "\",\"outline\":{\"x\":" + outline.x + ",\"y\":" + outline.y +
                ",\"width\":" + outline.width + ",\"height\":" + outline.height +
                "},\"rawSegments\":" + layout.getRawRoutingSegments() +
                ",\"topTraceCount\":" + topTraces + ",\"bottomTraceCount\":" + bottomTraces +
                ",\"totalTraceCount\":" + traces.size() + ",\"holeCount\":" + holes.size() +
                ",\"viaCount\":" + vias + ",\"platedHoleCount\":" + plated +
                ",\"nonPlatedHoleCount\":" + nonPlated + "}";
        }

        void prepareFacts() {
            StringBuilder facts = new StringBuilder("{\"protocolVersion\":2,\"runId\":\"");
            facts.append(jsonFull(runId)).append("\",\"fixture\":\"AUTHORED_RB100\"")
                .append(",\"variant\":\"").append(jsonFull(subject)).append("\"")
                .append(",\"layoutVersion\":").append(SeededPcbLayoutGenerator.CURRENT_VERSION)
                .append(",\"dependencyEpoch\":\"")
                .append(jsonFull(GenerationDependencyContext.INTERPRETATION_EPOCH)).append("\"")
                .append(",\"planVersion\":").append(Rb100Plan.PLAN_VERSION)
                .append(",\"modelVersion\":").append(Rb100Plan.MODEL_VERSION)
                .append(",\"physicalIntentVersion\":").append(Rb100Placement.INTENT_VERSION)
                .append(routingInputMetadataFields(inputMetadata))
                .append(",\"seed\":\"").append(seed)
                .append("\",\"placementSeed\":\"").append(placementSeed)
                .append("\",\"routingSeed\":\"").append(routingSeed).append("\"")
                .append(",\"components\":").append(components)
                .append(",\"pads\":").append(pads)
                .append(",\"nets\":").append(nets)
                .append(",\"boardIdentityStatus\":\"").append(boardIdentityStatus).append("\"")
                .append(",\"solverGraphIdentityStatus\":\"")
                .append(solverGraphIdentityStatus).append("\"")
                .append(",\"sessionTerminalStatus\":\"")
                .append(jsonFull(sessionTerminalStatus)).append("\"")
                .append(",\"sessionException\":").append(sessionExceptionJson(sessionRejection))
                .append(",\"advanceCalls\":").append(advanceCalls)
                .append(",\"observerChecks\":").append(observerChecks)
                .append(",\"limits\":").append(limitsJson())
                .append(",\"primaryRoutingOrders\":")
                .append(primaryRoutingOrdersJson(policyResult))
                .append(",\"primaryRoutingExpansions\":")
                .append(primaryRoutingExpansionsJson(policyResult))
                .append(",\"policyFailure\":");
            String policyFailure = policyResult == null ? null : policyResult.getFailure();
            facts.append(policyFailure == null ? "null" : "\"" + jsonFull(policyFailure) + "\"")
                .append(",\"policyStatistics\":").append(policyStatisticsJson(policyResult))
                .append(",\"packingProbe\":").append(packingProbeJson(policyResult))
                .append(",\"layoutOutput\":").append(layoutOutputJson)
                .append(",\"layoutQualification\":false}");
            reportFactsJson = facts.toString();
        }

        @Override String toJson() {
            return "{\"protocolVersion\":2,\"runId\":\"" + jsonFull(runId) +
                "\",\"status\":\"" + status + "\",\"phase\":\"" +
                jsonFull(phase) + "\",\"operationMs\":" + operationMillis +
                ",\"cleanupMs\":" + cleanupMillis + ",\"cleanupStatus\":\"" +
                (cleanupPassed ? "PASS" : "FAIL") + "\",\"workUnits\":" + workUnits +
                ",\"maxUnitMs\":" + maxUnitMillis + ",\"failurePhase\":\"" +
                jsonFull(failurePhase) + "\",\"failureType\":\"" +
                jsonFull(typedFailure(failure)) + "\",\"failure\":\"" +
                jsonFull(failure == null ? "" : failure.toString()) +
                "\",\"cleanupFailureType\":\"" + jsonFull(typedFailure(cleanupFailure)) +
                "\",\"cleanupFailure\":\"" + jsonFull(cleanupFailure == null ? "" :
                    cleanupFailure.toString()) + "\",\"scope\":\"OBSERVATIONAL_PRIVATE_P07_POLICY_ONLY\"," +
                "\"layoutQualification\":false,\"reportFacts\":" +
                (reportFactsJson == null ? "null" : reportFactsJson) + "}";
        }

        private static String sessionExceptionJson(PcbRoutingRejectedException rejection) {
            if (rejection == null) return "null";
            return "{\"type\":\"PcbRoutingRejectedException\",\"kind\":\"" +
                rejection.getKind().name() + "\",\"attemptIndex\":" +
                rejection.getAttemptIndex() + ",\"attemptCount\":" +
                rejection.getAttemptCount() + ",\"seed\":\"" + rejection.getSeed() +
                "\",\"exhausted\":" + rejection.isExhausted() + ",\"message\":\"" +
                jsonFull(rejection.getMessage()) + "\"}";
        }

        private static String limitsJson() {
            return "{\"placementCandidates\":" + MediumBoardPhysicalPolicy.PLACEMENT_CANDIDATES +
                ",\"routeCandidates\":" + MediumBoardPhysicalPolicy.ROUTING_CANDIDATES +
                ",\"p07Expansions\":" + PcbLayerRoutingPrototype.MAX_EXPANSIONS +
                ",\"p07BranchExpansions\":" + PcbLayerRoutingPrototype.MAX_BRANCH_EXPANSIONS +
                ",\"p07GridCells\":" + PcbLayerRoutingPrototype.MAX_CELLS +
                ",\"p07Orderings\":" + PcbLayerRoutingPrototype.ORDERINGS +
                ",\"p07Vias\":" + PcbLayerRoutingPrototype.Policy.FULLER_TWO_LAYER.vias +
                ",\"outerPlacements\":" + MAX_PLACEMENTS +
                ",\"outerRouteAttempts\":" + MAX_ROUTE_ATTEMPTS +
                ",\"outerRouteExpansions\":" + MAX_ROUTE_EXPANSIONS +
                ",\"outerAccessCells\":" + MAX_ACCESS_CELLS +
                ",\"operationMs\":" + nanosToMillis(CASE_LIMIT_NANOS) +
                ",\"workUnits\":" + WORK_LIMIT +
                ",\"unitMs\":" + nanosToMillis(UNIT_LIMIT_NANOS) +
                ",\"cleanupMs\":" + CLEANUP_LIMIT_MILLIS + "}";
        }

        private static String policyStatisticsJson(MediumBoardPhysicalPolicy.Result result) {
            if (result == null || result.getStatistics() == null) return "null";
            MediumBoardPhysicalPolicy.Statistics statistics = result.getStatistics();
            String canonical = statistics.toCanonical();
            return "{\"policyIdentity\":\"" + jsonFull(statistics.policyIdentity) +
                "\",\"outcome\":\"" + jsonFull(statistics.outcome) +
                "\",\"selectedRoutePolicy\":" + nullableString(statistics.selectedRoutePolicy) +
                ",\"placementCandidates\":" + statistics.placementCandidates +
                ",\"placementRejections\":" + statistics.placementRejections +
                ",\"placementEvaluations\":" + statistics.placementEvaluations +
                ",\"routeAttempts\":" + statistics.routeAttempts +
                ",\"oneFaceAttempts\":" + statistics.oneFaceAttempts +
                ",\"oneFaceSuccesses\":" + statistics.oneFaceSuccesses +
                ",\"twoLayerAttempts\":" + statistics.twoLayerAttempts +
                ",\"twoLayerSuccesses\":" + statistics.twoLayerSuccesses +
                ",\"selectedPlacementAttempt\":" + statistics.selectedPlacementAttempt +
                ",\"rankedPlacementCount\":" + statistics.rankedPlacementAttempts.size() +
                ",\"rankedPlacementAttempts\":" + integerVectorJson(
                    statistics.rankedPlacementAttempts) +
                ",\"placementScoreCount\":" + statistics.placementScores.size() +
                ",\"placementScores\":" + stringVectorJson(statistics.placementScores) +
                ",\"routeOutcomesCount\":" + statistics.routeOutcomes.size() +
                ",\"routeOutcomes\":" + stringVectorJson(statistics.routeOutcomes) +
                ",\"orderingSearchReceiptsCount\":" + statistics.orderingSearchReceipts.size() +
                ",\"orderingSearchReceipts\":" + orderingSearchReceiptsJson(statistics.orderingSearchReceipts) +
                ",\"routeEligiblePlacementAttemptCount\":" +
                    statistics.routeEligiblePlacementAttempts.size() +
                ",\"routeEligiblePlacementAttempts\":" + integerVectorJson(
                    statistics.routeEligiblePlacementAttempts) +
                ",\"routeEligibilitySkipCount\":" + statistics.routeEligibilitySkips.size() +
                ",\"routeEligibilitySkips\":" +
                    stringVectorJson(statistics.routeEligibilitySkips) +
                ",\"routingOrders\":" + statistics.routingOrders +
                ",\"routingExpansions\":" + statistics.routingExpansions +
                ",\"selectedPlacementScore\":" + statistics.selectedPlacementScore +
                ",\"selectedRouteQualityMilli\":" + statistics.selectedRouteQualityMilli +
                ",\"canonicalLength\":" + canonical.length() +
                ",\"canonicalJavaHash\":\"" + Integer.toHexString(canonical.hashCode()) +
                "\",\"canonical\":\"" + jsonFull(canonical) + "\"}";
        }

        private static String orderingSearchReceiptsJson(
                Vector<MediumBoardPhysicalPolicy.OrderingSearchReceipt> receipts) {
            StringBuilder json = new StringBuilder("[");
            for (int index = 0; index < receipts.size(); index++) {
                if (index > 0) json.append(',');
                MediumBoardPhysicalPolicy.OrderingSearchReceipt receipt = receipts.get(index);
                json.append("{\"attempt\":").append(receipt.attempt)
                    .append(",\"startedOrderings\":").append(receipt.startedOrderings)
                    .append(",\"completedOrderings\":").append(receipt.completedOrderings)
                    .append(",\"yieldedOrderings\":").append(receipt.yieldedOrderings)
                    .append(",\"finalizedOrderings\":").append(receipt.finalizedOrderings)
                    .append(",\"selectedOrdering\":").append(receipt.selectedOrdering)
                    .append(",\"expansions\":").append(receipt.expansions)
                    .append(",\"guidanceStarted\":").append(receipt.guidanceStarted)
                    .append(",\"comparisonComplete\":").append(receipt.comparisonComplete)
                    .append(",\"termination\":\"").append(jsonFull(receipt.termination)).append("\"}");
            }
            return json.append(']').toString();
        }

        private static String primaryRoutingOrdersJson(
                MediumBoardPhysicalPolicy.Result result) {
            if (result == null || result.getStatistics() == null) return "null";
            MediumBoardPhysicalPolicy.Statistics statistics = result.getStatistics();
            PcbPackingProbeReceipt probe = statistics.packingProbe;
            return Integer.toString(statistics.routingOrders -
                (probe == null ? 0 : probe.orderings));
        }

        private static String primaryRoutingExpansionsJson(
                MediumBoardPhysicalPolicy.Result result) {
            if (result == null || result.getStatistics() == null) return "null";
            MediumBoardPhysicalPolicy.Statistics statistics = result.getStatistics();
            PcbPackingProbeReceipt probe = statistics.packingProbe;
            return Integer.toString(statistics.routingExpansions -
                (probe == null ? 0 : probe.expansions));
        }

        private static String packingProbeJson(MediumBoardPhysicalPolicy.Result result) {
            if (result == null || result.getStatistics() == null ||
                    result.getStatistics().packingProbe == null) return "null";
            PcbPackingProbeReceipt probe = result.getStatistics().packingProbe;
            String canonical = probe.toCanonical();
            return "{\"attempts\":" + probe.attempts + ",\"orderings\":" + probe.orderings +
                ",\"expansions\":" + probe.expansions + ",\"millis\":" + probe.millis +
                ",\"routePolicy\":\"" + jsonFull(probe.routePolicy) +
                "\",\"disposition\":\"" + jsonFull(probe.disposition) +
                "\",\"baselineGeometry\":\"" + jsonFull(probe.baselineGeometry) +
                "\",\"selectedGeometry\":\"" + jsonFull(probe.selectedGeometry) +
                "\",\"metricEvidence\":" + metricEvidenceJson(probe.metricEvidence) +
                ",\"canonicalLength\":" + canonical.length() +
                ",\"canonicalJavaHash\":\"" + Integer.toHexString(canonical.hashCode()) +
                "\",\"canonical\":\"" + jsonFull(canonical) + "\"}";
        }

        private static String metricEvidenceJson(PcbPackingProbeReceipt.MetricEvidence evidence) {
            if (evidence == null) return "null";
            return "{\"state\":\"" + evidence.state.name() + "\",\"baseline\":" +
                packingMetricsJson(evidence.baseline) + ",\"candidate\":" +
                packingMetricsJson(evidence.candidate) + ",\"unavailableReason\":" +
                nullableString(evidence.unavailableReason) + "}";
        }

        private static String packingMetricsJson(PcbPackingMetrics metrics) {
            if (metrics == null) return "null";
            return "{\"area\":" + metrics.area + ",\"copper\":" + metrics.copper +
                ",\"vias\":" + metrics.vias + ",\"protectedVoid\":" + metrics.protectedVoid + "}";
        }

        private static String nullableString(String value) {
            return value == null ? "null" : "\"" + jsonFull(value) + "\"";
        }

        private static String integerVectorJson(Vector<Integer> values) {
            StringBuilder json = new StringBuilder("[");
            for (int index = 0; index < values.size(); index++) {
                if (index != 0) json.append(',');
                json.append(values.get(index).intValue());
            }
            return json.append(']').toString();
        }

        private static String stringVectorJson(Vector<String> values) {
            StringBuilder json = new StringBuilder("[");
            for (int index = 0; index < values.size(); index++) {
                if (index != 0) json.append(',');
                json.append('"').append(jsonFull(values.get(index))).append('"');
            }
            return json.append(']').toString();
        }
    }

    private static final class BranchDiagnosticCase extends PlacementDiagnosticCase {
        final String runId;
        final Vector<PcbLayerRoutingPrototype.BranchSnapshot> branches =
            new Vector<PcbLayerRoutingPrototype.BranchSnapshot>();
        long placementSeed, routingSeed, maximumBranchEvents;
        int advanceCalls, observerChecks;
        String boardIdentityStatus = "NOT_REACHED";
        String solverGraphIdentityStatus = "NOT_REACHED";
        String placementFingerprint;
        String layoutOutputJson = "null";
        RoutingInputMetadata inputMetadata;
        PcbLayerRoutingPrototype.Result routeResult;

        BranchDiagnosticCase(String variant, long seed, String runId) {
            super("AUTHORED_RB100", variant, seed, 4);
            this.runId = runId;
        }

        void captureLayoutOutput() {
            if (routeResult == null || routeResult.layout == null) {
                layoutOutputJson = "{\"layoutReturned\":false,\"zeroOutput\":true,\"fingerprint\":null," +
                    "\"outline\":null,\"rawSegments\":0,\"topTraceCount\":0,\"bottomTraceCount\":0," +
                    "\"totalTraceCount\":0,\"holeCount\":0,\"viaCount\":0," +
                    "\"platedHoleCount\":0,\"nonPlatedHoleCount\":0}";
                return;
            }
            PcbBoardLayout layout = routeResult.layout;
            String fingerprint = layout.geometryFingerprint();
            require(fingerprint != null && fingerprint.length() > 0 &&
                fingerprint.length() <= 1024 * 1024,
                "routed P07 output fingerprint stays within the 1MiB digest limit");
            int top = 0, bottom = 0;
            for (PcbTraceGeometry trace : layout.getTraces()) {
                if (trace.getLayer() == PcbCopperLayer.TOP) top++;
                else if (trace.getLayer() == PcbCopperLayer.BOTTOM) bottom++;
                else throw new AssertionError("P07 output contains an unknown layer");
            }
            int vias = 0, plated = 0, nonPlated = 0;
            Vector<PcbBoardHole> holes = layout.getHoles();
            for (PcbBoardHole hole : holes) {
                if (hole.kind == PcbBoardHole.Kind.VIA) vias++;
                else if (hole.kind == PcbBoardHole.Kind.PLATED) plated++;
                else if (hole.kind == PcbBoardHole.Kind.NON_PLATED) nonPlated++;
                else throw new AssertionError("P07 output contains an unknown hole kind");
            }
            Rectangle outline = layout.getBoardOutline();
            layoutOutputJson = "{\"layoutReturned\":true,\"zeroOutput\":false,\"fingerprint\":" +
                fingerprintJson(fingerprint) + ",\"outline\":{\"x\":" + outline.x +
                ",\"y\":" + outline.y + ",\"width\":" + outline.width +
                ",\"height\":" + outline.height + "},\"rawSegments\":" +
                layout.getRawRoutingSegments() + ",\"topTraceCount\":" + top +
                ",\"bottomTraceCount\":" + bottom + ",\"totalTraceCount\":" +
                layout.getTraces().size() + ",\"holeCount\":" + holes.size() +
                ",\"viaCount\":" + vias + ",\"platedHoleCount\":" + plated +
                ",\"nonPlatedHoleCount\":" + nonPlated + "}";
        }

        void prepareFacts() {
            StringBuilder facts = new StringBuilder("{\"protocolVersion\":2,\"runId\":\"");
            facts.append(jsonFull(runId)).append("\",\"fixture\":\"AUTHORED_RB100\"")
                .append(",\"variant\":\"").append(jsonFull(subject)).append("\"")
                .append(",\"seed\":\"").append(seed).append("\"")
                .append(",\"placementSeed\":\"").append(placementSeed).append("\"")
                .append(",\"routingSeed\":\"").append(routingSeed).append("\"")
                .append(",\"candidate\":4,\"fixedPlacementAttempt\":4")
                .append(",\"layoutVersion\":").append(SeededPcbLayoutGenerator.CURRENT_VERSION)
                .append(",\"dependencyEpoch\":\"")
                .append(jsonFull(GenerationDependencyContext.INTERPRETATION_EPOCH)).append("\"")
                .append(",\"planVersion\":").append(Rb100Plan.PLAN_VERSION)
                .append(",\"modelVersion\":").append(Rb100Plan.MODEL_VERSION)
                .append(",\"physicalIntentVersion\":").append(Rb100Placement.INTENT_VERSION)
                .append(routingInputMetadataFields(inputMetadata))
                .append(",\"components\":").append(components)
                .append(",\"pads\":").append(pads)
                .append(",\"nets\":").append(nets)
                .append(",\"boardIdentityStatus\":\"").append(boardIdentityStatus).append("\"")
                .append(",\"solverGraphIdentityStatus\":\"")
                .append(solverGraphIdentityStatus).append("\"")
                .append(",\"advanceCalls\":").append(advanceCalls)
                .append(",\"observerChecks\":").append(observerChecks)
                .append(",\"limits\":{\"p07Expansions\":")
                .append(PcbLayerRoutingPrototype.MAX_EXPANSIONS)
                .append(",\"p07BranchExpansions\":")
                .append(PcbLayerRoutingPrototype.MAX_BRANCH_EXPANSIONS)
                .append(",\"p07GridCells\":").append(PcbLayerRoutingPrototype.MAX_CELLS)
                .append(",\"p07Orderings\":").append(PcbLayerRoutingPrototype.ORDERINGS)
                .append(",\"p07Vias\":")
                .append(PcbLayerRoutingPrototype.Policy.FULLER_TWO_LAYER.vias)
                .append(",\"operationMs\":").append(nanosToMillis(CASE_LIMIT_NANOS))
                .append(",\"workUnits\":").append(WORK_LIMIT)
                .append(",\"unitMs\":").append(nanosToMillis(UNIT_LIMIT_NANOS))
                .append(",\"cleanupMs\":").append(CLEANUP_LIMIT_MILLIS).append("}")
                .append(",\"placementFingerprint\":")
                .append(placementFingerprint == null ? "null" : fingerprintJson(placementFingerprint))
                .append(",\"branchCount\":").append(branches.size())
                .append(",\"branches\":").append(branchesJson())
                .append(",\"routeResult\":").append(routeResultJson())
                .append(",\"layoutOutput\":").append(layoutOutputJson)
                .append(",\"layoutQualification\":false}");
            reportFactsJson = facts.toString();
            require(reportFactsJson.length() <= 4 * 1024 * 1024,
                "complete bounded branch report fits the 4MiB native receipt limit");
        }

        private String fingerprintJson(String fingerprint) {
            return "{\"length\":" + fingerprint.length() +
                ",\"javaHash\":\"" + Integer.toHexString(fingerprint.hashCode()) +
                "\",\"sha256\":\"" + sha256Hex(fingerprint) + "\"}";
        }

        private String branchesJson() {
            StringBuilder json = new StringBuilder("[");
            for (int index = 0; index < branches.size(); index++) {
                if (index != 0) json.append(',');
                PcbLayerRoutingPrototype.BranchSnapshot branch = branches.get(index);
                json.append("{\"pass\":").append(branch.pass)
                    .append(",\"netId\":\"").append(jsonFull(branch.netId)).append("\"")
                    .append(",\"startPadId\":\"").append(jsonFull(branch.startPadId)).append("\"")
                    .append(",\"rootPadId\":\"").append(jsonFull(branch.rootPadId)).append("\"")
                    .append(",\"startX\":").append(branch.startX)
                    .append(",\"startY\":").append(branch.startY)
                    .append(",\"rootX\":").append(branch.rootX)
                    .append(",\"rootY\":").append(branch.rootY)
                    .append(",\"gridWidth\":").append(branch.gridWidth)
                    .append(",\"gridHeight\":").append(branch.gridHeight)
                    .append(",\"goalsTop\":").append(branch.goalsTop)
                    .append(",\"goalsBottom\":").append(branch.goalsBottom)
                    .append(",\"prefixViaCount\":").append(branch.prefixViaCount)
                    .append(",\"reachedPadCount\":").append(branch.reachedPadCount)
                    .append(",\"initialManhattanDistance\":")
                    .append(branch.initialManhattanDistance)
                    .append(",\"minimumExploredManhattanDistance\":")
                    .append(branch.minimumExploredManhattanDistance)
                    .append(",\"branchExpanded\":").append(branch.branchExpanded)
                    .append(",\"totalExpanded\":").append(branch.totalExpanded)
                    .append(",\"expandedTop\":").append(branch.expandedTop)
                    .append(",\"expandedBottom\":").append(branch.expandedBottom)
                    .append(",\"frontierCount\":").append(branch.frontierCount)
                    .append(",\"bestStateCount\":").append(branch.bestStateCount)
                    .append(",\"terminalReason\":\"")
                    .append(jsonFull(String.valueOf(branch.terminalReason))).append("\"}");
            }
            return json.append(']').toString();
        }

        private String routeResultJson() {
            if (routeResult == null) return "null";
            return "{\"accepted\":" + routeResult.accepted() +
                ",\"outcome\":\"" + jsonFull(routeResult.outcome) +
                "\",\"expansions\":" + routeResult.expansions +
                ",\"orderings\":" + routeResult.orderings +
                ",\"vias\":" + routeResult.vias + ",\"links\":" + routeResult.links + "}";
        }

        @Override String toJson() {
            return "{\"protocolVersion\":2,\"runId\":\"" + jsonFull(runId) +
                "\",\"status\":\"" + status + "\",\"phase\":\"" + jsonFull(phase) +
                "\",\"operationMs\":" + operationMillis + ",\"cleanupMs\":" + cleanupMillis +
                ",\"cleanupStatus\":\"" + (cleanupPassed ? "PASS" : "FAIL") +
                "\",\"workUnits\":" + workUnits + ",\"maxUnitMs\":" + maxUnitMillis +
                ",\"failurePhase\":\"" + jsonFull(failurePhase) + "\",\"failureType\":\"" +
                jsonFull(typedFailure(failure)) + "\",\"failure\":\"" +
                jsonFull(failure == null ? "" : failure.toString()) +
                "\",\"cleanupFailureType\":\"" + jsonFull(typedFailure(cleanupFailure)) +
                "\",\"cleanupFailure\":\"" + jsonFull(cleanupFailure == null ? "" :
                    cleanupFailure.toString()) + "\",\"scope\":\"OBSERVATIONAL_FIXED_P07_BRANCHES_ONLY\"," +
                "\"layoutQualification\":false,\"reportFacts\":" +
                (reportFactsJson == null ? "null" : reportFactsJson) + "}";
        }
    }

    private static String sha256Hex(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(
                value.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(digest.length * 2);
            for (byte item : digest) {
                int unsigned = item & 0xff;
                if (unsigned < 16) hex.append('0');
                hex.append(Integer.toHexString(unsigned));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException impossible) {
            throw new AssertionError("JDK is missing SHA-256", impossible);
        }
    }

    private static final class DiagnosticCounts implements PcbPlacementPlanner.DiagnosticSink {
        final Budget budget;
        final RuntimeException abort;
        final EnumMap<PcbPlacementPlanner.FitOutcome, Long> counts =
            new EnumMap<PcbPlacementPlanner.FitOutcome, Long>(PcbPlacementPlanner.FitOutcome.class);
        int fitCallbacks, snapshotCalls;
        PcbPlacementPlanner.PlacementFailureSnapshot snapshot;
        DiagnosticCounts(Budget budget, RuntimeException abort) {
            this.budget = budget; this.abort = abort;
            for (PcbPlacementPlanner.FitOutcome outcome : PcbPlacementPlanner.FitOutcome.values())
                counts.put(outcome, 0L);
        }
        public void onFitOutcome(PcbPlacementPlanner.FitOutcome outcome) {
            budget.checkpoint();
            if (outcome == null) throw new AssertionError("planner emitted a null fit outcome");
            Long prior = counts.get(outcome);
            counts.put(outcome, Long.valueOf((prior == null ? 0 : prior.longValue()) + 1));
            fitCallbacks++;
            if (abort != null) throw abort;
        }
        public void onPlacementFailure(PcbPlacementPlanner.PlacementFailureSnapshot failure) {
            budget.checkpoint();
            snapshotCalls++;
            if (snapshotCalls != 1 || failure == null)
                throw new AssertionError("planner emitted duplicate or null failure snapshot");
            snapshot = failure;
        }
        long get(PcbPlacementPlanner.FitOutcome outcome) {
            Long value = counts.get(outcome);
            return value == null ? 0 : value.longValue();
        }
        long totalOutcomes() {
            long total = 0;
            for (Long value : counts.values()) total += value.longValue();
            return total;
        }
        EnumMap<PcbPlacementPlanner.FitOutcome, Long> copyCounts() {
            return new EnumMap<PcbPlacementPlanner.FitOutcome, Long>(counts);
        }
    }

    private static final class PlacementFixtureIdentity {
        final TreeMap<String, BoardComponent> components = new TreeMap<String, BoardComponent>();
        final TreeMap<String, String> pads = new TreeMap<String, String>();
        final TreeSet<String> nets = new TreeSet<String>();
        final PcbPlacementConstraints constraints;

        private PlacementFixtureIdentity(PcbPlacementConstraints constraints) {
            this.constraints = constraints;
        }

        static PlacementFixtureIdentity capture(TroubleshootBoard board) {
            PcbPlacementConstraints constraints = board.getPlacementConstraints();
            PlacementFixtureIdentity identity = new PlacementFixtureIdentity(constraints);
            for (String componentId : board.getComponentIds()) {
                BoardComponent component = board.getComponent(componentId);
                require(component != null, "fixture component exists before planner calls: " + componentId);
                identity.components.put(componentId, component);
            }
            for (String padId : board.getPadIds()) {
                BoardPad pad = board.getPad(padId);
                require(pad != null, "fixture pad exists before planner calls: " + padId);
                identity.pads.put(padId, padRecord(pad));
            }
            identity.nets.addAll(board.getNetIds());
            return identity;
        }

        void requireUnchanged(TroubleshootBoard board) {
            require(constraints == board.getPlacementConstraints() &&
                components.keySet().equals(new TreeSet<String>(board.getComponentIds())) &&
                pads.keySet().equals(new TreeSet<String>(board.getPadIds())) &&
                nets.equals(new TreeSet<String>(board.getNetIds())),
                "real planner observation retains fixture component, pad, net and constraint identities");
            for (Map.Entry<String, BoardComponent> entry : components.entrySet())
                require(board.getComponent(entry.getKey()) == entry.getValue(),
                    "planner preserves actual component object identity: " + entry.getKey());
            for (Map.Entry<String, String> entry : pads.entrySet())
                require(entry.getValue().equals(padRecord(board.getPad(entry.getKey()))),
                    "planner preserves actual pad terminal/net mapping: " + entry.getKey());
        }
    }

    private static final class DiagnosticCallbackAbort extends RuntimeException {
        private static final long serialVersionUID = 1L;
        DiagnosticCallbackAbort() { super("intentional local placement diagnostic callback abort"); }
    }

    private static final class BoardIdentity {
        final TreeMap<String, PhysicalPackage> components = new TreeMap<String, PhysicalPackage>();
        final TreeMap<String, String> pads = new TreeMap<String, String>();
        final TreeMap<String, EndpointIdentity> endpoints = new TreeMap<String, EndpointIdentity>();
        final TreeSet<String> nets = new TreeSet<String>();
        private BoardIdentity() { }
        static BoardIdentity capture(Rb100ElectricalPilot.Candidate candidate) {
            Rb100Plan plan = candidate.plan;
            TroubleshootBoard board = candidate.board();
            require(CircuitElm.sim != null && CirSim.theSim == CircuitElm.sim &&
                CircuitElm.sim.elmList != null && CircuitElm.sim.elmList.isEmpty(),
                "native candidate is not installed in any solver graph");
            BoardIdentity identity = new BoardIdentity();
            TreeSet<String> expectedComponents = new TreeSet<String>();
            TreeSet<String> expectedPads = new TreeSet<String>();
            TreeSet<String> expectedNets = new TreeSet<String>();
            for (Rb100Plan.Part part : plan.parts()) {
                BoardComponent component = board.getComponent(part.id);
                require(component != null && component.getPhysicalPackage() == part.physicalPackage,
                    "authored package identity is present before physical declaration: " + part.id);
                expectedComponents.add(part.id);
                identity.components.put(part.id, part.physicalPackage);
                String[] terminals = part.terminalIds();
                String[] nets = part.netIds();
                for (String net : nets) expectedNets.add(net);
                require(terminals.length == nets.length && component.getPadIds().size() == terminals.length,
                    "authored package pads equal its plan terminal count: " + part.id);
                for (int i = 0; i < terminals.length; i++) {
                    String padId = part.id + "." + terminals[i];
                    BoardPad pad = board.getPad(padId);
                    require(pad != null && pad.getComponentId().equals(part.id) &&
                        pad.getTerminalId().equals(terminals[i]) && pad.getNetId().equals(nets[i]),
                        "plan-derived terminal and net identity exists before layout: " + padId);
                    expectedPads.add(padId);
                    identity.pads.put(padId, padRecord(pad));
                    CircuitMeasurementEndpoint endpoint =
                        board.getSimulationBindings().getEndpoint(padId);
                    require(endpoint instanceof CircuitPostMeasurementEndpoint,
                        "actual board terminal has a CircuitJS post endpoint: " + padId);
                    CircuitPostMeasurementEndpoint post =
                        (CircuitPostMeasurementEndpoint)endpoint;
                    require(containsIdentity(candidate.assembly.elements, post.getElement()) &&
                        post.getPostIndex() >= 0 &&
                        post.getPostIndex() < post.getElement().getPostCount() &&
                        post.getElement().getPost(post.getPostIndex()) != null,
                        "actual CircuitJS element/post belongs to the unpublished candidate: " + padId);
                    identity.endpoints.put(padId, new EndpointIdentity(post.getElement(),
                        post.getPostIndex(), pad.getNetId(), pad.getTerminalId()));
                }
            }
            require(expectedComponents.equals(new TreeSet<String>(board.getComponentIds())),
                "plan-derived component identity set equals actual candidate board");
            require(expectedPads.equals(new TreeSet<String>(board.getPadIds())),
                "plan-derived complete pad identity set equals actual candidate board");
            TreeSet<String> actualNets = new TreeSet<String>(board.getNetIds());
            require(expectedNets.equals(actualNets),
                "unique plan-derived net identity set equals actual candidate board");
            identity.nets.addAll(actualNets);
            return identity;
        }
        void requireUnchanged(Rb100ElectricalPilot.Candidate candidate) {
            TroubleshootBoard board = candidate.board();
            require(components.keySet().equals(new TreeSet<String>(board.getComponentIds())) &&
                pads.keySet().equals(new TreeSet<String>(board.getPadIds())) &&
                nets.equals(new TreeSet<String>(board.getNetIds())) &&
                endpoints.keySet().equals(pads.keySet()),
                "component, pad, net and solver-endpoint identity sets remain unchanged through layout");
            for (Map.Entry<String, PhysicalPackage> entry : components.entrySet())
                require(board.getComponent(entry.getKey()).getPhysicalPackage() == entry.getValue(),
                    "exact package object remains unchanged through layout: " + entry.getKey());
            for (String padId : pads.keySet())
                require(pads.get(padId).equals(padRecord(board.getPad(padId))),
                    "terminal and net mapping remains unchanged through layout: " + padId);
            for (String padId : endpoints.keySet()) {
                CircuitMeasurementEndpoint endpoint = board.getSimulationBindings().getEndpoint(padId);
                require(endpoint instanceof CircuitPostMeasurementEndpoint,
                    "CircuitJS endpoint remains a physical post for " + padId);
                CircuitPostMeasurementEndpoint post = (CircuitPostMeasurementEndpoint)endpoint;
                EndpointIdentity expected = endpoints.get(padId);
                require(post.getElement() == expected.element &&
                    post.getPostIndex() == expected.postIndex &&
                    containsIdentity(candidate.assembly.elements, post.getElement()) &&
                    post.getPostIndex() >= 0 && post.getPostIndex() < post.getElement().getPostCount() &&
                    post.getElement().getPost(post.getPostIndex()) != null &&
                    expected.netId.equals(board.getPad(padId).getNetId()) &&
                    expected.terminalId.equals(board.getPad(padId).getTerminalId()),
                    "underlying CircuitJS element/post and terminal/net stay exact for " + padId);
            }
        }
    }

    private static final class EndpointIdentity {
        final CircuitElm element;
        final int postIndex;
        final String netId, terminalId;
        EndpointIdentity(CircuitElm element, int postIndex, String netId, String terminalId) {
            this.element = element; this.postIndex = postIndex;
            this.netId = netId; this.terminalId = terminalId;
        }
    }

    private static final class NativeContext {
        final CirSim priorCircuitSim = CircuitElm.sim;
        final CirSim priorSingleton = CirSim.theSim;
        final SimSnapshot priorCircuitSnapshot = new SimSnapshot(priorCircuitSim);
        final SimSnapshot priorSingletonSnapshot =
            priorSingleton == priorCircuitSim ? null : new SimSnapshot(priorSingleton);
        final ModelDefaults defaults = new ModelDefaults();
        final TestCirSim sim = new TestCirSim();
        final Vector<CircuitElm> rawGraph = sim.elmList;
        void activate() { CircuitElm.sim = sim; CirSim.theSim = sim; }
        void close(Rb100ElectricalPilot.Candidate candidate) {
            boolean exactDisposal = true;
            Throwable failure = null;
            try {
                activate();
                if (candidate != null) {
                    Vector<CircuitElm> owned = new Vector<CircuitElm>(candidate.assembly.elements);
                    IdentityHashMap<CircuitElm, Boolean> unique =
                        new IdentityHashMap<CircuitElm, Boolean>();
                    for (CircuitElm element : owned)
                        if (unique.put(element, Boolean.TRUE) != null) exactDisposal = false;
                    for (CircuitElm element : owned)
                        if (sim.elmList.contains(element))
                            throw new AssertionError("unpublished RB100 candidate entered a solver graph");
                    candidate.dispose();
                    candidate.dispose();
                    for (CircuitElm element : owned)
                        if (sim.deleted.get(element) == null || sim.deleted.get(element).intValue() != 1)
                            exactDisposal = false;
                    if (!candidate.assembly.elements.isEmpty()) exactDisposal = false;
                }
                for (Integer count : sim.deleted.values())
                    if (count.intValue() != 1) exactDisposal = false;
                if (sim.elmList != rawGraph || !rawGraph.isEmpty() || !exactDisposal)
                    throw new AssertionError("private candidate graph was not disposed exactly once");
            } catch (RuntimeException cleanupError) {
                failure = cleanupError;
            } catch (Error cleanupError) {
                failure = cleanupError;
            } finally {
                try {
                    defaults.restore();
                    CircuitElm.sim = priorCircuitSim;
                    CirSim.theSim = priorSingleton;
                    boolean restored = (priorCircuitSnapshot == null || priorCircuitSnapshot.matches()) &&
                        (priorSingletonSnapshot == null || priorSingletonSnapshot.matches()) &&
                        defaults.matchesRestored() && CircuitElm.sim == priorCircuitSim &&
                        CirSim.theSim == priorSingleton;
                    if (!restored)
                        failure = append(failure,
                            new AssertionError("prior native singletons, graph, scopes or defaults changed"));
                } catch (RuntimeException restoreError) {
                    failure = append(failure, restoreError);
                } catch (Error restoreError) {
                    failure = append(failure, restoreError);
                }
            }
            if (failure instanceof RuntimeException) throw (RuntimeException)failure;
            if (failure instanceof Error) throw (Error)failure;
        }
    }

    private static final class SimSnapshot {
        final CirSim sim;
        final Vector<CircuitElm> graph;
        final Vector<CircuitElm> graphElements;
        final Scope[] scopes;
        final Scope[] scopeElements;
        final int scopeCount;
        final int[] scopeColumns;
        final int[] scopeColumnValues;
        SimSnapshot(CirSim sim) {
            this.sim = sim;
            if (sim == null) {
                graph = null; graphElements = null; scopes = null; scopeElements = null;
                scopeCount = 0; scopeColumns = null; scopeColumnValues = null;
            } else {
                graph = sim.elmList;
                graphElements = graph == null ? null : new Vector<CircuitElm>(graph);
                scopes = sim.scopes;
                scopeElements = scopes == null ? null : scopes.clone();
                scopeCount = sim.scopeCount;
                scopeColumns = sim.scopeColCount;
                scopeColumnValues = scopeColumns == null ? null : scopeColumns.clone();
            }
        }
        boolean matches() {
            if (sim == null) return true;
            if (sim.elmList != graph || sim.scopeCount != scopeCount ||
                    sim.scopeColCount != scopeColumns || !Arrays.equals(sim.scopes, scopeElements) ||
                    !Arrays.equals(scopeColumns, scopeColumnValues)) return false;
            if (graph == null) return graphElements == null;
            if (graph.size() != graphElements.size()) return false;
            for (int i = 0; i < graph.size(); i++) if (graph.get(i) != graphElements.get(i)) return false;
            return true;
        }
    }

    private static final class ModelDefaults {
        final String diode = DiodeElm.lastModelName;
        final String zener = ZenerElm.lastZenerModelName;
        final String transistor = TransistorElm.lastModelName;
        final String customLogic = CustomLogicElm.lastModelName;
        final String customComposite = CustomCompositeElm.lastModelName;
        final String led = LEDElm.lastLEDModelName;
        final double mosfetBeta = MosfetElm.lastBeta;
        final int groundSymbol = GroundElm.lastSymbolType;
        void restore() {
            DiodeElm.lastModelName = diode;
            ZenerElm.lastZenerModelName = zener;
            TransistorElm.lastModelName = transistor;
            CustomLogicElm.lastModelName = customLogic;
            CustomCompositeElm.lastModelName = customComposite;
            LEDElm.lastLEDModelName = led;
            MosfetElm.lastBeta = mosfetBeta;
            GroundElm.lastSymbolType = groundSymbol;
        }
        boolean matchesRestored() {
            return same(diode, DiodeElm.lastModelName) && same(zener, ZenerElm.lastZenerModelName) &&
                same(transistor, TransistorElm.lastModelName) &&
                same(customLogic, CustomLogicElm.lastModelName) &&
                same(customComposite, CustomCompositeElm.lastModelName) &&
                same(led, LEDElm.lastLEDModelName) &&
                Double.doubleToLongBits(mosfetBeta) == Double.doubleToLongBits(MosfetElm.lastBeta) &&
                groundSymbol == GroundElm.lastSymbolType;
        }
        private static boolean same(String a, String b) {
            return a == null ? b == null : a.equals(b);
        }
    }

    private static final class TestCirSim extends CirSim {
        final IdentityHashMap<CircuitElm, Integer> deleted =
            new IdentityHashMap<CircuitElm, Integer>();
        TestCirSim() {
            gridSize = 16; gridMask = ~15; gridRound = 7;
            elmList = new Vector<CircuitElm>();
            adjustables = new Vector<Adjustable>();
            undoStack = new Vector<String>(); redoStack = new Vector<String>();
            scopes = new Scope[20]; scopeColCount = new int[20]; scopeCount = 0;
            maxTimeStep = minTimeStep = timeStep = .0001; adjustTimeStep = false;
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
        @Override void refreshBoardModificationControls() { }
        @Override void refreshChallengeInteractionState() { }
        @Override void deleteSliders(CircuitElm element) {
            Integer count = deleted.get(element);
            deleted.put(element, count == null ? 1 : count + 1);
            super.deleteSliders(element);
        }
    }
}
