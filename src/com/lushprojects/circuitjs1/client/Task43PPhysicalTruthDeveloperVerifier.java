package com.lushprojects.circuitjs1.client;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Vector;

/**
 * Developer-only cross-boundary verifier for Task 43P.  Production objects
 * are read once into source observations and the same canonical validator is
 * then used for the positive path and every negative fixture.  The validator
 * never asks the binding table to tell it what a solver net is: logical-board
 * and manifest observations are the independent electrical oracle.
 */
final class Task43PPhysicalTruthDeveloperVerifier {
    private static final String KEY_SEPARATOR = "\u0000";

    private static final class TerminalExpectation {
        final String padId;
        final String componentId;
        final String terminalId;
        final String netId;
        final String solverClass;
        final int solverPost;

        TerminalExpectation(String padId, String componentId, String terminalId,
                String netId, String solverClass, int solverPost) {
            this.padId = padId;
            this.componentId = componentId;
            this.terminalId = terminalId;
            this.netId = netId;
            this.solverClass = solverClass;
            this.solverPost = solverPost;
        }

        String terminalKey() {
            return key(componentId, terminalId);
        }
    }

    private static final class PackageExpectation {
        final String componentId;
        final String packageId;
        final Vector<String> terminalIds = new Vector<String>();
        final Vector<String> acceptedCatalog = new Vector<String>();

        PackageExpectation(String componentId, String packageId) {
            this.componentId = componentId;
            this.packageId = packageId;
        }

        void addTerminal(String terminalId) {
            if (!terminalIds.contains(terminalId))
                terminalIds.add(terminalId);
        }

        void addCatalogPair(String variant, String transform) {
            String catalogPair = pair(variant, transform);
            if (!acceptedCatalog.contains(catalogPair))
                acceptedCatalog.add(catalogPair);
        }

        boolean accepts(String variant, String transform) {
            return acceptedCatalog.contains(pair(variant, transform));
        }
    }

    private static final class Manifest {
        final Vector<TerminalExpectation> terminals = new Vector<TerminalExpectation>();
        final Vector<PackageExpectation> packages = new Vector<PackageExpectation>();

        PackageExpectation getPackage(String componentId) {
            for (PackageExpectation expected : packages)
                if (same(expected.componentId, componentId))
                    return expected;
            return null;
        }
    }

    /* Raw logical-board observations. */
    private static final class BoardPadObservation {
        String padId;
        String componentId;
        String terminalId;
        String netId;

        BoardPadObservation copy() {
            BoardPadObservation result = new BoardPadObservation();
            result.padId = padId;
            result.componentId = componentId;
            result.terminalId = terminalId;
            result.netId = netId;
            return result;
        }
    }

    private static final class BoardComponentObservation {
        String componentId;
        String packageId;
        Vector<String> padIds = new Vector<String>();

        BoardComponentObservation copy() {
            BoardComponentObservation result = new BoardComponentObservation();
            result.componentId = componentId;
            result.packageId = packageId;
            result.padIds = new Vector<String>(padIds);
            return result;
        }
    }

    private static final class BoardNetObservation {
        String netId;
        Vector<String> padIds = new Vector<String>();

        BoardNetObservation copy() {
            BoardNetObservation result = new BoardNetObservation();
            result.netId = netId;
            result.padIds = new Vector<String>(padIds);
            return result;
        }
    }

    private static final class LogicalBoardSnapshot {
        final Vector<BoardPadObservation> pads = new Vector<BoardPadObservation>();
        final Vector<BoardComponentObservation> components =
            new Vector<BoardComponentObservation>();
        final Vector<BoardNetObservation> nets = new Vector<BoardNetObservation>();

        LogicalBoardSnapshot copy() {
            LogicalBoardSnapshot result = new LogicalBoardSnapshot();
            for (BoardPadObservation pad : pads)
                result.pads.add(pad.copy());
            for (BoardComponentObservation component : components)
                result.components.add(component.copy());
            for (BoardNetObservation net : nets)
                result.nets.add(net.copy());
            return result;
        }
    }

    /* Raw PCB layout/copper observations. */
    private static final class LayoutPadObservation {
        String padId;
        int x;
        int y;

        LayoutPadObservation copy() {
            LayoutPadObservation result = new LayoutPadObservation();
            result.padId = padId;
            result.x = x;
            result.y = y;
            return result;
        }
    }

    private static final class TraceObservation {
        String netId;
        String startPadId;
        String endPadId;
        int[] xPoints;
        int[] yPoints;

        TraceObservation copy() {
            TraceObservation result = new TraceObservation();
            result.netId = netId;
            result.startPadId = startPadId;
            result.endPadId = endPadId;
            result.xPoints = copyIntArray(xPoints);
            result.yPoints = copyIntArray(yPoints);
            return result;
        }
    }

    private static final class LayoutComponentObservation {
        String componentId;
        String packageId;
        Vector<String> geometryTerminalIds = new Vector<String>();
        String variant;
        String transform;
        String realizationPackageId;
        String realizationVariant;
        String realizationTransform;
        boolean packageAcceptsGeometry;
        boolean realizationPackageIdentity;
        boolean realizationGeometryIdentity;
        boolean realizationVariantGeometryIdentity;
        boolean realizationContractMatches;

        LayoutComponentObservation copy() {
            LayoutComponentObservation result = new LayoutComponentObservation();
            result.componentId = componentId;
            result.packageId = packageId;
            result.geometryTerminalIds = new Vector<String>(geometryTerminalIds);
            result.variant = variant;
            result.transform = transform;
            result.realizationPackageId = realizationPackageId;
            result.realizationVariant = realizationVariant;
            result.realizationTransform = realizationTransform;
            result.packageAcceptsGeometry = packageAcceptsGeometry;
            result.realizationPackageIdentity = realizationPackageIdentity;
            result.realizationGeometryIdentity = realizationGeometryIdentity;
            result.realizationVariantGeometryIdentity = realizationVariantGeometryIdentity;
            result.realizationContractMatches = realizationContractMatches;
            return result;
        }
    }

    private static final class LayoutSnapshot {
        final Vector<LayoutPadObservation> pads = new Vector<LayoutPadObservation>();
        final Vector<LayoutComponentObservation> components =
            new Vector<LayoutComponentObservation>();
        final Vector<TraceObservation> traces = new Vector<TraceObservation>();

        LayoutSnapshot copy() {
            LayoutSnapshot result = new LayoutSnapshot();
            for (LayoutPadObservation pad : pads)
                result.pads.add(pad.copy());
            for (LayoutComponentObservation component : components)
                result.components.add(component.copy());
            for (TraceObservation trace : traces)
                result.traces.add(trace.copy());
            return result;
        }
    }

    /* Package, slot, geometry, and installed-part observations. */
    private static final class PackageObservation {
        String componentId;
        String boardPackageId;
        Vector<String> boardPackageTerminalIds = new Vector<String>();
        String placementPackageId;
        Vector<String> placementPackageTerminalIds = new Vector<String>();
        Vector<String> placementGeometryTerminalIds = new Vector<String>();
        String slotPackageId;
        Vector<String> slotTerminalIds = new Vector<String>();
        String partPackageId;
        Vector<String> partPackageTerminalIds = new Vector<String>();
        String partId;
        boolean partInstalled;
        boolean slotContainsPart;
        boolean partOwnsSlot;
        Vector<String> catalogPairs = new Vector<String>();

        PackageObservation copy() {
            PackageObservation result = new PackageObservation();
            result.componentId = componentId;
            result.boardPackageId = boardPackageId;
            result.boardPackageTerminalIds = new Vector<String>(boardPackageTerminalIds);
            result.placementPackageId = placementPackageId;
            result.placementPackageTerminalIds =
                new Vector<String>(placementPackageTerminalIds);
            result.placementGeometryTerminalIds =
                new Vector<String>(placementGeometryTerminalIds);
            result.slotPackageId = slotPackageId;
            result.slotTerminalIds = new Vector<String>(slotTerminalIds);
            result.partPackageId = partPackageId;
            result.partPackageTerminalIds = new Vector<String>(partPackageTerminalIds);
            result.partId = partId;
            result.partInstalled = partInstalled;
            result.slotContainsPart = slotContainsPart;
            result.partOwnsSlot = partOwnsSlot;
            result.catalogPairs = new Vector<String>(catalogPairs);
            return result;
        }
    }

    /* Renderer observations are captured separately from layout coordinates. */
    private static final class RendererPadObservation {
        String padId;
        int x;
        int y;
        int expectedScreenX;
        int expectedScreenY;
        boolean pointPresent;

        RendererPadObservation copy() {
            RendererPadObservation result = new RendererPadObservation();
            result.padId = padId;
            result.x = x;
            result.y = y;
            result.expectedScreenX = expectedScreenX;
            result.expectedScreenY = expectedScreenY;
            result.pointPresent = pointPresent;
            return result;
        }
    }

    private static final class RendererTerminalObservation {
        String componentId;
        String padId;
        String terminalId;
        int terminalIndex;
        int boardPadX;
        int boardPadY;
        int activeX;
        int activeY;
        int leadEndX;
        int leadEndY;
        int expectedBoardPadX;
        int expectedBoardPadY;
        int expectedActiveX;
        int expectedActiveY;
        int expectedLeadEndX;
        int expectedLeadEndY;
        boolean expectedGeometryPresent;
        boolean boardPadPointPresent;
        boolean activePointPresent;
        boolean leadEndPointPresent;
        boolean containsProbe;
        boolean geometryContainsPad;
        boolean targetIsBoardPad;
        boolean targetValid;
        String targetPadId;

        RendererTerminalObservation copy() {
            RendererTerminalObservation result = new RendererTerminalObservation();
            result.componentId = componentId;
            result.padId = padId;
            result.terminalId = terminalId;
            result.terminalIndex = terminalIndex;
            result.boardPadX = boardPadX;
            result.boardPadY = boardPadY;
            result.activeX = activeX;
            result.activeY = activeY;
            result.leadEndX = leadEndX;
            result.leadEndY = leadEndY;
            result.expectedBoardPadX = expectedBoardPadX;
            result.expectedBoardPadY = expectedBoardPadY;
            result.expectedActiveX = expectedActiveX;
            result.expectedActiveY = expectedActiveY;
            result.expectedLeadEndX = expectedLeadEndX;
            result.expectedLeadEndY = expectedLeadEndY;
            result.expectedGeometryPresent = expectedGeometryPresent;
            result.boardPadPointPresent = boardPadPointPresent;
            result.activePointPresent = activePointPresent;
            result.leadEndPointPresent = leadEndPointPresent;
            result.containsProbe = containsProbe;
            result.geometryContainsPad = geometryContainsPad;
            result.targetIsBoardPad = targetIsBoardPad;
            result.targetValid = targetValid;
            result.targetPadId = targetPadId;
            return result;
        }

        String terminalKey() {
            return key(componentId, terminalId);
        }
    }

    private static final class RendererSnapshot {
        final Vector<RendererPadObservation> pads = new Vector<RendererPadObservation>();
        final Vector<RendererTerminalObservation> terminals =
            new Vector<RendererTerminalObservation>();

        RendererSnapshot copy() {
            RendererSnapshot result = new RendererSnapshot();
            for (RendererPadObservation pad : pads)
                result.pads.add(pad.copy());
            for (RendererTerminalObservation terminal : terminals)
                result.terminals.add(terminal.copy());
            return result;
        }
    }

    /* Live CircuitJS post observations. */
    private static final class SolverObservation {
        String padId;
        CircuitPostMeasurementEndpoint endpoint;
        int solverNode;
        double voltage;
        boolean ownedElement;

        SolverObservation copy() {
            SolverObservation result = new SolverObservation();
            result.padId = padId;
            result.endpoint = endpoint;
            result.solverNode = solverNode;
            result.voltage = voltage;
            result.ownedElement = ownedElement;
            return result;
        }
    }

    /**
     * The retained producer-path board endpoint for one manifest pad.  The
     * immutable GeneratedBoardEndpointOracle owns the board-side identity;
     * detachable bindings are cross-checked against it and retain their
     * connection geometry for the separate ownership invariant.
     */
    private static final class RetainedSolverEndpoint {
        final CircuitPostMeasurementEndpoint endpoint;
        final GeneratedComponentConnectionBinding detachableBinding;

        RetainedSolverEndpoint(CircuitPostMeasurementEndpoint endpoint,
                GeneratedComponentConnectionBinding detachableBinding) {
            this.endpoint = endpoint;
            this.detachableBinding = detachableBinding;
        }
    }

