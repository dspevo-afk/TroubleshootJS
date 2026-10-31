package com.lushprojects.circuitjs1.client;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.TreeSet;
import java.util.UUID;

/** Focused independent contracts for the P07 relaxed distance field and fixed-graph cloning. */
public final class P07RelaxedDistanceBoundContractTest {
    private static final String PROTOCOL="p07-relaxed-distance-bound-contract-v1";
    private static final String SCOPE="FOCUSED_RELAXED_DISTANCE_BOUND_ALGORITHM_AND_CLONE_CONTRACTS_ONLY";
    private static int checks;
    private static final class CloneFixture {
        final int ordinal, placementVersion, candidateAttempt;
        final long rootSeed, placementSeed, routingSeed;
        final String caseId, topology, expectedInputGeometrySha256;
        CloneFixture(int ordinal,int placementVersion,String caseId,String topology,String expectedInputGeometrySha256) {
            this.ordinal=ordinal;this.placementVersion=placementVersion;this.caseId=caseId;
            this.candidateAttempt=1;this.rootSeed=5L;this.placementSeed=-7630701392004902526L;
            this.routingSeed=-9106650364266936003L;this.topology=topology;
            this.expectedInputGeometrySha256=expectedInputGeometrySha256;
        }
    }
    private static final CloneFixture[] CLONE_FIXTURES=new CloneFixture[] {
        new CloneFixture(1,10,"SEED5_V10_CANDIDATE1",
            "RB56_CH2_SHARED_DIRECT_A_BJT_B_NMOS_S5_true_S12_false_F1_O3_B5_false",
            "1c6136104db257aebfa692c2c6689fe620a658276062d783f36fbef10dd3cc19"),
        new CloneFixture(2,11,"SEED5_V11_CANDIDATE1",
            "RB56_CH2_SHARED_DIRECT_A_BJT_B_NMOS_S5_true_S12_false_F1_O3_B5_false",
            "b21527ea02b45b9fdfb6233fb116d42a32077da84709674efe3b59b36e15c43f")
    };
    private P07RelaxedDistanceBoundContractTest() { }
    public static void main(String[] args) {
        if(args!=null && args.length!=0) throw new IllegalArgumentException("The focused P07 relaxed-bound contract takes no arguments");
        String runId=UUID.randomUUID().toString();int before=checks;
        runRelaxedDistanceBoundContracts();runRefreshNodeContracts();
        for(CloneFixture fixture:CLONE_FIXTURES)runCloneFixture(runId,fixture);
        int assertions=checks-before;
        System.out.println("P07_RELAXED_DISTANCE_BOUND_SUITE {\"protocol\":"+quote(PROTOCOL)+
            ",\"runId\":"+quote(runId)+",\"status\":\"PASS_FOCUSED_CONTRACT_ONLY\""+
            ",\"fieldAlgorithmContracts\":true,\"refreshContracts\":true"+
            ",\"nativeCloneCancellationCanaryCount\":"+CLONE_FIXTURES.length+
            ",\"assertions\":"+assertions+",\"scope\":"+quote(SCOPE)+
            ",\"layoutQualification\":false,\"adoption\":false}");
        System.out.println("PASS: P07 relaxed bound contracts fieldAlgorithm=true refresh=true cloneCanaries="+
            CLONE_FIXTURES.length+" assertions="+assertions+" scope="+SCOPE+
            " layoutQualification=false adoption=false");
    }
    private static void runCloneFixture(final String runId,final CloneFixture selected) {
        Rb56Plan plan=Rb56Plan.resolve(selected.rootSeed,selected.placementVersion);
        require(SeededPcbLayoutGenerator.CURRENT_VERSION==25 &&
            "tsj-generation-dependencies-v26".equals(GenerationDependencyContext.INTERPRETATION_EPOCH),
            "clone canary input is pinned to the current layout-25/dependency-26 epoch");
        require(plan.seed==selected.rootSeed && plan.placementInterpretationVersion==selected.placementVersion &&
            plan.layoutSeed==selected.placementSeed && plan.routingSeed==selected.routingSeed,
            "clone fixture preserves the exact signed root and named generation seeds");
        require(selected.ordinal>=1 && selected.ordinal<=CLONE_FIXTURES.length && selected.candidateAttempt==1 &&
            selected.topology.equals(plan.topology()),"clone fixture identity matches its saved profile and candidate attempt");
        TroubleshootBoard board=plan.board();PcbPlacementConstraints constraints=board.getPlacementConstraints();
        require(constraints!=null && constraints.requiresTwoLayerRouting &&
            MediumBoardPhysicalPolicy.ID.equals(constraints.getPhysicalPolicyId()) &&
            constraints.getPhysicalPolicyVersion()==MediumBoardPhysicalPolicy.VERSION,
            "actual RB56 board retains the declared Medium two-layer policy");
        require("UNIFORM_TRACE@1".equals(constraints.getTraceWidthProfile().getIdentity()) &&
            constraints.placementPackingVersion==1 &&
            constraints.placementDensityVersion==Rb56Placement.densityVersion(selected.placementVersion),
            "clone fixture retains its saved uniform-width, density and packing interpretations");
        PcbPlacementPlanner.Plan plannerPlan=new PcbPlacementPlanner(StandardPcbFootprintProviders.createRegistry()).plan(
            board,constraints,plan.layoutSeed,selected.candidateAttempt);
        require(plannerPlan!=null && plannerPlan.candidate==selected.candidateAttempt,
            "default footprint planner returns the exact pinned clone fixture candidate");
        PcbBoardLayout rawPlacement=plannerPlan.materialize();
        validateRawInput(plan,board,plannerPlan,rawPlacement,selected);
        String currentInputGeometrySha256=PlayerSessionFingerprint.of(rawPlacement.geometryFingerprint());
        String historicalLayout24PrefixInputGeometrySha256=
            PcbCompactnessContractTest.layout24PrefixReferenceSha256(rawPlacement);
        require(historicalLayout24PrefixInputGeometrySha256.equals(selected.expectedInputGeometrySha256),
            "current raw clone fixture matches its retained layout-24-prefix projection");
        require(board.getComponentIds().size()==54 && board.getPadIds().size()==131,
            "actual clone canary fixtures retain the 54-package/131-pad logical inventory");
        runRouterCloneCancellation(runId,selected,board,rawPlacement,constraints,
            currentInputGeometrySha256,historicalLayout24PrefixInputGeometrySha256);
    }

