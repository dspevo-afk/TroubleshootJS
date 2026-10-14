package com.lushprojects.circuitjs1.client;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Vector;

/** Native contract for fault-observation admission across overview, zoom, and face changes. */
public final class FaultObservationInspectionContractTest {
    private static int assertions;

    public static void main(String[] args) throws Exception {
        CirSim priorSim = CircuitElm.sim;
        CirSim priorSingleton = CirSim.theSim;
        CirSim sim = new CirSim();
        sim.gridSize = 16;
        sim.gridMask = ~15;
        sim.gridRound = 7;
        CircuitElm.sim = sim;
        Vector<CircuitElm> originalElements = sim.elmList;
        Rectangle originalArea = sim.circuitArea;
        GeneratedBoardInstance owner = null;
        GeneratedBoardInstance foreign = null;
        try {
            owner = new LedIndicatorGenerator().generate(0L);
            sim.generatedBoardInstance = owner;
            sim.elmList = new Vector<CircuitElm>(owner.getSimulationElements());
            sim.circuitArea = new Rectangle(0, 0, 319, 670);
            BoardModificationController modifications =
                new BoardModificationController(sim, owner);
            sim.boardModificationController = modifications;
            owner.getPhysicalBoardRuntime().installRegisteredCapabilities(sim, owner,
                modifications, sim.t);

            OverviewFixture overview = findAmbiguousInspectableFault(sim, owner,
                modifications);
            check(overview != null,
                "a real admitted fault terminal is ambiguous at narrow overview scale");
            Point point = overview.renderer.getPadPoint(overview.padId);
            check(overview.renderer.findProbeTarget(sim, point.x, point.y) == null &&
                overview.renderer.wasTargetAmbiguous(),
                "narrow overview ambiguity is from the real renderer hit resolver");
            check(overview.renderer.canInspectPad(sim, overview.padId),
                "the same real endpoint is inspectable through the renderer fit/face path");

            setInspectionView(overview.renderer);
            ViewSnapshot before = ViewSnapshot.capture(overview.renderer);
            GeneratedFaultServiceabilityAdmission.requireRendererObservationSupport(sim,
                overview.renderer, owner, overview.serviceability);
            before.requireUnchanged(overview.renderer,
                "admission preserves the player's overview camera and face");

            verifyFaceRestore(sim, owner, modifications, overview.padId);
            verifyMissingAndUnprobeableTargets(sim, owner, modifications, overview);
            verifyWrongEndpoint(sim, owner, modifications, overview);

            foreign = new LedIndicatorGenerator().generate(1L);
            sim.generatedBoardInstance = foreign;
            reject("stale active board owner", "runtime workbench is not installed", sim, overview.renderer, owner,
                overview.serviceability);
            sim.generatedBoardInstance = owner;
            PcbWorkbenchRenderer foreignRenderer = new PcbWorkbenchRenderer(foreign,
                new BoardModificationController(sim, foreign), foreign.getPcbLayout());
            reject("stale renderer owner", "runtime workbench is not installed", sim, foreignRenderer, owner,
                overview.serviceability);
            System.out.println("PASS: fault observation inspection contracts assertions=" +
                assertions);
        } finally {
            if (owner != null) dispose(owner);
            if (foreign != null) dispose(foreign);
            sim.generatedBoardInstance = null;
            sim.boardModificationController = null;
            sim.elmList = originalElements;
            sim.circuitArea = originalArea;
            CircuitElm.sim = priorSim;
            CirSim.theSim = priorSingleton;
        }
    }

    private static OverviewFixture findAmbiguousInspectableFault(CirSim sim,
            GeneratedBoardInstance owner, BoardModificationController modifications) {
        Rectangle base = owner.getPcbLayout().getBoardOutline();
        int[] extents = { 50000, 80000, 120000, 180000 };
        int[] shifts = { 0, 13, 29, 47 };
        for (int extent : extents) for (int shift : shifts) {
            Rectangle outline = new Rectangle(base.x - shift, base.y - shift,
                extent, extent);
            PcbBoardLayout overviewLayout = copyRendererLayout(owner.getPcbLayout(), outline,
                null, null, null);
            PcbWorkbenchRenderer renderer = new PcbWorkbenchRenderer(owner, modifications,
                overviewLayout);
            initializeRenderer(sim, renderer);
            for (GeneratedFaultCandidate candidate : owner.getFaultCandidates()) {
                if (!GeneratedFaultServiceabilityAdmission.isAdmitted(candidate)) continue;
                GeneratedFaultServiceability serviceability = candidate.getServiceability();
                String componentId = serviceability.getLocus().getComponentId();
                if (componentId == null) continue;
                for (GeneratedComponentConnectionBinding connection : owner.getConnectionBindings()
                        .getForComponentOrEmpty(componentId)) {
                    Point point = renderer.getPadPoint(connection.getPadId());
                    if (point == null || renderer.findProbeTarget(sim, point.x, point.y) != null ||
                            !renderer.wasTargetAmbiguous()) continue;
                    if (allObservationPadsInspectable(sim, renderer, owner, componentId))
                        return new OverviewFixture(renderer, serviceability,
                            connection.getPadId());
                }
            }
        }
        return null;
    }

