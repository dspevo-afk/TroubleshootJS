package com.lushprojects.circuitjs1.client;

import java.util.Vector;

/** Focused A10 boundary checks for bounded physical candidate generation. */
public final class A10RoutingContractTest {
    private static int assertions;

    private A10RoutingContractTest() { }

    public static void main(String[] args) {
        admissibleRoutingLowerBounds();
        deterministicAttemptsAndBestCandidate();
        placementRejectionRetriesToTypedExhaustion();
        routingRejectionRetriesToTypedExhaustion();
        routeQualityClassificationAndProgrammerFailure();
        a03SeedQualityRetryCanary();
        assemblyCheckpointFailurePropagatesAfterCleanup();
        assemblyRouteQualityFailureNormalizesAfterCleanup();
        unexpectedProviderFailurePropagates();
        unexpectedRegistryFailurePropagates();
        observerFailurePropagates();
        nullBoardFailsBeforeObserver();
        System.out.println("PASS: A10 routing rejection contracts assertions=" + assertions);
    }

    private static void deterministicAttemptsAndBestCandidate() {
        final StringBuilder firstAttempts = new StringBuilder();
        final StringBuilder secondAttempts = new StringBuilder();
        TroubleshootBoard board = TroubleshootBoardFixtures.createLedIndicatorBoard();
        PcbBoardLayout first = new SeededPcbLayoutGenerator(
            StandardPcbFootprintProviders.createRegistry()).generate(board, 0L,
            new SeededPcbLayoutGenerator.AttemptObserver() {
                public void check(int attempt) { firstAttempts.append(attempt).append(','); }
            });
        PcbBoardLayout second = new SeededPcbLayoutGenerator(
            StandardPcbFootprintProviders.createRegistry(), new SeededPcbLayoutGenerator.AttemptObserver() {
                public void check(int attempt) { secondAttempts.append(attempt).append(','); }
            }).generate(board, 0L);
        require(firstAttempts.length() > 0 && firstAttempts.toString().equals(secondAttempts.toString()),
            "deterministic attempt checkpoints");
        require(first.geometryFingerprint().equals(second.geometryFingerprint()),
            "deterministic best viable geometry");
        System.out.println("ROUTING_METRICS profile=EASY seed=0 placements=" +
            first.getGenerationPlacementAttempts() + " routes=" +
            first.getGenerationRoutingAttempts() + " expansions=" +
            first.getGenerationRoutingExpansions() + " routeMillis=" +
            first.getGenerationRoutingMillis());
    }

    private static void admissibleRoutingLowerBounds() {
        require(PcbNetRouter.admissibleLowerBound(8, 10, 2) == 150,
            "fresh endpoint route charges ten per step and both required turns");
        require(PcbNetRouter.admissibleLowerBound(8, 7, 2) == 126,
            "fresh endpoint route with same-net clearance uses the seven-cost floor");
        require(PcbNetRouter.admissibleLowerBound(8, 2, 2) == 86,
            "existing same-net copper and tree joins retain the cost-two floor");
        require(PcbNetRouter.admissibleLowerBound(0, 2, 0) == 0,
            "an already reached goal has zero remaining cost");
        require(PcbNetRouter.admissibleLowerBound(Integer.MAX_VALUE, 10,
                Integer.MAX_VALUE) == Integer.MAX_VALUE,
            "routing lower bound saturates instead of overflowing");
        for (int direction = 0; direction < 4; direction++) {
            int dx = direction == 1 ? 1 : direction == 3 ? -1 : 0;
            int dy = direction == 0 ? -1 : direction == 2 ? 1 : 0;
            int px = dx == 0 ? 1 : 0;
            int py = dy == 0 ? 1 : 0;
            requireTurnBound(2 * dx, 2 * dy, direction, 0);
            requireTurnBound(-2 * dx, -2 * dy, direction, 1);
            requireTurnBound(dx + px, dy + py, direction, 1);
            requireTurnBound(-dx + px, -dy + py, direction, 2);
        }
        boolean[] diagonalGoal = new boolean[49];
        diagonalGoal[4 * 7 + 4] = true;
        require(new PcbNetRouter.GoalTurnBounds(diagonalGoal, 7, 7)
                .minimumTurns(3, 3, 4) == 0,
            "the initial heading pays no turn cost before its first move");
        dijkstraRoutingParity();
    }