    private static final class SyntheticGraph implements PcbLayerRoutingPrototype.FixedGraphEdges {
        final int width, height, cells;
        final boolean[][][] steps;
        final boolean[] vias;
        int ordinaryCalls, viaCalls;
        int malformedStepCalls;
        RuntimeException runtimeFailure;
        Error errorFailure;
        boolean failOnVia;
        SyntheticGraph(int width, int height) {
            this.width = width; this.height = height; cells = width * height;
            steps = new boolean[2][cells][cells]; vias = new boolean[cells];
        }
        void step(int layer, int from, int to) {
            if (layer < 0 || layer > 1 || from < 0 || from >= cells || to < 0 || to >= cells ||
                    Math.abs(from % width - to % width) + Math.abs(from / width - to / width) != 1)
                throw new IllegalArgumentException("Synthetic edge must join neighboring in-grid cells");
            steps[layer][from][to] = true;
        }
        public boolean permitsStep(int layer, int from, int to) {
            ordinaryCalls++;
            if (runtimeFailure != null) throw runtimeFailure;
            if (errorFailure != null) throw errorFailure;
            if (layer < 0 || layer > 1 || from < 0 || from >= cells || to < 0 || to >= cells ||
                    Math.abs(from % width - to % width) + Math.abs(from / width - to / width) != 1) {
                malformedStepCalls++;
                throw new IllegalStateException("Relaxed field queried a non-neighbor directed step");
            }
            return steps[layer][from][to];
        }
        public boolean permitsVia(int cell) {
            viaCalls++;
            if (runtimeFailure != null && failOnVia) throw runtimeFailure;
            if (errorFailure != null && failOnVia) throw errorFailure;
            if (cell < 0 || cell >= cells)
                throw new IllegalStateException("Relaxed field queried an out-of-grid via cell");
            return vias[cell];
        }
    }

    private interface CheckAction { void run(); }

