package com.lushprojects.circuitjs1.client;

import java.util.Vector;

/**
 * Provider-owned Q30 physical metadata regression. Every declared resistor
 * package must construct with the typed metadata required by its renderer.
 */
public final class Rb30PhysicalMetadataContractTest {
    private static int assertions;

    public static void main(String[] args) {
        CirSim sim = new CirSim();
        sim.gridSize = 16;
        sim.gridMask = ~15;
        sim.gridRound = 7;
        CircuitElm.sim = sim;
        int topologyCount = 0;
        for (long seed : new long[] { 0L, 1L, 3L, 11L, Long.MIN_VALUE,
                Long.MAX_VALUE, 9007199254740993L }) {
            Rb30Plan plan = Rb30Plan.resolve(seed);
            TroubleshootBoard board = plan.board();
            Rb30Generator.Candidate candidate = new Rb30Generator().construct(plan);
            check(candidate.plan == plan);
            int resistors = 0;
            int diodes = 0;
            int leds = 0;
            int ceramicCapacitors = 0;
            int electrolyticCapacitors = 0;
            for (String id : board.getComponentIds()) {
                BoardComponent component = board.getComponent(id);
                PhysicalPart<?> installed = candidate.runtime.getInstalledPart(id);
                check(installed != null);
                check(installed.getSpecification() != null);
                check(installed.getRenderMetadata().getVisualSpecification() ==
                    installed.getSpecification());
                if ("RESISTOR".equals(component.getType())) {
                    resistors++;
                    check(component.getPhysicalPackage().isEquivalentTo(
                        PhysicalPackages.AXIAL_RESISTOR));
                    check(installed.getPackage().isEquivalentTo(
                        PhysicalPackages.AXIAL_RESISTOR));
                    check(installed.getSpecification() instanceof ResistorNameplate);
                    ResistorNameplate metadata =
                        (ResistorNameplate) installed.getSpecification();
                    check(id.equals(metadata.getComponentId()));
                    check(candidate.backing.get(id) instanceof ResistorElm);
                    check(metadata.getNominalResistanceOhms() ==
                        ((ResistorElm) candidate.backing.get(id)).getResistance());
                } else if ("DIODE".equals(component.getType())) {
                    diodes++;
                    check(installed.getSpecification() instanceof DiodeNameplate);
                } else if ("LED".equals(component.getType())) {
                    leds++;
                    check(installed.getSpecification() instanceof LedNameplate);
                } else if ("CAPACITOR".equals(component.getType())) {
                    check(installed.getSpecification() instanceof CapacitorSpecification);
                    if (component.getPhysicalPackage().isEquivalentTo(
                            PhysicalPackages.RADIAL_CERAMIC_CAPACITOR))
                        ceramicCapacitors++;
                    else if (component.getPhysicalPackage().isEquivalentTo(
                            PhysicalPackages.RADIAL_ELECTROLYTIC_CAPACITOR))
                        electrolyticCapacitors++;
                }
                StandardPhysicalPartRenderProviders.createRegistry().requireRenderer(
                    component.getPhysicalPackage(), installed);
            }
            check(resistors == 12);
            check(diodes == 3);
            check(leds == 1);
            check(ceramicCapacitors == 2);
            check(electrolyticCapacitors == 1);
            topologyCount++;
        }
        check(topologyCount == 7);
        verifyFullOwnerConstruction(sim, 0L);
        verifyFullOwnerConstruction(sim, 37L);
        System.out.println("PASS: Q30 physical metadata contracts " + assertions +
            " assertions, topologies=" + topologyCount);
    }

    /**
     * Keep the metadata contract coupled to the same production owner path as
     * the compiled Q30 workbench verifier.  The candidate-only census above
     * catches provider declarations; this path catches constructor,
     * physical-runtime, conductor, and installed-geometry integration.
     */
    private static void verifyFullOwnerConstruction(CirSim sim, long seed) {
        String phase = "plan";
        try {
            Rb30Plan plan = Rb30Plan.resolve(seed);
            phase = "candidate";
            Rb30Generator.Candidate candidate = new Rb30Generator().construct(plan);
            check(MediumBoardPhysicalPolicy.selected(
                candidate.board().getPlacementConstraints()));

            phase = "medium route";
            MediumBoardPhysicalPolicy.Result routed =
                new SeededPcbLayoutGenerator().generateWithPolicyResult(
                    candidate.board(), plan.layoutSeed, plan.routingSeed);
            check(routed.accepted());
            PcbBoardLayout layout = routed.getLayout();
            check(layout != null);
            check(layout.matchesGenerationSeeds(plan.layoutSeed, plan.routingSeed));
            check(routed.getStatistics() != null);
            check(MediumBoardPhysicalPolicy.P07_FULLER_TWO_LAYER.equals(
                routed.getStatistics().selectedRoutePolicy));

            phase = "GeneratedBoardInstance construction";
            GeneratedBoardInstance instance = new Rb30Generator().assemble(candidate, layout);
            check(instance.isDeveloperOnlyFaultRoute());
            check(instance.getPhysicalBoardRuntime().getPhysicalParts().size() ==
                candidate.board().getComponentIds().size());

            phase = "conductor snapshot";
            PcbConductorGraph pristine = instance.getPristineConductorGraph();
            PcbConductorGraph.Snapshot current = instance.getCurrentConductorSnapshot();
            check(pristine != null);
            check(current != null);
            check(current.getGraph() == pristine);
            current.requirePristineNetConnectivity(instance.getBoard());
            check(!pristine.getEdges().isEmpty());
            check(pristine.getJunctions().size() >= instance.getBoard().getPadIds().size());

            phase = "production renderer construction";
            BoardModificationController modifications =
                new BoardModificationController(sim, instance);
            PcbWorkbenchRenderer renderer = new PcbWorkbenchRenderer(
                instance, modifications, layout);
            PhysicalPartRenderRegistry registry =
                StandardPhysicalPartRenderProviders.createRegistry();

            phase = "installed geometry";
            int geometryCount = 0;
            for (String id : instance.getBoard().getComponentIds()) {
                PhysicalPart<?> part = instance.getPhysicalBoardRuntime()
                    .getInstalledPart(id);
                PcbComponentPlacement placement = layout.getComponent(id);
                check(part != null);
                check(placement != null);
                PhysicalPartRenderer partRenderer = registry.requireRenderer(
                    part.getPackage(), part);
                PhysicalPartRenderContext context = new PhysicalPartRenderContext(
                    renderer, placement, part, part.getPackage(), -1, false);
                PhysicalPartRenderGeometry geometry =
                    partRenderer.getInstalledGeometry(context);
                check(geometry != null);
                check(geometry.getTerminals().size() == part.getTerminalCount());
                check(geometry.getSelectionBounds().width > 0 &&
                    geometry.getSelectionBounds().height > 0);
                check(geometry.getBodyBounds().width > 0 &&
                    geometry.getBodyBounds().height > 0);
                geometryCount++;
            }
            check(geometryCount == instance.getBoard().getComponentIds().size());
            System.out.println("Q30_FULL_OWNER seed=" + seed + " PASS components=" +
                geometryCount + " copperEdges=" + pristine.getEdges().size() +
                " copperJunctions=" + pristine.getJunctions().size());
        } catch (Throwable failure) {
            throw new AssertionError("Q30 full owner seed=" + seed +
                " failed in " + phase + ": " + failure, failure);
        }
    }

    private static void check(boolean condition) {
        assertions++;
        if (!condition)
            throw new AssertionError("Q30 physical metadata assertion " + assertions);
    }
}