    private static final class SolverSnapshot {
        final Vector<SolverObservation> observations = new Vector<SolverObservation>();

        SolverSnapshot copy() {
            SolverSnapshot result = new SolverSnapshot();
            for (SolverObservation observation : observations)
                result.observations.add(observation.copy());
            return result;
        }
    }

    private static final class ObservationSnapshot {
        LogicalBoardSnapshot logicalBoard = new LogicalBoardSnapshot();
        LayoutSnapshot layout = new LayoutSnapshot();
        final Vector<PackageObservation> packages = new Vector<PackageObservation>();
        RendererSnapshot renderer = new RendererSnapshot();
        SolverSnapshot solver = new SolverSnapshot();

        ObservationSnapshot copy() {
            ObservationSnapshot result = new ObservationSnapshot();
            result.logicalBoard = logicalBoard.copy();
            result.layout = layout.copy();
            for (PackageObservation observation : packages)
                result.packages.add(observation.copy());
            result.renderer = renderer.copy();
            result.solver = solver.copy();
            return result;
        }
    }

    private static final class ValidationSummary {
        final int rawTraceChecks;
        final int renderedSurfaceChecks;
        final int solverChecks;

        ValidationSummary(int rawTraceChecks, int renderedSurfaceChecks, int solverChecks) {
            this.rawTraceChecks = rawTraceChecks;
            this.renderedSurfaceChecks = renderedSurfaceChecks;
            this.solverChecks = solverChecks;
        }
    }

    private static final class NegativeFixtureResult {
        final String id;
        final String sourceMutation;
        final boolean caught;

        NegativeFixtureResult(String id, String sourceMutation, boolean caught) {
            this.id = id;
            this.sourceMutation = sourceMutation;
            this.caught = caught;
        }
    }

    private Task43PPhysicalTruthDeveloperVerifier() { }

    static String verify(CirSim sim) {
        if (sim == null)
            throw new IllegalArgumentException("Task43P physical verifier requires a simulation");
        GeneratedBoardInstance instance = sim.getGeneratedBoardInstance();
        if (instance == null || instance.getPcbLayout() == null)
            throw new IllegalStateException("task43p-physical-triad-missing-board");
        if (sim.pcbWorkbenchController == null)
            throw new IllegalStateException("task43p-physical-triad-missing-renderer");

        Manifest manifest = manifestFor(instance.getCircuitFamilyId());
        ObservationSnapshot snapshot = gatherSourceObservations(sim, instance);
        ValidationSummary summary = validateCanonical(instance, manifest, snapshot);
        Vector<NegativeFixtureResult> negativeFixtures = runNegativeFixtures(instance, manifest,
            snapshot);
        for (NegativeFixtureResult result : negativeFixtures)
            if (!result.caught)
                throw new IllegalStateException("task43p-negative-fixture-not-caught:" +
                    result.id);
        return buildEvidence(instance, manifest, snapshot, summary, negativeFixtures);
    }

    private static ObservationSnapshot gatherSourceObservations(CirSim sim,
            GeneratedBoardInstance instance) {
        ObservationSnapshot result = new ObservationSnapshot();
        TroubleshootBoard board = instance.getBoard();
        PcbBoardLayout layout = instance.getPcbLayout();
        PcbWorkbenchRenderer renderer = sim.pcbWorkbenchController.getRenderer();
        PhysicalBoardRuntime runtime = instance.getPhysicalBoardRuntime();

        for (String componentId : board.getComponentIds()) {
            BoardComponent component = board.getComponent(componentId);
            BoardComponentObservation observation = new BoardComponentObservation();
            observation.componentId = componentId;
            observation.packageId = component == null || component.getPhysicalPackage() == null ?
                null : component.getPhysicalPackage().getId();
            if (component != null)
                observation.padIds = new Vector<String>(component.getPadIds());
            result.logicalBoard.components.add(observation);
        }
        for (String padId : board.getPadIds()) {
            BoardPad pad = board.getPad(padId);
            BoardPadObservation observation = new BoardPadObservation();
            observation.padId = padId;
            if (pad != null) {
                observation.componentId = pad.getComponentId();
                observation.terminalId = pad.getTerminalId();
                observation.netId = pad.getNetId();
            }
            result.logicalBoard.pads.add(observation);
        }
        for (String netId : board.getNetIds()) {
            BoardNet net = board.getNet(netId);
            BoardNetObservation observation = new BoardNetObservation();
            observation.netId = netId;
            if (net != null)
                observation.padIds = new Vector<String>(net.getPadIds());
            result.logicalBoard.nets.add(observation);
        }

        for (PcbPadPlacement pad : layout.getPads()) {
            LayoutPadObservation observation = new LayoutPadObservation();
            observation.padId = pad.getPadId();
            observation.x = pad.getX();
            observation.y = pad.getY();
            result.layout.pads.add(observation);
        }
        for (PcbComponentPlacement placement : layout.getComponents()) {
            LayoutComponentObservation observation = new LayoutComponentObservation();
            observation.componentId = placement.getComponentId();
            observation.packageId = placement.getPhysicalPackage() == null ? null :
                placement.getPhysicalPackage().getId();
            observation.geometryTerminalIds = placement.getPhysicalGeometry() == null ?
                new Vector<String>() : placement.getPhysicalGeometry().getTerminalIds();
            observation.variant = placement.getGeometryVariantKey();
            observation.transform = placement.getGeometryTransformKey();
            PhysicalGeometryRealization realization = placement.getGeometryRealization();
            if (realization != null) {
                observation.realizationPackageId = realization.getPhysicalPackage() == null ?
                    null : realization.getPhysicalPackage().getId();
                observation.realizationVariant = realization.getVariantKey();
                observation.realizationTransform = realization.getTransformKey();
                observation.realizationPackageIdentity =
                    realization.getPhysicalPackage() == placement.getPhysicalPackage();
                observation.realizationGeometryIdentity =
                    realization.getPhysicalGeometry() == placement.getPhysicalGeometry();
                PhysicalPackage.GeometryVariant variant = placement.getPhysicalPackage() == null ?
                    null : placement.getPhysicalPackage().getGeometryVariant(
                        realization.getVariantKey());
                observation.realizationVariantGeometryIdentity = variant != null &&
                    variant.getGeometry() == placement.getPhysicalGeometry();
                observation.realizationContractMatches = placement.getGeometryContractVersion() !=
                    null && placement.getGeometryContractVersion().equals(
                        realization.getGeometryContractVersion());
            }
            observation.packageAcceptsGeometry = placement.getPhysicalPackage() != null &&
                placement.getPhysicalGeometry() != null && placement.getPhysicalPackage()
                    .acceptsGeometry(placement.getPhysicalGeometry());
            result.layout.components.add(observation);
        }
        for (PcbTraceGeometry trace : layout.getTraces()) {
            TraceObservation observation = new TraceObservation();
            observation.netId = trace.getNetId();
            observation.startPadId = trace.getStartPadId();
            observation.endPadId = trace.getEndPadId();
            observation.xPoints = copyIntArray(trace.getXPoints());
            observation.yPoints = copyIntArray(trace.getYPoints());
            result.layout.traces.add(observation);
        }

        Vector<String> componentIds = unionComponentIds(board, layout);
        for (String componentId : componentIds) {
            PackageObservation observation = new PackageObservation();
            observation.componentId = componentId;
            BoardComponent component = board.getComponent(componentId);
            PcbComponentPlacement placement = layout.getComponent(componentId);
            PhysicalBoardSlot slot = runtime == null ? null : runtime.getSlot(componentId);
            PhysicalPart<?> part = runtime == null ? null : runtime.getInstalledPart(componentId);
            if (component != null && component.getPhysicalPackage() != null) {
                observation.boardPackageId = component.getPhysicalPackage().getId();
                observation.boardPackageTerminalIds = new Vector<String>(component
                    .getPhysicalPackage().getTerminalIds());
            }
            if (placement != null) {
                PhysicalPackage placementPackage = placement.getPhysicalPackage();
                observation.placementPackageId = placementPackage == null ? null :
                    placementPackage.getId();
                observation.placementPackageTerminalIds = placementPackage == null ?
                    new Vector<String>() : new Vector<String>(placementPackage.getTerminalIds());
                observation.placementGeometryTerminalIds = placement.getPhysicalGeometry() == null ?
                    new Vector<String>() : new Vector<String>(placement.getPhysicalGeometry()
                        .getTerminalIds());
                for (PhysicalPackage.GeometryVariant variant : placementPackage == null ?
                        new Vector<PhysicalPackage.GeometryVariant>() :
                        placementPackage.getGeometryVariants())
                    observation.catalogPairs.add(pair(variant.getKey(), variant.getTransformKey()));
            }
            observation.slotPackageId = slot == null || slot.getPhysicalPackage() == null ? null :
                slot.getPhysicalPackage().getId();
            if (slot != null)
                observation.slotTerminalIds = new Vector<String>(slot.getTerminalIds());
            observation.partPackageId = part == null || part.getPackage() == null ? null :
                part.getPackage().getId();
            observation.partPackageTerminalIds = part == null || part.getPackage() == null ?
                new Vector<String>() : new Vector<String>(part.getPackage().getTerminalIds());
            observation.partId = part == null ? null : part.getId();
            observation.partInstalled = part != null && part.isInstalled();
            observation.slotContainsPart = slot != null && slot.getInstalledPart() == part;
            observation.partOwnsSlot = part != null && part.getBoardSlot() == slot;
            result.packages.add(observation);
        }

        Vector<String> padIds = unionPadIds(board, layout);
        for (String padId : padIds) {
            LayoutPadObservation layoutPad = findLayoutPad(result.layout, padId);
            RendererPadObservation observation = new RendererPadObservation();
            observation.padId = padId;
            Point point = renderer.getPadPoint(padId);
            observation.pointPresent = point != null;
            if (point != null) {
                observation.x = point.x;
                observation.y = point.y;
            }
            if (layoutPad != null) {
                observation.expectedScreenX = renderer.screenXForProvider(layoutPad.x);
                observation.expectedScreenY = renderer.screenYForProvider(layoutPad.y);
            }
            result.renderer.pads.add(observation);
        }
        for (String componentId : componentIds) {
            PhysicalPartRenderGeometry geometry = renderer
                .getInstalledGeometryForDeveloperVerification(componentId);
            if (geometry == null)
                continue;
            for (PhysicalPartRenderTerminal terminal : geometry.getTerminals()) {
                RendererTerminalObservation observation = new RendererTerminalObservation();
                observation.componentId = componentId;
                observation.padId = terminal.getBoardPadId();
                observation.terminalId = terminal.getTerminalId();
                observation.terminalIndex = terminal.getTerminalIndex();
                PcbComponentPlacement placement = layout.getComponent(componentId);
                if (placement != null && placement.getPhysicalGeometry() != null &&
                        observation.terminalIndex >= 0 && observation.terminalIndex <
                            placement.getPhysicalGeometry().getTerminals().size()) {
                    /*
                     * These are independently calculated from the raw package
                     * geometry retained by the layout.  They are deliberately
                     * not read from PhysicalPartRenderGeometry, so a renderer
                     * can never validate its own displaced lead/pad output.
                     */
                    PhysicalPackageGeometry.Placement placed = placement.getPhysicalGeometry()
                        .placedAt(placement.getX(), placement.getY());
                    Point expectedBoardPad = placed.getBoardPadProbeCenter(
                        observation.terminalIndex);
                    Point expectedLeadEnd = placed.getLeadEndPoint(observation.terminalIndex);
                    if (expectedBoardPad != null && expectedLeadEnd != null) {
                        observation.expectedBoardPadX = renderer.screenXForProvider(
                            expectedBoardPad.x);
                        observation.expectedBoardPadY = renderer.screenYForProvider(
                            expectedBoardPad.y);
                        observation.expectedActiveX = observation.expectedBoardPadX;
                        observation.expectedActiveY = observation.expectedBoardPadY;
                        observation.expectedLeadEndX = renderer.screenXForProvider(
                            expectedLeadEnd.x);
                        observation.expectedLeadEndY = renderer.screenYForProvider(
                            expectedLeadEnd.y);
                        observation.expectedGeometryPresent = true;
                    }
                }
                Point boardPadPoint = terminal.getBoardPadPoint();
                Point activePoint = terminal.getPoint();
                Point leadEndPoint = terminal.getLeadEndPoint();
                observation.boardPadPointPresent = boardPadPoint != null;
                observation.activePointPresent = activePoint != null;
                observation.leadEndPointPresent = leadEndPoint != null;
                if (boardPadPoint != null) {
                    observation.boardPadX = boardPadPoint.x;
                    observation.boardPadY = boardPadPoint.y;
                }
                if (activePoint != null) {
                    observation.activeX = activePoint.x;
                    observation.activeY = activePoint.y;
                }
                if (leadEndPoint != null) {
                    observation.leadEndX = leadEndPoint.x;
                    observation.leadEndY = leadEndPoint.y;
                }
                if (boardPadPoint != null) {
                    observation.containsProbe = terminal.containsProbe(boardPadPoint.x,
                        boardPadPoint.y);
                    observation.geometryContainsPad = geometry.contains(boardPadPoint.x,
                        boardPadPoint.y);
                    ProbeTarget target = renderer.findProbeTarget(sim, boardPadPoint.x,
                        boardPadPoint.y);
                    observation.targetIsBoardPad = target instanceof BoardPadProbeTarget;
                    observation.targetValid = target != null && target.isValid();
                    if (target instanceof BoardPadProbeTarget)
                        observation.targetPadId = ((BoardPadProbeTarget) target).getPadId();
                }
                result.renderer.terminals.add(observation);
            }
        }

        Vector<CircuitElm> ownedElements = instance.getSimulationElements();
        for (String padId : padIds) {
            SolverObservation observation = new SolverObservation();
            observation.padId = padId;
            CircuitMeasurementEndpoint endpoint = instance.getSimulationBindings()
                .getEndpoint(padId);
            if (endpoint instanceof CircuitPostMeasurementEndpoint) {
                observation.endpoint = (CircuitPostMeasurementEndpoint) endpoint;
                CircuitElm element = observation.endpoint.getElement();
                int postIndex = observation.endpoint.getPostIndex();
                observation.ownedElement = containsIdentity(ownedElements, element);
                if (element != null && postIndex >= 0 &&
                        postIndex < element.getPostCount()) {
                    observation.solverNode = element.nodes[postIndex];
                    observation.voltage = element.getPostVoltage(postIndex);
                }
                else
                    observation.voltage = Double.NaN;
            } else {
                observation.voltage = Double.NaN;
            }
            result.solver.observations.add(observation);
        }
        return result;
    }

