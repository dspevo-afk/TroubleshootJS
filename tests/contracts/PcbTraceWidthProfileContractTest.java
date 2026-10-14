package com.lushprojects.circuitjs1.client;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Vector;

/** Independent identity and rendering-dependency checks for finite PCB width profiles. */
public final class PcbTraceWidthProfileContractTest {
    private static int assertions;

    public static void main(String[] args) {
        literalProfileIdentity();
        privatePlanWidthBoundary();
        supportedEnvelopeWidthBoundary();
        copperUnionIdentity();
        copperViewUsesGraphWidth();
        copperCutRestoreRetainsWidthAndProbeTarget();
        System.out.println("PASS: PCB trace width profile contracts assertions=" + assertions);
    }

    private static void literalProfileIdentity() {
        PcbTraceWidthProfile uniform = PcbTraceWidthProfile.UNIFORM;
        final PcbTraceWidthProfile roles = PcbTraceWidthProfile.ROLE_WIDTHS;
        check("UNIFORM_TRACE@1".equals(uniform.getIdentity()),
            "legacy profile identity remains versioned and stable");
        check("ROLE_TRACE@1".equals(roles.getIdentity()),
            "role profile identity is explicit and versioned");
        check(uniform.maximumWidth() == 9 && roles.maximumWidth() == 13,
            "profile maxima match literal drawing widths");

        BoardNet.RoutingRole[] routingRoles = BoardNet.RoutingRole.values();
        int[] expectedRoleWidths = new int[] { 11, 11, 13, 9, 9 };
        for (int index = 0; index < routingRoles.length; index++) {
            check(uniform.widthFor(routingRoles[index]) == 9,
                "uniform profile width is9 for " + routingRoles[index]);
            check(roles.widthFor(routingRoles[index]) == expectedRoleWidths[index],
                "literal role width for " + routingRoles[index]);
        }
        check(("UNIFORM_TRACE@1;RETURN=9;SUPPLY=9;HIGH_CURRENT=9;CONTROL=9;SIGNAL=9;" +
                "units=drawing;edge-clearance=6").equals(uniform.canonical()),
            "uniform profile canonical identity stays literal");
        check(("ROLE_TRACE@1;RETURN=11;SUPPLY=11;HIGH_CURRENT=13;CONTROL=9;SIGNAL=9;" +
                "units=drawing;edge-clearance=6").equals(roles.canonical()),
            "role profile canonical identity matches independent literals");
        reject("missing role has no profile width", new Action() {
            public void run() { roles.widthFor(null); }
        });

        TroubleshootBoard publicBoard = new TroubleshootBoard("uniform-profile-default");
        check(publicBoard.getPlacementConstraints().getTraceWidthProfile() == uniform,
            "ordinary board construction still defaults to uniform width9");
    }