    private static void runRelaxedDistanceBoundContracts() {
        // A directed two-face route with one via checks exact face goals, secondary step cost,
        // and that predecessor queries ask about the original forward edge u -> v.
        SyntheticGraph directed = new SyntheticGraph(3, 2);
        directed.step(0, 0, 1); directed.step(0, 1, 2); directed.step(1, 2, 5);
        directed.vias[2] = true;
        boolean[] directedGoals = new boolean[directed.cells * 2];
        directedGoals[directed.cells + 5] = true;
        PcbLayerRoutingPrototype.RelaxedDistanceBound directedBound =
            verifyBoundAgainstReference(directed, directedGoals, 0);
        require(directedBound.value(0, 0) == 92 && directedBound.value(2, 0) == 72 &&
            directedBound.value(2, 1) == 12 && directedBound.value(5, 0) == -1 &&
            directedBound.value(5, 1) == 0,
            "directed two-face witness uses primary 10, secondary 12, via 60, and exact goal face");
        require(directed.ordinaryCalls == directedBound.getOrdinaryProbes() &&
            directed.viaCalls == directedBound.getViaProbes(),
            "relaxed-bound probe counters match independent callback call counts");

        SyntheticGraph multipleGoals = new SyntheticGraph(3, 1);
        multipleGoals.step(0, 0, 1); multipleGoals.step(0, 1, 2);
        multipleGoals.step(1, 1, 0); multipleGoals.vias[0] = true;
        boolean[] multipleGoalFaces = new boolean[6];
        multipleGoalFaces[2] = true; multipleGoalFaces[3] = true;
        PcbLayerRoutingPrototype.RelaxedDistanceBound multiGoalBound =
            verifyBoundAgainstReference(multipleGoals,multipleGoalFaces,0);
        require(multiGoalBound.value(0,0) == 20 && multiGoalBound.value(1,1) == 12 &&
            multiGoalBound.value(2,0) == 0 && multiGoalBound.value(0,1) == 0,
            "multi-goal relaxation takes the nearest exact cell-face goal from the full goal union");

        // With BOTTOM primary, costs reverse by face while via transitions stay symmetric.
        SyntheticGraph bottomPrimary = new SyntheticGraph(2, 1);
        bottomPrimary.step(1, 0, 1);
        boolean[] bottomGoals = new boolean[4]; bottomGoals[3] = true;
        PcbLayerRoutingPrototype.RelaxedDistanceBound bottomBound =
            verifyBoundAgainstReference(bottomPrimary, bottomGoals, 1);
        require(bottomBound.value(0, 1) == 10 && bottomBound.value(0, 0) == -1,
            "the selected primary copper face receives the ten-unit step cost");

        // The only permitted same-face path is a 24-edge top serpentine; a via pair plus
        // the shorter bottom corridor is the strictly cheaper relaxed route (216 vs. 240).
        SyntheticGraph viaShortcut = new SyntheticGraph(5, 5);
        int[] serpentine = new int[] {
            0,1,2,3,4,9,8,7,6,5,10,11,12,13,14,19,18,17,16,15,20,21,22,23,24
        };
        for (int i = 0; i + 1 < serpentine.length; i++) viaShortcut.step(0, serpentine[i], serpentine[i + 1]);
        int[] bottomCorridor = new int[] {0,1,2,3,4,9,14,19,24};
        for (int i = 0; i + 1 < bottomCorridor.length; i++) viaShortcut.step(1, bottomCorridor[i], bottomCorridor[i + 1]);
        viaShortcut.vias[0] = true; viaShortcut.vias[24] = true;
        boolean[] shortcutGoals = new boolean[50]; shortcutGoals[24] = true;
        PcbLayerRoutingPrototype.RelaxedDistanceBound shortcutBound =
            verifyBoundAgainstReference(viaShortcut, shortcutGoals, 0);
        require(shortcutBound.value(0, 0) == 216 && shortcutBound.value(0, 0) <
            (serpentine.length - 1) * 10,
            "a legal two-via relaxed shortcut beats the longer same-face path without changing route policy");

        // A flattened row-wrap is not a grid edge, and a reverse-only predicate cannot be
        // treated as a forward legal route.
        SyntheticGraph noWrap = new SyntheticGraph(3, 2);
        noWrap.step(0, 2, 5);
        boolean[] noWrapGoals = new boolean[12]; noWrapGoals[3] = true;
        PcbLayerRoutingPrototype.RelaxedDistanceBound noWrapBound =
            verifyBoundAgainstReference(noWrap, noWrapGoals, 0);
        require(noWrapBound.value(2, 0) == -1,
            "row-edge flattened neighbors do not create a wrapping transition");
        require(noWrap.malformedStepCalls == 0,
            "fixed-grid adjacency queries never wrap across a row boundary");
        SyntheticGraph reverseOnly = new SyntheticGraph(2, 1);
        reverseOnly.step(0, 1, 0);
        boolean[] reverseGoals = new boolean[4]; reverseGoals[1] = true;
        PcbLayerRoutingPrototype.RelaxedDistanceBound reverseBound =
            verifyBoundAgainstReference(reverseOnly, reverseGoals, 0);
        require(reverseBound.value(0, 0) == -1,
            "reverse edge permission cannot satisfy a forward directed route query");

        // Interleave two fields to ensure all heap/distance work is instance-local.
        SyntheticGraph firstGraph = new SyntheticGraph(2, 2);
        firstGraph.step(0, 0, 1); firstGraph.step(0, 1, 3);
        boolean[] firstGoals = new boolean[8]; firstGoals[3] = true;
        SyntheticGraph secondGraph = new SyntheticGraph(2, 2);
        secondGraph.step(1, 2, 0);
        boolean[] secondGoals = new boolean[8]; secondGoals[4] = true;
        final PcbLayerRoutingPrototype.RelaxedDistanceBound first =
            new PcbLayerRoutingPrototype.RelaxedDistanceBound(2, 2, firstGoals, 0,
                PcbLayerRoutingPrototype.Policy.FULLER_TWO_LAYER, firstGraph);
        final PcbLayerRoutingPrototype.RelaxedDistanceBound second =
            new PcbLayerRoutingPrototype.RelaxedDistanceBound(2, 2, secondGoals, 1,
                PcbLayerRoutingPrototype.Policy.FULLER_TWO_LAYER, secondGraph);
        expectIllegalState(new CheckAction() { public void run() { first.value(0, 0); } },
            "partial relaxed-distance fields cannot be queried");
        while (!first.isComplete() || !second.isComplete()) {
            if (!first.isComplete()) first.advanceOne();
            if (!second.isComplete()) second.advanceOne();
        }
        require(first.value(0, 0) == 20 && second.value(2, 1) == 10,
            "interleaved fields retain independent directed graph state");
        int firstWork = first.getWorkUnits(), secondWork = second.getWorkUnits();
        require(first.advanceOne() && second.advanceOne() && first.getWorkUnits() == firstWork &&
            second.getWorkUnits() == secondWork,
            "completed field advances are idempotent and consume no additional work");

        expectIllegalArgument(new CheckAction() { public void run() {
            new PcbLayerRoutingPrototype.RelaxedDistanceBound(0, 1, new boolean[2], 0,
                PcbLayerRoutingPrototype.Policy.FULLER_TWO_LAYER, new SyntheticGraph(1, 1));
        }}, "zero-width relaxed graphs are rejected");
        expectIllegalArgument(new CheckAction() { public void run() {
            new PcbLayerRoutingPrototype.RelaxedDistanceBound(1, 1, new boolean[1], 0,
                PcbLayerRoutingPrototype.Policy.FULLER_TWO_LAYER, new SyntheticGraph(1, 1));
        }}, "goal vectors must cover both copper faces");
        expectIllegalArgument(new CheckAction() { public void run() {
            new PcbLayerRoutingPrototype.RelaxedDistanceBound(500001, 1, new boolean[0], 0,
                PcbLayerRoutingPrototype.Policy.FULLER_TWO_LAYER, new SyntheticGraph(1, 1));
        }}, "oversized relaxed grids are rejected before allocation");
        expectIllegalArgument(new CheckAction() { public void run() {
            new PcbLayerRoutingPrototype.RelaxedDistanceBound(1, 1, new boolean[2], 2,
                PcbLayerRoutingPrototype.Policy.FULLER_TWO_LAYER, new SyntheticGraph(1, 1));
        }}, "invalid primary faces are rejected");
        expectIllegalArgument(new CheckAction() { public void run() {
            new PcbLayerRoutingPrototype.RelaxedDistanceBound(1, 1, new boolean[2], 0,
                PcbLayerRoutingPrototype.Policy.ONE_LAYER, new SyntheticGraph(1, 1));
        }}, "the fixed-field helper rejects policies outside its bounded P07 contract");
        expectIllegalArgument(new CheckAction() { public void run() {
            new PcbLayerRoutingPrototype.RelaxedDistanceBound(1, 1, new boolean[2], 0,
                PcbLayerRoutingPrototype.Policy.FULLER_TWO_LAYER, null);
        }}, "missing fixed-graph edge callbacks are rejected");

        // A query failure retires the partially built field and must remain observable on
        // every later advance/query; it must not resume after a half-scanned settled state.
        final RuntimeException querySentinel = new IllegalStateException("synthetic via query failure");
        SyntheticGraph throwingGraph = new SyntheticGraph(1, 1);
        throwingGraph.runtimeFailure = querySentinel; throwingGraph.failOnVia = true;
        boolean[] oneGoal = new boolean[] {true, false};
        final PcbLayerRoutingPrototype.RelaxedDistanceBound failed =
            new PcbLayerRoutingPrototype.RelaxedDistanceBound(1, 1, oneGoal, 0,
                PcbLayerRoutingPrototype.Policy.FULLER_TWO_LAYER, throwingGraph);
        failed.advanceOne(); failed.advanceOne();
        RuntimeException firstFailure = null;
        try { failed.advanceOne(); } catch (RuntimeException expected) { firstFailure = expected; }
        require(firstFailure == querySentinel, "fixed-graph query failures propagate unchanged");
        require(!failed.isComplete() && failed.getWorkUnits() == 3,
            "a callback failure counts its attempted work but cannot expose a completed partial field");
        final RuntimeException latchedFailure = firstFailure;
        int failedViaCalls = throwingGraph.viaCalls;
        int failedWorkUnits = failed.getWorkUnits();
        expectSameRuntimeFailure(new CheckAction() { public void run() { failed.advanceOne(); } },
            latchedFailure, "failed relaxed builds stay retired on advance");
        expectSameRuntimeFailure(new CheckAction() { public void run() { failed.value(0, 0); } },
            latchedFailure, "failed relaxed builds never expose partial field values");
        require(throwingGraph.viaCalls == failedViaCalls && failed.getWorkUnits() == failedWorkUnits &&
            !failed.isComplete(),
            "retry after query failure does not resume the interrupted settled-state scan");
        final Error errorSentinel = new AssertionError("synthetic graph error");
        SyntheticGraph throwingErrorGraph = new SyntheticGraph(1, 1);
        throwingErrorGraph.errorFailure = errorSentinel; throwingErrorGraph.failOnVia = true;
        final PcbLayerRoutingPrototype.RelaxedDistanceBound failedByError =
            new PcbLayerRoutingPrototype.RelaxedDistanceBound(1, 1, oneGoal, 0,
                PcbLayerRoutingPrototype.Policy.FULLER_TWO_LAYER, throwingErrorGraph);
        failedByError.advanceOne(); failedByError.advanceOne();
        Error firstError = null;
        try { failedByError.advanceOne(); } catch (Error expected) { firstError = expected; }
        require(firstError == errorSentinel, "fixed-graph errors propagate unchanged");
        require(!failedByError.isComplete() && failedByError.getWorkUnits() == 3,
            "an Error counts its attempted work but cannot expose a completed partial field");
        final Error latchedError = firstError;
        int failedErrorViaCalls = throwingErrorGraph.viaCalls;
        int failedErrorWorkUnits = failedByError.getWorkUnits();
        expectSameError(new CheckAction() { public void run() { failedByError.advanceOne(); } },
            latchedError, "failed relaxed builds stay retired after Error");
        expectSameError(new CheckAction() { public void run() { failedByError.value(0, 0); } },
            latchedError, "failed relaxed builds do not expose partial values after Error");
        require(throwingErrorGraph.viaCalls == failedErrorViaCalls &&
            failedByError.getWorkUnits() == failedErrorWorkUnits && !failedByError.isComplete(),
            "retry after Error does not resume the interrupted settled-state scan");
    }