    /**
     * Completeness is deliberately a separate first phase.  It uses sets and
     * exact source cardinalities before any expected terminal is inspected.
     */
    private static ValidationSummary validateCanonical(GeneratedBoardInstance instance,
            Manifest manifest,
            ObservationSnapshot snapshot) {
        if (instance == null || manifest == null || snapshot == null)
            throw new IllegalStateException("task43p-canonical-missing-observation");
        validateManifestCompleteness(manifest);
        validateLogicalCompleteness(manifest, snapshot.logicalBoard);
        validateLayoutCompleteness(manifest, snapshot.logicalBoard, snapshot.layout);
        validatePackageCompleteness(manifest, snapshot.packages, snapshot.layout);
        validateRendererCompleteness(manifest, snapshot.renderer);
        validateSolverCompleteness(instance, manifest, snapshot.solver);

        int rawTraceChecks = validateAllTraces(snapshot.logicalBoard, snapshot.layout);
        validatePackageObservations(manifest, snapshot.packages, snapshot.layout);
        int renderedSurfaceChecks = validateRendererObservations(manifest, snapshot);
        int solverChecks = validateSolverObservations(instance, manifest, snapshot);
        validateTerminalObservations(manifest, snapshot);
        return new ValidationSummary(rawTraceChecks, renderedSurfaceChecks, solverChecks);
    }

    private static void validateManifestCompleteness(Manifest manifest) {
        HashSet<String> packageIds = new HashSet<String>();
        HashSet<String> padIds = new HashSet<String>();
        HashSet<String> terminalKeys = new HashSet<String>();
        for (PackageExpectation expected : manifest.packages) {
            requireId(expected.componentId, "manifest component");
            requireId(expected.packageId, "manifest package");
            require(packageIds.add(expected.componentId),
                "task43p-duplicate-manifest-component:" + expected.componentId);
            require(expected.terminalIds.size() > 0,
                "task43p-missing-manifest-package-terminals:" + expected.componentId);
            require(expected.acceptedCatalog.size() > 0,
                "task43p-missing-manifest-package-catalog:" + expected.componentId);
            requireUniqueIds(expected.terminalIds,
                "manifest package terminal " + expected.componentId);
            for (String catalogPair : expected.acceptedCatalog)
                requireId(catalogPair, "manifest package catalog");
        }
        for (TerminalExpectation expected : manifest.terminals) {
            requireId(expected.padId, "manifest pad");
            requireId(expected.componentId, "manifest terminal component");
            requireId(expected.terminalId, "manifest terminal");
            requireId(expected.netId, "manifest net");
            requireId(expected.solverClass, "manifest solver class");
            require(expected.solverPost >= 0,
                "task43p-invalid-manifest-solver-post:" + expected.padId);
            require(padIds.add(expected.padId),
                "task43p-duplicate-manifest-pad:" + expected.padId);
            require(terminalKeys.add(expected.terminalKey()),
                "task43p-duplicate-manifest-terminal:" + expected.terminalKey());
            PackageExpectation packageExpectation = manifest.getPackage(expected.componentId);
            require(packageExpectation != null,
                "task43p-manifest-terminal-without-package:" + expected.terminalKey());
            require(packageExpectation.terminalIds.contains(expected.terminalId),
                "task43p-manifest-terminal-not-in-package:" + expected.terminalKey());
        }
        for (PackageExpectation expected : manifest.packages) {
            HashSet<String> terminalsForComponent = new HashSet<String>();
            for (TerminalExpectation terminal : manifest.terminals)
                if (same(expected.componentId, terminal.componentId))
                    terminalsForComponent.add(terminal.terminalId);
            requireSetEquals("manifest package terminals " + expected.componentId,
                toSet(expected.terminalIds), terminalsForComponent);
        }
        require(manifest.terminals.size() == padIds.size(),
            "task43p-manifest-pad-terminal-cardinality-mismatch");
    }

    private static void validateLogicalCompleteness(Manifest manifest,
            LogicalBoardSnapshot logical) {
        HashSet<String> boardPadIds = new HashSet<String>();
        HashSet<String> componentIds = new HashSet<String>();
        HashSet<String> netIds = new HashSet<String>();
        for (BoardComponentObservation component : logical.components) {
            requireId(component.componentId, "raw board component");
            requireId(component.packageId, "raw board package");
            require(componentIds.add(component.componentId),
                "task43p-duplicate-board-component:" + component.componentId);
            requireUniqueIds(component.padIds, "raw board component pads " + component.componentId);
        }
        for (BoardPadObservation pad : logical.pads) {
            requireId(pad.padId, "raw board pad");
            requireId(pad.componentId, "raw board pad component");
            requireId(pad.terminalId, "raw board pad terminal");
            requireId(pad.netId, "raw board pad net");
            require(boardPadIds.add(pad.padId),
                "task43p-duplicate-board-pad:" + pad.padId);
        }
        for (BoardNetObservation net : logical.nets) {
            requireId(net.netId, "raw board net");
            require(netIds.add(net.netId), "task43p-duplicate-board-net:" + net.netId);
            requireUniqueIds(net.padIds, "raw board net pads " + net.netId);
        }
        requireSetEquals("manifest/raw board pads", manifestPadIds(manifest), boardPadIds);
        requireSetEquals("manifest/raw board components", manifestComponentIds(manifest),
            componentIds);
        requireSetEquals("manifest/raw board nets", manifestNetIds(manifest), netIds);
        HashMap<String, BoardPadObservation> padsById = boardPadsById(logical.pads);
        HashMap<String, BoardComponentObservation> componentsById =
            boardComponentsById(logical.components);
        HashMap<String, BoardNetObservation> netsById = boardNetsById(logical.nets);
        HashMap<String, TerminalExpectation> manifestTerminalsByPad =
            manifestTerminalsByPadId(manifest);
        for (String componentId : componentIds) {
            BoardComponentObservation component = componentsById.get(componentId);
            HashSet<String> declared = toSet(component.padIds);
            HashSet<String> observed = new HashSet<String>();
            HashSet<String> observedTerminalIds = new HashSet<String>();
            for (BoardPadObservation pad : logical.pads)
                if (same(componentId, pad.componentId)) {
                    require(observed.add(pad.padId),
                        "task43p-duplicate-component-pad-observation:" + componentId);
                    require(observedTerminalIds.add(pad.terminalId),
                        "task43p-duplicate-board-component-terminal:" + componentId);
                }
            requireSetEquals("board component pads " + componentId, declared, observed);
            PackageExpectation expectedPackage = manifest.getPackage(componentId);
            require(expectedPackage != null,
                "task43p-board-component-without-manifest-package:" + componentId);
            requireSetEquals("manifest/board component terminals " + componentId,
                toSet(expectedPackage.terminalIds), observedTerminalIds);
        }
        for (BoardPadObservation pad : logical.pads) {
            require(componentsById.containsKey(pad.componentId),
                "task43p-board-pad-without-component:" + pad.padId);
            require(netsById.containsKey(pad.netId),
                "task43p-board-pad-without-net:" + pad.padId);
            BoardNetObservation net = netsById.get(pad.netId);
            require(net.padIds.contains(pad.padId),
                "task43p-board-net-omits-pad:" + pad.padId);
            TerminalExpectation expected = manifestTerminalsByPad.get(pad.padId);
            require(expected != null,
                "task43p-board-pad-without-manifest-terminal:" + pad.padId);
            require(same(expected.componentId, pad.componentId) &&
                same(expected.terminalId, pad.terminalId),
                "task43p-manifest-board-component-terminal-mismatch:" + pad.padId);
        }
        for (BoardNetObservation net : logical.nets)
            for (String padId : net.padIds) {
                BoardPadObservation pad = padsById.get(padId);
                require(pad != null, "task43p-board-net-references-unknown-pad:" + padId);
                require(same(net.netId, pad.netId),
                    "task43p-board-net-pad-mismatch:" + padId);
            }
    }

    private static void validateLayoutCompleteness(Manifest manifest,
            LogicalBoardSnapshot logical, LayoutSnapshot layout) {
        HashSet<String> layoutPadIds = new HashSet<String>();
        HashSet<String> layoutComponentIds = new HashSet<String>();
        for (LayoutPadObservation pad : layout.pads) {
            requireId(pad.padId, "raw layout pad");
            require(layoutPadIds.add(pad.padId),
                "task43p-duplicate-layout-pad:" + pad.padId);
        }
        for (LayoutComponentObservation component : layout.components) {
            requireId(component.componentId, "raw layout component");
            require(layoutComponentIds.add(component.componentId),
                "task43p-duplicate-layout-component:" + component.componentId);
            requireUniqueIds(component.geometryTerminalIds,
                "raw layout geometry terminals " + component.componentId);
        }
        requireSetEquals("manifest/raw board/layout pads", manifestPadIds(manifest), layoutPadIds);
        requireSetEquals("manifest/layout components", manifestComponentIds(manifest),
            layoutComponentIds);
        requireSetEquals("raw board/layout pads", boardPadIds(logical.pads), layoutPadIds);
    }

    private static void validatePackageCompleteness(Manifest manifest,
            Vector<PackageObservation> observations, LayoutSnapshot layout) {
        HashSet<String> observedComponents = new HashSet<String>();
        for (PackageObservation observation : observations) {
            requireId(observation.componentId, "package observation component");
            require(observedComponents.add(observation.componentId),
                "task43p-duplicate-package-observation:" + observation.componentId);
            requireUniqueIds(observation.boardPackageTerminalIds,
                "board package terminals " + observation.componentId);
            requireUniqueIds(observation.placementPackageTerminalIds,
                "placement package terminals " + observation.componentId);
            requireUniqueIds(observation.placementGeometryTerminalIds,
                "placement geometry terminals " + observation.componentId);
            requireUniqueIds(observation.slotTerminalIds,
                "slot terminals " + observation.componentId);
            requireUniqueIds(observation.partPackageTerminalIds,
                "installed package terminals " + observation.componentId);
            requireUniqueIds(observation.catalogPairs,
                "package catalog observations " + observation.componentId);
        }
        requireSetEquals("manifest/package components", manifestComponentIds(manifest),
            observedComponents);
        HashSet<String> layoutComponents = new HashSet<String>();
        for (LayoutComponentObservation component : layout.components)
            layoutComponents.add(component.componentId);
        requireSetEquals("layout/package components", layoutComponents, observedComponents);
        HashMap<String, PackageObservation> observationsByComponent =
            packageObservationsById(observations);
        for (PackageExpectation expected : manifest.packages) {
            PackageObservation observation = observationsByComponent.get(expected.componentId);
            require(observation != null,
                "task43p-package-completeness-missing:" + expected.componentId);
            requireSetEquals("physical board package terminals " + expected.componentId,
                toSet(expected.terminalIds), toSet(observation.boardPackageTerminalIds));
            requireSetEquals("physical placement package terminals " + expected.componentId,
                toSet(expected.terminalIds), toSet(observation.placementPackageTerminalIds));
            requireSetEquals("physical placement geometry terminals " + expected.componentId,
                toSet(expected.terminalIds), toSet(observation.placementGeometryTerminalIds));
            requireSetEquals("physical slot terminals " + expected.componentId,
                toSet(expected.terminalIds), toSet(observation.slotTerminalIds));
            requireSetEquals("physical installed package terminals " + expected.componentId,
                toSet(expected.terminalIds), toSet(observation.partPackageTerminalIds));
        }
    }