    private static void requireTurnBound(int goalDx, int goalDy, int direction, int expected) {
        boolean[] goals = new boolean[49];
        goals[(3 + goalDy) * 7 + 3 + goalDx] = true;
        PcbNetRouter.GoalTurnBounds bounds = new PcbNetRouter.GoalTurnBounds(goals, 7, 7);
        require(bounds.minimumTurns(3, 3, direction) == expected,
            "directional turn lower bound " + direction + " to offset " + goalDx + "," +
                goalDy + " is admissible");
    }

    /** Compares the production heuristic with an independent exhaustive Dijkstra oracle. */
    private static void dijkstraRoutingParity() {
        final int[][][] profiles = new int[][][] {
            filledCosts(7, 6, 10), clearanceCosts(7, 6), treeCosts(7, 6)
        };
        final int[] expectedMinimumSteps = new int[] { 10, 7, 2 };
        final boolean[] goals = new boolean[42];
        goals[5 * 7 + 6] = true;
        goals[5 * 7] = true;
        for (int profile = 0; profile < profiles.length; profile++) {
            int[][] costs = profiles[profile];
            int width = costs[0].length;
            int height = costs.length;
            int[] distances = routingDistances(goals, width, height);
            int minimum = expectedMinimumSteps[profile];
            PcbNetRouter.GoalTurnBounds turns =
                new PcbNetRouter.GoalTurnBounds(goals, width, height);
            for (int y = 0; y < height; y++) for (int x = 0; x < width; x++)
                for (int direction = 0; direction <= 4; direction++) {
                    int exact = dijkstraRouteCost(costs, goals, x, y, direction);
                    int heuristic = PcbNetRouter.admissibleLowerBound(
                        distances[y * width + x], minimum,
                        turns.minimumTurns(x, y, direction));
                    require(heuristic <= exact,
                        "routing heuristic stays below Dijkstra profile " + profile +
                            " state " + x + "," + y + "," + direction);
                    require(aStarRouteCost(costs, goals, x, y, direction, distances,
                            turns, minimum) == exact,
                        "A* and independent Dijkstra route costs agree for profile " + profile +
                            " state " + x + "," + y + "," + direction);
                }
        }
    }

    private static int[][] filledCosts(int width, int height, int cost) {
        int[][] result = new int[height][width];
        for (int y = 0; y < height; y++) java.util.Arrays.fill(result[y], cost);
        return result;
    }

    private static int[][] clearanceCosts(int width, int height) {
        int[][] result = filledCosts(width, height, 10);
        for (int y = 0; y < height; y++) for (int x = 0; x < width; x++)
            if (x == 2 || y == 3) result[y][x] = 7;
        return result;
    }

    private static int[][] treeCosts(int width, int height) {
        int[][] result = clearanceCosts(width, height);
        result[1][2] = 2; result[1][3] = 2; result[2][3] = 2;
        result[4][4] = 2; result[5][4] = 2;
        return result;
    }

    private static int[] routingDistances(boolean[] goals, int width, int height) {
        int[] distances = new int[goals.length];
        java.util.Arrays.fill(distances, Integer.MAX_VALUE);
        int[] queue = new int[goals.length];
        int head = 0, tail = 0;
        for (int cell = 0; cell < goals.length; cell++) if (goals[cell]) {
            distances[cell] = 0;
            queue[tail++] = cell;
        }
        while (head < tail) {
            int cell = queue[head++], x = cell % width, y = cell / width;
            if (x > 0) tail = addDistance(cell - 1, cell, distances, queue, tail);
            if (x + 1 < width) tail = addDistance(cell + 1, cell, distances, queue, tail);
            if (y > 0) tail = addDistance(cell - width, cell, distances, queue, tail);
            if (y + 1 < height) tail = addDistance(cell + width, cell, distances, queue, tail);
        }
        return distances;
    }

    private static int addDistance(int next, int current, int[] distances,
            int[] queue, int tail) {
        if (distances[next] == Integer.MAX_VALUE) {
            distances[next] = distances[current] + 1;
            queue[tail++] = next;
        }
        return tail;
    }