    private static void privatePlanWidthBoundary() {
        long seed = 20261014L;
        Rb56Plan saved11 = Rb56Plan.resolve(seed, Rb56Placement.VACANCY_VERSION);
        Rb56Plan saved12 = Rb56Plan.resolve(seed, Rb56Placement.ROLE_WIDTHS_VERSION12);
        Rb56Plan current13 = Rb56Plan.resolve(seed, Rb56Placement.CURRENT_VERSION);
        TroubleshootBoard savedBoard = saved11.board();
        TroubleshootBoard saved12Board = saved12.board();
        TroubleshootBoard currentBoard = current13.board();
        PcbPlacementConstraints saved = savedBoard.getPlacementConstraints();
        PcbPlacementConstraints saved12Constraints = saved12Board.getPlacementConstraints();
        PcbPlacementConstraints current = currentBoard.getPlacementConstraints();

        check(saved11.placementInterpretationVersion == 11 &&
                saved12.placementInterpretationVersion == 12 &&
                current13.placementInterpretationVersion == 13,
            "saved11, saved12, and current13 resolve their declared placement versions");
        check(saved.getTraceWidthProfile() == PcbTraceWidthProfile.UNIFORM &&
                saved12Constraints.getTraceWidthProfile() == PcbTraceWidthProfile.ROLE_WIDTHS &&
                current.getTraceWidthProfile() == PcbTraceWidthProfile.ROLE_WIDTHS,
            "saved12 and current13 retain role widths while saved11 remains uniform");
        check(saved.placementDensityVersion == 11 &&
                saved12Constraints.placementDensityVersion == 11 &&
                current.placementDensityVersion == 12,
            "saved12 retains density11 while current13 selects density12");
        check(saved.requiresTwoLayerRouting && saved12Constraints.requiresTwoLayerRouting &&
                current.requiresTwoLayerRouting && saved.routingLayer == current.routingLayer,
            "role-width versions retain the same declared routing mode and layer");
        check(saved11.layoutSeed == saved12.layoutSeed && saved12.layoutSeed == current13.layoutSeed &&
                saved11.routingSeed == saved12.routingSeed && saved12.routingSeed == current13.routingSeed &&
                saved11.topology().equals(saved12.topology()) && saved12.topology().equals(current13.topology()),
            "placement version leaves seeded electrical and routing intents stable");

        Vector<Rb56Plan.Part> oldParts = saved11.parts();
        Vector<Rb56Plan.Part> saved12Parts = saved12.parts();
        Vector<Rb56Plan.Part> newParts = current13.parts();
        check(oldParts.size() == saved12Parts.size() && saved12Parts.size() == newParts.size(),
            "saved11, saved12, and current13 retain one package inventory");
        for (int index = 0; index < oldParts.size(); index++)
            check(oldParts.get(index).canonical().equals(saved12Parts.get(index).canonical()) &&
                    saved12Parts.get(index).canonical().equals(newParts.get(index).canonical()),
                "plan part declaration stays byte-equal across interpretations at index " + index);
    }

    private static void supportedEnvelopeWidthBoundary() {
        CirSim sim = new CirSim();
        sim.gridSize = 16; sim.gridMask = ~15; sim.gridRound = 7;
        CircuitElm.sim = sim;
        GeneratedBoardInstance owner = GenerationRequest.leaf("LED_INDICATOR", 0, false)
            .resolve(new GenerationRequest.PlanCache()).construct().instance;
        try {
            final SupportedEnvelope envelope = SupportedEnvelope.current();
            TroubleshootBoard board = owner.getBoard();
            final PcbBoardLayout layout = owner.getPcbLayout();

            envelope.requireNormal(owner);
            check(true, "real P09 LED fixture passes the uniform-width envelope");

            final TroubleshootBoard roleWidthBoard = copyBoardWithTraceWidthProfile(board,
                PcbTraceWidthProfile.ROLE_WIDTHS);
            expectEnvelopeReject(SupportedEnvelope.Reason.ACCESS_FLOOR, new Action() {
                public void run() { envelope.requireBounds(roleWidthBoard, layout); }
            });
        } finally {
            for (CircuitElm element : owner.getSimulationElements()) element.delete();
            sim.solverExecutor.retire();
        }
    }

    private static TroubleshootBoard copyBoardWithTraceWidthProfile(TroubleshootBoard source,
            PcbTraceWidthProfile profile) {
        TroubleshootBoard copy = new TroubleshootBoard(source.getId() + "-trace-width-profile");
        if (source.getSilkscreenTitle() != null)
            copy.setSilkscreenTitle(source.getSilkscreenTitle());

        Vector<String> netIds = source.getNetIds();
        Collections.sort(netIds);
        for (String id : netIds) {
            BoardNet net = source.getNet(id);
            copy.addNet(new BoardNet(id, net.getRoutingRole()));
        }

        Vector<String> componentIds = source.getComponentIds();
        Collections.sort(componentIds);
        for (String id : componentIds) {
            BoardComponent component = source.getComponent(id);
            copy.addComponent(new BoardComponent(id, component.getType(),
                component.getPhysicalPackage(), component.getDisplayName()));
        }

        Vector<String> padIds = source.getPadIds();
        Collections.sort(padIds);
        for (String id : padIds) {
            BoardPad pad = source.getPad(id);
            copy.addPad(new BoardPad(id, pad.getComponentId(), pad.getTerminalId(), pad.getNetId()));
        }

        Vector<String> powerInputIds = source.getPowerInputIds();
        Collections.sort(powerInputIds);
        for (String id : powerInputIds) {
            ExternalBoardPowerInput input = source.getPowerInput(id);
            copy.addPowerInput(new ExternalBoardPowerInput(id, input.getPositivePadId(),
                input.getReturnPadId(), input.getPositiveNetId(), input.getReturnNetId()));
        }

        PcbPlacementConstraints original = source.getPlacementConstraints();
        Vector<PcbPlacementConstraints.Part> parts = new Vector<PcbPlacementConstraints.Part>();
        for (PcbPlacementConstraints.Part part : original.getParts())
            parts.add(new PcbPlacementConstraints.Part(part.componentId, part.regionId,
                part.regionLabel, part.domainId, part.anchor, part.accessMargin,
                part.getTerminalDomains()));
        Vector<PcbPlacementConstraints.Barrier> barriers = new Vector<PcbPlacementConstraints.Barrier>();
        for (PcbPlacementConstraints.Barrier barrier : original.getBarriers())
            barriers.add(new PcbPlacementConstraints.Barrier(barrier.firstDomain,
                barrier.secondDomain, barrier.clearance));
        copy.setPlacementConstraints(new PcbPlacementConstraints(parts, barriers,
            original.routingLayer, original.physicalPolicyId, original.physicalPolicyVersion,
            original.placementDensityVersion, original.requiresTwoLayerRouting, profile));
        copy.validate();
        return copy;
    }