    private static boolean allObservationPadsInspectable(CirSim sim,
            PcbWorkbenchRenderer renderer, GeneratedBoardInstance owner, String componentId) {
        Vector<GeneratedComponentConnectionBinding> connections = owner.getConnectionBindings()
            .getForComponentOrEmpty(componentId);
        if (connections.isEmpty()) return false;
        for (GeneratedComponentConnectionBinding connection : connections)
            if (!renderer.canInspectPad(sim, connection.getPadId())) return false;
        return true;
    }

    private static void verifyFaceRestore(CirSim sim, GeneratedBoardInstance owner,
            BoardModificationController modifications, String padId) {
        PcbBoardLayout surfaceLayout = copyRendererLayout(owner.getPcbLayout(),
            owner.getPcbLayout().getBoardOutline(), null, null, padId);
        PcbWorkbenchRenderer renderer = new PcbWorkbenchRenderer(owner, modifications,
            surfaceLayout);
        initializeRenderer(sim, renderer);
        renderer.setViewingFace(PcbBoardSide.BOTTOM);
        check(!renderer.canProbePad(padId), "fixture pad is top-face-only before inspection");
        setInspectionView(renderer);
        ViewSnapshot before = ViewSnapshot.capture(renderer);
        check(renderer.canInspectPad(sim, padId),
            "top-face-only pad is inspectable through the supported face change");
        before.requireUnchanged(renderer,
            "supported face change restores the prior viewport and bottom face");
    }

    private static void verifyMissingAndUnprobeableTargets(CirSim sim,
            GeneratedBoardInstance owner, BoardModificationController modifications,
            OverviewFixture overview) {
        PcbBoardLayout missingLayout = copyRendererLayout(owner.getPcbLayout(),
            owner.getPcbLayout().getBoardOutline(), overview.padId, null, null);
        PcbWorkbenchRenderer missingRenderer = new PcbWorkbenchRenderer(owner, modifications,
            missingLayout);
        initializeRenderer(sim, missingRenderer);
        reject("missing rendered pad", overview.padId, sim, missingRenderer, owner,
            overview.serviceability);

        PcbBoardLayout coveredLayout = copyRendererLayout(owner.getPcbLayout(),
            owner.getPcbLayout().getBoardOutline(), null, overview.padId, null);
        PcbWorkbenchRenderer coveredRenderer = new PcbWorkbenchRenderer(owner, modifications,
            coveredLayout);
        initializeRenderer(sim, coveredRenderer);
        reject("unprobeable covered pad", overview.padId, sim, coveredRenderer, owner,
            overview.serviceability);
    }

    private static void verifyWrongEndpoint(CirSim sim, GeneratedBoardInstance owner,
            BoardModificationController modifications, OverviewFixture overview) throws Exception {
        sim.circuitArea = new Rectangle(0, 0, 1280, 720);
        PcbWorkbenchRenderer renderer = new PcbWorkbenchRenderer(owner, modifications,
            owner.getPcbLayout());
        initializeRenderer(sim, renderer);
        GeneratedComponentConnectionBindings ownerBindings = owner.getConnectionBindings();
        Field field = GeneratedComponentConnectionBindings.class.getDeclaredField("bindings");
        field.setAccessible(true);
        @SuppressWarnings("unchecked")
        HashMap<String, GeneratedComponentConnectionBinding> bindings =
            (HashMap<String, GeneratedComponentConnectionBinding>) field.get(ownerBindings);
        GeneratedComponentConnectionBinding original = bindings.get(overview.padId);
        CircuitMeasurementEndpoint wrongEndpoint = null;
        for (GeneratedComponentConnectionBinding other : ownerBindings.getAll()) {
            if (!overview.padId.equals(other.getPadId()) &&
                    !GeneratedComponentConnectionBindings.sameEndpoint(
                        original.getBoardEndpoint(), other.getBoardEndpoint())) {
                wrongEndpoint = other.getBoardEndpoint();
                break;
            }
        }
        check(wrongEndpoint != null, "real owner supplies a distinct wrong endpoint fixture");
        bindings.put(overview.padId, new GeneratedComponentConnectionBinding(
            original.getComponentId(), original.getPadId(), wrongEndpoint,
            original.getComponentEndpoint(), original.getConnectionElement()));
        try {
            reject("wrong board endpoint", overview.padId, sim, renderer, owner,
                overview.serviceability);
        } finally {
            bindings.put(overview.padId, original);
        }
        check(bindings.get(overview.padId) == original,
            "wrong-endpoint fixture restores the real owner binding");
    }