    private static int dijkstraRouteCost(int[][] costs, boolean[] goals,
            int startX, int startY, int startDirection) {
        int width = costs[0].length, height = costs.length, stateCount = width * height * 5;
        int[] best = new int[stateCount];
        java.util.Arrays.fill(best, Integer.MAX_VALUE);
        boolean[] settled = new boolean[stateCount];
        int start = (startY * width + startX) * 5 + startDirection;
        best[start] = 0;
        for (int iteration = 0; iteration < stateCount; iteration++) {
            int current = -1;
            for (int key = 0; key < stateCount; key++)
                if (!settled[key] && best[key] != Integer.MAX_VALUE &&
                        (current < 0 || best[key] < best[current])) current = key;
            if (current < 0) return Integer.MAX_VALUE;
            int direction = current % 5, cell = current / 5;
            int x = cell % width, y = cell / width;
            if (goals[cell]) return best[current];
            settled[current] = true;
            for (int nextDirection = 0; nextDirection < 4; nextDirection++) {
                int nx = x + (nextDirection == 1 ? 1 : nextDirection == 3 ? -1 : 0);
                int ny = y + (nextDirection == 0 ? -1 : nextDirection == 2 ? 1 : 0);
                if (nx < 0 || ny < 0 || nx >= width || ny >= height) continue;
                int next = (ny * width + nx) * 5 + nextDirection;
                int candidate = best[current] + costs[ny][nx] +
                    (direction != 4 && direction != nextDirection ? 35 : 0);
                if (candidate < best[next]) best[next] = candidate;
            }
        }
        return Integer.MAX_VALUE;
    }

    private static int aStarRouteCost(int[][] costs, boolean[] goals, int startX, int startY,
            int startDirection, int[] distances, PcbNetRouter.GoalTurnBounds turns,
            int minimumStepCost) {
        int width = costs[0].length, height = costs.length;
        int[] best = new int[width * height * 5];
        java.util.Arrays.fill(best, Integer.MAX_VALUE);
        java.util.PriorityQueue<ContractRouteNode> open =
            new java.util.PriorityQueue<ContractRouteNode>();
        int start = (startY * width + startX) * 5 + startDirection;
        best[start] = 0;
        open.add(new ContractRouteNode(start, 0, routeHeuristic(start, width, distances,
            turns, minimumStepCost)));
        while (!open.isEmpty()) {
            ContractRouteNode node = open.remove();
            if (node.cost != best[node.state]) continue;
            int direction = node.state % 5, cell = node.state / 5;
            int x = cell % width, y = cell / width;
            if (goals[cell]) return node.cost;
            for (int nextDirection = 0; nextDirection < 4; nextDirection++) {
                int nx = x + (nextDirection == 1 ? 1 : nextDirection == 3 ? -1 : 0);
                int ny = y + (nextDirection == 0 ? -1 : nextDirection == 2 ? 1 : 0);
                if (nx < 0 || ny < 0 || nx >= width || ny >= height) continue;
                int next = (ny * width + nx) * 5 + nextDirection;
                int candidate = node.cost + costs[ny][nx] +
                    (direction != 4 && direction != nextDirection ? 35 : 0);
                if (candidate < best[next]) {
                    best[next] = candidate;
                    open.add(new ContractRouteNode(next, candidate,
                        routeHeuristic(next, width, distances, turns, minimumStepCost)));
                }
            }
        }
        return Integer.MAX_VALUE;
    }

    private static int routeHeuristic(int state, int width, int[] distances,
            PcbNetRouter.GoalTurnBounds turns, int minimumStepCost) {
        int direction = state % 5, cell = state / 5;
        return PcbNetRouter.admissibleLowerBound(distances[cell], minimumStepCost,
            turns.minimumTurns(cell % width, cell / width, direction));
    }

    private static final class ContractRouteNode implements Comparable<ContractRouteNode> {
        final int state, cost, heuristic;
        ContractRouteNode(int state, int cost, int heuristic) {
            this.state = state; this.cost = cost; this.heuristic = heuristic;
        }
        public int compareTo(ContractRouteNode other) {
            int byTotal = (cost + heuristic) - (other.cost + other.heuristic);
            if (byTotal != 0) return byTotal;
            int byHeuristic = heuristic - other.heuristic;
            return byHeuristic != 0 ? byHeuristic : state - other.state;
        }
    }