    private static PcbLayerRoutingPrototype.RelaxedDistanceBound verifyBoundAgainstReference(
            SyntheticGraph graph, boolean[] goals, int primary) {
        final PcbLayerRoutingPrototype.RelaxedDistanceBound bound =
            new PcbLayerRoutingPrototype.RelaxedDistanceBound(graph.width, graph.height, goals, primary,
                PcbLayerRoutingPrototype.Policy.FULLER_TWO_LAYER, graph);
        expectIllegalState(new CheckAction() { public void run() { bound.value(0, 0); } },
            "a relaxed-distance value is unavailable before field completion");
        int advances = 0;
        while (!bound.advanceOne()) {
            advances++;
            require(bound.getWorkUnits() == advances,
                "each unfinished incremental bound step consumes exactly one declared work unit");
        }
        advances++;
        require(bound.getWorkUnits() == advances && bound.isComplete(),
            "the final incremental step completes the field and is counted once");
        long[][] reference = referenceDistances(graph, goals, primary);
        for (int layer = 0; layer < 2; layer++) for (int cell = 0; cell < graph.cells; cell++) {
            int expected = reference[layer][cell] >= Long.MAX_VALUE / 8 ? -1 : (int) reference[layer][cell];
            require(bound.value(cell, layer) == expected,
                "incremental directed relaxed field equals independent Floyd-Warshall reference");
            if (goals[layer * graph.cells + cell])
                require(bound.value(cell, layer) == 0, "every exact-face goal has zero relaxed distance");
        }
        require(bound.getOrdinaryProbes() == graph.ordinaryCalls && bound.getViaProbes() == graph.viaCalls,
            "preprocessing counters equal independent edge callback observations");
        return bound;
    }

