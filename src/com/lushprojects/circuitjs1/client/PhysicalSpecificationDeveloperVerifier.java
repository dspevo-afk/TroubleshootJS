package com.lushprojects.circuitjs1.client;

import java.util.HashMap;
import java.util.Vector;

/** Developer-only Task 35 proof; it never registers a player-visible part family. */
final class PhysicalSpecificationDeveloperVerifier {
    private PhysicalSpecificationDeveloperVerifier() { }

    static void verify(CirSim sim) {
        if (sim == null || sim.getGeneratedBoardInstance() == null ||
                sim.pcbWorkbenchController == null || sim.backcontext == null)
            throw new IllegalStateException("Task 35 verification requires a generated workbench");
        verifyCatalogContract();
        verifyFuturePhysicalPart(sim);
    }

    private static void verifyCatalogContract() {
        verifyCatalog(new ResistorReplacementCatalog().getEntries());
        verifyCatalog(new DiodeReplacementCatalog().getEntries());
        verifyCatalog(new LedReplacementCatalog().getEntries());
        ResistorCatalogEntry resistor = new ResistorReplacementCatalog().getEntries().firstElement();
        require(resistor.getSpecification().getRatings().size() == 1 &&
                resistor.getSpecification().getRatings().firstElement() instanceof PowerRating,
            "resistor specification lost its reusable power rating boundary");
    }

    private static void verifyCatalog(Vector<? extends PhysicalCatalogEntry<?>> entries) {
        require(!entries.isEmpty(), "physical catalog has no immutable entries");
        for (PhysicalCatalogEntry<?> entry : entries) {
            require(entry != null && entry.getId() != null && entry.getId().length() > 0 &&
                    entry.getSpecification() != null && entry.getPlayerVisibleNameplate() != null &&
                    entry.getSpecification() != entry.getPlayerVisibleNameplate() &&
                    entry.getOrientation() != null,
                "catalog entry did not separate technical and visible metadata");
        }
    }