    private static void placementRejectionRetriesToTypedExhaustion() {
        final PhysicalPackage densePackage = PhysicalPackage.developerPackageWithGenericGeometry(
            "A10_DENSE_PACKAGE", vector("1", "2"), new Vector<String>(), false);
        PcbFootprintRegistry registry = StandardPcbFootprintProviders.createRegistry();
        registry.register(densePackage, new PcbFootprintProvider() {
            public PcbFootprint create(BoardComponent component, int x, int y,
                    java.util.Random random, Rectangle outline) {
                return PcbFootprint.fromPhysicalPackage(component, x, y, random, outline);
            }
        });
        // Forty serviceable parts now fit. Use the real declared capacity boundary:
        // 128 bodies plus J1 exceeds the unchanged 128-part planner limit.
        require(PcbPlacementPlanner.MAX_PARTS == 128, "declared placement part budget");
        final int[] checks = new int[] { 0 };
        try {
            new SeededPcbLayoutGenerator(registry, new SeededPcbLayoutGenerator.AttemptObserver() {
                public void check(int attempt) { checks[0]++; }
            }).generate(denseBoard(densePackage, 128), 0L);
            throw new AssertionError("dense placement unexpectedly produced a layout");
        } catch (PcbRoutingRejectedException expected) {
            require(expected.isExhausted(), "placement rejection reports bounded exhaustion");
            require(expected.getMessage().contains("PART_LIMIT"), "capacity rejection has its precise cause");
            require(expected.getKind() == PcbRoutingRejectedException.Kind.PLACEMENT,
                "placement rejection keeps its physical classification");
            require(expected.getAttemptCount() == 80 && checks[0] == 80,
                "placement rejection consumes the deterministic attempt budget");
            require(expected.getCause() instanceof PcbRoutingRejectedException,
                "exhaustion retains the last typed candidate rejection");
        }
    }

    private static void unexpectedProviderFailurePropagates() {
        final PhysicalPackage throwingPackage = PhysicalPackage.developerPackageWithGenericGeometry(
            "A10_THROWING_PACKAGE", vector("1", "2"), new Vector<String>(), true);
        PcbFootprintRegistry registry = StandardPcbFootprintProviders.createRegistry();
        registry.register(throwingPackage, new PcbFootprintProvider() {
            public PcbFootprint create(BoardComponent component, int x, int y,
                    java.util.Random random, Rectangle outline) {
                throw new RuntimeException("unexpected provider failure");
            }
        });
        try {
            new SeededPcbLayoutGenerator(registry).generate(
                twoComponentBoard(throwingPackage, PhysicalPackages.AXIAL_RESISTOR), 0L);
            throw new AssertionError("provider RuntimeException was swallowed");
        } catch (RuntimeException expected) {
            require(expected.getClass() == RuntimeException.class &&
                    "unexpected provider failure".equals(expected.getMessage()),
                "unexpected provider RuntimeException propagates unchanged");
        }
    }

    private static void routingRejectionRetriesToTypedExhaustion() {
        final PhysicalPackage boundaryConnector = boundaryConnectorPackage();
        PcbFootprintRegistry registry = StandardPcbFootprintProviders.createRegistry();
        registry.register(boundaryConnector, new PcbFootprintProvider() {
            public PcbFootprint create(BoardComponent component, int x, int y,
                    java.util.Random random, Rectangle outline) {
                return PcbFootprint.fromPhysicalPackage(component, x, y,
                    boundaryConnector.getGeometry());
            }
        });
        final int[] checks = new int[] { 0 };
        try {
            new SeededPcbLayoutGenerator(registry, new SeededPcbLayoutGenerator.AttemptObserver() {
                public void check(int attempt) { checks[0]++; }
            }).generate(routingBoundaryBoard(boundaryConnector), 0L);
            throw new AssertionError("boundary routing unexpectedly produced a layout");
        } catch (PcbRoutingRejectedException expected) {
            require(expected.isExhausted(), "routing rejection reports bounded exhaustion");
            require(expected.getKind() == PcbRoutingRejectedException.Kind.ROUTING,
                "routing rejection keeps its physical classification");
            require(expected.getAttemptCount() == 80 && checks[0] > 80,
                "routing rejection checks bounded path work inside attempts");
        }
    }