    private static void validateRendererCompleteness(Manifest manifest,
            RendererSnapshot renderer) {
        HashSet<String> padIds = new HashSet<String>();
        HashSet<String> terminalKeys = new HashSet<String>();
        HashMap<String, TerminalExpectation> manifestByTerminalKey =
            manifestTerminalsByKey(manifest);
        for (RendererPadObservation pad : renderer.pads) {
            requireId(pad.padId, "renderer pad");
            require(padIds.add(pad.padId), "task43p-duplicate-renderer-pad:" + pad.padId);
        }
        for (RendererTerminalObservation terminal : renderer.terminals) {
            requireId(terminal.componentId, "renderer terminal component");
            requireId(terminal.padId, "renderer terminal pad");
            requireId(terminal.terminalId, "renderer terminal");
            require(terminalKeys.add(terminal.terminalKey()),
                "task43p-duplicate-renderer-terminal:" + terminal.terminalKey());
            require(padIds.contains(terminal.padId),
                "task43p-renderer-terminal-without-pad:" + terminal.padId);
            TerminalExpectation expected = manifestByTerminalKey.get(terminal.terminalKey());
            require(expected != null,
                "task43p-renderer-terminal-without-manifest:" + terminal.terminalKey());
            require(same(expected.padId, terminal.padId),
                "task43p-renderer-terminal-pad-mismatch:" + terminal.terminalKey());
        }
        requireSetEquals("manifest/renderer pads", manifestPadIds(manifest), padIds);
        requireSetEquals("manifest/renderer terminals", manifestTerminalKeys(manifest),
            terminalKeys);
        for (PackageExpectation expectedPackage : manifest.packages) {
            HashSet<String> observedTerminalIds = new HashSet<String>();
            for (RendererTerminalObservation terminal : renderer.terminals)
                if (same(expectedPackage.componentId, terminal.componentId))
                    require(observedTerminalIds.add(terminal.terminalId),
                        "task43p-duplicate-renderer-component-terminal:" +
                        expectedPackage.componentId);
            requireSetEquals("manifest/renderer component terminals " +
                expectedPackage.componentId, toSet(expectedPackage.terminalIds),
                observedTerminalIds);
        }
    }

    private static void validateSolverCompleteness(GeneratedBoardInstance instance,
            Manifest manifest, SolverSnapshot solver) {
        HashSet<String> padIds = new HashSet<String>();
        for (SolverObservation observation : solver.observations) {
            requireId(observation.padId, "solver observation pad");
            require(padIds.add(observation.padId),
                "task43p-duplicate-solver-observation:" + observation.padId);
        }
        requireSetEquals("manifest/solver pads", manifestPadIds(manifest), padIds);
        require(instance.getDeveloperBoardEndpointOracle() != null,
            "task43p-retained-board-endpoint-oracle-missing");
        requireSetEquals("manifest/retained board endpoint pads", manifestPadIds(manifest),
            toSet(instance.getDeveloperBoardEndpointOracle().getPadIds()));
    }

    private static int validateAllTraces(LogicalBoardSnapshot logical, LayoutSnapshot layout) {
        HashSet<String> layoutPadIds = new HashSet<String>();
        HashMap<String, LayoutPadObservation> pads = new HashMap<String, LayoutPadObservation>();
        HashSet<String> netIds = new HashSet<String>();
        HashMap<String, BoardPadObservation> boardPads = boardPadsById(logical.pads);
        for (LayoutPadObservation pad : layout.pads) {
            layoutPadIds.add(pad.padId);
            pads.put(pad.padId, pad);
        }
        for (BoardNetObservation net : logical.nets)
            netIds.add(net.netId);
        HashSet<String> endpointPads = new HashSet<String>();
        for (TraceObservation trace : layout.traces) {
            requireId(trace.netId, "raw copper net");
            requireId(trace.startPadId, "raw copper start pad");
            requireId(trace.endPadId, "raw copper end pad");
            require(layoutPadIds.contains(trace.startPadId),
                "task43p-trace-start-pad-invalid:" + trace.startPadId);
            require(layoutPadIds.contains(trace.endPadId),
                "task43p-trace-end-pad-invalid:" + trace.endPadId);
            require(netIds.contains(trace.netId),
                "task43p-trace-net-invalid:" + trace.netId);
            require(trace.xPoints != null && trace.yPoints != null &&
                trace.xPoints.length >= 2 && trace.xPoints.length == trace.yPoints.length,
                "task43p-trace-points-invalid:" + trace.startPadId);
            LayoutPadObservation start = pads.get(trace.startPadId);
            LayoutPadObservation end = pads.get(trace.endPadId);
            require(trace.xPoints[0] == start.x && trace.yPoints[0] == start.y,
                "task43p-raw-copper-start-gap:" + trace.startPadId);
            int last = trace.xPoints.length - 1;
            require(trace.xPoints[last] == end.x && trace.yPoints[last] == end.y,
                "task43p-raw-copper-end-gap:" + trace.endPadId);
            BoardPadObservation startBoardPad = boardPads.get(trace.startPadId);
            BoardPadObservation endBoardPad = boardPads.get(trace.endPadId);
            require(startBoardPad != null && endBoardPad != null,
                "task43p-trace-endpoint-board-pad-missing");
            require(same(trace.netId, startBoardPad.netId) &&
                same(trace.netId, endBoardPad.netId),
                "task43p-trace-net-board-mismatch:" + trace.startPadId);
            endpointPads.add(trace.startPadId);
            endpointPads.add(trace.endPadId);
        }
        for (String padId : layoutPadIds)
            require(endpointPads.contains(padId),
                "task43p-layout-pad-has-no-trace-endpoint:" + padId);
        return layout.traces.size();
    }

    private static void validatePackageObservations(Manifest manifest,
            Vector<PackageObservation> observations, LayoutSnapshot layout) {
        HashMap<String, PackageObservation> byComponent = packageObservationsById(observations);
        HashMap<String, LayoutComponentObservation> layouts = layoutComponentsById(
            layout.components);
        for (PackageExpectation expected : manifest.packages) {
            PackageObservation observation = byComponent.get(expected.componentId);
            LayoutComponentObservation layoutObservation = layouts.get(expected.componentId);
            require(observation != null && layoutObservation != null,
                "task43p-package-observation-missing:" + expected.componentId);
            require(same(expected.packageId, observation.boardPackageId) &&
                same(expected.packageId, observation.placementPackageId) &&
                same(expected.packageId, observation.slotPackageId) &&
                same(expected.packageId, observation.partPackageId),
                "task43p-package-identity-mismatch:" + expected.componentId);
            require(same(expected.packageId, layoutObservation.packageId) &&
                same(expected.packageId, layoutObservation.realizationPackageId),
                "task43p-layout-package-identity-mismatch:" + expected.componentId);
            requireSetEquals("board package terminals " + expected.componentId,
                toSet(expected.terminalIds), toSet(observation.boardPackageTerminalIds));
            requireSetEquals("placement package terminals " + expected.componentId,
                toSet(expected.terminalIds), toSet(observation.placementPackageTerminalIds));
            requireSetEquals("placement geometry terminals " + expected.componentId,
                toSet(expected.terminalIds), toSet(observation.placementGeometryTerminalIds));
            requireSetEquals("slot terminals " + expected.componentId,
                toSet(expected.terminalIds), toSet(observation.slotTerminalIds));
            requireSetEquals("installed package terminals " + expected.componentId,
                toSet(expected.terminalIds), toSet(observation.partPackageTerminalIds));
            requireSetEquals("package catalog " + expected.componentId,
                toSet(expected.acceptedCatalog), toSet(observation.catalogPairs));
            require(observation.partInstalled && observation.slotContainsPart &&
                observation.partOwnsSlot && requireNonEmpty(observation.partId),
                "task43p-package-installed-part-identity-mismatch:" + expected.componentId);
            require(layoutObservation.packageAcceptsGeometry &&
                layoutObservation.realizationPackageIdentity &&
                layoutObservation.realizationGeometryIdentity &&
                layoutObservation.realizationVariantGeometryIdentity &&
                layoutObservation.realizationContractMatches,
                "task43p-package-realization-identity-mismatch:" + expected.componentId);
            require(expected.accepts(layoutObservation.variant, layoutObservation.transform),
                "task43p-physical-package-variant-mismatch:" + expected.componentId);
            require(same(layoutObservation.variant, layoutObservation.realizationVariant) &&
                same(layoutObservation.transform, layoutObservation.realizationTransform),
                "task43p-physical-transform-mismatch:" + expected.componentId);
        }
    }

    private static int validateRendererObservations(Manifest manifest,
            ObservationSnapshot snapshot) {
        HashMap<String, RendererPadObservation> pads = rendererPadsById(snapshot.renderer.pads);
        int checks = 0;
        for (RendererPadObservation pad : snapshot.renderer.pads) {
            require(pad.pointPresent && pad.x == pad.expectedScreenX &&
                pad.y == pad.expectedScreenY,
                "task43p-renderer-pad-projection-mismatch:" + pad.padId);
        }
        for (RendererTerminalObservation terminal : snapshot.renderer.terminals) {
            RendererPadObservation pad = pads.get(terminal.padId);
            require(pad != null && terminal.boardPadPointPresent &&
                terminal.boardPadX == pad.x && terminal.boardPadY == pad.y &&
                terminal.expectedGeometryPresent &&
                terminal.boardPadX == terminal.expectedBoardPadX &&
                terminal.boardPadY == terminal.expectedBoardPadY &&
                terminal.activePointPresent &&
                terminal.activeX == terminal.expectedActiveX &&
                terminal.activeY == terminal.expectedActiveY &&
                terminal.leadEndPointPresent &&
                terminal.leadEndX == terminal.expectedLeadEndX &&
                terminal.leadEndY == terminal.expectedLeadEndY &&
                terminal.containsProbe && terminal.geometryContainsPad &&
                terminal.targetIsBoardPad && terminal.targetValid &&
                same(terminal.padId, terminal.targetPadId),
                "task43p-renderer-surface-mismatch:" + terminal.padId);
            checks++;
        }
        return checks;
    }