    private static PcbBoardLayout copyRendererLayout(PcbBoardLayout source, Rectangle outline,
            String omittedPad, String coveredPad, String topOnlyPad) {
        PcbBoardLayout copy = new PcbBoardLayout(source.getWidth(), source.getHeight(), outline,
            source.getPartsTray(), source.getLayoutAlgorithmVersion());
        for (PcbPadPlacement pad : source.getPads()) {
            if (pad.getPadId().equals(omittedPad)) continue;
            if (pad.getPadId().equals(coveredPad)) {
                copy.addPad(copyPad(pad, pad.getAttachment(), pad.getMountingSide(),
                    PcbCopperAccess.Exposure.COVERED));
            } else if (pad.getPadId().equals(topOnlyPad)) {
                copy.addPad(copyPad(pad, PcbTerminalAttachment.SURFACE_PAD,
                    PcbBoardSide.TOP, PcbCopperAccess.Exposure.EXPOSED));
            } else {
                copy.addPad(pad);
            }
        }
        for (PcbComponentPlacement component : source.getComponents())
            copy.addComponent(component);
        for (PcbBoardHole hole : source.getHoles()) copy.addHole(hole);
        copy.seal();
        return copy;
    }

    private static PcbPadPlacement copyPad(PcbPadPlacement pad,
            PcbTerminalAttachment attachment, PcbBoardSide side,
            PcbCopperAccess.Exposure exposure) {
        return new PcbPadPlacement(pad.getPadId(), pad.getX(), pad.getY(),
            pad.getEscapeDx(), pad.getEscapeDy(), pad.getEscapeLength(),
            pad.getPadBounds(), pad.getProbeBounds(), attachment, side, exposure);
    }

    private static void initializeRenderer(CirSim sim, PcbWorkbenchRenderer renderer) {
        renderer.getViewport().resize(new Rectangle(0, 0, sim.circuitArea.width,
            sim.circuitArea.height));
        renderer.fitWorkbench();
        boolean inspectable = false;
        for (PcbPadPlacement pad : renderer.getLayoutForProvider().getPads())
            if (renderer.canInspectPad(sim, pad.getPadId())) {
                inspectable = true;
                break;
            }
        check(inspectable, "real renderer initializes through an inspectable board pad");
        renderer.fitWorkbench();
        renderer.updateProjection();
    }

    private static void setInspectionView(PcbWorkbenchRenderer renderer) {
        PcbViewport viewport = renderer.getViewport();
        Rectangle area = viewport.getArea();
        int x = area.x + area.width / 2;
        int y = area.y + area.height / 2;
        check(viewport.inspect(x, y), "fixture enters the existing inspection camera mode");
        viewport.zoom(1.25, x, y);
        renderer.updateProjection();
    }

    private static void reject(String label, String expectedMessage, CirSim sim,
            PcbWorkbenchRenderer renderer, GeneratedBoardInstance instance,
            GeneratedFaultServiceability serviceability) {
        try {
            GeneratedFaultServiceabilityAdmission.requireRendererObservationSupport(sim,
                renderer, instance, serviceability);
        } catch (IllegalArgumentException expected) {
            check(expected.getMessage() != null &&
                expected.getMessage().contains(expectedMessage), label + " reports the rejected boundary");
            return;
        }
        throw new AssertionError(label + " was accepted");
    }

    private static void dispose(GeneratedBoardInstance instance) {
        instance.getExternalPowerBindings().setConnected(false);
        for (CircuitElm element : instance.getSimulationElements()) element.delete();
    }

    private static void check(boolean ok, String message) {
        assertions++;
        if (!ok) throw new AssertionError(message);
    }

    private static final class OverviewFixture {
        final PcbWorkbenchRenderer renderer;
        final GeneratedFaultServiceability serviceability;
        final String padId;
        OverviewFixture(PcbWorkbenchRenderer renderer,
                GeneratedFaultServiceability serviceability, String padId) {
            this.renderer = renderer;
            this.serviceability = serviceability;
            this.padId = padId;
        }
    }

    private static final class ViewSnapshot {
        final double x, y, scale, magnification;
        final boolean flipped, inspecting;
        final PcbBoardSide rendererFace, viewportFace;
        final Rectangle area;
        private ViewSnapshot(PcbWorkbenchRenderer renderer) {
            PcbViewport viewport = renderer.getViewport();
            PcbViewport.Transform transform = viewport.current();
            x = transform.x;
            y = transform.y;
            scale = transform.scale;
            flipped = transform.flipped;
            magnification = viewport.getMagnification();
            inspecting = viewport.isInspecting();
            rendererFace = renderer.getViewingFace();
            viewportFace = viewport.getFace();
            area = viewport.getArea();
        }
        static ViewSnapshot capture(PcbWorkbenchRenderer renderer) {
            return new ViewSnapshot(renderer);
        }
        void requireUnchanged(PcbWorkbenchRenderer renderer, String message) {
            ViewSnapshot current = capture(renderer);
            check(x == current.x && y == current.y && scale == current.scale &&
                flipped == current.flipped && magnification == current.magnification &&
                inspecting == current.inspecting && rendererFace == current.rendererFace &&
                viewportFace == current.viewportFace && area.x == current.area.x &&
                area.y == current.area.y && area.width == current.area.width &&
                area.height == current.area.height, message);
        }
    }
}
