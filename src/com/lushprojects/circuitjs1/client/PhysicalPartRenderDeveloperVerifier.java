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
            PcbComponentPlacement placement = renderer.getLayoutForProvider()
                .getComponent(componentId);
            require(placement != null && placement.getPhysicalGeometry() != null &&
                    component.getPhysicalPackage().acceptsGeometry(
                        placement.getPhysicalGeometry()),
                "Installed provider lost the declared package geometry: " + componentId);
            PhysicalPackageGeometry.Placement placed = placement.getPhysicalGeometry().placedAt(
                placement.getX(), placement.getY());
            require(geometry.getBodyBounds().equals(renderer.screenRectForProvider(
                        placed.getBodyBounds())) &&
                    geometry.getSelectionBounds().equals(renderer.screenRectForProvider(
                        placed.getSelectionEnvelope())) &&
                    geometry.getDragBounds().equals(renderer.screenRectForProvider(
                        placed.getDragEnvelope())),
                "Installed provider diverged from package envelope identity: " + componentId);
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
                int terminalIndex = terminal.getTerminalIndex();
                PhysicalPackageGeometry.Terminal declared = placement.getPhysicalGeometry()
                    .getTerminal(terminalIndex);
                boolean connected = instance.getConnectionBindings()
                    .getForComponentOrEmpty(componentId).isEmpty() ||
                    sim.getBoardModificationController().isLeadConnected(componentId,
                        terminal.getBoardPadId());
                boolean lifted = !connected;
                require(declared != null && declared.getTerminalId().equals(
                        terminal.getTerminalId()) && terminal.getBoardPadId() != null &&
                        terminal.getPadBounds().equals(renderer.screenRectForProvider(
                            placed.getPadBounds(terminalIndex))) &&
                        pointEquals(terminal.getBoardPadPoint(), renderer.screenPointForProvider(
                            placed.getBoardPadProbeCenter(terminalIndex))) &&
                        terminal.getBoardPadProbeBounds().equals(renderer.screenRectForProvider(
                            placed.getBoardPadProbeBounds(terminalIndex))) &&
                        pointEquals(terminal.getComponentLeadPoint(), renderer.screenPointForProvider(
                            placed.getComponentLeadProbeCenter(terminalIndex, lifted))) &&
                        terminal.getComponentLeadProbeBounds().equals(renderer.screenRectForProvider(
                            placed.getComponentLeadProbeBounds(terminalIndex, lifted))) &&
                        pointEquals(terminal.getLeadBodyPoint(), renderer.screenPointForProvider(
                            placed.getLeadBodyPoint(terminalIndex, lifted))) &&
                        pointEquals(terminal.getLeadEndPoint(), renderer.screenPointForProvider(
                            placed.getLeadEndPoint(terminalIndex, lifted))) &&
                        terminal.getProbeBounds().equals(renderer.screenRectForProvider(
                            lifted ? placed.getComponentLeadProbeBounds(terminalIndex, true) :
                                placed.getBoardPadProbeBounds(terminalIndex))) &&
                        terminal.getLeadBounds().equals(renderer.screenRectForProvider(
                            placed.getLeadBounds(terminalIndex, lifted))),
                    "Installed provider diverged from package terminal geometry: " +
                        componentId + "." + terminal.getTerminalId());
                if (connected)
                    require(renderer.getComponentLeadPoint(componentId,
                            terminal.getBoardPadId()) == null,
                        "Connected lead exposed a component-side target: " +
                            terminal.getBoardPadId());
                else
                    require(renderer.getComponentLeadPoint(componentId,
                            terminal.getBoardPadId()) != null,
                        "Renderer omitted disconnected component-side lead: " +
                            terminal.getBoardPadId());
            }
        }
        renderer.setSelectedComponentId(null);
        verifyTerminalCountCanaries(sim, renderer, registry);
        verifyLooseProbeProviderDispatch(sim, renderer, registry);
        verifyConnectedAndLiftedProbeSemantics(sim, renderer);
    }

    private static void verifyLooseProbeProviderDispatch(CirSim sim,
            PcbWorkbenchRenderer renderer, PhysicalPartRenderRegistry registry) {
        Vector<PhysicalPart<?>> visibleParts = renderer.getVisibleLoosePhysicalParts();
        for (int partIndex = 0; partIndex < visibleParts.size(); partIndex++) {
            PhysicalPart<?> part = visibleParts.get(partIndex);
            PhysicalPartRenderMetadata metadata = part.getRenderMetadata();
            require(metadata != null && metadata.getLooseProbeProvider() != null,
                "Loose production part did not expose typed probe metadata: " + part.getId());
            PhysicalPartRenderProvider provider = registry.getProvider(part.getPackage());
            require(provider != null, "Loose production part has no package provider: " +
                part.getId());
            PhysicalPartRenderer partRenderer = provider.getRenderer(part);
            PhysicalPartRenderContext context = new PhysicalPartRenderContext(renderer, null, part,
                part.getPackage(), partIndex, true);
            PhysicalPartRenderGeometry geometry = partRenderer.getLooseGeometry(context);
            for (PhysicalPartRenderTerminal terminal : geometry.getTerminals()) {
                Point terminalPoint = terminal.getPoint();
                require(terminal.containsProbe(terminalPoint.x, terminalPoint.y) &&
                        geometry.contains(terminalPoint.x, terminalPoint.y),
                    "Loose geometry lost the declared terminal probe envelope: " +
                        part.getId());
                ProbeTarget target = partRenderer.createLooseProbeTarget(sim, context,
                    terminal.getTerminalIndex());
                require(target != null && target.isValid(),
                    "Typed loose probe provider returned an invalid target: " + part.getId());
                ProbeTarget hit = renderer.findProbeTarget(sim, terminalPoint.x, terminalPoint.y);
                require(hit != null && hit.isValid() && hit.isSameTarget(target),
                    "Loose renderer hit path diverged from provider probe geometry: " +
                        part.getId());
                require(target.getMeasurementEndpoint() == part.getTerminal(
                    terminal.getTerminalIndex()).getEndpoint(),
                    "Loose provider changed the physical terminal endpoint: " + part.getId());
                requireSpecializedLooseTarget(part.getPackage(), target, part.getId());
            }
        }
    }

    private static void verifyConnectedAndLiftedProbeSemantics(CirSim sim,
            PcbWorkbenchRenderer renderer) {
        GeneratedBoardInstance instance = sim.getGeneratedBoardInstance();
        BoardModificationController modifications = sim.getBoardModificationController();
        PhysicalBoardRuntime runtime = instance.getPhysicalBoardRuntime();
        GeneratedComponentConnectionBinding candidate = null;
        PhysicalPart<?> part = null;
        for (GeneratedComponentConnectionBinding binding : instance.getConnectionBindings().getAll()) {
            PhysicalPart<?> installed = instance.getPhysicalBoardRuntime().getInstalledPart(
                binding.getComponentId());
            if (installed != null && installed.isInstalled() &&
                    runtime.getMutationProvider(binding.getComponentId()) != null &&
                    modifications.getComponentState(binding.getComponentId()) ==
                        ComponentPhysicalState.INSTALLED) {
                candidate = binding;
                part = installed;
                break;
            }
        }
        require(candidate != null && part != null,
            "No installed named lead was available for render probe semantics");
        PhysicalSlotMutationProvider mutationProvider = runtime.getMutationProvider(
            candidate.getComponentId());
        PhysicalBoardSlot slot = runtime.getSlot(candidate.getComponentId());
        require(mutationProvider != null && slot != null && slot.getInstalledPart() == part,
            "Render probe lifecycle canary has no physical slot provider");

        Point padPoint = renderer.getPadPoint(candidate.getPadId());
        require(padPoint != null, "Candidate board pad has no rendered point: " +
            candidate.getPadId());
        ProbeTarget connectedPad = renderer.findProbeTarget(sim, padPoint.x, padPoint.y);
        require(connectedPad instanceof BoardPadProbeTarget && connectedPad.isValid() &&
                candidate.getPadId().equals(((BoardPadProbeTarget) connectedPad).getPadId()),
            "Connected board pad did not resolve as a board target: " + candidate.getPadId());

        ComponentLeadProbeTarget connectedLead = new ComponentLeadProbeTarget(sim, instance,
            candidate.getComponentId(), candidate.getPadId(), renderer, part.getId(),
            candidate.getComponentEndpoint());
        require(!connectedLead.isValid(),
            "Connected named lead was accepted as a component-side target");

        BoardPowerController power = sim.getBoardPowerController();
        BoardPowerState savedPower = power.getState();
        PhysicalGeometryRealization carrier = part.getGeometryRealization();
        PhysicalPartTerminal stableTerminal = findPartTerminal(part,
            instance.getBoard().getPad(candidate.getPadId()).getTerminalId());
        require(carrier != null && stableTerminal != null,
            "Render probe lifecycle canary lost physical identity before mutation");
        String stablePartId = part.getId();
        CircuitMeasurementEndpoint stableEndpoint = stableTerminal.getEndpoint();
        try {
            if (!power.isElectricallyUnpowered())
                power.setState(BoardPowerState.UNPOWERED);
            require(power.isElectricallyUnpowered(),
                "Render probe canary could not establish safe unpowered mutation state");
            require(modifications.liftLead(candidate.getComponentId(), candidate.getPadId()),
                "Render probe canary could not lift named lead");

            PhysicalPartRenderGeometry geometry = renderer
                .getInstalledGeometryForDeveloperVerification(candidate.getComponentId());
            PhysicalPartRenderTerminal terminal = findTerminal(geometry, candidate.getPadId());
            require(terminal != null && renderer.getComponentLeadPoint(candidate.getComponentId(),
                    candidate.getPadId()) != null,
                "Lifted named lead lost its package component-side geometry");
            Point componentSide = terminal.getComponentLeadPoint();
            Rectangle boardPadProbe = terminal.getBoardPadProbeBounds();
            require(componentSide != null && terminal.getComponentLeadProbeBounds().contains(
                    componentSide.x, componentSide.y) && (boardPadProbe == null ||
                    !boardPadProbe.contains(componentSide.x, componentSide.y)) &&
                    pointEquals(componentSide, renderer.getComponentLeadPoint(
                        candidate.getComponentId(), candidate.getPadId())),
                "Lifted lead did not expose its exact detached component surface");
            ProbeTarget liftedTarget = renderer.findProbeTarget(sim, componentSide.x,
                componentSide.y);
            require(liftedTarget instanceof ComponentLeadProbeTarget,
                "Lifted named lead resolved as the wrong target: " +
                    (liftedTarget == null ? "null" : liftedTarget.getClass().getName()));
            require(liftedTarget.isValid(),
                "Lifted named lead resolved to an invalid component target");
            ComponentLeadProbeTarget componentTarget = (ComponentLeadProbeTarget) liftedTarget;
            require(candidate.getComponentId().equals(
                    componentTarget.getComponentIdForDeveloperVerification()) &&
                    candidate.getPadId().equals(componentTarget.getPadIdForDeveloperVerification()) &&
                    part.getId().equals(componentTarget.getPhysicalPartIdForDeveloperVerification()) &&
                    liftedTarget.getMeasurementEndpoint() == stableEndpoint &&
                    pointEquals(componentTarget.getMarkerPoint(), componentSide),
                "Lifted component target changed stable physical or endpoint identity");
            ComponentLeadProbeTarget equivalent = new ComponentLeadProbeTarget(sim, instance,
                candidate.getComponentId(), candidate.getPadId(), renderer, part.getId(),
                candidate.getComponentEndpoint());
            require(componentTarget.isSameTarget(equivalent),
                "Lifted component target identity was not stable");
            ProbeTarget liftedPad = renderer.findProbeTarget(sim, padPoint.x, padPoint.y);
            require(liftedPad instanceof BoardPadProbeTarget && liftedPad.isValid(),
                "Lifted board pad no longer resolved as a board target");

            require(modifications.reconnectLead(candidate.getComponentId(), candidate.getPadId()),
                "Render probe canary could not reconnect named lead");
            require(!componentTarget.isValid() && renderer.getComponentLeadPoint(
                    candidate.getComponentId(), candidate.getPadId()) == null,
                "Reconnected lead retained a component-side target");
            ProbeTarget reconnectedPad = renderer.findProbeTarget(sim, padPoint.x, padPoint.y);
            require(reconnectedPad instanceof BoardPadProbeTarget && reconnectedPad.isValid(),
                "Reconnected lead did not restore board-pad resolution");

            require(modifications.liftLead(candidate.getComponentId(), candidate.getPadId()),
                "Render probe lifecycle canary could not re-lift before physical removal");
            require(modifications.removeComponent(candidate.getComponentId()) &&
                    modifications.getComponentState(candidate.getComponentId()) ==
                        ComponentPhysicalState.REMOVED && slot.getInstalledPart() == part &&
                    part.isInstalled() && renderer.getComponentLeadPoint(
                        candidate.getComponentId(), candidate.getPadId()) != null &&
                    candidate.getComponentId().equals(renderer.findComponentId(
                        geometry.getBodyBounds().x + geometry.getBodyBounds().width / 2,
                        geometry.getBodyBounds().y + geometry.getBodyBounds().height / 2)) &&
                    liftedPad.isValid() && !componentTarget.isValid(),
                "Graph-only removal was mistaken for final physical slot removal");
            ProbeTarget graphRemovedTarget = renderer.findProbeTarget(sim, componentSide.x,
                componentSide.y);
            require(graphRemovedTarget instanceof ComponentLeadProbeTarget &&
                    graphRemovedTarget.isValid(),
                "Graph-only removal lost the still-mounted component-side target");
            require(mutationProvider.removeInstalledPart() && slot.getInstalledPart() == null &&
                    !part.isInstalled(),
                "Physical removal did not reach final slot-empty state");
            require(liftedPad.isValid() && !componentTarget.isValid() &&
                    !graphRemovedTarget.isValid() &&
                    renderer.getComponentLeadPoint(candidate.getComponentId(),
                        candidate.getPadId()) == null &&
                    renderer.findComponentId(geometry.getBodyBounds().x +
                        geometry.getBodyBounds().width / 2,
                        geometry.getBodyBounds().y + geometry.getBodyBounds().height / 2) == null,
                "Final physical removal did not preserve pad target and invalidate installed target");

            require(mutationProvider.install(stablePartId) && slot.getInstalledPart() == part &&
                    part.isInstalled() && part.getGeometryRealization() == carrier &&
                    findPartTerminal(part, stableTerminal.getTerminalName()) == stableTerminal &&
                    stableTerminal.getEndpoint() == stableEndpoint,
                "Same-part physical reinstall changed stable physical identity");
            require(!componentTarget.isValid() && modifications.getComponentState(
                    candidate.getComponentId()) == ComponentPhysicalState.INSTALLED,
                "Old detached target revived across same-part physical reinstall");
            require(modifications.liftLead(candidate.getComponentId(), candidate.getPadId()),
                "Render probe lifecycle canary could not lift after same-part reinstall");
            PhysicalPartRenderGeometry reinstalledGeometry = renderer
                .getInstalledGeometryForDeveloperVerification(candidate.getComponentId());
            PhysicalPartRenderTerminal reinstalledTerminal = findTerminal(reinstalledGeometry,
                candidate.getPadId());
            Point reinstalledPoint = reinstalledTerminal.getComponentLeadPoint();
            ProbeTarget reinstalledTarget = renderer.findProbeTarget(sim, reinstalledPoint.x,
                reinstalledPoint.y);
            require(reinstalledTarget instanceof ComponentLeadProbeTarget &&
                    reinstalledTarget.isValid() &&
                    stablePartId.equals(((ComponentLeadProbeTarget) reinstalledTarget)
                        .getPhysicalPartIdForDeveloperVerification()) &&
                    reinstalledTarget.getMeasurementEndpoint() == stableEndpoint &&
                    pointEquals(reinstalledTarget.getMarkerPoint(), reinstalledPoint) &&
                    !componentTarget.isSameTarget(reinstalledTarget),
                        "Same-part reinstall did not recreate a distinct exact component target");

            ComponentLeadProbeTarget replacementIdentityTarget = new ComponentLeadProbeTarget(sim,
                instance, candidate.getComponentId(), candidate.getPadId(), renderer,
                stablePartId + "_REPLACEMENT", stableEndpoint);
            require(!replacementIdentityTarget.isValid(),
                "Component target accepted a replaced physical-part identity");
        } finally {
            PhysicalPart<?> installed = slot.getInstalledPart();
            if (installed != null && installed != part)
                mutationProvider.removeInstalledPart();
            if (slot.getInstalledPart() == null)
                mutationProvider.install(stablePartId);
            if (modifications.getComponentState(candidate.getComponentId()) !=
                    ComponentPhysicalState.INSTALLED)
                modifications.restoreComponent(candidate.getComponentId());
            if (savedPower == BoardPowerState.POWERED)
                power.setState(BoardPowerState.POWERED);
            else
                power.setState(BoardPowerState.UNPOWERED);
        }
    }

    private static PhysicalPartRenderTerminal findTerminal(PhysicalPartRenderGeometry geometry,
            String padId) {
        if (geometry == null)
            return null;
        for (PhysicalPartRenderTerminal terminal : geometry.getTerminals())
            if (padId.equals(terminal.getBoardPadId()))
                return terminal;
        return null;
    }

    private static PhysicalPartTerminal findPartTerminal(PhysicalPart<?> part, String terminalName) {
        if (part == null || terminalName == null)
            return null;
        for (PhysicalPartTerminal terminal : part.getTerminals())
            if (terminalName.equals(terminal.getTerminalName()))
                return terminal;
        return null;
    }

    private static Point findComponentSidePoint(Rectangle probeBounds,
            PcbWorkbenchRenderer renderer) {
        for (int y = probeBounds.y; y < probeBounds.y + probeBounds.height; y++)
            for (int x = probeBounds.x; x < probeBounds.x + probeBounds.width; x++) {
                boolean occupiedByBoardPad = false;
                for (PcbPadPlacement pad : renderer.getLayoutForProvider().getPads()) {
                    Rectangle boardProbe = renderer.getPadProbeBoundsForDeveloperVerification(
                        pad.getPadId());
                    if (boardProbe != null && boardProbe.contains(x, y)) {
                        occupiedByBoardPad = true;
                        break;
                    }
                }
                if (!occupiedByBoardPad)
                    return new Point(x, y);
            }
        return null;
    }

    private static void requireSpecializedLooseTarget(PhysicalPackage physicalPackage,
            ProbeTarget target, String partId) {
        if (PhysicalPackages.AXIAL_RESISTOR.isEquivalentTo(physicalPackage))
            require(target instanceof PhysicalResistorPartProbeTarget,
                "Resistor loose probe did not resolve through its provider: " + partId);
        else if (PhysicalPackages.AXIAL_DIODE.isEquivalentTo(physicalPackage))
            require(target instanceof PhysicalDiodePartProbeTarget,
                "Diode loose probe did not resolve through its provider: " + partId);
        else if (PhysicalPackages.THROUGH_HOLE_LED.isEquivalentTo(physicalPackage))
            require(target instanceof PhysicalLedPartProbeTarget,
                "LED loose probe did not resolve through its provider: " + partId);
        else if (PhysicalPackages.RADIAL_ELECTROLYTIC_CAPACITOR.isEquivalentTo(physicalPackage) ||
                PhysicalPackages.RADIAL_CERAMIC_CAPACITOR.isEquivalentTo(physicalPackage))
            require(target instanceof PhysicalCapacitorPartProbeTarget,
                "Capacitor loose probe did not resolve through its provider: " + partId);
    }

    private static void verifyTerminalCountCanaries(CirSim sim, PcbWorkbenchRenderer renderer,
            PhysicalPartRenderRegistry registry) {
        for (int terminalCount = 3; terminalCount <= 6; terminalCount++) {
            RenderCanaryFixture fixture = RenderCanaryFixture.create(sim, renderer, terminalCount);
            try {
                verifyRenderCanary(renderer, registry, fixture,
                    "terminal count " + terminalCount);
            } finally {
                fixture.dispose();
            }
        }
    }

    private static void verifyRenderCanary(PcbWorkbenchRenderer renderer,
            PhysicalPartRenderRegistry registry, RenderCanaryFixture fixture, String label) {
        PhysicalPackage physicalPackage = fixture.part.getPackage();
        require(registry.hasProvider(physicalPackage),
            "Missing render canary provider: " + label);
        PhysicalPartRenderCanaryResult result = renderer
            .renderProviderCanaryForDeveloperVerification(fixture.sim, fixture.graphics,
                fixture.board, fixture.part, fixture.placement, fixture.padPoints);
        PhysicalPartRenderGeometry geometry = result.getGeometry();
        require(result.wasBodyDrawn(), "Render canary provider did not draw its body: " + label);
        require(geometry.getTerminals().size() == physicalPackage.getTerminalCount(),
            "Render canary provider lost terminal count: " + label);
        Rectangle selection = geometry.getSelectionBounds();
        require(geometry.contains(selection.x + selection.width / 2,
                selection.y + selection.height / 2) &&
                fixture.componentId.equals(result.getHitComponentId()) &&
                !geometry.contains(selection.x - 1, selection.y + selection.height / 2),
            "Render canary provider selection/hit path was not used: " + label);
        Vector<PhysicalPartRenderTerminal> terminals = geometry.getTerminals();
        Vector<ProbeTarget> probeTargets = result.getProbeTargets();
        require(probeTargets.size() == physicalPackage.getTerminalCount(),
            "Render canary probe path lost terminals: " + label);
        PhysicalPackageGeometry.Placement placed = fixture.placement.getPhysicalGeometry()
            .placedAt(fixture.placement.getX(), fixture.placement.getY());
        for (int index = 0; index < physicalPackage.getTerminalCount(); index++) {
            PhysicalPartRenderTerminal terminal = terminals.get(index);
            require(terminal.getTerminalIndex() == index &&
                    terminal.getTerminalId().equals(fixture.part.getTerminal(index)
                        .getTerminalName()) &&
                    terminal.getBoardPadId().equals(fixture.componentId + "." +
                        terminal.getTerminalId()) && terminal.getPoint() != null &&
                    pointEquals(terminal.getBoardPadPoint(), renderer.screenPointForProvider(
                        placed.getBoardPadProbeCenter(index))) &&
                    terminal.getBoardPadProbeBounds().equals(renderer.screenRectForProvider(
                        placed.getBoardPadProbeBounds(index))) &&
                    terminal.getLeadBounds().equals(renderer.screenRectForProvider(
                        placed.getLeadBounds(index, false))),
                "Render canary provider terminal geometry failed: " + label);
            require(probeTargets.get(index) instanceof PhysicalPartRenderCanaryProbeTarget,
                "Render canary probe target did not come from provider path: " + label);
            PhysicalPartRenderCanaryProbeTarget target =
                (PhysicalPartRenderCanaryProbeTarget) probeTargets.get(index);
            require(target.isValid() && target.getPartIdForDeveloperVerification().equals(
                    fixture.part.getId()) &&
                    target.getTerminalIndexForDeveloperVerification() == index &&
                    target.getTerminalIdForDeveloperVerification().equals(
                        terminal.getTerminalId()) &&
                    target.getBoardPadIdForDeveloperVerification().equals(
                        terminal.getBoardPadId()) &&
                    pointEquals(target.getMarkerPoint(), terminal.getBoardPadPoint()),
                "Render canary probe target identity failed: " + label);
        }
    }

    private static boolean isFixedProductionBodyPackage(PhysicalPackage physicalPackage) {
        return PhysicalPackages.AXIAL_RESISTOR.isEquivalentTo(physicalPackage) ||
            PhysicalPackages.AXIAL_DIODE.isEquivalentTo(physicalPackage) ||
            PhysicalPackages.THROUGH_HOLE_LED.isEquivalentTo(physicalPackage) ||
            PhysicalPackages.TO92_NPN.isEquivalentTo(physicalPackage) ||
            PhysicalPackages.TO92_NMOS.isEquivalentTo(physicalPackage) ||
            PhysicalPackages.RADIAL_ELECTROLYTIC_CAPACITOR.isEquivalentTo(physicalPackage) ||
            PhysicalPackages.RADIAL_CERAMIC_CAPACITOR.isEquivalentTo(physicalPackage) ||
            PhysicalPackages.THROUGH_HOLE_CONNECTOR_2.isEquivalentTo(physicalPackage) ||
            PhysicalPackages.THROUGH_HOLE_OUTPUT_HEADER_2.isEquivalentTo(physicalPackage);
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
            return create(sim, renderer, "RENDER_CANARY_" + terminalCount,
                packageFor(terminalCount));
        }

        static RenderCanaryFixture create(CirSim sim, PcbWorkbenchRenderer renderer,
                String componentId, PhysicalPackage physicalPackage) {
            if (sim == null || sim.backcontext == null)
                throw new IllegalStateException("Render canary requires the active canvas");
            if (componentId == null || physicalPackage == null)
                throw new IllegalArgumentException("Incomplete render canary package");
            int terminalCount = physicalPackage.getTerminalCount();
            TroubleshootBoard board = new TroubleshootBoard(componentId);
            BoardComponent component = new BoardComponent(componentId,
                physicalPackage.getId(), physicalPackage);
            board.addComponent(component);
            for (int index = 1; index <= terminalCount; index++) {
                String terminalId = physicalPackage.getTerminalIds().get(index - 1);
                String netId = componentId + ".NET." + index;
                board.addNet(new BoardNet(netId));
                board.addPad(new BoardPad(componentId + "." + terminalId, componentId,
                    terminalId, netId));
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
            PhysicalPackageGeometry geometry = physicalPackage.getGeometry();
            int width = geometry.getWidth();
            int height = geometry.getHeight();
            int x = outline.x + Math.max(20, (outline.width - width) / 2);
            int y = outline.y + Math.max(20, (outline.height - height) / 2);
            PcbComponentPlacement placement = PcbComponentPlacement.fromPhysicalGeometry(
                componentId, x, y, physicalPackage, geometry);
            HashMap<String, Point> padPoints = new HashMap<String, Point>();
            PhysicalPackageGeometry.Placement placed = geometry.placedAt(x, y);
            for (int index = 0; index < terminalCount; index++)
                padPoints.put(componentId + "." + physicalPackage.getTerminalIds().get(index),
                    placed.getPadPoint(index));
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

    private static boolean pointEquals(Point first, Point second) {
        return first != null && second != null && first.x == second.x && first.y == second.y;
    }
}
