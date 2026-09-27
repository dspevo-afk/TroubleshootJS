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
                    if (id.equals("RPIN_A") || id.equals("RPIN_B")) {
                        String channel = id.substring("RPIN_".length());
                        check(metadata.getNominalResistanceOhms() == 1000.0);
                        check(metadata.getTolerancePercent() == 5.0);
                        check(metadata.getRatedWattage() == .25);
                        check(((ResistorElm) candidate.backing.get(id))
                            .getResistance() == 1000.0);
                        check(candidate.runtime.getSlot(id) != null);
                        check(candidate.runtime.getSlot(id).getInstalledPart() ==
                            installed);
                        check((channel + "_RAW").equals(
                            board.getPad(id + ".1").getNetId()));
                        check("CTRL_RETURN".equals(
                            board.getPad(id + ".2").getNetId()));
                    }
                } else if ("DIODE".equals(component.getType())) {
                    diodes++;
                    check(installed.getSpecification() instanceof DiodeNameplate);
                } else if ("LED".equals(component.getType())) {
                    leds++;
                    check(installed.getSpecification() instanceof LedNameplate);
                } else if ("CAPACITOR".equals(component.getType())) {
                    check(installed.getSpecification() instanceof CapacitorSpecification);
                    if (id.startsWith("CFLT_")) {
                        check(((CapacitorSpecification) installed.getSpecification())
                            .getCapacitanceFarads() == 100e-9);
                        check("100 nF / 25 V".equals(((CapacitorSpecification)
                            installed.getSpecification()).getNameplate().getMarking()));
                        check(((CapacitorElm) candidate.backing.get(id)).getCapacitance() == 100e-9);
                    }
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
            check(resistors == (plan.hasStatusIndicator() ? 14 : 13));
            check(diodes == 3);
            check(leds == (plan.hasStatusIndicator() ? 1 : 0));
            check(ceramicCapacitors == (plan.hasSensorInputFilters() ? 4 : 2));
            check(electrolyticCapacitors == 1);
            topologyCount++;
        }
        check(topologyCount == 7);
        verifyFullOwnerConstruction(sim, 3L);
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
            if (!routed.accepted()) throw new AssertionError(
                "Positive owner fixture has no accepted route: " + routed.toCanonical());
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