    private static int validateSolverObservations(GeneratedBoardInstance instance,
            Manifest manifest,
            ObservationSnapshot snapshot) {
        HashMap<String, SolverObservation> observations = solverByPadId(
            snapshot.solver.observations);
        HashMap<String, BoardPadObservation> rawPads = boardPadsById(
            snapshot.logicalBoard.pads);
        HashMap<String, Integer> solverNodeByNet = new HashMap<String, Integer>();
        HashMap<Integer, String> solverNetByNode = new HashMap<Integer, String>();
        int checks = 0;
        for (TerminalExpectation expected : manifest.terminals) {
            SolverObservation observation = observations.get(expected.padId);
            BoardPadObservation rawPad = rawPads.get(expected.padId);
            require(observation != null && rawPad != null,
                "task43p-solver-observation-missing:" + expected.padId);
            require(same(expected.netId, rawPad.netId),
                "task43p-solver-oracle-net-mismatch:" + expected.padId);

            /*
             * The simulation binding is the live endpoint source.  The
             * retained board endpoint is resolved independently from the
             * immutable generated-board oracle: detachable bindings are
             * cross-checked against it, while non-detachable physical parts
             * are checked only for fixed/package/terminal consistency.  Neither
             * side is inferred from the solver observation snapshot.
             */
            CircuitMeasurementEndpoint actualBindingEndpoint = instance.getSimulationBindings()
                .getEndpoint(expected.padId);
            require(actualBindingEndpoint instanceof CircuitPostMeasurementEndpoint,
                "task43p-solver-actual-endpoint-missing:" + expected.padId);
            CircuitPostMeasurementEndpoint actualEndpoint =
                (CircuitPostMeasurementEndpoint) actualBindingEndpoint;
            RetainedSolverEndpoint retained = resolveRetainedSolverEndpoint(instance, manifest,
                expected);
            CircuitPostMeasurementEndpoint boardEndpoint = retained.endpoint;

            CircuitElm element = actualEndpoint.getElement();
            int postIndex = actualEndpoint.getPostIndex();
            require(element != null && postIndex >= 0 &&
                postIndex < element.getPostCount() && element.nodes != null &&
                postIndex < element.nodes.length &&
                actualEndpoint.getElement() == boardEndpoint.getElement() &&
                actualEndpoint.getPostIndex() == boardEndpoint.getPostIndex(),
                "task43p-solver-binding-endpoint-identity-mismatch:" + expected.padId);
            if (retained.detachableBinding != null) {
                CircuitMeasurementEndpoint bindingEndpoint =
                    retained.detachableBinding.getBoardEndpoint();
                require(bindingEndpoint instanceof CircuitPostMeasurementEndpoint &&
                    sameEndpointIdentity(actualEndpoint,
                        (CircuitPostMeasurementEndpoint) bindingEndpoint),
                    "task43p-solver-connection-endpoint-identity-mismatch:" +
                        expected.padId);
            }
            require(observation.endpoint != null &&
                observation.endpoint.getElement() == actualEndpoint.getElement() &&
                observation.endpoint.getPostIndex() == actualEndpoint.getPostIndex(),
                "task43p-solver-observation-endpoint-mismatch:" + expected.padId);
            require(observation.ownedElement && containsIdentity(instance.getSimulationElements(),
                actualEndpoint.getElement()) &&
                same(expected.solverClass, element.getClass().getSimpleName()) &&
                expected.solverPost == postIndex,
                "task43p-solver-post-oracle-mismatch:" + expected.padId);

            Point actualBoardPoint = actualEndpoint.getElement().getPost(
                actualEndpoint.getPostIndex());
            Point retainedBoardPoint = boardEndpoint.getElement().getPost(
                boardEndpoint.getPostIndex());
            require(actualBoardPoint != null && retainedBoardPoint != null &&
                actualBoardPoint.equals(retainedBoardPoint),
                "task43p-solver-board-endpoint-point-mismatch:" + expected.padId);
            if (retained.detachableBinding != null) {
                CircuitElm connectionElement = retained.detachableBinding.getConnectionElement();
                require(connectionElement != null &&
                    containsIdentity(instance.getSimulationElements(), connectionElement) &&
                    touchesPoint(connectionElement, retainedBoardPoint),
                    "task43p-connection-binding-element-missing:" + expected.padId);
            }

            int solverNode = element.nodes[postIndex];
            require(solverNode == observation.solverNode,
                "task43p-solver-node-observation-mismatch:" + expected.padId);
            Integer priorNode = solverNodeByNet.get(rawPad.netId);
            if (priorNode == null)
                solverNodeByNet.put(rawPad.netId, Integer.valueOf(solverNode));
            else
                require(priorNode.intValue() == solverNode,
                    "task43p-solver-net-node-mismatch:" + rawPad.netId);
            Integer nodeKey = Integer.valueOf(solverNode);
            String priorNet = solverNetByNode.get(nodeKey);
            if (priorNet == null)
                solverNetByNode.put(nodeKey, rawPad.netId);
            else
                require(priorNet.equals(rawPad.netId),
                    "task43p-solver-distinct-nets-share-node:" + rawPad.netId);
            require(!Double.isNaN(observation.voltage) &&
                !Double.isInfinite(observation.voltage) &&
                Math.abs(observation.voltage) <= 1e12,
                "task43p-live-reading-invalid:" + expected.padId);
            checks++;
        }
        return checks;
    }

    private static void validateTerminalObservations(Manifest manifest,
            ObservationSnapshot snapshot) {
        HashMap<String, BoardPadObservation> boardPads = boardPadsById(
            snapshot.logicalBoard.pads);
        HashMap<String, LayoutPadObservation> layoutPads = layoutPadsById(snapshot.layout.pads);
        HashMap<String, RendererTerminalObservation> renderTerminals =
            rendererTerminalsByKey(snapshot.renderer.terminals);
        HashMap<String, PackageObservation> packages = packageObservationsById(snapshot.packages);
        for (TerminalExpectation expected : manifest.terminals) {
            BoardPadObservation boardPad = boardPads.get(expected.padId);
            LayoutPadObservation layoutPad = layoutPads.get(expected.padId);
            RendererTerminalObservation rendered = renderTerminals.get(expected.terminalKey());
            PackageObservation packageObservation = packages.get(expected.componentId);
            require(boardPad != null && layoutPad != null && rendered != null &&
                packageObservation != null,
                "task43p-terminal-observation-missing:" + expected.padId);
            require(same(expected.componentId, boardPad.componentId) &&
                same(expected.terminalId, boardPad.terminalId) &&
                same(expected.netId, boardPad.netId),
                "task43p-raw-board-terminal-mismatch:" + expected.padId);
            require(same(expected.componentId, rendered.componentId) &&
                same(expected.padId, rendered.padId) &&
                same(expected.terminalId, rendered.terminalId),
                "task43p-render-terminal-mismatch:" + expected.padId);
            require(packageObservation.boardPackageTerminalIds.contains(expected.terminalId),
                "task43p-terminal-package-mismatch:" + expected.padId);
            require(layoutPad != null, "task43p-layout-terminal-mismatch:" + expected.padId);
        }
    }

    private static Vector<NegativeFixtureResult> runNegativeFixtures(GeneratedBoardInstance instance,
            Manifest manifest,
            ObservationSnapshot validSnapshot) {
        Vector<NegativeFixtureResult> result = new Vector<NegativeFixtureResult>();
        String targetPadId = manifest.terminals.firstElement().padId;
        String targetTerminalKey = manifest.terminals.firstElement().terminalKey();

        ObservationSnapshot rendererOffset = validSnapshot.copy();
        RendererPadObservation rendererPad = findRendererPad(rendererOffset.renderer, targetPadId);
        require(rendererPad != null, "task43p-fixture-renderer-pad-missing");
        rendererPad.x += 20;
        result.add(expectRejected(instance, manifest, rendererOffset, "renderer-only-pad-offset",
            "renderer source pad point +20px"));

        ObservationSnapshot rendererLeadOffset = validSnapshot.copy();
        RendererTerminalObservation rendererTerminal = findRendererTerminal(
            rendererLeadOffset.renderer, targetTerminalKey);
        require(rendererTerminal != null, "task43p-fixture-renderer-terminal-missing");
        rendererTerminal.leadEndX += 20;
        result.add(expectRejected(instance, manifest, rendererLeadOffset, "renderer-only-lead-offset",
            "renderer source lead endpoint +20px"));

        ObservationSnapshot rawGap = validSnapshot.copy();
        TraceObservation rawTrace = findTrace(rawGap.layout, targetPadId);
        require(rawTrace != null, "task43p-fixture-raw-trace-missing");
        if (same(rawTrace.startPadId, targetPadId))
            rawTrace.xPoints[0] += 20;
        else
            rawTrace.xPoints[rawTrace.xPoints.length - 1] += 20;
        result.add(expectRejected(instance, manifest, rawGap, "raw-copper-endpoint-gap",
            "raw copper endpoint displacement"));

        ObservationSnapshot wrongNet = validSnapshot.copy();
        TraceObservation wrongNetTrace = findTrace(wrongNet.layout, targetPadId);
        require(wrongNetTrace != null, "task43p-fixture-wrong-net-trace-missing");
        wrongNetTrace.netId = "TASK43P_WRONG_NET";
        result.add(expectRejected(instance, manifest, wrongNet, "raw-net-mismatch",
            "raw copper net changed before validation"));

        ObservationSnapshot solverMismatch = validSnapshot.copy();
        SolverObservation solver = findSolverObservation(solverMismatch.solver, targetPadId);
        require(solver != null, "task43p-fixture-solver-observation-missing");
        require(solver.endpoint != null, "task43p-fixture-solver-endpoint-missing");
        TerminalExpectation targetExpected = manifestTerminalsByPadId(manifest).get(targetPadId);
        require(targetExpected != null, "task43p-fixture-solver-manifest-terminal-missing");
        RetainedSolverEndpoint retainedTarget = resolveRetainedSolverEndpoint(instance, manifest,
            targetExpected);
        CircuitMeasurementEndpoint liveTargetEndpoint = instance.getSimulationBindings()
            .getEndpoint(targetPadId);
        require(liveTargetEndpoint instanceof CircuitPostMeasurementEndpoint &&
            retainedTarget.endpoint != null && sameEndpointIdentity(
                (CircuitPostMeasurementEndpoint) liveTargetEndpoint, retainedTarget.endpoint),
            "task43p-fixture-solver-retained-endpoint-missing");
        /* Anchor the fixture to the live producer lookup, not a copied DTO field. */
        solver.endpoint = (CircuitPostMeasurementEndpoint) liveTargetEndpoint;
        CircuitElm targetElement = retainedTarget.endpoint.getElement();
        int targetPost = retainedTarget.endpoint.getPostIndex();
        require(targetElement != null && targetPost >= 0 &&
            targetPost < targetElement.getPostCount() && targetElement.nodes != null &&
            targetPost < targetElement.nodes.length,
            "task43p-fixture-solver-retained-endpoint-invalid");
        int targetNode = targetElement.nodes[targetPost];
        SolverObservation incompatible = null;
        Vector<SolverObservation> solverCandidates =
            new Vector<SolverObservation>(solverMismatch.solver.observations);
        Collections.sort(solverCandidates, new java.util.Comparator<SolverObservation>() {
            public int compare(SolverObservation first, SolverObservation second) {
                String firstPad = first == null ? null : first.padId;
                String secondPad = second == null ? null : second.padId;
                if (firstPad == null)
                    return secondPad == null ? 0 : -1;
                return secondPad == null ? 1 : firstPad.compareTo(secondPad);
            }
        });
        /* Prefer the exact same-node/different-element collision when present. */
        for (SolverObservation candidate : solverCandidates) {
            if (candidate == solver || candidate.endpoint == null)
                continue;
            CircuitElm candidateElement = candidate.endpoint.getElement();
            int candidatePost = candidate.endpoint.getPostIndex();
            boolean candidateNodeValid = candidateElement != null && candidateElement.nodes != null &&
                candidatePost >= 0 && candidatePost < candidateElement.nodes.length;
            if (candidateNodeValid && candidateElement != targetElement &&
                    candidatePost == targetPost && same(targetElement.getClass().getSimpleName(),
                        candidateElement.getClass().getSimpleName()) &&
                    targetNode == candidateElement.nodes[candidatePost]) {
                incompatible = candidate;
                break;
            }
        }
        /* Otherwise choose an endpoint incompatible by independent class/post/node facts. */
        if (incompatible == null) {
            for (SolverObservation candidate : solverCandidates) {
                if (candidate == solver || candidate.endpoint == null)
                    continue;
                CircuitElm candidateElement = candidate.endpoint.getElement();
                int candidatePost = candidate.endpoint.getPostIndex();
                boolean classDiffers = targetElement == null || candidateElement == null ||
                    !same(targetElement.getClass().getSimpleName(),
                        candidateElement.getClass().getSimpleName());
                boolean postDiffers = targetPost != candidatePost;
                boolean candidateNodeValid = candidateElement != null &&
                    candidateElement.nodes != null && candidatePost >= 0 &&
                    candidatePost < candidateElement.nodes.length;
                boolean nodeDiffers = !candidateNodeValid ||
                    targetNode != candidateElement.nodes[candidatePost];
                if (classDiffers || postDiffers || nodeDiffers) {
                    incompatible = candidate;
                    break;
                }
            }
        }
        if (incompatible != null) {
            solver.endpoint = incompatible.endpoint;
        } else {
            /*
             * The fallback remains a source-observation mutation, not a DTO
             * edit: it invalidates the independently captured element node
             * when this board has no observed endpoint with an incompatible
             * class/post/node.  This is deterministic and canonical-path
             * validation must reject it.
             */
            require(targetElement != null && targetPost >= 0 &&
                targetPost < targetElement.getPostCount() && targetElement.nodes != null &&
                targetPost < targetElement.nodes.length,
                "task43p-fixture-solver-target-invalid");
            solver.solverNode = targetNode == Integer.MAX_VALUE ? targetNode - 1 :
                targetNode + 1;
        }
        result.add(expectRejected(instance, manifest, solverMismatch, "solver-binding-post-mismatch",
            "solver source endpoint identity/class/post/node changed before canonical validation"));

        ObservationSnapshot emptyNet = validSnapshot.copy();
        BoardNetObservation unmanifestedNet = new BoardNetObservation();
        unmanifestedNet.netId = "TASK43P_EMPTY_UNMANIFESTED_NET";
        emptyNet.logicalBoard.nets.add(unmanifestedNet);
        result.add(expectRejected(instance, manifest, emptyNet, "raw-empty-unmanifested-net",
            "raw logical-board source added an empty unmanifested net"));

        ObservationSnapshot mirrorMismatch = validSnapshot.copy();
        PackageObservation mirrorPackage = findConnectorPackage(mirrorMismatch.packages);
        require(mirrorPackage != null, "task43p-fixture-connector-package-missing");
        LayoutComponentObservation mirrorLayout = findLayoutComponent(mirrorMismatch.layout,
            mirrorPackage.componentId);
        require(mirrorLayout != null, "task43p-fixture-connector-layout-missing");
        mirrorLayout.transform = "TASK43P_MIRROR_MISMATCH";
        result.add(expectRejected(instance, manifest, mirrorMismatch, "mirror-transform-mismatch",
            "package source transform changed before validation"));

        ObservationSnapshot selfConsistent = validSnapshot.copy();
        BoardPadObservation selfPad = findBoardPad(selfConsistent.logicalBoard, targetPadId);
        require(selfPad != null, "task43p-fixture-self-consistent-pad-missing");
        String oldNet = selfPad.netId;
        String wrongNetId = "TASK43P_WRONG_NET";
        selfPad.netId = wrongNetId;
        BoardNetObservation oldNetObservation = findBoardNet(selfConsistent.logicalBoard, oldNet);
        if (oldNetObservation != null)
            oldNetObservation.padIds.remove(targetPadId);
        BoardNetObservation newNetObservation = findBoardNet(selfConsistent.logicalBoard,
            wrongNetId);
        if (newNetObservation == null) {
            newNetObservation = new BoardNetObservation();
            newNetObservation.netId = wrongNetId;
            selfConsistent.logicalBoard.nets.add(newNetObservation);
        }
        if (!newNetObservation.padIds.contains(targetPadId))
            newNetObservation.padIds.add(targetPadId);
        for (TraceObservation trace : selfConsistent.layout.traces)
            if (same(trace.startPadId, targetPadId) || same(trace.endPadId, targetPadId))
                trace.netId = wrongNetId;
        result.add(expectRejected(instance, manifest, selfConsistent,
            "internally-self-consistent-wrong-mapping",
            "raw board/net/copper sources coherently remapped to an unmanifested net"));

        ObservationSnapshot omittedTerminal = validSnapshot.copy();
        RendererTerminalObservation omitted = findRendererTerminal(omittedTerminal.renderer,
            targetTerminalKey);
        require(omitted != null, "task43p-fixture-omitted-terminal-missing");
        omittedTerminal.renderer.terminals.remove(omitted);
        result.add(expectRejected(instance, manifest, omittedTerminal, "omitted-terminal",
            "renderer source terminal omitted before canonical validation"));
        return result;
    }