    private static void expectEnvelopeReject(SupportedEnvelope.Reason reason, Action action) {
        try {
            action.run();
        } catch (SupportedEnvelope.Rejected expected) {
            check(expected.reason == reason, "role-width envelope rejection reason is " + reason);
            return;
        }
        throw new AssertionError("Expected SupportedEnvelope rejection: " + reason);
    }

    private static void copperUnionIdentity() {
        String uniform = PhysicalBoardFingerprint.copperUnion(singleTrace(9,
            new int[] { 100, 180 }, new int[] { 200, 200 }, 0, 0), 0, 0);
        String wide = PhysicalBoardFingerprint.copperUnion(singleTrace(13,
            new int[] { 100, 180 }, new int[] { 200, 200 }, 0, 0), 0, 0);
        check("25:TOP/EXPOSED/H/200/100/180;".equals(uniform),
            "default-width copper union retains its independent literal record");
        check("34:TOP/EXPOSED/H/200/100/180/width=13;".equals(wide),
            "wide copper union carries its literal physical width");
        check(!uniform.equals(wide), "different copper widths have different physical identities");

        PcbBoardLayout split = new PcbBoardLayout(800, 500,
            new Rectangle(0, 0, 300, 300), new Rectangle(500, 0, 200, 300));
        split.addTrace(trace("split-a", 13, new int[] { 100, 140 }, new int[] { 200, 200 }));
        split.addTrace(trace("split-b", 13, new int[] { 140, 180 }, new int[] { 200, 200 }));
        check(wide.equals(PhysicalBoardFingerprint.copperUnion(split, 0, 0)),
            "same-width split and merged routes represent the same physical copper");

        PcbBoardLayout reversed = singleTrace(13, new int[] { 180, 100 },
            new int[] { 200, 200 }, 0, 0);
        check(wide.equals(PhysicalBoardFingerprint.copperUnion(reversed, 0, 0)),
            "route point reversal leaves copper-union identity unchanged");

        PcbBoardLayout translated = singleTrace(13, new int[] { 610, 690 },
            new int[] { 520, 520 }, 510, 320);
        check(wide.equals(PhysicalBoardFingerprint.copperUnion(translated, 510, 320)),
            "whole-board translation with a matching origin leaves normalized copper unchanged");
    }