    private static long[][] referenceDistances(SyntheticGraph graph, boolean[] goals, int primary) {
        final long infinity = Long.MAX_VALUE / 4;
        int states = graph.cells * 2;
        long[][] distance = new long[states][states];
        for (int from = 0; from < states; from++) for (int to = 0; to < states; to++)
            distance[from][to] = from == to ? 0L : infinity;
        for (int layer = 0; layer < 2; layer++) for (int from = 0; from < graph.cells; from++)
            for (int to = 0; to < graph.cells; to++) if (graph.steps[layer][from][to])
                distance[layer * graph.cells + from][layer * graph.cells + to] =
                    PcbLayerRoutingPrototype.GRID + (layer == primary ? 0 :
                        PcbLayerRoutingPrototype.Policy.FULLER_TWO_LAYER.secondaryCost);
        for (int cell = 0; cell < graph.cells; cell++) if (graph.vias[cell]) {
            distance[cell][graph.cells + cell] = PcbLayerRoutingPrototype.Policy.FULLER_TWO_LAYER.viaCost;
            distance[graph.cells + cell][cell] = PcbLayerRoutingPrototype.Policy.FULLER_TWO_LAYER.viaCost;
        }
        for (int via = 0; via < states; via++) for (int from = 0; from < states; from++) {
            if (distance[from][via] >= infinity) continue;
            for (int to = 0; to < states; to++) if (distance[via][to] < infinity) {
                long candidate = distance[from][via] + distance[via][to];
                if (candidate < distance[from][to]) distance[from][to] = candidate;
            }
        }
        long[][] result = new long[2][graph.cells];
        for (int layer = 0; layer < 2; layer++) for (int cell = 0; cell < graph.cells; cell++) {
            long best = infinity; int state = layer * graph.cells + cell;
            for (int goal = 0; goal < states; goal++) if (goals[goal] && distance[state][goal] < best)
                best = distance[state][goal];
            result[layer][cell] = best;
        }
        return result;
    }

    private static void expectIllegalArgument(CheckAction action, String message) {
        boolean thrown = false;
        try { action.run(); } catch (IllegalArgumentException expected) { thrown = true; }
        require(thrown, message);
    }

    private static void expectIllegalState(CheckAction action, String message) {
        boolean thrown = false;
        try { action.run(); } catch (IllegalStateException expected) { thrown = true; }
        require(thrown, message);
    }

    private static void expectSameRuntimeFailure(CheckAction action, RuntimeException failure, String message) {
        RuntimeException thrown = null;
        try { action.run(); } catch (RuntimeException expected) { thrown = expected; }
        require(thrown == failure, message);
    }

    private static void expectSameError(CheckAction action, Error failure, String message) {
        Error thrown = null;
        try { action.run(); } catch (Error expected) { thrown = expected; }
        require(thrown == failure, message);
    }