    private static NegativeFixtureResult expectRejected(GeneratedBoardInstance instance,
            Manifest manifest,
            ObservationSnapshot mutated, String id, String sourceMutation) {
        boolean caught = false;
        try {
            validateCanonical(instance, manifest, mutated);
        } catch (IllegalStateException expected) {
            caught = true;
        }
        return new NegativeFixtureResult(id, sourceMutation, caught);
    }

    private static String buildEvidence(GeneratedBoardInstance instance, Manifest manifest,
            ObservationSnapshot snapshot, ValidationSummary summary,
            Vector<NegativeFixtureResult> negativeFixtures) {
        StringBuilder result = new StringBuilder();
        result.append("{\"status\":\"PASS\",\"validator\":\"canonical-source-observations-v2\",");
        result.append("\"manifestAuthoring\":\"independent\",\"observationSources\":[");
        result.append(q("raw-logical-board")).append(',').append(q("raw-pcb-layout-copper"))
            .append(',').append(q("renderer")).append(',').append(q("physical-package"))
            .append(',').append(q("solver-post")).append("],");
        result.append("\"family\":").append(q(instance.getCircuitFamilyId())).append(',');
        result.append("\"topology\":").append(q(instance.getTopologyVariantId())).append(',');
        result.append("\"seed\":").append(instance.getSeed()).append(',');
        result.append("\"fault\":").append(q(instance.getFaultBinding().getFault().getId()))
            .append(',');
        result.append("\"faultType\":")
            .append(q(instance.getFaultBinding().getFault().getType().toString())).append(',');
        result.append("\"faultOwner\":").append(q(instance.getFaultLocus() == null ? null :
            instance.getFaultLocus().getOwnerId())).append(',');
        result.append("\"terminalCount\":").append(manifest.terminals.size()).append(',');
        result.append("\"rawTraceChecks\":").append(summary.rawTraceChecks).append(',');
        result.append("\"renderedSurfaceChecks\":").append(summary.renderedSurfaceChecks)
            .append(',');
        result.append("\"solverChecks\":").append(summary.solverChecks).append(',');
        result.append("\"negativeFixtureScope\":\"same-canonical-path-source-snapshot\",");
        result.append("\"liveReadings\":[");
        HashMap<String, SolverObservation> solvers = solverByPadId(snapshot.solver.observations);
        Vector<String> padIds = manifestPadIdsSorted(manifest);
        for (int index = 0; index < padIds.size(); index++) {
            if (index > 0)
                result.append(',');
            SolverObservation solver = solvers.get(padIds.get(index));
            result.append(q(padIds.get(index) + "=" + Double.toString(solver.voltage)));
        }
        result.append("],\"negativeFixtures\":[");
        for (int index = 0; index < negativeFixtures.size(); index++) {
            if (index > 0)
                result.append(',');
            NegativeFixtureResult fixture = negativeFixtures.get(index);
            result.append("{\"id\":").append(q(fixture.id))
                .append(",\"sourceMutation\":").append(q(fixture.sourceMutation))
                .append(",\"caught\":").append(fixture.caught).append('}');
        }
        result.append("],\"terminals\":[");
        HashMap<String, BoardPadObservation> boardPads = boardPadsById(snapshot.logicalBoard.pads);
        HashMap<String, LayoutPadObservation> layoutPads = layoutPadsById(snapshot.layout.pads);
        HashMap<String, RendererTerminalObservation> renderTerminals =
            rendererTerminalsByKey(snapshot.renderer.terminals);
        HashMap<String, PackageObservation> packages = packageObservationsById(snapshot.packages);
        for (int index = 0; index < manifest.terminals.size(); index++) {
            if (index > 0)
                result.append(',');
            TerminalExpectation expected = manifest.terminals.get(index);
            BoardPadObservation boardPad = boardPads.get(expected.padId);
            LayoutPadObservation layoutPad = layoutPads.get(expected.padId);
            RendererTerminalObservation rendered = renderTerminals.get(expected.terminalKey());
            PackageObservation packageObservation = packages.get(expected.componentId);
            SolverObservation solver = solvers.get(expected.padId);
            LayoutComponentObservation layoutComponent = findLayoutComponent(snapshot.layout,
                expected.componentId);
            CircuitElm solverElement = solver.endpoint.getElement();
            int solverPost = solver.endpoint.getPostIndex();
            result.append("{\"padId\":").append(q(expected.padId))
                .append(",\"rawNetId\":").append(q(boardPad.netId))
                .append(",\"rawEndpoint\":[").append(layoutPad.x).append(',')
                .append(layoutPad.y).append("]")
                .append(",\"renderedPadId\":").append(q(rendered.padId))
                .append(",\"renderedTerminalId\":").append(q(rendered.terminalId))
                .append(",\"renderedPadPoint\":[").append(rendered.boardPadX).append(',')
                .append(rendered.boardPadY).append("]")
                .append(",\"packageId\":").append(q(packageObservation.placementPackageId))
                .append(",\"variant\":").append(q(layoutComponent.variant))
                .append(",\"transform\":").append(q(layoutComponent.transform))
                .append(",\"solverOracleNetId\":").append(q(expected.netId))
                .append(",\"solverClass\":").append(q(solverElement == null ? null :
                    solverElement.getClass().getSimpleName()))
                .append(",\"solverPost\":").append(solverPost)
                .append(",\"solverNode\":").append(solverElement == null ? -1 :
                    solverElement.nodes[solverPost]).append('}');
        }
        return result.append("]}").toString();
    }