    private static void routeQualityClassificationAndProgrammerFailure() {
        PcbBoardLayout bendsLayout = qualityLayout();
        int[] bendX = new int[20];
        int[] bendY = new int[20];
        for (int index = 0; index < bendX.length; index++) {
            bendX[index] = 100 + index * 30;
            bendY[index] = (index & 1) == 0 ? 100 : 110;
        }
        bendsLayout.addTrace(new PcbTraceGeometry("a10-bends", bendX, bendY));
        try {
            bendsLayout.validateRouteQuality();
            throw new AssertionError(
                "declared bend limit should reject the candidate with its typed marker");
        } catch (PcbBoardLayout.RouteQualityRejectedException expected) {
            require(expected.getKind() == PcbBoardLayout.RouteQualityRejectedException.Kind.BENDS,
                "bend admission must retain its typed quality classification");
        }

        PcbBoardLayout detourLayout = qualityLayout();
        detourLayout.addTrace(new PcbTraceGeometry("a10-detour",
            new int[] { 100, 500, 500, 200, 200, 100 },
            new int[] { 100, 100, 200, 200, 300, 300 }));
        try {
            detourLayout.validateRouteQuality();
            throw new AssertionError(
                "declared detour limit should reject the candidate with its typed marker");
        } catch (PcbBoardLayout.RouteQualityRejectedException expected) {
            require(expected.getKind() == PcbBoardLayout.RouteQualityRejectedException.Kind.DETOUR,
                "detour admission must retain its typed quality classification");
        }

        PcbBoardLayout malformedLayout = qualityLayout();
        malformedLayout.addTrace(new PcbTraceGeometry("a10-programmer-failure",
            new int[] { 100, 100 }, new int[] { 100, 100 }));
        try {
            malformedLayout.validateRouteQuality();
            throw new AssertionError("zero-length trace should remain a programmer/validator failure");
        } catch (RuntimeException expected) {
            require(expected.getClass() == IllegalStateException.class,
                "structural route failures must not be relabeled as expected quality rejection");
        }
    }

    private static PcbBoardLayout qualityLayout() {
        return new PcbBoardLayout(1040, 520,
            new Rectangle(70, 50, 720, 400),
            new Rectangle(850, 125, 150, 255));
    }

    private static void a03SeedQualityRetryCanary() {
        BoundedAssemblyPlan plan = BoundedAssemblyPlan.resolve(
            BoundedAssemblyRequest.forCanary(1L));
        BoundedGeneratedBoardAssembler.PlanPhysicalChoices choices =
            BoundedGeneratedBoardAssembler.describePhysicalChoices(plan);
        require(choices.getLayoutVersion() == SeededPcbLayoutGenerator.CURRENT_VERSION,
            "A03 seed 1 must survive bounded route-quality admission");
        require(choices.getPackages().size() > 0,
            "A03 seed 1 must retain physical package choices after retry");
    }

    private static void assemblyCheckpointFailurePropagatesAfterCleanup() {
        BoundedAssemblyPlan plan = BoundedAssemblyPlan.resolve(
            BoundedAssemblyRequest.forCanary(1L));
        CheckpointServices services = new CheckpointServices();
        GenerationJob job = new GenerationJob(services, 100000, 80, 5000);
        CirSim previousSimulator = CircuitElm.sim;
        CirSim simulator = new CirSim();
        simulator.gridSize = 16;
        simulator.gridMask = ~(simulator.gridSize - 1);
        simulator.gridRound = simulator.gridSize / 2 - 1;
        CircuitElm.sim = simulator;
        boolean entered = false;
        try {
            GenerationWorkScope.enter(job);
            entered = true;
            try {
                BoundedGeneratedBoardAssembler.assemblePreparedPlan(plan);
                throw new AssertionError("assembly checkpoint unexpectedly completed");
            } catch (GenerationJob.Stale expected) {
                require(job.getOutcome() == GenerationJob.Outcome.STALE &&
                        job.getFailure() == expected,
                    "assembly propagates the typed checkpoint failure after cleanup");
                require(services.currentChecks >= 2,
                    "assembly checkpoint canary reaches the routing work boundary");
            } catch (BoundedGeneratedBoardAssembler.AssemblyFailure wrapped) {
                throw new AssertionError(
                    "successful assembly cleanup must not wrap a typed checkpoint failure", wrapped);
            }
        } finally {
            if (entered)
                GenerationWorkScope.exit(job);
            CircuitElm.sim = previousSimulator;
        }
    }

