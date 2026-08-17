package com.lushprojects.circuitjs1.client;

import java.util.HashMap;
import java.util.Vector;

/** Developer-only checks for provider discovery and provider-owned geometry. */
final class PhysicalPartRenderDeveloperVerifier {
    private PhysicalPartRenderDeveloperVerifier() { }

    static void verify(CirSim sim) {
        if (sim == null || sim.pcbWorkbenchController == null)
            throw new IllegalStateException("Physical render verification requires a workbench");
        PcbWorkbenchRenderer renderer = sim.pcbWorkbenchController.getRenderer();
        if (sim.backcontext == null || sim.circuitArea == null)
            throw new IllegalStateException("Physical render verification requires a canvas");
        Graphics graphics = new Graphics(sim.backcontext);
        PhysicalPartRenderRegistry registry = renderer.getRenderRegistryForDeveloperVerification();
        GeneratedBoardInstance instance = sim.getGeneratedBoardInstance();
        for (String componentId : instance.getBoard().getComponentIds()) {
            BoardComponent component = instance.getBoard().getComponent(componentId);
            require(registry.hasProvider(component.getPhysicalPackage()),
                "Missing render provider for production package: " +
                    component.getPhysicalPackage().getId());
            PhysicalPart<?> part = instance.getPhysicalBoardRuntime().getInstalledPart(componentId);
            if (part != null && isFixedProductionBodyPackage(component.getPhysicalPackage()) &&
                    part.getProvenance() != null &&
                    PhysicalPartProvenance.FIXED_GENERATED.equals(
                        part.getProvenance().getKind())) {
                require(
                        part.getRenderMetadata() != null &&
                        part.getRenderMetadata().getVisualSpecification() == part.getSpecification(),
                    "Fixed production visual metadata was not retained: " + componentId);
                require(renderer.drawInstalledForDeveloperVerification(sim, graphics, componentId),
                    "Fixed production body was not drawn: " + componentId);
            }
            PhysicalPartRenderGeometry geometry = renderer
                .getInstalledGeometryForDeveloperVerification(componentId);
            require(geometry != null && geometry.getTerminals().size() ==
                    component.getPhysicalPackage().getTerminalCount(),
                "Production provider did not expose all terminals: " + componentId);
            if (part != null)
                require(geometry.getTerminals().size() == part.getTerminalCount(),
                    "Installed part/provider terminal count disagrees: " + componentId);
            Rectangle selection = geometry.getSelectionBounds();
            require(geometry.contains(selection.x + selection.width / 2,
                    selection.y + selection.height / 2),
                "Provider selection geometry was not used for component hit testing: " +
                    componentId);
            renderer.setSelectedComponentId(componentId);
            String hit = renderer.findComponentId(selection.x + selection.width / 2,
                selection.y + selection.height / 2);
            if (part != null || instance.getPhysicalBoardRuntime().getMutationProvider(componentId) == null)
                require(componentId.equals(hit),
                    "Generic renderer did not consume provider hit geometry: " + componentId);
            for (PhysicalPartRenderTerminal terminal : geometry.getTerminals()) {
                require(terminal.getPoint() != null,
                    "Provider omitted terminal point: " + componentId + "." +
                        terminal.getTerminalId());
                if (terminal.getBoardPadId() != null)
                    require(renderer.getComponentLeadPoint(componentId,
                            terminal.getBoardPadId()) != null,
                        "Generic renderer did not consume provider lead geometry: " +
                            terminal.getBoardPadId());
            }
        }
        renderer.setSelectedComponentId(null);
        verifyTerminalCountCanaries(sim, renderer, registry);
        verifyLooseProbeProviderDispatch(sim, renderer, registry);
    }

    private static void verifyLooseProbeProviderDispatch(CirSim sim,
            PcbWorkbenchRenderer renderer, PhysicalPartRenderRegistry registry) {
        for (PhysicalPart<?> part : renderer.getVisibleLoosePhysicalParts()) {
            PhysicalPartRenderMetadata metadata = part.getRenderMetadata();
            require(metadata != null && metadata.getLooseProbeProvider() != null,
                "Loose production part did not expose typed probe metadata: " + part.getId());
            PhysicalPartRenderProvider provider = registry.getProvider(part.getPackage());
            require(provider != null, "Loose production part has no package provider: " +
                part.getId());
            PhysicalPartRenderer partRenderer = provider.getRenderer(part);
            PhysicalPartRenderContext context = new PhysicalPartRenderContext(renderer, null, part,
                part.getPackage(), 0, true);
            PhysicalPartRenderGeometry geometry = partRenderer.getLooseGeometry(context);
            for (PhysicalPartRenderTerminal terminal : geometry.getTerminals()) {
                ProbeTarget target = partRenderer.createLooseProbeTarget(sim, context,
                    terminal.getTerminalIndex());
                require(target != null && target.isValid(),
                    "Typed loose probe provider returned an invalid target: " + part.getId());
                require(target.getMeasurementEndpoint() == part.getTerminal(
                    terminal.getTerminalIndex()).getEndpoint(),
                    "Loose provider changed the physical terminal endpoint: " + part.getId());
                requireSpecializedLooseTarget(part.getPackage(), target, part.getId());
            }
        }
    }