    private static void copperViewUsesGraphWidth() {
        PcbConductorGraph wideGraph = oneTraceGraph(13);
        PcbConductorGraph.Snapshot wideSnapshot = wideGraph.pristine();
        PcbCopperViewCache cache = new PcbCopperViewCache();
        PcbViewport.Transform view = new PcbViewport.Transform(1, 0, 0, 0, false);
        PcbCopperViewCache.Entry wide = cache.get(wideSnapshot, view, PcbBoardSide.TOP,
            PcbCopperViewCache.POLICY_VERSION, "surface/high-current");
        check(wide != null && wide.surface == wideGraph.getSurface("surface/high-current"),
            "cache entry is bound to its actual graph surface");
        check(wide.traceWidth == 13,
            "render projection carries the trace width from its graph edge");
        check(wide.contains(140, 194) && wide.contains(140, 206) &&
                !wide.contains(140, 193) && !wide.contains(140, 207),
            "width13 probe target includes its two physical edges and excludes the adjacent pixels");
        check(wide.firstX == 100 && wide.firstY == 200 &&
                wide.secondX == 180 && wide.secondY == 200 &&
                wide.bounds().equals(new Rectangle(94, 194, 93, 13)),
            "projected width13 surface and endpoints match literal board geometry");
        check(wide == cache.get(wideSnapshot, new PcbViewport.Transform(1, 0, 0, 0, false),
                PcbBoardSide.TOP, PcbCopperViewCache.POLICY_VERSION, "surface/high-current") &&
                cache.getBuildCount() == 1,
            "same graph snapshot reuses the current rendered width entry");

        PcbConductorGraph legacyGraph = oneTraceGraph(9);
        PcbCopperViewCache.Entry legacy = cache.get(legacyGraph.pristine(), view,
            PcbBoardSide.TOP, PcbCopperViewCache.POLICY_VERSION, "surface/high-current");
        check(legacy != null && legacy.traceWidth == 9 &&
                legacy.bounds().equals(new Rectangle(96, 196, 89, 9)) &&
                cache.getBuildCount() == 2,
            "new graph width rebuilds the cache and preserves the legacy9 surface");
        check(!legacy.contains(140, 194) && !legacy.contains(140, 206) &&
                legacy.contains(140, 196) && legacy.contains(140, 204),
            "legacy9 target excludes the additional copper area belonging only to width13");
    }

    private static void copperCutRestoreRetainsWidthAndProbeTarget() {
        PcbConductorGraph graph=oneTraceGraph(13);
        String canonical=graph.toCanonical();
        PcbConductorGraph.Snapshot pristine=graph.pristine();
        PcbConductorGraph.Edge edge=graph.getEdges().get("edge/high-current");
        PcbConductorGraph.Surface surface=graph.getSurface("surface/high-current");
        Rectangle originalBounds=new Rectangle(94,194,93,13);
        PcbViewport.Transform view=new PcbViewport.Transform(1,0,0,0,false);
        PcbCopperViewCache cache=new PcbCopperViewCache();

        check(edge!=null && edge.getWidth()==13 &&
                surface!=null && surface.getBounds().equals(originalBounds),
            "pristine width13 edge owns its literal physical surface bounds");
        check(pristine.getGraph()==graph && pristine.hasEdge(edge.id) &&
                pristine.connected("junction/first","junction/second"),
            "pristine snapshot owns the graph and joins its original junctions");
        check(PcbCopperProbeAccess.available(pristine,surface,PcbBoardSide.TOP),
            "pristine width13 surface is an available current probe target");
        PcbCopperViewCache.Entry original=cache.get(pristine,view,PcbBoardSide.TOP,
            PcbCopperViewCache.POLICY_VERSION,surface.id);
        check(original!=null && original.surface==surface && original.traceWidth==13 &&
                original.bounds().equals(originalBounds) &&
                original.contains(140,194) && original.contains(140,206) &&
                !original.contains(140,193) && !original.contains(140,207) &&
                cache.getBuildCount()==1,
            "pristine cache exposes the exact width13 probe target and literal edges");

        PcbConductorGraph.Snapshot cut=pristine.withCut(edge.id,true);
        check(cut!=pristine && cut.getGraph()==graph && cut.getCutEdgeIds().contains(edge.id) &&
                !cut.hasEdge(edge.id) && !cut.connected("junction/first","junction/second"),
            "cut snapshot disconnects the original junctions without replacing the graph");
        check(!PcbCopperProbeAccess.available(cut,surface,PcbBoardSide.TOP) &&
                cache.get(cut,view,PcbBoardSide.TOP,PcbCopperViewCache.POLICY_VERSION,
                    surface.id)==null && cache.getBuildCount()==2,
            "cut edge is excluded from current probe access and rendered targets");

        PcbConductorGraph.Snapshot restored=cut.withCut(edge.id,false);
        check(restored!=cut && restored.getGraph()==graph && restored.getCutEdgeIds().isEmpty() &&
                restored.hasEdge(edge.id) &&
                restored.connected("junction/first","junction/second"),
            "restoring the cut reconnects both junctions on the same graph owner");
        check(PcbCopperProbeAccess.available(restored,surface,PcbBoardSide.TOP),
            "restored width13 surface is again an available current probe target");
        PcbCopperViewCache.Entry restoredEntry=cache.get(restored,view,PcbBoardSide.TOP,
            PcbCopperViewCache.POLICY_VERSION,surface.id);
        check(restoredEntry!=null && restoredEntry.surface==surface &&
                restoredEntry.traceWidth==13 && restoredEntry.bounds().equals(originalBounds) &&
                restoredEntry.contains(140,194) && restoredEntry.contains(140,206) &&
                !restoredEntry.contains(140,193) && !restoredEntry.contains(140,207) &&
                cache.getBuildCount()==3,
            "restored cache target recovers the original literal width13 surface");

        check(pristine.getGraph()==graph && pristine.getCutEdgeIds().isEmpty() &&
                pristine.hasEdge(edge.id) &&
                pristine.connected("junction/first","junction/second") &&
                edge.getWidth()==13 && surface.getBounds().equals(originalBounds) &&
                canonical.equals(graph.toCanonical()),
            "cut and restore leave the pristine snapshot and canonical original unchanged");
    }

