package com.lushprojects.circuitjs1.client;

import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.UUID;
import java.util.Vector;

/**
 * Six fixed actual RB56 geometry observations across the frozen/current layout
 * epoch boundary. Saved legacy/compact hashes are reported as context only;
 * this contract does not bless a new hash or require the current route to match.
 */
public final class P07GeometryEpochDiagnosticContractTest {
    private static int assertions;
    private static final RootCase[] ROOTS = new RootCase[] {
        new RootCase(-1017L, 40, 96,
            "27f9389815a080fc6d50890f868585b5e0ed7bd14f75644c041dff948d6d1f1a",
            "279e83fea450e4b4be6767a56bf2ba748bba5c6dd7817f60b07f48ff6906c378"),
        new RootCase(-1004L, 44, 104,
            "582064e2986b77ff92348024814f01fc0581c8f2273bd37ca695a26a3008574d",
            "15bb054ccc9a57f1dc111800127901b90d829d36fec5886ea4212e3f38b8b328"),
        new RootCase(-1022L, 60, 143,
            "17b1b0d0452d64332d636c46833b2be44348380303a8b66f539216ecac59f7d4",
            "f42fbd62199a8b254964cd256f7e45ebf4b9b6d06eef3ad89911ed8ecb32f375")
    };
    private static final long[] SAVED_REPRESENTATIVE_SEEDS =
        new long[] {-1017L, -1004L, -1022L, 0L, 17L, 42L};
    private static final long[] SAVED_HOLDOUT_SEEDS = new long[] {1L, 7L, 5L, 14L};
    private static final SavedProfileCohort[] SAVED_PROFILE_COHORTS = new SavedProfileCohort[] {
        new SavedProfileCohort("SAVED_3_4_REP", new int[] {3, 4}, SAVED_REPRESENTATIVE_SEEDS),
        new SavedProfileCohort("SAVED_5_6_REP", new int[] {5, 6}, SAVED_REPRESENTATIVE_SEEDS),
        new SavedProfileCohort("SAVED_7_8_REP", new int[] {7, 8}, SAVED_REPRESENTATIVE_SEEDS),
        new SavedProfileCohort("SAVED_9_10_REP", new int[] {9, 10}, SAVED_REPRESENTATIVE_SEEDS),
        new SavedProfileCohort("SAVED_11_12_REP", new int[] {11, 12}, SAVED_REPRESENTATIVE_SEEDS),
        new SavedProfileCohort("SAVED_10_11_12_HOLDOUT", new int[] {10, 11, 12}, SAVED_HOLDOUT_SEEDS)
    };
    private static final class SavedProfileCohort {
        final String id;
        final int[] versions;
        final long[] seeds;
        SavedProfileCohort(String id, int[] versions, long[] seeds) {
            this.id = id;
            this.versions = versions;
            this.seeds = seeds;
        }
        int caseCount() { return versions.length * seeds.length; }
    }
    private static final class RootCase {
        final long seed;
        final int packages;
        final int pads;
        final String legacyBaselineExpected;
        final String compactBaselineExpected;
        RootCase(long seed, int packages, int pads, String legacy, String compact) {
            this.seed = seed;
            this.packages = packages;
            this.pads = pads;
            legacyBaselineExpected = legacy;
            compactBaselineExpected = compact;
        }
        String baselineExpected(int interpretation) {
            return interpretation == Rb56Placement.LEGACY_VERSION ?
                legacyBaselineExpected : compactBaselineExpected;
        }
    }
    public static void main(String[] args) {
        String savedCohort = System.getenv("TSJ_GEOMETRY_COHORT");
        if (savedCohort != null && savedCohort.length() != 0) {
            runSavedProfileCohort(args, savedCohort);
            return;
        }
        if (args != null && args.length != 0)
            throw new IllegalArgumentException("This diagnostic takes no arguments");
        int layoutVersion = SeededPcbLayoutGenerator.CURRENT_VERSION;
        String dependencyEpoch = GenerationDependencyContext.INTERPRETATION_EPOCH;
        if (!((layoutVersion == 22 &&
                    "tsj-generation-dependencies-v23".equals(dependencyEpoch)) ||
                (layoutVersion == 25 &&
                    "tsj-generation-dependencies-v26".equals(dependencyEpoch))))
            throw new IllegalStateException("Expected the frozen layout22/dependency23 or current " +
                "layout25/dependency26 source pair; found layout=" + layoutVersion +
                " dependency=" + dependencyEpoch);
        String runId = UUID.randomUUID().toString();
        long started = System.nanoTime();
        System.out.println("P07_GEOMETRY_EPOCH_DIAGNOSTIC_FRAME {\"runId\":" + quote(runId) +
            ",\"layoutVersion\":" + layoutVersion +
            ",\"dependencyEpoch\":" + quote(dependencyEpoch) +
            ",\"caseCount\":6,\"scope\":\"OBSERVATIONAL_SIX_BOARD_EPOCH_TRANSITION\"," +
            "\"layoutQualification\":false,\"adopted\":false}");
        int cases = 0;
        for (int interpretation : new int[] {Rb56Placement.LEGACY_VERSION,
                Rb56Placement.COMPACT_VERSION}) {
            for (RootCase root : ROOTS) {
                runCase(runId, layoutVersion, dependencyEpoch, root, interpretation);
                cases++;
            }
        }
        long elapsedMillis = (System.nanoTime() - started) / 1000000L;
        System.out.println("P07_GEOMETRY_EPOCH_DIAGNOSTIC_SUMMARY {\"runId\":" + quote(runId) +
            ",\"cases\":" + cases + ",\"assertions\":" + assertions +
            ",\"nativeDiagnosticElapsedMs\":" + elapsedMillis +
            ",\"timingScope\":\"NATIVE_TEST_ONLY_NOT_PRODUCTION_TIMING\",\"layoutVersion\":" + layoutVersion +
            ",\"dependencyEpoch\":" + quote(dependencyEpoch) +
            ",\"scope\":\"OBSERVATIONAL_SIX_BOARD_EPOCH_TRANSITION\"," +
            "\"layoutQualification\":false,\"adopted\":false}");
        System.out.println("PASS: P07 geometry epoch diagnostic contracts assertions=" +
            assertions + " cases=" + cases + " layout=" + layoutVersion +
            " dependency=" + dependencyEpoch +
            " scope=OBSERVATIONAL_SIX_BOARD_EPOCH_TRANSITION");
    }
    private static void runSavedProfileCohort(String[] args, String selector) {
        if (args != null && args.length != 0)
            throw new IllegalArgumentException("This diagnostic takes no arguments");
        SavedProfileCohort cohort = savedProfileCohort(selector);
        if (cohort.caseCount() != 12)
            throw new IllegalStateException("Saved-profile cohort is not a 12-case finite selection");
        int layoutVersion = SeededPcbLayoutGenerator.CURRENT_VERSION;
        String dependencyEpoch = GenerationDependencyContext.INTERPRETATION_EPOCH;
        if (!((layoutVersion == 22 &&
                    "tsj-generation-dependencies-v23".equals(dependencyEpoch)) ||
                (layoutVersion == 25 &&
                    "tsj-generation-dependencies-v26".equals(dependencyEpoch))))
            throw new IllegalStateException("Expected the frozen layout22/dependency23 or current " +
                "layout25/dependency26 source pair; found layout=" + layoutVersion +
                " dependency=" + dependencyEpoch);
        String runId = UUID.randomUUID().toString();
        long started = System.nanoTime();
        System.out.println("P07_SAVED_PROFILE_EPOCH_FRAME {\"protocol\":\"p07-saved-profile-epoch-v1\"," +
            "\"runId\":" + quote(runId) + ",\"cohortId\":" + quote(cohort.id) +
            ",\"caseCount\":12,\"caseOrdinalBase\":1," +
            "\"caseOrder\":\"PLACEMENT_VERSION_MAJOR_THEN_EXPLICIT_SEED_ORDER\"," +
            "\"placementVersions\":" + integerArray(cohort.versions) +
            ",\"rootSeeds\":" + longStringArray(cohort.seeds) +
            ",\"layoutVersion\":" + layoutVersion +
            ",\"dependencyEpoch\":" + quote(dependencyEpoch) +
            ",\"scope\":\"OBSERVATIONAL_SAVED_PROFILE_EPOCH_TRANSITION\"," +
            "\"layoutQualification\":false,\"adopted\":false,\"adoption\":false," +
            "\"baselineExpected\":\"EXTERNAL_SAVED_PROFILE_CATALOG\"}");
        int cases = 0;
        for (int version : cohort.versions) {
            for (long seed : cohort.seeds) {
                cases++;
                runSavedProfileCase(runId, cohort, cases, layoutVersion,
                    dependencyEpoch, seed, version);
            }
        }
        require(cases == 12, "selected saved-profile cohort emitted exactly twelve ordered cases");
        long elapsedMillis = (System.nanoTime() - started) / 1000000L;
        System.out.println("P07_SAVED_PROFILE_EPOCH_SUMMARY {\"protocol\":\"p07-saved-profile-epoch-v1\"," +
            "\"runId\":" + quote(runId) + ",\"cohortId\":" + quote(cohort.id) +
            ",\"cases\":" + cases + ",\"caseCount\":" + cases +
            ",\"caseOrder\":\"PLACEMENT_VERSION_MAJOR_THEN_EXPLICIT_SEED_ORDER\"," +
            "\"placementVersions\":" + integerArray(cohort.versions) +
            ",\"rootSeeds\":" + longStringArray(cohort.seeds) +
            ",\"assertions\":" + assertions +
            ",\"nativeDiagnosticElapsedMs\":" + elapsedMillis +
            ",\"timingScope\":\"NATIVE_TEST_ONLY_NOT_PRODUCTION_TIMING\"," +
            "\"layoutVersion\":" + layoutVersion +
            ",\"dependencyEpoch\":" + quote(dependencyEpoch) +
            ",\"scope\":\"OBSERVATIONAL_SAVED_PROFILE_EPOCH_TRANSITION\"," +
            "\"layoutQualification\":false,\"adopted\":false,\"adoption\":false," +
            "\"baselineExpected\":\"EXTERNAL_SAVED_PROFILE_CATALOG\"}");
        System.out.println("PASS: P07 geometry epoch diagnostic contracts savedProfileEpoch=true cohortId=" +
            cohort.id + " assertions=" + assertions + " cases=" + cases +
            " layout=" + layoutVersion + " dependency=" + dependencyEpoch +
            " layoutQualification=false adoption=false" +
            " scope=OBSERVATIONAL_SAVED_PROFILE_EPOCH_TRANSITION");
    }
    private static SavedProfileCohort savedProfileCohort(String selector) {
        for (SavedProfileCohort cohort : SAVED_PROFILE_COHORTS)
            if (cohort.id.equals(selector)) return cohort;
        throw new IllegalArgumentException("Unrecognized TSJ_GEOMETRY_COHORT selector");
    }
    private static void runCase(String runId, int layoutVersion, String dependencyEpoch,
            RootCase root, int interpretation) {
        String frameId = UUID.randomUUID().toString();
        long caseStarted = System.nanoTime();
        Rb56Plan plan = Rb56Plan.resolve(root.seed, interpretation);
        require(plan.placementInterpretationVersion == interpretation,
            "resolved plan carries the requested physical interpretation");
        require(plan.physicalPackageCount() == root.packages,
            "fixed root retains its independent package census: " + root.seed);
        TroubleshootBoard board = plan.board();
        TreeSet<String> componentIds = new TreeSet<String>(board.getComponentIds());
        TreeSet<String> padIds = new TreeSet<String>(board.getPadIds());
        require(componentIds.size() == root.packages && padIds.size() == root.pads,
            "actual logical board matches the fixed package/pad counts");
        MediumBoardPhysicalPolicy.Result result = new SeededPcbLayoutGenerator()
            .generateWithPolicyResult(board, plan.layoutSeed, plan.routingSeed);
        require(result != null && result.accepted() && result.getFailure() == null,
            "fixed seed and interpretation return an accepted actual route: " + root.seed +
                "/" + interpretation + (result == null ? "" : " " + result.toCanonical()));
        MediumBoardPhysicalPolicy.Statistics statistics = result.getStatistics();
        require(statistics != null && MediumBoardPhysicalPolicy.P07_FULLER_TWO_LAYER.equals(
                statistics.selectedRoutePolicy),
            "fixed actual candidate retains its selected P07 route policy");
        PcbBoardLayout layout = result.getLayout();
        layout.validateGeometry(board);
        new PcbTwoLayerRules(board, layout).validate(layout);
        PcbTraceClearance.Statistics clearance =
            PcbTraceClearance.validate(layout.getTraces());
        long referenceClearancePairs = P08ScalabilityContractTest
            .referenceClearance(layout.getTraces());
        verifyPhysicalMapping(root, plan, board, layout, componentIds, padIds);
        PcbConductorGraph referenceGraph = P08ReferenceConductorBuilder.capture(board, layout);
        PcbConductorGraph.Snapshot referencePartition = referenceGraph.pristine();
        referencePartition.requirePristineNetConnectivity(board);
        long orderedPadPairs = verifyOrderedPadPartition(board, padIds, referencePartition);
        PcbConductorGraph productionGraph = layout.captureConductorGraph(board);
        require(referenceGraph.toCanonical().equals(productionGraph.toCanonical()),
            "independent P08 conductor graph canonical matches production graph canonical");
        P07LayeredRasterOracle.Metrics layered = P07LayeredRasterOracle.verify(layout, board);
        require(layered.getOrderedPadPairs() == (long)root.pads * root.pads,
            "layered centerline witness checks every ordered pad pair");
        require(layered.getPlatedPadBarrels() > 0,
            "actual package inventory supplies plated pad barrels to the layered witness");
        String geometry = layout.geometryFingerprint();
        String geometryHash = PlayerSessionFingerprint.of(geometry);
        String normalizedPhysicalHash = PcbCompactnessContractTest.physicalGeometrySha256(layout);
        String p08Canonical = referenceGraph.toCanonical();
        String placementCanonical = layout.componentGeometryFingerprint();
        String routeCanonical = layout.traceGeometryFingerprint();
        String json = observationJson(runId, frameId, layoutVersion, dependencyEpoch,
            root, interpretation, plan, board, layout, result, statistics, clearance,
            referenceClearancePairs, p08Canonical, orderedPadPairs,
            layered, geometry, geometryHash, normalizedPhysicalHash, placementCanonical,
            routeCanonical, System.nanoTime() - caseStarted);
        System.out.println("P07_GEOMETRY_EPOCH_CASE " + json);
    }
    private static void runSavedProfileCase(String runId, SavedProfileCohort cohort,
            int caseOrdinal, int layoutVersion, String dependencyEpoch, long seed,
            int interpretation) {
        String frameId = UUID.randomUUID().toString();
        long caseStarted = System.nanoTime();
        Rb56Plan plan = Rb56Plan.resolve(seed, interpretation);
        require(plan.placementInterpretationVersion == interpretation,
            "saved-profile plan carries its selected placement interpretation");
        TroubleshootBoard board = plan.board();
        int expectedPackages = plan.physicalPackageCount();
        int expectedPads = 0;
        Vector<Rb56Plan.Part> declaredParts = plan.parts();
        require(expectedPackages == declaredParts.size(),
            "saved-profile package census comes from the declared plan inventory");
        for (Rb56Plan.Part part : declaredParts) expectedPads += part.terminalIds().length;
        TreeSet<String> componentIds = new TreeSet<String>(board.getComponentIds());
        TreeSet<String> padIds = new TreeSet<String>(board.getPadIds());
        require(componentIds.size() == expectedPackages && padIds.size() == expectedPads,
            "declared board inventory matches plan-derived package and terminal census");
        require(board.getPlacementConstraints().requiresTwoLayerRouting,
            "saved-profile interpretation retains its actual two-layer routing contract");
        MediumBoardPhysicalPolicy.Result result = new SeededPcbLayoutGenerator()
            .generateWithPolicyResult(board, plan.layoutSeed, plan.routingSeed);
        require(result != null && result.accepted() && result.getFailure() == null,
            "saved-profile seed and interpretation return an accepted actual route: " + seed +
                "/" + interpretation + (result == null ? "" : " " + result.toCanonical()));
        MediumBoardPhysicalPolicy.Statistics statistics = result.getStatistics();
        require(statistics != null && MediumBoardPhysicalPolicy.P07_FULLER_TWO_LAYER.equals(
                statistics.selectedRoutePolicy),
            "saved-profile candidate retains its selected P07 route policy");
        PcbBoardLayout layout = result.getLayout();
        layout.validateGeometry(board);
        new PcbTwoLayerRules(board, layout).validate(layout);
        Integer largestProtectedEmptyRectangleArea = null;
        String whitespaceMetricBasis = "NOT_CLAIMED_NO_SAVED_V3_WHITESPACE_ORACLE";
        if (interpretation >= 4) {
            largestProtectedEmptyRectangleArea = Integer.valueOf(
                PcbFunctionalGroupingContractTest.reportWhitespace(
                    board, layout, seed, interpretation));
            whitespaceMetricBasis = "EXISTING_TEST_DRAWING_SPACE_SAMPLING_10_UNIT";
        }
        TreeMap<String, Integer> traceWidthCensus = verifySavedTraceWidths(
            board, layout, interpretation);
        require(PcbTraceRules.MIN_VISIBLE_CLEARANCE == 6,
            "saved-profile width-aware edge clearance remains six drawing units");
        PcbTraceClearance.Statistics clearance =
            PcbTraceClearance.validate(layout.getTraces());
        long referenceClearancePairs = P08ScalabilityContractTest
            .referenceClearance(layout.getTraces());
        verifyPhysicalMapping(plan, board, layout, componentIds, padIds,
            expectedPackages, expectedPads);
        PcbConductorGraph referenceGraph = P08ReferenceConductorBuilder.capture(board, layout);
        PcbConductorGraph.Snapshot referencePartition = referenceGraph.pristine();
        referencePartition.requirePristineNetConnectivity(board);
        long orderedPadPairs = verifyOrderedPadPartition(board, padIds, referencePartition);
        PcbConductorGraph productionGraph = layout.captureConductorGraph(board);
        require(referenceGraph.toCanonical().equals(productionGraph.toCanonical()),
            "saved-profile independent P08 graph matches production graph canonical");
        P07LayeredRasterOracle.Metrics layered = P07LayeredRasterOracle.verify(layout, board);
        require(layered.getOrderedPadPairs() == (long)expectedPads * expectedPads,
            "saved-profile layered centerline witness checks every ordered pad pair");
        require(layered.getPlatedPadBarrels() > 0,
            "saved-profile package inventory supplies plated pad barrels to the layered witness");
        String geometry = layout.geometryFingerprint();
        String geometryHash = PlayerSessionFingerprint.of(geometry);
        String normalizedPhysicalHash = PcbCompactnessContractTest.physicalGeometrySha256(layout);
        String p08Canonical = referenceGraph.toCanonical();
        String placementCanonical = layout.componentGeometryFingerprint();
        String routeCanonical = layout.traceGeometryFingerprint();
        TreeMap<String, String> netRoles = declaredNetRoutingRoles(board);
        String json = savedProfileObservationJson(runId, frameId, cohort, caseOrdinal,
            layoutVersion, dependencyEpoch, seed, interpretation, plan, board, layout,
            result, statistics, clearance, referenceClearancePairs, p08Canonical,
            orderedPadPairs, layered, geometry, geometryHash, normalizedPhysicalHash,
            placementCanonical, routeCanonical, expectedPackages, expectedPads,
            traceWidthCensus, netRoles, largestProtectedEmptyRectangleArea,
            whitespaceMetricBasis, System.nanoTime() - caseStarted);
        System.out.println("P07_SAVED_PROFILE_EPOCH_CASE " + json);
    }
    private static void verifyPhysicalMapping(RootCase root, Rb56Plan plan,
            TroubleshootBoard board, PcbBoardLayout layout, TreeSet<String> componentIds,
            TreeSet<String> padIds) {
        verifyPhysicalMapping(plan, board, layout, componentIds, padIds,
            root.packages, root.pads);
    }
    private static void verifyPhysicalMapping(Rb56Plan plan, TroubleshootBoard board,
            PcbBoardLayout layout, TreeSet<String> componentIds, TreeSet<String> padIds,
            int expectedPackages, int expectedPads) {
        TreeSet<String> planComponents = new TreeSet<String>();
        TreeSet<String> planPads = new TreeSet<String>();
        TreeSet<String> placedComponentIds = new TreeSet<String>();
        TreeSet<String> placedPadIds = new TreeSet<String>();
        for (PcbComponentPlacement placement : layout.getComponents()) {
            String id = placement.getComponentId();
            require(id != null && placedComponentIds.add(id),
                "physical component placement ID is unique: " + id);
            BoardComponent declared = board.getComponent(id);
            require(declared != null && declared.getPhysicalPackage() ==
                    placement.getPhysicalPackage(),
                "physical placement retains declared package object: " + id);
            require(componentIds.contains(id), "physical placement ID is declared: " + id);
        }
        require(layout.getComponents().size() == expectedPackages &&
                placedComponentIds.equals(componentIds),
            "physical component placement is an exact declaration bijection");
        for (PcbPadPlacement placement : layout.getPads()) {
            String id = placement.getPadId();
            require(id != null && placedPadIds.add(id),
                "physical pad placement ID is unique: " + id);
            BoardPad declared = board.getPad(id);
            require(declared != null && padIds.contains(id),
                "physical pad placement is declared: " + id);
        }
        require(layout.getPads().size() == expectedPads && placedPadIds.equals(padIds),
            "physical pad placement is an exact declaration bijection");
        for (Rb56Plan.Part part : plan.parts()) {
            require(planComponents.add(part.id), "plan component ID is unique: " + part.id);
            String[] terminals = part.terminalIds();
            String[] nets = part.netIds();
            require(terminals.length == nets.length &&
                part.physicalPackage.getTerminalCount() == terminals.length,
                "plan terminal/net/package declarations align: " + part.id);
            BoardComponent declaredComponent = board.getComponent(part.id);
            require(declaredComponent != null &&
                    declaredComponent.getPhysicalPackage() == part.physicalPackage,
                "logical board retains plan-owned package identity: " + part.id);
            PcbComponentPlacement component = layout.getComponent(part.id);
            require(component != null && component.getPhysicalPackage() == part.physicalPackage,
                "placed component resolves to the plan physical package: " + part.id);
            for (int index = 0; index < terminals.length; index++) {
                String id = part.id + "." + terminals[index];
                require(planPads.add(id), "plan terminal ID is unique: " + id);
                BoardPad declared = board.getPad(id);
                PcbPadPlacement pad = layout.getPad(id);
                require(pad != null && declared != null &&
                    declared.getComponentId().equals(part.id) &&
                    declared.getTerminalId().equals(terminals[index]) &&
                    declared.getNetId().equals(nets[index]),
                    "placed terminal retains its declared logical net: " + id);
                PhysicalPackageGeometry.Terminal terminal =
                    part.physicalPackage.getGeometry().getTerminal(index);
                require(terminal != null && terminal.getTerminalId().equals(terminals[index]) &&
                    terminal.getAttachment() == pad.getAttachment(),
                    "placed terminal retains physical package endpoint identity: " + id);
            }
        }
        require(planComponents.equals(componentIds) && planPads.equals(padIds),
            "plan declarations and physical mapping match exact logical IDs");
    }
    private static TreeMap<String, Integer> verifySavedTraceWidths(
            TroubleshootBoard board, PcbBoardLayout layout, int interpretation) {
        boolean roleWidths = interpretation == Rb56Placement.ROLE_WIDTHS_VERSION12;
        String expectedProfile = roleWidths ? "ROLE_TRACE@1" : "UNIFORM_TRACE@1";
        require(expectedProfile.equals(
                board.getPlacementConstraints().getTraceWidthProfile().getIdentity()),
            "saved-profile interpretation selects its expected trace width profile");
        TreeMap<String, Integer> widths = new TreeMap<String, Integer>();
        for (PcbTraceGeometry trace : layout.getTraces()) {
            BoardNet net = board.getNet(trace.getNetId());
            require(net != null, "saved-profile trace maps to a declared logical net");
            require(net.getRoutingRole() != null,
                "saved-profile logical net has an explicit routing role");
            int expected = savedReferenceWidth(interpretation, net.getRoutingRole());
            require(trace.getWidth() == expected,
                "saved-profile trace width matches the independent literal role rule: " +
                    trace.getNetId() + "/" + net.getRoutingRole());
            increment(widths, net.getRoutingRole().name() + "@" + expected);
        }
        require(!layout.getTraces().isEmpty(), "saved-profile actual route contains copper traces");
        if (roleWidths) {
            require(widths.containsKey("HIGH_CURRENT@13") && widths.containsKey("SUPPLY@11"),
                "role-width saved profile exercises literal high-current and supply widths");
        }
        return widths;
    }
    private static int savedReferenceWidth(int interpretation, BoardNet.RoutingRole role) {
        if (interpretation != Rb56Placement.ROLE_WIDTHS_VERSION12) return 9;
        if (role == BoardNet.RoutingRole.HIGH_CURRENT) return 13;
        if (role == BoardNet.RoutingRole.SUPPLY || role == BoardNet.RoutingRole.RETURN) return 11;
        if (role == BoardNet.RoutingRole.CONTROL || role == BoardNet.RoutingRole.SIGNAL) return 9;
        throw new IllegalArgumentException("Saved-profile reference lacks a routing role");
    }
    private static TreeMap<String, String> declaredNetRoutingRoles(TroubleshootBoard board) {
        TreeMap<String, String> result = new TreeMap<String, String>();
        for (String netId : board.getNetIds()) {
            BoardNet net = board.getNet(netId);
            require(net != null, "saved-profile declared net has a routing-role owner");
            require(net.getRoutingRole() != null,
                "saved-profile declared net has an explicit routing role");
            result.put(netId, net.getRoutingRole().name());
        }
        return result;
    }
    private static long verifyOrderedPadPartition(TroubleshootBoard board,
            TreeSet<String> padIds, PcbConductorGraph.Snapshot snapshot) {
        long pairs = 0;
        for (String firstId : padIds) {
            BoardPad first = board.getPad(firstId);
            for (String secondId : padIds) {
                BoardPad second = board.getPad(secondId);
                boolean sameDeclaredNet = first.getNetId().equals(second.getNetId());
                require(snapshot.padsConnected(firstId, secondId) == sameDeclaredNet,
                    "independent P08 pad partition matches declared net equality: " +
                        firstId + " -> " + secondId);
                pairs++;
            }
        }
        require(pairs == (long)padIds.size() * padIds.size(),
            "P08 partition census includes every ordered pair and self-pair");
        return pairs;
    }
    private static String observationJson(String runId, String frameId, int layoutVersion,
            String dependencyEpoch, RootCase root, int interpretation, Rb56Plan plan,
            TroubleshootBoard board, PcbBoardLayout layout,
            MediumBoardPhysicalPolicy.Result result, MediumBoardPhysicalPolicy.Statistics stats,
            PcbTraceClearance.Statistics clearance, long referenceClearancePairs,
            String p08Canonical, long orderedPadPairs,
            P07LayeredRasterOracle.Metrics layered, String geometry, String geometryHash,
            String normalizedPhysicalHash, String placementCanonical, String routeCanonical,
            long elapsedNanos) {
        Rectangle outline = layout.getBoardOutline();
        Rectangle tray = layout.getPartsTray();
        TreeMap<String, Integer> packageCensus = new TreeMap<String, Integer>();
        TreeMap<String, Integer> tracesByLayer = new TreeMap<String, Integer>();
        TreeMap<String, Integer> holesByKind = new TreeMap<String, Integer>();
        int segments = 0;
        for (PcbComponentPlacement placement : layout.getComponents())
            increment(packageCensus, placement.getPhysicalPackage().getId());
        for (PcbTraceGeometry trace : layout.getTraces()) {
            increment(tracesByLayer, trace.getLayer().name());
            segments += trace.getXPoints().length - 1;
        }
        int vias = 0, platedHoles = 0, nonPlatedHoles = 0;
        for (PcbBoardHole hole : layout.getHoles()) {
            increment(holesByKind, hole.kind.name());
            if (hole.kind == PcbBoardHole.Kind.VIA) vias++;
            else if (hole.kind == PcbBoardHole.Kind.PLATED) platedHoles++;
            else nonPlatedHoles++;
        }
        StringBuilder out = new StringBuilder(Math.max(32768, geometry.length() * 2));
        out.append('{');
        field(out, "runId", quote(runId)); field(out, "frameId", quote(frameId));
        field(out, "status", quote("PASS_OBSERVATION_ONLY")); field(out, "scope", quote("OBSERVATIONAL_SIX_BOARD_EPOCH_TRANSITION"));
        field(out, "layoutQualification", "false"); field(out, "adopted", "false");
        field(out, "rootSeed", quote(Long.toString(root.seed))); field(out, "placementInterpretationVersion", Integer.toString(interpretation));
        field(out, "placementInterpretation", quote(interpretation ==
            Rb56Placement.LEGACY_VERSION ? "LEGACY1" : "COMPACT2"));
        field(out, "placementSeed", quote(Long.toString(plan.layoutSeed))); field(out, "routingSeed", quote(Long.toString(plan.routingSeed)));
        field(out, "topology", quote(plan.topology()));
        field(out, "layoutVersion", Integer.toString(layoutVersion)); field(out, "dependencyEpoch", quote(dependencyEpoch));
        field(out, "planVersion", Integer.toString(Rb56Plan.PLAN_VERSION)); field(out, "modelDumpEpoch", quote(GenerationDependencyContext.CIRCUIT_DUMP_EPOCH));
        field(out, "expectedPackages", Integer.toString(root.packages)); field(out, "expectedPads", Integer.toString(root.pads));
        field(out, "declaredNets", sortedStringArray(board.getNetIds())); field(out, "packageCensus", integerMap(packageCensus));
        field(out, "logicalInventoryCanonical", quote(plan.canonical()));
        field(out, "layoutWidth", Integer.toString(layout.getWidth())); field(out, "layoutHeight", Integer.toString(layout.getHeight()));
        field(out, "routingLayer", quote(board.getPlacementConstraints().routingLayer.name()));
        field(out, "requiresTwoLayerRouting", Boolean.toString(board.getPlacementConstraints().requiresTwoLayerRouting));
        field(out, "physicalPolicyId", quote(board.getPlacementConstraints().physicalPolicyId)); field(out, "physicalPolicyVersion", Integer.toString(board.getPlacementConstraints().physicalPolicyVersion));
        field(out, "placementDensityVersion", Integer.toString(board.getPlacementConstraints().placementDensityVersion)); field(out, "placementPackingVersion", Integer.toString(board.getPlacementConstraints().placementPackingVersion));
        field(out, "traceWidthProfile", quote(board.getPlacementConstraints().getTraceWidthProfile().getIdentity()));
        field(out, "boardOutline", rectangle(outline)); field(out, "partsTray", rectangle(tray));
        field(out, "boardArea", Long.toString((long)outline.width * outline.height)); field(out, "boardUtilization", finiteNumber(layout.getBoardUtilization()));
        field(out, "selectedPlacementAttempt", Integer.toString(stats.selectedPlacementAttempt)); field(out, "selectedRoutePolicy", quote(stats.selectedRoutePolicy));
        field(out, "policyStatisticsCanonical", quote(stats.toCanonical()));
        field(out, "placementCanonical", quote(placementCanonical));
        field(out, "layoutGeometryFingerprint", quote(geometry)); field(out, "layoutGeometrySha256", quote(geometryHash));
        field(out, "normalizedPhysicalSha256", quote(normalizedPhysicalHash)); field(out, "baselineExpected", quote(root.baselineExpected(interpretation)));
        field(out, "traceGeometryFingerprint", quote(routeCanonical)); field(out, "p08Canonical", quote(p08Canonical));
        field(out, "p08CanonicalSha256", quote(PlayerSessionFingerprint.of(p08Canonical)));
        field(out, "p08SourceSegmentCount", Long.toString(P08ReferenceConductorBuilder.sourceSegmentCount)); field(out, "p08SourceLandCount", Long.toString(P08ReferenceConductorBuilder.sourceLandCount));
        field(out, "p08SegmentPairs", Long.toString(P08ReferenceConductorBuilder.segmentPairs)); field(out, "p08SegmentLandPairs", Long.toString(P08ReferenceConductorBuilder.segmentLandPairs));
        field(out, "p08LandPairs", Long.toString(P08ReferenceConductorBuilder.landPairs)); field(out, "p08OrderedPadPairs", Long.toString(orderedPadPairs));
        field(out, "layeredCenterline", quote("PASS"));
        field(out, "layeredRasterPoints", Long.toString(layered.getRasterPointCount())); field(out, "layeredOrderedPadPairs", Long.toString(layered.getOrderedPadPairs()));
        field(out, "layeredTraceUnitSteps", Long.toString(layered.getTraceUnitSteps())); field(out, "layeredPlatedPadBarrels", Long.toString(layered.getPlatedPadBarrels()));
        field(out, "layeredViaBarrels", Long.toString(layered.getViaBarrels())); field(out, "layeredPlatedHoleBarrels", Long.toString(layered.getPlatedHoleBarrels()));
        field(out, "layeredPackageInternalLinks", Long.toString(layered.getPackageInternalLinks())); field(out, "routeAccepted", Boolean.toString(result.accepted()));
        field(out, "tracePolylineCount", Integer.toString(layout.getTraces().size())); field(out, "traceSegmentCount", Integer.toString(segments));
        field(out, "traceCountsByLayer", integerMap(tracesByLayer));
        field(out, "viaCount", Integer.toString(vias)); field(out, "platedHoleCount", Integer.toString(platedHoles));
        field(out, "nonPlatedHoleCount", Integer.toString(nonPlatedHoles)); field(out, "holeCountsByKind", integerMap(holesByKind));
        field(out, "productionClearanceExactPairs", Long.toString(clearance.exactPairs)); field(out, "productionClearancePotentialPairs", Long.toString(clearance.potentialPairs));
        field(out, "referenceClearancePairs", Long.toString(referenceClearancePairs)); field(out, "clearanceMinimumCenterlineRule", Integer.toString(PcbTraceRules.MIN_CENTERLINE_CLEARANCE));
        field(out, "layoutGeometryValidation", quote("PASS")); field(out, "twoLayerRulesValidation", quote("PASS"));
        field(out, "productionFiniteWidthClearance", quote("PASS")); field(out, "independentP08Partition", quote("PASS"));
        field(out, "normalAdmission", quote("NOT CLAIMED")); field(out, "nativePrivacy", quote("NOT CLAIMED"));
        field(out, "nativeDiagnosticElapsedMs", Long.toString(elapsedNanos / 1000000L)); field(out, "timingScope", quote("NATIVE_TEST_ONLY_NOT_PRODUCTION_TIMING"));
        appendComponentMappings(out, plan, board, layout); appendHoleFingerprints(out, layout);
        return finishObject(out);
    }