    private static void runRefreshNodeContracts() {
        try {
            String outer = "com.lushprojects.circuitjs1.client.PcbLayerRoutingPrototype";
            Class<?> nodeType = Class.forName(outer + "$Node");
            Class<?> indexType = Class.forName(outer + "$NodeIndex");
            Class<?> queueType = Class.forName(outer + "$NodeQueue");
            Constructor<?> nodeConstructor = nodeType.getDeclaredConstructor(int.class,int.class,int.class,
                int.class,int.class,int.class,int.class,int.class,nodeType);
            Constructor<?> indexConstructor = indexType.getDeclaredConstructor();
            Constructor<?> queueConstructor = queueType.getDeclaredConstructor();
            nodeConstructor.setAccessible(true); indexConstructor.setAccessible(true);
            queueConstructor.setAccessible(true);
            Method put = indexType.getDeclaredMethod("put",int.class,nodeType);
            Method get = indexType.getDeclaredMethod("get",int.class);
            Method indexSize = indexType.getDeclaredMethod("size");
            Method add = queueType.getDeclaredMethod("add",nodeType);
            Method poll = queueType.getDeclaredMethod("poll");
            Method queueSize = queueType.getDeclaredMethod("size");
            Method refresh = PcbLayerRoutingPrototype.class.getDeclaredMethod("refreshNodeForBound",
                nodeType,int.class,indexType,queueType,int.class);
            put.setAccessible(true); get.setAccessible(true); indexSize.setAccessible(true);
            add.setAccessible(true); poll.setAccessible(true); queueSize.setAccessible(true);
            refresh.setAccessible(true);

            final int cells = 8;
            Object firstRoot = nodeConstructor.newInstance(0,0,4,0,0,0,1,cells,null);
            Object firstVia = nodeConstructor.newInstance(5,1,4,1,60,0,2,cells,firstRoot);
            Object selectedHistory = nodeConstructor.newInstance(1,1,1,1,70,0,3,cells,firstVia);
            Object secondRoot = nodeConstructor.newInstance(4,0,4,0,0,0,4,cells,null);
            Object secondVia = nodeConstructor.newInstance(6,1,4,1,60,0,5,cells,secondRoot);
            Object alternateHistory = nodeConstructor.newInstance(5,1,2,1,70,0,6,cells,secondVia);
            Object current = nodeConstructor.newInstance(2,1,1,1,80,30,9,cells,selectedHistory);
            Object sameKeyDifferentHistory = nodeConstructor.newInstance(2,1,1,1,80,30,10,cells,alternateHistory);
            Object competitor = nodeConstructor.newInstance(3,1,2,0,100,0,11,cells,null);
            Object index = indexConstructor.newInstance();
            Object queue = queueConstructor.newInstance();
            int key = nodeInt(nodeType,current,"key");
            put.invoke(index,key,current);
            add.invoke(queue,competitor);
            int beforeSize = ((Integer) indexSize.invoke(index)).intValue();
            require(((Boolean) refresh.invoke(null,current,80,index,queue,cells)).booleanValue(),
                "a strictly stronger admissible bound locally refreshes the exact best node");
            Object replacement = get.invoke(index,key);
            require(replacement != current && ((Integer) indexSize.invoke(index)).intValue() == beforeSize,
                "lazy refresh replaces the cached identity without adding another best-state key");
            require(nodeInt(nodeType,replacement,"key") == key &&
                nodeInt(nodeType,replacement,"cell") == nodeInt(nodeType,current,"cell") &&
                nodeInt(nodeType,replacement,"layer") == nodeInt(nodeType,current,"layer") &&
                nodeInt(nodeType,replacement,"direction") == nodeInt(nodeType,current,"direction") &&
                nodeInt(nodeType,replacement,"transitions") == nodeInt(nodeType,current,"transitions") &&
                nodeInt(nodeType,replacement,"cost") == nodeInt(nodeType,current,"cost") &&
                nodeInt(nodeType,replacement,"heuristic") == 80 &&
                nodeInt(nodeType,replacement,"serial") == nodeInt(nodeType,current,"serial") &&
                nodeField(nodeType,replacement,"previous") == selectedHistory,
                "refresh retains route key, g, heading, via count, serial, and concrete parent history");
            require(((Integer) queueSize.invoke(queue)).intValue() == 2,
                "refresh queues the stronger same-cost replacement while retaining unrelated frontier work");
            require(poll.invoke(queue) == competitor && poll.invoke(queue) == replacement,
                "the refreshed cached priority changes queue ordering without global reheapification");
            require(!((Boolean) refresh.invoke(null,current,100,index,queue,cells)).booleanValue() &&
                !((Boolean) refresh.invoke(null,replacement,80,index,queue,cells)).booleanValue() &&
                !((Boolean) refresh.invoke(null,replacement,79,index,queue,cells)).booleanValue() &&
                !((Boolean) refresh.invoke(null,sameKeyDifferentHistory,100,index,queue,cells)).booleanValue() &&
                ((Integer) queueSize.invoke(queue)).intValue() == 0,
                "stale, equal, lower, and same-key alternate-history labels do not create duplicate refreshes");
            require(get.invoke(index,key) == replacement &&
                nodeField(nodeType,replacement,"previous") != nodeField(nodeType,sameKeyDifferentHistory,"previous"),
                "refresh preserves the selected path while existing same-key via-history aliasing remains scoped");

            Object goal = nodeConstructor.newInstance(7,1,4,0,80,0,12,cells,null);
            int goalKey = nodeInt(nodeType,goal,"key");
            put.invoke(index,goalKey,goal);
            require(!((Boolean) refresh.invoke(null,goal,0,index,queue,cells)).booleanValue() &&
                nodeInt(nodeType,get.invoke(index,goalKey),"heuristic") == 0,
                "exact goal states retain zero heuristic and require no lazy refresh");
        } catch (Exception failure) {
            throw new AssertionError("reflective lazy-bound refresh contract failed", failure);
        }
    }

    private static int nodeInt(Class<?> nodeType, Object node, String name) throws Exception {
        Field field = nodeType.getDeclaredField(name); field.setAccessible(true);
        return field.getInt(node);
    }

    private static Object nodeField(Class<?> nodeType, Object node, String name) throws Exception {
        Field field = nodeType.getDeclaredField(name); field.setAccessible(true);
        return field.get(node);
    }