    private static void verifyFuturePhysicalPart(final CirSim sim) {
        PhysicalPackage boardPackage = futurePackage();
        final PhysicalPackage partPackage = reversedFuturePackage();
        PhysicalPackage conflictingPackage = conflictingFuturePackage();
        PcbFootprintRegistry footprintRegistry = StandardPcbFootprintProviders.createRegistry();
        PhysicalPartRenderRegistry renderRegistry =
            sim.pcbWorkbenchController.getRenderer().getRenderRegistryForDeveloperVerification();
        require(boardPackage.isEquivalentTo(partPackage) &&
                boardPackage.isInternallyConnected("1", "2") &&
                boardPackage.isInternallyConnected("2", "3") &&
                footprintRegistry.getProvider(boardPackage) != null &&
                footprintRegistry.getProvider(partPackage) != null &&
                renderRegistry.hasProvider(boardPackage) &&
                renderRegistry.hasProvider(partPackage),
            "equivalent package definitions did not resolve through standard registries");
        verifyConflictingPackageRejected(footprintRegistry, renderRegistry, conflictingPackage);

        TroubleshootBoard board = createFutureBoard(boardPackage);
        PcbBoardLayout layout = new SeededPcbLayoutGenerator(footprintRegistry).generate(board,
            35035);
        layout.validateGeometry(board);
        PcbComponentPlacement placement = layout.getComponent("U35");
        require(placement != null, "future package footprint omitted its component");
        HashMap<String, Point> padPoints = new HashMap<String, Point>();
        for (String padId : board.getComponent("U35").getPadIds()) {
            PcbPadPlacement pad = layout.getPad(padId);
            require(pad != null, "future package footprint omitted pad: " + padId);
            padPoints.put(padId, new Point(pad.getX(), pad.getY()));
        }

        PhysicalBoardRuntime runtime = new PhysicalBoardRuntime(board);
        runtime.createSlot("PWR_IN");
        PhysicalBoardSlot slot = runtime.createSlot("U35");
        final Vector<CircuitElm> backingElements = new Vector<CircuitElm>();
        final FuturePhysicalSpecification specification = new FuturePhysicalSpecification(
            "FUTURE_TASK35_SPEC", "future-material", "F3", 0.15);
        PhysicalPartInventory<PhysicalPart<FuturePhysicalSpecification>> inventory =
            new PhysicalPartInventory<PhysicalPart<FuturePhysicalSpecification>>(runtime,
                "TASK35_FUTURE_INVENTORY",
                new PhysicalPartTypeAdapter<PhysicalPart<FuturePhysicalSpecification>>() {
                    @SuppressWarnings("unchecked")
                    public PhysicalPart<FuturePhysicalSpecification> require(PhysicalPart<?> part) {
                        if (part == null || !(part.getSpecification() instanceof
                                FuturePhysicalSpecification))
                            throw new IllegalArgumentException("Task 35 future inventory type mismatch");
                        return (PhysicalPart<FuturePhysicalSpecification>) part;
                    }
                });
        PhysicalPartIdentityFactory<PhysicalPart<FuturePhysicalSpecification>> factory =
            new PhysicalPartIdentityFactory<PhysicalPart<FuturePhysicalSpecification>>() {
                public PhysicalPart<FuturePhysicalSpecification> create(String physicalPartId) {
                    return createFuturePart(sim, physicalPartId, partPackage, specification,
                        backingElements);
                }
            };
        PhysicalPart<FuturePhysicalSpecification> first = null;
        try {
            first = inventory.acquire("TASK35_FUTURE_PART", factory);
            PhysicalPart<FuturePhysicalSpecification> second = inventory.acquire(
                "TASK35_FUTURE_PART", factory);
            require(!first.getId().equals(second.getId()) && inventory.size() == 2 &&
                    inventory.get(first.getId()) == first && inventory.get(second.getId()) == second &&
                    inventory.getLooseParts().size() == 2,
                "runtime-owned future inventory did not allocate distinct stable identities");
            require(first.getSpecification() == specification &&
                    first.getPackage().isEquivalentTo(boardPackage) &&
                    first.getTerminalCount() == 3 && first.getOrientation() ==
                        PhysicalPartOrientation.NON_POLARIZED,
                "future physical part lost specification/package/orientation identity");
            require(first.getPlayerVisibleNameplate() != null &&
                    !first.getPlayerVisibleNameplate().getId().equals(
                        specification.getSpecificationId()) &&
                    "F3".equals(first.getPlayerVisibleNameplate().getWorkbenchDetailValue()) &&
                    "future-material".equals(specification.getArbitraryField()),
                "future physical part did not preserve private spec and player nameplate separation");
            require(WorkbenchCapabilityDiscovery.supportsOperation(first, "INSPECT_LOOSE"),
                "generic physical capability discovery did not reach future part");
            for (int terminal = 0; terminal < first.getTerminalCount(); terminal++) {
                PhysicalPartTerminal value = first.getTerminal(terminal);
                require(value.getId().equals(first.getId() + "." + (terminal + 1)) &&
                        value.getTerminalName().equals(String.valueOf(terminal + 1)) &&
                        value.getEndpoint() != null,
                    "future terminal identity was not stable: " + terminal);
            }
            new CirSimTroubleshootSimulationFacade(sim).validateBacking(first);
            new CirSimTroubleshootSimulationFacade(sim).validateBacking(second);

            slot.install(first);
            runtime.validate();
            require(first.isInstalled() && slot.getInstalledPart() == first &&
                    inventory.getLooseParts().size() == 1,
                "future part did not move into the installed slot without changing identity");
            verifyInstalledRender(sim, renderRegistry, board, first, placement, padPoints);

            PhysicalPart<?> removed = slot.remove();
            require(removed == first && removed.getId().equals(first.getId()) &&
                    !first.isInstalled() && inventory.get(first.getId()) == first &&
                    inventory.getLooseParts().size() == 2,
                "future part loose transition changed physical identity");
            verifyLooseRender(sim, renderRegistry, first);
            slot.install(first);
            runtime.validate();
            require(slot.getInstalledPart() == first && first.isInstalled() &&
                    first.getTerminal(0).getId().equals(first.getId() + ".1"),
                "future part reinstall did not preserve terminal identity");
        } finally {
            for (CircuitElm element : backingElements)
                while (sim.elmList.remove(element)) {
                }
        }
    }