    private static void assemblyRouteQualityFailureNormalizesAfterCleanup() {
        final long seed = 1L;
        final BoundedAssemblyRequest request = BoundedAssemblyRequest.forCanary(seed);
        final PcbBoardLayout.RouteQualityRejectedException qualityFailure =
            routeQualityRejection();
        CirSim previousSimulator = CircuitElm.sim;
        CirSim simulator = new CirSim();
        simulator.gridSize = 16;
        simulator.gridMask = ~(simulator.gridSize - 1);
        simulator.gridRound = simulator.gridSize / 2 - 1;
        CircuitElm.sim = simulator;
        try {
            try {
                BoundedGeneratedBoardAssembler.assemble(request,
                    new BoundedGeneratedBoardAssembler.FailureProbe() {
                        public void after(BoundedGeneratedBoardAssembler.Stage stage) {
                            if (stage == BoundedGeneratedBoardAssembler.Stage.LAYOUT)
                                throw qualityFailure;
                        }
                    });
                throw new AssertionError("typed route-quality failure unexpectedly completed");
            } catch (PcbRoutingRejectedException expected) {
                require(expected.getKind() == PcbRoutingRejectedException.Kind.ROUTING,
                    "assembly route-quality failure is classified as routing");
                require(expected.getSeed() == seed && expected.getAttemptIndex() == 0 &&
                        expected.getAttemptCount() == 1 && !expected.isExhausted(),
                    "assembly route-quality failure preserves the current seed metadata");
                require(expected.getCause() == qualityFailure,
                    "assembly route-quality failure preserves the typed quality cause");
            } catch (BoundedGeneratedBoardAssembler.AssemblyFailure wrapped) {
                throw new AssertionError(
                    "successful cleanup must not wrap a typed route-quality rejection", wrapped);
            }

            final RuntimeException unexpected =
                new IllegalStateException("A10 injected assembler invariant");
            try {
                BoundedGeneratedBoardAssembler.assemble(request,
                    new BoundedGeneratedBoardAssembler.FailureProbe() {
                        public void after(BoundedGeneratedBoardAssembler.Stage stage) {
                            if (stage == BoundedGeneratedBoardAssembler.Stage.LAYOUT)
                                throw unexpected;
                        }
                    });
                throw new AssertionError("unexpected assembler failure unexpectedly completed");
            } catch (BoundedGeneratedBoardAssembler.AssemblyFailure expected) {
                require(expected.getOriginalFailure() == unexpected,
                    "unexpected assembler failure remains the original failure");
                require(expected.isCleanupSucceeded(),
                    "unexpected assembler failure retains successful cleanup metadata");
            } catch (PcbRoutingRejectedException rejected) {
                throw new AssertionError(
                    "unexpected assembler RuntimeException was relabeled as routing", rejected);
            }
        } finally {
            CircuitElm.sim = previousSimulator;
        }
    }

    private static PcbBoardLayout.RouteQualityRejectedException routeQualityRejection() {
        PcbBoardLayout layout = qualityLayout();
        layout.addTrace(new PcbTraceGeometry("a10-assembly-detour",
            new int[] { 100, 500, 500, 200, 200, 100 },
            new int[] { 100, 100, 200, 200, 300, 300 }));
        try {
            layout.validateRouteQuality();
            throw new AssertionError("route-quality fixture unexpectedly passed validation");
        } catch (PcbBoardLayout.RouteQualityRejectedException expected) {
            require(expected.getKind() == PcbBoardLayout.RouteQualityRejectedException.Kind.DETOUR,
                "assembly route-quality fixture uses the declared detour rejection");
            return expected;
        }
    }