    private static void runRouterCloneCancellation(final String runId, final CloneFixture selected, TroubleshootBoard board,
            PcbBoardLayout layout, PcbPlacementConstraints constraints, String currentInputGeometrySha256,
            String historicalLayout24PrefixInputGeometrySha256) {
        final boolean[] armed = new boolean[] {false};
        final int[] callbacks = new int[] {0};
        final RuntimeException sentinel = new IllegalStateException("fixed-graph clone cancellation canary");
        SeededPcbLayoutGenerator.AttemptObserver observer = new SeededPcbLayoutGenerator.AttemptObserver() {
            public void check(int attempt) {
                if (!armed[0]) return;
                callbacks[0]++;
                if (attempt != selected.candidateAttempt)
                    throw new IllegalStateException("fixed-graph clone changed the candidate attempt");
                if (callbacks[0] == 6) throw sentinel;
            }
        };
        Rectangle outline = layout.getBoardOutline();
        PcbLayerRoutingPrototype.GridAdmission cloneGrid = PcbLayerRoutingPrototype.gridAdmission(outline);
        require(cloneGrid.eligible && cloneGrid.width < 1024,
            "the fixed clone-cancellation fixture uses a grid narrower than constructor polling intervals");
        PcbNetRouter.Router live = new PcbNetRouter.Router(layout,board,outline,
            selected.candidateAttempt,observer,constraints.routingLayer,0);
        String effectsBefore = live.fixedGraphObservationEffects();
        PcbNetRouter.Router pristine = live.copyForFixedGraphObservation();
        PcbNetRouter.Router query = live.copyForFixedGraphObservation();
        String pristineEffectsBefore = pristine.fixedGraphObservationEffects();
        String queryEffectsBefore = query.fixedGraphObservationEffects();
        require(live.sameFixedGraphObservation(pristine),
            "native fixed-graph observation copy starts with exact route-predicate state");
        require(live.sameFixedGraphObservation(query),
            "a separate query copy starts equal to the unprepared live and pristine routers");
        require(effectsBefore.equals(live.fixedGraphObservationEffects()) &&
            effectsBefore.equals(pristineEffectsBefore) && effectsBefore.equals(queryEffectsBefore),
            "live, pristine, and query Router copies start with equal fixed state and effects");
        try {
            require(routerField(live,"activeNetId") == null && routerInt(live,"activeTraceWidth") == -1,
                "actual raw router begins with its current-net derived cache unprepared");
        } catch (Exception failure) {
            throw new AssertionError("could not inspect fixed-router cache preparation state",failure);
        }
        require(callbacks[0] == 0,
            "the observer remains disarmed through Router construction and the pristine clone");
        require(board.getNetIds() != null && !board.getNetIds().isEmpty(),
            "cache regression fixture has a concrete logical board net");
        String warmNet = board.getNetIds().iterator().next();
        String inputLayoutBeforeWarm = layout.geometryFingerprint();
        String effectsBeforeWarm = live.fixedGraphObservationEffects();
        boolean queryFreeCell = query.freeLayerCell(outline.x + PcbLayerRoutingPrototype.GRID,
            outline.y + PcbLayerRoutingPrototype.GRID,warmNet);
        boolean livePristineStable = live.sameFixedGraphObservation(pristine);
        boolean liveWarmedQueryDiffers = !live.sameFixedGraphObservation(query);
        boolean pristineWarmedQueryDiffers = !pristine.sameFixedGraphObservation(query);
        boolean liveEffectsStable = effectsBeforeWarm.equals(live.fixedGraphObservationEffects());
        boolean pristineEffectsStable = pristineEffectsBefore.equals(pristine.fixedGraphObservationEffects());
        boolean inputLayoutStable = inputLayoutBeforeWarm.equals(layout.geometryFingerprint());
        try {
            require(warmNet.equals(routerField(query,"activeNetId")) &&
                routerInt(query,"activeTraceWidth") > 0 && routerField(live,"activeNetId") == null,
                "cache-warming query changes only its derived current-net cache from unprepared LIVE");
        } catch (Exception failure) {
            throw new AssertionError("could not inspect warmed query cache state",failure);
        }
        require(livePristineStable && liveWarmedQueryDiffers && liveEffectsStable &&
            pristineWarmedQueryDiffers && pristineEffectsStable && inputLayoutStable,
            "query-only cache warming preserves LIVE-to-PRISTINE state, both effects stamps, and layout while LIVE differs from the warmed query");
        armed[0] = true;
        long started = System.nanoTime();
        RuntimeException thrown = null;
        try { live.copyForFixedGraphObservation(); }
        catch (RuntimeException failure) { thrown = failure; }
        finally { armed[0] = false; }
        long elapsed = System.nanoTime() - started;
        require(thrown == sentinel && callbacks[0] == 6,
            "native clone cancellation propagates the exact sentinel after one private-copy chunk");
        require(elapsed > 0L && elapsed <= 5000000000L,
            "native clone cancellation responds within the five-second observation checkpoint bound");
        boolean liveStateUnchanged = live.sameFixedGraphObservation(pristine);
        boolean liveEffectsUnchanged = effectsBefore.equals(live.fixedGraphObservationEffects());
        require(liveStateUnchanged && liveEffectsUnchanged,
            "discarded partial clone leaves live fixed-graph predicates and query effects unchanged");
        System.out.println("P07_LAZY_BOUND_QUERY_CLONE_CANARY {\"protocol\":" + quote(PROTOCOL) +
            ",\"runId\":" + quote(runId) + ",\"caseId\":" + quote(selected.caseId) +
            ",\"profile\":" + quote("SAVED_PROFILE_V" + selected.placementVersion) +
            ",\"layoutVersion\":" + SeededPcbLayoutGenerator.CURRENT_VERSION +
            ",\"dependencyEpoch\":" + quote(GenerationDependencyContext.INTERPRETATION_EPOCH) +
            ",\"currentInputGeometrySha256\":" + quote(currentInputGeometrySha256) +
            ",\"historicalLayout24PrefixInputGeometrySha256\":" +
                quote(historicalLayout24PrefixInputGeometrySha256) +
            ",\"placementInterpretationVersion\":" + selected.placementVersion +
            ",\"candidateAttempt\":" + selected.candidateAttempt +
            ",\"candidate\":" + selected.candidateAttempt +
            ",\"declaredCaseCanaryCount\":1,\"attempt\":" + selected.candidateAttempt +
            ",\"checkpointOrdinal\":6,\"observedCallbacks\":" +
            callbacks[0] + ",\"sentinelPropagated\":true,\"partialCloneDiscarded\":true," +
            "\"throwKind\":\"RuntimeException\",\"expectedThrowableClass\":" +
            quote(sentinel.getClass().getName()) + ",\"sourceCloneStateParity\":true," +
            "\"cacheWarmMethod\":\"freeLayerCell\",\"cacheWarmNetId\":" + quote(warmNet) +
            ",\"cacheWarmFreeCellResult\":" + queryFreeCell + ",\"queryCacheWarmed\":true," +
            "\"queryActiveNetMatchesWarmNet\":true,\"queryActiveTraceWidthPositive\":true," +
            "\"liveActiveNetRemainsNull\":true,\"cacheDifferenceScope\":\"DERIVED_CURRENT_NET_PREPARATION_STATE\"," +
            "\"livePristineStableAfterQueryWarm\":" + livePristineStable +
            ",\"liveWarmedQueryDiffers\":" + liveWarmedQueryDiffers +
            ",\"pristineWarmedQueryDiffers\":" + pristineWarmedQueryDiffers +
            ",\"liveEffectsStableAfterQueryWarm\":" + liveEffectsStable +
            ",\"pristineEffectsStableAfterQueryWarm\":" + pristineEffectsStable +
            ",\"inputLayoutStableAfterQueryWarm\":" + inputLayoutStable + "," +
            "\"liveStateUnchanged\":" + liveStateUnchanged + ",\"liveEffectsUnchanged\":" +
            liveEffectsUnchanged + ",\"elapsedMs\":" + (elapsed / 1000000L) +
            ",\"observerArmScope\":\"AFTER_CONSTRUCTION_AND_PRISTINE_COPY\"," +
            "\"timingScope\":\"NATIVE_CLONE_CHECKPOINT_ONLY_NOT_GWT_DEADLINE_PROOF\"," +
            "\"scope\":" + quote(SCOPE) + ",\"layoutQualification\":false,\"adoption\":false}");
    }

