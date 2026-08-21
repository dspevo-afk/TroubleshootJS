package com.lushprojects.circuitjs1.client;

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
        verifyLooseProjectionLifecycle(sim, renderer, registry);
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
            verifyRemovedLooseCarrier(renderer, part, carrier);
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
}