    private static void unexpectedRegistryFailurePropagates() {
        PhysicalPackage missingPackage = PhysicalPackage.developerPackageWithGenericGeometry(
            "A10_MISSING_PACKAGE", vector("1", "2"), new Vector<String>(), true);
        try {
            new SeededPcbLayoutGenerator(new PcbFootprintRegistry()).generate(
                singleComponentBoard(missingPackage), 0L);
            throw new AssertionError("missing registry provider was swallowed");
        } catch (RuntimeException expected) {
            require(expected.getClass() == IllegalStateException.class &&
                    expected.getMessage().indexOf("No PCB footprint provider") >= 0,
                "unexpected registry failure propagates unchanged");
        }
    }

    private static void nullBoardFailsBeforeObserver() {
        final int[] checks = new int[] { 0 };
        try {
            new SeededPcbLayoutGenerator(StandardPcbFootprintProviders.createRegistry(),
                new SeededPcbLayoutGenerator.AttemptObserver() {
                    public void check(int attempt) { checks[0]++; }
                }).generate(null, 0L);
            throw new AssertionError("null board was accepted");
        } catch (IllegalArgumentException expected) {
            require(checks[0] == 0, "null board fails before generation checkpoints");
        }
    }

    private static void observerFailurePropagates() {
        final RuntimeException marker = new RuntimeException("unexpected observer failure");
        try {
            new SeededPcbLayoutGenerator(StandardPcbFootprintProviders.createRegistry())
                .generate(TroubleshootBoardFixtures.createLedIndicatorBoard(), 0L,
                    new SeededPcbLayoutGenerator.AttemptObserver() {
                        public void check(int attempt) { throw marker; }
                    });
            throw new AssertionError("observer RuntimeException was swallowed");
        } catch (RuntimeException expected) {
            require(expected == marker, "unexpected observer RuntimeException propagates unchanged");
        }
    }

    private static TroubleshootBoard denseBoard(PhysicalPackage packageDefinition,
            int componentCount) {
        TroubleshootBoard board = new TroubleshootBoard("A10_DENSE");
        board.addNet(new BoardNet("N"));
        board.addComponent(new BoardComponent("J1", "CONNECTOR",
            PhysicalPackages.THROUGH_HOLE_CONNECTOR_2));
        addPads(board, "J1", "1", "2");
        for (int index = 1; index <= componentCount; index++) {
            String id = "R" + index;
            board.addComponent(new BoardComponent(id, "DENSE", packageDefinition));
            addPads(board, id, "1", "2");
        }
        board.validate();
        return board;
    }

    private static TroubleshootBoard twoComponentBoard(PhysicalPackage connectorPackage,
            PhysicalPackage loadPackage) {
        TroubleshootBoard board = new TroubleshootBoard("A10_PROVIDER_FAILURE");
        board.addNet(new BoardNet("N"));
        board.addComponent(new BoardComponent("J1", "CONNECTOR", connectorPackage));
        board.addComponent(new BoardComponent("R1", "LOAD", loadPackage));
        addPads(board, "J1", "1", "2");
        addPads(board, "R1", "1", "2");
        board.validate();
        return board;
    }

    private static TroubleshootBoard routingBoundaryBoard(PhysicalPackage connectorPackage) {
        // Two legal, distinct probe surfaces have less than the routing clearance.
        // The demand planner can place their envelopes; no candidate can escape
        // both nets. This retains a real routing rejection after fixed outlines
        // and outward-facing connector admission were retired by P03.
        TroubleshootBoard board = new TroubleshootBoard("A10_ESCAPE_CLEARANCE");
        board.addNet(new BoardNet("A"));
        board.addNet(new BoardNet("B"));
        board.addComponent(new BoardComponent("J1", "CONNECTOR", connectorPackage));
        board.addComponent(new BoardComponent("R1", "LOAD", PhysicalPackages.AXIAL_RESISTOR));
        for (String id : new String[] { "J1", "R1" }) {
            board.addPad(new BoardPad(id + ".1", id, "1", "A"));
            board.addPad(new BoardPad(id + ".2", id, "2", "B"));
        }
        Vector<PcbPlacementConstraints.Part> parts = new Vector<PcbPlacementConstraints.Part>();
        parts.add(new PcbPlacementConstraints.Part("J1", "input", "Input", "board",
            PcbPlacementConstraints.Anchor.LEFT, 20));
        parts.add(new PcbPlacementConstraints.Part("R1", "load", "Load", "board",
            PcbPlacementConstraints.Anchor.NONE, 20));
        board.setPlacementConstraints(new PcbPlacementConstraints(parts,
            new Vector<PcbPlacementConstraints.Barrier>()));
        board.validate();
        return board;
    }