    private static void requireSpecializedLooseTarget(PhysicalPackage physicalPackage,
            ProbeTarget target, String partId) {
        if (physicalPackage == PhysicalPackages.AXIAL_RESISTOR)
            require(target instanceof PhysicalResistorPartProbeTarget,
                "Resistor loose probe did not resolve through its provider: " + partId);
        else if (physicalPackage == PhysicalPackages.AXIAL_DIODE)
            require(target instanceof PhysicalDiodePartProbeTarget,
                "Diode loose probe did not resolve through its provider: " + partId);
        else if (physicalPackage == PhysicalPackages.THROUGH_HOLE_LED)
            require(target instanceof PhysicalLedPartProbeTarget,
                "LED loose probe did not resolve through its provider: " + partId);
    }

    private static void verifyTerminalCountCanaries(CirSim sim, PcbWorkbenchRenderer renderer,
            PhysicalPartRenderRegistry registry) {
        for (int terminalCount = 3; terminalCount <= 6; terminalCount++) {
            RenderCanaryFixture fixture = RenderCanaryFixture.create(sim, renderer, terminalCount);
            try {
                PhysicalPackage physicalPackage = fixture.part.getPackage();
                require(registry.hasProvider(physicalPackage),
                    "Missing render canary provider: " + terminalCount);
                PhysicalPartRenderCanaryResult result = renderer
                    .renderProviderCanaryForDeveloperVerification(fixture.sim, fixture.graphics,
                        fixture.board, fixture.part, fixture.placement, fixture.padPoints);
                PhysicalPartRenderGeometry geometry = result.getGeometry();
                require(result.wasBodyDrawn(),
                    "Render canary provider did not draw its body: " + terminalCount);
                require(geometry.getTerminals().size() == terminalCount,
                    "Render canary provider lost terminal count: " + terminalCount);
                Rectangle selection = geometry.getSelectionBounds();
                require(geometry.contains(selection.x + selection.width / 2,
                        selection.y + selection.height / 2) &&
                        fixture.componentId.equals(result.getHitComponentId()),
                    "Render canary provider body hit path was not used: " + terminalCount);
                Vector<PhysicalPartRenderTerminal> terminals = geometry.getTerminals();
                Vector<ProbeTarget> probeTargets = result.getProbeTargets();
                require(probeTargets.size() == terminalCount,
                    "Render canary probe path lost terminals: " + terminalCount);
                for (int index = 0; index < terminalCount; index++) {
                    PhysicalPartRenderTerminal terminal = terminals.get(index);
                    require(terminal.getTerminalIndex() == index &&
                            terminal.getTerminalId().equals(fixture.part.getTerminal(index)
                                .getTerminalName()) &&
                            terminal.getBoardPadId().equals(fixture.componentId + "." +
                                terminal.getTerminalId()) && terminal.getPoint() != null,
                        "Render canary provider terminal identity failed: " + terminalCount);
                    require(probeTargets.get(index) instanceof PhysicalPartRenderCanaryProbeTarget,
                        "Render canary probe target did not come from provider path: " +
                            terminalCount);
                    PhysicalPartRenderCanaryProbeTarget target =
                        (PhysicalPartRenderCanaryProbeTarget) probeTargets.get(index);
                    require(target.isValid() && target.getPartIdForDeveloperVerification().equals(
                            fixture.part.getId()) &&
                            target.getTerminalIndexForDeveloperVerification() == index &&
                            target.getTerminalIdForDeveloperVerification().equals(
                                terminal.getTerminalId()) &&
                            target.getBoardPadIdForDeveloperVerification().equals(
                                terminal.getBoardPadId()),
                        "Render canary probe target identity failed: " + terminalCount);
                }
            } finally {
                fixture.dispose();
            }
        }
    }

    private static boolean isFixedProductionBodyPackage(PhysicalPackage physicalPackage) {
        return physicalPackage == PhysicalPackages.AXIAL_RESISTOR ||
            physicalPackage == PhysicalPackages.AXIAL_DIODE ||
            physicalPackage == PhysicalPackages.THROUGH_HOLE_LED;
    }