    private static PcbBoardLayout singleTrace(int width, int[] x, int[] y,
            int originX, int originY) {
        Rectangle outline = originX == 0 && originY == 0 ?
            new Rectangle(0, 0, 300, 300) : new Rectangle(originX, originY, 300, 300);
        Rectangle tray = originX == 0 && originY == 0 ?
            new Rectangle(500, 0, 200, 300) : new Rectangle(originX + 400, originY, 200, 300);
        PcbBoardLayout layout = new PcbBoardLayout(1400, 1000, outline, tray);
        layout.addTrace(trace("single", width, x, y));
        return layout;
    }

    private static PcbTraceGeometry trace(String source, int width, int[] x, int[] y) {
        return new PcbTraceGeometry(source, "HIGH", null, null, PcbCopperLayer.TOP,
            PcbCopperAccess.Exposure.EXPOSED, x, y, width);
    }

    private static PcbConductorGraph oneTraceGraph(int width) {
        Rectangle bounds;
        if (width == 13) bounds = new Rectangle(94, 194, 93, 13);
        else if (width == 9) bounds = new Rectangle(96, 196, 89, 9);
        else throw new IllegalArgumentException("Fixture supports literal width9 or13");
        PcbConductorGraph.Junction first = new PcbConductorGraph.Junction("junction/first",
            "HIGH", PcbCopperLayer.TOP, 100, 200);
        PcbConductorGraph.Junction second = new PcbConductorGraph.Junction("junction/second",
            "HIGH", PcbCopperLayer.TOP, 180, 200);
        PcbConductorGraph.Edge edge = new PcbConductorGraph.Edge("edge/high-current",
            first.id, second.id, "HIGH", PcbConductorGraph.Kind.TRACE,
            Collections.singletonList("manual/high-current"), width);
        PcbConductorGraph.Surface surface = new PcbConductorGraph.Surface(
            "surface/high-current", first.id, edge.id, null, PcbCopperLayer.TOP,
            PcbCopperAccess.Exposure.EXPOSED, bounds);
        List<PcbConductorGraph.Junction> junctions = Arrays.asList(first, second);
        List<PcbConductorGraph.Edge> edges = Collections.singletonList(edge);
        List<PcbConductorGraph.Surface> surfaces = Collections.singletonList(surface);
        Map<String, String> noReferences = Collections.emptyMap();
        return new PcbConductorGraph(junctions, edges, surfaces, noReferences, noReferences, "");
    }

    private static void reject(String message, Action action) {
        try {
            action.run();
        } catch (IllegalArgumentException expected) {
            check(true, message);
            return;
        }
        throw new AssertionError("Expected rejection: " + message);
    }

    private static void check(boolean condition, String message) {
        assertions++;
        if (!condition) throw new AssertionError(message);
    }

    private interface Action { void run(); }
}