    private static String savedProfileObservationJson(String runId, String frameId,
            SavedProfileCohort cohort, int caseOrdinal, int layoutVersion,
            String dependencyEpoch, long seed, int interpretation, Rb56Plan plan,
            TroubleshootBoard board, PcbBoardLayout layout,
            MediumBoardPhysicalPolicy.Result result, MediumBoardPhysicalPolicy.Statistics stats,
            PcbTraceClearance.Statistics clearance, long referenceClearancePairs,
            String p08Canonical, long orderedPadPairs,
            P07LayeredRasterOracle.Metrics layered, String geometry, String geometryHash,
            String normalizedPhysicalHash, String placementCanonical, String routeCanonical,
            int expectedPackages, int expectedPads, TreeMap<String, Integer> traceWidthCensus,
            TreeMap<String, String> netRoles, Integer largestProtectedEmptyRectangleArea,
            String whitespaceMetricBasis, long elapsedNanos) {
        Rectangle outline = layout.getBoardOutline();
        Rectangle tray = layout.getPartsTray();
        TreeMap<String, Integer> packageCensus = new TreeMap<String, Integer>();
        TreeMap<String, Integer> tracesByLayer = new TreeMap<String, Integer>();
        TreeMap<String, Integer> holesByKind = new TreeMap<String, Integer>();
        int segments = 0;
        for (PcbComponentPlacement placement : layout.getComponents())
            increment(packageCensus, placement.getPhysicalPackage().getId());
        for (PcbTraceGeometry trace : layout.getTraces()) {
            increment(tracesByLayer, trace.getLayer().name());
            segments += trace.getXPoints().length - 1;
        }
        int vias = 0, platedHoles = 0, nonPlatedHoles = 0;
        for (PcbBoardHole hole : layout.getHoles()) {
            increment(holesByKind, hole.kind.name());
            if (hole.kind == PcbBoardHole.Kind.VIA) vias++;
            else if (hole.kind == PcbBoardHole.Kind.PLATED) platedHoles++;
            else nonPlatedHoles++;
        }
        StringBuilder out = new StringBuilder(Math.max(32768, geometry.length() * 2));
        out.append('{');
        field(out, "protocol", quote("p07-saved-profile-epoch-v1"));
        field(out, "runId", quote(runId)); field(out, "frameId", quote(frameId));
        field(out, "status", quote("PASS_OBSERVATION_ONLY"));
        field(out, "scope", quote("OBSERVATIONAL_SAVED_PROFILE_EPOCH_TRANSITION"));
        field(out, "layoutQualification", "false"); field(out, "adopted", "false");
        field(out, "adoption", "false");
        field(out, "cohortId", quote(cohort.id)); field(out, "caseOrdinal", Integer.toString(caseOrdinal));
        field(out, "caseCount", "12");
        field(out, "caseOrder", quote("PLACEMENT_VERSION_MAJOR_THEN_EXPLICIT_SEED_ORDER"));
        field(out, "rootSeed", quote(Long.toString(seed)));
        field(out, "placementInterpretationVersion", Integer.toString(interpretation));
        field(out, "placementInterpretation", quote("SAVED_PROFILE_" + interpretation));
        field(out, "censusBasis", quote("DECLARED_PLAN_INVENTORY"));
        field(out, "expectedPackages", Integer.toString(expectedPackages));
        field(out, "expectedPads", Integer.toString(expectedPads));
        field(out, "declaredComponentCount", Integer.toString(board.getComponentIds().size()));
        field(out, "declaredPadCount", Integer.toString(board.getPadIds().size()));
        field(out, "baselineExpected", quote("EXTERNAL_SAVED_PROFILE_CATALOG"));
        field(out, "baselineExpectedBasis", quote("INDEPENDENT_SOURCE_DERIVED_CATALOG_READER"));
        field(out, "placementSeed", quote(Long.toString(plan.layoutSeed)));
        field(out, "routingSeed", quote(Long.toString(plan.routingSeed)));
        field(out, "boardId", quote(board.getId()));
        field(out, "fixtureIdentity", quote(Rb56Plan.FAMILY_ID + "/" + cohort.id + "/" +
            Long.toString(seed) + "/placement-" + interpretation));
        field(out, "topology", quote(plan.topology()));
        field(out, "layoutVersion", Integer.toString(layoutVersion));
        field(out, "dependencyEpoch", quote(dependencyEpoch));
        field(out, "planVersion", Integer.toString(Rb56Plan.PLAN_VERSION));
        field(out, "modelDumpEpoch", quote(GenerationDependencyContext.CIRCUIT_DUMP_EPOCH));
        field(out, "declaredNets", sortedStringArray(board.getNetIds()));
        field(out, "declaredNetRoutingRoles", stringMap(netRoles));
        field(out, "packageCensus", integerMap(packageCensus));
        field(out, "logicalInventoryCanonical", quote(plan.canonical()));
        field(out, "layoutWidth", Integer.toString(layout.getWidth()));
        field(out, "layoutHeight", Integer.toString(layout.getHeight()));
        field(out, "routingLayer", quote(board.getPlacementConstraints().routingLayer.name()));
        field(out, "requiresTwoLayerRouting",
            Boolean.toString(board.getPlacementConstraints().requiresTwoLayerRouting));
        field(out, "physicalPolicyId", quote(board.getPlacementConstraints().physicalPolicyId));
        field(out, "physicalPolicyVersion",
            Integer.toString(board.getPlacementConstraints().physicalPolicyVersion));
        field(out, "placementDensityVersion",
            Integer.toString(board.getPlacementConstraints().placementDensityVersion));
        field(out, "placementPackingVersion",
            Integer.toString(board.getPlacementConstraints().placementPackingVersion));
        field(out, "traceWidthProfile",
            quote(board.getPlacementConstraints().getTraceWidthProfile().getIdentity()));
        field(out, "traceWidthValidation", quote("PASS_INDEPENDENT_LITERAL_ROLE_TABLE"));
        field(out, "traceWidthRule", quote(interpretation == Rb56Placement.ROLE_WIDTHS_VERSION12 ?
            "HIGH_CURRENT=13;SUPPLY=11;RETURN=11;CONTROL=9;SIGNAL=9" : "ALL_ROLES=9"));
        field(out, "traceWidthCensusByRole", integerMap(traceWidthCensus));
        field(out, "clearanceEdgeRuleDrawingUnits", "6");
        field(out, "clearanceRuleBasis", quote("PAIRWISE_ROLE_WIDTH_ENVELOPE_PLUS_6_DRAWING_UNITS"));
        field(out, "boardOutline", rectangle(outline)); field(out, "partsTray", rectangle(tray));
        field(out, "boardArea", Long.toString((long)outline.width * outline.height));
        field(out, "boardUtilization", finiteNumber(layout.getBoardUtilization()));
        field(out, "largestProtectedEmptyRectangleArea",
            largestProtectedEmptyRectangleArea == null ? "null" :
                largestProtectedEmptyRectangleArea.toString());
        field(out, "largestProtectedEmptyRectangleAreaMetricBasis", quote(whitespaceMetricBasis));
        field(out, "selectedPlacementAttempt", Integer.toString(stats.selectedPlacementAttempt));
        field(out, "selectedRoutePolicy", quote(stats.selectedRoutePolicy));
        field(out, "policyStatisticsCanonical", quote(stats.toCanonical()));
        field(out, "placementCanonical", quote(placementCanonical));
        field(out, "placementCanonicalSha256", quote(PlayerSessionFingerprint.of(placementCanonical)));
        field(out, "layoutGeometryFingerprint", quote(geometry));
        field(out, "layoutGeometrySha256", quote(geometryHash));
        field(out, "normalizedPhysicalSha256", quote(normalizedPhysicalHash));
        field(out, "traceGeometryFingerprint", quote(routeCanonical));
        field(out, "traceGeometrySha256", quote(PlayerSessionFingerprint.of(routeCanonical)));
        field(out, "p08Canonical", quote(p08Canonical));
        field(out, "p08CanonicalSha256", quote(PlayerSessionFingerprint.of(p08Canonical)));
        field(out, "p08SourceSegmentCount", Long.toString(P08ReferenceConductorBuilder.sourceSegmentCount));
        field(out, "p08SourceLandCount", Long.toString(P08ReferenceConductorBuilder.sourceLandCount));
        field(out, "p08SegmentPairs", Long.toString(P08ReferenceConductorBuilder.segmentPairs));
        field(out, "p08SegmentLandPairs", Long.toString(P08ReferenceConductorBuilder.segmentLandPairs));
        field(out, "p08LandPairs", Long.toString(P08ReferenceConductorBuilder.landPairs));
        field(out, "p08OrderedPadPairs", Long.toString(orderedPadPairs));
        field(out, "layeredCenterline", quote("PASS"));
        field(out, "layeredRasterPoints", Long.toString(layered.getRasterPointCount()));
        field(out, "layeredOrderedPadPairs", Long.toString(layered.getOrderedPadPairs()));
        field(out, "layeredTraceUnitSteps", Long.toString(layered.getTraceUnitSteps()));
        field(out, "layeredPlatedPadBarrels", Long.toString(layered.getPlatedPadBarrels()));
        field(out, "layeredViaBarrels", Long.toString(layered.getViaBarrels()));
        field(out, "layeredPlatedHoleBarrels", Long.toString(layered.getPlatedHoleBarrels()));
        field(out, "layeredPackageInternalLinks", Long.toString(layered.getPackageInternalLinks()));
        field(out, "routeAccepted", Boolean.toString(result.accepted()));
        field(out, "tracePolylineCount", Integer.toString(layout.getTraces().size()));
        field(out, "traceSegmentCount", Integer.toString(segments));
        field(out, "traceCountsByLayer", integerMap(tracesByLayer));
        field(out, "viaCount", Integer.toString(vias));
        field(out, "platedHoleCount", Integer.toString(platedHoles));
        field(out, "nonPlatedHoleCount", Integer.toString(nonPlatedHoles));
        field(out, "holeCountsByKind", integerMap(holesByKind));
        field(out, "productionClearanceExactPairs", Long.toString(clearance.exactPairs));
        field(out, "productionClearancePotentialPairs", Long.toString(clearance.potentialPairs));
        field(out, "referenceClearancePairs", Long.toString(referenceClearancePairs));
        field(out, "clearanceMinimumCenterlineRule", Integer.toString(PcbTraceRules.MIN_CENTERLINE_CLEARANCE));
        field(out, "layoutGeometryValidation", quote("PASS"));
        field(out, "twoLayerRulesValidation", quote("PASS"));
        field(out, "productionFiniteWidthClearance", quote("PASS"));
        field(out, "independentReferenceClearance", quote("PASS"));
        field(out, "independentP08Partition", quote("PASS"));
        field(out, "normalAdmission", quote("NOT CLAIMED"));
        field(out, "nativePrivacy", quote("NOT CLAIMED"));
        field(out, "nativeDiagnosticElapsedMs", Long.toString(elapsedNanos / 1000000L));
        field(out, "timingScope", quote("NATIVE_TEST_ONLY_NOT_PRODUCTION_TIMING"));
        appendComponentMappings(out, plan, board, layout);
        appendHoleFingerprints(out, layout);
        return finishObject(out);
    }

