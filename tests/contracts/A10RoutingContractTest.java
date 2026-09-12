package com.lushprojects.circuitjs1.client;

import java.util.Vector;

/** Focused A10 boundary checks for bounded physical candidate generation. */
public final class A10RoutingContractTest {
    private static int assertions;

    private A10RoutingContractTest() { }

    public static void main(String[] args) {
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
        final int[] checks = new int[] { 0 };
        try {
            new SeededPcbLayoutGenerator(registry, new SeededPcbLayoutGenerator.AttemptObserver() {
                public void check(int attempt) { checks[0]++; }
            }).generate(denseBoard(densePackage, 40), 0L);
            throw new AssertionError("dense placement unexpectedly produced a layout");
        } catch (PcbRoutingRejectedException expected) {
            require(expected.isExhausted(), "placement rejection reports bounded exhaustion");
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
        return twoComponentBoard(connectorPackage, PhysicalPackages.AXIAL_RESISTOR);
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
        terminals.add(boundaryTerminal("2", 70));
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