    private static Manifest manifestFor(String family) {
        Manifest result = new Manifest();
        if ("LED_INDICATOR".equals(family)) {
            addPackage(result, "J1", "THROUGH_HOLE_CONNECTOR_2", true);
            addPackage(result, "R1", "AXIAL_RESISTOR", false, "SPAN_220", "SPAN_240",
                "SPAN_260");
            addPackage(result, "LED1", "THROUGH_HOLE_LED", false);
            addTerminal(result, "J1.1", "J1", "1", "VIN", "SwitchElm", 1);
            addTerminal(result, "J1.2", "J1", "2", "GND", "GroundElm", 0);
            addTerminal(result, "R1.1", "R1", "1", "VIN", "WireElm", 1);
            addTerminal(result, "R1.2", "R1", "2", "LED_NODE", "WireElm", 0);
            addTerminal(result, "LED1.A", "LED1", "A", "LED_NODE", "WireElm", 1);
            addTerminal(result, "LED1.K", "LED1", "K", "GND", "GroundElm", 0);
        } else if (DiodeProtectedIndicatorGenerator.FAMILY_ID.equals(family)) {
            addPackage(result, "J1", "THROUGH_HOLE_CONNECTOR_2", true);
            addPackage(result, "D1", "AXIAL_DIODE", false, "SPAN_230", "SPAN_250");
            addPackage(result, "R1", "AXIAL_RESISTOR", false, "SPAN_220", "SPAN_240",
                "SPAN_260");
            addPackage(result, "LED1", "THROUGH_HOLE_LED", false);
            addTerminal(result, "J1.1", "J1", "1", "VIN", "SwitchElm", 1);
            addTerminal(result, "J1.2", "J1", "2", "GND", "GroundElm", 0);
            addTerminal(result, "D1.A", "D1", "A", "VIN", "WireElm", 1);
            addTerminal(result, "D1.K", "D1", "K", "DIODE_OUT", "WireElm", 0);
            addTerminal(result, "R1.1", "R1", "1", "DIODE_OUT", "ResistorElm", 0);
            addTerminal(result, "R1.2", "R1", "2", "LED_NODE", "ResistorElm", 1);
            addTerminal(result, "LED1.A", "LED1", "A", "LED_NODE", "LEDElm", 0);
            addTerminal(result, "LED1.K", "LED1", "K", "GND", "LEDElm", 1);
        } else if (RcDelayGenerator.FAMILY_ID.equals(family)) {
            addPackage(result, "J1", "THROUGH_HOLE_CONNECTOR_2", true);
            addPackage(result, "J2", "THROUGH_HOLE_OUTPUT_HEADER_2", false);
            addPackage(result, "R1", "AXIAL_RESISTOR", false, "SPAN_220", "SPAN_240",
                "SPAN_260");
            addPackage(result, "R2", "AXIAL_RESISTOR", false, "SPAN_220", "SPAN_240",
                "SPAN_260");
            addPackage(result, "C1", "RADIAL_ELECTROLYTIC_CAPACITOR", false);
            addPackage(result, "C2", "RADIAL_CERAMIC_CAPACITOR", false);
            addTerminal(result, "J1.1", "J1", "1", "VIN", "WireElm", 1);
            addTerminal(result, "J1.2", "J1", "2", "GND", "GroundElm", 0);
            addTerminal(result, "J2.1", "J2", "1", "RC_OUT", "WireElm", 1);
            addTerminal(result, "J2.2", "J2", "2", "GND", "GroundElm", 0);
            addTerminal(result, "R1.1", "R1", "1", "VIN", "WireElm", 1);
            addTerminal(result, "R1.2", "R1", "2", "RC_OUT", "ResistorElm", 1);
            addTerminal(result, "R2.1", "R2", "1", "RC_OUT", "WireElm", 1);
            addTerminal(result, "R2.2", "R2", "2", "GND", "ResistorElm", 1);
            addTerminal(result, "C1.+", "C1", "+", "RC_OUT", "WireElm", 1);
            addTerminal(result, "C1.-", "C1", "-", "GND", "GroundElm", 0);
            addTerminal(result, "C2.1", "C2", "1", "VIN", "CapacitorElm", 0);
            addTerminal(result, "C2.2", "C2", "2", "GND", "CapacitorElm", 1);
        } else if (NpnLowSideSwitchGenerator.FAMILY_ID.equals(family)) {
            addSwitchPackage(result, "J1");
            addSwitchPackage(result, "J2");
            addPackage(result, "RLOAD", "AXIAL_RESISTOR", false, "SPAN_220", "SPAN_240",
                "SPAN_260");
            addPackage(result, "RB", "AXIAL_RESISTOR", false, "SPAN_220", "SPAN_240",
                "SPAN_260");
            addPackage(result, "RPD", "AXIAL_RESISTOR", false, "SPAN_220", "SPAN_240",
                "SPAN_260");
            addPackage(result, "LED1", "THROUGH_HOLE_LED", false);
            addPackage(result, "Q1", "TO92_NPN", false);
            /* J1.1 is the generator's raw load-input WireElm post 0. */
            addTerminal(result, "J1.1", "J1", "1", "LOAD_SUPPLY", "WireElm", 0);
            addTerminal(result, "J1.2", "J1", "2", "GND", "GroundElm", 0);
            addTerminal(result, "RLOAD.1", "RLOAD", "1", "LOAD_SUPPLY", "WireElm", 1);
            addTerminal(result, "RLOAD.2", "RLOAD", "2", "LOAD_NODE", "WireElm", 0);
            addTerminal(result, "LED1.A", "LED1", "A", "LOAD_NODE", "WireElm", 1);
            addTerminal(result, "LED1.K", "LED1", "K", "COLLECTOR", "WireElm", 0);
            addTerminal(result, "J2.1", "J2", "1", "CONTROL_INPUT", "WireElm", 1);
            addTerminal(result, "J2.2", "J2", "2", "GND", "GroundElm", 0);
            addTerminal(result, "RB.1", "RB", "1", "CONTROL_INPUT", "WireElm", 1);
            addTerminal(result, "RB.2", "RB", "2", "BASE", "WireElm", 1);
            addTerminal(result, "RPD.1", "RPD", "1", "BASE", "WireElm", 1);
            addTerminal(result, "RPD.2", "RPD", "2", "GND", "GroundElm", 0);
            addTerminal(result, "Q1.B", "Q1", "B", "BASE", "WireElm", 1);
            addTerminal(result, "Q1.C", "Q1", "C", "COLLECTOR", "WireElm", 1);
            addTerminal(result, "Q1.E", "Q1", "E", "GND", "WireElm", 1);
        } else if (NmosLowSideSwitchGenerator.FAMILY_ID.equals(family)) {
            addSwitchPackage(result, "J1");
            addSwitchPackage(result, "J2");
            addPackage(result, "RLOAD", "AXIAL_RESISTOR", false, "SPAN_220", "SPAN_240",
                "SPAN_260");
            addPackage(result, "RPD", "AXIAL_RESISTOR", false, "SPAN_220", "SPAN_240",
                "SPAN_260");
            addPackage(result, "LED1", "THROUGH_HOLE_LED", false);
            addPackage(result, "Q1", "TO92_NMOS", false);
            addTerminal(result, "J1.1", "J1", "1", "LOAD_SUPPLY", "WireElm", 0);
            addTerminal(result, "J1.2", "J1", "2", "GND", "GroundElm", 0);
            addTerminal(result, "RLOAD.1", "RLOAD", "1", "LOAD_SUPPLY", "WireElm", 1);
            addTerminal(result, "RLOAD.2", "RLOAD", "2", "LOAD_NODE", "WireElm", 0);
            addTerminal(result, "LED1.A", "LED1", "A", "LOAD_NODE", "WireElm", 1);
            addTerminal(result, "LED1.K", "LED1", "K", "DRAIN", "WireElm", 0);
            addTerminal(result, "J2.1", "J2", "1", "CONTROL_INPUT", "WireElm", 1);
            addTerminal(result, "J2.2", "J2", "2", "GND", "GroundElm", 0);
            addTerminal(result, "RPD.1", "RPD", "1", "CONTROL_INPUT", "WireElm", 0);
            addTerminal(result, "RPD.2", "RPD", "2", "GND", "GroundElm", 0);
            addTerminal(result, "Q1.G", "Q1", "G", "CONTROL_INPUT", "WireElm", 1);
            addTerminal(result, "Q1.D", "Q1", "D", "DRAIN", "WireElm", 1);
            addTerminal(result, "Q1.S", "Q1", "S", "GND", "WireElm", 1);
        } else if ("PARALLEL_DUAL_INDICATOR".equals(family)) {
            addSwitchPackage(result, "J1");
            addPackage(result, "R1", "AXIAL_RESISTOR", false, "SPAN_220", "SPAN_240",
                "SPAN_260");
            addPackage(result, "R2", "AXIAL_RESISTOR", false, "SPAN_220", "SPAN_240",
                "SPAN_260");
            addPackage(result, "LED1", "THROUGH_HOLE_LED", false);
            addPackage(result, "LED2", "THROUGH_HOLE_LED", false);
            addTerminal(result, "J1.1", "J1", "1", "VIN", "SwitchElm", 1);
            addTerminal(result, "J1.2", "J1", "2", "GND", "WireElm", 1);
            addTerminal(result, "R1.1", "R1", "1", "VIN", "WireElm", 1);
            addTerminal(result, "R1.2", "R1", "2", "BRANCH1_NODE", "WireElm", 0);
            addTerminal(result, "LED1.A", "LED1", "A", "BRANCH1_NODE", "WireElm", 1);
            addTerminal(result, "LED1.K", "LED1", "K", "GND", "WireElm", 0);
            addTerminal(result, "R2.1", "R2", "1", "VIN", "WireElm", 1);
            addTerminal(result, "R2.2", "R2", "2", "BRANCH2_NODE", "WireElm", 0);
            addTerminal(result, "LED2.A", "LED2", "A", "BRANCH2_NODE", "WireElm", 1);
            addTerminal(result, "LED2.K", "LED2", "K", "GND", "WireElm", 0);
        } else {
            throw new IllegalStateException("task43p-physical-unknown-family:" + family);
        }
        return result;
    }

    private static void addSwitchPackage(Manifest manifest, String componentId) {
        addPackage(manifest, componentId, "THROUGH_HOLE_CONNECTOR_2", true);
    }

    private static void addPackage(Manifest manifest, String componentId, String packageId,
            boolean connector) {
        addPackage(manifest, componentId, packageId, connector, "DEFAULT");
    }

    private static void addPackage(Manifest manifest, String componentId, String packageId,
            boolean connector, String firstVariant, String... additionalVariants) {
        PackageExpectation expected = new PackageExpectation(componentId, packageId);
        expected.addCatalogPair(firstVariant, "IDENTITY");
        for (String variant : additionalVariants)
            expected.addCatalogPair(variant, "IDENTITY");
        if (connector)
            expected.addCatalogPair("DEFAULT_MIRRORED_X", "MIRROR_X");
        manifest.packages.add(expected);
    }

    private static void addTerminal(Manifest manifest, String padId, String componentId,
            String terminalId, String netId, String solverClass, int solverPost) {
        manifest.terminals.add(new TerminalExpectation(padId, componentId, terminalId, netId,
            solverClass, solverPost));
        PackageExpectation expected = manifest.getPackage(componentId);
        if (expected != null)
            expected.addTerminal(terminalId);
    }

    private static Vector<String> unionPadIds(TroubleshootBoard board, PcbBoardLayout layout) {
        Vector<String> result = new Vector<String>();
        for (String padId : board.getPadIds())
            if (!result.contains(padId))
                result.add(padId);
        for (PcbPadPlacement pad : layout.getPads())
            if (!result.contains(pad.getPadId()))
                result.add(pad.getPadId());
        return result;
    }

    private static Vector<String> unionComponentIds(TroubleshootBoard board,
            PcbBoardLayout layout) {
        Vector<String> result = new Vector<String>();
        for (String componentId : board.getComponentIds())
            if (!result.contains(componentId))
                result.add(componentId);
        for (PcbComponentPlacement component : layout.getComponents())
            if (!result.contains(component.getComponentId()))
                result.add(component.getComponentId());
        return result;
    }

    private static HashMap<String, BoardPadObservation> boardPadsById(
            Vector<BoardPadObservation> values) {
        HashMap<String, BoardPadObservation> result = new HashMap<String, BoardPadObservation>();
        for (BoardPadObservation value : values)
            result.put(value.padId, value);
        return result;
    }

    private static HashMap<String, BoardComponentObservation> boardComponentsById(
            Vector<BoardComponentObservation> values) {
        HashMap<String, BoardComponentObservation> result =
            new HashMap<String, BoardComponentObservation>();
        for (BoardComponentObservation value : values)
            result.put(value.componentId, value);
        return result;
    }

    private static HashMap<String, BoardNetObservation> boardNetsById(
            Vector<BoardNetObservation> values) {
        HashMap<String, BoardNetObservation> result = new HashMap<String, BoardNetObservation>();
        for (BoardNetObservation value : values)
            result.put(value.netId, value);
        return result;
    }

    private static HashMap<String, LayoutPadObservation> layoutPadsById(
            Vector<LayoutPadObservation> values) {
        HashMap<String, LayoutPadObservation> result =
            new HashMap<String, LayoutPadObservation>();
        for (LayoutPadObservation value : values)
            result.put(value.padId, value);
        return result;
    }

    private static HashMap<String, LayoutComponentObservation> layoutComponentsById(
            Vector<LayoutComponentObservation> values) {
        HashMap<String, LayoutComponentObservation> result =
            new HashMap<String, LayoutComponentObservation>();
        for (LayoutComponentObservation value : values)
            result.put(value.componentId, value);
        return result;
    }

    private static HashMap<String, PackageObservation> packageObservationsById(
            Vector<PackageObservation> values) {
        HashMap<String, PackageObservation> result = new HashMap<String, PackageObservation>();
        for (PackageObservation value : values)
            result.put(value.componentId, value);
        return result;
    }

    private static HashMap<String, RendererPadObservation> rendererPadsById(
            Vector<RendererPadObservation> values) {
        HashMap<String, RendererPadObservation> result =
            new HashMap<String, RendererPadObservation>();
        for (RendererPadObservation value : values)
            result.put(value.padId, value);
        return result;
    }

    private static HashMap<String, RendererTerminalObservation> rendererTerminalsByKey(
            Vector<RendererTerminalObservation> values) {
        HashMap<String, RendererTerminalObservation> result =
            new HashMap<String, RendererTerminalObservation>();
        for (RendererTerminalObservation value : values)
            result.put(value.terminalKey(), value);
        return result;
    }

    private static HashMap<String, SolverObservation> solverByPadId(
            Vector<SolverObservation> values) {
        HashMap<String, SolverObservation> result = new HashMap<String, SolverObservation>();
        for (SolverObservation value : values)
            result.put(value.padId, value);
        return result;
    }

    private static HashSet<String> boardPadIds(Vector<BoardPadObservation> values) {
        HashSet<String> result = new HashSet<String>();
        for (BoardPadObservation value : values)
            result.add(value.padId);
        return result;
    }

    private static HashSet<String> manifestPadIds(Manifest manifest) {
        HashSet<String> result = new HashSet<String>();
        for (TerminalExpectation expected : manifest.terminals)
            result.add(expected.padId);
        return result;
    }

    private static HashSet<String> manifestNetIds(Manifest manifest) {
        HashSet<String> result = new HashSet<String>();
        for (TerminalExpectation expected : manifest.terminals)
            result.add(expected.netId);
        return result;
    }

    private static HashMap<String, TerminalExpectation> manifestTerminalsByPadId(
            Manifest manifest) {
        HashMap<String, TerminalExpectation> result =
            new HashMap<String, TerminalExpectation>();
        for (TerminalExpectation expected : manifest.terminals)
            result.put(expected.padId, expected);
        return result;
    }