    private static void appendComponentMappings(StringBuilder out, Rb56Plan plan,
            TroubleshootBoard board, PcbBoardLayout layout) {
        out.append("\"componentMappings\":[");
        boolean firstPart = true;
        for (Rb56Plan.Part part : plan.parts()) {
            if (!firstPart) out.append(',');
            firstPart = false;
            PcbComponentPlacement component = layout.getComponent(part.id);
            PcbPackagePose pose = component.getPose();
            out.append('{');
            field(out, "componentId", quote(part.id));
            field(out, "type", quote(part.type));
            field(out, "kind", quote(part.kind.name()));
            field(out, "physicalPackage", quote(part.physicalPackage.getId()));
            field(out, "value", finiteNumber(part.value));
            field(out, "channel", nullableString(part.channel));
            field(out, "inputId", nullableString(part.inputId));
            field(out, "poseX", Integer.toString(pose.getX()));
            field(out, "poseY", Integer.toString(pose.getY()));
            field(out, "rotation", quote(pose.getRotation().name()));
            field(out, "mountingSide", quote(pose.getMountingSide().name()));
            field(out, "placementFingerprint", quote(component.geometryFingerprint()));
            field(out, "bodyBounds", rectangle(component.getBodyBounds()));
            field(out, "keepOutBounds", rectangle(component.getKeepOut()));
            field(out, "routingCourtyardBounds", rectangle(component.getRoutingCourtyard()));
            field(out, "geometryVariant", quote(component.getGeometryVariantKey()));
            field(out, "geometryTransform", quote(component.getGeometryTransformKey()));
            field(out, "geometryContractVersion",
                Integer.toString(component.getGeometryContractVersionValue()));
            field(out, "boardType", quote(board.getComponent(part.id).getType()));
            out.append("\"terminals\":[");
            String[] terminals = part.terminalIds();
            String[] nets = part.netIds();
            for (int index = 0; index < terminals.length; index++) {
                if (index > 0) out.append(',');
                String padId = part.id + "." + terminals[index];
                BoardPad declared = board.getPad(padId);
                PcbPadPlacement pad = layout.getPad(padId);
                out.append('{');
                field(out, "padId", quote(padId)); field(out, "terminalId", quote(terminals[index]));
                field(out, "netId", quote(nets[index]));
                field(out, "declaredNetId", quote(declared.getNetId()));
                field(out, "x", Integer.toString(pad.getX()));
                field(out, "y", Integer.toString(pad.getY()));
                field(out, "escapeDx", Integer.toString(pad.getEscapeDx()));
                field(out, "escapeDy", Integer.toString(pad.getEscapeDy()));
                field(out, "escapeLength", Integer.toString(pad.getEscapeLength()));
                field(out, "attachment", quote(pad.getAttachment().name()));
                field(out, "mountingSide", quote(pad.getMountingSide().name()));
                field(out, "exposure", quote(pad.getExposure().name()));
                field(out, "padBounds", rectangle(pad.getPadBounds()));
                field(out, "probeBounds", rectangle(pad.getProbeBounds()));
                field(out, "padFingerprint", quote(pad.geometryFingerprint()));
                endObject(out);
            }
            out.append("],");
            endObject(out);
        }
        out.append("],");
    }

