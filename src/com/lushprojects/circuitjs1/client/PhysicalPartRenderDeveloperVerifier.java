package com.lushprojects.circuitjs1.client;

import java.util.Collections;
import java.util.HashMap;
import java.util.Vector;

import com.google.gwt.dom.client.NativeEvent;

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
        PhysicalBoardRuntime runtime = instance.getPhysicalBoardRuntime();
        for (String componentId : instance.getBoard().getComponentIds()) {
            BoardComponent component = instance.getBoard().getComponent(componentId);
            require(registry.hasProvider(component.getPhysicalPackage()),
                "Missing render provider for production package: " +
                    component.getPhysicalPackage().getId());
            PhysicalPart<?> part = runtime.getInstalledPart(componentId);
            PhysicalBoardSlot slot = runtime.getSlot(componentId);
            // Electrical disconnection is not physical removal.  Keep this
            // dispatch independent so a slot-empty component cannot be
            // mistaken for an installed lifted lead.
            boolean physicallyMounted = part != null && slot != null && part.isInstalled() &&
                slot.getInstalledPart() == part && part.getBoardSlot() == slot;
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
                boolean providerLeadLifted = !connected;
                require(declared != null && declared.getTerminalId().equals(
                        terminal.getTerminalId()) && terminal.getBoardPadId() != null &&
                        terminal.getPadBounds().equals(renderer.screenRectForProvider(
                            placed.getPadBounds(terminalIndex))) &&
                        pointEquals(terminal.getBoardPadPoint(), renderer.screenPointForProvider(
                            placed.getBoardPadProbeCenter(terminalIndex))) &&
                        terminal.getBoardPadProbeBounds().equals(renderer.screenRectForProvider(
                            placed.getBoardPadProbeBounds(terminalIndex))) &&
                        pointEquals(terminal.getComponentLeadPoint(), renderer.screenPointForProvider(
                            placed.getComponentLeadProbeCenter(terminalIndex, providerLeadLifted))) &&
                        terminal.getComponentLeadProbeBounds().equals(renderer.screenRectForProvider(
                            placed.getComponentLeadProbeBounds(terminalIndex, providerLeadLifted))) &&
                        pointEquals(terminal.getLeadBodyPoint(), renderer.screenPointForProvider(
                            placed.getLeadBodyPoint(terminalIndex, providerLeadLifted))) &&
                        pointEquals(terminal.getLeadEndPoint(), renderer.screenPointForProvider(
                            placed.getLeadEndPoint(terminalIndex, providerLeadLifted))) &&
                        terminal.getProbeBounds().equals(renderer.screenRectForProvider(
                            providerLeadLifted ? placed.getComponentLeadProbeBounds(terminalIndex, true) :
                                placed.getBoardPadProbeBounds(terminalIndex))) &&
                        terminal.getLeadBounds().equals(renderer.screenRectForProvider(
                            placed.getLeadBounds(terminalIndex, providerLeadLifted))),
                    "Installed provider diverged from package terminal geometry: " +
                        componentId + "." + terminal.getTerminalId());
                if (physicallyMounted) {
                    if (connected)
                        require(renderer.getComponentLeadPoint(componentId,
                                terminal.getBoardPadId()) == null,
                            "Connected lead exposed a component-side target: " +
                                terminal.getBoardPadId());
                    else
                        require(renderer.getComponentLeadPoint(componentId,
                                terminal.getBoardPadId()) != null,
                            "Renderer omitted mounted disconnected component-side lead: " +
                                terminal.getBoardPadId());
                } else {
                    Point componentPoint = terminal.getComponentLeadPoint();
                    Rectangle boardProbe = terminal.getBoardPadProbeBounds();
                    Rectangle componentProbe = terminal.getComponentLeadProbeBounds();
                    require(renderer.getComponentLeadPoint(componentId,
                            terminal.getBoardPadId()) == null && componentPoint != null &&
                            boardProbe != null && componentProbe != null &&
                            !componentProbe.intersects(boardProbe) &&
                            !boardProbe.contains(componentPoint.x, componentPoint.y),
                        "Physically removed component retained an installed lead surface: " +
                            componentId + "." + terminal.getBoardPadId());
                    ProbeTarget removedHit = renderer.findProbeTarget(sim, componentPoint.x,
                        componentPoint.y);
                    require(removedHit == null,
                        "Physically removed component-side geometry remained reachable: " +
                            componentId + "." + terminal.getBoardPadId());
                }
            }
        }
        renderer.setSelectedComponentId(null);
        verifyTerminalCountCanaries(sim, renderer, registry);
        verifyLooseProbeProviderDispatch(sim, renderer, registry);
        verifyLooseProjectionLifecycle(sim, renderer, registry);
        verifyConnectedAndLiftedProbeSemantics(sim, renderer);
        verifyInstalledNegativeCanaries(sim, registry);
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

    /**
     * Uses a detached runtime so the lifecycle canary has two real tray pages
     * without adding temporary parts to the generated challenge inventory.
     * Targets are always acquired through the renderer hit path and held by
     * the real instrument controller.
     */
    private static void verifyLooseProjectionLifecycle(CirSim sim,
            PcbWorkbenchRenderer originalRenderer, PhysicalPartRenderRegistry registry) {
        require(sim.getGeneratedChallengeController() != null,
            "Loose lifecycle snapshot requires an active generated challenge owner");
        Task41SimulationSnapshot originalSnapshot = Task41SimulationSnapshot.capture(sim);
        LooseProjectionLifecycleFixture fixture = null;
        try {
            originalSnapshot.beginProof(sim);
            fixture = LooseProjectionLifecycleFixture.create(sim, originalRenderer, registry);
            sim.generatedBoardInstance = fixture.instance;
            PcbWorkbenchRenderer renderer = fixture.controller.getRenderer();
            require(renderer.getTrayPageCount() >= 2,
                "Loose lifecycle canary did not provide two tray pages");
            renderer.setTrayPage(0);
            PhysicalPart<?> firstPart = renderer.getVisibleLoosePhysicalParts().firstElement();
            ProbeTarget firstTarget = findLooseTarget(sim, renderer, firstPart, 0);
            require(firstTarget != null && firstTarget.isValid() &&
                    firstTarget.getMarkerPoint() != null &&
                    firstTarget.getMeasurementEndpoint() == firstPart.getTerminal(0).getEndpoint(),
                "Visible loose target did not expose valid marker/endpoint identity");
            requireLiveEndpoint(sim, firstTarget,
                "Visible loose target endpoint is not in the active CircuitJS graph");

            Point boardPadPoint = renderer.getPadPoint(fixture.boardPadId);
            ProbeTarget unaffectedBoardPad = renderer.findProbeTarget(sim, boardPadPoint.x,
                boardPadPoint.y);
            require(unaffectedBoardPad instanceof BoardPadProbeTarget &&
                    unaffectedBoardPad.isValid(),
                "Lifecycle canary could not acquire its unaffected board-pad target");
            requireLiveEndpoint(sim, unaffectedBoardPad,
                "Lifecycle canary board-pad endpoint is not in the active CircuitJS graph");
            sim.instrumentController.activateDcVoltageModeForDeveloperVerification();
            sim.instrumentController.handlePointerInput(NativeEvent.BUTTON_LEFT, firstTarget);
            int beforeLiveDcMeasurement = sim.instrumentController
                .getDcVoltageMeasurementCountForDeveloperVerification();
            sim.instrumentController.handlePointerInput(NativeEvent.BUTTON_RIGHT,
                unaffectedBoardPad);
            require(sim.instrumentController.getRedProbeForStrategy() == firstTarget &&
                    sim.instrumentController.getBlackProbeForStrategy() == unaffectedBoardPad &&
                    sim.instrumentController.getDcVoltageMeasurementCountForDeveloperVerification() >
                    beforeLiveDcMeasurement &&
                    !Double.isNaN(sim.instrumentController.getLatestDcVoltageForDeveloperVerification()) &&
                    !Double.isInfinite(sim.instrumentController.getLatestDcVoltageForDeveloperVerification()) &&
                    !sim.activeMeasurementOverlay,
                "Instrument controller did not execute a live measurement for renderer-acquired targets");

            renderer.setTrayPage(1);
            require(!firstTarget.isValid() && firstTarget.getMarkerPoint() == null &&
                    sim.instrumentController.getRedProbeForStrategy() == null &&
                    sim.instrumentController.getBlackProbeForStrategy() == unaffectedBoardPad &&
                    "--- V".equals(sim.instrumentController.getReadingForDeveloperVerification()) &&
                    !sim.activeMeasurementOverlay,
                "Page transition did not clear only the invalid loose target safely");

            PhysicalPart<?> multiPart = fixture.multiTerminalPart;
            ProbeTarget multiFirst = findLooseTarget(sim, renderer, multiPart, 1);
            ProbeTarget multiSecond = findLooseTarget(sim, renderer, multiPart, 2);
            require(multiFirst != null && multiSecond != null && multiFirst.isValid() &&
                    multiSecond.isValid() && multiFirst.getMeasurementEndpoint() ==
                    multiPart.getTerminal(1).getEndpoint() && multiSecond.getMeasurementEndpoint() ==
                    multiPart.getTerminal(2).getEndpoint() && multiFirst != multiSecond,
                "Live multi-terminal loose canary lost terminal identity");
            requireLiveEndpoint(sim, multiFirst,
                "Multi-terminal first endpoint is not in the active CircuitJS graph");
            requireLiveEndpoint(sim, multiSecond,
                "Multi-terminal second endpoint is not in the active CircuitJS graph");
            sim.instrumentController.activateDcVoltageModeForDeveloperVerification();
            sim.instrumentController.handlePointerInput(NativeEvent.BUTTON_LEFT, multiFirst);
            sim.instrumentController.handlePointerInput(NativeEvent.BUTTON_RIGHT, multiSecond);
            sim.instrumentController.clearTargets();
            fixture.establishUnpowered(sim);
            int beforeContinuityMeasurement = sim.instrumentController
                .getContinuityMeasurementCountForDeveloperVerification();
            sim.instrumentController.activateDeveloperInstrumentModeForVerification("CONTINUITY");
            sim.instrumentController.handlePointerInput(NativeEvent.BUTTON_LEFT, multiFirst);
            sim.instrumentController.handlePointerInput(NativeEvent.BUTTON_RIGHT, multiSecond);
            require(sim.getBoardPowerController().isElectricallyUnpowered() &&
                    sim.instrumentController.getContinuityMeasurementCountForDeveloperVerification() >
                    beforeContinuityMeasurement &&
                    !"POWER OFF".equals(sim.instrumentController.getReadingForDeveloperVerification()) &&
                    !"--- Ohm".equals(sim.instrumentController.getReadingForDeveloperVerification()) &&
                    !sim.activeMeasurementOverlay,
                "Continuity canary did not execute a live unpowered measurement");
            renderer.setTrayPage(0);
            require(!multiFirst.isValid() && !multiSecond.isValid() &&
                    multiFirst.getMarkerPoint() == null && multiSecond.getMarkerPoint() == null &&
                    sim.instrumentController.getRedProbeForStrategy() == null &&
                    sim.instrumentController.getBlackProbeForStrategy() == null &&
                    "--- Ohm".equals(sim.instrumentController.getReadingForDeveloperVerification()) &&
                    !sim.instrumentController.isContinuityIndicatorVisibleForDeveloperVerification() &&
                    !sim.instrumentController.isContinuityFeedbackRequestedForDeveloperVerification() &&
                    !sim.activeMeasurementOverlay,
                "Page transition did not clear both held loose targets");

            renderer.setTrayPage(1);
            ProbeTarget freshMulti = findLooseTarget(sim, renderer, multiPart, 1);
            require(freshMulti != null && freshMulti.isValid() && freshMulti != multiFirst &&
                    freshMulti.isSameTarget(multiFirst) && freshMulti.getMeasurementEndpoint() ==
                    multiFirst.getMeasurementEndpoint() && freshMulti.getMarkerPoint() != null &&
                    !multiFirst.isValid(),
                "Returning to a page revived an old loose target instead of reacquiring it");
            requireLiveEndpoint(sim, freshMulti,
                "Fresh loose target endpoint is not in the active CircuitJS graph");
        } finally {
            try {
                if (fixture != null)
                    fixture.dispose(sim);
            } finally {
                originalSnapshot.restore(sim);
                originalSnapshot.assertRestored(sim);
            }
        }
    }

    private static void requireLiveEndpoint(CirSim sim, ProbeTarget target, String message) {
        CircuitMeasurementEndpoint endpoint = target == null ? null :
            target.getMeasurementEndpoint();
        require(endpoint instanceof CircuitPostMeasurementEndpoint &&
                sim.containsElement(((CircuitPostMeasurementEndpoint) endpoint).getElement()),
            message);
    }

    private static ProbeTarget findLooseTarget(CirSim sim, PcbWorkbenchRenderer renderer,
            PhysicalPart<?> part, int terminalIndex) {
        Vector<PhysicalPart<?>> visible = renderer.getVisibleLoosePhysicalParts();
        for (int index = 0; index < visible.size(); index++) {
            if (visible.get(index) != part)
                continue;
            PhysicalPartRenderContext context = new PhysicalPartRenderContext(renderer, null, part,
                part.getPackage(), index, true);
            Point point = renderer.getRenderRegistryForDeveloperVerification()
                .getProvider(part.getPackage()).getRenderer(part).getLooseGeometry(context)
                .getTerminal(terminalIndex).getPoint();
            return renderer.findProbeTarget(sim, point.x, point.y);
        }
        return null;
    }

    /**
     * Exercises the loose projection without changing the live board or its
     * inventory.  Every registered package is rendered from every declared
     * variant so a package-specific fallback cannot hide behind the normal
     * challenge's current inventory.
     */
    static void verifyLoosePoseCanaries(CirSim sim, PcbWorkbenchRenderer renderer,
            PhysicalPartRenderRegistry registry) {
        if (sim == null || renderer == null || registry == null)
            throw new IllegalArgumentException("Incomplete loose pose verification request");
        Graphics graphics = new Graphics(sim.backcontext);
        int trayRow = 0;
        for (PhysicalPackage physicalPackage : registry.getRegisteredPackages()) {
            PhysicalPart<?> unbound = LooseRenderCanaryPart.create(
                "LOOSE_CANARY_UNBOUND_" + physicalPackage.getId(), physicalPackage,
                isPolarizedCanaryPackage(physicalPackage));
            verifyLoosePoseProjection(sim, renderer, registry, graphics, unbound, trayRow++, null,
                "unbound " + physicalPackage.getId());
            for (PhysicalPackage.GeometryVariant variant : physicalPackage.getGeometryVariants()) {
                LooseRenderCanaryPart bound = LooseRenderCanaryPart.create(
                    "LOOSE_CANARY_BOUND_" + physicalPackage.getId() + "_" + variant.getKey(),
                    physicalPackage, isPolarizedCanaryPackage(physicalPackage));
                PhysicalGeometryRealization realization = new PhysicalGeometryRealization(
                    physicalPackage, variant.getGeometry(), variant.getKey(),
                    variant.getTransformKey(), variant.getGeometry().getGeometryContractVersion());
                bound.bindGeometryRealization(realization);
                verifyLoosePoseProjection(sim, renderer, registry, graphics, bound, trayRow++,
                    realization, "bound " + physicalPackage.getId() + "/" + variant.getKey());
            }
        }
        verifyLooseNegativeCanaries(sim, renderer, registry, graphics);
    }

    private static void verifyLoosePoseProjection(CirSim sim, PcbWorkbenchRenderer renderer,
            PhysicalPartRenderRegistry registry, Graphics graphics, PhysicalPart<?> part,
            int row, PhysicalGeometryRealization expectedRealization, String label) {
        int trayRow = row % renderer.getPartsPerTrayPage();
        PhysicalPackage physicalPackage = part.getPackage();
        PhysicalPartRenderProvider provider = registry.getProvider(physicalPackage);
        require(provider != null, "Loose canary has no provider: " + label);
        PhysicalPartRenderer partRenderer = provider.getRenderer(part);
        PhysicalPartRenderContext context = new PhysicalPartRenderContext(renderer, null, part,
            physicalPackage, trayRow, true);
        LoosePartPose pose = context.getLoosePose();
        require(context.getPhysicalGeometry() == pose.getSourceGeometry(),
            "Loose context did not expose its pose source geometry: " + label);
        if (expectedRealization == null)
            require(pose.getSourceRealization() == null && pose.getSourceGeometry() ==
                    physicalPackage.getDefaultLooseGeometry(),
                "Unbound loose part did not use the package canonical default: " + label);
        else
            require(pose.getSourceRealization() == expectedRealization &&
                    pose.getSourceGeometry() == expectedRealization.getPhysicalGeometry(),
                "Bound loose part lost its selected realization: " + label);
        require(pose.getScale() > 0.0 && pose.getScale() <= 1.0 &&
                !Double.isNaN(pose.getScale()) && !Double.isInfinite(pose.getScale()) &&
                contains(pose.getTrayCell(), pose.getSelectionEnvelope()),
            "Loose pose scale or cell containment is invalid: " + label);
        if (isPolarizedCanaryPackage(physicalPackage))
            require(pose.getOrientation() == PhysicalPartOrientation.REVERSED &&
                    pose.isPolarityMirrored(),
                "Reversed loose package did not use its explicit polarity mirror: " + label);
        if (physicalPackage.isConnector() || pose.getSourceGeometry().isDeveloperGeneric())
            require(pose.getQuarterTurn() == LoosePartPose.QuarterTurn.CLOCKWISE,
                "Vertical loose package did not use its explicit quarter-turn: " + label);
        else
            require(pose.getQuarterTurn() == LoosePartPose.QuarterTurn.NONE,
                "Horizontal loose package acquired an unexpected quarter-turn: " + label);

        PhysicalPartRenderGeometry geometry = partRenderer.getLooseGeometry(context);
        require(geometry.getSelectionBounds().equals(renderer.screenRectForProvider(
                    pose.getSelectionEnvelope())) &&
                geometry.getBodyBounds().equals(renderer.screenRectForProvider(
                    pose.getBodyBounds())) &&
                geometry.getDragBounds().equals(renderer.screenRectForProvider(
                    pose.getDragEnvelope())),
            "Loose provider did not consume the pose envelopes: " + label);
        partRenderer.drawLoose(graphics, context, geometry, false);
        require(context.wasBodyDrawn(), "Loose provider did not draw its body: " + label);

        for (int index = 0; index < part.getTerminalCount(); index++) {
            PhysicalPartRenderTerminal terminal = geometry.getTerminal(index);
            require(terminal != null && terminal.getTerminalIndex() == index &&
                    terminal.getTerminalId().equals(part.getTerminal(index).getTerminalName()) &&
                    terminal.getBoardPadId() == null,
                "Loose terminal identity or board-pad identity changed: " + label);
            Point expectedPoint = renderer.screenPointForProvider(pose.getTerminalPoint(index));
            require(pointEquals(terminal.getPoint(), expectedPoint) &&
                    terminal.containsProbe(expectedPoint.x, expectedPoint.y) &&
                    geometry.contains(expectedPoint.x, expectedPoint.y),
                "Loose marker lost the transformed terminal surface: " + label + "." + index);
            require(terminal.getPadBounds().equals(renderer.screenRectForProvider(
                        pose.getPadBounds(index))) &&
                    terminal.getProbeBounds().equals(renderer.screenRectForProvider(
                        pose.getProbeBounds(index))) &&
                    pointEquals(terminal.getComponentLeadPoint(), renderer.screenPointForProvider(
                        pose.getComponentLeadPoint(index))) &&
                    terminal.getComponentLeadProbeBounds().equals(renderer.screenRectForProvider(
                        pose.getComponentLeadProbeBounds(index))) &&
                    pointEquals(terminal.getLeadBodyPoint(), renderer.screenPointForProvider(
                        pose.getLeadBodyPoint(index))) &&
                    pointEquals(terminal.getLeadEndPoint(), renderer.screenPointForProvider(
                        pose.getLeadEndPoint(index))) &&
                    terminal.getLeadBounds().equals(renderer.screenRectForProvider(
                        pose.getLeadBounds(index))),
                "Loose terminal surface projection disagrees with the pose: " + label + "." +
                    index);
            Vector<Rectangle> surfaces = terminal.getProbeSurfaces();
            Vector<Rectangle> expectedSurfaces = new Vector<Rectangle>();
            for (Rectangle surface : pose.getProbeSurfaces(index))
                expectedSurfaces.add(renderer.screenRectForProvider(surface));
            require(sameRectangles(surfaces, expectedSurfaces),
                "Loose terminal probe surfaces were reconstructed or warped: " + label + "." +
                    index);
            require(surfaceContains(surfaces, terminal.getPoint()),
                "Loose marker is outside its declared terminal/lead probe surface: " + label +
                    "." + index);
            CircuitMeasurementEndpoint endpoint = part.getTerminal(index).getEndpoint();
            ProbeTarget target = partRenderer.createLooseProbeTarget(sim, context, index);
            require(target != null && part.getTerminal(index).getEndpoint() == endpoint,
                "Loose provider changed probe endpoint identity: " + label + "." + index);
            requireSpecializedLooseTarget(physicalPackage, target, part.getId());
        }
    }

    private static void verifyLooseNegativeCanaries(CirSim sim, PcbWorkbenchRenderer renderer,
            PhysicalPartRenderRegistry registry, Graphics graphics) {
        require(sim != null && graphics != null,
            "Loose negative canaries require a live render canvas");
        int row = 0;
        for (PhysicalPackage physicalPackage : registry.getRegisteredPackages()) {
            LooseRenderCanaryPart part = LooseRenderCanaryPart.create(
                "LOOSE_CANARY_NEGATIVE_" + physicalPackage.getId(), physicalPackage,
                isPolarizedCanaryPackage(physicalPackage));
            verifyLooseNegativeCanaryForPackage(renderer, registry, part, row++,
                physicalPackage.getId());
        }
    }

    private static void verifyLooseNegativeCanaryForPackage(PcbWorkbenchRenderer renderer,
            PhysicalPartRenderRegistry registry, LooseRenderCanaryPart part, int row,
            String label) {
        PhysicalPackage physicalPackage = part.getPackage();
        PhysicalPartRenderProvider provider = registry.getProvider(physicalPackage);
        require(provider != null && provider.getRenderer(part) != null,
            "Loose negative canary has no provider: " + label);
        int trayRow = row % renderer.getPartsPerTrayPage();
        PhysicalPartRenderContext context = new PhysicalPartRenderContext(renderer, null, part,
            physicalPackage, trayRow, true);
        PhysicalPartRenderer partRenderer = provider.getRenderer(part);
        final PhysicalPartRenderGeometry geometry = partRenderer.getLooseGeometry(context);
        final LoosePartPose pose = context.getLoosePose();
        final Rectangle screenCell = renderer.screenRectForProvider(pose.getTrayCell());
        final PhysicalPartRenderTerminal terminal = geometry.getTerminal(0);
        require(terminal != null, "Loose negative canary omitted terminal 0: " + label);

        verifyLooseOutsideNegative(geometry, screenCell, label);
        verifyLooseUniformPoseNegative(geometry, pose, label);

        final Rectangle body = geometry.getBodyBounds();
        final Rectangle selection = geometry.getSelectionBounds();
        final Rectangle warpedBody = new Rectangle(body.x, body.y,
            body.width + Math.max(3, selection.width), body.height);
        require(warpedBody.width != body.width && warpedBody.height == body.height,
            "Negative canary did not create a non-uniform body warp: " + label);
        expectLooseRejection("Physical render body escapes hit geometry",
            new LooseNegativeConstruction() {
                public void construct() {
                    new PhysicalPartRenderGeometry(geometry.getTerminals(),
                        geometry.getHitRegions(), selection, warpedBody,
                        geometry.getLeadBounds(), geometry.getDragBounds());
                }
            }, "non-uniform body warp: " + label);

        final Vector<PhysicalPartRenderHitRegion> bodylessHits = geometry.getHitRegions();
        removeHitRegions(bodylessHits, body);
        require(!containsHitRegion(bodylessHits, body),
            "Negative canary did not remove the declared body hit region: " + label);
        expectLooseRejection("Physical render body escapes hit geometry",
            new LooseNegativeConstruction() {
                public void construct() {
                    new PhysicalPartRenderGeometry(geometry.getTerminals(), bodylessHits,
                        selection, body, geometry.getLeadBounds(), geometry.getDragBounds());
                }
            }, "visible body outside hit geometry: " + label);

        final Point emptyLeadPoint = findOutsideHit(geometry, selection);
        require(emptyLeadPoint != null,
            "Negative canary could not find an empty selection point for a lead warp: " + label);
        final Rectangle corruptedLeadBounds = new Rectangle(emptyLeadPoint.x, emptyLeadPoint.y,
            1, 1);
        final Vector<PhysicalPartRenderTerminal> corruptedLeadTerminals = geometry.getTerminals();
        final PhysicalPartRenderTerminal canonicalLeadTerminal = corruptedLeadTerminals.get(0);
        corruptedLeadTerminals.set(0, new PhysicalPartRenderTerminal(
            canonicalLeadTerminal.getTerminalIndex(), canonicalLeadTerminal.getTerminalId(), null,
            canonicalLeadTerminal.getPoint(), canonicalLeadTerminal.getProbeBounds(), null, null,
            canonicalLeadTerminal.getPadBounds(), canonicalLeadTerminal.getComponentLeadPoint(),
            canonicalLeadTerminal.getComponentLeadProbeBounds(), emptyLeadPoint, emptyLeadPoint,
            corruptedLeadBounds, canonicalLeadTerminal.getProbeSurfaces()));
        final Vector<Rectangle> corruptedLeadBoundsList = geometry.getLeadBounds();
        corruptedLeadBoundsList.set(0, corruptedLeadBounds);
        expectLooseRejection("Physical render feature escapes hit geometry",
            new LooseNegativeConstruction() {
                public void construct() {
                    new PhysicalPartRenderGeometry(corruptedLeadTerminals,
                        geometry.getHitRegions(), selection, body, corruptedLeadBoundsList,
                        geometry.getDragBounds());
                }
            }, "visible lead outside hit geometry: " + label);

        Point emptyTrayPoint = findOutside(geometry, screenCell);
        require(emptyTrayPoint != null && !geometry.contains(emptyTrayPoint.x, emptyTrayPoint.y),
            "Negative canary did not identify empty tray space: " + label);
        final Vector<PhysicalPartRenderHitRegion> giantTrayHits = geometry.getHitRegions();
        final Rectangle giantTray = new Rectangle(screenCell.x - 4, screenCell.y - 4,
            screenCell.width + 8, screenCell.height + 8);
        giantTrayHits.add(new PhysicalPartRenderHitRegion(giantTray));
        require(!contains(selection, giantTray),
            "Negative canary giant tray region unexpectedly matched selection: " + label);
        expectLooseRejection("Physical render hit region escapes selection",
            new LooseNegativeConstruction() {
                public void construct() {
                    new PhysicalPartRenderGeometry(geometry.getTerminals(), giantTrayHits,
                        selection, body, geometry.getLeadBounds(), geometry.getDragBounds());
                }
            }, "giant empty-tray hit region: " + label);

        final Vector<Rectangle> markerlessSurfaces = new Vector<Rectangle>();
        markerlessSurfaces.add(terminal.getComponentLeadProbeBounds());
        expectLooseRejection("Physical render marker is outside its declared loose probe surfaces",
            new LooseNegativeConstruction() {
                public void construct() {
                    new PhysicalPartRenderTerminal(terminal.getTerminalIndex(),
                        terminal.getTerminalId(), null, terminal.getPoint(),
                        terminal.getProbeBounds(), null, null, terminal.getPadBounds(),
                        terminal.getComponentLeadPoint(), terminal.getComponentLeadProbeBounds(),
                        terminal.getLeadBodyPoint(), terminal.getLeadEndPoint(),
                        terminal.getLeadBounds(), markerlessSurfaces);
                }
            }, "marker outside declared surfaces: " + label);

        final PhysicalPartRenderTerminal canonicalTerminal = geometry.getTerminal(0);
        final Point mismatchedLeadBody = pose.transformPoint(new Point(
            pose.getSourceGeometry().getTerminal(0).getConnectedLead().getBodyPoint().x +
                pose.getSourceGeometry().getWidth() + 32,
            pose.getSourceGeometry().getTerminal(0).getConnectedLead().getBodyPoint().y));
        require(!pointEquals(mismatchedLeadBody, canonicalTerminal.getLeadBodyPoint()),
            "Negative canary did not create a body/terminal transform mismatch: " + label);
        expectLooseRejection("Physical render lead point escapes lead bounds",
            new LooseNegativeConstruction() {
                public void construct() {
                    new PhysicalPartRenderTerminal(canonicalTerminal.getTerminalIndex(),
                        canonicalTerminal.getTerminalId(), null, canonicalTerminal.getPoint(),
                        canonicalTerminal.getProbeBounds(), null, null,
                        canonicalTerminal.getPadBounds(), canonicalTerminal.getComponentLeadPoint(),
                        canonicalTerminal.getComponentLeadProbeBounds(), mismatchedLeadBody,
                        canonicalTerminal.getLeadEndPoint(), canonicalTerminal.getLeadBounds(),
                        canonicalTerminal.getProbeSurfaces());
                }
            }, "body/terminal transform mismatch: " + label);
    }

    private static void verifyLooseOutsideNegative(PhysicalPartRenderGeometry geometry,
            Rectangle cell, String label) {
        Point outside = findOutside(geometry, cell);
        require(outside != null && !geometry.contains(outside.x, outside.y),
            "Loose hit geometry became a giant tray region: " + label);
        PhysicalPartRenderTerminal terminal = geometry.getTerminal(0);
        Point badMarker = findOutsideTerminal(terminal, cell);
        require(badMarker != null && !terminal.containsProbe(badMarker.x, badMarker.y),
            "Loose terminal accepted a marker outside its declared surfaces: " + label);
    }

    private static void verifyLooseUniformPoseNegative(PhysicalPartRenderGeometry geometry,
            LoosePartPose pose, String label) {
        PhysicalPackageGeometry.Terminal sourceTerminal = pose.getSourceGeometry().getTerminal(0);
        Point canonicalPoint = pose.getTerminalPoint(0);
        final Point independentlyTranslated = pose.transformPoint(new Point(
            sourceTerminal.getPadCenter().x + pose.getSourceGeometry().getWidth() + 32,
            sourceTerminal.getPadCenter().y));
        require(!pointEquals(canonicalPoint, independentlyTranslated) &&
                !contains(pose.getSelectionEnvelope(), new Rectangle(independentlyTranslated.x,
                    independentlyTranslated.y, 1, 1)),
            "Negative canary did not detect an independently translated terminal: " + label);
        final PhysicalPartRenderTerminal canonicalTerminal = geometry.getTerminal(0);
        expectLooseRejection("Physical render marker is outside its probe surface",
            new LooseNegativeConstruction() {
                public void construct() {
                    new PhysicalPartRenderTerminal(canonicalTerminal.getTerminalIndex(),
                        canonicalTerminal.getTerminalId(), null, independentlyTranslated,
                        canonicalTerminal.getProbeBounds(), null, null,
                        canonicalTerminal.getPadBounds(), canonicalTerminal.getComponentLeadPoint(),
                        canonicalTerminal.getComponentLeadProbeBounds(),
                        canonicalTerminal.getLeadBodyPoint(), canonicalTerminal.getLeadEndPoint(),
                        canonicalTerminal.getLeadBounds(), canonicalTerminal.getProbeSurfaces());
                }
            }, "independently translated terminal: " + label);

        Rectangle body = geometry.getBodyBounds();
        Rectangle warpedBody = new Rectangle(body.x, body.y, body.width + 3, body.height);
        require(!warpedBody.equals(body) && warpedBody.width != body.width &&
                warpedBody.height == body.height,
            "Negative canary did not make the body warp non-uniform: " + label);

        Point canonicalLeadBody = pose.getLeadBodyPoint(0);
        Point mismatchedLeadBody = pose.transformPoint(new Point(
            sourceTerminal.getConnectedLead().getBodyPoint().x +
                pose.getSourceGeometry().getWidth() + 32,
            sourceTerminal.getConnectedLead().getBodyPoint().y));
        require(!pointEquals(canonicalLeadBody, mismatchedLeadBody),
            "Negative canary did not detect a body/terminal transform mismatch: " + label);
    }

    private interface LooseNegativeConstruction {
        void construct();
    }

    private static void expectLooseRejection(String expectedMessage,
            LooseNegativeConstruction construction, String label) {
        String actualMessage = null;
        try {
            construction.construct();
        } catch (IllegalArgumentException expected) {
            actualMessage = expected.getMessage();
        }
        require(expectedMessage.equals(actualMessage),
            "Negative canary had the wrong failure reason for " + label + ": expected " +
                expectedMessage + ", got " + actualMessage);
    }

    private static Point findOutsideHit(PhysicalPartRenderGeometry geometry, Rectangle envelope) {
        Vector<PhysicalPartRenderHitRegion> regions = geometry.getHitRegions();
        for (int y = envelope.y; y < envelope.y + envelope.height; y++)
            for (int x = envelope.x; x < envelope.x + envelope.width; x++)
                if (!containsHitRegion(regions, new Rectangle(x, y, 1, 1)))
                    return new Point(x, y);
        return null;
    }

    private static void removeHitRegions(Vector<PhysicalPartRenderHitRegion> regions,
            Rectangle bounds) {
        for (int index = regions.size() - 1; index >= 0; index--)
            if (bounds.equals(regions.get(index).getBounds()))
                regions.remove(index);
    }

    private static boolean containsHitRegion(Vector<PhysicalPartRenderHitRegion> regions,
            Rectangle bounds) {
        for (PhysicalPartRenderHitRegion region : regions)
            if (contains(region.getBounds(), bounds))
                return true;
        return false;
    }

    private static Point findOutside(PhysicalPartRenderGeometry geometry, Rectangle cell) {
        for (int y = cell.y; y < cell.y + cell.height; y++)
            for (int x = cell.x; x < cell.x + cell.width; x++)
                if (!geometry.contains(x, y))
                    return new Point(x, y);
        return null;
    }

    private static Point findOutsideTerminal(PhysicalPartRenderTerminal terminal,
            Rectangle cell) {
        for (int y = cell.y; y < cell.y + cell.height; y++)
            for (int x = cell.x; x < cell.x + cell.width; x++)
                if (!terminal.containsProbe(x, y))
                    return new Point(x, y);
        return null;
    }

    private static boolean surfaceContains(Vector<Rectangle> surfaces, Point point) {
        for (Rectangle surface : surfaces)
            if (surface.contains(point.x, point.y))
                return true;
        return false;
    }

    private static boolean sameRectangles(Vector<Rectangle> first, Vector<Rectangle> second) {
        if (first == null || second == null || first.size() != second.size())
            return false;
        for (int index = 0; index < first.size(); index++)
            if (!first.get(index).equals(second.get(index)))
                return false;
        return true;
    }

    private static boolean isPolarizedCanaryPackage(PhysicalPackage physicalPackage) {
        return PhysicalPackages.AXIAL_DIODE.isEquivalentTo(physicalPackage) ||
            PhysicalPackages.THROUGH_HOLE_LED.isEquivalentTo(physicalPackage);
    }

    private static final class LooseRenderCanaryPart implements PhysicalPart<PhysicalSpecification> {
        private final String id;
        private final PhysicalSpecification specification;
        private final PhysicalNameplate nameplate;
        private final PhysicalPackage physicalPackage;
        private final PhysicalPartRenderMetadata metadata;
        private final PhysicalPartTerminal[] terminals;
        private final CircuitPhysicalPartElectricalBacking backing;
        private final PhysicalPartMountState mountState = new PhysicalPartMountState();
        private final PhysicalPartGeometryRealization geometryRealization =
            new PhysicalPartGeometryRealization();

        private LooseRenderCanaryPart(String id, PhysicalPackage physicalPackage,
                PhysicalSpecification specification, PhysicalPartRenderMetadata metadata) {
            this.id = id;
            this.physicalPackage = physicalPackage;
            this.specification = specification;
            this.metadata = metadata;
            this.nameplate = new PhysicalNameplate(id, "Loose render canary");
            Vector<CircuitMeasurementEndpoint> endpoints = new Vector<CircuitMeasurementEndpoint>();
            Vector<CircuitElm> elements = new Vector<CircuitElm>();
            terminals = new PhysicalPartTerminal[physicalPackage.getTerminalCount()];
            for (int index = 0; index < terminals.length; index++) {
                WireElm wire = new WireElm(1000 + index * 32, 1000);
                wire.drag(1016 + index * 32, 1000);
                elements.add(wire);
                CircuitMeasurementEndpoint endpoint = new CircuitPostMeasurementEndpoint(wire, 0);
                endpoints.add(endpoint);
                terminals[index] = new PhysicalPartTerminal(id,
                    physicalPackage.getTerminalIds().get(index), endpoint);
            }
            backing = new CircuitPhysicalPartElectricalBacking(endpoints, elements);
        }

        static LooseRenderCanaryPart create(String id, PhysicalPackage physicalPackage,
                boolean reversed) {
            PhysicalSpecification specification;
            PhysicalPartRenderMetadata metadata;
            if (PhysicalPackages.AXIAL_RESISTOR.isEquivalentTo(physicalPackage)) {
                ResistorNameplate resistor = new ResistorNameplate(id, 1000, 5);
                specification = resistor;
                metadata = new PhysicalPartRenderMetadata(resistor,
                    PhysicalPartOrientation.NON_POLARIZED, PhysicalPartRenderProbeProviders.RESISTOR);
            } else if (PhysicalPackages.AXIAL_DIODE.isEquivalentTo(physicalPackage)) {
                DiodeNameplate diode = new DiodeNameplate(id, "Canary diode", "default");
                specification = diode;
                metadata = new PhysicalPartRenderMetadata(diode,
                    PhysicalPartOrientation.polarized(reversed), PhysicalPartRenderProbeProviders.DIODE);
            } else if (PhysicalPackages.THROUGH_HOLE_LED.isEquivalentTo(physicalPackage)) {
                LedNameplate led = new LedNameplate(id, "Canary LED", "default-led", 1, 0, 0);
                specification = led;
                metadata = new PhysicalPartRenderMetadata(led,
                    PhysicalPartOrientation.polarized(reversed), PhysicalPartRenderProbeProviders.LED);
            } else if (PhysicalPackages.RADIAL_ELECTROLYTIC_CAPACITOR.isEquivalentTo(
                    physicalPackage) || PhysicalPackages.RADIAL_CERAMIC_CAPACITOR.isEquivalentTo(
                    physicalPackage)) {
                CapacitorNameplate nameplate = new CapacitorNameplate("Canary capacitor", "10uF");
                CapacitorSpecification capacitor = new CapacitorSpecification(id, .00001, 20, 25,
                    physicalPackage, nameplate);
                specification = capacitor;
                metadata = new PhysicalPartRenderMetadata(capacitor,
                    physicalPackage.isEquivalentTo(PhysicalPackages.RADIAL_ELECTROLYTIC_CAPACITOR) ?
                        PhysicalPartOrientation.NORMAL : PhysicalPartOrientation.NON_POLARIZED,
                    PhysicalPartRenderProbeProviders.CAPACITOR);
            } else if (PhysicalPackages.TO92_NPN.isEquivalentTo(physicalPackage)) {
                NpnSpecification npn = new NpnSpecification(id, 100);
                specification = npn;
                metadata = new PhysicalPartRenderMetadata(npn,
                    PhysicalPartOrientation.NON_POLARIZED, PhysicalPartRenderProbeProviders.NPN);
            } else if (PhysicalPackages.TO92_NMOS.isEquivalentTo(physicalPackage)) {
                NmosSpecification nmos = new NmosSpecification(id, 2, 1);
                specification = nmos;
                metadata = new PhysicalPartRenderMetadata(nmos,
                    PhysicalPartOrientation.NON_POLARIZED, PhysicalPartRenderProbeProviders.NMOS);
            } else {
                specification = new BasicPhysicalSpecification(id);
                metadata = new PhysicalPartRenderMetadata(specification,
                    PhysicalPartOrientation.NON_POLARIZED, null);
            }
            return new LooseRenderCanaryPart(id, physicalPackage, specification, metadata);
        }

        public String getId() { return id; }
        public PhysicalSpecification getSpecification() { return specification; }
        public PhysicalNameplate getPlayerVisibleNameplate() { return nameplate; }
        public PhysicalPartRenderMetadata getRenderMetadata() { return metadata; }
        public PhysicalPartOrientation getOrientation() { return metadata.getOrientation(); }
        public PhysicalPackage getPackage() { return physicalPackage; }
        public int getTerminalCount() { return terminals.length; }
        public PhysicalPartTerminal getTerminal(int terminal) {
            if (terminal < 0 || terminal >= terminals.length)
                throw new IllegalArgumentException("Invalid loose render canary terminal");
            return terminals[terminal];
        }
        public Vector<PhysicalPartTerminal> getTerminals() {
            Vector<PhysicalPartTerminal> result = new Vector<PhysicalPartTerminal>();
            for (PhysicalPartTerminal terminal : terminals)
                result.add(terminal);
            return result;
        }
        public PhysicalPartElectricalBacking getElectricalBacking() { return backing; }
        public PhysicalGeometryRealization getGeometryRealization() {
            return geometryRealization.getGeometryRealization();
        }
        public void bindGeometryRealization(PhysicalGeometryRealization realization) {
            geometryRealization.bind(physicalPackage, realization);
        }
        public PhysicalPartMountState getMountState() { return mountState; }
        public PhysicalBoardSlot getBoardSlot() { return mountState.getSlot(); }
        public PhysicalPartProvenance getProvenance() {
            return new PhysicalPartProvenance(PhysicalPartProvenance.DEVELOPER_CANARY, id);
        }
        public PhysicalFailureState getFailureState() {
            return new PhysicalFailureState(PhysicalFailureState.HEALTHY, false);
        }
        public Vector<PhysicalPartCapability> getCapabilities() {
            return new Vector<PhysicalPartCapability>();
        }
        public Vector<PhysicalPartCapability> getIntrinsicCapabilities() {
            return getCapabilities();
        }
        public boolean isInstalled() { return mountState.isInstalled(); }
        public boolean isOriginal() { return false; }
        public boolean isFaulted() { return false; }
    }

    private static final class LooseProjectionLifecycleFixture {
        private final GeneratedBoardInstance instance;
        private final PcbWorkbenchController controller;
        private final String boardPadId;
        private final PhysicalPart<?> multiTerminalPart;
        private final GeneratedExternalPowerBindings powerBindings;
        private final Vector<CircuitElm> elements;

        private LooseProjectionLifecycleFixture(GeneratedBoardInstance instance,
                PcbWorkbenchController controller, String boardPadId,
                PhysicalPart<?> multiTerminalPart, GeneratedExternalPowerBindings powerBindings,
                Vector<CircuitElm> elements) {
            this.instance = instance;
            this.controller = controller;
            this.boardPadId = boardPadId;
            this.multiTerminalPart = multiTerminalPart;
            this.powerBindings = powerBindings;
            this.elements = elements;
        }

        static LooseProjectionLifecycleFixture create(CirSim sim,
                PcbWorkbenchRenderer originalRenderer, PhysicalPartRenderRegistry registry) {
            if (sim == null || originalRenderer == null || registry == null)
                throw new IllegalArgumentException("Incomplete loose lifecycle fixture");
            PcbBoardLayout layout = originalRenderer.getLayoutForProvider();
            require(!layout.getComponents().isEmpty() && !layout.getPads().isEmpty(),
                "Loose lifecycle fixture requires an existing board pad");
            GeneratedBoardInstance originalInstance = sim.getGeneratedBoardInstance();
            require(originalInstance != null && originalInstance.getBoard() != null,
                "Loose lifecycle fixture requires the original board for layout identity");
            TroubleshootBoard originalBoard = originalInstance.getBoard();
            PhysicalPackage connectorPackage = PhysicalPackages.THROUGH_HOLE_CONNECTOR_2;
            PcbPadPlacement layoutPad = null;
            BoardPad layoutBoardPad = null;
            for (PcbPadPlacement candidate : layout.getPads()) {
                BoardPad candidateBoardPad = originalBoard.getPad(candidate.getPadId());
                if (candidateBoardPad != null && connectorPackage.getTerminalIds().contains(
                        candidateBoardPad.getTerminalId())) {
                    layoutPad = candidate;
                    layoutBoardPad = candidateBoardPad;
                    break;
                }
            }
            require(layoutPad != null && layoutBoardPad != null,
                "Loose lifecycle fixture could not retain a connector-compatible layout pad");
            String boardPadId = layoutPad.getPadId();
            int separator = boardPadId.indexOf('.');
            require(separator > 0, "Loose lifecycle fixture pad has no component identity");
            String boardComponentId = boardPadId.substring(0, separator);
            String selectedTerminalId = layoutBoardPad.getTerminalId();
            String secondTerminalId = null;
            for (String terminalId : connectorPackage.getTerminalIds())
                if (!selectedTerminalId.equals(terminalId)) {
                    secondTerminalId = terminalId;
                    break;
                }
            require(secondTerminalId != null,
                "Loose lifecycle fixture connector has no companion terminal");
            String secondPadId = boardComponentId + "." + secondTerminalId;
            require(!boardPadId.equals(secondPadId),
                "Loose lifecycle fixture connector pads are not distinct");

            Vector<CircuitElm> elements = new Vector<CircuitElm>();
            Vector<CircuitElm> insertedElements = new Vector<CircuitElm>();
            PcbWorkbenchController controller = null;
            try {
                TroubleshootBoard board = new TroubleshootBoard("TASK43_LOOSE_LIFECYCLE");
                board.addNet(new BoardNet("CANARY_POWER"));
                board.addNet(new BoardNet("CANARY_RETURN"));
                board.addComponent(new BoardComponent(boardComponentId, "CONNECTOR",
                    connectorPackage));
                board.addPad(new BoardPad(boardPadId, boardComponentId, selectedTerminalId,
                    "CANARY_POWER"));
                board.addPad(new BoardPad(secondPadId, boardComponentId, secondTerminalId,
                    "CANARY_RETURN"));
                board.addPowerInput(new ExternalBoardPowerInput("CANARY_POWER_INPUT",
                    boardPadId, secondPadId, "CANARY_POWER", "CANARY_RETURN"));

                DCVoltageElm fixtureSupply = new DCVoltageElm(1800, 2200);
                fixtureSupply.drag(1800, 1800);
                fixtureSupply.maxVoltage = 5;
                SwitchElm fixtureIsolation = new SwitchElm(1800, 1800);
                fixtureIsolation.drag(1864, 1800);
                WireElm boardWire = new WireElm(1864, 1800);
                boardWire.drag(1880, 1800);
                GroundElm returnGround = new GroundElm(1800, 2200);
                returnGround.drag(1800, 2232);
                elements.add(fixtureSupply);
                elements.add(fixtureIsolation);
                elements.add(boardWire);
                elements.add(returnGround);
                board.getSimulationBindings().bindPad(boardPadId,
                    new CircuitPostMeasurementEndpoint(boardWire, 0));
                board.getSimulationBindings().bindPad(secondPadId,
                    new CircuitPostMeasurementEndpoint(returnGround, 0));
                board.validate();

                PhysicalBoardRuntime runtime = new PhysicalBoardRuntime(board);
                runtime.createSlot(boardComponentId);
                Vector<PhysicalPart<?>> parts = new Vector<PhysicalPart<?>>();
                PhysicalPackage[] packages = new PhysicalPackage[] {
                    PhysicalPackages.AXIAL_RESISTOR, PhysicalPackages.AXIAL_DIODE,
                    PhysicalPackages.THROUGH_HOLE_LED,
                    PhysicalPackages.RADIAL_CERAMIC_CAPACITOR, PhysicalPackages.TO92_NPN,
                    PhysicalPackages.TO92_NMOS
                };
                for (int index = 0; index < packages.length; index++) {
                    LooseRenderCanaryPart part = LooseRenderCanaryPart.create(
                        "TASK43_LOOSE_" + index, packages[index],
                        isPolarizedCanaryPackage(packages[index]));
                    runtime.registerPart(part);
                    parts.add(part);
                    elements.addAll(part.getElectricalBacking().getCircuitElements());
                }
                installFixtureElements(sim, elements, insertedElements);
                LooseProjectionPartsProvider provider = new LooseProjectionPartsProvider(parts);
                runtime.registerCapability(provider);

                GeneratedComponentBindings componentBindings =
                    new GeneratedComponentBindings(board);
                GeneratedExternalPowerBindings powerBindings =
                    new GeneratedExternalPowerBindings(board);
                Vector<CircuitElm> powerElements = new Vector<CircuitElm>();
                powerElements.add(fixtureSupply);
                powerElements.add(fixtureIsolation);
                powerBindings.bindPowerInput("CANARY_POWER_INPUT",
                    new ExternalPowerSimulationBinding(powerElements,
                        new SwitchExternalPowerControl(fixtureIsolation)));
                GeneratedComponentConnectionBindings connectionBindings =
                    new GeneratedComponentConnectionBindings(board);
                BoardPhysicalSpecifications specifications = new BoardPhysicalSpecifications();
                GeneratedChallengeBehaviorContract behavior =
                    new GeneratedChallengeBehaviorContract() {
                        public void verifyHealthy(GeneratedBoardInstance instance,
                                BoardPowerState powerState) { }
                        public void verifyFaulted(GeneratedBoardInstance instance,
                                BoardModificationController modifications,
                                BoardPowerState powerState) { }
                        public GeneratedRepairStatus getRepairStatus(
                                GeneratedBoardInstance instance,
                                BoardModificationController modifications,
                                BoardPowerState powerState, boolean activeMeasurementOverlay) {
                            return GeneratedRepairStatus.STILL_FAULTED_OR_NONFUNCTIONAL;
                        }
                        public boolean isFunctionallyRepaired(GeneratedBoardInstance instance,
                                BoardModificationController modifications,
                                BoardPowerState powerState, boolean activeMeasurementOverlay) {
                            return false;
                        }
                    };
                GeneratedBoardInstance instance = new GeneratedBoardInstance(board, elements,
                    43L, QuickPlayFamilyRegistry.LED_INDICATOR, "TASK43_LOOSE_LIFECYCLE",
                    "Task 43 loose lifecycle canary", componentBindings, powerBindings,
                    connectionBindings, behavior, null, specifications, null, null, null, null,
                    runtime, null, false, new Vector<GeneratedFaultCandidate>());
                BoardModificationController modifications = new BoardModificationController(sim,
                    instance);
                controller = new PcbWorkbenchController(sim, instance, modifications, layout,
                    null, false, false);
                return new LooseProjectionLifecycleFixture(instance, controller, boardPadId,
                    parts.lastElement(), powerBindings, elements);
            } catch (RuntimeException failure) {
                try {
                    if (controller != null)
                        controller.disposeForDeveloperVerification();
                } finally {
                    removeFixtureElements(sim, insertedElements);
                }
                throw failure;
            }
        }

        void dispose(CirSim sim) {
            try {
                controller.disposeForDeveloperVerification();
            } finally {
                try {
                    if (sim.getBoardPowerController().getBindingsForDeveloperVerification() ==
                            powerBindings)
                        sim.getBoardPowerController().detach();
                } finally {
                    removeFixtureElements(sim, elements);
                }
            }
        }

        void establishUnpowered(CirSim sim) {
            BoardPowerController power = sim.getBoardPowerController();
            power.attach(powerBindings);
            require(power.setState(BoardPowerState.UNPOWERED),
                "Loose lifecycle fixture could not switch its board power off");
            require(power.isElectricallyUnpowered(),
                "Loose lifecycle fixture did not establish electrical unpowered state");
        }

        private static void installFixtureElements(CirSim sim, Vector<CircuitElm> elements,
                Vector<CircuitElm> insertedElements) {
            for (CircuitElm element : elements) {
                require(element != null && !sim.elmList.contains(element) &&
                        !insertedElements.contains(element),
                    "Loose lifecycle fixture attempted duplicate or missing element insertion");
                sim.elmList.add(element);
                insertedElements.add(element);
            }
        }

        private static void removeFixtureElements(CirSim sim, Vector<CircuitElm> elements) {
            if (sim == null || sim.elmList == null || elements == null)
                return;
            for (CircuitElm element : elements)
                while (sim.elmList.remove(element)) {
                }
        }
    }

    private static final class LooseProjectionPartsProvider implements WorkbenchPartsProvider,
            PhysicalBoardRuntimeCapability {
        private final Vector<PhysicalPart<?>> parts;

        LooseProjectionPartsProvider(Vector<PhysicalPart<?>> parts) {
            this.parts = new Vector<PhysicalPart<?>>(parts);
        }

        public String getCapabilityId() { return "TASK43_LOOSE_LIFECYCLE_PARTS"; }
        public String getComponentId() { return "TASK43_LOOSE_LIFECYCLE"; }
        public String getCatalogTitle() { return ""; }
        public String getInstallNewLabel() { return ""; }
        public boolean showOccupiedMessageWhenPowered() { return false; }
        public Vector<WorkbenchCatalogEntry> getCatalogEntries() {
            return new Vector<WorkbenchCatalogEntry>();
        }
        public Vector<PhysicalPart<?>> getLooseParts() {
            return new Vector<PhysicalPart<?>>(parts);
        }
        public String getPartLabel(PhysicalPart<?> part) {
            return part == null ? "" : part.getId();
        }
        public PhysicalPart<?> getPart(String partId) {
            for (PhysicalPart<?> part : parts)
                if (part.getId().equals(partId))
                    return part;
            return null;
        }
        public boolean ownsPart(String partId) { return getPart(partId) != null; }
    }

    private static void verifyInstalledNegativeCanaries(CirSim sim,
            PhysicalPartRenderRegistry registry) {
        require(sim != null && registry != null,
            "Installed negative canaries require a live render context");
        InstalledRenderNegativeFixture fixture = InstalledRenderNegativeFixture.create(sim);
        GeneratedBoardInstance savedInstance = sim.generatedBoardInstance;
        BoardModificationController savedModifications = sim.boardModificationController;
        try {
            // Use a detached verification board and its own modification view so the
            // malformed installed projection cannot change the live challenge state.
            sim.generatedBoardInstance = fixture.instance;
            sim.boardModificationController = fixture.modifications;
            verifyInstalledProbeOverlapNegative(sim, registry, fixture);
            verifyInstalledDetachedMarkerNegative(sim, registry, fixture);
        } finally {
            sim.generatedBoardInstance = savedInstance;
            sim.boardModificationController = savedModifications;
        }
    }

    private static void verifyInstalledProbeOverlapNegative(CirSim sim,
            PhysicalPartRenderRegistry registry, InstalledRenderNegativeFixture fixture) {
        PhysicalPartRenderProvider standardProvider = registry.getProvider(fixture.part.getPackage());
        require(standardProvider != null && standardProvider.getRenderer(fixture.part) != null,
            "Installed overlap canary has no standard package renderer");
        PcbWorkbenchRenderer canonicalRenderer = new PcbWorkbenchRenderer(fixture.instance,
            fixture.modifications, fixture.layout, registry);
        PhysicalPartRenderGeometry canonicalGeometry = canonicalRenderer
            .getInstalledGeometryForDeveloperVerification(fixture.componentId);
        PhysicalPartRenderTerminal canonicalTerminal = findTerminal(canonicalGeometry,
            fixture.liftedPadId);
        require(canonicalTerminal != null && canonicalTerminal.getBoardPadId() != null &&
                canonicalTerminal.getBoardPadPoint() != null &&
                canonicalTerminal.getBoardPadProbeBounds() != null &&
                canonicalTerminal.getComponentLeadPoint() != null &&
                canonicalTerminal.getComponentLeadProbeBounds() != null,
            "Installed overlap canary has incomplete canonical installed surfaces");

        PhysicalPartRenderRegistry malformedRegistry = new PhysicalPartRenderRegistry();
        malformedRegistry.register(fixture.part.getPackage(),
            new InstalledOverlapProjectionProvider(standardProvider, canonicalTerminal
                .getTerminalIndex()));
        PcbWorkbenchRenderer malformedRenderer = new PcbWorkbenchRenderer(fixture.instance,
            fixture.modifications, fixture.layout, malformedRegistry);
        PhysicalPartRenderGeometry malformedGeometry = malformedRenderer
            .getInstalledGeometryForDeveloperVerification(fixture.componentId);
        PhysicalPartRenderTerminal malformedTerminal = findTerminal(malformedGeometry,
            fixture.liftedPadId);
        require(malformedTerminal != null && malformedTerminal.getBoardPadId() != null &&
                malformedTerminal.getBoardPadProbeBounds().equals(
                    malformedTerminal.getComponentLeadProbeBounds()) &&
                pointEquals(malformedTerminal.getBoardPadPoint(),
                    malformedTerminal.getComponentLeadPoint()) &&
                pointEquals(malformedTerminal.getPoint(), malformedTerminal.getBoardPadPoint()),
            "Installed overlap canary did not create a complete same-terminal probe collapse");

        Rectangle collapsedSurface = malformedTerminal.getComponentLeadProbeBounds();
        for (int y = collapsedSurface.y; y < collapsedSurface.y + collapsedSurface.height; y++)
            for (int x = collapsedSurface.x; x < collapsedSurface.x + collapsedSurface.width; x++) {
                ProbeTarget hit = malformedRenderer.findProbeTarget(sim, x, y);
                require(hit instanceof BoardPadProbeTarget && hit.isValid() &&
                        fixture.liftedPadId.equals(((BoardPadProbeTarget) hit).getPadId()),
                    "Installed overlap canary exposed an ambiguous component target or lost " +
                        "board-pad precedence: " + fixture.liftedPadId);
                require(!(hit instanceof ComponentLeadProbeTarget && hit.isValid()),
                    "Installed overlap canary returned a valid component target at a collapsed " +
                        "board-pad surface: " + fixture.liftedPadId);
            }
    }

    private static void verifyInstalledDetachedMarkerNegative(CirSim sim,
            PhysicalPartRenderRegistry registry, InstalledRenderNegativeFixture fixture) {
        PcbWorkbenchRenderer canonicalRenderer = new PcbWorkbenchRenderer(fixture.instance,
            fixture.modifications, fixture.layout, registry);
        PhysicalPartRenderGeometry geometry = canonicalRenderer
            .getInstalledGeometryForDeveloperVerification(fixture.componentId);
        final PhysicalPartRenderTerminal terminal = findTerminal(geometry, fixture.liftedPadId);
        require(terminal != null && terminal.getBoardPadId() != null &&
                terminal.getComponentLeadProbeBounds() != null &&
                terminal.getComponentLeadPoint() != null,
            "Installed marker canary has incomplete canonical installed surfaces");
        final Rectangle componentProbe = terminal.getComponentLeadProbeBounds();
        final Point detachedOutside = new Point(componentProbe.x + componentProbe.width + 20,
            componentProbe.y + componentProbe.height / 2);
        require(!componentProbe.contains(detachedOutside.x, detachedOutside.y),
            "Installed marker canary did not place its detached marker outside the probe");
        expectInstalledRejection("Physical render component probe omits its center",
            new InstalledNegativeConstruction() {
                public void construct() {
                    new PhysicalPartRenderTerminal(terminal.getTerminalIndex(),
                        terminal.getTerminalId(), terminal.getBoardPadId(), terminal.getPoint(),
                        terminal.getProbeBounds(), terminal.getBoardPadPoint(),
                        terminal.getBoardPadProbeBounds(), terminal.getPadBounds(),
                        detachedOutside, componentProbe, terminal.getLeadBodyPoint(),
                        terminal.getLeadEndPoint(), terminal.getLeadBounds());
                }
            }, "detached installed marker outside component probe: " + fixture.liftedPadId);
    }

    private interface InstalledNegativeConstruction {
        void construct();
    }

    private static void expectInstalledRejection(String expectedMessage,
            InstalledNegativeConstruction construction, String label) {
        String actualMessage = null;
        try {
            construction.construct();
        } catch (IllegalArgumentException expected) {
            actualMessage = expected.getMessage();
        }
        require(expectedMessage.equals(actualMessage),
            "Installed negative canary had the wrong failure reason for " + label +
                ": expected " + expectedMessage + ", got " + actualMessage);
    }

    private static void verifyConnectedAndLiftedProbeSemantics(CirSim sim,
            PcbWorkbenchRenderer renderer) {
        GeneratedBoardInstance instance = sim.getGeneratedBoardInstance();
        InstalledLeadCandidate candidate = findMountedLeadCandidate(instance, sim
            .getBoardModificationController());
        if (candidate != null) {
            verifyInstalledLeadLifecycle(sim, renderer, candidate, 0);
            verifyInstalledLeadLifecycle(sim, renderer, candidate, 1);
            return;
        }
        verifyRemovedLeadState(sim, renderer);
    }

    private static InstalledLeadCandidate findMountedLeadCandidate(
            GeneratedBoardInstance instance, BoardModificationController modifications) {
        PhysicalBoardRuntime runtime = instance.getPhysicalBoardRuntime();
        Vector<String> componentIds = instance.getBoard().getComponentIds();
        Collections.sort(componentIds);
        InstalledLeadCandidate nonCapacitorFallback = null;
        InstalledLeadCandidate resistorFallback = null;
        InstalledLeadCandidate capacitorFallback = null;
        for (String componentId : componentIds) {
            BoardComponent component = instance.getBoard().getComponent(componentId);
            Vector<GeneratedComponentConnectionBinding> bindings = instance
                .getConnectionBindings().getForComponentOrEmpty(componentId);
            if (component == null || component.getPhysicalPackage().getTerminalCount() != 2 ||
                    bindings.size() != 2 || runtime.getMutationProvider(componentId) == null ||
                    modifications.getComponentState(componentId) != ComponentPhysicalState.INSTALLED)
                continue;
            PhysicalPart<?> part = runtime.getInstalledPart(componentId);
            if (!isPhysicallyMounted(runtime, componentId, part))
                continue;
            InstalledLeadCandidate found = new InstalledLeadCandidate(componentId, bindings, part);
            if (!isCapacitorPackage(component.getPhysicalPackage())) {
                if (resistorFallback == null && PhysicalPackages.AXIAL_RESISTOR.isEquivalentTo(
                        component.getPhysicalPackage()))
                    resistorFallback = found;
                if (nonCapacitorFallback == null)
                    nonCapacitorFallback = found;
            }
            if ("C1".equals(componentId))
                capacitorFallback = found;
        }
        return resistorFallback != null ? resistorFallback :
            (nonCapacitorFallback != null ? nonCapacitorFallback : capacitorFallback);
    }

    private static boolean isPhysicallyMounted(PhysicalBoardRuntime runtime,
            String componentId, PhysicalPart<?> part) {
        PhysicalBoardSlot slot = runtime.getSlot(componentId);
        return part != null && part.isInstalled() && slot != null &&
            slot.getInstalledPart() == part && part.getBoardSlot() == slot;
    }

    private static boolean isCapacitorPackage(PhysicalPackage physicalPackage) {
        return PhysicalPackages.RADIAL_ELECTROLYTIC_CAPACITOR.isEquivalentTo(physicalPackage) ||
            PhysicalPackages.RADIAL_CERAMIC_CAPACITOR.isEquivalentTo(physicalPackage);
    }

    private static void verifyRemovedLeadState(CirSim sim, PcbWorkbenchRenderer renderer) {
        GeneratedBoardInstance instance = sim.getGeneratedBoardInstance();
        BoardModificationController modifications = sim.getBoardModificationController();
        RemovedLeadCandidate candidate = findRemovedLeadCandidate(instance, modifications);
        require(candidate != null,
            "No mounted lead candidate and no physically removed lead owned by the loose projection");
        PhysicalBoardRuntime runtime = instance.getPhysicalBoardRuntime();
        PhysicalBoardSlot slot = runtime.getSlot(candidate.componentId);
        require(slot != null && slot.getInstalledPart() == null && !candidate.part.isInstalled() &&
                candidate.part.getBoardSlot() == null &&
                modifications.getComponentState(candidate.componentId) ==
                    ComponentPhysicalState.REMOVED,
            "Removed lead canary did not observe an empty physical slot: " +
                candidate.componentId);
        PhysicalPartRenderGeometry geometry = renderer
            .getInstalledGeometryForDeveloperVerification(candidate.componentId);
        for (GeneratedComponentConnectionBinding binding : candidate.bindings) {
            Point padPoint = renderer.getPadPoint(binding.getPadId());
            require(padPoint != null, "Removed-state board pad has no rendered point: " +
                binding.getPadId());
            ProbeTarget boardTarget = renderer.findProbeTarget(sim, padPoint.x, padPoint.y);
            require(boardTarget instanceof BoardPadProbeTarget && boardTarget.isValid() &&
                    binding.getPadId().equals(((BoardPadProbeTarget) boardTarget).getPadId()),
                "Removed-state board pad no longer resolved as a board target: " +
                    binding.getPadId());
            PhysicalPartRenderTerminal terminal = findTerminal(geometry, binding.getPadId());
            require(terminal != null && renderer.getComponentLeadPoint(candidate.componentId,
                    binding.getPadId()) == null,
                "Removed-state component still exposed an installed lead point: " +
                    binding.getPadId());
            Point componentPoint = terminal.getComponentLeadPoint();
            Rectangle boardProbe = terminal.getBoardPadProbeBounds();
            Rectangle componentProbe = terminal.getComponentLeadProbeBounds();
            require(componentPoint != null && boardProbe != null && componentProbe != null &&
                    componentProbe.contains(componentPoint.x, componentPoint.y) &&
                    !componentProbe.intersects(boardProbe) &&
                    !boardProbe.contains(componentPoint.x, componentPoint.y),
                "Removed-state provider geometry overlaps its board-pad surface: " +
                    binding.getPadId());
            require(renderer.findProbeTarget(sim, componentPoint.x, componentPoint.y) == null,
                "Removed-state installed lead point remained reachable: " + binding.getPadId());
        }
        verifyRemovedLooseCarrier(renderer, candidate.part,
            candidate.part.getGeometryRealization());
        verifyRemovedLooseProbeOwnership(sim, renderer, candidate.part);
    }

    private static RemovedLeadCandidate findRemovedLeadCandidate(
            GeneratedBoardInstance instance, BoardModificationController modifications) {
        PhysicalBoardRuntime runtime = instance.getPhysicalBoardRuntime();
        Vector<String> componentIds = instance.getBoard().getComponentIds();
        Collections.sort(componentIds);
        for (String componentId : componentIds) {
            BoardComponent component = instance.getBoard().getComponent(componentId);
            Vector<GeneratedComponentConnectionBinding> bindings = instance
                .getConnectionBindings().getForComponentOrEmpty(componentId);
            PhysicalBoardSlot slot = runtime.getSlot(componentId);
            PhysicalSlotMutationProvider provider = runtime.getMutationProvider(componentId);
            if (component == null || component.getPhysicalPackage().getTerminalCount() != 2 ||
                    bindings.size() != 2 || provider == null || slot == null ||
                    slot.getInstalledPart() != null || modifications.getComponentState(componentId) !=
                        ComponentPhysicalState.REMOVED)
                continue;
            WorkbenchPartsProvider partsProvider = runtime.getWorkbenchPartsProvider(componentId);
            PhysicalPart<?> part = findLoosePart(partsProvider, component.getPhysicalPackage());
            if (part != null)
                return new RemovedLeadCandidate(componentId, bindings, part);
        }
        return null;
    }

    private static PhysicalPart<?> findLoosePart(WorkbenchPartsProvider provider,
            PhysicalPackage physicalPackage) {
        if (provider == null)
            return null;
        PhysicalPart<?> fallback = null;
        for (PhysicalPart<?> part : provider.getLooseParts()) {
            if (part == null || part.isInstalled() ||
                    !physicalPackage.isEquivalentTo(part.getPackage()))
                continue;
            if (part.isOriginal())
                return part;
            if (fallback == null)
                fallback = part;
        }
        return fallback;
    }

    private static void verifyInstalledLeadLifecycle(CirSim sim,
            PcbWorkbenchRenderer renderer, InstalledLeadCandidate candidate,
            int terminalPosition) {
        GeneratedBoardInstance instance = sim.getGeneratedBoardInstance();
        BoardModificationController modifications = sim.getBoardModificationController();
        PhysicalBoardRuntime runtime = instance.getPhysicalBoardRuntime();
        PhysicalSlotMutationProvider mutationProvider = runtime.getMutationProvider(
            candidate.componentId);
        PhysicalBoardSlot slot = runtime.getSlot(candidate.componentId);
        GeneratedComponentConnectionBinding binding = candidate.bindings.get(terminalPosition);
        require(mutationProvider != null && slot != null && slot.getInstalledPart() == candidate.part &&
                isPhysicallyMounted(runtime, candidate.componentId, candidate.part),
            "Render probe lifecycle canary has no mounted physical slot: " +
                candidate.componentId);

        Point padPoint = renderer.getPadPoint(binding.getPadId());
        require(padPoint != null, "Candidate board pad has no rendered point: " +
            binding.getPadId());
        ProbeTarget connectedPad = renderer.findProbeTarget(sim, padPoint.x, padPoint.y);
        require(connectedPad instanceof BoardPadProbeTarget && connectedPad.isValid() &&
                binding.getPadId().equals(((BoardPadProbeTarget) connectedPad).getPadId()),
            "Connected board pad did not resolve as a board target: " + binding.getPadId());

        PhysicalPartRenderGeometry connectedGeometry = renderer
            .getInstalledGeometryForDeveloperVerification(candidate.componentId);
        PhysicalPartRenderTerminal connectedTerminal = findTerminal(connectedGeometry,
            binding.getPadId());
        require(connectedTerminal != null && renderer.getComponentLeadPoint(candidate.componentId,
                binding.getPadId()) == null,
            "Connected two-terminal part exposed an installed component target: " +
                binding.getPadId());
        Point connectedComponentPoint = connectedTerminal.getComponentLeadPoint();
        Rectangle connectedBoardProbe = connectedTerminal.getBoardPadProbeBounds();
        Rectangle connectedComponentProbe = connectedTerminal.getComponentLeadProbeBounds();
        require(connectedComponentPoint != null && connectedBoardProbe != null &&
                connectedComponentProbe != null && connectedComponentProbe.contains(
                    connectedComponentPoint.x, connectedComponentPoint.y) &&
                !connectedComponentProbe.intersects(connectedBoardProbe) &&
                !connectedBoardProbe.contains(connectedComponentPoint.x,
                    connectedComponentPoint.y) &&
                renderer.findProbeTarget(sim, connectedComponentPoint.x,
                    connectedComponentPoint.y) == null,
            "Connected component-side probe surface was reachable or overlapped the board pad: " +
                binding.getPadId());
        ComponentLeadProbeTarget connectedLead = new ComponentLeadProbeTarget(sim, instance,
            candidate.componentId, binding.getPadId(), renderer, candidate.part.getId(),
            binding.getComponentEndpoint());
        require(!connectedLead.isValid(),
            "Connected named lead was accepted as a component-side target: " + binding.getPadId());

        BoardPowerController power = sim.getBoardPowerController();
        BoardPowerState savedPower = power.getState();
        PhysicalGeometryRealization carrier = candidate.part.getGeometryRealization();
        BoardPad boardPad = instance.getBoard().getPad(binding.getPadId());
        PhysicalPartTerminal stableTerminal = findPartTerminal(candidate.part,
            boardPad == null ? null : boardPad.getTerminalId());
            require(carrier != null && stableTerminal != null &&
                sameCircuitPostEndpoint(binding.getComponentEndpoint(),
                    stableTerminal.getEndpoint()),
            "Render probe lifecycle canary lost physical terminal identity before mutation: " +
                binding.getPadId());
        String stablePartId = candidate.part.getId();
        CircuitMeasurementEndpoint stableEndpoint = stableTerminal.getEndpoint();
        try {
            if (!power.isElectricallyUnpowered())
                power.setState(BoardPowerState.UNPOWERED);
            require(power.isElectricallyUnpowered(),
                "Render probe canary could not establish safe unpowered mutation state");
            require(modifications.liftLead(candidate.componentId, binding.getPadId()),
                "Render probe canary could not lift named lead: " + binding.getPadId());

            PhysicalPartRenderGeometry geometry = renderer
                .getInstalledGeometryForDeveloperVerification(candidate.componentId);
            PhysicalPartRenderTerminal terminal = findTerminal(geometry, binding.getPadId());
            require(terminal != null && renderer.getComponentLeadPoint(candidate.componentId,
                    binding.getPadId()) != null,
                "Lifted named lead lost its package component-side geometry: " +
                    binding.getPadId());
            Point componentSide = terminal.getComponentLeadPoint();
            Rectangle boardPadProbe = terminal.getBoardPadProbeBounds();
            Rectangle componentProbe = terminal.getComponentLeadProbeBounds();
            require(componentSide != null && componentProbe != null && boardPadProbe != null &&
                    componentProbe.contains(componentSide.x, componentSide.y) &&
                    terminal.containsComponentProbe(componentSide.x, componentSide.y,
                        boardPadProbe) && geometry.contains(componentSide.x, componentSide.y) &&
                    !componentProbe.intersects(boardPadProbe) &&
                    !boardPadProbe.contains(componentSide.x, componentSide.y) &&
                    pointEquals(componentSide, renderer.getComponentLeadPoint(
                        candidate.componentId, binding.getPadId())),
                "Lifted lead did not expose a distinct visible detached component surface: " +
                    binding.getPadId());
            ProbeTarget liftedTarget = renderer.findProbeTarget(sim, componentSide.x,
                componentSide.y);
            require(liftedTarget instanceof ComponentLeadProbeTarget &&
                    liftedTarget.isValid(),
                "Lifted named lead did not resolve as a valid component target: " +
                    binding.getPadId());
            ComponentLeadProbeTarget componentTarget = (ComponentLeadProbeTarget) liftedTarget;
            require(candidate.componentId.equals(
                    componentTarget.getComponentIdForDeveloperVerification()) &&
                    binding.getPadId().equals(componentTarget.getPadIdForDeveloperVerification()) &&
                    stablePartId.equals(componentTarget.getPhysicalPartIdForDeveloperVerification()) &&
                    liftedTarget.getMeasurementEndpoint() == stableEndpoint &&
                    pointEquals(componentTarget.getMarkerPoint(), componentSide),
                "Lifted component target changed stable physical or endpoint identity: " +
                    binding.getPadId());
            ComponentLeadProbeTarget equivalent = new ComponentLeadProbeTarget(sim, instance,
                candidate.componentId, binding.getPadId(), renderer, stablePartId,
                binding.getComponentEndpoint());
            require(componentTarget.isSameTarget(equivalent),
                "Lifted component target identity was not stable: " + binding.getPadId());
            ProbeTarget liftedPad = renderer.findProbeTarget(sim, padPoint.x, padPoint.y);
            require(liftedPad instanceof BoardPadProbeTarget && liftedPad.isValid() &&
                    liftedPad.getMeasurementEndpoint() == binding.getBoardEndpoint() &&
                    liftedPad.getMeasurementEndpoint() != componentTarget.getMeasurementEndpoint() &&
                    !liftedPad.isSameTarget(componentTarget),
                "Lifted board/component probe surfaces did not remain distinct: " +
                    binding.getPadId());

            GeneratedComponentConnectionBinding otherBinding = candidate.bindings.get(
                terminalPosition == 0 ? 1 : 0);
            ComponentLeadProbeTarget wrongPadTarget = new ComponentLeadProbeTarget(sim, instance,
                candidate.componentId, otherBinding.getPadId(), renderer, stablePartId,
                stableEndpoint);
            require(!wrongPadTarget.isValid(),
                "Component target accepted the wrong terminal binding: " + binding.getPadId());
            ComponentLeadProbeTarget wrongComponentTarget = new ComponentLeadProbeTarget(sim,
                instance, candidate.componentId + "_WRONG", binding.getPadId(), renderer,
                stablePartId, stableEndpoint);
            require(!wrongComponentTarget.isValid(),
                "Component target accepted the wrong component binding: " + binding.getPadId());
            require(modifications.reconnectLead(candidate.componentId, binding.getPadId()),
                "Render probe canary could not reconnect named lead: " + binding.getPadId());
            require(!componentTarget.isValid() && componentTarget.getMarkerPoint() == null &&
                    renderer.getComponentLeadPoint(candidate.componentId,
                        binding.getPadId()) == null && renderer.findProbeTarget(sim,
                        componentSide.x, componentSide.y) == null,
                "Reconnected lead retained a stale component-side target: " +
                    binding.getPadId());
            ProbeTarget reconnectedPad = renderer.findProbeTarget(sim, padPoint.x, padPoint.y);
            require(reconnectedPad instanceof BoardPadProbeTarget && reconnectedPad.isValid(),
                "Reconnected lead did not restore board-pad resolution: " + binding.getPadId());

            require(modifications.liftLead(candidate.componentId, binding.getPadId()),
                "Render probe lifecycle canary could not re-lift before physical removal: " +
                    binding.getPadId());
            PhysicalPartRenderGeometry reliftedGeometry = renderer
                .getInstalledGeometryForDeveloperVerification(candidate.componentId);
            PhysicalPartRenderTerminal reliftedTerminal = findTerminal(reliftedGeometry,
                binding.getPadId());
            require(reliftedTerminal != null && renderer.getComponentLeadPoint(
                    candidate.componentId, binding.getPadId()) != null,
                "Relifted lead did not regain its mounted component-side surface: " +
                    binding.getPadId());
            Point reliftedPoint = reliftedTerminal.getComponentLeadPoint();
            ProbeTarget beforeGraphRemoval = renderer.findProbeTarget(sim, reliftedPoint.x,
                reliftedPoint.y);
            require(beforeGraphRemoval instanceof ComponentLeadProbeTarget &&
                    beforeGraphRemoval.isValid(),
                "Relifted lead did not provide a target before graph-only removal: " +
                    binding.getPadId());
            require(modifications.removeComponent(candidate.componentId) &&
                    modifications.getComponentState(candidate.componentId) ==
                        ComponentPhysicalState.REMOVED && slot.getInstalledPart() == candidate.part &&
                    candidate.part.isInstalled() && isPhysicallyMounted(runtime,
                        candidate.componentId, candidate.part) && renderer.getComponentLeadPoint(
                        candidate.componentId, binding.getPadId()) != null &&
                    candidate.componentId.equals(renderer.findComponentId(
                        reliftedGeometry.getBodyBounds().x + reliftedGeometry.getBodyBounds().width / 2,
                        reliftedGeometry.getBodyBounds().y + reliftedGeometry.getBodyBounds().height / 2)) &&
                    liftedPad.isValid() && !componentTarget.isValid() &&
                    !beforeGraphRemoval.isValid(),
                "Graph-only removal was mistaken for final physical slot removal: " +
                    binding.getPadId());
            ProbeTarget graphRemovedTarget = renderer.findProbeTarget(sim, reliftedPoint.x,
                reliftedPoint.y);
            require(graphRemovedTarget instanceof ComponentLeadProbeTarget &&
                    graphRemovedTarget.isValid() && stablePartId.equals(
                        ((ComponentLeadProbeTarget) graphRemovedTarget)
                            .getPhysicalPartIdForDeveloperVerification()) &&
                    graphRemovedTarget.getMeasurementEndpoint() == stableEndpoint,
                "Graph-only removal lost the still-mounted component-side target: " +
                    binding.getPadId());
            require(mutationProvider.removeInstalledPart() && slot.getInstalledPart() == null &&
                    !candidate.part.isInstalled() && candidate.part.getBoardSlot() == null,
                "Physical removal did not reach final slot-empty state: " + binding.getPadId());
            verifyRemovedLooseCarrier(renderer, candidate.part, carrier);
            verifyRemovedLooseProbeOwnership(sim, renderer, candidate.part);
            require(liftedPad.isValid() && !componentTarget.isValid() &&
                    !graphRemovedTarget.isValid() && renderer.getComponentLeadPoint(
                        candidate.componentId, binding.getPadId()) == null &&
                    renderer.findProbeTarget(sim, reliftedPoint.x, reliftedPoint.y) == null &&
                    renderer.findComponentId(reliftedGeometry.getBodyBounds().x +
                        reliftedGeometry.getBodyBounds().width / 2,
                        reliftedGeometry.getBodyBounds().y +
                        reliftedGeometry.getBodyBounds().height / 2) == null,
                "Final physical removal did not invalidate installed interaction: " +
                    binding.getPadId());

            require(mutationProvider.install(stablePartId) && slot.getInstalledPart() == candidate.part &&
                    candidate.part.isInstalled() && candidate.part.getGeometryRealization() == carrier &&
                    findPartTerminal(candidate.part, stableTerminal.getTerminalName()) == stableTerminal &&
                    stableTerminal.getEndpoint() == stableEndpoint &&
                    modifications.getComponentState(candidate.componentId) ==
                        ComponentPhysicalState.INSTALLED,
                "Same-part physical reinstall changed stable physical identity: " +
                    binding.getPadId());
            require(!componentTarget.isValid() && !graphRemovedTarget.isValid() &&
                    renderer.getComponentLeadPoint(candidate.componentId,
                        binding.getPadId()) == null,
                "Old detached target revived across same-part physical reinstall: " +
                    binding.getPadId());
            ProbeTarget reinstalledPad = renderer.findProbeTarget(sim, padPoint.x, padPoint.y);
            require(reinstalledPad instanceof BoardPadProbeTarget && reinstalledPad.isValid() &&
                    renderer.getComponentLeadPoint(candidate.componentId,
                        binding.getPadId()) == null,
                "Connected reinstall did not expose board-side probing only: " +
                    binding.getPadId());
            require(modifications.liftLead(candidate.componentId, binding.getPadId()),
                "Render probe lifecycle canary could not lift after same-part reinstall: " +
                    binding.getPadId());
            PhysicalPartRenderGeometry reinstalledGeometry = renderer
                .getInstalledGeometryForDeveloperVerification(candidate.componentId);
            PhysicalPartRenderTerminal reinstalledTerminal = findTerminal(reinstalledGeometry,
                binding.getPadId());
            require(reinstalledTerminal != null, "Reinstalled provider omitted terminal: " +
                binding.getPadId());
            Point reinstalledPoint = reinstalledTerminal.getComponentLeadPoint();
            ProbeTarget reinstalledTarget = renderer.findProbeTarget(sim, reinstalledPoint.x,
                reinstalledPoint.y);
            require(reinstalledTarget instanceof ComponentLeadProbeTarget &&
                    reinstalledTarget.isValid() && stablePartId.equals(
                        ((ComponentLeadProbeTarget) reinstalledTarget)
                            .getPhysicalPartIdForDeveloperVerification()) &&
                    reinstalledTarget.getMeasurementEndpoint() == stableEndpoint &&
                    pointEquals(reinstalledTarget.getMarkerPoint(), reinstalledPoint) &&
                    !componentTarget.isSameTarget(reinstalledTarget) &&
                    !graphRemovedTarget.isSameTarget(reinstalledTarget),
                "Same-part reinstall did not recreate a distinct exact component target: " +
                    binding.getPadId());

            verifyReplacementEndpointInvalidation(sim,
                renderer.getRenderRegistryForDeveloperVerification());

            ComponentLeadProbeTarget replacementIdentityTarget = new ComponentLeadProbeTarget(sim,
                instance, candidate.componentId, binding.getPadId(), renderer,
                stablePartId + "_REPLACEMENT", stableEndpoint);
            require(!replacementIdentityTarget.isValid(),
                "Component target accepted a replaced physical-part identity: " +
                    binding.getPadId());
        } finally {
            PhysicalPart<?> installed = slot.getInstalledPart();
            if (installed != null && installed != candidate.part)
                mutationProvider.removeInstalledPart();
            if (slot.getInstalledPart() == null)
                mutationProvider.install(stablePartId);
            if (modifications.getComponentState(candidate.componentId) !=
                    ComponentPhysicalState.INSTALLED)
                modifications.restoreComponent(candidate.componentId);
            if (savedPower == BoardPowerState.POWERED)
                power.setState(BoardPowerState.POWERED);
            else
                power.setState(BoardPowerState.UNPOWERED);
        }
    }

    private static void verifyReplacementEndpointInvalidation(CirSim sim,
            PhysicalPartRenderRegistry registry) {
        GeneratedBoardInstance savedInstance = sim.generatedBoardInstance;
        BoardModificationController savedModifications = sim.boardModificationController;
        GeneratedChallengeController savedChallengeController = sim.generatedChallengeController;
        PcbWorkbenchController savedWorkbench = sim.pcbWorkbenchController;
        Vector<CircuitElm> savedElements = sim.elmList;
        BoardPowerState savedPower = sim.getBoardPowerController().getState();
        boolean savedAnalyzeFlag = sim.analyzeFlag;
        boolean savedNeedsRepaint = sim.needsRepaint;
        boolean savedVerificationPending = sim.generatedBoardVerificationPending;
        boolean savedVerificationAnalyzed = sim.generatedBoardVerificationAnalyzed;
        double savedVerificationStartTime = sim.generatedBoardVerificationStartTime;
        GeneratedBoardInstance isolatedInstance = null;
        BoardModificationController isolatedModifications = null;
        PhysicalSlotMutationProvider isolatedMutationProvider = null;
        PhysicalBoardSlot isolatedSlot = null;
        sim.beginObservationalValidation();
        try {
            // Catalog acquisition is intentionally append-only in the production
            // runtime.  Exercise the real provider on a disposable generated board
            // so its acquired identity cannot enter the live board fingerprint.
            isolatedInstance = new LedIndicatorGenerator().generateForFaultVerification(0,
                GeneratedFaultType.RESISTOR_OPEN);
            isolatedModifications = new BoardModificationController(sim, isolatedInstance);
            sim.generatedBoardInstance = isolatedInstance;
            sim.generatedChallengeController = null;
            sim.boardModificationController = isolatedModifications;
            sim.pcbWorkbenchController = null;
            sim.elmList = isolatedInstance.getSimulationElements();
            isolatedInstance.getPhysicalBoardRuntime().installRegisteredCapabilities(sim,
                isolatedInstance, isolatedModifications, sim.t);

            PcbWorkbenchRenderer isolatedRenderer = new PcbWorkbenchRenderer(isolatedInstance,
                isolatedModifications, isolatedInstance.getPcbLayout(), registry);
            InstalledLeadCandidate candidate = findMountedLeadCandidate(isolatedInstance,
                isolatedModifications);
            require(candidate != null && "R1".equals(candidate.componentId),
                "Replacement endpoint canary has no isolated resistor candidate");
            GeneratedComponentConnectionBinding binding = candidate.bindings.get(0);
            PhysicalBoardRuntime runtime = isolatedInstance.getPhysicalBoardRuntime();
            isolatedMutationProvider = runtime.getMutationProvider(candidate.componentId);
            isolatedSlot = runtime.getSlot(candidate.componentId);
            WorkbenchPartsProvider partsProvider = runtime.getWorkbenchPartsProvider(
                candidate.componentId);
            require(isolatedMutationProvider != null && isolatedSlot != null &&
                    partsProvider != null && partsProvider.getCatalogEntries() != null &&
                    !partsProvider.getCatalogEntries().isEmpty(),
                "Replacement endpoint canary has no isolated catalog path");
            require(isolatedModifications.liftLead(candidate.componentId, binding.getPadId()),
                "Replacement endpoint canary could not lift the isolated original lead: " +
                    binding.getPadId());
            Point originalPoint = isolatedRenderer.getComponentLeadPoint(candidate.componentId,
                binding.getPadId());
            ProbeTarget originalTarget = originalPoint == null ? null :
                isolatedRenderer.findProbeTarget(sim, originalPoint.x, originalPoint.y);
            require(originalPoint != null && originalTarget instanceof ComponentLeadProbeTarget &&
                    originalTarget.isValid(),
                "Replacement endpoint canary did not start from a valid isolated target: " +
                    binding.getPadId());

            String originalPartId = candidate.part.getId();
            BoardPad boardPad = isolatedInstance.getBoard().getPad(binding.getPadId());
            PhysicalPartTerminal originalTerminal = findPartTerminal(candidate.part,
                boardPad == null ? null : boardPad.getTerminalId());
            require(originalTerminal != null,
                "Replacement endpoint canary could not resolve the isolated original lead: " +
                    binding.getPadId());
            CircuitMeasurementEndpoint originalEndpoint = originalTerminal.getEndpoint();
            WorkbenchCatalogEntry catalogEntry = partsProvider.getCatalogEntries().firstElement();
            require(isolatedMutationProvider.removeInstalledPart(),
                "Replacement endpoint canary could not remove the isolated original part: " +
                    candidate.componentId);
            require(isolatedMutationProvider.installNewFromCatalog(catalogEntry.getId()),
                "Replacement endpoint canary could not install an isolated catalog part: " +
                    candidate.componentId);
            PhysicalPart<?> replacement = isolatedSlot.getInstalledPart();
            require(replacement != null && replacement != candidate.part &&
                    !originalPartId.equals(replacement.getId()) && replacement.isInstalled(),
                "Replacement endpoint canary did not create a new isolated physical part: " +
                    candidate.componentId);
            PhysicalPartTerminal replacementTerminal = findPartTerminal(replacement,
                boardPad == null ? null : boardPad.getTerminalId());
            require(replacementTerminal != null && replacementTerminal.getEndpoint() !=
                    originalEndpoint,
                "Replacement endpoint canary did not create a distinct isolated endpoint: " +
                    binding.getPadId());
            require(!originalTarget.isValid() && originalTarget.getMarkerPoint() == null &&
                    originalTarget.getMeasurementEndpoint() == originalEndpoint &&
                    originalTarget.getMeasurementEndpoint() != replacementTerminal.getEndpoint() &&
                    isolatedRenderer.getComponentLeadPoint(candidate.componentId,
                        binding.getPadId()) == null &&
                    isolatedRenderer.findProbeTarget(sim, originalPoint.x, originalPoint.y) == null,
                "Installed target silently migrated to an isolated replacement endpoint: " +
                    binding.getPadId());

            require(isolatedModifications.liftLead(candidate.componentId, binding.getPadId()),
                "Replacement endpoint canary could not lift the isolated replacement lead: " +
                    binding.getPadId());
            Point replacementPoint = isolatedRenderer.getComponentLeadPoint(
                candidate.componentId, binding.getPadId());
            require(replacementPoint != null,
                "Replacement endpoint canary did not expose the isolated replacement lead: " +
                    binding.getPadId());
            ProbeTarget replacementTarget = isolatedRenderer.findProbeTarget(sim,
                replacementPoint.x, replacementPoint.y);
            require(replacementTarget instanceof ComponentLeadProbeTarget &&
                    replacementTarget.isValid() && replacementTarget != originalTarget &&
                    !replacementTarget.isSameTarget(originalTarget) &&
                    replacement.getId().equals(((ComponentLeadProbeTarget) replacementTarget)
                        .getPhysicalPartIdForDeveloperVerification()) &&
                    replacementTarget.getMeasurementEndpoint() == replacementTerminal.getEndpoint() &&
                    replacementTarget.getMeasurementEndpoint() != originalEndpoint,
                "Replacement lead did not acquire a fresh isolated physical endpoint target: " +
                    binding.getPadId());
        } finally {
            try {
                if (isolatedMutationProvider != null && isolatedSlot != null &&
                        isolatedSlot.getInstalledPart() != null)
                    isolatedMutationProvider.removeInstalledPart();
            } finally {
                sim.generatedBoardInstance = savedInstance;
                sim.generatedChallengeController = savedChallengeController;
                sim.boardModificationController = savedModifications;
                sim.pcbWorkbenchController = savedWorkbench;
                sim.elmList = savedElements;
                sim.analyzeFlag = savedAnalyzeFlag;
                sim.needsRepaint = savedNeedsRepaint;
                sim.generatedBoardVerificationPending = savedVerificationPending;
                sim.generatedBoardVerificationAnalyzed = savedVerificationAnalyzed;
                sim.generatedBoardVerificationStartTime = savedVerificationStartTime;
                require(sim.getBoardPowerController().getState() == savedPower,
                    "Isolated replacement canary changed live board power state");
                sim.endObservationalValidation();
            }
        }
    }

    private static final class InstalledRenderNegativeFixture {
        final String componentId;
        final String liftedPadId;
        final PhysicalPackage physicalPackage;
        final PhysicalPart<?> part;
        final GeneratedBoardInstance instance;
        final BoardModificationController modifications;
        final PcbBoardLayout layout;

        private InstalledRenderNegativeFixture(String componentId, String liftedPadId,
                PhysicalPackage physicalPackage, PhysicalPart<?> part,
                GeneratedBoardInstance instance, BoardModificationController modifications,
                PcbBoardLayout layout) {
            this.componentId = componentId;
            this.liftedPadId = liftedPadId;
            this.physicalPackage = physicalPackage;
            this.part = part;
            this.instance = instance;
            this.modifications = modifications;
            this.layout = layout;
        }

        static InstalledRenderNegativeFixture create(CirSim sim) {
            String componentId = "TASK43_INSTALLED_RENDER_NEGATIVE";
            String liftedPadId = componentId + ".1";
            PhysicalPackage physicalPackage = PhysicalPackages.AXIAL_RESISTOR;
            TroubleshootBoard board = new TroubleshootBoard(componentId);
            board.addNet(new BoardNet(componentId + ".NET.1"));
            board.addNet(new BoardNet(componentId + ".NET.2"));
            board.addComponent(new BoardComponent(componentId, "RESISTOR", physicalPackage));
            board.addPad(new BoardPad(liftedPadId, componentId, "1", componentId + ".NET.1"));
            board.addPad(new BoardPad(componentId + ".2", componentId, "2",
                componentId + ".NET.2"));
            board.validate();

            PcbFootprint footprint = PcbFootprint.fromPhysicalPackage(
                board.getComponent(componentId), 180, 120);
            PcbBoardLayout layout = new PcbBoardLayout(900, 600,
                new Rectangle(20, 20, 700, 500), new Rectangle(740, 20, 140, 500));
            layout.addComponent(footprint.getPlacement());
            for (PcbPadPlacement pad : footprint.getPads())
                layout.addPad(pad);
            layout.validateAgainst(board);

            PhysicalBoardRuntime runtime = new PhysicalBoardRuntime(board);
            PhysicalBoardSlot slot = runtime.createSlot(componentId);
            LooseRenderCanaryPart part = LooseRenderCanaryPart.create(componentId + "_PART",
                physicalPackage, false);
            slot.install(part);
            runtime.bindGeometryRealizations(layout);

            Vector<CircuitElm> simulationElements = new Vector<CircuitElm>();
            Vector<CircuitElm> partElements = part.getElectricalBacking().getCircuitElements();
            simulationElements.addAll(partElements);
            GeneratedComponentBindings componentBindings = new GeneratedComponentBindings(board);
            componentBindings.bindComponentElements(componentId, partElements);
            GeneratedComponentConnectionBindings connectionBindings =
                new GeneratedComponentConnectionBindings(board);
            for (int index = 0; index < physicalPackage.getTerminalCount(); index++) {
                String padId = componentId + "." + physicalPackage.getTerminalIds().get(index);
                WireElm boardWire = new WireElm(700 + index * 80, 1000);
                boardWire.drag(716 + index * 80, 1000);
                CircuitPostMeasurementEndpoint boardEndpoint =
                    new CircuitPostMeasurementEndpoint(boardWire, 0);
                board.getSimulationBindings().bindPad(padId, boardEndpoint);
                CircuitMeasurementEndpoint componentEndpoint = part.getTerminal(index)
                    .getEndpoint();
                require(componentEndpoint instanceof CircuitPostMeasurementEndpoint,
                    "Installed negative canary part endpoint is not a CircuitJS post");
                CircuitPostMeasurementEndpoint componentPost =
                    (CircuitPostMeasurementEndpoint) componentEndpoint;
                Point boardPoint = boardEndpoint.getElement().getPost(boardEndpoint.getPostIndex());
                Point componentPoint = componentPost.getElement().getPost(
                    componentPost.getPostIndex());
                WireElm connection = new WireElm(boardPoint.x, boardPoint.y);
                connection.setPosition(boardPoint.x, boardPoint.y, componentPoint.x,
                    componentPoint.y);
                simulationElements.add(boardWire);
                simulationElements.add(connection);
                connectionBindings.bind(componentId, padId, boardEndpoint, componentEndpoint,
                    connection);
            }
            board.validate();
            runtime.validate();

            BoardPhysicalSpecifications specifications = new BoardPhysicalSpecifications();
            specifications.addPhysicalDefinition(componentId, part.getSpecification(),
                part.getPlayerVisibleNameplate(), physicalPackage);
            GeneratedExternalPowerBindings powerBindings =
                new GeneratedExternalPowerBindings(board);
            GeneratedChallengeBehaviorContract behavior =
                new GeneratedChallengeBehaviorContract() {
                    public void verifyHealthy(GeneratedBoardInstance instance,
                            BoardPowerState powerState) { }
                    public void verifyFaulted(GeneratedBoardInstance instance,
                            BoardModificationController modifications,
                            BoardPowerState powerState) { }
                    public GeneratedRepairStatus getRepairStatus(
                            GeneratedBoardInstance instance,
                            BoardModificationController modifications,
                            BoardPowerState powerState, boolean activeMeasurementOverlay) {
                        return GeneratedRepairStatus.STILL_FAULTED_OR_NONFUNCTIONAL;
                    }
                    public boolean isFunctionallyRepaired(GeneratedBoardInstance instance,
                            BoardModificationController modifications,
                            BoardPowerState powerState, boolean activeMeasurementOverlay) {
                        return false;
                    }
                };
            GeneratedBoardInstance instance = new GeneratedBoardInstance(board,
                simulationElements, 43L, QuickPlayFamilyRegistry.LED_INDICATOR,
                componentId, "Installed render negative canary", componentBindings,
                powerBindings, connectionBindings, behavior, layout, specifications, null,
                null, null, null, runtime, null, false,
                new Vector<GeneratedFaultCandidate>());
            BoardModificationController modifications = new InstalledNegativeModificationController(
                sim, instance, componentId, liftedPadId);
            return new InstalledRenderNegativeFixture(componentId, liftedPadId, physicalPackage,
                part, instance, modifications, layout);
        }
    }

    private static final class InstalledNegativeModificationController
            extends BoardModificationController {
        private final String liftedComponentId;
        private final String liftedPadId;

        InstalledNegativeModificationController(CirSim sim, GeneratedBoardInstance instance,
                String liftedComponentId, String liftedPadId) {
            super(sim, instance);
            this.liftedComponentId = liftedComponentId;
            this.liftedPadId = liftedPadId;
        }

        @Override
        boolean isLeadConnected(String componentId, String padId) {
            if (liftedComponentId.equals(componentId) && liftedPadId.equals(padId))
                return false;
            return super.isLeadConnected(componentId, padId);
        }

        @Override
        ComponentPhysicalState getComponentState(String componentId) {
            if (liftedComponentId.equals(componentId))
                return ComponentPhysicalState.LEAD_LIFTED;
            return super.getComponentState(componentId);
        }
    }

    private static final class InstalledOverlapProjectionProvider
            implements PhysicalPartRenderProvider {
        private final PhysicalPartRenderProvider delegate;
        private final int terminalIndex;

        InstalledOverlapProjectionProvider(PhysicalPartRenderProvider delegate,
                int terminalIndex) {
            this.delegate = delegate;
            this.terminalIndex = terminalIndex;
        }

        public PhysicalPartRenderer getRenderer(PhysicalPart<?> part) {
            PhysicalPartRenderer renderer = delegate.getRenderer(part);
            return renderer == null ? null : new InstalledOverlapProjectionRenderer(renderer,
                terminalIndex);
        }
    }

    private static final class InstalledOverlapProjectionRenderer
            implements PhysicalPartRenderer {
        private final PhysicalPartRenderer delegate;
        private final int terminalIndex;

        InstalledOverlapProjectionRenderer(PhysicalPartRenderer delegate, int terminalIndex) {
            this.delegate = delegate;
            this.terminalIndex = terminalIndex;
        }

        public PhysicalPartRenderGeometry getInstalledGeometry(PhysicalPartRenderContext context) {
            PhysicalPartRenderGeometry source = delegate.getInstalledGeometry(context);
            Vector<PhysicalPartRenderTerminal> terminals = source.getTerminals();
            PhysicalPartRenderTerminal sourceTerminal = terminals.get(terminalIndex);
            Point boardPoint = sourceTerminal.getBoardPadPoint();
            Rectangle boardProbe = sourceTerminal.getBoardPadProbeBounds();
            if (boardPoint == null || boardProbe == null)
                throw new IllegalStateException(
                    "Installed overlap canary source omitted its board-pad surface");
            terminals.set(terminalIndex, new PhysicalPartRenderTerminal(
                sourceTerminal.getTerminalIndex(), sourceTerminal.getTerminalId(),
                sourceTerminal.getBoardPadId(), boardPoint, boardProbe, boardPoint, boardProbe,
                sourceTerminal.getPadBounds(), boardPoint, boardProbe,
                sourceTerminal.getLeadBodyPoint(), sourceTerminal.getLeadEndPoint(),
                sourceTerminal.getLeadBounds()));
            return new PhysicalPartRenderGeometry(terminals, source.getHitRegions(),
                source.getSelectionBounds(), source.getBodyBounds(), source.getLeadBounds(),
                source.getDragBounds());
        }

        public PhysicalPartRenderGeometry getLooseGeometry(PhysicalPartRenderContext context) {
            return delegate.getLooseGeometry(context);
        }

        public void drawInstalled(Graphics graphics, PhysicalPartRenderContext context,
                PhysicalPartRenderGeometry geometry, boolean selected) {
            delegate.drawInstalled(graphics, context, geometry, selected);
        }

        public void drawLoose(Graphics graphics, PhysicalPartRenderContext context,
                PhysicalPartRenderGeometry geometry, boolean selected) {
            delegate.drawLoose(graphics, context, geometry, selected);
        }

        public ProbeTarget createInstalledProbeTarget(CirSim sim,
                PhysicalPartRenderContext context, int terminal) {
            return delegate.createInstalledProbeTarget(sim, context, terminal);
        }

        public ProbeTarget createLooseProbeTarget(CirSim sim,
                PhysicalPartRenderContext context, int terminal) {
            return delegate.createLooseProbeTarget(sim, context, terminal);
        }
    }

    private static void verifyRemovedLooseProbeOwnership(CirSim sim,
            PcbWorkbenchRenderer renderer, PhysicalPart<?> part) {
        int savedPage = renderer.getTrayPage();
        boolean found = false;
        try {
            for (int page = 0; page < renderer.getTrayPageCount(); page++) {
                renderer.setTrayPage(page);
                Vector<PhysicalPart<?>> visible = renderer.getVisibleLoosePhysicalParts();
                for (int index = 0; index < visible.size(); index++) {
                    if (visible.get(index) != part)
                        continue;
                    PhysicalPartRenderProvider provider = renderer
                        .getRenderRegistryForDeveloperVerification().getProvider(part.getPackage());
                    require(provider != null, "Removed part lost its loose render provider: " +
                        part.getId());
                    PhysicalPartRenderContext context = new PhysicalPartRenderContext(renderer,
                        null, part, part.getPackage(), index, true);
                    PhysicalPartRenderGeometry geometry = provider.getRenderer(part)
                        .getLooseGeometry(context);
                    for (PhysicalPartRenderTerminal terminal : geometry.getTerminals()) {
                        Point point = terminal.getPoint();
                        ProbeTarget target = renderer.findProbeTarget(sim, point.x, point.y);
                        require(target != null && target.isValid() &&
                                !(target instanceof BoardPadProbeTarget) &&
                                !(target instanceof ComponentLeadProbeTarget) &&
                                target.getMeasurementEndpoint() == part.getTerminal(
                                    terminal.getTerminalIndex()).getEndpoint() &&
                                target.getMarkerPoint() != null,
                            "Removed part loose ownership did not provide a fresh target: " +
                                part.getId() + "." + terminal.getTerminalIndex());
                        found = true;
                    }
                }
            }
        } finally {
            renderer.setTrayPage(savedPage);
        }
        require(found, "Removed part was not owned by a visible loose projection: " + part.getId());
    }

    private static class InstalledLeadCandidate {
        final String componentId;
        final Vector<GeneratedComponentConnectionBinding> bindings;
        final PhysicalPart<?> part;

        InstalledLeadCandidate(String componentId,
                Vector<GeneratedComponentConnectionBinding> bindings, PhysicalPart<?> part) {
            this.componentId = componentId;
            this.bindings = new Vector<GeneratedComponentConnectionBinding>(bindings);
            this.part = part;
        }
    }

    private static final class RemovedLeadCandidate extends InstalledLeadCandidate {
        RemovedLeadCandidate(String componentId,
                Vector<GeneratedComponentConnectionBinding> bindings, PhysicalPart<?> part) {
            super(componentId, bindings, part);
        }
    }

    private static void verifyRemovedLooseCarrier(PcbWorkbenchRenderer renderer,
            PhysicalPart<?> part, PhysicalGeometryRealization carrier) {
        int savedPage = renderer.getTrayPage();
        boolean found = false;
        try {
            for (int page = 0; page < renderer.getTrayPageCount(); page++) {
                renderer.setTrayPage(page);
                Vector<PhysicalPart<?>> visible = renderer.getVisibleLoosePhysicalParts();
                for (int index = 0; index < visible.size(); index++) {
                    PhysicalPart<?> candidate = visible.get(index);
                    if (candidate != part)
                        continue;
                    PhysicalPartRenderProvider provider = renderer
                        .getRenderRegistryForDeveloperVerification().getProvider(
                            candidate.getPackage());
                    require(provider != null, "Removed part lost its loose render provider");
                    PhysicalPartRenderContext context = new PhysicalPartRenderContext(renderer,
                        null, candidate, candidate.getPackage(), index, true);
                    PhysicalPartRenderGeometry geometry = provider.getRenderer(candidate)
                        .getLooseGeometry(context);
                    LoosePartPose pose = context.getLoosePose();
                    require(geometry != null && pose.getSourceRealization() == carrier &&
                            pose.getSourceGeometry() == carrier.getPhysicalGeometry() &&
                            renderer.getLooseTerminalPoint(candidate.getId(), 0) != null,
                        "Removed original part did not use its retained loose carrier");
                    found = true;
                }
            }
        } finally {
            renderer.setTrayPage(savedPage);
        }
        require(found, "Removed original part was not visible in the loose inventory");
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
        else if (PhysicalPackages.TO92_NPN.isEquivalentTo(physicalPackage))
            require(target instanceof PhysicalNpnPartProbeTarget,
                "NPN loose probe did not resolve through its provider: " + partId);
        else if (PhysicalPackages.TO92_NMOS.isEquivalentTo(physicalPackage))
            require(target instanceof PhysicalNmosPartProbeTarget,
                "NMOS loose probe did not resolve through its provider: " + partId);
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

    private static boolean contains(Rectangle outer, Rectangle inner) {
        return outer != null && inner != null && inner.x >= outer.x && inner.y >= outer.y &&
            (long) inner.x + inner.width <= (long) outer.x + outer.width &&
            (long) inner.y + inner.height <= (long) outer.y + outer.height;
    }

    private static boolean pointEquals(Point first, Point second) {
        return first != null && second != null && first.x == second.x && first.y == second.y;
    }

    /** Binding wrappers may be reallocated around the same CircuitJS post. */
    private static boolean sameCircuitPostEndpoint(CircuitMeasurementEndpoint first,
            CircuitMeasurementEndpoint second) {
        if (first == second)
            return true;
        if (!(first instanceof CircuitPostMeasurementEndpoint) ||
                !(second instanceof CircuitPostMeasurementEndpoint))
            return false;
        CircuitPostMeasurementEndpoint firstPost = (CircuitPostMeasurementEndpoint) first;
        CircuitPostMeasurementEndpoint secondPost = (CircuitPostMeasurementEndpoint) second;
        return firstPost.getElement() == secondPost.getElement() &&
            firstPost.getPostIndex() == secondPost.getPostIndex();
    }
}