    private static TroubleshootBoard singleComponentBoard(PhysicalPackage connectorPackage) {
        TroubleshootBoard board = new TroubleshootBoard("A10_REGISTRY_FAILURE");
        board.addNet(new BoardNet("N"));
        board.addComponent(new BoardComponent("J1", "CONNECTOR", connectorPackage));
        addPads(board, "J1", "1", "2");
        board.validate();
        return board;
    }

    private static void addPads(TroubleshootBoard board, String componentId,
            String firstTerminal, String secondTerminal) {
        board.addPad(new BoardPad(componentId + "." + firstTerminal, componentId,
            firstTerminal, "N"));
        board.addPad(new BoardPad(componentId + "." + secondTerminal, componentId,
            secondTerminal, "N"));
    }

    private static Vector<String> vector(String first, String second) {
        Vector<String> result = new Vector<String>();
        result.add(first); result.add(second);
        return result;
    }

    private static final class CheckpointServices implements GenerationJob.Services {
        int currentChecks;

        public int candidateCount() { return 1; }
        public String manifest(int candidate) { return "a10-checkpoint"; }
        public void beginCandidate(int index) { }
        public String resolve() { return "resolved"; }
        public String healthy() { return "healthy"; }
        public String physical() { return "physical"; }
        public boolean proveNext() { return false; }
        public String symptom() { return "symptom"; }
        public String dependencies() { return "dependencies"; }
        public void publish(GenerationReceipt receipt) { }
        public void abort() { }
        public boolean isCurrent() { return ++currentChecks < 2; }
        public long nowMillis() { return 0L; }
    }

    private static PhysicalPackage boundaryConnectorPackage() {
        Vector<PhysicalPackageGeometry.Terminal> terminals =
            new Vector<PhysicalPackageGeometry.Terminal>();
        terminals.add(boundaryTerminal("1", 30));
        terminals.add(boundaryTerminal("2", 40));
        PhysicalPackageGeometry geometry = new PhysicalPackageGeometry(150, 100, terminals,
            new Rectangle(20, 10, 100, 80), new Rectangle(20, 10, 100, 80),
            new Rectangle(10, 10, 150, 80), new Rectangle(0, 0, 160, 100),
            new Rectangle(-5, -5, 170, 110));
        Vector<PhysicalPackage.GeometryVariant> variants =
            new Vector<PhysicalPackage.GeometryVariant>();
        variants.add(new PhysicalPackage.GeometryVariant("A10_BOUNDARY_DEFAULT", "IDENTITY",
            geometry));
        return new PhysicalPackage("A10_BOUNDARY_CONNECTOR", vector("1", "2"),
            single("1=2"), true, geometry, variants, "A10_BOUNDARY_DEFAULT",
            PhysicalPackage.GeometryVariantSelection.FIXED_DEFAULT);
    }

    private static PhysicalPackageGeometry.Terminal boundaryTerminal(String id, int y) {
        Point pad = new Point(150, y);
        Point body = new Point(100, y);
        PhysicalPackageGeometry.Lead connected = new PhysicalPackageGeometry.Lead(pad, body,
            new Rectangle(97, y - 3, 56, 6));
        PhysicalPackageGeometry.Lead lifted = new PhysicalPackageGeometry.Lead(
            new Point(130, y), body, new Rectangle(97, y - 3, 36, 6));
        return new PhysicalPackageGeometry.Terminal(id, pad,
            new Rectangle(149, y - 1, 2, 2), pad,
            new Rectangle(148, y - 2, 4, 4), connected, lifted, 1, 0, 10);
    }

    private static Vector<String> single(String value) {
        Vector<String> result = new Vector<String>();
        result.add(value);
        return result;
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
        assertions++;
    }
}