    private static PhysicalPackage packageFor(int terminalCount) {
        if (terminalCount == 3) return PhysicalPackages.DEV_CANARY_3;
        if (terminalCount == 4) return PhysicalPackages.DEV_CANARY_4;
        if (terminalCount == 5) return PhysicalPackages.DEV_CANARY_5;
        return PhysicalPackages.DEV_CANARY_6;
    }

    private static final class RenderCanaryFixture {
        private final CirSim sim;
        private final TroubleshootBoard board;
        private final String componentId;
        private final FixedPhysicalPart<BasicPhysicalSpecification> part;
        private final PcbComponentPlacement placement;
        private final HashMap<String, Point> padPoints;
        private final Graphics graphics;
        private final Vector<CircuitElm> backingElements;

        private RenderCanaryFixture(CirSim sim, TroubleshootBoard board, String componentId,
                FixedPhysicalPart<BasicPhysicalSpecification> part,
                PcbComponentPlacement placement, HashMap<String, Point> padPoints,
                Graphics graphics, Vector<CircuitElm> backingElements) {
            this.sim = sim;
            this.board = board;
            this.componentId = componentId;
            this.part = part;
            this.placement = placement;
            this.padPoints = padPoints;
            this.graphics = graphics;
            this.backingElements = backingElements;
        }

        static RenderCanaryFixture create(CirSim sim, PcbWorkbenchRenderer renderer,
                int terminalCount) {
            if (sim == null || sim.backcontext == null)
                throw new IllegalStateException("Render canary requires the active canvas");
            String componentId = "RENDER_CANARY_" + terminalCount;
            PhysicalPackage physicalPackage = packageFor(terminalCount);
            TroubleshootBoard board = new TroubleshootBoard(componentId);
            BoardComponent component = new BoardComponent(componentId,
                "DEV_CANARY_" + terminalCount, physicalPackage);
            board.addComponent(component);
            for (int index = 1; index <= terminalCount; index++) {
                String netId = componentId + ".NET." + index;
                board.addNet(new BoardNet(netId));
                board.addPad(new BoardPad(componentId + "." + index, componentId,
                    String.valueOf(index), netId));
            }
            board.validate();
            PhysicalBoardRuntime runtime = new PhysicalBoardRuntime(board);
            PhysicalBoardSlot slot = runtime.createSlot(componentId);
            Vector<PhysicalPartTerminal> terminals = new Vector<PhysicalPartTerminal>();
            Vector<CircuitElm> backingElements = new Vector<CircuitElm>();
            int baseX = 24 + terminalCount * 10;
            int baseY = 24 + terminalCount * 12;
            for (int index = 0; index < terminalCount; index++) {
                WireElm wire = new WireElm(baseX + index * 32, baseY);
                wire.drag(baseX + index * 32 + 16, baseY);
                sim.elmList.add(wire);
                backingElements.add(wire);
                terminals.add(new PhysicalPartTerminal(componentId,
                    physicalPackage.getTerminalIds().get(index),
                    new CircuitPostMeasurementEndpoint(wire, 0)));
            }
            FixedPhysicalPart<BasicPhysicalSpecification> part =
                new FixedPhysicalPart<BasicPhysicalSpecification>(componentId,
                    new BasicPhysicalSpecification(componentId + "_SPEC"),
                    new PhysicalNameplate(componentId, "Developer render canary"),
                    physicalPackage, terminals, backingElements,
                    new PhysicalPartProvenance(PhysicalPartProvenance.DEVELOPER_CANARY,
                        componentId));
            slot.install(part);
            runtime.validate();
            Rectangle outline = renderer.getLayoutForProvider().getBoardOutline();
            int width = 180;
            int height = 100 + terminalCount * 5;
            int x = outline.x + Math.max(20, (outline.width - width) / 2);
            int y = outline.y + Math.max(20, (outline.height - height) / 2);
            PcbComponentPlacement placement = new PcbComponentPlacement(componentId, x, y,
                width, height);
            HashMap<String, Point> padPoints = new HashMap<String, Point>();
            for (int index = 0; index < terminalCount; index++)
                padPoints.put(componentId + "." + (index + 1),
                    new Point(x + 20 + index * 24, y + height / 2));
            return new RenderCanaryFixture(sim, board, componentId, part, placement, padPoints,
                new Graphics(sim.backcontext), backingElements);
        }

        void dispose() {
            for (CircuitElm element : backingElements)
                while (sim.elmList.remove(element)) {
                }
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition)
            throw new IllegalStateException(message);
    }
}