    private static HashMap<String, TerminalExpectation> manifestTerminalsByKey(
            Manifest manifest) {
        HashMap<String, TerminalExpectation> result =
            new HashMap<String, TerminalExpectation>();
        for (TerminalExpectation expected : manifest.terminals)
            result.put(expected.terminalKey(), expected);
        return result;
    }

    private static Vector<String> manifestPadIdsSorted(Manifest manifest) {
        Vector<String> result = new Vector<String>();
        for (TerminalExpectation expected : manifest.terminals)
            if (!result.contains(expected.padId))
                result.add(expected.padId);
        Collections.sort(result);
        return result;
    }

    private static HashSet<String> manifestComponentIds(Manifest manifest) {
        HashSet<String> result = new HashSet<String>();
        for (PackageExpectation expected : manifest.packages)
            result.add(expected.componentId);
        return result;
    }

    private static HashSet<String> manifestTerminalKeys(Manifest manifest) {
        HashSet<String> result = new HashSet<String>();
        for (TerminalExpectation expected : manifest.terminals)
            result.add(expected.terminalKey());
        return result;
    }

    private static HashSet<String> toSet(Vector<String> values) {
        return new HashSet<String>(values);
    }

    private static void requireUniqueIds(Vector<String> values, String description) {
        HashSet<String> observed = new HashSet<String>();
        for (String value : values) {
            requireId(value, description);
            require(observed.add(value), "task43p-duplicate-id:" + description + ':' + value);
        }
    }

    private static void requireSetEquals(String description, HashSet<String> expected,
            HashSet<String> actual) {
        Vector<String> expectedIds = new Vector<String>(expected);
        Vector<String> actualIds = new Vector<String>(actual);
        Collections.sort(expectedIds);
        Collections.sort(actualIds);
        require(expected.equals(actual), "task43p-set-mismatch:" + description +
            ":expected=" + expectedIds.toString() + ":actual=" + actualIds.toString());
    }

    private static void requireId(String value, String description) {
        require(requireNonEmpty(value), "task43p-invalid-id:" + description);
    }

    private static boolean requireNonEmpty(String value) {
        return value != null && value.trim().length() > 0;
    }

    private static void require(boolean condition, String message) {
        if (!condition)
            throw new IllegalStateException(message);
    }

    private static boolean same(String first, String second) {
        return first == null ? second == null : first.equals(second);
    }

    private static String key(String componentId, String terminalId) {
        return String.valueOf(componentId) + KEY_SEPARATOR + String.valueOf(terminalId);
    }

    private static String pair(String variant, String transform) {
        return String.valueOf(variant) + KEY_SEPARATOR + String.valueOf(transform);
    }

    private static int[] copyIntArray(int[] values) {
        if (values == null)
            return null;
        int[] result = new int[values.length];
        for (int index = 0; index < values.length; index++)
            result[index] = values[index];
        return result;
    }

    private static boolean containsIdentity(Vector<CircuitElm> values, CircuitElm target) {
        if (target == null)
            return false;
        for (CircuitElm value : values)
            if (value == target)
                return true;
        return false;
    }

    private static RetainedSolverEndpoint resolveRetainedSolverEndpoint(
            GeneratedBoardInstance instance, Manifest manifest, TerminalExpectation expected) {
        GeneratedBoardEndpointOracle oracle = instance.getDeveloperBoardEndpointOracle();
        require(oracle != null, "task43p-retained-board-endpoint-oracle-missing:" +
            expected.padId);
        CircuitMeasurementEndpoint oracleEndpoint = oracle.getEndpoint(expected.padId);
        require(oracleEndpoint instanceof CircuitPostMeasurementEndpoint,
            "task43p-retained-board-endpoint-missing:" + expected.padId);
        CircuitPostMeasurementEndpoint retainedBoardEndpoint =
            (CircuitPostMeasurementEndpoint) oracleEndpoint;

        GeneratedComponentConnectionBinding connectionBinding =
            findConnectionBinding(instance, expected);
        if (connectionBinding != null) {
            require(same(expected.componentId, connectionBinding.getComponentId()) &&
                same(expected.padId, connectionBinding.getPadId()),
                "task43p-connection-binding-identity-mismatch:" + expected.padId);
            CircuitMeasurementEndpoint bindingBoardEndpoint =
                connectionBinding.getBoardEndpoint();
            require(bindingBoardEndpoint instanceof CircuitPostMeasurementEndpoint,
                "task43p-connection-binding-board-endpoint-invalid:" + expected.padId);
            CircuitPostMeasurementEndpoint bindingPost =
                (CircuitPostMeasurementEndpoint) bindingBoardEndpoint;
            require(sameEndpointIdentity(retainedBoardEndpoint, bindingPost),
                "task43p-connection-binding-oracle-mismatch:" + expected.padId);
            return new RetainedSolverEndpoint(retainedBoardEndpoint, connectionBinding);
        }

        /*
         * Non-detachable pads use the immutable board-endpoint oracle above,
         * not an internal component terminal.  The installed generated part
         * is checked only as a fixed physical/package/terminal boundary; an
         * internal LED or resistor may legitimately terminate at its own
         * LEDElm/ResistorElm while the board pad terminates on a board trace.
         */
        PhysicalBoardRuntime runtime = instance.getPhysicalBoardRuntime();
        require(runtime != null, "task43p-fixed-part-runtime-missing:" + expected.padId);
        PhysicalPart<?> part = runtime.getInstalledPart(expected.componentId);
        require(part != null, "task43p-fixed-part-missing:" + expected.padId);
        PhysicalPartProvenance provenance = part.getProvenance();
        require(provenance != null &&
            PhysicalPartProvenance.FIXED_GENERATED.equals(provenance.getKind()) &&
            same(expected.componentId, provenance.getSourceId()),
            "task43p-fixed-part-provenance-mismatch:" + expected.padId);
        PhysicalBoardSlot slot = part.getBoardSlot();
        require(part.isInstalled() && slot != null &&
            same(expected.componentId, slot.getComponentId()) &&
            slot.getPadIds().contains(expected.padId) &&
            slot.getTerminalIds().contains(expected.terminalId) &&
            slot.getInstalledPart() == part,
            "task43p-fixed-part-not-installed:" + expected.padId);

        PackageExpectation expectedPackage = manifest.getPackage(expected.componentId);
        PhysicalPackage actualPackage = part.getPackage();
        require(expectedPackage != null && actualPackage != null &&
            slot.getPhysicalPackage() != null &&
            same(expectedPackage.packageId, actualPackage.getId()) &&
            same(expectedPackage.packageId, slot.getPhysicalPackage().getId()) &&
            slot.getPhysicalPackage().isEquivalentTo(actualPackage) &&
            actualPackage.getTerminalIds().contains(expected.terminalId) &&
            expectedPackage.terminalIds.contains(expected.terminalId),
            "task43p-fixed-part-package-mismatch:" + expected.padId);

        PhysicalPartTerminal retainedTerminal = null;
        Vector<PhysicalPartTerminal> terminals = part.getTerminals();
        require(terminals != null, "task43p-fixed-part-terminals-missing:" + expected.padId);
        for (PhysicalPartTerminal terminal : terminals) {
            if (terminal != null && same(expected.terminalId, terminal.getTerminalName())) {
                require(retainedTerminal == null,
                    "task43p-fixed-part-terminal-duplicate:" + expected.padId);
                retainedTerminal = terminal;
            }
        }
        require(retainedTerminal != null &&
            same(part.getId() + "." + expected.terminalId, retainedTerminal.getId()) &&
            same(expected.terminalId, retainedTerminal.getTerminalName()),
            "task43p-fixed-part-terminal-mismatch:" + expected.padId);

        /*
         * Foundation parts are constructed by PhysicalFoundationPartFactory,
         * which retains the same board endpoint in its PhysicalPartTerminal.
         * Internal fixed parts deliberately do not take this branch: their
         * physical terminals are component-side endpoints, not board pads.
         */
        if (part instanceof FixedPhysicalPart) {
            CircuitMeasurementEndpoint foundationEndpoint = retainedTerminal.getEndpoint();
            require(foundationEndpoint instanceof CircuitPostMeasurementEndpoint,
                "task43p-fixed-foundation-endpoint-invalid:" + expected.padId);
            require(sameEndpointIdentity(retainedBoardEndpoint,
                    (CircuitPostMeasurementEndpoint) foundationEndpoint),
                "task43p-fixed-foundation-endpoint-mismatch:" + expected.padId);
        }
        return new RetainedSolverEndpoint(retainedBoardEndpoint, null);
    }

    private static boolean sameEndpointIdentity(CircuitPostMeasurementEndpoint first,
            CircuitPostMeasurementEndpoint second) {
        return first != null && second != null && first.getElement() == second.getElement() &&
            first.getPostIndex() == second.getPostIndex();
    }

    private static GeneratedComponentConnectionBinding findConnectionBinding(
            GeneratedBoardInstance instance, TerminalExpectation expected) {
        GeneratedComponentConnectionBindings bindings = instance.getConnectionBindings();
        require(bindings != null, "task43p-connection-bindings-missing:" + expected.padId);
        boolean padBindingPresent = false;
        for (GeneratedComponentConnectionBinding candidate : bindings.getAll()) {
            if (candidate != null && same(expected.padId, candidate.getPadId())) {
                require(!padBindingPresent && same(expected.componentId,
                    candidate.getComponentId()),
                    "task43p-connection-binding-identity-mismatch:" + expected.padId);
                padBindingPresent = true;
            }
        }
        if (!padBindingPresent)
            return null;
        try {
            return bindings.get(expected.componentId, expected.padId);
        } catch (RuntimeException malformed) {
            throw new IllegalStateException("task43p-connection-binding-unreadable:" +
                expected.padId);
        }
    }

    private static boolean touchesPoint(CircuitElm element, Point point) {
        if (element == null || point == null)
            return false;
        for (int postIndex = 0; postIndex < element.getPostCount(); postIndex++) {
            if (point.equals(element.getPost(postIndex)))
                return true;
        }
        return false;
    }

    private static LayoutPadObservation findLayoutPad(LayoutSnapshot snapshot, String padId) {
        for (LayoutPadObservation value : snapshot.pads)
            if (same(value.padId, padId))
                return value;
        return null;
    }

    private static LayoutComponentObservation findLayoutComponent(LayoutSnapshot snapshot,
            String componentId) {
        for (LayoutComponentObservation value : snapshot.components)
            if (same(value.componentId, componentId))
                return value;
        return null;
    }

    private static RendererPadObservation findRendererPad(RendererSnapshot snapshot,
            String padId) {
        for (RendererPadObservation value : snapshot.pads)
            if (same(value.padId, padId))
                return value;
        return null;
    }

    private static RendererTerminalObservation findRendererTerminal(RendererSnapshot snapshot,
            String terminalKey) {
        for (RendererTerminalObservation value : snapshot.terminals)
            if (same(value.terminalKey(), terminalKey))
                return value;
        return null;
    }

    private static SolverObservation findSolverObservation(SolverSnapshot snapshot,
            String padId) {
        for (SolverObservation value : snapshot.observations)
            if (same(value.padId, padId))
                return value;
        return null;
    }

    private static BoardPadObservation findBoardPad(LogicalBoardSnapshot snapshot,
            String padId) {
        for (BoardPadObservation value : snapshot.pads)
            if (same(value.padId, padId))
                return value;
        return null;
    }

    private static BoardNetObservation findBoardNet(LogicalBoardSnapshot snapshot,
            String netId) {
        for (BoardNetObservation value : snapshot.nets)
            if (same(value.netId, netId))
                return value;
        return null;
    }

    private static TraceObservation findTrace(LayoutSnapshot snapshot, String padId) {
        for (TraceObservation value : snapshot.traces)
            if (same(value.startPadId, padId) || same(value.endPadId, padId))
                return value;
        return null;
    }

    private static PackageObservation findConnectorPackage(Vector<PackageObservation> values) {
        for (PackageObservation value : values)
            if ("THROUGH_HOLE_CONNECTOR_2".equals(value.boardPackageId))
                return value;
        return null;
    }

    private static String q(String value) {
        if (value == null)
            return "null";
        StringBuilder result = new StringBuilder("\"");
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            if (character == '\\') result.append("\\\\");
            else if (character == '"') result.append("\\\"");
            else if (character == '\n') result.append("\\n");
            else if (character == '\r') result.append("\\r");
            else if (character == '\t') result.append("\\t");
            else result.append(character);
        }
        return result.append('"').toString();
    }
}