    private static void verifyInstalledRender(CirSim sim, PhysicalPartRenderRegistry registry,
            TroubleshootBoard board, PhysicalPart<?> part, PcbComponentPlacement placement,
            HashMap<String, Point> padPoints) {
        PhysicalPartRenderCanaryResult result = sim.pcbWorkbenchController.getRenderer()
            .renderProviderCanaryForDeveloperVerification(sim, new Graphics(sim.backcontext), board,
                part, placement, padPoints);
        require(result.wasBodyDrawn() && result.getGeometry().getTerminals().size() == 3 &&
                result.getGeometry().getSelectionBounds() != null &&
                registry.getProvider(part.getPackage()) != null,
            "equivalent future package did not render through installed provider path");
        for (ProbeTarget target : result.getProbeTargets())
            require(target != null && target.isValid(),
                "equivalent future package installed probe path was invalid");
    }

    private static void verifyLooseRender(CirSim sim, PhysicalPartRenderRegistry registry,
            PhysicalPart<?> part) {
        PhysicalPartRenderProvider provider = registry.getProvider(part.getPackage());
        PhysicalPartRenderContext context = new PhysicalPartRenderContext(
            sim.pcbWorkbenchController.getRenderer(), null, part, part.getPackage(), 0, true);
        PhysicalPartRenderGeometry geometry = provider.getRenderer(part).getLooseGeometry(context);
        require(geometry != null && geometry.getTerminals().size() == 3 &&
                geometry.getTerminals().get(0).getTerminalId().equals("1") &&
                geometry.getTerminals().get(2).getTerminalId().equals("3"),
            "equivalent future package did not render through loose provider path");
    }

    private static PhysicalPart<FuturePhysicalSpecification> createFuturePart(CirSim sim,
            String id, PhysicalPackage physicalPackage, FuturePhysicalSpecification specification,
            Vector<CircuitElm> ownedElements) {
        Vector<PhysicalPartTerminal> terminals = new Vector<PhysicalPartTerminal>();
        Vector<CircuitElm> backing = new Vector<CircuitElm>();
        int baseX = 32 + ownedElements.size() * 48;
        for (int index = 0; index < physicalPackage.getTerminalCount(); index++) {
            WireElm wire = new WireElm(baseX + index * 48, 700);
            wire.drag(baseX + index * 48 + 16, 700);
            sim.elmList.add(wire);
            ownedElements.add(wire);
            backing.add(wire);
            terminals.add(new PhysicalPartTerminal(id, physicalPackage.getTerminalIds().get(index),
                new CircuitPostMeasurementEndpoint(wire, 0)));
        }
        Vector<PhysicalPartCapability> capabilities = new Vector<PhysicalPartCapability>();
        capabilities.add(new LoosePartInspectableCapability());
        return new FixedPhysicalPart<FuturePhysicalSpecification>(id, specification,
            new PhysicalNameplate(id, "Future part", "Marking", "F3"), physicalPackage,
            terminals, backing,
            new PhysicalPartProvenance(PhysicalPartProvenance.DEVELOPER_CANARY, "TASK35"),
            null, capabilities);
    }

    private static TroubleshootBoard createFutureBoard(PhysicalPackage physicalPackage) {
        TroubleshootBoard board = new TroubleshootBoard("TASK35_FUTURE_BOARD");
        board.addNet(new BoardNet("TASK35_POSITIVE"));
        board.addNet(new BoardNet("TASK35_NEGATIVE"));
        board.addComponent(new BoardComponent("PWR_IN", "CONNECTOR",
            PhysicalPackages.THROUGH_HOLE_CONNECTOR_2));
        board.addComponent(new BoardComponent("U35", "TASK35_FUTURE", physicalPackage));
        board.addPad(new BoardPad("PWR_IN.1", "PWR_IN", "1", "TASK35_POSITIVE"));
        board.addPad(new BoardPad("PWR_IN.2", "PWR_IN", "2", "TASK35_NEGATIVE"));
        board.addPad(new BoardPad("U35.1", "U35", "1", "TASK35_POSITIVE"));
        board.addPad(new BoardPad("U35.2", "U35", "2", "TASK35_POSITIVE"));
        board.addPad(new BoardPad("U35.3", "U35", "3", "TASK35_NEGATIVE"));
        board.validate();
        return board;
    }