    private static Object routerField(PcbNetRouter.Router router, String name) throws Exception {
        Field field = PcbNetRouter.Router.class.getDeclaredField(name);
        field.setAccessible(true);
        return field.get(router);
    }

    private static int routerInt(PcbNetRouter.Router router, String name) throws Exception {
        Field field = PcbNetRouter.Router.class.getDeclaredField(name);
        field.setAccessible(true);
        return field.getInt(router);
    }

    private static void validateRawInput(Rb56Plan plan, TroubleshootBoard board,
            PcbPlacementPlanner.Plan plannerPlan, PcbBoardLayout placement,
            CloneFixture selected) {
        board.validate();
        placement.validateAgainst(board);
        PcbFactoryLinkPolicy.validateLayout(placement);
        require(plannerPlan.candidate == selected.candidateAttempt,
            "planner receipt candidate matches the selected attempt");
        require(plan.physicalPackageCount() == plan.parts().size() &&
            plannerPlan.footprints.size() == plan.physicalPackageCount(),
            "raw planner footprint census matches plan-owned package inventory");
        TreeSet<String> declaredComponents = new TreeSet<String>(board.getComponentIds());
        TreeSet<String> placedComponents = new TreeSet<String>();
        for (PcbComponentPlacement placed : placement.getComponents()) {
            require(placed != null && placedComponents.add(placed.getComponentId()),
                "raw placement contains unique non-null component identities");
            BoardComponent declared = board.getComponent(placed.getComponentId());
            require(declared != null && declared.getPhysicalPackage() == placed.getPhysicalPackage(),
                "raw placement retains declared physical-package identity");
        }
        require(placedComponents.equals(declaredComponents),
            "raw placement is an exact board-component identity bijection");
        TreeSet<String> declaredPads = new TreeSet<String>(board.getPadIds());
        TreeSet<String> placedPads = new TreeSet<String>();
        for (PcbPadPlacement placed : placement.getPads()) {
            require(placed != null && placedPads.add(placed.getPadId()),
                "raw placement contains unique non-null pad identities");
            require(board.getPad(placed.getPadId()) != null,
                "raw pad placement maps to a declared logical terminal");
        }
        require(placedPads.equals(declaredPads),
            "raw placement is an exact board-pad identity bijection");
        int expectedPadCount = 0;
        TreeSet<String> planComponents = new TreeSet<String>();
        TreeSet<String> planPads = new TreeSet<String>();
        for (Rb56Plan.Part part : plan.parts()) {
            require(planComponents.add(part.id), "plan package ID is unique");
            BoardComponent declared = board.getComponent(part.id);
            require(declared != null && declared.getPhysicalPackage() == part.physicalPackage,
                "board retains the plan-owned physical package");
            PcbComponentPlacement placed = placement.getComponent(part.id);
            require(placed != null && placed.getPhysicalPackage() == part.physicalPackage,
                "raw planner candidate retains package identity");
            String[] terminals = part.terminalIds();
            String[] nets = part.netIds();
            require(terminals.length == nets.length &&
                terminals.length == part.physicalPackage.getTerminalCount(),
                "plan terminal, net and package declarations align");
            expectedPadCount += terminals.length;
            for (int index = 0; index < terminals.length; index++) {
                String padId = part.id + "." + terminals[index];
                require(planPads.add(padId), "plan pad identity is unique");
                BoardPad logical = board.getPad(padId);
                require(logical != null && logical.getComponentId().equals(part.id) &&
                    logical.getTerminalId().equals(terminals[index]) &&
                    logical.getNetId().equals(nets[index]) && placement.getPad(padId) != null,
                    "plan terminal preserves exact logical component, terminal and net identity");
            }
        }
        require(planComponents.equals(declaredComponents) && planPads.equals(declaredPads) &&
            expectedPadCount == declaredPads.size(),
            "plan package and pad census matches the exact logical board inventory");
        require(placement.getTraces().isEmpty() && placement.getHoles().isEmpty(),
            "raw candidate starts without published traces or vias");
    }

    private static void require(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }

    private static String quote(String value) {
        StringBuilder out = new StringBuilder("\"");
        for (int index = 0; index < value.length(); index++) {
            char ch = value.charAt(index);
            if (ch == '\\' || ch == '\"') out.append('\\').append(ch);
            else if (ch < 0x20) {
                final char[] hex = "0123456789abcdef".toCharArray();
                out.append("\\u00").append(hex[(ch >>> 4) & 15]).append(hex[ch & 15]);
            } else out.append(ch);
        }
        return out.append('\"').toString();
    }
}