    private static void appendHoleFingerprints(StringBuilder out, PcbBoardLayout layout) {
        out.append("\"holes\":[");
        Vector<PcbBoardHole> holes = layout.getHoles();
        for (int index = 0; index < holes.size(); index++) {
            if (index > 0) out.append(',');
            PcbBoardHole hole = holes.get(index);
            out.append('{');
            field(out, "id", quote(hole.id));
            field(out, "kind", quote(hole.kind.name()));
            field(out, "netId", nullableString(hole.netId));
            field(out, "x", Integer.toString(hole.x)); field(out, "y", Integer.toString(hole.y));
            field(out, "drillRadius", Integer.toString(hole.drillRadius));
            field(out, "landRadius", Integer.toString(hole.landRadius));
            field(out, "exposure", quote(hole.exposure.name()));
            field(out, "fingerprint", quote(hole.fingerprint()));
            endObject(out);
        }
        out.append("],");
    }

    private static String rectangle(Rectangle value) {
        return "{\"x\":" + value.x + ",\"y\":" + value.y +
            ",\"width\":" + value.width + ",\"height\":" + value.height + "}";
    }
    private static String stringMap(TreeMap<String, String> values) {
        StringBuilder out = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, String> entry : values.entrySet()) {
            if (!first) out.append(',');
            first = false;
            out.append(quote(entry.getKey())).append(':').append(quote(entry.getValue()));
        }
        return out.append('}').toString();
    }
    private static String integerMap(TreeMap<String, Integer> values) {
        StringBuilder out = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, Integer> entry : values.entrySet()) {
            if (!first) out.append(',');
            first = false;
            out.append(quote(entry.getKey())).append(':').append(entry.getValue().intValue());
        }
        return out.append('}').toString();
    }
    private static String integerArray(int[] values) {
        StringBuilder out = new StringBuilder("[");
        for (int index = 0; index < values.length; index++) {
            if (index > 0) out.append(',');
            out.append(values[index]);
        }
        return out.append(']').toString();
    }
    private static String longStringArray(long[] values) {
        StringBuilder out = new StringBuilder("[");
        for (int index = 0; index < values.length; index++) {
            if (index > 0) out.append(',');
            out.append(quote(Long.toString(values[index])));
        }
        return out.append(']').toString();
    }
    private static String sortedStringArray(Vector<String> values) {
        Vector<String> sorted = new Vector<String>(values);
        Collections.sort(sorted);
        StringBuilder out = new StringBuilder("[");
        for (int index = 0; index < values.size(); index++) {
            if (index > 0) out.append(',');
            out.append(quote(sorted.get(index)));
        }
        return out.append(']').toString();
    }
    private static String finiteNumber(double value) {
        if (Double.isNaN(value) || Double.isInfinite(value))
            throw new IllegalArgumentException("Diagnostic number is not finite");
        return Double.toString(value);
    }
    private static String nullableString(String value) {
        return value == null ? "null" : quote(value);
    }
    private static void increment(TreeMap<String, Integer> counts, String key) {
        Integer prior = counts.get(key);
        counts.put(key, Integer.valueOf(prior == null ? 1 : prior.intValue() + 1));
    }
    private static void field(StringBuilder out, String name, String value) {
        out.append(quote(name)).append(':').append(value).append(',');
    }
    private static String finishObject(StringBuilder out) {
        endObject(out);
        return out.toString();
    }
    private static void endObject(StringBuilder out) {
        if (out.length() > 0 && out.charAt(out.length() - 1) == ',')
            out.setLength(out.length() - 1);
        out.append('}');
    }
    private static String quote(String value) {
        StringBuilder out = new StringBuilder("\"");
        for (int index = 0; index < value.length(); index++) {
            char ch = value.charAt(index);
            if (ch == '\\' || ch == '"') out.append('\\').append(ch);
            else if (ch < 0x20) {
                final char[] hex = "0123456789abcdef".toCharArray();
                out.append("\\u00").append(hex[(ch >>> 4) & 15]).append(hex[ch & 15]);
            } else out.append(ch);
        }
        return out.append('"').toString();
    }
    private static void require(boolean condition, String message) {
        assertions++;
        if (!condition) throw new AssertionError(message);
    }
    private P07GeometryEpochDiagnosticContractTest() { }
}