    private static PhysicalPackage futurePackage() {
        Vector<String> terminals = new Vector<String>();
        terminals.add("1"); terminals.add("2"); terminals.add("3");
        Vector<String> connections = new Vector<String>();
        connections.add("1=2");
        connections.add("2=3");
        return new PhysicalPackage("DEV_CANARY_3_ORDERED", terminals, connections, false);
    }

    private static PhysicalPackage reversedFuturePackage() {
        Vector<String> terminals = new Vector<String>();
        terminals.add("1"); terminals.add("2"); terminals.add("3");
        Vector<String> connections = new Vector<String>();
        connections.add("2=3");
        connections.add("1=2");
        return new PhysicalPackage("DEV_CANARY_3_ORDERED", terminals, connections, false);
    }

    private static PhysicalPackage conflictingFuturePackage() {
        Vector<String> terminals = new Vector<String>();
        terminals.add("1"); terminals.add("2"); terminals.add("3");
        return new PhysicalPackage("DEV_CANARY_3_ORDERED", terminals, new Vector<String>(), false);
    }

    private static void verifyConflictingPackageRejected(PcbFootprintRegistry footprintRegistry,
            PhysicalPartRenderRegistry renderRegistry, PhysicalPackage conflictingPackage) {
        String firstFootprintMessage = null;
        String secondFootprintMessage = null;
        try {
            footprintRegistry.getProvider(conflictingPackage);
        } catch (IllegalArgumentException exception) {
            firstFootprintMessage = exception.getMessage();
        }
        try {
            footprintRegistry.getProvider(conflictingPackage);
        } catch (IllegalArgumentException exception) {
            secondFootprintMessage = exception.getMessage();
        }
        require(firstFootprintMessage != null && firstFootprintMessage.equals(secondFootprintMessage),
            "conflicting PCB package definition was not rejected deterministically");

        String firstRenderMessage = null;
        String secondRenderMessage = null;
        try {
            renderRegistry.getProvider(conflictingPackage);
        } catch (IllegalArgumentException exception) {
            firstRenderMessage = exception.getMessage();
        }
        try {
            renderRegistry.getProvider(conflictingPackage);
        } catch (IllegalArgumentException exception) {
            secondRenderMessage = exception.getMessage();
        }
        require(firstRenderMessage != null && firstRenderMessage.equals(secondRenderMessage),
            "conflicting render package definition was not rejected deterministically");
    }

    private static void require(boolean condition, String message) {
        if (!condition)
            throw new IllegalStateException(message);
    }

    private static final class FuturePhysicalSpecification implements PhysicalSpecification {
        private final String id;
        private final String arbitraryField;
        private final String markingFamily;
        private final double ratingValue;

        FuturePhysicalSpecification(String id, String arbitraryField, String markingFamily,
                double ratingValue) {
            if (id == null || id.length() == 0 || arbitraryField == null ||
                    arbitraryField.length() == 0 || markingFamily == null ||
                    markingFamily.length() == 0 || ratingValue <= 0)
                throw new IllegalArgumentException("Invalid Task 35 future specification");
            this.id = id;
            this.arbitraryField = arbitraryField;
            this.markingFamily = markingFamily;
            this.ratingValue = ratingValue;
        }

        public String getSpecificationId() { return id; }
        public Vector<PhysicalRating> getRatings() {
            Vector<PhysicalRating> result = new Vector<PhysicalRating>();
            result.add(new PowerRating(ratingValue));
            return result;
        }
        String getArbitraryField() { return arbitraryField; }
        String getMarkingFamily() { return markingFamily; }
    }
}
